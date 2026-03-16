package chess.tag.core;

import java.util.ArrayList;
import java.util.List;

import chess.core.Field;
import chess.core.Piece;

/**
 * Shared attack-geometry helpers for taggers.
 */
public final class AttackUtils {

    private static final int[][] KNIGHT_DELTAS = {
            { 1, 2 }, { 2, 1 }, { 2, -1 }, { 1, -2 },
            { -1, -2 }, { -2, -1 }, { -2, 1 }, { -1, 2 }
    };

    private static final int[][] KING_DELTAS = {
            { 1, 0 }, { 1, 1 }, { 0, 1 }, { -1, 1 },
            { -1, 0 }, { -1, -1 }, { 0, -1 }, { 1, -1 }
    };

    private static final int[][] BISHOP_DIRECTIONS = { { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 } };

    private static final int[][] ROOK_DIRECTIONS = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

    private AttackUtils() {
        // utility
    }

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
                int direction = Piece.isWhite(piece) ? 1 : -1;
                return (dy == direction && (dx == 1 || dx == -1));
            }
            case Piece.WHITE_KNIGHT: {
                for (int[] delta : KNIGHT_DELTAS) {
                    if (dx == delta[0] && dy == delta[1]) {
                        return true;
                    }
                }
                return false;
            }
            case Piece.WHITE_BISHOP: {
                if (Math.abs(dx) != Math.abs(dy)) {
                    return false;
                }
                return clearPath(board, fx, fy, tx, ty, dx, dy);
            }
            case Piece.WHITE_ROOK: {
                if (dx != 0 && dy != 0) {
                    return false;
                }
                return clearPath(board, fx, fy, tx, ty, dx, dy);
            }
            case Piece.WHITE_QUEEN: {
                if (dx == 0 || dy == 0 || Math.abs(dx) == Math.abs(dy)) {
                    return clearPath(board, fx, fy, tx, ty, dx, dy);
                }
                return false;
            }
            case Piece.WHITE_KING: {
                return Math.abs(dx) <= 1 && Math.abs(dy) <= 1;
            }
            default:
                return false;
        }
    }

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
