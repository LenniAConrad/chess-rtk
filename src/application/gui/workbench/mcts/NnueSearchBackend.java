package application.gui.workbench.mcts;

import chess.core.Position;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * NNUE value backend for workbench MCTS.
 */
final class NnueSearchBackend implements SearchBackend {

    /**
     * Loaded NNUE model.
     */
    private final chess.nn.nnue.Model model;

    /**
     * Loads an NNUE backend from weights.
     *
     * @param weights NNUE weights path
     * @return loaded backend
     * @throws IOException if weights cannot be loaded
     */
    static NnueSearchBackend load(Path weights) throws IOException {
        chess.nn.nnue.Model loaded = useDefaultFallback(weights)
                ? chess.nn.nnue.Model.loadDefaultOrFallback()
                : chess.nn.nnue.Model.load(weights);
        return new NnueSearchBackend(loaded);
    }

    /**
     * Creates an NNUE backend.
     *
     * @param model loaded model
     */
    private NnueSearchBackend(chess.nn.nnue.Model model) {
        this.model = model;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SearchEvaluation evaluate(Position position) {
        return SearchEvaluation.fromCentipawns(model.evaluateCentipawns(position));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String name() {
        return "nnue(" + model.backend() + ")";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        model.close();
    }

    /**
     * Returns whether missing weights should use the neutral default fallback.
     *
     * @param weights requested weights path
     * @return true when the default fallback should be used
     */
    private static boolean useDefaultFallback(Path weights) {
        return weights == null
                || chess.nn.nnue.Model.DEFAULT_WEIGHTS.equals(weights)
                && !Files.isRegularFile(weights);
    }
}
