package chess.engine;


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
     * @param evaluator position evaluator
     */
    EvaluatorBackend(CentipawnEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Evaluation evaluate(Position position) {
        if (evaluator instanceof Classical) {
            return Evaluation.fromWdl(Wdl.evaluate(position, false));
        }
        return Evaluation.fromCentipawns(evaluator.evaluate(position));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void prepareMoveOrdering(Position position) {
        evaluator.prepareMoveOrdering(position);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void scoreMoves(Position position, short[] moves, int[] scores) {
        evaluator.scoreMoves(position, moves, scores);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String name() {
        return evaluator.name();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean threadSafe() {
        return evaluator instanceof Classical;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        evaluator.close();
    }
}
