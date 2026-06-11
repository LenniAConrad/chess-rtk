package testing;

import chess.core.MoveList;
import chess.core.Position;
import chess.engine.AlphaBeta;
import chess.engine.Limits;
import chess.engine.Result;
import chess.eval.CentipawnEvaluator;
import chess.eval.Nnue;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * NNUE speed + correctness harness, used to verify that an NNUE optimization is
 * <em>output-preserving</em> (bit-identical centipawns) and to measure its
 * effect on search throughput (nodes/sec) on the alpha-beta path.
 *
 * <p>
 * Run before and after a change and compare: the {@code GOLDEN} lines must be
 * byte-identical (so there is no Elo change), while the {@code NPS} summary
 * should improve. Usage: {@code java -cp <dir> testing.NnueBench [nnuePath]}.
 * </p>
 */
public final class NnueBench {

    /**
     * Fixed evaluation corpus covering varied phases, castling rights, en
     * passant, and both sides to move.
     */
    private static final String[] FENS = {
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
        "r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 0 1",
        "r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/3P1N2/PPP2PPP/RNBQK2R b KQkq - 0 1",
        "r3k2r/pppq1ppp/2np1n2/2b1p1B1/2B1P1b1/2NP1N2/PPPQ1PPP/R3K2R w KQkq - 0 1",
        "rnbqkb1r/pp2pppp/3p1n2/2pP4/4P3/8/PPP2PPP/RNBQKBNR w KQkq c6 0 1",
        "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
        "8/8/4k3/8/2pP4/8/B6b/4K3 b - d3 0 1",
        "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N1P/1PP1QPP1/R4RK1 w - - 0 1",
        "8/5k2/8/8/8/3K4/4P3/8 w - - 0 1",
        "2r3k1/5ppp/8/8/8/8/5PPP/2R3K1 w - - 0 1",
    };

    /**
     * Start positions for the incremental-vs-full parity walk, chosen to exercise
     * castling, captures, promotions, en passant, and king moves (refresh path).
     */
    private static final String[] PARITY_STARTS = {
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
        "r3k2r/pppq1ppp/2np1n2/2b1p1B1/2B1P1b1/2NP1N2/PPPQ1PPP/R3K2R w KQkq - 0 1",
        "rnbqkb1r/pp2pppp/3p1n2/2pP4/4P3/8/PPP2PPP/RNBQKBNR w KQkq c6 0 1",
        "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
        "8/P6k/8/8/8/8/6Kp/8 w - - 0 1",
        "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
        // Kings on the d/e file boundary: random king marches repeatedly flip
        // the FullThreats orientation (per-perspective threat refresh path).
        "8/4k3/8/2p3p1/2P3P1/8/4K3/8 w - - 0 1",
        // En-passant-rich: facing pawn chains where double pushes create
        // immediate EP captures (capturedSquare differs from the to-square).
        "k7/2p1p1p1/8/1P1P1P2/p1p1p3/8/1P1P1P2/K7 w - - 0 1",
        // Promotion-heavy: both sides promote within the walk depth.
        "8/PPP2k2/8/8/8/8/3K1ppp/8 w - - 0 1",
        // Slider-dense middlegame: long see-through rays crossing move squares.
        "1r2r1k1/1bq2pp1/4p3/3nN3/2B5/1Q6/5PP1/3RR1K1 w - - 0 1",
        // Chess960 castling rights: overlap shapes (king castling in place,
        // rook landing on the king's origin) only exist in 960.
        "4k3/8/8/8/8/8/8/RK5R w HA - 0 1",
        "k7/8/8/8/8/8/8/5KR1 w G - 0 1",
    };

    /**
     * Number of parity comparisons performed.
     */
    private static long parityChecks;

    /**
     * Number of incremental-vs-full eval mismatches found.
     */
    private static long parityMismatches;

    /**
     * Prevents instantiation.
     */
    private NnueBench() {
    }

    /**
     * Runs the golden-eval dump and the nps benchmark.
     *
     * @param args optional NNUE weights path
     */
    public static void main(String[] args) throws Exception {
        String path = args.length > 0 ? args[0] : "models/crtk-halfkp.nnue";
        Nnue nnue = new Nnue(Path.of(path));
        try {
            // Parity: incremental search-state eval must equal the full eval at
            // every node of a make/undo walk (mirrors how AlphaBeta drives it).
            parity(nnue);
            // Concurrent parity: many threads each run their own incremental state
            // over the shared network, proving Lazy SMP can use NNUE safely.
            parityConcurrent(nnue, 8);
            // Golden eval: deterministic centipawns, must be identical across builds.
            for (String fen : FENS) {
                System.out.println("GOLDEN\t" + fen + "\t" + nnue.evaluate(new Position(fen)));
            }
            // nps benchmark: fixed node budget per position; report after a warmup rep.
            Limits limits = new Limits(64, 150_000L, 0L);
            for (int rep = 0; rep < 3; rep++) {
                long totalNodes = 0;
                long totalMs = 0;
                for (String fen : FENS) {
                    AlphaBeta engine = new AlphaBeta(nnue);
                    Result r = engine.search(new Position(fen), limits);
                    totalNodes += r.nodes();
                    totalMs += r.elapsedMillis();
                }
                long nps = totalNodes * 1000L / Math.max(1L, totalMs);
                System.out.printf("NPS\trep=%d\tnodes=%d\tms=%d\tnps=%d%n", rep, totalNodes, totalMs, nps);
            }
        } finally {
            nnue.close();
        }
    }

    /**
     * Walks a small make/undo tree from each parity start position, asserting the
     * incremental search-state eval equals the full eval at every node.
     *
     * @param nnue evaluator under test
     */
    private static void parity(Nnue nnue) {
        parityChecks = 0;
        parityMismatches = 0;
        boolean incremental = false;
        for (String fen : PARITY_STARTS) {
            Position root = new Position(fen);
            CentipawnEvaluator.SearchState state = nnue.openSearchState(root, 40);
            if (state == null) {
                continue;
            }
            incremental = true;
            try {
                walk(nnue, root, state, 0, 5, new Random(fen.hashCode()));
            } finally {
                state.close();
            }
        }
        if (!incremental) {
            System.out.println("PARITY\tNO_INCREMENTAL_STATE (full-eval fallback)");
        } else {
            System.out.printf("PARITY\tchecks=%d\tmismatches=%d%n", parityChecks, parityMismatches);
        }
    }

    /**
     * Runs the parity walk on several threads at once, each with its own
     * incremental {@link CentipawnEvaluator.SearchState} over the shared network,
     * to prove concurrent (Lazy SMP) use of the NNUE incremental path is safe.
     *
     * @param nnue evaluator under test
     * @param threadCount number of concurrent walker threads
     */
    private static void parityConcurrent(Nnue nnue, int threadCount) {
        AtomicLong checks = new AtomicLong();
        AtomicLong mismatches = new AtomicLong();
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < threadCount; t++) {
            final int tid = t;
            Thread thread = new Thread(() -> {
                for (String fen : PARITY_STARTS) {
                    Position root = new Position(fen);
                    CentipawnEvaluator.SearchState state = nnue.openSearchState(root, 40);
                    if (state == null) {
                        return;
                    }
                    try {
                        walkConcurrent(nnue, root, state, 0, 4,
                                new Random(fen.hashCode() * 31L + tid), checks, mismatches);
                    } finally {
                        state.close();
                    }
                }
            }, "nnue-parity-" + t);
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
        System.out.printf("PARITY_CONCURRENT\tthreads=%d\tchecks=%d\tmismatches=%d%n",
                threadCount, checks.get(), mismatches.get());
    }

    /**
     * Thread-safe variant of {@link #walk} using atomic counters, for concurrent
     * parity verification.
     *
     * @param nnue evaluator
     * @param pos current position (mutated in place)
     * @param state this thread's incremental state
     * @param ply current ply
     * @param depth remaining depth
     * @param rng move sampler
     * @param checks total comparisons counter
     * @param mismatches total mismatch counter
     */
    private static void walkConcurrent(Nnue nnue, Position pos, CentipawnEvaluator.SearchState state,
            int ply, int depth, Random rng, AtomicLong checks, AtomicLong mismatches) {
        checks.incrementAndGet();
        if (state.evaluate(pos, ply) != nnue.evaluate(pos)) {
            mismatches.incrementAndGet();
        }
        if (depth == 0) {
            return;
        }
        MoveList legal = pos.legalMoves();
        if (legal.isEmpty()) {
            return;
        }
        int samples = Math.min(4, legal.size());
        for (int s = 0; s < samples; s++) {
            short move = legal.raw(rng.nextInt(legal.size()));
            Position.State undo = new Position.State();
            pos.play(move, undo);
            state.movePlayed(pos, move, undo, ply + 1);
            walkConcurrent(nnue, pos, state, ply + 1, depth - 1, rng, checks, mismatches);
            pos.undo(move, undo);
        }
    }

    /**
     * Recursively compares incremental vs full eval, sampling moves and mirroring
     * the search's make/undo + ply-indexed accumulator usage.
     *
     * @param nnue evaluator
     * @param pos current position (mutated in place via make/undo)
     * @param state incremental search state
     * @param ply current ply
     * @param depth remaining depth
     * @param rng move sampler
     */
    private static void walk(Nnue nnue, Position pos, CentipawnEvaluator.SearchState state,
            int ply, int depth, Random rng) {
        int incrementalCp = state.evaluate(pos, ply);
        int fullCp = nnue.evaluate(pos);
        parityChecks++;
        if (incrementalCp != fullCp) {
            parityMismatches++;
            if (parityMismatches <= 8) {
                System.out.printf("PARITY_MISMATCH\tply=%d\tinc=%d\tfull=%d\tfen=%s%n",
                        ply, incrementalCp, fullCp, pos.toString());
            }
        }
        if (depth == 0) {
            return;
        }
        MoveList legal = pos.legalMoves();
        if (legal.isEmpty()) {
            return;
        }
        int samples = Math.min(4, legal.size());
        for (int s = 0; s < samples; s++) {
            short move = legal.raw(rng.nextInt(legal.size()));
            Position.State undo = new Position.State();
            pos.play(move, undo);
            state.movePlayed(pos, move, undo, ply + 1);
            walk(nnue, pos, state, ply + 1, depth - 1, rng);
            pos.undo(move, undo);
        }
    }
}
