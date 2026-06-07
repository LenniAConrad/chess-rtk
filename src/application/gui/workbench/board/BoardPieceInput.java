package application.gui.workbench.board;

import chess.core.Field;
import chess.core.Move;
import chess.core.Piece;
import java.util.Arrays;

/**
 * Owns piece-selection and drag/drop input state for the board surface.
 */
final class BoardPieceInput {

    /**
     * Selected source square.
     */
    private byte selectedSquare = Field.NO_SQUARE;

    /**
     * Legal move cache for the selected source square.
     */
    private short[] selectedLegalMoves = new short[0];

    /**
     * Legal target-square cache for the selected source square.
     */
    private byte[] selectedLegalTargets = new byte[0];

    /**
     * Drag source, target, hover square, and piece state.
     */
    private byte dragSquare = Field.NO_SQUARE, dragTargetSquare = Field.NO_SQUARE,
            dragHoverSquare = Field.NO_SQUARE, draggedPiece = Piece.EMPTY;

    /**
     * Drag anchor and live pointer coordinates.
     */
    private int dragStartX, dragStartY, dragX, dragY;

    /**
     * True while a piece drag is active.
     */
    private boolean draggingPiece;

    /**
     * Returns the selected source square.
     *
     * @return selected square, or {@link Field#NO_SQUARE}
     */
    byte selectedSquare() {
        return selectedSquare;
    }

    /**
     * Returns whether a source square is selected.
     *
     * @return true when a square is selected
     */
    boolean hasSelection() {
        return selectedSquare != Field.NO_SQUARE;
    }

    /**
     * Returns whether the supplied square is the selected source square.
     *
     * @param square square to compare
     * @return true when the square is selected
     */
    boolean isSelected(byte square) {
        return selectedSquare == square;
    }

    /**
     * Returns legal moves for the selected source square.
     *
     * @return legal move cache
     */
    short[] selectedLegalMoves() {
        return selectedLegalMoves;
    }

    /**
     * Returns legal target squares for the selected source square.
     *
     * @return legal target cache
     */
    byte[] selectedLegalTargets() {
        return selectedLegalTargets;
    }

    /**
     * Replaces the selected source and its legal move caches.
     *
     * @param square selected source square
     * @param legalMoves legal moves from the source
     * @param legalTargets unique legal target squares from the source
     */
    void select(byte square, short[] legalMoves, byte[] legalTargets) {
        selectedSquare = square;
        selectedLegalMoves = legalMoves == null ? new short[0] : legalMoves;
        selectedLegalTargets = legalTargets == null ? new byte[0] : legalTargets;
    }

    /**
     * Clears the selected source square and legal move caches.
     */
    void clearSelection() {
        selectedSquare = Field.NO_SQUARE;
        selectedLegalMoves = new short[0];
        selectedLegalTargets = new byte[0];
    }

    /**
     * Returns legal selected moves whose target is the supplied square.
     *
     * @param to target square
     * @return matching moves
     */
    short[] selectedMovesTo(byte to) {
        return matchingMovesTo(selectedLegalMoves, to);
    }

    /**
     * Returns the drag source square.
     *
     * @return drag source, or {@link Field#NO_SQUARE}
     */
    byte dragSquare() {
        return dragSquare;
    }

    /**
     * Returns the current legal drop target square.
     *
     * @return target square, or {@link Field#NO_SQUARE}
     */
    byte dragTargetSquare() {
        return dragTargetSquare;
    }

    /**
     * Returns the current hovered square during drag.
     *
     * @return hover square, or {@link Field#NO_SQUARE}
     */
    byte dragHoverSquare() {
        return dragHoverSquare;
    }

    /**
     * Returns the dragged piece.
     *
     * @return dragged piece code
     */
    byte draggedPiece() {
        return draggedPiece;
    }

    /**
     * Returns the current drag pointer x-coordinate.
     *
     * @return pointer x-coordinate
     */
    int dragX() {
        return dragX;
    }

    /**
     * Returns the current drag pointer y-coordinate.
     *
     * @return pointer y-coordinate
     */
    int dragY() {
        return dragY;
    }

    /**
     * Returns whether a draggable piece has been armed.
     *
     * @return true when a drag source and piece are present
     */
    boolean hasDragSource() {
        return dragSquare != Field.NO_SQUARE && draggedPiece != Piece.EMPTY;
    }

    /**
     * Returns whether the armed piece is currently being dragged.
     *
     * @return true while dragging
     */
    boolean isDragging() {
        return draggingPiece;
    }

    /**
     * Starts a potential piece drag from the supplied square.
     *
     * @param square source square
     * @param piece piece code
     * @param pointerX press x-coordinate
     * @param pointerY press y-coordinate
     */
    void startDrag(byte square, byte piece, int pointerX, int pointerY) {
        dragSquare = square;
        draggedPiece = piece;
        dragStartX = pointerX;
        dragStartY = pointerY;
        dragX = pointerX;
        dragY = pointerY;
        dragTargetSquare = Field.NO_SQUARE;
        dragHoverSquare = Field.NO_SQUARE;
        draggingPiece = false;
    }

    /**
     * Updates the live drag pointer position.
     *
     * @param pointerX pointer x-coordinate
     * @param pointerY pointer y-coordinate
     */
    void updatePointer(int pointerX, int pointerY) {
        dragX = pointerX;
        dragY = pointerY;
    }

    /**
     * Returns whether the drag pointer has moved far enough to count as a drag.
     *
     * @param threshold pixel threshold
     * @return true when the squared pointer distance reaches the threshold
     */
    boolean isPastDragThreshold(int threshold) {
        long dx = (long) dragX - dragStartX;
        long dy = (long) dragY - dragStartY;
        return dx * dx + dy * dy >= (long) threshold * threshold;
    }

    /**
     * Sets whether the armed piece is now actively dragged.
     *
     * @param dragging true while dragging
     */
    void setDragging(boolean dragging) {
        draggingPiece = dragging;
    }

    /**
     * Updates the hover and legal target squares for the active drag.
     *
     * @param hoverSquare hovered square
     * @param targetSquare legal target square
     */
    void setDragHoverTarget(byte hoverSquare, byte targetSquare) {
        dragHoverSquare = hoverSquare;
        dragTargetSquare = targetSquare;
    }

    /**
     * Clears the active drag hover and legal target squares.
     */
    void clearDragHoverTarget() {
        dragTargetSquare = Field.NO_SQUARE;
        dragHoverSquare = Field.NO_SQUARE;
    }

    /**
     * Clears all drag state without changing the selected source square.
     */
    void clearDrag() {
        dragSquare = Field.NO_SQUARE;
        dragTargetSquare = Field.NO_SQUARE;
        dragHoverSquare = Field.NO_SQUARE;
        draggedPiece = Piece.EMPTY;
        draggingPiece = false;
    }

    /**
     * Returns moves whose target square matches the supplied square.
     *
     * @param moves move array
     * @param to target square
     * @return matching moves
     */
    static short[] matchingMovesTo(short[] moves, byte to) {
        int count = 0;
        short[] buffer = new short[moves.length];
        for (short move : moves) {
            if (Move.getToIndex(move) == to) {
                buffer[count++] = move;
            }
        }
        return Arrays.copyOf(buffer, count);
    }
}
