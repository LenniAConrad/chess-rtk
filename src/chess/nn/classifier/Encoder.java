package chess.nn.classifier;

import chess.core.Field;
import chess.core.Position;

/**
 * Encodes a {@link Position} into the classifier 21-plane input tensor.
 *
 * <p>The returned tensor is a {@code float[21 * 64]} in channel-major order:
 * the first 64 entries are plane 0, the next 64 entries plane 1, etc. Within a
 * plane, squares are laid out as {@code a1..h1, a2..h2, ..., a8..h8}.
 *
 * <p>All planes are from the side-to-move perspective:
 * when it is black's turn, the position is rank-mirrored so that "our" pieces
 * still appear closest to rank 1.
 */
public final class Encoder {

    /**
     * Total number of planes in the classifier input.
     */
    public static final int TOTAL_CHANNELS = 21;

    /**
     * Plane index of the side-to-move-is-black auxiliary plane.
     */
    public static final int BLACK_TO_MOVE_PLANE = 17;

    /**
     * Prevents instantiation of this utility class.
     */
    private Encoder() {
    }

    /**
     * Encodes a {@link Position} into classifier planes.
     *
     * @param position current position
     * @return encoded planes (length {@code 21 * 64})
     */
    public static float[] encode(Position position) {
        boolean weAreBlack = !position.isWhiteToMove();
        long[] pieceBitboards = collectPieceBitboards(position);
        long[] perspective = toSideToMovePerspective(pieceBitboards, weAreBlack);

        float[] planes = new float[TOTAL_CHANNELS * 64];
        writeBoardPlanes(planes, perspective);
        writeAuxPlanes(planes, position, weAreBlack);
        return planes;
    }

    /**
     * Collects per-piece bitboards from a {@link Position}.
     *
     * <p>Output order: WP,WN,WB,WR,WQ,WK,BP,BN,BB,BR,BQ,BK.
     *
     * @param position source position
     * @return per-piece bitboards in white-first order
     */
    private static long[] collectPieceBitboards(Position position) {
        long[] bits = new long[12];
        for (int i = 0; i < bits.length; i++) {
            bits[i] = positionToPlaneBits(position.pieces(i));
        }
        return bits;
    }

    /**
     * Converts core bitboard indexing ({@code 0=a8..63=h1}) to plane indexing
     * ({@code 0=a1..63=h8}).
     *
     * @param bits core-indexed bitboard
     * @return plane-indexed bitboard
     */
    private static long positionToPlaneBits(long bits) {
        return mirrorRanks(bits);
    }

    /**
     * Produces bitboards from the side-to-move perspective.
     *
     * @param bits       base bitboards (white pieces first)
     * @param weAreBlack {@code true} when black is the side to move
     * @return perspective-adjusted bitboards from the side-to-move perspective
     */
    private static long[] toSideToMovePerspective(long[] bits, boolean weAreBlack) {
        if (!weAreBlack) {
            long[] out = new long[12];
            System.arraycopy(bits, 0, out, 0, 12);
            return out;
        }

        long[] out = new long[12];
        for (int i = 0; i < 6; i++) {
            out[i] = mirrorRanks(bits[6 + i]);
            out[6 + i] = mirrorRanks(bits[i]);
        }
        return out;
    }

    /**
     * Writes the current-position board planes.
     *
     * @param planes           output tensor
     * @param perspectiveBits  per-piece bitboards from side-to-move perspective
     */
    private static void writeBoardPlanes(float[] planes, long[] perspectiveBits) {
        for (int plane = 0; plane < 12; plane++) {
            addBits(planes, plane, perspectiveBits[plane]);
        }
        // Plane 12 (repetition) is left at zero because Position does not expose
        // repetition history.
    }

    /**
     * Writes castling, stm, rule-50, zero and edge planes.
     *
     * @param planes     output tensor
     * @param position   current position
     * @param weAreBlack {@code true} when encoding from black's perspective
     */
    private static void writeAuxPlanes(float[] planes, Position position, boolean weAreBlack) {
        boolean whiteCastleK = position.activeCastlingMoveTarget(Position.WHITE_KINGSIDE) != Field.NO_SQUARE;
        boolean whiteCastleQ = position.activeCastlingMoveTarget(Position.WHITE_QUEENSIDE) != Field.NO_SQUARE;
        boolean blackCastleK = position.activeCastlingMoveTarget(Position.BLACK_KINGSIDE) != Field.NO_SQUARE;
        boolean blackCastleQ = position.activeCastlingMoveTarget(Position.BLACK_QUEENSIDE) != Field.NO_SQUARE;

        boolean weCastleQ = weAreBlack ? blackCastleQ : whiteCastleQ;
        boolean weCastleK = weAreBlack ? blackCastleK : whiteCastleK;
        boolean theyCastleQ = weAreBlack ? whiteCastleQ : blackCastleQ;
        boolean theyCastleK = weAreBlack ? whiteCastleK : blackCastleK;

        if (weCastleQ) {
            fillOnes(planes, 13);
        }
        if (weCastleK) {
            fillOnes(planes, 14);
        }
        if (theyCastleQ) {
            fillOnes(planes, 15);
        }
        if (theyCastleK) {
            fillOnes(planes, 16);
        }
        if (weAreBlack) {
            fillOnes(planes, BLACK_TO_MOVE_PLANE);
        }

        fillConstant(planes, 18, position.halfMoveClock());
        fillOnes(planes, 20);
    }

    /**
     * Mirrors a bitboard vertically (rank 1 ↔ rank 8).
     *
     * @param bits bitboard with {@code 0=a1..63=h8}
     * @return mirrored bitboard
     */
    private static long mirrorRanks(long bits) {
        return Long.reverseBytes(bits);
    }

    /**
     * Writes a bitboard's set bits into a single plane.
     *
     * @param planes  output tensor
     * @param channel plane index
     * @param bits    bitboard with {@code 0=a1..63=h8}
     */
    private static void addBits(float[] planes, int channel, long bits) {
        int base = channel * 64;
        while (bits != 0) {
            int square = Long.numberOfTrailingZeros(bits);
            planes[base + square] = 1f;
            bits &= bits - 1;
        }
    }

    /**
     * Fills an entire plane with ones.
     *
     * @param planes  output tensor
     * @param channel plane index
     */
    private static void fillOnes(float[] planes, int channel) {
        fillConstant(planes, channel, 1f);
    }

    /**
     * Fills an entire plane with a constant value.
     *
     * @param planes  output tensor
     * @param channel plane index
     * @param value   constant value to write to all 64 squares
     */
    private static void fillConstant(float[] planes, int channel, float value) {
        int base = channel * 64;
        for (int i = 0; i < 64; i++) {
            planes[base + i] = value;
        }
    }
}
