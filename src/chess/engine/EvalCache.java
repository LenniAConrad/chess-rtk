package chess.engine;


/**
 * Fixed-size direct-mapped evaluation cache.
 *
 * <p>
 * This cache avoids repeatedly invoking expensive neural evaluators on the
 * same signed position during one search. Keys and scores live in primitive
 * parallel arrays, so lookups touch two flat words instead of chasing an
 * entry object, and filling a bucket allocates nothing.
 * </p>
 */
final class EvalCache {

    /**
     * Sentinel returned by {@link #get(long)} for a miss. Static evaluators
     * return bounded centipawn scores, so this value can never be a real
     * cached score.
     */
    static final int MISS = Integer.MIN_VALUE;

    /**
     * Cached position signatures; {@code 0} marks an empty bucket, so a
     * position whose signature is exactly zero is simply never cached.
     */
    private final long[] keys;

    /**
     * Cached centipawn scores, parallel to {@link #keys}.
     */
    private final int[] scores;

    /**
     * Bit mask used to map mixed signatures into the arrays.
     */
    private final int mask;

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
        this.keys = new long[size];
        this.scores = new int[size];
        this.mask = size - 1;
    }

    /**
     * Reads a cached score.
     *
     * @param key position signature to look up
     * @return cached centipawn score, or {@link #MISS} when the bucket is
     *         empty or holds another signature
     */
    int get(long key) {
        int index = index(key);
        return keys[index] == key && key != 0L ? scores[index] : MISS;
    }

    /**
     * Stores a score.
     *
     * @param key position signature for the cached evaluation
     * @param score static score to cache
     */
    void put(long key, int score) {
        int index = index(key);
        keys[index] = key;
        scores[index] = score;
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
