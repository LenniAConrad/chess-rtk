package application.cli.command;

import static application.cli.Constants.CMD_HELP_LONG;
import static application.cli.Constants.CMD_HELP_SHORT;
import static application.cli.Constants.ERR_INVALID_FEN;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;

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
		String fen = CommandSupport.resolveFenArgument(a, FEN_NORMALIZE, false);
		if (fen == null || fen.isBlank()) {
			System.err.println(FEN_NORMALIZE + " requires a FEN");
			System.exit(2);
			return;
		}

		try {
			System.out.println(new Position(Fen.normalize(fen)).toString());
		} catch (IllegalArgumentException ex) {
			printFenError(ex, verbose);
		}
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
		String fen = CommandSupport.resolveFenArgument(a, FEN_VALIDATE, false);
		if (fen == null || fen.isBlank()) {
			System.err.println(FEN_VALIDATE + " requires a FEN");
			System.exit(2);
			return;
		}

		try {
			Position position = new Position(Fen.normalize(fen));
			System.out.println("valid\t" + position.toString());
		} catch (IllegalArgumentException ex) {
			printFenError(ex, verbose);
		}
	}

	/**
	 * Prints a consistent invalid-FEN diagnostic and exits.
	 *
	 * @param ex      parsing failure.
	 * @param verbose whether to print a stack trace.
	 */
	private static void printFenError(IllegalArgumentException ex, boolean verbose) {
		System.err.println(ERR_INVALID_FEN + (ex.getMessage() == null ? "" : ex.getMessage()));
		if (verbose) {
			ex.printStackTrace(System.err);
		}
		System.exit(3);
	}
}
