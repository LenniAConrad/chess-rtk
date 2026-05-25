package application.gui.workbench.command;

import application.gui.workbench.ui.Theme;
import java.awt.Color;
import java.awt.Dimension;
import java.util.Locale;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

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
        setFont(Theme.mono(13));
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
        currentLine.setLength(0);
        column = 0;
        lineStart = 0;
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
        if (trimmed.startsWith("$ ")) {
    return style(Theme.STATUS_INFO_TEXT, true);
        }
        if (trimmed.startsWith("[exit 0")) {
    return style(Theme.STATUS_SUCCESS_TEXT, true);
        }
        if (trimmed.startsWith("[exit ") || trimmed.startsWith("[error")) {
    return style(Theme.STATUS_ERROR_TEXT, true);
        }
        if (trimmed.startsWith("[stopped]") || trimmed.startsWith("[")) {
    return style(Theme.STATUS_WARNING_TEXT, true);
        }
        if (lower.contains("error") || lower.contains("exception")
                || lower.contains("failed") || lower.contains("invalid")) {
    return style(Theme.STATUS_ERROR_TEXT, false);
        }
    return style(Theme.TERMINAL_TEXT, false);
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
