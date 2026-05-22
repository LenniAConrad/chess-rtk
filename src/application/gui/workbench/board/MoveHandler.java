/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package application.gui.workbench.board;

/**
 * Callback invoked when the board emits a legal move.
 */
@FunctionalInterface
public interface MoveHandler {

    /**
     * Plays a move.
     *
     * @param move move encoded in CRTK move format
     */
    void play(short move);
}
