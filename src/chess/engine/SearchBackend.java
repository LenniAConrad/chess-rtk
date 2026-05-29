package chess.engine;

import java.util.ArrayList;
import java.util.List;

import chess.core.Position;

/**
 * Policy/value backend used by MCTS.
 */
interface SearchBackend extends AutoCloseable {

    /**
     * Evaluates one non-terminal position from the side-to-move perspective.
     * @param position chess position
     * @return evaluation score
     */
    Evaluation evaluate(Position position);

    /**
     * Evaluates non-terminal positions as one backend batch.
     * @param positions positions value
     * @return evaluate batch result
     */
    default List<Evaluation> evaluateBatch(List<Position> positions) {
        List<Evaluation> out = new ArrayList<>(positions.size());
        for (Position position : positions) {
            out.add(evaluate(position));
        }
        return out;
    }

    /**
     * Lets the backend prime move-ordering side data.
     * @param position chess position
     */
    default void prepareMoveOrdering(Position position) {
        // default backend has no side data
    }

    /**
     * Adds backend-specific move-prior scores.
     * @param position chess position
     * @param moves candidate moves
     * @param scores score values
     */
    default void scoreMoves(Position position, short[] moves, int[] scores) {
        // default backend does not alter handcrafted priors
    }

    /**
     * Replaces fallback priors when direct policy logits are available.
     * @param position chess position
     * @param moves candidate moves
     * @param fallback fallback value
     * @return priors result
     */
    default double[] priors(Position position, short[] moves, double[] fallback) {
        return fallback;
    }

    /**
     * Returns the backend name.
     * @return name result
     */
    String name();

    /**
     * Returns whether backend calls are safe from multiple worker threads.
     * @return thread safe result
     */
    default boolean threadSafe() {
        return false;
    }

    /**
     * Releases backend resources.
     */
    @Override
    default void close() {
        // default backend has no resources
    }
}
