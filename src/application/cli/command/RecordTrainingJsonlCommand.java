package application.cli.command;

import static application.cli.Constants.OPT_FILTER;
import static application.cli.Constants.OPT_FILTER_SHORT;
import static application.cli.Constants.OPT_INCLUDE_ENGINE_METADATA;
import static application.cli.Constants.OPT_INPUT;
import static application.cli.Constants.OPT_INPUT_SHORT;
import static application.cli.Constants.OPT_MAX_RECORDS;
import static application.cli.Constants.OPT_OUTPUT;
import static application.cli.Constants.OPT_OUTPUT_SHORT;
import static application.cli.Constants.OPT_RECURSIVE;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;
import static application.cli.PathOps.deriveOutputPath;
import static application.cli.PathOps.ensureParentDir;
import static application.cli.RecordIO.streamRecordJson;
import static application.cli.command.RecordCommandSupport.exitWithError;
import static application.cli.command.RecordCommandSupport.finishProgress;

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
import java.util.Set;

import application.Config;
import application.console.Bar;
import chess.core.Move;
import chess.struct.Record;
import chess.uci.Chances;
import chess.uci.Evaluation;
import chess.uci.Filter;
import chess.uci.Filter.FilterDSL;
import chess.uci.Output;
import utility.Argv;

/**
 * Implements the record training-jsonl exporter.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class RecordTrainingJsonlCommand {

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
	 * Shared ext training jsonl constant.
	 */
	private static final String EXT_TRAINING_JSONL = ".training.jsonl";
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
	private RecordTrainingJsonlCommand() {
		// utility
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
	static void run(Argv a) {
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
	 * Parses a filter expression or exits with command diagnostics.
	 *
	 * @param command command label
	 * @param option option name
	 * @param filterDsl filter expression
	 * @param verbose whether verbose diagnostics are enabled
	 * @return parsed filter or {@code null}
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
	 * @return never returns normally
	 */
	private static IllegalStateException unreachable() {
		return new IllegalStateException("unreachable");
	}

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
	 * @param context context value
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
							.filter(RecordTrainingJsonlCommand::hasRecordExtension)
						.forEach(files::add);
			}
			return files;
		}
		try (java.util.stream.Stream<Path> stream = Files.list(dir)) {
				stream.filter(Files::isRegularFile)
						.filter(RecordTrainingJsonlCommand::hasRecordExtension)
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

}
