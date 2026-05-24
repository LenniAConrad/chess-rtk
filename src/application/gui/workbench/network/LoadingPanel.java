/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package application.gui.workbench.network;

import application.gui.workbench.ui.Theme;
import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
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
     * Maximum width of the centered loading surface.
     */
    private static final int PANEL_WIDTH = 440;

    /**
     * Minimum width of the centered loading surface.
     */
    private static final int PANEL_MIN_WIDTH = 280;

    /**
     * Height of the centered loading surface.
     */
    private static final int PANEL_HEIGHT = 148;

    /**
     * Width reserved for the animated network mark.
     */
    private static final int MARK_WIDTH = 82;

    /**
     * Horizontal progress indicator height.
     */
    private static final int INDICATOR_HEIGHT = 5;

    /**
     * Total animation-frame count before the phase wraps.
     */
    private static final int PHASE_LIMIT = 160;

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
     * Model-file or fallback detail retained for compatibility with callers
     * that update the loading state with full context. The simplified loading
     * card intentionally does not paint this raw model text.
     */
    private String modelDetail = "Model status pending";

    /**
     * Position detail retained for compatibility with callers that update the
     * loading state with full context. The simplified loading card intentionally
     * does not paint raw FEN text.
     */
    private String positionDetail = "No position loaded";

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
        start(title, detail, "Model status pending", "No position loaded");
    }

    /**
     * Starts or updates the loading state.
     *
     * @param title primary loading message
     * @param detail secondary loading detail
     * @param modelDetail model-file or fallback detail
     * @param positionDetail current position detail
     */
    void start(String title, String detail, String modelDetail, String positionDetail) {
        this.title = title == null || title.isBlank() ? "Loading network view" : title;
        this.detail = detail == null || detail.isBlank() ? "Preparing activations" : detail;
        this.modelDetail = modelDetail == null || modelDetail.isBlank()
                ? "Model status pending"
                : modelDetail;
        this.positionDetail = positionDetail == null || positionDetail.isBlank()
                ? "No position loaded"
                : positionDetail;
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
        phase = (phase + 1) % PHASE_LIMIT;
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
        g.setColor(Theme.BG);
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);

        int panelWidth = Math.max(PANEL_MIN_WIDTH,
                Math.min(PANEL_WIDTH, bounds.width - 2 * Theme.SPACE_LG));
        int panelHeight = Math.min(PANEL_HEIGHT, Math.max(120, bounds.height - 2 * Theme.SPACE_LG));
        int x = bounds.x + (bounds.width - panelWidth) / 2;
        int y = bounds.y + (bounds.height - panelHeight) / 2;
        int arc = Theme.RADIUS * 2;

        g.setColor(Theme.PANEL_SOLID);
        g.fillRoundRect(x, y, panelWidth, panelHeight, arc, arc);
        g.setStroke(new BasicStroke(1.0f));
        g.setColor(Theme.LINE);
        g.drawRoundRect(x, y, panelWidth, panelHeight, arc, arc);
        g.setColor(Theme.ACCENT);
        g.fillRoundRect(x, y, 4, panelHeight, arc, arc);

        Rectangle mark = new Rectangle(x + 26, y + 28, MARK_WIDTH, panelHeight - 56);
        paintSpinnerMark(g, mark);

        int textX = mark.x + mark.width + 20;
        int textW = Math.max(80, x + panelWidth - textX - 28);
        paintTextBlock(g, new Rectangle(textX, y + 30, textW, panelHeight - 60));
    }

    /**
     * Paints the animated loading mark.
     *
     * @param g graphics context
     * @param bounds mark bounds
     */
    private void paintSpinnerMark(Graphics2D g, Rectangle bounds) {
        int size = Math.min(bounds.width, bounds.height);
        int x = bounds.x + (bounds.width - size) / 2;
        int y = bounds.y + (bounds.height - size) / 2;
        int inset = 10;
        int arcSize = size - inset * 2;
        int start = (phase * 360 / PHASE_LIMIT) % 360;

        g.setStroke(new BasicStroke(5.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(Theme.withAlpha(Theme.LINE, Theme.isDark() ? 220 : 190));
        g.drawArc(x + inset, y + inset, arcSize, arcSize, 0, 360);
        g.setColor(Theme.ACCENT);
        g.drawArc(x + inset, y + inset, arcSize, arcSize, start, 104);

        int pulse = 9 + pulse(0, 5);
        int centerX = x + size / 2;
        int centerY = y + size / 2;
        g.setColor(Theme.withAlpha(Theme.ACCENT, 70));
        g.fillOval(centerX - pulse, centerY - pulse, pulse * 2, pulse * 2);
        g.setColor(Theme.ACCENT);
        g.fillOval(centerX - 4, centerY - 4, 8, 8);
    }

    /**
     * Paints title, phase detail and progress rail.
     *
     * @param g graphics context
     * @param bounds text bounds
     */
    private void paintTextBlock(Graphics2D g, Rectangle bounds) {
        g.setFont(Theme.font(15, Font.BOLD));
        FontMetrics titleMetrics = g.getFontMetrics();
        g.setColor(Theme.TEXT);
        g.drawString(elide(g, title, bounds.width), bounds.x, bounds.y + titleMetrics.getAscent());

        g.setFont(Theme.font(12, Font.PLAIN));
        g.setColor(Theme.MUTED);
        String animatedDetail = detail + ".".repeat((phase / 18) % 4);
        g.drawString(elide(g, animatedDetail, bounds.width), bounds.x, bounds.y + 38);

        int railY = bounds.y + 64;
        paintProgressRail(g, bounds.x, railY, bounds.width);
    }

    /**
     * Paints the clipped indeterminate progress rail.
     *
     * @param g graphics context
     * @param x rail x
     * @param y rail y
     * @param width rail width
     */
    private void paintProgressRail(Graphics2D g, int x, int y, int width) {
        int movingWidth = Math.max(54, width / 3);
        int travel = width + movingWidth;
        int movingX = x - movingWidth + (phase * travel / PHASE_LIMIT);
        g.setColor(Theme.withAlpha(Theme.LINE, Theme.isDark() ? 210 : 180));
        g.fillRoundRect(x, y, width, INDICATOR_HEIGHT, INDICATOR_HEIGHT, INDICATOR_HEIGHT);
        Shape oldClip = g.getClip();
        g.clipRect(x, y, width, INDICATOR_HEIGHT);
        g.setColor(Theme.ACCENT);
        g.fillRoundRect(movingX, y, movingWidth, INDICATOR_HEIGHT,
                INDICATOR_HEIGHT, INDICATOR_HEIGHT);
        g.setClip(oldClip);
    }

    /**
     * Returns an animated pulse value.
     *
     * @param offset phase offset
     * @param amplitude pulse amplitude
     * @return value in {@code [0, amplitude]}
     */
    private int pulse(int offset, int amplitude) {
        double angle = ((phase + offset) % PHASE_LIMIT) / (double) PHASE_LIMIT * Math.PI * 2.0;
        return (int) Math.round((Math.sin(angle) * 0.5 + 0.5) * amplitude);
    }

    /**
     * Elides text to fit the available width.
     *
     * @param g graphics context with the target font
     * @param text source text
     * @param width maximum text width
     * @return elided text
     */
    private static String elide(Graphics2D g, String text, int width) {
        String value = text == null ? "" : text;
        if (g.getFontMetrics().stringWidth(value) <= width) {
            return value;
        }
        String suffix = "...";
        int suffixWidth = g.getFontMetrics().stringWidth(suffix);
        int end = value.length();
        while (end > 0 && g.getFontMetrics().stringWidth(value.substring(0, end)) + suffixWidth > width) {
            end--;
        }
        return end <= 0 ? suffix : value.substring(0, end) + suffix;
    }
}
