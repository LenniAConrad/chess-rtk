package application.gui.workbench.ui;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicScrollBarUI;

/**
 * Thin solid scrollbar UI for workbench scroll panes.
 */
final class StyledScrollBarUI extends BasicScrollBarUI {

    /**
     * Thumb corner radius.
     */
    private static final int THUMB_RADIUS = Theme.RADIUS;

    /**
     * Creates an invisible scrollbar button.
     *
     * @param orientation button orientation
     * @return zero-size button
     */
    @Override
    protected JButton createDecreaseButton(int orientation) {
        return invisibleButton();
    }

    /**
     * Creates an invisible scrollbar button.
     *
     * @param orientation button orientation
     * @return zero-size button
     */
    @Override
    protected JButton createIncreaseButton(int orientation) {
        return invisibleButton();
    }

    /**
     * Paints the transparent track.
     *
     * @param graphics graphics context
     * @param component component
     * @param bounds track bounds
     */
    @Override
    protected void paintTrack(Graphics graphics, JComponent component, Rectangle bounds) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setColor(Theme.SCROLLBAR_TRACK);
            g.fillRoundRect(bounds.x + 2, bounds.y + 2, Math.max(0, bounds.width - 4),
                    Math.max(0, bounds.height - 4), THUMB_RADIUS, THUMB_RADIUS);
        } finally {
            g.dispose();
        }
    }

    /**
     * Paints the scrollbar thumb.
     *
     * @param graphics graphics context
     * @param component component
     * @param bounds thumb bounds
     */
    @Override
    protected void paintThumb(Graphics graphics, JComponent component, Rectangle bounds) {
        if (bounds.isEmpty() || !scrollbar.isEnabled()) {
            return;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(isDragging ? Theme.SCROLLBAR_THUMB_HOVER
                    : isThumbRollover() ? Theme.withAlpha(Theme.SCROLLBAR_THUMB, 190)
                    : Theme.SCROLLBAR_THUMB);
            g.fillRoundRect(bounds.x + 2, bounds.y + 2, Math.max(0, bounds.width - 4),
                    Math.max(0, bounds.height - 4), THUMB_RADIUS, THUMB_RADIUS);
        } finally {
            g.dispose();
        }
    }

    /**
     * Creates a zero-size invisible button.
     *
     * @return button
     */
    private static JButton invisibleButton() {
        JButton button = new JButton();
        Dimension size = new Dimension(0, 0);
        button.setPreferredSize(size);
        button.setMinimumSize(size);
        button.setMaximumSize(size);
        return button;
    }
}
