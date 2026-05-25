package application.gui.workbench.board;

import application.gui.workbench.ui.RenderAcceleration;
import application.gui.workbench.ui.Theme;
import chess.core.Field;
import chess.core.Move;
import chess.core.Piece;
import chess.images.assets.Shapes;
import chess.images.assets.shape.SvgShapes;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Render-based high quality exports for workbench chess boards.
 *
 * <p>The exporter draws from the board model and vector piece sources. It does
 * not capture the Swing component pixels, so exports are independent of the
 * current window size, scroll state, or monitor scaling.</p>
 *
 * @author Lennart A. Conrad
 */
public final class BoardExporter {

    /**
     * Default exported board-square size in pixels.
     */
    public static final int DEFAULT_BOARD_SIZE = 2048;

    /**
     * Minimum exported board-square size in pixels.
     */
    private static final int MIN_BOARD_SIZE = 256;

    /**
     * SVG view-box size used by embedded piece shapes.
     */
    private static final double PIECE_VIEWBOX_SIZE = 200.0;

    /**
     * Suggested-move export arrow opacity.
     */
    private static final int SUGGESTED_ARROW_ALPHA = 204;

    /**
     * Prevents instantiation.
     */
    private BoardExporter() {
        // utility
    }

    /**
     * Renders a board to an offscreen PNG image.
     *
     * @param board source board
     * @param requestedSize requested board-square size in pixels
     * @return rendered image
     */
    public static BufferedImage renderPng(BoardPanel board, int requestedSize) {
        BoardExportSnapshot snapshot = board.exportSnapshot();
        int size = normalizedBoardSize(requestedSize);
        int border = borderWidth(size);
        BufferedImage image = RenderAcceleration.translucentImage(size + border * 2, size + border * 2);
        Graphics2D g = image.createGraphics();
        try {
            useExportQuality(g);
            Rectangle bounds = new Rectangle(border, border, size, size);
            paintRaster(snapshot, g, bounds, border);
        } finally {
            g.dispose();
        }
        return image;
    }

    /**
     * Writes a board PNG export to disk.
     *
     * @param board source board
     * @param path destination file
     * @param requestedSize requested board-square size in pixels
     * @throws IOException when the file cannot be written
     */
    public static void writePng(BoardPanel board, Path path, int requestedSize) throws IOException {
        ensureParent(path);
        ImageIO.write(renderPng(board, requestedSize), "png", path.toFile());
    }

    /**
     * Builds an SVG export for a board.
     *
     * @param board source board
     * @param requestedSize requested board-square size in SVG units
     * @return SVG document text
     */
    public static String toSvg(BoardPanel board, int requestedSize) {
        BoardExportSnapshot snapshot = board.exportSnapshot();
        int size = normalizedBoardSize(requestedSize);
        int border = borderWidth(size);
        int total = size + border * 2;
        StringBuilder svg = new StringBuilder(48_000);
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"")
                .append(total)
                .append("\" height=\"")
                .append(total)
                .append("\" viewBox=\"0 0 ")
                .append(total)
                .append(' ')
                .append(total)
                .append("\" role=\"img\" aria-label=\"ChessRTK analysis board\">\n");
        appendBoardSvg(snapshot, svg, new Rectangle(border, border, size, size), border);
        svg.append("</svg>\n");
        return svg.toString();
    }

    /**
     * Writes a board SVG export to disk.
     *
     * @param board source board
     * @param path destination file
     * @param requestedSize requested board-square size in SVG units
     * @throws IOException when the file cannot be written
     */
    public static void writeSvg(BoardPanel board, Path path, int requestedSize) throws IOException {
        ensureParent(path);
        Files.writeString(path, toSvg(board, requestedSize), StandardCharsets.UTF_8);
    }

    /**
     * Paints a raster export.
     *
     * @param snapshot board state
     * @param g graphics context
     * @param board board rectangle
     * @param border border width
     */
    private static void paintRaster(BoardExportSnapshot snapshot, Graphics2D g, Rectangle board, int border) {
        g.setColor(Theme.BOARD_EDGE);
        g.fillRect(board.x - border, board.y - border, board.width + border * 2, board.height + border * 2);
        BoardStyle.drawBoardSurface(g, board, false);
        if (snapshot.showNotation()) {
            BoardStyle.drawInsideCoordinates(g, board, snapshot.whiteDown(), Math.max(12, board.width / 44));
        }
        paintRasterHighlights(snapshot, g, board);
        paintRasterPieces(snapshot, g, board);
        paintRasterAnnotations(snapshot, g, board);
    }

    /**
     * Paints square highlights and legal-move markers.
     *
     * @param snapshot board state
     * @param g graphics context
     * @param board board rectangle
     */
    private static void paintRasterHighlights(BoardExportSnapshot snapshot, Graphics2D g, Rectangle board) {
        snapshot.squareHighlights().forEach((square, color) ->
                BoardStyle.drawFilledSquareHighlight(g, squareBounds(board, square.byteValue(), snapshot.whiteDown()),
                        color));
        if (snapshot.showLastMoveHighlight() && snapshot.lastMove() != Move.NO_MOVE) {
            BoardStyle.drawFilledSquareHighlight(g,
                    squareBounds(board, Move.getFromIndex(snapshot.lastMove()), snapshot.whiteDown()),
                    Theme.LAST_MOVE_EDGE);
            BoardStyle.drawFilledSquareHighlight(g,
                    squareBounds(board, Move.getToIndex(snapshot.lastMove()), snapshot.whiteDown()),
                    Theme.LAST_MOVE_EDGE);
        }
        if (snapshot.selectedSquare() != Field.NO_SQUARE) {
            BoardStyle.drawFilledSquareHighlight(g,
                    squareBounds(board, snapshot.selectedSquare(), snapshot.whiteDown()), Theme.SELECTED_EDGE);
        }
        if (snapshot.showLegalMovePreview()) {
            for (byte target : snapshot.legalTargets()) {
                if (target != snapshot.selectedSquare()) {
                    BoardStyle.drawLegalTarget(g, squareBounds(board, target, snapshot.whiteDown()),
                            contains(snapshot.captureTargets(), target));
                }
            }
        }
        if (snapshot.checkedKingSquare() != Field.NO_SQUARE) {
            Rectangle checked = squareBounds(board, snapshot.checkedKingSquare(), snapshot.whiteDown());
            g.setColor(Theme.CHECK_FILL);
            g.fillRect(checked.x, checked.y, checked.width, checked.height);
            g.setColor(Theme.CHECK_EDGE);
            g.drawRect(checked.x, checked.y, checked.width - 1, checked.height - 1);
        }
    }

    /**
     * Paints vector pieces into the raster export.
     *
     * @param snapshot board state
     * @param g graphics context
     * @param board board rectangle
     */
    private static void paintRasterPieces(BoardExportSnapshot snapshot, Graphics2D g, Rectangle board) {
        int cell = board.width / 8;
        byte[] pieces = snapshot.pieces();
        for (byte square = 0; square < Math.min(64, pieces.length); square++) {
            byte piece = pieces[square];
            if (piece != Piece.EMPTY) {
                Rectangle bounds = squareBounds(board, square, snapshot.whiteDown());
                Shapes.drawPiece(piece, g, bounds.x, bounds.y, cell, cell);
            }
        }
    }

    /**
     * Paints suggested and user annotations into the raster export.
     *
     * @param snapshot board state
     * @param g graphics context
     * @param board board rectangle
     */
    private static void paintRasterAnnotations(BoardExportSnapshot snapshot, Graphics2D g, Rectangle board) {
        BoardArrowPainter arrows = new BoardArrowPainter();
        if (snapshot.showSuggestedMoveArrow() && snapshot.suggestedMove() != Move.NO_MOVE) {
            g.setColor(Theme.withAlpha(Theme.BOARD_ARROW, SUGGESTED_ARROW_ALPHA));
            arrows.draw(g,
                    center(board, Move.getFromIndex(snapshot.suggestedMove()), snapshot.whiteDown()),
                    center(board, Move.getToIndex(snapshot.suggestedMove()), snapshot.whiteDown()),
                    Math.max(8f, board.width / 80f),
                    Math.max(15.0, board.width / 64.0));
        }
        for (BoardMarkup markup : snapshot.boardMarkups()) {
            g.setColor(markup.brush().themedColor());
            if (markup.isCircle()) {
                paintRasterCircle(g, squareBounds(board, markup.from(), snapshot.whiteDown()), markup.brush());
            } else {
                arrows.draw(g,
                        center(board, markup.from(), snapshot.whiteDown()),
                        center(board, markup.to(), snapshot.whiteDown()),
                        Math.max(5f, board.width / 8f * markup.brush().lineWidth() / 64f),
                        Math.max(15.0, board.width / 64.0));
            }
        }
    }

    /**
     * Paints one circle markup.
     *
     * @param g graphics context
     * @param bounds square bounds
     * @param brush annotation brush
     */
    private static void paintRasterCircle(Graphics2D g, Rectangle bounds, MarkupBrush brush) {
        Stroke savedStroke = g.getStroke();
        try {
            float strokeWidth = Math.max(3f, bounds.width * brush.lineWidth() / 160f);
            g.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int inset = Math.round(strokeWidth / 2f);
            g.drawOval(bounds.x + inset, bounds.y + inset,
                    Math.max(1, bounds.width - inset * 2), Math.max(1, bounds.height - inset * 2));
        } finally {
            g.setStroke(savedStroke);
        }
    }

    /**
     * Appends the SVG export body.
     *
     * @param snapshot board state
     * @param svg destination builder
     * @param board board rectangle
     * @param border border width
     */
    private static void appendBoardSvg(BoardExportSnapshot snapshot, StringBuilder svg, Rectangle board, int border) {
        appendRect(svg, board.x - border, board.y - border, board.width + border * 2,
                board.height + border * 2, Theme.BOARD_EDGE);
        appendSquares(svg, board);
        if (snapshot.showNotation()) {
            appendCoordinates(svg, board, snapshot.whiteDown());
        }
        appendSvgHighlights(snapshot, svg, board);
        appendSvgPieces(snapshot, svg, board);
        appendSvgAnnotations(snapshot, svg, board);
    }

    /**
     * Appends the board square grid.
     *
     * @param svg destination builder
     * @param board board rectangle
     */
    private static void appendSquares(StringBuilder svg, Rectangle board) {
        svg.append("  <g shape-rendering=\"crispEdges\">\n");
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Rectangle cell = BoardStyle.cellBounds(board, row, col);
                appendRect(svg, cell.x, cell.y, cell.width, cell.height, BoardStyle.squareColor(row, col));
            }
        }
        svg.append("  </g>\n");
    }

    /**
     * Appends square highlights and legal-move markers.
     *
     * @param snapshot board state
     * @param svg destination builder
     * @param board board rectangle
     */
    private static void appendSvgHighlights(BoardExportSnapshot snapshot, StringBuilder svg, Rectangle board) {
        snapshot.squareHighlights().forEach((square, color) -> {
            Rectangle bounds = squareBounds(board, square.byteValue(), snapshot.whiteDown());
            appendRect(svg, bounds.x, bounds.y, bounds.width, bounds.height, color);
        });
        if (snapshot.showLastMoveHighlight() && snapshot.lastMove() != Move.NO_MOVE) {
            appendMoveSquare(svg, board, Move.getFromIndex(snapshot.lastMove()), snapshot.whiteDown(),
                    Theme.LAST_MOVE_EDGE);
            appendMoveSquare(svg, board, Move.getToIndex(snapshot.lastMove()), snapshot.whiteDown(),
                    Theme.LAST_MOVE_EDGE);
        }
        if (snapshot.selectedSquare() != Field.NO_SQUARE) {
            appendMoveSquare(svg, board, snapshot.selectedSquare(), snapshot.whiteDown(), Theme.SELECTED_EDGE);
        }
        if (snapshot.showLegalMovePreview()) {
            for (byte target : snapshot.legalTargets()) {
                if (target != snapshot.selectedSquare()) {
                    appendLegalTarget(svg, squareBounds(board, target, snapshot.whiteDown()),
                            contains(snapshot.captureTargets(), target));
                }
            }
        }
        if (snapshot.checkedKingSquare() != Field.NO_SQUARE) {
            Rectangle checked = squareBounds(board, snapshot.checkedKingSquare(), snapshot.whiteDown());
            appendRect(svg, checked.x, checked.y, checked.width, checked.height, Theme.CHECK_FILL);
            appendRectStroke(svg, checked.x, checked.y, checked.width, checked.height, Theme.CHECK_EDGE, 1.0);
        }
    }

    /**
     * Appends one move-highlight square.
     *
     * @param svg destination builder
     * @param board board rectangle
     * @param square board square
     * @param whiteDown orientation
     * @param color fill color
     */
    private static void appendMoveSquare(StringBuilder svg, Rectangle board, byte square,
            boolean whiteDown, Color color) {
        Rectangle bounds = squareBounds(board, square, whiteDown);
        appendRect(svg, bounds.x, bounds.y, bounds.width, bounds.height, color);
    }

    /**
     * Appends one legal-move marker.
     *
     * @param svg destination builder
     * @param bounds target square bounds
     * @param capture true for capture target
     */
    private static void appendLegalTarget(StringBuilder svg, Rectangle bounds, boolean capture) {
        double cx = bounds.getCenterX();
        double cy = bounds.getCenterY();
        int cell = Math.min(bounds.width, bounds.height);
        if (capture) {
            double radius = Math.max(12.0, cell * 0.43);
            svg.append("  <circle cx=\"").append(format(cx)).append("\" cy=\"").append(format(cy))
                    .append("\" r=\"").append(format(radius)).append("\" fill=\"")
                    .append(colorCss(Theme.LEGAL_CAPTURE_FILL)).append("\"");
            appendOpacity(svg, Theme.LEGAL_CAPTURE_FILL);
            svg.append(" stroke=\"").append(colorCss(Theme.LEGAL_CAPTURE_EDGE)).append("\"");
            appendStrokeOpacity(svg, Theme.LEGAL_CAPTURE_EDGE);
            svg.append(" stroke-width=\"").append(format(Math.max(2.0, cell * 0.035))).append("\"/>\n");
        } else {
            double radius = Math.max(5.0, cell * 0.145);
            svg.append("  <circle cx=\"").append(format(cx)).append("\" cy=\"").append(format(cy))
                    .append("\" r=\"").append(format(radius)).append("\" fill=\"")
                    .append(colorCss(Theme.LEGAL_TARGET)).append("\"");
            appendOpacity(svg, Theme.LEGAL_TARGET);
            svg.append("/>\n");
        }
    }

    /**
     * Appends vector pieces.
     *
     * @param snapshot board state
     * @param svg destination builder
     * @param board board rectangle
     */
    private static void appendSvgPieces(BoardExportSnapshot snapshot, StringBuilder svg, Rectangle board) {
        double cell = board.width / 8.0;
        byte[] pieces = snapshot.pieces();
        for (byte square = 0; square < Math.min(64, pieces.length); square++) {
            byte piece = pieces[square];
            if (piece == Piece.EMPTY) {
                continue;
            }
            Rectangle bounds = squareBounds(board, square, snapshot.whiteDown());
            String source = pieceSvg(piece);
            if (source.isBlank()) {
                continue;
            }
            svg.append("  <g transform=\"translate(")
                    .append(format(bounds.x))
                    .append(' ')
                    .append(format(bounds.y))
                    .append(") scale(")
                    .append(format(cell / PIECE_VIEWBOX_SIZE))
                    .append(")\">\n")
                    .append(innerSvg(source))
                    .append("  </g>\n");
        }
    }

    /**
     * Appends suggested and user annotations.
     *
     * @param snapshot board state
     * @param svg destination builder
     * @param board board rectangle
     */
    private static void appendSvgAnnotations(BoardExportSnapshot snapshot, StringBuilder svg, Rectangle board) {
        if (snapshot.showSuggestedMoveArrow() && snapshot.suggestedMove() != Move.NO_MOVE) {
            appendArrow(svg,
                    center(board, Move.getFromIndex(snapshot.suggestedMove()), snapshot.whiteDown()),
                    center(board, Move.getToIndex(snapshot.suggestedMove()), snapshot.whiteDown()),
                    Math.max(8.0, board.width / 80.0),
                    Math.max(15.0, board.width / 64.0),
                    Theme.withAlpha(Theme.BOARD_ARROW, SUGGESTED_ARROW_ALPHA));
        }
        for (BoardMarkup markup : snapshot.boardMarkups()) {
            Color color = markup.brush().themedColor();
            if (markup.isCircle()) {
                appendCircleMarkup(svg, squareBounds(board, markup.from(), snapshot.whiteDown()), markup.brush(), color);
            } else {
                appendArrow(svg,
                        center(board, markup.from(), snapshot.whiteDown()),
                        center(board, markup.to(), snapshot.whiteDown()),
                        Math.max(5.0, board.width / 8.0 * markup.brush().lineWidth() / 64.0),
                        Math.max(15.0, board.width / 64.0),
                        color);
            }
        }
    }

    /**
     * Appends one filled arrow polygon.
     *
     * @param svg destination builder
     * @param from origin point
     * @param to target point
     * @param lineWidth line width
     * @param shorten target shortening
     * @param color fill color
     */
    private static void appendArrow(StringBuilder svg, Point from, Point to,
            double lineWidth, double shorten, Color color) {
        double distance = from.distance(to);
        if (distance < 2.0) {
            return;
        }
        double angle = Math.atan2((double) to.y - from.y, (double) to.x - from.x);
        double targetShorten = Math.min(shorten, distance * 0.35);
        double headRadius = Math.min(Math.max(15.0, lineWidth * 2.6), Math.max(5.0, distance * 0.25));
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double px = -sin;
        double py = cos;
        double halfWidth = lineWidth / 2.0;
        double headX = to.x - cos * targetShorten;
        double headY = to.y - sin * targetShorten;
        double tipX = headX + cos * headRadius;
        double tipY = headY + sin * headRadius;
        double leftAngle = angle + 2.0 * Math.PI / 3.0;
        double rightAngle = angle - 2.0 * Math.PI / 3.0;
        double wingLeftX = headX + Math.cos(leftAngle) * headRadius;
        double wingLeftY = headY + Math.sin(leftAngle) * headRadius;
        double wingRightX = headX + Math.cos(rightAngle) * headRadius;
        double wingRightY = headY + Math.sin(rightAngle) * headRadius;
        double baseX = headX - 0.5 * headRadius * cos;
        double baseY = headY - 0.5 * headRadius * sin;
        svg.append("  <path d=\"M ").append(format(from.x + px * halfWidth)).append(' ')
                .append(format(from.y + py * halfWidth))
                .append(" L ").append(format(baseX + px * halfWidth)).append(' ')
                .append(format(baseY + py * halfWidth))
                .append(" L ").append(format(wingLeftX)).append(' ').append(format(wingLeftY))
                .append(" L ").append(format(tipX)).append(' ').append(format(tipY))
                .append(" L ").append(format(wingRightX)).append(' ').append(format(wingRightY))
                .append(" L ").append(format(baseX - px * halfWidth)).append(' ')
                .append(format(baseY - py * halfWidth))
                .append(" L ").append(format(from.x - px * halfWidth)).append(' ')
                .append(format(from.y - py * halfWidth))
                .append(" Z\" fill=\"").append(colorCss(color)).append("\"");
        appendOpacity(svg, color);
        svg.append("/>\n");
    }

    /**
     * Appends one circle annotation.
     *
     * @param svg destination builder
     * @param bounds square bounds
     * @param brush annotation brush
     * @param color stroke color
     */
    private static void appendCircleMarkup(StringBuilder svg, Rectangle bounds, MarkupBrush brush, Color color) {
        double strokeWidth = Math.max(3.0, bounds.width * brush.lineWidth() / 160.0);
        double radius = Math.max(4.0, (Math.min(bounds.width, bounds.height) - strokeWidth) / 2.0);
        svg.append("  <circle cx=\"").append(format(bounds.getCenterX())).append("\" cy=\"")
                .append(format(bounds.getCenterY())).append("\" r=\"").append(format(radius))
                .append("\" fill=\"none\" stroke=\"").append(colorCss(color)).append("\"");
        appendStrokeOpacity(svg, color);
        svg.append(" stroke-width=\"").append(format(strokeWidth)).append("\"/>\n");
    }

    /**
     * Appends board coordinates.
     *
     * @param svg destination builder
     * @param board board rectangle
     * @param whiteDown board orientation
     */
    private static void appendCoordinates(StringBuilder svg, Rectangle board, boolean whiteDown) {
        int fontSize = Math.max(12, board.width / 44);
        int fileInlinePad = Math.max(2, Math.round(fontSize / 4.5f));
        int fileBlockPad = Math.max(1, Math.round(fontSize / 14.0f));
        int rankInlinePad = Math.max(2, Math.round(fontSize / 7.0f));
        int rankBlockPad = Math.max(2, Math.round(fontSize / 7.0f));
        svg.append("  <g font-family=\"Inter, Segoe UI, sans-serif\" font-size=\"")
                .append(fontSize)
                .append("\" font-weight=\"500\">\n");
        for (int i = 0; i < 8; i++) {
            int file = whiteDown ? i : 7 - i;
            int rank = whiteDown ? 8 - i : i + 1;
            Rectangle fileCell = BoardStyle.cellBounds(board, 7, i);
            Rectangle rankCell = BoardStyle.cellBounds(board, i, 0);
            String fileText = String.valueOf((char) ('a' + file));
            String rankText = String.valueOf(rank);
            appendText(svg, fileText,
                    fileCell.x + fileCell.width - fileInlinePad - fontSize * 0.7,
                    fileCell.y + fileCell.height - fileBlockPad - fontSize * 0.22,
                    coordinateColor(7, i));
            appendText(svg, rankText,
                    rankCell.x + rankInlinePad,
                    rankCell.y + rankBlockPad + fontSize,
                    coordinateColor(i, 0));
        }
        svg.append("  </g>\n");
    }

    /**
     * Appends one SVG text node.
     *
     * @param svg destination builder
     * @param text node text
     * @param x x coordinate
     * @param y baseline coordinate
     * @param color fill color
     */
    private static void appendText(StringBuilder svg, String text, double x, double y, Color color) {
        svg.append("    <text x=\"").append(format(x)).append("\" y=\"").append(format(y))
                .append("\" fill=\"").append(colorCss(color)).append("\">")
                .append(escape(text)).append("</text>\n");
    }

    /**
     * Appends one filled rectangle.
     *
     * @param svg destination builder
     * @param x x coordinate
     * @param y y coordinate
     * @param width rectangle width
     * @param height rectangle height
     * @param color fill color
     */
    private static void appendRect(StringBuilder svg, double x, double y, double width, double height, Color color) {
        svg.append("  <rect x=\"").append(format(x)).append("\" y=\"").append(format(y))
                .append("\" width=\"").append(format(width)).append("\" height=\"").append(format(height))
                .append("\" fill=\"").append(colorCss(color)).append("\"");
        appendOpacity(svg, color);
        svg.append("/>\n");
    }

    /**
     * Appends one stroked rectangle.
     *
     * @param svg destination builder
     * @param x x coordinate
     * @param y y coordinate
     * @param width rectangle width
     * @param height rectangle height
     * @param color stroke color
     * @param strokeWidth stroke width
     */
    private static void appendRectStroke(StringBuilder svg, double x, double y, double width, double height,
            Color color, double strokeWidth) {
        svg.append("  <rect x=\"").append(format(x)).append("\" y=\"").append(format(y))
                .append("\" width=\"").append(format(width)).append("\" height=\"").append(format(height))
                .append("\" fill=\"none\" stroke=\"").append(colorCss(color)).append("\"");
        appendStrokeOpacity(svg, color);
        svg.append(" stroke-width=\"").append(format(strokeWidth)).append("\"/>\n");
    }

    /**
     * Appends fill opacity when needed.
     *
     * @param svg destination builder
     * @param color color with alpha
     */
    private static void appendOpacity(StringBuilder svg, Color color) {
        if (color.getAlpha() < 255) {
            svg.append(" fill-opacity=\"").append(format(color.getAlpha() / 255.0)).append('"');
        }
    }

    /**
     * Appends stroke opacity when needed.
     *
     * @param svg destination builder
     * @param color color with alpha
     */
    private static void appendStrokeOpacity(StringBuilder svg, Color color) {
        if (color.getAlpha() < 255) {
            svg.append(" stroke-opacity=\"").append(format(color.getAlpha() / 255.0)).append('"');
        }
    }

    /**
     * Applies export-quality rendering hints.
     *
     * @param g graphics context
     */
    private static void useExportQuality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    /**
     * Returns square bounds.
     *
     * @param board board rectangle
     * @param square square index
     * @param whiteDown orientation
     * @return square bounds
     */
    private static Rectangle squareBounds(Rectangle board, byte square, boolean whiteDown) {
        return BoardGeometry.squareBounds(board, square, whiteDown);
    }

    /**
     * Returns square center.
     *
     * @param board board rectangle
     * @param square square index
     * @param whiteDown orientation
     * @return square center
     */
    private static Point center(Rectangle board, byte square, boolean whiteDown) {
        return BoardGeometry.center(board, square, whiteDown);
    }

    /**
     * Returns whether a square is present in an array.
     *
     * @param values square values
     * @param target target square
     * @return true when found
     */
    private static boolean contains(byte[] values, byte target) {
        for (byte value : values) {
            if (value == target) {
                return true;
            }
        }
        return false;
    }

    /**
     * Normalizes an export board size.
     *
     * @param requested requested size
     * @return positive multiple-of-eight size
     */
    private static int normalizedBoardSize(int requested) {
        int size = Math.max(MIN_BOARD_SIZE, requested);
        return size - size % 8;
    }

    /**
     * Returns a proportional export border width.
     *
     * @param boardSize board size
     * @return border width
     */
    private static int borderWidth(int boardSize) {
        return Math.max(BoardStyle.BORDER_WIDTH, Math.round(boardSize / 384.0f));
    }

    /**
     * Returns a coordinate label color for a square.
     *
     * @param row visual row
     * @param col visual column
     * @return coordinate text color
     */
    private static Color coordinateColor(int row, int col) {
        return ((row + col) & 1) == 0 ? Theme.BOARD_DARK : Theme.BOARD_LIGHT;
    }

    /**
     * Converts a color to a CSS hex string.
     *
     * @param color color value
     * @return CSS hex color
     */
    private static String colorCss(Color color) {
        return String.format(Locale.ROOT, "#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    /**
     * Formats a SVG numeric value.
     *
     * @param value numeric value
     * @return compact SVG number
     */
    private static String format(double value) {
        String text = String.format(Locale.ROOT, "%.3f", value);
        return text.replaceAll("\\.?0+$", "");
    }

    /**
     * Escapes XML text content.
     *
     * @param text raw text
     * @return escaped text
     */
    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Returns the embedded SVG source for one piece.
     *
     * @param piece piece code
     * @return SVG source, or empty text
     */
    private static String pieceSvg(byte piece) {
        return switch (piece) {
            case Piece.BLACK_BISHOP -> SvgShapes.blackBishop();
            case Piece.BLACK_KING -> SvgShapes.blackKing();
            case Piece.BLACK_KNIGHT -> SvgShapes.blackKnight();
            case Piece.BLACK_PAWN -> SvgShapes.blackPawn();
            case Piece.BLACK_QUEEN -> SvgShapes.blackQueen();
            case Piece.BLACK_ROOK -> SvgShapes.blackRook();
            case Piece.WHITE_BISHOP -> SvgShapes.whiteBishop();
            case Piece.WHITE_KING -> SvgShapes.whiteKing();
            case Piece.WHITE_KNIGHT -> SvgShapes.whiteKnight();
            case Piece.WHITE_PAWN -> SvgShapes.whitePawn();
            case Piece.WHITE_QUEEN -> SvgShapes.whiteQueen();
            case Piece.WHITE_ROOK -> SvgShapes.whiteRook();
            default -> "";
        };
    }

    /**
     * Extracts nested SVG content from a full SVG document.
     *
     * @param source SVG source
     * @return inner SVG content
     */
    private static String innerSvg(String source) {
        int start = source.indexOf('>');
        int end = source.lastIndexOf("</svg>");
        if (start < 0 || end <= start) {
            return source;
        }
        return source.substring(start + 1, end);
    }

    /**
     * Creates the parent directory for an output file.
     *
     * @param path output file path
     * @throws IOException when directories cannot be created
     */
    private static void ensureParent(Path path) throws IOException {
        Path parent = path == null ? null : path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }
}
