package application.gui.workbench.ui;

import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JPanel;

/**
 * Root workbench backdrop with a quiet wash behind frosted surfaces.
 */
public final class BackdropPanel extends JPanel {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Creates a quiet backdrop panel.
     */
    public BackdropPanel() {
        setOpaque(true);
        setBackground(Theme.BG);
    }

    /**
     * Paints the subtle macOS-style backdrop wash shown at window edges and
     * during resize.
     *
     * @param graphics graphics context
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            int h = Math.max(1, getHeight());
            g.setPaint(new GradientPaint(0, 0, Theme.BACKDROP_TOP, 0, h, Theme.BACKDROP_BOTTOM));
            g.fillRect(0, 0, getWidth(), getHeight());
            // A faint cool accent tint warms the top of the light wash. It is
            // skipped in dark mode, where it sat below the perception threshold.
            if (!Theme.isDark()) {
                g.setColor(Theme.withAlpha(Theme.ACCENT, 6));
                g.fillRect(0, 0, getWidth(), Math.max(1, h / 3));
            }
        } finally {
            g.dispose();
        }
    }
}
