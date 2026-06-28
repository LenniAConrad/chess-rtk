package application.gui.workbench.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

/**
 * Creates and refreshes Workbench-styled scroll panes.
 */
final class ScrollPaneStyler {

    /**
     * Styled scrollbar thickness.
     */
    private static final int SCROLLBAR_THICKNESS = 10;

    /**
     * Client-property key holding a {@code Supplier<Color>} for the surface a
     * scroll pane is embedded on, so its viewport fills with that surface
     * instead of the default {@link Theme#PANEL_SOLID}.
     */
    private static final String VIEWPORT_SURFACE_KEY = "crtk.viewportSurface";

    /**
     * Prevents instantiation.
     */
    private ScrollPaneStyler() {
        // utility
    }

    /**
     * Creates a styled scroll pane.
     *
     * @param view scrollable view
     * @return scroll pane
     */
    static JScrollPane scroll(JComponent view) {
        JScrollPane pane = new JScrollPane(view);
        style(pane);
        return pane;
    }

    /**
     * Creates a styled scroll pane whose viewport adopts a declared embedding
     * surface instead of the default panel fill.
     *
     * @param view scrollable view
     * @param viewportSurface supplier of the surface colour the viewport sits on
     * @return scroll pane
     */
    static JScrollPane scroll(JComponent view, Supplier<Color> viewportSurface) {
        JScrollPane pane = new JScrollPane(view);
        if (viewportSurface != null) {
            pane.putClientProperty(VIEWPORT_SURFACE_KEY, viewportSurface);
        }
        style(pane);
        return pane;
    }

    /**
     * Styles an existing scroll pane and its nested scroll bars.
     *
     * @param pane scroll pane
     */
    static void style(JScrollPane pane) {
        pane.setBorder(BorderFactory.createEmptyBorder());
        refresh(pane);
    }

    /**
     * Styles an existing scroll pane and declares the surface used for its
     * viewport, tracks, and corners.
     *
     * @param pane scroll pane
     * @param viewportSurface supplier of the embedding surface color
     */
    static void style(JScrollPane pane, Supplier<Color> viewportSurface) {
        declareViewportSurface(pane, viewportSurface);
        style(pane);
    }

    /**
     * Reapplies scroll-pane colors and custom scroll bars while preserving the
     * caller's outer border.
     *
     * @param pane scroll pane
     */
    static void refresh(JScrollPane pane) {
        Component view = pane.getViewport() == null ? null : pane.getViewport().getView();
        Color declared = declaredViewportSurface(pane);
        Color viewportBackground = declared != null ? declared : scrollBackground(view);
        pane.setOpaque(false);
        pane.setViewportBorder(BorderFactory.createEmptyBorder());
        pane.getViewport().setOpaque(true);
        pane.getViewport().setBackground(viewportBackground);
        pane.setBackground(Theme.TRANSPARENT);
        installScrollCorners(pane, viewportBackground);
        styleScrollBar(pane.getVerticalScrollBar(), viewportBackground);
        styleScrollBar(pane.getHorizontalScrollBar(), viewportBackground);
    }

    /**
     * Declares a viewport surface and refreshes an existing scroll pane while
     * preserving its caller-owned border.
     *
     * @param pane scroll pane
     * @param viewportSurface supplier of the embedding surface color
     */
    static void refresh(JScrollPane pane, Supplier<Color> viewportSurface) {
        declareViewportSurface(pane, viewportSurface);
        refresh(pane);
    }

    /**
     * Stores the caller-declared embedding surface.
     *
     * @param pane scroll pane
     * @param viewportSurface supplier of the surface color
     */
    private static void declareViewportSurface(JScrollPane pane, Supplier<Color> viewportSurface) {
        if (viewportSurface != null) {
            pane.putClientProperty(VIEWPORT_SURFACE_KEY, viewportSurface);
        }
    }

    /**
     * Styles one scroll bar.
     *
     * @param bar scroll bar
     * @param background surface color behind the overlay thumb
     */
    private static void styleScrollBar(JScrollBar bar, Color background) {
        if (bar == null) {
            return;
        }
        bar.setUI(new StyledScrollBarUI());
        bar.setOpaque(true);
        bar.setBackground(background);
        bar.setBorder(BorderFactory.createEmptyBorder());
        bar.setPreferredSize(new Dimension(SCROLLBAR_THICKNESS, SCROLLBAR_THICKNESS));
        bar.setUnitIncrement(18);
    }

    /**
     * Returns the caller-declared embedding surface for a scroll pane, or
     * {@code null} when none was declared.
     *
     * @param pane scroll pane
     * @return declared viewport surface colour, or {@code null}
     */
    private static Color declaredViewportSurface(JScrollPane pane) {
        Object surface = pane.getClientProperty(VIEWPORT_SURFACE_KEY);
        if (surface instanceof Supplier<?> supplier && supplier.get() instanceof Color resolved) {
            return resolved;
        }
        return null;
    }

    /**
     * Returns a solid viewport background for one scrollable view.
     *
     * @param view scrollable view
     * @return viewport background
     */
    private static Color scrollBackground(Component view) {
        if (!(view instanceof JComponent jComponent)) {
            return Theme.PANEL_SOLID;
        }
        Color background = jComponent.getBackground();
        return jComponent.isOpaque() && background != null && background.getAlpha() == 255
                ? background
                : Theme.PANEL_SOLID;
    }

    /**
     * Installs matching corner fillers to avoid default gray scroll-pane
     * corners.
     *
     * @param pane scroll pane
     * @param background corner background
     */
    private static void installScrollCorners(JScrollPane pane, Color background) {
        pane.setCorner(ScrollPaneConstants.UPPER_RIGHT_CORNER, scrollCorner(background));
        pane.setCorner(ScrollPaneConstants.LOWER_RIGHT_CORNER, scrollCorner(background));
        pane.setCorner(ScrollPaneConstants.LOWER_LEFT_CORNER, scrollCorner(background));
        pane.setCorner(ScrollPaneConstants.UPPER_LEFT_CORNER, scrollCorner(background));
    }

    /**
     * Creates one solid scroll-pane corner filler.
     *
     * @param background background color
     * @return corner component
     */
    private static JComponent scrollCorner(Color background) {
        JPanel corner = new JPanel();
        corner.setOpaque(true);
        corner.setBackground(background);
        return corner;
    }
}
