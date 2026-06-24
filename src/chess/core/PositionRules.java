package chess.core;

import static chess.core.Position.*;

/**
 * Package-local piece, material, pawn-structure, and line-geometry helpers for {@link Position}.
 */
final class PositionRules {

    /**
     * Utility class; prevent instantiation.
     */
    private PositionRules() {
        // utility
    }

    /**
     * Validates a piece-array index.
     *
     * @param pieceIndex piece index to validate
     */
    static void requirePieceIndex(int pieceIndex) {
        if (pieceIndex < WHITE_PAWN || pieceIndex > BLACK_KING) {
            throw new IllegalArgumentException("Invalid piece index: " + pieceIndex);
        }
    }

    /**
     * Computes material for one side.
     *
     * @param position source position
     * @param white true for White, false for Black
     * @return material value
     */
    static int material(Position position, boolean white) {
        int start = white ? WHITE_PAWN : BLACK_PAWN;
        int end = white ? WHITE_KING : BLACK_KING;
        int value = 0;
        for (int piece = start; piece <= end; piece++) {
            value += Long.bitCount(position.pieces[piece]) * Piece.getValue(Position.pieceCode(piece));
        }
        return value;
    }

    /**
     * Returns the bitboard mask for a file.
     *
     * @param file zero-based file
     * @return file bitboard mask
     */
    static long fileMask(int file) {
        return switch (file) {
            case 0 -> Bits.FILE_A;
            case 1 -> Bits.FILE_B;
            case 2 -> Bits.FILE_C;
            case 3 -> Bits.FILE_D;
            case 4 -> Bits.FILE_E;
            case 5 -> Bits.FILE_F;
            case 6 -> Bits.FILE_G;
            case 7 -> Bits.FILE_H;
            default -> throw new IllegalArgumentException("Invalid file: " + file);
        };
    }

    /**
     * Returns whether a bishop bitboard contains a bishop on one square color.
     *
     * @param bishops bishop bitboard
     * @param color square color
     * @return true when a bishop occupies that color
     */
    static boolean hasBishopOnColor(long bishops, int color) {
        long scan = bishops;
        while (scan != 0L) {
            int square = Bits.lsb(scan);
            scan = Bits.withoutLsb(scan);
            if (squareColor(square) == color) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a square color parity.
     *
     * @param square square index
     * @return color parity
     */
    static int squareColor(int square) {
        return (Bits.file(square) + Bits.rank(square)) & 1;
    }

    /**
     * Expands a bitboard into square indices.
     *
     * @param mask source bitboard
     * @return occupied squares
     */
    static byte[] squares(long mask) {
        byte[] out = new byte[Long.bitCount(mask)];
        int index = 0;
        while (mask != 0L) {
            int square = Bits.lsb(mask);
            mask = Bits.withoutLsb(mask);
            out[index++] = (byte) square;
        }
        return out;
    }

    /**
     * Returns whether two coordinates share a rook or bishop line.
     *
     * @param firstFile source first file
     * @param firstRow first rank
     * @param secondFile source second file
     * @param secondRow second rank
     * @return true when the coordinates are aligned
     */
    static boolean aligned(int firstFile, int firstRow, int secondFile, int secondRow) {
        return firstFile == secondFile
                || firstRow == secondRow
                || Math.abs(firstFile - secondFile) == Math.abs(firstRow - secondRow);
    }

    /**
     * Returns whether a piece can pin along the requested line type.
     *
     * @param piece candidate piece index
     * @param pinnedWhite true when the pinned side is White
     * @param diagonal true for diagonal pins, false for orthogonal pins
     * @return true when the piece is an enemy slider for that line
     */
    static boolean isEnemySliderForPin(int piece, boolean pinnedWhite, boolean diagonal) {
        boolean enemy = pinnedWhite ? piece >= BLACK_PAWN : piece >= WHITE_PAWN && piece <= WHITE_KING;
        if (!enemy) {
            return false;
        }
        if (diagonal) {
            return piece == WHITE_BISHOP || piece == BLACK_BISHOP
                    || piece == WHITE_QUEEN || piece == BLACK_QUEEN;
        }
        return piece == WHITE_ROOK || piece == BLACK_ROOK
                || piece == WHITE_QUEEN || piece == BLACK_QUEEN;
    }
}
