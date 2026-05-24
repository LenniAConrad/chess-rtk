package chess.nn.t5;

/**
 * Immutable configuration for a T5 model export.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Config {

  /**
   * Human-readable export name carried from the source model.
   */
  public final String name;

  /**
   * Vocabulary size used for lookup tables and embedding layers.
   */
  public final int vocabSize;

  /**
   * Model width used across attention and projection layers.
   */
  public final int dModel;

  /**
   * Dimension of the query/key/value projections per head.
   */
  public final int dKv;

  /**
   * Hidden size of the feed-forward network.
   */
  public final int dFf;

  /**
   * Number of encoder layers.
   */
  public final int numLayers;

  /**
   * Number of decoder layers.
   */
  public final int numDecoderLayers;

  /**
   * Number of attention heads.
   */
  public final int numHeads;

  /**
   * Number of relative position buckets.
   */
  public final int relBuckets;

  /**
   * Maximum relative distance handled by the bias lookup.
   */
  public final int relMaxDistance;

  /**
   * Token id used for padding values.
   */
  public final int padId;

  /**
   * Token id used to mark the end of a sequence.
   */
  public final int eosId;

  /**
   * Initial token id provided to the decoder.
   */
  public final int decoderStartId;

  /**
   * Token id returned for unknown inputs.
   */
  public final int unkId;

  /**
   * Whether the FFN uses gated GELU variants.
   */
  public final boolean gatedGelu;

  /**
   * Epsilon used for layer normalization stability.
   */
  public final float layerNormEps;

  /**
   * Creates a configuration snapshot for the exported model.
   *
   * @param name human-readable model name
   * @param sizes model dimensions
   * @param layers layer configuration
   * @param relPos relative position configuration
   * @param tokens special token ids
   * @param gatedGelu whether the FFN uses gated GELU
   * @param layerNormEps layer norm epsilon
   */
  Config(String name, Sizes sizes, Layers layers, RelPos relPos, SpecialTokens tokens, boolean gatedGelu, float layerNormEps) {
    this.name = name;
    this.vocabSize = sizes.vocabSize;
    this.dModel = sizes.dModel;
    this.dKv = sizes.dKv;
    this.dFf = sizes.dFf;
    this.numLayers = layers.numLayers;
    this.numDecoderLayers = layers.numDecoderLayers;
    this.numHeads = layers.numHeads;
    this.relBuckets = relPos.relBuckets;
    this.relMaxDistance = relPos.relMaxDistance;
    this.padId = tokens.padId;
    this.eosId = tokens.eosId;
    this.decoderStartId = tokens.decoderStartId;
    this.unkId = tokens.unkId;
    this.gatedGelu = gatedGelu;
    this.layerNormEps = layerNormEps;
  }

  /**
   * Captures the core tensor dimensions needed during inference.
   *
   * @since 2026
   * @author Lennart A. Conrad
   */
  static final class Sizes {

    /**
     * Vocabulary size of the model.
     */
    final int vocabSize;

    /**
     * Model hidden size.
     */
    final int dModel;

    /**
     * Query/key/value dimension per head.
     */
    final int dKv;

    /**
     * Feed-forward hidden dimension.
     */
    final int dFf;

    /**
     * Records the dimension sizes used across the model.
     *
     * @param vocabSize vocabulary size
     * @param dModel model width
     * @param dKv attention head dimension
     * @param dFf FFN hidden dimension
     */
    Sizes(int vocabSize, int dModel, int dKv, int dFf) {
      this.vocabSize = vocabSize;
      this.dModel = dModel;
      this.dKv = dKv;
      this.dFf = dFf;
    }
  }

  /**
   * Bundles the number of layers used in each stack.
   *
   * @since 2026
   * @author Lennart A. Conrad
   */
  static final class Layers {

    /**
     * Number of encoder and decoder layers.
     */
    final int numLayers;

    /**
     * Number of decoder-only layers.
     */
    final int numDecoderLayers;
    
    /**
     * Attention head count.
     */
    final int numHeads;

    /**
     * Captures layer counts needed for encoder/decoder assembly.
     *
     * @param numLayers encoder layers
     * @param numDecoderLayers decoder layers
     * @param numHeads heads per attention
     */
    Layers(int numLayers, int numDecoderLayers, int numHeads) {
      this.numLayers = numLayers;
      this.numDecoderLayers = numDecoderLayers;
      this.numHeads = numHeads;
    }
  }

  /**
   * Stores relative position bucket settings.
   *
   * @since 2026
   * @author Lennart A. Conrad
   */
  static final class RelPos {

    /**
     * Number of relative position buckets.
     */
    final int relBuckets;

    /**
     * Maximum distance supported before clamping.
     */
    final int relMaxDistance;

    /**
     * Stores relative position parameters.
     *
     * @param relBuckets number of buckets
     * @param relMaxDistance maximum relative distance
     */
    RelPos(int relBuckets, int relMaxDistance) {
      this.relBuckets = relBuckets;
      this.relMaxDistance = relMaxDistance;
    }
  }

  /**
   * Defines ids for reserved tokens in the export.
   *
   * @since 2026
   * @author Lennart A. Conrad
   */
  static final class SpecialTokens {

    /**
     * Padding token identifier.
     */
    final int padId;

    /**
     * End-of-sequence token identifier.
     */
    final int eosId;

    /**
     * Decoder start token identifier.
     */
    final int decoderStartId;
    
    /**
     * Unknown token identifier used for fallback.
     */
    final int unkId;

    /**
     * Records identifiers for reserved tokens.
     *
     * @param padId padding token id
     * @param eosId end-of-sequence token id
     * @param decoderStartId decoder start token id
     * @param unkId unknown token id
     */
    SpecialTokens(int padId, int eosId, int decoderStartId, int unkId) {
      this.padId = padId;
      this.eosId = eosId;
      this.decoderStartId = decoderStartId;
      this.unkId = unkId;
    }
  }
}
