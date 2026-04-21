package chess.book.cover;

/**
 * Mutable options for native book-cover rendering.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Options {

	/**
	 * Binding layout for the generated cover.
	 */
	private Binding binding = Binding.PAPERBACK;

	/**
	 * Interior paper choice used for spine-width calculation.
	 */
	private Interior interior = Interior.WHITE_PAPER_BLACK_AND_WHITE;

	/**
	 * Explicit printed page count; zero means infer from the book metadata.
	 */
	private int pages = 0;

	/**
	 * Returns the binding layout.
	 *
	 * @return binding layout
	 */
	public Binding getBinding() {
		return binding;
	}

	/**
	 * Sets the binding layout.
	 *
	 * @param binding binding layout
	 * @return this options object
	 */
	public Options setBinding(Binding binding) {
		this.binding = binding == null ? Binding.PAPERBACK : binding;
		return this;
	}

	/**
	 * Returns the interior paper choice.
	 *
	 * @return interior paper choice
	 */
	public Interior getInterior() {
		return interior;
	}

	/**
	 * Sets the interior paper choice.
	 *
	 * @param interior interior paper choice
	 * @return this options object
	 */
	public Options setInterior(Interior interior) {
		this.interior = interior == null ? Interior.WHITE_PAPER_BLACK_AND_WHITE : interior;
		return this;
	}

	/**
	 * Returns the explicit printed page count.
	 *
	 * @return page count, or zero when inferred
	 */
	public int getPages() {
		return pages;
	}

	/**
	 * Sets the explicit printed page count.
	 *
	 * @param pages page count, or zero to infer from the book
	 * @return this options object
	 */
	public Options setPages(int pages) {
		this.pages = Math.max(0, pages);
		return this;
	}
}
