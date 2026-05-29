package chess.nn.lc0.cnn;



/**
 * Convolutional layer parameters and weights.
 */
final class ConvLayer {

    /**
     * Number of input channels.
     */
    final int inChannels;

    /**
     * Number of output channels.
     */
    final int outChannels;

    /**
     * Kernel size (usually 1 or 3).
     */
    final int kernel;

    /**
     * Flattened convolution weights.
     */
    final float[] weights;

    /**
     * Bias vector for each output channel.
     */
    final float[] bias;

    /**
     * Precomputed neighbor square indices for kernel convolution.
     * {@code null} when the kernel size is 1.
     */
    private final int[] neighborSquare;

    /**
     * Precomputed kernel index offsets for each neighbor square.
     * {@code null} when the kernel size is 1.
     */
    private final int[] neighborKernelIndex;

    /**
     * Precomputed start offsets into the neighbor arrays.
     * {@code null} when the kernel size is 1.
     */
    private final int[] neighborStart;

    /**
     * Creates a convolutional layer descriptor.
     *
     * @param inChannels  number of input feature planes
     * @param outChannels number of output feature planes
     * @param kernel      spatial kernel size (1 or 3)
     * @param weights     flattened convolution weights
     * @param bias        bias vector per output channel
     */
    ConvLayer(int inChannels, int outChannels, int kernel, float[] weights, float[] bias) {
        this.inChannels = inChannels;
        this.outChannels = outChannels;
        this.kernel = kernel;
        this.weights = weights;
        this.bias = bias;
        if (kernel == 1) {
            neighborSquare = null;
            neighborKernelIndex = null;
            neighborStart = null;
        } else {
            KernelNeighbors neighbors = KernelNeighbors.precompute(kernel);
            neighborSquare = neighbors.square;
            neighborKernelIndex = neighbors.kernelIndex;
            neighborStart = neighbors.start;
        }
    }

    /**
     * Computes convolution outputs without adding biases.
     *
     * @param input  input planes [inChannels, 64]
     * @param output destination planes [outChannels, 64]
     */
    void forwardNoBias(float[] input, float[] output) {
        if (Parallel.enabledForChannels(outChannels)) {
            Parallel.forRange(0, outChannels,
                    (start, end) -> forwardChannels(input, output, start, end));
        } else {
            forwardChannels(input, output, 0, outChannels);
        }
    }

    /**
     * Performs convolution for a subset of output channels.
     *
     * @param input   input planes
     * @param output  output planes
     * @param ocStart inclusive start output channel
     * @param ocEnd   exclusive end output channel
     */
    private void forwardChannels(float[] input, float[] output, int ocStart, int ocEnd) {
        if (kernel == 1) {
            forwardChannelsKernel1(input, output, ocStart, ocEnd);
            return;
        }
        forwardChannelsWithNeighbors(input, output, ocStart, ocEnd);
    }

    /**
     * Fast 1x1 convolution specialized for a flattened [C,64] layout.
     *
     * <p>
     * Computes only the linear part (no bias/activation) for
     * {@code ocStart..ocEnd}.
     *
     * @param input   input planes
     * @param output  output planes
     * @param ocStart inclusive start output channel
     * @param ocEnd   exclusive end output channel
     */
    private void forwardChannelsKernel1(float[] input, float[] output, int ocStart, int ocEnd) {
        int square = 64;
        for (int oc = ocStart; oc < ocEnd; oc++) {
            int weightOffset = oc * inChannels;
            int outBase = oc * square;
            for (int sq = 0; sq < square; sq++) {
                float sum = 0f;
                for (int ic = 0; ic < inChannels; ic++) {
                    sum += input[ic * square + sq] * weights[weightOffset + ic];
                }
                output[outBase + sq] = sum;
            }
        }
    }

    /**
     * Convolution for kernels &gt; 1 using a precomputed neighbor list per square
     * to avoid bounds checks.
     *
     * <p>
     * Computes only the linear part (no bias/activation) for
     * {@code ocStart..ocEnd}.
     *
     * @param input   input planes
     * @param output  output planes
     * @param ocStart inclusive start output channel
     * @param ocEnd   exclusive end output channel
     */
    private void forwardChannelsWithNeighbors(float[] input, float[] output, int ocStart, int ocEnd) {
        int square = 64;
        int kk = kernel * kernel;
        for (int oc = ocStart; oc < ocEnd; oc++) {
            int weightOffset = oc * inChannels * kk;
            int outBase = oc * square;
            for (int sq = 0; sq < square; sq++) {
                float sum = 0f;
                int start = neighborStart[sq];
                int end = neighborStart[sq + 1];
                for (int ic = 0; ic < inChannels; ic++) {
                    int inBase = ic * square;
                    int kernelBase = weightOffset + ic * kk;
                    for (int i = start; i < end; i++) {
                        sum += input[inBase + neighborSquare[i]] * weights[kernelBase + neighborKernelIndex[i]];
                    }
                }
                output[outBase + sq] = sum;
            }
        }
    }
}
