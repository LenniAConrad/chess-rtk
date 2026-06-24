package application.gui.workbench.game;

import chess.core.Move;
import chess.core.Position;
import chess.core.SAN;

/**
 * Shared text formatting helpers for positions and moves shown outside the
 * board.
 */
public final class PositionText {

    /**
     * Prevents instantiation.
     */
    private PositionText() {
        // utility
    }

    /**
     * Formats the tactical status of a position.
     *
     * @param position chess position
     * @return status text
     */
    public static String status(Position position) {
        if (position.isCheckmate()) {
            return "checkmate";
        }
        if (position.isStalemate()) {
            return "stalemate";
        }
        return position.inCheck() ? "check" : "normal";
    }

    /**
     * Formats SAN while keeping UI text resilient.
     *
     * @param position position before the move
     * @param move encoded move
     * @return SAN or UCI fallback
     */
    public static String safeSan(Position position, short move) {
        try {
            return SAN.toAlgebraic(position, move);
        } catch (IllegalArgumentException ex) {
            return Move.toString(move);
        }
    }
}
