package testing;

import application.cli.PathOps;
import static testing.TestSupport.*;

import java.nio.file.Path;

import application.cli.command.book.BookPdfCommand;
import utility.Argv;

/**
 * Zero-dependency regression checks for the {@code book pdf} CLI command.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */

public final class ChessPDFCommandRegressionTest {

	/**
	 * Utility class; prevent instantiation.
	 */
	private ChessPDFCommandRegressionTest() {
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
		System.out.println("ChessPDFCommandRegressionTest: all checks passed");
	}

	/**
	 * Verifies that a text file of FEN records can be exported to PDF.
	 *
	 * @throws Exception if export fails
	 */
	private static void testFenListExport() throws Exception {
		Path input = PathOps.createLocalTempFile("book-pdf-fens-", ".txt");
		writeUtf8(input,
				"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1\n"
						+ "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1\n");

		Path output = PathOps.createLocalTempFile("book-pdf-fens-", ".pdf");
		BookPdfCommand.runBookPdf(new Argv(new String[] {
				"--input", input.toString(),
				"--output", output.toString(),
				"--title", "Fen Sheet"
		}));

		String text = readLatin1WithMinSize(output, 6_000, "fen-list output size");
		assertTrue(text.contains("/Title (Fen Sheet)"), "fen-list title metadata");
	}

	/**
	 * Verifies that a PGN file can be exported as a multi-diagram PDF.
	 *
	 * @throws Exception if export fails
	 */
	private static void testPgnExport() throws Exception {
		Path input = PathOps.createLocalTempFile("book-pdf-games-", ".pgn");
		writeUtf8(input,
				"[Event \"Regression\"]\n"
						+ "[White \"Alpha\"]\n"
						+ "[Black \"Beta\"]\n"
						+ "[Result \"*\"]\n\n"
						+ "1. e4 e5 2. Nf3 Nc6 *\n");

		Path output = PathOps.createLocalTempFile("book-pdf-games-", ".pdf");
		BookPdfCommand.runBookPdf(new Argv(new String[] {
				"--pgn", input.toString(),
				"--output", output.toString(),
				"--title", "PGN Export"
		}));

		String text = readLatin1WithMinSize(output, 8_000, "pgn output size");
		assertTrue(text.contains("/Title (PGN Export)"), "pgn title metadata");
		assertFalse(text.contains("/Subtype /Image"), "pgn raster image embedding");
	}
}
