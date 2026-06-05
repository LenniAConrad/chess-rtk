package testing;

import chess.core.Position;
import chess.engine.AlphaBeta;
import chess.engine.Limits;
import chess.engine.Mcts;
import chess.engine.Result;
import chess.eval.Classical;

/**
 * Fixed-node throughput benchmark for the built-in searchers.
 *
 * <p>
 * This is intentionally not a regression test: it prints deterministic benchmark
 * inputs and measured nodes-per-second so engine speed changes can be compared
 * before and after an optimization. Usage:
 * {@code java -cp out testing.SearchSpeedBenchmark [alpha-beta|mcts] [nodes] [reps]}.
 * </p>
 */
public final class SearchSpeedBenchmark {

    /**
     * Mixed opening, middlegame, tactical, and endgame positions.
     */
    private static final String[] FENS = {
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
        "r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 0 1",
        "r3k2r/pppq1ppp/2np1n2/2b1p1B1/2B1P1b1/2NP1N2/PPPQ1PPP/R3K2R w KQkq - 0 1",
        "rnbqkb1r/pp2pppp/3p1n2/2pP4/4P3/8/PPP2PPP/RNBQKBNR w KQkq c6 0 1",
        "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N1P/1PP1QPP1/R4RK1 w - - 0 1",
        "2r3k1/5ppp/8/8/8/8/5PPP/2R3K1 w - - 0 1",
        "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
        "8/8/4k3/8/2pP4/8/B6b/4K3 b - d3 0 1",
    };

    /**
     * Prevents instantiation.
     */
    private SearchSpeedBenchmark() {
    }

    /**
     * Runs the benchmark.
     *
     * @param args optional search kind, nodes per position, and measured reps
     */
    public static void main(String[] args) {
        String search = args.length > 0 ? args[0] : "alpha-beta";
        long nodes = args.length > 1 ? Long.parseLong(args[1]) : 120_000L;
        int reps = args.length > 2 ? Integer.parseInt(args[2]) : 4;
        Limits limits = new Limits(AlphaBeta.MAX_DEPTH, nodes, 0L);

        runRep(search, limits, -1);
        for (int rep = 0; rep < reps; rep++) {
            runRep(search, limits, rep);
        }
    }

    /**
     * Runs one benchmark repetition.
     *
     * @param search searcher name
     * @param limits fixed search limits
     * @param rep repetition index, or -1 for warmup
     */
    private static void runRep(String search, Limits limits, int rep) {
        long totalNodes = 0L;
        long totalMillis = 0L;
        for (String fen : FENS) {
            Result result = searchPosition(search, fen, limits);
            totalNodes += result.nodes();
            totalMillis += result.elapsedMillis();
        }
        long nps = totalNodes * 1_000L / Math.max(1L, totalMillis);
        System.out.printf("BENCH\tsearch=%s\trep=%d\tpositions=%d\tnodes=%d\tms=%d\tnps=%d%n",
                search, rep, FENS.length, totalNodes, totalMillis, nps);
    }

    /**
     * Searches one position with the requested built-in searcher.
     *
     * @param search searcher name
     * @param fen position FEN
     * @param limits fixed search limits
     * @return search result
     */
    private static Result searchPosition(String search, String fen, Limits limits) {
        Position position = new Position(fen);
        if ("mcts".equalsIgnoreCase(search)) {
            try (Mcts engine = new Mcts(new Classical())) {
                return engine.search(position, limits);
            }
        }
        if ("alpha-beta".equalsIgnoreCase(search)
                || "alphabeta".equalsIgnoreCase(search)
                || "ab".equalsIgnoreCase(search)) {
            try (AlphaBeta engine = new AlphaBeta(new Classical(), false)) {
                return engine.search(position, limits);
            }
        }
        throw new IllegalArgumentException("unknown search kind: " + search);
    }
}
