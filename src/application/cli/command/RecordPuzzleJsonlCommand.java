package application.cli.command;

import static application.cli.Constants.OPT_FILTER;
import static application.cli.Constants.OPT_FILTER_SHORT;
import static application.cli.Constants.OPT_INPUT;
import static application.cli.Constants.OPT_INPUT_SHORT;
import static application.cli.Constants.OPT_NONPUZZLES;
import static application.cli.Constants.OPT_OUTPUT;
import static application.cli.Constants.OPT_OUTPUT_SHORT;
import static application.cli.Constants.OPT_PUZZLES;
import static application.cli.Constants.OPT_ROW_HASHES;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;
import static application.cli.Constants.OPT_WEIGHTS;
import static application.cli.PathOps.deriveOutputPath;
import static application.cli.PathOps.ensureParentDir;
import static application.cli.RecordIO.streamRecordJson;
import static application.cli.command.RecordCommandSupport.byteProgress;
import static application.cli.command.RecordCommandSupport.exitWithError;
import static application.cli.command.RecordCommandSupport.fileProgressBar;
import static application.cli.command.RecordCommandSupport.finishProgress;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import application.Config;
import application.console.Bar;
import chess.core.Move;
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

/**
 * Implements the record puzzle-jsonl exporter.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class RecordPuzzleJsonlCommand {

	/**
	 * Shared puzzle jsonl format name constant.
	 */
	private static final String PUZZLE_JSONL_FORMAT_NAME = "puzzle-jsonl";
	/**
	 * Shared puzzle-jsonl command label constant.
	 */
	private static final String COMMAND_RECORD_EXPORT_PUZZLE_JSONL = "record export puzzle-jsonl";
	/**
	 * Shared ext puzzle jsonl constant.
	 */
	private static final String EXT_PUZZLE_JSONL = ".puzzle.jsonl";

	/**
	 * Utility class; prevent instantiation.
	 */
	private RecordPuzzleJsonlCommand() {
		// utility
	}

	/**
	 * Handles {@code record export puzzle-jsonl}.
	 *
	 * @param a argument parser for the subcommand
	 */
	static void run(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		boolean puzzles = a.flag(OPT_PUZZLES);
		boolean nonpuzzles = a.flag(OPT_NONPUZZLES);
		boolean rowHashes = a.flag(OPT_ROW_HASHES);
		String filterDsl = a.string(OPT_FILTER, OPT_FILTER_SHORT);
		Path in = a.pathRequired(OPT_INPUT, OPT_INPUT_SHORT);
		Path weights = a.path(OPT_WEIGHTS);
		Path out = a.path(OPT_OUTPUT, OPT_OUTPUT_SHORT);
		a.ensureConsumed();

		validatePuzzleJsonlArguments(puzzles, nonpuzzles, weights);
		Filter filter = parseFilterOrExit(COMMAND_RECORD_EXPORT_PUZZLE_JSONL, OPT_FILTER, filterDsl, verbose);
		Filter puzzleVerify = resolvePuzzleJsonlVerify(puzzles, nonpuzzles);
		Path output = defaultOutputPath(in, out, EXT_PUZZLE_JSONL);
		Path rowHashPath = rowHashes ? DatasetManifestSupport.rowHashPathFor(output) : null;
		Lc0Artifacts lc0 = loadLc0Artifacts(weights, verbose, COMMAND_RECORD_EXPORT_PUZZLE_JSONL);
		PuzzleJsonlExportContext context =
				new PuzzleJsonlExportContext(filter, puzzleVerify, puzzles, nonpuzzles, verbose, lc0, rowHashPath);
		PuzzleJsonlExportStats stats = new PuzzleJsonlExportStats();
		exportPuzzleJsonl(in, output, context, stats);
		writePuzzleJsonlManifest(in, output, weights, context, stats);
	}

	/**
	 * Writes puzzle-jsonl manifest metadata after a successful export.
	 *
	 * @param input input record file
	 * @param output output JSONL file
	 * @param weights LC0 weights file
	 * @param context export context
	 * @param stats export counters
	 */
	private static void writePuzzleJsonlManifest(
			Path input,
			Path output,
			Path weights,
			PuzzleJsonlExportContext context,
			PuzzleJsonlExportStats stats) {
		DatasetManifestSupport.write(
				"record.export.puzzle-jsonl",
				input,
				List.of(output),
				weights,
				Path.of(output + ".manifest.json"),
				builder -> {
					builder.metadata("label_policy", "puzzle-jsonl-lc0-policy-v1")
							.metadata("label_definition",
									"critical_move=engine PV1 legal move; lc0_policy_pct/lc0_wdl from supplied LC0 weights")
							.metadata("selector", puzzleJsonlSelector(context))
							.metadataNumber("records_seen", stats.seen)
							.metadataNumber("rows_written", stats.written)
							.metadataNumber("skipped_invalid", stats.invalid)
							.metadataNumber("skipped_filtered", stats.skipped);
					DatasetManifestSupport.addRowHashSidecar(builder, context.rowHashPath);
					if (context.filter != null) {
						builder.metadata("row_filter", FilterDSL.toString(context.filter));
					}
					if (context.puzzleVerify != null) {
						builder.metadata("puzzle_filter", FilterDSL.toString(context.puzzleVerify));
					}
				});
	}

	/**
	 * Returns the row selector encoded by puzzle-jsonl flags.
	 *
	 * @param context export context
	 * @return selector label
	 */
	private static String puzzleJsonlSelector(PuzzleJsonlExportContext context) {
		if (context.puzzlesOnly) {
			return "puzzles";
		}
		if (context.nonpuzzlesOnly) {
			return "nonpuzzles";
		}
		return "all";
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
	 * Returns the explicit output path or a derived path with the requested suffix.
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
	 * Marks code paths after {@code exitWithError(...)} as unreachable.
	 *
	 * @return never returns normally
	 */
	private static IllegalStateException unreachable() {
		return new IllegalStateException("unreachable");
	}

	/**
	 * Validates puzzle-jsonl selector and weight arguments.
	 *
	 * @param puzzles true to export only puzzles
	 * @param nonpuzzles true to export only non-puzzles
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
	 * Loads LC0 network artifacts required for policy labels.
	 *
	 * @param weights weights path
	 * @param verbose true to include verbose failure diagnostics
	 * @param command command name for diagnostics
	 * @return loaded artifacts
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
	 * Writes puzzle-jsonl rows from one record input file.
	 *
	 * @param input input record file
	 * @param output output JSONL file
	 * @param context export context
	 * @param stats mutable export stats
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
				BufferedWriter writer = Files.newBufferedWriter(output);
				BufferedWriter rowHashWriter = context.rowHashPath == null
						? null
						: DatasetManifestSupport.openRowHashWriter(context.rowHashPath)) {
			PuzzleJsonlWriteContext writeContext =
					new PuzzleJsonlWriteContext(context, network, writer, bar,
							DatasetManifestSupport.rowHashSink(rowHashWriter));
			streamRecordJson(input,
					objJson -> writePuzzleJsonlRecord(objJson, writeContext, stats),
					byteProgress(bar));
		} catch (IOException | UncheckedIOException ex) {
			deleteQuietly(context.rowHashPath);
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
	 * Converts one record JSON object into an optional puzzle-jsonl row.
	 *
	 * @param objJson record JSON object
	 * @param context write context
	 * @param stats mutable export stats
	 */
	private static void writePuzzleJsonlRecord(
			String objJson,
			PuzzleJsonlWriteContext context,
			PuzzleJsonlExportStats stats) {
		stats.seen++;
		Record rec = parseRecord(objJson, context.options.verbose);
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
			if (context.rowHashSink != null) {
				context.rowHashSink.accept(objJson);
			}
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		stats.written++;
		updatePuzzleJsonlProgress(context.bar, stats);
	}
	/**
	 * Applies puzzle-jsonl filters to one record.
	 *
	 * @param objJson raw record JSON object
	 * @param rec parsed record
	 * @param options export options
	 * @return true when the record should be exported
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
		boolean isPuzzle = RecordCommands.isPuzzleRecordJson(objJson, rec, options.puzzleVerify);
		if (options.puzzlesOnly) {
			return isPuzzle;
		}
		return !isPuzzle;
	}
	/**
	 * Updates the puzzle-jsonl progress bar with periodic counters.
	 *
	 * @param bar progress bar, or null
	 * @param stats export stats
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
		 * Optional row-hash sidecar path.
		 */
		private final Path rowHashPath;

		/**
		 * Creates a new puzzle-jsonl export context.
		 *
		 * @param filter optional row filter
		 * @param puzzleVerify optional puzzle verifier
		 * @param puzzlesOnly whether only puzzles should be written
		 * @param nonpuzzlesOnly whether only non-puzzles should be written
		 * @param verbose whether verbose diagnostics are enabled
		 * @param lc0 loaded LC0 resources
		 * @param rowHashPath optional row-hash sidecar path
		 */
		private PuzzleJsonlExportContext(
				Filter filter,
				Filter puzzleVerify,
				boolean puzzlesOnly,
				boolean nonpuzzlesOnly,
				boolean verbose,
				Lc0Artifacts lc0,
				Path rowHashPath) {
			this.filter = filter;
			this.puzzleVerify = puzzleVerify;
			this.puzzlesOnly = puzzlesOnly;
			this.nonpuzzlesOnly = nonpuzzlesOnly;
			this.verbose = verbose;
			this.lc0 = lc0;
			this.rowHashPath = rowHashPath;
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
		 * Optional sink receiving raw JSON for every emitted row.
		 */
		private final Consumer<String> rowHashSink;

		/**
		 * Creates a new write context.
		 *
		 * @param options shared export options
		 * @param network loaded network
		 * @param writer destination writer
		 * @param bar optional progress bar
		 * @param rowHashSink optional row-hash sink
		 */
		private PuzzleJsonlWriteContext(
				PuzzleJsonlExportContext options,
				Network network,
				BufferedWriter writer,
				Bar bar,
				Consumer<String> rowHashSink) {
			this.options = options;
			this.network = network;
			this.writer = writer;
			this.bar = bar;
			this.rowHashSink = rowHashSink;
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
	 * Parses one training record, returning {@code null} when it is invalid.
	 *
	 * @param objJson raw record JSON
	 * @param verbose whether verbose diagnostics are enabled
	 * @return parsed record or {@code null}
	 */
	private static Record parseRecord(String objJson, boolean verbose) {
		try {
			return Record.fromJson(objJson);
		} catch (Exception ex) {
			if (verbose) {
				System.err.println(COMMAND_RECORD_EXPORT_PUZZLE_JSONL + ": skipped invalid record: " + ex.getMessage());
			}
			return null;
		}
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

}
