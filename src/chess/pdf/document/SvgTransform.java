package chess.pdf.document;

import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses SVG transform lists into Java2D affine transforms.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class SvgTransform {

	/**
	 * Pattern used to scan SVG numeric tokens.
	 */
	private static final Pattern NUMBER_PATTERN = Pattern.compile(
			"[+-]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?");

	/**
	 * Utility class; prevent instantiation.
	 */
	private SvgTransform() {
		// utility
	}

	/**
	 * Parses an SVG transform list.
	 *
	 * @param transform raw transform value
	 * @return combined transform
	 */
	static AffineTransform parse(String transform) {
		if (transform == null || transform.isBlank()) {
			return new AffineTransform();
		}

		TransformCursor cursor = new TransformCursor(transform);
		AffineTransform result = new AffineTransform();
		while (cursor.hasMore()) {
			String name = cursor.readName();
			if (name == null) {
				break;
			}
			double[] args = parseNumbers(cursor.readArgs(name));
			result.concatenate(toAffine(name, args));
		}
		return result;
	}

	/**
	 * Converts one SVG transform function to an affine transform.
	 *
	 * @param name transform function name
	 * @param args numeric arguments
	 * @return affine transform
	 */
	private static AffineTransform toAffine(String name, double[] args) {
		return switch (name) {
			case "translate" -> AffineTransform.getTranslateInstance(
					transformArg(args, 0, 0.0),
					transformArg(args, 1, 0.0));
			case "scale" -> AffineTransform.getScaleInstance(transformArg(args, 0, 1.0), scaleY(args));
			case "rotate" -> rotateTransform(args);
			case "matrix" -> matrixTransform(args);
			default -> throw new IllegalArgumentException("Unsupported transform: " + name);
		};
	}

	/**
	 * Parses all numeric tokens from a string.
	 *
	 * @param value source string
	 * @return parsed number array
	 */
	private static double[] parseNumbers(String value) {
		if (value == null || value.isBlank()) {
			return new double[0];
		}
		List<Double> numbers = new ArrayList<>();
		Matcher matcher = NUMBER_PATTERN.matcher(value);
		while (matcher.find()) {
			numbers.add(Double.parseDouble(matcher.group()));
		}
		double[] result = new double[numbers.size()];
		for (int i = 0; i < numbers.size(); i++) {
			result[i] = numbers.get(i);
		}
		return result;
	}

	/**
	 * Returns one transform argument with a fallback value.
	 *
	 * @param args transform argument array
	 * @param index argument index
	 * @param fallback fallback value
	 * @return transform argument or fallback
	 */
	private static double transformArg(double[] args, int index, double fallback) {
		return args.length > index ? args[index] : fallback;
	}

	/**
	 * Returns the y scale argument for SVG {@code scale()}.
	 *
	 * @param args scale argument array
	 * @return explicit y scale, x scale, or one
	 */
	private static double scaleY(double[] args) {
		if (args.length > 1) {
			return args[1];
		}
		return transformArg(args, 0, 1.0);
	}

	/**
	 * Builds an SVG rotate transform.
	 *
	 * @param args rotate arguments
	 * @return rotation transform
	 */
	private static AffineTransform rotateTransform(double[] args) {
		double angle = args.length > 0 ? Math.toRadians(args[0]) : 0.0;
		if (args.length >= 3) {
			return AffineTransform.getRotateInstance(angle, args[1], args[2]);
		}
		return AffineTransform.getRotateInstance(angle);
	}

	/**
	 * Builds an SVG matrix transform.
	 *
	 * @param args six matrix arguments
	 * @return affine transform
	 */
	private static AffineTransform matrixTransform(double[] args) {
		if (args.length != 6) {
			throw new IllegalArgumentException("matrix() expects 6 values");
		}
		return new AffineTransform(args[0], args[1], args[2], args[3], args[4], args[5]);
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
	 * Cursor for parsing SVG transform lists.
	 */
	private static final class TransformCursor {

		/**
		 * Raw transform string.
		 */
		private final String value;

		/**
		 * Current character index.
		 */
		private int index;

		/**
		 * Creates a transform cursor.
		 *
		 * @param value raw transform string
		 */
		private TransformCursor(String value) {
			this.value = value == null ? "" : value;
		}

		/**
		 * Returns whether another transform function remains.
		 *
		 * @return true when more content is available
		 */
		private boolean hasMore() {
			index = skipSeparators(value, index);
			return index < value.length();
		}

		/**
		 * Reads the next transform function name.
		 *
		 * @return transform function name, or null at end
		 */
		private String readName() {
			if (!hasMore()) {
				return null;
			}
			int start = index;
			while (index < value.length() && Character.isLetter(value.charAt(index))) {
				index++;
			}
			if (start == index) {
				throw new IllegalArgumentException("Invalid transform at index " + index);
			}
			return value.substring(start, index);
		}

		/**
		 * Reads the parenthesized argument string for a transform function.
		 *
		 * @param name transform function name
		 * @return raw argument string
		 */
		private String readArgs(String name) {
			index = skipSeparators(value, index);
			if (index >= value.length() || value.charAt(index) != '(') {
				throw new IllegalArgumentException("Expected '(' after transform " + name);
			}

			int depth = 1;
			int start = ++index;
			while (index < value.length() && depth > 0) {
				char ch = value.charAt(index);
				if (ch == '(') {
					depth++;
				} else if (ch == ')') {
					depth--;
				}
				index++;
			}
			if (depth != 0) {
				throw new IllegalArgumentException("Unclosed transform arguments for " + name);
			}
			return value.substring(start, index - 1);
		}
	}
}
