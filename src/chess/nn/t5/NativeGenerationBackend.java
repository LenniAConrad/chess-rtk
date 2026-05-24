package chess.nn.t5;

/**
 * Common contract for optional native T5 generation backends.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public interface NativeGenerationBackend extends AutoCloseable {

  /**
   * Runs greedy decoding and returns generated token ids.
   *
   * @param inputIds encoder input ids
   * @param maxNewTokens maximum new tokens to generate
   * @return generated token ids, or an empty array on failure
   */
  int[] generateIds(int[] inputIds, int maxNewTokens);

  /**
   * Releases any native resources held by the backend.
   */
  @Override
  void close();
}
