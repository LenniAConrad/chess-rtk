package chess.book.collection;

import chess.book.model.Book;

/**
 * Result of converting puzzle records into a puzzle-collection book model.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class BuildResult {

	/**
	 * Built book model.
	 */
	private final Book book;

	/**
	 * Number of accepted puzzle records.
	 */
	private final int accepted;

	/**
	 * Number of skipped records without a position.
	 */
	private final int skippedWithoutPosition;

	/**
	 * Number of skipped records without a usable PV.
	 */
	private final int skippedWithoutVariation;

	/**
	 * Creates one immutable build result.
	 *
	 * @param book built book model
	 * @param accepted accepted record count
	 * @param skippedWithoutPosition skipped count
	 * @param skippedWithoutVariation skipped count
	 */
	public BuildResult(Book book, int accepted, int skippedWithoutPosition, int skippedWithoutVariation) {
		this.book = book;
		this.accepted = Math.max(0, accepted);
		this.skippedWithoutPosition = Math.max(0, skippedWithoutPosition);
		this.skippedWithoutVariation = Math.max(0, skippedWithoutVariation);
	}

	/**
	 * Returns the built book model.
	 *
	 * @return book model
	 */
	public Book getBook() {
		return book;
	}

	/**
	 * Returns the accepted record count.
	 *
	 * @return accepted count
	 */
	public int getAccepted() {
		return accepted;
	}

	/**
	 * Returns the skipped count for records without a position.
	 *
	 * @return skipped count
	 */
	public int getSkippedWithoutPosition() {
		return skippedWithoutPosition;
	}

	/**
	 * Returns the skipped count for records without a usable PV.
	 *
	 * @return skipped count
	 */
	public int getSkippedWithoutVariation() {
		return skippedWithoutVariation;
	}

	/**
	 * Returns the total skipped count.
	 *
	 * @return total skipped records
	 */
	public int getSkipped() {
		return skippedWithoutPosition + skippedWithoutVariation;
	}
}
