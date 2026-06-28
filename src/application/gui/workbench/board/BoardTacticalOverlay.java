package application.gui.workbench.board;

import chess.core.Field;
import chess.core.MoveList;
import chess.core.Piece;
import chess.core.Position;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes lightweight tactical badges shown directly on the Workbench board.
 */
final class BoardTacticalOverlay {

    /**
     * Prevents instantiation.
     */
    private BoardTacticalOverlay() {
        // utility
    }

    /**
     * Returns overlay badges for the selected board hints.
     *
     * @param position source position
     * @param showUndefended true to mark attacked, undefended pieces
     * @param showPinned true to mark pieces pinned to their king
     * @param showCheckableKing true to mark the opponent king when a legal check exists
     * @return overlay badges
     */
    static List<Badge> badges(Position position,
            boolean showUndefended,
            boolean showPinned,
            boolean showCheckableKing) {
        if (position == null || (!showUndefended && !showPinned && !showCheckableKing)) {
            return List.of();
        }
        List<Badge> badges = new ArrayList<>(8);
        if (showUndefended || showPinned) {
            addPieceBadges(position, showUndefended, showPinned, badges);
        }
        if (showCheckableKing) {
            addCheckableKingBadge(position, badges);
        }
        return badges;
    }

    /**
     * Adds per-piece tactical badges.
     *
     * @param position source position
     * @param showUndefended true to add undefended-piece badges
     * @param showPinned true to add pinned-piece badges
     * @param badges output list
     */
    private static void addPieceBadges(Position position,
            boolean showUndefended,
            boolean showPinned,
            List<Badge> badges) {
        byte[] board = position.getBoard();
        for (byte square = 0; square < 64; square++) {
            byte piece = board[square];
            if (piece == Piece.EMPTY || Math.abs(piece) == Piece.KING) {
                continue;
            }
            boolean white = Piece.isWhite(piece);
            boolean pinned = showPinned && position.isPinnedToOwnKing(square);
            if (pinned) {
                badges.add(new Badge(square, Kind.PINNED));
            }
            if (!pinned && showUndefended && position.isSquareAttacked(square, !white)
                    && !position.isSquareAttacked(square, white)) {
                badges.add(new Badge(square, Kind.UNDEFENDED));
            }
        }
    }

    /**
     * Adds a king badge when the side to move has at least one legal checking move.
     *
     * @param position source position
     * @param badges output list
     */
    private static void addCheckableKingBadge(Position position, List<Badge> badges) {
        MoveList moves = position.legalMoves();
        for (int i = 0; i < moves.size(); i++) {
            Position child = position.copy().play(moves.raw(i));
            if (child.inCheck()) {
                byte king = position.kingSquare(!position.isWhiteToMove());
                if (king != Field.NO_SQUARE) {
                    badges.add(new Badge(king, Kind.CHECKABLE_KING));
                }
                return;
            }
        }
    }

    /**
     * One tactical board badge.
     *
     * @param square badge square
     * @param kind badge kind
     */
    record Badge(byte square, Kind kind) {
    }

    /**
     * Tactical badge classes.
     */
    enum Kind {
        /**
         * Attacked piece with no same-side defender.
         */
        UNDEFENDED,

        /**
         * Piece pinned to its own king by an enemy slider.
         */
        PINNED,

        /**
         * Opponent king can be checked by at least one legal move.
         */
        CHECKABLE_KING
    }
}
