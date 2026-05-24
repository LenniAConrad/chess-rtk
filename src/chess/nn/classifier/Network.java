package chess.nn.classifier;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

import chess.gpu.BackendNames;

/**
 * Binary classifier residual CNN evaluator.
 *
 * <p>This class loads classifier {@code .bin} weights and runs a single
 * forward pass that returns one scalar logit.
 *
 * <p>The implementation intentionally mirrors the pure-Java LC0 forward pass in
 * this repo, but with a smaller 21-plane input and a single-logit head.
 */
public final class Network implements AutoCloseable {

    /**
     * Active backend identifier.
     */
    public static final String BACKEND = BackendNames.CPU;

    /**
     * Preferred file magic for classifier weights.
     */
    private static final byte[] MAGIC_CLSF = new byte[] { 'C', 'L', 'S', 'F' };

    /**
     * Compatibility magic accepted for early puzzle-classifier exporters.
     */
    private static final byte[] MAGIC_PCLS = new byte[] { 'P', 'C', 'L', 'S' };

    /**
     * Compatibility magic accepted for early puzzle-classifier exporters.
     */
    private static final byte[] MAGIC_PCJ0 = new byte[] { 'P', 'C', 'J', '0' };

    /**
     * Compatibility magic accepted for early exporters that reuse the LC0
     * binary container identifier.
     */
    private static final byte[] MAGIC_LC0_COMPAT = new byte[] { 'L', 'C', '0', 'J' };

    /**
     * CPU backend weights.
     */
    private final Weights weights;

    /**
     * Internal constructor.
     *
     * @param weights parsed weights
     */
    private Network(Weights weights) {
        this.weights = weights;
    }

    /**
     * Loads a classifier {@code .bin} weights file.
     *
     * @param path path to the weights file
     * @return network evaluator
     * @throws IOException if the weights cannot be read or parsed
     */
    public static Network load(Path path) throws IOException {
        return new Network(Weights.load(path));
    }

    /**
     * Returns basic network metadata.
     *
     * @return parsed network metadata
     */
    public Info info() {
        return new Info(
                weights.inputChannels,
                weights.trunkChannels,
                weights.blocks.size(),
                weights.headChannels,
                weights.outputSize,
                weights.parameterCount);
    }

    /**
     * Runs one forward pass on already-encoded classifier planes.
     *
     * @param encodedPlanes encoded planes (length {@code inputChannels * 64})
     * @return classifier output
     */
    public Prediction predictEncoded(float[] encodedPlanes) {
        if (encodedPlanes.length != weights.inputChannels * 64) {
            throw new IllegalArgumentException("Encoded input must be " + (weights.inputChannels * 64) + " floats.");
        }
        return Evaluator.evaluate(weights, encodedPlanes);
    }

    /**
     * Releases backend resources.
     */
    @Override
    public void close() {
        Evaluator.clearThreadLocal();
    }

    /**
     * Model metadata extracted from the weights file.
     *
     * @param inputChannels  number of input feature planes
     * @param trunkChannels  number of channels in the residual trunk
     * @param residualBlocks count of residual blocks
     * @param headChannels   number of channels in the classifier head
     * @param outputSize     number of scalar outputs
     * @param parameterCount total number of parameters
     */
    public record Info(
        /**
         * Stores the input channels.
         */
        int inputChannels,
        /**
         * Stores the trunk channels.
         */
        int trunkChannels,
        /**
         * Stores the residual blocks.
         */
        int residualBlocks,
        /**
         * Stores the head channels.
         */
        int headChannels,
        /**
         * Stores the output size.
         */
        int outputSize,
        /**
         * Stores the parameter count.
         */
        long parameterCount
    ) {
    }

    /**
     * Inference result for one position.
     *
     * @param logit raw classifier logit for the positive class
     */
    public record Prediction(
        /**
         * Stores the logit.
         */
        float logit
    ) {

        /**
         * Returns the sigmoid probability implied by {@link #logit()}.
         *
         * @return probability in {@code [0,1]}
         */
        public float probability() {
            return sigmoid(logit);
        }

        /**
         * Returns the default binary decision at threshold 0.5 / logit 0.
         *
         * @return true when the prediction is positive
         */
        public boolean isPositive() {
            return logit >= 0f;
        }
    }

    /**
     * Activation function used in the network.
     */
    private enum Activation {

        /**
         * Rectified linear unit.
         */
        RELU,

        /**
         * No activation (identity).
         */
        NONE
    }

    /**
     * Fork-join helper used when channel counts justify parallel work.
     */
    private static final class Parallel {

        /**
         * Number of threads configured for classifier convolutions.
         */
        static final int THREADS = parseThreads();

        /**
         * Minimum number of channels before parallelism is enabled.
         */
        static final int MIN_CHANNELS = 64;

        /**
         * Optional fork-join pool used when more than one thread is configured.
         */
        static final ForkJoinPool POOL = (THREADS > 1) ? new ForkJoinPool(THREADS) : null;

        /**
         * Reads the configured thread count or falls back to available processors.
         *
         * @return resolved thread count (at least 1)
         */
        private static int parseThreads() {
            String value = System.getProperty("crtk.classifier.threads");
            if (value == null || value.isBlank()) {
                value = System.getProperty("crtk.puzzleclassifier.threads");
            }
            if (value == null || value.isBlank()) {
                return Math.max(1, Runtime.getRuntime().availableProcessors());
            }
            try {
                return Math.max(1, Integer.parseInt(value.trim()));
            } catch (NumberFormatException e) {
                return Math.max(1, Runtime.getRuntime().availableProcessors());
            }
        }

        /**
         * Returns whether parallelism should be used for the given channel count.
         *
         * @param channels output channel count
         * @return true if parallel execution should be used
         */
        static boolean enabledForChannels(int channels) {
            return POOL != null && channels >= MIN_CHANNELS;
        }

        /**
         * Converts a range into work that can be executed by fork/join tasks.
         */
        interface RangeBody {

            /**
             * Executes work for a half-open channel range.
             *
             * @param startInclusive inclusive start index
             * @param endExclusive   exclusive end index
             */
            void run(int startInclusive, int endExclusive);
        }

        /**
         * Executes a range body either sequentially or on the fork-join pool.
         *
         * @param startInclusive inclusive start index
         * @param endExclusive   exclusive end index
         * @param body           work body to execute
         */
        static void forRange(int startInclusive, int endExclusive, RangeBody body) {
            if (POOL == null) {
                body.run(startInclusive, endExclusive);
                return;
            }
            POOL.invoke(new RangeTask(startInclusive, endExclusive, body));
        }

        /**
         * Task used by the fork-join pool to split channel ranges.
         */
        private static final class RangeTask extends RecursiveAction {

             /**
             * Shared serial version uid constant.
             */
             @java.io.Serial
            private static final long serialVersionUID = 1L;

            /**
             * Grain size used to stop splitting ranges.
             */
            private static final int GRAIN = 16;

            /**
             * Inclusive start index for this task's range.
             */
            private final int start;

            /**
             * Exclusive end index for this task's range.
             */
            private final int end;

            /**
             * Work body executed for each range chunk.
             */
            private transient RangeBody body;

            /**
             * Records the range and work body for the task.
             *
             * @param start inclusive start index for this range
             * @param end   exclusive end index for this range
             * @param body  work body to execute
             */
            RangeTask(int start, int end, RangeBody body) {
                this.start = start;
                this.end = end;
                this.body = body;
            }

            /**
             * Splits the range recursively until small enough to execute directly.
             */
            @Override
            protected void compute() {
                int length = end - start;
                if (length <= GRAIN) {
                    body.run(start, end);
                    return;
                }
                int mid = start + (length / 2);
                RangeTask left = new RangeTask(start, mid, body);
                RangeTask right = new RangeTask(mid, end, body);
                invokeAll(left, right);
            }
        }
    }

    /**
     * Convolutional layer parameters and weights.
     */
    private static final class ConvLayer {

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
         */
        private final int[] neighborSquare;

        /**
         * Precomputed kernel index offsets for each neighbor square.
         */
        private final int[] neighborKernelIndex;

        /**
         * Precomputed start offsets into the neighbor arrays.
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
         * @param input   input planes
         * @param output  output planes
         * @param ocStart inclusive start output channel
         * @param ocEnd   exclusive end output channel
         */
        private void forwardChannelsKernel1(float[] input, float[] output, int ocStart, int ocEnd) {
            int squares = 64;
            for (int oc = ocStart; oc < ocEnd; oc++) {
                int weightOffset = oc * inChannels;
                int outBase = oc * squares;
                for (int square = 0; square < squares; square++) {
                    float sum = 0f;
                    for (int ic = 0; ic < inChannels; ic++) {
                        sum += input[ic * squares + square] * weights[weightOffset + ic];
                    }
                    output[outBase + square] = sum;
                }
            }
        }

        /**
         * Convolution for kernels greater than 1 using a precomputed neighbor list.
         *
         * @param input   input planes
         * @param output  output planes
         * @param ocStart inclusive start output channel
         * @param ocEnd   exclusive end output channel
         */
        private void forwardChannelsWithNeighbors(float[] input, float[] output, int ocStart, int ocEnd) {
            int squares = 64;
            int kernelArea = kernel * kernel;
            for (int oc = ocStart; oc < ocEnd; oc++) {
                int weightOffset = oc * inChannels * kernelArea;
                int outBase = oc * squares;
                for (int square = 0; square < squares; square++) {
                    float sum = 0f;
                    int start = neighborStart[square];
                    int end = neighborStart[square + 1];
                    for (int ic = 0; ic < inChannels; ic++) {
                        int inBase = ic * squares;
                        int kernelBase = weightOffset + ic * kernelArea;
                        for (int i = start; i < end; i++) {
                            sum += input[inBase + neighborSquare[i]] * weights[kernelBase + neighborKernelIndex[i]];
                        }
                    }
                    output[outBase + square] = sum;
                }
            }
        }
    }

    /**
     * Precomputed per-square neighbor indices for a convolution kernel.
     */
    private static final class KernelNeighbors {

        /**
         * Start offsets into {@link #square}/{@link #kernelIndex} for each board square.
         */
        final int[] start;

        /**
         * Flattened list of neighboring input squares.
         */
        final int[] square;

        /**
         * Flattened list of kernel tap indices aligned with {@link #square}.
         */
        final int[] kernelIndex;

        /**
         * Creates neighbor lookups for convolution kernels.
         *
         * @param start       offsets into square/kernelIndex per board square
         * @param square      flattened neighbor square indices
         * @param kernelIndex flattened kernel tap indices
         */
        private KernelNeighbors(int[] start, int[] square, int[] kernelIndex) {
            this.start = start;
            this.square = square;
            this.kernelIndex = kernelIndex;
        }

        /**
         * Precomputes neighbor ranges for each of the 64 squares.
         *
         * @param kernel convolution kernel size
         * @return neighbor lookups for the supplied kernel
         */
        static KernelNeighbors precompute(int kernel) {
            int pad = kernel / 2;
            int maxNeighbors = 64 * kernel * kernel;
            int[] start = new int[65];
            int[] square = new int[maxNeighbors];
            int[] kernelIndex = new int[maxNeighbors];

            int offset = 0;
            for (int sq = 0; sq < 64; sq++) {
                start[sq] = offset;
                int row = sq / 8;
                int col = sq % 8;
                for (int ky = 0; ky < kernel; ky++) {
                    int inRow = row + ky - pad;
                    if (inRow < 0 || inRow >= 8) {
                        continue;
                    }
                    for (int kx = 0; kx < kernel; kx++) {
                        int inCol = col + kx - pad;
                        if (inCol < 0 || inCol >= 8) {
                            continue;
                        }
                        square[offset] = inRow * 8 + inCol;
                        kernelIndex[offset] = ky * kernel + kx;
                        offset++;
                    }
                }
            }
            start[64] = offset;
            return new KernelNeighbors(copyOf(start), trim(square, offset), trim(kernelIndex, offset));
        }

        /**
         * Copies the provided array.
         *
         * @param src source array
         * @return copied array
         */
        private static int[] copyOf(int[] src) {
            int[] out = new int[src.length];
            System.arraycopy(src, 0, out, 0, src.length);
            return out;
        }

        /**
         * Trims an int array to the provided length.
         *
         * @param src    source array
         * @param length resulting length
         * @return trimmed copy
         */
        private static int[] trim(int[] src, int length) {
            int[] out = new int[length];
            System.arraycopy(src, 0, out, 0, length);
            return out;
        }
    }

    /**
     * Fully connected layer descriptor.
     */
    private static final class DenseLayer {

        /**
         * Input dimension.
         */
        final int inDim;

        /**
         * Output dimension.
         */
        final int outDim;

        /**
         * Flattened weight matrix (row-major).
         */
        final float[] weights;

        /**
         * Bias vector for each output unit.
         */
        final float[] bias;

        /**
         * Builds a dense layer descriptor.
         *
         * @param inDim   input dimension
         * @param outDim  output dimension
         * @param weights flattened row-major weights
         * @param bias    bias vector
         */
        DenseLayer(int inDim, int outDim, float[] weights, float[] bias) {
            this.inDim = inDim;
            this.outDim = outDim;
            this.weights = weights;
            this.bias = bias;
        }

        /**
         * Runs the dense layer and applies the optional activation.
         *
         * @param input      input vector
         * @param output     output vector
         * @param activation activation to apply
         */
        void forward(float[] input, float[] output, Activation activation) {
            for (int outIndex = 0; outIndex < outDim; outIndex++) {
                float acc = bias[outIndex];
                int weightBase = outIndex * inDim;
                for (int inIndex = 0; inIndex < inDim; inIndex++) {
                    acc += weights[weightBase + inIndex] * input[inIndex];
                }
                if (activation == Activation.RELU && acc < 0f) {
                    acc = 0f;
                }
                output[outIndex] = acc;
            }
        }
    }

    /**
     * Residual block containing two convolutional layers.
     */
    private record ResidualBlock(
        /**
         * Stores the conv1.
         */
        ConvLayer conv1,
        /**
         * Stores the conv2.
         */
        ConvLayer conv2
    ) {
    }

    /**
     * Parsed weight tensors for the CPU backend.
     */
    private static final class Weights {

        /**
         * Number of input channels.
         */
        final int inputChannels;

        /**
         * Number of channels in the residual trunk.
         */
        final int trunkChannels;

        /**
         * Number of channels in the classifier head.
         */
        final int headChannels;

        /**
         * Number of output logits.
         */
        final int outputSize;

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
         * Head convolution before pooling.
         */
        final ConvLayer headConv;

        /**
         * Final dense output layer.
         */
        final DenseLayer outputDense;

        /**
         * Packs all decoded tensors into a single object.
         *
         * @param builder populated build state
         */
        private Weights(Builder builder) {
            this.inputChannels = builder.inputChannels;
            this.trunkChannels = builder.trunkChannels;
            this.headChannels = builder.headChannels;
            this.outputSize = builder.outputSize;
            this.parameterCount = builder.parameterCount;
            this.inputLayer = builder.inputLayer;
            this.blocks = builder.blocks;
            this.headConv = builder.headConv;
            this.outputDense = builder.outputDense;
        }

        /**
         * Internal build state used while parsing a weights file.
         */
        private static final class Builder {

            /**
             * Number of input channels reported by the weights file.
             */
            int inputChannels;

            /**
             * Number of channels in the residual trunk.
             */
            int trunkChannels;

            /**
             * Number of channels in the classifier head.
             */
            int headChannels;

            /**
             * Number of output logits.
             */
            int outputSize;

            /**
             * Total parameter count computed during parsing.
             */
            long parameterCount;

            /**
             * Parsed input convolution layer descriptor.
             */
            ConvLayer inputLayer;

            /**
             * Parsed residual blocks in the trunk.
             */
            List<ResidualBlock> blocks;

            /**
             * Convolutional stem for the classifier head.
             */
            ConvLayer headConv;

            /**
             * Final dense output layer.
             */
            DenseLayer outputDense;
        }

        /**
         * Reads a classifier weights file and builds all layer descriptors.
         *
         * @param path path to the weights file
         * @return decoded {@link Weights}
         * @throws IOException on read or parse errors
         */
        static Weights load(Path path) throws IOException {
            byte[] bytes = Files.readAllBytes(path);
            ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

            byte[] magic = new byte[4];
            buf.get(magic);
            if (!matchesMagic(magic, MAGIC_CLSF) && !matchesMagic(magic, MAGIC_PCLS)
                    && !matchesMagic(magic, MAGIC_PCJ0)
                    && !matchesMagic(magic, MAGIC_LC0_COMPAT)) {
                throw new IOException("Invalid classifier weights file (bad magic).");
            }

            int version = buf.getInt();
            if (version != 1) {
                throw new IOException("Unsupported classifier weights version: " + version);
            }

            int inputChannels = buf.getInt();
            int trunkChannels = buf.getInt();
            int residualBlocks = buf.getInt();
            int headChannels = buf.getInt();
            int outputSize = buf.getInt();
            if (inputChannels <= 0 || trunkChannels <= 0 || residualBlocks < 0 || headChannels <= 0 || outputSize <= 0) {
                throw new IOException("Invalid classifier header values.");
            }

            ConvLayer inputLayer = readConv(buf);
            if (inputLayer.inChannels != inputChannels || inputLayer.outChannels != trunkChannels) {
                throw new IOException("Input layer shape mismatch in classifier weights.");
            }

            long params = countParams(inputLayer);
            List<ResidualBlock> blocks = new ArrayList<>(residualBlocks);
            for (int i = 0; i < residualBlocks; i++) {
                ConvLayer conv1 = readConv(buf);
                ConvLayer conv2 = readConv(buf);
                if (conv1.inChannels != trunkChannels || conv1.outChannels != trunkChannels
                        || conv2.inChannels != trunkChannels || conv2.outChannels != trunkChannels) {
                    throw new IOException("Residual block shape mismatch in classifier weights.");
                }
                params += countParams(conv1) + countParams(conv2);
                blocks.add(new ResidualBlock(conv1, conv2));
            }

            ConvLayer headConv = readConv(buf);
            if (headConv.inChannels != trunkChannels || headConv.outChannels != headChannels) {
                throw new IOException("Head layer shape mismatch in classifier weights.");
            }

            DenseLayer outputDense = readDense(buf, outputSize);
            if (outputDense.inDim != headChannels) {
                throw new IOException("Output dense input mismatch in classifier weights.");
            }

            params += countParams(headConv) + countParams(outputDense);

            if (buf.hasRemaining()) {
                throw new IOException("Unexpected bytes at end of classifier weights file.");
            }

            Builder builder = new Builder();
            builder.inputChannels = inputChannels;
            builder.trunkChannels = trunkChannels;
            builder.headChannels = headChannels;
            builder.outputSize = outputSize;
            builder.parameterCount = params;
            builder.inputLayer = inputLayer;
            builder.blocks = blocks;
            builder.headConv = headConv;
            builder.outputDense = outputDense;
            return new Weights(builder);
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

        /**
         * Returns whether the provided file magic matches the expected magic.
         *
         * @param actual   actual magic bytes
         * @param expected expected magic bytes
         * @return true on exact match
         */
        private static boolean matchesMagic(byte[] actual, byte[] expected) {
            if (actual.length != expected.length) {
                return false;
            }
            for (int i = 0; i < actual.length; i++) {
                if (actual[i] != expected[i]) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Performs the forward pass for the CPU backend using reusable scratch space.
     */
    private static final class Evaluator {

        /**
         * Thread-local buffers reused for each evaluation.
         */
        private static final ThreadLocal<Workspace> WORKSPACE = ThreadLocal.withInitial(Workspace::new);

        /**
         * Clears the thread-local workspace for the current thread.
         */
        static void clearThreadLocal() {
            WORKSPACE.remove();
        }

        /**
         * Evaluates the model and returns one scalar logit.
         *
         * @param weights       parsed weights
         * @param encodedInput  classifier planes
         * @return inference result
         */
        static Prediction evaluate(Weights weights, float[] encodedInput) {
            Workspace workspace = WORKSPACE.get();
            workspace.ensureCapacity(weights);

            weights.inputLayer.forwardNoBias(encodedInput, workspace.current);
            addBiasReLU(workspace.current, weights.inputLayer.bias, workspace.current);

            for (ResidualBlock block : weights.blocks) {
                block.conv1.forwardNoBias(workspace.current, workspace.tmp);
                addBiasReLU(workspace.tmp, block.conv1.bias, workspace.tmp);

                block.conv2.forwardNoBias(workspace.tmp, workspace.scratch);
                addResidualReLU(workspace.scratch, block.conv2.bias, workspace.current, workspace.next);

                float[] swap = workspace.current;
                workspace.current = workspace.next;
                workspace.next = swap;
            }

            weights.headConv.forwardNoBias(workspace.current, workspace.head);
            addBiasReLU(workspace.head, weights.headConv.bias, workspace.head);
            globalAveragePool(workspace.head, weights.headChannels, workspace.pooled);
            weights.outputDense.forward(workspace.pooled, workspace.logits, Activation.NONE);
            return new Prediction(workspace.logits[0]);
        }

        /**
         * Mutable buffers used during convolutional evaluation.
         */
        private static final class Workspace {

            /**
             * Current trunk activations [trunkChannels, 64].
             */
            float[] current = new float[0];

            /**
             * Next trunk activations [trunkChannels, 64].
             */
            float[] next = new float[0];

            /**
             * Temporary buffer for intermediate conv output [trunkChannels, 64].
             */
            float[] tmp = new float[0];

            /**
             * Scratch buffer for conv output [trunkChannels, 64].
             */
            float[] scratch = new float[0];

            /**
             * Head activations [headChannels, 64].
             */
            float[] head = new float[0];

            /**
             * Global-average-pooled head activations [headChannels].
             */
            float[] pooled = new float[0];

            /**
             * Output logits [outputSize].
             */
            float[] logits = new float[0];

            /**
             * Ensures buffers match the current model dimensions.
             *
             * @param weights current model weights
             */
            void ensureCapacity(Weights weights) {
                int trunkSize = weights.trunkChannels * 64;
                if (current.length != trunkSize) {
                    current = new float[trunkSize];
                }
                if (next.length != trunkSize) {
                    next = new float[trunkSize];
                }
                if (tmp.length != trunkSize) {
                    tmp = new float[trunkSize];
                }
                if (scratch.length != trunkSize) {
                    scratch = new float[trunkSize];
                }

                int headSize = weights.headChannels * 64;
                if (head.length != headSize) {
                    head = new float[headSize];
                }
                if (pooled.length != weights.headChannels) {
                    pooled = new float[weights.headChannels];
                }
                if (logits.length != weights.outputDense.outDim) {
                    logits = new float[weights.outputDense.outDim];
                }
            }
        }

        /**
         * Adds bias and applies ReLU when accumulating convolution outputs.
         *
         * @param convOut convolution output tensor
         * @param bias    bias vector per channel
         * @param dest    destination tensor
         */
        private static void addBiasReLU(float[] convOut, float[] bias, float[] dest) {
            int channels = bias.length;
            if (Parallel.enabledForChannels(channels)) {
                Parallel.forRange(0, channels, (start, end) -> addBiasReLUChannels(convOut, bias, dest, start, end));
            } else {
                addBiasReLUChannels(convOut, bias, dest, 0, channels);
            }
        }

        /**
         * Adds bias and applies ReLU for a subset of channels.
         *
         * @param convOut convolution output tensor
         * @param bias    bias vector per channel
         * @param dest    destination tensor
         * @param start   inclusive start channel
         * @param end     exclusive end channel
         */
        private static void addBiasReLUChannels(float[] convOut, float[] bias, float[] dest, int start, int end) {
            for (int ch = start; ch < end; ch++) {
                int base = ch * 64;
                float b = bias[ch];
                for (int i = 0; i < 64; i++) {
                    float value = convOut[base + i] + b;
                    dest[base + i] = value > 0f ? value : 0f;
                }
            }
        }

        /**
         * Combines residual input with convolution outputs then applies ReLU.
         *
         * @param convOut  convolution output tensor
         * @param bias     bias vector per channel
         * @param residual residual tensor to add
         * @param dest     destination tensor
         */
        private static void addResidualReLU(float[] convOut, float[] bias, float[] residual, float[] dest) {
            int channels = bias.length;
            for (int ch = 0; ch < channels; ch++) {
                int base = ch * 64;
                float b = bias[ch];
                for (int i = 0; i < 64; i++) {
                    float value = convOut[base + i] + b + residual[base + i];
                    dest[base + i] = value > 0f ? value : 0f;
                }
            }
        }

        /**
         * Global-average-pools each channel (mean over 64 squares).
         *
         * @param input     input tensor [channels, 64]
         * @param channels  number of channels
         * @param pooledOut destination vector [channels]
         */
        private static void globalAveragePool(float[] input, int channels, float[] pooledOut) {
            float invSquares = 1f / 64f;
            for (int ch = 0; ch < channels; ch++) {
                int base = ch * 64;
                float sum = 0f;
                for (int i = 0; i < 64; i++) {
                    sum += input[base + i];
                }
                pooledOut[ch] = sum * invSquares;
            }
        }
    }

    /**
     * Sigmoid helper used to expose prediction probabilities.
     *
     * @param x input logit
     * @return sigmoid(x)
     */
    private static float sigmoid(float x) {
        return 1f / (1f + (float) Math.exp(-x));
    }
}
