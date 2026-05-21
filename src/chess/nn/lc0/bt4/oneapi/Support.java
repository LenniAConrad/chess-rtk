package chess.nn.lc0.bt4.oneapi;

import chess.gpu.SharedLibrarySupport;

/**
 * Optional oneAPI support for LC0 BT4 native inference.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Support {

    /**
     * L i b  b a s e  n a m e.
     */
    private static final String LIB_BASE_NAME = "lc0_oneapi";

    /**
     * E n v  o n e a p i  l i b.
     */
    private static final String ENV_ONEAPI_LIB = "CRTK_ONEAPI_LIB";

    /**
     * D i r  n a t i v e  o n e a p i.
     */
    private static final String DIR_NATIVE_ONEAPI = "native/oneapi";

    /**
     * S t a t e.
     */
    private static final SharedLibrarySupport.State STATE =
            SharedLibrarySupport.load(
                    new String[] { LIB_BASE_NAME },
                    ENV_ONEAPI_LIB,
                    DIR_NATIVE_ONEAPI,
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
