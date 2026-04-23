package chess.eval;

import chess.classical.Wdl;
import chess.core.Position;

/**
 * Classical handcrafted centipawn evaluator backed by {@link Wdl}.
 *
 * <p>
 * This evaluator has no external model dependency and is the default fallback
 * for the built-in searcher. It exposes the existing CRTK WDL heuristic as a
 * side-to-move centipawn score suitable for alpha-beta leaf evaluation.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Classical implements CentipawnEvaluator {

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
     * @return stable label used in engine output
     */
    @Override
    public String name() {
        return Kind.CLASSICAL.label();
    }
}
