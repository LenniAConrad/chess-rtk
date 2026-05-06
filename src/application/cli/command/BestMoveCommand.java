package application.cli.command;

import static application.cli.Constants.CMD_BESTMOVE;
import static application.cli.Constants.CMD_BESTMOVE_BOTH;
import static application.cli.Constants.CMD_BESTMOVE_SAN;
import static application.cli.Constants.CMD_BESTMOVE_UCI;
import static application.cli.Constants.OPT_BOTH;
import static application.cli.Constants.OPT_FORMAT;
import static application.cli.Constants.OPT_SAN;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;
import static application.cli.EngineOps.analysePositionOrExit;
import static application.cli.EngineOps.configureEngine;
import static application.cli.EngineOps.parsePositionOrNull;
import static application.cli.EngineOps.resolveWdlFlag;
import static application.cli.Format.safeSan;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import application.console.Bar;
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
		/**
		 * Stores the verbose.
		 */
		boolean verbose,
		/**
		 * Stores the san.
		 */
		boolean san,
		/**
		 * Stores the both.
		 */
		boolean both,
		/**
		 * Stores the input.
		 */
		Path input,
		/**
		 * Stores the fen.
		 */
		String fen,
		/**
		 * Stores the proto path.
		 */
		String protoPath,
		/**
		 * Stores the nodes cap.
		 */
		long nodesCap,
		/**
		 * Stores the dur ms.
		 */
		long durMs,
		/**
		 * Stores the multipv.
		 */
		Integer multipv,
		/**
		 * Stores the threads.
		 */
		Integer threads,
		/**
		 * Stores the hash.
		 */
		Integer hash,
		/**
		 * Stores the wdl.
		 */
		boolean wdl,
		/**
		 * Stores the no wdl.
		 */
		boolean noWdl
	) {
	}

	/**
	 * Supported output formats for best-move requests.
	 */
	private enum BestMoveFormat {
		/**
		 * Shared default constant.
		 */
		DEFAULT,
		/**
		 * Shared uci constant.
		 */
		UCI,
		/**
		 * Shared san constant.
		 */
		SAN,
		/**
		 * Shared both constant.
		 */
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
		BestMoveOptions opts = parseBestMoveOptions(a, cmdLabel);
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
		List<String> fens = CommandSupport.resolveFenInputs(cmdLabel, opts.input(), opts.fen());
		Protocol protocol = EngineSupport.loadProtocolOrExit(opts.protoPath(), opts.verbose());
		Optional<Boolean> wdlFlag = resolveWdlFlag(opts.wdl(), opts.noWdl());

		try (Engine engine = new Engine(protocol)) {
			configureEngine(cmdLabel, engine, opts.threads(), opts.hash(), opts.multipv(), wdlFlag);
			Bar bar = positionProgressBar(fens, cmdLabel);
			try {
				for (String entry : fens) {
					try {
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
					} finally {
						CommandSupport.step(bar);
					}
				}
			} finally {
				CommandSupport.finish(bar);
			}
		} catch (CommandFailure failure) {
			throw failure;
		} catch (Exception ex) {
			throw new CommandFailure(cmdLabel + ": failed to initialize engine: " + ex.getMessage(),
					ex, 2, opts.verbose());
		}
	}

	/**
	 * Handles position progress bar.
	 * @param fens fens
	 * @param label label
	 * @return computed value
	 */
	private static Bar positionProgressBar(List<String> fens, String label) {
		return fens != null && fens.size() > 1 ? new Bar(fens.size(), label, false, System.err) : null;
	}

	/**
	 * Parses CLI arguments for best-move commands.
	 *
	 * @param a argument parser for the subcommand
	 * @return parsed option set
	 */
	private static BestMoveOptions parseBestMoveOptions(Argv a, String cmdLabel) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		boolean san = a.flag(OPT_SAN);
		boolean both = a.flag(OPT_BOTH);
		BestMoveFormat requestedFormat = parseFormat(a.string(OPT_FORMAT), san, both, cmdLabel);
		if (requestedFormat == BestMoveFormat.SAN) {
			san = true;
			both = false;
		} else if (requestedFormat == BestMoveFormat.BOTH) {
			san = false;
			both = true;
		} else if (requestedFormat == BestMoveFormat.UCI) {
			san = false;
			both = false;
		}
		EngineSupport.UciOptions engine = EngineSupport.parseUciOptions(a, cmdLabel, false);
		return new BestMoveOptions(
				verbose,
				san,
				both,
				engine.input(),
				engine.fen(),
				engine.protocolPath(),
				engine.nodesCap(),
				engine.durationMillis(),
				engine.multipv(),
				engine.threads(),
				engine.hash(),
				engine.wdl(),
				engine.noWdl());
	}

	/**
	 * Resolves output format flags for {@code bestmove}.
	 *
	 * @param value    optional {@code --format} value.
	 * @param san      whether {@code --san} was present.
	 * @param both     whether {@code --both} was present.
	 * @param cmdLabel command label for diagnostics.
	 * @return resolved output format.
	 */
	private static BestMoveFormat parseFormat(String value, boolean san, boolean both, String cmdLabel) {
		if (value == null || value.isBlank()) {
			if (both) {
				return BestMoveFormat.BOTH;
			}
			return san ? BestMoveFormat.SAN : BestMoveFormat.UCI;
		}
		if (san || both) {
			throw new CommandFailure(cmdLabel + ": use either " + OPT_FORMAT + " or --san/--both, not both", 2);
		}
		return switch (value.trim().toLowerCase(Locale.ROOT)) {
			case "uci" -> BestMoveFormat.UCI;
			case "san" -> BestMoveFormat.SAN;
			case "both" -> BestMoveFormat.BOTH;
			default -> throw new CommandFailure(cmdLabel + ": unsupported " + OPT_FORMAT + " value: " + value
					+ " (expected uci, san, or both)", 2);
		};
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
