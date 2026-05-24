package application.gui.workbench.board;

import application.gui.workbench.ui.Theme;
import chess.core.Field;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;

/**
 * Shared chessboard.js-style board painting and square geometry.
 */
public final class BoardStyle {

    /**
     * Standard chessboard.js border thickness used by the main board.
     */
    public static final int BORDER_WIDTH = 2;

    /**
     * Prevents instantiation.
     */
    private BoardStyle() {
        // utility
    }

    /**
     * Returns the square color for a visual board cell.
     *
     * @param row visual row
     * @param col visual column
     * @return board square color
     */
    public static Color squareColor(int row, int col) {
        return ((row + col) & 1) == 0 ? Theme.BOARD_LIGHT : Theme.BOARD_DARK;
    }

    /**
     * Returns the pixel-perfect bounds for one visual board cell.
     *
     * @param board board rectangle
     * @param row visual row
     * @param col visual column
     * @return cell bounds
     */
    public static Rectangle cellBounds(Rectangle board, int row, int col) {
        int safeRow = Math.max(0, Math.min(7, row));
        int safeCol = Math.max(0, Math.min(7, col));
        int x0 = board.x + safeCol * board.width / 8;
        int x1 = board.x + (safeCol + 1) * board.width / 8;
        int y0 = board.y + safeRow * board.height / 8;
        int y1 = board.y + (safeRow + 1) * board.height / 8;
        return new Rectangle(x0, y0, Math.max(0, x1 - x0), Math.max(0, y1 - y0));
    }

    /**
     * Returns bounds for a CRTK core square where {@code 0 == a8}.
     *
     * @param board board rectangle
     * @param square core square index
     * @param whiteDown whether white is rendered at the bottom
     * @return cell bounds
     */
    public static Rectangle fieldSquareBounds(Rectangle board, byte square, boolean whiteDown) {
        int file = Field.getX(square);
        int rankRow = square / 8;
        int col = whiteDown ? file : 7 - file;
        int row = whiteDown ? rankRow : 7 - rankRow;
        return cellBounds(board, row, col);
    }

    /**
     * Returns bounds for a LERF square where {@code 0 == a1}.
     *
     * @param board board rectangle
     * @param square LERF square index
     * @param whiteDown whether white is rendered at the bottom
     * @return cell bounds
     */
    public static Rectangle lerfSquareBounds(Rectangle board, int square, boolean whiteDown) {
        int safeSquare = Math.max(0, Math.min(63, square));
        int file = safeSquare & 7;
        int rank = safeSquare >> 3;
        int col = whiteDown ? file : 7 - file;
        int row = whiteDown ? 7 - rank : rank;
        return cellBounds(board, row, col);
    }

    /**
     * Paints the shared tan chessboard surface.
     *
     * @param g graphics context
     * @param board board rectangle
     * @param drawBorder true to draw the board edge inside the rectangle
     */
    public static void drawBoardSurface(Graphics2D g, Rectangle board, boolean drawBorder) {
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Rectangle cell = cellBounds(board, row, col);
                g.setColor(squareColor(row, col));
                g.fillRect(cell.x, cell.y, cell.width, cell.height);
            }
        }
        if (drawBorder) {
            g.setColor(Theme.BOARD_EDGE);
            g.drawRect(board.x, board.y, board.width - 1, board.height - 1);
        }
    }

    /**
     * Draws a chessboard.js-style inset square highlight.
     *
     * @param g graphics context
     * @param bounds square bounds
     * @param edge highlight edge color
     */
    public static void drawInsetSquareHighlight(Graphics2D g, Rectangle bounds, Color edge) {
        if (bounds.width <= 2 || bounds.height <= 2) {
            return;
        }
        Color savedColor = g.getColor();
        try {
            int cell = Math.min(bounds.width, bounds.height);
            int rings = Math.max(1, Math.min(3, Math.round(cell / 24.0f)));
            g.setColor(edge);
            for (int inset = 1; inset <= rings; inset++) {
                int x = bounds.x + inset;
                int y = bounds.y + inset;
                int w = bounds.width - inset * 2;
                int h = bounds.height - inset * 2;
                if (w <= 1 || h <= 1) {
                    continue;
                }
                g.fillRect(x, y, w, 1);
                g.fillRect(x, y + h - 1, w, 1);
                g.fillRect(x, y, 1, h);
                g.fillRect(x + w - 1, y, 1, h);
            }
        } finally {
            g.setColor(savedColor);
        }
    }

    /**
     * Draws chessboard.js-style coordinates inside the board squares.
     *
     * @param g graphics context
     * @param board board rectangle
     * @param whiteDown whether white is rendered at the bottom
     * @param fontSize coordinate font size
     */
    public static void drawInsideCoordinates(Graphics2D g, Rectangle board, boolean whiteDown, int fontSize) {
        int size = Math.max(7, fontSize);
        g.setFont(Theme.font(size, java.awt.Font.PLAIN));
        FontMetrics metrics = g.getFontMetrics();
        int fileInlinePad = Math.max(2, Math.round(size / 4.5f));
        int fileBlockPad = Math.max(1, Math.round(size / 14.0f));
        int rankInlinePad = Math.max(2, Math.round(size / 7.0f));
        int rankBlockPad = Math.max(2, Math.round(size / 7.0f));
        int bottomRank = whiteDown ? 1 : 8;
        int leftFile = whiteDown ? 0 : 7;
        for (int i = 0; i < 8; i++) {
            int file = whiteDown ? i : 7 - i;
            int rank = whiteDown ? 8 - i : i + 1;
            Rectangle fileCell = cellBounds(board, 7, i);
            Rectangle rankCell = cellBounds(board, i, 0);
            String fileText = String.valueOf((char) ('a' + file));
            String rankText = String.valueOf(rank);
            drawCoordinateString(g, fileText,
                    fileCell.x + fileCell.width - metrics.stringWidth(fileText) - fileInlinePad,
                    fileCell.y + fileCell.height - fileBlockPad - metrics.getDescent(),
                    notationColor(file, bottomRank));
            drawCoordinateString(g, rankText,
                    rankCell.x + rankInlinePad,
                    rankCell.y + rankBlockPad + metrics.getAscent(),
                    notationColor(leftFile, rank));
        }
    }

    /**
     * Draws one coordinate label with a tiny high-contrast halo.
     *
     * @param g graphics context
     * @param text coordinate label text
     * @param x x coordinate
     * @param baseline text baseline
     * @param color primary coordinate color
     */
    private static void drawCoordinateString(Graphics2D g, String text, int x, int baseline, Color color) {
        Color savedColor = g.getColor();
        try {
            g.setColor(coordinateHalo(color));
            g.drawString(text, x - 1, baseline);
            g.drawString(text, x + 1, baseline);
            g.drawString(text, x, baseline - 1);
            g.drawString(text, x, baseline + 1);
            g.setColor(color);
            g.drawString(text, x, baseline);
        } finally {
            g.setColor(savedColor);
        }
    }

    /**
     * Returns the coordinate label color for a real board square.
     *
     * @param file file index
     * @param rank rank number
     * @return coordinate label color
     */
    private static Color notationColor(int file, int rank) {
        return ((file + rank) & 1) == 0 ? Theme.COORD_ON_LIGHT : Theme.COORD_ON_DARK;
    }

    /**
     * Returns a high-contrast halo for one coordinate label color.
     *
     * @param color primary coordinate color
     * @return halo color
     */
    private static Color coordinateHalo(Color color) {
        int luminance = color.getRed() + color.getGreen() + color.getBlue();
        return luminance > 500 ? Theme.BOARD_EDGE : Theme.BOARD_LIGHT;
    }
}
