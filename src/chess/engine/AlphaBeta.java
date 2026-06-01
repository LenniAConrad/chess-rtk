package chess.engine;

import java.util.Arrays;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import chess.core.Move;
import chess.core.MoveList;
import chess.core.Piece;
import chess.core.Position;
import chess.eval.CentipawnEvaluator;
import chess.eval.Classical;
import chess.eval.See;

/**
 * Built-in zero-dependency position searcher.
 *
 * <p>
 * The searcher is intentionally small and deterministic. It uses the repository's
 * bitboard legal move generator, mutable make/undo support, and a selectable
 * centipawn evaluator, then searches with iterative deepening and negamax
 * alpha-beta pruning.
 * </p>
 *
 * <p>
 * This is not meant to replace an external UCI engine such as Stockfish for
 * deep analysis. It provides an in-process fallback for best-move selection,
 * smoke tests, and automation workflows where starting a separate engine
 * process is undesirable.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */

public final class AlphaBeta implements AutoCloseable {

    /**
     * Maximum supported iterative-deepening depth. The value also bounds
     * internal principal-variation scratch storage.
     */
    public static final int MAX_DEPTH = 64;

    /**
     * Base mate sentinel used before adding or subtracting ply distance.
     */
    public static final int MATE_SCORE = 900_000;

    /**
     * Scores at or beyond this absolute value are treated as mates.
     */
    public static final int MATE_THRESHOLD = MATE_SCORE - 1_000;

    /**
     * Alpha-beta infinity bound. It must be greater than all static scores and
     * less than {@link #MATE_SCORE}.
     */
    private static final int INF = 1_000_000;

    /**
     * Highest non-mate static score returned by the evaluator.
     */
    private static final int MAX_STATIC_SCORE = 100_000;

    /**
     * Sentinel for optional in-band score helper results. It is outside the
     * engine's normal score range and means "no score was available."
     */
    static final int NO_SCORE = Integer.MIN_VALUE;

    /**
     * Maximum quiescence recursion below the nominal search horizon.
     */
    private static final int QUIESCENCE_MAX_PLY = 8;

    /**
     * Initial centipawn window for aspiration searches after early iterations.
     */
    private static final int ASPIRATION_WINDOW = 50;

    /**
     * Depth from which aspiration windows are used around the previous score.
     */
    private static final int ASPIRATION_START_DEPTH = 4;

    /**
     * Minimum remaining depth required before null-move pruning is attempted.
     */
    private static final int NULL_MOVE_MIN_DEPTH = 3;

    /**
     * Maximum remaining depth where shallow futility pruning is considered.
     */
    private static final int FUTILITY_MAX_DEPTH = 3;

    /**
     * Base centipawn margin used by shallow futility pruning.
     */
    private static final int FUTILITY_MARGIN = 150;

    /**
     * Base centipawn margin used by quiescence delta pruning.
     */
    private static final int DELTA_MARGIN = 200;

    /**
     * Maximum remaining depth where reverse futility pruning is considered.
     */
    private static final int REVERSE_FUTILITY_MAX_DEPTH = 4;

    /**
     * Base centipawn margin used by reverse futility pruning.
     */
    private static final int REVERSE_FUTILITY_MARGIN = 120;

    /**
     * Maximum depth where move-count-based late-move pruning is considered.
     */
    private static final int LATE_MOVE_PRUNING_MAX_DEPTH = 5;

    /**
     * Node interval between wall-clock stop checks.
     */
    private static final int STOP_CHECK_INTERVAL = 1024;

    /**
     * Slow searches keep checking time every node until they pass this count.
     */
    private static final int STOP_CHECK_EVERY_NODE_UNTIL = 512;

    /**
     * Default transposition-table size. Must be a power of two.
     */
    private static final int TRANSPOSITION_ENTRIES = 1 << 20;

    /**
     * Default static-evaluation cache size. Must be a power of two.
     */
    private static final int EVAL_CACHE_ENTRIES = 1 << 18;

    /**
     * Exact transposition-table bound flag.
     */
    private static final byte TT_EXACT = 0;

    /**
     * Lower-bound transposition-table flag for fail-high entries.
     */
    private static final byte TT_LOWER = 1;

    /**
     * Upper-bound transposition-table flag for fail-low entries.
     */
    private static final byte TT_UPPER = 2;

    /**
     * Shared stop signal used as allocation-free recursive control flow.
     */
    private static final SearchStopped SEARCH_STOPPED = new SearchStopped();

    /**
     * Static evaluator used at leaf nodes.
     */
    private final CentipawnEvaluator evaluator;

    /**
     * Optional transposition table reused across successive {@link #search}
     * calls. When non-null, search knowledge (cutoffs, best moves) carries over
     * between moves of one game, which is a free strength gain; when null, each
     * search uses a fresh per-call table (the historical default). A reused
     * table also needs a non-resetting generation counter, supplied by
     * {@link #generationBase}.
     */
    private final TranspositionTable persistentTranspositions;

    /**
     * Generation offset applied to each search when {@link #persistentTranspositions}
     * is shared, so entries stored by earlier moves are not mistaken for the
     * current move's generation. Advanced after each completed search.
     */
    private int generationBase;

    /**
     * Optional search techniques, individually toggleable so a self-play harness
     * can measure each one's contribution in isolation. Production builds enable
     * every feature (the strongest configuration); a baseline opponent can be
     * constructed with a reduced set to A/B a single change.
     */
    public enum Feature {
        /**
         * SEE-based pruning of losing captures in quiescence, plus SEE-keyed
         * capture ordering in the main search.
         */
        SEE_PRUNING,

        /**
         * One-ply search extension for moves that give check.
         */
        CHECK_EXTENSION,

        /**
         * Draw detection by repetition inside the search tree (the engine could
         * not previously see repetitions/perpetuals during search).
         */
        SEARCH_REPETITION,

        /**
         * Late-move reductions from a {@code log(depth)*log(moveNumber)} table
         * with a history adjustment, replacing the coarse fixed-step ladder.
         */
        LMR_TABLE,

        /**
         * History "gravity" updates with a malus: on a quiet beta cutoff the
         * cutoff move is rewarded and the quiet moves tried before it are
         * penalized, with both bounded by a decay term, instead of the
         * bonus-only, monotonically-growing history table.
         */
        HISTORY_MALUS,

        /**
         * Late-move reductions also apply to SEE-losing captures (normally exempt
         * as tactical), since a capture that loses material is rarely worth a
         * full-depth search and is already ordered last.
         */
        SEE_LMR
    }

    /**
     * Bound on the magnitude of a gravity history value (used by
     * {@link Feature#HISTORY_MALUS}).
     */
    private static final int HISTORY_MAX = 1 << 14;

    /**
     * Precomputed late-move reduction by [remaining depth][move number], using
     * the conventional {@code log(d)*log(m)} growth so reductions scale smoothly
     * instead of in coarse steps. Indexed with both dimensions clamped to 63.
     */
    private static final int[][] LMR_TABLE = new int[64][64];

    static {
        for (int d = 1; d < 64; d++) {
            for (int m = 1; m < 64; m++) {
                LMR_TABLE[d][m] = (int) Math.round(0.75 + Math.log(d) * Math.log(m) / 2.25);
            }
        }
    }

    /**
     * Default enabled features for production searches. SEE pruning is a measured
     * strength gain (~+70 Elo in self-play) and repetition detection is a
     * correctness fix that measured strength-neutral. Check extensions are
     * deliberately excluded: they measured net-negative at this engine's budgets
     * (they remain available through the three-argument constructor for tuning).
     */
    private static final Set<Feature> DEFAULT_FEATURES =
            EnumSet.of(Feature.SEE_PRUNING, Feature.SEARCH_REPETITION);

    /**
     * Enabled search features; see {@link Feature}.
     */
    private final Set<Feature> features;

    /**
     * Number of parallel search threads (Lazy SMP). One = single-threaded (the
     * default and historical behavior). With more than one, helper threads share
     * the transposition table to deepen the main thread's search; this requires a
     * thread-safe evaluator (the stateless {@link Classical} is; share-safe
     * neural evaluators need per-thread instances, not yet wired).
     */
    private volatile int searchThreads = 1;

    /**
     * Creates a searcher with the classical evaluator.
     */
    public AlphaBeta() {
        this(new Classical());
    }

    /**
     * Creates a searcher with a caller-selected evaluator and a fresh
     * transposition table per search.
     *
     * @param evaluator static evaluator
     */
    public AlphaBeta(CentipawnEvaluator evaluator) {
        this(evaluator, false);
    }

    /**
     * Creates a searcher with a caller-selected evaluator, optionally reusing a
     * single transposition table across all {@link #search} calls so search
     * knowledge carries across moves of one game.
     *
     * @param evaluator static evaluator
     * @param persistentTable true to keep one transposition table across moves
     */
    public AlphaBeta(CentipawnEvaluator evaluator, boolean persistentTable) {
        this(evaluator, persistentTable, DEFAULT_FEATURES);
    }

    /**
     * Creates a searcher with an explicit set of enabled search features, so a
     * self-play harness can measure a single technique's contribution. Production
     * code should use the two-argument constructor, which enables every feature.
     *
     * @param evaluator static evaluator
     * @param persistentTable true to keep one transposition table across moves
     * @param features search features to enable
     */
    public AlphaBeta(CentipawnEvaluator evaluator, boolean persistentTable, Set<Feature> features) {
        if (evaluator == null) {
            throw new IllegalArgumentException("evaluator == null");
        }
        if (features == null) {
            throw new IllegalArgumentException("features == null");
        }
        this.evaluator = evaluator;
        this.persistentTranspositions = persistentTable
                ? new TranspositionTable(TRANSPOSITION_ENTRIES)
                : null;
        this.features = features.isEmpty()
                ? EnumSet.noneOf(Feature.class)
                : EnumSet.copyOf(features);
    }

    /**
     * Sets the number of parallel search threads (Lazy SMP). Values below one
     * are treated as one. Only safe with a thread-safe evaluator (e.g.
     * {@link Classical}); intended for fixed-time search.
     *
     * @param threads thread count
     * @return this searcher, for chaining
     */
    public AlphaBeta setSearchThreads(int threads) {
        this.searchThreads = Math.max(1, threads);
        return this;
    }

    /**
     * Searches a position using default limits.
     *
     * @param position position to search
     * @return search result
     */
    public Result search(Position position) {
        return search(position, Limits.defaults());
    }

    /**
     * Searches a position under explicit limits.
     *
     * @param position position to search
     * @param limits resource limits
     * @return search result
     * @throws IllegalArgumentException if an argument is null
     */
    public Result search(Position position, Limits limits) {
        return search(position, limits, null);
    }

    /**
     * Searches a position under explicit limits, notifying after each completed
     * iterative-deepening depth.
     *
     * @param position position to search
     * @param limits resource limits
     * @param listener optional completed-depth listener
     * @return search result
     * @throws IllegalArgumentException if an argument is null
     */
    public Result search(Position position, Limits limits, SearchListener listener) {
        if (position == null) {
            throw new IllegalArgumentException("position == null");
        }
        if (limits == null) {
            throw new IllegalArgumentException("limits == null");
        }

        long started = System.currentTimeMillis();
        Position root = position.copy();
        MoveList legalMoves = root.legalMoves();
        if (legalMoves.isEmpty()) {
            int terminal = terminalScore(root, 0);
            return new Result(
                    Move.NO_MOVE,
                    terminal,
                    0,
                    0L,
                    elapsedSince(started),
                    false,
                    new short[0]);
        }

        // Use the shared table when this searcher persists one across moves,
        // otherwise a fresh per-search table. The generation base ensures a
        // shared table's prior-move entries are not seen as the current search's.
        boolean shared = persistentTranspositions != null;
        TranspositionTable table = shared ? persistentTranspositions : new TranspositionTable(TRANSPOSITION_ENTRIES);
        int generationStart = shared ? generationBase : 0;
        int threads = Math.max(1, searchThreads);
        if (threads == 1) {
            return runWorker(position, started, limits, listener, table, generationStart, shared, true, 0);
        }

        // Lazy SMP: helper threads share the transposition table and deepen the
        // main thread's search by cross-pollinating cutoffs and best moves. Each
        // helper skips a different subset of iterative-deepening depths (see
        // skipIteration) so the threads explore different depths concurrently and
        // fill the table diversely instead of duplicating one search. The main
        // thread (index 0) owns the returned result, the listener, and the
        // shared-table generation bookkeeping; helpers only fill the table.
        List<Thread> helpers = new ArrayList<>();
        for (int t = 1; t < threads; t++) {
            final int threadIndex = t;
            Thread helper = new Thread(() -> {
                try {
                    runWorker(position, started, limits, null, table, generationStart, shared, false, threadIndex);
                } catch (RuntimeException ignored) {
                    // a failed helper must not abort the main search
                }
            }, "ab-smp-" + t);
            helper.setDaemon(true);
            helpers.add(helper);
            helper.start();
        }
        Result best = runWorker(position, started, limits, listener, table, generationStart, shared, true, 0);
        for (Thread helper : helpers) {
            try {
                helper.join();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
        return best;
    }

    /**
     * Runs one search worker on its own position copy and {@link SearchContext},
     * sharing the given transposition table. Used directly for single-threaded
     * search and as the per-thread body for Lazy SMP.
     *
     * @param position original position (copied internally)
     * @param started search start timestamp
     * @param limits resource limits
     * @param listener optional completed-depth listener (main thread only)
     * @param table transposition table (shared across threads)
     * @param generationStart starting generation for the table
     * @param shared whether the table persists across searches
     * @param manageGeneration whether this worker advances the shared generation
     * @return this worker's search result
     */
    private Result runWorker(
            Position position,
            long started,
            Limits limits,
            SearchListener listener,
            TranspositionTable table,
            int generationStart,
            boolean shared,
            boolean manageGeneration,
            int threadIndex) {
        Position root = position.copy();
        MoveList legalMoves = root.legalMoves();
        try (SearchContext context =
                new SearchContext(started, limits, evaluator, root, table, generationStart, features)) {
            Result best = staticRootResult(root, legalMoves, context, started);
            if (context.shouldStop()) {
                Result stopped = best.withRuntime(context.nodes, elapsedSince(started), true);
                notifyDepth(listener, stopped);
                if (manageGeneration) {
                    advanceGeneration(shared, context);
                }
                return stopped;
            }
            short preferred = best.bestMove();
            for (int depth = 1; depth <= limits.depth(); depth++) {
                if (threadIndex > 0 && skipIteration(threadIndex, depth)) {
                    // Helper diversification: this thread skips this depth so the
                    // pool covers different depths at once (Lazy SMP). The main
                    // thread (index 0) never skips, so the result is unaffected.
                    continue;
                }
                try {
                    context.newIteration();
                    RootOutcome outcome = searchRootWithAspiration(
                            context,
                            root,
                            legalMoves,
                            depth,
                            preferred,
                            best.scoreCentipawns());
                    preferred = outcome.bestMove();
                    best = new Result(
                            outcome.bestMove(),
                            outcome.score(),
                            depth,
                            context.nodes,
                            elapsedSince(started),
                            false,
                            outcome.principalVariation());
                    notifyDepth(listener, best);
                } catch (SearchStopped ignored) {
                    if (manageGeneration) {
                        advanceGeneration(shared, context);
                    }
                    return best.withRuntime(context.nodes, elapsedSince(started), true);
                }
            }
            if (manageGeneration) {
                advanceGeneration(shared, context);
            }
            return best.withRuntime(context.nodes, elapsedSince(started), false);
        }
    }

    /**
     * Advances the shared-table generation base past the generations this search
     * used, so a later search on the same persistent table writes newer entries.
     * A no-op when the table is not shared.
     *
     * @param shared whether a persistent table is in use
     * @param context the just-completed search context
     */
    private void advanceGeneration(boolean shared, SearchContext context) {
        if (shared) {
            generationBase = context.generation + 1;
        }
    }

    /**
     * Per-helper iterative-deepening skip pattern (the classic Lazy SMP scheme).
     * Helper threads skip runs of depths so that, at any instant, the pool is
     * searching several different depths and filling the shared table with
     * diverse entries rather than all repeating the main thread's search.
     */
    private static final int[] SKIP_SIZE =
            { 1, 1, 2, 2, 2, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5 };

    /**
     * Depth-phase offsets paired with {@link #SKIP_SIZE}.
     */
    private static final int[] SKIP_PHASE =
            { 0, 1, 0, 1, 2, 0, 1, 2, 0, 1, 2, 3, 0, 1, 2, 3 };

    /**
     * Returns whether a helper thread should skip searching this depth, so the
     * helper pool diversifies across depths (Lazy SMP). The main thread
     * (index 0) never skips.
     *
     * @param threadIndex helper thread index (1-based for helpers)
     * @param depth iterative-deepening depth
     * @return true to skip this depth on this helper
     */
    private static boolean skipIteration(int threadIndex, int depth) {
        int i = (threadIndex - 1) % SKIP_SIZE.length;
        return ((depth + SKIP_PHASE[i]) / SKIP_SIZE[i]) % 2 == 1;
    }

    /**
     * Notifies a completed-depth listener.
     *
     * @param listener optional listener
     * @param result completed-depth result
     */
    private static void notifyDepth(SearchListener listener, Result result) {
        if (listener != null) {
            listener.onDepth(result);
        }
    }

    /**
     * Runs one root iteration, using a narrow score window once a previous
     * iteration has produced a stable non-mate score.
     *
     * @param context search context
     * @param root mutable root position
     * @param legalMoves legal root moves
     * @param depth target depth
     * @param preferredMove move to order first, or {@link Move#NO_MOVE}
     * @param previousScore completed previous-iteration score
     * @return root outcome with an exact score for the completed depth
     */
    private static RootOutcome searchRootWithAspiration(
            SearchContext context,
            Position root,
            MoveList legalMoves,
            int depth,
            short preferredMove,
            int previousScore) {
        if (depth < ASPIRATION_START_DEPTH || isMateScore(previousScore)) {
            return searchRoot(context, root, legalMoves, depth, preferredMove, -INF, INF);
        }

        int window = ASPIRATION_WINDOW;
        int alpha = Math.max(-INF, previousScore - window);
        int beta = Math.min(INF, previousScore + window);
        while (true) {
            RootOutcome outcome = searchRoot(context, root, legalMoves, depth, preferredMove, alpha, beta);
            AspirationWindow widened = widenedAspirationWindow(outcome.score(), alpha, beta, window);
            if (widened == null) {
                return outcome;
            }
            alpha = widened.alpha();
            beta = widened.beta();
            window = widened.width();
        }
    }

    /**
     * Widens a failed aspiration search window.
     *
     * @param score score returned by the last root search
     * @param alpha previous alpha bound
     * @param beta previous beta bound
     * @param width previous centipawn window width
     * @return widened bounds, or null when the previous score was inside the
     *         window
     */
    private static AspirationWindow widenedAspirationWindow(int score, int alpha, int beta, int width) {
        int widenedWidth = width * 2;
        if (score <= alpha && alpha > -INF) {
            return new AspirationWindow(Math.max(-INF, score - widenedWidth), beta, widenedWidth);
        }
        if (score >= beta && beta < INF) {
            return new AspirationWindow(alpha, Math.min(INF, score + widenedWidth), widenedWidth);
        }
        return null;
    }

    /**
     * Runs one root search iteration.
     *
     * @param context search context
     * @param root mutable root position
     * @param legalMoves legal root moves
     * @param depth target depth
     * @param preferredMove move to order first, or {@link Move#NO_MOVE}
     * @param alpha initial alpha bound
     * @param beta initial beta bound
     * @return root outcome
     */
    private static RootOutcome searchRoot(
            SearchContext context,
            Position root,
            MoveList legalMoves,
            int depth,
            short preferredMove,
            int alpha,
            int beta) {
        context.clearPv();
        context.prepareMoveOrdering(root);
        short ttMove = context.transpositions.bestMove(root.signature());
        short[] moves = orderedMoves(root, legalMoves, preferredMove, ttMove, context, 0);
        Position.State state = new Position.State();
        RootSearchState searchState = new RootSearchState(depth, alpha);

        for (short move : moves) {
            int score = searchRootMove(context, root, move, depth, beta, searchState, state);
            searchState.recordScore(context, move, score, depth);
            if (searchState.alpha >= beta) {
                recordCutoff(root, context, move, depth, 0);
                break;
            }
        }
        byte flag = transpositionFlag(searchState.bestScore, searchState.originalAlpha, beta);
        context.transpositions.store(
                root.signature(),
                depth,
                searchState.bestScore,
                flag,
                searchState.bestMove,
                context.generation);
        return searchState.toOutcome();
    }

    /**
     * Searches one root move with PVS re-search rules.
     *
     * @param context search context
     * @param root mutable root position
     * @param depth target search depth
     * @param beta beta bound
     * @param searchState mutable root-search state
     * @param state reusable position state
     * @return score for the searched root move
     * @param move move encoded in CRTK move format
     */
    private static int searchRootMove(
            SearchContext context,
            Position root,
            short move,
            int depth,
            int beta,
            RootSearchState searchState,
            Position.State state) {
        root.play(move, state);
        try {
            context.movePlayed(root, move, state, 1);
            if (searchState.searchedMoves == 0) {
                return -negamax(context, root, depth - 1, -beta, -searchState.alpha, 1, true);
            }
            int score = -negamax(context, root, depth - 1, -searchState.alpha - 1, -searchState.alpha, 1, false);
            if (score > searchState.alpha && score < beta) {
                score = -negamax(context, root, depth - 1, -beta, -searchState.alpha, 1, true);
            }
            return score;
        } finally {
            root.undo(move, state);
        }
    }

    /**
     * Negamax alpha-beta recursion.
     *
     * @param context search context
     * @param position mutable current position
     * @param depth remaining depth
     * @param alpha alpha bound
     * @param beta beta bound
     * @param ply current ply from root
     * @param pvNode true when this node lies on the principal-variation window
     * @return score from current side-to-move perspective
     */
    private static int negamax(
            SearchContext context,
            Position position,
            int depth,
            int alpha,
            int beta,
            int ply,
            boolean pvNode) {
        context.visitNode();
        context.pvLength[ply] = 0;
        NegamaxSetup setup = prepareNegamax(context, position, depth, alpha, beta, ply, pvNode);
        if (setup.resolved()) {
            return setup.resolvedScore();
        }
        return searchNegamaxMoves(context, position, depth, beta, ply, pvNode, setup);
    }

    /**
     * Prepares one negamax node and resolves any early-return conditions.
     *
     * @param context search context
     * @param position mutable current position
     * @param depth remaining depth
     * @param alpha alpha bound
     * @param beta beta bound
     * @param ply current ply from root
     * @param pvNode true when this node lies on the principal variation
     * @return prepared search setup, or a resolved early-return score
     */
    private static NegamaxSetup prepareNegamax(
            SearchContext context,
            Position position,
            int depth,
            int alpha,
            int beta,
            int ply,
            boolean pvNode) {
        MoveList legalMoves = null;
        boolean inCheck = position.inCheck();
        if (inCheck) {
            legalMoves = position.legalMoves();
            if (legalMoves.isEmpty()) {
                return NegamaxSetup.resolved(terminalScore(position, ply));
            }
        }
        if (isDraw(position) || isRepetition(context, position, ply)) {
            return NegamaxSetup.resolved(0);
        }
        if (depth <= 0) {
            return NegamaxSetup.resolved(quiescence(context, position, alpha, beta, ply, 0));
        }

        long key = position.signature();
        Transposition entry = context.transpositions.probe(key);
        int transpositionScore = transpositionScore(entry, depth, alpha, beta);
        if (transpositionScore != NO_SCORE) {
            return NegamaxSetup.resolved(transpositionScore);
        }

        int staticEval = NO_SCORE;
        if (!inCheck) {
            staticEval = staticScore(context, position, ply);
            int reverseFutilityScore = tryReverseFutilityPruning(depth, beta, pvNode, staticEval);
            if (reverseFutilityScore != NO_SCORE) {
                return NegamaxSetup.resolved(reverseFutilityScore);
            }
            int nullScore = tryNullMovePruning(context, position, depth, beta, ply, pvNode, staticEval);
            if (nullScore != NO_SCORE) {
                return NegamaxSetup.resolved(nullScore);
            }
        }

        if (legalMoves == null) {
            legalMoves = position.legalMoves();
        }
        if (legalMoves.isEmpty()) {
            return NegamaxSetup.resolved(terminalScore(position, ply));
        }
        return NegamaxSetup.search(inCheck, legalMoves, alpha, staticEval, key, entry);
    }

    /**
     * Searches the legal moves for one prepared negamax node.
     *
     * @param context search context
     * @param position mutable current position
     * @param depth remaining depth
     * @param alpha alpha bound
     * @param beta beta bound
     * @param ply current ply from root
     * @param pvNode true when this node lies on the principal variation
     * @param setup prepared node setup
     * @return best score found for the node
     */
    private static int searchNegamaxMoves(
            SearchContext context,
            Position position,
            int depth,
            int beta,
            int ply,
            boolean pvNode,
            NegamaxSetup setup) {
        short ttMove = setup.entry() == null ? Move.NO_MOVE : Transposition.moveOf(setup.entry().data);
        short[] moves = orderedMoves(position, setup.legalMoves(), Move.NO_MOVE, ttMove, context, ply);
        Position.State state = context.state(ply);
        NegamaxSearchState searchState = new NegamaxSearchState(setup.alpha());
        // Buffer of quiet moves searched before a cutoff, for the history malus.
        short[] triedQuiets = context.historyMalus() ? context.triedQuietsBuffer(ply) : null;
        int triedCount = 0;
        for (short move : moves) {
            boolean tactical = isTacticalMove(position, move);
            boolean losingCapture = context.seeLmr() && tactical && isLosingCapture(position, move);
            MoveDecision decision = new MoveDecision(
                    setup.staticEval(),
                    searchState.alpha,
                    depth,
                    searchState.searchedMoves,
                    pvNode,
                    setup.inCheck(),
                    tactical,
                    losingCapture,
                    move,
                    ply);
            if (!shouldFutilityPrune(context, decision) && !shouldLateMovePrune(context, decision)) {
                if (triedQuiets != null && !tactical && triedCount < triedQuiets.length) {
                    triedQuiets[triedCount++] = move;
                }
                int score = searchNegamaxMove(context, position, state, depth, beta, pvNode, decision);
                searchState.recordScore(context, ply, move, score);
                if (searchState.alpha >= beta) {
                    if (!tactical) {
                        context.recordQuietCutoff(move, depth, ply, triedQuiets, triedCount);
                    }
                    break;
                }
            }
        }
        byte flag = transpositionFlag(searchState.bestScore, searchState.originalAlpha, beta);
        if (!isMateScore(searchState.bestScore)) {
            context.transpositions.store(
                    setup.key(),
                    depth,
                    searchState.bestScore,
                    flag,
                    searchState.bestMove,
                    context.generation);
        }
        return searchState.bestScore;
    }

    /**
     * Searches one move from a prepared negamax node.
     *
     * @param context search context
     * @param position mutable child position
     * @param state reusable position state
     * @param depth remaining depth at the parent node
     * @param beta beta bound
     * @param pvNode true when the parent node is a PV node
     * @param decision move-decision inputs
     * @return score for the move
     */
    private static int searchNegamaxMove(
            SearchContext context,
            Position position,
            Position.State state,
            int depth,
            int beta,
            boolean pvNode,
            MoveDecision decision) {
        position.play(decision.move(), state);
        try {
            context.movePlayed(position, decision.move(), state, decision.ply() + 1);
            // Extend a move that gives check by one ply (so forcing lines are not
            // cut at the horizon), capped so extended lines can't overrun the
            // scratch arrays. A checking move is never also reduced.
            int extension = context.checkExtensions()
                    && position.inCheck()
                    && decision.ply() + 1 < context.maxExtendedPly()
                    ? 1 : 0;
            int childDepth = depth - 1 + extension;
            int reduction = extension > 0 ? 0 : lateMoveReduction(context, decision);
            if (decision.searchedMoves() == 0) {
                return -negamax(context, position, childDepth, -beta, -decision.alpha(), decision.ply() + 1, pvNode);
            }
            return searchLateMove(context, position, childDepth, reduction, decision.alpha(), beta, decision.ply());
        } finally {
            position.undo(decision.move(), state);
        }
    }

    /**
     * Classifies a searched score for transposition-table storage.
     *
     * @param bestScore best score found at the node
     * @param originalAlpha alpha value before searching the node
     * @param beta beta bound used by the node
     * @return exact, lower-bound, or upper-bound transposition flag
     */
    private static byte transpositionFlag(int bestScore, int originalAlpha, int beta) {
        if (bestScore <= originalAlpha) {
            return TT_UPPER;
        }
        if (bestScore >= beta) {
            return TT_LOWER;
        }
        return TT_EXACT;
    }

    /**
     * Attempts null-move pruning for quiet non-PV nodes.
     *
     * <p>
     * The heuristic asks whether the side to move can still exceed beta even
     * after voluntarily passing. If so, the normal move search at this node is
     * very likely to fail high too, so the subtree can be cut.
     * </p>
     *
     * @param context search context
     * @param position mutable current position
     * @param depth remaining depth
     * @param beta beta bound
     * @param ply current ply from root
     * @param pvNode true when this node is in a principal-variation window
     * @param staticEval cached static evaluation from side-to-move perspective
     * @return cutoff score or {@link #NO_SCORE}
     */
    private static int tryNullMovePruning(
            SearchContext context,
            Position position,
            int depth,
            int beta,
            int ply,
            boolean pvNode,
            int staticEval) {
        if (pvNode
                || depth < NULL_MOVE_MIN_DEPTH
                || staticEval < beta
                || !hasNullMoveMaterial(position)) {
            return NO_SCORE;
        }

        Position.State state = context.nullState(ply);
        int reducedDepth = Math.max(0, depth - 1 - nullMoveReduction(depth));
        position.playNull(state);
        int score;
        try {
            context.nullMovePlayed(ply + 1);
            score = -negamax(context, position, reducedDepth, -beta, -beta + 1, ply + 1, false);
        } finally {
            position.undoNull(state);
        }
        return score >= beta && !isMateScore(score) ? score : NO_SCORE;
    }

    /**
     * Attempts reverse futility pruning for quiet non-PV nodes near the horizon.
     *
     * <p>
     * When a static evaluation already exceeds beta by a conservative depth-scaled
     * margin, shallow quiet replies are unlikely to overturn the fail-high.
     * </p>
     *
     * @param depth remaining depth
     * @param beta beta bound
     * @param pvNode true when this node is in the principal variation
     * @param staticEval cached static evaluation
     * @return cutoff score or {@link #NO_SCORE}
     */
    private static int tryReverseFutilityPruning(
            int depth,
            int beta,
            boolean pvNode,
            int staticEval) {
        if (pvNode
                || depth > REVERSE_FUTILITY_MAX_DEPTH
                || staticEval == NO_SCORE
                || Math.abs(beta) >= MATE_THRESHOLD) {
            return NO_SCORE;
        }
        return staticEval - (REVERSE_FUTILITY_MARGIN * depth) >= beta ? staticEval : NO_SCORE;
    }

    /**
     * Returns whether the side to move has enough non-pawn material for null-move
     * pruning to be reasonable.
     *
     * @param position position to inspect
     * @return true when the side to move owns at least one knight, bishop, rook,
     *         or queen
     */
    private static boolean hasNullMoveMaterial(Position position) {
        boolean white = position.isWhiteToMove();
        long material = position.pieces(white ? Position.WHITE_KNIGHT : Position.BLACK_KNIGHT)
                | position.pieces(white ? Position.WHITE_BISHOP : Position.BLACK_BISHOP)
                | position.pieces(white ? Position.WHITE_ROOK : Position.BLACK_ROOK)
                | position.pieces(white ? Position.WHITE_QUEEN : Position.BLACK_QUEEN);
        return material != 0L;
    }

    /**
     * Computes the null-move depth reduction.
     *
     * @param depth remaining depth before the null move
     * @return plies to reduce after the skipped turn
     */
    private static int nullMoveReduction(int depth) {
        return 2 + depth / 6;
    }

    /**
     * Returns whether a quiet shallow move can be skipped by futility pruning.
     *
     * @param staticEval static evaluation of the parent node
     * @param alpha alpha bound
     * @param depth remaining depth
     * @param searchedMoves number of moves already searched at this node
     * @param pvNode true for principal-variation nodes
     * @param inCheck true when side to move is in check
     * @param tactical true for captures, promotions, and en-passant
     * @param move move being considered
     * @param context search context
     * @param ply current ply from root
     * @return true when the move can be skipped
     * @param decision move decision metadata
     */
    private static boolean shouldFutilityPrune(SearchContext context, MoveDecision decision) {
        return decision.staticEval() != NO_SCORE
                && !decision.pvNode()
                && !decision.inCheck()
                && !decision.tactical()
                && decision.searchedMoves() > 0
                && decision.depth() <= FUTILITY_MAX_DEPTH
                && !context.isKiller(decision.move(), decision.ply())
                && Math.abs(decision.alpha()) < MATE_THRESHOLD
                && decision.staticEval() + FUTILITY_MARGIN * decision.depth() <= decision.alpha();
    }

    /**
     * Returns whether a late quiet move can be skipped on move-count grounds.
     *
     * <p>
     * Once a node has already searched enough earlier moves, very late quiet
     * moves at shallow depth are rarely relevant unless they have accumulated
     * strong history. This trims broad, low-value branches before the engine
     * spends even a reduced search on them.
     * </p>
     *
     * @param context search context
     * @param decision move-decision inputs
     * @return true when the move can be pruned without searching
     */
    private static boolean shouldLateMovePrune(SearchContext context, MoveDecision decision) {
        if (decision.staticEval() == NO_SCORE
                || decision.pvNode()
                || decision.inCheck()
                || decision.tactical()
                || decision.depth() > LATE_MOVE_PRUNING_MAX_DEPTH
                || context.isKiller(decision.move(), decision.ply())
                || Math.abs(decision.alpha()) >= MATE_THRESHOLD
                || decision.searchedMoves() < lateMovePruningThreshold(decision.depth())) {
            return false;
        }
        return context.historyScore(decision.move()) <= decision.depth() * decision.depth()
                && decision.staticEval() + FUTILITY_MARGIN * decision.depth() <= decision.alpha();
    }

    /**
     * Returns the quiet-move count after which late-move pruning may start.
     *
     * @param depth remaining node depth
     * @return move-count threshold
     */
    private static int lateMovePruningThreshold(int depth) {
        return 3 + depth * depth;
    }

    /**
     * Computes a late-move reduction for a quiet move.
     *
     * @param depth remaining depth
     * @param searchedMoves number of moves already searched at this node
     * @param pvNode true for principal-variation nodes
     * @param inCheck true when side to move is in check
     * @param tactical true for captures, promotions, and en-passant
     * @param move move being considered
     * @param context search context
     * @param ply current ply from root
     * @return reduction in plies, or zero for a full-depth search
     * @param decision move decision metadata
     */
    private static int lateMoveReduction(SearchContext context, MoveDecision decision) {
        if (decision.pvNode()
                || decision.inCheck()
                || (decision.tactical() && !decision.losingCapture())
                || decision.depth() < 3
                || decision.searchedMoves() < 3
                || context.isKiller(decision.move(), decision.ply())) {
            return 0;
        }
        if (context.lmrTable()) {
            int d = Math.min(decision.depth(), 63);
            int m = Math.min(decision.searchedMoves(), 63);
            int reduction = LMR_TABLE[d][m];
            // Reduce a quiet move less when it has accumulated strong history.
            if (context.historyScore(decision.move()) >= decision.depth() * decision.depth()) {
                reduction--;
            }
            return Math.max(0, Math.min(decision.depth() - 1, reduction));
        }
        int reduction = 1;
        if (decision.depth() >= 6 && decision.searchedMoves() >= 6) {
            reduction++;
        }
        if (decision.depth() >= 10 && decision.searchedMoves() >= 10) {
            reduction++;
        }
        return Math.min(decision.depth() - 1, reduction);
    }

    /**
     * Searches a non-first child with principal-variation search and optional LMR.
     *
     * @param context search context
     * @param position child position after the move has been made
     * @param childDepth full child depth
     * @param reduction late-move reduction in plies
     * @param alpha alpha bound at the parent
     * @param beta beta bound at the parent
     * @param ply parent ply from root
     * @return child score from the parent perspective
     */
    private static int searchLateMove(
            SearchContext context,
            Position position,
            int childDepth,
            int reduction,
            int alpha,
            int beta,
            int ply) {
        int score;
        if (reduction > 0) {
            int reducedDepth = Math.max(0, childDepth - reduction);
            score = -negamax(context, position, reducedDepth, -alpha - 1, -alpha, ply + 1, false);
            if (score <= alpha) {
                return score;
            }
        }
        score = -negamax(context, position, childDepth, -alpha - 1, -alpha, ply + 1, false);
        if (score > alpha && score < beta) {
            score = -negamax(context, position, childDepth, -beta, -alpha, ply + 1, true);
        }
        return score;
    }

    /**
     * Quiescence search for tactically unstable leaf nodes.
     *
     * <p>
     * Quiet leaf evaluation alone misses simple captures beyond the horizon. This
     * search extends only forcing captures/promotions, and all legal check evasions
     * when the side to move is in check.
     * </p>
     *
     * @param context search context
     * @param position mutable position
     * @param alpha alpha bound
     * @param beta beta bound
     * @param ply current ply from root
     * @param qply quiescence ply
     * @return score from side-to-move perspective
     */
    private static int quiescence(
            SearchContext context,
            Position position,
            int alpha,
            int beta,
            int ply,
            int qply) {
        context.visitNode();
        context.pvLength[ply] = 0;
        QuiescenceSetup setup = prepareQuiescence(context, position, alpha, beta, ply, qply);
        if (setup.resolved()) {
            return setup.resolvedScore();
        }

        short[] moves = orderedQuiescenceMoves(position, setup.legalMoves(), context, ply, setup.inCheck());
        int currentAlpha = setup.alpha();
        for (short move : moves) {
            if (!setup.inCheck()) {
                if (shouldDeltaPrune(position, move, setup.standPat(), currentAlpha, false)) {
                    continue;
                }
                // Skip captures that lose material outright; a losing capture
                // almost never improves a quiet stand-pat and only deepens the
                // tree. Check evasions (inCheck) are never pruned.
                if (context.seePruning() && isLosingCapture(position, move)) {
                    continue;
                }
            }
            int score = searchQuiescenceMove(context, position, currentAlpha, beta, ply, qply, move);
            if (score >= beta) {
                return score;
            }
            if (score > currentAlpha) {
                currentAlpha = score;
                updatePrincipalVariation(context, ply, move);
            }
        }
        return currentAlpha;
    }

    /**
     * Prepares quiescence search and resolves terminal or horizon cases.
     *
     * @param context search context
     * @param position mutable position
     * @param alpha alpha bound
     * @param beta beta bound
     * @param ply current ply from root
     * @param qply quiescence ply
     * @return prepared quiescence setup
     */
    private static QuiescenceSetup prepareQuiescence(
            SearchContext context,
            Position position,
            int alpha,
            int beta,
            int ply,
            int qply) {
        boolean inCheck = position.inCheck();
        if (isDraw(position) || isRepetition(context, position, ply)) {
            return QuiescenceSetup.resolved(0);
        }
        if (inCheck) {
            MoveList legalMoves = position.legalMoves();
            if (legalMoves.isEmpty()) {
                return QuiescenceSetup.resolved(terminalScore(position, ply));
            }
            return qply >= QUIESCENCE_MAX_PLY
                    ? QuiescenceSetup.resolved(staticScore(context, position, ply))
                    : QuiescenceSetup.search(legalMoves, true, NO_SCORE, alpha);
        }

        int standPat = staticScore(context, position, ply);
        if (standPat >= beta) {
            if (!position.hasLegalMove()) {
                return QuiescenceSetup.resolved(0);
            }
            return QuiescenceSetup.resolved(standPat);
        }
        if (qply >= QUIESCENCE_MAX_PLY) {
            if (!position.hasLegalMove()) {
                return QuiescenceSetup.resolved(0);
            }
            return QuiescenceSetup.resolved(Math.max(alpha, standPat));
        }
        MoveList legalMoves = position.legalMoves();
        if (legalMoves.isEmpty()) {
            return QuiescenceSetup.resolved(0);
        }
        return QuiescenceSetup.search(legalMoves, false, standPat, Math.max(alpha, standPat));
    }

    /**
     * Searches one quiescence move and restores the parent position afterwards.
     *
     * @param context search context
     * @param position mutable position
     * @param state reusable position state
     * @param alpha alpha bound
     * @param beta beta bound
     * @param ply current ply from root
     * @param qply quiescence ply
     * @param move move being considered
     * @return quiescence score for the move
     */
    private static int searchQuiescenceMove(
            SearchContext context,
            Position position,
            int alpha,
            int beta,
            int ply,
            int qply,
            short move) {
        Position.State state = context.state(ply);
        position.play(move, state);
        try {
            context.movePlayed(position, move, state, ply + 1);
            return -quiescence(context, position, -beta, -alpha, ply + 1, qply + 1);
        } finally {
            position.undo(move, state);
        }
    }

    /**
     * Returns a usable transposition-table score, if the stored bound resolves the
     * current alpha-beta window.
     *
     * @param entry transposition-table entry
     * @param depth remaining search depth
     * @param alpha alpha bound
     * @param beta beta bound
     * @return score or {@link #NO_SCORE}
     */
    private static int transpositionScore(Transposition entry, int depth, int alpha, int beta) {
        if (entry == null) {
            return NO_SCORE;
        }
        long data = entry.data;
        if (Transposition.depthOf(data) < depth) {
            return NO_SCORE;
        }
        int score = Transposition.scoreOf(data);
        byte flag = Transposition.flagOf(data);
        if (flag == TT_EXACT
                || (flag == TT_LOWER && score >= beta)
                || (flag == TT_UPPER && score <= alpha)) {
            return score;
        }
        return NO_SCORE;
    }

    /**
     * Updates the principal variation at one ply.
     *
     * @param context search context
     * @param ply current ply
     * @param move best move at this ply
     */
    private static void updatePrincipalVariation(SearchContext context, int ply, short move) {
        context.pv[ply][0] = move;
        int childLength = context.pvLength[ply + 1];
        if (childLength > 0) {
            System.arraycopy(
                    context.pv[ply + 1],
                    0,
                    context.pv[ply],
                    1,
                    Math.min(childLength, context.pv[ply].length - 1));
        }
        context.pvLength[ply] = Math.min(context.pv[ply].length, childLength + 1);
    }

    /**
     * Records a quiet beta cutoff for future move ordering.
     *
     * @param position parent position
     * @param context search context
     * @param move cutoff move
     * @param depth remaining depth
     * @param ply current ply
     */
    private static void recordCutoff(Position position, SearchContext context, short move, int depth, int ply) {
        if (!isTacticalMove(position, move)) {
            context.recordQuietCutoff(move, depth, ply);
        }
    }

    /**
     * Returns whether a non-check quiescence capture is too small to affect alpha.
     *
     * @param position parent position
     * @param move tactical move being considered
     * @param standPat static score before tactical moves
     * @param alpha current alpha bound
     * @param inCheck true when side to move is in check
     * @return true when delta pruning can skip the move
     */
    private static boolean shouldDeltaPrune(Position position, short move, int standPat, int alpha, boolean inCheck) {
        return !inCheck
                && standPat != NO_SCORE
                && Move.getPromotion(move) == 0
                && Math.abs(alpha) < MATE_THRESHOLD
                && standPat + captureValue(position, move) + DELTA_MARGIN <= alpha;
    }

    /**
     * Returns the material value that a move captures.
     *
     * @param position parent position
     * @param move move to inspect
     * @return centipawn value of the captured piece, or zero
     */
    private static int captureValue(Position position, short move) {
        int from = Move.getFromIndex(move);
        int to = Move.getToIndex(move);
        byte moving = position.pieceAt(from);
        byte victim = position.pieceAt(to);
        if (victim != Piece.EMPTY && Piece.isWhite(victim) != position.isWhiteToMove()) {
            return Piece.getValue(victim);
        }
        return isEnPassantCapture(position, moving, from, to) ? Piece.VALUE_PAWN : 0;
    }

    /**
     * Produces a deterministic fallback before the first full depth completes.
     *
     * @param root root position
     * @param legalMoves root legal moves
     * @param context search context
     * @param started start time
     * @return one-ply static result
     */
    private static Result staticRootResult(
            Position root,
            MoveList legalMoves,
            SearchContext context,
            long started) {
        context.prepareMoveOrdering(root);
        short[] moves = orderedMoves(root, legalMoves, Move.NO_MOVE, Move.NO_MOVE, context, 0);
        Position.State state = new Position.State();
        short bestMove = Move.NO_MOVE;
        int bestScore = -INF;
        boolean searching = true;
        int moveIndex = 0;
        while (searching && moveIndex < moves.length) {
            short move = moves[moveIndex++];
            if (!context.recordRootFallbackProbe()) {
                searching = false;
            } else {
                root.play(move, state);
                int score;
                try {
                    context.movePlayed(root, move, state, 1);
                    score = positionScoreAfterRootMove(context, root, 1);
                } finally {
                    root.undo(move, state);
                }
                if (score > bestScore || bestMove == Move.NO_MOVE) {
                    bestScore = score;
                    bestMove = move;
                }
                if (context.shouldStop()) {
                    searching = false;
                }
            }
        }
        if (bestMove == Move.NO_MOVE && moves.length > 0) {
            bestMove = moves[0];
            bestScore = 0;
        }
        return new Result(
                bestMove,
                bestScore,
                0,
                context.nodes,
                elapsedSince(started),
                false,
                bestMove == Move.NO_MOVE ? new short[0] : new short[] { bestMove });
    }

    /**
     * Scores the child position after a root move from the root perspective.
     *
     * @param context search context
     * @param position child position after root move
     * @param ply child ply
     * @return root-perspective score
     */
    private static int positionScoreAfterRootMove(SearchContext context, Position position, int ply) {
        if (isDraw(position)) {
            return 0;
        }
        if (!position.hasLegalMove()) {
            return -terminalScore(position, ply);
        }
        return -staticScore(context, position, ply);
    }

    /**
     * Orders moves using a deterministic capture/promotion/preferred heuristic.
     *
     * @param position position before the moves
     * @param moveList legal moves
     * @param preferredMove move to order first, or {@link Move#NO_MOVE}
     * @param transpositionMove transposition-table move to order early, or
     *        {@link Move#NO_MOVE}
     * @param context search context containing history and killer tables
     * @param ply current ply from root
     * @return sorted move array
     */
    private static short[] orderedMoves(
            Position position,
            MoveList moveList,
            short preferredMove,
            short transpositionMove,
            SearchContext context,
            int ply) {
        short[] moves = moveList.toArray();
        int[] scores = new int[moves.length];
        for (int i = 0; i < moves.length; i++) {
            scores[i] = moveOrderScore(position, moves[i], preferredMove, transpositionMove, context, ply);
        }
        context.scoreMoves(position, moves, scores);
        insertionSortDescending(moves, scores);
        return moves;
    }

    /**
     * Orders quiescence moves, filtering non-tactical moves outside check.
     *
     * @param position parent position
     * @param moveList legal moves for the node
     * @param context search context containing history and killer tables
     * @param ply current ply from root
     * @param inCheck true when the side to move is in check
     * @return sorted move array containing all legal evasions in check, or only
     *         tactical moves otherwise
     */
    private static short[] orderedQuiescenceMoves(
            Position position,
            MoveList moveList,
            SearchContext context,
            int ply,
            boolean inCheck) {
        if (inCheck) {
            return orderedMoves(position, moveList, Move.NO_MOVE, Move.NO_MOVE, context, ply);
        }
        short[] allMoves = moveList.toArray();
        int tacticalCount = 0;
        for (short move : allMoves) {
            if (isTacticalMove(position, move)) {
                tacticalCount++;
            }
        }
        if (tacticalCount == 0) {
            return new short[0];
        }
        short[] moves = new short[tacticalCount];
        int[] scores = new int[tacticalCount];
        int index = 0;
        for (short move : allMoves) {
            if (!isTacticalMove(position, move)) {
                continue;
            }
            moves[index] = move;
            scores[index] = moveOrderScore(position, move, Move.NO_MOVE, Move.NO_MOVE, context, ply);
            index++;
        }
        context.scoreMoves(position, moves, scores);
        insertionSortDescending(moves, scores);
        return moves;
    }

    /**
     * Scores one move for move ordering.
     *
     * @param position parent position
     * @param move encoded move
     * @param preferredMove move to order first
     * @param transpositionMove stored best move to try early
     * @param context search context
     * @param ply current ply
     * @return ordering score
     */
    private static int moveOrderScore(
            Position position,
            short move,
            short preferredMove,
            short transpositionMove,
            SearchContext context,
            int ply) {
        int score = move == preferredMove ? 1_000_000 : 0;
        if (move == transpositionMove) {
            score += 900_000;
        }
        int from = Move.getFromIndex(move);
        int to = Move.getToIndex(move);
        byte moving = position.pieceAt(from);
        byte victim = position.pieceAt(to);
        if (victim != Piece.EMPTY && Piece.isWhite(victim) != position.isWhiteToMove()) {
            // Winning/equal captures sort ahead of quiets; SEE-losing captures
            // drop below quiets so they are searched last.
            int base = context.seePruning() && isLosingCapture(position, move) ? -100_000 : 100_000;
            score += base + Piece.getValue(victim) * 10 - Piece.getValue(moving);
        } else if (isEnPassantCapture(position, moving, from, to)) {
            score += 100_000 + Piece.VALUE_PAWN * 10 - Piece.getValue(moving);
        }
        int promotion = Move.getPromotion(move);
        if (promotion != 0) {
            score += 80_000 + promotionValue(promotion);
        }
        if (score == 0) {
            if (context.isKiller(move, ply)) {
                score += 70_000;
            }
            score += context.historyScore(move);
        }
        return score;
    }

    /**
     * Returns whether a move is a capture or promotion.
     *
     * @param position parent position
     * @param move encoded move
     * @return true for tactical moves
     */
    private static boolean isTacticalMove(Position position, short move) {
        int from = Move.getFromIndex(move);
        int to = Move.getToIndex(move);
        byte moving = position.pieceAt(from);
        byte victim = position.pieceAt(to);
        return Move.getPromotion(move) != 0
                || (victim != Piece.EMPTY && Piece.isWhite(victim) != position.isWhiteToMove())
                || isEnPassantCapture(position, moving, from, to);
    }

    /**
     * Returns whether a normal capture loses material by static exchange
     * evaluation. Only "capturing up" (victim worth less than the mover) can be
     * SEE-negative, so cheaper cases skip the SEE call entirely; promotions and
     * en-passant are never treated as losing here.
     *
     * @param position parent position
     * @param move capture move to test
     * @return true when the capture has a negative SEE
     */
    private static boolean isLosingCapture(Position position, short move) {
        if (Move.getPromotion(move) != 0) {
            return false;
        }
        int from = Move.getFromIndex(move);
        int to = Move.getToIndex(move);
        byte moving = position.pieceAt(from);
        byte victim = position.pieceAt(to);
        boolean normalCapture = victim != Piece.EMPTY && Piece.isWhite(victim) != position.isWhiteToMove();
        if (!normalCapture || Piece.getValue(victim) >= Piece.getValue(moving)) {
            return false;
        }
        return !See.seeGreaterEqual(position, move, 0);
    }

    /**
     * Returns whether a move is an en-passant capture by geometry/state.
     *
     * @param position parent position
     * @param moving moving piece code
     * @param from origin square
     * @param to target square
     * @return true when en-passant
     */
    private static boolean isEnPassantCapture(Position position, byte moving, int from, int to) {
        if (!Piece.isPawn(moving) || to != position.enPassantSquare() || position.pieceAt(to) != Piece.EMPTY) {
            return false;
        }
        return Math.abs((from & 7) - (to & 7)) == 1;
    }

    /**
     * Returns a material value for a promotion code.
     *
     * @param promotion move promotion code
     * @return centipawn value
     */
    private static int promotionValue(int promotion) {
        return switch (promotion) {
            case 1 -> Piece.VALUE_KNIGHT;
            case 2 -> Piece.VALUE_BISHOP;
            case 3 -> Piece.VALUE_ROOK;
            case 4 -> Piece.VALUE_QUEEN;
            default -> 0;
        };
    }

    /**
     * Stable insertion sort by descending score.
     *
     * @param moves move array
     * @param scores parallel score array
     */
    private static void insertionSortDescending(short[] moves, int[] scores) {
        for (int i = 1; i < moves.length; i++) {
            short move = moves[i];
            int score = scores[i];
            int j = i - 1;
            while (j >= 0 && scores[j] < score) {
                moves[j + 1] = moves[j];
                scores[j + 1] = scores[j];
                j--;
            }
            moves[j + 1] = move;
            scores[j + 1] = score;
        }
    }

    /**
     * Returns whether a position is an immediate draw under simple deterministic rules.
     *
     * @param position position to inspect
     * @return true for halfmove-clock or insufficient-material draws
     */
    private static boolean isDraw(Position position) {
        return position.halfMoveClock() >= 100 || position.isInsufficientMaterial();
    }

    /**
     * Returns whether the current position repeats one already seen on the search
     * path (a single in-tree repetition is treated as a draw, the conventional
     * search rule). The scan steps back two plies at a time to keep the side to
     * move aligned, and stops at the last irreversible move via the halfmove
     * clock so positions across a capture or pawn move can never match.
     *
     * @param context search context holding the path signatures
     * @param position current position
     * @param ply current ply from the root
     * @return true when the position has occurred earlier on the path
     */
    private static boolean isRepetition(SearchContext context, Position position, int ply) {
        if (!context.searchRepetition() || ply <= 1) {
            return false;
        }
        long key = position.signatureCore();
        int limit = Math.max(0, ply - position.halfMoveClock());
        for (int i = ply - 2; i >= limit; i -= 2) {
            if (context.pathKey(i) == key) {
                return true;
            }
        }
        return false;
    }

    /**
     * Static centipawn evaluation from side-to-move perspective.
     *
     * @param context search context that owns the evaluation cache
     * @param position position to evaluate statically
     * @return score clamped to the non-mate static range
     * @param ply ply value
     */
    private static int staticScore(SearchContext context, Position position, int ply) {
        int score = context.evaluate(position, ply);
        if (score > MAX_STATIC_SCORE) {
            return MAX_STATIC_SCORE;
        }
        if (score < -MAX_STATIC_SCORE) {
            return -MAX_STATIC_SCORE;
        }
        return score;
    }

    /**
     * Terminal score from side-to-move perspective.
     *
     * @param position terminal position
     * @param ply ply from root
     * @return mate score adjusted by ply, or zero for stalemate
     */
    private static int terminalScore(Position position, int ply) {
        return position.inCheck() ? -MATE_SCORE + ply : 0;
    }

    /**
     * Returns whether a score represents mate.
     *
     * @param score search score to classify
     * @return true for mate-score range
     */
    private static boolean isMateScore(int score) {
        return Math.abs(score) >= MATE_THRESHOLD;
    }

    /**
     * Computes elapsed time from a start timestamp.
     *
     * @param started start time
     * @return non-negative elapsed milliseconds
     */
    private static long elapsedSince(long started) {
        return Math.max(0L, System.currentTimeMillis() - started);
    }

    /**
     * Returns the active evaluator label.
     *
     * @return evaluator name
     */
    public String evaluatorName() {
        return evaluator.name();
    }

    /**
     * Releases evaluator resources.
     */
    @Override
    public void close() {
        evaluator.close();
    }

    /**
     * Mutable state for one root search iteration.
     */
    private static final class RootSearchState {

        /**
         * Alpha bound before the iteration started.
         */
        private final int originalAlpha;

        /**
         * Current alpha bound.
         */
        private int alpha;

        /**
         * Best score found so far.
         */
        private int bestScore = -INF;

        /**
         * Best move found so far.
         */
        private short bestMove = Move.NO_MOVE;

        /**
         * Principal variation buffer.
         */
        private final short[] bestPv;

        /**
         * Principal variation length.
         */
        private int bestLength;

        /**
         * Number of moves searched so far.
         */
        private int searchedMoves;

        /**
         * Creates a root-search state.
         *
         * @param depth search depth
         * @param alpha initial alpha bound
         */
        private RootSearchState(int depth, int alpha) {
            this.originalAlpha = alpha;
            this.alpha = alpha;
            this.bestPv = new short[Math.max(1, depth)];
        }

        /**
         * Records one searched root move.
         *
         * @param context search context
         * @param move move that was searched
         * @param score returned score
         * @param depth current iteration depth
         */
        private void recordScore(SearchContext context, short move, int score, int depth) {
            if (score > bestScore || bestMove == Move.NO_MOVE) {
                bestScore = score;
                bestMove = move;
                bestPv[0] = move;
                int childLength = context.pvLength[1];
                if (childLength > 0) {
                    System.arraycopy(context.pv[1], 0, bestPv, 1, Math.min(childLength, bestPv.length - 1));
                }
                bestLength = Math.min(depth, childLength + 1);
            }
            if (score > alpha) {
                alpha = score;
            }
            searchedMoves++;
        }

        /**
         * Returns the final root outcome snapshot.
         *
         * @return root outcome
         */
        private RootOutcome toOutcome() {
            return new RootOutcome(bestMove, bestScore, Arrays.copyOf(bestPv, bestLength));
        }
    }


    /**
     * Mutable negamax move-search state.
     */
    private static final class NegamaxSearchState {

        /**
         * Alpha bound before move search started.
         */
        private final int originalAlpha;

        /**
         * Current alpha bound.
         */
        private int alpha;

        /**
         * Best score found so far.
         */
        private int bestScore = -INF;

        /**
         * Best move found so far.
         */
        private short bestMove = Move.NO_MOVE;

        /**
         * Number of moves searched so far.
         */
        private int searchedMoves;

        /**
         * Creates a negamax search state.
         *
         * @param alpha initial alpha bound
         */
        private NegamaxSearchState(int alpha) {
            this.originalAlpha = alpha;
            this.alpha = alpha;
        }

        /**
         * Records one searched move.
         *
         * @param context search context
         * @param ply current ply
         * @param move move that was searched
         * @param score returned score
         */
        private void recordScore(SearchContext context, int ply, short move, int score) {
            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
            if (score > alpha) {
                alpha = score;
                updatePrincipalVariation(context, ply, move);
            }
            searchedMoves++;
        }
    }


    /**
     * Mutable per-search context.
     *
     * <p>
     * A context owns all state that changes during one root search: budgets,
     * node counters, move-ordering tables, principal-variation scratch arrays,
     * transposition storage, and static-evaluation cache.
     * </p>
     */
    private static final class SearchContext implements AutoCloseable {

        /**
         * Absolute wall-clock deadline in milliseconds, or {@link Long#MAX_VALUE}
         * when the search has no time limit.
         */
        private final long deadline;

        /**
         * Maximum node count, or {@link Long#MAX_VALUE} when the search has no
         * node limit.
         */
        private final long maxNodes;

        /**
         * Whether SEE-based losing-capture pruning/ordering is enabled.
         */
        private final boolean seePruning;

        /**
         * Whether checking moves are extended by one ply.
         */
        private final boolean checkExtensions;

        /**
         * Whether in-tree repetition draw detection is enabled.
         */
        private final boolean searchRepetition;

        /**
         * Whether the log-log late-move-reduction table is used.
         */
        private final boolean lmrTable;

        /**
         * Whether history uses gravity updates with a malus on tried quiets.
         */
        private final boolean historyMalus;

        /**
         * Whether SEE-losing captures may be late-move-reduced.
         */
        private final boolean seeLmr;

        /**
         * Per-ply scratch holding the quiet moves searched at a node before a
         * cutoff, so they can receive a history malus. Only allocated/used when
         * {@link #historyMalus} is set.
         */
        private final short[][] triedQuiets;

        /**
         * Core position signature ({@link Position#signatureCore()}) at each ply
         * on the current search path, for in-tree repetition detection. Only
         * maintained when {@link #searchRepetition} is set.
         */
        private final long[] pathKeys;

        /**
         * Highest ply at which a check extension may still fire, so extended lines
         * (including their quiescence tail) never overrun the scratch arrays.
         */
        private final int maxExtendedPly;

        /**
         * Static evaluator used when search reaches a quiet leaf.
         */
        private final CentipawnEvaluator evaluator;

        /**
         * Optional evaluator-owned incremental search state.
         */
        private final CentipawnEvaluator.SearchState incrementalState;

        /**
         * Transposition table used for cutoffs and move ordering. Either a fresh
         * per-search table or one shared across moves of a game (see
         * {@link AlphaBeta#persistentTranspositions}).
         */
        private final TranspositionTable transpositions;

        /**
         * Per-search cache for repeated static evaluations.
         */
        private final EvalCache evalCache = new EvalCache(EVAL_CACHE_ENTRIES);

        /**
         * Killer quiet moves indexed by ply, with two slots per ply.
         */
        private final short[][] killers;

        /**
         * History heuristic table keyed by from/to.
         */
        private final int[] history = new int[64 * 64];

        /**
         * Current iterative-deepening generation for replacement decisions.
         */
        private int generation;

        /**
         * Principal-variation scratch moves indexed by ply and PV offset.
         */
        private final short[][] pv;

        /**
         * Number of valid principal-variation moves stored for each ply.
         */
        private final int[] pvLength;

        /**
         * Reusable make/undo state indexed by search ply.
         */
        private final Position.State[] states;

        /**
         * Reusable null-move undo state indexed by search ply.
         */
        private final Position.State[] nullStates;

        /**
         * Total number of visited search and quiescence nodes.
         */
        private long nodes;

        /**
         * Next node count at which a wall-clock stop check should run.
         */
        private long nextStopCheck = STOP_CHECK_INTERVAL;

        /**
         * Creates a search context.
         *
         * @param started start time
         * @param limits search limits
         * @param evaluator static evaluator used at leaf nodes
         * @param root root position or node
         * @param table transposition table to use (fresh per-search, or shared)
         * @param generationStart initial generation, so a shared table's entries
         *        from earlier searches are not treated as the current generation
         */
        private SearchContext(long started, Limits limits, CentipawnEvaluator evaluator, Position root,
                TranspositionTable table, int generationStart, Set<Feature> features) {
            this.deadline = computeDeadline(started, limits.maxDurationMillis());
            this.maxNodes = limits.maxNodes() == 0L ? Long.MAX_VALUE : limits.maxNodes();
            this.evaluator = evaluator;
            this.transpositions = table;
            this.generation = generationStart;
            this.seePruning = features.contains(Feature.SEE_PRUNING);
            this.checkExtensions = features.contains(Feature.CHECK_EXTENSION);
            this.searchRepetition = features.contains(Feature.SEARCH_REPETITION);
            this.lmrTable = features.contains(Feature.LMR_TABLE);
            this.historyMalus = features.contains(Feature.HISTORY_MALUS);
            this.seeLmr = features.contains(Feature.SEE_LMR);
            // Check extensions can deepen the main search beyond the nominal
            // depth, so when they are enabled reserve a full extra depth's worth
            // of plies (plus quiescence headroom) and cap the extension ply below
            // that ceiling. When disabled the arrays keep their original size.
            int extensionHeadroom = checkExtensions ? limits.depth() : 0;
            int searchPlies = limits.depth() + extensionHeadroom + QUIESCENCE_MAX_PLY + 4;
            this.maxExtendedPly = searchPlies - QUIESCENCE_MAX_PLY - 2;
            this.incrementalState = evaluator.openSearchState(root, searchPlies);
            this.pv = new short[searchPlies][searchPlies];
            this.pvLength = new int[searchPlies];
            this.states = new Position.State[searchPlies];
            this.nullStates = new Position.State[searchPlies];
            this.killers = new short[searchPlies][2];
            this.pathKeys = new long[searchPlies];
            // Chess positions have at most ~218 legal moves; 256 is a safe bound.
            this.triedQuiets = historyMalus ? new short[searchPlies][256] : null;
            for (int i = 0; i < searchPlies; i++) {
                states[i] = new Position.State();
                nullStates[i] = new Position.State();
            }
            for (short[] row : killers) {
                Arrays.fill(row, Move.NO_MOVE);
            }
            if (searchRepetition) {
                pathKeys[0] = root.signatureCore();
            }
        }

        /**
         * Advances iterative-deepening generation.
         */
        private void newIteration() {
            generation++;
        }

        /**
         * Evaluates a position with a small signature cache.
         *
         * @param position position to evaluate
         * @return centipawn score
         * @param ply ply value
         */
        private int evaluate(Position position, int ply) {
            long key = position.signature();
            EvalEntry cached = evalCache.get(key);
            if (cached != null) {
                return cached.score;
            }
            int score = incrementalState == null
                    ? evaluator.evaluate(position)
                    : incrementalState.evaluate(position, ply);
            evalCache.put(key, score);
            return score;
        }

        /**
         * Notifies the incremental evaluator that a normal move has been played.
         *
         * @param position child position after the move
         * @param move encoded move that was played
         * @param state undo state filled by the move application
         * @param ply child ply from the root
         */
        private void movePlayed(Position position, short move, Position.State state, int ply) {
            if (incrementalState != null) {
                incrementalState.movePlayed(position, move, state, ply);
            }
            if (searchRepetition && ply < pathKeys.length) {
                pathKeys[ply] = position.signatureCore();
            }
        }

        /**
         * Notifies the incremental evaluator that a null move has been played.
         *
         * @param ply child ply from the root
         */
        private void nullMovePlayed(int ply) {
            if (incrementalState != null) {
                incrementalState.nullMovePlayed(ply);
            }
            if (searchRepetition && ply < pathKeys.length) {
                // A null move is not a real game position; store a sentinel so it
                // can never satisfy a repetition match.
                pathKeys[ply] = 0L;
            }
        }

        /**
         * Returns whether SEE losing-capture pruning/ordering is enabled.
         *
         * @return true when SEE pruning is active
         */
        private boolean seePruning() {
            return seePruning;
        }

        /**
         * Returns whether check extensions are enabled.
         *
         * @return true when checking moves are extended
         */
        private boolean checkExtensions() {
            return checkExtensions;
        }

        /**
         * Returns whether in-tree repetition detection is enabled.
         *
         * @return true when repetitions are scored as draws during search
         */
        private boolean searchRepetition() {
            return searchRepetition;
        }

        /**
         * Returns whether the log-log late-move-reduction table is enabled.
         *
         * @return true when the table-based LMR is used
         */
        private boolean lmrTable() {
            return lmrTable;
        }

        /**
         * Returns whether history gravity/malus updates are enabled.
         *
         * @return true when the malus history scheme is used
         */
        private boolean historyMalus() {
            return historyMalus;
        }

        /**
         * Returns whether SEE-losing captures may be late-move-reduced.
         *
         * @return true when bad-capture reductions are enabled
         */
        private boolean seeLmr() {
            return seeLmr;
        }

        /**
         * Returns the per-ply scratch buffer for quiet moves tried at a node.
         *
         * @param ply current ply
         * @return reusable buffer (never null when {@link #historyMalus} is set)
         */
        private short[] triedQuietsBuffer(int ply) {
            return triedQuiets[ply];
        }

        /**
         * Returns the highest ply at which a check extension may still fire.
         *
         * @return extension ply ceiling
         */
        private int maxExtendedPly() {
            return maxExtendedPly;
        }

        /**
         * Returns the path signature stored for a ply (for repetition scanning).
         *
         * @param ply ply on the current search path
         * @return core signature recorded at that ply
         */
        private long pathKey(int ply) {
            return pathKeys[ply];
        }

        /**
         * Lets the evaluator add any position-specific move priors.
         *
         * @param position position whose moves are being ordered
         * @param moves legal moves aligned with {@code scores}
         * @param scores mutable move-ordering scores
         */
        private void scoreMoves(Position position, short[] moves, int[] scores) {
            evaluator.scoreMoves(position, moves, scores);
        }

        /**
         * Lets the evaluator warm any move-ordering side data for a position.
         *
         * @param position position whose legal moves are about to be ordered
         */
        private void prepareMoveOrdering(Position position) {
            evaluator.prepareMoveOrdering(position);
        }

        /**
         * Clears principal-variation scratch state.
         */
        private void clearPv() {
            Arrays.fill(pvLength, 0);
        }

        /**
         * Returns the reusable move undo state for a ply.
         *
         * @param ply current search ply
         * @return caller-owned state slot
         */
        private Position.State state(int ply) {
            return states[ply];
        }

        /**
         * Returns the reusable null-move undo state for a ply.
         *
         * @param ply current search ply
         * @return caller-owned null state slot
         */
        private Position.State nullState(int ply) {
            return nullStates[ply];
        }

        /**
         * Records one visited node and checks budgets.
         */
        private void visitNode() {
            nodes++;
            if (nodes >= maxNodes) {
                throw SEARCH_STOPPED;
            }
            if (deadline == Long.MAX_VALUE) {
                return;
            }
            if (nodes > STOP_CHECK_EVERY_NODE_UNTIL && nodes < nextStopCheck) {
                return;
            }
            nextStopCheck = nodes + STOP_CHECK_INTERVAL;
            if (System.currentTimeMillis() >= deadline) {
                throw SEARCH_STOPPED;
            }
        }

        /**
         * Returns whether a search budget has been exhausted.
         *
         * @return true when node or wall-clock limits are exceeded
         */
        private boolean shouldStop() {
            return nodes >= maxNodes
                    || (deadline != Long.MAX_VALUE && System.currentTimeMillis() >= deadline);
        }

        /**
         * Records one root-fallback probe without throwing.
         *
         * @return true when another fallback probe may run
         */
        private boolean recordRootFallbackProbe() {
            nodes++;
            return !shouldStop();
        }

        /**
         * Records a beta cutoff from a quiet move.
         *
         * @param move quiet move
         * @param depth remaining depth
         * @param ply current ply
         */
        private void recordQuietCutoff(short move, int depth, int ply) {
            if (ply < killers.length && killers[ply][0] != move) {
                killers[ply][1] = killers[ply][0];
                killers[ply][0] = move;
            }
            int index = historyIndex(move);
            int bonus = depth * depth;
            history[index] = Math.min(1_000_000, history[index] + bonus);
        }

        /**
         * Records a quiet beta cutoff, additionally penalizing the quiet moves
         * that were searched before the cutoff move (history malus). Uses bounded
         * "gravity" updates so values self-decay and stay within
         * {@link AlphaBeta#HISTORY_MAX}. Falls back to the bonus-only update when
         * the feature is off.
         *
         * @param move cutoff move
         * @param depth remaining depth
         * @param ply current ply
         * @param tried buffer of quiet moves tried before the cutoff
         * @param triedCount number of valid entries in {@code tried}
         */
        private void recordQuietCutoff(short move, int depth, int ply, short[] tried, int triedCount) {
            if (!historyMalus) {
                recordQuietCutoff(move, depth, ply);
                return;
            }
            if (ply < killers.length && killers[ply][0] != move) {
                killers[ply][1] = killers[ply][0];
                killers[ply][0] = move;
            }
            int bonus = Math.min(depth * depth, HISTORY_MAX);
            applyGravity(historyIndex(move), bonus);
            for (int i = 0; i < triedCount; i++) {
                if (tried[i] != move) {
                    applyGravity(historyIndex(tried[i]), -bonus);
                }
            }
        }

        /**
         * Applies a gravity-bounded delta to a history entry, so its magnitude
         * stays at or below {@link AlphaBeta#HISTORY_MAX} and large values decay
         * toward zero as opposite-signed updates arrive.
         *
         * @param index history table index
         * @param delta signed bonus or malus
         */
        private void applyGravity(int index, int delta) {
            history[index] += delta - history[index] * Math.abs(delta) / HISTORY_MAX;
        }

        /**
         * Returns whether a move is a killer at a ply.
         *
         * @param move move to test
         * @param ply ply whose killer slots should be checked
         * @return true when the move is stored as a killer at that ply
         */
        private boolean isKiller(short move, int ply) {
            return ply < killers.length && (killers[ply][0] == move || killers[ply][1] == move);
        }

        /**
         * Returns the history score for a move.
         *
         * @param move move to score
         * @return accumulated history-heuristic score
         */
        private int historyScore(short move) {
            return history[historyIndex(move)];
        }

        /**
         * Computes history index.
         *
         * @param move move whose from/to squares define the bucket
         * @return history-table index
         */
        private static int historyIndex(short move) {
            return ((Move.getFromIndex(move) & 0xff) << 6) | (Move.getToIndex(move) & 0xff);
        }

        /**
         * Computes an absolute deadline.
         *
         * @param started start timestamp
         * @param durationMillis allowed duration
         * @return absolute deadline
         */
        private static long computeDeadline(long started, long durationMillis) {
            if (durationMillis == 0L) {
                return Long.MAX_VALUE;
            }
            long maxAdd = Long.MAX_VALUE - started;
            return started + Math.min(durationMillis, maxAdd);
        }

        /**
         * Releases per-search evaluator state.
         */
        @Override
        public void close() {
            if (incrementalState != null) {
                incrementalState.close();
            }
        }
    }


    /**
     * Callback for completed iterative-deepening depths.
     */
    @FunctionalInterface
    public interface SearchListener {

        /**
         * Handles one completed search depth.
         *
         * @param result completed-depth result
         */
        void onDepth(Result result);
    }
}
