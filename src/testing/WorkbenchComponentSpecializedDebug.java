package testing;

import static testing.WorkbenchComponentDebugFormat.className;
import static testing.WorkbenchComponentDebugFormat.dim;
import static testing.WorkbenchComponentDebugFormat.quote;

import application.gui.workbench.ui.SwitchedWorkspace;
import java.awt.Component;
import java.awt.Window;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JProgressBar;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.SwingConstants;

/**
 * Appends diagnostics for Swing component types that need type-specific fields.
 */
final class WorkbenchComponentSpecializedDebug {

    /**
     * Prevents instantiation.
     */
    private WorkbenchComponentSpecializedDebug() {
        // utility
    }

    /**
     * Appends type-specific diagnostics for a component.
     *
     * @param component component to inspect
     * @param indent indentation prefix
     * @param out output buffer
     */
    static void append(Component component, String indent, StringBuilder out) {
        appendComboBox(component, indent, out);
        appendTable(component, indent, out);
        appendList(component, indent, out);
        appendScrollPane(component, indent, out);
        appendSplitPane(component, indent, out);
        appendTabbedPane(component, indent, out);
        appendWorkbenchWorkspace(component, indent, out);
        appendSlider(component, indent, out);
        appendSpinner(component, indent, out);
        appendProgressBar(component, indent, out);
        appendScrollBar(component, indent, out);
        appendWindow(component, indent, out);
        appendFrame(component, indent, out);
    }

    /**
     * Appends combo-box diagnostics.
     *
     * @param component component to inspect
     * @param indent indentation prefix
     * @param out output buffer
     */
    private static void appendComboBox(Component component, String indent, StringBuilder out) {
        if (component instanceof JComboBox<?> combo) {
            out.append(indent).append("  comboBox: itemCount=").append(combo.getItemCount())
                    .append(" selectedIndex=").append(combo.getSelectedIndex())
                    .append(" selectedItem=").append(quote(String.valueOf(combo.getSelectedItem())))
                    .append(" editable=").append(combo.isEditable())
                    .append('\n');
        }
    }

    /**
     * Appends table diagnostics.
     *
     * @param component component to inspect
     * @param indent indentation prefix
     * @param out output buffer
     */
    private static void appendTable(Component component, String indent, StringBuilder out) {
        if (component instanceof JTable table) {
            out.append(indent).append("  table: rows=").append(table.getRowCount())
                    .append(" columns=").append(table.getColumnCount())
                    .append(" rowHeight=").append(table.getRowHeight())
                    .append(" selectedRows=").append(table.getSelectedRowCount())
                    .append(" selectedColumns=").append(table.getSelectedColumnCount())
                    .append(" autoResizeMode=").append(table.getAutoResizeMode())
                    .append('\n');
        }
    }

    /**
     * Appends list diagnostics.
     *
     * @param component component to inspect
     * @param indent indentation prefix
     * @param out output buffer
     */
    private static void appendList(Component component, String indent, StringBuilder out) {
        if (component instanceof JList<?> list) {
            out.append(indent).append("  list: modelSize=").append(list.getModel().getSize())
                    .append(" selectedIndex=").append(list.getSelectedIndex())
                    .append(" selectedCount=").append(list.getSelectedIndices().length)
                    .append(" visibleRowCount=").append(list.getVisibleRowCount())
                    .append('\n');
        }
    }

    /**
     * Appends scroll-pane diagnostics.
     *
     * @param component component to inspect
     * @param indent indentation prefix
     * @param out output buffer
     */
    private static void appendScrollPane(Component component, String indent, StringBuilder out) {
        if (component instanceof JScrollPane scroll) {
            JViewport viewport = scroll.getViewport();
            Component view = viewport == null ? null : viewport.getView();
            out.append(indent).append("  scrollPane: horizontalPolicy=")
                    .append(scroll.getHorizontalScrollBarPolicy())
                    .append(" verticalPolicy=").append(scroll.getVerticalScrollBarPolicy())
                    .append(" viewportView=").append(view == null ? "<null>" : view.getClass().getName())
                    .append(" extent=").append(viewport == null ? "<null>" : dim(viewport.getExtentSize()))
                    .append(" viewSize=").append(viewport == null ? "<null>" : dim(viewport.getViewSize()))
                    .append('\n');
        }
    }

    /**
     * Appends split-pane diagnostics.
     *
     * @param component component to inspect
     * @param indent indentation prefix
     * @param out output buffer
     */
    private static void appendSplitPane(Component component, String indent, StringBuilder out) {
        if (component instanceof JSplitPane split) {
            out.append(indent).append("  splitPane: orientation=")
                    .append(split.getOrientation() == JSplitPane.HORIZONTAL_SPLIT ? "horizontal" : "vertical")
                    .append(" dividerLocation=").append(split.getDividerLocation())
                    .append(" resizeWeight=").append(split.getResizeWeight())
                    .append(" continuousLayout=").append(split.isContinuousLayout())
                    .append('\n');
        }
    }

    /**
     * Appends tabbed-pane diagnostics.
     *
     * @param component component to inspect
     * @param indent indentation prefix
     * @param out output buffer
     */
    private static void appendTabbedPane(Component component, String indent, StringBuilder out) {
        if (component instanceof JTabbedPane tabs) {
            out.append(indent).append("  tabbedPane: tabCount=").append(tabs.getTabCount())
                    .append(" selectedIndex=").append(tabs.getSelectedIndex())
                    .append(" layoutPolicy=").append(tabs.getTabLayoutPolicy())
                    .append('\n');
            for (int i = 0; i < tabs.getTabCount(); i++) {
                out.append(indent).append("    tab[").append(i).append("]: title=")
                        .append(quote(tabs.getTitleAt(i)))
                        .append(" enabled=").append(tabs.isEnabledAt(i))
                        .append(" component=").append(className(tabs.getComponentAt(i)))
                        .append('\n');
            }
        }
    }

    /**
     * Appends Workbench switched-workspace diagnostics.
     *
     * @param component component to inspect
     * @param indent indentation prefix
     * @param out output buffer
     */
    private static void appendWorkbenchWorkspace(Component component, String indent, StringBuilder out) {
        if (component instanceof SwitchedWorkspace workspace) {
            out.append(indent).append("  switchedWorkspace: mode=").append(workspace.mode()).append('\n');
        }
    }

    /**
     * Appends slider diagnostics.
     *
     * @param component component to inspect
     * @param indent indentation prefix
     * @param out output buffer
     */
    private static void appendSlider(Component component, String indent, StringBuilder out) {
        if (component instanceof JSlider slider) {
            out.append(indent).append("  slider: min=").append(slider.getMinimum())
                    .append(" max=").append(slider.getMaximum())
                    .append(" value=").append(slider.getValue())
                    .append(" majorTick=").append(slider.getMajorTickSpacing())
                    .append(" minorTick=").append(slider.getMinorTickSpacing())
                    .append('\n');
        }
    }

    /**
     * Appends spinner diagnostics.
     *
     * @param component component to inspect
     * @param indent indentation prefix
     * @param out output buffer
     */
    private static void appendSpinner(Component component, String indent, StringBuilder out) {
        if (component instanceof JSpinner spinner) {
            out.append(indent).append("  spinner: value=").append(quote(String.valueOf(spinner.getValue())))
                    .append(" editor=").append(className(spinner.getEditor()))
                    .append('\n');
        }
    }

    /**
     * Appends progress-bar diagnostics.
     *
     * @param component component to inspect
     * @param indent indentation prefix
     * @param out output buffer
     */
    private static void appendProgressBar(Component component, String indent, StringBuilder out) {
        if (component instanceof JProgressBar progress) {
            out.append(indent).append("  progressBar: min=").append(progress.getMinimum())
                    .append(" max=").append(progress.getMaximum())
                    .append(" value=").append(progress.getValue())
                    .append(" indeterminate=").append(progress.isIndeterminate())
                    .append(" string=").append(quote(progress.getString()))
                    .append('\n');
        }
    }

    /**
     * Appends scroll-bar diagnostics.
     *
     * @param component component to inspect
     * @param indent indentation prefix
     * @param out output buffer
     */
    private static void appendScrollBar(Component component, String indent, StringBuilder out) {
        if (component instanceof JScrollBar bar) {
            out.append(indent).append("  scrollBar: orientation=")
                    .append(bar.getOrientation() == SwingConstants.HORIZONTAL ? "horizontal" : "vertical")
                    .append(" value=").append(bar.getValue())
                    .append(" visibleAmount=").append(bar.getVisibleAmount())
                    .append(" min=").append(bar.getMinimum())
                    .append(" max=").append(bar.getMaximum())
                    .append('\n');
        }
    }

    /**
     * Appends window diagnostics.
     *
     * @param component component to inspect
     * @param indent indentation prefix
     * @param out output buffer
     */
    private static void appendWindow(Component component, String indent, StringBuilder out) {
        if (component instanceof Window window) {
            out.append(indent).append("  window: active=").append(window.isActive())
                    .append(" focused=").append(window.isFocused())
                    .append(" alwaysOnTop=").append(window.isAlwaysOnTop())
                    .append(" owner=").append(className(window.getOwner()))
                    .append('\n');
        }
    }

    /**
     * Appends frame diagnostics.
     *
     * @param component component to inspect
     * @param indent indentation prefix
     * @param out output buffer
     */
    private static void appendFrame(Component component, String indent, StringBuilder out) {
        if (component instanceof JFrame frame) {
            out.append(indent).append("  frame: title=").append(quote(frame.getTitle()))
                    .append(" state=").append(frame.getExtendedState())
                    .append(" defaultCloseOperation=").append(frame.getDefaultCloseOperation())
                    .append('\n');
        }
    }
}
