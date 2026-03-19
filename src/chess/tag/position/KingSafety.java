package chess.tag.position;

import static chess.tag.core.Literals.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import chess.core.Field;
import chess.core.Piece;
import chess.core.Position;

/**
 * Derives king-safety tags from a position.
 * <p>
 * The output describes whether each king is castled or uncastled, whether a
 * file near the king is open, whether the pawn shield is weakened, and whether
 * the king is exposed.
 * </p>
 * @author Lennart A. Conrad
 * @since 2026
 */
public final class KingSafety {

    /**
     * Prevents instantiation of this utility class.
     */
    private KingSafety() {
        // utility
    }

    /**
     * Returns king-safety tags for the given position.
     *
     * @param position the position to inspect
     * @return an immutable list of king-safety tags
     * @throws NullPointerException if {@code position} is {@code null}
     */
    public static List<String> tags(Position position) {
        Objects.requireNonNull(position, POSITION);

        if (position.isChess960()) {
            return List.of();
        }

        List<String> tags = new ArrayList<>();
        byte[] board = position.getBoard();
        addKingTags(tags, board, true, position.getWhiteKing());
        addKingTags(tags, board, false, position.getBlackKing());
        return List.copyOf(tags);
    }

    /**
     * Adds king-safety tags for one side.
     *
     * @param tags the mutable tag accumulator
     * @param board the board array
     * @param white whether the king belongs to White
     * @param kingSquare the king square
     */
    private static void addKingTags(List<String> tags, byte[] board, boolean white, byte kingSquare) {
        if (kingSquare == Field.NO_SQUARE) {
            return;
        }
        String side = white ? WHITE : BLACK;
        String castleTag = castlingStatus(kingSquare, white);
        if (castleTag != null) {
            tags.add(side + SPACE_TEXT + castleTag);
        }

        boolean openFile = isOpenFile(board, Field.getX(kingSquare));
        if (openFile) {
            tags.add(OPEN_FILE_NEAR + side + SPACE_TEXT + KING_NAME);
        }

        boolean shieldWeak = isPawnShieldWeakened(board, white, kingSquare);
        if (shieldWeak) {
            tags.add(side + SPACE_TEXT + PAWN_SHIELD_WEAKENED);
        }

        if (shieldWeak && (openFile || !isOnBackRank(white, kingSquare))) {
            tags.add(side + SPACE_TEXT + KING_EXPOSED);
        }
    }

    /**
     * Determines whether the king is already castled or still uncastled.
     *
     * @param kingSquare the king square
     * @param white whether the king belongs to White
     * @return the castling status label, or {@code null} when it does not apply
     */
    private static String castlingStatus(byte kingSquare, boolean white) {
        if (white) {
            if (kingSquare == Field.G1 || kingSquare == Field.C1) {
                return CASTLED;
            }
            if (kingSquare == Field.E1) {
                return UNCASTLED;
            }
        } else {
            if (kingSquare == Field.G8 || kingSquare == Field.C8) {
                return CASTLED;
            }
            if (kingSquare == Field.E8) {
                return UNCASTLED;
            }
        }
        return null;
    }

    /**
     * Checks whether a file contains no pawns at all.
     *
     * @param board the board array
     * @param file the file to inspect
     * @return {@code true} when the file is open
     */
    private static boolean isOpenFile(byte[] board, int file) {
        for (int rank = 0; rank < 8; rank++) {
            int idx = Field.toIndex(file, rank);
            if (Piece.isPawn(board[idx])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks whether the pawn shield in front of a king is weakened.
     *
     * @param board the board array
     * @param white whether the king belongs to White
     * @param kingSquare the king square
     * @return {@code true} when the pawn shield is weak
     */
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

    /**
     * Checks whether the king is still on its original back rank.
     *
     * @param white whether the king belongs to White
     * @param square the king square
     * @return {@code true} when the king is on the original back rank
     */
    private static boolean isOnBackRank(boolean white, byte square) {
        int rank = Field.getY(square);
        return white ? rank == 0 : rank == 7;
    }
}
