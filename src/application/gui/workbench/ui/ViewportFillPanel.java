package application.gui.workbench.ui;

import java.awt.LayoutManager;

/**
 * Scrollable wrapper that stretches short content to the visible viewport.
 */
final class ViewportFillPanel extends AbstractViewportPanel {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Creates a viewport-filling panel.
     *
     * @param layout layout manager
     */
    ViewportFillPanel(LayoutManager layout) {
        super(layout);
    }
}
