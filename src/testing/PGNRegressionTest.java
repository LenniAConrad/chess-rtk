package testing;

import static testing.TestSupport.*;

import java.util.List;

import application.cli.PgnOps;
import chess.struct.Game;
import chess.struct.Pgn;
import chess.struct.Record;

/**
 * Focused regression checks for PGN variation parsing and export.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
@SuppressWarnings("java:S2187")
public final class PGNRegressionTest {

	/**
	 * Shared start constant.
	 */
	private static final String START = Game.STANDARD_START_FEN;
	/**
	 * Shared after e4 constant.
	 */
	private static final String AFTER_E4 =
			"rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1";
	/**
	 * Shared after e4 e5 constant.
	 */
	private static final String AFTER_E4_E5 =
			"rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2";
	/**
	 * Shared after d4 constant.
	 */
	private static final String AFTER_D4 =
			"rnbqkbnr/pppppppp/8/8/3P4/8/PPP1PPPP/RNBQKBNR b KQkq - 0 1";
	/**
	 * Shared after d4 d5 constant.
	 */
	private static final String AFTER_D4_D5 =
			"rnbqkbnr/ppp1pppp/8/3p4/3P4/8/PPP1PPPP/RNBQKBNR w KQkq - 0 2";

	/**
	 * Creates a new pgn regression test instance.
	 */
	private PGNRegressionTest() {
		// utility
	}

	/**
	 * Handles main.
	 * @param args args
	 */
	public static void main(String[] args) {
		testVariationExtractionUsesPreMovePosition();
		testRootVariationsRoundTrip();
		System.out.println("PGNRegressionTest: all checks passed");
	}

	/**
	 * Handles test variation extraction uses pre move position.
	 */
	private static void testVariationExtractionUsesPreMovePosition() {
		Game game = Pgn.parseGame("1. e4 (1. d4 d5) e5 *");
		List<Record> records = PgnOps.extractRecordsWithVariations(game);

		assertEquals(4, records.size(), "record count with variation");
		assertHasRecord(records, START, AFTER_E4);
		assertHasRecord(records, AFTER_E4, AFTER_E4_E5);
		assertHasRecord(records, START, AFTER_D4);
		assertHasRecord(records, AFTER_D4, AFTER_D4_D5);
	}

	/**
	 * Handles test root variations round trip.
	 */
	private static void testRootVariationsRoundTrip() {
		Game game = Pgn.parseGame("(1. d4 d5) 1. e4 e5 *");
		assertEquals(1, game.getRootVariations().size(), "parsed root variation count");

		String rendered = Pgn.toPgn(game);
		assertTrue(rendered.contains("(1. d4 d5)"), "rendered root variation");

		Game reparsed = Pgn.parseGame(rendered);
		assertEquals(1, reparsed.getRootVariations().size(), "round-tripped root variation count");
	}

	/**
	 * Handles assert has record.
	 * @param records records
	 * @param parentFen parent fen
	 * @param positionFen position fen
	 */
	private static void assertHasRecord(List<Record> records, String parentFen, String positionFen) {
		for (Record candidate : records) {
			String parent = candidate.getParent() == null ? null : candidate.getParent().toString();
			String position = candidate.getPosition() == null ? null : candidate.getPosition().toString();
			if (positionFen.equals(position) && (parentFen == null ? parent == null : parentFen.equals(parent))) {
				return;
			}
		}
		throw new AssertionError("Missing record parent=" + parentFen + " position=" + positionFen);
	}
}
