package chess.book.render;

import java.awt.Color;

/**
 * Stores SVG stroke styling.
 *
 * @param color stroke color
 * @param opacity stroke opacity
 * @param width stroke width
 * @since 2026
 * @author Lennart A. Conrad
 */
record SvgStroke(Color color, double opacity, double width) {
}
