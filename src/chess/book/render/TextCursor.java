package chess.book.render;

/**
 * Tracks the current text cursor within a multi-page section.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class TextCursor {

	/**
	 * Current page frame.
	 */
	final PageFrame page;

	/**
	 * Current vertical cursor.
	 */
	double y;

	/**
	 * Creates a text cursor.
	 *
	 * @param page current page frame
	 * @param y current vertical cursor
	 */
	TextCursor(PageFrame page, double y) {
		this.page = page;
		this.y = y;
	}
}
