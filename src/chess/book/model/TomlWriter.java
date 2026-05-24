package chess.book.model;

import static utility.Toml.appendDouble;
import static utility.Toml.appendInt;
import static utility.Toml.appendString;
import static utility.Toml.appendStringArray;

/**
 * Serializes chess-book models to TOML.
 *
 * <p>
 * The emitted field order matches the manifest shape documented by the native
 * book renderer so generated files stay easy to inspect and edit by hand.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class TomlWriter {

	/**
	 * Utility class; prevent instantiation.
	 */
	private TomlWriter() {
		// utility
	}

	/**
	 * Serializes one book model to TOML text.
	 *
	 * @param book source book
	 * @return TOML manifest text
	 */
	public static String toToml(Book book) {
		if (book == null) {
			throw new IllegalArgumentException("book cannot be null");
		}

		StringBuilder sb = new StringBuilder(4096 + (book.getElements().length * 96));
		appendString(sb, "title", book.getTitle());
		appendString(sb, "subtitle", book.getSubtitle());
		appendString(sb, "author", book.getAuthor());
		appendString(sb, "time", book.getTime());
		appendString(sb, "location", book.getLocation());
		appendString(sb, "language", book.getLanguage().name());
		appendInt(sb, "pages", book.getPages());
		appendInt(sb, "tablefrequency", book.getTableFrequency());
		appendInt(sb, "puzzlerows", book.getPuzzleRows());
		appendInt(sb, "puzzlecolumns", book.getPuzzleColumns());
		appendDouble(sb, "paperwidth", book.getPaperWidthCm());
		appendDouble(sb, "paperheight", book.getPaperHeightCm());
		appendDouble(sb, "innermargin", book.getInnerMarginCm());
		appendDouble(sb, "outermargin", book.getOuterMarginCm());
		appendDouble(sb, "topmargin", book.getTopMarginCm());
		appendDouble(sb, "bottommargin", book.getBottomMarginCm());
		appendStringArray(sb, "imprint", book.getImprint());
		appendStringArray(sb, "dedication", book.getDedication());
		appendStringArray(sb, "introduction", book.getIntroduction());
		appendStringArray(sb, "howToRead", book.getHowToRead());
		appendStringArray(sb, "blurb", book.getBlurb());
		appendStringArray(sb, "link", book.getLink());
		appendStringArray(sb, "afterword", book.getAfterword());

		Element[] elements = book.getElements();
		if (elements.length > 0 && !sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') {
			sb.append('\n');
		}
		for (Element element : elements) {
			sb.append("[[elements]]\n");
			appendString(sb, "position", element == null ? "" : element.getPosition());
			appendString(sb, "moves", element == null ? "" : element.getMoves());
			sb.append('\n');
		}
		return sb.toString();
	}

}
