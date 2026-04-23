package chess.engine;

import java.util.Arrays;

import chess.core.Move;
import chess.core.MoveList;
import chess.core.Piece;
import chess.core.Position;

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
public final class Searcher implements AutoCloseable {

    /**
     * Maximum supported search depth.
     */
    public static final int MAX_DEPTH = 64;

    /**
     * Mate score sentinel.
     */
    public static final int MATE_SCORE = 900_000;

    /**
     * Scores at or beyond this absolute value are treated as mates.
     */
    public static final int MATE_THRESHOLD = MATE_SCORE - 1_000;

    /**
     * Alpha-beta infinity bound.
     */
    private static final int INF = 1_000_000;

    /**
     * Highest non-mate static score returned by the evaluator.
     */
    private static final int MAX_STATIC_SCORE = 100_000;

    /**
     * Maximum quiescence recursion below the nominal search horizon.
     */
    private static final int QUIESCENCE_MAX_PLY = 8;

    /**
     * Default transposition-table size. Must be a power of two.
     */
    private static final int TRANSPOSITION_ENTRIES = 1 << 20;

    /**
     * Default static-evaluation cache size. Must be a power of two.
     */
    private static final int EVAL_CACHE_ENTRIES = 1 << 16;

    /**
     * Exact transposition-table bound.
     */
    private static final byte TT_EXACT = 0;

    /**
     * Lower-bound transposition-table entry.
     */
    private static final byte TT_LOWER = 1;

    /**
     * Upper-bound transposition-table entry.
     */
    private static final byte TT_UPPER = 2;

    /**
     * Shared stop signal used as allocation-free recursive control flow.
     */
    private static final SearchStopped SEARCH_STOPPED = new SearchStopped();

    /**
     * Static evaluator used at leaf nodes.
     */
    private final PositionEvaluator evaluator;

    /**
     * Creates a searcher with the classical evaluator.
     */
    public Searcher() {
        this(new ClassicalEvaluator());
    }

    /**
     * Creates a searcher with a caller-selected evaluator.
     *
     * @param evaluator static evaluator
     */
    public Searcher(PositionEvaluator evaluator) {
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
    public SearchResult search(Position position) {
        return search(position, SearchLimits.defaults());
    }

    /**
     * Searches a position under explicit limits.
     *
     * @param position position to search
     * @param limits resource limits
     * @return search result
     * @throws IllegalArgumentException if an argument is null
     */
    public SearchResult search(Position position, SearchLimits limits) {
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
    public SearchResult search(Position position, SearchLimits limits, SearchListener listener) {
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
            return new SearchResult(
                    Move.NO_MOVE,
                    terminal,
                    0,
                    0L,
                    elapsedSince(started),
                    false,
                    new short[0]);
        }

        SearchContext context = new SearchContext(started, limits, evaluator);
        SearchResult best = staticRootResult(root, legalMoves, context, started);
        if (context.shouldStop()) {
            SearchResult stopped = best.withRuntime(context.nodes, elapsedSince(started), true);
            notifyDepth(listener, stopped);
            return stopped;
        }
        short preferred = best.bestMove();
        for (int depth = 1; depth <= limits.depth(); depth++) {
            try {
                context.newIteration();
                RootOutcome outcome = searchRoot(context, root, legalMoves, depth, preferred);
                preferred = outcome.bestMove();
                best = new SearchResult(
                        outcome.bestMove(),
                        outcome.score(),
                        depth,
                        context.nodes,
                        elapsedSince(started),
                        false,
                        outcome.principalVariation());
                notifyDepth(listener, best);
            } catch (SearchStopped stopped) {
                return best.withRuntime(context.nodes, elapsedSince(started), true);
            }
        }
        return best.withRuntime(context.nodes, elapsedSince(started), false);
    }

    /**
     * Notifies a completed-depth listener.
     *
     * @param listener optional listener
     * @param result completed-depth result
     */
    private static void notifyDepth(SearchListener listener, SearchResult result) {
        if (listener != null) {
            listener.onDepth(result);
        }
    }

    /**
     * Runs one root search iteration.
     *
     * @param context search context
     * @param root mutable root position
     * @param legalMoves legal root moves
     * @param depth target depth
     * @param preferredMove move to order first, or {@link Move#NO_MOVE}
     * @return root outcome
     */
    private static RootOutcome searchRoot(
            SearchContext context,
            Position root,
            MoveList legalMoves,
            int depth,
            short preferredMove) {
        context.clearPv();
        short ttMove = context.transpositions.bestMove(root.signature());
        short[] moves = orderedMoves(root, legalMoves, preferredMove, ttMove, context, 0);
        Position.State state = new Position.State();
        int alpha = -INF;
        int bestScore = -INF;
        short bestMove = Move.NO_MOVE;
        int bestLength = 0;
        short[] bestPv = new short[Math.max(1, depth)];

        for (short move : moves) {
            root.play(move, state);
            int score;
            try {
                score = -negamax(context, root, depth - 1, -INF, -alpha, 1);
            } finally {
                root.undo(move, state);
            }

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
        }
        context.transpositions.store(root.signature(), depth, bestScore, TT_EXACT, bestMove, context.generation);

        return new RootOutcome(bestMove, bestScore, Arrays.copyOf(bestPv, bestLength));
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
     * @return score from current side-to-move perspective
     */
    private static int negamax(
            SearchContext context,
            Position position,
            int depth,
            int alpha,
            int beta,
            int ply) {
        context.visitNode();
        context.pvLength[ply] = 0;

        MoveList legalMoves = terminalMovesIfChecked(position);
        if (legalMoves != null && legalMoves.isEmpty()) {
            return terminalScore(position, ply);
        }
        if (isDraw(position)) {
            return 0;
        }
        if (depth <= 0) {
            return quiescence(context, position, alpha, beta, ply, 0);
        }

        int originalAlpha = alpha;
        long key = position.signature();
        Transposition entry = context.transpositions.probe(key);
        if (entry != null && entry.depth >= depth) {
            int ttScore = entry.score;
            if (entry.flag == TT_EXACT) {
                return ttScore;
            }
            if (entry.flag == TT_LOWER && ttScore >= beta) {
                return ttScore;
            }
            if (entry.flag == TT_UPPER && ttScore <= alpha) {
                return ttScore;
            }
        }

        if (legalMoves == null) {
            legalMoves = position.legalMoves();
        }
        if (legalMoves.isEmpty()) {
            return terminalScore(position, ply);
        }

        short ttMove = entry == null ? Move.NO_MOVE : entry.bestMove;
        short[] moves = orderedMoves(position, legalMoves, Move.NO_MOVE, ttMove, context, ply);
        Position.State state = new Position.State();
        int bestScore = -INF;
        short bestMove = Move.NO_MOVE;
        for (short move : moves) {
            position.play(move, state);
            int score;
            try {
                score = -negamax(context, position, depth - 1, -beta, -alpha, ply + 1);
            } finally {
                position.undo(move, state);
            }

            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
            if (score > alpha) {
                alpha = score;
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
            if (alpha >= beta) {
                if (!isTacticalMove(position, move)) {
                    context.recordQuietCutoff(move, depth, ply);
                }
                break;
            }
        }
        byte flag = bestScore <= originalAlpha ? TT_UPPER : (bestScore >= beta ? TT_LOWER : TT_EXACT);
        if (!isMateScore(bestScore)) {
            context.transpositions.store(key, depth, bestScore, flag, bestMove, context.generation);
        }
        return bestScore;
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

        boolean inCheck = position.inCheck();
        MoveList legalMoves = position.legalMoves();
        if (legalMoves.isEmpty()) {
            return terminalScore(position, ply);
        }
        if (isDraw(position)) {
            return 0;
        }
        if (!inCheck) {
            int standPat = staticScore(context, position);
            if (standPat >= beta) {
                return standPat;
            }
            if (standPat > alpha) {
                alpha = standPat;
            }
            if (qply >= QUIESCENCE_MAX_PLY) {
                return alpha;
            }
        } else if (qply >= QUIESCENCE_MAX_PLY) {
            return staticScore(context, position);
        }

        short[] moves = orderedMoves(position, legalMoves, Move.NO_MOVE, Move.NO_MOVE, context, ply);
        Position.State state = new Position.State();
        for (short move : moves) {
            if (!inCheck && !isTacticalMove(position, move)) {
                continue;
            }
            position.play(move, state);
            int score;
            try {
                score = -quiescence(context, position, -beta, -alpha, ply + 1, qply + 1);
            } finally {
                position.undo(move, state);
            }
            if (score >= beta) {
                return score;
            }
            if (score > alpha) {
                alpha = score;
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
        }
        return alpha;
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
    private static SearchResult staticRootResult(
            Position root,
            MoveList legalMoves,
            SearchContext context,
            long started) {
        short[] moves = orderedMoves(root, legalMoves, Move.NO_MOVE, Move.NO_MOVE, context, 0);
        Position.State state = new Position.State();
        short bestMove = Move.NO_MOVE;
        int bestScore = -INF;
        for (short move : moves) {
            root.play(move, state);
            int score;
            try {
                score = positionScoreAfterRootMove(context, root, 1);
            } finally {
                root.undo(move, state);
            }
            if (score > bestScore || bestMove == Move.NO_MOVE) {
                bestScore = score;
                bestMove = move;
            }
        }
        return new SearchResult(
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
        return -staticScore(context, position);
    }

    /**
     * Orders moves using a deterministic capture/promotion/preferred heuristic.
     *
     * @param position position before the moves
     * @param moveList legal moves
     * @param preferredMove move to order first, or {@link Move#NO_MOVE}
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
            if (context != null && context.isKiller(move, ply)) {
                score += 70_000;
            }
            score += context == null ? 0 : context.historyScore(move);
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
        return Move.getPromotion(move) != 0
                || (position.pieceAt(to) != Piece.EMPTY && Piece.isWhite(position.pieceAt(to)) != position.isWhiteToMove())
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
     * @param position position
     * @return true for halfmove-clock or insufficient-material draws
     */
    private static boolean isDraw(Position position) {
        return position.halfMoveClock() >= 100 || position.isInsufficientMaterial();
    }

    /**
     * Generates legal moves only when needed to distinguish checkmate from draws.
     *
     * @param position position to inspect
     * @return legal moves when the side is in check; {@code null} otherwise
     */
    private static MoveList terminalMovesIfChecked(Position position) {
        if (!position.inCheck()) {
            return null;
        }
        return position.legalMoves();
    }

    /**
     * Static centipawn evaluation from side-to-move perspective.
     *
     * @param position position
     * @return bounded score
     */
    private static int staticScore(SearchContext context, Position position) {
        int score = context.evaluate(position);
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
     * @return mate or draw score
     */
    private static int terminalScore(Position position, int ply) {
        return position.inCheck() ? -MATE_SCORE + ply : 0;
    }

    /**
     * Returns whether a score represents mate.
     *
     * @param score score
     * @return true for mate-score range
     */
    private static boolean isMateScore(int score) {
        return Math.abs(score) >= MATE_THRESHOLD;
    }

    /**
     * Computes elapsed time from a start timestamp.
     *
     * @param started start time
     * @return elapsed milliseconds
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
     * Mutable per-search context.
     */
    private static final class SearchContext {

        /**
         * Absolute deadline, or {@link Long#MAX_VALUE}.
         */
        private final long deadline;

        /**
         * Node limit, or {@link Long#MAX_VALUE}.
         */
        private final long maxNodes;

        /**
         * Static evaluator.
         */
        private final PositionEvaluator evaluator;

        /**
         * Transposition table.
         */
        private final TranspositionTable transpositions = new TranspositionTable(TRANSPOSITION_ENTRIES);

        /**
         * Static evaluation cache.
         */
        private final EvalCache evalCache = new EvalCache(EVAL_CACHE_ENTRIES);

        /**
         * Killer moves by ply.
         */
        private final short[][] killers;

        /**
         * History heuristic table keyed by from/to.
         */
        private final int[] history = new int[64 * 64];

        /**
         * Current transposition-table generation.
         */
        private int generation;

        /**
         * Principal-variation scratch table.
         */
        private final short[][] pv;

        /**
         * Principal-variation lengths by ply.
         */
        private final int[] pvLength;

        /**
         * Visited node count.
         */
        private long nodes;

        /**
         * Creates a search context.
         *
         * @param started start time
         * @param limits search limits
         */
        private SearchContext(long started, SearchLimits limits, PositionEvaluator evaluator) {
            this.deadline = computeDeadline(started, limits.maxDurationMillis());
            this.maxNodes = limits.maxNodes() == 0L ? Long.MAX_VALUE : limits.maxNodes();
            this.evaluator = evaluator;
            int searchPlies = limits.depth() + QUIESCENCE_MAX_PLY + 4;
            this.pv = new short[searchPlies][searchPlies];
            this.pvLength = new int[searchPlies];
            this.killers = new short[limits.depth() + QUIESCENCE_MAX_PLY + 4][2];
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
         * @param position position
         * @return centipawn score
         */
        private int evaluate(Position position) {
            long key = position.signature();
            EvalEntry cached = evalCache.get(key);
            if (cached != null) {
                return cached.score;
            }
            int score = evaluator.evaluate(position);
            evalCache.put(key, score);
            return score;
        }

        /**
         * Clears principal-variation scratch state.
         */
        private void clearPv() {
            Arrays.fill(pvLength, 0);
        }

        /**
         * Records one visited node and checks budgets.
         */
        private void visitNode() {
            nodes++;
            if (shouldStop()) {
                throw SEARCH_STOPPED;
            }
        }

        /**
         * Returns whether a search budget has been exhausted.
         *
         * @return true when node or wall-clock limits are exceeded
         */
        private boolean shouldStop() {
            return nodes > maxNodes || System.currentTimeMillis() >= deadline;
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
         * @param move move
         * @param ply ply
         * @return true when killer
         */
        private boolean isKiller(short move, int ply) {
            return ply < killers.length && (killers[ply][0] == move || killers[ply][1] == move);
        }

        /**
         * Returns the history score for a move.
         *
         * @param move move
         * @return score
         */
        private int historyScore(short move) {
            return history[historyIndex(move)];
        }

        /**
         * Computes history index.
         *
         * @param move move
         * @return table index
         */
        private static int historyIndex(short move) {
            return (Move.getFromIndex(move) << 6) | Move.getToIndex(move);
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
    }

    /**
     * Transposition-table entry.
     */
    private static final class Transposition {

        /**
         * Position signature.
         */
        private long key;

        /**
         * Search depth.
         */
        private int depth;

        /**
         * Stored score.
         */
        private int score;

        /**
         * Bound flag.
         */
        private byte flag;

        /**
         * Best move for ordering.
         */
        private short bestMove;

        /**
         * Iteration generation.
         */
        private int generation;
    }

    /**
     * Fixed-size replacement transposition table.
     */
    private static final class TranspositionTable {

        /**
         * Entries.
         */
        private final Transposition[] entries;

        /**
         * Mask for indexing.
         */
        private final int mask;

        /**
         * Creates a table.
         *
         * @param size power-of-two size
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
         * @param key signature
         * @return matching entry or null
         */
        private Transposition probe(long key) {
            Transposition entry = entries[index(key)];
            return entry != null && entry.key == key ? entry : null;
        }

        /**
         * Returns a stored best move, if any.
         *
         * @param key signature
         * @return best move or no move
         */
        private short bestMove(long key) {
            Transposition entry = probe(key);
            return entry == null ? Move.NO_MOVE : entry.bestMove;
        }

        /**
         * Stores an entry.
         *
         * @param key signature
         * @param depth search depth
         * @param score score
         * @param flag bound flag
         * @param bestMove best move
         * @param generation generation
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
         * @param key signature
         * @return table index
         */
        private int index(long key) {
            long mixed = key ^ (key >>> 32);
            return (int) mixed & mask;
        }
    }

    /**
     * Evaluation cache entry.
     */
    private static final class EvalEntry {

        /**
         * Position signature.
         */
        private long key;

        /**
         * Static score.
         */
        private int score;
    }

    /**
     * Fixed-size direct-mapped evaluation cache.
     */
    private static final class EvalCache {

        /**
         * Entries.
         */
        private final EvalEntry[] entries;

        /**
         * Mask for indexing.
         */
        private final int mask;

        /**
         * Creates a cache.
         *
         * @param size power-of-two size
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
         * @param key signature
         * @return entry or null
         */
        private EvalEntry get(long key) {
            EvalEntry entry = entries[index(key)];
            return entry != null && entry.key == key ? entry : null;
        }

        /**
         * Stores a score.
         *
         * @param key signature
         * @param score score
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
         * @param key signature
         * @return table index
         */
        private int index(long key) {
            long mixed = key ^ (key >>> 32);
            return (int) mixed & mask;
        }
    }

    /**
     * Root-search outcome.
     *
     * @param bestMove best move
     * @param score score
     * @param principalVariation principal variation
     */
    private record RootOutcome(short bestMove, int score, short[] principalVariation) {
    }

    /**
     * Exception used to stop recursive search without stack traces.
     */
    private static final class SearchStopped extends RuntimeException {

        /**
         * Serial version.
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
        void onDepth(SearchResult result);
    }
}
