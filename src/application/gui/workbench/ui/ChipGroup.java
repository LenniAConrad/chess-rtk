package application.gui.workbench.ui;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.IntConsumer;
import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * Animated single-choice chip selector — a row of pill buttons with an accent
 * indicator that slides between them. Replaces a radio-button group: the
 * mutually-exclusive choice is impossible to break and the selection animates.
 */
public final class ChipGroup extends JComponent {

    /**
     * Serialization identifier for Swing component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Component height. Matches the shared compact-control height so chips line
     * up with combos, toggles, and the segmented switcher on a toolbar row.
     */
    private static final int HEIGHT = Theme.CONTROL_HEIGHT;

    /**
     * Horizontal text padding inside a chip.
     */
    private static final int PAD = 13;

    /**
     * Animation duration.
     */
    private static final int ANIMATION_MS = 150;

    /**
     * Chip labels.
     */
    private final transient List<String> labels;

    /**
     * Per-chip left edge.
     */
    private final int[] chipX;

    /**
     * Per-chip width.
     */
    private final int[] chipW;

    /**
     * Selected chip index.
     */
    private int selected;

    /**
     * Selection callback.
     */
    private transient IntConsumer onSelect = index -> {
        // no-op until wired
    };

    /**
     * Animated indicator left edge.
     */
    private double indicatorX;

    /**
     * Animated indicator width.
     */
    private double indicatorW;

    /**
     * Indicator edge at the start of the current animation.
     */
    private double fromX;

    /**
     * Indicator width at the start of the current animation.
     */
    private double fromW;

    /**
     * Animation start timestamp.
     */
    private long animationStart;

    /**
     * Slide animation timer.
     */
    private final transient Timer animator;

    /**
     * Creates a chip group.
     *
     * @param labels chip labels (at least one)
     */
    public ChipGroup(List<String> labels) {
        this.labels = List.copyOf(labels);
        this.chipX = new int[this.labels.size()];
        this.chipW = new int[this.labels.size()];
        setOpaque(false);
        layoutChips();
        indicatorX = chipX[0];
        indicatorW = chipW[0];
        animator = new Timer(16, event -> tick());
        animator.setCoalesce(true);
        addMouseListener(new MouseAdapter() {
            /**
             * {@inheritDoc}
             */
            @Override
            public void mousePressed(MouseEvent event) {
                int hit = chipAt(event.getX());
                if (hit >= 0 && hit != selected) {
                    setSelectedIndex(hit);
                    onSelect.accept(hit);
                }
            }
        });
    }

    /**
     * Stops the animation timer when detached.
     */
    @Override
    public void removeNotify() {
        animator.stop();
        super.removeNotify();
    }

    /**
     * Sets the selection callback.
     *
     * @param listener callback receiving the chosen index
     */
    public void setOnSelect(IntConsumer listener) {
        onSelect = listener == null ? index -> {
            // no-op
        } : listener;
    }

    /**
     * Returns the selected chip index.
     *
     * @return selected index
     */
    public int getSelectedIndex() {
        return selected;
    }

    /**
     * Selects a chip and animates the indicator to it.
     *
     * @param index chip index
     */
    public void setSelectedIndex(int index) {
        if (index < 0 || index >= labels.size() || index == selected) {
            return;
        }
        selected = index;
        fromX = indicatorX;
        fromW = indicatorW;
        animationStart = System.currentTimeMillis();
        if (!animator.isRunning()) {
            animator.start();
        }
        repaint();
    }

    /**
     * Advances the slide animation.
     */
    private void tick() {
        double progress = Math.min(1.0,
                (System.currentTimeMillis() - animationStart) / (double) ANIMATION_MS);
        double eased = progress < 0.5
                ? 4.0 * progress * progress * progress
                : 1.0 - Math.pow(-2.0 * progress + 2.0, 3.0) / 2.0;
        indicatorX = fromX + (chipX[selected] - fromX) * eased;
        indicatorW = fromW + (chipW[selected] - fromW) * eased;
        if (progress >= 1.0) {
            indicatorX = chipX[selected];
            indicatorW = chipW[selected];
            animator.stop();
        }
        repaint();
    }

    /**
     * Computes per-chip bounds from the label widths.
     */
    private void layoutChips() {
        FontMetrics fm = getFontMetrics(Theme.font(12, Font.BOLD));
        int x = 0;
        for (int i = 0; i < labels.size(); i++) {
            chipW[i] = fm.stringWidth(labels.get(i)) + PAD * 2;
            chipX[i] = x;
            x += chipW[i];
        }
    }

    /**
     * Returns the chip index at an x coordinate, or -1.
     *
     * @param x x coordinate
     * @return chip index
     */
    private int chipAt(int x) {
        for (int i = 0; i < labels.size(); i++) {
            if (x >= chipX[i] && x < chipX[i] + chipW[i]) {
                return i;
            }
        }
        return -1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Dimension getPreferredSize() {
        int width = chipX[labels.size() - 1] + chipW[labels.size() - 1];
    return new Dimension(width, HEIGHT);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Dimension getMinimumSize() {
    return getPreferredSize();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Dimension getMaximumSize() {
    return getPreferredSize();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getPreferredSize().width;
            int h = HEIGHT;
            // VS Code-style segmented input: white field, hairline border,
            // and a pale active option instead of a saturated pill.
            g.setColor(Theme.INPUT);
            g.fillRoundRect(0, 0, w, h, Theme.RADIUS, Theme.RADIUS);
            g.setColor(Theme.INPUT_BORDER);
            g.drawRoundRect(0, 0, w - 1, h - 1, Theme.RADIUS, Theme.RADIUS);
            for (int i = 1; i < labels.size(); i++) {
                g.setColor(Theme.LINE);
                g.drawLine(chipX[i], 4, chipX[i], h - 5);
            }
            // Sliding active option.
            int innerArc = Math.max(3, Theme.RADIUS - 2);
            g.setColor(Theme.SELECTION_SOLID);
            g.fillRoundRect((int) Math.round(indicatorX) + 2, 2,
                    (int) Math.round(indicatorW) - 4, h - 4, innerArc, innerArc);
            g.setColor(Theme.ACCENT);
            g.drawRoundRect((int) Math.round(indicatorX) + 2, 2,
                    (int) Math.round(indicatorW) - 5, h - 5, innerArc, innerArc);
            // Labels.
            g.setFont(Theme.font(12, Font.BOLD));
            FontMetrics fm = g.getFontMetrics();
            for (int i = 0; i < labels.size(); i++) {
                g.setColor(i == selected ? Theme.TEXT : Theme.MUTED);
                String label = labels.get(i);
                int tx = chipX[i] + (chipW[i] - fm.stringWidth(label)) / 2;
                int ty = (h + fm.getAscent() - fm.getDescent()) / 2;
                g.drawString(label, tx, ty);
            }
        } finally {
            g.dispose();
        }
    }
}
