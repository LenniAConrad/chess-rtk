package chess.nn.perft.cuda;

import chess.gpu.SharedLibrarySupport;

/**
 * Optional CUDA support for native bulk perft via a tiny JNI shared library.
 *
 * <p>
 * Loads {@code libperft_cuda.so} / {@code perft_cuda.dll} and exposes capability
 * checks. When the library or a CUDA device is absent, callers fall back to the
 * pure-Java perft path. Point {@code CRTK_PERFT_CUDA_LIB} at an explicit library
 * file (or set {@code -Djava.library.path}) to override discovery.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Support {

    /**
     * Base library name used by {@link System#loadLibrary(String)}.
     */
    private static final String LIB_BASE_NAME = "perft_cuda";

    /**
     * Environment variable pointing to an explicit CUDA perft library path.
     */
    private static final String ENV_PERFT_CUDA_LIB = "CRTK_PERFT_CUDA_LIB";

    /**
     * Repository directory containing the optional CUDA JNI sources/build outputs.
     */
    private static final String DIR_NATIVE_CUDA = "native/cuda";

    /**
     * Shared JNI load state.
     */
    private static final SharedLibrarySupport.State STATE =
            SharedLibrarySupport.load(LIB_BASE_NAME, ENV_PERFT_CUDA_LIB, DIR_NATIVE_CUDA, Support::nativeDeviceCount);

    /**
     * Utility class; prevents instantiation.
     */
    private Support() {
    }

    /**
     * @return {@code true} if the JNI library loaded and a CUDA device is visible
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
     * @return number of visible CUDA devices (0 when unavailable)
     */
    public static int deviceCount() {
        return STATE.deviceCount();
    }

    /**
     * JNI entry point implemented in {@code native/cuda/perft_cuda_jni.cu}.
     *
     * @return number of visible CUDA devices (0 on error)
     */
    private static native int nativeDeviceCount();
}
