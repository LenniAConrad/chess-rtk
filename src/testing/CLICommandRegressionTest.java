package testing;

import static testing.TestSupport.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import chess.core.Position;

/**
 * Regression checks for lightweight CLI command routing and formatting.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
@SuppressWarnings("java:S1192")
public final class CLICommandRegressionTest {

	/**
	 * Shared top-level engine command.
	 */
	private static final String ENGINE_COMMAND = "engine";

	/**
	 * Shared top-level record command.
	 */
	private static final String RECORD_COMMAND = "record";

	/**
	 * Shared Chess960 helper command.
	 */
	private static final String CHESS960_COMMAND = "chess960";

	/**
	 * Shared perft subcommand.
	 */
	private static final String PERFT_COMMAND = "perft";

	/**
	 * Shared FEN option name.
	 */
	private static final String FEN_OPTION = "--fen";

	/**
	 * Shared search-depth option name.
	 */
	private static final String DEPTH_OPTION = "--depth";

	/**
	 * Shared output-format option name.
	 */
	private static final String FORMAT_OPTION = "--format";

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
	 * Standard chess start position used in perft output assertions.
	 */
	private static final String START_FEN =
			"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

	/**
	 * Lichess-derived puzzle position whose legal move list contains a knight fork.
	 */
	private static final String FORK_TAG_FEN =
			"6k1/5p1p/4p3/4q3/3n4/2Q3P1/PP1N1P1P/6K1 b - - 3 37";

	/**
	 * Utility class; prevent instantiation.
	 */
	private CLICommandRegressionTest() {
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
		testFilteredFenGenerationShortcut();
		testFenGenerateFilterValidation();
		testFenTagsLegalMoveTactics();
		testMovesFormatOption();
		testGroupedFenAndMoveCommands();
		testCorePerftCommand();
		testHelpListsNewCommands();
		testContextualHelpForms();
		System.out.println("CLICommandRegressionTest: all checks passed");
	}

	/**
	 * Verifies grouped Chess960 lookup by stable Scharnagl index.
	 */
	private static void testChess960Lookup() {
		String output = TestSupport.runMain("fen", CHESS960_COMMAND, "518");
		assertEquals(CHESS960_STANDARD_FEN, output.strip(), "Chess960 index 518");
	}

	/**
	 * Verifies all-layout export keeps the full 960-position order.
	 */
	private static void testChess960AllLayouts() {
		String[] lines = TestSupport.runMain("fen", CHESS960_COMMAND, "--all", FORMAT_OPTION, "layout")
				.strip()
				.split("\\R");
		assertEquals(960, lines.length, "Chess960 layout count");
		assertEquals("BBQNNRKR", lines[0], "Chess960 first layout");
		assertEquals("RKRNNQBB", lines[959], "Chess960 last layout");
	}

	/**
	 * Verifies FEN normalization and validation helpers return deterministic text.
	 */
	private static void testFenHelpers() {
		assertEquals(SIMPLE_FEN, TestSupport.runMain("fen", "normalize", SIMPLE_FEN).strip(), "fen normalize output");
		assertEquals("valid\t" + SIMPLE_FEN, TestSupport.runMain("fen", "validate", FEN_OPTION, SIMPLE_FEN).strip(),
				"fen-validate output");
		assertEquals(START_FEN, TestSupport.runMain("fen", "normalize", "--startpos").strip(),
				"fen normalize --startpos output");
		assertTrue(TestSupport.runMain("fen", "validate", "--randompos").strip().startsWith("valid\t"),
				"fen validate --randompos output");
	}

	/**
	 * Verifies {@code gen fens} routes to filtered FEN generation.
	 */
	private static void testFilteredFenGenerationShortcut() {
		try {
			Path dir = Files.createTempDirectory("crtk-gen-fens-filter-");
			String output = TestSupport.runMain(
					"gen",
					"fens",
					"--output",
					dir.toString(),
					"--files",
					"1",
					"--per-file",
					"2",
					"--chess960-files",
					"0",
					"--batch",
					"32",
					"--max-attempts",
					"10000",
					"--side",
					"white",
					"--max-pieces",
					"32",
					"--ascii");
			assertTrue(output.contains("filters:"), "gen fens reports active filters");
			Path shard = dir.resolve("fens-0000-std.txt");
			List<String> lines = Files.readAllLines(shard);
			assertEquals(2, lines.size(), "filtered FEN shard line count");
			for (String line : lines) {
				Position position = new Position(line);
				assertTrue(position.isWhiteToMove(), "filtered FEN side to move");
				assertTrue(position.countTotalPieces() <= 32, "filtered FEN max piece count");
			}
		} catch (IOException ex) {
			throw new AssertionError("filtered FEN generation test failed", ex);
		}
	}

	/**
	 * Verifies invalid filter combinations fail before generation starts.
	 */
	private static void testFenGenerateFilterValidation() {
		TestSupport.FailureResult checkConflict = TestSupport.runMainExpectFailure(
				"gen",
				"fens",
				"--files",
				"1",
				"--per-file",
				"1",
				"--chess960-files",
				"0",
				"--in-check",
				"--not-in-check");
		assertEquals(2, checkConflict.exitCode(), "gen fens conflicting check filters exit code");
		assertTrue(checkConflict.stderr().contains("choose at most one"),
				"gen fens conflicting check filters message");

		TestSupport.FailureResult rangeConflict = TestSupport.runMainExpectFailure(
				"fen",
				"generate",
				"--files",
				"1",
				"--per-file",
				"1",
				"--chess960-files",
				"0",
				"--pieces",
				"2",
				"--min-pieces",
				"2");
		assertEquals(2, rangeConflict.exitCode(), "fen generate exact/min conflict exit code");
		assertTrue(rangeConflict.stderr().contains("cannot be combined"),
				"fen generate exact/min conflict message");
	}

	/**
	 * Verifies {@code fen tags} exposes legal-move tactical tags.
	 */
	private static void testFenTagsLegalMoveTactics() {
		String output = TestSupport.runMain("fen", "tags", FEN_OPTION, FORK_TAG_FEN);
		assertTrue(output.contains("TACTIC: motif=fork side=black move=d4e2"),
				"fen tags legal-move fork tag");
		assertFalse(output.contains("behind=pawn@"), "fen tags suppresses pawn-behind skewers");
	}

	/**
	 * Verifies the generic {@code moves} command accepts the shared format option.
	 */
	private static void testMovesFormatOption() {
		String output = TestSupport.runMain("move", "list", FORMAT_OPTION, "both", FEN_OPTION, SIMPLE_FEN);
		assertTrue(output.contains("\t"), "move list --format both tab separator");
		assertTrue(output.contains("a1"), "move list --format both UCI output");
		assertTrue(output.contains("K"), "move list --format both SAN output");

		String startMoves = TestSupport.runMain("move", "list", "--startpos", FORMAT_OPTION, "uci");
		assertTrue(startMoves.contains("e2e4"), "move list --startpos UCI output");
	}

	/**
	 * Verifies grouped command routing delegates to the same underlying helpers.
	 */
	private static void testGroupedFenAndMoveCommands() {
		assertEquals(SIMPLE_FEN, TestSupport.runMain("fen", "normalize", SIMPLE_FEN).strip(), "fen normalize output");
		assertEquals("valid\t" + SIMPLE_FEN, TestSupport.runMain("fen", "validate", FEN_OPTION, SIMPLE_FEN).strip(),
				"fen validate output");
		assertEquals("Ka2", TestSupport.runMain("move", "to-san", FEN_OPTION, SIMPLE_FEN, "a1a2").strip(),
				"move to-san output");
		assertEquals("a1a2", TestSupport.runMain("move", "to-uci", FEN_OPTION, SIMPLE_FEN, "Ka2").strip(),
				"move to-uci output");
		String groupedMoves = TestSupport.runMain("move", "list", FORMAT_OPTION, "both", FEN_OPTION, SIMPLE_FEN);
		assertTrue(groupedMoves.contains("\t"), "move list --format both tab separator");
		assertEquals("e4", TestSupport.runMain("move", "to-san", "--startpos", "e2e4").strip(),
				"move to-san --startpos output");
		assertEquals(
				"rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1",
				TestSupport.runMain("move", "after", "--startpos", "e2e4").strip(),
				"move after --startpos output");
	}

	/**
	 * Verifies detailed perft output stays parseable.
	 */
	private static void testCorePerftCommand() {
		String output = TestSupport.runMain(ENGINE_COMMAND, PERFT_COMMAND, DEPTH_OPTION, "2");
		assertTrue(output.contains("perft depth 2"), "engine perft heading");
		assertTrue(output.contains("nodes: 400"), "engine perft node count");
		assertTrue(output.contains("captures: 0"), "engine perft capture count");
		assertTrue(output.contains("checks: 0"), "engine perft check count");
		assertTrue(output.contains("time-ms:"), "engine perft timing");
		assertTrue(output.contains("nps:"), "engine perft throughput");

		String threaded = TestSupport.runMain(ENGINE_COMMAND, PERFT_COMMAND, DEPTH_OPTION, "2", "--threads", "2");
		assertTrue(threaded.contains("nodes: 400"), "engine perft threaded node count");

		String divide = TestSupport.runMain(ENGINE_COMMAND, PERFT_COMMAND, DEPTH_OPTION, "1", "--divide");
		assertTrue(divide.contains("Perft divide (depth 1)"), "engine perft divide title");
		assertTrue(divide.contains("Move") && divide.contains("Captures"), "engine perft divide table header");
		assertTrue(divide.matches("(?s).*a2a3\\s+1\\s+0\\s+0\\s+0\\s+0\\s+0\\s+0.*"),
				"engine perft divide table row");
		assertTrue(divide.matches("(?s).*Total\\s+20\\s+0\\s+0\\s+0\\s+0\\s+0\\s+0.*"),
				"engine perft divide table total");
		assertTrue(divide.contains("Summary: moves=20 nodes=20"), "engine perft divide summary");

		String detailDivide = TestSupport.runMain(ENGINE_COMMAND, PERFT_COMMAND, DEPTH_OPTION, "1", "--divide",
				FORMAT_OPTION, "detail");
		assertTrue(detailDivide.contains("a2a3: nodes=1"), "engine perft detail divide row");
		assertTrue(detailDivide.contains("total:"), "engine perft detail divide total");

		String stockfish = TestSupport.runMain(ENGINE_COMMAND, PERFT_COMMAND, DEPTH_OPTION, "1", FORMAT_OPTION, "stockfish",
				"--threads", "2");
		assertTrue(stockfish.contains("a2a3: 1"), "engine perft stockfish divide row");
		assertTrue(stockfish.contains("Nodes searched: 20"), "engine perft stockfish total");
		assertFalse(stockfish.contains("FEN:"), "engine perft stockfish omits crtk heading");

		String explicitStart = TestSupport.runMain(ENGINE_COMMAND, PERFT_COMMAND, "--startpos", DEPTH_OPTION, "2");
		assertTrue(explicitStart.contains("FEN: " + START_FEN), "engine perft --startpos uses standard start");
		assertTrue(explicitStart.contains("nodes: 400"), "engine perft --startpos node count");

		String random = TestSupport.runMain(ENGINE_COMMAND, PERFT_COMMAND, "--randompos", DEPTH_OPTION, "1");
		assertTrue(random.contains("FEN: "), "engine perft --randompos prints root FEN");
		assertTrue(random.contains("perft depth 1"), "engine perft --randompos heading");
		assertFalse(random.contains("FEN: " + START_FEN), "engine perft --randompos is not the untouched start");

		TestSupport.FailureResult conflict = TestSupport.runMainExpectFailure(
				ENGINE_COMMAND,
				PERFT_COMMAND,
				"--startpos",
				FEN_OPTION,
				SIMPLE_FEN,
				DEPTH_OPTION,
				"1");
		assertEquals(2, conflict.exitCode(), "engine perft conflicting selectors exit code");
		assertTrue(conflict.stderr().contains("choose at most one"), "engine perft conflicting selectors message");
	}

	/**
	 * Verifies help output exposes the new grouped and helper commands.
	 */
	private static void testHelpListsNewCommands() {
		String summary = TestSupport.runMain("help");
		assertTrue(summary.contains(RECORD_COMMAND), "help lists record group");
		assertTrue(summary.contains("fen"), "help lists fen group");
		assertTrue(summary.contains("gen"), "help lists gen group");
		assertTrue(summary.contains("move"), "help lists move group");
		assertTrue(summary.contains(ENGINE_COMMAND), "help lists engine group");
		assertTrue(summary.contains("book"), "help lists book group");
		assertTrue(summary.contains("puzzle"), "help lists puzzle group");
		assertTrue(summary.contains("doctor"), "help lists doctor");
		assertTrue(!summary.contains("record-to-plain"), "short help omits removed record converter");

		String chess960 = TestSupport.runMain("help", "fen", CHESS960_COMMAND);
		assertTrue(chess960.contains("usage: crtk fen chess960"), "help fen chess960 usage");

		String recordHelp = TestSupport.runMain("help", RECORD_COMMAND);
		assertTrue(recordHelp.contains("record subcommands:"), "help record subcommands");
		assertTrue(recordHelp.contains("Export records as plain"), "help record export");

		String recordExport = TestSupport.runMain("help", RECORD_COMMAND, "export");
		assertTrue(recordExport.contains("record export subcommands:"), "help record export subcommands");

		String recordExportPlain = TestSupport.runMain("help", RECORD_COMMAND, "export", "plain");
		assertTrue(recordExportPlain.contains("record export plain options:"), "help record export plain options");

		String fen = TestSupport.runMain("help", "fen");
		assertTrue(fen.contains("fen subcommands:"), "help fen subcommands");

		String gen = TestSupport.runMain("help", "gen");
		assertTrue(gen.contains("gen subcommands:"), "help gen subcommands");

		String full = TestSupport.runMain("help", "--full");
		assertTrue(full.contains("gen subcommands:"), "full help lists gen subcommands");

		String engineHelp = TestSupport.runMain("help", ENGINE_COMMAND);
		assertTrue(engineHelp.contains("uci-smoke"), "help engine lists uci-smoke");

		String enginePerft = TestSupport.runMain("help", ENGINE_COMMAND, PERFT_COMMAND);
		assertTrue(enginePerft.contains("engine perft options:"), "help engine perft options");
		assertTrue(enginePerft.contains("--startpos"), "help engine perft startpos option");
		assertTrue(enginePerft.contains("--randompos"), "help engine perft randompos option");
		assertTrue(enginePerft.contains("--format FMT"), "help engine perft format option");
		assertTrue(enginePerft.contains("--threads N"), "help engine perft threads option");

		String fenDisplay = TestSupport.runMain("help", "fen", "display");
		assertTrue(fenDisplay.contains("--startpos"), "help fen display startpos option");
		assertTrue(fenDisplay.contains("--randompos"), "help fen display randompos option");

		String fenGenerate = TestSupport.runMain("help", "fen", "generate");
		assertTrue(fenGenerate.contains("--endgame"), "help fen generate endgame filter");
		assertTrue(fenGenerate.contains("--rooks N"), "help fen generate rooks filter");
		assertTrue(fenGenerate.contains("--en-passant"), "help fen generate en-passant filter");
		assertTrue(fenGenerate.contains("Filters combine with AND"), "help fen generate filter contract");
		assertFalse(fenGenerate.contains("puzzle mine options"), "help fen generate does not bleed into next section");

		String moveList = TestSupport.runMain("help", "move", "list");
		assertTrue(moveList.contains("--startpos"), "help move list startpos option");
		assertTrue(moveList.contains("--randompos"), "help move list randompos option");

		String moveAfter = TestSupport.runMain("help", "move", "after");
		assertTrue(moveAfter.contains("--startpos"), "help move after startpos option");
		assertTrue(moveAfter.contains("--randompos"), "help move after randompos option");

		String engineBestmove = TestSupport.runMain("help", ENGINE_COMMAND, "bestmove");
		assertTrue(engineBestmove.contains("--startpos"), "help engine bestmove startpos option");
		assertTrue(engineBestmove.contains("--randompos"), "help engine bestmove randompos option");

		String engineBuiltin = TestSupport.runMain("help", ENGINE_COMMAND, "builtin");
		assertTrue(engineBuiltin.contains("--startpos"), "help engine builtin startpos option");
		assertTrue(engineBuiltin.contains("--randompos"), "help engine builtin randompos option");

		String engineEval = TestSupport.runMain("help", ENGINE_COMMAND, "eval");
		assertTrue(engineEval.contains("--startpos"), "help engine eval startpos option");
		assertTrue(engineEval.contains("--randompos"), "help engine eval randompos option");

		String enginePerftSuite = TestSupport.runMain("help", ENGINE_COMMAND, "perft-suite");
		assertTrue(enginePerftSuite.contains("engine perft-suite options:"),
				"help engine perft-suite options");
		assertTrue(enginePerftSuite.contains("--threads N"), "help engine perft-suite threads option");
		assertFalse(enginePerftSuite.contains("--stockfish"), "help engine perft-suite does not expose stockfish");

		String uciSmoke = TestSupport.runMain("help", ENGINE_COMMAND, "uci-smoke");
		assertTrue(uciSmoke.contains("engine uci-smoke options:"), "help engine uci-smoke options");

		String doctor = TestSupport.runMain("help", "doctor");
		assertTrue(doctor.contains("doctor options:"), "help doctor options");
	}

	/**
	 * Verifies contextual help works from both `help` and `--help` entry points.
	 */
	private static void testContextualHelpForms() {
		String rootHelp = TestSupport.runMain("--help");
		assertTrue(rootHelp.contains("crtk <area> <action> [options] [args]"), "root help usage grammar");
		assertTrue(rootHelp.contains("crtk move list --help"), "root help advertises local help");

		String moveHelp = TestSupport.runMain("move", "--help");
		assertTrue(moveHelp.contains("usage: crtk move <action> [options] [args]"), "move area help usage");
		assertTrue(moveHelp.contains("subcommands:"), "move area help subcommands");

		String moveListHelp = TestSupport.runMain("move", "list", "--help");
		assertTrue(moveListHelp.contains("move list options:"), "move list local help options");
		assertTrue(moveListHelp.contains("Canonical move-listing command"), "move list local help summary");

		String bestmoveHelp = TestSupport.runMain("engine", "bestmove", "--help");
		assertTrue(bestmoveHelp.contains("engine bestmove options:"), "bestmove local help options");
		assertTrue(bestmoveHelp.contains("Canonical best-move command"), "bestmove local help summary");

		String aliasHelp = TestSupport.runMain("help", "move", "line");
		assertTrue(aliasHelp.contains("usage: crtk move line [options] MOVES..."), "alias help usage");
		assertTrue(aliasHelp.contains("canonical command:"), "alias help canonical path");

		String datasetHelp = TestSupport.runMain("record", "dataset", "--help");
		assertTrue(datasetHelp.contains("usage: crtk record dataset [kind] [options]"), "record dataset group help");
		assertTrue(datasetHelp.contains("npy"), "record dataset group lists npy");
	}
}
