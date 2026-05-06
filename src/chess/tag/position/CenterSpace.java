package chess.tag.position;

import static chess.tag.core.Literals.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import chess.core.Field;
import chess.core.Piece;
import chess.core.Position;

/**
 * Derives center-control, space, and center-structure tags from a position.
 * <p>
 * These tags describe who controls the central squares, which side has more
 * advanced space, and whether the center is open or closed.
 * </p>
 * @author Lennart A. Conrad
 * @since 2026
 */
public final class CenterSpace {

    /**
     * The four central squares used for control analysis.
     */
    private static final byte[] CENTER_SQUARES = {
            Field.D4, Field.E4, Field.D5, Field.E5
    };

    /**
     * Prevents instantiation of this utility class.
     */
    private CenterSpace() {
        // utility
    }

    /**
     * Returns center- and space-related tags for the given position.
     *
     * @param position the position to inspect
     * @return an immutable list of center and space tags
     * @throws NullPointerException if {@code position} is {@code null}
     */
    public static List<String> tags(Position position) {
        Objects.requireNonNull(position, POSITION);

        byte[] board = position.getBoard();
        List<String> tags = new ArrayList<>();

        addCenterControl(tags, position, board);
        addSpaceAdvantage(tags, board);
        addCenterStructure(tags, board);

        return List.copyOf(tags);
    }

    /**
     * Adds a center-control tag based on attackers and occupation.
     *
     * @param tags the mutable tag accumulator
     * @param position the analyzed position
     * @param board the board array
     */
    private static void addCenterControl(List<String> tags, Position position, byte[] board) {
        int whiteControl = 0;
        int blackControl = 0;
        for (byte square : CENTER_SQUARES) {
            byte piece = board[square];
            if (piece != Piece.EMPTY) {
                if (Piece.isWhite(piece)) {
                    whiteControl++;
                } else {
                    blackControl++;
                }
            }
            whiteControl += position.countAttackersByWhite(square);
            blackControl += position.countAttackersByBlack(square);
        }
        int diff = whiteControl - blackControl;
        if (diff >= 2) {
            tags.add(CENTER_CONTROL_PREFIX + WHITE);
        } else if (diff <= -2) {
            tags.add(CENTER_CONTROL_PREFIX + BLACK);
        } else {
            tags.add(CENTER_CONTROL_PREFIX + BALANCED);
        }
        tags.add(SPACE_CENTER_CONTROL_PREFIX + WHITE + COUNT_FIELD + whiteControl);
        tags.add(SPACE_CENTER_CONTROL_PREFIX + BLACK + COUNT_FIELD + blackControl);
    }

    /**
     * Adds a space-advantage tag when one side has clearly more advanced pieces.
     *
     * @param tags the mutable tag accumulator
     * @param board the board array
     */
    private static void addSpaceAdvantage(List<String> tags, byte[] board) {
        int whiteSpace = 0;
        int blackSpace = 0;
        for (int index = 0; index < board.length; index++) {
            byte piece = board[index];
            if (piece == Piece.EMPTY || Piece.isKing(piece)) {
                continue;
            }
            int rank = Field.getY((byte) index);
            if (Piece.isWhite(piece) && rank >= 4) {
                whiteSpace++;
            } else if (Piece.isBlack(piece) && rank <= 3) {
                blackSpace++;
            }
        }
        int diff = whiteSpace - blackSpace;
        if (diff >= 2) {
            tags.add(SPACE_ADVANTAGE_PREFIX + WHITE);
        } else if (diff <= -2) {
            tags.add(SPACE_ADVANTAGE_PREFIX + BLACK);
        }
    }

    /**
     * Adds a center-state tag describing whether the center is open or closed.
     *
     * @param tags the mutable tag accumulator
     * @param board the board array
     */
    private static void addCenterStructure(List<String> tags, byte[] board) {
        boolean hasCenterPawn = false;
        boolean blocked = false;
        for (int index = 0; index < board.length; index++) {
            byte square = (byte) index;
            if (isCenterPawn(board[index], square)) {
                hasCenterPawn = true;
                blocked |= isBlockedCenterPawn(board, square);
            }
        }
        if (!hasCenterPawn) {
            tags.add(FACT_PREFIX + CENTER_STATE + EQUAL_SIGN + OPEN);
        } else if (blocked) {
            tags.add(FACT_PREFIX + CENTER_STATE + EQUAL_SIGN + CLOSED);
        }
    }

    /**
     * Checks whether a piece is a center pawn.
     *
     * @param piece the piece to inspect
     * @param square the square to inspect
     * @return {@code true} when the piece is a pawn on file d or e
     */
    private static boolean isCenterPawn(byte piece, byte square) {
        if (!Piece.isPawn(piece)) {
            return false;
        }
        int file = Field.getX(square);
        return file == 3 || file == 4;
    }

    /**
     * Checks whether a center pawn is blocked immediately in front of it.
     *
     * @param board the board array
     * @param square the pawn square
     * @return {@code true} when the pawn is blocked by an opposing pawn
     */
    private static boolean isBlockedCenterPawn(byte[] board, byte square) {
        byte piece = board[square];
        int file = Field.getX(square);
        int rank = Field.getY(square);
        int forward = Piece.isWhite(piece) ? rank + 1 : rank - 1;
        if (forward < 0 || forward > 7) {
            return false;
        }
        int target = Field.toIndex(file, forward);
        byte blocker = board[target];
        return Piece.isWhite(piece) ? blocker == Piece.BLACK_PAWN : blocker == Piece.WHITE_PAWN;
    }
}
