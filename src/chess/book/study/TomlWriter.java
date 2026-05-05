package chess.book.study;

import static utility.TomlWriterSupport.appendBoolean;
import static utility.TomlWriterSupport.appendDouble;
import static utility.TomlWriterSupport.appendInt;
import static utility.TomlWriterSupport.appendString;
import static utility.TomlWriterSupport.appendStringArray;

import chess.pdf.Composition;

/**
 * Serializes puzzle-study manifests to TOML.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
@SuppressWarnings("java:S3776")
public final class TomlWriter {

	/**
	 * Utility class; prevent instantiation.
	 */
	private TomlWriter() {
		// utility
	}

	/**
	 * Serializes one puzzle-study manifest to TOML.
	 *
	 * @param book source manifest
	 * @return TOML text
	 */
	public static String toToml(StudyBook book) {
		if (book == null) {
			throw new IllegalArgumentException("book cannot be null");
		}

		StringBuilder sb = new StringBuilder(4096 + (book.getCompositions().length * 320));
		appendString(sb, "title", book.getTitle());
		appendString(sb, "subtitle", book.getSubtitle());
		appendString(sb, "author", book.getAuthor());
		appendString(sb, "time", book.getTime());
		appendString(sb, "location", book.getLocation());
		appendInt(sb, "pages", book.getPages());
		appendStringArray(sb, "blurb", book.getBlurb());
		appendStringArray(sb, "link", book.getLink());
		appendString(sb, "pageSize", StudyBook.pageSizeToken(book.getPageSize()));
		appendDouble(sb, "margin", book.getMargin());
		appendInt(sb, "diagramsPerRow", book.getDiagramsPerRow());
		appendInt(sb, "boardPixels", book.getBoardPixels());
		appendBoolean(sb, "whiteSideDown", book.isWhiteSideDown());
		appendBoolean(sb, "showFen", book.isShowFen());

		Composition[] compositions = book.getCompositions();
		if (compositions.length > 0 && sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
			sb.append('\n');
		}
		for (Composition composition : compositions) {
			sb.append("[[compositions]]\n");
			appendString(sb, "title", composition == null ? "" : composition.getTitle());
			appendString(sb, "description", composition == null ? "" : composition.getDescription());
			appendString(sb, "analysis", composition == null ? "" : composition.getAnalysis());
			appendString(sb, "comment", composition == null ? "" : composition.getComment());
			appendString(sb, "hintLevel1", composition == null ? "" : composition.getHintLevel1());
			appendString(sb, "hintLevel2", composition == null ? "" : composition.getHintLevel2());
			appendString(sb, "hintLevel3", composition == null ? "" : composition.getHintLevel3());
			appendString(sb, "hintLevel4", composition == null ? "" : composition.getHintLevel4());
			appendString(sb, "id", composition == null ? "" : composition.getId());
			appendString(sb, "time", composition == null ? "" : composition.getTime());
			appendStringArray(sb, "figureMovesAlgebraic",
					composition == null ? new String[0] : composition.getFigureMovesAlgebraic().toArray(new String[0]));
			appendStringArray(sb, "figureMovesDetail",
					composition == null ? new String[0] : composition.getFigureMovesDetail().toArray(new String[0]));
			appendStringArray(sb, "figureFens",
					composition == null ? new String[0] : composition.getFigureFens().toArray(new String[0]));
			appendStringArray(sb, "figureArrows",
					composition == null ? new String[0] : composition.getFigureArrows().toArray(new String[0]));
			sb.append('\n');
		}
		return sb.toString();
	}

}
