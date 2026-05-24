package application.gui.workbench.window;

import application.gui.workbench.game.GameModel;
import application.gui.workbench.publish.PublishingPanel;
import application.gui.workbench.publish.ReportPanel;
import application.gui.workbench.ui.Toast;
import java.awt.Component;
import java.util.List;
import javax.swing.JComponent;

/**
 * Host bridge for the publishing panel.
 */
public final class WindowPublishingHost extends WindowHost implements PublishingPanel.Host {

    /**
     * Report panel owned by this publishing tab instance.
     */
    private final ReportPanel reportPanel;

    /**
     * Creates a publishing host.
     *
     * @param window owning workbench window
     */
    public WindowPublishingHost(WindowBase window) {
        super(window);
        reportPanel = new ReportPanel(new WindowReportHost(window));
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
    public GameModel gameModel() {
        return window.gameModel;
    }

    @Override
    public String batchInputText() {
        return window.batchPanel.inputText();
    }

    @Override
    public JComponent reportPanel() {
        return reportPanel.component();
    }

    @Override
    public void generateReport() {
        reportPanel.generateReport();
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
    public void toast(Toast.Kind kind, String message) {
        window.toast(kind, message);
    }

    @Override
    public void showError(String title, String message) {
        window.showError(title, message);
    }
}
