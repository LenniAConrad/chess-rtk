/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package application.gui.workbench.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.swing.JComponent;
import javax.swing.JProgressBar;
import javax.swing.Timer;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.basic.BasicProgressBarUI;

/**
 * Flat progress-bar painter for compact workbench activity indicators.
 */
final class ProgressBarChrome extends BasicProgressBarUI {

    /**
     * Default compact progress indicator size.
     */
    static final Dimension COMPACT_SIZE = new Dimension(96, 6);

    /**
     * Track corner radius.
     */
    private static final int RADIUS = 6;

    /**
     * Animation frame cadence for determinate fill transitions.
     */
    private static final int ANIMATION_DELAY_MS = 16;

    /**
     * Duration for determinate progress fill transitions.
     */
    private static final int PROGRESS_ANIMATION_MS = 170;

    /**
     * Currently displayed percentage, or -1 before first paint.
     */
    private double displayedPercent = -1.0d;

    /**
     * Fill percentage at the beginning of the active transition.
     */
    private double animationStartPercent;

    /**
     * Fill percentage target for the active transition.
     */
    private double animationTargetPercent;

    /**
     * Wall-clock start time for the active transition.
     */
    private long animationStartedAt;

    /**
     * Listener that starts a fill transition when the progress value changes.
     */
    private final ChangeListener valueListener = event -> startValueAnimation();

    /**
     * Timer driving determinate fill transitions.
     */
    private final Timer animationTimer = new Timer(ANIMATION_DELAY_MS, event -> tickAnimation());

    /**
     * Prevents direct construction outside the package.
     */
    ProgressBarChrome() {
        animationTimer.setCoalesce(true);
    }

    /**
     * Installs listeners for determinate value animation.
     */
    @Override
    protected void installListeners() {
        super.installListeners();
        progressBar.addChangeListener(valueListener);
    }

    /**
     * Removes listeners and stops active timers.
     */
    @Override
    protected void uninstallListeners() {
        animationTimer.stop();
        progressBar.removeChangeListener(valueListener);
        super.uninstallListeners();
    }

    /**
     * Paints a determinate progress bar.
     *
     * @param graphics graphics context
     * @param component progress component
     */
    @Override
    protected void paintDeterminate(Graphics graphics, JComponent component) {
        JProgressBar bar = (JProgressBar) component;
        Graphics2D g = begin(graphics);
        try {
            Rectangle track = trackBounds(component);
            paintTrack(g, track);
            double percent = animatedPercent(bar);
            int amount = (int) Math.round(percent
                    * (bar.getOrientation() == JProgressBar.HORIZONTAL ? track.width : track.height));
            if (amount > 0) {
                Rectangle fill = new Rectangle(track);
                if (bar.getOrientation() == JProgressBar.HORIZONTAL) {
                    fill.width = amount;
                } else {
                    fill.y = track.y + track.height - amount;
                    fill.height = amount;
                }
                paintFill(g, fill, bar.isEnabled());
            }
        } finally {
            g.dispose();
        }
    }

    /**
     * Paints an indeterminate progress bar.
     *
     * @param graphics graphics context
     * @param component progress component
     */
    @Override
    protected void paintIndeterminate(Graphics graphics, JComponent component) {
        JProgressBar bar = (JProgressBar) component;
        Graphics2D g = begin(graphics);
        try {
            Rectangle track = trackBounds(component);
            paintTrack(g, track);
            Rectangle box = getBox(null);
            if (box != null && !box.isEmpty()) {
                Rectangle fill = box.intersection(track);
                if (!fill.isEmpty()) {
                    paintFill(g, fill, bar.isEnabled());
                }
            }
        } finally {
            g.dispose();
        }
    }

    /**
     * Returns an anti-aliased graphics copy.
     *
     * @param graphics original graphics context
     * @return configured graphics context
     */
    private static Graphics2D begin(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        return g;
    }

    /**
     * Returns the inset track rectangle.
     *
     * @param component progress component
     * @return track bounds
     */
    private static Rectangle trackBounds(JComponent component) {
        int inset = 1;
        return new Rectangle(inset, inset,
                Math.max(0, component.getWidth() - inset * 2),
                Math.max(0, component.getHeight() - inset * 2));
    }

    /**
     * Paints the progress track.
     *
     * @param g graphics context
     * @param bounds track bounds
     */
    private static void paintTrack(Graphics2D g, Rectangle bounds) {
        g.setColor(Theme.INPUT_DISABLED);
        g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, RADIUS, RADIUS);
        g.setColor(Theme.INPUT_BORDER);
        g.drawRoundRect(bounds.x, bounds.y, Math.max(0, bounds.width - 1),
                Math.max(0, bounds.height - 1), RADIUS, RADIUS);
    }

    /**
     * Paints the progress fill.
     *
     * @param g graphics context
     * @param bounds fill bounds
     * @param enabled whether the progress bar is enabled
     */
    private static void paintFill(Graphics2D g, Rectangle bounds, boolean enabled) {
        Color fill = enabled ? Theme.ACCENT : Theme.BUTTON_DISABLED_BORDER;
        g.setColor(fill);
        g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, RADIUS, RADIUS);
    }

    /**
     * Returns the current animated determinate fill percentage.
     *
     * @param bar progress bar
     * @return percentage from 0 to 1
     */
    private double animatedPercent(JProgressBar bar) {
        double target = currentPercent(bar);
        if (displayedPercent < 0.0d || !bar.isShowing()) {
            displayedPercent = target;
            animationTargetPercent = target;
            animationTimer.stop();
        }
        return displayedPercent;
    }

    /**
     * Starts a transition toward the progress bar's current value.
     */
    private void startValueAnimation() {
        if (progressBar == null || progressBar.isIndeterminate()) {
            return;
        }
        double target = currentPercent(progressBar);
        if (displayedPercent < 0.0d || !progressBar.isShowing()) {
            displayedPercent = target;
            animationTargetPercent = target;
            animationTimer.stop();
            progressBar.repaint();
            return;
        }
        if (Math.abs(displayedPercent - target) < 0.001d) {
            displayedPercent = target;
            animationTargetPercent = target;
            animationTimer.stop();
            progressBar.repaint();
            return;
        }
        animationStartPercent = displayedPercent;
        animationTargetPercent = target;
        animationStartedAt = System.currentTimeMillis();
        if (!animationTimer.isRunning()) {
            animationTimer.start();
        }
    }

    /**
     * Advances one determinate progress animation frame.
     */
    private void tickAnimation() {
        if (progressBar == null) {
            animationTimer.stop();
            return;
        }
        double progress = Math.min(1.0d,
                (System.currentTimeMillis() - animationStartedAt)
                        / (double) PROGRESS_ANIMATION_MS);
        displayedPercent = animationStartPercent
                + (animationTargetPercent - animationStartPercent) * Ui.easeOutCubic(progress);
        if (progress >= 1.0d) {
            displayedPercent = animationTargetPercent;
            animationTimer.stop();
        }
        progressBar.repaint();
    }

    /**
     * Returns the clamped current progress percentage.
     *
     * @param bar progress bar
     * @return percentage from 0 to 1
     */
    private static double currentPercent(JProgressBar bar) {
        double percent = bar.getPercentComplete();
        if (Double.isNaN(percent) || Double.isInfinite(percent)) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, percent));
    }
}
