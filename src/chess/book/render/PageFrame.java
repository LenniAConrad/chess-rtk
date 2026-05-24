package chess.book.render;

import chess.pdf.document.Canvas;

/**
 * Describes the writable area of one physical page.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class PageFrame {

	/**
	 * Physical page number.
	 */
	final int pageNumber;

	/**
	 * Drawing canvas, or {@code null} during simulation.
	 */
	final Canvas canvas;

	/**
	 * Content-area left edge.
	 */
	final double left;

	/**
	 * Content-area right edge.
	 */
	final double right;

	/**
	 * Content-area top edge.
	 */
	final double top;

	/**
	 * Content-area bottom edge.
	 */
	final double bottom;

	/**
	 * Content-area width.
	 */
	final double width;

	/**
	 * Content-area height.
	 */
	final double height;

	/**
	 * Creates a new page frame.
	 *
	 * @param pageNumber physical page number
	 * @param canvas drawing canvas or {@code null}
	 * @param left content left edge
	 * @param right content right edge
	 * @param top content top edge
	 * @param bottom content bottom edge
	 */
	PageFrame(int pageNumber, Canvas canvas, double left, double right, double top, double bottom) {
		this.pageNumber = pageNumber;
		this.canvas = canvas;
		this.left = left;
		this.right = right;
		this.top = top;
		this.bottom = bottom;
		this.width = right - left;
		this.height = bottom - top;
	}
}
