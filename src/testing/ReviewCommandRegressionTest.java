package testing;

import static testing.TestSupport.assertEquals;
import static testing.TestSupport.assertTrue;
import static testing.TestSupport.readUtf8;
import static testing.TestSupport.runMain;
import static testing.TestSupport.runMainExpectFailure;
import static testing.TestSupport.writeUtf8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import application.cli.PathOps;
import chess.core.Move;
import chess.core.Position;
import chess.review.ReviewRow;
import chess.review.StudyUnit;
import chess.schema.Schemas;
import chess.schema.Violation;
import chess.struct.Record;
import utility.Json;

/**
 * Regression checks for {@code crtk review game}.
 *
 * <p>The fixture is deliberately one ply from a SetUp/FEN PGN. It keeps the
 * offline alpha-beta path cheap while still forcing the row assembler to parse
 * PGN, grade a played move, preserve deterministic tag deltas, and validate the
 * emitted {@code crtk.review.ply.v1} JSONL contract.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class ReviewCommandRegressionTest {

	/**
	 * Fixture FEN where {@code Qh5} allows decisive Black threats.
	 */
	private static final String BLUNDER_FEN = "k2r4/8/8/8/8/8/8/3QK3 w - - 0 1";

	/**
	 * Utility class; prevent instantiation.
	 */
	private ReviewCommandRegressionTest() {
		// utility
	}

	/**
	 * Runs every review-command regression check.
	 *
	 * @param args ignored command-line arguments
	 */
	public static void main(String[] args) {
		testOfflineReviewWritesSchemaValidJsonl();
		testExternalUciReviewUsesBoundedFakeEngine();
		testPgnIsRequired();
		System.out.println("ReviewCommandRegressionTest: all checks passed");
	}

	/**
	 * Verifies the offline review command emits one valid row for a one-ply PGN.
	 */
	private static void testOfflineReviewWritesSchemaValidJsonl() {
		try {
			Path pgn = writeFixturePgn();
			Path output = PathOps.createLocalTempFile("crtk-review-", ".jsonl");
			String summary = runMain("review", "game",
					"--pgn", pgn.toString(),
					"--offline",
					"--output", output.toString(),
					"--depth", "2",
					"--max-nodes", "25000");
			assertTrue(summary.contains("\"schemaVersion\":\"" + ReviewRow.SCHEMA_VERSION + "\""),
					"review summary schema version");
			assertTrue(summary.contains("\"games\":1"), "review summary game count");
			assertTrue(summary.contains("\"rows\":1"), "review summary row count");

			String body = readUtf8(output);
			String[] rows = body.strip().split("\\R");
			assertEquals(1, rows.length, "review JSONL row count");
			assertOfflineReviewRow(rows[0]);
		} catch (IOException ex) {
			throw new AssertionError("review command fixture failed", ex);
		}
	}

	/**
	 * Verifies external review uses bounded UCI searches and records repro metadata.
	 */
	private static void testExternalUciReviewUsesBoundedFakeEngine() {
		try {
			Path dir = PathOps.createLocalTempDirectory("crtk-review-uci-");
			Path log = dir.resolve("commands.log");
			Path fakeEngine = dir.resolve("fake-review-uci.sh");
			writeUtf8(fakeEngine, fakeEngineScript(log));
			assertTrue(fakeEngine.toFile().setExecutable(true), "fake review engine executable");
			Path protocol = dir.resolve("fake-review.engine.toml");
			writeUtf8(protocol, protocolToml(fakeEngine));
			Path pgn = writeFixturePgn();
			Path output = dir.resolve("review.jsonl");
			Path studyOutput = dir.resolve("study.jsonl");
			Path recordOutput = dir.resolve("study.record.json");

			String summary = runMain("review", "game",
					"--pgn", pgn.toString(),
					"--protocol-path", protocol.toString(),
					"--output", output.toString(),
					"--to-study",
					"--study-output", studyOutput.toString(),
					"--record-output", recordOutput.toString(),
					"--max-nodes", "7",
					"--max-duration", "2s",
					"--threads", "1",
					"--hash", "16",
					"--multipv", "2",
					"--wdl");
			assertTrue(summary.contains("\"rows\":1"), "uci review summary row count");
			assertTrue(summary.contains("\"studySchemaVersion\":\"" + StudyUnit.SCHEMA_VERSION + "\""),
					"uci review summary study schema");
			assertTrue(summary.contains("\"studyRows\":1"), "uci review summary study row count");
			assertTrue(summary.contains("\"recordRows\":1"), "uci review summary record row count");

			String[] rows = readUtf8(output).strip().split("\\R");
			assertEquals(1, rows.length, "uci review JSONL row count");
			assertUciReviewRow(rows[0], protocol);
			assertStudyArtifacts(studyOutput, recordOutput);

			List<String> commands = Files.readAllLines(log);
			assertEquals(2, count(commands, "go nodes 7"), "fake UCI go nodes count");
			assertTrue(commands.contains("setoption name Threads value 1"), "fake UCI threads option");
			assertTrue(commands.contains("setoption name Hash value 16"), "fake UCI hash option");
			assertTrue(commands.contains("setoption name MultiPV value 2"), "fake UCI multipv option");
			assertTrue(commands.contains("setoption name UCI_ShowWDL value true"), "fake UCI WDL option");
		} catch (IOException ex) {
			throw new AssertionError("review command UCI fixture failed", ex);
		}
	}

	/**
	 * Verifies missing PGN input fails as a usage error before any backend starts.
	 */
	private static void testPgnIsRequired() {
		TestSupport.FailureResult failure = runMainExpectFailure("review", "game", "--offline");
		assertEquals(2, failure.exitCode(), "review game missing PGN exit code");
		assertTrue(failure.stderr().contains("Usage: crtk review game --pgn PATH"),
				"review game missing PGN diagnostic");
	}

	/**
	 * Asserts the offline review row has the expected stable contract fragments.
	 *
	 * @param row compact review-row JSON
	 */
	private static void assertOfflineReviewRow(String row) {
		assertSchemaValid(row, ReviewRow.SCHEMA_VERSION);
		assertTrue(row.contains("\"schemaVersion\":\"" + ReviewRow.SCHEMA_VERSION + "\""),
				"review row schema version");
		assertTrue(row.contains("\"event\":\"Review Offline Blunder\""), "review row event");
		assertTrue(row.contains("\"played_uci\":\"d1h5\""), "review row played move");
		assertTrue(row.contains("\"mistake_category\":\"blunder\""), "review row mistake category");
		assertTrue(row.contains("\"recommended_action\":\"drill_puzzle\""),
				"review row recommended action");
		assertTrue(row.contains("\"engine\":\"offline-alpha-beta\""), "review row offline engine");
		assertTrue(row.contains("\"search_mode\":\"offline\""), "review row search mode");
		assertTrue(row.contains("\"deterministic\":true"), "review row deterministic repro");
		assertTrue(row.contains("\"THREAT:"), "review row keeps dynamic threat tags");
		assertTrue(row.contains("\"TACTIC:"), "review row keeps tactical tags");
		assertTrue(row.contains("\"wdl_loss\":null"), "offline review row records unknown WDL loss");
	}

	/**
	 * Asserts the UCI review row has the expected stable contract fragments.
	 *
	 * @param row compact review-row JSON
	 * @param protocol protocol path used for the run
	 */
	private static void assertUciReviewRow(String row, Path protocol) {
		assertSchemaValid(row, ReviewRow.SCHEMA_VERSION);
		assertTrue(row.contains("\"engine\":\"Fake Review UCI\""), "uci review row engine");
		assertTrue(row.contains("\"protocol_path\":\"" + jsonEsc(protocol.toString()) + "\""),
				"uci review row protocol path");
		assertTrue(row.contains("\"max_nodes\":7"), "uci review row max nodes");
		assertTrue(row.contains("\"max_duration_ms\":2000"), "uci review row max duration");
		assertTrue(row.contains("\"multipv\":2"), "uci review row multipv");
		assertTrue(row.contains("\"threads\":1"), "uci review row threads");
		assertTrue(row.contains("\"hash\":16"), "uci review row hash");
		assertTrue(row.contains("\"search_mode\":\"uci\""), "uci review row search mode");
		assertTrue(row.contains("\"deterministic\":false"), "uci review row deterministic flag");
		assertTrue(row.contains("\"played_uci\":\"d1h5\""), "uci review row played move");
		assertTrue(row.contains("\"best_uci\":\"d1d8\""), "uci review row best move");
		assertTrue(row.contains("\"second_best_cp\":200"), "uci review row second-best cp");
		assertTrue(row.contains("\"eval_before\":{\"cp\":500,\"mate\":null,\"wdl\":[900,90,10]}"),
				"uci review row before eval");
		assertTrue(row.contains("\"eval_after\":{\"cp\":-500,\"mate\":null,\"wdl\":[10,90,900]}"),
				"uci review row after eval");
		assertTrue(row.contains("\"wdl_loss\":0.89"), "uci review row WDL loss");
		assertTrue(row.contains("\"mistake_category\":\"blunder\""), "uci review row mistake category");
		assertTrue(row.contains("\"study_unit_id\":\""), "uci review row links study unit");
	}

	/**
	 * Asserts study-unit JSONL and Record sidecars were emitted.
	 *
	 * @param studyOutput study JSONL path
	 * @param recordOutput Record JSON path
	 * @throws IOException if output cannot be read
	 */
	private static void assertStudyArtifacts(Path studyOutput, Path recordOutput) throws IOException {
		String[] units = readUtf8(studyOutput).strip().split("\\R");
		assertEquals(1, units.length, "study-unit JSONL row count");
		String unit = units[0];
		assertSchemaValid(unit, StudyUnit.SCHEMA_VERSION);
		String bestLineFen = new Position(BLUNDER_FEN).copy().play(Move.parse("d1d8")).toString();
		assertTrue(unit.contains("\"schemaVersion\":\"" + StudyUnit.SCHEMA_VERSION + "\""),
				"study-unit schema version");
		assertTrue(unit.contains("\"parent_fen\":\"" + BLUNDER_FEN + "\""),
				"study-unit parent FEN");
		assertTrue(unit.contains("\"position_fen\":\"" + bestLineFen + "\""),
				"study-unit best-line FEN");
		assertTrue(unit.contains("\"played_uci\":\"d1h5\""), "study-unit played move");
		assertTrue(unit.contains("\"best_uci\":\"d1d8\""), "study-unit best move");
		assertTrue(unit.contains("\"refutation_line\":[\"d1d8\"]"), "study-unit refutation line");
		assertTrue(unit.contains("\"difficulty\":\"hard\""), "study-unit difficulty");
		assertTrue(unit.contains("\"META: study_unit_id="), "study-unit id tag");
		assertTrue(unit.contains("\"META: review_ply=0\""), "study-unit ply tag");

		List<String> records = Json.splitTopLevelObjects(readUtf8(recordOutput));
		assertEquals(1, records.size(), "study Record JSON array count");
		String recordJson = records.get(0);
		assertSchemaValid(recordJson, Record.SCHEMA_VERSION);
		assertTrue(recordJson.contains("\"created\":0"), "study Record deterministic timestamp");
		assertTrue(recordJson.contains("\"parent\":\"" + BLUNDER_FEN + "\""), "study Record parent");
		assertTrue(recordJson.contains("\"position\":\"" + bestLineFen + "\""), "study Record position");
		assertTrue(recordJson.contains("\"META: best_uci=d1d8\""), "study Record best tag");
		Record record = Record.fromJson(recordJson);
		assertEquals(BLUNDER_FEN, record.getParent().toString(), "parsed study Record parent");
		assertEquals(bestLineFen, record.getPosition().toString(), "parsed study Record position");
		assertEquals(Move.toString(Move.parse("d1d8")),
				Move.toString(record.getAnalysis().getBestMove()),
				"parsed study Record best move");
		assertTrue(Schemas.isKnown(StudyUnit.SCHEMA_VERSION),
				"schema registry knows study units");
		assertTrue(runMain("schema", "list").contains(StudyUnit.SCHEMA_VERSION + "\n"),
				"schema list prints study-unit schema");
	}

	/**
	 * Asserts a JSON row validates against the published schema.
	 *
	 * @param row compact JSON
	 * @param schemaVersion schema name
	 */
	private static void assertSchemaValid(String row, String schemaVersion) {
		List<Violation> violations = Schemas.load(schemaVersion).validate(row);
		if (!violations.isEmpty()) {
			StringBuilder report = new StringBuilder(schemaVersion)
					.append(" fixture disagrees with schema:\n");
			for (Violation violation : violations) {
				report.append("  ").append(violation.render()).append('\n');
			}
			throw new AssertionError(report.toString());
		}
	}

	/**
	 * Writes the one-ply SetUp/FEN PGN fixture.
	 *
	 * @return fixture PGN path
	 * @throws IOException if the file cannot be written
	 */
	private static Path writeFixturePgn() throws IOException {
		Path pgn = PathOps.createLocalTempFile("crtk-review-", ".pgn");
		writeUtf8(pgn, String.join(System.lineSeparator(),
				"[Event \"Review Offline Blunder\"]",
				"[Site \"?\"]",
				"[Date \"2026.06.16\"]",
				"[Round \"-\"]",
				"[White \"Tester\"]",
				"[Black \"Fixture\"]",
				"[Result \"*\"]",
				"[SetUp \"1\"]",
				"[FEN \"" + BLUNDER_FEN + "\"]",
				"",
				"1. Qh5 *",
				""));
		return pgn;
	}

	/**
	 * Writes a minimal protocol TOML for the fake review engine.
	 *
	 * @param fakeEngine fake engine executable path
	 * @return protocol TOML text
	 */
	private static String protocolToml(Path fakeEngine) {
		return ""
				+ "path = \"" + tomlEsc(fakeEngine.toString()) + "\"\n"
				+ "name = \"Fake Review UCI\"\n"
				+ "settings = \"test\"\n"
				+ "isready = \"isready\"\n"
				+ "readyok = \"readyok\"\n"
				+ "showUci = \"uci\"\n"
				+ "uciok = \"uciok\"\n"
				+ "searchDepth = \"go depth %d\"\n"
				+ "searchNodes = \"go nodes %d\"\n"
				+ "searchTime = \"go movetime %d\"\n"
				+ "setPosition = \"position fen %s\"\n"
				+ "stop = \"stop\"\n"
				+ "newGame = \"ucinewgame\"\n"
				+ "setChess960 = \"setoption name UCI_Chess960 value %b\"\n"
				+ "setHashSize = \"setoption name Hash value %d\"\n"
				+ "setMultiPivotAmount = \"setoption name MultiPV value %d\"\n"
				+ "setThreadAmount = \"setoption name Threads value %d\"\n"
				+ "showWinDrawLoss = \"setoption name UCI_ShowWDL value %b\"\n"
				+ "setup = []\n";
	}

	/**
	 * Builds a tiny shell script that behaves like a deterministic UCI engine.
	 *
	 * @param log path receiving every command sent by the review path
	 * @return script contents
	 */
	private static String fakeEngineScript(Path log) {
		String logPath = shellEsc(log.toString());
		return "#!/bin/sh\n"
				+ "LOG='" + logPath + "'\n"
				+ "POSITION=''\n"
				+ "while IFS= read -r line; do\n"
				+ "  printf '%s\\n' \"$line\" >> \"$LOG\"\n"
				+ "  case \"$line\" in\n"
				+ "    uci) printf '%s\\n' 'id name Fake Review UCI' 'uciok' ;;\n"
				+ "    isready) printf '%s\\n' 'readyok' ;;\n"
				+ "    position*) POSITION=\"$line\" ;;\n"
				+ "    'go nodes '*)\n"
				+ "      case \"$POSITION\" in\n"
				+ "        *' w '*) printf '%s\\n' "
				+ "'info depth 1 multipv 1 score cp 500 wdl 900 90 10 nodes 7 nps 7 time 1 pv d1d8' "
				+ "'info depth 1 multipv 2 score cp 200 wdl 700 200 100 nodes 7 nps 7 time 1 pv d1h5' "
				+ "'bestmove d1d8' ;;\n"
				+ "        *) printf '%s\\n' "
				+ "'info depth 1 multipv 1 score cp 500 wdl 900 90 10 nodes 7 nps 7 time 1 pv d8d1' "
				+ "'info depth 1 multipv 2 score cp 100 wdl 600 250 150 nodes 7 nps 7 time 1 pv d8h8' "
				+ "'bestmove d8d1' ;;\n"
				+ "      esac ;;\n"
				+ "    stop) printf '%s\\n' 'bestmove 0000' ;;\n"
				+ "  esac\n"
				+ "done\n";
	}

	/**
	 * Counts exact string matches.
	 *
	 * @param values source values
	 * @param expected expected value
	 * @return count of matching rows
	 */
	private static int count(List<String> values, String expected) {
		int total = 0;
		for (String value : values) {
			if (expected.equals(value)) {
				total++;
			}
		}
		return total;
	}

	/**
	 * Escapes a value for TOML double-quoted strings.
	 *
	 * @param value source value
	 * @return escaped value
	 */
	private static String tomlEsc(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	/**
	 * Escapes a value for JSON string-fragment assertions.
	 *
	 * @param value source value
	 * @return escaped value
	 */
	private static String jsonEsc(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	/**
	 * Escapes a value inside a single-quoted POSIX shell string.
	 *
	 * @param value source value
	 * @return escaped shell value
	 */
	private static String shellEsc(String value) {
		return value.replace("'", "'\"'\"'");
	}
}
