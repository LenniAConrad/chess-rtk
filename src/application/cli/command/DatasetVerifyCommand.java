package application.cli.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import chess.io.DatasetVerifier;
import chess.io.DatasetVerifier.ArtifactStatus;
import chess.io.DatasetVerifier.Outcome;
import chess.io.DatasetVerifier.Report;
import chess.schema.Violation;
import utility.Argv;
import utility.Json;

/**
 * CLI handler for {@code crtk dataset verify}.
 *
 * <p>Re-hashes every artifact referenced by a
 * {@code crtk.dataset.manifest.v1} sidecar and reports drift, missing files,
 * schema breakage, or parse failures with deterministic per-artifact lines
 * on standard error. Exits {@code 0} on a clean verification, {@code 3} on
 * any verification failure, or {@code 2} on argument-shape errors such as a
 * missing or unreadable {@code --input}.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class DatasetVerifyCommand {

	/**
	 * Exit code used for argument-shape failures.
	 */
	private static final int USAGE_FAILURE_EXIT = 2;

	/**
	 * Exit code used for verification failures.
	 */
	private static final int VERIFICATION_FAILURE_EXIT = 3;

	/**
	 * Utility class; prevent instantiation.
	 */
	private DatasetVerifyCommand() {
		// utility
	}

	/**
	 * Runs {@code crtk dataset verify --input PATH}.
	 *
	 * @param argv parsed arguments
	 */
	public static void runVerify(Argv argv) {
		Path input = argv.path("--input", "-i");
		argv.ensureConsumed();
		if (input == null) {
			throw new CommandFailure(
					"Usage: crtk dataset verify --input PATH",
					USAGE_FAILURE_EXIT);
		}
		if (!Files.isRegularFile(input)) {
			throw new CommandFailure(
					"crtk dataset verify: --input '" + input + "' is not a regular file",
					USAGE_FAILURE_EXIT);
		}
		Report report;
		try {
			report = DatasetVerifier.verify(input);
		} catch (IOException ex) {
			throw new CommandFailure(
					"crtk dataset verify: failed to read manifest '" + input + "': " + ex.getMessage(),
					USAGE_FAILURE_EXIT);
		}
		printDiagnostics(report);
		System.out.println(renderSummaryJson(report));
		if (!report.ok()) {
			throw new CommandFailure(
					"crtk dataset verify: " + describeOutcome(report),
					VERIFICATION_FAILURE_EXIT);
		}
	}

	/**
	 * Prints per-section diagnostics for every non-OK entry or schema violation.
	 *
	 * @param report verification report
	 */
	private static void printDiagnostics(Report report) {
		if (report.outcome() == Outcome.PARSE_FAILURE) {
			System.err.println("manifest parse failure: " + report.parseFailure());
			return;
		}
		if (report.outcome() == Outcome.SCHEMA_FAILURE) {
			for (Violation violation : report.schemaViolations()) {
				System.err.println("schema: " + violation.render());
			}
			return;
		}
		emitProblems("inputs", report.inputs());
		emitProblems("outputs", report.outputs());
		emitProblems("weights", report.weights());
	}

	/**
	 * Emits per-artifact diagnostics for one section.
	 *
	 * @param section section label
	 * @param entries section entries
	 */
	private static void emitProblems(String section, java.util.List<ArtifactStatus> entries) {
		for (ArtifactStatus entry : entries) {
			if (entry.status() == DatasetVerifier.Status.OK
					|| entry.status() == DatasetVerifier.Status.UNAVAILABLE) {
				continue;
			}
			System.err.println(section + "/" + entry.name() + ": "
					+ entry.status().name().toLowerCase()
					+ (entry.actualSha256() != null
							? " (expected " + entry.expectedSha256()
									+ ", got " + entry.actualSha256() + ")"
							: ""));
		}
	}

	/**
	 * Returns the agent-consumable JSON summary printed on standard output.
	 *
	 * @param report verification report
	 * @return single-line JSON object
	 */
	private static String renderSummaryJson(Report report) {
		StringBuilder sb = new StringBuilder().append('{');
		sb.append("\"manifest\":\"").append(Json.esc(report.manifestPath().toString())).append('"');
		sb.append(",\"outcome\":\"").append(report.outcome().name().toLowerCase()).append('"');
		sb.append(",\"inputs\":").append(report.inputs().size());
		sb.append(",\"outputs\":").append(report.outputs().size());
		sb.append(",\"weights\":").append(report.weights().size());
		sb.append(",\"problems\":").append(report.problems().size());
		sb.append('}');
		return sb.toString();
	}

	/**
	 * Returns a human-readable failure-summary string for the failure message.
	 *
	 * @param report verification report
	 * @return short outcome description
	 */
	private static String describeOutcome(Report report) {
		switch (report.outcome()) {
			case PARSE_FAILURE:
				return "manifest is not valid JSON";
			case SCHEMA_FAILURE:
				return "manifest does not validate against " + chess.io.DatasetManifest.SCHEMA_VERSION
						+ " (" + report.schemaViolations().size() + " violation"
						+ (report.schemaViolations().size() == 1 ? "" : "s") + ")";
			case DRIFT_DETECTED:
				return report.problems().size() + " artifact"
						+ (report.problems().size() == 1 ? "" : "s") + " failed verification";
			default:
				return "verification failed";
		}
	}
}
