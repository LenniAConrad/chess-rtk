package application.cli.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.LongConsumer;

import application.console.Bar;

/**
 * Shared helpers for the record command family.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class RecordCommandSupport {

	/**
	 * Utility class; prevent instantiation.
	 */
	private RecordCommandSupport() {
		// utility
	}

	/**
	 * Creates a file-sized progress bar for one or more passes.
	 *
	 * @param input input path
	 * @param passes number of full-file passes
	 * @param label progress-bar label
	 * @return progress bar
	 */
	static Bar fileProgressBar(Path input, int passes, String label) {
		long size = fileSize(input);
		long total = size <= 0L ? 0L : size * Math.max(1, passes);
		return progressBar(total, label);
	}

	/**
	 * Returns the file size or zero when it cannot be measured.
	 *
	 * @param input input path
	 * @return file size in bytes
	 */
	static long fileSize(Path input) {
		try {
			return input == null ? 0L : Files.size(input);
		} catch (IOException ex) {
			return 0L;
		}
	}

	/**
	 * Creates a byte-progress callback for the supplied bar.
	 *
	 * @param bar optional progress bar
	 * @return progress callback or {@code null}
	 */
	static LongConsumer byteProgress(Bar bar) {
		return bar == null ? null : bar::set;
	}

	/**
	 * Finishes a progress bar when one is active.
	 *
	 * @param bar optional progress bar
	 */
	static void finishProgress(Bar bar) {
		if (bar != null) {
			bar.finish();
		}
	}

	/**
	 * Verifies that an input file exists before invoking record converters.
	 *
	 * @param input input path
	 * @param label command label for diagnostics
	 */
	static void requireReadableFile(Path input, String label) {
		if (input == null || !Files.isRegularFile(input) || !Files.isReadable(input)) {
			throw new CommandFailure(label + ": input file not found or not readable: " + input, 3);
		}
	}

	/**
	 * Throws a structured record-command failure with exit code 2.
	 *
	 * @param message user-facing error message
	 */
	static void exitWithError(String message) {
		throw new CommandFailure(message, 2);
	}

	/**
	 * Throws a structured record-command failure with optional stack trace output.
	 *
	 * @param message user-facing error message
	 * @param cause nested failure
	 * @param verbose whether to print the stack trace
	 */
	static void exitWithError(String message, Throwable cause, boolean verbose) {
		throw new CommandFailure(message, cause, 2, verbose);
	}

	/**
	 * Creates a deterministic CLI progress bar.
	 *
	 * @param total total units
	 * @param label progress-bar label
	 * @return progress bar
	 */
	private static Bar progressBar(long total, String label) {
		return total > 0L ? new Bar(total, label) : null;
	}
}
