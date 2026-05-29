package chess.engine;

import java.util.List;

import chess.core.Position;

/**
 * Selected leaf and root-to-leaf path.
 */
record LeafTask(Node node, List<Node> path, Position position) {
}
