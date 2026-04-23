package chess.core;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Growable list of compact encoded moves.
 *
 * <p>
 * Checked methods are available for general callers. Hot-path methods skip
 * selected checks when the caller already owns capacity and bounds invariants.
 * </p>
 *
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class MoveList {

    /**
     * Backing array containing encoded moves.
     *
     * <p>
     * Only entries in the range {@code [0, size)} are valid.
     * </p>
     */
    private short[] moves;

    /**
     * Number of valid moves currently stored in {@link #moves}.
     */
    private int size;

    /**
     * Creates a move list with the default capacity used by perft scratch lists.
     */
    public MoveList() {
        this(256);
    }

    /**
     * Creates a move list with a caller-selected initial capacity.
     *
     * @param capacity initial capacity
     */
    public MoveList(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.moves = new short[capacity];
    }

    /**
     * Adds one move, growing the backing array when needed.
     *
     * @param move encoded move
     */
    public void add(short move) {
        if (size == moves.length) {
            moves = Arrays.copyOf(moves, moves.length * 2);
        }
        moves[size++] = move;
    }

    /**
     * Adds one move without checking capacity.
     *
     * <p>
     * This is intended for generator hot paths that use the default capacity and
     * know the current position cannot exceed it.
     * </p>
     *
     * @param move encoded move
     */
    void addFast(short move) {
        moves[size++] = move;
    }

    /**
     * Returns one move with bounds checking.
     *
     * @param index list index
     * @return encoded move
     */
    public short get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(index + " / " + size);
        }
        return moves[index];
    }

    /**
     * Returns a random stored move.
     *
     * @return random move, or {@link Move#NO_MOVE} when the list is empty
     */
    public short getRandomMove() {
        if (size == 0) {
            return Move.NO_MOVE;
        }
        return moves[ThreadLocalRandom.current().nextInt(size)];
    }

    /**
     * Returns the number of stored moves.
     *
     * @return move count
     */
    public int size() {
        return size;
    }

    /**
     * Returns whether the list contains no moves.
     *
     * @return true when empty
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Clears the list while keeping the allocated backing array.
     */
    public void clear() {
        size = 0;
    }

    /**
     * Removes one move by shifting later entries left.
     *
     * @param index move index to remove
     */
    public void removeAt(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(index + " / " + size);
        }
        int count = size - index - 1;
        if (count > 0) {
            System.arraycopy(moves, index + 1, moves, index, count);
        }
        moves[--size] = Move.NO_MOVE;
    }

    /**
     * Returns one move without bounds checks.
     *
     * <p>
     * Performance-sensitive callers use this while iterating up to
     * {@link #size()}.
     * </p>
     *
     * @param index list index known to be valid
     * @return encoded move
     */
    public short raw(int index) {
        return moves[index];
    }

    /**
     * Returns a compact copy of the stored moves.
     *
     * @return array whose length is exactly {@link #size()}
     */
    public short[] toArray() {
        return Arrays.copyOf(moves, size);
    }

    /**
     * Returns whether an equivalent encoded move is present.
     *
     * @param move encoded move to find
     * @return true when present
     */
    public boolean contains(short move) {
        for (int i = 0; i < size; i++) {
            if (Move.equals(moves[i], move)) {
                return true;
            }
        }
        return false;
    }
}
