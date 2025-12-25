package chess.tag;

import chess.core.Field;
import chess.core.Piece;

/**
 * Shared formatting helpers for {@code chess.tag}.
 *
 * <p>
 * Tag generators should use these helpers to keep wording consistent and avoid
 * duplicating small formatting methods across classes.
 * </p>
 *
 * @since 2025
 * @author Lennart A. Conrad
 */
final class Text {

    /**
     * Prevents instantiation; this class exposes only static helpers.
     */
    private Text() {
        // utility class
    }

    /**
     * Returns the algebraic square name in lowercase (e.g. {@code "e4"}).
     *
     * @param square board index
     * @return lowercase square name
     */
    static String squareNameLower(byte square) {
        return "" + Field.getFile(square) + Field.getRank(square);
    }

    /**
     * Returns the lowercase, human-readable piece name for {@code piece}.
     *
     * @param piece piece code
     * @return name such as {@code "knight"} or {@code "queen"}
     */
    static String pieceNameLower(byte piece) {
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
     * Returns the lowercase color name implied by {@code piece}.
     *
     * @param piece piece code
     * @return {@code "white"} for white pieces, otherwise {@code "black"}
     */
    static String colorNameLower(byte piece) {
        return Piece.isWhite(piece) ? "white" : "black";
    }
}
