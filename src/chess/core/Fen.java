package chess.core;

/**
 * Shared helpers for Forsyth-Edwards Notation text handling.
 *
 * <p>
 * Parsing and serialization still belong to {@link Position}; this class keeps
 * lightweight text normalization in one core location so callers do not carry
 * package-local FEN cleanup rules.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Fen {

	/**
	 * Utility class; prevent instantiation.
	 */
	private Fen() {
		// utility
	}

	/**
	 * Normalizes FEN text before strict position parsing.
	 *
	 * <p>
	 * The normalizer trims leading/trailing whitespace, collapses internal
	 * whitespace runs, and accepts a legacy five-field form where the en-passant
	 * field is omitted but halfmove and fullmove clocks are present. In that case,
	 * the missing en-passant field is restored as {@code -}.
	 * </p>
	 *
	 * @param value raw FEN text
	 * @return normalized FEN text, or an empty string for null input
	 */
	public static String normalize(String value) {
		if (value == null) {
			return "";
		}
		String normalized = collapseWhitespace(value.trim());
		String[] fields = normalized.split(" ");
		if (fields.length == 5 && isUnsignedInteger(fields[3]) && isUnsignedInteger(fields[4])) {
			return fields[0] + " " + fields[1] + " " + fields[2] + " - " + fields[3] + " " + fields[4];
		}
		return normalized;
	}

	/**
	 * Collapses every whitespace run to one ASCII space.
	 *
	 * @param value trimmed source text
	 * @return source text with normalized whitespace
	 */
	private static String collapseWhitespace(String value) {
		if (value.isEmpty()) {
			return "";
		}
		StringBuilder result = new StringBuilder(value.length());
		boolean inWhitespace = false;
		for (int i = 0; i < value.length(); i++) {
			char ch = value.charAt(i);
			if (Character.isWhitespace(ch)) {
				inWhitespace = true;
			} else {
				if (inWhitespace && !result.isEmpty()) {
					result.append(' ');
				}
				result.append(ch);
				inWhitespace = false;
			}
		}
		return result.toString();
	}

	/**
	 * Checks whether a FEN clock field contains only decimal digits.
	 *
	 * @param value source field
	 * @return true when the field is a non-empty unsigned integer
	 */
	private static boolean isUnsignedInteger(String value) {
		if (value == null || value.isEmpty()) {
			return false;
		}
		for (int i = 0; i < value.length(); i++) {
			if (!Character.isDigit(value.charAt(i))) {
				return false;
			}
		}
		return true;
	}
}
