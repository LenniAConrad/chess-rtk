package application.gui.workbench.mcts;

import chess.core.Position;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OTIS policy/WDL backend for workbench MCTS.
 */
final class OtisSearchBackend implements SearchBackend {

    /**
     * Loaded OTIS model.
     */
    private final chess.nn.otis.Model model;

    /**
     * Small access-ordered prediction cache.
     */
    private final Map<Long, chess.nn.otis.Model.Prediction> cache = newRecentPredictionCache();

    /**
     * Last predicted position signature.
     */
    private long lastKey = Long.MIN_VALUE;

    /**
     * Last prediction result.
     */
    private chess.nn.otis.Model.Prediction lastPrediction;

    /**
     * Loads an OTIS backend from weights.
     *
     * @param weights OTIS weights path
     * @return loaded backend
     * @throws IOException if weights cannot be loaded
     */
    static OtisSearchBackend load(Path weights) throws IOException {
        return new OtisSearchBackend(chess.nn.otis.Model.load(weights));
    }

    /**
     * Creates an OTIS backend.
     *
     * @param model loaded model
     */
    private OtisSearchBackend(chess.nn.otis.Model model) {
        this.model = model;
    }

    /**
     * Evaluates a position with OTIS WDL output.
     *
     * @param position source position
     * @return search evaluation
     */
    @Override
    public SearchEvaluation evaluate(Position position) {
        return SearchEvaluation.fromWdl(predict(position).wdl());
    }

    /**
     * Converts OTIS policy logits into legal-move priors.
     *
     * @param position source position
     * @param moves legal moves
     * @param fallback fallback priors
     * @return prior probabilities
     */
    @Override
    public double[] priors(Position position, short[] moves, double[] fallback) {
        return PolicyPriors.fromLogits(
                position,
                moves,
                fallback,
                predict(position).policy(),
                chess.nn.lc0.bt4.PolicyEncoder::compressedPolicyIndex);
    }

    /**
     * Returns the backend display name.
     *
     * @return backend name
     */
    @Override
    public String name() {
        return "otis(" + model.backend() + ", "
                + chess.nn.otis.Model.formatParameterCount(model.info().parameterCount()) + " params)";
    }

    /**
     * Releases model resources and cached predictions.
     */
    @Override
    public void close() {
        lastPrediction = null;
        cache.clear();
        model.close();
    }

    /**
     * Returns a cached OTIS prediction.
     *
     * @param position source position
     * @return prediction
     */
    private chess.nn.otis.Model.Prediction predict(Position position) {
        long key = position.signature();
        if (lastPrediction != null && lastKey == key) {
            return lastPrediction;
        }
        chess.nn.otis.Model.Prediction cached = cache.get(key);
        if (cached != null) {
            lastKey = key;
            lastPrediction = cached;
            return cached;
        }
        remember(key, model.predict(position));
        return lastPrediction;
    }

    /**
     * Stores a prediction in the recent-position cache.
     *
     * @param key position signature
     * @param prediction prediction
     */
    private void remember(long key, chess.nn.otis.Model.Prediction prediction) {
        lastKey = key;
        lastPrediction = prediction;
        cache.put(key, prediction);
    }

    /**
     * Creates a small access-ordered prediction cache.
     *
     * @return bounded cache
     */
    private static Map<Long, chess.nn.otis.Model.Prediction> newRecentPredictionCache() {
        return new LinkedHashMap<>(256, 0.75f, true) {
            /**
             * Serialization identifier for the bounded map implementation.
             */
            private static final long serialVersionUID = 1L;

            /**
             * Evicts the oldest cache entry after the bounded size is exceeded.
             *
             * @param eldest eldest entry
             * @return true when the eldest entry should be removed
             */
            @Override
            protected boolean removeEldestEntry(
                    Map.Entry<Long, chess.nn.otis.Model.Prediction> eldest) {
                return size() > 512;
            }
        };
    }
}
