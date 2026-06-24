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
import java.util.Locale;

import application.Main;
import application.cli.RecordIO;
import chess.io.DatasetManifest;
import chess.io.Provenance;
import chess.io.RecordRowHash;
import chess.io.RecordSplitter;
import chess.io.RecordSplitter.SplitSpec;
import chess.io.RecordSplitter.Strategy;
import utility.Argv;
import utility.Json;

/**
 * CLI handler for {@code crtk record split}.
 *
 * <p>Streams a {@code .record} JSON-array or JSONL input through
 * {@link RecordSplitter#bucketFor(String, SplitSpec, long, Strategy)}, writing
 * one JSONL output per declared split, and stamps a
 * {@code crtk.dataset.manifest.v1} sidecar next to each output so the
 * resulting splits drop straight into the existing
 * {@code crtk dataset verify}/{@code audit}/{@code diff} surface.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class RecordSplitCommand {

	/**
	 * Default ordered split names mapped to a {@code train:val:test} ratio spec.
	 */
	private static final List<String> DEFAULT_NAMES = List.of("train", "val", "test");

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
	private RecordSplitCommand() {
		// utility
	}

	/**
	 * Runs {@code crtk record split --input PATH --output PREFIX --split A:B:C
	 * --seed N [--split-by fen] [--row-hashes]}.
	 *
	 * @param argv parsed arguments
	 */
	public static void runSplit(Argv argv) {
		Path input = argv.path("--input", "-i");
		Path outputPrefix = argv.path("--output", "-o");
		String splitRaw = argv.string("--split");
		Long seedOpt = argv.lng("--seed");
		String strategyName = argv.string("--split-by");
		boolean writeRowHashes = argv.flag("--row-hashes");
		argv.ensureConsumed();
		if (input == null || outputPrefix == null || splitRaw == null || seedOpt == null) {
			throw new CommandFailure(
					"Usage: crtk record split --input PATH --output PREFIX --split A:B:C --seed N "
							+ "[--split-by fen] [--row-hashes]",
					USAGE_FAILURE_EXIT);
		}
		if (!Files.isRegularFile(input)) {
			throw new CommandFailure(
					"crtk record split: --input '" + input + "' is not a regular file",
					USAGE_FAILURE_EXIT);
		}
		Strategy strategy = resolveStrategy(strategyName);
		SplitSpec spec;
		try {
			spec = SplitSpec.parse(splitRaw, DEFAULT_NAMES);
		} catch (IllegalArgumentException ex) {
			throw new CommandFailure("crtk record split: " + ex.getMessage(), USAGE_FAILURE_EXIT);
		}

		LinkedHashMap<String, Path> outputs = new LinkedHashMap<>();
		LinkedHashMap<String, BufferedWriter> writers = new LinkedHashMap<>();
		LinkedHashMap<String, Long> counts = new LinkedHashMap<>();
		List<String> rowHashes = new ArrayList<>();
		LinkedHashMap<String, List<String>> rowHashesBySplit = new LinkedHashMap<>();
		long[] totalSeen = { 0L };
		try {
			openOutputs(outputPrefix, spec, outputs, writers, counts);
			for (String name : spec.names()) {
				rowHashesBySplit.put(name, new ArrayList<>());
			}
		} catch (IOException ex) {
			closeQuietly(writers);
			throw new CommandFailure(
					"crtk record split: failed to open output: " + ex.getMessage(),
					IO_FAILURE_EXIT);
		}

		try {
			RecordIO.streamRecordJson(input, json -> {
				totalSeen[0]++;
				String rowHash = RecordRowHash.rawRecordJsonV1(json);
				rowHashes.add(rowHash);
				String bucket = RecordSplitter.bucketFor(json, spec, seedOpt.longValue(), strategy);
				try {
					BufferedWriter writer = writers.get(bucket);
					writer.write(json);
					writer.newLine();
				} catch (IOException ex) {
					throw new UncheckedIOException(ex);
				}
				rowHashesBySplit.get(bucket).add(rowHash);
				counts.merge(bucket, 1L, Long::sum);
			});
		} catch (UncheckedIOException ex) {
			closeQuietly(writers);
			throw new CommandFailure(
					"crtk record split: write failed: " + ex.getCause().getMessage(),
					IO_FAILURE_EXIT);
		} catch (IOException ex) {
			closeQuietly(writers);
			throw new CommandFailure(
					"crtk record split: failed to read input: " + ex.getMessage(),
					IO_FAILURE_EXIT);
		}
		closeQuietly(writers);

		try {
			LinkedHashMap<String, Path> rowHashOutputs = writeRowHashes
					? writeRowHashSidecars(outputs, rowHashesBySplit)
					: new LinkedHashMap<>();
			writeManifests(input, outputs, rowHashOutputs, splitRaw, seedOpt.longValue(), strategy);
			writeAggregateManifest(input, outputPrefix, outputs, splitRaw, seedOpt.longValue(),
					strategy, counts, totalSeen[0], rowHashes, rowHashOutputs);
		} catch (IOException ex) {
			throw new CommandFailure(
					"crtk record split: failed to write manifest sidecar: " + ex.getMessage(),
					IO_FAILURE_EXIT);
		}

		System.out.println(renderSummaryJson(input, outputPrefix, spec, totalSeen[0],
				counts, strategy, seedOpt.longValue()));
	}

	/**
	 * Resolves the user-supplied strategy token to a {@link Strategy} value.
	 *
	 * @param strategyName user-supplied strategy name; {@code null} defaults to {@code "fen"}
	 * @return resolved strategy
	 */
	private static Strategy resolveStrategy(String strategyName) {
		try {
			return RecordSplitter.parseStrategy(strategyName);
		} catch (IllegalArgumentException ex) {
			throw new CommandFailure(
					"crtk record split: " + ex.getMessage(),
					USAGE_FAILURE_EXIT);
		}
	}

	/**
	 * Opens an output writer for each declared split.
	 *
	 * @param outputPrefix source output prefix
	 * @param spec         split spec
	 * @param outputs      destination map (name → path)
	 * @param writers      destination map (name → writer)
	 * @param counts       destination map (name → 0L)
	 * @throws IOException when an output cannot be created
	 */
	private static void openOutputs(Path outputPrefix, SplitSpec spec,
			LinkedHashMap<String, Path> outputs, LinkedHashMap<String, BufferedWriter> writers,
			LinkedHashMap<String, Long> counts) throws IOException {
		Path parent = outputPrefix.getParent();
		if (parent != null && !Files.isDirectory(parent)) {
			Files.createDirectories(parent);
		}
		for (String name : spec.names()) {
			Path target = RecordSplitter.outputPathFor(outputPrefix, name);
			outputs.put(name, target);
			writers.put(name, Files.newBufferedWriter(target, StandardCharsets.UTF_8));
			counts.put(name, 0L);
		}
	}

	/**
	 * Writes a {@code crtk.dataset.manifest.v1} sidecar next to each split output.
	 *
	 * @param input    input record file
	 * @param outputs  per-split output map
	 * @param splitRaw raw split spec string
	 * @param seed     deterministic seed
	 * @param strategy group-key strategy
	 * @param rowHashOutputs row-hash output sinks
	 * @throws IOException when writing a manifest fails
	 */
	private static void writeManifests(Path input, LinkedHashMap<String, Path> outputs,
			LinkedHashMap<String, Path> rowHashOutputs,
			String splitRaw, long seed, Strategy strategy) throws IOException {
		String exporter = "record.split." + strategy.name().toLowerCase(Locale.ROOT);
		List<String> argv = new ArrayList<>(Main.lastInvocationArgv());
		argv.add("# --split=" + splitRaw + " --seed=" + seed
				+ " --split-by=" + strategy.name().toLowerCase(Locale.ROOT));
		for (String name : outputs.keySet()) {
			Path output = outputs.get(name);
			Path rowHashes = rowHashOutputs.get(name);
			Path manifest = output.resolveSibling(output.getFileName() + ".manifest.json");
			DatasetManifest.Builder builder = DatasetManifest.builder()
					.exporter(exporter)
					.crtkVersion(VersionCommand.VERSION)
					.gitCommit(Provenance.gitCommit())
					.argv(argv)
					.input(input)
					.output(output)
					.metadata("split_strategy", RecordSplitter.strategyToken(strategy))
					.metadata("split_spec", splitRaw)
					.metadataNumber("seed", seed);
			if (rowHashes != null) {
				builder.output(rowHashes)
						.metadata("row_hash_algorithm", RecordRowHash.RAW_RECORD_JSON_V1)
						.metadata("row_hash_sidecar", rowHashes.getFileName().toString());
			}
			builder.writeTo(manifest);
		}
	}

	/**
	 * Writes optional per-split row-hash sidecars.
	 *
	 * @param outputs per-split output map
	 * @param rowHashesBySplit per-split row hashes in output row order
	 * @return per-split row-hash sidecar paths
	 * @throws IOException when writing a sidecar fails
	 */
	private static LinkedHashMap<String, Path> writeRowHashSidecars(
			LinkedHashMap<String, Path> outputs,
			LinkedHashMap<String, List<String>> rowHashesBySplit) throws IOException {
		LinkedHashMap<String, Path> rowHashOutputs = new LinkedHashMap<>();
		for (String name : outputs.keySet()) {
			Path target = rowHashPathFor(outputs.get(name));
			List<String> hashes = rowHashesBySplit.get(name);
			String content = hashes.isEmpty() ? "" : String.join("\n", hashes) + "\n";
			Files.writeString(target, content, StandardCharsets.UTF_8);
			rowHashOutputs.put(name, target);
		}
		return rowHashOutputs;
	}

	/**
	 * Computes the optional row-hash sidecar path for a split output.
	 *
	 * @param splitOutput split JSONL output path
	 * @return row-hash sidecar path
	 */
	private static Path rowHashPathFor(Path splitOutput) {
		String name = splitOutput.getFileName().toString();
		String rowHashName = name.endsWith(".jsonl")
				? name.substring(0, name.length() - ".jsonl".length()) + ".rowhashes.txt"
				: name + ".rowhashes.txt";
		return splitOutput.resolveSibling(rowHashName);
	}

	/**
	 * Writes the top-level split manifest covering all split outputs.
	 *
	 * @param input input record file
	 * @param outputPrefix source output prefix
	 * @param outputs per-split output map
	 * @param splitRaw raw split spec string
	 * @param seed deterministic seed
	 * @param strategy group-key strategy
	 * @param counts per-split row counts
	 * @param totalSeen total input rows seen
	 * @param rowHashes per-row raw-record hashes
	 * @param rowHashOutputs optional per-split row-hash sidecar outputs
	 * @throws IOException when writing the manifest fails
	 */
	private static void writeAggregateManifest(
			Path input,
			Path outputPrefix,
			LinkedHashMap<String, Path> outputs,
			String splitRaw,
			long seed,
			Strategy strategy,
			LinkedHashMap<String, Long> counts,
			long totalSeen,
			List<String> rowHashes,
			LinkedHashMap<String, Path> rowHashOutputs) throws IOException {
		DatasetManifest.Builder builder = DatasetManifest.builder()
				.exporter("record.split.aggregate")
				.crtkVersion(VersionCommand.VERSION)
				.gitCommit(Provenance.gitCommit())
				.argv(Main.lastInvocationArgv())
				.input(input)
				.metadata("split_strategy", RecordSplitter.strategyToken(strategy))
				.metadata("split_spec", splitRaw)
				.metadataNumber("seed", seed)
				.metadataNumber("rows_in", totalSeen)
				.metadata("row_hash_algorithm", RecordRowHash.RAW_RECORD_JSON_V1)
				.metadata("row_hashes_sorted_sha256", RecordRowHash.sortedMultisetDigest(rowHashes))
				.metadataBoolean("row_hash_sidecars", !rowHashOutputs.isEmpty());
		for (String name : outputs.keySet()) {
			Path output = outputs.get(name);
			builder.output(output)
					.metadataNumber("split_" + name + "_rows", counts.getOrDefault(name, 0L));
			Path rowHashesPath = rowHashOutputs.get(name);
			if (rowHashesPath != null) {
				builder.output(rowHashesPath);
			}
		}
		builder.writeTo(aggregateManifestPath(outputPrefix));
	}

	/**
	 * Computes the top-level split manifest path.
	 *
	 * @param outputPrefix source output prefix
	 * @return aggregate manifest path
	 */
	private static Path aggregateManifestPath(Path outputPrefix) {
		return outputPrefix.resolveSibling(outputPrefix.getFileName() + ".split.manifest.json");
	}

	/**
	 * Quietly closes all currently-open writers.
	 *
	 * @param writers writers to close
	 */
	private static void closeQuietly(LinkedHashMap<String, BufferedWriter> writers) {
		for (BufferedWriter writer : writers.values()) {
			try {
				writer.close();
			} catch (IOException ignored) {
				// best-effort
			}
		}
	}

	/**
	 * Renders the agent-consumable JSON summary printed on standard output.
	 *
	 * @param input        input record file
	 * @param outputPrefix source output prefix
	 * @param spec         split spec
	 * @param totalSeen    total record count seen across the input
	 * @param counts       per-split record counts
	 * @param strategy     group-key strategy
	 * @param seed         deterministic seed
	 * @return single-line JSON object
	 */
	private static String renderSummaryJson(Path input, Path outputPrefix, SplitSpec spec,
			long totalSeen, LinkedHashMap<String, Long> counts, Strategy strategy, long seed) {
		StringBuilder sb = new StringBuilder().append('{');
		sb.append("\"input\":\"").append(Json.esc(input.toString())).append('"');
		sb.append(",\"outputPrefix\":\"").append(Json.esc(outputPrefix.toString())).append('"');
		sb.append(",\"strategy\":\"").append(strategy.name().toLowerCase(Locale.ROOT)).append('"');
		sb.append(",\"seed\":").append(seed);
		sb.append(",\"total\":").append(totalSeen);
		sb.append(",\"splits\":{");
		boolean first = true;
		for (String name : spec.names()) {
			if (!first) {
				sb.append(',');
			}
			first = false;
			sb.append('"').append(name).append("\":").append(counts.getOrDefault(name, 0L));
		}
		sb.append('}');
		sb.append('}');
		return sb.toString();
	}
}
