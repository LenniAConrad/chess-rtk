package chess.book.render;

/**
 * Stores board-grid geometry shared by puzzle and solution pages.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class GridMetrics {

	/**
	 * Number of grid columns.
	 */
	final int columns;

	/**
	 * Width of one logical grid cell.
	 */
	final double cellWidth;

	/**
	 * Height of one logical grid cell.
	 */
	final double cellHeight;

	/**
	 * Square board width used inside each cell.
	 */
	final double boardWidth;

	/**
	 * Horizontal hfil-style gap between board cells.
	 */
	final double horizontalGap;

	/**
	 * Creates board-grid geometry.
	 *
	 * @param columns number of grid columns
	 * @param cellWidth logical cell width
	 * @param cellHeight logical cell height
	 * @param boardWidth square board width
	 * @param horizontalGap horizontal gap between boards
	 */
	GridMetrics(int columns, double cellWidth, double cellHeight, double boardWidth, double horizontalGap) {
		this.columns = columns;
		this.cellWidth = cellWidth;
		this.cellHeight = cellHeight;
		this.boardWidth = boardWidth;
		this.horizontalGap = horizontalGap;
	}
}
