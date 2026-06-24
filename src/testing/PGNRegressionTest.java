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
	 * Chess960 index 0 as Shredder-FEN.
	 */
	private static final String CHESS960_ZERO_FEN =
			"bbqnnrkr/pppppppp/8/8/8/8/PPPPPPPP/BBQNNRKR w HFhf - 0 1";
	/**
	 * Chess960 index 0 as source PGNs often encode it with KQkq plus Variant.
	 */
	private static final String CHESS960_ZERO_XFEN =
			"bbqnnrkr/pppppppp/8/8/8/8/PPPPPPPP/BBQNNRKR w KQkq - 0 1";

	/**
	 * Creates a new pgn regression test instance.
	 */
	private PGNRegressionTest() {
		// utility
	}

	/**
	 * Handles main.
	 * @param args command-line arguments
	 */
	public static void main(String[] args) {
		testVariationExtractionUsesPreMovePosition();
		testRootVariationsRoundTrip();
		testCommentsAfterNagsStayOnAnnotatedMove();
		testSetupZeroIgnoresFenTag();
		testFenWithoutSetupIsCompatibilityStartAndNormalizes();
		testChess960VariantNormalizesXfenCastling();
		testMultiSourcePgnSetupCorpusParses();
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
	 * Handles test comments after nags stay on annotated move.
	 */
	private static void testCommentsAfterNagsStayOnAnnotatedMove() {
		Game game = Pgn.parseGame("1. e4 $1 {good move} {keeps initiative} e5 *");
		Game.Node e4 = game.getMainline();
		Game.Node e5 = e4.getNext();

		assertEquals(List.of(1), e4.getNags(), "NAG attached to e4");
		assertEquals(List.of("good move", "keeps initiative"), e4.getCommentsAfter(),
				"comments after NAG attach to e4");
		assertTrue(e5.getCommentsBefore().isEmpty(), "comments after NAG are not moved before e5");

		String rendered = Pgn.toPgn(game);
		assertTrue(rendered.contains("e4 $1 {good move} {keeps initiative} e5"),
				"rendered PGN separates SAN, NAG, comments, and next move");
		assertFalse(rendered.contains("e4$1"), "rendered NAG is not glued to SAN");
		assertFalse(rendered.contains("  "), "rendered annotation path has no doubled spaces");
	}

	/**
	 * Verifies explicit SetUp=0 wins over a stray FEN tag.
	 */
	private static void testSetupZeroIgnoresFenTag() {
		Game game = Pgn.parseGame("""
				[Event "Explicit standard"]
				[SetUp "0"]
				[FEN "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1"]

				1. e4 *
				""");

		assertEquals(START, game.getStartPosition().toString(), "SetUp=0 keeps standard start");
		String rendered = Pgn.toPgn(game);
		assertFalse(rendered.contains("[FEN "), "rendered standard game drops ignored FEN tag");
		assertFalse(rendered.contains("[SetUp "), "rendered standard game drops ignored SetUp tag");
	}

	/**
	 * Verifies FEN-without-SetUp remains an import compatibility extension and
	 * serializes back to strict SetUp=1 PGN.
	 */
	private static void testFenWithoutSetupIsCompatibilityStartAndNormalizes() {
		Game game = Pgn.parseGame("""
				[Event "FEN only"]
				[FEN "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1"]

				1... e5 *
				""");

		assertEquals(AFTER_E4, game.getStartPosition().toString(), "FEN without SetUp is accepted");
		String rendered = Pgn.toPgn(game);
		assertTrue(rendered.contains("[SetUp \"1\"]"), "round-trip normalizes missing SetUp");
		assertTrue(rendered.contains("[FEN \"" + AFTER_E4 + "\"]"), "round-trip keeps start FEN");
		assertTrue(rendered.contains("1... e5"), "black-to-move FEN renders with ellipsis");
	}

	/**
	 * Verifies Variant=Chess960 lets PGN import normalize KQkq-style X-FEN
	 * castling into the core's Shredder-FEN representation.
	 */
	private static void testChess960VariantNormalizesXfenCastling() {
		Game game = Pgn.parseGame("""
				[Event "Chess960 X-FEN"]
				[Variant "Chess960"]
				[SetUp "1"]
				[FEN "bbqnnrkr/pppppppp/8/8/8/8/PPPPPPPP/BBQNNRKR w KQkq - 0 1"]

				1. g3 *
				""");

		assertEquals(CHESS960_ZERO_FEN, game.getStartPosition().toString(), "Chess960 X-FEN normalized");
		assertTrue(game.getStartPosition().isChess960(), "Chess960 castling metadata retained");
		String rendered = Pgn.toPgn(game);
		assertTrue(rendered.contains("[Variant \"Chess960\"]"), "Variant tag survives round-trip");
		assertTrue(rendered.contains("[FEN \"" + CHESS960_ZERO_FEN + "\"]"), "round-trip emits Shredder-FEN");
	}

	/**
	 * Verifies the small mixed-source setup corpus parses all games and keeps
	 * their distinct starting-position policies.
	 */
	private static void testMultiSourcePgnSetupCorpusParses() {
		String corpus = """
				[Event "Strict FEN"]
				[SetUp "1"]
				[FEN "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1"]

				1... e5 *

				[Event "Compatibility FEN"]
				[FEN "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1"]

				1... c5 *

				[Event "Chess960 source"]
				[Variant "Chess960"]
				[SetUp "1"]
				[FEN "bbqnnrkr/pppppppp/8/8/8/8/PPPPPPPP/BBQNNRKR w KQkq - 0 1"]

				1. g3 *
				""";

		List<Game> games = Pgn.parseGames(corpus);
		assertEquals(3, games.size(), "setup corpus game count");
		assertEquals(AFTER_E4, games.get(0).getStartPosition().toString(), "strict FEN start");
		assertEquals(AFTER_E4, games.get(1).getStartPosition().toString(), "compatibility FEN start");
		assertEquals(CHESS960_ZERO_FEN, games.get(2).getStartPosition().toString(), "Chess960 start");
		assertFalse(Pgn.toPgn(games.get(2)).contains(CHESS960_ZERO_XFEN), "X-FEN source is normalized");
	}

	/**
	 * Handles assert has record.
	 * @param records record list
	 * @param parentFen FEN before the move
	 * @param positionFen FEN after the move
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
