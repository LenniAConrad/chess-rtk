/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package application.gui.workbench.dataset;

import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * Compact horizontal bar chart for dataset metrics.
 */
public final class DatasetChart extends JComponent {

    /**
     * Serialization identifier for Swing component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Preferred chart height.
     */
    private static final int PREFERRED_HEIGHT = 150;

    /**
     * Minimum label column width.
     */
    private static final int LABEL_WIDTH = 88;

    /**
     * Right-side value column width.
     */
    private static final int VALUE_WIDTH = 58;

    /**
     * Vertical gap between bars.
     */
    private static final int BAR_GAP = 6;

    /**
     * Minimum bar row height.
     */
    private static final int BAR_HEIGHT = 14;

    /**
     * Animation frame cadence for newly loaded chart bars.
     */
    private static final int ANIMATION_DELAY_MS = 16;

    /**
     * Duration for bar reveal animation in milliseconds.
     */
    private static final int BAR_REVEAL_MS = 180;

    /**
     * Color role for a bar.
     */
    public enum Role {
        /**
         * Accent/info role.
         */
        ACCENT,

        /**
         * Success role.
         */
        SUCCESS,

        /**
         * Warning role.
         */
        WARNING,

        /**
         * Error role.
         */
        ERROR,

        /**
         * Purple neural-network role.
         */
        PURPLE,

        /**
         * Muted neutral role.
         */
        NEUTRAL
    }

    /**
     * One chart bar.
     *
     * @param label display label
     * @param value numeric value
     * @param role color role
     */
    public record Bar(String label, long value, Role role) {
        /**
         * Normalizes nullable labels and roles.
         */
        public Bar {
            label = label == null || label.isBlank() ? "-" : label.trim();
            role = role == null ? Role.ACCENT : role;
        }
    }

    /**
     * Bars currently rendered by the chart.
     */
    private List<Bar> bars = List.of();

    /**
     * Placeholder text shown when the chart has no values.
     */
    private String emptyText = "no data";

    /**
     * Current reveal progress applied to every filled bar.
     */
    private double barRevealProgress = 1.0d;

    /**
     * Wall-clock start time for the current bar reveal.
     */
    private long barRevealStartedAt;

    /**
     * Timer driving the bar reveal animation.
     */
    private final Timer barRevealTimer = new Timer(ANIMATION_DELAY_MS, event -> tickBarReveal());

    /**
     * Creates an empty chart.
     */
    public DatasetChart() {
        setOpaque(false);
        setFont(Theme.font(11, java.awt.Font.PLAIN));
        barRevealTimer.setCoalesce(true);
    }

    /**
     * Sets placeholder text.
     *
     * @param value placeholder text
     */
    public void setEmptyText(String value) {
        emptyText = value == null ? "" : value;
        repaint();
    }

    /**
     * Sets chart bars.
     *
     * @param values chart bars
     */
    public void setBars(List<Bar> values) {
        List<Bar> next = values == null ? List.of() : List.copyOf(values);
        boolean changed = !bars.equals(next);
        bars = next;
        if (changed) {
            startBarReveal();
        }
        repaint();
    }

    /**
     * Stops chart animation when the component is detached.
     */
    @Override
    public void removeNotify() {
        barRevealTimer.stop();
        super.removeNotify();
    }

    /**
     * Sets bucket values as labeled bars.
     *
     * @param labels bucket labels
     * @param values bucket values
     * @param role bar color role
     */
    public void setBuckets(String[] labels, int[] values, Role role) {
        List<Bar> next = new ArrayList<>();
        if (labels == null || values == null) {
            setBars(next);
            return;
        }
        int count = Math.min(labels.length, values.length);
        for (int i = 0; i < count; i++) {
            next.add(new Bar(labels[i], values[i], role));
        }
        setBars(next);
    }

    /**
     * Returns preferred chart size.
     *
     * @return preferred size
     */
    @Override
    public Dimension getPreferredSize() {
        return new Dimension(280, PREFERRED_HEIGHT);
    }

    /**
     * Returns minimum chart size.
     *
     * @return minimum size
     */
    @Override
    public Dimension getMinimumSize() {
        return new Dimension(180, 96);
    }

    /**
     * Paints chart content.
     *
     * @param graphics graphics context
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            paintShell(g);
            List<Bar> visible = bars.stream().filter(bar -> bar.value() > 0L).toList();
            if (visible.isEmpty()) {
                paintEmpty(g);
            } else {
                paintBars(g, visible);
            }
        } finally {
            g.dispose();
        }
    }

    /**
     * Paints the chart background and border.
     *
     * @param g graphics context
     */
    private void paintShell(Graphics2D g) {
        g.setColor(Theme.ELEVATED_SOLID);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, Theme.RADIUS, Theme.RADIUS);
        g.setColor(Theme.LINE);
        g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, Theme.RADIUS, Theme.RADIUS);
    }

    /**
     * Paints placeholder text.
     *
     * @param g graphics context
     */
    private void paintEmpty(Graphics2D g) {
        g.setFont(Theme.font(11, java.awt.Font.PLAIN));
        g.setColor(Theme.MUTED);
        FontMetrics metrics = g.getFontMetrics();
        int x = Math.max(Theme.SPACE_MD, (getWidth() - metrics.stringWidth(emptyText)) / 2);
        int y = (getHeight() + metrics.getAscent()) / 2;
        g.drawString(emptyText, x, y);
    }

    /**
     * Paints horizontal bars.
     *
     * @param g graphics context
     * @param visible visible bars
     */
    private void paintBars(Graphics2D g, List<Bar> visible) {
        long max = visible.stream().mapToLong(Bar::value).max().orElse(1L);
        int x = Theme.SPACE_MD;
        int y = Theme.SPACE_SM;
        int rowHeight = Math.max(BAR_HEIGHT + BAR_GAP,
                (getHeight() - 2 * Theme.SPACE_SM) / Math.max(1, visible.size()));
        int barX = x + LABEL_WIDTH;
        int barW = Math.max(1, getWidth() - barX - VALUE_WIDTH - Theme.SPACE_MD);
        g.setFont(Theme.font(11, java.awt.Font.PLAIN));
        FontMetrics metrics = g.getFontMetrics();
        for (Bar bar : visible) {
            paintBarRow(g, bar, max, x, y, rowHeight, barX, barW, metrics);
            y += rowHeight;
        }
    }

    /**
     * Paints one bar row.
     *
     * @param g graphics context
     * @param bar bar data
     * @param max maximum value
     * @param x row x coordinate
     * @param y row y coordinate
     * @param rowHeight row height
     * @param barX bar x coordinate
     * @param barW available bar width
     * @param metrics font metrics
     */
    private void paintBarRow(Graphics2D g, Bar bar, long max, int x, int y, int rowHeight,
            int barX, int barW, FontMetrics metrics) {
        int textY = y + (rowHeight + metrics.getAscent()) / 2 - 2;
        g.setColor(Theme.MUTED);
        g.drawString(elide(metrics, bar.label(), LABEL_WIDTH - Theme.SPACE_SM), x, textY);

        int barY = y + Math.max(0, (rowHeight - BAR_HEIGHT) / 2);
        g.setColor(Theme.NN_NEUTRAL);
        g.fillRoundRect(barX, barY, barW, BAR_HEIGHT, Theme.RADIUS, Theme.RADIUS);
        int filled = (int) Math.round((double) bar.value() * (double) barW
                / (double) Math.max(1L, max) * Ui.easeOutCubic(barRevealProgress));
        g.setColor(color(bar.role()));
        g.fillRoundRect(barX, barY, Math.max(1, filled), BAR_HEIGHT, Theme.RADIUS, Theme.RADIUS);

        String value = format(bar.value());
        g.setColor(Theme.TEXT);
        g.drawString(value, getWidth() - VALUE_WIDTH + Theme.SPACE_XS, textY);
    }

    /**
     * Returns a themed role color.
     *
     * @param role bar role
     * @return color
     */
    private static Color color(Role role) {
        return switch (role) {
            case SUCCESS -> Theme.STATUS_SUCCESS_BORDER;
            case WARNING -> Theme.STATUS_WARNING_BORDER;
            case ERROR -> Theme.STATUS_ERROR_BORDER;
            case PURPLE -> Theme.NN_POLICY;
            case NEUTRAL -> Theme.MUTED;
            case ACCENT -> Theme.ACCENT;
        };
    }

    /**
     * Starts the reveal animation for visible bars.
     */
    private void startBarReveal() {
        boolean hasVisibleBars = bars.stream().anyMatch(bar -> bar.value() > 0L);
        if (!hasVisibleBars) {
            barRevealProgress = 1.0d;
            barRevealTimer.stop();
            return;
        }
        barRevealProgress = 0.0d;
        barRevealStartedAt = System.currentTimeMillis();
        if (!barRevealTimer.isRunning()) {
            barRevealTimer.start();
        }
    }

    /**
     * Advances the reveal animation.
     */
    private void tickBarReveal() {
        barRevealProgress = Math.min(1.0d,
                (System.currentTimeMillis() - barRevealStartedAt) / (double) BAR_REVEAL_MS);
        if (barRevealProgress >= 1.0d) {
            barRevealTimer.stop();
        }
        repaint();
    }

    /**
     * Formats a count for compact chart display.
     *
     * @param value count
     * @return formatted count
     */
    private static String format(long value) {
        if (value >= 1_000_000L) {
            return String.format(java.util.Locale.ROOT, "%.1fm", value / 1_000_000.0d);
        }
        if (value >= 10_000L) {
            return String.format(java.util.Locale.ROOT, "%.1fk", value / 1_000.0d);
        }
        return Long.toString(value);
    }

    /**
     * Elides text to fit a width.
     *
     * @param metrics font metrics
     * @param text source text
     * @param width target width
     * @return elided text
     */
    private static String elide(FontMetrics metrics, String text, int width) {
        if (metrics.stringWidth(text) <= width) {
            return text;
        }
        String suffix = "...";
        int end = text.length();
        while (end > 0 && metrics.stringWidth(text.substring(0, end) + suffix) > width) {
            end--;
        }
        return end <= 0 ? suffix : text.substring(0, end) + suffix;
    }
}
