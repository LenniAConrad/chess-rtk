package chess.tag.tactical;

import java.util.ArrayList;
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
     * Cached knight targets for each source square.
     */
    private static final byte[][] KNIGHT_TARGETS = Field.getJumps();

    /**
     * Cached king targets for each source square.
     */
    private static final byte[][] KING_TARGETS = Field.getNeighbors();

    /**
     * Cached White pawn capture targets for each source square.
     */
    private static final byte[][] WHITE_PAWN_TARGETS = Field.getPawnCaptureWhite();

    /**
     * Cached Black pawn capture targets for each source square.
     */
    private static final byte[][] BLACK_PAWN_TARGETS = Field.getPawnCaptureBlack();

    /**
     * Cached bishop/queen diagonal rays for each source square.
     */
    private static final byte[][][] DIAGONAL_RAYS = Field.getDiagonals();

    /**
     * Cached rook/queen orthogonal rays for each source square.
     */
    private static final byte[][][] LINE_RAYS = Field.getLines();

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
        List<String> attacks = new ArrayList<>(8);

        for (int index = 0; index < board.length; index++) {
            byte piece = board[index];
            if (piece == Piece.EMPTY) {
                continue;
            }
            appendAttacksForPiece(position, board, attacks, (byte) index, piece);
        }

        return List.copyOf(attacks);
    }

    /**
     * Collects all meaningful attacks for one attacker.
     *
     * @param position the analyzed position
     * @param board the board array
     * @param attacks the mutable attack list
     * @param attackerSquare the attacker square
     * @param attackerPiece the attacker piece
     */
    private static void appendAttacksForPiece(Position position, byte[] board, List<String> attacks, byte attackerSquare,
            byte attackerPiece) {
        List<AttackTarget> meaningfulTargets = new ArrayList<>(2);
        collectMeaningfulTargets(position, board, attackerSquare, attackerPiece, meaningfulTargets);

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
     * Collects all tactically meaningful enemy targets attacked by one piece.
     *
     * @param position the analyzed position
     * @param board the board array
     * @param attackerSquare the attacker square
     * @param attackerPiece the attacker piece
     * @param meaningfulTargets the mutable target list
     */
    private static void collectMeaningfulTargets(Position position, byte[] board, byte attackerSquare, byte attackerPiece,
            List<AttackTarget> meaningfulTargets) {
        if (Piece.isPawn(attackerPiece)) {
            byte[][] pawnTargets = Piece.isWhite(attackerPiece) ? WHITE_PAWN_TARGETS : BLACK_PAWN_TARGETS;
            collectStaticTargets(position, board, attackerSquare, attackerPiece, pawnTargets[attackerSquare],
                    meaningfulTargets);
            return;
        }

        if (Piece.isKnight(attackerPiece)) {
            collectStaticTargets(position, board, attackerSquare, attackerPiece, KNIGHT_TARGETS[attackerSquare],
                    meaningfulTargets);
            return;
        }

        if (Piece.isKing(attackerPiece)) {
            collectStaticTargets(position, board, attackerSquare, attackerPiece, KING_TARGETS[attackerSquare],
                    meaningfulTargets);
            return;
        }

        if (Piece.isBishop(attackerPiece) || Piece.isQueen(attackerPiece)) {
            collectSlidingTargets(position, board, attackerSquare, attackerPiece, DIAGONAL_RAYS[attackerSquare],
                    meaningfulTargets);
        }

        if (Piece.isRook(attackerPiece) || Piece.isQueen(attackerPiece)) {
            collectSlidingTargets(position, board, attackerSquare, attackerPiece, LINE_RAYS[attackerSquare],
                    meaningfulTargets);
        }
    }

    /**
     * Collects meaningful targets from a non-sliding attack set.
     *
     * @param position the analyzed position
     * @param board the board array
     * @param attackerSquare the attacker square
     * @param attackerPiece the attacker piece
     * @param targets the attacked squares to inspect
     * @param meaningfulTargets the mutable target list
     */
    private static void collectStaticTargets(Position position, byte[] board, byte attackerSquare, byte attackerPiece,
            byte[] targets, List<AttackTarget> meaningfulTargets) {
        for (byte targetSquare : targets) {
            maybeAddMeaningfulTarget(position, board, attackerSquare, attackerPiece, targetSquare, meaningfulTargets);
        }
    }

    /**
     * Collects meaningful targets from sliding rays.
     *
     * @param position the analyzed position
     * @param board the board array
     * @param attackerSquare the attacker square
     * @param attackerPiece the attacker piece
     * @param rays the directional rays to inspect
     * @param meaningfulTargets the mutable target list
     */
    private static void collectSlidingTargets(Position position, byte[] board, byte attackerSquare, byte attackerPiece,
            byte[][] rays, List<AttackTarget> meaningfulTargets) {
        for (byte[] ray : rays) {
            for (byte targetSquare : ray) {
                maybeAddMeaningfulTarget(position, board, attackerSquare, attackerPiece, targetSquare,
                        meaningfulTargets);
                if (board[targetSquare] != Piece.EMPTY) {
                    break;
                }
            }
        }
    }

    /**
     * Adds one target when it is an enemy piece and the attack is meaningful.
     *
     * @param position the analyzed position
     * @param board the board array
     * @param attackerSquare the attacker square
     * @param attackerPiece the attacker piece
     * @param targetSquare the target square
     * @param meaningfulTargets the mutable target list
     */
    private static void maybeAddMeaningfulTarget(Position position, byte[] board, byte attackerSquare, byte attackerPiece,
            byte targetSquare, List<AttackTarget> meaningfulTargets) {
        byte targetPiece = board[targetSquare];
        if (targetPiece == Piece.EMPTY || Piece.isWhite(targetPiece) == Piece.isWhite(attackerPiece)) {
            return;
        }
        if (isMeaningfulAttack(position, attackerPiece, attackerSquare, targetSquare, targetPiece)) {
            meaningfulTargets.add(new AttackTarget(targetSquare, targetPiece));
        }
    }

    /**
     * Determines whether an attack is tactically meaningful.
     *
     * @param position the analyzed position
     * @param attackerPiece the attacker piece
     * @param attackerSquare the attacker square
     * @param targetSquare the target square
     * @param targetPiece the target piece
     * @return {@code true} when the attack should be surfaced
     */
    private static boolean isMeaningfulAttack(Position position, byte attackerPiece, byte attackerSquare,
            byte targetSquare, byte targetPiece) {
        if (Piece.isKing(targetPiece)) {
            return true;
        }
        int attackerValue = Piece.getMaterialValue(attackerPiece);
        int targetValue = Piece.getMaterialValue(targetPiece);
        if (targetValue > attackerValue) {
            return true;
        }
        return !isDefended(position, targetSquare, Piece.isWhite(targetPiece), attackerSquare);
    }

    /**
     * Checks whether a target square is defended by its own side.
     *
     * @param position the analyzed position
     * @param targetSquare the square being evaluated
     * @param targetIsWhite whether the target belongs to White
     * @param ignoreSquare a square to ignore when evaluating defenders
     * @return {@code true} when the square is defended
     */
    private static boolean isDefended(Position position, byte targetSquare, boolean targetIsWhite, byte ignoreSquare) {
        byte[] defenders = targetIsWhite ? position.getAttackersByWhite(targetSquare)
                : position.getAttackersByBlack(targetSquare);
        for (byte defender : defenders) {
            if (defender != targetSquare && defender != ignoreSquare) {
                return true;
            }
        }
        return false;
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
