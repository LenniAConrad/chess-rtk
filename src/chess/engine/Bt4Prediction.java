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
 * Cached BT4 prediction plus canonical transform.
 */
record Bt4Prediction(chess.nn.lc0.bt4.Network.Prediction prediction, int transform) {
}
