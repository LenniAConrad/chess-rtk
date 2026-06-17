package testing;

import static testing.TestSupport.assertEquals;
import static testing.TestSupport.assertTrue;
import static testing.TestSupport.countUtf8Lines;
import static testing.TestSupport.createTempDirectory;
import static testing.TestSupport.deleteRecursively;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import chess.pgn.PgnStore;

/**
 * Bounded larger-corpus regression for the local PGN store.
 *
 * <p>This is a scale-shaped benchmark rather than a wall-clock benchmark:
 * it deliberately asserts deterministic store invariants (import count,
 * sidecar line counts, duplicate detection, reopen behavior, and representative
 * FEN lookups) without pinning elapsed time or machine-dependent throughput.
 * The default corpus is large enough to exercise the append and scan paths
 * while staying cheap enough for the normal CLI regression phase.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class PgnStoreScaleRegressionTest {

	/**
	 * Configurable game count for manual larger runs.
	 */
	private static final int GAME_COUNT = boundedGameCount();

	/**
	 * Upper bound for accidental oversized CI runs.
	 */
	private static final int MAX_GAME_COUNT = 100_000;

	/**
	 * Default game count used by the recommended suite.
	 */
	private static final int DEFAULT_GAME_COUNT = 2_048;

	/**
	 * Standard chess start FEN.
	 */
	private static final String START_FEN =
			"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

	/**
	 * Position after 1. e4.
	 */
	private static final String AFTER_E4_FEN =
			"rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1";

	/**
	 * Mainline fixtures with exactly eight plies each.
	 */
	private static final OpeningLine[] LINES = {
			new OpeningLine("1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Ba4 Nf6 1-0", "1-0", true),
			new OpeningLine("1. d4 d5 2. c4 e6 3. Nc3 Nf6 4. Bg5 Be7 0-1", "0-1", false),
			new OpeningLine("1. c4 e5 2. Nc3 Nf6 3. g3 d5 4. cxd5 Nxd5 1/2-1/2", "1/2-1/2", false),
			new OpeningLine("1. Nf3 d5 2. g3 Nf6 3. Bg2 e6 4. O-O Be7 *", "*", false),
			new OpeningLine("1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 *", "*", true),
			new OpeningLine("1. d4 Nf6 2. c4 g6 3. Nc3 Bg7 4. e4 d6 *", "*", false),
			new OpeningLine("1. e4 e6 2. d4 d5 3. Nc3 Nf6 4. Bg5 Be7 *", "*", true),
			new OpeningLine("1. g3 d5 2. Bg2 Nf6 3. Nf3 e6 4. O-O Be7 *", "*", false)
	};

	/**
	 * Position observations per fixture line, including ply zero.
	 */
	private static final int POSITIONS_PER_GAME = 9;

	/**
	 * Utility class; prevent instantiation.
	 */
	private PgnStoreScaleRegressionTest() {
		// utility
	}

	/**
	 * Runs the bounded scale regression.
	 *
	 * @param args ignored
	 */
	public static void main(String[] args) {
		Path workspace = createTempDirectory("crtk-pgn-scale-");
		try {
			testBoundedCorpusImportFindAndDuplicate(workspace);
			System.out.println("PgnStoreScaleRegressionTest: all checks passed "
					+ "(games=" + GAME_COUNT
					+ ", positions=" + expectedPositionCount() + ")");
		} finally {
			deleteRecursively(workspace);
		}
	}

	/**
	 * Verifies a deterministic larger corpus exercises import, indexes,
	 * reopen, find, and duplicate detection.
	 *
	 * @param workspace scratch directory
	 */
	private static void testBoundedCorpusImportFindAndDuplicate(Path workspace) {
		Path pgnFile = workspace.resolve("scale.pgn");
		Path storeRoot = workspace.resolve("scale-store");
		writeScalePgn(pgnFile);
		try {
			PgnStore store = PgnStore.open(storeRoot);
			PgnStore.ImportReport report = store.importPgn(pgnFile);
			assertEquals(GAME_COUNT, report.gamesParsed(), "scale corpus parsed game count");
			assertEquals(GAME_COUNT, report.imported(), "scale corpus imported game count");
			assertEquals(0, report.duplicates(), "scale corpus duplicate count");
			assertEquals(0, report.malformed(), "scale corpus malformed count");
			assertStoreShape(store, storeRoot);

			PgnStore reopened = PgnStore.open(storeRoot);
			assertStoreShape(reopened, storeRoot);

			PgnStore.ImportReport duplicateReport = reopened.importPgn(pgnFile);
			assertEquals(GAME_COUNT, duplicateReport.gamesParsed(), "duplicate import parses all games");
			assertEquals(0, duplicateReport.imported(), "duplicate import adds no games");
			assertEquals(GAME_COUNT, duplicateReport.duplicates(), "duplicate import reports all duplicates");
			assertEquals(expectedPositionCount(), reopened.stats().positionCount(),
					"duplicate import does not append position rows");
		} catch (IOException ex) {
			throw new AssertionError("store I/O failed: " + ex.getMessage(), ex);
		}
	}

	/**
	 * Asserts the store's aggregate counts and representative find queries.
	 *
	 * @param store store handle
	 * @param root store root
	 * @throws IOException when store reads fail
	 */
	private static void assertStoreShape(PgnStore store, Path root) throws IOException {
		PgnStore.Stats stats = store.stats();
		assertEquals((long) GAME_COUNT, stats.gameCount(), "scale store game count");
		assertEquals(expectedPositionCount(), stats.positionCount(), "scale store position count");
		assertEquals(0L, stats.tombstoneCount(), "scale store tombstone count");
		assertEquals((long) GAME_COUNT, countUtf8Lines(root.resolve("games.jsonl")),
				"games.jsonl line count");
		assertEquals((long) GAME_COUNT, countUtf8Lines(root.resolve("games.idx")),
				"games.idx line count");
		assertEquals(expectedPositionCount(), countUtf8Lines(root.resolve("positions.idx")),
				"positions.idx line count");

		List<PgnStore.StoredGame> startMatches = store.findByFen(START_FEN);
		assertEquals(GAME_COUNT, startMatches.size(), "start FEN matches every game");
		List<PgnStore.StoredGame> e4Matches = store.findByFen(AFTER_E4_FEN);
		assertEquals(expectedE4Games(), e4Matches.size(), "after-e4 FEN match count");
		if (GAME_COUNT > 1) {
			assertTrue(!startMatches.get(0).gameId().equals(startMatches.get(startMatches.size() - 1).gameId()),
					"findByFen returns distinct games");
		}
	}

	/**
	 * Writes the deterministic scale corpus.
	 *
	 * @param target PGN target path
	 */
	private static void writeScalePgn(Path target) {
		try (BufferedWriter writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
			for (int i = 0; i < GAME_COUNT; i++) {
				if (i > 0) {
					writer.newLine();
				}
				OpeningLine line = LINES[i % LINES.length];
				writer.write("[Event \"Scale ");
				writer.write(Integer.toString(i));
				writer.write("\"]\n[Site \"Local\"]\n[Date \"2026.06.16\"]\n[Round \"");
				writer.write(Integer.toString(i + 1));
				writer.write("\"]\n[White \"FixtureW");
				writer.write(Integer.toString(i));
				writer.write("\"]\n[Black \"FixtureB");
				writer.write(Integer.toString(i));
				writer.write("\"]\n[Result \"");
				writer.write(line.result());
				writer.write("\"]\n\n");
				writer.write(line.movetext());
				writer.newLine();
			}
		} catch (IOException ex) {
			throw new AssertionError("failed to write scale corpus: " + ex.getMessage(), ex);
		}
	}

	/**
	 * Returns the expected number of position-index rows.
	 *
	 * @return position count
	 */
	private static long expectedPositionCount() {
		return (long) GAME_COUNT * POSITIONS_PER_GAME;
	}

	/**
	 * Returns how many generated games start with 1. e4.
	 *
	 * @return expected after-e4 query matches
	 */
	private static int expectedE4Games() {
		int fullCycles = GAME_COUNT / LINES.length;
		int remainder = GAME_COUNT % LINES.length;
		int perCycle = 0;
		int extra = 0;
		for (int i = 0; i < LINES.length; i++) {
			if (LINES[i].startsWithE4()) {
				perCycle++;
				if (i < remainder) {
					extra++;
				}
			}
		}
		return fullCycles * perCycle + extra;
	}

	/**
	 * Resolves the bounded game count from a system property.
	 *
	 * @return game count
	 */
	private static int boundedGameCount() {
		String configured = System.getProperty("crtk.pgn.scale.games");
		if (configured == null || configured.isBlank()) {
			return DEFAULT_GAME_COUNT;
		}
		try {
			int value = Integer.parseInt(configured.trim());
			if (value < 1 || value > MAX_GAME_COUNT) {
				throw new IllegalArgumentException("out of range");
			}
			return value;
		} catch (RuntimeException ex) {
			throw new AssertionError("crtk.pgn.scale.games must be 1.." + MAX_GAME_COUNT
					+ " (got " + configured + ")", ex);
		}
	}

	/**
	 * One deterministic opening fixture.
	 *
	 * @param movetext PGN movetext
	 * @param result PGN result token
	 * @param startsWithE4 whether the line starts with 1. e4
	 */
	private record OpeningLine(
		/**
		 * Stores the PGN movetext.
		 */
		String movetext,
		/**
		 * Stores the PGN result token.
		 */
		String result,
		/**
		 * Stores whether the first move is 1. e4.
		 */
		boolean startsWithE4
	) {
	}
}
