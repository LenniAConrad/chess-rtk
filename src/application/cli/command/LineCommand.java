package application.cli.command;

import static application.cli.Constants.CMD_FEN_AFTER;
import static application.cli.Constants.CMD_PLAY_LINE;
import static application.cli.Constants.OPT_INTERMEDIATE;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;

import chess.core.Position;
import chess.debug.LogService;
import utility.Argv;

/**
 * Implements line-based CLI commands.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class LineCommand {

	/**
	 * Utility class; prevent instantiation.
	 */
	private LineCommand() {
		// utility
	}

	/**
	 * Handles {@code fen-after}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runFenAfter(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		MoveCommandSupport.ParsedInput input = MoveCommandSupport.parseInputs(a, CMD_FEN_AFTER, true, verbose);

		if (input.moves.isEmpty()) {
			System.err.println(CMD_FEN_AFTER + " requires a move (UCI or SAN)");
			System.exit(2);
			return;
		}
		if (input.moves.size() > 1) {
			System.err.println(CMD_FEN_AFTER + " expects a single move; use play-line for sequences");
			System.exit(2);
			return;
		}

		Position pos = input.position;
		String token = input.moves.get(0);

		try {
			short move = MoveCommandSupport.parseMove(pos, token, MoveCommandSupport.MoveFormat.AUTO);
			pos.play(move);
			System.out.println(pos.toString());
		} catch (IllegalArgumentException ex) {
			System.err.println(CMD_FEN_AFTER + ": " + ex.getMessage());
			LogService.error(ex, CMD_FEN_AFTER + ": invalid move", "FEN: " + pos.toString(), "Move: " + token);
			if (verbose) {
				ex.printStackTrace(System.err);
			}
			System.exit(3);
		} catch (Exception ex) {
			System.err.println(CMD_FEN_AFTER + ": failed to apply move. "
					+ (ex.getMessage() == null ? "" : ex.getMessage()));
			LogService.error(ex, CMD_FEN_AFTER + ": unexpected failure", "FEN: " + pos.toString(), "Move: " + token);
			if (verbose) {
				ex.printStackTrace(System.err);
			}
			System.exit(3);
		}
	}

	/**
	 * Handles {@code play-line}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runPlayLine(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		boolean intermediate = a.flag(OPT_INTERMEDIATE);
		MoveCommandSupport.ParsedInput input = MoveCommandSupport.parseInputs(a, CMD_PLAY_LINE, true, verbose);

		if (input.moves.isEmpty()) {
			System.err.println(CMD_PLAY_LINE + " requires at least one move (UCI or SAN)");
			System.exit(2);
			return;
		}

		Position pos = input.position;

		for (int i = 0; i < input.moves.size(); i++) {
			String token = input.moves.get(i);
			int ply = i + 1;
			try {
				short move = MoveCommandSupport.parseMove(pos, token, MoveCommandSupport.MoveFormat.AUTO);
				pos.play(move);
				if (intermediate) {
					System.out.println(pos.toString());
				}
			} catch (IllegalArgumentException ex) {
				System.err.println(CMD_PLAY_LINE + ": invalid move at ply " + ply + ": " + token
						+ " (" + ex.getMessage() + ")");
				LogService.error(ex, CMD_PLAY_LINE + ": invalid move", "Ply: " + ply,
						"FEN: " + pos.toString(), "Move: " + token);
				if (verbose) {
					ex.printStackTrace(System.err);
				}
				System.exit(3);
			} catch (Exception ex) {
				System.err.println(CMD_PLAY_LINE + ": failed to apply move at ply " + ply + ": " + token
						+ ". " + (ex.getMessage() == null ? "" : ex.getMessage()));
				LogService.error(ex, CMD_PLAY_LINE + ": unexpected failure", "Ply: " + ply,
						"FEN: " + pos.toString(), "Move: " + token);
				if (verbose) {
					ex.printStackTrace(System.err);
				}
				System.exit(3);
			}
		}

		if (!intermediate) {
			System.out.println(pos.toString());
		}
	}
}
