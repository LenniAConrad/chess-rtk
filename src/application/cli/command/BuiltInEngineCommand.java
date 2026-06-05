package application.cli.command;

import static application.cli.Constants.OPT_DEPTH;
import static application.cli.Constants.OPT_DEPTH_SHORT;
import static application.cli.Constants.OPT_EVALUATOR;
import static application.cli.Constants.OPT_FORMAT;
import static application.cli.Constants.OPT_INPUT;
import static application.cli.Constants.OPT_INPUT_SHORT;
import static application.cli.Constants.OPT_CLASSICAL;
import static application.cli.Constants.OPT_LC0;
import static application.cli.Constants.OPT_MAX_DURATION;
import static application.cli.Constants.OPT_MAX_NODES;
import static application.cli.Constants.OPT_NODES;
import static application.cli.Constants.OPT_OTIS;
import static application.cli.Constants.OPT_THREADS;
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
import chess.engine.Mcts;
import chess.engine.MctsUci;
import chess.engine.Result;
import chess.eval.CentipawnEvaluator;
import chess.eval.Factory;
import chess.eval.Kind;
import utility.Argv;

/**
 * Implements the built-in engine CLI command.
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
	 * {@code --nnue} option flag.
	 */
	private static final String OPT_NNUE = "--nnue";

	/**
	 * {@code --uci} option flag.
	 */
	private static final String OPT_UCI = "--uci";

	/**
	 * {@code --search} option selecting the search algorithm.
	 */
	private static final String OPT_SEARCH = "--search";

	/**
	 * {@code --max-strength} option selecting stronger time-bound defaults.
	 */
	private static final String OPT_MAX_STRENGTH = "--max-strength";

	/**
	 * Default alpha-beta depth when the caller leaves all search budgets implicit.
	 * The normal node/time defaults still bound the run; this higher target lets
	 * iterative deepening use the available budget instead of stopping at depth 3.
	 */
	private static final int DEFAULT_ALPHA_BETA_DEPTH = 8;

	/**
	 * Alpha-beta depth ceiling used by {@code --max-strength}. This matches the
	 * Workbench Max setting: high enough for time-bounded iterative deepening while
	 * still keeping the fixed per-ply scratch arrays comfortably bounded.
	 */
	private static final int MAX_STRENGTH_ALPHA_BETA_DEPTH = 18;

	/**
	 * Maximum automatic Lazy SMP helper count for {@code --max-strength}. The cap
	 * keeps one core free and follows the measured Workbench Max configuration.
	 */
	private static final int MAX_STRENGTH_THREADS = 8;

	/**
	 * Utility class; prevent instantiation.
	 */
	private BuiltInEngineCommand() {
		// utility
	}

	/**
	 * Search algorithm for the built-in engine. {@link #ALPHA_BETA} is the normal
	 * default for classical/NNUE because it is materially stronger at fixed budget;
	 * {@link #MCTS} remains the policy/value search used by LC0/OTIS and by the
	 * minimal UCI loop.
	 */
	private enum SearchKind {
		/**
		 * PUCT Monte-Carlo tree search (default; the only search before this).
		 */
		MCTS,
		/**
		 * Iterative-deepening alpha-beta with transposition table and pruning.
		 */
		ALPHA_BETA
	}

	/**
	 * Uniform façade over the two built-in searchers so the command's option
	 * parsing, output formatting, and per-depth UCI streaming stay search-agnostic.
	 */
	private sealed interface Searcher extends AutoCloseable permits MctsSearcher, AlphaBetaSearcher {
		/**
		 * Searches a position to the given limits.
		 *
		 * @param position root position
		 * @param limits search limits
		 * @return final search result
		 */
		Result search(Position position, Limits limits);

		/**
		 * Searches a position, streaming a UCI {@code info} line per completed depth.
		 *
		 * @param position root position
		 * @param limits search limits
		 * @return final search result
		 */
		Result searchInfo(Position position, Limits limits);

		/**
		 * Returns the active evaluator label for summary output.
		 *
		 * @return evaluator name
		 */
		String evaluatorName();

		/**
		 * Releases searcher resources.
		 */
		@Override
		void close();
	}

	/**
	 * {@link Searcher} backed by the MCTS engine; also exposes the underlying
	 * {@link Mcts} for the MCTS-only {@code --uci} loop.
	 *
	 * @param mcts wrapped MCTS searcher
	 */
	private record MctsSearcher(Mcts mcts) implements Searcher {
		/**
		 * Searches a position with the configured MCTS limits.
		 */
		@Override
		public Result search(Position position, Limits limits) {
			return mcts.search(position, limits);
		}

		/**
		 * Searches a position and emits UCI search information.
		 */
		@Override
		public Result searchInfo(Position position, Limits limits) {
			return mcts.search(position, limits, BuiltInEngineCommand::printUciInfo);
		}

		/**
		 * Returns the evaluator name used by the wrapped searcher.
		 */
		@Override
		public String evaluatorName() {
			return mcts.evaluatorName();
		}

		/**
		 * Updates the MCTS worker thread count.
		 *
		 * @param threads requested thread count
		 */
		private void setThreads(int threads) {
			mcts.setThreads(threads);
		}

		/**
		 * Releases wrapped MCTS resources.
		 */
		@Override
		public void close() {
			mcts.close();
		}
	}

	/**
	 * {@link Searcher} backed by the alpha-beta engine.
	 *
	 * @param engine wrapped alpha-beta searcher
	 */
	private record AlphaBetaSearcher(AlphaBeta engine) implements Searcher {
		/**
		 * Searches a position with the configured alpha-beta limits.
		 */
		@Override
		public Result search(Position position, Limits limits) {
			return engine.search(position, limits);
		}

		/**
		 * Searches a position and emits UCI search information.
		 */
		@Override
		public Result searchInfo(Position position, Limits limits) {
			return engine.search(position, limits, BuiltInEngineCommand::printUciInfo);
		}

		/**
		 * Returns the evaluator name used by the wrapped searcher.
		 */
		@Override
		public String evaluatorName() {
			return engine.evaluatorName();
		}

		/**
		 * Updates the alpha-beta Lazy SMP helper count.
		 *
		 * @param threads requested thread count
		 */
		private void setThreads(int threads) {
			engine.setSearchThreads(threads);
		}

		/**
		 * Releases wrapped alpha-beta resources.
		 */
		@Override
		public void close() {
			engine.close();
		}
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
		Path weights,
		/**
		 * Stores whether to run the UCI loop.
		 */
		boolean uciLoop,
		/**
		 * Stores the search algorithm.
		 */
		SearchKind search,
		/**
		 * Stores the worker thread count.
		 */
		int threads
	) {
	}

	/**
	 * Handles {@code engine builtin}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runBuiltIn(Argv a) {
		Options opts = parseOptions(a);
		try (Searcher searcher = createSearcher(opts)) {
			if (opts.uciLoop()) {
				// --uci is rejected for alpha-beta at parse time, so this is
				// always an MctsSearcher; MctsUci.run is MCTS-specific.
				MctsUci.run(System.in, System.out, ((MctsSearcher) searcher).mcts());
				return;
			}
			List<String> fens = CommandSupport.resolveFenInputs(CMD_BUILTIN, opts.input(), opts.fen());
			Bar bar = progressBar(fens);
			for (int i = 0; i < fens.size(); i++) {
				searchAndPrint(fens.get(i), searcher, opts, i > 0);
				CommandSupport.step(bar);
			}
			CommandSupport.finish(bar);
		} catch (IOException ex) {
			if (opts.verbose()) {
				ex.printStackTrace(System.err);
			}
			throw new CommandFailure(CMD_BUILTIN + ": engine initialization failed: " + ex.getMessage(), 2);
		}
	}

	/**
	 * Searches one input FEN and prints the result.
	 * @param entry entry value
	 * @param searcher searcher value
	 * @param opts command options
	 * @param blankBeforeSummary blank before summary value
	 */
	private static void searchAndPrint(String entry, Searcher searcher, Options opts, boolean blankBeforeSummary) {
		try {
			Position position = EngineOps.parsePositionOrNull(entry, CMD_BUILTIN, opts.verbose());
			if (position == null) {
				return;
			}
			if (opts.format() == OutputFormat.UCI_INFO && opts.input() != null) {
				System.out.println("info string fen " + entry);
			}
			Result result = search(searcher, position, opts);
			printResult(entry, position, result, searcher.evaluatorName(), opts.input() != null, opts.format(),
					blankBeforeSummary);
		} catch (RuntimeException ex) {
			if (opts.verbose()) {
				ex.printStackTrace(System.err);
			}
			throw new CommandFailure(CMD_BUILTIN + ": search failed: " + ex.getMessage(), 2);
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
		boolean otis = a.flag(OPT_OTIS);
		boolean uciLoop = a.flag(OPT_UCI);
		boolean maxStrength = a.flag(OPT_MAX_STRENGTH);
		Path weights = a.path(OPT_WEIGHTS);
		Kind evaluator = resolveEvaluator(evaluatorValue, classical, nnue, lc0, otis);
		SearchKind search = resolveSearch(a.string(OPT_SEARCH), evaluator, uciLoop);
		Integer threadsOpt = a.integer(OPT_THREADS);
		int threads = threadsOpt == null ? defaultThreads(maxStrength) : threadsOpt;
		Integer depthOpt = a.integer(OPT_DEPTH, OPT_DEPTH_SHORT);
		int defaultDepth = defaultDepth(search, maxStrength);
		int depth = depthOpt == null ? defaultDepth : depthOpt;
		Long nodesOpt = a.lng(OPT_MAX_NODES, OPT_NODES);
		Duration durationOpt = a.duration(OPT_MAX_DURATION);
		long defaultNodes = defaultNodeBudget(depthOpt, maxStrength);
		// Only impose the default time cap when the caller gave neither a depth nor
		// an explicit node budget; otherwise an explicit --max-nodes would be
		// silently truncated by the 5s default.
		long defaultDuration = (depthOpt == null && nodesOpt == null)
				? Limits.DEFAULT_MAX_DURATION_MILLIS : 0L;
		long maxNodes = nodesOpt == null ? defaultNodes : nodesOpt;
		long maxDuration = CommandSupport.optionalDurationMs(durationOpt, defaultDuration);
		String fen;
		if (uciLoop) {
			if (search == SearchKind.ALPHA_BETA) {
				throw new CommandFailure(CMD_BUILTIN + ": " + OPT_UCI + " requires " + OPT_SEARCH + " mcts", 2);
			}
			if (input != null) {
				throw new CommandFailure(CMD_BUILTIN + ": " + OPT_UCI + " cannot be combined with " + OPT_INPUT, 2);
			}
			if (!a.positionals().isEmpty()) {
				throw new CommandFailure(CMD_BUILTIN + ": " + OPT_UCI + " does not take a FEN argument", 2);
			}
			a.ensureConsumed();
			fen = null;
		} else {
			fen = CommandSupport.resolveFenArgument(a, CMD_BUILTIN, false);
		}

		Validation.requireBetweenInclusive(CMD_BUILTIN, OPT_DEPTH, depth, 1, AlphaBeta.MAX_DEPTH);
		Validation.requirePositive(CMD_BUILTIN, OPT_THREADS, threads);
		if (maxNodes < 0L) {
			throw new CommandFailure(CMD_BUILTIN + ": " + OPT_MAX_NODES + " must be non-negative", 2);
		}
		if (maxDuration < 0L) {
			throw new CommandFailure(CMD_BUILTIN + ": " + OPT_MAX_DURATION + " must be non-negative", 2);
		}

		Limits limits = new Limits(depth, maxNodes, maxDuration);
		if (weights != null && evaluator == Kind.CLASSICAL) {
			throw new CommandFailure(CMD_BUILTIN + ": " + OPT_WEIGHTS + " requires "
					+ OPT_EVALUATOR + " nnue, lc0, or otis", 2);
		}
		return new Options(verbose, input, fen, limits, parseFormat(format), evaluator, weights, uciLoop, search,
				threads);
	}

	/**
	 * Resolves the implicit search depth.
	 *
	 * @param search selected search algorithm
	 * @param maxStrength whether max-strength defaults are enabled
	 * @return default depth
	 */
	private static int defaultDepth(SearchKind search, boolean maxStrength) {
		if (search == SearchKind.ALPHA_BETA) {
			return maxStrength ? MAX_STRENGTH_ALPHA_BETA_DEPTH : DEFAULT_ALPHA_BETA_DEPTH;
		}
		return Limits.DEFAULT_DEPTH;
	}

	/**
	 * Resolves the implicit node budget. Max-strength searches are time-bound by
	 * default, because Lazy SMP gains strength by searching more nodes per second.
	 *
	 * @param depthOpt explicit depth, or null
	 * @param maxStrength whether max-strength defaults are enabled
	 * @return default node cap
	 */
	private static long defaultNodeBudget(Integer depthOpt, boolean maxStrength) {
		if (depthOpt != null || maxStrength) {
			return 0L;
		}
		return Limits.DEFAULT_MAX_NODES;
	}

	/**
	 * Resolves the implicit worker count.
	 *
	 * @param maxStrength whether max-strength defaults are enabled
	 * @return default worker count
	 */
	private static int defaultThreads(boolean maxStrength) {
		if (!maxStrength) {
			return 1;
		}
		return Math.max(1, Math.min(MAX_STRENGTH_THREADS, Runtime.getRuntime().availableProcessors() - 1));
	}

	/**
	 * Resolves the search algorithm from {@code --search}.
	 *
	 * @param value optional {@code --search} value
	 * @param evaluator selected evaluator kind
	 * @param uciLoop whether the command starts the UCI loop
	 * @return selected search kind
	 */
	private static SearchKind resolveSearch(String value, Kind evaluator, boolean uciLoop) {
		if (value == null) {
			if (uciLoop || evaluator == Kind.LC0 || evaluator == Kind.OTIS) {
				return SearchKind.MCTS;
			}
			return SearchKind.ALPHA_BETA;
		}
		return switch (value.trim().toLowerCase(Locale.ROOT)) {
			case "mcts" -> SearchKind.MCTS;
			case "alpha-beta", "alphabeta", "ab" -> SearchKind.ALPHA_BETA;
			default -> throw new CommandFailure(CMD_BUILTIN + ": unknown " + OPT_SEARCH + " value '" + value
					+ "' (use alpha-beta or mcts)", 2);
		};
	}

	/**
	 * Resolves evaluator selection.
	 *
	 * @param value optional evaluator value
	 * @param classical whether {@code --classical} was provided
	 * @param nnue whether {@code --nnue} was provided
	 * @param lc0 whether {@code --lc0} was provided
	 * @param otis whether {@code --otis} was provided
	 * @return evaluator kind
	 */
	private static Kind resolveEvaluator(String value, boolean classical, boolean nnue, boolean lc0, boolean otis) {
		int flags = (classical ? 1 : 0) + (nnue ? 1 : 0) + (lc0 ? 1 : 0) + (otis ? 1 : 0);
		if (value != null && flags > 0) {
			throw new CommandFailure(
					CMD_BUILTIN + ": use either " + OPT_EVALUATOR + " or evaluator shortcut flags, not both", 2);
		}
		if (flags > 1) {
			throw new CommandFailure(CMD_BUILTIN + ": choose only one evaluator flag", 2);
		}
		if (value != null) {
			try {
				return Kind.parse(value);
			} catch (IllegalArgumentException ex) {
				throw new CommandFailure(CMD_BUILTIN + ": " + ex.getMessage(), 2);
			}
		}
		if (nnue) {
			return Kind.NNUE;
		}
		if (lc0) {
			return Kind.LC0;
		}
		if (otis) {
			return Kind.OTIS;
		}
		return Kind.CLASSICAL;
	}

	/**
	 * Creates the selected searcher.
	 *
	 * @param opts parsed options
	 * @return MCTS searcher
	 * @throws IOException if model weights cannot be loaded
	 */
	private static Searcher createSearcher(Options opts) throws IOException {
		if (opts.search() == SearchKind.ALPHA_BETA) {
			// Alpha-beta drives any CentipawnEvaluator (classical/NNUE strongest;
			// LC0/OTIS run per-leaf with no batching, so they are slow here). A
			// per-invocation table — the CLI searches one position per run, so a
			// persistent TT would only add memory with no cross-search reuse.
			AlphaBetaSearcher searcher = new AlphaBetaSearcher(new AlphaBeta(createEvaluator(opts), false));
			searcher.setThreads(opts.threads());
			return searcher;
		}
		if (opts.evaluator() == Kind.LC0) {
			Path weights = opts.weights() == null ? Path.of(Config.getLc0ModelPath()) : opts.weights();
			MctsSearcher searcher = new MctsSearcher(Mcts.lc0(weights));
			searcher.setThreads(opts.threads());
			return searcher;
		}
		if (opts.evaluator() == Kind.OTIS) {
			Path weights = opts.weights() == null ? chess.nn.otis.Model.DEFAULT_WEIGHTS : opts.weights();
			MctsSearcher searcher = new MctsSearcher(Mcts.otis(weights));
			searcher.setThreads(opts.threads());
			return searcher;
		}
		MctsSearcher searcher = new MctsSearcher(new Mcts(createEvaluator(opts)));
		searcher.setThreads(opts.threads());
		return searcher;
	}

	/**
	 * Creates the selected centipawn evaluator.
	 *
	 * @param opts parsed options
	 * @return evaluator
	 * @throws IOException if model weights cannot be loaded
	 */
	private static CentipawnEvaluator createEvaluator(Options opts) throws IOException {
		Path weights = opts.weights();
		if (opts.evaluator() == Kind.NNUE) {
			weights = resolveNnueWeights(weights);
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
			default -> throw new CommandFailure(CMD_BUILTIN + ": unsupported " + OPT_FORMAT + " value: " + value
					+ " (expected uci, uci-info, san, both, or summary)", 2);
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
	private static Result search(Searcher searcher, Position position, Options opts) {
		if (opts.format() != OutputFormat.UCI_INFO) {
			return searcher.search(position, opts.limits());
		}
		return searcher.searchInfo(position, opts.limits());
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
