package chess.engine;

import java.util.ArrayList;
import java.util.List;

import chess.classical.Wdl;
import chess.core.Move;
import chess.core.Position;

/**
 * One tree node.
 */
final class Node {
    /**
     * Parent.
     */
    Node parent;
    /**
     * Move.
     */
    short move;
    /**
     * Position.
     */
    final Position position;
    /**
     * Prior.
     */
    final double prior;
    /**
     * Depth.
     */
    int depth;
    /**
     * Key.
     */
    final long key;
    /**
     * Core key.
     */
    final long coreKey;
    /**
     * Stats.
     */
    final Stats stats;
    /**
     * Array list<>.
     */
    final List<Node> children = new ArrayList<>();
    /**
     * Expanded.
     */
    boolean expanded;
    /**
     * Proof.
     */
    ProofState proof = ProofState.UNKNOWN;
    /**
     * Proof plies.
     */
    int proofPlies = Integer.MAX_VALUE;

    /**
     * Node.
     * @param parent parent node
     * @param move move encoded in CRTK move format
     * @param position chess position
     * @param prior prior value
     * @param depth search depth
     * @param key lookup key
     * @param coreKey core key value
     * @param stats statistics data */
    Node(
            Node parent,
            short move,
            Position position,
            double prior,
            int depth,
            long key,
            long coreKey,
            Stats stats) {
        this.parent = parent;
        this.move = move;
        this.position = position;
        this.prior = prior;
        this.depth = depth;
        this.key = key;
        this.coreKey = coreKey;
        this.stats = stats;
    }

    /**
     * Q.
     * @return q result */
    double q() {
        return stats.q();
    }

    /**
     * Visits.
     * @return visits result */
    int visits() {
        return stats.visits;
    }
}
