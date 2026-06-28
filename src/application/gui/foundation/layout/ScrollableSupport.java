package application.gui.foundation.layout;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;
import javax.swing.JViewport;
import javax.swing.SwingConstants;

/**
 * Shared helpers for components that implement Swing's {@link javax.swing.Scrollable}
 * contract.
 *
 * <p>The foundation layer owns layout math that has no dependency on Workbench
 * feature state, commands, chess rules, or theme colors.</p>
 */
public final class ScrollableSupport {

    /**
     * Default pixel distance for one wheel/key scroll step.
     */
    public static final int DEFAULT_UNIT_INCREMENT = 24;

    /**
     * Prevents instantiation.
     */
    private ScrollableSupport() {
        // utility
    }

    /**
     * Returns a component's natural preferred viewport size.
     *
     * @param component component shown in a scroll pane
     * @return preferred viewport size
     */
    public static Dimension preferredViewportSize(Component component) {
        return component.getPreferredSize();
    }

    /**
     * Returns a block increment for the requested orientation, leaving one
     * overlap step visible between page jumps.
     *
     * @param visibleRect visible viewport rectangle
     * @param orientation scroll orientation
     * @param overlap overlap and minimum increment in pixels
     * @return block increment in pixels
     */
    public static int blockIncrement(Rectangle visibleRect, int orientation, int overlap) {
        int size = orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
        return Math.max(overlap, size - overlap);
    }

    /**
     * Returns a vertical block increment with the same minimum and overlap.
     *
     * @param visibleRect visible viewport rectangle
     * @param increment minimum increment and overlap in pixels
     * @return block increment in pixels
     */
    public static int verticalBlockIncrement(Rectangle visibleRect, int increment) {
        return verticalBlockIncrement(visibleRect, increment, increment);
    }

    /**
     * Returns a vertical block increment with separate minimum and overlap
     * values.
     *
     * @param visibleRect visible viewport rectangle
     * @param minimum minimum block increment in pixels
     * @param overlap overlap kept visible after a page jump
     * @return block increment in pixels
     */
    public static int verticalBlockIncrement(Rectangle visibleRect, int minimum, int overlap) {
        return Math.max(minimum, visibleRect.height - overlap);
    }

    /**
     * Returns whether a component should stretch to viewport height because its
     * preferred content is shorter than the visible area.
     *
     * @param component component shown in a viewport
     * @return true when the component should track viewport height
     */
    public static boolean preferredHeightFitsViewport(Component component) {
        Container parent = component.getParent();
        return parent instanceof JViewport viewport
                && component.getPreferredSize().height < viewport.getHeight();
    }
}
