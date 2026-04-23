package chess.nn.lc0;

import chess.core.Field;
import chess.core.Move;
import chess.core.Position;

/**
 * Encodes chess moves into the LC0-style 73-plane policy index.
 *
 * <p>Policy planes are indexed by {@code planeIndex * 64 + fromSquare}, where
 * {@code fromSquare} uses {@code a1=0..h8=63} ordering and the move is encoded
 * from the side-to-move perspective (black is rank-mirrored).</p>
 *
 * <p>Plane layout (AlphaZero/LC0 classical):
 * <ul>
 *   <li>0..55: queen-like moves (8 directions × 7 distances)</li>
 *   <li>56..63: knight moves (8)</li>
 *   <li>64..72: underpromotions (N,B,R) × (forward, forward-left, forward-right)</li>
 * </ul>
 * </p>
 *
 * <p>Direction order (queen-like):
 * N, S, E, W, NE, NW, SE, SW.</p>
 *
 * <p>Knight order (clockwise starting NNE):
 * ( +1,+2 ), ( +2,+1 ), ( +2,-1 ), ( +1,-2 ),
 * ( -1,-2 ), ( -2,-1 ), ( -2,+1 ), ( -1,+2 ).</p>
 *
 * <p>Underpromotion order:
 * Knight, Bishop, Rook × (forward, forward-left, forward-right).</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class PolicyEncoder {

    /**
     * Total number of policy planes in the uncompressed LC0 encoding.
     */
    public static final int POLICY_PLANES = 73;

    /**
     * Total number of policy logits in the uncompressed LC0 encoding.
     */
    public static final int RAW_POLICY_SIZE = POLICY_PLANES * 64;

     /**
     * Shared promo knight constant.
     */
     private static final int PROMO_KNIGHT = 1;
     /**
     * Shared promo bishop constant.
     */
     private static final int PROMO_BISHOP = 2;
     /**
     * Shared promo rook constant.
     */
     private static final int PROMO_ROOK = 3;
     /**
     * Shared promo queen constant.
     */
     private static final int PROMO_QUEEN = 4;

     /**
     * Shared knight deltas constant.
     */
     private static final int[][] KNIGHT_DELTAS = {
            { 1, 2 }, { 2, 1 }, { 2, -1 }, { 1, -2 },
            { -1, -2 }, { -2, -1 }, { -2, 1 }, { -1, 2 }
    };

     /**
     * Shared underpromo dirs constant.
     */
     private static final int[][] UNDERPROMO_DIRS = {
            { 0, 1 },   // forward
            { -1, 1 },  // forward-left
            { 1, 1 }    // forward-right
    };

    /**
     * Utility class; prevent instantiation.
     */
    private PolicyEncoder() {
        // utility
    }

    /**
     * Returns the raw LC0 policy index for a move, or {@code -1} if the move
     * cannot be encoded in the 73-plane format.
     *
     * @param position position the move is played from (side-to-move matters)
     * @param move encoded move (UCI short)
     * @return raw policy index in {@code [0, RAW_POLICY_SIZE)}, or {@code -1}
     */
    public static int rawPolicyIndex(Position position, short move) {
        if (position == null) {
            return -1;
        }

        int fromIndex = Move.getFromIndex(move);
        int toIndex = Move.getToIndex(move);
        int promotion = Move.getPromotion(move);

        int fromFile = Field.getX((byte) fromIndex);
        int fromRank = Field.getY((byte) fromIndex);
        int toFile = Field.getX((byte) toIndex);
        int toRank = Field.getY((byte) toIndex);

        boolean weAreBlack = !position.isWhiteToMove();
        if (weAreBlack) {
            fromRank = 7 - fromRank;
            toRank = 7 - toRank;
        }

        int deltaFile = toFile - fromFile;
        int deltaRank = toRank - fromRank;

        int fromSquare = (fromRank << 3) | fromFile; // a1..h8

        if (promotion != 0 && promotion != PROMO_QUEEN) {
            int promoIndex = underpromoPieceIndex(promotion);
            int dirIndex = underpromoDirectionIndex(deltaFile, deltaRank);
            if (promoIndex < 0 || dirIndex < 0) {
                return -1;
            }
            int plane = 64 + promoIndex * 3 + dirIndex;
            return plane * 64 + fromSquare;
        }

        int knightIndex = knightIndex(deltaFile, deltaRank);
        if (knightIndex >= 0) {
            int plane = 56 + knightIndex;
            return plane * 64 + fromSquare;
        }

        int slidePlane = slidePlaneIndex(deltaFile, deltaRank);
        if (slidePlane >= 0) {
            return slidePlane * 64 + fromSquare;
        }

        return -1;
    }

     /**
     * Handles knight index.
     * @param deltaFile delta file
     * @param deltaRank delta rank
     * @return computed value
     */
     private static int knightIndex(int deltaFile, int deltaRank) {
        for (int index = 0; index < KNIGHT_DELTAS.length; index++) {
            int[] delta = KNIGHT_DELTAS[index];
            if (delta[0] == deltaFile && delta[1] == deltaRank) {
                return index;
            }
        }
        return -1;
    }

     /**
     * Handles slide plane index.
     * @param deltaFile delta file
     * @param deltaRank delta rank
     * @return computed value
     */
     private static int slidePlaneIndex(int deltaFile, int deltaRank) {
        if (deltaFile == 0 && deltaRank != 0) {
            return planeIndex(deltaRank > 0 ? 0 : 1, Math.abs(deltaRank));
        }
        if (deltaRank == 0 && deltaFile != 0) {
            return planeIndex(deltaFile > 0 ? 2 : 3, Math.abs(deltaFile));
        }
        if (Math.abs(deltaFile) == Math.abs(deltaRank) && deltaFile != 0) {
            int dir = diagonalDirectionIndex(deltaFile, deltaRank);
            return planeIndex(dir, Math.abs(deltaFile));
        }
        return -1;
    }

     /**
     * Handles diagonal direction index.
     * @param deltaFile delta file
     * @param deltaRank delta rank
     * @return computed value
     */
     private static int diagonalDirectionIndex(int deltaFile, int deltaRank) {
        if (deltaFile > 0 && deltaRank > 0) {
            return 4;
        }
        if (deltaFile < 0 && deltaRank > 0) {
            return 5;
        }
        if (deltaFile > 0 && deltaRank < 0) {
            return 6;
        }
        if (deltaFile < 0 && deltaRank < 0) {
            return 7;
        }
        return -1;
    }

     /**
     * Handles plane index.
     * @param directionIndex direction index
     * @param distance distance
     * @return computed value
     */
     private static int planeIndex(int directionIndex, int distance) {
        if (directionIndex < 0 || distance < 1 || distance > 7) {
            return -1;
        }
        return directionIndex * 7 + (distance - 1);
    }

     /**
     * Handles underpromo piece index.
     * @param promotion promotion
     * @return computed value
     */
     private static int underpromoPieceIndex(int promotion) {
        return switch (promotion) {
        case PROMO_KNIGHT -> 0;
        case PROMO_BISHOP -> 1;
        case PROMO_ROOK -> 2;
        default -> -1;
        };
    }

     /**
     * Handles underpromo direction index.
     * @param deltaFile delta file
     * @param deltaRank delta rank
     * @return computed value
     */
     private static int underpromoDirectionIndex(int deltaFile, int deltaRank) {
        for (int index = 0; index < UNDERPROMO_DIRS.length; index++) {
            int[] delta = UNDERPROMO_DIRS[index];
            if (delta[0] == deltaFile && delta[1] == deltaRank) {
                return index;
            }
        }
        return -1;
    }
}
