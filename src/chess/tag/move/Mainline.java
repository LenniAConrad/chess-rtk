package chess.tag.move;

import static chess.tag.core.Literals.*;


import chess.core.SAN;

/**
 * Produces tags that describe the main continuation from engine analysis.
 * <p>
 * The output includes the best continuation line and additional tags showing
 * enabling and disabling relationships between plies in the sequence.
 * </p>
 * @author Lennart A. Conrad
 * @since 2026
 */
public final class Mainline {

    /**
     * The maximum number of full moves included in the main line output.
     */
    private static final int MAX_FULL_MOVES = 5;

    /**
     * The maximum number of plies included in the main line output.
     */
    private static final int MAX_PLIES = MAX_FULL_MOVES * 2;

    /**
     * Prevents instantiation of this utility class.
     */
    private Mainline() {
        // utility
    }







    /**
     * Holds the SAN and turn context for a single ply in the line.
 * @author Lennart A. Conrad
 * @since 2026
 */
    private static final class Ply {

        /**
         * The encoded move.
         */
        private final short move;

        /**
         * The SAN string for the move.
         */
        private final String san;

        /**
         * The move number at which the ply occurs.
         */
        private final int moveNumber;

        /**
         * Whether the ply belongs to White's turn.
         */
        private final boolean whiteToMove;

        /**
         * Creates a ply snapshot.
         *
         * @param move the encoded move
         * @param san the SAN string
         * @param moveNumber the move number
         * @param whiteToMove whether the ply belongs to White
         */
        private Ply(short move, String san, int moveNumber, boolean whiteToMove) {
            this.move = move;
            this.san = san;
            this.moveNumber = moveNumber;
            this.whiteToMove = whiteToMove;
        }
    }
}
