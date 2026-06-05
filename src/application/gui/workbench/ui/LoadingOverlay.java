package application.gui.workbench.ui;

import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * A reusable loading indicator: a translucent scrim with a centered animated
 * arc spinner and a short message. Shown over a view while a background load
 * runs, mirroring the workbench's other "still working" affordances.
 *
 * <p>The component paints its own surface and is non-opaque, so a theme toggle
 * cannot restamp it. Callers toggle it with {@link #start(String)} and
 * {@link #stop()}; the animation timer only runs while it is active.</p>
 */
public final class LoadingOverlay extends JPanel {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Animation frame delay (~60fps), matching the rest of the workbench.
     */
    private static final int FRAME_MS = 16;

    /**
     * Spinner radius.
     */
    private static final int RADIUS = 16;

    /**
     * Sweep advance per frame, as a fraction of a full turn.
     */
    private static final double SPEED = 0.018;

    /**
     * Repaint timer driving the sweep.
     */
    private final transient Timer timer;

    /**
     * Current message under the spinner.
     */
    private String message = "Loading…";

    /**
     * Sweep progress, 0.0..1.0 around the circle.
     */
    private double sweep;

    /**
     * Creates an inactive loading overlay.
     */
    public LoadingOverlay() {
        setOpaque(false);
        setVisible(false);
        timer = new Timer(FRAME_MS, event -> {
            sweep += SPEED;
            if (sweep >= 1.0) {
                sweep -= 1.0;
            }
            repaint();
        });
        timer.setCoalesce(true);
    }

    /**
     * Shows the overlay and starts the spinner.
     *
     * @param loadingMessage message under the spinner
     */
    public void start(String loadingMessage) {
        message = loadingMessage == null || loadingMessage.isBlank() ? "Loading…" : loadingMessage;
        setVisible(true);
        if (!timer.isRunning()) {
            timer.start();
        }
        repaint();
    }

    /**
     * Hides the overlay and stops the spinner.
     */
    public void stop() {
        timer.stop();
        setVisible(false);
    }

    /**
     * Stops the timer when detached so it cannot leak.
     */
    @Override
    public void removeNotify() {
        timer.stop();
        super.removeNotify();
    }

    /**
     * Paints the scrim, spinner, and message.
     *
     * @param graphics graphics context
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g.setColor(Theme.withAlpha(Theme.BG, 224));
            g.fillRect(0, 0, w, h);
            int cx = w / 2;
            int cy = h / 2 - 10;
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(Theme.withAlpha(Theme.MUTED, 70));
            g.drawOval(cx - RADIUS, cy - RADIUS, RADIUS * 2, RADIUS * 2);
            g.setColor(Theme.ACCENT);
            int start = (int) Math.round(sweep * 360);
            g.drawArc(cx - RADIUS, cy - RADIUS, RADIUS * 2, RADIUS * 2, 90 - start, -110);
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(12, Font.PLAIN));
            FontMetrics metrics = g.getFontMetrics();
            g.drawString(message, cx - metrics.stringWidth(message) / 2, cy + RADIUS + 24);
        } finally {
            g.dispose();
        }
    }
}
