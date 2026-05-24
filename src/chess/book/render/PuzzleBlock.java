package chess.book.render;

/**
 * Stores one recurring puzzle block between solution tables.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class PuzzleBlock {

	/**
	 * First puzzle index in the block.
	 */
	final int startIndex;

	/**
	 * Number of puzzles in the block.
	 */
	final int count;

	/**
	 * Number of spreads in the block.
	 */
	final int spreadCount;

	/**
	 * Creates a puzzle block.
	 *
	 * @param startIndex first puzzle index
	 * @param count puzzle count
	 * @param spreadCount spread count
	 */
	PuzzleBlock(int startIndex, int count, int spreadCount) {
		this.startIndex = startIndex;
		this.count = count;
		this.spreadCount = spreadCount;
	}
}
