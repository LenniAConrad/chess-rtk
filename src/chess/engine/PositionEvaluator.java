package chess.engine;

import chess.core.Position;

/**
 * Static position evaluator used by {@link Searcher}.
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
public interface PositionEvaluator extends AutoCloseable {

    /**
     * Evaluates one position.
     *
     * @param position position to evaluate
     * @return centipawns from the side-to-move perspective
     */
    int evaluate(Position position);

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
