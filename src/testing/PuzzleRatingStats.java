package testing;



/**
 * Report-wide statistics.
 */
record PuzzleRatingStats(
        double mean,
        double sd,
        double skewness,
        double kurtosis,
        int win,
        int draw,
        double rawMin,
        double rawMax,
        int explicitNodes,
        int branchRows,
        double avgNodes,
        double avgReplies,
        double avgBranches,
        int maxNodes,
        int maxPlies,
        int maxReplies,
        int maxBranches) {
}
