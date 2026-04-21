package application.cli.command;

import static application.cli.Constants.CMD_SAN_TO_UCI;
import static application.cli.Constants.CMD_UCI_TO_SAN;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;

import chess.core.Move;
import chess.core.Position;
import chess.core.SAN;
import chess.debug.LogService;
import utility.Argv;

/**
 * Implements move notation conversion commands.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class MoveNotationCommand {

	/**
	 * Utility class; prevent instantiation.
	 */
	private MoveNotationCommand() {
		// utility
	}

	/**
	 * Handles {@code uci-to-san}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runUciToSan(Argv a) {
		runConvert(a, MoveCommandSupport.MoveFormat.UCI, true, CMD_UCI_TO_SAN);
	}

	/**
	 * Handles {@code san-to-uci}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runSanToUci(Argv a) {
		runConvert(a, MoveCommandSupport.MoveFormat.SAN, false, CMD_SAN_TO_UCI);
	}

	/**
	 * Runs the convert workflow.
	 * @param a a
	 * @param format format
	 * @param outputSan output san
	 * @param cmdLabel cmd label
	 */
	private static void runConvert(Argv a, MoveCommandSupport.MoveFormat format, boolean outputSan, String cmdLabel) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		MoveCommandSupport.ParsedInput input = MoveCommandSupport.parseInputs(a, cmdLabel, true, verbose);

		if (input.moves.isEmpty()) {
			System.err.println(cmdLabel + " requires a move (UCI or SAN)");
			System.exit(2);
			return;
		}
		if (input.moves.size() > 1) {
			System.err.println(cmdLabel + " expects a single move; use play-line for sequences");
			System.exit(2);
			return;
		}

		Position pos = input.position;
		String token = input.moves.get(0);

		try {
			short move = MoveCommandSupport.parseMove(pos, token, format);
			String output = outputSan ? SAN.toAlgebraic(pos, move) : Move.toString(move);
			System.out.println(output);
		} catch (IllegalArgumentException ex) {
			System.err.println(cmdLabel + ": " + ex.getMessage());
			LogService.error(ex, cmdLabel + ": invalid move", "FEN: " + pos.toString(), "Move: " + token);
			if (verbose) {
				ex.printStackTrace(System.err);
			}
			System.exit(3);
		} catch (Exception ex) {
			System.err.println(cmdLabel + ": failed to convert move. "
					+ (ex.getMessage() == null ? "" : ex.getMessage()));
			LogService.error(ex, cmdLabel + ": unexpected failure", "FEN: " + pos.toString(), "Move: " + token);
			if (verbose) {
				ex.printStackTrace(System.err);
			}
			System.exit(3);
		}
	}
}
