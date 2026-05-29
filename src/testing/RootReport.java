package testing;


import java.util.List;


/**
 * One root stack report.
 */
record RootReport(
        NodeReport root,
        List<NodeReport> nodes,
        int treeMaxSwing,
        String stackSeverity,
        int treeNodes,
        int treeDepth,
        int treeBranchPoints,
        int treeUnstableNodes,
        int treeReversalNodes,
        int treeBestMoveChanges,
        int treePvChanges,
        String treeWorstFen) {
}
