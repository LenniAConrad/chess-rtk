package chess.nn.otis.cuda;

import chess.gpu.SharedLibrarySupport;

/**
 * Optional CUDA support for OTIS native inference via a tiny JNI shared library
 * (no third-party Java deps).
 *
 * <p>This class loads the JNI library ({@code libotis_cuda.so}/{@code otis_cuda.dll})
 * and exposes capability checks such as {@link #deviceCount()}.
 *
 * <p>If the native library isn't present, or no CUDA device exists, the Java code
 * automatically falls back to the pure-Java CPU path.
 *
 * <p>To enable CUDA inference, build the native library and run Java with:
 * {@code -Djava.library.path=/path/to/build/dir} or set {@code CRTK_OTIS_CUDA_LIB}.
 *
 * <p>Build instructions for the native library live under {@code native/cuda/} in this repo.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Support {

    /**
     * Base library name used by {@link System#loadLibrary(String)}.
     */
    private static final String LIB_BASE_NAME = "otis_cuda";

    /**
     * Environment variable that can point to an explicit CUDA JNI library path.
     *
     * <p>
     * Example: {@code CRTK_OTIS_CUDA_LIB=/absolute/path/to/libotis_cuda.so}
     * </p>
     */
    private static final String ENV_OTIS_CUDA_LIB = "CRTK_OTIS_CUDA_LIB";

    /**
     * Repository directory containing the optional CUDA JNI sources/build outputs.
     */
    private static final String DIR_NATIVE_CUDA = "native/cuda";

    /**
     * Shared JNI load state.
     */
    private static final SharedLibrarySupport.State STATE =
            SharedLibrarySupport.load(LIB_BASE_NAME, ENV_OTIS_CUDA_LIB, DIR_NATIVE_CUDA, Support::nativeDeviceCount);

    /**
     * Utility class, prevents instantiation.
     */
    private Support() {
    }

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
     * no CUDA device is visible or the CUDA runtime cannot initialize).
     * </p>
     *
     * @return {@code true} if the native library loaded.
     */
    public static boolean isLoaded() {
        return STATE.loaded();
    }

    /**
     * Returns the number of CUDA devices visible to the CUDA runtime.
     *
     * <p>If the native library is not present or the CUDA runtime cannot be initialized, this returns 0.
     *
     * @return number of available CUDA devices (0 when unavailable)
     */
    public static int deviceCount() {
        return STATE.deviceCount();
    }

    /**
     * JNI entry point implemented in {@code native/cuda/otis_cuda_jni.cu}.
     *
     * @return number of visible CUDA devices (0 on error)
     */
    private static native int nativeDeviceCount();
}
