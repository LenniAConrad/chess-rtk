package chess.nn.nnue;

import static chess.nn.nnue.UpstreamNetwork.*;



/**
 * Dense affine layer with Stockfish's serialized shape.
 */
final class AffineLayer {

    /**
     * Input dimensions.
     */
    final int inputDimensions;

    /**
     * Padded input dimensions.
     */
    final int paddedInputDimensions;

    /**
     * Output dimensions.
     */
    final int outputDimensions;

    /**
     * Biases.
     */
    final int[] biases;

    /**
     * Row-major int8 weights.
     */
    final byte[] weights;

    /**
     * Input-major (transposed) weights for sparse propagation over wide
     * inputs, or {@code null} for narrow layers that always run dense.
     */
    private final byte[] transposed;

    /**
     * Minimum input width at which the transposed copy is built; selects the
     * feature-transformer-fed first layer, whose post-activation input is
     * mostly zeros.
     */
    private static final int SPARSE_INPUT_THRESHOLD = 128;

    /**
     * Creates a layer.
     *
     * @param inputDimensions source input dimensions
     * @param outputDimensions source output dimensions
     * @param biases bias vectors
     * @param weights row-major weights
     */
    AffineLayer(int inputDimensions, int outputDimensions, int[] biases, byte[] weights) {
        this.inputDimensions = inputDimensions;
        this.paddedInputDimensions = ceilToMultiple(inputDimensions, 32);
        this.outputDimensions = outputDimensions;
        this.biases = biases;
        this.weights = weights;
        validate();
        this.transposed = inputDimensions >= SPARSE_INPUT_THRESHOLD ? buildTransposed() : null;
    }

    /**
     * Builds the input-major weight copy used by {@link #forwardSparseInto}.
     *
     * @return transposed weights
     */
    private byte[] buildTransposed() {
        byte[] out = new byte[inputDimensions * outputDimensions];
        for (int in = 0; in < inputDimensions; in++) {
            for (int output = 0; output < outputDimensions; output++) {
                out[in * outputDimensions + output] = weights[output * paddedInputDimensions + in];
            }
        }
        return out;
    }

    /**
     * Reads a layer.
     *
     * @param cursor source cursor
     * @param inputDimensions source input dimensions
     * @param outputDimensions source output dimensions
     * @return layer
     */
    static AffineLayer read(Cursor cursor, int inputDimensions, int outputDimensions) {
        int paddedInputDimensions = ceilToMultiple(inputDimensions, 32);
        int[] biases = new int[outputDimensions];
        for (int i = 0; i < outputDimensions; i++) {
            biases[i] = cursor.readInt();
        }
        byte[] weights = cursor.readByteArray((long) outputDimensions * paddedInputDimensions);
        return new AffineLayer(inputDimensions, outputDimensions, biases, weights);
    }

    /**
     * Runs the layer.
     *
     * @param input unsigned byte-like input values in int form
     * @return output vector
     * @param output output text
     */
    void forwardInto(int[] input, int[] output) {
        System.arraycopy(biases, 0, output, 0, outputDimensions);
        int weightBase = 0;
        for (int out = 0; out < outputDimensions; out++) {
            int sum = output[out];
            for (int in = 0; in < inputDimensions; in++) {
                sum += weights[weightBase + in] * input[in];
            }
            output[out] = sum;
            weightBase += paddedInputDimensions;
        }
    }

    /**
     * Runs a single-output layer without allocating an output vector.
     *
     * @param input unsigned byte-like input values in int form
     * @return scalar output
     */
    int forwardSingle(int[] input) {
        int sum = biases[0];
        for (int in = 0; in < inputDimensions; in++) {
            sum += weights[in] * input[in];
        }
        return sum;
    }

    /**
     * Runs the layer over only the nonzero input lanes. Skipped lanes
     * contribute exactly zero to every dot product, and the surviving terms
     * are added in the same ascending-input order as {@link #forwardInto}, so
     * the result is bit-identical to a dense pass.
     *
     * @param input unsigned byte-like input values in int form
     * @param nonZero ascending indices of nonzero entries in {@code input}
     * @param nonZeroCount number of valid indices in {@code nonZero}
     * @param output output vector
     */
    void forwardSparseInto(int[] input, int[] nonZero, int nonZeroCount, int[] output) {
        System.arraycopy(biases, 0, output, 0, outputDimensions);
        byte[] columns = transposed;
        int outs = outputDimensions;
        for (int n = 0; n < nonZeroCount; n++) {
            int in = nonZero[n];
            int value = input[in];
            int base = in * outs;
            for (int out = 0; out < outs; out++) {
                output[out] += columns[base + out] * value;
            }
        }
    }

    /**
     * Validates layer shapes.
     */
    private void validate() {
        requireLength(biases, outputDimensions, "affine biases");
        requireLength(weights, (long) outputDimensions * paddedInputDimensions, "affine weights");
    }

    /**
     * Rounds up to a multiple.
     *
     * @param value candidate value
     * @param multiple alignment multiple
     * @return rounded value
     */
    private static int ceilToMultiple(int value, int multiple) {
        return ((value + multiple - 1) / multiple) * multiple;
    }

    /**
     * Validates array length.
     *
     * @param values array
     * @param expected expected length
     * @param label display label
     */
    private void requireLength(int[] values, long expected, String label) {
        if (values == null || values.length != checkedLength(expected, label)) {
            throw new IllegalArgumentException(label + " length mismatch.");
        }
    }

    /**
     * Validates array length.
     *
     * @param values array
     * @param expected expected length
     * @param label display label
     */
    private void requireLength(byte[] values, long expected, String label) {
        if (values == null || values.length != checkedLength(expected, label)) {
            throw new IllegalArgumentException(label + " length mismatch.");
        }
    }
}
