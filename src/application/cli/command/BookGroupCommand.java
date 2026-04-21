package application.cli.command;

import static application.cli.Constants.CMD_BOOK;

import java.util.List;

import utility.Argv;

/**
 * Implements the grouped {@code book <subcommand>} command.
 *
 * <p>
 * The group keeps book rendering, cover rendering, and diagram PDF export in
 * one command family.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class BookGroupCommand {

	/**
	 * Utility class; prevent instantiation.
	 */
	private BookGroupCommand() {
		// utility
	}

	/**
	 * Handles {@code book <subcommand>}.
	 *
	 * @param a argument parser for the grouped command
	 */
	public static void runBook(Argv a) {
		List<String> rest = a.positionals();
		a.ensureConsumed();
		if (rest.isEmpty()) {
			printUsageAndExit();
			return;
		}

		String subcommand = rest.get(0);
		Argv nested = CommandGroupSupport.nestedArgv(rest);
		switch (subcommand) {
			case "render" -> ChessBookCommand.runChessBook(nested);
			case "cover" -> ChessBookCoverCommand.runChessBookCover(nested);
			case "pdf", "diagrams" -> ChessPdfCommand.runChessPdf(nested);
			default -> {
				System.err.println(CMD_BOOK + ": unknown subcommand: " + subcommand);
				printUsageAndExit();
			}
		}
	}

	/**
	 * Prints grouped command usage and exits with a usage status.
	 */
	private static void printUsageAndExit() {
		System.err.println("""
				usage: crtk book <subcommand> [options]

				subcommands:
				  render      Render a chess-book JSON/TOML file to PDF
				  cover       Render a native PDF cover for a chess-book file
				  pdf         Export chess diagrams to a PDF
				""");
		System.exit(2);
	}
}
