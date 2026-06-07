package application.gui.workbench.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import javax.swing.JButton;
import javax.swing.Timer;

/**
 * Rounded workbench button that paints a flat chip with a shared hover/press
 * fill animation.
 */
final class StyledButton extends JButton {

    /**
     * Serialization identifier for Swing button compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Button corner radius. Aligned with {@link Theme#RADIUS} so buttons keep the
     * softer VS Code macOS control shape without becoming pills.
     */
    private static final int RADIUS = Theme.RADIUS;

    /**
     * Live set of buttons currently animating; ticked by the shared timer.
     */
    private static final Set<StyledButton> ACTIVE_BUTTONS =
            Collections.newSetFromMap(new WeakHashMap<>());

    /**
     * Shared 60 fps animation timer that advances all active buttons.
     */
    private static final Timer SHARED_FILL_TIMER = createSharedFillTimer();

    /**
     * Whether this button is currently being ticked by the shared timer.
     */
    private boolean fillRunning;

    /**
     * Fill color at the start of the current transition.
     */
    private Color transitionStartFill;

    /**
     * Desired fill color at the end of the current transition.
     */
    private Color transitionTargetFill;

    /**
     * Current interpolated fill color.
     */
    private Color animatedFill;

    /**
     * Start time for the current fill transition.
     */
    private long fillTransitionStartedAt;

    /**
     * Creates a rounded chip button.
     *
     * @param text button text
     */
    StyledButton(String text) {
        super(text);
        setContentAreaFilled(false);
        setOpaque(false);
        setRolloverEnabled(true);
        getModel().addChangeListener(event -> startFillTransition());
    }

    /**
     * Floors the button height at the shared control height so every text button
     * lands on the same 32px baseline as the combos, spinners, and fields beside
     * it. Only grows short buttons; an explicit preferred size passes through
     * unchanged.
     *
     * @return the preferred size with a control-height floor
     */
    @Override
    public Dimension getPreferredSize() {
        Dimension preferred = super.getPreferredSize();
        return new Dimension(preferred.width,
                Math.max(preferred.height, Theme.CONTROL_HEIGHT));
    }

    /**
     * Creates and returns the shared 60 fps animation timer.
     *
     * @return shared coalescing timer
     */
    private static Timer createSharedFillTimer() {
        Timer timer = new Timer(Ui.ANIMATION_DELAY_MS, event -> {
            if (ACTIVE_BUTTONS.isEmpty()) {
                ((Timer) event.getSource()).stop();
                return;
            }
            for (StyledButton button : ACTIVE_BUTTONS.toArray(new StyledButton[0])) {
                button.tickFillTransition();
            }
        });
        timer.setCoalesce(true);
        return timer;
    }

    /**
     * Joins the shared animation tick set.
     */
    private void joinAnimation() {
        if (fillRunning) {
            return;
        }
        fillRunning = true;
        ACTIVE_BUTTONS.add(this);
        if (!SHARED_FILL_TIMER.isRunning()) {
            SHARED_FILL_TIMER.start();
        }
    }

    /**
     * Leaves the shared animation tick set.
     */
    private void leaveAnimation() {
        if (!fillRunning) {
            return;
        }
        fillRunning = false;
        ACTIVE_BUTTONS.remove(this);
    }

    /**
     * Returns whether this button is currently animating.
     *
     * @return true while the button participates in the shared tick
     */
    private boolean isFillRunning() {
        return fillRunning;
    }

    /**
     * Paints a rounded button body.
     *
     * @param graphics graphics context
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Theme.ButtonVariant variant = Theme.buttonVariant(this);
            Color fill = buttonFill(variant);
            Color border = isEnabled() ? Theme.buttonBorder(variant) : Theme.BUTTON_DISABLED_BORDER;
            setForeground(isEnabled()
                    ? Theme.buttonText(variant)
                    : Theme.BUTTON_DISABLED_TEXT);
            g.setColor(fill);
            g.fillRoundRect(0, 0, Math.max(0, getWidth() - 1), Math.max(0, getHeight() - 1), RADIUS, RADIUS);
            g.setColor(border);
            g.drawRoundRect(0, 0, Math.max(0, getWidth() - 1), Math.max(0, getHeight() - 1), RADIUS, RADIUS);
            if (isFocusOwner()) {
                g.setColor(Theme.FOCUS_RING);
                g.drawRoundRect(2, 2, Math.max(0, getWidth() - 5), Math.max(0, getHeight() - 5), RADIUS, RADIUS);
            }
        } finally {
            g.dispose();
        }
        super.paintComponent(graphics);
    }

    /**
     * Returns the current flat button fill.
     *
     * @param variant button hierarchy variant
     * @return fill color
     */
    private Color buttonFill(Theme.ButtonVariant variant) {
        Color desired = desiredButtonFill(variant);
        boolean targetChangedWhileIdle = !sameColor(desired, transitionTargetFill) && !isFillRunning();
        if (animatedFill == null || targetChangedWhileIdle) {
            animatedFill = desired;
            transitionTargetFill = desired;
        }
        return animatedFill == null ? desired : animatedFill;
    }

    /**
     * Starts a color transition toward the current button state.
     */
    private void startFillTransition() {
        Theme.ButtonVariant variant = Theme.buttonVariant(this);
        Color desired = desiredButtonFill(variant);
        Color current = animatedFill == null ? restingButtonFill(variant) : animatedFill;
        boolean alreadyAtTarget = sameColor(desired, transitionTargetFill);
        boolean unchangedWhileIdle = sameColor(desired, current) && !isFillRunning();
        if (alreadyAtTarget || unchangedWhileIdle) {
            transitionTargetFill = desired;
            animatedFill = current;
            repaint();
            return;
        }
        transitionStartFill = current;
        transitionTargetFill = desired;
        fillTransitionStartedAt = System.currentTimeMillis();
        joinAnimation();
        repaint();
    }

    /**
     * Advances the fill transition.
     */
    private void tickFillTransition() {
        if (transitionStartFill == null || transitionTargetFill == null) {
            leaveAnimation();
            return;
        }
        double progress = clamp((System.currentTimeMillis() - fillTransitionStartedAt)
                / (double) Ui.BUTTON_TRANSITION_MS, 0.0, 1.0);
        animatedFill = blend(transitionStartFill, transitionTargetFill, Ui.easeOutCubic(progress));
        if (progress >= 1.0) {
            animatedFill = transitionTargetFill;
            leaveAnimation();
        }
        repaint();
    }

    /**
     * Returns the desired fill for the current button state.
     *
     * @param variant button hierarchy variant
     * @return desired fill color
     */
    private Color desiredButtonFill(Theme.ButtonVariant variant) {
        if (!isEnabled()) {
            return Theme.BUTTON_DISABLED_BG;
        }
        if (getModel().isPressed()) {
            return Theme.buttonPressed(variant);
        }
        if (getModel().isRollover()) {
            return Theme.buttonHover(variant);
        }
        return Theme.buttonBackground(variant);
    }

    /**
     * Returns the non-hover button fill for transition starts.
     *
     * @param variant button hierarchy variant
     * @return resting fill color
     */
    private Color restingButtonFill(Theme.ButtonVariant variant) {
        return isEnabled() ? Theme.buttonBackground(variant) : Theme.BUTTON_DISABLED_BG;
    }

    /**
     * Blends two colors.
     *
     * @param from start color
     * @param to target color
     * @param progress blend progress
     * @return interpolated color
     */
    private static Color blend(Color from, Color to, double progress) {
        double amount = clamp(progress, 0.0, 1.0);
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
     * @param to target channel
     * @param progress blend progress
     * @return interpolated channel
     */
    private static int blendChannel(int from, int to, double progress) {
        return (int) Math.round(from + (to - from) * progress);
    }

    /**
     * Returns whether two colors are exactly equal, with null handling.
     *
     * @param left first color
     * @param right second color
     * @return true when both colors match
     */
    private static boolean sameColor(Color left, Color right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.getRGB() == right.getRGB();
    }

    /**
     * Clamps a value.
     *
     * @param value value
     * @param min minimum
     * @param max maximum
     * @return clamped value
     */
    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
