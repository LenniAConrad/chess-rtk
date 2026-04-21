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
record PagePreviewGeometry(double totalWidth, double totalHeight, double framedWidth,
				double framedHeight, 		double border, 		double pageWidth, 		double pageHeight, 		double boardStartX,
				double boardStartY, 		double boardSize, 		double horizontalGap, 		double verticalGap, 		int rows, 		int columns,
				double shadowBlur) {
}
