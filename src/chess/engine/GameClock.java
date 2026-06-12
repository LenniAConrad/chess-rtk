package chess.engine;

/**
 * Mutable per-game, per-side game clock for clocked self-play.
 *
 * <p>
 * The clock starts at the {@link TimeControl} base time. After each move the
 * owner calls {@link #spend} with the elapsed wall-clock think time: the
 * elapsed time is deducted and, if any time remains, the increment is added
 * back. A clock whose remaining time reaches zero has "flagged" and the side
 * loses the game on time. Per-move search budgets come from the same shared
 * clock-to-budget mapping the UCI loops apply to {@code wtime}/{@code btime}
 * (see {@link Limits#clockBudgetMillis}), so time-management changes are
 * exercised identically in both paths.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class GameClock {

    /**
     * Immutable time control this clock runs under.
     */
    private final TimeControl control;

    /**
     * Remaining time in milliseconds, never negative.
     */
    private long remainingMillis;

    /**
     * Whether the clock has reached zero (flag fall).
     */
    private boolean flagged;

    /**
     * Creates a clock at the time control's base time.
     *
     * @param control enabled time control (positive base time)
     * @throws IllegalArgumentException if the control is null or disabled
     */
    public GameClock(TimeControl control) {
        if (control == null || !control.enabled()) {
            throw new IllegalArgumentException("game clock requires an enabled time control");
        }
        this.control = control;
        this.remainingMillis = control.baseMillis();
    }

    /**
     * Returns the remaining time.
     *
     * @return remaining time in milliseconds, never negative
     */
    public long remainingMillis() {
        return remainingMillis;
    }

    /**
     * Returns the per-move increment.
     *
     * @return increment in milliseconds
     */
    public long incrementMillis() {
        return control.incrementMillis();
    }

    /**
     * Returns whether the clock has flagged (remaining time reached zero).
     *
     * @return true after a flag fall
     */
    public boolean flagged() {
        return flagged;
    }

    /**
     * Returns the per-move time budget implied by the current clock state,
     * using the shared {@link Limits#clockBudgetMillis} mapping with the
     * default {@link Limits#CLOCK_MOVES_TO_GO} horizon.
     *
     * @return per-move budget in milliseconds, or zero once flagged
     */
    public long budgetMillis() {
        return Limits.clockBudgetMillis(remainingMillis, control.incrementMillis(),
                Limits.CLOCK_MOVES_TO_GO);
    }

    /**
     * Returns search limits for the next move on this clock.
     *
     * @return time-bounded limits of at least one millisecond
     */
    public Limits limits() {
        return new Limits(AlphaBeta.MAX_DEPTH, 0L, Math.max(1L, budgetMillis()));
    }

    /**
     * Records one completed move: deducts the elapsed think time and, when the
     * clock survives, adds the increment back.
     *
     * @param elapsedMillis elapsed wall-clock think time in milliseconds
     * @return true when time remains; false on a flag fall (the side loses)
     */
    public boolean spend(long elapsedMillis) {
        if (flagged) {
            return false;
        }
        remainingMillis -= Math.max(0L, elapsedMillis);
        if (remainingMillis <= 0L) {
            remainingMillis = 0L;
            flagged = true;
            return false;
        }
        remainingMillis += control.incrementMillis();
        return true;
    }
}
