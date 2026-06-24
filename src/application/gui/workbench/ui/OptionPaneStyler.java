package application.gui.workbench.ui;

import java.awt.Component;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JOptionPane;

/**
 * Creates and displays Workbench-styled option-pane dialogs.
 */
final class OptionPaneStyler {

    /**
     * Prevents instantiation.
     */
    private OptionPaneStyler() {
        // utility
    }

    /**
     * Shows a styled confirm dialog.
     *
     * @param owner owner component
     * @param content dialog content
     * @param title dialog title
     * @return JOptionPane
     */
    static int showConfirmDialog(Component owner, JComponent content, String title) {
        JOptionPane pane = new JOptionPane(content, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
        return showOptionPane(owner, title, pane);
    }

    /**
     * Shows a styled error dialog.
     *
     * @param owner owner component
     * @param title dialog title
     * @param message dialog message
     */
    static void showErrorDialog(Component owner, String title, String message) {
        JOptionPane pane = new JOptionPane(message == null ? title : message, JOptionPane.ERROR_MESSAGE,
                JOptionPane.DEFAULT_OPTION);
        showOptionPane(owner, title, pane);
    }

    /**
     * Shows an option pane after styling generated dialog content.
     *
     * @param owner owner component
     * @param title dialog title
     * @param pane option pane
     * @return selected option
     */
    private static int showOptionPane(Component owner, String title, JOptionPane pane) {
        ComponentTreeStyler.style(pane);
        JDialog dialog = pane.createDialog(owner, title);
        dialog.getRootPane().setBorder(BorderFactory.createLineBorder(Theme.LINE));
        ComponentTreeStyler.style(dialog.getContentPane());
        dialog.setVisible(true);
        dialog.dispose();
        Object value = pane.getValue();
        return value instanceof Integer option ? option.intValue() : JOptionPane.CLOSED_OPTION;
    }
}
