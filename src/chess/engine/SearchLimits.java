package chess.engine;

/**
 * Resource limits for the built-in searcher.
 *
 * <p>
 * A zero {@code maxNodes} or {@code maxDurationMillis} value means "unlimited"
 * for that dimension. Depth is always bounded because the implementation uses
 * fixed principal-variation scratch arrays.
 * </p>
 *
 * @param depth maximum iterative-deepening depth in plies
 * @param maxNodes maximum visited search nodes, or zero for unlimited
 * @param maxDurationMillis maximum search time in milliseconds, or zero for unlimited
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public record SearchLimits(
    /**
     * Stores the depth.
     */
    int depth,
    /**
     * Stores the max nodes.
     */
    long maxNodes,
    /**
     * Stores the max duration millis.
     */
    long maxDurationMillis
) {

    /**
     * Default depth for CLI and simple API calls.
     */
    public static final int DEFAULT_DEPTH = 3;

    /**
     * Default node budget for CLI and simple API calls.
     */
    public static final long DEFAULT_MAX_NODES = 250_000L;

    /**
     * Default wall-clock budget for CLI and simple API calls.
     */
    public static final long DEFAULT_MAX_DURATION_MILLIS = 5_000L;

    /**
     * Validates the search limits.
     */
    public SearchLimits {
        if (depth < 1 || depth > Searcher.MAX_DEPTH) {
            throw new IllegalArgumentException("depth must be between 1 and " + Searcher.MAX_DEPTH);
        }
        if (maxNodes < 0L) {
            throw new IllegalArgumentException("maxNodes must be non-negative");
        }
        if (maxDurationMillis < 0L) {
            throw new IllegalArgumentException("maxDurationMillis must be non-negative");
        }
    }

    /**
     * Returns the default limit set.
     *
     * @return default search limits
     */
    public static SearchLimits defaults() {
        return new SearchLimits(DEFAULT_DEPTH, DEFAULT_MAX_NODES, DEFAULT_MAX_DURATION_MILLIS);
    }
}
