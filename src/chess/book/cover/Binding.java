package chess.book.cover;

import java.util.Locale;

/**
 * Enumerates the cover binding layouts supported by the book-cover renderer.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public enum Binding {

	/**
	 * Full wraparound paperback cover with back, spine, and front panels.
	 */
	PAPERBACK("paperback"),

	/**
	 * Full wraparound hardcover case-laminate cover with wrap and hinge space.
	 */
	HARDCOVER("hardcover"),

	/**
	 * Front-only electronic cover.
	 */
	EBOOK("ebook");

	/**
	 * Stable command-line token.
	 */
	private final String token;

	/**
	 * Creates one binding descriptor.
	 *
	 * @param token command-line token
	 */
	Binding(String token) {
		this.token = token;
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
	 * Parses a binding token.
	 *
	 * @param value raw token
	 * @return parsed binding, defaulting to paperback when blank
	 */
	public static Binding parse(String value) {
		if (value == null || value.isBlank()) {
			return PAPERBACK;
		}
		String normalized = value.trim().toLowerCase(Locale.ROOT)
				.replace("_", "")
				.replace("-", "")
				.replace(" ", "");
		return switch (normalized) {
			case "paperback", "paper", "pb" -> PAPERBACK;
			case "hardcover", "hard", "case", "caselaminate", "hc" -> HARDCOVER;
			case "ebook", "electronic", "front" -> EBOOK;
			default -> throw new IllegalArgumentException("unknown cover binding: " + value);
		};
	}
}
