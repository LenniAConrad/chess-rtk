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
 * Policy/value backend implementations for {@link Mcts}.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
/**
 * Maps one legal move to a policy-logit index.
 */
@FunctionalInterface
interface PolicyIndex {
    /**
     * Returns the backend policy index for one legal move.
     *
     * @param position source position
     * @param move legal move
     * @return policy index, or negative when unmapped
     */
    int index(Position position, short move);
}
