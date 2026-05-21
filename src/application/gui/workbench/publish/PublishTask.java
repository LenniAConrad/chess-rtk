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
 * Publishing task type with stable identity and display label.
 */
public enum PublishTask {

    /** Direct diagram PDFs. */
    DIAGRAMS("Diagrams PDF"),
    /** Render a manifest PDF. */
    RENDER("Render Manifest PDF"),
    /** Build a puzzle collection. */
    COLLECTION("Puzzle Collection"),
    /** Render a study book. */
    STUDY("Study Book"),
    /** Render a cover PDF. */
    COVER("Cover PDF");

    /**
     * Combo-box label.
     */
    private final String label;

    /**
     * Creates a publish task.
     *
     * @param label display label
     */
    PublishTask(String label) {
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
