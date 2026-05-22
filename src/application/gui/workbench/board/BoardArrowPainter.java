package application.gui.workbench.board;

import application.gui.workbench.ui.Theme;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.Path2D;

/**
 * Paints solid board arrows with a single filled outline.
 *
 * <p>The board uses translucent arrow colors. Drawing shaft and head as two
 * separate shapes creates darker overlap at the join, so this painter owns the
 * reusable path and fills one continuous arrow outline.</p>
 */
final class BoardArrowPainter {

    /**
     * Cached suggested-arrow color matching chessboard-arrows' canvas opacity.
     */
    private static final Color ARROW_FILL = Theme.withAlpha(Theme.BOARD_ARROW, 204);

    /**
     * Cached suggested-arrow stroke.
     */
    private static final BasicStroke ARROW_STROKE =
            new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER);

    /**
     * Suggested-arrow head radius matching chessboard-arrows.
     */
    private static final int ARROW_HEAD_RADIUS = 15;

    /**
     * Suggested-arrow shorten-to-target distance.
     */
    private static final int ARROW_SHORTEN = 15;

    /**
     * Reusable path buffer for the unified arrow outline.
     */
    private final Path2D.Double arrowShape = new Path2D.Double();

    /**
     * Draws a suggested-move arrow.
     *
     * @param graphics graphics context
     * @param from origin point
     * @param to destination point
     */
    void drawSuggested(Graphics2D graphics, Point from, Point to) {
        Color savedColor = graphics.getColor();
        try {
            graphics.setColor(ARROW_FILL);
            draw(graphics, from, to, ARROW_STROKE.getLineWidth(), ARROW_SHORTEN);
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
     * @param shorten distance to shorten the arrow endpoint by
     */
    void draw(Graphics2D graphics, Point from, Point to, float lineWidth, double shorten) {
        double distance = from.distance(to);
        if (distance < 2.0) {
            return;
        }
        double angle = Math.atan2((double) to.y - from.y, (double) to.x - from.x);
        double targetShorten = Math.min(shorten, distance * 0.35);
        double headRadius = Math.min(Math.max(ARROW_HEAD_RADIUS, lineWidth * 2.6),
                Math.max(5.0, distance * 0.25));
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
        arrowShape.reset();
        arrowShape.moveTo(from.x + px * halfWidth, from.y + py * halfWidth);
        arrowShape.lineTo(baseX + px * halfWidth, baseY + py * halfWidth);
        arrowShape.lineTo(wingLeftX, wingLeftY);
        arrowShape.lineTo(tipX, tipY);
        arrowShape.lineTo(wingRightX, wingRightY);
        arrowShape.lineTo(baseX - px * halfWidth, baseY - py * halfWidth);
        arrowShape.lineTo(from.x - px * halfWidth, from.y - py * halfWidth);
        arrowShape.closePath();
        graphics.fill(arrowShape);
    }
}
