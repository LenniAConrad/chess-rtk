package application.gui.workbench.network;

import application.gui.workbench.ui.Theme;
import java.awt.BasicStroke;
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
     * Height of the neural-network glyph above the text.
     */
    private static final int GLYPH_HEIGHT = 60;

    /**
     * Width of the neural-network glyph above the text.
     */
    private static final int GLYPH_WIDTH = 150;

    /**
     * Layer sizes for the compact feed-forward network motif.
     */
    private static final int[] GLYPH_LAYERS = { 4, 3, 3, 2 };

    /**
     * Vertical distance between adjacent nodes in every layer.
     */
    private static final int GLYPH_NODE_GAP = 18;

    /**
     * Gap between the glyph and the title.
     */
    private static final int GLYPH_GAP = 18;

    /**
     * Primary loading message.
     */
    private String title = "Loading evaluator view";

    /**
     * Secondary loading detail.
     */
    private String detail = "Preparing activations";

    /**
     * Model-file or fallback detail, shown as a muted caption when present.
     */
    private String modelDetail = "";

    /**
     * Current-position detail, shown as a muted caption when present.
     */
    private String positionDetail = "";

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
        start(title, detail, "", "");
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
        this.title = title == null || title.isBlank() ? "Loading evaluator view" : title;
        this.detail = detail == null || detail.isBlank() ? "Preparing activations" : detail;
        this.modelDetail = modelDetail == null ? "" : modelDetail.trim();
        this.positionDetail = positionDetail == null ? "" : positionDetail.trim();
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
        g.setFont(Theme.font(11, Font.PLAIN));
        FontMetrics captionMetrics = g.getFontMetrics();
        boolean hasModel = !modelDetail.isBlank();
        boolean hasPosition = !positionDetail.isBlank();
        int extraLines = (hasModel ? 1 : 0) + (hasPosition ? 1 : 0);
        int textW = Math.max(96, Math.min(TEXT_WIDTH, bounds.width - 2 * Theme.SPACE_XL));
        int blockH = GLYPH_HEIGHT + GLYPH_GAP + titleMetrics.getHeight() + TEXT_GAP + detailMetrics.getHeight()
                + extraLines * (Theme.SPACE_XS + captionMetrics.getHeight());
        int x = bounds.x + (bounds.width - textW) / 2;
        int top = bounds.y + Math.max(Theme.SPACE_XL, (bounds.height - blockH) / 2);

        paintNetworkGlyph(g, bounds.x + bounds.width / 2, top, GLYPH_HEIGHT);
        int baseline = top + GLYPH_HEIGHT + GLYPH_GAP;

        g.setFont(Theme.font(15, Font.BOLD));
        String titleText = elide(g, title, textW);
        g.setColor(Theme.TEXT);
        baseline += titleMetrics.getAscent();
        g.drawString(titleText, centeredX(g, titleText, x, textW), baseline);

        g.setFont(Theme.font(12, Font.PLAIN));
        String detailText = elide(g, detail, textW);
        g.setColor(Theme.MUTED);
        baseline += titleMetrics.getDescent() + TEXT_GAP + detailMetrics.getAscent();
        g.drawString(detailText, centeredX(g, detailText, x, textW), baseline);

        // The richer model / position captions the caller computes (e.g.
        // "lc0-model.pb.gz - loaded", "rnbq... - white to move").
        g.setFont(Theme.font(11, Font.PLAIN));
        if (hasModel) {
            String t = elide(g, modelDetail, textW);
            baseline += detailMetrics.getDescent() + Theme.SPACE_XS + captionMetrics.getAscent();
            g.drawString(t, centeredX(g, t, x, textW), baseline);
        }
        if (hasPosition) {
            String t = elide(g, positionDetail, textW);
            baseline += (hasModel ? captionMetrics.getDescent() : detailMetrics.getDescent())
                    + Theme.SPACE_XS + captionMetrics.getAscent();
            g.drawString(t, centeredX(g, t, x, textW), baseline);
        }
    }

    /**
     * Paints a small, quiet feed-forward neural-network motif: four input
     * nodes, two three-node hidden layers, and two accent output nodes. Adjacent
     * layers are fully connected, while every layer uses the same vertical node
     * spacing so the tapered shape stays balanced.
     *
     * @param g graphics context
     * @param centerX horizontal center
     * @param top top of the glyph
     * @param size glyph height
     */
    private static void paintNetworkGlyph(Graphics2D g, int centerX, int top, int size) {
        int[] counts = GLYPH_LAYERS;
        int columnGap = GLYPH_WIDTH / (counts.length - 1);
        int totalWidth = columnGap * (counts.length - 1);
        int firstX = centerX - totalWidth / 2;
        int radius = Math.max(3, Math.round(size * 0.07f));
        int[][] nodeX = new int[counts.length][];
        int[][] nodeY = new int[counts.length][];
        for (int col = 0; col < counts.length; col++) {
            nodeX[col] = new int[counts[col]];
            nodeY[col] = new int[counts[col]];
            int span = Math.max(0, (counts[col] - 1) * GLYPH_NODE_GAP);
            int startY = top + (size - span) / 2;
            for (int row = 0; row < counts[col]; row++) {
                nodeX[col][row] = firstX + col * columnGap;
                nodeY[col][row] = counts[col] == 1 ? top + size / 2
                        : startY + Math.round(span * (row / (float) (counts[col] - 1)));
            }
        }
        g.setStroke(new BasicStroke(1.15f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(Theme.withAlpha(Theme.MUTED, Theme.isDark() ? 72 : 96));
        paintFullyConnectedLayers(g, nodeX, nodeY);
        for (int col = 0; col < counts.length; col++) {
            boolean output = col == counts.length - 1;
            for (int row = 0; row < counts[col]; row++) {
                g.setColor(output ? Theme.ACCENT
                        : col == 0 ? Theme.withAlpha(Theme.TEXT, 180)
                                : Theme.withAlpha(Theme.MUTED, 205));
                g.fillOval(nodeX[col][row] - radius, nodeY[col][row] - radius, radius * 2, radius * 2);
            }
        }
    }

    /**
     * Paints every edge between adjacent layers.
     *
     * @param g graphics context
     * @param nodeX x coordinates by layer and row
     * @param nodeY y coordinates by layer and row
     */
    private static void paintFullyConnectedLayers(
            Graphics2D g,
            int[][] nodeX,
            int[][] nodeY) {
        for (int col = 0; col < nodeX.length - 1; col++) {
            for (int left = 0; left < nodeX[col].length; left++) {
                for (int right = 0; right < nodeX[col + 1].length; right++) {
                    g.drawLine(nodeX[col][left], nodeY[col][left],
                            nodeX[col + 1][right], nodeY[col + 1][right]);
                }
            }
        }
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
