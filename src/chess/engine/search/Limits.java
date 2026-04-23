package chess.engine.search;

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
public record Limits(
    /**
     * Maximum iterative-deepening depth in plies. The searcher attempts each
     * depth from one up to this value unless another budget stops it first.
     */
    int depth,
    /**
     * Maximum visited-node budget. A value of zero disables the node budget.
     */
    long maxNodes,
    /**
     * Maximum wall-clock budget in milliseconds. A value of zero disables the
     * time budget.
     */
    long maxDurationMillis
) {

    /**
     * Default iterative-deepening depth for CLI calls and simple API use.
     */
    public static final int DEFAULT_DEPTH = 3;

    /**
     * Default visited-node budget for CLI calls and simple API use.
     */
    public static final long DEFAULT_MAX_NODES = 250_000L;

    /**
     * Default wall-clock budget for CLI calls and simple API use.
     */
    public static final long DEFAULT_MAX_DURATION_MILLIS = 5_000L;

    /**
     * Validates the search limits.
     *
     * @param depth maximum iterative-deepening depth in plies
     * @param maxNodes maximum visited-node budget, or zero for unlimited
     * @param maxDurationMillis maximum wall-clock budget, or zero for unlimited
     * @throws IllegalArgumentException if depth is outside the supported range or
     *         either budget is negative
     */
    public Limits {
        if (depth < 1 || depth > AlphaBeta.MAX_DEPTH) {
            throw new IllegalArgumentException("depth must be between 1 and " + AlphaBeta.MAX_DEPTH);
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
     * @return limits using the built-in depth, node, and time defaults
     */
    public static Limits defaults() {
        return new Limits(DEFAULT_DEPTH, DEFAULT_MAX_NODES, DEFAULT_MAX_DURATION_MILLIS);
    }
}
