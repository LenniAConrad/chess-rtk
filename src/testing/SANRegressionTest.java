package testing;

import static testing.TestSupport.*;

import chess.core.Move;
import chess.core.Position;
import chess.core.SAN;
import chess.struct.Game;

/**
 * Zero-dependency regression checks for SAN helpers.
 *
 * @author Lennart A. Conrad
 * @since 2026
 */
public final class SANRegressionTest {

	/**
	 * Prevents instantiation of this utility class.
	 */
	private SANRegressionTest() {
		// utility
	}

	/**
	 * Runs all regression checks.
	 *
	 * @param args ignored
	 */
	public static void main(String[] args) {
		testCastlingTokenNormalization();
		testSanLineApplication();
		testInvalidSanLineKeepsValidPrefix();
		testLastMoveToken();
		System.out.println("SANRegressionTest: all checks passed");
	}

	/**
	 * Verifies common castling spellings are accepted by core SAN parsing.
	 */
	private static void testCastlingTokenNormalization() {
		Position position = new Position("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
		assertEquals("e1g1", Move.toString(SAN.fromAlgebraic(position, "0-0")), "zero kingside castle");
		assertEquals("e1c1", Move.toString(SAN.fromAlgebraic(position, "o-o-o")), "lowercase queenside castle");
	}

	/**
	 * Verifies that core SAN can apply a full move line and report the last move
	 * with its move-number context.
	 */
	private static void testSanLineApplication() {
		Position position = new Position("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
		SAN.PlayedLine line = SAN.playLine(position, "1. 0-0 0-0-0");

		assertTrue(line.isParsed(), "castling line parsed");
		assertEquals(2, line.getPliesPlayed(), "castling line plies");
		assertEquals("e8c8", Move.toString(line.getLastMove()), "castling line last move");
		assertEquals("1... 0-0-0", line.lastSanWithMoveNumber(), "castling line last SAN label");
		assertEquals("2kr3r/8/8/8/8/8/8/R4RK1 w - - 2 2", line.getResult().toString(),
				"castling line result");
	}

	/**
	 * Verifies invalid lines report the first invalid token and retain the valid
	 * prefix result.
	 */
	private static void testInvalidSanLineKeepsValidPrefix() {
		SAN.PlayedLine line = SAN.playLine(new Position(Game.STANDARD_START_FEN), "1. e4 bad");

		assertFalse(line.isParsed(), "invalid line parsed flag");
		assertEquals(1, line.getPliesPlayed(), "invalid line plies");
		assertEquals("bad", line.getInvalidToken(), "invalid line token");
		assertEquals("1. e4", line.lastSanWithMoveNumber(), "invalid line last valid SAN label");
		assertEquals("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1",
				line.getResult().toString(), "invalid line prefix result");
	}

	/**
	 * Verifies last SAN token extraction ignores PGN wrapper tokens.
	 */
	private static void testLastMoveToken() {
		String raw = "1. e4 e5 (1... c5) 2. Nf3 $1 {comment} 1-0";
		assertEquals("Nf3", SAN.lastMoveToken(raw), "last SAN token");
		assertEquals("", SAN.lastMoveToken("1-0"), "empty last SAN token");
	}
}
