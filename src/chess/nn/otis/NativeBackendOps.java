package chess.nn.otis;

import java.nio.file.Path;
import java.util.function.LongConsumer;

/**
 * Shared helpers for thin JNI-backed OTIS policy/WDL backends.
 *
 * <p>The optional CUDA/ROCm/oneAPI OTIS backends are tiny wrappers around native
 * code that all expose the same JNI surface (create, get-info, get-name,
 * predict, destroy). This class centralizes the Java-side glue so each backend
 * only declares its {@code native} entry points.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class NativeBackendOps {

    /**
     * Board squares per OTIS input plane.
     */
    private static final int SQUARES = 64;

    /**
     * Number of metadata longs returned by {@code nativeGetInfo}.
     */
    private static final int INFO_FIELDS = 5;

    /**
     * Creates a new native backend ops instance.
     */
    private NativeBackendOps() {
    }

    /**
     * Common result of native backend creation.
     *
     * @param handle native backend handle
     * @param info parsed model metadata
     */
    public record Created(
        /**
         * Stores the handle.
         */
        long handle,
        /**
         * Stores the info.
         */
        Model.Info info
    ) {
    }

    /**
     * Functional interface for native evaluator creation.
     */
    @FunctionalInterface
    public interface HandleCreator {

        /**
         * Creates a native evaluator for the given weights path.
         *
         * @param weightsPath absolute path to the weights file
         * @return native handle, or zero on failure
         */
        long create(String weightsPath);
    }

    /**
     * Functional interface for native metadata lookup.
     */
    @FunctionalInterface
    public interface InfoReader {

        /**
         * Reads model metadata from the native backend.
         *
         * @param handle native backend handle
         * @return raw metadata array, or {@code null} on failure
         */
        long[] read(long handle);
    }

    /**
     * Functional interface for native model-name lookup.
     */
    @FunctionalInterface
    public interface NameReader {

        /**
         * Reads the model name from the native backend.
         *
         * @param handle native backend handle
         * @return model name, or {@code null} when unavailable
         */
        String read(long handle);
    }

    /**
     * Functional interface for native prediction.
     */
    @FunctionalInterface
    public interface Predictor {

        /**
         * Runs prediction on already-encoded planes.
         *
         * @param handle native backend handle
         * @param encodedPlanes encoded input planes
         * @param outPolicy output policy buffer
         * @param outWdl output WDL buffer
         * @return scalar value prediction
         */
        float predict(long handle, float[] encodedPlanes, float[] outPolicy, float[] outWdl);
    }

    /**
     * Runs the common OTIS backend creation flow.
     *
     * @param weightsBin path to the weights file
     * @param creator native handle creator
     * @param infoReader native metadata reader
     * @param nameReader native model-name reader
     * @param destroyer native destroy function
     * @param createFailure message used when creation fails
     * @param infoFailure message used when metadata is invalid
     * @return created native backend handle and metadata
     */
    public static Created create(
            Path weightsBin,
            HandleCreator creator,
            InfoReader infoReader,
            NameReader nameReader,
            LongConsumer destroyer,
            String createFailure,
            String infoFailure) {
        long handle = creator.create(weightsBin.toAbsolutePath().toString());
        if (handle == 0L) {
            throw new IllegalStateException(createFailure);
        }
        long[] meta = infoReader.read(handle);
        if (meta == null || meta.length < INFO_FIELDS) {
            destroyer.accept(handle);
            throw new IllegalStateException(infoFailure);
        }
        String name = nameReader.read(handle);
        return new Created(handle, new Model.Info(
                name == null || name.isBlank() ? "otis" : name,
                (int) meta[0],
                (int) meta[1],
                (int) meta[2],
                (int) meta[3],
                (int) meta[4]));
    }

    /**
     * Runs the common Java-side prediction validation and wrapping.
     *
     * @param handle native backend handle
     * @param info loaded model metadata
     * @param encodedPlanes encoded input planes
     * @param predictor native predictor
     * @return wrapped prediction
     */
    public static Model.Prediction predictEncoded(
            long handle,
            Model.Info info,
            float[] encodedPlanes,
            Predictor predictor) {
        int expected = info.inputPlanes() * SQUARES;
        if (encodedPlanes.length != expected) {
            throw new IllegalArgumentException("Encoded input must be " + expected + " floats.");
        }
        float[] policy = new float[info.policySize()];
        float[] wdl = new float[3];
        float value = predictor.predict(handle, encodedPlanes, policy, wdl);
        return new Model.Prediction(policy, wdl, value);
    }

    /**
     * Releases a native handle through the provided destroy function.
     *
     * @param handle native backend handle
     * @param destroyer native destroy function
     */
    public static void destroy(long handle, LongConsumer destroyer) {
        destroyer.accept(handle);
    }
}
