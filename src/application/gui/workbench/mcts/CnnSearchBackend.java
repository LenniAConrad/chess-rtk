package application.gui.workbench.mcts;

import chess.core.Position;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LC0 CNN policy/value backend for workbench MCTS.
 */
final class CnnSearchBackend implements SearchBackend {

    /**
     * Loaded CNN model.
     */
    private final chess.nn.lc0.cnn.Model model;

    /**
     * Small access-ordered prediction cache.
     */
    private final Map<Long, chess.nn.lc0.cnn.Network.Prediction> cache = newRecentPredictionCache();

    /**
     * Last predicted position signature.
     */
    private long lastKey = Long.MIN_VALUE;

    /**
     * Last prediction result.
     */
    private chess.nn.lc0.cnn.Network.Prediction lastPrediction;

    /**
     * Loads a CNN backend from weights.
     *
     * @param weights CNN weights path
     * @return loaded backend
     * @throws IOException if weights cannot be loaded
     */
    static CnnSearchBackend load(Path weights) throws IOException {
        return new CnnSearchBackend(chess.nn.lc0.cnn.Model.load(weights));
    }

    /**
     * Creates a CNN backend.
     *
     * @param model loaded model
     */
    private CnnSearchBackend(chess.nn.lc0.cnn.Model model) {
        this.model = model;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SearchEvaluation evaluate(Position position) {
        return SearchEvaluation.fromWdl(predict(position).wdl());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double[] priors(Position position, short[] moves, double[] fallback) {
        return PolicyPriors.fromLogits(
                position,
                moves,
                fallback,
                predict(position).policy(),
                chess.nn.lc0.cnn.PolicyEncoder::rawPolicyIndex);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String name() {
        return "cnn(" + model.backend() + ")";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        lastPrediction = null;
        cache.clear();
        model.close();
    }

    /**
     * Returns a cached CNN prediction for one position.
     *
     * @param position source position
     * @return prediction
     */
    private chess.nn.lc0.cnn.Network.Prediction predict(Position position) {
        long key = position.signature();
        if (lastPrediction != null && lastKey == key) {
            return lastPrediction;
        }
        chess.nn.lc0.cnn.Network.Prediction cached = cache.get(key);
        if (cached != null) {
            lastKey = key;
            lastPrediction = cached;
            return cached;
        }
        remember(key, model.predict(position));
        return lastPrediction;
    }

    /**
     * Stores a CNN prediction in the recent-position cache.
     *
     * @param key position signature
     * @param prediction prediction result
     */
    private void remember(long key, chess.nn.lc0.cnn.Network.Prediction prediction) {
        lastKey = key;
        lastPrediction = prediction;
        cache.put(key, prediction);
    }

    /**
     * Creates a small access-ordered prediction cache.
     *
     * @return bounded cache
     */
    private static Map<Long, chess.nn.lc0.cnn.Network.Prediction> newRecentPredictionCache() {
        return new LinkedHashMap<>(256, 0.75f, true) {
            /**
             * Serialization identifier for the bounded map implementation.
             */
            private static final long serialVersionUID = 1L;

            /**
             * {@inheritDoc}
             */
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, chess.nn.lc0.cnn.Network.Prediction> eldest) {
                return size() > 512;
            }
        };
    }
}
