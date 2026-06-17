package application.gui.workbench.ui;

import java.awt.Color;
import java.awt.LayoutManager;
import javax.swing.JPanel;

/**
 * Flat, opaque workbench surface for top-level editor regions. Content panes
 * stay dense and seam-free in the VS Code sense: the surface fully clears its
 * own background so partial repaints never leave translucent trails, and it
 * does not draw a rounded card. Elevation and frosted-glass cues are reserved
 * for genuinely floating chrome (command palette, dialogs, popovers), where a
 * controlled backdrop keeps text legible without expensive live blur.
 *
 * <p>The {@link Theme.Surface} role chooses between the document-tone
 * {@code PANEL_SOLID} fill (dashboards, reports) and the chrome {@code BG}
 * backdrop that boards and engine surfaces lay raised cards on top of. A
 * single primitive on both sides keeps the uniformity contract intact while
 * letting each surface pick the appropriate elevation.</p>
 */
public class SurfacePanel extends JPanel {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Surface role chosen at construction time.
     */
    private final Theme.Surface role;

    /**
     * Creates an opaque document-tone surface panel.
     *
     * @param layout layout manager
     */
    public SurfacePanel(LayoutManager layout) {
        this(layout, Theme.Surface.PANEL);
    }

    /**
     * Creates an opaque surface panel with the requested role.
     *
     * @param layout layout manager
     * @param role surface role (document tone or chrome backdrop)
     */
    public SurfacePanel(LayoutManager layout, Theme.Surface role) {
        super(layout);
        this.role = role == null ? Theme.Surface.PANEL : role;
        setOpaque(true);
        setBackground(surfaceColor(this.role));
        setForeground(Theme.TEXT);
    }

    /**
     * Returns the surface role this panel was built with.
     *
     * @return surface role
     */
    public final Theme.Surface surfaceRole() {
        return role;
    }

    /**
     * Resolves the themed background color for a surface role. Called both at
     * construction and on theme refresh so a light/dark switch repaints in the
     * correct surface color.
     *
     * @param role surface role
     * @return background color
     */
    static Color surfaceColor(Theme.Surface role) {
        return role == Theme.Surface.BACKDROP ? Theme.BG : Theme.PANEL_SOLID;
    }
}
