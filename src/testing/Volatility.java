package testing;


import java.util.List;


/**
 * Depth volatility summary.
 */
record Volatility(
        List<TimelineEntry> timeline,
        int depthSamples,
        String scoreSources,
        int startDepth,
        int finalDepth,
        int startScore,
        int finalScore,
        int minScore,
        int minDepth,
        int maxScore,
        int maxDepth,
        int swing,
        int net,
        int worstAdjacentDelta,
        int signFlips,
        int outcomeChanges,
        int bestMoveChanges,
        int pvChanges,
        int finalBestStableDepth,
        int firstToFinalLcp,
        double avgAdjacentLcp,
        Long finalNodes,
        Long finalTimeMs,
        String direction,
        String severity,
        String startBestMove,
        String finalBestMove,
        String finalSecondBestMove,
        Integer finalSecondScore,
        Integer finalMargin,
        String startPv,
        String finalPv) {

    /**
     * {.
     * @param timeline timeline entries
     * @param depthSamples number of depth samples
     * @param scoreSources score source count
     * @param startDepth starting depth
     * @param finalDepth final depth
     * @param startScore starting score
     * @param finalScore final score
     * @param minScore minimum score
     * @param minDepth depth at the minimum score
     * @param maxScore maximum score
     * @param maxDepth depth at the maximum score
     * @param swing largest score swing
     * @param net net score change
     * @param worstAdjacentDelta largest adjacent score delta
     * @param signFlips number of score sign flips
     * @param outcomeChanges number of outcome changes
     * @param bestMoveChanges number of best-move changes
     * @param pvChanges number of principal-variation changes
     * @param finalBestStableDepth first depth where the final best move stayed stable
     * @param firstToFinalLcp common prefix length between first and final PVs
     * @param avgAdjacentLcp average adjacent principal-variation common prefix length
     * @param finalNodes final searched node count
     * @param finalTimeMs final elapsed time in milliseconds
     * @param direction volatility direction label
     * @param severity volatility severity label
     * @param startBestMove starting best move
     * @param finalBestMove final best move
     * @param finalSecondBestMove final second-best move
     * @param finalSecondScore final second-best score
     * @param finalMargin final score margin
     * @param startPv starting principal variation
     * @param finalPv final principal variation
     */
    Volatility {
        timeline = timeline == null ? List.of() : List.copyOf(timeline);
        scoreSources = scoreSources == null ? "" : scoreSources;
        direction = direction == null ? "missing" : direction;
        severity = severity == null ? "stable" : severity;
        startBestMove = startBestMove == null ? "" : startBestMove;
        finalBestMove = finalBestMove == null ? "" : finalBestMove;
        finalSecondBestMove = finalSecondBestMove == null ? "" : finalSecondBestMove;
        startPv = startPv == null ? "" : startPv;
        finalPv = finalPv == null ? "" : finalPv;
    }

    /**
     * Empty volatility for records with no usable depth timeline.
     * @return empty result
     */
    static Volatility empty() {
        return new Volatility(
                List.<TimelineEntry>of(),
                0,
                "",
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0,
                0.0,
                null,
                null,
                "missing",
                "stable",
                "",
                "",
                "",
                null,
                null,
                "",
                "");
    }
}
