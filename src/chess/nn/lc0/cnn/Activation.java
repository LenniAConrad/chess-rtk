package chess.nn.lc0.cnn;



/**
 * Pure-Java CPU tensors and evaluator for {@link Network}.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
enum Activation {

    /**
     * Rectified linear unit.
     */
    RELU,

    /**
     * No activation (identity).
     */
    NONE
}
