/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package application.gui.workbench.layout;

import application.gui.workbench.ui.Theme;
import javax.swing.BorderFactory;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

/**
 * Shared popup-menu styling for editor tabs and layout controls.
 */
final class PopupMenus {

    /**
     * Prevents instantiation.
     */
    private PopupMenus() {
        // utility
    }

    /**
     * Creates a styled popup menu item.
     *
     * @param label item label
     * @param action item action
     * @return styled item
     */
    static JMenuItem item(String label, Runnable action) {
        JMenuItem item = new JMenuItem(label);
        styleItem(item);
        item.addActionListener(event -> action.run());
        return item;
    }

    /**
     * Applies the workbench palette to a popup menu.
     *
     * @param menu popup menu
     */
    static void style(JPopupMenu menu) {
        menu.setOpaque(true);
        menu.setBackground(Theme.PANEL_SOLID);
        menu.setForeground(Theme.TEXT);
        menu.setBorder(BorderFactory.createLineBorder(Theme.LINE));
    }

    /**
     * Applies the workbench palette to a popup item.
     *
     * @param item popup item
     */
    static void styleItem(JMenuItem item) {
        item.setOpaque(true);
        item.setBackground(Theme.PANEL_SOLID);
        item.setForeground(Theme.TEXT);
        item.setFont(Theme.font(12, java.awt.Font.PLAIN));
        item.setBorder(Theme.pad(5, 10, 5, 10));
    }
}
