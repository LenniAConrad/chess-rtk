package application.gui.workbench;

/**
 * Listener notified whenever the shared {@link WorkbenchSession} state changes
 * — position, engine status, batch readiness or environment health.
 *
 * <p>The dashboard registers one of these so it can re-render from the session
 * model rather than scraping Swing components or console text.</p>
 */
interface WorkbenchSessionListener {

    /**
     * Invoked, on the Swing event-dispatch thread, after the session changes.
     *
     * @param session the session that changed (the single shared instance)
     */
    void sessionChanged(WorkbenchSession session);
}
