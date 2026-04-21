package chess.pdf.document;

/**
 * Named PDF page sizes measured in PostScript points.
 *
 * @param name human-readable page size name
 * @param width page width in points
 * @param height page height in points
 * @since 2026
 * @author Lennart A. Conrad
 */
public record PageSize(String name, double width, double height) {

	/**
	 * ISO A4 portrait page size.
	 */
	public static final PageSize A4 = new PageSize("A4", 595.0, 842.0);

	/**
	 * ISO A5 portrait page size.
	 */
	public static final PageSize A5 = new PageSize("A5", 420.0, 595.0);

	/**
	 * US Letter portrait page size.
	 */
	public static final PageSize LETTER = new PageSize("Letter", 612.0, 792.0);

	/**
	 * Creates a custom page size.
	 *
	 * @param name human-readable size name
	 * @param width page width in points
	 * @param height page height in points
	 */
	public PageSize {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("name cannot be blank");
		}
		if (width <= 0.0 || height <= 0.0) {
			throw new IllegalArgumentException("page dimensions must be positive");
		}
	}

	/**
	 * Returns the size name.
	 *
	 * @return page size name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Returns the page width in points.
	 *
	 * @return page width
	 */
	public double getWidth() {
		return width;
	}

	/**
	 * Returns the page height in points.
	 *
	 * @return page height
	 */
	public double getHeight() {
		return height;
	}

	/**
	 * Returns a debug-friendly page size description.
	 *
	 * @return page size name and dimensions
	 */
	@Override
	public String toString() {
		return name + " (" + width + " x " + height + ")";
	}
}
