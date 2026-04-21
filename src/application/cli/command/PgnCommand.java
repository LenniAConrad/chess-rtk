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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import application.console.Bar;
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

		Bar readBar = fileProgressBar(input, CMD_PGN_TO_FENS + " read");
		List<chess.struct.Game> games;
		try {
			games = readPgnOrExit(
					input,
					verbose,
					CMD_PGN_TO_FENS,
					readBar == null ? null : readBar::set);
		} finally {
			finish(readBar);
		}
		if (games == null) {
			return;
		}

		try (BufferedWriter writer = openWriterOrExit(output, verbose, CMD_PGN_TO_FENS)) {
			if (writer == null) {
				return;
			}
			Bar bar = games.size() > 1 ? new Bar(games.size(), CMD_PGN_TO_FENS + " write", false, System.err) : null;
			long lines = writePgnFens(games, writer, mainline, pairs, bar == null ? null : bar::step);
			finish(bar);
			System.out.printf("pgn-to-fens wrote %d lines to %s%n", lines, output.toAbsolutePath());
		} catch (IOException ex) {
			System.err.println("pgn-to-fens: failed to write output: " + ex.getMessage());
			if (verbose) {
				ex.printStackTrace(System.err);
			}
			System.exit(2);
		}
	}

	/**
	 * Creates a byte-count progress bar for an input file when its size is known.
	 */
	private static Bar fileProgressBar(Path input, String label) {
		try {
			long size = Files.size(input);
			return size > 0L ? new Bar(size, label, false, System.err) : null;
		} catch (IOException ex) {
			return null;
		}
	}

	/**
	 * Finishes a progress bar if one was created.
	 */
	private static void finish(Bar bar) {
		if (bar != null) {
			bar.finish();
		}
	}
}
