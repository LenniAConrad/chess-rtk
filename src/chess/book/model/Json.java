package chess.book.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Small JSON field reader for book manifests.
 *
 * <p>
 * The repo-wide JSON helper is intentionally limited and expects compact
 * {@code "key":} spellings. Legacy book files often contain pretty-printed
 * fields, so the book loader keeps this whitespace-tolerant reader local to the
 * book package.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
@SuppressWarnings({ "java:S127", "java:S135", "java:S3776" })
final class Json {

	/**
	 * Utility class; prevent instantiation.
	 */
	private Json() {
		// utility
	}

	/**
	 * Reads a string field from a JSON object.
	 *
	 * @param json source object text
	 * @param name field name
	 * @return parsed string, or null when absent or not a string
	 */
	static String stringField(String json, String name) {
		int start = valueStart(json, name);
		if (start < 0 || start >= safe(json).length() || safe(json).charAt(start) != '"') {
			return null;
		}
		StringToken token = readString(safe(json), start);
		return token == null ? null : token.value;
	}

	/**
	 * Reads an integer field from a JSON object.
	 *
	 * @param json source object text
	 * @param name field name
	 * @param fallback fallback value
	 * @return parsed integer or fallback
	 */
	static int intField(String json, String name, int fallback) {
		int start = valueStart(json, name);
		if (start < 0) {
			return fallback;
		}
		String token = numberToken(safe(json), start, false);
		if (token.isEmpty()) {
			return fallback;
		}
		try {
			return (int) Long.parseLong(token);
		} catch (NumberFormatException ex) {
			return fallback;
		}
	}

	/**
	 * Reads a floating-point field from a JSON object.
	 *
	 * @param json source object text
	 * @param name field name
	 * @param fallback fallback value
	 * @return parsed double or fallback
	 */
	static double doubleField(String json, String name, double fallback) {
		int start = valueStart(json, name);
		if (start < 0) {
			return fallback;
		}
		String token = numberToken(safe(json), start, true);
		if (token.isEmpty()) {
			return fallback;
		}
		try {
			return Double.parseDouble(token);
		} catch (NumberFormatException ex) {
			return fallback;
		}
	}

	/**
	 * Reads a string array field from a JSON object.
	 *
	 * @param json source object text
	 * @param name field name
	 * @return parsed strings, never null
	 */
	static String[] stringArrayField(String json, String name) {
		String source = safe(json);
		int start = valueStart(source, name);
		if (start < 0 || start >= source.length() || source.charAt(start) != '[') {
			return new String[0];
		}

		List<String> values = new ArrayList<>();
		int index = start + 1;
		while (index < source.length()) {
			index = skipWhitespace(source, index);
			if (index >= source.length() || source.charAt(index) == ']') {
				break;
			}
			if (source.charAt(index) == ',') {
				index++;
				continue;
			}
			if (source.charAt(index) == '"') {
				StringToken token = readString(source, index);
				if (token == null) {
					break;
				}
				values.add(token.value);
				index = token.end;
			} else {
				index = skipValue(source, index);
			}
		}
		return values.toArray(new String[0]);
	}

	/**
	 * Reads a raw JSON array field from a JSON object.
	 *
	 * @param json source object text
	 * @param name field name
	 * @return raw array slice including brackets, or null when absent
	 */
	static String arrayField(String json, String name) {
		String source = safe(json);
		int start = valueStart(source, name);
		if (start < 0 || start >= source.length() || source.charAt(start) != '[') {
			return null;
		}
		int end = matchingDelimiter(source, start);
		return end < 0 ? null : source.substring(start, end + 1);
	}

	/**
	 * Locates the start of a top-level field value.
	 *
	 * @param json source object text
	 * @param name field name
	 * @return value start index or -1
	 */
	private static int valueStart(String json, String name) {
		String source = safe(json);
		if (name == null || name.isEmpty()) {
			return -1;
		}
		int index = skipWhitespace(source, 0);
		if (index < source.length() && source.charAt(index) == '{') {
			index++;
		}

		while (index < source.length()) {
			index = skipWhitespace(source, index);
			if (index >= source.length() || source.charAt(index) == '}') {
				return -1;
			}
			if (source.charAt(index) == ',') {
				index++;
				continue;
			}
			if (source.charAt(index) != '"') {
				index++;
				continue;
			}

			StringToken key = readString(source, index);
			if (key == null) {
				return -1;
			}
			int colon = skipWhitespace(source, key.end);
			if (colon >= source.length() || source.charAt(colon) != ':') {
				index = key.end;
				continue;
			}
			int value = skipWhitespace(source, colon + 1);
			if (name.equals(key.value)) {
				return value;
			}
			index = skipValue(source, value);
		}
		return -1;
	}

	/**
	 * Parses one JSON string token.
	 *
	 * @param source source text
	 * @param quoteIndex opening quote index
	 * @return token value and end index, or null when malformed
	 */
	private static StringToken readString(String source, int quoteIndex) {
		if (quoteIndex < 0 || quoteIndex >= source.length() || source.charAt(quoteIndex) != '"') {
			return null;
		}
		StringBuilder value = new StringBuilder();
		boolean escaped = false;
		for (int i = quoteIndex + 1; i < source.length(); i++) {
			char ch = source.charAt(i);
			if (escaped) {
				switch (ch) {
					case '"' -> value.append('"');
					case '\\' -> value.append('\\');
					case '/' -> value.append('/');
					case 'b' -> value.append('\b');
					case 'f' -> value.append('\f');
					case 'n' -> value.append('\n');
					case 'r' -> value.append('\r');
					case 't' -> value.append('\t');
					case 'u' -> {
						if (i + 4 < source.length() && isHex(source, i + 1, i + 5)) {
							value.append((char) Integer.parseInt(source.substring(i + 1, i + 5), 16));
							i += 4;
						} else {
							value.append('u');
						}
					}
					default -> value.append(ch);
				}
				escaped = false;
			} else if (ch == '\\') {
				escaped = true;
			} else if (ch == '"') {
				return new StringToken(value.toString(), i + 1);
			} else {
				value.append(ch);
			}
		}
		return null;
	}

	/**
	 * Skips one JSON value without validating every detail.
	 *
	 * @param source source text
	 * @param index value start index
	 * @return first index after the value
	 */
	private static int skipValue(String source, int index) {
		int start = skipWhitespace(source, index);
		if (start >= source.length()) {
			return start;
		}
		char ch = source.charAt(start);
		if (ch == '"') {
			StringToken token = readString(source, start);
			return token == null ? source.length() : token.end;
		}
		if (ch == '[' || ch == '{') {
			int end = matchingDelimiter(source, start);
			return end < 0 ? source.length() : end + 1;
		}
		int i = start;
		while (i < source.length()) {
			char current = source.charAt(i);
			if (current == ',' || current == '}' || current == ']') {
				break;
			}
			i++;
		}
		return i;
	}

	/**
	 * Finds the matching array or object delimiter.
	 *
	 * @param source source text
	 * @param start opening delimiter index
	 * @return closing delimiter index or -1
	 */
	private static int matchingDelimiter(String source, int start) {
		char open = source.charAt(start);
		char close = open == '[' ? ']' : '}';
		int depth = 0;
		boolean inString = false;
		boolean escaped = false;
		for (int i = start; i < source.length(); i++) {
			char ch = source.charAt(i);
			if (inString) {
				if (escaped) {
					escaped = false;
				} else if (ch == '\\') {
					escaped = true;
				} else if (ch == '"') {
					inString = false;
				}
				continue;
			}
			if (ch == '"') {
				inString = true;
			} else if (ch == open) {
				depth++;
			} else if (ch == close) {
				depth--;
				if (depth == 0) {
					return i;
				}
			}
		}
		return -1;
	}

	/**
	 * Reads a JSON number token.
	 *
	 * @param source source text
	 * @param start number start
	 * @param floating whether decimal and exponent markers are allowed
	 * @return number token or empty string
	 */
	private static String numberToken(String source, int start, boolean floating) {
		int index = skipWhitespace(source, start);
		int end = index;
		while (end < source.length()) {
			char ch = source.charAt(end);
			if (ch == '-' || ch == '+' || Character.isDigit(ch) || (floating && (ch == '.' || ch == 'e' || ch == 'E'))) {
				end++;
				continue;
			}
			break;
		}
		return end == index ? "" : source.substring(index, end);
	}

	/**
	 * Skips JSON whitespace.
	 *
	 * @param source source text
	 * @param index starting index
	 * @return first non-whitespace index
	 */
	private static int skipWhitespace(String source, int index) {
		int i = index;
		while (i < source.length() && Character.isWhitespace(source.charAt(i))) {
			i++;
		}
		return i;
	}

	/**
	 * Returns a non-null string.
	 *
	 * @param value source text
	 * @return source text or empty string
	 */
	private static String safe(String value) {
		return value == null ? "" : value;
	}

	/**
	 * Checks whether a substring contains only hex digits.
	 *
	 * @param source source text
	 * @param start inclusive start
	 * @param end exclusive end
	 * @return true when all characters are hex digits
	 */
	private static boolean isHex(String source, int start, int end) {
		for (int i = start; i < end; i++) {
			char ch = source.charAt(i);
			boolean digit = ch >= '0' && ch <= '9';
			boolean lower = ch >= 'a' && ch <= 'f';
			boolean upper = ch >= 'A' && ch <= 'F';
			if (!digit && !lower && !upper) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Parsed JSON string token.
	 */
	private static final class StringToken {

		/**
		 * Parsed string value.
		 */
		private final String value;

		/**
		 * Index after the closing quote.
		 */
		private final int end;

		/**
		 * Creates one token.
		 *
		 * @param value parsed value
		 * @param end index after closing quote
		 */
		private StringToken(String value, int end) {
			this.value = value;
			this.end = end;
		}
	}
}
