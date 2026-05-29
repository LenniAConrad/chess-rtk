package application.gui.workbench.ui;

import application.gui.workbench.audio.SoundCue;
import application.gui.workbench.audio.SoundService;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.RenderingHints;
import javax.swing.JCheckBox;
import javax.swing.Timer;

/**
 * Compact switch-style checkbox for workbench boolean options.
 */
public final class ToggleBox extends JCheckBox {

    /**
     * Serialization identifier for Swing checkbox compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Outer horizontal padding.
     */
    private static final int PAD_X = 10;

    /**
     * Gap between label and switch.
     */
    private static final int LABEL_GAP = 9;

    /**
     * Switch track width.
     */
    private static final int TRACK_WIDTH = 38;

    /**
     * Switch track height.
     */
    private static final int TRACK_HEIGHT = 20;

    /**
     * Switch thumb size.
     */
    private static final int THUMB_SIZE = 14;

    /**
     * Toggle animation frame delay.
     */
    private static final int ANIMATION_DELAY_MS = 16;

    /**
     * Toggle animation duration. Short enough to feel immediate while still
     * making the state change visible.
     */
    private static final int ANIMATION_DURATION_MS = 130;

    /**
     * Component height.
     */
    private static final int HEIGHT = Theme.CONTROL_HEIGHT;

    /**
     * Minimum row width that keeps settings labels readable. Only applied in
     * full-width (non-compact) mode.
     */
    private static final int MIN_WIDTH = 320;

    /**
     * When true the toggle sizes to its content instead of stretching to
     * {@link #MIN_WIDTH}. Used for toolbar toggles, where a 320px-wide switch
     * would dominate the control row.
     */
    private final boolean compact;

    /**
     * Timer driving the switch thumb/track transition.
     */
    private final Timer animationTimer;

    /**
     * Current visual on/off progress: 0.0 = off, 1.0 = on.
     */
    private double visualProgress;

    /**
     * Progress value at the start of the current animation.
     */
    private double animationStartProgress;

    /**
     * Target progress value for the current animation.
     */
    private double animationTargetProgress;

    /**
     * Wall-clock start time for the current animation.
     */
    private long animationStartedAt;

    /**
     * Creates a full-width toggle checkbox suitable for a settings list.
     *
     * @param text toggle label
     */
    public ToggleBox(String text) {
        this(text, false);
    }

    /**
     * Creates a toggle checkbox.
     *
     * @param text toggle label
     * @param compact true to size to content (toolbar use), false to stretch
     *     to a settings-row width
     */
    public ToggleBox(String text, boolean compact) {
        super(text);
        this.compact = compact;
        setOpaque(false);
        setFocusPainted(false);
        setRolloverEnabled(true);
        setForeground(Theme.TEXT);
        setFont(Theme.font(13, Font.PLAIN));
        setBorder(Theme.pad(0));
        visualProgress = selectedProgress();
        animationStartProgress = visualProgress;
        animationTargetProgress = visualProgress;
        animationTimer = new Timer(ANIMATION_DELAY_MS, event -> tickAnimation());
        animationTimer.setCoalesce(true);
        addItemListener(event -> animateSelectionChange());
        addActionListener(event -> SoundService.play(SoundCue.UI_CLICK));
    }

    /**
     * Returns the preferred switch size.
     *
     * @return preferred size
     */
    @Override
    public Dimension getPreferredSize() {
        FontMetrics metrics = getFontMetrics(getFont());
        int width = PAD_X * 2 + metrics.stringWidth(getText()) + LABEL_GAP + TRACK_WIDTH;
    return new Dimension(compact ? width : Math.max(MIN_WIDTH, width), HEIGHT);
    }

    /**
     * Paints the row, label, and switch.
     *
     * @param graphics graphics context
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            paintRow(g);
            paintLabel(g);
            paintTrack(g);
            if (isFocusOwner()) {
                paintFocus(g);
            }
        } finally {
            g.dispose();
        }
    }

    /**
     * Paints the row background.
     *
     * @param g graphics context
     */
    private void paintRow(Graphics2D g) {
        if (getModel().isRollover() || isFocusOwner()) {
            g.setColor(isEnabled() ? Theme.SECONDARY_BUTTON_HOVER : Theme.BUTTON_DISABLED_BG);
            g.fillRoundRect(0, 0, Math.max(0, getWidth() - 1), Math.max(0, getHeight() - 1),
                    Theme.RADIUS, Theme.RADIUS);
        }
    }

    /**
     * Paints the toggle label.
     *
     * @param g graphics context
     */
    private void paintLabel(Graphics2D g) {
        FontMetrics metrics = g.getFontMetrics(getFont());
        int trackX = trackX();
        int labelWidth = Math.max(0, trackX - LABEL_GAP - PAD_X);
        String label = Ui.elide(getText(), metrics, labelWidth);
        g.setFont(getFont());
        g.setColor(isEnabled() ? Theme.TEXT : Theme.BUTTON_DISABLED_TEXT);
        int baseline = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent() - 1;
        g.drawString(label, PAD_X, baseline);
    }

    /**
     * Paints the switch track and thumb.
     *
     * @param g graphics context
     */
    private void paintTrack(Graphics2D g) {
        int x = trackX();
        int y = Math.max(0, (getHeight() - TRACK_HEIGHT) / 2 - 1);
        double progress = normalizedProgress();
        Color track = blend(Theme.TOGGLE_TRACK, Theme.TOGGLE_ON_TRACK, progress);
        if (!isEnabled()) {
            track = Theme.BUTTON_DISABLED_BORDER;
        }
        g.setColor(track);
        g.fillRoundRect(x, y, TRACK_WIDTH, TRACK_HEIGHT, TRACK_HEIGHT, TRACK_HEIGHT);

        int travel = TRACK_WIDTH - THUMB_SIZE - 6;
        int thumbX = x + 3 + (int) Math.round(travel * progress);
        int thumbY = y + 3;
        g.setColor(Theme.TOGGLE_THUMB);
        g.fillOval(thumbX, thumbY, THUMB_SIZE, THUMB_SIZE);
    }

    /**
     * Paints a focus ring.
     *
     * @param g graphics context
     */
    private void paintFocus(Graphics2D g) {
        g.setColor(Theme.TOGGLE_FOCUS);
        g.drawRoundRect(2, 2, Math.max(0, getWidth() - 5), Math.max(0, getHeight() - 5),
                Theme.RADIUS, Theme.RADIUS);
    }

    /**
     * Returns the switch track x coordinate.
     *
     * @return track x coordinate
     */
    private int trackX() {
        return Math.max(PAD_X, getWidth() - PAD_X - TRACK_WIDTH);
    }

    /**
     * Starts an animation toward the selected state.
     */
    private void animateSelectionChange() {
        double target = selectedProgress();
        if (!isEnabled()) {
            visualProgress = target;
            animationStartProgress = target;
            animationTargetProgress = target;
            animationTimer.stop();
            repaint();
            return;
        }
        animationStartProgress = visualProgress;
        animationTargetProgress = target;
        animationStartedAt = System.currentTimeMillis();
        if (Math.abs(animationStartProgress - animationTargetProgress) < 0.001) {
            visualProgress = animationTargetProgress;
            animationTimer.stop();
        } else {
            animationTimer.start();
        }
        repaint();
    }

    /**
     * Advances the toggle animation one frame.
     */
    private void tickAnimation() {
        double elapsed = (double) (System.currentTimeMillis() - animationStartedAt);
        double raw = elapsed / Math.max(1.0, ANIMATION_DURATION_MS);
        double progress = Ui.easeOutCubic(raw);
        visualProgress = animationStartProgress
                + (animationTargetProgress - animationStartProgress) * progress;
        if (raw >= 1.0) {
            visualProgress = animationTargetProgress;
            animationTimer.stop();
        }
        repaint();
    }

    /**
     * Returns selected-state progress.
     *
     * @return 1.0 when selected, 0.0 when off
     */
    private double selectedProgress() {
    return isSelected() ? 1.0 : 0.0;
    }

    /**
     * Returns the current visual progress clamped to the valid paint range.
     *
     * @return clamped progress
     */
    private double normalizedProgress() {
        return Math.max(0.0, Math.min(1.0, visualProgress));
    }

    /**
     * Blends two colors in sRGB space.
     *
     * @param from start color
     * @param to end color
     * @param progress blend progress
     * @return blended color
     */
    private static Color blend(Color from, Color to, double progress) {
        double p = Math.max(0.0, Math.min(1.0, progress));
        int r = (int) Math.round(from.getRed() + (to.getRed() - from.getRed()) * p);
        int g = (int) Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * p);
        int b = (int) Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * p);
        int a = (int) Math.round(from.getAlpha() + (to.getAlpha() - from.getAlpha()) * p);
    return new Color(r, g, b, a);
    }

}
