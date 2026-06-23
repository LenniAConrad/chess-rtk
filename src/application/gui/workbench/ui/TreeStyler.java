package application.gui.workbench.ui;

import java.awt.Font;
import javax.swing.JTree;
import javax.swing.tree.TreeSelectionModel;

/**
 * Shared Workbench styling for tree views.
 */
final class TreeStyler {

    /**
     * Swing client-property value that disables platform connector lines.
     */
    private static final String LINE_STYLE_NONE = "None";

    /**
     * Swing client-property key for tree connector-line style.
     */
    private static final String LINE_STYLE_KEY = "JTree.lineStyle";

    /**
     * Shared tree row height.
     */
    private static final int ROW_HEIGHT = 26;

    /**
     * Prevents instantiation.
     */
    private TreeStyler() {
        // utility
    }

    /**
     * Applies Workbench tree chrome.
     *
     * @param tree tree to style
     */
    static void style(JTree tree) {
        if (tree == null) {
            return;
        }
        tree.setOpaque(true);
        tree.setBackground(Theme.PANEL_SOLID);
        tree.setForeground(Theme.TEXT);
        tree.setFont(Theme.font(12, Font.PLAIN));
        tree.setRowHeight(ROW_HEIGHT);
        tree.setToggleClickCount(1);
        tree.putClientProperty(LINE_STYLE_KEY, LINE_STYLE_NONE);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    }

}
