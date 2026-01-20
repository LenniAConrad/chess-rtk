package application.cli.command;

import static application.cli.Constants.CMD_THREATS;
import static application.cli.Constants.OPT_FEN;
import static application.cli.Constants.OPT_HASH;
import static application.cli.Constants.OPT_INPUT;
import static application.cli.Constants.OPT_INPUT_SHORT;
import static application.cli.Constants.OPT_MAX_DURATION;
import static application.cli.Constants.OPT_MAX_NODES;
import static application.cli.Constants.OPT_MULTIPV;
import static application.cli.Constants.OPT_NODES;
import static application.cli.Constants.OPT_NO_WDL;
import static application.cli.Constants.OPT_PROTOCOL_PATH;
import static application.cli.Constants.OPT_PROTOCOL_PATH_SHORT;
import static application.cli.Constants.OPT_THREADS;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;
import static application.cli.Constants.OPT_WDL;
import static application.cli.EngineOps.analysePositionOrExit;
import static application.cli.EngineOps.configureEngine;
import static application.cli.EngineOps.parsePositionOrNull;
import static application.cli.EngineOps.resolveWdlFlag;
import static application.cli.Format.formatBound;
import static application.cli.Format.formatChances;
import static application.cli.Format.formatEvaluation;
import static application.cli.Format.formatPvMoves;
import static application.cli.Format.safeSan;

import java.util.List;
import java.util.Optional;

import application.Config;
import chess.core.Move;
import chess.core.Position;
import chess.uci.Analysis;
import chess.uci.Engine;
import chess.uci.Output;
import chess.uci.Protocol;
import utility.Argv;

/**
 * Implements the {@code threats} CLI command.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class ThreatsCommand {

	/**
	 * Utility class; prevent instantiation.
	 */
	private ThreatsCommand() {
		// utility
	}

	/**
	 * Handles {@code threats}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runThreats(Argv a) {
		ThreatsOptions opts = parseThreatsOptions(a);
		if (opts.fens.isEmpty()) {
			return;
		}
		Protocol protocol = EngineSupport.loadProtocolOrExit(opts.protoPath, opts.verbose);
		Optional<Boolean> wdlFlag = resolveWdlFlag(opts.wdlConfig.wdl, opts.wdlConfig.noWdl);

		try (Engine engine = new Engine(protocol)) {
			runThreatsWithEngine(engine, protocol, opts, wdlFlag);
		} catch (Exception ex) {
			System.err.println("threats: failed to initialize engine: " + ex.getMessage());
			if (opts.verbose) {
				ex.printStackTrace(System.err);
			}
			System.exit(2);
		}
	}

	/**
	 * Parses CLI arguments for {@code threats}.
	 *
	 * @param a argument parser for the subcommand
	 * @return parsed threat options, or {@code null} when invalid
	 */
	private static ThreatsOptions parseThreatsOptions(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		java.nio.file.Path input = a.path(OPT_INPUT, OPT_INPUT_SHORT);
		String fen = a.string(OPT_FEN);
		String protoPath = CommandSupport.optional(a.string(OPT_PROTOCOL_PATH, OPT_PROTOCOL_PATH_SHORT),
				Config.getProtocolPath());
		long nodesCap = Math.max(1, CommandSupport.optional(a.lng(OPT_MAX_NODES, OPT_NODES), Config.getMaxNodes()));
		long durMs = Math.max(1,
				CommandSupport.optionalDurationMs(a.duration(OPT_MAX_DURATION), Config.getMaxDuration()));
		Integer multipv = a.integer(OPT_MULTIPV);
		Integer threads = a.integer(OPT_THREADS);
		Integer hash = a.integer(OPT_HASH);
		boolean wdl = a.flag(OPT_WDL);
		boolean noWdl = a.flag(OPT_NO_WDL);
		List<String> rest = a.positionals();
		if (fen == null && !rest.isEmpty()) {
			fen = String.join(" ", rest);
		}
		a.ensureConsumed();

		if (wdl && noWdl) {
			System.err.println(String.format("threats: only one of %s or %s may be set", OPT_WDL, OPT_NO_WDL));
			System.exit(2);
			return null;
		}

		List<String> fens = CommandSupport.resolveFenInputs(CMD_THREATS, input, fen);
		ThreatsOptions.Limits limits = new ThreatsOptions.Limits(nodesCap, durMs);
		ThreatsOptions.EngineConfig engineConfig = new ThreatsOptions.EngineConfig(multipv, threads, hash);
		ThreatsOptions.WdlConfig wdlConfig = new ThreatsOptions.WdlConfig(wdl, noWdl);
		return new ThreatsOptions(verbose, protoPath, limits, engineConfig, wdlConfig, fens);
	}

	/**
	 * Runs the threats workflow using an initialized engine instance.
	 *
	 * @param engine   engine instance to reuse
	 * @param protocol engine protocol metadata
	 * @param opts     parsed threat options
	 * @param wdlFlag  resolved WDL flag override
	 */
	private static void runThreatsWithEngine(Engine engine, Protocol protocol, ThreatsOptions opts,
			Optional<Boolean> wdlFlag) {
		configureEngine(CMD_THREATS, engine, opts.engineConfig.threads, opts.engineConfig.hash,
				opts.engineConfig.multipv, wdlFlag);
		String engineLabel = protocol.getName() != null ? protocol.getName() : protocol.getPath();
		System.out.println("Engine: " + engineLabel);

		ThreatsRunState state = new ThreatsRunState(opts.engineConfig.multipv);
		for (String entry : opts.fens) {
			if (!processThreatEntry(engine, entry, opts, state)) {
				return;
			}
		}
	}

	/**
	 * Processes a single FEN entry for threat analysis.
	 *
	 * @param engine engine instance to use
	 * @param entry  raw FEN entry
	 * @param opts   parsed threat options
	 * @param state  run state tracking multipv and output
	 * @return true to continue, false to abort on error
	 */
	private static boolean processThreatEntry(Engine engine, String entry, ThreatsOptions opts, ThreatsRunState state) {
		Position base = parsePositionOrNull(entry, CMD_THREATS, opts.verbose);
		if (base == null) {
			return true;
		}
		if (base.inCheck()) {
			System.err.println("threats: skipped (side to move is in check): " + entry);
			return true;
		}

		Position threatPos;
		try {
			threatPos = nullMovePosition(base);
		} catch (IllegalArgumentException ex) {
			System.err.println("threats: skipped (null move not legal): " + entry);
			if (opts.verbose) {
				ex.printStackTrace(System.err);
			}
			return true;
		}

		syncThreatsMultiPv(engine, threatPos, state);

		Analysis analysis = analysePositionOrExit(engine, threatPos, opts.limits.nodesCap, opts.limits.durMs,
				CMD_THREATS, opts.verbose);
		if (analysis == null) {
			return false;
		}

		if (state.printedAny) {
			System.out.println();
		}
		state.printedAny = true;
		printThreatsSummary(base, threatPos, analysis);
		return true;
	}

	/**
	 * Ensures the engine multipv setting matches the number of legal replies.
	 *
	 * @param engine    engine instance to update
	 * @param threatPos position after the null move
	 * @param state     run state tracking multipv settings
	 */
	private static void syncThreatsMultiPv(Engine engine, Position threatPos, ThreatsRunState state) {
		if (state.requestedMultiPv != null) {
			return;
		}
		int all = Math.max(1, threatPos.getMoves().size());
		if (state.lastMultiPv == null || state.lastMultiPv.intValue() != all) {
			engine.setMultiPivot(all);
			state.lastMultiPv = all;
		}
	}

	/**
	 * Produces a new position representing the null-move turn swap.
	 *
	 * @param base base position
	 * @return position after a null move
	 */
	private static Position nullMovePosition(Position base) {
		if (base.inCheck()) {
			throw new IllegalArgumentException("null move not legal while in check");
		}

		String[] parts = base.toString().split(" ");
		if (parts.length < 4) {
			throw new IllegalArgumentException("unexpected FEN: " + base);
		}

		boolean wasWhite = base.isWhiteTurn();
		String newTurn = wasWhite ? "b" : "w";

		String placement = parts[0];
		String castling = parts[2];
		String enPassant = "-";

		int halfMove = parts.length > 4 ? Integer.parseInt(parts[4]) : 0;
		int fullMove = parts.length > 5 ? Integer.parseInt(parts[5]) : 1;

		halfMove = Math.max(0, halfMove + 1);
		if (!wasWhite) {
			fullMove = Math.max(1, fullMove + 1);
		}

		String fen = String.format(
				"%s %s %s %s %d %d",
				placement,
				newTurn,
				castling,
				enPassant,
				halfMove,
				fullMove);
		return new Position(fen);
	}

	/**
	 * Prints a summary of threat analysis results.
	 *
	 * @param base      original position
	 * @param threatPos null-move position
	 * @param analysis  analysis results for threat side
	 */
	private static void printThreatsSummary(Position base, Position threatPos, Analysis analysis) {
		String side = base.isWhiteTurn() ? "black" : "white";
		System.out.println(String.format("FEN: %s", base.toString()));
		System.out.println(String.format("threats-for: %s", side));
		System.out.println(String.format("threats-fen: %s", threatPos.toString()));
		if (analysis == null || analysis.isEmpty()) {
			System.out.println("threats: (no output)");
			return;
		}
		int pivots = Math.max(1, analysis.getPivots());
		for (int pv = 1; pv <= pivots; pv++) {
			Output best = analysis.getBestOutput(pv);
			if (best == null) {
				continue;
			}
			String eval = formatEvaluation(best.getEvaluation());
			String wdl = formatChances(best.getChances());
			String bound = formatBound(best.getBound());
			System.out.printf(
					"pv%d eval=%s depth=%d seldepth=%d nodes=%d nps=%d time=%d wdl=%s bound=%s%n",
					pv,
					eval,
					best.getDepth(),
					best.getSelectiveDepth(),
					best.getNodes(),
					best.getNodesPerSecond(),
					best.getTime(),
					wdl,
					bound);
			short bestMove = analysis.getBestMove(pv);
			if (bestMove != Move.NO_MOVE) {
				System.out.printf("pv%d threat=%s san=%s%n", pv, Move.toString(bestMove),
						safeSan(threatPos, bestMove));
			}
			String pvLine = formatPvMoves(best.getMoves());
			if (!pvLine.isEmpty()) {
				System.out.printf("pv%d line=%s%n", pv, pvLine);
			}
		}
	}

	/**
	 * Parsed options for threat analysis.
	 */
	private static final class ThreatsOptions {

		/**
		 * Whether to print verbose diagnostics.
		 */
		private final boolean verbose;

		/**
		 * Protocol configuration path.
		 */
		private final String protoPath;

		/**
		 * Analysis limit settings.
		 */
		private final Limits limits;

		/**
		 * Engine configuration overrides.
		 */
		private final EngineConfig engineConfig;

		/**
		 * WDL output configuration flags.
		 */
		private final WdlConfig wdlConfig;

		/**
		 * FEN entries to analyze.
		 */
		private final List<String> fens;

		/**
		 * Creates a new parsed option bundle.
		 *
		 * @param verbose     whether to print verbose diagnostics
		 * @param protoPath   protocol configuration path
		 * @param limits      analysis limit settings
		 * @param engineConfig engine configuration overrides
		 * @param wdlConfig   WDL output configuration flags
		 * @param fens        FEN entries to analyze
		 */
		private ThreatsOptions(boolean verbose, String protoPath, Limits limits, EngineConfig engineConfig,
				WdlConfig wdlConfig, List<String> fens) {
			this.verbose = verbose;
			this.protoPath = protoPath;
			this.limits = limits;
			this.engineConfig = engineConfig;
			this.wdlConfig = wdlConfig;
			this.fens = fens;
		}

		/**
		 * Analysis limit values for nodes and duration.
		 */
		private static final class Limits {

			/**
			 * Maximum nodes per analysis.
			 */
			private final long nodesCap;

			/**
			 * Maximum duration per analysis (ms).
			 */
			private final long durMs;

			/**
			 * Creates a new limit configuration.
			 *
			 * @param nodesCap node limit
			 * @param durMs    duration limit (ms)
			 */
			private Limits(long nodesCap, long durMs) {
				this.nodesCap = nodesCap;
				this.durMs = durMs;
			}
		}

		/**
		 * Engine configuration overrides for threats analysis.
		 */
		private static final class EngineConfig {

			/**
			 * MultiPV override value.
			 */
			private final Integer multipv;

			/**
			 * Thread count override.
			 */
			private final Integer threads;

			/**
			 * Hash size override (MB).
			 */
			private final Integer hash;

			/**
			 * Creates a new engine configuration override.
			 *
			 * @param multipv multi-PV override
			 * @param threads thread count override
			 * @param hash    hash size override
			 */
			private EngineConfig(Integer multipv, Integer threads, Integer hash) {
				this.multipv = multipv;
				this.threads = threads;
				this.hash = hash;
			}
		}

		/**
		 * WDL output flag overrides.
		 */
		private static final class WdlConfig {

			/**
			 * Whether to request WDL output.
			 */
			private final boolean wdl;

			/**
			 * Whether to disable WDL output.
			 */
			private final boolean noWdl;

			/**
			 * Creates a new WDL configuration bundle.
			 *
			 * @param wdl   whether to request WDL output
			 * @param noWdl whether to disable WDL output
			 */
			private WdlConfig(boolean wdl, boolean noWdl) {
				this.wdl = wdl;
				this.noWdl = noWdl;
			}
		}
	}

	/**
	 * Tracks runtime state for the threats command.
	 */
	private static final class ThreatsRunState {

		/**
		 * Requested MultiPV value from the CLI (may be null).
		 */
		private final Integer requestedMultiPv;

		/**
		 * Whether any output has been printed yet.
		 */
		private boolean printedAny;

		/**
		 * Last MultiPV value applied to the engine.
		 */
		private Integer lastMultiPv;

		/**
		 * Creates a new run state tracker.
		 *
		 * @param requestedMultiPv requested MultiPV value
		 */
		private ThreatsRunState(Integer requestedMultiPv) {
			this.requestedMultiPv = requestedMultiPv;
			this.lastMultiPv = requestedMultiPv;
		}
	}
}
