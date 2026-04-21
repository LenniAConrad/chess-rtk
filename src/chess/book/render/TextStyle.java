package chess.book.render;

import java.awt.Color;

import chess.pdf.document.Font;

/**
 * Stores font, size, and fill color for text drawing.
 *
 * @param font font to use
 * @param size font size
 * @param color fill color
 * @since 2026
 * @author Lennart A. Conrad
 */
record TextStyle(Font font, double size, Color color) {
}
