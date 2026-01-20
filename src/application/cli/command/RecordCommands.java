package application.cli.command;

import static application.cli.Constants.OPT_FILTER;
import static application.cli.Constants.OPT_FILTER_SHORT;
import static application.cli.Constants.OPT_INPUT;
import static application.cli.Constants.OPT_INPUT_SHORT;
import static application.cli.Constants.OPT_MAX_RECORDS;
import static application.cli.Constants.OPT_NONPUZZLES;
import static application.cli.Constants.OPT_OUTPUT;
import static application.cli.Constants.OPT_OUTPUT_SHORT;
import static application.cli.Constants.OPT_PUZZLES;
import static application.cli.Constants.OPT_RECURSIVE;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;
import static application.cli.Constants.OPT_EXPORT_ALL;
import static application.cli.Constants.OPT_EXPORT_ALL_SHORT;
import static application.cli.Constants.OPT_SIDELINES;
import static application.cli.Constants.OPT_CSV;
import static application.cli.Constants.OPT_CSV_OUTPUT;
import static application.cli.Constants.OPT_CSV_OUTPUT_SHORT;
import static application.cli.PathOps.ensureParentDir;

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
import chess.io.Converter;
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
	private static final String EXT_JSON = ".json";
	private static final String EXT_JSONL = ".jsonl";
	private static final String EXT_RECORD = ".record";

	/**
	 * Utility class; prevent instantiation.
	 */
	private RecordCommands() {
		// utility
	}

	/**
	 * Handles {@code record-to-plain}.
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

		Filter filter = null;
		if (filterDsl != null && !filterDsl.isEmpty()) {
			filter = FilterDSL.fromString(filterDsl);
		}

		Converter.recordToPlain(exportAll, filter, in, out);
		if (csv || csvOut != null) {
			Converter.recordToCsv(filter, in, csvOut);
		}
	}

	/**
	 * Handles {@code record-to-csv}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runRecordToCsv(Argv a) {
		String filterDsl = a.string(OPT_FILTER, OPT_FILTER_SHORT);
		Path in = a.pathRequired(OPT_INPUT, OPT_INPUT_SHORT);
		Path out = a.path(OPT_OUTPUT, OPT_OUTPUT_SHORT);
		a.ensureConsumed();

		Filter filter = null;
		if (filterDsl != null && !filterDsl.isEmpty()) {
			filter = FilterDSL.fromString(filterDsl);
		}

		Converter.recordToCsv(filter, in, out);
	}

	/**
	 * Handles {@code record-to-dataset}.
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

		try {
			chess.io.RecordDatasetExporter.export(in, out);
			System.out.printf("Wrote %s.features.npy and %s.labels.npy%n", out, out);
		} catch (IOException e) {
			System.err.println("Failed to export dataset: " + e.getMessage());
			System.exit(2);
		}
	}

	/**
	 * Handles {@code record-to-pgn}.
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
	 * Handles {@code puzzles-to-pgn}.
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
	 * Handles {@code stack-to-dataset}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runStackToDataset(Argv a) {
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

		try {
			chess.io.RecordDatasetExporter.exportStack(in, out);
			System.out.printf("Wrote %s.features.npy and %s.labels.npy%n", out, out);
		} catch (IOException e) {
			System.err.println("Failed to export stack dataset: " + e.getMessage());
			System.exit(2);
		}
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
		processRecordInputs(inputFiles, writer, stats, request, filters);
		closeRecordWriterOrExit(writer, request.verbose);

		System.out.printf(
				"records: wrote %d/%d records (skipped %d invalid) to %s%n",
				stats.matched,
				stats.seen,
				stats.invalid,
				writer.describeOutputs());
	}

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

	private static List<Path> collectRecordInputsOrExit(List<String> inputs, boolean recursive, boolean verbose) {
		try {
			return collectRecordInputs(inputs, recursive);
		} catch (IOException ex) {
			exitWithError("records: failed to read inputs: " + ex.getMessage(), ex, verbose);
			return List.of();
		}
	}

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

	private static void processRecordInputs(
			List<Path> inputFiles,
			RecordBatchWriter writer,
			RecordStats stats,
			RecordsRequest request,
			RecordsFilters filters) {
		for (Path input : inputFiles) {
			try {
				streamRecordJson(input, objJson -> handleRecordJson(objJson, writer, stats, request, filters));
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

	private static final class RecordsRequest {
		private final boolean verbose;
		private final String filterDsl;
		private final int maxRecords;
		private final boolean puzzles;
		private final boolean nonpuzzles;
		private final boolean recursive;
		private final Path output;
		private final List<String> inputs;

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

	private static final class RecordsRequestOptions {
		private final boolean verbose;
		private final String filterDsl;
		private final int maxRecords;
		private final boolean puzzles;
		private final boolean nonpuzzles;
		private final boolean recursive;

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

	private static final class RecordsFilters {
		private final Filter dslFilter;
		private final Filter puzzleVerify;

		private RecordsFilters(Filter dslFilter, Filter puzzleVerify) {
			this.dslFilter = dslFilter;
			this.puzzleVerify = puzzleVerify;
		}
	}

	private static final class RecordStats {
		private long seen;
		private long matched;
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

	private static void exitWithError(String message) {
		System.err.println(message);
		System.exit(2);
		throw new IllegalStateException(message);
	}

	private static void exitWithError(String message, Throwable cause, boolean verbose) {
		System.err.println(message);
		if (verbose && cause != null) {
			cause.printStackTrace(System.err);
		}
		System.exit(2);
		throw new IllegalStateException(message, cause);
	}
}
