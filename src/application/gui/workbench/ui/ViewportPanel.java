package application.gui.workbench.ui;

import application.gui.foundation.layout.ScrollableSupport;
import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import javax.swing.JPanel;
import javax.swing.Scrollable;

/**
 * Lightweight scroll-pane viewport content that tracks the viewport width and
 * stretches vertically while its preferred content is shorter than the view.
 */
class ViewportPanel extends JPanel implements Scrollable {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Creates a viewport panel with the given layout.
     *
     * @param layout layout manager
     */
    ViewportPanel(LayoutManager layout) {
        super(layout);
        setOpaque(false);
        setBackground(Theme.BG);
    }

    /**
     * Returns the natural viewport size.
     *
     * @return preferred size
     */
    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return ScrollableSupport.preferredViewportSize(this);
    }

    /**
     * Returns the shared compact scroll increment.
     *
     * @param visibleRect visible rectangle
     * @param orientation scroll orientation
     * @param direction scroll direction
     * @return unit increment
     */
    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return ScrollableSupport.DEFAULT_UNIT_INCREMENT;
    }

    /**
     * Returns a viewport-sized scroll increment with one unit of overlap.
     *
     * @param visibleRect visible rectangle
     * @param orientation scroll orientation
     * @param direction scroll direction
     * @return block increment
     */
    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return ScrollableSupport.blockIncrement(visibleRect, orientation,
                ScrollableSupport.DEFAULT_UNIT_INCREMENT);
    }

    /**
     * Tracks viewport width to avoid horizontal gutters and scrollbars.
     *
     * @return true
     */
    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    /**
     * Tracks viewport height while preferred content is shorter than the
     * available area.
     *
     * @return true when the panel should stretch to viewport height
     */
    @Override
    public boolean getScrollableTracksViewportHeight() {
        return ScrollableSupport.preferredHeightFitsViewport(this);
    }
}
