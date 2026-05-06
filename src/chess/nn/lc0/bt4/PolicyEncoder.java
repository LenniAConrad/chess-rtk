package chess.nn.lc0.bt4;

import java.util.Arrays;

import chess.core.Field;
import chess.core.Move;
import chess.core.Position;

/**
 * Maps moves to and from LC0's BT4 attention-policy layout.
 *
 * <p>
 * The attention policy first produces an internal {@code 67 * 64} tensor:
 * {@code 64 * 64} from-square/to-square logits plus three underpromotion
 * planes. LC0 then gathers the geometrically valid entries into 1858 compressed
 * policy logits.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class PolicyEncoder {

    /**
     * Number of from-to attention logits.
     */
    public static final int FROM_TO_POLICY_SIZE = 64 * 64;

    /**
     * Internal attention-policy planes.
     */
    public static final int INTERNAL_POLICY_PLANES = 67;

    /**
     * Internal attention-policy size.
     */
    public static final int INTERNAL_POLICY_SIZE = INTERNAL_POLICY_PLANES * 64;

    /**
     * Compressed LC0 attention-policy size.
     */
    public static final int POLICY_SIZE = Architecture.ATTENTION_POLICY_SIZE;

    /**
     * Promotion code for knight.
     */
    private static final int PROMO_KNIGHT = 1;

    /**
     * Promotion code for bishop.
     */
    private static final int PROMO_BISHOP = 2;

    /**
     * Promotion code for rook.
     */
    private static final int PROMO_ROOK = 3;

    /**
     * Promotion code for queen.
     */
    private static final int PROMO_QUEEN = 4;

    /**
     * Knight move deltas in file/rank coordinates.
     */
    private static final int[][] KNIGHT_DELTAS = {
            { 1, 2 }, { 2, 1 }, { 2, -1 }, { 1, -2 },
            { -1, -2 }, { -2, -1 }, { -2, 1 }, { -1, 2 }
    };

    /**
     * Internal index to compressed policy index map.
     */
    private static final int[] COMPRESSED_BY_INTERNAL = buildCompressedByInternal();

    /**
     * Prevents instantiation.
     */
    private PolicyEncoder() {
        // utility
    }

    /**
     * Returns the internal {@code 67 * 64} policy index for a move.
     *
     * @param position source position
     * @param move encoded move
     * @return internal index, or {@code -1} when not encodable
     */
    public static int internalPolicyIndex(Position position, short move) {
        return internalPolicyIndex(position, move, 0);
    }

    /**
     * Returns the internal {@code 67 * 64} policy index for a move after applying
     * a canonical transform.
     *
     * @param position source position
     * @param move encoded move
     * @param transform LC0 canonical transform bit mask
     * @return internal index, or {@code -1} when not encodable
     */
    public static int internalPolicyIndex(Position position, short move, int transform) {
        if (position == null || move == Move.NO_MOVE) {
            return -1;
        }
        int from = perspectiveSquare(position, Move.getFromIndex(move));
        int to = perspectiveSquare(position, Move.getToIndex(move));
        if (transform != 0) {
            from = transformSquare(from, transform);
            to = transformSquare(to, transform);
        }

        int promotion = Move.getPromotion(move);
        if (promotion != 0 && promotion != PROMO_QUEEN) {
            int promoIndex = underpromotionPieceIndex(promotion);
            if (promoIndex < 0) {
                return -1;
            }
            int fromFile = from & 7;
            int fromRank = from >>> 3;
            int toFile = to & 7;
            int toRank = to >>> 3;
            if (fromRank != 6 || toRank != 7 || Math.abs(toFile - fromFile) > 1) {
                return -1;
            }
            return FROM_TO_POLICY_SIZE + fromFile * 24 + toFile * 3 + promoIndex;
        }

        if (from == to) {
            return -1;
        }
        if (!isQueenLikeOrKnight(from, to)) {
            return -1;
        }
        return from * 64 + to;
    }

    /**
     * Returns the compressed LC0 policy index for a move.
     *
     * @param position source position
     * @param move encoded move
     * @return compressed index in {@code [0, 1858)}, or {@code -1}
     */
    public static int compressedPolicyIndex(Position position, short move) {
        return compressedPolicyIndex(position, move, 0);
    }

    /**
     * Returns the compressed LC0 policy index for a transformed move.
     *
     * @param position source position
     * @param move encoded move
     * @param transform LC0 canonical transform bit mask
     * @return compressed index in {@code [0, 1858)}, or {@code -1}
     */
    public static int compressedPolicyIndex(Position position, short move, int transform) {
        int internal = internalPolicyIndex(position, move, transform);
        if (internal < 0 || internal >= COMPRESSED_BY_INTERNAL.length) {
            return -1;
        }
        return COMPRESSED_BY_INTERNAL[internal];
    }

    /**
     * Gathers internal attention-policy logits into LC0's 1858-logit vector.
     *
     * @param internalLogits internal {@code 67 * 64} logits
     * @return compressed logits
     */
    public static float[] mapInternalPolicy(float[] internalLogits) {
        if (internalLogits == null || internalLogits.length != INTERNAL_POLICY_SIZE) {
            throw new IllegalArgumentException("internalLogits length must be " + INTERNAL_POLICY_SIZE);
        }
        float[] out = new float[POLICY_SIZE];
        for (int i = 0; i < COMPRESSED_BY_INTERNAL.length; i++) {
            int mapped = COMPRESSED_BY_INTERNAL[i];
            if (mapped >= 0) {
                out[mapped] = internalLogits[i];
            }
        }
        return out;
    }

    /**
     * Returns a copy of the internal-to-compressed map.
     *
     * @return copy of the map
     */
    public static int[] compressedByInternalMap() {
        return Arrays.copyOf(COMPRESSED_BY_INTERNAL, COMPRESSED_BY_INTERNAL.length);
    }

    /**
     * Builds LC0's attention-policy gather map from geometry.
     *
     * @return map from internal index to compressed index
     */
    private static int[] buildCompressedByInternal() {
        int[] map = new int[INTERNAL_POLICY_SIZE];
        Arrays.fill(map, -1);
        int next = 0;
        for (int from = 0; from < 64; from++) {
            for (int to = 0; to < 64; to++) {
                if (from != to && isQueenLikeOrKnight(from, to)) {
                    map[from * 64 + to] = next++;
                }
            }
        }
        for (int fromFile = 0; fromFile < 8; fromFile++) {
            int minTo = Math.max(0, fromFile - 1);
            int maxTo = Math.min(7, fromFile + 1);
            for (int toFile = minTo; toFile <= maxTo; toFile++) {
                for (int promo = 0; promo < 3; promo++) {
                    map[FROM_TO_POLICY_SIZE + fromFile * 24 + toFile * 3 + promo] = next++;
                }
            }
        }
        if (next != POLICY_SIZE) {
            throw new IllegalStateException("BT4 policy map generated " + next + " entries");
        }
        return map;
    }

    /**
     * Returns whether a from-to pair is representable by the attention policy.
     *
     * @param from from square in token coordinates
     * @param to to square in token coordinates
     * @return true if representable
     */
    private static boolean isQueenLikeOrKnight(int from, int to) {
        int df = (to & 7) - (from & 7);
        int dr = (to >>> 3) - (from >>> 3);
        if (df == 0 || dr == 0 || Math.abs(df) == Math.abs(dr)) {
            return true;
        }
        for (int[] delta : KNIGHT_DELTAS) {
            if (delta[0] == df && delta[1] == dr) {
                return true;
            }
        }
        return false;
    }

    /**
     * Converts a core square to side-to-move token coordinates.
     *
     * @param position source position
     * @param coreSquare core square index
     * @return token square index
     */
    private static int perspectiveSquare(Position position, int coreSquare) {
        byte square = (byte) coreSquare;
        int file = Field.getX(square);
        int rank = Field.getY(square);
        if (!position.isWhiteToMove()) {
            rank = 7 - rank;
        }
        return (rank << 3) | file;
    }

    /**
     * Applies an LC0 canonical transform to a token square.
     *
     * @param square token square
     * @param transform transform bit mask
     * @return transformed token square
     */
    private static int transformSquare(int square, int transform) {
        int file = square & 7;
        int rank = square >>> 3;
        if ((transform & (Encoder.MIRROR_TRANSFORM | Encoder.TRANSPOSE_TRANSFORM)) != 0) {
            rank = 7 - rank;
        }
        if ((transform & (Encoder.FLIP_TRANSFORM | Encoder.TRANSPOSE_TRANSFORM)) != 0) {
            file = 7 - file;
        }
        return (rank << 3) | file;
    }

    /**
     * Maps promotion code to BT4 underpromotion plane index.
     *
     * @param promotion move promotion code
     * @return index, or {@code -1}
     */
    private static int underpromotionPieceIndex(int promotion) {
        return switch (promotion) {
            case PROMO_KNIGHT -> 0;
            case PROMO_BISHOP -> 1;
            case PROMO_ROOK -> 2;
            default -> -1;
        };
    }
}
