package chess.engine;

import java.util.ArrayList;
import java.util.List;

import chess.classical.Wdl;
import chess.core.Move;
import chess.core.Position;

/**
 * Selected leaf and root-to-leaf path.
 */
record LeafTask(Node node, List<Node> path, Position position) {
}
