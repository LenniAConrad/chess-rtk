package chess.images.assets.shape;

import chess.core.Piece;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Original, procedurally drawn classic Staunton-style chess pieces.
 *
 * <p>Each piece is composed from geometric primitives ({@link Area} unions of
 * ellipses, rounded rectangles, and hand-built paths) in a fixed 200×200 design
 * space, then filled and outlined. The artwork is original to this project and
 * is not derived from any third-party piece set.</p>
 */
public final class StauntonPieces {

    /**
     * Design-space side length the geometry is authored in.
     */
    private static final double UNIT = 200.0;

    /**
     * Horizontal center of the design space.
     */
    private static final double CX = 100.0;

    /**
     * Outline stroke width in design-space units.
     */
    private static final float OUTLINE_WIDTH = 3.4f;

    /**
     * Ivory fill for white pieces.
     */
    private static final Color WHITE_FILL = new Color(0xF4F1E9);

    /**
     * Dark fill for black pieces.
     */
    private static final Color BLACK_FILL = new Color(0x2E3137);

    /**
     * Outline ink shared by both colours for a crisp Staunton edge.
     */
    private static final Color OUTLINE = new Color(0x1F2227);

    /**
     * Detail-line ink for white pieces.
     */
    private static final Color WHITE_DETAIL = new Color(0x3A3D42);

    /**
     * Detail-line ink for black pieces.
     */
    private static final Color BLACK_DETAIL = new Color(0x9AA0A8);

    /**
     * Prevents instantiation.
     */
    private StauntonPieces() {
        // utility
    }

    /**
     * Draws a Staunton-style piece scaled into the supplied rectangle.
     *
     * @param piece signed piece code from {@link Piece}
     * @param graphics graphics context
     * @param x left offset
     * @param y top offset
     * @param width target width
     * @param height target height
     */
    public static void draw(byte piece, Graphics2D graphics, double x, double y, double width, double height) {
        if (piece == Piece.EMPTY || width <= 0 || height <= 0) {
            return;
        }
        boolean white = Piece.isWhitePiece(piece);
        Area silhouette = silhouette(piece);
        if (silhouette == null) {
            return;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.translate(x, y);
            g.scale(width / UNIT, height / UNIT);
            g.setColor(white ? WHITE_FILL : BLACK_FILL);
            g.fill(silhouette);
            g.setColor(white ? WHITE_DETAIL : BLACK_DETAIL);
            g.setStroke(new BasicStroke(OUTLINE_WIDTH * 0.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            drawDetail(g, piece);
            g.setColor(OUTLINE);
            g.setStroke(new BasicStroke(OUTLINE_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(silhouette);
        } finally {
            g.dispose();
        }
    }

    /**
     * Builds the filled silhouette for one piece.
     *
     * @param piece signed piece code
     * @return piece silhouette, or null for unknown codes
     */
    private static Area silhouette(byte piece) {
        return switch (Math.abs(piece)) {
            case Piece.WHITE_PAWN -> pawn();
            case Piece.WHITE_KNIGHT -> knight();
            case Piece.WHITE_BISHOP -> bishop();
            case Piece.WHITE_ROOK -> rook();
            case Piece.WHITE_QUEEN -> queen();
            case Piece.WHITE_KING -> king();
            default -> null;
        };
    }

    /**
     * Builds the shared base, skirt, and collar pedestal.
     *
     * @param stemHalfWidth half-width where the stem meets the skirt top
     * @param skirtTopY y where the skirt meets the stem
     * @return pedestal area
     */
    private static Area pedestal(double stemHalfWidth, double skirtTopY) {
        Area area = new Area(new RoundRectangle2D.Double(CX - 50, 174, 100, 18, 16, 16));
        area.add(new Area(new Ellipse2D.Double(CX - 44, 160, 88, 20)));
        Path2D skirt = new Path2D.Double();
        skirt.moveTo(CX - stemHalfWidth, skirtTopY);
        skirt.curveTo(CX - stemHalfWidth - 4, skirtTopY + 16, CX - 44, 150, CX - 48, 172);
        skirt.lineTo(CX + 48, 172);
        skirt.curveTo(CX + 44, 150, CX + stemHalfWidth + 4, skirtTopY + 16, CX + stemHalfWidth, skirtTopY);
        skirt.closePath();
        area.add(new Area(skirt));
        return area;
    }

    /**
     * Adds a flaring stem from a collar down to the skirt top.
     *
     * @param area target silhouette
     * @param topHalf half-width at the stem top
     * @param topY stem top y
     * @param bottomHalf half-width at the stem bottom
     * @param bottomY stem bottom y
     */
    private static void stem(Area area, double topHalf, double topY, double bottomHalf, double bottomY) {
        Path2D stem = new Path2D.Double();
        stem.moveTo(CX - topHalf, topY);
        stem.curveTo(CX - topHalf, topY + (bottomY - topY) * 0.5, CX - bottomHalf, bottomY - 6, CX - bottomHalf, bottomY);
        stem.lineTo(CX + bottomHalf, bottomY);
        stem.curveTo(CX + bottomHalf, bottomY - 6, CX + topHalf, topY + (bottomY - topY) * 0.5, CX + topHalf, topY);
        stem.closePath();
        area.add(new Area(stem));
    }

    /**
     * Adds a collar disc.
     *
     * @param area target silhouette
     * @param halfWidth collar half-width
     * @param centerY collar center y
     */
    private static void collar(Area area, double halfWidth, double centerY) {
        area.add(new Area(new Ellipse2D.Double(CX - halfWidth, centerY - 9, halfWidth * 2, 18)));
    }

    /**
     * Builds the pawn silhouette.
     *
     * @return pawn area
     */
    private static Area pawn() {
        Area area = pedestal(15, 116);
        stem(area, 11, 100, 16, 118);
        collar(area, 24, 102);
        area.add(new Area(new Ellipse2D.Double(CX - 22, 48, 44, 44)));
        return area;
    }

    /**
     * Builds the bishop silhouette.
     *
     * @return bishop area
     */
    private static Area bishop() {
        Area area = pedestal(15, 116);
        stem(area, 11, 96, 16, 118);
        collar(area, 26, 98);
        Path2D mitre = new Path2D.Double();
        mitre.moveTo(CX, 28);
        mitre.curveTo(CX + 20, 42, CX + 32, 66, CX + 26, 84);
        mitre.curveTo(CX + 20, 98, CX - 20, 98, CX - 26, 84);
        mitre.curveTo(CX - 32, 66, CX - 20, 42, CX, 28);
        mitre.closePath();
        area.add(new Area(mitre));
        area.add(new Area(new Ellipse2D.Double(CX - 7, 16, 14, 14)));
        // Diagonal slit across the mitre.
        Path2D slit = new Path2D.Double();
        slit.moveTo(CX - 4, 46);
        slit.lineTo(CX + 20, 70);
        slit.lineTo(CX + 14, 76);
        slit.lineTo(CX - 10, 52);
        slit.closePath();
        area.subtract(new Area(slit));
        return area;
    }

    /**
     * Builds the rook silhouette.
     *
     * @return rook area
     */
    private static Area rook() {
        Area area = pedestal(18, 122);
        stem(area, 16, 96, 20, 124);
        collar(area, 28, 104);
        area.add(new Area(new RoundRectangle2D.Double(CX - 24, 74, 48, 34, 6, 6)));
        area.add(new Area(new RoundRectangle2D.Double(CX - 34, 44, 68, 30, 4, 4)));
        area.subtract(new Area(new Rectangle2D.Double(CX - 15, 40, 12, 18)));
        area.subtract(new Area(new Rectangle2D.Double(CX + 3, 40, 12, 18)));
        return area;
    }

    /**
     * Builds the queen silhouette.
     *
     * @return queen area
     */
    private static Area queen() {
        Area area = pedestal(16, 118);
        stem(area, 13, 92, 17, 120);
        collar(area, 28, 96);
        area.add(new Area(new RoundRectangle2D.Double(CX - 30, 70, 60, 18, 8, 8)));
        // Coronet: five spikes, each tipped with a pearl.
        double[] tips = { CX - 34, CX - 17, CX, CX + 17, CX + 34 };
        Path2D crown = new Path2D.Double();
        crown.moveTo(CX - 34, 74);
        for (int i = 0; i < tips.length; i++) {
            crown.lineTo(tips[i], 42);
            double valley = i < tips.length - 1 ? (tips[i] + tips[i + 1]) / 2 : CX + 34;
            crown.lineTo(valley, 70);
        }
        crown.lineTo(CX + 34, 74);
        crown.closePath();
        area.add(new Area(crown));
        for (double tip : tips) {
            area.add(new Area(new Ellipse2D.Double(tip - 7, 34, 14, 14)));
        }
        return area;
    }

    /**
     * Builds the king silhouette.
     *
     * @return king area
     */
    private static Area king() {
        Area area = pedestal(16, 118);
        stem(area, 14, 92, 18, 120);
        collar(area, 28, 96);
        area.add(new Area(new RoundRectangle2D.Double(CX - 30, 64, 60, 22, 10, 10)));
        area.add(new Area(new RoundRectangle2D.Double(CX - 26, 52, 52, 18, 8, 8)));
        // Cross finial.
        area.add(new Area(new RoundRectangle2D.Double(CX - 5, 14, 10, 38, 4, 4)));
        area.add(new Area(new RoundRectangle2D.Double(CX - 16, 24, 32, 10, 4, 4)));
        return area;
    }

    /**
     * Builds the knight silhouette: a left-facing horse head on the shared
     * pedestal.
     *
     * @return knight area
     */
    private static Area knight() {
        Area area = pedestal(22, 132);
        Path2D head = new Path2D.Double();
        head.moveTo(CX + 28, 132);
        head.curveTo(CX + 38, 104, CX + 38, 80, CX + 26, 60);
        head.curveTo(CX + 22, 52, CX + 18, 46, CX + 16, 40);
        head.lineTo(CX + 22, 24);
        head.lineTo(CX + 6, 36);
        head.lineTo(CX - 2, 22);
        head.lineTo(CX - 6, 42);
        head.curveTo(CX - 26, 50, CX - 44, 64, CX - 48, 84);
        head.lineTo(CX - 44, 94);
        head.curveTo(CX - 38, 100, CX - 26, 100, CX - 18, 96);
        head.curveTo(CX - 10, 112, CX + 2, 126, CX + 10, 132);
        head.closePath();
        area.add(new Area(head));
        return area;
    }

    /**
     * Draws per-piece interior detail lines after the silhouette is filled.
     *
     * @param g graphics context (already scaled to design space)
     * @param piece signed piece code
     */
    private static void drawDetail(Graphics2D g, byte piece) {
        int kind = Math.abs(piece);
        // A collar definition line shared by most pieces.
        if (kind != Piece.WHITE_KNIGHT) {
            g.draw(new java.awt.geom.Line2D.Double(CX - 24, 104, CX + 24, 104));
        }
        if (kind == Piece.WHITE_KNIGHT) {
            // Eye and a hint of mane.
            g.fill(new Ellipse2D.Double(CX - 18, 56, 7, 7));
            g.draw(new java.awt.geom.Line2D.Double(CX + 16, 52, CX + 26, 84));
            g.draw(new java.awt.geom.Line2D.Double(CX + 8, 50, CX + 18, 86));
        }
    }
}
