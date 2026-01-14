package chess.eval;

/**
 * Identifies which evaluation backend produced a result.
 *
 * <p>
 * This is primarily used by {@link Result} and {@link Evaluator} so
 * callers can tell whether evaluation came from the LC0 neural network or the
 * built-in classical heuristics.
 * </p>
 *
 * @since 2025
 * @author Lennart A. Conrad
 */
public enum Backend {

    /**
     * LC0 network evaluated the position on the CPU backend.
     */
    LC0_CPU,

    /**
     * LC0 network evaluated the position on the CUDA backend.
     */
    LC0_CUDA,

    /**
     * LC0 network evaluated the position on the ROCm backend.
     */
    LC0_ROCM,

    /**
     * LC0 network evaluated the position on the oneAPI backend.
     */
    LC0_ONEAPI,

    /**
     * Classical heuristic evaluation was used (no neural network involved).
     */
    CLASSICAL
}
