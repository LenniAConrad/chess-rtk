package chess.core;

/**
 * Sliding-piece attack calculator for bishops and rooks.
 *
 * <p>
 * Runtime attack queries use magic-bitboard lookups: the relevant blockers are
 * masked, multiplied by a per-square magic, and shifted to index a precomputed
 * attack table. The tables are built once at class-initialization from the
 * hyperbola-style line arithmetic kept below as the reference implementation, and
 * the magics are found deterministically at init (no external generated data),
 * so the lookup is bit-for-bit identical to the reference but branch-free and
 * single-multiply per piece line.
 * </p>
 *
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class SlidingAttacks {

    /**
     * Direction vectors for bishop diagonals in file/rank coordinates.
     */
    private static final int[][] BISHOP_DIRECTIONS = {
            { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 }
    };

    /**
     * Direction vectors for rook ranks and files in file/rank coordinates.
     */
    private static final int[][] ROOK_DIRECTIONS = {
            { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }
    };

    /**
     * Full rank masks, including the origin square, for line attack arithmetic.
     */
    private static final long[] RANK_MASKS = new long[64];

    /**
     * Full file masks, including the origin square, for line attack arithmetic.
     */
    private static final long[] FILE_MASKS = new long[64];

    /**
     * Full main-diagonal masks, including the origin square.
     */
    private static final long[] DIAGONAL_MASKS = new long[64];

    /**
     * Full anti-diagonal masks, including the origin square.
     */
    private static final long[] ANTIDIAGONAL_MASKS = new long[64];

    static {
        for (int square = 0; square < 64; square++) {
            RANK_MASKS[square] = lineMask(square, 1, 0) | lineMask(square, -1, 0) | (1L << square);
            FILE_MASKS[square] = lineMask(square, 0, 1) | lineMask(square, 0, -1) | (1L << square);
            DIAGONAL_MASKS[square] = lineMask(square, 1, 1) | lineMask(square, -1, -1) | (1L << square);
            ANTIDIAGONAL_MASKS[square] = lineMask(square, 1, -1) | lineMask(square, -1, 1) | (1L << square);
        }
    }

    /**
     * Per-square magic tables for bishop attacks.
     */
    private static final Table[] BISHOP_TABLES = buildMagicTables(BISHOP_DIRECTIONS, true);

    /**
     * Per-square magic tables for rook attacks.
     */
    private static final Table[] ROOK_TABLES = buildMagicTables(ROOK_DIRECTIONS, false);

    /**
     * Prevents instantiation of this stateless utility class.
     */
    private SlidingAttacks() {
        // utility
    }

    /**
     * Returns bishop attacks from a square for the given occupancy.
     *
     * <p>
     * Friendly and enemy blockers are treated the same; callers mask out their
     * own pieces after obtaining the attack ray.
     * </p>
     *
     * @param square origin square
     * @param occupancy occupied squares
     * @return attack bitboard
     */
    public static long bishopAttacks(int square, long occupancy) {
        return BISHOP_TABLES[square].lookup(occupancy);
    }

    /**
     * Returns rook attacks from a square for the given occupancy.
     *
     * <p>
     * Friendly and enemy blockers are treated the same; callers mask out their
     * own pieces after obtaining the attack ray.
     * </p>
     *
     * @param square origin square
     * @param occupancy occupied squares
     * @return attack bitboard
     */
    public static long rookAttacks(int square, long occupancy) {
        return ROOK_TABLES[square].lookup(occupancy);
    }

    /**
     * Returns the relevant blocker mask for bishop attacks from a square.
     *
     * @param square origin square
     * @return blocker mask
     */
    public static long bishopRelevantBlockers(int square) {
        Bits.requireSquare(square);
        return BISHOP_TABLES[square].mask;
    }

    /**
     * Returns the relevant blocker mask for rook attacks from a square.
     *
     * @param square origin square
     * @return blocker mask
     */
    public static long rookRelevantBlockers(int square) {
        Bits.requireSquare(square);
        return ROOK_TABLES[square].mask;
    }

    /**
     * Builds per-square magic tables for one sliding piece family.
     *
     * @param directions ray direction vectors
     * @param bishop true for bishops, false for rooks (selects the reference)
     * @return magic table for each origin square
     */
    private static Table[] buildMagicTables(int[][] directions, boolean bishop) {
        Table[] tables = new Table[64];
        // Deterministic xorshift state so the magics are reproducible across runs.
        long[] rng = { 0x9E3779B97F4A7C15L ^ (bishop ? 0xD1B54A32D192ED03L : 0xA0761D6478BD642FL) };
        for (int square = 0; square < 64; square++) {
            long mask = relevantBlockers(square, directions);
            tables[square] = buildMagic(square, mask, bishop, rng);
        }
        return tables;
    }

    /**
     * Finds a magic and builds the attack table for one square.
     *
     * @param square origin square
     * @param mask relevant blocker mask
     * @param bishop true for bishop attacks
     * @param rng one-element deterministic xorshift state
     * @return populated magic table
     */
    private static Table buildMagic(int square, long mask, boolean bishop, long[] rng) {
        int bits = Long.bitCount(mask);
        int size = 1 << bits;
        long[] occupancies = new long[size];
        long[] reference = new long[size];
        long subset = 0L;
        for (int i = 0; i < size; i++) {
            occupancies[i] = subset;
            reference[i] = bishop ? bishopReference(square, subset) : rookReference(square, subset);
            subset = (subset - mask) & mask;
        }
        int shift = 64 - bits;
        long[] table = new long[size];
        int[] usedEpoch = new int[size];
        int epoch = 0;
        while (true) {
            long magic = sparseRandom(rng);
            // Reject magics that scatter the mask's top byte poorly (standard filter).
            if (Long.bitCount((mask * magic) & 0xFF00000000000000L) < 6) {
                continue;
            }
            epoch++;
            boolean ok = true;
            for (int i = 0; i < size; i++) {
                int index = (int) ((occupancies[i] * magic) >>> shift);
                if (usedEpoch[index] != epoch) {
                    usedEpoch[index] = epoch;
                    table[index] = reference[i];
                } else if (table[index] != reference[i]) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                return new Table(mask, magic, shift, table);
            }
        }
    }

    /**
     * Deterministic sparse 64-bit value (AND of three xorshift draws), as used by
     * magic-number searches to favour magics with few set bits.
     *
     * @param state one-element xorshift state, updated in place
     * @return sparse pseudo-random value
     */
    private static long sparseRandom(long[] state) {
        return nextRandom(state) & nextRandom(state) & nextRandom(state);
    }

    /**
     * Advances an xorshift64 generator.
     *
     * @param state one-element state, updated in place
     * @return next pseudo-random value
     */
    private static long nextRandom(long[] state) {
        long x = state[0];
        x ^= x << 13;
        x ^= x >>> 7;
        x ^= x << 17;
        state[0] = x;
        return x;
    }

    /**
     * Reference bishop attacks via hyperbola line arithmetic (table source).
     *
     * @param square origin square
     * @param occupancy occupied squares
     * @return attack bitboard
     */
    private static long bishopReference(int square, long occupancy) {
        return lineAttacks(square, occupancy, DIAGONAL_MASKS[square])
                | lineAttacks(square, occupancy, ANTIDIAGONAL_MASKS[square]);
    }

    /**
     * Reference rook attacks via hyperbola line arithmetic (table source).
     *
     * @param square origin square
     * @param occupancy occupied squares
     * @return attack bitboard
     */
    private static long rookReference(int square, long occupancy) {
        return lineAttacks(square, occupancy, RANK_MASKS[square])
                | lineAttacks(square, occupancy, FILE_MASKS[square]);
    }

    /**
     * Builds the relevant occupancy mask for one square.
     *
     * <p>
     * Edge squares are excluded from the blocker mask because a blocker on the edge
     * does not change which squares before the edge are attacked.
     * </p>
     *
     * @param square origin square
     * @param directions piece directions
     * @return mask
     */
    private static long relevantBlockers(int square, int[][] directions) {
        long mask = 0L;
        int file = Bits.file(square);
        int rank = Bits.rank(square);
        for (int[] direction : directions) {
            int targetFile = file + direction[0];
            int targetRank = rank + direction[1];
            while (Field.isOnBoard(targetFile, targetRank)
                    && Field.isOnBoard(targetFile + direction[0], targetRank + direction[1])) {
                mask |= Bits.bit(Bits.square(targetFile, targetRank));
                targetFile += direction[0];
                targetRank += direction[1];
            }
        }
        return mask;
    }

    /**
     * Builds a one-direction line mask from an origin square.
     *
     * @param square origin square
     * @param fileDelta file step for the ray
     * @param rowDelta row step for the ray
     * @return mask of all squares reached in that direction
     */
    private static long lineMask(int square, int fileDelta, int rowDelta) {
        long mask = 0L;
        int file = square & 7;
        int row = square >>> 3;
        file += fileDelta;
        row += rowDelta;
        while (file >= 0 && file < 8 && row >= 0 && row < 8) {
            mask |= 1L << (row * 8 + file);
            file += fileDelta;
            row += rowDelta;
        }
        return mask;
    }

    /**
     * Computes attacks on one rank, file, or diagonal line (hyperbola method).
     *
     * @param square origin square
     * @param occupancy occupied squares
     * @param mask full line mask including the origin square
     * @return attack mask on that line
     */
    private static long lineAttacks(int square, long occupancy, long mask) {
        long bit = 1L << square;
        long forward = (occupancy & mask) - (bit << 1);
        long reverseBit = Long.reverse(bit);
        long reverseOccupancy = Long.reverse(occupancy & mask);
        long reverse = reverseOccupancy - (reverseBit << 1);
        return (forward ^ Long.reverse(reverse)) & mask;
    }

    /**
     * Per-square magic attack table.
     */
    private static final class Table {

        /**
         * Relevant blocker mask for this square.
         */
        private final long mask;

        /**
         * Magic multiplier mapping masked occupancy to a table index.
         */
        private final long magic;

        /**
         * Right shift applied after the magic multiply ({@code 64 - bits}).
         */
        private final int shift;

        /**
         * Precomputed attacks indexed by the magic hash of the masked occupancy.
         */
        private final long[] attacks;

        /**
         * Creates a magic attack table.
         *
         * @param mask relevant blocker mask
         * @param magic magic multiplier
         * @param shift index shift
         * @param attacks attack table
         */
        private Table(long mask, long magic, int shift, long[] attacks) {
            this.mask = mask;
            this.magic = magic;
            this.shift = shift;
            this.attacks = attacks;
        }

        /**
         * Looks up the attack bitboard for an occupancy.
         *
         * @param occupancy occupied squares
         * @return attack bitboard
         */
        private long lookup(long occupancy) {
            return attacks[(int) (((occupancy & mask) * magic) >>> shift)];
        }
    }
}
