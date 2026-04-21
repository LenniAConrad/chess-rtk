package chess.nn.t5.cuda;

import chess.nn.t5.Model;
import chess.nn.t5.NativeBackendOps;
import chess.nn.t5.NativeGenerationBackend;

/**
 * Optional CUDA backend for end-to-end T5 inference.
 *
 * <p>This loads the native {@code t5_cuda} library and runs greedy decoding on the GPU.
 * If initialization fails, callers should fall back to the CPU path.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Backend implements NativeGenerationBackend {

  /**
   * Native handle to the CUDA model instance.
   */
  private final long handle;

   /**
   * Creates a new backend instance.
   * @param handle handle
   */
   private Backend(long handle) {
    this.handle = handle;
  }

  /**
   * Attempts to create a CUDA backend for the given model.
   *
   * @param model loaded T5 model (must have a source path)
   * @return backend instance or {@code null} if unavailable
   */
  public static Backend tryCreate(Model model) {
    long handle = NativeBackendOps.tryCreateHandle(model, Support::isAvailable, Backend::nativeCreate);
    if (handle == 0L) {
      return null;
    }
    return new Backend(handle);
  }

  /**
   * Runs greedy decoding and returns generated token ids (including decoder start).
   *
   * @param inputIds encoder input ids
   * @param maxNewTokens maximum new tokens to generate
   * @return generated token ids, or an empty array if the backend failed
   */
  public int[] generateIds(int[] inputIds, int maxNewTokens) {
    return NativeBackendOps.generateIds(handle, inputIds, maxNewTokens, Backend::nativeGenerateIds);
  }

  /**
   * Releases native resources.
   */
  @Override
  public void close() {
    NativeBackendOps.destroy(handle, Backend::nativeDestroy);
  }

  /**
   * JNI entry point implemented in {@code native/cuda/t5_cuda_jni.cu}.
   *
   * @param weightsPath absolute path to the T5 weights file
   * @return native handle or zero on failure
   */
  private static native long nativeCreate(String weightsPath);

  /**
   * JNI entry point implemented in {@code native/cuda/t5_cuda_jni.cu}.
   *
   * @param handle native handle to destroy
   */
  private static native void nativeDestroy(long handle);

  /**
   * JNI entry point implemented in {@code native/cuda/t5_cuda_jni.cu}.
   *
   * @param handle native handle
   * @param inputIds encoder input ids
   * @param maxNewTokens maximum new tokens to generate
   * @return generated token ids, or {@code null} on failure
   */
  private static native int[] nativeGenerateIds(long handle, int[] inputIds, int maxNewTokens);
}
