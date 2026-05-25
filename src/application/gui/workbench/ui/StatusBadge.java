package application.gui.workbench.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.RenderingHints;
import java.awt.event.HierarchyEvent;
import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * Compact status indicator for the workbench network toolbar.
 *
 * <p>Replaces the previous bare italic {@link javax.swing.JLabel}: a small
 * coloured state dot plus a short message, so the user can tell at a glance
 * whether the panel is idle, working, finished, or in error — instead of
 * having to read grey italic text in the corner.</p>
 */
public final class StatusBadge extends JComponent {

    /**
     * Serialization identifier for Swing component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Diameter of the state dot in pixels.
     */
    private static final int DOT = 8;

    /**
     * Gap between the dot and the message text.
     */
    private static final int GAP = Theme.SPACE_SM;

    /**
     * Animation frame cadence for status-dot transitions.
     */
    private static final int ANIMATION_DELAY_MS = 16;

    /**
     * Duration for status-color easing in milliseconds.
     */
    private static final int TRANSITION_MS = 120;

    /**
     * The severity of a status message, which selects the dot colour.
     */
    public enum Kind {

        /**
         * Idle / informational — a muted dot.
         */
        IDLE,

        /**
         * Work in progress — an accent dot.
         */
        BUSY,

        /**
         * Finished successfully — a green dot.
         */
        SUCCESS,

        /**
         * Failed — a red dot.
         */
        ERROR
    }

    /**
     * Current message text.
     */
    private String text = "";

    /**
     * Current message severity.
     */
    private Kind kind = Kind.IDLE;

    /**
     * Dot color at the beginning of the current transition.
     */
    private Color transitionStartColor = dotColor(Kind.IDLE);

    /**
     * Dot color at the end of the current transition.
     */
    private Color transitionTargetColor = dotColor(Kind.IDLE);

    /**
     * Wall-clock start time for the current color transition.
     */
    private long transitionStartedAt;

    /**
     * Current transition progress from 0.0 to 1.0.
     */
    private double transitionProgress = 1.0d;

    /**
     * Wall-clock start time for the busy pulse.
     */
    private long pulseStartedAt;

    /**
     * Timer driving color easing and busy-state pulse repainting.
     */
    private final Timer animationTimer = new Timer(ANIMATION_DELAY_MS, event -> tickAnimation());

    /**
     * Reserved message width in pixels, or zero when the badge sizes to the
     * current text.
     */
    private int fixedTextWidth;

    /**
     * Creates an empty status badge.
     */
    public StatusBadge() {
        setOpaque(false);
        setFont(Theme.font(11, Font.PLAIN));
        animationTimer.setCoalesce(true);
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0L) {
                updateAnimationTimer();
            }
        });
    }

    /**
     * Shows an idle / informational message.
     *
     * @param message message text
     */
    public void idle(String message) {
        set(message, Kind.IDLE);
    }

    /**
     * Shows a work-in-progress message.
     *
     * @param message message text
     */
    public void busy(String message) {
        set(message, Kind.BUSY);
    }

    /**
     * Shows a success message.
     *
     * @param message message text
     */
    public void success(String message) {
        set(message, Kind.SUCCESS);
    }

    /**
     * Shows an error message.
     *
     * @param message message text
     */
    public void error(String message) {
        set(message, Kind.ERROR);
    }

    /**
     * Sets the message text and severity together.
     *
     * @param message message text (null treated as empty)
     * @param newKind severity
     */
    public void set(String message, Kind newKind) {
        Kind nextKind = newKind == null ? Kind.IDLE : newKind;
        Color currentColor = currentDotColor();
        Dimension previousPreferredSize = getPreferredSize();
        this.text = message == null ? "" : message;
        if (nextKind != kind) {
            transitionStartColor = currentColor;
            transitionTargetColor = dotColor(nextKind);
            transitionProgress = 0.0d;
            transitionStartedAt = System.currentTimeMillis();
        }
        this.kind = nextKind;
        if (kind == Kind.BUSY && pulseStartedAt == 0L) {
            pulseStartedAt = System.currentTimeMillis();
        } else if (kind != Kind.BUSY) {
            pulseStartedAt = 0L;
        }
        updateAnimationTimer();
        if (!previousPreferredSize.equals(getPreferredSize())) {
            revalidate();
        }
        repaint();
    }

    /**
     * Reserves a fixed message lane. This prevents high-frequency status
     * streams from shifting neighboring toolbar controls as the text changes.
     *
     * @param width text lane width in pixels, or zero to size to text
     */
    public void setFixedTextWidth(int width) {
        int nextWidth = Math.max(0, width);
        if (fixedTextWidth == nextWidth) {
            return;
        }
        fixedTextWidth = nextWidth;
        revalidate();
        repaint();
    }

    /**
     * Stops the animation timer when the badge leaves the component tree.
     */
    @Override
    public void removeNotify() {
        animationTimer.stop();
        super.removeNotify();
    }

    /**
     * Restarts the busy pulse when an already-busy badge becomes visible.
     */
    @Override
    public void addNotify() {
        super.addNotify();
        updateAnimationTimer();
    }

    /**
     * Returns the preferred size from the current text plus the dot and gap.
     *
     * @return preferred size
     */
    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        int textWidth = fixedTextWidth > 0 ? fixedTextWidth : fm.stringWidth(text);
        int width = DOT + GAP + textWidth;
        return new Dimension(width, Math.max(Theme.CONTROL_HEIGHT, fm.getHeight()));
    }

    /**
     * Paints the state dot and the message text.
     *
     * @param graphics graphics context
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        if (text.isEmpty()) {
            return;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setFont(getFont());
            FontMetrics fm = g.getFontMetrics();
            int cy = getHeight() / 2;
            Color dot = currentDotColor();
            int size = animatedDotSize();
            int x = Math.max(0, (DOT - size) / 2);
            g.setColor(dot);
            g.fillOval(x, cy - size / 2, size, size);
            g.setColor(Theme.MUTED);
            int baseline = cy + fm.getAscent() / 2 - 1;
            int textX = DOT + GAP;
            int textWidth = Math.max(0, getWidth() - textX);
            g.drawString(Ui.elide(text, fm, textWidth), textX, baseline);
        } finally {
            g.dispose();
        }
    }

    /**
     * Advances the status color and pulse animation.
     */
    private void tickAnimation() {
        if (transitionProgress < 1.0d) {
            transitionProgress = Math.min(1.0d,
                    (System.currentTimeMillis() - transitionStartedAt) / (double) TRANSITION_MS);
        }
        repaint();
        updateAnimationTimer();
    }

    /**
     * Starts or stops the animation timer based on active visual work.
     */
    private void updateAnimationTimer() {
        boolean shouldRun = transitionProgress < 1.0d || kind == Kind.BUSY && isShowing();
        if (shouldRun && !animationTimer.isRunning()) {
            animationTimer.start();
        } else if (!shouldRun && animationTimer.isRunning()) {
            animationTimer.stop();
        }
    }

    /**
     * Returns the current animated status-dot color.
     *
     * @return interpolated dot color
     */
    private Color currentDotColor() {
        return blend(transitionStartColor, transitionTargetColor, Ui.easeOutCubic(transitionProgress));
    }

    /**
     * Returns the status-dot size in pixels. Constant across all states; the
     * accent colour alone communicates BUSY. The previous implementation
     * pulsed the diameter ±1px on a 900ms sine wave, which read as the badge
     * scaling weirdly on every tick.
     *
     * @return dot size in pixels
     */
    private int animatedDotSize() {
        return DOT;
    }

    /**
     * Returns the active theme dot color for a status kind.
     *
     * @param value status kind
     * @return active dot color
     */
    private static Color dotColor(Kind value) {
        return switch (value == null ? Kind.IDLE : value) {
            case BUSY -> Theme.ACCENT;
            case SUCCESS -> Theme.STATUS_SUCCESS_TEXT;
            case ERROR -> Theme.STATUS_ERROR_TEXT;
            case IDLE -> Theme.MUTED;
        };
    }

    /**
     * Blends two colors.
     *
     * @param from start color
     * @param to end color
     * @param progress progress from 0 to 1
     * @return blended color
     */
    private static Color blend(Color from, Color to, double progress) {
        double amount = Math.max(0.0d, Math.min(1.0d, progress));
        return new Color(
                blendChannel(from.getRed(), to.getRed(), amount),
                blendChannel(from.getGreen(), to.getGreen(), amount),
                blendChannel(from.getBlue(), to.getBlue(), amount),
                blendChannel(from.getAlpha(), to.getAlpha(), amount));
    }

    /**
     * Blends one color channel.
     *
     * @param from start channel
     * @param to end channel
     * @param progress progress from 0 to 1
     * @return blended channel
     */
    private static int blendChannel(int from, int to, double progress) {
        return (int) Math.round(from + (to - from) * progress);
    }
}
