package application.gui.layout.command;

import java.awt.Dimension;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import application.gui.ui.RoundedPanel;

/**
 * CommandCenterContext interface.
 *
 * Provides interface behavior for the GUI module.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public interface CommandCenterContext {
    /**
     * createMutedLabel method.
     *
     * @param text parameter.
     * @return return value.
     */
    JLabel createMutedLabel(String text);
    /**
     * createThemedButton method.
     *
     * @param text parameter.
     * @param action parameter.
     * @return return value.
     */
    JButton createThemedButton(String text, ActionListener action);
    /**
     * createThemedCheckbox method.
     *
     * @param text parameter.
     * @param selected parameter.
     * @param action parameter.
     * @return return value.
     */
    JCheckBox createThemedCheckbox(String text, boolean selected, ActionListener action);
    /**
     * buildFlatCard method.
     *
     * @param title parameter.
     * @return return value.
     */
    RoundedPanel buildFlatCard(String title);
    /**
     * scaledDimension method.
     *
     * @param base parameter.
     * @return return value.
     */
    Dimension scaledDimension(Dimension base);
    /**
     * registerFlatCard method.
     *
     * @param card parameter.
     */
    void registerFlatCard(RoundedPanel card);
    /**
     * registerComboBox method.
     *
     * @param combo parameter.
     */
    void registerComboBox(JComboBox<?> combo);
    /**
     * registerTextField method.
     *
     * @param field parameter.
     */
    void registerTextField(JTextField field);
    /**
     * registerTextArea method.
     *
     * @param area parameter.
     */
    void registerTextArea(JTextArea area);
    /**
     * registerList method.
     *
     * @param list parameter.
     */
    void registerList(JList<?> list);
    /**
     * registerScrollPane method.
     *
     * @param scroll parameter.
     */
    void registerScrollPane(JScrollPane scroll);
    /**
     * registerButton method.
     *
     * @param button parameter.
     */
    void registerButton(JButton button);
    /**
     * requestFenToggle method.
     */
    void requestFenToggle();
    /**
     * requestCommandRun method.
     */
    void requestCommandRun();
    /**
     * requestCommandStop method.
     */
    void requestCommandStop();
    /**
     * requestCommandHelp method.
     */
    void requestCommandHelp();
    /**
     * requestRecentCommand method.
     *
     * @param index parameter.
     */
    void requestRecentCommand(int index);
    /**
     * requestCommandFormUpdate method.
     */
    void requestCommandFormUpdate();
}
