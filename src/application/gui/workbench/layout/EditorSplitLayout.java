package application.gui.workbench.layout;

import java.awt.Component;
import java.util.List;
import javax.swing.JSplitPane;

/**
 * Builds styled split panes for editor-group layouts.
 */
final class EditorSplitLayout {

    /**
     * Prevents instantiation.
     */
    private EditorSplitLayout() {
        // utility class
    }

    /**
     * Creates a styled split pane and restores the remembered divider location
     * for its orientation when available.
     *
     * @param orientation split orientation
     * @param first first component
     * @param second second component
     * @param verticalDividerLocation last vertical divider location
     * @param horizontalDividerLocation last horizontal divider location
     * @param splitPanes destination list for visible split panes
     * @return styled split pane
     */
    static JSplitPane createSplitPane(
            int orientation,
            Component first,
            Component second,
            int verticalDividerLocation,
            int horizontalDividerLocation,
            List<JSplitPane> splitPanes) {
        JSplitPane pane = new JSplitPane(orientation, first, second);
        SplitPaneStyler.style(pane);
        pane.setResizeWeight(0.5);
        int remembered = orientation == JSplitPane.VERTICAL_SPLIT
                ? verticalDividerLocation
                : horizontalDividerLocation;
        if (remembered > 0) {
            pane.setDividerLocation(remembered);
        } else {
            pane.setDividerLocation(0.5);
        }
        splitPanes.add(pane);
        return pane;
    }
}
