package application.gui.workbench;

/**
 * Host bridge for run-log, manifest, and artifact access.
 */
final class WorkbenchWindowRunArtifactHost extends WorkbenchWindowHost implements WorkbenchRunArtifacts.Host {

    /**
     * Creates a run-artifact host.
     *
     * @param window owning workbench window
     */
    WorkbenchWindowRunArtifactHost(WorkbenchWindowBase window) {
        super(window);
    }

    @Override
    public WorkbenchSession session() {
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
