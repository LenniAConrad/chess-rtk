package chess.tag.position;

import static chess.tag.core.Literals.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import chess.core.Field;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Position;

/**
 * Derives castling-right and castling-status tags from a position.
 * <p>
 * The output distinguishes castling rights, immediate castling availability,
 * and whether each king is already castled or still uncastled.
 * </p>
 * @author Lennart A. Conrad
 * @since 2026
 */
public final class Castling {

    /**
     * Prevents instantiation of this utility class.
     */
    private Castling() {
        // utility
    }

    /**
     * Returns castling-related tags for the given position.
     *
     * @param position the position to inspect
     * @return an immutable list of castling facts
     * @throws NullPointerException if {@code position} is {@code null}
     */
    public static List<String> tags(Position position) {
        Objects.requireNonNull(position, POSITION);

        List<String> tags = new ArrayList<>(8);
        addRights(tags, WHITE, position.getWhiteKingside(), position.getWhiteQueenside());
        addRights(tags, BLACK, position.getBlackKingside(), position.getBlackQueenside());

        if (!position.isChess960()) {
            addStatus(tags, WHITE, position.getWhiteKing());
            addStatus(tags, BLACK, position.getBlackKing());
        }

        if (position.isWhiteTurn()) {
            addCastleNowTags(tags, WHITE, position.getWhiteKing(),
                    position.getWhiteKingside(), position.getWhiteQueenside(), position.getMoves());
        } else {
            addCastleNowTags(tags, BLACK, position.getBlackKing(),
                    position.getBlackKingside(), position.getBlackQueenside(), position.getMoves());
        }

        return List.copyOf(tags);
    }

    /**
     * Adds castling-right tags for one side.
     *
     * @param tags the mutable tag accumulator
     * @param side the side being processed
     * @param kingside the kingside rook square
     * @param queenside the queenside rook square
     */
    private static void addRights(List<String> tags, String side, byte kingside, byte queenside) {
        if (kingside != Field.NO_SQUARE) {
            tags.add(CASTLING_RIGHT_PREFIX + side + ROOK_SIDE_FIELD + KINGSIDE);
        }
        if (queenside != Field.NO_SQUARE) {
            tags.add(CASTLING_RIGHT_PREFIX + side + ROOK_SIDE_FIELD + QUEENSIDE);
        }
    }

    /**
     * Adds immediate castling-availability tags for one side.
     *
     * @param tags the mutable tag accumulator
     * @param side the side being processed
     * @param kingSquare the king square
     * @param kingside the kingside destination square
     * @param queenside the queenside destination square
     * @param moves the legal move list
     */
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
            tags.add(CAN_CASTLE_PREFIX + side + ROOK_SIDE_FIELD + KINGSIDE);
        }
        if (canQueenside) {
            tags.add(CAN_CASTLE_PREFIX + side + ROOK_SIDE_FIELD + QUEENSIDE);
        }
    }

    /**
     * Adds castling-status tags for one king.
     *
     * @param tags the mutable tag accumulator
     * @param side the side being processed
     * @param kingSquare the king square
     */
    private static void addStatus(List<String> tags, String side, byte kingSquare) {
        if (kingSquare == Field.NO_SQUARE) {
            return;
        }
        if (WHITE.equals(side)) {
            if (kingSquare == Field.G1 || kingSquare == Field.C1) {
                tags.add(CASTLING_STATUS_PREFIX + WHITE + SPACE_TEXT + STATUS + EQUAL_SIGN + CASTLED);
            } else if (kingSquare == Field.E1) {
                tags.add(CASTLING_STATUS_PREFIX + WHITE + SPACE_TEXT + STATUS + EQUAL_SIGN + UNCASTLED);
            }
        } else {
            if (kingSquare == Field.G8 || kingSquare == Field.C8) {
                tags.add(CASTLING_STATUS_PREFIX + BLACK + SPACE_TEXT + STATUS + EQUAL_SIGN + CASTLED);
            } else if (kingSquare == Field.E8) {
                tags.add(CASTLING_STATUS_PREFIX + BLACK + SPACE_TEXT + STATUS + EQUAL_SIGN + UNCASTLED);
            }
        }
    }
}
