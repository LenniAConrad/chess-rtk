package chess.engine;


/**
 * Mutable static-evaluation cache entry.
 *
 * <p>
 * The cache is direct-mapped and stores only exact static evaluations, so an
 * entry needs only the position signature and the computed score.
 * </p>
 */
final class EvalEntry {

    /**
     * Position signature for the cached static evaluation.
     */
    long key;

    /**
     * Cached centipawn score from the side-to-move perspective.
     */
    int score;
}
