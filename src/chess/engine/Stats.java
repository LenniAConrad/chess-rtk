package chess.engine;



/**
 * Shared MCTS stats for one hashed position.
 */
final class Stats {
    /**
     * Visits.
     */
    int visits;
    /**
     * Virtual visits.
     */
    int virtualVisits;
    /**
     * Value sum.
     */
    double valueSum;
    /**
     * Virtual loss sum.
     */
    double virtualLossSum;

    /**
     * Q.
     * @return q
     */
    double q() {
        int totalVisits = visits + virtualVisits;
        // Virtual loss biases this node toward a WIN from its own side-to-move
        // perspective, so the selecting parent (which scores children by -q) sees
        // an in-flight node as least attractive and diversifies to other lines.
        // virtualLossSum is zero on the default single-thread, batch-1 path, so q
        // is unchanged there.
        return totalVisits == 0 ? 0.0 : (valueSum + virtualLossSum) / totalVisits;
    }
}
