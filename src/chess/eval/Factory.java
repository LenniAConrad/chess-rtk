package chess.eval;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Factory helpers for in-process centipawn evaluators.
 *
 * <p>
 * The CLI uses this class to resolve evaluator flags into concrete evaluator
 * instances while keeping model-loading details out of command parsing code.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Factory {

    /**
     * Prevents construction because this class only contains static factory
     * methods.
     */
    private Factory() {
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
    public static CentipawnEvaluator create(Kind kind, Path weights) throws IOException {
        Kind resolved = kind == null ? Kind.CLASSICAL : kind;
        return switch (resolved) {
            case CLASSICAL -> new Classical();
            case NNUE -> new Nnue(weights);
            case LC0 -> new Lc0(weights);
        };
    }
}
