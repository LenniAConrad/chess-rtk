package chess.tag;

import static chess.tag.core.Literals.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a single parsed tag line and its extracted fields.
 * <p>
 * The parser splits a raw tag string into a normalized family name and a
 * stable map of key-value fields for later identity and diff operations.
 * </p>
 * @author Lennart A. Conrad
 * @since 2026
 */
final class Line {

    /**
     * The original raw tag text, trimmed and normalized for parsing.
     */
    final String raw;

    /**
     * The uppercased family prefix extracted from the raw line.
     */
    final String family;

    /**
     * The parsed key-value fields in encounter order.
     */
    final Map<String, String> fields;

    /**
     * Parses a raw tag line into family and field components.
     *
     * @param raw the raw tag line to parse
     */
    Line(String raw) {
        this.raw = raw == null ? EMPTY : raw.trim();
        int idx = this.raw.indexOf(COLON);
        if (idx <= 0) {
            this.family = EMPTY;
            this.fields = Map.of();
            return;
        }
        this.family = this.raw.substring(0, idx).trim().toUpperCase();
        String rest = this.raw.substring(idx + 1).trim();
        this.fields = parseFields(rest);
    }

    /**
     * Parses the field segment after the family prefix.
     * <p>
     * Tokens are split on whitespace outside quotes, then interpreted as
     * key-value pairs separated by {@code =}.
     * </p>
     *
     * @param rest the field text after the family separator
     * @return a map of parsed fields, or an empty map when no fields are present
     */
    private static Map<String, String> parseFields(String rest) {
        if (rest == null || rest.isEmpty()) {
            return Map.of();
        }
        List<String> tokens = splitTokens(rest);
        Map<String, String> out = new LinkedHashMap<>();
        for (String token : tokens) {
            int eq = token.indexOf(EQUAL_SIGN);
            if (eq <= 0) {
                continue;
            }
            String key = token.substring(0, eq).trim();
            String value = token.substring(eq + 1).trim();
            if (value.startsWith(String.valueOf(QUOTE)) && value.endsWith(String.valueOf(QUOTE)) && value.length() >= 2) {
                value = unescape(value.substring(1, value.length() - 1));
            }
            if (!key.isEmpty()) {
                out.put(key, value);
            }
        }
        return out;
    }

    /**
     * Splits a field segment into tokens while preserving quoted values.
     *
     * @param input the raw field text to tokenize
     * @return the list of tokens found in the input
     */
    private static List<String> splitTokens(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;
        boolean escape = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (escapeChar(current, c, escape)) {
                escape = false;
            } else if (c == BACKSLASH) {
                current.append(c);
                escape = inQuote;
            } else if (c == QUOTE) {
                inQuote = !inQuote;
                current.append(c);
            } else if (Character.isWhitespace(c) && !inQuote) {
                flushToken(tokens, current);
            } else {
                current.append(c);
            }
        }
        flushToken(tokens, current);
        return tokens;
    }

    /**
     * Appends the current character when an escape sequence is active.
     *
     * @param current the token buffer currently being built
     * @param c the character under consideration
     * @param escape whether the previous character was a backslash inside quotes
     * @return {@code true} when the character was consumed as part of an escape
     *         sequence
     */
    private static boolean escapeChar(StringBuilder current, char c, boolean escape) {
        if (!escape) {
            return false;
        }
        current.append(c);
        return true;
    }

    /**
     * Moves the buffered token into the token list and resets the buffer.
     *
     * @param tokens the destination token list
     * @param current the token buffer to flush
     */
    private static void flushToken(List<String> tokens, StringBuilder current) {
        if (!current.isEmpty()) {
            tokens.add(current.toString());
            current.setLength(0);
        }
    }

    /**
     * Removes surrounding quotes and unescapes embedded quote characters.
     *
     * @param value the quoted value text to normalize
     * @return the unescaped inner string
     */
    private static String unescape(String value) {
        return value.replace(ESCAPED_QUOTE, String.valueOf(QUOTE)).replace(ESCAPED_BACKSLASH,
                String.valueOf(BACKSLASH));
    }
}
