package chess.nn.lc0.bt4.cuda;

import java.nio.file.Path;

import chess.nn.lc0.bt4.NativeBackendOps;
import chess.nn.lc0.bt4.Network;

/**
 * Optional CUDA backend for LC0 BT4 policy+value inference.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Backend implements AutoCloseable {

    /**
     * Native evaluator handle.
     */
    private final long handle;

    /**
     * Loaded network metadata.
     */
    private final Network.Info info;

    /**
     * Creates a wrapper around a native evaluator.
     *
     * @param handle native handle
     * @param info network metadata
     */
    private Backend(long handle, Network.Info info) {
        this.handle = handle;
        this.info = info;
    }

    /**
     * @return {@code true} if CUDA BT4 inference is available
     */
    public static boolean isAvailable() {
        return Support.isAvailable();
    }

    /**
     * Creates a CUDA evaluator from a compact BT4 weights file.
     *
     * @param weightsBin BT4 weights file
     * @return backend instance
     */
    public static Backend create(Path weightsBin) {
        NativeBackendOps.Created created = NativeBackendOps.create(
                weightsBin,
                Backend::nativeCreate,
                Backend::nativeGetName,
                Backend::nativeGetInfo,
                Backend::nativeDestroy,
                "Failed to create BT4 CUDA evaluator (no device / init failed).",
                "BT4 CUDA evaluator returned invalid info.");
        return new Backend(created.handle(), created.info());
    }

    /**
     * @return loaded network metadata
     */
    public Network.Info info() {
        return info;
    }

    /**
     * Runs one forward pass on already-encoded BT4 input planes.
     *
     * @param encodedPlanes channel-major input planes
     * @return prediction
     */
    public Network.Prediction predictEncoded(float[] encodedPlanes) {
        return NativeBackendOps.predictEncoded(handle, info, encodedPlanes, Backend::nativePredict);
    }

    /**
     * Releases native resources.
     */
    @Override
    public void close() {
        NativeBackendOps.destroy(handle, Backend::nativeDestroy);
    }

    private static native long nativeCreate(String weightsPath);

    private static native void nativeDestroy(long handle);

    private static native String nativeGetName(long handle);

    /**
     * @return {@code [inputC, tokens, embedding, encoders, heads, policySize, paramCount]}
     */
    private static native long[] nativeGetInfo(long handle);

    private static native float nativePredict(long handle, float[] encodedPlanes, float[] outPolicy, float[] outWdl);
}
