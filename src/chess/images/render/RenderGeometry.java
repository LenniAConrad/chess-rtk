package chess.images.render;

/**
 * Render dimensions and board placement shared by raster and SVG output.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class RenderGeometry {

    /**
     * Native output width.
     */
    final int width;

    /**
     * Native output height.
     */
    final int height;

    /**
     * Board origin x coordinate.
     */
    final int boardX;

    /**
     * Board origin y coordinate.
     */
    final int boardY;

    /**
     * Creates immutable render geometry.
     *
     * @param width native output width
     * @param height native output height
     * @param boardX board origin x coordinate
     * @param boardY board origin y coordinate
     */
    RenderGeometry(int width, int height, int boardX, int boardY) {
        this.width = width;
        this.height = height;
        this.boardX = boardX;
        this.boardY = boardY;
    }
}
