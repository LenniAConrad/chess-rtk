package chess.engine;

import java.util.Arrays;

import chess.core.Move;
import chess.core.MoveList;
import chess.core.Piece;
import chess.core.Position;
import chess.eval.CentipawnEvaluator;
import chess.eval.Classical;

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
    private static final int NO_SCORE = Integer.MIN_VALUE;

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
     * Creates a searcher with the classical evaluator.
     */
    public AlphaBeta() {
        this(new Classical());
    }

    /**
     * Creates a searcher with a caller-selected evaluator.
     *
     * @param evaluator static evaluator
     */
    public AlphaBeta(CentipawnEvaluator evaluator) {
        if (evaluator == null) {
            throw new IllegalArgumentException("evaluator == null");
        }
        this.evaluator = evaluator;
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

        try (SearchContext context = new SearchContext(started, limits, evaluator, root)) {
            Result best = staticRootResult(root, legalMoves, context, started);
            if (context.shouldStop()) {
                Result stopped = best.withRuntime(context.nodes, elapsedSince(started), true);
                notifyDepth(listener, stopped);
                return stopped;
            }
            short preferred = best.bestMove();
            for (int depth = 1; depth <= limits.depth(); depth++) {
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
                    return best.withRuntime(context.nodes, elapsedSince(started), true);
                }
            }
            return best.withRuntime(context.nodes, elapsedSince(started), false);
        }
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
        if (isDraw(position)) {
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
        short ttMove = setup.entry() == null ? Move.NO_MOVE : setup.entry().bestMove;
        short[] moves = orderedMoves(position, setup.legalMoves(), Move.NO_MOVE, ttMove, context, ply);
        Position.State state = context.state(ply);
        NegamaxSearchState searchState = new NegamaxSearchState(setup.alpha());
        for (short move : moves) {
            boolean tactical = isTacticalMove(position, move);
            MoveDecision decision = new MoveDecision(
                    setup.staticEval(),
                    searchState.alpha,
                    depth,
                    searchState.searchedMoves,
                    pvNode,
                    setup.inCheck(),
                    tactical,
                    move,
                    ply);
            if (!shouldFutilityPrune(context, decision)) {
                if (shouldLateMovePrune(context, decision)) {
                    continue;
                }
                int score = searchNegamaxMove(context, position, state, depth, beta, pvNode, decision);
                searchState.recordScore(context, ply, move, score);
                if (searchState.alpha >= beta) {
                    recordCutoff(position, context, move, depth, ply);
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
        int childDepth = depth - 1;
        int reduction = lateMoveReduction(context, decision);
        position.play(decision.move(), state);
        try {
            context.movePlayed(position, decision.move(), state, decision.ply() + 1);
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
     */
    private static int lateMoveReduction(SearchContext context, MoveDecision decision) {
        if (decision.pvNode()
                || decision.inCheck()
                || decision.tactical()
                || decision.depth() < 3
                || decision.searchedMoves() < 3
                || context.isKiller(decision.move(), decision.ply())) {
            return 0;
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
            if (!setup.inCheck() && shouldDeltaPrune(position, move, setup.standPat(), currentAlpha, false)) {
                continue;
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
        if (isDraw(position)) {
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
        if (entry == null || entry.depth < depth) {
            return NO_SCORE;
        }
        int score = entry.score;
        if (entry.flag == TT_EXACT
                || (entry.flag == TT_LOWER && score >= beta)
                || (entry.flag == TT_UPPER && score <= alpha)) {
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
        for (short move : moves) {
            if (!context.recordRootFallbackProbe()) {
                break;
            }
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
                break;
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
            score += 100_000 + Piece.getValue(victim) * 10 - Piece.getValue(moving);
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
     * Static centipawn evaluation from side-to-move perspective.
     *
     * @param context search context that owns the evaluation cache
     * @param position position to evaluate statically
     * @return score clamped to the non-mate static range
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
     * Immutable move-decision inputs used by pruning and reduction heuristics.
     *
     * @param staticEval static evaluation of the node
     * @param alpha current alpha bound
     * @param depth remaining depth
     * @param searchedMoves number of moves already searched
     * @param pvNode true for principal-variation nodes
     * @param inCheck true when the side to move is in check
     * @param tactical true for captures, promotions, and en-passant
     * @param move move being considered
     * @param ply current ply from root
     */
    private record MoveDecision(
            int staticEval,
            int alpha,
            int depth,
            int searchedMoves,
            boolean pvNode,
            boolean inCheck,
            boolean tactical,
            short move,
            int ply) {
    }

    /**
     * Prepared negamax node data or an already-resolved score.
     *
     * @param resolved whether the node resolved before move search
     * @param resolvedScore resolved score when {@code resolved} is true
     * @param inCheck true when the side to move is in check
     * @param legalMoves legal moves to search
     * @param staticEval cached static evaluation, or {@link #NO_SCORE}
     * @param key position signature
     * @param entry transposition-table entry
     */
    private record NegamaxSetup(
            boolean resolved,
            int resolvedScore,
            boolean inCheck,
            MoveList legalMoves,
            int alpha,
            int staticEval,
            long key,
            Transposition entry) {

        /**
         * Creates a resolved setup.
         *
         * @param score resolved score
         * @return resolved setup
         */
        private static NegamaxSetup resolved(int score) {
            return new NegamaxSetup(true, score, false, null, NO_SCORE, NO_SCORE, 0L, null);
        }

        /**
         * Creates a prepared setup for normal move search.
         *
         * @param inCheck true when the side to move is in check
         * @param legalMoves legal moves to search
         * @param staticEval cached static evaluation, or {@link #NO_SCORE}
         * @param key position signature
         * @param entry transposition-table entry
         * @return search setup
         */
        private static NegamaxSetup search(
                boolean inCheck,
                MoveList legalMoves,
                int alpha,
                int staticEval,
                long key,
                Transposition entry) {
            return new NegamaxSetup(false, NO_SCORE, inCheck, legalMoves, alpha, staticEval, key, entry);
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
     * Prepared quiescence state or an already-resolved score.
     *
     * @param resolved whether quiescence resolved before move search
     * @param resolvedScore resolved score when {@code resolved} is true
     * @param legalMoves legal moves to search
     * @param inCheck true when the side to move is in check
     * @param standPat stand-pat score, or {@link #NO_SCORE}
     * @param alpha current alpha bound
     */
    private record QuiescenceSetup(
            boolean resolved,
            int resolvedScore,
            MoveList legalMoves,
            boolean inCheck,
            int standPat,
            int alpha) {

        /**
         * Creates a resolved quiescence setup.
         *
         * @param score resolved score
         * @return resolved setup
         */
        private static QuiescenceSetup resolved(int score) {
            return new QuiescenceSetup(true, score, null, false, NO_SCORE, NO_SCORE);
        }

        /**
         * Creates a prepared quiescence setup.
         *
         * @param legalMoves legal moves to search
         * @param inCheck true when the side to move is in check
         * @param standPat stand-pat score, or {@link #NO_SCORE}
         * @param alpha current alpha bound
         * @return search setup
         */
        private static QuiescenceSetup search(MoveList legalMoves, boolean inCheck, int standPat, int alpha) {
            return new QuiescenceSetup(false, NO_SCORE, legalMoves, inCheck, standPat, alpha);
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
         * Static evaluator used when search reaches a quiet leaf.
         */
        private final CentipawnEvaluator evaluator;

        /**
         * Optional evaluator-owned incremental search state.
         */
        private final CentipawnEvaluator.SearchState incrementalState;

        /**
         * Per-search transposition table used for cutoffs and move ordering.
         */
        private final TranspositionTable transpositions = new TranspositionTable(TRANSPOSITION_ENTRIES);

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
         */
        private SearchContext(long started, Limits limits, CentipawnEvaluator evaluator, Position root) {
            this.deadline = computeDeadline(started, limits.maxDurationMillis());
            this.maxNodes = limits.maxNodes() == 0L ? Long.MAX_VALUE : limits.maxNodes();
            this.evaluator = evaluator;
            int searchPlies = limits.depth() + QUIESCENCE_MAX_PLY + 4;
            this.incrementalState = evaluator.openSearchState(root, searchPlies);
            this.pv = new short[searchPlies][searchPlies];
            this.pvLength = new int[searchPlies];
            this.states = new Position.State[searchPlies];
            this.nullStates = new Position.State[searchPlies];
            this.killers = new short[limits.depth() + QUIESCENCE_MAX_PLY + 4][2];
            for (int i = 0; i < searchPlies; i++) {
                states[i] = new Position.State();
                nullStates[i] = new Position.State();
            }
            for (short[] row : killers) {
                Arrays.fill(row, Move.NO_MOVE);
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
     * Mutable transposition-table entry.
     *
     * <p>
     * Entries are reused in-place to avoid allocation during search. Each entry
     * stores one hashed position, the depth and bound type of its score, and the
     * best move used for future ordering.
     * </p>
     */
    private static final class Transposition {

        /**
         * Zobrist-like position signature for the stored entry.
         */
        private long key;

        /**
         * Remaining search depth used to produce the stored score.
         */
        private int depth;

        /**
         * Stored alpha-beta score from the side-to-move perspective.
         */
        private int score;

        /**
         * Bound flag identifying exact, lower-bound, or upper-bound semantics.
         */
        private byte flag;

        /**
         * Best move from the stored node, reused as a move-ordering hint.
         */
        private short bestMove;

        /**
         * Iterative-deepening generation in which this entry was written.
         */
        private int generation;
    }

    /**
     * Fixed-size replacement transposition table.
     *
     * <p>
     * The table is intentionally simple: one entry per bucket, power-of-two
     * indexing, and a depth-aware replacement rule for same-generation entries.
     * </p>
     */
    private static final class TranspositionTable {

        /**
         * Direct-addressed transposition buckets.
         */
        private final Transposition[] entries;

        /**
         * Bit mask used to map mixed signatures into the entry array.
         */
        private final int mask;

        /**
         * Creates a transposition table with direct indexing.
         *
         * @param size power-of-two size
         * @throws IllegalArgumentException if size is not a power of two
         */
        private TranspositionTable(int size) {
            if (Integer.bitCount(size) != 1) {
                throw new IllegalArgumentException("size must be power of two");
            }
            this.entries = new Transposition[size];
            this.mask = size - 1;
        }

        /**
         * Probes a position signature.
         *
         * @param key position signature to look up
         * @return matching table entry, or null when the bucket is empty or holds
         *         another signature
         */
        private Transposition probe(long key) {
            Transposition entry = entries[index(key)];
            return entry != null && entry.key == key ? entry : null;
        }

        /**
         * Returns a stored best move, if any.
         *
         * @param key position signature to look up
         * @return best move or no move
         */
        private short bestMove(long key) {
            Transposition entry = probe(key);
            return entry == null ? Move.NO_MOVE : entry.bestMove;
        }

        /**
         * Stores an entry.
         *
         * @param key position signature for the stored node
         * @param depth search depth
         * @param score alpha-beta score to store
         * @param flag bound flag
         * @param bestMove best move
         * @param generation iterative-deepening generation
         */
        private void store(long key, int depth, int score, byte flag, short bestMove, int generation) {
            int index = index(key);
            Transposition entry = entries[index];
            if (entry == null) {
                entry = new Transposition();
                entries[index] = entry;
            } else if (entry.key != key && entry.depth > depth && entry.generation == generation) {
                return;
            }
            entry.key = key;
            entry.depth = depth;
            entry.score = score;
            entry.flag = flag;
            entry.bestMove = bestMove;
            entry.generation = generation;
        }

        /**
         * Computes an index.
         *
         * @param key position signature to map
         * @return transposition-table index
         */
        private int index(long key) {
            long mixed = key ^ (key >>> 32);
            return (int) mixed & mask;
        }
    }

    /**
     * Mutable static-evaluation cache entry.
     *
     * <p>
     * The cache is direct-mapped and stores only exact static evaluations, so an
     * entry needs only the position signature and the computed score.
     * </p>
     */
    private static final class EvalEntry {

        /**
         * Position signature for the cached static evaluation.
         */
        private long key;

        /**
         * Cached centipawn score from the side-to-move perspective.
         */
        private int score;
    }

    /**
     * Fixed-size direct-mapped evaluation cache.
     *
     * <p>
     * This cache avoids repeatedly invoking expensive neural evaluators on the
     * same signed position during one search.
     * </p>
     */
    private static final class EvalCache {

        /**
         * Direct-addressed evaluation buckets.
         */
        private final EvalEntry[] entries;

        /**
         * Bit mask used to map mixed signatures into the entry array.
         */
        private final int mask;

        /**
         * Creates a direct-mapped evaluation cache.
         *
         * @param size power-of-two size
         * @throws IllegalArgumentException if size is not a power of two
         */
        private EvalCache(int size) {
            if (Integer.bitCount(size) != 1) {
                throw new IllegalArgumentException("size must be power of two");
            }
            this.entries = new EvalEntry[size];
            this.mask = size - 1;
        }

        /**
         * Reads a cached score.
         *
         * @param key position signature to look up
         * @return cached entry, or null when the bucket is empty or holds another
         *         signature
         */
        private EvalEntry get(long key) {
            EvalEntry entry = entries[index(key)];
            return entry != null && entry.key == key ? entry : null;
        }

        /**
         * Stores a score.
         *
         * @param key position signature for the cached evaluation
         * @param score static score to cache
         */
        private void put(long key, int score) {
            int index = index(key);
            EvalEntry entry = entries[index];
            if (entry == null) {
                entry = new EvalEntry();
                entries[index] = entry;
            }
            entry.key = key;
            entry.score = score;
        }

        /**
         * Computes an index.
         *
         * @param key position signature to map
         * @return evaluation-cache index
         */
        private int index(long key) {
            long mixed = key ^ (key >>> 32);
            return (int) mixed & mask;
        }
    }

    /**
     * Current aspiration-window bounds.
     *
     * @param alpha lower search bound
     * @param beta upper search bound
     * @param width current half-window width in centipawns
     */
    private record AspirationWindow(int alpha, int beta, int width) {
    }

    /**
     * Root-search outcome.
     *
     * @param bestMove best move selected at the root
     * @param score root-perspective score for the selected move
     * @param principalVariation principal variation beginning with
     *        {@code bestMove}
     */
    private record RootOutcome(short bestMove, int score, short[] principalVariation) {

        /**
         * Creates an immutable root outcome.
         *
         * @param bestMove best move selected at the root
         * @param score root-perspective score for the selected move
         * @param principalVariation principal variation beginning with
         *        {@code bestMove}
         */
        private RootOutcome {
            principalVariation = principalVariation == null
                    ? new short[0]
                    : Arrays.copyOf(principalVariation, principalVariation.length);
        }

	        /**
	         * Returns a defensive copy of the root principal variation.
	         *
	         * @return principal variation moves
	         */
	        @Override
	        public short[] principalVariation() {
	            return Arrays.copyOf(principalVariation, principalVariation.length);
	        }

	        /**
	         * Compares this outcome with another root-search outcome.
	         *
	         * @param other object to compare
	         * @return true when all scalar fields and principal-variation moves match
	         */
	        @Override
	        public boolean equals(Object other) {
	            return other instanceof RootOutcome that
	                    && bestMove == that.bestMove
                    && score == that.score
	                    && Arrays.equals(principalVariation, that.principalVariation);
	        }

	        /**
	         * Computes a hash over the selected move, score, and principal variation.
	         *
	         * @return outcome hash code
	         */
	        @Override
	        public int hashCode() {
	            int result = Short.hashCode(bestMove);
	            result = 31 * result + Integer.hashCode(score);
            result = 31 * result + Arrays.hashCode(principalVariation);
	            return result;
	        }

	        /**
	         * Formats this outcome for diagnostics.
	         *
	         * @return debug string containing move, score, and principal variation
	         */
	        @Override
	        public String toString() {
	            return "RootOutcome[bestMove="
                    + bestMove
                    + ", score="
                    + score
                    + ", principalVariation="
                    + Arrays.toString(principalVariation)
                    + "]";
        }
    }

    /**
     * Exception used to stop recursive search without stack traces.
     */
    private static final class SearchStopped extends RuntimeException {

        /**
         * Serialization identifier required by {@link RuntimeException}.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Creates the singleton stop exception.
         */
        private SearchStopped() {
            super(null, null, false, false);
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
