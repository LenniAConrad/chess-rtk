package application.gui.workbench.network;

import application.gui.workbench.ui.Theme;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.swing.JPanel;

/**
 * Plain text placeholder shown while a network view is waiting for its first
 * activation snapshot.
 */
final class LoadingPanel extends JPanel {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Maximum width used for loading text.
     */
    private static final int TEXT_WIDTH = 520;

    /**
     * Vertical gap between title and detail text.
     */
    private static final int TEXT_GAP = 8;

    /**
     * Primary loading message.
     */
    private String title = "Loading network view";

    /**
     * Secondary loading detail.
     */
    private String detail = "Preparing activations";

    /**
     * Whether the loading state is active.
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
        active = true;
        repaint();
    }

    /**
     * Stops the loading state.
     */
    void stop() {
        active = false;
        repaint();
    }

    /**
     * Returns whether the loading state is active.
     *
     * @return true while active
     */
    boolean isActive() {
        return active;
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
     * Paints the loading text.
     *
     * @param g graphics context
     * @param bounds panel bounds
     */
    private void paintLoading(Graphics2D g, Rectangle bounds) {
        g.setColor(Theme.BG);
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);

        g.setFont(Theme.font(15, Font.BOLD));
        FontMetrics titleMetrics = g.getFontMetrics();
        g.setFont(Theme.font(12, Font.PLAIN));
        FontMetrics detailMetrics = g.getFontMetrics();
        int textW = Math.max(96, Math.min(TEXT_WIDTH, bounds.width - 2 * Theme.SPACE_XL));
        int blockH = titleMetrics.getHeight() + TEXT_GAP + detailMetrics.getHeight();
        int x = bounds.x + (bounds.width - textW) / 2;
        int y = bounds.y + Math.max(Theme.SPACE_XL, (bounds.height - blockH) / 2);

        g.setFont(Theme.font(15, Font.BOLD));
        String titleText = elide(g, title, textW);
        g.setColor(Theme.TEXT);
        g.drawString(titleText, centeredX(g, titleText, x, textW), y + titleMetrics.getAscent());

        g.setFont(Theme.font(12, Font.PLAIN));
        String detailText = elide(g, detail, textW);
        g.setColor(Theme.MUTED);
        g.drawString(detailText, centeredX(g, detailText, x, textW),
                y + titleMetrics.getHeight() + TEXT_GAP + detailMetrics.getAscent());
    }

    /**
     * Returns the x coordinate that centers text in a fixed-width text column.
     *
     * @param g graphics context
     * @param text text to draw
     * @param x column x
     * @param width column width
     * @return centered x coordinate
     */
    private static int centeredX(Graphics2D g, String text, int x, int width) {
        return x + Math.max(0, (width - g.getFontMetrics().stringWidth(text)) / 2);
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
