package chess.engine;

import java.util.Arrays;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import chess.core.Move;
import chess.core.MoveGenerator;
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
     * Maximum remaining depth where razoring drops into quiescence.
     */
    private static final int RAZOR_MAX_DEPTH = 3;

    /**
     * Per-depth centipawn margin used by razoring.
     */
    private static final int RAZOR_MARGIN = 300;

    /**
     * Minimum remaining depth at which an internal iterative reduction may fire.
     */
    private static final int IIR_MIN_DEPTH = 4;

    /**
     * Quiet root positions with no checking move get only a narrow exact proof
     * probe, so ordinary searches are not taxed by the full mate-prover horizon.
     */
    private static final int QUIET_ROOT_MATE_PROBE_MOVES = 2;

    /**
     * Node cap for the no-root-check quiet mate probe.
     */
    private static final long QUIET_ROOT_MATE_PROBE_NODES = 2_000L;

    /**
     * Minimum remaining depth at which ProbCut may attempt a reduced tactical
     * confirmation search.
     */
    private static final int PROBCUT_MIN_DEPTH = 5;

    /**
     * Extra centipawn margin above beta required before ProbCut trusts a tactical
     * fail-high.
     */
    private static final int PROBCUT_MARGIN = 220;

    /**
     * Margin reduction when the side to move is already improving statically.
     */
    private static final int PROBCUT_IMPROVING_MARGIN = 60;

    /**
     * Child search depth used by ProbCut is parent depth minus this value.
     */
    private static final int PROBCUT_REDUCTION = 4;

    /**
     * Minimum remaining depth at which a singular-extension verification search is
     * attempted.
     */
    private static final int SINGULAR_MIN_DEPTH = 8;

    /**
     * Per-depth centipawn margin subtracted from the transposition score to form
     * the singular-extension verification beta.
     */
    private static final int SINGULAR_MARGIN_PER_DEPTH = 2;

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
         * Quiet checking moves receive a move-ordering bonus, so forcing quiets are
         * tried before ordinary history-scored quiets instead of relying on a later
         * check extension to repair poor ordering.
         */
        CHECK_ORDERING,

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
        SEE_LMR,

        /**
         * "Improving" heuristic: track the static evaluation along the search path
         * and detect whether the side to move's static eval has risen versus two
         * plies earlier. Pruning and reduction margins are tightened when the side
         * is not improving (a worsening position is pruned more aggressively) and
         * loosened when it is, which lets the same budget reach greater depth.
         */
        IMPROVING,

        /**
         * Continuation ("counter-move") history: in addition to the from/to butterfly
         * history, score quiet moves by how often the same piece-to move produced a
         * cutoff as a reply to the previous move (1 ply back) and the move before it
         * (2 plies back). Used for move ordering and to reduce good-continuation
         * quiets less in late-move reductions.
         */
        CONT_HISTORY,

        /**
         * Capture history: capture ordering learns which (moving piece, target
         * square, captured piece type) captures cause beta cutoffs, rather than
         * relying only on fixed MVV-LVA plus SEE. Inspired by Stockfish's capture
         * history table; independently implemented here.
         */
        CAPTURE_HISTORY,

        /**
         * Internal iterative reduction: when a node that should have a best move
         * cached (sufficient depth, PV or expected-cut node) has no transposition
         * move to try first, reduce its depth by one instead of searching it in full
         * with poor move ordering. The shallower search seeds a TT move for a later
         * re-visit far more cheaply than a full-width search would.
         */
        IIR,

        /**
         * Root score ordering: after each completed iterative-deepening pass, keep
         * every searched root move's score and use those previous scores to order
         * non-PV root moves on the next depth. The previous best move still receives
         * the normal preferred-move priority; this mainly stabilizes the rest of the
         * root list so deeper searches spend fewer nodes rediscovering known-bad
         * alternatives.
         */
        ROOT_SCORE_ORDERING,

        /**
         * ProbCut: at deep non-PV nodes, try high-SEE captures and queen promotions
         * against a beta-plus-margin window. If quiescence and a reduced search both
         * confirm the tactical fail-high, prune the full move search. Conceptually
         * inspired by Stockfish's ProbCut stage; independently implemented here.
         */
        PROBCUT,

        /**
         * Razoring: at shallow depth, when even a generous margin over the static
         * evaluation cannot reach alpha, drop directly into quiescence rather than
         * searching quiet moves that are very unlikely to raise alpha.
         */
        RAZORING,

        /**
         * Singular extensions: when the transposition move is much better than every
         * alternative (a reduced-depth search of the other moves fails low below a
         * margin under the TT score), extend it by one ply; conversely, when the TT
         * score is already failing high well above beta, reduce ("multi-cut"-style).
         */
        SINGULAR_EXT,

        /**
         * Static-evaluation correction history: a small table keyed by pawn-and-king
         * structure (and side to move) accumulates the running gap between the
         * search result and the static evaluation, and that learned correction is
         * added back into future static evaluations. This corrects systematic bias
         * the fixed evaluator cannot, sharpening every static-eval-driven decision
         * (reverse-futility, null-move, razoring, futility, improving, stand-pat).
         * Inspired by the correction-history idea used in modern engines such as
         * Stockfish; this is an independent reimplementation.
         */
        CORRECTION_HISTORY,

        /**
         * Staged move generation at non-check nodes: the transposition move is
         * validated standalone and searched before anything is generated, then
         * pseudo-legal tacticals (SEE-losing captures deferred to the end),
         * killers, and pseudo-legal quiets are generated and legality-checked
         * lazily per stage. A transposition-move or early-capture cutoff skips
         * quiet generation, quiet scoring, and the full-list sort entirely.
         * In-check nodes keep the eager fully-legal evasion path. The stage
         * order is fixed (captures/promotions, then killers in slot order, then
         * quiets, then losing captures), unlike the eager scorer's global sort
         * where accumulated history or evaluator priors can interleave a hot
         * killer above weak captures or a hot quiet above a cold killer, so
         * search trees differ from the eager path; measured at fixed move time
         * the staged order plus the lazy work was worth about +13 Elo.
         */
        STAGED_PICKER,

        /**
         * Stability-based time management for clock games. When the limits
         * carry a soft time budget, iterative deepening stops before starting
         * another iteration once the elapsed time exceeds the soft budget
         * scaled by a stability factor: searches whose best move has stayed
         * stable for several iterations stop early, while a freshly changed
         * best move extends the budget toward the hard deadline. Fixed
         * per-move budgets (no soft time) are unaffected.
         */
        STABILITY_TIME
    }

    /**
     * Bound on the magnitude of a gravity history value.
     */
    private static final int HISTORY_MAX = 1 << 14;

    /**
     * Move-ordering tier bonuses ({@link #moveOrderScore}). The hierarchy is
     * preferred (PV) move &gt; transposition move &gt; winning/equal captures &gt;
     * promotions &gt; killers &gt; quiets (history-scored), with SEE-losing captures
     * pushed below quiets. Compile-time constants, so substituting them is free.
     */
    private static final int ORDER_PREFERRED = 1_000_000;

    /**
     * Ordering bonus for the transposition-table move.
     */
    private static final int ORDER_TT = 900_000;

    /**
     * Ordering bonus for a winning or equal capture (and en passant).
     */
    private static final int ORDER_CAPTURE = 100_000;

    /**
     * Ordering penalty for a SEE-losing capture, dropping it below quiets.
     */
    private static final int ORDER_LOSING_CAPTURE = -100_000;

    /**
     * Ordering bonus for a promotion.
     */
    private static final int ORDER_PROMOTION = 80_000;

    /**
     * Ordering bonus for a killer move.
     */
    private static final int ORDER_KILLER = 70_000;

    /**
     * Ordering bonus for a quiet checking move searched at the quiescence horizon.
     */
    private static final int ORDER_QUIET_CHECK = 60_000;

    /**
     * Ordering bonus for a quiet checking move in the main search. Kept below the
     * killer tier so proven cutoff moves still sort first.
     */
    private static final int ORDER_MAIN_QUIET_CHECK = 55_000;

    /**
     * Root-ordering tier for moves that have a score from the previous completed
     * iterative-deepening pass. Kept below the preferred and TT tiers, but above
     * static capture/promotion tiers, because a searched previous root score is a
     * stronger ordering signal than one-ply move shape.
     */
    private static final int ORDER_ROOT_PREVIOUS = 500_000;

    /**
     * Clamp for previous root scores before adding them to an ordering tier.
     */
    private static final int ROOT_PREVIOUS_SCORE_CLAMP = 100_000;

    /**
     * Root score table indexed by the unsigned 16-bit encoded move.
     */
    private static final int ROOT_SCORE_TABLE_SIZE = 1 << 16;

    /**
     * Initial per-ply move-score scratch width. A legal chess position fits inside
     * this in normal play; rows grow defensively if a generated list is larger.
     */
    private static final int MOVE_SCORE_BUFFER_SIZE = 256;

    /**
     * Number of distinct (piece, square) pairs, i.e. {@code 12 * 64}: the
     * continuation-history dimension. The one- and two-ply tables are
     * {@code CONT_PIECE_SQUARES * CONT_PIECE_SQUARES} entries, indexed by a
     * context (piece, to) over a move (piece, to).
     */
    private static final int CONT_PIECE_SQUARES = 12 * 64;

    /**
     * Upper bound on quiet moves tracked per ply for the history malus; a chess
     * position has at most ~218 legal moves, so 256 is a safe cap.
     */
    private static final int MAX_TRIED_QUIETS = 256;

    /**
     * Captured piece-type buckets for capture history (pawn through king).
     */
    private static final int CAPTURE_HISTORY_TYPES = 6;

    /**
     * Number of capture-history buckets: moving piece, destination square, and
     * captured piece type.
     */
    private static final int CAPTURE_HISTORY_BUCKETS = CONT_PIECE_SQUARES * CAPTURE_HISTORY_TYPES;

    /**
     * Upper bound on captures tracked per ply for capture-history maluses.
     */
    private static final int MAX_TRIED_CAPTURES = 64;

    /**
     * Number of buckets in each static-eval correction table (power of two).
     */
    private static final int CORRECTION_ENTRIES = 1 << 14;

    /**
     * Gravity bound on a stored correction value.
     */
    private static final int CORRECTION_MAX = 1 << 12;

    /**
     * Divisor mapping a stored correction value to an applied centipawn delta.
     */
    private static final int CORRECTION_DIV = 32;

    /**
     * Absolute cap (centipawns) on the correction applied to a static evaluation.
     */
    private static final int CORRECTION_CLAMP = 96;

    /**
     * Continuation-history score at or above which a quiet move is reduced one ply
     * less in late-move reductions.
     */
    private static final int CONT_HISTORY_LMR_GOOD = 1 << 12;

    /**
     * Continuation-history score at or below which a quiet move is reduced one ply
     * more in late-move reductions.
     */
    private static final int CONT_HISTORY_LMR_BAD = -(1 << 12);

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
     * Default enabled features for production searches — the strongest measured
     * configuration. SEE pruning + in-search repetition are the original shipped
     * gains; LMR table, the improving heuristic (which also enables the eval-scaled
     * null-move reduction), continuation history, check extensions, and IIR were
     * each measured as net positive in fixed-budget self-play. Features that
     * measured neutral/negative (history malus, capture history, SEE-LMR, root
     * score ordering, quiet-check ordering, ProbCut, razoring, singular extensions,
     * correction history) stay off but remain available through the three-argument
     * constructor for tuning.
     */
    private static final Set<Feature> DEFAULT_FEATURES =
            EnumSet.of(
                    Feature.SEE_PRUNING,
                    Feature.SEARCH_REPETITION,
                    Feature.LMR_TABLE,
                    Feature.IMPROVING,
                    Feature.CONT_HISTORY,
                    Feature.CHECK_EXTENSION,
                    Feature.IIR,
                    Feature.STABILITY_TIME);

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

        Result forcedMate = forcedMateRootResult(root, legalMoves, started);
        if (forcedMate != null) {
            notifyDepth(listener, forcedMate);
            return forcedMate;
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
     * Returns an exact short-mate proof before heuristic search spends budget.
     *
     * @param root root position
     * @param legalMoves legal root moves
     * @param started search start timestamp
     * @return forced-mate result, or null if no bounded proof was found
     */
    private static Result forcedMateRootResult(Position root, MoveList legalMoves, long started) {
        short mateInOne = findMateInOne(root, legalMoves);
        if (mateInOne != Move.NO_MOVE) {
            return new Result(
                    mateInOne,
                    MATE_SCORE - 1,
                    1,
                    0L,
                    elapsedSince(started),
                    false,
                    new short[] { mateInOne });
        }
        boolean forcingRoot = root.inCheck() || hasRootCheck(root, legalMoves);
        MateProver.Proof proof = forcingRoot
                ? MateProver.proveMate(root)
                : MateProver.proveMate(
                        root,
                        QUIET_ROOT_MATE_PROBE_MOVES,
                        QUIET_ROOT_MATE_PROBE_NODES);
        if (proof == null) {
            return null;
        }
        return new Result(
                proof.bestMove(),
                MATE_SCORE - proof.plies(),
                proof.plies(),
                0L,
                elapsedSince(started),
                false,
                proof.principalVariation());
    }

    /**
     * Returns a mate-in-one move without entering the broader proof search.
     *
     * @param root position to inspect
     * @param legalMoves legal moves from the position
     * @return mating move, or {@link Move#NO_MOVE}
     */
    private static short findMateInOne(Position root, MoveList legalMoves) {
        Position.State state = new Position.State();
        for (int i = 0; i < legalMoves.size(); i++) {
            short move = legalMoves.raw(i);
            root.play(move, state);
            try {
                if (root.inCheck() && root.legalMoves().isEmpty()) {
                    return move;
                }
            } finally {
                root.undo(move, state);
            }
        }
        return Move.NO_MOVE;
    }

    /**
     * Returns whether the root has at least one checking move.
     *
     * @param root root position
     * @param legalMoves legal root moves
     * @return true when a legal root move gives check
     */
    private static boolean hasRootCheck(Position root, MoveList legalMoves) {
        Position.State state = new Position.State();
        for (int i = 0; i < legalMoves.size(); i++) {
            short move = legalMoves.raw(i);
            root.play(move, state);
            try {
                if (root.inCheck()) {
                    return true;
                }
            } finally {
                root.undo(move, state);
            }
        }
        return false;
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
                new SearchContext(
                        started,
                        limits,
                        evaluator,
                        root,
                        table,
                        generationStart,
                        features)) {
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
            int[] previousRootScores = features.contains(Feature.ROOT_SCORE_ORDERING)
                    ? newRootScoreTable()
                    : null;
            boolean softStop = limits.softMillis() > 0L && features.contains(Feature.STABILITY_TIME);
            int stableIterations = 0;
            for (int depth = 1; depth <= limits.depth(); depth++) {
                if (threadIndex > 0 && skipIteration(threadIndex, depth)) {
                    // Helper diversification: this thread skips this depth so the
                    // pool covers different depths at once (Lazy SMP). The main
                    // thread (index 0) never skips, so the result is unaffected.
                    continue;
                }
                if (softStop && depth > 1
                        && elapsedSince(started) >= stabilityScaledSoftMillis(
                                limits.softMillis(), stableIterations)) {
                    // Soft stop: a stable best move releases time back to the
                    // clock; an unstable one extends toward the hard deadline,
                    // which still aborts mid-iteration via the context check.
                    break;
                }
                try {
                    context.newIteration();
                    short previousBest = preferred;
                    RootOutcome outcome = searchRootWithAspiration(
                            context,
                            root,
                            legalMoves,
                            depth,
                            preferred,
                            best.scoreCentipawns(),
                            previousRootScores);
                    preferred = outcome.bestMove();
                    stableIterations = outcome.bestMove() == previousBest ? stableIterations + 1 : 0;
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
     * Scales the soft time target by best-move stability: a best move that
     * just changed extends the target (more time to resolve the new line),
     * while one that has survived several iterations releases time back to
     * the clock. The hard deadline still bounds the search regardless of the
     * returned value.
     *
     * @param softMillis soft per-move target in milliseconds
     * @param stableIterations completed iterations since the best move changed
     * @return stability-scaled soft target in milliseconds
     */
    static long stabilityScaledSoftMillis(long softMillis, int stableIterations) {
        if (stableIterations == 0) {
            return softMillis * 17L / 10L;
        }
        if (stableIterations >= 4) {
            return softMillis * 11L / 20L;
        }
        if (stableIterations >= 2) {
            return softMillis * 3L / 4L;
        }
        return softMillis;
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
     * @param previousRootScores root move scores from the previous completed
     *        depth, or null when root score ordering is disabled
     * @return root outcome with an exact score for the completed depth
     */
    private static RootOutcome searchRootWithAspiration(
            SearchContext context,
            Position root,
            MoveList legalMoves,
            int depth,
            short preferredMove,
            int previousScore,
            int[] previousRootScores) {
        int[] iterationRootScores = previousRootScores == null ? null : newRootScoreTable();
        if (depth < ASPIRATION_START_DEPTH || isMateScore(previousScore)) {
            RootOutcome outcome = searchRoot(
                    context,
                    root,
                    legalMoves,
                    depth,
                    preferredMove,
                    -INF,
                    INF,
                    previousRootScores,
                    iterationRootScores);
            publishRootScores(previousRootScores, iterationRootScores);
            return outcome;
        }

        int window = ASPIRATION_WINDOW;
        int alpha = Math.max(-INF, previousScore - window);
        int beta = Math.min(INF, previousScore + window);
        while (true) {
            if (iterationRootScores != null) {
                Arrays.fill(iterationRootScores, NO_SCORE);
            }
            RootOutcome outcome = searchRoot(
                    context,
                    root,
                    legalMoves,
                    depth,
                    preferredMove,
                    alpha,
                    beta,
                    previousRootScores,
                    iterationRootScores);
            AspirationWindow widened = widenedAspirationWindow(outcome.score(), alpha, beta, window);
            if (widened == null) {
                publishRootScores(previousRootScores, iterationRootScores);
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
     * @param previousRootScores root move scores from the previous completed
     *        depth, or null when root score ordering is disabled
     * @param iterationRootScores destination for this depth's searched root
     *        scores, or null when root score ordering is disabled
     * @return root outcome
     */
    private static RootOutcome searchRoot(
            SearchContext context,
            Position root,
            MoveList legalMoves,
            int depth,
            short preferredMove,
            int alpha,
            int beta,
            int[] previousRootScores,
            int[] iterationRootScores) {
        context.clearPv();
        context.prepareMoveOrdering(root);
        short ttMove = context.transpositions.bestMove(root.signature());
        short[] moves = orderedRootMoves(root, legalMoves, preferredMove, ttMove, context, previousRootScores);
        Position.State state = new Position.State();
        RootSearchState searchState = new RootSearchState(depth, alpha);

        for (short move : moves) {
            int score = searchRootMove(context, root, move, depth, beta, searchState, state);
            recordRootMoveScore(iterationRootScores, move, score);
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
                scoreToTransposition(searchState.bestScore, 0),
                flag,
                searchState.bestMove,
                context.generation);
        return searchState.toOutcome();
    }

    /**
     * Allocates and initializes a per-root score table.
     *
     * @return table indexed by unsigned encoded move, filled with {@link #NO_SCORE}
     */
    private static int[] newRootScoreTable() {
        int[] scores = new int[ROOT_SCORE_TABLE_SIZE];
        Arrays.fill(scores, NO_SCORE);
        return scores;
    }

    /**
     * Publishes the final scores from a completed root iteration for next-depth
     * ordering. Failed aspiration-window probes are discarded by passing only the
     * accepted iteration table here.
     *
     * @param previousRootScores persistent previous-depth table, or null
     * @param iterationRootScores accepted current-depth table, or null
     */
    private static void publishRootScores(int[] previousRootScores, int[] iterationRootScores) {
        if (previousRootScores == null || iterationRootScores == null) {
            return;
        }
        for (int i = 0; i < iterationRootScores.length; i++) {
            if (iterationRootScores[i] != NO_SCORE) {
                previousRootScores[i] = iterationRootScores[i];
            }
        }
    }

    /**
     * Records one searched root move score into the current iteration table.
     *
     * @param iterationRootScores current-depth score table, or null
     * @param move searched root move
     * @param score root-perspective score returned by search
     */
    private static void recordRootMoveScore(int[] iterationRootScores, short move, int score) {
        if (iterationRootScores != null) {
            iterationRootScores[move & 0xFFFF] = score;
        }
    }

    /**
     * Searches one root move with PVS re-search rules.
     *
     * @param context search context
     * @param root mutable root position
     * @param move move encoded in CRTK move format
     * @param depth target search depth
     * @param beta beta bound
     * @param searchState mutable root-search state
     * @param state reusable position state
     * @return score for the searched root move
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
        // Hard recursion ceiling: a pathological run of forced extensions (check +
        // singular can stack) could otherwise drive ply past the per-ply scratch
        // arrays. Fall back to a static evaluation rather than overrun them. The
        // bound guarantees ply and ply+1 indices stay valid.
        if (ply + 2 >= context.pvLength.length) {
            return staticScore(context, position, ply);
        }
        context.pvLength[ply] = 0;
        alpha = Math.max(alpha, -MATE_SCORE + ply);
        beta = Math.min(beta, MATE_SCORE - ply - 1);
        if (alpha >= beta) {
            return alpha;
        }
        NegamaxSetup setup = prepareNegamax(context, position, depth, alpha, beta, ply, pvNode);
        if (setup.resolved()) {
            return setup.resolvedScore();
        }
        // Internal iterative reduction: with no transposition move to order first,
        // search one ply shallower to cheaply seed a best move for later re-visits
        // rather than spending full depth on a poorly ordered node.
        if (context.iir()
                && depth >= IIR_MIN_DEPTH
                && !setup.inCheck()
                && (setup.entry() == null || Transposition.moveOf(setup.entry().data) == Move.NO_MOVE)) {
            depth--;
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
            legalMoves = context.legalMoves(position, true);
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
        int transpositionScore = transpositionScore(entry, depth, alpha, beta, ply);
        // During a singular-extension verification search this node excludes one
        // move, so the stored full-node bound does not apply: probe for ordering
        // but never take the cutoff.
        if (transpositionScore != NO_SCORE && context.excluded(ply) == Move.NO_MOVE) {
            return NegamaxSetup.resolved(transpositionScore);
        }

        int staticEval = NO_SCORE;
        if (!inCheck) {
            staticEval = staticScore(context, position, ply);
        }
        // Record the static eval on the path (NO_SCORE in check) so deeper nodes
        // can compute the improving heuristic.
        context.recordStaticEval(ply, staticEval);
        if (!inCheck) {
            boolean improving = context.improving(ply, staticEval);
            int razorScore = tryRazoring(context, position, depth, alpha, beta, pvNode, ply, staticEval);
            if (razorScore != NO_SCORE) {
                return NegamaxSetup.resolved(razorScore);
            }
            int reverseFutilityScore = tryReverseFutilityPruning(depth, beta, pvNode, staticEval, improving);
            if (reverseFutilityScore != NO_SCORE) {
                return NegamaxSetup.resolved(reverseFutilityScore);
            }
            int nullScore = tryNullMovePruning(context, position, depth, beta, ply, pvNode, staticEval);
            if (nullScore != NO_SCORE) {
                return NegamaxSetup.resolved(nullScore);
            }
            int probCutScore = tryProbCut(context, position, depth, beta, ply, pvNode, staticEval, improving, entry);
            if (probCutScore != NO_SCORE) {
                return NegamaxSetup.resolved(probCutScore);
            }
        }

        if (legalMoves == null) {
            if (context.stagedPicker()) {
                // The staged picker generates lazily inside the move loop and
                // detects stalemate from its emission count, so this non-check
                // node skips up-front generation entirely (a null move list
                // routes the node to the staged search path).
                return NegamaxSetup.search(false, null, alpha, staticEval, key, entry);
            }
            legalMoves = context.legalMoves(position, false);
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
        if (setup.legalMoves() == null) {
            // Staged picker: prepareNegamax skipped up-front generation for this
            // non-check node, so moves are generated lazily stage by stage.
            return searchNegamaxMovesStaged(context, position, depth, beta, ply, pvNode, setup);
        }
        short ttMove = setup.entry() == null ? Move.NO_MOVE : Transposition.moveOf(setup.entry().data);
        // Copy the legal moves into a stable array NOW, before singularExtension's
        // verification search regenerates the shared move-gen scratch underneath
        // us. This toArray is the same copy orderedMoves used to make; the
        // expensive scoring/sort is deferred below.
        short[] moves = setup.legalMoves().toArray();
        Position.State state = context.state(ply);
        NegamaxSearchState searchState = new NegamaxSearchState(setup.alpha());
        boolean improving = context.improving(ply, setup.staticEval());
        // Buffer of quiet moves searched before a cutoff, for the history/
        // continuation-history malus (needed by either feature). triedCount is a
        // one-element holder so it can persist across the helper calls.
        boolean buffering = context.historyMalus() || context.contHistory();
        short[] triedQuiets = buffering ? context.triedQuietsBuffer(ply) : null;
        int[] triedContIdx = context.contHistory() ? context.triedContIdxBuffer(ply) : null;
        int[] triedCount = { 0 };
        short[] triedCaptures = context.captureHistory() ? context.triedCapturesBuffer(ply) : null;
        int[] triedCaptureCount = { 0 };
        // Singular extension: probe whether the transposition move is much better
        // than every alternative; if so it is extended one ply.
        int ttMoveExtension = singularExtension(context, position, setup, ttMove, depth, beta, ply);
        // Read after the singular probe (which restores it): NO_MOVE for a normal
        // node, or this node's own excluded move when it is itself a verification.
        short excluded = context.excluded(ply);
        // Search the transposition move before scoring and sorting the rest, so a
        // TT-move cutoff (the most common cutoff) skips ordering the whole list,
        // including its per-capture SEE checks. Behaviour-identical: the TT move is
        // already ordered first, so the searched moves and their order are unchanged.
        boolean cutoff = false;
        boolean ttSearched = false;
        if (ttMove != Move.NO_MOVE && ttMove != excluded && arrayContains(moves, ttMove)) {
            cutoff = searchNodeMove(context, position, state, depth, beta, ply, pvNode, setup,
                    ttMove, ttMove, ttMoveExtension, improving, searchState, triedQuiets, triedContIdx, triedCount,
                    triedCaptures, triedCaptureCount);
            ttSearched = true;
        }
        if (!cutoff) {
            scoreAndSortMoves(position, moves, Move.NO_MOVE, ttSearched ? Move.NO_MOVE : ttMove, context, ply);
            for (short move : moves) {
                if (move == excluded || (ttSearched && move == ttMove)) {
                    continue;
                }
                if (searchNodeMove(context, position, state, depth, beta, ply, pvNode, setup,
                        move, ttMove, ttMoveExtension, improving, searchState, triedQuiets, triedContIdx, triedCount,
                        triedCaptures, triedCaptureCount)) {
                    break;
                }
            }
        }
        byte flag = transpositionFlag(searchState.bestScore, searchState.originalAlpha, beta);
        // A verification search (excluded move set) searched a restricted move set,
        // so its result must not be stored under the full-node key.
        if (excluded == Move.NO_MOVE) {
            context.transpositions.store(
                    setup.key(),
                    depth,
                    scoreToTransposition(searchState.bestScore, ply),
                    flag,
                    searchState.bestMove,
                    context.generation);
        }
        // Learn the static-eval correction when the searched score is a usable
        // bound on the (corrected) static eval: exact, a fail-high above it, or a
        // fail-low below it. Skipped in check, in a verification search, and for
        // mate scores (handled inside updateCorrection).
        if (excluded == Move.NO_MOVE && !setup.inCheck() && setup.staticEval() != NO_SCORE
                && (flag == TT_EXACT
                        || (flag == TT_LOWER && searchState.bestScore > setup.staticEval())
                        || (flag == TT_UPPER && searchState.bestScore < setup.staticEval()))) {
            context.updateCorrection(position, setup.staticEval(), searchState.bestScore);
        }
        return searchState.bestScore;
    }

    /**
     * Searches the moves of one prepared non-check negamax node through the
     * staged move picker. The picker validates and yields the transposition
     * move before generating anything, then generates, orders, and
     * legality-checks tacticals, killers, quiets, and deferred SEE-losing
     * captures lazily, so a cutoff in an early stage skips all later-stage
     * generation and scoring work. Per-move handling is shared with the eager
     * path via {@link #searchNodeMove}, so pruning, reductions, and heuristic
     * updates behave identically per move; the emitted move SET is exactly the
     * legal move set (regression-verified), while the emission ORDER follows
     * the fixed stage tiers rather than the eager global sort, so the searched
     * trees legitimately differ.
     *
     * <p>
     * Because no up-front legal list exists, stalemate is detected from the
     * picker's emission count: a non-check node whose picker yields nothing has
     * no legal moves. A singular-verification node whose only legal move is the
     * excluded one returns its fail-low best score instead, matching the eager
     * loop's behaviour.
     * </p>
     *
     * @param context search context
     * @param position mutable current position
     * @param depth remaining depth
     * @param beta beta bound
     * @param ply current ply from root
     * @param pvNode true when this node lies on the principal variation
     * @param setup prepared node setup with no move list
     * @return best score found for the node
     */
    private static int searchNegamaxMovesStaged(
            SearchContext context,
            Position position,
            int depth,
            int beta,
            int ply,
            boolean pvNode,
            NegamaxSetup setup) {
        short ttMove = setup.entry() == null ? Move.NO_MOVE : Transposition.moveOf(setup.entry().data);
        Position.State state = context.state(ply);
        NegamaxSearchState searchState = new NegamaxSearchState(setup.alpha());
        boolean improving = context.improving(ply, setup.staticEval());
        boolean buffering = context.historyMalus() || context.contHistory();
        short[] triedQuiets = buffering ? context.triedQuietsBuffer(ply) : null;
        int[] triedContIdx = context.contHistory() ? context.triedContIdxBuffer(ply) : null;
        int[] triedCount = { 0 };
        short[] triedCaptures = context.captureHistory() ? context.triedCapturesBuffer(ply) : null;
        int[] triedCaptureCount = { 0 };
        // The singular probe runs before the picker exists: its same-ply
        // verification search regenerates the shared move scratch, which the
        // picker must not be holding at that point.
        int ttMoveExtension = singularExtension(context, position, setup, ttMove, depth, beta, ply);
        short excluded = context.excluded(ply);
        StagedMovePicker picker = new StagedMovePicker(context, position, ply, ttMove, excluded);
        int emitted = 0;
        for (short move = picker.next(); move != Move.NO_MOVE; move = picker.next()) {
            emitted++;
            if (searchNodeMove(context, position, state, depth, beta, ply, pvNode, setup,
                    move, ttMove, ttMoveExtension, improving, searchState, triedQuiets, triedContIdx, triedCount,
                    triedCaptures, triedCaptureCount)) {
                break;
            }
        }
        if (emitted == 0) {
            // No legal move at a non-check node is stalemate, except in a
            // verification search whose only legal move is the excluded one;
            // that node fails low exactly like the eager loop does.
            return excluded == Move.NO_MOVE ? terminalScore(position, ply) : searchState.bestScore;
        }
        byte flag = transpositionFlag(searchState.bestScore, searchState.originalAlpha, beta);
        if (excluded == Move.NO_MOVE) {
            context.transpositions.store(
                    setup.key(),
                    depth,
                    scoreToTransposition(searchState.bestScore, ply),
                    flag,
                    searchState.bestMove,
                    context.generation);
        }
        if (excluded == Move.NO_MOVE && !setup.inCheck() && setup.staticEval() != NO_SCORE
                && (flag == TT_EXACT
                        || (flag == TT_LOWER && searchState.bestScore > setup.staticEval())
                        || (flag == TT_UPPER && searchState.bestScore < setup.staticEval()))) {
            context.updateCorrection(position, setup.staticEval(), searchState.bestScore);
        }
        return searchState.bestScore;
    }

    /**
     * Searches one move at a negamax node: applies futility/late-move pruning,
     * buffers quiet moves for the history malus, searches the move, records the
     * result, and on a beta cutoff updates the quiet-move heuristics. Shared by
     * the transposition-move-first path and the ordered move loop so both behave
     * identically.
     *
     * @param context search context
     * @param position mutable current position
     * @param state reusable move undo state
     * @param depth remaining depth
     * @param beta beta bound
     * @param ply current ply from root
     * @param pvNode true at principal-variation nodes
     * @param setup prepared node setup (static eval, check status)
     * @param move move to search
     * @param ttMove transposition move (extended when {@code move == ttMove})
     * @param ttMoveExtension singular extension to apply to the transposition move
     * @param improving whether the side to move is improving
     * @param searchState mutable node search state
     * @param triedQuiets quiet-move buffer, or null
     * @param triedContIdx parallel continuation-index buffer, or null
     * @param triedCount one-element holder of the tried-quiet count
     * @param triedCaptures capture-move buffer, or null
     * @param triedCaptureCount one-element holder of the tried-capture count
     * @return true when the move caused a beta cutoff
     */
    private static boolean searchNodeMove(
            SearchContext context,
            Position position,
            Position.State state,
            int depth,
            int beta,
            int ply,
            boolean pvNode,
            NegamaxSetup setup,
            short move,
            short ttMove,
            int ttMoveExtension,
            boolean improving,
            NegamaxSearchState searchState,
            short[] triedQuiets,
            int[] triedContIdx,
            int[] triedCount,
            short[] triedCaptures,
            int[] triedCaptureCount) {
        boolean tactical = isTacticalMove(position, move);
        boolean capture = context.captureHistory() && isCaptureMove(position, move);
        boolean losingCapture = context.seeLmr() && tactical && isLosingCapture(position, move);
        int contIdx = !tactical && context.contHistory() ? contTargetIndex(position, move) : -1;
        int moveExtension = move == ttMove ? ttMoveExtension : 0;
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
                ply,
                improving,
                contIdx,
                moveExtension);
        if (shouldFutilityPrune(context, decision) || shouldLateMovePrune(context, decision)) {
            return false;
        }
        if (triedQuiets != null && !tactical && triedCount[0] < triedQuiets.length) {
            if (triedContIdx != null) {
                triedContIdx[triedCount[0]] = contIdx;
            }
            triedQuiets[triedCount[0]] = move;
            triedCount[0]++;
        }
        if (triedCaptures != null && capture && triedCaptureCount[0] < triedCaptures.length) {
            triedCaptures[triedCaptureCount[0]] = move;
            triedCaptureCount[0]++;
        }
        int score = searchNegamaxMove(context, position, state, depth, beta, pvNode, decision);
        searchState.recordScore(context, ply, move, score);
        if (searchState.alpha >= beta) {
            if (!tactical) {
                context.recordQuietCutoff(move, depth, ply, triedQuiets, triedCount[0]);
            } else if (capture) {
                context.recordCaptureCutoff(position, move, depth, triedCaptures, triedCaptureCount[0]);
            }
            return true;
        }
        return false;
    }

    /**
     * Returns whether a move is present in a move array (cheap linear scan, no
     * scoring), used to confirm a transposition move is legal here before
     * searching it ahead of move ordering.
     *
     * @param moves legal move array
     * @param move move to find
     * @return true when present
     */
    private static boolean arrayContains(short[] moves, short move) {
        for (short candidate : moves) {
            if (candidate == move) {
                return true;
            }
        }
        return false;
    }

    /**
     * Computes a singular extension for the transposition move: when a reduced
     * verification search of every <em>other</em> move fails low beneath a margin
     * under the stored score, the transposition move is "singular" (clearly best)
     * and is searched one ply deeper. Returns 0 when singular extensions are off,
     * the preconditions are unmet, or the move is not singular.
     *
     * @param context search context
     * @param position mutable current position (the node, before any move)
     * @param setup prepared node setup (holds the transposition entry)
     * @param ttMove transposition move, or {@link Move#NO_MOVE}
     * @param depth remaining depth at this node
     * @param beta beta bound
     * @param ply current ply from root
     * @return 1 to extend the transposition move, or 0
     */
    private static int singularExtension(
            SearchContext context,
            Position position,
            NegamaxSetup setup,
            short ttMove,
            int depth,
            int beta,
            int ply) {
        if (!context.singularExt()
                || ttMove == Move.NO_MOVE
                || setup.inCheck()
                || depth < SINGULAR_MIN_DEPTH
                || setup.entry() == null
                || context.excluded(ply) != Move.NO_MOVE) {
            return 0;
        }
        long data = setup.entry().data;
        int ttScore = scoreFromTransposition(Transposition.scoreOf(data), ply);
        byte ttFlag = Transposition.flagOf(data);
        // Need a trustworthy lower bound on the move's value from a deep-enough
        // search, and a non-mate score (mate distances must not be perturbed).
        if (Transposition.depthOf(data) < depth - 3
                || (ttFlag != TT_LOWER && ttFlag != TT_EXACT)
                || isMateScore(ttScore)) {
            return 0;
        }
        int singularBeta = Math.max(-INF + 1, ttScore - SINGULAR_MARGIN_PER_DEPTH * depth);
        int singularDepth = (depth - 1) / 2;
        context.setExcluded(ply, ttMove);
        int value;
        try {
            value = negamax(context, position, singularDepth, singularBeta - 1, singularBeta, ply, false);
        } finally {
            context.setExcluded(ply, Move.NO_MOVE);
        }
        // Every alternative failed below singularBeta: the transposition move stands
        // alone, so extend it.
        return value < singularBeta ? 1 : 0;
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
            // A move proven singular is forced one ply deeper; otherwise extend a
            // checking move by one ply so forcing lines are not cut at the horizon.
            // Both are capped so extended lines can't overrun the scratch arrays,
            // and an extended move is never also reduced.
            int extension = decision.singularExtension();
            if (extension == 0
                    && context.checkExtensions()
                    && position.inCheck()) {
                extension = 1;
            }
            if (decision.ply() + 1 >= context.maxExtendedPly()) {
                extension = 0;
            }
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
        int reducedDepth = Math.max(0,
                depth - 1 - nullMoveReduction(depth, staticEval, beta, context.improvingFeature()));
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
            int staticEval,
            boolean improving) {
        if (pvNode
                || depth > REVERSE_FUTILITY_MAX_DEPTH
                || staticEval == NO_SCORE
                || Math.abs(beta) >= MATE_THRESHOLD) {
            return NO_SCORE;
        }
        // Require a smaller margin (prune more) when the side to move is improving,
        // since a fail-high is then more trustworthy. improving is always false
        // when its feature is off, preserving the original margin.
        int margin = REVERSE_FUTILITY_MARGIN * (depth - (improving ? 1 : 0));
        return staticEval - margin >= beta ? staticEval : NO_SCORE;
    }

    /**
     * Attempts razoring: at shallow depth in a non-PV node, when even a generous
     * margin over the static evaluation cannot reach alpha, verify with a
     * quiescence search and fail low directly if it confirms, skipping the quiet
     * move search entirely.
     *
     * @param context search context
     * @param position mutable current position
     * @param depth remaining depth
     * @param alpha alpha bound
     * @param beta beta bound
     * @param pvNode true when this node is in a principal-variation window
     * @param ply current ply from root
     * @param staticEval cached static evaluation
     * @return a fail-low quiescence score, or {@link #NO_SCORE}
     */
    private static int tryRazoring(
            SearchContext context,
            Position position,
            int depth,
            int alpha,
            int beta,
            boolean pvNode,
            int ply,
            int staticEval) {
        if (!context.razoring()
                || pvNode
                || depth > RAZOR_MAX_DEPTH
                || staticEval == NO_SCORE
                || Math.abs(alpha) >= MATE_THRESHOLD
                || staticEval + RAZOR_MARGIN * depth > alpha) {
            return NO_SCORE;
        }
        int score = quiescence(context, position, alpha, beta, ply, 0);
        return score <= alpha ? score : NO_SCORE;
    }

    /**
     * Attempts ProbCut at a deep non-PV node: search only tactical moves that can
     * plausibly exceed a beta-plus-margin threshold, first with quiescence and then
     * with a reduced-depth null-window search.
     *
     * @param context search context
     * @param position mutable current position
     * @param depth remaining depth
     * @param beta beta bound
     * @param ply current ply from root
     * @param pvNode true when this node is in a principal-variation window
     * @param staticEval cached static evaluation from side-to-move perspective
     * @param improving whether the side to move is statically improving
     * @param entry transposition-table entry for this node, or null
     * @return cutoff score or {@link #NO_SCORE}
     */
    private static int tryProbCut(
            SearchContext context,
            Position position,
            int depth,
            int beta,
            int ply,
            boolean pvNode,
            int staticEval,
            boolean improving,
            Transposition entry) {
        if (!context.probCut()
                || pvNode
                || depth < PROBCUT_MIN_DEPTH
                || staticEval == NO_SCORE
                || Math.abs(beta) >= MATE_THRESHOLD
                || context.excluded(ply) != Move.NO_MOVE) {
            return NO_SCORE;
        }
        int probCutBeta = beta + PROBCUT_MARGIN - (improving ? PROBCUT_IMPROVING_MARGIN : 0);
        if (Math.abs(probCutBeta) >= MATE_THRESHOLD || ttRefutesProbCut(entry, depth, probCutBeta, ply)) {
            return NO_SCORE;
        }
        MoveList tacticals = context.legalTacticals(position, false);
        if (tacticals.isEmpty()) {
            return NO_SCORE;
        }
        short[] moves = tacticals.toArray();
        short ttMove = entry == null ? Move.NO_MOVE : Transposition.moveOf(entry.data);
        scoreAndSortMoves(position, moves, Move.NO_MOVE, ttMove, context, ply);

        Position.State state = context.state(ply);
        int reducedDepth = Math.max(0, depth - PROBCUT_REDUCTION);
        for (short move : moves) {
            if (!isProbCutCandidate(position, move, staticEval, probCutBeta)) {
                continue;
            }
            position.play(move, state);
            try {
                context.movePlayed(position, move, state, ply + 1);
                int qscore = -quiescence(context, position, -probCutBeta, -probCutBeta + 1, ply + 1, 0);
                if (qscore < probCutBeta) {
                    continue;
                }
                int score = -negamax(context, position, reducedDepth, -probCutBeta, -probCutBeta + 1, ply + 1, false);
                if (score >= probCutBeta && !isMateScore(score)) {
                    return score;
                }
            } finally {
                position.undo(move, state);
            }
        }
        return NO_SCORE;
    }

    /**
     * Returns whether the current transposition entry makes a ProbCut attempt
     * unlikely to pay off.
     *
     * @param entry transposition entry, or null
     * @param depth remaining depth
     * @param probCutBeta beta-plus-margin threshold
     * @return true when a fresh enough exact/upper entry is below the threshold
     */
    private static boolean ttRefutesProbCut(Transposition entry, int depth, int probCutBeta, int ply) {
        if (entry == null || Transposition.depthOf(entry.data) < depth - 3) {
            return false;
        }
        int score = scoreFromTransposition(Transposition.scoreOf(entry.data), ply);
        byte flag = Transposition.flagOf(entry.data);
        return !isMateScore(score)
                && score < probCutBeta
                && (flag == TT_EXACT || flag == TT_UPPER);
    }

    /**
     * Returns whether a tactical move is large and stable enough to justify a
     * ProbCut verification search.
     *
     * @param position parent position
     * @param move tactical move
     * @param staticEval cached static evaluation
     * @param probCutBeta beta-plus-margin threshold
     * @return true when the move can plausibly exceed {@code probCutBeta}
     */
    private static boolean isProbCutCandidate(Position position, short move, int staticEval, int probCutBeta) {
        int threshold = probCutBeta - staticEval;
        int gain = captureValue(position, move);
        int promotion = Move.getPromotion(move);
        if (promotion != 0) {
            gain += Math.max(0, promotionValue(promotion) - Piece.VALUE_PAWN);
        }
        if (gain <= 0) {
            return false;
        }
        if (promotion == 4 && gain >= threshold - PROBCUT_IMPROVING_MARGIN) {
            return true;
        }
        return See.seeGreaterEqual(position, move, Math.max(0, threshold));
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
     * Computes the null-move depth reduction. When {@code evalScaled} is set, a
     * static evaluation comfortably above beta reduces harder (a voluntary pass is
     * then more likely to still fail high), bounded by a few plies. With it off the
     * reduction is the historical depth-only formula, so the baseline is unchanged.
     *
     * @param depth remaining depth before the null move
     * @param staticEval static evaluation, or {@link #NO_SCORE}
     * @param beta beta bound
     * @param evalScaled whether to add the eval-margin term
     * @return plies to reduce after the skipped turn
     */
    private static int nullMoveReduction(int depth, int staticEval, int beta, boolean evalScaled) {
        int reduction = 2 + depth / 6;
        if (evalScaled && staticEval != NO_SCORE) {
            reduction += Math.min(3, Math.max(0, (staticEval - beta) / 200));
        }
        return reduction;
    }

    /**
     * Returns whether a quiet shallow move can be skipped by futility pruning.
     *
     * @param context search context
     * @param decision move decision metadata
     * @return true when the move can be skipped
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
     * @param context search context
     * @param decision move decision metadata
     * @return reduction in plies, or zero for a full-depth search
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
            // Reduce more when the side to move is not improving (improving is
            // always false when the feature is off, so this is gated on it).
            if (context.improvingFeature() && !decision.improving()) {
                reduction++;
            }
            // Reduce strong continuations less and weak ones more.
            if (context.contHistory() && decision.contIdx() >= 0) {
                int ch = context.contHistScore(decision.ply(), decision.contIdx());
                if (ch >= CONT_HISTORY_LMR_GOOD) {
                    reduction--;
                } else if (ch <= CONT_HISTORY_LMR_BAD) {
                    reduction++;
                }
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
        // Hard recursion ceiling (see negamax): never index the per-ply arrays out
        // of range, even under a pathological extension/quiescence chain.
        if (ply + 2 >= context.pvLength.length) {
            return staticScore(context, position, ply);
        }
        context.pvLength[ply] = 0;
        alpha = Math.max(alpha, -MATE_SCORE + ply);
        beta = Math.min(beta, MATE_SCORE - ply - 1);
        if (alpha >= beta) {
            return alpha;
        }
        QuiescenceSetup setup = prepareQuiescence(context, position, alpha, beta, ply, qply);
        if (setup.resolved()) {
            return setup.resolvedScore();
        }

        short[] moves = orderedQuiescenceMoves(
                position,
                setup.legalMoves(),
                context,
                ply,
                setup.inCheck(),
                qply == 0);
        int currentAlpha = setup.alpha();
        for (short move : moves) {
            if (!setup.inCheck()) {
                boolean tactical = isTacticalMove(position, move);
                if (tactical) {
                    if (shouldDeltaPrune(position, move, setup.standPat(), currentAlpha, false)) {
                        continue;
                    }
                    // Skip captures that lose material outright; a losing capture
                    // almost never improves a quiet stand-pat and only deepens the
                    // tree. Check evasions (inCheck) and bounded quiet checks are
                    // never pruned this way.
                    if (context.seePruning() && isLosingCapture(position, move)) {
                        continue;
                    }
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
            MoveList legalMoves = context.legalMoves(position, true);
            if (legalMoves.isEmpty()) {
                return QuiescenceSetup.resolved(terminalScore(position, ply));
            }
            return qply >= QUIESCENCE_MAX_PLY
                    ? QuiescenceSetup.resolved(staticScore(context, position, ply))
                    : QuiescenceSetup.search(legalMoves, true, NO_SCORE, alpha);
        }

        MoveList horizonMoves = null;
        if (qply == 0) {
            horizonMoves = context.legalMoves(position, false);
            if (horizonMoves.isEmpty()) {
                return QuiescenceSetup.resolved(0);
            }
            if (findMateInOne(position, horizonMoves) != Move.NO_MOVE) {
                return QuiescenceSetup.resolved(MATE_SCORE - ply - 1);
            }
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
        if (horizonMoves != null) {
            return QuiescenceSetup.search(horizonMoves, false, standPat, Math.max(alpha, standPat));
        }
        // Generate only tactical moves below the first quiescence ply. An empty
        // tactical list is NOT stalemate, so fall back to a cheap legal-move
        // probe before standing pat.
        MoveList tacticals = context.legalTacticals(position, false);
        if (tacticals.isEmpty()) {
            if (!position.hasLegalMove()) {
                return QuiescenceSetup.resolved(0);
            }
            return QuiescenceSetup.resolved(Math.max(alpha, standPat));
        }
        return QuiescenceSetup.search(tacticals, false, standPat, Math.max(alpha, standPat));
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
     * @param ply current ply from root
     * @return score or {@link #NO_SCORE}
     */
    private static int transpositionScore(Transposition entry, int depth, int alpha, int beta, int ply) {
        if (entry == null) {
            return NO_SCORE;
        }
        long data = entry.data;
        if (Transposition.depthOf(data) < depth) {
            return NO_SCORE;
        }
        int score = scoreFromTransposition(Transposition.scoreOf(data), ply);
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
     * Returns whether a move captures material.
     *
     * @param position parent position
     * @param move move to inspect
     * @return true for normal and en-passant captures
     */
    private static boolean isCaptureMove(Position position, short move) {
        return captureHistoryIndex(position, move) >= 0;
    }

    /**
     * Capture-history index for a move, keyed by moving piece, destination square,
     * and captured piece type.
     *
     * @param position parent position
     * @param move capture move
     * @return table index, or -1 for non-captures / malformed moves
     */
    private static int captureHistoryIndex(Position position, short move) {
        int from = Move.getFromIndex(move);
        int to = Move.getToIndex(move);
        int moving = pieceIndex(position.pieceAt(from));
        int captured = capturedPieceType(position, move);
        if (moving < 0 || captured < 0) {
            return -1;
        }
        return (moving * 64 + to) * CAPTURE_HISTORY_TYPES + captured;
    }

    /**
     * Dense captured-piece type for capture history.
     *
     * @param position parent position
     * @param move move to inspect
     * @return 0 for pawn through 5 for king, or -1 when the move is not a capture
     */
    private static int capturedPieceType(Position position, short move) {
        int from = Move.getFromIndex(move);
        int to = Move.getToIndex(move);
        byte moving = position.pieceAt(from);
        byte victim = position.pieceAt(to);
        if (victim != Piece.EMPTY && Piece.isWhite(victim) != position.isWhiteToMove()) {
            return Math.abs(victim) - 1;
        }
        return isEnPassantCapture(position, moving, from, to) ? Piece.PAWN - 1 : -1;
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
        MoveList legalMoves = position.legalMoves();
        if (legalMoves.isEmpty()) {
            return -terminalScore(position, ply);
        }
        if (findMateInOne(position, legalMoves) != Move.NO_MOVE) {
            return -(MATE_SCORE - ply - 1);
        }
        return -staticScore(context, position, ply);
    }

    /**
     * Orders root moves. When previous root scores are available, searched
     * previous-depth scores dominate ordinary static move shape for non-PV moves;
     * the explicit preferred and transposition moves still stay first.
     *
     * @param position root position before the moves
     * @param moveList root legal moves
     * @param preferredMove previous best move, or {@link Move#NO_MOVE}
     * @param transpositionMove stored root move, or {@link Move#NO_MOVE}
     * @param context search context containing history and evaluator hooks
     * @param previousRootScores previous-depth scores indexed by encoded move, or
     *        null when disabled
     * @return sorted root move array
     */
    private static short[] orderedRootMoves(
            Position position,
            MoveList moveList,
            short preferredMove,
            short transpositionMove,
            SearchContext context,
            int[] previousRootScores) {
        short[] moves = moveList.toArray();
        int[] scores = context.scoreBuffer(0, moves.length);
        for (int i = 0; i < moves.length; i++) {
            scores[i] = moveOrderScore(position, moves[i], preferredMove, transpositionMove, context, 0)
                    + rootPreviousScoreBonus(moves[i], previousRootScores);
        }
        context.scoreMoves(position, moves, scores);
        insertionSortDescending(moves, scores, moves.length);
        return moves;
    }

    /**
     * Converts a previous-depth root score into a move-ordering bonus.
     *
     * @param move encoded move
     * @param previousRootScores previous-depth score table, or null
     * @return ordering bonus, or zero when no previous score is available
     */
    private static int rootPreviousScoreBonus(short move, int[] previousRootScores) {
        if (previousRootScores == null) {
            return 0;
        }
        int previous = previousRootScores[move & 0xFFFF];
        if (previous == NO_SCORE) {
            return 0;
        }
        int clamped = Math.max(-ROOT_PREVIOUS_SCORE_CLAMP, Math.min(ROOT_PREVIOUS_SCORE_CLAMP, previous));
        return ORDER_ROOT_PREVIOUS + clamped;
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
        scoreAndSortMoves(position, moves, preferredMove, transpositionMove, context, ply);
        return moves;
    }

    /**
     * Scores and descending-sorts a move array in place (the expensive part of
     * move ordering, including per-capture SEE). Split out from {@link #orderedMoves}
     * so the search can copy the move list cheaply first and defer scoring until
     * after the transposition move is tried.
     *
     * @param position parent position
     * @param moves move array to score and sort in place
     * @param preferredMove move to order first, or {@link Move#NO_MOVE}
     * @param transpositionMove stored best move to order early, or {@link Move#NO_MOVE}
     * @param context search context
     * @param ply current ply from root
     */
    private static void scoreAndSortMoves(
            Position position,
            short[] moves,
            short preferredMove,
            short transpositionMove,
            SearchContext context,
            int ply) {
        int[] scores = context.scoreBuffer(ply, moves.length);
        for (int i = 0; i < moves.length; i++) {
            scores[i] = moveOrderScore(position, moves[i], preferredMove, transpositionMove, context, ply);
        }
        context.scoreMoves(position, moves, scores);
        insertionSortDescending(moves, scores, moves.length);
    }

    /**
     * Orders quiescence moves, filtering non-tactical moves outside check except
     * for bounded first-ply quiet checks.
     *
     * @param position parent position
     * @param moveList legal moves for the node
     * @param context search context containing history and killer tables
     * @param ply current ply from root
     * @param inCheck true when the side to move is in check
     * @param includeQuietChecks true to include legal quiet checks
     * @return sorted move array containing all legal evasions in check, or only
     *         tactical moves otherwise
     */
    private static short[] orderedQuiescenceMoves(
            Position position,
            MoveList moveList,
            SearchContext context,
            int ply,
            boolean inCheck,
            boolean includeQuietChecks) {
        if (inCheck) {
            return orderedMoves(position, moveList, Move.NO_MOVE, Move.NO_MOVE, context, ply);
        }
        short[] allMoves = moveList.toArray();
        short[] moves = new short[allMoves.length];
        int[] scores = context.scoreBuffer(ply, allMoves.length);
        int candidateCount = 0;
        for (short move : allMoves) {
            boolean tactical = isTacticalMove(position, move);
            boolean quietCheck = !tactical && includeQuietChecks && givesCheck(position, move, context.checkState());
            if (!tactical && !quietCheck) {
                continue;
            }
            moves[candidateCount] = move;
            scores[candidateCount] = moveOrderScore(position, move, Move.NO_MOVE, Move.NO_MOVE, context, ply);
            if (quietCheck) {
                scores[candidateCount] += ORDER_QUIET_CHECK;
            }
            candidateCount++;
        }
        if (candidateCount == 0) {
            return new short[0];
        }
        moves = Arrays.copyOf(moves, candidateCount);
        context.scoreMoves(position, moves, scores);
        insertionSortDescending(moves, scores, candidateCount);
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
        int score = move == preferredMove ? ORDER_PREFERRED : 0;
        if (move == transpositionMove) {
            score += ORDER_TT;
        }
        int from = Move.getFromIndex(move);
        int to = Move.getToIndex(move);
        byte moving = position.pieceAt(from);
        byte victim = position.pieceAt(to);
        if (victim != Piece.EMPTY && Piece.isWhite(victim) != position.isWhiteToMove()) {
            // Winning/equal captures sort ahead of quiets; SEE-losing captures
            // drop below quiets so they are searched last.
            int base = context.seePruning() && isLosingCapture(position, move) ? ORDER_LOSING_CAPTURE : ORDER_CAPTURE;
            score += base + Piece.getValue(victim) * 10 - Piece.getValue(moving)
                    + context.captureHistoryScore(position, move);
        } else if (isEnPassantCapture(position, moving, from, to)) {
            score += ORDER_CAPTURE + Piece.VALUE_PAWN * 10 - Piece.getValue(moving)
                    + context.captureHistoryScore(position, move);
        }
        int promotion = Move.getPromotion(move);
        if (promotion != 0) {
            score += ORDER_PROMOTION + promotionValue(promotion);
        }
        if (score == 0) {
            if (context.isKiller(move, ply)) {
                score += ORDER_KILLER;
            } else if (context.checkOrdering() && givesCheck(position, move, context.checkState())) {
                score += ORDER_MAIN_QUIET_CHECK;
            }
            score += context.historyScore(move);
            if (context.contHistory()) {
                score += context.contHistScore(ply, contTargetIndex(position, move));
            }
        }
        return score;
    }

    /**
     * Scores one tactical move for the staged picker's tactical stage:
     * MVV-LVA plus capture history for captures and en passant, plus the
     * promotion tier. Mirrors the capture/promotion arms of
     * {@link #moveOrderScore} without the SEE good/bad split, because the
     * picker runs SEE lazily at emission time and defers losing captures
     * instead of down-scoring them.
     *
     * @param position parent position
     * @param move tactical move
     * @param context search context
     * @return ordering score
     */
    private static int tacticalOrderScore(Position position, short move, SearchContext context) {
        int from = Move.getFromIndex(move);
        int to = Move.getToIndex(move);
        byte moving = position.pieceAt(from);
        byte victim = position.pieceAt(to);
        int score = 0;
        if (victim != Piece.EMPTY && Piece.isWhite(victim) != position.isWhiteToMove()) {
            score += ORDER_CAPTURE + Piece.getValue(victim) * 10 - Piece.getValue(moving)
                    + context.captureHistoryScore(position, move);
        } else if (isEnPassantCapture(position, moving, from, to)) {
            score += ORDER_CAPTURE + Piece.VALUE_PAWN * 10 - Piece.getValue(moving)
                    + context.captureHistoryScore(position, move);
        }
        int promotion = Move.getPromotion(move);
        if (promotion != 0) {
            score += ORDER_PROMOTION + promotionValue(promotion);
        }
        return score;
    }

    /**
     * Scores one quiet move for the staged picker's quiet stage: history and
     * continuation history, plus the quiet-check bonus when check ordering is
     * enabled. Mirrors the quiet arm of {@link #moveOrderScore} without the
     * killer branch, because killers are emitted by their own earlier stage.
     *
     * @param position parent position
     * @param move quiet move
     * @param context search context
     * @param ply current ply
     * @return ordering score
     */
    private static int quietOrderScore(Position position, short move, SearchContext context, int ply) {
        int score = 0;
        if (context.checkOrdering() && givesCheck(position, move, context.checkState())) {
            score += ORDER_MAIN_QUIET_CHECK;
        }
        score += context.historyScore(move);
        if (context.contHistory()) {
            score += context.contHistScore(ply, contTargetIndex(position, move));
        }
        return score;
    }

    /**
     * Maps a piece byte code to a dense 0..11 index (white pawn..king = 0..5,
     * black pawn..king = 6..11) for continuation-history keys. Empty maps to -1.
     *
     * @param piece piece byte code
     * @return dense piece index, or -1 for an empty square
     */
    private static int pieceIndex(byte piece) {
        if (piece == Piece.EMPTY) {
            return -1;
        }
        return piece > 0 ? piece - 1 : 5 - piece;
    }

    /**
     * Continuation-history target index ({@code piece*64 + to}) for a move played
     * from {@code position}, or -1 when the source square is empty (should not
     * happen for a legal move).
     *
     * @param position parent position before the move
     * @param move encoded move
     * @return target index in 0..767, or -1
     */
    private static int contTargetIndex(Position position, short move) {
        int piece = pieceIndex(position.pieceAt(Move.getFromIndex(move)));
        if (piece < 0) {
            return -1;
        }
        return piece * 64 + Move.getToIndex(move);
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
     * Returns whether a legal move gives check.
     *
     * @param position parent position
     * @param move encoded move
     * @return true when the opponent is in check after the move
     */
    private static boolean givesCheck(Position position, short move, Position.State state) {
        position.play(move, state);
        try {
            return position.inCheck();
        } finally {
            position.undo(move, state);
        }
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
     * @param length number of aligned entries to sort
     */
    private static void insertionSortDescending(short[] moves, int[] scores, int length) {
        for (int i = 1; i < length; i++) {
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
     * @param ply ply from root
     * @return score clamped to the non-mate static range
     */
    private static int staticScore(SearchContext context, Position position, int ply) {
        int score = context.evaluate(position, ply) + context.correctionFor(position);
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
     * Converts a root-relative mate score into a transposition-table score.
     *
     * <p>
     * Search scores encode mate distance from the root by adding/subtracting the
     * current ply. A transposition-table entry can be probed at a different ply,
     * so mate scores must be stored relative to the table position itself. This
     * mirrors the standard engine convention used by Stockfish-style searches.
     * Non-mate scores are stored unchanged.
     * </p>
     *
     * @param score root-relative search score
     * @param ply current ply from root
     * @return score normalized for table storage
     */
    private static int scoreToTransposition(int score, int ply) {
        if (score >= MATE_THRESHOLD) {
            return score + ply;
        }
        if (score <= -MATE_THRESHOLD) {
            return score - ply;
        }
        return score;
    }

    /**
     * Converts a transposition-table score back into the current root-relative
     * search frame.
     *
     * @param score table-normalized score
     * @param ply current ply from root
     * @return root-relative score for this probe
     */
    private static int scoreFromTransposition(int score, int ply) {
        if (score >= MATE_THRESHOLD) {
            return score - ply;
        }
        if (score <= -MATE_THRESHOLD) {
            return score + ply;
        }
        return score;
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
     * Walks the staged move picker over one position and returns every move it
     * emits, in emission order. Verification-only entry point: regression
     * tests assert that the emitted set equals the legal move set under
     * arbitrary transposition-move and killer injections, which is the
     * correctness contract of the picker's pseudo-legal generation plus lazy
     * legality validation. Not used by the search itself.
     *
     * @param position position to emit for; must not be in check
     * @param ttMove transposition move to inject, possibly arbitrary bits
     * @param killer0 primary killer to inject, possibly arbitrary bits
     * @param killer1 secondary killer to inject, possibly arbitrary bits
     * @return emitted moves in emission order
     */
    public static short[] stagedMoveEmission(Position position, short ttMove, short killer0, short killer1) {
        if (position.inCheck()) {
            throw new IllegalArgumentException("staged picker emission requires a non-check position");
        }
        Position root = position.copy();
        try (SearchContext context = new SearchContext(
                System.currentTimeMillis(),
                new Limits(2, 0L, 0L),
                new Classical(),
                root,
                new TranspositionTable(1024),
                0,
                // SEE pruning is enabled so the emission walk also exercises
                // the bad-capture deferral stage, not just the direct path.
                EnumSet.of(Feature.STAGED_PICKER, Feature.SEE_PRUNING))) {
            context.killers[0][0] = killer0;
            context.killers[0][1] = killer1;
            StagedMovePicker picker = new StagedMovePicker(context, root, 0, ttMove, Move.NO_MOVE);
            MoveList emitted = new MoveList();
            for (short move = picker.next(); move != Move.NO_MOVE; move = picker.next()) {
                emitted.add(move);
            }
            return emitted.toArray();
        }
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
     * Lazy staged move generator for one non-check negamax node.
     *
     * <p>
     * Stages, in emission order: validated transposition move (no generation),
     * pseudo-legal tacticals ordered by MVV-LVA/promotion/capture-history with
     * SEE-losing captures deferred, killer moves, pseudo-legal quiets ordered by
     * history (plus evaluator priors), and finally the deferred losing captures.
     * Legality is established lazily per emitted move via the same
     * pin/king fast path and play/undo fallback the eager generator uses, so the
     * emitted move set is exactly the legal move set. The picker reads the
     * board between emissions, so callers must restore the position (make/undo)
     * before each {@link #next()} call; killer slots are captured at
     * construction because same-ply verification searches run before the picker
     * exists.
     * </p>
     */
    private static final class StagedMovePicker {

        /**
         * Stage constant: validated transposition move.
         */
        private static final int STAGE_TT = 0;

        /**
         * Stage constant: generate and order tactical moves.
         */
        private static final int STAGE_GEN_TACTICALS = 1;

        /**
         * Stage constant: emit ordered tactical moves.
         */
        private static final int STAGE_TACTICALS = 2;

        /**
         * Stage constant: emit killer moves.
         */
        private static final int STAGE_KILLERS = 3;

        /**
         * Stage constant: generate and order quiet moves.
         */
        private static final int STAGE_GEN_QUIETS = 4;

        /**
         * Stage constant: emit ordered quiet moves.
         */
        private static final int STAGE_QUIETS = 5;

        /**
         * Stage constant: emit deferred SEE-losing captures.
         */
        private static final int STAGE_BAD_CAPTURES = 6;

        /**
         * Stage constant: exhausted.
         */
        private static final int STAGE_DONE = 7;

        /**
         * Owning search context, for scratch buffers and ordering tables.
         */
        private final SearchContext context;

        /**
         * Node position; mutated transiently by legality probes only.
         */
        private final Position position;

        /**
         * Node ply, for per-ply scratch and history keys.
         */
        private final int ply;

        /**
         * Transposition move to try first, or {@link Move#NO_MOVE}.
         */
        private final short ttMove;

        /**
         * Excluded move of a singular verification search, or {@link Move#NO_MOVE}.
         */
        private final short excluded;

        /**
         * Primary killer slot captured at construction.
         */
        private final short killer0;

        /**
         * Secondary killer slot captured at construction.
         */
        private final short killer1;

        /**
         * Side to move at the node.
         */
        private final boolean white;

        /**
         * Absolutely pinned friendly pieces, for the legality fast path.
         */
        private final long pinned;

        /**
         * Friendly king square, for the legality fast path.
         */
        private final int king;

        /**
         * En-passant target square, for the legality fast path.
         */
        private final int enPassant;

        /**
         * Deferred SEE-losing captures, backed by per-ply context scratch.
         */
        private final short[] badCaptures;

        /**
         * Number of deferred SEE-losing captures.
         */
        private int badCount;

        /**
         * Current stage.
         */
        private int stage = STAGE_TT;

        /**
         * Sorted move array of the current generation stage.
         */
        private short[] moves;

        /**
         * Emission cursor into {@link #moves} or {@link #badCaptures}.
         */
        private int index;

        /**
         * Next killer slot to consider.
         */
        private int killerSlot;

        /**
         * Creates a picker for one non-check node.
         *
         * @param context search context
         * @param position node position, not in check
         * @param ply current ply from root
         * @param ttMove transposition move, possibly arbitrary bits
         * @param excluded excluded move of a verification search, or
         *        {@link Move#NO_MOVE}
         */
        private StagedMovePicker(SearchContext context, Position position, int ply, short ttMove, short excluded) {
            this.context = context;
            this.position = position;
            this.ply = ply;
            this.ttMove = ttMove;
            this.excluded = excluded;
            this.killer0 = context.killer(ply, 0);
            this.killer1 = context.killer(ply, 1);
            this.white = position.isWhiteToMove();
            this.pinned = MoveGenerator.pinnedPieces(position, white);
            this.king = position.kingSquare(white);
            this.enPassant = position.enPassantSquare();
            this.badCaptures = context.pickerBadCaptures(ply);
        }

        /**
         * Yields the next legal move, advancing stages as each one empties.
         *
         * @return next legal move, or {@link Move#NO_MOVE} when exhausted
         */
        private short next() {
            while (true) {
                switch (stage) {
                    case STAGE_TT -> {
                        stage = STAGE_GEN_TACTICALS;
                        if (ttMove != Move.NO_MOVE && ttMove != excluded
                                && MoveGenerator.isPseudoLegal(position, ttMove)
                                && isLegal(ttMove)) {
                            return ttMove;
                        }
                    }
                    case STAGE_GEN_TACTICALS -> {
                        moves = context.pseudoTacticals(position).toArray();
                        int[] scores = context.scoreBuffer(ply, moves.length);
                        for (int i = 0; i < moves.length; i++) {
                            scores[i] = tacticalOrderScore(position, moves[i], context);
                        }
                        // Evaluator priors apply per stage (a no-op for the
                        // classical evaluator, which boosts quiets only, but it
                        // keeps policy-backed evaluators' capture priors).
                        context.scoreMoves(position, moves, scores);
                        insertionSortDescending(moves, scores, moves.length);
                        index = 0;
                        stage = STAGE_TACTICALS;
                    }
                    case STAGE_TACTICALS -> {
                        while (index < moves.length) {
                            short move = moves[index++];
                            if (move == ttMove || move == excluded || !isLegal(move)) {
                                continue;
                            }
                            if (context.seePruning() && isLosingCapture(position, move)) {
                                badCaptures[badCount++] = move;
                                continue;
                            }
                            return move;
                        }
                        stage = STAGE_KILLERS;
                    }
                    case STAGE_KILLERS -> {
                        while (killerSlot < 2) {
                            short killer = killerSlot == 0 ? killer0 : killer1;
                            killerSlot++;
                            if (killer == Move.NO_MOVE || killer == ttMove || killer == excluded
                                    || (killerSlot == 2 && killer == killer0)
                                    || isTacticalMove(position, killer)
                                    || !MoveGenerator.isPseudoLegal(position, killer)
                                    || !isLegal(killer)) {
                                continue;
                            }
                            return killer;
                        }
                        stage = STAGE_GEN_QUIETS;
                    }
                    case STAGE_GEN_QUIETS -> {
                        moves = context.pseudoQuiets(position).toArray();
                        int[] scores = context.scoreBuffer(ply, moves.length);
                        for (int i = 0; i < moves.length; i++) {
                            short move = moves[i];
                            // Moves already emitted (or excluded) are skipped at
                            // emission by identity; park them last unscored so
                            // they pay no history reads or check probes.
                            scores[i] = move == ttMove || move == excluded || move == killer0 || move == killer1
                                    ? Integer.MIN_VALUE
                                    : quietOrderScore(position, move, context, ply);
                        }
                        context.scoreMoves(position, moves, scores);
                        insertionSortDescending(moves, scores, moves.length);
                        index = 0;
                        stage = STAGE_QUIETS;
                    }
                    case STAGE_QUIETS -> {
                        while (index < moves.length) {
                            short move = moves[index++];
                            if (move == ttMove || move == excluded || move == killer0 || move == killer1
                                    || !isLegal(move)) {
                                continue;
                            }
                            return move;
                        }
                        index = 0;
                        stage = STAGE_BAD_CAPTURES;
                    }
                    case STAGE_BAD_CAPTURES -> {
                        if (index < badCount) {
                            return badCaptures[index++];
                        }
                        stage = STAGE_DONE;
                    }
                    default -> {
                        return Move.NO_MOVE;
                    }
                }
            }
        }

        /**
         * Tests full legality of a pseudo-legal move at this non-check node,
         * mirroring the eager generator's filtering: the pin/king fast path
         * first, then a transient play/undo king-safety check.
         *
         * @param move pseudo-legal move to test
         * @return true when the move is legal
         */
        private boolean isLegal(short move) {
            if (MoveGenerator.isUsuallyLegal(position, move, pinned, king, enPassant)) {
                return true;
            }
            position.play(move, context.genState);
            boolean legal = !MoveGenerator.isKingAttacked(position, white);
            position.undo(move, context.genState);
            return legal;
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
         * Whether the improving heuristic (static-eval path tracking) is enabled.
         */
        private final boolean improvingFeature;

        /**
         * Whether continuation (counter-move) history is maintained and used.
         */
        private final boolean contHistory;

        /**
         * Whether capture history is maintained and used for capture ordering.
         */
        private final boolean captureHistory;

        /**
         * Whether quiet checking moves get a main-search ordering bonus.
         */
        private final boolean checkOrdering;

        /**
         * Whether internal iterative reductions are enabled.
         */
        private final boolean iir;

        /**
         * Whether ProbCut tactical pre-search pruning is enabled.
         */
        private final boolean probCut;

        /**
         * Whether shallow razoring into quiescence is enabled.
         */
        private final boolean razoring;

        /**
         * Whether singular extensions are enabled.
         */
        private final boolean singularExt;

        /**
         * Whether static-eval correction history is enabled.
         */
        private final boolean correctionHistory;

        /**
         * Whether the staged move picker drives non-check nodes.
         */
        private final boolean stagedPicker;

        /**
         * Per-ply buffers holding SEE-losing captures the staged picker deferred
         * past the quiet stage. Only allocated when {@link #stagedPicker} is set.
         */
        private final short[][] pickerBadCaptures;

        /**
         * Static-eval correction table keyed by pawn-and-king structure plus side to
         * move. Gravity-bounded; only allocated when {@link #correctionHistory} is
         * set. Per-SearchContext (hence per-thread under Lazy SMP).
         */
        private final int[] correction;

        /**
         * Static evaluation (side-to-move perspective) recorded for each ply on the
         * current search path, or {@link #NO_SCORE} when unknown (e.g. in check).
         * Used by the improving heuristic. Only maintained when
         * {@link #improvingFeature} is set.
         */
        private final int[] pathEval;

        /**
         * Continuation-history context: the 0..11 piece index of the move that was
         * played to reach each ply, or -1 for the root / a null move. Paired with
         * {@link #contTo}. Only maintained when {@link #contHistory} is set.
         */
        private final int[] contPiece;

        /**
         * Continuation-history context: the destination square of the move that was
         * played to reach each ply. Paired with {@link #contPiece}.
         */
        private final int[] contTo;

        /**
         * One-ply continuation history, indexed by
         * {@code (contextPiece*64 + contextTo) * CONT_PIECE_SQUARES + (movePiece*64 + moveTo)}
         * and bounded like the butterfly history. Only allocated when
         * {@link #contHistory} is set.
         */
        private final short[] contHist1;

        /**
         * Two-ply continuation history, same indexing as {@link #contHist1} but
         * keyed by the move two plies back.
         */
        private final short[] contHist2;

        /**
         * Capture history indexed by moving piece, destination square, and captured
         * piece type. Only allocated when {@link #captureHistory} is set.
         */
        private final short[] captureHist;

        /**
         * Per-ply move currently excluded from search at that ply, used by singular
         * extensions (the verification search omits the transposition move).
         * {@link Move#NO_MOVE} when nothing is excluded. Only allocated when
         * {@link #singularExt} is set.
         */
        private final short[] excluded;

        /**
         * Per-ply scratch holding the quiet moves searched at a node before a
         * cutoff, so they can receive a history malus. Only allocated/used when
         * {@link #historyMalus} is set.
         */
        private final short[][] triedQuiets;

        /**
         * Per-ply scratch parallel to {@link #triedQuiets}, holding each tried
         * quiet's continuation-history target index ({@code piece*64 + to}), so the
         * cutoff update can penalize them without re-deriving the moved piece. Only
         * allocated/used when {@link #contHistory} is set.
         */
        private final int[][] triedQuietContIdx;

        /**
         * Per-ply scratch holding captures searched before a capture cutoff.
         */
        private final short[][] triedCaptures;

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
         * Reusable per-ply score rows for move ordering. The first {@code n}
         * entries align with the currently ordered {@code n} moves; rows may be
         * longer than the active move list.
         */
        private final int[][] moveScores;

        /**
         * Reusable pseudo-legal scratch list for allocation-free move generation.
         * Safe as a single shared buffer because each generated list is consumed
         * (copied out by ordering) within the same node before any recursion.
         */
        private final MoveList genPseudo = new MoveList();

        /**
         * Reusable legal-move output list, paired with {@link #genPseudo}.
         */
        private final MoveList genLegal = new MoveList();

        /**
         * Reusable undo state for the transient play/undo legality checks inside
         * move generation (distinct from the per-ply search {@link #states}).
         */
        private final Position.State genState = new Position.State();

        /**
         * Reusable undo state for transient checking-move probes during move ordering.
         */
        private final Position.State checkState = new Position.State();

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
            // Without stability-based time management a soft target is simply
            // the deadline (the pre-soft-budget behavior); with it the search
            // may run past the soft target up to the hard budget, and the
            // iterative-deepening loop applies the stability-scaled soft stop.
            boolean stabilityTime = features.contains(Feature.STABILITY_TIME);
            long hardMillis = limits.softMillis() > 0L && !stabilityTime
                    ? limits.softMillis()
                    : limits.maxDurationMillis();
            this.deadline = computeDeadline(started, hardMillis);
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
            this.improvingFeature = features.contains(Feature.IMPROVING);
            this.contHistory = features.contains(Feature.CONT_HISTORY);
            this.captureHistory = features.contains(Feature.CAPTURE_HISTORY);
            this.checkOrdering = features.contains(Feature.CHECK_ORDERING);
            this.iir = features.contains(Feature.IIR);
            this.probCut = features.contains(Feature.PROBCUT);
            this.razoring = features.contains(Feature.RAZORING);
            this.singularExt = features.contains(Feature.SINGULAR_EXT);
            this.correctionHistory = features.contains(Feature.CORRECTION_HISTORY);
            this.stagedPicker = features.contains(Feature.STAGED_PICKER);
            this.correction = correctionHistory ? new int[CORRECTION_ENTRIES] : null;
            // Check and singular extensions can both deepen the main search beyond
            // the nominal depth, so when either is enabled reserve a full extra
            // depth's worth of plies (plus quiescence headroom) and cap the
            // extension ply below that ceiling. When disabled the arrays keep their
            // original size.
            int extensionHeadroom = (checkExtensions || singularExt) ? limits.depth() : 0;
            int searchPlies = limits.depth() + extensionHeadroom + QUIESCENCE_MAX_PLY + 4;
            this.maxExtendedPly = searchPlies - QUIESCENCE_MAX_PLY - 2;
            this.incrementalState = evaluator.openSearchState(root, searchPlies);
            this.pv = new short[searchPlies][searchPlies];
            this.pvLength = new int[searchPlies];
            this.states = new Position.State[searchPlies];
            this.nullStates = new Position.State[searchPlies];
            this.killers = new short[searchPlies][2];
            this.pathKeys = new long[searchPlies];
            this.moveScores = new int[searchPlies][MOVE_SCORE_BUFFER_SIZE];
            // The tried-quiet buffer feeds both the history malus and the
            // continuation-history malus, so it is needed by either feature.
            boolean needTried = historyMalus || contHistory;
            this.triedQuiets = needTried ? new short[searchPlies][MAX_TRIED_QUIETS] : null;
            this.triedQuietContIdx = contHistory ? new int[searchPlies][MAX_TRIED_QUIETS] : null;
            this.pathEval = improvingFeature ? new int[searchPlies] : null;
            this.contPiece = contHistory ? new int[searchPlies] : null;
            this.contTo = contHistory ? new int[searchPlies] : null;
            // Continuation history is keyed by (contextPiece*64+contextTo) over the
            // (movePiece*64+moveTo) target, i.e. CONT_PIECE_SQUARES^2 buckets.
            this.contHist1 = contHistory ? new short[CONT_PIECE_SQUARES * CONT_PIECE_SQUARES] : null;
            this.contHist2 = contHistory ? new short[CONT_PIECE_SQUARES * CONT_PIECE_SQUARES] : null;
            this.captureHist = captureHistory ? new short[CAPTURE_HISTORY_BUCKETS] : null;
            this.triedCaptures = captureHistory ? new short[searchPlies][MAX_TRIED_CAPTURES] : null;
            this.pickerBadCaptures = stagedPicker ? new short[searchPlies][MOVE_SCORE_BUFFER_SIZE] : null;
            this.excluded = singularExt ? new short[searchPlies] : null;
            for (int i = 0; i < searchPlies; i++) {
                states[i] = new Position.State();
                nullStates[i] = new Position.State();
            }
            for (short[] row : killers) {
                Arrays.fill(row, Move.NO_MOVE);
            }
            if (improvingFeature) {
                Arrays.fill(pathEval, NO_SCORE);
            }
            if (contHistory) {
                // The root has no preceding move, so its continuation context is
                // empty (-1 piece is treated as "no context" by lookups).
                Arrays.fill(contPiece, -1);
            }
            if (singularExt) {
                Arrays.fill(excluded, Move.NO_MOVE);
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
         * @param ply ply from root
         * @return centipawn score
         */
        private int evaluate(Position position, int ply) {
            long key = position.signature();
            int cached = evalCache.get(key);
            if (cached != EvalCache.MISS) {
                return cached;
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
            if (contHistory && ply < contPiece.length) {
                // position is already AFTER the move, so the piece now on the
                // destination square is the (possibly promoted) moving piece.
                int to = Move.getToIndex(move);
                contPiece[ply] = pieceIndex(position.pieceAt(to));
                contTo[ply] = to;
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
            if (contHistory && ply < contPiece.length) {
                // A null move has no continuation context for the child node.
                contPiece[ply] = -1;
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
         * Returns whether the improving heuristic is enabled.
         *
         * @return true when static-eval path tracking is active
         */
        private boolean improvingFeature() {
            return improvingFeature;
        }

        /**
         * Returns whether continuation history is enabled.
         *
         * @return true when continuation history is maintained
         */
        private boolean contHistory() {
            return contHistory;
        }

        /**
         * Returns whether capture history is enabled.
         *
         * @return true when capture history is maintained
         */
        private boolean captureHistory() {
            return captureHistory;
        }

        /**
         * Returns whether quiet checking moves get an ordering bonus.
         *
         * @return true when quiet checks are ordered above ordinary quiets
         */
        private boolean checkOrdering() {
            return checkOrdering;
        }

        /**
         * Returns whether internal iterative reductions are enabled.
         *
         * @return true when IIR is active
         */
        private boolean iir() {
            return iir;
        }

        /**
         * Returns whether ProbCut is enabled.
         *
         * @return true when tactical ProbCut pruning is active
         */
        private boolean probCut() {
            return probCut;
        }

        /**
         * Returns whether razoring is enabled.
         *
         * @return true when shallow razoring is active
         */
        private boolean razoring() {
            return razoring;
        }

        /**
         * Returns whether singular extensions are enabled.
         *
         * @return true when singular extensions are active
         */
        private boolean singularExt() {
            return singularExt;
        }

        /**
         * Returns whether the staged move picker drives non-check nodes.
         *
         * @return true when staged move picking is active
         */
        private boolean stagedPicker() {
            return stagedPicker;
        }

        /**
         * Returns the staged picker's deferred bad-capture buffer for a ply.
         *
         * @param ply current search ply
         * @return caller-owned buffer slot
         */
        private short[] pickerBadCaptures(int ply) {
            return pickerBadCaptures[ply];
        }

        /**
         * Returns one killer slot for a ply, or {@link Move#NO_MOVE} when the
         * ply is past the killer table.
         *
         * @param ply ply whose killer slot should be read
         * @param slot killer slot, 0 or 1
         * @return stored killer move or {@link Move#NO_MOVE}
         */
        private short killer(int ply, int slot) {
            return ply < killers.length ? killers[ply][slot] : Move.NO_MOVE;
        }

        /**
         * Generates pseudo-legal tactical moves into the shared scratch list.
         * Valid only until the next generation in this context; callers copy the
         * list out before any recursion.
         *
         * @param position position to generate for
         * @return the shared scratch list, filled
         */
        private MoveList pseudoTacticals(Position position) {
            MoveGenerator.generatePseudoLegalTacticals(position, genPseudo);
            return genPseudo;
        }

        /**
         * Generates pseudo-legal quiet moves into the shared scratch list.
         * Valid only until the next generation in this context; callers copy the
         * list out before any recursion.
         *
         * @param position position to generate for
         * @return the shared scratch list, filled
         */
        private MoveList pseudoQuiets(Position position) {
            MoveGenerator.generatePseudoLegalQuiets(position, genPseudo);
            return genPseudo;
        }

        /**
         * Correction-table bucket for a position, keyed by pawn-and-king structure
         * and side to move (the placement signals the evaluator's systematic bias).
         *
         * @param position position to key
         * @return bucket index in {@code [0, CORRECTION_ENTRIES)}
         */
        private static int correctionKey(Position position) {
            long w = position.pieces(Position.WHITE_PAWN)
                    ^ Long.rotateLeft(position.pieces(Position.WHITE_KING), 1);
            long b = position.pieces(Position.BLACK_PAWN)
                    ^ Long.rotateLeft(position.pieces(Position.BLACK_KING), 1);
            long h = w * 0x9E3779B97F4A7C15L ^ Long.rotateLeft(b, 32) * 0xC2B2AE3D27D4EB4FL;
            if (!position.isWhiteToMove()) {
                h = ~h;
            }
            h ^= h >>> 29;
            return (int) h & (CORRECTION_ENTRIES - 1);
        }

        /**
         * Returns the learned static-eval correction (centipawns) for a position,
         * or zero when correction history is disabled. Clamped to a safe magnitude.
         *
         * @param position position being evaluated
         * @return signed centipawn correction
         */
        private int correctionFor(Position position) {
            if (!correctionHistory) {
                return 0;
            }
            int c = correction[correctionKey(position)] / CORRECTION_DIV;
            return Math.max(-CORRECTION_CLAMP, Math.min(CORRECTION_CLAMP, c));
        }

        /**
         * Folds the gap between a node's searched score and its (corrected) static
         * evaluation back into the correction table via a gravity-bounded update, so
         * future evaluations of similar pawn structures are nudged toward reality.
         * No-op when disabled, in check (no static eval), or for mate scores.
         *
         * @param position node position
         * @param staticEval the node's static evaluation (already corrected)
         * @param bestScore the node's searched score
         */
        private void updateCorrection(Position position, int staticEval, int bestScore) {
            if (!correctionHistory || staticEval == NO_SCORE || isMateScore(bestScore)) {
                return;
            }
            int key = correctionKey(position);
            int diff = bestScore - staticEval;
            correction[key] += diff - correction[key] * Math.abs(diff) / CORRECTION_MAX;
        }

        /**
         * Returns the move currently excluded from search at a ply (singular
         * extensions), or {@link Move#NO_MOVE} when none / the feature is off.
         *
         * @param ply ply from root
         * @return excluded move, or {@link Move#NO_MOVE}
         */
        private short excluded(int ply) {
            return excluded == null ? Move.NO_MOVE : excluded[ply];
        }

        /**
         * Sets (or clears with {@link Move#NO_MOVE}) the excluded move at a ply for
         * a singular-extension verification search. A no-op when the feature is off.
         *
         * @param ply ply from root
         * @param move move to exclude, or {@link Move#NO_MOVE} to clear
         */
        private void setExcluded(int ply, short move) {
            if (excluded != null) {
                excluded[ply] = move;
            }
        }

        /**
         * Records a node's static evaluation on the path (improving heuristic).
         * Stores {@link #NO_SCORE} when the value is unknown (in check), so an
         * earlier sibling's value cannot leak in.
         *
         * @param ply current ply from root
         * @param eval static evaluation, or {@link #NO_SCORE}
         */
        private void recordStaticEval(int ply, int eval) {
            if (improvingFeature && ply < pathEval.length) {
                pathEval[ply] = eval;
            }
        }

        /**
         * Returns whether the side to move is "improving": its static evaluation is
         * higher than two plies earlier. Unknown (returns false) at the first two
         * plies, when in check, or when the two-plies-back value is unknown.
         *
         * @param ply current ply from root
         * @param staticEval this node's static evaluation
         * @return true when the position is improving for the side to move
         */
        private boolean improving(int ply, int staticEval) {
            if (!improvingFeature || staticEval == NO_SCORE || ply < 2) {
                return false;
            }
            int prev = pathEval[ply - 2];
            return prev != NO_SCORE && staticEval > prev;
        }

        /**
         * Returns the per-ply scratch buffer for tried quiets' continuation indices.
         *
         * @param ply current ply
         * @return reusable buffer (never null when {@link #contHistory} is set)
         */
        private int[] triedContIdxBuffer(int ply) {
            return triedQuietContIdx[ply];
        }

        /**
         * Sums the one- and two-ply continuation-history scores for a quiet move
         * with the given target index, under the current path context. Returns zero
         * when continuation history is disabled or no context is available.
         *
         * @param ply current ply from root (context = move that reached this ply)
         * @param targetIdx the move's {@code piece*64 + to} index, or -1
         * @return summed continuation-history score
         */
        private int contHistScore(int ply, int targetIdx) {
            if (!contHistory || targetIdx < 0) {
                return 0;
            }
            int score = 0;
            int p1 = contPiece[ply];
            if (p1 >= 0) {
                score += contHist1[(p1 * 64 + contTo[ply]) * CONT_PIECE_SQUARES + targetIdx];
            }
            if (ply >= 1) {
                int p2 = contPiece[ply - 1];
                if (p2 >= 0) {
                    score += contHist2[(p2 * 64 + contTo[ply - 1]) * CONT_PIECE_SQUARES + targetIdx];
                }
            }
            return score;
        }

        /**
         * Applies a gravity-bounded continuation-history delta for a target index
         * under the current path context (both the one- and two-ply tables).
         *
         * @param ply current ply from root
         * @param targetIdx move target index ({@code piece*64 + to}), or -1
         * @param delta signed bonus or malus
         */
        private void applyContGravity(int ply, int targetIdx, int delta) {
            if (targetIdx < 0) {
                return;
            }
            int p1 = contPiece[ply];
            if (p1 >= 0) {
                int idx = (p1 * 64 + contTo[ply]) * CONT_PIECE_SQUARES + targetIdx;
                contHist1[idx] = gravityShort(contHist1[idx], delta);
            }
            if (ply >= 1) {
                int p2 = contPiece[ply - 1];
                if (p2 >= 0) {
                    int idx = (p2 * 64 + contTo[ply - 1]) * CONT_PIECE_SQUARES + targetIdx;
                    contHist2[idx] = gravityShort(contHist2[idx], delta);
                }
            }
        }

        /**
         * Returns the learned capture-history score for a capture.
         *
         * @param position parent position
         * @param move move to score
         * @return bounded capture-history value, or zero when disabled/non-capture
         */
        private int captureHistoryScore(Position position, short move) {
            if (!captureHistory) {
                return 0;
            }
            int index = captureHistoryIndex(position, move);
            return index < 0 ? 0 : captureHist[index];
        }

        /**
         * Applies a gravity-bounded capture-history delta.
         *
         * @param position parent position
         * @param move capture move
         * @param delta signed bonus or malus
         */
        private void applyCaptureGravity(Position position, short move, int delta) {
            int index = captureHistoryIndex(position, move);
            if (index >= 0) {
                captureHist[index] = gravityShort(captureHist[index], delta);
            }
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
         * Returns the per-ply scratch buffer for capture moves tried at a node.
         *
         * @param ply current ply
         * @return reusable capture buffer
         */
        private short[] triedCapturesBuffer(int ply) {
            return triedCaptures[ply];
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
         * Returns a reusable score row for move ordering at this ply.
         *
         * @param ply current search ply
         * @param required required active prefix length
         * @return score row with at least {@code required} entries
         */
        private int[] scoreBuffer(int ply, int required) {
            int[] scores = moveScores[ply];
            if (scores.length < required) {
                scores = Arrays.copyOf(scores, required);
                moveScores[ply] = scores;
            }
            return scores;
        }

        /**
         * Lets the evaluator add any position-specific move priors.
         *
         * @param position position whose moves are being ordered
         * @param moves legal moves aligned with the prefix of {@code scores}
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
         * Returns the reusable checking-probe state.
         *
         * @return caller-owned transient state
         */
        private Position.State checkState() {
            return checkState;
        }

        /**
         * Generates legal moves into the shared scratch lists (allocation-free),
         * reusing a known check status to skip a redundant king-attack scan. The
         * returned list is valid only until the next generation in this context,
         * which is always after the caller has consumed it.
         *
         * @param position position to generate for
         * @param inCheck whether the side to move is in check
         * @return the shared legal-move list
         */
        private MoveList legalMoves(Position position, boolean inCheck) {
            return position.legalMoves(genPseudo, genLegal, genState, inCheck);
        }

        /**
         * Generates only legal tactical moves into the shared scratch, for
         * quiescence (no quiet moves, so an empty result is not stalemate).
         *
         * @param position position to generate for
         * @param inCheck whether the side to move is in check
         * @return the shared legal-tactical list
         */
        private MoveList legalTacticals(Position position, boolean inCheck) {
            return position.legalTacticals(genPseudo, genLegal, genState, inCheck);
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
            if (nodes >= maxNodes) {
                throw SEARCH_STOPPED;
            }
            nodes++;
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
            if (shouldStop()) {
                return false;
            }
            nodes++;
            return true;
        }

        /**
         * Shifts a quiet cutoff move into the killer table for a ply, demoting the
         * previous primary killer to the secondary slot. A no-op when the move is
         * already the primary killer or the ply is past the killer table.
         *
         * @param move quiet cutoff move
         * @param ply current ply
         */
        private void updateKillers(short move, int ply) {
            if (ply < killers.length && killers[ply][0] != move) {
                killers[ply][1] = killers[ply][0];
                killers[ply][0] = move;
            }
        }

        /**
         * Records a beta cutoff from a quiet move.
         *
         * @param move quiet move
         * @param depth remaining depth
         * @param ply current ply
         */
        private void recordQuietCutoff(short move, int depth, int ply) {
            updateKillers(move, ply);
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
            if (historyMalus) {
                updateKillers(move, ply);
                int bonus = Math.min(depth * depth, HISTORY_MAX);
                applyGravity(historyIndex(move), bonus);
                for (int i = 0; i < triedCount; i++) {
                    if (tried[i] != move) {
                        applyGravity(historyIndex(tried[i]), -bonus);
                    }
                }
            } else {
                recordQuietCutoff(move, depth, ply);
            }
            // Continuation history is updated regardless of the main-history scheme:
            // reward the cutoff move's piece-to under the path context and penalize
            // the quiet moves tried before it.
            if (contHistory) {
                int[] contIdx = triedQuietContIdx[ply];
                int bonus = Math.min(depth * depth, HISTORY_MAX);
                for (int i = 0; i < triedCount; i++) {
                    applyContGravity(ply, contIdx[i], tried[i] == move ? bonus : -bonus);
                }
            }
        }

        /**
         * Records a beta cutoff from a capture and penalizes captures searched
         * earlier at the same node.
         *
         * @param position parent position
         * @param move cutoff capture
         * @param depth remaining depth
         * @param tried buffer of capture moves tried before the cutoff
         * @param triedCount number of valid entries in {@code tried}
         */
        private void recordCaptureCutoff(Position position, short move, int depth, short[] tried, int triedCount) {
            if (!captureHistory) {
                return;
            }
            int bonus = Math.min(depth * depth, HISTORY_MAX);
            applyCaptureGravity(position, move, bonus);
            for (int i = 0; i < triedCount; i++) {
                if (tried[i] != move) {
                    applyCaptureGravity(position, tried[i], -bonus);
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
         * Gravity-bounded update of a {@code short} history cell, keeping the
         * magnitude at or below {@link AlphaBeta#HISTORY_MAX} (well within the
         * {@code short} range) and decaying large values toward zero.
         *
         * @param current current stored value
         * @param delta signed bonus or malus
         * @return updated value
         */
        private static short gravityShort(short current, int delta) {
            int updated = current + delta - current * Math.abs(delta) / HISTORY_MAX;
            return (short) updated;
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
