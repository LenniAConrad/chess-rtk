package application.cli.command;

import static application.cli.Constants.CMD_CHESS960;
import static application.cli.Constants.CMD_HELP_LONG;
import static application.cli.Constants.CMD_HELP_SHORT;
import static application.cli.Constants.OPT_ALL;
import static application.cli.Constants.OPT_COUNT;
import static application.cli.Constants.OPT_FORMAT;
import static application.cli.Constants.OPT_INDEX;
import static application.cli.Constants.OPT_RANDOM;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import chess.core.Setup;
import utility.Argv;

/**
 * Implements direct Chess960 start-position lookup and export.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Chess960Command {

	/**
	 * Number of Chess960 positions.
	 */
	private static final int CHESS960_COUNT = 960;

	/**
	 * Utility class; prevent instantiation.
	 */
	private Chess960Command() {
		// utility
	}

	/**
	 * Supported Chess960 output formats.
	 */
	private enum OutputFormat {
		/**
		 * Print FEN only.
		 */
		FEN,
		/**
		 * Print back-rank layout only.
		 */
		LAYOUT,
		/**
		 * Print index, layout, and FEN.
		 */
		BOTH
	}

	/**
	 * Handles {@code chess960}.
	 *
	 * @param a argument parser for the subcommand.
	 */
	public static void runChess960(Argv a) {
		if (a.flag(CMD_HELP_SHORT, CMD_HELP_LONG)) {
			HelpCommand.runHelp(new Argv(new String[] { CMD_CHESS960 }));
			return;
		}
		try {
			runChess960Unchecked(a);
		} catch (IllegalArgumentException ex) {
			System.err.println(ex.getMessage());
			System.exit(2);
		}
	}

	/**
	 * Runs {@code chess960} after help handling.
	 *
	 * @param a argument parser for the subcommand.
	 */
	private static void runChess960Unchecked(Argv a) {
		boolean all = a.flag(OPT_ALL);
		boolean random = a.flag(OPT_RANDOM);
		Integer index = a.integer(OPT_INDEX);
		Integer count = a.integer(OPT_COUNT);
		OutputFormat format = parseFormat(a.string(OPT_FORMAT));
		List<String> rest = a.positionals();
		a.ensureConsumed();

		if (index == null && rest.size() == 1) {
			index = parseIndex(rest.get(0));
		} else if (!rest.isEmpty()) {
			failUsage("use at most one positional Chess960 index");
		}

		int modes = (all ? 1 : 0) + (random ? 1 : 0) + (index != null ? 1 : 0);
		if (modes != 1) {
			failUsage("choose exactly one of --index N, --random, --all, or a positional index");
		}

		if (all) {
			printRange(0, CHESS960_COUNT, format);
			return;
		}
		if (random) {
			int samples = count == null ? 1 : count;
			if (samples <= 0) {
				failUsage("--count must be positive");
			}
			for (int i = 0; i < samples; i++) {
				int sampled = ThreadLocalRandom.current().nextInt(CHESS960_COUNT);
				printPosition(sampled, format);
			}
			return;
		}

		printPosition(validateIndex(index), format);
	}

	/**
	 * Prints a contiguous range of Chess960 positions.
	 *
	 * @param start  first index, inclusive.
	 * @param end    last index, exclusive.
	 * @param format output format.
	 */
	private static void printRange(int start, int end, OutputFormat format) {
		for (int index = start; index < end; index++) {
			printPosition(index, format);
		}
	}

	/**
	 * Prints one Chess960 position.
	 *
	 * @param index  Chess960 index.
	 * @param format output format.
	 */
	private static void printPosition(int index, OutputFormat format) {
		String fen = Setup.getChess960ByIndex(index).toString();
		String layout = layout(fen);
		switch (format) {
			case LAYOUT -> System.out.println(layout);
			case BOTH -> System.out.println(index + "\t" + layout + "\t" + fen);
			case FEN -> System.out.println(fen);
		}
	}

	/**
	 * Parses a user-provided output format.
	 *
	 * @param value raw value.
	 * @return parsed output format.
	 */
	private static OutputFormat parseFormat(String value) {
		if (value == null || value.isBlank()) {
			return OutputFormat.FEN;
		}
		return switch (value.trim().toLowerCase(Locale.ROOT)) {
			case "fen" -> OutputFormat.FEN;
			case "layout", "rank" -> OutputFormat.LAYOUT;
			case "both", "all" -> OutputFormat.BOTH;
			default -> throw new IllegalArgumentException("unsupported chess960 format: " + value);
		};
	}

	/**
	 * Parses a Chess960 index.
	 *
	 * @param value raw index token.
	 * @return parsed index.
	 */
	private static int parseIndex(String value) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException ex) {
			throw new IllegalArgumentException("invalid Chess960 index: " + value, ex);
		}
	}

	/**
	 * Validates the Chess960 index range.
	 *
	 * @param index candidate index.
	 * @return the validated index.
	 */
	private static int validateIndex(Integer index) {
		if (index == null || index < 0 || index >= CHESS960_COUNT) {
			return failUsage("--index must be between 0 and 959");
		}
		return index;
	}

	/**
	 * Extracts the white back-rank layout from a Chess960 FEN.
	 *
	 * @param fen Chess960 FEN.
	 * @return uppercase layout.
	 */
	private static String layout(String fen) {
		String board = fen.substring(0, fen.indexOf(' '));
		return board.substring(board.lastIndexOf('/') + 1);
	}

	/**
	 * Raises a usage error.
	 *
	 * @param message diagnostic text.
	 */
	private static int failUsage(String message) {
		throw new IllegalArgumentException(CMD_CHESS960 + ": " + message);
	}
}
