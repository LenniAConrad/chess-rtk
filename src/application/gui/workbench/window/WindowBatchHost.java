package application.gui.workbench.window;

import application.gui.workbench.command.BatchPanel;
import application.gui.workbench.command.CommandTemplates.TemplateContext;
import java.util.List;

/**
 * Host bridge for the extracted batch panel.
 */
public final class WindowBatchHost extends WindowHost implements BatchPanel.Host {

    /**
     * Creates a batch host.
     *
     * @param window owning workbench window
     */
    public WindowBatchHost(WindowBase window) {
        super(window);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String currentFen() {
        return window.currentFen();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TemplateContext templateContext() {
        return window.templateContext();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void runCommand(List<String> args, String stdin) {
        window.runCommand(args, stdin);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void copyText(String text) {
        window.copyText(text);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void showError(String title, String message) {
        window.showError(title, message);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateBatchSummary(String summary) {
        window.session.updateBatch(summary);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updatePublishCommand() {
        if (!window.publishingPanels.isEmpty()) {
            window.updatePublishCommand();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void syncBatchDuration(String value) {
        window.syncDurationFromBatch(value);
    }
}
