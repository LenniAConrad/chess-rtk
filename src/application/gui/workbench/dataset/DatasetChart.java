package application.gui.workbench.dataset;

import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
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
    private static final int PREFERRED_HEIGHT = 204;

    /**
     * Preferred label column width.
     */
    private static final int LABEL_WIDTH = 122;

    /**
     * Right-side value column width.
     */
    private static final int VALUE_WIDTH = 58;

    /**
     * Vertical gap between bars.
     */
    private static final int BAR_GAP = 8;

    /**
     * Minimum bar row height.
     */
    private static final int BAR_HEIGHT = 12;

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
         * Default informational series.
         */
        ACCENT,

        /**
         * Valid, complete, or otherwise healthy counts.
         */
        SUCCESS,

        /**
         * Recoverable data-quality or coverage issues.
         */
        WARNING,

        /**
         * Invalid rows or failed processing outcomes.
         */
        ERROR,

        /**
         * Neural-network or model-derived series.
         */
        PURPLE,

        /**
         * Baseline or background comparison series.
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
         *
         * @param label display label
         * @param value numeric value
         * @param role color role
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
     * Fixed full-scale value the bars are measured against, or {@code 0} to scale
     * each bar relative to the largest bar. A fixed scale lets bars read as a true
     * fraction of a known whole (e.g. tag / score coverage out of valid rows), so
     * the faint track behind each bar represents 100%.
     */
    private long scaleMax;

    /**
     * Placeholder text shown when the chart has no values.
     */
    private String emptyText = "no data";

    /**
     * Optional one-line hint shown beneath the empty-state title.
     */
    private String emptyHint = "";

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
     * Optional callback fired when the user clicks a visible bar label/row.
     */
    private transient Consumer<String> selectionHandler;

    /**
     * Creates an empty chart.
     */
    public DatasetChart() {
        setOpaque(false);
        setFont(Theme.font(11, java.awt.Font.PLAIN));
        barRevealTimer.setCoalesce(true);
        addMouseListener(new MouseAdapter() {
            /**
             * {@inheritDoc}
             */
            @Override
            public void mouseClicked(MouseEvent event) {
                if (selectionHandler == null || !javax.swing.SwingUtilities.isLeftMouseButton(event)) {
                    return;
                }
                Bar bar = barAt(event.getY());
                if (bar != null) {
                    selectionHandler.accept(bar.label());
                }
            }
        });
    }

    /**
     * Sets an optional visible-bar click callback. Passing {@code null}
     * disables chart actions.
     *
     * @param handler label callback
     */
    public void setSelectionHandler(Consumer<String> handler) {
        selectionHandler = handler;
        setCursor(handler == null ? Cursor.getDefaultCursor() : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText(handler == null ? null : "Click a bar to filter matching rows");
    }

    /**
     * Sets a richer two-line empty state: a title and a one-line hint on how to
     * populate the chart.
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
     * Sets chart bars scaled relative to the largest bar.
     *
     * @param values chart bars
     */
    public void setBars(List<Bar> values) {
        setBars(values, 0L);
    }

    /**
     * Sets chart bars measured against a fixed full-scale value.
     *
     * @param values chart bars
     * @param fullScale value mapped to a full-width bar, or {@code 0} to scale
     *     each bar relative to the largest bar
     */
    public void setBars(List<Bar> values, long fullScale) {
        List<Bar> next = values == null ? List.of() : List.copyOf(values);
        long nextScale = Math.max(0L, fullScale);
        boolean changed = !bars.equals(next) || scaleMax != nextScale;
        bars = next;
        scaleMax = nextScale;
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
            // The chart is transparent: it sits inside the shared elevated card
            // (Ui.card) which paints the surface + border, so every card-grid in
            // the app (dashboard, datasets) reads identically.
            List<Bar> visible = visibleBars();
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
     * Paints placeholder text.
     *
     * @param g graphics context
     */
    private void paintEmpty(Graphics2D g) {
        application.gui.workbench.ui.Ui.paintEmptyState(g,
                new java.awt.Rectangle(0, 0, getWidth(), getHeight()), emptyText, emptyHint);
    }

    /**
     * Paints horizontal bars.
     *
     * @param g graphics context
     * @param visible visible bars
     */
    private void paintBars(Graphics2D g, List<Bar> visible) {
        long max = scaleMax > 0L ? scaleMax : visible.stream().mapToLong(Bar::value).max().orElse(1L);
        int x = Theme.SPACE_MD;
        int y = Theme.SPACE_SM;
        int availableHeight = Math.max(1, getHeight() - 2 * Theme.SPACE_SM);
        int rowHeight = Math.max(BAR_HEIGHT + BAR_GAP, availableHeight / Math.max(1, visible.size()));
        int labelWidth = labelWidth();
        int barX = x + labelWidth;
        int barW = Math.max(1, getWidth() - barX - VALUE_WIDTH - Theme.SPACE_MD);
        g.setFont(Theme.font(11, java.awt.Font.PLAIN));
        FontMetrics metrics = g.getFontMetrics();
        for (Bar bar : visible) {
            paintBarRow(g, bar, max, x, y, rowHeight, labelWidth, barX, barW, metrics);
            y += rowHeight;
        }
    }

    /**
     * Returns non-zero bars that can fit in the current chart height.
     *
     * @return visible bars
     */
    private List<Bar> visibleBars() {
        List<Bar> visible = bars.stream().filter(bar -> bar.value() > 0L).toList();
        int rowCapacity = Math.max(1,
                (getHeight() - 2 * Theme.SPACE_SM) / Math.max(1, BAR_HEIGHT + BAR_GAP));
        return visible.size() <= rowCapacity ? visible : visible.subList(0, rowCapacity);
    }

    /**
     * Returns the visible bar row at a y coordinate.
     *
     * @param y mouse y coordinate
     * @return bar, or null
     */
    private Bar barAt(int y) {
        List<Bar> visible = visibleBars();
        if (visible.isEmpty()) {
            return null;
        }
        int availableHeight = Math.max(1, getHeight() - 2 * Theme.SPACE_SM);
        int rowHeight = Math.max(BAR_HEIGHT + BAR_GAP, availableHeight / Math.max(1, visible.size()));
        int index = (y - Theme.SPACE_SM) / Math.max(1, rowHeight);
        return index >= 0 && index < visible.size() ? visible.get(index) : null;
    }

    /**
     * Returns the label-column width for the current component width.
     *
     * @return label width
     */
    private int labelWidth() {
        int dynamic = Math.max(LABEL_WIDTH, getWidth() / 5);
        return Math.min(dynamic, Math.max(72, getWidth() / 3));
    }

    /**
     * Paints one bar row.
     *
     * @param g graphics context
     * @param bar bar data
     * @param max maximum value
     * @param x row x coordinate
     * @param y row y coordinate
     * @param rowHeight row height in pixels
     * @param labelWidth label column width
     * @param barX bar x coordinate
     * @param barW available bar width
     * @param metrics font metrics
     */
    private void paintBarRow(Graphics2D g, Bar bar, long max, int x, int y, int rowHeight,
            int labelWidth, int barX, int barW, FontMetrics metrics) {
        int textY = y + (rowHeight + metrics.getAscent()) / 2 - 2;
        g.setColor(Theme.MUTED);
        g.drawString(Ui.elide(bar.label(), metrics, labelWidth - Theme.SPACE_SM), x, textY);

        int barY = y + Math.max(0, (rowHeight - BAR_HEIGHT) / 2);
        g.setColor(Theme.NN_NEUTRAL);
        g.fillRoundRect(barX, barY, barW, BAR_HEIGHT, Theme.RADIUS, Theme.RADIUS);
        int filled = (int) Math.round((double) bar.value() * (double) barW
                / (double) Math.max(1L, max) * Ui.easeOutCubic(barRevealProgress));
        g.setColor(roleColor(bar.role()));
        g.fillRoundRect(barX, barY, Math.max(1, Math.min(barW, filled)), BAR_HEIGHT, Theme.RADIUS, Theme.RADIUS);

        String value = formatCompactValue(bar.value());
        g.setColor(Theme.TEXT);
        g.drawString(value, getWidth() - VALUE_WIDTH + Theme.SPACE_XS, textY);
    }

    /**
     * Shared palette for all dataset chart variants. A missing role maps to
     * neutral so optional metrics do not accidentally render as primary accent.
     *
     * @param role chart role
     * @return color
     */
    static Color roleColor(Role role) {
        return switch (role == null ? Role.NEUTRAL : role) {
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
     * Shared compact value format for dataset charts. Four-digit counts remain
     * exact; suffixes start at {@code 10k} to keep small scan sizes readable.
     *
     * @param value count
     * @return formatted count
     */
    static String formatCompactValue(long value) {
        if (value >= 1_000_000L) {
            return String.format(java.util.Locale.ROOT, "%.1fm", value / 1_000_000.0d);
        }
        if (value >= 10_000L) {
            return String.format(java.util.Locale.ROOT, "%.1fk", value / 1_000.0d);
        }
        return Long.toString(value);
    }

}
