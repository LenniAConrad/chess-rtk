package chess.tag.material;

import static chess.tag.core.Literals.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import chess.core.Field;
import chess.core.Piece;
import chess.core.Position;

/**
 * Derives coarse endgame classification tags from the current material balance.
 * <p>
 * The output identifies queenless endgames, minor-piece endgames, rook endgames,
 * and opposite-colored bishop endgames.
 * </p>
 * @author Lennart A. Conrad
 * @since 2026
 */
public final class Endgame {

    /**
     * Prevents instantiation of this utility class.
     */
    private Endgame() {
        // utility
    }

    /**
     * Returns the canonical endgame tags for the given position.
     *
     * @param position the position to inspect
     * @return an immutable list of endgame facts
     * @throws NullPointerException if {@code position} is {@code null}
     */
    public static List<String> tags(Position position) {
        Objects.requireNonNull(position, POSITION);

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
            tags.add(FACT_ENDGAME_PREFIX + QUEENLESS);
        }

        boolean hasMinors = (whiteBishops + blackBishops + whiteKnights + blackKnights) > 0;
        boolean hasRooks = (whiteRooks + blackRooks) > 0;
        if (queenless && hasMinors && !hasRooks) {
            tags.add(FACT_ENDGAME_PREFIX + MINOR_PIECE_ENDGAME);
        }
        if (queenless && !hasMinors && whiteRooks > 0 && blackRooks > 0) {
            tags.add(FACT_ENDGAME_PREFIX + ROOK_ENDGAME);
        }

        if (queenless && whiteBishops == 1 && blackBishops == 1 && hasOppositeColoredBishops(position.getBoard())) {
            tags.add(FACT_OPPOSITE_COLORED_BISHOPS_PREFIX + TRUE);
        }

        return List.copyOf(tags);
    }

    /**
     * Checks whether the board contains bishops on opposite colors.
     *
     * @param board the board array to inspect
     * @return {@code true} when both sides have bishops on opposite-colored squares
     */
    private static boolean hasOppositeColoredBishops(byte[] board) {
        BishopColorState bishops = new BishopColorState();
        for (int index = 0; index < board.length; index++) {
            byte piece = board[index];
            if (Piece.isBishop(piece)) {
                bishops.mark(piece, (byte) index);
            }
        }
        return bishops.hasOppositeColors();
    }

    /**
     * Tracks bishop-square colors for both sides.
 * @author Lennart A. Conrad
 * @since 2026
 */
    private static final class BishopColorState {

        /**
         * Whether White has a bishop on a light square.
         */
        private boolean whiteLight;

        /**
         * Whether White has a bishop on a dark square.
         */
        private boolean whiteDark;

        /**
         * Whether Black has a bishop on a light square.
         */
        private boolean blackLight;

        /**
         * Whether Black has a bishop on a dark square.
         */
        private boolean blackDark;

        /**
         * Records a bishop on the color of its square.
         *
         * @param piece the bishop piece code
         * @param square the square index
         */
        private void mark(byte piece, byte square) {
            boolean light = isLightSquare(square);
            if (Piece.isWhite(piece)) {
                whiteLight |= light;
                whiteDark |= !light;
            } else {
                blackLight |= light;
                blackDark |= !light;
            }
        }

        /**
         * Checks whether the bishop colors are opposite across the two sides.
         *
         * @return {@code true} when the bishop colors are opposite
         */
        private boolean hasOppositeColors() {
            return (whiteLight && blackDark) || (whiteDark && blackLight);
        }

        /**
         * Determines whether a square is light-colored.
         *
         * @param square the square index
         * @return {@code true} when the square is light-colored
         */
        private static boolean isLightSquare(byte square) {
            int file = Field.getX(square);
            int rank = Field.getY(square);
            return ((file + rank) & 1) == 0;
        }
    }
}
