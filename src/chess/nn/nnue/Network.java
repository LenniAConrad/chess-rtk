package chess.nn.nnue;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import chess.core.Position;
import chess.gpu.BackendNames;

/**
 * Pure-Java NNUE evaluator with a HalfKP-style sparse input transformer.
 *
 * <p>
 * The implemented architecture is intentionally compact:
 * </p>
 *
 * <pre>
 * sparse HalfKP features -> perspective accumulators -> clipped ReLU
 * concatenated side-to-move/opponent accumulators -> linear centipawn output
 * </pre>
 *
 * <p>
 * The accumulator can be rebuilt from a {@link Position} or updated by applying
 * sparse feature deltas, which is the efficient-update part of NNUE.
 * </p>
 */
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
            return (long) featureBias.length + (long) featureWeights.length
                    + (long) outputWeights.length + 1L;
        }

        /**
         * Validates layer shapes and scalar values.
         */
        private void validate() {
            if (hiddenSize <= 0) {
                throw new IllegalArgumentException("hiddenSize must be positive.");
            }
            requireLength(featureBias, hiddenSize, "featureBias");
            requireLength(featureWeights, (long) FeatureEncoder.FEATURE_COUNT * hiddenSize, "featureWeights");
            requireLength(outputWeights, (long) hiddenSize * 2L, "outputWeights");
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
