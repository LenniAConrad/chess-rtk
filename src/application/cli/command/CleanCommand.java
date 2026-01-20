package application.cli.command;

import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;

import chess.debug.SessionCache;
import utility.Argv;

/**
 * Implements the {@code clean} subcommand.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class CleanCommand {

	/**
	 * Utility class; prevent instantiation.
	 */
	private CleanCommand() {
		// utility
	}

	/**
	 * Handles {@code clean}.
	 *
	 * <p>
	 * Deletes cached session artifacts under the default session directory while
	 * preserving the directory itself.
	 * </p>
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runClean(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		a.ensureConsumed();

		try {
			SessionCache.clean();
			SessionCache.ensureDirectory();
			System.out.println("Session cache cleared: " + SessionCache.directory().toAbsolutePath());
		} catch (Exception ex) {
			System.err.println("Failed to clean session cache: " + ex.getMessage());
			if (verbose) {
				ex.printStackTrace(System.err);
			}
			System.exit(1);
		}
	}
}
