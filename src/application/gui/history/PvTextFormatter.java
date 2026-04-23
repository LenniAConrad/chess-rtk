package application.gui.history;

import java.awt.FontMetrics;

/**
 * Pure PV text formatting helpers used by GUI renderers.
  *
  * @since 2026
  * @author Lennart A. Conrad
 */
public final class PvTextFormatter {

	/**
	 * PvTextFormatter method.
	 */
	private PvTextFormatter() {
	}

	/**
	 * normalize method.
	 *
	 * @param moves parameter.
	 * @return return value.
	 */
	public static String normalize(String moves) {
		return moves == null ? "" : moves.trim().replaceAll("\\s+", " ");
	}

	/**
	 * formatForMode method.
	 *
	 * @param moves parameter.
	 * @param mode parameter.
	 * @param wrapTokens parameter.
	 * @param wrapWidthPx parameter.
	 * @param fm parameter.
	 * @return return value.
	 */
	public static String formatForMode(String moves, String mode, int wrapTokens, int wrapWidthPx, FontMetrics fm) {
		String text = normalize(moves);
		if (text.isEmpty()) {
			return "";
		}
		String normalizedMode = mode == null ? "Auto Word Wrap" : mode;
		if (normalizedMode.startsWith("Off")) {
			return text;
		}
		String[] tokens = text.split("\\s+");
		if (tokens.length == 0) {
			return text;
		}
		if (normalizedMode.startsWith("Token")) {
			int space = fm == null ? 4 : Math.max(1, fm.charWidth(' '));
			int maxWidth = Math.max(80, wrapWidthPx);
			StringBuilder out = new StringBuilder(text.length() + 16);
			int lineWidth = 0;
			int t = 0;
			for (String token : tokens) {
				if (token == null || token.isEmpty()) {
					continue;
				}
				int tokenWidth = fm == null ? token.length() * 7 : fm.stringWidth(token);
				if (t > 0) {
					if (lineWidth > 0 && lineWidth + space + tokenWidth > maxWidth) {
						out.append('\n');
						lineWidth = 0;
					} else if (lineWidth > 0) {
						out.append(' ');
						lineWidth += space;
					}
				}
				out.append(token);
				lineWidth += tokenWidth;
				t++;
			}
			return out.toString();
		}
		if (normalizedMode.startsWith("Fixed Tokens")) {
			int perLine = Math.max(4, wrapTokens);
			StringBuilder out = new StringBuilder(text.length() + 16);
			int t = 0;
			for (String token : tokens) {
				if (token == null || token.isEmpty()) {
					continue;
				}
				if (t > 0) {
					out.append((t % perLine) == 0 ? '\n' : ' ');
				}
				out.append(token);
				t++;
			}
			return out.toString();
		}
		if (normalizedMode.startsWith("Fixed Chars")) {
			int maxChars = 56;
			StringBuilder out = new StringBuilder(text.length() + 16);
			int lineLen = 0;
			int t = 0;
			for (String token : tokens) {
				if (token == null || token.isEmpty()) {
					continue;
				}
				int tokenLen = token.length();
				if (t > 0) {
					if (lineLen > 0 && lineLen + 1 + tokenLen > maxChars) {
						out.append('\n');
						lineLen = 0;
					} else if (lineLen > 0) {
						out.append(' ');
						lineLen += 1;
					}
				}
				out.append(token);
				lineLen += tokenLen;
				t++;
			}
			return out.toString();
		}
		return text;
	}

	/**
	 * collapse method.
	 *
	 * @param moves parameter.
	 * @param maxWidthPx parameter.
	 * @param fm parameter.
	 * @return return value.
	 */
	public static String collapse(String moves, int maxWidthPx, FontMetrics fm) {
		String text = normalize(moves);
		if (text.isEmpty()) {
			return "";
		}
		if (fm == null || maxWidthPx <= 0) {
			return text;
		}
		String[] tokens = text.split("\\s+");
		if (tokens.length <= 1) {
			return text;
		}
		StringBuilder visible = new StringBuilder(text.length());
		boolean overflow = false;
		for (String token : tokens) {
			if (token == null || token.isEmpty()) {
				continue;
			}
			int originalLen = visible.length();
			if (!visible.isEmpty()) {
				visible.append(' ');
			}
			visible.append(token);
			if (fm.stringWidth(visible.toString()) > maxWidthPx) {
				visible.setLength(originalLen);
				overflow = true;
				break;
			}
		}
		if (!overflow) {
			return text;
		}
		if (visible.isEmpty()) {
			String ellipsis = "\u2026";
			StringBuilder chunk = new StringBuilder();
			for (int i = 0; i < text.length(); i++) {
				chunk.append(text.charAt(i));
					if (fm.stringWidth(chunk + ellipsis) > maxWidthPx) {
						if (!chunk.isEmpty()) {
						chunk.setLength(chunk.length() - 1);
					}
					break;
				}
			}
			return chunk.isEmpty() ? ellipsis : chunk + ellipsis;
		}
		String result = visible + " \u2026";
		while (fm.stringWidth(result) > maxWidthPx) {
			int cut = visible.lastIndexOf(" ");
			if (cut <= 0) {
				return "\u2026";
			}
			visible.setLength(cut);
			result = visible + " \u2026";
		}
		return result;
	}

	/**
	 * canExpand method.
	 *
	 * @param moves parameter.
	 * @param maxWidthPx parameter.
	 * @param fm parameter.
	 * @return return value.
	 */
	public static boolean canExpand(String moves, int maxWidthPx, FontMetrics fm) {
		String text = normalize(moves);
		if (text.isEmpty()) {
			return false;
		}
		return !collapse(text, maxWidthPx, fm).equals(text);
	}

	/**
	 * totalPlies method.
	 *
	 * @param moves parameter.
	 * @return return value.
	 */
	public static int totalPlies(String moves) {
		String text = normalize(moves);
		if (text.isEmpty()) {
			return 1;
		}
		String[] tokens = text.split("\\s+");
		int count = 0;
		for (String token : tokens) {
			if (token != null && !token.isBlank()) {
				count++;
			}
		}
		return Math.max(1, count);
	}
}
