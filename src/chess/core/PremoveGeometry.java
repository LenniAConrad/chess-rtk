package chess.core;

import java.util.Arrays;

/**
 * Pseudo-legal movement geometry used when a UI queues a premove.
 *
 * <p>
 * A premove is not a legal move in the current position. It is a proposed move
 * shape that may become legal after the opponent replies, so this helper checks
 * piece geometry and a small amount of board context without consulting side to
 * move, check, pins, or blockers.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class PremoveGeometry {

    /**
     * Prevents instantiation.
     */
    private PremoveGeometry() {
        // utility
    }

    /**
     * Returns whether an encoded premove has usable source and target squares.
     *
     * @param move encoded move
     * @return true when the move can be displayed as a queued premove
     */
    public static boolean isEncodedShape(short move) {
        if (move == Move.NO_MOVE) {
            return false;
        }
        try {
            byte from = Move.getFromIndex(move);
            byte to = Move.getToIndex(move);
            return isSquare(from) && isSquare(to) && from != to;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * Returns every pseudo-legal premove target for one source square.
     *
     * @param position optional board context for castling rook checks
     * @param from source square
     * @param piece encoded moving piece
     * @return target squares
     */
    public static byte[] targets(Position position, byte from, byte piece) {
        if (!isSquare(from) || piece == Piece.EMPTY) {
            return new byte[0];
        }
        byte[] buffer = new byte[64];
        int count = 0;
        for (byte target = 0; target < 64; target++) {
            if (target != from && isTarget(position, from, piece, target)) {
                buffer[count++] = target;
            }
        }
        return Arrays.copyOf(buffer, count);
    }

    /**
     * Returns whether a target square matches one piece's premove geometry.
     *
     * @param position optional board context for castling rook checks
     * @param from source square
     * @param piece encoded moving piece
     * @param to target square
     * @return true when the target is geometrically plausible
     */
    public static boolean isTarget(Position position, byte from, byte piece, byte to) {
        if (!isSquare(from) || !isSquare(to) || from == to || piece == Piece.EMPTY) {
            return false;
        }
        int fromFile = Field.getX(from);
        int fromRank = Field.getY(from);
        int toFile = Field.getX(to);
        int toRank = Field.getY(to);
        int df = Math.abs(toFile - fromFile);
        int dr = Math.abs(toRank - fromRank);
        return switch (Math.abs(piece)) {
            case Piece.PAWN -> isPawnTarget(piece, fromFile, fromRank, toFile, toRank);
            case Piece.KNIGHT -> df * dr == 2;
            case Piece.BISHOP -> df == dr;
            case Piece.ROOK -> df == 0 || dr == 0;
            case Piece.QUEEN -> df == dr || df == 0 || dr == 0;
            case Piece.KING -> Math.max(df, dr) == 1
                    || isCastleTarget(position, piece, fromFile, fromRank, toFile, toRank);
            default -> false;
        };
    }

    /**
     * Returns the default promotion code for a premove ending on the last rank.
     *
     * @param piece encoded moving piece
     * @param to target square
     * @return queen promotion code, or zero for non-promotion moves
     */
    public static byte promotion(byte piece, byte to) {
        if (piece != Piece.WHITE_PAWN && piece != Piece.BLACK_PAWN) {
            return 0;
        }
        int rank = Field.getY(to);
        return rank == 0 || rank == 7 ? (byte) 4 : 0;
    }

    /**
     * Returns whether a square index is inside the board.
     *
     * @param square square index
     * @return true for {@code 0..63}
     */
    private static boolean isSquare(byte square) {
        return square >= 0 && square < 64;
    }

    /**
     * Returns whether a pawn can premove to the target coordinates.
     *
     * @param piece encoded pawn
     * @param fromFile source file index
     * @param fromRank source rank index
     * @param toFile target file index
     * @param toRank target rank index
     * @return true when the pawn move shape is plausible
     */
    private static boolean isPawnTarget(byte piece, int fromFile, int fromRank, int toFile, int toRank) {
        int direction = Piece.isWhite(piece) ? 1 : -1;
        int df = Math.abs(toFile - fromFile);
        int rankDelta = toRank - fromRank;
        if (df == 1) {
            return rankDelta == direction;
        }
        if (df != 0) {
            return false;
        }
        if (rankDelta == direction) {
            return true;
        }
        int startRank = Piece.isWhite(piece) ? 1 : 6;
        return fromRank == startRank && rankDelta == direction * 2;
    }

    /**
     * Returns whether a king premove targets a rook-backed castling square.
     *
     * @param position board context
     * @param piece encoded king
     * @param fromFile source file index
     * @param fromRank source rank index
     * @param toFile target file index
     * @param toRank target rank index
     * @return true when the corresponding rook is present
     */
    private static boolean isCastleTarget(Position position, byte piece, int fromFile, int fromRank, int toFile,
            int toRank) {
        boolean white = Piece.isWhite(piece);
        int homeRank = white ? 0 : 7;
        if (fromFile != 4 || fromRank != homeRank || toRank != homeRank || position == null) {
            return false;
        }
        byte[] board = position.getBoard();
        byte rook = white ? Piece.WHITE_ROOK : Piece.BLACK_ROOK;
        if (toFile == 6) {
            return board[(byte) Field.toIndex(7, homeRank)] == rook;
        }
        if (toFile == 2) {
            return board[(byte) Field.toIndex(0, homeRank)] == rook;
        }
        return false;
    }
}
