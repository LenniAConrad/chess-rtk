package chess.classical;

import chess.core.Bits;
import chess.core.MoveGenerator;
import chess.core.Position;

/**
 * Evaluates a position using a simple <em>mobility</em> heuristic.
 *
 * <p>
 * Mobility here means the number of <strong>pseudo-legal</strong> destinations
 * available to each side from the current bitboard layout:
 * </p>
 * <ul>
 * <li>Moves are counted if the destination square is empty or occupied by an
 * enemy piece.</li>
 * <li>For sliding pieces, rays stop at the first occupied square (optionally
 * counting that square if it holds an enemy piece).</li>
 * <li>For pawns, forward pushes are counted as long as intermediate squares are
 * empty; diagonal captures are counted if an enemy piece is present.</li>
 * </ul>
 *
 * <p>
 * <strong>Important limitations:</strong> this is intentionally fast and does
 * not attempt to be a full legal move generator.
 * It therefore ignores:
 * </p>
 * <ul>
 * <li>Check legality (e.g., pinned pieces and king moves into check are still
 * counted).</li>
 * <li>Castling and en passant.</li>
 * <li>Promotion-specific move expansion (a pawn move to the last rank is
 * counted once).</li>
 * </ul>
 *
 * <p>
 * The returned score is symmetric: positive values favor White, negative values
 * favor Black.
 * </p>
 *
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class Mobility {

    /**
     * Weight applied when translating the raw mobility difference into a
     * floating-point score.
     *
     * <p>
     * The raw mobility difference is a plain count of reachable destinations
     * (White minus Black). This factor brings the magnitude into a more
     * "evaluation-like" range.
     * </p>
     */
    private static final double MOBILITY_WEIGHT = 0.08;

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private Mobility() {
        // Utility class; prevent instantiation.
    }

    /**
     * Evaluates the provided position using the difference in pseudo-legal
     * mobility between the two sides.
     *
     * @param pos the position to evaluate
     * @return positive values when White is more mobile; negative when Black
     *         dominates
     */
    public static double evaluate(Position pos) {
        long occupancy = pos.occupancy();
        int whiteMobility = countSideMobility(pos, true, occupancy);
        int blackMobility = countSideMobility(pos, false, occupancy);
        return (whiteMobility - blackMobility) * MOBILITY_WEIGHT;
    }

    /**
     * Counts all pseudo-legal target squares reachable by the given side.
     *
     * <p>
     * This is a bitboard-only scan (no side-to-move, no check constraints). It is
     * therefore suitable as a cheap feature in other evaluators.
     * </p>
     *
     * @param pos       the position to evaluate
     * @param white     {@code true} to count White's mobility; {@code false} for Black
     * @param occupancy occupied-square mask for both sides
     * @return the mobility count for the requested side
     */
    private static int countSideMobility(Position pos, boolean white, long occupancy) {
        int mobility = 0;
        long own = pos.occupancy(white);
        long enemyKing = pos.pieces(white ? Position.BLACK_KING : Position.WHITE_KING);
        mobility += countPawnMobility(pos, white, occupancy);
        mobility += countLeaperMobility(pos.pieces(white ? Position.WHITE_KNIGHT : Position.BLACK_KNIGHT),
                own, enemyKing, true);
        mobility += countLeaperMobility(pos.pieces(white ? Position.WHITE_KING : Position.BLACK_KING),
                own, enemyKing, false);
        mobility += countBishopMobility(pos.pieces(white ? Position.WHITE_BISHOP : Position.BLACK_BISHOP),
                own, enemyKing, occupancy);
        mobility += countRookMobility(pos.pieces(white ? Position.WHITE_ROOK : Position.BLACK_ROOK),
                own, enemyKing, occupancy);
        mobility += countQueenMobility(pos.pieces(white ? Position.WHITE_QUEEN : Position.BLACK_QUEEN),
                own, enemyKing, occupancy);
        return mobility;
    }

    /**
     * Counts how many pawn pushes and captures are currently available for all
     * pawns of one side.
     *
     * <p>
     * Pushes are counted in-order (one-step, then optional two-step) and stop at
     * the first occupied square. Captures are counted only if an enemy piece is
     * present on the target square.
     * </p>
     *
     * @param pos       current position
     * @param white     {@code true} if the pawn belongs to White
     * @param occupancy occupied-square mask for both sides
     * @return number of reachable pawn target squares
     */
    private static int countPawnMobility(Position pos, boolean white, long occupancy) {
        long pawns = pos.pieces(white ? Position.WHITE_PAWN : Position.BLACK_PAWN);
        long empty = ~occupancy;
        long enemies = pos.occupancy(!white) & ~pos.pieces(white ? Position.BLACK_KING : Position.WHITE_KING);
        if (white) {
            long single = (pawns >>> 8) & empty;
            long doubles = ((single & Bits.RANK_3) >>> 8) & empty;
            long captures = (((pawns & ~Bits.FILE_A) >>> 9) | ((pawns & ~Bits.FILE_H) >>> 7)) & enemies;
            return Long.bitCount(single) + Long.bitCount(doubles) + Long.bitCount(captures);
        }
        long single = (pawns << 8) & empty;
        long doubles = ((single & Bits.RANK_6) << 8) & empty;
        long captures = (((pawns & ~Bits.FILE_A) << 7) | ((pawns & ~Bits.FILE_H) << 9)) & enemies;
        return Long.bitCount(single) + Long.bitCount(doubles) + Long.bitCount(captures);
    }

    /**
     * Counts reachable squares for fixed-offset pieces (knights and kings).
     *
     * <p>
     * A destination is counted if it is empty or occupied by an enemy piece.
     * Friendly-occupied squares are not counted.
     * </p>
     *
     * @param pieces    source-piece bitboard
     * @param own       own occupancy
     * @param enemyKing enemy king mask, excluded from pseudo-captures
     * @param knight    true for knights, false for kings
     * @return number of target squares that are empty or contain enemy pieces
     */
    private static int countLeaperMobility(long pieces, long own, long enemyKing, boolean knight) {
        int mobility = 0;
        long forbidden = own | enemyKing;
        while (pieces != 0L) {
            int from = Long.numberOfTrailingZeros(pieces);
            pieces &= pieces - 1L;
            long attacks = knight ? MoveGenerator.knightAttacks(from) : MoveGenerator.kingAttacks(from);
            mobility += Long.bitCount(attacks & ~forbidden);
        }
        return mobility;
    }

    /**
     * Walks each ray for sliding pieces and counts legal destinations.
     *
     * <p>
     * Empty squares are counted until a blocking piece is found. If the blocking
     * piece is an enemy, that square is also counted (capture square) and the ray
     * terminates.
     * </p>
     *
     * @param pieces    source-piece bitboard
     * @param own       own occupancy
     * @param enemyKing enemy king mask, excluded from pseudo-captures
     * @param occupancy occupied-square mask for both sides
     * @return number of legal destinations along the rays
     */
    private static int countBishopMobility(long pieces, long own, long enemyKing, long occupancy) {
        int mobility = 0;
        long forbidden = own | enemyKing;
        while (pieces != 0L) {
            int from = Long.numberOfTrailingZeros(pieces);
            pieces &= pieces - 1L;
            mobility += Long.bitCount(MoveGenerator.bishopAttacks(from, occupancy) & ~forbidden);
        }
        return mobility;
    }

    /**
     * Counts reachable squares for rooks.
     *
     * @param pieces    source-piece bitboard
     * @param own       own occupancy
     * @param enemyKing enemy king mask, excluded from pseudo-captures
     * @param occupancy occupied-square mask for both sides
     * @return number of legal destinations along rook rays
     */
    private static int countRookMobility(long pieces, long own, long enemyKing, long occupancy) {
        int mobility = 0;
        long forbidden = own | enemyKing;
        while (pieces != 0L) {
            int from = Long.numberOfTrailingZeros(pieces);
            pieces &= pieces - 1L;
            mobility += Long.bitCount(MoveGenerator.rookAttacks(from, occupancy) & ~forbidden);
        }
        return mobility;
    }

    /**
     * Counts reachable squares for queens.
     *
     * @param pieces    source-piece bitboard
     * @param own       own occupancy
     * @param enemyKing enemy king mask, excluded from pseudo-captures
     * @param occupancy occupied-square mask for both sides
     * @return number of legal destinations along queen rays
     */
    private static int countQueenMobility(long pieces, long own, long enemyKing, long occupancy) {
        int mobility = 0;
        long forbidden = own | enemyKing;
        while (pieces != 0L) {
            int from = Long.numberOfTrailingZeros(pieces);
            pieces &= pieces - 1L;
            long attacks = MoveGenerator.bishopAttacks(from, occupancy) | MoveGenerator.rookAttacks(from, occupancy);
            mobility += Long.bitCount(attacks & ~forbidden);
        }
        return mobility;
    }
}
