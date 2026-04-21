package chess.pdf.document;

import java.awt.geom.Path2D;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the path-data subset supported by the local SVG renderer.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class SvgPath {

	/**
	 * Pattern used to scan SVG numeric tokens.
	 */
	private static final Pattern NUMBER_PATTERN = Pattern.compile(
			"[+-]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?");

	/**
	 * Utility class; prevent instantiation.
	 */
	private SvgPath() {
		// utility
	}

	/**
	 * Parses a subset of SVG path data.
	 *
	 * @param data raw path data
	 * @param fillRule SVG fill rule
	 * @return parsed path
	 */
	static Path2D.Double parse(String data, String fillRule) {
		Path2D.Double path = new Path2D.Double(windingRule(fillRule));
		if (data == null || data.isBlank()) {
			return path;
		}

		PathCursor cursor = new PathCursor(data);
		while (cursor.hasMore()) {
			cursor.consumeSeparators();
			if (!cursor.hasMore()) {
				break;
			}

			char ch = cursor.currentChar();
			if (isCommand(ch)) {
				cursor.command = ch;
				cursor.advance();
			} else if (cursor.command == 0) {
				throw new IllegalArgumentException("SVG path missing command at index " + cursor.index);
			}

			switch (cursor.command) {
				case 'M', 'm' -> parseMoveTo(cursor, path);
				case 'L', 'l' -> parseLineTo(cursor, path);
				case 'C', 'c' -> parseCurveTo(cursor, path);
				case 'Z', 'z' -> closePath(cursor, path);
				default -> throw new IllegalArgumentException("Unsupported SVG path command: " + cursor.command);
			}
		}
		return path;
	}

	/**
	 * Parses an SVG move-to command and any implicit line-to coordinates.
	 *
	 * @param cursor path parser cursor
	 * @param path target path
	 */
	private static void parseMoveTo(PathCursor cursor, Path2D.Double path) {
		boolean relative = cursor.command == 'm';
		double x = cursor.readNumber();
		double y = cursor.readNumber();
		if (relative) {
			x += cursor.cx;
			y += cursor.cy;
		}
		cursor.cx = x;
		cursor.cy = y;
		cursor.sx = x;
		cursor.sy = y;
		path.moveTo(x, y);

		cursor.command = relative ? 'l' : 'L';
		while (cursor.hasNumber()) {
			x = cursor.readNumber();
			y = cursor.readNumber();
			if (cursor.command == 'l') {
				x += cursor.cx;
				y += cursor.cy;
			}
			cursor.cx = x;
			cursor.cy = y;
			path.lineTo(x, y);
		}
	}

	/**
	 * Parses an SVG line-to command.
	 *
	 * @param cursor path parser cursor
	 * @param path target path
	 */
	private static void parseLineTo(PathCursor cursor, Path2D.Double path) {
		boolean relative = cursor.command == 'l';
		while (cursor.hasNumber()) {
			double x = cursor.readNumber();
			double y = cursor.readNumber();
			if (relative) {
				x += cursor.cx;
				y += cursor.cy;
			}
			cursor.cx = x;
			cursor.cy = y;
			path.lineTo(x, y);
		}
	}

	/**
	 * Parses an SVG cubic curve-to command.
	 *
	 * @param cursor path parser cursor
	 * @param path target path
	 */
	private static void parseCurveTo(PathCursor cursor, Path2D.Double path) {
		boolean relative = cursor.command == 'c';
		while (cursor.hasNumber()) {
			double x1 = cursor.readNumber();
			double y1 = cursor.readNumber();
			double x2 = cursor.readNumber();
			double y2 = cursor.readNumber();
			double x = cursor.readNumber();
			double y = cursor.readNumber();
			if (relative) {
				x1 += cursor.cx;
				y1 += cursor.cy;
				x2 += cursor.cx;
				y2 += cursor.cy;
				x += cursor.cx;
				y += cursor.cy;
			}
			cursor.cx = x;
			cursor.cy = y;
			path.curveTo(x1, y1, x2, y2, x, y);
		}
	}

	/**
	 * Closes the current SVG path.
	 *
	 * @param cursor path parser cursor
	 * @param path target path
	 */
	private static void closePath(PathCursor cursor, Path2D.Double path) {
		path.closePath();
		cursor.cx = cursor.sx;
		cursor.cy = cursor.sy;
		cursor.advance();
	}

	/**
	 * Converts an SVG fill rule to a Java2D winding rule.
	 *
	 * @param fillRule SVG fill rule
	 * @return Java2D winding rule
	 */
	private static int windingRule(String fillRule) {
		return "evenodd".equals(fillRule) ? Path2D.WIND_EVEN_ODD : Path2D.WIND_NON_ZERO;
	}

	/**
	 * Returns whether a character starts an SVG path command.
	 *
	 * @param ch character to inspect
	 * @return true for supported path commands
	 */
	private static boolean isCommand(char ch) {
		return ch == 'M' || ch == 'm'
				|| ch == 'L' || ch == 'l'
				|| ch == 'C' || ch == 'c'
				|| ch == 'Z' || ch == 'z';
	}

	/**
	 * Skips SVG numeric-list separators.
	 *
	 * @param data source string
	 * @param index starting index
	 * @return index after separators
	 */
	private static int skipSeparators(String data, int index) {
		int i = index;
		while (i < data.length()) {
			char ch = data.charAt(i);
			if (Character.isWhitespace(ch) || ch == ',') {
				i++;
			} else {
				break;
			}
		}
		return i;
	}

	/**
	 * Returns whether a number starts at the requested index.
	 *
	 * @param data source string
	 * @param index starting index
	 * @return true when a number can be read
	 */
	private static boolean hasNumber(String data, int index) {
		int i = skipSeparators(data, index);
		if (i >= data.length()) {
			return false;
		}
		char ch = data.charAt(i);
		return ch == '+' || ch == '-' || ch == '.' || Character.isDigit(ch);
	}

	/**
	 * Reads one SVG number.
	 *
	 * @param data source string
	 * @param index starting index
	 * @return parsed number token
	 */
	private static NumberToken readNumber(String data, int index) {
		int i = skipSeparators(data, index);
		if (i >= data.length()) {
			throw new IllegalArgumentException("Unexpected end of SVG number list");
		}
		Matcher matcher = NUMBER_PATTERN.matcher(data);
		matcher.region(i, data.length());
		if (!matcher.lookingAt()) {
			throw new IllegalArgumentException("Expected number at index " + i);
		}
		return new NumberToken(Double.parseDouble(matcher.group()), matcher.end());
	}

	/**
	 * Parsed number token.
	 *
	 * @param value numeric value
	 * @param endIndex first index after the token
	 */
	private record NumberToken(
		/**
		 * Stores the value.
		 */
		double value,
		/**
		 * Stores the end index.
		 */
		int endIndex
	) {
	}

	/**
	 * Cursor for parsing SVG path data.
	 */
	private static final class PathCursor {

		/**
		 * Raw path data.
		 */
		private final String data;

		/**
		 * Current character index.
		 */
		private int index;

		/**
		 * Current path command.
		 */
		private char command;

		/**
		 * Current x coordinate.
		 */
		private double cx;

		/**
		 * Current y coordinate.
		 */
		private double cy;

		/**
		 * Current subpath start x coordinate.
		 */
		private double sx;

		/**
		 * Current subpath start y coordinate.
		 */
		private double sy;

		/**
		 * Creates a path cursor.
		 *
		 * @param data raw path data
		 */
		private PathCursor(String data) {
			this.data = data == null ? "" : data;
		}

		/**
		 * Returns whether unread path data remains.
		 *
		 * @return true when more characters are available
		 */
		private boolean hasMore() {
			return index < data.length();
		}

		/**
		 * Advances past list separators.
		 */
		private void consumeSeparators() {
			index = skipSeparators(data, index);
		}

		/**
		 * Returns the current path data character.
		 *
		 * @return current character
		 */
		private char currentChar() {
			return data.charAt(index);
		}

		/**
		 * Advances by one character.
		 */
		private void advance() {
			index++;
		}

		/**
		 * Returns whether a number starts at the cursor.
		 *
		 * @return true when a number can be read
		 */
		private boolean hasNumber() {
			return SvgPath.hasNumber(data, index);
		}

		/**
		 * Reads one number and advances the cursor.
		 *
		 * @return parsed number
		 */
		private double readNumber() {
			NumberToken token = SvgPath.readNumber(data, index);
			index = token.endIndex();
			return token.value();
		}
	}
}
