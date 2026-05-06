package chess.nn.lc0.bt4.oneapi;

import chess.gpu.SharedLibrarySupport;

/**
 * Optional oneAPI support for LC0 BT4 native inference.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Support {

    private static final String LIB_BASE_NAME = "lc0_oneapi";

    private static final String ENV_ONEAPI_LIB = "CRTK_ONEAPI_LIB";

    private static final String DIR_NATIVE_ONEAPI = "native/oneapi";

    private static final SharedLibrarySupport.State STATE =
            SharedLibrarySupport.load(
                    new String[] { LIB_BASE_NAME },
                    ENV_ONEAPI_LIB,
                    DIR_NATIVE_ONEAPI,
                    Support::nativeDeviceCount);

    private Support() {
    }

    public static boolean isAvailable() {
        return STATE.deviceCount() > 0;
    }

    public static boolean isLoaded() {
        return STATE.loaded();
    }

    public static int deviceCount() {
        return STATE.deviceCount();
    }

    private static native int nativeDeviceCount();
}
