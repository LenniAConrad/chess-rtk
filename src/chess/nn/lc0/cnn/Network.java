package chess.nn.lc0.cnn;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import chess.gpu.BackendNames;
import chess.nn.lc0.cnn.cuda.Backend;

/**
 * LCZero (LC0) "classical" policy+value network evaluator.
 *
 * <p>
 * This class loads ChessRTK LC0 CNN {@code .bin} weights and runs a
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
 * <li>{@code -Dcrtk.lc0.backend=auto} (default): use the first available GPU
 * backend
 * (CUDA, then ROCm, then oneAPI) that initializes successfully, else CPU</li>
 * <li>{@code -Dcrtk.lc0.backend=cpu}: force CPU</li>
 * <li>{@code -Dcrtk.lc0.backend=cuda}: force CUDA (throws if init
 * fails/unavailable)</li>
 * <li>{@code -Dcrtk.lc0.backend=rocm|amd|hip}: force ROCm (AMD)</li>
 * <li>{@code -Dcrtk.lc0.backend=oneapi|intel}: force oneAPI (Intel)</li>
 * </ul>
 *
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
     * CPU backend identifier.
     */
    public static final String BACKEND_CPU = BackendNames.CPU;

    /**
     * CUDA backend identifier.
     */
    public static final String BACKEND_CUDA = BackendNames.CUDA;

    /**
     * ROCm backend identifier.
     */
    public static final String BACKEND_ROCM = BackendNames.ROCM;

    /**
     * oneAPI backend identifier.
     */
    public static final String BACKEND_ONEAPI = BackendNames.ONEAPI;

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
    private final chess.nn.lc0.cnn.rocm.Backend rocm; // ROCm backend (when non-null)

    /**
     * oneAPI backend instance (null when inactive).
     */
    private final chess.nn.lc0.cnn.oneapi.Backend oneapi; // oneAPI backend (when non-null)

    /**
     * Internal constructor selecting the active backend.
     * @param weights network weights
     * @param cuda cuda value
     * @param rocm rocm value
     * @param oneapi oneapi value
     */
    private Network(Weights weights, Backend cuda, chess.nn.lc0.cnn.rocm.Backend rocm, chess.nn.lc0.cnn.oneapi.Backend oneapi) {
        this.weights = weights;
        this.cuda = cuda;
        this.rocm = rocm;
        this.oneapi = oneapi;
    }

    /**
     * Loads a ChessRTK LC0 CNN {@code .bin} weights file.
     *
     * <p>
     * Depending on {@code -Dcrtk.lc0.backend} and GPU availability, this will load
     * either the CPU or a GPU backend.
     *
     * @param path path to a ChessRTK LC0 CNN binary weights file
     * @return network evaluator
     * @throws IOException if the weights cannot be read/parsed, or if a forced
     *                     GPU backend fails to initialize
     */
    public static Network load(Path path) throws IOException {
        BackendRequest request = BackendRequest.fromSystemProperty();
        BackendAvailability availability = BackendAvailability.detect();
        request.requireAvailable(availability);

        Network accelerated = loadAccelerated(path, request, availability);
        return accelerated != null ? accelerated : loadCpu(path);
    }

    /**
     * Loads a network using the CPU backend.
     *
     * @param path path to the weights file
     * @return CPU-backed network instance
     * @throws IOException if the weights cannot be read or parsed
     */
    public static Network loadCpu(Path path) throws IOException {
        return new Network(Weights.load(path), null, null, null);
    }

    /**
     * Attempts requested GPU backends in priority order.
     *
     * @param path path to the weights file
     * @param request requested backend preferences
     * @param availability detected backend availability
     * @return initialized accelerated network, or {@code null} to fall back to CPU
     * @throws IOException if a forced backend fails to initialize
     */
    private static Network loadAccelerated(Path path, BackendRequest request, BackendAvailability availability)
            throws IOException {
        Network network = tryLoadBackend(path, request.preferCuda(), availability.cuda(), request.forceCuda(), "CUDA",
                Network::loadCuda);
        if (network != null) {
            return network;
        }
        network = tryLoadBackend(path, request.preferRocm(), availability.rocm(), request.forceRocm(), "ROCm",
                Network::loadRocm);
        if (network != null) {
            return network;
        }
        return tryLoadBackend(path, request.preferOneapi(), availability.oneapi(), request.forceOneapi(), "oneAPI",
                Network::loadOneapi);
    }

    /**
     * Attempts one optional backend and preserves forced-backend failure behavior.
     *
     * @param path path to the weights file
     * @param preferred whether this backend should be attempted
     * @param available whether this backend is available
     * @param forced whether failure should be fatal
     * @param label user-facing backend label
     * @param loader backend loader
     * @return initialized network, or {@code null} when skipped or optional setup fails
     * @throws IOException if a forced backend fails to initialize
     */
    private static Network tryLoadBackend(Path path, boolean preferred, boolean available, boolean forced, String label,
            BackendLoader loader) throws IOException {
        if (!preferred || !available) {
            return null;
        }
        try {
            return loader.load(path);
        } catch (RuntimeException e) {
            if (forced) {
                throw new IOException(label + " backend requested but failed to initialize.", e);
            }
            return null;
        }
    }

    /**
     * Backend loader callback.
     */
    @FunctionalInterface
    private interface BackendLoader {

        /**
         * Loads a backend-specific network.
         *
         * @param path path to the weights file
         * @return initialized network
         */
        Network load(Path path);
    }

    /**
     * Parsed LC0 backend request.
     */
    private record BackendRequest(
        /**
         * Stores the prefer cuda.
         */
        boolean preferCuda,
        /**
         * Stores the prefer rocm.
         */
        boolean preferRocm,
        /**
         * Stores the prefer oneapi.
         */
        boolean preferOneapi,
        /**
         * Stores the force cuda.
         */
        boolean forceCuda,
        /**
         * Stores the force rocm.
         */
        boolean forceRocm,
        /**
         * Stores the force oneapi.
         */
        boolean forceOneapi
    ) {

        /**
         * Parses {@code crtk.lc0.backend} into backend preferences.
         *
         * @return parsed backend request
         */
        static BackendRequest fromSystemProperty() {
            String backend = System.getProperty("crtk.lc0.backend");
            if (backend == null) {
                backend = "auto";
            }
            backend = backend.trim().toLowerCase();
            boolean auto = backend.equals("auto");
            boolean cuda = backend.equals(BACKEND_CUDA);
            boolean rocm = backend.equals(BACKEND_ROCM) || backend.equals("amd") || backend.equals("hip");
            boolean oneapi = backend.equals(BACKEND_ONEAPI) || backend.equals("intel");
            return new BackendRequest(auto || cuda, auto || rocm, auto || oneapi, cuda, rocm, oneapi);
        }

        /**
         * Throws when a forced backend is unavailable.
         *
         * @param availability detected backend availability
         * @throws IOException if a forced backend is unavailable
         */
        void requireAvailable(BackendAvailability availability) throws IOException {
            requireBackendAvailable(forceCuda, availability.cuda(),
                    "CUDA backend requested but unavailable (JNI library not loaded and/or no CUDA device).");
            requireBackendAvailable(forceRocm, availability.rocm(),
                    "ROCm backend requested but unavailable (JNI library not loaded and/or no ROCm device).");
            requireBackendAvailable(forceOneapi, availability.oneapi(),
                    "oneAPI backend requested but unavailable (JNI library not loaded and/or no Intel GPU device).");
        }
    }

    /**
     * Detected LC0 backend availability.
     */
    private record BackendAvailability(
        /**
         * Stores the cuda.
         */
        boolean cuda,
        /**
         * Stores the rocm.
         */
        boolean rocm,
        /**
         * Stores the oneapi.
         */
        boolean oneapi
    ) {

        /**
         * Detects available native backends.
         *
         * @return availability snapshot
         */
        static BackendAvailability detect() {
            return new BackendAvailability(
                    Backend.isAvailable(),
                    chess.nn.lc0.cnn.rocm.Backend.isAvailable(),
                    chess.nn.lc0.cnn.oneapi.Backend.isAvailable());
        }
    }

    /**
     * Throws when a forced backend is unavailable.
     *
     * @param forced whether the backend was explicitly requested
     * @param available whether the backend is available
     * @param message exception message
     * @throws IOException if the forced backend is unavailable
     */
    private static void requireBackendAvailable(boolean forced, boolean available, String message) throws IOException {
        if (forced && !available) {
            throw new IOException(message);
        }
    }

    /**
     * Loads the policy map from a ChessRTK LC0 CNN {@code .bin} weights file.
     *
     * <p>
     * This always parses the weights on CPU and returns a copy of the policy
     * map (move-index mapping) used to compress the raw 73-plane policy.
     * </p>
     *
     * @param path path to a ChessRTK LC0 CNN binary weights file
     * @return copy of the policy map array
     * @throws IOException if the weights cannot be read/parsed
     */
    public static int[] loadPolicyMap(Path path) throws IOException {
        Weights weights = Weights.load(path);
        return Arrays.copyOf(weights.policyMap, weights.policyMap.length);
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
        try (RocmBackendHolder holder = new RocmBackendHolder(chess.nn.lc0.cnn.rocm.Backend.create(path))) {
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
        try (OneapiBackendHolder holder = new OneapiBackendHolder(chess.nn.lc0.cnn.oneapi.Backend.create(path))) {
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
         /**
         * Stores the backend.
         */
         private chess.nn.lc0.cnn.rocm.Backend backend;

         /**
         * Creates a new rocm backend holder instance.
         * @param backend backend
         */
         private RocmBackendHolder(chess.nn.lc0.cnn.rocm.Backend backend) {
            this.backend = backend;
        }

         /**
         * Handles detach.
         */
         private void detach() {
            backend = null;
        }

         /**
         * Handles close.
         */
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
         /**
         * Stores the backend.
         */
         private chess.nn.lc0.cnn.oneapi.Backend backend;

         /**
         * Creates a new oneapi backend holder instance.
         * @param backend backend
         */
         private OneapiBackendHolder(chess.nn.lc0.cnn.oneapi.Backend backend) {
            this.backend = backend;
        }

         /**
         * Handles detach.
         */
         private void detach() {
            backend = null;
        }

         /**
         * Handles close.
         */
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
            return BACKEND_CUDA;
        }
        if (rocm != null) {
            return BACKEND_ROCM;
        }
        if (oneapi != null) {
            return BACKEND_ONEAPI;
        }
        return BACKEND_CPU;
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
        return predictEncoded(encodedPlanes, null);
    }

    /**
     * Runs one forward pass on already-encoded LC0 planes and optionally
     * captures intermediate CPU activations.
     *
     * <p>
     * Activation capture is available for the Java CPU backend. Accelerated
     * backends return the normal prediction and ignore the sink because native
     * backends do not expose intermediate tensors.
     * </p>
     *
     * @param encodedPlanes encoded planes (length {@code inputChannels * 64})
     * @param activationSink optional activation collector
     * @return policy logits, WDL probabilities, and scalar {@code W-L} value
     */
    public Prediction predictEncoded(float[] encodedPlanes, chess.nn.ActivationSink activationSink) {
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
        return Evaluator.evaluate(weights, encodedPlanes, activationSink);
    }

    /**
     * Runs forward passes for already-encoded LC0 plane batches.
     *
     * @param encodedBatch encoded planes aligned by position
     * @return predictions aligned with {@code encodedBatch}
     */
    public List<Prediction> predictEncodedBatch(List<float[]> encodedBatch) {
        if (encodedBatch == null) {
            throw new IllegalArgumentException("encodedBatch == null");
        }
        if (cuda != null) {
            return predictEncodedBatchSequential(encodedBatch);
        }
        if (rocm != null) {
            return predictEncodedBatchSequential(encodedBatch);
        }
        if (oneapi != null) {
            return predictEncodedBatchSequential(encodedBatch);
        }
        int expected = weights.inputChannels * 64;
        for (float[] encodedPlanes : encodedBatch) {
            if (encodedPlanes == null || encodedPlanes.length != expected) {
                throw new IllegalArgumentException("Encoded input must be " + expected + " floats.");
            }
        }
        return Evaluator.evaluateBatch(weights, encodedBatch);
    }

    /**
     * Runs batch prediction through single-position backend calls.
     * @param encodedBatch encoded batch value
     * @return predict encoded batch sequential result
     */
    private List<Prediction> predictEncodedBatchSequential(List<float[]> encodedBatch) {
        List<Prediction> out = new ArrayList<>(encodedBatch.size());
        for (float[] encodedPlanes : encodedBatch) {
            out.add(predictEncoded(encodedPlanes));
        }
        return out;
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
     * <p>
     * These values summarize the network's structure and parameter count.
     * </p>
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
         * Stores the policy channels.
         */
        int policyChannels,
        /**
         * Stores the value channels.
         */
        int valueChannels,
        /**
         * Stores the policy size.
         */
        int policySize,
        /**
         * Stores the parameter count.
         */
        long parameterCount
    ) {
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
    public record DebugValue(
        /**
         * Stores the raw wdl.
         */
        float[] rawWdl,
        /**
         * Stores the black to move.
         */
        boolean blackToMove
    ) {

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
    public record Prediction(
        /**
         * Stores the policy.
         */
        float[] policy,
        /**
         * Stores the wdl.
         */
        float[] wdl,
        /**
         * Stores the value.
         */
        float value
    ) {

        /**
         * Equality compares {@link #value()} exactly (bitwise) and compares arrays by
         * content.
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
         * Hash is based on the policy/WDL array contents and the scalar
         * {@link #value()}.
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
}
