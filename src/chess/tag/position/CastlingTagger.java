package chess.tag.position;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import chess.core.Field;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Position;

/**
 * Emits tags that describe castling rights and immediate castling availability.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class CastlingTagger {

    /**
     * Utility class, prevents instantiation.
     */
    private CastlingTagger() {
        // utility
    }

    /**
     * Returns castling-related tags for the current position.
     *
     * @param position position to inspect
     * @return immutable list of castling tags
     */
    public static List<String> tags(Position position) {
        Objects.requireNonNull(position, "position");

        List<String> tags = new ArrayList<>(8);
        addRights(tags, "white", position.getWhiteKingside(), position.getWhiteQueenside());
        addRights(tags, "black", position.getBlackKingside(), position.getBlackQueenside());

        if (!position.isChess960()) {
            addStatus(tags, "white", position.getWhiteKing());
            addStatus(tags, "black", position.getBlackKing());
        }

        if (position.isWhiteTurn()) {
            addCastleNowTags(tags, "white", position.getWhiteKing(),
                    position.getWhiteKingside(), position.getWhiteQueenside(), position.getMoves());
        } else {
            addCastleNowTags(tags, "black", position.getBlackKing(),
                    position.getBlackKingside(), position.getBlackQueenside(), position.getMoves());
        }

        return List.copyOf(tags);
    }

    private static void addRights(List<String> tags, String side, byte kingside, byte queenside) {
        if (kingside != Field.NO_SQUARE) {
            tags.add("FACT: castling_right side=" + side + " rook_side=kingside");
        }
        if (queenside != Field.NO_SQUARE) {
            tags.add("FACT: castling_right side=" + side + " rook_side=queenside");
        }
    }

    private static void addCastleNowTags(List<String> tags, String side, byte kingSquare,
            byte kingside, byte queenside, MoveList moves) {
        if (kingside == Field.NO_SQUARE && queenside == Field.NO_SQUARE) {
            return;
        }
        boolean canKingside = false;
        boolean canQueenside = false;
        for (int i = 0; i < moves.size(); i++) {
            short move = moves.get(i);
            byte from = Move.getFromIndex(move);
            if (from != kingSquare) {
                continue;
            }
            byte to = Move.getToIndex(move);
            if (to == kingside) {
                canKingside = true;
            } else if (to == queenside) {
                canQueenside = true;
            }
        }
        if (canKingside) {
            tags.add("FACT: can_castle side=" + side + " rook_side=kingside");
        }
        if (canQueenside) {
            tags.add("FACT: can_castle side=" + side + " rook_side=queenside");
        }
    }

    private static void addStatus(List<String> tags, String side, byte kingSquare) {
        if (kingSquare == Field.NO_SQUARE) {
            return;
        }
        if ("white".equals(side)) {
            if (kingSquare == Field.G1 || kingSquare == Field.C1) {
                tags.add("FACT: castling_status side=white status=castled");
            } else if (kingSquare == Field.E1) {
                tags.add("FACT: castling_status side=white status=uncastled");
            }
        } else {
            if (kingSquare == Field.G8 || kingSquare == Field.C8) {
                tags.add("FACT: castling_status side=black status=castled");
            } else if (kingSquare == Field.E8) {
                tags.add("FACT: castling_status side=black status=uncastled");
            }
        }
    }
}
