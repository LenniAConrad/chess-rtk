/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package application.gui.workbench.ui;

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
