package chess.core;

/**
 * Sliding-piece attack calculator for bishops and rooks.
 *
 * <p>
 * Runtime attack queries use hyperbola-style line arithmetic. The compact tables
 * are still built and exposed for regression checks of relevant blocker masks,
 * keeping the implementation easy to audit without adding magic constants or
 * external generated data.
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
     * Per-square bishop tables used for relevant blocker mask validation.
     */
    private static final Table[] BISHOP_TABLES = buildTables(BISHOP_DIRECTIONS);

    /**
     * Per-square rook tables used for relevant blocker mask validation.
     */
    private static final Table[] ROOK_TABLES = buildTables(ROOK_DIRECTIONS);

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
        return lineAttacks(square, occupancy, DIAGONAL_MASKS[square])
                | lineAttacks(square, occupancy, ANTIDIAGONAL_MASKS[square]);
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
        return lineAttacks(square, occupancy, RANK_MASKS[square])
                | lineAttacks(square, occupancy, FILE_MASKS[square]);
    }

    /**
     * Returns the relevant blocker mask for bishop attacks from a square.
     *
     * <p>
     * The mask excludes edge squares, matching the standard compact table
     * construction used by magic-style sliding attack generators.
     * </p>
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
     * <p>
     * The mask excludes edge squares, matching the standard compact table
     * construction used by magic-style sliding attack generators.
     * </p>
     *
     * @param square origin square
     * @return blocker mask
     */
    public static long rookRelevantBlockers(int square) {
        Bits.requireSquare(square);
        return ROOK_TABLES[square].mask;
    }

    /**
     * Builds per-square lookup tables for one sliding piece family.
     *
     * @param directions ray direction vectors
     * @return lookup table for each origin square
     */
    private static Table[] buildTables(int[][] directions) {
        Table[] tables = new Table[64];
        for (int square = 0; square < tables.length; square++) {
            long mask = relevantBlockers(square, directions);
            int[] bits = maskBits(mask);
            long[] attacks = new long[1 << bits.length];
            for (int index = 0; index < attacks.length; index++) {
                long blockers = blockerSubset(index, bits);
                attacks[index] = rayAttacks(square, blockers, directions);
            }
            tables[square] = new Table(mask, bits, attacks);
        }
        return tables;
    }

    /**
     * Builds the relevant occupancy mask for one square.
     *
     * <p>
     * Edge squares are excluded from the blocker mask because a blocker on the edge
     * does not change which squares before the edge are attacked. The edge square is
     * always included in the attack result unless a nearer blocker stops the ray.
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
     * Returns the set-bit square indexes in a mask.
     *
     * @param mask source mask
     * @return square indexes in least-significant-bit order
     */
    private static int[] maskBits(long mask) {
        int[] bits = new int[Long.bitCount(mask)];
        int index = 0;
        while (mask != 0L) {
            int square = Bits.lsb(mask);
            mask = Bits.withoutLsb(mask);
            bits[index++] = square;
        }
        return bits;
    }

    /**
     * Expands a compact table index into a blocker bitboard.
     *
     * @param index compact index
     * @param bits relevant square list
     * @return blocker bitboard
     */
    private static long blockerSubset(int index, int[] bits) {
        long out = 0L;
        for (int bit = 0; bit < bits.length; bit++) {
            if ((index & (1 << bit)) != 0) {
                out |= Bits.bit(bits[bit]);
            }
        }
        return out;
    }

    /**
     * Computes sliding attacks by walking rays.
     *
     * <p>
     * This intentionally simple implementation is used only while building the
     * compact validation tables.
     * </p>
     *
     * @param square origin square
     * @param occupancy occupied squares
     * @param directions ray direction vectors
     * @return attack mask
     */
    private static long rayAttacks(int square, long occupancy, int[][] directions) {
        long attacks = 0L;
        int file = Bits.file(square);
        int rank = Bits.rank(square);
        for (int[] direction : directions) {
            int targetFile = file + direction[0];
            int targetRank = rank + direction[1];
            while (Field.isOnBoard(targetFile, targetRank)) {
                int target = Bits.square(targetFile, targetRank);
                attacks |= Bits.bit(target);
                if ((occupancy & Bits.bit(target)) != 0L) {
                    break;
                }
                targetFile += direction[0];
                targetRank += direction[1];
            }
        }
        return attacks;
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
     * Computes attacks on one rank, file, or diagonal line.
     *
     * <p>
     * The formula subtracts the origin bit from both forward and reversed
     * occupancy views, then combines both spans to recover blockers in both
     * directions.
     * </p>
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
     * Compact lookup table for one origin square.
     */
    private static final class Table {

        /**
         * Relevant blocker mask for this square.
         */
        private final long mask;

        /**
         * Single-square masks for every relevant blocker square.
         */
        private final long[] bitMasks;

        /**
         * Attack masks indexed by compressed relevant occupancy.
         */
        private final long[] attacks;

        /**
         * Creates a compact attack table.
         *
         * @param mask relevant blocker mask
         * @param bits relevant blocker square indexes
         * @param attacks attack masks indexed by compressed occupancy
         */
        private Table(long mask, int[] bits, long[] attacks) {
            this.mask = mask;
            this.bitMasks = new long[bits.length];
            for (int i = 0; i < bits.length; i++) {
                this.bitMasks[i] = 1L << bits[i];
            }
            this.attacks = attacks;
        }

        /**
         * Compresses relevant occupancy bits into a table index.
         *
         * @param relevantOccupancy occupancy masked to relevant squares
         * @return compact index
         */
        private int index(long relevantOccupancy) {
            int out = 0;
            for (int bit = 0; bit < bitMasks.length; bit++) {
                if ((relevantOccupancy & bitMasks[bit]) != 0L) {
                    out |= 1 << bit;
                }
            }
            return out;
        }
    }
}
