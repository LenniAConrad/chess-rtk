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
        float layerNormEpsilon) {

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
     * Default BT4 architecture descriptor.
     */
    public static final Architecture BT4_1024X15X32H = new Architecture(
            "lc0-bt4-1024x15x32h",
            InputFormat.BT4_CANONICAL_112,
            InputEmbedding.PE_MAP,
            INPUT_CHANNELS,
            BOARD_TOKENS,
            1024,
            15,
            32,
            ATTENTION_POLICY_SIZE,
            1.0e-6f);

    /**
     * Validates architecture metadata.
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
        if (inputChannels <= 0 || tokens <= 0 || embeddingSize <= 0 || attentionHeads <= 0 || policySize <= 0) {
            throw new IllegalArgumentException("architecture dimensions must be positive");
        }
        if (encoderLayers < 0) {
            throw new IllegalArgumentException("encoderLayers must be non-negative");
        }
        if (embeddingSize % attentionHeads != 0) {
            throw new IllegalArgumentException("embeddingSize must be divisible by attentionHeads");
        }
        if (layerNormEpsilon <= 0.0f) {
            throw new IllegalArgumentException("layerNormEpsilon must be positive");
        }
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
         * Append a 64-way square one-hot vector to each token. The following input
         * dense layer learns the positional embedding.
         */
        PE_MAP;

        /**
         * Returns the per-token input width after this embedding strategy.
         *
         * @param inputChannels base LC0 input-channel count
         * @param tokens token count
         * @return per-token width
         */
        int projectedWidth(int inputChannels, int tokens) {
            return this == PE_MAP ? inputChannels + tokens : inputChannels;
        }
    }
}
