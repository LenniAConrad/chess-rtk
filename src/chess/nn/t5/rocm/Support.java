package chess.nn.t5.rocm;

import chess.gpu.SharedLibrarySupport;

/**
 * Optional ROCm (AMD) support for the T5 tag-to-text pipeline.
 *
 * <p>This loads the JNI library ({@code libt5_rocm.so}/{@code t5_rocm.dll})
 * and exposes simple capability checks such as {@link #deviceCount()}.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Support {

  /**
   * Base library name used by {@link System#loadLibrary(String)}.
   */
  private static final String LIB_BASE_NAME = "t5_rocm";

  /**
   * Environment variable that can point to an explicit ROCm JNI library path.
   */
  private static final String ENV_T5_ROCM_LIB = "CRTK_T5_ROCM_LIB";

  /**
   * Repository directory containing the optional ROCm JNI sources/build outputs.
   */
  private static final String DIR_NATIVE_T5_ROCM = "native/rocm";

  /**
   * Shared JNI load state.
   */
  private static final SharedLibrarySupport.State STATE =
      SharedLibrarySupport.load(LIB_BASE_NAME, ENV_T5_ROCM_LIB, DIR_NATIVE_T5_ROCM, Support::nativeDeviceCount);

  /**
   * Utility class, prevents instantiation.
   */
  private Support() {}

  /**
   * @return {@code true} if the JNI library loaded and {@link #deviceCount()} is greater than zero
   */
  public static boolean isAvailable() {
    return STATE.deviceCount() > 0;
  }

  /**
   * Returns whether the JNI library was successfully loaded.
   *
   * @return {@code true} if the native library loaded.
   */
  public static boolean isLoaded() {
    return STATE.loaded();
  }

  /**
   * Returns the number of ROCm devices visible to the HIP runtime.
   *
   * @return number of available devices (0 when unavailable)
   */
  public static int deviceCount() {
    return STATE.deviceCount();
  }

  /**
   * JNI entry point implemented in {@code native/rocm/t5_rocm_jni.hip} (when provided).
   *
   * @return number of visible ROCm devices (0 on error)
   */
  private static native int nativeDeviceCount();
}
