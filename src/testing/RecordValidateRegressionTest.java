package testing;

import static testing.TestSupport.assertEquals;
import static testing.TestSupport.assertTrue;
import static testing.TestSupport.runMain;
import static testing.TestSupport.runMainExpectFailure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Regression coverage for {@code crtk record validate}, the fail-loud
 * sibling of {@link chess.struct.Record#fromJson(String)}.
 *
 * <p>The historical parser silently dropped malformed records by returning
 * {@code null}. The validate verb replaces that tolerance for CI and ingest
 * workflows: it streams a {@code .record} or {@code .jsonl} file, surfaces
 * every field-level issue with a stable diagnostic line, and exits with
 * code {@code 3} when anything fails to validate. This test pins that
 * behaviour across the positive, strict, max-errors, malformed-JSON, and
 * argument-shape paths.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class RecordValidateRegressionTest {

	/**
	 * Known-good standard chess start position FEN.
	 */
	private static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

	/**
	 * Utility class; prevent instantiation.
	 */
	private RecordValidateRegressionTest() {
		// utility
	}

	/**
	 * Runs every regression check and prints a success line.
	 *
	 * @param args ignored command-line arguments
	 */
	public static void main(String[] args) {
		testCleanJsonlReportsOk();
		testCleanJsonArrayReportsOk();
		testMissingCreatedFailsLoud();
		testInvalidParentFenFailsLoud();
		testRootNotAnObjectFailsLoud();
		testMalformedJsonFailsLoud();
		testStrictStopsAtFirstError();
		testMaxErrorsCapsReports();
		testMissingInputFailsAsUsage();
		testNonExistentInputFailsAsUsage();
		System.out.println("RecordValidateRegressionTest: all checks passed");
	}

	/**
	 * Verifies the tolerant happy path on a clean JSONL fixture.
	 */
	private static void testCleanJsonlReportsOk() {
		withTempFile("crtk-validate-ok-", ".jsonl", path -> {
			Files.writeString(path,
					recordJson(1711171067923L, "Stockfish 16", START_FEN, START_FEN) + "\n"
							+ recordJson(1711171067924L, "Stockfish 16", null, null) + "\n");
			String out = runMain("record", "validate", "--input", path.toString());
			assertEquals("ok: 2 records validated", out.trim(),
					"clean JSONL stdout summary");
		});
	}

	/**
	 * Verifies the tolerant happy path on a clean JSON array fixture.
	 */
	private static void testCleanJsonArrayReportsOk() {
		withTempFile("crtk-validate-array-", ".json", path -> {
			Files.writeString(path, "[\n  "
					+ recordJson(1711171067923L, "Stockfish 16", START_FEN, START_FEN) + "\n]\n");
			String out = runMain("record", "validate", "--input", path.toString());
			assertEquals("ok: 1 record validated", out.trim(),
					"clean JSON array stdout summary");
		});
	}

	/**
	 * Verifies a missing {@code created} field surfaces with the field name.
	 */
	private static void testMissingCreatedFailsLoud() {
		withTempFile("crtk-validate-missing-", ".jsonl", path -> {
			Files.writeString(path, "{\"engine\":\"Stockfish 16\"}\n");
			TestSupport.FailureResult result = runMainExpectFailure(
					"record", "validate", "--input", path.toString());
			assertEquals(3, result.exitCode(), "missing-required exit code is 3");
			assertTrue(result.stderr().contains("index=0 field=created: missing required field"),
					"stderr names the missing field");
		});
	}

	/**
	 * Verifies an invalid {@code parent} FEN surfaces with the field name.
	 */
	private static void testInvalidParentFenFailsLoud() {
		withTempFile("crtk-validate-fen-", ".jsonl", path -> {
			Files.writeString(path,
					"{\"created\":1,\"parent\":\"not-a-fen\",\"position\":\"" + START_FEN + "\"}\n");
			TestSupport.FailureResult result = runMainExpectFailure(
					"record", "validate", "--input", path.toString());
			assertEquals(3, result.exitCode(), "invalid-FEN exit code is 3");
			assertTrue(result.stderr().contains("index=0 field=parent: invalid FEN"),
					"stderr names the parent FEN failure");
		});
	}

	/**
	 * Verifies a non-object JSONL line surfaces as a structural failure.
	 *
	 * <p>The fixture starts with a real object so {@link application.cli.RecordIO}
	 * routes through JSONL mode (a JSON-array file is detected by a leading
	 * {@code "["}); the second line is a bare JSON string that must surface as a
	 * structural mismatch rather than be silently dropped.</p>
	 */
	private static void testRootNotAnObjectFailsLoud() {
		withTempFile("crtk-validate-root-", ".jsonl", path -> {
			Files.writeString(path,
					recordJson(1L, "ok", null, null) + "\n"
							+ "\"just-a-bare-string\"\n");
			TestSupport.FailureResult result = runMainExpectFailure(
					"record", "validate", "--input", path.toString());
			assertEquals(3, result.exitCode(), "non-object root exit code is 3");
			assertTrue(result.stderr().contains("expected JSON object"),
					"stderr names the structural mismatch");
			assertTrue(result.stderr().contains("index=1"),
					"diagnostic points at the offending line");
		});
	}

	/**
	 * Verifies a malformed JSON object reports the parse failure.
	 */
	private static void testMalformedJsonFailsLoud() {
		withTempFile("crtk-validate-malformed-", ".jsonl", path -> {
			Files.writeString(path, "{\"created\": 1, \"engine\": \"oops\n");
			TestSupport.FailureResult result = runMainExpectFailure(
					"record", "validate", "--input", path.toString());
			assertEquals(3, result.exitCode(), "malformed-JSON exit code is 3");
			assertTrue(result.stderr().contains("not valid JSON"),
					"stderr labels the JSON parse error");
		});
	}

	/**
	 * Verifies {@code --strict} halts at the first invalid record.
	 */
	private static void testStrictStopsAtFirstError() {
		withTempFile("crtk-validate-strict-", ".jsonl", path -> {
			Files.writeString(path,
					"{\"engine\":\"first-bad\"}\n"
							+ "{\"engine\":\"second-bad\"}\n"
							+ recordJson(1L, "good", null, null) + "\n");
			TestSupport.FailureResult result = runMainExpectFailure(
					"record", "validate", "--input", path.toString(), "--strict");
			assertEquals(3, result.exitCode(), "strict failure exit code is 3");
			assertTrue(result.stderr().contains("--strict stopped at first error"),
					"failure message marks the strict short-circuit");
			assertTrue(result.stderr().contains("index=0"),
					"first-record diagnostic is emitted");
			assertTrue(!result.stderr().contains("index=1"),
					"second-record diagnostic is not emitted");
		});
	}

	/**
	 * Verifies {@code --max-errors} caps the printed reports without changing the exit code.
	 */
	private static void testMaxErrorsCapsReports() {
		withTempFile("crtk-validate-max-", ".jsonl", path -> {
			StringBuilder content = new StringBuilder();
			int badRecords = 5;
			for (int i = 0; i < badRecords; i++) {
				content.append("{\"engine\":\"bad-").append(i).append("\"}\n");
			}
			Files.writeString(path, content.toString());
			TestSupport.FailureResult result = runMainExpectFailure(
					"record", "validate", "--input", path.toString(), "--max-errors", "2");
			assertEquals(3, result.exitCode(), "max-errors mode still exits 3 on failure");
			assertTrue(result.stderr().contains("index=0"), "first issue is printed");
			assertTrue(result.stderr().contains("index=1"), "second issue is printed");
			assertTrue(!result.stderr().contains("index=2"), "third issue is suppressed by the cap");
			assertTrue(result.stderr().contains("additional issue"),
					"failure summary names the suppressed-issue count");
		});
	}

	/**
	 * Verifies a missing {@code --input} flag exits as a usage failure.
	 */
	private static void testMissingInputFailsAsUsage() {
		TestSupport.FailureResult result = runMainExpectFailure("record", "validate");
		assertEquals(2, result.exitCode(), "missing --input exits 2");
		assertTrue(result.stderr().contains("Usage: crtk record validate"),
				"stderr shows the usage line");
	}

	/**
	 * Verifies a path that is not a regular file exits as a usage failure.
	 */
	private static void testNonExistentInputFailsAsUsage() {
		TestSupport.FailureResult result = runMainExpectFailure(
				"record", "validate", "--input", "/definitely/not/a/path.json");
		assertEquals(2, result.exitCode(), "missing file exits 2");
		assertTrue(result.stderr().contains("not a regular file"),
				"stderr explains the missing file");
	}

	/**
	 * Renders a minimal valid record-shaped JSON object for fixtures.
	 *
	 * @param created  epoch millis
	 * @param engine   engine identifier
	 * @param parent   parent FEN or {@code null}
	 * @param position position FEN or {@code null}
	 * @return record JSON object text
	 */
	private static String recordJson(long created, String engine, String parent, String position) {
		StringBuilder sb = new StringBuilder("{\"created\":").append(created);
		if (engine != null) {
			sb.append(",\"engine\":\"").append(engine).append("\"");
		}
		if (parent != null) {
			sb.append(",\"parent\":\"").append(parent).append("\"");
		}
		if (position != null) {
			sb.append(",\"position\":\"").append(position).append("\"");
		}
		return sb.append("}").toString();
	}

	/**
	 * Functional helper for tests that need a temporary file.
	 */
	private interface FixtureAction {

		/**
		 * Performs work using the temporary file path.
		 *
		 * @param path scoped temporary path
		 * @throws IOException when fixture I/O fails
		 */
		void run(Path path) throws IOException;
	}

	/**
	 * Runs a fixture action with a temporary file and cleans up afterwards.
	 *
	 * @param prefix temp-file prefix
	 * @param suffix temp-file suffix
	 * @param action fixture action receiving the temporary path
	 */
	private static void withTempFile(String prefix, String suffix, FixtureAction action) {
		Path path;
		try {
			path = Files.createTempFile(prefix, suffix);
		} catch (IOException ex) {
			throw new AssertionError("failed to create temp file: " + ex.getMessage(), ex);
		}
		try {
			action.run(path);
		} catch (IOException ex) {
			throw new AssertionError("fixture I/O failed: " + ex.getMessage(), ex);
		} finally {
			try {
				Files.deleteIfExists(path);
			} catch (IOException ignored) {
				// best-effort cleanup
			}
		}
	}
}
