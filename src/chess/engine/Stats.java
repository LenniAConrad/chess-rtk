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
     * Win sum.
     */
    double winSum;
    /**
     * Draw sum.
     */
    double drawSum;
    /**
     * Loss sum.
     */
    double lossSum;

    /**
     * Q.
     * @return q result
     */
    double q() {
        int totalVisits = visits + virtualVisits;
        return totalVisits == 0 ? 0.0 : (valueSum - virtualLossSum) / totalVisits;
    }
}
