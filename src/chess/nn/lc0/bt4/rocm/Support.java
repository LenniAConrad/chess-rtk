package chess.nn.lc0.bt4.rocm;

import chess.gpu.SharedLibrarySupport;

/**
 * Optional ROCm support for LC0 BT4 native inference.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Support {

    /**
     * L i b  b a s e  n a m e.
     */
    private static final String LIB_BASE_NAME = "lc0_rocm";

    /**
     * E n v  r o c m  l i b.
     */
    private static final String ENV_ROCM_LIB = "CRTK_ROCM_LIB";

    /**
     * D i r  n a t i v e  r o c m.
     */
    private static final String DIR_NATIVE_ROCM = "native/rocm";

    /**
     * S t a t e.
     */
    private static final SharedLibrarySupport.State STATE =
            SharedLibrarySupport.load(
                    new String[] { LIB_BASE_NAME },
                    ENV_ROCM_LIB,
                    DIR_NATIVE_ROCM,
                    Support::nativeDeviceCount);

    /**
     * Support.
     */
    private Support() {
    }

    /**
     * Returns whether the backend is available.
     * @return true when the backend is available */
    public static boolean isAvailable() {
        return STATE.deviceCount() > 0;
    }

    /**
     * Is loaded.
     * @return true when is loaded */
    public static boolean isLoaded() {
        return STATE.loaded();
    }

    /**
     * Device count.
     * @return device count result */
    public static int deviceCount() {
        return STATE.deviceCount();
    }

    /**
     * Native device count.
     * @return native device count result */
    private static native int nativeDeviceCount();
}
