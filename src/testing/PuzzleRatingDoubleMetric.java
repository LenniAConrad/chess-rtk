package testing;



/**
 * Floating-point accessor for difficulty-driver metrics.
 */
interface PuzzleRatingDoubleMetric {
    /**
     * Returns the floating-point metric value for one complexity band.
     *
     * @param band source band
     * @return metric value
     */
    double value(PuzzleRatingComplexityBand band);
}
