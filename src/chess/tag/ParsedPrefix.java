package chess.tag;




/**
 * Represents a candidate prefix and its normalized value.
 *
 * @author Lennart A. Conrad
 * @since 2026
 */
final class ParsedPrefix {

    /**
     * The prefix text to match.
     */
    final String key;

    /**
     * The value associated with the prefix.
     */
    final String value;

    /**
     * The remainder of the string after the prefix.
     */
    final String remainder;

    /**
     * Creates a prefix/value pair without a remainder.
     *
     * @param key   the prefix text
     * @param value the normalized value
     */
    ParsedPrefix(String key, String value) {
        this(key, value, null);
    }

    /**
     * Creates a prefix/value pair with a parsed remainder.
     *
     * @param key       the prefix text
     * @param value     the normalized value
     * @param remainder the remaining text after the prefix
     */
    ParsedPrefix(String key, String value, String remainder) {
        this.key = key;
        this.value = value;
        this.remainder = remainder;
    }
}
