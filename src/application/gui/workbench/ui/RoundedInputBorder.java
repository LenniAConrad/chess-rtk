package application.gui.workbench.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;
import javax.swing.border.Border;

/**
 * A rounded hairline border for opaque input controls (text fields, combos,
 * spinners, terminals). Stock Swing inputs paint a square opaque background, so
 * a plain rounded line would leave the square fill poking past the corners.
 * This border masks the four corner wedges with the control's backdrop colour
 * before stroking the rounded outline, giving every input the same
 * {@link Theme#RADIUS} corner as buttons, cards, and chips.
 */
final class RoundedInputBorder implements Border {

    /**
     * One-pixel insets, matching the line border this replaces so layouts do
     * not shift.
     */
    private static final Insets INSETS = new Insets(1, 1, 1, 1);

    /**
     * Border line colour for the current interaction state.
     */
    private final Color line;

    /**
     * Creates a rounded input border.
     *
     * @param line line colour
     */
    RoundedInputBorder(Color line) {
        this.line = line;
    }

    /**
     * Returns the fixed one-pixel insets.
     *
     * @param component bordered component
     * @return border insets
     */
    @Override
    public Insets getBorderInsets(Component component) {
        return new Insets(INSETS.top, INSETS.left, INSETS.bottom, INSETS.right);
    }

    /**
     * Reports the border as non-opaque since it masks rather than fills.
     *
     * @return false
     */
    @Override
    public boolean isBorderOpaque() {
        return false;
    }

    /**
     * Masks the corners with the backdrop and strokes the rounded outline.
     *
     * @param component bordered component
     * @param graphics graphics context
     * @param x left
     * @param y top
     * @param width width
     * @param height height
     */
    @Override
    public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int arc = Theme.RADIUS;
            RoundRectangle2D round = new RoundRectangle2D.Float(x, y, width - 1f, height - 1f, arc, arc);
            Area corners = new Area(new Rectangle(x, y, width, height));
            corners.subtract(new Area(round));
            g.setColor(backdrop(component));
            g.fill(corners);
            g.setColor(line);
            g.draw(round);
        } finally {
            g.dispose();
        }
    }

    /**
     * Returns the visible colour behind the control — the first opaque ancestor
     * background — so the masked corners blend in.
     *
     * @param component bordered component
     * @return backdrop colour
     */
    private static Color backdrop(Component component) {
        Component parent = component.getParent();
        while (parent != null) {
            if (parent.isOpaque() && parent.getBackground() != null) {
                return parent.getBackground();
            }
            parent = parent.getParent();
        }
        return Theme.PANEL_SOLID;
    }
}
