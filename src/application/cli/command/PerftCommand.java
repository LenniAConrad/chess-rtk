package application.cli.command;

import static application.cli.Constants.CMD_PERFT;
import static application.cli.Constants.ERR_INVALID_FEN;
import static application.cli.Constants.OPT_DEPTH;
import static application.cli.Constants.OPT_DEPTH_SHORT;
import static application.cli.Constants.OPT_DIVIDE;
import static application.cli.Constants.OPT_FEN;
import static application.cli.Constants.OPT_PER_MOVE;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;
import static application.cli.Validation.requireNonNegative;

import java.util.List;

import chess.core.Position;
import chess.core.Setup;
import chess.debug.LogService;
import chess.debug.Printer;
import utility.Argv;

/**
 * Implements {@code perft} and {@code perft-suite}.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class PerftCommand {

	/**
	 * Utility class; prevent instantiation.
	 */
	private PerftCommand() {
		// utility
	}

	/**
	 * Handles {@code perft}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runPerft(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		boolean divide = a.flag(OPT_DIVIDE, OPT_PER_MOVE);
		Integer depth = a.integer(OPT_DEPTH, OPT_DEPTH_SHORT);
		String fen = a.string(OPT_FEN);
		List<String> rest = a.positionals();
		if (fen == null && !rest.isEmpty()) {
			fen = String.join(" ", rest);
		}
		a.ensureConsumed();

		if (depth == null) {
			System.err.println("perft requires " + OPT_DEPTH + " <n>");
			System.exit(2);
			return;
		}
		requireNonNegative(CMD_PERFT, OPT_DEPTH, depth);

		Position pos;
		try {
			pos = (fen == null || fen.isEmpty())
					? Setup.getStandardStartPosition()
					: new Position(fen.trim());
		} catch (IllegalArgumentException ex) {
			System.err.println(ERR_INVALID_FEN + (ex.getMessage() == null ? "" : ex.getMessage()));
			LogService.error(ex, "perft: invalid FEN", "FEN: " + fen);
			if (verbose) {
				ex.printStackTrace(System.err);
			}
			System.exit(3);
			return;
		}

		if (divide) {
			Printer.perft(pos, depth);
			return;
		}
		long nodes = pos.perft(depth);
		System.out.println("FEN: " + pos.toString());
		System.out.println("perft depth " + depth + ": " + nodes);
	}

	/**
	 * Handles {@code perft-suite}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runPerftSuite(Argv a) {
		a.ensureConsumed();
		Printer.testPerft();
	}
}
