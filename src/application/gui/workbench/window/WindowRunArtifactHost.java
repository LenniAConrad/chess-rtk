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
