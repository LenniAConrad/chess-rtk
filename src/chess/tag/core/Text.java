package chess.tag.core;

import chess.core.Field;
import chess.core.Piece;

/**
 * Converts board coordinates and piece codes into lower-case text fragments.
 * <p>
 * These helpers are used when rendering human-readable tag descriptions.
 * </p>
 * @author Lennart A. Conrad
 * @since 2026
 */
public final class Text {

    /**
     * Prevents instantiation of this utility class.
     */
    private Text() {
        // utility class
    }

    /**
     * Returns the lower-case algebraic name for a square.
     *
     * @param square the square to render
     * @return the square name in lower-case algebraic notation
     */
    public static String squareNameLower(byte square) {
        return "" + Field.getFile(square) + Field.getRank(square);
    }

    /**
     * Returns the lower-case piece name for a piece code.
     *
     * @param piece the piece to render
     * @return the piece name in lower-case text
     */
    public static String pieceNameLower(byte piece) {
        if (Piece.isPawn(piece)) {
            return "pawn";
        }
        if (Piece.isKnight(piece)) {
            return "knight";
        }
        if (Piece.isBishop(piece)) {
            return "bishop";
        }
        if (Piece.isRook(piece)) {
            return "rook";
        }
        if (Piece.isQueen(piece)) {
            return "queen";
        }
        return "king";
    }

    /**
     * Returns the lower-case color name for a piece code.
     *
     * @param piece the piece to render
     * @return {@code white} for White pieces, otherwise {@code black}
     */
    public static String colorNameLower(byte piece) {
        return Piece.isWhite(piece) ? "white" : "black";
    }
}
