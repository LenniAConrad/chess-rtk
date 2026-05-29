package testing;


import java.util.Arrays;


/**
 * One depth timeline point.
 */
record TimelineEntry(
        int depth,
        int score,
        String source,
        String rawEval,
        String rawWdl,
        Outcome outcome,
        short bestMove,
        Long nodes,
        Long timeMs,
        short[] pv) {
    /**
     * {.
     * @param depth search depth
     * @param score centipawn score
     * @param source score source
     * @param rawEval raw evaluation text
     * @param rawWdl raw WDL text
     * @param outcome WDL outcome label
     * @param bestMove best move text
     * @param nodes searched node count
     * @param timeMs elapsed time in milliseconds
     * @param pv principal variation text
     */
    TimelineEntry {
        source = source == null || source.isBlank() ? "unknown" : source;
        rawEval = rawEval == null ? "" : rawEval;
        rawWdl = rawWdl == null ? "" : rawWdl;
        pv = pv == null ? new short[0] : Arrays.copyOf(pv, pv.length);
    }
}
