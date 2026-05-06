package chess.nn.lc0.bt4.rocm;

import chess.gpu.SharedLibrarySupport;

/**
 * Optional ROCm support for LC0 BT4 native inference.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Support {

    private static final String LIB_BASE_NAME = "lc0_rocm";

    private static final String ENV_ROCM_LIB = "CRTK_ROCM_LIB";

    private static final String DIR_NATIVE_ROCM = "native/rocm";

    private static final SharedLibrarySupport.State STATE =
            SharedLibrarySupport.load(
                    new String[] { LIB_BASE_NAME },
                    ENV_ROCM_LIB,
                    DIR_NATIVE_ROCM,
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
