package application.gui.workbench.ui;

import application.gui.workbench.board.*;
import application.gui.workbench.command.*;
import application.gui.workbench.dashboard.*;
import application.gui.workbench.game.*;
import application.gui.workbench.layout.*;
import application.gui.workbench.mcts.*;
import application.gui.workbench.network.*;
import application.gui.workbench.publish.*;
import application.gui.workbench.session.*;
import application.gui.workbench.window.*;

import javax.swing.JPanel;

/**
 * Root workbench backdrop with a quiet, low-noise fill.
 */
public final class BackdropPanel extends JPanel {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Creates a quiet backdrop panel.
     */
    public BackdropPanel() {
        setOpaque(true);
        setBackground(Theme.BG);
    }
}
