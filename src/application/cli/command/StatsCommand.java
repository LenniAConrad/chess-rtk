package application.cli.command;

import static application.cli.Constants.CMD_STATS;
import static application.cli.Constants.CMD_STATS_TAGS;
import static application.cli.Constants.OPT_INPUT;
import static application.cli.Constants.OPT_INPUT_SHORT;
import static application.cli.Constants.OPT_TOP;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;
import static application.cli.Format.formatSigned;
import static application.cli.RecordIO.streamRecordFile;
import static application.cli.Validation.requirePositive;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import application.cli.RecordIO.RecordConsumer;
import chess.struct.Record;
import chess.uci.Analysis;
import chess.uci.Evaluation;
import chess.uci.Output;
import utility.Argv;

/**
 * Implements {@code stats} and {@code stats-tags}.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class StatsCommand {

	/**
	 * Utility class; prevent instantiation.
	 */
	private StatsCommand() {
		// utility
	}

	/**
	 * Handles {@code stats}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runStats(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		Path input = a.pathRequired(OPT_INPUT, OPT_INPUT_SHORT);
		int top = a.integerOr(10, OPT_TOP);
		requirePositive(CMD_STATS, OPT_TOP, top);
		a.ensureConsumed();

		StatsAccumulator stats = new StatsAccumulator();
		try {
			streamRecordFile(input, verbose, CMD_STATS, stats);
		} catch (Exception ex) {
			System.err.println("stats: failed to read input: " + ex.getMessage());
			if (verbose) {
				ex.printStackTrace(System.err);
			}
			System.exit(2);
			return;
		}

		stats.printSummary(input, top);
	}

	/**
	 * Handles {@code stats-tags}.
	 *
	 * @param a argument parser for the subcommand
	 */
	public static void runStatsTags(Argv a) {
		boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
		Path input = a.pathRequired(OPT_INPUT, OPT_INPUT_SHORT);
		int top = a.integerOr(20, OPT_TOP);
		requirePositive(CMD_STATS_TAGS, OPT_TOP, top);
		a.ensureConsumed();

		TagStatsAccumulator stats = new TagStatsAccumulator();
		try {
			streamRecordFile(input, verbose, CMD_STATS_TAGS, stats);
		} catch (Exception ex) {
			System.err.println("stats-tags: failed to read input: " + ex.getMessage());
			if (verbose) {
				ex.printStackTrace(System.err);
			}
			System.exit(2);
			return;
		}

		stats.printSummary(input, top);
	}

	/**
	 * Aggregates summary statistics across record entries.
	 */
	private static final class StatsAccumulator implements RecordConsumer {

		/**
		 * Total number of records processed.
		 */
		private long total;

		/**
		 * Total number of invalid records encountered.
		 */
		private long invalid;

		/**
		 * Records with a position attached.
		 */
		private long withPosition;

		/**
		 * Records with a parent position attached.
		 */
		private long withParent;

		/**
		 * Records with at least one tag.
		 */
		private long withTags;

		/**
		 * Records with non-empty analysis data.
		 */
		private long withAnalysis;

		/**
		 * Records with valid evaluations.
		 */
		private long withEval;

		/**
		 * Records with mate evaluations.
		 */
		private long withMate;

		/**
		 * Sum of centipawn values.
		 */
		private long sumCp;

		/**
		 * Count of centipawn evaluations.
		 */
		private long countCp;

		/**
		 * Minimum centipawn value encountered.
		 */
		private int minCp = Integer.MAX_VALUE;

		/**
		 * Maximum centipawn value encountered.
		 */
		private int maxCp = Integer.MIN_VALUE;

		/**
		 * Minimum mate value encountered.
		 */
		private int minMate = Integer.MAX_VALUE;

		/**
		 * Maximum mate value encountered.
		 */
		private int maxMate = Integer.MIN_VALUE;

		/**
		 * Sum of search depths.
		 */
		private long sumDepth;

		/**
		 * Count of depth values recorded.
		 */
		private long countDepth;

		/**
		 * Sum of node counts recorded.
		 */
		private long sumNodes;

		/**
		 * Count of node totals recorded.
		 */
		private long countNodes;

		/**
		 * Per-engine record counts.
		 */
		private final Map<String, Long> engineCounts = new HashMap<>();

		/**
		 * Per-tag record counts.
		 */
		private final Map<String, Long> tagCounts = new HashMap<>();

		/**
		 * Records an invalid record entry.
		 */
		@Override
		public void invalid() {
			invalid++;
		}

		/**
		 * Consumes a record entry and updates aggregate stats.
		 *
		 * @param rec record entry to inspect
		 */
		@Override
		public void accept(Record rec) {
			total++;
			recordPositions(rec);
			recordEngine(rec);
			recordTags(rec);
			Analysis analysis = recordAnalysis(rec);
			Output best = (analysis == null) ? null : analysis.getBestOutput(1);
			if (best == null) {
				return;
			}
			recordEvaluation(best);
			recordDepthNodes(best);
		}

		/**
		 * Tracks which records contain positions or parents.
		 *
		 * @param rec record entry to inspect
		 */
		private void recordPositions(Record rec) {
			if (rec.getPosition() != null) {
				withPosition++;
			}
			if (rec.getParent() != null) {
				withParent++;
			}
		}

		/**
		 * Tracks engine labels for records.
		 *
		 * @param rec record entry to inspect
		 */
		private void recordEngine(Record rec) {
			String engine = rec.getEngine();
			if (engine != null && !engine.isEmpty()) {
				increment(engineCounts, engine);
			}
		}

		/**
		 * Tracks tag counts for records.
		 *
		 * @param rec record entry to inspect
		 */
		private void recordTags(Record rec) {
			String[] tags = rec.getTags();
			if (tags.length == 0) {
				return;
			}
			withTags++;
			for (String tag : tags) {
				if (tag != null && !tag.isEmpty()) {
					increment(tagCounts, tag);
				}
			}
		}

		/**
		 * Increments a counter for the given key.
		 *
		 * @param counts map to update
		 * @param key    key to increment
		 */
		private void increment(Map<String, Long> counts, String key) {
			counts.put(key, counts.getOrDefault(key, 0L) + 1L);
		}

		/**
		 * Records analysis presence and returns the analysis object.
		 *
		 * @param rec record entry to inspect
		 * @return analysis object or {@code null}
		 */
		private Analysis recordAnalysis(Record rec) {
			Analysis analysis = rec.getAnalysis();
			if (analysis != null && !analysis.isEmpty()) {
				withAnalysis++;
			}
			return analysis;
		}

		/**
		 * Records evaluation statistics from the best output.
		 *
		 * @param best best engine output entry
		 */
		private void recordEvaluation(Output best) {
			Evaluation eval = best.getEvaluation();
			if (eval == null || !eval.isValid()) {
				return;
			}
			withEval++;
			if (eval.isMate()) {
				withMate++;
				minMate = Math.min(minMate, eval.getValue());
				maxMate = Math.max(maxMate, eval.getValue());
				return;
			}
			countCp++;
			sumCp += eval.getValue();
			minCp = Math.min(minCp, eval.getValue());
			maxCp = Math.max(maxCp, eval.getValue());
		}

		/**
		 * Records depth and node counts from the best output.
		 *
		 * @param best best engine output entry
		 */
		private void recordDepthNodes(Output best) {
			short depth = best.getDepth();
			if (depth > 0) {
				sumDepth += depth;
				countDepth++;
			}
			long nodes = best.getNodes();
			if (nodes > 0) {
				sumNodes += nodes;
				countNodes++;
			}
		}

		/**
		 * Prints a summary of collected stats.
		 *
		 * @param input input file path
		 * @param top   number of top tags/engines to display
		 */
		void printSummary(Path input, int top) {
			final int labelWidth = 12;

			printStat(labelWidth, "Input", input.toAbsolutePath().toString());
			printStat(labelWidth, "Records",
					CommandSupport.formatCount(total) + " (invalid " + CommandSupport.formatCount(invalid) + ")");
			printStat(labelWidth, "Positions",
					CommandSupport.formatCount(withPosition) + " (parents " + CommandSupport.formatCount(withParent)
							+ ")");
			printStat(labelWidth, "Tags", CommandSupport.formatCount(withTags));
			printStat(labelWidth, "Analysis",
					CommandSupport.formatCount(withAnalysis)
							+ " (evals " + CommandSupport.formatCount(withEval)
							+ ", mates " + CommandSupport.formatCount(withMate) + ")");

			if (countCp > 0) {
				double avg = sumCp / (double) countCp;
				printStat(labelWidth, "Eval (cp)",
						"count " + CommandSupport.formatCount(countCp)
								+ " avg " + String.format(Locale.ROOT, "%+.1f", avg)
								+ " min " + formatSigned(minCp)
								+ " max " + formatSigned(maxCp));
			} else {
				printStat(labelWidth, "Eval (cp)", "n/a");
			}

			if (withMate > 0) {
				printStat(labelWidth, "Eval (mate)", "min #" + minMate + " max #" + maxMate);
			} else {
				printStat(labelWidth, "Eval (mate)", "n/a");
			}

			if (countDepth > 0) {
				printStat(labelWidth, "Depth",
						"avg " + String.format(Locale.ROOT, "%.1f", sumDepth / (double) countDepth));
			} else {
				printStat(labelWidth, "Depth", "n/a");
			}

			if (countNodes > 0) {
				printStat(labelWidth, "Nodes",
						"avg " + String.format(Locale.ROOT, "%,.1f", sumNodes / (double) countNodes));
			} else {
				printStat(labelWidth, "Nodes", "n/a");
			}

			printStat(labelWidth, "Top engines", formatTopCounts(engineCounts, top));
			printStat(labelWidth, "Top tags", formatTopCounts(tagCounts, top));
		}

		/**
		 * Prints a single labeled stat line.
		 *
		 * @param labelWidth width of label column
		 * @param label      label text
		 * @param value      value text
		 */
	private static void printStat(int labelWidth, String label, String value) {
			String formatString = "%-" + labelWidth + "s: %s%n";
			System.out.printf(formatString, label, value);
		}
	}

	/**
	 * Aggregates tag distribution statistics.
	 */
	private static final class TagStatsAccumulator implements RecordConsumer {

		/**
		 * Total number of records processed.
		 */
		private long total;

		/**
		 * Total number of invalid records encountered.
		 */
		private long invalid;

		/**
		 * Records containing at least one tag.
		 */
		private long withTags;

		/**
		 * Total number of tags observed.
		 */
		private long totalTags;

		/**
		 * Per-tag record counts.
		 */
		private final Map<String, Long> tagCounts = new HashMap<>();

		/**
		 * Records an invalid record entry.
		 */
		@Override
		public void invalid() {
			invalid++;
		}

		/**
		 * Consumes a record entry and updates tag counters.
		 *
		 * @param rec record entry to inspect
		 */
		@Override
		public void accept(Record rec) {
			total++;
			String[] tags = rec.getTags();
			if (tags.length == 0) {
				return;
			}
			withTags++;
			for (String tag : tags) {
				if (tag == null || tag.isEmpty()) {
					continue;
				}
				totalTags++;
				tagCounts.put(tag, tagCounts.getOrDefault(tag, 0L) + 1L);
			}
		}

		/**
		 * Prints a summary of tag distribution statistics.
		 *
		 * @param input input file path
		 * @param top   number of top tags to display
		 */
		void printSummary(Path input, int top) {
			final int labelWidth = 12;
			printStat(labelWidth, "Input", input.toAbsolutePath().toString());
			printStat(labelWidth, "Records",
					CommandSupport.formatCount(total) + " (invalid " + CommandSupport.formatCount(invalid) + ")");
			printStat(labelWidth, "Tagged", CommandSupport.formatCount(withTags));
			printStat(labelWidth, "Tags", CommandSupport.formatCount(totalTags));
			printStat(labelWidth, "Top tags", formatTopCounts(tagCounts, top));
		}

		/**
		 * Prints a single labeled stat line.
		 *
		 * @param labelWidth width of label column
		 * @param label      label text
		 * @param value      value text
		 */
		private static void printStat(int labelWidth, String label, String value) {
			String formatString = "%-" + labelWidth + "s: %s%n";
			System.out.printf(formatString, label, value);
		}
	}

	/**
	 * Formats a list of top counts into a single comma-separated string.
	 *
	 * @param counts map of labels to counts
	 * @param limit  maximum number of entries to include
	 * @return formatted summary string
	 */
	private static String formatTopCounts(Map<String, Long> counts, int limit) {
		if (counts.isEmpty() || limit <= 0) {
			return "-";
		}
		List<Map.Entry<String, Long>> entries = new ArrayList<>(counts.entrySet());
		entries.sort(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue).reversed()
				.thenComparing(Map.Entry::getKey));
		StringBuilder sb = new StringBuilder();
		int n = Math.min(limit, entries.size());
		for (int i = 0; i < n; i++) {
			if (i > 0) {
				sb.append(", ");
			}
			Map.Entry<String, Long> entry = entries.get(i);
			sb.append(entry.getKey()).append('=').append(CommandSupport.formatCount(entry.getValue()));
		}
		return sb.toString();
	}
}
