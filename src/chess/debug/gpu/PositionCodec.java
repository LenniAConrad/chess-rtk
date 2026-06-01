package chess.debug.gpu;

import chess.core.Position;

/**
 * Packs positions into the flat little-endian {@code long[]} layout consumed by
 * the native perft kernels.
 *
 * <p>
 * Each position occupies {@link #WORDS} longs and MUST match the C++ decoder in
 * {@code native/common/perft_core.h} ({@code unpack_position}):
 * </p>
 * <pre>
 *   words[0..11] = piece bitboards (WHITE_PAWN .. BLACK_KING)
 *   words[12]    = meta:
 *      bit 0      side to move (1 = White)
 *      bit 1      Chess960 castling encoding
 *      bits 2..5  castling rights
 *      bits 8..15 en-passant square          (0xFF = none)
 *      bits 16..23 white kingside rook square (0xFF = none)
 *      bits 24..31 white queenside rook square
 *      bits 32..39 black kingside rook square
 *      bits 40..47 black queenside rook square
 * </pre>
 *
 * <p>
 * Occupancy masks, the square-to-piece table, and king squares are derived on
 * the native side, so they are not stored. The halfmove/fullmove clocks are
 * irrelevant to perft and are omitted.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class PositionCodec {

    /**
     * Number of {@code long} words used to encode one position.
     */
    public static final int WORDS = 13;

    /**
     * Sentinel byte for an absent square ({@code Field.NO_SQUARE}).
     */
    private static final long NO_SQUARE_BYTE = 0xFFL;

    /**
     * Utility class; prevents instantiation.
     */
    private PositionCodec() {
    }

    /**
     * Packs a frontier of positions into one flat array.
     *
     * @param frontier positions to encode
     * @return {@code frontier.length * WORDS} packed longs
     */
    public static long[] pack(Position[] frontier) {
        long[] out = new long[frontier.length * WORDS];
        for (int i = 0; i < frontier.length; i++) {
            packInto(frontier[i], out, i * WORDS);
        }
        return out;
    }

    /**
     * Packs one position into {@code out} starting at {@code base}.
     *
     * @param position position to encode
     * @param out target array
     * @param base start offset in {@code out}
     */
    public static void packInto(Position position, long[] out, int base) {
        for (int piece = 0; piece < 12; piece++) {
            out[base + piece] = position.pieces(piece);
        }
        long meta = 0L;
        if (position.isWhiteToMove()) {
            meta |= 1L;
        }
        if (position.isChess960()) {
            meta |= 2L;
        }
        meta |= (long) (position.castlingRights() & 0xF) << 2;
        meta |= packSquare(position.enPassantSquare()) << 8;
        meta |= packSquare(position.castlingRookSquare(Position.WHITE_KINGSIDE)) << 16;
        meta |= packSquare(position.castlingRookSquare(Position.WHITE_QUEENSIDE)) << 24;
        meta |= packSquare(position.castlingRookSquare(Position.BLACK_KINGSIDE)) << 32;
        meta |= packSquare(position.castlingRookSquare(Position.BLACK_QUEENSIDE)) << 40;
        out[base + 12] = meta;
    }

    /**
     * Encodes a square index as an unsigned byte, using {@code 0xFF} for none.
     *
     * @param square square index, or a negative value for none
     * @return packed byte value
     */
    private static long packSquare(int square) {
        return square < 0 ? NO_SQUARE_BYTE : (square & 0xFFL);
    }
}
