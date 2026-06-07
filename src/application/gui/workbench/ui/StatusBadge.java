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
     * Horizontal badge padding.
     */
    private static final int PAD_X = Theme.SPACE_SM;

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
     * Full status variant set used across Workbench surfaces.
     */
    public enum Variant {
        /**
         * Ready / idle but available.
         */
        READY,

        /**
         * Work in progress.
         */
        RUNNING,

        /**
         * Finished successfully.
         */
        COMPLETE,

        /**
         * Needs attention.
         */
        WARNING,

        /**
         * Failed.
         */
        ERROR,

        /**
         * Required resource is missing.
         */
        MISSING,

        /**
         * Has not run yet.
         */
        NOT_RUN,

        /**
         * Temporarily paused.
         */
        PAUSED,

        /**
         * Available information is stale.
         */
        STALE
    }

    /**
     * Current message text.
     */
    private String text = "";

    /**
     * Current message severity.
     */
    private Variant variant = Variant.NOT_RUN;

    /**
     * Dot color at the beginning of the current transition.
     */
    private Color transitionStartColor = dotColor(Variant.NOT_RUN);

    /**
     * Dot color at the end of the current transition.
     */
    private Color transitionTargetColor = dotColor(Variant.NOT_RUN);

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
        setFont(Theme.font(Theme.FONT_METADATA, Font.PLAIN));
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
        set(message, Variant.NOT_RUN);
    }

    /**
     * Shows a work-in-progress message.
     *
     * @param message message text
     */
    public void busy(String message) {
        set(message, Variant.RUNNING);
    }

    /**
     * Shows a success message.
     *
     * @param message message text
     */
    public void success(String message) {
        set(message, Variant.COMPLETE);
    }

    /**
     * Shows an error message.
     *
     * @param message message text
     */
    public void error(String message) {
        set(message, Variant.ERROR);
    }

    /**
     * Shows a ready message.
     *
     * @param message message text
     */
    public void ready(String message) {
        set(message, Variant.READY);
    }

    /**
     * Shows a running message.
     *
     * @param message message text
     */
    public void running(String message) {
        set(message, Variant.RUNNING);
    }

    /**
     * Shows a complete message.
     *
     * @param message message text
     */
    public void complete(String message) {
        set(message, Variant.COMPLETE);
    }

    /**
     * Shows a warning message.
     *
     * @param message message text
     */
    public void warning(String message) {
        set(message, Variant.WARNING);
    }

    /**
     * Shows a missing-resource message.
     *
     * @param message message text
     */
    public void missing(String message) {
        set(message, Variant.MISSING);
    }

    /**
     * Shows a not-run message.
     *
     * @param message message text
     */
    public void notRun(String message) {
        set(message, Variant.NOT_RUN);
    }

    /**
     * Shows a paused message.
     *
     * @param message message text
     */
    public void paused(String message) {
        set(message, Variant.PAUSED);
    }

    /**
     * Shows a stale message.
     *
     * @param message message text
     */
    public void stale(String message) {
        set(message, Variant.STALE);
    }

    /**
     * Sets the message text and severity together.
     *
     * @param message message text (null treated as empty)
     * @param newKind severity
     */
    public void set(String message, Kind newKind) {
        set(message, variantFor(newKind));
    }

    /**
     * Sets the message text and variant together.
     *
     * @param message message text (null treated as empty)
     * @param newVariant status variant
     */
    public void set(String message, Variant newVariant) {
        Variant nextVariant = newVariant == null ? Variant.NOT_RUN : newVariant;
        Color currentColor = currentDotColor();
        Dimension previousPreferredSize = getPreferredSize();
        this.text = message == null ? "" : message;
        if (nextVariant != variant) {
            transitionStartColor = currentColor;
            transitionTargetColor = dotColor(nextVariant);
            transitionProgress = 0.0d;
            transitionStartedAt = System.currentTimeMillis();
        }
        this.variant = nextVariant;
        if (variant == Variant.RUNNING && pulseStartedAt == 0L) {
            pulseStartedAt = System.currentTimeMillis();
        } else if (variant != Variant.RUNNING) {
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
        int width = PAD_X * 2 + DOT + GAP + textWidth;
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
            g.setColor(backgroundColor(variant));
            g.fillRoundRect(0, 3, Math.max(0, getWidth() - 1), Math.max(0, getHeight() - 7),
                    Theme.RADIUS, Theme.RADIUS);
            g.setColor(borderColor(variant));
            g.drawRoundRect(0, 3, Math.max(0, getWidth() - 1), Math.max(0, getHeight() - 7),
                    Theme.RADIUS, Theme.RADIUS);
            Color dot = currentDotColor();
            int size = animatedDotSize();
            int x = PAD_X + Math.max(0, (DOT - size) / 2);
            g.setColor(dot);
            g.fillOval(x, cy - size / 2, size, size);
            g.setColor(textColor(variant));
            int baseline = cy + fm.getAscent() / 2 - 1;
            int textX = PAD_X + DOT + GAP;
            int textWidth = Math.max(0, getWidth() - textX - PAD_X);
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
        boolean shouldRun = transitionProgress < 1.0d || variant == Variant.RUNNING && isShowing();
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
     * @param value status variant
     * @return active dot color
     */
    private static Color dotColor(Variant value) {
        return switch (value == null ? Variant.NOT_RUN : value) {
            case READY -> Theme.STATUS_READY_TEXT;
            case RUNNING -> Theme.STATUS_RUNNING_TEXT;
            case COMPLETE -> Theme.STATUS_COMPLETE_TEXT;
            case WARNING -> Theme.STATUS_WARNING_TEXT;
            case ERROR -> Theme.STATUS_ERROR_TEXT;
            case MISSING -> Theme.STATUS_MISSING_TEXT;
            case NOT_RUN -> Theme.STATUS_NOT_RUN_TEXT;
            case PAUSED -> Theme.STATUS_PAUSED_TEXT;
            case STALE -> Theme.STATUS_STALE_TEXT;
        };
    }

    /**
     * Returns a status variant for a legacy status kind.
     *
     * @param value legacy kind
     * @return status variant
     */
    private static Variant variantFor(Kind value) {
        return switch (value == null ? Kind.IDLE : value) {
            case BUSY -> Variant.RUNNING;
            case SUCCESS -> Variant.COMPLETE;
            case ERROR -> Variant.ERROR;
            case IDLE -> Variant.NOT_RUN;
        };
    }

    /**
     * Returns the variant background color.
     *
     * @param value status variant
     * @return background color
     */
    private static Color backgroundColor(Variant value) {
        return switch (value == null ? Variant.NOT_RUN : value) {
            case READY -> Theme.STATUS_READY_BG;
            case RUNNING -> Theme.STATUS_RUNNING_BG;
            case COMPLETE -> Theme.STATUS_COMPLETE_BG;
            case WARNING -> Theme.STATUS_WARNING_BG;
            case ERROR -> Theme.STATUS_ERROR_BG;
            case MISSING -> Theme.STATUS_MISSING_BG;
            case NOT_RUN -> Theme.STATUS_NOT_RUN_BG;
            case PAUSED -> Theme.STATUS_PAUSED_BG;
            case STALE -> Theme.STATUS_STALE_BG;
        };
    }

    /**
     * Returns the variant border color.
     *
     * @param value status variant
     * @return border color
     */
    private static Color borderColor(Variant value) {
        return switch (value == null ? Variant.NOT_RUN : value) {
            case READY -> Theme.STATUS_READY_BORDER;
            case RUNNING -> Theme.STATUS_RUNNING_BORDER;
            case COMPLETE -> Theme.STATUS_COMPLETE_BORDER;
            case WARNING -> Theme.STATUS_WARNING_BORDER;
            case ERROR -> Theme.STATUS_ERROR_BORDER;
            case MISSING -> Theme.STATUS_MISSING_BORDER;
            case NOT_RUN -> Theme.STATUS_NOT_RUN_BORDER;
            case PAUSED -> Theme.STATUS_PAUSED_BORDER;
            case STALE -> Theme.STATUS_STALE_BORDER;
        };
    }

    /**
     * Returns the variant text color.
     *
     * @param value status variant
     * @return text color
     */
    private static Color textColor(Variant value) {
        return switch (value == null ? Variant.NOT_RUN : value) {
            case READY -> Theme.STATUS_READY_TEXT;
            case RUNNING -> Theme.STATUS_RUNNING_TEXT;
            case COMPLETE -> Theme.STATUS_COMPLETE_TEXT;
            case WARNING -> Theme.STATUS_WARNING_TEXT;
            case ERROR -> Theme.STATUS_ERROR_TEXT;
            case MISSING -> Theme.STATUS_MISSING_TEXT;
            case NOT_RUN -> Theme.STATUS_NOT_RUN_TEXT;
            case PAUSED -> Theme.STATUS_PAUSED_TEXT;
            case STALE -> Theme.STATUS_STALE_TEXT;
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
