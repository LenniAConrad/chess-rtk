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
import java.util.Optional;

import chess.pgn.GameIdentity;
import chess.pgn.PgnStore;
import chess.schema.Schemas;
import chess.schema.Validator;
import chess.schema.Violation;
import chess.struct.Game;
import chess.struct.Pgn;

/**
 * Regression coverage for the local PGN game store.
 *
 * <p>Pins the core contract: gameId is deterministic, import is idempotent,
 * findByGameId round-trips a stored game's PGN through
 * {@link chess.struct.Pgn#toPgn(Game)}, findByFen returns the games whose
 * mainline passes through a position (with FEN-equality verification on
 * top of FNV-1a signatures), the layout exposes the expected sidecars,
 * the on-disk manifest validates against
 * {@code crtk.pgn.store.manifest.v1}, and the CLI ingest report is
 * agent-consumable JSON. Negative paths check that missing arguments
 * exit with the documented code.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class PgnStoreRegressionTest {

	/**
	 * Standard chess start FEN used by every fixture.
	 */
	private static final String START_FEN =
			"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

	/**
	 * Sample PGN with two games.
	 */
	private static final String SAMPLE_PGN =
			"[Event \"Smoke 1\"]\n"
					+ "[Site \"?\"]\n"
					+ "[Date \"2026.06.16\"]\n"
					+ "[White \"Alice\"]\n"
					+ "[Black \"Bob\"]\n"
					+ "[Result \"1-0\"]\n"
					+ "\n"
					+ "1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Ba4 Nf6 5. O-O Be7 6. Re1 b5 7. Bb3 d6 8. c3 O-O 1-0\n"
					+ "\n"
					+ "[Event \"Smoke 2\"]\n"
					+ "[Site \"?\"]\n"
					+ "[Date \"2026.06.16\"]\n"
					+ "[White \"Eve\"]\n"
					+ "[Black \"Frank\"]\n"
					+ "[Result \"0-1\"]\n"
					+ "\n"
					+ "1. d4 d5 2. c4 e6 3. Nc3 Nf6 4. Bg5 Be7 0-1\n";

	/**
	 * Position after 1. e4, used as a custom PGN starting point.
	 */
	private static final String AFTER_E4_FEN =
			"rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1";

	/**
	 * Chess960 index 0 in the core's canonical Shredder-FEN form.
	 */
	private static final String CHESS960_ZERO_FEN =
			"bbqnnrkr/pppppppp/8/8/8/8/PPPPPPPP/BBQNNRKR w HFhf - 0 1";

	/**
	 * Mixed setup corpus exercising PGN import sources seen in the wild.
	 */
	private static final String SETUP_CORPUS_PGN =
			"[Event \"Strict FEN\"]\n"
					+ "[SetUp \"1\"]\n"
					+ "[FEN \"" + AFTER_E4_FEN + "\"]\n"
					+ "\n"
					+ "1... e5 *\n"
					+ "\n"
					+ "[Event \"Explicit standard\"]\n"
					+ "[SetUp \"0\"]\n"
					+ "[FEN \"" + AFTER_E4_FEN + "\"]\n"
					+ "\n"
					+ "1. Nf3 *\n"
					+ "\n"
					+ "[Event \"Chess960 X-FEN\"]\n"
					+ "[Variant \"Chess960\"]\n"
					+ "[SetUp \"1\"]\n"
					+ "[FEN \"bbqnnrkr/pppppppp/8/8/8/8/PPPPPPPP/BBQNNRKR w KQkq - 0 1\"]\n"
					+ "\n"
					+ "1. g3 *\n";

	/**
	 * Utility class; prevent instantiation.
	 */
	private PgnStoreRegressionTest() {
		// utility
	}

	/**
	 * Runs every regression check and prints a success line.
	 *
	 * @param args ignored command-line arguments
	 */
	public static void main(String[] args) {
		Path workspace = createTempDirectory("crtk-pgnstore-");
		try {
			testGameIdentityIsDeterministic();
			testImportIsIdempotent(workspace);
			testFindByStartingPositionReturnsAllGames(workspace);
			testFindByAbsentPositionReturnsEmpty(workspace);
			testSetupCorpusImportsAndIndexesStartFens(workspace);
			testShowRoundTripsPgn(workspace);
			testStoreLayoutExposesSidecars(workspace);
			testManifestValidatesAgainstSchema(workspace);
			testCliIngestReportIsJson(workspace);
			testMissingInputFailsAsUsage();
			testMissingGameIdShowFailsAsUsage();
			System.out.println("PgnStoreRegressionTest: all checks passed");
		} finally {
			deleteRecursively(workspace);
		}
	}

	/**
	 * Verifies the canonical game identifier is deterministic across two parses.
	 */
	private static void testGameIdentityIsDeterministic() {
		List<Game> firstParse = Pgn.parseGames(SAMPLE_PGN);
		List<Game> secondParse = Pgn.parseGames(SAMPLE_PGN);
		assertEquals(firstParse.size(), secondParse.size(), "parsed game count matches");
		for (int i = 0; i < firstParse.size(); i++) {
			assertEquals(
					GameIdentity.compute(firstParse.get(i)),
					GameIdentity.compute(secondParse.get(i)),
					"GameIdentity reproducible for game " + i);
		}
	}

	/**
	 * Verifies a repeat import reports duplicates and does not bloat the store.
	 *
	 * @param workspace scratch directory
	 */
	private static void testImportIsIdempotent(Path workspace) {
		Path pgnFile = writeSamplePgn(workspace.resolve("idem.pgn"));
		Path storeRoot = workspace.resolve("idem-store");
		try {
			PgnStore store = PgnStore.open(storeRoot);
			PgnStore.ImportReport first = store.importPgn(pgnFile);
			PgnStore.ImportReport second = store.importPgn(pgnFile);
			assertEquals(2, first.gamesParsed(), "first import parses both games");
			assertEquals(2, first.imported(), "first import adds both games");
			assertEquals(0, first.duplicates(), "first import reports no duplicates");
			assertEquals(2, second.gamesParsed(), "second import re-parses both games");
			assertEquals(0, second.imported(), "second import adds nothing");
			assertEquals(2, second.duplicates(), "second import reports both as duplicates");
			PgnStore.Stats stats = store.stats();
			assertEquals(2L, stats.gameCount(), "store still holds exactly two games");
		} catch (IOException ex) {
			throw new AssertionError("store I/O failed: " + ex.getMessage(), ex);
		}
	}

	/**
	 * Verifies a lookup by the standard starting FEN returns every imported game.
	 *
	 * @param workspace scratch directory
	 */
	private static void testFindByStartingPositionReturnsAllGames(Path workspace) {
		Path pgnFile = writeSamplePgn(workspace.resolve("find-start.pgn"));
		Path storeRoot = workspace.resolve("find-start-store");
		try {
			PgnStore store = PgnStore.open(storeRoot);
			store.importPgn(pgnFile);
			List<PgnStore.StoredGame> matches = store.findByFen(START_FEN);
			assertEquals(2, matches.size(), "starting position appears in both games");
		} catch (IOException ex) {
			throw new AssertionError("store I/O failed: " + ex.getMessage(), ex);
		}
	}

	/**
	 * Verifies a lookup by an unreached FEN returns the empty list.
	 *
	 * @param workspace scratch directory
	 */
	private static void testFindByAbsentPositionReturnsEmpty(Path workspace) {
		Path pgnFile = writeSamplePgn(workspace.resolve("find-absent.pgn"));
		Path storeRoot = workspace.resolve("find-absent-store");
		try {
			PgnStore store = PgnStore.open(storeRoot);
			store.importPgn(pgnFile);
			// A KQ vs k tablebase endgame the sample games never touch.
			List<PgnStore.StoredGame> matches = store.findByFen(
					"8/8/8/4k3/8/8/4K3/4Q3 w - - 0 1");
			assertEquals(0, matches.size(), "unreached position returns no matches");
		} catch (IOException ex) {
			throw new AssertionError("store I/O failed: " + ex.getMessage(), ex);
		}
	}

	/**
	 * Verifies PGN-store import indexes explicit FEN starts, ignores
	 * SetUp=0+FEN, and normalizes Chess960 Variant-tagged X-FEN starts.
	 *
	 * @param workspace scratch directory
	 */
	private static void testSetupCorpusImportsAndIndexesStartFens(Path workspace) {
		Path pgnFile = writeSetupCorpusPgn(workspace.resolve("setup-corpus.pgn"));
		Path storeRoot = workspace.resolve("setup-corpus-store");
		try {
			PgnStore store = PgnStore.open(storeRoot);
			PgnStore.ImportReport report = store.importPgn(pgnFile);
			assertEquals(3, report.gamesParsed(), "setup corpus parsed games");
			assertEquals(3, report.imported(), "setup corpus imported games");
			assertEquals(0, report.malformed(), "setup corpus malformed games");

			List<PgnStore.StoredGame> fenMatches = store.findByFen(AFTER_E4_FEN);
			assertEquals(1, fenMatches.size(), "only SetUp=1 game indexes custom FEN start");

			List<PgnStore.StoredGame> chess960Matches = store.findByFen(CHESS960_ZERO_FEN);
			assertEquals(1, chess960Matches.size(), "Chess960 normalized FEN is indexed");
			String rendered = Pgn.toPgn(chess960Matches.get(0).game());
			assertTrue(rendered.contains("[FEN \"" + CHESS960_ZERO_FEN + "\"]"),
					"stored Chess960 game round-trips with canonical FEN");
		} catch (IOException ex) {
			throw new AssertionError("store I/O failed: " + ex.getMessage(), ex);
		}
	}

	/**
	 * Verifies {@code findByGameId} round-trips the stored PGN through {@link Pgn#toPgn}.
	 *
	 * @param workspace scratch directory
	 */
	private static void testShowRoundTripsPgn(Path workspace) {
		Path pgnFile = writeSamplePgn(workspace.resolve("show.pgn"));
		Path storeRoot = workspace.resolve("show-store");
		try {
			PgnStore store = PgnStore.open(storeRoot);
			store.importPgn(pgnFile);
			Game firstParsed = Pgn.parseGames(SAMPLE_PGN).get(0);
			String firstId = GameIdentity.compute(firstParsed);
			Optional<PgnStore.StoredGame> looked = store.findByGameId(firstId);
			assertTrue(looked.isPresent(), "stored game looks up by canonical id");
			PgnStore.StoredGame stored = looked.get();
			assertEquals(Pgn.toPgn(firstParsed).strip(),
					Pgn.toPgn(stored.game()).strip(),
					"show round-trips through Pgn.toPgn");
		} catch (IOException ex) {
			throw new AssertionError("store I/O failed: " + ex.getMessage(), ex);
		}
	}

	/**
	 * Verifies the on-disk layout exposes the documented sidecar files.
	 *
	 * @param workspace scratch directory
	 */
	private static void testStoreLayoutExposesSidecars(Path workspace) {
		Path pgnFile = writeSamplePgn(workspace.resolve("layout.pgn"));
		Path storeRoot = workspace.resolve("layout-store");
		try {
			PgnStore store = PgnStore.open(storeRoot);
			store.importPgn(pgnFile);
		} catch (IOException ex) {
			throw new AssertionError("store I/O failed: " + ex.getMessage(), ex);
		}
		for (String name : List.of("games.jsonl", "games.idx", "positions.idx", "manifest.json")) {
			assertTrue(Files.isRegularFile(storeRoot.resolve(name)),
					"store layout exposes " + name);
		}
	}

	/**
	 * Verifies the on-disk manifest validates against the published schema.
	 *
	 * @param workspace scratch directory
	 */
	private static void testManifestValidatesAgainstSchema(Path workspace) {
		Path pgnFile = writeSamplePgn(workspace.resolve("schema.pgn"));
		Path storeRoot = workspace.resolve("schema-store");
		try {
			PgnStore store = PgnStore.open(storeRoot);
			store.importPgn(pgnFile);
		} catch (IOException ex) {
			throw new AssertionError("store I/O failed: " + ex.getMessage(), ex);
		}
		String manifestText = readUtf8(storeRoot.resolve("manifest.json"));
		Validator validator = Schemas.load(PgnStore.MANIFEST_SCHEMA);
		List<Violation> violations = validator.validate(manifestText);
		if (!violations.isEmpty()) {
			StringBuilder report = new StringBuilder("manifest fails schema:\n");
			for (Violation violation : violations) {
				report.append("  ").append(violation.render()).append('\n');
			}
			throw new AssertionError(report.toString());
		}
	}

	/**
	 * Verifies {@code crtk pgn import} emits a single agent-consumable JSON
	 * ingest report and counts duplicates on the second invocation.
	 *
	 * @param workspace scratch directory
	 */
	private static void testCliIngestReportIsJson(Path workspace) {
		Path pgnFile = writeSamplePgn(workspace.resolve("cli.pgn"));
		Path storeRoot = workspace.resolve("cli-store");
		String first = runMain("pgn", "import",
				"--input", pgnFile.toString(),
				"--store", storeRoot.toString()).trim();
		assertTrue(first.startsWith("{") && first.endsWith("}"),
				"ingest report is one JSON object");
		assertTrue(first.contains("\"games_parsed\":2"), "ingest report names games_parsed");
		assertTrue(first.contains("\"imported\":2"), "ingest report names imported");
		assertTrue(first.contains("\"duplicates\":0"), "ingest report names duplicates");
		assertTrue(first.contains("\"malformed\":0"), "ingest report names malformed");
		String second = runMain("pgn", "import",
				"--input", pgnFile.toString(),
				"--store", storeRoot.toString()).trim();
		assertTrue(second.contains("\"imported\":0"), "second import added nothing");
		assertTrue(second.contains("\"duplicates\":2"), "second import flagged duplicates");
	}

	/**
	 * Verifies the import verb exits as a usage failure when the input is missing.
	 */
	private static void testMissingInputFailsAsUsage() {
		TestSupport.FailureResult result = runMainExpectFailure("pgn", "import");
		assertEquals(2, result.exitCode(), "missing --input exits 2");
		assertTrue(result.stderr().contains("Usage: crtk pgn import"),
				"stderr shows the usage line");
	}

	/**
	 * Verifies the show verb exits as a usage failure when no game id is supplied.
	 */
	private static void testMissingGameIdShowFailsAsUsage() {
		TestSupport.FailureResult result = runMainExpectFailure("pgn", "show");
		assertEquals(2, result.exitCode(), "missing --gameId exits 2");
		assertTrue(result.stderr().contains("Usage: crtk pgn show"),
				"stderr shows the usage line");
	}

	/**
	 * Writes the shared sample PGN to the given path.
	 *
	 * @param target destination file
	 * @return the destination path
	 */
	private static Path writeSamplePgn(Path target) {
		return writeUtf8(target, SAMPLE_PGN);
	}

	/**
	 * Writes the setup corpus PGN to the given path.
	 *
	 * @param target destination file
	 * @return the destination path
	 */
	private static Path writeSetupCorpusPgn(Path target) {
		return writeUtf8(target, SETUP_CORPUS_PGN);
	}

}
