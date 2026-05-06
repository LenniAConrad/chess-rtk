package chess.nn.lc0.bt4.oneapi;

import java.nio.file.Path;

import chess.nn.lc0.bt4.NativeBackendOps;
import chess.nn.lc0.bt4.Network;

/**
 * Optional oneAPI backend for LC0 BT4 policy+value inference.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Backend implements AutoCloseable {

    private final long handle;

    private final Network.Info info;

    private Backend(long handle, Network.Info info) {
        this.handle = handle;
        this.info = info;
    }

    public static boolean isAvailable() {
        return Support.isAvailable();
    }

    public static Backend create(Path weightsBin) {
        NativeBackendOps.Created created = NativeBackendOps.create(
                weightsBin,
                Backend::nativeCreate,
                Backend::nativeGetName,
                Backend::nativeGetInfo,
                Backend::nativeDestroy,
                "Failed to create BT4 oneAPI evaluator (no device / init failed).",
                "BT4 oneAPI evaluator returned invalid info.");
        return new Backend(created.handle(), created.info());
    }

    public Network.Info info() {
        return info;
    }

    public Network.Prediction predictEncoded(float[] encodedPlanes) {
        return NativeBackendOps.predictEncoded(handle, info, encodedPlanes, Backend::nativePredict);
    }

    @Override
    public void close() {
        NativeBackendOps.destroy(handle, Backend::nativeDestroy);
    }

    private static native long nativeCreate(String weightsPath);

    private static native void nativeDestroy(long handle);

    private static native String nativeGetName(long handle);

    private static native long[] nativeGetInfo(long handle);

    private static native float nativePredict(long handle, float[] encodedPlanes, float[] outPolicy, float[] outWdl);
}
