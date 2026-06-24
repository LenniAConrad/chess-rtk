package application.gui.workbench.board;

import chess.core.Field;
import chess.core.Piece;
import java.awt.Cursor;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.function.BiConsumer;
import javax.swing.SwingUtilities;

/**
 * State and input controller for direct board setup editing.
 */
final class BoardSetupEditor {

    /**
     * Owning board panel.
     */
    private final BoardPanel boardPanel;

    /**
     * True while setup editing is active.
     */
    private boolean active;

    /**
     * Mutable board used by setup editing.
     */
    private final byte[] board = new byte[64];

    /**
     * Selected setup-edit piece and last edited square.
     */
    private byte selectedPiece = Piece.WHITE_KING;
    /**
     * Last square edited during a drag gesture, used to avoid duplicate writes.
     */
    private byte lastSquare = Field.NO_SQUARE;

    /**
     * Setup edit observer.
     */
    private BiConsumer<Byte, Byte> observer;

    /**
     * Creates a setup editor for a board panel.
     *
     * @param boardPanel owning board panel
     */
    BoardSetupEditor(BoardPanel boardPanel) {
        this.boardPanel = boardPanel;
    }

    /**
     * Enables or disables setup editing.
     *
     * @param enabled true when setup editing should intercept board input
     */
    void setMode(boolean enabled) {
        if (enabled == active) {
            return;
        }
        active = enabled;
        boardPanel.cancelPromotionOverlay();
        boardPanel.clearDragState();
        boardPanel.clearSelection();
        boardPanel.setCursor(Cursor.getDefaultCursor());
        boardPanel.repaint();
    }

    /**
     * Returns whether setup editing is active.
     *
     * @return true when setup editing is active
     */
    boolean active() {
        return active;
    }

    /**
     * Replaces the board shown by setup editing.
     *
     * @param nextBoard board piece array, or null to clear
     */
    void setBoard(byte[] nextBoard) {
        if (nextBoard == null) {
            Arrays.fill(board, Piece.EMPTY);
        } else if (nextBoard.length != board.length) {
            throw new IllegalArgumentException("Setup board must contain 64 squares");
        } else {
            System.arraycopy(nextBoard, 0, board, 0, board.length);
        }
        lastSquare = Field.NO_SQUARE;
        if (active) {
            boardPanel.repaint();
        }
    }

    /**
     * Sets the piece painted by a left click in setup editing mode.
     *
     * @param piece piece code, or {@link Piece#EMPTY} for erase
     */
    void setSelectedPiece(byte piece) {
        validatePiece(piece);
        selectedPiece = piece;
    }

    /**
     * Sets one setup-edit square and notifies the editor model.
     *
     * @param square board square index
     * @param piece piece code
     */
    void setPieceAt(byte square, byte piece) {
        setPieceAt(square, piece, true);
    }

    /**
     * Returns one setup-edit square.
     *
     * @param square board square index
     * @return piece code
     */
    byte pieceAt(byte square) {
        if (!BoardPanel.isSquareIndex(square)) {
            throw new IllegalArgumentException("Invalid square " + square);
        }
        return board[square];
    }

    /**
     * Sets the setup-edit observer.
     *
     * @param nextObserver observer, or null
     */
    void setObserver(BiConsumer<Byte, Byte> nextObserver) {
        observer = nextObserver;
    }

    /**
     * Handles a setup-mode mouse gesture.
     *
     * @param event mouse event
     */
    void handle(MouseEvent event) {
        boolean paintGesture = paintGesture(event);
        boolean eraseGesture = eraseGesture(event);
        if (!paintGesture && !eraseGesture) {
            return;
        }
        byte square = boardPanel.squareAt(event.getX(), event.getY());
        if (square == Field.NO_SQUARE) {
            return;
        }
        byte piece = eraseGesture ? Piece.EMPTY : selectedPiece;
        setPieceAt(square, piece, true);
        boardPanel.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        event.consume();
    }

    /**
     * Returns the mutable setup board for painting.
     *
     * @return setup board
     */
    byte[] board() {
        return board;
    }

    /**
     * Returns a detached setup board copy.
     *
     * @return setup board copy
     */
    byte[] boardCopy() {
        return board.clone();
    }

    /**
     * Returns the last edited square.
     *
     * @return last square or {@link Field#NO_SQUARE}
     */
    byte lastSquare() {
        return lastSquare;
    }

    /**
     * Sets one setup-edit square.
     *
     * @param square board square index
     * @param piece piece code
     * @param notify whether to notify the editor model
     */
    private void setPieceAt(byte square, byte piece, boolean notify) {
        if (!BoardPanel.isSquareIndex(square)) {
            throw new IllegalArgumentException("Invalid square " + square);
        }
        validatePiece(piece);
        if (board[square] == piece && lastSquare == square) {
            return;
        }
        board[square] = piece;
        lastSquare = square;
        if (notify && observer != null) {
            observer.accept(Byte.valueOf(square), Byte.valueOf(piece));
        }
        if (active) {
            boardPanel.repaint(boardPanel.squareBounds(boardPanel.boardBounds(), square));
        }
    }

    /**
     * Returns whether a setup-editor event should paint the selected piece.
     *
     * @param event mouse event
     * @return true for left-button setup painting
     */
    private static boolean paintGesture(MouseEvent event) {
        return SwingUtilities.isLeftMouseButton(event)
                || (event.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) != 0;
    }

    /**
     * Returns whether a setup-editor event should erase the target square.
     *
     * @param event mouse event
     * @return true for right-button setup erasing
     */
    private static boolean eraseGesture(MouseEvent event) {
        return SwingUtilities.isRightMouseButton(event)
                || (event.getModifiersEx() & MouseEvent.BUTTON3_DOWN_MASK) != 0;
    }

    /**
     * Validates a setup-edit piece code.
     *
     * @param piece piece code
     */
    private static void validatePiece(byte piece) {
        if (piece < Piece.BLACK_KING || piece > Piece.WHITE_KING) {
            throw new IllegalArgumentException("Invalid piece " + piece);
        }
    }
}
