package chess.tag.pawn;

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
 * Emits tags when a pawn promotion is available.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class PromotionTagger {

    /**
     * Utility class, prevents instantiation.
     */
    private PromotionTagger() {
        // utility
    }

    /**
     * Returns tags when promotion moves are available.
     *
     * @param position position to inspect
     * @return immutable list of promotion tags
     */
    public static List<String> tags(Position position) {
        Objects.requireNonNull(position, "position");

        MoveList moves = position.getMoves();
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
                        tags.add("FACT: promotion_available side=" + Text.colorNameLower(piece) + " square="
                                + Text.squareNameLower(from));
                    }
                }
            }
        }

        return List.copyOf(tags);
    }
}
