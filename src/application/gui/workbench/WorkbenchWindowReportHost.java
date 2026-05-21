package application.gui.workbench;

import java.awt.Component;

import javax.swing.DefaultListModel;

import chess.core.Position;

/**
 * Host bridge for the extracted report panel.
 */
final class WorkbenchWindowReportHost extends WorkbenchWindowHost implements WorkbenchReportPanel.Host {

    /**
     * Creates a report host.
     *
     * @param window owning workbench window
     */
    WorkbenchWindowReportHost(WorkbenchWindowBase window) {
        super(window);
    }

    @Override
    public Component owner() {
        return window;
    }

    @Override
    public Position currentPosition() {
        return window.currentPosition;
    }

    @Override
    public short[] visibleMoves() {
        return window.visibleMoves;
    }

    @Override
    public WorkbenchGameModel gameModel() {
        return window.gameModel;
    }

    @Override
    public DefaultListModel<String> tagModel() {
        return window.tagModel;
    }

    @Override
    public void copyText(String text) {
        window.copyText(text);
    }

    @Override
    public void appendConsole(String text) {
        window.appendConsole(text);
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
