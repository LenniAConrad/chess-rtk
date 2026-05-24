package chess.tag.material;

import static chess.tag.core.Literals.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import chess.core.Position;
import chess.core.Piece;

/**
 * Produces material-count tags for a position.
 * <p>
 * The output includes total centipawn material per side, the signed material
 * discrepancy, and per-piece counts for both sides.
 * </p>
 * @author Lennart A. Conrad
 * @since 2026
 */
public final class Counts {

    /**
     * Prevents instantiation of this utility class.
     */
    private Counts() {
        // utility
    }

    /**
     * Returns the canonical material tags for the given position.
     *
     * @param position the position to inspect
     * @return an immutable list of material facts
     * @throws NullPointerException if {@code position} is {@code null}
     */
    public static List<String> tags(Position position) {
        Objects.requireNonNull(position, POSITION);

        int whiteMaterial = position.countWhiteMaterial();
        int blackMaterial = position.countBlackMaterial();
        int discrepancy = position.materialDiscrepancy();

        int whitePawns = position.countPieces(Piece.WHITE_PAWN);
        int whiteKnights = position.countPieces(Piece.WHITE_KNIGHT);
        int whiteBishops = position.countPieces(Piece.WHITE_BISHOP);
        int whiteRooks = position.countPieces(Piece.WHITE_ROOK);
        int whiteQueens = position.countPieces(Piece.WHITE_QUEEN);
        int whiteKings = position.countPieces(Piece.WHITE_KING);

        int blackPawns = position.countPieces(Piece.BLACK_PAWN);
        int blackKnights = position.countPieces(Piece.BLACK_KNIGHT);
        int blackBishops = position.countPieces(Piece.BLACK_BISHOP);
        int blackRooks = position.countPieces(Piece.BLACK_ROOK);
        int blackQueens = position.countPieces(Piece.BLACK_QUEEN);
        int blackKings = position.countPieces(Piece.BLACK_KING);

        List<String> tags = new ArrayList<>(16);
        tags.add(META_MATERIAL_CP_WHITE_PREFIX + whiteMaterial);
        tags.add(META_MATERIAL_CP_BLACK_PREFIX + blackMaterial);
        tags.add(META_MATERIAL_DISCREPANCY_CP_PREFIX + formatSigned(discrepancy));
        tags.addAll(formatPieceCounts(WHITE, whiteKings, whiteQueens, whiteRooks, whiteBishops, whiteKnights, whitePawns));
        tags.addAll(formatPieceCounts(BLACK, blackKings, blackQueens, blackRooks, blackBishops, blackKnights, blackPawns));
        return List.copyOf(tags);
    }

    /**
     * Formats per-piece counts for a single side.
     *
     * @param side the side name
     * @param kings the number of kings
     * @param queens the number of queens
     * @param rooks the number of rooks
     * @param bishops the number of bishops
     * @param knights the number of knights
     * @param pawns the number of pawns
     * @return the list of serialized piece-count facts for that side
     */
    private static List<String> formatPieceCounts(String side, int kings, int queens, int rooks, int bishops,
            int knights, int pawns) {
        List<String> tags = new ArrayList<>(6);
        tags.add(formatCount(side, kings, KING_NAME));
        tags.add(formatCount(side, queens, QUEEN));
        tags.add(formatCount(side, rooks, ROOK));
        tags.add(formatCount(side, bishops, BISHOP));
        tags.add(formatCount(side, knights, KNIGHT));
        tags.add(formatCount(side, pawns, PAWN));
        return tags;
    }

    /**
     * Formats a single piece-count fact.
     *
     * @param side the side name
     * @param count the number of pieces
     * @param piece the piece name
     * @return the serialized piece-count tag
     */
    private static String formatCount(String side, int count, String piece) {
        return FACT_PREFIX + PIECE_COUNT + SIDE_FIELD + side + SPACE_TEXT + PIECE_KEY + EQUAL_SIGN + piece + COUNT_FIELD
                + count;
    }

    /**
     * Formats a signed material value as text.
     *
     * @param value the signed centipawn value
     * @return the signed text representation
     */
    private static String formatSigned(int value) {
        return Integer.toString(value);
    }
}
