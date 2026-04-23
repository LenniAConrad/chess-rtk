package chess.engine;

import chess.classical.Wdl;
import chess.core.Position;

/**
 * Classical handcrafted evaluator backed by {@link Wdl}.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class ClassicalEvaluator implements PositionEvaluator {

    /**
     * Evaluates one position.
     *
     * @param position position to evaluate
     * @return centipawns from the side-to-move perspective
     */
    @Override
    public int evaluate(Position position) {
        return Wdl.evaluateStmCentipawns(position);
    }

    /**
     * Returns the evaluator label.
     *
     * @return label
     */
    @Override
    public String name() {
        return EvaluatorKind.CLASSICAL.label();
    }
}
