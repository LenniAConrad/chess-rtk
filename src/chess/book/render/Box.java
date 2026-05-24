package chess.book.render;

/**
 * Stores rectangular box geometry.
 *
 * @param x left edge
 * @param y top edge
 * @param width box width
 * @param height box height
 * @since 2026
 * @author Lennart A. Conrad
 */
record Box(
	/**
	 * Stores the x.
	 */
	double x,
	/**
	 * Stores the y.
	 */
	double y,
	/**
	 * Stores the width.
	 */
	double width,
	/**
	 * Stores the height.
	 */
	double height
) {
}
