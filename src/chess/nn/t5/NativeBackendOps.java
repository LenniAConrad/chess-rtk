package chess.nn.t5;

import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

/**
 * Shared helpers for thin JNI-backed T5 backends.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class NativeBackendOps {

   /**
   * Creates a new native backend ops instance.
   */
   private NativeBackendOps() {}

  /**
   * Functional interface for native model creation.
   */
  @FunctionalInterface
  public interface HandleCreator {
    /**
     * Creates a native backend handle for the given weights path.
     *
     * @param weightsPath absolute path to the weights file
     * @return native handle, or zero on failure
     */
    long create(String weightsPath);
  }

  /**
   * Functional interface for native id generation.
   */
  @FunctionalInterface
  public interface IdGenerator {
    /**
     * Generates token ids using the native backend.
     *
     * @param handle native backend handle
     * @param inputIds encoder input ids
     * @param maxNewTokens maximum new tokens to generate
     * @return generated ids, or {@code null} on failure
     */
    int[] generate(long handle, int[] inputIds, int maxNewTokens);
  }

  /**
   * Runs the common T5 backend creation checks and returns a handle.
   *
   * @param model loaded model
   * @param available whether the backend is currently available
   * @param creator native handle creator
   * @return native handle, or zero when unavailable or initialization failed
   */
  public static long tryCreateHandle(Model model, BooleanSupplier available, HandleCreator creator) {
    if (model == null || model.sourcePath == null || model.sourcePath.isBlank()) {
      return 0L;
    }
    if (!available.getAsBoolean()) {
      return 0L;
    }
    return creator.create(model.sourcePath);
  }

  /**
   * Runs a native generation call with the standard null guard.
   *
   * @param handle native backend handle
   * @param inputIds encoder input ids
   * @param maxNewTokens maximum new tokens to generate
   * @param generator native generator
   * @return generated ids, or an empty array on invalid input / backend failure
   */
  public static int[] generateIds(long handle, int[] inputIds, int maxNewTokens, IdGenerator generator) {
    if (inputIds == null) {
      return new int[0];
    }
    return generator.generate(handle, inputIds, maxNewTokens);
  }

  /**
   * Releases a native handle through the provided destroy function.
   *
   * @param handle native backend handle
   * @param destroyer native destroy function
   */
  public static void destroy(long handle, LongConsumer destroyer) {
    destroyer.accept(handle);
  }
}
