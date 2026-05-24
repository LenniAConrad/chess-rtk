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
 * @author Lennart A. Conrad
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
	 * Marks this analysis as completed so callers can skip redundant engine runs.
	 *
	 * @param completed whether the analysis has already been completed
	 * @return this {@code Analysis} instance for chaining
	 */
	public Analysis setCompleted(boolean completed) {
		this.completed = completed;
		return this;
	}

	/**
	 * Checks whether the analysis has been marked as completed.
	 *
	 * @return {@code true} if this analysis contains finalized results
	 */
	public boolean isCompleted() {
		return completed;
	}

	/**
	 * Parses and adds multiple engine output lines to this analysis.
	 *
	 * <p>
	 * Each string is parsed into an {@link Output}, and the valid ones are inserted
	 * into the PV/depth grid.
	 * </p>
	 *
	 * @param string array of engine/UCI output lines to parse; must not be {@code null}
	 * @return this analysis instance for chaining
	 */
	public Analysis addAll(String[] string) {
		for (int i = 0; i < string.length; i++) {
			add(string[i]);
		}
		return this;
	}

	/**
	 * Adds multiple {@link Output} instances into the PV/depth grid.
	 *
	 * @param output array of {@code Output} items to add; must not be {@code null}
	 * @return this analysis instance for chaining
	 */
	public Analysis addAll(Output[] output) {
		for (int i = 0; i < output.length; i++) {
			add(output[i]);
		}
		return this;
	}

	/**
	 * Parses and adds a single engine output line.
	 *
	 * @param string engine/UCI output line to parse; must not be {@code null}
	 * @return this analysis instance for chaining
	 */
	public Analysis add(String string) {
		add(new Output(string));
		return this;
	}

	/**
	 * Inserts a single {@link Output} into the principal-variation grid.
	 *
	 * @param output {@code Output} instance to record; invalid outputs are ignored
	 * @return this analysis instance for chaining
	 */
	public Analysis add(Output output) {
		if (output == null || !output.hasContent()) {
			return this;
		}
		final int pvIdx = output.getPrincipalVariation() - 1;
		if (pvIdx < 0) {
			return this;
		}
		while (pvIdx >= pvOutputs.size()) {
			pvOutputs.add(null);
		}
		Output[] row = pvOutputs.get(pvIdx);
		final int depth = output.hasDepth() ? output.getDepth() : 0;
		if (depth < 0) {
			return this;
		}
		row = ensure(row, depth + 1);
		row[depth] = output;
		pvOutputs.set(pvIdx, row);
		return this;
	}

	/**
	 * Retrieves the last {@code Output} from the deepest principal variation available.
	 *
	 * @return deepest {@code Output}, or {@code null} if none stored
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
	 * Shortcut to fetch the latest {@code Output} from the first principal variation.
	 *
	 * @return greatest {@code Output} from PV1, or {@code null} if unavailable
	 */
	public Output getBestOutput() {
		return getBestOutput(1);
	}

	/**
	 * Retrieves the last {@code Output} for the specified principal variation index.
	 *
	 * @param principalVariation principal variation index (1-based)
	 * @return last {@code Output} for that PV, or {@code null} if out of range
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
	 * Retrieves the {@code Output} stored at the given depth/PV coordinates.
	 *
	 * @param depth depth index within the principal variation (0-based)
	 * @param principalVariation principal variation index (1-based)
	 * @return {@code Output} at that cell or {@code null} when missing/out of bounds
	 */
	public Output get(int depth, int principalVariation) {
		if (principalVariation < 1 || principalVariation > pvOutputs.size()) {
			return null;
		}
		Output[] row = pvOutputs.get(principalVariation - 1);
		return (row == null || depth < 0 || depth >= row.length) ? null : row[depth];
	}

	/**
	 * Collects all stored {@code Output} instances into a dense array.
	 *
	 * @return array of all non-null {@code Output} entries
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
	 * Converts the stored outputs into a JSON array string for debugging or serialization.
	 *
	 * @return JSON representation of this analysis grid
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
	 * Counts how many {@code Output} entries are currently stored.
	 *
	 * @return total non-null {@code Output} count
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
	 * Returns the best move (first ply) of PV1.
	 *
	 * @return best {@code Move}, or {@link Move#NO_MOVE} if missing
	 */
	public short getBestMove() {
		return getBestMove(1);
	}

	/**
	 * Returns the best move (first ply) of the specified principal variation.
	 *
	 * @param pivot principal variation index (1-based)
	 * @return best {@code Move} for that PV, or {@link Move#NO_MOVE} if none
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
	 * Returns the count of principal variations currently held.
	 *
	 * @return number of PV lists
	 */
	public int getPivots() {
		return pvOutputs.size();
	}

	/**
	 * Checks whether this analysis currently contains no outputs.
	 *
	 * @return {@code true} when empty, {@code false} otherwise
	 */
	public boolean isEmpty() {
		return pvOutputs.isEmpty();
	}

	/**
	 * Creates a deep copy of this {@code Analysis}, duplicating every stored {@code Output}.
	 *
	 * @return cloned {@code Analysis}
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
