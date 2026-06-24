package application.gui.workbench.board;

import application.gui.workbench.ui.Theme;
import chess.core.Move;
import chess.core.Position;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.util.List;

/**
 * Paints persistent and preview board markups.
 */
final class BoardMarkupPainter {

    /**
     * Opacity for the markup currently being drawn.
     */
    private static final double CURRENT_OPACITY = 0.9;

    /**
     * Opacity for a pending erase markup.
     */
    private static final double PENDING_ERASE_OPACITY = 0.6;

    /**
     * Reusable arrow painter.
     */
    private final BoardArrowPainter arrowPainter = new BoardArrowPainter();

    /**
     * Horizontal glyph-circle center within the target square.
     */
    private static final float GLYPH_CENTER_X_FRACTION = 0.75f;

    /**
     * Vertical glyph-circle center within the target square.
     */
    private static final float GLYPH_CENTER_Y_FRACTION = 0.25f;

    /**
     * Glyph-circle diameter relative to the target square.
     */
    private static final float GLYPH_DIAMETER_FRACTION = 0.50f;

    /**
     * Paints all user markups.
     *
     * @param g graphics context
     * @param board board bounds
     * @param whiteDown true when white is at the bottom
     * @param input markup input state
     */
    void drawBackgroundMarkups(Graphics2D g, Rectangle board, boolean whiteDown, BoardMarkupInput input) {
        drawMarkups(g, board, whiteDown, input, true);
    }

    /**
     * Paints user markups that should appear above pieces.
     *
     * @param g graphics context
     * @param board board bounds
     * @param whiteDown true when white is at the bottom
     * @param input markup input state
     */
    void drawForegroundMarkups(Graphics2D g, Rectangle board, boolean whiteDown, BoardMarkupInput input) {
        drawMarkups(g, board, whiteDown, input, false);
    }

    /**
     * Paints user markups for one board layer.
     *
     * @param g graphics context
     * @param board board bounds
     * @param whiteDown true when white is at the bottom
     * @param input markup input state
     * @param background true to paint rectangles below pieces
     */
    private void drawMarkups(Graphics2D g, Rectangle board, boolean whiteDown, BoardMarkupInput input,
            boolean background) {
        if (input.isEmpty()) {
            return;
        }
        int pendingEraseIndex = input.pendingEraseIndex();
        List<BoardMarkup> boardMarkups = input.markups();
        for (int i = 0; i < boardMarkups.size(); i++) {
            BoardMarkup markup = boardMarkups.get(i);
            if (markup.isRectangle() != background) {
                continue;
            }
            double opacity = i == pendingEraseIndex ? PENDING_ERASE_OPACITY : 1.0;
            drawMarkup(g, board, whiteDown, markup, opacity);
        }
        BoardMarkup currentMarkup = input.currentMarkup();
        if (currentMarkup != null && pendingEraseIndex < 0 && currentMarkup.isRectangle() == background) {
            drawMarkup(g, board, whiteDown, currentMarkup, CURRENT_OPACITY);
        }
    }

    /**
     * Paints automatic castling-right and en-passant hint arrows.
     *
     * @param g graphics context
     * @param board board bounds
     * @param whiteDown true when white is at the bottom
     * @param position source position
     */
    void drawSpecialMoveHints(Graphics2D g, Rectangle board, boolean whiteDown, Position position) {
        if (position == null) {
            return;
        }
        int cell = Math.max(1, board.width / 8);
        float lineWidth = Math.max(5f, board.width / 80f);
        double gap = cell * BoardStyle.ARROW_PIECE_GAP_FRACTION;
        Color savedColor = g.getColor();
        try {
            g.setColor(BoardStyle.SPECIAL_MOVE_HINT_FILL);
            for (Short arrow : BoardSpecialMoveHints.arrows(position)) {
                short move = arrow.shortValue();
                arrowPainter.draw(g,
                        BoardGeometry.center(board, Move.getFromIndex(move), whiteDown),
                        BoardGeometry.center(board, Move.getToIndex(move), whiteDown),
                        lineWidth,
                        gap,
                        BoardStyle.SPECIAL_MOVE_HINT_BORDER);
            }
        } finally {
            g.setColor(savedColor);
        }
    }

    /**
     * Paints one markup.
     *
     * @param g graphics context
     * @param board board bounds
     * @param whiteDown true when white is at the bottom
     * @param markup board markup
     * @param opacity opacity multiplier
     */
    private void drawMarkup(Graphics2D g, Rectangle board, boolean whiteDown, BoardMarkup markup, double opacity) {
        Color savedColor = g.getColor();
        try {
            if (markup.isCircle()) {
                drawCircle(g, BoardGeometry.squareBounds(board, markup.from(), whiteDown), markup.brush(), opacity);
            } else if (markup.isRectangle()) {
                drawRectangle(g, rectangleBounds(board, markup, whiteDown), markup.brush(), board.width / 8, opacity);
            } else if (markup.isGlyph()) {
                drawGlyph(g, BoardGeometry.squareBounds(board, markup.from(), whiteDown), markup.brush(), opacity);
            } else if (markup.isArrow()) {
                drawArrow(g, board, whiteDown, markup, opacity);
            }
        } finally {
            g.setColor(savedColor);
        }
    }

    /**
     * Draws one circle marker.
     *
     * @param g graphics context
     * @param bounds square bounds
     * @param brush markup brush
     * @param opacity opacity multiplier
     */
    private static void drawCircle(Graphics2D g, Rectangle bounds, MarkupBrush brush, double opacity) {
        Stroke savedStroke = g.getStroke();
        try {
            int cell = bounds.width;
            float strokeWidth = scaledBorderWidth(cell, brush);
            int diameter = Math.max(8, Math.round(cell - strokeWidth));
            Color fill = markupColor(brush.displayColor(), opacity);
            g.setColor(Theme.withAlpha(fill, Math.round(fill.getAlpha() * 0.28f)));
            g.fillOval(bounds.x + Math.round(strokeWidth / 2f), bounds.y + Math.round(strokeWidth / 2f),
                    diameter, diameter);
            Color border = markupColor(brush.displayBorderColor(), opacity);
            if (strokeWidth > 0f && border.getAlpha() > 0) {
                g.setColor(border);
                g.setStroke(new BasicStroke(strokeWidth));
                g.drawOval(bounds.x + Math.round(strokeWidth / 2f), bounds.y + Math.round(strokeWidth / 2f),
                        diameter, diameter);
            }
        } finally {
            g.setStroke(savedStroke);
        }
    }

    /**
     * Draws one filled rectangle marker.
     *
     * @param g graphics context
     * @param bounds rectangle bounds
     * @param brush markup brush
     * @param cell board cell size
     * @param opacity opacity multiplier
     */
    private static void drawRectangle(Graphics2D g, Rectangle bounds, MarkupBrush brush, int cell, double opacity) {
        Stroke savedStroke = g.getStroke();
        try {
            float strokeWidth = scaledBorderWidth(cell, brush);
            boolean rounded = brush.displayRoundedRectangle();
            int arc = rectangleCornerArc(cell);
            g.setColor(markupColor(brush.displayColor(), opacity));
            if (rounded) {
                g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, arc, arc);
            } else {
                g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            }
            Color border = markupColor(brush.displayBorderColor(), opacity);
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
     * Draws one glyph marker.
     *
     * @param g graphics context
     * @param bounds square bounds
     * @param brush markup brush
     * @param opacity opacity multiplier
     */
    private static void drawGlyph(Graphics2D g, Rectangle bounds, MarkupBrush brush, double opacity) {
        Font savedFont = g.getFont();
        Stroke savedStroke = g.getStroke();
        try {
            int cell = Math.min(bounds.width, bounds.height);
            String glyph = brush.glyph();
            Font font = Theme.font(Math.max(12, Math.round(cell * 0.34f)), Font.BOLD);
            g.setFont(font);
            FontMetrics metrics = g.getFontMetrics();
            float borderWidth = scaledBadgeBorderWidth(cell, brush);
            int diameter = glyphDiameter(cell, borderWidth);
            int centerX = glyphCenterX(bounds);
            int centerY = glyphCenterY(bounds);
            int x = Math.round(centerX - diameter / 2f);
            int y = Math.round(centerY - diameter / 2f);
            Color fill = markupColor(brush.displayColor(), opacity);
            Color border = markupColor(brush.displayBorderColor(), opacity);
            g.setColor(fill);
            g.fillOval(x, y, diameter, diameter);
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
     * Draws one arrow marker.
     *
     * @param g graphics context
     * @param board board bounds
     * @param whiteDown true when white is at the bottom
     * @param markup arrow markup
     * @param opacity opacity multiplier
     */
    private void drawArrow(Graphics2D g, Rectangle board, boolean whiteDown, BoardMarkup markup, double opacity) {
        int cell = Math.max(1, board.width / 8);
        float lineWidth = Math.max(5f, cell * markup.brush().lineWidth() / 64f);
        double gap = cell * BoardStyle.ARROW_PIECE_GAP_FRACTION;
        g.setColor(markupColor(markup.brush().displayColor(), opacity));
        arrowPainter.draw(g,
                BoardGeometry.center(board, markup.from(), whiteDown),
                BoardGeometry.center(board, markup.to(), whiteDown),
                lineWidth,
                gap,
                markupColor(markup.brush().displayBorderColor(), opacity),
                scaledArrowBorderWidth(lineWidth, markup.brush()));
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
     * Applies one opacity multiplier to a color.
     *
     * @param color source color
     * @param opacity opacity multiplier
     * @return color with adjusted alpha
     */
    private static Color markupColor(Color color, double opacity) {
        int alpha = (int) Math.round(color.getAlpha() * Math.max(0.0, Math.min(1.0, opacity)));
        return Theme.withAlpha(color, alpha);
    }

    /**
     * Scales a brush border width to one board square.
     *
     * @param cell board cell size
     * @param brush annotation brush
     * @return scaled border width
     */
    private static float scaledBorderWidth(int cell, MarkupBrush brush) {
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
     * Scales a brush border width for compact glyph badges.
     *
     * @param cell board cell size
     * @param brush annotation brush
     * @return scaled badge border width
     */
    private static float scaledBadgeBorderWidth(int cell, MarkupBrush brush) {
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
        return bounds.x + Math.round(bounds.width * GLYPH_CENTER_X_FRACTION);
    }

    /**
     * Returns the vertical glyph circle center.
     *
     * @param bounds square bounds
     * @return center y
     */
    private static int glyphCenterY(Rectangle bounds) {
        return bounds.y + Math.round(bounds.height * GLYPH_CENTER_Y_FRACTION);
    }

    /**
     * Returns a glyph circle diameter that keeps the outline inside the square.
     *
     * @param cell board cell size
     * @param borderWidth scaled border width
     * @return circle diameter
     */
    private static int glyphDiameter(int cell, float borderWidth) {
        return Math.max(1, Math.round(cell * GLYPH_DIAMETER_FRACTION - borderWidth));
    }

    /**
     * Scales a brush border width for arrow silhouettes.
     *
     * @param lineWidth arrow line width
     * @param brush annotation brush
     * @return scaled arrow border width
     */
    private static float scaledArrowBorderWidth(float lineWidth, MarkupBrush brush) {
        int width = brush.displayBorderWidth();
        return width <= 0 ? 0f : Math.max(1f, lineWidth * width / 25f);
    }
}
