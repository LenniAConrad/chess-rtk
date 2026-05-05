package chess.nn.nnue;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import chess.core.Move;
import chess.core.Piece;
import chess.core.Position;
import chess.gpu.BackendNames;

/**
 * Pure-Java NNUE evaluator with a HalfKP-style sparse input transformer.
 *
 * <p>
 * See the package documentation for the architecture overview, feature layout,
 * and the relationship between the compact CRTK format and supported Stockfish
 * loaders. This class is the compact CRTK-format implementation with public
 * accumulator and incremental-search APIs.
 * </p>
 */
@SuppressWarnings("java:S3398")
public final class Network implements AutoCloseable {

    /**
     * CPU backend identifier.
     */
    public static final String BACKEND = BackendNames.CPU;

    /**
     * CRTK NNUE binary magic.
     */
    private static final byte[] MAGIC = new byte[] { 'N', 'N', 'U', 'E' };

    /**
     * Supported CRTK NNUE binary version.
     */
    private static final int VERSION = 1;

    /**
     * Parsed network weights.
     */
    private final Weights weights;

    /**
     * Creates a network around validated weights.
     *
     * @param weights parsed weights
     */
    private Network(Weights weights) {
        this.weights = weights;
    }

    /**
     * Loads a CRTK NNUE {@code .bin} or {@code .nnue} weights file.
     *
     * @param path path to the weights file
     * @return loaded network
     * @throws IOException if the file cannot be read or parsed
     */
    public static Network load(Path path) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("path == null");
        }
        return new Network(Weights.load(path));
    }

    /**
     * Builds a network directly from arrays.
     *
     * @param hiddenSize number of accumulator hidden units
     * @param featureBias hidden bias vector
     * @param featureWeights feature-major transformer weights
     * @param outputWeights output weights for {@code [us, them]} accumulator halves
     * @param outputBias output bias before scaling
     * @param outputScale multiplier applied to the raw output
     * @return network instance
     */
    public static Network create(
            int hiddenSize,
            float[] featureBias,
            float[] featureWeights,
            float[] outputWeights,
            float outputBias,
            float outputScale) {
        return new Network(new Weights(
                hiddenSize,
                copy(featureBias, "featureBias"),
                copy(featureWeights, "featureWeights"),
                copy(outputWeights, "outputWeights"),
                outputBias,
                outputScale));
    }

    /**
     * Returns basic network metadata.
     *
     * @return network metadata
     */
    public Info info() {
        return new Info(FeatureEncoder.FEATURE_COUNT, weights.hiddenSize, weights.parameterCount());
    }

    /**
     * Returns the active backend identifier.
     *
     * @return backend name
     */
    public String backendName() {
        return BACKEND;
    }

    /**
     * Creates a fresh accumulator initialized to network biases.
     *
     * @return accumulator
     */
    public Accumulator newAccumulator() {
        return new Accumulator(weights);
    }

    /**
     * Creates and rebuilds an accumulator for a position.
     *
     * @param position position to encode
     * @return initialized accumulator
     */
    public Accumulator newAccumulator(Position position) {
        return newAccumulator().refresh(position);
    }

    /**
     * Opens per-search incremental state backed by accumulator deltas.
     *
     * @param root root position at ply 0
     * @param searchPlies maximum ply count the search may reach
     * @return incremental search state
     */
    public Model.SearchState newSearchState(Position root, int searchPlies) {
        if (root == null) {
            throw new IllegalArgumentException("root == null");
        }
        if (searchPlies <= 0) {
            throw new IllegalArgumentException("searchPlies must be positive");
        }
        return new IncrementalState(root, searchPlies);
    }

    /**
     * Evaluates a position by rebuilding accumulators from scratch.
     *
     * @param position position to evaluate
     * @return network prediction
     */
    public Prediction predict(Position position) {
        if (position == null) {
            throw new IllegalArgumentException("position == null");
        }
        return predict(newAccumulator(position), position.isWhiteToMove());
    }

    /**
     * Evaluates a prepared accumulator.
     *
     * @param accumulator accumulator created by this network
     * @param whiteToMove true when White is the side to move
     * @return network prediction
     */
    public Prediction predict(Accumulator accumulator, boolean whiteToMove) {
        if (accumulator == null) {
            throw new IllegalArgumentException("accumulator == null");
        }
        if (accumulator.weights != weights) {
            throw new IllegalArgumentException("Accumulator belongs to a different NNUE network.");
        }

        float[] us = accumulator.values(whiteToMove);
        float[] them = accumulator.values(!whiteToMove);
        float raw = weights.outputBias;
        int hidden = weights.hiddenSize;
        for (int i = 0; i < hidden; i++) {
            raw += weights.outputWeights[i] * clippedRelu(us[i]);
            raw += weights.outputWeights[hidden + i] * clippedRelu(them[i]);
        }
        return new Prediction(raw * weights.outputScale);
    }

    /**
     * Releases backend resources. The pure-Java NNUE backend has none.
     */
    @Override
    public void close() {
        // no native resources
    }

    /**
     * Per-search incremental state backed by accumulator deltas.
     */
    private final class IncrementalState implements Model.SearchState {

        /**
         * Dedicated accumulator buffers indexed by ply.
         */
        private final Accumulator[] buffers;

        /**
         * Active accumulator views indexed by ply.
         */
        private final Accumulator[] active;

        /**
         * Creates incremental state rooted at one position.
         *
         * @param root root position
         * @param searchPlies maximum ply count
         */
        private IncrementalState(Position root, int searchPlies) {
            this.buffers = new Accumulator[searchPlies];
            this.active = new Accumulator[searchPlies];
            for (int ply = 0; ply < searchPlies; ply++) {
                buffers[ply] = newAccumulator();
            }
            buffers[0].refresh(root);
            active[0] = buffers[0];
        }

	        /**
	         * Updates the child ply accumulator after a normal move.
	         *
	         * @param position child position after the move
	         * @param move encoded move that was played
	         * @param state undo state filled by the move application
	         * @param ply child ply from the root
	         */
	        @Override
	        public void movePlayed(Position position, short move, Position.State state, int ply) {
	            Accumulator child = buffers[ply];
	            copyAccumulator(child, active[ply - 1]);
            active[ply] = child;
            updatePerspective(child, position, move, state, true);
	            updatePerspective(child, position, move, state, false);
	        }

	        /**
	         * Reuses the parent accumulator for a null-move child ply.
	         *
	         * @param ply child ply from the root
	         */
	        @Override
	        public void nullMovePlayed(int ply) {
	            active[ply] = active[ply - 1];
	        }

	        /**
	         * Evaluates the active accumulator at one search ply.
	         *
	         * @param position current position
	         * @param ply current ply from the root
	         * @return centipawn score from the side-to-move perspective
	         */
	        @Override
	        public int evaluate(Position position, int ply) {
	            return predict(active[ply], position.isWhiteToMove()).roundedCentipawns();
        }

        /**
         * Updates one perspective after a played move.
         *
         * @param accumulator child accumulator
         * @param position child position
         * @param move encoded move
         * @param state undo state filled by the move application
         * @param whitePerspective true for White's perspective
         */
        private void updatePerspective(
                Accumulator accumulator,
                Position position,
                short move,
                Position.State state,
                boolean whitePerspective) {
            int moving = state.movingPiece();
            if (moving == kingPieceIndex(whitePerspective)) {
                refreshPerspective(accumulator, position, whitePerspective);
                return;
            }

            updatePieceMove(
                    accumulator,
                    position,
                    whitePerspective,
                    moving,
                    Move.getFromIndex(move),
                    state.actualToSquare(),
                    promotedPieceIndex(moving, Move.getPromotion(move)));
            if (state.capture()) {
                updatePieceRemoval(
                        accumulator,
                        position,
                        whitePerspective,
                        state.capturedPiece(),
                        state.capturedSquare());
            }
            if (state.castle()) {
                updatePieceMove(
                        accumulator,
                        position,
                        whitePerspective,
                        state.rookPiece(),
                        state.rookFromSquare(),
                        state.rookToSquare(),
                        state.rookPiece());
            }
        }
    }

    /**
     * Copies one accumulator into another.
     *
     * @param target destination accumulator
     * @param source source accumulator
     */
    private static void copyAccumulator(Accumulator target, Accumulator source) {
        System.arraycopy(source.values(true), 0, target.values(true), 0, source.hiddenSize());
        System.arraycopy(source.values(false), 0, target.values(false), 0, source.hiddenSize());
    }

    /**
     * Rebuilds one perspective accumulator from the child position.
     *
     * @param accumulator accumulator to update
     * @param position child position
     * @param whitePerspective true for White's perspective
     */
    private void refreshPerspective(Accumulator accumulator, Position position, boolean whitePerspective) {
        System.arraycopy(weights.featureBias, 0, accumulator.values(whitePerspective), 0, weights.hiddenSize);
        accumulator.addFeatures(whitePerspective, FeatureEncoder.activeFeatures(position, whitePerspective));
    }

    /**
     * Applies one moved-piece feature delta for one perspective.
     *
     * @param accumulator accumulator to update
     * @param position child position
     * @param whitePerspective true for White's perspective
     * @param sourcePiece moving piece before the move
     * @param fromSquare origin square in {@link Position} order
     * @param toSquare destination square in {@link Position} order
     * @param targetPiece placed piece after the move
     */
    private static void updatePieceMove(
            Accumulator accumulator,
            Position position,
            boolean whitePerspective,
            int sourcePiece,
            int fromSquare,
            int toSquare,
            int targetPiece) {
        int kingSquare = FeatureEncoder.perspectiveKingSquare(position, whitePerspective);
        int removeFeature = featureIndex(whitePerspective, kingSquare, sourcePiece, fromSquare);
        int addFeature = featureIndex(whitePerspective, kingSquare, targetPiece, toSquare);
        applyFeatureDelta(accumulator, whitePerspective, removeFeature, addFeature);
    }

    /**
     * Applies one removed-piece feature delta for one perspective.
     *
     * @param accumulator accumulator to update
     * @param position child position
     * @param whitePerspective true for White's perspective
     * @param piece removed piece
     * @param square removed-piece square in {@link Position} order
     */
    private static void updatePieceRemoval(
            Accumulator accumulator,
            Position position,
            boolean whitePerspective,
            int piece,
            int square) {
        int kingSquare = FeatureEncoder.perspectiveKingSquare(position, whitePerspective);
        int feature = featureIndex(whitePerspective, kingSquare, piece, square);
        applyFeatureDelta(accumulator, whitePerspective, feature, -1);
    }

    /**
     * Applies a sparse feature delta to one perspective.
     *
     * @param accumulator accumulator to update
     * @param whitePerspective true for White's perspective
     * @param removeFeature feature to remove, or {@code -1}
     * @param addFeature feature to add, or {@code -1}
     */
    private static void applyFeatureDelta(
            Accumulator accumulator,
            boolean whitePerspective,
            int removeFeature,
            int addFeature) {
        if (removeFeature >= 0 && addFeature >= 0) {
            accumulator.replaceFeature(whitePerspective, removeFeature, addFeature);
        } else if (removeFeature >= 0) {
            accumulator.removeFeature(whitePerspective, removeFeature);
        } else if (addFeature >= 0) {
            accumulator.addFeature(whitePerspective, addFeature);
        }
    }

    /**
     * Returns the sparse feature index for one piece and square.
     *
     * @param whitePerspective true for White's perspective
     * @param kingSquare perspective king square in NNUE order
     * @param pieceIndex internal piece index
     * @param square square in {@link Position} order
     * @return feature index, or {@code -1} when the piece is not represented
     */
    private static int featureIndex(boolean whitePerspective, int kingSquare, int pieceIndex, int square) {
        byte piece = pieceCode(pieceIndex);
        int plane = FeatureEncoder.piecePlane(piece, whitePerspective);
        if (plane < 0) {
            return -1;
        }
        int orientedSquare = FeatureEncoder.orientSquare(
                FeatureEncoder.squareFromPositionIndex(square),
                whitePerspective);
        return FeatureEncoder.encodeFeature(kingSquare, plane, orientedSquare);
    }

    /**
     * Returns the internal king piece index for one perspective.
     *
     * @param whitePerspective true for White
     * @return internal king piece index
     */
    private static int kingPieceIndex(boolean whitePerspective) {
        return whitePerspective ? Position.WHITE_KING : Position.BLACK_KING;
    }

    /**
     * Returns the placed piece index after a move.
     *
     * @param movingPiece moving piece before the move
     * @param promotion promotion code
     * @return placed internal piece index
     */
    private static int promotedPieceIndex(int movingPiece, int promotion) {
        if (promotion == 0) {
            return movingPiece;
        }
        boolean white = movingPiece < Position.BLACK_PAWN;
        return switch (promotion) {
            case 1 -> white ? Position.WHITE_KNIGHT : Position.BLACK_KNIGHT;
            case 2 -> white ? Position.WHITE_BISHOP : Position.BLACK_BISHOP;
            case 3 -> white ? Position.WHITE_ROOK : Position.BLACK_ROOK;
            case 4 -> white ? Position.WHITE_QUEEN : Position.BLACK_QUEEN;
            default -> movingPiece;
        };
    }

    /**
     * Maps an internal piece index to the repository piece code.
     *
     * @param pieceIndex internal piece index
     * @return repository piece code, or {@link Piece#EMPTY}
     */
    private static byte pieceCode(int pieceIndex) {
        return switch (pieceIndex) {
            case Position.WHITE_PAWN -> Piece.WHITE_PAWN;
            case Position.WHITE_KNIGHT -> Piece.WHITE_KNIGHT;
            case Position.WHITE_BISHOP -> Piece.WHITE_BISHOP;
            case Position.WHITE_ROOK -> Piece.WHITE_ROOK;
            case Position.WHITE_QUEEN -> Piece.WHITE_QUEEN;
            case Position.WHITE_KING -> Piece.WHITE_KING;
            case Position.BLACK_PAWN -> Piece.BLACK_PAWN;
            case Position.BLACK_KNIGHT -> Piece.BLACK_KNIGHT;
            case Position.BLACK_BISHOP -> Piece.BLACK_BISHOP;
            case Position.BLACK_ROOK -> Piece.BLACK_ROOK;
            case Position.BLACK_QUEEN -> Piece.BLACK_QUEEN;
            case Position.BLACK_KING -> Piece.BLACK_KING;
            default -> Piece.EMPTY;
        };
    }

    /**
     * Clipped ReLU activation used by the output layer.
     *
     * @param value accumulator value
     * @return value clipped to {@code [0,1]}
     */
    private static float clippedRelu(float value) {
        if (value <= 0.0f) {
            return 0.0f;
        }
        if (value >= 1.0f) {
            return 1.0f;
        }
        return value;
    }

    /**
     * Copies and validates a float array argument.
     *
     * @param values source values
     * @param label argument label
     * @return copied array
     */
    private static float[] copy(float[] values, String label) {
        if (values == null) {
            throw new IllegalArgumentException(label + " == null");
        }
        return Arrays.copyOf(values, values.length);
    }

    /**
     * Model metadata extracted from the weights.
     *
     * @param inputFeatures sparse feature count
     * @param hiddenSize accumulator hidden unit count
     * @param parameterCount total parameter count
     */
    public record Info(
        /**
         * Stores the input feature count.
         */
        int inputFeatures,
        /**
         * Stores the hidden size.
         */
        int hiddenSize,
        /**
         * Stores the parameter count.
         */
        long parameterCount
    ) {
    }

    /**
     * Inference result for one position.
     *
     * @param centipawns centipawn score from the side-to-move perspective
     */
    public record Prediction(
        /**
         * Stores the centipawn score.
         */
        float centipawns
    ) {

        /**
         * Returns the rounded centipawn score.
         *
         * @return rounded centipawns
         */
        public int roundedCentipawns() {
            return Math.round(centipawns);
        }

        /**
         * Returns the score in pawns.
         *
         * @return pawn score
         */
        public float pawns() {
            return centipawns / 100.0f;
        }
    }

    /**
     * Parsed NNUE weights.
     */
    static final class Weights {

        /**
         * Hidden accumulator size.
         */
        final int hiddenSize;

        /**
         * Bias vector for the feature transformer.
         */
        final float[] featureBias;

        /**
         * Feature-major transformer weights.
         */
        final float[] featureWeights;

        /**
         * Output weights for side-to-move accumulator followed by opponent accumulator.
         */
        final float[] outputWeights;

        /**
         * Output bias before scaling.
         */
        final float outputBias;

        /**
         * Output multiplier.
         */
        final float outputScale;

        /**
         * Packs validated arrays.
         *
         * @param hiddenSize hidden size
         * @param featureBias hidden bias vector
         * @param featureWeights feature-major transformer weights
         * @param outputWeights output weights
         * @param outputBias output bias
         * @param outputScale output scale
         */
        Weights(
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
            validate();
        }

        /**
         * Loads weights from disk.
         *
         * @param path weights path
         * @return parsed weights
         * @throws IOException if parsing fails
         */
        static Weights load(Path path) throws IOException {
            try {
                byte[] bytes = Files.readAllBytes(path);
                ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

                byte[] magic = new byte[4];
                buf.get(magic);
                if (!matchesMagic(magic)) {
                    throw new IOException("Invalid NNUE weights file (bad magic).");
                }

                int version = buf.getInt();
                if (version != VERSION) {
                    throw new IOException("Unsupported NNUE weights version: " + version);
                }

                int featureCount = buf.getInt();
                int hiddenSize = buf.getInt();
                float outputScale = buf.getFloat();
                if (featureCount != FeatureEncoder.FEATURE_COUNT) {
                    throw new IOException("NNUE feature count mismatch: " + featureCount
                            + " vs expected " + FeatureEncoder.FEATURE_COUNT);
                }

                float[] featureBias = readFloatArray(buf);
                float[] featureWeights = readFloatArray(buf);
                float[] outputWeights = readFloatArray(buf);
                float outputBias = buf.getFloat();

                if (buf.hasRemaining()) {
                    throw new IOException("Unexpected bytes at end of NNUE weights file.");
                }

                return new Weights(hiddenSize, featureBias, featureWeights, outputWeights, outputBias, outputScale);
            } catch (BufferUnderflowException | IllegalArgumentException ex) {
                throw new IOException("Invalid NNUE weights file.", ex);
            }
        }

        /**
         * Adds or subtracts one feature's transformer weights into a target buffer.
         *
         * @param target accumulator buffer
         * @param feature sparse feature index
         * @param sign {@code +1} to add, {@code -1} to remove
         */
        void addFeature(float[] target, int feature, float sign) {
            if (feature < 0 || feature >= FeatureEncoder.FEATURE_COUNT) {
                throw new IllegalArgumentException("feature out of range: " + feature);
            }
            int base = feature * hiddenSize;
            for (int i = 0; i < hiddenSize; i++) {
                target[i] += sign * featureWeights[base + i];
            }
        }

        /**
         * Returns the total parameter count.
         *
         * @return parameter count
         */
        long parameterCount() {
            return featureBias.length + (long) featureWeights.length + outputWeights.length + 1L;
        }

        /**
         * Validates layer shapes and scalar values.
         */
        private void validate() {
            if (hiddenSize <= 0) {
                throw new IllegalArgumentException("hiddenSize must be positive.");
            }
            requireLength(featureBias, hiddenSize, "featureBias");
            requireLength(featureWeights, FeatureEncoder.FEATURE_COUNT * (long) hiddenSize, "featureWeights");
            requireLength(outputWeights, hiddenSize * 2L, "outputWeights");
            if (!Float.isFinite(outputBias)) {
                throw new IllegalArgumentException("outputBias must be finite.");
            }
            if (!Float.isFinite(outputScale)) {
                throw new IllegalArgumentException("outputScale must be finite.");
            }
        }

        /**
         * Validates an array length.
         *
         * @param values array
         * @param expected expected length
         * @param label label for errors
         */
        private static void requireLength(float[] values, long expected, String label) {
            if (values == null) {
                throw new IllegalArgumentException(label + " == null");
            }
            if (expected > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(label + " expected length is too large: " + expected);
            }
            if (values.length != (int) expected) {
                throw new IllegalArgumentException(label + " length mismatch: " + values.length
                        + " vs expected " + expected);
            }
            for (float value : values) {
                if (!Float.isFinite(value)) {
                    throw new IllegalArgumentException(label + " contains non-finite values.");
                }
            }
        }

        /**
         * Reads a length-prefixed float array.
         *
         * @param buf source buffer
         * @return decoded array
         * @throws IOException if the length is invalid
         */
        private static float[] readFloatArray(ByteBuffer buf) throws IOException {
            int size = buf.getInt();
            if (size < 0 || size > (buf.remaining() / Float.BYTES)) {
                throw new IOException("Invalid NNUE float array length: " + size);
            }
            float[] out = new float[size];
            for (int i = 0; i < size; i++) {
                out[i] = buf.getFloat();
            }
            return out;
        }

        /**
         * Checks the file magic.
         *
         * @param actual magic read from file
         * @return true if it matches this format
         */
        private static boolean matchesMagic(byte[] actual) {
            if (actual.length != MAGIC.length) {
                return false;
            }
            for (int i = 0; i < MAGIC.length; i++) {
                if (actual[i] != MAGIC[i]) {
                    return false;
                }
            }
            return true;
        }
    }
}
