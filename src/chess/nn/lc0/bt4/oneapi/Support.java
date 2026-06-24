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
     * Native oneAPI library base name.
     */
    private static final String LIB_BASE_NAME = "lc0_oneapi";

    /**
     * Environment override for the oneAPI library path.
     */
    private static final String ENV_ONEAPI_LIB = "CRTK_ONEAPI_LIB";

    /**
     * Repository-relative oneAPI native library directory.
     */
    private static final String DIR_NATIVE_ONEAPI = "native/oneapi";

    /**
     * Loaded native oneAPI backend state.
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
     * @return true when the backend is available
     */
    public static boolean isAvailable() {
        return STATE.deviceCount() > 0;
    }

    /**
     * Is loaded.
     * @return true when is loaded
     */
    public static boolean isLoaded() {
        return STATE.loaded();
    }

    /**
     * Device count.
     * @return device count
     */
    public static int deviceCount() {
        return STATE.deviceCount();
    }

    /**
     * Native device count.
     * @return native device count
     */
    private static native int nativeDeviceCount();
}
