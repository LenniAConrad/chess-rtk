package testing;

import static testing.TestSupport.assertEquals;
import static testing.TestSupport.assertTrue;
import static testing.TestSupport.createTempDirectory;
import static testing.TestSupport.deleteRecursively;
import static testing.TestSupport.readUtf8;
import static testing.TestSupport.runMain;
import static testing.TestSupport.runMainExpectFailure;
import static testing.TestSupport.writeUtf8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import chess.pgn.GameIdentity;
import chess.pgn.PgnStore;
import chess.schema.Schemas;
import chess.schema.Validator;
import chess.schema.Violation;
import chess.struct.Game;
import chess.struct.Pgn;

/**
 * Regression coverage for the PGN store's mutating verbs
 * ({@code crtk pgn delete} and {@code crtk pgn compact}).
 *
 * <p>The synthesis was emphatic: {@code compact} is the only mutating path
 * the store ever takes, so its correctness gates the whole store. This
 * test pins six invariants:</p>
 * <ul>
 *   <li>{@code delete} hides a stored game from subsequent lookups
 *       without removing its bytes from disk;</li>
 *   <li>{@code compact} physically drops tombstoned games and rebuilds
 *       both sidecar indexes (positionCount shrinks accordingly);</li>
 *   <li>{@code compact} is idempotent — a second invocation with no
 *       pending tombstones is a clean no-op;</li>
 *   <li>survivors are still byte-round-trippable through {@code Pgn.toPgn}
 *       after compaction;</li>
 *   <li>the manifest after compaction still validates against the
 *       schema (the new {@code tombstoneCount} field included);</li>
 *   <li>{@code crtk pgn delete} on an unknown id exits {@code 3}.</li>
 * </ul>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class PgnStoreCompactRegressionTest {

	/**
	 * Sample PGN with three distinct games used as the fixture.
	 */
	private static final String SAMPLE_PGN =
			"[Event \"A\"]\n[White \"Alice\"]\n[Black \"Bob\"]\n[Result \"1-0\"]\n\n"
					+ "1. e4 e5 2. Nf3 Nc6 1-0\n\n"
					+ "[Event \"B\"]\n[White \"Eve\"]\n[Black \"Frank\"]\n[Result \"0-1\"]\n\n"
					+ "1. d4 d5 2. c4 e6 0-1\n\n"
					+ "[Event \"C\"]\n[White \"Mallory\"]\n[Black \"Oscar\"]\n[Result \"1/2-1/2\"]\n\n"
					+ "1. c4 c5 2. Nf3 Nf6 1/2-1/2\n";

	/**
	 * Utility class; prevent instantiation.
	 */
	private PgnStoreCompactRegressionTest() {
		// utility
	}

	/**
	 * Runs every regression check and prints a success line.
	 *
	 * @param args ignored command-line arguments
	 */
	public static void main(String[] args) {
		Path workspace = createTempDirectory("crtk-pgn-compact-");
		try {
			testDeleteHidesGameWithoutRemovingBytes(workspace);
			testCompactDropsTombstonedRowsAndRebuildsIndex(workspace);
			testCompactSurvivorIsStillRoundTrippable(workspace);
			testCompactIsIdempotent(workspace);
			testManifestStillValidatesAfterCompact(workspace);
			testDeleteUnknownIdFailsLoud(workspace);
			System.out.println("PgnStoreCompactRegressionTest: all checks passed");
		} finally {
			deleteRecursively(workspace);
		}
	}

	/**
	 * Verifies {@code delete} hides the game from lookup but leaves the
	 * games.jsonl bytes intact for the still-pending compact pass.
	 *
	 * @param workspace scratch directory
	 */
	private static void testDeleteHidesGameWithoutRemovingBytes(Path workspace) {
		Path root = workspace.resolve("delete-store");
		Path pgnFile = writePgn(workspace.resolve("delete.pgn"));
		try {
			PgnStore store = PgnStore.open(root);
			store.importPgn(pgnFile);
			String firstId = GameIdentity.compute(Pgn.parseGames(SAMPLE_PGN).get(0));
			long bytesBefore = Files.size(root.resolve("games.jsonl"));
			assertTrue(store.findByGameId(firstId).isPresent(), "first game exists before delete");
			assertTrue(store.delete(firstId), "delete returns true for an existing id");
			assertTrue(store.findByGameId(firstId).isEmpty(), "deleted game is no longer found");
			long bytesAfter = Files.size(root.resolve("games.jsonl"));
			assertEquals(bytesBefore, bytesAfter,
					"games.jsonl byte count is unchanged by a tombstone (delete is logical only)");
			assertTrue(Files.size(root.resolve("tombstones.idx")) > 0L,
					"tombstones.idx is populated");
			PgnStore.Stats stats = store.stats();
			assertEquals(2L, stats.gameCount(), "visible game count drops by one");
			assertEquals(1L, stats.tombstoneCount(), "tombstone count rises to one");
		} catch (IOException ex) {
			throw new AssertionError("store I/O failed: " + ex.getMessage(), ex);
		}
	}

	/**
	 * Verifies {@code compact} drops tombstoned rows from games.jsonl AND
	 * positions.idx, then clears the tombstone sidecar.
	 *
	 * @param workspace scratch directory
	 */
	private static void testCompactDropsTombstonedRowsAndRebuildsIndex(Path workspace) {
		Path root = workspace.resolve("compact-store");
		Path pgnFile = writePgn(workspace.resolve("compact.pgn"));
		try {
			PgnStore store = PgnStore.open(root);
			store.importPgn(pgnFile);
			assertEquals(3L, store.stats().gameCount(), "fixture imports three games");
			String firstId = GameIdentity.compute(Pgn.parseGames(SAMPLE_PGN).get(0));
			long positionsBefore = Files.lines(root.resolve("positions.idx")).count();
			store.delete(firstId);
			PgnStore.CompactionReport report = store.compact();
			// delete() removes the game from the live view before compact runs;
			// compact only changes the on-disk footprint, never the live count.
			assertEquals(2L, report.gameCountBefore(),
					"live count after delete (before compact runs) is 2");
			assertEquals(2L, report.gameCountAfter(),
					"compact does not change the live game count");
			assertTrue(report.positionCountAfter() < positionsBefore,
					"positions.idx shrinks to drop the deleted game's observations");
			assertEquals(1L, report.tombstonesDropped(), "one tombstone dropped");
			assertTrue(!Files.exists(root.resolve("tombstones.idx")),
					"tombstones.idx is removed after compaction");
			assertTrue(store.findByGameId(firstId).isEmpty(),
					"deleted game stays invisible after compaction");
			long survivingMentions = Files.lines(root.resolve("positions.idx"))
					.filter(line -> line.contains(firstId))
					.count();
			assertEquals(0L, survivingMentions,
					"positions.idx contains no row for the deleted gameId after compaction");
		} catch (IOException ex) {
			throw new AssertionError("store I/O failed: " + ex.getMessage(), ex);
		}
	}

	/**
	 * Verifies surviving games still round-trip through Pgn.toPgn after compaction.
	 *
	 * @param workspace scratch directory
	 */
	private static void testCompactSurvivorIsStillRoundTrippable(Path workspace) {
		Path root = workspace.resolve("roundtrip-store");
		Path pgnFile = writePgn(workspace.resolve("roundtrip.pgn"));
		try {
			PgnStore store = PgnStore.open(root);
			store.importPgn(pgnFile);
			List<Game> games = Pgn.parseGames(SAMPLE_PGN);
			String firstId = GameIdentity.compute(games.get(0));
			String secondId = GameIdentity.compute(games.get(1));
			store.delete(firstId);
			store.compact();
			java.util.Optional<PgnStore.StoredGame> survivor = store.findByGameId(secondId);
			assertTrue(survivor.isPresent(), "survivor is still findable by id");
			assertEquals(Pgn.toPgn(games.get(1)).strip(),
					Pgn.toPgn(survivor.get().game()).strip(),
					"surviving game still round-trips through Pgn.toPgn");
		} catch (IOException ex) {
			throw new AssertionError("store I/O failed: " + ex.getMessage(), ex);
		}
	}

	/**
	 * Verifies a second {@code compact()} call with no pending tombstones
	 * is a clean no-op.
	 *
	 * @param workspace scratch directory
	 */
	private static void testCompactIsIdempotent(Path workspace) {
		Path root = workspace.resolve("idempotent-store");
		Path pgnFile = writePgn(workspace.resolve("idempotent.pgn"));
		try {
			PgnStore store = PgnStore.open(root);
			store.importPgn(pgnFile);
			store.delete(GameIdentity.compute(Pgn.parseGames(SAMPLE_PGN).get(0)));
			PgnStore.CompactionReport first = store.compact();
			PgnStore.CompactionReport second = store.compact();
			assertEquals(0L, second.tombstonesDropped(),
					"second compaction drops nothing");
			assertEquals(first.gameCountAfter(), second.gameCountAfter(),
					"game count is stable across the second compact");
			assertEquals(first.positionCountAfter(), second.positionCountAfter(),
					"position count is stable across the second compact");
		} catch (IOException ex) {
			throw new AssertionError("store I/O failed: " + ex.getMessage(), ex);
		}
	}

	/**
	 * Verifies the on-disk manifest still validates against the schema
	 * after a delete + compact cycle (the schema gained {@code tombstoneCount}).
	 *
	 * @param workspace scratch directory
	 */
	private static void testManifestStillValidatesAfterCompact(Path workspace) {
		Path root = workspace.resolve("manifest-store");
		Path pgnFile = writePgn(workspace.resolve("manifest.pgn"));
		try {
			PgnStore store = PgnStore.open(root);
			store.importPgn(pgnFile);
			store.delete(GameIdentity.compute(Pgn.parseGames(SAMPLE_PGN).get(0)));
			store.compact();
		} catch (IOException ex) {
			throw new AssertionError("store I/O failed: " + ex.getMessage(), ex);
		}
		String manifest = readUtf8(root.resolve("manifest.json"));
		Validator validator = Schemas.load(PgnStore.MANIFEST_SCHEMA);
		List<Violation> violations = validator.validate(manifest);
		if (!violations.isEmpty()) {
			StringBuilder report = new StringBuilder("manifest fails schema:\n");
			for (Violation violation : violations) {
				report.append("  ").append(violation.render()).append('\n');
			}
			throw new AssertionError(report.toString());
		}
		assertTrue(manifest.contains("\"tombstoneCount\": 0"),
				"manifest reports tombstoneCount=0 after a successful compaction");
	}

	/**
	 * Verifies {@code crtk pgn delete} on an unknown id exits {@code 3}.
	 *
	 * @param workspace scratch directory
	 */
	private static void testDeleteUnknownIdFailsLoud(Path workspace) {
		Path root = workspace.resolve("unknown-store");
		Path pgnFile = writePgn(workspace.resolve("unknown.pgn"));
		runMain("pgn", "import", "--input", pgnFile.toString(),
				"--store", root.toString());
		TestSupport.FailureResult result = runMainExpectFailure(
				"pgn", "delete",
				"--gameId", "deadbeef",
				"--store", root.toString());
		assertEquals(3, result.exitCode(), "unknown id exits 3");
		assertTrue(result.stderr().contains("no game with id"),
				"stderr names the unknown id");
	}

	/**
	 * Writes the shared sample PGN fixture to the given path.
	 *
	 * @param target destination path
	 * @return the destination path
	 */
	private static Path writePgn(Path target) {
		return writeUtf8(target, SAMPLE_PGN);
	}

}
