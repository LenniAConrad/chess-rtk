package chess.nn.t5;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads the custom .bin export for the T5 model.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class BinLoader {
  /**
   * Maximum accepted UTF-8 string byte length in the binary export.
   */
  private static final int MAX_STRING_LENGTH = 10_000_000;

  /**
   * Upper bound for SentencePiece vocabulary rows accepted before allocation.
   */
  private static final int MAX_SENTENCEPIECE_VOCAB = 1_000_000;

  /**
   * Upper bound for tensor records accepted before map allocation.
   */
  private static final int MAX_TENSOR_COUNT = 100_000;

  /**
   * Upper bound for one tensor's rank.
   */
  private static final int MAX_TENSOR_DIMS = 8;

  /**
   * Upper bound for one tensor's flat float element count.
   */
  private static final int MAX_TENSOR_ELEMENTS = 100_000_000;

  /**
   * Prevent instantiation since helpers are static.
   */
  private BinLoader() {}

  /**
   * Loads a model from a .bin file.
   *
   * @param path path to the exported model
   * @return loaded model
   * @throws IOException if the file is missing or malformed
   */
  public static Model load(String path) throws IOException {
    try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(path)))) {
      int magic = in.readInt();
      if (magic != 0x4C545454) { // LTTT
        throw new IOException("Invalid T5 bin magic");
      }
      int version = in.readInt();
      if (version != 1) {
        throw new IOException("Unsupported T5 bin version: " + version);
      }
      String name = readString(in);
      int vocabSize = in.readInt();
      int dModel = in.readInt();
      int dKv = in.readInt();
      int dFf = in.readInt();
      int numLayers = in.readInt();
      int numDecoderLayers = in.readInt();
      int numHeads = in.readInt();
      int relBuckets = in.readInt();
      int relMaxDistance = in.readInt();
      int padId = in.readInt();
      int eosId = in.readInt();
      int decoderStartId = in.readInt();
      int unkId = in.readInt();
      boolean gatedGelu = in.readInt() == 1;
      in.readInt(); // fp16 flag (unused for now)
      float layerNormEps = in.readFloat();

      int spVocab = readNonNegativeInt(in, "SentencePiece vocabulary size", MAX_SENTENCEPIECE_VOCAB);
      String[] pieces = new String[spVocab];
      for (int i = 0; i < spVocab; i++) {
        pieces[i] = readString(in);
      }
      float[] scores = new float[spVocab];
      for (int i = 0; i < spVocab; i++) {
        scores[i] = in.readFloat();
      }

      int tensorCount = readNonNegativeInt(in, "tensor count", MAX_TENSOR_COUNT);
      Map<String, Tensor> tensors = new HashMap<>(Math.max(16, tensorCount * 2));
      for (int t = 0; t < tensorCount; t++) {
        String tensorName = readString(in);
        int dims = readNonNegativeInt(in, "dimension count for tensor " + tensorName, MAX_TENSOR_DIMS);
        int[] shape = new int[dims];
        int total = 1;
        for (int i = 0; i < dims; i++) {
          shape[i] = readNonNegativeInt(in, "dimension " + i + " for tensor " + tensorName, MAX_TENSOR_ELEMENTS);
          total = checkedElementProduct(total, shape[i], tensorName);
        }
        int count = readNonNegativeInt(in, "element count for tensor " + tensorName, MAX_TENSOR_ELEMENTS);
        if (count != total) {
          throw new IOException("Tensor size mismatch for " + tensorName);
        }
        float[] data = new float[count];
        for (int i = 0; i < count; i++) {
          data[i] = readFloatLE(in);
        }
        tensors.put(tensorName, new Tensor(data, shape));
      }

      Config.Sizes sizes = new Config.Sizes(vocabSize, dModel, dKv, dFf);
      Config.Layers layers = new Config.Layers(numLayers, numDecoderLayers, numHeads);
      Config.RelPos relPos = new Config.RelPos(relBuckets, relMaxDistance);
      Config.SpecialTokens tokens = new Config.SpecialTokens(padId, eosId, decoderStartId, unkId);
      Config config = new Config(name, sizes, layers, relPos, tokens, gatedGelu, layerNormEps);
      Tokenizer tokenizer = new Tokenizer(pieces, scores, unkId);
      return new Model(config, tokenizer, tensors, path);
    }
  }

  /**
   * Reads a length-prefixed UTF-8 string.
   *
   * @param in input stream
   * @return decoded string
   * @throws IOException on I/O errors or invalid length
   */
  private static String readString(DataInputStream in) throws IOException {
    int len = readNonNegativeInt(in, "string length", MAX_STRING_LENGTH);
    byte[] data = new byte[len];
    in.readFully(data);
    return new String(data, StandardCharsets.UTF_8);
  }

  /**
   * Reads a bounded non-negative integer field from the binary export.
   *
   * @param in input stream
   * @param label field label for diagnostics
   * @param max largest accepted value
   * @return validated integer value
   * @throws IOException on I/O errors or invalid values
   */
  private static int readNonNegativeInt(DataInputStream in, String label, int max) throws IOException {
    int value = in.readInt();
    if (value < 0 || value > max) {
      throw new IOException("Invalid " + label + ": " + value);
    }
    return value;
  }

  /**
   * Multiplies tensor dimensions while rejecting overflow and huge tensors
   * before allocation.
   *
   * @param current current element product
   * @param dimension next tensor dimension
   * @param tensorName tensor name for diagnostics
   * @return updated element product
   * @throws IOException when the tensor is too large
   */
  private static int checkedElementProduct(int current, int dimension, String tensorName) throws IOException {
    if (current == 0 || dimension == 0) {
      return 0;
    }
    if (current > MAX_TENSOR_ELEMENTS / dimension) {
      throw new IOException("Tensor too large for " + tensorName);
    }
    return current * dimension;
  }

  /**
   * Reads a little-endian float.
   *
   * @param in input stream
   * @return float value
   * @throws IOException on I/O errors
   */
  private static float readFloatLE(DataInputStream in) throws IOException {
    int i = Integer.reverseBytes(in.readInt());
    return Float.intBitsToFloat(i);
  }
}
