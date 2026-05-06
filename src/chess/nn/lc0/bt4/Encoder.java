package chess.nn.lc0.bt4;

import java.util.List;

import chess.core.Field;
import chess.core.Position;

/**
 * Encodes {@link Position} objects for LCZero BT4 attention-body networks.
 *
 * <p>
 * The base representation is LC0's 112 planes in channel-major layout
 * {@code [112][64]}. Attention-body networks then transpose that into
 * token-major layout {@code [64][112]}, one token per board square.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Encoder {

    /**
     * Planes per historical board.
     */
    public static final int PLANES_PER_BOARD = 13;

    /**
     * Historical boards encoded by LC0.
     */
    public static final int HISTORY = 8;

    /**
     * First auxiliary plane index.
     */
    public static final int AUX_BASE = PLANES_PER_BOARD * HISTORY;

    /**
     * Total LC0 input planes.
     */
    public static final int INPUT_CHANNELS = Architecture.INPUT_CHANNELS;

    /**
     * Board-square token count.
     */
    public static final int TOKENS = Architecture.BOARD_TOKENS;

    /**
     * Horizontal mirror transform bit used by LC0 canonicalization.
     */
    public static final int FLIP_TRANSFORM = 1;

    /**
     * Vertical mirror transform bit used by LC0 canonicalization.
     */
    public static final int MIRROR_TRANSFORM = 2;

    /**
     * Diagonal transpose transform bit used by LC0 canonicalization.
     */
    public static final int TRANSPOSE_TRANSFORM = 4;

    /**
     * Prevents instantiation.
     */
    private Encoder() {
        // utility
    }

    /**
     * Encoded input plus the LC0 canonical transform applied to the board.
     *
     * @param planes channel-major {@code [112][64]} input planes
     * @param transform canonical transform bit mask
     */
    public record EncodedInput(float[] planes, int transform) {

        /**
         * Defensive construction.
         */
        public EncodedInput {
            if (planes == null) {
                throw new IllegalArgumentException("planes == null");
            }
            if (planes.length != INPUT_CHANNELS * TOKENS) {
                throw new IllegalArgumentException("planes length must be " + (INPUT_CHANNELS * TOKENS));
            }
        }

        /**
         * Returns token-major features without positional one-hot channels.
         *
         * @return {@code [64][112]} token-major features
         */
        public float[] tokenMajor() {
            return toTokenMajor(planes, INPUT_CHANNELS, TOKENS);
        }
    }

    /**
     * Encodes one position using the default BT4 input format.
     *
     * @param position source position
     * @return encoded input
     */
    public static EncodedInput encode(Position position) {
        return encode(position, InputFormat.BT4_CANONICAL_112);
    }

    /**
     * Encodes one position using a specific input format.
     *
     * @param position source position
     * @param format input format
     * @return encoded input
     */
    public static EncodedInput encode(Position position, InputFormat format) {
        return encodeHistory(List.of(position), format);
    }

    /**
     * Encodes a position history whose last item is the current position.
     *
     * <p>
     * When fewer than eight positions are supplied, the oldest available position
     * is reused for the missing history slots.
     * </p>
     *
     * @param historyOldestToNewest non-empty position history
     * @param format input format
     * @return encoded input
     */
    public static EncodedInput encodeHistory(List<Position> historyOldestToNewest, InputFormat format) {
        if (historyOldestToNewest == null || historyOldestToNewest.isEmpty()) {
            throw new IllegalArgumentException("history must contain at least one position");
        }
        if (format == null) {
            throw new IllegalArgumentException("format == null");
        }
        Position current = historyOldestToNewest.get(historyOldestToNewest.size() - 1);
        if (current == null) {
            throw new IllegalArgumentException("current position == null");
        }

        boolean weAreBlack = !current.isWhiteToMove();
        long[] currentPerspective = toSidePerspective(collectPlaneBits(current), weAreBlack);
        int transform = format.canonical() ? chooseCanonicalTransform(current, currentPerspective) : 0;

        float[] planes = new float[INPUT_CHANNELS * TOKENS];
        for (int slot = 0; slot < HISTORY; slot++) {
            int historyIndex = Math.max(0, historyOldestToNewest.size() - 1 - slot);
            Position historical = historyOldestToNewest.get(historyIndex);
            if (historical == null) {
                throw new IllegalArgumentException("history contains null position");
            }
            long[] perspective = toSidePerspective(collectPlaneBits(historical), weAreBlack);
            writeHistorySlot(planes, slot, perspective, transform);
        }
        writeAuxPlanes(planes, current, format, weAreBlack, transform);
        return new EncodedInput(planes, transform);
    }

    /**
     * Converts channel-major planes to token-major features.
     *
     * @param planes channel-major data
     * @param channels channel count
     * @param tokens token count
     * @return token-major copy
     */
    public static float[] toTokenMajor(float[] planes, int channels, int tokens) {
        if (planes == null) {
            throw new IllegalArgumentException("planes == null");
        }
        if (planes.length != channels * tokens) {
            throw new IllegalArgumentException("planes length mismatch");
        }
        float[] out = new float[planes.length];
        for (int token = 0; token < tokens; token++) {
            int outBase = token * channels;
            for (int channel = 0; channel < channels; channel++) {
                out[outBase + channel] = planes[channel * tokens + token];
            }
        }
        return out;
    }

    /**
     * Appends one-hot square position channels to token-major LC0 features.
     *
     * @param tokenMajor token-major {@code [64][112]} features
     * @return token-major {@code [64][176]} features
     */
    public static float[] appendPositionMap(float[] tokenMajor) {
        if (tokenMajor == null || tokenMajor.length != TOKENS * INPUT_CHANNELS) {
            throw new IllegalArgumentException("tokenMajor length must be " + (TOKENS * INPUT_CHANNELS));
        }
        int width = INPUT_CHANNELS + TOKENS;
        float[] out = new float[TOKENS * width];
        for (int token = 0; token < TOKENS; token++) {
            System.arraycopy(tokenMajor, token * INPUT_CHANNELS, out, token * width, INPUT_CHANNELS);
            out[token * width + INPUT_CHANNELS + token] = 1.0f;
        }
        return out;
    }

    /**
     * Collects current board bitboards converted to plane indexing
     * {@code 0=a1..63=h8}.
     *
     * @param position source position
     * @return per-piece plane-indexed bitboards
     */
    private static long[] collectPlaneBits(Position position) {
        long[] bits = new long[12];
        for (int i = 0; i < bits.length; i++) {
            bits[i] = mirrorRanks(position.pieces(i));
        }
        return bits;
    }

    /**
     * Converts white/black piece bitboards to side-to-move perspective.
     *
     * @param bits white/black piece bitboards
     * @param weAreBlack true when Black is the current side to move
     * @return own/opponent piece bitboards
     */
    private static long[] toSidePerspective(long[] bits, boolean weAreBlack) {
        long[] out = new long[12];
        if (!weAreBlack) {
            System.arraycopy(bits, 0, out, 0, bits.length);
            return out;
        }
        for (int i = 0; i < 6; i++) {
            out[i] = mirrorRanks(bits[6 + i]);
            out[6 + i] = mirrorRanks(bits[i]);
        }
        return out;
    }

    /**
     * Writes one history slot.
     *
     * @param planes output planes
     * @param slot history slot
     * @param perspectiveBits side-to-move perspective piece bitboards
     * @param transform canonical transform bit mask
     */
    private static void writeHistorySlot(float[] planes, int slot, long[] perspectiveBits, int transform) {
        int base = slot * PLANES_PER_BOARD;
        for (int piece = 0; piece < 12; piece++) {
            addBits(planes, base + piece, transformBits(perspectiveBits[piece], transform));
        }
    }

    /**
     * Writes auxiliary planes.
     *
     * @param planes output planes
     * @param position current position
     * @param format input format
     * @param weAreBlack true when Black is side to move
     * @param transform canonical transform bit mask
     */
    private static void writeAuxPlanes(
            float[] planes,
            Position position,
            InputFormat format,
            boolean weAreBlack,
            int transform) {
        if (format.castlingPlane()) {
            writeCastlingRookPlane(planes, AUX_BASE, position, weAreBlack, false, transform);
            writeCastlingRookPlane(planes, AUX_BASE + 1, position, weAreBlack, true, transform);
        } else {
            writeClassicalCastlingPlanes(planes, position, weAreBlack);
        }

        if (format.enPassantPlane()) {
            long enPassant = squareBit(position.enPassantSquare(), weAreBlack);
            addBits(planes, AUX_BASE + 4, transformBits(enPassant, transform));
        } else if (weAreBlack) {
            fillConstant(planes, AUX_BASE + 4, 1.0f);
        }

        float halfmove = format.hectoplies() ? position.halfMoveClock() / 100.0f : position.halfMoveClock();
        fillConstant(planes, AUX_BASE + 5, halfmove);
        fillConstant(planes, AUX_BASE + 7, 1.0f);
    }

    /**
     * Writes legacy constant castling planes.
     *
     * @param planes output planes
     * @param position current position
     * @param weAreBlack true when Black is side to move
     */
    private static void writeClassicalCastlingPlanes(float[] planes, Position position, boolean weAreBlack) {
        boolean whiteCastleK = position.activeCastlingMoveTarget(Position.WHITE_KINGSIDE) != Field.NO_SQUARE;
        boolean whiteCastleQ = position.activeCastlingMoveTarget(Position.WHITE_QUEENSIDE) != Field.NO_SQUARE;
        boolean blackCastleK = position.activeCastlingMoveTarget(Position.BLACK_KINGSIDE) != Field.NO_SQUARE;
        boolean blackCastleQ = position.activeCastlingMoveTarget(Position.BLACK_QUEENSIDE) != Field.NO_SQUARE;

        boolean weCastleQ = weAreBlack ? blackCastleQ : whiteCastleQ;
        boolean weCastleK = weAreBlack ? blackCastleK : whiteCastleK;
        boolean theyCastleQ = weAreBlack ? whiteCastleQ : blackCastleQ;
        boolean theyCastleK = weAreBlack ? whiteCastleK : blackCastleK;

        if (weCastleQ) {
            fillConstant(planes, AUX_BASE, 1.0f);
        }
        if (weCastleK) {
            fillConstant(planes, AUX_BASE + 1, 1.0f);
        }
        if (theyCastleQ) {
            fillConstant(planes, AUX_BASE + 2, 1.0f);
        }
        if (theyCastleK) {
            fillConstant(planes, AUX_BASE + 3, 1.0f);
        }
    }

    /**
     * Writes one modern castling rook-location plane.
     *
     * @param planes output planes
     * @param channel output channel
     * @param position current position
     * @param weAreBlack true when Black is side to move
     * @param kingside true for kingside, false for queenside
     * @param transform canonical transform bit mask
     */
    private static void writeCastlingRookPlane(
            float[] planes,
            int channel,
            Position position,
            boolean weAreBlack,
            boolean kingside,
            int transform) {
        int ourRight = weAreBlack
                ? (kingside ? Position.BLACK_KINGSIDE : Position.BLACK_QUEENSIDE)
                : (kingside ? Position.WHITE_KINGSIDE : Position.WHITE_QUEENSIDE);
        int theirRight = weAreBlack
                ? (kingside ? Position.WHITE_KINGSIDE : Position.WHITE_QUEENSIDE)
                : (kingside ? Position.BLACK_KINGSIDE : Position.BLACK_QUEENSIDE);

        long bits = 0L;
        if (position.canCastle(ourRight)) {
            bits |= squareBit(position.castlingRookSquare(ourRight), weAreBlack);
        }
        if (position.canCastle(theirRight)) {
            bits |= squareBit(position.castlingRookSquare(theirRight), weAreBlack);
        }
        addBits(planes, channel, transformBits(bits, transform));
    }

    /**
     * Chooses LC0's canonical spatial transform for the current board.
     *
     * @param position current position
     * @param perspective side-to-move perspective bitboards
     * @return transform bit mask
     */
    private static int chooseCanonicalTransform(Position position, long[] perspective) {
        if (position.castlingRights() != 0) {
            return 0;
        }
        long ourKing = perspective[5];
        int transform = 0;
        if ((ourKing & 0x0F0F0F0F0F0F0F0FL) != 0L) {
            transform |= FLIP_TRANSFORM;
            ourKing = flipFiles(ourKing);
        }
        long pawns = perspective[0] | perspective[6];
        if (pawns != 0L) {
            return transform;
        }
        if ((ourKing & 0xFFFFFFFF00000000L) != 0L) {
            transform |= MIRROR_TRANSFORM;
            ourKing = mirrorRanks(ourKing);
        }
        if ((ourKing & 0xE0C08000L) != 0L) {
            return transform | TRANSPOSE_TRANSFORM;
        }
        if ((ourKing & 0x10204080L) == 0L) {
            return transform;
        }
        return compareTransposing(perspective, transform) > 0 ? transform | TRANSPOSE_TRANSFORM : transform;
    }

    /**
     * Tie-breaks canonical transpose choices.
     *
     * @param perspective side-to-move perspective bitboards
     * @param transform current transform
     * @return positive if transpose should be added
     */
    private static int compareTransposing(long[] perspective, int transform) {
        long[] tests = new long[] {
                union(perspective),
                ownUnion(perspective),
                perspective[5] | perspective[11],
                perspective[4] | perspective[10],
                perspective[3] | perspective[9],
                perspective[1] | perspective[7],
                perspective[2] | perspective[8]
        };
        for (long test : tests) {
            long value = transformBits(test, transform);
            long alternative = transpose(value);
            int cmp = Long.compareUnsigned(value, alternative);
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    /**
     * Returns a union of all piece bitboards.
     *
     * @param bits bitboards
     * @return union
     */
    private static long union(long[] bits) {
        long out = 0L;
        for (long bit : bits) {
            out |= bit;
        }
        return out;
    }

    /**
     * Returns a union of own piece bitboards.
     *
     * @param bits bitboards
     * @return union
     */
    private static long ownUnion(long[] bits) {
        long out = 0L;
        for (int i = 0; i < 6; i++) {
            out |= bits[i];
        }
        return out;
    }

    /**
     * Converts a core square to a perspective plane bit.
     *
     * @param square core square
     * @param weAreBlack true when Black is side to move
     * @return single-bit mask, or zero for no square
     */
    private static long squareBit(byte square, boolean weAreBlack) {
        if (square == Field.NO_SQUARE) {
            return 0L;
        }
        int file = Field.getX(square);
        int rank = Field.getY(square);
        if (weAreBlack) {
            rank = 7 - rank;
        }
        return 1L << ((rank << 3) | file);
    }

    /**
     * Writes set bits into a channel.
     *
     * @param planes output planes
     * @param channel channel index
     * @param bits plane-indexed bitboard
     */
    private static void addBits(float[] planes, int channel, long bits) {
        int base = channel * TOKENS;
        while (bits != 0L) {
            int sq = Long.numberOfTrailingZeros(bits);
            planes[base + sq] = 1.0f;
            bits &= bits - 1L;
        }
    }

    /**
     * Fills one channel with a constant.
     *
     * @param planes output planes
     * @param channel channel index
     * @param value value to write
     */
    private static void fillConstant(float[] planes, int channel, float value) {
        int base = channel * TOKENS;
        for (int i = 0; i < TOKENS; i++) {
            planes[base + i] = value;
        }
    }

    /**
     * Applies canonical transform bits to a bitboard.
     *
     * @param bits input bitboard
     * @param transform transform bit mask
     * @return transformed bitboard
     */
    static long transformBits(long bits, int transform) {
        long out = bits;
        if ((transform & FLIP_TRANSFORM) != 0) {
            out = flipFiles(out);
        }
        if ((transform & MIRROR_TRANSFORM) != 0) {
            out = mirrorRanks(out);
        }
        if ((transform & TRANSPOSE_TRANSFORM) != 0) {
            out = transpose(out);
        }
        return out;
    }

    /**
     * Mirrors ranks (rank 1 to rank 8).
     *
     * @param bits bitboard
     * @return rank-mirrored bitboard
     */
    private static long mirrorRanks(long bits) {
        return Long.reverseBytes(bits);
    }

    /**
     * Mirrors files (file a to file h).
     *
     * @param bits bitboard
     * @return file-mirrored bitboard
     */
    private static long flipFiles(long bits) {
        long v = bits;
        v = ((v >>> 1) & 0x5555555555555555L) | ((v & 0x5555555555555555L) << 1);
        v = ((v >>> 2) & 0x3333333333333333L) | ((v & 0x3333333333333333L) << 2);
        v = ((v >>> 4) & 0x0F0F0F0F0F0F0F0FL) | ((v & 0x0F0F0F0F0F0F0F0FL) << 4);
        return v;
    }

    /**
     * Transposes across the A1-H8 diagonal.
     *
     * @param bits bitboard
     * @return transposed bitboard
     */
    private static long transpose(long bits) {
        long v = bits;
        v = ((v & 0xAA00AA00AA00AA00L) >>> 9)
                | ((v & 0x0055005500550055L) << 9)
                | (v & 0x55AA55AA55AA55AAL);
        v = ((v & 0xCCCC0000CCCC0000L) >>> 18)
                | ((v & 0x0000333300003333L) << 18)
                | (v & 0x3333CCCC3333CCCCL);
        v = ((v & 0xF0F0F0F000000000L) >>> 36)
                | ((v & 0x000000000F0F0F0FL) << 36)
                | (v & 0x0F0F0F0FF0F0F0F0L);
        return v;
    }
}
