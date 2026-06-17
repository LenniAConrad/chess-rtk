package application.cli.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import chess.io.DatasetAuditor;
import chess.io.DatasetAuditor.AuditReport;
import chess.io.DatasetVerifier.ArtifactStatus;
import chess.io.DatasetVerifier.Outcome;
import chess.io.DatasetVerifier.Report;
import utility.Argv;
import utility.Json;

/**
 * CLI handler for {@code crtk dataset audit}.
 *
 * <p>Walks a directory tree, runs {@link DatasetAuditor#audit(Path, int)} over
 * every {@code *.manifest.json} sidecar it finds, and prints a per-manifest
 * summary line on stdout plus a one-line aggregate. Per-manifest diagnostics
 * (parse failures, drift, missing artifacts, schema violations) print on
 * stderr just as {@code dataset verify} does, so a CI consumer sees the same
 * diagnostic shape whether it audited one manifest or thousands.</p>
 *
 * <p>Exit codes: {@code 0} on a clean audit, {@code 3} when any manifest
 * fails verification, {@code 2} for argument shape errors.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class DatasetAuditCommand {

	/**
	 * Exit code used for argument-shape failures.
	 */
	private static final int USAGE_FAILURE_EXIT = 2;

	/**
	 * Exit code used when at least one manifest failed verification.
	 */
	private static final int AUDIT_FAILURE_EXIT = 3;

	/**
	 * Utility class; prevent instantiation.
	 */
	private DatasetAuditCommand() {
		// utility
	}

	/**
	 * Runs {@code crtk dataset audit --root DIR [--limit N]}.
	 *
	 * @param argv parsed arguments
	 */
	public static void runAudit(Argv argv) {
		Path root = argv.path("--root");
		int limit = argv.integerOr(0, "--limit");
		argv.ensureConsumed();
		if (root == null) {
			throw new CommandFailure(
					"Usage: crtk dataset audit --root DIR [--limit N]",
					USAGE_FAILURE_EXIT);
		}
		if (!Files.isDirectory(root)) {
			throw new CommandFailure(
					"crtk dataset audit: --root '" + root + "' is not a directory",
					USAGE_FAILURE_EXIT);
		}
		if (limit < 0) {
			throw new CommandFailure(
					"crtk dataset audit: --limit must be non-negative (got " + limit + ")",
					USAGE_FAILURE_EXIT);
		}
		AuditReport audit;
		try {
			audit = DatasetAuditor.audit(root, limit);
		} catch (IOException ex) {
			throw new CommandFailure(
					"crtk dataset audit: failed to walk '" + root + "': " + ex.getMessage(),
					USAGE_FAILURE_EXIT);
		}
		printDiagnostics(audit);
		System.out.println(renderSummaryJson(audit));
		if (!audit.ok()) {
			throw new CommandFailure(
					"crtk dataset audit: " + audit.problems() + " of " + audit.total()
							+ " manifest" + (audit.total() == 1 ? "" : "s") + " failed verification",
					AUDIT_FAILURE_EXIT);
		}
	}

	/**
	 * Prints per-manifest diagnostic lines for every failing report.
	 *
	 * @param audit aggregated audit report
	 */
	private static void printDiagnostics(AuditReport audit) {
		for (Report report : audit.reports()) {
			if (report.ok()) {
				continue;
			}
			String manifest = report.manifestPath().toString();
			switch (report.outcome()) {
				case PARSE_FAILURE:
					System.err.println(manifest + ": parse failure: " + report.parseFailure());
					break;
				case SCHEMA_FAILURE:
					System.err.println(manifest + ": schema failure ("
							+ report.schemaViolations().size() + " violations)");
					break;
				case DRIFT_DETECTED:
					for (ArtifactStatus entry : report.problems()) {
						System.err.println(manifest + ": "
								+ entry.status().name().toLowerCase()
								+ " " + entry.name());
					}
					break;
				default:
					System.err.println(manifest + ": " + report.outcome().name().toLowerCase());
			}
		}
	}

	/**
	 * Returns the agent-consumable JSON summary printed on standard output.
	 *
	 * @param audit aggregated audit report
	 * @return single-line JSON object
	 */
	private static String renderSummaryJson(AuditReport audit) {
		StringBuilder sb = new StringBuilder().append('{');
		sb.append("\"root\":\"").append(Json.esc(audit.root().toString())).append('"');
		sb.append(",\"total\":").append(audit.total());
		sb.append(",\"ok\":").append(audit.oks());
		sb.append(",\"failed\":").append(audit.problems());
		Outcome outcome = audit.ok() ? Outcome.OK : Outcome.DRIFT_DETECTED;
		sb.append(",\"outcome\":\"").append(outcome.name().toLowerCase()).append('"');
		sb.append('}');
		return sb.toString();
	}
}
