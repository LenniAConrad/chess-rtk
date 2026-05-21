package application.gui.workbench.game;

import application.gui.workbench.board.*;
import application.gui.workbench.command.*;
import application.gui.workbench.dashboard.*;
import application.gui.workbench.layout.*;
import application.gui.workbench.mcts.*;
import application.gui.workbench.network.*;
import application.gui.workbench.publish.*;
import application.gui.workbench.session.*;
import application.gui.workbench.ui.*;
import application.gui.workbench.window.*;

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
     * @param position position
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
