package application.gui.workbench.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/**
 * Recursive styling pass for plain Swing component trees created by dialogs,
 * file choosers, and small ad hoc panels.
 */
final class ComponentTreeStyler {

    /**
     * Prevents instantiation.
     */
    private ComponentTreeStyler() {
        // utility
    }

    /**
     * Applies workbench styling to a component subtree.
     *
     * @param component root component
     */
    static void style(Component component) {
        if (component == null) {
            return;
        }
        styleComponent(component);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                style(child);
            }
        }
        if (component instanceof JScrollPane pane) {
            Ui.styleScrollPane(pane);
        }
    }

    /**
     * Applies styling for one component in a recursively scanned component tree.
     *
     * @param component component
     */
    private static void styleComponent(Component component) {
        if (component instanceof JFileChooser chooser) {
            chooser.setOpaque(true);
            chooser.setBackground(Theme.BG);
            chooser.setForeground(Theme.TEXT);
        } else if (component instanceof JPopupMenu popup) {
            Ui.stylePopupMenu(popup);
        } else if (component instanceof JMenuItem item) {
            Ui.stylePopupMenuItem(item);
        } else if (component instanceof JSeparator separator) {
            separator.setBackground(Theme.PANEL_SOLID);
            separator.setForeground(Theme.LINE);
        } else if (component instanceof JScrollPane pane) {
            Ui.styleScrollPane(pane);
        } else if (component instanceof JTextArea area) {
            Theme.area(area);
        } else if (component instanceof JFormattedTextField field
                && field.getParent() instanceof JSpinner.DefaultEditor editor) {
            Ui.styleSpinnerEditor(editor);
        } else if (component instanceof JTextField field) {
            Theme.field(field);
        } else if (component instanceof JComboBox<?> combo) {
            Ui.styleCombo(combo);
        } else if (component instanceof JSlider slider) {
            Ui.styleSlider(slider);
        } else if (component instanceof JSpinner spinner) {
            Ui.styleSpinner(spinner);
        } else if (component instanceof JProgressBar bar) {
            Ui.styleProgressBar(bar);
        } else if (component instanceof JTable table) {
            Theme.table(table, Math.max(24, table.getRowHeight()));
        } else if (component instanceof JList<?> list) {
            Theme.list(list);
            list.setFixedCellHeight(Math.max(24, list.getFixedCellHeight()));
        } else if (component instanceof JTabbedPane tabs) {
            Ui.styleTabs(tabs);
        } else if (component instanceof JCheckBox box) {
            Ui.styleCheckBox(box);
        } else if (component instanceof AbstractButton button) {
            styleAbstractButton(button);
        } else if (component instanceof JLabel label) {
            Theme.refreshForeground(label);
            label.setFont(Theme.font(12, Font.PLAIN));
        } else if (component instanceof JPanel panel) {
            panel.setOpaque(true);
            panel.setBackground(Theme.PANEL_SOLID);
        } else if (component instanceof JComponent jComponent) {
            jComponent.setBackground(Theme.PANEL_SOLID);
            Theme.refreshForeground(jComponent);
        }
    }

    /**
     * Styles a standard dialog button without breaking icon-only chooser controls.
     *
     * @param button button
     */
    private static void styleAbstractButton(AbstractButton button) {
        String text = button.getText();
        if (text != null && !text.isBlank() && button instanceof JButton) {
            Theme.button(button, false);
            return;
        }
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setRolloverEnabled(true);
        button.setForeground(Theme.TEXT);
        button.setFont(Theme.font(12, Font.PLAIN));
        button.setBorder(Theme.pad(4, 6, 4, 6));
        if (button.getIcon() != null) {
            Dimension size = new Dimension(32, 32);
            button.setMargin(new Insets(0, 0, 0, 0));
            button.setPreferredSize(size);
            button.setMinimumSize(size);
            button.setMaximumSize(size);
        }
    }
}
