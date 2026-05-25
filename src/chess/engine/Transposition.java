package chess.engine;

import chess.core.Move;

/**
 * Direct-mapped search caches used by {@link AlphaBeta}.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
/**
 * Mutable transposition-table entry.
 *
 * <p>
 * Entries are reused in-place to avoid allocation during search. Each entry
 * stores one hashed position, the depth and bound type of its score, and the
 * best move used for future ordering.
 * </p>
 */
final class Transposition {

    /**
     * Zobrist-like position signature for the stored entry.
     */
    long key;

    /**
     * Remaining search depth used to produce the stored score.
     */
    int depth;

    /**
     * Stored alpha-beta score from the side-to-move perspective.
     */
    int score;

    /**
     * Bound flag identifying exact, lower-bound, or upper-bound semantics.
     */
    byte flag;

    /**
     * Best move from the stored node, reused as a move-ordering hint.
     */
    short bestMove;

    /**
     * Iterative-deepening generation in which this entry was written.
     */
    int generation;
}
