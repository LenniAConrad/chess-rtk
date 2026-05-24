package testing;

import static testing.TestSupport.*;

import java.util.HashSet;
import java.util.Set;

import chess.core.Position;
import chess.core.Setup;

/**
 * Regression checks for the generated Chess960 start-position table.
 *
 * @author Lennart A. Conrad
 * @since 2026
 */
@SuppressWarnings("java:S2187")
public final class Chess960SetupRegressionTest {

	/**
	 * Known FENs at stable Scharnagl indexes.
	 */
	private static final IndexedFen[] EXPECTED = {
			new IndexedFen(0, "bbqnnrkr/pppppppp/8/8/8/8/PPPPPPPP/BBQNNRKR w HFhf - 0 1"),
			new IndexedFen(1, "bqnbnrkr/pppppppp/8/8/8/8/PPPPPPPP/BQNBNRKR w HFhf - 0 1"),
			new IndexedFen(2, "bqnnrbkr/pppppppp/8/8/8/8/PPPPPPPP/BQNNRBKR w HEhe - 0 1"),
			new IndexedFen(3, "bqnnrkrb/pppppppp/8/8/8/8/PPPPPPPP/BQNNRKRB w GEge - 0 1"),
			new IndexedFen(4, "qbbnnrkr/pppppppp/8/8/8/8/PPPPPPPP/QBBNNRKR w HFhf - 0 1"),
			new IndexedFen(16, "bbnqnrkr/pppppppp/8/8/8/8/PPPPPPPP/BBNQNRKR w HFhf - 0 1"),
			new IndexedFen(518, "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w HAha - 0 1"),
			new IndexedFen(959, "rkrnnqbb/pppppppp/8/8/8/8/PPPPPPPP/RKRNNQBB w CAca - 0 1")
	};

	/**
	 * Prevents instantiation of this utility class.
	 */
	private Chess960SetupRegressionTest() {
		// utility
	}

	/**
	 * Runs all Chess960 setup regression checks.
	 *
	 * @param args ignored.
	 */
	public static void main(String[] args) {
		testIndexedFens();
		testAllPositionsArePresentAndUnique();
		System.out.println("Chess960SetupRegressionTest: all checks passed");
	}

	/**
	 * Verifies selected stable FENs by index.
	 */
	private static void testIndexedFens() {
		for (IndexedFen expected : EXPECTED) {
			assertEquals(expected.fen(), Setup.getChess960ByIndex(expected.index()).toString(),
					"Chess960 index " + expected.index());
		}
	}

	/**
	 * Verifies the generated table has exactly 960 unique positions.
	 */
	private static void testAllPositionsArePresentAndUnique() {
		Position[] positions = Setup.getAllChess960Positions();
		assertEquals(960, positions.length, "Chess960 table size");

		Set<String> fens = new HashSet<>();
		for (int i = 0; i < positions.length; i++) {
			String fen = positions[i].toString();
			if (!fens.add(fen)) {
				throw new AssertionError("Duplicate Chess960 FEN at index " + i + ": " + fen);
			}
		}
		assertEquals(960, fens.size(), "Chess960 unique FEN count");
	}

	/**
	 * Expected FEN bound to a Chess960 index.
	 *
	 * @param index the Chess960 index.
	 * @param fen   the expected FEN.
	 */
	private record IndexedFen(
		/**
		 * Stores the index.
		 */
		int index,
		/**
		 * Stores the fen.
		 */
		String fen
	) {
	}
}
