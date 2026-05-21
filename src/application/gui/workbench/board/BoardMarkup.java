package application.gui.workbench.board;

import application.gui.workbench.command.*;
import application.gui.workbench.dashboard.*;
import application.gui.workbench.game.*;
import application.gui.workbench.layout.*;
import application.gui.workbench.mcts.*;
import application.gui.workbench.network.*;
import application.gui.workbench.publish.*;
import application.gui.workbench.session.*;
import application.gui.workbench.ui.*;
import application.gui.workbench.window.*;

import chess.core.Field;

/**
 * One arrow or circle markup overlay.
 *
 * @param from origin square
 * @param to target square, or {@link Field#NO_SQUARE} for a circle
 * @param brush annotation brush
 */
public record BoardMarkup(byte from, byte to, MarkupBrush brush) {

    /**
     * Returns whether this annotation is a circle marker.
     *
     * @return true for circles
     */
    public boolean isCircle() {
        return to == Field.NO_SQUARE;
    }
}
