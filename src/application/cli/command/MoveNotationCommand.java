package application.cli.command;

import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;

import application.cli.command.CommandSupport.OutputMode;
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
	 * Current command label for UCI to SAN conversion.
	 */
	private static final String MOVE_TO_SAN = "move to-san";

	/**
	 * Current command label for SAN to UCI conversion.
	 */
	private static final String MOVE_TO_UCI = "move to-uci";

	/**
	 * Utility class; prevent instantiation.
	 */
	private MoveNotationCommand() {
		// utility
	}

	/**
	 * Handles {@code move to-san}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runUciToSan(Argv a) {
		runConvert(a, MoveCommandSupport.MoveFormat.UCI, true, MOVE_TO_SAN);
	}

	/**
	 * Handles {@code move to-uci}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runSanToUci(Argv a) {
		runConvert(a, MoveCommandSupport.MoveFormat.SAN, false, MOVE_TO_UCI);
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
		OutputMode outputMode = CommandSupport.resolveOutputMode(a, cmdLabel);
		MoveCommandSupport.ParsedInput input = MoveCommandSupport.parseInputs(a, cmdLabel, true, verbose);

		if (input.moves.isEmpty()) {
			throw new CommandFailure(cmdLabel + " requires a move (UCI or SAN)", 2);
		}
		if (input.moves.size() > 1) {
			throw new CommandFailure(cmdLabel + " expects a single move; use move play for sequences", 2);
		}

		Position pos = input.position;
		String token = input.moves.get(0);

		try {
			short move = MoveCommandSupport.parseMove(pos, token, format);
			String output = outputSan ? SAN.toAlgebraic(pos, move) : Move.toString(move);
			printOutput(pos, token, output, outputSan, outputMode);
		} catch (IllegalArgumentException ex) {
			LogService.error(ex, cmdLabel + ": invalid move", "FEN: " + pos.toString(), "Move: " + token);
			throw new CommandFailure(cmdLabel + ": " + ex.getMessage(), ex, 3, verbose);
		} catch (Exception ex) {
			LogService.error(ex, cmdLabel + ": unexpected failure", "FEN: " + pos.toString(), "Move: " + token);
			throw new CommandFailure(cmdLabel + ": failed to convert move. "
					+ (ex.getMessage() == null ? "" : ex.getMessage()), ex, 3, verbose);
		}
	}

	/**
	 * Prints conversion output in text, JSON, or JSONL.
	 *
	 * @param pos        source position
	 * @param token      input move token
	 * @param output     converted move
	 * @param outputSan  whether the output field is SAN
	 * @param outputMode selected output mode
	 */
	private static void printOutput(Position pos, String token, String output, boolean outputSan, OutputMode outputMode) {
		if (outputMode == OutputMode.TEXT) {
			System.out.println(output);
			return;
		}
		String field = outputSan ? "san" : "uci";
		String json = "{\"fen\":" + CommandSupport.jsonString(pos.toString())
				+ ",\"input\":" + CommandSupport.jsonString(token)
				+ ",\"" + field + "\":" + CommandSupport.jsonString(output) + "}";
		System.out.println(json);
	}
}
