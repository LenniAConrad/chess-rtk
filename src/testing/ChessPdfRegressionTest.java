package testing;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import chess.core.Move;
import chess.core.Position;
import chess.images.render.Render;
import chess.pdf.Composition;
import chess.pdf.Options;
import chess.pdf.Writer;
import chess.pdf.document.PageSize;
import chess.struct.Game;

/**
 * Zero-dependency regression checks for direct PDF generation.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class ChessPdfRegressionTest {

	/**
	 * Matches simple horizontal PDF text runs and captures the baseline y and text.
	 */
	private static final Pattern TEXT_RUN = Pattern.compile(
			"1 0 0 1\\s+[-0-9.]+\\s+([-0-9.]+)\\s+Tm\\n\\(([^\\n]*)\\) Tj");

	/**
	 * Utility class; prevent instantiation.
	 */
	private ChessPdfRegressionTest() {
		// utility
	}

	/**
	 * Runs the direct PDF regression checks.
	 *
	 * @param args unused command-line arguments
	 * @throws Exception if any check fails unexpectedly
	 */
	public static void main(String[] args) throws Exception {
		testDefaultPositionRendererShowsSpecialMoveHints();
		testSingleCompositionExport();
		testWrappedMetadataAdvancesLayout();
		testDiagramSheetExport();
		System.out.println("ChessPdfRegressionTest: all checks passed");
	}

	/**
	 * Verifies that bare position renders include Chaqi-style special-move hints
	 * unless explicitly disabled.
	 */
	private static void testDefaultPositionRendererShowsSpecialMoveHints() {
		String fen = "r3k2r/8/8/3pP3/8/8/8/R3K2R w KQkq d6 0 1";
		String defaultSvg = new Render().setPosition(new Position(fen)).renderSvg(700, 700);
		assertTrue(countOccurrences(defaultSvg, "<polygon ") >= 5, "default special-move hint arrows");

		String disabledSvg = new Render()
				.setPosition(new Position(fen))
				.setShowSpecialMoveHints(false)
				.renderSvg(700, 700);
		assertFalse(disabledSvg.contains("<polygon "), "disabled special-move hint arrows");
	}

	/**
	 * Verifies that a single composition can be exported as a vector PDF.
	 *
	 * @throws Exception if export fails
	 */
	private static void testSingleCompositionExport() throws Exception {
		Position start = new Position(Game.STANDARD_START_FEN);
		Position afterE4 = start.copyOf().play(Move.parse("e2e4"));

		Composition composition = new Composition()
				.setId("sample-001")
				.setTime("5 min")
				.setTitle("King's Pawn Sample")
				.setDescription("A minimal composition page rendered without LaTeX or third-party PDF libraries.")
				.setComment("This layout is intentionally close to the old composition model: title, diagrams, hints, and analysis.")
				.setHintLevel1("Look for the most forcing central pawn move.")
				.setAnalysis("1. e4 claims space and opens lines for the bishop and queen.")
				.addFigure(start, "Start", "Initial position", "")
				.addFigure(afterE4, "1. e4", "King's pawn opening", "e2e4");

		Options options = new Options()
				.setPageSize(PageSize.A4)
				.setDiagramsPerRow(2)
				.setBoardPixels(800);

		Path file = Files.createTempFile("chess-pdf-composition-", ".pdf");
		Writer.writeComposition(file, composition, options);

		byte[] bytes = Files.readAllBytes(file);
		assertTrue(bytes.length > 8_000, "single composition pdf size");
		String header = new String(bytes, 0, Math.min(bytes.length, 32), StandardCharsets.ISO_8859_1);
		assertTrue(header.startsWith("%PDF-1.4"), "pdf header");
		String text = new String(bytes, StandardCharsets.ISO_8859_1);
		assertTrue(text.contains("/Type /Page"), "page marker");
		assertFalse(text.contains("/Subtype /Image"), "raster image marker");
		assertTrue(text.contains("/Type /Font"), "font marker");
		assertTrue(text.contains("/Producer (chess-rtk pdf)"), "producer metadata");
	}

	/**
	 * Verifies that multi-line metadata advances the body cursor before the first
	 * section heading is drawn.
	 *
	 * @throws Exception if export fails
	 */
	private static void testWrappedMetadataAdvancesLayout() throws Exception {
		StringBuilder id = new StringBuilder();
		for (int i = 0; i < 80; i++) {
			id.append("metadata ");
		}
		id.append("FINALMETA");

		Composition composition = new Composition()
				.setTitle("Metadata Wrap")
				.setId(id.toString())
				.addFigure(new Position(Game.STANDARD_START_FEN), "Start", "Initial position", "");

		Path file = Files.createTempFile("chess-pdf-metadata-wrap-", ".pdf");
		Writer.writeComposition(file, composition, new Options());

		String text = Files.readString(file, StandardCharsets.ISO_8859_1);
		double lastMetadataBaseline = baselineForText(text, "FINALMETA");
		double diagramsBaseline = baselineForText(text, "Diagrams");
		assertTrue(diagramsBaseline < lastMetadataBaseline - 8.0, "wrapped metadata cursor advance");
	}

	/**
	 * Verifies that a raw FEN list can be exported as a diagram sheet.
	 *
	 * @throws Exception if export fails
	 */
	private static void testDiagramSheetExport() throws Exception {
		List<String> fens = List.of(
				"8/p1p3Q1/1p4r1/5qk1/5pp1/P7/1P5R/K7 w - - 0 1",
				"r1bqkbnr/pppp1ppp/2n5/4p3/1b1PP3/2N2N2/PPP2PPP/R1BQKB1R w KQkq - 2 4",
				"2rkr3/2p1p3/4N3/8/5K2/8/8/3R4 b - - 0 1");

		Path file = Files.createTempFile("chess-pdf-sheet-", ".pdf");
		Writer.writeDiagramSheet(file, "Puzzle Sheet", fens,
				new Options().setPageSize(PageSize.A5).setDiagramsPerRow(1).setBoardPixels(700));

		byte[] bytes = Files.readAllBytes(file);
		assertTrue(bytes.length > 6_000, "diagram sheet pdf size");
		String text = new String(bytes, StandardCharsets.ISO_8859_1);
		assertTrue(text.contains("/Title (Puzzle Sheet)"), "title metadata");
		assertTrue(text.contains("/Type /Pages /Count "), "page tree count");
	}

	/**
	 * Fails when the supplied condition is false.
	 *
	 * @param condition condition to verify
	 * @param label failure label
	 */
	private static void assertTrue(boolean condition, String label) {
		if (!condition) {
			throw new AssertionError(label + ": expected true");
		}
	}

	/**
	 * Fails when the supplied condition is true.
	 *
	 * @param condition condition to verify
	 * @param label failure label
	 */
	private static void assertFalse(boolean condition, String label) {
		if (condition) {
			throw new AssertionError(label + ": expected false");
		}
	}

	/**
	 * Finds the PDF baseline for a simple text run containing a marker.
	 *
	 * @param pdf serialized PDF text
	 * @param marker marker text
	 * @return baseline y coordinate
	 */
	private static double baselineForText(String pdf, String marker) {
		Matcher matcher = TEXT_RUN.matcher(pdf);
		while (matcher.find()) {
			if (matcher.group(2).contains(marker)) {
				return Double.parseDouble(matcher.group(1));
			}
		}
		throw new AssertionError("missing text marker: " + marker);
	}

	/**
	 * Counts non-overlapping occurrences of a substring.
	 *
	 * @param text source text
	 * @param needle substring to find
	 * @return occurrence count
	 */
	private static int countOccurrences(String text, String needle) {
		int count = 0;
		int index = 0;
		while (text != null && needle != null && !needle.isEmpty()) {
			int found = text.indexOf(needle, index);
			if (found < 0) {
				return count;
			}
			count++;
			index = found + needle.length();
		}
		return count;
	}
}
