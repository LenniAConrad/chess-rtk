package chess.engine;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Factory helpers for built-in evaluators.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class EvaluatorFactory {

    /**
     * Utility class; prevent instantiation.
     */
    private EvaluatorFactory() {
        // utility
    }

    /**
     * Creates an evaluator.
     *
     * @param kind evaluator kind
     * @param weights optional weights path for NNUE or LC0
     * @return evaluator
     * @throws IOException if model weights cannot be loaded
     */
    public static PositionEvaluator create(EvaluatorKind kind, Path weights) throws IOException {
        EvaluatorKind resolved = kind == null ? EvaluatorKind.CLASSICAL : kind;
        return switch (resolved) {
            case CLASSICAL -> new ClassicalEvaluator();
            case NNUE -> new NnueEvaluator(weights);
            case LC0 -> new Lc0Evaluator(weights);
        };
    }
}
