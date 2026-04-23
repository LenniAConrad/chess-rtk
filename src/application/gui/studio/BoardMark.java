package application.gui.studio;

import chess.core.Field;

/**
 * Manual board annotation.
 *
 * @param type annotation type
 * @param from source square
 * @param to target square; {@link Field#NO_SQUARE} for circles
 * @param colorIndex annotation color index
 */
public record BoardMark(BoardMarkType type, byte from, byte to, int colorIndex) {

	/**
	 * Creates a circle mark.
	 *
	 * @param square target square
	 * @param colorIndex color index
	 * @return circle mark
	 */
	public static BoardMark circle(byte square, int colorIndex) {
		return new BoardMark(BoardMarkType.CIRCLE, square, Field.NO_SQUARE, colorIndex);
	}

	/**
	 * Creates an arrow mark.
	 *
	 * @param from source square
	 * @param to target square
	 * @param colorIndex color index
	 * @return arrow mark
	 */
	public static BoardMark arrow(byte from, byte to, int colorIndex) {
		return new BoardMark(BoardMarkType.ARROW, from, to, colorIndex);
	}
}
