package chess.tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import chess.core.Move;
import chess.core.MoveList;
import chess.core.Piece;
import chess.core.Position;
import chess.core.SAN;

/**
 * Generates simple, move-centric tags that describe tactical outcomes.
 *
 * <p>
 * Each tag is prefixed with the UCI move string and followed by a short label
 * such as {@code capture}, {@code check}, or {@code castle kingside}. The tagger
 * looks only at the supplied position and move list; it does not attempt to
 * validate illegal moves.
 * </p>
 *
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class MoveTagger {

    /**
     * Utility class, prevents instantiation.
     */
    private MoveTagger() {
        // Utility class
    }

    /**
     * Produces tags for a single move.
     *
     * @param position position to evaluate from
     * @param move move to evaluate
     * @return immutable list of tags for the move
     */
    public static List<String> tags(Position position, short move) {
        Objects.requireNonNull(position, "position");
        List<String> tags = new ArrayList<>();
        byte[] board = position.getBoard();
        byte from = Move.getFromIndex(move);
        byte to = Move.getToIndex(move);
        if (board[from] == Piece.EMPTY) {
            return Collections.unmodifiableList(tags);
        }

        String san = SAN.toAlgebraic(position, move);
        String prefix = san;
        if (prefix.endsWith("+") || prefix.endsWith("#")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }

        if (position.isCapture(from, to)) {
            tags.add(prefix + " capture");
        }
        if (position.isEnPassantCapture(from, to)) {
            tags.add(prefix + " en passant");
        }
        if (san.startsWith("O-O-O")) {
            tags.add(prefix + " castle queenside");
        } else if (san.startsWith("O-O")) {
            tags.add(prefix + " castle kingside");
        }

        int promotion = Move.getPromotion(move);
        if (promotion != 0) {
            tags.add(prefix + " promotion");
            if (!san.contains("=Q")) {
                tags.add(prefix + " underpromotion");
            }
        }

        boolean isCheckmate = san.endsWith("#");
        boolean isCheck = isCheckmate || san.endsWith("+");
        if (isCheck) {
            tags.add(prefix + " check");
        }
        if (isCheckmate) {
            tags.add(prefix + " checkmate");
        } else {
            Position next = position.copyOf().play(move);
            if (!next.inCheck() && next.getMoves().isEmpty()) {
                tags.add(prefix + " stalemate");
            }
        }

        return Collections.unmodifiableList(tags);
    }

    /**
     * Produces tags for every move in {@code moves}.
     *
     * @param position position to evaluate from
     * @param moves list of candidate moves
     * @return immutable list of tags across all moves
     */
    public static List<String> tags(Position position, MoveList moves) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(moves, "moves");
        List<String> tags = new ArrayList<>();
        for (int i = 0; i < moves.size(); i++) {
            tags.addAll(tags(position, moves.get(i)));
        }
        return Collections.unmodifiableList(tags);
    }

}
