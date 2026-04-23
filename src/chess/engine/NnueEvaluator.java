package chess.engine;

import java.io.IOException;
import java.nio.file.Path;

import chess.core.Position;

/**
 * NNUE evaluator backed by {@link chess.nn.nnue.Model}.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class NnueEvaluator implements PositionEvaluator {

    /**
     * Loaded NNUE model.
     */
    private final chess.nn.nnue.Model model;

    /**
     * Creates an evaluator from a weights path.
     *
     * @param weights weights path, or null for the default/fallback model
     * @throws IOException if the model cannot be loaded
     */
    public NnueEvaluator(Path weights) throws IOException {
        this(weights == null ? chess.nn.nnue.Model.loadDefaultOrFallback() : chess.nn.nnue.Model.load(weights));
    }

    /**
     * Creates an evaluator from an already loaded model.
     *
     * @param model loaded model
     */
    public NnueEvaluator(chess.nn.nnue.Model model) {
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
     * @return label
     */
    @Override
    public String name() {
        return EvaluatorKind.NNUE.label() + "(" + model.backend() + ")";
    }

    /**
     * Releases model resources.
     */
    @Override
    public void close() {
        model.close();
    }
}
