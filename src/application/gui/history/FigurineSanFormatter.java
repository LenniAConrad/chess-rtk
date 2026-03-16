package application.gui.history;

import java.awt.Font;

/**
 * SAN display helpers with optional figurine replacement.
  *
  * @since 2026
  * @author Lennart A. Conrad
 */
public final class FigurineSanFormatter {

	/**
	 * FigurineSanFormatter method.
	 */
	private FigurineSanFormatter() {
	}

	/**
	 * displayFont method.
	 *
	 * @param base parameter.
	 * @param figurineEnabled parameter.
	 * @return return value.
	 */
	public static Font displayFont(Font base, boolean figurineEnabled) {
		if (base == null || !figurineEnabled) {
			return base;
		}
		float size = Math.max(base.getSize2D() + 1f, base.getSize2D() * 1.12f);
		return base.deriveFont(Font.BOLD, size);
	}

	/**
	 * displaySan method.
	 *
	 * @param san parameter.
	 * @param figurineEnabled parameter.
	 * @return return value.
	 */
	public static String displaySan(String san, boolean figurineEnabled) {
		if (san == null || san.isBlank() || !figurineEnabled) {
			return san;
		}
		return figurineSanToken(san);
	}

	/**
	 * displaySanLine method.
	 *
	 * @param text parameter.
	 * @param figurineEnabled parameter.
	 * @return return value.
	 */
	public static String displaySanLine(String text, boolean figurineEnabled) {
		if (text == null || text.isBlank() || !figurineEnabled) {
			return text;
		}
		StringBuilder out = new StringBuilder(text.length() + 8);
		int i = 0;
		while (i < text.length()) {
			char ch = text.charAt(i);
			if (Character.isWhitespace(ch)) {
				out.append(ch);
				i++;
				continue;
			}
			int start = i;
			while (i < text.length() && !Character.isWhitespace(text.charAt(i))) {
				i++;
			}
			out.append(figurineSanToken(text.substring(start, i)));
		}
		return out.toString();
	}

	/**
	 * figurineSanToken method.
	 *
	 * @param token parameter.
	 * @return return value.
	 */
	private static String figurineSanToken(String token) {
		if (token == null || token.isEmpty()) {
			return token;
		}
		StringBuilder out = new StringBuilder(token.length() + 2);
		boolean atSanStart = true;
		for (int i = 0; i < token.length(); i++) {
			char ch = token.charAt(i);
			if (atSanStart) {
				if (Character.isDigit(ch) || ch == '.' || ch == '(' || ch == '[' || ch == '{') {
					out.append(ch);
					continue;
				}
				atSanStart = false;
				char piece = figurinePiece(ch);
				out.append(piece != 0 ? piece : ch);
				continue;
			}
			if (ch == '=' && i + 1 < token.length()) {
				char next = token.charAt(i + 1);
				char promo = figurinePiece(next);
				if (promo != 0) {
					out.append('=').append(promo);
					i++;
					continue;
				}
			}
			out.append(ch);
		}
		return out.toString();
	}

	private static char figurinePiece(char piece) {
		return switch (piece) {
			case 'K' -> '\u2654';
			case 'Q' -> '\u2655';
			case 'R' -> '\u2656';
			case 'B' -> '\u2657';
			case 'N' -> '\u2658';
			default -> 0;
		};
	}
}
