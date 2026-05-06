package testing;

import static testing.TestSupport.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import chess.io.RecordLc0Exporter;

/**
 * Regression checks for LC0 tensor export.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
@SuppressWarnings("java:S2187")
public final class RecordLC0ExporterRegressionTest {

    /**
     * Standard starting position.
     */
	private static final String START_FEN =
			"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    /**
     * Prevents instantiation.
     */
	private RecordLC0ExporterRegressionTest() {
		// utility
	}

    /**
     * Runs the regression checks.
     *
     * @param args ignored
     * @throws Exception if temp-file IO fails
     */
	public static void main(String[] args) throws Exception {
		Path dir = Files.createTempDirectory("crtk-lc0-export-test");
		Path input = dir.resolve("records.record");
		Path stem = dir.resolve("dataset");
		Files.writeString(input,
				"[\n"
						+ recordJson(START_FEN, "info depth 1 multipv 1 score cp 20 nodes 1 pv e2e4")
						+ ",\n"
						+ recordJson(START_FEN, "info depth 1 multipv 1 score cp 20 nodes 1 pv e2e5")
						+ "\n]",
				StandardCharsets.UTF_8);

		RecordLc0Exporter.export(input, stem, null);

		String meta = Files.readString(dir.resolve("dataset.lc0.meta.json"), StandardCharsets.UTF_8);
		assertContains(meta, "\"rows_written\": 1", "LC0 exporter written count");
		assertContains(meta, "\"rows_skipped\": 1", "LC0 exporter skipped illegal move count");
		assertTrue(Files.size(dir.resolve("dataset.lc0.inputs.npy")) > 0L, "LC0 inputs file written");
		assertTrue(Files.size(dir.resolve("dataset.lc0.policy.npy")) > 0L, "LC0 policy file written");
		assertTrue(Files.size(dir.resolve("dataset.lc0.value.npy")) > 0L, "LC0 value file written");

		System.out.println("RecordLC0ExporterRegressionTest: all checks passed");
	}

    /**
     * Builds a minimal record JSON object.
     *
     * @param position FEN position
     * @param analysis UCI engine info line
     * @return JSON object
     */
	private static String recordJson(String position, String analysis) {
		return "{\"created\":1,\"engine\":\"Stockfish\",\"position\":\"" + position
				+ "\",\"description\":\"\",\"tags\":[],\"analysis\":[\"" + analysis + "\"]}";
	}

    /**
     * Verifies that a string contains a substring.
     *
     * @param value value to inspect
     * @param needle expected substring
     * @param label assertion label
     */
	private static void assertContains(String value, String needle, String label) {
		if (!value.contains(needle)) {
			throw new AssertionError(label + ": expected to find " + needle + " in " + value);
		}
	}
}
