package chess.engine;


import chess.core.Position;

/**
 * Maps one legal move to a policy-logit index, used by {@link Mcts}.
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
