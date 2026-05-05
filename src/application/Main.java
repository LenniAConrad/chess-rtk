package application;

import static application.cli.Constants.CMD_HELP;
import static application.cli.Constants.CMD_HELP_LONG;
import static application.cli.Constants.CMD_HELP_SHORT;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import application.cli.CliCommand;
import application.cli.CliRegistry;
import application.cli.command.CommandFailure;
import application.cli.command.HelpCommand;
import utility.Argv;

/**
 * Used for providing the CLI entry point and dispatching subcommands.
 *
 * <p>
 * The dispatcher exposes the current top-level command families:
 * {@code record}, {@code fen}, {@code move}, {@code engine}, {@code book},
 * {@code puzzle}, plus operational commands such as {@code gui},
 * {@code config}, {@code doctor}, {@code clean}, and {@code help}.
 * </p>
 *
 * @since 2025
 * @author Lennart A. Conrad
 */
@SuppressWarnings({"java:S135", "java:S3776"})
public final class Main {

	/**
	 * Utility class; prevent instantiation.
	 */
	private Main() {
		// utility
	}

	/**
	 * Used for parsing top-level CLI arguments and delegating to a subcommand
	 * handler.
	 *
	 * <p>
	 * Behavior:
	 * </p>
	 * <ul>
	 * <li>Attempts to read the first positional argument as the subcommand.</li>
	 * <li>Delegates remaining positionals to the corresponding command handler.</li>
	 * <li>On unknown subcommands, prints help and exits with status {@code 2}.</li>
	 * </ul>
	 *
	 * @param argv raw command-line arguments; first positional must be a valid
	 *             subcommand
	 */
	public static void main(String[] argv) {
		int exitCode = run(argv);
		if (exitCode != 0) {
			System.exit(exitCode);
		}
	}

	/**
	 * Runs the CLI dispatcher and returns the process exit code instead of exiting.
	 *
	 * @param argv raw command-line arguments; first positional must be a valid
	 *             subcommand
	 * @return process exit code
	 */
	public static int run(String[] argv) {
		try {
			if (argv == null || argv.length == 0) {
				HelpCommand.helpSummary();
				return 0;
			}
			return dispatch(argv);
		} catch (CommandFailure failure) {
			failure.printTo(System.err);
			return failure.exitCode();
		} catch (IllegalArgumentException ex) {
			System.err.println(ex.getMessage());
			return 2;
		}
	}

	/**
	 * Dispatches a command path from the central registry.
	 *
	 * @param argv raw command-line arguments
	 * @return process exit code
	 */
	private static int dispatch(String[] argv) {
		if (argv.length == 0) {
			HelpCommand.helpSummary();
			return 0;
		}
		if (CMD_HELP.equals(argv[0])) {
			HelpCommand.runHelp(new Argv(Arrays.copyOfRange(argv, 1, argv.length)));
			return 0;
		}
		if (isHelpFlag(argv[0])) {
			HelpCommand.helpSummary();
			return 0;
		}

		CliCommand current = CliRegistry.root();
		List<String> matched = new ArrayList<>();
		int index = 0;
		while (index < argv.length) {
			String token = argv[index];
			if (isEndOfOptions(token) || token.startsWith("-")) {
				break;
			}
			CliCommand next = current.child(token);
			if (next == null) {
				break;
			}
			current = next;
			matched.add(token);
			index++;
		}

		String[] tail = Arrays.copyOfRange(argv, index, argv.length);
		String requestedPath = String.join(" ", matched);
		if (current.isRoot() && matched.isEmpty()) {
			System.err.println("Unknown command: " + argv[0]);
			HelpCommand.helpSummary(System.err);
			return 2;
		}
		if (containsHelpFlag(tail)) {
			HelpCommand.printCommandHelp(current, requestedPath, System.out);
			return 0;
		}

		if (!current.isRunnable()) {
			if (tail.length == 0) {
				System.err.println("Missing subcommand for: " + current.commandPath());
			} else {
				System.err.println("Unknown command: " + current.commandPath() + " " + tail[0]);
			}
			HelpCommand.printCommandHelp(current, requestedPath, System.err);
			return 2;
		}

		Consumer<Argv> handler = current.handler();
		handler.accept(new Argv(tail));
		return 0;
	}

	/**
	 * Returns whether the token is a help flag.
	 *
	 * @param token CLI token
	 * @return true for help flags
	 */
	private static boolean isHelpFlag(String token) {
		return CMD_HELP_SHORT.equals(token) || CMD_HELP_LONG.equals(token);
	}

	/**
	 * Returns whether the token terminates option parsing.
	 *
	 * @param token CLI token
	 * @return true for end-of-options markers
	 */
	private static boolean isEndOfOptions(String token) {
		return "--".equals(token) || "--end-of-options".equals(token);
	}

	/**
	 * Returns whether a tail contains a help flag before end-of-options.
	 *
	 * @param tail remaining CLI tail
	 * @return true when help was requested
	 */
	private static boolean containsHelpFlag(String[] tail) {
		if (tail == null) {
			return false;
		}
		for (String token : tail) {
			if (isEndOfOptions(token)) {
				break;
			}
			if (isHelpFlag(token)) {
				return true;
			}
		}
		return false;
	}
}
