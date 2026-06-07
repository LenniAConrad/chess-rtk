package application.gui.workbench.board;

import application.gui.workbench.ui.Theme;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Stroke;
import java.awt.geom.Path2D;

/**
 * Paints solid board arrows with a single filled body plus a crisp contrasting
 * edge.
 *
 * <p>The board uses translucent arrow colors. Drawing shaft and head as two
 * separate shapes creates darker overlap at the join, so this painter owns the
 * reusable path and fills one continuous arrow outline. A thin contrasting
 * stroke is then traced around that silhouette so faint, low-alpha arrows
 * (notably the colour-coded relation channels) stay legible over pieces and
 * busy light/dark squares instead of washing out.</p>
 */
final class BoardArrowPainter {

    /**
     * Cached suggested-arrow color matching chessboard-arrows' canvas opacity.
     */
    private static final Color ARROW_FILL =
            Theme.withAlpha(Theme.BOARD_ARROW, BoardStyle.BOARD_ARROW_OPACITY);

    /**
     * Cached suggested-arrow stroke.
     */
    private static final BasicStroke ARROW_STROKE =
            new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER);

    /**
     * Reusable path buffer for the unified arrow outline.
     */
    private final Path2D.Double arrowShape = new Path2D.Double();

    /**
     * Cached contrasting-outline stroke, keyed by line width. The relation
     * overlay paints up to a dozen arrows every repaint (and on every animation
     * frame), so the outline stroke is memoised instead of allocated per arrow.
     */
    private BasicStroke outlineStroke;

    /**
     * Line width the cached {@link #outlineStroke} was built for.
     */
    private float outlineStrokeWidth = -1f;

    /**
     * Draws a suggested-move arrow.
     *
     * @param graphics graphics context
     * @param from origin point
     * @param to destination point
     * @param gap distance to keep from each piece centre (a quarter square)
     */
    void drawSuggested(Graphics2D graphics, Point from, Point to, double gap) {
        drawColored(graphics, from, to, gap, ARROW_FILL);
    }

    /**
     * Draws a board arrow with the supplied fill.
     *
     * @param graphics graphics context
     * @param from origin point
     * @param to destination point
     * @param gap distance to keep from each piece centre (a quarter square)
     * @param fill arrow fill
     */
    private void drawColored(Graphics2D graphics, Point from, Point to, double gap, Color fill) {
        Color savedColor = graphics.getColor();
        try {
            graphics.setColor(fill);
            draw(graphics, from, to, ARROW_STROKE.getLineWidth(), gap);
        } finally {
            graphics.setColor(savedColor);
        }
    }

    /**
     * Draws an arrow from square center to square center.
     *
     * @param graphics graphics context
     * @param from origin point
     * @param to destination point
     * @param lineWidth arrow line width in pixels
     * @param gap distance to pull BOTH endpoints inward by, so the arrow keeps a
     *     clear gap from the start and target piece centres (a quarter square)
     */
    void draw(Graphics2D graphics, Point from, Point to, float lineWidth, double gap) {
        double distance = from.distance(to);
        if (distance < 2.0) {
            return;
        }
        double angle = Math.atan2((double) to.y - from.y, (double) to.x - from.x);
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        // Pull BOTH endpoints inward by `gap` (a quarter square, as in the legacy
        // PuzzleProjekt renderer) so the arrow keeps a clear distance from the
        // piece it starts on and the piece it points to instead of touching their
        // centres. Clamp so a short arrow still keeps a visible shaft.
        double pull = Math.min(gap, distance * 0.40);
        double startX = from.x + cos * pull;
        double startY = from.y + sin * pull;
        double tipX = to.x - cos * pull;
        double tipY = to.y - sin * pull;
        double span = distance - 2.0 * pull;
        // The arrowhead width is exactly THREE TIMES the stalk (line) width, as in
        // palgor/PuzzleProjekt. The head width is sqrt(3) * headRadius, so
        // headRadius = sqrt(3) * lineWidth makes the head exactly 3 * lineWidth
        // wide. It scales with the stalk, not the arrow length. The clamp only
        // guards the degenerate case where the head would outrun a short shaft.
        double headRadius = lineWidth * Math.sqrt(3.0);
        headRadius = Math.min(headRadius, span * 0.8);
        double px = -sin;
        double py = cos;
        double halfWidth = lineWidth / 2.0;
        // Anchor the head so its tip lands at the (gapped) target end.
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
        arrowShape.reset();
        arrowShape.moveTo(startX + px * halfWidth, startY + py * halfWidth);
        arrowShape.lineTo(baseX + px * halfWidth, baseY + py * halfWidth);
        arrowShape.lineTo(wingLeftX, wingLeftY);
        arrowShape.lineTo(tipX, tipY);
        arrowShape.lineTo(wingRightX, wingRightY);
        arrowShape.lineTo(baseX - px * halfWidth, baseY - py * halfWidth);
        arrowShape.lineTo(startX - px * halfWidth, startY - py * halfWidth);
        arrowShape.closePath();
        Color fill = graphics.getColor();
        graphics.fill(arrowShape);
        // Trace a thin contrasting hairline around the silhouette so the arrow
        // separates from whatever it crosses. The shaft body keeps the caller's
        // colour; only the edge gains the contrast.
        Stroke savedStroke = graphics.getStroke();
        graphics.setColor(outlineColor(fill));
        graphics.setStroke(outlineStroke(Math.max(1.5f, lineWidth * 0.16f)));
        graphics.draw(arrowShape);
        graphics.setColor(fill);
        graphics.setStroke(savedStroke);
    }

    /**
     * Returns the contrasting-outline stroke for a given width, reusing the
     * cached instance when the width is unchanged (the common case — all arrows
     * on a board share one width).
     *
     * @param width outline stroke width in pixels
     * @return cached or freshly built round stroke
     */
    private BasicStroke outlineStroke(float width) {
        if (outlineStroke == null || width != outlineStrokeWidth) {
            outlineStroke = new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
            outlineStrokeWidth = width;
        }
        return outlineStroke;
    }

    /**
     * Picks a contrasting outline colour for an arrow body: a dark hairline on
     * light/mid arrows, a light one on dark arrows, so the edge reads against
     * the arrow regardless of channel hue. The edge tracks (and slightly
     * exceeds) the body's translucency so it stays visible even when the body
     * is faint, capped so it never looks like heavy ink.
     *
     * @param fill arrow body colour (alpha honoured)
     * @return contrasting outline colour
     */
    private static Color outlineColor(Color fill) {
        double luminance = (0.2126 * fill.getRed()
                + 0.7152 * fill.getGreen()
                + 0.0722 * fill.getBlue()) / 255.0;
        int alpha = Math.max(150, Math.min(210, fill.getAlpha() + 36));
        return luminance > 0.5
                ? new Color(18, 18, 18, alpha)
                : new Color(240, 240, 240, alpha);
    }
}
