package application.cli.command;

import static application.cli.Constants.CMD_CHESS960;
import static application.cli.Constants.CMD_DISPLAY;
import static application.cli.Constants.CMD_FEN;
import static application.cli.Constants.CMD_PRINT;
import static application.cli.Constants.CMD_RENDER;
import static application.cli.Constants.CMD_TAGS;

import java.util.List;

import utility.Argv;

/**
 * Implements the grouped {@code fen <subcommand>} command.
 *
 * <p>
 * The group keeps FEN validation, generation, transformation, and board-view
 * operations under one discoverable entry point.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class FenGroupCommand {

	/**
	 * Utility class; prevent instantiation.
	 */
	private FenGroupCommand() {
		// utility
	}

	/**
	 * Handles {@code fen <subcommand>}.
	 *
	 * @param a argument parser for the grouped command
	 */
	public static void runFen(Argv a) {
		List<String> rest = a.positionals();
		a.ensureConsumed();
		if (rest.isEmpty()) {
			printUsageAndExit();
			return;
		}

		String subcommand = rest.get(0);
		Argv nested = CommandGroupSupport.nestedArgv(rest);
		switch (subcommand) {
			case "normalize", "normalise" -> FenCommand.runFenNormalize(nested);
			case "validate" -> FenCommand.runFenValidate(nested);
			case "after" -> LineCommand.runFenAfter(nested);
			case "line", "play" -> LineCommand.runPlayLine(nested);
			case "generate", "gen" -> GenFensCommand.runGenerateFens(nested);
			case "pgn", "from-pgn" -> PgnCommand.runPgnToFens(nested);
			case "960", CMD_CHESS960 -> Chess960Command.runChess960(nested);
			case CMD_PRINT -> PositionViewCommand.runPrint(nested);
			case CMD_DISPLAY -> PositionViewCommand.runDisplay(nested);
			case CMD_RENDER -> PositionViewCommand.runRenderImage(nested);
			case CMD_TAGS -> TagsCommand.runTags(nested);
			case "text" -> TagTextCommand.runTagText(nested);
			default -> {
				System.err.println(CMD_FEN + ": unknown subcommand: " + subcommand);
				printUsageAndExit();
			}
		}
	}

	/**
	 * Prints grouped command usage and exits with a usage status.
	 */
	private static void printUsageAndExit() {
		System.err.println("""
				usage: crtk fen <subcommand> [options]

				subcommands:
				  normalize      Normalize and validate a FEN
				  validate       Validate a FEN
				  after          Apply one move and print the resulting FEN
				  line           Apply a move line and print the resulting FEN
				  generate       Generate random legal FEN shards
				  pgn            Convert PGN games to FEN lists
				  chess960       Print Chess960 starting positions
				  print          Pretty-print a FEN
				  display        Render a board image in a window
				  render         Save a board image to disk
				  tags           Generate tags for FENs, PGNs, or variations
				  text           Summarize position tags with T5
				""");
		System.exit(2);
	}
}
