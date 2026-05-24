package chess.images.render;

import java.awt.Color;
import java.awt.Font;
import java.awt.Stroke;

/**
 * Per-square text overlay state for rendered chess boards.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class SquareText {

    /**
     * Target square index.
     */
    final byte index;

    /**
     * Label to draw.
     */
    final String text;

    /**
     * Text color.
     */
    final Color textColor;

    /**
     * Background fill color.
     */
    final Color background;

    /**
     * Border color.
     */
    final Color border;

    /**
     * Border stroke.
     */
    final Stroke borderStroke;

    /**
     * Optional base font override.
     */
    final Font baseFont;

    /**
     * Whether to align the box to the bottom of the tile.
     */
    final boolean bottomAligned;

    /**
     * Whether to draw this label below pieces and overlays.
     */
    final boolean detail;

    /**
     * Creates a square-text overlay.
     *
     * @param index target square index
     * @param text label to draw
     * @param textColor text color
     * @param background background fill color
     * @param border border color
     * @param borderStroke border stroke
     * @param baseFont optional base font override
     * @param bottomAligned whether to align the box to the bottom of the tile
     * @param detail whether to draw this label below pieces and overlays
     */
    SquareText(
            byte index,
            String text,
            Color textColor,
            Color background,
            Color border,
            Stroke borderStroke,
            Font baseFont,
            boolean bottomAligned,
            boolean detail) {
        this.index = index;
        this.text = text;
        this.textColor = textColor;
        this.background = background;
        this.border = border;
        this.borderStroke = borderStroke;
        this.baseFont = baseFont;
        this.bottomAligned = bottomAligned;
        this.detail = detail;
    }
}
