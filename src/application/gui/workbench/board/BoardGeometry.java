/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package application.gui.workbench.board;

import application.gui.workbench.ui.Theme;
import chess.core.Field;
import java.awt.Color;
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
     * Returns the square color for a visual board cell.
     *
     * @param row visual row
     * @param col visual column
     * @return board square color
     */
    static Color squareColor(int row, int col) {
        return ((row + col) & 1) == 0 ? Theme.BOARD_LIGHT : Theme.BOARD_DARK;
    }

    /**
     * Returns the coordinate label color for a real board square.
     *
     * @param file file index
     * @param rank rank number
     * @return coordinate label color
     */
    static Color notationColor(int file, int rank) {
        return ((file + rank) & 1) == 0 ? Theme.COORD_ON_LIGHT : Theme.COORD_ON_DARK;
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
        int cell = board.width / 8;
        int file = Field.getX(square);
        int rankRow = square / 8;
        int col = whiteDown ? file : 7 - file;
        int row = whiteDown ? rankRow : 7 - rankRow;
        return new Rectangle(board.x + col * cell, board.y + row * cell, cell, cell);
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
        int cell = board.width / 8;
        int col = (x - board.x) / cell;
        int row = (y - board.y) / cell;
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
