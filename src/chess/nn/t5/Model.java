package chess.nn.t5;

import java.util.List;
import java.util.Map;

/**
 * Holds the loaded T5 weights, config, and tokenizer.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Model {

  /**
   * Configuration metadata for the loaded model.
   */
  public final Config config;

  /**
   * Tokenizer instance for encoding/decoding text.
   */
  public final Tokenizer tokenizer;
  
  /**
   * Map of layer tensors keyed by export names.
   */
  public final Map<String, Tensor> tensors;

  /**
   * Optional path to the source .bin file (used by GPU backends).
   */
  public final String sourcePath;

  /**
   * Creates a model instance.
   *
   * @param config model configuration
   * @param tokenizer tokenizer
   * @param tensors weight tensors
   * @param sourcePath optional source path to the model .bin file
   */
  public Model(Config config, Tokenizer tokenizer, Map<String, Tensor> tensors, String sourcePath) {
    this.config = config;
    this.tokenizer = tokenizer;
    this.tensors = tensors;
    this.sourcePath = sourcePath;
  }

  /**
   * Fetches a tensor by name.
   *
   * @param name tensor name
   * @return tensor
   * @throws IllegalArgumentException if missing
   */
  public Tensor get(String name) {
    Tensor t = tensors.get(name);
    if (t == null) {
      throw new IllegalArgumentException("Missing tensor: " + name);
    }
    return t;
  }

  /**
   * Looks up token embeddings for a token sequence.
   *
   * @param ids token ids
   * @return embedding tensor
   */
  public Tensor embed(List<Integer> ids) {
    Tensor emb = get("shared.weight");
    int vocab = emb.shape[0];
    int dModel = emb.shape[1];
    Tensor out = Tensor.zeros(ids.size(), dModel);
    float[] outData = out.data;
    for (int i = 0; i < ids.size(); i++) {
      int id = ids.get(i);
      if (id < 0 || id >= vocab) {
        id = config.unkId;
      }
      int embOffset = id * dModel;
      int outOffset = i * dModel;
      System.arraycopy(emb.data, embOffset, outData, outOffset, dModel);
    }
    return out;
  }
}
