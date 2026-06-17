package application.cli.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import chess.io.DatasetDiff;
import chess.io.DatasetDiff.DiffResult;
import chess.io.DatasetDiff.FieldDelta;
import chess.io.DatasetDiff.HashDelta;
import chess.io.DatasetDiff.SectionDiff;
import utility.Argv;
import utility.Json;

/**
 * CLI handler for {@code crtk dataset diff}.
 *
 * <p>Compares two {@code crtk.dataset.manifest.v1} sidecars and emits a
 * structured explanation of where they disagree across four categories:
 * envelope (identity fields), argv, and three artifact sections
 * (inputs/outputs/weights). The default exit code stays within the
 * documented {@code 0/2/3} catalog: exit {@code 0} on every successful
 * comparison (the JSON output answers "did they match?"), exit {@code 2}
 * on argument shape errors, exit {@code 3} on parse failures. The
 * {@code --strict} flag converts a successful "they differ" comparison
 * into exit {@code 3} for CI scripts that want failure-on-diff.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class DatasetDiffCommand {

	/**
	 * Exit code used for argument-shape failures.
	 */
	private static final int USAGE_FAILURE_EXIT = 2;

	/**
	 * Exit code used for parse failures and {@code --strict} difference failures.
	 */
	private static final int FAILURE_EXIT = 3;

	/**
	 * Utility class; prevent instantiation.
	 */
	private DatasetDiffCommand() {
		// utility
	}

	/**
	 * Runs {@code crtk dataset diff --left A --right B [--strict]}.
	 *
	 * @param argv parsed arguments
	 */
	public static void runDiff(Argv argv) {
		Path left = argv.path("--left");
		Path right = argv.path("--right");
		boolean strict = argv.flag("--strict");
		argv.ensureConsumed();
		if (left == null || right == null) {
			throw new CommandFailure(
					"Usage: crtk dataset diff --left A.manifest.json --right B.manifest.json [--strict]",
					USAGE_FAILURE_EXIT);
		}
		for (Path manifest : List.of(left, right)) {
			if (!Files.isRegularFile(manifest)) {
				throw new CommandFailure(
						"crtk dataset diff: manifest '" + manifest + "' is not a regular file",
						USAGE_FAILURE_EXIT);
			}
		}
		DiffResult result;
		try {
			result = DatasetDiff.diff(left, right);
		} catch (IOException ex) {
			throw new CommandFailure(
					"crtk dataset diff: failed to read manifest: " + ex.getMessage(),
					USAGE_FAILURE_EXIT);
		}
		printDiagnostics(result);
		System.out.println(renderSummaryJson(result));
		switch (result.outcome()) {
			case PARSE_FAILURE:
				throw new CommandFailure(
						"crtk dataset diff: one or both manifests could not be parsed",
						FAILURE_EXIT);
			case DIFFER:
				if (strict) {
					throw new CommandFailure(
							"crtk dataset diff: manifests differ (--strict)",
							FAILURE_EXIT);
				}
				break;
			case IDENTICAL:
				break;
			default:
				break;
		}
	}

	/**
	 * Prints per-category diagnostics for every difference.
	 *
	 * @param result diff result
	 */
	private static void printDiagnostics(DiffResult result) {
		switch (result.outcome()) {
			case PARSE_FAILURE:
				if (result.leftError() != null) {
					System.err.println("left: " + result.leftError());
				}
				if (result.rightError() != null) {
					System.err.println("right: " + result.rightError());
				}
				return;
			case IDENTICAL:
				return;
			default:
				break;
		}
		for (FieldDelta delta : result.envelope()) {
			System.err.println("envelope/" + delta.field() + ": "
					+ renderValue(delta.leftValue()) + " -> " + renderValue(delta.rightValue()));
		}
		if (!result.argv().identical()) {
			System.err.println("argv: differs (left=" + result.argv().leftArgv().size()
					+ " tokens, right=" + result.argv().rightArgv().size() + " tokens)");
		}
		emitSection(result.inputs());
		emitSection(result.outputs());
		emitSection(result.weights());
	}

	/**
	 * Emits per-section diagnostics for every difference inside one section.
	 *
	 * @param section section diff
	 */
	private static void emitSection(SectionDiff section) {
		for (String name : section.added()) {
			System.err.println(section.section() + "/" + name + ": added");
		}
		for (String name : section.removed()) {
			System.err.println(section.section() + "/" + name + ": removed");
		}
		for (HashDelta hash : section.hashChanges()) {
			System.err.println(section.section() + "/" + hash.name() + ": sha256 changed ("
					+ hash.leftSha256() + " -> " + hash.rightSha256() + ")");
		}
	}

	/**
	 * Renders a possibly-null field value for diagnostics.
	 *
	 * @param value field value
	 * @return human-readable string ({@code "null"} when absent)
	 */
	private static String renderValue(String value) {
		return value == null ? "null" : value;
	}

	/**
	 * Returns the agent-consumable JSON summary printed on standard output.
	 *
	 * @param result diff result
	 * @return single-line JSON object
	 */
	private static String renderSummaryJson(DiffResult result) {
		StringBuilder sb = new StringBuilder().append('{');
		sb.append("\"left\":\"").append(Json.esc(result.left().toString())).append('"');
		sb.append(",\"right\":\"").append(Json.esc(result.right().toString())).append('"');
		sb.append(",\"outcome\":\"").append(result.outcome().name().toLowerCase()).append('"');
		sb.append(",\"identical\":").append(result.identical());
		sb.append(",\"envelopeDiffs\":").append(result.envelope().size());
		sb.append(",\"argvDifferent\":").append(!result.argv().identical());
		sb.append(",\"inputsDiff\":").append(sectionDiffCount(result.inputs()));
		sb.append(",\"outputsDiff\":").append(sectionDiffCount(result.outputs()));
		sb.append(",\"weightsDiff\":").append(sectionDiffCount(result.weights()));
		sb.append('}');
		return sb.toString();
	}

	/**
	 * Returns the total number of differing entries inside one section.
	 *
	 * @param section section diff
	 * @return added + removed + hash-changed count
	 */
	private static int sectionDiffCount(SectionDiff section) {
		return section.added().size() + section.removed().size() + section.hashChanges().size();
	}
}
