package application.gui.workbench.mcts;

import chess.core.Position;

/**
 * Policy/value backend used by MCTS.
 */
interface SearchBackend extends AutoCloseable {

    /**
     * Evaluates one non-terminal position from the side-to-move perspective.
     *
     * @param position position to evaluate
     * @return structured evaluation
     */
    SearchEvaluation evaluate(Position position);

    /**
     * Lets the backend prime move-ordering side data.
     *
     * @param position position whose moves will be ordered
     */
    default void prepareMoveOrdering(Position position) {
        // default backend has no side data
    }

    /**
     * Adds backend-specific move-prior scores.
     *
     * @param position position to inspect
     * @param moves moves to score
     * @param scores mutable score array
     */
    default void scoreMoves(Position position, short[] moves, int[] scores) {
        // default backend does not alter handcrafted priors
    }

    /**
     * Replaces or blends fallback priors.
     *
     * @param position position to inspect
     * @param moves moves to score
     * @param fallback fallback priors
     * @return backend priors
     */
    default double[] priors(Position position, short[] moves, double[] fallback) {
        return fallback;
    }

    /**
     * Returns a compact backend name for UI status text.
     *
     * @return backend name
     */
    default String name() {
        return "backend";
    }

    /**
     * Releases backend resources.
     */
    @Override
    default void close() {
        // default backend has no resources
    }
}
