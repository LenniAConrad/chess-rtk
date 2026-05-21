package application.gui.workbench.publish;

import application.gui.workbench.board.*;
import application.gui.workbench.command.*;
import application.gui.workbench.dashboard.*;
import application.gui.workbench.game.*;
import application.gui.workbench.layout.*;
import application.gui.workbench.mcts.*;
import application.gui.workbench.network.*;
import application.gui.workbench.session.*;
import application.gui.workbench.ui.*;
import application.gui.workbench.window.*;

/**
 * Diagram source type with stable identity and display label.
 */
public enum PublishSource {

    /** The current board position. */
    CURRENT_FEN("Current FEN"),
    /** The workbench game PGN. */
    GAME_PGN("Game PGN"),
    /** The batch FEN editor. */
    BATCH_FENS("Batch FENs"),
    /** A user-selected file. */
    EXISTING_FILE("Existing File");

    /**
     * Combo-box label.
     */
    private final String label;

    /**
     * Creates a publish source.
     *
     * @param label display label
     */
    PublishSource(String label) {
        this.label = label;
    }

    /**
     * Returns the display label.
     *
     * @return display label
     */
    @Override
    public String toString() {
        return label;
    }
}
