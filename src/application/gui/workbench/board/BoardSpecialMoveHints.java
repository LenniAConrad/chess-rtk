package application.gui.workbench.board;

import chess.core.Field;
import chess.core.Move;
import chess.core.Position;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds Workbench board arrows for castling-right and en-passant hints.
 */
final class BoardSpecialMoveHints {

    /**
     * Prevents instantiation.
     */
    private BoardSpecialMoveHints() {
        // utility
    }

    /**
     * Returns special-move hint arrows for the supplied position.
     *
     * @param position source position
     * @return encoded from-to arrows
     */
    static List<Short> arrows(Position position) {
        if (position == null) {
            return List.of();
        }
        List<Short> arrows = new ArrayList<>(5);
        addEnPassant(position, arrows);
        addCastlingRights(position, arrows);
        return arrows;
    }

    /**
     * Adds the en-passant hint arrow, when the FEN has one.
     *
     * @param position source position
     * @param arrows target arrow list
     */
    private static void addEnPassant(Position position, List<Short> arrows) {
        byte enPassant = position.enPassantSquare();
        if (enPassant == Field.NO_SQUARE) {
            return;
        }
        if (Field.isOn6thRank(enPassant)) {
            arrows.add(Short.valueOf(Move.of((byte) (enPassant - 8), (byte) (enPassant + 8))));
            return;
        }
        if (Field.isOn3rdRank(enPassant)) {
            arrows.add(Short.valueOf(Move.of((byte) (enPassant + 8), (byte) (enPassant - 8))));
        }
    }

    /**
     * Adds active castling-right arrows.
     *
     * @param position source position
     * @param arrows target arrow list
     */
    private static void addCastlingRights(Position position, List<Short> arrows) {
        addCastlingRights(position, true, arrows);
        addCastlingRights(position, false, arrows);
    }

    /**
     * Adds active castling-right arrows for one side.
     *
     * @param position source position
     * @param white true for white
     * @param arrows target arrow list
     */
    private static void addCastlingRights(Position position, boolean white, List<Short> arrows) {
        byte king = position.kingSquare(white);
        if (king == Field.NO_SQUARE) {
            return;
        }
        addCastlingRight(position, king, white ? Position.WHITE_KINGSIDE : Position.BLACK_KINGSIDE, arrows);
        addCastlingRight(position, king, white ? Position.WHITE_QUEENSIDE : Position.BLACK_QUEENSIDE, arrows);
    }

    /**
     * Adds one active castling-right arrow.
     *
     * @param position source position
     * @param king king square
     * @param right castling right
     * @param arrows target arrow list
     */
    private static void addCastlingRight(Position position, byte king, int right, List<Short> arrows) {
        byte target = position.activeCastlingMoveTarget(right);
        if (target != Field.NO_SQUARE) {
            arrows.add(Short.valueOf(Move.of(king, target)));
        }
    }
}
