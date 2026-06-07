package application.gui.workbench.board;

/**
 * Callback invoked when the board queues a premove during opponent thinking.
 */
@FunctionalInterface
public interface PremoveHandler {

    /**
     * Queues a premove candidate.
     *
     * @param context premove context
     * @return true when the premove was accepted and should be shown on board
     */
    boolean queue(PremoveContext context);
}
