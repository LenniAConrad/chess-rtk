package application.gui.workbench.ui;

import java.awt.LayoutManager;
import javax.swing.JPanel;

/**
 * Flat, opaque workbench surface for top-level editor regions. Content panes
 * stay dense and seam-free in the VS Code sense: the surface fully clears its
 * own background so partial repaints never leave translucent trails, and it
 * does not draw a rounded card. Elevation and frosted-glass cues are reserved
 * for genuinely floating chrome (command palette, dialogs, popovers), where a
 * controlled backdrop keeps text legible without expensive live blur.
 */
public final class SurfacePanel extends JPanel {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Creates an opaque surface panel with a layout.
     *
     * @param layout layout manager
     */
    public SurfacePanel(LayoutManager layout) {
        super(layout);
        configure();
    }

    /**
     * Applies opaque-surface defaults.
     */
    private void configure() {
        setOpaque(true);
        setBackground(Theme.PANEL_SOLID);
        setForeground(Theme.TEXT);
        setBorder(Theme.pad(Theme.SPACE_MD, Theme.SPACE_MD, Theme.SPACE_MD, Theme.SPACE_MD));
    }
}
