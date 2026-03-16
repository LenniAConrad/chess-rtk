package chess.tag.tactical;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import chess.core.Field;
import chess.core.Piece;
import chess.core.Position;
import chess.tag.core.AttackUtils;
import chess.tag.core.PinUtils;
import chess.tag.core.Text;

/**
 * Emits common tactical motif tags.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class TacticalTagger {

    private static final int[][] ORTHO_DIRS = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
    private static final int[][] DIAG_DIRS = { { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 } };
    private static final int[][] KNIGHT_DIRS = {
            { 1, 2 }, { 2, 1 }, { -1, 2 }, { -2, 1 },
            { 1, -2 }, { 2, -1 }, { -1, -2 }, { -2, -1 }
    };
    private static final int[][] KING_DIRS = {
            { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 },
            { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 }
    };

    private TacticalTagger() {
        // utility
    }

    public static List<String> tags(Position position) {
        Objects.requireNonNull(position, "position");

        byte[] board = position.getBoard();
        List<String> tags = new ArrayList<>();

        addPins(tags, board, true, position.getWhiteKing());
        addPins(tags, board, false, position.getBlackKing());
        addSkewers(tags, board);
        addDiscoveredAttacks(tags, board);
        addOverloadedDefenders(tags, board, true);
        addOverloadedDefenders(tags, board, false);
        addHangingPieces(tags, board);

        return List.copyOf(tags);
    }

    private static void addPins(List<String> tags, byte[] board, boolean pinnedIsWhite, byte kingSquare) {
        if (kingSquare == Field.NO_SQUARE) {
            return;
        }
        for (int index = 0; index < board.length; index++) {
            byte piece = board[index];
            if (piece == Piece.EMPTY || Piece.isWhite(piece) != pinnedIsWhite || Piece.isKing(piece)) {
                continue;
            }
            PinUtils.PinInfo info = PinUtils.findPinToKing(board, pinnedIsWhite, kingSquare, (byte) index);
            if (info == null) {
                continue;
            }
            String tag = "pin: " + Text.colorNameLower(info.pinnerPiece) + " "
                    + Text.pieceNameLower(info.pinnerPiece) + " " + Text.squareNameLower(info.pinnerSquare)
                    + " pins " + Text.colorNameLower(piece) + " " + Text.pieceNameLower(piece) + " "
                    + Text.squareNameLower(info.pinnedSquare) + " to king";
            tags.add(formatTactical(tag));
        }
    }

    private static void addSkewers(List<String> tags, byte[] board) {
        for (int index = 0; index < board.length; index++) {
            byte piece = board[index];
            if (piece == Piece.EMPTY || !(Piece.isRook(piece) || Piece.isBishop(piece) || Piece.isQueen(piece))) {
                continue;
            }
            int[][] dirs = Piece.isBishop(piece) ? DIAG_DIRS : (Piece.isRook(piece) ? ORTHO_DIRS : null);
            if (Piece.isQueen(piece)) {
                checkSkewers(tags, board, piece, (byte) index, ORTHO_DIRS);
                checkSkewers(tags, board, piece, (byte) index, DIAG_DIRS);
                continue;
            }
            checkSkewers(tags, board, piece, (byte) index, dirs);
        }
    }

    private static void checkSkewers(List<String> tags, byte[] board, byte piece, byte from, int[][] dirs) {
        if (dirs == null) {
            return;
        }
        boolean white = Piece.isWhite(piece);
        int fx = Field.getX(from);
        int fy = Field.getY(from);
        for (int[] dir : dirs) {
            int x = fx + dir[0];
            int y = fy + dir[1];
            int firstIdx = Field.NO_SQUARE;
            while (Field.isOnBoard(x, y)) {
                int idx = Field.toIndex(x, y);
                if (board[idx] != Piece.EMPTY) {
                    firstIdx = idx;
                    break;
                }
                x += dir[0];
                y += dir[1];
            }
            if (firstIdx == Field.NO_SQUARE) {
                continue;
            }
            byte firstPiece = board[firstIdx];
            if (Piece.isWhite(firstPiece) == white) {
                continue;
            }
            int firstValue = Piece.isKing(firstPiece) ? 10000 : Piece.getMaterialValue(firstPiece);
            int nx = Field.getX((byte) firstIdx) + dir[0];
            int ny = Field.getY((byte) firstIdx) + dir[1];
            int secondIdx = Field.NO_SQUARE;
            while (Field.isOnBoard(nx, ny)) {
                int idx = Field.toIndex(nx, ny);
                if (board[idx] != Piece.EMPTY) {
                    secondIdx = idx;
                    break;
                }
                nx += dir[0];
                ny += dir[1];
            }
            if (secondIdx == Field.NO_SQUARE) {
                continue;
            }
            byte secondPiece = board[secondIdx];
            if (Piece.isWhite(secondPiece) == white) {
                continue;
            }
            int secondValue = Piece.getMaterialValue(secondPiece);
            if (firstValue <= secondValue) {
                continue;
            }
            tags.add(formatTactical("skewer: " + Text.colorNameLower(piece) + " " + Text.pieceNameLower(piece) + " "
                    + Text.squareNameLower(from) + " skewers " + Text.colorNameLower(firstPiece) + " "
                    + Text.pieceNameLower(firstPiece) + " " + Text.squareNameLower((byte) firstIdx)
                    + " with " + Text.colorNameLower(secondPiece) + " " + Text.pieceNameLower(secondPiece) + " "
                    + Text.squareNameLower((byte) secondIdx) + " behind"));
        }
    }

    private static void addDiscoveredAttacks(List<String> tags, byte[] board) {
        for (int index = 0; index < board.length; index++) {
            byte piece = board[index];
            if (piece == Piece.EMPTY || !(Piece.isRook(piece) || Piece.isBishop(piece) || Piece.isQueen(piece))) {
                continue;
            }
            if (Piece.isQueen(piece)) {
                checkDiscovered(tags, board, piece, (byte) index, ORTHO_DIRS);
                checkDiscovered(tags, board, piece, (byte) index, DIAG_DIRS);
                continue;
            }
            int[][] dirs = Piece.isBishop(piece) ? DIAG_DIRS : ORTHO_DIRS;
            checkDiscovered(tags, board, piece, (byte) index, dirs);
        }
    }

    private static void checkDiscovered(List<String> tags, byte[] board, byte slider, byte from, int[][] dirs) {
        boolean white = Piece.isWhite(slider);
        int fx = Field.getX(from);
        int fy = Field.getY(from);
        for (int[] dir : dirs) {
            int x = fx + dir[0];
            int y = fy + dir[1];
            int blockerIdx = Field.NO_SQUARE;
            while (Field.isOnBoard(x, y)) {
                int idx = Field.toIndex(x, y);
                if (board[idx] != Piece.EMPTY) {
                    blockerIdx = idx;
                    break;
                }
                x += dir[0];
                y += dir[1];
            }
            if (blockerIdx == Field.NO_SQUARE) {
                continue;
            }
            byte blockerPiece = board[blockerIdx];
            if (Piece.isWhite(blockerPiece) != white) {
                continue;
            }
            if (!hasPseudoMove(board, (byte) blockerIdx, blockerPiece)) {
                continue;
            }
            int nx = Field.getX((byte) blockerIdx) + dir[0];
            int ny = Field.getY((byte) blockerIdx) + dir[1];
            int targetIdx = Field.NO_SQUARE;
            while (Field.isOnBoard(nx, ny)) {
                int idx = Field.toIndex(nx, ny);
                if (board[idx] != Piece.EMPTY) {
                    targetIdx = idx;
                    break;
                }
                nx += dir[0];
                ny += dir[1];
            }
            if (targetIdx == Field.NO_SQUARE) {
                continue;
            }
            byte targetPiece = board[targetIdx];
            if (Piece.isWhite(targetPiece) == white) {
                continue;
            }
            tags.add(formatTactical("discovered attack: moving " + Text.colorNameLower(blockerPiece) + " "
                    + Text.pieceNameLower(blockerPiece) + " " + Text.squareNameLower((byte) blockerIdx)
                    + " reveals " + Text.colorNameLower(slider) + " " + Text.pieceNameLower(slider) + " "
                    + Text.squareNameLower(from) + " attack on " + Text.colorNameLower(targetPiece) + " "
                    + Text.pieceNameLower(targetPiece) + " " + Text.squareNameLower((byte) targetIdx)));
        }
    }

    private static boolean hasPseudoMove(byte[] board, byte from, byte piece) {
        if (piece == Piece.EMPTY) {
            return false;
        }
        boolean white = Piece.isWhite(piece);
        if (Piece.isPawn(piece)) {
            return pawnHasMove(board, from, white);
        }
        if (Piece.isKnight(piece)) {
            return knightHasMove(board, from, white);
        }
        if (Piece.isKing(piece)) {
            return kingHasMove(board, from, white);
        }
        if (Piece.isBishop(piece)) {
            return sliderHasMove(board, from, white, DIAG_DIRS);
        }
        if (Piece.isRook(piece)) {
            return sliderHasMove(board, from, white, ORTHO_DIRS);
        }
        if (Piece.isQueen(piece)) {
            return sliderHasMove(board, from, white, ORTHO_DIRS) || sliderHasMove(board, from, white, DIAG_DIRS);
        }
        return false;
    }

    private static boolean pawnHasMove(byte[] board, byte from, boolean white) {
        int x = Field.getX(from);
        int y = Field.getY(from);
        int dir = white ? 1 : -1;
        int nextY = y + dir;
        if (Field.isOnBoard(x, nextY)) {
            int idx = Field.toIndex(x, nextY);
            if (board[idx] == Piece.EMPTY) {
                return true;
            }
        }
        int diagY = y + dir;
        if (Field.isOnBoard(x - 1, diagY)) {
            int idx = Field.toIndex(x - 1, diagY);
            if (board[idx] != Piece.EMPTY && Piece.isWhite(board[idx]) != white) {
                return true;
            }
        }
        if (Field.isOnBoard(x + 1, diagY)) {
            int idx = Field.toIndex(x + 1, diagY);
            if (board[idx] != Piece.EMPTY && Piece.isWhite(board[idx]) != white) {
                return true;
            }
        }
        int startRank = white ? 1 : 6;
        int twoY = y + (2 * dir);
        if (y == startRank && Field.isOnBoard(x, twoY)) {
            int mid = Field.toIndex(x, y + dir);
            int end = Field.toIndex(x, twoY);
            if (board[mid] == Piece.EMPTY && board[end] == Piece.EMPTY) {
                return true;
            }
        }
        return false;
    }

    private static boolean knightHasMove(byte[] board, byte from, boolean white) {
        int fx = Field.getX(from);
        int fy = Field.getY(from);
        for (int[] jump : KNIGHT_DIRS) {
            int x = fx + jump[0];
            int y = fy + jump[1];
            if (!Field.isOnBoard(x, y)) {
                continue;
            }
            byte target = board[Field.toIndex(x, y)];
            if (target == Piece.EMPTY || Piece.isWhite(target) != white) {
                return true;
            }
        }
        return false;
    }

    private static boolean kingHasMove(byte[] board, byte from, boolean white) {
        int fx = Field.getX(from);
        int fy = Field.getY(from);
        for (int[] dir : KING_DIRS) {
            int x = fx + dir[0];
            int y = fy + dir[1];
            if (!Field.isOnBoard(x, y)) {
                continue;
            }
            byte target = board[Field.toIndex(x, y)];
            if (target == Piece.EMPTY || Piece.isWhite(target) != white) {
                return true;
            }
        }
        return false;
    }

    private static boolean sliderHasMove(byte[] board, byte from, boolean white, int[][] dirs) {
        int fx = Field.getX(from);
        int fy = Field.getY(from);
        for (int[] dir : dirs) {
            int x = fx + dir[0];
            int y = fy + dir[1];
            if (!Field.isOnBoard(x, y)) {
                continue;
            }
            int idx = Field.toIndex(x, y);
            byte target = board[idx];
            if (target == Piece.EMPTY) {
                return true;
            }
            if (Piece.isWhite(target) != white) {
                return true;
            }
        }
        return false;
    }

    private static void addOverloadedDefenders(List<String> tags, byte[] board, boolean white) {
        Map<Byte, List<Byte>> overloaded = new HashMap<>();
        for (int index = 0; index < board.length; index++) {
            byte piece = board[index];
            if (piece == Piece.EMPTY || Piece.isWhite(piece) != white || Piece.isKing(piece)) {
                continue;
            }
            if (AttackUtils.countAttackers(board, !white, (byte) index) == 0) {
                continue;
            }
            List<Byte> defenders = AttackUtils.attackers(board, white, (byte) index);
            if (defenders.size() != 1) {
                continue;
            }
            byte defender = defenders.get(0);
            overloaded.computeIfAbsent(defender, k -> new ArrayList<>()).add((byte) index);
        }
        for (Map.Entry<Byte, List<Byte>> entry : overloaded.entrySet()) {
            List<Byte> defended = entry.getValue();
            if (defended.size() < 2) {
                continue;
            }
            byte defenderSquare = entry.getKey();
            byte defenderPiece = board[defenderSquare];
            StringBuilder detail = new StringBuilder();
            for (int i = 0; i < defended.size(); i++) {
                if (i > 0) {
                    detail.append(i == defended.size() - 1 ? " and " : ", ");
                }
                detail.append(Text.squareNameLower(defended.get(i)));
            }
            tags.add(formatTactical("overloaded defender: " + Text.colorNameLower(defenderPiece) + " "
                    + Text.pieceNameLower(defenderPiece) + " " + Text.squareNameLower(defenderSquare)
                    + " defends " + detail));
        }
    }

    private static void addHangingPieces(List<String> tags, byte[] board) {
        for (int index = 0; index < board.length; index++) {
            byte piece = board[index];
            if (piece == Piece.EMPTY || Piece.isKing(piece)) {
                continue;
            }
            boolean white = Piece.isWhite(piece);
            if (AttackUtils.countAttackers(board, !white, (byte) index) == 0) {
                continue;
            }
            if (AttackUtils.countAttackers(board, white, (byte) index) != 0) {
                continue;
            }
            tags.add(formatTactical("hanging " + Text.colorNameLower(piece) + " " + Text.pieceNameLower(piece) + " "
                    + Text.squareNameLower((byte) index)));
        }
    }

    private static String formatTactical(String text) {
        return "FACT: tactical=\"" + text.replace("\"", "\\\"") + "\"";
    }
}
