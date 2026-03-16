package chess.nn.t5.oneapi;

import chess.gpu.SharedLibrarySupport;

/**
 * Optional oneAPI (Intel) support for the T5 tag-to-text pipeline.
 *
 * <p>This loads the JNI library ({@code libt5_oneapi.so}/{@code t5_oneapi.dll})
 * and exposes simple capability checks such as {@link #deviceCount()}.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Support {

  /**
   * Base library name used by {@link System#loadLibrary(String)}.
   */
  private static final String LIB_BASE_NAME = "t5_oneapi";

  /**
   * Environment variable that can point to an explicit oneAPI JNI library path.
   */
  private static final String ENV_T5_ONEAPI_LIB = "CRTK_T5_ONEAPI_LIB";

  /**
   * Repository directory containing the optional oneAPI JNI sources/build outputs.
   */
  private static final String DIR_NATIVE_T5_ONEAPI = "native/oneapi";

  /**
   * Shared JNI load state.
   */
  private static final SharedLibrarySupport.State STATE =
      SharedLibrarySupport.load(LIB_BASE_NAME, ENV_T5_ONEAPI_LIB, DIR_NATIVE_T5_ONEAPI, Support::nativeDeviceCount);

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
   * Returns the number of oneAPI devices visible to the runtime.
   *
   * @return number of available devices (0 when unavailable)
   */
  public static int deviceCount() {
    return STATE.deviceCount();
  }

  /**
   * JNI entry point implemented in {@code native/oneapi/t5_oneapi_jni.cpp} (when provided).
   *
   * @return number of visible devices (0 on error)
   */
  private static native int nativeDeviceCount();
}
