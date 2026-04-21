package testing;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import application.Main;

/**
 * Regression checks for lightweight CLI command routing and formatting.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class CliCommandRegressionTest {

	/**
	 * Known standard chess start position at Chess960 index 518.
	 */
	private static final String CHESS960_STANDARD_FEN =
			"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w HAha - 0 1";

	/**
	 * Simple legal bare-king position used by non-engine CLI checks.
	 */
	private static final String SIMPLE_FEN = "8/8/8/8/8/8/8/K6k w - - 0 1";

	/**
	 * Utility class; prevent instantiation.
	 */
	private CliCommandRegressionTest() {
		// utility
	}

	/**
	 * Runs the CLI regression checks.
	 *
	 * @param args unused command-line arguments
	 */
	public static void main(String[] args) {
		testChess960Lookup();
		testChess960AllLayouts();
		testFenHelpers();
		testMovesFormatOption();
		testGroupedFenAndMoveCommands();
		testHelpListsNewCommands();
		System.out.println("CliCommandRegressionTest: all checks passed");
	}

	/**
	 * Verifies grouped Chess960 lookup by stable Scharnagl index.
	 */
	private static void testChess960Lookup() {
		String output = run("fen", "chess960", "518");
		assertEquals(CHESS960_STANDARD_FEN, output.strip(), "Chess960 index 518");
	}

	/**
	 * Verifies all-layout export keeps the full 960-position order.
	 */
	private static void testChess960AllLayouts() {
		String[] lines = run("fen", "chess960", "--all", "--format", "layout").strip().split("\\R");
		assertEquals(960, lines.length, "Chess960 layout count");
		assertEquals("BBQNNRKR", lines[0], "Chess960 first layout");
		assertEquals("RKRNNQBB", lines[959], "Chess960 last layout");
	}

	/**
	 * Verifies FEN normalization and validation helpers return deterministic text.
	 */
	private static void testFenHelpers() {
		assertEquals(SIMPLE_FEN, run("fen", "normalize", SIMPLE_FEN).strip(), "fen normalize output");
		assertEquals("valid\t" + SIMPLE_FEN, run("fen", "validate", "--fen", SIMPLE_FEN).strip(),
				"fen-validate output");
	}

	/**
	 * Verifies the generic {@code moves} command accepts the shared format option.
	 */
	private static void testMovesFormatOption() {
		String output = run("move", "list", "--format", "both", "--fen", SIMPLE_FEN);
		assertTrue(output.contains("\t"), "move list --format both tab separator");
		assertTrue(output.contains("a1"), "move list --format both UCI output");
		assertTrue(output.contains("K"), "move list --format both SAN output");
	}

	/**
	 * Verifies grouped command routing delegates to the same underlying helpers.
	 */
	private static void testGroupedFenAndMoveCommands() {
		assertEquals(SIMPLE_FEN, run("fen", "normalize", SIMPLE_FEN).strip(), "fen normalize output");
		assertEquals("valid\t" + SIMPLE_FEN, run("fen", "validate", "--fen", SIMPLE_FEN).strip(),
				"fen validate output");
		assertEquals("Ka2", run("move", "to-san", "--fen", SIMPLE_FEN, "a1a2").strip(),
				"move to-san output");
		assertEquals("a1a2", run("move", "to-uci", "--fen", SIMPLE_FEN, "Ka2").strip(),
				"move to-uci output");
		String groupedMoves = run("move", "list", "--format", "both", "--fen", SIMPLE_FEN);
		assertTrue(groupedMoves.contains("\t"), "move list --format both tab separator");
	}

	/**
	 * Verifies help output exposes the new grouped and helper commands.
	 */
	private static void testHelpListsNewCommands() {
		String summary = run("help");
		assertTrue(summary.contains("record"), "help lists record group");
		assertTrue(summary.contains("fen"), "help lists fen group");
		assertTrue(summary.contains("move"), "help lists move group");
		assertTrue(summary.contains("engine"), "help lists engine group");
		assertTrue(summary.contains("book"), "help lists book group");
		assertTrue(summary.contains("puzzle"), "help lists puzzle group");
		assertTrue(summary.contains("doctor"), "help lists doctor");
		assertTrue(!summary.contains("record-to-plain"), "short help omits removed record converter");

		String chess960 = run("help", "fen", "chess960");
		assertTrue(chess960.contains("usage: crtk fen chess960"), "help fen chess960 usage");

		String record = run("help", "record");
		assertTrue(record.contains("record subcommands:"), "help record subcommands");
		assertTrue(record.contains("export FORMAT"), "help record export");

		String recordExport = run("help", "record", "export");
		assertTrue(recordExport.contains("record export subcommands:"), "help record export subcommands");

		String recordExportPlain = run("help", "record", "export", "plain");
		assertTrue(recordExportPlain.contains("record export plain options:"), "help record export plain options");

		String fen = run("help", "fen");
		assertTrue(fen.contains("fen subcommands:"), "help fen subcommands");

		String engine = run("help", "engine");
		assertTrue(engine.contains("uci-smoke"), "help engine lists uci-smoke");

		String uciSmoke = run("help", "engine", "uci-smoke");
		assertTrue(uciSmoke.contains("engine uci-smoke options:"), "help engine uci-smoke options");

		String doctor = run("help", "doctor");
		assertTrue(doctor.contains("doctor options:"), "help doctor options");
	}

	/**
	 * Captures standard output while invoking the application entry point.
	 *
	 * @param args command-line arguments to pass to {@link Main#main(String[])}
	 * @return captured standard output
	 */
	private static String run(String... args) {
		PrintStream original = System.out;
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		try (PrintStream replacement = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
			System.setOut(replacement);
			Main.main(args);
		} finally {
			System.setOut(original);
		}
		return buffer.toString(StandardCharsets.UTF_8);
	}

	/**
	 * Verifies equality for integer values.
	 *
	 * @param expected expected value
	 * @param actual   actual value
	 * @param label    assertion label
	 */
	private static void assertEquals(int expected, int actual, String label) {
		if (expected != actual) {
			throw new AssertionError(label + ": expected " + expected + ", got " + actual);
		}
	}

	/**
	 * Verifies equality for string values.
	 *
	 * @param expected expected value
	 * @param actual   actual value
	 * @param label    assertion label
	 */
	private static void assertEquals(String expected, String actual, String label) {
		if (!expected.equals(actual)) {
			throw new AssertionError(label + ": expected " + expected + ", got " + actual);
		}
	}

	/**
	 * Fails when the supplied condition is false.
	 *
	 * @param condition condition to verify
	 * @param label     assertion label
	 */
	private static void assertTrue(boolean condition, String label) {
		if (!condition) {
			throw new AssertionError(label + ": expected true");
		}
	}
}
