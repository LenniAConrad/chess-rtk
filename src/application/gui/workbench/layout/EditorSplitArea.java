package application.gui.workbench.layout;

import application.gui.workbench.ui.Theme;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.MenuSelectionManager;
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

    private static final long serialVersionUID = 1L;

    private static final int DROP_NONE = 0;

    private static final int DROP_LEFT = 1;

    private static final int DROP_RIGHT = 2;

    private static final int DROP_TOP = 3;

    private static final int DROP_BOTTOM = 4;

    private static final int DROP_PRIMARY_CENTER = 5;

    private static final int DROP_SECONDARY_CENTER = 6;

    private static final int DROP_TOP_LEFT = 7;

    private static final int DROP_TOP_RIGHT = 8;

    private static final int DROP_BOTTOM_LEFT = 9;

    private static final int DROP_BOTTOM_RIGHT = 10;

    private static final int DROP_TERTIARY_CENTER = 11;

    private static final int DROP_QUATERNARY_CENTER = 12;

    private static final int PANE_PRIMARY = 0;

    private static final int PANE_SECONDARY = 1;

    private static final int PANE_TERTIARY = 2;

    private static final int PANE_QUATERNARY = 3;

    private static final int DROP_FILL_ALPHA = 32;

    private static final int DROP_BORDER_ALPHA = 190;

    private final transient List<String> names = new ArrayList<>();
    private final transient List<String> baseNames = new ArrayList<>();
    private final transient List<JComponent> panels = new ArrayList<>();
    private final transient List<Supplier<JComponent>> panelFactories = new ArrayList<>();
    private final transient List<Theme.Mode> panelThemeModes = new ArrayList<>();

    private final transient List<Integer> open = new ArrayList<>();

    private final transient List<Integer> primaryTabs = new ArrayList<>();

    private final transient List<Integer> secondaryTabs = new ArrayList<>();

    private final transient List<Integer> tertiaryTabs = new ArrayList<>();

    private final transient List<Integer> quaternaryTabs = new ArrayList<>();

    /**
     * Selected tab index for the primary pane.
     */
    private int primaryIndex;

    /**
     * Selected tab index for each optional split pane.
     */
    private int secondaryIndex = -1, tertiaryIndex = -1, quaternaryIndex = -1;

    /**
     * Current drag drop zone.
     */
    private int dragZone;

    /**
     * Target pane and insertion index for a tab drag.
     */
    private int dragTargetPane = -1, dragTargetIndex = -1;

    /**
     * Current tab-drag x coordinate.
     */
    private int dragTargetX = -1;

    /**
     * Pane that currently owns keyboard and command focus.
     */
    private int activePane;

    /**
     * Remembered divider locations for split restoration.
     */
    private int horizontalDividerLocation = -1, verticalDividerLocation = -1;

    /**
     * Root split pane when more than one editor group is visible.
     */
    JSplitPane splitPane;

    /**
     * All live split panes.
     */
    private final transient List<JSplitPane> splitPanes = new ArrayList<>();

    /**
     * Optional selected-tab listener.
     */
    private transient IntConsumer selectionListener;

    /**
     * Editor pane containers for each split group.
     */
    private final JPanel primaryPane = new JPanel(new BorderLayout(0, 0)),
            secondaryPane = new JPanel(new BorderLayout(0, 0)),
            tertiaryPane = new JPanel(new BorderLayout(0, 0)),
            quaternaryPane = new JPanel(new BorderLayout(0, 0));

    /**
     * Editor content hosts for each split group.
     */
    private final JPanel primaryHost = new EmptyEditorHost(),
            secondaryHost = new EmptyEditorHost(),
            tertiaryHost = new EmptyEditorHost(),
            quaternaryHost = new EmptyEditorHost();

    /**
     * Tab strips for each split group.
     */
    private final JPanel primaryStrip = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)),
            secondaryStrip = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)),
            tertiaryStrip = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)),
            quaternaryStrip = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));

    /**
     * Header panels for each split group.
     */
    private final JPanel[] paneHeaders = { new JPanel(new BorderLayout()), new JPanel(new BorderLayout()),
        new JPanel(new BorderLayout()), new JPanel(new BorderLayout()) };

    /**
     * Containers for per-pane split-layout action buttons.
     */
    private final JPanel[] splitButtonHolders = {
        splitButtonHolder(), splitButtonHolder(), splitButtonHolder(), splitButtonHolder()
    };

    /**
     * Root center panel holding the current split layout.
     */
    private final JPanel centre = new JPanel(new BorderLayout());

    /**
     * Layered host that stacks {@link #centre} beneath {@link #dropOverlay} so
     * the drag drop-preview always composites above the editor content.
     */
    private final CenterStack layeredCentre = new CenterStack();

    /**
     * Top-layer overlay that paints the active drop-zone preview.
     */
    private final DropOverlay dropOverlay = new DropOverlay();

    /**
     * Buttons used to split each editor group.
     */
    private final JToggleButton[] splitButtons = {
        new SplitGroupButton(), new SplitGroupButton(), new SplitGroupButton(), new SplitGroupButton()
    };

    /**
     * Primary split button retained for existing reflective tests.
     */
    private final JToggleButton splitButton = splitButtons[PANE_PRIMARY];

    /**
     * Per-pane "more tabs" buttons, shown only when a tab strip overflows its
     * available width so every open tab stays reachable.
     */
    private final JToggleButton[] overflowButtons = new JToggleButton[PANE_QUATERNARY + 1];

    /**
     * Creates an empty split area.
     */
    public EditorSplitArea() {
        super(new BorderLayout());
        for (int pane = PANE_PRIMARY; pane <= PANE_QUATERNARY; pane++) {
            int paneId = pane;
            splitButtons[pane].addActionListener(event -> splitSelectedTabRightFromPane(paneId));
            JToggleButton overflow = new JToggleButton("»");
            overflow.setName("workbench.editor.tabs.overflow");
            overflow.setToolTipText("Show all open tabs in this editor group");
            overflow.getAccessibleContext().setAccessibleName("Show all open tabs");
            Theme.commandTab(overflow);
            overflow.setVisible(false);
            overflow.addActionListener(event -> {
                overflow.setSelected(false);
                showTabOverflowMenu(paneId, overflow);
            });
            overflowButtons[pane] = overflow;
            splitButtonHolders[pane].add(overflow);
            splitButtonHolders[pane].add(splitButtons[pane]);
            paneStrip(pane).addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override
                public void componentResized(java.awt.event.ComponentEvent event) {
                    updateOverflowButton(paneId);
                }
            });
            EditorPaneShell.install(panePanel(pane), paneHeader(pane), paneStrip(pane), paneHost(pane),
                    splitButtonHolders[pane]);
        }
        applyChromeTheme();
        layeredCentre.add(centre, JLayeredPane.DEFAULT_LAYER);
        layeredCentre.add(dropOverlay, JLayeredPane.DRAG_LAYER);
        add(layeredCentre, BorderLayout.CENTER);
    }

    private static JPanel splitButtonHolder() {
        JPanel holder = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 3));
        holder.setOpaque(false);
        return holder;
    }

    /**
     * Adds a workbench panel.
     *
     * @param name display name
     * @param panel panel component
     */
    public void addPanel(String name, JComponent panel) {
        addPanel(name, panel, null);
    }

    /**
     * Adds a workbench panel that can optionally create more tab instances.
     *
     * @param name display name
     * @param panel initial panel component
     * @param duplicateFactory factory for additional instances, or null
     */
    public void addPanel(String name, JComponent panel, Supplier<JComponent> duplicateFactory) {
        int index = names.size();
        names.add(name);
        baseNames.add(name);
        panels.add(panel);
        panelFactories.add(duplicateFactory);
        panelThemeModes.add(null);
        open.add(index);
        primaryTabs.add(index);
    }

    /**
     * Opens another tab instance for a factory-backed panel.
     *
     * @param index source panel index
     * @return new panel index, or the original index when duplication is not available
     */
    public int duplicate(int index) {
        if (!validPanel(index) || panelFactories.get(index) == null) {
            return index;
        }
        Supplier<JComponent> factory = panelFactories.get(index);
        JComponent panel = factory.get();
        if (panel == null) {
            return index;
        }
        int copy = names.size();
        String baseName = baseNames.get(index);
        names.add(nextDuplicateName(baseName));
        baseNames.add(baseName);
        panels.add(panel);
        panelFactories.add(factory);
        panelThemeModes.add(null);
        int targetPane = paneVisible(activePane) ? activePane : firstVisiblePane();
        open.add(copy);
        tabsForPane(targetPane).add(copy);
        setPaneIndex(targetPane, copy);
        activePane = targetPane;
        relayout();
        notifySelectionChanged();
        return copy;
    }

    /**
     * Builds the tab strips and shows the first panel. Call once after every
     * panel has been added.
     */
    public void install() {
        updateSplitButtonState();
        relayout();
    }

    /**
     * Refreshes the editor shell and every stored tab panel after a workbench
     * theme switch, including panels that are open but not currently attached
     * to a visible editor group.
     */
    public void refreshTheme() {
        applyChromeTheme();
        Theme.Mode currentMode = Theme.mode();
        for (int i = 0; i < panels.size(); i++) {
            JComponent panel = panels.get(i);
            if (panel != null) {
                Theme.refreshComponentTree(panel);
                panelThemeModes.set(i, currentMode);
            }
        }
        relayout();
        applyChromeTheme();
        revalidate();
        repaint();
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
        int pane = paneVisible(activePane) ? activePane : firstVisiblePane();
        int selected = paneIndex(pane);
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
        } else if (isSplitActive()) {
            int targetPane = paneVisible(activePane) ? activePane : firstVisiblePane();
            setPaneSelection(targetPane, index);
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
     * Returns the number of open workbench tabs.
     *
     * @return open tab count
     */
    public int openTabCount() {
        return open.size();
    }

    /**
     * Returns the number of visible editor groups.
     *
     * @return visible editor group count
     */
    public int visibleGroupCount() {
        int count = 0;
        for (int pane = PANE_PRIMARY; pane <= PANE_QUATERNARY; pane++) {
            if (paneVisible(pane)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Splits the selected tab into a group on the right.
     */
    public void splitSelectedTabRight() {
        splitSelectedTabCopy(DROP_RIGHT);
    }

    private void splitSelectedTabRightFromPane(int pane) {
        if (paneVisible(pane)) {
            activePane = pane;
        }
        splitSelectedTabCopy(DROP_RIGHT);
    }

    /**
     * Splits the selected tab into a group on the left.
     */
    public void splitSelectedTabLeft() {
        splitSelectedTabCopy(DROP_LEFT);
    }

    /**
     * Splits the selected tab into a group above.
     */
    public void splitSelectedTabUp() {
        splitSelectedTabCopy(DROP_TOP);
    }

    /**
     * Splits the selected tab into a group below.
     */
    public void splitSelectedTabDown() {
        splitSelectedTabCopy(DROP_BOTTOM);
    }

    /**
     * Closes the selected tab.
     */
    public void closeSelectedTab() {
        hideOpenMenus();
        int selected = selectedIndex();
        if (validPanel(selected)) {
            closeTab(selected);
        }
    }

    /**
     * Closes every tab except the selected tab.
     */
    public void closeOtherTabs() {
        hideOpenMenus();
        closeOtherTabs(selectedIndex());
    }

    /**
     * Reopens every workbench tab into the active editor group.
     */
    public void reopenAllTabs() {
        hideOpenMenus();
        reopenAllTabs(activePane);
    }

    private void setPrimary(int index) {
        setPaneSelection(PANE_PRIMARY, index);
    }

    private void setSecondary(int index) {
        setPaneSelection(PANE_SECONDARY, index);
    }

    private void setTertiary(int index) {
        setPaneSelection(PANE_TERTIARY, index);
    }

    private void setQuaternary(int index) {
        setPaneSelection(PANE_QUATERNARY, index);
    }

    private void setPaneSelection(int pane, int index) {
        if (!validPanel(index)) {
            return;
        }
        if (tabsForPane(pane).contains(index) && paneVisible(pane)
                && paneIndex(pane) == index && activePane == pane) {
            return;
        }
        if (tabsForPane(pane).contains(index) && paneVisible(pane)) {
            setPaneIndex(pane, index);
            activePane = pane;
            refreshVisiblePaneSelection(pane);
            notifySelectionChanged();
            return;
        }
        moveToPane(index, pane);
        setPaneIndex(pane, index);
        activePane = pane;
        relayout();
        notifySelectionChanged();
    }

    private void refreshVisiblePaneSelection(int selectedPane) {
        preparePane(selectedPane);
        for (int pane = PANE_PRIMARY; pane <= PANE_QUATERNARY; pane++) {
            if (pane != selectedPane && paneVisible(pane)) {
                rebuildStrip(paneStrip(pane), tabsForPane(pane), paneIndex(pane), pane);
            }
        }
        panePanel(selectedPane).revalidate();
        panePanel(selectedPane).repaint();
    }

    private void closeTab(int index) {
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

    private int firstOpen() {
        return open.isEmpty() ? 0 : open.get(0);
    }

    private void rebuildStrip(JPanel strip, List<Integer> tabList, int activeIndex, int pane) {
        boolean paneActive = activePane == pane || (!isSplitActive() && pane == PANE_PRIMARY);
        if (EditorTabStripState.refreshInPlace(strip, tabList, activeIndex, paneActive,
                isSplitActive(), open.size(), panels.size(), factoryBackedPanelCount(), needsReopenButton())) {
            return;
        }
        strip.removeAll();
        for (int index : tabList) {
            int panelIndex = index;
            EditorTab tab = new EditorTab(names.get(index),
                    () -> setPaneSelection(pane, panelIndex),
                    () -> closeTab(panelIndex));
            EditorTabStripState.markTab(tab, panelIndex);
            tab.setSelected(index == activeIndex);
            tab.setDragHandler(
                    point -> handleTabDrag(strip, tabList, panelIndex, tab, point),
                    () -> finishTabDrag(panelIndex));
            tab.setComponentPopupMenu(tabContextMenu(pane, panelIndex));
            strip.add(tab);
            tab.setPaneActive(paneActive);
        }
        if (needsReopenButton()) {
            strip.add(reopenButton(pane));
        }
        EditorTabStripState.remember(strip, isSplitActive(), open.size(), panels.size(), factoryBackedPanelCount());
        strip.revalidate();
        strip.repaint();
        SwingUtilities.invokeLater(() -> updateOverflowButton(pane));
    }

    /**
     * Shows or hides a pane's overflow button based on whether its tab strip
     * has more tabs than fit in the available width.
     *
     * @param pane editor-group index
     */
    private void updateOverflowButton(int pane) {
        JToggleButton button = overflowButtons[pane];
        if (button == null) {
            return;
        }
        JPanel strip = paneStrip(pane);
        boolean overflowing = paneVisible(pane) && strip.getWidth() > 0
                && strip.getPreferredSize().width > strip.getWidth() + 2;
        if (button.isVisible() != overflowing) {
            button.setVisible(overflowing);
            JComponent holder = splitButtonHolders[pane];
            holder.revalidate();
            holder.repaint();
        }
    }

    /**
     * Pops up a menu listing every open tab in a pane so overflowed tabs stay
     * reachable; selecting an entry activates that tab.
     *
     * @param pane editor-group index
     * @param anchor button the menu drops from
     */
    private void showTabOverflowMenu(int pane, JComponent anchor) {
        JPopupMenu menu = new JPopupMenu();
        PopupMenus.style(menu);
        int active = paneIndex(pane);
        for (int index : tabsForPane(pane)) {
            if (!validPanel(index)) {
                continue;
            }
            int panelIndex = index;
            String name = index == active ? "● " + names.get(index) : names.get(index);
            menu.add(PopupMenus.item(name, () -> setPaneSelection(pane, panelIndex)));
        }
        menu.show(anchor, 0, anchor.getHeight());
    }

    private boolean needsReopenButton() {
        return open.size() < panels.size() || panelFactories.stream().anyMatch(Objects::nonNull);
    }

    private int factoryBackedPanelCount() {
        return (int) panelFactories.stream().filter(Objects::nonNull).count();
    }

    private JComponent reopenButton(int pane) {
        JToggleButton plus = new JToggleButton("+");
        EditorTabStripState.markReopenButton(plus);
        Theme.commandTab(plus);
        plus.setToolTipText("New duplicate tab or restore a closed tab");
        plus.setActionCommand("workbench.editor.tabs.newOrRestore");
        plus.setName("workbench.editor.tabs.newOrRestore");
        plus.getAccessibleContext().setAccessibleName("New or restore tab");
        plus.getAccessibleContext().setAccessibleDescription(
                "Opens another duplicate-capable tab or restores a closed workbench tab.");
        plus.addActionListener(event -> {
            plus.setSelected(false);
            JPopupMenu menu = new JPopupMenu();
            PopupMenus.style(menu);
            List<String> offered = new ArrayList<>();
            for (int i = 0; i < panels.size(); i++) {
                if (panelFactories.get(i) == null || offered.contains(baseNames.get(i))) {
                    continue;
                }
                int index = i;
                offered.add(baseNames.get(i));
                menu.add(PopupMenus.item("New " + baseNames.get(i), () -> {
                    activePane = pane;
                    duplicate(index);
                }));
            }
            for (int i = 0; i < panels.size(); i++) {
                if (open.contains(i)) {
                    continue;
                }
                int index = i;
                menu.add(PopupMenus.item(names.get(i), () -> setPaneSelection(pane, index)));
            }
            menu.show(plus, 0, plus.getHeight());
        });
        return plus;
    }

    private JPopupMenu tabContextMenu(int pane, int panelIndex) {
        JPopupMenu menu = new JPopupMenu();
        PopupMenus.style(menu);
        boolean canSplit = canSplitTab(panelIndex);
        menu.add(PopupMenus.item("Split Tab Right", () -> splitTabCopy(panelIndex, DROP_RIGHT), canSplit));
        menu.add(PopupMenus.item("Split Tab Down", () -> splitTabCopy(panelIndex, DROP_BOTTOM), canSplit));
        menu.add(PopupMenus.item("Split Tab Left", () -> splitTabCopy(panelIndex, DROP_LEFT), canSplit));
        menu.add(PopupMenus.item("Split Tab Up", () -> splitTabCopy(panelIndex, DROP_TOP), canSplit));
        if (validPanel(panelIndex) && panelFactories.get(panelIndex) != null) {
            menu.add(PopupMenus.item("Duplicate Tab", () -> duplicate(panelIndex)));
        }
        menu.add(PopupMenus.item("Close", () -> closeTab(panelIndex)));
        menu.add(PopupMenus.item("Close Other Tabs", () -> closeOtherTabs(panelIndex), open.size() > 1));
        if (open.size() < panels.size()) {
            menu.add(PopupMenus.item("Restore Closed Tabs", () -> reopenAllTabs(pane)));
        }
        if (isSplitActive()) {
            menu.add(PopupMenus.item("Collapse Editor Groups", this::collapseAndRelayout));
        }
        return menu;
    }

    private String nextDuplicateName(String baseName) {
        if (!openDuplicateNameTaken(baseName, baseName)) {
            return baseName;
        }
        int ordinal = 2;
        String candidate = baseName + " " + ordinal;
        while (openDuplicateNameTaken(baseName, candidate)) {
            ordinal++;
            candidate = baseName + " " + ordinal;
        }
        return candidate;
    }

    private boolean openDuplicateNameTaken(String baseName, String candidate) {
        for (int index : open) {
            if (validPanel(index)
                    && baseName.equals(baseNames.get(index))
                    && candidate.equals(names.get(index))) {
                return true;
            }
        }
        return false;
    }

    private void handleTabDrag(
            JPanel strip,
            List<Integer> tabList,
            int draggedPanelIndex,
            EditorTab tab,
            Point tabPoint) {
        Point inArea = SwingUtilities.convertPoint(tab, tabPoint, this);
        int targetPane = paneForStripPoint(inArea);
        if (targetPane >= PANE_PRIMARY) {
            int stripX = SwingUtilities.convertPoint(tab, tabPoint, strip).x;
            if (targetPane == paneContaining(draggedPanelIndex)) {
                boolean clearedTarget = clearDragTarget();
                if (dragZone != DROP_NONE || clearedTarget) {
                    dragZone = DROP_NONE;
                    dropOverlay.repaint();
                }
                reorderWithinStrip(strip, tabList, draggedPanelIndex, stripX);
            } else {
                JPanel targetStrip = paneStrip(targetPane);
                int targetX = SwingUtilities.convertPoint(tab, tabPoint, targetStrip).x;
                armTabStripDrop(targetPane, targetX);
            }
            return;
        }

        boolean clearedTarget = clearDragTarget();
        Rectangle body = centre.getBounds();
        int zone = dropZoneFor(inArea, body);
        if (zone != dragZone || clearedTarget) {
            dragZone = zone;
            dropOverlay.repaint();
        }
    }

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

        int shortest = Math.min(body.width, body.height);
        int edge = Math.max(24, Math.min(120, shortest / 5));
        edge = Math.min(edge, Math.max(1, shortest / 2 - 1));
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

    private int paneForStripPoint(Point point) {
        for (int pane = PANE_PRIMARY; pane <= PANE_QUATERNARY; pane++) {
            if (!paneVisible(pane)) {
                continue;
            }
            Rectangle bounds = componentBounds(paneStrip(pane));
            bounds.grow(8, 8);
            if (bounds.contains(point)) {
                return pane;
            }
        }
        return -1;
    }

    private void armTabStripDrop(int pane, int stripX) {
        JPanel strip = paneStrip(pane);
        int insertionIndex = insertionIndexForStrip(strip, stripX);
        int insertionX = insertionXForStrip(strip, insertionIndex);
        int zone = centerDropZone(pane);
        if (dragTargetPane != pane
                || dragTargetIndex != insertionIndex
                || dragTargetX != insertionX
                || dragZone != zone) {
            dragTargetPane = pane;
            dragTargetIndex = insertionIndex;
            dragTargetX = insertionX;
            dragZone = zone;
            dropOverlay.repaint();
        }
    }

    private static int insertionIndexForStrip(JPanel strip, int stripX) {
        int target = 0;
        for (Component child : strip.getComponents()) {
            if (child instanceof EditorTab && child.getX() + child.getWidth() / 2 < stripX) {
                target++;
            }
        }
        return Math.max(0, target);
    }

    private static int insertionXForStrip(JPanel strip, int insertionIndex) {
        int tab = 0;
        int lastRight = 4;
        for (Component child : strip.getComponents()) {
            if (!(child instanceof EditorTab)) {
                continue;
            }
            if (tab == insertionIndex) {
                return child.getX();
            }
            lastRight = child.getX() + child.getWidth();
            tab++;
        }
        return lastRight;
    }

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

    private void finishTabDrag(int draggedPanelIndex) {
        int zone = dragZone;
        int targetPane = dragTargetPane;
        int targetIndex = dragTargetIndex;
        dragZone = DROP_NONE;
        clearDragTarget();
        dropOverlay.repaint();
        if (targetPane >= PANE_PRIMARY) {
            dockDraggedTab(draggedPanelIndex, targetPane, targetIndex);
            return;
        }
        splitTab(draggedPanelIndex, zone);
    }

    private void dockDraggedTab(int panelIndex, int pane, int targetIndex) {
        if (!validPanel(panelIndex)) {
            return;
        }
        rememberDividerLocation();
        int targetPane = pane >= PANE_PRIMARY && pane <= PANE_QUATERNARY ? pane : PANE_PRIMARY;
        ensureOpen(panelIndex);
        for (int candidate = PANE_PRIMARY; candidate <= PANE_QUATERNARY; candidate++) {
            tabsForPane(candidate).remove(Integer.valueOf(panelIndex));
        }
        List<Integer> target = tabsForPane(targetPane);
        int insertion = Math.max(0, Math.min(targetIndex, target.size()));
        target.add(insertion, panelIndex);
        setPaneIndex(targetPane, panelIndex);
        activePane = targetPane;
        repairGroups();
        relayout();
        notifySelectionChanged();
    }

    private boolean clearDragTarget() {
        boolean hadTarget = dragTargetPane >= PANE_PRIMARY || dragTargetX >= 0;
        dragTargetPane = -1;
        dragTargetIndex = -1;
        dragTargetX = -1;
        return hadTarget;
    }

    private void splitTab(int panelIndex, int zone) {
        switch (zone) {
            case DROP_PRIMARY_CENTER -> setPrimary(panelIndex);
            case DROP_SECONDARY_CENTER -> setSecondary(panelIndex);
            case DROP_TERTIARY_CENTER -> setTertiary(panelIndex);
            case DROP_QUATERNARY_CENTER -> setQuaternary(panelIndex);
            case DROP_RIGHT, DROP_BOTTOM, DROP_LEFT, DROP_TOP -> splitWithDragged(panelIndex, zone);
            case DROP_TOP_LEFT -> splitWithDraggedInPane(panelIndex, PANE_PRIMARY);
            case DROP_TOP_RIGHT -> splitWithDraggedInPane(panelIndex, PANE_SECONDARY);
            case DROP_BOTTOM_LEFT -> splitWithDraggedInPane(panelIndex, PANE_TERTIARY);
            case DROP_BOTTOM_RIGHT -> splitWithDraggedInPane(panelIndex, PANE_QUATERNARY);
            default -> relayout();
        }
    }

    private void splitTabCopy(int panelIndex, int zone) {
        int sourcePane = paneContaining(panelIndex);
        int target = duplicate(panelIndex);
        splitTab(target, splitCommandZone(sourcePane < 0 ? activePane : sourcePane, zone));
    }

    private int splitCommandZone(int sourcePane, int zone) {
        if (!isSplitActive()) {
            return zone;
        }
        return switch (zone) {
            case DROP_RIGHT -> sourcePane == PANE_PRIMARY ? DROP_SECONDARY_CENTER
                    : sourcePane == PANE_TERTIARY ? DROP_QUATERNARY_CENTER : zone;
            case DROP_LEFT -> sourcePane == PANE_SECONDARY ? DROP_PRIMARY_CENTER
                    : sourcePane == PANE_QUATERNARY ? DROP_TERTIARY_CENTER : zone;
            case DROP_BOTTOM -> sourcePane == PANE_PRIMARY ? DROP_TERTIARY_CENTER
                    : sourcePane == PANE_SECONDARY ? DROP_QUATERNARY_CENTER : zone;
            case DROP_TOP -> sourcePane == PANE_TERTIARY ? DROP_PRIMARY_CENTER
                    : sourcePane == PANE_QUATERNARY ? DROP_SECONDARY_CENTER : zone;
            default -> zone;
        };
    }

    private void splitSelectedTabCopy(int zone) {
        hideOpenMenus();
        int selected = selectedIndex();
        if (validPanel(selected)) {
            splitTabCopy(selected, zone);
        }
    }

    private static void hideOpenMenus() {
        MenuSelectionManager.defaultManager().clearSelectedPath();
    }

    /**
     * Splits by dragging a tab toward the primary or secondary side.
     *
     * @param draggedPanelIndex dragged tab index
     * @param draggedInPrimary true to split left, false to split right
     */
    void splitWithDragged(int draggedPanelIndex, boolean draggedInPrimary) {
        splitWithDragged(draggedPanelIndex, draggedInPrimary ? DROP_LEFT : DROP_RIGHT);
    }

    private void splitWithDragged(int draggedPanelIndex, int zone) {
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
        secondaryIndex = -1;
        tertiaryIndex = -1;
        quaternaryIndex = -1;
        switch (zone) {
            case DROP_LEFT -> {
                primaryTabs.add(draggedPanelIndex);
                secondaryTabs.addAll(others);
                primaryIndex = draggedPanelIndex;
                secondaryIndex = secondaryTabs.get(0);
                activePane = PANE_PRIMARY;
            }
            case DROP_TOP -> {
                primaryTabs.add(draggedPanelIndex);
                tertiaryTabs.addAll(others);
                primaryIndex = draggedPanelIndex;
                tertiaryIndex = tertiaryTabs.get(0);
                activePane = PANE_PRIMARY;
            }
            case DROP_BOTTOM -> {
                primaryTabs.addAll(others);
                tertiaryTabs.add(draggedPanelIndex);
                primaryIndex = selectedOrFirst(primaryTabs, primaryIndex);
                tertiaryIndex = draggedPanelIndex;
                activePane = PANE_TERTIARY;
            }
            case DROP_RIGHT -> {
                primaryTabs.addAll(others);
                secondaryTabs.add(draggedPanelIndex);
                primaryIndex = selectedOrFirst(primaryTabs, primaryIndex);
                secondaryIndex = draggedPanelIndex;
                activePane = PANE_SECONDARY;
            }
            default -> {
                primaryTabs.addAll(others);
                secondaryTabs.add(draggedPanelIndex);
                primaryIndex = selectedOrFirst(primaryTabs, primaryIndex);
                secondaryIndex = draggedPanelIndex;
                activePane = PANE_SECONDARY;
            }
        }
        syncOpenFromGroups();
        relayout();
        notifySelectionChanged();
    }

    private static int selectedOrFirst(List<Integer> tabs, int selected) {
        return tabs.contains(selected) ? selected : tabs.get(0);
    }

    private void splitWithDraggedInPane(int draggedPanelIndex, int targetPane) {
        if (!validPanel(draggedPanelIndex)) {
            return;
        }
        int targetPaneId = targetPane >= PANE_PRIMARY && targetPane <= PANE_QUATERNARY
                ? targetPane
                : PANE_PRIMARY;
        if (!isSplitActive()) {
            splitWithDragged(draggedPanelIndex, initialSplitZone(targetPaneId));
            return;
        }
        ensureOpen(draggedPanelIndex);
        rememberDividerLocation();
        List<Integer> displaced = new ArrayList<>(tabsForPane(targetPaneId));
        for (int pane = PANE_PRIMARY; pane <= PANE_QUATERNARY; pane++) {
            tabsForPane(pane).remove(Integer.valueOf(draggedPanelIndex));
        }
        displaced.remove(Integer.valueOf(draggedPanelIndex));
        List<Integer> target = tabsForPane(targetPaneId);
        target.clear();
        target.add(draggedPanelIndex);
        setPaneIndex(targetPaneId, draggedPanelIndex);
        int paired = pairedPane(targetPaneId);
        for (int index : displaced) {
            if (!tabsForPane(paired).contains(index)) {
                tabsForPane(paired).add(index);
            }
        }
        if (!displaced.isEmpty() && paneIndex(paired) < 0) {
            setPaneIndex(paired, displaced.get(0));
        }
        activePane = targetPaneId;
        repairGroups();
        syncOpenFromGroups();
        relayout();
        notifySelectionChanged();
    }

    private static int initialSplitZone(int targetPane) {
        return switch (targetPane) {
            case PANE_PRIMARY -> DROP_TOP;
            case PANE_SECONDARY -> DROP_RIGHT;
            case PANE_TERTIARY -> DROP_BOTTOM;
            case PANE_QUATERNARY -> DROP_RIGHT;
            default -> DROP_RIGHT;
        };
    }

    private void relayout() {
        rememberDividerLocation();
        repairGroups();
        updateSplitButtonState();
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

    private void updateSplitButtonState() {
        for (int pane = PANE_PRIMARY; pane <= PANE_QUATERNARY; pane++) {
            updateSplitButtonState(pane);
        }
    }

    private void updateSplitButtonState(int pane) {
        JToggleButton button = splitButtons[pane];
        boolean enabled = paneVisible(pane) && canSplitTab(paneIndex(pane));
        button.setEnabled(enabled);
        button.setSelected(enabled && isSplitActive() && activePane == pane);
        button.setToolTipText(enabled
                ? "Split active tab to the right"
                : "Open another tab or select a duplicate-capable tab to split right");
    }

    private boolean canSplitTab(int index) {
        return validPanel(index) && (open.size() >= 2 || panelFactories.get(index) != null);
    }

    private void preparePane(int pane) {
        JPanel panePanel = panePanel(pane);
        JPanel strip = paneStrip(pane);
        JPanel host = paneHost(pane);
        List<Integer> tabList = tabsForPane(pane);
        int activeIndex = paneIndex(pane);
        EditorPaneShell.install(panePanel, paneHeader(pane), strip, host,
                splitButtonHolders[pane]);
        JComponent panel = null;
        if (validPanel(activeIndex) && tabList.contains(activeIndex)) {
            panel = panels.get(activeIndex);
            refreshPanelThemeIfNeeded(activeIndex, panel);
        }
        EditorPaneShell.updateHost(host, panel);
        rebuildStrip(strip, tabList, activeIndex, pane);
    }

    private void refreshPanelThemeIfNeeded(int index, JComponent panel) {
        Theme.Mode currentMode = Theme.mode();
        if (panelThemeModes.get(index) == currentMode) {
            return;
        }
        Theme.refreshComponentTree(panel);
        panelThemeModes.set(index, currentMode);
    }

    private void applyChromeTheme() {
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
        for (JPanel header : paneHeaders) {
            EditorPaneShell.styleHeader(header);
        }
        for (JPanel holder : splitButtonHolders) {
            holder.setOpaque(false);
        }
    }

    private Component editorLayout() {
        Component left = columnLayout(PANE_PRIMARY, PANE_TERTIARY);
        Component right = columnLayout(PANE_SECONDARY, PANE_QUATERNARY);
        if (left != null && right != null) {
            return EditorSplitLayout.createSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right,
                    verticalDividerLocation, horizontalDividerLocation, splitPanes);
        }
        if (left != null) {
            return left;
        }
        return right == null ? primaryPane : right;
    }

    private Component columnLayout(int topPane, int bottomPane) {
        boolean topVisible = paneVisible(topPane);
        boolean bottomVisible = paneVisible(bottomPane);
        if (topVisible && bottomVisible) {
            return EditorSplitLayout.createSplitPane(JSplitPane.VERTICAL_SPLIT, panePanel(topPane),
                    panePanel(bottomPane), verticalDividerLocation, horizontalDividerLocation, splitPanes);
        }
        if (topVisible) {
            return panePanel(topPane);
        }
        return bottomVisible ? panePanel(bottomPane) : null;
    }

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
            primaryIndex = -1;
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
        if (primaryTabs.isEmpty() && !open.isEmpty()) {
            primaryTabs.add(firstOpen());
            primaryIndex = firstOpen();
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

    private void ensureOpen(int index) {
        if (!open.contains(index)) {
            open.add(index);
        }
    }

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

    private void collapseAndRelayout() {
        collapseSplit();
        relayout();
        notifySelectionChanged();
    }

    private void closeOtherTabs(int panelIndex) {
        if (!validPanel(panelIndex)) {
            return;
        }
        rememberDividerLocation();
        open.clear();
        primaryTabs.clear();
        secondaryTabs.clear();
        tertiaryTabs.clear();
        quaternaryTabs.clear();
        open.add(panelIndex);
        primaryTabs.add(panelIndex);
        primaryIndex = panelIndex;
        secondaryIndex = -1;
        tertiaryIndex = -1;
        quaternaryIndex = -1;
        activePane = PANE_PRIMARY;
        splitPane = null;
        splitPanes.clear();
        relayout();
        notifySelectionChanged();
    }

    private void reopenAllTabs(int pane) {
        int targetPane = paneVisible(pane) ? pane : PANE_PRIMARY;
        List<Integer> targetTabs = tabsForPane(targetPane);
        for (int index = 0; index < panels.size(); index++) {
            ensureOpen(index);
            if (paneContaining(index) < 0) {
                targetTabs.add(index);
            }
        }
        if (!targetTabs.isEmpty() && !targetTabs.contains(paneIndex(targetPane))) {
            setPaneIndex(targetPane, targetTabs.get(0));
        }
        repairGroups();
        relayout();
        notifySelectionChanged();
    }

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

    private boolean isSplitActive() {
        return paneVisible(PANE_SECONDARY) || paneVisible(PANE_TERTIARY) || paneVisible(PANE_QUATERNARY);
    }

    private boolean validPanel(int index) {
        return index >= 0 && index < panels.size();
    }

    private List<Integer> tabsForPane(int pane) {
        return switch (pane) {
            case PANE_SECONDARY -> secondaryTabs;
            case PANE_TERTIARY -> tertiaryTabs;
            case PANE_QUATERNARY -> quaternaryTabs;
            default -> primaryTabs;
        };
    }

    private int paneIndex(int pane) {
        return switch (pane) {
            case PANE_SECONDARY -> secondaryIndex;
            case PANE_TERTIARY -> tertiaryIndex;
            case PANE_QUATERNARY -> quaternaryIndex;
            default -> primaryIndex;
        };
    }

    private void setPaneIndex(int pane, int index) {
        switch (pane) {
            case PANE_SECONDARY -> secondaryIndex = index;
            case PANE_TERTIARY -> tertiaryIndex = index;
            case PANE_QUATERNARY -> quaternaryIndex = index;
            default -> primaryIndex = index;
        }
    }

    private JPanel panePanel(int pane) {
        return switch (pane) {
            case PANE_SECONDARY -> secondaryPane;
            case PANE_TERTIARY -> tertiaryPane;
            case PANE_QUATERNARY -> quaternaryPane;
            default -> primaryPane;
        };
    }

    private JPanel paneStrip(int pane) {
        return switch (pane) {
            case PANE_SECONDARY -> secondaryStrip;
            case PANE_TERTIARY -> tertiaryStrip;
            case PANE_QUATERNARY -> quaternaryStrip;
            default -> primaryStrip;
        };
    }

    private JPanel paneHeader(int pane) {
        return paneHeaders[pane >= PANE_PRIMARY && pane <= PANE_QUATERNARY ? pane : PANE_PRIMARY];
    }

    private JPanel paneHost(int pane) {
        return switch (pane) {
            case PANE_SECONDARY -> secondaryHost;
            case PANE_TERTIARY -> tertiaryHost;
            case PANE_QUATERNARY -> quaternaryHost;
            default -> primaryHost;
        };
    }

    private boolean paneVisible(int pane) {
        return paneIndex(pane) >= 0 && !tabsForPane(pane).isEmpty();
    }

    private int paneContaining(int index) {
        for (int pane = PANE_PRIMARY; pane <= PANE_QUATERNARY; pane++) {
            if (tabsForPane(pane).contains(index)) {
                return pane;
            }
        }
        return -1;
    }

    private int firstVisiblePane() {
        for (int pane = PANE_PRIMARY; pane <= PANE_QUATERNARY; pane++) {
            if (paneVisible(pane)) {
                return pane;
            }
        }
        return PANE_PRIMARY;
    }

    private static int pairedPane(int pane) {
        return switch (pane) {
            case PANE_PRIMARY -> PANE_TERTIARY;
            case PANE_SECONDARY -> PANE_QUATERNARY;
            case PANE_TERTIARY -> PANE_PRIMARY;
            case PANE_QUATERNARY -> PANE_SECONDARY;
            default -> PANE_SECONDARY;
        };
    }

    private static int centerDropZone(int pane) {
        return switch (pane) {
            case PANE_SECONDARY -> DROP_SECONDARY_CENTER;
            case PANE_TERTIARY -> DROP_TERTIARY_CENTER;
            case PANE_QUATERNARY -> DROP_QUATERNARY_CENTER;
            default -> DROP_PRIMARY_CENTER;
        };
    }

    private Rectangle componentBounds(JComponent component) {
        return SwingUtilities.convertRectangle(component,
                new Rectangle(0, 0, component.getWidth(), component.getHeight()), this);
    }

    private void notifySelectionChanged() {
        if (selectionListener != null) {
            selectionListener.accept(selectedIndex());
        }
    }

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
     * Paints the active drop-zone hint while a tab is being dragged into the
     * editor body. Rendered by {@link DropOverlay} on the top layer of
     * {@link #layeredCentre}, so it stays above tabs, split panes, and hosted
     * panels even when those repaint independently mid-drag. The overlay shares
     * this component's coordinate space because {@link #layeredCentre} fills the
     * split area and the overlay fills the layered host.
     *
     * @param g overlay graphics
     */
    private void paintDropPreview(Graphics2D g) {
        if (dragZone == DROP_NONE) {
            return;
        }
        Rectangle body = centre.getBounds();
        Rectangle zone = dropPreviewBounds(body, dragZone);
        if (zone.isEmpty()) {
            return;
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(Theme.ACCENT.getRed(), Theme.ACCENT.getGreen(),
                Theme.ACCENT.getBlue(), DROP_FILL_ALPHA));
        g.fillRect(zone.x, zone.y, zone.width, zone.height);
        g.setColor(Theme.withAlpha(Theme.ACCENT, DROP_BORDER_ALPHA));
        g.setStroke(new BasicStroke(1f));
        g.drawRect(zone.x, zone.y, Math.max(0, zone.width - 1), Math.max(0, zone.height - 1));
        paintTabInsertionMarker(g);
    }

    private void paintTabInsertionMarker(Graphics2D g) {
        if (dragTargetPane < PANE_PRIMARY || dragTargetX < 0 || !paneVisible(dragTargetPane)) {
            return;
        }
        JPanel strip = paneStrip(dragTargetPane);
        Rectangle bounds = componentBounds(strip);
        int x = bounds.x + dragTargetX;
        int top = bounds.y + 4;
        int height = Math.max(12, bounds.height - 8);
        g.setColor(Theme.ACCENT);
        g.fillRoundRect(x - 1, top, 3, height, 3, 3);
    }

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

    private static Rectangle inset(Rectangle rectangle, int amount) {
        return new Rectangle(rectangle.x + amount, rectangle.y + amount,
                Math.max(1, rectangle.width - amount * 2),
                Math.max(1, rectangle.height - amount * 2));
    }
    /**
     * {@inheritDoc}
     */
    @Override
    public Dimension getPreferredSize() {
        return new Dimension(900, 620);
    }

    /**
     * Center container that stacks the editor layout beneath the drop-preview
     * overlay, sizing both children to fill. As a {@link JLayeredPane} it
     * reports {@code isOptimizedDrawingEnabled() == false}, so any repaint that
     * originates inside the editor layout re-composites the overlay on top —
     * keeping the drop preview above tabs, split panes, and hosted panels.
     */
    private static final class CenterStack extends JLayeredPane {

        private static final long serialVersionUID = 1L;

        /**
         * {@inheritDoc}
         */
        @Override
        public void doLayout() {
            int w = getWidth();
            int h = getHeight();
            for (Component child : getComponents()) {
                child.setBounds(0, 0, w, h);
            }
        }
    }

    /**
     * Transparent overlay that paints the active drop-zone preview on the top
     * layer of {@link CenterStack}. It never intercepts pointer input, so the
     * editor content and the in-flight tab drag below keep receiving events.
     */
    private final class DropOverlay extends JComponent {

        private static final long serialVersionUID = 1L;

        DropOverlay() {
            setOpaque(false);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean contains(int x, int y) {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                paintDropPreview(g);
            } finally {
                g.dispose();
            }
        }
    }
}
