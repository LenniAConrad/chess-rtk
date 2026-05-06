package testing;

import static testing.TestSupport.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import chess.book.study.StudyBook;

/**
 * Regression checks for the {@code book study} CLI command.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
@SuppressWarnings({"java:S2187", "java:S3400"})
public final class PuzzleStudyCommandRegressionTest {

	/**
	 * Shared input-file prefix.
	 */
	private static final String PREFIX = "book-study-";

	/**
	 * Utility class; prevent instantiation.
	 */
	private PuzzleStudyCommandRegressionTest() {
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
		testLegacyAliasesStillRun();
		System.out.println("PuzzleStudyCommandRegressionTest: all checks passed");
	}

	/**
	 * Verifies that the command can normalize a rich JSON manifest and render the
	 * matching interior and cover PDFs.
	 *
	 * @throws Exception if export fails
	 */
	private static void testBuildsManifestPdfAndCover() throws Exception {
		Path input = Files.createTempFile(PREFIX, ".json");
		Files.writeString(input, sampleJson(), StandardCharsets.UTF_8);

		Path manifest = Files.createTempFile(PREFIX, ".toml");
		Path interiorPdf = Files.createTempFile(PREFIX, ".pdf");
		Path coverPdf = Files.createTempFile(PREFIX, "-cover.pdf");
		String console = runMain(
				"book", "study",
				"--input", input.toString(),
				"--manifest-output", manifest.toString(),
				"--output", interiorPdf.toString(),
				"--cover-output", coverPdf.toString(),
				"--title", "Chess Puzzle Studies",
				"--subtitle", "Regression Sample",
				"--author", "Codex",
				"--blurb", "A curated annotated puzzle collection.",
				"--link", "example.com/art");

		String manifestText = Files.readString(manifest, StandardCharsets.UTF_8);
		assertTrue(console.contains("wrote normalized manifest with 2 compositions"), "manifest console");
		assertTrue(console.contains("wrote interior PDF"), "interior console");
		assertTrue(console.contains("wrote paperback cover"), "cover console");
		assertTrue(manifestText.contains("title = \"Chess Puzzle Studies\""), "manifest title");
		assertTrue(manifestText.contains("subtitle = \"Regression Sample\""), "manifest subtitle");
		assertTrue(manifestText.contains("[[compositions]]"), "manifest composition tables");

		StudyBook book = StudyBook.load(manifest);
		assertEquals("Codex", book.getAuthor(), "manifest author");
		assertEquals("a5", StudyBook.pageSizeToken(book.getPageSize()), "manifest page size");
		assertFalse(book.isWhiteSideDown(), "manifest orientation");
		assertFalse(book.isShowFen(), "manifest fen visibility");
		assertEquals(2, book.getCompositions().length, "composition count");
		assertEquals("Look at the back rank.", book.getCompositions()[0].getHintLevel1(), "hint import");
		assertEquals(2, book.getCompositions()[0].getFigureFens().size(), "diagram import");

		byte[] interiorBytes = Files.readAllBytes(interiorPdf);
		String interiorText = new String(interiorBytes, StandardCharsets.ISO_8859_1);
		assertTrue(interiorBytes.length > 10_000, "interior pdf size");
		assertTrue(interiorText.contains("/Producer (chess-rtk puzzle study pdf)"), "interior producer");
		assertTrue(interiorText.contains("/Author (Codex)"), "interior author");
		assertTrue(interiorText.contains("/Title (Chess Puzzle Studies: Regression Sample)"), "interior title");

		byte[] coverBytes = Files.readAllBytes(coverPdf);
		String coverText = new String(coverBytes, StandardCharsets.ISO_8859_1);
		assertTrue(coverBytes.length > 6_000, "cover pdf size");
		assertTrue(coverText.contains("/Producer (chess-rtk native book cover pdf)"), "cover producer");
	}

	/**
	 * Verifies validation mode checks the manifest without writing files.
	 *
	 * @throws Exception if validation fails unexpectedly
	 */
	private static void testCheckModeSkipsWrites() throws Exception {
		Path input = Files.createTempFile(PREFIX + "check-", ".json");
		Files.writeString(input, sampleJson(), StandardCharsets.UTF_8);

		Path manifest = Files.createTempDirectory(PREFIX + "check-").resolve("art.book.toml");
		Path interiorPdf = Files.createTempDirectory(PREFIX + "check-pdf-").resolve("art.pdf");
		Path coverPdf = Files.createTempDirectory(PREFIX + "check-cover-").resolve("art-cover.pdf");
		String console = runMain(
				"book", "study",
				"--input", input.toString(),
				"--manifest-output", manifest.toString(),
				"--output", interiorPdf.toString(),
				"--cover-output", coverPdf.toString(),
				"--check");

		assertTrue(console.contains("book study OK: 2 compositions"), "check summary");
		assertFalse(Files.exists(manifest), "check skipped manifest");
		assertFalse(Files.exists(interiorPdf), "check skipped pdf");
		assertFalse(Files.exists(coverPdf), "check skipped cover");
	}

	/**
	 * Verifies the old study command names remain working aliases.
	 *
	 * @throws Exception if validation fails unexpectedly
	 */
	private static void testLegacyAliasesStillRun() throws Exception {
		for (String alias : new String[] { "artofchess", "art" }) {
			Path input = Files.createTempFile(PREFIX + "alias-", ".json");
			Files.writeString(input, sampleJson(), StandardCharsets.UTF_8);

			String console = runMain(
					"book", alias,
					"--input", input.toString(),
					"--check");

			assertTrue(console.contains("book study OK: 2 compositions"), "legacy alias check summary " + alias);
		}
	}

	/**
	 * Builds a small pretty-printed JSON manifest to exercise the rich annotated
	 * loader.
	 *
	 * @return manifest JSON text
	 */
	private static String sampleJson() {
		return """
				{
				  "title": "Legacy Puzzle Study",
				  "subtitle": "Imported Sample",
				  "author": "Legacy",
				  "time": "2024",
				  "location": "Shanghai",
				  "pageSize": "a5",
				  "margin": 42.0,
				  "diagramsPerRow": 1,
				  "boardPixels": 720,
				  "whiteSideDown": false,
				  "showFen": false,
				  "blurb": ["Original back-cover text."],
				  "link": ["legacy.example"],
				  "compositions": [
				    {
				      "title": "Mate in One",
				      "description": "White to move and mate in one.",
				      "analysis": "1. Qh8#.",
				      "hintLevel1": "Look at the back rank.",
				      "figureMovesAlgebraic": ["Start", "1. Qh8#"],
				      "figureMovesDetail": ["Initial position", "Mate"],
				      "figureFens": [
				        "6k1/5ppp/8/8/8/8/5PPP/6KQ w - - 0 1",
				        "7Q/5pkp/8/8/8/8/5PPP/6K1 b - - 0 1"
				      ],
				      "figureArrows": ["h1h8", ""]
				    },
				    {
				      "title": "Punished Weakness",
				      "comment": "Black exploits the loosened kingside dark squares.",
				      "analysis": "2... Qh4#",
				      "id": "A2",
				      "figureMovesAlgebraic": ["Final"],
				      "figureFens": [
				        "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3"
				      ]
				    }
				  ]
				}
				""";
	}
}
