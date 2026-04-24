package application.cli.command;

import static application.cli.Constants.OPT_DEPTH;
import static application.cli.Constants.OPT_DEPTH_SHORT;
import static application.cli.Constants.OPT_FORMAT;
import static application.cli.Constants.OPT_INPUT;
import static application.cli.Constants.OPT_INPUT_SHORT;
import static application.cli.Constants.OPT_CLASSICAL;
import static application.cli.Constants.OPT_LC0;
import static application.cli.Constants.OPT_MAX_DURATION;
import static application.cli.Constants.OPT_MAX_NODES;
import static application.cli.Constants.OPT_NODES;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;
import static application.cli.Constants.OPT_WEIGHTS;
import static application.cli.Format.formatPvMoves;
import static application.cli.Format.formatPvMovesSan;
import static application.cli.Format.safeSan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import application.Config;
import application.cli.EngineOps;
import application.cli.Validation;
import application.console.Bar;
import chess.core.Move;
import chess.core.Position;
import chess.engine.AlphaBeta;
import chess.engine.Limits;
import chess.engine.Result;
import chess.eval.CentipawnEvaluator;
import chess.eval.Factory;
import chess.eval.Kind;
import utility.Argv;

/**
 * Implements the built-in Java engine CLI command.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class BuiltInEngineCommand {

	/**
	 * Command label used in diagnostics.
	 */
	private static final String CMD_BUILTIN = "engine builtin";

	/**
	 * {@code --evaluator} option flag.
	 */
	private static final String OPT_EVALUATOR = "--evaluator";

	/**
	 * {@code --nnue} option flag.
	 */
	private static final String OPT_NNUE = "--nnue";

	/**
	 * Utility class; prevent instantiation.
	 */
	private BuiltInEngineCommand() {
		// utility
	}

	/**
	 * Supported output formats.
	 */
	private enum OutputFormat {
		/**
		 * UCI best move only.
		 */
		UCI,
		/**
		 * UCI-style search transcript with info lines and final bestmove.
		 */
		UCI_INFO,
		/**
		 * SAN best move only.
		 */
		SAN,
		/**
		 * UCI and SAN best move.
		 */
		BOTH,
		/**
		 * Human-readable search summary.
		 */
		SUMMARY
	}

	/**
	 * Parsed command options.
	 *
	 * @param verbose whether to print verbose diagnostics
	 * @param input optional input path
	 * @param fen optional FEN string
	 * @param limits search limits
	 * @param format output format
	 * @param evaluator evaluator kind
	 * @param weights optional evaluator weights
	 */
	private record Options(
		/**
		 * Stores the verbose.
		 */
		boolean verbose,
		/**
		 * Stores the input.
		 */
		Path input,
		/**
		 * Stores the fen.
		 */
		String fen,
		/**
		 * Stores the limits.
		 */
		Limits limits,
		/**
		 * Stores the format.
		 */
		OutputFormat format,
		/**
		 * Stores the evaluator.
		 */
		Kind evaluator,
		/**
		 * Stores the weights.
		 */
		Path weights
	) {
	}

	/**
	 * Handles {@code engine builtin}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runBuiltIn(Argv a) {
		Options opts = parseOptions(a);
		List<String> fens = CommandSupport.resolveFenInputs(CMD_BUILTIN, opts.input(), opts.fen());
		try (AlphaBeta searcher = new AlphaBeta(createEvaluator(opts))) {
			Bar bar = progressBar(fens);
			for (int i = 0; i < fens.size(); i++) {
				try {
					String entry = fens.get(i);
					Position position = EngineOps.parsePositionOrNull(entry, CMD_BUILTIN, opts.verbose());
					if (position == null) {
						continue;
					}
					if (opts.format() == OutputFormat.UCI_INFO && opts.input() != null) {
						System.out.println("info string fen " + entry);
					}
					Result result = search(searcher, position, opts);
					printResult(entry, position, result, searcher.evaluatorName(), opts.input() != null, opts.format(), i > 0);
				} catch (RuntimeException ex) {
					System.err.println(CMD_BUILTIN + ": search failed: " + ex.getMessage());
					if (opts.verbose()) {
						ex.printStackTrace(System.err);
					}
					System.exit(2);
				} finally {
					CommandSupport.step(bar);
				}
			}
			CommandSupport.finish(bar);
		} catch (IOException ex) {
			System.err.println(CMD_BUILTIN + ": evaluator initialization failed: " + ex.getMessage());
			if (opts.verbose()) {
				ex.printStackTrace(System.err);
			}
			System.exit(2);
		}
	}

	/**
	 * Parses command options.
	 *
	 * @param a argument parser
	 * @return parsed options
	 */
	private static Options parseOptions(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		Path input = a.path(OPT_INPUT, OPT_INPUT_SHORT);
		String format = a.string(OPT_FORMAT);
		String evaluatorValue = a.string(OPT_EVALUATOR);
		boolean classical = a.flag(OPT_CLASSICAL);
		boolean nnue = a.flag(OPT_NNUE);
		boolean lc0 = a.flag(OPT_LC0);
		Path weights = a.path(OPT_WEIGHTS);
		Integer depthOpt = a.integer(OPT_DEPTH, OPT_DEPTH_SHORT);
		int depth = depthOpt == null ? Limits.DEFAULT_DEPTH : depthOpt;
		Long nodesOpt = a.lng(OPT_MAX_NODES, OPT_NODES);
		Duration durationOpt = a.duration(OPT_MAX_DURATION);
		long defaultNodes = depthOpt == null ? Limits.DEFAULT_MAX_NODES : 0L;
		long defaultDuration = depthOpt == null ? Limits.DEFAULT_MAX_DURATION_MILLIS : 0L;
		long maxNodes = nodesOpt == null ? defaultNodes : nodesOpt;
		long maxDuration = CommandSupport.optionalDurationMs(durationOpt, defaultDuration);
		String fen = CommandSupport.resolveFenArgument(a, CMD_BUILTIN, false);

		Validation.requireBetweenInclusive(CMD_BUILTIN, OPT_DEPTH, depth, 1, AlphaBeta.MAX_DEPTH);
		if (maxNodes < 0L) {
			System.err.println(CMD_BUILTIN + ": " + OPT_MAX_NODES + " must be non-negative");
			System.exit(2);
		}
		if (maxDuration < 0L) {
			System.err.println(CMD_BUILTIN + ": " + OPT_MAX_DURATION + " must be non-negative");
			System.exit(2);
		}

		Limits limits = new Limits(depth, maxNodes, maxDuration);
		Kind evaluator = resolveEvaluator(evaluatorValue, classical, nnue, lc0);
		if (weights != null && evaluator == Kind.CLASSICAL) {
			System.err.println(CMD_BUILTIN + ": " + OPT_WEIGHTS + " requires " + OPT_EVALUATOR + " nnue or lc0");
			System.exit(2);
		}
		return new Options(verbose, input, fen, limits, parseFormat(format), evaluator, weights);
	}

	/**
	 * Resolves evaluator selection.
	 *
	 * @param value optional evaluator value
	 * @param classical whether {@code --classical} was provided
	 * @param nnue whether {@code --nnue} was provided
	 * @param lc0 whether {@code --lc0} was provided
	 * @return evaluator kind
	 */
	private static Kind resolveEvaluator(String value, boolean classical, boolean nnue, boolean lc0) {
		int flags = (classical ? 1 : 0) + (nnue ? 1 : 0) + (lc0 ? 1 : 0);
		if (value != null && flags > 0) {
			System.err.println(CMD_BUILTIN + ": use either " + OPT_EVALUATOR + " or evaluator shortcut flags, not both");
			System.exit(2);
		}
		if (flags > 1) {
			System.err.println(CMD_BUILTIN + ": choose only one evaluator flag");
			System.exit(2);
		}
		if (value != null) {
			try {
				return Kind.parse(value);
			} catch (IllegalArgumentException ex) {
				System.err.println(CMD_BUILTIN + ": " + ex.getMessage());
				System.exit(2);
			}
		}
		if (nnue) {
			return Kind.NNUE;
		}
		if (lc0) {
			return Kind.LC0;
		}
		return Kind.CLASSICAL;
	}

	/**
	 * Creates the selected evaluator.
	 *
	 * @param opts parsed options
	 * @return evaluator
	 * @throws IOException if model weights cannot be loaded
	 */
	private static CentipawnEvaluator createEvaluator(Options opts) throws IOException {
		Path weights = opts.weights();
		if (opts.evaluator() == Kind.NNUE) {
			weights = resolveNnueWeights(weights);
		} else if (weights == null && opts.evaluator() == Kind.LC0) {
			weights = Path.of(Config.getLc0ModelPath());
		}
		return Factory.create(opts.evaluator(), weights);
	}

	/**
	 * Resolves NNUE weights for the built-in engine.
	 *
	 * <p>
	 * User-facing NNUE search must never silently fall back to the synthetic
	 * all-zero smoke-test model because that produces flat scores and misleading
	 * best moves. The low-level NNUE API still exposes the fallback for isolated
	 * tests, but the CLI requires real weights.
	 * </p>
	 *
	 * @param weights explicit weights path, or null for the default path
	 * @return resolved NNUE weights path
	 */
	private static Path resolveNnueWeights(Path weights) {
		if (weights != null) {
			return weights;
		}
		Path defaultWeights = chess.nn.nnue.Model.DEFAULT_WEIGHTS;
		if (Files.isRegularFile(defaultWeights)) {
			return defaultWeights;
		}
		throw new CommandFailure(
				CMD_BUILTIN + ": default NNUE weights not found at " + defaultWeights
						+ "; install that file, run ./install.sh --models, or pass " + OPT_WEIGHTS + " <path>",
				2);
	}

	/**
	 * Parses an output format.
	 *
	 * @param value raw format value
	 * @return output format
	 */
	private static OutputFormat parseFormat(String value) {
		if (value == null || value.isBlank()) {
			return OutputFormat.UCI_INFO;
		}
		return switch (value.trim().toLowerCase(Locale.ROOT)) {
			case "uci" -> OutputFormat.UCI;
			case "uci-info", "info", "search" -> OutputFormat.UCI_INFO;
			case "san" -> OutputFormat.SAN;
			case "both" -> OutputFormat.BOTH;
			case "summary", "text" -> OutputFormat.SUMMARY;
			default -> {
				System.err.println(CMD_BUILTIN + ": unsupported " + OPT_FORMAT + " value: " + value
						+ " (expected uci, uci-info, san, both, or summary)");
				System.exit(2);
				yield OutputFormat.UCI;
			}
		};
	}

	/**
	 * Searches a position, attaching a UCI-style completed-depth listener when
	 * requested.
	 *
	 * @param searcher searcher instance
	 * @param position root position
	 * @param opts parsed options
	 * @return final search result
	 */
	private static Result search(AlphaBeta searcher, Position position, Options opts) {
		if (opts.format() != OutputFormat.UCI_INFO) {
			return searcher.search(position, opts.limits());
		}
		return searcher.search(position, opts.limits(), BuiltInEngineCommand::printUciInfo);
	}

	/**
	 * Prints one search result.
	 *
	 * @param entry raw FEN input
	 * @param position parsed position
	 * @param result search result
	 * @param evaluator evaluator label
	 * @param includeFen whether to include the FEN in compact output
	 * @param format requested output format
	 * @param separate whether to print a separating blank line for summary output
	 */
	private static void printResult(
			String entry,
			Position position,
			Result result,
			String evaluator,
			boolean includeFen,
			OutputFormat format,
			boolean separate) {
		if (format == OutputFormat.UCI_INFO) {
			printBestMove(result);
			return;
		}
		if (format == OutputFormat.SUMMARY) {
			printSummary(entry, position, result, evaluator, separate);
			return;
		}
		String uci = result.hasBestMove() ? Move.toString(result.bestMove()) : "0000";
		String san = result.hasBestMove() ? safeSan(position, result.bestMove()) : "-";
		String prefix = includeFen ? entry + "\t" : "";
		if (format == OutputFormat.SAN) {
			System.out.println(prefix + san);
		} else if (format == OutputFormat.BOTH) {
			System.out.println(prefix + uci + "\t" + san);
		} else {
			System.out.println(prefix + uci);
		}
	}

	/**
	 * Prints one UCI-style completed-depth info line.
	 *
	 * @param result completed-depth result
	 */
	private static void printUciInfo(Result result) {
		StringBuilder sb = new StringBuilder();
		sb.append("info depth ").append(result.depth())
				.append(" score ").append(uciScore(result))
				.append(" nodes ").append(result.nodes())
				.append(" nps ").append(nodesPerSecond(result))
				.append(" time ").append(result.elapsedMillis());
		String pv = formatPvMoves(result.principalVariation());
		if (!pv.isEmpty()) {
			sb.append(" pv ").append(pv);
		}
		System.out.println(sb);
		System.out.flush();
	}

	/**
	 * Formats a UCI score field.
	 *
	 * @param result search result
	 * @return UCI score field value
	 */
	private static String uciScore(Result result) {
		if (result.isMateScore()) {
			return "mate " + result.mateIn();
		}
		return "cp " + result.scoreCentipawns();
	}

	/**
	 * Computes nodes per second for UCI-style output.
	 *
	 * @param result search result
	 * @return nodes per second
	 */
	private static long nodesPerSecond(Result result) {
		long elapsed = Math.max(1L, result.elapsedMillis());
		return Math.max(0L, result.nodes() * 1_000L / elapsed);
	}

	/**
	 * Prints the final UCI bestmove line.
	 *
	 * @param result final result
	 */
	private static void printBestMove(Result result) {
		String uci = result.hasBestMove() ? Move.toString(result.bestMove()) : "0000";
		System.out.println("bestmove " + uci);
	}

	/**
	 * Prints a human-readable search summary.
	 *
	 * @param entry raw FEN
	 * @param position parsed position
	 * @param result search result
	 * @param evaluator evaluator label
	 * @param separate whether to prefix a blank line
	 */
	private static void printSummary(
			String entry,
			Position position,
			Result result,
			String evaluator,
			boolean separate) {
		if (separate) {
			System.out.println();
		}
		String uci = result.hasBestMove() ? Move.toString(result.bestMove()) : "0000";
		String san = result.hasBestMove() ? safeSan(position, result.bestMove()) : "-";
		System.out.println("FEN: " + entry);
		System.out.println("evaluator: " + evaluator);
		System.out.println("best: " + uci + " (" + san + ")");
		System.out.println("score: " + result.scoreLabel());
		System.out.println("depth: " + result.depth());
		System.out.println("nodes: " + CommandSupport.formatCount(result.nodes()));
		System.out.println("time-ms: " + result.elapsedMillis());
		System.out.println("stopped: " + result.stopped());
		String pv = formatPvMoves(result.principalVariation());
		if (!pv.isEmpty()) {
			System.out.println("pv: " + pv);
			System.out.println("pv-san: " + formatPvMovesSan(position, result.principalVariation()));
		}
	}

	/**
	 * Creates a progress bar for multi-position input.
	 *
	 * @param fens input FENs
	 * @return progress bar or null
	 */
	private static Bar progressBar(List<String> fens) {
		return fens != null && fens.size() > 1 ? new Bar(fens.size(), CMD_BUILTIN, false, System.err) : null;
	}

}
