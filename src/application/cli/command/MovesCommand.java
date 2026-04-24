package application.cli.command;

import static application.cli.Constants.ERR_INVALID_FEN;
import static application.cli.Constants.MSG_FEN_REQUIRED_HINT;
import static application.cli.Constants.OPT_BOTH;
import static application.cli.Constants.OPT_FORMAT;
import static application.cli.Constants.OPT_SAN;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;
import static application.cli.Format.safeSan;

import chess.core.Move;
import chess.core.MoveList;
import chess.core.Position;
import chess.debug.LogService;
import java.util.Locale;
import utility.Argv;

/**
 * Implements the {@code move} family of CLI commands.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class MovesCommand {

	/**
	 * Current command label for format-selectable move listing.
	 */
	private static final String MOVE_LIST = "move list";

	/**
	 * Current command label for UCI move listing.
	 */
	private static final String MOVE_UCI = "move uci";

	/**
	 * Current command label for SAN move listing.
	 */
	private static final String MOVE_SAN = "move san";

	/**
	 * Current command label for UCI and SAN move listing.
	 */
	private static final String MOVE_BOTH = "move both";

	/**
	 * Utility class; prevent instantiation.
	 */
	private MovesCommand() {
		// utility
	}

	/**
	 * Handles {@code move list}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runMoves(Argv a) {
		runMovesCommand(a, MovesFormat.DEFAULT, MOVE_LIST);
	}

	/**
	 * Handles {@code move uci}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runMovesUci(Argv a) {
		runMovesCommand(a, MovesFormat.UCI, MOVE_UCI);
	}

	/**
	 * Handles {@code move san}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runMovesSan(Argv a) {
		runMovesCommand(a, MovesFormat.SAN, MOVE_SAN);
	}

	/**
	 * Handles {@code move both}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runMovesBoth(Argv a) {
		runMovesCommand(a, MovesFormat.BOTH, MOVE_BOTH);
	}

	/**
	 * Supported output formats for move listings.
	 */
	private enum MovesFormat {
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
	 * Executes the moves command for a given format.
	 *
	 * @param a        argument parser for the subcommand
	 * @param format   output format override
	 * @param cmdLabel command label for diagnostics
	 */
	private static void runMovesCommand(Argv a, MovesFormat format, String cmdLabel) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		boolean san = a.flag(OPT_SAN);
		boolean both = a.flag(OPT_BOTH);
		MovesFormat requested = parseFormat(a.string(OPT_FORMAT), san, both, cmdLabel);
		String fen = CommandSupport.resolveFenArgument(a, cmdLabel, false);
		MovesFormat resolved = format == MovesFormat.DEFAULT ? requested : format;

		if (resolved == MovesFormat.UCI) {
			san = false;
			both = false;
		} else if (resolved == MovesFormat.SAN) {
			san = true;
			both = false;
		} else if (resolved == MovesFormat.BOTH) {
			san = false;
			both = true;
		}

		if (fen == null || fen.isEmpty()) {
			System.err.println(cmdLabel + " requires a FEN (" + MSG_FEN_REQUIRED_HINT + ")");
			System.exit(2);
			return;
		}

		try {
			Position pos = new Position(fen.trim());
			printMoves(pos, san, both);
		} catch (IllegalArgumentException ex) {
			System.err.println(ERR_INVALID_FEN + (ex.getMessage() == null ? "" : ex.getMessage()));
			LogService.error(ex, cmdLabel + ": invalid FEN", "FEN: " + fen);
			if (verbose) {
				ex.printStackTrace(System.err);
			}
			System.exit(3);
		} catch (Exception t) {
			System.err.println("Error: failed to list moves. " + (t.getMessage() == null ? "" : t.getMessage()));
			LogService.error(t, cmdLabel + ": unexpected failure", "FEN: " + fen);
			if (verbose) {
				t.printStackTrace(System.err);
			}
			System.exit(3);
		}
	}

	/**
	 * Resolves output format flags for {@code moves}.
	 *
	 * @param value    optional {@code --format} value.
	 * @param san      whether {@code --san} was present.
	 * @param both     whether {@code --both} was present.
	 * @param cmdLabel command label for diagnostics.
	 * @return resolved output format.
	 */
	private static MovesFormat parseFormat(String value, boolean san, boolean both, String cmdLabel) {
		if (value == null || value.isBlank()) {
			if (both) {
				return MovesFormat.BOTH;
			}
			return san ? MovesFormat.SAN : MovesFormat.UCI;
		}
		if (san || both) {
			System.err.println(cmdLabel + ": use either " + OPT_FORMAT + " or --san/--both, not both");
			System.exit(2);
			return MovesFormat.UCI;
		}
		return switch (value.trim().toLowerCase(Locale.ROOT)) {
			case "uci" -> MovesFormat.UCI;
			case "san" -> MovesFormat.SAN;
			case "both" -> MovesFormat.BOTH;
			default -> {
				System.err.println(cmdLabel + ": unsupported " + OPT_FORMAT + " value: " + value
						+ " (expected uci, san, or both)");
				System.exit(2);
				yield MovesFormat.UCI;
			}
		};
	}

	/**
	 * Prints all legal moves for a position using the selected format.
	 *
	 * @param pos  position to list moves for
	 * @param san  whether to print SAN instead of UCI
	 * @param both whether to print UCI and SAN side-by-side
	 */
	private static void printMoves(Position pos, boolean san, boolean both) {
		MoveList moves = pos.legalMoves();
		for (int i = 0; i < moves.size(); i++) {
			printMoveLine(pos, moves.get(i), san, both);
		}
	}

	/**
	 * Prints a single move line in UCI/SAN as configured.
	 *
	 * @param pos  position used to compute SAN
	 * @param move move to print
	 * @param san  whether to print SAN instead of UCI
	 * @param both whether to print both UCI and SAN
	 */
	private static void printMoveLine(Position pos, short move, boolean san, boolean both) {
		String uci = Move.toString(move);
		if (both) {
			System.out.println(uci + "\t" + safeSan(pos, move));
			return;
		}
		if (san) {
			System.out.println(safeSan(pos, move));
			return;
		}
		System.out.println(uci);
	}
}
