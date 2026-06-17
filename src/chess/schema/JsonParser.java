package chess.schema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Minimal recursive-descent JSON parser used by the schema validator.
 *
 * <p>The parser accepts the RFC 8259 grammar with two long-term-friendly
 * choices: object iteration order is preserved (so error pointers can name
 * keys deterministically), and non-finite numeric literals are rejected
 * loudly. All other deviations from the spec throw
 * {@link JsonParseException} with the failing offset.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class JsonParser {

	/**
	 * Source text being parsed.
	 */
	private final String text;

	/**
	 * Current read pointer.
	 */
	private int index;

	/**
	 * Constructs a parser over the given text.
	 *
	 * @param text source JSON text
	 */
	private JsonParser(String text) {
		this.text = text;
		this.index = 0;
	}

	/**
	 * Parses the input text into a {@link JsonValue} tree.
	 *
	 * @param text source JSON text (must not be {@code null})
	 * @return parsed value
	 * @throws JsonParseException when the input does not encode a single JSON value
	 */
	public static JsonValue parse(String text) {
		if (text == null) {
			throw new JsonParseException(0, "input was null");
		}
		JsonParser parser = new JsonParser(text);
		parser.skipWhitespace();
		JsonValue value = parser.readValue();
		parser.skipWhitespace();
		if (parser.index != text.length()) {
			throw new JsonParseException(parser.index, "trailing content after JSON value");
		}
		return value;
	}

	/**
	 * Reads one JSON value at the current position.
	 *
	 * @return parsed value
	 */
	private JsonValue readValue() {
		if (index >= text.length()) {
			throw new JsonParseException(index, "expected JSON value, found end of input");
		}
		char c = text.charAt(index);
		switch (c) {
			case '{':
				return readObject();
			case '[':
				return readArray();
			case '"':
				return JsonValue.ofString(readString());
			case 't':
			case 'f':
				return readBoolean();
			case 'n':
				return readNull();
			default:
				if (c == '-' || (c >= '0' && c <= '9')) {
					return readNumber();
				}
				throw new JsonParseException(index, "unexpected character '" + c + "'");
		}
	}

	/**
	 * Reads a JSON object at the current position.
	 *
	 * @return parsed object value
	 */
	private JsonValue readObject() {
		expect('{');
		LinkedHashMap<String, JsonValue> entries = new LinkedHashMap<>();
		skipWhitespace();
		if (peek() == '}') {
			index++;
			return JsonValue.ofObject(entries);
		}
		while (true) {
			skipWhitespace();
			if (peek() != '"') {
				throw new JsonParseException(index, "expected string key inside object");
			}
			String key = readString();
			skipWhitespace();
			expect(':');
			skipWhitespace();
			JsonValue value = readValue();
			if (entries.containsKey(key)) {
				throw new JsonParseException(index, "duplicate key in JSON object: " + key);
			}
			entries.put(key, value);
			skipWhitespace();
			char next = peek();
			if (next == ',') {
				index++;
				continue;
			}
			if (next == '}') {
				index++;
				return JsonValue.ofObject(entries);
			}
			throw new JsonParseException(index, "expected ',' or '}' inside object");
		}
	}

	/**
	 * Reads a JSON array at the current position.
	 *
	 * @return parsed array value
	 */
	private JsonValue readArray() {
		expect('[');
		List<JsonValue> items = new ArrayList<>();
		skipWhitespace();
		if (peek() == ']') {
			index++;
			return JsonValue.ofArray(items);
		}
		while (true) {
			skipWhitespace();
			items.add(readValue());
			skipWhitespace();
			char next = peek();
			if (next == ',') {
				index++;
				continue;
			}
			if (next == ']') {
				index++;
				return JsonValue.ofArray(items);
			}
			throw new JsonParseException(index, "expected ',' or ']' inside array");
		}
	}

	/**
	 * Reads a JSON string literal at the current position.
	 *
	 * @return decoded string value
	 */
	private String readString() {
		expect('"');
		StringBuilder out = new StringBuilder();
		while (index < text.length()) {
			char c = text.charAt(index++);
			if (c == '"') {
				return out.toString();
			}
			if (c == '\\') {
				if (index >= text.length()) {
					throw new JsonParseException(index, "unterminated escape inside string");
				}
				char escape = text.charAt(index++);
				switch (escape) {
					case '"':
						out.append('"');
						break;
					case '\\':
						out.append('\\');
						break;
					case '/':
						out.append('/');
						break;
					case 'b':
						out.append('\b');
						break;
					case 'f':
						out.append('\f');
						break;
					case 'n':
						out.append('\n');
						break;
					case 'r':
						out.append('\r');
						break;
					case 't':
						out.append('\t');
						break;
					case 'u':
						out.append(readUnicodeEscape());
						break;
					default:
						throw new JsonParseException(index - 1, "invalid escape sequence \\" + escape);
				}
				continue;
			}
			if (c < 0x20) {
				throw new JsonParseException(index - 1,
						"unescaped control character (0x" + Integer.toHexString(c) + ") in string");
			}
			out.append(c);
		}
		throw new JsonParseException(index, "unterminated string literal");
	}

	/**
	 * Reads a JSON boolean literal at the current position.
	 *
	 * @return parsed boolean value
	 */
	private JsonValue readBoolean() {
		if (matchLiteral("true")) {
			return JsonValue.ofBool(true);
		}
		if (matchLiteral("false")) {
			return JsonValue.ofBool(false);
		}
		throw new JsonParseException(index, "expected boolean literal");
	}

	/**
	 * Reads a JSON null literal at the current position.
	 *
	 * @return parsed null value
	 */
	private JsonValue readNull() {
		if (matchLiteral("null")) {
			return JsonValue.ofNull();
		}
		throw new JsonParseException(index, "expected null literal");
	}

	/**
	 * Reads a JSON number literal at the current position.
	 *
	 * @return parsed number value
	 */
	private JsonValue readNumber() {
		int start = index;
		boolean integer = true;
		if (text.charAt(index) == '-') {
			index++;
		}
		while (index < text.length() && isDigit(text.charAt(index))) {
			index++;
		}
		if (index < text.length() && text.charAt(index) == '.') {
			integer = false;
			index++;
			while (index < text.length() && isDigit(text.charAt(index))) {
				index++;
			}
		}
		if (index < text.length() && (text.charAt(index) == 'e' || text.charAt(index) == 'E')) {
			integer = false;
			index++;
			if (index < text.length() && (text.charAt(index) == '+' || text.charAt(index) == '-')) {
				index++;
			}
			while (index < text.length() && isDigit(text.charAt(index))) {
				index++;
			}
		}
		String literal = text.substring(start, index);
		try {
			double value = Double.parseDouble(literal);
			if (Double.isNaN(value) || Double.isInfinite(value)) {
				throw new JsonParseException(start, "non-finite numeric literal: " + literal);
			}
			return JsonValue.ofNumber(value, integer);
		} catch (NumberFormatException ex) {
			throw new JsonParseException(start, "invalid numeric literal: " + literal);
		}
	}

	/**
	 * Reads four hex digits following a backslash-u escape and returns the decoded character.
	 *
	 * @return decoded unicode character
	 */
	private char readUnicodeEscape() {
		if (index + 4 > text.length()) {
			throw new JsonParseException(index, "truncated unicode escape");
		}
		int code = 0;
		for (int i = 0; i < 4; i++) {
			char c = text.charAt(index++);
			int digit = Character.digit(c, 16);
			if (digit < 0) {
				throw new JsonParseException(index - 1, "invalid hex digit in unicode escape: " + c);
			}
			code = (code << 4) | digit;
		}
		return (char) code;
	}

	/**
	 * Attempts to match a literal at the current position.
	 *
	 * @param literal literal token to match
	 * @return {@code true} when the literal matched and the cursor advanced
	 */
	private boolean matchLiteral(String literal) {
		if (index + literal.length() > text.length()) {
			return false;
		}
		for (int i = 0; i < literal.length(); i++) {
			if (text.charAt(index + i) != literal.charAt(i)) {
				return false;
			}
		}
		index += literal.length();
		return true;
	}

	/**
	 * Returns whether the given character is an ASCII digit.
	 *
	 * @param c character to inspect
	 * @return {@code true} when {@code c} is between {@code '0'} and {@code '9'}
	 */
	private static boolean isDigit(char c) {
		return c >= '0' && c <= '9';
	}

	/**
	 * Returns the current character without consuming it.
	 *
	 * @return character at the current index
	 */
	private char peek() {
		if (index >= text.length()) {
			throw new JsonParseException(index, "unexpected end of input");
		}
		return text.charAt(index);
	}

	/**
	 * Consumes the expected character at the current position.
	 *
	 * @param expected character to consume
	 */
	private void expect(char expected) {
		if (index >= text.length() || text.charAt(index) != expected) {
			throw new JsonParseException(index, "expected '" + expected + "'");
		}
		index++;
	}

	/**
	 * Advances over any RFC 8259 whitespace characters.
	 */
	private void skipWhitespace() {
		while (index < text.length()) {
			char c = text.charAt(index);
			if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
				index++;
			} else {
				return;
			}
		}
	}
}
