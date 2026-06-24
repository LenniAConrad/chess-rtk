package application.gui.workbench.dataset;

import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * Compact area/line chart for ordered dataset buckets.
 */
public final class DatasetAreaChart extends JComponent {

    /**
     * Serialization identifier for Swing component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Preferred chart height.
     */
    private static final int PREFERRED_HEIGHT = 204;

    /**
     * Animation frame cadence for newly loaded series.
     */
    private static final int ANIMATION_DELAY_MS = 16;

    /**
     * Duration for the area reveal animation in milliseconds.
     */
    private static final int REVEAL_MS = 220;

    /**
     * One ordered chart point.
     *
     * @param label x-axis label
     * @param value bucket value
     */
    private record PointValue(String label, long value) {
        /**
         * Normalizes nullable labels.
         *
         * @param label x-axis label
         * @param value bucket value
         */
        private PointValue {
            label = label == null || label.isBlank() ? "-" : label.trim();
        }
    }

    /**
     * Ordered points currently rendered by the chart.
     */
    private List<PointValue> points = List.of();

    /**
     * Series color role.
     */
    private DatasetChart.Role role = DatasetChart.Role.ACCENT;

    /**
     * Placeholder title shown when the chart has no values.
     */
    private String emptyText = "no data";

    /**
     * Optional one-line hint shown beneath the empty-state title.
     */
    private String emptyHint = "";

    /**
     * Current reveal progress applied to the plotted values.
     */
    private double revealProgress = 1.0d;

    /**
     * Wall-clock start time for the current reveal.
     */
    private long revealStartedAt;

    /**
     * Timer driving the reveal animation.
     */
    private final Timer revealTimer = new Timer(ANIMATION_DELAY_MS, event -> tickReveal());

    /**
     * Creates an empty area chart.
     */
    public DatasetAreaChart() {
        setOpaque(false);
        setFont(Theme.font(11, java.awt.Font.PLAIN));
        revealTimer.setCoalesce(true);
    }

    /**
     * Sets a two-line empty state.
     *
     * @param title empty-state title
     * @param hint empty-state hint
     */
    public void setEmpty(String title, String hint) {
        emptyText = title == null ? "" : title;
        emptyHint = hint == null ? "" : hint;
        repaint();
    }

    /**
     * Sets ordered bucket values.
     *
     * @param labels bucket labels
     * @param values bucket values
     * @param nextRole series color role
     */
    public void setBuckets(String[] labels, int[] values, DatasetChart.Role nextRole) {
        List<PointValue> next = new ArrayList<>();
        if (labels != null && values != null) {
            int count = Math.min(labels.length, values.length);
            for (int i = 0; i < count; i++) {
                next.add(new PointValue(labels[i], Math.max(0L, values[i])));
            }
        }
        DatasetChart.Role normalizedRole = nextRole == null ? DatasetChart.Role.ACCENT : nextRole;
        boolean changed = !points.equals(next) || role != normalizedRole;
        points = List.copyOf(next);
        role = normalizedRole;
        if (changed) {
            startReveal();
        }
        repaint();
    }

    /**
     * Stops chart animation when the component is detached.
     */
    @Override
    public void removeNotify() {
        revealTimer.stop();
        super.removeNotify();
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
        return new Dimension(180, 108);
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
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            if (points.isEmpty() || maxValue() <= 0L) {
                Ui.paintEmptyState(g, new Rectangle(0, 0, getWidth(), getHeight()), emptyText, emptyHint);
            } else {
                paintSeries(g);
            }
        } finally {
            g.dispose();
        }
    }

    /**
     * Paints the filled area, line, points, and compact labels.
     *
     * @param g graphics context
     */
    private void paintSeries(Graphics2D g) {
        int left = Theme.SPACE_MD + 2;
        int top = Theme.SPACE_SM + 4;
        int right = Theme.SPACE_MD + 2;
        int bottom = 26;
        Rectangle plot = new Rectangle(left, top,
                Math.max(1, getWidth() - left - right),
                Math.max(1, getHeight() - top - bottom));
        paintGrid(g, plot);

        long max = maxValue();
        double eased = Ui.easeOutCubic(revealProgress);
        Path2D line = new Path2D.Double();
        Path2D area = new Path2D.Double();
        int count = points.size();
        int baseY = plot.y + plot.height;
        int firstX = xFor(plot, 0, count);
        area.moveTo(firstX, baseY);
        for (int i = 0; i < count; i++) {
            int x = xFor(plot, i, count);
            int y = yFor(plot, points.get(i).value(), max, eased);
            if (i == 0) {
                line.moveTo(x, y);
            } else {
                line.lineTo(x, y);
            }
            area.lineTo(x, y);
        }
        int lastX = xFor(plot, count - 1, count);
        area.lineTo(lastX, baseY);
        area.closePath();

        Color series = DatasetChart.roleColor(role);
        g.setColor(Theme.withAlpha(series, 48));
        g.fill(area);
        g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(series);
        g.draw(line);
        paintPoints(g, plot, max, eased, series);
        paintLabels(g, plot, max);
    }

    /**
     * Paints background grid lines and the baseline.
     *
     * @param g graphics context
     * @param plot plot bounds
     */
    private void paintGrid(Graphics2D g, Rectangle plot) {
        g.setStroke(new BasicStroke(1f));
        g.setColor(Theme.withAlpha(Theme.LINE, 110));
        for (int i = 0; i <= 3; i++) {
            int y = plot.y + (plot.height * i) / 3;
            g.drawLine(plot.x, y, plot.x + plot.width, y);
        }
    }

    /**
     * Paints one point marker per bucket.
     *
     * @param g graphics context
     * @param plot plot bounds
     * @param max maximum value
     * @param eased animation progress
     * @param series series color
     */
    private void paintPoints(Graphics2D g, Rectangle plot, long max, double eased, Color series) {
        int count = points.size();
        for (int i = 0; i < count; i++) {
            int x = xFor(plot, i, count);
            int y = yFor(plot, points.get(i).value(), max, eased);
            g.setColor(Theme.PANEL_SOLID);
            g.fillOval(x - 4, y - 4, 8, 8);
            g.setColor(series);
            g.fillOval(x - 3, y - 3, 6, 6);
        }
    }

    /**
     * Paints compact axis labels and max value.
     *
     * @param g graphics context
     * @param plot plot bounds
     * @param max maximum value
     */
    private void paintLabels(Graphics2D g, Rectangle plot, long max) {
        g.setFont(Theme.font(10, java.awt.Font.PLAIN));
        FontMetrics metrics = g.getFontMetrics();
        String maxText = DatasetChart.formatCompactValue(max);
        g.setColor(Theme.MUTED);
        g.drawString(maxText, plot.x + plot.width - metrics.stringWidth(maxText), plot.y + metrics.getAscent());

        int labelY = plot.y + plot.height + metrics.getAscent() + Theme.SPACE_XS;
        String first = points.get(0).label();
        String last = points.get(points.size() - 1).label();
        g.drawString(Ui.elide(first, metrics, Math.max(40, plot.width / 3)), plot.x, labelY);
        String lastText = Ui.elide(last, metrics, Math.max(40, plot.width / 3));
        g.drawString(lastText, plot.x + plot.width - metrics.stringWidth(lastText), labelY);
        if (points.size() > 2) {
            String mid = points.get(points.size() / 2).label();
            int room = Math.max(40, plot.width / 3);
            String midText = Ui.elide(mid, metrics, room);
            g.drawString(midText, plot.x + plot.width / 2 - metrics.stringWidth(midText) / 2, labelY);
        }
    }

    /**
     * Returns the x coordinate for a point index.
     *
     * @param plot plot bounds
     * @param index point index
     * @param count point count
     * @return x coordinate
     */
    private static int xFor(Rectangle plot, int index, int count) {
        if (count <= 1) {
            return plot.x + plot.width / 2;
        }
        return plot.x + (int) Math.round(index * plot.width / (double) (count - 1));
    }

    /**
     * Returns the animated y coordinate for a value.
     *
     * @param plot plot bounds
     * @param value point value
     * @param max maximum value
     * @param eased animation progress
     * @return y coordinate
     */
    private static int yFor(Rectangle plot, long value, long max, double eased) {
        double fraction = Math.max(0.0d, Math.min(1.0d, value / (double) Math.max(1L, max)));
        return plot.y + plot.height - (int) Math.round(plot.height * fraction * eased);
    }

    /**
     * Returns the maximum point value.
     *
     * @return maximum value
     */
    private long maxValue() {
        return points.stream().mapToLong(PointValue::value).max().orElse(0L);
    }

    /**
     * Starts the reveal animation when the series has visible data.
     */
    private void startReveal() {
        if (maxValue() <= 0L) {
            revealProgress = 1.0d;
            revealTimer.stop();
            return;
        }
        revealProgress = 0.0d;
        revealStartedAt = System.currentTimeMillis();
        if (!revealTimer.isRunning()) {
            revealTimer.start();
        }
    }

    /**
     * Advances the reveal animation.
     */
    private void tickReveal() {
        revealProgress = Math.min(1.0d,
                (System.currentTimeMillis() - revealStartedAt) / (double) REVEAL_MS);
        if (revealProgress >= 1.0d) {
            revealTimer.stop();
        }
        repaint();
    }

}
