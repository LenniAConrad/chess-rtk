package testing;

import static testing.TestSupport.assertEquals;
import static testing.TestSupport.assertSame;
import static testing.TestSupport.assertTrue;

import chess.engine.Sprt;

/**
 * Regression checks for the pentanomial SPRT math behind the self-play
 * gauntlet's early stopping.
 *
 * <p>
 * The log-likelihood-ratio reference values were computed independently with a
 * Python implementation of the same regularized GSPRT approximation (add one
 * half pair to each pentanomial category, normalize pair scores to
 * {@code [0, 1]}, plain logistic Elo-to-score conversion) and must match to
 * {@code 1e-6}.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class SprtRegressionTest {

    /**
     * Absolute tolerance for comparisons against the Python reference values.
     */
    private static final double TOLERANCE = 1e-6;

    /**
     * Standard error rates used by the gauntlet's SPRT.
     */
    private static final double ALPHA = 0.05;

    /**
     * Standard type-II error rate used by the gauntlet's SPRT.
     */
    private static final double BETA = 0.05;

    /**
     * Utility class; prevent instantiation.
     */
    private SprtRegressionTest() {
        // utility
    }

    /**
     * Runs all SPRT regression checks.
     *
     * @param args unused command-line arguments
     */
    public static void main(String[] args) {
        testLlrMatchesReferenceValues();
        testLogisticScoreAndBounds();
        testEloEstimateMatchesReferenceValues();
        testBoundCrossingAndLatching();
        testPairCategoryMapping();
        testSnapshotAccounting();
        testRejectsInvalidInput();
        System.out.println("SprtRegressionTest: all checks passed");
    }

    /**
     * Verifies the regularized pentanomial LLR against independently computed
     * reference values, including a case with empty categories.
     */
    private static void testLlrMatchesReferenceValues() {
        assertClose(5.722924115810672,
                Sprt.llr(new long[] { 12, 41, 281, 107, 59 }, 0.0, 5.0), "llr large sample 0:5");
        assertClose(0.9292192398890742,
                Sprt.llr(new long[] { 0, 0, 10, 5, 5 }, 0.0, 10.0), "llr empty categories 0:10");
        assertClose(-0.08271817953901142,
                Sprt.llr(new long[] { 30, 50, 100, 50, 30 }, 0.0, 5.0), "llr symmetric 0:5");
        assertClose(0.6902119001476238,
                Sprt.llr(new long[] { 5, 20, 120, 30, 8 }, -3.0, 1.0), "llr shifted bounds -3:1");
    }

    /**
     * Verifies the logistic score conversion and the Wald stopping bounds.
     */
    private static void testLogisticScoreAndBounds() {
        assertClose(0.5, Sprt.logisticScore(0.0), "logistic score at zero Elo");
        assertClose(1.0 / (1.0 + Math.pow(10.0, -5.0 / 400.0)), Sprt.logisticScore(5.0),
                "logistic score at five Elo");
        assertClose(2.9444389791664403, Sprt.upperBound(ALPHA, BETA), "upper bound log(19)");
        assertClose(-2.9444389791664403, Sprt.lowerBound(ALPHA, BETA), "lower bound -log(19)");
        // With no pairs the regularized mean is one half, so the LLR vanishes
        // for symmetric hypotheses and is only epsilon-negative for 0:5.
        assertClose(0.0, Sprt.llr(new long[] { 0, 0, 0, 0, 0 }, -5.0, 5.0), "llr of empty symmetric test");
        assertClose(-0.0005176920079992552, Sprt.llr(new long[] { 0, 0, 0, 0, 0 }, 0.0, 5.0),
                "llr of empty 0:5 test");
    }

    /**
     * Verifies the regularized logistic Elo estimate against reference values.
     */
    private static void testEloEstimateMatchesReferenceValues() {
        assertClose(55.78768656493816, Sprt.eloEstimate(new long[] { 12, 41, 281, 107, 59 }),
                "elo estimate large sample");
        assertClose(14.993044785526937, Sprt.eloEstimate(new long[] { 5, 20, 120, 30, 8 }),
                "elo estimate shifted sample");
        assertClose(0.0, Sprt.eloEstimate(new long[] { 30, 50, 100, 50, 30 }),
                "elo estimate symmetric sample");
    }

    /**
     * Verifies both stopping bounds are crossed after the reference number of
     * one-sided pairs and that the first decision latches against later
     * contrary results.
     */
    private static void testBoundCrossingAndLatching() {
        Sprt h1 = new Sprt(0.0, 5.0, ALPHA, BETA);
        for (int pair = 0; pair < 25; pair++) {
            h1.addPair(4);
        }
        assertSame(Sprt.Status.CONTINUE, h1.status(), "no H1 decision after 25 won pairs");
        h1.addPair(4);
        assertSame(Sprt.Status.ACCEPT_H1, h1.status(), "H1 accepted after 26 won pairs");
        assertTrue(h1.llr() >= Sprt.upperBound(ALPHA, BETA), "H1 LLR at or above upper bound");

        Sprt h0 = new Sprt(0.0, 5.0, ALPHA, BETA);
        for (int pair = 0; pair < 25; pair++) {
            h0.addPair(0);
        }
        assertSame(Sprt.Status.CONTINUE, h0.status(), "no H0 decision after 25 lost pairs");
        h0.addPair(0);
        assertSame(Sprt.Status.ACCEPT_H0, h0.status(), "H0 accepted after 26 lost pairs");
        assertTrue(h0.llr() <= Sprt.lowerBound(ALPHA, BETA), "H0 LLR at or below lower bound");

        // In-flight pairs reported after the decision update the counts but can
        // no longer change the latched decision.
        for (int pair = 0; pair < 200; pair++) {
            h1.addPair(0);
        }
        assertSame(Sprt.Status.ACCEPT_H1, h1.status(), "H1 decision latched");
        assertEquals(200L, h1.snapshot().losses(), "post-decision pairs still counted");
        assertTrue(h1.llr() < Sprt.lowerBound(ALPHA, BETA), "post-decision LLR may leave the bounds");
    }

    /**
     * Verifies the opening-pair scores map to the documented pentanomial
     * categories.
     */
    private static void testPairCategoryMapping() {
        assertEquals(0, Sprt.pairCategory(0, 0), "loss+loss category");
        assertEquals(1, Sprt.pairCategory(0, 1), "loss+draw category");
        assertEquals(1, Sprt.pairCategory(1, 0), "draw+loss category");
        assertEquals(2, Sprt.pairCategory(1, 1), "draw+draw category");
        assertEquals(2, Sprt.pairCategory(2, 0), "win+loss category");
        assertEquals(2, Sprt.pairCategory(0, 2), "loss+win category");
        assertEquals(3, Sprt.pairCategory(2, 1), "win+draw category");
        assertEquals(3, Sprt.pairCategory(1, 2), "draw+win category");
        assertEquals(4, Sprt.pairCategory(2, 2), "win+win category");
    }

    /**
     * Verifies snapshots count every category once and aggregate pair and game
     * totals.
     */
    private static void testSnapshotAccounting() {
        Sprt sprt = new Sprt(0.0, 5.0, ALPHA, BETA);
        for (int category = 0; category < 5; category++) {
            sprt.addPair(category);
        }
        Sprt.Snapshot snapshot = sprt.snapshot();
        assertEquals(1L, snapshot.losses(), "losses category count");
        assertEquals(1L, snapshot.halfLosses(), "half-losses category count");
        assertEquals(1L, snapshot.evens(), "evens category count");
        assertEquals(1L, snapshot.halfWins(), "half-wins category count");
        assertEquals(1L, snapshot.wins(), "wins category count");
        assertEquals(5L, snapshot.pairs(), "snapshot pair total");
        assertEquals(10L, snapshot.games(), "snapshot game total");
        assertSame(Sprt.Status.CONTINUE, snapshot.status(), "balanced test keeps running");
        assertClose(0.0, snapshot.elo(), "balanced test Elo estimate");
        assertClose(Sprt.llr(new long[] { 1, 1, 1, 1, 1 }, 0.0, 5.0), snapshot.llr(),
                "snapshot LLR matches static math");
    }

    /**
     * Verifies invalid hypotheses, error rates, pair scores, and count arrays
     * are rejected.
     */
    private static void testRejectsInvalidInput() {
        assertThrows(() -> new Sprt(5.0, 5.0, ALPHA, BETA), "equal hypothesis Elos");
        assertThrows(() -> new Sprt(5.0, 0.0, ALPHA, BETA), "inverted hypothesis Elos");
        assertThrows(() -> new Sprt(0.0, 5.0, 0.0, BETA), "zero alpha");
        assertThrows(() -> new Sprt(0.0, 5.0, ALPHA, 1.0), "unit beta");
        assertThrows(() -> new Sprt(0.0, 5.0, ALPHA, BETA).addPair(-1), "negative pair score");
        assertThrows(() -> new Sprt(0.0, 5.0, ALPHA, BETA).addPair(5), "oversized pair score");
        assertThrows(() -> Sprt.pairCategory(3, 0), "oversized game score");
        assertThrows(() -> Sprt.pairCategory(0, -1), "negative game score");
        assertThrows(() -> Sprt.llr(new long[4], 0.0, 5.0), "short count array");
        assertThrows(() -> Sprt.llr(null, 0.0, 5.0), "missing count array");
        assertThrows(() -> Sprt.eloEstimate(new long[6]), "long count array");
    }

    /**
     * Verifies an action fails with {@link IllegalArgumentException}.
     *
     * @param action action expected to throw
     * @param label assertion label
     */
    private static void assertThrows(Runnable action, String label) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(label + ": expected IllegalArgumentException");
    }

    /**
     * Verifies a double matches an expected value within the shared tolerance.
     *
     * @param expected expected value
     * @param actual actual value
     * @param label assertion label
     */
    private static void assertClose(double expected, double actual, String label) {
        if (Math.abs(expected - actual) > TOLERANCE) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }
}
