package application.gui.workbench.window;

import application.gui.workbench.game.GameModel;
import application.gui.workbench.publish.ReportPanel;
import application.gui.workbench.ui.Toast;
import chess.core.Position;
import java.awt.Component;
import javax.swing.DefaultListModel;

/**
 * Host bridge for the extracted report panel.
 */
public final class WindowReportHost extends WindowHost implements ReportPanel.Host {

    /**
     * Creates a report host.
     *
     * @param window owning workbench window
     */
    public WindowReportHost(WindowBase window) {
        super(window);
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
    public Position currentPosition() {
        return window.currentPosition;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public short[] visibleMoves() {
        return window.visibleMoves;
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
    public DefaultListModel<String> tagModel() {
        return window.tagModel;
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
    public void appendConsole(String text) {
        window.appendConsole(text);
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
