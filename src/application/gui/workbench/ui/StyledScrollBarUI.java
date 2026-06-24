package application.gui.workbench.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.SwingConstants;
import javax.swing.plaf.basic.BasicScrollBarUI;

/**
 * Thin solid scrollbar UI for workbench scroll panes.
 */
final class StyledScrollBarUI extends BasicScrollBarUI {

    /**
     * Thumb corner radius.
     */
    private static final int THUMB_RADIUS = 7;

    /**
     * Visible thumb thickness inside the reserved scrollbar gutter.
     */
    private static final int THUMB_THICKNESS = 6;

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
     * Paints an invisible track using the host surface color.
     *
     * @param graphics graphics context
     * @param component Swing component
     * @param bounds track bounds
     */
    @Override
    protected void paintTrack(Graphics graphics, JComponent component, Rectangle bounds) {
        Color background = component.getBackground();
        graphics.setColor(background == null ? Theme.PANEL_SOLID : background);
        graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    /**
     * Paints the scrollbar thumb.
     *
     * @param graphics graphics context
     * @param component Swing component
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
            boolean vertical = scrollbar.getOrientation() == SwingConstants.VERTICAL;
            int x = vertical ? bounds.x + Math.max(1, (bounds.width - THUMB_THICKNESS) / 2) : bounds.x + 2;
            int y = vertical ? bounds.y + 2 : bounds.y + Math.max(1, (bounds.height - THUMB_THICKNESS) / 2);
            int w = vertical ? THUMB_THICKNESS : Math.max(0, bounds.width - 4);
            int h = vertical ? Math.max(0, bounds.height - 4) : THUMB_THICKNESS;
            Color resting = Theme.withAlpha(Theme.SCROLLBAR_THUMB, Theme.isDark() ? 92 : 82);
            g.setColor(isDragging ? Theme.SCROLLBAR_THUMB_HOVER
                    : isThumbRollover() ? Theme.withAlpha(Theme.SCROLLBAR_THUMB_HOVER, Theme.isDark() ? 170 : 150)
                            : resting);
            g.fillRoundRect(x, y, w, h, THUMB_RADIUS, THUMB_RADIUS);
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
