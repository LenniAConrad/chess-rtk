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
import java.util.ArrayList;
import java.util.List;

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
     * Armed split drop zone during a tab drag: 0 none, 1 left, 2 right.
     */
    private int dragZone;

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
        setPrimary(index);
    }

    /**
     * Shows a panel in the primary pane.
     *
     * @param index panel index
     */
    private void setPrimary(int index) {
        primaryIndex = index;
        if (secondaryIndex == index) {
            secondaryIndex = firstOther(index);
        }
        relayout();
    }

    /**
     * Shows a panel in the secondary pane.
     *
     * @param index panel index
     */
    private void setSecondary(int index) {
        secondaryIndex = index;
        if (primaryIndex == index) {
            primaryIndex = firstOther(index);
        }
        relayout();
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
        }
        relayout();
    }

    /**
     * Toggles the side-by-side split.
     */
    private void toggleSplit() {
        if (secondaryIndex >= 0) {
            secondaryIndex = -1;
        } else if (open.size() >= 2) {
            secondaryIndex = firstOther(primaryIndex);
        }
        relayout();
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
                            setPrimary(panelIndex);
                        } else {
                            setSecondary(panelIndex);
                        }
                    },
                    () -> closeTab(panelIndex));
            tab.setSelected(index == activeIndex);
            tab.setDragHandler(
                    point -> handleTabDrag(strip, panelIndex, tab, point),
                    () -> finishTabDrag(panelIndex));
            strip.add(tab);
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
            if (dragZone != 0) {
                dragZone = 0;
                repaint();
            }
            int stripX = SwingUtilities.convertPoint(tab, tabPoint, strip).x;
            reorderWithinStrip(strip, draggedPanelIndex, stripX);
        } else {
            // In the editor body: arm a split drop zone.
            int zone = inArea.x < body.x + body.width / 2 ? 1 : 2;
            if (zone != dragZone) {
                dragZone = zone;
                repaint();
            }
        }
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
        dragZone = 0;
        if (zone == 2) {
            if (!open.contains(draggedPanelIndex)) {
                open.add(draggedPanelIndex);
            }
            setSecondary(draggedPanelIndex);
        } else if (zone == 1) {
            setPrimary(draggedPanelIndex);
        } else {
            relayout();
        }
    }

    /**
     * Rebuilds the centre area and tab strips for the current state.
     */
    private void relayout() {
        if (!open.contains(primaryIndex)) {
            primaryIndex = open.isEmpty() ? 0 : open.get(0);
        }
        centre.removeAll();
        primaryHost.removeAll();
        primaryHost.add(panels.get(primaryIndex), BorderLayout.CENTER);
        rebuildStrip(primaryStrip, primaryIndex, true);
        if (secondaryIndex < 0 || !open.contains(secondaryIndex)) {
            secondaryIndex = -1;
            splitButton.setSelected(false);
            centre.add(primaryHost, BorderLayout.CENTER);
        } else {
            splitButton.setSelected(true);
            secondaryHost.removeAll();
            secondaryHost.add(panels.get(secondaryIndex), BorderLayout.CENTER);
            rebuildStrip(secondaryStrip, secondaryIndex, false);
            JPanel right = new JPanel(new BorderLayout(0, 4));
            right.setOpaque(false);
            right.add(secondaryStrip, BorderLayout.NORTH);
            right.add(secondaryHost, BorderLayout.CENTER);
            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, primaryHost, right);
            styleSplit(split);
            split.setResizeWeight(0.5);
            split.setDividerLocation(0.5);
            centre.add(split, BorderLayout.CENTER);
        }
        centre.revalidate();
        centre.repaint();
    }

    /**
     * Styles the split pane with a quiet themed divider and one-touch
     * collapse arrows.
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
                        graphics.setColor(WorkbenchTheme.BG);
                        graphics.fillRect(0, 0, getWidth(), getHeight());
                        graphics.setColor(WorkbenchTheme.LINE);
                        int x = getWidth() / 2;
                        graphics.drawLine(x, 0, x, getHeight());
                        super.paint(graphics);
                    }
                };
            }
        });
        pane.setOneTouchExpandable(true);
        pane.setDividerSize(11);
        pane.setContinuousLayout(true);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.setBackground(WorkbenchTheme.BG);
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
        if (dragZone == 0) {
            return;
        }
        Rectangle body = centre.getBounds();
        int half = body.width / 2;
        int x = dragZone == 1 ? body.x : body.x + half;
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setColor(new Color(WorkbenchTheme.ACCENT.getRed(), WorkbenchTheme.ACCENT.getGreen(),
                    WorkbenchTheme.ACCENT.getBlue(), 48));
            g.fillRect(x, body.y, half, body.height);
            g.setColor(WorkbenchTheme.ACCENT);
            g.setStroke(new BasicStroke(2f));
            g.drawRect(x + 1, body.y + 1, half - 2, body.height - 2);
        } finally {
            g.dispose();
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(900, 620);
    }
}
