package application.gui.workbench.ui;

import application.gui.workbench.layout.FlatTabbedPaneUI;
import javax.swing.JTabbedPane;

/**
 * Shared Workbench tabbed-pane primitive.
 */
final class Tabs {

    /**
     * Prevents instantiation.
     */
    private Tabs() {
        // utility
    }

    /**
     * Styles an existing tabbed pane.
     *
     * @param tabs tabbed pane
     */
    public static void style(JTabbedPane tabs) {
        tabs.setUI(new FlatTabbedPaneUI());
        tabs.setOpaque(false);
        tabs.setBackground(Theme.TRANSPARENT);
        tabs.setForeground(Theme.TEXT);
        tabs.setFont(Theme.font(Theme.FONT_DENSE_TABLE, java.awt.Font.BOLD));
        tabs.setFocusable(true);
    }

    /**
     * Creates a scrollable single-row Workbench tabbed pane.
     *
     * @return tabbed pane
     */
    public static JTabbedPane create() {
        JTabbedPane pane = new JTabbedPane();
        pane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        style(pane);
        return pane;
    }
}
