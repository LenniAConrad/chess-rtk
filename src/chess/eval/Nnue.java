package chess.eval;

import java.io.IOException;
import java.nio.file.Path;

import chess.core.Position;

/**
 * NNUE centipawn evaluator backed by {@link chess.nn.nnue.Model}.
 *
 * <p>
 * A missing default model is tolerated when no explicit weights are requested:
 * the model wrapper supplies a tiny neutral fallback so the in-house engine can
 * still run smoke tests and deterministic CLI checks.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Nnue implements CentipawnEvaluator {

    /**
     * Loaded NNUE model used for all leaf evaluations.
     */
    private final chess.nn.nnue.Model model;

    /**
     * Creates an evaluator from a weights path.
     *
     * @param weights weights path, or null for the default/fallback model
     * @throws IOException if the model cannot be loaded
     */
    public Nnue(Path weights) throws IOException {
        this(weights == null ? chess.nn.nnue.Model.loadDefaultOrFallback() : chess.nn.nnue.Model.load(weights));
    }

    /**
     * Creates an evaluator from an already loaded model.
     *
     * @param model loaded model
     * @throws IllegalArgumentException if the model is null
     */
    public Nnue(chess.nn.nnue.Model model) {
        if (model == null) {
            throw new IllegalArgumentException("model == null");
        }
        this.model = model;
    }

    /**
     * Evaluates one position.
     *
     * @param position position to evaluate
     * @return NNUE centipawns from the side-to-move perspective
     */
    @Override
    public int evaluate(Position position) {
        return model.evaluateCentipawns(position);
    }

    /**
     * Returns the evaluator label.
     *
     * @return label including the active NNUE backend
     */
    @Override
    public String name() {
        return Kind.NNUE.label() + "(" + model.backend() + ")";
    }

    /**
     * Releases model resources.
     */
    @Override
    public void close() {
        model.close();
    }
}
