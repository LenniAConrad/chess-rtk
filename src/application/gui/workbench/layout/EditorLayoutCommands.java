package application.gui.workbench.layout;

/**
 * Stable command ids and user-facing text for editor-group layout controls.
 */
final class EditorLayoutCommands {

    static final String SPLIT_RIGHT = "workbench.editor.split.right";
    static final String TAB_SPLIT_RIGHT = "workbench.editor.tab.split.right";
    static final String TAB_SPLIT_DOWN = "workbench.editor.tab.split.down";
    static final String TAB_SPLIT_LEFT = "workbench.editor.tab.split.left";
    static final String TAB_SPLIT_UP = "workbench.editor.tab.split.up";
    static final String TAB_DUPLICATE = "workbench.editor.tab.duplicate";
    static final String TAB_DETACH = "workbench.editor.tab.detach";
    static final String TAB_REATTACH = "workbench.editor.tab.reattach";
    static final String TAB_CLOSE = "workbench.editor.tab.close";
    static final String TAB_CLOSE_OTHERS = "workbench.editor.tab.closeOthers";
    static final String TABS_RESTORE_CLOSED = "workbench.editor.tabs.restoreClosed";
    static final String TABS_NEW_OR_RESTORE = "workbench.editor.tabs.newOrRestore";
    static final String TABS_OVERFLOW = "workbench.editor.tabs.overflow";
    static final String GROUPS_COLLAPSE = "workbench.editor.groups.collapse";

    static final String SPLIT_RIGHT_ACCESSIBLE_NAME = "Split active tab right";
    static final String SPLIT_RIGHT_ACCESSIBLE_DESCRIPTION =
            "Creates an editor group to the right using the active tab.";
    static final String SPLIT_RIGHT_TOOLTIP = "Split active tab to the right";
    static final String SPLIT_RIGHT_DISABLED_TOOLTIP =
            "Open another tab or select a duplicate-capable tab to split right";

    static final String OVERFLOW_ACCESSIBLE_NAME = "Show all open tabs";
    static final String OVERFLOW_TOOLTIP = "Show all open tabs in this editor group";

    static final String NEW_OR_RESTORE_ACCESSIBLE_NAME = "New or restore tab";
    static final String NEW_OR_RESTORE_ACCESSIBLE_DESCRIPTION =
            "Opens another duplicate-capable tab or restores a closed workbench tab.";
    static final String NEW_OR_RESTORE_TOOLTIP = "New duplicate tab or restore a closed tab";

    static final String SPLIT_DISABLED_TOOLTIP =
            "Open another tab or select a duplicate-capable tab to split this tab.";
    static final String CLOSE_OTHERS_DISABLED_TOOLTIP = "Open another tab before closing others.";

    private static final String TAB_OVERFLOW_PREFIX = "workbench.editor.tabs.overflow.";
    private static final String TAB_DUPLICATE_PREFIX = "workbench.editor.tab.duplicate.";
    private static final String TAB_RESTORE_PREFIX = "workbench.editor.tabs.restoreClosed.";
    private static final String TAB_REATTACH_PREFIX = "workbench.editor.tabs.reattach.";

    /**
     * Prevents instantiation.
     */
    private EditorLayoutCommands() {
        // utility class
    }

    static String overflowTabCommand(int panelIndex) {
        return TAB_OVERFLOW_PREFIX + panelIndex;
    }

    static String duplicateTabCommand(int panelIndex) {
        return TAB_DUPLICATE_PREFIX + panelIndex;
    }

    static String restoreTabCommand(int panelIndex) {
        return TAB_RESTORE_PREFIX + panelIndex;
    }

    static String reattachTabCommand(int panelIndex) {
        return TAB_REATTACH_PREFIX + panelIndex;
    }
}
