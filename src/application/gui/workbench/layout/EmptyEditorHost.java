package application.gui.workbench.layout;

import application.gui.workbench.ui.Theme;
import chess.images.assets.shape.SvgShapes;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import javax.swing.JPanel;
import utility.Svg;

/**
 * Editor-body host that shows a subtle rook silhouette when no tab content is
 * open, mirroring VS Code's empty editor watermark treatment.
 */
final class EmptyEditorHost extends JPanel {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Minimum watermark rook size.
     */
    private static final int WATERMARK_MIN_SIZE = 72;

    /**
     * Maximum watermark rook size.
     */
    private static final int WATERMARK_MAX_SIZE = 240;

    /**
     * Alpha for the dark-mode empty editor watermark.
     */
    private static final int WATERMARK_DARK_ALPHA = 56;

    /**
     * Alpha for the light-mode empty editor watermark.
     */
    private static final int WATERMARK_LIGHT_ALPHA = 20;

    /**
     * Parsed embedded rook SVG used as the empty-editor watermark source.
     */
    private static final Svg.DocumentModel ROOK_WATERMARK_DOCUMENT = Svg.parse(SvgShapes.whiteRook());

    /**
     * Outer rook silhouette from the embedded SVG, after its local SVG
     * transforms have been applied.
     */
    private static final Shape ROOK_WATERMARK_SILHOUETTE = rookSvgSilhouette();

    /**
     * Creates an empty editor host.
     */
    EmptyEditorHost() {
        super(new BorderLayout());
        // Breathing room around every tab's content. Previously panels sat
        // flush against the tab strip and pane edges; even 10px inside the
        // host gives the workbench a noticeably less cramped feel without
        // touching any per-panel layout.
        setBorder(Theme.pad(10));
    }

    /**
     * Paints the normal panel background and, when empty, a muted rook
     * silhouette in the center.
     *
     * @param graphics graphics context
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (getComponentCount() > 0) {
            return;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            paintRookWatermark(g, getWidth(), getHeight());
        } finally {
            g.dispose();
        }
    }

    /**
     * Paints the centered rook silhouette watermark.
     *
     * @param g graphics context
     * @param width host width
     * @param height host height
     */
    private static void paintRookWatermark(Graphics2D g, int width, int height) {
        int shortest = Math.min(width, height);
        if (shortest < WATERMARK_MIN_SIZE) {
            return;
        }
        int size = Math.min(WATERMARK_MAX_SIZE, Math.max(WATERMARK_MIN_SIZE, shortest / 3));
        double x = (width - size) / 2.0;
        double y = (height - size) / 2.0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(watermarkColor());
        g.fill(rookWatermarkShape(x, y, size));
    }

    /**
     * Returns the low-contrast letterpress watermark color for the active
     * theme.
     *
     * @return watermark fill color
     */
    private static Color watermarkColor() {
        int alpha = Theme.isDark() ? WATERMARK_DARK_ALPHA : WATERMARK_LIGHT_ALPHA;
        return new Color(0, 0, 0, alpha);
    }

    /**
     * Extracts the outer silhouette from the embedded rook SVG.
     *
     * @return transformed rook silhouette
     */
    private static Shape rookSvgSilhouette() {
        Svg.ShapeModel shape = ROOK_WATERMARK_DOCUMENT.shapes().get(0);
        return shape.transform().createTransformedShape(shape.path());
    }

    /**
     * Builds a fitted watermark shape from the embedded rook SVG silhouette.
     *
     * @param x left edge
     * @param y top edge
     * @param size square size
     * @return rook watermark shape
     */
    private static Shape rookWatermarkShape(double x, double y, double size) {
        Rectangle2D bounds = ROOK_WATERMARK_SILHOUETTE.getBounds2D();
        double scale = size / Math.max(bounds.getWidth(), bounds.getHeight());
        double scaledWidth = bounds.getWidth() * scale;
        double scaledHeight = bounds.getHeight() * scale;
        AffineTransform transform = new AffineTransform();
        transform.translate(x + (size - scaledWidth) / 2.0, y + (size - scaledHeight) / 2.0);
        transform.scale(scale, scale);
        transform.translate(-bounds.getX(), -bounds.getY());
        return transform.createTransformedShape(ROOK_WATERMARK_SILHOUETTE);
    }
}
