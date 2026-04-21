package chess.book.cover;

import java.util.Locale;

/**
 * Describes print interior paper choices used for spine-width calculation.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public enum Interior {

	/**
	 * White paper with black-and-white printing.
	 */
	WHITE_PAPER_BLACK_AND_WHITE("white-bw", 0.002252),

	/**
	 * Standard color ink on white paper.
	 */
	WHITE_PAPER_STANDARD_COLOR("white-standard-color", 0.002252),

	/**
	 * Premium color ink on white paper.
	 */
	WHITE_PAPER_PREMIUM_COLOR("white-premium-color", 0.002347),

	/**
	 * Cream paper with black-and-white printing.
	 */
	CREAM_PAPER_BLACK_AND_WHITE("cream-bw", 0.0025);

	/**
	 * Number of centimeters in one inch.
	 */
	private static final double CENTIMETERS_PER_INCH = 2.54;

	/**
	 * Stable command-line token.
	 */
	private final String token;

	/**
	 * Paper thickness in centimeters per page.
	 */
	private final double paperThicknessCm;

	/**
	 * Creates one print-interior descriptor.
	 *
	 * @param token command-line token
	 * @param paperThicknessInches paper thickness in inches per page
	 */
	Interior(String token, double paperThicknessInches) {
		this.token = token;
		this.paperThicknessCm = paperThicknessInches * CENTIMETERS_PER_INCH;
	}

	/**
	 * Returns the stable command-line token.
	 *
	 * @return command-line token
	 */
	public String token() {
		return token;
	}

	/**
	 * Returns the paper thickness used for spine-width calculation.
	 *
	 * @return paper thickness in centimeters per page
	 */
	public double getPaperThicknessCm() {
		return paperThicknessCm;
	}

	/**
	 * Parses a print-interior token.
	 *
	 * @param value raw token
	 * @return parsed interior, defaulting to white black-and-white paper when blank
	 */
	public static Interior parse(String value) {
		if (value == null || value.isBlank()) {
			return WHITE_PAPER_BLACK_AND_WHITE;
		}
		String compact = compact(value);
		for (Interior interior : values()) {
			if (compact.equals(compact(interior.token)) || compact.equals(compact(interior.name()))) {
				return interior;
			}
		}
		return switch (compact) {
			case "whitebw", "whiteblackandwhite", "bw", "blackandwhite" -> WHITE_PAPER_BLACK_AND_WHITE;
			case "whitestandard", "whitecolor", "standardcolor" -> WHITE_PAPER_STANDARD_COLOR;
			case "whitepremium", "premiumcolor" -> WHITE_PAPER_PREMIUM_COLOR;
			case "creambw", "creamblackandwhite" -> CREAM_PAPER_BLACK_AND_WHITE;
			default -> throw new IllegalArgumentException("unknown cover interior: " + value);
		};
	}

	/**
	 * Compacts user-facing tokens for tolerant comparisons.
	 *
	 * @param value source token
	 * @return lowercase alphanumeric token
	 */
	private static String compact(String value) {
		return value.toLowerCase(Locale.ROOT)
				.replace("_", "")
				.replace("-", "")
				.replace(" ", "")
				.replace("/", "")
				.replace("&", "and");
	}
}
