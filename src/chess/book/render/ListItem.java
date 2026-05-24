package chess.book.render;

/**
 * Stores one parsed list item.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class ListItem {

	/**
	 * Visible marker drawn at the start of the list item.
	 */
	final String marker;

	/**
	 * List item body text.
	 */
	final String text;

	/**
	 * Creates a list item.
	 *
	 * @param marker visible marker
	 * @param text item body text
	 */
	ListItem(String marker, String text) {
		this.marker = BookModelText.blankTo(marker, "-");
		this.text = BookModelText.blankTo(text, "");
	}
}
