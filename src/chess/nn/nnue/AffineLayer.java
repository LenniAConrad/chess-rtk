package chess.nn.nnue;

import static chess.nn.nnue.UpstreamNetwork.*;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import chess.core.Position;
import chess.nn.nnue.UpstreamNetwork.Size;
import chess.nn.nnue.UpstreamNetwork.Variant;
import utility.Numbers;

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
     * Creates a layer.
     *
     * @param inputDimensions input dimensions
     * @param outputDimensions output dimensions
     * @param biases biases
     * @param weights row-major weights
     */
    AffineLayer(int inputDimensions, int outputDimensions, int[] biases, byte[] weights) {
        this.inputDimensions = inputDimensions;
        this.paddedInputDimensions = ceilToMultiple(inputDimensions, 32);
        this.outputDimensions = outputDimensions;
        this.biases = biases;
        this.weights = weights;
        validate();
    }

    /**
     * Reads a layer.
     *
     * @param cursor source cursor
     * @param inputDimensions input dimensions
     * @param outputDimensions output dimensions
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
     * Validates layer shapes.
     */
    private void validate() {
        requireLength(biases, outputDimensions, "affine biases");
        requireLength(weights, (long) outputDimensions * paddedInputDimensions, "affine weights");
    }

    /**
     * Rounds up to a multiple.
     *
     * @param value value
     * @param multiple multiple
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
     * @param label label
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
     * @param label label
     */
    private void requireLength(byte[] values, long expected, String label) {
        if (values == null || values.length != checkedLength(expected, label)) {
            throw new IllegalArgumentException(label + " length mismatch.");
        }
    }
}
