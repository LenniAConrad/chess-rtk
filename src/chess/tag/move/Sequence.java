package chess.tag.move;

import static chess.tag.core.Literals.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import chess.core.MoveInference;
import chess.core.MoveList;
import chess.core.Position;
import chess.core.SAN;

/**
 * Builds move-sequence tags for a list of consecutive positions.
 * <p>
 * The sequence tags describe the local line of play as well as enabling and
 * disabling relationships discovered inside each contiguous segment.
 * </p>
 * @author Lennart A. Conrad
 * @since 2026
 */
public final class Sequence {

    /**
     * The maximum number of full moves considered in a sequence segment.
     */
    private static final int MAX_FULL_MOVES = 5;

    /**
     * The maximum number of plies considered in a sequence segment.
     */
    private static final int MAX_PLIES = MAX_FULL_MOVES * 2;

    /**
     * Prevents instantiation of this utility class.
     */
    private Sequence() {
        // utility
    }

    /**
     * Builds per-position sequence tags for contiguous segments of positions.
     * <p>
     * Null positions split the input into separate segments. Each returned list
     * corresponds to the tag output for the matching input index.
     * </p>
     *
     * @param positions the ordered list of positions to inspect
     * @return an immutable list of per-position tag lists
     * @throws NullPointerException if {@code positions} is {@code null}
     */
    public static List<List<String>> tags(List<Position> positions) {
        Objects.requireNonNull(positions, POSITIONS);

        List<List<String>> tags = new ArrayList<>(positions.size());
        for (int i = 0; i < positions.size(); i++) {
            tags.add(new ArrayList<>());
        }

        int index = 0;
        while (index < positions.size()) {
            index = skipNullPositions(positions, index);
            if (index >= positions.size()) {
                break;
            }
            int start = index;
            index = advanceSegmentEnd(positions, index);
            int end = index - 1;
            if (end - start >= 1) {
                processSegment(positions, tags, start, end);
            }
        }

        List<List<String>> result = new ArrayList<>(tags.size());
        for (List<String> entry : tags) {
            if (entry.isEmpty()) {
                result.add(Collections.emptyList());
            } else {
                result.add(List.copyOf(entry));
            }
        }
        return List.copyOf(result);
    }

    /**
     * Skips null position entries until the next segment start.
     *
     * @param positions the position list to scan
     * @param index the current index
     * @return the next non-null index, or the list size if none remains
     */
    private static int skipNullPositions(List<Position> positions, int index) {
        while (index < positions.size() && positions.get(index) == null) {
            index++;
        }
        return index;
    }

    /**
     * Advances to the end index of the current non-null position segment.
     *
     * @param positions the position list to scan
     * @param index the current index
     * @return the first index after the contiguous non-null segment
     */
    private static int advanceSegmentEnd(List<Position> positions, int index) {
        while (index < positions.size() && positions.get(index) != null) {
            index++;
        }
        return index;
    }

    /**
     * Processes one contiguous segment of positions.
     *
     * @param positions the full input list
     * @param tags the per-position tag accumulator
     * @param start the first index of the segment
     * @param end the first index after the segment
     */
    private static void processSegment(List<Position> positions, List<List<String>> tags, int start, int end) {
        List<Ply> plies = new ArrayList<>(Math.max(0, end - start));
        int segmentStart = start;
        for (int i = start; i < end; i++) {
            Position from = positions.get(i);
            Position to = positions.get(i + 1);
            short move = MoveInference.uniqueMove(from, to);
            if (move == chess.core.Move.NO_MOVE) {
                processPlies(positions, tags, plies, segmentStart);
                plies.clear();
                segmentStart = i + 1;
                continue;
            }
            String san = SAN.toAlgebraic(from, move);
            plies.add(new Ply(move, san, from.fullMoveNumber(), from.isWhiteToMove(), i));
        }
        processPlies(positions, tags, plies, segmentStart);
    }

    /**
     * Examines a sequence of plies for enable and disable relationships.
     *
     * @param positions the full input list
     * @param tags the per-position tag accumulator
     * @param plies the plies collected for the segment
     * @param start the starting index of the segment in the source list
     */
    private static void processPlies(List<Position> positions, List<List<String>> tags, List<Ply> plies, int start) {
        if (plies.size() < 2) {
            return;
        }
        for (int j = 2; j < plies.size(); j++) {
            Ply target = plies.get(j);
            Position current = positions.get(start + j);
            if (!current.isLegalMove(target.move)) {
                continue;
            }
            int maxBack = Math.max(0, j - MAX_PLIES);
            for (int prev = j - 2; prev >= maxBack; prev -= 2) {
                Position prevSameSide = positions.get(start + prev);
                Position nextSameSide = positions.get(start + prev + 2);
                if (!prevSameSide.isLegalMove(target.move) && nextSameSide.isLegalMove(target.move)) {
                    Ply enabler = plies.get(prev);
                    tags.get(enabler.positionIndex + 1).add(formatEnableTag(enabler, target));
                    break;
                }
            }

            Ply disabler = plies.get(j - 2);
            Position prevSameSide = positions.get(start + j - 2);
            Position nextSameSide = positions.get(start + j);
            String disabled = findDisableTag(prevSameSide, nextSameSide, disabler);
            if (disabled != null) {
                tags.get(disabler.positionIndex + 1).add(disabled);
            }
        }
    }

    /**
     * Finds a move that becomes illegal after the disabler ply.
     *
     * @param previous the position before the disabler
     * @param current the position after the disabler
     * @param disabler the ply that caused the disable
     * @return the formatted disable tag, or {@code null} when none is found
     */
    private static String findDisableTag(Position previous, Position current, Ply disabler) {
        MoveList prevMoves = previous.legalMoves();
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
     * Checks whether a legal move qualifies as a disable candidate.
     *
     * @param move the move under consideration
     * @param disablerMove the move that caused the disable
     * @param disablerFrom the source square of the disabler move
     * @param current the position after the disabler
     * @return {@code true} when the move is plausibly disabled
     */
    private static boolean isDisabledMoveCandidate(short move, short disablerMove, byte disablerFrom, Position current) {
        return !chess.core.Move.equals(move, disablerMove)
                && chess.core.Move.getFromIndex(move) != disablerFrom
                && !current.isLegalMove(move);
    }

    /**
     * Formats an enable relationship tag.
     *
     * @param enabler the ply that enabled the later move
     * @param target the later target ply
     * @return the serialized enable tag
     */
    private static String formatEnableTag(Ply enabler, Ply target) {
        return MOVE_TAG_PREFIX + formatPly(enabler) + ENABLES_ELLIPSIS + formatPly(target);
    }

    /**
     * Formats a disable relationship tag.
     *
     * @param disabler the ply that caused the disable
     * @param previous the position before the disable
     * @param disabledMove the move that is no longer legal
     * @return the serialized disable tag
     */
    private static String formatDisableTag(Ply disabler, Position previous, short disabledMove) {
        return MOVE_TAG_PREFIX + formatPly(disabler) + DISABLES_ELLIPSIS + formatMove(previous, disabledMove);
    }

    /**
     * Formats the best continuation tag for a sequence of plies.
     *
     * @param plies the plies to render
     * @return the serialized best-continuation tag
     */
    private static String formatPly(Ply ply) {
        if (ply.whiteToMove) {
            return ply.moveNumber + DOT_SPACE + ply.san;
        }
        return ply.moveNumber + ELLIPSIS_SPACE + ply.san;
    }

    /**
     * Formats a move from a given position into move-number notation.
     *
     * @param position the position before the move
     * @param move the move to format
     * @return the formatted move string
     */
    private static String formatMove(Position position, short move) {
        String san = SAN.toAlgebraic(position, move);
        int moveNumber = position.fullMoveNumber();
        if (position.isWhiteToMove()) {
            return moveNumber + DOT_SPACE + san;
        }
        return moveNumber + ELLIPSIS_SPACE + san;
    }

    /**
     * Holds the parsed data for one ply in the line.
 * @author Lennart A. Conrad
 * @since 2026
 */
    private static final class Ply {

        /**
         * The encoded move.
         */
        private final short move;

        /**
         * The SAN representation of the move.
         */
        private final String san;

        /**
         * The move number associated with the ply.
         */
        private final int moveNumber;

        /**
         * Whether the ply belongs to White's turn.
         */
        private final boolean whiteToMove;

        /**
         * The source index of the ply in the original position list.
         */
        private final int positionIndex;

        /**
         * Creates a ply snapshot.
         *
         * @param move the encoded move
         * @param san the SAN string
         * @param moveNumber the move number
         * @param whiteToMove whether the ply belongs to White
         * @param positionIndex the source index in the original list
         */
        private Ply(short move, String san, int moveNumber, boolean whiteToMove, int positionIndex) {
            this.move = move;
            this.san = san;
            this.moveNumber = moveNumber;
            this.whiteToMove = whiteToMove;
            this.positionIndex = positionIndex;
        }
    }
}
