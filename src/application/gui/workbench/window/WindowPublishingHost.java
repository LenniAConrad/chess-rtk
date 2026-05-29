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

    /**
     * {@inheritDoc}
     */
    @Override
    public Component owner() {
        return window;
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
    public GameModel gameModel() {
        return window.gameModel;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String batchInputText() {
        return window.batchPanel.inputText();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JComponent reportPanel() {
        return reportPanel.component();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void generateReport() {
        reportPanel.generateReport();
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
    public void stopCommand() {
        window.stopCommand();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void toast(Toast.Kind kind, String message) {
        window.toast(kind, message);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void showError(String title, String message) {
        window.showError(title, message);
    }
}
