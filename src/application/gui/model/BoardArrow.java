package application.gui.model;

/**
 * Arrow annotation drawn on the board.
 *
 * Records the source and destination squares plus the selected color index so rendering layers can show move ideas, tactics, or engine highlights.
 *
 * @param from starting square of the arrow.
 * @param to ending square of the arrow.
 * @param color color index used for rendering the arrow.
  * @since 2026
  * @author Lennart A. Conrad
 */
public record BoardArrow(byte from, byte to, byte color) {
}
