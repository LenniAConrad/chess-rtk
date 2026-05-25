package chess.classical;

import static chess.classical.Wdl.*;

import chess.core.Bits;
import chess.core.MoveGenerator;
import chess.core.Piece;
import chess.core.Position;

/**
 * Tracks the minor-material counts used for dead-position detection.
 */
final class MinorMaterialState {

    /**
     * White knight count.
     */
    private int whiteKnights;

    /**
     * White bishop count.
     */
    private int whiteBishops;

    /**
     * Black knight count.
     */
    private int blackKnights;

    /**
     * Black bishop count.
     */
    private int blackBishops;

    /**
     * White bishop square color, when present.
     */
    private int whiteBishopColor = -1;

    /**
     * Black bishop square color, when present.
     */
    private int blackBishopColor = -1;

    /**
     * Consumes one board square for material tracking.
     *
     * @param square board square
     * @param piece piece on the square
     * @return true when the position still qualifies for insufficient-material checks
     */
    boolean accept(int square, byte piece) {
        if (piece == Piece.EMPTY || Piece.isKing(piece)) {
            return true;
        }
        if (Piece.isPawn(piece) || Piece.isRook(piece) || Piece.isQueen(piece)) {
            return false;
        }
        if (Piece.isKnight(piece)) {
            if (Piece.isWhite(piece)) {
                whiteKnights++;
            } else {
                blackKnights++;
            }
            return true;
        }
        if (!Piece.isBishop(piece)) {
            return false;
        }
        if (Piece.isWhite(piece)) {
            whiteBishops++;
            whiteBishopColor = bishopSquareColor(square);
        } else {
            blackBishops++;
            blackBishopColor = bishopSquareColor(square);
        }
        return true;
    }

    /**
     * Returns whether the collected minor material is trivially drawn.
     *
     * @return true when no mating material remains
     */
    boolean isInsufficient() {
        int whiteMinors = whiteKnights + whiteBishops;
        int blackMinors = blackKnights + blackBishops;
        return (whiteMinors == 0 && blackMinors == 0)
                || (whiteMinors == 1 && blackMinors == 0)
                || (whiteMinors == 0 && blackMinors == 1)
                || sameColorBishopDraw();
    }

    /**
     * Returns whether both sides only retain same-color bishops.
     *
     * @return true when the position is bishop-only dead material
     */
    private boolean sameColorBishopDraw() {
        return whiteKnights == 0
                && blackKnights == 0
                && whiteBishops == 1
                && blackBishops == 1
                && whiteBishopColor == blackBishopColor;
    }

    /**
     * Returns a square color index for bishop-only dead-material checks.
     *
     * @param square board square, 0..63
     * @return 0 for one color complex, 1 for the other
     */
    private int bishopSquareColor(int square) {
        return ((square & 7) + (square >>> 3)) & 1;
    }
}
