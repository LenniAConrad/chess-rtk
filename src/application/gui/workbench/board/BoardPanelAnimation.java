package application.gui.workbench.board;

import chess.core.Field;
import chess.core.Move;
import chess.core.Piece;
import java.awt.Rectangle;
import javax.swing.Timer;

/**
 * Drives board animation timers and dirty repaint throttling for {@link BoardPanel}.
 */
final class BoardPanelAnimation {

    /**
     * Animation timer delay in milliseconds.
     */
    private static final int ANIMATION_DELAY_MS = 16;

    /**
     * Extra repaint padding around drag dirty regions.
     */
    private static final int DRAG_REPAINT_PADDING = 8;

    /**
     * Minimum nanoseconds between throttled drag repaint flushes.
     */
    private static final long DRAG_REPAINT_FRAME_NANOS = 16_000_000L;

    /**
     * Board component owning interaction and render state.
     */
    private final BoardPanel boardPanel;

    /**
     * Move, snap, flip, and wrong-move marker timer.
     */
    private final Timer animationTimer;

    /**
     * Timer used to coalesce drag dirty repaints.
     */
    private final Timer dragRepaintTimer;

    /**
     * Dirty rectangle queued for throttled drag repainting.
     */
    private Rectangle pendingDragDirty;

    /**
     * Last throttled drag repaint timestamp.
     */
    private long lastDragRepaintNanos;

    /**
     * Creates an animation driver for one board panel.
     *
     * @param boardPanel board component to drive
     */
    BoardPanelAnimation(BoardPanel boardPanel) {
        this.boardPanel = boardPanel;
        animationTimer = new Timer(ANIMATION_DELAY_MS, event -> tickAnimation());
        animationTimer.setCoalesce(true);
        dragRepaintTimer = new Timer(ANIMATION_DELAY_MS, event -> flushPendingDragRepaint());
        dragRepaintTimer.setCoalesce(true);
        dragRepaintTimer.setRepeats(false);
    }

    void stop() {
        animationTimer.stop();
        dragRepaintTimer.stop();
    }

    Rectangle dragRepaintBounds(
            Rectangle board,
            byte targetSquare,
            byte hoverSquare,
            int pointerX,
            int pointerY,
            boolean includeDraggedPiece,
            boolean includeOriginSquare) {
        Rectangle dirty = null;
        byte dragSquare = boardPanel.pieceInput.dragSquare();
        if (includeOriginSquare && BoardPanel.isSquareIndex(dragSquare)) {
            dirty = union(dirty, expanded(boardPanel.squareBounds(board, dragSquare), DRAG_REPAINT_PADDING));
        }
        if (BoardPanel.isSquareIndex(targetSquare)) {
            dirty = union(dirty, expanded(boardPanel.squareBounds(board, targetSquare), DRAG_REPAINT_PADDING));
        }
        if (BoardPanel.isSquareIndex(hoverSquare)) {
            dirty = union(dirty, expanded(boardPanel.squareBounds(board, hoverSquare), DRAG_REPAINT_PADDING));
        }
        if (includeDraggedPiece && boardPanel.pieceInput.draggedPiece() != Piece.EMPTY) {
            int cell = board.width / 8;
            int scaledCell = (int) Math.round(cell * BoardPanel.DRAG_SCALE);
            dirty = union(dirty, new Rectangle(
                    pointerX - scaledCell / 2 - DRAG_REPAINT_PADDING,
                    pointerY - scaledCell / 2 - DRAG_REPAINT_PADDING,
                    scaledCell + DRAG_REPAINT_PADDING * 2,
                    scaledCell + DRAG_REPAINT_PADDING * 2));
        }
        return dirty;
    }

    void scheduleDragRepaint(Rectangle dirty) {
        if (dirty == null) {
            return;
        }
        pendingDragDirty = union(pendingDragDirty, dirty);
        long now = System.nanoTime();
        long elapsed = lastDragRepaintNanos == 0L ? Long.MAX_VALUE : now - lastDragRepaintNanos;
        if (elapsed >= DRAG_REPAINT_FRAME_NANOS) {
            flushPendingDragRepaint();
            return;
        }
        if (!dragRepaintTimer.isRunning()) {
            long remaining = DRAG_REPAINT_FRAME_NANOS - elapsed;
            int delayMs = (int) Math.max(1L, (remaining + 999_999L) / 1_000_000L);
            dragRepaintTimer.setInitialDelay(delayMs);
            dragRepaintTimer.restart();
        }
    }

    void scheduleDragRepaint(Rectangle first, Rectangle second) {
        scheduleDragRepaint(union(first, second));
    }

    void cancelPendingDragRepaint() {
        pendingDragDirty = null;
        lastDragRepaintNanos = 0L;
        dragRepaintTimer.stop();
    }

    void startAnimation() {
        if (!animationTimer.isRunning()) {
            animationTimer.start();
        }
    }

    void startMoveAnimation(byte[] oldBoard, byte[] newBoard, short move, boolean reverseMoveAnimation) {
        if (!boardPanel.animationState.canAnimateMove()) {
            clearMoveAnimation();
            return;
        }
        clearMoveAnimation();
        if (oldBoard == null || newBoard == null || move == Move.NO_MOVE) {
            return;
        }
        byte from = Move.getFromIndex(move);
        byte to = Move.getToIndex(move);
        if (!BoardPanel.isSquareIndex(from) || !BoardPanel.isSquareIndex(to)) {
            return;
        }
        byte moveFrom = from;
        byte moveTo = to;
        if (reverseMoveAnimation) {
            from = moveTo;
            to = moveFrom;
        }
        byte piece = animatedPiece(oldBoard, newBoard, from, to);
        if (piece == Piece.EMPTY) {
            return;
        }
        boardPanel.animationState.startMove(piece, from, to, System.currentTimeMillis());
        configureAnimatedCapture(oldBoard, newBoard, from, to, piece);
        configureSecondaryMove(oldBoard, newBoard, moveFrom, moveTo, reverseMoveAnimation);
        startAnimation();
    }

    void clearMoveAnimation() {
        boardPanel.animationState.clearMoveAnimation();
    }

    void clearAllAnimations() {
        if (boardPanel.animationState.clearAllAnimations()) {
            // Commit a pending flip so disabling animations mid-flight does not
            // silently leave the board in its pre-flip orientation.
            boardPanel.whiteDown = !boardPanel.whiteDown;
        }
        animationTimer.stop();
    }

    double moveAnimationProgress() {
        return boardPanel.animationState.moveAnimationProgress(System.currentTimeMillis());
    }

    double wrongMoveMarkerProgress() {
        return boardPanel.animationState.wrongMoveMarkerProgress(System.currentTimeMillis());
    }

    void clearWrongMoveMarkerState() {
        boardPanel.animationState.clearWrongMoveMarker();
    }

    private void flushPendingDragRepaint() {
        Rectangle dirty = pendingDragDirty;
        pendingDragDirty = null;
        dragRepaintTimer.stop();
        if (dirty != null) {
            lastDragRepaintNanos = System.nanoTime();
            boardPanel.repaint(dirty.x, dirty.y, dirty.width, dirty.height);
        }
    }

    private void tickAnimation() {
        if (boardPanel.animationState.moveAnimationActive() && moveAnimationProgress() >= 1.0) {
            clearMoveAnimation();
        }
        SnapAnimation finished = boardPanel.animationState.takeFinishedSnapAnimation();
        if (finished != null) {
            if (finished.snapback) {
                if (boardPanel.snapbackEndObserver != null) {
                    boardPanel.snapbackEndObserver.run();
                }
            } else if (boardPanel.snapEndObserver != null) {
                boardPanel.snapEndObserver.run();
            }
        }
        if (boardPanel.animationState.wrongMoveMarkerExpired(System.currentTimeMillis())) {
            clearWrongMoveMarkerState();
        }
        if (boardPanel.animationState.tickFlipAnimation(System.currentTimeMillis())) {
            boardPanel.whiteDown = !boardPanel.whiteDown;
        }
        if (!hasActiveAnimation()) {
            animationTimer.stop();
        }
        boardPanel.repaint();
    }

    private boolean hasActiveAnimation() {
        return boardPanel.animationState.hasActiveAnimation();
    }

    private static byte animatedPiece(byte[] oldBoard, byte[] newBoard, byte from, byte to) {
        byte source = oldBoard[from];
        byte target = newBoard[to];
        if (source == Piece.EMPTY || target == Piece.EMPTY || Piece.isWhite(source) != Piece.isWhite(target)) {
            return Piece.EMPTY;
        }
        return target;
    }

    private void configureAnimatedCapture(byte[] oldBoard, byte[] newBoard, byte from, byte to, byte movingPiece) {
        byte direct = oldBoard[to];
        if (direct != Piece.EMPTY && Piece.isWhite(direct) != Piece.isWhite(movingPiece)) {
            boardPanel.animationState.setAnimatedCapture(direct, to);
            return;
        }
        if (!Piece.isPawn(oldBoard[from]) || Field.getX(from) == Field.getX(to)) {
            return;
        }
        byte candidate = (byte) ((from / 8) * 8 + Field.getX(to));
        if (BoardPanel.isSquareIndex(candidate) && oldBoard[candidate] != Piece.EMPTY && newBoard[candidate] == Piece.EMPTY
                && Piece.isWhite(oldBoard[candidate]) != Piece.isWhite(movingPiece)) {
            boardPanel.animationState.setAnimatedCapture(oldBoard[candidate], candidate);
        }
    }

    private void configureSecondaryMove(byte[] oldBoard, byte[] newBoard, byte moveFrom, byte moveTo,
            boolean reverseMoveAnimation) {
        byte kingFrom = reverseMoveAnimation ? moveTo : moveFrom;
        byte movingPiece = oldBoard[kingFrom];
        if (!Piece.isKing(movingPiece)) {
            return;
        }
        int fileDelta = Field.getX(moveTo) - Field.getX(moveFrom);
        if (Math.abs(fileDelta) != 2) {
            return;
        }
        int rank = moveFrom / 8;
        byte forwardRookFrom = (byte) (rank * 8 + (fileDelta > 0 ? 7 : 0));
        byte forwardRookTo = (byte) (rank * 8 + (fileDelta > 0 ? 5 : 3));
        byte rookFrom = reverseMoveAnimation ? forwardRookTo : forwardRookFrom;
        byte rookTo = reverseMoveAnimation ? forwardRookFrom : forwardRookTo;
        if (!BoardPanel.isSquareIndex(rookFrom) || !BoardPanel.isSquareIndex(rookTo)) {
            return;
        }
        byte rookPiece = oldBoard[rookFrom];
        if (rookPiece == Piece.EMPTY || newBoard[rookTo] != rookPiece) {
            return;
        }
        boardPanel.animationState.setAnimatedSecondaryMove(rookPiece, rookFrom, rookTo);
    }

    private static Rectangle expanded(Rectangle bounds, int padding) {
        return new Rectangle(bounds.x - padding, bounds.y - padding,
                bounds.width + padding * 2, bounds.height + padding * 2);
    }

    private static Rectangle union(Rectangle first, Rectangle second) {
        if (first == null) {
            return second == null ? null : new Rectangle(second);
        }
        if (second == null) {
            return new Rectangle(first);
        }
        return first.union(second);
    }
}
