package application.gui.workbench.command;

import application.gui.workbench.ui.Theme;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.util.Locale;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import javax.swing.text.LayeredHighlighter;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.View;

/**
 * Console output view for the workbench. Beyond a plain text area it adds two
 * terminal behaviours:
 *
 * <ul>
 *   <li>carriage-return handling — a {@code \r} rewinds to the start of the
 *       current line so progress output redraws in place instead of stacking
 *       up hundreds of stale lines;</li>
 *   <li>line highlighting — command echoes, exit codes, and error lines are
 *       tinted so the eye finds them in a long run log.</li>
 * </ul>
 */
public final class Console extends JTextPane implements Theme.ConsoleLike {

    /**
     * Serialization identifier for Swing component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Text of the line currently being assembled (not yet terminated).
     */
    private final StringBuilder currentLine = new StringBuilder();

    /**
     * Write column within {@link #currentLine}; a {@code \r} resets it to 0.
     */
    private int column;

    /**
     * Document offset where {@link #currentLine} begins.
     */
    private int lineStart;

    /**
     * Centered guidance drawn while the console has no output yet.
     */
    private String placeholder = "";

    /**
     * Creates an empty console view.
     */
    public Console() {
        setEditable(false);
        setOpaque(true);
        applyConsoleTheme();
    }

    /**
     * Applies terminal colors after a workbench theme change.
     */
    @Override
    public void applyConsoleTheme() {
        setBackground(Theme.TERMINAL);
        setForeground(Theme.TERMINAL_TEXT);
        setCaretColor(Theme.TERMINAL_TEXT);
        setSelectionColor(Theme.TEXT_SELECTION);
        setSelectedTextColor(Theme.TERMINAL_TEXT);
        // Console-specific mono guarantees CLI progress-bar glyphs render.
        setFont(Theme.consoleMono(13));
        setBorder(Theme.pad(7, 9, 7, 9));
        repaint();
    }

    /**
     * Console lines never soft-wrap; long output scrolls horizontally like a
     * real terminal.
     *
     * @return false so the text pane keeps its natural width
     */
    @Override
    public boolean getScrollableTracksViewportWidth() {
        return getParent() == null || getUI().getPreferredSize(this).width <= getParent().getWidth();
    }

    /**
     * Clears all console output.
     */
    public void clearOutput() {
        setText("");
        getHighlighter().removeAllHighlights();
        currentLine.setLength(0);
        column = 0;
        lineStart = 0;
    }

    /**
     * Appends one visually separated section header. The text remains selectable
     * and copyable, while a full-width highlight behind the line replaces ASCII
     * fence markers in views such as the persisted-log browser.
     *
     * @param text header text without a trailing newline
     */
    public void appendSectionHeader(String text) {
        String line = text == null ? "" : text.stripTrailing();
        if (line.isEmpty()) {
            return;
        }
        if (currentLine.length() > 0 || column > 0) {
            commitNewline();
        }
        StyledDocument doc = getStyledDocument();
        try {
            int start = doc.getLength();
            doc.insertString(start, line + "\n", sectionHeaderStyle());
            int end = doc.getLength();
            getHighlighter().addHighlight(start, Math.max(start, end - 1),
                    new FullLineHighlightPainter(Theme.withAlpha(Theme.LINE, Theme.isDark() ? 80 : 55)));
            currentLine.setLength(0);
            column = 0;
            lineStart = doc.getLength();
        } catch (BadLocationException ignored) {
            // Offsets are derived from the document itself.
        }
    }

    /**
     * Appends terminal output, honouring carriage returns and newlines.
     *
     * @param text raw text chunk
     */
    public void appendOutput(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            // Strip ANSI escape sequences. Scripts like install.sh and many
            // CLI tools emit CSI codes ([ ... letter) for colour and
            // cursor moves. The Console is not a terminal — leaving the
            // codes in produced visible garbage like "[1;32m" instead of
            // the styled "loading screen" output the user expected.
            if (ch == '' && i + 1 < text.length()) {
                int next = text.charAt(i + 1);
                if (next == '[') {
                    int end = i + 2;
                    while (end < text.length()) {
                        char c = text.charAt(end);
                        if (c >= '@' && c <= '~') {
                            break;
                        }
                        end++;
                    }
                    i = end;
                    continue;
                }
                // Two-byte escapes (ESC + single byte) — skip both.
                i++;
                continue;
            }
            switch (ch) {
                case '\n' -> commitNewline();
                case '\r' -> column = 0;
                default -> writeChar(ch);
            }
        }
        flushLine();
        setCaretPosition(getDocument().getLength());
    }

    /**
     * Writes one visible character at the current column, overwriting any
     * character a carriage return rewound past.
     *
     * @param ch character to write
     */
    private void writeChar(char ch) {
        if (column < currentLine.length()) {
            currentLine.setCharAt(column, ch);
        } else {
            while (currentLine.length() < column) {
                currentLine.append(' ');
            }
            currentLine.append(ch);
        }
        column++;
    }

    /**
     * Commits the current line and starts a fresh one.
     */
    private void commitNewline() {
        flushLine();
        StyledDocument doc = getStyledDocument();
        try {
            doc.insertString(doc.getLength(), "\n", styleFor(currentLine.toString()));
        } catch (BadLocationException ignored) {
            // Offsets are derived from the document itself.
        }
        currentLine.setLength(0);
        column = 0;
        lineStart = doc.getLength();
    }

    /**
     * Rewrites the in-progress line in the document so the view matches
     * {@link #currentLine}, restyled for its current content.
     */
    private void flushLine() {
        StyledDocument doc = getStyledDocument();
        try {
            int end = doc.getLength();
            if (end > lineStart) {
                doc.remove(lineStart, end - lineStart);
            }
            doc.insertString(lineStart, currentLine.toString(), styleFor(currentLine.toString()));
        } catch (BadLocationException ignored) {
            // Offsets are derived from the document itself.
        }
    }

    /**
     * Picks the highlight style for a finished or in-progress line.
     *
     * @param line line text
     * @return attribute set
     */
    private SimpleAttributeSet styleFor(String line) {
        String trimmed = line.strip();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        String padded = ' ' + lower + ' ';
        if (trimmed.startsWith("$ ")) {
            return badge(Theme.STATUS_INFO_TEXT);
        }
        if (trimmed.startsWith("[exit 0")) {
            return badge(Theme.STATUS_SUCCESS_TEXT);
        }
        if (trimmed.startsWith("[exit ") || trimmed.startsWith("[error")) {
            return badge(Theme.STATUS_ERROR_TEXT);
        }
        if (trimmed.startsWith("[stopped]") || trimmed.startsWith("[")) {
            return badge(Theme.STATUS_WARNING_TEXT);
        }
        SimpleAttributeSet levelStyle = styleForLogLevel(trimmed);
        if (levelStyle != null) {
            return levelStyle;
        }
        if (lower.contains("error") || lower.contains("exception")
                || lower.contains("failed") || lower.contains("invalid")) {
            return style(Theme.STATUS_ERROR_TEXT, false);
        }
        if (padded.contains(" warning ") || padded.contains(" warn ")) {
            return style(Theme.STATUS_WARNING_TEXT, false);
        }
        if (padded.contains(" info ") || padded.contains(" debug ") || padded.contains(" trace ")) {
            return style(Theme.MUTED, false);
        }
        return style(Theme.TERMINAL_TEXT, false);
    }

    /**
     * Returns a style for Java log-level lines, including continuation lines
     * that start directly with the level token.
     *
     * @param trimmed line text without surrounding whitespace
     * @return style for a known log level, or null when none is present
     */
    private static SimpleAttributeSet styleForLogLevel(String trimmed) {
        String level = firstLogLevel(trimmed);
        if (level == null) {
            return null;
        }
        return switch (level) {
            case "SEVERE", "ERROR" -> style(Theme.STATUS_ERROR_TEXT, false);
            case "WARNING", "WARN" -> style(Theme.STATUS_WARNING_TEXT, false);
            case "INFO", "CONFIG", "FINE", "FINER", "FINEST", "DEBUG", "TRACE" -> style(Theme.MUTED, false);
            default -> null;
        };
    }

    /**
     * Extracts a log level from either a plain level-prefixed line or a
     * timestamped Java log line.
     *
     * @param trimmed line text without surrounding whitespace
     * @return uppercase log level token, or null when no token is found
     */
    private static String firstLogLevel(String trimmed) {
        if (trimmed.isEmpty()) {
            return null;
        }
        String first = firstToken(trimmed, 0);
        if (isLogLevel(first)) {
            return first;
        }
        if (looksLikeTimestamp(first)) {
            int secondStart = nextTokenStart(trimmed, first.length());
            String second = firstToken(trimmed, secondStart);
            if (looksLikeClock(second)) {
                int levelStart = nextTokenStart(trimmed, secondStart + second.length());
                String level = firstToken(trimmed, levelStart);
                if (isLogLevel(level)) {
                    return level;
                }
            }
        }
        return null;
    }

    /**
     * Reads the next non-whitespace token as uppercase text.
     *
     * @param text source text
     * @param start starting offset
     * @return uppercase token, or an empty string when none exists
     */
    private static String firstToken(String text, int start) {
        int begin = Math.max(0, start);
        while (begin < text.length() && Character.isWhitespace(text.charAt(begin))) {
            begin++;
        }
        int end = begin;
        while (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
            end++;
        }
        return text.substring(begin, end).toUpperCase(Locale.ROOT);
    }

    /**
     * Finds the start of the next token after {@code offset}.
     *
     * @param text source text
     * @param offset offset after a previous token
     * @return next token start, or {@code text.length()}
     */
    private static int nextTokenStart(String text, int offset) {
        int index = Math.max(0, offset);
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }

    /**
     * Tests whether {@code token} is a supported log level.
     *
     * @param token uppercase token
     * @return true when the token names a log level
     */
    private static boolean isLogLevel(String token) {
        return switch (token) {
            case "SEVERE", "ERROR", "WARNING", "WARN", "INFO", "CONFIG", "FINE", "FINER", "FINEST", "DEBUG",
                    "TRACE" -> true;
            default -> false;
        };
    }

    /**
     * Recognizes the date part of the workbench log timestamp.
     *
     * @param token first line token
     * @return true for {@code yyyy-MM-dd}
     */
    private static boolean looksLikeTimestamp(String token) {
        return token.length() == 10
                && token.charAt(4) == '-'
                && token.charAt(7) == '-'
                && isDigit(token, 0)
                && isDigit(token, 1)
                && isDigit(token, 2)
                && isDigit(token, 3)
                && isDigit(token, 5)
                && isDigit(token, 6)
                && isDigit(token, 8)
                && isDigit(token, 9);
    }

    /**
     * Recognizes the time part of the workbench log timestamp.
     *
     * @param token second line token
     * @return true for {@code HH:mm:ss}
     */
    private static boolean looksLikeClock(String token) {
        return token.length() == 8
                && token.charAt(2) == ':'
                && token.charAt(5) == ':'
                && isDigit(token, 0)
                && isDigit(token, 1)
                && isDigit(token, 3)
                && isDigit(token, 4)
                && isDigit(token, 6)
                && isDigit(token, 7);
    }

    /**
     * Tests one character offset for an ASCII digit.
     *
     * @param text source text
     * @param index character offset
     * @return true when the character is a digit
     */
    private static boolean isDigit(String text, int index) {
        char ch = text.charAt(index);
        return ch >= '0' && ch <= '9';
    }

    /**
     * Builds a bold "status badge" run for a bracketed status line (command
     * echo, exit code, error, stopped): the line's status colour over a faint
     * tinted background banner so finished-command status reads at a glance.
     *
     * @param color status colour
     * @return attribute set
     */
    private static SimpleAttributeSet badge(Color color) {
        SimpleAttributeSet attributes = style(color, true);
        StyleConstants.setBackground(attributes, Theme.withAlpha(color, 38));
        return attributes;
    }

    /**
     * Builds the section-header text style. The full-row background is painted
     * by {@link FullLineHighlightPainter}; this only controls the foreground
     * and weight.
     *
     * @return attribute set
     */
    private static SimpleAttributeSet sectionHeaderStyle() {
        return style(Theme.MUTED, true);
    }

    /**
     * Builds an attribute set for a colour and weight.
     *
     * @param color text colour
     * @param bold whether the run is bold
     * @return attribute set
     */
    private static SimpleAttributeSet style(Color color, boolean bold) {
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        StyleConstants.setForeground(attributes, color);
        StyleConstants.setBold(attributes, bold);
        return attributes;
    }

    /**
     * Highlighter that fills the whole visible text row instead of only the
     * glyph bounds.
     */
    private static final class FullLineHighlightPainter extends LayeredHighlighter.LayerPainter {

        /**
         * Highlight fill.
         */
        private final Color color;

        /**
         * Creates a painter.
         *
         * @param color highlight fill
         */
        private FullLineHighlightPainter(Color color) {
            this.color = color;
        }

        /**
         * Paints non-layered highlights.
         *
         * @param graphics graphics context
         * @param p0 start offset
         * @param p1 end offset
         * @param bounds component bounds
         * @param component text component
         */
        @Override
        public void paint(Graphics graphics, int p0, int p1, Shape bounds, JTextComponent component) {
            paintLine(graphics, p0, component);
        }

        /**
         * Paints a layered highlight.
         *
         * @param graphics graphics context
         * @param p0 start offset
         * @param p1 end offset
         * @param bounds allocation bounds
         * @param component text component
         * @param view text view
         * @return painted bounds
         */
        @Override
        public Shape paintLayer(Graphics graphics, int p0, int p1, Shape bounds,
                JTextComponent component, View view) {
            return paintLine(graphics, p0, component);
        }

        /**
         * Paints one full-width row for the model offset.
         *
         * @param graphics graphics context
         * @param offset model offset
         * @param component text component
         * @return painted row bounds
         */
        private Shape paintLine(Graphics graphics, int offset, JTextComponent component) {
            try {
                Rectangle2D rect = component.modelToView2D(offset);
                int y = (int) Math.floor(rect.getY());
                int height = Math.max(component.getFontMetrics(component.getFont()).getHeight(),
                        (int) Math.ceil(rect.getHeight()));
                graphics.setColor(color);
                graphics.fillRect(0, y, component.getWidth(), height);
                return new java.awt.Rectangle(0, y, component.getWidth(), height);
            } catch (BadLocationException ex) {
                return null;
            }
        }
    }

    /**
     * Sets centered guidance shown while the console has no output, so an idle
     * console reads as a deliberate empty state instead of a black void.
     *
     * @param text placeholder text, or empty to show nothing
     */
    public void setPlaceholder(String text) {
        this.placeholder = text == null ? "" : text;
        repaint();
    }

    /**
     * Fills the viewport while empty so the placeholder centers in the visible
     * area rather than floating near the top of a short text pane.
     *
     * @return true when there is no output yet
     */
    @Override
    public boolean getScrollableTracksViewportHeight() {
        return getDocument().getLength() == 0 || super.getScrollableTracksViewportHeight();
    }

    /**
     * Paints the console, drawing the centered placeholder while it is empty.
     *
     * @param graphics graphics context
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (placeholder.isEmpty() || getDocument().getLength() > 0) {
            return;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(Theme.font(Theme.FONT_BODY, Font.PLAIN));
            g.setColor(Theme.MUTED);
            FontMetrics fm = g.getFontMetrics();
            int x = Math.max(getInsets().left, (getWidth() - fm.stringWidth(placeholder)) / 2);
            int y = getHeight() / 2 + fm.getAscent() / 2 - 2;
            g.drawString(placeholder, x, y);
        } finally {
            g.dispose();
        }
    }

    /**
     * Returns a sensible default size for the console scroll view.
     *
     * @return preferred size
     */
    @Override
    public Dimension getPreferredSize() {
        Dimension preferred = super.getPreferredSize();
        return new Dimension(Math.max(preferred.width, 480), Math.max(preferred.height, 240));
    }
}
