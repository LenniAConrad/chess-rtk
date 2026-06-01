package application.gui.workbench.play;

import chess.core.Move;
import chess.core.Position;
import chess.core.Setup;
import chess.eco.Encyclopedia;
import chess.eco.Entry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Position-keyed opening book built from the ECO encyclopedia, used to give the
 * Play opponent sound, varied openings instead of computing the first moves with
 * a shallow search.
 *
 * <p>
 * The book is built once by replaying every {@link Encyclopedia} entry's parsed
 * move list from the standard start position and recording, for each position
 * reached (keyed by {@link Position#signatureCore()}), the next move played and
 * how often it occurs across all lines. {@link #move} returns a book move for a
 * position — the most frequent one when deterministic, otherwise a
 * frequency-weighted random pick — always re-validated as legal in the actual
 * position, or {@link Move#NO_MOVE} when the position is out of book. The book is
 * a pure function of the current position, so it composes cleanly with take-back
 * and never depends on game history.
 * </p>
 *
 * <p>
 * Construction is wrapped so a missing or corrupt book degrades to an empty
 * index (every lookup misses) rather than failing — Play still works without it.
 * </p>
 */
public final class OpeningBook {

    /**
     * Maximum plies into a line that are indexed. Beyond this the engine should
     * search; opening books rarely help past the first dozen moves and a cap
     * keeps the index small.
     */
    private static final int MAX_BOOK_PLY = 24;

    /**
     * Core position signature → candidate next moves with occurrence counts.
     */
    private final Map<Long, Map<Short, Integer>> index;

    /**
     * Builds the book from the default ECO encyclopedia.
     */
    public OpeningBook() {
        this.index = buildIndex();
    }

    /**
     * Returns a book move for a position, or {@link Move#NO_MOVE} when the
     * position is out of book or no candidate is currently legal.
     *
     * @param position position to move in
     * @param rng random source for weighted selection, or null for deterministic
     *        (most-frequent) selection
     * @return a legal book move, or {@link Move#NO_MOVE}
     */
    public short move(Position position, Random rng) {
        if (position == null) {
            return Move.NO_MOVE;
        }
        Map<Short, Integer> candidates = index.get(position.signatureCore());
        if (candidates == null || candidates.isEmpty()) {
            return Move.NO_MOVE;
        }
        if (rng == null) {
            return mostFrequentLegal(position, candidates);
        }
        return weightedLegal(position, candidates, rng);
    }

    /**
     * Returns whether the book holds any candidates for a position.
     *
     * @param position position to test
     * @return true when the position is in book
     */
    public boolean contains(Position position) {
        Map<Short, Integer> candidates = position == null ? null : index.get(position.signatureCore());
        return candidates != null && !candidates.isEmpty();
    }

    /**
     * Returns the number of indexed positions (for diagnostics/tests).
     *
     * @return indexed position count
     */
    public int size() {
        return index.size();
    }

    /**
     * Picks the most frequent candidate that is legal in the position, falling
     * back through the frequency order so a rare-but-legal move is still played
     * if the top choice somehow does not validate.
     *
     * @param position position to move in
     * @param candidates candidate move → count
     * @return a legal move, or {@link Move#NO_MOVE}
     */
    private static short mostFrequentLegal(Position position, Map<Short, Integer> candidates) {
        short best = Move.NO_MOVE;
        int bestCount = -1;
        for (Map.Entry<Short, Integer> e : candidates.entrySet()) {
            if (e.getValue() > bestCount && position.isLegalMove(e.getKey())) {
                bestCount = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    /**
     * Picks a frequency-weighted random legal candidate.
     *
     * @param position position to move in
     * @param candidates candidate move → count
     * @param rng random source
     * @return a legal move, or {@link Move#NO_MOVE}
     */
    private static short weightedLegal(Position position, Map<Short, Integer> candidates, Random rng) {
        List<Short> legal = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        long total = 0;
        for (Map.Entry<Short, Integer> e : candidates.entrySet()) {
            if (position.isLegalMove(e.getKey())) {
                legal.add(e.getKey());
                weights.add(e.getValue());
                total += e.getValue();
            }
        }
        if (legal.isEmpty()) {
            return Move.NO_MOVE;
        }
        long target = (long) (rng.nextDouble() * total);
        long running = 0;
        for (int i = 0; i < legal.size(); i++) {
            running += weights.get(i);
            if (target < running) {
                return legal.get(i);
            }
        }
        return legal.get(legal.size() - 1);
    }

    /**
     * Builds the signature → candidate-move index by replaying every ECO line.
     *
     * @return populated index, or an empty map on any failure
     */
    private static Map<Long, Map<Short, Integer>> buildIndex() {
        Map<Long, Map<Short, Integer>> map = new HashMap<>();
        try {
            for (Entry entry : Encyclopedia.defaultBook().entries()) {
                short[] moves = entry.getMoves();
                if (moves == null) {
                    continue;
                }
                Position cursor = Setup.getStandardStartPosition();
                for (int ply = 0; ply < moves.length && ply < MAX_BOOK_PLY; ply++) {
                    short move = moves[ply];
                    if (!cursor.isLegalMove(move)) {
                        break;
                    }
                    map.computeIfAbsent(cursor.signatureCore(), k -> new HashMap<>())
                            .merge(move, 1, Integer::sum);
                    cursor.play(move);
                }
            }
        } catch (RuntimeException ex) {
            return Map.of();
        }
        return map;
    }
}
