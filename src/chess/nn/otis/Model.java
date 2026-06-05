package chess.nn.otis;

import chess.core.Move;
import chess.core.MoveList;
import chess.core.Piece;
import chess.core.Position;
import chess.gpu.BackendNames;
import chess.nn.ActivationSink;
import chess.nn.lc0.bt4.PolicyEncoder;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Lightweight OTIS policy/WDL model used by the workbench while trained
 * weights are not available yet.
 *
 * <p>The binary format deliberately stores deterministic randomized i249-style
 * tensors. That keeps the placeholder file easy to regenerate while still
 * exercising the real model-loading, policy, WDL, activation, and MCTS wiring
 * instead of hard-coding a missing-model path.</p>
 */
public final class Model implements AutoCloseable {

    /**
     * Default randomized OTIS weights path.
     */
    public static final Path DEFAULT_WEIGHTS = Path.of("models/otis_policy_wdl_random.bin");

    /**
     * OTIS binary magic, ASCII {@code OTIS}.
     */
    private static final int MAGIC = 0x5349544F;

    /**
     * Supported OTIS placeholder binary version.
     */
    private static final int VERSION = 2;

    /**
     * chess-nn-playground {@code simple_18} input planes.
     */
    public static final int INPUT_PLANES = 18;

    /**
     * Default randomized placeholder trunk width.
     */
    public static final int DEFAULT_TRUNK_CHANNELS = 64;

    /**
     * Default randomized placeholder sheaf/MLP block count.
     */
    public static final int DEFAULT_BLOCKS = 2;

    /**
     * Default randomized placeholder compressed policy width.
     */
    public static final int DEFAULT_POLICY_SIZE = PolicyEncoder.POLICY_SIZE;

    /**
     * Stored float parameter count for the default randomized placeholder.
     */
    public static final int DEFAULT_PARAMETER_COUNT = 271_975;

    /**
     * Board squares.
     */
    private static final int SQUARES = 64;

    /**
     * Side-to-move-oriented piece-state channels: empty plus six us/them pieces.
     */
    public static final int PIECE_STATE_PLANES = 13;

    /**
     * Square-token raw projection width.
     */
    public static final int RAW_DIM = 32;

    /**
     * Square-token piece-state projection width.
     */
    public static final int PIECE_DIM = 16;

    /**
     * Square coordinate projection width.
     */
    public static final int COORD_DIM = 8;

    /**
     * Fused square-token input width.
     */
    private static final int FUSE_DIM = RAW_DIM + PIECE_DIM + COORD_DIM;

    /**
     * Readout hidden width.
     */
    public static final int HIDDEN_DIM = 96;

    /**
     * Triad-defect diagnostic width.
     */
    private static final int TRIAD_DIM = 4;

    /**
     * Board-statistics diagnostic width.
     */
    private static final int BOARD_STATS_DIM = 8;

    /**
     * Typed tactical relations from i018/i249.
     */
    public static final int RELATION_COUNT = 12;

    /**
     * Stalk dimension used by the compact Java sheaf step.
     */
    public static final int STALK_DIM = 8;

    /**
     * Relation names, ordered to match the chess-nn-playground i018/i249 models.
     */
    public static final String[] RELATION_NAMES = {
            "us_attacks_them_piece",
            "them_attacks_us_piece",
            "us_defends_us_piece",
            "them_defends_them_piece",
            "us_attacks_empty_near_king",
            "them_attacks_empty_near_king",
            "bishop_ray_visible",
            "rook_ray_visible",
            "queen_ray_visible",
            "knight_attack",
            "pawn_attack_forward_oriented",
            "king_ray_pin_candidate"
    };

    /**
     * i018/i249 relation signs separating attacker-target and same-side context.
     */
    private static final float[] RELATION_SIGNS = {
            -1.0f, -1.0f, 1.0f, 1.0f, -1.0f, -1.0f,
            -1.0f, -1.0f, -1.0f, -1.0f, -1.0f, -1.0f
    };

    /**
     * Stable sheaf heat-step scale, matching the i018 initialization.
     */
    private static final float SHEAF_ETA = 0.125f;

    /**
     * Maximum accepted model name length.
     */
    private static final int MAX_NAME_BYTES = 4096;

    /**
     * Pure-Java CPU backend identifier reported by {@link #backend()}.
     */
    public static final String BACKEND_CPU = "java-i249-random-v2";

    /**
     * CUDA backend identifier reported by {@link #backend()}.
     */
    public static final String BACKEND_CUDA = BackendNames.CUDA;

    /**
     * ROCm backend identifier reported by {@link #backend()}.
     */
    public static final String BACKEND_ROCM = BackendNames.ROCM;

    /**
     * oneAPI backend identifier reported by {@link #backend()}.
     */
    public static final String BACKEND_ONEAPI = BackendNames.ONEAPI;

    /**
     * Loaded random projection weights (null when a GPU backend is active).
     */
    private final Weights weights;

    /**
     * CUDA backend instance (null when inactive).
     */
    private final chess.nn.otis.cuda.Backend cuda;

    /**
     * ROCm backend instance (null when inactive).
     */
    private final chess.nn.otis.rocm.Backend rocm;

    /**
     * oneAPI backend instance (null when inactive).
     */
    private final chess.nn.otis.oneapi.Backend oneapi;

    /**
     * Creates a model bound to a single active backend.
     *
     * @param weights parsed weights for the CPU path, or null when a GPU backend is active
     * @param cuda CUDA backend, or null
     * @param rocm ROCm backend, or null
     * @param oneapi oneAPI backend, or null
     */
    private Model(Weights weights, chess.nn.otis.cuda.Backend cuda,
            chess.nn.otis.rocm.Backend rocm, chess.nn.otis.oneapi.Backend oneapi) {
        this.weights = weights;
        this.cuda = cuda;
        this.rocm = rocm;
        this.oneapi = oneapi;
    }

    /**
     * Loads an OTIS model from disk, selecting an inference backend.
     *
     * <p>Backend selection follows {@code -Dcrtk.otis.backend}:
     * <ul>
     *   <li>{@code auto} (default): first available GPU backend (CUDA, then ROCm,
     *       then oneAPI) that initializes, else the pure-Java CPU path</li>
     *   <li>{@code cpu}: force the pure-Java path</li>
     *   <li>{@code cuda}: force CUDA (throws if init fails/unavailable)</li>
     *   <li>{@code rocm|amd|hip}: force ROCm (AMD)</li>
     *   <li>{@code oneapi|intel}: force oneAPI (Intel)</li>
     * </ul>
     *
     * @param path model path
     * @return loaded model
     * @throws IOException if the file cannot be read/parsed, or if a forced GPU backend fails to initialize
     */
    public static Model load(Path path) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("path == null");
        }
        BackendRequest request = BackendRequest.fromSystemProperty();
        BackendAvailability availability = BackendAvailability.detect();
        request.requireAvailable(availability);
        Model accelerated = loadAccelerated(path, request, availability);
        return accelerated != null ? accelerated : loadCpu(path);
    }

    /**
     * Attempts requested GPU backends in priority order.
     *
     * @param path path to the weights file
     * @param request requested backend preferences
     * @param availability detected backend availability
     * @return initialized accelerated model, or {@code null} to fall back to CPU
     * @throws IOException if a forced backend fails to initialize
     */
    private static Model loadAccelerated(Path path, BackendRequest request, BackendAvailability availability)
            throws IOException {
        Model model = tryLoadBackend(path, request.preferCuda(), availability.cuda(), request.forceCuda(), "CUDA",
                Model::loadCuda);
        if (model != null) {
            return model;
        }
        model = tryLoadBackend(path, request.preferRocm(), availability.rocm(), request.forceRocm(), "ROCm",
                Model::loadRocm);
        if (model != null) {
            return model;
        }
        return tryLoadBackend(path, request.preferOneapi(), availability.oneapi(), request.forceOneapi(), "oneAPI",
                Model::loadOneapi);
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
     * @return initialized model, or {@code null} when skipped or optional setup fails
     * @throws IOException if a forced backend fails to initialize
     */
    private static Model tryLoadBackend(Path path, boolean preferred, boolean available, boolean forced, String label,
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
     * Loads a model using the CUDA backend.
     *
     * @param path path to the weights file
     * @return CUDA-backed model instance
     */
    private static Model loadCuda(Path path) {
        try (CudaBackendHolder holder = new CudaBackendHolder(chess.nn.otis.cuda.Backend.create(path))) {
            Model model = new Model(null, holder.backend, null, null);
            holder.detach();
            return model;
        }
    }

    /**
     * Loads a model using the ROCm backend.
     *
     * @param path path to the weights file
     * @return ROCm-backed model instance
     */
    private static Model loadRocm(Path path) {
        try (RocmBackendHolder holder = new RocmBackendHolder(chess.nn.otis.rocm.Backend.create(path))) {
            Model model = new Model(null, null, holder.backend, null);
            holder.detach();
            return model;
        }
    }

    /**
     * Loads a model using the oneAPI backend.
     *
     * @param path path to the weights file
     * @return oneAPI-backed model instance
     */
    private static Model loadOneapi(Path path) {
        try (OneapiBackendHolder holder = new OneapiBackendHolder(chess.nn.otis.oneapi.Backend.create(path))) {
            Model model = new Model(null, null, null, holder.backend);
            holder.detach();
            return model;
        }
    }

    /**
     * Backend loader callback.
     */
    @FunctionalInterface
    private interface BackendLoader {

        /**
         * Loads a backend-specific model.
         *
         * @param path path to the weights file
         * @return initialized model
         */
        Model load(Path path);
    }

    /**
     * Helper that owns a CUDA backend until detached or closed.
     */
    private static final class CudaBackendHolder implements AutoCloseable {

        /**
         * Owned backend instance, or {@code null} once detached.
         */
        private chess.nn.otis.cuda.Backend backend;

        /**
         * Creates a holder that owns the provided backend until detached or closed.
         *
         * @param backend backend instance to manage
         */
        private CudaBackendHolder(chess.nn.otis.cuda.Backend backend) {
            this.backend = backend;
        }

        /**
         * Releases ownership so the backend is not closed by this holder.
         */
        private void detach() {
            backend = null;
        }

        /**
         * Closes the backend if it is still attached.
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
     */
    private static final class RocmBackendHolder implements AutoCloseable {

        /**
         * Owned backend instance, or {@code null} once detached.
         */
        private chess.nn.otis.rocm.Backend backend;

        /**
         * Creates a holder that owns the provided backend until detached or closed.
         *
         * @param backend backend instance to manage
         */
        private RocmBackendHolder(chess.nn.otis.rocm.Backend backend) {
            this.backend = backend;
        }

        /**
         * Releases ownership so the backend is not closed by this holder.
         */
        private void detach() {
            backend = null;
        }

        /**
         * Closes the backend if it is still attached.
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
     */
    private static final class OneapiBackendHolder implements AutoCloseable {

        /**
         * Owned backend instance, or {@code null} once detached.
         */
        private chess.nn.otis.oneapi.Backend backend;

        /**
         * Creates a holder that owns the provided backend until detached or closed.
         *
         * @param backend backend instance to manage
         */
        private OneapiBackendHolder(chess.nn.otis.oneapi.Backend backend) {
            this.backend = backend;
        }

        /**
         * Releases ownership so the backend is not closed by this holder.
         */
        private void detach() {
            backend = null;
        }

        /**
         * Closes the backend if it is still attached.
         */
        @Override
        public void close() {
            if (backend != null) {
                backend.close();
            }
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
     * Parsed OTIS backend request.
     *
     * @param preferCuda whether CUDA may be attempted
     * @param preferRocm whether ROCm may be attempted
     * @param preferOneapi whether oneAPI may be attempted
     * @param forceCuda whether CUDA failure is fatal
     * @param forceRocm whether ROCm failure is fatal
     * @param forceOneapi whether oneAPI failure is fatal
     */
    private record BackendRequest(
            boolean preferCuda,
            boolean preferRocm,
            boolean preferOneapi,
            boolean forceCuda,
            boolean forceRocm,
            boolean forceOneapi) {

        /**
         * Parses {@code crtk.otis.backend} into backend preferences.
         *
         * @return parsed backend request
         */
        static BackendRequest fromSystemProperty() {
            String backend = System.getProperty("crtk.otis.backend");
            if (backend == null) {
                backend = "auto";
            }
            backend = backend.trim().toLowerCase(java.util.Locale.ROOT);
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
     * Detected OTIS backend availability.
     *
     * @param cuda whether CUDA is available
     * @param rocm whether ROCm is available
     * @param oneapi whether oneAPI is available
     */
    private record BackendAvailability(boolean cuda, boolean rocm, boolean oneapi) {

        /**
         * Detects available native backends.
         *
         * @return availability snapshot
         */
        static BackendAvailability detect() {
            return new BackendAvailability(
                    chess.nn.otis.cuda.Backend.isAvailable(),
                    chess.nn.otis.rocm.Backend.isAvailable(),
                    chess.nn.otis.oneapi.Backend.isAvailable());
        }
    }

    /**
     * Loads an OTIS placeholder model using the pure-Java CPU path.
     *
     * @param path model path
     * @return loaded model
     * @throws IOException if the file cannot be read or parsed
     */
    public static Model loadCpu(Path path) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("path == null");
        }
        try {
            ByteBuffer in = ByteBuffer.wrap(Files.readAllBytes(path)).order(ByteOrder.LITTLE_ENDIAN);
            int magic = in.getInt();
            if (magic != MAGIC) {
                throw new IOException("Invalid OTIS bin magic.");
            }
            int version = in.getInt();
            if (version != VERSION) {
                throw new IOException("Unsupported OTIS bin version: " + version);
            }
            String name = readString(in);
            int inputPlanes = in.getInt();
            int trunkChannels = in.getInt();
            int blocks = in.getInt();
            int policySize = in.getInt();
            if (inputPlanes != INPUT_PLANES) {
                throw new IOException("Unsupported OTIS input planes: " + inputPlanes);
            }
            if (trunkChannels <= 0 || blocks <= 0 || policySize != PolicyEncoder.POLICY_SIZE) {
                throw new IOException("Invalid OTIS architecture dimensions.");
            }
            Weights weights = new Weights(
                    new Info(name, inputPlanes, trunkChannels, blocks, policySize, 0),
                    readFloats(in, RAW_DIM * inputPlanes, "raw projection weights"),
                    readFloats(in, RAW_DIM, "raw projection bias"),
                    readFloats(in, PIECE_DIM * PIECE_STATE_PLANES, "piece projection weights"),
                    readFloats(in, PIECE_DIM, "piece projection bias"),
                    readFloats(in, COORD_DIM * 6, "coordinate projection weights"),
                    readFloats(in, COORD_DIM, "coordinate projection bias"),
                    readFloats(in, trunkChannels * FUSE_DIM, "fuse input weights"),
                    readFloats(in, trunkChannels, "fuse input bias"),
                    readFloats(in, trunkChannels, "fuse norm weights"),
                    readFloats(in, trunkChannels, "fuse norm bias"),
                    readFloats(in, trunkChannels * trunkChannels, "fuse output weights"),
                    readFloats(in, trunkChannels, "fuse output bias"),
                    readFloats(in, trunkChannels, "encoder norm weights"),
                    readFloats(in, trunkChannels, "encoder norm bias"),
                    readFloats(in, blocks * RELATION_COUNT * STALK_DIM * STALK_DIM, "rho source"),
                    readFloats(in, blocks * RELATION_COUNT * STALK_DIM * STALK_DIM, "rho target"),
                    readFloats(in, blocks * RELATION_COUNT, "relation gate logits"),
                    readFloats(in, blocks, "eta logits"),
                    readFloats(in, blocks * STALK_DIM * trunkChannels, "node-to-stalk weights"),
                    readFloats(in, blocks * STALK_DIM, "node-to-stalk bias"),
                    readFloats(in, blocks * trunkChannels * STALK_DIM, "stalk-to-node weights"),
                    readFloats(in, blocks * trunkChannels, "stalk-to-node bias"),
                    readFloats(in, blocks * trunkChannels, "node MLP norm weights"),
                    readFloats(in, blocks * trunkChannels, "node MLP norm bias"),
                    readFloats(in, blocks * trunkChannels * (trunkChannels * 2), "node MLP up weights"),
                    readFloats(in, blocks * (trunkChannels * 2), "node MLP up bias"),
                    readFloats(in, blocks * (trunkChannels * 2) * trunkChannels, "node MLP down weights"),
                    readFloats(in, blocks * trunkChannels, "node MLP down bias"),
                    readFloats(in, blocks * trunkChannels, "block norm weights"),
                    readFloats(in, blocks * trunkChannels, "block norm bias"),
                    readFloats(in, trunkChannels * trunkChannels, "triad attacker weights"),
                    readFloats(in, trunkChannels * trunkChannels, "triad target weights"),
                    readFloats(in, trunkChannels * trunkChannels, "triad defender weights"),
                    readFloats(in, TRIAD_DIM, "triad norm weights"),
                    readFloats(in, TRIAD_DIM, "triad norm bias"),
                    readFloats(in, readoutDim(trunkChannels), "readout norm weights"),
                    readFloats(in, readoutDim(trunkChannels), "readout norm bias"),
                    readFloats(in, HIDDEN_DIM * readoutDim(trunkChannels), "readout hidden weights"),
                    readFloats(in, HIDDEN_DIM, "readout hidden bias"),
                    readFloats(in, policySize * HIDDEN_DIM, "policy head weights"),
                    readFloats(in, policySize, "policy head bias"),
                    readFloats(in, 3 * HIDDEN_DIM, "WDL head weights"),
                    readFloats(in, 3, "WDL head bias"),
                    readFloats(in, SQUARES, "square atlas"),
                    readFloats(in, SQUARES, "policy atlas"),
                    readFloats(in, SQUARES, "value atlas"));
            if (in.hasRemaining()) {
                throw new IOException("Unexpected bytes at end of OTIS weights file.");
            }
            return new Model(weights.withParameterCount(), null, null, null);
        } catch (BufferUnderflowException | IllegalArgumentException ex) {
            throw new IOException("Malformed OTIS weights file.", ex);
        }
    }

    /**
     * Returns architecture metadata.
     *
     * @return info
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
        return weights.info;
    }

    /**
     * Returns the active backend label.
     *
     * @return {@code "cuda"}, {@code "rocm"}, {@code "oneapi"}, or the pure-Java CPU label
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
     * Returns a compact architecture summary for visible UI and CLI status.
     *
     * @return architecture summary
     */
    public String architectureLabel() {
        return architectureLabel(info());
    }

    /**
     * Returns the default placeholder architecture summary.
     *
     * @return architecture summary
     */
    public static String defaultArchitectureLabel() {
        return "simple_18, " + DEFAULT_TRUNK_CHANNELS + " trunk, "
                + DEFAULT_BLOCKS + " blocks, " + RELATION_COUNT + " relations, "
                + DEFAULT_POLICY_SIZE + " policy, 3 WDL, "
                + formatParameterCount(DEFAULT_PARAMETER_COUNT) + " params";
    }

    /**
     * Returns an architecture summary from loaded metadata.
     *
     * @param info model metadata
     * @return architecture summary
     */
    public static String architectureLabel(Info info) {
        if (info == null) {
            return defaultArchitectureLabel();
        }
        return "simple_18, " + info.trunkChannels() + " trunk, "
                + info.blocks() + " blocks, " + RELATION_COUNT + " relations, "
                + info.policySize() + " policy, 3 WDL, "
                + formatParameterCount(info.parameterCount()) + " params";
    }

    /**
     * Formats a parameter count with grouping.
     *
     * @param count parameter count
     * @return formatted count
     */
    public static String formatParameterCount(int count) {
        return String.format(java.util.Locale.ROOT, "%,d", count);
    }

    /**
     * Returns the readout feature width for the default placeholder.
     *
     * @return readout feature count
     */
    public static int defaultReadoutDim() {
        return readoutDim(DEFAULT_TRUNK_CHANNELS);
    }

    /**
     * Evaluates one position.
     *
     * @param position source position
     * @return prediction
     */
    public Prediction predict(Position position) {
        return predict(position, null);
    }

    /**
     * Evaluates one position and optionally captures workbench tensors.
     *
     * @param position source position
     * @param sink activation sink, or null
     * @return prediction
     */
    public Prediction predict(Position position, ActivationSink sink) {
        if (position == null) {
            throw new IllegalArgumentException("position == null");
        }
        if (cuda != null) {
            return predictNative(position, sink, cuda::predictEncoded);
        }
        if (rocm != null) {
            return predictNative(position, sink, rocm::predictEncoded);
        }
        if (oneapi != null) {
            return predictNative(position, sink, oneapi::predictEncoded);
        }
        float[] input = encodeInput(position);
        float[] tokens = squareTokens(position, input);
        float[] salience = squareSalience(tokens);
        SheafState sheaf = sheafState(position, input, salience, tokens);
        float[] trunk = sheaf.trunk;
        float[] trunkSummary = trunkSummary(trunk);
        float[] hidden = readoutHidden(position, sheaf, trunk);
        float[] policy = policyLogits(position, salience, sheaf, hidden);
        float[] valueLogits = valueLogits(hidden);
        float[] wdl = softmax(valueLogits);
        float scalar = wdl[0] - wdl[2];
        if (sink != null) {
            sink.put("otis.input", new int[] { INPUT_PLANES, 8, 8 }, input);
            sink.put("otis.square.salience", new int[] { 8, 8 }, salience);
            sink.put("otis.sheaf.node", new int[] { 8, 8 }, sheaf.nodeSignal);
            sink.put("otis.sheaf.laplacian", new int[] { 8, 8 }, sheaf.laplacian);
            sink.put("otis.sheaf.target.pressure",
                    new int[] { RELATION_COUNT, 8, 8 }, sheaf.targetPressure);
            sink.put("otis.sheaf.source.pressure",
                    new int[] { RELATION_COUNT, 8, 8 }, sheaf.sourcePressure);
            sink.put("otis.sheaf.relation.density",
                    new int[] { RELATION_COUNT }, sheaf.relationDensity);
            sink.put("otis.sheaf.relation.energy",
                    new int[] { RELATION_COUNT }, sheaf.relationEnergy);
            sink.put("otis.sheaf.relation.gate",
                    new int[] { RELATION_COUNT }, sheaf.relationGate);
            sink.put("otis.sheaf.tension", new int[] { 1 }, new float[] { sheaf.tension });
            sink.put("otis.sheaf.transport_imbalance",
                    new int[] { 1 }, new float[] { sheaf.transportImbalance });
            sink.put("otis.sheaf.topology_pressure",
                    new int[] { 1 }, new float[] { sheaf.topologyPressure });
            sink.put("otis.sheaf.pin_pressure", new int[] { 1 }, new float[] { sheaf.pinPressure });
            sink.put("otis.trunk", new int[] { weights.info.trunkChannels(), 8, 8 }, trunk);
            sink.put("otis.trunk.summary", new int[] { weights.info.trunkChannels() }, trunkSummary);
            sink.put("otis.policy.logits", new int[] { policy.length }, policy);
            sink.put("otis.value.logits", new int[] { 3 }, valueLogits);
            sink.put("otis.value.wdl", new int[] { 3 }, wdl);
            sink.put("otis.value.scalar", new int[] { 1 }, new float[] { scalar });
            sink.put("otis.weights.square", new int[] { 8, 8 }, weights.squareAtlas);
            sink.put("otis.weights.policy", new int[] { 8, 8 }, weights.policyAtlas);
            sink.put("otis.weights.value", new int[] { 8, 8 }, weights.valueAtlas);
            sink.put("otis.weights.raw_proj", new int[] { RAW_DIM, INPUT_PLANES }, weights.rawProjWeight);
            sink.put("otis.weights.piece_proj",
                    new int[] { PIECE_DIM, PIECE_STATE_PLANES }, weights.pieceProjWeight);
            sink.put("otis.weights.coord_proj", new int[] { COORD_DIM, 6 }, weights.coordProjWeight);
            sink.put("otis.weights.rho_src",
                    new int[] { weights.info.blocks() * RELATION_COUNT, STALK_DIM, STALK_DIM }, weights.rhoSrc);
            sink.put("otis.weights.rho_dst",
                    new int[] { weights.info.blocks() * RELATION_COUNT, STALK_DIM, STALK_DIM }, weights.rhoDst);
            sink.put("otis.weights.readout_hidden",
                    new int[] { HIDDEN_DIM, readoutDim(weights.info.trunkChannels()) }, weights.readoutHiddenWeight);
            sink.put("otis.weights.policy_head",
                    new int[] { weights.info.policySize(), HIDDEN_DIM }, weights.policyHeadWeight);
            sink.put("otis.weights.wdl_head", new int[] { 3, HIDDEN_DIM }, weights.wdlHeadWeight);
        }
        return new Prediction(policy, wdl, scalar);
    }

    /**
     * Runs one prediction through an active native backend.
     *
     * @param position source position
     * @param sink activation sink, or null
     * @param predictor native encoded predictor
     * @return prediction
     */
    private Prediction predictNative(Position position, ActivationSink sink,
            java.util.function.Function<float[], Prediction> predictor) {
        float[] input = encodeInput(position);
        Prediction prediction = predictor.apply(input);
        if (sink != null) {
            sink.put("otis.input", new int[] { INPUT_PLANES, 8, 8 }, input);
            sink.put("otis.policy.logits", new int[] { prediction.policy().length }, prediction.policy());
            sink.put("otis.value.wdl", new int[] { 3 }, prediction.wdl());
            sink.put("otis.value.scalar", new int[] { 1 }, new float[] { prediction.value() });
        }
        return prediction;
    }

    /**
     * Releases model resources (notably GPU device memory when a GPU backend is active).
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
    }

    /**
     * Model metadata.
     *
     * @param name architecture name
     * @param inputPlanes input plane count
     * @param trunkChannels trunk channel count
     * @param blocks abstract block count
     * @param policySize compressed policy size
     * @param parameterCount stored parameter count
     */
    public record Info(String name, int inputPlanes, int trunkChannels,
            int blocks, int policySize, int parameterCount) {
    }

    /**
     * Prediction outputs.
     *
     * @param policy compressed policy logits
     * @param wdl win/draw/loss probabilities
     * @param value scalar win-minus-loss value
     */
    public record Prediction(float[] policy, float[] wdl, float value) {
    }

    /**
     * Parsed tensors.
     */
    private record Weights(
            Info info,
            float[] rawProjWeight,
            float[] rawProjBias,
            float[] pieceProjWeight,
            float[] pieceProjBias,
            float[] coordProjWeight,
            float[] coordProjBias,
            float[] fuseInWeight,
            float[] fuseInBias,
            float[] fuseNormWeight,
            float[] fuseNormBias,
            float[] fuseOutWeight,
            float[] fuseOutBias,
            float[] encoderNormWeight,
            float[] encoderNormBias,
            float[] rhoSrc,
            float[] rhoDst,
            float[] relationGateLogits,
            float[] etaLogits,
            float[] nodeToStalkWeight,
            float[] nodeToStalkBias,
            float[] stalkToNodeWeight,
            float[] stalkToNodeBias,
            float[] nodeMlpNormWeight,
            float[] nodeMlpNormBias,
            float[] nodeMlpUpWeight,
            float[] nodeMlpUpBias,
            float[] nodeMlpDownWeight,
            float[] nodeMlpDownBias,
            float[] blockNormWeight,
            float[] blockNormBias,
            float[] triadAttackerWeight,
            float[] triadTargetWeight,
            float[] triadDefenderWeight,
            float[] triadNormWeight,
            float[] triadNormBias,
            float[] readoutNormWeight,
            float[] readoutNormBias,
            float[] readoutHiddenWeight,
            float[] readoutHiddenBias,
            float[] policyHeadWeight,
            float[] policyHeadBias,
            float[] wdlHeadWeight,
            float[] wdlHeadBias,
            float[] squareAtlas,
            float[] policyAtlas,
            float[] valueAtlas) {

        /**
         * Returns a copy with populated parameter count.
         *
         * @return weights with parameter count in metadata
         */
        Weights withParameterCount() {
            int params = rawProjWeight.length
                    + rawProjBias.length
                    + pieceProjWeight.length
                    + pieceProjBias.length
                    + coordProjWeight.length
                    + coordProjBias.length
                    + fuseInWeight.length
                    + fuseInBias.length
                    + fuseNormWeight.length
                    + fuseNormBias.length
                    + fuseOutWeight.length
                    + fuseOutBias.length
                    + encoderNormWeight.length
                    + encoderNormBias.length
                    + rhoSrc.length
                    + rhoDst.length
                    + relationGateLogits.length
                    + etaLogits.length
                    + nodeToStalkWeight.length
                    + nodeToStalkBias.length
                    + stalkToNodeWeight.length
                    + stalkToNodeBias.length
                    + nodeMlpNormWeight.length
                    + nodeMlpNormBias.length
                    + nodeMlpUpWeight.length
                    + nodeMlpUpBias.length
                    + nodeMlpDownWeight.length
                    + nodeMlpDownBias.length
                    + blockNormWeight.length
                    + blockNormBias.length
                    + triadAttackerWeight.length
                    + triadTargetWeight.length
                    + triadDefenderWeight.length
                    + triadNormWeight.length
                    + triadNormBias.length
                    + readoutNormWeight.length
                    + readoutNormBias.length
                    + readoutHiddenWeight.length
                    + readoutHiddenBias.length
                    + policyHeadWeight.length
                    + policyHeadBias.length
                    + wdlHeadWeight.length
                    + wdlHeadBias.length
                    + squareAtlas.length
                    + policyAtlas.length
                    + valueAtlas.length;
            return new Weights(
                    new Info(info.name(), info.inputPlanes(), info.trunkChannels(),
                            info.blocks(), info.policySize(), params),
                    rawProjWeight,
                    rawProjBias,
                    pieceProjWeight,
                    pieceProjBias,
                    coordProjWeight,
                    coordProjBias,
                    fuseInWeight,
                    fuseInBias,
                    fuseNormWeight,
                    fuseNormBias,
                    fuseOutWeight,
                    fuseOutBias,
                    encoderNormWeight,
                    encoderNormBias,
                    rhoSrc,
                    rhoDst,
                    relationGateLogits,
                    etaLogits,
                    nodeToStalkWeight,
                    nodeToStalkBias,
                    stalkToNodeWeight,
                    stalkToNodeBias,
                    nodeMlpNormWeight,
                    nodeMlpNormBias,
                    nodeMlpUpWeight,
                    nodeMlpUpBias,
                    nodeMlpDownWeight,
                    nodeMlpDownBias,
                    blockNormWeight,
                    blockNormBias,
                    triadAttackerWeight,
                    triadTargetWeight,
                    triadDefenderWeight,
                    triadNormWeight,
                    triadNormBias,
                    readoutNormWeight,
                    readoutNormBias,
                    readoutHiddenWeight,
                    readoutHiddenBias,
                    policyHeadWeight,
                    policyHeadBias,
                    wdlHeadWeight,
                    wdlHeadBias,
                    squareAtlas,
                    policyAtlas,
                    valueAtlas);
        }
    }

    /**
     * Compact sheaf outputs used by the placeholder forward pass and UI.
     */
    private record SheafState(
            float[] nodeSignal,
            float[] laplacian,
            float[] trunk,
            float[] sourcePressure,
            float[] targetPressure,
            float[] relationDensity,
            float[] relationEnergy,
            float[] relationGate,
            float tension,
            float transportImbalance,
            float topologyPressure,
            float pinPressure) {
    }

    /**
     * Encodes current-position planes.
     *
     * @param position source position
     * @return flat plane-major input tensor
     */
    private static float[] encodeInput(Position position) {
        float[] input = new float[INPUT_PLANES * SQUARES];
        boolean whiteToMove = position.isWhiteToMove();
        byte[] board = position.getBoard();
        for (int sq = 0; sq < board.length; sq++) {
            byte piece = board[sq];
            int plane = piecePlane(piece);
            if (plane >= 0) {
                input[plane * SQUARES + sq] = 1.0f;
            }
        }
        fillPlane(input, 12, whiteToMove ? 1.0f : 0.0f);
        fillPlane(input, 13, position.canCastle(Position.WHITE_KINGSIDE) ? 1.0f : 0.0f);
        fillPlane(input, 14, position.canCastle(Position.WHITE_QUEENSIDE) ? 1.0f : 0.0f);
        fillPlane(input, 15, position.canCastle(Position.BLACK_KINGSIDE) ? 1.0f : 0.0f);
        fillPlane(input, 16, position.canCastle(Position.BLACK_QUEENSIDE) ? 1.0f : 0.0f);
        byte ep = position.enPassantSquare();
        if (ep >= 0 && ep < SQUARES) {
            input[17 * SQUARES + ep] = 1.0f;
        }
        return input;
    }

    /**
     * Maps a repository piece code to the chess-nn-playground simple_18
     * absolute piece plane order: P,N,B,R,Q,K,p,n,b,r,q,k.
     *
     * @param piece piece code
     * @return plane index or -1 for empty
     */
    private static int piecePlane(byte piece) {
        if (piece == Piece.EMPTY) {
            return -1;
        }
        int type = Math.abs(piece) - 1;
        if (type < 0 || type >= 6) {
            return -1;
        }
        return Piece.isWhitePiece(piece) ? type : 6 + type;
    }

    /**
     * Fills one input plane with a scalar.
     *
     * @param input tensor
     * @param plane plane index
     * @param value value
     */
    private static void fillPlane(float[] input, int plane, float value) {
        int offset = plane * SQUARES;
        for (int i = 0; i < SQUARES; i++) {
            input[offset + i] = value;
        }
    }

    /**
     * Returns the readout feature width for a trunk size.
     */
    private static int readoutDim(int channels) {
        return channels * 4 + RELATION_COUNT * 4 + TRIAD_DIM + BOARD_STATS_DIM;
    }

    /**
     * Fills side-to-move-oriented piece-state features.
     */
    private static void fillPieceState(float[] out, byte piece, boolean whiteToMove) {
        for (int i = 0; i < out.length; i++) {
            out[i] = 0.0f;
        }
        if (piece == Piece.EMPTY) {
            out[0] = 1.0f;
            return;
        }
        int type = Math.abs(piece) - 1;
        if (type < 0 || type >= 6) {
            return;
        }
        out[(isOwn(piece, whiteToMove) ? 1 : 7) + type] = 1.0f;
    }

    /**
     * Fills normalized square-coordinate features.
     */
    private static void fillCoordinates(float[] out, int square, boolean whiteToMove) {
        int rank = square >>> 3;
        int file = square & 7;
        out[0] = rank / 7.0f;
        out[1] = file / 7.0f;
        out[2] = (rank - 3.5f) / 3.5f;
        out[3] = (file - 3.5f) / 3.5f;
        out[4] = edgeDistance(rank, file);
        out[5] = whiteToMove ? rank / 7.0f : (7 - rank) / 7.0f;
    }

    /**
     * Applies a row-major dense layer.
     */
    private static float[] linear(float[] weight, float[] bias, float[] input, int outDim, int inDim) {
        float[] out = new float[outDim];
        for (int row = 0; row < outDim; row++) {
            float sum = bias[row];
            int offset = row * inDim;
            for (int col = 0; col < inDim; col++) {
                sum += weight[offset + col] * input[col];
            }
            out[row] = sum;
        }
        return out;
    }

    /**
     * Applies layer normalization in-place.
     */
    private static void layerNormInPlace(float[] values, int valueOffset,
            float[] scale, int scaleOffset, float[] bias, int biasOffset, int length) {
        float mean = 0.0f;
        for (int i = 0; i < length; i++) {
            mean += values[valueOffset + i];
        }
        mean /= length;
        float variance = 0.0f;
        for (int i = 0; i < length; i++) {
            float centered = values[valueOffset + i] - mean;
            variance += centered * centered;
        }
        float invStd = (float) (1.0d / Math.sqrt(variance / length + 1.0e-5d));
        for (int i = 0; i < length; i++) {
            values[valueOffset + i] = (values[valueOffset + i] - mean) * invStd
                    * scale[scaleOffset + i] + bias[biasOffset + i];
        }
    }

    /**
     * Applies GELU in-place.
     */
    private static void geluInPlace(float[] values) {
        for (int i = 0; i < values.length; i++) {
            values[i] = gelu(values[i]);
        }
    }

    /**
     * Returns approximate GELU.
     */
    private static float gelu(float value) {
        double x = value;
        return (float) (0.5d * x * (1.0d
                + Math.tanh(0.7978845608028654d * (x + 0.044715d * x * x * x))));
    }

    /**
     * Returns the logistic sigmoid.
     */
    private static float sigmoid(float value) {
        return (float) (1.0d / (1.0d + Math.exp(-value)));
    }

    /**
     * Builds i249-style square tokens from simple_18 planes, side-relative piece
     * state, and board coordinates.
     *
     * @param position source position
     * @param input input tensor
     * @return square-major tokens
     */
    private float[] squareTokens(Position position, float[] input) {
        int channels = weights.info.trunkChannels();
        float[] out = new float[SQUARES * channels];
        float[] raw = new float[INPUT_PLANES];
        float[] piece = new float[PIECE_STATE_PLANES];
        float[] coord = new float[6];
        float[] fused = new float[FUSE_DIM];
        byte[] board = position.getBoard();
        boolean whiteToMove = position.isWhiteToMove();
        for (int sq = 0; sq < SQUARES; sq++) {
            for (int plane = 0; plane < INPUT_PLANES; plane++) {
                raw[plane] = input[plane * SQUARES + sq];
            }
            fillPieceState(piece, board[sq], whiteToMove);
            fillCoordinates(coord, sq, whiteToMove);
            float[] rawProjection = linear(weights.rawProjWeight, weights.rawProjBias,
                    raw, RAW_DIM, INPUT_PLANES);
            float[] pieceProjection = linear(weights.pieceProjWeight, weights.pieceProjBias,
                    piece, PIECE_DIM, PIECE_STATE_PLANES);
            float[] coordProjection = linear(weights.coordProjWeight, weights.coordProjBias,
                    coord, COORD_DIM, coord.length);
            System.arraycopy(rawProjection, 0, fused, 0, RAW_DIM);
            System.arraycopy(pieceProjection, 0, fused, RAW_DIM, PIECE_DIM);
            System.arraycopy(coordProjection, 0, fused, RAW_DIM + PIECE_DIM, COORD_DIM);
            float[] token = linear(weights.fuseInWeight, weights.fuseInBias, fused, channels, FUSE_DIM);
            layerNormInPlace(token, 0, weights.fuseNormWeight, 0, weights.fuseNormBias, 0, channels);
            geluInPlace(token);
            float[] residual = linear(weights.fuseOutWeight, weights.fuseOutBias, token, channels, channels);
            for (int c = 0; c < channels; c++) {
                token[c] += residual[c];
            }
            layerNormInPlace(token, 0, weights.encoderNormWeight, 0, weights.encoderNormBias, 0, channels);
            System.arraycopy(token, 0, out, sq * channels, channels);
        }
        return out;
    }

    /**
     * Computes a square salience stream from square tokens.
     *
     * @param tokens square-major tokens
     * @return salience values
     */
    private float[] squareSalience(float[] tokens) {
        int channels = weights.info.trunkChannels();
        float[] out = new float[SQUARES];
        for (int sq = 0; sq < SQUARES; sq++) {
            float mean = 0.0f;
            float magnitude = 0.0f;
            int offset = sq * channels;
            for (int c = 0; c < channels; c++) {
                float value = tokens[offset + c];
                mean += value;
                magnitude += Math.abs(value);
            }
            mean /= channels;
            magnitude /= channels;
            out[sq] = (float) Math.tanh(mean + 0.12f * magnitude + 0.20f * weights.squareAtlas[sq]);
        }
        return out;
    }

    /**
     * Summarizes trunk channels.
     *
     * @param trunk trunk tensor
     * @return one mean per channel
     */
    private float[] trunkSummary(float[] trunk) {
        int channels = weights.info.trunkChannels();
        float[] out = new float[channels];
        for (int c = 0; c < channels; c++) {
            float sum = 0.0f;
            int offset = c * SQUARES;
            for (int sq = 0; sq < SQUARES; sq++) {
                sum += trunk[offset + sq];
            }
            out[c] = sum / SQUARES;
        }
        return out;
    }

    /**
     * Computes compressed move-policy logits.
     *
     * @param position source position
     * @param salience square salience
     * @param sheaf sheaf state
     * @param hidden readout hidden vector
     * @return policy logits
     */
    private float[] policyLogits(Position position, float[] salience, SheafState sheaf, float[] hidden) {
        float[] logits = weights.policyHeadBias.clone();
        for (int policy = 0; policy < logits.length; policy++) {
            int offset = policy * HIDDEN_DIM;
            float sum = logits[policy];
            for (int h = 0; h < HIDDEN_DIM; h++) {
                sum += weights.policyHeadWeight[offset + h] * hidden[h];
            }
            logits[policy] = sum;
        }
        MoveList legal = position.legalMoves();
        for (int i = 0; i < legal.size(); i++) {
            short move = legal.get(i);
            int policyIndex = PolicyEncoder.compressedPolicyIndex(position, move);
            if (policyIndex < 0 || policyIndex >= logits.length) {
                continue;
            }
            int from = Move.getFromIndex(move);
            int to = Move.getToIndex(move);
            float capture = pieceValue(position.pieceAt(to)) * 0.18f;
            float promotion = Move.getPromotion(move) == 0 ? 0.0f : 0.25f;
            logits[policyIndex] += 1.25f * salience[to]
                    - 0.35f * salience[from]
                    + 0.55f * sheaf.nodeSignal[to]
                    - 0.18f * sheaf.laplacian[from]
                    + 0.20f * weights.policyAtlas[to]
                    - 0.08f * weights.policyAtlas[from]
                    + capture
                    + promotion;
        }
        return logits;
    }

    /**
     * Computes WDL logits.
     *
     * @param hidden readout hidden vector
     * @return raw WDL logits
     */
    private float[] valueLogits(float[] hidden) {
        float[] out = weights.wdlHeadBias.clone();
        for (int bucket = 0; bucket < out.length; bucket++) {
            int offset = bucket * HIDDEN_DIM;
            for (int h = 0; h < HIDDEN_DIM; h++) {
                out[bucket] += weights.wdlHeadWeight[offset + h] * hidden[h];
            }
        }
        return out;
    }

    /**
     * Computes the i249-style typed tactical sheaf and transformer-style trunk.
     *
     * @param position source position
     * @param input encoded input planes
     * @param salience square salience stream
     * @param tokens square-major input tokens
     * @return sheaf state
     */
    private SheafState sheafState(Position position, float[] input, float[] salience, float[] tokens) {
        int channels = weights.info.trunkChannels();
        int blocks = weights.info.blocks();
        float[] masks = relationMasks(position);
        float[] h = tokens.clone();
        float[] sourcePressure = new float[RELATION_COUNT * SQUARES];
        float[] targetPressure = new float[RELATION_COUNT * SQUARES];
        float[] density = new float[RELATION_COUNT];
        float[] energy = new float[RELATION_COUNT];
        float[] gates = new float[RELATION_COUNT];
        float[] node = new float[SQUARES];
        float[] laplacian = new float[SQUARES];
        float[] src = new float[STALK_DIM];
        float[] dst = new float[STALK_DIM];
        float[] residual = new float[STALK_DIM];
        float[] srcBack = new float[STALK_DIM];
        float[] dstBack = new float[STALK_DIM];
        float edgeTotal = 0.0f;

        for (int block = 0; block < blocks; block++) {
            float[] stalks = nodeToStalk(block, h, channels);
            float[] update = new float[SQUARES * STALK_DIM];
            float[] degree = new float[SQUARES];
            float eta = eta(block);
            for (int relation = 0; relation < RELATION_COUNT; relation++) {
                float gate = relationGate(block, relation);
                gates[relation] += gate;
                float sign = RELATION_SIGNS[relation];
                int edgeCount = 0;
                float energySum = 0.0f;
                for (int from = 0; from < SQUARES; from++) {
                    for (int to = 0; to < SQUARES; to++) {
                        float edge = masks[relationIndex(relation, from, to)];
                        if (edge <= 0.0f) {
                            continue;
                        }
                        edgeCount++;
                        if (block == 0) {
                            sourcePressure[relation * SQUARES + from] += edge;
                            targetPressure[relation * SQUARES + to] += edge;
                        }
                        projectStalk(block, relation, true, stalks, from, src);
                        projectStalk(block, relation, false, stalks, to, dst);
                        float norm = 0.0f;
                        for (int dim = 0; dim < STALK_DIM; dim++) {
                            float value = dst[dim] - sign * src[dim];
                            residual[dim] = value;
                            norm += value * value;
                        }
                        energySum += edge * norm;
                        backProjectStalk(block, relation, true, residual, srcBack);
                        backProjectStalk(block, relation, false, residual, dstBack);
                        float scaled = gate * edge;
                        for (int dim = 0; dim < STALK_DIM; dim++) {
                            update[from * STALK_DIM + dim] += scaled * sign * srcBack[dim];
                            update[to * STALK_DIM + dim] -= scaled * dstBack[dim];
                        }
                        degree[from] += scaled;
                        degree[to] += scaled;
                    }
                }
                if (block == 0) {
                    density[relation] = edgeCount / (float) (SQUARES * SQUARES);
                    edgeTotal += edgeCount;
                }
                if (edgeCount > 0) {
                    energy[relation] += gate * energySum / edgeCount;
                }
            }
            float[] stalkDelta = new float[SQUARES * STALK_DIM];
            for (int sq = 0; sq < SQUARES; sq++) {
                float invDegree = 1.0f / Math.max(1.0f, degree[sq]);
                float signed = 0.0f;
                float norm = 0.0f;
                for (int dim = 0; dim < STALK_DIM; dim++) {
                    float delta = update[sq * STALK_DIM + dim] * invDegree;
                    stalkDelta[sq * STALK_DIM + dim] = eta * delta;
                    signed += delta;
                    norm += delta * delta;
                }
                laplacian[sq] = -signed / STALK_DIM;
                node[sq] = (float) Math.tanh(salience[sq]
                        + 0.45f * signed / STALK_DIM
                        - 0.08f * Math.sqrt(norm)
                        + 0.05f * input[17 * SQUARES + sq]);
            }
            applyStalkUpdate(block, h, stalkDelta, channels);
            applyNodeMlp(block, h, channels);
        }
        for (int relation = 0; relation < RELATION_COUNT; relation++) {
            energy[relation] /= blocks;
            gates[relation] /= blocks;
        }
        for (int sq = 0; sq < SQUARES; sq++) {
            float mean = 0.0f;
            int offset = sq * channels;
            for (int c = 0; c < channels; c++) {
                mean += h[offset + c];
            }
            node[sq] = (float) Math.tanh(0.5f * node[sq] + 0.5f * mean / channels);
        }
        float[] trunk = trunkToChannelMajor(h, channels);
        float tension = mean(energy);
        float usPressure = density[0] * SQUARES * SQUARES;
        float themPressure = density[1] * SQUARES * SQUARES;
        float transport = Math.abs(usPressure - themPressure)
                / Math.max(1.0f, usPressure + themPressure);
        float topology = edgeTotal / (RELATION_COUNT * SQUARES * SQUARES);
        return new SheafState(node, laplacian, trunk, sourcePressure, targetPressure, density, energy, gates,
                tension, transport, topology, density[11]);
    }

    /**
     * Computes the post-sheaf policy/value readout hidden state.
     */
    private float[] readoutHidden(Position position, SheafState sheaf, float[] trunk) {
        int channels = weights.info.trunkChannels();
        float[] features = new float[readoutDim(channels)];
        float[] means = channelMeans(trunk, channels);
        int cursor = 0;
        cursor = copyInto(means, features, cursor);
        cursor = channelMaxInto(trunk, channels, features, cursor);
        cursor = sideMeanInto(position, trunk, channels, true, features, cursor);
        cursor = sideMeanInto(position, trunk, channels, false, features, cursor);
        for (int relation = 0; relation < RELATION_COUNT; relation++) {
            features[cursor++] = sheaf.relationEnergy[relation];
        }
        for (int relation = 0; relation < RELATION_COUNT; relation++) {
            features[cursor++] = sheaf.relationDensity[relation];
        }
        for (int relation = 0; relation < RELATION_COUNT; relation++) {
            features[cursor++] = sheaf.relationGate[relation];
        }
        for (int relation = 0; relation < RELATION_COUNT; relation++) {
            features[cursor++] = relationPressure(sheaf, relation);
        }
        float[] triad = triadFeatures(means, sheaf);
        cursor = copyInto(triad, features, cursor);
        float[] stats = boardStats(position, sheaf);
        copyInto(stats, features, cursor);
        layerNormInPlace(features, 0, weights.readoutNormWeight, 0,
                weights.readoutNormBias, 0, features.length);
        float[] hidden = linear(weights.readoutHiddenWeight, weights.readoutHiddenBias,
                features, HIDDEN_DIM, features.length);
        geluInPlace(hidden);
        return hidden;
    }

    /**
     * Converts square-major tokens into channel-major trunk activations.
     */
    private static float[] trunkToChannelMajor(float[] h, int channels) {
        float[] out = new float[channels * SQUARES];
        for (int sq = 0; sq < SQUARES; sq++) {
            int squareOffset = sq * channels;
            for (int c = 0; c < channels; c++) {
                out[c * SQUARES + sq] = h[squareOffset + c];
            }
        }
        return out;
    }

    /**
     * Returns channel means from channel-major trunk activations.
     */
    private static float[] channelMeans(float[] trunk, int channels) {
        float[] out = new float[channels];
        for (int c = 0; c < channels; c++) {
            float sum = 0.0f;
            int offset = c * SQUARES;
            for (int sq = 0; sq < SQUARES; sq++) {
                sum += trunk[offset + sq];
            }
            out[c] = sum / SQUARES;
        }
        return out;
    }

    /**
     * Copies source values into a destination at a cursor.
     */
    private static int copyInto(float[] source, float[] destination, int cursor) {
        System.arraycopy(source, 0, destination, cursor, source.length);
        return cursor + source.length;
    }

    /**
     * Appends channel maxima to a readout vector.
     */
    private static int channelMaxInto(float[] trunk, int channels, float[] out, int cursor) {
        for (int c = 0; c < channels; c++) {
            float max = Float.NEGATIVE_INFINITY;
            int offset = c * SQUARES;
            for (int sq = 0; sq < SQUARES; sq++) {
                max = Math.max(max, trunk[offset + sq]);
            }
            out[cursor++] = max;
        }
        return cursor;
    }

    /**
     * Appends own-piece or opponent-piece channel means to a readout vector.
     */
    private static int sideMeanInto(Position position, float[] trunk, int channels,
            boolean ownSide, float[] out, int cursor) {
        byte[] board = position.getBoard();
        boolean whiteToMove = position.isWhiteToMove();
        int count = 0;
        for (int sq = 0; sq < SQUARES; sq++) {
            byte piece = board[sq];
            if (piece == Piece.EMPTY || isOwn(piece, whiteToMove) != ownSide) {
                continue;
            }
            count++;
            for (int c = 0; c < channels; c++) {
                out[cursor + c] += trunk[c * SQUARES + sq];
            }
        }
        if (count > 0) {
            float inv = 1.0f / count;
            for (int c = 0; c < channels; c++) {
                out[cursor + c] *= inv;
            }
        }
        return cursor + channels;
    }

    /**
     * Returns the average source/target pressure for one relation.
     */
    private static float relationPressure(SheafState sheaf, int relation) {
        float sum = 0.0f;
        int offset = relation * SQUARES;
        for (int sq = 0; sq < SQUARES; sq++) {
            sum += sheaf.sourcePressure[offset + sq] + sheaf.targetPressure[offset + sq];
        }
        return sum / (2.0f * SQUARES);
    }

    /**
     * Computes compact attacker-target-defender diagnostics.
     */
    private float[] triadFeatures(float[] means, SheafState sheaf) {
        int channels = weights.info.trunkChannels();
        float[] out = new float[TRIAD_DIM];
        out[0] = matrixResponse(weights.triadAttackerWeight, means, channels);
        out[1] = matrixResponse(weights.triadTargetWeight, means, channels);
        out[2] = matrixResponse(weights.triadDefenderWeight, means, channels);
        out[3] = sheaf.tension - sheaf.pinPressure + 0.25f * sheaf.transportImbalance;
        layerNormInPlace(out, 0, weights.triadNormWeight, 0, weights.triadNormBias, 0, out.length);
        return out;
    }

    /**
     * Returns the mean response of a square channel matrix.
     */
    private static float matrixResponse(float[] matrix, float[] values, int channels) {
        float sum = 0.0f;
        for (int row = 0; row < channels; row++) {
            float rowSum = 0.0f;
            int offset = row * channels;
            for (int col = 0; col < channels; col++) {
                rowSum += matrix[offset + col] * values[col];
            }
            sum += (float) Math.tanh(rowSum);
        }
        return sum / channels;
    }

    /**
     * Computes compact side-to-move board statistics for readout.
     */
    private float[] boardStats(Position position, SheafState sheaf) {
        float[] out = new float[BOARD_STATS_DIM];
        byte[] board = position.getBoard();
        boolean whiteToMove = position.isWhiteToMove();
        int ownCount = 0;
        int themCount = 0;
        float material = 0.0f;
        float atlas = 0.0f;
        int occupied = 0;
        for (int sq = 0; sq < SQUARES; sq++) {
            byte piece = board[sq];
            if (piece == Piece.EMPTY) {
                continue;
            }
            occupied++;
            atlas += weights.valueAtlas[sq];
            material += signedMaterial(piece, whiteToMove);
            if (isOwn(piece, whiteToMove)) {
                ownCount++;
            } else {
                themCount++;
            }
        }
        out[0] = occupied / (float) SQUARES;
        out[1] = ownCount / 16.0f;
        out[2] = themCount / 16.0f;
        out[3] = material / 4.0f;
        out[4] = occupied == 0 ? 0.0f : atlas / occupied;
        out[5] = sheaf.tension;
        out[6] = sheaf.transportImbalance;
        out[7] = sheaf.topologyPressure + sheaf.pinPressure;
        return out;
    }

    /**
     * Builds the twelve typed tactical incidence masks over board squares.
     *
     * @param position source position
     * @return flat relation-major masks, relation x source x target
     */
    private static float[] relationMasks(Position position) {
        byte[] board = position.getBoard();
        boolean whiteToMove = position.isWhiteToMove();
        float[] masks = new float[RELATION_COUNT * SQUARES * SQUARES];
        boolean[] nearOwnKing = kingZone(findKing(board, whiteToMove));
        boolean[] nearThemKing = kingZone(findKing(board, !whiteToMove));

        for (int from = 0; from < SQUARES; from++) {
            byte piece = board[from];
            if (piece == Piece.EMPTY) {
                continue;
            }
            addPieceRelations(masks, board, whiteToMove, nearOwnKing, nearThemKing, from, piece);
            addSliderRelationMasks(masks, board, from, piece);
        }
        addPinRelations(masks, board, true);
        addPinRelations(masks, board, false);
        return masks;
    }

    /**
     * One typed directed relation edge between two squares, i.e. a single entry of
     * the tactical-incidence tensor A(x). The {@code channel} indexes
     * {@link #RELATION_NAMES}.
     *
     * @param channel relation channel index (0..{@link #RELATION_COUNT}-1)
     * @param from source square index (0..63)
     * @param to target square index (0..63)
     */
    public record IncidenceEdge(int channel, int from, int to) {
    }

    /**
     * Computes the typed tactical-incidence edges of a position: every set entry
     * of the {@link #RELATION_COUNT}-channel A(x) tensor the OTIS network ingests.
     * Deterministic and weightless (no model file or evaluation needed) — it reuses
     * the exact {@link #relationMasks} builder, so a rendering of these edges
     * matches the network's input. Channels follow {@link #RELATION_NAMES}.
     *
     * @param position source position
     * @return all set relation edges, grouped implicitly by ascending channel
     */
    public static java.util.List<IncidenceEdge> incidenceEdges(Position position) {
        float[] masks = relationMasks(position);
        java.util.List<IncidenceEdge> edges = new java.util.ArrayList<>();
        for (int channel = 0; channel < RELATION_COUNT; channel++) {
            int channelBase = channel * SQUARES * SQUARES;
            for (int from = 0; from < SQUARES; from++) {
                int fromBase = channelBase + from * SQUARES;
                for (int to = 0; to < SQUARES; to++) {
                    if (masks[fromBase + to] != 0.0f) {
                        edges.add(new IncidenceEdge(channel, from, to));
                    }
                }
            }
        }
        return edges;
    }

    /**
     * Adds attack/defense and piece-specific relation edges for one piece.
     */
    private static void addPieceRelations(float[] masks, byte[] board, boolean whiteToMove,
            boolean[] nearOwnKing, boolean[] nearThemKing, int from, byte piece) {
        int type = Math.abs(piece);
        boolean own = isOwn(piece, whiteToMove);
        switch (type) {
            case Piece.PAWN -> addPawnRelations(masks, board, whiteToMove, nearOwnKing, nearThemKing,
                    from, piece, own);
            case Piece.KNIGHT -> addStepRelations(masks, board, nearOwnKing, nearThemKing, from, own,
                    new int[][] { { 1, 2 }, { 2, 1 }, { 2, -1 }, { 1, -2 },
                            { -1, -2 }, { -2, -1 }, { -2, 1 }, { -1, 2 } },
                    true, false);
            case Piece.BISHOP -> addRayRelations(masks, board, nearOwnKing, nearThemKing, from, own,
                    true, false);
            case Piece.ROOK -> addRayRelations(masks, board, nearOwnKing, nearThemKing, from, own,
                    false, true);
            case Piece.QUEEN -> addRayRelations(masks, board, nearOwnKing, nearThemKing, from, own,
                    true, true);
            case Piece.KING -> addStepRelations(masks, board, nearOwnKing, nearThemKing, from, own,
                    new int[][] { { 1, 0 }, { 1, 1 }, { 0, 1 }, { -1, 1 },
                            { -1, 0 }, { -1, -1 }, { 0, -1 }, { 1, -1 } },
                    false, false);
            default -> {
                // Unknown piece code: ignore.
            }
        }
    }

    /**
     * Adds pawn attack and pawn-specific relation edges.
     */
    private static void addPawnRelations(float[] masks, byte[] board, boolean whiteToMove,
            boolean[] nearOwnKing, boolean[] nearThemKing, int from, byte piece, boolean own) {
        // Board indices run rank 0 = the 8th rank (a8) down to rank 7 = the 1st
        // rank (a1), so a pawn advancing forward DECREASES the internal rank for
        // White and increases it for Black. This must stay identical to the native
        // host-side encoder in native/common/otis_gpu_impl.inl (add_pawn_relations)
        // for CPU/GPU parity.
        int direction = Piece.isWhitePiece(piece) ? -1 : 1;
        int rank = from >>> 3;
        int file = from & 7;
        for (int df : new int[] { -1, 1 }) {
            int to = square(rank + direction, file + df);
            if (to >= 0) {
                addTacticalEdge(masks, board, own, nearOwnKing, nearThemKing, from, to);
                setRelation(masks, 10, from, to);
            }
        }
    }

    /**
     * Adds king/knight relation edges.
     */
    private static void addStepRelations(float[] masks, byte[] board, boolean[] nearOwnKing,
            boolean[] nearThemKing, int from, boolean own, int[][] deltas, boolean knight, boolean pawn) {
        int rank = from >>> 3;
        int file = from & 7;
        for (int[] delta : deltas) {
            int to = square(rank + delta[0], file + delta[1]);
            if (to < 0) {
                continue;
            }
            addTacticalEdge(masks, board, own, nearOwnKing, nearThemKing, from, to);
            if (knight) {
                setRelation(masks, 9, from, to);
            }
            if (pawn) {
                setRelation(masks, 10, from, to);
            }
        }
    }

    /**
     * Adds sliding attack/defense relation edges.
     */
    private static void addRayRelations(float[] masks, byte[] board, boolean[] nearOwnKing,
            boolean[] nearThemKing, int from, boolean own, boolean diagonals, boolean orthogonals) {
        int[][] directions = rayDirections(diagonals, orthogonals);
        int rank = from >>> 3;
        int file = from & 7;
        for (int[] direction : directions) {
            int r = rank + direction[0];
            int f = file + direction[1];
            while (true) {
                int to = square(r, f);
                if (to < 0) {
                    break;
                }
                addTacticalEdge(masks, board, own, nearOwnKing, nearThemKing, from, to);
                if (board[to] != Piece.EMPTY) {
                    break;
                }
                r += direction[0];
                f += direction[1];
            }
        }
    }

    /**
     * Adds relation-family visible ray masks for bishop/rook/queen pieces.
     */
    private static void addSliderRelationMasks(float[] masks, byte[] board, int from, byte piece) {
        int type = Math.abs(piece);
        if (type == Piece.BISHOP || type == Piece.QUEEN) {
            addVisibleRayMask(masks, board, from, type == Piece.BISHOP ? 6 : 8, true, false);
        }
        if (type == Piece.ROOK || type == Piece.QUEEN) {
            addVisibleRayMask(masks, board, from, type == Piece.ROOK ? 7 : 8, false, true);
        }
    }

    /**
     * Adds one visible slider relation mask.
     */
    private static void addVisibleRayMask(float[] masks, byte[] board, int from, int relation,
            boolean diagonals, boolean orthogonals) {
        int rank = from >>> 3;
        int file = from & 7;
        for (int[] direction : rayDirections(diagonals, orthogonals)) {
            int r = rank + direction[0];
            int f = file + direction[1];
            while (true) {
                int to = square(r, f);
                if (to < 0) {
                    break;
                }
                setRelation(masks, relation, from, to);
                if (board[to] != Piece.EMPTY) {
                    break;
                }
                r += direction[0];
                f += direction[1];
            }
        }
    }

    /**
     * Adds attacker/defender relation edges based on target occupancy.
     */
    private static void addTacticalEdge(float[] masks, byte[] board, boolean own,
            boolean[] nearOwnKing, boolean[] nearThemKing, int from, int to) {
        byte target = board[to];
        if (own) {
            if (target == Piece.EMPTY) {
                if (nearThemKing[to]) {
                    setRelation(masks, 4, from, to);
                }
            } else if (isOwn(target, true) == isOwn(board[from], true)) {
                setRelation(masks, 2, from, to);
            } else {
                setRelation(masks, 0, from, to);
            }
        } else {
            if (target == Piece.EMPTY) {
                if (nearOwnKing[to]) {
                    setRelation(masks, 5, from, to);
                }
            } else if (isOwn(target, true) == isOwn(board[from], true)) {
                setRelation(masks, 3, from, to);
            } else {
                setRelation(masks, 1, from, to);
            }
        }
    }

    /**
     * Adds king-blocker-slider pin candidate edges.
     */
    private static void addPinRelations(float[] masks, byte[] board, boolean whiteKing) {
        int king = findAbsoluteKing(board, whiteKing);
        if (king < 0) {
            return;
        }
        int rank = king >>> 3;
        int file = king & 7;
        for (int[] direction : rayDirections(true, true)) {
            int blocker = -1;
            int r = rank + direction[0];
            int f = file + direction[1];
            while (true) {
                int sq = square(r, f);
                if (sq < 0) {
                    break;
                }
                byte piece = board[sq];
                if (piece != Piece.EMPTY) {
                    if (blocker < 0) {
                        if (Piece.isWhitePiece(piece) == whiteKing) {
                            blocker = sq;
                        } else {
                            break;
                        }
                    } else {
                        if (Piece.isWhitePiece(piece) != whiteKing && sliderMatches(piece, direction)) {
                            setRelation(masks, 11, sq, blocker);
                        }
                        break;
                    }
                }
                r += direction[0];
                f += direction[1];
            }
        }
    }

    /**
     * Projects square tokens into stalk vectors for one sheaf block.
     */
    private float[] nodeToStalk(int block, float[] h, int channels) {
        float[] out = new float[SQUARES * STALK_DIM];
        int weightBase = block * STALK_DIM * channels;
        int biasBase = block * STALK_DIM;
        for (int sq = 0; sq < SQUARES; sq++) {
            int nodeOffset = sq * channels;
            int stalkOffset = sq * STALK_DIM;
            for (int dim = 0; dim < STALK_DIM; dim++) {
                float sum = weights.nodeToStalkBias[biasBase + dim];
                int row = weightBase + dim * channels;
                for (int c = 0; c < channels; c++) {
                    sum += weights.nodeToStalkWeight[row + c] * h[nodeOffset + c];
                }
                out[stalkOffset + dim] = sum;
            }
        }
        return out;
    }

    /**
     * Applies one stalk-space update back to square tokens.
     */
    private void applyStalkUpdate(int block, float[] h, float[] stalkDelta, int channels) {
        int weightBase = block * channels * STALK_DIM;
        int biasBase = block * channels;
        for (int sq = 0; sq < SQUARES; sq++) {
            int nodeOffset = sq * channels;
            int stalkOffset = sq * STALK_DIM;
            for (int c = 0; c < channels; c++) {
                float sum = weights.stalkToNodeBias[biasBase + c] * 0.05f;
                int row = weightBase + c * STALK_DIM;
                for (int dim = 0; dim < STALK_DIM; dim++) {
                    sum += weights.stalkToNodeWeight[row + dim] * stalkDelta[stalkOffset + dim];
                }
                h[nodeOffset + c] += sum;
            }
        }
    }

    /**
     * Applies the per-square trunk MLP for one sheaf block.
     */
    private void applyNodeMlp(int block, float[] h, int channels) {
        int upDim = channels * 2;
        float[] token = new float[channels];
        float[] up = new float[upDim];
        float[] down = new float[channels];
        int normOffset = block * channels;
        int upWeightBase = block * upDim * channels;
        int upBiasBase = block * upDim;
        int downWeightBase = block * channels * upDim;
        int downBiasBase = block * channels;
        for (int sq = 0; sq < SQUARES; sq++) {
            int nodeOffset = sq * channels;
            System.arraycopy(h, nodeOffset, token, 0, channels);
            layerNormInPlace(token, 0, weights.nodeMlpNormWeight, normOffset,
                    weights.nodeMlpNormBias, normOffset, channels);
            for (int out = 0; out < upDim; out++) {
                float sum = weights.nodeMlpUpBias[upBiasBase + out];
                int row = upWeightBase + out * channels;
                for (int c = 0; c < channels; c++) {
                    sum += weights.nodeMlpUpWeight[row + c] * token[c];
                }
                up[out] = gelu(sum);
            }
            for (int c = 0; c < channels; c++) {
                float sum = weights.nodeMlpDownBias[downBiasBase + c];
                int row = downWeightBase + c * upDim;
                for (int i = 0; i < upDim; i++) {
                    sum += weights.nodeMlpDownWeight[row + i] * up[i];
                }
                down[c] = h[nodeOffset + c] + sum;
            }
            layerNormInPlace(down, 0, weights.blockNormWeight, normOffset,
                    weights.blockNormBias, normOffset, channels);
            System.arraycopy(down, 0, h, nodeOffset, channels);
        }
    }

    /**
     * Projects a square stalk through one relation restriction map.
     */
    private void projectStalk(int block, int relation, boolean source,
            float[] stalks, int square, float[] out) {
        int offset = square * STALK_DIM;
        for (int j = 0; j < STALK_DIM; j++) {
            float sum = 0.0f;
            for (int i = 0; i < STALK_DIM; i++) {
                sum += stalks[offset + i] * restriction(block, relation, source, i, j);
            }
            out[j] = sum;
        }
    }

    /**
     * Back-projects a residual through the transpose of one restriction map.
     */
    private void backProjectStalk(int block, int relation, boolean source,
            float[] residual, float[] out) {
        for (int j = 0; j < STALK_DIM; j++) {
            float sum = 0.0f;
            for (int i = 0; i < STALK_DIM; i++) {
                sum += residual[i] * restriction(block, relation, source, j, i);
            }
            out[j] = sum;
        }
    }

    /**
     * Returns one learned randomized restriction-map entry.
     */
    private float restriction(int block, int relation, boolean source, int row, int col) {
        int index = ((block * RELATION_COUNT + relation) * STALK_DIM + row) * STALK_DIM + col;
        return source ? weights.rhoSrc[index] : weights.rhoDst[index];
    }

    /**
     * Returns one learned randomized relation gate in {@code (0, 2)}.
     */
    private float relationGate(int block, int relation) {
        return 2.0f * sigmoid(weights.relationGateLogits[block * RELATION_COUNT + relation]);
    }

    /**
     * Returns the learned heat-step scale for one block.
     */
    private float eta(int block) {
        return SHEAF_ETA * 2.0f * sigmoid(weights.etaLogits[block]);
    }

    /**
     * Returns the flat relation mask index.
     */
    private static int relationIndex(int relation, int from, int to) {
        return (relation * SQUARES + from) * SQUARES + to;
    }

    /**
     * Sets one relation edge.
     */
    private static void setRelation(float[] masks, int relation, int from, int to) {
        if (from != to && from >= 0 && from < SQUARES && to >= 0 && to < SQUARES) {
            masks[relationIndex(relation, from, to)] = 1.0f;
        }
    }

    /**
     * Returns the square index for a rank/file pair.
     */
    private static int square(int rank, int file) {
        return rank >= 0 && rank < 8 && file >= 0 && file < 8 ? (rank << 3) + file : -1;
    }

    /**
     * Returns slider ray directions.
     */
    private static int[][] rayDirections(boolean diagonals, boolean orthogonals) {
        if (diagonals && orthogonals) {
            return new int[][] { { 1, 0 }, { 1, 1 }, { 0, 1 }, { -1, 1 },
                    { -1, 0 }, { -1, -1 }, { 0, -1 }, { 1, -1 } };
        }
        if (diagonals) {
            return new int[][] { { 1, 1 }, { -1, 1 }, { -1, -1 }, { 1, -1 } };
        }
        return new int[][] { { 1, 0 }, { 0, 1 }, { -1, 0 }, { 0, -1 } };
    }

    /**
     * Tests whether a slider piece attacks along a ray direction.
     */
    private static boolean sliderMatches(byte piece, int[] direction) {
        int type = Math.abs(piece);
        boolean diagonal = direction[0] != 0 && direction[1] != 0;
        return type == Piece.QUEEN
                || (diagonal && type == Piece.BISHOP)
                || (!diagonal && type == Piece.ROOK);
    }

    /**
     * Finds the side-to-move-relative king square.
     */
    private static int findKing(byte[] board, boolean sideIsWhite) {
        return findAbsoluteKing(board, sideIsWhite);
    }

    /**
     * Finds an absolute-color king square.
     */
    private static int findAbsoluteKing(byte[] board, boolean white) {
        byte target = white ? Piece.WHITE_KING : Piece.BLACK_KING;
        for (int sq = 0; sq < board.length; sq++) {
            if (board[sq] == target) {
                return sq;
            }
        }
        return -1;
    }

    /**
     * Returns a king-zone mask with Chebyshev radius two.
     */
    private static boolean[] kingZone(int kingSquare) {
        boolean[] out = new boolean[SQUARES];
        if (kingSquare < 0) {
            return out;
        }
        int kingRank = kingSquare >>> 3;
        int kingFile = kingSquare & 7;
        for (int sq = 0; sq < SQUARES; sq++) {
            int rank = sq >>> 3;
            int file = sq & 7;
            out[sq] = sq != kingSquare
                    && Math.max(Math.abs(rank - kingRank), Math.abs(file - kingFile)) <= 2;
        }
        return out;
    }

    /**
     * Returns whether a piece belongs to the side to move.
     */
    private static boolean isOwn(byte piece, boolean whiteToMove) {
        return piece != Piece.EMPTY && Piece.isWhitePiece(piece) == whiteToMove;
    }

    /**
     * Returns a side-to-move signed material value.
     */
    private static float signedMaterial(byte piece, boolean whiteToMove) {
        if (piece == Piece.EMPTY) {
            return 0.0f;
        }
        float value = switch (Math.abs(piece)) {
            case Piece.PAWN -> 0.10f;
            case Piece.KNIGHT, Piece.BISHOP -> 0.30f;
            case Piece.ROOK -> 0.50f;
            case Piece.QUEEN -> 0.90f;
            case Piece.KING -> 0.20f;
            default -> 0.0f;
        };
        return isOwn(piece, whiteToMove) ? value : -value;
    }

    /**
     * Returns normalized edge distance.
     */
    private static float edgeDistance(int rank, int file) {
        return Math.min(Math.min(rank, 7 - rank), Math.min(file, 7 - file)) / 3.5f;
    }

    /**
     * Returns arithmetic mean.
     */
    private static float mean(float[] values) {
        if (values == null || values.length == 0) {
            return 0.0f;
        }
        float sum = 0.0f;
        for (float value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    /**
     * Returns a small capture bonus scale by piece type.
     *
     * @param piece target piece
     * @return scaled material value
     */
    private static float pieceValue(byte piece) {
        return switch (Math.abs(piece)) {
            case Piece.PAWN -> 0.10f;
            case Piece.KNIGHT, Piece.BISHOP -> 0.30f;
            case Piece.ROOK -> 0.50f;
            case Piece.QUEEN -> 0.90f;
            default -> 0.0f;
        };
    }

    /**
     * Numerically stable softmax.
     *
     * @param values logits
     * @return probabilities
     */
    private static float[] softmax(float[] values) {
        float max = Float.NEGATIVE_INFINITY;
        for (float value : values) {
            max = Math.max(max, value);
        }
        float[] out = new float[values.length];
        double sum = 0.0d;
        for (int i = 0; i < values.length; i++) {
            out[i] = (float) Math.exp(values[i] - max);
            sum += out[i];
        }
        float inv = (float) (1.0d / Math.max(sum, 1.0e-9d));
        for (int i = 0; i < out.length; i++) {
            out[i] *= inv;
        }
        return out;
    }

    /**
     * Reads a UTF-8 string.
     *
     * @param in source buffer
     * @return string
     * @throws IOException on invalid length
     */
    private static String readString(ByteBuffer in) throws IOException {
        int length = in.getInt();
        if (length < 0 || length > MAX_NAME_BYTES || length > in.remaining()) {
            throw new IOException("Invalid OTIS name length.");
        }
        byte[] bytes = new byte[length];
        in.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Reads a length-prefixed float array.
     *
     * @param in source buffer
     * @param expected expected element count
     * @param label tensor label
     * @return float array
     * @throws IOException on invalid length
     */
    private static float[] readFloats(ByteBuffer in, int expected, String label) throws IOException {
        int length = in.getInt();
        if (length != expected) {
            throw new IOException("Invalid OTIS " + label + " length: " + length
                    + " (expected " + expected + ")");
        }
        if (in.remaining() < length * Float.BYTES) {
            throw new IOException("Truncated OTIS " + label + ".");
        }
        float[] out = new float[length];
        for (int i = 0; i < length; i++) {
            out[i] = in.getFloat();
        }
        return out;
    }
}
