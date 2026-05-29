package chess.tag;




/**
 * Captures king-safety properties while folding multiple tokens together.
 *
 * @author Lennart A. Conrad
 * @since 2026
 */
final class KingSafety {

    /**
     * Whether castling status was observed.
     */
    Boolean castled;

    /**
     * Whether the pawn shield appears weakened.
     */
    boolean shieldWeakened;

    /**
     * Whether the king appears exposed.
     */
    boolean exposed;

    /**
     * Whether an open file is near the king.
     */
    boolean openFile;
}
