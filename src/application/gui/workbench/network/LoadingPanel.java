package application.gui.workbench.network;

import application.gui.workbench.ui.Theme;
import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * Animated placeholder shown while a network view is waiting for its first
 * activation snapshot.
 */
final class LoadingPanel extends JPanel {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Animation frame duration in milliseconds.
     */
    private static final int FRAME_MS = 80;

    /**
     * Indicator width in pixels.
     */
    private static final int INDICATOR_WIDTH = 176;

    /**
     * Indicator height in pixels.
     */
    private static final int INDICATOR_HEIGHT = 4;

    /**
     * Timer that advances the indeterminate loading animation.
     */
    private final Timer timer = new Timer(FRAME_MS, event -> tick());

    /**
     * Primary loading message.
     */
    private String title = "Loading network view";

    /**
     * Secondary loading detail.
     */
    private String detail = "Preparing activations...";

    /**
     * Current animation phase.
     */
    private int phase;

    /**
     * Whether the loading animation is active.
     */
    private boolean active;

    /**
     * Creates the loading panel.
     */
    LoadingPanel() {
        setOpaque(true);
        setBackground(Theme.BG);
    }

    /**
     * Starts or updates the loading state.
     *
     * @param title primary loading message
     * @param detail secondary loading detail
     */
    void start(String title, String detail) {
        this.title = title == null || title.isBlank() ? "Loading network view" : title;
        this.detail = detail == null || detail.isBlank() ? "Preparing activations..." : detail;
        active = true;
        if (!timer.isRunning()) {
            timer.start();
        }
        repaint();
    }

    /**
     * Stops the loading animation.
     */
    void stop() {
        active = false;
        timer.stop();
        repaint();
    }

    /**
     * Returns whether the loading animation is active.
     *
     * @return true while active
     */
    boolean isActive() {
        return active;
    }

    /**
     * Advances one animation frame.
     */
    private void tick() {
        phase = (phase + 1) % 120;
        repaint();
    }

    /**
     * Paints the centered loading message.
     *
     * @param graphics graphics context
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            paintLoading(g, new Rectangle(0, 0, getWidth(), getHeight()));
        } finally {
            g.dispose();
        }
    }

    /**
     * Paints the loading content.
     *
     * @param g graphics context
     * @param bounds panel bounds
     */
    private void paintLoading(Graphics2D g, Rectangle bounds) {
        int cx = bounds.x + bounds.width / 2;
        int cy = bounds.y + bounds.height / 2;
        int x = cx - INDICATOR_WIDTH / 2;
        int y = cy - 16;

        g.setColor(Theme.withAlpha(Theme.LINE, 180));
        g.fillRoundRect(x, y, INDICATOR_WIDTH, INDICATOR_HEIGHT, 3, 3);

        int movingWidth = Math.max(42, INDICATOR_WIDTH / 3);
        int travel = INDICATOR_WIDTH + movingWidth;
        int movingX = x - movingWidth + (phase * travel / 120);
        g.setColor(Theme.ACCENT);
        g.fillRoundRect(movingX, y, movingWidth, INDICATOR_HEIGHT, 3, 3);

        g.setStroke(new BasicStroke(1.2f));
        g.setColor(Theme.withAlpha(Theme.ACCENT, 84));
        g.drawRoundRect(x - 10, y - 20, INDICATOR_WIDTH + 20, 72, 6, 6);

        g.setFont(Theme.font(13, Font.BOLD));
        g.setColor(Theme.TEXT);
        int titleWidth = g.getFontMetrics().stringWidth(title);
        g.drawString(title, cx - titleWidth / 2, y + 28);

        g.setFont(Theme.font(12, Font.PLAIN));
        g.setColor(Theme.MUTED);
        String animatedDetail = detail + ".".repeat((phase / 20) % 4);
        int detailWidth = g.getFontMetrics().stringWidth(animatedDetail);
        g.drawString(animatedDetail, cx - detailWidth / 2, y + 48);
    }
}
