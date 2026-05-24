package chess.images.render;

import java.awt.Font;
import java.awt.FontMetrics;

/**
 * Temporary layout metrics for square text strings.
 *
 * <p>
 * The renderer updates one instance per label to track measured width, height,
 * and font selection while fitting overlay text into a square.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class TextLayout {

    /**
     * Font chosen for the label.
     */
    Font font;

    /**
     * Font metrics corresponding to {@link #font}.
     */
    FontMetrics fm;

    /**
     * Measured width of the current text.
     */
    int textWidth;

    /**
     * Measured height of the current text.
     */
    int textHeight;

    /**
     * Rendered font size in points.
     */
    int fontSize;
}
