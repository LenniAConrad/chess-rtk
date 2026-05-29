package chess.engine;



/**
 * Current aspiration-window bounds.
 *
 * @param alpha lower search bound
 * @param beta upper search bound
 * @param width current half-window width in centipawns
 */
record AspirationWindow(int alpha, int beta, int width) {
}
