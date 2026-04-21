package chess.book.render;

import java.util.ArrayList;
import java.util.List;

import chess.book.model.Book;
import chess.pdf.document.Document;
import chess.pdf.document.PageSize;

/**
 * Carries state shared by one layout pass.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class LayoutState {

	/**
	 * Book currently being rendered.
	 */
	final Book book;

	/**
	 * Parsed solution cache.
	 */
	final SolutionInfo[] solutions;

	/**
	 * PDF document being written, or {@code null} during simulation.
	 */
	final Document document;

	/**
	 * Physical page size for the whole document.
	 */
	final PageSize pageSize;

	/**
	 * Reserved number of TOC pages.
	 */
	final int tocPages;

	/**
	 * Table-of-contents entries collected during layout.
	 */
	final List<TocEntry> tocEntries = new ArrayList<>();

	/**
	 * Reserved TOC page frames used during the final render pass.
	 */
	final List<PageFrame> tocPageFrames = new ArrayList<>();

	/**
	 * All physical page frames, retained so final overlays can be appended after
	 * normal page content.
	 */
	final List<PageFrame> pageFrames = new ArrayList<>();

	/**
	 * Inner margin in points.
	 */
	final double innerMargin;

	/**
	 * Outer margin in points.
	 */
	final double outerMargin;

	/**
	 * Top margin in points.
	 */
	final double topMargin;

	/**
	 * Bottom margin in points.
	 */
	final double bottomMargin;

	/**
	 * Current physical page number.
	 */
	int pageNumber;

	/**
	 * Current top-level section number.
	 */
	int sectionNumber;

	/**
	 * Creates a new layout-state container.
	 *
	 * @param book book to render
	 * @param solutions parsed solution cache
	 * @param document target PDF document or {@code null}
	 * @param tocPages reserved TOC page count
	 */
	LayoutState(Book book, SolutionInfo[] solutions, Document document, int tocPages) {
		this.book = book;
		this.solutions = solutions;
		this.document = document;
		this.pageSize = book.toPageSize();
		this.tocPages = tocPages;
		this.innerMargin = Book.cmToPoints(book.getInnerMarginCm());
		this.outerMargin = Book.cmToPoints(book.getOuterMarginCm());
		this.topMargin = Book.cmToPoints(book.getTopMarginCm());
		this.bottomMargin = Book.cmToPoints(book.getBottomMarginCm());
		this.pageNumber = 0;
		this.sectionNumber = 0;
	}
}
