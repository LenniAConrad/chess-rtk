/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package chess.nn.lc0.bt4;

/**
 * Shape metadata for LCZero BT4-style transformer networks.
 *
 * <p>
 * The standard public BT4 family is commonly identified as
 * {@code 1024x15x32h}: 1024 model channels, 15 encoder layers, and 32
 * attention heads over 64 board-square tokens.
 * </p>
 *
 * <p>
 * BT4 nets that originate from LC0 also carry an extended input embedding
 * stack and a per-attention smolgen bias generator. Architecture records
 * capture both simplified and extended (LC0-faithful)
 * configurations through a set of feature flags and the dimensions that
 * back them.
 * </p>
 *
 * @param name architecture identifier
 * @param inputFormat LC0 input-plane format
 * @param inputEmbedding input embedding strategy
 * @param inputChannels number of LC0 board feature planes
 * @param tokens number of board tokens
 * @param embeddingSize transformer model width
 * @param encoderLayers encoder layer count
 * @param attentionHeads attention head count
 * @param policySize compressed LC0 attention-policy size
 * @param layerNormEpsilon layer-normalization epsilon
 * @param ffnHiddenSize encoder-block FFN hidden width
 * @param smolgenHiddenChannels per-token smolgen compression width (e.g. 32)
 * @param smolgenHiddenSize block-shared smolgen mid width (e.g. 256)
 * @param smolgenPerHeadDim per-head smolgen final width (e.g. 256), total = heads * this
 * @param smolgenGlobalSize attention-bias size produced by smolgen (tokens*tokens)
 * @param defaultActivation default activation for body MISH/RELU/NONE
 * @param smolgenActivation smolgen-internal activation
 * @param ffnActivation FFN activation
 * @param hasInputPreproc whether per-square preproc dense is present
 * @param hasInputEmbFfn whether an FFN follows the input embedding
 * @param hasInputGates whether mult/add gates follow the input embedding
 * @param hasSmolgen whether each attention layer adds a smolgen bias
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public record Architecture(
        String name,
        InputFormat inputFormat,
        InputEmbedding inputEmbedding,
        int inputChannels,
        int tokens,
        int embeddingSize,
        int encoderLayers,
        int attentionHeads,
        int policySize,
        float layerNormEpsilon,
        int ffnHiddenSize,
        int smolgenHiddenChannels,
        int smolgenHiddenSize,
        int smolgenPerHeadDim,
        int smolgenGlobalSize,
        Network.Activation defaultActivation,
        Network.Activation smolgenActivation,
        Network.Activation ffnActivation,
        boolean hasInputPreproc,
        boolean hasInputEmbFfn,
        boolean hasInputGates,
        boolean hasSmolgen) {

    /**
     * Number of board-square tokens in LC0 attention-body networks.
     */
    public static final int BOARD_TOKENS = 64;

    /**
     * Number of LC0 input planes.
     */
    public static final int INPUT_CHANNELS = 112;

    /**
     * Compressed LC0 attention-policy output size.
     */
    public static final int ATTENTION_POLICY_SIZE = 1858;

    /**
     * Default BT4 architecture descriptor for the full 1024x15x32h LC0 net.
     */
    public static final Architecture BT4_1024X15X32H = new Architecture(
            "lc0-bt4-1024x15x32h",
            InputFormat.BT4_CANONICAL_112,
            InputEmbedding.PE_DENSE,
            INPUT_CHANNELS,
            BOARD_TOKENS,
            1024,
            15,
            32,
            ATTENTION_POLICY_SIZE,
            1.0e-3f,
            1536,
            32,
            256,
            256,
            BOARD_TOKENS * BOARD_TOKENS,
            Network.Activation.MISH,
            Network.Activation.SWISH,
            Network.Activation.MISH,
            true,
            true,
            true,
            true);

    /**
     * Validates architecture metadata.
     * @param name name value
     * @param inputFormat input encoding format
     * @param inputEmbedding input embedding type
     * @param inputChannels number of input channels
     * @param tokens token values
     * @param embeddingSize embedding size
     * @param encoderLayers number of encoder layers
     * @param attentionHeads attention head count
     * @param policySize policy output size
     * @param layerNormEpsilon layer normalization epsilon
     * @param ffnHiddenSize feed-forward hidden size
     * @param smolgenHiddenChannels Smolgen hidden channel count
     * @param smolgenHiddenSize Smolgen hidden size
     * @param smolgenPerHeadDim Smolgen per-head dimension
     * @param smolgenGlobalSize Smolgen global size
     * @param defaultActivation default activation function
     * @param smolgenActivation Smolgen activation function
     * @param ffnActivation feed-forward activation function
     * @param hasInputPreproc true when input preprocessing is enabled
     * @param hasInputEmbFfn true when the input embedding feed-forward network is enabled
     * @param hasInputGates true when input gates are enabled
     * @param hasSmolgen true when Smolgen is enabled
     */
    public Architecture {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is blank");
        }
        if (inputFormat == null) {
            throw new IllegalArgumentException("inputFormat == null");
        }
        if (inputEmbedding == null) {
            throw new IllegalArgumentException("inputEmbedding == null");
        }
        if (defaultActivation == null || smolgenActivation == null || ffnActivation == null) {
            throw new IllegalArgumentException("activation enum == null");
        }
        if (inputChannels <= 0 || tokens <= 0 || embeddingSize <= 0 || attentionHeads <= 0 || policySize <= 0) {
            throw new IllegalArgumentException("architecture dimensions must be positive");
        }
        if (encoderLayers < 0) {
            throw new IllegalArgumentException("encoderLayers must be non-negative");
        }
        if (ffnHiddenSize <= 0) {
            throw new IllegalArgumentException("ffnHiddenSize must be positive");
        }
        if (embeddingSize % attentionHeads != 0) {
            throw new IllegalArgumentException("embeddingSize must be divisible by attentionHeads");
        }
        if (layerNormEpsilon <= 0.0f) {
            throw new IllegalArgumentException("layerNormEpsilon must be positive");
        }
        if (hasSmolgen && (smolgenHiddenChannels <= 0 || smolgenHiddenSize <= 0
                || smolgenPerHeadDim <= 0 || smolgenGlobalSize != tokens * tokens)) {
            throw new IllegalArgumentException("smolgen dimensions invalid");
        }
    }

    /**
     * Convenience constructor for simplified BT4 architectures that
     * predate the extended LC0 stack.
     *
     * @param name architecture identifier
     * @param inputFormat LC0 input-plane format
     * @param inputEmbedding input embedding strategy
     * @param inputChannels LC0 input planes
     * @param tokens board-token count
     * @param embeddingSize transformer model width
     * @param encoderLayers encoder layer count
     * @param attentionHeads attention head count
     * @param policySize compressed LC0 attention policy size
     * @param layerNormEpsilon layer-normalization epsilon
     * @return simplified architecture with all extension flags disabled
     */
    public static Architecture simplified(String name, InputFormat inputFormat, InputEmbedding inputEmbedding,
            int inputChannels, int tokens, int embeddingSize, int encoderLayers,
            int attentionHeads, int policySize, float layerNormEpsilon) {
        return new Architecture(
                name,
                inputFormat,
                inputEmbedding,
                inputChannels,
                tokens,
                embeddingSize,
                encoderLayers,
                attentionHeads,
                policySize,
                layerNormEpsilon,
                Math.max(1, embeddingSize),
                1,
                1,
                1,
                tokens * tokens,
                Network.Activation.MISH,
                Network.Activation.SWISH,
                Network.Activation.MISH,
                false,
                false,
                false,
                false);
    }

    /**
     * Returns the token feature width consumed by the input projection.
     *
     * @return projected input width
     */
    public int projectedInputWidth() {
        return inputEmbedding.projectedWidth(inputChannels, tokens);
    }

    /**
     * Input embedding strategy used before the transformer stack.
     */
    public enum InputEmbedding {

        /**
         * Use the 112 LC0 planes directly as per-square token features.
         */
        NONE,

        /**
         * Append a 64-way square one-hot vector to each token. The following
         * input dense layer learns the positional embedding.
         */
        PE_MAP,

        /**
         * BT4-style "dense" positional embedding. The first 12 input channels
         * are routed through a learned per-square dense ({@code ip_emb_preproc})
         * and then concatenated to the per-token features before the main
         * embedding projection.
         */
        PE_DENSE;

        /**
         * Returns the per-token input width after this embedding strategy.
         *
         * <p>
         * For {@link #PE_DENSE} the projected width depends on the preproc
         * output size, which is carried separately on the input stack record
         * rather than inferred from the input embedding alone. This method
         * therefore returns the channel-only width for {@code PE_DENSE} and
         * leaves the dense concatenation to the {@code InputStack}.
         * </p>
         *
         * @param inputChannels base LC0 input-channel count
         * @param tokens token count
         * @return per-token width
         */
        int projectedWidth(int inputChannels, int tokens) {
            return switch (this) {
                case NONE, PE_DENSE -> inputChannels;
                case PE_MAP -> inputChannels + tokens;
            };
        }
    }
}
