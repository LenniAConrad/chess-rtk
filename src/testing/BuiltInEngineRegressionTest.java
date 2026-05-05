package testing;

import static testing.TestSupport.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import chess.classical.Wdl;
import chess.core.Move;
import chess.core.Position;
import chess.engine.AlphaBeta;
import chess.engine.Limits;
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
@SuppressWarnings({"java:S1192", "squid:S1192"})
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
		testSparseMaterialMateInOne();
		testNodeBudgetStop();
		testPrimedRootFallbackOrdering();
		testEvaluatorSelection();
		testClassicalEvaluatorSanity();
		testCliFormats();
		testNnueCliEvaluator();
		System.out.println("BuiltInEngineRegressionTest: all checks passed");
	}

	/**
	 * Verifies the engine returns a legal move and completes the requested depth.
	 */
	private static void testStartPositionSearch() {
		Position position = new Position(START_FEN);
		try (AlphaBeta searcher = new AlphaBeta()) {
			Result result = searcher.search(position, new Limits(2, 0L, 0L));
			assertTrue(result.hasBestMove(), "start position has a best move");
			assertTrue(position.isLegalMove(result.bestMove()), "start position best move is legal");
			assertEquals(2, result.depth(), "start position search depth");
			assertTrue(result.nodes() > 0L, "start position node count");
		}
	}

	/**
	 * Verifies mate scores and best-move output on a forced mate in one.
	 */
	private static void testMateInOne() {
		try (AlphaBeta searcher = new AlphaBeta()) {
			Result result = searcher.search(
					new Position(MATE_IN_ONE_FEN),
					new Limits(1, 0L, 0L));
			assertEquals("g6g7", Move.toString(result.bestMove()), "mate-in-one best move");
			assertEquals(1, result.mateIn(), "mate-in-one score");
			assertTrue(result.scoreLabel().equals("#1"), "mate-in-one score label");
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

		try (AlphaBeta searcher = new AlphaBeta()) {
			Result result = searcher.search(position, new Limits(1, 0L, 0L));
			assertEquals("d2c3", Move.toString(result.bestMove()), "sparse-material mate best move");
			assertEquals(1, result.mateIn(), "sparse-material mate score");
		}
	}

	/**
	 * Verifies node budgets stop search while still returning a fallback move.
	 */
	private static void testNodeBudgetStop() {
		try (AlphaBeta searcher = new AlphaBeta()) {
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
		try (AlphaBeta searcher = new AlphaBeta(new PrimedOrderingEvaluator())) {
			Result result = searcher.search(position, new Limits(5, 1L, 0L));
			assertTrue(result.stopped(), "primed root fallback stop flag");
			assertEquals("e2e4", Move.toString(result.bestMove()), "primed root fallback preferred move");
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

		try (AlphaBeta searcher = new AlphaBeta(new Classical())) {
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
		assertTrue(uciInfo.contains("info depth 1 score "), "engine builtin default UCI info depth 1");
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
		assertTrue(capped.contains("info depth 0 score "), "engine builtin budget fallback UCI info");
		assertTrue(capped.contains("bestmove "), "engine builtin budget fallback bestmove");

		String engineHelp = TestSupport.runMain("help", ENGINE_COMMAND);
		assertTrue(engineHelp.contains(BUILTIN_COMMAND), "help engine lists builtin");

		String builtinHelp = TestSupport.runMain("help", ENGINE_COMMAND, BUILTIN_COMMAND);
		assertTrue(builtinHelp.contains("engine builtin options:"), "help engine builtin options");
		assertTrue(builtinHelp.contains("--startpos"), "help engine builtin startpos option");
		assertTrue(builtinHelp.contains("--randompos"), "help engine builtin randompos option");
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
		Path defaultWeights = chess.nn.nnue.Model.DEFAULT_WEIGHTS;
		Path backup = null;
		if (Files.exists(defaultWeights)) {
			backup = Files.createTempFile("crtk-engine-nnue-default-backup-", ".nnue");
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

		Path temp = Files.createTempFile("crtk-engine-nnue-", ".nnue");
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
