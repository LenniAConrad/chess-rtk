package chess.book.render;

import java.util.List;

import chess.pdf.document.Document;

/**
 * Stores the outcome of one layout pass.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class LayoutResult {

	/**
	 * Rendered PDF document, or {@code null} during simulation.
	 */
	final Document document;

	/**
	 * Collected TOC entries.
	 */
	final List<TocEntry> tocEntries;

	/**
	 * Creates a layout result.
	 *
	 * @param document rendered PDF document or {@code null}
	 * @param tocEntries collected TOC entries
	 */
	LayoutResult(Document document, List<TocEntry> tocEntries) {
		this.document = document;
		this.tocEntries = List.copyOf(tocEntries);
	}
}
