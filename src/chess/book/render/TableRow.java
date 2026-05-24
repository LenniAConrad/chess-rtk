package chess.book.render;

/**
 * Stores one rendered solution-table row.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class TableRow {

	/**
	 * Left-side puzzle identifier.
	 */
	final String leftId;

	/**
	 * Left-side move text.
	 */
	final String leftMoves;

	/**
	 * Right-side puzzle identifier.
	 */
	final String rightId;

	/**
	 * Right-side move text.
	 */
	final String rightMoves;

	/**
	 * Creates a solution-table row.
	 *
	 * @param leftId left-side identifier
	 * @param leftMoves left-side move text
	 * @param rightId right-side identifier
	 * @param rightMoves right-side move text
	 */
	TableRow(String leftId, String leftMoves, String rightId, String rightMoves) {
		this.leftId = BookModelText.blankTo(leftId, "");
		this.leftMoves = BookModelText.blankTo(leftMoves, "");
		this.rightId = BookModelText.blankTo(rightId, "");
		this.rightMoves = BookModelText.blankTo(rightMoves, "");
	}
}
