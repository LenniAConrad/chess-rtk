package application.cli.command;

import java.io.PrintStream;

/**
 * Structured command failure that can be rendered once at the CLI boundary.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class CommandFailure extends RuntimeException {

	/**
	 * Shared serial version UID.
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Process exit code for the failure.
	 */
	private final int exitCode;

	/**
	 * Whether to include the cause stack trace in diagnostics.
	 */
	private final boolean verbose;

	/**
	 * Creates a command failure without a nested cause.
	 *
	 * @param message user-facing error message
	 * @param exitCode process exit code
	 */
	public CommandFailure(String message, int exitCode) {
		this(message, null, exitCode, false);
	}

	/**
	 * Creates a command failure with an optional nested cause.
	 *
	 * @param message user-facing error message
	 * @param cause nested failure
	 * @param exitCode process exit code
	 * @param verbose whether to print the stack trace
	 */
	public CommandFailure(String message, Throwable cause, int exitCode, boolean verbose) {
		super(message, cause);
		this.exitCode = exitCode;
		this.verbose = verbose;
	}

	/**
	 * Returns the process exit code for the failure.
	 *
	 * @return exit code
	 */
	public int exitCode() {
		return exitCode;
	}

	/**
	 * Prints the user-facing failure details.
	 *
	 * @param err diagnostic output stream
	 */
	public void printTo(PrintStream err) {
		if (getMessage() != null && !getMessage().isBlank()) {
			err.println(getMessage());
		}
		if (verbose && getCause() != null) {
			getCause().printStackTrace(err);
		}
	}
}
