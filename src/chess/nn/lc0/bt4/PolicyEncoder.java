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
     * One scored legal move in the network's policy distribution.
     *
     * @param move encoded crtk move
     * @param policyIndex compressed LC0 policy index
     * @param logit raw logit value
     * @param probability softmax probability over the LEGAL moves
     */
    public record ScoredMove(short move, int policyIndex, float logit, float probability) {
    }

    /**
     * Decodes the top-K most likely legal moves in {@code position} given the
     * compressed 1858-logit policy distribution. The softmax is computed over
     * the LEGAL moves only so probabilities sum to 1.
     *
     * @param position position to enumerate legal moves from
     * @param compressedLogits raw policy logits (size {@link #POLICY_SIZE})
     * @param transform LC0 canonical transform bits used during inference
     * @param k how many top moves to return
     * @return at most k {@link ScoredMove}s, ranked by logit descending
     */
    public static java.util.List<ScoredMove> topLegalMoves(Position position,
            float[] compressedLogits, int transform, int k) {
        if (position == null || compressedLogits == null || k <= 0) {
            return java.util.Collections.emptyList();
        }
        chess.core.MoveList legal = position.legalMoves();
        int count = legal.size();
        if (count == 0) {
            return java.util.Collections.emptyList();
        }
        short[] moves = new short[count];
        int[] indices = new int[count];
        float[] logits = new float[count];
        int valid = 0;
        for (int i = 0; i < count; i++) {
            short m = legal.get(i);
            int idx = compressedPolicyIndex(position, m, transform);
            if (idx < 0 || idx >= compressedLogits.length) {
                continue;
            }
            moves[valid] = m;
            indices[valid] = idx;
            logits[valid] = compressedLogits[idx];
            valid++;
        }
        if (valid == 0) {
            return java.util.Collections.emptyList();
        }
        // Softmax over legal moves only.
        float max = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < valid; i++) {
            if (logits[i] > max) {
                max = logits[i];
            }
        }
        double sum = 0.0;
        float[] probs = new float[valid];
        for (int i = 0; i < valid; i++) {
            probs[i] = (float) Math.exp(logits[i] - max);
            sum += probs[i];
        }
        float inv = (float) (1.0 / Math.max(sum, 1e-9));
        for (int i = 0; i < valid; i++) {
            probs[i] *= inv;
        }
        Integer[] order = new Integer[valid];
        for (int i = 0; i < valid; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Float.compare(logits[b], logits[a]));
        int take = Math.min(k, valid);
        java.util.List<ScoredMove> out = new java.util.ArrayList<>(take);
        for (int i = 0; i < take; i++) {
            int j = order[i];
            out.add(new ScoredMove(moves[j], indices[j], logits[j], probs[j]));
        }
        return out;
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
        // Derive the square permutation directly from the input encoder's board
        // transform so the policy lookup can never drift from how the board was
        // canonicalized. The previous hand-written version omitted the diagonal
        // coordinate swap for TRANSPOSE_TRANSFORM (it applied a 180-degree rotation
        // instead), scrambling priors in the pawnless/canonical positions where
        // LC0 chooses the transpose.
        return Long.numberOfTrailingZeros(Encoder.transformBits(1L << square, transform));
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
