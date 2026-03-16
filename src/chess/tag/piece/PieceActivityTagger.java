package chess.tag.piece;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import chess.core.Field;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Piece;
import chess.core.Position;
import chess.tag.core.AttackUtils;
import chess.tag.core.PinUtils;
import chess.tag.core.Text;

/**
 * Emits piece-activity related tags.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class PieceActivityTagger {

    private PieceActivityTagger() {
        // utility
    }

    public static List<String> tags(Position position) {
        Objects.requireNonNull(position, "position");

        byte[] board = position.getBoard();
        MoveList moves = position.getMoves();
        int[] mobility = new int[64];
        for (int i = 0, size = moves.size(); i < size; i++) {
            short move = moves.get(i);
            mobility[Move.getFromIndex(move)]++;
        }

        List<String> tags = new ArrayList<>();
        byte whiteKing = position.getWhiteKing();
        byte blackKing = position.getBlackKing();

        for (int index = 0; index < board.length; index++) {
            byte piece = board[index];
            if (piece == Piece.EMPTY) {
                continue;
            }
            byte square = (byte) index;
            boolean white = Piece.isWhite(piece);
            if (!Piece.isKing(piece) && PinUtils.isPinnedToKing(board, white, white ? whiteKing : blackKing, square)) {
                tags.add(Text.colorNameLower(piece) + " " + Text.pieceNameLower(piece) + " "
                        + Text.squareNameLower(square) + " pinned");
            }

            if (Piece.isKnight(piece) || Piece.isBishop(piece)) {
                if (isOutpost(board, white, square)) {
                    tags.add("outpost: " + Text.colorNameLower(piece) + " " + Text.pieceNameLower(piece) + " "
                            + Text.squareNameLower(square));
                }
            }

            if (Piece.isPawn(piece) || Piece.isKing(piece)) {
                continue;
            }

            int moveCount = mobility[square];
            if (moveCount == 0 && AttackUtils.countAttackers(board, !white, square) > 0) {
                tags.add("trapped " + Text.colorNameLower(piece) + " " + Text.pieceNameLower(piece) + " "
                        + Text.squareNameLower(square));
                continue;
            }

            int lowThreshold = lowMobilityThreshold(piece);
            int highThreshold = highMobilityThreshold(piece);
            if (moveCount <= lowThreshold) {
                tags.add("low mobility " + Text.colorNameLower(piece) + " " + Text.pieceNameLower(piece) + " "
                        + Text.squareNameLower(square));
            } else if (moveCount >= highThreshold) {
                tags.add("high mobility " + Text.colorNameLower(piece) + " " + Text.pieceNameLower(piece) + " "
                        + Text.squareNameLower(square));
            }
        }

        return List.copyOf(tags);
    }

    private static int lowMobilityThreshold(byte piece) {
        if (Piece.isQueen(piece)) {
            return 2;
        }
        if (Piece.isRook(piece)) {
            return 2;
        }
        if (Piece.isBishop(piece) || Piece.isKnight(piece)) {
            return 1;
        }
        return 0;
    }

    private static int highMobilityThreshold(byte piece) {
        if (Piece.isQueen(piece)) {
            return 10;
        }
        if (Piece.isRook(piece)) {
            return 7;
        }
        if (Piece.isBishop(piece) || Piece.isKnight(piece)) {
            return 5;
        }
        return Integer.MAX_VALUE;
    }

    private static boolean isOutpost(byte[] board, boolean white, byte square) {
        int rank = Field.getY(square);
        if (white && rank < 3) {
            return false;
        }
        if (!white && rank > 4) {
            return false;
        }
        if (!isPawnSupported(board, white, square)) {
            return false;
        }
        return !isPawnAttacked(board, !white, square);
    }

    private static boolean isPawnSupported(byte[] board, boolean white, byte square) {
        int file = Field.getX(square);
        int rank = Field.getY(square);
        int supportRank = white ? rank - 1 : rank + 1;
        if (supportRank < 0 || supportRank > 7) {
            return false;
        }
        for (int df = -1; df <= 1; df += 2) {
            int f = file + df;
            if (!Field.isOnBoard(f, supportRank)) {
                continue;
            }
            byte piece = board[Field.toIndex(f, supportRank)];
            if (Piece.isPawn(piece) && Piece.isWhite(piece) == white) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPawnAttacked(byte[] board, boolean pawnIsWhite, byte square) {
        int file = Field.getX(square);
        int rank = Field.getY(square);
        int pawnRank = pawnIsWhite ? rank - 1 : rank + 1;
        if (pawnRank < 0 || pawnRank > 7) {
            return false;
        }
        for (int df = -1; df <= 1; df += 2) {
            int f = file + df;
            if (!Field.isOnBoard(f, pawnRank)) {
                continue;
            }
            byte piece = board[Field.toIndex(f, pawnRank)];
            if (Piece.isPawn(piece) && Piece.isWhite(piece) == pawnIsWhite) {
                return true;
            }
        }
        return false;
    }
}
