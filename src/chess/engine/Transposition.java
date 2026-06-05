package chess.engine;


/**
 * Mutable transposition-table entry, packed for lock-free tear tolerance.
 *
 * <p>
 * Entries are reused in-place to avoid allocation during search. The depth,
 * score, bound flag, and best move are packed into a single {@code long}
 * ({@link #data}), and {@link #key} stores the position signature XOR-ed with
 * that packed payload. A reader validates an entry with
 * {@code (key ^ data) == signature}: if another (Lazy&nbsp;SMP) thread is part
 * way through a store, the two longs are inconsistent and the check fails, so a
 * torn entry simply misses instead of returning a corrupt score. The generation
 * counter is plain (used only for the replacement policy, where a race merely
 * affects replacement quality, not correctness).
 * </p>
 */
final class Transposition {

    /**
     * Bit width of the packed best move.
     */
    private static final int MOVE_BITS = 16;

    /**
     * Bit offset of the packed bound flag.
     */
    private static final int FLAG_SHIFT = 16;

    /**
     * Bit offset of the packed remaining depth.
     */
    private static final int DEPTH_SHIFT = 18;

    /**
     * Bit width of the packed remaining depth.
     */
    private static final int DEPTH_BITS = 14;

    /**
     * Bit offset of the packed score (occupies the high 32 bits).
     */
    private static final int SCORE_SHIFT = 32;

    /**
     * Position signature XOR-ed with {@link #data}, for tear-tolerant validation.
     */
    long key;

    /**
     * Packed payload: best move, bound flag, remaining depth, and score.
     */
    long data;

    /**
     * Iterative-deepening generation in which this entry was written.
     */
    int generation;

    /**
     * Packs an entry's payload into a single {@code long}.
     *
     * @param depth remaining search depth
     * @param score stored alpha-beta score
     * @param flag bound flag (exact/lower/upper)
     * @param bestMove best move ordering hint
     * @return packed payload
     */
    static long pack(int depth, int score, byte flag, short bestMove) {
        return (bestMove & 0xFFFFL)
                | ((flag & 0x3L) << FLAG_SHIFT)
                | (((long) depth & ((1L << DEPTH_BITS) - 1)) << DEPTH_SHIFT)
                | (((long) score) << SCORE_SHIFT);
    }

    /**
     * Extracts the remaining depth from a packed payload.
     *
     * @param data packed payload
     * @return remaining depth
     */
    static int depthOf(long data) {
        return (int) ((data >>> DEPTH_SHIFT) & ((1L << DEPTH_BITS) - 1));
    }

    /**
     * Extracts the score from a packed payload.
     *
     * @param data packed payload
     * @return stored score
     */
    static int scoreOf(long data) {
        return (int) (data >> SCORE_SHIFT);
    }

    /**
     * Extracts the bound flag from a packed payload.
     *
     * @param data packed payload
     * @return bound flag
     */
    static byte flagOf(long data) {
        return (byte) ((data >>> FLAG_SHIFT) & 0x3);
    }

    /**
     * Extracts the best move from a packed payload.
     *
     * @param data packed payload
     * @return best move
     */
    static short moveOf(long data) {
        return (short) (data & ((1 << MOVE_BITS) - 1));
    }
}
