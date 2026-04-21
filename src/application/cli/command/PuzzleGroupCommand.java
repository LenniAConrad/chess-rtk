package application.cli.command;

import static application.cli.Constants.CMD_PUZZLE;

import java.util.List;

import utility.Argv;

/**
 * Implements the grouped {@code puzzle <subcommand>} command.
 *
 * <p>
 * The group contains puzzle mining, PGN conversion, tag generation, and text
 * summarization commands.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class PuzzleGroupCommand {

	/**
	 * Utility class; prevent instantiation.
	 */
	private PuzzleGroupCommand() {
		// utility
	}

	/**
	 * Handles {@code puzzle <subcommand>}.
	 *
	 * @param a argument parser for the grouped command
	 */
	public static void runPuzzle(Argv a) {
		List<String> rest = a.positionals();
		a.ensureConsumed();
		if (rest.isEmpty()) {
			printUsageAndExit();
			return;
		}

		String subcommand = rest.get(0);
		Argv nested = CommandGroupSupport.nestedArgv(rest);
		switch (subcommand) {
			case "mine" -> MineCommand.runMine(nested);
			case "pgn", "to-pgn" -> RecordCommands.runPuzzlesToPgn(nested);
			case "tags" -> PuzzleTagsCommand.runPuzzleTags(nested);
			case "text" -> PuzzleTextCommand.runPuzzleText(nested);
			default -> {
				System.err.println(CMD_PUZZLE + ": unknown subcommand: " + subcommand);
				printUsageAndExit();
			}
		}
	}

	/**
	 * Prints grouped command usage and exits with a usage status.
	 */
	private static void printUsageAndExit() {
		System.err.println("""
				usage: crtk puzzle <subcommand> [options]

				subcommands:
				  mine       Mine chess puzzles
				  pgn        Convert mixed puzzle dumps to PGN games
				  tags       Generate per-move tags for puzzle PVs
				  text       Run T5 over puzzle PVs
				""");
		System.exit(2);
	}
}
