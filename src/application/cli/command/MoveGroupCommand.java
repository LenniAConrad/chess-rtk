package application.cli.command;

import static application.cli.Constants.CMD_MOVE;

import java.util.List;

import utility.Argv;

/**
 * Implements the grouped {@code move <subcommand>} command.
 *
 * <p>
 * The group contains move listing, notation conversion, and move application
 * helpers.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class MoveGroupCommand {

	/**
	 * Utility class; prevent instantiation.
	 */
	private MoveGroupCommand() {
		// utility
	}

	/**
	 * Handles {@code move <subcommand>}.
	 *
	 * @param a argument parser for the grouped command
	 */
	public static void runMove(Argv a) {
		List<String> rest = a.positionals();
		a.ensureConsumed();
		if (rest.isEmpty()) {
			printUsageAndExit();
			return;
		}

		String subcommand = rest.get(0);
		Argv nested = CommandGroupSupport.nestedArgv(rest);
		switch (subcommand) {
			case "list" -> MovesCommand.runMoves(nested);
			case "uci", "list-uci" -> MovesCommand.runMovesUci(nested);
			case "san", "list-san" -> MovesCommand.runMovesSan(nested);
			case "both", "list-both" -> MovesCommand.runMovesBoth(nested);
			case "to-san" -> MoveNotationCommand.runUciToSan(nested);
			case "to-uci" -> MoveNotationCommand.runSanToUci(nested);
			case "after" -> LineCommand.runFenAfter(nested);
			case "play", "line" -> LineCommand.runPlayLine(nested);
			default -> {
				System.err.println(CMD_MOVE + ": unknown subcommand: " + subcommand);
				printUsageAndExit();
			}
		}
	}

	/**
	 * Prints grouped command usage and exits with a usage status.
	 */
	private static void printUsageAndExit() {
		System.err.println("""
				usage: crtk move <subcommand> [options]

				subcommands:
				  list          List legal moves for a FEN
				  uci           List legal moves in UCI
				  san           List legal moves in SAN
				  both          List legal moves in UCI and SAN
				  to-san        Convert one UCI move to SAN
				  to-uci        Convert one SAN move to UCI
				  after         Apply one move and print the resulting FEN
				  play          Apply a move line and print the resulting FEN
				""");
		System.exit(2);
	}
}
