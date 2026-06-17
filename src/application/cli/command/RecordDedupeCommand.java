package application.cli.command;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import application.Main;
import application.cli.RecordIO;
import chess.io.DatasetManifest;
import chess.io.Provenance;
import chess.io.RecordDeduper;
import chess.io.RecordDeduper.Keep;
import chess.io.RecordDeduper.Key;
import utility.Argv;
import utility.Json;

/**
 * CLI handler for {@code crtk record dedupe}.
 *
 * <p>The command streams a record JSON array or JSONL file, keeps exactly one
 * row per selected key, writes JSONL output in deterministic retained-row
 * order, and stamps a dataset manifest beside the output.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class RecordDedupeCommand {

	/**
	 * Exit code used for argument-shape failures.
	 */
	private static final int USAGE_FAILURE_EXIT = 2;

	/**
	 * Exit code used for input/output failures.
	 */
	private static final int IO_FAILURE_EXIT = 3;

	/**
	 * Utility class; prevent instantiation.
	 */
	private RecordDedupeCommand() {
		// utility
	}

	/**
	 * Runs {@code crtk record dedupe --input PATH --output PATH [--key NAME]
	 * [--keep first|last]}.
	 *
	 * @param argv parsed arguments
	 */
	public static void runDedupe(Argv argv) {
		Path input = argv.path("--input", "-i");
		Path output = argv.path("--output", "-o");
		String keyRaw = argv.string("--key");
		String keepRaw = argv.string("--keep");
		argv.ensureConsumed();
		if (input == null || output == null) {
			throw new CommandFailure(
					"Usage: crtk record dedupe --input PATH --output PATH "
							+ "[--key position-signature|fen-exact|row-hash] [--keep first|last]",
					USAGE_FAILURE_EXIT);
		}
		if (!Files.isRegularFile(input)) {
			throw new CommandFailure(
					"crtk record dedupe: --input '" + input + "' is not a regular file",
					USAGE_FAILURE_EXIT);
		}
		Key key = parseKey(keyRaw);
		Keep keep = parseKeep(keepRaw);
		LinkedHashMap<String, String> rows = new LinkedHashMap<>();
		long[] rowsIn = { 0L };
		try {
			RecordIO.streamRecordJson(input, json -> {
				rowsIn[0]++;
				merge(rows, RecordDeduper.keyFor(json, key), json, keep);
			});
			writeRows(output, rows);
			long rowsOut = rows.size();
			writeManifest(input, output, key, keep, rowsIn[0], rowsOut);
		} catch (UncheckedIOException ex) {
			throw new CommandFailure(
					"crtk record dedupe: write failed: " + ex.getCause().getMessage(),
					IO_FAILURE_EXIT);
		} catch (IOException ex) {
			throw new CommandFailure(
					"crtk record dedupe: I/O failure: " + ex.getMessage(),
					IO_FAILURE_EXIT);
		}
		long rowsOut = rows.size();
		System.out.println(renderSummaryJson(input, output, key, keep, rowsIn[0], rowsOut));
	}

	/**
	 * Parses a dedupe key and maps validation failures to CLI usage.
	 *
	 * @param raw raw token
	 * @return parsed key
	 */
	private static Key parseKey(String raw) {
		try {
			return RecordDeduper.parseKey(raw);
		} catch (IllegalArgumentException ex) {
			throw new CommandFailure("crtk record dedupe: " + ex.getMessage(), USAGE_FAILURE_EXIT);
		}
	}

	/**
	 * Parses a keep policy and maps validation failures to CLI usage.
	 *
	 * @param raw raw token
	 * @return parsed keep policy
	 */
	private static Keep parseKeep(String raw) {
		try {
			return RecordDeduper.parseKeep(raw);
		} catch (IllegalArgumentException ex) {
			throw new CommandFailure("crtk record dedupe: " + ex.getMessage(), USAGE_FAILURE_EXIT);
		}
	}

	/**
	 * Merges one row into the retained-row map according to the keep policy.
	 *
	 * @param rows retained rows keyed by dedupe key
	 * @param key  dedupe key
	 * @param json raw record JSON object
	 * @param keep keep policy
	 */
	private static void merge(LinkedHashMap<String, String> rows, String key, String json, Keep keep) {
		if (!rows.containsKey(key)) {
			rows.put(key, json);
			return;
		}
		if (keep == Keep.LAST) {
			rows.remove(key);
			rows.put(key, json);
		}
	}

	/**
	 * Writes retained rows as JSONL.
	 *
	 * @param output destination path
	 * @param rows   retained rows
	 * @throws IOException when writing fails
	 */
	private static void writeRows(Path output, LinkedHashMap<String, String> rows) throws IOException {
		Path parent = output.getParent();
		if (parent != null && !Files.isDirectory(parent)) {
			Files.createDirectories(parent);
		}
		try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
			for (String json : rows.values()) {
				writer.write(json);
				writer.newLine();
			}
		}
	}

	/**
	 * Writes the dataset manifest sidecar.
	 *
	 * @param input  input record file
	 * @param output deduplicated output file
	 * @param key    dedupe key strategy
	 * @param keep   keep policy
	 * @param rowsIn rows read
	 * @param rowsOut rows written
	 * @throws IOException when hashing or writing fails
	 */
	private static void writeManifest(Path input, Path output, Key key, Keep keep,
			long rowsIn, long rowsOut) throws IOException {
		String keyToken = RecordDeduper.keyToken(key);
		String keepToken = RecordDeduper.keepToken(keep);
		List<String> argv = new ArrayList<>(Main.lastInvocationArgv());
		argv.add("# --key=" + keyToken + " --keep=" + keepToken);
		Path manifest = output.resolveSibling(output.getFileName() + ".manifest.json");
		DatasetManifest.builder()
				.exporter("record.dedupe." + keyToken)
				.crtkVersion(VersionCommand.VERSION)
				.gitCommit(Provenance.gitCommit())
				.argv(argv)
				.input(input)
				.output(output)
				.metadata("dedup_key", keyToken)
				.metadata("keep", keepToken)
				.metadataNumber("rows_in", rowsIn)
				.metadataNumber("rows_out", rowsOut)
				.metadataNumber("duplicates_removed", rowsIn - rowsOut)
				.writeTo(manifest);
	}

	/**
	 * Renders the machine-readable command summary.
	 *
	 * @param input  input path
	 * @param output output path
	 * @param key    key strategy
	 * @param keep   keep policy
	 * @param rowsIn rows read
	 * @param rowsOut rows written
	 * @return single-line JSON object
	 */
	private static String renderSummaryJson(Path input, Path output, Key key, Keep keep,
			long rowsIn, long rowsOut) {
		long duplicatesRemoved = rowsIn - rowsOut;
		return "{"
				+ "\"input\":\"" + Json.esc(input.toString()) + "\","
				+ "\"output\":\"" + Json.esc(output.toString()) + "\","
				+ "\"key\":\"" + RecordDeduper.keyToken(key) + "\","
				+ "\"keep\":\"" + RecordDeduper.keepToken(keep) + "\","
				+ "\"rowsIn\":" + rowsIn + ","
				+ "\"rowsOut\":" + rowsOut + ","
				+ "\"duplicatesRemoved\":" + duplicatesRemoved
				+ "}";
	}
}
