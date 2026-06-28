package application.gui.workbench.dataset;

import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * Compact donut (ring) chart for compositional dataset metrics — a split that
 * sums to a meaningful whole, such as side-to-move balance or row health.
 *
 * <p>Like {@link DatasetChart} the component is transparent and lives inside the
 * shared elevated card ({@link Ui#card}); it only paints the ring, the centred
 * total, and a small swatch legend. Distributions and rankings stay in
 * {@link DatasetChart} — a donut only reads well when the parts make a whole.</p>
 */
public final class DonutChart extends JComponent {

    /**
     * Serialization identifier for Swing component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Preferred chart height.
     */
    private static final int PREFERRED_HEIGHT = 204;

    /**
     * Gap in degrees rendered between adjacent ring segments. Kept small and
     * symmetric so it separates segments without distorting their proportions.
     */
    private static final double SEGMENT_GAP_DEGREES = 2.5d;

    /**
     * Animation frame cadence for the ring sweep reveal.
     */
    private static final int ANIMATION_DELAY_MS = 16;

    /**
     * Duration for the ring sweep reveal in milliseconds.
     */
    private static final int SWEEP_REVEAL_MS = 320;

    /**
     * One ring segment.
     *
     * @param label display label
     * @param value numeric value
     * @param role color role
     */
    public record Segment(String label, long value, DatasetChart.Role role) {
        /**
         * Normalizes nullable labels and roles.
         *
         * @param label display label
         * @param value numeric value
         * @param role color role
         */
        public Segment {
            label = label == null || label.isBlank() ? "-" : label.trim();
            role = role == null ? DatasetChart.Role.ACCENT : role;
        }
    }

    /**
     * Segments currently rendered by the chart.
     */
    private List<Segment> segments = List.of();

    /**
     * Caption shown beneath the centred total (e.g. "rows", "positions").
     */
    private String centerCaption = "";

    /**
     * Placeholder title shown when the chart has no values.
     */
    private String emptyText = "no data";

    /**
     * Optional one-line hint shown beneath the empty-state title.
     */
    private String emptyHint = "";

    /**
     * Current sweep progress applied to the ring reveal.
     */
    private double sweepProgress = 1.0d;

    /**
     * Wall-clock start time for the current sweep reveal.
     */
    private long sweepStartedAt;

    /**
     * Timer driving the ring sweep reveal animation.
     */
    private final Timer sweepTimer = new Timer(ANIMATION_DELAY_MS, event -> tickSweep());

    /**
     * Creates an empty donut chart.
     */
    public DonutChart() {
        setOpaque(false);
        setFont(Theme.font(11, java.awt.Font.PLAIN));
        sweepTimer.setCoalesce(true);
    }

    /**
     * Sets a two-line empty state: a title and a one-line hint.
     *
     * @param title empty-state title
     * @param hint one-line hint
     */
    public void setEmpty(String title, String hint) {
        emptyText = title == null ? "" : title;
        emptyHint = hint == null ? "" : hint;
        repaint();
    }

    /**
     * Sets ring segments and the caption shown beneath the centred total.
     *
     * @param values ring segments
     * @param caption short caption for the centred total
     */
    public void setSegments(List<Segment> values, String caption) {
        List<Segment> next = values == null ? List.of() : List.copyOf(values);
        boolean changed = !segments.equals(next);
        segments = next;
        centerCaption = caption == null ? "" : caption;
        if (changed) {
            startSweep();
        }
        repaint();
    }

    /**
     * Stops chart animation when the component is detached.
     */
    @Override
    public void removeNotify() {
        sweepTimer.stop();
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
        return new Dimension(180, 150);
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
            List<Segment> visible = visibleSegments();
            long total = visible.stream().mapToLong(Segment::value).sum();
            if (visible.isEmpty() || total <= 0L) {
                Ui.paintEmptyState(g, new Rectangle(0, 0, getWidth(), getHeight()), emptyText, emptyHint);
            } else {
                paintDonut(g, visible, total);
            }
        } finally {
            g.dispose();
        }
    }

    /**
     * Returns the non-zero segments.
     *
     * @return visible segments
     */
    private List<Segment> visibleSegments() {
        return segments.stream().filter(segment -> segment.value() > 0L).toList();
    }

    /**
     * Paints the ring, centred total, and legend.
     *
     * @param g graphics context
     * @param visible visible segments
     * @param total summed value across visible segments
     */
    private void paintDonut(Graphics2D g, List<Segment> visible, long total) {
        int pad = Theme.SPACE_MD;
        int availableWidth = Math.max(1, getWidth() - 2 * pad);
        int rowHeight = 20;
        int legendColumns = legendColumns(visible.size(), availableWidth);
        int legendRows = Math.max(1, (visible.size() + legendColumns - 1) / legendColumns);
        int legendHeight = legendRows * rowHeight;
        int ringAreaHeight = Math.max(72, getHeight() - 2 * pad - legendHeight - Theme.SPACE_SM);
        int ringBox = Math.max(72, Math.min(availableWidth, ringAreaHeight));
        int ringX = pad + Math.max(0, (availableWidth - ringBox) / 2);
        int ringY = pad;
        float thickness = Math.max(10f, ringBox * 0.18f);
        double inset = thickness / 2.0d + 1.0d;
        double arcX = ringX + inset;
        double arcY = ringY + inset;
        double arcD = ringBox - 2 * inset;

        // Faint full-circle track so partial data still reads as a ring.
        g.setStroke(new BasicStroke(thickness, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g.setColor(Theme.NN_NEUTRAL);
        g.draw(new Arc2D.Double(arcX, arcY, arcD, arcD, 0.0d, 360.0d, Arc2D.OPEN));

        // Segments sweep clockwise from twelve o'clock with flat (butt) caps so
        // each arc spans exactly its share of the circle — rounded caps added a
        // fixed half-disc to every end, which inflated small slices and made an
        // even split (e.g. side-to-move balance) read as lopsided. A thin
        // symmetric gap still separates adjacent segments without skewing them.
        double swept = Ui.easeOutCubic(sweepProgress);
        boolean single = visible.size() == 1;
        double gap = single ? 0.0d : SEGMENT_GAP_DEGREES;
        g.setStroke(new BasicStroke(thickness, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        double cursor = 90.0d;
        for (Segment segment : visible) {
            double fraction = (double) segment.value() / (double) total;
            double extent = fraction * 360.0d * swept;
            double drawExtent = Math.max(0.0d, extent - gap);
            if (drawExtent > 0.0d) {
                g.setColor(DatasetChart.roleColor(segment.role()));
                g.draw(new Arc2D.Double(arcX, arcY, arcD, arcD,
                        cursor - gap / 2.0d, -drawExtent, Arc2D.OPEN));
            }
            cursor -= extent;
        }

        paintCenter(g, ringX, ringY, ringBox, total);
        paintLegend(g, visible, total, pad, ringY + ringBox + Theme.SPACE_SM,
                availableWidth, legendColumns, rowHeight);
    }

    /**
     * Paints the centred total and its caption inside the ring hole.
     *
     * @param g graphics context
     * @param ringX ring left
     * @param ringY ring top
     * @param ringBox ring diameter box
     * @param total summed value
     */
    private void paintCenter(Graphics2D g, int ringX, int ringY, int ringBox, long total) {
        int cx = ringX + ringBox / 2;
        int cy = ringY + ringBox / 2;
        g.setFont(Theme.font(19, java.awt.Font.BOLD));
        FontMetrics big = g.getFontMetrics();
        String headline = DatasetChart.formatCompactValue(total);
        g.setColor(Theme.TEXT);
        g.drawString(headline, cx - big.stringWidth(headline) / 2,
                cy + (centerCaption.isBlank() ? big.getAscent() / 2 - 2 : big.getAscent() / 2 - 6));
        if (!centerCaption.isBlank()) {
            g.setFont(Theme.font(10, java.awt.Font.PLAIN));
            FontMetrics small = g.getFontMetrics();
            g.setColor(Theme.MUTED);
            g.drawString(centerCaption, cx - small.stringWidth(centerCaption) / 2,
                    cy + big.getAscent() / 2 + small.getAscent() - 2);
        }
    }

    /**
     * Paints the swatch legend below the ring.
     *
     * @param g graphics context
     * @param visible visible segments
     * @param total summed value
     * @param x legend left
     * @param y legend top
     * @param width available width
     * @param columns legend column count
     * @param rowHeight legend row height
     */
    private void paintLegend(Graphics2D g, List<Segment> visible, long total, int x, int y,
            int width, int columns, int rowHeight) {
        int swatch = 9;
        int cellGap = Theme.SPACE_MD;
        int cellWidth = Math.max(1, (width - (columns - 1) * cellGap) / Math.max(1, columns));
        g.setFont(Theme.font(11, java.awt.Font.PLAIN));
        FontMetrics metrics = g.getFontMetrics();
        for (int i = 0; i < visible.size(); i++) {
            Segment segment = visible.get(i);
            int column = i % columns;
            int row = i / columns;
            int cellX = x + column * (cellWidth + cellGap);
            int centerY = y + row * rowHeight + rowHeight / 2;
            g.setColor(DatasetChart.roleColor(segment.role()));
            g.fillRoundRect(cellX, centerY - swatch / 2, swatch, swatch, 3, 3);
            int textX = cellX + swatch + Theme.SPACE_SM;
            int percent = (int) Math.round(100.0d * segment.value() / total);
            String value = percent + "%";
            int valueWidth = metrics.stringWidth(value);
            g.setColor(Theme.TEXT);
            g.drawString(value, cellX + cellWidth - valueWidth, centerY + metrics.getAscent() / 2 - 1);
            g.setColor(Theme.MUTED);
            int labelRoom = Math.max(1, cellWidth - swatch - Theme.SPACE_SM - valueWidth - Theme.SPACE_SM);
            g.drawString(Ui.elide(segment.label(), metrics, labelRoom), textX,
                    centerY + metrics.getAscent() / 2 - 1);
        }
    }

    /**
     * Returns the number of legend columns that can read comfortably below the
     * ring.
     *
     * @param segmentCount visible segment count
     * @param availableWidth width available to the legend
     * @return legend columns
     */
    private static int legendColumns(int segmentCount, int availableWidth) {
        return segmentCount > 2 && availableWidth >= 220 ? 2 : 1;
    }

    /**
     * Starts the sweep reveal animation.
     */
    private void startSweep() {
        boolean hasValues = segments.stream().anyMatch(segment -> segment.value() > 0L);
        if (!hasValues) {
            sweepProgress = 1.0d;
            sweepTimer.stop();
            return;
        }
        sweepProgress = 0.0d;
        sweepStartedAt = System.currentTimeMillis();
        if (!sweepTimer.isRunning()) {
            sweepTimer.start();
        }
    }

    /**
     * Advances the sweep reveal animation.
     */
    private void tickSweep() {
        sweepProgress = Math.min(1.0d,
                (System.currentTimeMillis() - sweepStartedAt) / (double) SWEEP_REVEAL_MS);
        if (sweepProgress >= 1.0d) {
            sweepTimer.stop();
        }
        repaint();
    }

}
