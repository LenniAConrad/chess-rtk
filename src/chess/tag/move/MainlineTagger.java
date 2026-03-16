package chess.tag.move;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import chess.core.Move;
import chess.core.MoveList;
import chess.core.Position;
import chess.core.SAN;
import chess.uci.Analysis;
import chess.uci.Output;

/**
 * Emits tags derived from the engine main line (PV) when available.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class MainlineTagger {

    /**
     * Maximum number of full moves to inspect for PV-based tags.
     */
    private static final int MAX_FULL_MOVES = 5;

    /**
     * Maximum number of plies to inspect for PV-based tags.
     */
    private static final int MAX_PLIES = MAX_FULL_MOVES * 2;

    /**
     * Utility class, prevents instantiation.
     */
    private MainlineTagger() {
        // utility
    }

    /**
     * Returns PV-related tags, including a PV sequence line and "enables" markers.
     *
     * @param position position to inspect
     * @param analysis engine analysis containing PVs (may be null)
     * @return immutable list of PV tags
     */
    public static List<String> tags(Position position, Analysis analysis) {
        Objects.requireNonNull(position, "position");

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
        return formatPly(enabler) + " enables ... " + formatPly(target);
    }

    private static String formatDisableTag(Ply disabler, Position previous, short disabledMove) {
        return formatPly(disabler) + " disables ... " + formatMove(previous, disabledMove);
    }

    private static String formatBestContinuation(List<Ply> plies) {
        StringBuilder builder = new StringBuilder(64);
        builder.append("best continuation: ");
        for (int i = 0; i < plies.size(); i++) {
            Ply ply = plies.get(i);
            if (ply.whiteToMove) {
                builder.append(ply.moveNumber).append(". ");
            }
            builder.append(ply.san);
            if (i + 1 < plies.size()) {
                builder.append(' ');
            }
        }
        return builder.toString();
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
     * Minimal PV ply metadata for formatting tags.
     */
    private static final class Ply {
        private final short move;
        private final String san;
        private final int moveNumber;
        private final boolean whiteToMove;

        private Ply(short move, String san, int moveNumber, boolean whiteToMove) {
            this.move = move;
            this.san = san;
            this.moveNumber = moveNumber;
            this.whiteToMove = whiteToMove;
        }
    }
}
