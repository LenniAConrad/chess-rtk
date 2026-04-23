package chess.pdf;

/**
 * Stores the physical geometry inferred from a PDF document.
 *
 * @param pageCount total page count
 * @param pageWidthPoints first-page width in PostScript points
 * @param pageHeightPoints first-page height in PostScript points
 * @since 2026
 * @author Lennart A. Conrad
 */
public record DocumentMetrics(
	/**
	 * Stores the page count.
	 */
	int pageCount,
	/**
	 * Stores the page width points.
	 */
	double pageWidthPoints,
	/**
	 * Stores the page height points.
	 */
	double pageHeightPoints
) {

	/**
	 * Number of centimeters in one PostScript point.
	 */
	private static final double CENTIMETERS_PER_POINT = 2.54 / 72.0;

	/**
	 * Validates the extracted geometry.
	 */
	public DocumentMetrics {
		if (pageCount <= 0) {
			throw new IllegalArgumentException("page count must be positive");
		}
		if (!Double.isFinite(pageWidthPoints) || pageWidthPoints <= 0.0) {
			throw new IllegalArgumentException("page width must be positive");
		}
		if (!Double.isFinite(pageHeightPoints) || pageHeightPoints <= 0.0) {
			throw new IllegalArgumentException("page height must be positive");
		}
	}

	/**
	 * Returns the first-page width in centimeters.
	 *
	 * @return width in centimeters
	 */
	public double pageWidthCm() {
		return pageWidthPoints * CENTIMETERS_PER_POINT;
	}

	/**
	 * Returns the first-page height in centimeters.
	 *
	 * @return height in centimeters
	 */
	public double pageHeightCm() {
		return pageHeightPoints * CENTIMETERS_PER_POINT;
	}
}
