package chess.tag.material;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import chess.core.Position;
import chess.core.Piece;

/**
 * Emits compact material and piece-count summary tags.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class MaterialTagger {

    /**
     * Utility class, prevents instantiation.
     */
    private MaterialTagger() {
        // utility
    }

    /**
     * Returns material and piece-count tags for the current position.
     *
     * @param position position to inspect
     * @return immutable list of material tags
     */
    public static List<String> tags(Position position) {
        Objects.requireNonNull(position, "position");

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
        tags.add("META: material_cp_white=" + whiteMaterial);
        tags.add("META: material_cp_black=" + blackMaterial);
        tags.add("META: material_discrepancy_cp=" + formatSigned(discrepancy));
        tags.addAll(formatPieceCounts("white", whiteKings, whiteQueens, whiteRooks, whiteBishops, whiteKnights, whitePawns));
        tags.addAll(formatPieceCounts("black", blackKings, blackQueens, blackRooks, blackBishops, blackKnights, blackPawns));
        return List.copyOf(tags);
    }

    private static List<String> formatPieceCounts(String side, int kings, int queens, int rooks, int bishops,
            int knights, int pawns) {
        List<String> tags = new ArrayList<>(6);
        tags.add(formatCount(side, kings, "king"));
        tags.add(formatCount(side, queens, "queen"));
        tags.add(formatCount(side, rooks, "rook"));
        tags.add(formatCount(side, bishops, "bishop"));
        tags.add(formatCount(side, knights, "knight"));
        tags.add(formatCount(side, pawns, "pawn"));
        return tags;
    }

    private static String formatCount(String side, int count, String piece) {
        return "FACT: piece_count side=" + side + " piece=" + piece + " count=" + count;
    }
    private static String formatSigned(int value) {
        return Integer.toString(value);
    }
}
