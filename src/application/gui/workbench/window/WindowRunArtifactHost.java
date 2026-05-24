package application.gui.workbench.window;

import application.gui.workbench.session.RunArtifacts;
import application.gui.workbench.session.Session;

/**
 * Host bridge for run-log, manifest, and artifact access.
 */
public final class WindowRunArtifactHost extends WindowHost implements RunArtifacts.Host {

    /**
     * Creates a run-artifact host.
     *
     * @param window owning workbench window
     */
    public WindowRunArtifactHost(WindowBase window) {
        super(window);
    }

    @Override
    public Session session() {
        return window.session;
    }

    @Override
    public void appendConsole(String text) {
        window.appendConsole(text);
    }

    @Override
    public void showWarning(String title, String message) {
        window.showWarning(title, message);
    }

    @Override
    public void showError(String title, String message) {
        window.showError(title, message);
    }
}
