package chess.nn.lc0.cnn.oneapi;

import java.nio.file.Path;

import chess.nn.lc0.cnn.NativeBackendOps;
import chess.nn.lc0.cnn.Network;

/**
 * Optional oneAPI (Intel) backend (JNI) for LC0 policy+value inference.
 *
 * <p>This uses a native shared library ({@code lc0_oneapi}) and will only be used when:
 * <ul>
 *   <li>the library is loadable (see {@link Support})</li>
 *   <li>an Intel GPU is present</li>
 * </ul>
 *
 * <p>{@link Network#load(Path)} selects this backend automatically when {@code -Dcrtk.lc0.backend=auto} and oneAPI is available
 * (current system properties only).
 *
 * <p>This class is a thin wrapper around native code. It owns native resources and must be closed.
 *
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class Backend implements AutoCloseable {

    /**
     * Native handle to the oneAPI evaluator instance (opaque pointer stored as a {@code long}).
     */
    private final long handle;

    /**
     * Metadata for the loaded network.
     */
    private final Network.Info info;

    /**
     * Constructor used internally after a successful native creation.
     *
     * @param handle native JNI handle
     * @param info parsed network metadata
     */
    private Backend(long handle, Network.Info info) {
        this.handle = handle;
        this.info = info;
    }

    /**
     * Returns {@code true} if the JNI library loaded and at least one Intel GPU is present.
     *
     * @return {@code true} when oneAPI inference is available
     */
    public static boolean isAvailable() {
        return Support.isAvailable();
    }

    /**
     * Creates a oneAPI evaluator from a ChessRTK LC0 CNN {@code .bin} weights file.
     *
     * @param weightsBin path to ChessRTK LC0 CNN binary weights
     * @return evaluator instance owning device resources
     * @throws IllegalStateException if initialization fails
     */
    public static Backend create(Path weightsBin) {
        NativeBackendOps.Created created = NativeBackendOps.create(
                weightsBin,
                Backend::nativeCreate,
                Backend::nativeGetInfo,
                Backend::nativeDestroy,
                "Failed to create oneAPI evaluator (no device / init failed).",
                "oneAPI evaluator returned invalid info.");
        return new Backend(created.handle(), created.info());
    }

    /**
     * Returns basic network metadata.
     *
     * @return parsed network information
     */
    public Network.Info info() {
        return info;
    }

    /**
     * Runs one forward pass on an already-encoded LC0 112-plane input.
     *
     * @param encodedPlanes input planes, shape {@code [inputChannels * 64]}
     * @return policy logits, WDL probabilities, and scalar {@code W-L} value
     */
    public Network.Prediction predictEncoded(float[] encodedPlanes) {
        return NativeBackendOps.predictEncoded(handle, info, encodedPlanes, Backend::nativePredict);
    }

    /**
     * Releases native resources (device memory).
     */
    @Override
    public void close() {
        NativeBackendOps.destroy(handle, Backend::nativeDestroy);
    }

    /**
     * JNI entry point implemented in {@code native/oneapi/lc0_cnn_oneapi_jni.cpp}.
     *
     * @param weightsPath absolute path to the LC0 CNN weights file
     * @return native handle or zero on failure
     */
    private static native long nativeCreate(String weightsPath);

    /**
     * JNI entry point implemented in {@code native/oneapi/lc0_cnn_oneapi_jni.cpp}.
     *
     * @param handle native handle to destroy
     */
    private static native void nativeDestroy(long handle);

    /**
     * JNI entry point implemented in {@code native/oneapi/lc0_cnn_oneapi_jni.cpp}.
     *
     * @param handle native handle to inspect
     * @return {@code [inputC, trunkC, blocks, policyC, valueC, policySize, paramCount]}
     */
    private static native long[] nativeGetInfo(long handle);

    /**
     * JNI entry point implemented in {@code native/oneapi/lc0_cnn_oneapi_jni.cpp}.
     *
     * <p>Writes {@code outPolicy} (length {@code policySize}) and {@code outWdl} (length 3).
     *
     * @param handle native handle
     * @param encodedPlanes LC0 input planes
     * @param outPolicy array to receive policy logits
     * @param outWdl array to receive raw WDL
     * @return scalar {@code W-L} value
     */
    private static native float nativePredict(long handle, float[] encodedPlanes, float[] outPolicy, float[] outWdl);
}
