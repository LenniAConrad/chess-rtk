package application.gui.workbench;

import java.util.List;

import application.gui.workbench.WorkbenchCommandTemplates.TemplateContext;

/**
 * Host bridge for the extracted batch panel.
 */
final class WorkbenchWindowBatchHost extends WorkbenchWindowHost implements WorkbenchBatchPanel.Host {

    /**
     * Creates a batch host.
     *
     * @param window owning workbench window
     */
    WorkbenchWindowBatchHost(WorkbenchWindowBase window) {
        super(window);
    }

    @Override
    public String currentFen() {
        return window.currentFen();
    }

    @Override
    public TemplateContext templateContext() {
        return window.templateContext();
    }

    @Override
    public void runCommand(List<String> args, String stdin) {
        window.runCommand(args, stdin);
    }

    @Override
    public void copyText(String text) {
        window.copyText(text);
    }

    @Override
    public void showError(String title, String message) {
        window.showError(title, message);
    }

    @Override
    public void updateBatchSummary(String summary) {
        window.session.updateBatch(summary);
    }

    @Override
    public void updatePublishCommand() {
        if (window.publishingPanel != null) {
            window.updatePublishCommand();
        }
    }

    @Override
    public void syncBatchDuration(String value) {
        window.syncDurationFromBatch(value);
    }
}
