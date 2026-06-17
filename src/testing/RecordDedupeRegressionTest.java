package testing;

import static testing.TestSupport.assertEquals;
import static testing.TestSupport.assertTrue;
import static testing.TestSupport.countUtf8Lines;
import static testing.TestSupport.createTempDirectory;
import static testing.TestSupport.deleteRecursivelyStrict;
import static testing.TestSupport.readUtf8;
import static testing.TestSupport.runMain;
import static testing.TestSupport.runMainExpectFailure;
import static testing.TestSupport.writeJsonArrayFixture;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Regression coverage for {@code crtk record dedupe}.
 *
 * <p>Dedupe sits before split/export in the dataset pipeline, so it has to be
 * deterministic and auditable: FEN identity must collapse halfmove/fullmove
 * counter noise, full-FEN mode must keep those counters significant, row-hash
 * mode must ignore object field order, and every output must ship with a
 * manifest sidecar.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class RecordDedupeRegressionTest {

	/**
	 * Initial position FEN used by fixture rows.
	 */
	private static final String START =
			"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

	/**
	 * Same position identity as {@link #START}, with different move counters.
	 */
	private static final String START_LATER =
			"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 5 7";

	/**
	 * Distinct position fixture row.
	 */
	private static final String AFTER_E4 =
			"rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1";

	/**
	 * Utility class; prevent instantiation.
	 */
	private RecordDedupeRegressionTest() {
		// utility
	}

	/**
	 * Runs all record-dedupe regression checks.
	 *
	 * @param args ignored
	 */
	public static void main(String[] args) {
		Path workspace = createTempDirectory("crtk-dedupe-");
		try {
			testPositionSignatureCollapsesMoveCounters(workspace);
			testFenExactKeepsMoveCountersSignificant(workspace);
			testLastPolicyKeepsLastDuplicate(workspace);
			testRowHashIgnoresObjectFieldOrder(workspace);
			testManifestSidecarValidatesAgainstSchema(workspace);
			testAliasAndUsageFailures(workspace);
			System.out.println("RecordDedupeRegressionTest: all checks passed");
		} finally {
			deleteRecursivelyStrict(workspace);
		}
	}

	/**
	 * Verifies the default key collapses halfmove/fullmove counter differences.
	 *
	 * @param workspace scratch directory
	 */
	private static void testPositionSignatureCollapsesMoveCounters(Path workspace) {
		String first = record("first", START);
		String counterNoise = record("counter-noise", START_LATER);
		String unique = record("unique", AFTER_E4);
		Path input = writeJsonArrayFixture(workspace.resolve("position.record"),
				List.of(first, counterNoise, unique));
		Path output = workspace.resolve("position.unique.jsonl");
		String stdout = runMain("record", "dedupe",
				"--input", input.toString(),
				"--output", output.toString()).trim();
		String body = readUtf8(output);
		assertEquals(2L, countUtf8Lines(output),
				"position-signature removes one duplicate");
		assertTrue(body.contains("\"name\":\"first\""),
				"first policy keeps the first duplicate row");
		assertTrue(!body.contains("counter-noise"),
				"first policy drops the later duplicate row");
		assertTrue(stdout.contains("\"rowsIn\":3"),
				"summary reports rows in");
		assertTrue(stdout.contains("\"rowsOut\":2"),
				"summary reports rows out");
		assertTrue(stdout.contains("\"duplicatesRemoved\":1"),
				"summary reports duplicate count");
	}

	/**
	 * Verifies full-FEN mode keeps halfmove/fullmove counters significant.
	 *
	 * @param workspace scratch directory
	 */
	private static void testFenExactKeepsMoveCountersSignificant(Path workspace) {
		String first = record("first", START);
		String counterNoise = record("counter-noise", START_LATER);
		String unique = record("unique", AFTER_E4);
		Path input = writeJsonArrayFixture(workspace.resolve("fen.record"),
				List.of(first, counterNoise, unique));
		Path output = workspace.resolve("fen.unique.jsonl");
		String stdout = runMain("record", "dedupe",
				"--input", input.toString(),
				"--output", output.toString(),
				"--key", "fen-exact").trim();
		assertEquals(3L, countUtf8Lines(output),
				"fen-exact keeps full-FEN counter differences");
		assertTrue(stdout.contains("\"key\":\"fen-exact\""),
				"summary reports the selected key");
	}

	/**
	 * Verifies {@code --keep last} replaces the first retained row.
	 *
	 * @param workspace scratch directory
	 */
	private static void testLastPolicyKeepsLastDuplicate(Path workspace) {
		String first = record("first", START);
		String last = record("last", START_LATER);
		String unique = record("unique", AFTER_E4);
		Path input = writeJsonArrayFixture(workspace.resolve("last.record"),
				List.of(first, last, unique));
		Path output = workspace.resolve("last.unique.jsonl");
		String stdout = runMain("record", "dedupe",
				"--input", input.toString(),
				"--output", output.toString(),
				"--keep", "last").trim();
		String body = readUtf8(output);
		assertEquals(2L, countUtf8Lines(output),
				"last policy still keeps one row per key");
		assertTrue(!body.contains("\"name\":\"first\""),
				"last policy replaces the first duplicate");
		assertTrue(body.contains("\"name\":\"last\""),
				"last policy keeps the later duplicate");
		assertTrue(stdout.contains("\"keep\":\"last\""),
				"summary reports keep policy");
	}

	/**
	 * Verifies full-row hashing canonicalizes object key order.
	 *
	 * @param workspace scratch directory
	 */
	private static void testRowHashIgnoresObjectFieldOrder(Path workspace) {
		Path input = writeJsonArrayFixture(workspace.resolve("rowhash.record"), List.of(
				"{\"name\":\"same\",\"position\":\"" + START + "\",\"created\":1}",
				"{\"created\":1,\"position\":\"" + START + "\",\"name\":\"same\"}"));
		Path output = workspace.resolve("rowhash.unique.jsonl");
		String stdout = runMain("record", "dedup",
				"--input", input.toString(),
				"--output", output.toString(),
				"--key", "row-hash").trim();
		assertEquals(1L, countUtf8Lines(output),
				"row-hash ignores JSON object field order");
		assertTrue(stdout.contains("\"key\":\"row-hash\""),
				"alias path reports the row-hash key");
	}

	/**
	 * Verifies the dedupe output manifest validates against its schema.
	 *
	 * @param workspace scratch directory
	 */
	private static void testManifestSidecarValidatesAgainstSchema(Path workspace) {
		String first = record("first", START);
		String counterNoise = record("counter-noise", START_LATER);
		Path input = writeJsonArrayFixture(workspace.resolve("manifest.record"),
				List.of(first, counterNoise));
		Path output = workspace.resolve("manifest.unique.jsonl");
		runMain("record", "dedupe",
				"--input", input.toString(),
				"--output", output.toString());
		Path manifest = output.resolveSibling(output.getFileName() + ".manifest.json");
		assertTrue(Files.isRegularFile(manifest),
				"manifest sidecar exists");
		String manifestText = readUtf8(manifest);
		assertTrue(manifestText.contains("\"dedup_key\": \"position-signature\""),
				"manifest records the dedupe key");
		assertTrue(manifestText.contains("\"rows_in\": 2"),
				"manifest records rows in");
		assertTrue(manifestText.contains("\"rows_out\": 1"),
				"manifest records rows out");
		assertTrue(manifestText.contains("\"duplicates_removed\": 1"),
				"manifest records duplicates removed");
		runMain("schema", "validate", "crtk.dataset.manifest.v1",
				"--input", manifest.toString());
	}

	/**
	 * Verifies usage failures are loud and stable.
	 *
	 * @param workspace scratch directory
	 */
	private static void testAliasAndUsageFailures(Path workspace) {
		Path input = writeJsonArrayFixture(workspace.resolve("failure.record"), List.of(record("first", START)));
		Path output = workspace.resolve("failure.unique.jsonl");
		TestSupport.FailureResult highestDepth = runMainExpectFailure("record", "dedupe",
				"--input", input.toString(),
				"--output", output.toString(),
				"--keep", "highest-depth");
		assertEquals(2, highestDepth.exitCode(),
				"highest-depth is a usage failure until implemented");
		assertTrue(highestDepth.stderr().contains("reserved"),
				"highest-depth failure explains the reservation");
		TestSupport.FailureResult missing = runMainExpectFailure("record", "dedupe");
		assertEquals(2, missing.exitCode(),
				"missing required flags exit 2");
		assertTrue(missing.stderr().contains("Usage: crtk record dedupe"),
				"missing flags print usage");
	}

	/**
	 * Builds one compact record row.
	 *
	 * @param name row name
	 * @param fen  position FEN
	 * @return record JSON object
	 */
	private static String record(String name, String fen) {
		return "{\"name\":\"" + name + "\",\"position\":\"" + fen + "\",\"created\":1}";
	}

}
