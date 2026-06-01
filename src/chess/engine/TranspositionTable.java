package chess.engine;

import chess.core.Move;

/**
 * Fixed-size replacement transposition table.
 *
 * <p>
 * The table is intentionally simple: one entry per bucket, power-of-two
 * indexing, and a depth-aware replacement rule for same-generation entries.
 * </p>
 */
final class TranspositionTable {

    /**
     * Direct-addressed transposition buckets.
     */
    final Transposition[] entries;

    /**
     * Bit mask used to map mixed signatures into the entry array.
     */
    final int mask;

    /**
     * Creates a transposition table with direct indexing.
     *
     * @param size power-of-two size
     * @throws IllegalArgumentException if size is not a power of two
     */
    TranspositionTable(int size) {
        if (Integer.bitCount(size) != 1) {
            throw new IllegalArgumentException("size must be power of two");
        }
        this.entries = new Transposition[size];
        this.mask = size - 1;
    }

    /**
     * Probes a position signature.
     *
     * @param key position signature to look up
     * @return matching table entry, or null when the bucket is empty or holds
     *         another signature
     */
    Transposition probe(long key) {
        Transposition entry = entries[index(key)];
        // Tear-tolerant validation: key holds signature ^ data, so a half-written
        // entry (concurrent Lazy SMP store) fails this check and is treated as a
        // miss rather than returning a corrupt payload.
        return entry != null && (entry.key ^ entry.data) == key ? entry : null;
    }

    /**
     * Returns a stored best move, if any.
     *
     * @param key position signature to look up
     * @return best move or no move
     */
    short bestMove(long key) {
        Transposition entry = probe(key);
        return entry == null ? Move.NO_MOVE : Transposition.moveOf(entry.data);
    }

    /**
     * Stores an entry.
     *
     * @param key position signature for the stored node
     * @param depth search depth
     * @param score alpha-beta score to store
     * @param flag bound flag
     * @param bestMove best move
     * @param generation iterative-deepening generation
     */
    void store(long key, int depth, int score, byte flag, short bestMove, int generation) {
        int index = index(key);
        Transposition entry = entries[index];
        long packed = Transposition.pack(depth, score, flag, bestMove);
        if (entry == null) {
            entry = new Transposition();
            entries[index] = entry;
        } else {
            long storedKey = entry.key ^ entry.data;
            if (storedKey != key && Transposition.depthOf(entry.data) > depth
                    && entry.generation == generation) {
                return;
            }
        }
        // Write the payload first, then the XOR-folded key, so a concurrent reader
        // sees either the old or the new consistent entry, never a torn mix.
        entry.data = packed;
        entry.key = key ^ packed;
        entry.generation = generation;
    }

    /**
     * Computes an index.
     *
     * @param key position signature to map
     * @return transposition-table index
     */
    int index(long key) {
        long mixed = key ^ (key >>> 32);
        return (int) mixed & mask;
    }
}
