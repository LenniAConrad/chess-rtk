package application.gui.workbench.mcts;

import chess.classical.Wdl;
import chess.core.Position;
import chess.eval.CentipawnEvaluator;
import chess.eval.Classical;

/**
 * Cheap always-available classical WDL backend.
 */
final class ClassicalSearchBackend implements SearchBackend {

    /**
     * Classical centipawn evaluator.
     */
    private final CentipawnEvaluator evaluator = new Classical();

    @Override
    public SearchEvaluation evaluate(Position position) {
        return SearchEvaluation.fromWdl(Wdl.evaluate(position, false));
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
        return "classical";
    }

    /**
     * Releases evaluator resources.
     */
    @Override
    public void close() {
        evaluator.close();
    }
}
