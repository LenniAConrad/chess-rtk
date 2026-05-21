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

import java.awt.LayoutManager;

import javax.swing.JPanel;

/**
 * Flat workbench surface for top-level editor regions.
 */
public final class SurfacePanel extends JPanel {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Creates a solid panel with a layout.
     *
     * @param layout layout manager
     */
    public SurfacePanel(LayoutManager layout) {
        super(layout);
        configure();
    }

    /**
     * Applies solid-panel defaults.
     */
    private void configure() {
        setOpaque(true);
        setBackground(Theme.PANEL_SOLID);
        setForeground(Theme.TEXT);
        setBorder(Theme.pad(10, 10, 10, 10));
    }
}
