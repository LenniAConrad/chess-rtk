package testing;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import application.cli.command.ChessBookCommand;
import utility.Argv;

/**
 * Zero-dependency regression checks for the {@code chess-book} CLI command.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class ChessBookCommandRegressionTest {

	/**
	 * Utility class; prevent instantiation.
	 */
	private ChessBookCommandRegressionTest() {
		// utility
	}

	/**
	 * Runs the regression checks.
	 *
	 * @param args unused command-line arguments
	 * @throws Exception if any check fails unexpectedly
	 */
	public static void main(String[] args) throws Exception {
		testJsonBookExport();
		testTomlBookExport();
		testMetadataOverridesAndLimit();
		testFreeWatermarkFlag();
		System.out.println("ChessBookCommandRegressionTest: all checks passed");
	}

	/**
	 * Verifies that the CLI can read a JSON book and write a PDF.
	 *
	 * @throws Exception if export fails
	 */
	private static void testJsonBookExport() throws Exception {
		Path input = Files.createTempFile("chess-book-", ".json");
		Files.writeString(input, sampleJson(16), StandardCharsets.UTF_8);

		Path output = Files.createTempFile("chess-book-", ".pdf");
		ChessBookCommand.runChessBook(new Argv(new String[] {
				"--input", input.toString(),
				"--output", output.toString(),
				"--title", "CLI Book"
		}));

		byte[] bytes = Files.readAllBytes(output);
		String text = new String(bytes, StandardCharsets.ISO_8859_1);
		assertTrue(bytes.length > 16_000, "pdf size");
		assertTrue(text.contains("/Title (CLI Book: Command Sample)"), "title metadata");
		assertFalse(text.contains("/Subtype /Image"), "raster image marker");
	}

	/**
	 * Verifies that the CLI can read the TOML manifest shape and write a native
	 * PDF.
	 *
	 * @throws Exception if export fails
	 */
	private static void testTomlBookExport() throws Exception {
		Path input = Files.createTempFile("chess-book-", ".toml");
		Files.writeString(input, sampleToml(16), StandardCharsets.UTF_8);

		Path output = Files.createTempFile("chess-book-", ".pdf");
		ChessBookCommand.runChessBook(new Argv(new String[] {
				input.toString(),
				"--output", output.toString()
		}));

		byte[] bytes = Files.readAllBytes(output);
		String text = new String(bytes, StandardCharsets.ISO_8859_1);
		assertTrue(bytes.length > 16_000, "toml pdf size");
		assertTrue(text.contains("/Title (TOML Book Regression: Command Sample)"), "toml title metadata");
		assertTrue(text.contains("Full solutions at page"), "toml solution footnote");
		assertFalse(text.contains("/Subtype /Image"), "toml raster image marker");
	}

	/**
	 * Verifies book metadata overrides and source limiting for derived editions.
	 *
	 * @throws Exception if export fails
	 */
	private static void testMetadataOverridesAndLimit() throws Exception {
		Path input = Files.createTempFile("chess-book-", ".json");
		Files.writeString(input, sampleJson(12), StandardCharsets.UTF_8);

		Path output = Files.createTempFile("chess-book-", ".pdf");
		String console = captureStdout(() -> ChessBookCommand.runChessBook(new Argv(new String[] {
					"--input", input.toString(),
					"--output", output.toString(),
					"--title", "Art of Chess Puzzles",
					"--subtitle", "400 Mate in 2 Puzzles",
					"--limit", "5"
			})));

		byte[] bytes = Files.readAllBytes(output);
		String text = new String(bytes, StandardCharsets.ISO_8859_1);
		assertTrue(bytes.length > 10_000, "limited pdf size");
		assertTrue(text.contains("/Title (Art of Chess Puzzles: 400 Mate in 2 Puzzles)"), "metadata override");
		assertTrue(text.contains("This book contains 5 generated puzzles."), "count text updated");
		assertTrue(console.contains("wrote 5 puzzles"), "limited puzzle count");
	}

	/**
	 * Verifies the CLI free-edition flag adds watermark metadata and vector opacity
	 * resources.
	 *
	 * @throws Exception if export fails
	 */
	private static void testFreeWatermarkFlag() throws Exception {
		Path input = Files.createTempFile("chess-book-watermark-", ".json");
		Files.writeString(input, sampleJson(8), StandardCharsets.UTF_8);

		Path output = Files.createTempFile("chess-book-watermark-", ".pdf");
		String console = captureStdout(() -> ChessBookCommand.runChessBook(new Argv(new String[] {
				"--input", input.toString(),
				"--output", output.toString(),
				"--free-watermark"
		})));

		byte[] bytes = Files.readAllBytes(output);
		String text = new String(bytes, StandardCharsets.ISO_8859_1);
		assertTrue(bytes.length > 45_000, "watermarked pdf size");
		assertTrue(text.contains("Free electronic copy; printing, resale, and unauthorized redistribution not allowed"),
				"watermarked subject metadata");
		assertTrue(text.contains("/ExtGState"), "watermark opacity resources");
		assertTrue(console.contains("free watermarked PDF"), "watermark console marker");
		assertFalse(text.contains("/Subtype /Image"), "watermark raster image marker");
	}

	/**
	 * Captures standard output while running a small regression action.
	 *
	 * @param action action to run
	 * @return captured standard output as UTF-8-compatible text
	 */
	private static String captureStdout(Runnable action) {
		PrintStream original = System.out;
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		try (PrintStream replacement = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
			System.setOut(replacement);
			action.run();
		} finally {
			System.setOut(original);
		}
		return buffer.toString(StandardCharsets.UTF_8);
	}

	/**
	 * Builds a small book JSON document.
	 *
	 * @param count requested puzzle count
	 * @return JSON book source text
	 */
	private static String sampleJson(int count) {
		String fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
		String[] moves = { "1. e4", "1. d4", "1. Nf3", "1. c4" };
		StringBuilder builder = new StringBuilder(4_096);
		builder.append("{\n")
				.append("  \"title\": \"CLI Book Regression\",\n")
				.append("  \"subtitle\": \"Command Sample\",\n")
				.append("  \"author\": \"Codex\",\n")
				.append("  \"time\": \"2026\",\n")
				.append("  \"location\": \"Hangzhou\",\n")
				.append("  \"language\": \"English\",\n")
				.append("  \"tablefrequency\": 2,\n")
				.append("  \"puzzlerows\": 2,\n")
				.append("  \"puzzlecolumns\": 2,\n")
				.append("  \"dedication\": [\"For the CLI regression suite.\"],\n")
				.append("  \"introduction\": [\"This book contains ").append(count).append(" generated puzzles.\"],\n")
				.append("  \"howToRead\": [\"Solve the left page first.\", \"Then check the solution spread.\"],\n")
				.append("  \"afterword\": [\"Regression complete.\"],\n")
				.append("  \"elements\": [\n");
		for (int i = 0; i < count; i++) {
			if (i > 0) {
				builder.append(",\n");
			}
			builder.append("    {\"position\":\"").append(fen).append("\",\"moves\":\"")
					.append(moves[i % moves.length]).append("\"}");
		}
		builder.append("\n  ]\n")
				.append("}\n");
		return builder.toString();
	}

	/**
	 * Builds a small TOML book manifest using the {@code [[elements]]} table
	 * format.
	 *
	 * @param count requested puzzle count
	 * @return TOML book source text
	 */
	private static String sampleToml(int count) {
		String fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
		String[] moves = { "1. e4", "1. d4", "1. Nf3", "1. c4" };
		StringBuilder builder = new StringBuilder(4_096);
		builder.append("title = \"TOML Book Regression\"\n")
				.append("subtitle = \"Command Sample\"\n")
				.append("author = \"Codex\"\n")
				.append("time = \"2026\"\n")
				.append("location = \"Hangzhou\"\n")
				.append("language = \"English\"\n")
				.append("tablefrequency = 2\n")
				.append("puzzlerows = 2\n")
				.append("puzzlecolumns = 2\n")
				.append("dedication = [\"For the TOML regression suite.\"]\n")
				.append("introduction = [\"This book is generated from a TOML manifest.\"]\n")
				.append("howToRead = [\"Solve the left page first.\", \"Then check the solution spread.\"]\n")
				.append("afterword = [\"Regression complete.\"]\n\n");
		for (int i = 0; i < count; i++) {
			builder.append("[[elements]]\n")
					.append("position = \"").append(fen).append("\"\n")
					.append("moves = \"").append(moves[i % moves.length]).append("\"\n\n");
		}
		return builder.toString();
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
