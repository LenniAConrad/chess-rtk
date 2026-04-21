package testing;

import java.util.Arrays;

import chess.core.Fen;
import chess.core.Field;
import chess.core.Piece;
import chess.core.Position;

/**
 * Zero-dependency regression checks for recently added {@link Position} query
 * APIs.
 *
 * <p>
 * This harness intentionally avoids external test frameworks so it can run with
 * the repository's standard {@code javac}/{@code java} workflow.
 * </p>
 *
 * @author Lennart A. Conrad
 * @since 2026
 */
public final class PositionRegressionTest {

	/**
	 * Prevents instantiation of this utility class.
	 */
	private PositionRegressionTest() {
		// utility
	}

	/**
	 * Runs all regression checks and exits with a non-zero status when any check
	 * fails.
	 *
	 * @param args ignored
	 */
	public static void main(String[] args) {
		testFenNormalization();
		testWhiteAttackQueries();
		testBlackAttackQueries();
		testWhitePinQueries();
		testBlackPinQueries();
		testPieceLocalMoveCounts();
		System.out.println("PositionRegressionTest: all checks passed");
	}

	/**
	 * Verifies shared FEN normalization for whitespace and legacy omitted
	 * en-passant fields.
	 */
	private static void testFenNormalization() {
		String legacy = " rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR \t w  KQkq  0  1 ";
		String expected = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

		assertEquals("", Fen.normalize(null), "null FEN normalization");
		assertEquals(expected, Fen.normalize(legacy), "legacy five-field FEN normalization");
		assertEquals(expected, new Position(legacy).toString(), "constructor FEN normalization");
	}

	/**
	 * Verifies White-side attack queries.
	 */
	private static void testWhiteAttackQueries() {
		Position position = new Position("2k5/8/8/8/8/3P4/5N2/K3R2B w - - 0 1");
		byte target = Field.toIndex("e4");

		assertTrue(position.isAttackedByWhite(target), "white attack presence on e4");
		assertFalse(position.isAttackedByBlack(target), "black attack absence on e4");
		assertEquals(4, position.countAttackersByWhite(target), "white attacker count on e4");
		assertEquals(0, position.countAttackersByBlack(target), "black attacker count on e4");
		assertAttackers(position.getAttackersByWhite(target), "d3", "f2", "e1", "h1");
		assertAttackers(position.getAttackersByBlack(target));
	}

	/**
	 * Verifies Black-side attack queries.
	 */
	private static void testBlackAttackQueries() {
		Position position = new Position("k3r2b/5n2/3p4/8/8/8/8/7K b - - 0 1");
		byte target = Field.toIndex("e5");

		assertTrue(position.isAttackedByBlack(target), "black attack presence on e5");
		assertFalse(position.isAttackedByWhite(target), "white attack absence on e5");
		assertEquals(4, position.countAttackersByBlack(target), "black attacker count on e5");
		assertEquals(0, position.countAttackersByWhite(target), "white attacker count on e5");
		assertAttackers(position.getAttackersByBlack(target), "d6", "f7", "e8", "h8");
		assertAttackers(position.getAttackersByWhite(target));
	}

	/**
	 * Verifies White pin queries and pinned-piece mobility.
	 */
	private static void testWhitePinQueries() {
		Position position = new Position("4r1k1/8/8/8/8/8/4B3/4K3 w - - 0 1");
		byte pinnedSquare = Field.toIndex("e2");

		assertTrue(position.isPinnedToOwnKing(pinnedSquare), "white bishop pinned on e2");
		Position.PinInfo info = position.findPinToOwnKing(pinnedSquare);
		assertNotNull(info, "white pin info on e2");
		assertEquals(Field.toIndex("e2"), info.pinnedSquare, "white pin info pinned square");
		assertEquals(Field.toIndex("e8"), info.pinnerSquare, "white pin info pinner square");
		assertEquals(Piece.BLACK_ROOK, info.pinnerPiece, "white pin info pinner piece");
		assertEquals(0, position.countLegalMovesFrom(pinnedSquare), "white pinned bishop mobility");
		assertFalse(position.isPinnedToOwnKing(Field.toIndex("e1")), "white king should not be reported pinned");
	}

	/**
	 * Verifies Black pin queries and pinned-piece mobility.
	 */
	private static void testBlackPinQueries() {
		Position position = new Position("4k3/4n3/8/8/8/8/8/4R1K1 b - - 0 1");
		byte pinnedSquare = Field.toIndex("e7");

		assertTrue(position.isPinnedToOwnKing(pinnedSquare), "black knight pinned on e7");
		Position.PinInfo info = position.findPinToOwnKing(pinnedSquare);
		assertNotNull(info, "black pin info on e7");
		assertEquals(Field.toIndex("e7"), info.pinnedSquare, "black pin info pinned square");
		assertEquals(Field.toIndex("e1"), info.pinnerSquare, "black pin info pinner square");
		assertEquals(Piece.WHITE_ROOK, info.pinnerPiece, "black pin info pinner piece");
		assertEquals(0, position.countLegalMovesFrom(pinnedSquare), "black pinned knight mobility");
		assertFalse(position.isPinnedToOwnKing(Field.toIndex("e8")), "black king should not be reported pinned");
	}

	/**
	 * Verifies piece-local legal move counts for normal moves, promotions,
	 * en passant, and opposite-side castling rights.
	 */
	private static void testPieceLocalMoveCounts() {
		Position start = new Position("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
		assertEquals(2, start.countLegalMovesFrom(Field.toIndex("b1")), "white knight mobility in start position");
		assertEquals(2, start.countLegalMovesFrom(Field.toIndex("b8")), "black knight mobility in start position");

		Position promotion = new Position("8/P7/8/8/8/8/8/k6K w - - 0 1");
		assertEquals(4, promotion.countLegalMovesFrom(Field.toIndex("a7")), "promotion move expansion");

		Position enPassant = new Position("k7/8/8/3Pp3/8/8/8/7K w - e6 0 1");
		assertEquals(2, enPassant.countLegalMovesFrom(Field.toIndex("d5")), "white en-passant mobility");
		assertEquals(1, enPassant.countLegalMovesFrom(Field.toIndex("e5")),
				"black opposite-side pawn should not inherit en-passant rights");

		Position whiteCastle = new Position("2k5/8/8/8/8/8/8/R3K2R w KQ - 0 1");
		assertEquals(7, whiteCastle.countLegalMovesFrom(Field.toIndex("e1")),
				"white king move count should include both castling moves");

		Position blackCastle = new Position("r3k2r/8/8/8/8/8/8/2K5 w kq - 0 1");
		assertEquals(7, blackCastle.countLegalMovesFrom(Field.toIndex("e8")),
				"black king move count should include castling moves off-turn");
	}

	/**
	 * Verifies equality for integer values.
	 *
	 * @param expected the expected value
	 * @param actual the actual value
	 * @param label the assertion label
	 */
	private static void assertEquals(int expected, int actual, String label) {
		if (expected != actual) {
			throw new AssertionError(label + ": expected " + expected + ", got " + actual);
		}
	}

	/**
	 * Verifies equality for byte values.
	 *
	 * @param expected the expected value
	 * @param actual the actual value
	 * @param label the assertion label
	 */
	private static void assertEquals(byte expected, byte actual, String label) {
		if (expected != actual) {
			throw new AssertionError(label + ": expected " + expected + ", got " + actual);
		}
	}

	/**
	 * Verifies equality for string values.
	 *
	 * @param expected the expected value
	 * @param actual the actual value
	 * @param label the assertion label
	 */
	private static void assertEquals(String expected, String actual, String label) {
		if (!expected.equals(actual)) {
			throw new AssertionError(label + ": expected '" + expected + "', got '" + actual + "'");
		}
	}

	/**
	 * Verifies that an object reference is not {@code null}.
	 *
	 * @param value the value to inspect
	 * @param label the assertion label
	 */
	private static void assertNotNull(Object value, String label) {
		if (value == null) {
			throw new AssertionError(label + ": expected non-null value");
		}
	}

	/**
	 * Verifies that a condition is {@code true}.
	 *
	 * @param condition the condition to inspect
	 * @param label the assertion label
	 */
	private static void assertTrue(boolean condition, String label) {
		if (!condition) {
			throw new AssertionError(label + ": expected true");
		}
	}

	/**
	 * Verifies that a condition is {@code false}.
	 *
	 * @param condition the condition to inspect
	 * @param label the assertion label
	 */
	private static void assertFalse(boolean condition, String label) {
		if (condition) {
			throw new AssertionError(label + ": expected false");
		}
	}

	/**
	 * Verifies that the attacker list matches the expected set of squares.
	 *
	 * @param actual the returned attacker squares
	 * @param expectedSquares the expected square names
	 */
	private static void assertAttackers(byte[] actual, String... expectedSquares) {
		byte[] expected = new byte[expectedSquares.length];
		for (int i = 0; i < expectedSquares.length; i++) {
			expected[i] = Field.toIndex(expectedSquares[i]);
		}
		Arrays.sort(actual);
		Arrays.sort(expected);
		if (!Arrays.equals(actual, expected)) {
			throw new AssertionError("attacker set mismatch: expected " + Arrays.toString(expected)
					+ ", got " + Arrays.toString(actual));
		}
	}
}
