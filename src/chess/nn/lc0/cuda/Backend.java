package chess.nn.lc0.cuda;

import java.nio.file.Path;

import chess.nn.lc0.NativeBackendOps;
import chess.nn.lc0.Network;

/**
 * Optional CUDA backend (JNI) for LC0 policy+value inference.
 *
 * <p>This uses a native shared library ({@code lc0j_cuda}) and will only be used when:
 * <ul>
 *   <li>the library is loadable (see {@link Support})</li>
 *   <li>a CUDA device is present</li>
 * </ul>
 *
 * <p>{@link Network#load(Path)} selects this backend automatically when {@code -Dcrtk.lc0.backend=auto} and CUDA is available
 * (no legacy system properties).
 *
 * <p>This class is a thin wrapper around native code. It owns native resources and must be closed.
 *
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class Backend implements AutoCloseable {
    
    /**
     * Native handle to the CUDA evaluator instance (opaque pointer stored as a {@code long}).
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
     * Returns {@code true} if the JNI library loaded and at least one CUDA device is present.
     *
     * @return {@code true} when CUDA inference is available
     */
    public static boolean isAvailable() {
        return Support.isAvailable();
    }

    /**
     * Creates a CUDA evaluator from an LC0J {@code .bin} weights file.
     *
     * @param weightsBin path to {@code LC0J} binary weights
     * @return evaluator instance owning device resources
     * @throws IllegalStateException if initialization fails
     */
    public static Backend create(Path weightsBin) {
        NativeBackendOps.Created created = NativeBackendOps.create(
                weightsBin,
                Backend::nativeCreate,
                Backend::nativeGetInfo,
                Backend::nativeDestroy,
                "Failed to create CUDA evaluator (no device / init failed).",
                "CUDA evaluator returned invalid info.");
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
     * JNI entry point implemented in {@code native/cuda/lc0j_cuda_jni.cu}.
     *
     * @param weightsPath absolute path to the LC0J weights file
     * @return native handle or zero on failure
     */
    private static native long nativeCreate(String weightsPath);

    /**
     * JNI entry point implemented in {@code native/cuda/lc0j_cuda_jni.cu}.
     *
     * @param handle native handle to destroy
     */
    private static native void nativeDestroy(long handle);

    /**
     * JNI entry point implemented in {@code native/cuda/lc0j_cuda_jni.cu}.
     *
     * @param handle native handle to inspect
     * @return {@code [inputC, trunkC, blocks, policyC, valueC, policySize, paramCount]}
     */
    private static native long[] nativeGetInfo(long handle);

    /**
     * JNI entry point implemented in {@code native/cuda/lc0j_cuda_jni.cu}.
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
