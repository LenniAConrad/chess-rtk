package chess.pdf.document;

import java.awt.Color;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Top-left oriented drawing API for a {@link Page}.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
@SuppressWarnings("java:S107")
public final class Canvas {

	/**
	 * Shared render context used when text must be emitted as vector outlines.
	 */
	private static final FontRenderContext VECTOR_FONT_CONTEXT = new FontRenderContext(null, true, true);

	/**
	 * Font families tried when the requested PDF font lacks a vector-only glyph.
	 */
	private static final List<String> VECTOR_FALLBACK_FAMILIES = Arrays.asList(
			"DejaVu Sans",
			"FreeSerif",
			java.awt.Font.SERIF,
			java.awt.Font.SANS_SERIF);

	/**
	 * Page that receives all drawing commands.
	 */
	private final Page page;

	/**
	 * Creates a canvas for one page.
	 *
	 * @param page page that receives content stream commands
	 */
	Canvas(Page page) {
		this.page = page;
	}

	/**
	 * Fills a rectangle.
	 *
	 * @param x      left edge
	 * @param y      top edge
	 * @param width  width in points
	 * @param height height in points
	 * @param color  fill color
	 * @return this canvas
	 */
	public Canvas fillRect(double x, double y, double width, double height, Color color) {
		if (width <= 0.0 || height <= 0.0) {
			return this;
		}
		appendFillColor(color);
		page.append(Document.number(x) + " " + Document.number(toPdfY(y + height)) + " "
				+ Document.number(width) + " " + Document.number(height) + " re f\n");
		return this;
	}

	/**
	 * Strokes a rectangle.
	 *
	 * @param x         left edge
	 * @param y         top edge
	 * @param width     width in points
	 * @param height    height in points
	 * @param color     stroke color
	 * @param lineWidth stroke width
	 * @return this canvas
	 */
	public Canvas strokeRect(double x, double y, double width, double height, Color color, double lineWidth) {
		if (width <= 0.0 || height <= 0.0) {
			return this;
		}
		appendStrokeColor(color);
		page.append(Document.number(Math.max(0.1, lineWidth)) + " w\n");
		page.append(Document.number(x) + " " + Document.number(toPdfY(y + height)) + " "
				+ Document.number(width) + " " + Document.number(height) + " re S\n");
		return this;
	}

	/**
	 * Draws a straight line.
	 *
	 * @param x1        first x coordinate
	 * @param y1        first y coordinate
	 * @param x2        second x coordinate
	 * @param y2        second y coordinate
	 * @param color     stroke color
	 * @param lineWidth stroke width
	 * @return this canvas
	 */
	public Canvas line(double x1, double y1, double x2, double y2, Color color, double lineWidth) {
		appendStrokeColor(color);
		page.append(Document.number(Math.max(0.1, lineWidth)) + " w\n");
		page.append(Document.number(x1) + " " + Document.number(toPdfY(y1)) + " m "
				+ Document.number(x2) + " " + Document.number(toPdfY(y2)) + " l S\n");
		return this;
	}

	/**
	 * Draws one text run using the given font and fill color.
	 *
	 * @param x        left edge
	 * @param y        top edge
	 * @param font     font
	 * @param fontSize font size in points
	 * @param color    fill color
	 * @param text     text content
	 * @return this canvas
	 */
	public Canvas drawText(double x, double y, Font font, double fontSize, Color color, String text) {
		if (text == null || text.isBlank()) {
			return this;
		}
		String baseText = normalizeBaseFontText(text);
		if (needsVectorText(baseText)) {
			return drawMixedText(x, y, font, fontSize, color, baseText);
		}
		return drawBaseText(x, y, font, fontSize, color, baseText);
	}

	/**
	 * Draws one text run using PDF text operators rotated around a page point.
	 *
	 * @param x left edge before rotation
	 * @param y top edge before rotation
	 * @param angleDegrees clockwise-positive angle in top-left page coordinates
	 * @param pivotX rotation pivot x coordinate
	 * @param pivotY rotation pivot y coordinate
	 * @param font font
	 * @param fontSize font size in points
	 * @param color fill color, including alpha
	 * @param text text content
	 * @return this canvas
	 */
	public Canvas drawTextRotated(double x, double y, double angleDegrees, double pivotX, double pivotY,
			Font font, double fontSize, Color color, String text) {
		if (text == null || text.isBlank()) {
			return this;
		}
		String baseText = normalizeBaseFontText(text);
		if (needsVectorText(baseText)) {
			return drawTextOutlineRotated(x, y, angleDegrees, pivotX, pivotY, font, fontSize, color, baseText);
		}

		AffineTransform placement = new AffineTransform();
		placement.rotate(Math.toRadians(angleDegrees), pivotX, pivotY);
		placement.translate(x, y + fontSize);

		Point2D origin = placement.transform(new Point2D.Double(0.0, 0.0), null);
		Point2D xBasis = placement.deltaTransform(new Point2D.Double(1.0, 0.0), null);
		Point2D yDown = placement.deltaTransform(new Point2D.Double(0.0, 1.0), null);
		return drawBaseTextTransformed(origin.getX(), toPdfY(origin.getY()), xBasis.getX(), -xBasis.getY(),
				-yDown.getX(), yDown.getY(), font, fontSize, color, baseText);
	}

	/**
	 * Draws one rotated base-font text run using a hex string operand.
	 *
	 * <p>
	 * This keeps compact repeated watermark text out of plain literal PDF strings
	 * while retaining normal PDF text rendering size. Text that cannot be emitted
	 * through the base font path still falls back to vector outlines.
	 * </p>
	 *
	 * @param x left edge before rotation
	 * @param y top edge before rotation
	 * @param angleDegrees clockwise-positive angle in top-left page coordinates
	 * @param pivotX rotation pivot x coordinate
	 * @param pivotY rotation pivot y coordinate
	 * @param font font
	 * @param fontSize font size in points
	 * @param color fill color, including alpha
	 * @param text text content
	 * @return this canvas
	 */
	public Canvas drawTextRotatedEncoded(double x, double y, double angleDegrees, double pivotX, double pivotY,
			Font font, double fontSize, Color color, String text) {
		if (text == null || text.isBlank()) {
			return this;
		}
		String baseText = normalizeBaseFontText(text);
		if (needsVectorText(baseText)) {
			return drawTextOutlineRotated(x, y, angleDegrees, pivotX, pivotY, font, fontSize, color, baseText);
		}

		AffineTransform placement = new AffineTransform();
		placement.rotate(Math.toRadians(angleDegrees), pivotX, pivotY);
		placement.translate(x, y + fontSize);

		Point2D origin = placement.transform(new Point2D.Double(0.0, 0.0), null);
		Point2D xBasis = placement.deltaTransform(new Point2D.Double(1.0, 0.0), null);
		Point2D yDown = placement.deltaTransform(new Point2D.Double(0.0, 1.0), null);
		return drawBaseTextTransformed(origin.getX(), toPdfY(origin.getY()), xBasis.getX(), -xBasis.getY(),
				-yDown.getX(), yDown.getY(), font, fontSize, color, baseText, true);
	}

	/**
	 * Draws one text run as filled vector outlines instead of PDF text.
	 *
	 * <p>
	 * This is intentionally used for watermark overlays and other marks where the
	 * visible text should not be a trivially removable/selectable text object.
	 * </p>
	 *
	 * @param x left edge
	 * @param y top edge
	 * @param font requested logical font
	 * @param fontSize requested font size
	 * @param color fill color, including alpha
	 * @param text text content
	 * @return this canvas
	 */
	public Canvas drawTextOutline(double x, double y, Font font, double fontSize, Color color, String text) {
		AffineTransform placement = AffineTransform.getTranslateInstance(x, y + fontSize);
		return drawVectorTextTransformed(placement, font, fontSize, color, text);
	}

	/**
	 * Draws one text run as filled vector outlines rotated around a page point.
	 *
	 * @param x left edge before rotation
	 * @param y top edge before rotation
	 * @param angleDegrees clockwise-positive angle in top-left page coordinates
	 * @param pivotX rotation pivot x coordinate
	 * @param pivotY rotation pivot y coordinate
	 * @param font requested logical font
	 * @param fontSize requested font size
	 * @param color fill color, including alpha
	 * @param text text content
	 * @return this canvas
	 */
	public Canvas drawTextOutlineRotated(double x, double y, double angleDegrees, double pivotX, double pivotY,
			Font font, double fontSize, Color color, String text) {
		AffineTransform placement = new AffineTransform();
		placement.rotate(Math.toRadians(angleDegrees), pivotX, pivotY);
		placement.translate(x, y + fontSize);
		return drawVectorTextTransformed(placement, font, fontSize, color, text);
	}

	/**
	 * Draws one base-font text run with PDF text operators.
	 *
	 * @param x left edge
	 * @param y top edge
	 * @param font font
	 * @param fontSize font size in points
	 * @param color fill color
	 * @param text already-normalized base-font text
	 * @return this canvas
	 */
	private Canvas drawBaseText(double x, double y, Font font, double fontSize, Color color, String text) {
		if (text == null || text.isBlank()) {
			return this;
		}
		return drawBaseTextTransformed(x, textBaseline(y, fontSize), 1.0, 0.0, 0.0, 1.0, font, fontSize, color,
				text);
	}

	/**
	 * Draws one base-font text run with a supplied PDF text matrix.
	 *
	 * @param e text matrix translation x
	 * @param f text matrix translation y
	 * @param a text matrix x scale/rotation x
	 * @param b text matrix x scale/rotation y
	 * @param c text matrix y scale/rotation x
	 * @param d text matrix y scale/rotation y
	 * @param font font
	 * @param fontSize font size in points
	 * @param color fill color, including alpha
	 * @param text already-normalized base-font text
	 * @return this canvas
	 */
	private Canvas drawBaseTextTransformed(double e, double f, double a, double b, double c, double d, Font font,
			double fontSize, Color color, String text) {
		return drawBaseTextTransformed(e, f, a, b, c, d, font, fontSize, color, text, false);
	}

	/**
	 * Draws one base-font text run with a supplied PDF text matrix.
	 *
	 * @param e text matrix translation x
	 * @param f text matrix translation y
	 * @param a text matrix x scale/rotation x
	 * @param b text matrix x scale/rotation y
	 * @param c text matrix y scale/rotation x
	 * @param d text matrix y scale/rotation y
	 * @param font font
	 * @param fontSize font size in points
	 * @param color fill color, including alpha
	 * @param text already-normalized base-font text
	 * @param hexEncode whether to emit the text operand as a PDF hex string
	 * @return this canvas
	 */
	private Canvas drawBaseTextTransformed(double e, double f, double a, double b, double c, double d, Font font,
			double fontSize, Color color, String text, boolean hexEncode) {
		if (text == null || text.isBlank()) {
			return this;
		}
		Color safe = color == null ? Color.BLACK : color;
		page.useFont(font);
		String opacityName = page.addOpacity(safe.getAlpha() / 255.0, 1.0);
		if (opacityName != null) {
			page.append("q\n/" + opacityName + " gs\n");
		}
		page.append("BT\n");
		page.append("/" + font.resourceName() + " " + Document.number(fontSize) + " Tf\n");
		appendFillColor(safe);
		page.append(Document.number(a) + " " + Document.number(b) + " "
				+ Document.number(c) + " " + Document.number(d) + " "
				+ Document.number(e) + " " + Document.number(f) + " Tm\n");
		if (hexEncode) {
			page.append("<" + hexText(text) + "> Tj\n");
		} else {
			page.append("(" + Document.escape(text) + ") Tj\n");
		}
		page.append("ET\n");
		if (opacityName != null) {
			page.append("Q\n");
		}
		return this;
	}

	/**
	 * Converts printable base-font text to a PDF hex string.
	 *
	 * @param text already-normalized base-font text
	 * @return uppercase hexadecimal bytes
	 */
	private String hexText(String text) {
		StringBuilder hex = new StringBuilder(text.length() * 2);
		for (int i = 0; i < text.length(); i++) {
			int value = text.charAt(i) & 0xFF;
			if (value < 0x10) {
				hex.append('0');
			}
			hex.append(Integer.toHexString(value).toUpperCase(Locale.ROOT));
		}
		return hex.toString();
	}

	/**
	 * Draws text containing both base-font characters and vector-only glyphs.
	 *
	 * <p>
	 * This preserves searchable ASCII text around figurine chess symbols and real
	 * list bullets while still keeping glyphs visible when they are not available
	 * through the simple PDF font encoding.
	 * </p>
	 *
	 * @param x left edge
	 * @param y top edge
	 * @param font font
	 * @param fontSize font size in points
	 * @param color fill color
	 * @param text normalized text containing at least one vector-only character
	 * @return this canvas
	 */
	private Canvas drawMixedText(double x, double y, Font font, double fontSize, Color color, String text) {
		double cursorX = x;
		int runStart = 0;
		boolean vectorRun = !isBaseFontCharacter(text.charAt(0));
		for (int i = 1; i < text.length(); i++) {
			boolean nextVector = !isBaseFontCharacter(text.charAt(i));
			if (nextVector == vectorRun) {
				continue;
			}
			String run = text.substring(runStart, i);
			cursorX = drawTextRun(cursorX, y, font, fontSize, color, run, vectorRun);
			runStart = i;
			vectorRun = nextVector;
		}
		drawTextRun(cursorX, y, font, fontSize, color, text.substring(runStart), vectorRun);
		return this;
	}

	/**
	 * Draws one split text run and returns the next x coordinate.
	 *
	 * @param x left edge
	 * @param y top edge
	 * @param font font
	 * @param fontSize font size in points
	 * @param color fill color
	 * @param text run text
	 * @param vector whether this run must be vectorized
	 * @return x coordinate immediately after the run
	 */
	private double drawTextRun(double x, double y, Font font, double fontSize, Color color, String text,
			boolean vector) {
		if (text.isEmpty()) {
			return x;
		}
		if (vector) {
			drawVectorText(x, y, font, fontSize, color, text);
			return x + vectorTextAdvance(font, fontSize, text);
		}
		drawBaseText(x, y, font, fontSize, color, text);
		return x + font.textWidth(text, fontSize);
	}

	/**
	 * Draws two adjacent text runs in one PDF text object.
	 *
	 * <p>
	 * This is useful for bold labels followed by regular text because the PDF
	 * text state advances naturally between the two font changes. That avoids
	 * manual width estimation gaps between adjacent runs.
	 * </p>
	 *
	 * @param x left edge
	 * @param y top edge
	 * @param firstFont first run font
	 * @param firstText first run text
	 * @param secondFont second run font
	 * @param secondText second run text
	 * @param fontSize font size in points
	 * @param color fill color
	 * @return this canvas
	 */
	public Canvas drawTextPair(double x, double y, Font firstFont, String firstText, Font secondFont,
			String secondText, double fontSize, Color color) {
		String left = firstText == null ? "" : firstText;
		String right = secondText == null ? "" : secondText;
		if (left.isBlank() && right.isBlank()) {
			return this;
		}

		String baseLeft = normalizeBaseFontText(left);
		String baseRight = normalizeBaseFontText(right);
		if (needsVectorText(baseLeft) || needsVectorText(baseRight)) {
			drawText(x, y, firstFont, fontSize, color, left);
			return drawText(x + firstFont.textWidth(left, fontSize), y, secondFont, fontSize, color, right);
		}

		page.useFont(firstFont);
		page.useFont(secondFont);
		page.append("BT\n");
		appendFillColor(color);
		page.append("1 0 0 1 " + Document.number(x) + " " + Document.number(textBaseline(y, fontSize))
				+ " Tm\n");
		appendTextRun(firstFont, fontSize, baseLeft);
		appendTextRun(secondFont, fontSize, baseRight);
		page.append("ET\n");
		return this;
	}

	/**
	 * Draws wrapped text and returns the vertical space consumed.
	 *
	 * @param x        left edge
	 * @param y        top edge
	 * @param width    wrap width
	 * @param font     font
	 * @param fontSize font size
	 * @param leading  line advance
	 * @param color    fill color
	 * @param text     paragraph text
	 * @return consumed height in points
	 */
	public double drawWrappedText(double x, double y, double width, Font font, double fontSize, double leading,
			Color color, String text) {
		List<String> lines = wrapLines(text, font, fontSize, width);
		double cursorY = y;
		for (String line : lines) {
			if (!line.isBlank()) {
				drawText(x, cursorY, font, fontSize, color, line);
			}
			cursorY += leading;
		}
		return cursorY - y;
	}

	/**
	 * Appends one base-font text run inside an active PDF text object.
	 *
	 * @param font run font
	 * @param fontSize font size in points
	 * @param text already-normalized base-font text
	 */
	private void appendTextRun(Font font, double fontSize, String text) {
		if (text == null || text.isEmpty()) {
			return;
		}
		page.append("/" + font.resourceName() + " " + Document.number(fontSize) + " Tf\n");
		page.append("(" + Document.escape(text) + ") Tj\n");
	}

	/**
	 * Measures wrapped text height without drawing it.
	 *
	 * @param width    wrap width
	 * @param font     font
	 * @param fontSize font size
	 * @param leading  line advance
	 * @param text     paragraph text
	 * @return wrapped height
	 */
	public double measureWrappedText(double width, Font font, double fontSize, double leading, String text) {
		return wrapLines(text, font, fontSize, width).size() * leading;
	}

	/**
	 * Wraps text into layout lines.
	 *
	 * @param text     source text
	 * @param font     font
	 * @param fontSize font size
	 * @param width    wrap width
	 * @return wrapped lines
	 */
	public List<String> wrapLines(String text, Font font, double fontSize, double width) {
		List<String> lines = new ArrayList<>();
		if (text == null || text.isBlank()) {
			return lines;
		}
		if (width <= 0.0) {
			lines.add(text.trim());
			return lines;
		}

		String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
		String[] paragraphs = normalized.split("\n", -1);
		for (String paragraph : paragraphs) {
			if (paragraph.isBlank()) {
				lines.add("");
				continue;
			}

			String[] words = paragraph.trim().split("\\s+");
			StringBuilder line = new StringBuilder();
			for (String word : words) {
				appendWrappedWord(lines, line, word, font, fontSize, width);
			}
			if (!line.isEmpty()) {
				lines.add(line.toString());
			}
		}
		return lines;
	}

	/**
	 * Measures a single text run.
	 *
	 * @param font     font
	 * @param fontSize font size
	 * @param text     text content
	 * @return measured width
	 */
	public double textWidth(Font font, double fontSize, String text) {
		return font.textWidth(text, fontSize);
	}

	/**
	 * Returns a line height suggestion for the given font and size.
	 *
	 * @param font     font
	 * @param fontSize font size
	 * @return measured line height
	 */
	public double lineHeight(Font font, double fontSize) {
		return font.lineHeight(fontSize);
	}

	/**
	 * Draws an image resource.
	 *
	 * @param image  image to place
	 * @param x      left edge
	 * @param y      top edge
	 * @param width  width in points
	 * @param height height in points
	 * @return this canvas
	 */
	public Canvas drawImage(BufferedImage image, double x, double y, double width, double height) {
		if (image == null || width <= 0.0 || height <= 0.0) {
			return this;
		}
		String name = page.addImage(image);
		page.append("q\n");
		page.append(Document.number(width) + " 0 0 " + Document.number(height) + " "
				+ Document.number(x) + " " + Document.number(toPdfY(y + height)) + " cm\n");
		page.append("/" + name + " Do\n");
		page.append("Q\n");
		return this;
	}

	/**
	 * Draws SVG content as vector commands.
	 *
	 * @param svgText SVG source text
	 * @param x       left edge
	 * @param y       top edge
	 * @param width   width in points
	 * @param height  height in points
	 * @return this canvas
	 */
	public Canvas drawSvg(String svgText, double x, double y, double width, double height) {
		if (svgText == null || svgText.isBlank() || width <= 0.0 || height <= 0.0) {
			return this;
		}
		Svg.draw(page, svgText, x, y, width, height);
		return this;
	}

	/**
	 * Adds an invisible internal link rectangle to another page.
	 *
	 * @param x left edge
	 * @param y top edge
	 * @param width link width
	 * @param height link height
	 * @param targetPageNumber one-based destination page number
	 * @return this canvas
	 */
	public Canvas linkToPage(double x, double y, double width, double height, int targetPageNumber) {
		page.addPageLink(x, y, width, height, targetPageNumber);
		return this;
	}

	/**
	 * Draws text as filled vector outlines for characters outside PDF base-font
	 * ASCII support.
	 *
	 * <p>
	 * This keeps multilingual book labels and prose visible without depending on
	 * LaTeX or rasterized text. ASCII text still uses normal PDF text operators so
	 * compact English output remains searchable.
	 * </p>
	 *
	 * @param x left edge
	 * @param y top edge
	 * @param font requested logical font
	 * @param fontSize requested font size
	 * @param color fill color
	 * @param text text content
	 * @return this canvas
	 */
	private Canvas drawVectorText(double x, double y, Font font, double fontSize, Color color, String text) {
		return drawTextOutline(x, y, font, fontSize, color, text);
	}

	/**
	 * Draws text outlines with an arbitrary top-left-coordinate transform.
	 *
	 * @param placement Java2D transform applied before path emission
	 * @param font requested logical font
	 * @param fontSize requested font size
	 * @param color fill color, including alpha
	 * @param text text content
	 * @return this canvas
	 */
	private Canvas drawVectorTextTransformed(AffineTransform placement, Font font, double fontSize, Color color,
			String text) {
		if (text == null || text.isBlank()) {
			return this;
		}
		Color safe = color == null ? Color.BLACK : color;
		TextLayout layout = new TextLayout(text, vectorAwtFont(font, fontSize, text), VECTOR_FONT_CONTEXT);
		Shape outline = layout.getOutline(placement);
		page.append("q\n");
		appendFillColor(safe);
		String opacityName = page.addOpacity(safe.getAlpha() / 255.0, 1.0);
		if (opacityName != null) {
			page.append("/" + opacityName + " gs\n");
		}
		appendShapePath(outline);
		page.append("f\n");
		page.append("Q\n");
		return this;
	}

	/**
	 * Measures the advance of a vector text run.
	 *
	 * @param font requested logical font
	 * @param fontSize requested font size
	 * @param text text content
	 * @return vector text advance in points
	 */
	private double vectorTextAdvance(Font font, double fontSize, String text) {
		TextLayout layout = new TextLayout(text, vectorAwtFont(font, fontSize, text), VECTOR_FONT_CONTEXT);
		return layout.getAdvance();
	}

	/**
	 * Chooses a vector font capable of displaying the supplied text.
	 *
	 * @param font requested PDF font
	 * @param fontSize requested font size
	 * @param text text to display as outlines
	 * @return AWT font that can display the text when one is available
	 */
	private java.awt.Font vectorAwtFont(Font font, double fontSize, String text) {
		java.awt.Font requested = font.awtFont(fontSize);
		if (requested.canDisplayUpTo(text) < 0) {
			return requested;
		}
		for (String family : VECTOR_FALLBACK_FAMILIES) {
			java.awt.Font candidate = new java.awt.Font(family, requested.getStyle(), 1)
					.deriveFont((float) Math.max(1.0, fontSize));
			if (candidate.canDisplayUpTo(text) < 0) {
				return candidate;
			}
		}
		return requested;
	}

	/**
	 * Returns whether a text run needs vector fallback instead of PDF base-font
	 * text operators.
	 *
	 * @param text text to inspect
	 * @return true when any character is outside printable ASCII
	 */
	private boolean needsVectorText(String text) {
		for (int i = 0; i < text.length(); i++) {
			if (!isBaseFontCharacter(text.charAt(i))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns whether a character can be emitted through the simple PDF font path.
	 *
	 * @param ch character to inspect
	 * @return true for printable ASCII characters
	 */
	private boolean isBaseFontCharacter(char ch) {
		return ch >= 32 && ch <= 126;
	}

	/**
	 * Replaces common typographic punctuation with base-font-compatible ASCII.
	 *
	 * <p>
	 * PDF base fonts in this writer are emitted through ASCII literal strings.
	 * Without this normalization, a single curly quote or em dash routes an
	 * otherwise ordinary English line through vector outline text, which makes the
	 * same paragraph appear to change font size. True non-ASCII scripts still use
	 * the vector fallback.
	 * </p>
	 *
	 * @param text source text
	 * @return text suitable for the PDF base-font path when possible
	 */
	private String normalizeBaseFontText(String text) {
		StringBuilder builder = null;
		for (int i = 0; i < text.length(); i++) {
			char ch = text.charAt(i);
			String replacement = asciiReplacement(ch);
			if (replacement == null) {
				if (builder != null) {
					builder.append(ch);
				}
				continue;
			}
			if (builder == null) {
				builder = new StringBuilder(text.length() + 8);
				builder.append(text, 0, i);
			}
			builder.append(replacement);
		}
		return builder == null ? text : builder.toString();
	}

	/**
	 * Returns an ASCII replacement for punctuation supported by PDF base fonts.
	 *
	 * @param ch character to map
	 * @return ASCII replacement, or null when no replacement is available
	 */
	private String asciiReplacement(char ch) {
		return switch (ch) {
			case '\u00A0' -> " ";
			case '\u2018', '\u2019', '\u201A', '\u201B' -> "'";
			case '\u201C', '\u201D', '\u201E', '\u201F' -> "\"";
			case '\u2010', '\u2011', '\u2012', '\u2013', '\u2014', '\u2015', '\u2212' -> "-";
			case '\u2026' -> "...";
			default -> null;
		};
	}

	/**
	 * Appends one Java2D shape as a filled PDF path.
	 *
	 * @param shape outline in top-left page coordinates
	 */
	private void appendShapePath(Shape shape) {
		PathIterator iterator = shape.getPathIterator(null);
		double[] coords = new double[6];
		double currentX = 0.0;
		double currentY = 0.0;

		while (!iterator.isDone()) {
			int type = iterator.currentSegment(coords);
			switch (type) {
				case PathIterator.SEG_MOVETO -> {
					currentX = coords[0];
					currentY = coords[1];
					page.append(Document.number(currentX) + " " + Document.number(toPdfY(currentY)) + " m\n");
				}
				case PathIterator.SEG_LINETO -> {
					currentX = coords[0];
					currentY = coords[1];
					page.append(Document.number(currentX) + " " + Document.number(toPdfY(currentY)) + " l\n");
				}
				case PathIterator.SEG_QUADTO -> {
					double x1 = currentX + (coords[0] - currentX) * (2.0 / 3.0);
					double y1 = currentY + (coords[1] - currentY) * (2.0 / 3.0);
					double x2 = coords[2] + (coords[0] - coords[2]) * (2.0 / 3.0);
					double y2 = coords[3] + (coords[1] - coords[3]) * (2.0 / 3.0);
					currentX = coords[2];
					currentY = coords[3];
					page.append(Document.number(x1) + " " + Document.number(toPdfY(y1)) + " "
							+ Document.number(x2) + " " + Document.number(toPdfY(y2)) + " "
							+ Document.number(currentX) + " " + Document.number(toPdfY(currentY)) + " c\n");
				}
				case PathIterator.SEG_CUBICTO -> {
					currentX = coords[4];
					currentY = coords[5];
					page.append(Document.number(coords[0]) + " " + Document.number(toPdfY(coords[1])) + " "
							+ Document.number(coords[2]) + " " + Document.number(toPdfY(coords[3])) + " "
							+ Document.number(currentX) + " " + Document.number(toPdfY(currentY)) + " c\n");
				}
				case PathIterator.SEG_CLOSE -> page.append("h\n");
				default -> throw new IllegalArgumentException("Unsupported text outline segment: " + type);
			}
			iterator.next();
		}
	}

	/**
	 * Appends one word to the current wrapped line or starts a new wrapped line.
	 *
	 * @param lines output line list
	 * @param line current line buffer
	 * @param word word to append
	 * @param font measurement font
	 * @param fontSize measurement font size
	 * @param width available width
	 */
	private void appendWrappedWord(List<String> lines, StringBuilder line, String word, Font font, double fontSize,
			double width) {
		String candidate = line.isEmpty() ? word : line + " " + word;
		if (line.isEmpty() || font.textWidth(candidate, fontSize) <= width) {
			line.setLength(0);
			line.append(candidate);
			return;
		}

		lines.add(line.toString());
		appendBrokenWord(lines, word, font, fontSize, width, line);
	}

	/**
	 * Adds line breaks for a single word wider than the available text width.
	 *
	 * @param lines output line list
	 * @param word oversized word
	 * @param font measurement font
	 * @param fontSize measurement font size
	 * @param width available width
	 * @param line current line buffer
	 */
	private void appendBrokenWord(List<String> lines, String word, Font font, double fontSize, double width,
			StringBuilder line) {
		line.setLength(0);
		StringBuilder chunk = new StringBuilder();
		for (int i = 0; i < word.length(); i++) {
			chunk.append(word.charAt(i));
			if (font.textWidth(chunk.toString(), fontSize) > width && chunk.length() > 1) {
				String carry = chunk.substring(0, chunk.length() - 1);
				lines.add(carry);
				chunk.delete(0, chunk.length() - 1);
			}
		}
		line.append(chunk);
	}

	/**
	 * Appends the current fill color command.
	 *
	 * @param color requested fill color
	 */
	private void appendFillColor(Color color) {
		Color safe = color == null ? Color.BLACK : color;
		page.append(Document.rgb(safe) + " rg\n");
	}

	/**
	 * Appends the current stroke color command.
	 *
	 * @param color requested stroke color
	 */
	private void appendStrokeColor(Color color) {
		Color safe = color == null ? Color.BLACK : color;
		page.append(Document.rgb(safe) + " RG\n");
	}

	/**
	 * Converts a top-edge text coordinate into a PDF baseline coordinate.
	 *
	 * @param topY text top edge in top-left coordinates
	 * @param fontSize font size in points
	 * @return PDF baseline y coordinate
	 */
	private double textBaseline(double topY, double fontSize) {
		return toPdfY(topY + fontSize);
	}

	/**
	 * Converts a top-left-origin y coordinate into a PDF bottom-left-origin y
	 * coordinate.
	 *
	 * @param topOriginY top-left-origin y coordinate
	 * @return PDF y coordinate
	 */
	private double toPdfY(double topOriginY) {
		return page.getHeight() - topOriginY;
	}
}
