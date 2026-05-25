package chess.tag;

import static chess.tag.core.Literals.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import chess.core.Field;
import chess.core.Piece;
import chess.core.Position;

/**
 * Tracks bishop-square colors for each side.
 *
 * @author Lennart A. Conrad
 * @since 2026
 */
final class BishopColorState {

    /**
     * Whether White has a bishop on a light square.
     */
    boolean whiteLight;

    /**
     * Whether White has a bishop on a dark square.
     */
    boolean whiteDark;

    /**
     * Whether Black has a bishop on a light square.
     */
    boolean blackLight;

    /**
     * Whether Black has a bishop on a dark square.
     */
    boolean blackDark;

    /**
     * Records a bishop on its square color.
     *
     * @param piece  the bishop piece code
     * @param square the square index
     */
    void mark(byte piece, byte square) {
        boolean light = isLightSquare(square);
        if (Piece.isWhite(piece)) {
            whiteLight |= light;
            whiteDark |= !light;
        } else {
            blackLight |= light;
            blackDark |= !light;
        }
    }

    /**
     * Checks whether opposite-colored bishops are present.
     *
     * @return {@code true} when the bishop colors are opposite across sides
     */
    boolean hasOppositeColors() {
        return (whiteLight && blackDark) || (whiteDark && blackLight);
    }

    /**
     * Determines whether a square is light-colored.
     *
     * @param square the square index
     * @return {@code true} when the square is light-colored
     */
    private static boolean isLightSquare(byte square) {
        int file = Field.getX(square);
        int rank = Field.getY(square);
        return ((file + rank) & 1) == 0;
    }
}
