package chess.tag.move;

import static chess.tag.core.Literals.*;


import chess.core.SAN;

/**
 * Builds move-sequence tags for a list of consecutive positions.
 * <p>
 * The sequence tags describe the local line of play as well as enabling and
 * disabling relationships discovered inside each contiguous segment.
 * </p>
 * @author Lennart A. Conrad
 * @since 2026
 */
public final class Sequence {

    /**
     * The maximum number of full moves considered in a sequence segment.
     */
    private static final int MAX_FULL_MOVES = 5;

    /**
     * The maximum number of plies considered in a sequence segment.
     */
    private static final int MAX_PLIES = MAX_FULL_MOVES * 2;

    /**
     * Prevents instantiation of this utility class.
     */
    private Sequence() {
        // utility
    }








    /**
     * Holds the parsed data for one ply in the line.
 * @author Lennart A. Conrad
 * @since 2026
 */
    private static final class Ply {

        /**
         * The encoded move.
         */
        private final short move;

        /**
         * The SAN representation of the move.
         */
        private final String san;

        /**
         * The move number associated with the ply.
         */
        private final int moveNumber;

        /**
         * Whether the ply belongs to White's turn.
         */
        private final boolean whiteToMove;

        /**
         * The source index of the ply in the original position list.
         */
        private final int positionIndex;

        /**
         * Creates a ply snapshot.
         *
         * @param move the encoded move
         * @param san the SAN string
         * @param moveNumber the move number
         * @param whiteToMove whether the ply belongs to White
         * @param positionIndex the source index in the original list
         */
        private Ply(short move, String san, int moveNumber, boolean whiteToMove, int positionIndex) {
            this.move = move;
            this.san = san;
            this.moveNumber = moveNumber;
            this.whiteToMove = whiteToMove;
            this.positionIndex = positionIndex;
        }
    }
}
