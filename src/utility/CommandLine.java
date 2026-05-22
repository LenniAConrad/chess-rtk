/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package utility;

import java.util.ArrayList;
import java.util.List;

/**
 * Small shell-style command-line tokenizer used where ChessRTK accepts one
 * command per text row.
 *
 * <p>The tokenizer supports whitespace separation, single and double quotes,
 * and backslash escaping. It intentionally does not expand variables, globs,
 * command substitutions, or other shell features because commands are executed
 * through {@link ProcessBuilder}, not a shell.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class CommandLine {

    /**
     * Prevents instantiation.
     */
    private CommandLine() {
        // utility
    }

    /**
     * Splits a command line into argv tokens.
     *
     * @param input command line text
     * @return parsed tokens
     * @throws IllegalArgumentException when quotes or escapes are unfinished
     */
    public static List<String> split(String input) {
        if (input == null || input.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        char quote = 0;
        boolean inToken = false;
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (ch == '\\' && i + 1 < input.length() && isEscapable(input.charAt(i + 1))) {
                token.append(input.charAt(++i));
                inToken = true;
            } else if (quote != 0) {
                if (ch == quote) {
                    quote = 0;
                } else {
                    token.append(ch);
                }
                inToken = true;
            } else if (ch == '\'' || ch == '"') {
                quote = ch;
                inToken = true;
            } else if (Character.isWhitespace(ch)) {
                if (inToken) {
                    tokens.add(token.toString());
                    token.setLength(0);
                    inToken = false;
                }
            } else {
                token.append(ch);
                inToken = true;
            }
        }
        if (quote != 0) {
            throw new IllegalArgumentException("Unclosed quote in command line");
        }
        if (inToken) {
            tokens.add(token.toString());
        }
        return List.copyOf(tokens);
    }

    /**
     * Returns whether a backslash should escape the next character.
     *
     * @param ch next character
     * @return true when the backslash has escaping semantics
     */
    private static boolean isEscapable(char ch) {
        return Character.isWhitespace(ch) || ch == '\'' || ch == '"' || ch == '\\';
    }

    /**
     * Joins tokens into a display command with conservative double-quote escaping.
     *
     * @param tokens argv tokens
     * @return display command line
     */
    public static String join(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String token : tokens) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(quote(token));
        }
        return sb.toString();
    }

    /**
     * Quotes one token when whitespace or quoting characters require it.
     *
     * @param token raw token
     * @return display token
     */
    private static String quote(String token) {
        if (token == null || token.isEmpty()) {
            return "\"\"";
        }
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (Character.isWhitespace(c) || c == '"' || c == '\'' || c == '\\') {
                return "\"" + token.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
            }
        }
        return token;
    }
}
