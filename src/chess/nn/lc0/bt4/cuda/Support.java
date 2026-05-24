package chess.nn.lc0.bt4.cuda;

import chess.gpu.SharedLibrarySupport;

/**
 * Optional CUDA support for LC0 BT4 native inference.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Support {

    /**
     * Shared LC0 CUDA JNI library name.
     */
    private static final String LIB_BASE_NAME = "lc0_cuda";

    /**
     * Explicit CUDA JNI library path environment variable.
     */
    private static final String ENV_CUDA_LIB = "CRTK_CUDA_LIB";

    /**
     * Native CUDA source/build directory.
     */
    private static final String DIR_NATIVE_CUDA = "native/cuda";

    /**
     * Shared JNI load state.
     */
    private static final SharedLibrarySupport.State STATE =
            SharedLibrarySupport.load(
                    new String[] { LIB_BASE_NAME },
                    ENV_CUDA_LIB,
                    DIR_NATIVE_CUDA,
                    Support::nativeDeviceCount);

    /**
     * Utility class, prevents instantiation.
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
     * @return visible CUDA device count, or zero
     */
    public static int deviceCount() {
        return STATE.deviceCount();
    }

    /**
     * JNI entry point implemented in {@code native/cuda/lc0_bt4_cuda_jni.cu}.
     *
     * @return visible CUDA device count
     */
    private static native int nativeDeviceCount();
}
