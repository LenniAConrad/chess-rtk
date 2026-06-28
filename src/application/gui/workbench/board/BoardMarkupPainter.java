package application.gui.workbench.board;

import application.gui.workbench.ui.Theme;
import chess.core.Move;
import chess.core.Position;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
     * Whether glyph badges are drawn with a soft drop shadow (Lichess-style).
     */
    private boolean glyphShadow = true;

    /**
     * Enables or disables the glyph badge drop shadow.
     *
     * @param enabled true to draw glyph badges with a drop shadow
     */
    void setGlyphShadow(boolean enabled) {
        glyphShadow = enabled;
    }

    /**
     * Returns whether glyph badges are drawn with a drop shadow.
     *
     * @return true when the glyph drop shadow is enabled
     */
    boolean isGlyphShadow() {
        return glyphShadow;
    }

    /**
     * Horizontal glyph-circle center within the target square. Anchored near the
     * top-right corner (Lichess-style) so the badge straddles the corner.
     */
    private static final float GLYPH_CENTER_X_FRACTION = 0.90f;

    /**
     * Vertical glyph-circle center within the target square.
     */
    private static final float GLYPH_CENTER_Y_FRACTION = 0.10f;

    /**
     * Glyph-circle diameter relative to the target square (Lichess uses ~0.40).
     */
    private static final float GLYPH_DIAMETER_FRACTION = 0.48f;

    /**
     * Horizontal step between stacked glyph badges, relative to badge diameter.
     */
    private static final float GLYPH_STACK_STEP_FRACTION = 0.56f;

    /**
     * Left-most center used when several glyph badges share one square.
     */
    private static final float GLYPH_STACK_MIN_CENTER_X_FRACTION = 0.25f;

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
        BoardMarkup currentMarkup = input.currentMarkup();
        int[] glyphCounts = background ? null
                : glyphCounts(boardMarkups, pendingEraseIndex < 0 ? currentMarkup : null);
        int[] glyphSlots = background ? null : new int[64];
        for (int i = 0; i < boardMarkups.size(); i++) {
            BoardMarkup markup = boardMarkups.get(i);
            if (markup.isRectangle() != background) {
                continue;
            }
            double opacity = i == pendingEraseIndex ? PENDING_ERASE_OPACITY : 1.0;
            int slot = glyphSlot(markup, glyphSlots);
            int count = glyphCount(markup, glyphCounts);
            drawMarkup(g, board, whiteDown, markup, opacity, slot, count);
        }
        if (currentMarkup != null && pendingEraseIndex < 0 && currentMarkup.isRectangle() == background) {
            int slot = glyphSlot(currentMarkup, glyphSlots);
            int count = glyphCount(currentMarkup, glyphCounts);
            drawMarkup(g, board, whiteDown, currentMarkup, CURRENT_OPACITY, slot, count);
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
     * @param glyphSlot glyph badge slot within a same-square stack
     * @param glyphCount number of glyph badges in the same-square stack
     */
    private void drawMarkup(Graphics2D g, Rectangle board, boolean whiteDown, BoardMarkup markup, double opacity,
            int glyphSlot, int glyphCount) {
        Color savedColor = g.getColor();
        try {
            if (markup.isCircle()) {
                drawCircle(g, BoardGeometry.squareBounds(board, markup.from(), whiteDown), markup.brush(), opacity);
            } else if (markup.isRectangle()) {
                drawRectangle(g, rectangleBounds(board, markup, whiteDown), markup.brush(), board.width / 8, opacity);
            } else if (markup.isGlyph()) {
                drawGlyph(g, BoardGeometry.squareBounds(board, markup.from(), whiteDown), markup.brush(),
                        opacity, glyphSlot, glyphCount);
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
     * @param slot glyph badge slot within a same-square stack
     * @param count number of glyph badges in the same-square stack
     */
    private void drawGlyph(Graphics2D g, Rectangle bounds, MarkupBrush brush, double opacity,
            int slot, int count) {
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
            int centerX = glyphCenterX(bounds, diameter, slot, count);
            int centerY = glyphCenterY(bounds);
            int x = Math.round(centerX - diameter / 2f);
            int y = Math.round(centerY - diameter / 2f);
            Color fill = Theme.withAlpha(brush.displayColor(), 255);
            Color border = Theme.withAlpha(brush.displayBorderColor(), 255);
            if (glyphShadow) {
                paintGlyphShadow(g, x, y, diameter);
            }
            if (AnnotationGlyphs.isCustom(glyph)) {
                AnnotationGlyphs.paintCustom(g, glyph, x, y, diameter, fill, border, borderWidth);
                return;
            }
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
     * Cache of pre-blurred drop-shadow sprites keyed by badge diameter, so the
     * Gaussian blur is computed once per size and reused on every repaint.
     */
    private static final Map<Integer, BufferedImage> SHADOW_SPRITES = new ConcurrentHashMap<>();

    /**
     * Glyph drop-shadow geometry as fractions of the badge diameter: a soft
     * Lichess-style shadow dropped slightly down and to the right. The disc is
     * a touch larger than the badge (spread) so the shadow shows all around it
     * rather than as a thin one-sided sliver, with a generous blur so it still
     * reads at small on-board sizes.
     */
    private static final double SHADOW_DX_FRACTION = 0.04,
            SHADOW_DY_FRACTION = 0.07,
            SHADOW_SPREAD_FRACTION = 0.0,
            SHADOW_BLUR_FRACTION = 0.05;

    /**
     * Glyph drop-shadow opacity.
     */
    private static final float SHADOW_OPACITY = 0.5f;

    /**
     * Paints a soft, centered drop shadow behind a glyph badge circle.
     *
     * <p>Renders a Gaussian-blurred black disc (cached per size) slightly larger
     * than the badge, centered on it, so a soft even halo shows all around the
     * opaque badge drawn on top — not an offset crescent.</p>
     *
     * @param g graphics context
     * @param x badge left edge
     * @param y badge top edge
     * @param diameter badge diameter
     */
    static void paintGlyphShadow(Graphics2D g, int x, int y, int diameter) {
        if (diameter <= 0) {
            return;
        }
        BufferedImage sprite = SHADOW_SPRITES.computeIfAbsent(diameter, BoardMarkupPainter::buildShadowSprite);
        int centerX = x + Math.round(diameter / 2f);
        int centerY = y + Math.round(diameter / 2f);
        int half = sprite.getWidth() / 2;
        int dx = Math.round(diameter * (float) SHADOW_DX_FRACTION);
        int dy = Math.round(diameter * (float) SHADOW_DY_FRACTION);
        Composite savedComposite = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, SHADOW_OPACITY));
        g.drawImage(sprite, centerX - half + dx, centerY - half + dy, null);
        g.setComposite(savedComposite);
    }

    /**
     * Builds a centered Gaussian-blurred black disc sprite for one badge diameter.
     * The disc is larger than the badge by {@link #SHADOW_SPREAD_FRACTION} on each
     * side and centered in the square sprite, so it reads as an even halo.
     *
     * @param diameter badge diameter
     * @return blurred black disc on a transparent, padded canvas
     */
    private static BufferedImage buildShadowSprite(int diameter) {
        double spread = diameter * SHADOW_SPREAD_FRACTION;
        double sigma = Math.max(0.6, diameter * SHADOW_BLUR_FRACTION);
        int discDiameter = Math.max(1, (int) Math.round(diameter + 2.0 * spread));
        int radius = (int) Math.ceil(sigma * 3.0);
        int margin = radius + 1;
        int size = discDiameter + margin * 2;
        BufferedImage disc = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D ig = disc.createGraphics();
        try {
            ig.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            ig.setColor(Color.BLACK);
            ig.fill(new Ellipse2D.Double(margin, margin, discDiameter, discDiameter));
        } finally {
            ig.dispose();
        }
        BufferedImage blurred = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        gaussianKernel(sigma, radius).filter(disc, blurred);
        return blurred;
    }

    /**
     * Builds a normalized 2-D Gaussian convolution for a given blur radius.
     *
     * @param sigma Gaussian standard deviation
     * @param radius kernel half-size
     * @return convolution operation
     */
    private static ConvolveOp gaussianKernel(double sigma, int radius) {
        int n = radius * 2 + 1;
        float[] weights = new float[n * n];
        double twoSigmaSq = 2.0 * sigma * sigma;
        double sum = 0.0;
        for (int j = -radius; j <= radius; j++) {
            for (int i = -radius; i <= radius; i++) {
                double w = Math.exp(-(i * i + j * j) / twoSigmaSq);
                weights[(j + radius) * n + (i + radius)] = (float) w;
                sum += w;
            }
        }
        for (int k = 0; k < weights.length; k++) {
            weights[k] /= (float) sum;
        }
        return new ConvolveOp(new Kernel(n, n, weights), ConvolveOp.EDGE_NO_OP, null);
    }

    /**
     * Clamps a glyph badge center (int) so the whole badge stays within a board axis.
     *
     * @param center proposed badge center along the axis
     * @param diameter badge diameter
     * @param origin board axis origin (x or y)
     * @param span board axis length (width or height)
     * @return clamped center keeping the badge fully on-board
     */
    static int clampGlyphCenter(int center, int diameter, int origin, int span) {
        return (int) Math.round(clampGlyphCenter((double) center, diameter, origin, span));
    }

    /**
     * Clamps a glyph badge center so the whole badge stays within a board axis,
     * so a badge straddling an edge square's corner is nudged inward instead of
     * being clipped at the board boundary.
     *
     * @param center proposed badge center along the axis
     * @param diameter badge diameter
     * @param origin board axis origin (x or y)
     * @param span board axis length (width or height)
     * @return clamped center keeping the badge fully on-board
     */
    static double clampGlyphCenter(double center, double diameter, double origin, double span) {
        double half = diameter / 2.0;
        double lo = origin + half;
        double hi = origin + span - half;
        return hi < lo ? center : Math.max(lo, Math.min(center, hi));
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
     * Returns the horizontal center for one glyph badge in a same-square stack.
     *
     * @param bounds square bounds
     * @param diameter badge diameter
     * @param slot zero-based badge slot
     * @param count badge count on the same square
     * @return center x
     */
    private static int glyphCenterX(Rectangle bounds, int diameter, int slot, int count) {
        if (count <= 1) {
            return glyphCenterX(bounds);
        }
        float step = Math.max(1f, diameter * GLYPH_STACK_STEP_FRACTION);
        float start = glyphCenterX(bounds) - step * (count - 1);
        float minStart = bounds.x + bounds.width * GLYPH_STACK_MIN_CENTER_X_FRACTION;
        return Math.round(Math.max(start, minStart) + step * Math.max(0, slot));
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

    /**
     * Counts glyph badges by source square.
     *
     * @param markups persistent annotations
     * @param currentMarkup current preview annotation
     * @return count by square index
     */
    private static int[] glyphCounts(List<BoardMarkup> markups, BoardMarkup currentMarkup) {
        int[] counts = new int[64];
        for (BoardMarkup markup : markups) {
            incrementGlyphCount(counts, markup);
        }
        incrementGlyphCount(counts, currentMarkup);
        return counts;
    }

    /**
     * Increments one square count for glyph markups.
     *
     * @param counts count by square index
     * @param markup markup to inspect
     */
    private static void incrementGlyphCount(int[] counts, BoardMarkup markup) {
        int square = glyphSquare(markup);
        if (square >= 0) {
            counts[square]++;
        }
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
        return square >= 0 && slots != null ? slots[square]++ : 0;
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
        return square >= 0 && counts != null ? counts[square] : 1;
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
}
