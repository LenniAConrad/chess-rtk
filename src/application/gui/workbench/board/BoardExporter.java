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
import java.awt.Font;
import java.awt.FontMetrics;
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
import java.util.List;
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
    private static final int SUGGESTED_ARROW_ALPHA = BoardStyle.BOARD_ARROW_OPACITY;

    /**
     * Horizontal glyph-circle center within the target square. Anchored near the
     * top-right corner (Lichess-style) so the badge straddles the corner.
     */
    private static final double GLYPH_CENTER_X_FRACTION = 0.90;

    /**
     * Vertical glyph-circle center within the target square.
     */
    private static final double GLYPH_CENTER_Y_FRACTION = 0.10;

    /**
     * Glyph-circle diameter relative to the target square (Lichess uses ~0.40).
     */
    private static final double GLYPH_DIAMETER_FRACTION = 0.48;

    /**
     * Horizontal step between stacked glyph badges, relative to badge diameter.
     */
    private static final double GLYPH_STACK_STEP_FRACTION = 0.56;

    /**
     * Left-most center used when several glyph badges share one square.
     */
    private static final double GLYPH_STACK_MIN_CENTER_X_FRACTION = 0.25;

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
        if (snapshot.glyphShadow() && hasGlyphMarkup(snapshot)) {
            appendGlyphShadowFilter(svg, size);
        }
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
        BoardStyle.drawBoardSurface(g, board, false, snapshot.boardLightColor(), snapshot.boardDarkColor());
        if (snapshot.showNotation()) {
            BoardStyle.drawInsideCoordinates(g, board, snapshot.whiteDown(), Math.max(12, board.width / 44),
                    snapshot.boardLightColor(), snapshot.boardDarkColor());
        }
        paintRasterHighlights(snapshot, g, board);
        paintRasterBackgroundAnnotations(snapshot, g, board);
        paintRasterPieces(snapshot, g, board);
        paintRasterForegroundAnnotations(snapshot, g, board);
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
                BoardStyle.drawFilledSquareHighlight(g, BoardGeometry.squareBounds(board, square.byteValue(), snapshot.whiteDown()),
                        color));
        if (snapshot.showLastMoveHighlight() && snapshot.lastMove() != Move.NO_MOVE) {
            BoardStyle.drawFilledSquareHighlight(g,
                    BoardGeometry.squareBounds(board, Move.getFromIndex(snapshot.lastMove()), snapshot.whiteDown()),
                    Theme.LAST_MOVE_EDGE);
            BoardStyle.drawFilledSquareHighlight(g,
                    BoardGeometry.squareBounds(board, Move.getToIndex(snapshot.lastMove()), snapshot.whiteDown()),
                    Theme.LAST_MOVE_EDGE);
        }
        if (snapshot.selectedSquare() != Field.NO_SQUARE) {
            BoardStyle.drawFilledSquareHighlight(g,
                    BoardGeometry.squareBounds(board, snapshot.selectedSquare(), snapshot.whiteDown()), Theme.SELECTED_EDGE);
        }
        if (snapshot.showLegalMovePreview()) {
            for (byte target : snapshot.legalTargets()) {
                if (target != snapshot.selectedSquare()) {
                    BoardStyle.drawLegalTarget(g, BoardGeometry.squareBounds(board, target, snapshot.whiteDown()),
                            contains(snapshot.captureTargets(), target));
                }
            }
        }
        if (snapshot.checkedKingSquare() != Field.NO_SQUARE) {
            Rectangle checked = BoardGeometry.squareBounds(board, snapshot.checkedKingSquare(), snapshot.whiteDown());
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
                Rectangle bounds = BoardGeometry.squareBounds(board, square, snapshot.whiteDown());
                Shapes.drawPiece(piece, g, bounds.x, bounds.y, cell, cell);
            }
        }
    }

    /**
     * Paints under-piece user annotations into the raster export.
     *
     * @param snapshot board state
     * @param g graphics context
     * @param board board rectangle
     */
    private static void paintRasterBackgroundAnnotations(BoardExportSnapshot snapshot, Graphics2D g, Rectangle board) {
        BoardArrowPainter arrows = new BoardArrowPainter();
        for (BoardMarkup markup : snapshot.boardMarkups()) {
            if (markup.isRectangle()) {
                paintRasterMarkup(snapshot, g, board, arrows, markup, 0, 1);
            }
        }
    }

    /**
     * Paints suggested and foreground user annotations into the raster export.
     *
     * @param snapshot board state
     * @param g graphics context
     * @param board board rectangle
     */
    private static void paintRasterForegroundAnnotations(BoardExportSnapshot snapshot, Graphics2D g, Rectangle board) {
        BoardArrowPainter arrows = new BoardArrowPainter();
        if (snapshot.showSuggestedMoveArrow() && snapshot.suggestedMove() != Move.NO_MOVE) {
            arrows.drawSuggested(g,
                    BoardGeometry.center(board, Move.getFromIndex(snapshot.suggestedMove()), snapshot.whiteDown()),
                    BoardGeometry.center(board, Move.getToIndex(snapshot.suggestedMove()), snapshot.whiteDown()),
                    Math.max(BoardStyle.SUGGESTED_ARROW_LINE_WIDTH, board.width / 64f),
                    board.width / 8.0 * BoardStyle.ARROW_PIECE_GAP_FRACTION);
        }
        if (snapshot.showSpecialMoveHints()) {
            paintRasterSpecialMoveHints(snapshot, g, board, arrows);
        }
        int[] glyphCounts = glyphCounts(snapshot.boardMarkups());
        int[] glyphSlots = new int[64];
        for (BoardMarkup markup : snapshot.boardMarkups()) {
            if (!markup.isRectangle()) {
                int slot = glyphSlot(markup, glyphSlots);
                int count = glyphCount(markup, glyphCounts);
                paintRasterMarkup(snapshot, g, board, arrows, markup, slot, count);
            }
        }
    }

    /**
     * Paints special-move hint arrows.
     *
     * @param snapshot board state
     * @param g graphics context
     * @param board board rectangle
     * @param arrows arrow painter
     */
    private static void paintRasterSpecialMoveHints(BoardExportSnapshot snapshot, Graphics2D g, Rectangle board,
            BoardArrowPainter arrows) {
        int cell = Math.max(1, board.width / 8);
        float lineWidth = Math.max(5f, board.width / 80f);
        double gap = cell * BoardStyle.ARROW_PIECE_GAP_FRACTION;
        Color savedColor = g.getColor();
        try {
            g.setColor(BoardStyle.SPECIAL_MOVE_HINT_FILL);
            for (Short arrow : BoardSpecialMoveHints.arrows(snapshot.position())) {
                short move = arrow.shortValue();
                arrows.draw(g,
                        BoardGeometry.center(board, Move.getFromIndex(move), snapshot.whiteDown()),
                        BoardGeometry.center(board, Move.getToIndex(move), snapshot.whiteDown()),
                        lineWidth,
                        gap,
                        BoardStyle.SPECIAL_MOVE_HINT_BORDER);
            }
        } finally {
            g.setColor(savedColor);
        }
    }

    /**
     * Paints one user markup annotation.
     *
     * @param snapshot board state
     * @param g graphics context
     * @param board board rectangle
     * @param arrows arrow painter
     * @param markup board markup
     * @param glyphSlot glyph badge slot within a same-square stack
     * @param glyphCount number of glyph badges in the same-square stack
     */
    private static void paintRasterMarkup(BoardExportSnapshot snapshot, Graphics2D g, Rectangle board,
            BoardArrowPainter arrows, BoardMarkup markup, int glyphSlot, int glyphCount) {
        if (markup.isCircle()) {
            paintRasterCircle(g, BoardGeometry.squareBounds(board, markup.from(), snapshot.whiteDown()), markup.brush());
        } else if (markup.isRectangle()) {
            paintRasterRectangle(g, rectangleBounds(board, markup, snapshot.whiteDown()), markup.brush(), board.width / 8);
        } else if (markup.isGlyph()) {
            paintRasterGlyph(g, BoardGeometry.squareBounds(board, markup.from(), snapshot.whiteDown()), board,
                    markup.brush(), glyphSlot, glyphCount, snapshot.glyphShadow());
        } else if (markup.isArrow()) {
            g.setColor(markup.brush().displayColor());
            arrows.draw(g,
                    BoardGeometry.center(board, markup.from(), snapshot.whiteDown()),
                    BoardGeometry.center(board, markup.to(), snapshot.whiteDown()),
                    Math.max(5f, board.width / 8f * markup.brush().lineWidth() / 64f),
                    board.width / 8.0 * BoardStyle.ARROW_PIECE_GAP_FRACTION,
                    markup.brush().displayBorderColor(),
                    (float) arrowBorderWidth(Math.max(5f, board.width / 8f * markup.brush().lineWidth() / 64f),
                            markup.brush()));
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
            float strokeWidth = annotationBorderWidth(bounds.width, brush);
            Color fill = brush.displayColor();
            g.setColor(Theme.withAlpha(fill, Math.round(fill.getAlpha() * 0.28f)));
            int inset = Math.round(strokeWidth / 2f);
            g.fillOval(bounds.x + inset, bounds.y + inset,
                    Math.max(1, bounds.width - inset * 2), Math.max(1, bounds.height - inset * 2));
            Color border = brush.displayBorderColor();
            if (strokeWidth > 0f && border.getAlpha() > 0) {
                g.setColor(border);
                g.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawOval(bounds.x + inset, bounds.y + inset,
                        Math.max(1, bounds.width - inset * 2), Math.max(1, bounds.height - inset * 2));
            }
        } finally {
            g.setStroke(savedStroke);
        }
    }

    /**
     * Paints one filled rectangle markup.
     *
     * @param g graphics context
     * @param bounds rectangle bounds
     * @param brush annotation brush
     * @param cell board cell size
     */
    private static void paintRasterRectangle(Graphics2D g, Rectangle bounds, MarkupBrush brush, int cell) {
        Stroke savedStroke = g.getStroke();
        try {
            float strokeWidth = annotationBorderWidth(cell, brush);
            boolean rounded = brush.displayRoundedRectangle();
            int arc = rectangleCornerArc(cell);
            g.setColor(brush.displayColor());
            if (rounded) {
                g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, arc, arc);
            } else {
                g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            }
            Color border = brush.displayBorderColor();
            if (strokeWidth > 0f && border.getAlpha() > 0) {
                g.setColor(border);
                g.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int inset = Math.round(strokeWidth / 2f);
                int width = Math.max(1, bounds.width - inset * 2);
                int height = Math.max(1, bounds.height - inset * 2);
                if (rounded) {
                    int insetArc = Math.max(1, arc - inset * 2);
                    g.drawRoundRect(bounds.x + inset, bounds.y + inset, width, height, insetArc, insetArc);
                } else {
                    g.drawRect(bounds.x + inset, bounds.y + inset, width, height);
                }
            }
        } finally {
            g.setStroke(savedStroke);
        }
    }

    /**
     * Paints one glyph markup.
     *
     * @param g graphics context
     * @param bounds square bounds
     * @param boardBounds full board bounds, used to keep edge badges on-board
     * @param brush annotation brush
     * @param slot glyph badge slot within a same-square stack
     * @param count number of glyph badges in the same-square stack
     * @param shadow true to draw the badge drop shadow
     */
    private static void paintRasterGlyph(Graphics2D g, Rectangle bounds, Rectangle boardBounds, MarkupBrush brush,
            int slot, int count, boolean shadow) {
        Font savedFont = g.getFont();
        Stroke savedStroke = g.getStroke();
        try {
            int cell = Math.min(bounds.width, bounds.height);
            String glyph = brush.glyph();
            Font font = Theme.font(Math.max(12, Math.round(cell * 0.34f)), Font.BOLD);
            g.setFont(font);
            FontMetrics metrics = g.getFontMetrics();
            float borderWidth = glyphBorderWidth(cell, brush);
            int diameter = glyphDiameter(cell, borderWidth);
            int centerX = BoardMarkupPainter.clampGlyphCenter(
                    (int) Math.round(glyphCenterX(bounds, diameter, slot, count)), diameter,
                    boardBounds.x, boardBounds.width);
            int centerY = BoardMarkupPainter.clampGlyphCenter(glyphCenterY(bounds), diameter,
                    boardBounds.y, boardBounds.height);
            int x = Math.round(centerX - diameter / 2f);
            int y = Math.round(centerY - diameter / 2f);
            if (shadow) {
                BoardMarkupPainter.paintGlyphShadow(g, x, y, diameter);
            }
            if (AnnotationGlyphs.isCustom(glyph)) {
                AnnotationGlyphs.paintCustom(g, glyph, x, y, diameter,
                        brush.displayColor(), brush.displayBorderColor(), borderWidth);
                return;
            }
            g.setColor(brush.displayColor());
            g.fillOval(x, y, diameter, diameter);
            Color border = brush.displayBorderColor();
            if (borderWidth > 0f && border.getAlpha() > 0) {
                g.setColor(border);
                g.setStroke(new BasicStroke(borderWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawOval(x, y, diameter, diameter);
            }
            g.setColor(border);
            g.drawString(glyph, centerX - metrics.stringWidth(glyph) / 2,
                    centerY + (metrics.getAscent() - metrics.getDescent()) / 2);
        } finally {
            g.setStroke(savedStroke);
            g.setFont(savedFont);
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
        appendSquares(snapshot, svg, board);
        if (snapshot.showNotation()) {
            appendCoordinates(snapshot, svg, board, snapshot.whiteDown());
        }
        appendSvgHighlights(snapshot, svg, board);
        appendSvgBackgroundAnnotations(snapshot, svg, board);
        appendSvgPieces(snapshot, svg, board);
        appendSvgForegroundAnnotations(snapshot, svg, board);
    }

    /**
     * Appends the board square grid.
     *
     * @param svg destination builder
     * @param board board rectangle
     * @param snapshot board export snapshot
     */
    private static void appendSquares(BoardExportSnapshot snapshot, StringBuilder svg, Rectangle board) {
        svg.append("  <g shape-rendering=\"crispEdges\">\n");
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                Rectangle cell = BoardStyle.cellBounds(board, row, col);
                appendRect(svg, cell.x, cell.y, cell.width, cell.height,
                        BoardStyle.squareColor(row, col, snapshot.boardLightColor(), snapshot.boardDarkColor()));
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
            Rectangle bounds = BoardGeometry.squareBounds(board, square.byteValue(), snapshot.whiteDown());
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
                    appendLegalTarget(svg, BoardGeometry.squareBounds(board, target, snapshot.whiteDown()),
                            contains(snapshot.captureTargets(), target));
                }
            }
        }
        if (snapshot.checkedKingSquare() != Field.NO_SQUARE) {
            Rectangle checked = BoardGeometry.squareBounds(board, snapshot.checkedKingSquare(), snapshot.whiteDown());
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
        Rectangle bounds = BoardGeometry.squareBounds(board, square, whiteDown);
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
                    .append("\" r=\"").append(format(radius)).append("\" fill=\"none\"")
                    .append(" stroke=\"").append(colorCss(Theme.LEGAL_CAPTURE_EDGE)).append("\"");
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
            Rectangle bounds = BoardGeometry.squareBounds(board, square, snapshot.whiteDown());
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
     * Appends under-piece user annotations.
     *
     * @param snapshot board state
     * @param svg destination builder
     * @param board board rectangle
     */
    private static void appendSvgBackgroundAnnotations(BoardExportSnapshot snapshot, StringBuilder svg,
            Rectangle board) {
        for (BoardMarkup markup : snapshot.boardMarkups()) {
            if (markup.isRectangle()) {
                appendRectangleMarkup(svg, rectangleBounds(board, markup, snapshot.whiteDown()), markup.brush(),
                        board.width / 8);
            }
        }
    }

    /**
     * Appends suggested and foreground user annotations.
     *
     * @param snapshot board state
     * @param svg destination builder
     * @param board board rectangle
     */
    private static void appendSvgForegroundAnnotations(BoardExportSnapshot snapshot, StringBuilder svg,
            Rectangle board) {
        if (snapshot.showSuggestedMoveArrow() && snapshot.suggestedMove() != Move.NO_MOVE) {
            appendArrow(svg,
                    BoardGeometry.center(board, Move.getFromIndex(snapshot.suggestedMove()), snapshot.whiteDown()),
                    BoardGeometry.center(board, Move.getToIndex(snapshot.suggestedMove()), snapshot.whiteDown()),
                    Math.max((double) BoardStyle.SUGGESTED_ARROW_LINE_WIDTH, board.width / 64.0),
                    board.width / 8.0 * BoardStyle.ARROW_PIECE_GAP_FRACTION,
                    Theme.withAlpha(Theme.BOARD_ARROW, SUGGESTED_ARROW_ALPHA));
        }
        if (snapshot.showSpecialMoveHints()) {
            appendSvgSpecialMoveHints(snapshot, svg, board);
        }
        int[] glyphCounts = glyphCounts(snapshot.boardMarkups());
        int[] glyphSlots = new int[64];
        for (BoardMarkup markup : snapshot.boardMarkups()) {
            if (markup.isCircle()) {
                appendCircleMarkup(svg, BoardGeometry.squareBounds(board, markup.from(), snapshot.whiteDown()), markup.brush());
            } else if (markup.isGlyph()) {
                int slot = glyphSlot(markup, glyphSlots);
                int count = glyphCount(markup, glyphCounts);
                appendGlyphMarkup(svg, BoardGeometry.squareBounds(board, markup.from(), snapshot.whiteDown()),
                        board, markup.brush(), slot, count, snapshot.glyphShadow());
            } else if (markup.isArrow()) {
                appendArrow(svg,
                        BoardGeometry.center(board, markup.from(), snapshot.whiteDown()),
                        BoardGeometry.center(board, markup.to(), snapshot.whiteDown()),
                        Math.max(5.0, board.width / 8.0 * markup.brush().lineWidth() / 64.0),
                        board.width / 8.0 * BoardStyle.ARROW_PIECE_GAP_FRACTION,
                        markup.brush().displayColor(),
                        markup.brush().displayBorderColor(),
                        arrowBorderWidth(Math.max(5.0, board.width / 8.0 * markup.brush().lineWidth() / 64.0),
                                markup.brush()));
            }
        }
    }

    /**
     * Appends special-move hint arrows.
     *
     * @param snapshot board state
     * @param svg destination builder
     * @param board board rectangle
     */
    private static void appendSvgSpecialMoveHints(BoardExportSnapshot snapshot, StringBuilder svg, Rectangle board) {
        double lineWidth = Math.max(5.0, board.width / 80.0);
        double gap = board.width / 8.0 * BoardStyle.ARROW_PIECE_GAP_FRACTION;
        for (Short arrow : BoardSpecialMoveHints.arrows(snapshot.position())) {
            short move = arrow.shortValue();
            appendArrow(svg,
                    BoardGeometry.center(board, Move.getFromIndex(move), snapshot.whiteDown()),
                    BoardGeometry.center(board, Move.getToIndex(move), snapshot.whiteDown()),
                    lineWidth,
                    gap,
                    BoardStyle.SPECIAL_MOVE_HINT_FILL,
                    BoardStyle.SPECIAL_MOVE_HINT_BORDER);
        }
    }

    /**
     * Appends one filled arrow polygon.
     *
     * @param svg destination builder
     * @param from origin point
     * @param to target point
     * @param lineWidth stroke width in pixels
     * @param gap distance to pull BOTH endpoints inward by (a quarter square), so
     *     the arrow keeps a clear gap from the start and target piece centres
     * @param color fill color
     */
    private static void appendArrow(StringBuilder svg, Point from, Point to,
            double lineWidth, double gap, Color color) {
        appendArrow(svg, from, to, lineWidth, gap, color, null);
    }

    /**
     * Appends one filled arrow polygon with an optional border.
     *
     * @param svg destination builder
     * @param from origin point
     * @param to target point
     * @param lineWidth stroke width in pixels
     * @param gap endpoint gap
     * @param color fill color
     * @param borderColor border color, or null
     */
    private static void appendArrow(StringBuilder svg, Point from, Point to,
            double lineWidth, double gap, Color color, Color borderColor) {
        appendArrow(svg, from, to, lineWidth, gap, color, borderColor, Math.max(1.5, lineWidth * 0.16));
    }

    /**
     * Appends one filled arrow polygon with an optional border.
     *
     * @param svg destination builder
     * @param from origin point
     * @param to target point
     * @param lineWidth stroke width in pixels
     * @param gap endpoint gap
     * @param color fill color
     * @param borderColor border color, or null
     * @param borderWidth source border width
     */
    private static void appendArrow(StringBuilder svg, Point from, Point to,
            double lineWidth, double gap, Color color, Color borderColor, double borderWidth) {
        double distance = from.distance(to);
        if (distance < 2.0) {
            return;
        }
        double angle = Math.atan2((double) to.y - from.y, (double) to.x - from.x);
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        // Mirror BoardArrowPainter.draw: pull both ends inward by `gap` and put
        // the arrowhead tip at the gapped target end, with a constant head size.
        double pull = Math.min(gap, distance * 0.40);
        double startX = from.x + cos * pull;
        double startY = from.y + sin * pull;
        double tipX = to.x - cos * pull;
        double tipY = to.y - sin * pull;
        double span = distance - 2.0 * pull;
        // Arrowhead width is exactly 3x the stalk width (sqrt(3) * headRadius),
        // matching BoardArrowPainter.draw and palgor/PuzzleProjekt.
        double headRadius = lineWidth * Math.sqrt(3.0);
        headRadius = Math.min(headRadius, span * 0.8);
        double px = -sin;
        double py = cos;
        double halfWidth = lineWidth / 2.0;
        double headX = tipX - cos * headRadius;
        double headY = tipY - sin * headRadius;
        double leftAngle = angle + 2.0 * Math.PI / 3.0;
        double rightAngle = angle - 2.0 * Math.PI / 3.0;
        double wingLeftX = headX + Math.cos(leftAngle) * headRadius;
        double wingLeftY = headY + Math.sin(leftAngle) * headRadius;
        double wingRightX = headX + Math.cos(rightAngle) * headRadius;
        double wingRightY = headY + Math.sin(rightAngle) * headRadius;
        double baseX = headX - 0.5 * headRadius * cos;
        double baseY = headY - 0.5 * headRadius * sin;
        svg.append("  <path d=\"M ").append(format(startX + px * halfWidth)).append(' ')
                .append(format(startY + py * halfWidth))
                .append(" L ").append(format(baseX + px * halfWidth)).append(' ')
                .append(format(baseY + py * halfWidth))
                .append(" L ").append(format(wingLeftX)).append(' ').append(format(wingLeftY))
                .append(" L ").append(format(tipX)).append(' ').append(format(tipY))
                .append(" L ").append(format(wingRightX)).append(' ').append(format(wingRightY))
                .append(" L ").append(format(baseX - px * halfWidth)).append(' ')
                .append(format(baseY - py * halfWidth))
                .append(" L ").append(format(startX - px * halfWidth)).append(' ')
                .append(format(startY - py * halfWidth))
                .append(" Z\" fill=\"").append(colorCss(color)).append("\"");
        appendOpacity(svg, color);
        if (borderColor != null && borderColor.getAlpha() > 0 && borderWidth > 0.0) {
            svg.append(" stroke=\"").append(colorCss(borderColor)).append("\"");
            appendStrokeOpacity(svg, borderColor);
            svg.append(" stroke-width=\"").append(format(borderWidth)).append("\"")
                    .append(" stroke-linejoin=\"round\" stroke-linecap=\"round\"");
        }
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
    private static void appendCircleMarkup(StringBuilder svg, Rectangle bounds, MarkupBrush brush) {
        Color fill = brush.displayColor();
        Color circleFill = Theme.withAlpha(fill, Math.round(fill.getAlpha() * 0.28f));
        Color border = brush.displayBorderColor();
        double strokeWidth = annotationBorderWidth(bounds.width, brush);
        double radius = Math.max(4.0, (Math.min(bounds.width, bounds.height) - strokeWidth) / 2.0);
        svg.append("  <circle cx=\"").append(format(bounds.getCenterX())).append("\" cy=\"")
                .append(format(bounds.getCenterY())).append("\" r=\"").append(format(radius))
                .append("\" fill=\"").append(colorCss(circleFill)).append("\"");
        appendOpacity(svg, circleFill);
        if (border.getAlpha() > 0 && strokeWidth > 0.0) {
            svg.append(" stroke=\"").append(colorCss(border)).append("\"");
            appendStrokeOpacity(svg, border);
            svg.append(" stroke-width=\"").append(format(strokeWidth)).append('"');
        }
        svg.append("/>\n");
    }

    /**
     * Returns the visual bounds covered by a rectangle markup.
     *
     * @param board board bounds
     * @param markup rectangle markup
     * @param whiteDown true when white is at the bottom
     * @return rectangle bounds
     */
    private static Rectangle rectangleBounds(Rectangle board, BoardMarkup markup, boolean whiteDown) {
        Rectangle first = BoardGeometry.squareBounds(board, markup.from(), whiteDown);
        Rectangle second = BoardGeometry.squareBounds(board, markup.to(), whiteDown);
        return first.union(second);
    }

    /**
     * Appends one filled rectangle markup.
     *
     * @param svg destination builder
     * @param bounds rectangle bounds
     * @param brush annotation brush
     * @param cell board cell size
     */
    private static void appendRectangleMarkup(StringBuilder svg, Rectangle bounds, MarkupBrush brush, int cell) {
        Color fill = brush.displayColor();
        Color border = brush.displayBorderColor();
        double strokeWidth = annotationBorderWidth(cell, brush);
        svg.append("  <rect x=\"").append(format(bounds.x)).append("\" y=\"").append(format(bounds.y))
                .append("\" width=\"").append(format(bounds.width)).append("\" height=\"")
                .append(format(bounds.height)).append("\"");
        if (brush.displayRoundedRectangle()) {
            svg.append(" rx=\"").append(format(rectangleCornerRadius(cell))).append("\"")
                    .append(" ry=\"").append(format(rectangleCornerRadius(cell))).append("\"");
        }
        svg.append(" fill=\"").append(colorCss(fill)).append("\"");
        appendOpacity(svg, fill);
        if (border.getAlpha() > 0 && strokeWidth > 0.0) {
            svg.append(" stroke=\"").append(colorCss(border)).append("\"");
            appendStrokeOpacity(svg, border);
            svg.append(" stroke-width=\"").append(format(strokeWidth)).append("\"");
        }
        svg.append("/>\n");
    }

    /**
     * Appends one glyph markup.
     *
     * @param svg destination builder
     * @param bounds square bounds
     * @param boardBounds full board bounds, used to keep edge badges on-board
     * @param brush annotation brush
     * @param slot glyph badge slot within a same-square stack
     * @param count number of glyph badges in the same-square stack
     * @param shadow true to attach the drop-shadow filter
     */
    private static void appendGlyphMarkup(StringBuilder svg, Rectangle bounds, Rectangle boardBounds,
            MarkupBrush brush, int slot, int count, boolean shadow) {
        int cell = Math.min(bounds.width, bounds.height);
        String glyph = brush.glyph();
        double fontSize = Math.max(12.0, cell * 0.34);
        Color fill = Theme.withAlpha(brush.displayColor(), 255);
        Color border = Theme.withAlpha(brush.displayBorderColor(), 255);
        double strokeWidth = glyphBorderWidth(cell, brush);
        double radius = glyphRadius(cell, strokeWidth);
        double cx = BoardMarkupPainter.clampGlyphCenter(glyphCenterX(bounds, radius * 2.0, slot, count),
                radius * 2.0, boardBounds.x, boardBounds.width);
        double cy = BoardMarkupPainter.clampGlyphCenter((double) glyphCenterY(bounds), radius * 2.0,
                boardBounds.y, boardBounds.height);
        if (AnnotationGlyphs.isCustom(glyph)) {
            appendCustomGlyph(svg, glyph, cx, cy, radius, fill, border, strokeWidth, shadow);
            return;
        }
        svg.append("  <circle cx=\"").append(format(cx)).append("\" cy=\"").append(format(cy))
                .append("\" r=\"").append(format(radius))
                .append("\" fill=\"").append(colorCss(fill)).append("\"");
        appendOpacity(svg, fill);
        appendGlyphShadowRef(svg, shadow);
        svg.append("/>\n");
        svg.append("  <text x=\"").append(format(cx)).append("\" y=\"")
                .append(format(glyphTextBaseline(cy, fontSize)))
                .append("\" fill=\"").append(colorCss(border)).append("\"");
        appendOpacity(svg, border);
        svg.append(" font-family=\"Inter, Segoe UI, sans-serif\" font-size=\"")
                .append(format(fontSize))
                .append("\" font-weight=\"700\" text-anchor=\"middle\">")
                .append(escape(glyph)).append("</text>\n");
    }

    /**
     * Appends the glyph drop-shadow filter reference to the current badge circle.
     *
     * @param svg destination builder
     * @param shadow true to attach the drop-shadow filter
     */
    private static void appendGlyphShadowRef(StringBuilder svg, boolean shadow) {
        if (shadow) {
            svg.append(" filter=\"url(#glyph-shadow)\"");
        }
    }

    /**
     * Appends the reusable soft drop-shadow filter used by glyph badges, sized
     * from the board square so it matches the on-screen Lichess-style shadow.
     *
     * @param svg destination builder
     * @param size board square-area size in SVG units
     */
    private static void appendGlyphShadowFilter(StringBuilder svg, int size) {
        double diameter = size / 8.0 * GLYPH_DIAMETER_FRACTION;
        // Soft Lichess-style drop shadow: blur the badge silhouette, drop it
        // slightly down/right, and merge it back UNDER the badge. Uses the classic
        // blur+offset+merge (feDropShadow is not reliably composited by every SVG
        // renderer, e.g. Inkscape drops the source graphic). The blur is generous
        // so the shadow still reads at small sizes.
        svg.append("  <defs><filter id=\"glyph-shadow\" x=\"-60%\" y=\"-60%\" width=\"220%\" height=\"220%\">")
                .append("<feGaussianBlur in=\"SourceAlpha\" stdDeviation=\"").append(format(diameter * 0.12))
                .append("\" result=\"b\"/>")
                .append("<feOffset in=\"b\" dx=\"0\" dy=\"").append(format(diameter * 0.03)).append("\" result=\"o\"/>")
                .append("<feComponentTransfer in=\"o\" result=\"s\">")
                .append("<feFuncA type=\"linear\" slope=\"0.65\"/></feComponentTransfer>")
                .append("<feMerge><feMergeNode in=\"s\"/><feMergeNode in=\"SourceGraphic\"/></feMerge>")
                .append("</filter></defs>\n");
    }

    /**
     * Returns whether the snapshot contains at least one glyph badge markup.
     *
     * @param snapshot board export snapshot
     * @return true when a glyph markup is present
     */
    private static boolean hasGlyphMarkup(BoardExportSnapshot snapshot) {
        for (BoardMarkup markup : snapshot.boardMarkups()) {
            if (markup.isGlyph()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Appends a custom vector SVG marker.
     *
     * @param svg destination builder
     * @param glyph annotation glyph token
     * @param cx badge center x
     * @param cy badge center y
     * @param radius badge radius
     * @param fill badge fill color
     * @param border glyph and border color
     * @param strokeWidth border stroke width
     * @param shadow true to attach the drop-shadow filter
     */
    private static void appendCustomGlyph(StringBuilder svg, String glyph, double cx, double cy, double radius,
            Color fill, Color border, double strokeWidth, boolean shadow) {
        svg.append("  <circle cx=\"").append(format(cx)).append("\" cy=\"").append(format(cy))
                .append("\" r=\"").append(format(radius))
                .append("\" fill=\"").append(colorCss(fill)).append("\"");
        appendOpacity(svg, fill);
        appendGlyphShadowRef(svg, shadow);
        svg.append("/>\n");
        double diameter = radius * 2.0;
        double x = cx - radius;
        double y = cy - radius;
        double scale = diameter / 100.0;
        svg.append("  <g transform=\"translate(").append(format(x)).append(' ')
                .append(format(y)).append(") scale(").append(format(scale)).append(")\">\n");
        if (AnnotationGlyphs.ZUGZWANG.equals(glyph)) {
            appendCustomCircle(svg, 50.0, 50.0, AnnotationGlyphs.ZUGZWANG_OUTER_RADIUS, border,
                    AnnotationGlyphs.vectorStrokeWidth(glyph));
            appendFilledCircle(svg, 50.0, 50.0, AnnotationGlyphs.ZUGZWANG_INNER_RADIUS, border);
        } else {
            String fillPath = AnnotationGlyphs.fillPath(glyph);
            if (fillPath != null) {
                svg.append("    <path fill=\"").append(colorCss(border)).append("\"");
                if (AnnotationGlyphs.fillEvenOdd(glyph)) {
                    svg.append(" fill-rule=\"evenodd\"");
                }
                appendOpacity(svg, border);
                svg.append(" d=\"").append(fillPath).append("\"/>\n");
            }
            String strokePath = AnnotationGlyphs.strokePath(glyph);
            if (strokePath != null) {
                svg.append("    <path fill=\"none\" stroke=\"").append(colorCss(border)).append("\"");
                appendStrokeOpacity(svg, border);
                svg.append(" stroke-width=\"").append(format(AnnotationGlyphs.vectorStrokeWidth(glyph))).append("\"");
                if (AnnotationGlyphs.strokeRoundCap(glyph)) {
                    svg.append(" stroke-linecap=\"round\" stroke-linejoin=\"round\"");
                }
                svg.append(" d=\"").append(strokePath).append("\"/>\n");
            }
        }
        svg.append("  </g>\n");
    }

    /**
     * Appends one stroke-only custom circle in a custom glyph group.
     *
     * @param svg destination builder
     * @param cx circle center x in vector space
     * @param cy circle center y in vector space
     * @param radius circle radius in vector space
     * @param stroke stroke color
     * @param strokeWidth stroke width in vector space
     */
    private static void appendCustomCircle(StringBuilder svg, double cx, double cy, double radius, Color stroke,
            double strokeWidth) {
        svg.append("    <circle cx=\"").append(format(cx)).append("\" cy=\"").append(format(cy))
                .append("\" r=\"").append(format(radius)).append("\" fill=\"none\" stroke=\"")
                .append(colorCss(stroke)).append("\"");
        appendStrokeOpacity(svg, stroke);
        svg.append(" stroke-width=\"").append(format(strokeWidth)).append("\"/>\n");
    }

    /**
     * Appends a filled SVG circle for a custom glyph component.
     *
     * @param svg destination builder
     * @param cx circle center x
     * @param cy circle center y
     * @param radius circle radius
     * @param fill fill color
     */
    private static void appendFilledCircle(StringBuilder svg, double cx, double cy, double radius, Color fill) {
        svg.append("    <circle cx=\"").append(format(cx)).append("\" cy=\"").append(format(cy))
                .append("\" r=\"").append(format(radius)).append("\" fill=\"").append(colorCss(fill)).append("\"");
        appendOpacity(svg, fill);
        svg.append("/>\n");
    }

    /**
     * Scales a brush border width to one board square.
     *
     * @param cell board cell size
     * @param brush annotation brush
     * @return scaled border width
     */
    private static float annotationBorderWidth(int cell, MarkupBrush brush) {
        int width = brush.displayBorderWidth();
        return width <= 0 ? 0f : Math.max(1f, cell * width / 64f);
    }

    /**
     * Returns the rectangle corner arc diameter for rounded annotations.
     *
     * @param cell board cell size
     * @return corner arc diameter
     */
    private static int rectangleCornerArc(int cell) {
        return Math.max(6, Math.round(cell * 0.22f));
    }

    /**
     * Returns the SVG rectangle corner radius for rounded annotations.
     *
     * @param cell board cell size
     * @return corner radius
     */
    private static double rectangleCornerRadius(int cell) {
        return rectangleCornerArc(cell) / 2.0;
    }

    /**
     * Scales a brush border width for compact glyph badges.
     *
     * @param cell board cell size
     * @param brush annotation brush
     * @return scaled glyph border width
     */
    private static float glyphBorderWidth(int cell, MarkupBrush brush) {
        int width = brush.displayBorderWidth();
        return width <= 0 ? 0f : Math.max(1f, cell * width / 192f);
    }

    /**
     * Returns the horizontal glyph circle center.
     *
     * @param bounds square bounds
     * @return center x
     */
    private static int glyphCenterX(Rectangle bounds) {
        return bounds.x + Math.round((float) (bounds.width * GLYPH_CENTER_X_FRACTION));
    }

    /**
     * Returns the horizontal center for one glyph badge in a same-square stack.
     *
     * @param bounds square bounds
     * @param diameter badge diameter
     * @param slot zero-based badge slot
     * @param count badge count on the same square
     * @return center x
     */
    private static double glyphCenterX(Rectangle bounds, double diameter, int slot, int count) {
        if (count <= 1) {
            return glyphCenterX(bounds);
        }
        double step = Math.max(1.0, diameter * GLYPH_STACK_STEP_FRACTION);
        double start = glyphCenterX(bounds) - step * (count - 1);
        double minStart = bounds.x + bounds.width * GLYPH_STACK_MIN_CENTER_X_FRACTION;
        return Math.max(start, minStart) + step * Math.max(0, slot);
    }

    /**
     * Returns the vertical glyph circle center.
     *
     * @param bounds square bounds
     * @return center y
     */
    private static int glyphCenterY(Rectangle bounds) {
        return bounds.y + Math.round((float) (bounds.height * GLYPH_CENTER_Y_FRACTION));
    }

    /**
     * Returns a glyph circle diameter that keeps the outline inside the square.
     *
     * @param cell board cell size
     * @param borderWidth scaled border width
     * @return circle diameter
     */
    private static int glyphDiameter(int cell, float borderWidth) {
        return Math.max(1, Math.round((float) (cell * GLYPH_DIAMETER_FRACTION - borderWidth)));
    }

    /**
     * Returns a glyph circle radius that keeps the outline inside the square.
     *
     * @param cell board cell size
     * @param borderWidth scaled border width
     * @return circle radius
     */
    private static double glyphRadius(int cell, double borderWidth) {
        return Math.max(0.5, cell * GLYPH_DIAMETER_FRACTION / 2.0 - borderWidth / 2.0);
    }

    /**
     * Returns the SVG text baseline that visually matches Java2D glyph badges.
     *
     * @param centerY badge center y
     * @param fontSize SVG font size
     * @return text baseline y
     */
    private static double glyphTextBaseline(double centerY, double fontSize) {
        return centerY + fontSize * 0.35;
    }

    /**
     * Counts glyph badges by source square.
     *
     * @param markups persistent annotations
     * @return count by square index
     */
    private static int[] glyphCounts(List<BoardMarkup> markups) {
        int[] counts = new int[64];
        for (BoardMarkup markup : markups) {
            int square = glyphSquare(markup);
            if (square >= 0) {
                counts[square]++;
            }
        }
        return counts;
    }

    /**
     * Returns and advances the slot for one glyph markup.
     *
     * @param markup markup to inspect
     * @param slots slot counters by square
     * @return slot index
     */
    private static int glyphSlot(BoardMarkup markup, int[] slots) {
        int square = glyphSquare(markup);
        return square >= 0 ? slots[square]++ : 0;
    }

    /**
     * Returns the glyph count for one markup square.
     *
     * @param markup markup to inspect
     * @param counts count by square index
     * @return glyph count
     */
    private static int glyphCount(BoardMarkup markup, int[] counts) {
        int square = glyphSquare(markup);
        return square >= 0 ? counts[square] : 1;
    }

    /**
     * Returns the source square for a glyph markup.
     *
     * @param markup markup to inspect
     * @return square index, or -1 when not a glyph
     */
    private static int glyphSquare(BoardMarkup markup) {
        if (markup == null || !markup.isGlyph()) {
            return -1;
        }
        int square = markup.from() & 0xff;
        return square < 64 ? square : -1;
    }

    /**
     * Scales a brush border width for arrow silhouettes.
     *
     * @param lineWidth arrow line width
     * @param brush annotation brush
     * @return scaled arrow border width
     */
    private static double arrowBorderWidth(double lineWidth, MarkupBrush brush) {
        int width = brush.displayBorderWidth();
        return width <= 0 ? 0.0 : Math.max(1.0, lineWidth * width / 25.0);
    }

    /**
     * Appends board coordinates.
     *
     * @param svg destination builder
     * @param board board rectangle
     * @param whiteDown board orientation
     * @param snapshot board export snapshot
     */
    private static void appendCoordinates(BoardExportSnapshot snapshot, StringBuilder svg, Rectangle board,
            boolean whiteDown) {
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
                    coordinateColor(7, i, snapshot.boardLightColor(), snapshot.boardDarkColor()));
            appendText(svg, rankText,
                    rankCell.x + rankInlinePad,
                    rankCell.y + rankBlockPad + fontSize,
                    coordinateColor(i, 0, snapshot.boardLightColor(), snapshot.boardDarkColor()));
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
     * @param strokeWidth source stroke width
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
     * @param boardSize source board size
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
     * @param light light-square color
     * @param dark dark-square color
     * @return coordinate text color
     */
    private static Color coordinateColor(int row, int col, Color light, Color dark) {
        return ((row + col) & 1) == 0 ? dark : light;
    }

    /**
     * Converts a color to a CSS hex string.
     *
     * @param color display color
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
