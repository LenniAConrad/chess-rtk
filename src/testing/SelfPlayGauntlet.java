package testing;

import chess.classical.Wdl;
import chess.core.Field;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Piece;
import chess.core.Position;
import chess.engine.AlphaBeta;
import chess.engine.Limits;
import chess.engine.Mcts;
import chess.engine.Result;
import chess.eval.CentipawnEvaluator;
import chess.eval.Classical;
import chess.eval.Nnue;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Self-play A/B harness for the built-in searchers.
 *
 * <p>
 * Pits a candidate feature configuration against a baseline at an equal,
 * fixed per-move budget (node count by default, or wall-clock time) so that a
 * search change's strength effect is <em>measured</em> by game results rather
 * than asserted. Both engines run in one process against an embedded set of
 * varied opening positions, each played from both colors. The score and a
 * point Elo estimate are printed from the candidate's perspective.
 * </p>
 *
 * <p>
 * Usage: {@code java -cp out testing.SelfPlayGauntlet [flags]}
 * </p>
 * <ul>
 * <li>{@code --a CSV} candidate features (default {@code all})</li>
 * <li>{@code --b CSV} baseline features (default {@code none})</li>
 * <li>{@code --nodes N} fixed node budget per move (default 5000)</li>
 * <li>{@code --movetime MS} fixed time budget per move (overrides --nodes)</li>
 * <li>{@code --eval classical|nnue} evaluator for both engines (default classical)</li>
 * <li>{@code --evalA NAME} candidate evaluator override</li>
 * <li>{@code --evalB NAME} baseline evaluator override</li>
 * <li>{@code --search alpha-beta|mcts} search for both engines (default alpha-beta)</li>
 * <li>{@code --searchA alpha-beta|mcts} candidate search override</li>
 * <li>{@code --searchB alpha-beta|mcts} baseline search override</li>
 * <li>{@code --cpuctA N} candidate MCTS cpuct (default 2.8)</li>
 * <li>{@code --cpuctB N} baseline MCTS cpuct (default 2.8)</li>
 * <li>{@code --fpuA N} candidate MCTS FPU reduction (default 0.05)</li>
 * <li>{@code --fpuB N} baseline MCTS FPU reduction (default 0.05)</li>
 * <li>{@code --checkPriorA N} candidate MCTS check-prior bonus (default 4000)</li>
 * <li>{@code --checkPriorB N} baseline MCTS check-prior bonus (default 4000)</li>
 * <li>{@code --capturePenaltyA N} candidate MCTS SEE-losing-capture prior penalty (default 8000)</li>
 * <li>{@code --capturePenaltyB N} baseline MCTS SEE-losing-capture prior penalty (default 8000)</li>
 * <li>{@code --captureWinScaleA N} candidate MCTS positive-SEE capture prior scale (default 8)</li>
 * <li>{@code --captureWinScaleB N} baseline MCTS positive-SEE capture prior scale (default 8)</li>
 * <li>{@code --maxplies N} adjudicate a draw past this many plies (default 240)</li>
 * <li>{@code --workers N} opening pairs to play concurrently (default 1)</li>
 * </ul>
 * <p>
 * A CSV is a comma-separated list of {@link AlphaBeta.Feature} names, or the
 * shorthands {@code all} / {@code none}.
 * </p>
 */
public final class SelfPlayGauntlet {

    /**
     * Outcome of one game from the candidate's perspective.
     */
    private enum Outcome {
        /**
         * Candidate won.
         */
        WIN,

        /**
         * Game drawn.
         */
        DRAW,

        /**
         * Candidate lost.
         */
        LOSS
    }

    /**
     * Two games from one opening: candidate as White, then candidate as Black.
     *
     * @param first candidate-as-White result
     * @param second candidate-as-Black result
     */
    private record OpeningResult(Outcome first, Outcome second) {
    }

    /**
     * Aggregate match score from the candidate's perspective.
     *
     * @param win candidate wins
     * @param draw draws
     * @param loss candidate losses
     */
    private record Score(int win, int draw, int loss) {
    }

    /**
     * Search algorithm used by one side in a gauntlet game.
     */
    private enum SearchKind {
        /**
         * Iterative-deepening alpha-beta.
         */
        ALPHA_BETA,

        /**
         * PUCT Monte-Carlo tree search.
         */
        MCTS
    }

    /**
     * Per-side searcher abstraction for self-play.
     */
    private interface GameSearcher extends AutoCloseable {
        /**
         * Searches a position.
         *
         * @param position root position
         * @param limits search limits
         * @param history pre-root core-position history
         * @return search result
         */
        Result search(Position position, Limits limits, long[] history);

        /**
         * Releases resources.
         */
        @Override
        void close();
    }

    /**
     * Varied opening positions (each played from both colors) so the two
     * deterministic engines do not replay one identical game.
     */
    private static final String[] OPENINGS = {
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
        "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1",
        "rnbqkbnr/pppppppp/8/8/3P4/8/PPP1PPPP/RNBQKBNR b KQkq - 0 1",
        "rnbqkbnr/pppppppp/8/8/2P5/8/PP1PPPPP/RNBQKBNR b KQkq - 0 1",
        "rnbqkbnr/pppppppp/8/8/8/5N2/PPPPPPPP/RNBQKB1R b KQkq - 0 1",
        "rnbqkbnr/pppppppp/8/8/8/6P1/PPPPPP1P/RNBQKBNR b KQkq - 0 1",
        "rnbqkbnr/pppppppp/8/8/8/1P6/P1PPPPPP/RNBQKBNR b KQkq - 0 1",
        "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2",
        "rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2",
        "rnbqkbnr/pppp1ppp/4p3/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2",
        "rnbqkbnr/pp1ppppp/2p5/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2",
        "rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2",
        "rnbqkbnr/pppppp1p/6p1/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2",
        "rnbqkb1r/pppppppp/5n2/8/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2",
        "rnbqkbnr/ppp1pppp/8/3p4/3P4/8/PPP1PPPP/RNBQKBNR w KQkq - 0 2",
        "rnbqkb1r/pppppppp/5n2/8/3P4/8/PPP1PPPP/RNBQKBNR w KQkq - 0 2",
        "rnbqkbnr/ppppp1pp/8/5p2/3P4/8/PPP1PPPP/RNBQKBNR w KQkq - 0 2",
        "rnbqkbnr/pppppp1p/6p1/8/3P4/8/PPP1PPPP/RNBQKBNR w KQkq - 0 2",
        "rnbqkbnr/pppp1ppp/8/4p3/2P5/8/PP1PPPPP/RNBQKBNR w KQkq - 0 2",
        "rnbqkbnr/pp1ppppp/8/2p5/2P5/8/PP1PPPPP/RNBQKBNR w KQkq - 0 2",
        "rnbqkbnr/ppp1pppp/8/3p4/8/5N2/PPPPPPPP/RNBQKB1R w KQkq - 0 2",
        "rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2",
        "rnbqkbnr/pp1ppppp/8/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2",
        "rnbqkbnr/ppp1pppp/8/3p4/2PP4/8/PP2PPPP/RNBQKBNR b KQkq - 0 2",
    };

    /**
     * Path to the bundled NNUE network, for {@code --eval nnue}.
     */
    private static final String NNUE_PATH = "models/crtk-halfkp.nnue";

    /**
     * Starting non-king material for both sides.
     */
    private static final int STARTING_MATERIAL = 7_800;

    /**
     * Prevents instantiation.
     */
    private SelfPlayGauntlet() {
    }

    /**
     * Runs the gauntlet.
     *
     * @param args optional flags (see class docs)
     */
    public static void main(String[] args) {
        Map<String, String> opts = parseArgs(args);
        Set<AlphaBeta.Feature> featuresA = parseFeatures(opts.getOrDefault("a", "all"));
        Set<AlphaBeta.Feature> featuresB = parseFeatures(opts.getOrDefault("b", "none"));
        long nodes = Long.parseLong(opts.getOrDefault("nodes", "5000"));
        long movetime = Long.parseLong(opts.getOrDefault("movetime", "0"));
        String eval = opts.getOrDefault("eval", "classical");
        String evalA = opts.getOrDefault("evalA", eval);
        String evalB = opts.getOrDefault("evalB", eval);
        SearchKind search = parseSearch(opts.getOrDefault("search", "alpha-beta"));
        SearchKind searchA = parseSearch(opts.getOrDefault("searchA", searchName(search)));
        SearchKind searchB = parseSearch(opts.getOrDefault("searchB", searchName(search)));
        double cpuctA = Double.parseDouble(opts.getOrDefault("cpuctA", "2.8"));
        double cpuctB = Double.parseDouble(opts.getOrDefault("cpuctB", "2.8"));
        double fpuA = Double.parseDouble(opts.getOrDefault("fpuA", "0.05"));
        double fpuB = Double.parseDouble(opts.getOrDefault("fpuB", "0.05"));
        int checkPriorA = Integer.parseInt(opts.getOrDefault("checkPriorA", "4000"));
        int checkPriorB = Integer.parseInt(opts.getOrDefault("checkPriorB", "4000"));
        int capturePenaltyA = Integer.parseInt(opts.getOrDefault("capturePenaltyA", "8000"));
        int capturePenaltyB = Integer.parseInt(opts.getOrDefault("capturePenaltyB", "8000"));
        int captureWinScaleA = Integer.parseInt(opts.getOrDefault("captureWinScaleA", "8"));
        int captureWinScaleB = Integer.parseInt(opts.getOrDefault("captureWinScaleB", "8"));
        int maxPlies = Integer.parseInt(opts.getOrDefault("maxplies", "240"));
        int openingCount = Integer.parseInt(opts.getOrDefault("openings", "0"));
        long seed = Long.parseLong(opts.getOrDefault("seed", "20260531"));
        int threadsA = Integer.parseInt(opts.getOrDefault("threadsA", "1"));
        int threadsB = Integer.parseInt(opts.getOrDefault("threadsB", "1"));
        int workers = Integer.parseInt(opts.getOrDefault("workers", "1"));
        if (workers < 1) {
            throw new IllegalArgumentException("--workers must be positive");
        }

        // Either the curated set, or a larger seeded-random set for a tighter
        // (reproducible) Elo estimate.
        String[] openings = openingCount > 0
                ? generateOpenings(openingCount, seed)
                : OPENINGS;

        Limits limits = movetime > 0
                ? new Limits(AlphaBeta.MAX_DEPTH, 0L, movetime)
                : new Limits(AlphaBeta.MAX_DEPTH, nodes, 0L);

        System.out.println("SelfPlayGauntlet");
        System.out.println("  candidate (A) features: " + featuresA);
        System.out.println("  baseline  (B) features: " + featuresB);
        System.out.println("  searchA=" + searchName(searchA) + " searchB=" + searchName(searchB)
                + "  evalA=" + evalA + " evalB=" + evalB
                + "  cpuctA=" + cpuctA + " cpuctB=" + cpuctB
                + "  fpuA=" + fpuA + " fpuB=" + fpuB
                + "  checkPriorA=" + checkPriorA + " checkPriorB=" + checkPriorB
                + "  capturePenaltyA=" + capturePenaltyA + " capturePenaltyB=" + capturePenaltyB
                + "  captureWinScaleA=" + captureWinScaleA + " captureWinScaleB=" + captureWinScaleB
                + "  budget=" + (movetime > 0 ? movetime + "ms/move" : nodes + " nodes/move")
                + "  threadsA=" + threadsA + " threadsB=" + threadsB
                + "  workers=" + workers
                + "  openings=" + openings.length + "  games=" + (openings.length * 2));

        Score score = workers == 1
                ? runSequential(openings, featuresA, featuresB, searchA, searchB, evalA, evalB, cpuctA, cpuctB,
                        fpuA, fpuB, checkPriorA, checkPriorB, capturePenaltyA, capturePenaltyB, captureWinScaleA,
                        captureWinScaleB, limits, maxPlies, threadsA, threadsB)
                : runParallel(openings, featuresA, featuresB, searchA, searchB, evalA, evalB, cpuctA, cpuctB,
                        fpuA, fpuB, checkPriorA, checkPriorB, capturePenaltyA, capturePenaltyB, captureWinScaleA,
                        captureWinScaleB, limits, maxPlies, threadsA, threadsB, workers);
        report(score.win(), score.draw(), score.loss());
    }

    /**
     * Runs opening pairs one at a time.
     *
     * @param openings opening FENs
     * @param featuresA candidate features
     * @param featuresB baseline features
     * @param searchA candidate search
     * @param searchB baseline search
     * @param evalA candidate evaluator name
     * @param evalB baseline evaluator name
     * @param cpuctA candidate MCTS cpuct
     * @param cpuctB baseline MCTS cpuct
     * @param fpuA candidate MCTS FPU reduction
     * @param fpuB baseline MCTS FPU reduction
     * @param checkPriorA candidate MCTS check-prior bonus
     * @param checkPriorB baseline MCTS check-prior bonus
     * @param capturePenaltyA candidate MCTS SEE-losing-capture prior penalty
     * @param capturePenaltyB baseline MCTS SEE-losing-capture prior penalty
     * @param captureWinScaleA candidate MCTS positive-SEE capture prior scale
     * @param captureWinScaleB baseline MCTS positive-SEE capture prior scale
     * @param limits per-move search limits
     * @param maxPlies draw-adjudication ply cap
     * @param threadsA candidate search threads
     * @param threadsB baseline search threads
     * @return aggregate score
     */
    private static Score runSequential(
            String[] openings,
            Set<AlphaBeta.Feature> featuresA,
            Set<AlphaBeta.Feature> featuresB,
            SearchKind searchA,
            SearchKind searchB,
            String evalA,
            String evalB,
            double cpuctA,
            double cpuctB,
            double fpuA,
            double fpuB,
            int checkPriorA,
            int checkPriorB,
            int capturePenaltyA,
            int capturePenaltyB,
            int captureWinScaleA,
            int captureWinScaleB,
            Limits limits,
            int maxPlies,
            int threadsA,
            int threadsB) {
        int[] score = { 0, 0, 0 };
        for (int i = 0; i < openings.length; i++) {
            add(score, playOpeningPair(openings[i], featuresA, featuresB, searchA, searchB, evalA, evalB, cpuctA,
                    cpuctB, fpuA, fpuB, checkPriorA, checkPriorB, capturePenaltyA, capturePenaltyB,
                    captureWinScaleA, captureWinScaleB, limits, maxPlies, threadsA, threadsB));
            printProgress(i + 1, openings.length, score);
        }
        return new Score(score[0], score[1], score[2]);
    }

    /**
     * Runs opening pairs concurrently, collecting futures in opening order so the
     * progress stream remains deterministic.
     *
     * @param openings opening FENs
     * @param featuresA candidate features
     * @param featuresB baseline features
     * @param searchA candidate search
     * @param searchB baseline search
     * @param evalA candidate evaluator name
     * @param evalB baseline evaluator name
     * @param cpuctA candidate MCTS cpuct
     * @param cpuctB baseline MCTS cpuct
     * @param fpuA candidate MCTS FPU reduction
     * @param fpuB baseline MCTS FPU reduction
     * @param checkPriorA candidate MCTS check-prior bonus
     * @param checkPriorB baseline MCTS check-prior bonus
     * @param capturePenaltyA candidate MCTS SEE-losing-capture prior penalty
     * @param capturePenaltyB baseline MCTS SEE-losing-capture prior penalty
     * @param captureWinScaleA candidate MCTS positive-SEE capture prior scale
     * @param captureWinScaleB baseline MCTS positive-SEE capture prior scale
     * @param limits per-move search limits
     * @param maxPlies draw-adjudication ply cap
     * @param threadsA candidate search threads
     * @param threadsB baseline search threads
     * @param workers number of concurrent opening-pair workers
     * @return aggregate score
     */
    private static Score runParallel(
            String[] openings,
            Set<AlphaBeta.Feature> featuresA,
            Set<AlphaBeta.Feature> featuresB,
            SearchKind searchA,
            SearchKind searchB,
            String evalA,
            String evalB,
            double cpuctA,
            double cpuctB,
            double fpuA,
            double fpuB,
            int checkPriorA,
            int checkPriorB,
            int capturePenaltyA,
            int capturePenaltyB,
            int captureWinScaleA,
            int captureWinScaleB,
            Limits limits,
            int maxPlies,
            int threadsA,
            int threadsB,
            int workers) {
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(workers, openings.length));
        try {
            List<Future<OpeningResult>> futures = new ArrayList<>(openings.length);
            for (String opening : openings) {
                futures.add(pool.submit(() -> playOpeningPair(
                        opening,
                        featuresA,
                        featuresB,
                        searchA,
                        searchB,
                        evalA,
                        evalB,
                        cpuctA,
                        cpuctB,
                        fpuA,
                        fpuB,
                        checkPriorA,
                        checkPriorB,
                        capturePenaltyA,
                        capturePenaltyB,
                        captureWinScaleA,
                        captureWinScaleB,
                        limits,
                        maxPlies,
                        threadsA,
                        threadsB)));
            }
            int[] score = { 0, 0, 0 };
            for (int i = 0; i < futures.size(); i++) {
                add(score, futures.get(i).get());
                printProgress(i + 1, openings.length, score);
            }
            return new Score(score[0], score[1], score[2]);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("self-play interrupted", ex);
        } catch (ExecutionException ex) {
            throw new IllegalStateException("self-play worker failed", ex.getCause());
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Plays both color assignments for one opening.
     *
     * @param opening opening FEN
     * @param featuresA candidate features
     * @param featuresB baseline features
     * @param searchA candidate search
     * @param searchB baseline search
     * @param evalA candidate evaluator name
     * @param evalB baseline evaluator name
     * @param cpuctA candidate MCTS cpuct
     * @param cpuctB baseline MCTS cpuct
     * @param fpuA candidate MCTS FPU reduction
     * @param fpuB baseline MCTS FPU reduction
     * @param checkPriorA candidate MCTS check-prior bonus
     * @param checkPriorB baseline MCTS check-prior bonus
     * @param capturePenaltyA candidate MCTS SEE-losing-capture prior penalty
     * @param capturePenaltyB baseline MCTS SEE-losing-capture prior penalty
     * @param captureWinScaleA candidate MCTS positive-SEE capture prior scale
     * @param captureWinScaleB baseline MCTS positive-SEE capture prior scale
     * @param limits per-move search limits
     * @param maxPlies draw-adjudication ply cap
     * @param threadsA candidate search threads
     * @param threadsB baseline search threads
     * @return two-game result for the opening
     */
    private static OpeningResult playOpeningPair(
            String opening,
            Set<AlphaBeta.Feature> featuresA,
            Set<AlphaBeta.Feature> featuresB,
            SearchKind searchA,
            SearchKind searchB,
            String evalA,
            String evalB,
            double cpuctA,
            double cpuctB,
            double fpuA,
            double fpuB,
            int checkPriorA,
            int checkPriorB,
            int capturePenaltyA,
            int capturePenaltyB,
            int captureWinScaleA,
            int captureWinScaleB,
            Limits limits,
            int maxPlies,
            int threadsA,
            int threadsB) {
        // Play each opening from both colors so a result is not just one
        // deterministic game counted twice.
        Outcome first = playGame(opening, true, featuresA, featuresB, searchA, searchB, evalA, evalB, cpuctA, cpuctB,
                fpuA, fpuB, checkPriorA, checkPriorB, capturePenaltyA, capturePenaltyB, captureWinScaleA,
                captureWinScaleB, limits, maxPlies, threadsA, threadsB);
        Outcome second = playGame(opening, false, featuresA, featuresB, searchA, searchB, evalA, evalB, cpuctA,
                cpuctB, fpuA, fpuB, checkPriorA, checkPriorB, capturePenaltyA, capturePenaltyB, captureWinScaleA,
                captureWinScaleB, limits, maxPlies, threadsA, threadsB);
        return new OpeningResult(first, second);
    }

    /**
     * Adds one opening-pair result to an aggregate score.
     *
     * @param score mutable {wins, draws, losses} score
     * @param result opening-pair result
     */
    private static void add(int[] score, OpeningResult result) {
        add(score, result.first());
        add(score, result.second());
    }

    /**
     * Adds one game outcome to an aggregate score.
     *
     * @param score mutable {wins, draws, losses} score
     * @param outcome game outcome
     */
    private static void add(int[] score, Outcome outcome) {
        switch (outcome) {
            case WIN -> score[0]++;
            case DRAW -> score[1]++;
            case LOSS -> score[2]++;
            default -> { }
        }
    }

    /**
     * Prints periodic progress.
     *
     * @param completed completed opening pairs
     * @param total total opening pairs
     * @param score mutable {wins, draws, losses} score
     */
    private static void printProgress(int completed, int total, int[] score) {
        if (completed % 10 == 0 || completed == total) {
            System.out.printf("  [%3d/%3d]  running W-D-L = %d-%d-%d%n",
                    completed, total, score[0], score[1], score[2]);
        }
    }

    /**
     * Generates a reproducible set of varied, non-terminal opening positions by
     * playing a short seeded-random sequence of legal moves from the standard
     * start. Each opening is still played deterministically by both engines, so
     * the only randomness is which positions are sampled, fixed by the seed.
     *
     * @param count number of distinct openings to produce
     * @param seed RNG seed for reproducibility
     * @return array of opening FENs
     */
    private static String[] generateOpenings(int count, long seed) {
        java.util.Random rng = new java.util.Random(seed);
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        String start = OPENINGS[0];
        int attempts = 0;
        int cap = count * 100 + 1000;
        while (set.size() < count && attempts < cap) {
            attempts++;
            Position position = new Position(start);
            int plies = 4 + rng.nextInt(5); // 4..8 plies
            boolean ok = true;
            for (int i = 0; i < plies; i++) {
                MoveList legal = position.legalMoves();
                if (legal.isEmpty()) {
                    ok = false;
                    break;
                }
                position.play(legal.raw(rng.nextInt(legal.size())));
            }
            if (!ok || position.legalMoves().isEmpty() || position.isInsufficientMaterial()) {
                continue;
            }
            set.add(position.toString());
        }
        return set.toArray(new String[0]);
    }

    /**
     * Plays one game and returns its outcome from the candidate's perspective.
     *
     * @param fen opening FEN
     * @param candidateIsWhite whether the candidate plays white this game
     * @param featuresA candidate features
     * @param featuresB baseline features
     * @param searchA candidate search
     * @param searchB baseline search
     * @param evalA candidate evaluator name
     * @param evalB baseline evaluator name
     * @param cpuctA candidate MCTS cpuct
     * @param cpuctB baseline MCTS cpuct
     * @param fpuA candidate MCTS FPU reduction
     * @param fpuB baseline MCTS FPU reduction
     * @param checkPriorA candidate MCTS check-prior bonus
     * @param checkPriorB baseline MCTS check-prior bonus
     * @param capturePenaltyA candidate MCTS SEE-losing-capture prior penalty
     * @param capturePenaltyB baseline MCTS SEE-losing-capture prior penalty
     * @param captureWinScaleA candidate MCTS positive-SEE capture prior scale
     * @param captureWinScaleB baseline MCTS positive-SEE capture prior scale
     * @param limits per-move search limits
     * @param maxPlies draw-adjudication ply cap
     * @return game outcome from the candidate's perspective
     */
    private static Outcome playGame(
            String fen,
            boolean candidateIsWhite,
            Set<AlphaBeta.Feature> featuresA,
            Set<AlphaBeta.Feature> featuresB,
            SearchKind searchA,
            SearchKind searchB,
            String evalA,
            String evalB,
            double cpuctA,
            double cpuctB,
            double fpuA,
            double fpuB,
            int checkPriorA,
            int checkPriorB,
            int capturePenaltyA,
            int capturePenaltyB,
            int captureWinScaleA,
            int captureWinScaleB,
            Limits limits,
            int maxPlies,
            int threadsA,
            int threadsB) {
        Position position = new Position(fen);
        GameSearcher candidate = searcher(searchA, evaluator(evalA), featuresA, threadsA, cpuctA, fpuA,
                checkPriorA, capturePenaltyA, captureWinScaleA);
        GameSearcher baseline = searcher(searchB, evaluator(evalB), featuresB, threadsB, cpuctB, fpuB,
                checkPriorB, capturePenaltyB, captureWinScaleB);
        Map<Long, Integer> seen = new HashMap<>();
        List<Long> history = new ArrayList<>();
        try {
            countPosition(seen, position);
            for (int ply = 0; ply < maxPlies; ply++) {
                MoveList legal = position.legalMoves();
                if (legal.isEmpty()) {
                    // Checkmate or stalemate; the side to move has no reply.
                    if (position.inCheck()) {
                        boolean whiteMated = position.isWhiteToMove();
                        return whiteMated == candidateIsWhite ? Outcome.LOSS : Outcome.WIN;
                    }
                    return Outcome.DRAW;
                }
                if (position.isInsufficientMaterial()
                        || position.halfMoveClock() >= 100
                        || seen.getOrDefault(position.signatureCore(), 0) >= 3) {
                    return Outcome.DRAW;
                }
                boolean candidateToMove = position.isWhiteToMove() == candidateIsWhite;
                GameSearcher engine = candidateToMove ? candidate : baseline;
                Result result = engine.search(position, limits, historyArray(history));
                short move = result.bestMove();
                if (move == Move.NO_MOVE || !isLegal(legal, move)) {
                    // Defensive: a stuck engine forfeits.
                    return candidateToMove ? Outcome.LOSS : Outcome.WIN;
                }
                history.add(position.signatureCore());
                position.play(move);
                countPosition(seen, position);
            }
            return Outcome.DRAW;
        } finally {
            candidate.close();
            baseline.close();
        }
    }

    /**
     * Builds a per-side searcher.
     *
     * @param kind search kind
     * @param evaluator evaluator instance
     * @param features alpha-beta features
     * @param threads worker threads
     * @param cpuct MCTS exploration constant
     * @param fpuReduction MCTS first-play urgency reduction
     * @param checkPriorBonus MCTS check-prior bonus
     * @param losingCapturePriorPenalty MCTS SEE-losing-capture prior penalty
     * @param winningCapturePriorScale MCTS positive-SEE capture prior scale
     * @return searcher wrapper
     */
    private static GameSearcher searcher(
            SearchKind kind,
            CentipawnEvaluator evaluator,
            Set<AlphaBeta.Feature> features,
            int threads,
            double cpuct,
            double fpuReduction,
            int checkPriorBonus,
            int losingCapturePriorPenalty,
            int winningCapturePriorScale) {
        if (kind == SearchKind.MCTS) {
            Mcts mcts = new Mcts(evaluator, cpuct, fpuReduction, checkPriorBonus, losingCapturePriorPenalty,
                    winningCapturePriorScale);
            mcts.setThreads(threads);
            return new GameSearcher() {
                /**
                 * Searches with the reusable MCTS tree and supplied repetition history.
                 */
                @Override
                public Result search(Position position, Limits limits, long[] history) {
                    return mcts.searchReusable(position, limits, null, history);
                }

                /**
                 * Releases the wrapped MCTS searcher.
                 */
                @Override
                public void close() {
                    mcts.close();
                }
            };
        }
        AlphaBeta alphaBeta = new AlphaBeta(evaluator, true, features).setSearchThreads(threads);
        return new GameSearcher() {
            /**
             * Searches with the alpha-beta engine.
             */
            @Override
            public Result search(Position position, Limits limits, long[] history) {
                return alphaBeta.search(position, limits);
            }

            /**
             * Releases the wrapped alpha-beta engine.
             */
            @Override
            public void close() {
                alphaBeta.close();
            }
        };
    }

    /**
     * Copies history keys to a primitive array.
     *
     * @param history pre-root history keys
     * @return primitive history
     */
    private static long[] historyArray(List<Long> history) {
        long[] out = new long[history.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = history.get(i).longValue();
        }
        return out;
    }

    /**
     * Records a position occurrence for threefold detection.
     *
     * @param seen occurrence counts by core signature
     * @param position current position
     */
    private static void countPosition(Map<Long, Integer> seen, Position position) {
        long key = position.signatureCore();
        seen.merge(key, 1, Integer::sum);
    }

    /**
     * Returns whether a move is present in a legal move list.
     *
     * @param legal legal move list
     * @param move move to find
     * @return true when legal
     */
    private static boolean isLegal(MoveList legal, short move) {
        for (int i = 0; i < legal.size(); i++) {
            if (legal.raw(i) == move) {
                return true;
            }
        }
        return false;
    }

    /**
     * Builds a fresh evaluator, falling back to classical when a net cannot load.
     *
     * @param eval evaluator name
     * @return evaluator instance
     */
    private static CentipawnEvaluator evaluator(String eval) {
        if ("nnue".equalsIgnoreCase(eval)) {
            try {
                return new Nnue(Path.of(NNUE_PATH));
            } catch (RuntimeException | java.io.IOException ex) {
                System.out.println("  (nnue unavailable: " + ex.getMessage() + " - using classical)");
            }
        }
        String normalized = eval.toLowerCase(java.util.Locale.ROOT);
        if (normalized.startsWith("scale") && normalized.length() > "scale".length()) {
            int scale = Integer.parseInt(normalized.substring("scale".length()));
            return new ExperimentalClassical(eval, 0, scale, false, false, false);
        }
        return switch (normalized) {
            case "rawclassical" -> new ExperimentalClassical(eval, 0, 100, false, false, false);
            case "tempo16" -> new ExperimentalClassical(eval, 8, 100, false, false, false);
            case "development" -> new ExperimentalClassical(eval, 0, 100, true, false, false);
            case "tropism" -> new ExperimentalClassical(eval, 0, 100, false, true, false);
            case "devtropism" -> new ExperimentalClassical(eval, 0, 100, true, true, false);
            case "drawish" -> new ExperimentalClassical(eval, 0, 100, false, false, true);
            default -> new Classical();
        };
    }

    /**
     * Experimental wrapper around the production classical evaluator.
     *
     * @param label evaluator label
     * @param extraTempoCp additional side-to-move tempo
     * @param scalePercent scalar applied to the base classical score
     * @param development whether to add opening-development pressure
     * @param tropism whether to add piece/king tropism
     * @param drawish whether to damp low-material pawnless endings
     */
    private record ExperimentalClassical(
            String label,
            int extraTempoCp,
            int scalePercent,
            boolean development,
            boolean tropism,
            boolean drawish) implements CentipawnEvaluator {

        /**
         * Production evaluator used for the base score and move priors.
         */
        private static final Classical BASE = new Classical();

        /**
         * {@inheritDoc}
         */
        @Override
        public int evaluate(Position position) {
            int score = Wdl.evaluateStmCentipawns(position) * scalePercent / 100;
            if (extraTempoCp != 0) {
                score += extraTempoCp;
            }
            int whiteExtra = 0;
            if (development) {
                whiteExtra += developmentWhite(position);
            }
            if (tropism) {
                whiteExtra += tropismWhite(position);
            }
            if (whiteExtra != 0) {
                score += position.isWhiteToMove() ? whiteExtra : -whiteExtra;
            }
            if (drawish) {
                score = dampDrawishEndgame(position, score);
            }
            return score;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void prepareMoveOrdering(Position position) {
            BASE.prepareMoveOrdering(position);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void scoreMoves(Position position, short[] moves, int[] scores) {
            BASE.scoreMoves(position, moves, scores);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String name() {
            return label;
        }
    }

    /**
     * Scores undeveloped back-rank pieces from White's perspective.
     *
     * @param position current position
     * @return centipawn term from White's perspective
     */
    private static int developmentWhite(Position position) {
        int material = nonKingMaterial(position);
        if (material < STARTING_MATERIAL / 2) {
            return 0;
        }
        int score = 0;
        score -= undeveloped(position, Field.B1, Piece.WHITE_KNIGHT, 10);
        score -= undeveloped(position, Field.G1, Piece.WHITE_KNIGHT, 10);
        score -= undeveloped(position, Field.C1, Piece.WHITE_BISHOP, 8);
        score -= undeveloped(position, Field.F1, Piece.WHITE_BISHOP, 8);
        score += undeveloped(position, Field.B8, Piece.BLACK_KNIGHT, 10);
        score += undeveloped(position, Field.G8, Piece.BLACK_KNIGHT, 10);
        score += undeveloped(position, Field.C8, Piece.BLACK_BISHOP, 8);
        score += undeveloped(position, Field.F8, Piece.BLACK_BISHOP, 8);
        return score * Math.min(material, STARTING_MATERIAL) / STARTING_MATERIAL;
    }

    /**
     * Returns a penalty when a home-square piece is still undeveloped.
     *
     * @param position current position
     * @param square home square
     * @param piece expected undeveloped piece
     * @param penalty penalty value
     * @return penalty when the piece is on the home square
     */
    private static int undeveloped(Position position, int square, byte piece, int penalty) {
        return position.pieceAt(square) == piece ? penalty : 0;
    }

    /**
     * Scores attacking-piece distance to the enemy king from White's perspective.
     *
     * @param position current position
     * @return centipawn term from White's perspective
     */
    private static int tropismWhite(Position position) {
        int material = nonKingMaterial(position);
        int phase = Math.min(material, STARTING_MATERIAL);
        int whiteKing = position.kingSquare(true);
        int blackKing = position.kingSquare(false);
        if (whiteKing < 0 || blackKing < 0) {
            return 0;
        }
        int white = sideTropism(position, true, blackKing);
        int black = sideTropism(position, false, whiteKing);
        return (white - black) * phase / STARTING_MATERIAL;
    }

    /**
     * Scores one side's attacking pieces by Chebyshev distance to the enemy king.
     *
     * @param position current position
     * @param white side to score
     * @param enemyKing enemy king square
     * @return side tropism score
     */
    private static int sideTropism(Position position, boolean white, int enemyKing) {
        int side = 0;
        side += tropismPieces(position.pieces(white ? Position.WHITE_QUEEN : Position.BLACK_QUEEN), enemyKing, 4);
        side += tropismPieces(position.pieces(white ? Position.WHITE_ROOK : Position.BLACK_ROOK), enemyKing, 2);
        side += tropismPieces(position.pieces(white ? Position.WHITE_BISHOP : Position.BLACK_BISHOP), enemyKing, 2);
        side += tropismPieces(position.pieces(white ? Position.WHITE_KNIGHT : Position.BLACK_KNIGHT), enemyKing, 3);
        return side;
    }

    /**
     * Scores a bitboard of pieces by proximity to the enemy king.
     *
     * @param pieces piece bitboard
     * @param enemyKing enemy king square
     * @param weight per-distance weight
     * @return tropism score
     */
    private static int tropismPieces(long pieces, int enemyKing, int weight) {
        int score = 0;
        long remaining = pieces;
        while (remaining != 0L) {
            int square = Long.numberOfTrailingZeros(remaining);
            remaining &= remaining - 1L;
            score += Math.max(0, 7 - kingDistance(square, enemyKing)) * weight;
        }
        return score;
    }

    /**
     * Damps optimistic scores in pawnless low-material endings.
     *
     * @param position current position
     * @param score side-to-move score
     * @return possibly damped score
     */
    private static int dampDrawishEndgame(Position position, int score) {
        if ((position.pieces(Position.WHITE_PAWN) | position.pieces(Position.BLACK_PAWN)) != 0L) {
            return score;
        }
        int material = nonKingMaterial(position);
        if (material <= Piece.VALUE_ROOK + Piece.VALUE_BISHOP) {
            return score * 65 / 100;
        }
        if (material <= 2 * Piece.VALUE_ROOK + 2 * Piece.VALUE_BISHOP) {
            return score * 80 / 100;
        }
        return score;
    }

    /**
     * Returns total non-king material for both sides.
     *
     * @param position current position
     * @return material in centipawns
     */
    private static int nonKingMaterial(Position position) {
        int total = 0;
        byte[] board = position.getBoard();
        for (byte piece : board) {
            int type = Math.abs(piece);
            if (type != Piece.KING) {
                total += Piece.getValue(piece);
            }
        }
        return total;
    }

    /**
     * Chebyshev king distance between two squares.
     *
     * @param a first square
     * @param b second square
     * @return king distance
     */
    private static int kingDistance(int a, int b) {
        return Math.max(Math.abs((a & 7) - (b & 7)), Math.abs((a >>> 3) - (b >>> 3)));
    }

    /**
     * Prints the match result and a point Elo estimate.
     *
     * @param win candidate wins
     * @param draw draws
     * @param loss candidate losses
     */
    private static void report(int win, int draw, int loss) {
        int games = win + draw + loss;
        double score = games == 0 ? 0.0 : (win + 0.5 * draw) / games;
        System.out.println("----");
        System.out.printf("Result (candidate A vs baseline B): +%d =%d -%d  of %d games%n",
                win, draw, loss, games);
        System.out.printf("Score: %.1f%%%n", score * 100.0);
        if (score <= 0.0 || score >= 1.0) {
            System.out.println("Elo: " + (score >= 1.0 ? "+inf (no losses)" : "-inf (no wins)"));
        } else {
            double elo = -400.0 * Math.log10(1.0 / score - 1.0);
            System.out.printf("Elo estimate: %+.0f%n", elo);
        }
    }

    /**
     * Parses {@code --key value} flags into a map.
     *
     * @param args raw arguments
     * @return option map
     */
    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> opts = new HashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            String key = args[i];
            if (key.startsWith("--")) {
                opts.put(key.substring(2), args[i + 1]);
            }
        }
        return opts;
    }

    /**
     * Parses a search kind name.
     *
     * @param name search name
     * @return parsed search kind
     */
    private static SearchKind parseSearch(String name) {
        String normalized = name.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "ab", "alpha", "alphabeta", "alpha-beta" -> SearchKind.ALPHA_BETA;
            case "mcts", "puct" -> SearchKind.MCTS;
            default -> throw new IllegalArgumentException("unknown search kind: " + name);
        };
    }

    /**
     * Returns the command-line name for a search kind.
     *
     * @param search search kind
     * @return stable command-line name
     */
    private static String searchName(SearchKind search) {
        return switch (search) {
            case ALPHA_BETA -> "alpha-beta";
            case MCTS -> "mcts";
        };
    }

    /**
     * Parses a feature CSV (or {@code all}/{@code none}) into a feature set.
     *
     * @param csv feature specification
     * @return parsed feature set
     */
    private static Set<AlphaBeta.Feature> parseFeatures(String csv) {
        String trimmed = csv.trim();
        if (trimmed.equalsIgnoreCase("all")) {
            return EnumSet.allOf(AlphaBeta.Feature.class);
        }
        if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("none")) {
            return EnumSet.noneOf(AlphaBeta.Feature.class);
        }
        List<AlphaBeta.Feature> parsed = new ArrayList<>();
        for (String token : trimmed.split(",")) {
            String name = token.trim();
            if (!name.isEmpty()) {
                parsed.add(AlphaBeta.Feature.valueOf(name));
            }
        }
        return parsed.isEmpty() ? EnumSet.noneOf(AlphaBeta.Feature.class) : EnumSet.copyOf(parsed);
    }
}
