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
 * @param softMillis soft per-move time target for clock games, or zero when
 *        the hard budget is the only deadline
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
    long maxDurationMillis,
    /**
     * Soft per-move target in milliseconds for clock games, always at most
     * {@link #maxDurationMillis} when both are set. Searchers without
     * stability-based time management treat this value as their deadline, so
     * the pair is backward-compatible; with {@link AlphaBeta.Feature#STABILITY_TIME}
     * the searcher modulates it by best-move stability and may run up to the
     * hard budget. Zero disables soft timing.
     */
    long softMillis
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
     * Default planning horizon, in moves, used to convert a remaining game
     * clock into a per-move budget when no explicit {@code movestogo} is known.
     */
    public static final long CLOCK_MOVES_TO_GO = 30L;

    /**
     * Safety reserve in milliseconds kept on the game clock so a search that
     * slightly overshoots its deadline does not lose on time.
     */
    public static final long CLOCK_RESERVE_MILLIS = 50L;

    /**
     * Validates the search limits.
     *
     * @param depth maximum iterative-deepening depth in plies
     * @param maxNodes maximum visited-node budget, or zero for unlimited
     * @param maxDurationMillis maximum wall-clock budget, or zero for unlimited
     * @param softMillis soft per-move time target in milliseconds
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
        if (softMillis < 0L) {
            throw new IllegalArgumentException("softMillis must be non-negative");
        }
        if (softMillis > 0L && maxDurationMillis > 0L && softMillis > maxDurationMillis) {
            throw new IllegalArgumentException("softMillis must not exceed maxDurationMillis");
        }
    }

    /**
     * Creates limits without a soft time target.
     *
     * @param depth maximum iterative-deepening depth in plies
     * @param maxNodes maximum visited-node budget, or zero for unlimited
     * @param maxDurationMillis maximum wall-clock budget, or zero for unlimited
     */
    public Limits(int depth, long maxNodes, long maxDurationMillis) {
        this(depth, maxNodes, maxDurationMillis, 0L);
    }

    /**
     * Returns the default limit set.
     *
     * @return limits using the built-in depth, node, and time defaults
     */
    public static Limits defaults() {
        return new Limits(DEFAULT_DEPTH, DEFAULT_MAX_NODES, DEFAULT_MAX_DURATION_MILLIS);
    }

    /**
     * Converts a remaining game clock into a per-move time budget.
     *
     * <p>
     * This is the single shared clock-to-budget mapping ("time manager") used
     * by the UCI loops for {@code wtime}/{@code btime}/{@code winc}/{@code binc}
     * and by the self-play gauntlet's game-clock mode: spend an even share of
     * the remaining time over a {@code movesToGo} horizon plus half the
     * increment, never more than the remaining time minus the
     * {@link #CLOCK_RESERVE_MILLIS} safety reserve, and always at least one
     * millisecond while time remains.
     * </p>
     *
     * @param remainingMillis remaining clock time in milliseconds
     * @param incrementMillis per-move increment in milliseconds
     * @param movesToGo planning horizon in moves (see {@link #CLOCK_MOVES_TO_GO})
     * @return per-move budget in milliseconds, or zero when no time remains
     */
    public static long clockBudgetMillis(long remainingMillis, long incrementMillis, long movesToGo) {
        if (remainingMillis <= 0L) {
            return 0L;
        }
        long divisor = Math.max(1L, movesToGo);
        long budget = Math.max(1L, remainingMillis / divisor + incrementMillis / 2L);
        long reserveAdjusted = remainingMillis > CLOCK_RESERVE_MILLIS
                ? remainingMillis - CLOCK_RESERVE_MILLIS
                : 1L;
        return Math.min(budget, reserveAdjusted);
    }

    /**
     * Converts a remaining game clock into the hard per-move deadline that
     * pairs with {@link #clockBudgetMillis} as the soft target.
     *
     * <p>
     * The hard deadline allows an unstable search to run past its soft target:
     * four times the soft budget, but never more than a quarter of the
     * remaining clock plus half the increment, and never into the
     * {@link #CLOCK_RESERVE_MILLIS} safety reserve.
     * </p>
     *
     * @param remainingMillis remaining clock time in milliseconds
     * @param incrementMillis per-move increment in milliseconds
     * @param movesToGo planning horizon in moves (see {@link #CLOCK_MOVES_TO_GO})
     * @return hard per-move deadline in milliseconds, or zero when no time remains
     */
    public static long clockHardBudgetMillis(long remainingMillis, long incrementMillis, long movesToGo) {
        long soft = clockBudgetMillis(remainingMillis, incrementMillis, movesToGo);
        if (soft <= 0L) {
            return 0L;
        }
        long quarterClock = Math.max(1L, remainingMillis / 4L + incrementMillis / 2L);
        long reserveAdjusted = remainingMillis > CLOCK_RESERVE_MILLIS
                ? remainingMillis - CLOCK_RESERVE_MILLIS
                : 1L;
        return Math.max(soft, Math.min(Math.min(soft * 4L, quarterClock), reserveAdjusted));
    }
}
