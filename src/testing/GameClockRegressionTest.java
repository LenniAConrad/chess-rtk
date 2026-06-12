package testing;

import static testing.TestSupport.assertEquals;
import static testing.TestSupport.assertFalse;
import static testing.TestSupport.assertTrue;

import chess.engine.AlphaBeta;
import chess.engine.GameClock;
import chess.engine.Limits;
import chess.engine.TimeControl;

/**
 * Regression checks for the gauntlet game-clock bookkeeping: time-control spec
 * parsing, the shared clock-to-budget mapping, per-move deduction and
 * increment, flag-fall losses, and the CLI's clock/budget conflict validation.
 *
 * <p>
 * Everything is exercised without playing real timed games, so the suite stays
 * fast and deterministic. The budget reference values mirror the UCI
 * {@code wtime}/{@code btime} mapping (share of remaining time over a
 * thirty-move horizon plus half the increment, capped by a 50 ms reserve),
 * which the gauntlet must reuse rather than reimplement.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class GameClockRegressionTest {

    /**
     * Utility class; prevent instantiation.
     */
    private GameClockRegressionTest() {
        // utility
    }

    /**
     * Runs all game-clock regression checks.
     *
     * @param args unused command-line arguments
     */
    public static void main(String[] args) {
        testSpecParsing();
        testSpecParsingRejectsInvalidInput();
        testClockBudgetMapping();
        testDeductionAndIncrement();
        testFlagFall();
        testCliConflictValidation();
        System.out.println("GameClockRegressionTest: all checks passed");
    }

    /**
     * Verifies {@code BASE+INC} parsing in seconds with decimals, the bare
     * {@code BASE} form, and the canonical round-tripping text form.
     */
    private static void testSpecParsing() {
        TimeControl blitz = TimeControl.parse("10+0.1");
        assertEquals(10_000L, blitz.baseMillis(), "10+0.1 base millis");
        assertEquals(100L, blitz.incrementMillis(), "10+0.1 increment millis");
        assertEquals("10+0.1", blitz.text(), "10+0.1 canonical text");
        assertTrue(blitz.enabled(), "parsed control is enabled");

        TimeControl bullet = TimeControl.parse("2+0.05");
        assertEquals(2_000L, bullet.baseMillis(), "2+0.05 base millis");
        assertEquals(50L, bullet.incrementMillis(), "2+0.05 increment millis");
        assertEquals("2+0.05", bullet.text(), "2+0.05 canonical text");

        TimeControl bare = TimeControl.parse("0.5");
        assertEquals(500L, bare.baseMillis(), "bare 0.5 base millis");
        assertEquals(0L, bare.incrementMillis(), "bare 0.5 has no increment");
        assertEquals("0.5+0", bare.text(), "bare 0.5 canonical text");

        TimeControl padded = TimeControl.parse(" 60+1 ");
        assertEquals(60_000L, padded.baseMillis(), "padded 60+1 base millis");
        assertEquals(1_000L, padded.incrementMillis(), "padded 60+1 increment millis");

        assertFalse(TimeControl.NONE.enabled(), "NONE is disabled");
    }

    /**
     * Verifies malformed, non-positive, and inconsistent specs are rejected.
     */
    private static void testSpecParsingRejectsInvalidInput() {
        expectInvalidSpec(null, "null spec");
        expectInvalidSpec("", "blank spec");
        expectInvalidSpec("+1", "missing base");
        expectInvalidSpec("10+", "missing increment after plus");
        expectInvalidSpec("abc", "non-numeric base");
        expectInvalidSpec("10+x", "non-numeric increment");
        expectInvalidSpec("-5+1", "negative base");
        expectInvalidSpec("1+-1", "negative increment");
        expectInvalidSpec("0", "zero base");
        expectInvalidSpec("0+1", "increment without base");
        expectInvalidConstruction(-1L, 0L, "negative base millis");
        expectInvalidConstruction(0L, 100L, "increment without base millis");
    }

    /**
     * Verifies the shared clock-to-budget mapping against the formula the UCI
     * loops apply to {@code wtime}/{@code btime}: at least one millisecond of
     * {@code remaining / movesToGo + increment / 2}, capped by the remaining
     * time minus the {@link Limits#CLOCK_RESERVE_MILLIS} reserve.
     */
    private static void testClockBudgetMapping() {
        assertEquals(383L, Limits.clockBudgetMillis(10_000L, 100L, 30L),
                "budget 10s+0.1s at thirty moves to go");
        assertEquals(91L, Limits.clockBudgetMillis(2_000L, 50L, 30L),
                "budget 2s+0.05s at thirty moves to go");
        assertEquals(1L, Limits.clockBudgetMillis(40L, 0L, 30L),
                "tiny remaining time still yields a one-millisecond budget");
        assertEquals(10L, Limits.clockBudgetMillis(60L, 10_000L, 30L),
                "huge increment is capped by the remaining-minus-reserve time");
        assertEquals(950L, Limits.clockBudgetMillis(1_000L, 0L, 1L),
                "single-move horizon spends all but the reserve");
        assertEquals(950L, Limits.clockBudgetMillis(1_000L, 0L, 0L),
                "non-positive horizon is clamped to one move");
        assertEquals(0L, Limits.clockBudgetMillis(0L, 100L, 30L),
                "no remaining time yields no budget");
        assertEquals(0L, Limits.clockBudgetMillis(-5L, 100L, 30L),
                "negative remaining time yields no budget");
    }

    /**
     * Verifies per-move deduction plus increment, and that the clock's search
     * limits carry the budget as a pure time bound.
     */
    private static void testDeductionAndIncrement() {
        GameClock clock = new GameClock(TimeControl.parse("10+0.1"));
        assertEquals(10_000L, clock.remainingMillis(), "clock starts at the base time");
        assertEquals(100L, clock.incrementMillis(), "clock carries the increment");
        assertEquals(383L, clock.budgetMillis(), "initial budget matches the shared mapping");
        assertFalse(clock.flagged(), "fresh clock has not flagged");

        assertTrue(clock.spend(200L), "spending within the budget keeps the clock alive");
        assertEquals(9_900L, clock.remainingMillis(), "deduct elapsed time then add the increment");
        assertTrue(clock.spend(-50L), "negative elapsed time is clamped to zero");
        assertEquals(10_000L, clock.remainingMillis(), "clamped move still earns the increment");

        Limits limits = clock.limits();
        assertEquals(AlphaBeta.MAX_DEPTH, limits.depth(), "clock limits leave depth unbounded");
        assertEquals(0L, limits.maxNodes(), "clock limits carry no node bound");
        assertEquals(clock.budgetMillis(), limits.softMillis(),
                "clock limits carry the budget as the soft target");
        assertEquals(
                Limits.clockHardBudgetMillis(clock.remainingMillis(), clock.incrementMillis(),
                        Limits.CLOCK_MOVES_TO_GO),
                limits.maxDurationMillis(),
                "clock limits carry the hard deadline as the time bound");
        assertTrue(limits.softMillis() <= limits.maxDurationMillis(),
                "soft target never exceeds the hard deadline");
    }

    /**
     * Verifies a clock that reaches zero flags exactly once, earns no
     * increment, and stays flagged (the side loses that game).
     */
    private static void testFlagFall() {
        GameClock clock = new GameClock(new TimeControl(100L, 50L));
        assertTrue(clock.spend(99L), "one millisecond left survives");
        assertEquals(51L, clock.remainingMillis(), "survivor earns the increment");
        assertFalse(clock.spend(51L), "exact exhaustion is a flag fall");
        assertTrue(clock.flagged(), "flag fall latches");
        assertEquals(0L, clock.remainingMillis(), "flagged clock holds zero, no increment");
        assertFalse(clock.spend(1L), "a flagged clock stays flagged");
        assertEquals(1L, clock.limits().maxDurationMillis(),
                "flagged clock limits stay a positive time bound");

        GameClock overrun = new GameClock(new TimeControl(100L, 50L));
        assertFalse(overrun.spend(101L), "overrunning the remaining time is a flag fall");
        assertEquals(0L, overrun.remainingMillis(), "overrun clock is clamped to zero");
    }

    /**
     * Verifies the CLI rejects a game clock combined with an explicit fixed
     * per-move budget for the same side, and rejects malformed specs, without
     * playing any games.
     */
    private static void testCliConflictValidation() {
        expectGauntletFailure("--tc with --movetime",
                "--tc", "2+0.05", "--movetime", "100");
        expectGauntletFailure("--tc with --nodes",
                "--tc", "2+0.05", "--nodes", "5000");
        expectGauntletFailure("--tcA with --nodesA",
                "--tcA", "2+0.05", "--nodesA", "100");
        expectGauntletFailure("--tcB with --movetimeB",
                "--tcB", "2+0.05", "--movetimeB", "100");
        expectGauntletFailure("malformed --tc spec",
                "--tc", "banana");
        expectGauntletFailure("zero-base --tc spec",
                "--tc", "0+1");
    }

    /**
     * Runs {@code engine gauntlet} with the supplied flags and asserts it
     * fails with the CLI usage exit code before any game is played.
     *
     * @param label assertion label
     * @param flags gauntlet flags after the subcommand
     */
    private static void expectGauntletFailure(String label, String... flags) {
        String[] args = new String[flags.length + 2];
        args[0] = "engine";
        args[1] = "gauntlet";
        System.arraycopy(flags, 0, args, 2, flags.length);
        TestSupport.FailureResult failure = TestSupport.runMainExpectFailure(args);
        assertEquals(2, failure.exitCode(), label + " exits with usage code 2");
        assertTrue(failure.stderr().contains("--tc"), label + " names the clock flag");
    }

    /**
     * Asserts a spec is rejected by {@link TimeControl#parse}.
     *
     * @param spec offending specification text
     * @param label assertion label
     */
    private static void expectInvalidSpec(String spec, String label) {
        try {
            TimeControl.parse(spec);
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("expected rejection: " + label);
    }

    /**
     * Asserts a millisecond pair is rejected by the {@link TimeControl}
     * constructor.
     *
     * @param baseMillis base time in milliseconds
     * @param incrementMillis increment in milliseconds
     * @param label assertion label
     */
    private static void expectInvalidConstruction(long baseMillis, long incrementMillis, String label) {
        try {
            new TimeControl(baseMillis, incrementMillis);
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("expected rejection: " + label);
    }
}
