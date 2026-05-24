/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package application.gui.workbench.ui;

import java.awt.Color;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * Lightweight TOML syntax highlighter for read-only workbench config previews.
 */
public final class TomlHighlighter {

    /**
     * Prevents instantiation.
     */
    private TomlHighlighter() {
        // utility
    }

    /**
     * Replaces the pane contents with syntax-highlighted TOML text.
     *
     * @param pane destination text pane
     * @param text TOML source
     */
    public static void apply(JTextPane pane, String text) {
        String source = text == null ? "" : text;
        StyledDocument doc = pane.getStyledDocument();
        try {
            doc.remove(0, doc.getLength());
            doc.insertString(0, source, attrs(Theme.TEXT, false, false));
            if (!source.isEmpty()) {
                doc.setCharacterAttributes(0, source.length(),
                        attrs(Theme.TEXT, false, false), true);
            }
            highlight(doc, source);
            pane.setCaretPosition(0);
        } catch (BadLocationException ex) {
            pane.setText(source);
            pane.setCaretPosition(0);
        }
    }

    /**
     * Applies line-oriented TOML styles.
     *
     * @param doc document
     * @param source source text
     * @throws BadLocationException when the document rejects a range
     */
    private static void highlight(StyledDocument doc, String source)
            throws BadLocationException {
        int lineStart = 0;
        boolean inMultilineString = false;
        while (lineStart < source.length()) {
            int newline = source.indexOf('\n', lineStart);
            int lineEnd = newline >= 0 ? newline : source.length();
            String line = source.substring(lineStart, lineEnd);
            inMultilineString = highlightLine(doc, line, lineStart, inMultilineString);
            lineStart = newline >= 0 ? newline + 1 : source.length();
        }
    }

    /**
     * Highlights one TOML source line.
     *
     * @param doc document
     * @param line line text without trailing newline
     * @param offset line start offset
     * @param inMultilineString true when a previous line opened a triple-quote
     * @return updated multiline-string state
     * @throws BadLocationException when the document rejects a range
     */
    private static boolean highlightLine(StyledDocument doc, String line, int offset,
            boolean inMultilineString) throws BadLocationException {
        if (line.isEmpty()) {
            return inMultilineString;
        }
        if (inMultilineString) {
            int close = line.indexOf("\"\"\"");
            int len = close < 0 ? line.length() : close + 3;
            set(doc, offset, len, attrs(stringColor(), false, false));
            if (close < 0) {
                return true;
            }
    return highlightLine(doc, line.substring(close + 3),
                    offset + close + 3, false);
        }

        int comment = commentIndex(line);
        int codeEnd = comment >= 0 ? comment : line.length();
        if (comment >= 0) {
            set(doc, offset + comment, line.length() - comment,
                    attrs(Theme.MUTED, false, true));
        }
        int first = firstNonWhitespace(line, 0, codeEnd);
        int last = lastNonWhitespace(line, first, codeEnd);
        if (first >= last) {
            return false;
        }
        if (line.charAt(first) == '[') {
            set(doc, offset + first, last - first, attrs(sectionColor(), true, false));
            return false;
        }
        int equals = unquotedEquals(line, first, codeEnd);
        if (equals < 0) {
            return false;
        }
        int keyEnd = lastNonWhitespace(line, first, equals);
        if (keyEnd > first) {
            set(doc, offset + first, keyEnd - first, attrs(keyColor(), true, false));
        }
    return highlightValue(doc, line, offset, equals + 1, codeEnd);
    }

    /**
     * Highlights value tokens in a key/value line.
     *
     * @param doc document
     * @param line line text
     * @param offset line start offset
     * @param start value start
     * @param end value end
     * @return true when the line opens an unterminated triple-quote string
     */
    private static boolean highlightValue(StyledDocument doc, String line, int offset,
            int start, int end) {
        int i = start;
        while (i < end) {
            char ch = line.charAt(i);
            if (Character.isWhitespace(ch) || ch == ',' || ch == '[' || ch == ']') {
                i++;
                continue;
            }
            if (line.startsWith("\"\"\"", i)) {
                int close = line.indexOf("\"\"\"", i + 3);
                if (close < 0) {
                    set(doc, offset + i, end - i, attrs(stringColor(), false, false));
                    return true;
                }
                set(doc, offset + i, close + 3 - i, attrs(stringColor(), false, false));
                i = close + 3;
                continue;
            }
            if (ch == '"' || ch == '\'') {
                int close = closingQuote(line, i, end, ch);
                set(doc, offset + i, close - i, attrs(stringColor(), false, false));
                i = close;
                continue;
            }
            int tokenEnd = tokenEnd(line, i, end);
            String token = line.substring(i, tokenEnd);
            if (isBooleanLiteral(token)) {
                set(doc, offset + i, tokenEnd - i, attrs(booleanColor(), true, false));
            } else if (isNumberLiteral(token)) {
                set(doc, offset + i, tokenEnd - i, attrs(numberColor(), false, false));
            }
            i = tokenEnd;
        }
        return false;
    }

    /**
     * Returns a text-attribute set.
     *
     * @param color foreground color
     * @param bold true for bold text
     * @param italic true for italic text
     * @return attributes
     */
    private static MutableAttributeSet attrs(Color color, boolean bold, boolean italic) {
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setForeground(attrs, color);
        StyleConstants.setBold(attrs, bold);
        StyleConstants.setItalic(attrs, italic);
        return attrs;
    }

    /**
     * Returns the active key color.
     *
     * @return key color
     */
    private static Color keyColor() {
        return Theme.ACCENT;
    }

    /**
     * Returns the active section-header color.
     *
     * @return section color
     */
    private static Color sectionColor() {
        return Theme.STATUS_INFO_TEXT;
    }

    /**
     * Returns the active string-literal color.
     *
     * @return string color
     */
    private static Color stringColor() {
        return Theme.STATUS_SUCCESS_TEXT;
    }

    /**
     * Returns the active number-literal color.
     *
     * @return number color
     */
    private static Color numberColor() {
        return Theme.STATUS_ERROR_TEXT;
    }

    /**
     * Returns the active boolean/null-literal color.
     *
     * @return boolean color
     */
    private static Color booleanColor() {
        return Theme.STATUS_WARNING_TEXT;
    }

    /**
     * Applies attributes to a document range when non-empty.
     *
     * @param doc document
     * @param start start offset
     * @param length range length
     * @param attrs attributes
     */
    private static void set(StyledDocument doc, int start, int length,
            MutableAttributeSet attrs) {
        if (length > 0) {
            doc.setCharacterAttributes(start, length, attrs, false);
        }
    }

    /**
     * Finds a TOML comment marker outside quoted strings.
     *
     * @param line line text
     * @return comment offset, or -1
     */
    private static int commentIndex(String line) {
        boolean inQuote = false;
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuote) {
                if (ch == '\\' && quote == '"' && i + 1 < line.length()) {
                    i++;
                } else if (ch == quote) {
                    inQuote = false;
                }
            } else if (ch == '"' || ch == '\'') {
                inQuote = true;
                quote = ch;
            } else if (ch == '#') {
                return i;
            }
        }
        return -1;
    }

    /**
     * Finds an equals sign outside quoted strings.
     *
     * @param line line text
     * @param start search start
     * @param end search end
     * @return equals offset, or -1
     */
    private static int unquotedEquals(String line, int start, int end) {
        boolean inQuote = false;
        char quote = 0;
        for (int i = start; i < end; i++) {
            char ch = line.charAt(i);
            if (inQuote) {
                if (ch == '\\' && quote == '"' && i + 1 < end) {
                    i++;
                } else if (ch == quote) {
                    inQuote = false;
                }
            } else if (ch == '"' || ch == '\'') {
                inQuote = true;
                quote = ch;
            } else if (ch == '=') {
                return i;
            }
        }
        return -1;
    }

    /**
     * Finds the first non-whitespace character in a range.
     *
     * @param text text
     * @param start range start
     * @param end range end
     * @return index or end
     */
    private static int firstNonWhitespace(String text, int start, int end) {
        int i = start;
        while (i < end && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    /**
     * Finds the exclusive end of the last non-whitespace character in a range.
     *
     * @param text text
     * @param start range start
     * @param end range end
     * @return exclusive end
     */
    private static int lastNonWhitespace(String text, int start, int end) {
        int i = end;
        while (i > start && Character.isWhitespace(text.charAt(i - 1))) {
            i--;
        }
        return i;
    }

    /**
     * Finds the exclusive end of a quoted string.
     *
     * @param line line text
     * @param start opening quote offset
     * @param end line end
     * @param quote quote character
     * @return exclusive end
     */
    private static int closingQuote(String line, int start, int end, char quote) {
        int i = start + 1;
        while (i < end) {
            char ch = line.charAt(i);
            if (ch == '\\' && quote == '"' && i + 1 < end) {
                i += 2;
            } else if (ch == quote) {
                return i + 1;
            } else {
                i++;
            }
        }
        return end;
    }

    /**
     * Finds the exclusive end of a bare token.
     *
     * @param line line text
     * @param start token start
     * @param end line end
     * @return exclusive token end
     */
    private static int tokenEnd(String line, int start, int end) {
        int i = start;
        while (i < end) {
            char ch = line.charAt(i);
            if (Character.isWhitespace(ch) || ch == ',' || ch == ']' || ch == '[') {
                break;
            }
            i++;
        }
        return i;
    }

    /**
     * Returns whether a bare token is a TOML boolean/null literal.
     *
     * @param token token text
     * @return true when boolean/null
     */
    private static boolean isBooleanLiteral(String token) {
        return "true".equals(token) || "false".equals(token) || "null".equals(token);
    }

    /**
     * Returns whether a bare token looks like a TOML number.
     *
     * @param token token text
     * @return true when numeric
     */
    private static boolean isNumberLiteral(String token) {
        return token.matches("[+-]?(?:0|[1-9][0-9_]*)(?:\\.[0-9_]+)?(?:[eE][+-]?[0-9_]+)?");
    }
}
