package chess.nn.lc0.bt4;

import java.nio.file.Path;
import java.util.function.LongConsumer;

/**
 * Shared helpers for thin JNI-backed BT4 backends.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class NativeBackendOps {

    /**
     * Utility class, prevents instantiation.
     */
    private NativeBackendOps() {
    }

    /**
     * Common result of native backend creation.
     *
     * @param handle native backend handle
     * @param info parsed network metadata
     */
    public record Created(long handle, Network.Info info) {
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
     * Functional interface for native model-name lookup.
     */
    @FunctionalInterface
    public interface NameReader {
        /**
         * Reads the native model name.
         *
         * @param handle native backend handle
         * @return model name, or {@code null}
         */
        String read(long handle);
    }

    /**
     * Functional interface for native metadata lookup.
     */
    @FunctionalInterface
    public interface InfoReader {
        /**
         * Reads network metadata from the native backend.
         *
         * @param handle native backend handle
         * @return raw metadata array, or {@code null} on failure
         */
        long[] read(long handle);
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
     * Runs the common BT4 backend creation flow.
     *
     * @param weightsBin path to the weights file
     * @param creator native handle creator
     * @param nameReader native model-name reader
     * @param infoReader native metadata reader
     * @param destroyer native destroy function
     * @param createFailure message used when creation fails
     * @param infoFailure message used when metadata is invalid
     * @return created native backend handle and metadata
     */
    public static Created create(
            Path weightsBin,
            HandleCreator creator,
            NameReader nameReader,
            InfoReader infoReader,
            LongConsumer destroyer,
            String createFailure,
            String infoFailure) {
        long handle = creator.create(weightsBin.toAbsolutePath().toString());
        if (handle == 0L) {
            throw new IllegalStateException(createFailure);
        }
        String name = nameReader.read(handle);
        long[] meta = infoReader.read(handle);
        if (meta == null || meta.length < 7) {
            destroyer.accept(handle);
            throw new IllegalStateException(infoFailure);
        }
        if (name == null || name.isBlank()) {
            name = "lc0-bt4-native";
        }
        return new Created(handle, new Network.Info(
                name,
                (int) meta[0],
                (int) meta[1],
                (int) meta[2],
                (int) meta[3],
                (int) meta[4],
                (int) meta[5],
                meta[6]));
    }

    /**
     * Runs the common Java-side prediction validation and wrapping.
     *
     * @param handle native backend handle
     * @param info loaded network metadata
     * @param encodedPlanes encoded input planes
     * @param predictor native predictor
     * @return wrapped prediction result
     */
    public static Network.Prediction predictEncoded(
            long handle,
            Network.Info info,
            float[] encodedPlanes,
            Predictor predictor) {
        int expected = info.inputChannels() * info.tokens();
        if (encodedPlanes == null || encodedPlanes.length != expected) {
            throw new IllegalArgumentException("Encoded input must be " + expected + " floats.");
        }
        float[] policy = new float[info.policySize()];
        float[] wdl = new float[3];
        float value = predictor.predict(handle, encodedPlanes, policy, wdl);
        return new Network.Prediction(policy, wdl, value);
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
