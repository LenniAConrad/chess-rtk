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
     * Prevents direct construction outside the package.
     */
    ProgressBarChrome() {
        // package UI delegate
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
            double percent = Math.max(0.0, Math.min(1.0, bar.getPercentComplete()));
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
}
