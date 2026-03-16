package chess.tag.move;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import chess.core.Move;
import chess.core.MoveList;
import chess.core.Position;
import chess.core.SAN;

/**
 * Emits tags derived from a sequence of positions (game line).
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class SequenceTagger {

    /**
     * Maximum number of full moves to look ahead when linking enable tags.
     */
    private static final int MAX_FULL_MOVES = 5;

    /**
     * Maximum number of plies to look ahead when linking enable tags.
     */
    private static final int MAX_PLIES = MAX_FULL_MOVES * 2;

    /**
     * Utility class, prevents instantiation.
     */
    private SequenceTagger() {
        // utility
    }

    /**
     * Returns per-position tags describing move transitions that enable/disable later moves.
     *
     * @param positions ordered list of positions; null entries break sequences
     * @return list of tag lists, one entry per input position
     */
    public static List<List<String>> tags(List<Position> positions) {
        Objects.requireNonNull(positions, "positions");

        List<List<String>> tags = new ArrayList<>(positions.size());
        for (int i = 0; i < positions.size(); i++) {
            tags.add(new ArrayList<>());
        }

        int index = 0;
        while (index < positions.size()) {
            while (index < positions.size() && positions.get(index) == null) {
                index++;
            }
            if (index >= positions.size()) {
                break;
            }
            int start = index;
            while (index < positions.size() && positions.get(index) != null) {
                index++;
            }
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

    private static void processSegment(List<Position> positions, List<List<String>> tags, int start, int end) {
        List<Ply> plies = new ArrayList<>(Math.max(0, end - start));
        int segmentStart = start;
        for (int i = start; i < end; i++) {
            Position from = positions.get(i);
            Position to = positions.get(i + 1);
            short move = inferMove(from, to);
            if (move == Move.NO_MOVE) {
                processPlies(positions, tags, plies, segmentStart);
                plies.clear();
                segmentStart = i + 1;
                continue;
            }
            String san = SAN.toAlgebraic(from, move);
            plies.add(new Ply(move, san, from.getFullMove(), from.isWhiteTurn(), i));
        }
        processPlies(positions, tags, plies, segmentStart);
    }

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

    private static short inferMove(Position from, Position to) {
        long target = to.signatureCore();
        MoveList moves = from.getMoves();
        short found = Move.NO_MOVE;
        for (int i = 0; i < moves.size(); i++) {
            short move = moves.get(i);
            Position candidate = from.copyOf().play(move);
            if (candidate.signatureCore() == target) {
                if (found != Move.NO_MOVE) {
                    return Move.NO_MOVE;
                }
                found = move;
            }
        }
        return found;
    }

    private static String findDisableTag(Position previous, Position current, Ply disabler) {
        MoveList prevMoves = previous.getMoves();
        byte disablerFrom = Move.getFromIndex(disabler.move);
        for (int i = 0; i < prevMoves.size(); i++) {
            short move = prevMoves.get(i);
            if (Move.equals(move, disabler.move)) {
                continue;
            }
            if (Move.getFromIndex(move) == disablerFrom) {
                continue;
            }
            if (current.isLegalMove(move)) {
                continue;
            }
            return formatDisableTag(disabler, previous, move);
        }
        return null;
    }

    private static String formatEnableTag(Ply enabler, Ply target) {
        return "move: " + formatPly(enabler) + " enables ... " + formatPly(target);
    }

    private static String formatDisableTag(Ply disabler, Position previous, short disabledMove) {
        return "move: " + formatPly(disabler) + " disables ... " + formatMove(previous, disabledMove);
    }

    private static String formatPly(Ply ply) {
        if (ply.whiteToMove) {
            return ply.moveNumber + ". " + ply.san;
        }
        return ply.moveNumber + "... " + ply.san;
    }

    private static String formatMove(Position position, short move) {
        String san = SAN.toAlgebraic(position, move);
        int moveNumber = position.getFullMove();
        if (position.isWhiteTurn()) {
            return moveNumber + ". " + san;
        }
        return moveNumber + "... " + san;
    }

    /**
     * Minimal line ply metadata for formatting tags.
     */
    private static final class Ply {
        private final short move;
        private final String san;
        private final int moveNumber;
        private final boolean whiteToMove;
        private final int positionIndex;

        private Ply(short move, String san, int moveNumber, boolean whiteToMove, int positionIndex) {
            this.move = move;
            this.san = san;
            this.moveNumber = moveNumber;
            this.whiteToMove = whiteToMove;
            this.positionIndex = positionIndex;
        }
    }
}
