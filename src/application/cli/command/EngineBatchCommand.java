package application.cli.command;

import static application.cli.Constants.CMD_ANALYZE_BATCH;
import static application.cli.Constants.CMD_BESTMOVE_BATCH;
import static application.cli.Constants.CMD_COMPARE;
import static application.cli.Constants.OPT_OUTPUT;
import static application.cli.Constants.OPT_OUTPUT_SHORT;
import static application.cli.Constants.OPT_STDIN;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;
import static application.cli.EngineOps.analysePositionOrExit;
import static application.cli.EngineOps.configureEngine;
import static application.cli.EngineOps.resolveWdlFlag;
import static application.cli.Format.formatBound;
import static application.cli.Format.formatChances;
import static application.cli.Format.formatEvaluation;
import static application.cli.Format.formatPvMovesSan;
import static application.cli.Format.safeSan;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import application.cli.command.CommandSupport.OutputMode;
import application.console.Bar;
import chess.core.Move;
import chess.core.Position;
import chess.uci.Analysis;
import chess.uci.Engine;
import chess.uci.Evaluation;
import chess.uci.Output;
import chess.uci.Protocol;
import utility.Argv;

/**
 * Implements high-throughput external-engine batch and comparison commands.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class EngineBatchCommand {

	/**
	 * Left protocol option for {@code engine compare}.
	 */
	private static final String OPT_LEFT_PROTOCOL = "--left-protocol";

	/**
	 * Right protocol option for {@code engine compare}.
	 */
	private static final String OPT_RIGHT_PROTOCOL = "--right-protocol";

	/**
	 * Alternate left protocol option.
	 */
	private static final String OPT_PROTOCOL_A = "--protocol-a";

	/**
	 * Alternate right protocol option.
	 */
	private static final String OPT_PROTOCOL_B = "--protocol-b";

	/**
	 * Utility class; prevent instantiation.
	 */
	private EngineBatchCommand() {
		// utility
	}

	/**
	 * Handles {@code engine analyze-batch}.
	 *
	 * @param a argument parser
	 */
	public static void runAnalyzeBatch(Argv a) {
		String cmd = "engine " + CMD_ANALYZE_BATCH;
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		boolean stdin = a.flag(OPT_STDIN);
		Path output = a.path(OPT_OUTPUT, OPT_OUTPUT_SHORT);
		OutputMode outputMode = batchOutputMode(a, cmd);
		EngineSupport.UciOptions opts = EngineSupport.parseUciOptions(a, cmd, false);
		List<String> fens = resolveBatchFenInputs(cmd, opts, stdin, verbose);
		Protocol protocol = EngineSupport.loadProtocolOrExit(opts.protocolPath(), verbose);
		Optional<Boolean> wdlFlag = resolveWdlFlag(opts.wdl(), opts.noWdl());
		List<String> rows = new ArrayList<>();

		try (Engine engine = new Engine(protocol)) {
			configureEngine(cmd, engine, opts.threads(), opts.hash(), opts.multipv(), wdlFlag);
			String engineLabel = engineLabel(protocol);
			Bar bar = positionProgressBar(fens, cmd);
			try {
				for (int i = 0; i < fens.size(); i++) {
					rows.add(analyzeBatchRow(engine, engineLabel, fens.get(i), i + 1, opts, cmd, verbose));
					CommandSupport.step(bar);
				}
			} finally {
				CommandSupport.finish(bar);
			}
		} catch (CommandFailure failure) {
			throw failure;
		} catch (Exception ex) {
			throw new CommandFailure(cmd + ": failed to initialize engine: " + ex.getMessage(), ex, 2, verbose);
		}
		writeRows(rows, outputMode, output, cmd, verbose);
	}

	/**
	 * Handles {@code engine bestmove-batch}.
	 *
	 * @param a argument parser
	 */
	public static void runBestMoveBatch(Argv a) {
		String cmd = "engine " + CMD_BESTMOVE_BATCH;
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		boolean stdin = a.flag(OPT_STDIN);
		Path output = a.path(OPT_OUTPUT, OPT_OUTPUT_SHORT);
		OutputMode outputMode = batchOutputMode(a, cmd);
		EngineSupport.UciOptions opts = EngineSupport.parseUciOptions(a, cmd, false);
		List<String> fens = resolveBatchFenInputs(cmd, opts, stdin, verbose);
		Protocol protocol = EngineSupport.loadProtocolOrExit(opts.protocolPath(), verbose);
		Optional<Boolean> wdlFlag = resolveWdlFlag(opts.wdl(), opts.noWdl());
		List<String> rows = new ArrayList<>();

		try (Engine engine = new Engine(protocol)) {
			configureEngine(cmd, engine, opts.threads(), opts.hash(), opts.multipv(), wdlFlag);
			String engineLabel = engineLabel(protocol);
			Bar bar = positionProgressBar(fens, cmd);
			try {
				for (int i = 0; i < fens.size(); i++) {
					rows.add(bestMoveBatchRow(engine, engineLabel, fens.get(i), i + 1, opts, cmd, verbose));
					CommandSupport.step(bar);
				}
			} finally {
				CommandSupport.finish(bar);
			}
		} catch (CommandFailure failure) {
			throw failure;
		} catch (Exception ex) {
			throw new CommandFailure(cmd + ": failed to initialize engine: " + ex.getMessage(), ex, 2, verbose);
		}
		writeRows(rows, outputMode, output, cmd, verbose);
	}

	/**
	 * Handles {@code engine compare}.
	 *
	 * @param a argument parser
	 */
	public static void runCompare(Argv a) {
		String cmd = "engine " + CMD_COMPARE;
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		boolean stdin = a.flag(OPT_STDIN);
		String leftProtocolPath = a.string(OPT_LEFT_PROTOCOL, OPT_PROTOCOL_A);
		String rightProtocolPath = a.string(OPT_RIGHT_PROTOCOL, OPT_PROTOCOL_B);
		Path output = a.path(OPT_OUTPUT, OPT_OUTPUT_SHORT);
		OutputMode outputMode = CommandSupport.resolveOutputMode(a, cmd);
		EngineSupport.UciOptions opts = EngineSupport.parseUciOptions(a, cmd, false);
		List<String> fens = resolveBatchFenInputs(cmd, opts, stdin, verbose);
		String defaultProtocolPath = opts.protocolPath();
		Protocol leftProtocol = EngineSupport.loadProtocolOrExit(
				CommandSupport.optional(leftProtocolPath, defaultProtocolPath), verbose);
		Protocol rightProtocol = EngineSupport.loadProtocolOrExit(
				CommandSupport.optional(rightProtocolPath, defaultProtocolPath), verbose);
		Optional<Boolean> wdlFlag = resolveWdlFlag(opts.wdl(), opts.noWdl());
		List<CompareRow> rows = new ArrayList<>();

		try (Engine left = new Engine(leftProtocol); Engine right = new Engine(rightProtocol)) {
			configureEngine(cmd, left, opts.threads(), opts.hash(), opts.multipv(), wdlFlag);
			configureEngine(cmd, right, opts.threads(), opts.hash(), opts.multipv(), wdlFlag);
			Bar bar = positionProgressBar(fens, cmd);
			try {
				for (int i = 0; i < fens.size(); i++) {
					rows.add(compareRow(left, right, leftProtocol, rightProtocol, fens.get(i), i + 1, opts,
							cmd, verbose));
					CommandSupport.step(bar);
				}
			} finally {
				CommandSupport.finish(bar);
			}
		} catch (CommandFailure failure) {
			throw failure;
		} catch (Exception ex) {
			throw new CommandFailure(cmd + ": failed to initialize engines: " + ex.getMessage(), ex, 2, verbose);
		}
		writeCompareRows(rows, outputMode, output, cmd, verbose);
	}

	/**
	 * Resolves batch commands to JSONL unless JSON is explicitly requested.
	 *
	 * @param a argument parser
	 * @param cmd command label
	 * @return output mode
	 */
	private static OutputMode batchOutputMode(Argv a, String cmd) {
		OutputMode outputMode = CommandSupport.resolveOutputMode(a, cmd);
		return outputMode == OutputMode.TEXT ? OutputMode.JSONL : outputMode;
	}

	/**
	 * Resolves FEN rows from input, stdin, or a single selector.
	 *
	 * @param cmd command label
	 * @param opts parsed engine options
	 * @param stdin whether stdin should be read
	 * @param verbose whether stack traces should be printed
	 * @return FEN rows
	 */
	private static List<String> resolveBatchFenInputs(
			String cmd,
			EngineSupport.UciOptions opts,
			boolean stdin,
			boolean verbose) {
		if (stdin) {
			if (opts.input() != null || opts.fen() != null) {
				throw new CommandFailure(cmd + ": provide either " + OPT_STDIN
						+ ", --input, or one position selector, not more than one", 2);
			}
			return CommandSupport.readStdinLines(cmd, verbose);
		}
		return CommandSupport.resolveFenInputs(cmd, opts.input(), opts.fen());
	}

	/**
	 * Builds one analyze-batch JSON row.
	 *
	 * @param engine engine instance
	 * @param engineLabel display label
	 * @param fen raw FEN
	 * @param index one-based row index
	 * @param opts engine options
	 * @param cmd command label
	 * @param verbose whether stack traces should be printed
	 * @return JSON object row
	 */
	private static String analyzeBatchRow(
			Engine engine,
			String engineLabel,
			String fen,
			int index,
			EngineSupport.UciOptions opts,
			String cmd,
			boolean verbose) {
		Position position = parseBatchPosition(fen);
		if (position == null) {
			return errorRow(index, fen, "invalid FEN");
		}
		Analysis analysis = analysePositionOrExit(engine, position, opts.nodesCap(), opts.durationMillis(), cmd,
				verbose);
		StringBuilder sb = new StringBuilder(256);
		sb.append("{\"ok\":true,\"index\":").append(index)
				.append(",\"fen\":").append(CommandSupport.jsonString(position.toString()))
				.append(",\"engine\":").append(CommandSupport.jsonString(engineLabel))
				.append(",\"pv\":").append(analysisJson(position, analysis))
				.append('}');
		return sb.toString();
	}

	/**
	 * Builds one bestmove-batch JSON row.
	 *
	 * @param engine engine instance
	 * @param engineLabel display label
	 * @param fen raw FEN
	 * @param index one-based row index
	 * @param opts engine options
	 * @param cmd command label
	 * @param verbose whether stack traces should be printed
	 * @return JSON object row
	 */
	private static String bestMoveBatchRow(
			Engine engine,
			String engineLabel,
			String fen,
			int index,
			EngineSupport.UciOptions opts,
			String cmd,
			boolean verbose) {
		Position position = parseBatchPosition(fen);
		if (position == null) {
			return errorRow(index, fen, "invalid FEN");
		}
		Analysis analysis = analysePositionOrExit(engine, position, opts.nodesCap(), opts.durationMillis(), cmd,
				verbose);
		Output bestOutput = analysis.getBestOutput();
		short best = analysis.getBestMove();
		String uci = best == Move.NO_MOVE ? "0000" : Move.toString(best);
		String san = best == Move.NO_MOVE ? "-" : safeSan(position, best);
		StringBuilder sb = new StringBuilder(192);
		sb.append("{\"ok\":true,\"index\":").append(index)
				.append(",\"fen\":").append(CommandSupport.jsonString(position.toString()))
				.append(",\"engine\":").append(CommandSupport.jsonString(engineLabel))
				.append(",\"uci\":").append(CommandSupport.jsonString(uci))
				.append(",\"san\":").append(CommandSupport.jsonString(san))
				.append(",\"eval\":").append(CommandSupport.jsonString(formatEvaluation(outputEvaluation(bestOutput))))
				.append(",\"depth\":").append(bestOutput == null ? 0 : bestOutput.getDepth())
				.append(",\"nodes\":").append(bestOutput == null ? 0L : bestOutput.getNodes())
				.append(",\"time_ms\":").append(bestOutput == null ? 0L : bestOutput.getTime())
				.append('}');
		return sb.toString();
	}

	/**
	 * Builds one compare row.
	 *
	 * @param left left engine
	 * @param right right engine
	 * @param leftProtocol left protocol metadata
	 * @param rightProtocol right protocol metadata
	 * @param fen raw FEN
	 * @param index one-based row index
	 * @param opts engine options
	 * @param cmd command label
	 * @param verbose whether stack traces should be printed
	 * @return comparison row
	 */
	private static CompareRow compareRow(
			Engine left,
			Engine right,
			Protocol leftProtocol,
			Protocol rightProtocol,
			String fen,
			int index,
			EngineSupport.UciOptions opts,
			String cmd,
			boolean verbose) {
		Position position = parseBatchPosition(fen);
		if (position == null) {
			return CompareRow.error(index, fen, "invalid FEN");
		}
		Analysis leftAnalysis = analysePositionOrExit(left, position, opts.nodesCap(), opts.durationMillis(), cmd,
				verbose);
		Analysis rightAnalysis = analysePositionOrExit(right, position, opts.nodesCap(), opts.durationMillis(), cmd,
				verbose);
		Output leftOutput = leftAnalysis.getBestOutput();
		Output rightOutput = rightAnalysis.getBestOutput();
		short leftMove = leftAnalysis.getBestMove();
		short rightMove = rightAnalysis.getBestMove();
		String leftUci = leftMove == Move.NO_MOVE ? "0000" : Move.toString(leftMove);
		String rightUci = rightMove == Move.NO_MOVE ? "0000" : Move.toString(rightMove);
		return new CompareRow(
				true,
				index,
				position.toString(),
				null,
				engineLabel(leftProtocol),
				engineLabel(rightProtocol),
				leftUci,
				leftMove == Move.NO_MOVE ? "-" : safeSan(position, leftMove),
				rightUci,
				rightMove == Move.NO_MOVE ? "-" : safeSan(position, rightMove),
				leftUci.equals(rightUci),
				formatEvaluation(outputEvaluation(leftOutput)),
				formatEvaluation(outputEvaluation(rightOutput)),
				evaluationDelta(leftOutput, rightOutput),
				leftOutput == null ? 0 : leftOutput.getDepth(),
				rightOutput == null ? 0 : rightOutput.getDepth(),
				leftOutput == null ? 0L : leftOutput.getNodes(),
				rightOutput == null ? 0L : rightOutput.getNodes());
	}

	/**
	 * Builds a JSON array for all PVs.
	 *
	 * @param position analyzed position
	 * @param analysis analysis result
	 * @return JSON array string
	 */
	private static String analysisJson(Position position, Analysis analysis) {
		if (analysis == null || analysis.isEmpty()) {
			return "[]";
		}
		List<String> rows = new ArrayList<>();
		int pivots = Math.max(1, analysis.getPivots());
		for (int pv = 1; pv <= pivots; pv++) {
			Output output = analysis.getBestOutput(pv);
			if (output != null) {
				rows.add(pvJson(position, analysis, output, pv));
			}
		}
		return "[" + String.join(",", rows) + "]";
	}

	/**
	 * Builds one PV JSON object.
	 *
	 * @param position analyzed position
	 * @param analysis analysis result
	 * @param output PV output
	 * @param pv one-based PV index
	 * @return JSON object string
	 */
	private static String pvJson(Position position, Analysis analysis, Output output, int pv) {
		short best = analysis.getBestMove(pv);
		String uci = best == Move.NO_MOVE ? "0000" : Move.toString(best);
		String san = best == Move.NO_MOVE ? "-" : safeSan(position, best);
		return "{\"multipv\":" + pv
				+ ",\"uci\":" + CommandSupport.jsonString(uci)
				+ ",\"san\":" + CommandSupport.jsonString(san)
				+ ",\"eval\":" + CommandSupport.jsonString(formatEvaluation(output.getEvaluation()))
				+ ",\"depth\":" + output.getDepth()
				+ ",\"seldepth\":" + output.getSelectiveDepth()
				+ ",\"nodes\":" + output.getNodes()
				+ ",\"nps\":" + output.getNodesPerSecond()
				+ ",\"time_ms\":" + output.getTime()
				+ ",\"bound\":" + CommandSupport.jsonString(formatBound(output.getBound()))
				+ ",\"wdl\":" + CommandSupport.jsonString(formatChances(output.getChances()))
				+ ",\"line_uci\":" + moveArrayJson(output.getMoves())
				+ ",\"line_san\":" + CommandSupport.jsonString(formatPvMovesSan(position, output.getMoves()))
				+ "}";
	}

	/**
	 * Parses a FEN for batch mode without aborting the whole batch.
	 *
	 * @param fen raw FEN
	 * @return parsed position or {@code null}
	 */
	private static Position parseBatchPosition(String fen) {
		try {
			return new Position(fen);
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}

	/**
	 * Builds a JSON error row.
	 *
	 * @param index one-based index
	 * @param fen raw FEN
	 * @param message error message
	 * @return JSON object string
	 */
	private static String errorRow(int index, String fen, String message) {
		return "{\"ok\":false,\"index\":" + index
				+ ",\"fen\":" + CommandSupport.jsonString(fen)
				+ ",\"error\":" + CommandSupport.jsonString(message)
				+ "}";
	}

	/**
	 * Writes row-oriented machine output.
	 *
	 * @param rows JSON object rows
	 * @param outputMode output mode
	 * @param output optional output file
	 * @param cmd command label
	 * @param verbose whether stack traces should be printed
	 */
	private static void writeRows(
			List<String> rows,
			OutputMode outputMode,
			Path output,
			String cmd,
			boolean verbose) {
		String body = outputMode == OutputMode.JSON
				? "[" + String.join(",", rows) + "]" + System.lineSeparator()
				: String.join(System.lineSeparator(), rows) + System.lineSeparator();
		writeOrPrint(body, output, cmd, verbose);
	}

	/**
	 * Writes compare output.
	 *
	 * @param rows compare rows
	 * @param outputMode output mode
	 * @param output optional output file
	 * @param cmd command label
	 * @param verbose whether stack traces should be printed
	 */
	private static void writeCompareRows(
			List<CompareRow> rows,
			OutputMode outputMode,
			Path output,
			String cmd,
			boolean verbose) {
		String body;
		if (outputMode == OutputMode.JSON) {
			body = compareJson(rows) + System.lineSeparator();
		} else if (outputMode == OutputMode.JSONL) {
			body = String.join(System.lineSeparator(), rows.stream().map(CompareRow::toJson).toList())
					+ System.lineSeparator();
		} else {
			body = compareText(rows);
		}
		writeOrPrint(body, output, cmd, verbose);
	}

	/**
	 * Writes text to stdout or to a file.
	 *
	 * @param body text body
	 * @param output optional file
	 * @param cmd command label
	 * @param verbose whether stack traces should be printed
	 */
	private static void writeOrPrint(String body, Path output, String cmd, boolean verbose) {
		if (output == null) {
			System.out.print(body);
			return;
		}
		try {
			Path parent = output.toAbsolutePath().getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			Files.writeString(output, body, StandardCharsets.UTF_8);
		} catch (IOException ex) {
			throw new CommandFailure(cmd + ": failed to write output: " + ex.getMessage(), ex, 2, verbose);
		}
	}

	/**
	 * Renders compare output as a human-readable table.
	 *
	 * @param rows compare rows
	 * @return text output
	 */
	private static String compareText(List<CompareRow> rows) {
		StringBuilder sb = new StringBuilder();
		sb.append(String.format(Locale.ROOT, "%5s  %-5s  %-8s  %-8s  %-8s%n",
				"Index", "Same", "Left", "Right", "Delta"));
		for (CompareRow row : rows) {
			if (!row.ok()) {
				sb.append(String.format(Locale.ROOT, "%5d  %-5s  %-8s  %-8s  %s%n",
						row.index(), "error", "-", "-", row.error()));
			} else {
				sb.append(String.format(Locale.ROOT, "%5d  %-5s  %-8s  %-8s  %s%n",
						row.index(), row.sameMove(), row.leftUci(), row.rightUci(), deltaText(row.evalDeltaCp())));
			}
		}
		CompareSummary summary = summarize(rows);
		sb.append(String.format(Locale.ROOT, "Summary: rows=%d same=%d different=%d errors=%d%n",
				summary.rows(), summary.same(), summary.different(), summary.errors()));
		return sb.toString();
	}

	/**
	 * Renders compare output as one JSON object with rows and summary.
	 *
	 * @param rows compare rows
	 * @return JSON object
	 */
	private static String compareJson(List<CompareRow> rows) {
		CompareSummary summary = summarize(rows);
		return "{\"rows\":[" + String.join(",", rows.stream().map(CompareRow::toJson).toList())
				+ "],\"summary\":" + summary.toJson() + "}";
	}

	/**
	 * Summarizes compare rows.
	 *
	 * @param rows compare rows
	 * @return summary
	 */
	private static CompareSummary summarize(List<CompareRow> rows) {
		int same = 0;
		int different = 0;
		int errors = 0;
		for (CompareRow row : rows) {
			if (!row.ok()) {
				errors++;
			} else if (row.sameMove()) {
				same++;
			} else {
				different++;
			}
		}
		return new CompareSummary(rows.size(), same, different, errors);
	}

	/**
	 * Formats a nullable centipawn delta.
	 *
	 * @param delta nullable delta
	 * @return text
	 */
	private static String deltaText(Integer delta) {
		return delta == null ? "-" : String.format(Locale.ROOT, "%+d", delta);
	}

	/**
	 * Returns an engine display label.
	 *
	 * @param protocol protocol metadata
	 * @return label
	 */
	private static String engineLabel(Protocol protocol) {
		return protocol.getName() != null ? protocol.getName() : protocol.getPath();
	}

	/**
	 * Returns a nullable output evaluation.
	 *
	 * @param output output row
	 * @return evaluation or {@code null}
	 */
	private static Evaluation outputEvaluation(Output output) {
		return output == null ? null : output.getEvaluation();
	}

	/**
	 * Computes a centipawn delta when both outputs are centipawn scores.
	 *
	 * @param left left output
	 * @param right right output
	 * @return left minus right, or {@code null}
	 */
	private static Integer evaluationDelta(Output left, Output right) {
		Integer leftCp = evaluationCp(left);
		Integer rightCp = evaluationCp(right);
		return leftCp == null || rightCp == null ? null : leftCp - rightCp;
	}

	/**
	 * Extracts a centipawn score if available.
	 *
	 * @param output engine output
	 * @return centipawn score or {@code null}
	 */
	private static Integer evaluationCp(Output output) {
		Evaluation evaluation = outputEvaluation(output);
		if (evaluation == null || !evaluation.isValid() || evaluation.isMate()) {
			return null;
		}
		return evaluation.getValue();
	}

	/**
	 * Builds a JSON string array of UCI moves.
	 *
	 * @param moves encoded moves
	 * @return JSON array
	 */
	private static String moveArrayJson(short[] moves) {
		if (moves == null || moves.length == 0) {
			return "[]";
		}
		List<String> values = new ArrayList<>();
		for (short move : moves) {
			if (move != Move.NO_MOVE) {
				values.add(CommandSupport.jsonString(Move.toString(move)));
			}
		}
		return "[" + String.join(",", values) + "]";
	}

	/**
	 * Handles position progress bar.
	 *
	 * @param fens FEN rows
	 * @param label bar label
	 * @return progress bar or {@code null}
	 */
	private static Bar positionProgressBar(List<String> fens, String label) {
		return fens != null && fens.size() > 1 ? new Bar(fens.size(), label, false, System.err) : null;
	}

	/**
	 * One compare row.
	 */
	private record CompareRow(
			boolean ok,
			int index,
			String fen,
			String error,
			String leftEngine,
			String rightEngine,
			String leftUci,
			String leftSan,
			String rightUci,
			String rightSan,
			boolean sameMove,
			String leftEval,
			String rightEval,
			Integer evalDeltaCp,
			int leftDepth,
			int rightDepth,
			long leftNodes,
			long rightNodes) {

		/**
		 * Builds an error row.
		 *
		 * @param index row index
		 * @param fen raw FEN
		 * @param message error message
		 * @return row
		 */
		static CompareRow error(int index, String fen, String message) {
			return new CompareRow(false, index, fen, message, null, null, null, null, null, null,
					false, null, null, null, 0, 0, 0L, 0L);
		}

		/**
		 * Converts the row to JSON.
		 *
		 * @return JSON object
		 */
		String toJson() {
			if (!ok) {
				return errorRow(index, fen, error);
			}
			return "{\"ok\":true,\"index\":" + index
					+ ",\"fen\":" + CommandSupport.jsonString(fen)
					+ ",\"left_engine\":" + CommandSupport.jsonString(leftEngine)
					+ ",\"right_engine\":" + CommandSupport.jsonString(rightEngine)
					+ ",\"left_uci\":" + CommandSupport.jsonString(leftUci)
					+ ",\"left_san\":" + CommandSupport.jsonString(leftSan)
					+ ",\"right_uci\":" + CommandSupport.jsonString(rightUci)
					+ ",\"right_san\":" + CommandSupport.jsonString(rightSan)
					+ ",\"same_move\":" + sameMove
					+ ",\"left_eval\":" + CommandSupport.jsonString(leftEval)
					+ ",\"right_eval\":" + CommandSupport.jsonString(rightEval)
					+ ",\"eval_delta_cp\":" + (evalDeltaCp == null ? "null" : evalDeltaCp)
					+ ",\"left_depth\":" + leftDepth
					+ ",\"right_depth\":" + rightDepth
					+ ",\"left_nodes\":" + leftNodes
					+ ",\"right_nodes\":" + rightNodes
					+ "}";
		}
	}

	/**
	 * Compare summary.
	 *
	 * @param rows total rows
	 * @param same same-move rows
	 * @param different different-move rows
	 * @param errors error rows
	 */
	private record CompareSummary(int rows, int same, int different, int errors) {

		/**
		 * Converts the summary to JSON.
		 *
		 * @return JSON object
		 */
		String toJson() {
			return "{\"rows\":" + rows
					+ ",\"same\":" + same
					+ ",\"different\":" + different
					+ ",\"errors\":" + errors
					+ "}";
		}
	}
}
