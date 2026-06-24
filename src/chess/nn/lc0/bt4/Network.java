package chess.nn.lc0.bt4;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
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
     * Prefix used for captured encoder-block tensors.
     */
    private static final String BLOCK_CAPTURE_PREFIX = "bt4.block";

    /**
     * Parsed network weights for the CPU backend.
     */
    private final Weights weights;

    /**
     * Architecture metadata used for position encoding.
     */
    private final Architecture architecture;

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
        this(weights, weights.architecture(), null, null, null);
    }

    /**
     * Creates a network using exactly one backend.
     *
     * @param weights CPU weights, or {@code null} for native backends
     * @param cuda CUDA backend, or {@code null}
     * @param rocm ROCm backend, or {@code null}
     * @param oneapi oneAPI backend, or {@code null}
     * @param architecture network architecture
     */
    private Network(
            Weights weights,
            Architecture architecture,
            chess.nn.lc0.bt4.cuda.Backend cuda,
            chess.nn.lc0.bt4.rocm.Backend rocm,
            chess.nn.lc0.bt4.oneapi.Backend oneapi) {
        if (architecture == null) {
            throw new IllegalArgumentException("architecture == null");
        }
        this.weights = weights;
        this.architecture = architecture;
        this.cuda = cuda;
        this.rocm = rocm;
        this.oneapi = oneapi;
    }

    /**
     * Creates a network from in-memory weights.
     *
     * @param weights network weights
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
     * Loads a network using the pure-Java CPU backend, bypassing GPU backend
     * selection entirely.
     *
     * <p>Unlike {@link #load(Path)}, this never consults {@code crtk.lc0.bt4.backend}
     * and never touches global state, so callers that specifically need the CPU
     * path (for example the workbench activation visualizer, which needs the
     * intermediate tensors only the CPU path exposes) can request it without
     * mutating JVM-wide backend selection.
     *
     * @param path path to the weights file
     * @return CPU-backed network
     * @throws IOException if the weights cannot be read or parsed
     */
    public static Network loadCpu(Path path) throws IOException {
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
         * @throws java.io.IOException if IOException is raised by the underlying operation
         */
        Network load(Path path) throws IOException;
    }

    /**
     * Loads the CUDA backend.
     *
     * @param path weights path
     * @return CUDA-backed network
     * @throws java.io.IOException if IOException is raised by the underlying operation
     */
    private static Network loadCuda(Path path) throws IOException {
        Architecture architecture = BinLoader.loadArchitecture(path);
        try (CudaBackendHolder holder = new CudaBackendHolder(chess.nn.lc0.bt4.cuda.Backend.create(path))) {
            Network network = new Network(null, architecture, holder.backend, null, null);
            holder.detach();
            return network;
        }
    }

    /**
     * Loads the ROCm backend.
     *
     * @param path weights path
     * @return ROCm-backed network
     * @throws java.io.IOException if IOException is raised by the underlying operation
     */
    private static Network loadRocm(Path path) throws IOException {
        Architecture architecture = BinLoader.loadArchitecture(path);
        try (RocmBackendHolder holder = new RocmBackendHolder(chess.nn.lc0.bt4.rocm.Backend.create(path))) {
            Network network = new Network(null, architecture, null, holder.backend, null);
            holder.detach();
            return network;
        }
    }

    /**
     * Loads the oneAPI backend.
     *
     * @param path weights path
     * @return oneAPI-backed network
     * @throws java.io.IOException if IOException is raised by the underlying operation
     */
    private static Network loadOneapi(Path path) throws IOException {
        Architecture architecture = BinLoader.loadArchitecture(path);
        try (OneapiBackendHolder holder = new OneapiBackendHolder(chess.nn.lc0.bt4.oneapi.Backend.create(path))) {
            Network network = new Network(null, architecture, null, null, holder.backend);
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
     * Per-prediction activation sink. Set on entry to a predict call and
     * cleared on exit. Single-threaded per-network; callers expecting
     * concurrency should hold independent Network instances.
     */
    private chess.nn.ActivationSink sink;

    /**
     * Evaluates a position.
     *
     * @param position source position
     * @return prediction
     */
    public Prediction predict(Position position) {
        return predict(position, null);
    }

    /**
     * Evaluates a batch of positions without activation capture.
     *
     * @param positions source positions
     * @return predictions aligned with {@code positions}
     */
    public List<Prediction> predictBatch(List<Position> positions) {
        if (positions == null) {
            throw new IllegalArgumentException("positions == null");
        }
        List<float[]> encoded = new ArrayList<>(positions.size());
        InputFormat inputFormat = architecture.inputFormat();
        for (Position position : positions) {
            encoded.add(Encoder.encode(position, inputFormat).planes());
        }
        return predictEncodedBatch(encoded);
    }

    /**
     * Evaluates a batch and preserves each canonical transform for policy
     * indexing.
     *
     * @param positions source positions
     * @return predictions plus transforms aligned with {@code positions}
     */
    public List<TransformedPrediction> predictBatchWithTransforms(List<Position> positions) {
        if (positions == null) {
            throw new IllegalArgumentException("positions == null");
        }
        List<float[]> encodedBatch = new ArrayList<>(positions.size());
        int[] transforms = new int[positions.size()];
        InputFormat inputFormat = architecture.inputFormat();
        for (int i = 0; i < positions.size(); i++) {
            Encoder.EncodedInput encoded = Encoder.encode(positions.get(i), inputFormat);
            encodedBatch.add(encoded.planes());
            transforms[i] = encoded.transform();
        }
        List<Prediction> predictions = predictEncodedBatch(encodedBatch);
        List<TransformedPrediction> out = new ArrayList<>(predictions.size());
        for (int i = 0; i < predictions.size(); i++) {
            out.add(new TransformedPrediction(predictions.get(i), transforms[i]));
        }
        return out;
    }

    /**
     * Evaluates a position and captures intermediate activations.
     *
     * @param position source position
     * @param sink activation collector; null for production callers
     * @return prediction
     */
    public Prediction predict(Position position, chess.nn.ActivationSink sink) {
        Encoder.EncodedInput encoded = Encoder.encode(position, architecture.inputFormat());
        if (sink != null) {
            sink.put("bt4.input.transform", new int[] { 1 }, new float[] { encoded.transform() });
        }
        return predictEncoded(encoded.planes(), sink);
    }

    /**
     * Evaluates already encoded LC0 planes.
     *
     * @param encodedPlanes channel-major {@code [112][64]} input planes
     * @return prediction
     */
    public Prediction predictEncoded(float[] encodedPlanes) {
        return predictEncoded(encodedPlanes, null);
    }

    /**
     * Evaluates already encoded LC0 planes as a batch.
     *
     * @param encodedBatch encoded plane arrays
     * @return predictions aligned with {@code encodedBatch}
     */
    public List<Prediction> predictEncodedBatch(List<float[]> encodedBatch) {
        if (encodedBatch == null) {
            throw new IllegalArgumentException("encodedBatch == null");
        }
        List<Prediction> out = new ArrayList<>(encodedBatch.size());
        for (float[] encodedPlanes : encodedBatch) {
            out.add(predictEncoded(encodedPlanes));
        }
        return out;
    }

    /**
     * Evaluates already encoded LC0 planes and optionally captures
     * intermediate activations into the supplied sink.
     *
     * @param encodedPlanes channel-major {@code [112][64]} input planes
     * @param activationSink activation collector; null for production callers
     * @return prediction
     */
    public Prediction predictEncoded(float[] encodedPlanes, chess.nn.ActivationSink activationSink) {
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
        sink = activationSink;
        try {
            capture("bt4.input", new int[] { Encoder.INPUT_CHANNELS, 8, 8 }, encodedPlanes);
            float[] body = runBody(encodedPlanes);
            capture("bt4.final.tokens", new int[] { Encoder.TOKENS, weights.architecture().embeddingSize() }, body);
            captureTokenEnergy(body);
            float[] policy = runPolicy(body);
            capture("bt4.policy.logits", new int[] { policy.length }, policy);
            float[] wdl = runValue(body);
            capture("bt4.value.wdl", new int[] { 3 }, wdl);
            float scalar = wdl[0] - wdl[2];
            capture("bt4.value.scalar", new int[] { 1 }, new float[] { scalar });
            return new Prediction(policy, wdl, scalar);
        } finally {
            sink = null;
        }
    }

    /**
     * Forwards one snapshot entry to the active sink, if any.
     *
     * @param key activation key
     * @param shape tensor shape
     * @param data flat values (caller-owned; sink defensively copies)
     */
    private void capture(String key, int[] shape, float[] data) {
        if (sink != null) {
            sink.put(key, shape, data.clone());
        }
    }

    /**
     * Captures a derived per-square "token energy" map: the mean absolute
     * embedding activation per board square. Provides the BT4 view a stable
     * scalar-per-square signal without requiring a re-run of attention.
     *
     * @param body final transformer output {@code [tokens, embedding]}
     */
    private void captureTokenEnergy(float[] body) {
        if (sink == null) {
            return;
        }
        int tokens = weights.architecture().tokens();
        int embedding = weights.architecture().embeddingSize();
        float[] energy = new float[tokens];
        for (int t = 0; t < tokens; t++) {
            double sum = 0.0;
            int base = t * embedding;
            for (int d = 0; d < embedding; d++) {
                sum += Math.abs(body[base + d]);
            }
            energy[t] = (float) (sum / embedding);
        }
        sink.put("bt4.token.energy", new int[] { 8, 8 }, energy);
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
     * Runs the input projection and transformer body.
     *
     * @param encodedPlanes channel-major input
     * @return token-major body activations
     */
    private float[] runBody(float[] encodedPlanes) {
        Architecture currentArchitecture = weights.architecture();
        InputStack stack = weights.input();
        int tokens = currentArchitecture.tokens();
        float[] perToken = Encoder.toTokenMajor(encodedPlanes, currentArchitecture.inputChannels(), tokens);

        float[] flow;
        if (currentArchitecture.inputEmbedding() == Architecture.InputEmbedding.PE_MAP) {
            float[] mapped = Encoder.appendPositionMap(perToken);
            flow = denseTokens(mapped, tokens, currentArchitecture.inputChannels() + tokens, stack.embedding());
        } else if (currentArchitecture.inputEmbedding() == Architecture.InputEmbedding.PE_DENSE) {
            float[] concatenated = inputDenseEmbedding(perToken, stack.preproc(),
                    currentArchitecture.inputChannels(), tokens, stack.embedding().inDim());
            flow = denseTokens(concatenated, tokens, stack.embedding().inDim(), stack.embedding());
        } else {
            flow = denseTokens(perToken, tokens, currentArchitecture.inputChannels(), stack.embedding());
        }
        activate(flow, currentArchitecture.defaultActivation());

        int embeddingSize = currentArchitecture.embeddingSize();
        if (currentArchitecture.inputEmbedding() == Architecture.InputEmbedding.PE_DENSE
                && stack.embLnGamma() != null) {
            layerNormInPlace(flow, tokens, embeddingSize, stack.embLnGamma(), stack.embLnBeta(),
                    currentArchitecture.layerNormEpsilon());
        }
        if (stack.multGate() != null) {
            applyPerTokenGate(flow, tokens, embeddingSize, stack.multGate(), true);
        }
        if (stack.addGate() != null) {
            applyPerTokenGate(flow, tokens, embeddingSize, stack.addGate(), false);
        }
        float alpha = encoderAlpha(currentArchitecture);
        if (stack.embFfn() != null) {
            float[] ffnIn = denseTokens(flow, tokens, embeddingSize, stack.embFfn().dense1());
            activate(ffnIn, currentArchitecture.ffnActivation());
            float[] ffnOut = denseTokens(ffnIn, tokens, stack.embFfn().dense1().outDim(),
                    stack.embFfn().dense2());
            if (alpha != 1.0f) {
                scale(ffnOut, alpha);
            }
            addInPlace(ffnOut, flow);
            layerNormInPlace(ffnOut, tokens, embeddingSize, stack.embFfnLnGamma(), stack.embFfnLnBeta(),
                    currentArchitecture.layerNormEpsilon());
            flow = ffnOut;
        }

        capture("bt4.token.embedding", new int[] { tokens, embeddingSize }, flow);
        int blockIndex = 0;
        for (EncoderBlock block : weights.encoders()) {
            flow = runEncoderBlock(flow, block, tokens, currentArchitecture, alpha, blockIndex);
            capture(BLOCK_CAPTURE_PREFIX + blockIndex + ".out",
                    new int[] { tokens, embeddingSize }, flow);
            blockIndex++;
        }
        return flow;
    }

    /**
     * Number of input channels per token consumed by the PE_DENSE preproc
     * layer. LC0 hardcodes this to 12 (the leading position-info planes).
     */
    private static final int PREPROC_CHANNELS_PER_TOKEN = 12;

    /**
     * Computes the BT4 PE_DENSE input concatenation.
     *
     * <p>
     * LC0's BT4 input takes the first {@link #PREPROC_CHANNELS_PER_TOKEN}
     * channels of every token, flattens them into one
     * {@code [tokens * PREPROC_CHANNELS_PER_TOKEN]} vector, runs that through
     * a single dense layer to a {@code [tokens * outPerTok]} vector, reshapes
     * to {@code [tokens, outPerTok]}, and concatenates the result after the
     * original {@code [tokens, inputChannels]} features. The combined
     * {@code [tokens, inputChannels + outPerTok]} tensor is the input to the
     * main embedding projection.
     * </p>
     *
     * @param perToken token-major input
     * @param preproc preproc dense layer
     * @param inputChannels base input channel count
     * @param tokens token count
     * @param embeddingInDim expected embedding input width
     * @return concatenated token-major input ready for the embedding projection
     */
    private static float[] inputDenseEmbedding(float[] perToken, Dense preproc, int inputChannels,
            int tokens, int embeddingInDim) {
        if (preproc == null) {
            throw new IllegalStateException("PE_DENSE input embedding requires preproc weights");
        }
        int expectedIn = tokens * PREPROC_CHANNELS_PER_TOKEN;
        if (preproc.inDim() != expectedIn) {
            throw new IllegalArgumentException(
                    "preproc input width " + preproc.inDim() + " != tokens*" + PREPROC_CHANNELS_PER_TOKEN);
        }
        if (preproc.outDim() % tokens != 0) {
            throw new IllegalArgumentException("preproc output width not divisible by tokens");
        }
        int outPerTok = preproc.outDim() / tokens;
        int outWidth = inputChannels + outPerTok;
        if (embeddingInDim != outWidth) {
            throw new IllegalArgumentException(
                    "embedding input width " + embeddingInDim + " != base+preproc " + outWidth);
        }
        float[] sliced = new float[expectedIn];
        for (int t = 0; t < tokens; t++) {
            System.arraycopy(perToken, t * inputChannels, sliced, t * PREPROC_CHANNELS_PER_TOKEN,
                    PREPROC_CHANNELS_PER_TOKEN);
        }
        float[] processed = denseVector(sliced, preproc);
        float[] concatenated = new float[tokens * outWidth];
        for (int t = 0; t < tokens; t++) {
            System.arraycopy(perToken, t * inputChannels, concatenated, t * outWidth, inputChannels);
            System.arraycopy(processed, t * outPerTok, concatenated, t * outWidth + inputChannels, outPerTok);
        }
        return concatenated;
    }

    /**
     * Applies a per-square per-channel gate in place. Multiplicative when
     * {@code multiplicative} is true, additive otherwise.
     *
     * @param values token-major values
     * @param tokens token count
     * @param dim per-token width
     * @param gate flat gate values, layout {@code [tokens, dim]}
     * @param multiplicative true to multiply, false to add
     */
    private static void applyPerTokenGate(float[] values, int tokens, int dim, float[] gate,
            boolean multiplicative) {
        if (gate.length != tokens * dim) {
            throw new IllegalArgumentException("gate length mismatch");
        }
        for (int i = 0; i < values.length; i++) {
            if (multiplicative) {
                values[i] *= gate[i];
            } else {
                values[i] += gate[i];
            }
        }
    }

    /**
     * Returns the LC0 DeepNet residual scale used by BT4: {@code (2 * N)^-0.25}.
     *
     * @param architecture network architecture
     * @return scaling factor
     */
    private static float encoderAlpha(Architecture architecture) {
        int n = Math.max(1, architecture.encoderLayers());
        return (float) Math.pow(2.0 * n, -0.25);
    }

    /**
     * Runs one transformer encoder block. The block's own {@code alpha} is
     * preserved; the {@code alpha} parameter overrides it when an extended
     * BT4 architecture provides a deepnet-style scale to use instead.
     *
     * @param input token-major input
     * @param block block weights
     * @param tokens token count
     * @param architecture architecture metadata (LN eps, activations, smolgen flag)
     * @param alpha residual scaling override; pass {@code block.alpha()} for compact blocks
     * @return token-major output
     * @param blockIndex zero-based block index
     */
    private float[] runEncoderBlock(float[] input, EncoderBlock block, int tokens, Architecture architecture,
            float alpha, int blockIndex) {
        int embedding = block.attention().query().inDim();
        float eps = architecture.layerNormEpsilon();
        float[] attended = attention(input, tokens, embedding, block.attention(),
                architecture.hasSmolgen() ? weights.smolgenW() : null,
                architecture.smolgenActivation(), eps, blockIndex);
        if (alpha != 1.0f) {
            scale(attended, alpha);
        }
        addInPlace(attended, input);
        layerNormInPlace(attended, tokens, embedding, block.ln1Gamma(), block.ln1Beta(), eps);
        if (blockIndex >= 0) {
            capture(BLOCK_CAPTURE_PREFIX + blockIndex + ".attention.out",
                    new int[] { tokens, embedding }, attended);
        }

        float[] hidden = denseTokens(attended, tokens, embedding, block.ffnIn());
        activate(hidden, block.activation());
        float[] ffnOut = denseTokens(hidden, tokens, block.ffnIn().outDim(), block.ffnOut());
        if (alpha != 1.0f) {
            scale(ffnOut, alpha);
        }
        addInPlace(ffnOut, attended);
        layerNormInPlace(ffnOut, tokens, embedding, block.ln2Gamma(), block.ln2Beta(), eps);
        if (blockIndex >= 0) {
            capture(BLOCK_CAPTURE_PREFIX + blockIndex + ".ffn",
                    new int[] { tokens, embedding }, ffnOut);
        }
        return ffnOut;
    }

    /**
     * Compact entry point used by the policy head's optional encoder stack
     * (which never carries smolgen weights).
     *
     * @param input token-major input
     * @param block block weights
     * @param tokens token count
     * @param eps layer-normalization epsilon
     * @return token-major output
     */
    private float[] runEncoderBlock(float[] input, EncoderBlock block, int tokens, float eps) {
        Architecture currentArchitecture = weights.architecture();
        return runEncoderBlock(input, block, tokens,
                currentArchitecture.hasSmolgen() ? currentArchitecture : Architecture.simplified(
                        currentArchitecture.name(),
                        currentArchitecture.inputFormat(),
                        currentArchitecture.inputEmbedding(),
                        currentArchitecture.inputChannels(),
                        tokens,
                        block.attention().query().inDim(),
                        currentArchitecture.encoderLayers(),
                        block.attention().heads(),
                        currentArchitecture.policySize(),
                        eps),
                block.alpha(),
                -1);
    }

    /**
     * Runs the attention policy head and gathers to 1858 logits.
     *
     * @param body token-major body activations
     * @return compressed policy logits
     */
    private float[] runPolicy(float[] body) {
        Architecture currentArchitecture = weights.architecture();
        PolicyHead head = weights.policyHead();
        int tokens = currentArchitecture.tokens();
        int embedding = currentArchitecture.embeddingSize();
        float[] flow = denseTokens(body, tokens, embedding, head.embedding());
        activate(flow, head.activation());
        int policyEmbedding = head.embedding().outDim();
        for (EncoderBlock block : head.encoders()) {
            flow = runEncoderBlock(flow, block, tokens, currentArchitecture.layerNormEpsilon());
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
        Architecture currentArchitecture = weights.architecture();
        ValueHead head = weights.valueHead();
        int tokens = currentArchitecture.tokens();
        int embedding = currentArchitecture.embeddingSize();
        float[] flow = denseTokens(body, tokens, embedding, head.embedding());
        activate(flow, head.activation());
        float[] flat = flow;
        float[] hidden = denseVector(flat, head.fc1());
        activate(hidden, head.activation());
        float[] logits = denseVector(hidden, head.fc2());
        return softmax(logits);
    }

    /**
     * Runs multi-head self-attention with optional smolgen bias.
     *
     * <p>
     * When the attention layer carries a smolgen block and a non-null
     * {@code smolgenW} is supplied, a per-head {@code [tokens, tokens]} bias
     * is computed from the input embeddings and added to the scaled QK
     * scores before the softmax.
     * </p>
     *
     * @param input token-major input
     * @param tokens token count
     * @param embedding input width
     * @param attention attention weights
     * @param smolgenW shared smolgen attention-bias projection; may be null
     * @param smolgenActivation smolgen-internal activation
     * @param layerNormEpsilon smolgen layer-norm epsilon
     * @return token-major attention output
     * @param blockIndex zero-based block index
     */
    private float[] attention(float[] input, int tokens, int embedding, Attention attention,
            float[] smolgenW, Activation smolgenActivation, float layerNormEpsilon, int blockIndex) {
        float[] q = denseTokens(input, tokens, embedding, attention.query());
        float[] k = denseTokens(input, tokens, embedding, attention.key());
        float[] v = denseTokens(input, tokens, embedding, attention.value());
        int dModel = attention.query().outDim();
        int heads = attention.heads();
        int depth = dModel / heads;
        float[] smolgenBias = null;
        if (attention.smolgen() != null && smolgenW != null) {
            smolgenBias = computeSmolgenBias(input, tokens, embedding, heads, attention.smolgen(),
                    smolgenW, smolgenActivation, layerNormEpsilon);
        }
        float[] combined = new float[tokens * dModel];
        float[] scores = new float[tokens];
        boolean captureHeads = sink != null && blockIndex >= 0;
        float[] perHeadAttention = captureHeads ? new float[heads * tokens * tokens] : null;
        for (int head = 0; head < heads; head++) {
            int headOffset = head * depth;
            float invScale = (float) (1.0 / Math.sqrt(depth));
            int biasHeadOffset = head * tokens * tokens;
            for (int queryToken = 0; queryToken < tokens; queryToken++) {
                int qBase = queryToken * dModel + headOffset;
                int biasRow = biasHeadOffset + queryToken * tokens;
                for (int keyToken = 0; keyToken < tokens; keyToken++) {
                    int kBase = keyToken * dModel + headOffset;
                    float sum = 0.0f;
                    for (int d = 0; d < depth; d++) {
                        sum += q[qBase + d] * k[kBase + d];
                    }
                    float score = sum * invScale;
                    if (smolgenBias != null) {
                        score += smolgenBias[biasRow + keyToken];
                    }
                    scores[keyToken] = score;
                }
                softmaxInPlace(scores);
                if (perHeadAttention != null) {
                    System.arraycopy(scores, 0, perHeadAttention, biasRow, tokens);
                }
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
        if (perHeadAttention != null) {
            sink.put(BLOCK_CAPTURE_PREFIX + blockIndex + ".attention.heads",
                    new int[] { heads, tokens, tokens }, perHeadAttention);
        }
        return denseTokens(combined, tokens, dModel, attention.out());
    }

    /**
     * Computes the per-head {@code [tokens, tokens]} smolgen bias.
     *
     * <p>
     * Flow: compress each token's embedding to {@code hiddenChannels},
     * flatten over tokens, dense -&gt; activation -&gt; LN -&gt; dense -&gt;
     * activation -&gt; LN -&gt; reshape to {@code [heads, perHeadDim]}, then
     * project each head row through the shared {@code smolgenW} (shape
     * {@code [perHeadDim, tokens*tokens]}). Output layout is flat
     * {@code [heads, tokens, tokens]}.
     * </p>
     *
     * @param input token-major input
     * @param tokens token count
     * @param embedding embedding width
     * @param heads attention head count
     * @param smolgen smolgen weights
     * @param smolgenW shared global smolgen projection
     * @param activation smolgen-internal activation
     * @param eps layer-norm epsilon
     * @return flat smolgen bias
     */
    private static float[] computeSmolgenBias(float[] input, int tokens, int embedding, int heads,
            Smolgen smolgen, float[] smolgenW, Activation activation, float eps) {
        int hiddenChannels = smolgen.compress().outDim();
        float[] compressed = denseTokens(input, tokens, embedding, smolgen.compress());
        if (compressed.length != tokens * hiddenChannels) {
            throw new IllegalStateException("smolgen compress output length unexpected");
        }
        float[] mid = denseVector(compressed, smolgen.dense1());
        activate(mid, activation);
        layerNormInPlace(mid, 1, mid.length, smolgen.ln1Gamma(), smolgen.ln1Beta(), eps);
        float[] gen = denseVector(mid, smolgen.dense2());
        activate(gen, activation);
        layerNormInPlace(gen, 1, gen.length, smolgen.ln2Gamma(), smolgen.ln2Beta(), eps);
        int perHead = gen.length / heads;
        int outSize = tokens * tokens;
        if (smolgenW.length != perHead * outSize) {
            throw new IllegalStateException("smolgenW shape mismatch: expected "
                    + (perHead * outSize) + " got " + smolgenW.length);
        }
        float[] bias = new float[heads * outSize];
        for (int h = 0; h < heads; h++) {
            int genBase = h * perHead;
            int outBase = h * outSize;
            for (int o = 0; o < outSize; o++) {
                float sum = 0.0f;
                int wRow = o * perHead;
                for (int d = 0; d < perHead; d++) {
                    sum += gen[genBase + d] * smolgenW[wRow + d];
                }
                bias[outBase + o] = sum;
            }
        }
        return bias;
    }

    /**
     * Runs a dense layer over token rows.
     *
     * @param input token-major input
     * @param tokens token count
     * @param inDim input width
     * @param layer network layer
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
     * @param layer network layer
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
     * @param layer network layer
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
     * @param values input values
     * @param activation activation vector
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
     * @param values input values
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
     * @param source source object
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
     * @param logits policy logits
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
     * @param policySize source policy size
     * @param parameterCount number of parameter
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
     * Prediction plus the canonical transform used during encoding.
     *
     * @param prediction network prediction
     * @param transform canonical transform bits
     */
    public record TransformedPrediction(Prediction prediction, int transform) {
    }

    /**
     * Network weight bundle.
     *
     * @param architecture architecture metadata
     * @param input input embedding stack (embedding + optional preproc/gates/FFN)
     * @param encoders body encoder blocks
     * @param smolgenW shared smolgen attention-bias projection; null when smolgen
     *        is disabled across all encoder blocks
     * @param policyHead attention policy head
     * @param valueHead WDL value head
     */
    public record Weights(
            Architecture architecture,
            InputStack input,
            List<EncoderBlock> encoders,
            float[] smolgenW,
            PolicyHead policyHead,
            ValueHead valueHead) {

        /**
         * Validates the bundle.
         * @param architecture network architecture
         * @param input input path or text
         * @param encoders encoder blocks
         * @param smolgenW Smolgen weights
         * @param policyHead policy-head weights
         * @param valueHead value-head weights
         */
        public Weights {
            if (architecture == null || input == null || encoders == null || policyHead == null
                    || valueHead == null) {
                throw new IllegalArgumentException("BT4 weights contain null component");
            }
            encoders = List.copyOf(encoders);
            smolgenW = smolgenW == null ? null : smolgenW.clone();
            if (input.embedding().outDim() != architecture.embeddingSize()) {
                throw new IllegalArgumentException("input embedding shape does not match architecture");
            }
            if (encoders.size() != architecture.encoderLayers()) {
                throw new IllegalArgumentException("encoder count does not match architecture");
            }
            if (architecture.hasSmolgen()) {
                int needed = architecture.smolgenPerHeadDim() * architecture.smolgenGlobalSize();
                if (smolgenW == null || smolgenW.length != needed) {
                    throw new IllegalArgumentException(
                            "smolgenW length mismatch: " + (smolgenW == null ? -1 : smolgenW.length) + " vs " + needed);
                }
            }
        }

        /**
         * Convenience constructor for callers that build simplified
         * BT4 weights with only an input embedding dense layer.
         *
         * @param architecture architecture metadata
         * @param inputEmbedding input embedding dense layer
         * @param encoders body encoder blocks
         * @param policyHead attention policy head
         * @param valueHead WDL value head
         */
        public Weights(Architecture architecture, Dense inputEmbedding, List<EncoderBlock> encoders,
                PolicyHead policyHead, ValueHead valueHead) {
            this(architecture,
                    new InputStack(null, inputEmbedding, null, null, null, null, null, null, null),
                    encoders,
                    null,
                    policyHead,
                    valueHead);
        }

        /**
         * Returns the input embedding dense layer for callers that only need
         * the embedding projection (e.g. compact code paths or shape inspection).
         *
         * @return embedding dense
         */
        public Dense inputEmbedding() {
            return input.embedding();
        }

        /**
         * Returns total parameter count.
         *
         * @return parameter count
         */
        public long parameterCount() {
            long total = input.parameterCount() + policyHead.parameterCount() + valueHead.parameterCount();
            for (EncoderBlock encoder : encoders) {
                total += encoder.parameterCount();
            }
            if (smolgenW != null) {
                total += smolgenW.length;
            }
            return total;
        }
    }

    /**
     * Two-layer feed-forward network with an activation between dense layers.
     *
     * @param dense1 first dense layer
     * @param dense2 second dense layer
     */
    public record Ffn(Dense dense1, Dense dense2) {

        /**
         * Validates the FFN shape.
         * @param dense1 first dense layer
         * @param dense2 second dense layer
         */
        public Ffn {
            if (dense1 == null || dense2 == null) {
                throw new IllegalArgumentException("FFN dense layer == null");
            }
            if (dense1.outDim() != dense2.inDim()) {
                throw new IllegalArgumentException("FFN inner dimensions mismatch");
            }
        }

        /**
         * Returns parameter count.
         *
         * @return parameter count
         */
        public long parameterCount() {
            return dense1.parameterCount() + dense2.parameterCount();
        }
    }

    /**
     * Per-attention smolgen bias generator.
     *
     * <p>
     * Smolgen reads the encoder-block input ({@code [tokens, embedding]}),
     * compresses each token to {@code hiddenChannels}, flattens to a single
     * block-shared vector, passes it through two dense layers with layer
     * normalization and an activation between, and finally reshapes the
     * result to {@code [heads, perHeadDim]}. The shared global
     * {@code smolgenW} projects each head's {@code perHeadDim} vector to
     * the full {@code tokens * tokens} attention bias.
     * </p>
     *
     * @param compress per-token compression dense ({@code embedding} to
     *        {@code hiddenChannels}); has no bias by LC0 convention
     * @param dense1 first dense ({@code tokens * hiddenChannels} to
     *        {@code hiddenSize})
     * @param ln1Gamma first layer-norm scale ({@code hiddenSize})
     * @param ln1Beta first layer-norm bias ({@code hiddenSize})
     * @param dense2 second dense ({@code hiddenSize} to
     *        {@code heads * perHeadDim})
     * @param ln2Gamma second layer-norm scale ({@code heads * perHeadDim})
     * @param ln2Beta second layer-norm bias ({@code heads * perHeadDim})
     */
    public record Smolgen(
            Dense compress,
            Dense dense1,
            float[] ln1Gamma,
            float[] ln1Beta,
            Dense dense2,
            float[] ln2Gamma,
            float[] ln2Beta) {

        /**
         * Validates smolgen shape.
         * @param compress compression projection
         * @param dense1 first dense layer
         * @param ln1Gamma source ln1 gamma
         * @param ln1Beta source ln1 beta
         * @param dense2 second dense layer
         * @param ln2Gamma source ln2 gamma
         * @param ln2Beta source ln2 beta
         */
        public Smolgen {
            if (compress == null || dense1 == null || dense2 == null) {
                throw new IllegalArgumentException("smolgen dense layer == null");
            }
            ln1Gamma = copy(ln1Gamma, "ln1Gamma");
            ln1Beta = copy(ln1Beta, "ln1Beta");
            ln2Gamma = copy(ln2Gamma, "ln2Gamma");
            ln2Beta = copy(ln2Beta, "ln2Beta");
            if (ln1Gamma.length != dense1.outDim() || ln1Beta.length != dense1.outDim()) {
                throw new IllegalArgumentException("smolgen ln1 length mismatch");
            }
            if (ln2Gamma.length != dense2.outDim() || ln2Beta.length != dense2.outDim()) {
                throw new IllegalArgumentException("smolgen ln2 length mismatch");
            }
        }

        /**
         * Returns parameter count.
         *
         * @return parameter count
         */
        public long parameterCount() {
            return compress.parameterCount() + dense1.parameterCount() + dense2.parameterCount()
                    + ln1Gamma.length + ln1Beta.length + ln2Gamma.length + ln2Beta.length;
        }
    }

    /**
     * BT4 input embedding stack.
     *
     * <p>
     * Models the full LC0 attention-body input pipeline. Components after
     * {@code embedding} are optional: compact simplified BT4 nets supply only
     * {@code embedding} and leave all other fields null.
     * </p>
     *
     * @param preproc dense applied to the flattened first-K input channels
     *        of all tokens (PE_DENSE only)
     * @param embedding main per-token dense projection to the body width
     * @param embLnGamma layer-norm gamma after embedding activation
     * @param embLnBeta layer-norm beta after embedding activation
     * @param multGate per-square per-channel multiplicative gate ({@code [tokens, embedding]})
     * @param addGate per-square per-channel additive gate ({@code [tokens, embedding]})
     * @param embFfn optional FFN after the gates
     * @param embFfnLnGamma layer-norm gamma after the embedding FFN
     * @param embFfnLnBeta layer-norm beta after the embedding FFN
     */
    public record InputStack(
            Dense preproc,
            Dense embedding,
            float[] embLnGamma,
            float[] embLnBeta,
            float[] multGate,
            float[] addGate,
            Ffn embFfn,
            float[] embFfnLnGamma,
            float[] embFfnLnBeta) {

        /**
         * Validates the stack.
         * @param preproc preprocessing weights
         * @param embedding embedding weights
         * @param embLnGamma source emb ln gamma
         * @param embLnBeta source emb ln beta
         * @param multGate source mult gate
         * @param addGate source add gate
         * @param embFfn source emb ffn
         * @param embFfnLnGamma source emb ffn ln gamma
         * @param embFfnLnBeta source emb ffn ln beta
         */
        public InputStack {
            if (embedding == null) {
                throw new IllegalArgumentException("input embedding == null");
            }
            embLnGamma = embLnGamma == null ? null : embLnGamma.clone();
            embLnBeta = embLnBeta == null ? null : embLnBeta.clone();
            multGate = multGate == null ? null : multGate.clone();
            addGate = addGate == null ? null : addGate.clone();
            embFfnLnGamma = embFfnLnGamma == null ? null : embFfnLnGamma.clone();
            embFfnLnBeta = embFfnLnBeta == null ? null : embFfnLnBeta.clone();
            int e = embedding.outDim();
            if (embLnGamma != null && (embLnGamma.length != e || embLnBeta == null || embLnBeta.length != e)) {
                throw new IllegalArgumentException("embedding LN length mismatch");
            }
            if (multGate != null && multGate.length % e != 0) {
                throw new IllegalArgumentException("multGate length not divisible by embedding");
            }
            if (addGate != null && addGate.length % e != 0) {
                throw new IllegalArgumentException("addGate length not divisible by embedding");
            }
            if (embFfn != null) {
                if (embFfn.dense1().inDim() != e || embFfn.dense2().outDim() != e) {
                    throw new IllegalArgumentException("embedding FFN shape mismatch");
                }
                if (embFfnLnGamma == null || embFfnLnGamma.length != e
                        || embFfnLnBeta == null || embFfnLnBeta.length != e) {
                    throw new IllegalArgumentException("embedding FFN LN missing or wrong size");
                }
            }
        }

        /**
         * Returns parameter count.
         *
         * @return parameter count
         */
        public long parameterCount() {
            long total = embedding.parameterCount();
            if (preproc != null) {
                total += preproc.parameterCount();
            }
            if (embLnGamma != null) {
                total += embLnGamma.length + embLnBeta.length;
            }
            if (multGate != null) {
                total += multGate.length;
            }
            if (addGate != null) {
                total += addGate.length;
            }
            if (embFfn != null) {
                total += embFfn.parameterCount() + embFfnLnGamma.length + embFfnLnBeta.length;
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
         * @param inDim input dimension
         * @param outDim output dimension
         * @param weights network weights
         * @param bias bias vector
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
     * @param smolgen optional smolgen bias generator; null when disabled
     */
    public record Attention(int heads, Dense query, Dense key, Dense value, Dense out, Smolgen smolgen) {

        /**
         * Validates attention dimensions.
         * @param heads attention head count
         * @param query query vector or text
         * @param key lookup key
         * @param value value to use
         * @param out destination stream or buffer
         * @param smolgen Smolgen state
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
            if (smolgen != null && smolgen.dense2().outDim() % heads != 0) {
                throw new IllegalArgumentException("smolgen final width must be divisible by heads");
            }
        }

        /**
         * Convenience constructor for attention without smolgen.
         *
         * @param heads attention head count
         * @param query query projection
         * @param key key projection
         * @param value value projection
         * @param out output projection
         */
        public Attention(int heads, Dense query, Dense key, Dense value, Dense out) {
            this(heads, query, key, value, out, null);
        }

        /**
         * Returns parameter count.
         *
         * @return parameter count
         */
        public long parameterCount() {
            long total = query.parameterCount() + key.parameterCount()
                    + value.parameterCount() + out.parameterCount();
            if (smolgen != null) {
                total += smolgen.parameterCount();
            }
            return total;
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
         * @param attention attention weights
         * @param ffnIn FFN input weights
         * @param ffnOut FFN output weights
         * @param ln1Gamma source ln1 gamma
         * @param ln1Beta source ln1 beta
         * @param ln2Gamma source ln2 gamma
         * @param ln2Beta source ln2 beta
         * @param activation activation function
         * @param alpha alpha search bound
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
         * @param embedding embedding weights
         * @param encoders encoder blocks
         * @param query query vector or text
         * @param key lookup key
         * @param promotionWeights source promotion weights
         * @param activation activation function
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
         * @param embedding embedding weights
         * @param fc1 first fully connected layer
         * @param fc2 second fully connected layer
         * @param activation activation function
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
        MISH,

        /**
         * Swish (SiLU) activation, used by BT4 smolgen blocks.
         */
        SWISH,

        /**
         * Hyperbolic tangent activation.
         */
        TANH;

        /**
         * Applies activation to one value.
         *
         * @param x x-coordinate
         * @return activated value
         */
        float apply(float x) {
            return switch (this) {
                case NONE -> x;
                case RELU -> x > 0.0f ? x : 0.0f;
                case MISH -> x * (float) Math.tanh(softplus(x));
                case SWISH -> x / (1.0f + (float) Math.exp(-x));
                case TANH -> (float) Math.tanh(x);
            };
        }

        /**
         * Numerically stable softplus.
         *
         * @param x x-coordinate
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
     * @param source source object
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
