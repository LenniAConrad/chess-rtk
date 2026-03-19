package chess.tag.move;

import static chess.tag.core.Literals.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import chess.core.MoveList;
import chess.core.Position;
import chess.core.SAN;
import chess.uci.Analysis;
import chess.uci.Output;

/**
 * Produces tags that describe the main continuation from engine analysis.
 * <p>
 * The output includes the best continuation line and additional tags showing
 * enabling and disabling relationships between plies in the sequence.
 * </p>
 * @author Lennart A. Conrad
 * @since 2026
 */
public final class Mainline {

    /**
     * The maximum number of full moves included in the main line output.
     */
    private static final int MAX_FULL_MOVES = 5;

    /**
     * The maximum number of plies included in the main line output.
     */
    private static final int MAX_PLIES = MAX_FULL_MOVES * 2;

    /**
     * Prevents instantiation of this utility class.
     */
    private Mainline() {
        // utility
    }

    /**
     * Builds mainline tags from the best engine analysis output.
     * <p>
     * If no usable analysis or legal continuation exists, the method returns an
     * empty list.
     * </p>
     *
     * @param position the starting position for the main line
     * @param analysis optional engine analysis containing the continuation
     * @return an immutable list of mainline tags
     * @throws NullPointerException if {@code position} is {@code null}
     */
    public static List<String> tags(Position position, Analysis analysis) {
        Objects.requireNonNull(position, POSITION);

        if (analysis == null) {
            return Collections.emptyList();
        }
        Output output = analysis.getBestOutput();
        if (output == null) {
            return Collections.emptyList();
        }
        short[] moves = output.getMoves();
        if (moves == null || moves.length == 0) {
            return Collections.emptyList();
        }

        int maxPlies = Math.min(moves.length, MAX_PLIES);
        List<Ply> plies = new ArrayList<>(maxPlies);
        List<Position> positions = new ArrayList<>(maxPlies + 1);
        Position current = position.copyOf();
        positions.add(current.copyOf());

        for (int i = 0; i < maxPlies; i++) {
            short move = moves[i];
            if (!current.isLegalMove(move)) {
                break;
            }
            String san = SAN.toAlgebraic(current, move);
            plies.add(new Ply(move, san, current.getFullMove(), current.isWhiteTurn()));
            current.play(move);
            positions.add(current.copyOf());
        }

        if (plies.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> tags = new ArrayList<>();
        tags.add(formatBestContinuation(plies));
        tags.addAll(findMainlineTags(positions, plies));
        return List.copyOf(tags);
    }

    /**
     * Searches the continuation for enabling and disabling relationships.
     *
     * @param positions the positions corresponding to each ply boundary
     * @param plies the parsed ply sequence
     * @return the list of relationship tags discovered in the line
     */
    private static List<String> findMainlineTags(List<Position> positions, List<Ply> plies) {
        List<String> tags = new ArrayList<>();
        for (int j = 2; j < plies.size(); j++) {
            Ply target = plies.get(j);
            Position current = positions.get(j);
            if (!current.isLegalMove(target.move)) {
                continue;
            }
            Position previousSameSide = positions.get(j - 2);
            if (!previousSameSide.isLegalMove(target.move)) {
                Ply enabler = plies.get(j - 2);
                tags.add(formatEnableTag(enabler, target));
            }
            String disabled = findDisableTag(previousSameSide, current, plies.get(j - 2));
            if (disabled != null) {
                tags.add(disabled);
            }
        }
        return tags;
    }

    /**
     * Finds a move that becomes disabled after the current ply.
     *
     * @param previous the position before the disabler move
     * @param current the position after the disabler move
     * @param disabler the ply that disables the move
     * @return the formatted disable tag, or {@code null} when no clear disable was found
     */
    private static String findDisableTag(Position previous, Position current, Ply disabler) {
        MoveList prevMoves = previous.getMoves();
        byte disablerFrom = chess.core.Move.getFromIndex(disabler.move);
        for (int i = 0; i < prevMoves.size(); i++) {
            short move = prevMoves.get(i);
            if (isDisabledMoveCandidate(move, disabler.move, disablerFrom, current)) {
                return formatDisableTag(disabler, previous, move);
            }
        }
        return null;
    }

    /**
     * Checks whether a previous legal move is a valid disable candidate.
     *
     * @param move the move under consideration
     * @param disablerMove the move that caused the disable
     * @param disablerFrom the source square of the disabler
     * @param current the position after the disabler move
     * @return {@code true} when the move appears to be disabled
     */
    private static boolean isDisabledMoveCandidate(short move, short disablerMove, byte disablerFrom, Position current) {
        return !chess.core.Move.equals(move, disablerMove)
                && chess.core.Move.getFromIndex(move) != disablerFrom
                && !current.isLegalMove(move);
    }

    /**
     * Formats an enabling relationship between two plies.
     *
     * @param enabler the ply that enables a later move
     * @param target the later ply that becomes possible
     * @return the serialized enable tag
     */
    private static String formatEnableTag(Ply enabler, Ply target) {
        return formatPly(enabler) + ENABLES_ELLIPSIS + formatPly(target);
    }

    /**
     * Formats a disabling relationship between a ply and a previously legal move.
     *
     * @param disabler the ply that causes the disable
     * @param previous the position before the disable
     * @param disabledMove the move that is no longer legal
     * @return the serialized disable tag
     */
    private static String formatDisableTag(Ply disabler, Position previous, short disabledMove) {
        return formatPly(disabler) + DISABLES_ELLIPSIS + formatMove(previous, disabledMove);
    }

    /**
     * Formats the best continuation line as a single tag string.
     *
     * @param plies the plies to format
     * @return the serialized best-continuation tag
     */
    private static String formatBestContinuation(List<Ply> plies) {
        StringBuilder builder = new StringBuilder(64);
        builder.append(BEST_CONTINUATION_PREFIX);
        for (int i = 0; i < plies.size(); i++) {
            Ply ply = plies.get(i);
            if (ply.whiteToMove) {
                builder.append(ply.moveNumber).append(DOT_SPACE);
            }
            builder.append(ply.san);
            if (i + 1 < plies.size()) {
                builder.append(SPACE_CHAR);
            }
        }
        return builder.toString();
    }

    /**
     * Formats one ply for display in enable/disable tags.
     *
     * @param ply the ply to format
     * @return the formatted ply text
     */
    private static String formatPly(Ply ply) {
        if (ply.whiteToMove) {
            return ply.moveNumber + DOT_SPACE + ply.san;
        }
        return ply.moveNumber + ELLIPSIS_SPACE + ply.san;
    }

    /**
     * Formats a move from a position in move-number notation.
     *
     * @param position the position before the move
     * @param move the move to format
     * @return the formatted move text
     */
    private static String formatMove(Position position, short move) {
        String san = SAN.toAlgebraic(position, move);
        int moveNumber = position.getFullMove();
        if (position.isWhiteTurn()) {
            return moveNumber + DOT_SPACE + san;
        }
        return moveNumber + ELLIPSIS_SPACE + san;
    }

    /**
     * Holds the SAN and turn context for a single ply in the line.
 * @author Lennart A. Conrad
 * @since 2026
     */
    private static final class Ply {

        /**
         * The encoded move.
         */
        private final short move;

        /**
         * The SAN string for the move.
         */
        private final String san;

        /**
         * The move number at which the ply occurs.
         */
        private final int moveNumber;

        /**
         * Whether the ply belongs to White's turn.
         */
        private final boolean whiteToMove;

        /**
         * Creates a ply snapshot.
         *
         * @param move the encoded move
         * @param san the SAN string
         * @param moveNumber the move number
         * @param whiteToMove whether the ply belongs to White
         */
        private Ply(short move, String san, int moveNumber, boolean whiteToMove) {
            this.move = move;
            this.san = san;
            this.moveNumber = moveNumber;
            this.whiteToMove = whiteToMove;
        }
    }
}
