package application.gui.workbench;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
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

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(900, 620);
    }
}
