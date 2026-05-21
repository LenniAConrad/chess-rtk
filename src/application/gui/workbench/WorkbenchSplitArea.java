package application.gui.workbench;

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
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;

/**
 * VS Code-style workbench shell. Holds every workbench panel and shows them
 * through closable editor tabs: any tab can be closed off the strip and
 * reopened from the {@code +} menu, the view can split into two panes side by
 * side with a draggable, collapsible divider, and each pane keeps its own tab
 * strip.
 */
final class WorkbenchSplitArea extends JPanel {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Panel display names.
     */
    private final transient List<String> names = new ArrayList<>();

    /**
     * Panel components, parallel to {@link #names}.
     */
    private final transient List<JComponent> panels = new ArrayList<>();

    /**
     * Panel indices currently open as tabs, in strip order.
     */
    private final transient List<Integer> open = new ArrayList<>();

    /**
     * Index shown in the primary (left) pane.
     */
    private int primaryIndex;

    /**
     * Index shown in the secondary (right) pane, or -1 when not split.
     */
    private int secondaryIndex = -1;

    /**
     * Armed split drop zone during a tab drag.
     */
    private int dragZone;

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
     * Active pane for keyboard cycling: 0 primary, 1 secondary.
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
     * Optional primary-selection listener used by the window to start/stop
     * work that should only run while a pane is visible.
     */
    private transient IntConsumer selectionListener;

    /**
     * Host for the primary pane's panel.
     */
    private final JPanel primaryHost = new JPanel(new BorderLayout());

    /**
     * Host for the secondary pane's panel.
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
     * Centre area holding either the primary host or the split pane.
     */
    private final JPanel centre = new JPanel(new BorderLayout());

    /**
     * Split toggle button.
     */
    private final JToggleButton splitButton = new JToggleButton("Split");

    /**
     * Creates an empty split area.
     */
    WorkbenchSplitArea() {
        super(new BorderLayout(0, 6));
        setOpaque(false);
        primaryHost.setOpaque(false);
        secondaryHost.setOpaque(false);
        centre.setOpaque(false);
        primaryStrip.setOpaque(false);
        secondaryStrip.setOpaque(false);
        add(centre, BorderLayout.CENTER);
    }

    /**
     * Adds a workbench panel.
     *
     * @param name display name
     * @param panel panel component
     */
    void addPanel(String name, JComponent panel) {
        names.add(name);
        panels.add(panel);
        open.add(names.size() - 1);
    }

    /**
     * Builds the tab strips and shows the first panel. Call once after every
     * panel has been added.
     */
    void install() {
        WorkbenchTheme.commandTab(splitButton);
        splitButton.setToolTipText("Show two panels side by side");
        splitButton.addActionListener(event -> toggleSplit());

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(primaryStrip, BorderLayout.CENTER);
        JPanel splitHolder = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 3));
        splitHolder.setOpaque(false);
        splitHolder.add(splitButton);
        header.add(splitHolder, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        relayout();
    }

    /**
     * Returns the panel count.
     *
     * @return number of panels
     */
    int count() {
        return panels.size();
    }

    /**
     * Returns the primary pane's panel index.
     *
     * @return primary index
     */
    int selectedIndex() {
        return primaryIndex;
    }

    /**
     * Returns whether a panel is visible in either editor pane.
     *
     * @param index panel index
     * @return true when visible
     */
    boolean isVisibleInPane(int index) {
        return primaryIndex == index || secondaryIndex == index;
    }

    /**
     * Installs a listener that runs after primary selection changes.
     *
     * @param listener listener, or null
     */
    void setSelectionListener(IntConsumer listener) {
        selectionListener = listener;
    }

    /**
     * Selects a panel in the primary pane, reopening it when it was closed.
     *
     * @param index panel index
     */
    void select(int index) {
        if (index < 0 || index >= panels.size()) {
            return;
        }
        if (!open.contains(index)) {
            open.add(index);
        }
        activePane = 0;
        setPrimary(index);
    }

    /**
     * Selects the next open tab in the currently-active pane.
     */
    void selectNextTab() {
        cycleActivePane(1);
    }

    /**
     * Selects the previous open tab in the currently-active pane.
     */
    void selectPreviousTab() {
        cycleActivePane(-1);
    }

    /**
     * Shows a panel in the primary pane.
     *
     * @param index panel index
     */
    private void setPrimary(int index) {
        activePane = 0;
        primaryIndex = index;
        if (secondaryIndex == index) {
            secondaryIndex = firstOther(index);
        }
        relayout();
        notifySelectionChanged();
    }

    /**
     * Shows a panel in the secondary pane.
     *
     * @param index panel index
     */
    private void setSecondary(int index) {
        activePane = 1;
        secondaryIndex = index;
        if (primaryIndex == index) {
            primaryIndex = firstOther(index);
        }
        relayout();
        notifySelectionChanged();
    }

    /**
     * Closes a tab, hiding its panel from the strip.
     *
     * @param index panel index
     */
    private void closeTab(int index) {
        if (open.size() <= 1) {
            return;
        }
        open.remove(Integer.valueOf(index));
        if (primaryIndex == index) {
            primaryIndex = open.get(0);
        }
        if (secondaryIndex == index) {
            secondaryIndex = open.size() >= 2 ? firstOther(primaryIndex) : -1;
            if (activePane == 1) {
                activePane = 0;
            }
        }
        relayout();
        notifySelectionChanged();
    }

    /**
     * Toggles the side-by-side split.
     */
    private void toggleSplit() {
        if (secondaryIndex >= 0) {
            rememberDividerLocation();
            secondaryIndex = -1;
            splitPane = null;
            activePane = 0;
        } else if (open.size() >= 2) {
            secondaryIndex = firstOther(primaryIndex);
            activePane = 1;
        }
        relayout();
    }

    /**
     * Cycles tabs in the active pane.
     *
     * @param delta +1 next, -1 previous
     */
    private void cycleActivePane(int delta) {
        if (open.isEmpty()) {
            return;
        }
        boolean secondaryActive = activePane == 1 && secondaryIndex >= 0;
        int current = secondaryActive ? secondaryIndex : primaryIndex;
        int pos = open.indexOf(current);
        if (pos < 0) {
            pos = 0;
        }
        int next = Math.floorMod(pos + delta, open.size());
        if (secondaryActive) {
            setSecondary(open.get(next));
        } else {
            setPrimary(open.get(next));
        }
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
     * Rebuilds a tab strip for the open panels.
     *
     * @param strip strip panel
     * @param activeIndex the strip's active panel index
     * @param primary true for the primary strip
     */
    private void rebuildStrip(JPanel strip, int activeIndex, boolean primary) {
        strip.removeAll();
        for (int index : open) {
            int panelIndex = index;
            WorkbenchTab tab = new WorkbenchTab(names.get(index),
                    () -> {
                        if (primary) {
                            activePane = 0;
                            setPrimary(panelIndex);
                        } else {
                            activePane = 1;
                            setSecondary(panelIndex);
                        }
                    },
                    () -> closeTab(panelIndex));
            tab.setSelected(index == activeIndex);
            tab.setDragHandler(
                    point -> handleTabDrag(strip, panelIndex, tab, point),
                    () -> finishTabDrag(panelIndex));
            strip.add(tab);
            tab.setPaneActive(primary ? activePane == 0 || secondaryIndex < 0 : activePane == 1);
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
                    open.add(index);
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
     * Handles a live tab drag. Inside the strip the tab reorders; dragged down
     * into the editor body it arms a left/right split drop zone.
     *
     * @param strip strip being dragged from
     * @param draggedPanelIndex panel index of the dragged tab
     * @param tab the dragged tab
     * @param tabPoint mouse point in the tab's coordinate space
     */
    private void handleTabDrag(JPanel strip, int draggedPanelIndex, WorkbenchTab tab, Point tabPoint) {
        Point inArea = SwingUtilities.convertPoint(tab, tabPoint, this);
        Rectangle body = centre.getBounds();
        if (inArea.y < body.y + 6) {
            // Inside the strip band: reorder.
            if (dragZone != DROP_NONE) {
                dragZone = DROP_NONE;
                repaint();
            }
            int stripX = SwingUtilities.convertPoint(tab, tabPoint, strip).x;
            reorderWithinStrip(strip, draggedPanelIndex, stripX);
        } else {
            // In the editor body: arm a split drop zone.
            int zone = dropZoneFor(inArea, body);
            if (zone != dragZone) {
                dragZone = zone;
                repaint();
            }
        }
    }

    /**
     * Chooses a body drop zone. The outer top/bottom bands create vertical
     * splits; the center area creates left/right splits.
     *
     * @param point point in this component
     * @param body editor body bounds
     * @return drop-zone constant
     */
    private static int dropZoneFor(Point point, Rectangle body) {
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

    /**
     * Live-reorders a tab within its strip.
     *
     * @param strip strip
     * @param draggedPanelIndex panel index of the dragged tab
     * @param stripX drag x in the strip's coordinate space
     */
    private void reorderWithinStrip(JPanel strip, int draggedPanelIndex, int stripX) {
        List<WorkbenchTab> tabs = new ArrayList<>();
        for (java.awt.Component child : strip.getComponents()) {
            if (child instanceof WorkbenchTab tab) {
                tabs.add(tab);
            }
        }
        int from = open.indexOf(draggedPanelIndex);
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
        target = Math.max(0, Math.min(open.size() - 1, target));
        if (target == from) {
            return;
        }
        WorkbenchTab dragged = tabs.get(from);
        open.add(target, open.remove(from));
        strip.remove(dragged);
        strip.add(dragged, target);
        strip.revalidate();
        strip.repaint();
    }

    /**
     * Commits a tab drag — splitting the editor when the drag ended in a body
     * drop zone, otherwise committing the reorder.
     *
     * @param draggedPanelIndex panel index of the dragged tab
     */
    private void finishTabDrag(int draggedPanelIndex) {
        int zone = dragZone;
        dragZone = DROP_NONE;
        if (zone == DROP_RIGHT || zone == DROP_BOTTOM) {
            if (!open.contains(draggedPanelIndex)) {
                open.add(draggedPanelIndex);
            }
            splitOrientation = zone == DROP_BOTTOM ? JSplitPane.VERTICAL_SPLIT : JSplitPane.HORIZONTAL_SPLIT;
            setSecondary(draggedPanelIndex);
        } else if (zone == DROP_LEFT || zone == DROP_TOP) {
            splitOrientation = zone == DROP_TOP ? JSplitPane.VERTICAL_SPLIT : JSplitPane.HORIZONTAL_SPLIT;
            if (secondaryIndex < 0 && open.size() >= 2) {
                secondaryIndex = firstOther(draggedPanelIndex);
            }
            setPrimary(draggedPanelIndex);
        } else {
            relayout();
        }
    }

    /**
     * Rebuilds the centre area and tab strips for the current state.
     */
    private void relayout() {
        rememberDividerLocation();
        if (!open.contains(primaryIndex)) {
            primaryIndex = open.isEmpty() ? 0 : open.get(0);
        }
        centre.removeAll();
        primaryHost.removeAll();
        primaryHost.add(panels.get(primaryIndex), BorderLayout.CENTER);
        rebuildStrip(primaryStrip, primaryIndex, true);
        if (secondaryIndex < 0 || !open.contains(secondaryIndex)) {
            secondaryIndex = -1;
            splitPane = null;
            splitButton.setSelected(false);
            centre.add(primaryHost, BorderLayout.CENTER);
        } else {
            splitButton.setSelected(true);
            secondaryHost.removeAll();
            secondaryHost.add(panels.get(secondaryIndex), BorderLayout.CENTER);
            rebuildStrip(secondaryStrip, secondaryIndex, false);
            JPanel secondaryPane = new JPanel(new BorderLayout(0, 4));
            secondaryPane.setOpaque(false);
            secondaryPane.add(secondaryStrip, BorderLayout.NORTH);
            secondaryPane.add(secondaryHost, BorderLayout.CENTER);
            splitPane = new JSplitPane(splitOrientation, primaryHost, secondaryPane);
            styleSplit(splitPane);
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
     * Notifies the primary selection listener.
     */
    private void notifySelectionChanged() {
        if (selectionListener != null) {
            selectionListener.accept(primaryIndex);
        }
    }

    /**
     * Remembers the current divider location before the split pane is rebuilt.
     */
    private void rememberDividerLocation() {
        if (splitPane == null || secondaryIndex < 0) {
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
     * Styles the split pane with a quiet themed divider.
     *
     * @param pane split pane
     */
    private static void styleSplit(JSplitPane pane) {
        pane.setUI(new BasicSplitPaneUI() {
            @Override
            public BasicSplitPaneDivider createDefaultDivider() {
                return new BasicSplitPaneDivider(this) {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public void paint(Graphics graphics) {
                        Graphics2D g = (Graphics2D) graphics.create();
                        try {
                            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                            g.setColor(WorkbenchTheme.BG);
                            g.fillRect(0, 0, getWidth(), getHeight());
                            g.setColor(WorkbenchTheme.LINE);
                            if (orientation == JSplitPane.HORIZONTAL_SPLIT) {
                                int x = getWidth() / 2;
                                g.drawLine(x, 0, x, getHeight());
                                paintGrip(g, x - 1, getHeight() / 2 - 12, false);
                            } else {
                                int y = getHeight() / 2;
                                g.drawLine(0, y, getWidth(), y);
                                paintGrip(g, getWidth() / 2 - 12, y - 1, true);
                            }
                        } finally {
                            g.dispose();
                        }
                    }
                };
            }
        });
        pane.setOneTouchExpandable(false);
        pane.setDividerSize(8);
        pane.setContinuousLayout(true);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.setBackground(WorkbenchTheme.BG);
    }

    /**
     * Paints a quiet divider grip.
     *
     * @param g graphics
     * @param x x
     * @param y y
     * @param horizontal true for a horizontal grip
     */
    private static void paintGrip(Graphics2D g, int x, int y, boolean horizontal) {
        g.setColor(WorkbenchTheme.withAlpha(WorkbenchTheme.MUTED, 120));
        for (int i = 0; i < 3; i++) {
            int dx = horizontal ? i * 8 : 0;
            int dy = horizontal ? 0 : i * 8;
            g.fillRoundRect(x + dx, y + dy, 3, 3, 3, 3);
        }
    }

    /**
     * Paints the children, then the split drop-zone hint while a tab is being
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
    private static Rectangle dropPreviewBounds(Rectangle body, int zone) {
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
            default -> new Rectangle();
        };
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(900, 620);
    }
}
