package chess.nn.nnue;

import static chess.nn.nnue.UpstreamNetwork.*;


import utility.Numbers;

/**
 * One Stockfish layer stack.
 */
final class Architecture {

    /**
     * Layout.
     */
    final Layout layout;

    /**
     * FC0 layer.
     */
    final AffineLayer fc0;

    /**
     * FC1 layer.
     */
    final AffineLayer fc1;

    /**
     * FC2 layer.
     */
    final AffineLayer fc2;

    /**
     * FC0 outputs.
     */
    final int fc0OutputDimensions;

    /**
     * FC1 outputs.
     */
    final int fc1OutputDimensions;

    /**
     * Creates an architecture.
     *
     * @param layout layout
     * @param fc0 first layer
     * @param fc1 second layer
     * @param fc2 output layer
     */
    Architecture(Layout layout, AffineLayer fc0, AffineLayer fc1, AffineLayer fc2) {
        this.layout = layout;
        this.fc0 = fc0;
        this.fc1 = fc1;
        this.fc2 = fc2;
        this.fc0OutputDimensions = layout.l2 + 1;
        this.fc1OutputDimensions = layout.l3;
    }

    /**
     * Creates reusable propagation scratch space for this architecture.
     *
     * @return dense-layer workspace
     */
    Scratch newScratch() {
        return new Scratch(layout);
    }

    /**
     * Reads a layer stack.
     *
     * @param cursor source cursor
     * @param layout layout
     * @return layer stack
     */
    static Architecture read(Cursor cursor, Layout layout) {
        AffineLayer fc0 = AffineLayer.read(cursor, layout.transformedDimensions, layout.l2 + 1);
        AffineLayer fc1 = AffineLayer.read(cursor, layout.l2 * 2, layout.l3);
        AffineLayer fc2 = AffineLayer.read(cursor, layout.l3, 1);
        return new Architecture(layout, fc0, fc1, fc2);
    }

    /**
     * Propagates transformed features through this layer stack.
     *
     * @param transformedFeatures transformed features
     * @return scaled Stockfish positional output
     */
    int propagate(int[] transformedFeatures) {
        return propagate(transformedFeatures, newScratch());
    }

    /**
     * Propagates transformed features through this layer stack using reusable
     * workspace.
     *
     * @param transformedFeatures transformed features
     * @param scratch reusable dense-layer workspace
     * @return scaled Stockfish positional output
     */
    int propagate(int[] transformedFeatures, Scratch scratch) {
        fc0.forwardInto(transformedFeatures, scratch.fc0Out);
        return propagateTail(scratch);
    }

    /**
     * Propagates transformed features using only their nonzero lanes for the
     * wide first layer; bit-identical to
     * {@link #propagate(int[], Scratch)}.
     *
     * @param transformedFeatures transformed features
     * @param nonZero ascending indices of nonzero transformed lanes
     * @param nonZeroCount number of valid indices in {@code nonZero}
     * @param scratch reusable dense-layer workspace
     * @return scaled Stockfish positional output
     */
    int propagate(int[] transformedFeatures, int[] nonZero, int nonZeroCount, Scratch scratch) {
        fc0.forwardSparseInto(transformedFeatures, nonZero, nonZeroCount, scratch.fc0Out);
        return propagateTail(scratch);
    }

    /**
     * Runs the activation and remaining dense layers after FC0.
     *
     * @param scratch workspace with {@code fc0Out} already populated
     * @return scaled Stockfish positional output
     */
    private int propagateTail(Scratch scratch) {
        for (int i = 0; i < layout.l2; i++) {
            int value = scratch.fc0Out[i];
            scratch.fc1Input[i] = sqrClippedRelu(value);
            scratch.fc1Input[layout.l2 + i] = clippedRelu(value);
        }

        fc1.forwardInto(scratch.fc1Input, scratch.fc1Out);
        for (int i = 0; i < layout.l3; i++) {
            scratch.fc2Input[i] = clippedRelu(scratch.fc1Out[i]);
        }
        int output = fc2.forwardSingle(scratch.fc2Input);
        int fwdOut = scratch.fc0Out[layout.l2] * (600 * OUTPUT_SCALE)
                / (127 * (1 << WEIGHT_SCALE_BITS));
        return output + fwdOut;
    }

    /**
     * Reusable dense-layer workspace.
     */
    static final class Scratch {

        /**
         * FC0 output buffer.
         */
        final int[] fc0Out;

        /**
         * FC1 input buffer.
         */
        final int[] fc1Input;

        /**
         * FC1 output buffer.
         */
        final int[] fc1Out;

        /**
         * FC2 input buffer.
         */
        final int[] fc2Input;

        /**
         * Creates one workspace sized for the architecture layout.
         *
         * @param layout layer layout
         */
        Scratch(Layout layout) {
            this.fc0Out = new int[layout.l2 + 1];
            this.fc1Input = new int[layout.l2 * 2];
            this.fc1Out = new int[layout.l3];
            this.fc2Input = new int[layout.l3];
        }
    }

    /**
     * Clipped ReLU.
     *
     * @param value int32 input
     * @return uint8-like output
     */
    private static int clippedRelu(int value) {
        return Numbers.clamp(value >> WEIGHT_SCALE_BITS, 0, 127);
    }

    /**
     * Squared clipped ReLU.
     *
     * @param value int32 input
     * @return uint8-like output
     */
    private static int sqrClippedRelu(int value) {
        long squared = (long) value * value;
        long shifted = squared >> (2 * WEIGHT_SCALE_BITS + 7);
        return (int) Math.min(127L, shifted);
    }
}
