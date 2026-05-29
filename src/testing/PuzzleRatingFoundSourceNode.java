package testing;



/**
 * Internal control-flow signal used to stop record scanning once the root is found.
 */
final class PuzzleRatingFoundSourceNode extends RuntimeException {

    /**
     * Serialization marker for runtime exception compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Found source solution node.
     */
    final PuzzleRatingSourceNode node;

    /**
     * Creates a found-node sentinel.
     *
     * @param node found source node
     */
    PuzzleRatingFoundSourceNode(PuzzleRatingSourceNode node) {
        this.node = node;
    }
}
