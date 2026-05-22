/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package chess.engine;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import chess.core.Move;
import chess.core.MoveList;
import chess.core.Piece;
import chess.core.Position;

/**
 * Small bounded proof search for forced mates from the side to move.
 *
 * <p>
 * This is intentionally narrower than the regular engine search: it only
 * returns a result after proving that one attacking move mates against every
 * legal defender reply within the requested mate distance.
 * </p>
 */
public final class MateProver {

    /**
     * Default proof horizon used by the standalone mate command.
     */
    public static final int DEFAULT_MAX_MATE_MOVES = 4;

    /**
     * Default node cap for the proof search.
     */
    public static final long DEFAULT_NODE_LIMIT = 25_000L;

    /**
     * Failed proof sentinel for memoized positions.
     */
    private static final Line FAIL = new Line(false, new short[0]);

    /**
     * Mate move-ordering score.
     */
    private static final int SCORE_MATE = 1_000_000;

    /**
     * Checking move-ordering score.
     */
    private static final int SCORE_CHECK = 100_000;

    /**
     * Quiet moves that create mate-in-one threats should outrank material grabs.
     */
    private static final int SCORE_MATE_THREAT = 75_000;

    /**
     * Near-promotion pawn pushes are common quiet keys in mate compositions.
     */
    private static final int SCORE_NEAR_PROMOTION = 60_000;

    /**
     * Capture ordering is a proof-search tie-breaker, not the main signal.
     */
    private static final int MATERIAL_ORDER_SCALE = 8;

    /**
     * Utility class.
     */
    private MateProver() {
        // utility
    }

    /**
     * Proves a forced mate using the default horizon and node cap.
     *
     * @param root root position
     * @return proof, or null when no bounded proof was found
     */
    public static Proof proveMate(Position root) {
        return proveMate(root, DEFAULT_MAX_MATE_MOVES, DEFAULT_NODE_LIMIT);
    }

    /**
     * Proves a forced mate from {@code root}'s side to move.
     *
     * @param root root position
     * @param maxMateMoves maximum mate distance in full moves
     * @param maxNodes maximum searched proof nodes, or zero/negative for no cap
     * @return proof, or null when no bounded proof was found
     */
    public static Proof proveMate(Position root, int maxMateMoves, long maxNodes) {
        return search(root, maxMateMoves, maxNodes).proof();
    }

    /**
     * Searches for a forced mate and returns proof-search status even when no
     * proof is found.
     *
     * @param root root position
     * @param maxMateMoves maximum mate distance in full moves
     * @param maxNodes maximum searched proof nodes, or zero/negative for no cap
     * @return search result
     */
    public static SearchResult search(Position root, int maxMateMoves, long maxNodes) {
        return search(root, maxMateMoves, maxNodes, 1);
    }

    /**
     * Searches for a forced mate with optional root-move parallelism.
     *
     * @param root root position
     * @param maxMateMoves maximum mate distance in full moves
     * @param maxNodes maximum searched proof nodes, or zero/negative for no cap
     * @param threads root-move worker count; values below 2 use the serial search
     * @return search result
     */
    public static SearchResult search(Position root, int maxMateMoves, long maxNodes, int threads) {
        if (root == null || maxMateMoves <= 0) {
            return new SearchResult(null, 0L, false);
        }
        if (isDraw(root) || !root.hasLegalMove()) {
            return new SearchResult(null, 0L, false);
        }
        long nodeLimit = maxNodes <= 0L ? Long.MAX_VALUE : maxNodes;
        if (threads <= 1) {
            return searchSerial(root, maxMateMoves, nodeLimit);
        }
        return searchParallel(root, maxMateMoves, nodeLimit, threads);
    }

    /**
     * Runs the original deterministic single-threaded proof search.
     * @param root root position or node
     * @param maxMateMoves max mate moves value
     * @param nodeLimit node limit value
     * @return search serial result
     */
    private static SearchResult searchSerial(Position root, int maxMateMoves, long nodeLimit) {
        NodeBudget budget = new NodeBudget(nodeLimit);
        Search search = new Search(root.copy(), budget);
        int limit = Math.max(1, maxMateMoves);
        for (int mateMoves = 1; mateMoves <= limit && !search.exhausted(); mateMoves++) {
            int plies = mateMoves * 2 - 1;
            Line line = search.proveAttacker(plies);
            if (line != null && line.proved()) {
                short bestMove = line.moves().length == 0 ? Move.NO_MOVE : line.moves()[0];
                if (bestMove != Move.NO_MOVE) {
                    Proof proof = new Proof(bestMove, mateMoves, plies, line.moves(), search.nodes());
                    return new SearchResult(proof, search.nodes(), false);
                }
            }
        }
        return new SearchResult(null, search.nodes(), search.exhausted());
    }

    /**
     * Runs a root-split proof search. Each worker owns its own board and memo,
     * while all workers share one global proof-node budget.
     * @param root root position or node
     * @param maxMateMoves max mate moves value
     * @param nodeLimit node limit value
     * @param threads threads value
     * @return search parallel result
     */
    private static SearchResult searchParallel(Position root, int maxMateMoves, long nodeLimit, int threads) {
        NodeBudget budget = new NodeBudget(nodeLimit);
        Map<Key, Bound> memo = new ConcurrentHashMap<>();
        int limit = Math.max(1, maxMateMoves);
        ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, threads));
        try {
            for (int mateMoves = 1; mateMoves <= limit && !budget.exhausted(); mateMoves++) {
                int plies = mateMoves * 2 - 1;
                SearchResult result = searchParallelDepth(root, mateMoves, plies, budget, memo, executor);
                if (result.found() || result.exhausted()) {
                    return result;
                }
            }
            return new SearchResult(null, budget.nodes(), budget.exhausted());
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Searches one mate distance by distributing root moves across workers.
     * @param root root position or node
     * @param mateMoves mate moves value
     * @param plies ply count
     * @param budget budget value
     * @param memo memo value
     * @param executor executor value
     * @return search parallel depth result
     */
    private static SearchResult searchParallelDepth(
            Position root,
            int mateMoves,
            int plies,
            NodeBudget budget,
            Map<Key, Bound> memo,
            ExecutorService executor) {
        Position orderedRoot = root.copy();
        MoveList legal = orderedRoot.legalMoves();
        MoveOrder ordered = orderedMoves(orderedRoot, legal, true, plies);
        if (ordered.size() == 0) {
            return new SearchResult(null, budget.nodes(), budget.exhausted());
        }

        Attempt first = proveRootMove(root, ordered.move(0), 0, plies, budget, memo);
        if (first.proved()) {
            Proof proof = new Proof(first.move(), mateMoves, plies, first.line().moves(), budget.nodes());
            return new SearchResult(proof, budget.nodes(), false);
        }
        if (budget.exhausted() || ordered.size() == 1) {
            return new SearchResult(null, budget.nodes(), budget.exhausted());
        }

        ExecutorCompletionService<Attempt> completion = new ExecutorCompletionService<>(executor);
        List<Future<Attempt>> futures = new ArrayList<>(ordered.size() - 1);
        boolean[] completed = new boolean[ordered.size()];
        completed[0] = true;
        Attempt best = null;
        for (int i = 1; i < ordered.size(); i++) {
            int index = i;
            short move = ordered.move(i);
            Callable<Attempt> task = () -> proveRootMove(root, move, index, plies, budget, memo);
            futures.add(completion.submit(task));
        }

        int remaining = futures.size();
        try {
            while (remaining > 0) {
                Attempt attempt = completion.take().get();
                remaining--;
                completed[attempt.index()] = true;
                if (attempt.proved() && (best == null || attempt.index() < best.index())) {
                    best = attempt;
                }
                if (best != null && earlierMovesFinished(best.index(), completed)) {
                    cancelAll(futures);
                    Proof proof = new Proof(best.move(), mateMoves, plies, best.line().moves(), budget.nodes());
                    return new SearchResult(proof, budget.nodes(), false);
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            cancelAll(futures);
            return new SearchResult(null, budget.nodes(), true);
        } catch (ExecutionException ex) {
            cancelAll(futures);
            throw new IllegalStateException("parallel mate proof worker failed", ex.getCause());
        }
        return new SearchResult(null, budget.nodes(), budget.exhausted());
    }

    /**
     * Proves one root move in a worker-owned search tree.
     * @param root root position or node
     * @param move move encoded in CRTK move format
     * @param index index value
     * @param plies ply count
     * @param budget budget value
     * @param memo memo value
     * @return prove root move result
     */
    private static Attempt proveRootMove(
            Position root,
            short move,
            int index,
            int plies,
            NodeBudget budget,
            Map<Key, Bound> memo) {
        Search search = new Search(root.copy(), budget, memo);
        Line line = search.proveRootMove(move, plies);
        return new Attempt(index, move, line);
    }

    /**
     * Returns whether every root move before {@code index} has finished.
     * @param index index value
     * @param completed completed value
     * @return earlier moves finished result
     */
    private static boolean earlierMovesFinished(int index, boolean[] completed) {
        for (int i = 0; i < index; i++) {
            if (!completed[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Cancels submitted worker futures.
     * @param futures futures value
     */
    private static void cancelAll(List<? extends Future<?>> futures) {
        for (Future<?> future : futures) {
            future.cancel(true);
        }
    }

    /**
     * Returns whether the position is an automatic draw for proof purposes.
     * @param position chess position
     * @return true when is draw
     */
    private static boolean isDraw(Position position) {
        return position.halfMoveClock() >= 100 || position.isInsufficientMaterial();
    }

    /**
     * Returns a move line with {@code move} prepended.
     * @param move move encoded in CRTK move format
     * @param tail tail value
     * @return prepend result
     */
    private static short[] prepend(short move, short[] tail) {
        short[] out = new short[(tail == null ? 0 : tail.length) + 1];
        out[0] = move;
        if (tail != null && tail.length > 0) {
            System.arraycopy(tail, 0, out, 1, tail.length);
        }
        return out;
    }

    /**
     * Forced mate proof result.
     *
     * @param bestMove proven root move
     * @param mateMoves mate distance in full moves from the root side
     * @param plies mate distance in plies
     * @param principalVariation one worst-case mating line
     * @param nodes proof nodes searched
     */
    public record Proof(
            short bestMove,
            int mateMoves,
            int plies,
            short[] principalVariation,
            long nodes) {

        /**
         * Defensively copies mutable input.
         * @param bestMove best move reported by the engine
         * @param mateMoves mate moves value
         * @param plies ply count
         * @param principalVariation principal variation value
         * @param nodes node count
         */
        public Proof {
            principalVariation = principalVariation == null
                    ? new short[0]
                    : Arrays.copyOf(principalVariation, principalVariation.length);
        }

        /**
         * Returns a defensive copy of the proof line.
         * @return principal variation result
         */
        @Override
        public short[] principalVariation() {
            return Arrays.copyOf(principalVariation, principalVariation.length);
        }
    }

    /**
     * Result of a bounded mate proof search.
     *
     * @param proof proven mate, or null
     * @param nodes proof nodes searched
     * @param exhausted true when the node cap stopped the search before the
     *        horizon was completely disproven
     */
    public record SearchResult(Proof proof, long nodes, boolean exhausted) {

        /**
         * Returns whether a forced mate was proven.
         *
         * @return true when {@link #proof()} is non-null
         */
        public boolean found() {
            return proof != null;
        }
    }

    /**
     * Mutable proof search state.
     */
    private static final class Search {
        /**
         * Root.
         */
        private final Position root;
        /**
         * Budget.
         */
        private final NodeBudget budget;
        /**
         * Memo.
         */
        private final Map<Key, Bound> memo;

        /**
         * Search.
         * @param root root position or node
         * @param budget budget value */
        private Search(Position root, NodeBudget budget) {
            this(root, budget, new HashMap<>());
        }

        /**
         * Search.
         * @param root root position or node
         * @param budget budget value
         * @param memo memo value */
        private Search(Position root, NodeBudget budget, Map<Key, Bound> memo) {
            this.root = root;
            this.budget = budget;
            this.memo = memo;
        }

        /**
         * Nodes.
         * @return nodes result */
        private long nodes() {
            return budget.nodes();
        }

        /**
         * Exhausted.
         * @return exhausted result */
        private boolean exhausted() {
            return budget.exhausted();
        }

        /**
         * Prove attacker.
         * @param plies ply count
         * @return prove attacker result */
        private Line proveAttacker(int plies) {
            return proveAttacker(root, plies);
        }

        /**
         * Prove root move.
         * @param move move encoded in CRTK move format
         * @param plies ply count
         * @return prove root move result */
        private Line proveRootMove(short move, int plies) {
            if (!enter() || plies <= 0 || isDraw(root)) {
                return null;
            }
            Position.State state = new Position.State();
            root.play(move, state);
            try {
                if (root.inCheck() && !root.hasLegalMove()) {
                    return new Line(true, new short[] { move });
                }
                if (plies > 1) {
                    Line tail = proveDefender(root, plies - 1);
                    if (tail != null && tail.proved()) {
                        return new Line(true, prepend(move, tail.moves()));
                    }
                }
                return null;
            } finally {
                root.undo(move, state);
            }
        }

        /**
         * Prove attacker.
         * @param position chess position
         * @param plies ply count
         * @return prove attacker result */
        private Line proveAttacker(Position position, int plies) {
            if (!enter() || plies <= 0 || isDraw(position)) {
                return null;
            }
            Key key = new Key(position.signature(), true);
            Line cached = lookup(key, plies);
            if (cached != null) {
                return cached.proved() ? cached : null;
            }

            MoveList legal = position.legalMoves();
            if (legal.isEmpty()) {
                return null;
            }
            if (plies == 1) {
                Line mate = findMateInOne(position, legal);
                if (mate != null) {
                    recordProved(key, plies, mate);
                    return mate;
                }
                recordFailed(key, plies);
                return null;
            }

            MoveOrder ordered = orderedMoves(position, legal, true, plies);
            Position.State state = new Position.State();
            for (int i = 0; i < ordered.size(); i++) {
                short move = ordered.move(i);
                if (ordered.isMate(i)) {
                    Line proved = new Line(true, new short[] { move });
                    recordProved(key, plies, proved);
                    return proved;
                }
                position.play(move, state);
                try {
                    Line tail = proveDefender(position, plies - 1);
                    if (tail != null && tail.proved()) {
                        Line proved = new Line(true, prepend(move, tail.moves()));
                        recordProved(key, plies, proved);
                        return proved;
                    }
                } finally {
                    position.undo(move, state);
                }
                if (exhausted()) {
                    return null;
                }
            }
            if (!exhausted()) {
                recordFailed(key, plies);
            }
            return null;
        }

        /**
         * Prove defender.
         * @param position chess position
         * @param plies ply count
         * @return prove defender result */
        private Line proveDefender(Position position, int plies) {
            if (!enter() || isDraw(position)) {
                return null;
            }
            Key key = new Key(position.signature(), false);
            Line cached = lookup(key, plies);
            if (cached != null) {
                return cached.proved() ? cached : null;
            }
            MoveList legal = position.legalMoves();
            if (legal.isEmpty()) {
                if (position.inCheck()) {
                    Line proved = new Line(true, new short[0]);
                    recordProved(key, plies, proved);
                    return proved;
                }
                recordFailed(key, plies);
                return null;
            }
            if (plies <= 0) {
                return null;
            }

            short[] worstLine = new short[0];
            MoveOrder ordered = orderedMoves(position, legal, false, plies);
            Position.State state = new Position.State();
            for (int i = 0; i < ordered.size(); i++) {
                short move = ordered.move(i);
                position.play(move, state);
                Line attackerProof;
                try {
                    attackerProof = proveAttacker(position, plies - 1);
                } finally {
                    position.undo(move, state);
                }
                if (attackerProof == null || !attackerProof.proved()) {
                    if (!exhausted()) {
                        recordFailed(key, plies);
                    }
                    return null;
                }
                short[] line = prepend(move, attackerProof.moves());
                if (line.length > worstLine.length) {
                    worstLine = line;
                }
                if (exhausted()) {
                    return null;
                }
            }
            Line proved = new Line(true, worstLine);
            recordProved(key, plies, proved);
            return proved;
        }

        /**
         * Find mate in one.
         * @param position chess position
         * @param legal legal moves
         * @return find mate in one result */
        private Line findMateInOne(Position position, MoveList legal) {
            short bestMove = bestMateInOneMove(position, legal);
            return bestMove == Move.NO_MOVE ? null : new Line(true, new short[] { bestMove });
        }

        /**
         * Lookup.
         * @param key lookup key
         * @param plies ply count
         * @return lookup result */
        private Line lookup(Key key, int plies) {
            Bound bound = memo.get(key);
            if (bound == null) {
                return null;
            }
            synchronized (bound) {
                if (bound.provedLine != null && bound.provedPlies <= plies) {
                    return bound.provedLine;
                }
                if (bound.failedPlies >= plies) {
                    return FAIL;
                }
            }
            return null;
        }

        /**
         * Record proved.
         * @param key lookup key
         * @param plies ply count
         * @param line line text */
        private void recordProved(Key key, int plies, Line line) {
            Bound bound = memo.computeIfAbsent(key, ignored -> new Bound());
            synchronized (bound) {
                if (bound.provedLine == null || plies < bound.provedPlies) {
                    bound.provedPlies = plies;
                    bound.provedLine = line;
                }
            }
        }

        /**
         * Record failed.
         * @param key lookup key
         * @param plies ply count */
        private void recordFailed(Key key, int plies) {
            Bound bound = memo.computeIfAbsent(key, ignored -> new Bound());
            synchronized (bound) {
                if (plies > bound.failedPlies) {
                    bound.failedPlies = plies;
                }
            }
        }

        /**
         * Enter.
         * @return enter result */
        private boolean enter() {
            return budget.enter();
        }
    }

    /**
     * Shared node budget for serial and parallel proof searches.
     */
    private static final class NodeBudget {
        /**
         * Limit.
         */
        private final long limit;
        /**
         * Atomic long.
         */
        private final AtomicLong nodes = new AtomicLong();
        /**
         * Exhausted.
         */
        private volatile boolean exhausted;

        /**
         * Node budget.
         * @param limit limit value */
        private NodeBudget(long limit) {
            this.limit = limit;
        }

        /**
         * Enter.
         * @return enter result */
        private boolean enter() {
            if (Thread.currentThread().isInterrupted()) {
                return false;
            }
            while (true) {
                long current = nodes.get();
                if (current >= limit) {
                    exhausted = true;
                    return false;
                }
                if (nodes.compareAndSet(current, current + 1L)) {
                    return true;
                }
            }
        }

        /**
         * Nodes.
         * @return nodes result */
        private long nodes() {
            return nodes.get();
        }

        /**
         * Exhausted.
         * @return exhausted result */
        private boolean exhausted() {
            return exhausted;
        }
    }

    /**
     * Sorts legal moves so useful proof candidates are tried first.
     * @param position chess position
     * @param legal legal moves
     * @param attacker attacker value
     * @param plies ply count
     * @return ordered moves result
     */
    private static MoveOrder orderedMoves(Position position, MoveList legal, boolean attacker, int plies) {
        MoveOrder order = new MoveOrder(legal.size());
        Position.State state = new Position.State();
        for (int i = 0; i < legal.size(); i++) {
            short move = legal.raw(i);
            int score = tacticalScore(position, move, attacker);
            boolean mate = false;
            position.play(move, state);
            try {
                if (position.inCheck()) {
                    int replyCount = position.legalMoveCount();
                    score += SCORE_CHECK - replyCount * 256;
                    mate = replyCount == 0;
                    if (mate) {
                        score += SCORE_MATE;
                    }
                } else if (attacker && plies >= 3 && hasMateInOneAfterNull(position)) {
                    score += SCORE_MATE_THREAT;
                }
            } finally {
                position.undo(move, state);
            }
            if (!attacker && mate) {
                score += SCORE_MATE;
            }
            order.add(move, score, mate);
        }
        order.sort();
        return order;
    }

    /**
     * Returns the highest-priority mate-in-one move in a legal move list.
     * @param position chess position
     * @param legal legal moves
     * @return best mate in one move result
     */
    private static short bestMateInOneMove(Position position, MoveList legal) {
        short bestMove = Move.NO_MOVE;
        int bestScore = Integer.MIN_VALUE;
        Position.State state = new Position.State();
        for (int i = 0; i < legal.size(); i++) {
            short move = legal.raw(i);
            int score = tacticalScore(position, move, true);
            position.play(move, state);
            try {
                if (position.inCheck() && !position.hasLegalMove()) {
                    score += SCORE_MATE + SCORE_CHECK;
                    if (bestMove == Move.NO_MOVE || MoveOrder.comesBefore(move, score, bestMove, bestScore)) {
                        bestMove = move;
                        bestScore = score;
                    }
                }
            } finally {
                position.undo(move, state);
            }
        }
        return bestMove;
    }

    /**
     * Returns whether the attacker would have mate in one if the defender passed.
     * @param position chess position
     * @return true when has mate in one after null
     */
    private static boolean hasMateInOneAfterNull(Position position) {
        Position.State nullState = new Position.State();
        position.playNull(nullState);
        try {
            return bestMateInOneMove(position, position.legalMoves()) != Move.NO_MOVE;
        } finally {
            position.undoNull(nullState);
        }
    }

    /**
     * Scores static tactical move features without making the move.
     * @param position chess position
     * @param move move encoded in CRTK move format
     * @param attacker attacker value
     * @return tactical score result
     */
    private static int tacticalScore(Position position, short move, boolean attacker) {
        int score = 0;
        int captured = position.capturedPiece(move);
        if (captured >= 0) {
            int victim = internalPieceValue(captured);
            int attackerPiece = internalPieceValue(position.movingPiece(move));
            score += victim * MATERIAL_ORDER_SCALE - attackerPiece;
        }
        if (position.isPromotion(move)) {
            score += promotionValue(Move.getPromotion(move)) * MATERIAL_ORDER_SCALE;
        }
        if (attacker && isNearPromotionPawnPush(position, move)) {
            score += SCORE_NEAR_PROMOTION;
        }
        if (!attacker && position.movingPiece(move) == (position.isWhiteToMove()
                ? Position.WHITE_KING
                : Position.BLACK_KING)) {
            score += 50;
        }
        return score;
    }

    /**
     * Returns whether a pawn moves onto the seventh rank without promoting yet.
     * @param position chess position
     * @param move move encoded in CRTK move format
     * @return true when is near promotion pawn push
     */
    private static boolean isNearPromotionPawnPush(Position position, short move) {
        int moving = position.movingPiece(move);
        int toRank = Move.getToY(move);
        return (moving == Position.WHITE_PAWN && toRank == 6)
                || (moving == Position.BLACK_PAWN && toRank == 1);
    }

    /**
     * Material value for an internal {@link Position} piece index.
     * @param pieceIndex piece index value
     * @return internal piece value result
     */
    private static int internalPieceValue(int pieceIndex) {
        return switch (pieceIndex) {
            case Position.WHITE_PAWN, Position.BLACK_PAWN -> Piece.VALUE_PAWN;
            case Position.WHITE_KNIGHT, Position.BLACK_KNIGHT -> Piece.VALUE_KNIGHT;
            case Position.WHITE_BISHOP, Position.BLACK_BISHOP -> Piece.VALUE_BISHOP;
            case Position.WHITE_ROOK, Position.BLACK_ROOK -> Piece.VALUE_ROOK;
            case Position.WHITE_QUEEN, Position.BLACK_QUEEN -> Piece.VALUE_QUEEN;
            case Position.WHITE_KING, Position.BLACK_KING -> Piece.VALUE_KING;
            default -> Piece.VALUE_EMPTY;
        };
    }

    /**
     * Material value for a promotion code.
     * @param promotion promotion value
     * @return promotion value result
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
     * Memo key for one proof subproblem.
     */
    private record Key(long signature, boolean attacker) {
    }

    /**
     * One root-move worker result.
     */
    private record Attempt(int index, short move, Line line) {
        /**
         * Proved.
         * @return proved result */
        private boolean proved() {
            return line != null && line.proved();
        }
    }

    /**
     * Depth-monotonic proof/disproof memo entry.
     */
    private static final class Bound {
        /**
         * Proved plies.
         */
        private int provedPlies = Integer.MAX_VALUE;
        /**
         * Proved line.
         */
        private Line provedLine;
        /**
         * Failed plies.
         */
        private int failedPlies = -1;
    }

    /**
     * Internal proof line.
     */
    private record Line(boolean proved, short[] moves) {
    }

    /**
     * Primitive move-ordering buffer.
     */
    private static final class MoveOrder {
        /**
         * Moves.
         */
        private final short[] moves;
        /**
         * Scores.
         */
        private final int[] scores;
        /**
         * Mates.
         */
        private final boolean[] mates;
        /**
         * Size.
         */
        private int size;

        /**
         * Move order.
         * @param capacity capacity value */
        private MoveOrder(int capacity) {
            this.moves = new short[Math.max(1, capacity)];
            this.scores = new int[this.moves.length];
            this.mates = new boolean[this.moves.length];
        }

        /**
         * Add.
         * @param move move encoded in CRTK move format
         * @param score score value
         * @param mate mate flag */
        private void add(short move, int score, boolean mate) {
            moves[size] = move;
            scores[size] = score;
            mates[size] = mate;
            size++;
        }

        /**
         * Size.
         * @return size result */
        private int size() {
            return size;
        }

        /**
         * Move.
         * @param index index value
         * @return move result */
        private short move(int index) {
            return moves[index];
        }

        /**
         * Is mate.
         * @param index index value
         * @return true when is mate */
        private boolean isMate(int index) {
            return mates[index];
        }

        /**
         * Sort.
         */
        private void sort() {
            for (int i = 1; i < size; i++) {
                short move = moves[i];
                int score = scores[i];
                boolean mate = mates[i];
                int j = i - 1;
                while (j >= 0 && comesBefore(move, score, moves[j], scores[j])) {
                    moves[j + 1] = moves[j];
                    scores[j + 1] = scores[j];
                    mates[j + 1] = mates[j];
                    j--;
                }
                moves[j + 1] = move;
                scores[j + 1] = score;
                mates[j + 1] = mate;
            }
        }

        /**
         * Comes before.
         * @param leftMove left move value
         * @param leftScore left score value
         * @param rightMove right move value
         * @param rightScore right score value
         * @return comes before result */
        private static boolean comesBefore(short leftMove, int leftScore, short rightMove, int rightScore) {
            if (leftScore != rightScore) {
                return leftScore > rightScore;
            }
            return (leftMove & 0xFFFF) < (rightMove & 0xFFFF);
        }
    }
}
