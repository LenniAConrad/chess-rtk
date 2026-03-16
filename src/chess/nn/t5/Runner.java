package chess.nn.t5;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Executes T5 forward passes and greedy decoding.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Runner implements AutoCloseable {
  
  /**
   * Loaded T5 model used for inference routine.
   */
  private final Model model;
  
  /**
   * Optional CUDA backend for accelerated inference.
   */
  private final chess.nn.t5.cuda.Backend cudaBackend;

  /**
   * Optional ROCm backend for accelerated inference.
   */
  private final chess.nn.t5.rocm.Backend rocmBackend;

  /**
   * Optional oneAPI backend for accelerated inference.
   */
  private final chess.nn.t5.oneapi.Backend oneapiBackend;

  /**
   * Minimum work size before parallel loops are enabled.
   */
  private static final int PARALLEL_THRESHOLD = 256;

  /**
   * Creates a runner for a loaded model.
   *
   * @param model loaded model
   */
  public Runner(Model model) {
    this.model = model;
    BackendChoice choice = backendChoice();
    if (choice == BackendChoice.CPU) {
      this.cudaBackend = null;
      this.rocmBackend = null;
      this.oneapiBackend = null;
    } else if (choice == BackendChoice.CUDA) {
      this.cudaBackend = chess.nn.t5.cuda.Backend.tryCreate(model);
      this.rocmBackend = null;
      this.oneapiBackend = null;
    } else if (choice == BackendChoice.ROCM) {
      this.cudaBackend = null;
      this.rocmBackend = chess.nn.t5.rocm.Backend.tryCreate(model);
      this.oneapiBackend = null;
    } else if (choice == BackendChoice.ONEAPI) {
      this.cudaBackend = null;
      this.rocmBackend = null;
      this.oneapiBackend = chess.nn.t5.oneapi.Backend.tryCreate(model);
    } else {
      chess.nn.t5.cuda.Backend cuda = chess.nn.t5.cuda.Backend.tryCreate(model);
      chess.nn.t5.rocm.Backend rocm = cuda == null ? chess.nn.t5.rocm.Backend.tryCreate(model) : null;
      chess.nn.t5.oneapi.Backend oneapi =
          (cuda == null && rocm == null) ? chess.nn.t5.oneapi.Backend.tryCreate(model) : null;
      this.cudaBackend = cuda;
      this.rocmBackend = rocm;
      this.oneapiBackend = oneapi;
    }
  }

  /**
   * Generates text from a prompt using greedy decoding.
   *
   * @param prompt input prompt
   * @param maxNewTokens maximum generated tokens
   * @return decoded output text
   */
  public String generate(String prompt, int maxNewTokens) {
    String backendOutput = generateWithBackends(prompt, maxNewTokens);
    if (backendOutput != null) {
      return backendOutput;
    }
    return generateCpu(prompt, maxNewTokens);
  }

  /**
   * Tries all configured native backends in priority order.
   *
   * @param prompt prompt to decode
   * @param maxNewTokens maximum generated token count
   * @return decoded output, or {@code null} when all backends fall back
   */
  private String generateWithBackends(String prompt, int maxNewTokens) {
    String output = tryGenerateCuda(prompt, maxNewTokens);
    if (output != null) {
      return output;
    }
    output = tryGenerateRocm(prompt, maxNewTokens);
    if (output != null) {
      return output;
    }
    return tryGenerateOneapi(prompt, maxNewTokens);
  }

  /**
   * Attempts generation using the CUDA backend.
   *
   * @param prompt prompt to decode
   * @param maxNewTokens maximum generated token count
   * @return decoded output, or {@code null} on failure/unavailable backend
   */
  private String tryGenerateCuda(String prompt, int maxNewTokens) {
    if (cudaBackend == null) {
      return null;
    }
    try {
      return generateCuda(prompt, maxNewTokens);
    } catch (UnsatisfiedLinkError | RuntimeException ignore) {
      // Fallback to CPU path on CUDA failures.
      return null;
    }
  }

  /**
   * Attempts generation using the ROCm backend.
   *
   * @param prompt prompt to decode
   * @param maxNewTokens maximum generated token count
   * @return decoded output, or {@code null} on failure/unavailable backend
   */
  private String tryGenerateRocm(String prompt, int maxNewTokens) {
    if (rocmBackend == null) {
      return null;
    }
    try {
      return generateRocm(prompt, maxNewTokens);
    } catch (UnsatisfiedLinkError | RuntimeException ignore) {
      // Fallback to CPU path on ROCm failures.
      return null;
    }
  }

  /**
   * Attempts generation using the oneAPI backend.
   *
   * @param prompt prompt to decode
   * @param maxNewTokens maximum generated token count
   * @return decoded output, or {@code null} on failure/unavailable backend
   */
  private String tryGenerateOneapi(String prompt, int maxNewTokens) {
    if (oneapiBackend == null) {
      return null;
    }
    try {
      return generateOneapi(prompt, maxNewTokens);
    } catch (UnsatisfiedLinkError | RuntimeException ignore) {
      // Fallback to CPU path on oneAPI failures.
      return null;
    }
  }

  /**
   * Executes greedy decoding on the CPU path.
   *
   * @param prompt prompt to decode
   * @param maxNewTokens maximum generated token count
   * @return decoded output text
   */
  private String generateCpu(String prompt, int maxNewTokens) {
    List<Integer> inputIds = model.tokenizer.encode(prompt);
    Tensor encoderHidden = runEncoder(inputIds);

    List<Integer> outputIds = new ArrayList<>();
    outputIds.add(model.config.decoderStartId);

    for (int step = 0; step < maxNewTokens; step++) {
      Tensor logits = runDecoder(outputIds, encoderHidden);
      int nextId = argmaxLast(logits);
      if (nextId == model.config.eosId) {
        break;
      }
      outputIds.add(nextId);
    }

    return model.tokenizer.decode(outputIds.subList(1, outputIds.size()));
  }

  /**
   * Generates token ids from a prompt using greedy decoding.
   *
   * @param prompt input prompt
   * @param maxNewTokens maximum generated tokens
   * @return generated token ids (including decoder start)
   */
  public List<Integer> generateIds(String prompt, int maxNewTokens) {
    List<Integer> backendOutput = generateIdsWithBackends(prompt, maxNewTokens);
    if (!backendOutput.isEmpty()) {
      return backendOutput;
    }
    return generateCpuIds(prompt, maxNewTokens);
  }

  /**
   * Tries all configured native backends for id generation.
   *
   * @param prompt prompt to decode
   * @param maxNewTokens maximum generated token count
   * @return generated ids, or an empty list when all backends fall back
   */
  private List<Integer> generateIdsWithBackends(String prompt, int maxNewTokens) {
    List<Integer> output = tryGenerateCudaIds(prompt, maxNewTokens);
    if (!output.isEmpty()) {
      return output;
    }
    output = tryGenerateRocmIds(prompt, maxNewTokens);
    if (!output.isEmpty()) {
      return output;
    }
    return tryGenerateOneapiIds(prompt, maxNewTokens);
  }

  /**
   * Attempts id generation via CUDA.
   *
   * @param prompt prompt to decode
   * @param maxNewTokens maximum generated token count
   * @return generated ids, or empty when unavailable/failing
   */
  private List<Integer> tryGenerateCudaIds(String prompt, int maxNewTokens) {
    if (cudaBackend == null) {
      return List.of();
    }
    try {
      return generateCudaIds(prompt, maxNewTokens);
    } catch (UnsatisfiedLinkError | RuntimeException ignore) {
      // Fallback to CPU path on CUDA failures.
      return List.of();
    }
  }

  /**
   * Attempts id generation via ROCm.
   *
   * @param prompt prompt to decode
   * @param maxNewTokens maximum generated token count
   * @return generated ids, or empty when unavailable/failing
   */
  private List<Integer> tryGenerateRocmIds(String prompt, int maxNewTokens) {
    if (rocmBackend == null) {
      return List.of();
    }
    try {
      return generateRocmIds(prompt, maxNewTokens);
    } catch (UnsatisfiedLinkError | RuntimeException ignore) {
      // Fallback to CPU path on ROCm failures.
      return List.of();
    }
  }

  /**
   * Attempts id generation via oneAPI.
   *
   * @param prompt prompt to decode
   * @param maxNewTokens maximum generated token count
   * @return generated ids, or empty when unavailable/failing
   */
  private List<Integer> tryGenerateOneapiIds(String prompt, int maxNewTokens) {
    if (oneapiBackend == null) {
      return List.of();
    }
    try {
      return generateOneapiIds(prompt, maxNewTokens);
    } catch (UnsatisfiedLinkError | RuntimeException ignore) {
      // Fallback to CPU path on oneAPI failures.
      return List.of();
    }
  }

  /**
   * Executes greedy id generation on CPU.
   *
   * @param prompt prompt to decode
   * @param maxNewTokens maximum generated token count
   * @return generated ids including the decoder start id
   */
  private List<Integer> generateCpuIds(String prompt, int maxNewTokens) {
    List<Integer> inputIds = model.tokenizer.encode(prompt);
    Tensor encoderHidden = runEncoder(inputIds);

    List<Integer> outputIds = new ArrayList<>();
    outputIds.add(model.config.decoderStartId);

    for (int step = 0; step < maxNewTokens; step++) {
      Tensor logits = runDecoder(outputIds, encoderHidden);
      int nextId = argmaxLast(logits);
      if (nextId == model.config.eosId) {
        break;
      }
      outputIds.add(nextId);
    }
    return outputIds;
  }

  /**
   * Runs a single decoder step for the current output ids.
   *
   * @param outputIds current decoder ids
   * @param encoderHidden encoder hidden states
   * @return logits tensor
   */
  public Tensor decodeOnce(List<Integer> outputIds, Tensor encoderHidden) {
    return runDecoder(outputIds, encoderHidden);
  }

  /**
   * Releases any native resources held by the runner.
   */
  @Override
  public void close() {
    if (cudaBackend != null) {
      cudaBackend.close();
    }
    if (rocmBackend != null) {
      rocmBackend.close();
    }
    if (oneapiBackend != null) {
      oneapiBackend.close();
    }
  }

  /**
   * Supported backend selection modes for T5 inference.
   *
   * @since 2026
   * @author Lennart A. Conrad
   */
  private enum BackendChoice {
    /**
     * Probe accelerated backends in fallback order.
     */
    AUTO,
    /**
     * Force pure CPU inference.
     */
    CPU,
    /**
     * Force CUDA backend.
     */
    CUDA,
    /**
     * Force ROCm backend.
     */
    ROCM,
    /**
     * Force oneAPI backend.
     */
    ONEAPI
  }

  /**
   * Resolves the configured backend preference from JVM properties.
   *
   * @return resolved backend selection mode
   */
  private static BackendChoice backendChoice() {
    String value = System.getProperty("crtk.t5.backend", "auto").trim().toLowerCase();
    if ("cpu".equals(value)) {
      return BackendChoice.CPU;
    }
    if ("cuda".equals(value)) {
      return BackendChoice.CUDA;
    }
    if ("rocm".equals(value) || "amd".equals(value) || "hip".equals(value)) {
      return BackendChoice.ROCM;
    }
    if ("oneapi".equals(value) || "intel".equals(value)) {
      return BackendChoice.ONEAPI;
    }
    return BackendChoice.AUTO;
  }

  /**
   * Determines whether a loop should run in parallel for the given work size.
   *
   * @param workItems number of independent items to process
   * @return {@code true} when parallel execution is preferred
   */
  private static boolean useParallel(int workItems) {
    int cores = Runtime.getRuntime().availableProcessors();
    return cores > 1 && workItems >= PARALLEL_THRESHOLD;
  }

  /**
   * Generates decoded text using CUDA-native execution.
   *
   * @param prompt prompt to decode
   * @param maxNewTokens maximum generated token count
   * @return decoded text, or {@code null} when backend returned no output
   */
  private String generateCuda(String prompt, int maxNewTokens) {
    List<Integer> inputIds = model.tokenizer.encode(prompt);
    ensureEncoderEos(inputIds);
    int[] ids = toIntArray(inputIds);
    int[] outputIds = cudaBackend.generateIds(ids, maxNewTokens);
    if (outputIds == null || outputIds.length == 0) {
      return null;
    }
    return model.tokenizer.decode(toList(outputIds, 1));
  }

  /**
   * Generates token ids using CUDA-native execution.
   *
   * @param prompt prompt to decode
   * @param maxNewTokens maximum generated token count
   * @return generated ids, or empty when backend returned no output
   */
  private List<Integer> generateCudaIds(String prompt, int maxNewTokens) {
    List<Integer> inputIds = model.tokenizer.encode(prompt);
    ensureEncoderEos(inputIds);
    int[] ids = toIntArray(inputIds);
    int[] outputIds = cudaBackend.generateIds(ids, maxNewTokens);
    if (outputIds == null || outputIds.length == 0) {
      return List.of();
    }
    return toList(outputIds, 0);
  }

  /**
   * Generates decoded text using ROCm-native execution.
   *
   * @param prompt prompt to decode
   * @param maxNewTokens maximum generated token count
   * @return decoded text, or {@code null} when backend returned no output
   */
  private String generateRocm(String prompt, int maxNewTokens) {
    List<Integer> inputIds = model.tokenizer.encode(prompt);
    ensureEncoderEos(inputIds);
    int[] ids = toIntArray(inputIds);
    int[] outputIds = rocmBackend.generateIds(ids, maxNewTokens);
    if (outputIds == null || outputIds.length == 0) {
      return null;
    }
    return model.tokenizer.decode(toList(outputIds, 1));
  }

  /**
   * Generates token ids using ROCm-native execution.
   *
   * @param prompt prompt to decode
   * @param maxNewTokens maximum generated token count
   * @return generated ids, or empty when backend returned no output
   */
  private List<Integer> generateRocmIds(String prompt, int maxNewTokens) {
    List<Integer> inputIds = model.tokenizer.encode(prompt);
    ensureEncoderEos(inputIds);
    int[] ids = toIntArray(inputIds);
    int[] outputIds = rocmBackend.generateIds(ids, maxNewTokens);
    if (outputIds == null || outputIds.length == 0) {
      return List.of();
    }
    return toList(outputIds, 0);
  }

  /**
   * Generates decoded text using oneAPI-native execution.
   *
   * @param prompt prompt to decode
   * @param maxNewTokens maximum generated token count
   * @return decoded text, or {@code null} when backend returned no output
   */
  private String generateOneapi(String prompt, int maxNewTokens) {
    List<Integer> inputIds = model.tokenizer.encode(prompt);
    ensureEncoderEos(inputIds);
    int[] ids = toIntArray(inputIds);
    int[] outputIds = oneapiBackend.generateIds(ids, maxNewTokens);
    if (outputIds == null || outputIds.length == 0) {
      return null;
    }
    return model.tokenizer.decode(toList(outputIds, 1));
  }

  /**
   * Generates token ids using oneAPI-native execution.
   *
   * @param prompt prompt to decode
   * @param maxNewTokens maximum generated token count
   * @return generated ids, or empty when backend returned no output
   */
  private List<Integer> generateOneapiIds(String prompt, int maxNewTokens) {
    List<Integer> inputIds = model.tokenizer.encode(prompt);
    ensureEncoderEos(inputIds);
    int[] ids = toIntArray(inputIds);
    int[] outputIds = oneapiBackend.generateIds(ids, maxNewTokens);
    if (outputIds == null || outputIds.length == 0) {
      return List.of();
    }
    return toList(outputIds, 0);
  }

  /**
   * Ensures the encoder input ends with EOS.
   *
   * @param ids mutable id list to normalize
   */
  private void ensureEncoderEos(List<Integer> ids) {
    if (ids.isEmpty() || ids.get(ids.size() - 1) != model.config.eosId) {
      ids.add(model.config.eosId);
    }
  }

  /**
   * Converts a boxed id list into a primitive array.
   *
   * @param ids source token ids
   * @return primitive int array copy
   */
  private static int[] toIntArray(List<Integer> ids) {
    int[] out = new int[ids.size()];
    for (int i = 0; i < ids.size(); i++) {
      out[i] = ids.get(i);
    }
    return out;
  }

  /**
   * Converts a primitive array segment to a boxed list.
   *
   * @param ids source id buffer
   * @param offset starting index to include
   * @return boxed list view copy from {@code offset} to the end
   */
  private static List<Integer> toList(int[] ids, int offset) {
    List<Integer> out = new ArrayList<>();
    for (int i = offset; i < ids.length; i++) {
      out.add(ids[i]);
    }
    return out;
  }

  /**
   * Encodes a prompt into encoder hidden states.
   *
   * @param prompt input prompt
   * @return encoder hidden states
   */
  public Tensor encodePrompt(String prompt) {
    return runEncoder(model.tokenizer.encode(prompt));
  }

  /**
   * Runs the encoder stack and returns its hidden states.
   *
   * @param inputIds token ids for the source prompt
   * @return encoder hidden tensor
   */
  private Tensor runEncoder(List<Integer> inputIds) {
    List<Integer> ids = new ArrayList<>(inputIds);
    ensureEncoderEos(ids);
    Tensor x = model.embed(ids);
    Tensor relBias = model.get("encoder.block.0.layer.0.SelfAttention.relative_attention_bias.weight");
    for (int layer = 0; layer < model.config.numLayers; layer++) {
      encoderBlock(x, layer, relBias);
    }
    Tensor ln = model.get("encoder.final_layer_norm.weight");
    MathOps.layerNormInPlace(x.data, x.shape[0], x.shape[1], ln.data, model.config.layerNormEps);
    return x;
  }

  /**
   * Runs decoder layers to produce logits for the next token.
   *
   * @param outputIds current decoder ids
   * @param encoderHidden encoder states to attend to
   * @return logits tensor
   */
  private Tensor runDecoder(List<Integer> outputIds, Tensor encoderHidden) {
    Tensor x = model.embed(outputIds);
    Tensor relBias = model.get("decoder.block.0.layer.0.SelfAttention.relative_attention_bias.weight");
    for (int layer = 0; layer < model.config.numDecoderLayers; layer++) {
      decoderBlock(x, layer, encoderHidden, relBias);
    }
    Tensor ln = model.get("decoder.final_layer_norm.weight");
    MathOps.layerNormInPlace(x.data, x.shape[0], x.shape[1], ln.data, model.config.layerNormEps);
    Tensor lmHead = model.get("lm_head.weight");
    return MathOps.matmul(x, lmHead);
  }

  /**
   * Applies one encoder transformer block to the activation tensor.
   *
   * @param x current activations
   * @param layer layer index
   * @param relBias bias tensor for relative attention
   * @return updated activations
   */
  private Tensor encoderBlock(Tensor x, int layer, Tensor relBias) {
    String prefix = "encoder.block." + layer + ".";
    Tensor ln1 = model.get(prefix + "layer.0.layer_norm.weight");
    Tensor q = model.get(prefix + "layer.0.SelfAttention.q.weight");
    Tensor k = model.get(prefix + "layer.0.SelfAttention.k.weight");
    Tensor v = model.get(prefix + "layer.0.SelfAttention.v.weight");
    Tensor o = model.get(prefix + "layer.0.SelfAttention.o.weight");

    Tensor xNorm = x.copy();
    MathOps.layerNormInPlace(xNorm.data, xNorm.shape[0], xNorm.shape[1], ln1.data, model.config.layerNormEps);
    Tensor attn = selfAttention(xNorm, selfAttentionSpec(q, k, v, o, relBias, false, true));
    MathOps.addInPlace(x.data, attn.data);

    Tensor ln2 = model.get(prefix + "layer.1.layer_norm.weight");
    Tensor wi0 = model.get(prefix + "layer.1.DenseReluDense.wi_0.weight");
    Tensor wi1 = model.get(prefix + "layer.1.DenseReluDense.wi_1.weight");
    Tensor wo = model.get(prefix + "layer.1.DenseReluDense.wo.weight");
    Tensor xNorm2 = x.copy();
    MathOps.layerNormInPlace(xNorm2.data, xNorm2.shape[0], xNorm2.shape[1], ln2.data, model.config.layerNormEps);
    Tensor ff = feedForward(xNorm2, wi0, wi1, wo);
    MathOps.addInPlace(x.data, ff.data);

    return x;
  }

  /**
   * Applies one decoder transformer block with encoder attention.
   *
   * @param x decoder activations
   * @param layer decoder layer index
   * @param encoderHidden encoder hidden states
   * @param relBias relative bias tensor
   * @return updated decoder activations
   */
  private Tensor decoderBlock(Tensor x, int layer, Tensor encoderHidden, Tensor relBias) {
    String prefix = "decoder.block." + layer + ".";
    Tensor ln1 = model.get(prefix + "layer.0.layer_norm.weight");
    Tensor q = model.get(prefix + "layer.0.SelfAttention.q.weight");
    Tensor k = model.get(prefix + "layer.0.SelfAttention.k.weight");
    Tensor v = model.get(prefix + "layer.0.SelfAttention.v.weight");
    Tensor o = model.get(prefix + "layer.0.SelfAttention.o.weight");

    Tensor xNorm = x.copy();
    MathOps.layerNormInPlace(xNorm.data, xNorm.shape[0], xNorm.shape[1], ln1.data, model.config.layerNormEps);
    Tensor selfAttn = selfAttention(xNorm, selfAttentionSpec(q, k, v, o, relBias, true, false));
    MathOps.addInPlace(x.data, selfAttn.data);

    Tensor ln2 = model.get(prefix + "layer.1.layer_norm.weight");
    Tensor q2 = model.get(prefix + "layer.1.EncDecAttention.q.weight");
    Tensor k2 = model.get(prefix + "layer.1.EncDecAttention.k.weight");
    Tensor v2 = model.get(prefix + "layer.1.EncDecAttention.v.weight");
    Tensor o2 = model.get(prefix + "layer.1.EncDecAttention.o.weight");

    Tensor xNorm2 = x.copy();
    MathOps.layerNormInPlace(xNorm2.data, xNorm2.shape[0], xNorm2.shape[1], ln2.data, model.config.layerNormEps);
    Tensor cross = crossAttention(xNorm2, encoderHidden, q2, k2, v2, o2);
    MathOps.addInPlace(x.data, cross.data);

    Tensor ln3 = model.get(prefix + "layer.2.layer_norm.weight");
    Tensor wi0 = model.get(prefix + "layer.2.DenseReluDense.wi_0.weight");
    Tensor wi1 = model.get(prefix + "layer.2.DenseReluDense.wi_1.weight");
    Tensor wo = model.get(prefix + "layer.2.DenseReluDense.wo.weight");
    Tensor xNorm3 = x.copy();
    MathOps.layerNormInPlace(xNorm3.data, xNorm3.shape[0], xNorm3.shape[1], ln3.data, model.config.layerNormEps);
    Tensor ff = feedForward(xNorm3, wi0, wi1, wo);
    MathOps.addInPlace(x.data, ff.data);

    return x;
  }

  /**
   * Runs the gated feed-forward network for a given layer.
   *
   * @param x normalized inputs
   * @param wi0 first projection weights
   * @param wi1 second projection weights
   * @param wo output projection weights
   * @return FFN output tensor
   */
  private Tensor feedForward(Tensor x, Tensor wi0, Tensor wi1, Tensor wo) {
    Tensor h0 = MathOps.matmul(x, wi0);
    Tensor h1 = MathOps.matmul(x, wi1);
    int len = h0.data.length;
    if (useParallel(len)) {
      IntStream.range(0, len).parallel().forEach(i -> h0.data[i] = MathOps.gelu(h0.data[i]) * h1.data[i]);
    } else {
      for (int i = 0; i < len; i++) {
        h0.data[i] = MathOps.gelu(h0.data[i]) * h1.data[i];
      }
    }
    return MathOps.matmul(h0, wo);
  }

  /**
   * Runs a self-attention block using the provided spec.
   *
   * @param x input tensor [seq, dModel]
   * @param spec attention spec with weights and biases
   * @return attention output tensor
   */
  public Tensor selfAttention(Tensor x, SelfAttentionSpec spec) {
    int seq = x.shape[0];
    int heads = model.config.numHeads;
    int dKv = model.config.dKv;
    int dAttn = heads * dKv;

    Tensor q = MathOps.matmul(x, spec.wq); // [seq, dModel]
    Tensor k = MathOps.matmul(x, spec.wk);
    Tensor v = MathOps.matmul(x, spec.wv);

    float[] attn = attentionScores(q.data, k.data, seq, heads, dKv, dAttn, spec.causal);

    addRelativeBias(attn, spec.relBias, seq, seq, spec.bidirectionalBias);
    softmaxPerHead(attn, seq, heads);

    Tensor out = attentionOutput(attn, v.data, seq, heads, dKv, dAttn);

    return MathOps.matmul(out, spec.wo);
  }

  /**
   * Computes scaled dot-product scores for each head.
   *
   * @param q query matrix data
   * @param k key matrix data
   * @param seq sequence length
   * @param heads number of heads
   * @param dKv key/value dimension per head
   * @param dAttn total attention width
   * @param causal whether to mask future tokens
   * @return attention score buffer
   */
  private static float[] attentionScores(float[] q, float[] k, int seq, int heads, int dKv, int dAttn, boolean causal) {
    float[] attn = new float[seq * heads * seq];
    int rows = heads * seq;
    SelfScoreContext context = new SelfScoreContext(q, k, seq, dKv, dAttn, causal);
    if (useParallel(rows)) {
      IntStream.range(0, rows).parallel().forEach(row -> fillAttentionScoreRow(attn, row, context));
    } else {
      for (int row = 0; row < rows; row++) {
        fillAttentionScoreRow(attn, row, context);
      }
    }
    return attn;
  }

  /**
   * Fills one flattened self-attention score row for a given head/query pair.
   *
   * @param attn destination attention score buffer
   * @param row flattened row index ({@code head * seq + queryIndex})
   * @param context shared self-attention score context
   */
  private static void fillAttentionScoreRow(float[] attn, int row, SelfScoreContext context) {
    int h = row / context.seq;
    int i = row % context.seq;
    int hOffset = h * context.seq * context.seq;
    int hKvOffset = h * context.dKv;
    int rowOffset = hOffset + i * context.seq;
    for (int j = 0; j < context.seq; j++) {
      attn[rowOffset + j] = attentionScoreForPair(context, i, j, hKvOffset);
    }
  }

  /**
   * Computes one self-attention score entry with optional causal masking.
   *
   * @param context shared self-attention score context
   * @param i query token index
   * @param j key token index
   * @param hKvOffset per-head offset inside the attention channel block
   * @return score value for {@code (i, j)}
   */
  private static float attentionScoreForPair(SelfScoreContext context, int i, int j, int hKvOffset) {
    if (context.causal && j > i) {
      return -1e9f;
    }
    return dotProductForPair(context.q, context.k, i, j, hKvOffset, context.dKv, context.dAttn);
  }

  /**
   * Computes one dot product between query and key slices.
   *
   * @param q query tensor data
   * @param k key tensor data
   * @param i query token index
   * @param j key token index
   * @param hKvOffset per-head offset inside the attention channel block
   * @param dKv key/value width per head
   * @param dAttn total attention width
   * @return unscaled dot-product score
   */
  private static float dotProductForPair(float[] q, float[] k, int i, int j, int hKvOffset, int dKv, int dAttn) {
    int qiBase = i * dAttn + hKvOffset;
    int kjBase = j * dAttn + hKvOffset;
    float sum = 0f;
    for (int d = 0; d < dKv; d++) {
      sum += q[qiBase + d] * k[kjBase + d];
    }
    return sum;
  }

  /**
   * Applies softmax within each head's score matrix.
   *
   * @param attn flattened attention scores
   * @param seq sequence length
   * @param heads number of heads
   */
  private static void softmaxPerHead(float[] attn, int seq, int heads) {
    if (useParallel(heads)) {
      IntStream.range(0, heads).parallel().forEach(h -> {
        int offset = h * seq * seq;
        MathOps.softmaxInPlace(attn, offset, seq, seq);
      });
      return;
    }
    for (int h = 0; h < heads; h++) {
      int offset = h * seq * seq;
      MathOps.softmaxInPlace(attn, offset, seq, seq);
    }
  }

  /**
   * Multiplies attention weights by values to get output.
   *
   * @param attn attention scores
   * @param v value matrix data
   * @param seq query sequence length
   * @param heads number of heads
   * @param dKv head dimension
   * @param dAttn attention width
   * @return combined output tensor
   */
  private static Tensor attentionOutput(float[] attn, float[] v, int seq, int heads, int dKv, int dAttn) {
    Tensor out = Tensor.zeros(seq, dAttn);
    int rows = heads * seq;
    if (useParallel(rows)) {
      IntStream.range(0, rows).parallel().forEach(row -> fillAttentionOutputRow(out.data, attn, v, row, seq, dKv, dAttn));
    } else {
      for (int row = 0; row < rows; row++) {
        fillAttentionOutputRow(out.data, attn, v, row, seq, dKv, dAttn);
      }
    }
    return out;
  }

  /**
   * Fills one flattened attention-output row for a head/query pair.
   *
   * @param outData destination output tensor data
   * @param attn attention probability buffer
   * @param v value tensor data
   * @param row flattened row index ({@code head * seq + queryIndex})
   * @param seq sequence length
   * @param dKv key/value width per head
   * @param dAttn total attention width
   */
  private static void fillAttentionOutputRow(float[] outData, float[] attn, float[] v, int row, int seq, int dKv, int dAttn) {
    int h = row / seq;
    int i = row % seq;
    int hOffset = h * seq * seq;
    int hKvOffset = h * dKv;
    int outOffset = i * dAttn + hKvOffset;
    int rowOffset = hOffset + i * seq;
    for (int d = 0; d < dKv; d++) {
      float sum = 0f;
      int vBase = hKvOffset + d;
      for (int j = 0; j < seq; j++) {
        sum += attn[rowOffset + j] * v[j * dAttn + vBase];
      }
      outData[outOffset + d] = sum;
    }
  }

  /**
   * Wraps attention parameters into a spec for reuse.
   *
   * @param wq query weights
   * @param wk key weights
   * @param wv value weights
   * @param wo output weights
   * @param relBias relative bias tensor
   * @param causal whether masking is enabled
   * @param bidirectionalBias whether bias buckets cover both directions
   * @return prepared spec
   */
  private SelfAttentionSpec selfAttentionSpec(Tensor wq, Tensor wk, Tensor wv, Tensor wo, Tensor relBias, boolean causal, boolean bidirectionalBias) {
    return new SelfAttentionSpec(wq, wk, wv, wo, relBias, causal, bidirectionalBias);
  }

  /**
   * Container for the tensors used during a self-attention call.
   *
   * @since 2026
   * @author Lennart A. Conrad
   */
  public static final class SelfAttentionSpec {
    /**
     * Query projection weights.
     */
    public final Tensor wq;
    /**
     * Key projection weights.
     */
    public final Tensor wk;
    /**
     * Value projection weights.
     */
    public final Tensor wv;
    /**
     * Output projection weights.
     */
    public final Tensor wo;
    /**
     * Relative attention bias tensor.
     */
    public final Tensor relBias;
    /**
     * Whether to mask future tokens.
     */
    public final boolean causal;
    /**
     * Whether bias buckets cover both directions.
     */
    public final boolean bidirectionalBias;

    /**
     * Stores the projection tensors for an attention call.
     *
     * @param wq query weights
     * @param wk key weights
     * @param wv value weights
     * @param wo output weights
     * @param relBias relative bias tensor
     * @param causal causal masking flag
     * @param bidirectionalBias bidirectional bias flag
     */
    public SelfAttentionSpec(Tensor wq, Tensor wk, Tensor wv, Tensor wo, Tensor relBias, boolean causal, boolean bidirectionalBias) {
      this.wq = wq;
      this.wk = wk;
      this.wv = wv;
      this.wo = wo;
      this.relBias = relBias;
      this.causal = causal;
      this.bidirectionalBias = bidirectionalBias;
    }
  }

  /**
   * Runs encoder-decoder (cross) attention.
   *
   * @param x decoder input [seq, dModel]
   * @param enc encoder hidden states [encSeq, dModel]
   * @param wq query weights
   * @param wk key weights
   * @param wv value weights
   * @param wo output projection weights
   * @return attention output tensor
   */
  public Tensor crossAttention(Tensor x, Tensor enc, Tensor wq, Tensor wk, Tensor wv, Tensor wo) {
    int seq = x.shape[0];
    int encSeq = enc.shape[0];
    int heads = model.config.numHeads;
    int dKv = model.config.dKv;
    int dAttn = heads * dKv;

    Tensor q = MathOps.matmul(x, wq);
    Tensor k = MathOps.matmul(enc, wk);
    Tensor v = MathOps.matmul(enc, wv);

    float[] attn = crossAttentionScores(q, k, seq, encSeq, heads, dKv, dAttn);
    softmaxAttention(attn, heads, seq, encSeq);
    Tensor out = applyAttentionValues(attn, v, seq, heads, dKv, dAttn);
    return MathOps.matmul(out, wo);
  }

  /**
   * Adds relative position bias into the attention logits.
   *
   * @param attn attention buffer
   * @param relBias relative bias tensor
   * @param qLen query length
   * @param kLen key length
   * @param bidirectional whether buckets cover both directions
   */
  private void addRelativeBias(float[] attn, Tensor relBias, int qLen, int kLen, boolean bidirectional) {
    int heads = model.config.numHeads;
    int[] bucket = relativePositionBuckets(qLen, kLen, model.config.relBuckets, model.config.relMaxDistance, bidirectional);
    for (int h = 0; h < heads; h++) {
      for (int i = 0; i < qLen; i++) {
        for (int j = 0; j < kLen; j++) {
          int b = bucket[i * kLen + j];
          float bias = relBias.data[b * model.config.numHeads + h];
          attn[(h * qLen + i) * kLen + j] += bias;
        }
      }
    }
  }

  /**
   * Builds bucket indices for each query-key pair.
   *
   * @param qLen query length
   * @param kLen key length
   * @param numBuckets total buckets
   * @param maxDistance max distance to consider
   * @param bidirectional whether buckets split directions
   * @return bucket assignments
   */
  private int[] relativePositionBuckets(int qLen, int kLen, int numBuckets, int maxDistance, boolean bidirectional) {
    int[] out = new int[qLen * kLen];
    int buckets = bidirectional ? numBuckets / 2 : numBuckets;
    int maxExact = buckets / 2;
    for (int i = 0; i < qLen; i++) {
      for (int j = 0; j < kLen; j++) {
        out[i * kLen + j] = relativeBucket(j - i, buckets, maxExact, maxDistance, bidirectional);
      }
    }
    return out;
  }

  /**
   * Maps a single relative offset into a bucket index.
   *
   * @param relative offset value
   * @param buckets bucket count
   * @param maxExact number of exact buckets
   * @param maxDistance distance limit
   * @param bidirectional flag for bidirectional mode
   * @return bucket index
   */
  private int relativeBucket(int relative, int buckets, int maxExact, int maxDistance, boolean bidirectional) {
    int n = relative;
    int bucketOffset = 0;
    if (bidirectional) {
      if (n > 0) {
        bucketOffset = buckets;
      }
      n = Math.abs(n);
    } else {
      n = Math.max(-n, 0);
    }
    int bucket = n < maxExact ? n : logBucket(n, maxExact, buckets, maxDistance);
    return bucketOffset + bucket;
  }

  /**
   * Interpolates a logarithmic bucket for large distances.
   *
   * @param n offset magnitude
   * @param maxExact number of exact buckets
   * @param buckets bucket count
   * @param maxDistance maximum distance
   * @return computed bucket
   */
  private int logBucket(int n, int maxExact, int buckets, int maxDistance) {
    double logVal = Math.log((double) n / maxExact) / Math.log((double) maxDistance / maxExact);
    int scaled = (int) (maxExact + (buckets - maxExact) * logVal);
    return Math.min(buckets - 1, scaled);
  }

  /**
   * Computes dot-product scores between decoder queries and encoder keys.
   *
   * @param q decoder queries
   * @param k encoder keys
   * @param seq decoder length
   * @param encSeq encoder length
   * @param heads attention heads
   * @param dKv head dimension
   * @param dAttn attention width
   * @return flattened score tensor
   */
  private float[] crossAttentionScores(Tensor q, Tensor k, int seq, int encSeq, int heads, int dKv, int dAttn) {
    float[] attn = new float[seq * heads * encSeq];
    int rows = heads * seq;
    CrossScoreContext context = new CrossScoreContext(q.data, k.data, seq, encSeq, dKv, dAttn);
    if (useParallel(rows)) {
      IntStream.range(0, rows).parallel().forEach(row -> fillCrossAttentionScoreRow(attn, row, context));
    } else {
      for (int row = 0; row < rows; row++) {
        fillCrossAttentionScoreRow(attn, row, context);
      }
    }
    return attn;
  }

  /**
   * Fills one flattened cross-attention score row for a head/query pair.
   *
   * @param attn destination cross-attention score buffer
   * @param row flattened row index ({@code head * qLen + queryIndex})
   * @param context shared cross-attention score context
   */
  private static void fillCrossAttentionScoreRow(float[] attn, int row, CrossScoreContext context) {
    int h = row / context.seq;
    int i = row % context.seq;
    int rowOffset = (h * context.seq + i) * context.encSeq;
    int qiBase = i * context.dAttn + h * context.dKv;
    int hOffset = h * context.dKv;
    for (int j = 0; j < context.encSeq; j++) {
      int kjBase = j * context.dAttn + hOffset;
      float sum = 0f;
      for (int d = 0; d < context.dKv; d++) {
        sum += context.qData[qiBase + d] * context.kData[kjBase + d];
      }
      attn[rowOffset + j] = sum;
    }
  }

  /**
   * Applies softmax per head for encoder-decoder attention.
   *
   * @param attn scores buffer
   * @param heads number of heads
   * @param qLen decoder length
   * @param kLen encoder length
   */
  private void softmaxAttention(float[] attn, int heads, int qLen, int kLen) {
    if (useParallel(heads)) {
      IntStream.range(0, heads).parallel().forEach(h -> {
        int offset = h * qLen * kLen;
        MathOps.softmaxInPlace(attn, offset, qLen, kLen);
      });
      return;
    }
    for (int h = 0; h < heads; h++) {
      int offset = h * qLen * kLen;
      MathOps.softmaxInPlace(attn, offset, qLen, kLen);
    }
  }

  /**
   * Applies attention weights to the values tensor.
   *
   * @param attn attention probabilities
   * @param v value tensor
   * @param qLen decoder length
   * @param heads number of heads
   * @param dKv head dimension
   * @param dAttn attention width
   * @return aggregated tensor
   */
  private Tensor applyAttentionValues(float[] attn, Tensor v, int qLen, int heads, int dKv, int dAttn) {
    Tensor out = Tensor.zeros(qLen, dAttn);
    int rows = heads * qLen;
    if (useParallel(rows)) {
      IntStream.range(0, rows).parallel().forEach(row -> fillCrossAttentionValueRow(out.data, attn, v.data, row, dKv, dAttn));
    } else {
      for (int row = 0; row < rows; row++) {
        fillCrossAttentionValueRow(out.data, attn, v.data, row, dKv, dAttn);
      }
    }
    return out;
  }

  /**
   * Shared constants and buffers for self-attention score row computation.
   *
   * @since 2026
   * @author Lennart A. Conrad
   */
  private static final class SelfScoreContext {
    /**
     * Flattened query projection matrix.
     */
    final float[] q;
    /**
     * Flattened key projection matrix.
     */
    final float[] k;
    /**
     * Sequence length.
     */
    final int seq;
    /**
     * Key/value width per head.
     */
    final int dKv;
    /**
     * Total attention channel width.
     */
    final int dAttn;
    /**
     * Whether causal masking is enabled.
     */
    final boolean causal;

    /**
     * Captures immutable context used to score self-attention rows.
     *
     * @param q flattened query projection matrix
     * @param k flattened key projection matrix
     * @param seq sequence length
     * @param dKv key/value width per head
     * @param dAttn total attention channel width
     * @param causal whether causal masking is enabled
     */
    SelfScoreContext(float[] q, float[] k, int seq, int dKv, int dAttn, boolean causal) {
      this.q = q;
      this.k = k;
      this.seq = seq;
      this.dKv = dKv;
      this.dAttn = dAttn;
      this.causal = causal;
    }
  }

  /**
   * Shared constants and buffers for cross-attention score row computation.
   *
   * @since 2026
   * @author Lennart A. Conrad
   */
  private static final class CrossScoreContext {
    /**
     * Flattened decoder query projection matrix.
     */
    final float[] qData;
    /**
     * Flattened encoder key projection matrix.
     */
    final float[] kData;
    /**
     * Decoder sequence length.
     */
    final int seq;
    /**
     * Encoder sequence length.
     */
    final int encSeq;
    /**
     * Key/value width per head.
     */
    final int dKv;
    /**
     * Total attention channel width.
     */
    final int dAttn;

    /**
     * Captures immutable context used to score cross-attention rows.
     *
     * @param qData flattened decoder query projection matrix
     * @param kData flattened encoder key projection matrix
     * @param seq decoder sequence length
     * @param encSeq encoder sequence length
     * @param dKv key/value width per head
     * @param dAttn total attention channel width
     */
    CrossScoreContext(float[] qData, float[] kData, int seq, int encSeq, int dKv, int dAttn) {
      this.qData = qData;
      this.kData = kData;
      this.seq = seq;
      this.encSeq = encSeq;
      this.dKv = dKv;
      this.dAttn = dAttn;
    }
  }

  /**
   * Fills one flattened cross-attention value row for a head/query pair.
   *
   * @param outData destination output tensor data
   * @param attn cross-attention probability buffer
   * @param vData encoder value projection data
   * @param row flattened row index ({@code head * qLen + queryIndex})
   * @param dKv key/value width per head
   * @param dAttn total attention channel width
   */
  private static void fillCrossAttentionValueRow(float[] outData, float[] attn, float[] vData, int row, int dKv, int dAttn) {
    int qLen = outData.length / dAttn;
    int kLen = vData.length / dAttn;
    int h = row / qLen;
    int i = row % qLen;
    int outOffset = i * dAttn + h * dKv;
    for (int d = 0; d < dKv; d++) {
      float sum = 0f;
      for (int j = 0; j < kLen; j++) {
        float weight = attn[(h * qLen + i) * kLen + j];
        int vIndex = j * dAttn + h * dKv + d;
        sum += weight * vData[vIndex];
      }
      outData[outOffset + d] = sum;
    }
  }

  /**
   * Selects the id with the highest logit from the last position.
   *
   * @param logits decoder logits
   * @return argmax id
   */
  private int argmaxLast(Tensor logits) {
    int seq = logits.shape[0];
    int vocab = logits.shape[1];
    int offset = (seq - 1) * vocab;
    float max = -Float.MAX_VALUE;
    int idx = 0;
    for (int i = 0; i < vocab; i++) {
      float v = logits.data[offset + i];
      if (v > max) {
        max = v;
        idx = i;
      }
    }
    return idx;
  }
}
