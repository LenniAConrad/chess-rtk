package application.gui.workbench.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

/**
 * Form-label and compact field helpers shared by Workbench panels through the
 * public {@link Ui} facade.
 */
final class UiFormControls {

    /**
     * Prevents instantiation.
     */
    private UiFormControls() {
        // utility
    }

    /**
     * Creates a muted bold label.
     *
     * @param text text to render or parse
     * @return label
     */
    static JLabel label(String text) {
        JLabel label = new JLabel(text);
        Theme.foreground(label, Theme.ForegroundRole.MUTED);
        label.setFont(Theme.font(Theme.FONT_SECTION_TITLE, Font.BOLD));
        label.setHorizontalAlignment(SwingConstants.LEFT);
        return label;
    }

    /**
     * Returns trimmed text from a field, treating null document text as blank.
     *
     * @param field source field
     * @return trimmed text
     */
    static String trimmed(JTextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }

    /**
     * Creates a muted, plain-weight one-line caption.
     *
     * @param text caption text
     * @return styled caption label
     */
    static JLabel caption(String text) {
        JLabel label = new JLabel(text);
        Theme.foreground(label, Theme.ForegroundRole.MUTED);
        label.setFont(Theme.font(Theme.FONT_METADATA, Font.PLAIN));
        label.setHorizontalAlignment(SwingConstants.LEFT);
        return label;
    }

    /**
     * Pairs a caption with a control in a tight, baseline-aligned row.
     *
     * @param caption caption text, or blank for no label
     * @param control the control component
     * @return labelled control row
     */
    static JPanel labeledControl(String caption, JComponent control) {
        boolean hasCaption = caption != null && !caption.isBlank();
        int gap = hasCaption ? Theme.SPACE_SM : 0;
        JPanel row = UiLayout.transparentPanel(new WrappingFlowLayout(FlowLayout.LEFT, gap, 0));
        if (hasCaption) {
            row.add(label(caption));
        }
        row.add(control);
        return row;
    }

    /**
     * Pairs a fixed-width label with a control in a compact flow row.
     *
     * @param text label text
     * @param control control component
     * @param labelWidth fixed label width in pixels
     * @return labelled control row
     */
    static JComponent labelControlRow(String text, JComponent control, int labelWidth) {
        JLabel label = label(text);
        label.setPreferredSize(new Dimension(labelWidth, Theme.CONTROL_HEIGHT));
        JPanel row = UiLayout.transparentPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_SM, 0));
        row.add(label);
        row.add(control);
        row.add(Box.createHorizontalGlue());
        return row;
    }

    /**
     * Creates one compact form option group.
     *
     * @param text label text
     * @param control option control
     * @return option group
     */
    static JComponent optionGroup(String text, JComponent control) {
        JPanel panel = UiLayout.transparentPanel(new BorderLayout(Theme.SPACE_SM, 0));
        control.setPreferredSize(new Dimension(120, Theme.CONTROL_HEIGHT));
        panel.add(label(text), BorderLayout.WEST);
        panel.add(control, BorderLayout.CENTER);
        return panel;
    }
}
