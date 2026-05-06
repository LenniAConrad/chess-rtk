package chess.nn.lc0.bt4;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import chess.core.Position;
import chess.gpu.BackendNames;

/**
 * LCZero BT4-style attention-body network evaluator.
 *
 * <p>
 * The CPU implementation is intentionally simple and batch-size-1 oriented. If
 * CUDA, ROCm, or oneAPI support is available, {@link #load(Path)} can select a
 * native GPU backend using the same fallback model as the LC0 CNN evaluator.
 * </p>
 *
 * @since 2026
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
     * BT4-specific backend selection property.
     */
    private static final String BT4_BACKEND_PROPERTY = "crtk.lc0.bt4.backend";

    /**
     * Shared LC0 backend selection property used as a fallback.
     */
    private static final String LC0_BACKEND_PROPERTY = "crtk.lc0.backend";

    /**
     * Parsed network weights for the CPU backend.
     */
    private final Weights weights;

    /**
     * CUDA backend instance, or {@code null}.
     */
    private final chess.nn.lc0.bt4.cuda.Backend cuda;

    /**
     * ROCm backend instance, or {@code null}.
     */
    private final chess.nn.lc0.bt4.rocm.Backend rocm;

    /**
     * oneAPI backend instance, or {@code null}.
     */
    private final chess.nn.lc0.bt4.oneapi.Backend oneapi;

    /**
     * Creates a network.
     *
     * @param weights validated weights
     */
    private Network(Weights weights) {
        this(weights, null, null, null);
    }

    /**
     * Creates a network using exactly one backend.
     *
     * @param weights CPU weights, or {@code null} for native backends
     * @param cuda CUDA backend, or {@code null}
     * @param rocm ROCm backend, or {@code null}
     * @param oneapi oneAPI backend, or {@code null}
     */
    private Network(
            Weights weights,
            chess.nn.lc0.bt4.cuda.Backend cuda,
            chess.nn.lc0.bt4.rocm.Backend rocm,
            chess.nn.lc0.bt4.oneapi.Backend oneapi) {
        this.weights = weights;
        this.cuda = cuda;
        this.rocm = rocm;
        this.oneapi = oneapi;
    }

    /**
     * Creates a network from in-memory weights.
     *
     * @param weights weights
     * @return network
     */
    public static Network create(Weights weights) {
        if (weights == null) {
            throw new IllegalArgumentException("weights == null");
        }
        return new Network(weights);
    }

    /**
     * Loads a network from the compact CRTK BT4 binary format.
     *
     * @param path path to the model file
     * @return loaded network
     * @throws IOException if the file cannot be read or parsed
     */
    public static Network load(Path path) throws IOException {
        BackendRequest request = BackendRequest.fromSystemProperties();
        BackendAvailability availability = BackendAvailability.detect();
        request.requireAvailable(availability);

        Network accelerated = loadAccelerated(path, request, availability);
        return accelerated != null ? accelerated : loadCpu(path);
    }

    /**
     * Loads a network using the CPU backend.
     *
     * @param path path to the weights file
     * @return CPU-backed network
     * @throws IOException if the weights cannot be read or parsed
     */
    private static Network loadCpu(Path path) throws IOException {
        return create(BinLoader.loadWeights(path));
    }

    /**
     * Attempts requested native backends in priority order.
     *
     * @param path path to the weights file
     * @param request requested backend preferences
     * @param availability detected backend availability
     * @return native-backed network, or {@code null}
     * @throws IOException when a forced backend fails to initialize
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
     * @return initialized network, or {@code null}
     * @throws IOException when a forced backend fails to initialize
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
                throw new IOException(label + " BT4 backend requested but failed to initialize.", e);
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
     * Loads the CUDA backend.
     *
     * @param path weights path
     * @return CUDA-backed network
     */
    private static Network loadCuda(Path path) {
        try (CudaBackendHolder holder = new CudaBackendHolder(chess.nn.lc0.bt4.cuda.Backend.create(path))) {
            Network network = new Network(null, holder.backend, null, null);
            holder.detach();
            return network;
        }
    }

    /**
     * Loads the ROCm backend.
     *
     * @param path weights path
     * @return ROCm-backed network
     */
    private static Network loadRocm(Path path) {
        try (RocmBackendHolder holder = new RocmBackendHolder(chess.nn.lc0.bt4.rocm.Backend.create(path))) {
            Network network = new Network(null, null, holder.backend, null);
            holder.detach();
            return network;
        }
    }

    /**
     * Loads the oneAPI backend.
     *
     * @param path weights path
     * @return oneAPI-backed network
     */
    private static Network loadOneapi(Path path) {
        try (OneapiBackendHolder holder = new OneapiBackendHolder(chess.nn.lc0.bt4.oneapi.Backend.create(path))) {
            Network network = new Network(null, null, null, holder.backend);
            holder.detach();
            return network;
        }
    }

    /**
     * Returns basic network metadata.
     *
     * @return metadata
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
        Architecture architecture = weights.architecture();
        return new Info(
                architecture.name(),
                architecture.inputChannels(),
                architecture.tokens(),
                architecture.embeddingSize(),
                weights.encoders().size(),
                architecture.attentionHeads(),
                architecture.policySize(),
                weights.parameterCount());
    }

    /**
     * Evaluates a position.
     *
     * @param position source position
     * @return prediction
     */
    public Prediction predict(Position position) {
        Encoder.EncodedInput encoded = Encoder.encode(position, weights.architecture().inputFormat());
        return predictEncoded(encoded.planes());
    }

    /**
     * Evaluates already encoded LC0 planes.
     *
     * @param encodedPlanes channel-major {@code [112][64]} input planes
     * @return prediction
     */
    public Prediction predictEncoded(float[] encodedPlanes) {
        if (encodedPlanes == null || encodedPlanes.length != Encoder.INPUT_CHANNELS * Encoder.TOKENS) {
            throw new IllegalArgumentException("encodedPlanes length must be " + (Encoder.INPUT_CHANNELS * Encoder.TOKENS));
        }
        if (cuda != null) {
            return cuda.predictEncoded(encodedPlanes);
        }
        if (rocm != null) {
            return rocm.predictEncoded(encodedPlanes);
        }
        if (oneapi != null) {
            return oneapi.predictEncoded(encodedPlanes);
        }
        float[] body = runBody(encodedPlanes);
        float[] policy = runPolicy(body);
        float[] wdl = runValue(body);
        return new Prediction(policy, wdl, wdl[0] - wdl[2]);
    }

    /**
     * Releases resources. The Java reference backend owns no native resources.
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
     * Returns the active backend.
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
     * Parsed BT4 backend request.
     *
     * @param preferCuda whether CUDA should be attempted
     * @param preferRocm whether ROCm should be attempted
     * @param preferOneapi whether oneAPI should be attempted
     * @param forceCuda whether CUDA was explicitly requested
     * @param forceRocm whether ROCm was explicitly requested
     * @param forceOneapi whether oneAPI was explicitly requested
     */
    private record BackendRequest(
            boolean preferCuda,
            boolean preferRocm,
            boolean preferOneapi,
            boolean forceCuda,
            boolean forceRocm,
            boolean forceOneapi) {

        /**
         * Parses backend system properties.
         *
         * <p>
         * {@code crtk.lc0.bt4.backend} has priority. If absent, BT4 follows the
         * shared {@code crtk.lc0.backend} property used by the CNN evaluator.
         * </p>
         *
         * @return parsed backend request
         */
        static BackendRequest fromSystemProperties() {
            String backend = System.getProperty(BT4_BACKEND_PROPERTY);
            if (backend == null) {
                backend = System.getProperty(LC0_BACKEND_PROPERTY);
            }
            if (backend == null) {
                backend = "auto";
            }
            backend = backend.trim().toLowerCase();
            boolean auto = backend.equals("auto");
            boolean cuda = backend.equals(BACKEND_CUDA);
            boolean rocm = backend.equals(BACKEND_ROCM) || backend.equals("amd") || backend.equals("hip");
            boolean oneapi = backend.equals(BACKEND_ONEAPI) || backend.equals("intel");
            boolean cpu = backend.equals(BACKEND_CPU);
            if (cpu) {
                return new BackendRequest(false, false, false, false, false, false);
            }
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
                    "BT4 CUDA backend requested but unavailable (JNI library not loaded and/or no CUDA device).");
            requireBackendAvailable(forceRocm, availability.rocm(),
                    "BT4 ROCm backend requested but unavailable (JNI library not loaded and/or no ROCm device).");
            requireBackendAvailable(forceOneapi, availability.oneapi(),
                    "BT4 oneAPI backend requested but unavailable (JNI library not loaded and/or no Intel GPU device).");
        }
    }

    /**
     * Detected BT4 backend availability.
     *
     * @param cuda CUDA availability
     * @param rocm ROCm availability
     * @param oneapi oneAPI availability
     */
    private record BackendAvailability(boolean cuda, boolean rocm, boolean oneapi) {

        /**
         * Detects native backend availability.
         *
         * @return availability snapshot
         */
        static BackendAvailability detect() {
            return new BackendAvailability(
                    chess.nn.lc0.bt4.cuda.Backend.isAvailable(),
                    chess.nn.lc0.bt4.rocm.Backend.isAvailable(),
                    chess.nn.lc0.bt4.oneapi.Backend.isAvailable());
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
     * Helper that owns a CUDA backend until detached or closed.
     */
    private static final class CudaBackendHolder implements AutoCloseable {

        /**
         * Owned backend instance.
         */
        private chess.nn.lc0.bt4.cuda.Backend backend;

        /**
         * Creates a holder.
         *
         * @param backend backend instance
         */
        private CudaBackendHolder(chess.nn.lc0.bt4.cuda.Backend backend) {
            this.backend = backend;
        }

        /**
         * Transfers ownership to the caller.
         */
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
     * Helper that owns a ROCm backend until detached or closed.
     */
    private static final class RocmBackendHolder implements AutoCloseable {

        private chess.nn.lc0.bt4.rocm.Backend backend;

        private RocmBackendHolder(chess.nn.lc0.bt4.rocm.Backend backend) {
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
     */
    private static final class OneapiBackendHolder implements AutoCloseable {

        private chess.nn.lc0.bt4.oneapi.Backend backend;

        private OneapiBackendHolder(chess.nn.lc0.bt4.oneapi.Backend backend) {
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
     * Runs the input projection and transformer body.
     *
     * @param encodedPlanes channel-major input
     * @return token-major body activations
     */
    private float[] runBody(float[] encodedPlanes) {
        Architecture architecture = weights.architecture();
        float[] tokens = Encoder.toTokenMajor(encodedPlanes, architecture.inputChannels(), architecture.tokens());
        if (architecture.inputEmbedding() == Architecture.InputEmbedding.PE_MAP) {
            tokens = Encoder.appendPositionMap(tokens);
        }
        float[] flow = denseTokens(tokens, architecture.tokens(), architecture.projectedInputWidth(), weights.inputEmbedding());
        activate(flow, Activation.MISH);
        for (EncoderBlock block : weights.encoders()) {
            flow = runEncoderBlock(flow, block, architecture.tokens(), architecture.layerNormEpsilon());
        }
        return flow;
    }

    /**
     * Runs one transformer encoder block.
     *
     * @param input token-major input
     * @param block block weights
     * @param tokens token count
     * @param eps layer-normalization epsilon
     * @return token-major output
     */
    private float[] runEncoderBlock(float[] input, EncoderBlock block, int tokens, float eps) {
        int embedding = block.attention().query().inDim();
        float[] attended = attention(input, tokens, embedding, block.attention());
        if (block.alpha() != 1.0f) {
            scale(attended, block.alpha());
        }
        addInPlace(attended, input);
        layerNormInPlace(attended, tokens, embedding, block.ln1Gamma(), block.ln1Beta(), eps);

        float[] hidden = denseTokens(attended, tokens, embedding, block.ffnIn());
        activate(hidden, block.activation());
        float[] ffnOut = denseTokens(hidden, tokens, block.ffnIn().outDim(), block.ffnOut());
        if (block.alpha() != 1.0f) {
            scale(ffnOut, block.alpha());
        }
        addInPlace(ffnOut, attended);
        layerNormInPlace(ffnOut, tokens, embedding, block.ln2Gamma(), block.ln2Beta(), eps);
        return ffnOut;
    }

    /**
     * Runs the attention policy head and gathers to 1858 logits.
     *
     * @param body token-major body activations
     * @return compressed policy logits
     */
    private float[] runPolicy(float[] body) {
        Architecture architecture = weights.architecture();
        PolicyHead head = weights.policyHead();
        int tokens = architecture.tokens();
        int embedding = architecture.embeddingSize();
        float[] flow = denseTokens(body, tokens, embedding, head.embedding());
        activate(flow, head.activation());
        int policyEmbedding = head.embedding().outDim();
        for (EncoderBlock block : head.encoders()) {
            flow = runEncoderBlock(flow, block, tokens, architecture.layerNormEpsilon());
        }

        float[] q = denseTokens(flow, tokens, policyEmbedding, head.query());
        float[] k = denseTokens(flow, tokens, policyEmbedding, head.key());
        int policyDModel = head.query().outDim();
        float invScale = (float) (1.0 / Math.sqrt(policyDModel));
        float[] internal = new float[PolicyEncoder.INTERNAL_POLICY_SIZE];
        for (int from = 0; from < 64; from++) {
            int qBase = from * policyDModel;
            int outBase = from * 64;
            for (int to = 0; to < 64; to++) {
                int kBase = to * policyDModel;
                float sum = 0.0f;
                for (int d = 0; d < policyDModel; d++) {
                    sum += q[qBase + d] * k[kBase + d];
                }
                internal[outBase + to] = sum * invScale;
            }
        }
        addUnderpromotionLogits(internal, k, policyDModel, head.promotionWeights());
        return PolicyEncoder.mapInternalPolicy(internal);
    }

    /**
     * Adds the three underpromotion planes to the internal policy tensor.
     *
     * @param internal internal policy logits
     * @param key key tensor used by LC0's promotion projection
     * @param policyDModel key/query width
     * @param promotionWeights promotion projection weights {@code [4][policyDModel]}
     */
    private static void addUnderpromotionLogits(
            float[] internal,
            float[] key,
            int policyDModel,
            float[] promotionWeights) {
        for (int fromFile = 0; fromFile < 8; fromFile++) {
            int minTo = Math.max(0, fromFile - 1);
            int maxTo = Math.min(7, fromFile + 1);
            for (int toFile = minTo; toFile <= maxTo; toFile++) {
                int from = 48 + fromFile;
                int to = 56 + toFile;
                float base = internal[from * 64 + to];
                float queen = promotionProjection(key, to, policyDModel, promotionWeights, 3);
                for (int promo = 0; promo < 3; promo++) {
                    int internalIndex = PolicyEncoder.FROM_TO_POLICY_SIZE + fromFile * 24 + toFile * 3 + promo;
                    internal[internalIndex] = base + queen
                            + promotionProjection(key, to, policyDModel, promotionWeights, promo);
                }
            }
        }
    }

    /**
     * Computes one promotion projection value.
     *
     * @param key key tensor
     * @param token destination token
     * @param policyDModel key width
     * @param weights projection weights
     * @param output output column
     * @return projected value
     */
    private static float promotionProjection(float[] key, int token, int policyDModel, float[] weights, int output) {
        float sum = 0.0f;
        int keyBase = token * policyDModel;
        int weightBase = output * policyDModel;
        for (int d = 0; d < policyDModel; d++) {
            sum += key[keyBase + d] * weights[weightBase + d];
        }
        return sum;
    }

    /**
     * Runs the WDL value head.
     *
     * @param body token-major body activations
     * @return WDL probabilities
     */
    private float[] runValue(float[] body) {
        Architecture architecture = weights.architecture();
        ValueHead head = weights.valueHead();
        int tokens = architecture.tokens();
        int embedding = architecture.embeddingSize();
        float[] flow = denseTokens(body, tokens, embedding, head.embedding());
        activate(flow, head.activation());
        float[] flat = flow;
        float[] hidden = denseVector(flat, head.fc1());
        activate(hidden, head.activation());
        float[] logits = denseVector(hidden, head.fc2());
        return softmax(logits);
    }

    /**
     * Runs multi-head self-attention.
     *
     * @param input token-major input
     * @param tokens token count
     * @param embedding input width
     * @param attention attention weights
     * @return token-major attention output
     */
    private static float[] attention(float[] input, int tokens, int embedding, Attention attention) {
        float[] q = denseTokens(input, tokens, embedding, attention.query());
        float[] k = denseTokens(input, tokens, embedding, attention.key());
        float[] v = denseTokens(input, tokens, embedding, attention.value());
        int dModel = attention.query().outDim();
        int heads = attention.heads();
        int depth = dModel / heads;
        float[] combined = new float[tokens * dModel];
        float[] scores = new float[tokens];
        for (int head = 0; head < heads; head++) {
            int headOffset = head * depth;
            float invScale = (float) (1.0 / Math.sqrt(depth));
            for (int queryToken = 0; queryToken < tokens; queryToken++) {
                int qBase = queryToken * dModel + headOffset;
                for (int keyToken = 0; keyToken < tokens; keyToken++) {
                    int kBase = keyToken * dModel + headOffset;
                    float sum = 0.0f;
                    for (int d = 0; d < depth; d++) {
                        sum += q[qBase + d] * k[kBase + d];
                    }
                    scores[keyToken] = sum * invScale;
                }
                softmaxInPlace(scores);
                int outBase = queryToken * dModel + headOffset;
                for (int d = 0; d < depth; d++) {
                    float sum = 0.0f;
                    for (int keyToken = 0; keyToken < tokens; keyToken++) {
                        int vBase = keyToken * dModel + headOffset;
                        sum += scores[keyToken] * v[vBase + d];
                    }
                    combined[outBase + d] = sum;
                }
            }
        }
        return denseTokens(combined, tokens, dModel, attention.out());
    }

    /**
     * Runs a dense layer over token rows.
     *
     * @param input token-major input
     * @param tokens token count
     * @param inDim input width
     * @param layer layer
     * @return token-major output
     */
    private static float[] denseTokens(float[] input, int tokens, int inDim, Dense layer) {
        if (layer.inDim() != inDim) {
            throw new IllegalArgumentException("dense input mismatch: " + layer.inDim() + " vs " + inDim);
        }
        float[] out = new float[tokens * layer.outDim()];
        for (int token = 0; token < tokens; token++) {
            denseRow(input, token * inDim, layer, out, token * layer.outDim());
        }
        return out;
    }

    /**
     * Runs a dense layer over one vector.
     *
     * @param input input vector
     * @param layer layer
     * @return output vector
     */
    private static float[] denseVector(float[] input, Dense layer) {
        if (input.length != layer.inDim()) {
            throw new IllegalArgumentException("dense vector input mismatch");
        }
        float[] out = new float[layer.outDim()];
        denseRow(input, 0, layer, out, 0);
        return out;
    }

    /**
     * Dense row helper.
     *
     * @param input input data
     * @param inputOffset input row offset
     * @param layer layer
     * @param output output data
     * @param outputOffset output row offset
     */
    private static void denseRow(float[] input, int inputOffset, Dense layer, float[] output, int outputOffset) {
        for (int out = 0; out < layer.outDim(); out++) {
            float sum = layer.bias()[out];
            int weightBase = out * layer.inDim();
            for (int in = 0; in < layer.inDim(); in++) {
                sum += layer.weights()[weightBase + in] * input[inputOffset + in];
            }
            output[outputOffset + out] = sum;
        }
    }

    /**
     * Applies activation in-place.
     *
     * @param values values
     * @param activation activation
     */
    private static void activate(float[] values, Activation activation) {
        if (activation == Activation.NONE) {
            return;
        }
        for (int i = 0; i < values.length; i++) {
            values[i] = activation.apply(values[i]);
        }
    }

    /**
     * Scales values in-place.
     *
     * @param values values
     * @param scale scale factor
     */
    private static void scale(float[] values, float scale) {
        for (int i = 0; i < values.length; i++) {
            values[i] *= scale;
        }
    }

    /**
     * Adds source to destination in-place.
     *
     * @param dest destination
     * @param source source
     */
    private static void addInPlace(float[] dest, float[] source) {
        if (dest.length != source.length) {
            throw new IllegalArgumentException("add length mismatch");
        }
        for (int i = 0; i < dest.length; i++) {
            dest[i] += source[i];
        }
    }

    /**
     * Applies layer normalization independently to each token.
     *
     * @param values token-major values
     * @param tokens token count
     * @param dim row width
     * @param gamma scale vector
     * @param beta bias vector
     * @param eps epsilon
     */
    private static void layerNormInPlace(float[] values, int tokens, int dim, float[] gamma, float[] beta, float eps) {
        for (int token = 0; token < tokens; token++) {
            int base = token * dim;
            float mean = 0.0f;
            for (int i = 0; i < dim; i++) {
                mean += values[base + i];
            }
            mean /= dim;
            float variance = 0.0f;
            for (int i = 0; i < dim; i++) {
                float centered = values[base + i] - mean;
                variance += centered * centered;
            }
            float invStd = (float) (1.0 / Math.sqrt(variance / dim + eps));
            for (int i = 0; i < dim; i++) {
                values[base + i] = (values[base + i] - mean) * invStd * gamma[i] + beta[i];
            }
        }
    }

    /**
     * Softmaxes a vector in-place.
     *
     * @param values vector
     */
    private static void softmaxInPlace(float[] values) {
        float max = Float.NEGATIVE_INFINITY;
        for (float value : values) {
            if (value > max) {
                max = value;
            }
        }
        float sum = 0.0f;
        for (int i = 0; i < values.length; i++) {
            float exp = (float) Math.exp(values[i] - max);
            values[i] = exp;
            sum += exp;
        }
        if (sum > 0.0f) {
            for (int i = 0; i < values.length; i++) {
                values[i] /= sum;
            }
        }
    }

    /**
     * Returns a softmaxed copy.
     *
     * @param logits logits
     * @return probabilities
     */
    private static float[] softmax(float[] logits) {
        float[] out = Arrays.copyOf(logits, logits.length);
        softmaxInPlace(out);
        return out;
    }

    /**
     * Network metadata.
     *
     * @param name architecture name
     * @param inputChannels input channel count
     * @param tokens token count
     * @param embeddingSize model width
     * @param encoderLayers encoder count
     * @param attentionHeads attention head count
     * @param policySize policy size
     * @param parameterCount parameter count
     */
    public record Info(
            String name,
            int inputChannels,
            int tokens,
            int embeddingSize,
            int encoderLayers,
            int attentionHeads,
            int policySize,
            long parameterCount) {
    }

    /**
     * Prediction for one position.
     *
     * @param policy compressed policy logits
     * @param wdl WDL probabilities ordered as win, draw, loss
     * @param value scalar {@code W-L}
     */
    public record Prediction(float[] policy, float[] wdl, float value) {

        /**
         * Compares array contents and scalar value.
         *
         * @param o other object
         * @return true when equal
         */
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Prediction other)) {
                return false;
            }
            return Float.floatToIntBits(value) == Float.floatToIntBits(other.value)
                    && Arrays.equals(policy, other.policy)
                    && Arrays.equals(wdl, other.wdl);
        }

        /**
         * Hashes arrays by content.
         *
         * @return hash code
         */
        @Override
        public int hashCode() {
            int result = Arrays.hashCode(policy);
            result = 31 * result + Arrays.hashCode(wdl);
            result = 31 * result + Float.hashCode(value);
            return result;
        }
    }

    /**
     * Network weight bundle.
     *
     * @param architecture architecture metadata
     * @param inputEmbedding input embedding dense layer
     * @param encoders body encoder blocks
     * @param policyHead attention policy head
     * @param valueHead WDL value head
     */
    public record Weights(
            Architecture architecture,
            Dense inputEmbedding,
            List<EncoderBlock> encoders,
            PolicyHead policyHead,
            ValueHead valueHead) {

        /**
         * Validates the bundle.
         */
        public Weights {
            if (architecture == null || inputEmbedding == null || encoders == null || policyHead == null
                    || valueHead == null) {
                throw new IllegalArgumentException("BT4 weights contain null component");
            }
            encoders = List.copyOf(encoders);
            if (inputEmbedding.inDim() != architecture.projectedInputWidth()
                    || inputEmbedding.outDim() != architecture.embeddingSize()) {
                throw new IllegalArgumentException("input embedding shape does not match architecture");
            }
            if (encoders.size() != architecture.encoderLayers()) {
                throw new IllegalArgumentException("encoder count does not match architecture");
            }
        }

        /**
         * Returns total parameter count.
         *
         * @return parameter count
         */
        public long parameterCount() {
            long total = inputEmbedding.parameterCount() + policyHead.parameterCount() + valueHead.parameterCount();
            for (EncoderBlock encoder : encoders) {
                total += encoder.parameterCount();
            }
            return total;
        }
    }

    /**
     * Dense layer with row-major weights {@code [out][in]}.
     *
     * @param inDim input dimension
     * @param outDim output dimension
     * @param weights row-major weights
     * @param bias output bias
     */
    public record Dense(int inDim, int outDim, float[] weights, float[] bias) {

        /**
         * Validates dense layer shape.
         */
        public Dense {
            if (inDim <= 0 || outDim <= 0) {
                throw new IllegalArgumentException("dense dimensions must be positive");
            }
            weights = copy(weights, "weights");
            bias = copy(bias, "bias");
            if (weights.length != inDim * outDim) {
                throw new IllegalArgumentException("dense weight length mismatch");
            }
            if (bias.length != outDim) {
                throw new IllegalArgumentException("dense bias length mismatch");
            }
        }

        /**
         * Returns parameter count.
         *
         * @return parameter count
         */
        public long parameterCount() {
            return (long) weights.length + bias.length;
        }
    }

    /**
     * Multi-head attention weights.
     *
     * @param heads attention head count
     * @param query query projection
     * @param key key projection
     * @param value value projection
     * @param out output projection
     */
    public record Attention(int heads, Dense query, Dense key, Dense value, Dense out) {

        /**
         * Validates attention dimensions.
         */
        public Attention {
            if (heads <= 0 || query == null || key == null || value == null || out == null) {
                throw new IllegalArgumentException("invalid attention layer");
            }
            if (query.outDim() != key.outDim() || query.outDim() != value.outDim()) {
                throw new IllegalArgumentException("Q/K/V output dimensions must match");
            }
            if (query.outDim() % heads != 0) {
                throw new IllegalArgumentException("attention width must be divisible by heads");
            }
            if (out.inDim() != query.outDim() || out.outDim() != query.inDim()) {
                throw new IllegalArgumentException("attention output projection shape mismatch");
            }
        }

        /**
         * Returns parameter count.
         *
         * @return parameter count
         */
        public long parameterCount() {
            return query.parameterCount() + key.parameterCount() + value.parameterCount() + out.parameterCount();
        }
    }

    /**
     * Transformer encoder block.
     *
     * @param attention attention layer
     * @param ffnIn first FFN dense layer
     * @param ffnOut second FFN dense layer
     * @param ln1Gamma first layer-norm scale
     * @param ln1Beta first layer-norm bias
     * @param ln2Gamma second layer-norm scale
     * @param ln2Beta second layer-norm bias
     * @param activation FFN activation
     * @param alpha DeepNet residual scaling
     */
    public record EncoderBlock(
            Attention attention,
            Dense ffnIn,
            Dense ffnOut,
            float[] ln1Gamma,
            float[] ln1Beta,
            float[] ln2Gamma,
            float[] ln2Beta,
            Activation activation,
            float alpha) {

        /**
         * Validates block shape.
         */
        public EncoderBlock {
            if (attention == null || ffnIn == null || ffnOut == null || activation == null) {
                throw new IllegalArgumentException("encoder block contains null component");
            }
            ln1Gamma = copy(ln1Gamma, "ln1Gamma");
            ln1Beta = copy(ln1Beta, "ln1Beta");
            ln2Gamma = copy(ln2Gamma, "ln2Gamma");
            ln2Beta = copy(ln2Beta, "ln2Beta");
            int embedding = attention.out().outDim();
            if (ffnIn.inDim() != embedding || ffnOut.outDim() != embedding || ffnOut.inDim() != ffnIn.outDim()) {
                throw new IllegalArgumentException("FFN dimensions do not match encoder embedding");
            }
            if (ln1Gamma.length != embedding || ln1Beta.length != embedding || ln2Gamma.length != embedding
                    || ln2Beta.length != embedding) {
                throw new IllegalArgumentException("layer norm dimensions do not match encoder embedding");
            }
        }

        /**
         * Returns parameter count.
         *
         * @return parameter count
         */
        public long parameterCount() {
            return attention.parameterCount() + ffnIn.parameterCount() + ffnOut.parameterCount()
                    + ln1Gamma.length + ln1Beta.length + ln2Gamma.length + ln2Beta.length;
        }
    }

    /**
     * Attention policy head.
     *
     * @param embedding policy embedding layer
     * @param encoders optional policy-only encoder blocks
     * @param query policy query projection
     * @param key policy key projection
     * @param promotionWeights promotion projection weights {@code [4][policyDModel]}
     * @param activation policy embedding activation
     */
    public record PolicyHead(
            Dense embedding,
            List<EncoderBlock> encoders,
            Dense query,
            Dense key,
            float[] promotionWeights,
            Activation activation) {

        /**
         * Validates head shape.
         */
        public PolicyHead {
            if (embedding == null || encoders == null || query == null || key == null || activation == null) {
                throw new IllegalArgumentException("policy head contains null component");
            }
            encoders = List.copyOf(encoders);
            promotionWeights = copy(promotionWeights, "promotionWeights");
            if (query.inDim() != embedding.outDim() || key.inDim() != embedding.outDim()
                    || query.outDim() != key.outDim()) {
                throw new IllegalArgumentException("policy Q/K dimensions mismatch");
            }
            if (promotionWeights.length != 4 * query.outDim()) {
                throw new IllegalArgumentException("promotion weight length mismatch");
            }
        }

        /**
         * Returns parameter count.
         *
         * @return parameter count
         */
        public long parameterCount() {
            long total = embedding.parameterCount() + query.parameterCount() + key.parameterCount()
                    + promotionWeights.length;
            for (EncoderBlock encoder : encoders) {
                total += encoder.parameterCount();
            }
            return total;
        }
    }

    /**
     * WDL value head.
     *
     * @param embedding value embedding layer
     * @param fc1 first dense layer
     * @param fc2 output dense layer
     * @param activation value activation
     */
    public record ValueHead(Dense embedding, Dense fc1, Dense fc2, Activation activation) {

        /**
         * Validates value head.
         */
        public ValueHead {
            if (embedding == null || fc1 == null || fc2 == null || activation == null) {
                throw new IllegalArgumentException("value head contains null component");
            }
            if (fc1.inDim() != embedding.outDim() * Encoder.TOKENS || fc2.inDim() != fc1.outDim()
                    || fc2.outDim() != 3) {
                throw new IllegalArgumentException("value head dimensions mismatch");
            }
        }

        /**
         * Returns parameter count.
         *
         * @return parameter count
         */
        public long parameterCount() {
            return embedding.parameterCount() + fc1.parameterCount() + fc2.parameterCount();
        }
    }

    /**
     * Supported activations in LC0 attention-body networks.
     */
    public enum Activation {

        /**
         * No activation.
         */
        NONE,

        /**
         * ReLU activation.
         */
        RELU,

        /**
         * Mish activation, used by BT4 FFNs.
         */
        MISH;

        /**
         * Applies activation to one value.
         *
         * @param x input
         * @return activated value
         */
        float apply(float x) {
            return switch (this) {
                case NONE -> x;
                case RELU -> x > 0.0f ? x : 0.0f;
                case MISH -> x * (float) Math.tanh(softplus(x));
            };
        }

        /**
         * Numerically stable softplus.
         *
         * @param x input
         * @return softplus
         */
        private static float softplus(float x) {
            if (x > 20.0f) {
                return x;
            }
            if (x < -20.0f) {
                return (float) Math.exp(x);
            }
            return (float) Math.log1p(Math.exp(x));
        }
    }

    /**
     * Copies a float array.
     *
     * @param source source
     * @param name field name
     * @return copy
     */
    private static float[] copy(float[] source, String name) {
        if (source == null) {
            throw new IllegalArgumentException(name + " == null");
        }
        return Arrays.copyOf(source, source.length);
    }
}
