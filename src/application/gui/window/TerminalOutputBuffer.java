package application.gui.window;

import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

/**
 * Plain-text terminal buffer for Swing text areas. A carriage return rewinds
 * the write column to the start of the current line, so progress output that
 * redraws in place does not stack stale intermediate frames.
 */
final class TerminalOutputBuffer {

	/**
	 * Target text area.
	 */
	private final JTextArea area;

	/**
	 * Text of the current unterminated terminal line.
	 */
	private final StringBuilder currentLine = new StringBuilder();

	/**
	 * Write column within {@link #currentLine}.
	 */
	private int column;

	/**
	 * Document offset where {@link #currentLine} starts.
	 */
	private int lineStart;

	/**
	 * Creates a terminal output buffer.
	 *
	 * @param area target text area
	 */
	TerminalOutputBuffer(JTextArea area) {
		this.area = area;
	}

	/**
	 * Clears the visible text and terminal cursor state.
	 */
	void clear() {
		area.setText("");
		currentLine.setLength(0);
		column = 0;
		lineStart = 0;
	}

	/**
	 * Appends raw terminal text, preserving carriage returns and newlines.
	 *
	 * @param text raw text chunk
	 */
	void append(String text) {
		if (text == null || text.isEmpty()) {
			return;
		}
		if (area.getDocument().getLength() < lineStart) {
			clear();
		}
		for (int i = 0; i < text.length(); i++) {
			char ch = text.charAt(i);
			switch (ch) {
				case '\r' -> column = 0;
				case '\n' -> commitNewline();
				default -> writeChar(ch);
			}
		}
		flushLine();
		area.setCaretPosition(area.getDocument().getLength());
	}

	/**
	 * Writes one visible character at the current terminal column.
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
	 * Commits the current terminal line and starts the next one.
	 */
	private void commitNewline() {
		flushLine();
		Document document = area.getDocument();
		try {
			document.insertString(document.getLength(), "\n", null);
		} catch (BadLocationException ignored) {
			// Offsets come from the document itself.
		}
		currentLine.setLength(0);
		column = 0;
		lineStart = document.getLength();
	}

	/**
	 * Rewrites the visible in-progress line to match {@link #currentLine}.
	 */
	private void flushLine() {
		Document document = area.getDocument();
		try {
			int end = document.getLength();
			if (end > lineStart) {
				document.remove(lineStart, end - lineStart);
			}
			document.insertString(lineStart, currentLine.toString(), null);
		} catch (BadLocationException ignored) {
			// Offsets come from the document itself.
		}
	}
}
