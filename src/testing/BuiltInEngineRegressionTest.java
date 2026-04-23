package testing;

import static testing.TestSupport.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import chess.core.Move;
import chess.core.Position;
import chess.engine.search.AlphaBeta;
import chess.engine.search.Limits;
import chess.engine.search.Result;
import chess.eval.Classical;
import chess.eval.Kind;
import chess.nn.nnue.FeatureEncoder;

/**
 * Regression checks for the built-in Java engine package and CLI wrapper.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class BuiltInEngineRegressionTest {

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
	 * Bare-king legal-position smoke test.
	 */
	private static final String SIMPLE_FEN =
			"8/8/8/8/8/8/8/K6k w - - 0 1";

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
		testSparseMaterialMateInOne();
		testNodeBudgetStop();
		testEvaluatorSelection();
		testCliFormats();
		testNnueCliEvaluator();
		System.out.println("BuiltInEngineRegressionTest: all checks passed");
	}

	/**
	 * Verifies the engine returns a legal move and completes the requested depth.
	 */
	private static void testStartPositionSearch() {
		Position position = new Position(START_FEN);
		Result result = new AlphaBeta().search(position, new Limits(2, 0L, 0L));
		assertTrue(result.hasBestMove(), "start position has a best move");
		assertTrue(position.isLegalMove(result.bestMove()), "start position best move is legal");
		assertEquals(2, result.depth(), "start position search depth");
		assertTrue(result.nodes() > 0L, "start position node count");
	}

	/**
	 * Verifies mate scores and best-move output on a forced mate in one.
	 */
	private static void testMateInOne() {
		Result result = new AlphaBeta().search(
				new Position(MATE_IN_ONE_FEN),
				new Limits(1, 0L, 0L));
		assertEquals("g6g7", Move.toString(result.bestMove()), "mate-in-one best move");
		assertEquals(1, result.mateIn(), "mate-in-one score");
		assertTrue(result.scoreLabel().equals("#1"), "mate-in-one score label");
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

		Result result = new AlphaBeta().search(position, new Limits(1, 0L, 0L));
		assertEquals("d2c3", Move.toString(result.bestMove()), "sparse-material mate best move");
		assertEquals(1, result.mateIn(), "sparse-material mate score");
	}

	/**
	 * Verifies node budgets stop search while still returning a fallback move.
	 */
	private static void testNodeBudgetStop() {
		Result result = new AlphaBeta().search(
				new Position(START_FEN),
				new Limits(5, 1L, 0L));
		assertTrue(result.stopped(), "node budget stop flag");
		assertTrue(result.hasBestMove(), "node budget fallback best move");
	}

	/**
	 * Verifies evaluator kind parsing and explicit evaluator injection.
	 */
	private static void testEvaluatorSelection() {
		assertTrue(Kind.parse("classical") == Kind.CLASSICAL,
				"classical evaluator parse");
		assertTrue(Kind.parse("nnue") == Kind.NNUE,
				"nnue evaluator parse");
		assertTrue(Kind.parse("lc0") == Kind.LC0,
				"lc0 evaluator parse");

		try (AlphaBeta searcher = new AlphaBeta(new Classical())) {
			Result result = searcher.search(new Position(SIMPLE_FEN), new Limits(1, 0L, 0L));
			assertTrue(result.hasBestMove(), "explicit classical evaluator best move");
			assertTrue(searcher.evaluatorName().equals("classical"), "explicit classical evaluator label");
		}
	}

	/**
	 * Verifies the CLI wrapper exposes compact and summary formats.
	 */
	private static void testCliFormats() {
		String both = TestSupport.runMain("engine", "builtin", "--fen", SIMPLE_FEN, "--depth", "1", "--format", "both").strip();
		assertTrue(both.contains("\t"), "engine builtin both output separator");
		assertTrue(both.startsWith("a1"), "engine builtin UCI move output");
		assertTrue(both.contains("K"), "engine builtin SAN move output");

		String summary = TestSupport.runMain("engine", "java", "--fen", MATE_IN_ONE_FEN, "--depth", "1",
				"--evaluator", "classical", "--format", "summary");
		assertTrue(summary.contains("evaluator: classical"), "engine java summary evaluator");
		assertTrue(summary.contains("best: g6g7 (Qg7#)"), "engine java summary best move");
		assertTrue(summary.contains("score: #1"), "engine java summary score");

		String uciInfo = TestSupport.runMain("engine", "builtin", "--fen", SIMPLE_FEN, "--depth", "2", "--nodes", "1000");
		assertTrue(uciInfo.contains("info depth 1 score "), "engine builtin default UCI info depth 1");
		assertTrue(uciInfo.contains(" pv "), "engine builtin default UCI info PV");
		assertTrue(uciInfo.contains("bestmove "), "engine builtin default UCI bestmove");

		String capped = TestSupport.runMain("engine", "builtin", "--fen", START_FEN, "--depth", "10", "--max-duration", "1ms");
		assertTrue(capped.contains("info depth 0 score "), "engine builtin budget fallback UCI info");
		assertTrue(capped.contains("bestmove "), "engine builtin budget fallback bestmove");

		String engineHelp = TestSupport.runMain("help", "engine");
		assertTrue(engineHelp.contains("builtin"), "help engine lists builtin");

		String builtinHelp = TestSupport.runMain("help", "engine", "builtin");
		assertTrue(builtinHelp.contains("engine builtin options:"), "help engine builtin options");
		assertTrue(builtinHelp.contains("--evaluator KIND"), "help engine builtin evaluator option");
		assertTrue(builtinHelp.contains("--classical|--nnue|--lc0"), "help engine builtin evaluator shortcuts");
		assertTrue(builtinHelp.contains("uci-info"), "help engine builtin UCI info format");
	}

	/**
	 * Verifies the built-in engine can load NNUE weights through the CLI.
	 *
	 * @throws IOException if temp-file IO fails
	 */
	private static void testNnueCliEvaluator() throws IOException {
		String fallbackSummary = TestSupport.runMain("engine", "builtin", "--fen", SIMPLE_FEN, "--depth", "1",
				"--nnue", "--format", "summary");
		assertTrue(fallbackSummary.contains("evaluator: nnue(cpu)"), "engine builtin default NNUE fallback label");
		assertTrue(fallbackSummary.contains("best: "), "engine builtin default NNUE fallback best move");

		Path temp = Files.createTempFile("crtk-engine-nnue-", ".nnue");
		try {
			writeZeroNnue(temp);
			String summary = TestSupport.runMain("engine", "builtin", "--fen", SIMPLE_FEN, "--depth", "1",
					"--nnue", "--weights", temp.toString(), "--format", "summary");
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
}
