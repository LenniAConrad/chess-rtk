package application.gui.workbench.ui;

import application.gui.workbench.board.*;
import application.gui.workbench.command.*;
import application.gui.workbench.dashboard.*;
import application.gui.workbench.game.*;
import application.gui.workbench.layout.*;
import application.gui.workbench.mcts.*;
import application.gui.workbench.network.*;
import application.gui.workbench.publish.*;
import application.gui.workbench.session.*;
import application.gui.workbench.window.*;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JComponent;

/**
 * A small, dependency-free chart component for the workbench dashboard and
 * panels — either a signed {@linkplain Mode#LINE sparkline} or a
 * {@linkplain Mode#BARS bar strip}.
 *
 * <p>It is intentionally minimal: no axes, ticks or labels, just the shape of
 * the data at a glance. {@link #setLine(float[])} renders a signed series
 * around a zero baseline (positive above); {@link #setBars(float[], Color[])}
 * renders one coloured bar per value scaled into {@code 0..1}. The component
 * stretches horizontally but keeps a fixed compact height so it drops cleanly
 * into a {@code BoxLayout} card body.</p>
 */
public final class MiniChart extends JComponent {

    /**
     * Serialization identifier for Swing component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Rendering mode.
     */
    public enum Mode {

        /**
         * Signed line series around a zero baseline.
         */
        LINE,

        /**
         * One coloured bar per value.
         */
        BARS
    }

    /**
     * Default compact component height in pixels.
     */
    private static final int DEFAULT_HEIGHT = 46;

    /**
     * Inner padding around the plotted area.
     */
    private static final int INSET = Theme.SPACE_XS;

    /**
     * Active rendering mode.
     */
    private Mode mode = Mode.LINE;

    /**
     * Series values — signed for {@link Mode#LINE}, {@code 0..1} for
     * {@link Mode#BARS}.
     */
    private float[] values = new float[0];

    /**
     * Per-bar colours for {@link Mode#BARS}; null or short entries fall back to
     * the accent colour.
     */
    private transient Color[] barColors = new Color[0];

    /**
     * Placeholder shown when there is nothing to plot.
     */
    private String emptyText = "no data yet";

    /**
     * Creates an empty line-mode mini chart.
     */
    public MiniChart() {
        setOpaque(false);
        setForeground(Theme.ACCENT);
    }

    /**
     * Sets the placeholder text shown while the chart has no data.
     *
     * @param text placeholder text
     */
    public void setEmptyText(String text) {
        this.emptyText = text == null ? "" : text;
        repaint();
    }

    /**
     * Switches to line mode and sets a signed series. The vertical scale is
     * symmetric around zero, sized to the largest magnitude in the series.
     *
     * @param series signed values, oldest first (null treated as empty)
     */
    public void setLine(float[] series) {
        this.mode = Mode.LINE;
        this.values = series == null ? new float[0] : series.clone();
        this.barColors = new Color[0];
        repaint();
    }

    /**
     * Switches to bar mode and sets the bar heights and colours.
     *
     * @param heights bar heights, each clamped into {@code 0..1}, oldest first
     * @param colors per-bar colours (may be null or shorter than {@code heights})
     */
    public void setBars(float[] heights, Color[] colors) {
        this.mode = Mode.BARS;
        this.values = heights == null ? new float[0] : heights.clone();
        this.barColors = colors == null ? new Color[0] : colors.clone();
        repaint();
    }

    /**
     * Returns the preferred size — flexible width, fixed compact height.
     *
     * @return preferred size
     */
    @Override
    public Dimension getPreferredSize() {
    return new Dimension(160, DEFAULT_HEIGHT);
    }

    /**
     * Returns the maximum size — unbounded width, fixed height, so a vertical
     * {@code BoxLayout} stretches it sideways but not tall.
     *
     * @return maximum size
     */
    @Override
    public Dimension getMaximumSize() {
    return new Dimension(Integer.MAX_VALUE, DEFAULT_HEIGHT);
    }

    /**
     * Returns the minimum size.
     *
     * @return minimum size
     */
    @Override
    public Dimension getMinimumSize() {
    return new Dimension(60, DEFAULT_HEIGHT);
    }

    /**
     * Paints the chart.
     *
     * @param graphics graphics context
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g.setColor(Theme.ELEVATED_SOLID);
            g.fillRoundRect(0, 0, w - 1, h - 1, Theme.RADIUS, Theme.RADIUS);
            g.setColor(Theme.LINE);
            g.drawRoundRect(0, 0, w - 1, h - 1, Theme.RADIUS, Theme.RADIUS);

            int plotX = INSET;
            int plotY = INSET;
            int plotW = Math.max(1, w - 2 * INSET);
            int plotH = Math.max(1, h - 2 * INSET);
            if (values.length == 0) {
                paintEmpty(g, w, h);
            } else if (mode == Mode.LINE) {
                paintLine(g, plotX, plotY, plotW, plotH);
            } else {
                paintBars(g, plotX, plotY, plotW, plotH);
            }
        } finally {
            g.dispose();
        }
    }

    /**
     * Paints the empty-state placeholder.
     *
     * @param g graphics
     * @param w component width
     * @param h component height
     */
    private void paintEmpty(Graphics2D g, int w, int h) {
        if (emptyText.isEmpty()) {
            return;
        }
        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(10, java.awt.Font.PLAIN));
        java.awt.FontMetrics fm = g.getFontMetrics();
        int textWidth = fm.stringWidth(emptyText);
        g.drawString(emptyText, Math.max(INSET, (w - textWidth) / 2),
                (h - fm.getHeight()) / 2 + fm.getAscent());
    }

    /**
     * Paints the signed line series with a zero baseline.
     *
     * @param g graphics
     * @param x plot x origin
     * @param y plot y origin
     * @param plotW plot width
     * @param plotH plot height
     */
    private void paintLine(Graphics2D g, int x, int y, int plotW, int plotH) {
        float maxAbs = 0f;
        for (float v : values) {
            maxAbs = Math.max(maxAbs, Math.abs(v));
        }
        if (maxAbs <= 0f) {
            maxAbs = 1f;
        }
        int zeroY = y + plotH / 2;
        g.setColor(Theme.LINE);
        g.drawLine(x, zeroY, x + plotW, zeroY);

        int n = values.length;
        int[] px = new int[n];
        int[] py = new int[n];
        for (int i = 0; i < n; i++) {
            px[i] = n == 1 ? x + plotW / 2 : x + Math.round(i * (plotW - 1f) / (n - 1));
            float norm = values[i] / maxAbs; // -1..1
            py[i] = zeroY - Math.round(norm * (plotH / 2f - 1));
        }
        if (n >= 2) {
            // Soft fill between the line and the zero baseline.
            java.awt.Polygon fill = new java.awt.Polygon();
            fill.addPoint(px[0], zeroY);
            for (int i = 0; i < n; i++) {
                fill.addPoint(px[i], py[i]);
            }
            fill.addPoint(px[n - 1], zeroY);
            g.setColor(Theme.withAlpha(getForeground(), 38));
            g.fillPolygon(fill);
            g.setColor(getForeground());
            g.setStroke(new java.awt.BasicStroke(1.6f));
            for (int i = 1; i < n; i++) {
                g.drawLine(px[i - 1], py[i - 1], px[i], py[i]);
            }
        }
        // Highlight the most recent sample.
        int last = n - 1;
        g.setColor(getForeground());
        g.fillOval(px[last] - 2, py[last] - 2, 5, 5);
    }

    /**
     * Paints the bar strip.
     *
     * @param g graphics
     * @param x plot x origin
     * @param y plot y origin
     * @param plotW plot width
     * @param plotH plot height
     */
    private void paintBars(Graphics2D g, int x, int y, int plotW, int plotH) {
        int n = values.length;
        float slot = plotW / (float) n;
        int barWidth = Math.max(1, Math.round(slot) - 2);
        int baseY = y + plotH;
        for (int i = 0; i < n; i++) {
            float height01 = Math.max(0f, Math.min(1f, values[i]));
            int barHeight = Math.max(1, Math.round(height01 * (plotH - 1)));
            int barX = x + Math.round(i * slot);
            Color color = i < barColors.length && barColors[i] != null
                    ? barColors[i]
                    : Theme.ACCENT;
            g.setColor(color);
            g.fillRect(barX, baseY - barHeight, barWidth, barHeight);
        }
    }
}
