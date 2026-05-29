package testing;


import java.util.List;


/**
 * Full tree report before aggregation.
 */
record TreeReport(List<NodeReport> nodes, int branchPoints, boolean truncated) {
}
