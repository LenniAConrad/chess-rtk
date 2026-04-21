package application.cli.command;

import static application.cli.Constants.OPT_FILTER;
import static application.cli.Constants.OPT_FILTER_SHORT;
import static application.cli.Constants.OPT_INCLUDE_ENGINE_METADATA;
import static application.cli.Constants.OPT_INPUT;
import static application.cli.Constants.OPT_INPUT_SHORT;
import static application.cli.Constants.OPT_MAX_RECORDS;
import static application.cli.Constants.OPT_NONPUZZLES;
import static application.cli.Constants.OPT_OUTPUT;
import static application.cli.Constants.OPT_OUTPUT_SHORT;
import static application.cli.Constants.OPT_WEIGHTS;
import static application.cli.Constants.OPT_PUZZLES;
import static application.cli.Constants.OPT_RECURSIVE;
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
import static application.cli.PathOps.ensureParentDir;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongConsumer;

import application.Config;
import application.console.Bar;
import chess.core.Move;
import chess.io.Converter;
import chess.io.ClassifierDatasetExporter;
import chess.io.Writer;
import chess.nn.lc0.Encoder;
import chess.nn.lc0.Network;
import chess.nn.lc0.PolicyEncoder;
import chess.struct.Record;
import chess.uci.Chances;
import chess.uci.Evaluation;
import chess.uci.Filter;
import chess.uci.Filter.FilterDSL;
import chess.uci.Output;
import utility.Argv;
import utility.Json;

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
	 * Shared ext puzzle jsonl constant.
	 */
	private static final String EXT_PUZZLE_JSONL = ".puzzle.jsonl";
	/**
	 * Shared ext training jsonl constant.
	 */
	private static final String EXT_TRAINING_JSONL = ".training.jsonl";
	/**
	 * Shared puzzle jsonl format name constant.
	 */
	private static final String PUZZLE_JSONL_FORMAT_NAME = "puzzle-jsonl";
	/**
	 * Shared label known non puzzle constant.
	 */
	private static final String LABEL_KNOWN_NON_PUZZLE = "known_non_puzzle";
	/**
	 * Shared label verified near puzzle constant.
	 */
	private static final String LABEL_VERIFIED_NEAR_PUZZLE = "verified_near_puzzle";
	/**
	 * Shared label verified puzzle constant.
	 */
	private static final String LABEL_VERIFIED_PUZZLE = "verified_puzzle";
	/**
	 * Current command label for plain export diagnostics.
	 */
	private static final String RECORD_EXPORT_PLAIN = "record export plain";
	/**
	 * Current command label for CSV export diagnostics.
	 */
	private static final String RECORD_EXPORT_CSV = "record export csv";

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

		Bar plainBar = fileProgressBar(in, 1, "record export plain");
		try {
			Converter.recordToPlain(exportAll, filter, in, out, byteProgress(plainBar));
		} finally {
			finishProgress(plainBar);
		}
		if (csv || csvOut != null) {
			Bar csvBar = fileProgressBar(in, 2, "record export csv");
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

		Bar bar = fileProgressBar(in, 2, "record export csv");
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
		a.ensureConsumed();

		if (out == null) {
			String stem = in.getFileName().toString();
			int dot = stem.lastIndexOf('.');
			if (dot > 0) {
				stem = stem.substring(0, dot);
			}
			out = in.resolveSibling(stem + ".dataset");
		}

		Bar bar = fileProgressBar(in, 1, "record dataset npy");
		boolean progressFinished = false;
		try {
			chess.io.RecordDatasetExporter.export(in, out, byteProgress(bar));
			finishProgress(bar);
			progressFinished = true;
			System.out.printf("Wrote %s.features.npy and %s.labels.npy%n", out, out);
		} catch (IOException e) {
			finishProgress(bar);
			progressFinished = true;
			System.err.println("Failed to export dataset: " + e.getMessage());
			System.exit(2);
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
		argv.ensureConsumed();

		if (out == null) {
			String stem = in.getFileName().toString();
			int dot = stem.lastIndexOf('.');
			if (dot > 0) {
				stem = stem.substring(0, dot);
			}
			out = in.resolveSibling(stem + ".lc0");
		}

		Bar bar = fileProgressBar(in, 1, "record dataset lc0");
		boolean progressFinished = false;
		try {
			chess.io.RecordLc0Exporter.export(in, out, weights, byteProgress(bar));
			finishProgress(bar);
			progressFinished = true;
			System.out.printf(
					"Wrote %s.lc0.inputs.npy, %s.lc0.policy.npy, %s.lc0.value.npy, %s.lc0.meta.json%n",
					out, out, out, out);
		} catch (IOException e) {
			finishProgress(bar);
			progressFinished = true;
			System.err.println("Failed to export LC0 dataset: " + e.getMessage());
			System.exit(2);
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

		List<String> inputs = new ArrayList<>();
		inputs.addAll(a.strings(OPT_INPUT, OPT_INPUT_SHORT));
		inputs.addAll(a.positionals());
		a.ensureConsumed();

		if (inputs.isEmpty()) {
			exitWithError("record dataset classifier: missing " + OPT_INPUT + " (or positional files/dirs)");
		}

		if (out == null) {
			out = deriveClassifierOutputOrExit(inputs);
		}

		long maxPositives = maxPositivesOpt == null
				? ClassifierDatasetExporter.NO_CLASS_CAP
				: maxPositivesOpt.longValue();
		long maxNegatives = maxNegativesOpt == null
				? ClassifierDatasetExporter.NO_CLASS_CAP
				: maxNegativesOpt.longValue();
		if (maxPositives < 0) {
			exitWithError("record dataset classifier: " + OPT_MAX_POSITIVES + " must be non-negative");
		}
		if (maxNegatives < 0) {
			exitWithError("record dataset classifier: " + OPT_MAX_NEGATIVES + " must be non-negative");
		}

		Filter rowFilter = parseFilterOrExit("record dataset classifier", OPT_FILTER, rowFilterDsl, verbose);
		Filter labelFilter = parseFilterOrExit("record dataset classifier", OPT_LABEL_FILTER, labelFilterDsl, verbose);

		Config.reload();
		Filter fallbackLabelFilter = labelFilter == null ? Config.getPuzzleVerify() : null;
		String fallbackLabelFilterDsl = fallbackLabelFilter == null ? null : FilterDSL.toString(fallbackLabelFilter);

		List<Path> inputFiles = collectRecordInputsOrExit("record dataset classifier", inputs, recursive, verbose);
		if (inputFiles.isEmpty()) {
			exitWithError("record dataset classifier: no input files found");
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
		try {
			ClassifierDatasetExporter.Summary summary =
					ClassifierDatasetExporter.export(inputFiles, out, options,
							filesBar == null ? null : progress -> updateClassifierProgress(filesBar, progress),
							null);
			finishProgress(filesBar);
			progressFinished[0] = true;
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
			exitWithError("record dataset classifier: failed to export dataset: " + ex.getMessage(), ex, verbose);
		} finally {
			if (!progressFinished[0]) {
				finishProgress(filesBar);
			}
		}
	}

	/**
	 * Handles derive classifier output or exit.
	 * @param inputs inputs
	 * @return computed value
	 */
	private static Path deriveClassifierOutputOrExit(List<String> inputs) {
		if (inputs.size() == 1) {
			Path input = Paths.get(inputs.get(0));
			if (!Files.isDirectory(input)) {
				return deriveOutputPath(input, ".classifier");
			}
		}
		exitWithError("record dataset classifier: missing " + OPT_OUTPUT
				+ " when exporting multiple inputs or a directory");
		return null;
	}

	/**
	 * Parses the filter or exit.
	 * @param command command
	 * @param option option
	 * @param filterDsl filter dsl
	 * @param verbose verbose
	 * @return computed value
	 */
	private static Filter parseFilterOrExit(String command, String option, String filterDsl, boolean verbose) {
		if (filterDsl == null || filterDsl.isEmpty()) {
			return null;
		}
		try {
			return FilterDSL.fromString(filterDsl);
		} catch (RuntimeException ex) {
			exitWithError(command + ": invalid " + option + " expression: " + filterDsl, ex, verbose);
			return null;
		}
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

		Converter.recordToPgn(in, out);
	}

	/**
	 * Handles {@code record export puzzle-jsonl}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runRecordToPuzzleJsonl(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		boolean puzzles = a.flag(OPT_PUZZLES);
		boolean nonpuzzles = a.flag(OPT_NONPUZZLES);
		String filterDsl = a.string(OPT_FILTER, OPT_FILTER_SHORT);
		Path in = a.pathRequired(OPT_INPUT, OPT_INPUT_SHORT);
		Path weights = a.path(OPT_WEIGHTS);
		Path out = a.path(OPT_OUTPUT, OPT_OUTPUT_SHORT);
		a.ensureConsumed();

		if (puzzles && nonpuzzles) {
			exitWithError("record export puzzle-jsonl: cannot combine " + OPT_PUZZLES + " and " + OPT_NONPUZZLES);
		}
		if (weights == null) {
			exitWithError("record export puzzle-jsonl: missing " + OPT_WEIGHTS + " for LC0 policy values");
		}

		final Filter filter = (filterDsl != null && !filterDsl.isEmpty())
				? FilterDSL.fromString(filterDsl)
				: null;

		final Filter puzzleVerify;
		if (puzzles || nonpuzzles) {
			Config.reload();
			puzzleVerify = Config.getPuzzleVerify();
		} else {
			puzzleVerify = null;
		}

		if (out == null) {
			String stem = in.getFileName().toString();
			int dot = stem.lastIndexOf('.');
			if (dot > 0) {
				stem = stem.substring(0, dot);
			}
			out = in.resolveSibling(stem + EXT_PUZZLE_JSONL);
		}

		final Network network;
		final int[] policyMapInverse;
		try {
			network = Network.load(weights);
			policyMapInverse = invertPolicyMap(Network.loadPolicyMap(weights));
		} catch (IOException ex) {
			exitWithError("record export puzzle-jsonl: failed to load LC0 weights: " + ex.getMessage(), ex, verbose);
			return;
		}

		try {
			ensureParentDir(out);
		} catch (IOException ex) {
			exitWithError("record export puzzle-jsonl: failed to prepare output: " + ex.getMessage(), ex, verbose);
		}
		final Bar bar = fileProgressBar(in, 1, "record export puzzle-jsonl");
		final long[] seen = { 0 };
		final long[] written = { 0 };
		final long[] skipped = { 0 };
		final long[] invalid = { 0 };

		try (Network net = network; BufferedWriter writer = Files.newBufferedWriter(out)) {
			streamRecordJson(in, objJson -> {
				seen[0]++;
				Record rec;
				try {
					rec = Record.fromJson(objJson);
				} catch (Exception ex) {
					invalid[0]++;
					if (verbose) {
						System.err.println("record export puzzle-jsonl: skipped invalid record: " + ex.getMessage());
					}
					return;
				}
				if (rec == null) {
					invalid[0]++;
					return;
				}
				if (filter != null && !filter.apply(rec.getAnalysis())) {
					skipped[0]++;
					return;
				}
				if (puzzles || nonpuzzles) {
					boolean isPuzzle = isPuzzleRecordJson(objJson, rec, puzzleVerify);
					if (puzzles && !isPuzzle) {
						skipped[0]++;
						return;
					}
					if (nonpuzzles && isPuzzle) {
						skipped[0]++;
						return;
					}
				}
				String line = toPuzzleJsonlLine(rec, net, policyMapInverse);
				if (line == null) {
					skipped[0]++;
					return;
				}
				try {
					writer.write(line);
					writer.newLine();
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
				written[0]++;
				if (bar != null && (seen[0] % 1000L == 0L)) {
					bar.setPostfix(String.format(Locale.ROOT,
							"written=%d skipped=%d invalid=%d", written[0], skipped[0], invalid[0]));
				}
			}, byteProgress(bar));
		} catch (IOException | UncheckedIOException ex) {
			exitWithError("record export puzzle-jsonl: failed to write output: " + ex.getMessage(), ex, verbose);
		}
		if (bar != null) {
			bar.setPostfix(String.format(Locale.ROOT,
					"written=%d skipped=%d invalid=%d", written[0], skipped[0], invalid[0]));
			bar.finish();
		}

		System.out.printf(
				"record export puzzle-jsonl: wrote %d/%d %s records (skipped %d invalid, %d filtered) to %s%n",
				written[0],
				seen[0],
				PUZZLE_JSONL_FORMAT_NAME,
				invalid[0],
				skipped[0],
				out);
	}

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
	public static void runRecordToTrainingJsonl(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		boolean recursive = a.flag(OPT_RECURSIVE);
		boolean includeEngineMetadata = a.flag(OPT_INCLUDE_ENGINE_METADATA);
		String puzzleFilterDsl = a.string(OPT_FILTER, OPT_FILTER_SHORT);
		Long maxRecordsOpt = a.lng(OPT_MAX_RECORDS);
		Path out = a.path(OPT_OUTPUT, OPT_OUTPUT_SHORT);

		List<String> inputs = new ArrayList<>();
		inputs.addAll(a.strings(OPT_INPUT, OPT_INPUT_SHORT));
		inputs.addAll(a.positionals());
		a.ensureConsumed();

		if (inputs.isEmpty()) {
			exitWithError("record export training-jsonl: missing " + OPT_INPUT + " (or positional files/dirs)");
		}
		if (out == null) {
			out = deriveTrainingJsonlOutputOrExit(inputs);
		}
		long maxRecords = maxRecordsOpt == null ? 0L : maxRecordsOpt.longValue();
		if (maxRecords < 0L) {
			exitWithError("record export training-jsonl: " + OPT_MAX_RECORDS + " must be non-negative");
		}

		Filter puzzleFilter = resolveTrainingPuzzleFilter(puzzleFilterDsl, verbose);
		List<Path> inputFiles = collectRecordInputsOrExit("record export training-jsonl", inputs, recursive, verbose);
		if (inputFiles.isEmpty()) {
			exitWithError("record export training-jsonl: no input files found");
		}
		validateTrainingOutputPath(inputFiles, out);

		Bar bar = progressBar(inputFiles.size() * 2L, "training-jsonl files");
		TrainingExportStats stats = new TrainingExportStats();
		Set<String> puzzleParents;
		try {
			puzzleParents = collectPuzzleParentFens(inputFiles, puzzleFilter, verbose, bar);
		} catch (IOException | RuntimeException ex) {
			finishProgress(bar);
			exitWithError("record export training-jsonl: failed while scanning puzzle parents: " + ex.getMessage(), ex,
					verbose);
			return;
		}

		stats.resetForWritePass();
		try {
			ensureParentDir(out);
		} catch (IOException ex) {
			finishProgress(bar);
			exitWithError("record export training-jsonl: failed to prepare output: " + ex.getMessage(), ex, verbose);
		}

		try (BufferedWriter writer = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
			writeTrainingJsonl(inputFiles, writer, puzzleFilter, puzzleParents, includeEngineMetadata,
					maxRecords, stats, verbose, bar);
		} catch (StopTrainingExport ignored) {
			// max-records reached cleanly
		} catch (IOException | UncheckedIOException ex) {
			finishProgress(bar);
			exitWithError("record export training-jsonl: failed to write output: " + ex.getMessage(), ex, verbose);
		}
		finishProgress(bar);

		System.out.printf(Locale.ROOT,
				"record export training-jsonl: wrote %d/%d records to %s (puzzles=%d, similar=%d, random=%d, invalid=%d, puzzle-parents=%d)%n",
				stats.written,
				stats.seen,
				out,
				stats.puzzles,
				stats.similar,
				stats.random,
				stats.invalid,
				puzzleParents.size());
	}

	/**
	 * Handles {@code puzzle pgn}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runPuzzlesToPgn(Argv a) {
		Path in = a.pathRequired(OPT_INPUT, OPT_INPUT_SHORT);
		Path out = a.path(OPT_OUTPUT, OPT_OUTPUT_SHORT);
		a.ensureConsumed();

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
			System.err.println("records: no input files found");
			System.exit(2);
			return;
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
	 * @param a a
	 * @return computed value
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
			exitWithError("records: missing " + OPT_INPUT + " (or positional files/dirs)");
		}
		if (output == null) {
			exitWithError("records: missing " + OPT_OUTPUT + " for output");
		}
		if (puzzles && nonpuzzles) {
			exitWithError("records: choose either " + OPT_PUZZLES + " or " + OPT_NONPUZZLES + ", not both");
		}

		int maxRecords = (maxRecordsOpt == null) ? 0 : maxRecordsOpt;
		if (maxRecords < 0) {
			exitWithError("records: " + OPT_MAX_RECORDS + " must be non-negative");
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
	 * @param request request
	 * @return computed value
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
	 * @param inputs inputs
	 * @param recursive recursive
	 * @param verbose verbose
	 * @return computed value
	 */
	private static List<Path> collectRecordInputsOrExit(List<String> inputs, boolean recursive, boolean verbose) {
		return collectRecordInputsOrExit("records", inputs, recursive, verbose);
	}

	/**
	 * Handles collect record inputs or exit.
	 * @param command command
	 * @param inputs inputs
	 * @param recursive recursive
	 * @param verbose verbose
	 * @return computed value
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
	 * @param inputFiles input files
	 * @param output output
	 * @param maxRecords max records
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
	 * @param inputFiles input files
	 * @param writer writer
	 * @param stats stats
	 * @param request request
	 * @param filters filters
	 * @param filesBar files bar
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
				System.err.println("records: failed to read input: " + input + " (" + ex.getMessage() + ")");
				if (request.verbose) {
					ex.printStackTrace(System.err);
				}
				System.exit(2);
				return;
			} catch (RuntimeException ex) {
				Throwable cause = ex.getCause();
				if (cause instanceof IOException io) {
					System.err.println("records: failed to write output: " + io.getMessage());
					if (request.verbose) {
						io.printStackTrace(System.err);
					}
					System.exit(2);
					return;
				}
				throw ex;
			}
		}
	}

	/**
	 * Handles handle record json.
	 * @param objJson obj json
	 * @param writer writer
	 * @param stats stats
	 * @param request request
	 * @param filters filters
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
	 * @param writer writer
	 * @param verbose verbose
	 */
	private static void closeRecordWriterOrExit(RecordBatchWriter writer, boolean verbose) {
		try {
			writer.close();
		} catch (IOException ex) {
			System.err.println("records: failed to finalize output: " + ex.getMessage());
			if (verbose) {
				ex.printStackTrace(System.err);
			}
			System.exit(2);
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
		 * @param options options
		 * @param output output
		 * @param inputs inputs
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
		 * @param verbose verbose
		 * @param filterDsl filter dsl
		 * @param maxRecords max records
		 * @param puzzles puzzles
		 * @param nonpuzzles nonpuzzles
		 * @param recursive recursive
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
		 * @param dslFilter dsl filter
		 * @param puzzleVerify puzzle verify
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
	private static boolean isPuzzleRecordJson(String objJson, Record rec, Filter puzzleVerify) {
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
	 * Converts this value to puzzle jsonl line.
	 * @param rec rec
	 * @param network network
	 * @param policyMapInverse policy map inverse
	 * @return computed value
	 */
	private static String toPuzzleJsonlLine(Record rec, Network network, int[] policyMapInverse) {
		if (rec == null || rec.getPosition() == null || rec.getAnalysis() == null) {
			return null;
		}
		Output best = rec.getAnalysis().getBestOutput();
		short[] moves = best == null ? null : best.getMoves();
		Short criticalMove = toCriticalMove(moves);
		if (criticalMove == null) {
			return null;
		}
		String fen = rec.getPosition().toString();
		int policyIndex = PolicyEncoder.rawPolicyIndex(rec.getPosition(), criticalMove);
		Lc0PolicyEval lc0 = lc0PolicyEval(rec.getPosition(), network, policyIndex, policyMapInverse);
		Chances engineChances = best.getChances();
		String engineWdlJson = toWdlJson(engineChances);
		String engineEval = formatEngineEval(best.getEvaluation());
		long id = rec.getPosition().signature();
		StringBuilder sb = new StringBuilder(128);
		sb.append("{\"id\":")
				.append(id)
				.append(",\"position\":\"")
				.append(Json.esc(fen))
				.append("\",\"critical_move\":\"")
				.append(Move.toString(criticalMove))
				.append('"')
				.append(",\"lc0_policy_index\":")
				.append(policyIndex >= 0 ? policyIndex : "null")
				.append(",\"lc0_policy_pct\":")
				.append(lc0 != null ? formatPercent(lc0.policyPercent) : "null")
				.append(",\"lc0_wdl\":")
				.append(lc0 != null ? lc0.wdlJson : "null")
				.append(",\"engine_eval\":")
				.append(engineEval != null ? ("\"" + engineEval + "\"") : "null")
				.append(",\"engine_wdl\":")
				.append(engineWdlJson)
				.append('}');
		return sb.toString();
	}

	/**
	 * Handles derive training jsonl output or exit.
	 * @param inputs inputs
	 * @return computed value
	 */
	private static Path deriveTrainingJsonlOutputOrExit(List<String> inputs) {
		if (inputs.size() == 1) {
			Path input = Paths.get(inputs.get(0));
			if (!Files.isDirectory(input)) {
				return deriveOutputPath(input, EXT_TRAINING_JSONL);
			}
		}
		exitWithError("record export training-jsonl: missing " + OPT_OUTPUT
				+ " when exporting multiple inputs or a directory");
		return null;
	}

	/**
	 * Handles resolve training puzzle filter.
	 * @param puzzleFilterDsl puzzle filter dsl
	 * @param verbose verbose
	 * @return computed value
	 */
	private static Filter resolveTrainingPuzzleFilter(String puzzleFilterDsl, boolean verbose) {
		if (puzzleFilterDsl != null && !puzzleFilterDsl.isEmpty()) {
			return parseFilterOrExit("record export training-jsonl", OPT_FILTER, puzzleFilterDsl, verbose);
		}
		Config.reload();
		Filter puzzleFilter = Config.getPuzzleVerify();
		if (puzzleFilter == null) {
			exitWithError("record export training-jsonl: missing puzzle DSL; pass " + OPT_FILTER
					+ " or configure puzzle verification filters");
		}
		return puzzleFilter;
	}

	/**
	 * Handles validate training output path.
	 * @param inputFiles input files
	 * @param output output
	 */
	private static void validateTrainingOutputPath(List<Path> inputFiles, Path output) {
		if (Files.isDirectory(output)) {
			exitWithError("record export training-jsonl: " + OPT_OUTPUT + " must be a JSONL file path");
		}
		Path outputAbs = output.toAbsolutePath().normalize();
		for (Path input : inputFiles) {
			if (outputAbs.equals(input.toAbsolutePath().normalize())) {
				exitWithError("record export training-jsonl: output cannot overwrite input file " + input);
			}
		}
	}

	/**
	 * Handles collect puzzle parent fens.
	 * @param inputFiles input files
	 * @param puzzleFilter puzzle filter
	 * @param verbose verbose
	 * @param bar bar
	 * @return computed value
	 * @throws IOException if the operation fails
	 */
	private static Set<String> collectPuzzleParentFens(
			List<Path> inputFiles,
			Filter puzzleFilter,
			boolean verbose,
			Bar bar) throws IOException {
		Set<String> puzzleParents = new HashSet<>();
		for (Path input : inputFiles) {
			streamRecordJson(input, objJson -> {
				Record rec = parseTrainingRecord(objJson, verbose, "record export training-jsonl");
				if (rec == null || rec.getParent() == null) {
					return;
				}
				if (puzzleFilter.apply(rec.getAnalysis())) {
					puzzleParents.add(rec.getParent().toString());
				}
			});
			if (bar != null) {
				bar.step(String.format(Locale.ROOT,
						"pass=parents last=%s puzzle-parents=%d",
						fileName(input),
						puzzleParents.size()));
			}
		}
		return puzzleParents;
	}

	/**
	 * Writes the training jsonl.
	 * @param inputFiles input files
	 * @param writer writer
	 * @param puzzleFilter puzzle filter
	 * @param puzzleParents puzzle parents
	 * @param includeEngineMetadata include engine metadata
	 * @param maxRecords max records
	 * @param stats stats
	 * @param verbose verbose
	 * @param bar bar
	 * @throws IOException if the operation fails
	 */
	private static void writeTrainingJsonl(
			List<Path> inputFiles,
			BufferedWriter writer,
			Filter puzzleFilter,
			Set<String> puzzleParents,
			boolean includeEngineMetadata,
			long maxRecords,
			TrainingExportStats stats,
			boolean verbose,
			Bar bar) throws IOException {
		for (Path input : inputFiles) {
			long[] sourceRecordIndex = { 0L };
			streamRecordJson(input, objJson -> {
				if (maxRecords > 0L && stats.written >= maxRecords) {
					throw new StopTrainingExport();
				}
				long index = sourceRecordIndex[0]++;
				stats.seen++;
				Record rec = parseTrainingRecord(objJson, verbose, "record export training-jsonl");
				if (rec == null || rec.getPosition() == null) {
					stats.invalid++;
					return;
				}
				TrainingLabel label = trainingLabelFor(rec, puzzleFilter, puzzleParents);
				String line = toTrainingJsonlLine(rec, input, index, label, includeEngineMetadata);
				try {
					writer.write(line);
					writer.newLine();
				} catch (IOException ex) {
					throw new UncheckedIOException(ex);
				}
				stats.recordWritten(label);
			});
			if (bar != null) {
				bar.step(String.format(Locale.ROOT,
						"pass=write last=%s written=%d invalid=%d",
						fileName(input),
						stats.written,
						stats.invalid));
			}
			if (maxRecords > 0L && stats.written >= maxRecords) {
				throw new StopTrainingExport();
			}
		}
	}

	/**
	 * Parses the training record.
	 * @param objJson obj json
	 * @param verbose verbose
	 * @param label label
	 * @return computed value
	 */
	private static Record parseTrainingRecord(String objJson, boolean verbose, String label) {
		try {
			return Record.fromJson(objJson);
		} catch (Exception ex) {
			if (verbose) {
				System.err.println(label + ": skipped invalid record: " + ex.getMessage());
			}
			return null;
		}
	}

	/**
	 * Handles training label for.
	 * @param rec rec
	 * @param puzzleFilter puzzle filter
	 * @param puzzleParents puzzle parents
	 * @return computed value
	 */
	private static TrainingLabel trainingLabelFor(Record rec, Filter puzzleFilter, Set<String> puzzleParents) {
		if (puzzleFilter.apply(rec.getAnalysis())) {
			return new TrainingLabel(LABEL_VERIFIED_PUZZLE, 1, 2, "puzzle_filter_matched");
		}
		if (rec.getParent() != null && puzzleParents.contains(rec.getParent().toString())) {
			return new TrainingLabel(LABEL_VERIFIED_NEAR_PUZZLE, 1, 1, "sister_parent_matched");
		}
		return new TrainingLabel(LABEL_KNOWN_NON_PUZZLE, 0, 0, "random_position");
	}

	/**
	 * Converts this value to training jsonl line.
	 * @param rec rec
	 * @param input input
	 * @param sourceRecordIndex source record index
	 * @param label label
	 * @param includeEngineMetadata include engine metadata
	 * @return computed value
	 */
	private static String toTrainingJsonlLine(
			Record rec,
			Path input,
			long sourceRecordIndex,
			TrainingLabel label,
			boolean includeEngineMetadata) {
		String parentGroup = null;
		if (rec.getParent() != null
				&& (LABEL_VERIFIED_PUZZLE.equals(label.status) || LABEL_VERIFIED_NEAR_PUZZLE.equals(label.status))) {
			parentGroup = "crtk_parent_" + rec.getParent().signature();
		}
		StringBuilder sb = new StringBuilder(512);
		sb.append('{');
		appendJsonStringField(sb, "fen", rec.getPosition().toString());
		appendJsonStringField(sb, "label_status", label.status);
		appendJsonIntField(sb, "coarse_label", label.coarse);
		appendJsonNullableIntField(sb, "fine_label", label.fine);
		appendJsonStringField(sb, "source_kind", "crtk_record");
		appendJsonStringField(sb, "source_file", fileName(input));
		appendJsonLongField(sb, "source_record_index", sourceRecordIndex);
		appendJsonNullField(sb, "source_group_id");
		appendJsonStringField(sb, "sister_group_id", parentGroup);
		appendJsonNullField(sb, "game_id");
		appendJsonNullField(sb, "position_index");
		appendJsonStringField(sb, "verification_status", label.verificationStatus);
		if (includeEngineMetadata) {
			appendTrainingEngineMetadata(sb, rec);
		}
		sb.append('}');
		return sb.toString();
	}

	/**
	 * Handles append training engine metadata.
	 * @param sb sb
	 * @param rec rec
	 */
	private static void appendTrainingEngineMetadata(StringBuilder sb, Record rec) {
		chess.uci.Analysis analysis = rec.getAnalysis();
		Output pv1 = analysis == null ? null : analysis.getBestOutput(1);
		Output pv2 = analysis == null ? null : analysis.getBestOutput(2);
		Integer pv1Cp = cpValue(pv1);
		Integer pv2Cp = cpValue(pv2);
		appendJsonStringField(sb, "best_move", firstMoveUci(pv1));
		appendJsonNullableIntField(sb, "pv1_cp", pv1Cp);
		appendJsonNullableIntField(sb, "pv2_cp", pv2Cp);
		appendJsonNullableIntField(sb, "pv_gap_cp", pv1Cp != null && pv2Cp != null ? pv1Cp - pv2Cp : null);
		appendJsonNullableIntField(sb, "pv1_mate", mateValue(pv1));
		appendJsonNullableIntField(sb, "pv2_mate", mateValue(pv2));
		appendJsonNullableLongField(sb, "stockfish_nodes", pv1 != null && pv1.getNodes() > 0L ? pv1.getNodes() : null);
		appendJsonNullableIntField(sb, "stockfish_depth", pv1 != null ? (int) pv1.getDepth() : null);
		appendJsonStringField(sb, "stockfish_version", rec.getEngine());
		appendJsonStringField(sb, "engine_eval", formatEngineEvalWithKind(pv1 == null ? null : pv1.getEvaluation()));
		appendJsonRawField(sb, "engine_wdl", toWdlJson(pv1 == null ? null : pv1.getChances()));
		appendJsonRawField(sb, "multipv", multipvJson(analysis));
	}

	/**
	 * Handles multipv json.
	 * @param analysis analysis
	 * @return computed value
	 */
	private static String multipvJson(chess.uci.Analysis analysis) {
		if (analysis == null || analysis.isEmpty()) {
			return "[]";
		}
		StringBuilder sb = new StringBuilder(256).append('[');
		boolean first = true;
		for (int rank = 1; rank <= analysis.getPivots(); rank++) {
			Output output = analysis.getBestOutput(rank);
			if (output == null || !output.hasContent()) {
				continue;
			}
			if (!first) {
				sb.append(',');
			}
			sb.append('{');
			appendJsonIntField(sb, "rank", rank);
			appendJsonStringField(sb, "move", firstMoveUci(output));
			appendJsonNullableIntField(sb, "cp", cpValue(output));
			appendJsonNullableIntField(sb, "mate", mateValue(output));
			appendJsonRawField(sb, "pv", pvMovesJson(output.getMoves()));
			sb.append('}');
			first = false;
		}
		return sb.append(']').toString();
	}

	/**
	 * Handles pv moves json.
	 * @param moves moves
	 * @return computed value
	 */
	private static String pvMovesJson(short[] moves) {
		if (moves == null || moves.length == 0) {
			return "[]";
		}
		StringBuilder sb = new StringBuilder(moves.length * 8).append('[');
		for (int i = 0; i < moves.length; i++) {
			if (i > 0) {
				sb.append(',');
			}
			appendJsonStringValue(sb, Move.toString(moves[i]));
		}
		return sb.append(']').toString();
	}

	/**
	 * Handles cp value.
	 * @param output output
	 * @return computed value
	 */
	private static Integer cpValue(Output output) {
		Evaluation eval = output == null ? null : output.getEvaluation();
		return eval != null && eval.isValid() && !eval.isMate() ? eval.getValue() : null;
	}

	/**
	 * Handles mate value.
	 * @param output output
	 * @return computed value
	 */
	private static Integer mateValue(Output output) {
		Evaluation eval = output == null ? null : output.getEvaluation();
		return eval != null && eval.isValid() && eval.isMate() ? eval.getValue() : null;
	}

	/**
	 * Handles first move uci.
	 * @param output output
	 * @return computed value
	 */
	private static String firstMoveUci(Output output) {
		if (output == null || output.getMoves() == null || output.getMoves().length == 0) {
			return null;
		}
		return Move.toString(output.getMoves()[0]);
	}

	/**
	 * Handles format engine eval with kind.
	 * @param eval eval
	 * @return computed value
	 */
	private static String formatEngineEvalWithKind(Evaluation eval) {
		if (eval == null || !eval.isValid()) {
			return null;
		}
		return (eval.isMate() ? "mate " : "cp ") + eval.getValue();
	}

	/**
	 * Handles append json string field.
	 * @param sb sb
	 * @param name name
	 * @param value value
	 */
	private static void appendJsonStringField(StringBuilder sb, String name, String value) {
		appendJsonFieldName(sb, name);
		appendJsonStringValue(sb, value);
	}

	/**
	 * Handles append json int field.
	 * @param sb sb
	 * @param name name
	 * @param value value
	 */
	private static void appendJsonIntField(StringBuilder sb, String name, int value) {
		appendJsonFieldName(sb, name);
		sb.append(value);
	}

	/**
	 * Handles append json long field.
	 * @param sb sb
	 * @param name name
	 * @param value value
	 */
	private static void appendJsonLongField(StringBuilder sb, String name, long value) {
		appendJsonFieldName(sb, name);
		sb.append(value);
	}

	/**
	 * Handles append json nullable int field.
	 * @param sb sb
	 * @param name name
	 * @param value value
	 */
	private static void appendJsonNullableIntField(StringBuilder sb, String name, Integer value) {
		appendJsonFieldName(sb, name);
		if (value == null) {
			sb.append("null");
		} else {
			sb.append(value.intValue());
		}
	}

	/**
	 * Handles append json nullable long field.
	 * @param sb sb
	 * @param name name
	 * @param value value
	 */
	private static void appendJsonNullableLongField(StringBuilder sb, String name, Long value) {
		appendJsonFieldName(sb, name);
		if (value == null) {
			sb.append("null");
		} else {
			sb.append(value.longValue());
		}
	}

	/**
	 * Handles append json null field.
	 * @param sb sb
	 * @param name name
	 */
	private static void appendJsonNullField(StringBuilder sb, String name) {
		appendJsonFieldName(sb, name);
		sb.append("null");
	}

	/**
	 * Handles append json raw field.
	 * @param sb sb
	 * @param name name
	 * @param rawJson raw json
	 */
	private static void appendJsonRawField(StringBuilder sb, String name, String rawJson) {
		appendJsonFieldName(sb, name);
		sb.append(rawJson == null || rawJson.isEmpty() ? "null" : rawJson);
	}

	/**
	 * Handles append json field name.
	 * @param sb sb
	 * @param name name
	 */
	private static void appendJsonFieldName(StringBuilder sb, String name) {
		if (sb.length() > 1 && sb.charAt(sb.length() - 1) != '{' && sb.charAt(sb.length() - 1) != '[') {
			sb.append(',');
		}
		appendJsonStringValue(sb, name);
		sb.append(':');
	}

	/**
	 * Handles append json string value.
	 * @param sb sb
	 * @param value value
	 */
	private static void appendJsonStringValue(StringBuilder sb, String value) {
		if (value == null) {
			sb.append("null");
			return;
		}
		sb.append('"');
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
				case '\\' -> sb.append("\\\\");
				case '"' -> sb.append("\\\"");
				case '\n' -> sb.append("\\n");
				case '\r' -> sb.append("\\r");
				case '\t' -> sb.append("\\t");
				default -> {
					if (c < 0x20) {
						sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
					} else {
						sb.append(c);
					}
				}
			}
		}
		sb.append('"');
	}

	/**
	 * Provides training label behavior.
	 */
	private static final class TrainingLabel {
		 /**
		 * Stores the status.
		 */
		 private final String status;
		 /**
		 * Stores the coarse.
		 */
		 private final int coarse;
		 /**
		 * Stores the fine.
		 */
		 private final Integer fine;
		 /**
		 * Stores the verification status.
		 */
		 private final String verificationStatus;

		 /**
		 * Creates a new training label instance.
		 * @param status status
		 * @param coarse coarse
		 * @param fine fine
		 * @param verificationStatus verification status
		 */
		 private TrainingLabel(String status, int coarse, Integer fine, String verificationStatus) {
			this.status = status;
			this.coarse = coarse;
			this.fine = fine;
			this.verificationStatus = verificationStatus;
		}
	}

	/**
	 * Provides training export stats behavior.
	 */
	private static final class TrainingExportStats {
		 /**
		 * Stores the seen.
		 */
		 private long seen;
		 /**
		 * Stores the written.
		 */
		 private long written;
		 /**
		 * Stores the invalid.
		 */
		 private long invalid;
		 /**
		 * Stores the puzzles.
		 */
		 private long puzzles;
		 /**
		 * Stores the similar.
		 */
		 private long similar;
		 /**
		 * Stores the random.
		 */
		 private long random;

		 /**
		 * Handles reset for write pass.
		 */
		 private void resetForWritePass() {
			seen = 0L;
			written = 0L;
			invalid = 0L;
			puzzles = 0L;
			similar = 0L;
			random = 0L;
		}

		 /**
		 * Handles record written.
		 * @param label label
		 */
		 private void recordWritten(TrainingLabel label) {
			written++;
			if (LABEL_VERIFIED_PUZZLE.equals(label.status)) {
				puzzles++;
			} else if (LABEL_VERIFIED_NEAR_PUZZLE.equals(label.status)) {
				similar++;
			} else {
				random++;
			}
		}
	}

	/**
	 * Provides stop training export behavior.
	 */
	private static final class StopTrainingExport extends RuntimeException {
		 /**
		 * Shared serial version uid constant.
		 */
		 private static final long serialVersionUID = 1L;
	}

	/**
	 * Handles file progress bar.
	 * @param input input
	 * @param passes passes
	 * @param label label
	 * @return computed value
	 */
	private static Bar fileProgressBar(Path input, int passes, String label) {
		long size = fileSize(input);
		long total = size <= 0L ? 0L : size * Math.max(1, passes);
		return progressBar(total, label);
	}

	/**
	 * Handles file size.
	 * @param input input
	 * @return computed value
	 */
	private static long fileSize(Path input) {
		try {
			return input == null ? 0L : Files.size(input);
		} catch (IOException ex) {
			return 0L;
		}
	}

	/**
	 * Handles byte progress.
	 * @param bar bar
	 * @return computed value
	 */
	private static LongConsumer byteProgress(Bar bar) {
		return bar == null ? null : bar::set;
	}

	/**
	 * Handles finish progress.
	 * @param bar bar
	 */
	private static void finishProgress(Bar bar) {
		if (bar != null) {
			bar.finish();
		}
	}

	/**
	 * Verifies that a record conversion input exists before calling conversion
	 * helpers that log and return on read failure.
	 *
	 * @param input input path to validate
	 * @param label command label for diagnostics
	 */
	private static void requireReadableFile(Path input, String label) {
		if (input == null || !Files.isRegularFile(input) || !Files.isReadable(input)) {
			System.err.println(label + ": input file not found or not readable: " + input);
			System.exit(3);
		}
	}

	/**
	 * Handles update classifier progress.
	 * @param bar bar
	 * @param progress progress
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
	 * @param bar bar
	 * @param input input
	 * @param stats stats
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
	 * @param path path
	 * @return computed value
	 */
	private static String fileName(Path path) {
		if (path == null || path.getFileName() == null) {
			return "";
		}
		return path.getFileName().toString();
	}

	/**
	 * Handles progress bar.
	 * @param totalRecords total records
	 * @param label label
	 * @return computed value
	 */
	private static Bar progressBar(long totalRecords, String label) {
		if (totalRecords <= 0) {
			return null;
		}
		return new Bar(totalRecords, label);
	}

	/**
	 * Converts this value to critical move.
	 * @param moves moves
	 * @return computed value
	 */
	private static Short toCriticalMove(short[] moves) {
		if (moves == null || moves.length == 0) {
			return null;
		}
		for (short move : moves) {
			if (move == Move.NO_MOVE) {
				continue;
			}
			return move;
		}
		return null;
	}

	/**
	 * Converts this value to wdl json.
	 * @param chances chances
	 * @return computed value
	 */
	private static String toWdlJson(Chances chances) {
		if (chances == null) {
			return "null";
		}
		return new StringBuilder(32)
				.append('[')
				.append(chances.getWinChance())
				.append(',')
				.append(chances.getDrawChance())
				.append(',')
				.append(chances.getLossChance())
				.append(']')
				.toString();
	}

	/**
	 * Handles format engine eval.
	 * @param eval eval
	 * @return computed value
	 */
	private static String formatEngineEval(Evaluation eval) {
		if (eval == null || !eval.isValid()) {
			return null;
		}
		if (eval.isMate()) {
			return "mate " + eval.getValue();
		}
		return String.valueOf(eval.getValue());
	}

	/**
	 * Handles invert policy map.
	 * @param policyMap policy map
	 * @return computed value
	 */
	private static int[] invertPolicyMap(int[] policyMap) {
		if (policyMap == null || policyMap.length == 0) {
			return null;
		}
		int max = -1;
		for (int value : policyMap) {
			if (value > max) {
				max = value;
			}
		}
		if (max < 0) {
			return null;
		}
		int[] inverse = new int[max + 1];
		java.util.Arrays.fill(inverse, -1);
		for (int compressedIndex = 0; compressedIndex < policyMap.length; compressedIndex++) {
			int rawIndex = policyMap[compressedIndex];
			if (rawIndex >= 0 && rawIndex < inverse.length) {
				inverse[rawIndex] = compressedIndex;
			}
		}
		return inverse;
	}

	/**
	 * Handles lc0 policy eval.
	 * @param position position
	 * @param network network
	 * @param rawPolicyIndex raw policy index
	 * @param policyMapInverse policy map inverse
	 * @return computed value
	 */
	private static Lc0PolicyEval lc0PolicyEval(
			chess.core.Position position,
			Network network,
			int rawPolicyIndex,
			int[] policyMapInverse) {
		if (network == null || position == null) {
			return null;
		}
		float[] encoded = Encoder.encode(position);
		Network.Prediction prediction = network.predictEncoded(encoded);
		String wdlJson = toWdlJson(prediction.wdl());
		Double prob = null;
		int policyIndex = rawPolicyIndex;
		if (rawPolicyIndex >= 0) {
			if (policyMapInverse != null) {
				if (rawPolicyIndex < policyMapInverse.length) {
					policyIndex = policyMapInverse[rawPolicyIndex];
				}
				if (policyIndex < 0) {
					policyIndex = -1;
				}
			}
			float[] logits = prediction.policy();
			if (policyIndex >= 0 && logits != null && policyIndex < logits.length) {
				double max = Double.NEGATIVE_INFINITY;
				for (float v : logits) {
					if (v > max) {
						max = v;
					}
				}
				double denom = 0.0;
				for (float v : logits) {
					denom += Math.exp(v - max);
				}
				if (denom > 0.0) {
					prob = Math.exp(logits[policyIndex] - max) / denom;
				}
			}
		}
		return new Lc0PolicyEval(prob != null ? prob * 100.0 : null, wdlJson);
	}

	/**
	 * Converts this value to wdl json.
	 * @param wdl wdl
	 * @return computed value
	 */
	private static String toWdlJson(float[] wdl) {
		if (wdl == null || wdl.length < 3) {
			return "null";
		}
		return new StringBuilder(64)
				.append('[')
				.append(formatPercent(wdl[0] * 100.0))
				.append(',')
				.append(formatPercent(wdl[1] * 100.0))
				.append(',')
				.append(formatPercent(wdl[2] * 100.0))
				.append(']')
				.toString();
	}

	/**
	 * Handles format percent.
	 * @param value value
	 * @return computed value
	 */
	private static String formatPercent(double value) {
		return String.format(Locale.ROOT, "%.6f", value);
	}

	/**
	 * Provides lc0 policy eval behavior.
	 */
	private static final class Lc0PolicyEval {
		 /**
		 * Stores the policy percent.
		 */
		 private final Double policyPercent;
		 /**
		 * Stores the wdl json.
		 */
		 private final String wdlJson;

		 /**
		 * Creates a new lc0 policy eval instance.
		 * @param policyPercent policy percent
		 * @param wdlJson wdl json
		 */
		 private Lc0PolicyEval(Double policyPercent, String wdlJson) {
			this.policyPercent = policyPercent;
			this.wdlJson = wdlJson;
		}
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

	/**
	 * Handles exit with error.
	 * @param message message
	 */
	private static void exitWithError(String message) {
		System.err.println(message);
		System.exit(2);
		throw new IllegalStateException(message);
	}

	/**
	 * Handles exit with error.
	 * @param message message
	 * @param cause cause
	 * @param verbose verbose
	 */
	private static void exitWithError(String message, Throwable cause, boolean verbose) {
		System.err.println(message);
		if (verbose && cause != null) {
			cause.printStackTrace(System.err);
		}
		System.exit(2);
		throw new IllegalStateException(message, cause);
	}
}
