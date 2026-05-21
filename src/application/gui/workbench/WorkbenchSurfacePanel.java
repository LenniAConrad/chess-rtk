package application.gui.workbench;

import java.awt.LayoutManager;

import javax.swing.JPanel;

/**
 * Flat workbench surface for top-level editor regions.
 */
final class WorkbenchSurfacePanel extends JPanel {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Creates a solid panel with a layout.
     *
     * @param layout layout manager
     */
    WorkbenchSurfacePanel(LayoutManager layout) {
        super(layout);
        configure();
    }

    /**
     * Applies solid-panel defaults.
     */
    private void configure() {
        setOpaque(true);
        setBackground(WorkbenchTheme.PANEL_SOLID);
        setForeground(WorkbenchTheme.TEXT);
        setBorder(WorkbenchTheme.pad(10, 10, 10, 10));
    }
}
