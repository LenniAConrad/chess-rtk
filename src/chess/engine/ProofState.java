package chess.engine;



/**
 * Proven endgame state from the node side-to-move perspective.
 */
enum ProofState {
    /**
     * Not yet proven.
     */
    UNKNOWN,
    /**
     * Proven win for the side to move.
     */
    WIN,
    /**
     * Proven draw.
     */
    DRAW,
    /**
     * Proven loss for the side to move.
     */
    LOSS
}
