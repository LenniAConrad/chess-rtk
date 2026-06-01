package chess.nn.perft.oneapi;

import chess.gpu.SharedLibrarySupport;

/**
 * Optional oneAPI/SYCL support for native bulk perft via a tiny JNI shared library.
 *
 * <p>
 * Loads {@code libperft_oneapi.so} and exposes capability checks; callers fall
 * back to the pure-Java perft path when the library or an Intel GPU is absent.
 * Point {@code CRTK_PERFT_ONEAPI_LIB} at an explicit library file to override
 * discovery.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Support {

    /**
     * Base library name used by {@link System#loadLibrary(String)}.
     */
    private static final String LIB_BASE_NAME = "perft_oneapi";

    /**
     * Environment variable pointing to an explicit oneAPI perft library path.
     */
    private static final String ENV_PERFT_ONEAPI_LIB = "CRTK_PERFT_ONEAPI_LIB";

    /**
     * Repository directory containing the optional oneAPI JNI sources/build outputs.
     */
    private static final String DIR_NATIVE_ONEAPI = "native/oneapi";

    /**
     * Shared JNI load state.
     */
    private static final SharedLibrarySupport.State STATE =
            SharedLibrarySupport.load(LIB_BASE_NAME, ENV_PERFT_ONEAPI_LIB, DIR_NATIVE_ONEAPI, Support::nativeDeviceCount);

    /**
     * Utility class; prevents instantiation.
     */
    private Support() {
    }

    /**
     * @return {@code true} if the JNI library loaded and an Intel GPU is visible
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
     * @return number of visible Intel GPUs (0 when unavailable)
     */
    public static int deviceCount() {
        return STATE.deviceCount();
    }

    /**
     * JNI entry point implemented in {@code native/oneapi/perft_oneapi_jni.cpp}.
     *
     * @return number of visible Intel GPUs (0 on error)
     */
    private static native int nativeDeviceCount();
}
