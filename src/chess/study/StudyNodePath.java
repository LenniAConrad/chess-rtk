package chess.study;

import chess.core.Move;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable UCI move path from a study chapter root.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class StudyNodePath {

    /**
     * Root path singleton.
     */
    private static final StudyNodePath ROOT = new StudyNodePath(List.of());

    /**
     * UCI moves from root to node.
     */
    private final List<String> uciMoves;

    /**
     * Creates a path from normalized UCI moves.
     *
     * @param uciMoves UCI moves
     */
    private StudyNodePath(List<String> uciMoves) {
        this.uciMoves = List.copyOf(uciMoves);
    }

    /**
     * Returns the root path.
     *
     * @return root path
     */
    public static StudyNodePath root() {
        return ROOT;
    }

    /**
     * Creates a path from a UCI move list.
     *
     * @param moves source moves
     * @return immutable path
     */
    public static StudyNodePath fromUciList(List<String> moves) {
        if (moves == null || moves.isEmpty()) {
            return root();
        }
        List<String> normalized = new ArrayList<>();
        for (String move : moves) {
            if (move == null || move.isBlank()) {
                continue;
            }
            normalized.add(normalize(move));
        }
        return normalized.isEmpty() ? root() : new StudyNodePath(normalized);
    }

    /**
     * Appends one UCI move.
     *
     * @param uci UCI move
     * @return extended path
     */
    public StudyNodePath append(String uci) {
        List<String> next = new ArrayList<>(uciMoves);
        next.add(normalize(uci));
        return new StudyNodePath(next);
    }

    /**
     * Appends one encoded move.
     *
     * @param move encoded move
     * @return extended path
     */
    public StudyNodePath append(short move) {
        return append(Move.toString(move));
    }

    /**
     * Returns immutable UCI moves.
     *
     * @return UCI moves
     */
    public List<String> toUciList() {
        return uciMoves;
    }

    /**
     * Returns path length in plies.
     *
     * @return move count
     */
    public int size() {
        return uciMoves.size();
    }

    /**
     * Returns whether this is the chapter root.
     *
     * @return true at root
     */
    public boolean isRoot() {
        return uciMoves.isEmpty();
    }

    /**
     * Returns the stable sidecar key.
     *
     * @return stable key
     */
    public String stableKey() {
        return uciMoves.isEmpty() ? "root" : String.join(" ", uciMoves);
    }

    /**
     * Returns the path text.
     *
     * @return stable key
     */
    @Override
    public String toString() {
        return stableKey();
    }

    /**
     * Compares paths.
     *
     * @param other other object
     * @return true when equal
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof StudyNodePath path && uciMoves.equals(path.uciMoves);
    }

    /**
     * Returns path hash.
     *
     * @return hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(uciMoves);
    }

    /**
     * Normalizes a UCI move through the shared move codec.
     *
     * @param uci source UCI
     * @return normalized UCI
     */
    private static String normalize(String uci) {
        return Move.toString(Move.parse(uci.trim()));
    }
}
