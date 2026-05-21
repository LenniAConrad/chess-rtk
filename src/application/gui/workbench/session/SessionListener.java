package application.gui.workbench.session;

import application.gui.workbench.board.*;
import application.gui.workbench.command.*;
import application.gui.workbench.dashboard.*;
import application.gui.workbench.game.*;
import application.gui.workbench.layout.*;
import application.gui.workbench.mcts.*;
import application.gui.workbench.network.*;
import application.gui.workbench.publish.*;
import application.gui.workbench.ui.*;
import application.gui.workbench.window.*;

/**
 * Listener notified whenever the shared {@link Session} state changes
 * — position, engine status, batch readiness or environment health.
 *
 * <p>The dashboard registers one of these so it can re-render from the session
 * model rather than scraping Swing components or console text.</p>
 */
public interface SessionListener {

    /**
     * Invoked, on the Swing event-dispatch thread, after the session changes.
     *
     * @param session the session that changed (the single shared instance)
     */
    public void sessionChanged(Session session);
}
