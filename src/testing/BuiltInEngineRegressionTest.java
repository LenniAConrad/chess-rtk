/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package testing;

import application.cli.PathOps;
import static testing.TestSupport.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import chess.classical.Wdl;
import chess.core.Move;
import chess.core.Position;
import chess.engine.Limits;
import chess.engine.Mcts;
import chess.engine.MctsUci;
import chess.engine.Result;
import chess.eval.CentipawnEvaluator;
import chess.eval.Classical;
import chess.eval.Kind;
import chess.nn.nnue.FeatureEncoder;

/**
 * Regression checks for the built-in Java engine package and CLI wrapper.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
@SuppressWarnings({"java:S1192", "java:S2187", "java:S3011", "squid:S1192"})
public final class BuiltInEngineRegressionTest {

	/**
	 * Shared top-level engine command.
	 */
	private static final String ENGINE_COMMAND = "engine";

	/**
	 * Shared built-in engine subcommand.
	 */
	private static final String BUILTIN_COMMAND = "builtin";

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
	 * Shared summary-format token.
	 */
	private static final String SUMMARY_FORMAT = "summary";

	/**
	 * Summary prefix for the reported best move.
	 */
	private static final String BEST_PREFIX = "best: ";

	/**
	 * Shared classical-evaluator token.
	 */
	private static final String CLASSICAL_EVALUATOR = "classical";

	/**
	 * Standard starting position.
	 */
	private static final String START_FEN =
			"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

	/**
	 * Simple mate-in-one position: {@code Qg7#}.
	 */
	private static final String MATE_IN_ONE_FEN =
			"7k/8/5KQ1/8/8/8/8/8 w - - 0 1";

	/**
	 * Mate-in-one where both sides only have kings and opposite-colored bishops.
	 */
	private static final String OPPOSITE_BISHOP_MATE_IN_ONE_FEN =
			"7k/5K1b/8/8/8/8/3B4/8 w - - 0 1";

	/**
	 * User-reported forced mate in four where every black interposition is
	 * captured on the back rank.
	 */
	private static final String FORCED_MATE_IN_FOUR_FEN =
			"7k/3rrrpp/8/8/8/8/PP6/1KR5 w - - 0 1";

	/**
	 * Fool's Mate pre-mate position after {@code 1.f3 e5 2.g4}.
	 */
	private static final String FOOLS_MATE_PRE_FEN =
			"rnbqkbnr/pppp1ppp/8/4p3/6P1/5P2/PPPPP2P/RNBQKBNR b KQkq - 0 2";

	/**
	 * Scholar's Mate pre-mate position after {@code 1.e4 e5 2.Qh5 Nc6
	 * 3.Bc4 Nf6}.
	 */
	private static final String SCHOLARS_MATE_PRE_FEN =
			"r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4";

	/**
	 * Legal Trap pre-mate position after {@code 1.e4 e5 2.Nf3 d6 3.Bc4 Bg4
	 * 4.Nc3 g6 5.Nxe5 Bxd1 6.Bxf7+ Ke7}.
	 */
	private static final String LEGAL_TRAP_PRE_FEN =
			"rn1q1bnr/ppp1kB1p/3p2p1/4N3/4P3/2N5/PPPP1PPP/R1BbK2R w KQ - 1 7";

	/**
	 * Laws 1888 mate-in-two composition.
	 */
	private static final String LAWS_MATE_IN_TWO_FEN =
			"5b2/1Q6/1P4R1/3rkP2/8/5R1K/5N2/6B1 w - - 0 1";

	/**
	 * Niels Hoeg 1905 promotion mate-in-three composition.
	 */
	private static final String HOEG_PROMOTION_MATE_IN_THREE_FEN =
			"8/R7/4kPP1/3ppp2/3B1P2/1K1P1P2/8/8 w - - 0 1";

	/**
	 * Bare-king legal-position smoke test.
	 */
	private static final String SIMPLE_FEN =
			"8/8/8/8/8/8/8/K6k w - - 0 1";

	/**
	 * Legal position where White has an extra queen.
	 */
	private static final String WHITE_UP_QUEEN_FEN =
			"4k3/8/8/8/8/8/8/Q3K3 w - - 0 1";

	/**
	 * Legal position where Black has an extra queen.
	 */
	private static final String BLACK_UP_QUEEN_FEN =
			"q3k3/8/8/8/8/8/8/4K3 b - - 0 1";

	/**
	 * Utility class; prevent instantiation.
	 */
	private BuiltInEngineRegressionTest() {
		// utility
	}

	/**
	 * Runs the regression checks.
	 *
	 * @param args unused command-line arguments
	 * @throws IOException if the synthetic NNUE test file cannot be written
	 */
	public static void main(String[] args) throws IOException {
		testStartPositionSearch();
		testMateInOne();
		testMctsForcedMateProofOverridesVisits();
		testMctsForcedMateInFourProofOverridesVisits();
		testMateCliCommand();
		testPublishedMatePositions();
		testMctsQuiescenceResolvesTacticalLeaves();
		testSparseMaterialMateInOne();
		testNodeBudgetStop();
		testPrimedRootFallbackOrdering();
		testRootHistoryRepetition();
		testParallelAndBatchedSearch();
		testEvaluatorSelection();
		testClassicalEvaluatorSanity();
		testCliFormats();
		testBuiltInUciLoop();
		testNnueCliEvaluator();
		System.out.println("BuiltInEngineRegressionTest: all checks passed");
	}

	/**
	 * Verifies the engine returns a legal move and completes the requested depth.
	 */
	private static void testStartPositionSearch() {
		Position position = new Position(START_FEN);
		try (Mcts searcher = new Mcts()) {
			Result result = searcher.search(position, new Limits(2, 64L, 0L));
			assertTrue(result.hasBestMove(), "start position has a best move");
			assertTrue(position.isLegalMove(result.bestMove()), "start position best move is legal");
			assertTrue(result.depth() > 0, "start position search depth");
			assertTrue(result.nodes() > 0L, "start position node count");
		}
	}

	/**
	 * Verifies mate scores and best-move output on a forced mate in one.
	 */
	private static void testMateInOne() {
		try (Mcts searcher = new Mcts()) {
			Result result = searcher.search(
					new Position(MATE_IN_ONE_FEN),
					new Limits(1, 0L, 0L));
			assertEquals("g6g7", Move.toString(result.bestMove()), "mate-in-one best move");
			assertEquals(1, result.mateIn(), "mate-in-one score");
			assertTrue(result.scoreLabel().equals("#1"), "mate-in-one score label");
		}
	}

	/**
	 * Verifies a root mate-in-one is proven from expanded terminal children
	 * before any playout budget is spent.
	 */
	private static void testMctsForcedMateProofOverridesVisits() {
		try (Mcts searcher = new Mcts(new PrimedOrderingEvaluator())) {
			Result result = searcher.search(
					new Position(MATE_IN_ONE_FEN),
					new Limits(1, 1L, 0L));
			assertEquals("g6g7", Move.toString(result.bestMove()), "mate-in-one tree proof best move");
			assertEquals(1, result.mateIn(), "mate-in-one tree proof distance");
			assertEquals("#1", result.scoreLabel(), "mate-in-one tree proof score label");
			assertEquals(0L, result.nodes(), "mate-in-one tree proof uses no MCTS playouts");
			assertFalse(result.stopped(), "mate-in-one tree proof does not report a budget stop");
			assertEquals(1, result.principalVariation().length, "mate-in-one tree proof PV length");
		}
	}

	/**
	 * Verifies LC0-style terminal proof propagation reaches the reported mate in
	 * four and stops before exhausting the requested playout budget.
	 */
	private static void testMctsForcedMateInFourProofOverridesVisits() {
		try (Mcts searcher = new Mcts(new PrimedOrderingEvaluator())) {
			Result result = searcher.search(
					new Position(FORCED_MATE_IN_FOUR_FEN),
					new Limits(1, 1L, 0L));
			assertEquals("c1c8", Move.toString(result.bestMove()), "forced mate-in-four proof best move");
			assertEquals(4, result.mateIn(), "forced mate-in-four proof distance");
			assertEquals("#4", result.scoreLabel(), "forced mate-in-four proof score label");
			assertEquals(0L, result.nodes(), "forced mate-in-four root proof uses no MCTS playouts");
			assertFalse(result.stopped(), "forced mate-in-four proof does not report a budget stop");
			assertEquals(7, result.principalVariation().length, "forced mate-in-four proof PV length");
		}
	}

	/**
	 * Verifies the deterministic no-eval mate finder CLI proves forced mates and
	 * reports non-mates without invoking engine evaluation.
	 */
	private static void testMateCliCommand() {
		String summary = TestSupport.runMain("engine", "mate", FEN_OPTION, FORCED_MATE_IN_FOUR_FEN,
				"--mate", "4");
		assertTrue(summary.contains("found: true"), "engine mate summary found flag");
		assertTrue(summary.contains("mate: #4"), "engine mate summary mate distance");
		assertTrue(summary.contains("best: c1c8 (Rc8+)"), "engine mate summary best move");
		assertTrue(summary.contains("pv-san: Rc8+ Rd8 Rxd8+ Re8 Rxe8+ Rf8 Rxf8#"),
				"engine mate summary PV");

		String both = TestSupport.runMain("engine", "mate", FEN_OPTION, FORCED_MATE_IN_FOUR_FEN,
				"--mate", "4", FORMAT_OPTION, "both").strip();
		assertEquals("c1c8\tRc8+\t#4", both, "engine mate both format");

		String shortcut = TestSupport.runMain("mate", FEN_OPTION, FORCED_MATE_IN_FOUR_FEN,
				"--mate", "4", FORMAT_OPTION, "both").strip();
		assertEquals("c1c8\tRc8+\t#4", shortcut, "top-level mate shortcut both format");

		String threaded = TestSupport.runMain("engine", "mate", FEN_OPTION, FORCED_MATE_IN_FOUR_FEN,
				"--mate", "4", "--threads", "2", FORMAT_OPTION, "both").strip();
		assertEquals("c1c8\tRc8+\t#4", threaded, "engine mate threaded both format");

		String json = TestSupport.runMain("engine", "mate", "--startpos", "--mate", "1",
				"--nodes", "10000", "--threads", "2", "--json").strip();
		assertTrue(json.contains("\"found\":false"), "engine mate JSON found flag");
		assertTrue(json.contains("\"maxMate\":1"), "engine mate JSON max mate");
		assertTrue(json.contains("\"threads\":2"), "engine mate JSON threads");

		TestSupport.FailureResult badThreads = TestSupport.runMainExpectFailure("engine", "mate",
				FEN_OPTION, MATE_IN_ONE_FEN, "--threads", "0");
		assertEquals(2, badThreads.exitCode(), "engine mate invalid threads exit code");
		assertTrue(badThreads.stderr().contains("--threads must be positive"), "engine mate invalid threads error");
	}

	/**
	 * Verifies published mate examples from common chess references.
	 */
	private static void testPublishedMatePositions() {
		assertMateCli(FOOLS_MATE_PRE_FEN, 1, "d8h4\tQh4#\t#1", "Fool's Mate");
		assertMateCli(SCHOLARS_MATE_PRE_FEN, 1, "h5f7\tQxf7#\t#1", "Scholar's Mate");
		assertMateCli(LEGAL_TRAP_PRE_FEN, 1, "c3d5\tNd5#\t#1", "Legal Trap");
		assertMateCli(LAWS_MATE_IN_TWO_FEN, 2, "g6d6\tRd6\t#2", "Laws mate-in-two");
		assertMateCli(HOEG_PROMOTION_MATE_IN_THREE_FEN, 3, "f6f7\tf7\t#3", "Hoeg promotion mate-in-three");
		assertBuiltInTreeProof(LAWS_MATE_IN_TWO_FEN, "g6d6", "#2", 500L, "Laws mate-in-two");
	}

	/**
	 * Asserts the no-eval mate finder returns the expected compact result.
	 * @param fen FEN string
	 * @param maxMate max mate value
	 * @param expectedBoth expected both value
	 * @param label label text
	 */
	private static void assertMateCli(String fen, int maxMate, String expectedBoth, String label) {
		String both = TestSupport.runMain("engine", "mate", FEN_OPTION, fen,
				"--mate", String.valueOf(maxMate), FORMAT_OPTION, "both").strip();
		assertEquals(expectedBoth, both, label + " mate CLI result");
	}

	/**
	 * Asserts the built-in MCTS command proves a mate through terminal tree
	 * propagation before exhausting its playout budget.
	 * @param fen FEN string
	 * @param expectedUci expected uci value
	 * @param expectedScore expected score value
	 * @param maxNodes maximum node count
	 * @param label label text
	 */
	private static void assertBuiltInTreeProof(
			String fen,
			String expectedUci,
			String expectedScore,
			long maxNodes,
			String label) {
		String summary = TestSupport.runMain(ENGINE_COMMAND, BUILTIN_COMMAND, FEN_OPTION, fen,
				"--max-nodes", String.valueOf(maxNodes), FORMAT_OPTION, SUMMARY_FORMAT);
		assertTrue(summary.contains(BEST_PREFIX + expectedUci + " ("), label + " built-in best move");
		assertTrue(summary.contains("score: " + expectedScore), label + " built-in mate score");
		assertTrue(summary.contains("stopped: false"), label + " built-in proof stops search");
	}

	/**
	 * Verifies MCTS leaf valuation does not stop abruptly on mate-in-one or a
	 * hanging capture.
	 */
	private static void testMctsQuiescenceResolvesTacticalLeaves() {
		try (Mcts neutral = new Mcts(new PrimedOrderingEvaluator())) {
			Object evaluation = invokePrivate(neutral, "evaluatePosition",
					new Class<?>[] { Position.class }, new Position(MATE_IN_ONE_FEN));
			double value = (Double) invokePrivate(evaluation, "value", new Class<?>[0]);
			assertTrue(value >= 0.999, "MCTS quiescence sees mate-in-one leaf");
		}

		try (Mcts searcher = new Mcts()) {
			Object evaluation = invokePrivate(searcher, "evaluatePosition",
					new Class<?>[] { Position.class },
					new Position("7k/8/4q3/8/2B5/8/8/4K3 w - - 0 1"));
			double value = (Double) invokePrivate(evaluation, "value", new Class<?>[0]);
			assertTrue(value > -0.05, "MCTS quiescence resolves a hanging queen capture");
		}
	}

	/**
	 * Verifies sparse material that can still mate is not pruned as a draw.
	 */
	private static void testSparseMaterialMateInOne() {
		Position position = new Position(OPPOSITE_BISHOP_MATE_IN_ONE_FEN);
		short mate = Move.parse("d2c3");
		assertTrue(position.isLegalMove(mate), "sparse-material mate move legal");
		Position after = position.copy().play(mate);
		assertTrue(after.isCheckmate(), "sparse-material move gives checkmate");
		assertFalse(after.isInsufficientMaterial(), "opposite-colored bishops are not dead material");

		try (Mcts searcher = new Mcts()) {
			Result result = searcher.search(position, new Limits(1, 0L, 0L));
			assertEquals("d2c3", Move.toString(result.bestMove()), "sparse-material mate best move");
			assertEquals(1, result.mateIn(), "sparse-material mate score");
		}
	}

	/**
	 * Verifies node budgets stop search while still returning a fallback move.
	 */
	private static void testNodeBudgetStop() {
		try (Mcts searcher = new Mcts()) {
			Result result = searcher.search(
					new Position(START_FEN),
					new Limits(5, 1L, 0L));
			assertTrue(result.stopped(), "node budget stop flag");
			assertTrue(result.hasBestMove(), "node budget fallback best move");
		}
	}

	/**
	 * Verifies root fallback can honor evaluator move priors even when the node
	 * budget expires before any root child is evaluated.
	 */
	private static void testPrimedRootFallbackOrdering() {
		Position position = new Position(START_FEN);
		try (Mcts searcher = new Mcts(new PrimedOrderingEvaluator())) {
			Result result = searcher.search(position, new Limits(5, 1L, 0L));
			assertTrue(result.stopped(), "primed root fallback stop flag");
			assertEquals("e2e4", Move.toString(result.bestMove()), "primed root fallback preferred move");
		}
	}

	/**
	 * Verifies pre-root history is used for repetition draws.
	 */
	private static void testRootHistoryRepetition() {
		Position root = new Position(START_FEN);
		long key = root.signatureCore();
		try (Mcts searcher = new Mcts()) {
			Result result = searcher.searchReusable(root, new Limits(3, 64L, 0L), null, new long[] { key, key });
			assertTrue(result.hasBestMove(), "repetition root still returns a legal move");
			assertEquals(0, result.scoreCentipawns(), "repetition root score is draw");
			assertEquals(0L, result.nodes(), "repetition root stops before playouts");
		}
	}

	/**
	 * Verifies threaded and batched MCTS modes return legal bounded results.
	 */
	private static void testParallelAndBatchedSearch() {
		Position start = new Position(START_FEN);
		try (Mcts searcher = new Mcts()) {
			searcher.setThreads(2);
			Result result = searcher.search(start, new Limits(2, 64L, 0L));
			assertTrue(result.hasBestMove(), "threaded MCTS best move");
			assertTrue(start.isLegalMove(result.bestMove()), "threaded MCTS legal best move");
			assertTrue(result.nodes() >= 64L, "threaded MCTS visits node budget");
		}
		try (Mcts searcher = new Mcts()) {
			searcher.setBatchSize(4);
			Result result = searcher.search(start, new Limits(2, 16L, 0L));
			assertTrue(result.hasBestMove(), "batched MCTS best move");
			assertEquals(16L, result.nodes(), "batched MCTS exact node budget");
		}
	}

	/**
	 * Verifies evaluator kind parsing and explicit evaluator injection.
	 */
	private static void testEvaluatorSelection() {
		assertTrue(Kind.parse(CLASSICAL_EVALUATOR) == Kind.CLASSICAL,
				"classical evaluator parse");
		assertTrue(Kind.parse("nnue") == Kind.NNUE,
				"nnue evaluator parse");
		assertTrue(Kind.parse("lc0") == Kind.LC0,
				"lc0 evaluator parse");

		try (Mcts searcher = new Mcts(new Classical())) {
			Result result = searcher.search(new Position(SIMPLE_FEN), new Limits(1, 0L, 0L));
			assertTrue(result.hasBestMove(), "explicit classical evaluator best move");
			assertTrue(searcher.evaluatorName().equals(CLASSICAL_EVALUATOR), "explicit classical evaluator label");
		}
	}

	/**
	 * Verifies basic classical-evaluator sign and material sanity without pinning
	 * exact centipawn constants.
	 */
	private static void testClassicalEvaluatorSanity() {
		Position start = new Position(START_FEN);
		assertTrue(Math.abs(Wdl.evaluateWhiteCentipawns(start)) < 50,
				"classical start position near equal");

		Position whiteUpQueen = new Position(WHITE_UP_QUEEN_FEN);
		assertTrue(Wdl.evaluateWhiteCentipawns(whiteUpQueen) > 700,
				"classical white extra queen is clearly winning");
		assertTrue(Wdl.evaluateStmCentipawns(whiteUpQueen) > 700,
				"classical white-to-move score follows side to move");

		Position blackUpQueen = new Position(BLACK_UP_QUEEN_FEN);
		assertTrue(Wdl.evaluateWhiteCentipawns(blackUpQueen) < -700,
				"classical black extra queen is clearly winning");
		assertTrue(Wdl.evaluateStmCentipawns(blackUpQueen) > 700,
				"classical black-to-move score follows side to move");
	}

	/**
	 * Verifies the CLI wrapper exposes compact and summary formats.
	 */
	private static void testCliFormats() {
		String both = TestSupport.runMain(ENGINE_COMMAND, BUILTIN_COMMAND, FEN_OPTION, SIMPLE_FEN,
				DEPTH_OPTION, "1", FORMAT_OPTION, "both").strip();
		assertTrue(both.contains("\t"), "engine builtin both output separator");
		assertTrue(both.startsWith("a1"), "engine builtin UCI move output");
		assertTrue(both.contains("K"), "engine builtin SAN move output");

		String summary = TestSupport.runMain(ENGINE_COMMAND, "java", FEN_OPTION, MATE_IN_ONE_FEN, DEPTH_OPTION, "1",
				"--evaluator", CLASSICAL_EVALUATOR, FORMAT_OPTION, SUMMARY_FORMAT);
		assertTrue(summary.contains("evaluator: " + CLASSICAL_EVALUATOR), "engine java summary evaluator");
		assertTrue(summary.contains(BEST_PREFIX + "g6g7 (Qg7#)"), "engine java summary best move");
		assertTrue(summary.contains("score: #1"), "engine java summary score");

		String uciInfo = TestSupport.runMain(ENGINE_COMMAND, BUILTIN_COMMAND, FEN_OPTION, SIMPLE_FEN,
				DEPTH_OPTION, "2", "--nodes", "1000");
		assertTrue(uciInfo.contains("info depth "), "engine builtin default UCI info");
		assertTrue(uciInfo.contains(" pv "), "engine builtin default UCI info PV");
		assertTrue(uciInfo.contains("bestmove "), "engine builtin default UCI bestmove");

		String startSummary = TestSupport.runMain(
				ENGINE_COMMAND, BUILTIN_COMMAND, "--startpos", DEPTH_OPTION, "1", FORMAT_OPTION, SUMMARY_FORMAT);
		assertTrue(startSummary.contains(BEST_PREFIX), "engine builtin --startpos summary best move");

		String randomSummary = TestSupport.runMain(
				ENGINE_COMMAND, BUILTIN_COMMAND, "--randompos", DEPTH_OPTION, "1", FORMAT_OPTION, SUMMARY_FORMAT);
		assertTrue(randomSummary.contains(BEST_PREFIX), "engine builtin --randompos summary best move");

		String capped = TestSupport.runMain(ENGINE_COMMAND, BUILTIN_COMMAND, FEN_OPTION, START_FEN,
				DEPTH_OPTION, "10", "--max-duration", "1ms");
		assertTrue(capped.contains("info depth "), "engine builtin budget fallback UCI info");
		assertTrue(capped.contains("bestmove "), "engine builtin budget fallback bestmove");

		String engineHelp = TestSupport.runMain("help", ENGINE_COMMAND);
		assertTrue(engineHelp.contains(BUILTIN_COMMAND), "help engine lists builtin");

		String builtinHelp = TestSupport.runMain("help", ENGINE_COMMAND, BUILTIN_COMMAND);
		assertTrue(builtinHelp.contains("engine builtin options:"), "help engine builtin options");
		assertTrue(builtinHelp.contains("--startpos"), "help engine builtin startpos option");
		assertTrue(builtinHelp.contains("--randompos"), "help engine builtin randompos option");
		assertTrue(builtinHelp.contains("--uci"), "help engine builtin UCI loop option");
		assertTrue(builtinHelp.contains("--evaluator KIND"), "help engine builtin evaluator option");
		assertTrue(builtinHelp.contains("--classical|--nnue|--lc0"), "help engine builtin evaluator shortcuts");
		assertTrue(builtinHelp.contains("uci-info"), "help engine builtin UCI info format");
	}

	/**
	 * Verifies the built-in MCTS engine speaks the essential UCI loop.
	 *
	 * @throws IOException if the test reader fails
	 */
	private static void testBuiltInUciLoop() throws IOException {
		String input = String.join(System.lineSeparator(),
				"uci",
				"setoption name Threads value 2",
				"isready",
				"ucinewgame",
				"position startpos moves e2e4 e7e5",
				"go nodes 8",
				"quit",
				"");
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (Mcts searcher = new Mcts();
				PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
			MctsUci.run(new StringReader(input), out, searcher);
		}
		String output = bytes.toString(StandardCharsets.UTF_8);
		assertTrue(output.contains("id name ChessRTK MCTS"), "MCTS UCI id name");
		assertTrue(output.contains("option name Threads"), "MCTS UCI Threads option");
		assertTrue(output.contains("uciok"), "MCTS UCI uciok");
		assertTrue(output.contains("readyok"), "MCTS UCI readyok");
		assertTrue(output.contains("bestmove "), "MCTS UCI bestmove");

		String stopInput = String.join(System.lineSeparator(),
				"position startpos",
				"go infinite",
				"stop",
				"quit",
				"");
		ByteArrayOutputStream stopBytes = new ByteArrayOutputStream();
		try (Mcts searcher = new Mcts();
				PrintStream out = new PrintStream(stopBytes, true, StandardCharsets.UTF_8)) {
			MctsUci.run(new StringReader(stopInput), out, searcher);
		}
		assertTrue(stopBytes.toString(StandardCharsets.UTF_8).contains("bestmove "), "MCTS UCI stop bestmove");

		String repetitionInput = String.join(System.lineSeparator(),
				"position startpos moves g1f3 g8f6 f3g1 f6g8 g1f3 g8f6 f3g1 f6g8",
				"go nodes 64",
				"quit",
				"");
		ByteArrayOutputStream repetitionBytes = new ByteArrayOutputStream();
		try (Mcts searcher = new Mcts();
				PrintStream out = new PrintStream(repetitionBytes, true, StandardCharsets.UTF_8)) {
			MctsUci.run(new StringReader(repetitionInput), out, searcher);
		}
		String repetitionOutput = repetitionBytes.toString(StandardCharsets.UTF_8);
		assertTrue(repetitionOutput.contains("info depth 0 score cp 0 nodes 0"),
				"MCTS UCI root repetition draw");
	}

	/**
	 * Verifies the built-in engine can load NNUE weights through the CLI.
	 *
	 * @throws IOException if temp-file IO fails
	 */
	private static void testNnueCliEvaluator() throws IOException {
		Path defaultWeights = chess.nn.nnue.Model.DEFAULT_WEIGHTS;
		Path backup = null;
		if (Files.exists(defaultWeights)) {
			backup = PathOps.createLocalTempFile("crtk-engine-nnue-default-backup-", ".nnue");
			Files.deleteIfExists(backup);
			Files.move(defaultWeights, backup, StandardCopyOption.REPLACE_EXISTING);
		}
		try {
			TestSupport.FailureResult missingWeights = TestSupport.runMainExpectFailure(
					ENGINE_COMMAND, BUILTIN_COMMAND, FEN_OPTION, SIMPLE_FEN,
					DEPTH_OPTION, "1", "--nnue", FORMAT_OPTION, SUMMARY_FORMAT);
			assertEquals(2, missingWeights.exitCode(), "engine builtin NNUE missing-weights exit code");
			assertTrue(missingWeights.stdout().isBlank(), "engine builtin NNUE missing-weights stdout");
			assertTrue(
					missingWeights.stderr().contains("default NNUE weights not found"),
					"engine builtin NNUE missing-weights error");
			assertTrue(
					missingWeights.stderr().contains("--weights <path>"),
					"engine builtin NNUE missing-weights hint");
			assertTrue(
					missingWeights.stderr().contains("./install.sh --models"),
					"engine builtin NNUE install hint");
		} finally {
			if (backup != null) {
				Files.move(backup, defaultWeights, StandardCopyOption.REPLACE_EXISTING);
			}
		}

		Path temp = PathOps.createLocalTempFile("crtk-engine-nnue-", ".nnue");
		try {
			writeZeroNnue(temp);
			String summary = TestSupport.runMain(ENGINE_COMMAND, BUILTIN_COMMAND, FEN_OPTION, SIMPLE_FEN,
					DEPTH_OPTION, "1", "--nnue", "--weights", temp.toString(), FORMAT_OPTION, SUMMARY_FORMAT);
			assertTrue(summary.contains("evaluator: nnue(cpu)"), "engine builtin NNUE evaluator label");
			assertTrue(summary.contains("best: "), "engine builtin NNUE best move");
		} finally {
			Files.deleteIfExists(temp);
		}
	}

	/**
	 * Writes a minimal valid CRTK NNUE file whose evaluation is always zero.
	 *
	 * @param path output path
	 * @throws IOException if writing fails
	 */
	private static void writeZeroNnue(Path path) throws IOException {
		int hidden = 1;
		float[] featureBias = new float[hidden];
		float[] featureWeights = new float[FeatureEncoder.FEATURE_COUNT * hidden];
		float[] outputWeights = new float[hidden * 2];
		int bytes = 4 + Integer.BYTES + Integer.BYTES + Integer.BYTES + Float.BYTES
				+ bytesForArray(featureBias)
				+ bytesForArray(featureWeights)
				+ bytesForArray(outputWeights)
				+ Float.BYTES;
		ByteBuffer buffer = ByteBuffer.allocate(bytes).order(ByteOrder.LITTLE_ENDIAN);
		buffer.put(new byte[] { 'N', 'N', 'U', 'E' });
		buffer.putInt(1);
		buffer.putInt(FeatureEncoder.FEATURE_COUNT);
		buffer.putInt(hidden);
		buffer.putFloat(1.0f);
		putArray(buffer, featureBias);
		putArray(buffer, featureWeights);
		putArray(buffer, outputWeights);
		buffer.putFloat(0.0f);
		Files.write(path, buffer.array());
	}

	/**
	 * Returns byte size of a length-prefixed float array.
	 *
	 * @param values values
	 * @return byte size
	 */
	private static int bytesForArray(float[] values) {
		return Integer.BYTES + values.length * Float.BYTES;
	}

	/**
	 * Writes a length-prefixed float array.
	 *
	 * @param buffer destination
	 * @param values values
	 */
	private static void putArray(ByteBuffer buffer, float[] values) {
		buffer.putInt(values.length);
		for (float value : values) {
			buffer.putFloat(value);
		}
	}

	/**
	 * Invokes a private method for focused engine invariants.
	 * @param target target value
	 * @param name name value
	 * @param parameterTypes parameter types value
	 * @param args command arguments
	 * @return invoke private result
	 */
	private static Object invokePrivate(Object target, String name, Class<?>[] parameterTypes, Object... args) {
		try {
			Method method = target.getClass().getDeclaredMethod(name, parameterTypes);
			method.setAccessible(true);
			return method.invoke(target, args);
		} catch (ReflectiveOperationException ex) {
			throw new AssertionError("failed to invoke " + name, ex);
		}
	}

	/**
	 * Synthetic evaluator that only exposes a move prior after explicit priming.
	 */
	private static final class PrimedOrderingEvaluator implements CentipawnEvaluator {

		/**
		 * Position signature that is currently eligible for the synthetic move prior.
		 */
		private long primedSignature = Long.MIN_VALUE;

		/**
		 * Returns a neutral static evaluation for every position.
		 *
		 * @param position position to evaluate
		 * @return zero centipawns
		 */
		@Override
		public int evaluate(Position position) {
			return 0;
		}

		/**
		 * Primes the evaluator so only this position receives move-ordering bonuses.
		 *
		 * @param position position about to be ordered
		 */
		@Override
		public void prepareMoveOrdering(Position position) {
			primedSignature = position.signature();
		}

		/**
		 * Adds a large synthetic bonus to {@code e2e4} after explicit priming.
		 *
		 * @param position position whose legal moves are being ordered
		 * @param moves legal moves aligned with {@code scores}
		 * @param scores mutable ordering scores to adjust in place
		 */
		@Override
		public void scoreMoves(Position position, short[] moves, int[] scores) {
			if (position.signature() != primedSignature) {
				return;
			}
			for (int index = 0; index < moves.length; index++) {
				if ("e2e4".equals(Move.toString(moves[index]))) {
					scores[index] += 2_000_000;
				}
			}
		}
	}
}
