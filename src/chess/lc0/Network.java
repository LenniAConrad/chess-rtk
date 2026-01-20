package chess.lc0;

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

import chess.lc0.cuda.Backend;

/**
 * LCZero (LC0) "classical" policy+value network evaluator.
 *
 * <p>
 * This class loads LC0J {@code .bin} weights (magic {@code LC0J}) and runs a
 * single forward pass:
 * policy logits and a value head in WDL form ({@code [win, draw, loss]}) from
 * the side-to-move perspective.
 * The returned scalar {@link Prediction#value()} is {@code W-L}.
 *
 * <p>
 * This class can run inference using either:
 * <ul>
 * <li>a pure-Java CPU backend</li>
 * <li>optional GPU backends via JNI (CUDA/ROCm/oneAPI)</li>
 * </ul>
 *
	 * <h2>Backend selection</h2>
	 * <ul>
	 * <li>{@code -Dcrtk.lc0.backend=auto} (default): use the first available GPU backend
	 * (CUDA, then ROCm, then oneAPI) that initializes successfully, else CPU</li>
	 * <li>{@code -Dcrtk.lc0.backend=cpu}: force CPU</li>
	 * <li>{@code -Dcrtk.lc0.backend=cuda}: force CUDA (throws if init fails/unavailable)</li>
	 * <li>{@code -Dcrtk.lc0.backend=rocm|amd|hip}: force ROCm (AMD)</li>
	 * <li>{@code -Dcrtk.lc0.backend=oneapi|intel}: force oneAPI (Intel)</li>
	 * </ul>
	 *
	 * <p>
	 * Legacy compatibility: {@code ucicli.lc0.*}, {@code Ucicli.lc0.*}, and {@code lc0j.*} are also honored.
 *
 * <b>CPU threading</b>
 * <p>
	 * The CPU backend parallelizes large convolutions over output channels using a
	 * {@link ForkJoinPool}.
	 * Configure with {@code -Dcrtk.lc0.threads=N}.
 *
 * <b>Inputs</b>
 * <ul>
 * <li>Already-encoded planes: use {@link #predictEncoded(float[])}</li>
 * <li>FEN to planes: use {@link InputEncoder#encodeFen(String)}</li>
 * <li>{@code chess.core.Position} to planes: use
 * {@link Encoder#encode(chess.core.Position)} or {@link Model}</li>
 * </ul>
 *
 * <b>Value semantics</b>
 * <p>
 * The value head outputs WDL probabilities ordered as {@code [win, draw, loss]}
 * from the side-to-move perspective.
 * The scalar value returned by {@link Prediction#value()} is {@code W-L} (range
 * approximately {@code [-1, +1]}).
 *
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class Network implements AutoCloseable {

    /**
     * CPU backend weights (null when a GPU backend is active).
     */
    private final Weights weights; // CPU backend (when non-null)

    /**
     * CUDA backend instance (null when inactive).
     */
    private final Backend cuda; // CUDA backend (when non-null)

    /**
     * ROCm backend instance (null when inactive).
     */
    private final chess.lc0.rocm.Backend rocm; // ROCm backend (when non-null)

    /**
     * oneAPI backend instance (null when inactive).
     */
    private final chess.lc0.oneapi.Backend oneapi; // oneAPI backend (when non-null)

    /**
     * Internal constructor selecting the active backend.
     */
    private Network(Weights weights, Backend cuda, chess.lc0.rocm.Backend rocm, chess.lc0.oneapi.Backend oneapi) {
        this.weights = weights;
        this.cuda = cuda;
        this.rocm = rocm;
        this.oneapi = oneapi;
    }

    /**
     * Loads an LC0J {@code .bin} weights file.
     *
	     * <p>
	     * Depending on {@code -Dcrtk.lc0.backend} (or legacy {@code ucicli.lc0.backend} / {@code lc0j.backend})
	     * and GPU availability, this will load either the CPU or a GPU backend.
     *
     * @param path path to an LC0J binary weights file (magic {@code LC0J})
     * @return network evaluator
     * @throws IOException if the weights cannot be read/parsed, or if a forced
     *                     GPU backend fails to initialize
     */
	    public static Network load(Path path) throws IOException {
	        String backend = System.getProperty("crtk.lc0.backend");
	        if (backend == null) {
	            backend = System.getProperty("ucicli.lc0.backend");
	        }
	        if (backend == null) {
	            backend = System.getProperty("Ucicli.lc0.backend");
	        }
	        if (backend == null) {
	            backend = System.getProperty("lc0j.backend", "auto");
	        }
	        backend = backend.trim().toLowerCase();
        boolean preferCuda = backend.equals("auto") || backend.equals("cuda");
        boolean preferRocm = backend.equals("auto") || backend.equals("rocm") || backend.equals("amd") || backend.equals("hip");
        boolean preferOneapi = backend.equals("auto") || backend.equals("oneapi") || backend.equals("intel");
        boolean forceCuda = backend.equals("cuda");
        boolean forceRocm = backend.equals("rocm") || backend.equals("amd") || backend.equals("hip");
        boolean forceOneapi = backend.equals("oneapi") || backend.equals("intel");

        boolean cudaAvailable = Backend.isAvailable();
        boolean rocmAvailable = chess.lc0.rocm.Backend.isAvailable();
        boolean oneapiAvailable = chess.lc0.oneapi.Backend.isAvailable();

        if (forceCuda && !cudaAvailable) {
            throw new IOException(
                    "CUDA backend requested but unavailable (JNI library not loaded and/or no CUDA device).");
        }
        if (forceRocm && !rocmAvailable) {
            throw new IOException(
                    "ROCm backend requested but unavailable (JNI library not loaded and/or no ROCm device).");
        }
        if (forceOneapi && !oneapiAvailable) {
            throw new IOException(
                    "oneAPI backend requested but unavailable (JNI library not loaded and/or no Intel GPU device).");
        }

        if (preferCuda && cudaAvailable) {
            try {
                return loadCuda(path);
            } catch (RuntimeException e) {
                if (forceCuda) {
                    throw new IOException("CUDA backend requested but failed to initialize.", e);
                }
            }
        }
        if (preferRocm && rocmAvailable) {
            try {
                return loadRocm(path);
            } catch (RuntimeException e) {
                if (forceRocm) {
                    throw new IOException("ROCm backend requested but failed to initialize.", e);
                }
            }
        }
        if (preferOneapi && oneapiAvailable) {
            try {
                return loadOneapi(path);
            } catch (RuntimeException e) {
                if (forceOneapi) {
                    throw new IOException("oneAPI backend requested but failed to initialize.", e);
                }
            }
        }
        return new Network(Weights.load(path), null, null, null);
    }

    /**
     * Loads a network using the CUDA backend.
     * Returns an initialized instance when CUDA setup succeeds.
     *
     * @param path path to the weights file
     * @return CUDA-backed network instance
     */
    private static Network loadCuda(Path path) {
        try (CudaBackendHolder holder = new CudaBackendHolder(Backend.create(path))) {
            Network network = new Network(null, holder.backend, null, null);
            holder.detach();
            return network;
        }
    }

    /**
     * Loads a network using the ROCm backend.
     * Returns an initialized instance when ROCm setup succeeds.
     *
     * @param path path to the weights file
     * @return ROCm-backed network instance
     */
    private static Network loadRocm(Path path) {
        try (RocmBackendHolder holder = new RocmBackendHolder(chess.lc0.rocm.Backend.create(path))) {
            Network network = new Network(null, null, holder.backend, null);
            holder.detach();
            return network;
        }
    }

    /**
     * Loads a network using the oneAPI backend.
     * Returns an initialized instance when oneAPI setup succeeds.
     *
     * @param path path to the weights file
     * @return oneAPI-backed network instance
     */
    private static Network loadOneapi(Path path) {
        try (OneapiBackendHolder holder = new OneapiBackendHolder(chess.lc0.oneapi.Backend.create(path))) {
            Network network = new Network(null, null, null, holder.backend);
            holder.detach();
            return network;
        }
    }

    /**
     * Helper that owns a CUDA backend until detached or closed.
     * Ensures the backend is closed on error paths.
     */
    private static final class CudaBackendHolder implements AutoCloseable {

        /**
         * Owned backend instance, or {@code null} once detached.
         * Closed on {@link #close()} when still attached.
         */
        private Backend backend;

        /**
         * Creates a holder that owns the provided backend until detached or closed.
         *
         * @param backend backend instance to manage
         */
        private CudaBackendHolder(Backend backend) {
            this.backend = backend;
        }

        /**
         * Releases ownership so the backend is not closed by this holder.
         * Used after transferring the backend to a {@link Network}.
         */
        private void detach() {
            backend = null;
        }

        /**
         * Closes the backend if it is still attached.
         * Safe to call multiple times.
         */
        @Override
        public void close() {
            if (backend != null) {
                backend.close();
            }
        }
    }

    /**
     * Helper that owns a ROCm backend until detached or closed.
     * Ensures the backend is closed on error paths.
     */
    private static final class RocmBackendHolder implements AutoCloseable {
        private chess.lc0.rocm.Backend backend;

        private RocmBackendHolder(chess.lc0.rocm.Backend backend) {
            this.backend = backend;
        }

        private void detach() {
            backend = null;
        }

        @Override
        public void close() {
            if (backend != null) {
                backend.close();
            }
        }
    }

    /**
     * Helper that owns a oneAPI backend until detached or closed.
     * Ensures the backend is closed on error paths.
     */
    private static final class OneapiBackendHolder implements AutoCloseable {
        private chess.lc0.oneapi.Backend backend;

        private OneapiBackendHolder(chess.lc0.oneapi.Backend backend) {
            this.backend = backend;
        }

        private void detach() {
            backend = null;
        }

        @Override
        public void close() {
            if (backend != null) {
                backend.close();
            }
        }
    }

    /**
     * Returns the active backend for this instance.
     *
     * @return {@code "cpu"}, {@code "cuda"}, {@code "rocm"}, or {@code "oneapi"}
     */
    public String backend() {
        if (cuda != null) {
            return "cuda";
        }
        if (rocm != null) {
            return "rocm";
        }
        if (oneapi != null) {
            return "oneapi";
        }
        return "cpu";
    }

    /**
     * Returns basic network metadata (shape and parameter count).
     *
     * @return parsed network metadata
     */
    public Info info() {
        if (cuda != null) {
            return cuda.info();
        }
        if (rocm != null) {
            return rocm.info();
        }
        if (oneapi != null) {
            return oneapi.info();
        }
        return new Info(
                weights.inputChannels,
                weights.trunkChannels,
                weights.blocks.size(),
                weights.policyChannels,
                weights.valueChannels,
                weights.policyMap.length,
                weights.parameterCount);
    }

    /**
     * Debug helper: returns the raw value-head WDL probabilities and the
     * side-to-move flag.
     *
     * <p>
     * This is only supported for the CPU backend (GPU backends do not
     * currently expose this hook).
     *
     * @param encodedPlanes encoded LC0 planes (length {@code inputChannels * 64})
     * @return raw value-head output and side-to-move information
     */
    public DebugValue debugValue(float[] encodedPlanes) {
        if (cuda != null || rocm != null || oneapi != null) {
            throw new UnsupportedOperationException("debugValue() not supported for GPU backends.");
        }
        if (encodedPlanes.length != weights.inputChannels * 64) {
            throw new IllegalArgumentException("Encoded input must be " + (weights.inputChannels * 64) + " floats.");
        }
        return Evaluator.debugValue(weights, encodedPlanes);
    }

    /**
     * Runs one forward pass on an already-encoded LC0 112-plane input.
     *
     * @param encodedPlanes encoded planes (length {@code inputChannels * 64})
     * @return policy logits, WDL probabilities, and scalar {@code W-L} value
     */
    public Prediction predictEncoded(float[] encodedPlanes) {
        if (cuda != null) {
            return cuda.predictEncoded(encodedPlanes);
        }
        if (rocm != null) {
            return rocm.predictEncoded(encodedPlanes);
        }
        if (oneapi != null) {
            return oneapi.predictEncoded(encodedPlanes);
        }
        if (encodedPlanes.length != weights.inputChannels * 64) {
            throw new IllegalArgumentException("Encoded input must be " + (weights.inputChannels * 64) + " floats.");
        }
        return Evaluator.evaluate(weights, encodedPlanes);
    }

    /**
     * Releases backend resources.
     *
     * <p>
     * CPU backend has no native resources. GPU backends must be closed to free
     * device memory.
     */
    @Override
    public void close() {
        if (cuda != null) {
            cuda.close();
        }
        if (rocm != null) {
            rocm.close();
        }
        if (oneapi != null) {
            oneapi.close();
        }
        if (cuda == null && rocm == null && oneapi == null) {
            Evaluator.clearThreadLocal();
        }
    }

    /**
     * Model metadata extracted from the weights file.
     *
     * <p>These values summarize the network's structure and parameter count.</p>
     *
     * @param inputChannels  number of input feature planes
     * @param trunkChannels  number of channels in the residual trunk
     * @param residualBlocks count of residual blocks
     * @param policyChannels number of channels in the policy head
     * @param valueChannels  number of channels in the value head
     * @param policySize     number of policy outputs
     * @param parameterCount total number of parameters
     *
     * @since 2025
     * @author Lennart A. Conrad
     */
    public record Info(int inputChannels, int trunkChannels, int residualBlocks,
            int policyChannels, int valueChannels, int policySize,
            long parameterCount) {
    }

    /**
     * Debug output for the value head (CPU backend only).
     *
     * @param rawWdl      raw WDL probabilities (after softmax) ordered as
     *                    {@code [W, D, L]} from side-to-move
     * @param blackToMove true if the input indicates black to move
     *
     * @since 2025
     * @author Lennart A. Conrad
     */
    public record DebugValue(float[] rawWdl, boolean blackToMove) {

        /**
         * Compares the WDL arrays by content and the side-to-move flag by value.
         * Treats array contents as the equality contract.
         *
         * @param o object to compare against
         * @return true if the values are equal
         */
        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof DebugValue other))
                return false;
            return blackToMove == other.blackToMove && Arrays.equals(rawWdl, other.rawWdl);
        }

        /**
         * Hashes the WDL array contents and the side-to-move flag.
         * Matches the equality contract for this record.
         *
         * @return hash code for this instance
         */
        @Override
        public int hashCode() {
            int result = Arrays.hashCode(rawWdl);
            result = 31 * result + Boolean.hashCode(blackToMove);
            return result;
        }

        /**
         * Returns a readable representation of the debug value.
         * Includes the WDL array and side-to-move flag.
         *
         * @return string form of this debug value
         */
        @Override
        public String toString() {
            return "DebugValue[rawWdl=" + Arrays.toString(rawWdl) + ", blackToMove=" + blackToMove + "]";
        }
    }

    /**
     * Inference result for one position.
     *
     * @param policy policy logits (not softmaxed) over the LC0 move encoding
     * @param wdl    WDL probabilities ordered as {@code [W, D, L]} from
     *               side-to-move
     * @param value  scalar {@code W-L} from side-to-move
     *
     * @since 2025
     * @author Lennart A. Conrad
     */
    public record Prediction(float[] policy, float[] wdl, float value) {

        /**
         * Equality compares {@link #value()} exactly (bitwise) and compares arrays by content.
         * Treats policy and WDL arrays as part of the value identity.
         *
         * @param o object to compare against
         * @return true if the values are equal
         */
        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof Prediction other))
                return false;
            return Float.floatToIntBits(value) == Float.floatToIntBits(other.value)
                    && Arrays.equals(policy, other.policy)
                    && Arrays.equals(wdl, other.wdl);
        }

        /**
         * Hash is based on the policy/WDL array contents and the scalar {@link #value()}.
         * Matches the equality contract for this record.
         *
         * @return hash code for this instance
         */
        @Override
        public int hashCode() {
            int result = Arrays.hashCode(policy);
            result = 31 * result + Arrays.hashCode(wdl);
            result = 31 * result + Float.hashCode(value);
            return result;
        }

        /**
         * Returns a readable representation including policy, WDL and scalar value.
         *
         * @return formatted string representation
         */
        @Override
        public String toString() {
            return "Prediction[policy=" + Arrays.toString(policy) + ", wdl=" + Arrays.toString(wdl) + ", value=" + value
                    + "]";
        }
    }

    /**
     * Activation function used in the network.
     */
    private enum Activation {

        /** Rectified linear unit. */
        RELU,

        /** No activation (identity). */
        NONE
    }

    /**
     * Simple fork-join helper to parallelize over channel ranges.
     *
     * <p>
     * Use {@code -Dlc0j.threads=N} to override thread count (default:
     * {@code availableProcessors()}).
     */

    /**
     * Fork-join helper used when channel counts justify parallel work.
     */
    private static final class Parallel {

        /**
         * Number of threads configured for LC0 convolutions.
         */
        static final int THREADS = parseThreads();

        /**
         * Minimum number of channels before parallelism is enabled.
         */
        static final int MIN_CHANNELS = 128;

        /**
         * Optional {@link ForkJoinPool} used when more than one thread is configured.
         */
        static final ForkJoinPool POOL = (THREADS > 1) ? new ForkJoinPool(THREADS) : null;

        /**
         * Reads the configured thread count or falls back to available processors.
         *
         * @return resolved thread count (at least 1)
         */
	        private static int parseThreads() {
	            String v = System.getProperty("crtk.lc0.threads");
	            if (v == null) {
	                v = System.getProperty("ucicli.lc0.threads");
	            }
	            if (v == null) {
	                v = System.getProperty("Ucicli.lc0.threads");
	            }
	            if (v == null) {
	                v = System.getProperty("lc0j.threads");
	            }
	            if (v == null || v.isBlank()) {
	                return Math.max(1, Runtime.getRuntime().availableProcessors());
	            }
            try {
                return Math.max(1, Integer.parseInt(v.trim()));
            } catch (NumberFormatException e) {
                return Math.max(1, Runtime.getRuntime().availableProcessors());
            }
        }

        /**
         * Returns {@code true} when parallelism should be used for the provided channel
         * count.
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
             * @param endExclusive exclusive end index
             */
            void run(int startInclusive, int endExclusive);
        }

        /**
         * Executes {@link RangeBody} either sequentially or on the fork/join pool.
         *
         * @param startInclusive inclusive start index
         * @param endExclusive exclusive end index
         * @param body work body to execute
         */
        static void forRange(int startInclusive, int endExclusive, RangeBody body) {
            if (POOL == null) {
                body.run(startInclusive, endExclusive);
                return;
            }
            POOL.invoke(new RangeTask(startInclusive, endExclusive, body));
        }

        /**
         * Task used by {@link ForkJoinPool} to split channel ranges.
         */
        private static final class RangeTask extends RecursiveAction {

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
             * @param end exclusive end index for this range
             * @param body work body to execute
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
                int len = end - start;
                if (len <= GRAIN) {
                    body.run(start, end);
                    return;
                }
                int mid = start + (len / 2);
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
         * @param inChannels number of input feature planes
         * @param outChannels number of output feature planes
         * @param kernel spatial kernel size (1 or 3)
         * @param weights flattened convolution weights
         * @param bias bias vector per output channel
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
         * @param input input planes [inChannels, 64]
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
         * @param input input planes
         * @param output output planes
         * @param ocStart inclusive start output channel
         * @param ocEnd exclusive end output channel
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
         * <p>Computes only the linear part (no bias/activation) for {@code ocStart..ocEnd}.
         *
         * @param input input planes
         * @param output output planes
         * @param ocStart inclusive start output channel
         * @param ocEnd exclusive end output channel
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
         * Convolution for kernels &gt; 1 using a precomputed neighbor list per square to avoid bounds checks.
         *
         * <p>Computes only the linear part (no bias/activation) for {@code ocStart..ocEnd}.
         *
         * @param input input planes
         * @param output output planes
         * @param ocStart inclusive start output channel
         * @param ocEnd exclusive end output channel
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

    /**
     * Precomputed per-square neighbor indices for a given convolution kernel size.
     *
     * <p>Used to speed up spatial convolutions by skipping off-board kernel taps.
     */
    private static final class KernelNeighbors {

        /** Start offsets into {@link #square}/{@link #kernelIndex} for each board square (length 65). */
        final int[] start; // length 65

        /** Flattened list of neighboring input squares, indexed by ranges in {@link #start}. */
        final int[] square;

        /** Flattened list of kernel tap indices aligned with {@link #square}. */
        final int[] kernelIndex;

        /**
         * Creates neighbor lookups for convolution kernels.
         *
         * @param start       offsets into {@code square}/{@code kernelIndex} per board square
         * @param square      flattened neighbor square indices
         * @param kernelIndex flattened kernel tap indices aligned with {@code square}
         */
        private KernelNeighbors(int[] start, int[] square, int[] kernelIndex) {
            this.start = start;
            this.square = square;
            this.kernelIndex = kernelIndex;
        }

        /**
         * Precomputes neighbor ranges for each of the 64 squares for the given odd kernel size (e.g. 3).
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

            int off = 0;
            for (int sq = 0; sq < 64; sq++) {
                start[sq] = off;
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
                        square[off] = inRow * 8 + inCol;
                        kernelIndex[off] = ky * kernel + kx;
                        off++;
	    }
	}
            }
            start[64] = off;

            int[] squareTrim = Arrays.copyOf(square, off);
            int[] kernelTrim = Arrays.copyOf(kernelIndex, off);
            return new KernelNeighbors(start, squareTrim, kernelTrim);
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
         */
        DenseLayer(int inDim, int outDim, float[] weights, float[] bias) {
            this.inDim = inDim;
            this.outDim = outDim;
            this.weights = weights;
            this.bias = bias;
        }

        /**
         * Runs the dense layer and applies the optional activation.
         */
        void forward(float[] input, float[] output, Activation activation) {
            for (int o = 0; o < outDim; o++) {
                float acc = bias[o];
                int weightBase = o * inDim;
                for (int i = 0; i < inDim; i++) {
                    acc += weights[weightBase + i] * input[i];
                }
                if (activation == Activation.RELU && acc < 0f) {
                    acc = 0f;
                }
                output[o] = acc;
            }
        }
    }

    /**
     * Parameter set for an SE (squeeze-and-excitation) unit.
     */
    private static final class SeUnit {

        /**
         * Number of channels in the residual block.
         */
        final int channels;

        /**
         * Hidden dimension used in the SE bottleneck.
         */
        final int hidden;

        /**
         * First-layer weights (channels × hidden).
         */
        final float[] w1;

        /**
         * First-layer biases.
         */
        final float[] b1;

        /**
         * Second-layer weights (hidden × 2*channels).
         */
        final float[] w2;

        /**
         * Second-layer biases.
         */
        final float[] b2;

        /**
         * Creates an SE unit descriptor.
         */
        SeUnit(int channels, int hidden, float[] w1, float[] b1, float[] w2, float[] b2) {
            this.channels = channels;
            this.hidden = hidden;
            this.w1 = w1;
            this.b1 = b1;
            this.w2 = w2;
            this.b2 = b2;
        }
    }

    /**
     * Residual block containing convolutional layers and an optional SE unit.
     */
    private record ResidualBlock(ConvLayer conv1, ConvLayer conv2, SeUnit se) {
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
         * Reads an LC0J weights file and builds all layer descriptors.
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
         * @param buf source buffer
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
         * @param buf source buffer
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
         * Allows buffers to be reclaimed or reinitialized later.
         */
        static void clearThreadLocal() {
            WORKSPACE.remove();
        }

        /**
         * Evaluates the model and returns logits, WDL and scalar value.
         *
         * @param w            parsed weights
         * @param encodedInput LC0 planes (length {@code inputChannels * 64})
         * @return inference result
         */
        static Prediction evaluate(Weights w, float[] encodedInput) {
            Workspace ws = WORKSPACE.get();
            ws.ensureCapacity(w);

            w.inputLayer.forwardNoBias(encodedInput, ws.current);
            addBiasReLU(ws.current, w.inputLayer.bias, ws.current);

            for (ResidualBlock block : w.blocks) {
                block.conv1.forwardNoBias(ws.current, ws.tmp);
                addBiasReLU(ws.tmp, block.conv1.bias, ws.tmp);

                block.conv2.forwardNoBias(ws.tmp, ws.scratch);
                if (block.se != null) {
                    applySe(block.se, ws, ws.scratch, block.conv2.bias, ws.current, ws.next);
                } else {
                    addResidualReLU(ws.scratch, block.conv2.bias, ws.current, ws.next);
                }
                float[] swap = ws.current;
                ws.current = ws.next;
                ws.next = swap;
            }

            w.policyStem.forwardNoBias(ws.current, ws.policyHidden);
            addBiasReLU(ws.policyHidden, w.policyStem.bias, ws.policyHidden);
            w.policyOutput.forwardNoBias(ws.policyHidden, ws.policyPlanes);
            addBias(ws.policyPlanes, w.policyOutput.bias, ws.policyPlanes);
            float[] policy = mapPolicy(ws.policyPlanes, w.policyMap);

            w.valueConv.forwardNoBias(ws.current, ws.valueInput);
            addBiasReLU(ws.valueInput, w.valueConv.bias, ws.valueInput);
            w.valueFc1.forward(ws.valueInput, ws.fc1, Activation.RELU);
            w.valueFc2.forward(ws.fc1, ws.logits, Activation.NONE);
            float[] raw = softmax(ws.logits);
            // LC0 WDL outputs are ordered [win, draw, loss] from the side-to-move ("our")
            // perspective.
            float win = raw[0];
            float draw = raw[1];
            float loss = raw[2];
            float[] wdl = new float[] { win, draw, loss };
            float scalar = win - loss;

            return new Prediction(policy, wdl, scalar);
        }

        /**
         * Runs the value head just far enough to return raw WDL probabilities and stm
         * flag.
         *
         * @param w            parsed weights
         * @param encodedInput LC0 planes (length {@code inputChannels * 64})
         * @return debug-only values
         */
        static DebugValue debugValue(Weights w, float[] encodedInput) {
            Workspace ws = WORKSPACE.get();
            ws.ensureCapacity(w);

            w.inputLayer.forwardNoBias(encodedInput, ws.current);
            addBiasReLU(ws.current, w.inputLayer.bias, ws.current);

            for (ResidualBlock block : w.blocks) {
                block.conv1.forwardNoBias(ws.current, ws.tmp);
                addBiasReLU(ws.tmp, block.conv1.bias, ws.tmp);

                block.conv2.forwardNoBias(ws.tmp, ws.scratch);
                if (block.se != null) {
                    applySe(block.se, ws, ws.scratch, block.conv2.bias, ws.current, ws.next);
                } else {
                    addResidualReLU(ws.scratch, block.conv2.bias, ws.current, ws.next);
                }
                float[] swap = ws.current;
                ws.current = ws.next;
                ws.next = swap;
            }

            w.valueConv.forwardNoBias(ws.current, ws.valueInput);
            addBiasReLU(ws.valueInput, w.valueConv.bias, ws.valueInput);
            w.valueFc1.forward(ws.valueInput, ws.fc1, Activation.RELU);
            w.valueFc2.forward(ws.fc1, ws.logits, Activation.NONE);
            float[] raw = softmax(ws.logits);
            boolean blackToMove = encodedInput[108 * 64] > 0.5f;
            return new DebugValue(raw, blackToMove);
        }

        /**
         * Mutable buffers used during convolutional evaluation.
         */
        private static final class Workspace {

            /** Current trunk activations [trunkChannels, 64]. */
            float[] current = new float[0];

            /** Next trunk activations [trunkChannels, 64] used for residual updates. */
            float[] next = new float[0];

            /** Temporary buffer for intermediate conv output [trunkChannels, 64]. */
            float[] tmp = new float[0];

            /** Scratch buffer for conv output [trunkChannels, 64]. */
            float[] scratch = new float[0];

            /** Policy stem activations [trunkChannels, 64]. */
            float[] policyHidden = new float[0];

            /** Raw policy planes [policyChannels, 64]. */
            float[] policyPlanes = new float[0];

            /** Value head conv activations [valueChannels, 64]. */
            float[] valueInput = new float[0];

            /** Value head hidden activations (dense). */
            float[] fc1 = new float[0];

            /** Value head logits (dense). */
            float[] logits = new float[0];

            /** SE pooled vector (per-channel mean). */
            float[] sePooled = new float[0];

            /** SE hidden activations. */
            float[] seHidden = new float[0];

            /** SE gate outputs (gamma and beta concatenated). */
            float[] seGates = new float[0];

            /**
             * Ensures all non-SE buffers match the current model dimensions.
             *
             * @param w current model weights
             */
            void ensureCapacity(Weights w) {
                int trunkSize = w.trunkChannels * 64;
                if (current.length != trunkSize)
                    current = new float[trunkSize];
                if (next.length != trunkSize)
                    next = new float[trunkSize];
                if (tmp.length != trunkSize)
                    tmp = new float[trunkSize];
                if (scratch.length != trunkSize)
                    scratch = new float[trunkSize];
                if (policyHidden.length != trunkSize)
                    policyHidden = new float[trunkSize];

                int policyPlanesSize = w.policyChannels * 64;
                if (policyPlanes.length != policyPlanesSize)
                    policyPlanes = new float[policyPlanesSize];

                int valueInputSize = w.valueChannels * 64;
                if (valueInput.length != valueInputSize)
                    valueInput = new float[valueInputSize];

                if (fc1.length != w.valueFc1.outDim)
                    fc1 = new float[w.valueFc1.outDim];
                if (logits.length != w.valueFc2.outDim)
                    logits = new float[w.valueFc2.outDim];
            }

            /**
             * Ensures SE-specific buffers match the provided SE unit dimensions.
             *
             * @param se SE unit controlling buffer sizes
             */
            void ensureSeCapacity(SeUnit se) {
                if (sePooled.length != se.channels)
                    sePooled = new float[se.channels];
                if (seHidden.length != se.hidden)
                    seHidden = new float[se.hidden];
                int gatesSize = se.channels * 2;
                if (seGates.length != gatesSize)
                    seGates = new float[gatesSize];
            }
        }

        /**
         * Adds bias and applies ReLU when accumulating convolution outputs.
         *
         * @param convOut convolution output tensor
         * @param bias bias vector per channel
         * @param dest destination tensor
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
         * @param bias bias vector per channel
         * @param dest destination tensor
         * @param start inclusive start channel
         * @param end exclusive end channel
         */
        private static void addBiasReLUChannels(float[] convOut, float[] bias, float[] dest, int start, int end) {
            for (int ch = start; ch < end; ch++) {
                int base = ch * 64;
                float b = bias[ch];
                for (int i = 0; i < 64; i++) {
                    float val = convOut[base + i] + b;
                    dest[base + i] = val > 0f ? val : 0f;
                }
            }
        }

        /**
         * Adds bias to convolution outputs without activation.
         *
         * @param convOut convolution output tensor
         * @param bias bias vector per channel
         * @param dest destination tensor
         */
        private static void addBias(float[] convOut, float[] bias, float[] dest) {
            int channels = bias.length;
            if (Parallel.enabledForChannels(channels)) {
                Parallel.forRange(0, channels, (start, end) -> {
                    for (int ch = start; ch < end; ch++) {
                        int base = ch * 64;
                        float b = bias[ch];
                        for (int i = 0; i < 64; i++) {
                            dest[base + i] = convOut[base + i] + b;
                        }
                    }
                });
            } else {
                for (int ch = 0; ch < channels; ch++) {
                    int base = ch * 64;
                    float b = bias[ch];
                    for (int i = 0; i < 64; i++) {
                        dest[base + i] = convOut[base + i] + b;
                    }
                }
            }
        }

        /**
         * Combines residual input with convolution outputs then applies ReLU.
         *
         * @param convOut convolution output tensor
         * @param bias bias vector per channel
         * @param residual residual tensor to add
         * @param dest destination tensor
         */
        private static void addResidualReLU(float[] convOut, float[] bias, float[] residual, float[] dest) {
            int channels = bias.length;
            for (int ch = 0; ch < channels; ch++) {
                int base = ch * 64;
                float b = bias[ch];
                for (int i = 0; i < 64; i++) {
                    float val = convOut[base + i] + b + residual[base + i];
                    dest[base + i] = val > 0f ? val : 0f;
                }
            }
        }

        /**
         * Executes the squeeze-and-excitation block when present in a residual block.
         *
         * @param se SE unit descriptor
         * @param ws workspace buffers
         * @param convOut trunk convolution output
         * @param bias trunk bias vector
         * @param residual residual input
         * @param dest destination tensor
         */
        private static void applySe(SeUnit se, Workspace ws, float[] convOut, float[] bias, float[] residual,
                float[] dest) {
            ws.ensureSeCapacity(se);
            sePoolWithBias(convOut, bias, se.channels, ws.sePooled);
            denseRelu(ws.sePooled, se.w1, se.b1, se.channels, se.hidden, ws.seHidden);
            dense(ws.seHidden, se.w2, se.b2, se.hidden, se.channels * 2, ws.seGates);
            seCombine(convOut, bias, residual, dest, se.channels, ws.seGates);
        }

        /**
         * Global-average-pools each channel (mean over 64 squares) and adds bias to form the SE input vector.
         *
         * @param convOut convolution output
         * @param bias bias vector per channel
         * @param channels number of channels
         * @param pooledOut output vector
         */
        private static void sePoolWithBias(float[] convOut, float[] bias, int channels, float[] pooledOut) {
            float invSquares = 1f / 64f;
            for (int ch = 0; ch < channels; ch++) {
                int base = ch * 64;
                float sum = 0f;
                for (int i = 0; i < 64; i++) {
                    sum += convOut[base + i];
                }
                pooledOut[ch] = sum * invSquares + bias[ch];
            }
        }

        /**
         * Dense layer with ReLU activation.
         *
         * @param input input vector
         * @param weights weight matrix
         * @param bias bias vector
         * @param inDim input dimension
         * @param outDim output dimension
         * @param out output vector
         */
        private static void denseRelu(float[] input, float[] weights, float[] bias, int inDim, int outDim,
                float[] out) {
            for (int o = 0; o < outDim; o++) {
                float acc = bias[o];
                int weightBase = o * inDim;
                for (int i = 0; i < inDim; i++) {
                    acc += weights[weightBase + i] * input[i];
                }
                out[o] = acc > 0f ? acc : 0f;
            }
        }

        /**
         * Dense layer without activation.
         *
         * @param input input vector
         * @param weights weight matrix
         * @param bias bias vector
         * @param inDim input dimension
         * @param outDim output dimension
         * @param out output vector
         */
        private static void dense(float[] input, float[] weights, float[] bias, int inDim, int outDim, float[] out) {
            for (int o = 0; o < outDim; o++) {
                float acc = bias[o];
                int weightBase = o * inDim;
                for (int i = 0; i < inDim; i++) {
                    acc += weights[weightBase + i] * input[i];
                }
                out[o] = acc;
            }
        }

        /**
         * Applies SE gating and combines with residual input, then applies ReLU.
         *
         * <p>{@code gates} contains {@code [gammaLogit[channels], betaExtra[channels]]}.
         *
         * @param convOut convolution output tensor
         * @param bias bias vector per channel
         * @param residual residual tensor
         * @param dest destination tensor
         * @param channels number of channels
         * @param gates gate logits
         */
        private static void seCombine(float[] convOut, float[] bias, float[] residual, float[] dest, int channels,
                float[] gates) {
            for (int ch = 0; ch < channels; ch++) {
                float gamma = sigmoid(gates[ch]);
                float betaExtra = gates[ch + channels];
                float b = bias[ch];
                int base = ch * 64;
                for (int i = 0; i < 64; i++) {
                    float z = convOut[base + i] + b;
                    float val = gamma * z + residual[base + i] + betaExtra;
                    dest[base + i] = val > 0f ? val : 0f;
                }
            }
        }

        /**
         * Maps the raw policy output planes to the compressed LC0 move-logit vector.
         *
         * <p>The weights file provides {@code policyMap} indices into the uncompressed plane tensor; out-of-range
         * indices are treated as zero.
         *
         * @param planes raw policy planes
         * @param policyMap index map into the compressed move logits
         * @return compressed policy logits
         */
        private static float[] mapPolicy(float[] planes, int[] policyMap) {
            float[] out = new float[policyMap.length];
            for (int i = 0; i < policyMap.length; i++) {
                int idx = policyMap[i];
                if (idx >= 0 && idx < planes.length) {
                    out[i] = planes[idx];
                }
            }
            return out;
        }

        /**
         * Applies softmax to logits to produce probabilities (numerically stabilized by subtracting max).
         *
         * @param logits input logits
         * @return normalized probabilities of the same length
         */
        private static float[] softmax(float[] logits) {
            float max = Float.NEGATIVE_INFINITY;
            for (float val : logits) {
                if (val > max)
                    max = val;
            }
            float sum = 0f;
            float[] out = new float[logits.length];
            for (int i = 0; i < logits.length; i++) {
                float exp = (float) Math.exp(logits[i] - max);
                out[i] = exp;
                sum += exp;
            }
            if (sum > 0f) {
                for (int i = 0; i < out.length; i++) {
                    out[i] /= sum;
                }
            }
            return out;
        }

        /**
         * Sigmoid helper used by the SE gating mechanism.
         *
         * @param x input logit
         * @return sigmoid(x)
         */
        private static float sigmoid(float x) {
            return 1f / (1f + (float) Math.exp(-x));
        }
    }
}
