package chess.nn.otis.rocm;

import java.nio.file.Path;

import chess.nn.otis.Model;
import chess.nn.otis.NativeBackendOps;

/**
 * Optional ROCm (AMD) backend (JNI) for OTIS policy/WDL inference.
 *
 * <p>This uses a native shared library ({@code otis_rocm}) and will only be used when:
 * <ul>
 *   <li>the library is loadable (see {@link Support})</li>
 *   <li>a ROCm device is present</li>
 * </ul>
 *
 * <p>{@link Model#load(Path)} selects this backend automatically when
 * {@code -Dcrtk.otis.backend=auto} and ROCm is available (current system properties only).
 *
 * <p>The native backend reproduces the OTIS network forward pass (square tokens, typed
 * tactical sheaf, readout, policy head, and WDL head) from already-encoded planes. The
 * per-legal-move policy refinement that the pure-Java path layers on top is not applied
 * by the native backend because it has no legal-move generator; the returned policy is
 * therefore the raw policy-head logits.
 *
 * <p>This class is a thin wrapper around native code. It owns native resources and must be closed.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Backend implements AutoCloseable {

    /**
     * Native handle to the ROCm evaluator instance (opaque pointer stored as a {@code long}).
     */
    private final long handle;

    /**
     * Metadata for the loaded model.
     */
    private final Model.Info info;

    /**
     * Constructor used internally after a successful native creation.
     *
     * @param handle native JNI handle
     * @param info parsed model metadata
     */
    private Backend(long handle, Model.Info info) {
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
     * Creates a ROCm evaluator from a ChessRTK OTIS {@code .bin} weights file.
     *
     * @param weightsBin path to ChessRTK OTIS binary weights
     * @return evaluator instance owning device resources
     * @throws IllegalStateException if initialization fails
     */
    public static Backend create(Path weightsBin) {
        NativeBackendOps.Created created = NativeBackendOps.create(
                weightsBin,
                Backend::nativeCreate,
                Backend::nativeGetInfo,
                Backend::nativeGetName,
                Backend::nativeDestroy,
                "Failed to create ROCm OTIS evaluator (no device / init failed).",
                "ROCm OTIS evaluator returned invalid info.");
        return new Backend(created.handle(), created.info());
    }

    /**
     * Returns basic model metadata.
     *
     * @return parsed model information
     */
    public Model.Info info() {
        return info;
    }

    /**
     * Runs one forward pass on an already-encoded OTIS input.
     *
     * @param encodedPlanes input planes, shape {@code [inputPlanes * 64]}
     * @return policy logits, WDL probabilities, and scalar {@code W-L} value
     */
    public Model.Prediction predictEncoded(float[] encodedPlanes) {
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
     * JNI entry point implemented in {@code native/rocm/otis_rocm_jni.hip}.
     *
     * @param weightsPath absolute path to the OTIS weights file
     * @return native handle or zero on failure
     */
    private static native long nativeCreate(String weightsPath);

    /**
     * JNI entry point implemented in {@code native/rocm/otis_rocm_jni.hip}.
     *
     * @param handle native handle to destroy
     */
    private static native void nativeDestroy(long handle);

    /**
     * JNI entry point implemented in {@code native/rocm/otis_rocm_jni.hip}.
     *
     * @param handle native handle to inspect
     * @return {@code [inputPlanes, trunkChannels, blocks, policySize, paramCount]}
     */
    private static native long[] nativeGetInfo(long handle);

    /**
     * JNI entry point implemented in {@code native/rocm/otis_rocm_jni.hip}.
     *
     * @param handle native handle to inspect
     * @return loaded model name
     */
    private static native String nativeGetName(long handle);

    /**
     * JNI entry point implemented in {@code native/rocm/otis_rocm_jni.hip}.
     *
     * <p>Writes {@code outPolicy} (length {@code policySize}) and {@code outWdl} (length 3).
     *
     * @param handle native handle
     * @param encodedPlanes OTIS input planes
     * @param outPolicy array to receive policy logits
     * @param outWdl array to receive raw WDL
     * @return scalar {@code W-L} value
     */
    private static native float nativePredict(long handle, float[] encodedPlanes, float[] outPolicy, float[] outWdl);
}
