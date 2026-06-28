package chess.tag.tactical;


import chess.core.Field;

/**
 * Detects direct attack relationships that produce tactical motifs.
 * <p>
 * The tags include single-piece attacks and forks when one attacker threatens
 * multiple meaningful enemy targets.
 * </p>
 * @author Lennart A. Conrad
 * @since 2026
 */
public final class Attack {

    /**
     * Cached knight targets for each source square.
     */
    private static final byte[][] KNIGHT_TARGETS = Field.getJumps();

    /**
     * Cached king targets for each source square.
     */
    private static final byte[][] KING_TARGETS = Field.getNeighbors();

    /**
     * Cached White pawn capture targets for each source square.
     */
    private static final byte[][] WHITE_PAWN_TARGETS = Field.getPawnCaptureWhite();

    /**
     * Cached Black pawn capture targets for each source square.
     */
    private static final byte[][] BLACK_PAWN_TARGETS = Field.getPawnCaptureBlack();

    /**
     * Cached bishop/queen diagonal rays for each source square.
     */
    private static final byte[][][] DIAGONAL_RAYS = Field.getDiagonals();

    /**
     * Cached rook/queen orthogonal rays for each source square.
     */
    private static final byte[][][] LINE_RAYS = Field.getLines();

    /**
     * Prevents instantiation of this utility class.
     */
    private Attack() {
        // Utility class
    }









    /**
     * Represents one tactically meaningful attacked target.
 * @author Lennart A. Conrad
 * @since 2026
 */
    private static final class AttackTarget {

        /**
         * The target square.
         */
        private final byte square;

        /**
         * The target piece.
         */
        private final byte piece;

        /**
         * Creates an attacked-target record.
         *
         * @param square the target square
         * @param piece the target piece
         */
        private AttackTarget(byte square, byte piece) {
            this.square = square;
            this.piece = piece;
        }
    }
}
