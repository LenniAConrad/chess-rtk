package chess.core;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Used for storing and managing a dynamic list of generated moves.
 * 
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class MoveList {

	/**
	 * Used for storing the moves in the list.
	 */
	protected short[] moves;

	/**
	 * Used for tracking the number of moves currently in the list.
	 */
	protected int size;

	/**
	 * Used for constructing a MoveList with a specific initial capacity.
	 *
	 * @param capacity the initial capacity of the move array
	 */
	protected MoveList(int capacity) {
		this.moves = new short[capacity];
		this.size = 0;
	}

	/**
	 * Used for constructing a MoveList with a default capacity of 128.
	 */
	protected MoveList() {
		this(128);
	}

	/**
	 * Used for retrieving the number of moves currently in the list.
	 *
	 * @return the number of moves in the list
	 */
	public int size() {
		return size;
	}

	/**
	 * Used for checking if the move list is empty.
	 *
	 * @return true if the list has no moves; false otherwise
	 */
	public boolean isEmpty() {
		return size == 0;
	}

	/**
	 * Used for adding a move to the end of the list. Automatically grows the array
	 * if full.
	 *
	 * @param m the move to be added
	 */
	public void add(short m) {
		if (size == moves.length) {
			moves = Arrays.copyOf(moves, moves.length * 2);
		}
		moves[size++] = m;
	}

	/**
	 * Used for retrieving the move at a specific index.
	 *
	 * @param index the index of the move to retrieve
	 * @return the move at the specified index
	 * @throws IndexOutOfBoundsException if the index is out of range
	 */
	public short get(int index) {
		if (index < 0 || index >= size) {
			throw new IndexOutOfBoundsException(index + " / " + size);
		}
		return moves[index];
	}

	/**
	 * Used for retrieving a random move from the list.
	 *
	 * @param index the index of the move to retrieve
	 * @return the move at the specified index
	 * @throws IndexOutOfBoundsException if the index is out of range
	 */
	public short getRandomMove() {
		return moves[ThreadLocalRandom.current().nextInt(0, size)];
	}

	/**
	 * Used for removing the move at the specified index by shifting subsequent
	 * elements left.
	 *
	 * @param index the index of the move to remove
	 */
	public void removeAt(int index) {
		int numMoved = size - index - 1;
		if (numMoved > 0) {
			System.arraycopy(moves, index + 1, moves, index, numMoved);
		}
		moves[--size] = Move.NO_MOVE;
	}

	/**
	 * Used for clearing all moves from the list while retaining the array's
	 * capacity.
	 */
	public void clear() {
		for (int i = 0; i < size; i++) {
			moves[i] = Move.NO_MOVE;
		}
		size = 0;
	}
}
