package application.gui.workbench.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * Shared compact label/control row for inspector and settings forms.
 */
final class FieldRow extends JPanel {

    /**
     * Serialization identifier for Swing component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Creates a field row.
     *
     * @param label label text
     * @param control field/control component
     * @param labelWidth fixed label width in pixels
     */
    public FieldRow(String label, JComponent control, int labelWidth) {
        this(label, control, labelWidth, null);
    }

    /**
     * Creates a field row with optional trailing metadata/status.
     *
     * @param label label text
     * @param control field/control component
     * @param labelWidth fixed label width in pixels
     * @param trailing optional trailing component
     */
    public FieldRow(String label, JComponent control, int labelWidth, JComponent trailing) {
        super(new BorderLayout(Theme.SPACE_SM, 0));
        setOpaque(false);
        javax.swing.JLabel labelView = UiFormControls.label(label);
        labelView.setPreferredSize(new Dimension(labelWidth, Theme.CONTROL_HEIGHT));
        add(labelView, BorderLayout.WEST);
        if (control != null) {
            add(control, BorderLayout.CENTER);
        }
        if (trailing != null) {
            trailing.setOpaque(false);
            add(trailing, BorderLayout.EAST);
        }
    }
}
