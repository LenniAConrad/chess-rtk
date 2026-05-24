package application.gui.workbench.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import javax.swing.AbstractButton;
import javax.swing.Icon;

/**
 * Flat workbench checkbox glyph for plain Swing checkboxes.
 */
@SuppressWarnings("java:S6548")
final class CheckBoxGlyph implements Icon {

    /**
     * Shared checkbox icon instance.
     */
    static final CheckBoxGlyph INSTANCE = new CheckBoxGlyph();

    /**
     * Checkbox square size.
     */
    private static final int SIZE = 15;

    /**
     * Icon drawing viewport.
     */
    private static final int VIEWPORT = 17;

    /**
     * Prevents external instantiation.
     */
    private CheckBoxGlyph() {
        // shared singleton
    }

    /**
     * Returns the icon width.
     *
     * @return icon width
     */
    @Override
    public int getIconWidth() {
        return VIEWPORT;
    }

    /**
     * Returns the icon height.
     *
     * @return icon height
     */
    @Override
    public int getIconHeight() {
        return getIconWidth();
    }

    /**
     * Paints a checkbox glyph from the owning button state.
     *
     * @param component owning component
     * @param graphics graphics context
     * @param x x coordinate
     * @param y y coordinate
     */
    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        AbstractButton button = component instanceof AbstractButton abstractButton ? abstractButton : null;
        boolean enabled = button == null || button.isEnabled();
        boolean selected = button != null && button.isSelected();
        boolean hovered = button != null && button.getModel().isRollover();
        boolean focused = button != null && button.isFocusOwner();
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int boxX = x + 1;
            int boxY = y + 1;
            paintBox(g, boxX, boxY, enabled, selected, hovered, focused);
            if (selected) {
                paintCheck(g, boxX, boxY, enabled);
            }
        } finally {
            g.dispose();
        }
    }

    /**
     * Paints the checkbox square.
     *
     * @param g graphics context
     * @param x x coordinate
     * @param y y coordinate
     * @param enabled whether the checkbox is enabled
     * @param selected whether the checkbox is selected
     * @param hovered whether the pointer is over the checkbox
     * @param focused whether the checkbox has focus
     */
    private static void paintBox(Graphics2D g, int x, int y, boolean enabled,
            boolean selected, boolean hovered, boolean focused) {
        Color fill = selected ? Theme.ACCENT : enabled ? Theme.INPUT : Theme.INPUT_DISABLED;
        Color border = selected ? Theme.ACCENT
                : focused ? Theme.INPUT_FOCUS
                : hovered && enabled ? Theme.ACCENT_HOVER
                : Theme.INPUT_BORDER;
        g.setColor(fill);
        g.fillRoundRect(x, y, SIZE, SIZE, Theme.RADIUS, Theme.RADIUS);
        g.setColor(border);
        g.drawRoundRect(x, y, SIZE - 1, SIZE - 1, Theme.RADIUS, Theme.RADIUS);
        if (focused && enabled) {
            g.setColor(Theme.withAlpha(Theme.INPUT_FOCUS, 70));
            g.drawRoundRect(x - 1, y - 1, SIZE + 1, SIZE + 1, Theme.RADIUS + 1, Theme.RADIUS + 1);
        }
    }

    /**
     * Paints the selected-state check mark.
     *
     * @param g graphics context
     * @param x x coordinate
     * @param y y coordinate
     * @param enabled whether the checkbox is enabled
     */
    private static void paintCheck(Graphics2D g, int x, int y, boolean enabled) {
        double left = x;
        double top = y;
        Path2D check = new Path2D.Double();
        check.moveTo(left + 4.0, top + 8.0);
        check.lineTo(left + 7.0, top + 11.0);
        check.lineTo(left + 12.0, top + 5.0);
        g.setColor(enabled ? Theme.PRIMARY_BUTTON_TEXT : Theme.BUTTON_DISABLED_TEXT);
        g.setStroke(new BasicStroke(1.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(check);
    }
}
