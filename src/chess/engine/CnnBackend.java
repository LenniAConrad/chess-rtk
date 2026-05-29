package chess.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import chess.core.Position;

/**
 * LC0 CNN policy/value backend.
 */
final class CnnBackend implements SearchBackend {
    /**
     * Model.
     */
    final chess.nn.lc0.cnn.Model model;
    /**
     * New recent prediction cache.
     * @return new recent prediction cache result
     */
    final Map<Long, chess.nn.lc0.cnn.Network.Prediction> cache = MctsBackendSupport.newRecentPredictionCache();
    /**
     * Last key.
     */
    long lastKey = Long.MIN_VALUE;
    /**
     * Last prediction.
     */
    chess.nn.lc0.cnn.Network.Prediction lastPrediction;

    /**
     * Cnn backend.
     * @param model model value
     */
    CnnBackend(chess.nn.lc0.cnn.Model model) {
        this.model = model;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Evaluation evaluate(Position position) {
        return Evaluation.fromWdl(predict(position).wdl());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Evaluation> evaluateBatch(List<Position> positions) {
        List<chess.nn.lc0.cnn.Network.Prediction> predictions = model.predictBatch(positions);
        List<Evaluation> out = new ArrayList<>(predictions.size());
        for (int i = 0; i < predictions.size(); i++) {
            Position position = positions.get(i);
            chess.nn.lc0.cnn.Network.Prediction prediction = predictions.get(i);
            remember(position.signature(), prediction);
            out.add(Evaluation.fromWdl(prediction.wdl()));
        }
        return out;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double[] priors(Position position, short[] moves, double[] fallback) {
        float[] logits = predict(position).policy();
        return MctsBackendSupport.policyPriorsFromLogits(
                position,
                moves,
                fallback,
                logits,
                chess.nn.lc0.cnn.PolicyEncoder::rawPolicyIndex);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String name() {
        return "lc0(" + model.backend() + ")";
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
     * Predict.
     * @param position chess position
     * @return predict result
     */
    chess.nn.lc0.cnn.Network.Prediction predict(Position position) {
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
     * Remember.
     * @param key lookup key
     * @param prediction prediction value
     */
    void remember(long key, chess.nn.lc0.cnn.Network.Prediction prediction) {
        lastKey = key;
        lastPrediction = prediction;
        cache.put(key, prediction);
    }
}
