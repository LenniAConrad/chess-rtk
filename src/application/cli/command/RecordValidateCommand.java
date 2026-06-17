package application.cli.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import application.cli.RecordIO;
import chess.struct.RecordValidator;
import chess.struct.RecordValidator.Issue;
import chess.struct.RecordValidator.Outcome;
import utility.Argv;

/**
 * CLI handler for {@code crtk record validate}.
 *
 * <p>Walks a record file (JSON array or JSONL) and emits a per-record
 * fail-loud diagnostic instead of the historical silent-drop behaviour of
 * {@link chess.struct.Record#fromJson(String)}. The default mode streams the
 * whole file and reports up to {@code --max-errors} issues so a user can see
 * an entire malformed batch at once; {@code --strict} stops at the first
 * issue for fast CI feedback.</p>
 *
 * <p>Exit codes follow the catalog's documented taxonomy: {@code 0} on
 * success, {@code 3} on validation failure (issues found or input not
 * parseable as JSON), and {@code 2} for argument-shape errors such as a
 * missing or unreadable {@code --input}.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class RecordValidateCommand {

	/**
	 * Exit code used when validation fails.
	 */
	private static final int VALIDATION_FAILURE_EXIT = 3;

	/**
	 * Exit code used when arguments are malformed.
	 */
	private static final int USAGE_FAILURE_EXIT = 2;

	/**
	 * Default cap on accumulated issue reports in tolerant mode.
	 */
	private static final int DEFAULT_MAX_ERRORS = 50;

	/**
	 * Utility class; prevent instantiation.
	 */
	private RecordValidateCommand() {
		// utility
	}

	/**
	 * Runs {@code crtk record validate --input PATH [--strict] [--max-errors N]}.
	 *
	 * @param argv parsed arguments
	 */
	public static void runValidate(Argv argv) {
		Path input = argv.path("--input", "-i");
		boolean strict = argv.flag("--strict");
		int maxErrors = argv.integerOr(DEFAULT_MAX_ERRORS, "--max-errors");
		argv.ensureConsumed();

		if (input == null) {
			throw new CommandFailure(
					"Usage: crtk record validate --input PATH [--strict] [--max-errors N]",
					USAGE_FAILURE_EXIT);
		}
		if (!Files.isRegularFile(input)) {
			throw new CommandFailure(
					"failed to read --input '" + input + "': not a regular file",
					USAGE_FAILURE_EXIT);
		}
		if (maxErrors < 0) {
			throw new CommandFailure(
					"--max-errors must be non-negative (got " + maxErrors + ")",
					USAGE_FAILURE_EXIT);
		}

		State state = new State(maxErrors, strict);
		try {
			RecordIO.streamRecordJson(input, json -> validateOne(json, state));
		} catch (StopValidation ignored) {
			// expected when --strict trips on the first issue
		} catch (IOException ex) {
			throw new CommandFailure(
					"failed to read --input '" + input + "': " + ex.getMessage(),
					USAGE_FAILURE_EXIT);
		}

		for (String report : state.reports) {
			System.err.println(report);
		}

		int total = state.recordCount;
		int invalid = state.invalidCount;
		if (invalid == 0) {
			System.out.println("ok: " + total + " record" + plural(total) + " validated");
			return;
		}
		String detail = "validation failed: " + invalid + " of " + total
				+ " record" + plural(total) + " invalid";
		if (strict) {
			detail += " (--strict stopped at first error)";
		} else if (state.reports.size() < state.totalIssues) {
			detail += " (" + (state.totalIssues - state.reports.size())
					+ " additional issue"
					+ (state.totalIssues - state.reports.size() == 1 ? "" : "s")
					+ " suppressed; raise --max-errors to see them)";
		}
		throw new CommandFailure(detail, VALIDATION_FAILURE_EXIT);
	}

	/**
	 * Validates one record and accumulates issues into the streaming state.
	 *
	 * @param json  raw JSON text for a single record
	 * @param state streaming validation state
	 */
	private static void validateOne(String json, State state) {
		int index = state.recordCount++;
		Outcome outcome = RecordValidator.validate(json);
		if (outcome.ok()) {
			return;
		}
		state.invalidCount++;
		for (Issue issue : outcome.issues()) {
			state.totalIssues++;
			if (state.reports.size() < state.maxErrors) {
				state.reports.add(formatIssue(index, issue));
			}
		}
		if (state.strict) {
			throw new StopValidation();
		}
	}

	/**
	 * Formats a single issue for diagnostic output.
	 *
	 * @param index zero-based record index
	 * @param issue field-level validation issue
	 * @return formatted diagnostic line
	 */
	private static String formatIssue(int index, Issue issue) {
		return "index=" + index + " field=" + issue.field() + ": " + issue.message();
	}

	/**
	 * Returns the pluralisation suffix for a count.
	 *
	 * @param count cardinality
	 * @return empty string when count is one, otherwise {@code "s"}
	 */
	private static String plural(int count) {
		return count == 1 ? "" : "s";
	}

	/**
	 * Mutable state carried through the streaming validation loop.
	 */
	private static final class State {

		/**
		 * Maximum number of issue reports to emit.
		 */
		private final int maxErrors;

		/**
		 * Whether validation should stop at the first failing record.
		 */
		private final boolean strict;

		/**
		 * Diagnostic lines collected for emission to stderr.
		 */
		private final List<String> reports = new ArrayList<>();

		/**
		 * Total number of records inspected.
		 */
		private int recordCount;

		/**
		 * Total number of records flagged as invalid.
		 */
		private int invalidCount;

		/**
		 * Total number of issues discovered (may exceed reports.size()).
		 */
		private int totalIssues;

		/**
		 * Constructs a fresh streaming state.
		 *
		 * @param maxErrors maximum number of issue reports to emit
		 * @param strict    whether to stop at the first failing record
		 */
		private State(int maxErrors, boolean strict) {
			this.maxErrors = maxErrors;
			this.strict = strict;
		}
	}

	/**
	 * Sentinel exception used to break out of the streaming loop in {@code --strict} mode.
	 */
	private static final class StopValidation extends RuntimeException {

		/**
		 * Serialisation identifier.
		 */
		private static final long serialVersionUID = 1L;

		/**
		 * Constructs the sentinel without a stack trace.
		 */
		private StopValidation() {
			super(null, null, false, false);
		}
	}
}
