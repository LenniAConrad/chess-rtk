package chess.book.render;

/**
 * Shared string normalization for extracted book layout models.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class BookModelText {

	/**
	 * Utility class; prevent instantiation.
	 */
	private BookModelText() {
		// utility
	}

	/**
	 * Returns a fallback for null or blank text.
	 *
	 * @param value source value
	 * @param fallback fallback value
	 * @return value or fallback
	 */
	static String blankTo(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}
}
