package application.gui.platform;

/**
 * Shows blocking dialogs through the GUI shell.
 */
@FunctionalInterface
public interface DialogService {

    /**
     * Shows an error dialog.
     *
     * @param title dialog title
     * @param message dialog message
     */
    void showError(String title, String message);
}
