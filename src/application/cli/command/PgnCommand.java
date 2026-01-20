package application.cli.command;

import static application.cli.Constants.CMD_PGN_TO_FENS;
import static application.cli.Constants.OPT_INPUT;
import static application.cli.Constants.OPT_INPUT_SHORT;
import static application.cli.Constants.OPT_MAINLINE;
import static application.cli.Constants.OPT_OUTPUT;
import static application.cli.Constants.OPT_OUTPUT_SHORT;
import static application.cli.Constants.OPT_PAIRS;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;
import static application.cli.PathOps.deriveOutputPath;
import static application.cli.PgnOps.openWriterOrExit;
import static application.cli.PgnOps.readPgnOrExit;
import static application.cli.PgnOps.writePgnFens;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import utility.Argv;

/**
 * Implements PGN-related CLI commands.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class PgnCommand {

	/**
	 * Utility class; prevent instantiation.
	 */
	private PgnCommand() {
		// utility
	}

	/**
	 * Handles {@code pgn-to-fens}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runPgnToFens(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		boolean mainline = a.flag(OPT_MAINLINE);
		boolean pairs = a.flag(OPT_PAIRS);
		Path input = a.pathRequired(OPT_INPUT, OPT_INPUT_SHORT);
		Path output = a.path(OPT_OUTPUT, OPT_OUTPUT_SHORT);
		a.ensureConsumed();

		if (output == null) {
			output = deriveOutputPath(input, ".txt");
		}

		List<chess.struct.Game> games = readPgnOrExit(input, verbose, CMD_PGN_TO_FENS);
		if (games == null) {
			return;
		}

		try (BufferedWriter writer = openWriterOrExit(output, verbose, CMD_PGN_TO_FENS)) {
			if (writer == null) {
				return;
			}
			long lines = writePgnFens(games, writer, mainline, pairs);
			System.out.printf("pgn-to-fens wrote %d lines to %s%n", lines, output.toAbsolutePath());
		} catch (IOException ex) {
			System.err.println("pgn-to-fens: failed to write output: " + ex.getMessage());
			if (verbose) {
				ex.printStackTrace(System.err);
			}
			System.exit(2);
		}
	}
}
