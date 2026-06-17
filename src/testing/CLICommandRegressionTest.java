package testing;

import application.cli.PathOps;
import application.cli.command.VersionCommand;
import static testing.TestSupport.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;

import chess.schema.JsonParser;
import chess.schema.JsonValue;
import chess.describe.PositionDescriptionInput;
import chess.describe.PositionDescriptionVerifier;
import chess.core.Position;
import chess.eval.Evaluator;
import chess.nn.otis.Model;
import utility.Json;

/**
 * Regression checks for lightweight CLI command routing and formatting.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */

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
	 * Simple position after moving the White king from a1 to a2.
	 */
	private static final String SIMPLE_AFTER_KA2_FEN = "8/8/8/8/8/8/K7/7k b - - 1 1";

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
		System.setProperty(Evaluator.LC0_DISABLED_PROPERTY, "true");
		testChess960Lookup();
		testChess960AllLayouts();
		testFenHelpers();
		testFenRenderSvgAccent();
		testFenRelations();
		testFilteredFenGenerationShortcut();
		testFenGenerateFilterValidation();
		testFenTagsLegalMoveTactics();
		testFenTagsAnalyzeGame();
		testPuzzleTagsAnalyzeFlagConflict();
		testMovesFormatOption();
		testGroupedFenAndMoveCommands();
		testMachineReadableFenAndMoveCommands();
		testStructuredFenAndMoveFailures();
		testStructuredLegacyCommandFailures();
		testVersionCommand();
		testDiagnosticJsonCommands();
		testBatchRunCommandScript();
		testDefaultOutputPathsUseDumpDirectory();
		testCorePerftCommand();
		testEngineEvalEvaluatorModes();
		testHighValueResearchCommands();
		testPositionDescribeClassicalGoldenOutput();
		testPositionDescribeStrategicPlans();
		testPositionDescribeJsonlAndT5Failure();
		testPositionDescribeEngineEval();
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
	 * Verifies the generic batch runner executes one CLI command per script row.
	 */
	private static void testBatchRunCommandScript() {
		try {
			Path script = PathOps.createLocalTempFile("crtk-batch-run-", ".txt");
			Files.writeString(script, String.join(System.lineSeparator(),
					"help version",
					"fen normalize --fen \"" + SIMPLE_FEN + "\""));
			String output = TestSupport.runMain("batch", "run", "--input", script.toString(), "--quiet");
			assertTrue(output.contains("Print the launcher version"), "batch run help command");
			assertTrue(output.contains(SIMPLE_FEN), "batch run fen command");

			Path stdinScript = PathOps.createLocalTempFile("crtk-batch-run-stdin-", ".txt");
			Files.writeString(stdinScript, "fen normalize --stdin");
			TestSupport.FailureResult failure = TestSupport.runMainExpectFailure("batch", "run",
					"--input", stdinScript.toString(), "--quiet");
			assertTrue(failure.stdout().contains("standard input has no non-blank lines"),
					"batch run closes child stdin");

			Path emptyCommandScript = PathOps.createLocalTempFile("crtk-batch-run-empty-", ".txt");
			Files.writeString(emptyCommandScript, "crtk");
			TestSupport.FailureResult emptyCommand = TestSupport.runMainExpectFailure("batch", "run",
					"--input", emptyCommandScript.toString(), "--quiet");
			assertTrue(emptyCommand.stderr().contains("missing command after crtk"),
					"batch run rejects empty launcher row");
		} catch (IOException ex) {
			throw new AssertionError("batch run temp file failed", ex);
		}
	}

	/**
	 * Verifies implicit CLI outputs are derived under the shared dump directory.
	 */
	private static void testDefaultOutputPathsUseDumpDirectory() {
		assertEquals(Path.of("dump", "games.txt"),
				PathOps.deriveOutputPath(Path.of("input", "games.pgn"), ".txt"),
				"derived output uses dump directory");
		assertEquals(Path.of("dump", "workbench-game.pgn"),
				PathOps.dumpPath("workbench-game.pgn"),
				"dump path helper");
		assertEquals(Path.of("dump", "trimmed.txt"),
				PathOps.dumpPath(" trimmed.txt "),
				"dump path trims filenames");
		assertDumpPathRejected("../escape.txt");
		assertDumpPathRejected("nested/escape.txt");
		assertDumpPathRejected("nested\\escape.txt");
		assertDumpPathRejected("/tmp/escape.txt");
		assertDumpPathRejected(".");
		assertDumpPathRejected("..");
	}

	/**
	 * Verifies unsafe dump filenames are rejected before they can escape dump/.
	 *
	 * @param filename filename candidate
	 */
	private static void assertDumpPathRejected(String filename) {
		try {
			PathOps.dumpPath(filename);
		} catch (IllegalArgumentException expected) {
			return;
		}
		throw new AssertionError("dump path rejected " + filename + ": expected IllegalArgumentException");
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
	 * Verifies {@code fen render --format svg --accent} tints the vector board.
	 */
	private static void testFenRenderSvgAccent() {
		try {
			Path output = PathOps.createLocalTempFile("crtk-render-accent-", ".svg");
			String stdout = TestSupport.runMain(
					"fen",
					"render",
					FEN_OPTION,
					SIMPLE_FEN,
					"--output",
					output.toString(),
					FORMAT_OPTION,
					"svg",
					"--accent",
					"#4ab66f");
			String svg = Files.readString(output);
			assertTrue(stdout.contains("Saved board SVG:"), "fen render svg accent reports SVG output");
			assertTrue(svg.contains("fill=\"#e4f4e9\""), "accent SVG light square fill");
			assertTrue(svg.contains("fill=\"#c9e9d4\""), "accent SVG dark square fill");
			assertTrue(svg.contains("fill=\"#aedebe\""), "accent SVG grid fill");
			assertTrue(svg.contains("fill=\"#347f4e\""), "accent SVG frame fill");
			assertFalse(svg.contains("fill=\"#e5e5e5\""), "accent SVG omits default light square fill");
			assertFalse(svg.contains("fill=\"#cccccc\""), "accent SVG omits default dark square fill");
		} catch (IOException ex) {
			throw new AssertionError("fen render SVG accent test failed", ex);
		}
	}

	/**
	 * Verifies {@code fen relations} renders the OTIS tactical-incidence channels
	 * deterministically (svg arrows, montage png) and that the underlying weightless
	 * incidence builder is stable.
	 */
	private static void testFenRelations() {
		try {
			// The knight-attack channel from the start position is deterministic:
			// four knights, three on-board jumps each, 12 directed edges.
			Path svg = PathOps.createLocalTempFile("crtk-relations-", ".svg");
			String stdout = TestSupport.runMain("fen", "relations", "--startpos",
					"--channel", "knight_attack", "--output", svg.toString());
			assertTrue(stdout.contains("12 edges"), "knight_attack channel has 12 edges from the start");
			assertTrue(Files.readString(svg).contains("<polygon"), "relations svg draws arrow polygons");

			// No absolute pins exist in the start position.
			Path pin = PathOps.createLocalTempFile("crtk-relations-pin-", ".svg");
			String pinOut = TestSupport.runMain("fen", "relations", "--startpos",
					"--channel", "king_ray_pin_candidate", "--output", pin.toString());
			assertTrue(pinOut.contains("0 edges"), "no pin candidates in the start position");

			// The montage writes a non-empty raster.
			Path montage = PathOps.createLocalTempFile("crtk-relations-montage-", ".png");
			String montageOut = TestSupport.runMain("fen", "relations", "--startpos",
					"--montage", "--output", montage.toString());
			assertTrue(montageOut.contains("Saved relation montage"), "montage reports output");
			assertTrue(Files.size(montage) > 0L, "montage png is non-empty");

			// Shared fen-render features apply: accent, drop shadow, coordinates,
			// comma-separated manual arrows/circles, special arrows.
			Path styled = PathOps.createLocalTempFile("crtk-relations-styled-", ".png");
			String styledOut = TestSupport.runMain("fen", "relations", "--startpos",
					"--channel", "knight_attack", "--accent", "#b58863", "--drop-shadow",
					"--coordinates-outside", "--arrows", "e2e4,d2d4", "--circles", "e4,d5",
					"--special-arrows", "--output", styled.toString());
			assertTrue(styledOut.contains("Saved relation graph"), "styled render reports output");
			assertTrue(Files.size(styled) > 0L, "styled relation png is non-empty");

			// The shared overlay path now splits comma lists for fen render too.
			Path renderArrows = PathOps.createLocalTempFile("crtk-render-arrows-", ".png");
			String renderOut = TestSupport.runMain("fen", "render", "--startpos",
					"--arrows", "e2e4,d2d4", "--output", renderArrows.toString());
			assertTrue(renderOut.contains("Saved board image"), "fen render accepts comma-separated arrows");

			// The builder is weightless, deterministic, and matches the network input.
			Position start = new Position("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
			long knightEdges = Model.incidenceEdges(start).stream().filter(e -> e.channel() == 9).count();
			assertEquals(12, (int) knightEdges, "Model.incidenceEdges knight_attack count");
			long pinEdges = Model.incidenceEdges(start).stream().filter(e -> e.channel() == 11).count();
			assertEquals(0, (int) pinEdges, "Model.incidenceEdges pin count from start");

			// The pawn channel is forward-oriented: the white e2 pawn (index 52)
			// attacks d3/f3 (indices 43/45), NOT the backward d1/f1 (59/61). The Java
			// encoder and the native GPU encoder share this; OtisBackendRegressionTest
			// guards their parity.
			long e2Forward = Model.incidenceEdges(start).stream()
					.filter(e -> e.channel() == 10 && e.from() == 52 && (e.to() == 43 || e.to() == 45)).count();
			long e2Backward = Model.incidenceEdges(start).stream()
					.filter(e -> e.channel() == 10 && e.from() == 52 && (e.to() == 59 || e.to() == 61)).count();
			assertEquals(2, (int) e2Forward, "white e2 pawn attacks forward (d3/f3)");
			assertEquals(0, (int) e2Backward, "white e2 pawn does not attack backward (d1/f1)");
		} catch (IOException ex) {
			throw new AssertionError("fen relations test failed", ex);
		}
	}

	/**
	 * Verifies {@code gen fens} routes to filtered FEN generation.
	 */
	private static void testFilteredFenGenerationShortcut() {
		try {
			Path dir = PathOps.createLocalTempDirectory("crtk-gen-fens-filter-");
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
	 * Verifies {@code fen tags --pgn <file> --analyze-game} emits whole-game
	 * analysis JSON (per-ply move effects, line tactics, and a game summary),
	 * grounded by replay. Uses a Scholar's-mate game so the terminal cause is a
	 * named checkmate pattern.
	 */
	private static void testFenTagsAnalyzeGame() {
		try {
			Path pgn = PathOps.createLocalTempFile("crtk-analyze-game-", ".pgn");
			Files.writeString(pgn, "[Event \"t\"]" + System.lineSeparator()
					+ "[Result \"1-0\"]" + System.lineSeparator() + System.lineSeparator()
					+ "1. e4 e5 2. Bc4 Nc6 3. Qh5 Nf6 4. Qxf7# 1-0" + System.lineSeparator());
			String output = TestSupport.runMain("fen", "tags", "--pgn", pgn.toString(),
					"--analyze-game");
			assertTrue(output.contains("\"game_index\":0"), "analyze-game emits game_index");
			assertTrue(output.contains("GAME: result_cause=checkmate pattern=scholars_mate"),
					"analyze-game emits grounded checkmate result cause");
			assertTrue(output.contains("MOVE_EFFECT: san=Qxf7# type=checkmate"),
					"analyze-game emits per-ply move effects");
			assertTrue(output.contains("\"sharedTactics\":"), "analyze-game JSON carries sharedTactics");

			TestSupport.FailureResult conflict = TestSupport.runMainExpectFailure(
					"fen", "tags", FEN_OPTION, SIMPLE_FEN, "--analyze-game");
			assertEquals(2, conflict.exitCode(), "analyze-game without --pgn exit code");
			assertTrue(conflict.stderr().contains("--analyze-game requires --pgn"),
					"analyze-game requires pgn message");
		} catch (IOException ex) {
			throw new AssertionError("analyze-game temp file failed", ex);
		}
	}

	/**
	 * Verifies puzzle tag analysis flags reject contradictory selectors.
	 */
	private static void testPuzzleTagsAnalyzeFlagConflict() {
		TestSupport.FailureResult conflict = TestSupport.runMainExpectFailure(
				"puzzle",
				"tags",
				FEN_OPTION,
				SIMPLE_FEN,
				"--analyze",
				"--no-analyze");
		assertEquals(2, conflict.exitCode(), "puzzle tags analyze/no-analyze conflict exit code");
		assertTrue(conflict.stderr().contains("only one of --analyze or --no-analyze"),
				"puzzle tags analyze/no-analyze conflict message");

		TestSupport.FailureResult textConflict = TestSupport.runMainExpectFailure(
				"puzzle",
				"text",
				FEN_OPTION,
				SIMPLE_FEN,
				"--analyze",
				"--no-analyze");
		assertEquals(2, textConflict.exitCode(), "puzzle text analyze/no-analyze conflict exit code");
		assertTrue(textConflict.stderr().contains("only one of --analyze or --no-analyze"),
				"puzzle text analyze/no-analyze conflict message");
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
	 * Verifies deterministic commands expose JSON/JSONL output for automation.
	 */
	private static void testMachineReadableFenAndMoveCommands() {
		String validated = TestSupport.runMain("fen", "validate", "--json", FEN_OPTION, SIMPLE_FEN).strip();
		assertTrue(validated.startsWith("{\"valid\":true"), "fen validate --json object");
		assertTrue(validated.contains("\"fen\":\"" + SIMPLE_FEN + "\""), "fen validate --json fen field");

		String normalized = TestSupport.runMain("fen", "normalize", "--jsonl", SIMPLE_FEN).strip();
		assertTrue(normalized.startsWith("{\"input\":"), "fen normalize --jsonl object");
		assertTrue(normalized.contains("\"fen\":\"" + SIMPLE_FEN + "\""), "fen normalize --jsonl fen field");

		String moves = TestSupport.runMain("move", "list", "--json", FEN_OPTION, SIMPLE_FEN).strip();
		assertTrue(moves.startsWith("[{\"uci\":"), "move list --json array");
		assertTrue(moves.contains("\"san\":\"Ka2\""), "move list --json san field");

		String sanOnly = TestSupport.runMain("move", "list", "--fields", "san", FEN_OPTION, SIMPLE_FEN);
		assertTrue(sanOnly.contains("Ka2"), "move list --fields san output");
		assertFalse(sanOnly.contains("\t"), "move list --fields san omits tab");

		String toSan = TestSupport.runMain("move", "to-san", "--json", "--startpos", "e2e4").strip();
		assertTrue(toSan.contains("\"input\":\"e2e4\""), "move to-san --json input field");
		assertTrue(toSan.contains("\"san\":\"e4\""), "move to-san --json san field");

		String after = TestSupport.runMain("move", "after", "--json", "--startpos", "e2e4").strip();
		assertTrue(after.contains("\"move\":\"e2e4\""), "move after --json move field");
		assertTrue(after.contains("\"result\":\"rnbqkbnr/pppppppp/8/8/4P3"),
				"move after --json result field");

		String play = TestSupport.runMain("move", "play", "--json", "--startpos", "e4", "e5", "--intermediate")
				.strip();
		assertTrue(play.contains("\"moves\":[\"e4\",\"e5\"]"), "move play --json moves field");
		assertTrue(play.contains("\"intermediate\":["), "move play --json intermediate field");
	}

	/**
	 * Verifies FEN and move command failures return structured exit codes without
	 * terminating the in-process test JVM.
	 */
	private static void testStructuredFenAndMoveFailures() {
		TestSupport.FailureResult badFen = TestSupport.runMainExpectFailure("fen", "normalize", "not a fen");
		assertEquals(3, badFen.exitCode(), "fen normalize invalid FEN exit code");
		assertTrue(badFen.stderr().contains("Invalid FEN"), "fen normalize invalid FEN message");
		assertFalse(badFen.stderr().contains("Exception"), "fen normalize invalid FEN hides stack by default");

		TestSupport.FailureResult missingPrint = TestSupport.runMainExpectFailure("fen", "print");
		assertEquals(2, missingPrint.exitCode(), "fen print missing FEN exit code");
		assertTrue(missingPrint.stderr().contains("print requires a FEN"), "fen print missing FEN message");

		TestSupport.FailureResult badPrint = TestSupport.runMainExpectFailure("fen", "print",
				FEN_OPTION, "not-a-fen");
		assertEquals(3, badPrint.exitCode(), "fen print invalid FEN exit code");
		assertTrue(badPrint.stderr().contains("invalid FEN"), "fen print invalid FEN message");
		assertFalse(badPrint.stderr().contains("Exception"), "fen print invalid FEN hides stack by default");

		TestSupport.FailureResult missingDisplay = TestSupport.runMainExpectFailure("fen", "display");
		assertEquals(2, missingDisplay.exitCode(), "fen display missing FEN exit code");
		assertTrue(missingDisplay.stderr().contains("display requires a FEN"), "fen display missing FEN message");

		TestSupport.FailureResult badDisplay = TestSupport.runMainExpectFailure("fen", "display",
				FEN_OPTION, "not-a-fen");
		assertEquals(3, badDisplay.exitCode(), "fen display invalid FEN exit code");
		assertTrue(badDisplay.stderr().contains("invalid display input"), "fen display invalid FEN message");
		assertFalse(badDisplay.stderr().contains("Exception"), "fen display invalid FEN hides stack by default");

		TestSupport.FailureResult missingRenderFen = TestSupport.runMainExpectFailure("fen", "render",
				"--output", "unused.png");
		assertEquals(2, missingRenderFen.exitCode(), "fen render missing FEN exit code");
		assertTrue(missingRenderFen.stderr().contains("render requires a FEN"),
				"fen render missing FEN message");

		TestSupport.FailureResult missingRenderOutput = TestSupport.runMainExpectFailure("fen", "render",
				FEN_OPTION, SIMPLE_FEN);
		assertEquals(2, missingRenderOutput.exitCode(), "fen render missing output exit code");
		assertTrue(missingRenderOutput.stderr().contains("render requires --output"),
				"fen render missing output message");

		TestSupport.FailureResult badRender = TestSupport.runMainExpectFailure("fen", "render",
				FEN_OPTION, "not-a-fen", "--output", "unused.png");
		assertEquals(3, badRender.exitCode(), "fen render invalid FEN exit code");
		assertTrue(badRender.stderr().contains("invalid render input"), "fen render invalid FEN message");
		assertFalse(badRender.stderr().contains("Exception"), "fen render invalid FEN hides stack by default");

		TestSupport.FailureResult adjacentFenDigits = TestSupport.runMainExpectFailure(
				"fen",
				"validate",
				FEN_OPTION,
				"11111111/8/8/8/8/8/8/K6k w - - 0 1");
		assertEquals(3, adjacentFenDigits.exitCode(), "fen validate adjacent digits exit code");
		assertTrue(adjacentFenDigits.stderr().contains("adjacent empty-square counts"),
				"fen validate adjacent digits message");

		TestSupport.FailureResult badFormat = TestSupport.runMainExpectFailure(
				"move",
				"list",
				FORMAT_OPTION,
				"json",
				FEN_OPTION,
				SIMPLE_FEN);
		assertEquals(2, badFormat.exitCode(), "move list unsupported format exit code");
		assertTrue(badFormat.stderr().contains("unsupported --format value"), "move list unsupported format message");

		TestSupport.FailureResult jsonConflict = TestSupport.runMainExpectFailure(
				"move",
				"list",
				"--json",
				"--jsonl",
				FEN_OPTION,
				SIMPLE_FEN);
		assertEquals(2, jsonConflict.exitCode(), "move list json/jsonl conflict exit code");
		assertTrue(jsonConflict.stderr().contains("use either --json or --jsonl"),
				"move list json/jsonl conflict message");
	}

	/**
	 * Verifies older CLI implementations return structured failures instead of
	 * terminating the in-process caller.
	 */
	private static void testStructuredLegacyCommandFailures() {
		TestSupport.FailureResult missingTagsFen = TestSupport.runMainExpectFailure("fen", "tags");
		assertEquals(2, missingTagsFen.exitCode(), "fen tags missing FEN exit code");
		assertTrue(missingTagsFen.stderr().contains("tags requires a FEN"), "fen tags missing FEN message");

		TestSupport.FailureResult tagsSourceConflict = TestSupport.runMainExpectFailure("fen", "tags",
				"--pgn", "missing.pgn", FEN_OPTION, SIMPLE_FEN);
		assertEquals(2, tagsSourceConflict.exitCode(), "fen tags source conflict exit code");
		assertTrue(tagsSourceConflict.stderr().contains("provide either --pgn or a FEN input"),
				"fen tags source conflict message");

		TestSupport.FailureResult textMissingFen = TestSupport.runMainExpectFailure("fen", "text",
				"--model", "missing-model.bin");
		assertEquals(2, textMissingFen.exitCode(), "fen text missing FEN exit code");
		assertTrue(textMissingFen.stderr().contains("no valid positions provided"),
				"fen text missing FEN message");

		TestSupport.FailureResult puzzleTagsBadFen = TestSupport.runMainExpectFailure("puzzle", "tags",
				FEN_OPTION, "not-a-fen");
		assertEquals(2, puzzleTagsBadFen.exitCode(), "puzzle tags invalid FEN exit code");
		assertTrue(puzzleTagsBadFen.stderr().contains("invalid FEN skipped"),
				"puzzle tags invalid FEN message");

		TestSupport.FailureResult puzzleTextMissingFen = TestSupport.runMainExpectFailure("puzzle", "text",
				"--model", "missing-model.bin");
		assertEquals(2, puzzleTextMissingFen.exitCode(), "puzzle text missing FEN exit code");
		assertTrue(puzzleTextMissingFen.stderr().contains("puzzle text requires --fen"),
				"puzzle text missing FEN message");

		try {
			Path outputBlocker = PathOps.createLocalTempFile("crtk-gen-fens-blocker-", ".txt");
			TestSupport.FailureResult genOutputConflict = TestSupport.runMainExpectFailure(
					"gen",
					"fens",
					"--output",
					outputBlocker.toString(),
					"--files",
					"1",
					"--per-file",
					"1",
					"--chess960-files",
					"0",
					"--max-attempts",
					"1");
			assertEquals(2, genOutputConflict.exitCode(), "gen fens output-file conflict exit code");
			assertTrue(genOutputConflict.stderr().contains("failed to create output directory"),
					"gen fens output-file conflict message");

			Path missingMineInput = PathOps.createLocalTempDirectory("crtk-mine-missing-")
					.resolve("missing-records.json");
			TestSupport.FailureResult mineMissingInput = TestSupport.runMainExpectFailure(
					"puzzle",
					"mine",
					"--input",
					missingMineInput.toString(),
					"--output",
					"unused-mine-output.json");
			assertEquals(2, mineMissingInput.exitCode(), "puzzle mine missing input exit code");
			assertTrue(mineMissingInput.stderr().contains("Failed to load seed positions"),
					"puzzle mine missing input message");
		} catch (IOException ex) {
			throw new AssertionError("structured legacy failure temp path failed", ex);
		}
	}

	/**
	 * Verifies release metadata is available from the CLI.
	 */
	private static void testVersionCommand() {
		String text = TestSupport.runMain("version").strip();
		assertTrue(text.startsWith("crtk "), "version text output");
		assertEquals("crtk " + VersionCommand.VERSION, text, "version text matches command constant");

		String json = TestSupport.runMain("version", "--json").strip();
		assertTrue(json.contains("\"name\":\"ChessRTK\""), "version --json name");
		assertTrue(json.contains("\"launcher\":\"crtk\""), "version --json launcher");
		assertEquals(VersionCommand.VERSION, Json.parseStringField(json, "version"),
				"version --json matches command constant");

		try {
			String packageJson = Files.readString(Path.of("package.json"));
			assertEquals(VersionCommand.VERSION, Json.parseStringField(packageJson, "version"),
					"package.json version matches command constant");
		} catch (IOException ex) {
			throw new AssertionError("package.json version check failed", ex);
		}
	}

	/**
	 * Verifies diagnostics expose stable machine-readable objects without changing exit codes.
	 */
	private static void testDiagnosticJsonCommands() {
		TestSupport.RunResult doctorText = TestSupport.runMainAny("doctor");
		TestSupport.RunResult doctorJson = TestSupport.runMainAny("doctor", "--json");
		assertEquals(doctorText.exitCode(), doctorJson.exitCode(), "doctor --json exit code matches text");

		LinkedHashMap<String, JsonValue> doctor = JsonParser.parse(doctorJson.stdout().strip()).asObject();
		assertEquals("crtk.doctor.v1", stringField(doctor, "schema"), "doctor --json schema");
		String status = stringField(doctor, "status");
		assertValidDoctorStatus(status, "doctor --json status");
		assertEquals(doctorStatus(doctorText.stdout()), status, "doctor --json status matches text");
		assertEquals(System.getProperty("java.version"), stringField(doctor, "java"), "doctor --json java");
		assertFalse(stringField(doctor, "config").isBlank(), "doctor --json config path");
		assertFalse(stringField(doctor, "protocol").isBlank(), "doctor --json protocol path");
		assertTrue(longField(doctor, "engineInstances") >= 1L, "doctor --json engine instances");
		assertFalse(stringField(doctor, "output").isBlank(), "doctor --json output");
		assertNotNull(doctor.get("warnings").asArray(), "doctor --json warnings array");
		assertNotNull(doctor.get("errors").asArray(), "doctor --json errors array");
		assertTrue(doctor.get("nativeBackends").asArray().size() >= 12, "doctor --json native backend rows");

		TestSupport.RunResult strictText = TestSupport.runMainAny("doctor", "--strict");
		TestSupport.RunResult strictJson = TestSupport.runMainAny("doctor", "--strict", "--json");
		assertEquals(strictText.exitCode(), strictJson.exitCode(), "doctor --strict --json exit code matches text");
		LinkedHashMap<String, JsonValue> strict = JsonParser.parse(strictJson.stdout().strip()).asObject();
		assertEquals(doctorStatus(strictText.stdout()), stringField(strict, "status"),
				"doctor --strict --json status matches text");

		LinkedHashMap<String, JsonValue> config = JsonParser.parse(
				TestSupport.runMain("config", "show", "--json").strip()).asObject();
		assertEquals("crtk.config.v1", stringField(config, "schema"), "config show --json schema");
		assertFalse(stringField(config, "config").isBlank(), "config show --json config path");
		assertFalse(stringField(config, "protocol").isBlank(), "config show --json protocol path");
		assertFalse(stringField(config, "output").isBlank(), "config show --json output");
		assertTrue(longField(config, "engineInstances") >= 1L, "config show --json engine instances");
		assertTrue(longField(config, "maxNodes") >= 1L, "config show --json max nodes");
		assertTrue(longField(config, "maxDurationMs") >= 1L, "config show --json max duration");
		assertTrue(longField(config, "puzzleAnalysisCache") >= 1L, "config show --json puzzle cache");
		assertFalse(stringField(config, "puzzleQuality").isBlank(), "config show --json puzzle quality");
	}

	/**
	 * Extracts the doctor status from text mode.
	 *
	 * @param stdout command standard output
	 * @return doctor status
	 */
	private static String doctorStatus(String stdout) {
		for (String line : stdout.split("\\R")) {
			if (line.startsWith("doctor: ")) {
				return line.substring("doctor: ".length()).trim();
			}
		}
		throw new AssertionError("doctor status line missing: " + stdout);
	}

	/**
	 * Verifies a doctor status is part of the public status vocabulary.
	 *
	 * @param status status to check
	 * @param label assertion label
	 */
	private static void assertValidDoctorStatus(String status, String label) {
		assertTrue(List.of("ok", "ok-with-warnings", "failed", "failed-strict").contains(status), label);
	}

	/**
	 * Reads a JSON object string field.
	 *
	 * @param object parsed JSON object
	 * @param field field name
	 * @return string field value
	 */
	private static String stringField(LinkedHashMap<String, JsonValue> object, String field) {
		JsonValue value = object.get(field);
		assertNotNull(value, "JSON field present: " + field);
		return value.asString();
	}

	/**
	 * Reads a JSON object integer field as a long.
	 *
	 * @param object parsed JSON object
	 * @param field field name
	 * @return numeric field value
	 */
	private static long longField(LinkedHashMap<String, JsonValue> object, String field) {
		JsonValue value = object.get(field);
		assertNotNull(value, "JSON field present: " + field);
		assertTrue(value.numberIsInteger(), "JSON integer field: " + field);
		return (long) value.asNumber();
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

		String gpuDivide = TestSupport.runMain(ENGINE_COMMAND, PERFT_COMMAND, DEPTH_OPTION, "1", "--gpu", "--divide");
		assertTrue(gpuDivide.contains("Perft divide (depth 1)"), "engine perft gpu divide title");
		assertTrue(gpuDivide.matches("(?s).*Total\\s+20\\s+0\\s+0\\s+0\\s+0\\s+0\\s+0.*"),
				"engine perft gpu divide total");

		String detailDivide = TestSupport.runMain(ENGINE_COMMAND, PERFT_COMMAND, DEPTH_OPTION, "1", "--divide",
				FORMAT_OPTION, "detail");
		assertTrue(detailDivide.contains("a2a3: nodes=1"), "engine perft detail divide row");
		assertTrue(detailDivide.contains("total:"), "engine perft detail divide total");

		String stockfish = TestSupport.runMain(ENGINE_COMMAND, PERFT_COMMAND, DEPTH_OPTION, "1", FORMAT_OPTION, "stockfish",
				"--threads", "2");
		assertTrue(stockfish.contains("a2a3: 1"), "engine perft stockfish divide row");
		assertTrue(stockfish.contains("Nodes searched: 20"), "engine perft stockfish total");
		assertFalse(stockfish.contains("FEN:"), "engine perft stockfish omits crtk heading");

		String gpuStockfish = TestSupport.runMain(ENGINE_COMMAND, PERFT_COMMAND, DEPTH_OPTION, "1", "--gpu",
				FORMAT_OPTION, "stockfish");
		assertTrue(gpuStockfish.contains("a2a3: 1"), "engine perft gpu stockfish divide row");
		assertTrue(gpuStockfish.contains("Nodes searched: 20"), "engine perft gpu stockfish total");
		assertFalse(gpuStockfish.contains("FEN:"), "engine perft gpu stockfish omits crtk heading");

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
	 * Verifies {@code engine eval} exposes concrete evaluator modes and rejects
	 * the old ambiguous "none" value.
	 */
	private static void testEngineEvalEvaluatorModes() {
		String classical = TestSupport.runMain(ENGINE_COMMAND, "eval", "--evaluator", "classical",
				FEN_OPTION, SIMPLE_FEN);
		assertTrue(classical.contains("backend: classical"), "engine eval --evaluator classical backend");

		if (Files.exists(chess.nn.otis.Model.DEFAULT_WEIGHTS)) {
			// Force the CPU backend so the asserted label is deterministic even
			// when an optional OTIS GPU backend (CUDA/ROCm/oneAPI) is present.
			String previousOtisBackend = System.getProperty("crtk.otis.backend");
			System.setProperty("crtk.otis.backend", "cpu");
			try {
				String otis = TestSupport.runMain(ENGINE_COMMAND, "eval", "--otis", FEN_OPTION, SIMPLE_FEN);
				String params = chess.nn.otis.Model.formatParameterCount(chess.nn.otis.Model.DEFAULT_PARAMETER_COUNT)
						+ " params";
				assertTrue(otis.contains("model: otis(java-i249-random-v2, " + params + ")"),
						"engine eval --otis model metadata");
			} finally {
				if (previousOtisBackend == null) {
					System.clearProperty("crtk.otis.backend");
				} else {
					System.setProperty("crtk.otis.backend", previousOtisBackend);
				}
			}
		}

		FailureResult none = TestSupport.runMainExpectFailure(ENGINE_COMMAND, "eval", "--evaluator", "none",
				FEN_OPTION, SIMPLE_FEN);
		assertEquals(2, none.exitCode(), "engine eval rejects none");
		assertTrue(none.stderr().contains("expected auto, lc0, otis, or classical"),
				"engine eval none message");

		FailureResult conflict = TestSupport.runMainExpectFailure(ENGINE_COMMAND, "eval", "--evaluator", "auto",
				"--lc0", FEN_OPTION, SIMPLE_FEN);
		assertEquals(2, conflict.exitCode(), "engine eval rejects mixed evaluator selectors");
		assertTrue(conflict.stderr().contains("use either --evaluator or evaluator shortcut flags"),
				"engine eval mixed selector message");

		FailureResult weights = TestSupport.runMainExpectFailure(ENGINE_COMMAND, "eval", "--evaluator", "classical",
				"--weights", "models/missing.bin", FEN_OPTION, SIMPLE_FEN);
		assertEquals(2, weights.exitCode(), "engine eval rejects weights for classical");
		assertTrue(weights.stderr().contains("--weights requires --evaluator auto, lc0, or otis"),
				"engine eval classical weights message");
	}

	/**
	 * Verifies the high-value batch/research helpers that do not require an
	 * external engine process.
	 */
	private static void testHighValueResearchCommands() {
		String benchmark = TestSupport.runMain(
				ENGINE_COMMAND,
				"benchmark",
				DEPTH_OPTION,
				"1",
				"--iterations",
				"1",
				"--json");
		assertTrue(benchmark.contains("\"nodes\":20"), "engine benchmark --json node count");
		assertTrue(benchmark.contains("\"total_nodes\":20"), "engine benchmark --json total nodes");

		String diff = TestSupport.runMain(
				"position",
				"diff",
				FEN_OPTION,
				SIMPLE_FEN,
				"--other",
				SIMPLE_AFTER_KA2_FEN);
		assertTrue(diff.contains("side-to-move: w -> b"), "position diff side-to-move output");
		assertTrue(diff.contains("a1: K -> ."), "position diff source square output");
		assertTrue(diff.contains("a2: . -> K"), "position diff target square output");

		String diffJson = TestSupport.runMain(
				"position",
				"diff",
				"--json",
				FEN_OPTION,
				SIMPLE_FEN,
				"--other",
				SIMPLE_AFTER_KA2_FEN);
		assertTrue(diffJson.contains("\"equal\":false"), "position diff --json equal field");
		assertTrue(diffJson.contains("\"state\":["), "position diff --json state field");
		assertTrue(diffJson.contains("\"board\":["), "position diff --json board field");

		try {
			Path suite = PathOps.createLocalTempFile("crtk-perft-suite-", ".txt");
			Files.writeString(suite, "start\t1\t" + START_FEN + "\t20\n");
			String customSuite = TestSupport.runMain(
					ENGINE_COMMAND,
					"perft-suite",
					"--suite",
					suite.toString(),
					"--threads",
					"1");
			assertTrue(customSuite.contains("Perft validation (depth 1)"),
					"engine perft-suite --suite heading");
			assertTrue(customSuite.contains("match=true"), "engine perft-suite --suite summary");
			String customGpuSuite = TestSupport.runMain(
					ENGINE_COMMAND,
					"perft-suite",
					"--suite",
					suite.toString(),
					"--gpu",
					"--split",
					"0");
			assertTrue(customGpuSuite.contains("Perft validation (depth 1)"),
					"engine perft-suite --suite --gpu heading");
			assertTrue(customGpuSuite.contains("match=true"), "engine perft-suite --suite --gpu summary");
		} catch (IOException ex) {
			throw new AssertionError("custom perft suite test failed", ex);
		}
	}

	/**
	 * Verifies deterministic classical position-description text at each detail
	 * level.
	 */
	private static void testPositionDescribeClassicalGoldenOutput() {
		assertEquals(
				"White is to move in the opening, with the material level and little to choose between the two sides.",
				TestSupport.runMain("position", "describe", FEN_OPTION, START_FEN, "--detail", "brief").strip(),
				"position describe brief golden");
		assertEquals(
				TestSupport.runMain("position", "describe", FEN_OPTION, START_FEN, "--detail", "brief").strip(),
				TestSupport.runMain("position", "describe", FEN_OPTION, START_FEN, "--audience", "beginner")
						.strip(),
				"position describe beginner audience maps to brief");
		assertEquals(
				"White is to move in the opening, and the position is dead level. The static evaluation of "
						+ "+0.2 for White and a WDL of 249/537/214 amount to next to nothing; this is as balanced "
						+ "as a position gets. The natural course is to develop with Nc3, with Nf3 and d4 as "
						+ "alternatives.",
				TestSupport.runMain("position", "describe", FEN_OPTION, START_FEN, "--detail", "normal").strip(),
				"position describe normal golden");
		assertEquals(
				TestSupport.runMain("position", "describe", FEN_OPTION, START_FEN, "--detail", "full",
						"--budget", "1").strip(),
				TestSupport.runMain("position", "describe", FEN_OPTION, START_FEN, "--audience", "beginner",
						"--detail", "full", "--budget", "1").strip(),
				"position describe explicit detail overrides audience preset");
		assertEquals(
				"White is to move in a bare king-and-king endgame. The material is level and, with too little "
						+ "left to force a checkmate, the result is not in doubt. The evaluation reads a nominal "
						+ "+0.2 for White, but the WDL of 0/1000/0 tells the true story: a dead draw in which "
						+ "neither side can make progress. The king can only shuffle - Kb2, Ka2 and Kb1 - and "
						+ "none of it matters. The point has long since been split.",
				TestSupport.runMain("position", "describe", FEN_OPTION, SIMPLE_FEN, "--detail", "full",
						"--budget", "2").strip(),
				"position describe full golden");
	}

	/**
	 * Verifies deterministic why/plan sentences are grounded in extracted tags.
	 */
	private static void testPositionDescribeStrategicPlans() {
		String passedPawn = "8/8/8/4P3/8/8/8/4K2k w - - 0 1";
		String first = TestSupport.runMain("position", "describe", FEN_OPTION, passedPawn, "--detail", "normal")
				.strip();
		assertTrue(first.contains("Because White has a passed pawn"),
				"position describe passed-pawn why sentence");
		assertTrue(first.contains("the plan is to push it only when it stays supported"),
				"position describe passed-pawn plan sentence");
		assertTrue(PositionDescriptionVerifier.verify(PositionDescriptionInput.fromFen(passedPawn), first).grounded(),
				"position describe passed-pawn plan remains verifier-grounded");
		String repeat = TestSupport.runMain("position", "describe", FEN_OPTION, passedPawn, "--detail", "normal")
				.strip();
		assertEquals(first, repeat, "position describe strategic plan is deterministic");

		String closedCenter = "4k3/8/8/3p4/3P4/8/8/4K3 w - - 0 1";
		String full = TestSupport.runMain("position", "describe", FEN_OPTION, closedCenter, "--detail", "full")
				.strip();
		assertTrue(full.contains("Because the center is closed"),
				"position describe closed-center why sentence");
		assertTrue(full.contains("improve pieces before opening lines"),
				"position describe closed-center plan sentence");
	}

	/**
	 * Verifies batch JSONL rows, output file writing, and the retired T5 selector.
	 */
	private static void testPositionDescribeJsonlAndT5Failure() {
		try {
			Path input = PathOps.createLocalTempFile("crtk-position-describe-", ".fen");
			Files.writeString(input, START_FEN + System.lineSeparator() + SIMPLE_FEN + System.lineSeparator());
			String jsonl = TestSupport.runMain("position", "describe", "--input", input.toString(),
					"--format", "jsonl", "--detail", "brief");
			String[] lines = jsonl.strip().split("\\R");
			assertEquals(2, lines.length, "position describe jsonl row count");
			assertTrue(lines[0].contains("\"index\":1"), "position describe jsonl first index");
			assertTrue(lines[1].contains("\"index\":2"), "position describe jsonl second index");
			assertTrue(lines[0].contains("\"input\":"), "position describe jsonl includes structured input");
			assertTrue(lines[0].contains("\"grounding\":{\"grounded\":true,\"violations\":[]}"),
					"position describe jsonl includes verifier verdict");

			String factsJsonl = TestSupport.runMain("position", "describe", "--input", input.toString(),
					FORMAT_OPTION, "jsonl", "--detail", "brief", "--facts-only");
			String[] factsLines = factsJsonl.strip().split("\\R");
			assertEquals(2, factsLines.length, "position describe facts-only row count");
			assertTrue(factsLines[0].contains("\"schema\":\"crtk.position_description.facts.v1\""),
					"position describe facts-only schema");
			assertTrue(factsLines[0].contains("\"facts\":{\"fen\":"),
					"position describe facts-only structured facts");
			assertTrue(factsLines[0].contains("\"grounding\":{\"grounded\":true,\"violations\":[]}"),
					"position describe facts-only grounding verdict");
			assertFalse(factsLines[0].contains("\"description\":"),
					"position describe facts-only omits prose description");
			assertFalse(factsLines[0].contains("\"prompt\":"),
					"position describe facts-only omits training prompt");
			assertFalse(factsLines[0].contains("\"target\":"),
					"position describe facts-only omits training target");

			String mlJson = TestSupport.runMain("position", "describe", FEN_OPTION, START_FEN,
					"--audience", "ml");
			assertTrue(mlJson.contains("\"schema\":\"crtk.position_description.facts.v1\""),
					"position describe ml audience defaults to facts schema");
			assertTrue(mlJson.contains("\"audience\":\"ml\""), "position describe ml audience is recorded");
			assertTrue(mlJson.contains("\"facts\":{\"fen\":"),
					"position describe ml audience emits facts object");
			assertFalse(mlJson.contains("\"description\":"),
					"position describe ml audience suppresses prose by default");

			String trainingJsonl = TestSupport.runMain("position", "describe", "--input", input.toString(),
					"--format", "training-jsonl", "--detail", "brief", "--budget", "2", "--max-new", "64");
			String[] trainingLines = trainingJsonl.strip().split("\\R");
			assertEquals(2, trainingLines.length, "position describe training-jsonl row count");
			assertTrue(trainingLines[0].contains("\"schema\":\"crtk.position_description.training.v1\""),
					"position describe training-jsonl schema");
			assertTrue(trainingLines[0].contains("\"prompt\":\"describe_position detail=brief max_new=64\\nfeatures:"),
					"position describe training-jsonl prompt");
			assertTrue(trainingLines[0].contains("\"target\":\"White is to move in the opening"),
					"position describe training-jsonl target");
			assertTrue(trainingLines[0].contains("\"classical_text\":\"White is to move in the opening"),
					"position describe training-jsonl classical text");
			assertTrue(trainingLines[0].contains("\"grounding\":{\"grounded\":true,\"violations\":[]}"),
					"position describe training-jsonl grounding verdict");
			assertTrue(trainingLines[0].contains("\"input\":"), "position describe training-jsonl input");
			assertTrue(trainingLines[0].contains("\"candidate_budget\":2"),
					"position describe training-jsonl candidate budget");
			assertTrue(trainingLines[0].contains("\"max_new_tokens\":64"),
					"position describe training-jsonl max_new metadata");

			Path output = PathOps.createLocalTempFile("crtk-position-describe-output-", ".txt");
			assertEquals("", TestSupport.runMain("position", "describe", FEN_OPTION, SIMPLE_FEN,
					"--output", output.toString()), "position describe --output stdout");
			assertTrue(Files.readString(output).contains("bare king-and-king endgame"),
					"position describe writes output file");
		} catch (IOException ex) {
			throw new AssertionError("position describe temp file failed", ex);
		}

		TestSupport.FailureResult factsText = TestSupport.runMainExpectFailure("position", "describe",
				FEN_OPTION, SIMPLE_FEN, "--facts-only");
		assertEquals(2, factsText.exitCode(), "position describe facts-only text exit code");
		assertTrue(factsText.stderr().contains("--facts-only requires --json or --format jsonl"),
				"position describe facts-only text message");

		TestSupport.FailureResult factsTraining = TestSupport.runMainExpectFailure("position", "describe",
				FEN_OPTION, SIMPLE_FEN, FORMAT_OPTION, "training-jsonl", "--facts-only");
		assertEquals(2, factsTraining.exitCode(), "position describe facts-only training exit code");
		assertTrue(factsTraining.stderr().contains("--facts-only is not valid with training-jsonl"),
				"position describe facts-only training message");

		TestSupport.FailureResult badAudience = TestSupport.runMainExpectFailure("position", "describe",
				FEN_OPTION, SIMPLE_FEN, "--audience", "oracle");
		assertEquals(2, badAudience.exitCode(), "position describe bad audience exit code");
		assertTrue(badAudience.stderr().contains("unsupported --audience oracle"),
				"position describe bad audience message");

		TestSupport.FailureResult t5 = TestSupport.runMainExpectFailure("position", "describe",
				FEN_OPTION, SIMPLE_FEN, "--engine", "t5");
		assertEquals(2, t5.exitCode(), "position describe retired t5 exit code");
		assertTrue(t5.stderr().contains("position describe is classical-only"),
				"position describe retired t5 message");
		assertTrue(t5.stderr().contains("fen text") && t5.stderr().contains("puzzle text"),
				"position describe retired t5 points to working runtime");
	}

	/**
	 * Verifies the opt-in engine evaluation corrects a false static verdict, stays
	 * deterministic, reports forced mate, and rejects a bad source value.
	 */
	private static void testPositionDescribeEngineEval() {
		// White is in check and a queen down on the static count, but Nxe5 captures
		// the checking queen and wins: a real search must flip the verdict.
		String tactic = "4k3/p7/8/4q3/8/5N2/P7/4K3 w - - 0 1";
		String staticText = TestSupport.runMain("position", "describe", FEN_OPTION, tactic, "--detail", "normal")
				.strip();
		assertTrue(staticText.contains("Black is winning"), "position describe static misjudges tactic");
		String engineText = TestSupport.runMain("position", "describe", FEN_OPTION, tactic, "--detail", "normal",
				"--eval", "engine", "--eval-depth", "8").strip();
		// The search flips the verdict to White's favor (clearly better or winning).
		assertTrue(engineText.contains("White is clearly better") || engineText.contains("White is winning"),
				"position describe engine corrects verdict");
		assertTrue(!engineText.contains("Black is winning"), "position describe engine drops false verdict");
		assertTrue(engineText.contains("engine evaluation"), "position describe engine eval label");
		String engineRepeat = TestSupport.runMain("position", "describe", FEN_OPTION, tactic, "--detail", "normal",
				"--eval", "engine", "--eval-depth", "8").strip();
		assertEquals(engineText, engineRepeat, "position describe engine eval is deterministic");

		// A mate in one is reported as a forced mate, and --eval-depth implies engine.
		String mateFen = "6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1";
		String mateText = TestSupport.runMain("position", "describe", FEN_OPTION, mateFen, "--detail", "brief",
				"--eval-depth", "6").strip();
		assertTrue(mateText.contains("forced mate in one"), "position describe engine forced mate");
		String mateJson = TestSupport.runMain("position", "describe", FEN_OPTION, mateFen, "--json",
				"--eval", "engine", "--eval-depth", "6");
		assertTrue(mateJson.contains("\"source\":\"engine-d"), "position describe engine eval json source");
		assertTrue(mateJson.contains("\"mate_in\":1"), "position describe engine eval json mate field");

		FailureResult bad = TestSupport.runMainExpectFailure("position", "describe", FEN_OPTION, tactic,
				"--eval", "bogus");
		assertEquals(2, bad.exitCode(), "position describe bad eval source exit code");
	}

	/**
	 * Verifies help output exposes the new grouped and helper commands.
	 */
	private static void testHelpListsNewCommands() {
		String summary = TestSupport.runMain("help");
		assertTrue(summary.contains(RECORD_COMMAND), "help lists record group");
		assertTrue(summary.contains("fen"), "help lists fen group");
		assertTrue(summary.contains("gen"), "help lists gen group");
		assertTrue(summary.contains("batch"), "help lists batch group");
		assertTrue(summary.contains("move"), "help lists move group");
		assertTrue(summary.contains(ENGINE_COMMAND), "help lists engine group");
		assertTrue(summary.contains("position"), "help lists position group");
		assertTrue(summary.contains("book"), "help lists book group");
		assertTrue(summary.contains("puzzle"), "help lists puzzle group");
		assertTrue(summary.contains("review"), "help lists review group");
		assertTrue(summary.contains("doctor"), "help lists doctor");
		assertTrue(summary.contains("version"), "help lists version");
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
		assertTrue(full.contains("batch subcommands:"), "full help lists batch subcommands");

		String batchHelp = TestSupport.runMain("help", "batch");
		assertTrue(batchHelp.contains("batch subcommands:"), "help batch subcommands");

		String batchRunHelp = TestSupport.runMain("help", "batch", "run");
		assertTrue(batchRunHelp.contains("batch run options:"), "help batch run options");
		assertTrue(batchRunHelp.contains("--keep-going"), "help batch run keep-going option");

		String positionDescribeHelp = TestSupport.runMain("help", "position", "describe");
		assertTrue(positionDescribeHelp.contains("position describe options:"),
				"help position describe options");
		assertTrue(positionDescribeHelp.contains("--audience MODE"),
				"help position describe audience option");

		String reviewHelp = TestSupport.runMain("help", "review");
		assertTrue(reviewHelp.contains("review subcommands:"), "help review subcommands");
		assertTrue(reviewHelp.contains("game"), "help review lists game command");

		String reviewGameHelp = TestSupport.runMain("help", "review", "game");
		assertTrue(reviewGameHelp.contains("review game options:"), "help review game options");
		assertTrue(reviewGameHelp.contains("--offline"), "help review game offline option");
		assertTrue(reviewGameHelp.contains("--protocol-path"), "help review game protocol option");
		assertTrue(reviewGameHelp.contains("--multipv"), "help review game multipv option");
		assertTrue(reviewGameHelp.contains("--to-study"), "help review game study option");
		assertTrue(reviewGameHelp.contains("--record-output"), "help review game record output option");

		String engineHelp = TestSupport.runMain("help", ENGINE_COMMAND);
		assertTrue(engineHelp.contains("uci-smoke"), "help engine lists uci-smoke");
		assertTrue(engineHelp.contains("analyze-batch"), "help engine lists analyze-batch");
		assertTrue(engineHelp.contains("bestmove-batch"), "help engine lists bestmove-batch");
		assertTrue(engineHelp.contains("compare"), "help engine lists compare");
		assertTrue(engineHelp.contains("benchmark"), "help engine lists benchmark");

		String enginePerft = TestSupport.runMain("help", ENGINE_COMMAND, PERFT_COMMAND);
		assertTrue(enginePerft.contains("engine perft options:"), "help engine perft options");
		assertTrue(enginePerft.contains("--startpos"), "help engine perft startpos option");
		assertTrue(enginePerft.contains("--randompos"), "help engine perft randompos option");
		assertTrue(enginePerft.contains("--format FMT"), "help engine perft format option");
		assertTrue(enginePerft.contains("--threads N"), "help engine perft threads option");
		assertTrue(enginePerft.contains("--gpu"), "help engine perft gpu option");
		assertTrue(enginePerft.contains("--split N"), "help engine perft split option");

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
		assertTrue(engineBuiltin.contains("--search alpha-beta|mcts"), "help engine builtin search option");
		assertTrue(engineBuiltin.contains("--threads N"), "help engine builtin threads option");

		String engineEval = TestSupport.runMain("help", ENGINE_COMMAND, "eval");
		assertTrue(engineEval.contains("--startpos"), "help engine eval startpos option");
		assertTrue(engineEval.contains("--randompos"), "help engine eval randompos option");
		assertTrue(engineEval.contains("--evaluator MODE"), "help engine eval evaluator option");

		String engineGauntlet = TestSupport.runMain("help", ENGINE_COMMAND, "gauntlet");
		assertTrue(engineGauntlet.contains("--eval classical|nnue|cnn|bt4|otis"),
				"help engine gauntlet neural evaluators");

		String enginePerftSuite = TestSupport.runMain("help", ENGINE_COMMAND, "perft-suite");
		assertTrue(enginePerftSuite.contains("engine perft-suite options:"),
				"help engine perft-suite options");
		assertTrue(enginePerftSuite.contains("--threads N"), "help engine perft-suite threads option");
		assertTrue(enginePerftSuite.contains("--suite PATH"), "help engine perft-suite custom suite option");
		assertTrue(enginePerftSuite.contains("--gpu"), "help engine perft-suite gpu option");
		assertTrue(enginePerftSuite.contains("--split N"), "help engine perft-suite split option");
		assertFalse(enginePerftSuite.contains("--stockfish"), "help engine perft-suite does not expose stockfish");

		String engineAnalyzeBatch = TestSupport.runMain("help", ENGINE_COMMAND, "analyze-batch");
		assertTrue(engineAnalyzeBatch.contains("engine analyze-batch options:"),
				"help engine analyze-batch options");
		assertTrue(engineAnalyzeBatch.contains("--jsonl"), "help engine analyze-batch jsonl option");

		String engineBestmoveBatch = TestSupport.runMain("help", ENGINE_COMMAND, "bestmove-batch");
		assertTrue(engineBestmoveBatch.contains("engine bestmove-batch options:"),
				"help engine bestmove-batch options");
		assertTrue(engineBestmoveBatch.contains("--stdin"), "help engine bestmove-batch stdin option");

		String engineCompare = TestSupport.runMain("help", ENGINE_COMMAND, "compare");
		assertTrue(engineCompare.contains("engine compare options:"), "help engine compare options");
		assertTrue(engineCompare.contains("--left-protocol"), "help engine compare left protocol option");

		String engineBenchmark = TestSupport.runMain("help", ENGINE_COMMAND, "benchmark");
		assertTrue(engineBenchmark.contains("engine benchmark options:"), "help engine benchmark options");
		assertTrue(engineBenchmark.contains("--iterations N"), "help engine benchmark iterations option");

		String positionHelp = TestSupport.runMain("help", "position");
		assertTrue(positionHelp.contains("position subcommands:"), "help position subcommands");

		String positionDiff = TestSupport.runMain("help", "position", "diff");
		assertTrue(positionDiff.contains("position diff options:"), "help position diff options");
		assertTrue(positionDiff.contains("--other FEN"), "help position diff other option");

		String uciSmoke = TestSupport.runMain("help", ENGINE_COMMAND, "uci-smoke");
		assertTrue(uciSmoke.contains("engine uci-smoke options:"), "help engine uci-smoke options");

		String doctor = TestSupport.runMain("help", "doctor");
		assertTrue(doctor.contains("doctor options:"), "help doctor options");

		String version = TestSupport.runMain("help", "version");
		assertTrue(version.contains("version options:"), "help version options");

		String workbench = TestSupport.runMain("workbench", "--help");
		assertTrue(workbench.contains("usage: crtk workbench [options]"), "workbench help usage");
		assertTrue(workbench.contains("workbench options:"), "workbench help options");
		assertTrue(workbench.contains("crtk workbench --fen \"<FEN>\""), "workbench help canonical example");
		assertTrue(workbench.contains("crtk gui"), "workbench help gui alias");

		String guiWorkbench = TestSupport.runMain("gui", "--help");
		assertTrue(guiWorkbench.contains("canonical command:"), "gui help canonical heading");
		assertTrue(guiWorkbench.contains("crtk workbench"), "gui help canonical command");
		assertTrue(guiWorkbench.contains("workbench options:"), "gui help uses workbench options");

		TestSupport.FailureResult removedWorkbenchAlias = TestSupport.runMainExpectFailure("gui-workbench", "--help");
		assertTrue(removedWorkbenchAlias.stderr().contains("Unknown command: gui-workbench"),
				"removed gui-workbench command");

		TestSupport.FailureResult removedWebGui = TestSupport.runMainExpectFailure("gui-web", "--help");
		assertTrue(removedWebGui.stderr().contains("Unknown command: gui-web"), "removed gui-web command");

		TestSupport.FailureResult removedNextGui = TestSupport.runMainExpectFailure("gui-next", "--help");
		assertTrue(removedNextGui.stderr().contains("Unknown command: gui-next"), "removed gui-next command");

		TestSupport.FailureResult unknownHelp = TestSupport.runMainExpectFailure("help", "nope");
		assertEquals(2, unknownHelp.exitCode(), "unknown help path exits non-zero");
		assertTrue(unknownHelp.stderr().contains("Unknown command for help: nope"),
				"unknown help path reports the typo");
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
