package chess.tag.core;

import chess.core.Field;
import chess.core.Piece;

/**
 * Shared helpers for detecting pins against the king.
 */
public final class PinUtils {

    private PinUtils() {
        // utility
    }

    public static boolean isPinnedToKing(byte[] board, boolean pinnedIsWhite, byte kingSquare, byte pieceSquare) {
        return findPinToKing(board, pinnedIsWhite, kingSquare, pieceSquare) != null;
    }

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

    private static boolean isSliderForLine(byte piece, int stepX, int stepY) {
        boolean diagonal = stepX != 0 && stepY != 0;
        if (diagonal) {
            return Piece.isBishop(piece) || Piece.isQueen(piece);
        }
        return Piece.isRook(piece) || Piece.isQueen(piece);
    }

    public static final class PinInfo {
        public final byte pinnerSquare;
        public final byte pinnerPiece;
        public final byte pinnedSquare;

        private PinInfo(byte pinnerSquare, byte pinnerPiece, byte pinnedSquare) {
            this.pinnerSquare = pinnerSquare;
            this.pinnerPiece = pinnerPiece;
            this.pinnedSquare = pinnedSquare;
        }
    }
}
