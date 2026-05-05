package testing;

import static testing.TestSupport.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import chess.core.Move;
import chess.core.Field;
import chess.core.Position;
import chess.nn.nnue.Accumulator;
import chess.nn.nnue.FeatureEncoder;
import chess.nn.nnue.Model;
import chess.nn.nnue.Network;

/**
 * Regression checks for the pure-Java NNUE implementation.
 */
public final class NNUERegressionTest {

    /**
     * Synthetic position with one non-king piece.
     */
	private static final String ONE_PAWN_FEN = "4k3/8/8/8/8/8/4P3/4K3 w - - 0 1";

    /**
     * Same kings as {@link #ONE_PAWN_FEN}, with the pawn removed.
     */
	private static final String NO_PAWN_FEN = "4k3/8/8/8/8/8/8/4K3 w - - 0 1";

    /**
     * Prevents instantiation.
     */
	private NNUERegressionTest() {
		// utility
	}

    /**
     * Runs all checks.
     *
     * @param args ignored
     * @throws IOException if the binary-load check cannot write its temp file
     */
	public static void main(String[] args) throws IOException {
		testFeaturePerspectiveSymmetry();
		testAccumulatorDeltaMatchesRebuild();
		testIncrementalSearchState();
		testBinaryLoad();
		System.out.println("NNUERegressionTest: all checks passed");
	}

    /**
     * Verifies White and Black perspective features normalize the starting position
     * symmetrically.
     */
	private static void testFeaturePerspectiveSymmetry() {
		Position start = new Position("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
		int[] white = FeatureEncoder.activeFeatures(start, true);
		int[] black = FeatureEncoder.activeFeatures(start, false);

		assertEquals(30, white.length, "white active feature count");
		assertEquals(30, black.length, "black active feature count");

		int whiteKing = FeatureEncoder.squareFromPositionIndex(Field.toIndex("e1"));
		int whitePawn = FeatureEncoder.squareFromPositionIndex(Field.toIndex("a2"));
		int expectedWhitePawn = FeatureEncoder.encodeFeature(
				whiteKing,
				FeatureEncoder.OWN_PAWN,
				whitePawn);

		int blackKing = FeatureEncoder.orientSquare(
				FeatureEncoder.squareFromPositionIndex(Field.toIndex("e8")), false);
		int blackPawn = FeatureEncoder.orientSquare(
				FeatureEncoder.squareFromPositionIndex(Field.toIndex("a7")), false);
		int expectedBlackPawn = FeatureEncoder.encodeFeature(
				blackKing,
				FeatureEncoder.OWN_PAWN,
				blackPawn);

		assertTrue(contains(white, expectedWhitePawn), "white a2 pawn feature present");
		assertTrue(contains(black, expectedBlackPawn), "black a7 pawn feature present");
		assertEquals(expectedWhitePawn, expectedBlackPawn, "start-position pawn symmetry");
	}

    /**
     * Verifies incremental remove operations produce the same prediction as a fresh
     * accumulator rebuild.
     */
	private static void testAccumulatorDeltaMatchesRebuild() {
		Position withPawn = new Position(ONE_PAWN_FEN);
		Position noPawn = new Position(NO_PAWN_FEN);
		Network network = singlePawnNetwork(withPawn);

		float rebuilt = network.predict(withPawn).centipawns();
		Accumulator accumulator = network.newAccumulator(withPawn);
		float accumulated = network.predict(accumulator, true).centipawns();
		assertClose(rebuilt, accumulated, "fresh accumulator prediction");
		assertClose(40.0f, rebuilt, "synthetic pawn score");

		int[] whiteFeatures = FeatureEncoder.activeFeatures(withPawn, true);
		int[] blackFeatures = FeatureEncoder.activeFeatures(withPawn, false);
		assertEquals(1, whiteFeatures.length, "white one-pawn feature count");
		assertEquals(1, blackFeatures.length, "black one-pawn feature count");

		accumulator.removeFeature(true, whiteFeatures[0]);
		accumulator.removeFeature(false, blackFeatures[0]);

		float deltaUpdated = network.predict(accumulator, true).centipawns();
		float noPawnRebuilt = network.predict(noPawn).centipawns();
		assertClose(noPawnRebuilt, deltaUpdated, "delta-updated accumulator");
		assertClose(0.0f, noPawnRebuilt, "no-pawn score");
	}

    /**
     * Verifies incremental search state matches direct evaluation across move
     * types.
     */
	private static void testIncrementalSearchState() {
		Network network = denseSyntheticNetwork();
		assertIncrementalMove(network,
				"4k3/8/8/8/8/8/3P4/4K3 w - - 0 1",
				"e1f2",
				"incremental king move");
		assertIncrementalMove(network,
				"4k3/8/8/3p4/4P3/8/8/4K3 w - - 0 1",
				"e4d5",
				"incremental capture");
		assertIncrementalMove(network,
				"4k2r/8/8/8/8/8/8/R3K2R w KQ - 0 1",
				"e1g1",
				"incremental castle");
		assertIncrementalMove(network,
				"6k1/4P3/8/8/8/8/8/4K3 w - - 0 1",
				"e7e8q",
				"incremental promotion");
		assertIncrementalNullMove(network,
				"4k3/8/8/8/8/8/3P4/4K3 w - - 0 1",
				"incremental null move");
	}

    /**
     * Verifies the CRTK NNUE binary loader reads a valid synthetic model.
     *
     * @throws IOException if temp-file IO fails
     */
	private static void testBinaryLoad() throws IOException {
		Position position = new Position(ONE_PAWN_FEN);
		ModelArrays arrays = singlePawnArrays(position);
		Path temp = Files.createTempFile("crtk-nnue-test-", ".nnue");
		try {
			writeWeights(temp, arrays);
			Network loaded = Network.load(temp);
			assertEquals(FeatureEncoder.FEATURE_COUNT, loaded.info().inputFeatures(), "loaded feature count");
			assertEquals(arrays.hiddenSize, loaded.info().hiddenSize(), "loaded hidden size");
			assertClose(40.0f, loaded.predict(position).centipawns(), "loaded synthetic prediction");
		} finally {
			Files.deleteIfExists(temp);
		}
	}

    /**
     * Builds a network whose only non-zero feature weights respond to the pawn in
     * {@code position}.
     *
     * @param position reference position
     * @return synthetic network
     */
	private static Network singlePawnNetwork(Position position) {
		ModelArrays arrays = singlePawnArrays(position);
		return Network.create(
				arrays.hiddenSize,
				arrays.featureBias,
				arrays.featureWeights,
				arrays.outputWeights,
				arrays.outputBias,
				arrays.outputScale);
	}

    /**
     * Builds a deterministic dense network whose score changes under many feature
     * deltas.
     *
     * @return synthetic network
     */
	private static Network denseSyntheticNetwork() {
		int hidden = 2;
		float[] featureBias = new float[hidden];
		float[] featureWeights = new float[FeatureEncoder.FEATURE_COUNT * hidden];
		float[] outputWeights = new float[hidden * 2];
			for (int feature = 0; feature < FeatureEncoder.FEATURE_COUNT; feature++) {
				int base = feature * hidden;
				int featureBand = (feature / 29) % 23;
				featureWeights[base] = ((feature % 29) - 14) * 0.0006f;
				featureWeights[base + 1] = (featureBand - 11) * 0.0005f;
			}
		outputWeights[0] = 850.0f;
		outputWeights[1] = -675.0f;
		outputWeights[hidden] = -725.0f;
		outputWeights[hidden + 1] = 540.0f;
		return Network.create(hidden, featureBias, featureWeights, outputWeights, 0.0f, 1.0f);
	}

    /**
     * Creates deterministic arrays for a tiny synthetic NNUE model.
     *
     * @param position reference position containing one non-king piece
     * @return model arrays
     */
	private static ModelArrays singlePawnArrays(Position position) {
		int hidden = 2;
		float[] featureBias = new float[hidden];
		float[] featureWeights = new float[FeatureEncoder.FEATURE_COUNT * hidden];
		float[] outputWeights = new float[hidden * 2];

		int whiteFeature = FeatureEncoder.activeFeatures(position, true)[0];
		int blackFeature = FeatureEncoder.activeFeatures(position, false)[0];

		featureWeights[whiteFeature * hidden] = 0.50f;
		featureWeights[(blackFeature * hidden) + 1] = 0.25f;
		outputWeights[0] = 100.0f;
		outputWeights[hidden + 1] = -40.0f;

		return new ModelArrays(hidden, featureBias, featureWeights, outputWeights, 0.0f, 1.0f);
	}

    /**
     * Verifies one played move against the incremental search-state path.
     *
     * @param network network to test
     * @param fen root FEN
     * @param moveUci legal move in UCI
     * @param label assertion label prefix
     */
	private static void assertIncrementalMove(Network network, String fen, String moveUci, String label) {
		Position position = new Position(fen);
		Model.SearchState state = network.newSearchState(position, 4);
		short move = Move.parse(moveUci);
		assertTrue(position.isLegalMove(move), label + " legal move");
		Position.State undo = new Position.State();
		position.play(move, undo);
		state.movePlayed(position, move, undo, 1);
		assertEquals(
				network.predict(position).roundedCentipawns(),
				state.evaluate(position, 1),
				label + " evaluation parity");
	}

    /**
     * Verifies one null move against the incremental search-state path.
     *
     * @param network network to test
     * @param fen root FEN
     * @param label assertion label prefix
     */
	private static void assertIncrementalNullMove(Network network, String fen, String label) {
		Position position = new Position(fen);
		Model.SearchState state = network.newSearchState(position, 4);
		Position.State undo = new Position.State();
		position.playNull(undo);
		state.nullMovePlayed(1);
		assertEquals(
				network.predict(position).roundedCentipawns(),
				state.evaluate(position, 1),
				label + " evaluation parity");
	}

    /**
     * Writes a synthetic CRTK NNUE weights file.
     *
     * @param path output path
     * @param arrays model arrays
     * @throws IOException if writing fails
     */
	private static void writeWeights(Path path, ModelArrays arrays) throws IOException {
		int bytes = 4 + Integer.BYTES + Integer.BYTES + Integer.BYTES + Float.BYTES
				+ bytesForArray(arrays.featureBias)
				+ bytesForArray(arrays.featureWeights)
				+ bytesForArray(arrays.outputWeights)
				+ Float.BYTES;
		ByteBuffer buffer = ByteBuffer.allocate(bytes).order(ByteOrder.LITTLE_ENDIAN);
		buffer.put(new byte[] { 'N', 'N', 'U', 'E' });
		buffer.putInt(1);
		buffer.putInt(FeatureEncoder.FEATURE_COUNT);
		buffer.putInt(arrays.hiddenSize);
		buffer.putFloat(arrays.outputScale);
		putArray(buffer, arrays.featureBias);
		putArray(buffer, arrays.featureWeights);
		putArray(buffer, arrays.outputWeights);
		buffer.putFloat(arrays.outputBias);
		Files.write(path, buffer.array());
	}

    /**
     * Returns the byte size of one length-prefixed float array.
     *
     * @param values float values
     * @return byte count
     */
	private static int bytesForArray(float[] values) {
		return Integer.BYTES + (values.length * Float.BYTES);
	}

    /**
     * Writes one length-prefixed float array.
     *
     * @param buffer destination buffer
     * @param values values to write
     */
	private static void putArray(ByteBuffer buffer, float[] values) {
		buffer.putInt(values.length);
		for (float value : values) {
			buffer.putFloat(value);
		}
	}

    /**
     * Returns whether an int array contains a value.
     *
     * @param values values to scan
     * @param needle target value
     * @return true when present
     */
	private static boolean contains(int[] values, int needle) {
		for (int value : values) {
			if (value == needle) {
				return true;
			}
		}
		return false;
	}

    /**
     * Synthetic model arrays.
     */
	private static final class ModelArrays {
		/**
		 * Number of hidden neurons in the synthetic network.
		 */
		private final int hiddenSize;

		/**
		 * Bias values applied before feature weights.
		 */
		private final float[] featureBias;

		/**
		 * Dense feature-to-hidden weights.
		 */
		private final float[] featureWeights;

		/**
		 * Hidden-to-output weights.
		 */
		private final float[] outputWeights;

		/**
		 * Scalar output bias.
		 */
		private final float outputBias;

		/**
		 * Scalar output scale.
		 */
		private final float outputScale;

		private ModelArrays(
				int hiddenSize,
				float[] featureBias,
				float[] featureWeights,
				float[] outputWeights,
				float outputBias,
				float outputScale) {
			this.hiddenSize = hiddenSize;
			this.featureBias = featureBias;
			this.featureWeights = featureWeights;
			this.outputWeights = outputWeights;
			this.outputBias = outputBias;
			this.outputScale = outputScale;
		}
	}
}
