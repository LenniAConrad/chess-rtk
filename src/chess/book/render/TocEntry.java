package chess.book.render;

/**
 * Stores one table-of-contents entry.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class TocEntry {

	/**
	 * Display title.
	 */
	final String title;

	/**
	 * Nesting level.
	 */
	final int level;

	/**
	 * Destination page number.
	 */
	final int pageNumber;

	/**
	 * Creates a TOC entry.
	 *
	 * @param title display title
	 * @param level nesting level
	 * @param pageNumber destination page number
	 */
	TocEntry(String title, int level, int pageNumber) {
		this.title = title;
		this.level = level;
		this.pageNumber = pageNumber;
	}
}
