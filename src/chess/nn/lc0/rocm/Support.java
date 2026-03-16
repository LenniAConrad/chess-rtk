package chess.nn.lc0.rocm;

import chess.gpu.SharedLibrarySupport;

/**
 * Optional ROCm (AMD) support via a tiny JNI shared library (no third-party Java deps).
 *
 * <p>This class loads the JNI library ({@code liblc0j_rocm.so}/{@code lc0j_rocm.dll})
 * and exposes capability checks such as {@link #deviceCount()}.
 *
 * <p>If the native library isn't present, or no ROCm device exists, the Java code
 * automatically falls back to the pure-Java CPU path.
 *
 * <p>To enable ROCm inference, build the native library and run Java with:
 * {@code -Djava.library.path=/path/to/build/dir}.
 *
 * <p>Build instructions for the native library live under {@code native/rocm/} in this repo.
 *
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class Support {

    /**
     * Base library name used by {@link System#loadLibrary(String)}.
     */
    private static final String LIB_BASE_NAME = "lc0j_rocm";

    /**
     * Environment variable that can point to an explicit ROCm JNI library path.
     *
     * <p>
     * Example: {@code CRTK_ROCM_LIB=/absolute/path/to/liblc0j_rocm.so}
     * </p>
     */
    private static final String ENV_ROCM_LIB = "CRTK_ROCM_LIB";

    /**
     * Repository directory containing the optional ROCm JNI sources/build outputs.
     */
    private static final String DIR_NATIVE_ROCM = "native/rocm";

    /**
     * Shared JNI load state.
     */
    private static final SharedLibrarySupport.State STATE =
            SharedLibrarySupport.load(LIB_BASE_NAME, ENV_ROCM_LIB, DIR_NATIVE_ROCM, Support::nativeDeviceCount);

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
     * <p>
     * This can be {@code true} even when {@link #deviceCount()} is zero (for example, when the library is present but
     * no ROCm device is visible or the runtime cannot initialize).
     * </p>
     *
     * @return {@code true} if the native library loaded.
     */
    public static boolean isLoaded() {
        return STATE.loaded();
    }

    /**
     * Returns the number of ROCm devices visible to the HIP runtime.
     *
     * <p>If the native library is not present or the runtime cannot be initialized, this returns 0.
     *
     * @return number of available devices (0 when unavailable)
     */
    public static int deviceCount() {
        return STATE.deviceCount();
    }

    /**
     * JNI entry point implemented in {@code native/rocm/lc0j_rocm_jni.hip}.
     *
     * @return number of visible ROCm devices (0 on error)
     */
    private static native int nativeDeviceCount();
}
