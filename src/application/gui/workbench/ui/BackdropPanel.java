package application.gui.workbench.ui;

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
