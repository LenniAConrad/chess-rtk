package application.gui.workbench.layout;

/**
 * Stable command ids and user-facing text for editor-group layout controls.
 */
final class EditorLayoutCommands {

    /**
     * Stable text value for split right.
     */
    static final String SPLIT_RIGHT = "workbench.editor.split.right";
    /**
     * Stable text value for tab split right.
     */
    static final String TAB_SPLIT_RIGHT = "workbench.editor.tab.split.right";
    /**
     * Stable text value for tab split down.
     */
    static final String TAB_SPLIT_DOWN = "workbench.editor.tab.split.down";
    /**
     * Stable text value for tab split left.
     */
    static final String TAB_SPLIT_LEFT = "workbench.editor.tab.split.left";
    /**
     * Stable text value for tab split up.
     */
    static final String TAB_SPLIT_UP = "workbench.editor.tab.split.up";
    /**
     * Stable text value for tab duplicate.
     */
    static final String TAB_DUPLICATE = "workbench.editor.tab.duplicate";
    /**
     * Stable text value for tab detach.
     */
    static final String TAB_DETACH = "workbench.editor.tab.detach";
    /**
     * Stable text value for tab reattach.
     */
    static final String TAB_REATTACH = "workbench.editor.tab.reattach";
    /**
     * Stable text value for tab close.
     */
    static final String TAB_CLOSE = "workbench.editor.tab.close";
    /**
     * Stable text value for tab close others.
     */
    static final String TAB_CLOSE_OTHERS = "workbench.editor.tab.closeOthers";
    /**
     * Stable text value for tabs restore closed.
     */
    static final String TABS_RESTORE_CLOSED = "workbench.editor.tabs.restoreClosed";
    /**
     * Stable text value for tabs new or restore.
     */
    static final String TABS_NEW_OR_RESTORE = "workbench.editor.tabs.newOrRestore";
    /**
     * Stable text value for tabs overflow.
     */
    static final String TABS_OVERFLOW = "workbench.editor.tabs.overflow";
    /**
     * Stable text value for groups collapse.
     */
    static final String GROUPS_COLLAPSE = "workbench.editor.groups.collapse";

    /**
     * Stable text value for split right accessible name.
     */
    static final String SPLIT_RIGHT_ACCESSIBLE_NAME = "Split active tab right";
    /**
     * Stable text value for split right accessible description.
     */
    static final String SPLIT_RIGHT_ACCESSIBLE_DESCRIPTION =
            "Creates an editor group to the right using the active tab.";
    /**
     * Stable text value for split right tooltip.
     */
    static final String SPLIT_RIGHT_TOOLTIP = "Split active tab to the right";
    /**
     * Stable text value for split right disabled tooltip.
     */
    static final String SPLIT_RIGHT_DISABLED_TOOLTIP =
            "Open another tab or select a duplicate-capable tab to split right";

    /**
     * Stable text value for overflow accessible name.
     */
    static final String OVERFLOW_ACCESSIBLE_NAME = "Show all open tabs";
    /**
     * Stable text value for overflow tooltip.
     */
    static final String OVERFLOW_TOOLTIP = "Show all open tabs in this editor group";

    /**
     * Stable text value for new or restore accessible name.
     */
    static final String NEW_OR_RESTORE_ACCESSIBLE_NAME = "New or restore tab";
    /**
     * Stable text value for new or restore accessible description.
     */
    static final String NEW_OR_RESTORE_ACCESSIBLE_DESCRIPTION =
            "Opens another duplicate-capable tab or restores a closed workbench tab.";
    /**
     * Stable text value for new or restore tooltip.
     */
    static final String NEW_OR_RESTORE_TOOLTIP = "New duplicate tab or restore a closed tab";

    /**
     * Stable text value for split disabled tooltip.
     */
    static final String SPLIT_DISABLED_TOOLTIP =
            "Open another tab or select a duplicate-capable tab to split this tab.";
    /**
     * Stable text value for close others disabled tooltip.
     */
    static final String CLOSE_OTHERS_DISABLED_TOOLTIP = "Open another tab before closing others.";

    /**
     * Stable text value for tab overflow prefix.
     */
    private static final String TAB_OVERFLOW_PREFIX = "workbench.editor.tabs.overflow.";
    /**
     * Stable text value for tab duplicate prefix.
     */
    private static final String TAB_DUPLICATE_PREFIX = "workbench.editor.tab.duplicate.";
    /**
     * Stable text value for tab restore prefix.
     */
    private static final String TAB_RESTORE_PREFIX = "workbench.editor.tabs.restoreClosed.";
    /**
     * Stable text value for tab reattach prefix.
     */
    private static final String TAB_REATTACH_PREFIX = "workbench.editor.tabs.reattach.";

    /**
     * Prevents instantiation.
     */
    private EditorLayoutCommands() {
        // utility class
    }

    /**
     * Returns the overflow tab command.
     *
     * @param panelIndex zero-based panel index
     * @return overflow tab command text
     */
    static String overflowTabCommand(int panelIndex) {
        return TAB_OVERFLOW_PREFIX + panelIndex;
    }

    /**
     * Returns the duplicate tab command.
     *
     * @param panelIndex zero-based panel index
     * @return duplicate tab command text
     */
    static String duplicateTabCommand(int panelIndex) {
        return TAB_DUPLICATE_PREFIX + panelIndex;
    }

    /**
     * Returns the restore tab command.
     *
     * @param panelIndex zero-based panel index
     * @return restore tab command text
     */
    static String restoreTabCommand(int panelIndex) {
        return TAB_RESTORE_PREFIX + panelIndex;
    }

    /**
     * Returns the reattach tab command.
     *
     * @param panelIndex zero-based panel index
     * @return reattach tab command text
     */
    static String reattachTabCommand(int panelIndex) {
        return TAB_REATTACH_PREFIX + panelIndex;
    }
}
