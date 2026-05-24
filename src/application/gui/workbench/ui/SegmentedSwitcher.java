/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package application.gui.workbench.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * Compact tab-style segmented selector.
 *
 * <p>Renders a horizontal strip of mutually-exclusive labelled buttons —
 * the current pick is highlighted with the workbench accent colour, and a
 * single click selects a new value and fires {@link ActionListener}s. Used
 * by the workbench toolbar to pick view modes without burying the choices
 * inside a dropdown.</p>
 */
public final class SegmentedSwitcher extends JComponent {

    /**
     * Serialization identifier for Swing component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Horizontal padding inside each segment.
     */
    private static final int PAD_X = 14;

    /**
     * Component height. Matches the shared compact-control height so the
     * switcher lines up with combos and toggles on a toolbar row.
     */
    private static final int HEIGHT = Theme.CONTROL_HEIGHT;

    /**
     * Animation frame cadence for the active segment underline.
     */
    private static final int ANIMATION_DELAY_MS = 16;

    /**
     * Duration for selection-underline travel in milliseconds.
     */
    private static final int SELECTION_ANIMATION_MS = 130;

    /**
     * Segment labels.
     */
    private final String[] labels;

    /**
     * Currently selected segment index.
     */
    private int selected;

    /**
     * Index of the segment under the cursor (-1 when none).
     */
    private int hovered = -1;

    /**
     * Whether each segment accepts input. All true by default.
     */
    private final boolean[] segmentEnabled;

    /**
     * Listeners registered with {@link #addActionListener(ActionListener)}.
     */
    private final List<ActionListener> listeners = new ArrayList<>();

    /**
     * Cached per-paint segment bounds, used by the click handler.
     */
    private Rectangle[] segmentBounds;

    /**
     * Animated underline x coordinate.
     */
    private int indicatorX;

    /**
     * Animated underline width.
     */
    private int indicatorWidth;

    /**
     * Underline x coordinate at the start of the active transition.
     */
    private int indicatorStartX;

    /**
     * Underline width at the start of the active transition.
     */
    private int indicatorStartWidth;

    /**
     * Underline target x coordinate.
     */
    private int indicatorTargetX;

    /**
     * Underline target width.
     */
    private int indicatorTargetWidth;

    /**
     * Wall-clock start time for the active selection animation.
     */
    private long indicatorAnimationStartedAt;

    /**
     * Timer driving the active-segment underline.
     */
    private final Timer selectionTimer = new Timer(ANIMATION_DELAY_MS, event -> tickSelectionAnimation());

    /**
     * Creates a segmented switcher.
     *
     * @param labels segment labels
     */
    public SegmentedSwitcher(String[] labels) {
        this.labels = labels.clone();
        this.segmentEnabled = new boolean[labels.length];
        for (int i = 0; i < labels.length; i++) {
            this.segmentEnabled[i] = true;
        }
        this.selected = 0;
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        selectionTimer.setCoalesce(true);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                onPress(event.getX(), event.getY());
            }

            @Override
            public void mouseExited(MouseEvent event) {
                if (hovered != -1) {
                    hovered = -1;
                    repaint();
                }
            }
        });
        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                updateHover(event.getX(), event.getY());
            }
        });
    }

    /**
     * Returns the selected segment index.
     *
     * @return selection index
     */
    public int getSelectedIndex() {
        return selected;
    }

    /**
     * Selects a segment programmatically, firing listeners if it changes.
     *
     * @param index segment index
     */
    public void setSelectedIndex(int index) {
        if (index < 0 || index >= labels.length || selected == index) {
            return;
        }
        selected = index;
        startSelectionAnimation();
        fire();
    }

    /**
     * Stops animation when the switcher leaves the component tree.
     */
    @Override
    public void removeNotify() {
        selectionTimer.stop();
        super.removeNotify();
    }

    /**
     * Enables or disables a specific segment.
     *
     * @param index segment index
     * @param value true to enable
     */
    public void setSegmentEnabled(int index, boolean value) {
        if (index < 0 || index >= segmentEnabled.length) {
            return;
        }
        if (segmentEnabled[index] != value) {
            segmentEnabled[index] = value;
            repaint();
        }
    }

    /**
     * Registers a listener fired whenever the selection changes via click or
     * {@link #setSelectedIndex(int)}.
     *
     * @param listener listener
     */
    public void addActionListener(ActionListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /**
     * Returns the preferred size based on label widths.
     *
     * @return preferred size
     */
    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(Theme.font(13, Font.BOLD));
        int total = 0;
        for (String label : labels) {
            total += fm.stringWidth(label) + PAD_X * 2;
        }
    return new Dimension(total, HEIGHT);
    }

    /**
     * Paints the segmented strip.
     *
     * @param graphics graphics context
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(Theme.font(12, Font.BOLD));
            FontMetrics fm = g.getFontMetrics();
            int x = 0;
            segmentBounds = new Rectangle[labels.length];
            int height = getHeight();
            for (int i = 0; i < labels.length; i++) {
                int w = fm.stringWidth(labels[i]) + PAD_X * 2;
                Rectangle r = new Rectangle(x, 0, w, height);
                segmentBounds[i] = r;
                paintSegment(g, r, i, fm);
                x += w;
            }
            paintSelectionIndicator(g, height);
        } finally {
            g.dispose();
        }
    }

    /**
     * Paints one segment cell.
     *
     * @param g graphics
     * @param r segment rectangle
     * @param index segment index
     * @param fm font metrics for label centering
     */
    private void paintSegment(Graphics2D g, Rectangle r, int index, FontMetrics fm) {
        boolean isSelected = index == selected;
        boolean isHovered = index == hovered;
        boolean isEnabled = segmentEnabled[index];
        // Match the workbench tab look: a quiet strip where the active
        // segment is a light accent-tinted pill rather than a solid block.
        Color fill = Theme.ELEVATED_SOLID;
        if (isSelected) {
            fill = Theme.SELECTION_SOLID;
        } else if (isHovered && isEnabled) {
            fill = Theme.TAB_HOVER;
        }
        g.setColor(fill);
        int arcLeft = index == 0 ? Theme.RADIUS : 0;
        int arcRight = index == labels.length - 1 ? Theme.RADIUS : 0;
        fillRoundedSegment(g, r, arcLeft, arcRight);
        g.setColor(Theme.LINE);
        drawRoundedSegmentBorder(g, r, arcLeft, arcRight, index == labels.length - 1);
        Color textColor;
        if (!isEnabled) {
            textColor = Theme.BUTTON_DISABLED_TEXT;
        } else if (isSelected) {
            textColor = Theme.STATUS_INFO_TEXT;
        } else {
            textColor = Theme.MUTED;
        }
        g.setColor(textColor);
        int textY = r.y + (r.height - fm.getHeight()) / 2 + fm.getAscent() - 1;
        g.drawString(labels[index], r.x + PAD_X, textY);
    }

    /**
     * Paints the animated active-segment underline.
     *
     * @param g graphics
     * @param height component height
     */
    private void paintSelectionIndicator(Graphics2D g, int height) {
        Rectangle selectedBounds = selectedBounds();
        if (selectedBounds == null || !segmentEnabled[selected]) {
            return;
        }
        if (!selectionTimer.isRunning() || indicatorWidth <= 0) {
            indicatorX = selectedBounds.x;
            indicatorWidth = selectedBounds.width;
        }
        g.setColor(Theme.ACCENT);
        g.fillRect(indicatorX, height - 2, indicatorWidth, 2);
    }

    /**
     * Fills a segment with the right-side corners rounded only for the
     * trailing segment so the strip reads as a single connected control.
     *
     * @param g graphics
     * @param r segment rectangle
     * @param arcLeft arc radius on the left side (0 for non-leading)
     * @param arcRight arc radius on the right side (0 for non-trailing)
     */
    private static void fillRoundedSegment(Graphics2D g, Rectangle r, int arcLeft, int arcRight) {
        if (arcLeft == 0 && arcRight == 0) {
            g.fillRect(r.x, r.y, r.width, r.height);
            return;
        }
        g.fillRoundRect(r.x, r.y, r.width, r.height,
                Math.max(arcLeft, arcRight) * 2, Math.max(arcLeft, arcRight) * 2);
        if (arcLeft == 0) {
            g.fillRect(r.x, r.y, r.width / 2, r.height);
        } else if (arcRight == 0) {
            g.fillRect(r.x + r.width / 2, r.y, r.width / 2, r.height);
        }
    }

    /**
     * Draws the border around a segment. Internal seams (between segments)
     * are drawn as a single line so adjacent segments share their edge.
     *
     * @param g graphics
     * @param r segment rectangle
     * @param arcLeft arc radius on the left side
     * @param arcRight arc radius on the right side
     * @param last true for the trailing segment
     */
    private static void drawRoundedSegmentBorder(Graphics2D g, Rectangle r,
            int arcLeft, int arcRight, boolean last) {
        if (arcLeft == 0 && arcRight == 0) {
            g.drawLine(r.x + r.width - 1, r.y, r.x + r.width - 1, r.y + r.height - 1);
            g.drawLine(r.x, r.y, r.x + r.width - 1, r.y);
            g.drawLine(r.x, r.y + r.height - 1, r.x + r.width - 1, r.y + r.height - 1);
            return;
        }
        g.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1,
                Math.max(arcLeft, arcRight) * 2, Math.max(arcLeft, arcRight) * 2);
        if (!last) {
            // Right edge: draw a straight seam to join the next segment.
            g.drawLine(r.x + r.width - 1, r.y, r.x + r.width - 1, r.y + r.height - 1);
        }
    }

    /**
     * Handles a mouse press, updating the selection when it lands on an
     * enabled segment.
     *
     * @param x x
     * @param y y
     */
    private void onPress(int x, int y) {
        if (segmentBounds == null) {
            return;
        }
        for (int i = 0; i < segmentBounds.length; i++) {
            if (segmentBounds[i] != null && segmentBounds[i].contains(x, y)) {
                if (segmentEnabled[i] && selected != i) {
                    selected = i;
                    startSelectionAnimation();
                    fire();
                }
                return;
            }
        }
    }

    /**
     * Updates the hovered segment index and triggers a repaint when it
     * changes.
     *
     * @param x x
     * @param y y
     */
    private void updateHover(int x, int y) {
        if (segmentBounds == null) {
            return;
        }
        int next = -1;
        for (int i = 0; i < segmentBounds.length; i++) {
            if (segmentBounds[i] != null && segmentBounds[i].contains(x, y)) {
                next = i;
                break;
            }
        }
        if (next != hovered) {
            hovered = next;
            repaint();
        }
    }

    /**
     * Fires action listeners with a synthetic action event.
     */
    private void fire() {
        ActionEvent event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED,
                Integer.toString(selected));
        for (ActionListener listener : listeners) {
            listener.actionPerformed(event);
        }
    }

    /**
     * Starts an underline animation toward the current selection.
     */
    private void startSelectionAnimation() {
        Rectangle target = selectedBounds();
        if (target == null) {
            repaint();
            return;
        }
        if (indicatorWidth <= 0) {
            indicatorX = target.x;
            indicatorWidth = target.width;
        }
        indicatorStartX = indicatorX;
        indicatorStartWidth = indicatorWidth;
        indicatorTargetX = target.x;
        indicatorTargetWidth = target.width;
        indicatorAnimationStartedAt = System.currentTimeMillis();
        if (!selectionTimer.isRunning()) {
            selectionTimer.start();
        }
        repaint();
    }

    /**
     * Advances the active underline animation.
     */
    private void tickSelectionAnimation() {
        double progress = Math.min(1.0d,
                (System.currentTimeMillis() - indicatorAnimationStartedAt)
                        / (double) SELECTION_ANIMATION_MS);
        double eased = Ui.easeOutCubic(progress);
        indicatorX = interpolate(indicatorStartX, indicatorTargetX, eased);
        indicatorWidth = interpolate(indicatorStartWidth, indicatorTargetWidth, eased);
        if (progress >= 1.0d) {
            selectionTimer.stop();
            indicatorX = indicatorTargetX;
            indicatorWidth = indicatorTargetWidth;
        }
        repaint();
    }

    /**
     * Returns the current selected segment bounds.
     *
     * @return selected bounds, or null before the first layout pass
     */
    private Rectangle selectedBounds() {
        if (segmentBounds == null || selected < 0 || selected >= segmentBounds.length) {
            return null;
        }
        return segmentBounds[selected];
    }

    /**
     * Interpolates an integer value.
     *
     * @param from start value
     * @param to target value
     * @param progress progress from 0 to 1
     * @return interpolated value
     */
    private static int interpolate(int from, int to, double progress) {
        return (int) Math.round(from + (to - from) * progress);
    }
}
