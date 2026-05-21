package application.gui.workbench.layout;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
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

import application.gui.workbench.WorkbenchTheme;

/**
 * VS Code-style workbench shell. Holds every workbench panel and shows them
 * through closable editor tabs. Split mode behaves as two editor groups: each
 * group owns its own tab strip, a tab can be moved into the other group by
 * dropping it in that group's center, and edge drops create horizontal or
 * vertical splits.
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
     * Index shown in the primary editor group.
     */
    private int primaryIndex;

    /**
     * Index shown in the secondary editor group, or -1 when not split.
     */
    private int secondaryIndex = -1;

    /**
     * Armed split drop zone during a tab drag.
     */
    private int dragZone;

    /**
     * Active editor group for keyboard cycling: 0 primary, 1 secondary.
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
     * Host for the primary pane's selected panel.
     */
    private final JPanel primaryHost = new JPanel(new BorderLayout());

    /**
     * Host for the secondary pane's selected panel.
     */
    private final JPanel secondaryHost = new JPanel(new BorderLayout());

    /**
     * Primary tab strip.
     */
    private final JPanel primaryStrip = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));

    /**
     * Secondary tab strip, shown only when split.
     */
    private final JPanel secondaryStrip = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));

    /**
     * Centre area holding either one editor group or the split pane.
     */
    private final JPanel centre = new JPanel(new BorderLayout());

    /**
     * Split toggle button.
     */
    private final JToggleButton splitButton = new JToggleButton("Split");

    /**
     * Creates an empty split area.
     */
    public EditorSplitArea() {
        super(new BorderLayout());
        setOpaque(true);
        setBackground(WorkbenchTheme.BG);
        primaryPane.setOpaque(true);
        primaryPane.setBackground(WorkbenchTheme.PANEL_SOLID);
        secondaryPane.setOpaque(true);
        secondaryPane.setBackground(WorkbenchTheme.PANEL_SOLID);
        primaryHost.setOpaque(true);
        primaryHost.setBackground(WorkbenchTheme.PANEL_SOLID);
        secondaryHost.setOpaque(true);
        secondaryHost.setBackground(WorkbenchTheme.PANEL_SOLID);
        centre.setOpaque(true);
        centre.setBackground(WorkbenchTheme.PANEL_SOLID);
        primaryStrip.setOpaque(true);
        primaryStrip.setBackground(WorkbenchTheme.BG);
        secondaryStrip.setOpaque(true);
        secondaryStrip.setBackground(WorkbenchTheme.BG);
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
        WorkbenchTheme.commandTab(splitButton);
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
        return activePane == 1 && isSplitActive() ? secondaryIndex : primaryIndex;
    }

    /**
     * Returns whether a panel is visible in either editor pane.
     *
     * @param index panel index
     * @return true when visible
     */
    public boolean isVisibleInPane(int index) {
        return primaryIndex == index || secondaryIndex == index;
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
        if (secondaryTabs.contains(index)) {
            setSecondary(index);
        } else if (primaryTabs.contains(index)) {
            setPrimary(index);
        } else if (activePane == 1 && isSplitActive()) {
            setSecondary(index);
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
        if (!validPanel(index)) {
            return;
        }
        moveToPane(index, 0);
        primaryIndex = index;
        activePane = 0;
        relayout();
        notifySelectionChanged();
    }

    /**
     * Shows a panel in the secondary editor group.
     *
     * @param index panel index
     */
    private void setSecondary(int index) {
        if (!validPanel(index)) {
            return;
        }
        moveToPane(index, 1);
        secondaryIndex = index;
        activePane = 1;
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
        primaryTabs.remove(Integer.valueOf(index));
        secondaryTabs.remove(Integer.valueOf(index));
        if (primaryIndex == index) {
            primaryIndex = primaryTabs.isEmpty() ? firstOpen() : primaryTabs.get(0);
        }
        if (secondaryIndex == index) {
            secondaryIndex = secondaryTabs.isEmpty() ? -1 : secondaryTabs.get(0);
            if (activePane == 1 && secondaryIndex < 0) {
                activePane = 0;
            }
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
                activePane = 1;
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
        List<Integer> tabs = activePane == 1 && isSplitActive() ? secondaryTabs : primaryTabs;
        if (tabs.isEmpty()) {
            return;
        }
        int current = activePane == 1 && isSplitActive() ? secondaryIndex : primaryIndex;
        int pos = tabs.indexOf(current);
        if (pos < 0) {
            pos = 0;
        }
        int next = Math.floorMod(pos + delta, tabs.size());
        if (activePane == 1 && isSplitActive()) {
            setSecondary(tabs.get(next));
        } else {
            setPrimary(tabs.get(next));
        }
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
     * @param primary true for the primary strip
     */
    private void rebuildStrip(JPanel strip, List<Integer> tabList, int activeIndex, boolean primary) {
        strip.removeAll();
        for (int index : tabList) {
            int panelIndex = index;
            EditorTab tab = new EditorTab(names.get(index),
                    () -> {
                        if (primary) {
                            setPrimary(panelIndex);
                        } else {
                            setSecondary(panelIndex);
                        }
                    },
                    () -> closeTab(panelIndex));
            tab.setSelected(index == activeIndex);
            tab.setDragHandler(
                    point -> handleTabDrag(strip, tabList, panelIndex, tab, point),
                    () -> finishTabDrag(panelIndex));
            strip.add(tab);
            tab.setPaneActive(primary ? activePane == 0 || !isSplitActive() : activePane == 1);
        }
        if (open.size() < panels.size()) {
            strip.add(reopenButton(primary));
        }
        strip.revalidate();
        strip.repaint();
    }

    /**
     * Builds the {@code +} button that reopens closed tabs.
     *
     * @param primary true for the primary strip
     * @return reopen button
     */
    private JComponent reopenButton(boolean primary) {
        JToggleButton plus = new JToggleButton("+");
        WorkbenchTheme.commandTab(plus);
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
                item.addActionListener(choice -> {
                    if (primary) {
                        setPrimary(index);
                    } else {
                        setSecondary(index);
                    }
                });
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
     * bands still change split geometry, while each group's center moves the
     * dragged tab into that group.
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
        if (point.y < body.y + edge) {
            return DROP_TOP;
        }
        if (point.y > body.y + body.height - edge) {
            return DROP_BOTTOM;
        }
        if (point.x < body.x + edge) {
            return DROP_LEFT;
        }
        if (point.x > body.x + body.width - edge) {
            return DROP_RIGHT;
        }
        if (componentBounds(secondaryPane).contains(point)) {
            return DROP_SECONDARY_CENTER;
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
        if (draggedInPrimary) {
            primaryTabs.add(draggedPanelIndex);
            secondaryTabs.addAll(others);
            primaryIndex = draggedPanelIndex;
            secondaryIndex = secondaryTabs.get(0);
            activePane = 0;
        } else {
            primaryTabs.addAll(others);
            secondaryTabs.add(draggedPanelIndex);
            primaryIndex = primaryTabs.contains(primaryIndex) ? primaryIndex : primaryTabs.get(0);
            secondaryIndex = draggedPanelIndex;
            activePane = 1;
        }
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
        preparePane(primaryPane, primaryStrip, primaryHost, primaryTabs, primaryIndex, true);
        if (!isSplitActive()) {
            splitPane = null;
            splitButton.setSelected(false);
            centre.add(primaryPane, BorderLayout.CENTER);
        } else {
            splitButton.setSelected(true);
            preparePane(secondaryPane, secondaryStrip, secondaryHost, secondaryTabs, secondaryIndex, false);
            splitPane = new JSplitPane(splitOrientation, primaryPane, secondaryPane);
            SplitPaneStyler.style(splitPane);
            splitPane.setResizeWeight(0.5);
            int remembered = splitOrientation == JSplitPane.VERTICAL_SPLIT
                    ? verticalDividerLocation
                    : horizontalDividerLocation;
            if (remembered > 0) {
                JSplitPane currentSplit = splitPane;
                SwingUtilities.invokeLater(() -> currentSplit.setDividerLocation(remembered));
            } else {
                splitPane.setDividerLocation(0.5);
            }
            centre.add(splitPane, BorderLayout.CENTER);
        }
        centre.revalidate();
        centre.repaint();
    }

    /**
     * Rebuilds one editor-group pane.
     *
     * @param pane pane wrapper
     * @param strip tab strip
     * @param host selected panel host
     * @param tabList tabs owned by the pane
     * @param activeIndex selected panel index
     * @param primary true for primary pane
     */
    private void preparePane(
            JPanel pane,
            JPanel strip,
            JPanel host,
            List<Integer> tabList,
            int activeIndex,
            boolean primary) {
        pane.removeAll();
        host.removeAll();
        if (validPanel(activeIndex)) {
            host.add(panels.get(activeIndex), BorderLayout.CENTER);
        }
        rebuildStrip(strip, tabList, activeIndex, primary);
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(true);
        header.setBackground(WorkbenchTheme.BG);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, WorkbenchTheme.LINE));
        header.add(strip, BorderLayout.CENTER);
        if (primary) {
            JPanel splitHolder = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 3));
            splitHolder.setOpaque(false);
            splitHolder.add(splitButton);
            header.add(splitHolder, BorderLayout.EAST);
        }
        pane.add(header, BorderLayout.NORTH);
        pane.add(host, BorderLayout.CENTER);
    }

    /**
     * Ensures open tabs are assigned to exactly one editor group and selected
     * indices point at existing group members.
     */
    private void repairGroups() {
        primaryTabs.removeIf(index -> !open.contains(index) || !validPanel(index));
        secondaryTabs.removeIf(index -> !open.contains(index) || !validPanel(index));
        secondaryTabs.removeIf(primaryTabs::contains);
        for (int index : open) {
            if (!primaryTabs.contains(index) && !secondaryTabs.contains(index) && validPanel(index)) {
                primaryTabs.add(index);
            }
        }
        if (open.isEmpty()) {
            primaryIndex = 0;
            secondaryIndex = -1;
            activePane = 0;
            return;
        }
        if (secondaryTabs.isEmpty()) {
            secondaryIndex = -1;
            activePane = activePane == 1 ? 0 : activePane;
            for (int index : open) {
                if (!primaryTabs.contains(index)) {
                    primaryTabs.add(index);
                }
            }
        } else if (primaryTabs.isEmpty()) {
            int moved = secondaryTabs.remove(0);
            primaryTabs.add(moved);
            primaryIndex = moved;
            if (secondaryTabs.isEmpty()) {
                secondaryIndex = -1;
                activePane = 0;
            }
        }
        if (!primaryTabs.contains(primaryIndex)) {
            primaryIndex = primaryTabs.get(0);
        }
        if (secondaryIndex >= 0 && !secondaryTabs.contains(secondaryIndex)) {
            secondaryIndex = secondaryTabs.isEmpty() ? -1 : secondaryTabs.get(0);
        }
        if (activePane == 1 && secondaryIndex < 0) {
            activePane = 0;
        }
        syncOpenFromGroups();
    }

    /**
     * Moves a panel tab to an editor group, reopening it when needed.
     *
     * @param index panel index
     * @param pane target pane: 0 primary, 1 secondary
     */
    private void moveToPane(int index, int pane) {
        ensureOpen(index);
        List<Integer> target = pane == 1 ? secondaryTabs : primaryTabs;
        List<Integer> other = pane == 1 ? primaryTabs : secondaryTabs;
        other.remove(Integer.valueOf(index));
        if (!target.contains(index)) {
            target.add(index);
        }
        if (pane == 0) {
            primaryIndex = index;
        } else {
            secondaryIndex = index;
        }
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
        for (int index : secondaryTabs) {
            if (!primaryTabs.contains(index)) {
                primaryTabs.add(index);
            }
        }
        secondaryTabs.clear();
        secondaryIndex = -1;
        activePane = 0;
        splitPane = null;
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
    }

    /**
     * Returns whether split mode is active.
     *
     * @return true when a secondary editor group is visible
     */
    private boolean isSplitActive() {
        return secondaryIndex >= 0 && !secondaryTabs.isEmpty();
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
        if (splitPane == null || !isSplitActive()) {
            return;
        }
        int location = splitPane.getDividerLocation();
        if (location <= 0) {
            return;
        }
        if (splitPane.getOrientation() == JSplitPane.VERTICAL_SPLIT) {
            verticalDividerLocation = location;
        } else {
            horizontalDividerLocation = location;
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
            g.setColor(new Color(WorkbenchTheme.ACCENT.getRed(), WorkbenchTheme.ACCENT.getGreen(),
                    WorkbenchTheme.ACCENT.getBlue(), 36));
            g.fillRoundRect(zone.x, zone.y, zone.width, zone.height, 6, 6);
            g.setColor(WorkbenchTheme.ACCENT);
            g.setStroke(new BasicStroke(2f));
            g.drawRoundRect(zone.x + 1, zone.y + 1, zone.width - 2, zone.height - 2, 6, 6);
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
            case DROP_PRIMARY_CENTER -> inset(componentBounds(primaryPane), inset);
            case DROP_SECONDARY_CENTER -> inset(componentBounds(secondaryPane), inset);
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
}
