package testing;

import static testing.TestSupport.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import chess.book.model.Book;
import chess.book.model.Element;
import chess.book.render.MoveText;
import chess.book.render.Writer;
import chess.pdf.document.PageSize;

/**
 * Zero-dependency regression checks for native chess-book PDF generation.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class BookRegressionTest {

	/**
	 * Utility class; prevent instantiation.
	 */
	private BookRegressionTest() {
		// utility
	}

	/**
	 * Runs the regression checks.
	 *
	 * @param args unused command-line arguments
	 * @throws Exception if any check fails unexpectedly
	 */
	public static void main(String[] args) throws Exception {
		testFigurineMoveTextFormatter();
		testDefaultILoveChessGeometry();
		testPrettyPrintedJsonBookParsing();
		testBookExport();
		testLongSolutionTableRows();
		testFenWhitespace();
		testDefaultHowToReadAndMoveLabels();
		testSolutionLineParsingUsesCoreSanHelpers();
		testFreeEditionWatermark();
		testTocEntriesAreLinks();
		testUserSuppliedListParagraphs();
		testTypographicPunctuationUsesBaseFontText();
		testMultilingualVectorText();
		System.out.println("BookRegressionTest: all checks passed");
	}

	/**
	 * Verifies that SAN-like movetext is converted to figurine algebraic notation
	 * without changing ordinary text.
	 */
	private static void testFigurineMoveTextFormatter() {
		String formatted = MoveText.figurine("1. Qxe8+ Nbd6 2. bxa8=Q# LONGTABLEMARKER");

		assertTrue(formatted.contains("\u2655\u00D7e8+"), "queen capture figurine");
		assertTrue(formatted.contains("\u2658bd6"), "knight disambiguation figurine");
		assertTrue(formatted.contains("b\u00D7a8=\u2655#"), "promotion figurine");
		assertTrue(formatted.contains("LONGTABLEMARKER"), "ordinary text preserved");
	}

	/**
	 * Verifies the default book geometry matches the original I Love Chess LaTeX
	 * geometry package settings.
	 */
	private static void testDefaultILoveChessGeometry() {
		Book book = new Book();
		PageSize size = book.toPageSize();

		assertNear(20.95, book.getPaperWidthCm(), 0.0001, "paper width cm");
		assertNear(27.94, book.getPaperHeightCm(), 0.0001, "paper height cm");
		assertNear(2.0, book.getInnerMarginCm(), 0.0001, "inner margin cm");
		assertNear(1.5, book.getOuterMarginCm(), 0.0001, "outer margin cm");
		assertNear(2.0, book.getTopMarginCm(), 0.0001, "top margin cm");
		assertNear(2.0, book.getBottomMarginCm(), 0.0001, "bottom margin cm");
		assertNear(593.858, size.getWidth(), 0.001, "paper width points");
		assertNear(792.0, size.getHeight(), 0.001, "paper height points");
	}

	/**
	 * Verifies that pretty-printed JSON with whitespace around colons is
	 * accepted by the book loader.
	 */
	private static void testPrettyPrintedJsonBookParsing() {
		Book book = Book.fromJson("""
				{
				  "title" : "Whitespace Book",
				  "subtitle" : "Parser Sample",
				  "author" : "Codex",
				  "tablefrequency" : 0,
				  "puzzlerows" : 0,
				  "puzzlecolumns" : 0,
				  "paperwidth" : -1,
				  "introduction" : [ "Pretty JSON should parse." ],
				  "elements" : [
				    {
				      "position" : "8/8/8/8/8/8/8/K6k w - - 0 1",
				      "moves" : "1. Ka2"
				    }
				  ]
				}
				""");

		assertTrue("Whitespace Book".equals(book.getTitle()), "pretty JSON title");
		assertTrue("Parser Sample".equals(book.getSubtitle()), "pretty JSON subtitle");
		assertTrue(book.getTableFrequency() == 1, "pretty JSON table frequency clamp");
		assertTrue(book.getPuzzleRows() == 1, "pretty JSON puzzle rows clamp");
		assertTrue(book.getPuzzleColumns() == 1, "pretty JSON puzzle columns clamp");
		assertNear(20.95, book.getPaperWidthCm(), 0.0001, "pretty JSON paper width fallback");
		assertTrue(book.getIntroduction().length == 1, "pretty JSON string array");
		assertTrue(book.getElements().length == 1, "pretty JSON elements");
		assertTrue("1. Ka2".equals(book.getElements()[0].getMoves()), "pretty JSON element moves");
	}

	/**
	 * Verifies that the native book renderer creates a vector PDF with the expected
	 * structural text.
	 *
	 * @throws Exception if export fails
	 */
	private static void testBookExport() throws Exception {
		Path file = Files.createTempFile("chess-book-", ".pdf");
		Writer.write(file, sampleBook(48));

		byte[] bytes = Files.readAllBytes(file);
		String header = new String(bytes, 0, Math.min(bytes.length, 32), StandardCharsets.ISO_8859_1);
		String text = new String(bytes, StandardCharsets.ISO_8859_1);
		assertTrue(header.startsWith("%PDF-1.4"), "pdf header");
		assertTrue(bytes.length > 30_000, "pdf size");
		assertTrue(text.contains("/Producer (chess-rtk native book pdf)"), "producer metadata");
		assertTrue(text.contains("Contents"), "toc text");
		assertTrue(text.contains("Puzzles"), "puzzle heading");
		assertTrue(text.contains("Full solutions at page"), "solution footnote");
		assertTrue(text.contains("/Subtype /Link"), "solution footnote link annotation");
		assertTrue(text.contains("/S /GoTo"), "solution footnote internal link action");
		assertFalse(text.contains("/Subtype /Image"), "raster image marker");
	}

	/**
	 * Verifies that unusually long solution strings are paginated through the table
	 * renderer instead of aborting PDF generation.
	 *
	 * @throws Exception if export fails
	 */
	private static void testLongSolutionTableRows() throws Exception {
		Path file = Files.createTempFile("chess-book-long-table-", ".pdf");
		Writer.write(file, longTableBook());

		byte[] bytes = Files.readAllBytes(file);
		String text = new String(bytes, StandardCharsets.ISO_8859_1);
		assertTrue(bytes.length > 20_000, "long-table pdf size");
		assertTrue(text.contains("LONGTABLEMARKER"), "long-table text marker");
		assertFalse(text.contains("/Subtype /Image"), "long-table raster image marker");
	}

	/**
	 * Verifies that strict FENs with repeated whitespace still render.
	 *
	 * @throws Exception if export fails
	 */
	private static void testFenWhitespace() throws Exception {
		Path file = Files.createTempFile("chess-book-fen-whitespace-", ".pdf");
		Book book = sampleBook(1);
		Element[] elements = book.getElements();
		elements[0].setPosition("1k4nr/ppp3pp/8/8/1b6/3r1P2/PP1B1P1P/R2KR3   w  -  -  0  1");
		elements[0].setMoves("1. Re8+ Rd8 2. Rxd8#");
		book.setElements(elements);
		Writer.write(file, book);

		byte[] bytes = Files.readAllBytes(file);
		String text = new String(bytes, StandardCharsets.ISO_8859_1);
		assertTrue(bytes.length > 10_000, "whitespace pdf size");
		assertTrue(text.contains("/Producer (chess-rtk native book pdf)"), "whitespace metadata");
	}

	/**
	 * Verifies parity content from the generated LaTeX how-to-read section and
	 * move-numbered solution labels.
	 *
	 * @throws Exception if export fails
	 */
	private static void testDefaultHowToReadAndMoveLabels() throws Exception {
		Path file = Files.createTempFile("chess-book-default-howto-", ".pdf");
		Book book = sampleBook(8).setHowToRead(new String[0]);
		Writer.write(file, book);

		byte[] bytes = Files.readAllBytes(file);
		String text = new String(bytes, StandardCharsets.ISO_8859_1);
		assertTrue(text.contains("Special Illustrations for Castling and En Passant"), "special-move how-to text");
		assertTrue(text.contains("White is always at the bottom"), "board orientation text");
		assertTrue(text.contains("a1 square"), "a1 orientation text");
		assertTrue(text.contains("lower-left square"), "lower-left orientation text");
		assertTrue(text.contains("Puzzle Page"), "puzzle-page example caption");
		assertTrue(text.contains("Solution Page"), "solution-page example caption");
		assertTrue(text.contains("/BaseFont /LMRoman10-Italic"), "italic caption font");
		assertTrue(text.contains("1 \\(1. e4\\)") || text.contains("1 (1. e4)"), "move-numbered solution label");
		assertTrue(text.contains("Solve the Puzzles:"), "list item label");
		assertTrue(text.contains("Reference the Solutions:"), "second list item label");
		assertTrue(text.contains("Full solutions at page"), "footnote wording");
		assertTrue(text.contains("/Subtype /Link"), "footnote hyperlink");
		assertFalse(text.contains("/Subtype /Image"), "default how-to raster image marker");
	}

	/**
	 * Verifies that book solution parsing uses core SAN line handling, including
	 * common zero-based castling notation.
	 *
	 * @throws Exception if export fails
	 */
	private static void testSolutionLineParsingUsesCoreSanHelpers() throws Exception {
		Path file = Files.createTempFile("chess-book-core-san-", ".pdf");
		Book book = sampleBook(1)
				.setTitle("Core SAN Book")
				.setSubtitle("Castling Sample")
				.setPuzzleRows(1)
				.setPuzzleColumns(1);
		Element[] elements = book.getElements();
		elements[0].setPosition("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
		elements[0].setMoves("1. 0-0");
		book.setElements(elements);

		Writer.write(file, book);

		String text = Files.readString(file, StandardCharsets.ISO_8859_1);
		assertTrue(text.contains("1 \\(1. O-O\\)") || text.contains("1 (1. O-O)"),
				"core SAN castling label");
		assertFalse(text.contains("/Subtype /Image"), "core SAN raster image marker");
	}

	/**
	 * Verifies that the free-edition renderer adds noisy vector watermark resources
	 * and explicit restriction metadata without rasterizing the book.
	 *
	 * @throws Exception if export fails
	 */
	private static void testFreeEditionWatermark() throws Exception {
		Path file = Files.createTempFile("chess-book-free-watermark-", ".pdf");
		Writer.write(file, sampleBook(8), true, "ARC-REGRESSION-42");

		byte[] bytes = Files.readAllBytes(file);
		String text = new String(bytes, StandardCharsets.ISO_8859_1);
		assertTrue(bytes.length > 60_000, "free watermark pdf size");
		assertTrue(text.contains("Free electronic copy; printing, resale, and unauthorized redistribution not allowed"),
				"free watermark metadata");
		assertTrue(text.contains("Watermark ID ARC-REGRESSION-42"), "free watermark id metadata");
		assertTrue(text.contains("/ExtGState"), "free watermark opacity resources");
		assertFalse(text.contains("/Subtype /Image"), "free watermark raster image marker");
	}

	/**
	 * Verifies that table-of-contents rows are internal PDF links in addition to
	 * the solution-page footnote links.
	 *
	 * @throws Exception if export fails
	 */
	private static void testTocEntriesAreLinks() throws Exception {
		Path file = Files.createTempFile("chess-book-toc-links-", ".pdf");
		Writer.write(file, sampleBook(1));

		String text = Files.readString(file, StandardCharsets.ISO_8859_1);
		assertTrue(countOccurrences(text, "/Subtype /Link") > 1, "toc link annotations");
		assertTrue(text.contains("/S /GoTo"), "toc internal link action");
	}

	/**
	 * Verifies that user-supplied bullet and numbered list syntax renders without
	 * falling back to raster output.
	 *
	 * @throws Exception if export fails
	 */
	private static void testUserSuppliedListParagraphs() throws Exception {
		Path file = Files.createTempFile("chess-book-lists-", ".pdf");
		Book book = sampleBook(8)
				.setTitle("List Book")
				.setSubtitle("Flow Sample")
				.setIntroduction(new String[] {
						"- First Item: This list item is intentionally long enough to require wrapping in the native renderer.",
						"1. Numbered Item: This item checks numbered list parsing and hanging indentation." });
		Writer.write(file, book);

		byte[] bytes = Files.readAllBytes(file);
		String text = new String(bytes, StandardCharsets.ISO_8859_1);
		assertTrue(bytes.length > 18_000, "list pdf size");
		assertTrue(text.contains("First Item:"), "bullet list label");
		assertTrue(text.contains("Numbered Item:"), "numbered list label");
		assertFalse(text.contains("/Subtype /Image"), "list raster image marker");
	}

	/**
	 * Verifies that common English typographic punctuation does not force a
	 * different vector text path for otherwise ASCII prose.
	 *
	 * @throws Exception if export fails
	 */
	private static void testTypographicPunctuationUsesBaseFontText() throws Exception {
		Path file = Files.createTempFile("chess-book-punctuation-", ".pdf");
		Book book = sampleBook(8)
				.setTitle("Punctuation Book")
				.setSubtitle("Base Text")
				.setIntroduction(new String[] {
						"Think of it as a benchmark—can you beat the clock and finish faster?",
						"If you’re interested in the “Art of Chess Puzzles” series, continue." });
		Writer.write(file, book);

		byte[] bytes = Files.readAllBytes(file);
		String text = new String(bytes, StandardCharsets.ISO_8859_1);
		assertTrue(text.contains("benchmark-can you beat the clock"), "normalized em dash text");
		assertTrue(text.contains("you're interested"), "normalized apostrophe text");
		assertTrue(text.contains("\"Art of Chess Puzzles\""), "normalized quote text");
		assertFalse(text.contains("/Subtype /Image"), "punctuation raster image marker");
	}

	/**
	 * Verifies that multilingual front matter can be rendered through the native
	 * vector-text fallback without LaTeX.
	 *
	 * @throws Exception if export fails
	 */
	private static void testMultilingualVectorText() throws Exception {
		Path file = Files.createTempFile("chess-book-i18n-", ".pdf");
		Book book = sampleBook(8)
				.setTitle("I18N Book")
				.setSubtitle("Vector Text")
				.setDedication(new String[] { "李佳琪", "Échecs für alle" })
				.setIntroduction(new String[] { "如何阅读本书", "Résumé: prüfe deine Lösung." });
		Writer.write(file, book);

		byte[] bytes = Files.readAllBytes(file);
		String text = new String(bytes, StandardCharsets.ISO_8859_1);
		assertTrue(bytes.length > 18_000, "i18n pdf size");
		assertFalse(text.contains("/Subtype /Image"), "i18n raster image marker");
	}

	/**
	 * Builds a sample book large enough to exercise page flow and solution tables.
	 *
	 * @param count requested puzzle count
	 * @return populated sample book
	 */
	private static Book sampleBook(int count) {
		Book book = new Book()
				.setTitle("Native Book")
				.setSubtitle("Regression Sample")
				.setAuthor("Codex")
				.setTime("2026")
				.setLocation("Hangzhou")
				.setTableFrequency(3)
				.setPuzzleRows(2)
				.setPuzzleColumns(2)
				.setDedication(new String[] { "For the regression suite." })
				.setIntroduction(new String[] {
						"This sample book validates the native PDF renderer against the book layout model.",
						"It includes front matter, a generated table of contents, puzzle spreads, and recurring solution tables." })
				.setHowToRead(new String[] {
						"Solve the position on the left page first.",
						"Then turn the page to compare your choice with the final board and the table reference." })
				.setAfterword(new String[] { "Thank you for reading this generated sample." });

		Element[] elements = new Element[count];
		String fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
		String[] moves = { "1. e4", "1. d4", "1. Nf3", "1. c4" };
		for (int i = 0; i < elements.length; i++) {
			elements[i] = new Element(fen, moves[i % moves.length]);
		}
		book.setElements(elements);
		return book;
	}

	/**
	 * Builds a book with oversized table content to exercise table pagination.
	 *
	 * @return populated sample book
	 */
	private static Book longTableBook() {
		Book book = sampleBook(8)
				.setTitle("Long Table Book")
				.setSubtitle("Overflow Sample")
				.setTableFrequency(1)
				.setPuzzleRows(1)
				.setPuzzleColumns(2);
		Element[] elements = book.getElements();
		StringBuilder moves = new StringBuilder("LONGTABLEMARKER");
		for (int i = 0; i < 180; i++) {
			moves.append(' ').append("verylongsolutiontoken").append(i);
		}
		elements[0].setMoves(moves.toString());
		book.setElements(elements);
		return book;
	}

	/**
	 * Counts non-overlapping occurrences of a substring.
	 *
	 * @param text source text
	 * @param needle substring to count
	 * @return occurrence count
	 */
	private static int countOccurrences(String text, String needle) {
		if (text == null || needle == null || needle.isEmpty()) {
			return 0;
		}
		int count = 0;
		int index = 0;
		while ((index = text.indexOf(needle, index)) >= 0) {
			count++;
			index += needle.length();
		}
		return count;
	}
}
