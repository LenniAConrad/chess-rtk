package chess.book.render;

/**
 * Stores the native geometry for one page-preview example.
 *
 * @param totalWidth total shadow-inclusive preview width
 * @param totalHeight total shadow-inclusive preview height
 * @param framedWidth page frame width including border
 * @param framedHeight page frame height including border
 * @param border page border thickness
 * @param pageWidth preview page width
 * @param pageHeight preview page height
 * @param boardStartX first board x coordinate
 * @param boardStartY first board y coordinate
 * @param boardSize board width and height
 * @param horizontalGap horizontal gap between boards
 * @param verticalGap vertical gap between boards
 * @param rows board rows
 * @param columns board columns
 * @param shadowBlur shadow blur reserve
 * @since 2026
 * @author Lennart A. Conrad
 */
record PagePreviewGeometry(
	/**
	 * Stores the total width.
	 */
	double totalWidth,
	/**
	 * Stores the total height.
	 */
	double totalHeight,
	/**
	 * Stores the framed width.
	 */
	double framedWidth,
	/**
	 * Stores the framed height.
	 */
	double framedHeight,
	/**
	 * Stores the border.
	 */
	double border,
	/**
	 * Stores the page width.
	 */
	double pageWidth,
	/**
	 * Stores the page height.
	 */
	double pageHeight,
	/**
	 * Stores the board start x.
	 */
	double boardStartX,
	/**
	 * Stores the board start y.
	 */
	double boardStartY,
	/**
	 * Stores the board size.
	 */
	double boardSize,
	/**
	 * Stores the horizontal gap.
	 */
	double horizontalGap,
	/**
	 * Stores the vertical gap.
	 */
	double verticalGap,
	/**
	 * Stores the rows.
	 */
	int rows,
	/**
	 * Stores the columns.
	 */
	int columns,
	/**
	 * Stores the shadow blur.
	 */
	double shadowBlur
) {
}
