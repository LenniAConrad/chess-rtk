package chess.nn.t5;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses tag lists supplied either as newline-separated text or JSON arrays.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class TagInputParser {
  
  /**
   * Parses the provided input into tag strings.
   *
   * @param input raw input text
   * @return extracted tag list
   */
  public List<String> parse(String input) {
    if (input == null) {
      return List.of();
    }
    String trimmed = input.trim();
    if (trimmed.isEmpty()) {
      return List.of();
    }
    if (trimmed.startsWith("[")) {
      List<String> jsonParsed = parseJsonArray(trimmed);
      if (!jsonParsed.isEmpty()) {
        return jsonParsed;
      }
    }
    return parseLines(trimmed);
  }

  /**
   * Splits newline-separated input into trimmed tags.
   *
   * @param input trimmed input
   * @return line-based tags
   */
  private List<String> parseLines(String input) {
    String[] lines = input.split("\\r?\\n");
    List<String> tags = new ArrayList<>();
    for (String line : lines) {
      String trimmed = line.trim();
      if (!trimmed.isEmpty()) {
        tags.add(trimmed);
      }
    }
    return tags;
  }

  /**
   * Attempts to parse a JSON array of strings.
   *
   * @param input JSON-like text
   * @return parsed tags or empty if invalid
   */
  private List<String> parseJsonArray(String input) {
    Cursor cursor = new Cursor(input);
    cursor.skipWhitespace();
    if (!cursor.consume('[')) {
      return List.of();
    }

    List<String> tags = new ArrayList<>();
    cursor.skipWhitespace();
    if (cursor.peek() == ']') {
      cursor.pos++;
      return tags;
    }

    while (cursor.pos < cursor.length()) {
      String value = parseQuotedString(cursor);
      if (value == null) {
        return List.of();
      }
      tags.add(value);
      cursor.skipWhitespace();
      char next = cursor.peek();
      if (next == ',') {
        cursor.pos++;
        cursor.skipWhitespace();
        continue;
      }
      if (next == ']') {
        cursor.pos++;
        return tags;
      }
      if (next == '\0') {
        return tags;
      }
      return List.of();
    }
    return tags;
  }

  /**
   * Reads a quoted string handling escapes.
   *
   * @param cursor parsing cursor
   * @return string or null on failure
   */
  private String parseQuotedString(Cursor cursor) {
    if (!cursor.consume('"')) {
      return null;
    }
    StringBuilder builder = new StringBuilder();
    while (cursor.pos < cursor.length()) {
      char ch = cursor.input.charAt(cursor.pos);
      if (ch == '"') {
        cursor.pos++;
        return builder.toString();
      }
      if (ch == '\\') {
        if (cursor.pos + 1 >= cursor.length()) {
          return null;
        }
        char escaped = cursor.input.charAt(cursor.pos + 1);
        builder.append(unescape(escaped));
        cursor.pos += 2;
      } else {
        builder.append(ch);
        cursor.pos++;
      }
    }
    return null;
  }

  /**
   * Converts a JSON-style escape into the real character.
   *
   * @param escaped escape character
   * @return actual character
   */
  private char unescape(char escaped) {
    switch (escaped) {
      case '"':
        return '"';
      case '\\':
        return '\\';
      case 'n':
        return '\n';
      case 'r':
        return '\r';
      case 't':
        return '\t';
      default:
        return escaped;
    }
  }

  /**
   * Lightweight cursor for manual JSON parsing.
   *
   * @since 2026
   * @author Lennart A. Conrad
   */
  private static final class Cursor {
    /**
     * Source string being parsed.
     */
    private final String input;
    /**
     * Current position within the input.
     */
    private int pos;

    private Cursor(String input) {
      this.input = input;
    }

    /**
     * @return input length
     */
    private int length() {
      return input.length();
    }

    /**
     * Advances past whitespace characters.
     */
    private void skipWhitespace() {
      while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
        pos++;
      }
    }

    /**
     * Consumes a specific character if it matches.
     *
     * @param expected character to match
     * @return true when consumed
     */
    private boolean consume(char expected) {
      if (pos < input.length() && input.charAt(pos) == expected) {
        pos++;
        return true;
      }
      return false;
    }

    /**
     * Peeks at the current character or returns null.
     *
     * @return current character or '\0'
     */
    private char peek() {
      if (pos >= input.length()) {
        return '\0';
      }
      return input.charAt(pos);
    }
  }
}
