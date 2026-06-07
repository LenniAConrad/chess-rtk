package chess.engine;

import chess.classical.Wdl;
import chess.core.Field;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Piece;
import chess.core.Position;
import chess.eval.CentipawnEvaluator;
import chess.eval.Classical;
import chess.eval.Nnue;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Deterministic self-play A/B gauntlet engine for the built-in searchers.
 *
 * <p>
 * Pits a candidate configuration against a baseline at an equal, fixed per-move
 * budget (node count by default, or wall-clock time) so a search or evaluation
 * change's strength effect is <em>measured</em> by game results rather than
 * asserted. Both engines run in one process against a set of varied opening
 * positions, each played from both colors, and the aggregate score is returned
 * from the candidate's perspective.
 * </p>
 *
 * <p>
 * This is the reusable runner shared by the {@code crtk engine gauntlet} CLI
 * command, the workbench Engine Lab, and the bundled standalone harness. It owns
 * no input/output: callers pass a {@link Config}, observe progress through a
 * {@link ProgressListener}, and render the returned {@link Score} themselves.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Gauntlet {

    /**
     * Search algorithm used by one side in a gauntlet game.
     */
    public enum SearchKind {

        /**
         * Iterative-deepening alpha-beta.
         */
        ALPHA_BETA,

        /**
         * PUCT Monte-Carlo tree search.
         */
        MCTS;

        /**
         * Parses a search-kind name accepting the common command-line spellings.
         *
         * @param name search name
         * @return parsed search kind
         * @throws IllegalArgumentException for an unknown name
         */
        public static SearchKind parse(String name) {
            String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "ab", "alpha", "alphabeta", "alpha-beta" -> ALPHA_BETA;
                case "mcts", "puct" -> MCTS;
                default -> throw new IllegalArgumentException("unknown search kind: " + name);
            };
        }

        /**
         * Returns the stable command-line name for this search kind.
         *
         * @return command-line name
         */
        public String cliName() {
            return switch (this) {
                case ALPHA_BETA -> "alpha-beta";
                case MCTS -> "mcts";
            };
        }
    }

    /**
     * Receives gauntlet progress and informational notes.
     *
     * <p>
     * {@link #onProgress} is invoked in opening order from a single thread.
     * {@link #onNote} may be invoked from worker threads and must be safe to
     * call concurrently.
     * </p>
     */
    public interface ProgressListener {

        /**
         * No-op listener.
         */
        ProgressListener NONE = new ProgressListener() {
        };

        /**
         * Reports completion of one opening pair.
         *
         * @param completed completed opening pairs
         * @param total total opening pairs
         * @param running running score from the candidate's perspective
         */
        default void onProgress(int completed, int total, Score running) {
            // optional
        }

        /**
         * Reports an informational note such as an evaluator fallback.
         *
         * @param message human-readable note
         */
        default void onNote(String message) {
            // optional
        }
    }

    /**
     * Aggregate match score from the candidate's perspective.
     *
     * @param win candidate wins
     * @param draw draws
     * @param loss candidate losses
     */
    public record Score(int win, int draw, int loss) {

        /**
         * Returns the total number of games played.
         *
         * @return game count
         */
        public int games() {
            return win + draw + loss;
        }

        /**
         * Returns the candidate's score fraction in {@code [0, 1]}.
         *
         * @return score fraction, or zero when no games were played
         */
        public double fraction() {
            int games = games();
            return games == 0 ? 0.0 : (win + 0.5 * draw) / games;
        }

        /**
         * Returns whether a finite Elo estimate exists (some wins and losses).
         *
         * @return true when a finite Elo estimate is defined
         */
        public boolean hasEloEstimate() {
            double fraction = fraction();
            return fraction > 0.0 && fraction < 1.0;
        }

        /**
         * Returns the point Elo estimate from the candidate's perspective.
         *
         * @return Elo estimate; only meaningful when {@link #hasEloEstimate()}
         */
        public double elo() {
            double fraction = fraction();
            return -400.0 * Math.log10(1.0 / fraction - 1.0);
        }
    }

    /**
     * Immutable gauntlet configuration.
     *
     * @param featuresA candidate alpha-beta features
     * @param featuresB baseline alpha-beta features
     * @param searchA candidate search kind
     * @param searchB baseline search kind
     * @param evalA candidate evaluator name
     * @param evalB baseline evaluator name
     * @param nodes fixed node budget per move (used when {@code movetime} is zero)
     * @param movetime fixed time budget per move in milliseconds, or zero
     * @param cpuctA candidate MCTS cpuct
     * @param cpuctB baseline MCTS cpuct
     * @param fpuA candidate MCTS first-play-urgency reduction
     * @param fpuB baseline MCTS first-play-urgency reduction
     * @param checkPriorA candidate MCTS check-prior bonus
     * @param checkPriorB baseline MCTS check-prior bonus
     * @param capturePenaltyA candidate MCTS SEE-losing-capture prior penalty
     * @param capturePenaltyB baseline MCTS SEE-losing-capture prior penalty
     * @param captureWinScaleA candidate MCTS positive-SEE capture prior scale
     * @param captureWinScaleB baseline MCTS positive-SEE capture prior scale
     * @param maxPlies draw-adjudication ply cap
     * @param openingCount seeded-random opening count, or zero for the curated set
     * @param seed RNG seed for reproducible openings
     * @param threadsA candidate search threads
     * @param threadsB baseline search threads
     * @param workers concurrent opening-pair workers
     */
    public record Config(
            Set<AlphaBeta.Feature> featuresA,
            Set<AlphaBeta.Feature> featuresB,
            SearchKind searchA,
            SearchKind searchB,
            String evalA,
            String evalB,
            long nodes,
            long movetime,
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
            int maxPlies,
            int openingCount,
            long seed,
            int threadsA,
            int threadsB,
            int workers) {

        /**
         * Returns the per-move search limits implied by this configuration.
         *
         * @return per-move limits
         */
        public Limits limits() {
            return movetime > 0
                    ? new Limits(AlphaBeta.MAX_DEPTH, 0L, movetime)
                    : new Limits(AlphaBeta.MAX_DEPTH, nodes, 0L);
        }

        /**
         * Returns a human-readable per-move budget description.
         *
         * @return budget description
         */
        public String budgetText() {
            return movetime > 0 ? movetime + "ms/move" : nodes + " nodes/move";
        }
    }

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
     * Path to the bundled NNUE network, for {@code nnue} evaluators.
     */
    private static final String NNUE_PATH = "models/crtk-halfkp.nnue";

    /**
     * Starting non-king material for both sides.
     */
    private static final int STARTING_MATERIAL = 7_800;

    /**
     * Prevents instantiation.
     */
    private Gauntlet() {
        // utility
    }

    /**
     * Resolves the opening positions for a configuration.
     *
     * <p>
     * Returns the curated opening set when {@link Config#openingCount()} is zero,
     * otherwise a reproducible seeded-random set of that size.
     * </p>
     *
     * @param config gauntlet configuration
     * @return opening FENs (each played from both colors)
     */
    public static String[] openings(Config config) {
        return config.openingCount() > 0
                ? generateOpenings(config.openingCount(), config.seed())
                : OPENINGS.clone();
    }

    /**
     * Returns the number of openings in the curated default set.
     *
     * @return curated opening count
     */
    public static int curatedOpeningCount() {
        return OPENINGS.length;
    }

    /**
     * Runs the gauntlet over the supplied openings.
     *
     * @param config gauntlet configuration
     * @param openings opening FENs (see {@link #openings(Config)})
     * @param listener progress listener, or {@code null} for none
     * @return aggregate score from the candidate's perspective
     */
    public static Score run(Config config, String[] openings, ProgressListener listener) {
        if (config.workers() < 1) {
            throw new IllegalArgumentException("workers must be positive");
        }
        ProgressListener sink = listener == null ? ProgressListener.NONE : listener;
        // Probe each evaluator once so a missing NNUE is reported a single time
        // rather than per game.
        probeEvaluator(config.evalA(), sink);
        if (!config.evalB().equalsIgnoreCase(config.evalA())) {
            probeEvaluator(config.evalB(), sink);
        }
        Limits limits = config.limits();
        return config.workers() == 1
                ? runSequential(config, openings, limits, sink)
                : runParallel(config, openings, limits, sink);
    }

    /**
     * Runs opening pairs one at a time.
     *
     * @param config gauntlet configuration
     * @param openings opening FENs
     * @param limits per-move search limits
     * @param listener progress listener
     * @return aggregate score
     */
    private static Score runSequential(Config config, String[] openings, Limits limits,
            ProgressListener listener) {
        int[] score = { 0, 0, 0 };
        for (int i = 0; i < openings.length; i++) {
            add(score, playOpeningPair(openings[i], config, limits));
            listener.onProgress(i + 1, openings.length, new Score(score[0], score[1], score[2]));
        }
        return new Score(score[0], score[1], score[2]);
    }

    /**
     * Runs opening pairs concurrently, collecting futures in opening order so the
     * progress stream remains deterministic.
     *
     * @param config gauntlet configuration
     * @param openings opening FENs
     * @param limits per-move search limits
     * @param listener progress listener
     * @return aggregate score
     */
    private static Score runParallel(Config config, String[] openings, Limits limits,
            ProgressListener listener) {
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(config.workers(), openings.length));
        try {
            List<Future<OpeningResult>> futures = new ArrayList<>(openings.length);
            for (String opening : openings) {
                futures.add(pool.submit(() -> playOpeningPair(opening, config, limits)));
            }
            int[] score = { 0, 0, 0 };
            for (int i = 0; i < futures.size(); i++) {
                add(score, futures.get(i).get());
                listener.onProgress(i + 1, openings.length, new Score(score[0], score[1], score[2]));
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
     * @param config gauntlet configuration
     * @param limits per-move search limits
     * @return two-game result for the opening
     */
    private static OpeningResult playOpeningPair(String opening, Config config, Limits limits) {
        // Play each opening from both colors so a result is not just one
        // deterministic game counted twice.
        Outcome first = playGame(opening, true, config, limits);
        Outcome second = playGame(opening, false, config, limits);
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
        Random rng = new Random(seed);
        LinkedHashSet<String> set = new LinkedHashSet<>();
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
     * @param config gauntlet configuration
     * @param limits per-move search limits
     * @return game outcome from the candidate's perspective
     */
    private static Outcome playGame(String fen, boolean candidateIsWhite, Config config, Limits limits) {
        Position position = new Position(fen);
        GameSearcher candidate = searcher(config.searchA(), evaluator(config.evalA()), config.featuresA(),
                config.threadsA(), config.cpuctA(), config.fpuA(), config.checkPriorA(),
                config.capturePenaltyA(), config.captureWinScaleA());
        GameSearcher baseline = searcher(config.searchB(), evaluator(config.evalB()), config.featuresB(),
                config.threadsB(), config.cpuctB(), config.fpuB(), config.checkPriorB(),
                config.capturePenaltyB(), config.captureWinScaleB());
        Map<Long, Integer> seen = new HashMap<>();
        List<Long> history = new ArrayList<>();
        try {
            countPosition(seen, position);
            for (int ply = 0; ply < config.maxPlies(); ply++) {
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
     * Builds an evaluator once and reports through the listener if a requested
     * NNUE network is unavailable and a classical fallback will be used.
     *
     * @param eval evaluator name
     * @param listener progress listener
     */
    private static void probeEvaluator(String eval, ProgressListener listener) {
        if ("nnue".equalsIgnoreCase(eval)) {
            try {
                new Nnue(Path.of(NNUE_PATH));
            } catch (RuntimeException | java.io.IOException ex) {
                listener.onNote("nnue unavailable: " + ex.getMessage() + " - using classical");
            }
        }
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
                // Reported once via probeEvaluator; fall back silently per game.
                return new Classical();
            }
        }
        String normalized = eval.toLowerCase(Locale.ROOT);
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
     * Parses a feature CSV (or {@code all}/{@code none}) into a feature set.
     *
     * @param csv feature specification
     * @return parsed feature set
     */
    public static Set<AlphaBeta.Feature> parseFeatures(String csv) {
        String trimmed = csv == null ? "" : csv.trim();
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
