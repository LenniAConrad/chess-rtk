package chess.tag.core;

import chess.core.Field;
import chess.core.Piece;

/**
 * Finds pins against a king along rank, file, and diagonal lines.
 * <p>
 * The helpers detect whether a piece is pinned to its king and provide the
 * location of the pinner when a pin exists.
 * </p>
 * @author Lennart A. Conrad
 * @since 2026
 */
public final class PinUtils {

    /**
     * Prevents instantiation of this utility class.
     */
    private PinUtils() {
        // utility
    }

    /**
     * Checks whether a piece is pinned to its king.
     *
     * @param board the board array
     * @param pinnedIsWhite whether the pinned piece belongs to White
     * @param kingSquare the pinned side's king square
     * @param pieceSquare the candidate pinned square
     * @return {@code true} when the piece is pinned to the king
     */
    public static boolean isPinnedToKing(byte[] board, boolean pinnedIsWhite, byte kingSquare, byte pieceSquare) {
        return findPinToKing(board, pinnedIsWhite, kingSquare, pieceSquare) != null;
    }

    /**
     * Finds the pin information for a piece pinned to its king.
     *
     * @param board the board array
     * @param pinnedIsWhite whether the pinned piece belongs to White
     * @param kingSquare the pinned side's king square
     * @param pieceSquare the candidate pinned square
     * @return the pin information, or {@code null} when no pin exists
     */
    public static PinInfo findPinToKing(byte[] board, boolean pinnedIsWhite, byte kingSquare, byte pieceSquare) {
        if (kingSquare == Field.NO_SQUARE || kingSquare == pieceSquare) {
            return null;
        }
        int kx = Field.getX(kingSquare);
        int ky = Field.getY(kingSquare);
        int px = Field.getX(pieceSquare);
        int py = Field.getY(pieceSquare);
        int diffX = px - kx;
        int diffY = py - ky;
        if (!(diffX == 0 || diffY == 0 || Math.abs(diffX) == Math.abs(diffY))) {
            return null;
        }
        int stepX = Integer.signum(diffX);
        int stepY = Integer.signum(diffY);
        int x = kx + stepX;
        int y = ky + stepY;
        while (x != px || y != py) {
            if (board[Field.toIndex(x, y)] != Piece.EMPTY) {
                return null;
            }
            x += stepX;
            y += stepY;
        }
        x = px + stepX;
        y = py + stepY;
        while (Field.isOnBoard(x, y)) {
            int idx = Field.toIndex(x, y);
            byte piece = board[idx];
            if (piece != Piece.EMPTY) {
                if (Piece.isWhite(piece) != pinnedIsWhite && isSliderForLine(piece, stepX, stepY)) {
                    return new PinInfo((byte) idx, piece, pieceSquare);
                }
                return null;
            }
            x += stepX;
            y += stepY;
        }
        return null;
    }

    /**
     * Checks whether a slider piece matches the line direction.
     *
     * @param piece the candidate pinner piece
     * @param stepX the file direction
     * @param stepY the rank direction
     * @return {@code true} when the piece can pin along the given line
     */
    private static boolean isSliderForLine(byte piece, int stepX, int stepY) {
        boolean diagonal = stepX != 0 && stepY != 0;
        if (diagonal) {
            return Piece.isBishop(piece) || Piece.isQueen(piece);
        }
        return Piece.isRook(piece) || Piece.isQueen(piece);
    }

    /**
     * Describes a detected pin.
 * @author Lennart A. Conrad
 * @since 2026
     */
    public static final class PinInfo {

        /**
         * The square of the pinning piece.
         */
        public final byte pinnerSquare;

        /**
         * The pinning piece code.
         */
        public final byte pinnerPiece;

        /**
         * The pinned square.
         */
        public final byte pinnedSquare;

        /**
         * Creates pin metadata.
         *
         * @param pinnerSquare the square of the pinning piece
         * @param pinnerPiece the pinning piece code
         * @param pinnedSquare the pinned square
         */
        private PinInfo(byte pinnerSquare, byte pinnerPiece, byte pinnedSquare) {
            this.pinnerSquare = pinnerSquare;
            this.pinnerPiece = pinnerPiece;
            this.pinnedSquare = pinnedSquare;
        }
    }
}
