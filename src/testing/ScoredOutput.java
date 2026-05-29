package testing;




/**
 * Signed score plus data source label.
 */
record ScoredOutput(int score, String source, String rawEval, String rawWdl) {
    /**
     * {.
     * @param score centipawn score
     * @param source score source
     * @param rawEval raw evaluation text
     * @param rawWdl raw WDL text
     */
    ScoredOutput {
        source = source == null || source.isBlank() ? "unknown" : source;
        rawEval = rawEval == null ? "" : rawEval;
        rawWdl = rawWdl == null ? "" : rawWdl;
    }
}
