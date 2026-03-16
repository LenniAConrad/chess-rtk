package chess.tag.position;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import chess.core.Field;
import chess.core.Piece;
import chess.core.Position;

/**
 * Emits king-safety related tags.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class KingSafetyTagger {

    private KingSafetyTagger() {
        // utility
    }

    public static List<String> tags(Position position) {
        Objects.requireNonNull(position, "position");

        if (position.isChess960()) {
            return List.of();
        }

        List<String> tags = new ArrayList<>();
        byte[] board = position.getBoard();
        addKingTags(tags, board, true, position.getWhiteKing());
        addKingTags(tags, board, false, position.getBlackKing());
        return List.copyOf(tags);
    }

    private static void addKingTags(List<String> tags, byte[] board, boolean white, byte kingSquare) {
        if (kingSquare == Field.NO_SQUARE) {
            return;
        }
        String side = white ? "white" : "black";
        String castleTag = castlingStatus(kingSquare, white);
        if (castleTag != null) {
            tags.add(side + " " + castleTag);
        }

        boolean openFile = isOpenFile(board, Field.getX(kingSquare));
        if (openFile) {
            tags.add("open file near " + side + " king");
        }

        boolean shieldWeak = isPawnShieldWeakened(board, white, kingSquare);
        if (shieldWeak) {
            tags.add(side + " pawn shield weakened");
        }

        if (shieldWeak && (openFile || !isOnBackRank(white, kingSquare))) {
            tags.add(side + " king exposed");
        }
    }

    private static String castlingStatus(byte kingSquare, boolean white) {
        if (white) {
            if (kingSquare == Field.G1 || kingSquare == Field.C1) {
                return "castled";
            }
            if (kingSquare == Field.E1) {
                return "uncastled";
            }
        } else {
            if (kingSquare == Field.G8 || kingSquare == Field.C8) {
                return "castled";
            }
            if (kingSquare == Field.E8) {
                return "uncastled";
            }
        }
        return null;
    }

    private static boolean isOpenFile(byte[] board, int file) {
        for (int rank = 0; rank < 8; rank++) {
            int idx = Field.toIndex(file, rank);
            if (Piece.isPawn(board[idx])) {
                return false;
            }
        }
        return true;
    }

    private static boolean isPawnShieldWeakened(byte[] board, boolean white, byte kingSquare) {
        int file = Field.getX(kingSquare);
        int rank = Field.getY(kingSquare);
        int shieldRank = white ? rank + 1 : rank - 1;
        if (shieldRank < 0 || shieldRank > 7) {
            return true;
        }
        int pawns = 0;
        for (int f = file - 1; f <= file + 1; f++) {
            if (!Field.isOnBoard(f, shieldRank)) {
                continue;
            }
            byte piece = board[Field.toIndex(f, shieldRank)];
            if (Piece.isPawn(piece) && Piece.isWhite(piece) == white) {
                pawns++;
            }
        }
        return pawns <= 1;
    }

    private static boolean isOnBackRank(boolean white, byte square) {
        int rank = Field.getY(square);
        return white ? rank == 0 : rank == 7;
    }
}
