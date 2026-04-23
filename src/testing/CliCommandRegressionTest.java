package testing;

import static testing.TestSupport.*;

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
		testCorePerftCommand();
		testHelpListsNewCommands();
		System.out.println("CliCommandRegressionTest: all checks passed");
	}

	/**
	 * Verifies grouped Chess960 lookup by stable Scharnagl index.
	 */
	private static void testChess960Lookup() {
		String output = TestSupport.runMain("fen", "chess960", "518");
		assertEquals(CHESS960_STANDARD_FEN, output.strip(), "Chess960 index 518");
	}

	/**
	 * Verifies all-layout export keeps the full 960-position order.
	 */
	private static void testChess960AllLayouts() {
		String[] lines = TestSupport.runMain("fen", "chess960", "--all", "--format", "layout").strip().split("\\R");
		assertEquals(960, lines.length, "Chess960 layout count");
		assertEquals("BBQNNRKR", lines[0], "Chess960 first layout");
		assertEquals("RKRNNQBB", lines[959], "Chess960 last layout");
	}

	/**
	 * Verifies FEN normalization and validation helpers return deterministic text.
	 */
	private static void testFenHelpers() {
		assertEquals(SIMPLE_FEN, TestSupport.runMain("fen", "normalize", SIMPLE_FEN).strip(), "fen normalize output");
		assertEquals("valid\t" + SIMPLE_FEN, TestSupport.runMain("fen", "validate", "--fen", SIMPLE_FEN).strip(),
				"fen-validate output");
	}

	/**
	 * Verifies the generic {@code moves} command accepts the shared format option.
	 */
	private static void testMovesFormatOption() {
		String output = TestSupport.runMain("move", "list", "--format", "both", "--fen", SIMPLE_FEN);
		assertTrue(output.contains("\t"), "move list --format both tab separator");
		assertTrue(output.contains("a1"), "move list --format both UCI output");
		assertTrue(output.contains("K"), "move list --format both SAN output");
	}

	/**
	 * Verifies grouped command routing delegates to the same underlying helpers.
	 */
	private static void testGroupedFenAndMoveCommands() {
		assertEquals(SIMPLE_FEN, TestSupport.runMain("fen", "normalize", SIMPLE_FEN).strip(), "fen normalize output");
		assertEquals("valid\t" + SIMPLE_FEN, TestSupport.runMain("fen", "validate", "--fen", SIMPLE_FEN).strip(),
				"fen validate output");
		assertEquals("Ka2", TestSupport.runMain("move", "to-san", "--fen", SIMPLE_FEN, "a1a2").strip(),
				"move to-san output");
		assertEquals("a1a2", TestSupport.runMain("move", "to-uci", "--fen", SIMPLE_FEN, "Ka2").strip(),
				"move to-uci output");
		String groupedMoves = TestSupport.runMain("move", "list", "--format", "both", "--fen", SIMPLE_FEN);
		assertTrue(groupedMoves.contains("\t"), "move list --format both tab separator");
	}

	/**
	 * Verifies detailed perft output stays parseable.
	 */
	private static void testCorePerftCommand() {
		String output = TestSupport.runMain("engine", "perft", "--depth", "2");
		assertTrue(output.contains("perft depth 2"), "engine perft heading");
		assertTrue(output.contains("nodes: 400"), "engine perft node count");
		assertTrue(output.contains("captures: 0"), "engine perft capture count");
		assertTrue(output.contains("checks: 0"), "engine perft check count");
		assertTrue(output.contains("time-ms:"), "engine perft timing");
		assertTrue(output.contains("nps:"), "engine perft throughput");

		String threaded = TestSupport.runMain("engine", "perft", "--depth", "2", "--threads", "2");
		assertTrue(threaded.contains("nodes: 400"), "engine perft threaded node count");

		String divide = TestSupport.runMain("engine", "perft", "--depth", "1", "--divide");
		assertTrue(divide.contains("Perft divide (depth 1)"), "engine perft divide title");
		assertTrue(divide.contains("Move") && divide.contains("Captures"), "engine perft divide table header");
		assertTrue(divide.matches("(?s).*a2a3\\s+1\\s+0\\s+0\\s+0\\s+0\\s+0\\s+0.*"),
				"engine perft divide table row");
		assertTrue(divide.matches("(?s).*Total\\s+20\\s+0\\s+0\\s+0\\s+0\\s+0\\s+0.*"),
				"engine perft divide table total");
		assertTrue(divide.contains("Summary: moves=20 nodes=20"), "engine perft divide summary");

		String detailDivide = TestSupport.runMain("engine", "perft", "--depth", "1", "--divide", "--format", "detail");
		assertTrue(detailDivide.contains("a2a3: nodes=1"), "engine perft detail divide row");
		assertTrue(detailDivide.contains("total:"), "engine perft detail divide total");

		String stockfish = TestSupport.runMain("engine", "perft", "--depth", "1", "--format", "stockfish",
				"--threads", "2");
		assertTrue(stockfish.contains("a2a3: 1"), "engine perft stockfish divide row");
		assertTrue(stockfish.contains("Nodes searched: 20"), "engine perft stockfish total");
		assertFalse(stockfish.contains("FEN:"), "engine perft stockfish omits crtk heading");
	}

	/**
	 * Verifies help output exposes the new grouped and helper commands.
	 */
	private static void testHelpListsNewCommands() {
		String summary = TestSupport.runMain("help");
		assertTrue(summary.contains("record"), "help lists record group");
		assertTrue(summary.contains("fen"), "help lists fen group");
		assertTrue(summary.contains("move"), "help lists move group");
		assertTrue(summary.contains("engine"), "help lists engine group");
		assertTrue(summary.contains("book"), "help lists book group");
		assertTrue(summary.contains("puzzle"), "help lists puzzle group");
		assertTrue(summary.contains("doctor"), "help lists doctor");
		assertTrue(!summary.contains("record-to-plain"), "short help omits removed record converter");

		String chess960 = TestSupport.runMain("help", "fen", "chess960");
		assertTrue(chess960.contains("usage: crtk fen chess960"), "help fen chess960 usage");

		String record = TestSupport.runMain("help", "record");
		assertTrue(record.contains("record subcommands:"), "help record subcommands");
		assertTrue(record.contains("export FORMAT"), "help record export");

		String recordExport = TestSupport.runMain("help", "record", "export");
		assertTrue(recordExport.contains("record export subcommands:"), "help record export subcommands");

		String recordExportPlain = TestSupport.runMain("help", "record", "export", "plain");
		assertTrue(recordExportPlain.contains("record export plain options:"), "help record export plain options");

		String fen = TestSupport.runMain("help", "fen");
		assertTrue(fen.contains("fen subcommands:"), "help fen subcommands");

		String engine = TestSupport.runMain("help", "engine");
		assertTrue(engine.contains("uci-smoke"), "help engine lists uci-smoke");

		String enginePerft = TestSupport.runMain("help", "engine", "perft");
		assertTrue(enginePerft.contains("engine perft options:"), "help engine perft options");
		assertTrue(enginePerft.contains("--format FMT"), "help engine perft format option");
		assertTrue(enginePerft.contains("--threads N"), "help engine perft threads option");

		String enginePerftSuite = TestSupport.runMain("help", "engine", "perft-suite");
		assertTrue(enginePerftSuite.contains("engine perft-suite options:"),
				"help engine perft-suite options");
		assertTrue(enginePerftSuite.contains("--threads N"), "help engine perft-suite threads option");
		assertFalse(enginePerftSuite.contains("--stockfish"), "help engine perft-suite does not expose stockfish");

		String uciSmoke = TestSupport.runMain("help", "engine", "uci-smoke");
		assertTrue(uciSmoke.contains("engine uci-smoke options:"), "help engine uci-smoke options");

		String doctor = TestSupport.runMain("help", "doctor");
		assertTrue(doctor.contains("doctor options:"), "help doctor options");
	}
}
