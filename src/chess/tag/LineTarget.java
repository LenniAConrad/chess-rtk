package chess.tag;




/**
 * Holds one target discovered during a ray scan.
 *
 * @author Lennart A. Conrad
 * @since 2026
 */
final class LineTarget {

    /**
     * The board index.
     */
    final int index;

    /**
     * The piece on the target square.
     */
    final byte piece;

    /**
     * Creates a line target snapshot.
     *
     * @param index the board index
     * @param piece the piece on the target square
     */
    LineTarget(int index, byte piece) {
        this.index = index;
        this.piece = piece;
    }
}
