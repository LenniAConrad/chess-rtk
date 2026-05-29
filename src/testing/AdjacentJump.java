package testing;




/**
 * Largest adjacent-depth score jump for one changed node.
 */
record AdjacentJump(
        int rootRank,
        RootReport root,
        NodeReport node,
        int delta,
        TimelineEntry from,
        TimelineEntry to) {
}
