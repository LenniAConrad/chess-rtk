package chess.book.render;

import static chess.book.render.Writer.*;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import chess.pdf.document.Canvas;
import chess.pdf.document.Font;

/**
 * Text, notation, wrapping, and table-of-contents helpers for {@link Writer}.
 */
final class WriterText {

	/**
	 * Utility class; prevent instantiation.
	 */
	private WriterText() {
		// utility
	}

	static void drawCentered(Canvas canvas, double left, double right, double y, TextStyle style, String text) {
		String safe = blankTo(text, "");
		double width = textWidth(style.font(), style.size(), safe);
		double x = left + Math.max(0.0, (right - left - width) / 2.0);
		canvas.drawText(x, y, style.font(), style.size(), style.color(), safe);
	}

	/**
	 * Draws centered chess notation, replacing figurine placeholders with SVG
	 * pieces.
	 *
	 * @param canvas target canvas
	 * @param left left boundary
	 * @param right right boundary
	 * @param y top edge
	 * @param style text style for ordinary text
	 * @param text notation text to draw
	 */
	static void drawCenteredNotationText(Canvas canvas, double left, double right, double y, TextStyle style,
			String text) {
		String safe = blankTo(text, "");
		double width = notationTextWidth(style.font(), style.size(), safe);
		double x = left + Math.max(0.0, (right - left - width) / 2.0);
		drawNotationText(canvas, x, y, style.font(), style.size(), style.color(), safe);
	}

	/**
	 * Draws one chess notation run using embedded SVGs for piece symbols.
	 *
	 * @param canvas target canvas
	 * @param x left edge
	 * @param y text top edge
	 * @param font font for ordinary characters
	 * @param size font size
	 * @param color fill color for ordinary characters
	 * @param text notation text to draw
	 */
	static void drawNotationText(Canvas canvas, double x, double y, Font font, double size, Color color,
			String text) {
		String safe = blankTo(text, "");
		double cursorX = x;
		StringBuilder run = new StringBuilder();
		for (int i = 0; i < safe.length(); i++) {
			char ch = safe.charAt(i);
			String pieceSvg = NotationPieceSvg.svg(ch);
			if (pieceSvg == null) {
				run.append(ch);
				continue;
			}
			cursorX = drawNotationTextRun(canvas, cursorX, y, font, size, color, run);
			drawNotationPiece(canvas, cursorX, y, size, pieceSvg);
			cursorX += notationPieceAdvance(size);
		}
		drawNotationTextRun(canvas, cursorX, y, font, size, color, run);
	}

	/**
	 * Draws a plain text segment inside notation and returns the next cursor.
	 *
	 * @param canvas target canvas
	 * @param x left edge
	 * @param y text top edge
	 * @param font font to use
	 * @param size font size
	 * @param color fill color
	 * @param run pending ordinary text
	 * @return cursor after the text segment
	 */
	static double drawNotationTextRun(Canvas canvas, double x, double y, Font font, double size, Color color,
			StringBuilder run) {
		if (run.isEmpty()) {
			return x;
		}
		String text = run.toString();
		canvas.drawText(x, y, font, size, color, text);
		run.setLength(0);
		return x + textWidth(font, size, text);
	}

	/**
	 * Draws one inline notation piece SVG.
	 *
	 * @param canvas target canvas
	 * @param x logical cursor x coordinate
	 * @param y surrounding text top edge
	 * @param size surrounding font size
	 * @param pieceSvg embedded SVG source
	 */
	static void drawNotationPiece(Canvas canvas, double x, double y, double size, String pieceSvg) {
		double boxSize = notationPieceBoxSize(size);
		double drawX = x + notationPieceLeftPadding(size);
		double drawY = y - size * NOTATION_PIECE_TOP_SHIFT_SCALE;
		canvas.drawSvg(pieceSvg, drawX, drawY, boxSize, boxSize);
	}

	/**
	 * Draws centered wrapped text with a line cap.
	 *
	 * @param canvas target canvas
	 * @param block text block geometry
	 * @param style text style
	 * @param leading line advance
	 * @param text text to draw
	 * @param maxLines maximum line count
	 */
	static void drawCenteredWrapped(Canvas canvas, TextBlock block, TextStyle style, double leading, String text,
			int maxLines) {
		List<String> lines = wrapLines(text, style.font(), style.size(), block.width());
		for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
			drawCentered(canvas, block.x(), block.x() + block.width(), block.y() + i * leading, style, lines.get(i));
		}
	}

	/**
	 * Draws a table-of-contents heading and returns the next cursor position.
	 *
	 * @param page target page
	 * @param title heading text
	 * @param first whether the first-page layout should be used
	 * @return next vertical cursor position
	 */
	static double drawTocHeading(PageFrame page, String title, boolean first) {
		if (page.canvas == null) {
			return page.top + (first ? 34.0 : 18.0);
		}
		double y = page.top;
		if (first) {
			drawCentered(page.canvas, page.left, page.right, y,
					textStyle(Font.LATIN_MODERN_BOLD, TOC_TITLE_SIZE, INK), title);
			double lineY = y + Font.LATIN_MODERN_BOLD.lineHeight(TOC_TITLE_SIZE) + 8.0;
			page.canvas.line(page.left + page.width * 0.2, lineY, page.right - page.width * 0.2, lineY, SEPARATOR, 0.8);
			return lineY + 16.0;
		}
		page.canvas.drawText(page.left, y, Font.LATIN_MODERN_BOLD, TOC_ENTRY_SIZE, INK, title);
		return y + 18.0;
	}

	/**
	 * Draws one table-of-contents line with dot leaders.
	 *
	 * @param page target page
	 * @param entry TOC entry to draw
	 * @param y top edge
	 */
	static void drawTocEntry(PageFrame page, TocEntry entry, double y) {
		if (page.canvas == null) {
			return;
		}
		double indent = entry.level == 1 ? 0.0 : 18.0;
		double x = page.left + indent;
		String pageText = Integer.toString(entry.pageNumber);
		double pageWidth = textWidth(Font.LATIN_MODERN_ROMAN, TOC_ENTRY_SIZE, pageText);
		double pageX = page.right - pageWidth;
		double titleMaxWidth = Math.max(10.0, pageX - x - 14.0);
		String title = fitText(entry.title, Font.LATIN_MODERN_ROMAN, TOC_ENTRY_SIZE, titleMaxWidth * 0.72);
		double titleWidth = textWidth(Font.LATIN_MODERN_ROMAN, TOC_ENTRY_SIZE, title);
		double dotsX = x + titleWidth + 6.0;
		double dotsWidth = Math.max(0.0, pageX - dotsX - 6.0);
		String dots = dotLeader(Font.LATIN_MODERN_ROMAN, TOC_ENTRY_SIZE, dotsWidth);
		page.canvas.drawText(x, y, Font.LATIN_MODERN_ROMAN, TOC_ENTRY_SIZE, INK, title);
		page.canvas.drawText(dotsX, y, Font.LATIN_MODERN_ROMAN, TOC_ENTRY_SIZE, MUTED, dots);
		page.canvas.drawText(pageX, y, Font.LATIN_MODERN_ROMAN, TOC_ENTRY_SIZE, INK, pageText);
		page.canvas.linkToPage(x, y, page.right - x, Math.max(14.0,
				Font.LATIN_MODERN_ROMAN.lineHeight(TOC_ENTRY_SIZE)), entry.pageNumber);
	}

	/**
	 * Builds a dot-leader string that fits inside the requested width.
	 *
	 * @param font measurement font
	 * @param size font size
	 * @param width target width
	 * @return dot-leader string
	 */
	static String dotLeader(Font font, double size, double width) {
		double dotWidth = Math.max(0.1, textWidth(font, size, "."));
		int count = Math.max(0, (int) Math.floor(width / dotWidth));
		StringBuilder builder = new StringBuilder(count);
		for (int i = 0; i < count; i++) {
			builder.append('.');
		}
		return builder.toString();
	}

	/**
	 * Fits a string into the requested width using an ASCII ellipsis fallback.
	 *
	 * @param text source text
	 * @param font measurement font
	 * @param size font size
	 * @param width target width
	 * @return fitted string
	 */
	static String fitText(String text, Font font, double size, double width) {
		String safe = blankTo(text, "");
		if (textWidth(font, size, safe) <= width) {
			return safe;
		}
		String ellipsis = "...";
		int end = safe.length();
		while (end > 1) {
			String candidate = safe.substring(0, end).trim() + ellipsis;
			if (textWidth(font, size, candidate) <= width) {
				return candidate;
			}
			end--;
		}
		return ellipsis;
	}

	/**
	 * Fits chess notation into the requested width while measuring SVG pieces.
	 *
	 * @param text source notation
	 * @param font measurement font for ordinary text
	 * @param size font size
	 * @param width target width
	 * @return fitted notation string
	 */
	static String fitNotationText(String text, Font font, double size, double width) {
		String safe = blankTo(text, "");
		if (notationTextWidth(font, size, safe) <= width) {
			return safe;
		}
		String ellipsis = "...";
		int end = safe.length();
		while (end > 1) {
			String candidate = safe.substring(0, end).trim() + ellipsis;
			if (notationTextWidth(font, size, candidate) <= width) {
				return candidate;
			}
			end--;
		}
		return ellipsis;
	}

	/**
	 * Measures wrapped notation height using SVG-aware piece widths.
	 *
	 * @param text source notation
	 * @param font font to measure ordinary text with
	 * @param size font size
	 * @param width wrap width
	 * @param leading line advance
	 * @return wrapped height
	 */
	static double wrappedNotationHeight(String text, Font font, double size, double width, double leading) {
		return wrapNotationLines(text, font, size, width).size() * leading;
	}

	/**
	 * Wraps text using the same rules for both simulation and rendering passes.
	 *
	 * @param text source text
	 * @param font font to measure with
	 * @param size font size
	 * @param width wrap width
	 * @return wrapped line list
	 */
	static List<String> wrapLines(String text, Font font, double size, double width) {
		return wrapLines(text, font, size, width, false);
	}

	/**
	 * Wraps notation using SVG-aware piece widths.
	 *
	 * @param text source notation
	 * @param font font to measure ordinary text with
	 * @param size font size
	 * @param width wrap width
	 * @return wrapped line list
	 */
	static List<String> wrapNotationLines(String text, Font font, double size, double width) {
		return wrapLines(text, font, size, width, true);
	}

	/**
	 * Wraps text with optional SVG notation measurement.
	 *
	 * @param text source text
	 * @param font font to measure with
	 * @param size font size
	 * @param width wrap width
	 * @param notation whether figurine placeholders should use SVG widths
	 * @return wrapped line list
	 */
	static List<String> wrapLines(String text, Font font, double size, double width, boolean notation) {
		List<String> lines = new ArrayList<>();
		String safe = normalizeWhitespacePreserveBreaks(text);
		if (safe.isBlank()) {
			return lines;
		}
		if (width <= 0.0) {
			lines.add(safe.trim());
			return lines;
		}

		String[] paragraphs = safe.split("\n", -1);
		for (String paragraph : paragraphs) {
			if (paragraph.isBlank()) {
				lines.add("");
				continue;
			}
			String[] words = paragraph.trim().split("\\s+");
			StringBuilder line = new StringBuilder();
			for (String word : words) {
				appendWrappedWord(lines, line, word, font, size, width, notation);
			}
			if (!line.isEmpty()) {
				lines.add(line.toString());
			}
		}
		return lines;
	}

	/**
	 * Appends one word to the current wrapped line, flushing as needed.
	 *
	 * @param lines output line list
	 * @param line current line builder
	 * @param word word to append
	 * @param font measurement font
	 * @param size font size
	 * @param width wrap width
	 * @param notation whether figurine placeholders should use SVG widths
	 */
	static void appendWrappedWord(List<String> lines, StringBuilder line, String word, Font font, double size,
			double width, boolean notation) {
		String candidate = line.isEmpty() ? word : line + " " + word;
		if (line.isEmpty() || measuredTextWidth(font, size, candidate, notation) <= width) {
			line.setLength(0);
			line.append(candidate);
			return;
		}
		lines.add(line.toString());
		appendBrokenWord(lines, word, font, size, width, notation, line);
	}

	/**
	 * Appends a single oversized word by breaking it into line-sized fragments.
	 *
	 * @param lines output line list
	 * @param word oversized word
	 * @param font measurement font
	 * @param size font size
	 * @param width wrap width
	 * @param notation whether figurine placeholders should use SVG widths
	 * @param scratch reusable line builder
	 */
	static void appendBrokenWord(List<String> lines, String word, Font font, double size, double width,
			boolean notation, StringBuilder scratch) {
		scratch.setLength(0);
		for (int i = 0; i < word.length(); i++) {
			char ch = word.charAt(i);
			String candidate = scratch.toString() + ch;
			if (!scratch.isEmpty() && measuredTextWidth(font, size, candidate, notation) > width) {
				lines.add(scratch.toString());
				scratch.setLength(0);
			}
			scratch.append(ch);
		}
	}

	/**
	 * Measures a text run with optional notation SVG widths.
	 *
	 * @param font font to measure ordinary text with
	 * @param size font size
	 * @param text text to measure
	 * @param notation whether figurine placeholders should use SVG widths
	 * @return measured width
	 */
	static double measuredTextWidth(Font font, double size, String text, boolean notation) {
		return notation ? notationTextWidth(font, size, text) : textWidth(font, size, text);
	}

	/**
	 * Measures chess notation with SVG piece advances instead of figurine glyphs.
	 *
	 * @param font font to measure ordinary text with
	 * @param size font size
	 * @param text notation text to measure
	 * @return measured width
	 */
	static double notationTextWidth(Font font, double size, String text) {
		String safe = blankTo(text, "");
		double width = 0.0;
		StringBuilder run = new StringBuilder();
		for (int i = 0; i < safe.length(); i++) {
			char ch = safe.charAt(i);
			if (!NotationPieceSvg.isPlaceholder(ch)) {
				run.append(ch);
				continue;
			}
			width += textWidth(font, size, run.toString());
			run.setLength(0);
			width += notationPieceAdvance(size);
		}
		return width + textWidth(font, size, run.toString());
	}

	/**
	 * Returns whether a text run contains a notation piece placeholder.
	 *
	 * @param text text to inspect
	 * @return true when at least one SVG-rendered notation piece is present
	 */
	static boolean containsNotationPiece(String text) {
		String safe = blankTo(text, "");
		for (int i = 0; i < safe.length(); i++) {
			if (NotationPieceSvg.isPlaceholder(safe.charAt(i))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Computes the SVG box size used for one inline notation piece.
	 *
	 * @param size surrounding font size
	 * @return square SVG box size
	 */
	static double notationPieceBoxSize(double size) {
		return size * NOTATION_PIECE_SIZE_SCALE;
	}

	/**
	 * Computes the left side bearing before one inline notation piece.
	 *
	 * @param size surrounding font size
	 * @return left padding before the piece
	 */
	static double notationPieceLeftPadding(double size) {
		return size * NOTATION_PIECE_LEFT_PADDING_SCALE;
	}

	/**
	 * Computes the right side bearing after one inline notation piece.
	 *
	 * @param size surrounding font size
	 * @return right padding after the piece
	 */
	static double notationPieceRightPadding(double size) {
		return size * NOTATION_PIECE_RIGHT_PADDING_SCALE;
	}

	/**
	 * Computes the logical advance used for one inline notation piece.
	 *
	 * @param size surrounding font size
	 * @return cursor advance after drawing the piece
	 */
	static double notationPieceAdvance(double size) {
		return notationPieceLeftPadding(size) + notationPieceBoxSize(size) + notationPieceRightPadding(size);
	}

	/**
	 * Measures the width of a text run.
	 *
	 * @param font font to measure with
	 * @param size font size
	 * @param text text to measure
	 * @return measured width
	 */
	static double textWidth(Font font, double size, String text) {
		return font.textWidth(blankTo(text, ""), size);
	}

	/**
	 * Returns a non-null string fallback.
	 *
	 * @param value source value
	 * @param fallback fallback when the source is null
	 * @return non-null string
	 */
	static String blankTo(String value, String fallback) {
		return value == null ? fallback : value;
	}

	/**
	 * Normalizes whitespace to a single-line representation.
	 *
	 * @param text source text
	 * @return normalized text
	 */
	static String normalizeWhitespace(String text) {
		return normalizeWhitespacePreserveBreaks(text).replace('\n', ' ').replaceAll("\\s+", " ").trim();
	}

	/**
	 * Normalizes line endings while preserving paragraph breaks.
	 *
	 * @param text source text
	 * @return normalized text
	 */
	static String normalizeWhitespacePreserveBreaks(String text) {
		return blankTo(text, "").replace("\r\n", "\n").replace('\r', '\n').trim();
	}

	/**
	 * Returns a null-safe array clone.
	 *
	 * @param source source array
	 * @return cloned array, never null
	 */
	static String[] safeArray(String[] source) {
		return source == null ? new String[0] : source.clone();
	}
}
