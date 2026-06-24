package application.cli.command;

import static application.cli.Constants.OPT_FILTER;
import static application.cli.Constants.OPT_FILTER_SHORT;
import static application.cli.Constants.OPT_INPUT;
import static application.cli.Constants.OPT_INPUT_SHORT;
import static application.cli.Constants.OPT_MAX_RECORDS;
import static application.cli.Constants.OPT_NONPUZZLES;
import static application.cli.Constants.OPT_OUTPUT;
import static application.cli.Constants.OPT_OUTPUT_SHORT;
import static application.cli.Constants.OPT_WEIGHTS;
import static application.cli.Constants.OPT_PUZZLES;
import static application.cli.Constants.OPT_RATINGS_CSV;
import static application.cli.Constants.OPT_RECURSIVE;
import static application.cli.Constants.OPT_ROW_HASHES;
import static application.cli.Constants.OPT_THREADS;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;
import static application.cli.Constants.OPT_EXPORT_ALL;
import static application.cli.Constants.OPT_EXPORT_ALL_SHORT;
import static application.cli.Constants.OPT_SIDELINES;
import static application.cli.Constants.OPT_LABEL_FILTER;
import static application.cli.Constants.OPT_MAX_NEGATIVES;
import static application.cli.Constants.OPT_MAX_POSITIVES;
import static application.cli.Constants.OPT_CSV;
import static application.cli.Constants.OPT_CSV_OUTPUT;
import static application.cli.Constants.OPT_CSV_OUTPUT_SHORT;
import static application.cli.PathOps.deriveOutputPath;
import static application.cli.PathOps.dumpPath;
import static application.cli.PathOps.ensureParentDir;
import static application.cli.command.RecordCommandSupport.byteProgress;
import static application.cli.command.RecordCommandSupport.exitWithError;
import static application.cli.command.RecordCommandSupport.fileProgressBar;
import static application.cli.command.RecordCommandSupport.finishProgress;
import static application.cli.command.RecordCommandSupport.requireReadableFile;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import application.Config;
import application.console.Bar;
import chess.io.Converter;
import chess.io.ClassifierDatasetExporter;
import chess.io.DatasetManifest;
import chess.io.PuzzleEloExporter;
import chess.io.RecordPgnExporter;
import chess.io.Writer;
import chess.struct.Record;
import chess.uci.Filter;
import chess.uci.Filter.FilterDSL;
import utility.Argv;

import static application.cli.RecordIO.streamRecordJson;

/**
 * Implements record conversion and merge/split commands.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */

public final class RecordCommands {

	/**
	 * Maximum number of JSON records to buffer before flushing.
	 */
	private static final int RECORD_BATCH_SIZE = 1000;
	/**
	 * Shared ext json constant.
	 */
	private static final String EXT_JSON = ".json";
	/**
	 * Shared ext jsonl constant.
	 */
	private static final String EXT_JSONL = ".jsonl";
	/**
	 * Shared ext record constant.
	 */
	private static final String EXT_RECORD = ".record";
	/**
	 * Shared Elo-rated puzzle jsonl constant.
	 */
	private static final String EXT_PUZZLE_ELO_JSONL = ".puzzle-elo.jsonl";
	/**
	 * Current command label for plain export diagnostics.
	 */
	private static final String RECORD_EXPORT_PLAIN = "record export plain";
	/**
	 * Current command label for CSV export diagnostics.
	 */
	private static final String RECORD_EXPORT_CSV = "record export csv";
	/**
	 * Shared classifier command label constant.
	 */
	private static final String COMMAND_RECORD_DATASET_CLASSIFIER = "record dataset classifier";
	/**
	 * Shared Elo-rated puzzle export command label constant.
	 */
	private static final String COMMAND_RECORD_EXPORT_PUZZLE_ELO_JSONL = "record export puzzle-elo-jsonl";
	/**
	 * Shared positional input hint constant.
	 */
	private static final String INPUTS_OR_POSITIONAL_HINT = " (or positional files/dirs)";
	/**
	 * Shared non-negative error suffix constant.
	 */
	private static final String MUST_BE_NON_NEGATIVE = " must be non-negative";

	/**
	 * Utility class; prevent instantiation.
	 */
	private RecordCommands() {
		// utility
	}

	/**
	 * Handles {@code record export plain}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runRecordToPlain(Argv a) {
		boolean exportAll = a.flag(OPT_SIDELINES, OPT_EXPORT_ALL, OPT_EXPORT_ALL_SHORT);
		String filterDsl = a.string(OPT_FILTER, OPT_FILTER_SHORT);
		Path in = a.pathRequired(OPT_INPUT, OPT_INPUT_SHORT);
		Path out = a.path(OPT_OUTPUT, OPT_OUTPUT_SHORT);
		boolean csv = a.flag(OPT_CSV);
		Path csvOut = a.path(OPT_CSV_OUTPUT, OPT_CSV_OUTPUT_SHORT);
		a.ensureConsumed();
		requireReadableFile(in, RECORD_EXPORT_PLAIN);

		Filter filter = null;
		if (filterDsl != null && !filterDsl.isEmpty()) {
			filter = FilterDSL.fromString(filterDsl);
		}

		if (out == null) {
			out = deriveOutputPath(in, ".plain");
		}
		Bar plainBar = fileProgressBar(in, 1, RECORD_EXPORT_PLAIN);
		try {
			Converter.recordToPlain(exportAll, filter, in, out, byteProgress(plainBar));
		} finally {
			finishProgress(plainBar);
		}
		if (csv || csvOut != null) {
			if (csvOut == null) {
				csvOut = deriveOutputPath(in, ".csv");
			}
			Bar csvBar = fileProgressBar(in, 2, RECORD_EXPORT_CSV);
			try {
				Converter.recordToCsv(filter, in, csvOut, byteProgress(csvBar));
			} finally {
				finishProgress(csvBar);
			}
		}
	}

	/**
	 * Handles {@code record export csv}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runRecordToCsv(Argv a) {
		String filterDsl = a.string(OPT_FILTER, OPT_FILTER_SHORT);
		Path in = a.pathRequired(OPT_INPUT, OPT_INPUT_SHORT);
		Path out = a.path(OPT_OUTPUT, OPT_OUTPUT_SHORT);
		a.ensureConsumed();
		requireReadableFile(in, RECORD_EXPORT_CSV);

		Filter filter = null;
		if (filterDsl != null && !filterDsl.isEmpty()) {
			filter = FilterDSL.fromString(filterDsl);
		}

		if (out == null) {
			out = deriveOutputPath(in, ".csv");
		}
		Bar bar = fileProgressBar(in, 2, RECORD_EXPORT_CSV);
		try {
			Converter.recordToCsv(filter, in, out, byteProgress(bar));
		} finally {
			finishProgress(bar);
		}
	}

	/**
	 * Handles {@code record dataset npy}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runRecordToDataset(Argv a) {
		Path in = a.pathRequired(OPT_INPUT, OPT_INPUT_SHORT);
		Path out = a.path(OPT_OUTPUT, OPT_OUTPUT_SHORT);
		boolean rowHashes = a.flag(OPT_ROW_HASHES);
		a.ensureConsumed();

		if (out == null) {
			out = deriveOutputPath(in, ".dataset");
		}
		Path rowHashPath = rowHashes ? DatasetManifestSupport.rowHashPathFor(out) : null;
		Path manifestRowHashPath = rowHashPath;

		Bar bar = fileProgressBar(in, 1, "record dataset npy");
		boolean progressFinished = false;
		try (BufferedWriter rowHashWriter = rowHashPath == null
				? null
				: DatasetManifestSupport.openRowHashWriter(rowHashPath)) {
			chess.io.RecordDatasetExporter.export(in, out, byteProgress(bar),
					DatasetManifestSupport.rowHashSink(rowHashWriter));
			finishProgress(bar);
			progressFinished = true;
			flushSidecar(rowHashWriter);
			DatasetManifestSupport.write(
					"record.dataset.npy",
					in,
					java.util.List.of(
							Path.of(out + ".features.npy"),
							Path.of(out + ".labels.npy")),
					null,
					Path.of(out + ".manifest.json"),
					builder -> DatasetManifestSupport.addRowHashSidecar(builder, manifestRowHashPath));
			System.out.printf("Wrote %s.features.npy and %s.labels.npy%n", out, out);
		} catch (IOException e) {
			finishProgress(bar);
			progressFinished = true;
			deleteQuietly(rowHashPath);
			exitWithError("record dataset npy: failed to export dataset: " + e.getMessage(), e, false);
		} finally {
			if (!progressFinished) {
				finishProgress(bar);
			}
		}
	}

	/**
	 * Handles {@code record dataset lc0}.
	 *
	 * @param argv argument parser for the subcommand
	 */
	public static void runRecordToLc0(Argv argv) {
		Path in = argv.pathRequired(OPT_INPUT, OPT_INPUT_SHORT);
		Path out = argv.path(OPT_OUTPUT, OPT_OUTPUT_SHORT);
		Path weights = argv.path(OPT_WEIGHTS);
		boolean rowHashes = argv.flag(OPT_ROW_HASHES);
		argv.ensureConsumed();

		if (out == null) {
			out = deriveOutputPath(in, ".lc0");
		}
		Path rowHashPath = rowHashes ? DatasetManifestSupport.rowHashPathFor(out) : null;
		Path manifestRowHashPath = rowHashPath;

		Bar bar = fileProgressBar(in, 1, "record dataset lc0");
		boolean progressFinished = false;
		try (BufferedWriter rowHashWriter = rowHashPath == null
				? null
				: DatasetManifestSupport.openRowHashWriter(rowHashPath)) {
			chess.io.RecordLc0Exporter.export(in, out, weights, byteProgress(bar),
					DatasetManifestSupport.rowHashSink(rowHashWriter));
			finishProgress(bar);
			progressFinished = true;
			flushSidecar(rowHashWriter);
			DatasetManifestSupport.write(
					"record.dataset.lc0",
					in,
					java.util.List.of(
							Path.of(out + ".lc0.inputs.npy"),
							Path.of(out + ".lc0.policy.npy"),
							Path.of(out + ".lc0.value.npy")),
					weights,
					Path.of(out + ".lc0.manifest.json"),
					builder -> DatasetManifestSupport.addRowHashSidecar(builder, manifestRowHashPath));
			System.out.printf(
					"Wrote %s.lc0.inputs.npy, %s.lc0.policy.npy, %s.lc0.value.npy, %s.lc0.meta.json%n",
					out, out, out, out);
		} catch (IOException e) {
			finishProgress(bar);
			progressFinished = true;
			deleteQuietly(rowHashPath);
			exitWithError("record dataset lc0: failed to export LC0 dataset: " + e.getMessage(), e, false);
		} finally {
			if (!progressFinished) {
				finishProgress(bar);
			}
		}
	}

	/**
	 * Handles {@code record dataset classifier}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runRecordToClassifier(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		boolean recursive = a.flag(OPT_RECURSIVE);
		String rowFilterDsl = a.string(OPT_FILTER, OPT_FILTER_SHORT);
		String labelFilterDsl = a.string(OPT_LABEL_FILTER);
		Long maxPositivesOpt = a.lng(OPT_MAX_POSITIVES);
		Long maxNegativesOpt = a.lng(OPT_MAX_NEGATIVES);
		Path out = a.path(OPT_OUTPUT, OPT_OUTPUT_SHORT);
		boolean rowHashes = a.flag(OPT_ROW_HASHES);

		List<String> inputs = new ArrayList<>();
		inputs.addAll(a.strings(OPT_INPUT, OPT_INPUT_SHORT));
		inputs.addAll(a.positionals());
		a.ensureConsumed();

		if (inputs.isEmpty()) {
			exitWithError(COMMAND_RECORD_DATASET_CLASSIFIER + ": missing " + OPT_INPUT + INPUTS_OR_POSITIONAL_HINT);
		}

		if (out == null) {
			out = deriveClassifierOutputOrExit(inputs);
		}
		Path rowHashPath = rowHashes ? DatasetManifestSupport.rowHashPathFor(out) : null;

		long maxPositives = maxPositivesOpt == null
				? ClassifierDatasetExporter.NO_CLASS_CAP
				: maxPositivesOpt.longValue();
		long maxNegatives = maxNegativesOpt == null
				? ClassifierDatasetExporter.NO_CLASS_CAP
				: maxNegativesOpt.longValue();
		if (maxPositives < 0) {
			exitWithError(COMMAND_RECORD_DATASET_CLASSIFIER + ": " + OPT_MAX_POSITIVES + MUST_BE_NON_NEGATIVE);
		}
		if (maxNegatives < 0) {
			exitWithError(COMMAND_RECORD_DATASET_CLASSIFIER + ": " + OPT_MAX_NEGATIVES + MUST_BE_NON_NEGATIVE);
		}

		Filter rowFilter = parseFilterOrExit(COMMAND_RECORD_DATASET_CLASSIFIER, OPT_FILTER, rowFilterDsl, verbose);
		Filter labelFilter = parseFilterOrExit(COMMAND_RECORD_DATASET_CLASSIFIER, OPT_LABEL_FILTER,
				labelFilterDsl, verbose);

		Config.reload();
		Filter fallbackLabelFilter = labelFilter == null ? Config.getPuzzleVerify() : null;
		String fallbackLabelFilterDsl = fallbackLabelFilter == null ? null : FilterDSL.toString(fallbackLabelFilter);

		List<Path> inputFiles = collectRecordInputsOrExit(COMMAND_RECORD_DATASET_CLASSIFIER, inputs, recursive, verbose);
		if (inputFiles.isEmpty()) {
			exitWithError(COMMAND_RECORD_DATASET_CLASSIFIER + ": no input files found");
		}

		ClassifierDatasetExporter.Options options = new ClassifierDatasetExporter.Options(
				rowFilter,
				rowFilterDsl,
				labelFilter,
				labelFilterDsl,
				fallbackLabelFilter,
				fallbackLabelFilterDsl,
				maxPositives,
				maxNegatives);

		Bar filesBar = progressBar(inputFiles.size(), "classifier files");
		final boolean[] progressFinished = { false };
		try (BufferedWriter rowHashWriter = rowHashPath == null
				? null
				: DatasetManifestSupport.openRowHashWriter(rowHashPath)) {
			ClassifierDatasetExporter.Summary summary =
					ClassifierDatasetExporter.export(inputFiles, out, options,
							filesBar == null ? null : progress -> updateClassifierProgress(filesBar, progress),
							null,
							DatasetManifestSupport.rowHashSink(rowHashWriter));
			finishProgress(filesBar);
			progressFinished[0] = true;
			flushSidecar(rowHashWriter);
			writeClassifierManifest(inputFiles, out, options, summary, rowHashPath);
			System.out.printf(
					"Wrote %s.classifier.inputs.npy and %s.classifier.labels.npy (%d rows: %d positive, %d negative; skipped %d invalid, %d unlabeled)%n",
					out,
					out,
					summary.rowsWritten(),
					summary.positives(),
					summary.negatives(),
					summary.skippedInvalid(),
					summary.skippedUnlabeled());
			} catch (IOException | RuntimeException ex) {
				finishProgress(filesBar);
				progressFinished[0] = true;
				deleteQuietly(rowHashPath);
				exitWithError(COMMAND_RECORD_DATASET_CLASSIFIER + ": failed to export dataset: " + ex.getMessage(),
						ex, verbose);
		} finally {
			if (!progressFinished[0]) {
				finishProgress(filesBar);
			}
		}
	}

	/**
	 * Writes classifier manifest metadata that downstream training jobs need to
	 * interpret labels without reading the exporter implementation.
	 *
	 * @param inputFiles input record files
	 * @param out output stem
	 * @param options classifier export options
	 * @param summary export counters
	 * @param rowHashPath optional row-hash sidecar path
	 */
	private static void writeClassifierManifest(
			List<Path> inputFiles,
			Path out,
			ClassifierDatasetExporter.Options options,
			ClassifierDatasetExporter.Summary summary,
			Path rowHashPath) {
		DatasetManifestSupport.write(
				"record.dataset.classifier",
				inputFiles,
				java.util.List.of(
						Path.of(out + ".classifier.inputs.npy"),
						Path.of(out + ".classifier.labels.npy")),
				null,
				Path.of(out + ".classifier.manifest.json"),
				builder -> {
					builder.metadata("label_policy", "classifier-binary-21plane-v1")
							.metadata("label_source", ClassifierDatasetExporter.labelSource(options))
							.metadata("positive_definition", ClassifierDatasetExporter.positiveDefinition(options))
							.metadataNumber("records_seen", summary.seen())
							.metadataNumber("rows_written", summary.rowsWritten())
							.metadataNumber("positives", summary.positives())
							.metadataNumber("negatives", summary.negatives())
							.metadataNumber("skipped_invalid", summary.skippedInvalid())
							.metadataNumber("skipped_missing_position", summary.skippedMissingPosition())
							.metadataNumber("skipped_row_filter", summary.skippedRowFilter())
							.metadataNumber("skipped_unlabeled", summary.skippedUnlabeled())
							.metadataNumber("skipped_class_cap", summary.skippedClassCap());
					DatasetManifestSupport.addRowHashSidecar(builder, rowHashPath);
					addOptionalManifestString(builder, "row_filter", options.rowFilterDsl());
					addOptionalManifestString(builder, "label_filter", options.labelFilterDsl());
					addOptionalManifestString(builder, "fallback_label_filter", options.fallbackLabelFilterDsl());
					addOptionalManifestCap(builder, "max_positives", options.maxPositives());
					addOptionalManifestCap(builder, "max_negatives", options.maxNegatives());
				});
	}

	/**
	 * Adds a string metadata field only when it has a meaningful value.
	 *
	 * @param builder manifest builder
	 * @param name metadata key
	 * @param value metadata value
	 */
	private static void addOptionalManifestString(DatasetManifest.Builder builder, String name, String value) {
		if (value != null && !value.isBlank()) {
			builder.metadata(name, value);
		}
	}

	/**
	 * Adds a class-cap metadata field only when the export was capped.
	 *
	 * @param builder manifest builder
	 * @param name metadata key
	 * @param value class-cap value
	 */
	private static void addOptionalManifestCap(DatasetManifest.Builder builder, String name, long value) {
		if (value != ClassifierDatasetExporter.NO_CLASS_CAP) {
			builder.metadataNumber(name, value);
		}
	}

	/**
	 * Removes a partially written optional sidecar after an export failure.
	 *
	 * @param path sidecar path, or {@code null} when disabled
	 */
	private static void deleteQuietly(Path path) {
		if (path == null) {
			return;
		}
		try {
			Files.deleteIfExists(path);
		} catch (IOException ignored) {
			// best-effort cleanup
		}
	}

	/**
	 * Flushes an optional sidecar writer before its path is hashed into a manifest.
	 *
	 * @param writer sidecar writer, or {@code null} when disabled
	 * @throws IOException when flushing fails
	 */
	private static void flushSidecar(BufferedWriter writer) throws IOException {
		if (writer != null) {
			writer.flush();
		}
	}

	/**
	 * Handles derive classifier output or exit.
	 * @param inputs input paths
	 * @return handles derive classifier output or exit
	 */
	private static Path deriveClassifierOutputOrExit(List<String> inputs) {
		if (inputs.size() == 1) {
			Path input = Paths.get(inputs.get(0));
			if (!Files.isDirectory(input)) {
				return deriveOutputPath(input, ".classifier");
			}
		}
		exitWithError(COMMAND_RECORD_DATASET_CLASSIFIER + ": missing " + OPT_OUTPUT
				+ " when exporting multiple inputs or a directory");
		throw unreachable();
	}

	/**
	 * Parses the filter or exit.
	 * @param command command text
	 * @param option CLI option
	 * @param filterDsl source filter dsl
	 * @param verbose whether verbose output is enabled
	 * @return parsed the filter or exit
	 */
	private static Filter parseFilterOrExit(String command, String option, String filterDsl, boolean verbose) {
		if (filterDsl == null || filterDsl.isEmpty()) {
			return null;
		}
		try {
			return FilterDSL.fromString(filterDsl);
		} catch (RuntimeException ex) {
			exitWithError(command + ": invalid " + option + " expression: " + filterDsl, ex, verbose);
			throw unreachable();
		}
	}

	/**
	 * Marks code paths after {@code exitWithError(...)} as unreachable.
	 *
	 * @param <T> inferred return type
	 * @return never returns normally
	 */
	private static IllegalStateException unreachable() {
		return new IllegalStateException("unreachable");
	}

	/**
	 * Handles {@code record export pgn}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runRecordToPgn(Argv a) {
		Path in = a.pathRequired(OPT_INPUT, OPT_INPUT_SHORT);
		Path out = a.path(OPT_OUTPUT, OPT_OUTPUT_SHORT);
		a.ensureConsumed();

		if (out == null) {
			out = deriveOutputPath(in, ".pgn");
		}
		try {
			ensureParentDir(out);
		} catch (IOException ex) {
			exitWithError("record export pgn: failed to prepare output: " + ex.getMessage(), ex, false);
		}
		RecordPgnExporter.export(in, out, RecordCommands::recordPgnProgress);
	}

	/**
	 * Handles {@code record export puzzle-jsonl}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runRecordToPuzzleJsonl(Argv a) {
		RecordPuzzleJsonlCommand.run(a);
	}

	/**
	 * Handles {@code record export puzzle-elo-jsonl}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runRecordToPuzzleEloJsonl(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		boolean recursive = a.flag(OPT_RECURSIVE);
		boolean rowHashes = a.flag(OPT_ROW_HASHES);
		Long maxPuzzlesOpt = a.lng(OPT_MAX_RECORDS);
		Integer threadsOpt = a.integer(OPT_THREADS);
		Path ratingsCsv = a.path(OPT_RATINGS_CSV);
		Path out = a.path(OPT_OUTPUT, OPT_OUTPUT_SHORT);

		List<String> inputs = new ArrayList<>();
		inputs.addAll(a.strings(OPT_INPUT, OPT_INPUT_SHORT));
		inputs.addAll(a.positionals());
		a.ensureConsumed();

		if (inputs.isEmpty()) {
			exitWithError(COMMAND_RECORD_EXPORT_PUZZLE_ELO_JSONL + ": missing "
					+ OPT_INPUT + INPUTS_OR_POSITIONAL_HINT);
		}
		long maxPuzzles = maxPuzzlesOpt == null ? 0L : maxPuzzlesOpt.longValue();
		if (maxPuzzles < 0L) {
			exitWithError(COMMAND_RECORD_EXPORT_PUZZLE_ELO_JSONL + ": "
					+ OPT_MAX_RECORDS + MUST_BE_NON_NEGATIVE);
		}
		if (ratingsCsv != null && maxPuzzles > 0L) {
			exitWithError(COMMAND_RECORD_EXPORT_PUZZLE_ELO_JSONL + ": "
					+ OPT_MAX_RECORDS + " cannot be combined with " + OPT_RATINGS_CSV);
		}
		int threads = threadsOpt == null ? Runtime.getRuntime().availableProcessors() : threadsOpt.intValue();
		if (threads <= 0) {
			exitWithError(COMMAND_RECORD_EXPORT_PUZZLE_ELO_JSONL + ": " + OPT_THREADS + " must be positive");
		}

		List<Path> inputFiles =
				collectRecordInputsOrExit(COMMAND_RECORD_EXPORT_PUZZLE_ELO_JSONL, inputs, recursive, verbose);
		if (inputFiles.isEmpty()) {
			exitWithError(COMMAND_RECORD_EXPORT_PUZZLE_ELO_JSONL + ": no input files found");
		}
		Path output = out == null ? defaultPuzzleEloOutput(inputFiles) : out;
		validatePuzzleEloOutput(inputFiles, output);
		Path rowHashPath = rowHashes ? DatasetManifestSupport.rowHashPathFor(output) : null;

		Config.reload();
		Filter puzzleVerify = Config.getPuzzleVerify();
		try (BufferedWriter rowHashWriter = rowHashPath == null
				? null
				: DatasetManifestSupport.openRowHashWriter(rowHashPath)) {
			PuzzleEloExporter.Summary summary = ratingsCsv == null
					? PuzzleEloExporter.export(
							inputFiles,
							output,
							new PuzzleEloExporter.Options(puzzleVerify, maxPuzzles, threads),
							DatasetManifestSupport.rowHashSink(rowHashWriter))
					: PuzzleEloExporter.exportFromRatingCsv(inputFiles, output, ratingsCsv, puzzleVerify,
							DatasetManifestSupport.rowHashSink(rowHashWriter));
			flushSidecar(rowHashWriter);
			writePuzzleEloManifest(inputFiles, output, ratingsCsv, puzzleVerify, maxPuzzles, threads,
					summary, rowHashPath);
			System.out.printf(
					COMMAND_RECORD_EXPORT_PUZZLE_ELO_JSONL
							+ ": wrote %d/%d Elo-rated puzzle records (indexed %d puzzles, invalid %d, skipped %d unscorable, %d non-puzzles, truncated %d trees) to %s%n",
					summary.written(),
					summary.seen(),
					summary.indexedPuzzles(),
					summary.invalid(),
					summary.skipped(),
					summary.nonPuzzles(),
					summary.truncatedTrees(),
					output);
		} catch (IOException ex) {
			deleteQuietly(rowHashPath);
			exitWithError(COMMAND_RECORD_EXPORT_PUZZLE_ELO_JSONL + ": failed to export: "
					+ ex.getMessage(), ex, verbose);
		}
	}

	/**
	 * Writes puzzle-Elo manifest metadata after a successful export.
	 *
	 * @param inputFiles input record files
	 * @param output output JSONL file
	 * @param ratingsCsv optional scored ratings CSV
	 * @param puzzleVerify canonical puzzle verification filter
	 * @param maxPuzzles direct-export puzzle cap
	 * @param threads direct-export tree worker count
	 * @param summary export counters
	 * @param rowHashPath optional row-hash sidecar path
	 */
	private static void writePuzzleEloManifest(
			List<Path> inputFiles,
			Path output,
			Path ratingsCsv,
			Filter puzzleVerify,
			long maxPuzzles,
			int threads,
			PuzzleEloExporter.Summary summary,
			Path rowHashPath) {
		List<Path> manifestInputs = new ArrayList<>(inputFiles);
		if (ratingsCsv != null) {
			manifestInputs.add(ratingsCsv);
		}
		DatasetManifestSupport.write(
				"record.export.puzzle-elo-jsonl",
				manifestInputs,
				List.of(output),
				null,
				Path.of(output + ".manifest.json"),
				builder -> {
					builder.metadata("label_policy", PuzzleEloExporter.modelId())
							.metadata("rating_scope", "corpus-internal")
							.metadataBoolean("calibrated_to_humans", false)
							.metadata("rating_source", ratingsCsv == null ? "tree-search" : "ratings-csv")
							.metadataNumber("records_seen", summary.seen())
							.metadataNumber("indexed_puzzles", summary.indexedPuzzles())
							.metadataNumber("rows_written", summary.written())
							.metadataNumber("non_puzzles", summary.nonPuzzles())
							.metadataNumber("skipped", summary.skipped())
							.metadataNumber("invalid", summary.invalid())
							.metadataNumber("truncated_trees", summary.truncatedTrees());
					DatasetManifestSupport.addRowHashSidecar(builder, rowHashPath);
					if (puzzleVerify != null) {
						builder.metadata("puzzle_filter", FilterDSL.toString(puzzleVerify));
					}
					if (ratingsCsv == null) {
						builder.metadataNumber("threads", threads)
								.metadataNumber("tree_solver_depth", PuzzleEloExporter.maxTreeSolverDepth())
								.metadataNumber("tree_nodes_per_root", PuzzleEloExporter.maxTreeNodesPerRoot());
						if (maxPuzzles > 0L) {
							builder.metadataNumber("max_puzzles", maxPuzzles);
						}
					}
				});
	}

	/**
	 * Builds a default output for the Elo-rated puzzle exporter.
	 * @param inputFiles source input files
	 * @return built a default output for the Elo-rated puzzle exporter
	 */
	private static Path defaultPuzzleEloOutput(List<Path> inputFiles) {
		if (inputFiles.size() == 1) {
			return defaultOutputPath(inputFiles.get(0), null, EXT_PUZZLE_ELO_JSONL);
		}
		return dumpPath("puzzles" + EXT_PUZZLE_ELO_JSONL);
	}

	/**
	 * Returns the explicit output path or a dump-local path with the requested suffix.
	 *
	 * @param input source input file
	 * @param output explicit output path
	 * @param suffix default output suffix
	 * @return resolved output path
	 */
	private static Path defaultOutputPath(Path input, Path output, String suffix) {
		if (output != null) {
			return output;
		}
		return deriveOutputPath(input, suffix);
	}

	/**
	 * Prevents accidental in-place exports.
	 * @param inputFiles source input files
	 * @param output output text
	 */
	private static void validatePuzzleEloOutput(List<Path> inputFiles, Path output) {
		Path outputAbs = output.toAbsolutePath().normalize();
		for (Path input : inputFiles) {
			if (outputAbs.equals(input.toAbsolutePath().normalize())) {
				exitWithError(COMMAND_RECORD_EXPORT_PUZZLE_ELO_JSONL
						+ ": output cannot overwrite input file " + input);
			}
		}
	}

	/**
	 * Validates puzzle-jsonl flags before export starts.
	 *
	 * @param puzzles only export puzzles
	 * @param nonpuzzles only export non-puzzles
	 * @param weights LC0 weights path
	 */

	/**
	 * Handles {@code record export training-jsonl}.
	 *
	 * <p>
	 * The exporter writes one position per JSONL line and labels rows by record
	 * relationships: puzzle-DSL matches are class 2, records with the same parent
	 * as a puzzle are class 1, and the remaining records are class 0.
	 * </p>
	 *
	 * @param a argument parser for the subcommand
	 */
	/**
	 * Handles {@code record export training-jsonl}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runRecordToTrainingJsonl(Argv a) {
		RecordTrainingJsonlCommand.run(a);
	}

	/**
	 * Parses and validates a training-jsonl export request.
	 *
	 * @param a command arguments
	 * @return normalized export request
	 */

	/**
	 * Handles {@code puzzle pgn}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runPuzzlesToPgn(Argv a) {
		Path in = a.pathRequired(OPT_INPUT, OPT_INPUT_SHORT);
		Path out = a.path(OPT_OUTPUT, OPT_OUTPUT_SHORT);
		a.ensureConsumed();

		if (out == null) {
			out = deriveOutputPath(in, ".pgn");
		}
		try {
			ensureParentDir(out);
		} catch (IOException ex) {
			exitWithError("puzzle pgn: failed to prepare output: " + ex.getMessage(), ex, false);
		}
		Config.reload();
		Filter verify = Config.getPuzzleVerify();
		Converter.puzzlesToPgn(in, out, objJson -> isPuzzleRecordJson(objJson, null, verify));
	}

	/**
	 * Handles {@code records}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runRecords(Argv a) {
		RecordsRequest request = parseRecordsRequest(a);
		RecordsFilters filters = buildRecordsFilters(request);

		List<Path> inputFiles = collectRecordInputsOrExit(request.inputs, request.recursive, request.verbose);
		if (inputFiles.isEmpty()) {
			exitWithError("records: no input files found");
		}
		validateOutputPathForInputs(inputFiles, request.output, request.maxRecords);

		RecordBatchWriter writer = new RecordBatchWriter(request.output, request.maxRecords);
		RecordStats stats = new RecordStats();
		Bar filesBar = progressBar(inputFiles.size(), "records files");
		try {
			processRecordInputs(inputFiles, writer, stats, request, filters, filesBar);
			closeRecordWriterOrExit(writer, request.verbose);
		} finally {
			finishProgress(filesBar);
		}

		System.out.printf(
				"records: wrote %d/%d records (skipped %d invalid) to %s%n",
				stats.matched,
				stats.seen,
				stats.invalid,
				writer.describeOutputs());
	}

	/**
	 * Parses the records request.
	 * @param a first value
	 * @return parsed the records request
	 */
	private static RecordsRequest parseRecordsRequest(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		String filterDsl = a.string(OPT_FILTER, OPT_FILTER_SHORT);
		Integer maxRecordsOpt = a.integer(OPT_MAX_RECORDS);
		boolean puzzles = a.flag(OPT_PUZZLES);
		boolean nonpuzzles = a.flag(OPT_NONPUZZLES);
		boolean recursive = a.flag(OPT_RECURSIVE);
		Path output = a.path(OPT_OUTPUT, OPT_OUTPUT_SHORT);

		List<String> inputs = new ArrayList<>();
		inputs.addAll(a.strings(OPT_INPUT, OPT_INPUT_SHORT));
		inputs.addAll(a.positionals());
		a.ensureConsumed();

		if (inputs.isEmpty()) {
			exitWithError("records: missing " + OPT_INPUT + INPUTS_OR_POSITIONAL_HINT);
		}
		if (output == null) {
			exitWithError("records: missing " + OPT_OUTPUT + " for output");
		}
		if (puzzles && nonpuzzles) {
			exitWithError("records: choose either " + OPT_PUZZLES + " or " + OPT_NONPUZZLES + ", not both");
		}

		int maxRecords = (maxRecordsOpt == null) ? 0 : maxRecordsOpt;
		if (maxRecords < 0) {
			exitWithError("records: " + OPT_MAX_RECORDS + MUST_BE_NON_NEGATIVE);
		}
		if (maxRecords <= 0 && Files.isDirectory(output)) {
			exitWithError("records: " + OPT_OUTPUT + " must be a file when not splitting");
		}

		RecordsRequestOptions options =
				new RecordsRequestOptions(verbose, filterDsl, maxRecords, puzzles, nonpuzzles, recursive);
		return new RecordsRequest(options, output, inputs);
	}

	/**
	 * Handles build records filters.
	 * @param request command request
	 * @return handles build records filters
	 */
	private static RecordsFilters buildRecordsFilters(RecordsRequest request) {
		Filter dslFilter = null;
		if (request.filterDsl != null && !request.filterDsl.isEmpty()) {
			try {
				dslFilter = FilterDSL.fromString(request.filterDsl);
			} catch (RuntimeException ex) {
				exitWithError("records: invalid filter expression: " + request.filterDsl, ex, request.verbose);
			}
		}

		Filter puzzleVerify = null;
		if (request.puzzles || request.nonpuzzles) {
			Config.reload();
			puzzleVerify = Config.getPuzzleVerify();
		}
		return new RecordsFilters(dslFilter, puzzleVerify);
	}

	/**
	 * Handles collect record inputs or exit.
	 * @param inputs input paths
	 * @param recursive whether input traversal is recursive
	 * @param verbose whether verbose output is enabled
	 * @return handles collect record inputs or exit
	 */
	private static List<Path> collectRecordInputsOrExit(List<String> inputs, boolean recursive, boolean verbose) {
		return collectRecordInputsOrExit("records", inputs, recursive, verbose);
	}

	/**
	 * Handles collect record inputs or exit.
	 * @param command command text
	 * @param inputs input paths
	 * @param recursive whether input traversal is recursive
	 * @param verbose whether verbose output is enabled
	 * @return handles collect record inputs or exit
	 */
	private static List<Path> collectRecordInputsOrExit(
			String command,
			List<String> inputs,
			boolean recursive,
			boolean verbose) {
		try {
			return collectRecordInputs(inputs, recursive);
		} catch (IOException ex) {
			exitWithError(command + ": failed to read inputs: " + ex.getMessage(), ex, verbose);
			return List.of();
		}
	}

	/**
	 * Handles validate output path for inputs.
	 * @param inputFiles source input files
	 * @param output output path
	 * @param maxRecords maximum records
	 */
	private static void validateOutputPathForInputs(List<Path> inputFiles, Path output, int maxRecords) {
		if (maxRecords > 0) {
			return;
		}
		Path outputAbs = output.toAbsolutePath().normalize();
		for (Path p : inputFiles) {
			if (outputAbs.equals(p.toAbsolutePath().normalize())) {
				exitWithError("records: output cannot overwrite input file " + p);
			}
		}
	}

	/**
	 * Handles process record inputs.
	 * @param inputFiles source input files
	 * @param writer output writer
	 * @param stats running statistics
	 * @param request command request
	 * @param filters record filters
	 * @param filesBar source files bar
	 */
	private static void processRecordInputs(
			List<Path> inputFiles,
			RecordBatchWriter writer,
			RecordStats stats,
			RecordsRequest request,
			RecordsFilters filters,
			Bar filesBar) {
		for (Path input : inputFiles) {
			try {
				streamRecordJson(input, objJson -> handleRecordJson(objJson, writer, stats, request, filters));
				updateRecordsProgress(filesBar, input, stats);
			} catch (IOException ex) {
				exitWithError("records: failed to read input: " + input + " (" + ex.getMessage() + ")", ex,
						request.verbose);
			} catch (RuntimeException ex) {
				Throwable cause = ex.getCause();
				if (cause instanceof IOException io) {
					exitWithError("records: failed to write output: " + io.getMessage(), io, request.verbose);
				}
				throw ex;
			}
		}
	}

	/**
	 * Handles handle record json.
	 * @param objJson JSON payload for obj
	 * @param writer output writer
	 * @param stats running statistics
	 * @param request command request
	 * @param filters record filters
	 */
	private static void handleRecordJson(
			String objJson,
			RecordBatchWriter writer,
			RecordStats stats,
			RecordsRequest request,
			RecordsFilters filters) {
		Record rec;
		try {
			rec = Record.fromJson(objJson);
		} catch (Exception ex) {
			rec = null;
		}
		if (rec == null) {
			stats.invalid++;
			return;
		}
		stats.seen++;

		boolean keep = true;
		if (request.puzzles || request.nonpuzzles) {
			boolean isPuzzle = isPuzzleRecordJson(objJson, rec, filters.puzzleVerify);
			keep = request.puzzles ? isPuzzle : !isPuzzle;
		}
		if (keep && filters.dslFilter != null) {
			keep = filters.dslFilter.apply(rec.getAnalysis());
		}
		if (!keep) {
			return;
		}
		try {
			writer.append(objJson);
			stats.matched++;
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/**
	 * Handles close record writer or exit.
	 * @param writer output writer
	 * @param verbose whether verbose output is enabled
	 */
	private static void closeRecordWriterOrExit(RecordBatchWriter writer, boolean verbose) {
		try {
			writer.close();
		} catch (IOException ex) {
			exitWithError("records: failed to finalize output: " + ex.getMessage(), ex, verbose);
		}
	}

	/**
	 * Provides records request behavior.
	 */
	private static final class RecordsRequest {
		 /**
		 * Stores the verbose.
		 */
		 private final boolean verbose;
		 /**
		 * Stores the filter dsl.
		 */
		 private final String filterDsl;
		 /**
		 * Stores the max records.
		 */
		 private final int maxRecords;
		 /**
		 * Stores the puzzles.
		 */
		 private final boolean puzzles;
		 /**
		 * Stores the nonpuzzles.
		 */
		 private final boolean nonpuzzles;
		 /**
		 * Stores the recursive.
		 */
		 private final boolean recursive;
		 /**
		 * Stores the output.
		 */
		 private final Path output;
		 /**
		 * Stores the inputs.
		 */
		 private final List<String> inputs;

		 /**
		 * Creates a new records request instance.
		 * @param options parsed options
		 * @param output output path
		 * @param inputs input paths
		 */
		 private RecordsRequest(
				RecordsRequestOptions options,
				Path output,
				List<String> inputs) {
			this.verbose = options.verbose;
			this.filterDsl = options.filterDsl;
			this.maxRecords = options.maxRecords;
			this.puzzles = options.puzzles;
			this.nonpuzzles = options.nonpuzzles;
			this.recursive = options.recursive;
			this.output = output;
			this.inputs = inputs;
		}
	}

	/**
	 * Provides records request options behavior.
	 */
	private static final class RecordsRequestOptions {
		 /**
		 * Stores the verbose.
		 */
		 private final boolean verbose;
		 /**
		 * Stores the filter dsl.
		 */
		 private final String filterDsl;
		 /**
		 * Stores the max records.
		 */
		 private final int maxRecords;
		 /**
		 * Stores the puzzles.
		 */
		 private final boolean puzzles;
		 /**
		 * Stores the nonpuzzles.
		 */
		 private final boolean nonpuzzles;
		 /**
		 * Stores the recursive.
		 */
		 private final boolean recursive;

		 /**
		 * Creates a new records request options instance.
		 * @param verbose whether verbose output is enabled
		 * @param filterDsl source filter dsl
		 * @param maxRecords maximum records
		 * @param puzzles puzzle records
		 * @param nonpuzzles non-puzzle count
		 * @param recursive whether input traversal is recursive
		 */
		 private RecordsRequestOptions(
				boolean verbose,
				String filterDsl,
				int maxRecords,
				boolean puzzles,
				boolean nonpuzzles,
				boolean recursive) {
			this.verbose = verbose;
			this.filterDsl = filterDsl;
			this.maxRecords = maxRecords;
			this.puzzles = puzzles;
			this.nonpuzzles = nonpuzzles;
			this.recursive = recursive;
		}
	}

	/**
	 * Provides records filters behavior.
	 */
	private static final class RecordsFilters {
		 /**
		 * Stores the dsl filter.
		 */
		 private final Filter dslFilter;
		 /**
		 * Stores the puzzle verify.
		 */
		 private final Filter puzzleVerify;

		 /**
		 * Creates a new records filters instance.
		 * @param dslFilter source dsl filter
		 * @param puzzleVerify puzzle verification filter
		 */
		 private RecordsFilters(Filter dslFilter, Filter puzzleVerify) {
			this.dslFilter = dslFilter;
			this.puzzleVerify = puzzleVerify;
		}
	}

	/**
	 * Provides record stats behavior.
	 */
	private static final class RecordStats {
		 /**
		 * Stores the seen.
		 */
		 private long seen;
		 /**
		 * Stores the matched.
		 */
		 private long matched;
		 /**
		 * Stores the invalid.
		 */
		 private long invalid;
	}

	/**
	 * Determines whether a JSON record represents a puzzle.
	 *
	 * @param objJson      raw JSON object string
	 * @param rec          parsed record instance (optional)
	 * @param puzzleVerify verification filter to apply when {@code kind} is absent
	 * @return true when the record is classified as a puzzle
	 */
	static boolean isPuzzleRecordJson(String objJson, Record rec, Filter puzzleVerify) {
		if (objJson == null || objJson.isEmpty()) {
			return false;
		}
		String kind = utility.Json.parseStringField(objJson, "kind");
		if (kind != null && !kind.isEmpty()) {
			return "puzzle".equalsIgnoreCase(kind);
		}
		if (puzzleVerify == null) {
			return false;
		}
		if (rec == null) {
			try {
				rec = Record.fromJson(objJson);
			} catch (Exception ex) {
				rec = null;
			}
		}
		if (rec == null) {
			return false;
		}
		return puzzleVerify.apply(rec.getAnalysis());
	}


	/**
	 * Handles derive training jsonl output or exit.
	 * @param inputs input paths
	 * @return handles derive training jsonl output or exit
	 */

	/**
	 * Handles update classifier progress.
	 * @param bar progress bar
	 * @param progress progress callback
	 */
	private static void updateClassifierProgress(
			Bar bar,
			ClassifierDatasetExporter.FileProgress progress) {
		if (bar == null || progress == null) {
			return;
		}
		ClassifierDatasetExporter.Summary summary = progress.summary();
		bar.step(String.format(Locale.ROOT,
				"rows=%d bad=%d",
				summary.rowsWritten(),
				summary.skippedInvalid()));
	}

	/**
	 * Handles update records progress.
	 * @param bar progress bar
	 * @param input input path or text
	 * @param stats running statistics
	 */
	private static void updateRecordsProgress(Bar bar, Path input, RecordStats stats) {
		if (bar == null) {
			return;
		}
		bar.step(String.format(Locale.ROOT,
				"last=%s matched=%d seen=%d invalid=%d",
				fileName(input),
				stats.matched,
				stats.seen,
				stats.invalid));
	}

	/**
	 * Handles file name.
	 * @param path file-system path
	 * @return handles file name
	 */
	private static String fileName(Path path) {
		if (path == null || path.getFileName() == null) {
			return "";
		}
		return path.getFileName().toString();
	}

	/**
	 * Handles progress bar.
	 * @param totalRecords source total records
	 * @param label display label
	 * @return handles progress bar
	 */
	private static Bar progressBar(long totalRecords, String label) {
		if (totalRecords <= 0) {
			return null;
		}
		return new Bar(totalRecords, label);
	}

	/**
	 * Creates a PGN export progress sink backed by the CLI progress bar.
	 *
	 * @param total total work units
	 * @param label phase label
	 * @return progress sink or {@code null}
	 */
	private static RecordPgnExporter.ProgressSink recordPgnProgress(long total, String label) {
		Bar bar = progressBar(total, label);
		if (bar == null) {
			return null;
		}
		return new RecordPgnExporter.ProgressSink() {
			/**
			 * {@inheritDoc}
			 */
			@Override
			public void accept(long value) {
				bar.set(value);
			}

			/**
			 * {@inheritDoc}
			 */
			@Override
			public void finish() {
				finishProgress(bar);
			}
		};
	}


	/**
	 * Collects record files from input paths and directories.
	 *
	 * @param inputs    list of input file or directory strings
	 * @param recursive whether to recurse into subdirectories
	 * @return sorted list of record file paths
	 * @throws IOException when inputs cannot be resolved
	 */
	private static List<Path> collectRecordInputs(List<String> inputs, boolean recursive) throws IOException {
		List<Path> files = new ArrayList<>();
		for (String entry : inputs) {
			if (entry != null && !entry.isEmpty()) {
				Path p = Paths.get(entry);
				if (!Files.exists(p)) {
					throw new IOException("input does not exist: " + p);
				}
				if (Files.isDirectory(p)) {
					files.addAll(listRecordFiles(p, recursive));
				} else {
					files.add(p);
				}
			}
		}
		files.sort(Comparator.comparing(Path::toString));
		return files;
	}

	/**
	 * Lists record files in a directory, optionally recursing into subdirectories.
	 *
	 * @param dir       directory to scan
	 * @param recursive whether to recurse into subdirectories
	 * @return list of record file paths
	 * @throws IOException when directory traversal fails
	 */
	private static List<Path> listRecordFiles(Path dir, boolean recursive) throws IOException {
		List<Path> files = new ArrayList<>();
		if (recursive) {
			try (java.util.stream.Stream<Path> stream = Files.walk(dir)) {
				stream.filter(Files::isRegularFile)
						.filter(RecordCommands::hasRecordExtension)
						.forEach(files::add);
			}
			return files;
		}
		try (java.util.stream.Stream<Path> stream = Files.list(dir)) {
			stream.filter(Files::isRegularFile)
					.filter(RecordCommands::hasRecordExtension)
					.forEach(files::add);
		}
		return files;
	}

	/**
	 * Checks whether a path has a record file extension.
	 *
	 * @param p path to inspect
	 * @return true if the extension is supported
	 */
	private static boolean hasRecordExtension(Path p) {
		String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
		return name.endsWith(EXT_JSON) || name.endsWith(EXT_JSONL) || name.endsWith(EXT_RECORD);
	}

	/**
	 * Buffered writer that can split JSONL outputs into multiple parts.
	 */
	private static final class RecordBatchWriter {

		/**
		 * Output path provided by the caller.
		 */
		private final Path output;

		/**
		 * Maximum records per part; zero disables splitting.
		 */
		private final int maxRecords;

		/**
		 * Output directory used when splitting into parts.
		 */
		private final Path outputDir;

		/**
		 * Output filename stem used when splitting into parts.
		 */
		private final String outputStem;

		/**
		 * Output filename extension used when splitting into parts.
		 */
		private final String outputExt;

		/**
		 * Buffered JSON payloads awaiting flush.
		 */
		private final List<String> buffer = new ArrayList<>(RECORD_BATCH_SIZE);

		/**
		 * Current part index (1-based).
		 */
		private int partIndex = 1;

		/**
		 * Record count in the current part.
		 */
		private int countInPart = 0;

		/**
		 * Total number of records written across all parts.
		 */
		private long totalWritten = 0;

		/**
		 * Number of output parts written.
		 */
		private int partsWritten = 0;

		/**
		 * Creates a new batch writer for the requested output.
		 *
		 * @param output     output file or directory
		 * @param maxRecords maximum records per part (0 to disable splitting)
		 */
		private RecordBatchWriter(Path output, int maxRecords) {
			this.output = Objects.requireNonNull(output, "output");
			this.maxRecords = Math.max(0, maxRecords);
			if (this.maxRecords > 0) {
				String name = output.getFileName().toString();
				boolean hasExt = name.endsWith(EXT_JSON) || name.endsWith(EXT_JSONL);
				if (hasExt) {
					this.outputDir = (output.getParent() == null) ? Paths.get(".") : output.getParent();
					int dot = name.lastIndexOf('.');
					this.outputStem = name.substring(0, dot);
					this.outputExt = name.substring(dot);
				} else {
					this.outputDir = output;
					this.outputStem = "records";
					this.outputExt = EXT_JSON;
				}
			} else {
				this.outputDir = null;
				this.outputStem = null;
				this.outputExt = null;
			}
		}

		/**
		 * Appends a JSON record to the buffer and flushes when needed.
		 *
		 * @param json JSON record string
		 * @throws IOException when flushing fails
		 */
		private void append(String json) throws IOException {
			buffer.add(json);
			if (buffer.size() >= RECORD_BATCH_SIZE) {
				flushBuffer();
			}
		}

		/**
		 * Flushes remaining buffered data and writes an empty output if needed.
		 *
		 * @throws IOException when output writing fails
		 */
		private void close() throws IOException {
			flushBuffer();
			if (totalWritten == 0) {
				writeEmptyOutput();
			}
		}

		/**
		 * Flushes the in-memory buffer to disk.
		 *
		 * @throws IOException when writing fails
		 */
		private void flushBuffer() throws IOException {
			if (buffer.isEmpty()) {
				return;
			}
			writeBatch(buffer);
			buffer.clear();
		}

		/**
		 * Writes a batch of JSON records across one or more parts.
		 *
		 * @param batch JSON record batch
		 * @throws IOException when writing fails
		 */
		private void writeBatch(List<String> batch) throws IOException {
			int idx = 0;
			while (idx < batch.size()) {
				if (maxRecords > 0 && countInPart >= maxRecords) {
					rotate();
				}
				Path target = currentOutputPath();
				ensureParentDir(target);
				int take = batch.size() - idx;
				if (maxRecords > 0) {
					int remaining = Math.max(0, maxRecords - countInPart);
					take = Math.min(remaining, take);
				}
				if (take <= 0) {
					rotate();
					continue;
				}
				Writer.appendJsonObjects(target, batch.subList(idx, idx + take));
				markPartWritten();
				countInPart += take;
				totalWritten += take;
				idx += take;
			}
		}

		/**
		 * Rotates to the next output part.
		 */
		private void rotate() {
			partIndex++;
			countInPart = 0;
		}

		/**
		 * Tracks the highest part index that has been written.
		 */
		private void markPartWritten() {
			if (maxRecords <= 0) {
				partsWritten = 1;
				return;
			}
			partsWritten = Math.max(partsWritten, partIndex);
		}

		/**
		 * Writes an empty JSON array when no records were written.
		 *
		 * @throws IOException when writing fails
		 */
		private void writeEmptyOutput() throws IOException {
			Path target = currentOutputPath();
			ensureParentDir(target);
			Files.writeString(target, "[]");
			markPartWritten();
		}

		/**
		 * Resolves the current output path for the active part.
		 *
		 * @return output path for the current part
		 */
		private Path currentOutputPath() {
			if (maxRecords <= 0) {
				return output;
			}
			return outputDir.resolve(String.format("%s.part-%04d%s", outputStem, partIndex, outputExt));
		}

		/**
		 * Builds a human-readable description of output paths.
		 *
		 * @return output description for logging
		 */
		private String describeOutputs() {
			if (maxRecords <= 0) {
				return output.toString();
			}
			Path first = outputDir.resolve(String.format("%s.part-0001%s", outputStem, outputExt));
			if (partsWritten <= 1) {
				return first.toString();
			}
			Path last = outputDir.resolve(String.format("%s.part-%04d%s", outputStem, partsWritten, outputExt));
			return first + ".." + last;
		}
	}

}
