package chess.tag.pawn;

import static chess.tag.core.Literals.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import chess.core.Move;
import chess.core.MoveList;
import chess.core.Piece;
import chess.core.Position;
import chess.tag.core.Text;

/**
 * Detects pawn promotions that are immediately available in a position.
 * <p>
 * The tag output is intentionally de-duplicated by source square so a pawn with
 * multiple promotion moves only contributes one availability fact.
 * </p>
 * @author Lennart A. Conrad
 * @since 2026
 */
public final class Promotion {

    /**
     * Prevents instantiation of this utility class.
     */
    private Promotion() {
        // utility
    }

    /**
     * Returns promotion-availability facts for the given position.
     * <p>
     * Each fact identifies the side and source square of a pawn that can
     * promote on the current move.
     * </p>
     *
     * @param position the position to inspect
     * @return an immutable list of promotion availability facts
     * @throws NullPointerException if {@code position} is {@code null}
     */
    public static List<String> tags(Position position) {
        Objects.requireNonNull(position, POSITION);

        MoveList moves = position.legalMoves();
        if (moves.isEmpty()) {
            return Collections.emptyList();
        }

        byte[] board = position.getBoard();
        boolean[] seen = new boolean[board.length];
        List<String> tags = new ArrayList<>();

        for (int i = 0; i < moves.size(); i++) {
            short move = moves.get(i);
            if (Move.isPromotion(move)) {
                byte from = Move.getFromIndex(move);
                if (!seen[from]) {
                    seen[from] = true;
                    byte piece = board[from];
                    if (piece != Piece.EMPTY && Piece.isPawn(piece)) {
                        tags.add(FACT_PREFIX + PROMOTION_AVAILABLE + SIDE_FIELD + Text.colorNameLower(piece)
                                + SQUARE_FIELD + Text.squareNameLower(from));
                    }
                }
            }
        }

        return List.copyOf(tags);
    }
}
