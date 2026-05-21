package application.gui.workbench;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;

/**
 * VS Code-style workbench shell. Holds every workbench panel and shows either
 * one panel full-width or two side by side in a draggable split, each with its
 * own tab strip — so the user can focus one view or compare two, and resize
 * the divide freely.
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
    private final JPanel primaryStrip = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));

    /**
     * Secondary tab strip, shown only when split.
     */
    private final JPanel secondaryStrip = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));

    /**
     * Primary tab buttons.
     */
    private final transient List<JToggleButton> primaryTabs = new ArrayList<>();

    /**
     * Secondary tab buttons.
     */
    private final transient List<JToggleButton> secondaryTabs = new ArrayList<>();

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
    }

    /**
     * Builds the tab strips and shows the first panel. Call once after every
     * panel has been added.
     */
    void install() {
        ButtonGroup primaryGroup = new ButtonGroup();
        for (int i = 0; i < names.size(); i++) {
            int index = i;
            JToggleButton tab = makeTab(names.get(i));
            tab.addActionListener(event -> setPrimary(index));
            primaryGroup.add(tab);
            primaryTabs.add(tab);
            primaryStrip.add(tab);

            JToggleButton secondary = makeTab(names.get(i));
            secondary.addActionListener(event -> setSecondary(index));
            secondaryTabs.add(secondary);
            secondaryStrip.add(secondary);
        }
        WorkbenchTheme.commandTab(splitButton);
        splitButton.setToolTipText("Show two panels side by side");
        splitButton.addActionListener(event -> toggleSplit());

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(primaryStrip, BorderLayout.CENTER);
        JPanel splitHolder = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 4));
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
     * Selects a panel in the primary pane.
     *
     * @param index panel index
     */
    void select(int index) {
        setPrimary(index);
    }

    /**
     * Shows a panel in the primary pane.
     *
     * @param index panel index
     */
    private void setPrimary(int index) {
        if (index < 0 || index >= panels.size()) {
            return;
        }
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
        if (index < 0 || index >= panels.size()) {
            return;
        }
        secondaryIndex = index;
        if (primaryIndex == index) {
            primaryIndex = firstOther(index);
        }
        relayout();
    }

    /**
     * Toggles the side-by-side split.
     */
    private void toggleSplit() {
        secondaryIndex = secondaryIndex < 0 ? firstOther(primaryIndex) : -1;
        relayout();
    }

    /**
     * Returns the first panel index that is not {@code avoid}.
     *
     * @param avoid index to skip
     * @return another index, or {@code avoid} when only one panel exists
     */
    private int firstOther(int avoid) {
        for (int i = 0; i < panels.size(); i++) {
            if (i != avoid) {
                return i;
            }
        }
        return avoid;
    }

    /**
     * Rebuilds the centre area for the current split state.
     */
    private void relayout() {
        centre.removeAll();
        primaryHost.removeAll();
        primaryHost.add(panels.get(primaryIndex), BorderLayout.CENTER);
        for (int i = 0; i < primaryTabs.size(); i++) {
            primaryTabs.get(i).setSelected(i == primaryIndex);
        }
        if (secondaryIndex < 0) {
            splitButton.setSelected(false);
            centre.add(primaryHost, BorderLayout.CENTER);
        } else {
            splitButton.setSelected(true);
            secondaryHost.removeAll();
            secondaryHost.add(panels.get(secondaryIndex), BorderLayout.CENTER);
            for (int i = 0; i < secondaryTabs.size(); i++) {
                secondaryTabs.get(i).setSelected(i == secondaryIndex);
                secondaryTabs.get(i).setEnabled(i != primaryIndex);
            }
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
     * Creates a themed tab toggle button.
     *
     * @param name tab label
     * @return styled toggle button
     */
    private static JToggleButton makeTab(String name) {
        JToggleButton tab = new JToggleButton(name);
        WorkbenchTheme.commandTab(tab);
        return tab;
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
