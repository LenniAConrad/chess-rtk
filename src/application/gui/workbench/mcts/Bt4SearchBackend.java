package application.gui.workbench.mcts;

import chess.core.Position;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Optional BT4 policy/value backend for experiments with real network priors.
 * It is intentionally opt-in because CPU BT4 inference is expensive.
 */
final class Bt4SearchBackend implements SearchBackend {

    /**
     * BT4 policy/value network.
     */
    private final chess.nn.lc0.bt4.Network network;

    /**
     * Small access-ordered prediction cache.
     */
    private final Map<Long, Bt4Prediction> cache = newRecentPredictionCache();

    /**
     * Last predicted position signature.
     */
    private long lastKey = Long.MIN_VALUE;

    /**
     * Last prediction result.
     */
    private Bt4Prediction lastPrediction;

    /**
     * Loads a BT4 backend from weights.
     *
     * @param weights BT4 weights path
     * @return loaded backend
     * @throws IOException if weights cannot be loaded
     */
    static Bt4SearchBackend load(Path weights) throws IOException {
        return new Bt4SearchBackend(chess.nn.lc0.bt4.Network.load(weights));
    }

    /**
     * Creates a BT4 backend.
     *
     * @param network loaded BT4 network
     */
    private Bt4SearchBackend(chess.nn.lc0.bt4.Network network) {
        this.network = network;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SearchEvaluation evaluate(Position position) {
        return SearchEvaluation.fromWdl(predict(position).prediction().wdl());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double[] priors(Position position, short[] moves, double[] fallback) {
        Bt4Prediction prediction = predict(position);
        int transform = prediction.transform();
        return PolicyPriors.fromLogits(
                position,
                moves,
                fallback,
                prediction.prediction().policy(),
                (pos, move) -> chess.nn.lc0.bt4.PolicyEncoder.compressedPolicyIndex(pos, move, transform));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String name() {
        return "bt4(" + network.backend() + ")";
    }

    /**
     * Releases BT4 resources.
     */
    @Override
    public void close() {
        lastPrediction = null;
        cache.clear();
        network.close();
    }

    /**
     * Returns a cached BT4 prediction for one position.
     *
     * @param position source position
     * @return prediction plus canonical transform
     */
    private Bt4Prediction predict(Position position) {
        long key = position.signature();
        if (lastPrediction != null && lastKey == key) {
            return lastPrediction;
        }
        Bt4Prediction cached = cache.get(key);
        if (cached != null) {
            lastKey = key;
            lastPrediction = cached;
            return cached;
        }
        TransformSink sink = new TransformSink();
        chess.nn.lc0.bt4.Network.Prediction prediction = network.predict(position, sink);
        remember(key, new Bt4Prediction(prediction, sink.transform));
        return lastPrediction;
    }

    /**
     * Stores a BT4 prediction in the small recent-position cache.
     *
     * @param key position signature
     * @param prediction cached prediction
     */
    private void remember(long key, Bt4Prediction prediction) {
        lastKey = key;
        lastPrediction = prediction;
        cache.put(key, prediction);
    }

    /**
     * Creates a small access-ordered prediction cache.
     *
     * @return bounded cache
     */
    private static Map<Long, Bt4Prediction> newRecentPredictionCache() {
        return new LinkedHashMap<>(256, 0.75f, true) {
            /**
             * Serialization identifier for the bounded map implementation.
             */
            private static final long serialVersionUID = 1L;

            /**
             * Returns whether the oldest entry should be evicted.
             *
             * @param eldest eldest map entry
             * @return true when the cache is above capacity
             */
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, Bt4Prediction> eldest) {
                return size() > 512;
            }
        };
    }

    /**
     * Cached BT4 prediction plus canonical transform.
     *
     * @param prediction network prediction
     * @param transform canonical input transform
     */
    private record Bt4Prediction(chess.nn.lc0.bt4.Network.Prediction prediction, int transform) {
    }

    /**
     * Captures BT4 canonical transform from activation output.
     */
    private static final class TransformSink implements chess.nn.ActivationSink {

        /**
         * Captured transform id.
         */
        private int transform;

        /**
         * Receives activation tensors from the BT4 network.
         *
         * @param key tensor key
         * @param shape tensor shape
         * @param data tensor data
         */
        @Override
        public void put(String key, int[] shape, float[] data) {
            if ("bt4.input.transform".equals(key) && data != null && data.length > 0) {
                transform = Math.round(data[0]);
            }
        }
    }
}
