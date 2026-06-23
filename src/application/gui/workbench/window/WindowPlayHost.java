package application.gui.workbench.window;

import application.gui.workbench.play.PlayHost;
import application.gui.workbench.ui.Toast;
import chess.core.Move;
import chess.core.Position;

/**
 * Bridges {@link application.gui.workbench.play.PlaySession} to the owning
 * workbench window, mirroring the {@link WindowHost} adapter pattern used by the
 * Dashboard, Batch, and Publishing panels. Living in the window package lets it
 * reach the window's protected board, eval bar, and move funnel without widening
 * their visibility.
 */
public final class WindowPlayHost extends WindowHost implements PlayHost {

    /**
     * Creates a play host.
     *
     * @param window owning workbench window
     */
    public WindowPlayHost(WindowBase window) {
        super(window);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void playMove(short move) {
        window.playMove(move);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void startNewGame(String fen) {
        window.startNewGame(fen);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String currentFen() {
        return window.currentFen();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Position currentPosition() {
        return window.currentPosition == null ? null : window.currentPosition.copy();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int currentPly() {
        return window.gameModel.currentPly();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void showPly(int ply) {
        window.showGamePly(ply);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBoardWhiteDown(boolean whiteDown) {
        window.board.setWhiteDown(whiteDown);
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
    public void setEvalThinking() {
        window.evalBar.setThinking();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setEvalWhiteCp(int whiteCentipawns) {
        window.evalBar.setCentipawns(whiteCentipawns);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setInputGate(boolean humanAllowed) {
        // The board's drag-start filter already consults the session for gating;
        // repaint so the hover cursor reflects the new turn state promptly.
        window.board.repaint();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setPositionEntryLocked(boolean locked) {
        window.setPlayPositionLocked(locked);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void showHint(short move) {
        window.board.setSuggestedMove(move);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clearHint() {
        window.board.setSuggestedMove(Move.NO_MOVE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void showPremove(short move) {
        window.board.setPremove(move);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clearPremove() {
        window.board.clearPremove();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void saveGameState(String status) {
        window.persistCurrentGame(status);
    }
}
