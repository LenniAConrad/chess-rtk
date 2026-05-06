package application.cli.command;

import static application.cli.Constants.CMD_HELP_LONG;
import static application.cli.Constants.CMD_HELP_SHORT;

import application.cli.command.CommandSupport.OutputMode;
import utility.Argv;

/**
 * Prints ChessRTK version metadata.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class VersionCommand {

	/**
	 * Current project version.
	 */
	public static final String VERSION = "1.0.0";

	/**
	 * Project display name.
	 */
	private static final String NAME = "ChessRTK";

	/**
	 * Launcher name.
	 */
	private static final String LAUNCHER = "crtk";

	/**
	 * Utility class; prevent instantiation.
	 */
	private VersionCommand() {
		// utility
	}

	/**
	 * Handles {@code version}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runVersion(Argv a) {
		if (a.flag(CMD_HELP_SHORT, CMD_HELP_LONG)) {
			HelpCommand.runHelp(new Argv(new String[] { "version" }));
			return;
		}
		OutputMode outputMode = CommandSupport.resolveOutputMode(a, "version");
		a.ensureConsumed();

		if (outputMode == OutputMode.TEXT) {
			System.out.println(LAUNCHER + " " + VERSION);
			return;
		}
		System.out.println("{\"name\":" + CommandSupport.jsonString(NAME)
				+ ",\"launcher\":" + CommandSupport.jsonString(LAUNCHER)
				+ ",\"version\":" + CommandSupport.jsonString(VERSION)
				+ ",\"java\":" + CommandSupport.jsonString(System.getProperty("java.version")) + "}");
	}
}
