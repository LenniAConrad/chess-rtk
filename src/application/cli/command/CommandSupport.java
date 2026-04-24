package application.cli.command;

import static application.cli.Constants.ERR_INVALID_FEN;
import static application.cli.Constants.MSG_FEN_REQUIRED_HINT;
import static application.cli.Constants.OPT_FEN;
import static application.cli.Constants.OPT_INPUT;
import static application.cli.Constants.OPT_RANDOMPOS;
import static application.cli.Constants.OPT_STARTPOS;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import application.console.Bar;
import chess.core.Position;
import chess.core.Setup;
import chess.debug.LogService;
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
	 * Number of attempts used when sampling a random playable position.
	 */
	private static final int RANDOM_POSITION_SAMPLE_ATTEMPTS = 32;

	/**
	 * Utility class; prevent instantiation.
	 */
	private CommandSupport() {
		// utility
	}

	/**
	 * Resolves one position selector from {@code --fen}, {@code --startpos},
	 * {@code --randompos}, or positional FEN input.
	 *
	 * @param a argument parser for the current subcommand
	 * @param cmd command label for diagnostics
	 * @param defaultToStart whether to default to the standard start when no selector
	 *        is provided
	 * @return resolved FEN string or {@code null} when no selector was provided
	 */
	public static String resolveFenArgument(Argv a, String cmd, boolean defaultToStart) {
		boolean startPos = a.flag(OPT_STARTPOS);
		boolean randomPos = a.flag(OPT_RANDOMPOS);
		String fen = a.string(OPT_FEN);
		List<String> rest = a.positionals();
		a.ensureConsumed();
		return resolveSelectedFen(cmd, fen, rest, startPos, randomPos, defaultToStart);
	}

	/**
	 * Resolves and parses one position selector from the current command tail.
	 *
	 * @param a argument parser for the current subcommand
	 * @param cmd command label for diagnostics
	 * @param defaultToStart whether to default to the standard start when no selector
	 *        is provided
	 * @param verbose whether to print stack traces on failure
	 * @return resolved position, or {@code null} when none was provided
	 */
	public static Position resolvePositionArgument(Argv a, String cmd, boolean defaultToStart, boolean verbose) {
		boolean startPos = a.flag(OPT_STARTPOS);
		boolean randomPos = a.flag(OPT_RANDOMPOS);
		String fen = a.string(OPT_FEN);
		List<String> rest = a.positionals();
		a.ensureConsumed();
		return resolveSelectedPosition(cmd, fen, rest, startPos, randomPos, defaultToStart, verbose);
	}

	/**
	 * Resolves one selected FEN from flags and/or positional tokens.
	 *
	 * @param cmd command label for diagnostics
	 * @param fen optional FEN from {@code --fen}
	 * @param rest optional positional tokens that may contain a FEN
	 * @param startPos whether {@code --startpos} was provided
	 * @param randomPos whether {@code --randompos} was provided
	 * @param defaultToStart whether to fall back to the standard start
	 * @return resolved FEN string or {@code null} when none was provided
	 */
	public static String resolveSelectedFen(
			String cmd,
			String fen,
			List<String> rest,
			boolean startPos,
			boolean randomPos,
			boolean defaultToStart) {
		String resolvedFen = resolvePositionalFen(fen, rest);
		validateSinglePositionSelector(cmd, resolvedFen, startPos, randomPos);
		if (randomPos) {
			return randomPlayablePosition().toString();
		}
		if (startPos || (defaultToStart && isBlank(resolvedFen))) {
			return Setup.getStandardStartFEN();
		}
		return resolvedFen;
	}

	/**
	 * Resolves and parses one selected position from flags and/or positional tokens.
	 *
	 * @param cmd command label for diagnostics
	 * @param fen optional FEN from {@code --fen}
	 * @param rest optional positional tokens that may contain a FEN
	 * @param startPos whether {@code --startpos} was provided
	 * @param randomPos whether {@code --randompos} was provided
	 * @param defaultToStart whether to fall back to the standard start
	 * @param verbose whether to print stack traces on failure
	 * @return resolved position, or {@code null} when no selector was provided
	 */
	public static Position resolveSelectedPosition(
			String cmd,
			String fen,
			List<String> rest,
			boolean startPos,
			boolean randomPos,
			boolean defaultToStart,
			boolean verbose) {
		String resolvedFen = resolveSelectedFen(cmd, fen, rest, startPos, randomPos, defaultToStart);
		if (isBlank(resolvedFen)) {
			return null;
		}
		return parsePositionOrExit(resolvedFen.trim(), cmd, verbose);
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
			throw new CommandFailure(cmd + ": provide either " + OPT_INPUT + " or a single FEN, not both", 2);
		}
		if (input != null) {
			try {
				List<String> fens = Reader.readFenList(input);
				if (fens.isEmpty()) {
					throw new CommandFailure(cmd + ": input file has no FENs", 2);
				}
				return fens;
			} catch (IOException ex) {
				throw new CommandFailure(cmd + ": failed to read input: " + ex.getMessage(), ex, 2, false);
			}
		}
		if (fen == null || fen.isEmpty()) {
			throw new CommandFailure(cmd + " requires a FEN (" + MSG_FEN_REQUIRED_HINT + ")", 2);
		}
		return List.of(fen);
	}

	/**
	 * Samples a random legal standard-chess position that could arise in a game and
	 * still has at least one legal move.
	 *
	 * @return sampled reachable position
	 */
	public static Position randomPlayablePosition() {
		for (int attempts = 0; attempts < RANDOM_POSITION_SAMPLE_ATTEMPTS; attempts++) {
			Position candidate = randomReachablePosition();
			if (!candidate.legalMoves().isEmpty()) {
				return candidate;
			}
		}
		return randomReachablePosition();
	}

	/**
	 * Parses a FEN into a position or exits with a consistent CLI diagnostic.
	 *
	 * @param fen FEN string to parse
	 * @param cmd command label for diagnostics
	 * @param verbose whether to print stack traces on failure
	 * @return parsed position
	 */
	public static Position parsePositionOrExit(String fen, String cmd, boolean verbose) {
		try {
			return new Position(fen);
		} catch (IllegalArgumentException ex) {
			System.err.println(ERR_INVALID_FEN + (ex.getMessage() == null ? "" : ex.getMessage()));
			LogService.error(ex, cmd + ": invalid FEN", "FEN: " + fen);
			if (verbose) {
				ex.printStackTrace(System.err);
			}
			System.exit(3);
			return Setup.getStandardStartPosition();
		}
	}

	/**
	 * Throws a usage error for invalid option combinations.
	 *
	 * @param message explanation of the invalid input
	 */
	public static void failUsage(String message) {
		throw new IllegalArgumentException(message);
	}

	/**
	 * Normalizes optional strings by trimming and converting blank values to null.
	 *
	 * @param value raw option value
	 * @return trimmed value or {@code null}
	 */
	public static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	/**
	 * Resolves an input path from an explicit option or one positional path.
	 *
	 * @param a argument parser after all named options have been consumed
	 * @param input optional explicit input path
	 * @param conflictMessage message when both sources are provided
	 * @param missingMessage message when neither source is provided
	 * @return resolved input path
	 */
	public static Path resolveSingleInputPath(
			Argv a,
			Path input,
			String conflictMessage,
			String missingMessage) {
		List<String> rest = a.positionals();
		a.ensureConsumed();
		return resolveSingleInputPath(input, rest, conflictMessage, missingMessage);
	}

	/**
	 * Resolves an input path from an explicit option or one positional path.
	 *
	 * @param input optional explicit input path
	 * @param rest positional arguments
	 * @param conflictMessage message when both sources are provided
	 * @param missingMessage message when neither source is provided
	 * @return resolved input path
	 */
	public static Path resolveSingleInputPath(
			Path input,
			List<String> rest,
			String conflictMessage,
			String missingMessage) {
		if (input == null && rest.size() == 1) {
			return Path.of(rest.get(0));
		}
		if (input != null && !rest.isEmpty()) {
			failUsage(conflictMessage);
		}
		if (input == null) {
			failUsage(missingMessage);
		}
		return input;
	}

	/**
	 * Counts active single-position selectors.
	 *
	 * @param fen optional FEN selector
	 * @param startPos whether {@code --startpos} is active
	 * @param randomPos whether {@code --randompos} is active
	 * @return active-selector count
	 */
	private static int selectorCount(String fen, boolean startPos, boolean randomPos) {
		int count = 0;
		if (!isBlank(fen)) {
			count++;
		}
		if (startPos) {
			count++;
		}
		if (randomPos) {
			count++;
		}
		return count;
	}

	/**
	 * Resolves a FEN from {@code --fen} or remaining positionals.
	 *
	 * @param fen optional FEN from {@code --fen}
	 * @param rest optional positional tokens
	 * @return resolved FEN string, or {@code null} when none was provided
	 */
	private static String resolvePositionalFen(String fen, List<String> rest) {
		if (fen == null && rest != null && !rest.isEmpty()) {
			return String.join(" ", rest);
		}
		return fen;
	}

	/**
	 * Validates that at most one single-position selector is active.
	 *
	 * @param cmd command label for diagnostics
	 * @param fen optional FEN selector
	 * @param startPos whether {@code --startpos} is active
	 * @param randomPos whether {@code --randompos} is active
	 */
	private static void validateSinglePositionSelector(String cmd, String fen, boolean startPos, boolean randomPos) {
		if (selectorCount(fen, startPos, randomPos) > 1) {
			throw new CommandFailure(
					cmd + ": choose at most one of " + OPT_FEN + " <FEN>, " + OPT_STARTPOS + ", or " + OPT_RANDOMPOS,
					2);
		}
	}

	/**
	 * Returns whether a string is null or blank.
	 *
	 * @param value candidate string
	 * @return true when the value is null or blank
	 */
	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	/**
	 * Samples one reachable random standard-chess position.
	 *
	 * @return sampled position
	 */
	private static Position randomReachablePosition() {
		return Setup.getRandomPositions(1, false).get(0);
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

	/**
	 * Advances a progress bar when one is active.
	 *
	 * @param bar optional progress bar
	 */
	public static void step(Bar bar) {
		if (bar != null) {
			bar.step();
		}
	}

	/**
	 * Finishes a progress bar when one is active.
	 *
	 * @param bar optional progress bar
	 */
	public static void finish(Bar bar) {
		if (bar != null) {
			bar.finish();
		}
	}

	/**
	 * Prints a command error, optionally prints its stack trace, and exits.
	 *
	 * @param commandLabel command label for diagnostics
	 * @param message user-facing error detail
	 * @param failure original failure
	 * @param exitCode process exit code
	 * @param verbose whether to print stack traces
	 */
	public static void exitWithError(String commandLabel, String message, Throwable failure, int exitCode,
			boolean verbose) {
		throw new CommandFailure(commandLabel + ": " + message, failure, exitCode, verbose);
	}
}
