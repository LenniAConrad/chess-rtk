package chess.tag;

import static chess.tag.core.Literals.*;

import java.util.ArrayList;
import java.util.List;

import chess.core.Field;
import chess.core.Piece;
import chess.core.Position;
import chess.tag.core.Text;

/**
 * Emits actual-checkmate attributes and conservative mate-pattern tags.
 *
 * @author Lennart A. Conrad
 * @since 2026
 */
final class Checkmate {

    /**
     * Prevents instantiation.
     */
    private Checkmate() {
        // utility
    }

    /**
     * Adds checkmate tags for terminal mating positions.
     *
     * @param context shared tagging context
     * @param out mutable tag accumulator
     */
    static void addTags(Context context, List<String> out) {
        Position position = context.position;
        if (!position.inCheck() || !context.legalMoves().isEmpty()) {
            return;
        }
        boolean defenderWhite = position.isWhiteToMove();
        String defender = sideName(defenderWhite);
        String winner = sideName(!defenderWhite);
        byte[] board = context.board();
        List<Byte> checkers = checkerSquares(position, defenderWhite);

        Emitter.tag(CHECKMATE).field(WINNER, winner).emit(out);
        Emitter.tag(CHECKMATE).field(DEFENDER, defender).emit(out);
        Emitter.tag(CHECKMATE).field(DELIVERY, delivery(board, checkers)).emit(out);
        addPatternTags(out, board, position, defenderWhite, checkers);
    }

    /**
     * Adds conservative mate-pattern tags.
     *
     * @param out mutable tag accumulator
     * @param board board snapshot
     * @param position checkmated position
     * @param defenderWhite whether White is mated
     * @param checkers checker squares
     */
    private static void addPatternTags(List<String> out, byte[] board, Position position, boolean defenderWhite,
            List<Byte> checkers) {
        if (checkers.size() >= 2) {
            addPattern(out, DOUBLE_CHECK);
        }
        if (isBackRankMate(board, position, defenderWhite, checkers)) {
            addPattern(out, BACK_RANK_MATE);
        }
        if (isSmotheredMate(board, position, defenderWhite, checkers)) {
            addPattern(out, SMOTHERED_MATE);
        }
        if (isCornerMate(position, defenderWhite)) {
            addPattern(out, CORNER_MATE);
        }
        if (isSupportMate(board, position, defenderWhite, checkers)) {
            addPattern(out, SUPPORT_MATE);
        }
    }

    /**
     * Adds one checkmate pattern tag.
     *
     * @param out mutable tag accumulator
     * @param pattern pattern label
     */
    private static void addPattern(List<String> out, String pattern) {
        Emitter.tag(CHECKMATE).field(PATTERN, pattern).emit(out);
    }

    /**
     * Returns the checker squares attacking the mated side's king.
     *
     * @param position checkmated position
     * @param defenderWhite whether White is mated
     * @return checker squares
     */
    private static List<Byte> checkerSquares(Position position, boolean defenderWhite) {
        byte king = position.kingSquare(defenderWhite);
        if (king == Field.NO_SQUARE) {
            return List.of();
        }
        byte[] attackers = defenderWhite ? position.getAttackersByBlack(king) : position.getAttackersByWhite(king);
        List<Byte> checkers = new ArrayList<>(attackers.length);
        for (byte attacker : attackers) {
            checkers.add(attacker);
        }
        return checkers;
    }

    /**
     * Returns the delivery-piece label for a mate.
     *
     * @param board board snapshot
     * @param checkers checker squares
     * @return piece label or {@code multiple}
     */
    private static String delivery(byte[] board, List<Byte> checkers) {
        if (checkers.size() != 1) {
            return MULTIPLE;
        }
        byte piece = board[checkers.get(0)];
        return piece == Piece.EMPTY ? MULTIPLE : Text.pieceNameLower(piece);
    }

    /**
     * Conservatively detects a classic back-rank mate geometry.
     *
     * @param board board snapshot
     * @param position checkmated position
     * @param defenderWhite whether White is mated
     * @param checkers checker squares
     * @return true when a rook or queen mates a boxed king on its home rank
     */
    private static boolean isBackRankMate(byte[] board, Position position, boolean defenderWhite,
            List<Byte> checkers) {
        if (checkers.size() != 1) {
            return false;
        }
        byte king = position.kingSquare(defenderWhite);
        if (king == Field.NO_SQUARE || Field.getY(king) != (defenderWhite ? 0 : 7)) {
            return false;
        }
        byte checker = checkers.get(0);
        byte checkerPiece = board[checker];
        if (!Piece.isRook(checkerPiece) && !Piece.isQueen(checkerPiece)) {
            return false;
        }
        if (Field.getX(checker) != Field.getX(king) && Field.getY(checker) != Field.getY(king)) {
            return false;
        }
        return friendlyEscapeRankBlockers(board, defenderWhite, Field.getX(king)) >= 2;
    }

    /**
     * Conservatively detects smothered mate.
     *
     * @param board board snapshot
     * @param position checkmated position
     * @param defenderWhite whether White is mated
     * @param checkers checker squares
     * @return true when a knight mates a king boxed by its own pieces
     */
    private static boolean isSmotheredMate(byte[] board, Position position, boolean defenderWhite,
            List<Byte> checkers) {
        if (checkers.size() != 1 || !Piece.isKnight(board[checkers.get(0)])) {
            return false;
        }
        return adjacentFriendlyBlockers(board, position.kingSquare(defenderWhite), defenderWhite) >= 3;
    }

    /**
     * Detects corner mate from the mated king square.
     *
     * @param position checkmated position
     * @param defenderWhite whether White is mated
     * @return true when the mated king is on a corner square
     */
    private static boolean isCornerMate(Position position, boolean defenderWhite) {
        byte king = position.kingSquare(defenderWhite);
        return king == Field.A1 || king == Field.H1 || king == Field.A8 || king == Field.H8;
    }

    /**
     * Detects whether the checking piece is protected by another attacking piece.
     *
     * @param board board snapshot
     * @param position checkmated position
     * @param defenderWhite whether White is mated
     * @param checkers checker squares
     * @return true when a single checker is protected by its side
     */
    private static boolean isSupportMate(byte[] board, Position position, boolean defenderWhite,
            List<Byte> checkers) {
        if (checkers.size() != 1) {
            return false;
        }
        byte checker = checkers.get(0);
        if (board[checker] == Piece.EMPTY) {
            return false;
        }
        byte[] defenders = defenderWhite
                ? position.getAttackersByBlack(checker)
                : position.getAttackersByWhite(checker);
        for (byte defender : defenders) {
            if (defender != checker && board[defender] != Piece.EMPTY) {
                return true;
            }
        }
        return false;
    }

    /**
     * Counts friendly blockers on the escape rank in front of a back-rank king.
     *
     * @param board board snapshot
     * @param defenderWhite whether the blocked king belongs to White
     * @param kingFile king file
     * @return number of friendly blockers on the three escape files
     */
    private static int friendlyEscapeRankBlockers(byte[] board, boolean defenderWhite, int kingFile) {
        int escapeRank = defenderWhite ? 1 : 6;
        int blockers = 0;
        for (int file = kingFile - 1; file <= kingFile + 1; file++) {
            if (!Field.isOnBoard(file, escapeRank)) {
                continue;
            }
            byte piece = board[Field.toIndex(file, escapeRank)];
            if (piece != Piece.EMPTY && Piece.isWhite(piece) == defenderWhite) {
                blockers++;
            }
        }
        return blockers;
    }

    /**
     * Counts mated-side pieces adjacent to the king.
     *
     * @param board board snapshot
     * @param king mated king square
     * @param defenderWhite whether White is mated
     * @return adjacent friendly blockers
     */
    private static int adjacentFriendlyBlockers(byte[] board, byte king, boolean defenderWhite) {
        if (king == Field.NO_SQUARE) {
            return 0;
        }
        int blockers = 0;
        int kingFile = Field.getX(king);
        int kingRank = Field.getY(king);
        for (int df = -1; df <= 1; df++) {
            for (int dr = -1; dr <= 1; dr++) {
                if (df == 0 && dr == 0) {
                    continue;
                }
                int file = kingFile + df;
                int rank = kingRank + dr;
                if (!Field.isOnBoard(file, rank)) {
                    continue;
                }
                byte piece = board[Field.toIndex(file, rank)];
                if (piece != Piece.EMPTY && Piece.isWhite(piece) == defenderWhite) {
                    blockers++;
                }
            }
        }
        return blockers;
    }

    /**
     * Returns the canonical label for a side.
     *
     * @param white whether the side is White
     * @return {@code white} or {@code black}
     */
    private static String sideName(boolean white) {
        return white ? WHITE : BLACK;
    }
}
