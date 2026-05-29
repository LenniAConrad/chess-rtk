package chess.engine;



/**
 * Cached BT4 prediction plus canonical transform.
 */
record Bt4Prediction(chess.nn.lc0.bt4.Network.Prediction prediction, int transform) {
}
