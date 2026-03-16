package application.cli.command;

import chess.core.Position;

/**
 * Shared helpers for null-move threat analysis.
 */
final class ThreatsSupport {

    private ThreatsSupport() {
        // utility
    }

    /**
     * Produces a new position representing the null-move turn swap.
     *
     * @param base base position
     * @return position after a null move
     */
    static Position nullMovePosition(Position base) {
        if (base.inCheck()) {
            throw new IllegalArgumentException("null move not legal while in check");
        }

        String[] parts = base.toString().split(" ");
        if (parts.length < 4) {
            throw new IllegalArgumentException("unexpected FEN: " + base);
        }

        boolean wasWhite = base.isWhiteTurn();
        String newTurn = wasWhite ? "b" : "w";

        String placement = parts[0];
        String castling = parts[2];
        String enPassant = "-";

        int halfMove = parts.length > 4 ? Integer.parseInt(parts[4]) : 0;
        int fullMove = parts.length > 5 ? Integer.parseInt(parts[5]) : 1;

        halfMove = Math.max(0, halfMove + 1);
        if (!wasWhite) {
            fullMove = Math.max(1, fullMove + 1);
        }

        String fen = String.format(
                "%s %s %s %s %d %d",
                placement,
                newTurn,
                castling,
                enPassant,
                halfMove,
                fullMove);
        return new Position(fen);
    }
}
