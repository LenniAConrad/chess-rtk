package chess.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import chess.classical.Wdl;
import chess.core.Position;
import chess.eval.CentipawnEvaluator;
import chess.eval.Classical;

/**
 * Centipawn-evaluator backend.
 */
final class EvaluatorBackend implements SearchBackend {
    /**
     * Evaluator.
     */
    final CentipawnEvaluator evaluator;

    /**
     * Evaluator backend.
     * @param evaluator evaluator value */
    EvaluatorBackend(CentipawnEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    @Override
    public Evaluation evaluate(Position position) {
        if (evaluator instanceof Classical) {
            return Evaluation.fromWdl(Wdl.evaluate(position, false));
        }
        return Evaluation.fromCentipawns(evaluator.evaluate(position));
    }

    @Override
    public void prepareMoveOrdering(Position position) {
        evaluator.prepareMoveOrdering(position);
    }

    @Override
    public void scoreMoves(Position position, short[] moves, int[] scores) {
        evaluator.scoreMoves(position, moves, scores);
    }

    @Override
    public String name() {
        return evaluator.name();
    }

    @Override
    public boolean threadSafe() {
        return evaluator instanceof Classical;
    }

    @Override
    public void close() {
        evaluator.close();
    }
}
