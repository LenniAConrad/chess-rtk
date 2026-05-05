package chess.tag.tactical;

import static chess.tag.core.Literals.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import chess.core.Field;
import chess.core.Piece;
import chess.core.Position;
import chess.tag.core.Text;

/**
 * Derives higher-level tactical motif tags from the current position.
 * <p>
 * The output includes pins, skewers, overloaded defenders, and hanging pieces.
 * </p>
 * @author Lennart A. Conrad
 * @since 2026
 */
public final class Motifs {

    /**
     * No-direction sentinel used when a sliding piece has no valid vectors.
     */
    private static final int[][] NO_DIRS = new int[0][];

    /**
     * Orthogonal directions for rook-like movement.
     */
    private static final int[][] ORTHO_DIRS = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

    /**
     * Diagonal directions for bishop-like movement.
     */
    private static final int[][] DIAG_DIRS = { { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 } };

    /**
     * Prevents instantiation of this utility class.
     */
    private Motifs() {
        // utility
    }

    /**
     * Returns tactical motif tags for the given position.
     *
     * @param position the position to inspect
     * @return an immutable list of tactical tags
     * @throws NullPointerException if {@code position} is {@code null}
     */
    public static List<String> tags(Position position) {
        Objects.requireNonNull(position, POSITION);

        byte[] board = position.getBoard();
        List<String> tags = new ArrayList<>();

        addPins(tags, position, board, true);
        addPins(tags, position, board, false);
        addSkewers(tags, board);
        addOverloadedDefenders(tags, position, board, true);
        addOverloadedDefenders(tags, position, board, false);
        addHangingPieces(tags, position, board);

        return List.copyOf(tags);
    }

    /**
     * Adds pin tags for one side.
     *
     * @param tags the mutable tag accumulator
     * @param position the analyzed position
     * @param board the board array
     * @param pinnedIsWhite whether the pinned side is White
     */
    private static void addPins(List<String> tags, Position position, byte[] board, boolean pinnedIsWhite) {
        for (int index = 0; index < board.length; index++) {
            String tag = pinTag(position, board, pinnedIsWhite, (byte) index);
            if (tag != null) {
                tags.add(tag);
            }
        }
    }

    /**
     * Builds a pin tag when the inspected piece is pinned to its king.
     *
     * @param position the analyzed position
     * @param board the board array
     * @param pinnedIsWhite whether the pinned side is White
     * @param square the candidate pinned square
     * @return the formatted pin tag, or {@code null} when no pin exists
     */
    private static String pinTag(Position position, byte[] board, boolean pinnedIsWhite, byte square) {
        byte piece = board[square];
        if (!isPinnedCandidate(piece, pinnedIsWhite)) {
            return null;
        }
        Position.PinInfo info = position.findPinToOwnKing(square);
        if (info == null) {
            return null;
        }
        return formatTactical(PIN_PREFIX + Text.colorNameLower(info.pinnerPiece) + SPACE_TEXT
                + Text.pieceNameLower(info.pinnerPiece) + SPACE_TEXT + Text.squareNameLower(info.pinnerSquare)
                + PINS + Text.colorNameLower(piece) + SPACE_TEXT + Text.pieceNameLower(piece) + SPACE_TEXT
                + Text.squareNameLower(info.pinnedSquare) + TO_KING);
    }

    /**
     * Checks whether a piece can be a pinned candidate.
     *
     * @param piece the piece to inspect
     * @param pinnedIsWhite whether the pinned side is White
     * @return {@code true} when the piece can be pinned
     */
    private static boolean isPinnedCandidate(byte piece, boolean pinnedIsWhite) {
        return piece != Piece.EMPTY && Piece.isWhite(piece) == pinnedIsWhite && !Piece.isKing(piece);
    }

    /**
     * Adds skewer tags for all sliding attackers.
     *
     * @param tags the mutable tag accumulator
     * @param board the board array
     */
    private static void addSkewers(List<String> tags, byte[] board) {
        for (int index = 0; index < board.length; index++) {
            byte piece = board[index];
            if (isSlidingAttacker(piece)) {
                addSkewersForPiece(tags, board, piece, (byte) index);
            }
        }
    }

    /**
     * Adds skewer checks for one sliding attacker.
     *
     * @param tags the mutable tag accumulator
     * @param board the board array
     * @param piece the attacker piece
     * @param from the attacker square
     */
    private static void addSkewersForPiece(List<String> tags, byte[] board, byte piece, byte from) {
        if (Piece.isQueen(piece)) {
            checkSkewers(tags, board, piece, from, ORTHO_DIRS);
            checkSkewers(tags, board, piece, from, DIAG_DIRS);
            return;
        }
        checkSkewers(tags, board, piece, from, slidingDirections(piece));
    }

    /**
     * Checks whether a piece is a sliding attacker.
     *
     * @param piece the piece to inspect
     * @return {@code true} when the piece is a rook, bishop, or queen
     */
    private static boolean isSlidingAttacker(byte piece) {
        return piece != Piece.EMPTY && (Piece.isRook(piece) || Piece.isBishop(piece) || Piece.isQueen(piece));
    }

    /**
     * Returns the sliding directions appropriate for a piece.
     *
     * @param piece the piece to inspect
     * @return the set of sliding directions for the piece
     */
    private static int[][] slidingDirections(byte piece) {
        if (Piece.isBishop(piece)) {
            return DIAG_DIRS;
        }
        if (Piece.isRook(piece)) {
            return ORTHO_DIRS;
        }
        return NO_DIRS;
    }

    /**
     * Checks all directions for a skewer from a given attacker.
     *
     * @param tags the mutable tag accumulator
     * @param board the board array
     * @param piece the attacker piece
     * @param from the attacker square
     * @param dirs the directions to inspect
     */
    private static void checkSkewers(List<String> tags, byte[] board, byte piece, byte from, int[][] dirs) {
        boolean white = Piece.isWhite(piece);
        for (int[] dir : dirs) {
            String tag = skewerTagInDirection(board, piece, from, white, dir);
            if (tag != null) {
                tags.add(tag);
            }
        }
    }

    /**
     * Builds a skewer tag when a valid skewer is found.
     *
     * @param board the board array
     * @param piece the attacking piece
     * @param from the attacker square
     * @param white whether the attacker is White
     * @param dir the direction being inspected
     * @return the formatted skewer tag, or {@code null} when no skewer exists
     */
    private static String skewerTagInDirection(byte[] board, byte piece, byte from, boolean white, int[] dir) {
        LineTarget first = firstOccupied(board, from, dir, 0);
        if (!isEnemyTarget(first, white)) {
            return null;
        }
        LineTarget second = firstOccupied(board, from, dir, 1);
        if (!isMajorSkewerFront(first.piece)
                || !isEnemyTarget(second, white) || !isValuableSkewerTarget(second.piece)
                || !isHigherValueTarget(first.piece, second.piece)) {
            return null;
        }
        return formatTactical(SKEWER_PREFIX + Text.colorNameLower(piece) + SPACE_TEXT + Text.pieceNameLower(piece)
                + SPACE_TEXT + Text.squareNameLower(from) + SKEWERS + Text.colorNameLower(first.piece) + SPACE_TEXT
                + Text.pieceNameLower(first.piece) + SPACE_TEXT + Text.squareNameLower((byte) first.index)
                + WITH + Text.colorNameLower(second.piece) + SPACE_TEXT + Text.pieceNameLower(second.piece)
                + SPACE_TEXT
                + Text.squareNameLower((byte) second.index) + BEHIND);
    }

    /**
     * Adds overloaded-defender tags for one side.
     *
     * @param tags the mutable tag accumulator
     * @param position the analyzed position
     * @param board the board array
     * @param white whether to inspect White or Black pieces
     */
    private static void addOverloadedDefenders(List<String> tags, Position position, byte[] board, boolean white) {
        Map<Byte, List<Byte>> overloaded = new HashMap<>();
        for (int index = 0; index < board.length; index++) {
            Byte defender = soleDefenderIfOverloaded(position, board, white, (byte) index);
            if (defender != null) {
                overloaded.computeIfAbsent(defender, k -> new ArrayList<>()).add((byte) index);
            }
        }
        for (Map.Entry<Byte, List<Byte>> entry : overloaded.entrySet()) {
            List<Byte> defended = entry.getValue();
            if (defended.size() < 2) {
                continue;
            }
            byte defenderSquare = entry.getKey();
            byte defenderPiece = board[defenderSquare];
            tags.add(formatTactical(OVERLOADED_DEFENDER_PREFIX + Text.colorNameLower(defenderPiece) + SPACE_TEXT
                    + Text.pieceNameLower(defenderPiece) + SPACE_TEXT + Text.squareNameLower(defenderSquare)
                    + DEFENDS + joinSquares(defended)));
        }
    }

    /**
     * Returns the sole defender square when a piece is defended by exactly one friendly piece.
     *
     * @param position the analyzed position
     * @param board the board array
     * @param white whether the inspected piece belongs to White
     * @param square the defended square
     * @return the sole defender square, or {@code null} if not overloaded
     */
    private static Byte soleDefenderIfOverloaded(Position position, byte[] board, boolean white, byte square) {
        byte piece = board[square];
        if (piece == Piece.EMPTY || Piece.isWhite(piece) != white || Piece.isKing(piece)) {
            return null;
        }
        if (countAttackers(position, !white, square) == 0) {
            return null;
        }
        byte[] defenders = getAttackers(position, white, square);
        return defenders.length == 1 ? defenders[0] : null;
    }

    /**
     * Joins defended squares into human-readable text.
     *
     * @param defended the defended squares to join
     * @return the joined square list
     */
    private static String joinSquares(List<Byte> defended) {
        StringBuilder detail = new StringBuilder();
        for (int i = 0; i < defended.size(); i++) {
            if (i > 0) {
                detail.append(i == defended.size() - 1 ? AND : COMMA_SPACE);
            }
            detail.append(Text.squareNameLower(defended.get(i)));
        }
        return detail.toString();
    }

    /**
     * Adds hanging-piece tags for all pieces on the board.
     *
     * @param tags the mutable tag accumulator
     * @param position the analyzed position
     * @param board the board array
     */
    private static void addHangingPieces(List<String> tags, Position position, byte[] board) {
        for (int index = 0; index < board.length; index++) {
            if (isHangingPiece(position, board, (byte) index)) {
                byte piece = board[index];
                tags.add(formatTactical(HANGING_PREFIX + Text.colorNameLower(piece) + SPACE_TEXT
                        + Text.pieceNameLower(piece) + SPACE_TEXT + Text.squareNameLower((byte) index)));
            }
        }
    }

    /**
     * Checks whether a piece is hanging.
     *
     * @param position the analyzed position
     * @param board the board array
     * @param square the square to inspect
     * @return {@code true} when the piece is attacked but not defended
     */
    private static boolean isHangingPiece(Position position, byte[] board, byte square) {
        byte piece = board[square];
        if (piece == Piece.EMPTY || Piece.isKing(piece) || Piece.isPawn(piece)) {
            return false;
        }
        boolean white = Piece.isWhite(piece);
        return countAttackers(position, !white, square) > 0
                && countAttackers(position, white, square) == 0;
    }

    /**
     * Counts attackers from one side using the core attack-query API.
     *
     * @param position the analyzed position
     * @param white whether to count White attackers or Black attackers
     * @param square the attacked square
     * @return the number of attackers from the requested side
     */
    private static int countAttackers(Position position, boolean white, byte square) {
        return white ? position.countAttackersByWhite(square) : position.countAttackersByBlack(square);
    }

    /**
     * Returns the attacker squares for one side using the core attack-query API.
     *
     * @param position the analyzed position
     * @param white whether to return White attackers or Black attackers
     * @param square the attacked square
     * @return the attacker squares from the requested side
     */
    private static byte[] getAttackers(Position position, boolean white, byte square) {
        return white ? position.getAttackersByWhite(square) : position.getAttackersByBlack(square);
    }

    /**
     * Returns the first occupied square in a direction, optionally skipping the first one.
     *
     * @param board the board array
     * @param from the starting square
     * @param dir the direction vector
     * @param skip how many occupied squares to skip before returning one
     * @return the located square, or {@code null} when the ray exits the board
     */
    private static LineTarget firstOccupied(byte[] board, byte from, int[] dir, int skip) {
        int x = Field.getX(from);
        int y = Field.getY(from);
        int seen = 0;
        while (true) {
            x += dir[0];
            y += dir[1];
            if (!Field.isOnBoard(x, y)) {
                return null;
            }
            int idx = Field.toIndex(x, y);
            if (board[idx] != Piece.EMPTY) {
                if (seen == skip) {
                    return new LineTarget(idx, board[idx]);
                }
                seen++;
            }
        }
    }

    /**
     * Checks whether a line target belongs to the enemy side.
     *
     * @param target the target to inspect
     * @param white whether the attacker is White
     * @return {@code true} when the target is an enemy piece
     */
    private static boolean isEnemyTarget(LineTarget target, boolean white) {
        return target != null && Piece.isWhite(target.piece) != white;
    }

    /**
     * Compares the material values of two pieces.
     *
     * @param firstPiece the first piece
     * @param secondPiece the second piece
     * @return {@code true} when the first piece is more valuable than the second
     */
    private static boolean isHigherValueTarget(byte firstPiece, byte secondPiece) {
        return tacticalValue(firstPiece) > tacticalValue(secondPiece);
    }

    /**
     * Returns a comparison value for line-tactic targets.
     *
     * @param piece the piece to inspect
     * @return material-like tactical value, with kings ranked highest
     */
    private static int tacticalValue(byte piece) {
        return Piece.isKing(piece) ? 10000 : Piece.getMaterialValue(piece);
    }

    /**
     * Checks whether a target behind a front piece is meaningful enough for a skewer.
     *
     * @param piece the target piece
     * @return true for kings and non-pawn material
     */
    private static boolean isValuableSkewerTarget(byte piece) {
        return Piece.isKing(piece) || Piece.isQueen(piece) || Piece.isRook(piece)
                || Piece.isBishop(piece) || Piece.isKnight(piece);
    }

    /**
     * Checks whether the front piece is important enough to be called a skewer.
     *
     * @param piece the front target
     * @return true for king, queen, and rook targets
     */
    private static boolean isMajorSkewerFront(byte piece) {
        return Piece.isKing(piece) || Piece.isQueen(piece) || Piece.isRook(piece);
    }

    /**
     * Wraps a tactical description in the canonical FACT payload format.
     *
     * @param text the tactical description text
     * @return the serialized tactical tag
     */
    private static String formatTactical(String text) {
        return FACT_PREFIX + TACTICAL + EQUAL_SIGN + QUOTE
                + text.replace(String.valueOf(QUOTE), String.valueOf(BACKSLASH) + QUOTE)
                + QUOTE;
    }

    /**
     * Holds one target discovered during ray scans.
 * @author Lennart A. Conrad
 * @since 2026
 */
    private static final class LineTarget {

        /**
         * The board index.
         */
        private final int index;

        /**
         * The piece on the square.
         */
        private final byte piece;

        /**
         * Creates a line target snapshot.
         *
         * @param index the board index
         * @param piece the piece on the square
         */
        private LineTarget(int index, byte piece) {
            this.index = index;
            this.piece = piece;
        }
    }
}
