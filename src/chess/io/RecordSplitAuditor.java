package chess.io;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

import chess.io.RecordSplitter.Strategy;

/**
 * Stateful leakage auditor for already-materialized record splits.
 *
 * <p>The auditor uses the exact group-key function used by
 * {@link RecordSplitter}: for {@link Strategy#FEN}, the canonical four-field
 * position identity. Repeated keys inside one split are allowed; the same key
 * appearing in two or more splits is reported as leakage.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class RecordSplitAuditor {

	/**
	 * Group-key strategy.
	 */
	private final Strategy strategy;

	/**
	 * Split name to row count.
	 */
	private final LinkedHashMap<String, Long> rowsBySplit = new LinkedHashMap<>();

	/**
	 * Group key to first split that claimed it.
	 */
	private final LinkedHashMap<String, String> owners = new LinkedHashMap<>();

	/**
	 * Group key to leakage report.
	 */
	private final LinkedHashMap<String, Leakage> leaks = new LinkedHashMap<>();

	/**
	 * Number of rows scanned.
	 */
	private long rows;

	/**
	 * Number of rows with a usable group key.
	 */
	private long keyedRows;

	/**
	 * Number of rows without a usable group key.
	 */
	private long unkeyedRows;

	/**
	 * Constructs an empty auditor.
	 *
	 * @param strategy group-key strategy
	 */
	public RecordSplitAuditor(Strategy strategy) {
		this.strategy = strategy == null ? Strategy.FEN : strategy;
	}

	/**
	 * Adds one record row from a split.
	 *
	 * @param splitName split name or path
	 * @param json      raw record JSON object
	 */
	public void add(String splitName, String json) {
		String split = splitName == null || splitName.isBlank() ? "<unnamed>" : splitName;
		rows++;
		rowsBySplit.merge(split, 1L, Long::sum);
		String group = RecordSplitter.groupKey(json, strategy);
		if (group == null || group.isBlank()) {
			unkeyedRows++;
			return;
		}
		keyedRows++;
		String owner = owners.get(group);
		if (owner == null) {
			owners.put(group, split);
			return;
		}
		if (!owner.equals(split)) {
			leaks.computeIfAbsent(group, key -> new Leakage(key, owner)).add(split);
		}
	}

	/**
	 * Returns an immutable report of the accumulated scan.
	 *
	 * @return audit report
	 */
	public Report report() {
		return new Report(
				strategy,
				rows,
				keyedRows,
				unkeyedRows,
				owners.size(),
				List.copyOf(leaks.values()),
				new LinkedHashMap<>(rowsBySplit));
	}

	/**
	 * One leaking group key and the splits that contain it.
	 */
	public static final class Leakage {

		/**
		 * Leaking group key.
		 */
		private final String groupKey;

		/**
		 * First split where the key appeared.
		 */
		private final String firstSplit;

		/**
		 * Other splits where the key appeared.
		 */
		private final LinkedHashSet<String> otherSplits = new LinkedHashSet<>();

		/**
		 * Constructs a leakage report.
		 *
		 * @param groupKey   leaking group key
		 * @param firstSplit first split where the key appeared
		 */
		private Leakage(String groupKey, String firstSplit) {
			this.groupKey = groupKey;
			this.firstSplit = firstSplit;
		}

		/**
		 * Adds another split containing the key.
		 *
		 * @param split split name
		 */
		private void add(String split) {
			otherSplits.add(split);
		}

		/**
		 * Returns the leaking group key.
		 *
		 * @return group key
		 */
		public String groupKey() {
			return groupKey;
		}

		/**
		 * Returns the first split where the key appeared.
		 *
		 * @return first split
		 */
		public String firstSplit() {
			return firstSplit;
		}

		/**
		 * Returns the other splits where the key appeared.
		 *
		 * @return other split names
		 */
		public List<String> otherSplits() {
			return new ArrayList<>(otherSplits);
		}
	}

	/**
	 * Immutable audit report.
	 */
	public static final class Report {

		/**
		 * Group-key strategy used for the scan.
		 */
		private final Strategy strategy;

		/**
		 * Total rows scanned.
		 */
		private final long rows;

		/**
		 * Rows with usable group keys.
		 */
		private final long keyedRows;

		/**
		 * Rows without usable group keys.
		 */
		private final long unkeyedRows;

		/**
		 * Unique group-key count.
		 */
		private final long groups;

		/**
		 * Leakage entries.
		 */
		private final List<Leakage> leaks;

		/**
		 * Per-split row counts.
		 */
		private final LinkedHashMap<String, Long> rowsBySplit;

		/**
		 * Constructs an immutable report.
		 *
		 * @param strategy    group-key strategy
		 * @param rows        total rows
		 * @param keyedRows   source keyed rows
		 * @param unkeyedRows source unkeyed rows
		 * @param groups      unique groups
		 * @param leaks       leakage entries
		 * @param rowsBySplit per-split row counts
		 */
		private Report(Strategy strategy, long rows, long keyedRows, long unkeyedRows,
				long groups, List<Leakage> leaks, LinkedHashMap<String, Long> rowsBySplit) {
			this.strategy = strategy;
			this.rows = rows;
			this.keyedRows = keyedRows;
			this.unkeyedRows = unkeyedRows;
			this.groups = groups;
			this.leaks = List.copyOf(leaks);
			this.rowsBySplit = new LinkedHashMap<>(rowsBySplit);
		}

		/**
		 * Returns the group-key strategy.
		 *
		 * @return strategy
		 */
		public Strategy strategy() {
			return strategy;
		}

		/**
		 * Returns the total row count.
		 *
		 * @return total rows
		 */
		public long rows() {
			return rows;
		}

		/**
		 * Returns the keyed row count.
		 *
		 * @return keyed rows
		 */
		public long keyedRows() {
			return keyedRows;
		}

		/**
		 * Returns the unkeyed row count.
		 *
		 * @return unkeyed rows
		 */
		public long unkeyedRows() {
			return unkeyedRows;
		}

		/**
		 * Returns the unique group count.
		 *
		 * @return group count
		 */
		public long groups() {
			return groups;
		}

		/**
		 * Returns leakage entries.
		 *
		 * @return leakage entries
		 */
		public List<Leakage> leaks() {
			return leaks;
		}

		/**
		 * Returns per-split row counts.
		 *
		 * @return split row counts
		 */
		public LinkedHashMap<String, Long> rowsBySplit() {
			return new LinkedHashMap<>(rowsBySplit);
		}

		/**
		 * Returns whether the scan found no leakage.
		 *
		 * @return {@code true} when no leakage was found
		 */
		public boolean ok() {
			return leaks.isEmpty();
		}
	}
}
