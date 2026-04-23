package chess.book.cover;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import chess.book.model.Book;
import chess.book.model.Language;
import chess.pdf.DocumentMetrics;
import chess.pdf.document.Canvas;
import chess.pdf.document.Document;
import chess.pdf.document.Font;
import chess.pdf.document.Page;

/**
 * Writes native vector PDFs for book covers.
 *
 * <p>
 * The dimension formulas derive spine width from page count and paper
 * thickness. Paperback covers include print bleed and safe areas, and hardcover
 * covers add wrap and hinge space.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Writer {

	/**
	 * Error text used when no book model is supplied.
	 */
	private static final String BOOK_NULL_MESSAGE = "book cannot be null";

	/**
	 * Number of centimeters in one inch.
	 */
	private static final double CENTIMETERS_PER_INCH = 2.54;

	/**
	 * Paperback cover bleed on every outside edge.
	 */
	private static final double PAPERBACK_BLEED_CM = inchesToCm(0.125);

	/**
	 * Minimum front/back cover text inset from each trim line.
	 */
	private static final double PAPERBACK_TEXT_MARGIN_CM = PAPERBACK_BLEED_CM;

	/**
	 * Paperback and hardcover spine text inset from each fold line.
	 */
	private static final double SPINE_TEXT_MARGIN_CM = inchesToCm(0.0625);

	/**
	 * Hardcover wrap allowance.
	 */
	private static final double HARD_COVER_WRAP_CM = 1.5;

	/**
	 * Hardcover hinge allowance.
	 */
	private static final double HARD_COVER_HINGE_CM = 1.0;

	/**
	 * Hardcover front/back text inset from each visible book edge.
	 */
	private static final double HARD_COVER_TEXT_MARGIN_CM = inchesToCm(0.635);

	/**
	 * Hardcover barcode bottom inset from the visible book edge.
	 */
	private static final double HARD_COVER_BARCODE_BOTTOM_MARGIN_CM = inchesToCm(0.76);

	/**
	 * Barcode inset from the spine or trim line.
	 */
	private static final double BARCODE_MARGIN_CM = inchesToCm(0.25);

	/**
	 * Minimum drawable spine safe-area width.
	 */
	private static final double MIN_SPINE_SAFE_CM = 0.0;

	/**
	 * Fraction of each cover panel used for major text blocks.
	 */
	private static final double TEXT_WIDTH_RATIO = 0.75;

	/**
	 * Barcode placeholder width.
	 */
	private static final double BARCODE_WIDTH_CM = inchesToCm(2.0);

	/**
	 * Barcode placeholder height.
	 */
	private static final double BARCODE_HEIGHT_CM = inchesToCm(1.2);

	/**
	 * Cover sheet background color.
	 */
	private static final Color PAPER = new Color(249, 247, 240);

	/**
	 * Front and back panel tint.
	 */
	private static final Color PANEL = new Color(255, 253, 247);

	/**
	 * Spine tint.
	 */
	private static final Color SPINE = new Color(235, 231, 218);

	/**
	 * Primary text color.
	 */
	private static final Color INK = new Color(28, 29, 26);

	/**
	 * Secondary text and rule color.
	 */
	private static final Color MUTED = new Color(103, 100, 91);

	/**
	 * Accent rule color.
	 */
	private static final Color ACCENT = new Color(151, 123, 54);

	/**
	 * Light guide-stroke color.
	 */
	private static final Color GUIDE = new Color(191, 187, 174);

	/**
	 * Text box used by cover text helpers.
	 *
	 * @param x left edge
	 * @param y top edge
	 * @param width available width
	 * @param height available height
	 */
	private record TextBox(
		/**
		 * Stores the x.
		 */
		double x,
		/**
		 * Stores the y.
		 */
		double y,
		/**
		 * Stores the width.
		 */
		double width,
		/**
		 * Stores the height.
		 */
		double height
	) {
	}

	/**
	 * Font and allowed size range for fitted cover text.
	 *
	 * @param font text font
	 * @param maxSize maximum font size
	 * @param minSize minimum font size
	 */
	private record FontRange(
		/**
		 * Stores the font.
		 */
		Font font,
		/**
		 * Stores the max size.
		 */
		double maxSize,
		/**
		 * Stores the min size.
		 */
		double minSize
	) {
	}

	/**
	 * Centered line geometry for rotated spine text.
	 *
	 * @param centerX center x coordinate
	 * @param centerY center y coordinate
	 * @param availableLength available rotated length
	 */
	private record RotatedLine(
		/**
		 * Stores the center x.
		 */
		double centerX,
		/**
		 * Stores the center y.
		 */
		double centerY,
		/**
		 * Stores the available length.
		 */
		double availableLength
	) {
	}

	/**
	 * Utility class; prevent instantiation.
	 */
	private Writer() {
		// utility
	}

	/**
	 * Writes a cover PDF to disk.
	 *
	 * @param output destination PDF path
	 * @param book source book metadata
	 * @param options cover rendering options
	 * @throws IOException if the PDF cannot be written
	 */
	public static void write(Path output, Book book, Options options) throws IOException {
		if (output == null) {
			throw new IllegalArgumentException("output cannot be null");
		}
		if (book == null) {
			throw new IllegalArgumentException(BOOK_NULL_MESSAGE);
		}
		Options safeOptions = options == null ? new Options() : options;
		Dimensions dimensions = calculateDimensions(book, safeOptions);

		Document document = new Document()
				.setTitle(book.getFullTitle() + " cover")
				.setAuthor(book.getAuthor())
				.setSubject("Book cover")
				.setCreator("chess.book.cover.Writer")
				.setProducer("chess-rtk native book cover pdf");
		Page page = document.addPage(dimensions.toPageSize());
		drawCover(page.canvas(), book, dimensions);
		document.write(output);
	}

	/**
	 * Calculates cover dimensions from a book and rendering options.
	 *
	 * @param book source book metadata
	 * @param options cover rendering options
	 * @return calculated cover dimensions
	 */
	public static Dimensions calculateDimensions(Book book, Options options) {
		if (book == null) {
			throw new IllegalArgumentException(BOOK_NULL_MESSAGE);
		}
		Options safeOptions = options == null ? new Options() : options;
		Binding binding = safeOptions.getBinding();
		Interior interior = safeOptions.getInterior();
		DocumentMetrics interiorPdf = safeOptions.getInteriorPdfMetrics();
		int pages = resolvePages(book, safeOptions, interiorPdf);
		double trimWidth = resolveTrimWidth(book, interiorPdf);
		double trimHeight = resolveTrimHeight(book, interiorPdf);
		double spineWidth = binding == Binding.EBOOK ? 0.0 : pages * interior.getPaperThicknessCm();

		if (binding == Binding.HARDCOVER) {
			return hardcoverDimensions(interior, pages, trimWidth, trimHeight, spineWidth);
		}
		if (binding == Binding.EBOOK) {
			return ebookDimensions(interior, pages, trimWidth, trimHeight);
		}
		return paperbackDimensions(interior, pages, trimWidth, trimHeight, spineWidth);
	}

	/**
	 * Calculates paperback cover dimensions.
	 *
	 * @param interior interior paper choice
	 * @param pages printed page count
	 * @param trimWidth trim width in centimeters
	 * @param trimHeight trim height in centimeters
	 * @param spineWidth spine width in centimeters
	 * @return paperback dimensions
	 */
	private static Dimensions paperbackDimensions(Interior interior, int pages, double trimWidth,
			double trimHeight, double spineWidth) {
		double safeSpineWidth = Math.max(MIN_SPINE_SAFE_CM, spineWidth - (2.0 * SPINE_TEXT_MARGIN_CM));
		double backX = PAPERBACK_BLEED_CM + PAPERBACK_TEXT_MARGIN_CM;
		double panelY = PAPERBACK_BLEED_CM + PAPERBACK_TEXT_MARGIN_CM;
		double panelWidth = Math.max(0.0, trimWidth - (2.0 * PAPERBACK_TEXT_MARGIN_CM));
		double panelHeight = Math.max(0.0, trimHeight - (2.0 * PAPERBACK_TEXT_MARGIN_CM));
		double physicalSpineX = PAPERBACK_BLEED_CM + trimWidth;
		double physicalFrontX = physicalSpineX + spineWidth;
		Dimensions.Area back = new Dimensions.Area(
				backX,
				panelY,
				panelWidth,
				panelHeight);
		Dimensions.Area spine = new Dimensions.Area(
				physicalSpineX + SPINE_TEXT_MARGIN_CM,
				panelY,
				safeSpineWidth,
				panelHeight);
		Dimensions.Area front = new Dimensions.Area(
				physicalFrontX + PAPERBACK_TEXT_MARGIN_CM,
				panelY,
				panelWidth,
				panelHeight);
		double fullWidth = (2.0 * trimWidth) + spineWidth + (2.0 * PAPERBACK_BLEED_CM);
		double fullHeight = trimHeight + (2.0 * PAPERBACK_BLEED_CM);
		return new Dimensions(Binding.PAPERBACK, interior, pages, trimWidth, trimHeight, spineWidth,
				fullWidth, fullHeight, back, spine, front);
	}

	/**
	 * Calculates hardcover cover dimensions.
	 *
	 * @param interior interior paper choice
	 * @param pages printed page count
	 * @param trimWidth trim width in centimeters
	 * @param trimHeight trim height in centimeters
	 * @param spineWidth spine width in centimeters
	 * @return hardcover dimensions
	 */
	private static Dimensions hardcoverDimensions(Interior interior, int pages, double trimWidth,
			double trimHeight, double spineWidth) {
		double safeSpineWidth = Math.max(MIN_SPINE_SAFE_CM, spineWidth - (2.0 * SPINE_TEXT_MARGIN_CM));
		double panelY = HARD_COVER_WRAP_CM + HARD_COVER_TEXT_MARGIN_CM;
		double panelWidth = Math.max(0.0, trimWidth - (2.0 * HARD_COVER_TEXT_MARGIN_CM));
		double panelHeight = Math.max(0.0, trimHeight - (2.0 * HARD_COVER_TEXT_MARGIN_CM));
		double backX = HARD_COVER_WRAP_CM + HARD_COVER_TEXT_MARGIN_CM;
		double physicalSpineX = HARD_COVER_WRAP_CM + trimWidth + HARD_COVER_HINGE_CM;
		double physicalFrontX = physicalSpineX + spineWidth + HARD_COVER_HINGE_CM;
		Dimensions.Area back = new Dimensions.Area(
				backX,
				panelY,
				panelWidth,
				panelHeight);
		Dimensions.Area spine = new Dimensions.Area(
				physicalSpineX + SPINE_TEXT_MARGIN_CM,
				panelY,
				safeSpineWidth,
				panelHeight);
		Dimensions.Area front = new Dimensions.Area(
				physicalFrontX + HARD_COVER_TEXT_MARGIN_CM,
				panelY,
				panelWidth,
				panelHeight);
		double fullWidth = (2.0 * (trimWidth + HARD_COVER_HINGE_CM + HARD_COVER_WRAP_CM)) + spineWidth;
		double fullHeight = trimHeight + (2.0 * HARD_COVER_WRAP_CM);
		return new Dimensions(Binding.HARDCOVER, interior, pages, trimWidth, trimHeight, spineWidth,
				fullWidth, fullHeight, back, spine, front);
	}

	/**
	 * Calculates front-only electronic cover dimensions.
	 *
	 * @param interior interior paper choice retained in metadata
	 * @param pages inferred page count retained in metadata
	 * @param trimWidth trim width in centimeters
	 * @param trimHeight trim height in centimeters
	 * @return ebook front-cover dimensions
	 */
	private static Dimensions ebookDimensions(Interior interior, int pages, double trimWidth, double trimHeight) {
		Dimensions.Area front = new Dimensions.Area(
				PAPERBACK_TEXT_MARGIN_CM,
				PAPERBACK_TEXT_MARGIN_CM,
				Math.max(0.0, trimWidth - (2.0 * PAPERBACK_TEXT_MARGIN_CM)),
				Math.max(0.0, trimHeight - (2.0 * PAPERBACK_TEXT_MARGIN_CM)));
		return new Dimensions(Binding.EBOOK, interior, pages, trimWidth, trimHeight, 0.0,
				trimWidth, trimHeight, null, null, front);
	}

	/**
	 * Draws all cover panels.
	 *
	 * @param canvas target drawing canvas
	 * @param book source book metadata
	 * @param dimensions cover dimensions
	 */
	private static void drawCover(Canvas canvas, Book book, Dimensions dimensions) {
		double width = points(dimensions.fullWidthCm());
		double height = points(dimensions.fullHeightCm());
		drawBackground(canvas, dimensions, width, height);
		drawBackPanel(canvas, book, dimensions);
		drawFrontPanel(canvas, book, dimensions.front());
		drawSpinePanel(canvas, book, dimensions);
	}

	/**
	 * Draws the cover background, panel fills, and trim guides.
	 *
	 * @param canvas target drawing canvas
	 * @param dimensions cover dimensions
	 * @param width full page width in points
	 * @param height full page height in points
	 */
	private static void drawBackground(Canvas canvas, Dimensions dimensions, double width, double height) {
		canvas.fillRect(0.0, 0.0, width, height, PAPER);
		drawDecorativeRules(canvas, width, height);
		drawAreaFill(canvas, dimensions.back(), PANEL);
		drawSpineFill(canvas, dimensions);
		drawAreaFill(canvas, dimensions.front(), PANEL);
		drawAreaStroke(canvas, dimensions.back(), GUIDE, 0.45);
		drawAreaStroke(canvas, dimensions.spine(), GUIDE, 0.45);
		drawAreaStroke(canvas, dimensions.front(), GUIDE, 0.45);
	}

	/**
	 * Draws quiet horizontal cover rules.
	 *
	 * @param canvas target drawing canvas
	 * @param width full page width in points
	 * @param height full page height in points
	 */
	private static void drawDecorativeRules(Canvas canvas, double width, double height) {
		canvas.line(0.0, height * 0.08, width, height * 0.08, ACCENT, 0.7);
		canvas.line(0.0, height * 0.92, width, height * 0.92, ACCENT, 0.7);
		canvas.line(0.0, height * 0.0815, width, height * 0.0815, MUTED, 0.25);
		canvas.line(0.0, height * 0.9185, width, height * 0.9185, MUTED, 0.25);
	}

	/**
	 * Draws the physical spine background band.
	 *
	 * @param canvas target drawing canvas
	 * @param dimensions cover dimensions
	 */
	private static void drawSpineFill(Canvas canvas, Dimensions dimensions) {
		if (dimensions.spineWidthCm() <= 0.0) {
			return;
		}
		double x = points(physicalSpineX(dimensions));
		double width = points(dimensions.spineWidthCm());
		canvas.fillRect(Math.max(0.0, x), 0.0, Math.max(0.0, width), points(dimensions.fullHeightCm()), SPINE);
	}

	/**
	 * Draws one area fill.
	 *
	 * @param canvas target drawing canvas
	 * @param area area in centimeters
	 * @param color fill color
	 */
	private static void drawAreaFill(Canvas canvas, Dimensions.Area area, Color color) {
		if (area.widthCm() <= 0.0 || area.heightCm() <= 0.0) {
			return;
		}
		canvas.fillRect(points(area.xCm()), points(area.yCm()), points(area.widthCm()), points(area.heightCm()), color);
	}

	/**
	 * Draws one area border.
	 *
	 * @param canvas target drawing canvas
	 * @param area area in centimeters
	 * @param color stroke color
	 * @param lineWidth stroke width in points
	 */
	private static void drawAreaStroke(Canvas canvas, Dimensions.Area area, Color color, double lineWidth) {
		if (area.widthCm() <= 0.0 || area.heightCm() <= 0.0) {
			return;
		}
		canvas.strokeRect(points(area.xCm()), points(area.yCm()), points(area.widthCm()), points(area.heightCm()),
				color, lineWidth);
	}

	/**
	 * Draws the back cover blurb and barcode-safe box.
	 *
	 * @param canvas target drawing canvas
	 * @param book source book metadata
	 * @param dimensions cover dimensions
	 */
	private static void drawBackPanel(Canvas canvas, Book book, Dimensions dimensions) {
		Dimensions.Area back = dimensions.back();
		if (back.widthCm() <= 0.0 || back.heightCm() <= 0.0) {
			return;
		}
		double x = points(back.xCm());
		double y = points(back.yCm());
		double width = points(back.widthCm());
		double height = points(back.heightCm());
		double inset = width * ((1.0 - TEXT_WIDTH_RATIO) / 2.0);
		String blurb = coverBlurb(book);
		if (!blurb.isBlank()) {
			drawCenteredLine(canvas, "About this book",
					new TextBox(x + inset, y + height * 0.12, width - (2.0 * inset), 0.0),
					new FontRange(Font.LATIN_MODERN_BOLD, 13.0, 6.0), MUTED);
			canvas.drawWrappedText(x + inset, y + height * 0.21, width - (2.0 * inset),
					Font.LATIN_MODERN_ROMAN, 10.5, 14.0, INK, blurb);
		}
		drawBarcodeBox(canvas, dimensions);
	}

	/**
	 * Draws the front cover title, subtitle, and author.
	 *
	 * @param canvas target drawing canvas
	 * @param book source book metadata
	 * @param front front-cover safe text area
	 */
	private static void drawFrontPanel(Canvas canvas, Book book, Dimensions.Area front) {
		if (front.widthCm() <= 0.0 || front.heightCm() <= 0.0) {
			return;
		}
		double x = points(front.xCm());
		double y = points(front.yCm());
		double width = points(front.widthCm());
		double height = points(front.heightCm());
		double textX = x + width * ((1.0 - TEXT_WIDTH_RATIO) / 2.0);
		double textWidth = width * TEXT_WIDTH_RATIO;
		drawCenteredWrapped(canvas, titleText(book), new TextBox(textX, y + height * 0.12,
				textWidth, height * 0.30), new FontRange(Font.LATIN_MODERN_BOLD, 42.0, 15.0), INK);
		if (!book.getSubtitle().isBlank()) {
			drawCenteredWrapped(canvas, book.getSubtitle(), new TextBox(textX, y + height * 0.45,
					textWidth, height * 0.11), new FontRange(Font.LATIN_MODERN_ROMAN, 16.0, 9.0), MUTED);
		}
		drawCenteredLine(canvas, book.getAuthor(), new TextBox(textX, y + height * 0.78, textWidth, 0.0),
				new FontRange(Font.LATIN_MODERN_ROMAN, 15.0, 6.0), INK);
	}

	/**
	 * Draws rotated title and author text on the spine.
	 *
	 * @param canvas target drawing canvas
	 * @param book source book metadata
	 * @param dimensions cover dimensions
	 */
	private static void drawSpinePanel(Canvas canvas, Book book, Dimensions dimensions) {
		if (!dimensions.hasSpine()) {
			return;
		}
		Dimensions.Area spine = dimensions.spine();
		double x = points(spine.xCm());
		double y = points(spine.yCm());
		double width = points(spine.widthCm());
		double height = points(spine.heightCm());
		if (width < 8.0 || height < 48.0) {
			return;
		}
		double centerX = x + (width / 2.0);
		drawRotatedCenteredLine(canvas, book.getFullTitle(), new RotatedLine(centerX, y + height * 0.42,
				height * 0.62), new FontRange(Font.LATIN_MODERN_BOLD, Math.min(13.0, width * 0.80), 5.0), INK);
		drawRotatedCenteredLine(canvas, book.getAuthor(), new RotatedLine(centerX, y + height * 0.80,
				height * 0.22), new FontRange(Font.LATIN_MODERN_ROMAN, Math.min(10.0, width * 0.70), 4.5), MUTED);
	}

	/**
	 * Draws a blank barcode-safe box on the back cover.
	 *
	 * @param canvas target drawing canvas
	 * @param dimensions cover dimensions
	 */
	private static void drawBarcodeBox(Canvas canvas, Dimensions dimensions) {
		Dimensions.Area back = dimensions.back();
		if (back.widthCm() <= 0.0 || back.heightCm() <= 0.0) {
			return;
		}
		double width = points(BARCODE_WIDTH_CM);
		double height = points(BARCODE_HEIGHT_CM);
		double xCm = barcodeX(dimensions);
		double yCm = barcodeY(dimensions);
		double x = points(Math.max(0.0, xCm));
		double y = points(Math.max(0.0, yCm));
		canvas.fillRect(x, y, width, height, Color.WHITE);
		canvas.strokeRect(x, y, width, height, GUIDE, 0.45);
	}

	/**
	 * Draws wrapped text centered in a box.
	 *
	 * @param canvas target drawing canvas
	 * @param text source text
	 * @param box text box
	 * @param range font range
	 * @param color text color
	 */
	private static void drawCenteredWrapped(Canvas canvas, String text, TextBox box, FontRange range, Color color) {
		String safe = normalizeText(text);
		if (safe.isBlank()) {
			return;
		}
		double fontSize = fitWrappedFontSize(canvas, safe, box, range);
		double leading = fontSize * 1.16;
		List<String> lines = canvas.wrapLines(safe, range.font(), fontSize, box.width());
		double totalHeight = lines.size() * leading;
		double cursorY = box.y() + Math.max(0.0, (box.height() - totalHeight) / 2.0);
		for (String line : lines) {
			double lineWidth = range.font().textWidth(line, fontSize);
			canvas.drawText(box.x() + Math.max(0.0, (box.width() - lineWidth) / 2.0), cursorY,
					range.font(), fontSize, color, line);
			cursorY += leading;
		}
	}

	/**
	 * Draws a single line centered in a box.
	 *
	 * @param canvas target drawing canvas
	 * @param text source text
	 * @param box text box
	 * @param range font range
	 * @param color text color
	 */
	private static void drawCenteredLine(Canvas canvas, String text, TextBox box, FontRange range, Color color) {
		String safe = normalizeText(text);
		if (safe.isBlank()) {
			return;
		}
		double fitted = fitSingleLineFontSize(safe, range, box.width());
		double textWidth = range.font().textWidth(safe, fitted);
		canvas.drawText(box.x() + Math.max(0.0, (box.width() - textWidth) / 2.0), box.y(),
				range.font(), fitted, color, safe);
	}

	/**
	 * Draws a single line rotated around its center point.
	 *
	 * @param canvas target drawing canvas
	 * @param text source text
	 * @param line rotated line geometry
	 * @param range font range
	 * @param color text color
	 */
	private static void drawRotatedCenteredLine(Canvas canvas, String text, RotatedLine line, FontRange range,
			Color color) {
		String safe = normalizeText(text);
		if (safe.isBlank()) {
			return;
		}
		double fontSize = fitSingleLineFontSize(safe, range, line.availableLength());
		double textWidth = range.font().textWidth(safe, fontSize);
		double x = line.centerX() - (textWidth / 2.0);
		double y = line.centerY() - (fontSize / 2.0);
		canvas.drawTextRotated(x, y, -90.0, line.centerX(), line.centerY(), range.font(), fontSize, color, safe);
	}

	/**
	 * Fits wrapped text into a rectangular box.
	 *
	 * @param canvas target drawing canvas
	 * @param text source text
	 * @param box text box
	 * @param range font range
	 * @return fitted font size
	 */
	private static double fitWrappedFontSize(Canvas canvas, String text, TextBox box, FontRange range) {
		for (double size = range.maxSize(); size >= range.minSize(); size -= 0.5) {
			List<String> lines = canvas.wrapLines(text, range.font(), size, box.width());
			double totalHeight = lines.size() * size * 1.16;
			if (totalHeight <= box.height() && maxLineWidth(lines, range.font(), size) <= box.width()) {
				return size;
			}
		}
		return range.minSize();
	}

	/**
	 * Fits one line into an available width.
	 *
	 * @param text source text
	 * @param range font range
	 * @param width available width
	 * @return fitted font size
	 */
	private static double fitSingleLineFontSize(String text, FontRange range, double width) {
		for (double size = range.maxSize(); size >= range.minSize(); size -= 0.5) {
			if (range.font().textWidth(text, size) <= width) {
				return size;
			}
		}
		return range.minSize();
	}

	/**
	 * Measures the widest line in a wrapped line list.
	 *
	 * @param lines wrapped lines
	 * @param font measurement font
	 * @param fontSize measurement font size
	 * @return widest line width
	 */
	private static double maxLineWidth(List<String> lines, Font font, double fontSize) {
		double max = 0.0;
		for (String line : lines) {
			max = Math.max(max, font.textWidth(line, fontSize));
		}
		return max;
	}

	/**
	 * Builds the front-cover title text, including language-specific heart support.
	 *
	 * @param book source book metadata
	 * @return display title
	 */
	private static String titleText(Book book) {
		String title = normalizeText(book.getTitle());
		if (title.isBlank()) {
			return "Untitled";
		}
		String keyword = loveKeyword(book.getLanguage());
		if (keyword == null) {
			return title;
		}
		return replaceFirstIgnoreCase(title, keyword, "\u2665");
	}

	/**
	 * Returns the language-specific love keyword used by cover title rendering.
	 *
	 * @param language book language
	 * @return keyword, or null when no keyword is defined
	 */
	private static String loveKeyword(Language language) {
		if (language == null) {
			return "Love";
		}
		return switch (language) {
			case English -> "Love";
			case German -> "liebe";
			case French -> "aime";
			case Spanish -> "Amo";
			case Chinese -> "\u7231";
			default -> null;
		};
	}

	/**
	 * Replaces the first case-insensitive keyword occurrence.
	 *
	 * @param text source text
	 * @param needle keyword to replace
	 * @param replacement replacement text
	 * @return updated text
	 */
	private static String replaceFirstIgnoreCase(String text, String needle, String replacement) {
		String lowerText = text.toLowerCase(Locale.ROOT);
		String lowerNeedle = needle.toLowerCase(Locale.ROOT);
		int index = lowerText.indexOf(lowerNeedle);
		if (index < 0) {
			return text;
		}
		String before = text.substring(0, index).stripTrailing();
		String after = text.substring(index + needle.length()).stripLeading();
		StringBuilder builder = new StringBuilder(text.length() + replacement.length() + 2);
		if (!before.isBlank()) {
			builder.append(before).append(' ');
		}
		builder.append(replacement);
		if (!after.isBlank()) {
			builder.append(' ').append(after);
		}
		return builder.toString();
	}

	/**
	 * Builds the back-cover blurb text.
	 *
	 * @param book source book metadata
	 * @return blurb text, or blank when no suitable text exists
	 */
	private static String coverBlurb(Book book) {
		String blurb = joinParagraphs(book.getBlurb(), Integer.MAX_VALUE);
		if (!blurb.isBlank()) {
			return blurb;
		}
		return joinParagraphs(book.getIntroduction(), 2);
	}

	/**
	 * Joins text paragraphs for cover rendering.
	 *
	 * @param values source paragraphs
	 * @param limit maximum number of non-blank paragraphs
	 * @return joined text
	 */
	private static String joinParagraphs(String[] values, int limit) {
		if (values == null || values.length == 0 || limit <= 0) {
			return "";
		}
		List<String> paragraphs = new ArrayList<>();
		for (String value : values) {
			String normalized = normalizeText(value);
			if (!normalized.isBlank()) {
				paragraphs.add(normalized);
			}
			if (paragraphs.size() >= limit) {
				break;
			}
		}
		return String.join("\n\n", paragraphs);
	}

	/**
	 * Normalizes cover text whitespace.
	 *
	 * @param value source value
	 * @return normalized text
	 */
	private static String normalizeText(String value) {
		if (value == null) {
			return "";
		}
		return value.replace('\r', '\n').trim();
	}

	/**
	 * Resolves the printed page count used for spine calculation.
	 *
	 * @param book source book metadata
	 * @param options cover rendering options
	 * @return positive page count
	 */
	private static int resolvePages(Book book, Options options, DocumentMetrics interiorPdf) {
		if (options.getPages() > 0) {
			return options.getPages();
		}
		if (interiorPdf != null) {
			return interiorPdf.pageCount();
		}
		if (book.getPages() > 0) {
			return book.getPages();
		}
		return estimatePages(book);
	}

	/**
	 * Resolves the trim width used by the cover sheet.
	 *
	 * @param book source book metadata
	 * @param interiorPdf optional interior-PDF metrics
	 * @return positive trim width in centimeters
	 */
	private static double resolveTrimWidth(Book book, DocumentMetrics interiorPdf) {
		if (interiorPdf != null) {
			return trimDimension(interiorPdf.pageWidthCm());
		}
		return trimDimension(book.getPaperWidthCm());
	}

	/**
	 * Resolves the trim height used by the cover sheet.
	 *
	 * @param book source book metadata
	 * @param interiorPdf optional interior-PDF metrics
	 * @return positive trim height in centimeters
	 */
	private static double resolveTrimHeight(Book book, DocumentMetrics interiorPdf) {
		if (interiorPdf != null) {
			return trimDimension(interiorPdf.pageHeightCm());
		}
		return trimDimension(book.getPaperHeightCm());
	}

	/**
	 * Estimates a page count when metadata does not contain one.
	 *
	 * @param book source book metadata
	 * @return estimated printed page count
	 */
	private static int estimatePages(Book book) {
		int perPuzzlePage = Math.max(1, book.getPuzzleRows() * book.getPuzzleColumns());
		int puzzlePages = (book.getElements().length + perPuzzlePage - 1) / perPuzzlePage;
		int solutionTables = Math.max(1, (puzzlePages + Math.max(1, book.getTableFrequency()) - 1)
				/ Math.max(1, book.getTableFrequency()));
		int estimated = 8 + (2 * puzzlePages) + solutionTables;
		return Math.max(24, estimated + (estimated & 1));
	}

	/**
	 * Returns the physical x-coordinate of the spine.
	 *
	 * @param dimensions cover dimensions
	 * @return physical spine x-coordinate in centimeters
	 */
	private static double physicalSpineX(Dimensions dimensions) {
		return switch (dimensions.binding()) {
			case PAPERBACK -> PAPERBACK_BLEED_CM + dimensions.trimWidthCm();
			case HARDCOVER -> HARD_COVER_WRAP_CM + dimensions.trimWidthCm() + HARD_COVER_HINGE_CM;
			case EBOOK -> 0.0;
		};
	}

	/**
	 * Returns the barcode x-coordinate.
	 *
	 * @param dimensions cover dimensions
	 * @return barcode x-coordinate in centimeters
	 */
	private static double barcodeX(Dimensions dimensions) {
		if (dimensions.binding() == Binding.HARDCOVER) {
			return HARD_COVER_WRAP_CM + dimensions.trimWidthCm() - BARCODE_MARGIN_CM - BARCODE_WIDTH_CM;
		}
		return PAPERBACK_BLEED_CM + dimensions.trimWidthCm() - BARCODE_MARGIN_CM - BARCODE_WIDTH_CM;
	}

	/**
	 * Returns the barcode y-coordinate.
	 *
	 * @param dimensions cover dimensions
	 * @return barcode y-coordinate in centimeters
	 */
	private static double barcodeY(Dimensions dimensions) {
		if (dimensions.binding() == Binding.HARDCOVER) {
			return HARD_COVER_WRAP_CM + dimensions.trimHeightCm() - HARD_COVER_BARCODE_BOTTOM_MARGIN_CM
					- BARCODE_HEIGHT_CM;
		}
		return PAPERBACK_BLEED_CM + dimensions.trimHeightCm() - BARCODE_MARGIN_CM - BARCODE_HEIGHT_CM;
	}

	/**
	 * Returns a usable trim dimension.
	 *
	 * @param value source trim dimension
	 * @return finite positive trim dimension, or the minimum supported value
	 */
	private static double trimDimension(double value) {
		return Double.isFinite(value) && value > 0.0 ? Math.max(1.0, value) : 1.0;
	}

	/**
	 * Converts centimeters to PDF points.
	 *
	 * @param centimeters source value
	 * @return converted points
	 */
	private static double points(double centimeters) {
		return Book.cmToPoints(centimeters);
	}

	/**
	 * Converts inches to centimeters.
	 *
	 * @param inches source value
	 * @return centimeters
	 */
	private static double inchesToCm(double inches) {
		return inches * CENTIMETERS_PER_INCH;
	}
}
