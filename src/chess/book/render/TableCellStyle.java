package chess.book.render;

/**
 * Stores text style details for one solution-table cell.
 *
 * @param text text drawing style
 * @param centered whether the text should be centered
 * @since 2026
 * @author Lennart A. Conrad
 */
record TableCellStyle(
	/**
	 * Stores the text.
	 */
	TextStyle text,
	/**
	 * Stores the centered.
	 */
	boolean centered
) {
}
