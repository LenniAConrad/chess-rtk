package application.gui.workbench.board;

import chess.core.Field;
import java.awt.Point;
import java.awt.Rectangle;

/**
 * Pure board-coordinate helpers shared by painting and pointer handling.
 */
final class BoardGeometry {

    /**
     * Prevents instantiation.
     */
    private BoardGeometry() {
        // utility
    }

    /**
     * Returns square bounds in panel coordinates.
     *
     * @param board board bounds
     * @param square square index
     * @param whiteDown whether white is rendered at the bottom
     * @return square bounds
     */
    static Rectangle squareBounds(Rectangle board, byte square, boolean whiteDown) {
        return BoardStyle.fieldSquareBounds(board, square, whiteDown);
    }

    /**
     * Returns the board square at panel coordinates.
     *
     * @param board board bounds
     * @param x x coordinate
     * @param y y coordinate
     * @param whiteDown whether white is rendered at the bottom
     * @return square index, or {@link Field#NO_SQUARE}
     */
    static byte squareAt(Rectangle board, int x, int y, boolean whiteDown) {
        if (!board.contains(x, y)) {
            return Field.NO_SQUARE;
        }
        int col = (x - board.x) * 8 / Math.max(1, board.width);
        int row = (y - board.y) * 8 / Math.max(1, board.height);
        int file = whiteDown ? col : 7 - col;
        int rankRow = whiteDown ? row : 7 - row;
        return (byte) (rankRow * 8 + file);
    }

    /**
     * Returns square center in panel coordinates.
     *
     * @param board board bounds
     * @param square square index
     * @param whiteDown whether white is rendered at the bottom
     * @return square center
     */
    static Point center(Rectangle board, byte square, boolean whiteDown) {
        Rectangle bounds = squareBounds(board, square, whiteDown);
        return new Point(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
    }
}
