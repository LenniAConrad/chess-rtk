package application.gui.workbench.ui;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JProgressBar;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import javax.swing.text.NumberFormatter;

/**
 * Applies Workbench styling to Swing input and small feedback controls.
 */
final class ControlStyler {

    /**
     * Client-property key marking combos that already have the enabled-state
     * refresh listener installed.
     */
    private static final String COMBO_STATE_LISTENER_PROPERTY =
            ControlStyler.class.getName() + ".comboStateListener";

    /**
     * Prevents instantiation.
     */
    private ControlStyler() {
        // utility
    }

    /**
     * Styles a combo box.
     *
     * @param combo combo box
     */
    static void styleCombo(JComboBox<?> combo) {
        combo.setUI(new StyledComboBoxUI());
        combo.setOpaque(false);
        applyComboState(combo);
        combo.setFont(Theme.font(Theme.FONT_CONTROL, Font.PLAIN));
        combo.setBorder(InputChrome.compactBorder(false, false));
        InputChrome.install(combo, true);
        combo.setMaximumRowCount(12);
        combo.setRenderer(new StyledComboRenderer(combo));
        installComboStateListener(combo);
    }

    /**
     * Styles multiple combo boxes.
     *
     * @param combos combo boxes
     */
    static void styleCombos(JComboBox<?>... combos) {
        for (JComboBox<?> combo : combos) {
            styleCombo(combo);
        }
    }

    /**
     * Installs the enabled-state refresh listener for a styled combo.
     *
     * @param combo combo box
     */
    private static void installComboStateListener(JComboBox<?> combo) {
        if (Boolean.TRUE.equals(combo.getClientProperty(COMBO_STATE_LISTENER_PROPERTY))) {
            return;
        }
        combo.putClientProperty(COMBO_STATE_LISTENER_PROPERTY, Boolean.TRUE);
        combo.addPropertyChangeListener("enabled", event -> {
            applyComboState(combo);
            combo.repaint();
        });
    }

    /**
     * Applies the active palette to an enabled or disabled combo box.
     *
     * @param combo combo box
     */
    private static void applyComboState(JComboBox<?> combo) {
        boolean enabled = combo.isEnabled();
        combo.setBackground(enabled ? Theme.INPUT : Theme.INPUT_DISABLED);
        combo.setForeground(enabled ? Theme.TEXT : Theme.BUTTON_DISABLED_TEXT);
    }

    /**
     * Styles a spinner and its text editor.
     *
     * @param spinner spinner component
     */
    static void styleSpinner(JSpinner spinner) {
        spinner.setUI(new StyledSpinnerUI());
        spinner.setOpaque(false);
        spinner.setBackground(Theme.INPUT);
        spinner.setForeground(Theme.TEXT);
        spinner.setFont(Theme.font(Theme.FONT_CONTROL, Font.PLAIN));
        spinner.setBorder(InputChrome.compactBorder(false, false));
        InputChrome.install(spinner, true);
        if (spinner.getEditor() instanceof JSpinner.DefaultEditor editor) {
            styleSpinnerEditor(editor);
        }
    }

    /**
     * Styles a spinner whose editor should accept only integer values.
     *
     * @param spinner spinner component
     */
    static void styleIntegerSpinner(JSpinner spinner) {
        spinner.setEditor(new JSpinner.NumberEditor(spinner, "#,##0"));
        styleSpinner(spinner);
        if (spinner.getEditor() instanceof JSpinner.DefaultEditor editor) {
            JFormattedTextField field = editor.getTextField();
            if (field.getFormatter() instanceof NumberFormatter formatter) {
                formatter.setValueClass(Integer.class);
                formatter.setAllowsInvalid(false);
                formatter.setCommitsOnValidEdit(true);
            }
        }
    }

    /**
     * Styles the text field embedded inside a spinner without adding another
     * nested input border.
     *
     * @param editor spinner editor
     */
    static void styleSpinnerEditor(JSpinner.DefaultEditor editor) {
        JFormattedTextField field = editor.getTextField();
        editor.setOpaque(true);
        editor.setBackground(Theme.INPUT);
        field.setOpaque(true);
        field.setBackground(Theme.INPUT);
        field.setForeground(Theme.TEXT);
        field.setDisabledTextColor(Theme.BUTTON_DISABLED_TEXT);
        field.setCaretColor(Theme.ACCENT);
        field.setSelectionColor(Theme.TEXT_SELECTION);
        field.setSelectedTextColor(Theme.TEXT);
        field.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 6));
        field.setFont(Theme.font(Theme.FONT_CONTROL, Font.PLAIN));
        field.setHorizontalAlignment(SwingConstants.RIGHT);
        field.setMinimumSize(new Dimension(32, Theme.CONTROL_HEIGHT - 2));
        if (field.getFormatter() instanceof NumberFormatter formatter) {
            formatter.setCommitsOnValidEdit(true);
        }
        field.addFocusListener(new FocusAdapter() {
            /**
             * Selects the spinner value after focus enters.
             *
             * @param event focus event
             */
            @Override
            public void focusGained(FocusEvent event) {
                SwingUtilities.invokeLater(field::selectAll);
            }
        });
    }

    /**
     * Styles a compact horizontal slider with Workbench palette colors.
     *
     * @param slider slider to style
     */
    static void styleSlider(JSlider slider) {
        slider.setUI(new StyledSliderUI(slider));
        slider.setOpaque(false);
        Theme.foreground(slider, Theme.ForegroundRole.TEXT);
        slider.setBackground(Theme.PANEL_SOLID);
        slider.setFocusable(true);
    }

    /**
     * Styles multiple spinners.
     *
     * @param spinners spinner components
     */
    static void styleSpinners(JSpinner... spinners) {
        for (JSpinner spinner : spinners) {
            styleSpinner(spinner);
        }
    }

    /**
     * Styles multiple text fields.
     *
     * @param fields text fields
     */
    static void styleFields(JTextField... fields) {
        for (JTextField field : fields) {
            Theme.field(field);
        }
    }

    /**
     * Styles multiple text areas.
     *
     * @param areas text areas
     */
    static void styleAreas(JTextArea... areas) {
        for (JTextArea area : areas) {
            Theme.area(area);
        }
    }

    /**
     * Installs empty-field placeholder text without changing the component
     * value.
     *
     * @param component text component
     * @param text placeholder copy
     */
    static void placeholder(JTextComponent component, String text) {
        Theme.placeholder(component, text);
    }

    /**
     * Styles a checkbox that is not using the custom Workbench toggle class.
     *
     * @param box checkbox
     */
    static void styleCheckBox(JCheckBox box) {
        if (box instanceof ToggleBox) {
            return;
        }
        box.setOpaque(false);
        box.setForeground(Theme.TEXT);
        box.setFont(Theme.font(Theme.FONT_DENSE_TABLE, Font.PLAIN));
        box.setFocusPainted(false);
        box.setRolloverEnabled(true);
        box.setIcon(CheckBoxGlyph.INSTANCE);
        box.setSelectedIcon(CheckBoxGlyph.INSTANCE);
        box.setRolloverIcon(CheckBoxGlyph.INSTANCE);
        box.setRolloverSelectedIcon(CheckBoxGlyph.INSTANCE);
        box.setDisabledIcon(CheckBoxGlyph.INSTANCE);
        box.setDisabledSelectedIcon(CheckBoxGlyph.INSTANCE);
        box.setIconTextGap(6);
        box.setBorder(Theme.pad(2, 2, 2, 2));
    }

    /**
     * Styles a compact progress bar.
     *
     * @param bar progress bar
     */
    static void styleProgressBar(JProgressBar bar) {
        bar.setUI(new ProgressBarChrome());
        bar.setOpaque(false);
        bar.setBorderPainted(false);
        bar.setStringPainted(false);
        bar.setForeground(Theme.ACCENT);
        bar.setBackground(Theme.INPUT_DISABLED);
        bar.setPreferredSize(new Dimension(ProgressBarChrome.COMPACT_SIZE));
        bar.setMinimumSize(new Dimension(ProgressBarChrome.COMPACT_SIZE));
    }
}
