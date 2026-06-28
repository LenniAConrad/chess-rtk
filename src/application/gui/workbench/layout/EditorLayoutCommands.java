package application.gui.workbench.layout;

/**
 * Stable command ids and user-facing text for editor-group layout controls.
 */
public final class EditorLayoutCommands {

    /**
     * Stable text value for split right.
     */
    public static final String SPLIT_RIGHT = "workbench.editor.split.right";
    /**
     * Stable text value for split down.
     */
    public static final String SPLIT_DOWN = "workbench.editor.split.down";
    /**
     * Stable text value for tab split right.
     */
    public static final String TAB_SPLIT_RIGHT = "workbench.editor.tab.split.right";
    /**
     * Stable text value for tab split down.
     */
    public static final String TAB_SPLIT_DOWN = "workbench.editor.tab.split.down";
    /**
     * Stable text value for tab split left.
     */
    public static final String TAB_SPLIT_LEFT = "workbench.editor.tab.split.left";
    /**
     * Stable text value for tab split up.
     */
    public static final String TAB_SPLIT_UP = "workbench.editor.tab.split.up";
    /**
     * Stable text value for tab duplicate.
     */
    public static final String TAB_DUPLICATE = "workbench.editor.tab.duplicate";
    /**
     * Stable text value for tab detach.
     */
    public static final String TAB_DETACH = "workbench.editor.tab.detach";
    /**
     * Stable text value for tab reattach.
     */
    public static final String TAB_REATTACH = "workbench.editor.tab.reattach";
    /**
     * Stable text value for tab close.
     */
    public static final String TAB_CLOSE = "workbench.editor.tab.close";
    /**
     * Stable text value for tab close others.
     */
    public static final String TAB_CLOSE_OTHERS = "workbench.editor.tab.closeOthers";
    /**
     * Stable text value for tabs restore closed.
     */
    public static final String TABS_RESTORE_CLOSED = "workbench.editor.tabs.restoreClosed";
    /**
     * Stable text value for tabs new or restore.
     */
    public static final String TABS_NEW_OR_RESTORE = "workbench.editor.tabs.newOrRestore";
    /**
     * Stable text value for tabs overflow.
     */
    public static final String TABS_OVERFLOW = "workbench.editor.tabs.overflow";
    /**
     * Stable text value for groups collapse.
     */
    public static final String GROUPS_COLLAPSE = "workbench.editor.groups.collapse";

    /**
     * Stable text value for split right accessible name.
     */
    public static final String SPLIT_RIGHT_ACCESSIBLE_NAME = "Split active tab right";
    /**
     * Stable text value for split right accessible description.
     */
    public static final String SPLIT_RIGHT_ACCESSIBLE_DESCRIPTION =
            "Creates an editor group to the right using the active tab.";
    /**
     * Stable text value for split down accessible name.
     */
    public static final String SPLIT_DOWN_ACCESSIBLE_NAME = "Split active tab below";
    /**
     * Stable text value for split right tooltip.
     */
    public static final String SPLIT_RIGHT_TOOLTIP = "Split active tab to the right";
    /**
     * Stable text value for split down tooltip.
     */
    public static final String SPLIT_DOWN_TOOLTIP = "Split active tab below";
    /**
     * Stable text value for split right disabled tooltip.
     */
    public static final String SPLIT_RIGHT_DISABLED_TOOLTIP =
            "Open another tab or select a duplicate-capable tab to split right";
    /**
     * Stable text value for split down disabled tooltip.
     */
    public static final String SPLIT_DOWN_DISABLED_TOOLTIP =
            "Open another tab or select a duplicate-capable tab to split below";

    /**
     * Stable text value for overflow accessible name.
     */
    public static final String OVERFLOW_ACCESSIBLE_NAME = "Show all open tabs";
    /**
     * Stable text value for overflow tooltip.
     */
    public static final String OVERFLOW_TOOLTIP = "Show all open tabs in this editor group";

    /**
     * Stable text value for new or restore accessible name.
     */
    public static final String NEW_OR_RESTORE_ACCESSIBLE_NAME = "New or restore tab";
    /**
     * Stable text value for new or restore accessible description.
     */
    public static final String NEW_OR_RESTORE_ACCESSIBLE_DESCRIPTION =
            "Opens another duplicate-capable tab or restores a closed workbench tab.";
    /**
     * Stable text value for new or restore tooltip.
     */
    public static final String NEW_OR_RESTORE_TOOLTIP = "New duplicate tab or restore a closed tab";

    /**
     * Stable text value for split disabled tooltip.
     */
    public static final String SPLIT_DISABLED_TOOLTIP =
            "Open another tab or select a duplicate-capable tab to split this tab.";
    /**
     * Stable text value for close others disabled tooltip.
     */
    public static final String CLOSE_OTHERS_DISABLED_TOOLTIP = "Open another tab before closing others.";
    /**
     * Stable text value for restore disabled tooltip.
     */
    public static final String RESTORE_CLOSED_DISABLED_TOOLTIP =
            "No closed or detached tabs to restore.";
    /**
     * Stable text value for detach disabled tooltip.
     */
    public static final String DETACH_DISABLED_TOOLTIP = "Open a tab before detaching it.";
    /**
     * Stable text value for tab split-right label.
     */
    public static final String TAB_SPLIT_RIGHT_LABEL = "Split Tab Right";
    /**
     * Stable text value for tab split-down label.
     */
    public static final String TAB_SPLIT_DOWN_LABEL = "Split Tab Down";
    /**
     * Stable text value for tab split-left label.
     */
    public static final String TAB_SPLIT_LEFT_LABEL = "Split Tab Left";
    /**
     * Stable text value for tab split-up label.
     */
    public static final String TAB_SPLIT_UP_LABEL = "Split Tab Up";
    /**
     * Stable text value for tab duplicate label.
     */
    public static final String TAB_DUPLICATE_LABEL = "Duplicate Tab";
    /**
     * Stable text value for tab detach label.
     */
    public static final String TAB_DETACH_LABEL = "Detach Tab to New Window";
    /**
     * Stable text value for tab close label.
     */
    public static final String TAB_CLOSE_LABEL = "Close Tab";
    /**
     * Stable text value for close-others label.
     */
    public static final String TAB_CLOSE_OTHERS_LABEL = "Close Other Tabs";
    /**
     * Stable text value for restore-closed label.
     */
    public static final String TABS_RESTORE_CLOSED_LABEL = "Restore Closed Tabs";
    /**
     * Stable text value for collapse-groups label.
     */
    public static final String GROUPS_COLLAPSE_LABEL = "Collapse Editor Groups";

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
