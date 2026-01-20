package application.cli.command;

import static application.cli.Constants.CMD_BESTMOVE;
import static application.cli.Constants.CMD_BESTMOVE_BOTH;
import static application.cli.Constants.CMD_BESTMOVE_SAN;
import static application.cli.Constants.CMD_BESTMOVE_UCI;
import static application.cli.Constants.OPT_BOTH;
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
import static application.cli.Constants.OPT_SAN;
import static application.cli.Constants.OPT_THREADS;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;
import static application.cli.Constants.OPT_WDL;
import static application.cli.EngineOps.analysePositionOrExit;
import static application.cli.EngineOps.configureEngine;
import static application.cli.EngineOps.parsePositionOrNull;
import static application.cli.EngineOps.resolveWdlFlag;
import static application.cli.Format.safeSan;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import application.Config;
import chess.core.Move;
import chess.core.Position;
import chess.uci.Analysis;
import chess.uci.Engine;
import chess.uci.Protocol;
import utility.Argv;

/**
 * Implements {@code bestmove} and its output shortcuts.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class BestMoveCommand {

	/**
	 * Utility class; prevent instantiation.
	 */
	private BestMoveCommand() {
		// utility
	}

	/**
	 * Parsed options for best-move queries.
	 *
	 * @param verbose   whether to print verbose diagnostics
	 * @param san       whether to emit SAN output
	 * @param both      whether to emit both UCI and SAN
	 * @param input     optional input file path
	 * @param fen       optional FEN string
	 * @param protoPath protocol configuration path
	 * @param nodesCap  node limit for analysis
	 * @param durMs     duration limit for analysis (ms)
	 * @param multipv   multi-PV count override
	 * @param threads   engine threads override
	 * @param hash      engine hash override (MB)
	 * @param wdl       request WDL output
	 * @param noWdl     disable WDL output
	 */
	private record BestMoveOptions(
			boolean verbose,
			boolean san,
			boolean both,
			Path input,
			String fen,
			String protoPath,
			long nodesCap,
			long durMs,
			Integer multipv,
			Integer threads,
			Integer hash,
			boolean wdl,
			boolean noWdl) {
	}

	/**
	 * Supported output formats for best-move requests.
	 */
	private enum BestMoveFormat {
		DEFAULT,
		UCI,
		SAN,
		BOTH
	}

	/**
	 * Handles {@code bestmove}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runBestMove(Argv a) {
		runBestMoveCommand(CMD_BESTMOVE, a, BestMoveFormat.DEFAULT);
	}

	/**
	 * Handles {@code bestmove-uci}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runBestMoveUci(Argv a) {
		runBestMoveCommand(CMD_BESTMOVE_UCI, a, BestMoveFormat.UCI);
	}

	/**
	 * Handles {@code bestmove-san}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runBestMoveSan(Argv a) {
		runBestMoveCommand(CMD_BESTMOVE_SAN, a, BestMoveFormat.SAN);
	}

	/**
	 * Handles {@code bestmove-both}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runBestMoveBoth(Argv a) {
		runBestMoveCommand(CMD_BESTMOVE_BOTH, a, BestMoveFormat.BOTH);
	}

	/**
	 * Dispatches best-move handling with a fixed output format override.
	 *
	 * @param cmdLabel command label for diagnostics
	 * @param a        argument parser for the subcommand
	 * @param format   output format override
	 */
	private static void runBestMoveCommand(String cmdLabel, Argv a, BestMoveFormat format) {
		BestMoveOptions opts = parseBestMoveOptions(a);
		BestMoveOptions resolved = applyBestMoveFormat(opts, format);
		runBestMoveWithOptions(cmdLabel, resolved);
	}

	/**
	 * Applies the fixed format override on top of parsed CLI options.
	 *
	 * @param opts   parsed option set
	 * @param format format override to apply
	 * @return adjusted options reflecting the format override
	 */
	private static BestMoveOptions applyBestMoveFormat(BestMoveOptions opts, BestMoveFormat format) {
		boolean san = opts.san();
		boolean both = opts.both();
		if (format == BestMoveFormat.UCI) {
			san = false;
			both = false;
		} else if (format == BestMoveFormat.SAN) {
			san = true;
			both = false;
		} else if (format == BestMoveFormat.BOTH) {
			san = false;
			both = true;
		}
		return new BestMoveOptions(
				opts.verbose(),
				san,
				both,
				opts.input(),
				opts.fen(),
				opts.protoPath(),
				opts.nodesCap(),
				opts.durMs(),
				opts.multipv(),
				opts.threads(),
				opts.hash(),
				opts.wdl(),
				opts.noWdl());
	}

	/**
	 * Runs the best-move workflow with a resolved option set.
	 *
	 * @param cmdLabel command label for diagnostics
	 * @param opts     resolved options to apply
	 */
	private static void runBestMoveWithOptions(String cmdLabel, BestMoveOptions opts) {
		if (opts.wdl() && opts.noWdl()) {
			System.err.println(String.format(
					"%s: only one of %s or %s may be set",
					cmdLabel,
					OPT_WDL,
					OPT_NO_WDL));
			System.exit(2);
			return;
		}

		List<String> fens = CommandSupport.resolveFenInputs(cmdLabel, opts.input(), opts.fen());
		Protocol protocol = EngineSupport.loadProtocolOrExit(opts.protoPath(), opts.verbose());
		Optional<Boolean> wdlFlag = resolveWdlFlag(opts.wdl(), opts.noWdl());

		try (Engine engine = new Engine(protocol)) {
			configureEngine(cmdLabel, engine, opts.threads(), opts.hash(), opts.multipv(), wdlFlag);
			for (String entry : fens) {
				Position pos = parsePositionOrNull(entry, cmdLabel, opts.verbose());
				if (pos == null) {
					continue;
				}
				Analysis analysis = analysePositionOrExit(engine, pos, opts.nodesCap(), opts.durMs(), cmdLabel,
						opts.verbose());
				if (analysis == null) {
					return;
				}
				printBestMove(entry, pos, analysis, opts.input(), opts.san(), opts.both());
			}
		} catch (Exception ex) {
			System.err.println(cmdLabel + ": failed to initialize engine: " + ex.getMessage());
			if (opts.verbose()) {
				ex.printStackTrace(System.err);
			}
			System.exit(2);
		}
	}

	/**
	 * Parses CLI arguments for best-move commands.
	 *
	 * @param a argument parser for the subcommand
	 * @return parsed option set
	 */
	private static BestMoveOptions parseBestMoveOptions(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		boolean san = a.flag(OPT_SAN);
		boolean both = a.flag(OPT_BOTH);
		Path input = a.path(OPT_INPUT, OPT_INPUT_SHORT);
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
		return new BestMoveOptions(
				verbose,
				san,
				both,
				input,
				fen,
				protoPath,
				nodesCap,
				durMs,
				multipv,
				threads,
				hash,
				wdl,
				noWdl);
	}

	/**
	 * Prints the best move output for a single analyzed position.
	 *
	 * @param entry   raw FEN line (used when reading from file)
	 * @param pos     parsed position
	 * @param analysis analysis results containing the best move
	 * @param input   optional input file path
	 * @param san     whether to print SAN output
	 * @param both    whether to print both UCI and SAN
	 */
	private static void printBestMove(
			String entry,
			Position pos,
			Analysis analysis,
			Path input,
			boolean san,
			boolean both) {
		short best = analysis.getBestMove();
		String uci = (best == Move.NO_MOVE) ? "0000" : Move.toString(best);
		String sanMove = (best == Move.NO_MOVE) ? "-" : safeSan(pos, best);
		if (input != null) {
			if (both) {
				System.out.println(entry + "\t" + uci + "\t" + sanMove);
			} else if (san) {
				System.out.println(entry + "\t" + sanMove);
			} else {
				System.out.println(entry + "\t" + uci);
			}
			return;
		}
		if (both) {
			System.out.println(uci + "\t" + sanMove);
		} else if (san) {
			System.out.println(sanMove);
		} else {
			System.out.println(uci);
		}
	}
}
