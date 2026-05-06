package application.gui.workbench;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;

import javax.swing.JPanel;

/**
 * Minimal top-level surface that clears its own background before painting.
 */
final class WorkbenchSurfacePanel extends JPanel {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Corner radius in pixels.
     */
    private static final int RADIUS = 6;

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
        setBackground(WorkbenchTheme.BG);
        setForeground(WorkbenchTheme.TEXT);
        setBorder(WorkbenchTheme.pad(10, 10, 10, 10));
    }

    /**
     * Paints a simple fill and hairline border.
     *
     * @param graphics graphics context
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int width = getWidth() - 1;
            int height = getHeight() - 1;
            g.setColor(WorkbenchTheme.PANEL);
            g.fillRoundRect(0, 0, width, height, RADIUS, RADIUS);
            g.setColor(WorkbenchTheme.LINE);
            g.drawRoundRect(0, 0, width, height, RADIUS, RADIUS);
        } finally {
            g.dispose();
        }
    }
}
