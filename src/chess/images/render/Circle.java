/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package chess.images.render;

import java.awt.Color;
import java.awt.Stroke;

/**
 * Circle overlay state for rendered chess boards.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class Circle {

    /**
     * Target square index.
     */
    final byte index;

    /**
     * Circle diameter in pixels.
     */
    final int diameter;

    /**
     * Outline color.
     */
    final Color border;

    /**
     * Fill color.
     */
    final Color fill;

    /**
     * Outline stroke.
     */
    final Stroke stroke;

    /**
     * Creates a circle overlay.
     *
     * @param index target square index
     * @param diameter circle diameter in pixels
     * @param border outline color
     * @param fill fill color
     * @param stroke outline stroke
     */
    Circle(byte index, int diameter, Color border, Color fill, Stroke stroke) {
        this.index = index;
        this.diameter = diameter;
        this.border = border;
        this.fill = fill;
        this.stroke = stroke;
    }
}
