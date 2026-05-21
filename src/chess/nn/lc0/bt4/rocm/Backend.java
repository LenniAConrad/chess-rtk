package chess.nn.lc0.bt4.rocm;

import java.nio.file.Path;

import chess.nn.lc0.bt4.NativeBackendOps;
import chess.nn.lc0.bt4.Network;

/**
 * Optional ROCm backend for LC0 BT4 policy+value inference.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Backend implements AutoCloseable {

    /**
     * Native backend handle.
     */
    private final long handle;

    /**
     * Network metadata.
     */
    private final Network.Info info;

    /**
     * Backend.
     * @param handle native backend handle
     * @param info network metadata */
    private Backend(long handle, Network.Info info) {
        this.handle = handle;
        this.info = info;
    }

    /**
     * Returns whether the backend is available.
     * @return true when the backend is available */
    public static boolean isAvailable() {
        return Support.isAvailable();
    }

    /**
     * Creates a backend instance.
     * @param weightsBin weights binary path
     * @return created instance */
    public static Backend create(Path weightsBin) {
        NativeBackendOps.Created created = NativeBackendOps.create(
                weightsBin,
                Backend::nativeCreate,
                Backend::nativeGetName,
                Backend::nativeGetInfo,
                Backend::nativeDestroy,
                "Failed to create BT4 ROCm evaluator (no device / init failed).",
                "BT4 ROCm evaluator returned invalid info.");
        return new Backend(created.handle(), created.info());
    }

    /**
     * Network metadata.
     * @return backend metadata */
    public Network.Info info() {
        return info;
    }

    /**
     * Runs inference on encoded input planes.
     * @param encodedPlanes encoded input planes
     * @return network prediction */
    public Network.Prediction predictEncoded(float[] encodedPlanes) {
        return NativeBackendOps.predictEncoded(handle, info, encodedPlanes, Backend::nativePredict);
    }

    @Override
    public void close() {
        NativeBackendOps.destroy(handle, Backend::nativeDestroy);
    }

    /**
     * Creates a native backend instance.
     * @param weightsPath path to the weights file
     * @return native backend handle */
    private static native long nativeCreate(String weightsPath);

    /**
     * Destroys a native backend instance.
     * @param handle native backend handle */
    private static native void nativeDestroy(long handle);

    /**
     * Returns the native backend name.
     * @param handle native backend handle
     * @return native backend name */
    private static native String nativeGetName(long handle);

    /**
     * Returns native backend metadata.
     * @param handle native backend handle
     * @return native backend metadata */
    private static native long[] nativeGetInfo(long handle);

    /**
     * Runs a native backend prediction.
     * @param handle native backend handle
     * @param encodedPlanes encoded input planes
     * @param outPolicy policy output buffer
     * @param outWdl WDL output buffer
     * @return predicted value score */
    private static native float nativePredict(long handle, float[] encodedPlanes, float[] outPolicy, float[] outWdl);
}
