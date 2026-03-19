package chess.tag.piece;

import static chess.tag.core.Literals.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import chess.core.Field;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Piece;
import chess.core.Position;
import chess.tag.core.AttackUtils;
import chess.tag.core.PinUtils;
import chess.tag.core.Text;

/**
 * Derives activity-based tags for individual pieces.
 * <p>
 * The generated tags cover pinned pieces, outposts, trapped pieces, and low or
 * high mobility for pieces that meaningfully move.
 * </p>
 * @author Lennart A. Conrad
 * @since 2026
 */
public final class PieceActivity {

    /**
     * Prevents instantiation of this utility class.
     */
    private PieceActivity() {
        // utility
    }

    /**
     * Returns activity tags for every piece on the board.
     *
     * @param position the position to analyze
     * @return an immutable list of activity tags
     * @throws NullPointerException if {@code position} is {@code null}
     */
    public static List<String> tags(Position position) {
        Objects.requireNonNull(position, POSITION);

        byte[] board = position.getBoard();
        int[] mobility = mobilityCounts(position.getMoves());
        List<String> tags = new ArrayList<>();
        byte whiteKing = position.getWhiteKing();
        byte blackKing = position.getBlackKing();

        for (int index = 0; index < board.length; index++) {
            addTagsForPiece(tags, board, mobility, whiteKing, blackKing, (byte) index);
        }

        return List.copyOf(tags);
    }

    /**
     * Counts the number of legal moves originating from each square.
     *
     * @param moves the legal move list to inspect
     * @return an array of mobility counts indexed by source square
     */
    private static int[] mobilityCounts(MoveList moves) {
        int[] mobility = new int[64];
        for (int i = 0, size = moves.size(); i < size; i++) {
            mobility[Move.getFromIndex(moves.get(i))]++;
        }
        return mobility;
    }

    /**
     * Applies pinned, outpost, and mobility tags to one board square.
     *
     * @param tags the mutable tag accumulator
     * @param board the board array
     * @param mobility the mobility counts by source square
     * @param whiteKing the White king square
     * @param blackKing the Black king square
     * @param square the board square being inspected
     */
    private static void addTagsForPiece(List<String> tags, byte[] board, int[] mobility, byte whiteKing,
            byte blackKing, byte square) {
        byte piece = board[square];
        if (piece == Piece.EMPTY) {
            return;
        }
        boolean white = Piece.isWhite(piece);
        addPinnedTag(tags, board, whiteKing, blackKing, piece, white, square);
        addOutpostTag(tags, board, piece, white, square);
        addMobilityTag(tags, board, mobility[square], piece, white, square);
    }

    /**
     * Adds a pinned-piece tag when the piece is pinned to its king.
     *
     * @param tags the mutable tag accumulator
     * @param board the board array
     * @param whiteKing the White king square
     * @param blackKing the Black king square
     * @param piece the piece being inspected
     * @param white whether the piece belongs to White
     * @param square the piece square
     */
    private static void addPinnedTag(List<String> tags, byte[] board, byte whiteKing, byte blackKing, byte piece,
            boolean white, byte square) {
        if (!Piece.isKing(piece) && PinUtils.isPinnedToKing(board, white, white ? whiteKing : blackKing, square)) {
            tags.add(Text.colorNameLower(piece) + SPACE_TEXT + Text.pieceNameLower(piece) + SPACE_TEXT
                    + Text.squareNameLower(square) + PINNED_SUFFIX);
        }
    }

    /**
     * Adds an outpost tag for a knight or bishop that satisfies the outpost rules.
     *
     * @param tags the mutable tag accumulator
     * @param board the board array
     * @param piece the piece being inspected
     * @param white whether the piece belongs to White
     * @param square the piece square
     */
    private static void addOutpostTag(List<String> tags, byte[] board, byte piece, boolean white, byte square) {
        if ((Piece.isKnight(piece) || Piece.isBishop(piece)) && isOutpost(board, white, square)) {
            tags.add(OUTPOST_PREFIX + Text.colorNameLower(piece) + SPACE_TEXT + Text.pieceNameLower(piece) + SPACE_TEXT
                    + Text.squareNameLower(square));
        }
    }

    /**
     * Adds trapped, low-mobility, or high-mobility tags for a piece.
     *
     * @param tags the mutable tag accumulator
     * @param board the board array
     * @param moveCount the number of legal moves for the piece
     * @param piece the piece being inspected
     * @param white whether the piece belongs to White
     * @param square the piece square
     */
    private static void addMobilityTag(List<String> tags, byte[] board, int moveCount, byte piece, boolean white,
            byte square) {
        if (Piece.isPawn(piece) || Piece.isKing(piece)) {
            return;
        }
        if (moveCount == 0 && AttackUtils.countAttackers(board, !white, square) > 0) {
            tags.add(TRAPPED_PREFIX + Text.colorNameLower(piece) + SPACE_TEXT + Text.pieceNameLower(piece) + SPACE_TEXT
                    + Text.squareNameLower(square));
            return;
        }
        int lowThreshold = lowMobilityThreshold(piece);
        int highThreshold = highMobilityThreshold(piece);
        if (moveCount <= lowThreshold) {
            tags.add(LOW_MOBILITY_PREFIX + Text.colorNameLower(piece) + SPACE_TEXT + Text.pieceNameLower(piece)
                    + SPACE_TEXT
                    + Text.squareNameLower(square));
        } else if (moveCount >= highThreshold) {
            tags.add(HIGH_MOBILITY_PREFIX + Text.colorNameLower(piece) + SPACE_TEXT + Text.pieceNameLower(piece)
                    + SPACE_TEXT
                    + Text.squareNameLower(square));
        }
    }

    /**
     * Returns the low-mobility threshold for a piece.
     *
     * @param piece the piece being evaluated
     * @return the low-mobility move-count threshold
     */
    private static int lowMobilityThreshold(byte piece) {
        if (Piece.isQueen(piece)) {
            return 2;
        }
        if (Piece.isRook(piece)) {
            return 2;
        }
        if (Piece.isBishop(piece) || Piece.isKnight(piece)) {
            return 1;
        }
        return 0;
    }

    /**
     * Returns the high-mobility threshold for a piece.
     *
     * @param piece the piece being evaluated
     * @return the high-mobility move-count threshold
     */
    private static int highMobilityThreshold(byte piece) {
        if (Piece.isQueen(piece)) {
            return 10;
        }
        if (Piece.isRook(piece)) {
            return 7;
        }
        if (Piece.isBishop(piece) || Piece.isKnight(piece)) {
            return 5;
        }
        return Integer.MAX_VALUE;
    }

    /**
     * Checks whether a knight or bishop square qualifies as an outpost.
     *
     * @param board the board array
     * @param white whether the piece belongs to White
     * @param square the piece square
     * @return {@code true} when the square is a supported, unattacked outpost
     */
    private static boolean isOutpost(byte[] board, boolean white, byte square) {
        int rank = Field.getY(square);
        if (white && rank < 3) {
            return false;
        }
        if (!white && rank > 4) {
            return false;
        }
        if (!isPawnSupported(board, white, square)) {
            return false;
        }
        return !isPawnAttacked(board, !white, square);
    }

    /**
     * Checks whether a pawn is supported by a friendly pawn behind it.
     *
     * @param board the board array
     * @param white whether the pawn belongs to White
     * @param square the square to inspect
     * @return {@code true} when a friendly pawn supports the square
     */
    private static boolean isPawnSupported(byte[] board, boolean white, byte square) {
        int file = Field.getX(square);
        int rank = Field.getY(square);
        int supportRank = white ? rank - 1 : rank + 1;
        if (supportRank < 0 || supportRank > 7) {
            return false;
        }
        for (int df = -1; df <= 1; df += 2) {
            int f = file + df;
            if (!Field.isOnBoard(f, supportRank)) {
                continue;
            }
            byte piece = board[Field.toIndex(f, supportRank)];
            if (Piece.isPawn(piece) && Piece.isWhite(piece) == white) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether a pawn attack can hit the given square.
     *
     * @param board the board array
     * @param pawnIsWhite whether the attacking pawn belongs to White
     * @param square the square to inspect
     * @return {@code true} when a pawn on the attacking side can attack the square
     */
    private static boolean isPawnAttacked(byte[] board, boolean pawnIsWhite, byte square) {
        int file = Field.getX(square);
        int rank = Field.getY(square);
        int pawnRank = pawnIsWhite ? rank - 1 : rank + 1;
        if (pawnRank < 0 || pawnRank > 7) {
            return false;
        }
        for (int df = -1; df <= 1; df += 2) {
            int f = file + df;
            if (!Field.isOnBoard(f, pawnRank)) {
                continue;
            }
            byte piece = board[Field.toIndex(f, pawnRank)];
            if (Piece.isPawn(piece) && Piece.isWhite(piece) == pawnIsWhite) {
                return true;
            }
        }
        return false;
    }
}
