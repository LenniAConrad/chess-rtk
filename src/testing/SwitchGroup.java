package testing;




/**
 * Severity group used by the PDF driver panel.
 */
record SwitchGroup(
        String name,
        String shortName,
        int count,
        double medianSwing,
        double avgFirstToFinalLcp,
        double bestChangedShare,
        double outcomeChangedShare) {
}
