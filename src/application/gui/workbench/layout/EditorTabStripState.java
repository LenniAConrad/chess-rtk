package application.gui.workbench.layout;

import java.awt.Component;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * Stores and refreshes lightweight structural state for editor tab strips.
 */
final class EditorTabStripState {

    /**
     * Client-property key storing the backing workbench panel index for a tab
     * component.
     */
    private static final String TAB_INDEX_PROPERTY = "crtk.editor.tabIndex";

    /**
     * Client-property key marking the new-or-restore tab affordance.
     */
    private static final String REOPEN_BUTTON_PROPERTY = "crtk.editor.reopenButton";

    /**
     * Client-property key storing the open-tab count that shaped a tab strip.
     */
    private static final String STRIP_OPEN_SIZE_PROPERTY = "crtk.editor.stripOpenSize";

    /**
     * Client-property key storing the registered-panel count that shaped a tab
     * strip.
     */
    private static final String STRIP_PANEL_SIZE_PROPERTY = "crtk.editor.stripPanelSize";

    /**
     * Client-property key storing the duplicate-capable panel count for a tab
     * strip.
     */
    private static final String STRIP_FACTORY_COUNT_PROPERTY = "crtk.editor.stripFactoryCount";

    /**
     * Client-property key storing whether a strip was built while split mode
     * was active.
     */
    private static final String STRIP_SPLIT_ACTIVE_PROPERTY = "crtk.editor.stripSplitActive";

    /**
     * Prevents instantiation.
     */
    private EditorTabStripState() {
        // utility class
    }

    /**
     * Marks an editor tab with its backing panel index.
     *
     * @param tab tab component
     * @param panelIndex panel index
     */
    static void markTab(EditorTab tab, int panelIndex) {
        tab.putClientProperty(TAB_INDEX_PROPERTY, Integer.valueOf(panelIndex));
    }

    /**
     * Marks the restore-or-new tab button.
     *
     * @param button button component
     */
    static void markReopenButton(JComponent button) {
        button.putClientProperty(REOPEN_BUTTON_PROPERTY, Boolean.TRUE);
    }

    /**
     * Updates an existing tab strip without recreating tab components when the
     * tab membership and order have not changed.
     *
     * @param strip strip panel
     * @param tabList tabs owned by the strip
     * @param activeIndex the strip's active panel index
     * @param paneActive true when the owning editor group is active
     * @param splitActive true when any editor split is active
     * @param openSize current open-tab count
     * @param panelSize registered panel count
     * @param factoryCount duplicate-capable panel count
     * @param needsReopenButton true when the strip should include a restore button
     * @return true when the existing strip could be reused
     */
    static boolean refreshInPlace(
            JPanel strip,
            List<Integer> tabList,
            int activeIndex,
            boolean paneActive,
            boolean splitActive,
            int openSize,
            int panelSize,
            int factoryCount,
            boolean needsReopenButton) {
        if (!stripMatches(strip, tabList, splitActive, openSize, panelSize, factoryCount, needsReopenButton)) {
            return false;
        }
        for (Component child : strip.getComponents()) {
            if (child instanceof EditorTab tab) {
                tab.setSelected(tabIndex(child) == activeIndex);
                tab.setPaneActive(paneActive);
            }
        }
        strip.repaint();
        return true;
    }

    /**
     * Stores the structural state used by context menus and restore affordances
     * for a rebuilt strip.
     *
     * @param strip strip panel
     * @param splitActive true when any editor split is active
     * @param openSize current open-tab count
     * @param panelSize registered panel count
     * @param factoryCount duplicate-capable panel count
     */
    static void remember(
            JPanel strip,
            boolean splitActive,
            int openSize,
            int panelSize,
            int factoryCount) {
        strip.putClientProperty(STRIP_OPEN_SIZE_PROPERTY, Integer.valueOf(openSize));
        strip.putClientProperty(STRIP_PANEL_SIZE_PROPERTY, Integer.valueOf(panelSize));
        strip.putClientProperty(STRIP_FACTORY_COUNT_PROPERTY, Integer.valueOf(factoryCount));
        strip.putClientProperty(STRIP_SPLIT_ACTIVE_PROPERTY, Boolean.valueOf(splitActive));
    }

    /**
     * Returns whether the strip already contains exactly the requested tab
     * sequence and restore affordance.
     *
     * @param strip strip panel
     * @param tabList desired tab indices
     * @param splitActive true when any editor split is active
     * @param openSize current open-tab count
     * @param panelSize registered panel count
     * @param factoryCount duplicate-capable panel count
     * @param needsReopenButton true when the strip should include a restore button
     * @return true when the strip can be updated in place
     */
    private static boolean stripMatches(
            JPanel strip,
            List<Integer> tabList,
            boolean splitActive,
            int openSize,
            int panelSize,
            int factoryCount,
            boolean needsReopenButton) {
        if (!stripStateMatches(strip, splitActive, openSize, panelSize, factoryCount)) {
            return false;
        }
        int tabPosition = 0;
        boolean hasReopenButton = false;
        for (Component child : strip.getComponents()) {
            if (child instanceof EditorTab) {
                if (tabPosition >= tabList.size() || tabIndex(child) != tabList.get(tabPosition)) {
                    return false;
                }
                tabPosition++;
            } else if (isReopenButton(child)) {
                hasReopenButton = true;
            } else {
                return false;
            }
        }
        return tabPosition == tabList.size() && hasReopenButton == needsReopenButton;
    }

    /**
     * Returns whether an existing strip was built with the current structural
     * state.
     *
     * @param strip strip panel
     * @param splitActive true when any editor split is active
     * @param openSize current open-tab count
     * @param panelSize registered panel count
     * @param factoryCount duplicate-capable panel count
     * @return true when the strip can keep its tab components
     */
    private static boolean stripStateMatches(
            JPanel strip,
            boolean splitActive,
            int openSize,
            int panelSize,
            int factoryCount) {
        return Integer.valueOf(openSize).equals(strip.getClientProperty(STRIP_OPEN_SIZE_PROPERTY))
                && Integer.valueOf(panelSize).equals(strip.getClientProperty(STRIP_PANEL_SIZE_PROPERTY))
                && Integer.valueOf(factoryCount).equals(strip.getClientProperty(STRIP_FACTORY_COUNT_PROPERTY))
                && Boolean.valueOf(splitActive).equals(strip.getClientProperty(STRIP_SPLIT_ACTIVE_PROPERTY));
    }

    /**
     * Returns the panel index associated with a tab component.
     *
     * @param component tab component
     * @return panel index, or -1 when unavailable
     */
    private static int tabIndex(Component component) {
        if (!(component instanceof JComponent tab)) {
            return -1;
        }
        Object value = tab.getClientProperty(TAB_INDEX_PROPERTY);
        return value instanceof Integer index ? index.intValue() : -1;
    }

    /**
     * Returns whether a component is the restore-or-new tab button.
     *
     * @param component component to inspect
     * @return true when the component is the tab restore affordance
     */
    private static boolean isReopenButton(Component component) {
        return component instanceof JComponent button
                && Boolean.TRUE.equals(button.getClientProperty(REOPEN_BUTTON_PROPERTY));
    }
}
