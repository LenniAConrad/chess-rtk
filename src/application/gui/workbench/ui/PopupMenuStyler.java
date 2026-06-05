package application.gui.workbench.ui;

import java.awt.Component;
import java.awt.Container;
import javax.swing.BorderFactory;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSeparator;

/**
 * Applies shared Workbench chrome to popup menus and their nested menu items.
 */
final class PopupMenuStyler {

    /**
     * Prevents instantiation.
     */
    private PopupMenuStyler() {
        // utility
    }

    /**
     * Applies shared Workbench chrome to a popup menu and its current children.
     *
     * @param menu popup menu
     */
    static void styleMenu(JPopupMenu menu) {
        menu.setOpaque(true);
        menu.setBackground(Theme.PANEL_SOLID);
        menu.setForeground(Theme.TEXT);
        menu.setBorder(BorderFactory.createLineBorder(Theme.LINE));
        for (Component child : menu.getComponents()) {
            styleComponent(child);
        }
    }

    /**
     * Applies shared Workbench chrome to one popup menu item.
     *
     * @param item menu item
     */
    static void styleItem(JMenuItem item) {
        if (item instanceof JRadioButtonMenuItem radio) {
            MenuGlyphs.styleRadioItem(radio);
        } else if (item instanceof JCheckBoxMenuItem check) {
            MenuGlyphs.styleCheckItem(check);
        } else {
            MenuGlyphs.styleItem(item);
        }
    }

    /**
     * Recursively styles one popup child component.
     *
     * @param component popup child component
     */
    private static void styleComponent(Component component) {
        if (component instanceof JPopupMenu popup) {
            styleMenu(popup);
            return;
        }
        if (component instanceof JMenuItem item) {
            styleItem(item);
        } else if (component instanceof JSeparator separator) {
            separator.setBackground(Theme.PANEL_SOLID);
            separator.setForeground(Theme.LINE);
        } else if (component instanceof JComponent jComponent) {
            jComponent.setOpaque(true);
            jComponent.setBackground(Theme.PANEL_SOLID);
            jComponent.setForeground(Theme.TEXT);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                styleComponent(child);
            }
        }
    }
}
