package chess.images.render;

import java.awt.Color;
import java.awt.Stroke;

/**
 * Reusable mutable holder for square text colors and strokes.
 *
 * <p>
 * The board renderer keeps one instance and rewrites the fields for each label
 * so drawing overlays does not allocate a short-lived style object per square.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class SquareTextStyle {

    /**
     * Background fill color for the text box.
     */
    Color background;

    /**
     * Border color for the text box outline.
     */
    Color border;

    /**
     * Color used to draw the text glyph.
     */
    Color textColor;

    /**
     * Stroke used when drawing the border.
     */
    Stroke borderStroke;
}
