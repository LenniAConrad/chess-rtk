package testing;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import application.cli.command.RecordCommands;
import utility.Argv;

/**
 * Focused regression harness for {@code record-to-training-jsonl}.
 */
public final class RecordTrainingJsonlRegressionTest {

	/**
	 * Creates a new record training jsonl regression test instance.
	 */
	private RecordTrainingJsonlRegressionTest() {
		// utility
	}

	/**
	 * Handles main.
	 * @param args args
	 * @throws Exception if the operation fails
	 */
	public static void main(String[] args) throws Exception {
		Path dir = Files.createTempDirectory("crtk-training-jsonl-test");
		Path input = dir.resolve("records.jsonl");
		Path output = dir.resolve("training.jsonl");

		String parent = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
		String otherParent = "rnbqkbnr/pppp1ppp/8/4p3/8/8/PPPPPPPP/RNBQKBNR w KQkq e6 0 2";
		List<String> records = List.of(
				record(parent,
						"rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1",
						"info depth 10 multipv 1 score cp 500 nodes 100 pv e7e5"),
				record(parent,
						"rnbqkbnr/pppppppp/8/8/3P4/8/PPP1PPPP/RNBQKBNR b KQkq - 0 1",
						"info depth 1 multipv 1 score cp 20 nodes 10 pv d7d5"),
				record(otherParent,
						"rnbqkbnr/pppp1ppp/8/4p3/8/2N5/PPPPPPPP/R1BQKBNR b KQkq - 1 2",
						"info depth 1 multipv 1 score cp 10 nodes 10 pv b8c6"));
		Files.write(input, records, StandardCharsets.UTF_8);

		RecordCommands.runRecordToTrainingJsonl(new Argv(new String[] {
				"--input", input.toString(),
				"--output", output.toString(),
				"--filter", "depth>=10",
				"--include-engine-metadata"
		}));

		List<String> lines = Files.readAllLines(output, StandardCharsets.UTF_8);
		assertEquals(3, lines.size(), "row count");
		assertContains(lines.get(0), "\"label_status\":\"verified_puzzle\"");
		assertContains(lines.get(0), "\"coarse_label\":1");
		assertContains(lines.get(0), "\"fine_label\":2");
		assertContains(lines.get(0), "\"best_move\":\"e7e5\"");
		assertContains(lines.get(1), "\"label_status\":\"verified_near_puzzle\"");
		assertContains(lines.get(1), "\"coarse_label\":1");
		assertContains(lines.get(1), "\"fine_label\":1");
		assertContains(lines.get(1), "\"verification_status\":\"sister_parent_matched\"");
		assertContains(lines.get(2), "\"label_status\":\"known_non_puzzle\"");
		assertContains(lines.get(2), "\"coarse_label\":0");
		assertContains(lines.get(2), "\"fine_label\":0");
		for (String line : lines) {
			assertContains(line, "\"fen\":");
			assertContains(line, "\"source_record_index\":");
		}
		System.out.println("RecordTrainingJsonlRegressionTest OK: " + output);
	}

	/**
	 * Handles record.
	 * @param parent parent
	 * @param position position
	 * @param analysis analysis
	 * @return computed value
	 */
	private static String record(String parent, String position, String analysis) {
		return "{\"created\":1,\"engine\":\"Stockfish\",\"parent\":\"" + parent
				+ "\",\"position\":\"" + position
				+ "\",\"description\":\"\",\"tags\":[],\"analysis\":[\"" + analysis + "\"]}";
	}

	/**
	 * Handles assert contains.
	 * @param value value
	 * @param needle needle
	 */
	private static void assertContains(String value, String needle) {
		if (!value.contains(needle)) {
			throw new AssertionError("Expected to find " + needle + " in " + value);
		}
	}

	/**
	 * Handles assert equals.
	 * @param expected expected
	 * @param actual actual
	 * @param label label
	 */
	private static void assertEquals(int expected, int actual, String label) {
		if (expected != actual) {
			throw new AssertionError(label + ": expected " + expected + " but got " + actual);
		}
	}
}
