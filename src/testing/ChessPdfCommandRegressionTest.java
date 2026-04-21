package testing;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import application.cli.command.ChessPdfCommand;
import utility.Argv;

/**
 * Zero-dependency regression checks for the {@code book pdf} CLI command.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class ChessPdfCommandRegressionTest {

	/**
	 * Utility class; prevent instantiation.
	 */
	private ChessPdfCommandRegressionTest() {
		// utility
	}

	/**
	 * Runs the command regression checks.
	 *
	 * @param args unused command-line arguments
	 * @throws Exception if any check fails unexpectedly
	 */
	public static void main(String[] args) throws Exception {
		testFenListExport();
		testPgnExport();
		System.out.println("ChessPdfCommandRegressionTest: all checks passed");
	}

	/**
	 * Verifies that a text file of FEN records can be exported to PDF.
	 *
	 * @throws Exception if export fails
	 */
	private static void testFenListExport() throws Exception {
		Path input = Files.createTempFile("book-pdf-fens-", ".txt");
		Files.writeString(input,
				"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1\n"
						+ "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1\n",
				StandardCharsets.UTF_8);

		Path output = Files.createTempFile("book-pdf-fens-", ".pdf");
		ChessPdfCommand.runChessPdf(new Argv(new String[] {
				"--input", input.toString(),
				"--output", output.toString(),
				"--title", "Fen Sheet"
		}));

		byte[] bytes = Files.readAllBytes(output);
		assertTrue(bytes.length > 6_000, "fen-list output size");
		assertTrue(new String(bytes, StandardCharsets.ISO_8859_1).contains("/Title (Fen Sheet)"),
				"fen-list title metadata");
	}

	/**
	 * Verifies that a PGN file can be exported as a multi-diagram PDF.
	 *
	 * @throws Exception if export fails
	 */
	private static void testPgnExport() throws Exception {
		Path input = Files.createTempFile("book-pdf-games-", ".pgn");
		Files.writeString(input,
				"[Event \"Regression\"]\n"
						+ "[White \"Alpha\"]\n"
						+ "[Black \"Beta\"]\n"
						+ "[Result \"*\"]\n\n"
						+ "1. e4 e5 2. Nf3 Nc6 *\n",
				StandardCharsets.UTF_8);

		Path output = Files.createTempFile("book-pdf-games-", ".pdf");
		ChessPdfCommand.runChessPdf(new Argv(new String[] {
				"--pgn", input.toString(),
				"--output", output.toString(),
				"--title", "PGN Export"
		}));

		byte[] bytes = Files.readAllBytes(output);
		String text = new String(bytes, StandardCharsets.ISO_8859_1);
		assertTrue(bytes.length > 8_000, "pgn output size");
		assertTrue(text.contains("/Title (PGN Export)"), "pgn title metadata");
		assertFalse(text.contains("/Subtype /Image"), "pgn raster image embedding");
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
}
