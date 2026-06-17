package application.cli.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import application.cli.RecordIO;
import chess.io.RecordSplitAuditor;
import chess.io.RecordSplitAuditor.Leakage;
import chess.io.RecordSplitAuditor.Report;
import chess.io.RecordSplitter;
import chess.io.RecordSplitter.Strategy;
import utility.Argv;
import utility.Json;

/**
 * CLI handler for {@code crtk record audit-split}.
 *
 * <p>Reads already-generated record split files and fails when a
 * position-signature appears in more than one split. This is a read-only,
 * defense-in-depth check for leakage bugs in ML dataset pipelines.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class RecordAuditSplitCommand {

	/**
	 * Exit code used for argument-shape failures.
	 */
	private static final int USAGE_FAILURE_EXIT = 2;

	/**
	 * Exit code used for I/O failures.
	 */
	private static final int IO_FAILURE_EXIT = 3;

	/**
	 * Exit code used when leakage is detected.
	 */
	private static final int LEAKAGE_FAILURE_EXIT = 3;

	/**
	 * Default number of leakage examples to print on stderr.
	 */
	private static final int DEFAULT_MAX_LEAKS = 20;

	/**
	 * Utility class; prevent instantiation.
	 */
	private RecordAuditSplitCommand() {
		// utility
	}

	/**
	 * Runs {@code crtk record audit-split --splits A,B,C [--split-by fen]}.
	 *
	 * @param argv parsed arguments
	 */
	public static void runAuditSplit(Argv argv) {
		String splitRaw = argv.string("--splits");
		String strategyRaw = firstNonBlank(argv.string("--split-by"), argv.string("--group-by"));
		int maxLeaks = argv.integerOr(DEFAULT_MAX_LEAKS, "--max-leaks");
		argv.ensureConsumed();
		if (splitRaw == null || splitRaw.isBlank()) {
			throw new CommandFailure(
					"Usage: crtk record audit-split --splits TRAIN,VAL,TEST "
							+ "[--split-by fen] [--max-leaks N]",
					USAGE_FAILURE_EXIT);
		}
		if (maxLeaks < 0) {
			throw new CommandFailure(
					"crtk record audit-split: --max-leaks must be non-negative (got "
							+ maxLeaks + ")",
					USAGE_FAILURE_EXIT);
		}
		Strategy strategy = parseStrategy(strategyRaw);
		List<Path> splits = parseSplits(splitRaw);
		RecordSplitAuditor auditor = new RecordSplitAuditor(strategy);
		try {
			for (Path split : splits) {
				String splitName = split.toString();
				RecordIO.streamRecordJson(split, json -> auditor.add(splitName, json));
			}
		} catch (IOException ex) {
			throw new CommandFailure(
					"crtk record audit-split: failed to read split: " + ex.getMessage(),
					IO_FAILURE_EXIT);
		}
		Report report = auditor.report();
		printLeaks(report, maxLeaks);
		System.out.println(renderSummaryJson(report, splits));
		if (!report.ok()) {
			throw new CommandFailure(
					"crtk record audit-split: " + report.leaks().size()
							+ " group" + (report.leaks().size() == 1 ? "" : "s")
							+ " leaked across splits",
					LEAKAGE_FAILURE_EXIT);
		}
	}

	/**
	 * Returns the first nonblank string.
	 *
	 * @param first  first candidate
	 * @param second second candidate
	 * @return first nonblank candidate or {@code null}
	 */
	private static String firstNonBlank(String first, String second) {
		if (first != null && !first.isBlank()) {
			return first;
		}
		if (second != null && !second.isBlank()) {
			return second;
		}
		return null;
	}

	/**
	 * Parses the split strategy.
	 *
	 * @param raw strategy token
	 * @return parsed strategy
	 */
	private static Strategy parseStrategy(String raw) {
		try {
			return RecordSplitter.parseStrategy(raw);
		} catch (IllegalArgumentException ex) {
			throw new CommandFailure("crtk record audit-split: " + ex.getMessage(),
					USAGE_FAILURE_EXIT);
		}
	}

	/**
	 * Parses and validates the comma-separated split file list.
	 *
	 * @param raw raw {@code --splits} value
	 * @return split paths
	 */
	private static List<Path> parseSplits(String raw) {
		String[] tokens = raw.split(",");
		List<Path> paths = new ArrayList<>();
		for (String token : tokens) {
			String stripped = token.strip();
			if (stripped.isEmpty()) {
				continue;
			}
			Path path = Path.of(stripped);
			if (!Files.isRegularFile(path)) {
				throw new CommandFailure(
						"crtk record audit-split: split '" + path + "' is not a regular file",
						USAGE_FAILURE_EXIT);
			}
			paths.add(path);
		}
		if (paths.size() < 2) {
			throw new CommandFailure(
					"crtk record audit-split: --splits must name at least two files",
					USAGE_FAILURE_EXIT);
		}
		return paths;
	}

	/**
	 * Prints deterministic leakage diagnostics.
	 *
	 * @param report   audit report
	 * @param maxLeaks maximum examples to print
	 */
	private static void printLeaks(Report report, int maxLeaks) {
		int printed = 0;
		for (Leakage leak : report.leaks()) {
			if (printed >= maxLeaks) {
				break;
			}
			System.err.println("leak: group=\"" + leak.groupKey()
					+ "\" first=\"" + leak.firstSplit()
					+ "\" also=\"" + String.join(",", leak.otherSplits()) + "\"");
			printed++;
		}
		if (report.leaks().size() > printed) {
			System.err.println("leak: omitted " + (report.leaks().size() - printed)
					+ " additional group(s)");
		}
	}

	/**
	 * Renders the machine-readable summary.
	 *
	 * @param report audit report
	 * @param splits split paths
	 * @return single-line JSON object
	 */
	private static String renderSummaryJson(Report report, List<Path> splits) {
		StringBuilder sb = new StringBuilder().append('{');
		sb.append("\"splits\":").append(splits.size());
		sb.append(",\"strategy\":\"").append(RecordSplitter.strategyToken(report.strategy())).append('"');
		sb.append(",\"rows\":").append(report.rows());
		sb.append(",\"keyedRows\":").append(report.keyedRows());
		sb.append(",\"unkeyedRows\":").append(report.unkeyedRows());
		sb.append(",\"groups\":").append(report.groups());
		sb.append(",\"leakageGroups\":").append(report.leaks().size());
		sb.append(",\"outcome\":\"").append(report.ok() ? "ok" : "leakage").append('"');
		sb.append(",\"rowsBySplit\":{");
		boolean first = true;
		for (var entry : report.rowsBySplit().entrySet()) {
			if (!first) {
				sb.append(',');
			}
			first = false;
			sb.append('"').append(Json.esc(entry.getKey())).append("\":").append(entry.getValue());
		}
		sb.append("}}");
		return sb.toString();
	}
}
