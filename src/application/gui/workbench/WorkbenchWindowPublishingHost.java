package application.gui.workbench;

import java.awt.Component;
import java.util.List;

import javax.swing.JComponent;

/**
 * Host bridge for the publishing panel.
 */
final class WorkbenchWindowPublishingHost extends WorkbenchWindowHost implements WorkbenchPublishingPanel.Host {

    /**
     * Creates a publishing host.
     *
     * @param window owning workbench window
     */
    WorkbenchWindowPublishingHost(WorkbenchWindowBase window) {
        super(window);
    }

    @Override
    public Component owner() {
        return window;
    }

    @Override
    public String currentFen() {
        return window.currentFen();
    }

    @Override
    public WorkbenchGameModel gameModel() {
        return window.gameModel;
    }

    @Override
    public String batchInputText() {
        return window.batchPanel.inputText();
    }

    @Override
    public JComponent reportPanel() {
        return window.reportPanel.component();
    }

    @Override
    public void generateReport() {
        window.generateReport();
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
    public void stopCommand() {
        window.stopCommand();
    }

    @Override
    public void toast(WorkbenchToast.Kind kind, String message) {
        window.toast(kind, message);
    }

    @Override
    public void showError(String title, String message) {
        window.showError(title, message);
    }
}
