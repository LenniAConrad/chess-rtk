package chess.tag.core;

import java.util.ArrayList;
import java.util.List;

import chess.core.Field;
import chess.core.Piece;

/**
 * Computes attack relationships between pieces and squares.
 * <p>
 * The helpers in this class are used to count attackers, collect attacking
 * pieces, and test whether an individual piece attacks a target square.
 * </p>
 * @author Lennart A. Conrad
 * @since 2026
 */
public final class AttackUtils {

    /**
     * The relative knight moves used when testing knight attacks.
     */
    private static final int[][] KNIGHT_DELTAS = {
            { 1, 2 }, { 2, 1 }, { 2, -1 }, { 1, -2 },
            { -1, -2 }, { -2, -1 }, { -2, 1 }, { -1, 2 }
    };

    /**
     * Prevents instantiation of this utility class.
     */
    private AttackUtils() {
        // utility
    }

    /**
     * Returns the squares of all pieces that attack a target square.
     *
     * @param board the board array to inspect
     * @param attackersWhite whether to collect White attackers or Black attackers
     * @param targetSquare the square being attacked
     * @return the list of attacker square indices
     */
    public static List<Byte> attackers(byte[] board, boolean attackersWhite, byte targetSquare) {
        List<Byte> attackers = new ArrayList<>();
        for (int index = 0; index < board.length; index++) {
            byte piece = board[index];
            if (piece == Piece.EMPTY || Piece.isWhite(piece) != attackersWhite) {
                continue;
            }
            if (attacksSquare(board, piece, (byte) index, targetSquare)) {
                attackers.add((byte) index);
            }
        }
        return attackers;
    }

    /**
     * Counts how many pieces attack a target square.
     *
     * @param board the board array to inspect
     * @param attackersWhite whether to count White attackers or Black attackers
     * @param targetSquare the square being attacked
     * @return the number of attacking pieces
     */
    public static int countAttackers(byte[] board, boolean attackersWhite, byte targetSquare) {
        int count = 0;
        for (int index = 0; index < board.length; index++) {
            byte piece = board[index];
            if (piece == Piece.EMPTY || Piece.isWhite(piece) != attackersWhite) {
                continue;
            }
            if (attacksSquare(board, piece, (byte) index, targetSquare)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Checks whether a given piece attacks a target square.
     *
     * @param board the board array to inspect
     * @param piece the piece to test
     * @param from the source square of the piece
     * @param target the target square
     * @return {@code true} when the piece attacks the target square
     */
    public static boolean attacksSquare(byte[] board, byte piece, byte from, byte target) {
        if (from == target) {
            return false;
        }
        int fx = Field.getX(from);
        int fy = Field.getY(from);
        int tx = Field.getX(target);
        int ty = Field.getY(target);
        int dx = tx - fx;
        int dy = ty - fy;
        int absPiece = Math.abs(piece);

        switch (absPiece) {
            case Piece.WHITE_PAWN: {
                return pawnAttacks(piece, dx, dy);
            }
            case Piece.WHITE_KNIGHT: {
                return knightAttacks(dx, dy);
            }
            case Piece.WHITE_BISHOP: {
                return bishopAttacks(board, fx, fy, tx, ty, dx, dy);
            }
            case Piece.WHITE_ROOK: {
                return rookAttacks(board, fx, fy, tx, ty, dx, dy);
            }
            case Piece.WHITE_QUEEN: {
                return queenAttacks(board, fx, fy, tx, ty, dx, dy);
            }
            case Piece.WHITE_KING: {
                return kingAttacks(dx, dy);
            }
            default:
                return false;
        }
    }

    /**
     * Tests whether a pawn attacks a target square.
     *
     * @param piece the pawn piece code
     * @param dx the horizontal distance to the target
     * @param dy the vertical distance to the target
     * @return {@code true} when the pawn attacks the target square
     */
    private static boolean pawnAttacks(byte piece, int dx, int dy) {
        int direction = Piece.isWhite(piece) ? 1 : -1;
        return dy == direction && (dx == 1 || dx == -1);
    }

    /**
     * Tests whether a knight attacks a target square.
     *
     * @param dx the horizontal distance to the target
     * @param dy the vertical distance to the target
     * @return {@code true} when the knight attacks the target square
     */
    private static boolean knightAttacks(int dx, int dy) {
        for (int[] delta : KNIGHT_DELTAS) {
            if (dx == delta[0] && dy == delta[1]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tests whether a bishop attacks a target square.
     *
     * @param board the board array to inspect
     * @param fx the source file
     * @param fy the source rank
     * @param tx the target file
     * @param ty the target rank
     * @param dx the horizontal distance to the target
     * @param dy the vertical distance to the target
     * @return {@code true} when the bishop attacks the target square
     */
    private static boolean bishopAttacks(byte[] board, int fx, int fy, int tx, int ty, int dx, int dy) {
        return Math.abs(dx) == Math.abs(dy) && clearPath(board, fx, fy, tx, ty, dx, dy);
    }

    /**
     * Tests whether a rook attacks a target square.
     *
     * @param board the board array to inspect
     * @param fx the source file
     * @param fy the source rank
     * @param tx the target file
     * @param ty the target rank
     * @param dx the horizontal distance to the target
     * @param dy the vertical distance to the target
     * @return {@code true} when the rook attacks the target square
     */
    private static boolean rookAttacks(byte[] board, int fx, int fy, int tx, int ty, int dx, int dy) {
        return (dx == 0 || dy == 0) && clearPath(board, fx, fy, tx, ty, dx, dy);
    }

    /**
     * Tests whether a queen attacks a target square.
     *
     * @param board the board array to inspect
     * @param fx the source file
     * @param fy the source rank
     * @param tx the target file
     * @param ty the target rank
     * @param dx the horizontal distance to the target
     * @param dy the vertical distance to the target
     * @return {@code true} when the queen attacks the target square
     */
    private static boolean queenAttacks(byte[] board, int fx, int fy, int tx, int ty, int dx, int dy) {
        return (dx == 0 || dy == 0 || Math.abs(dx) == Math.abs(dy))
                && clearPath(board, fx, fy, tx, ty, dx, dy);
    }

    /**
     * Tests whether a king attacks a target square.
     *
     * @param dx the horizontal distance to the target
     * @param dy the vertical distance to the target
     * @return {@code true} when the king attacks the target square
     */
    private static boolean kingAttacks(int dx, int dy) {
        return Math.abs(dx) <= 1 && Math.abs(dy) <= 1;
    }

    /**
     * Checks that the line between source and target contains no blocking pieces.
     *
     * @param board the board array to inspect
     * @param fx the source file
     * @param fy the source rank
     * @param tx the target file
     * @param ty the target rank
     * @param dx the horizontal distance to the target
     * @param dy the vertical distance to the target
     * @return {@code true} when every intervening square is empty
     */
    private static boolean clearPath(byte[] board, int fx, int fy, int tx, int ty, int dx, int dy) {
        int stepX = Integer.signum(dx);
        int stepY = Integer.signum(dy);
        int x = fx + stepX;
        int y = fy + stepY;
        while (x != tx || y != ty) {
            int idx = Field.toIndex(x, y);
            if (board[idx] != Piece.EMPTY) {
                return false;
            }
            x += stepX;
            y += stepY;
        }
        return true;
    }
}
