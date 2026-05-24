/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package chess.images.render;

import java.awt.Graphics2D;

/**
 * Reusable render context for one square-text paint pass.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class SquareTextRenderContext {

    /**
     * Metrics graphics used only for text measurement.
     */
    final Graphics2D metrics;

    /**
     * Board origin x coordinate.
     */
    final int boardX;

    /**
     * Board origin y coordinate.
     */
    final int boardY;

    /**
     * Mutable style scratch object.
     */
    final SquareTextStyle style;

    /**
     * Mutable layout scratch object.
     */
    final TextLayout layout;

    /**
     * Mutable tile-coordinate scratch object.
     */
    final IntPoint tile;

    /**
     * Creates one reusable render context.
     *
     * @param metrics metrics graphics
     * @param boardX board origin x coordinate
     * @param boardY board origin y coordinate
     * @param style mutable style scratch object
     * @param layout mutable layout scratch object
     * @param tile mutable tile-coordinate scratch object
     */
    SquareTextRenderContext(
            Graphics2D metrics,
            int boardX,
            int boardY,
            SquareTextStyle style,
            TextLayout layout,
            IntPoint tile) {
        this.metrics = metrics;
        this.boardX = boardX;
        this.boardY = boardY;
        this.style = style;
        this.layout = layout;
        this.tile = tile;
    }
}
