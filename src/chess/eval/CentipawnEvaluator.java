package chess.eval;

import chess.core.Position;
import chess.engine.search.AlphaBeta;

/**
 * Static centipawn evaluator used by {@link AlphaBeta}.
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
