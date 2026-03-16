package chess.tag.position;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import chess.core.Field;
import chess.core.Piece;
import chess.core.Position;
import chess.tag.core.AttackUtils;

/**
 * Emits center-control and space-related tags.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class CenterSpaceTagger {

    private static final byte[] CENTER_SQUARES = {
            Field.D4, Field.E4, Field.D5, Field.E5
    };

    private CenterSpaceTagger() {
        // utility
    }

    public static List<String> tags(Position position) {
        Objects.requireNonNull(position, "position");

        byte[] board = position.getBoard();
        List<String> tags = new ArrayList<>();

        addCenterControl(tags, board);
        addSpaceAdvantage(tags, board);
        addCenterStructure(tags, board);

        return List.copyOf(tags);
    }

    private static void addCenterControl(List<String> tags, byte[] board) {
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
            whiteControl += AttackUtils.countAttackers(board, true, square);
            blackControl += AttackUtils.countAttackers(board, false, square);
        }
        int diff = whiteControl - blackControl;
        if (diff >= 2) {
            tags.add("FACT: center_control=white");
        } else if (diff <= -2) {
            tags.add("FACT: center_control=black");
        } else {
            tags.add("FACT: center_control=balanced");
        }
    }

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
            tags.add("FACT: space_advantage=white");
        } else if (diff <= -2) {
            tags.add("FACT: space_advantage=black");
        }
    }

    private static void addCenterStructure(List<String> tags, byte[] board) {
        boolean hasCenterPawn = false;
        boolean blocked = false;
        for (int index = 0; index < board.length; index++) {
            byte piece = board[index];
            if (!Piece.isPawn(piece)) {
                continue;
            }
            int file = Field.getX((byte) index);
            if (file != 3 && file != 4) {
                continue;
            }
            hasCenterPawn = true;
            int rank = Field.getY((byte) index);
            if (Piece.isWhite(piece)) {
                int forward = rank + 1;
                if (forward <= 7) {
                    int target = Field.toIndex(file, forward);
                    if (board[target] == Piece.BLACK_PAWN) {
                        blocked = true;
                    }
                }
            } else {
                int forward = rank - 1;
                if (forward >= 0) {
                    int target = Field.toIndex(file, forward);
                    if (board[target] == Piece.WHITE_PAWN) {
                        blocked = true;
                    }
                }
            }
        }
        if (!hasCenterPawn) {
            tags.add("FACT: center_state=open");
        } else if (blocked) {
            tags.add("FACT: center_state=closed");
        }
    }
}
