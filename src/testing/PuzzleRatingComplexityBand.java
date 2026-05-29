package testing;



/**
 * Difficulty-band aggregate used by the driver chart.
 */
record PuzzleRatingComplexityBand(
        String name,
        String shortName,
        int count,
        double avgLegal,
        double avgRank,
        double avgPlies,
        double avgNodes,
        double branchShare) {
}
