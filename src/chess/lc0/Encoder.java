package chess.lc0;

import chess.core.Field;
import chess.core.Piece;
import chess.core.Position;

/**
 * Encodes a {@link Position} into LCZero "classical" 112-plane input
 * ({@code INPUT_CLASSICAL_112_PLANE}).
 *
 * <p>The returned tensor is a {@code float[112 * 64]} in channel-major order:
 * the first 64 entries are plane 0, the next 64 entries plane 1, etc. Within a
 * plane, squares are laid out as {@code a1..h1, a2..h2, ..., a8..h8}.
 *
 * <p>All planes are from the side-to-move perspective:
 * when it is black's turn, the position is rank-mirrored so that "our" pieces
 * still appear closest to rank 1.
 *
 * <h2>Plane layout</h2>
 * <ul>
 * <li>History planes: 8 blocks of 13 planes (104 planes total)
 * <ul>
 * <li>0..5: our pieces {P,N,B,R,Q,K}</li>
 * <li>6..11: opponent pieces {P,N,B,R,Q,K}</li>
 * <li>12: repetition indicator (not available here; kept at zero)</li>
 * </ul>
 * </li>
 * <li>Aux planes (indices 104..111)
 * <ul>
 * <li>104..107: castling rights (we Q, we K, they Q, they K)</li>
 * <li>108: side-to-move is black (all ones if black to move)</li>
 * <li>109: halfmove clock (rule-50 counter) as a constant value</li>
 * <li>110: unused (all zeros)</li>
 * <li>111: edge plane (all ones)</li>
 * </ul>
 * </li>
 * </ul>
 *
 * <p>Limitation: {@link Position} does not provide full move history; the
 * current board is repeated for all eight history slots.
 *
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class Encoder {

    /**
     * Planes per time step in LC0 classical input (12 piece planes + 1 repetition plane).
     */
    private static final int PLANES_PER_BOARD = 13;

    /**
     * Number of historical positions in LC0 classical input.
     */
    private static final int HISTORY = 8;

    /**
     * First auxiliary plane index after history planes (castling, stm, rule50, edges).
     */
    private static final int AUX_BASE = PLANES_PER_BOARD * HISTORY; // 104

    /**
     * Total number of planes in LC0 classical input.
     */
    private static final int TOTAL_CHANNELS = 112;

    /**
     * Prevents instantiation of this utility class.
     */
    private Encoder() {}

    /**
     * Encodes a {@link Position} into LC0 112-plane input.
     *
     * @param position current position
     * @return encoded planes (length {@code 112 * 64})
     */
    public static float[] encode(Position position) {
        boolean weAreBlack = !position.isWhiteTurn();
        long[] pieceBitboards = collectPieceBitboards(position.getBoard());
        long[] perspective = toSideToMovePerspective(pieceBitboards, weAreBlack);

        float[] planes = new float[TOTAL_CHANNELS * 64];
        writeRepeatedHistory(planes, perspective);
        writeAuxPlanes(planes, position, weAreBlack);
        return planes;
    }

    /**
     * Collects per-piece bitboards from a {@link Position} board.
     *
     * <p>Output order: WP,WN,WB,WR,WQ,WK,BP,BN,BB,BR,BQ,BK.
     *
     * <p>Bit numbering uses {@code 0=a1..63=h8}.
     *
     * @param board board array from {@link Position}
     * @return per-piece bitboards in LC0 order (white pawn..black king)
     */
    private static long[] collectPieceBitboards(byte[] board) {
        long[] bits = new long[12];

        // Position uses 0=a8..63=h1; this encoder uses 0=a1..63=h8 for plane indexing.
        for (int i = 0; i < 64; i++) {
            byte piece = board[i];
            if (piece != Piece.EMPTY) {
                int abs = piece < 0 ? -piece : piece; // 1..6
                if (abs <= 6) {
                    int sq = positionIndexToPlaneSquare(i);
                    long mask = 1L << sq;
                    int typeIndex = abs - 1;             // 0..5 (pawn..king)
                    int colorOffset = piece < 0 ? 6 : 0; // black planes after white planes
                    bits[colorOffset + typeIndex] |= mask;
                }
            }
        }

        return bits;
    }

    /**
     * Converts {@link Position}'s board indexing (0=a8..63=h1) to this encoder's plane indexing (0=a1..63=h8).
     *
     * @param positionIndex square index in {@link Position}
     * @return square index in LC0 plane ordering
     */
    private static int positionIndexToPlaneSquare(int positionIndex) {
        int rankFromTop = positionIndex >>> 3;
        int file = positionIndex & 7;
        return ((7 - rankFromTop) << 3) | file;
    }

    /**
     * Produces bitboards from the side-to-move perspective (i.e., when black to move, mirror ranks and swap colors).
     *
     * <p>Input and output order: WP,WN,WB,WR,WQ,WK,BP,BN,BB,BR,BQ,BK where W/B are "us/them" after the transform.
     *
     * @param bits base bitboards (white pawns first)
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
     * Writes eight repeated history blocks (Position has no move history).
     *
     * @param planes output tensor
     * @param perspectiveBits per-piece bitboards to repeat
     */
    private static void writeRepeatedHistory(float[] planes, long[] perspectiveBits) {
        for (int h = 0; h < HISTORY; h++) {
            int base = h * PLANES_PER_BOARD;
            for (int p = 0; p < 12; p++) {
                addBits(planes, base + p, perspectiveBits[p]);
            }
            // Plane 12 (repetition) left as zeros.
        }
    }

    /**
     * Writes castling, stm, rule50 and edge planes.
     *
     * @param planes output tensor
     * @param position current position
     * @param weAreBlack {@code true} when encoding from black's perspective
     */
    private static void writeAuxPlanes(float[] planes, Position position, boolean weAreBlack) {
        boolean whiteCastleK = position.getWhiteKingside() != Field.NO_SQUARE;
        boolean whiteCastleQ = position.getWhiteQueenside() != Field.NO_SQUARE;
        boolean blackCastleK = position.getBlackKingside() != Field.NO_SQUARE;
        boolean blackCastleQ = position.getBlackQueenside() != Field.NO_SQUARE;

        boolean weCastleQ = weAreBlack ? blackCastleQ : whiteCastleQ;
        boolean weCastleK = weAreBlack ? blackCastleK : whiteCastleK;
        boolean theyCastleQ = weAreBlack ? whiteCastleQ : blackCastleQ;
        boolean theyCastleK = weAreBlack ? whiteCastleK : blackCastleK;

        if (weCastleQ) {
            fillOnes(planes, AUX_BASE + 0);
        }
        if (weCastleK) {
            fillOnes(planes, AUX_BASE + 1);
        }
        if (theyCastleQ) {
            fillOnes(planes, AUX_BASE + 2);
        }
        if (theyCastleK) {
            fillOnes(planes, AUX_BASE + 3);
        }

        if (weAreBlack) {
            fillOnes(planes, AUX_BASE + 4);
        }

        fillConstant(planes, AUX_BASE + 5, position.getHalfMove());

        // Plane 110: kept at zeros.
        fillOnes(planes, AUX_BASE + 7);
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
     * @param channel plane index (0..111)
     * @param bits    bitboard with {@code 0=a1..63=h8}
     */
    private static void addBits(float[] planes, int channel, long bits) {
        int base = channel * 64;
        while (bits != 0) {
            int sq = Long.numberOfTrailingZeros(bits);
            planes[base + sq] = 1f;
            bits &= bits - 1;
        }
    }

    /**
     * Fills an entire plane with ones.
     *
     * @param planes  output tensor
     * @param channel plane index (0..111)
     */
    private static void fillOnes(float[] planes, int channel) {
        fillConstant(planes, channel, 1f);
    }

    /**
     * Fills an entire plane with a constant value.
     *
     * @param planes  output tensor
     * @param channel plane index (0..111)
     * @param value   constant value to write to all 64 squares
     */
    private static void fillConstant(float[] planes, int channel, float value) {
        int base = channel * 64;
        for (int i = 0; i < 64; i++) {
            planes[base + i] = value;
        }
    }
}
