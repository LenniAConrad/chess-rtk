package testing;

import static testing.TestSupport.assertEquals;
import static testing.TestSupport.assertTrue;

import chess.review.Classifier;
import chess.review.Classifier.Category;
import chess.review.Classifier.Request;
import chess.review.Classifier.Score;
import chess.review.Classifier.Thresholds;

/**
 * Pure-function regressions for the game-review mistake classifier.
 *
 * <p>The review router will be engine-dependent, but its classification step
 * must be deterministic once the eval inputs are known. These cases pin the
 * product-critical false-positive guards before any UCI command or JSONL row
 * emitter is added.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class ReviewClassifierRegressionTest {

	/**
	 * Utility class; prevent instantiation.
	 */
	private ReviewClassifierRegressionTest() {
		// utility
	}

	/**
	 * Runs every classifier regression check.
	 *
	 * @param args ignored command-line arguments
	 */
	public static void main(String[] args) {
		testCpAndWdlAgreeOnBlunder();
		testWdlGatePreventsResultNeutralCpBlunder();
		testAlreadyDecidedPositionDowngradesMistake();
		testOnlyMoveNearBestLossIsOk();
		testDeadDrawSuppressionWinsOverCpLoss();
		testTheorySuppressesNonBlunder();
		testTheoryDoesNotSuppressBlunderGradeLoss();
		testBlitzProfileWidensNoiseFloor();
		System.out.println("ReviewClassifierRegressionTest: all checks passed");
	}

	/**
	 * Verifies a large centipawn drop with matching WDL loss is a blunder.
	 */
	private static void testCpAndWdlAgreeOnBlunder() {
		Classifier.Verdict verdict = classify(
				Score.withWinShare(100, 0.70d),
				Score.withWinShare(-200, 0.30d));
		assertEquals(Category.BLUNDER, verdict.category(), "300cp and 0.40 WDL loss is blunder");
		assertEquals(300, verdict.cpLoss(), "blunder cp loss");
		assertTrue(verdict.wdlLoss() != null && verdict.wdlLoss() > 0.39d,
				"blunder WDL loss is recorded");
	}

	/**
	 * Verifies WDL prevents a nominal centipawn blunder when the result barely
	 * changes.
	 */
	private static void testWdlGatePreventsResultNeutralCpBlunder() {
		Classifier.Verdict verdict = classify(
				Score.withWinShare(400, 0.70d),
				Score.withWinShare(0, 0.65d));
		assertEquals(Category.MISTAKE, verdict.category(),
				"WDL gate downgrades a result-neutral cp blunder");
		assertEquals(400, verdict.cpLoss(), "WDL gate cp loss is retained");
	}

	/**
	 * Verifies already-decided positions downgrade instead of over-reporting
	 * mistakes.
	 */
	private static void testAlreadyDecidedPositionDowngradesMistake() {
		Classifier.Verdict verdict = classify(
				Score.withWinShare(900, 0.97d),
				Score.withWinShare(650, 0.95d));
		assertEquals(Category.INACCURACY, verdict.category(),
				"still-winning 250cp loss downgrades from mistake to inaccuracy");
		assertTrue(verdict.alreadyDecided(), "already-decided guard recorded");
	}

	/**
	 * Verifies an only-move position still reports a near-best played move as OK.
	 */
	private static void testOnlyMoveNearBestLossIsOk() {
		Classifier.Verdict verdict = Classifier.classify(new Request(
				Score.centipawns(100),
				Score.centipawns(60),
				Score.centipawns(-200),
				Thresholds.classical(),
				false));
		assertEquals(Category.OK, verdict.category(), "near-best move stays OK");
		assertTrue(verdict.onlyMovePosition(), "only-move position recorded");
		assertEquals(40, verdict.cpLoss(), "near-best cp loss retained");
	}

	/**
	 * Verifies dead-draw suppression wins over raw centipawn arithmetic.
	 */
	private static void testDeadDrawSuppressionWinsOverCpLoss() {
		Classifier.Verdict verdict = classify(
				Score.centipawns(30),
				Score.centipawns(-30));
		assertEquals(Category.OK, verdict.category(), "dead-draw cp swing is OK");
		assertTrue(verdict.deadDraw(), "dead-draw guard recorded");
		assertEquals(60, verdict.cpLoss(), "dead-draw cp loss retained");
	}

	/**
	 * Verifies book/theory positions do not become study mistakes for
	 * non-blunder losses.
	 */
	private static void testTheorySuppressesNonBlunder() {
		Classifier.Verdict verdict = Classifier.classify(new Request(
				Score.centipawns(100),
				Score.centipawns(-80),
				null,
				Thresholds.classical(),
				true));
		assertEquals(Category.OK, verdict.category(), "theory suppresses non-blunder mistake");
		assertTrue(verdict.theorySuppressed(), "theory suppression recorded");
	}

	/**
	 * Verifies theory does not hide blunder-grade losses.
	 */
	private static void testTheoryDoesNotSuppressBlunderGradeLoss() {
		Classifier.Verdict verdict = Classifier.classify(new Request(
				Score.centipawns(100),
				Score.centipawns(-250),
				null,
				Thresholds.classical(),
				true));
		assertEquals(Category.BLUNDER, verdict.category(), "theory keeps blunder-grade losses");
		assertTrue(!verdict.theorySuppressed(), "theory suppression does not fire for blunders");
	}

	/**
	 * Verifies blitz thresholds absorb losses classical would flag.
	 */
	private static void testBlitzProfileWidensNoiseFloor() {
		Classifier.Verdict classical = Classifier.classify(new Request(
				Score.centipawns(100),
				Score.centipawns(40),
				null,
				Thresholds.classical(),
				false));
		Classifier.Verdict blitz = Classifier.classify(new Request(
				Score.centipawns(100),
				Score.centipawns(40),
				null,
				Thresholds.blitz(),
				false));
		assertEquals(Category.INACCURACY, classical.category(),
				"classical flags a 60cp loss as inaccuracy");
		assertEquals(Category.OK, blitz.category(),
				"blitz widens the inaccuracy floor");
	}

	/**
	 * Classifies one move with the default thresholds.
	 *
	 * @param before pre-move score
	 * @param after post-move score
	 * @return verdict
	 */
	private static Classifier.Verdict classify(Score before, Score after) {
		return Classifier.classify(before, after);
	}
}
