package chess.eval;

import chess.classical.Wdl;

/**
 * Result of evaluating a chess position.
 *
 * <p>
 * All values are from the <strong>side-to-move</strong> perspective.
 * </p>
 *
 * <h4>Fields</h4>
 * <ul>
 * <li>{@link #backend()}: which backend produced the result</li>
 * <li>{@link #wdl()}: win/draw/loss triplet, scaled to sum to 1000</li>
 * <li>{@link #value()}: a compact scalar in [-1, +1] (roughly win minus loss)</li>
 * <li>{@link #centipawns()}: optional centipawn estimate; may be {@code null}
 * (not all backends naturally produce centipawns)</li>
 * </ul>
 *
 * @param backend backend used for this evaluation
 * @param wdl WDL triplet (scaled to sum to 1000)
 * @param value scalar value from the side-to-move perspective (typically in [-1,+1])
 * @param centipawns optional centipawn estimate (side-to-move perspective), or {@code null}
 *
 * @since 2025
 * @author Lennart A. Conrad
 */
public record Result(Backend backend, Wdl wdl, double value, Integer centipawns) {

    /**
     * Validates the evaluation result components.
     *
     * @param backend backend used for this evaluation
     * @param wdl WDL triplet (scaled to sum to 1000)
     * @param value scalar value from the side-to-move perspective (typically in [-1,+1])
     * @param centipawns optional centipawn estimate (side-to-move perspective), or {@code null}
     * @throws IllegalArgumentException if {@code backend} or {@code wdl} is {@code null},
     *                                  or {@code value} is not finite
     */
    public Result {
        if (backend == null) {
            throw new IllegalArgumentException("backend == null");
        }
        if (wdl == null) {
            throw new IllegalArgumentException("wdl == null");
        }
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("value must be finite");
        }
    }
}
