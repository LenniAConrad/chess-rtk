package chess.core;

/**
 * Utility methods and board masks for the core move generator.
 *
 * <p>
 * Bit indexes intentionally match {@link Field}'s board indexes:
 * {@code a8 == 0} and {@code h1 == 63}. That keeps generated moves directly
 * compatible with {@code chess.core.Move}.
 * </p>
 *
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class Bits {

    /**
     * Mask containing every square on file A.
     */
    public static final long FILE_A = fileMask(0);

    /**
     * Mask containing every square on file B.
     */
    public static final long FILE_B = fileMask(1);

    /**
     * Mask containing every square on file C.
     */
    public static final long FILE_C = fileMask(2);

    /**
     * Mask containing every square on file D.
     */
    public static final long FILE_D = fileMask(3);

    /**
     * Mask containing every square on file E.
     */
    public static final long FILE_E = fileMask(4);

    /**
     * Mask containing every square on file F.
     */
    public static final long FILE_F = fileMask(5);

    /**
     * Mask containing every square on file G.
     */
    public static final long FILE_G = fileMask(6);

    /**
     * Mask containing every square on file H.
     */
    public static final long FILE_H = fileMask(7);

    /**
     * Mask containing every square on rank 1.
     */
    public static final long RANK_1 = rankMask(0);

    /**
     * Mask containing every square on rank 2.
     */
    public static final long RANK_2 = rankMask(1);

    /**
     * Mask containing every square on rank 3.
     */
    public static final long RANK_3 = rankMask(2);

    /**
     * Mask containing every square on rank 4.
     */
    public static final long RANK_4 = rankMask(3);

    /**
     * Mask containing every square on rank 5.
     */
    public static final long RANK_5 = rankMask(4);

    /**
     * Mask containing every square on rank 6.
     */
    public static final long RANK_6 = rankMask(5);

    /**
     * Mask containing every square on rank 7.
     */
    public static final long RANK_7 = rankMask(6);

    /**
     * Mask containing every square on rank 8.
     */
    public static final long RANK_8 = rankMask(7);

    /**
     * Empty bitboard value with no occupied squares.
     */
    public static final long EMPTY = 0L;

    /**
     * Prevents instantiation of this stateless utility class.
     */
    private Bits() {
        // utility
    }

    /**
     * Returns a bitboard with exactly one square set.
     *
     * @param square board square, 0..63
     * @return mask containing only {@code square}
     */
    public static long bit(int square) {
        requireSquare(square);
        return 1L << square;
    }

    /**
     * Returns whether a bitboard contains a square.
     *
     * @param bitboard bitboard
     * @param square square to test
     * @return true when occupied
     */
    public static boolean contains(long bitboard, int square) {
        return (bitboard & bit(square)) != 0L;
    }

    /**
     * Returns the least significant set bit index.
     *
     * <p>
     * The result is the square index of the lowest-numbered occupied square.
     * This package uses that as the standard iterator primitive for bitboards.
     * </p>
     *
     * @param bitboard non-empty bitboard
     * @return square index
     */
    public static int lsb(long bitboard) {
        if (bitboard == 0L) {
            throw new IllegalArgumentException("bitboard is empty");
        }
        return Long.numberOfTrailingZeros(bitboard);
    }

    /**
     * Clears the least significant set bit.
     *
     * <p>
     * This is the companion operation for {@link #lsb(long)} and is used when
     * walking all set bits in a mask.
     * </p>
     *
     * @param bitboard bitboard
     * @return bitboard without its least significant bit
     */
    public static long withoutLsb(long bitboard) {
        return bitboard & (bitboard - 1L);
    }

    /**
     * Counts set bits in a bitboard.
     *
     * @param bitboard bitboard
     * @return population count
     */
    public static int popcount(long bitboard) {
        return Long.bitCount(bitboard);
    }

    /**
     * Returns a square's file index.
     *
     * @param square board square, 0..63
     * @return file index, 0 for file a and 7 for file h
     */
    public static int file(int square) {
        requireSquare(square);
        return square & 7;
    }

    /**
     * Returns a square's rank index.
     *
     * @param square board square, 0..63
     * @return rank index, 0 for rank 1 and 7 for rank 8
     */
    public static int rank(int square) {
        requireSquare(square);
        return 7 - (square >>> 3);
    }

    /**
     * Converts file/rank coordinates into the repository square index.
     *
     * @param file file index, 0..7
     * @param rank rank index, 0..7
     * @return square
     */
    public static int square(int file, int rank) {
        if (!Field.isOnBoard(file, rank)) {
            throw new IllegalArgumentException("Invalid square coordinates: " + file + "," + rank);
        }
        return Field.toIndex(file, rank);
    }

    /**
     * Returns coordinate text for a square, such as {@code e4}.
     *
     * @param square board square, 0..63
     * @return coordinate text
     */
    public static String name(int square) {
        requireSquare(square);
        return Field.toString((byte) square);
    }

    /**
     * Validates that a square index is inside the 64-square board.
     *
     * @param square square index to validate
     */
    public static void requireSquare(int square) {
        if (square < 0 || square >= 64) {
            throw new IllegalArgumentException("Invalid square: " + square);
        }
    }

    /**
     * Builds a mask for all squares on one file.
     *
     * @param file file index, 0..7
     * @return file mask
     */
    private static long fileMask(int file) {
        long mask = 0L;
        for (int rank = 0; rank < 8; rank++) {
            mask |= 1L << Field.toIndex(file, rank);
        }
        return mask;
    }

    /**
     * Builds a mask for all squares on one rank.
     *
     * @param rank rank index, 0..7
     * @return rank mask
     */
    private static long rankMask(int rank) {
        long mask = 0L;
        for (int file = 0; file < 8; file++) {
            mask |= 1L << Field.toIndex(file, rank);
        }
        return mask;
    }
}
