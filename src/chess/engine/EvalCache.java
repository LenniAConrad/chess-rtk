package chess.engine;


/**
 * Fixed-size direct-mapped evaluation cache.
 *
 * <p>
 * This cache avoids repeatedly invoking expensive neural evaluators on the
 * same signed position during one search.
 * </p>
 */
final class EvalCache {

    /**
     * Direct-addressed evaluation buckets.
     */
    final EvalEntry[] entries;

    /**
     * Bit mask used to map mixed signatures into the entry array.
     */
    final int mask;

    /**
     * Creates a direct-mapped evaluation cache.
     *
     * @param size power-of-two size
     * @throws IllegalArgumentException if size is not a power of two
     */
    EvalCache(int size) {
        if (Integer.bitCount(size) != 1) {
            throw new IllegalArgumentException("size must be power of two");
        }
        this.entries = new EvalEntry[size];
        this.mask = size - 1;
    }

    /**
     * Reads a cached score.
     *
     * @param key position signature to look up
     * @return cached entry, or null when the bucket is empty or holds another
     *         signature
     */
    EvalEntry get(long key) {
        EvalEntry entry = entries[index(key)];
        return entry != null && entry.key == key ? entry : null;
    }

    /**
     * Stores a score.
     *
     * @param key position signature for the cached evaluation
     * @param score static score to cache
     */
    void put(long key, int score) {
        int index = index(key);
        EvalEntry entry = entries[index];
        if (entry == null) {
            entry = new EvalEntry();
            entries[index] = entry;
        }
        entry.key = key;
        entry.score = score;
    }

    /**
     * Computes an index.
     *
     * @param key position signature to map
     * @return evaluation-cache index
     */
    int index(long key) {
        long mixed = key ^ (key >>> 32);
        return (int) mixed & mask;
    }
}
