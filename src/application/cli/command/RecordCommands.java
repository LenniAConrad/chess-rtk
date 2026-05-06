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
import static application.cli.Constants.OPT_RATINGS_CSV;
import static application.cli.Constants.OPT_RECURSIVE;
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
import static application.cli.PathOps.ensureParentDir;
import static application.cli.command.RecordCommandSupport.byteProgress;
import static application.cli.command.RecordCommandSupport.exitWithError;
import static application.cli.command.RecordCommandSupport.fileProgressBar;
import static application.cli.command.RecordCommandSupport.finishProgress;
import static application.cli.command.RecordCommandSupport.requireReadableFile;

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

import application.Config;
import application.console.Bar;
import chess.core.Move;
import chess.io.Converter;
import chess.io.ClassifierDatasetExporter;
import chess.io.PuzzleEloExporter;
import chess.io.Writer;
import chess.nn.lc0.cnn.Encoder;
import chess.nn.lc0.cnn.Network;
import chess.nn.lc0.cnn.PolicyEncoder;
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
@SuppressWarnings({"java:S1192", "java:S3776"})
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
	 * Shared Elo-rated puzzle jsonl constant.
	 */
	private static final String EXT_PUZZLE_ELO_JSONL = ".puzzle-elo.jsonl";
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
	 * Shared classifier command label constant.
	 */
	private static final String COMMAND_RECORD_DATASET_CLASSIFIER = "record dataset classifier";
	/**
	 * Shared puzzle-jsonl command label constant.
	 */
	private static final String COMMAND_RECORD_EXPORT_PUZZLE_JSONL = "record export puzzle-jsonl";
	/**
	 * Shared Elo-rated puzzle export command label constant.
	 */
	private static final String COMMAND_RECORD_EXPORT_PUZZLE_ELO_JSONL = "record export puzzle-elo-jsonl";
	/**
	 * Shared training-jsonl command label constant.
	 */
	private static final String COMMAND_RECORD_EXPORT_TRAINING_JSONL = "record export training-jsonl";
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

		Bar plainBar = fileProgressBar(in, 1, RECORD_EXPORT_PLAIN);
		try {
			Converter.recordToPlain(exportAll, filter, in, out, byteProgress(plainBar));
		} finally {
			finishProgress(plainBar);
		}
		if (csv || csvOut != null) {
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
				exitWithError(COMMAND_RECORD_DATASET_CLASSIFIER + ": failed to export dataset: " + ex.getMessage(),
						ex, verbose);
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
		exitWithError(COMMAND_RECORD_DATASET_CLASSIFIER + ": missing " + OPT_OUTPUT
				+ " when exporting multiple inputs or a directory");
		throw unreachable();
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

		validatePuzzleJsonlArguments(puzzles, nonpuzzles, weights);
		Filter filter = parseFilterOrExit(COMMAND_RECORD_EXPORT_PUZZLE_JSONL, OPT_FILTER, filterDsl, verbose);
		Filter puzzleVerify = resolvePuzzleJsonlVerify(puzzles, nonpuzzles);
		Path output = defaultOutputPath(in, out, EXT_PUZZLE_JSONL);
		Lc0Artifacts lc0 = loadLc0Artifacts(weights, verbose, COMMAND_RECORD_EXPORT_PUZZLE_JSONL);
		exportPuzzleJsonl(in, output,
				new PuzzleJsonlExportContext(filter, puzzleVerify, puzzles, nonpuzzles, verbose, lc0),
				new PuzzleJsonlExportStats());
	}

	/**
	 * Handles {@code record export puzzle-elo-jsonl}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runRecordToPuzzleEloJsonl(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		boolean recursive = a.flag(OPT_RECURSIVE);
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

		Config.reload();
		try {
			PuzzleEloExporter.Summary summary = ratingsCsv == null
					? PuzzleEloExporter.export(
							inputFiles,
							output,
							new PuzzleEloExporter.Options(Config.getPuzzleVerify(), maxPuzzles, threads))
					: PuzzleEloExporter.exportFromRatingCsv(inputFiles, output, ratingsCsv, Config.getPuzzleVerify());
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
			exitWithError(COMMAND_RECORD_EXPORT_PUZZLE_ELO_JSONL + ": failed to export: "
					+ ex.getMessage(), ex, verbose);
		}
	}

	/**
	 * Builds a default output for the Elo-rated puzzle exporter.
	 */
	private static Path defaultPuzzleEloOutput(List<Path> inputFiles) {
		if (inputFiles.size() == 1) {
			return defaultOutputPath(inputFiles.get(0), null, EXT_PUZZLE_ELO_JSONL);
		}
		return Paths.get("puzzles" + EXT_PUZZLE_ELO_JSONL);
	}

	/**
	 * Prevents accidental in-place exports.
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
	private static void validatePuzzleJsonlArguments(boolean puzzles, boolean nonpuzzles, Path weights) {
		if (puzzles && nonpuzzles) {
			exitWithError(COMMAND_RECORD_EXPORT_PUZZLE_JSONL + ": cannot combine "
					+ OPT_PUZZLES + " and " + OPT_NONPUZZLES);
		}
		if (weights == null) {
			exitWithError(COMMAND_RECORD_EXPORT_PUZZLE_JSONL + ": missing "
					+ OPT_WEIGHTS + " for LC0 policy values");
		}
	}

	/**
	 * Resolves the optional puzzle verifier for puzzle-jsonl export.
	 *
	 * @param puzzles only export puzzles
	 * @param nonpuzzles only export non-puzzles
	 * @return verifier filter or {@code null}
	 */
	private static Filter resolvePuzzleJsonlVerify(boolean puzzles, boolean nonpuzzles) {
		if (!(puzzles || nonpuzzles)) {
			return null;
		}
		Config.reload();
		return Config.getPuzzleVerify();
	}

	/**
	 * Returns the explicit output path or a sibling path with the requested suffix.
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
		String stem = input.getFileName().toString();
		int dot = stem.lastIndexOf('.');
		if (dot > 0) {
			stem = stem.substring(0, dot);
		}
		return input.resolveSibling(stem + suffix);
	}

	/**
	 * Loads LC0 network artifacts for puzzle-jsonl export.
	 *
	 * @param weights LC0 weights path
	 * @param verbose whether verbose diagnostics are enabled
	 * @param command command label for diagnostics
	 * @return loaded LC0 artifacts
	 */
	private static Lc0Artifacts loadLc0Artifacts(Path weights, boolean verbose, String command) {
		try {
			return new Lc0Artifacts(Network.load(weights), invertPolicyMap(Network.loadPolicyMap(weights)));
		} catch (IOException ex) {
			exitWithError(command + ": failed to load LC0 weights: " + ex.getMessage(), ex, verbose);
			throw unreachable();
		}
	}

	/**
	 * Streams puzzle-jsonl output and prints a final summary.
	 *
	 * @param input source record file
	 * @param output target jsonl file
	 * @param context export settings
	 * @param stats running counters
	 */
	private static void exportPuzzleJsonl(
			Path input,
			Path output,
			PuzzleJsonlExportContext context,
			PuzzleJsonlExportStats stats) {
		try {
			ensureParentDir(output);
		} catch (IOException ex) {
			exitWithError(COMMAND_RECORD_EXPORT_PUZZLE_JSONL + ": failed to prepare output: " + ex.getMessage(),
					ex, context.verbose);
		}
		Bar bar = fileProgressBar(input, 1, COMMAND_RECORD_EXPORT_PUZZLE_JSONL);
		try (Network network = context.lc0.network;
				BufferedWriter writer = Files.newBufferedWriter(output)) {
			PuzzleJsonlWriteContext writeContext =
					new PuzzleJsonlWriteContext(context, network, writer, bar);
			streamRecordJson(input,
					objJson -> writePuzzleJsonlRecord(objJson, writeContext, stats),
					byteProgress(bar));
		} catch (IOException | UncheckedIOException ex) {
			exitWithError(COMMAND_RECORD_EXPORT_PUZZLE_JSONL + ": failed to write output: " + ex.getMessage(),
					ex, context.verbose);
		}
		finishPuzzleJsonlExport(bar, stats);
		System.out.printf(
				COMMAND_RECORD_EXPORT_PUZZLE_JSONL + ": wrote %d/%d %s records (skipped %d invalid, %d filtered) to %s%n",
				stats.written,
				stats.seen,
				PUZZLE_JSONL_FORMAT_NAME,
				stats.invalid,
				stats.skipped,
				output);
	}

	/**
	 * Writes one puzzle-jsonl record when it passes the active filters.
	 *
	 * @param objJson raw record json
	 * @param context active write context
	 * @param stats running counters
	 */
	private static void writePuzzleJsonlRecord(
			String objJson,
			PuzzleJsonlWriteContext context,
			PuzzleJsonlExportStats stats) {
		stats.seen++;
		Record rec = parseTrainingRecord(objJson, context.options.verbose, COMMAND_RECORD_EXPORT_PUZZLE_JSONL);
		if (rec == null) {
			stats.invalid++;
			updatePuzzleJsonlProgress(context.bar, stats);
			return;
		}
		if (!acceptPuzzleJsonlRecord(objJson, rec, context.options)) {
			stats.skipped++;
			updatePuzzleJsonlProgress(context.bar, stats);
			return;
		}
		String line = toPuzzleJsonlLine(rec, context.network, context.options.lc0.policyMapInverse);
		if (line == null) {
			stats.skipped++;
			updatePuzzleJsonlProgress(context.bar, stats);
			return;
		}
		try {
			context.writer.write(line);
			context.writer.newLine();
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		stats.written++;
		updatePuzzleJsonlProgress(context.bar, stats);
	}

	/**
	 * Checks whether a puzzle-jsonl candidate record should be exported.
	 *
	 * @param objJson raw record json
	 * @param rec parsed record
	 * @param options export settings
	 * @return {@code true} when the record should be written
	 */
	private static boolean acceptPuzzleJsonlRecord(
			String objJson,
			Record rec,
			PuzzleJsonlExportContext options) {
		if (options.filter != null && !options.filter.apply(rec.getAnalysis())) {
			return false;
		}
		if (!(options.puzzlesOnly || options.nonpuzzlesOnly)) {
			return true;
		}
		boolean isPuzzle = isPuzzleRecordJson(objJson, rec, options.puzzleVerify);
		if (options.puzzlesOnly) {
			return isPuzzle;
		}
		return !isPuzzle;
	}

	/**
	 * Refreshes puzzle-jsonl progress output periodically.
	 *
	 * @param bar optional progress bar
	 * @param stats running counters
	 */
	private static void updatePuzzleJsonlProgress(Bar bar, PuzzleJsonlExportStats stats) {
		if (bar != null && stats.seen % 1000L == 0L) {
			bar.setPostfix(String.format(Locale.ROOT,
					"written=%d skipped=%d invalid=%d", stats.written, stats.skipped, stats.invalid));
		}
	}

	/**
	 * Finalizes the puzzle-jsonl progress bar.
	 *
	 * @param bar optional progress bar
	 * @param stats running counters
	 */
	private static void finishPuzzleJsonlExport(Bar bar, PuzzleJsonlExportStats stats) {
		if (bar != null) {
			bar.setPostfix(String.format(Locale.ROOT,
					"written=%d skipped=%d invalid=%d", stats.written, stats.skipped, stats.invalid));
		}
		finishProgress(bar);
	}

	/**
	 * Holds LC0 resources needed by JSONL exporters.
	 */
	private static final class Lc0Artifacts {
		/**
		 * Loaded network instance.
		 */
		private final Network network;
		/**
		 * Inverse policy-map lookup.
		 */
		private final int[] policyMapInverse;

		/**
		 * Creates a new LC0 artifact bundle.
		 *
		 * @param network loaded network
		 * @param policyMapInverse inverse policy-map lookup
		 */
		private Lc0Artifacts(Network network, int[] policyMapInverse) {
			this.network = network;
			this.policyMapInverse = policyMapInverse;
		}
	}

	/**
	 * Immutable configuration for puzzle-jsonl export.
	 */
	private static final class PuzzleJsonlExportContext {
		/**
		 * Optional row filter.
		 */
		private final Filter filter;
		/**
		 * Optional puzzle verifier.
		 */
		private final Filter puzzleVerify;
		/**
		 * Whether only puzzle rows should be written.
		 */
		private final boolean puzzlesOnly;
		/**
		 * Whether only non-puzzle rows should be written.
		 */
		private final boolean nonpuzzlesOnly;
		/**
		 * Whether verbose diagnostics are enabled.
		 */
		private final boolean verbose;
		/**
		 * Loaded LC0 resources.
		 */
		private final Lc0Artifacts lc0;

		/**
		 * Creates a new puzzle-jsonl export context.
		 *
		 * @param filter optional row filter
		 * @param puzzleVerify optional puzzle verifier
		 * @param puzzlesOnly whether only puzzles should be written
		 * @param nonpuzzlesOnly whether only non-puzzles should be written
		 * @param verbose whether verbose diagnostics are enabled
		 * @param lc0 loaded LC0 resources
		 */
		private PuzzleJsonlExportContext(
				Filter filter,
				Filter puzzleVerify,
				boolean puzzlesOnly,
				boolean nonpuzzlesOnly,
				boolean verbose,
				Lc0Artifacts lc0) {
			this.filter = filter;
			this.puzzleVerify = puzzleVerify;
			this.puzzlesOnly = puzzlesOnly;
			this.nonpuzzlesOnly = nonpuzzlesOnly;
			this.verbose = verbose;
			this.lc0 = lc0;
		}
	}

	/**
	 * Mutable state for a single puzzle-jsonl write pass.
	 */
	private static final class PuzzleJsonlWriteContext {
		/**
		 * Shared export options.
		 */
		private final PuzzleJsonlExportContext options;
		/**
		 * Loaded network to use for the current write pass.
		 */
		private final Network network;
		/**
		 * Destination writer.
		 */
		private final BufferedWriter writer;
		/**
		 * Optional progress bar.
		 */
		private final Bar bar;

		/**
		 * Creates a new write context.
		 *
		 * @param options shared export options
		 * @param network loaded network
		 * @param writer destination writer
		 * @param bar optional progress bar
		 */
		private PuzzleJsonlWriteContext(
				PuzzleJsonlExportContext options,
				Network network,
				BufferedWriter writer,
				Bar bar) {
			this.options = options;
			this.network = network;
			this.writer = writer;
			this.bar = bar;
		}
	}

	/**
	 * Running counters for puzzle-jsonl export.
	 */
	private static final class PuzzleJsonlExportStats {
		/**
		 * Total input rows seen.
		 */
		private long seen;
		/**
		 * Rows written to output.
		 */
		private long written;
		/**
		 * Rows skipped by filters or empty conversions.
		 */
		private long skipped;
		/**
		 * Invalid input rows.
		 */
		private long invalid;
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
		TrainingJsonlRequest request = parseTrainingJsonlRequest(a);
		Bar bar = progressBar(request.inputFiles.size() * 2L, "training-jsonl files");
		TrainingExportStats stats = new TrainingExportStats();
		Set<String> puzzleParents = collectPuzzleParentsOrExit(request, bar);
		stats.resetForWritePass();
		ensureTrainingOutputReady(request.output, request.verbose, bar);
		writeTrainingJsonlOutput(request, puzzleParents, stats, bar);
		finishProgress(bar);
		System.out.printf(Locale.ROOT,
				COMMAND_RECORD_EXPORT_TRAINING_JSONL
						+ ": wrote %d/%d records to %s (puzzles=%d, similar=%d, random=%d, invalid=%d, puzzle-parents=%d)%n",
				stats.written,
				stats.seen,
				request.output,
				stats.puzzles,
				stats.similar,
				stats.random,
				stats.invalid,
				puzzleParents.size());
	}

	/**
	 * Parses and validates a training-jsonl export request.
	 *
	 * @param a command arguments
	 * @return normalized export request
	 */
	private static TrainingJsonlRequest parseTrainingJsonlRequest(Argv a) {
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
			exitWithError(COMMAND_RECORD_EXPORT_TRAINING_JSONL + ": missing "
					+ OPT_INPUT + INPUTS_OR_POSITIONAL_HINT);
		}
		if (out == null) {
			out = deriveTrainingJsonlOutputOrExit(inputs);
		}
		long maxRecords = maxRecordsOpt == null ? 0L : maxRecordsOpt.longValue();
		if (maxRecords < 0L) {
			exitWithError(COMMAND_RECORD_EXPORT_TRAINING_JSONL + ": "
					+ OPT_MAX_RECORDS + MUST_BE_NON_NEGATIVE);
		}

		Filter puzzleFilter = resolveTrainingPuzzleFilter(puzzleFilterDsl, verbose);
		List<Path> inputFiles =
				collectRecordInputsOrExit(COMMAND_RECORD_EXPORT_TRAINING_JSONL, inputs, recursive, verbose);
		if (inputFiles.isEmpty()) {
			exitWithError(COMMAND_RECORD_EXPORT_TRAINING_JSONL + ": no input files found");
		}
		validateTrainingOutputPath(inputFiles, out);
		return new TrainingJsonlRequest(out, inputFiles, puzzleFilter, includeEngineMetadata, maxRecords, verbose);
	}

	/**
	 * Collects puzzle-parent groups for training-jsonl export.
	 *
	 * @param request export request
	 * @param bar optional progress bar
	 * @return set of puzzle-parent FENs
	 */
	private static Set<String> collectPuzzleParentsOrExit(TrainingJsonlRequest request, Bar bar) {
		try {
			return collectPuzzleParentFens(request.inputFiles, request.puzzleFilter, request.verbose, bar);
		} catch (IOException | RuntimeException ex) {
			finishProgress(bar);
			exitWithError(COMMAND_RECORD_EXPORT_TRAINING_JSONL
					+ ": failed while scanning puzzle parents: " + ex.getMessage(), ex, request.verbose);
			throw unreachable();
		}
	}

	/**
	 * Creates the destination directory for training-jsonl export.
	 *
	 * @param output output path
	 * @param verbose whether verbose diagnostics are enabled
	 * @param bar optional progress bar
	 */
	private static void ensureTrainingOutputReady(Path output, boolean verbose, Bar bar) {
		try {
			ensureParentDir(output);
		} catch (IOException ex) {
			finishProgress(bar);
			exitWithError(COMMAND_RECORD_EXPORT_TRAINING_JSONL
					+ ": failed to prepare output: " + ex.getMessage(), ex, verbose);
		}
	}

	/**
	 * Executes the training-jsonl write pass.
	 *
	 * @param request export request
	 * @param puzzleParents parent-group lookup
	 * @param stats running counters
	 * @param bar optional progress bar
	 */
	private static void writeTrainingJsonlOutput(
			TrainingJsonlRequest request,
			Set<String> puzzleParents,
			TrainingExportStats stats,
			Bar bar) {
		try (BufferedWriter writer = Files.newBufferedWriter(request.output, StandardCharsets.UTF_8)) {
			writeTrainingJsonl(request.inputFiles, writer,
					new TrainingWriteContext(request, puzzleParents, stats, bar));
		} catch (StopTrainingExport ignored) {
			// max-records reached cleanly
		} catch (IOException | UncheckedIOException ex) {
			finishProgress(bar);
			exitWithError(COMMAND_RECORD_EXPORT_TRAINING_JSONL
					+ ": failed to write output: " + ex.getMessage(), ex, request.verbose);
		}
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
		if (!rec.getPosition().isLegalMove(criticalMove)) {
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
		exitWithError(COMMAND_RECORD_EXPORT_TRAINING_JSONL + ": missing " + OPT_OUTPUT
				+ " when exporting multiple inputs or a directory");
		throw unreachable();
	}

	/**
	 * Handles resolve training puzzle filter.
	 * @param puzzleFilterDsl puzzle filter dsl
	 * @param verbose verbose
	 * @return computed value
	 */
	private static Filter resolveTrainingPuzzleFilter(String puzzleFilterDsl, boolean verbose) {
		if (puzzleFilterDsl != null && !puzzleFilterDsl.isEmpty()) {
			return parseFilterOrExit(COMMAND_RECORD_EXPORT_TRAINING_JSONL, OPT_FILTER, puzzleFilterDsl, verbose);
		}
		Config.reload();
		Filter puzzleFilter = Config.getPuzzleVerify();
		if (puzzleFilter == null) {
			exitWithError(COMMAND_RECORD_EXPORT_TRAINING_JSONL + ": missing puzzle DSL; pass " + OPT_FILTER
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
			exitWithError(COMMAND_RECORD_EXPORT_TRAINING_JSONL + ": " + OPT_OUTPUT + " must be a JSONL file path");
		}
		Path outputAbs = output.toAbsolutePath().normalize();
		for (Path input : inputFiles) {
				if (outputAbs.equals(input.toAbsolutePath().normalize())) {
					exitWithError(COMMAND_RECORD_EXPORT_TRAINING_JSONL + ": output cannot overwrite input file " + input);
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
					Record rec = parseTrainingRecord(objJson, verbose, COMMAND_RECORD_EXPORT_TRAINING_JSONL);
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
			TrainingWriteContext context) throws IOException {
		for (Path input : inputFiles) {
			writeTrainingJsonlFile(input, writer, context);
			if (context.bar != null) {
				context.bar.step(String.format(Locale.ROOT,
						"pass=write last=%s written=%d invalid=%d",
						fileName(input),
						context.stats.written,
						context.stats.invalid));
			}
			if (context.hasReachedLimit()) {
				throw new StopTrainingExport();
			}
		}
	}

	/**
	 * Writes all training-jsonl rows for a single input file.
	 *
	 * @param input source input file
	 * @param writer destination writer
	 * @param context write context
	 * @throws IOException if the input stream fails
	 */
	private static void writeTrainingJsonlFile(
			Path input,
			BufferedWriter writer,
			TrainingWriteContext context) throws IOException {
		long[] sourceRecordIndex = { 0L };
		streamRecordJson(input,
				objJson -> writeTrainingJsonlRecord(objJson, input, sourceRecordIndex[0]++, writer, context));
	}

	/**
	 * Writes one training-jsonl row.
	 *
	 * @param objJson raw input json
	 * @param input source file
	 * @param sourceRecordIndex source-record index within the input file
	 * @param writer destination writer
	 * @param context write context
	 */
	private static void writeTrainingJsonlRecord(
			String objJson,
			Path input,
			long sourceRecordIndex,
			BufferedWriter writer,
			TrainingWriteContext context) {
		if (context.hasReachedLimit()) {
			throw new StopTrainingExport();
		}
		context.stats.seen++;
		Record rec = parseTrainingRecord(objJson, context.verbose, COMMAND_RECORD_EXPORT_TRAINING_JSONL);
		if (rec == null || rec.getPosition() == null) {
			context.stats.invalid++;
			return;
		}
		TrainingLabel label = trainingLabelFor(rec, context.puzzleFilter, context.puzzleParents);
		String line = toTrainingJsonlLine(rec, input, sourceRecordIndex, label, context.includeEngineMetadata);
		try {
			writer.write(line);
			writer.newLine();
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		context.stats.recordWritten(label);
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
	 * Immutable request for training-jsonl export.
	 */
	private static final class TrainingJsonlRequest {
		/**
		 * Destination output path.
		 */
		private final Path output;
		/**
		 * Sorted input files.
		 */
		private final List<Path> inputFiles;
		/**
		 * Puzzle classification filter.
		 */
		private final Filter puzzleFilter;
		/**
		 * Whether engine metadata should be included.
		 */
		private final boolean includeEngineMetadata;
		/**
		 * Maximum number of records to write.
		 */
		private final long maxRecords;
		/**
		 * Whether verbose diagnostics are enabled.
		 */
		private final boolean verbose;

		/**
		 * Creates a new training-jsonl request.
		 *
		 * @param output destination output path
		 * @param inputFiles sorted input files
		 * @param puzzleFilter puzzle classification filter
		 * @param includeEngineMetadata whether engine metadata should be included
		 * @param maxRecords maximum number of records to write
		 * @param verbose whether verbose diagnostics are enabled
		 */
		private TrainingJsonlRequest(
				Path output,
				List<Path> inputFiles,
				Filter puzzleFilter,
				boolean includeEngineMetadata,
				long maxRecords,
				boolean verbose) {
			this.output = output;
			this.inputFiles = inputFiles;
			this.puzzleFilter = puzzleFilter;
			this.includeEngineMetadata = includeEngineMetadata;
			this.maxRecords = maxRecords;
			this.verbose = verbose;
		}
	}

	/**
	 * Mutable context for the training-jsonl write pass.
	 */
	private static final class TrainingWriteContext {
		/**
		 * Puzzle classification filter.
		 */
		private final Filter puzzleFilter;
		/**
		 * Parent-group lookup.
		 */
		private final Set<String> puzzleParents;
		/**
		 * Whether engine metadata should be included.
		 */
		private final boolean includeEngineMetadata;
		/**
		 * Maximum number of records to write.
		 */
		private final long maxRecords;
		/**
		 * Whether verbose diagnostics are enabled.
		 */
		private final boolean verbose;
		/**
		 * Running output counters.
		 */
		private final TrainingExportStats stats;
		/**
		 * Optional progress bar.
		 */
		private final Bar bar;

		/**
		 * Creates a new training write context.
		 *
		 * @param request export request
		 * @param puzzleParents parent-group lookup
		 * @param stats running counters
		 * @param bar optional progress bar
		 */
		private TrainingWriteContext(
				TrainingJsonlRequest request,
				Set<String> puzzleParents,
				TrainingExportStats stats,
				Bar bar) {
			this.puzzleFilter = request.puzzleFilter;
			this.puzzleParents = puzzleParents;
			this.includeEngineMetadata = request.includeEngineMetadata;
			this.maxRecords = request.maxRecords;
			this.verbose = request.verbose;
			this.stats = stats;
			this.bar = bar;
		}

		/**
		 * Returns whether the configured max-record limit was reached.
		 *
		 * @return {@code true} when writing should stop
		 */
		private boolean hasReachedLimit() {
			return maxRecords > 0L && stats.written >= maxRecords;
		}
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
			return new int[0];
		}
		int max = -1;
		for (int value : policyMap) {
			if (value > max) {
				max = value;
			}
		}
		if (max < 0) {
			return new int[0];
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

}
