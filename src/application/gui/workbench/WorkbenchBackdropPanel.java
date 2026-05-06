package application.gui.workbench;

import javax.swing.JPanel;

/**
 * Root workbench backdrop with a quiet, low-noise fill.
 */
final class WorkbenchBackdropPanel extends JPanel {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Creates a quiet backdrop panel.
     */
    WorkbenchBackdropPanel() {
        setOpaque(true);
        setBackground(WorkbenchTheme.BG);
    }
}
