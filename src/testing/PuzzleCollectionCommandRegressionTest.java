package testing;

import static testing.TestSupport.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import chess.book.model.Book;
import chess.struct.Record;
import chess.core.Position;
import chess.uci.Analysis;

/**
 * Regression checks for the {@code book collection} CLI command.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
@SuppressWarnings("java:S2187")
public final class PuzzleCollectionCommandRegressionTest {

	/**
	 * Shared input-file prefix.
	 */
	private static final String PREFIX = "book-collection-";

	/**
	 * Utility class; prevent instantiation.
	 */
	private PuzzleCollectionCommandRegressionTest() {
		// utility
	}

	/**
	 * Runs the regression checks.
	 *
	 * @param args unused command-line arguments
	 * @throws Exception if any check fails unexpectedly
	 */
	public static void main(String[] args) throws Exception {
		testBuildsManifestPdfAndCover();
		testCheckModeSkipsWrites();
		testLegacyAliasStillRuns();
		System.out.println("PuzzleCollectionCommandRegressionTest: all checks passed");
	}

	/**
	 * Verifies that the command builds a manifest, interior PDF, and matching
	 * cover from analyzed record input.
	 *
	 * @throws Exception if export fails
	 */
	private static void testBuildsManifestPdfAndCover() throws Exception {
		Path input = Files.createTempFile(PREFIX, ".json");
		Files.writeString(input, sampleRecordsJson(), StandardCharsets.UTF_8);

		Path manifest = Files.createTempFile(PREFIX, ".toml");
		Path interiorPdf = Files.createTempFile(PREFIX, ".pdf");
		Path coverPdf = Files.createTempFile(PREFIX, "-cover.pdf");
		String console = runMain(
				"book", "collection",
				"--input", input.toString(),
				"--output", manifest.toString(),
				"--pdf-output", interiorPdf.toString(),
				"--cover-output", coverPdf.toString(),
				"--subtitle", "Regression Sample",
				"--author", "Codex",
				"--time", "2026",
				"--location", "Hangzhou");

		String manifestText = Files.readString(manifest, StandardCharsets.UTF_8);
		assertTrue(console.contains("wrote manifest with 2 puzzles"), "manifest console");
		assertTrue(console.contains("without PV"), "skipped console");
		assertTrue(manifestText.contains("title = \"Chess Puzzle Collection\""), "manifest title");
		assertTrue(manifestText.contains("subtitle = \"Regression Sample\""), "manifest subtitle");

		Book book = Book.load(manifest);
		assertEquals("Codex", book.getAuthor(), "manifest author");
		assertEquals(2, book.getElements().length, "accepted puzzle count");
		assertEquals("1. e4 e5 2. Nf3", book.getElements()[0].getMoves(), "white-to-move PV");
		assertEquals("2... Qh4#", book.getElements()[1].getMoves(), "black-to-move PV");

		byte[] interiorBytes = Files.readAllBytes(interiorPdf);
		String interiorText = new String(interiorBytes, StandardCharsets.ISO_8859_1);
		assertTrue(interiorBytes.length > 16_000, "interior pdf size");
		assertTrue(interiorText.contains("/Producer (chess-rtk native book pdf)"), "interior producer");

		byte[] coverBytes = Files.readAllBytes(coverPdf);
		String coverText = new String(coverBytes, StandardCharsets.ISO_8859_1);
		assertTrue(coverBytes.length > 6_000, "cover pdf size");
		assertTrue(coverText.contains("/Producer (chess-rtk native book cover pdf)"), "cover producer");
	}

	/**
	 * Verifies validation mode checks the generated model without writing files.
	 *
	 * @throws Exception if validation fails unexpectedly
	 */
	private static void testCheckModeSkipsWrites() throws Exception {
		Path input = Files.createTempFile(PREFIX + "check-", ".json");
		Files.writeString(input, sampleRecordsJson(), StandardCharsets.UTF_8);

		Path manifest = Files.createTempDirectory(PREFIX + "check-").resolve("sample.book.toml");
		Path interiorPdf = Files.createTempDirectory(PREFIX + "check-pdf-").resolve("sample.pdf");
		Path coverPdf = Files.createTempDirectory(PREFIX + "check-cover-").resolve("sample-cover.pdf");
		String console = runMain(
				"book", "collection",
				"--input", input.toString(),
				"--output", manifest.toString(),
				"--pdf-output", interiorPdf.toString(),
				"--cover-output", coverPdf.toString(),
				"--check");

		assertTrue(console.contains("book collection OK: 2 puzzles"), "check summary");
		assertFalse(Files.exists(manifest), "check skipped manifest");
		assertFalse(Files.exists(interiorPdf), "check skipped pdf");
		assertFalse(Files.exists(coverPdf), "check skipped cover");
	}

	/**
	 * Verifies the old collection command name remains a working alias.
	 *
	 * @throws Exception if validation fails unexpectedly
	 */
	private static void testLegacyAliasStillRuns() throws Exception {
		Path input = Files.createTempFile(PREFIX + "alias-", ".json");
		Files.writeString(input, sampleRecordsJson(), StandardCharsets.UTF_8);

		String console = runMain(
				"book", "ilovechess",
				"--input", input.toString(),
				"--check");

		assertTrue(console.contains("book collection OK: 2 puzzles"), "legacy alias check summary");
	}

	/**
	 * Builds a small analyzed record array with one skipped entry.
	 *
	 * @return JSON array text
	 */
	private static String sampleRecordsJson() {
		Record whiteToMove = new Record()
				.withPosition(new Position("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"))
				.withAnalysis(new Analysis().add("info depth 1 multipv 1 score cp 20 nodes 1 pv e2e4 e7e5 g1f3"));
		Record blackToMove = new Record()
				.withPosition(new Position("rnbqkbnr/pppp1ppp/8/4p3/6P1/5P2/PPPPP2P/RNBQKBNR b KQkq g3 0 2"))
				.withAnalysis(new Analysis().add("info depth 1 multipv 1 score mate 1 nodes 1 pv d8h4"));
		Record skipped = new Record()
				.withPosition(new Position("8/8/8/8/8/8/8/K6k w - - 0 1"));
		return Record.toJsonArray(List.of(whiteToMove, blackToMove, skipped));
	}
}
