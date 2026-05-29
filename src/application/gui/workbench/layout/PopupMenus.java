package application.gui.workbench.layout;

import application.gui.workbench.ui.Ui;
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
        return item(label, action, true);
    }

    /**
     * Creates a styled popup menu item with an explicit enabled state.
     *
     * @param label item label
     * @param action item action
     * @param enabled whether the item can run
     * @return styled item
     */
    static JMenuItem item(String label, Runnable action, boolean enabled) {
        JMenuItem item = new JMenuItem(label);
        styleItem(item);
        item.setEnabled(enabled);
        item.addActionListener(event -> action.run());
        return item;
    }

    /**
     * Applies the workbench palette to a popup menu.
     *
     * @param menu popup menu
     */
    static void style(JPopupMenu menu) {
        Ui.stylePopupMenu(menu);
    }

    /**
     * Applies the workbench palette to a popup item.
     *
     * @param item popup item
     */
    static void styleItem(JMenuItem item) {
        Ui.stylePopupMenuItem(item);
    }
}
