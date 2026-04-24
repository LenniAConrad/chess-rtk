package testing;

import static testing.TestSupport.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import application.cli.command.ChessBookCoverCommand;
import chess.book.model.Book;
import chess.book.cover.Binding;
import chess.book.cover.Dimensions;
import chess.book.cover.Interior;
import chess.book.cover.Options;
import chess.book.cover.Writer;
import chess.pdf.DocumentMetrics;
import chess.pdf.Inspector;
import chess.pdf.document.Document;
import chess.pdf.document.PageSize;
import utility.Argv;

/**
 * Zero-dependency regression checks for the {@code book cover} command.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class ChessBookCoverCommandRegressionTest {

	/**
	 * Shared JSON suffix for sample manifests.
	 */
	private static final String JSON_SUFFIX = ".json";

	/**
	 * Shared CLI input option.
	 */
	private static final String INPUT_OPTION = "--input";

	/**
	 * Shared CLI output option.
	 */
	private static final String OUTPUT_OPTION = "--output";

	/**
	 * Shared CLI binding option.
	 */
	private static final String BINDING_OPTION = "--binding";

	/**
	 * Shared paperback binding token.
	 */
	private static final String PAPERBACK_BINDING = "paperback";

	/**
	 * Shared CLI interior option.
	 */
	private static final String INTERIOR_OPTION = "--interior";

	/**
	 * Shared cream black-and-white interior token.
	 */
	private static final String CREAM_BW_INTERIOR = "cream-bw";

	/**
	 * Number of centimeters in one inch.
	 */
	private static final double CENTIMETERS_PER_INCH = 2.54;

	/**
	 * Sample cover page count.
	 */
	private static final int COVER_PAGES = 120;

	/**
	 * Sample trim width.
	 */
	private static final double TRIM_WIDTH_CM = 20.95;

	/**
	 * Sample trim height.
	 */
	private static final double TRIM_HEIGHT_CM = 27.94;

	/**
	 * Sample interior-PDF page count used to override manifest metadata.
	 */
	private static final int PDF_PAGES = 64;

	/**
	 * Sample interior-PDF trim width.
	 */
	private static final double PDF_TRIM_WIDTH_CM = 15.24;

	/**
	 * Sample interior-PDF trim height.
	 */
	private static final double PDF_TRIM_HEIGHT_CM = 22.86;

	/**
	 * White paper spine multiplier in inches per page.
	 */
	private static final double WHITE_PAPER_SPINE_INCHES = 0.002252;

	/**
	 * Cream paper spine multiplier in inches per page.
	 */
	private static final double CREAM_PAPER_SPINE_INCHES = 0.0025;

	/**
	 * Premium color spine multiplier in inches per page.
	 */
	private static final double PREMIUM_COLOR_SPINE_INCHES = 0.002347;

	/**
	 * Paperback bleed on every outside edge.
	 */
	private static final double PAPERBACK_BLEED_INCHES = 0.125;

	/**
	 * Spine text margin from each fold line.
	 */
	private static final double SPINE_MARGIN_INCHES = 0.0625;

	/**
	 * Hardcover wrap allowance.
	 */
	private static final double HARD_COVER_WRAP_CM = 1.5;

	/**
	 * Hardcover hinge allowance.
	 */
	private static final double HARD_COVER_HINGE_CM = 1.0;

	/**
	 * Hardcover safe margin from the visible book edge.
	 */
	private static final double HARD_COVER_SAFE_MARGIN_INCHES = 0.635;

	/**
	 * Tight floating-point tolerance for dimension checks.
	 */
	private static final double DIMENSION_EPSILON = 0.0000001;

	/**
	 * Tight floating-point tolerance for paper multiplier checks.
	 */
	private static final double PAPER_EPSILON = 0.0000000001;

	/**
	 * Utility class; prevent instantiation.
	 */
	private ChessBookCoverCommandRegressionTest() {
		// utility
	}

	/**
	 * Runs the regression checks.
	 *
	 * @param args unused command-line arguments
	 * @throws Exception if a check fails unexpectedly
	 */
	public static void main(String[] args) throws Exception {
		testInteriorPaperConstants();
		testSupportedInteriorSet();
		testPaperbackDimensions();
		testHardcoverDimensions();
		testInteriorPdfOverridesManifestMetadata();
		testPaperbackCoverExport();
		testCliUsesInteriorPdfMetadata();
		testCheckModeDoesNotWritePdf();
		System.out.println("ChessBookCoverCommandRegressionTest: all checks passed");
	}

	/**
	 * Verifies the published paper-thickness constants used for spine width.
	 */
	private static void testInteriorPaperConstants() {
		assertNear(cm(WHITE_PAPER_SPINE_INCHES), Interior.WHITE_PAPER_BLACK_AND_WHITE.getPaperThicknessCm(),
				PAPER_EPSILON,
				"white black-and-white thickness");
		assertNear(cm(CREAM_PAPER_SPINE_INCHES), Interior.CREAM_PAPER_BLACK_AND_WHITE.getPaperThicknessCm(),
				PAPER_EPSILON,
				"cream black-and-white thickness");
		assertNear(cm(WHITE_PAPER_SPINE_INCHES), Interior.WHITE_PAPER_STANDARD_COLOR.getPaperThicknessCm(),
				PAPER_EPSILON,
				"standard color thickness");
		assertNear(cm(PREMIUM_COLOR_SPINE_INCHES), Interior.WHITE_PAPER_PREMIUM_COLOR.getPaperThicknessCm(),
				PAPER_EPSILON,
				"premium color thickness");
	}

	/**
	 * Verifies the published cover interior set stays narrow.
	 */
	private static void testSupportedInteriorSet() {
		assertTrue(Interior.values().length == 4, "published interior option count");
	}

	/**
	 * Verifies paperback cover dimensions use exact bleed and spine constants.
	 */
	private static void testPaperbackDimensions() {
		Book book = Book.fromJson(sampleBook());
		Options options = new Options()
				.setBinding(Binding.PAPERBACK)
				.setInterior(Interior.CREAM_PAPER_BLACK_AND_WHITE)
				.setPages(COVER_PAGES);
		Dimensions dimensions = Writer.calculateDimensions(book, options);
		double bleed = cm(PAPERBACK_BLEED_INCHES);
		double spineMargin = cm(SPINE_MARGIN_INCHES);
		double spineWidth = cm(COVER_PAGES * CREAM_PAPER_SPINE_INCHES);

		assertNear(TRIM_WIDTH_CM, dimensions.trimWidthCm(), DIMENSION_EPSILON, "paperback trim width");
		assertNear(TRIM_HEIGHT_CM, dimensions.trimHeightCm(), DIMENSION_EPSILON, "paperback trim height");
		assertNear(spineWidth, dimensions.spineWidthCm(), DIMENSION_EPSILON, "paperback spine width");
		assertNear(both(TRIM_WIDTH_CM) + spineWidth + both(bleed), dimensions.fullWidthCm(), DIMENSION_EPSILON,
				"paperback full width");
		assertNear(TRIM_HEIGHT_CM + both(bleed), dimensions.fullHeightCm(), DIMENSION_EPSILON,
				"paperback full height");
		assertArea(dimensions.back(), both(bleed), both(bleed), TRIM_WIDTH_CM - both(bleed),
				TRIM_HEIGHT_CM - both(bleed), "paperback back safe area");
		assertArea(dimensions.spine(), bleed + TRIM_WIDTH_CM + spineMargin, both(bleed),
				spineWidth - both(spineMargin), TRIM_HEIGHT_CM - both(bleed), "paperback spine safe area");
		assertArea(dimensions.front(), bleed + TRIM_WIDTH_CM + spineWidth + bleed, both(bleed),
				TRIM_WIDTH_CM - both(bleed), TRIM_HEIGHT_CM - both(bleed), "paperback front safe area");
		assertNear(Book.cmToPoints(dimensions.fullWidthCm()), dimensions.toPageSize().getWidth(), DIMENSION_EPSILON,
				"paperback page width points");
		assertNear(Book.cmToPoints(dimensions.fullHeightCm()), dimensions.toPageSize().getHeight(),
				DIMENSION_EPSILON, "paperback page height points");
	}

	/**
	 * Verifies hardcover cover dimensions use wrap and hinge allowances.
	 */
	private static void testHardcoverDimensions() {
		Book book = Book.fromJson(sampleBook());
		Options options = new Options()
				.setBinding(Binding.HARDCOVER)
				.setInterior(Interior.CREAM_PAPER_BLACK_AND_WHITE)
				.setPages(COVER_PAGES);
		Dimensions dimensions = Writer.calculateDimensions(book, options);
		double safeMargin = cm(HARD_COVER_SAFE_MARGIN_INCHES);
		double spineMargin = cm(SPINE_MARGIN_INCHES);
		double spineWidth = cm(COVER_PAGES * CREAM_PAPER_SPINE_INCHES);

		assertNear(both(TRIM_WIDTH_CM + HARD_COVER_HINGE_CM + HARD_COVER_WRAP_CM) + spineWidth,
				dimensions.fullWidthCm(), DIMENSION_EPSILON, "hardcover full width");
		assertNear(TRIM_HEIGHT_CM + both(HARD_COVER_WRAP_CM), dimensions.fullHeightCm(), DIMENSION_EPSILON,
				"hardcover full height");
		assertArea(dimensions.back(), HARD_COVER_WRAP_CM + safeMargin, HARD_COVER_WRAP_CM + safeMargin,
				TRIM_WIDTH_CM - both(safeMargin), TRIM_HEIGHT_CM - both(safeMargin),
				"hardcover back safe area");
		assertArea(dimensions.spine(), HARD_COVER_WRAP_CM + TRIM_WIDTH_CM + HARD_COVER_HINGE_CM + spineMargin,
				HARD_COVER_WRAP_CM + safeMargin, spineWidth - both(spineMargin),
				TRIM_HEIGHT_CM - both(safeMargin), "hardcover spine safe area");
		assertArea(dimensions.front(),
				HARD_COVER_WRAP_CM + TRIM_WIDTH_CM + HARD_COVER_HINGE_CM + spineWidth + HARD_COVER_HINGE_CM
						+ safeMargin,
				HARD_COVER_WRAP_CM + safeMargin, TRIM_WIDTH_CM - both(safeMargin),
				TRIM_HEIGHT_CM - both(safeMargin),
				"hardcover front safe area");
	}

	/**
	 * Verifies that interior-PDF metadata overrides manifest trim and page data.
	 *
	 * @throws Exception if PDF inspection fails
	 */
	private static void testInteriorPdfOverridesManifestMetadata() throws Exception {
		Book book = Book.fromJson(sampleBook());
		Path pdf = writeInteriorPdf(PDF_TRIM_WIDTH_CM, PDF_TRIM_HEIGHT_CM, PDF_PAGES);
		DocumentMetrics metrics = Inspector.inspect(pdf);
		Options options = new Options()
				.setBinding(Binding.PAPERBACK)
				.setInterior(Interior.WHITE_PAPER_BLACK_AND_WHITE)
				.setInteriorPdfMetrics(metrics);
		Dimensions dimensions = Writer.calculateDimensions(book, options);
		double bleed = cm(PAPERBACK_BLEED_INCHES);
		double spineWidth = PDF_PAGES * Interior.WHITE_PAPER_BLACK_AND_WHITE.getPaperThicknessCm();

		assertEquals(PDF_PAGES, dimensions.pages(), "pdf-derived page count");
		assertNear(PDF_TRIM_WIDTH_CM, dimensions.trimWidthCm(), DIMENSION_EPSILON, "pdf-derived trim width");
		assertNear(PDF_TRIM_HEIGHT_CM, dimensions.trimHeightCm(), DIMENSION_EPSILON, "pdf-derived trim height");
		assertNear(spineWidth, dimensions.spineWidthCm(), DIMENSION_EPSILON, "pdf-derived spine width");
		assertNear(both(PDF_TRIM_WIDTH_CM) + spineWidth + both(bleed), dimensions.fullWidthCm(), DIMENSION_EPSILON,
				"pdf-derived full width");
		assertNear(PDF_TRIM_HEIGHT_CM + both(bleed), dimensions.fullHeightCm(), DIMENSION_EPSILON,
				"pdf-derived full height");
	}

	/**
	 * Verifies that the CLI can render a paperback cover PDF.
	 *
	 * @throws Exception if cover export fails
	 */
	private static void testPaperbackCoverExport() throws Exception {
		Path input = Files.createTempFile("book-cover-", JSON_SUFFIX);
		Files.writeString(input, sampleBook(), StandardCharsets.UTF_8);

		Path output = Files.createTempFile("book-cover-", ".pdf");
		String console = captureStdout(() -> ChessBookCoverCommand.runChessBookCover(new Argv(new String[] {
				INPUT_OPTION, input.toString(),
				OUTPUT_OPTION, output.toString(),
				BINDING_OPTION, PAPERBACK_BINDING,
				INTERIOR_OPTION, CREAM_BW_INTERIOR,
				"--pages", String.valueOf(COVER_PAGES)
		})));

		byte[] bytes = Files.readAllBytes(output);
		String header = new String(bytes, 0, Math.min(bytes.length, 32), StandardCharsets.ISO_8859_1);
		String text = new String(bytes, StandardCharsets.ISO_8859_1);
		assertTrue(header.startsWith("%PDF-1.4"), "cover pdf header");
		assertTrue(text.contains("%%EOF"), "cover eof marker");
		assertTrue(text.contains("/Title (Cover CLI: Cover Sample cover)"), "cover metadata title");
		assertTrue(text.contains("/Producer (chess-rtk native book cover pdf)"), "cover producer metadata");
		assertTrue(text.contains("About this book"), "back-cover heading");
		assertTrue(text.contains("Cover CLI"), "front-cover title");
		assertTrue(console.contains(PAPERBACK_BINDING + " cover for " + COVER_PAGES + " pages"), "console summary");
		assertFalse(text.contains("/Subtype /Image"), "cover raster image marker");
	}

	/**
	 * Verifies that the CLI can derive cover trim and pages from an interior PDF.
	 *
	 * @throws Exception if PDF inspection or validation fails
	 */
	private static void testCliUsesInteriorPdfMetadata() throws Exception {
		Path input = Files.createTempFile("book-cover-pdf-", JSON_SUFFIX);
		Files.writeString(input, sampleBook(), StandardCharsets.UTF_8);
		Path pdf = writeInteriorPdf(PDF_TRIM_WIDTH_CM, PDF_TRIM_HEIGHT_CM, PDF_PAGES);

		String console = captureStdout(() -> ChessBookCoverCommand.runChessBookCover(new Argv(new String[] {
				INPUT_OPTION, input.toString(),
				"--pdf", pdf.toString(),
				BINDING_OPTION, PAPERBACK_BINDING,
				INTERIOR_OPTION, CREAM_BW_INTERIOR,
				"--check"
		})));

		assertTrue(console.contains("book cover OK: " + PAPERBACK_BINDING + "/" + CREAM_BW_INTERIOR + ", "
				+ PDF_PAGES + " pages"),
				"cover check mode pdf page count");
		assertTrue(console.contains(String.format(Locale.ROOT, "trim %.2f x %.2f cm",
				PDF_TRIM_WIDTH_CM, PDF_TRIM_HEIGHT_CM)),
				"cover check mode pdf trim");
	}

	/**
	 * Verifies validation mode checks cover dimensions without writing a PDF.
	 *
	 * @throws Exception if validation fails unexpectedly
	 */
	private static void testCheckModeDoesNotWritePdf() throws Exception {
		Path input = Files.createTempFile("book-cover-check-", JSON_SUFFIX);
		Files.writeString(input, sampleBook(), StandardCharsets.UTF_8);

		Path output = Files.createTempDirectory("book-cover-check-").resolve("cover.pdf");
		String console = captureStdout(() -> ChessBookCoverCommand.runChessBookCover(new Argv(new String[] {
				INPUT_OPTION, input.toString(),
				OUTPUT_OPTION, output.toString(),
				BINDING_OPTION, PAPERBACK_BINDING,
				INTERIOR_OPTION, CREAM_BW_INTERIOR,
				"--pages", String.valueOf(COVER_PAGES),
				"--check"
		})));

		assertTrue(console.contains("book cover OK: " + PAPERBACK_BINDING + "/" + CREAM_BW_INTERIOR + ", 120 pages"),
				"cover check mode summary");
		assertFalse(Files.exists(output), "cover check mode skipped pdf output");
	}

	/**
	 * Writes a plain interior PDF with a uniform custom page size.
	 *
	 * @param widthCm page width in centimeters
	 * @param heightCm page height in centimeters
	 * @param pages page count
	 * @return written PDF path
	 * @throws Exception if the PDF cannot be written
	 */
	private static Path writeInteriorPdf(double widthCm, double heightCm, int pages) throws Exception {
		Document document = new Document().setTitle("Interior");
		PageSize size = new PageSize("Interior", Book.cmToPoints(widthCm), Book.cmToPoints(heightCm));
		for (int i = 0; i < pages; i++) {
			document.addPage(size);
		}
		Path output = Files.createTempFile("book-cover-interior-", ".pdf");
		document.write(output);
		return output;
	}

	/**
	 * Builds a minimal book JSON document.
	 *
	 * @return JSON book source text
	 */
	private static String sampleBook() {
		return """
				{
				  "title": "Cover CLI",
				  "subtitle": "Cover Sample",
				  "author": "Codex",
				  "pages": %d,
				  "paperwidth": %.2f,
				  "paperheight": %.2f,
				  "blurb": [
				    "A compact regression book that exercises the native cover renderer.",
				    "The back cover uses wrapped vector text and a barcode-safe box."
				  ],
				  "elements": [
				    {
				      "position": "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
				      "moves": "1. e4"
				    }
				  ]
				}
				""".formatted(COVER_PAGES, TRIM_WIDTH_CM, TRIM_HEIGHT_CM);
	}

	/**
	 * Fails when a cover area differs from the expected coordinates.
	 *
	 * @param area   actual area
	 * @param x      expected x-coordinate
	 * @param y      expected y-coordinate
	 * @param width  expected width
	 * @param height expected height
	 * @param label  failure label
	 */
	private static void assertArea(Dimensions.Area area, double x, double y, double width, double height,
			String label) {
		assertNear(x, area.xCm(), DIMENSION_EPSILON, label + " x");
		assertNear(y, area.yCm(), DIMENSION_EPSILON, label + " y");
		assertNear(width, area.widthCm(), DIMENSION_EPSILON, label + " width");
		assertNear(height, area.heightCm(), DIMENSION_EPSILON, label + " height");
	}

	/**
	 * Returns a value counted on both cover sides.
	 *
	 * @param value one-side value
	 * @return doubled value
	 */
	private static double both(double value) {
		return 2.0 * value;
	}

	/**
	 * Converts inches to centimeters for expected print constants.
	 *
	 * @param inches source value
	 * @return centimeters
	 */
	private static double cm(double inches) {
		return inches * CENTIMETERS_PER_INCH;
	}
}
