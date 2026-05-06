package application.cli.command;

import static application.cli.Constants.OPT_INTERMEDIATE;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;

import java.util.ArrayList;
import java.util.List;

import application.cli.command.CommandSupport.OutputMode;
import chess.core.Position;
import chess.debug.LogService;
import utility.Argv;

/**
 * Implements line-based CLI commands.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
@SuppressWarnings({"java:S1192", "java:S3776"})
public final class LineCommand {

	/**
	 * Current command label for applying one move.
	 */
	private static final String MOVE_AFTER = "move after";

	/**
	 * Current command label for applying a move sequence.
	 */
	private static final String MOVE_PLAY = "move play";

	/**
	 * Utility class; prevent instantiation.
	 */
	private LineCommand() {
		// utility
	}

	/**
	 * Handles {@code move after}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runFenAfter(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		OutputMode outputMode = CommandSupport.resolveOutputMode(a, MOVE_AFTER);
		MoveCommandSupport.ParsedInput input = MoveCommandSupport.parseInputs(a, MOVE_AFTER, true, verbose);

		if (input.moves.isEmpty()) {
			throw new CommandFailure(MOVE_AFTER + " requires a move (UCI or SAN)", 2);
		}
		if (input.moves.size() > 1) {
			throw new CommandFailure(MOVE_AFTER + " expects a single move; use move play for sequences", 2);
		}

		Position pos = input.position;
		String startFen = pos.toString();
		String token = input.moves.get(0);

		try {
			short move = MoveCommandSupport.parseMove(pos, token, MoveCommandSupport.MoveFormat.AUTO);
			pos.play(move);
			printAfter(startFen, token, pos.toString(), outputMode);
		} catch (IllegalArgumentException ex) {
			LogService.error(ex, MOVE_AFTER + ": invalid move", "FEN: " + pos.toString(), "Move: " + token);
			throw new CommandFailure(MOVE_AFTER + ": " + ex.getMessage(), ex, 3, verbose);
		} catch (Exception ex) {
			LogService.error(ex, MOVE_AFTER + ": unexpected failure", "FEN: " + pos.toString(), "Move: " + token);
			throw new CommandFailure(MOVE_AFTER + ": failed to apply move. "
					+ (ex.getMessage() == null ? "" : ex.getMessage()), ex, 3, verbose);
		}
	}

	/**
	 * Handles {@code move play}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runPlayLine(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		OutputMode outputMode = CommandSupport.resolveOutputMode(a, MOVE_PLAY);
		boolean intermediate = a.flag(OPT_INTERMEDIATE);
		MoveCommandSupport.ParsedInput input = MoveCommandSupport.parseInputs(a, MOVE_PLAY, true, verbose);

		if (input.moves.isEmpty()) {
			throw new CommandFailure(MOVE_PLAY + " requires at least one move (UCI or SAN)", 2);
		}

		Position pos = input.position;
		String startFen = pos.toString();
		List<String> intermediateFens = new ArrayList<>();

		for (int i = 0; i < input.moves.size(); i++) {
			String token = input.moves.get(i);
			int ply = i + 1;
			try {
				short move = MoveCommandSupport.parseMove(pos, token, MoveCommandSupport.MoveFormat.AUTO);
				pos.play(move);
				String result = pos.toString();
				intermediateFens.add(result);
				if (intermediate) {
					if (outputMode == OutputMode.TEXT) {
						System.out.println(result);
					} else if (outputMode == OutputMode.JSONL) {
						System.out.println(playStepJson(ply, token, result));
					}
				}
			} catch (IllegalArgumentException ex) {
				LogService.error(ex, MOVE_PLAY + ": invalid move", "Ply: " + ply,
						"FEN: " + pos.toString(), "Move: " + token);
				throw new CommandFailure(MOVE_PLAY + ": invalid move at ply " + ply + ": " + token
						+ " (" + ex.getMessage() + ")", ex, 3, verbose);
			} catch (Exception ex) {
				LogService.error(ex, MOVE_PLAY + ": unexpected failure", "Ply: " + ply,
						"FEN: " + pos.toString(), "Move: " + token);
				throw new CommandFailure(MOVE_PLAY + ": failed to apply move at ply " + ply + ": " + token
						+ ". " + (ex.getMessage() == null ? "" : ex.getMessage()), ex, 3, verbose);
			}
		}

		if (outputMode == OutputMode.JSON) {
			System.out.println(playJson(startFen, input.moves, pos.toString(), intermediateFens, intermediate));
		} else if (!intermediate) {
			if (outputMode == OutputMode.JSONL) {
				System.out.println(playJson(startFen, input.moves, pos.toString(), List.of(), false));
			} else {
				System.out.println(pos.toString());
			}
		}
	}

	/**
	 * Prints one-move application output.
	 *
	 * @param startFen   source FEN
	 * @param move       input move
	 * @param resultFen  resulting FEN
	 * @param outputMode selected output mode
	 */
	private static void printAfter(String startFen, String move, String resultFen, OutputMode outputMode) {
		if (outputMode == OutputMode.TEXT) {
			System.out.println(resultFen);
			return;
		}
		System.out.println("{\"fen\":" + CommandSupport.jsonString(startFen)
				+ ",\"move\":" + CommandSupport.jsonString(move)
				+ ",\"result\":" + CommandSupport.jsonString(resultFen) + "}");
	}

	/**
	 * Builds a JSON object for one intermediate move-play row.
	 *
	 * @param ply       1-based ply
	 * @param move      input move
	 * @param resultFen resulting FEN
	 * @return JSON object text
	 */
	private static String playStepJson(int ply, String move, String resultFen) {
		return "{\"ply\":" + ply
				+ ",\"move\":" + CommandSupport.jsonString(move)
				+ ",\"result\":" + CommandSupport.jsonString(resultFen) + "}";
	}

	/**
	 * Builds a JSON object for a played move line.
	 *
	 * @param startFen         source FEN
	 * @param moves            input moves
	 * @param resultFen        final FEN
	 * @param intermediateFens intermediate FENs
	 * @param includeSteps     whether to include intermediate positions
	 * @return JSON object text
	 */
	private static String playJson(
			String startFen,
			List<String> moves,
			String resultFen,
			List<String> intermediateFens,
			boolean includeSteps) {
		StringBuilder sb = new StringBuilder(160);
		sb.append("{\"fen\":").append(CommandSupport.jsonString(startFen))
				.append(",\"moves\":").append(jsonStringArray(moves))
				.append(",\"result\":").append(CommandSupport.jsonString(resultFen));
		if (includeSteps) {
			sb.append(",\"intermediate\":").append(jsonStringArray(intermediateFens));
		}
		return sb.append('}').toString();
	}

	/**
	 * Builds a JSON string array.
	 *
	 * @param values string values
	 * @return JSON array text
	 */
	private static String jsonStringArray(List<String> values) {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < values.size(); i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append(CommandSupport.jsonString(values.get(i)));
		}
		return sb.append(']').toString();
	}
}
