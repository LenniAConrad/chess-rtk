package application.gui.workbench.ui;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JSlider;
import javax.swing.plaf.basic.BasicSliderUI;

/**
 * Minimal horizontal slider UI for compact settings controls.
 */
final class StyledSliderUI extends BasicSliderUI {

    /**
     * Track height.
     */
    private static final int TRACK_HEIGHT = 4;

    /**
     * Thumb size.
     */
    private static final int THUMB_SIZE = 14;

    /**
     * Creates the slider UI.
     *
     * @param slider target slider
     */
    StyledSliderUI(JSlider slider) {
        super(slider);
    }

    /**
     * Returns the compact thumb size.
     *
     * @return thumb size
     */
    @Override
    protected Dimension getThumbSize() {
        return new Dimension(THUMB_SIZE, THUMB_SIZE);
    }

    /**
     * Paints the flat track and filled range.
     *
     * @param graphics graphics context
     */
    @Override
    public void paintTrack(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int y = trackRect.y + (trackRect.height - TRACK_HEIGHT) / 2;
            int x = trackRect.x;
            int width = trackRect.width;
            int fill = Math.max(0, thumbRect.x + thumbRect.width / 2 - x);
            g.setColor(Theme.LINE);
            g.fillRoundRect(x, y, width, TRACK_HEIGHT, TRACK_HEIGHT, TRACK_HEIGHT);
            g.setColor(slider.isEnabled() ? Theme.ACCENT : Theme.BUTTON_DISABLED_TEXT);
            g.fillRoundRect(x, y, fill, TRACK_HEIGHT, TRACK_HEIGHT, TRACK_HEIGHT);
        } finally {
            g.dispose();
        }
    }

    /**
     * Paints the compact circular thumb.
     *
     * @param graphics graphics context
     */
    @Override
    public void paintThumb(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(slider.isEnabled() ? Theme.ACCENT : Theme.BUTTON_DISABLED_TEXT);
            g.fillOval(thumbRect.x, thumbRect.y, thumbRect.width, thumbRect.height);
            g.setColor(Theme.PANEL_SOLID);
            g.drawOval(thumbRect.x, thumbRect.y, thumbRect.width - 1, thumbRect.height - 1);
        } finally {
            g.dispose();
        }
    }
}
