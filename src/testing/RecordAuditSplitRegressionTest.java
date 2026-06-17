package testing;

import static testing.TestSupport.assertEquals;
import static testing.TestSupport.assertTrue;
import static testing.TestSupport.createTempDirectory;
import static testing.TestSupport.deleteRecursivelyStrict;
import static testing.TestSupport.runMain;
import static testing.TestSupport.runMainExpectFailure;
import static testing.TestSupport.writeJsonArrayFixture;

import java.nio.file.Path;
import java.util.List;

import chess.io.RecordSplitter;

/**
 * Regression coverage for {@code crtk record audit-split}.
 *
 * <p>The audit command is the independent leakage check for group-aware
 * dataset splits. It must accept real split outputs, reject manual leakage,
 * and report unkeyed rows without turning them into false positives.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class RecordAuditSplitRegressionTest {

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
	 * Position FEN after 1.e4.
	 */
	private static final String AFTER_E4 =
			"rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1";

	/**
	 * Position FEN after 1.d4.
	 */
	private static final String AFTER_D4 =
			"rnbqkbnr/pppppppp/8/8/3P4/8/PPP1PPPP/RNBQKBNR b KQkq - 0 1";

	/**
	 * Utility class; prevent instantiation.
	 */
	private RecordAuditSplitRegressionTest() {
		// utility
	}

	/**
	 * Runs all audit-split regression checks.
	 *
	 * @param args ignored
	 */
	public static void main(String[] args) {
		Path workspace = createTempDirectory("crtk-audit-split-");
		try {
			testGeneratedSplitAuditsClean(workspace);
			testManualLeakageFailsLoud(workspace);
			testUnkeyedRowsDoNotCreateFalseLeak(workspace);
			testUsageFailures(workspace);
			System.out.println("RecordAuditSplitRegressionTest: all checks passed");
		} finally {
			deleteRecursivelyStrict(workspace);
		}
	}

	/**
	 * Verifies files produced by {@code record split} audit clean.
	 *
	 * @param workspace scratch directory
	 */
	private static void testGeneratedSplitAuditsClean(Path workspace) {
		String start = record("start", START);
		String startLater = record("start-later", START_LATER);
		String afterE4 = record("e4", AFTER_E4);
		String afterD4 = record("d4", AFTER_D4);
		Path corpus = writeJsonArrayFixture(workspace.resolve("generated.record"),
				List.of(start, startLater, afterE4, afterD4));
		Path prefix = workspace.resolve("generated");
		runMain("record", "split",
				"--input", corpus.toString(),
				"--output", prefix.toString(),
				"--split", "70:15:15",
				"--seed", "11");
		String splits = String.join(",",
				RecordSplitter.outputPathFor(prefix, "train").toString(),
				RecordSplitter.outputPathFor(prefix, "val").toString(),
				RecordSplitter.outputPathFor(prefix, "test").toString());
		String stdout = runMain("record", "audit-split", "--splits", splits).trim();
		assertTrue(stdout.contains("\"splits\":3"),
				"generated audit reports three splits");
		assertTrue(stdout.contains("\"leakageGroups\":0"),
				"generated split has no leakage");
		assertTrue(stdout.contains("\"outcome\":\"ok\""),
				"generated split audit outcome is ok");
	}

	/**
	 * Verifies a position identity placed in two splits exits non-zero.
	 *
	 * @param workspace scratch directory
	 */
	private static void testManualLeakageFailsLoud(Path workspace) {
		Path train = writeJsonArrayFixture(workspace.resolve("manual.train.jsonl"),
				List.of(record("train-start", START)));
		Path val = writeJsonArrayFixture(workspace.resolve("manual.val.jsonl"),
				List.of(record("val-start", START_LATER)));
		Path test = writeJsonArrayFixture(workspace.resolve("manual.test.jsonl"),
				List.of(record("test-e4", AFTER_E4)));
		String splits = train + "," + val + "," + test;
		TestSupport.FailureResult result = runMainExpectFailure("record", "audit-split",
				"--splits", splits,
				"--max-leaks", "1");
		assertEquals(3, result.exitCode(),
				"leaking split exits 3");
		assertTrue(result.stdout().contains("\"leakageGroups\":1"),
				"stdout reports one leaking group");
		assertTrue(result.stdout().contains("\"outcome\":\"leakage\""),
				"stdout reports leakage outcome");
		assertTrue(result.stderr().contains("leak: group="),
				"stderr prints a leakage example");
		assertTrue(result.stderr().contains(train.toString()),
				"stderr names the first split");
		assertTrue(result.stderr().contains(val.toString()),
				"stderr names the second split");
	}

	/**
	 * Verifies rows without positions are counted but not treated as one leaking group.
	 *
	 * @param workspace scratch directory
	 */
	private static void testUnkeyedRowsDoNotCreateFalseLeak(Path workspace) {
		Path train = writeJsonArrayFixture(workspace.resolve("unkeyed.train.jsonl"),
				List.of("{\"name\":\"train\"}"));
		Path val = writeJsonArrayFixture(workspace.resolve("unkeyed.val.jsonl"),
				List.of("{\"name\":\"val\"}"));
		String stdout = runMain("record", "audit-split",
				"--splits", train + "," + val).trim();
		assertTrue(stdout.contains("\"unkeyedRows\":2"),
				"unkeyed rows are reported");
		assertTrue(stdout.contains("\"leakageGroups\":0"),
				"unkeyed rows do not create false leakage");
	}

	/**
	 * Verifies stable usage failures.
	 *
	 * @param workspace scratch directory
	 */
	private static void testUsageFailures(Path workspace) {
		Path single = writeJsonArrayFixture(workspace.resolve("single.jsonl"),
				List.of(record("single", START)));
		TestSupport.FailureResult missing = runMainExpectFailure("record", "audit-split");
		assertEquals(2, missing.exitCode(),
				"missing --splits exits 2");
		assertTrue(missing.stderr().contains("Usage: crtk record audit-split"),
				"missing --splits prints usage");
		TestSupport.FailureResult one = runMainExpectFailure("record", "audit-split",
				"--splits", single.toString());
		assertEquals(2, one.exitCode(),
				"one split exits 2");
		assertTrue(one.stderr().contains("at least two files"),
				"one split explains the minimum");
		TestSupport.FailureResult unsupported = runMainExpectFailure("record", "audit-split",
				"--splits", single + "," + single,
				"--group-by", "game-id");
		assertEquals(2, unsupported.exitCode(),
				"unsupported group strategy exits 2");
		assertTrue(unsupported.stderr().contains("unsupported split strategy"),
				"unsupported group strategy is explained");
	}

	/**
	 * Builds one compact record row.
	 *
	 * @param name row name
	 * @param fen  position FEN
	 * @return record JSON object
	 */
	private static String record(String name, String fen) {
		return "{\"name\":\"" + name + "\",\"position\":\"" + fen + "\"}";
	}

}
