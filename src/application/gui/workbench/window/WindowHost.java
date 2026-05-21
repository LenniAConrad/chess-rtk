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

import java.util.Objects;

/**
 * Shared base for small workbench-window host adapters.
 */
public abstract class WindowHost {

    /** Owning workbench window. */
    protected final WindowBase window;

    /**
     * Creates a host adapter.
     *
     * @param window owning workbench window
     */
    public WindowHost(WindowBase window) {
        this.window = Objects.requireNonNull(window, "window");
    }
}
