package chess.eval;

import chess.core.Position;

/**
 * Static centipawn evaluator used by {@link chess.engine.AlphaBeta}.
 *
 * <p>
 * Implementations return centipawns from the side-to-move perspective. Search
 * handles terminal nodes separately, so evaluators can focus on non-terminal
 * static positions.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
@FunctionalInterface
public interface CentipawnEvaluator extends AutoCloseable {

    /**
     * Optional evaluator-owned per-search state.
     *
     * <p>
     * {@link chess.engine.AlphaBeta} searchers can use this hook to keep
     * incremental evaluation state aligned
     * with their make/undo recursion. Implementations that do not support
     * incremental updates can simply return {@code null} from
     * {@link #openSearchState(Position, int)}.
     * </p>
     */
    interface SearchState extends AutoCloseable {

        /**
         * Notifies the evaluator that a normal move has just been played.
         *
         * @param position child position after the move
         * @param move encoded move that was played
         * @param state undo state filled by the move application
         * @param ply child ply from the root
         */
        void movePlayed(Position position, short move, Position.State state, int ply);

        /**
         * Notifies the evaluator that a null move has just been played.
         *
         * @param ply child ply from the root
         */
        default void nullMovePlayed(int ply) {
            // default incremental state does not need null-move handling
        }

        /**
         * Evaluates the current position using the evaluator-owned search state.
         *
         * @param position current position
         * @param ply current ply from the root
         * @return centipawns from the side-to-move perspective
         */
        int evaluate(Position position, int ply);

        /**
         * Releases per-search resources.
         */
        @Override
        default void close() {
            // default search state has no resources
        }
    }

    /**
     * Evaluates one position.
     *
     * @param position position to evaluate
     * @return centipawns from the side-to-move perspective
     */
    int evaluate(Position position);

    /**
     * Lets an evaluator precompute move-ordering side data for a position.
     *
     * <p>
     * Search uses this sparingly, mainly where a position may be ordered before a
     * normal {@link #evaluate(Position)} call would have populated any evaluator
     * side caches. The default implementation is a no-op.
     * </p>
     *
     * @param position position whose legal moves will be ordered soon
     */
    default void prepareMoveOrdering(Position position) {
        // default evaluator does not need priming
    }

    /**
     * Opens optional evaluator-owned per-search state.
     *
     * @param root root position at ply 0
     * @param searchPlies maximum ply count the search may reach
     * @return incremental search state, or {@code null} when unsupported
     */
    default SearchState openSearchState(Position root, int searchPlies) {
        return null;
    }

    /**
     * Adds evaluator-specific move-ordering bonuses in place.
     *
     * <p>
     * Implementations may use already-cached side data from a recent
     * {@link #evaluate(Position)} call on the same position. The default
     * implementation leaves the searcher's generic ordering scores unchanged.
     * </p>
     *
     * @param position position whose legal moves are being ordered
     * @param moves legal moves aligned with {@code scores}
     * @param scores mutable ordering scores to adjust in place
     */
    default void scoreMoves(Position position, short[] moves, int[] scores) {
        // default evaluator does not contribute move priors
    }

    /**
     * Returns a human-readable evaluator name.
     *
     * @return evaluator label
     */
    default String name() {
        return getClass().getSimpleName();
    }

    /**
     * Releases evaluator resources.
     */
    @Override
    default void close() {
        // default evaluator has no resources
    }
}
