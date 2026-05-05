package testing;

import static testing.TestSupport.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import application.cli.command.book.BookRenderCommand;
import utility.Argv;

/**
 * Zero-dependency regression checks for the {@code book render} CLI command.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class ChessBookCommandRegressionTest {

	/**
	 * Shared temp-file prefix for book-render sources and outputs.
	 */
	private static final String BOOK_RENDER_PREFIX = "book-render-";

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
	 * Marker indicating a raster image was embedded in the generated PDF.
	 */
	private static final String PDF_IMAGE_MARKER = "/Subtype /Image";

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
		testCheckModeDoesNotWritePdf();
		testFreeWatermarkFlag();
		System.out.println("ChessBookCommandRegressionTest: all checks passed");
	}

	/**
	 * Verifies that the CLI can read a JSON book and write a PDF.
	 *
	 * @throws Exception if export fails
	 */
	private static void testJsonBookExport() throws Exception {
		Path input = Files.createTempFile(BOOK_RENDER_PREFIX, JSON_SUFFIX);
		Files.writeString(input, sampleJson(16), StandardCharsets.UTF_8);

		Path output = Files.createTempFile(BOOK_RENDER_PREFIX, ".pdf");
		BookRenderCommand.runBookRender(new Argv(new String[] {
				INPUT_OPTION, input.toString(),
				OUTPUT_OPTION, output.toString(),
				"--title", "CLI Book"
		}));

		byte[] bytes = Files.readAllBytes(output);
		String text = new String(bytes, StandardCharsets.ISO_8859_1);
		assertTrue(bytes.length > 16_000, "pdf size");
		assertTrue(text.contains("/Title (CLI Book: Command Sample)"), "title metadata");
		assertFalse(text.contains(PDF_IMAGE_MARKER), "raster image marker");
	}

	/**
	 * Verifies that the CLI can read the TOML manifest shape and write a native
	 * PDF.
	 *
	 * @throws Exception if export fails
	 */
	private static void testTomlBookExport() throws Exception {
		Path input = Files.createTempFile(BOOK_RENDER_PREFIX, ".toml");
		Files.writeString(input, sampleToml(16), StandardCharsets.UTF_8);

		Path output = Files.createTempFile(BOOK_RENDER_PREFIX, ".pdf");
		BookRenderCommand.runBookRender(new Argv(new String[] {
				input.toString(),
				OUTPUT_OPTION, output.toString()
		}));

		byte[] bytes = Files.readAllBytes(output);
		String text = new String(bytes, StandardCharsets.ISO_8859_1);
		assertTrue(bytes.length > 16_000, "toml pdf size");
		assertTrue(text.contains("/Title (TOML Book Regression: Command Sample)"), "toml title metadata");
		assertTrue(text.contains("Full solutions at page"), "toml solution footnote");
		assertFalse(text.contains(PDF_IMAGE_MARKER), "toml raster image marker");
	}

	/**
	 * Verifies book metadata overrides and source limiting for derived editions.
	 *
	 * @throws Exception if export fails
	 */
	private static void testMetadataOverridesAndLimit() throws Exception {
		Path input = Files.createTempFile(BOOK_RENDER_PREFIX, JSON_SUFFIX);
		Files.writeString(input, sampleJson(12), StandardCharsets.UTF_8);

		Path output = Files.createTempFile(BOOK_RENDER_PREFIX, ".pdf");
		String console = captureStdout(() -> BookRenderCommand.runBookRender(new Argv(new String[] {
					INPUT_OPTION, input.toString(),
					OUTPUT_OPTION, output.toString(),
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
	 * Verifies validation mode checks the manifest without writing a PDF.
	 *
	 * @throws Exception if validation fails unexpectedly
	 */
	private static void testCheckModeDoesNotWritePdf() throws Exception {
		Path input = Files.createTempFile(BOOK_RENDER_PREFIX + "check-", JSON_SUFFIX);
		Files.writeString(input, sampleJson(6), StandardCharsets.UTF_8);

		Path output = Files.createTempDirectory("book-render-check-").resolve("book.pdf");
		String console = captureStdout(() -> BookRenderCommand.runBookRender(new Argv(new String[] {
				INPUT_OPTION, input.toString(),
				OUTPUT_OPTION, output.toString(),
				"--limit", "4",
				"--check"
		})));

		assertTrue(console.contains("book render OK: 4 puzzles"), "check mode summary");
		assertFalse(Files.exists(output), "check mode skipped pdf output");
	}

	/**
	 * Verifies the CLI free-edition flag adds watermark metadata and vector opacity
	 * resources.
	 *
	 * @throws Exception if export fails
	 */
	private static void testFreeWatermarkFlag() throws Exception {
		Path input = Files.createTempFile(BOOK_RENDER_PREFIX + "watermark-", JSON_SUFFIX);
		Files.writeString(input, sampleJson(8), StandardCharsets.UTF_8);

		Path output = Files.createTempFile(BOOK_RENDER_PREFIX + "watermark-", ".pdf");
		String console = captureStdout(() -> BookRenderCommand.runBookRender(new Argv(new String[] {
				INPUT_OPTION, input.toString(),
				OUTPUT_OPTION, output.toString(),
				"--watermark-id", "CLI-ARC-42"
		})));

		byte[] bytes = Files.readAllBytes(output);
		String text = new String(bytes, StandardCharsets.ISO_8859_1);
		assertTrue(bytes.length > 45_000, "watermarked pdf size");
		assertTrue(text.contains("Free electronic copy; printing, resale, and unauthorized redistribution not allowed"),
				"watermarked subject metadata");
		assertTrue(text.contains("Watermark ID CLI-ARC-42"), "watermarked id metadata");
		assertTrue(text.contains("/ExtGState"), "watermark opacity resources");
		assertTrue(console.contains("watermark ID embedded"), "watermark console marker");
		assertFalse(text.contains(PDF_IMAGE_MARKER), "watermark raster image marker");
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
}
