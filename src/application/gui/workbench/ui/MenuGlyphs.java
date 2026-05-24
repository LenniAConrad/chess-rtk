/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package application.gui.workbench.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JRadioButtonMenuItem;

/**
 * Theme-aware glyphs for popup menu selection controls.
 */
public final class MenuGlyphs {

    /**
     * Shared radio menu icon.
     */
    private static final Icon RADIO_ICON = new RadioIcon();

    /**
     * Shared checkbox menu icon.
     */
    private static final Icon CHECK_ICON = new CheckIcon();

    /**
     * Prevents instantiation.
     */
    private MenuGlyphs() {
        // utility
    }

    /**
     * Applies the custom workbench radio glyph to one radio menu item.
     *
     * @param item radio menu item
     */
    public static void styleRadioItem(JRadioButtonMenuItem item) {
        installIconSet(item, RADIO_ICON);
    }

    /**
     * Applies the custom workbench checkbox glyph to one checkbox menu item.
     *
     * @param item checkbox menu item
     */
    public static void styleCheckItem(JCheckBoxMenuItem item) {
        installIconSet(item, CHECK_ICON);
    }

    /**
     * Installs an icon for every enabled/disabled and selected/unselected menu
     * item state.
     *
     * @param item target item
     * @param icon state-aware icon
     */
    private static void installIconSet(AbstractButton item, Icon icon) {
        item.setIcon(icon);
        item.setSelectedIcon(icon);
        item.setRolloverIcon(icon);
        item.setRolloverSelectedIcon(icon);
        item.setDisabledIcon(icon);
        item.setDisabledSelectedIcon(icon);
        item.setIconTextGap(8);
        item.setBorderPainted(false);
        item.setFocusPainted(false);
    }

    /**
     * Returns the ring color for a menu selection state.
     *
     * @param selected true when selected
     * @param armed true when hovered or armed
     * @param enabled true when enabled
     * @return ring color
     */
    private static Color ringColor(boolean selected, boolean armed, boolean enabled) {
        if (!enabled) {
            return Theme.BUTTON_DISABLED_TEXT;
        }
        if (selected) {
            return Theme.ACCENT;
        }
        return armed ? Theme.TEXT : Theme.MUTED;
    }

    /**
     * Returns whether a menu button is hovered or armed.
     *
     * @param component owner component
     * @return true when active
     */
    private static boolean active(Component component) {
        return component instanceof AbstractButton button
                && (button.getModel().isArmed() || button.getModel().isRollover());
    }

    /**
     * Returns whether a menu button is selected.
     *
     * @param component owner component
     * @return true when selected
     */
    private static boolean selected(Component component) {
        return component instanceof AbstractButton button && button.isSelected();
    }

    /**
     * Base icon with common sizing.
     */
    private abstract static class SelectionIcon implements Icon {

        /**
         * Icon box size.
         */
        static final int SIZE = 16;

        /**
         * Returns icon width.
         *
         * @return width
         */
        @Override
        public final int getIconWidth() {
            return SIZE;
        }

        /**
         * Returns icon height.
         *
         * @return height
         */
        @Override
        public final int getIconHeight() {
            return SIZE;
        }
    }

    /**
     * Radio glyph for mutually-exclusive menu items.
     */
    private static final class RadioIcon extends SelectionIcon {

        /**
         * Outer circle diameter.
         */
        private static final int OUTER = 11;

        /**
         * Inner selected dot diameter.
         */
        private static final int INNER = 5;

        /**
         * Stroke used for the outer ring.
         */
        private static final BasicStroke STROKE =
                new BasicStroke(1.35f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

        /**
         * Paints the themed radio ring and selected dot.
         *
         * @param component owner component
         * @param graphics graphics context
         * @param x icon x
         * @param y icon y
         */
        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            boolean selected = selected(component);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int ox = x + (SIZE - OUTER) / 2;
                int oy = y + (SIZE - OUTER) / 2;
                g.setStroke(STROKE);
                g.setColor(ringColor(selected, active(component), component == null || component.isEnabled()));
                g.drawOval(ox, oy, OUTER, OUTER);
                if (selected) {
                    int ix = x + (SIZE - INNER) / 2;
                    int iy = y + (SIZE - INNER) / 2;
                    g.setColor(component == null || component.isEnabled()
                            ? Theme.ACCENT : Theme.BUTTON_DISABLED_TEXT);
                    g.fillOval(ix, iy, INNER, INNER);
                }
            } finally {
                g.dispose();
            }
        }
    }

    /**
     * Checkbox glyph for independent menu toggles.
     */
    private static final class CheckIcon extends SelectionIcon {

        /**
         * Box side length.
         */
        private static final int BOX = 12;

        /**
         * Box outline stroke.
         */
        private static final BasicStroke BOX_STROKE =
                new BasicStroke(1.25f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

        /**
         * Checkmark stroke.
         */
        private static final BasicStroke CHECK_STROKE =
                new BasicStroke(1.75f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

        /**
         * Paints the themed checkbox outline and selected checkmark.
         *
         * @param component owner component
         * @param graphics graphics context
         * @param x icon x
         * @param y icon y
         */
        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            boolean selected = selected(component);
            boolean enabled = component == null || component.isEnabled();
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int bx = x + (SIZE - BOX) / 2;
                int by = y + (SIZE - BOX) / 2;
                g.setStroke(BOX_STROKE);
                g.setColor(ringColor(selected, active(component), enabled));
                g.drawRoundRect(bx, by, BOX, BOX, 3, 3);
                if (selected) {
                    g.setStroke(CHECK_STROKE);
                    g.setColor(enabled ? Theme.ACCENT : Theme.BUTTON_DISABLED_TEXT);
                    Path2D check = new Path2D.Double();
                    check.moveTo(bx + 3.0, by + 6.5);
                    check.lineTo(bx + 5.5, by + 9.0);
                    check.lineTo(bx + 9.5, by + 3.5);
                    g.draw(check);
                }
            } finally {
                g.dispose();
            }
        }
    }
}
