package application.gui.workbench.layout;

import application.gui.workbench.ui.Theme;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

/**
 * VS Code-style workbench shell. Holds every workbench panel and shows them
 * through closable editor tabs. Split mode behaves as VS Code-style editor
 * groups: each group owns its own tab strip, a tab can be moved into another
 * group by dropping it in that group's center, and edge or corner drops create
 * horizontal, vertical, or quadrant splits.
 */
public final class EditorSplitArea extends JPanel {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * No split drop zone armed.
     */
    private static final int DROP_NONE = 0;

    /**
     * Drop into the left side of a horizontal split.
     */
    private static final int DROP_LEFT = 1;

    /**
     * Drop into the right side of a horizontal split.
     */
    private static final int DROP_RIGHT = 2;

    /**
     * Drop into the top side of a vertical split.
     */
    private static final int DROP_TOP = 3;

    /**
     * Drop into the bottom side of a vertical split.
     */
    private static final int DROP_BOTTOM = 4;

    /**
     * Drop into the primary editor group without changing the split geometry.
     */
    private static final int DROP_PRIMARY_CENTER = 5;

    /**
     * Drop into the secondary editor group without changing the split geometry.
     */
    private static final int DROP_SECONDARY_CENTER = 6;

    /**
     * Drop into the top-left editor quadrant.
     */
    private static final int DROP_TOP_LEFT = 7;

    /**
     * Drop into the top-right editor quadrant.
     */
    private static final int DROP_TOP_RIGHT = 8;

    /**
     * Drop into the bottom-left editor quadrant.
     */
    private static final int DROP_BOTTOM_LEFT = 9;

    /**
     * Drop into the bottom-right editor quadrant.
     */
    private static final int DROP_BOTTOM_RIGHT = 10;

    /**
     * Drop into the tertiary editor group without changing the split geometry.
     */
    private static final int DROP_TERTIARY_CENTER = 11;

    /**
     * Drop into the quaternary editor group without changing the split geometry.
     */
    private static final int DROP_QUATERNARY_CENTER = 12;

    /**
     * Primary editor group id.
     */
    private static final int PANE_PRIMARY = 0;

    /**
     * Secondary editor group id.
     */
    private static final int PANE_SECONDARY = 1;

    /**
     * Tertiary editor group id.
     */
    private static final int PANE_TERTIARY = 2;

    /**
     * Quaternary editor group id.
     */
    private static final int PANE_QUATERNARY = 3;

    /**
     * Alpha for the VS Code-style drop target fill.
     */
    private static final int DROP_FILL_ALPHA = 32;

    /**
     * Alpha for the VS Code-style drop target outline.
     */
    private static final int DROP_BORDER_ALPHA = 190;

    /**
     * Panel display names.
     */
    private final transient List<String> names = new ArrayList<>();

    /**
     * Panel components, parallel to {@link #names}.
     */
    private final transient List<JComponent> panels = new ArrayList<>();

    /**
     * Panel indices currently open as tabs, in visible strip order.
     */
    private final transient List<Integer> open = new ArrayList<>();

    /**
     * Tabs owned by the primary editor group.
     */
    private final transient List<Integer> primaryTabs = new ArrayList<>();

    /**
     * Tabs owned by the secondary editor group.
     */
    private final transient List<Integer> secondaryTabs = new ArrayList<>();

    /**
     * Tabs owned by the tertiary editor group.
     */
    private final transient List<Integer> tertiaryTabs = new ArrayList<>();

    /**
     * Tabs owned by the quaternary editor group.
     */
    private final transient List<Integer> quaternaryTabs = new ArrayList<>();

    /**
     * Index shown in the primary editor group.
     */
    private int primaryIndex;

    /**
     * Index shown in the secondary editor group, or -1 when not split.
     */
    private int secondaryIndex = -1;

    /**
     * Index shown in the tertiary editor group, or -1 when hidden.
     */
    private int tertiaryIndex = -1;

    /**
     * Index shown in the quaternary editor group, or -1 when hidden.
     */
    private int quaternaryIndex = -1;

    /**
     * Armed split drop zone during a tab drag.
     */
    private int dragZone;

    /**
     * Active editor group for keyboard cycling.
     */
    private int activePane;

    /**
     * Current split orientation.
     */
    private int splitOrientation = JSplitPane.HORIZONTAL_SPLIT;

    /**
     * Last user-set horizontal divider location.
     */
    private int horizontalDividerLocation = -1;

    /**
     * Last user-set vertical divider location.
     */
    private int verticalDividerLocation = -1;

    /**
     * Current split pane, when split mode is active.
     */
    private JSplitPane splitPane;

    /**
     * Visible split panes from the current layout tree.
     */
    private final transient List<JSplitPane> splitPanes = new ArrayList<>();

    /**
     * Optional selection listener used by the window to start/stop work that
     * should only run while a pane is visible.
     */
    private transient IntConsumer selectionListener;

    /**
     * Primary editor group wrapper.
     */
    private final JPanel primaryPane = new JPanel(new BorderLayout(0, 0));

    /**
     * Secondary editor group wrapper.
     */
    private final JPanel secondaryPane = new JPanel(new BorderLayout(0, 0));

    /**
     * Tertiary editor group wrapper.
     */
    private final JPanel tertiaryPane = new JPanel(new BorderLayout(0, 0));

    /**
     * Quaternary editor group wrapper.
     */
    private final JPanel quaternaryPane = new JPanel(new BorderLayout(0, 0));

    /**
     * Host for the primary pane's selected panel.
     */
    private final JPanel primaryHost = new JPanel(new BorderLayout());

    /**
     * Host for the secondary pane's selected panel.
     */
    private final JPanel secondaryHost = new JPanel(new BorderLayout());

    /**
     * Host for the tertiary pane's selected panel.
     */
    private final JPanel tertiaryHost = new JPanel(new BorderLayout());

    /**
     * Host for the quaternary pane's selected panel.
     */
    private final JPanel quaternaryHost = new JPanel(new BorderLayout());

    /**
     * Primary tab strip.
     */
    private final JPanel primaryStrip = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));

    /**
     * Secondary tab strip, shown only when split.
     */
    private final JPanel secondaryStrip = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));

    /**
     * Tertiary tab strip, shown only when split.
     */
    private final JPanel tertiaryStrip = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));

    /**
     * Quaternary tab strip, shown only when split.
     */
    private final JPanel quaternaryStrip = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));

    /**
     * Centre area holding either one editor group or the split pane.
     */
    private final JPanel centre = new JPanel(new BorderLayout());

    /**
     * Split toggle button.
     */
    private final JToggleButton splitButton = new SplitGroupButton();

    /**
     * Creates an empty split area.
     */
    public EditorSplitArea() {
        super(new BorderLayout());
        setOpaque(true);
        setBackground(Theme.BG);
        primaryPane.setOpaque(true);
        primaryPane.setBackground(Theme.PANEL_SOLID);
        secondaryPane.setOpaque(true);
        secondaryPane.setBackground(Theme.PANEL_SOLID);
        tertiaryPane.setOpaque(true);
        tertiaryPane.setBackground(Theme.PANEL_SOLID);
        quaternaryPane.setOpaque(true);
        quaternaryPane.setBackground(Theme.PANEL_SOLID);
        primaryHost.setOpaque(true);
        primaryHost.setBackground(Theme.PANEL_SOLID);
        secondaryHost.setOpaque(true);
        secondaryHost.setBackground(Theme.PANEL_SOLID);
        tertiaryHost.setOpaque(true);
        tertiaryHost.setBackground(Theme.PANEL_SOLID);
        quaternaryHost.setOpaque(true);
        quaternaryHost.setBackground(Theme.PANEL_SOLID);
        centre.setOpaque(true);
        centre.setBackground(Theme.PANEL_SOLID);
        primaryStrip.setOpaque(true);
        primaryStrip.setBackground(Theme.BG);
        secondaryStrip.setOpaque(true);
        secondaryStrip.setBackground(Theme.BG);
        tertiaryStrip.setOpaque(true);
        tertiaryStrip.setBackground(Theme.BG);
        quaternaryStrip.setOpaque(true);
        quaternaryStrip.setBackground(Theme.BG);
        add(centre, BorderLayout.CENTER);
    }

    /**
     * Adds a workbench panel.
     *
     * @param name display name
     * @param panel panel component
     */
    public void addPanel(String name, JComponent panel) {
        int index = names.size();
        names.add(name);
        panels.add(panel);
        open.add(index);
        primaryTabs.add(index);
    }

    /**
     * Builds the tab strips and shows the first panel. Call once after every
     * panel has been added.
     */
    public void install() {
        splitButton.setToolTipText("Split editor group");
        splitButton.addActionListener(event -> toggleSplit());
        relayout();
    }

    /**
     * Returns the panel count.
     *
     * @return number of panels
     */
    public int count() {
        return panels.size();
    }

    /**
     * Returns the active editor group's selected panel index.
     *
     * @return selected index
     */
    public int selectedIndex() {
        int selected = paneIndex(activePane);
        return selected >= 0 ? selected : primaryIndex;
    }

    /**
     * Returns whether a panel is visible in either editor pane.
     *
     * @param index panel index
     * @return true when visible
     */
    public boolean isVisibleInPane(int index) {
        return primaryIndex == index
                || secondaryIndex == index
                || tertiaryIndex == index
                || quaternaryIndex == index;
    }

    /**
     * Installs a listener that runs after selection changes.
     *
     * @param listener listener, or null
     */
    public void setSelectionListener(IntConsumer listener) {
        selectionListener = listener;
    }

    /**
     * Selects a panel in the active editor group, reopening it when needed.
     *
     * @param index panel index
     */
    public void select(int index) {
        if (!validPanel(index)) {
            return;
        }
        int pane = paneContaining(index);
        if (pane >= 0) {
            setPaneSelection(pane, index);
        } else if (isSplitActive() && paneVisible(activePane)) {
            setPaneSelection(activePane, index);
        } else {
            setPrimary(index);
        }
    }

    /**
     * Selects the next open tab in the currently-active pane.
     */
    public void selectNextTab() {
        cycleActivePane(1);
    }

    /**
     * Selects the previous open tab in the currently-active pane.
     */
    public void selectPreviousTab() {
        cycleActivePane(-1);
    }

    /**
     * Shows a panel in the primary editor group.
     *
     * @param index panel index
     */
    private void setPrimary(int index) {
        setPaneSelection(PANE_PRIMARY, index);
    }

    /**
     * Shows a panel in the secondary editor group.
     *
     * @param index panel index
     */
    private void setSecondary(int index) {
        setPaneSelection(PANE_SECONDARY, index);
    }

    /**
     * Shows a panel in the tertiary editor group.
     *
     * @param index panel index
     */
    private void setTertiary(int index) {
        setPaneSelection(PANE_TERTIARY, index);
    }

    /**
     * Shows a panel in the quaternary editor group.
     *
     * @param index panel index
     */
    private void setQuaternary(int index) {
        setPaneSelection(PANE_QUATERNARY, index);
    }

    /**
     * Shows a panel in an editor group.
     *
     * @param pane editor group id
     * @param index panel index
     */
    private void setPaneSelection(int pane, int index) {
        if (!validPanel(index)) {
            return;
        }
        moveToPane(index, pane);
        setPaneIndex(pane, index);
        activePane = pane;
        relayout();
        notifySelectionChanged();
    }

    /**
     * Closes a tab, hiding its panel from the strips.
     *
     * @param index panel index
     */
    private void closeTab(int index) {
        if (open.size() <= 1) {
            return;
        }
        open.remove(Integer.valueOf(index));
        for (int pane = PANE_PRIMARY; pane <= PANE_QUATERNARY; pane++) {
            tabsForPane(pane).remove(Integer.valueOf(index));
            if (paneIndex(pane) == index) {
                setPaneIndex(pane, tabsForPane(pane).isEmpty() ? -1 : tabsForPane(pane).get(0));
            }
        }
        if (primaryIndex < 0 && !open.isEmpty()) {
            primaryIndex = firstOpen();
        }
        if (!paneVisible(activePane)) {
            activePane = firstVisiblePane();
        }
        relayout();
        notifySelectionChanged();
    }

    /**
     * Toggles the second editor group.
     */
    private void toggleSplit() {
        if (isSplitActive()) {
            collapseSplit();
        } else if (open.size() >= 2) {
            int candidate = firstOther(primaryIndex);
            if (candidate != primaryIndex) {
                secondaryTabs.clear();
                secondaryTabs.add(candidate);
                primaryTabs.remove(Integer.valueOf(candidate));
                secondaryIndex = candidate;
                activePane = PANE_SECONDARY;
            }
        }
        relayout();
        notifySelectionChanged();
    }

    /**
     * Cycles tabs in the active editor group.
     *
     * @param delta +1 next, -1 previous
     */
    private void cycleActivePane(int delta) {
        int pane = paneVisible(activePane) ? activePane : PANE_PRIMARY;
        List<Integer> tabs = tabsForPane(pane);
        if (tabs.isEmpty()) {
            return;
        }
        int current = paneIndex(pane);
        int pos = tabs.indexOf(current);
        if (pos < 0) {
            pos = 0;
        }
        int next = Math.floorMod(pos + delta, tabs.size());
        setPaneSelection(pane, tabs.get(next));
    }

    /**
     * Returns the first open index.
     *
     * @return first open panel index
     */
    private int firstOpen() {
        return open.isEmpty() ? 0 : open.get(0);
    }

    /**
     * Returns the first open index that is not {@code avoid}.
     *
     * @param avoid index to skip
     * @return another open index, or {@code avoid} when none
     */
    private int firstOther(int avoid) {
        for (int index : open) {
            if (index != avoid) {
                return index;
            }
        }
        return avoid;
    }

    /**
     * Rebuilds a tab strip for one editor group.
     *
     * @param strip strip panel
     * @param tabList tabs owned by the strip
     * @param activeIndex the strip's active panel index
     * @param pane editor group id
     */
    private void rebuildStrip(JPanel strip, List<Integer> tabList, int activeIndex, int pane) {
        strip.removeAll();
        for (int index : tabList) {
            int panelIndex = index;
            EditorTab tab = new EditorTab(names.get(index),
                    () -> setPaneSelection(pane, panelIndex),
                    () -> closeTab(panelIndex));
            tab.setSelected(index == activeIndex);
            tab.setDragHandler(
                    point -> handleTabDrag(strip, tabList, panelIndex, tab, point),
                    () -> finishTabDrag(panelIndex));
            strip.add(tab);
            tab.setPaneActive(activePane == pane || (!isSplitActive() && pane == PANE_PRIMARY));
        }
        if (open.size() < panels.size()) {
            strip.add(reopenButton(pane));
        }
        strip.revalidate();
        strip.repaint();
    }

    /**
     * Builds the {@code +} button that reopens closed tabs.
     *
     * @param pane editor group id
     * @return reopen button
     */
    private JComponent reopenButton(int pane) {
        JToggleButton plus = new JToggleButton("+");
        Theme.commandTab(plus);
        plus.setToolTipText("Reopen a closed panel");
        plus.addActionListener(event -> {
            plus.setSelected(false);
            JPopupMenu menu = new JPopupMenu();
            for (int i = 0; i < panels.size(); i++) {
                if (open.contains(i)) {
                    continue;
                }
                int index = i;
                JMenuItem item = new JMenuItem(names.get(i));
                item.addActionListener(choice -> setPaneSelection(pane, index));
                menu.add(item);
            }
            menu.show(plus, 0, plus.getHeight());
        });
        return plus;
    }

    /**
     * Handles a live tab drag. Inside the source strip the tab reorders; inside
     * the editor body it arms either a split drop zone or a center move into an
     * existing editor group.
     *
     * @param strip strip being dragged from
     * @param tabList tabs owned by the source strip
     * @param draggedPanelIndex panel index of the dragged tab
     * @param tab the dragged tab
     * @param tabPoint mouse point in the tab's coordinate space
     */
    private void handleTabDrag(
            JPanel strip,
            List<Integer> tabList,
            int draggedPanelIndex,
            EditorTab tab,
            Point tabPoint) {
        Point inArea = SwingUtilities.convertPoint(tab, tabPoint, this);
        Rectangle stripBounds = componentBounds(strip);
        stripBounds.grow(0, 8);
        if (stripBounds.contains(inArea)) {
            if (dragZone != DROP_NONE) {
                dragZone = DROP_NONE;
                repaint();
            }
            int stripX = SwingUtilities.convertPoint(tab, tabPoint, strip).x;
            reorderWithinStrip(strip, tabList, draggedPanelIndex, stripX);
            return;
        }

        Rectangle body = centre.getBounds();
        int zone = dropZoneFor(inArea, body);
        if (zone != dragZone) {
            dragZone = zone;
            repaint();
        }
    }

    /**
     * Chooses a body drop zone. Without a split, the familiar top/bottom and
     * left/right body regions create a second group. With a split, outer edge
     * bands still change split geometry, corner bands create quadrant groups,
     * and each group's center moves the dragged tab into that group.
     *
     * @param point point in this component
     * @param body editor body bounds
     * @return drop-zone constant
     */
    private int dropZoneFor(Point point, Rectangle body) {
        if (!body.contains(point)) {
            return DROP_NONE;
        }
        if (!isSplitActive()) {
            int topBand = body.y + Math.max(90, body.height / 4);
            int bottomBand = body.y + body.height - Math.max(90, body.height / 4);
            if (point.y < topBand) {
                return DROP_TOP;
            }
            if (point.y > bottomBand) {
                return DROP_BOTTOM;
            }
            return point.x < body.x + body.width / 2 ? DROP_LEFT : DROP_RIGHT;
        }

        int edge = Math.max(56, Math.min(120, Math.min(body.width, body.height) / 5));
        boolean top = point.y < body.y + edge;
        boolean bottom = point.y > body.y + body.height - edge;
        boolean left = point.x < body.x + edge;
        boolean right = point.x > body.x + body.width - edge;
        if (top && left) {
            return DROP_TOP_LEFT;
        }
        if (top && right) {
            return DROP_TOP_RIGHT;
        }
        if (bottom && left) {
            return DROP_BOTTOM_LEFT;
        }
        if (bottom && right) {
            return DROP_BOTTOM_RIGHT;
        }
        if (top) {
            return DROP_TOP;
        }
        if (bottom) {
            return DROP_BOTTOM;
        }
        if (left) {
            return DROP_LEFT;
        }
        if (right) {
            return DROP_RIGHT;
        }
        for (int pane = PANE_PRIMARY; pane <= PANE_QUATERNARY; pane++) {
            if (paneVisible(pane) && componentBounds(panePanel(pane)).contains(point)) {
                return centerDropZone(pane);
            }
        }
        return DROP_PRIMARY_CENTER;
    }

    /**
     * Live-reorders a tab within its owning strip.
     *
     * @param strip strip
     * @param tabList tabs owned by the strip
     * @param draggedPanelIndex panel index of the dragged tab
     * @param stripX drag x in the strip's coordinate space
     */
    private void reorderWithinStrip(JPanel strip, List<Integer> tabList, int draggedPanelIndex, int stripX) {
        List<EditorTab> tabs = new ArrayList<>();
        for (java.awt.Component child : strip.getComponents()) {
            if (child instanceof EditorTab tab) {
                tabs.add(tab);
            }
        }
        int from = tabList.indexOf(draggedPanelIndex);
        if (from < 0 || from >= tabs.size()) {
            return;
        }
        int target = 0;
        for (int i = 0; i < tabs.size(); i++) {
            if (i == from) {
                continue;
            }
            if (tabs.get(i).getX() + tabs.get(i).getWidth() / 2 < stripX) {
                target++;
            }
        }
        target = Math.max(0, Math.min(tabList.size() - 1, target));
        if (target == from) {
            return;
        }
        EditorTab dragged = tabs.get(from);
        tabList.add(target, tabList.remove(from));
        syncOpenFromGroups();
        strip.remove(dragged);
        strip.add(dragged, target);
        strip.revalidate();
        strip.repaint();
    }

    /**
     * Commits a tab drag.
     *
     * @param draggedPanelIndex panel index of the dragged tab
     */
    private void finishTabDrag(int draggedPanelIndex) {
        int zone = dragZone;
        dragZone = DROP_NONE;
        switch (zone) {
            case DROP_PRIMARY_CENTER -> setPrimary(draggedPanelIndex);
            case DROP_SECONDARY_CENTER -> setSecondary(draggedPanelIndex);
            case DROP_TERTIARY_CENTER -> setTertiary(draggedPanelIndex);
            case DROP_QUATERNARY_CENTER -> setQuaternary(draggedPanelIndex);
            case DROP_RIGHT -> {
                splitOrientation = JSplitPane.HORIZONTAL_SPLIT;
                splitWithDragged(draggedPanelIndex, false);
            }
            case DROP_BOTTOM -> {
                splitOrientation = JSplitPane.VERTICAL_SPLIT;
                splitWithDragged(draggedPanelIndex, false);
            }
            case DROP_LEFT -> {
                splitOrientation = JSplitPane.HORIZONTAL_SPLIT;
                splitWithDragged(draggedPanelIndex, true);
            }
            case DROP_TOP -> {
                splitOrientation = JSplitPane.VERTICAL_SPLIT;
                splitWithDragged(draggedPanelIndex, true);
            }
            case DROP_TOP_LEFT -> splitWithDraggedInPane(draggedPanelIndex, PANE_PRIMARY);
            case DROP_TOP_RIGHT -> splitWithDraggedInPane(draggedPanelIndex, PANE_SECONDARY);
            case DROP_BOTTOM_LEFT -> splitWithDraggedInPane(draggedPanelIndex, PANE_TERTIARY);
            case DROP_BOTTOM_RIGHT -> splitWithDraggedInPane(draggedPanelIndex, PANE_QUATERNARY);
            default -> relayout();
        }
    }

    /**
     * Creates a two-group split with the dragged tab alone on one side and the
     * remaining tabs in the other group.
     *
     * @param draggedPanelIndex dragged panel index
     * @param draggedInPrimary true when the dragged tab should become the
     *     primary group
     */
    private void splitWithDragged(int draggedPanelIndex, boolean draggedInPrimary) {
        if (!validPanel(draggedPanelIndex)) {
            return;
        }
        ensureOpen(draggedPanelIndex);
        if (open.size() < 2) {
            setPrimary(draggedPanelIndex);
            return;
        }
        rememberDividerLocation();
        List<Integer> others = new ArrayList<>();
        for (int index : open) {
            if (index != draggedPanelIndex) {
                others.add(index);
            }
        }
        primaryTabs.clear();
        secondaryTabs.clear();
        tertiaryTabs.clear();
        quaternaryTabs.clear();
        tertiaryIndex = -1;
        quaternaryIndex = -1;
        if (draggedInPrimary) {
            primaryTabs.add(draggedPanelIndex);
            secondaryTabs.addAll(others);
            primaryIndex = draggedPanelIndex;
            secondaryIndex = secondaryTabs.get(0);
            activePane = PANE_PRIMARY;
        } else {
            primaryTabs.addAll(others);
            secondaryTabs.add(draggedPanelIndex);
            primaryIndex = primaryTabs.contains(primaryIndex) ? primaryIndex : primaryTabs.get(0);
            secondaryIndex = draggedPanelIndex;
            activePane = PANE_SECONDARY;
        }
        syncOpenFromGroups();
        relayout();
        notifySelectionChanged();
    }

    /**
     * Splits a dragged tab into a specific editor-grid pane.
     *
     * @param draggedPanelIndex dragged panel index
     * @param targetPane target editor group id
     */
    private void splitWithDraggedInPane(int draggedPanelIndex, int targetPane) {
        if (!validPanel(draggedPanelIndex)) {
            return;
        }
        if (!isSplitActive()) {
            splitWithDragged(draggedPanelIndex, targetPane == PANE_PRIMARY || targetPane == PANE_TERTIARY);
            return;
        }
        ensureOpen(draggedPanelIndex);
        rememberDividerLocation();
        List<Integer> displaced = new ArrayList<>(tabsForPane(targetPane));
        for (int pane = PANE_PRIMARY; pane <= PANE_QUATERNARY; pane++) {
            tabsForPane(pane).remove(Integer.valueOf(draggedPanelIndex));
        }
        displaced.remove(Integer.valueOf(draggedPanelIndex));
        List<Integer> target = tabsForPane(targetPane);
        target.clear();
        target.add(draggedPanelIndex);
        setPaneIndex(targetPane, draggedPanelIndex);
        int paired = pairedPane(targetPane);
        for (int index : displaced) {
            if (!tabsForPane(paired).contains(index)) {
                tabsForPane(paired).add(index);
            }
        }
        if (!displaced.isEmpty() && paneIndex(paired) < 0) {
            setPaneIndex(paired, displaced.get(0));
        }
        activePane = targetPane;
        repairGroups();
        syncOpenFromGroups();
        relayout();
        notifySelectionChanged();
    }

    /**
     * Rebuilds the centre area and tab strips for the current state.
     */
    private void relayout() {
        rememberDividerLocation();
        repairGroups();
        centre.removeAll();
        splitPanes.clear();
        preparePane(PANE_PRIMARY);
        if (!isSplitActive()) {
            splitPane = null;
            splitButton.setSelected(false);
            centre.add(primaryPane, BorderLayout.CENTER);
        } else {
            splitButton.setSelected(true);
            for (int pane = PANE_SECONDARY; pane <= PANE_QUATERNARY; pane++) {
                if (paneVisible(pane)) {
                    preparePane(pane);
                }
            }
            Component layout = editorLayout();
            splitPane = layout instanceof JSplitPane root ? root : null;
            centre.add(layout, BorderLayout.CENTER);
        }
        centre.revalidate();
        centre.repaint();
    }

    /**
     * Rebuilds one editor-group pane.
     *
     * @param pane editor group id
     */
    private void preparePane(int pane) {
        JPanel panePanel = panePanel(pane);
        JPanel strip = paneStrip(pane);
        JPanel host = paneHost(pane);
        List<Integer> tabList = tabsForPane(pane);
        int activeIndex = paneIndex(pane);
        panePanel.removeAll();
        host.removeAll();
        if (validPanel(activeIndex)) {
            host.add(panels.get(activeIndex), BorderLayout.CENTER);
        }
        rebuildStrip(strip, tabList, activeIndex, pane);
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(true);
        header.setBackground(Theme.BG);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE));
        header.add(strip, BorderLayout.CENTER);
        if (pane == PANE_PRIMARY) {
            JPanel splitHolder = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 3));
            splitHolder.setOpaque(false);
            splitHolder.add(splitButton);
            header.add(splitHolder, BorderLayout.EAST);
        }
        panePanel.add(header, BorderLayout.NORTH);
        panePanel.add(host, BorderLayout.CENTER);
    }

    /**
     * Builds the visible editor-group split tree.
     *
     * @return editor layout component
     */
    private Component editorLayout() {
        Component left = columnLayout(PANE_PRIMARY, PANE_TERTIARY);
        Component right = columnLayout(PANE_SECONDARY, PANE_QUATERNARY);
        if (left != null && right != null) {
            return createSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        }
        if (left != null) {
            return left;
        }
        return right == null ? primaryPane : right;
    }

    /**
     * Builds one top/bottom editor column.
     *
     * @param topPane top editor group id
     * @param bottomPane bottom editor group id
     * @return column component, or null when neither group is visible
     */
    private Component columnLayout(int topPane, int bottomPane) {
        boolean topVisible = paneVisible(topPane);
        boolean bottomVisible = paneVisible(bottomPane);
        if (topVisible && bottomVisible) {
            return createSplitPane(JSplitPane.VERTICAL_SPLIT, panePanel(topPane), panePanel(bottomPane));
        }
        if (topVisible) {
            return panePanel(topPane);
        }
        return bottomVisible ? panePanel(bottomPane) : null;
    }

    /**
     * Creates a styled split pane and restores the remembered divider location
     * for its orientation when available.
     *
     * @param orientation split orientation
     * @param first first component
     * @param second second component
     * @return styled split pane
     */
    private JSplitPane createSplitPane(int orientation, Component first, Component second) {
        JSplitPane pane = new JSplitPane(orientation, first, second);
        SplitPaneStyler.style(pane);
        pane.setResizeWeight(0.5);
        int remembered = orientation == JSplitPane.VERTICAL_SPLIT
                ? verticalDividerLocation
                : horizontalDividerLocation;
        if (remembered > 0) {
            SwingUtilities.invokeLater(() -> pane.setDividerLocation(remembered));
        } else {
            pane.setDividerLocation(0.5);
        }
        splitPanes.add(pane);
        return pane;
    }

    /**
     * Ensures open tabs are assigned to exactly one editor group and selected
     * indices point at existing group members.
     */
    private void repairGroups() {
        List<Integer> assigned = new ArrayList<>();
        for (int pane = PANE_PRIMARY; pane <= PANE_QUATERNARY; pane++) {
            List<Integer> tabs = tabsForPane(pane);
            tabs.removeIf(index -> !open.contains(index) || !validPanel(index) || assigned.contains(index));
            assigned.addAll(tabs);
        }
        for (int index : open) {
            if (!assigned.contains(index) && validPanel(index)) {
                primaryTabs.add(index);
                assigned.add(index);
            }
        }
        if (open.isEmpty()) {
            primaryIndex = 0;
            secondaryIndex = -1;
            tertiaryIndex = -1;
            quaternaryIndex = -1;
            activePane = PANE_PRIMARY;
            return;
        }
        if (primaryTabs.isEmpty()) {
            for (int pane = PANE_SECONDARY; pane <= PANE_QUATERNARY; pane++) {
                if (!tabsForPane(pane).isEmpty()) {
                    int moved = tabsForPane(pane).remove(0);
                    primaryTabs.add(moved);
                    primaryIndex = moved;
                    break;
                }
            }
        }
        for (int pane = PANE_PRIMARY; pane <= PANE_QUATERNARY; pane++) {
            List<Integer> tabs = tabsForPane(pane);
            if (tabs.isEmpty()) {
                setPaneIndex(pane, pane == PANE_PRIMARY ? firstOpen() : -1);
            } else if (!tabs.contains(paneIndex(pane))) {
                setPaneIndex(pane, tabs.get(0));
            }
        }
        if (!paneVisible(activePane)) {
            activePane = firstVisiblePane();
        }
        syncOpenFromGroups();
    }

    /**
     * Moves a panel tab to an editor group, reopening it when needed.
     *
     * @param index panel index
     * @param pane target editor group id
     */
    private void moveToPane(int index, int pane) {
        ensureOpen(index);
        List<Integer> target = tabsForPane(pane);
        for (int candidate = PANE_PRIMARY; candidate <= PANE_QUATERNARY; candidate++) {
            if (candidate != pane) {
                tabsForPane(candidate).remove(Integer.valueOf(index));
            }
        }
        if (!target.contains(index)) {
            target.add(index);
        }
        setPaneIndex(pane, index);
        repairGroups();
    }

    /**
     * Reopens a panel if it is currently closed.
     *
     * @param index panel index
     */
    private void ensureOpen(int index) {
        if (!open.contains(index)) {
            open.add(index);
        }
    }

    /**
     * Collapses split mode, preserving tab order by appending secondary-group
     * tabs after primary-group tabs.
     */
    private void collapseSplit() {
        rememberDividerLocation();
        for (int pane = PANE_SECONDARY; pane <= PANE_QUATERNARY; pane++) {
            for (int index : tabsForPane(pane)) {
                if (!primaryTabs.contains(index)) {
                    primaryTabs.add(index);
                }
            }
            tabsForPane(pane).clear();
            setPaneIndex(pane, -1);
        }
        activePane = PANE_PRIMARY;
        splitPane = null;
        splitPanes.clear();
        syncOpenFromGroups();
    }

    /**
     * Rebuilds the global open-tab list from the editor groups.
     */
    private void syncOpenFromGroups() {
        open.clear();
        for (int index : primaryTabs) {
            if (!open.contains(index) && validPanel(index)) {
                open.add(index);
            }
        }
        for (int index : secondaryTabs) {
            if (!open.contains(index) && validPanel(index)) {
                open.add(index);
            }
        }
        for (int index : tertiaryTabs) {
            if (!open.contains(index) && validPanel(index)) {
                open.add(index);
            }
        }
        for (int index : quaternaryTabs) {
            if (!open.contains(index) && validPanel(index)) {
                open.add(index);
            }
        }
    }

    /**
     * Returns whether split mode is active.
     *
     * @return true when any non-primary editor group is visible
     */
    private boolean isSplitActive() {
        return paneVisible(PANE_SECONDARY) || paneVisible(PANE_TERTIARY) || paneVisible(PANE_QUATERNARY);
    }

    /**
     * Returns whether a panel index is valid.
     *
     * @param index panel index
     * @return true when valid
     */
    private boolean validPanel(int index) {
        return index >= 0 && index < panels.size();
    }

    /**
     * Returns the tab list for an editor group.
     *
     * @param pane editor group id
     * @return tab list
     */
    private List<Integer> tabsForPane(int pane) {
        return switch (pane) {
            case PANE_SECONDARY -> secondaryTabs;
            case PANE_TERTIARY -> tertiaryTabs;
            case PANE_QUATERNARY -> quaternaryTabs;
            default -> primaryTabs;
        };
    }

    /**
     * Returns the selected panel index for an editor group.
     *
     * @param pane editor group id
     * @return selected panel index, or -1 when hidden
     */
    private int paneIndex(int pane) {
        return switch (pane) {
            case PANE_SECONDARY -> secondaryIndex;
            case PANE_TERTIARY -> tertiaryIndex;
            case PANE_QUATERNARY -> quaternaryIndex;
            default -> primaryIndex;
        };
    }

    /**
     * Sets the selected panel index for an editor group.
     *
     * @param pane editor group id
     * @param index selected panel index
     */
    private void setPaneIndex(int pane, int index) {
        switch (pane) {
            case PANE_SECONDARY -> secondaryIndex = index;
            case PANE_TERTIARY -> tertiaryIndex = index;
            case PANE_QUATERNARY -> quaternaryIndex = index;
            default -> primaryIndex = index;
        }
    }

    /**
     * Returns the pane wrapper for an editor group.
     *
     * @param pane editor group id
     * @return pane wrapper
     */
    private JPanel panePanel(int pane) {
        return switch (pane) {
            case PANE_SECONDARY -> secondaryPane;
            case PANE_TERTIARY -> tertiaryPane;
            case PANE_QUATERNARY -> quaternaryPane;
            default -> primaryPane;
        };
    }

    /**
     * Returns the tab strip for an editor group.
     *
     * @param pane editor group id
     * @return tab strip
     */
    private JPanel paneStrip(int pane) {
        return switch (pane) {
            case PANE_SECONDARY -> secondaryStrip;
            case PANE_TERTIARY -> tertiaryStrip;
            case PANE_QUATERNARY -> quaternaryStrip;
            default -> primaryStrip;
        };
    }

    /**
     * Returns the selected-panel host for an editor group.
     *
     * @param pane editor group id
     * @return selected-panel host
     */
    private JPanel paneHost(int pane) {
        return switch (pane) {
            case PANE_SECONDARY -> secondaryHost;
            case PANE_TERTIARY -> tertiaryHost;
            case PANE_QUATERNARY -> quaternaryHost;
            default -> primaryHost;
        };
    }

    /**
     * Returns whether an editor group is visible.
     *
     * @param pane editor group id
     * @return true when the group has tabs and a selected panel
     */
    private boolean paneVisible(int pane) {
        return paneIndex(pane) >= 0 && !tabsForPane(pane).isEmpty();
    }

    /**
     * Returns the editor group containing a panel.
     *
     * @param index panel index
     * @return editor group id, or -1 when the panel is closed
     */
    private int paneContaining(int index) {
        for (int pane = PANE_PRIMARY; pane <= PANE_QUATERNARY; pane++) {
            if (tabsForPane(pane).contains(index)) {
                return pane;
            }
        }
        return -1;
    }

    /**
     * Returns the first visible editor group.
     *
     * @return visible editor group id
     */
    private int firstVisiblePane() {
        for (int pane = PANE_PRIMARY; pane <= PANE_QUATERNARY; pane++) {
            if (paneVisible(pane)) {
                return pane;
            }
        }
        return PANE_PRIMARY;
    }

    /**
     * Returns the same-column paired group used when a corner split displaces
     * an existing group.
     *
     * @param pane editor group id
     * @return paired editor group id
     */
    private static int pairedPane(int pane) {
        return switch (pane) {
            case PANE_PRIMARY -> PANE_TERTIARY;
            case PANE_SECONDARY -> PANE_QUATERNARY;
            case PANE_TERTIARY -> PANE_PRIMARY;
            case PANE_QUATERNARY -> PANE_SECONDARY;
            default -> PANE_SECONDARY;
        };
    }

    /**
     * Returns the center drop-zone id for an editor group.
     *
     * @param pane editor group id
     * @return drop-zone constant
     */
    private static int centerDropZone(int pane) {
        return switch (pane) {
            case PANE_SECONDARY -> DROP_SECONDARY_CENTER;
            case PANE_TERTIARY -> DROP_TERTIARY_CENTER;
            case PANE_QUATERNARY -> DROP_QUATERNARY_CENTER;
            default -> DROP_PRIMARY_CENTER;
        };
    }

    /**
     * Returns component bounds converted into this split area's coordinate
     * space.
     *
     * @param component component
     * @return converted bounds
     */
    private Rectangle componentBounds(JComponent component) {
        return SwingUtilities.convertRectangle(component,
                new Rectangle(0, 0, component.getWidth(), component.getHeight()), this);
    }

    /**
     * Notifies the selection listener.
     */
    private void notifySelectionChanged() {
        if (selectionListener != null) {
            selectionListener.accept(selectedIndex());
        }
    }

    /**
     * Remembers the current divider location before the split pane is rebuilt.
     */
    private void rememberDividerLocation() {
        if (splitPanes.isEmpty() || !isSplitActive()) {
            return;
        }
        for (JSplitPane pane : splitPanes) {
            int location = pane.getDividerLocation();
            if (location <= 0) {
                continue;
            }
            if (pane.getOrientation() == JSplitPane.VERTICAL_SPLIT) {
                verticalDividerLocation = location;
            } else {
                horizontalDividerLocation = location;
            }
        }
    }

    /**
     * Paints the children, then the drop-zone hint while a tab is being
     * dragged into the editor body.
     *
     * @param graphics graphics
     */
    @Override
    public void paint(Graphics graphics) {
        super.paint(graphics);
        if (dragZone == DROP_NONE) {
            return;
        }
        Rectangle body = centre.getBounds();
        Rectangle zone = dropPreviewBounds(body, dragZone);
        if (zone.isEmpty()) {
            return;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(Theme.ACCENT.getRed(), Theme.ACCENT.getGreen(),
                    Theme.ACCENT.getBlue(), DROP_FILL_ALPHA));
            g.fillRect(zone.x, zone.y, zone.width, zone.height);
            g.setColor(Theme.withAlpha(Theme.ACCENT, DROP_BORDER_ALPHA));
            g.setStroke(new BasicStroke(1f));
            g.drawRect(zone.x, zone.y, Math.max(0, zone.width - 1), Math.max(0, zone.height - 1));
        } finally {
            g.dispose();
        }
    }

    /**
     * Returns the visible preview rectangle for a drop zone.
     *
     * @param body editor body
     * @param zone drop-zone constant
     * @return preview bounds
     */
    private Rectangle dropPreviewBounds(Rectangle body, int zone) {
        int halfW = body.width / 2;
        int halfH = body.height / 2;
        int inset = 6;
        return switch (zone) {
            case DROP_LEFT -> new Rectangle(body.x + inset, body.y + inset,
                    Math.max(1, halfW - inset * 2), Math.max(1, body.height - inset * 2));
            case DROP_RIGHT -> new Rectangle(body.x + halfW + inset, body.y + inset,
                    Math.max(1, body.width - halfW - inset * 2), Math.max(1, body.height - inset * 2));
            case DROP_TOP -> new Rectangle(body.x + inset, body.y + inset,
                    Math.max(1, body.width - inset * 2), Math.max(1, halfH - inset * 2));
            case DROP_BOTTOM -> new Rectangle(body.x + inset, body.y + halfH + inset,
                    Math.max(1, body.width - inset * 2), Math.max(1, body.height - halfH - inset * 2));
            case DROP_TOP_LEFT -> new Rectangle(body.x + inset, body.y + inset,
                    Math.max(1, halfW - inset * 2), Math.max(1, halfH - inset * 2));
            case DROP_TOP_RIGHT -> new Rectangle(body.x + halfW + inset, body.y + inset,
                    Math.max(1, body.width - halfW - inset * 2), Math.max(1, halfH - inset * 2));
            case DROP_BOTTOM_LEFT -> new Rectangle(body.x + inset, body.y + halfH + inset,
                    Math.max(1, halfW - inset * 2), Math.max(1, body.height - halfH - inset * 2));
            case DROP_BOTTOM_RIGHT -> new Rectangle(body.x + halfW + inset, body.y + halfH + inset,
                    Math.max(1, body.width - halfW - inset * 2), Math.max(1, body.height - halfH - inset * 2));
            case DROP_PRIMARY_CENTER -> inset(componentBounds(primaryPane), inset);
            case DROP_SECONDARY_CENTER -> inset(componentBounds(secondaryPane), inset);
            case DROP_TERTIARY_CENTER -> inset(componentBounds(tertiaryPane), inset);
            case DROP_QUATERNARY_CENTER -> inset(componentBounds(quaternaryPane), inset);
            default -> new Rectangle();
        };
    }

    /**
     * Insets a rectangle without allowing negative dimensions.
     *
     * @param rectangle source rectangle
     * @param amount inset amount
     * @return inset rectangle
     */
    private static Rectangle inset(Rectangle rectangle, int amount) {
        return new Rectangle(rectangle.x + amount, rectangle.y + amount,
                Math.max(1, rectangle.width - amount * 2),
                Math.max(1, rectangle.height - amount * 2));
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(900, 620);
    }

    /**
     * Compact split-editor action button. VS Code presents editor-group actions
     * as icon controls in the title strip; this avoids a text pill in the tab
     * header.
     */
    private static final class SplitGroupButton extends JToggleButton {

        /**
         * Serialization identifier for Swing button compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Fixed action button size.
         */
        private static final int SIZE = 28;

        /**
         * Whether the pointer is hovering over the button.
         */
        private boolean hover;

        /**
         * Creates the split-group action.
         */
        SplitGroupButton() {
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(SIZE, SIZE));
            setMinimumSize(new Dimension(SIZE, SIZE));
            setMaximumSize(new Dimension(SIZE, SIZE));
            getAccessibleContext().setAccessibleName("Split editor group");
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent event) {
                    setHover(true);
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    setHover(false);
                }
            });
        }

        /**
         * Updates hover state.
         *
         * @param value true while hovered
         */
        private void setHover(boolean value) {
            if (hover != value) {
                hover = value;
                repaint();
            }
        }

        /**
         * Paints the split-group icon.
         *
         * @param graphics graphics context
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                if (isSelected() || hover) {
                    g.setColor(isSelected()
                            ? Theme.withAlpha(Theme.ACCENT, 30)
                            : Theme.TAB_HOVER);
                    g.fillRect(2, 2, Math.max(0, w - 4), Math.max(0, h - 4));
                }
                Color stroke = isSelected() ? Theme.ACCENT : Theme.MUTED;
                g.setColor(stroke);
                g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int x = (w - 16) / 2;
                int y = (h - 14) / 2;
                g.drawRect(x, y, 16, 14);
                g.drawLine(x + 8, y, x + 8, y + 14);
                if (isSelected()) {
                    g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.drawLine(x + 10, y + 3, x + 14, y + 3);
                    g.drawLine(x + 10, y + 7, x + 14, y + 7);
                    g.drawLine(x + 10, y + 11, x + 14, y + 11);
                }
            } finally {
                g.dispose();
            }
        }
    }
}
