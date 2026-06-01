package chess.nn.perft.rocm;

import chess.gpu.SharedLibrarySupport;

/**
 * Optional ROCm/HIP support for native bulk perft via a tiny JNI shared library.
 *
 * <p>
 * Loads {@code libperft_rocm.so} and exposes capability checks; callers fall back
 * to the pure-Java perft path when the library or an AMD device is absent. Point
 * {@code CRTK_PERFT_ROCM_LIB} at an explicit library file to override discovery.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Support {

    /**
     * Base library name used by {@link System#loadLibrary(String)}.
     */
    private static final String LIB_BASE_NAME = "perft_rocm";

    /**
     * Environment variable pointing to an explicit ROCm perft library path.
     */
    private static final String ENV_PERFT_ROCM_LIB = "CRTK_PERFT_ROCM_LIB";

    /**
     * Repository directory containing the optional ROCm JNI sources/build outputs.
     */
    private static final String DIR_NATIVE_ROCM = "native/rocm";

    /**
     * Shared JNI load state.
     */
    private static final SharedLibrarySupport.State STATE =
            SharedLibrarySupport.load(LIB_BASE_NAME, ENV_PERFT_ROCM_LIB, DIR_NATIVE_ROCM, Support::nativeDeviceCount);

    /**
     * Utility class; prevents instantiation.
     */
    private Support() {
    }

    /**
     * @return {@code true} if the JNI library loaded and a ROCm device is visible
     */
    public static boolean isAvailable() {
        return STATE.deviceCount() > 0;
    }

    /**
     * @return {@code true} if the native library loaded
     */
    public static boolean isLoaded() {
        return STATE.loaded();
    }

    /**
     * @return number of visible ROCm devices (0 when unavailable)
     */
    public static int deviceCount() {
        return STATE.deviceCount();
    }

    /**
     * JNI entry point implemented in {@code native/rocm/perft_rocm_jni.hip}.
     *
     * @return number of visible ROCm devices (0 on error)
     */
    private static native int nativeDeviceCount();
}
