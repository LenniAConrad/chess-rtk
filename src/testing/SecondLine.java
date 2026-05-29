package testing;




/**
 * Final second-PV summary.
 */
record SecondLine(String bestMove, Integer score, Integer margin) {

    /**
     * Empty second-line summary.
     * @return empty result
     */
    static SecondLine empty() {
        return new SecondLine("", null, null);
    }
}
