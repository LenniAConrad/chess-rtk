package chess.engine;



/**
 * Proven endgame state from the node side-to-move perspective.
 */
enum ProofState {
    /**
     * U n k n o w n,.
     */
    UNKNOWN,
    /**
     * W i n,.
     */
    WIN,
    /**
     * D r a w,.
     */
    DRAW,
    /**
     * L o s s.
     */
    LOSS
}
