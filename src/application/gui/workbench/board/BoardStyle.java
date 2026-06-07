package application.gui.workbench.board;

import application.gui.workbench.ui.Theme;
import chess.core.Field;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;

/**
 * Shared chessboard.js-style board painting and square geometry.
 */
public final class BoardStyle {

    /**
     * Standard chessboard.js border thickness used by the main board.
     */
    public static final int BORDER_WIDTH = 2;

    /**
     * Suggested-move arrow opacity, matching chessboard-arrows' canvas
     * transparency. Shared by the live painter and the raster/SVG exporters so
     * the arrow looks identical on screen and on export.
     */
    public static final int BOARD_ARROW_OPACITY = 204;

    /**
     * Distance, as a fraction of one square, that board arrows are pulled inward
     * from each piece centre so they keep a clear gap instead of touching the
     * start and target pieces (a quarter square, matching the legacy renderer).
     * Shared by the live painter and the raster/SVG exporters.
     */
    public static final double ARROW_PIECE_GAP_FRACTION = 0.25;

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
        Color savedColor = g.getColor();
        Stroke savedStroke = g.getStroke();
        try {
            for (int row = 0; row < 8; row++) {
                for (int col = 0; col < 8; col++) {
                    Rectangle cell = cellBounds(board, row, col);
                    g.setColor(squareColor(row, col));
                    g.fillRect(cell.x, cell.y, cell.width, cell.height);
                }
            }
            if (drawBorder) {
                g.setStroke(new BasicStroke(1f));
                g.setColor(Theme.BOARD_EDGE);
                g.drawRect(board.x, board.y, board.width - 1, board.height - 1);
            }
        } finally {
            g.setStroke(savedStroke);
            g.setColor(savedColor);
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
     * Draws a filled board-square highlight.
     *
     * @param g graphics context
     * @param bounds square bounds
     * @param fill highlight fill color
     */
    public static void drawFilledSquareHighlight(Graphics2D g, Rectangle bounds, Color fill) {
        if (bounds.width <= 0 || bounds.height <= 0) {
            return;
        }
        Color savedColor = g.getColor();
        try {
            g.setColor(fill);
            g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        } finally {
            g.setColor(savedColor);
        }
    }

    /**
     * Draws a visible legal-move destination marker.
     *
     * @param g graphics context
     * @param bounds square bounds
     * @param capture true when the destination contains a capturable piece
     */
    public static void drawLegalTarget(Graphics2D g, Rectangle bounds, boolean capture) {
        Stroke savedStroke = g.getStroke();
        Color savedColor = g.getColor();
        try {
            int centerX = bounds.x + bounds.width / 2;
            int centerY = bounds.y + bounds.height / 2;
            int cell = Math.min(bounds.width, bounds.height);
            if (capture) {
                int diameter = Math.max(24, Math.round(cell * 0.86f));
                int strokeWidth = Math.max(2, Math.round(cell * 0.035f));
                g.setColor(Theme.LEGAL_CAPTURE_FILL);
                g.fillOval(centerX - diameter / 2, centerY - diameter / 2, diameter, diameter);
                g.setColor(Theme.LEGAL_CAPTURE_EDGE);
                g.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawOval(centerX - diameter / 2, centerY - diameter / 2, diameter, diameter);
            } else {
                int diameter = Math.max(10, Math.round(cell * 0.29f));
                int strokeWidth = Math.max(1, Math.round(cell * 0.014f));
                g.setColor(Theme.LEGAL_TARGET);
                g.fillOval(centerX - diameter / 2, centerY - diameter / 2, diameter, diameter);
                g.setColor(Theme.withAlpha(Theme.LEGAL_CAPTURE_EDGE, 70));
                g.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawOval(centerX - diameter / 2, centerY - diameter / 2, diameter, diameter);
            }
        } finally {
            g.setStroke(savedStroke);
            g.setColor(savedColor);
        }
    }

    /**
     * Draws a lichess/chessground-style premove destination marker.
     *
     * @param g graphics context
     * @param bounds square bounds
     * @param occupied true when a piece occupies the destination square
     */
    public static void drawPremoveTarget(Graphics2D g, Rectangle bounds, boolean occupied) {
        Color savedColor = g.getColor();
        try {
            int centerX = bounds.x + bounds.width / 2;
            int centerY = bounds.y + bounds.height / 2;
            int cell = Math.min(bounds.width, bounds.height);
            if (occupied) {
                int outerDiameter = Math.max(24, Math.round(cell * 0.86f));
                int innerDiameter = Math.max(12, Math.round(cell * 0.66f));
                Area ring = new Area(new Ellipse2D.Double(
                        centerX - outerDiameter / 2.0, centerY - outerDiameter / 2.0,
                        outerDiameter, outerDiameter));
                ring.subtract(new Area(new Ellipse2D.Double(
                        centerX - innerDiameter / 2.0, centerY - innerDiameter / 2.0,
                        innerDiameter, innerDiameter)));
                g.setColor(new Color(20, 30, 85, 70));
                g.fill(ring);
            } else {
                int diameter = Math.max(10, Math.round(cell * 0.29f));
                g.setColor(new Color(20, 30, 85, 128));
                g.fillOval(centerX - diameter / 2, centerY - diameter / 2, diameter, diameter);
                g.setColor(new Color(32, 48, 133, 160));
                g.drawOval(centerX - diameter / 2, centerY - diameter / 2, diameter, diameter);
            }
        } finally {
            g.setColor(savedColor);
        }
    }

    /**
     * Draws the lichess/chessground current-premove square fill.
     *
     * @param g graphics context
     * @param bounds square bounds
     */
    public static void drawCurrentPremoveSquare(Graphics2D g, Rectangle bounds) {
        drawFilledSquareHighlight(g, bounds, new Color(20, 30, 85, 128));
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
                    squareColor(7, i));
            drawCoordinateString(g, rankText,
                    rankCell.x + rankInlinePad,
                    rankCell.y + rankBlockPad + metrics.getAscent(),
                    squareColor(i, 0));
        }
    }

    /**
     * Draws one coordinate label directly on the square without a halo.
     *
     * @param g graphics context
     * @param text coordinate label text
     * @param x x coordinate
     * @param baseline text baseline
     * @param squareColor square color behind the label
     */
    private static void drawCoordinateString(Graphics2D g, String text, int x, int baseline, Color squareColor) {
        Color savedColor = g.getColor();
        try {
            g.setColor(coordinateTextColor(squareColor));
            g.drawString(text, x, baseline);
        } finally {
            g.setColor(savedColor);
        }
    }

    /**
     * Returns the coordinate label color paired to the board square colour.
     *
     * <p>Matching chessboard.js, labels on light squares use the dark-square
     * colour and labels on dark squares use the light-square colour.</p>
     *
     * @param squareColor square color behind the label
     * @return coordinate label color
     */
    private static Color coordinateTextColor(Color squareColor) {
        return Theme.BOARD_DARK.equals(squareColor) ? Theme.COORD_ON_DARK : Theme.COORD_ON_LIGHT;
    }
}
