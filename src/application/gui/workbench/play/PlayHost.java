package application.gui.workbench.play;

import application.gui.workbench.ui.Toast;
import chess.core.Position;

/**
 * Narrow set of callbacks {@link PlaySession} uses to drive the workbench
 * window, so the controller never depends on concrete window internals and can
 * be unit-tested against a fake host.
 *
 * <p>
 * All methods are invoked on the event-dispatch thread.
 * </p>
 */
public interface PlayHost {

    /**
     * Applies a move through the board's normal move funnel (the same path a
     * human click takes), so legality, sounds, history, and board refresh are
     * reused for engine replies.
     *
     * @param move move to play
     */
    void playMove(short move);

    /**
     * Starts a fresh game line from a FEN, replacing the current position.
     *
     * @param fen start position FEN
     */
    void startNewGame(String fen);

    /**
     * Returns the current board FEN.
     *
     * @return current FEN
     */
    String currentFen();

    /**
     * Returns a copy of the current board position.
     *
     * @return current position copy
     */
    Position currentPosition();

    /**
     * Returns the current mainline ply (zero at the root).
     *
     * @return current ply
     */
    int currentPly();

    /**
     * Shows a mainline ply on the board (used by take-back to step the game
     * back to an earlier human-to-move position).
     *
     * @param ply target ply
     */
    void showPly(int ply);

    /**
     * Sets the board orientation so the human's pieces are at the bottom.
     *
     * @param whiteDown true to render white at the bottom
     */
    void setBoardWhiteDown(boolean whiteDown);

    /**
     * Shows a transient status message.
     *
     * @param kind toast kind
     * @param message message text
     */
    void toast(Toast.Kind kind, String message);

    /**
     * Marks the evaluation bar as thinking while the engine searches.
     */
    void setEvalThinking();

    /**
     * Sets the evaluation bar to a white-relative centipawn value.
     *
     * @param whiteCentipawns evaluation in centipawns from white's perspective
     */
    void setEvalWhiteCp(int whiteCentipawns);

    /**
     * Reflects whether the human is currently allowed to move (engine idle and
     * it is the human's turn). Hosts may use this to refresh the cursor.
     *
     * @param humanAllowed true when human input is allowed
     */
    void setInputGate(boolean humanAllowed);

    /**
     * Locks or unlocks position-entry controls (FEN field, board editor,
     * move navigation) for the duration of a game.
     *
     * @param locked true to lock while a game is active
     */
    void setPositionEntryLocked(boolean locked);

    /**
     * Draws a hint on the board as a suggested-move arrow, without playing it.
     * Defaulted to a no-op so existing hosts (and the headless test fake) keep
     * compiling; the real window override draws the board's suggested-move arrow.
     *
     * @param move move to suggest
     */
    default void showHint(short move) {
        // optional; the window host draws the suggested-move arrow
    }

    /**
     * Clears any hint arrow previously shown by {@link #showHint(short)}.
     * Defaulted to a no-op for the same compatibility reason as
     * {@link #showHint(short)}.
     */
    default void clearHint() {
        // optional; the window host clears the suggested-move arrow
    }

    /**
     * Draws a queued premove on the board. Defaulted to a no-op for headless
     * tests and alternate hosts.
     *
     * @param move queued premove
     */
    default void showPremove(short move) {
        // optional; the window host draws the premove arrow
    }

    /**
     * Clears any queued premove arrow.
     */
    default void clearPremove() {
        // optional; the window host clears the premove arrow
    }
}
