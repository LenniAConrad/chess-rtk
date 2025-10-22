package chess.uci;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import chess.core.Move;

/**
 * Used for aggregating and querying engine {@code Output} for a {@code Position}.
 *
 * <p>Stores results in a two-dimensional structure: the outer list groups by principal variation
 * (PV, 1-based), while each inner array indexes search depths (0-based). Flags indicate whether
 * this analysis currently contains any valid output and whether engine evaluation has already been
 * completed for reuse.</p>
 *
 * @see Output
 * @see Engine
 * @since 2023
 * author Lennart A. Conrad
 */
public class Analysis {

	/**
	 * Used for storing {@code Output}. The first layer of {@code List} is responsible for the
	 * principal variation, whilst the second layer is a depth-indexed {@code Output[]} to minimize
	 * per-level overhead compared to {@code ArrayList}.
	 */
	private final List<Output[]> pvOutputs = new ArrayList<>();

	/**
	 * Tells if the {@code Analysis} has already been analyzed before. This is useful when skipping
	 * a {@code Position} to save computational effort.
	 */
	private boolean completed = false;

	/**
	 * Used for marking this analysis as already analyzed to allow skipping engine evaluation.
	 *
	 * @param completed whether the analysis has been completed
	 * @return itself for chaining
	 */
	public Analysis setCompleted(boolean completed) {
		this.completed = completed;
		return this;
	}

	/**
	 * Used for knowing if the {@code Analysis} has already been analyzed.
	 *
	 * @return {@code true} if it has already been analyzed, else {@code false}
	 */
	public boolean isCompleted() {
		return completed;
	}

	/**
	 * Used for parsing and adding multiple engine output lines to this analysis.
	 *
	 * @param string the array of engine/UCI output lines to parse; must not be {@code null}
	 * @return itself for chaining
	 */
	public Analysis addAll(String[] string) {
		for (int i = 0; i < string.length; i++) {
			add(string[i]);
		}
		return this;
	}

	/**
	 * Used for adding multiple {@code Output} instances to this analysis.
	 *
	 * @param output the array of {@code Output} items to add; must not be {@code null}
	 * @return itself for chaining
	 */
	public Analysis addAll(Output[] output) {
		for (int i = 0; i < output.length; i++) {
			add(output[i]);
		}
		return this;
	}

	/**
	 * Used for parsing and adding a single engine output line to this analysis.
	 *
	 * @param string the engine/UCI output line to parse; must not be {@code null}
	 * @return itself for chaining
	 */
	public Analysis add(String string) {
		add(new Output(string));
		return this;
	}

	/**
	 * Used for adding a single {@code Output} instance into the PV/depth grid.
	 *
	 * @param output the {@code Output} to add; ignored if not valid
	 * @return itself for chaining
	 */
	public Analysis add(Output output) {
		if (!output.hasContent()) {
			return this;
		}
		final int pvIdx = output.getPrincipalVariation() - 1;
		while (pvIdx >= pvOutputs.size()) {
			pvOutputs.add(null);
		}
		Output[] row = pvOutputs.get(pvIdx);
		final int depth = output.getDepth();
		row = ensure(row, depth + 1);
		row[depth] = output;
		pvOutputs.set(pvIdx, row);
		return this;
	}

	/**
	 * Used for retrieving the last {@code Output} of the deepest principal variation, or null if
	 * none.
	 *
	 * @return the worst (deepest) {@code Output}, or null if unavailable
	 */
	public Output getWorstOutput() {
		if (pvOutputs.isEmpty()) {
			return null;
		}
		Output[] lastPv = pvOutputs.get(pvOutputs.size() - 1);
		if (lastPv == null) {
			return null;
		}
		for (int i = lastPv.length - 1; i >= 0; i--) {
			if (lastPv[i] != null) {
				return lastPv[i];
			}
		}
		return null;
	}

	/**
	 * Used as a shortcut to retrieve the last {@code Output} of the first principal variation.
	 *
	 * @return the greatest {@code Output} from the first principal variation, or null if unavailable
	 */
	public Output getBestOutput() {
		return getBestOutput(1);
	}

	/**
	 * Used for retrieving the last {@code Output} of the given principal variation, or null if not
	 * available.
	 *
	 * @param principalVariation the principal variation index (1-based)
	 * @return the last {@code Output} of the specified principal variation, or null if unavailable
	 */
	public Output getBestOutput(int principalVariation) {
		if (principalVariation < 1 || principalVariation > pvOutputs.size()) {
			return null;
		}
		Output[] row = pvOutputs.get(principalVariation - 1);
		if (row == null) {
			return null;
		}
		for (int i = row.length - 1; i >= 0; i--) {
			if (row[i] != null) {
				return row[i];
			}
		}
		return null;
	}

	/**
	 * Used for retrieving the {@code Output} at the specified depth and principal variation, or
	 * null if out of range.
	 *
	 * @param depth the depth index within the principal variation
	 * @param principalVariation the principal variation index (1-based)
	 * @return the {@code Output} at the specified location, or null if out of bounds
	 */
	public Output get(int depth, int principalVariation) {
		if (principalVariation < 1 || principalVariation > pvOutputs.size()) {
			return null;
		}
		Output[] row = pvOutputs.get(principalVariation - 1);
		return (row == null || depth < 0 || depth >= row.length) ? null : row[depth];
	}

	/**
	 * Used for retrieving an array filled with non {@code null} {@code Output} {@code Objects}.
	 *
	 * @return an array filled with non {@code null} {@code Output} {@code Objects}
	 */
	public Output[] getOutputs() {
		Output[] outputs = new Output[getSize()];
		int counter = 0;
		for (int i = 0; i < pvOutputs.size(); i++) {
			Output[] row = pvOutputs.get(i);
			if (row == null) {
				continue;
			}
			for (int j = 0; j < row.length; j++) {
				if (row[j] != null) {
					outputs[counter++] = row[j];
				}
			}
		}
		return outputs;
	}

	/**
	 * Used for converting the current {@code Analysis} into a JSON {@code String}.
	 *
	 * @return the current {@code Analysis} as a JSON {@code String}
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(128).append('[');
		boolean first = true;
		for (int i = 0; i < pvOutputs.size(); i++) {
			Output[] row = pvOutputs.get(i);
			if (row == null) {
				continue;
			}
			for (int j = 0; j < row.length; j++) {
				Output o = row[j];
				if (o == null) {
					continue;
				}
				if (!first) {
					sb.append(", ");
				}
				sb.append('"').append(o.toString()).append('"');
				first = false;
			}
		}
		return sb.append(']').toString();
	}

	/**
	 * Used for calculating the total amount of non {@code null} {@code Output} {@code Objects}.
	 *
	 * @return the total amount of non {@code null} {@code Output} {@code Objects}
	 */
	public int getSize() {
		int count = 0;
		for (int i = 0, n = pvOutputs.size(); i < n; i++) {
			Output[] row = pvOutputs.get(i);
			if (row == null) {
				continue;
			}
			for (int j = 0; j < row.length; j++) {
				if (row[j] != null) {
					count++;
				}
			}
		}
		return count;
	}

	/**
	 * Used for retrieving the best move from the first principal variation.
	 *
	 * @return the best {@code Move}, or {@link Move#NO_MOVE} if unavailable
	 */
	public short getBestMove() {
		return getBestMove(1);
	}

	/**
	 * Used for retrieving the best move from a specified principal variation index.
	 *
	 * @param pivot the index of the principal variation to extract the best move from (1-based)
	 * @return the best {@code Move}, or {@link Move#NO_MOVE} if unavailable
	 */
	public short getBestMove(int pivot) {
		if (pivot < 1 || pivot > pvOutputs.size()) {
			return Move.NO_MOVE;
		}
		Output[] row = pvOutputs.get(pivot - 1);
		if (row == null) {
			return Move.NO_MOVE;
	}
		Output last = null;
		for (int i = row.length - 1; i >= 0; i--) {
			if (row[i] != null) {
				last = row[i];
				break;
			}
		}
		if (last == null) {
			return Move.NO_MOVE;
		}
		short[] moves = last.getMoves();
		return (moves != null && moves.length > 0) ? moves[0] : Move.NO_MOVE;
	}

	/**
	 * Used for retrieving the number of principal variations (PVs) currently stored.
	 *
	 * @return the count of PV lists held by this analysis
	 */
	public int getPivots() {
		return pvOutputs.size();
	}

	/**
	 * Used for checking whether this analysis currently holds no outputs.
	 *
	 * @return {@code true} if the analysis contains no entries, otherwise {@code false}
	 */
	public boolean isEmpty() {
		return pvOutputs.isEmpty();
	}

	/**
	 * Used for creating a deep copy of this {@code Analysis} instance.
	 *
	 * @return a new {@code Analysis} containing copies of all {@code Output} entries
	 */
	public Analysis copyOf() {
		Analysis analysis = new Analysis();
		for (int i = 0; i < pvOutputs.size(); i++) {
			Output[] row = pvOutputs.get(i);
			if (row == null) {
				continue;
			}
			for (int j = 0; j < row.length; j++) {
				if (row[j] != null) {
					analysis.add(new Output(row[j]));
				}
			}
		}
		return analysis;
	}

	/**
	 * Used for ensuring the given row can hold at least {@code requiredLen} elements, growing by
	 * 1.5x when expansion is needed.
	 *
	 * @param row the current row array, possibly {@code null}
	 * @param requiredLen required minimum length
	 * @return a non-null array with capacity for {@code requiredLen} elements
	 */
	private static Output[] ensure(Output[] row, int requiredLen) {
		if (row == null) {
			return new Output[requiredLen];
		}
		if (requiredLen <= row.length) {
			return row;
		}
		int newLen = Math.max(requiredLen, row.length + (row.length >>> 1) + 1);
		return Arrays.copyOf(row, newLen);
	}
}
