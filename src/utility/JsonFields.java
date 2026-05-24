package utility;

import java.util.ArrayList;
import java.util.List;

/**
 * Small whitespace-tolerant JSON field reader for manifest-style objects.
 *
 * <p>
 * This is not a full JSON parser. It is intended for repo manifests that need
 * tolerant scalar and string-array field extraction without introducing a JSON
 * dependency.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
@SuppressWarnings({ "java:S127", "java:S135", "java:S3776" })
public final class JsonFields {

	/**
	 * Utility class; prevent instantiation.
	 */
	private JsonFields() {
		// utility
	}

	/**
	 * Reads a string field from a JSON object.
	 *
	 * @param json source object text
	 * @param name field name
	 * @return parsed string, or null when absent or not a string
	 */
	public static String stringField(String json, String name) {
		int start = valueStart(json, name);
		String source = safe(json);
		if (start < 0 || start >= source.length() || source.charAt(start) != '"') {
			return null;
		}
		StringToken token = readString(source, start);
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
	public static int intField(String json, String name, int fallback) {
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
	public static double doubleField(String json, String name, double fallback) {
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
	 * Reads a boolean field from a JSON object.
	 *
	 * @param json source object text
	 * @param name field name
	 * @param fallback fallback value
	 * @return parsed boolean or fallback
	 */
	public static boolean booleanField(String json, String name, boolean fallback) {
		int start = valueStart(json, name);
		if (start < 0) {
			return fallback;
		}
		String source = safe(json);
		if (startsWithLiteral(source, start, "true")) {
			return true;
		}
		if (startsWithLiteral(source, start, "false")) {
			return false;
		}
		return fallback;
	}

	/**
	 * Reads a string array field from a JSON object.
	 *
	 * @param json source object text
	 * @param name field name
	 * @return parsed strings, never null
	 */
	public static String[] stringArrayField(String json, String name) {
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
	public static String arrayField(String json, String name) {
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
	 * @param start number start index
	 * @param allowDecimal whether to allow floating-point syntax
	 * @return number token, or an empty string when invalid
	 */
	private static String numberToken(String source, int start, boolean allowDecimal) {
		int index = skipWhitespace(source, start);
		if (index >= source.length()) {
			return "";
		}
		int end = index;
		boolean decimal = false;
		boolean exponent = false;
		while (end < source.length()) {
			char ch = source.charAt(end);
			if (Character.isDigit(ch) || ch == '+' || ch == '-') {
				end++;
				continue;
			}
			if (allowDecimal && ch == '.' && !decimal && !exponent) {
				decimal = true;
				end++;
				continue;
			}
			if (allowDecimal && (ch == 'e' || ch == 'E') && !exponent) {
				exponent = true;
				end++;
				continue;
			}
			break;
		}
		return index == end ? "" : source.substring(index, end);
	}

	/**
	 * Skips ASCII whitespace.
	 *
	 * @param source source text
	 * @param index starting index
	 * @return first non-whitespace index
	 */
	private static int skipWhitespace(String source, int index) {
		int i = Math.max(0, index);
		while (i < source.length() && Character.isWhitespace(source.charAt(i))) {
			i++;
		}
		return i;
	}

	/**
	 * Checks whether a substring contains only hexadecimal digits.
	 *
	 * @param source source text
	 * @param start inclusive start
	 * @param end exclusive end
	 * @return true when the range is hexadecimal
	 */
	private static boolean isHex(String source, int start, int end) {
		for (int i = start; i < end; i++) {
			char ch = source.charAt(i);
			boolean digit = ch >= '0' && ch <= '9';
			boolean lower = ch >= 'a' && ch <= 'f';
			boolean upper = ch >= 'A' && ch <= 'F';
			if (!(digit || lower || upper)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Checks whether the source starts with a JSON literal at the given index.
	 *
	 * @param source source JSON text
	 * @param start candidate literal start
	 * @param literal literal text
	 * @return true when the literal is present and token-delimited
	 */
	private static boolean startsWithLiteral(String source, int start, String literal) {
		if (literal == null || literal.isEmpty()) {
			return false;
		}
		int end = start + literal.length();
		if (start < 0 || end > source.length()) {
			return false;
		}
		if (!source.regionMatches(start, literal, 0, literal.length())) {
			return false;
		}
		if (end >= source.length()) {
			return true;
		}
		char next = source.charAt(end);
		return Character.isWhitespace(next) || next == ',' || next == '}' || next == ']';
	}

	/**
	 * Returns a non-null source string.
	 *
	 * @param json raw JSON
	 * @return safe string
	 */
	private static String safe(String json) {
		return json == null ? "" : json;
	}

	/**
	 * Parsed JSON string token with end offset.
	 *
	 * @param value decoded string value
	 * @param end first index after the closing quote
	 */
	private record StringToken(String value, int end) {
	}
}
