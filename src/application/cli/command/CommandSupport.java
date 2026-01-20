package application.cli.command;

import static application.cli.Constants.MSG_FEN_REQUIRED_HINT;
import static application.cli.Constants.OPT_FEN;
import static application.cli.Constants.OPT_INPUT;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import chess.io.Reader;
import utility.Argv;

/**
 * Shared helpers for CLI command implementations.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class CommandSupport {

	/**
	 * Utility class; prevent instantiation.
	 */
	private CommandSupport() {
		// utility
	}

	/**
	 * Resolves a FEN either from {@code --fen} or remaining positionals.
	 *
	 * @param a argument parser for the current subcommand
	 * @return FEN string or {@code null} when none provided
	 */
	public static String resolveFenArgument(Argv a) {
		String fen = a.string(OPT_FEN);
		List<String> rest = a.positionals();
		if (fen == null && !rest.isEmpty()) {
			fen = String.join(" ", rest);
		}
		a.ensureConsumed();
		return fen;
	}

	/**
	 * Resolves FEN inputs from either a file or a single CLI FEN string.
	 *
	 * @param cmd   command label used in diagnostics
	 * @param input optional input file path
	 * @param fen   optional single FEN string
	 * @return list of FEN strings to process
	 */
	public static List<String> resolveFenInputs(String cmd, Path input, String fen) {
		if (input != null && fen != null) {
			System.err.println(cmd + ": provide either " + OPT_INPUT + " or a single FEN, not both");
			System.exit(2);
			return List.of();
		}
		if (input != null) {
			try {
				List<String> fens = Reader.readFenList(input);
				if (fens.isEmpty()) {
					System.err.println(cmd + ": input file has no FENs");
					System.exit(2);
				}
				return fens;
			} catch (IOException ex) {
				System.err.println(cmd + ": failed to read input: " + ex.getMessage());
				System.exit(2);
				return List.of();
			}
		}
		if (fen == null || fen.isEmpty()) {
			System.err.println(cmd + " requires a FEN (" + MSG_FEN_REQUIRED_HINT + ")");
			System.exit(2);
			return List.of();
		}
		return List.of(fen);
	}

	/**
	 * Returns {@code value} when non-null/non-empty, otherwise the default.
	 *
	 * @param value candidate value
	 * @param def   default value
	 * @return chosen value
	 */
	public static String optional(String value, String def) {
		return (value == null || value.isEmpty()) ? def : value;
	}

	/**
	 * Returns {@code value} when non-null, otherwise the default.
	 *
	 * @param value candidate value
	 * @param def   default value
	 * @return chosen value
	 */
	public static long optional(Long value, long def) {
		return (value == null) ? def : value;
	}

	/**
	 * Converts an optional duration to milliseconds with a default.
	 *
	 * @param value candidate duration
	 * @param defMs default duration in milliseconds
	 * @return chosen duration in milliseconds
	 */
	public static long optionalDurationMs(Duration value, long defMs) {
		return (value == null) ? defMs : value.toMillis();
	}

	/**
	 * Formats counts using {@link Locale#ROOT} for stable output.
	 *
	 * @param value count to format
	 * @return formatted count string
	 */
	public static String formatCount(long value) {
		return String.format(Locale.ROOT, "%,d", value);
	}
}
