package chess.nn.perft.oneapi;

/**
 * Optional oneAPI/SYCL backend (JNI) for split-depth bulk perft.
 *
 * <p>
 * Each call computes {@code perft(remainingDepth)} for a batch of frontier
 * positions packed by {@code chess.debug.gpu.PositionCodec}. The library is
 * loaded lazily by {@link Support}; callers must check {@link #isAvailable()}
 * before invoking {@link #bulkPerft(long[], int, int)}.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Backend {

    /**
     * Utility class; prevents instantiation.
     */
    private Backend() {
    }

    /**
     * @return {@code true} when the oneAPI perft backend is loaded and a device is present
     */
    public static boolean isAvailable() {
        return Support.isAvailable();
    }

    /**
     * Counts {@code perft(remainingDepth)} for each packed frontier position.
     *
     * @param packed packed positions, {@code count * PositionCodec.WORDS} longs
     * @param count number of frontier positions
     * @param remainingDepth non-negative depth below each frontier position
     * @return per-position leaf-node counts, or {@code null} on native failure
     */
    public static long[] bulkPerft(long[] packed, int count, int remainingDepth) {
        return nativeBulkPerft(packed, count, remainingDepth);
    }

    /**
     * Counts detailed perft (7 counters) for each packed frontier position.
     *
     * @param packed packed positions, {@code count * PositionCodec.WORDS} longs
     * @param count number of frontier positions
     * @param remainingDepth non-negative depth below each frontier position
     * @return {@code count * 7} counters per position, or {@code null} on failure
     */
    public static long[] bulkPerftDetailed(long[] packed, int count, int remainingDepth) {
        return nativeBulkPerftDetailed(packed, count, remainingDepth);
    }

    /**
     * JNI entry point implemented in {@code native/oneapi/perft_oneapi_jni.cpp}.
     *
     * @param packed packed frontier positions
     * @param count number of frontier positions
     * @param remainingDepth depth below each frontier position
     * @return per-position leaf-node counts
     */
    private static native long[] nativeBulkPerft(long[] packed, int count, int remainingDepth);

    /**
     * JNI entry point implemented in {@code native/oneapi/perft_oneapi_jni.cpp}.
     *
     * @param packed packed frontier positions
     * @param count number of frontier positions
     * @param remainingDepth depth below each frontier position
     * @return per-position detailed counters ({@code count * 7})
     */
    private static native long[] nativeBulkPerftDetailed(long[] packed, int count, int remainingDepth);
}
