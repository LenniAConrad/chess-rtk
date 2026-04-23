package chess.book.render;

import java.util.regex.Pattern;

/**
 * Formats chess movetext for book and PDF output.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class MoveText {

	/**
	 * White king figurine used by standard figurine algebraic notation.
	 */
	private static final String KING = "\u2654";

	/**
	 * White queen figurine used by standard figurine algebraic notation.
	 */
	private static final String QUEEN = "\u2655";

	/**
	 * White rook figurine used by standard figurine algebraic notation.
	 */
	private static final String ROOK = "\u2656";

	/**
	 * White bishop figurine used by standard figurine algebraic notation.
	 */
	private static final String BISHOP = "\u2657";

	/**
	 * White knight figurine used by standard figurine algebraic notation.
	 */
	private static final String KNIGHT = "\u2658";

	/**
	 * White pawn figurine used when explicit pawn letters appear in source text.
	 */
	private static final String PAWN = "\u2659";

	/**
	 * Multiplication sign used for rendered capture text.
	 */
	private static final String CAPTURE = "\u00D7";

	/**
	 * Pattern for ordinary SAN move tokens, including disambiguation and
	 * promotion.
	 */
	private static final Pattern SAN_MOVE = Pattern.compile(
			"[KQRBNP]?[a-h]?[1-8]?x?[a-h][1-8](?:=[QRBN])?[+#]?");

	/**
	 * Pattern for castling move tokens.
	 */
	private static final Pattern CASTLING_MOVE = Pattern.compile("[O0]-[O0](?:-[O0])?[+#]?");

	/**
	 * Utility class; prevent instantiation.
	 */
	private MoveText() {
		// utility
	}

	/**
	 * Converts SAN-like tokens in a text run to figurine algebraic notation.
	 *
	 * @param text source movetext or mixed text
	 * @return text with SAN-like tokens converted to figurines
	 */
	public static String figurine(String text) {
		if (text == null) {
			return "";
		}
		if (text.isBlank()) {
			return text;
		}

		StringBuilder result = new StringBuilder(text.length());
		StringBuilder token = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char ch = text.charAt(i);
			if (Character.isWhitespace(ch)) {
				appendFormattedToken(result, token);
				result.append(ch);
			} else {
				token.append(ch);
			}
		}
		appendFormattedToken(result, token);
		return result.toString();
	}

	/**
	 * Appends a pending token after applying figurine formatting when applicable.
	 *
	 * @param result output buffer
	 * @param token pending token buffer
	 */
	private static void appendFormattedToken(StringBuilder result, StringBuilder token) {
		if (token.isEmpty()) {
			return;
		}
		result.append(formatToken(token.toString()));
		token.setLength(0);
	}

	/**
	 * Converts one token when it is a SAN token, preserving surrounding
	 * punctuation.
	 *
	 * @param token source token
	 * @return formatted token
	 */
	private static String formatToken(String token) {
		int start = 0;
		int end = token.length();
		while (start < end && isLeadingWrapper(token.charAt(start))) {
			start++;
		}
		while (end > start && isTrailingWrapper(token.charAt(end - 1))) {
			end--;
		}

		String prefix = token.substring(0, start);
		String core = token.substring(start, end);
		String suffix = token.substring(end);
		String formatted = formatCore(core);
		return formatted == null ? token : prefix + formatted + suffix;
	}

	/**
	 * Converts one unwrapped SAN core.
	 *
	 * @param core token core without surrounding punctuation
	 * @return formatted core, or null when the core is not SAN-like
	 */
	private static String formatCore(String core) {
		if (core.isBlank()) {
			return null;
		}

		int annotationStart = core.length();
		while (annotationStart > 0 && isAnnotationMark(core.charAt(annotationStart - 1))) {
			annotationStart--;
		}

		String move = core.substring(0, annotationStart);
		String annotation = core.substring(annotationStart);
		if (CASTLING_MOVE.matcher(move).matches()) {
			return move.replace('0', 'O') + annotation;
		}
		if (!SAN_MOVE.matcher(move).matches()) {
			return null;
		}
		return convertSanLetters(move) + annotation;
	}

	/**
	 * Converts SAN piece letters and captures inside a validated move token.
	 *
	 * @param move SAN move token
	 * @return figurine move token
	 */
	private static String convertSanLetters(String move) {
		StringBuilder builder = new StringBuilder(move.length() + 2);
		for (int i = 0; i < move.length(); i++) {
			builder.append(convertSanCharacter(move.charAt(i)));
		}
		return builder.toString();
	}

	/**
	 * Converts one SAN character to its figurine equivalent when available.
	 *
	 * @param ch SAN character
	 * @return replacement text for the character
	 */
	private static String convertSanCharacter(char ch) {
		return switch (ch) {
			case 'K' -> KING;
			case 'Q' -> QUEEN;
			case 'R' -> ROOK;
			case 'B' -> BISHOP;
			case 'N' -> KNIGHT;
			case 'P' -> PAWN;
			case 'x' -> CAPTURE;
			default -> Character.toString(ch);
		};
	}

	/**
	 * Returns whether a character is leading punctuation around a token.
	 *
	 * @param ch character to inspect
	 * @return true when the character can precede a SAN token
	 */
	private static boolean isLeadingWrapper(char ch) {
		return ch == '(' || ch == '[' || ch == '{' || ch == '"' || ch == '\'';
	}

	/**
	 * Returns whether a character is trailing punctuation around a token.
	 *
	 * @param ch character to inspect
	 * @return true when the character can follow a SAN token
	 */
	private static boolean isTrailingWrapper(char ch) {
		return ch == ')' || ch == ']' || ch == '}' || ch == '"' || ch == '\''
				|| ch == ',' || ch == ';' || ch == ':';
	}

	/**
	 * Returns whether a character is a SAN annotation suffix.
	 *
	 * @param ch character to inspect
	 * @return true for annotation punctuation
	 */
	private static boolean isAnnotationMark(char ch) {
		return ch == '!' || ch == '?';
	}
}
