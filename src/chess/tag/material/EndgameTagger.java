package chess.tag.material;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import chess.core.Field;
import chess.core.Piece;
import chess.core.Position;

/**
 * Emits tags describing endgame material patterns.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class EndgameTagger {

    private EndgameTagger() {
        // utility
    }

    public static List<String> tags(Position position) {
        Objects.requireNonNull(position, "position");

        int whiteQueens = position.countPieces(Piece.WHITE_QUEEN);
        int blackQueens = position.countPieces(Piece.BLACK_QUEEN);
        int whiteRooks = position.countPieces(Piece.WHITE_ROOK);
        int blackRooks = position.countPieces(Piece.BLACK_ROOK);
        int whiteBishops = position.countPieces(Piece.WHITE_BISHOP);
        int blackBishops = position.countPieces(Piece.BLACK_BISHOP);
        int whiteKnights = position.countPieces(Piece.WHITE_KNIGHT);
        int blackKnights = position.countPieces(Piece.BLACK_KNIGHT);

        List<String> tags = new ArrayList<>();
        boolean queenless = (whiteQueens + blackQueens) == 0;
        if (queenless) {
            tags.add("FACT: endgame=queenless");
        }

        boolean hasMinors = (whiteBishops + blackBishops + whiteKnights + blackKnights) > 0;
        boolean hasRooks = (whiteRooks + blackRooks) > 0;
        if (queenless && hasMinors && !hasRooks) {
            tags.add("FACT: endgame=minor_piece_endgame");
        }
        if (queenless && !hasMinors && whiteRooks > 0 && blackRooks > 0) {
            tags.add("FACT: endgame=rook_endgame");
        }

        if (whiteBishops > 0 && blackBishops > 0 && hasOppositeColoredBishops(position.getBoard())) {
            tags.add("FACT: opposite_colored_bishops=true");
        }

        return List.copyOf(tags);
    }

    private static boolean hasOppositeColoredBishops(byte[] board) {
        boolean whiteLight = false;
        boolean whiteDark = false;
        boolean blackLight = false;
        boolean blackDark = false;
        for (int index = 0; index < board.length; index++) {
            byte piece = board[index];
            if (!Piece.isBishop(piece)) {
                continue;
            }
            int file = Field.getX((byte) index);
            int rank = Field.getY((byte) index);
            boolean light = ((file + rank) & 1) == 0;
            if (Piece.isWhite(piece)) {
                if (light) {
                    whiteLight = true;
                } else {
                    whiteDark = true;
                }
            } else {
                if (light) {
                    blackLight = true;
                } else {
                    blackDark = true;
                }
            }
        }
        return (whiteLight && blackDark) || (whiteDark && blackLight);
    }
}
