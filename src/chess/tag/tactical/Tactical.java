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
 * The output includes pins, skewers, discovered attacks, overloaded defenders,
 * and hanging pieces.
 * </p>
 * @author Lennart A. Conrad
 * @since 2026
 */
public final class Tactical {

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
     * Knight move offsets used for pseudo-move checks.
     */
    private static final int[][] KNIGHT_DIRS = {
            { 1, 2 }, { 2, 1 }, { -1, 2 }, { -2, 1 },
            { 1, -2 }, { 2, -1 }, { -1, -2 }, { -2, -1 }
    };

    /**
     * King move offsets used for pseudo-move checks.
     */
    private static final int[][] KING_DIRS = {
            { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 },
            { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 }
    };

    /**
     * Prevents instantiation of this utility class.
     */
    private Tactical() {
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
        addDiscoveredAttacks(tags, board);
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
        LineTarget second = firstOccupied(board, from, dir, first.distance + 1);
        if (!isEnemyTarget(second, white) || !isHigherValueTarget(first.piece, second.piece)) {
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
     * Adds discovered-attack tags for all sliding attackers.
     *
     * @param tags the mutable tag accumulator
     * @param board the board array
     */
    private static void addDiscoveredAttacks(List<String> tags, byte[] board) {
        for (int index = 0; index < board.length; index++) {
            byte piece = board[index];
            if (isSlidingAttacker(piece)) {
                addDiscoveredAttacksForPiece(tags, board, piece, (byte) index);
            }
        }
    }

    /**
     * Adds discovered-attack checks for one sliding attacker.
     *
     * @param tags the mutable tag accumulator
     * @param board the board array
     * @param piece the slider piece
     * @param from the slider square
     */
    private static void addDiscoveredAttacksForPiece(List<String> tags, byte[] board, byte piece, byte from) {
        if (Piece.isQueen(piece)) {
            checkDiscovered(tags, board, piece, from, ORTHO_DIRS);
            checkDiscovered(tags, board, piece, from, DIAG_DIRS);
            return;
        }
        checkDiscovered(tags, board, piece, from, slidingDirections(piece));
    }

    /**
     * Checks all directions for discovered attacks from one slider.
     *
     * @param tags the mutable tag accumulator
     * @param board the board array
     * @param slider the slider piece
     * @param from the slider square
     * @param dirs the directions to inspect
     */
    private static void checkDiscovered(List<String> tags, byte[] board, byte slider, byte from, int[][] dirs) {
        boolean white = Piece.isWhite(slider);
        for (int[] dir : dirs) {
            String tag = discoveredAttackTagInDirection(board, slider, from, white, dir);
            if (tag != null) {
                tags.add(tag);
            }
        }
    }

    /**
     * Builds a discovered-attack tag when a valid sequence exists.
     *
     * @param board the board array
     * @param slider the slider piece
     * @param from the slider square
     * @param white whether the slider is White
     * @param dir the direction being inspected
     * @return the formatted discovered-attack tag, or {@code null} when no sequence exists
     */
    private static String discoveredAttackTagInDirection(byte[] board, byte slider, byte from, boolean white,
            int[] dir) {
        LineTarget blocker = firstOccupied(board, from, dir, 0);
        if (!isFriendlyTarget(blocker, white) || !hasPseudoMove(board, (byte) blocker.index, blocker.piece)) {
            return null;
        }
        LineTarget target = firstOccupied(board, from, dir, blocker.distance + 1);
        if (!isEnemyTarget(target, white)) {
            return null;
        }
        return formatTactical(DISCOVERED_ATTACK_PREFIX + MOVING + Text.colorNameLower(blocker.piece) + SPACE_TEXT
                + Text.pieceNameLower(blocker.piece) + SPACE_TEXT + Text.squareNameLower((byte) blocker.index)
                + REVEALS + Text.colorNameLower(slider) + SPACE_TEXT + Text.pieceNameLower(slider) + SPACE_TEXT
                + Text.squareNameLower(from) + ATTACK_ON + Text.colorNameLower(target.piece) + SPACE_TEXT
                + Text.pieceNameLower(target.piece) + SPACE_TEXT + Text.squareNameLower((byte) target.index));
    }

    /**
     * Checks whether a piece has at least one pseudo-legal move.
     *
     * @param board the board array
     * @param from the source square
     * @param piece the piece to inspect
     * @return {@code true} when the piece can move in principle
     */
    private static boolean hasPseudoMove(byte[] board, byte from, byte piece) {
        if (piece == Piece.EMPTY) {
            return false;
        }
        boolean white = Piece.isWhite(piece);
        if (Piece.isPawn(piece)) {
            return pawnHasMove(board, from, white);
        }
        if (Piece.isKnight(piece)) {
            return knightHasMove(board, from, white);
        }
        if (Piece.isKing(piece)) {
            return kingHasMove(board, from, white);
        }
        if (Piece.isBishop(piece)) {
            return sliderHasMove(board, from, white, DIAG_DIRS);
        }
        if (Piece.isRook(piece)) {
            return sliderHasMove(board, from, white, ORTHO_DIRS);
        }
        if (Piece.isQueen(piece)) {
            return sliderHasMove(board, from, white, ORTHO_DIRS) || sliderHasMove(board, from, white, DIAG_DIRS);
        }
        return false;
    }

    /**
     * Tests whether a pawn has any pseudo-legal move.
     *
     * @param board the board array
     * @param from the pawn square
     * @param white whether the pawn is White
     * @return {@code true} when the pawn can advance or capture
     */
    private static boolean pawnHasMove(byte[] board, byte from, boolean white) {
        int x = Field.getX(from);
        int y = Field.getY(from);
        int dir = white ? 1 : -1;
        return pawnCanAdvance(board, x, y, dir)
                || pawnCanCapture(board, x, y + dir, white)
                || pawnCanDoubleStep(board, x, y, dir, white);
    }

    /**
     * Tests whether a pawn can advance one square.
     *
     * @param board the board array
     * @param x the pawn file
     * @param y the pawn rank
     * @param dir the forward direction
     * @return {@code true} when the advance square is empty
     */
    private static boolean pawnCanAdvance(byte[] board, int x, int y, int dir) {
        int nextY = y + dir;
        return Field.isOnBoard(x, nextY) && board[Field.toIndex(x, nextY)] == Piece.EMPTY;
    }

    /**
     * Tests whether a pawn can capture diagonally.
     *
     * @param board the board array
     * @param x the pawn file
     * @param diagY the target rank
     * @param white whether the pawn is White
     * @return {@code true} when either diagonal contains an enemy piece
     */
    private static boolean pawnCanCapture(byte[] board, int x, int diagY, boolean white) {
        return pawnCanCaptureOn(board, x - 1, diagY, white) || pawnCanCaptureOn(board, x + 1, diagY, white);
    }

    /**
     * Tests whether a pawn can capture on a specific square.
     *
     * @param board the board array
     * @param x the target file
     * @param y the target rank
     * @param white whether the pawn is White
     * @return {@code true} when the square contains an enemy piece
     */
    private static boolean pawnCanCaptureOn(byte[] board, int x, int y, boolean white) {
        if (!Field.isOnBoard(x, y)) {
            return false;
        }
        byte target = board[Field.toIndex(x, y)];
        return target != Piece.EMPTY && Piece.isWhite(target) != white;
    }

    /**
     * Tests whether a pawn can double-step from its starting rank.
     *
     * @param board the board array
     * @param x the pawn file
     * @param y the pawn rank
     * @param dir the forward direction
     * @param white whether the pawn is White
     * @return {@code true} when the double-step path is clear
     */
    private static boolean pawnCanDoubleStep(byte[] board, int x, int y, int dir, boolean white) {
        int startRank = white ? 1 : 6;
        int twoY = y + (2 * dir);
        if (y != startRank || !Field.isOnBoard(x, twoY)) {
            return false;
        }
        int mid = Field.toIndex(x, y + dir);
        int end = Field.toIndex(x, twoY);
        return board[mid] == Piece.EMPTY && board[end] == Piece.EMPTY;
    }

    /**
     * Tests whether a knight has any pseudo-legal move.
     *
     * @param board the board array
     * @param from the knight square
     * @param white whether the knight is White
     * @return {@code true} when the knight can move
     */
    private static boolean knightHasMove(byte[] board, byte from, boolean white) {
        int fx = Field.getX(from);
        int fy = Field.getY(from);
        for (int[] jump : KNIGHT_DIRS) {
            int x = fx + jump[0];
            int y = fy + jump[1];
            if (!Field.isOnBoard(x, y)) {
                continue;
            }
            byte target = board[Field.toIndex(x, y)];
            if (target == Piece.EMPTY || Piece.isWhite(target) != white) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tests whether a king has any pseudo-legal move.
     *
     * @param board the board array
     * @param from the king square
     * @param white whether the king is White
     * @return {@code true} when the king can move
     */
    private static boolean kingHasMove(byte[] board, byte from, boolean white) {
        int fx = Field.getX(from);
        int fy = Field.getY(from);
        for (int[] dir : KING_DIRS) {
            int x = fx + dir[0];
            int y = fy + dir[1];
            if (!Field.isOnBoard(x, y)) {
                continue;
            }
            byte target = board[Field.toIndex(x, y)];
            if (target == Piece.EMPTY || Piece.isWhite(target) != white) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tests whether a sliding piece has any pseudo-legal move.
     *
     * @param board the board array
     * @param from the piece square
     * @param white whether the piece is White
     * @param dirs the movement directions to inspect
     * @return {@code true} when the piece can move along any direction
     */
    private static boolean sliderHasMove(byte[] board, byte from, boolean white, int[][] dirs) {
        int fx = Field.getX(from);
        int fy = Field.getY(from);
        for (int[] dir : dirs) {
            int x = fx + dir[0];
            int y = fy + dir[1];
            if (!Field.isOnBoard(x, y)) {
                continue;
            }
            int idx = Field.toIndex(x, y);
            byte target = board[idx];
            if (target == Piece.EMPTY) {
                return true;
            }
            if (Piece.isWhite(target) != white) {
                return true;
            }
        }
        return false;
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
        if (piece == Piece.EMPTY || Piece.isKing(piece)) {
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
        int step = 0;
        while (true) {
            step++;
            x += dir[0];
            y += dir[1];
            if (!Field.isOnBoard(x, y)) {
                return null;
            }
            int idx = Field.toIndex(x, y);
            if (board[idx] != Piece.EMPTY) {
                if (seen == skip) {
                    return new LineTarget(idx, board[idx], step);
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
     * Checks whether a line target belongs to the friendly side.
     *
     * @param target the target to inspect
     * @param white whether the attacker is White
     * @return {@code true} when the target is a friendly piece
     */
    private static boolean isFriendlyTarget(LineTarget target, boolean white) {
        return target != null && Piece.isWhite(target.piece) == white;
    }

    /**
     * Compares the material values of two pieces.
     *
     * @param firstPiece the first piece
     * @param secondPiece the second piece
     * @return {@code true} when the first piece is more valuable than the second
     */
    private static boolean isHigherValueTarget(byte firstPiece, byte secondPiece) {
        int firstValue = Piece.isKing(firstPiece) ? 10000 : Piece.getMaterialValue(firstPiece);
        int secondValue = Piece.getMaterialValue(secondPiece);
        return firstValue > secondValue;
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
         * The distance from the attacker.
         */
        private final int distance;

        /**
         * Creates a line target snapshot.
         *
         * @param index the board index
         * @param piece the piece on the square
         * @param distance the distance from the attacker
         */
        private LineTarget(int index, byte piece, int distance) {
            this.index = index;
            this.piece = piece;
            this.distance = distance;
        }
    }
}
