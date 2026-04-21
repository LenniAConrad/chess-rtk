package chess.book.model;

import java.util.Locale;

/**
 * Enumerates the book languages supported by the native chess-book renderer.
 *
 * <p>
 * The enum names are stable manifest tokens accepted by the native renderer.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
@SuppressWarnings("java:S115")
public enum Language {

	/**
	 * German-language book content.
	 */
	German(true),

	/**
	 * English-language book content.
	 */
	English(true),

	/**
	 * Spanish-language book content.
	 */
	Spanish(true),

	/**
	 * French-language book content.
	 */
	French(true),

	/**
	 * Chinese-language book content.
	 */
	Chinese(true),

	/**
	 * Russian-language book content.
	 */
	Russian(true),

	/**
	 * Italian-language book content.
	 */
	Italian(true),

	/**
	 * Turkish-language book content.
	 */
	Turkish(true),

	/**
	 * Swiss-German-language book content.
	 */
	SwissGerman(true),

	/**
	 * Japanese-language book content.
	 */
	Japanese(true),

	/**
	 * Hebrew-language book content.
	 */
	Hebrew(false),

	/**
	 * Portuguese-language book content.
	 */
	Portuguese(true),

	/**
	 * Korean-language book content.
	 */
	Korean(true),

	/**
	 * Arabic-language book content.
	 */
	Arabic(false);

	/**
	 * Indicates whether the language is laid out left-to-right.
	 */
	private final boolean leftToRight;

	/**
	 * Creates one language descriptor.
	 *
	 * @param leftToRight true when normal book text should flow left-to-right
	 */
	Language(boolean leftToRight) {
		this.leftToRight = leftToRight;
	}

	/**
	 * Returns whether the language uses left-to-right text flow.
	 *
	 * @return true when the language reads left-to-right
	 */
	public boolean isLeftToRight() {
		return leftToRight;
	}

	/**
	 * Parses a language name from book metadata.
	 *
	 * <p>
	 * The match is case-insensitive and falls back to English when the source value
	 * is missing or unknown.
	 * </p>
	 *
	 * @param value raw JSON field value
	 * @return parsed language enum, never null
	 */
	public static Language parse(String value) {
		if (value == null || value.isBlank()) {
			return English;
		}
		String needle = value.trim().toLowerCase(Locale.ROOT);
		for (Language language : values()) {
			if (language.name().toLowerCase(Locale.ROOT).equals(needle)) {
				return language;
			}
		}
		return English;
	}
}
