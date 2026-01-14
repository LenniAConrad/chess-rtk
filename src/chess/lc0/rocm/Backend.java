package chess.lc0.rocm;

import java.nio.file.Path;

import chess.lc0.Network;

/**
 * Optional ROCm (AMD) backend (JNI) for LC0 policy+value inference.
 *
 * <p>This uses a native shared library ({@code lc0j_rocm}) and will only be used when:
 * <ul>
 *   <li>the library is loadable (see {@link Support})</li>
 *   <li>a ROCm device is present</li>
 * </ul>
 *
 * <p>{@link Network#load(Path)} selects this backend automatically when {@code -Ducicli.lc0.backend=auto} and ROCm is available.
 *
 * <p>This class is a thin wrapper around native code. It owns native resources and must be closed.
 *
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class Backend implements AutoCloseable {

    /**
     * Native handle to the ROCm evaluator instance (opaque pointer stored as a {@code long}).
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
     * Returns {@code true} if the JNI library loaded and at least one ROCm device is present.
     *
     * @return {@code true} when ROCm inference is available
     */
    public static boolean isAvailable() {
        return Support.isAvailable();
    }

    /**
     * Creates a ROCm evaluator from an LC0J {@code .bin} weights file.
     *
     * @param weightsBin path to {@code LC0J} binary weights
     * @return evaluator instance owning device resources
     * @throws IllegalStateException if initialization fails
     */
    public static Backend create(Path weightsBin) {
        long h = nativeCreate(weightsBin.toAbsolutePath().toString());
        if (h == 0L) {
            throw new IllegalStateException("Failed to create ROCm evaluator (no device / init failed).");
        }
        long[] meta = nativeGetInfo(h);
        if (meta == null || meta.length < 7) {
            nativeDestroy(h);
            throw new IllegalStateException("ROCm evaluator returned invalid info.");
        }
        Network.Info info = new Network.Info(
                (int) meta[0],
                (int) meta[1],
                (int) meta[2],
                (int) meta[3],
                (int) meta[4],
                (int) meta[5],
                meta[6]);
        return new Backend(h, info);
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
        if (encodedPlanes.length != info.inputChannels() * 64) {
            throw new IllegalArgumentException("Encoded input must be " + (info.inputChannels() * 64) + " floats.");
        }
        float[] policy = new float[info.policySize()];
        float[] wdl = new float[3];
        float value = nativePredict(handle, encodedPlanes, policy, wdl);
        return new Network.Prediction(policy, wdl, value);
    }

    /**
     * Releases native resources (device memory).
     */
    @Override
    public void close() {
        nativeDestroy(handle);
    }

    /**
     * JNI entry point implemented in {@code native/rocm/lc0j_rocm_jni.hip}.
     *
     * @param weightsPath absolute path to the LC0J weights file
     * @return native handle or zero on failure
     */
    private static native long nativeCreate(String weightsPath);

    /**
     * JNI entry point implemented in {@code native/rocm/lc0j_rocm_jni.hip}.
     *
     * @param handle native handle to destroy
     */
    private static native void nativeDestroy(long handle);

    /**
     * JNI entry point implemented in {@code native/rocm/lc0j_rocm_jni.hip}.
     *
     * @param handle native handle to inspect
     * @return {@code [inputC, trunkC, blocks, policyC, valueC, policySize, paramCount]}
     */
    private static native long[] nativeGetInfo(long handle);

    /**
     * JNI entry point implemented in {@code native/rocm/lc0j_rocm_jni.hip}.
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
