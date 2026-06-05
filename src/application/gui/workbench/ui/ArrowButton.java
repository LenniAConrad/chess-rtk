package application.gui.workbench.ui;

import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.SwingConstants;

/**
 * Small chevron button shared by styled combo boxes and spinners.
 */
final class ArrowButton extends JButton {

    /**
     * Serialization identifier for Swing button compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Arrow direction.
     */
    private final int direction;

    /**
     * Creates an arrow button.
     *
     * @param direction SwingConstants arrow direction
     */
    ArrowButton(int direction) {
        this.direction = direction;
        setBorder(BorderFactory.createEmptyBorder());
        setContentAreaFilled(false);
        setFocusPainted(false);
        setOpaque(false);
        setPreferredSize(new Dimension(24, 20));
    }

    /**
     * Paints the chevron.
     *
     * @param graphics graphics context
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int h = getHeight();
            // No opaque fill: the spinner/combo already painted the input well,
            // and its rounded border masked the corners. Filling here would
            // re-square the rounded right corners.
            g.setColor(Theme.withAlpha(Theme.LINE, 150));
            g.drawLine(0, direction == SwingConstants.NORTH ? 3 : 0,
                    0, direction == SwingConstants.NORTH ? h : h - 3);
            g.setColor(isEnabled() ? Theme.MUTED : Theme.BUTTON_DISABLED_TEXT);
            Path2D path = new Path2D.Double();
            double centerX = getWidth() / 2.0;
            double centerY = getHeight() / 2.0;
            if (direction == SwingConstants.NORTH) {
                path.moveTo(centerX - 4, centerY + 2);
                path.lineTo(centerX, centerY - 2);
                path.lineTo(centerX + 4, centerY + 2);
            } else {
                path.moveTo(centerX - 4, centerY - 2);
                path.lineTo(centerX, centerY + 2);
                path.lineTo(centerX + 4, centerY - 2);
            }
            g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(path);
        } finally {
            g.dispose();
        }
    }
}
