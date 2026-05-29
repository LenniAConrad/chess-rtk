package testing;




/**
 * PDF report-wide statistics.
 */
record ReportStats(
        int rootStacks,
        int changedStacks,
        int changedNodes,
        int reversalStacks,
        int minSwing,
        int p10Swing,
        int medianSwing,
        int p90Swing,
        int p99Swing,
        int maxSwing,
        String scoreSources,
        double bestChangedShare,
        double outcomeChangedShare,
        double medianPvPrefix,
        int minFinalDepth,
        int medianFinalDepth,
        int maxFinalDepth) {
}
