package application.cli.command;

import static application.cli.Constants.CMD_HELP_LONG;
import static application.cli.Constants.CMD_HELP_SHORT;
import static application.cli.Constants.ERR_INVALID_FEN;
import static application.cli.Constants.OPT_STDIN;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;

import java.util.ArrayList;
import java.util.List;

import application.cli.command.CommandSupport.OutputMode;
import chess.core.Fen;
import chess.core.Position;
import utility.Argv;

/**
 * Implements FEN normalization and validation CLI helpers.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class FenCommand {

	/**
	 * Current command label for FEN normalization.
	 */
	private static final String FEN_NORMALIZE = "fen normalize";

	/**
	 * Current command label for FEN validation.
	 */
	private static final String FEN_VALIDATE = "fen validate";

	/**
	 * Utility class; prevent instantiation.
	 */
	private FenCommand() {
		// utility
	}

	/**
	 * Handles {@code fen normalize}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runFenNormalize(Argv a) {
		if (a.flag(CMD_HELP_SHORT, CMD_HELP_LONG)) {
			HelpCommand.runHelp(new Argv(new String[] { "fen", "normalize" }));
			return;
		}

		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		boolean stdin = a.flag(OPT_STDIN);
		OutputMode outputMode = CommandSupport.resolveOutputMode(a, FEN_NORMALIZE);
		String fen = CommandSupport.resolveFenArgument(a, FEN_NORMALIZE, false);
		List<String> inputs = resolveFenRows(FEN_NORMALIZE, fen, stdin, verbose);

		List<String> rows = new ArrayList<>();
		for (int i = 0; i < inputs.size(); i++) {
			String input = inputs.get(i);
			try {
				String normalized = new Position(Fen.normalize(input)).toString();
				rows.add(normalizeJson(input, normalized));
				if (outputMode == OutputMode.TEXT) {
					System.out.println(normalized);
				} else if (outputMode == OutputMode.JSONL) {
					System.out.println(rows.get(rows.size() - 1));
				}
			} catch (IllegalArgumentException ex) {
				throw fenError(FEN_NORMALIZE, ex, verbose, i + 1);
			}
		}
		printJsonRows(rows, outputMode);
	}

	/**
	 * Handles {@code fen validate}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runFenValidate(Argv a) {
		if (a.flag(CMD_HELP_SHORT, CMD_HELP_LONG)) {
			HelpCommand.runHelp(new Argv(new String[] { "fen", "validate" }));
			return;
		}

		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		boolean stdin = a.flag(OPT_STDIN);
		OutputMode outputMode = CommandSupport.resolveOutputMode(a, FEN_VALIDATE);
		String fen = CommandSupport.resolveFenArgument(a, FEN_VALIDATE, false);
		List<String> inputs = resolveFenRows(FEN_VALIDATE, fen, stdin, verbose);

		List<String> rows = new ArrayList<>();
		for (int i = 0; i < inputs.size(); i++) {
			String input = inputs.get(i);
			try {
				Position position = new Position(Fen.normalize(input));
				String normalized = position.toString();
				rows.add(validateJson(input, normalized));
				if (outputMode == OutputMode.TEXT) {
					System.out.println("valid\t" + normalized);
				} else if (outputMode == OutputMode.JSONL) {
					System.out.println(rows.get(rows.size() - 1));
				}
			} catch (IllegalArgumentException ex) {
				throw fenError(FEN_VALIDATE, ex, verbose, i + 1);
			}
		}
		printJsonRows(rows, outputMode);
	}

	/**
	 * Resolves FEN rows from CLI args or stdin.
	 *
	 * @param cmd     command label
	 * @param fen     single FEN argument
	 * @param stdin   whether stdin was requested
	 * @param verbose whether to include stack traces for stdin read failures
	 * @return input FEN rows
	 */
	private static List<String> resolveFenRows(String cmd, String fen, boolean stdin, boolean verbose) {
		if (stdin) {
			if (fen != null && !fen.isBlank()) {
				throw new CommandFailure(cmd + ": provide either " + OPT_STDIN + " or a FEN, not both", 2);
			}
			return CommandSupport.readStdinLines(cmd, verbose);
		}
		if (fen == null || fen.isBlank()) {
			throw new CommandFailure(cmd + " requires a FEN", 2);
		}
		return List.of(fen);
	}

	/**
	 * Builds the JSON object for {@code fen normalize}.
	 *
	 * @param input      original input FEN
	 * @param normalized normalized FEN
	 * @return JSON object text
	 */
	private static String normalizeJson(String input, String normalized) {
		return "{\"input\":" + CommandSupport.jsonString(input)
				+ ",\"fen\":" + CommandSupport.jsonString(normalized) + "}";
	}

	/**
	 * Builds the JSON object for {@code fen validate}.
	 *
	 * @param input      original input FEN
	 * @param normalized normalized FEN
	 * @return JSON object text
	 */
	private static String validateJson(String input, String normalized) {
		return "{\"valid\":true,\"input\":" + CommandSupport.jsonString(input)
				+ ",\"fen\":" + CommandSupport.jsonString(normalized) + "}";
	}

	/**
	 * Prints accumulated JSON rows for {@code --json}.
	 *
	 * @param rows       JSON object rows
	 * @param outputMode selected output mode
	 */
	private static void printJsonRows(List<String> rows, OutputMode outputMode) {
		if (outputMode != OutputMode.JSON) {
			return;
		}
		if (rows.size() == 1) {
			System.out.println(rows.get(0));
			return;
		}
		System.out.println("[" + String.join(",", rows) + "]");
	}

	/**
	 * Creates a structured invalid-FEN failure.
	 *
	 * @param cmd     command label
	 * @param ex      parsing failure
	 * @param verbose whether to print a stack trace
	 * @param row     1-based input row
	 * @return command failure
	 */
	private static CommandFailure fenError(String cmd, IllegalArgumentException ex, boolean verbose, int row) {
		String suffix = ex.getMessage() == null ? "" : ex.getMessage();
		String prefix = row > 1 ? cmd + ": input row " + row + ": " : "";
		return new CommandFailure(prefix + ERR_INVALID_FEN + suffix, ex, 3, verbose);
	}
}
