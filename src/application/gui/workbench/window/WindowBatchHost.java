package application.gui.workbench.window;

import application.gui.workbench.board.*;
import application.gui.workbench.command.*;
import application.gui.workbench.dashboard.*;
import application.gui.workbench.game.*;
import application.gui.workbench.layout.*;
import application.gui.workbench.mcts.*;
import application.gui.workbench.network.*;
import application.gui.workbench.publish.*;
import application.gui.workbench.session.*;
import application.gui.workbench.ui.*;

import java.util.List;

import application.gui.workbench.command.CommandTemplates.TemplateContext;

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
