package chess.tag.tactical;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import chess.core.Field;
import chess.core.Piece;
import chess.core.Position;
import chess.tag.core.Text;

/**
 * Detects direct attack relationships that produce tactical motifs.
 * <p>
 * The tags include single-piece attacks and forks when one attacker threatens
 * multiple meaningful enemy targets.
 * </p>
 * @author Lennart A. Conrad
 * @since 2026
 */
public final class Attack {

    /**
     * Relative knight move offsets used for attack generation.
     */
    private static final int[][] KNIGHT_DELTAS = {
            { 1, 2 }, { 2, 1 }, { 2, -1 }, { 1, -2 },
            { -1, -2 }, { -2, -1 }, { -2, 1 }, { -1, 2 }
    };

    /**
     * Relative king move offsets used for attack generation.
     */
    private static final int[][] KING_DELTAS = {
            { 1, 0 }, { 1, 1 }, { 0, 1 }, { -1, 1 },
            { -1, 0 }, { -1, -1 }, { 0, -1 }, { 1, -1 }
    };

    /**
     * Sliding directions used for bishops.
     */
    private static final int[][] BISHOP_DIRECTIONS = { { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 } };

    /**
     * Sliding directions used for rooks.
     */
    private static final int[][] ROOK_DIRECTIONS = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

    /**
     * Prevents instantiation of this utility class.
     */
    private Attack() {
        // Utility class
    }

    /**
     * Returns tactical attack descriptions for all pieces on the board.
     *
     * @param position the position to inspect
     * @return an immutable list of tactical attack tags
     * @throws NullPointerException if {@code position} is {@code null}
     */
    public static List<String> tag(Position position) {
        Objects.requireNonNull(position, "position");

        byte[] board = position.getBoard();
        List<String> attacks = new ArrayList<>();

        for (int index = 0; index < board.length; index++) {
            byte piece = board[index];
            if (piece == Piece.EMPTY) {
                continue;
            }
            appendAttacksForPiece(board, attacks, (byte) index, piece);
        }

        return Collections.unmodifiableList(attacks);
    }

    /**
     * Collects all meaningful attacks for one attacker.
     *
     * @param board the board array
     * @param attacks the mutable attack list
     * @param attackerSquare the attacker square
     * @param attackerPiece the attacker piece
     */
    private static void appendAttacksForPiece(byte[] board, List<String> attacks, byte attackerSquare, byte attackerPiece) {
        boolean attackerIsWhite = Piece.isWhite(attackerPiece);
        List<Integer> targets = enumerateAttacks(board, attackerPiece, attackerSquare);
        List<AttackTarget> meaningfulTargets = new ArrayList<>(4);

        for (int i = 0; i < targets.size(); i++) {
            int targetSquare = targets.get(i);
            byte targetPiece = board[targetSquare];
            if (targetPiece == Piece.EMPTY || Piece.isWhite(targetPiece) == attackerIsWhite) {
                continue;
            }
            if (isMeaningfulAttack(board, attackerPiece, attackerSquare, (byte) targetSquare, targetPiece)) {
                meaningfulTargets.add(new AttackTarget((byte) targetSquare, targetPiece));
            }
        }

        if (meaningfulTargets.isEmpty()) {
            return;
        }
        if (meaningfulTargets.size() == 1) {
            AttackTarget target = meaningfulTargets.get(0);
            attacks.add(formatAttack(attackerSquare, attackerPiece, target.square, target.piece));
            return;
        }
        attacks.add(formatFork(attackerSquare, attackerPiece, meaningfulTargets));
    }

    /**
     * Enumerates all squares a piece attacks geometrically.
     *
     * @param board the board array
     * @param piece the attacker piece
     * @param square the attacker square
     * @return the list of target squares
     */
    private static List<Integer> enumerateAttacks(byte[] board, byte piece, byte square) {
        List<Integer> targets = new ArrayList<>();
        int file = Field.getX(square);
        int rank = Field.getY(square);
        int absPiece = Math.abs(piece);

        switch (absPiece) {
            case Piece.WHITE_PAWN: {
                int direction = Piece.isWhite(piece) ? 1 : -1;
                addIfValid(targets, file - 1, rank + direction);
                addIfValid(targets, file + 1, rank + direction);
                break;
            }
            case Piece.WHITE_KNIGHT: {
                for (int[] delta : KNIGHT_DELTAS) {
                    addIfValid(targets, file + delta[0], rank + delta[1]);
                }
                break;
            }
            case Piece.WHITE_BISHOP: {
                addSlidingTargets(targets, board, file, rank, BISHOP_DIRECTIONS);
                break;
            }
            case Piece.WHITE_ROOK: {
                addSlidingTargets(targets, board, file, rank, ROOK_DIRECTIONS);
                break;
            }
            case Piece.WHITE_QUEEN: {
                addSlidingTargets(targets, board, file, rank, BISHOP_DIRECTIONS);
                addSlidingTargets(targets, board, file, rank, ROOK_DIRECTIONS);
                break;
            }
            case Piece.WHITE_KING: {
                for (int[] delta : KING_DELTAS) {
                    addIfValid(targets, file + delta[0], rank + delta[1]);
                }
                break;
            }
            default:
                break;
        }

        return targets;
    }

    /**
     * Adds all squares reachable by a sliding piece in the given directions.
     *
     * @param targets the mutable target list
     * @param board the board array
     * @param file the source file
     * @param rank the source rank
     * @param directions the sliding directions to explore
     */
    private static void addSlidingTargets(List<Integer> targets, byte[] board, int file, int rank, int[][] directions) {
        for (int[] dir : directions) {
            int f = file + dir[0];
            int r = rank + dir[1];
            while (Field.isOnBoard(f, r)) {
                int index = Field.toIndex(f, r);
                targets.add(index);
                if (board[index] != Piece.EMPTY) {
                    break;
                }
                f += dir[0];
                r += dir[1];
            }
        }
    }

    /**
     * Adds a target square when it lies on the board.
     *
     * @param targets the mutable target list
     * @param file the target file
     * @param rank the target rank
     */
    private static void addIfValid(List<Integer> targets, int file, int rank) {
        if (Field.isOnBoard(file, rank)) {
            targets.add(Field.toIndex(file, rank));
        }
    }

    /**
     * Determines whether an attack is tactically meaningful.
     *
     * @param board the board array
     * @param attackerPiece the attacker piece
     * @param attackerSquare the attacker square
     * @param targetSquare the target square
     * @param targetPiece the target piece
     * @return {@code true} when the attack should be surfaced
     */
    private static boolean isMeaningfulAttack(byte[] board, byte attackerPiece, byte attackerSquare,
            byte targetSquare, byte targetPiece) {
        if (Piece.isKing(targetPiece)) {
            return true;
        }
        int attackerValue = Piece.getMaterialValue(attackerPiece);
        int targetValue = Piece.getMaterialValue(targetPiece);
        if (targetValue > attackerValue) {
            return true;
        }
        return !isDefended(board, targetSquare, Piece.isWhite(targetPiece), attackerSquare);
    }

    /**
     * Checks whether a target square is defended by its own side.
     *
     * @param board the board array
     * @param targetSquare the square being evaluated
     * @param targetIsWhite whether the target belongs to White
     * @param ignoreSquare a square to ignore when evaluating defenders
     * @return {@code true} when the square is defended
     */
    private static boolean isDefended(byte[] board, byte targetSquare, boolean targetIsWhite, byte ignoreSquare) {
        for (int index = 0; index < board.length; index++) {
            byte piece = board[index];
            if (isEligibleDefender(piece, index, targetIsWhite, targetSquare, ignoreSquare)) {
                List<Integer> attacks = enumerateAttacks(board, piece, (byte) index);
                for (int i = 0; i < attacks.size(); i++) {
                    if (attacks.get(i) == targetSquare) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Checks whether a piece can defend the target square.
     *
     * @param piece the candidate defender
     * @param index the defender square index
     * @param targetIsWhite whether the defended piece belongs to White
     * @param targetSquare the square being defended
     * @param ignoreSquare a square to exclude from consideration
     * @return {@code true} when the piece is a usable defender
     */
    private static boolean isEligibleDefender(byte piece, int index, boolean targetIsWhite, byte targetSquare,
            byte ignoreSquare) {
        return piece != Piece.EMPTY
                && Piece.isWhite(piece) == targetIsWhite
                && index != targetSquare
                && index != ignoreSquare;
    }

    /**
     * Formats a single attack description.
     *
     * @param attackerSquare the attacker square
     * @param attackerPiece the attacker piece
     * @param targetSquare the target square
     * @param targetPiece the target piece
     * @return the formatted attack tag
     */
    private static String formatAttack(byte attackerSquare, byte attackerPiece, byte targetSquare, byte targetPiece) {
        return Text.colorNameLower(attackerPiece) + " " + Text.squareNameLower(attackerSquare) + " "
                + Text.pieceNameLower(attackerPiece) + " attacks " + Text.colorNameLower(targetPiece) + " "
                + Text.squareNameLower(targetSquare) + " " + Text.pieceNameLower(targetPiece);
    }

    /**
     * Formats a fork description for multiple targets.
     *
     * @param attackerSquare the attacker square
     * @param attackerPiece the attacker piece
     * @param targets the attacked targets
     * @return the formatted fork tag
     */
    private static String formatFork(byte attackerSquare, byte attackerPiece, List<AttackTarget> targets) {
        StringBuilder builder = new StringBuilder();
        builder.append(Text.colorNameLower(attackerPiece)).append(" ").append(Text.squareNameLower(attackerSquare)).append(" ")
                .append(Text.pieceNameLower(attackerPiece)).append(" is forking ");
        for (int i = 0; i < targets.size(); i++) {
            AttackTarget target = targets.get(i);
            if (i > 0) {
                if (i == targets.size() - 1) {
                    builder.append(" and ");
                } else {
                    builder.append(", ");
                }
            }
            builder.append(Text.colorNameLower(target.piece)).append(" ").append(Text.squareNameLower(target.square)).append(" ")
                    .append(Text.pieceNameLower(target.piece));
        }
        return builder.toString();
    }

    /**
     * Represents one tactically meaningful attacked target.
 * @author Lennart A. Conrad
 * @since 2026
     */
    private static final class AttackTarget {

        /**
         * The target square.
         */
        private final byte square;

        /**
         * The target piece.
         */
        private final byte piece;

        /**
         * Creates an attacked-target record.
         *
         * @param square the target square
         * @param piece the target piece
         */
        private AttackTarget(byte square, byte piece) {
            this.square = square;
            this.piece = piece;
        }
    }
}
