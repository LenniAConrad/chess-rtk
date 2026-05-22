/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package application.gui.workbench.session;

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
