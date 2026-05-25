package chess.nn.lc0.cnn;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

import chess.nn.lc0.cnn.Network.DebugValue;
import chess.nn.lc0.cnn.Network.Prediction;

/**
 * Parsed weight tensors for the CPU backend.
 */
final class Weights {

    /**
     * Number of input channels.
     */
    final int inputChannels;

    /**
     * Number of channels in the residual trunk.
     */
    final int trunkChannels;

    /**
     * Number of policy channels before mapping to moves.
     */
    final int policyChannels;

    /**
     * Number of channels entering the value head.
     */
    final int valueChannels;

    /**
     * Mapping from raw policy planes to LC0 move encoding.
     */
    final int[] policyMap;

    /**
     * Total number of parameters decoded from the weights file.
     */
    final long parameterCount;

    /**
     * First convolutional layer.
     */
    final ConvLayer inputLayer;

    /**
     * Residual blocks forming the trunk.
     */
    final List<ResidualBlock> blocks;

    /**
     * Stem convolution before the policy head.
     */
    final ConvLayer policyStem;

    /**
     * Final convolution in the policy head.
     */
    final ConvLayer policyOutput;

    /**
     * Convolution feeding the value head.
     */
    final ConvLayer valueConv;

    /**
     * First dense layer in the value head.
     */
    final DenseLayer valueFc1;

    /**
     * Output dense layer in the value head.
     */
    final DenseLayer valueFc2;

    /**
     * Packs all decoded tensors into a single object.
     * @param b second value
     */
    private Weights(Builder b) {
        this.inputChannels = b.inputChannels;
        this.trunkChannels = b.trunkChannels;
        this.policyChannels = b.policyChannels;
        this.valueChannels = b.valueChannels;
        this.policyMap = b.policyMap;
        this.parameterCount = b.parameterCount;
        this.inputLayer = b.inputLayer;
        this.blocks = b.blocks;
        this.policyStem = b.policyStem;
        this.policyOutput = b.policyOutput;
        this.valueConv = b.valueConv;
        this.valueFc1 = b.valueFc1;
        this.valueFc2 = b.valueFc2;
    }

    /**
     * Internal build state used while parsing a weights file.
     */
    private static final class Builder {

        /**
         * Number of input channels reported by the weights file.
         * Copied into the built {@link Weights} instance.
         */
        int inputChannels;

        /**
         * Number of channels in the residual trunk.
         * Copied into the built {@link Weights} instance.
         */
        int trunkChannels;

        /**
         * Number of channels feeding the policy head.
         * Copied into the built {@link Weights} instance.
         */
        int policyChannels;

        /**
         * Number of channels feeding the value head.
         * Copied into the built {@link Weights} instance.
         */
        int valueChannels;

        /**
         * Mapping from raw policy planes to LC0 move indices.
         * Populated from the weights file.
         */
        int[] policyMap;

        /**
         * Total parameter count computed during parsing.
         * Propagated to the built {@link Weights}.
         */
        long parameterCount;

        /**
         * Parsed input convolution layer descriptor.
         * Assigned once during file decoding.
         */
        ConvLayer inputLayer;

        /**
         * Parsed residual blocks in the trunk.
         * Preserves original order from the weights file.
         */
        List<ResidualBlock> blocks;

        /**
         * Convolutional stem for the policy head.
         * Assigned once during file decoding.
         */
        ConvLayer policyStem;

        /**
         * Final convolution producing policy logits.
         * Assigned once during file decoding.
         */
        ConvLayer policyOutput;

        /**
         * Convolutional stem for the value head.
         * Assigned once during file decoding.
         */
        ConvLayer valueConv;

        /**
         * First dense layer in the value head.
         * Assigned once during file decoding.
         */
        DenseLayer valueFc1;

        /**
         * Second dense layer in the value head.
         * Assigned once during file decoding.
         */
        DenseLayer valueFc2;
    }

    /**
     * Reads a ChessRTK LC0 CNN weights file and builds all layer descriptors.
     *
     * @param path path to the weights file
     * @return decoded {@link Weights}
     * @throws IOException on read/parse errors
     */
    static Weights load(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

        byte[] magic = new byte[4];
        buf.get(magic);
        if (magic[0] != 'L' || magic[1] != 'C' || magic[2] != '0' || magic[3] != 'J') {
            throw new IOException("Invalid weights file (bad magic).");
        }
        int version = buf.getInt();
        if (version != 1) {
            throw new IOException("Unsupported weights version: " + version);
        }

        int inputChannels = buf.getInt();
        int trunkChannels = buf.getInt();
        int residualBlocks = buf.getInt();
        int policyChannels = buf.getInt();
        int valueChannels = buf.getInt();
        int valueHidden = buf.getInt();
        int policyMapLength = buf.getInt();
        int wdlOutputs = buf.getInt();
        if (wdlOutputs != 3) {
            throw new IOException("Expected WDL outputs = 3 but found " + wdlOutputs);
        }

        ConvLayer inputLayer = readConv(buf);
        long params = countParams(inputLayer);
        List<ResidualBlock> blocks = new ArrayList<>(residualBlocks);
        for (int i = 0; i < residualBlocks; i++) {
            ConvLayer conv1 = readConv(buf);
            ConvLayer conv2 = readConv(buf);
            SeUnit se = readSeUnit(buf, conv2.outChannels);
            params += countParams(conv1) + countParams(conv2) + countParams(se);
            blocks.add(new ResidualBlock(conv1, conv2, se));
        }

        ConvLayer policyStem = readConv(buf);
        ConvLayer policyOut = readConv(buf);
        ConvLayer valueConv = readConv(buf);
        DenseLayer valueFc1 = readDense(buf, valueHidden);
        DenseLayer valueFc2 = readDense(buf, wdlOutputs);
        params += countParams(policyStem) + countParams(policyOut) + countParams(valueConv);
        params += countParams(valueFc1) + countParams(valueFc2);

        int mapEntries = buf.getInt();
        if (mapEntries != policyMapLength) {
            throw new IOException("Policy map length mismatch.");
        }
        int[] policyMap = new int[mapEntries];
        for (int i = 0; i < mapEntries; i++) {
            policyMap[i] = buf.getInt();
        }

        if (buf.hasRemaining()) {
            throw new IOException("Unexpected bytes at end of weights file.");
        }

        Builder b = new Builder();
        b.inputChannels = inputChannels;
        b.trunkChannels = trunkChannels;
        b.policyChannels = policyChannels;
        b.valueChannels = valueChannels;
        b.policyMap = policyMap;
        b.parameterCount = params;
        b.inputLayer = inputLayer;
        b.blocks = blocks;
        b.policyStem = policyStem;
        b.policyOutput = policyOut;
        b.valueConv = valueConv;
        b.valueFc1 = valueFc1;
        b.valueFc2 = valueFc2;
        return new Weights(b);
    }

    /**
     * Returns total parameters for a convolutional layer.
     *
     * @param layer convolutional layer descriptor
     * @return total parameters (weights + bias)
     */
    private static long countParams(ConvLayer layer) {
        return (layer == null) ? 0L : ((long) layer.weights.length + (long) layer.bias.length);
    }

    /**
     * Returns total parameters for a dense layer.
     *
     * @param layer dense layer descriptor
     * @return total parameters (weights + bias)
     */
    private static long countParams(DenseLayer layer) {
        return (layer == null) ? 0L : ((long) layer.weights.length + (long) layer.bias.length);
    }

    /**
     * Returns total parameters for an SE unit (or 0 if absent).
     *
     * @param se optional squeeze-and-excitation unit
     * @return total parameters for the unit (or 0 when {@code null})
     */
    private static long countParams(SeUnit se) {
        if (se == null) {
            return 0L;
        }
        return (long) se.w1.length + (long) se.b1.length + se.w2.length + se.b2.length;
    }

    /**
     * Reads one convolutional layer from the buffer.
     *
     * @param buf source buffer
     * @return convolutional layer descriptor
     */
    private static ConvLayer readConv(ByteBuffer buf) {
        int out = buf.getInt();
        int in = buf.getInt();
        int kernel = buf.getInt();
        float[] weights = readFloatArray(buf);
        float[] bias = readFloatArray(buf);
        return new ConvLayer(in, out, kernel, weights, bias);
    }

    /**
     * Reads a dense layer and validates the output dimension.
     *
     * @param buf         source buffer
     * @param expectedOut expected output dimension
     * @return dense layer descriptor
     */
    private static DenseLayer readDense(ByteBuffer buf, int expectedOut) {
        int out = buf.getInt();
        int in = buf.getInt();
        float[] weights = readFloatArray(buf);
        float[] bias = readFloatArray(buf);
        if (out != expectedOut) {
            throw new IllegalStateException("Dense output mismatch: " + out + " vs expected " + expectedOut);
        }
        return new DenseLayer(in, out, weights, bias);
    }

    /**
     * Reads an optional SE unit for the provided channel count.
     *
     * @param buf      source buffer
     * @param channels expected channel count
     * @return populated SE unit or {@code null} when absent
     */
    private static SeUnit readSeUnit(ByteBuffer buf, int channels) {
        boolean present = buf.get() != 0;
        if (!present) {
            return null;
        }
        int hidden = buf.getInt();
        int expectedChannels = buf.getInt();
        if (expectedChannels != channels) {
            throw new IllegalStateException("SE unit channel mismatch.");
        }
        float[] w1 = readFloatArray(buf);
        float[] b1 = readFloatArray(buf);
        float[] w2 = readFloatArray(buf);
        float[] b2 = readFloatArray(buf);
        return new SeUnit(channels, hidden, w1, b1, w2, b2);
    }

    /**
     * Reads a length-prefixed float array.
     *
     * @param buf source buffer
     * @return decoded float array
     */
    private static float[] readFloatArray(ByteBuffer buf) {
        int size = buf.getInt();
        float[] arr = new float[size];
        for (int i = 0; i < size; i++) {
            arr[i] = buf.getFloat();
        }
        return arr;
    }
}
