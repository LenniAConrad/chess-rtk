package chess.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import chess.core.Position;

/**
 * LC0 BT4 policy/value backend.
 */
final class Bt4Backend implements SearchBackend {
    /**
     * Network.
     */
    final chess.nn.lc0.bt4.Network network;
    /**
     * New recent prediction cache.
     * @return new recent prediction cache result
     */
    final Map<Long, Bt4Prediction> cache = MctsBackendSupport.newRecentPredictionCache();
    /**
     * Last key.
     */
    long lastKey = Long.MIN_VALUE;
    /**
     * Last prediction.
     */
    Bt4Prediction lastPrediction;

    /**
     * Bt4 backend.
     * @param network network value
     */
    Bt4Backend(chess.nn.lc0.bt4.Network network) {
        this.network = network;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Evaluation evaluate(Position position) {
        return Evaluation.fromWdl(predict(position).prediction().wdl());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Evaluation> evaluateBatch(List<Position> positions) {
        List<chess.nn.lc0.bt4.Network.TransformedPrediction> predictions =
                network.predictBatchWithTransforms(positions);
        List<Evaluation> out = new ArrayList<>(predictions.size());
        for (int i = 0; i < predictions.size(); i++) {
            chess.nn.lc0.bt4.Network.TransformedPrediction prediction = predictions.get(i);
            Bt4Prediction cached = new Bt4Prediction(prediction.prediction(), prediction.transform());
            remember(positions.get(i).signature(), cached);
            out.add(Evaluation.fromWdl(cached.prediction().wdl()));
        }
        return out;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double[] priors(Position position, short[] moves, double[] fallback) {
        Bt4Prediction cached = predict(position);
        float[] logits = cached.prediction().policy();
        int transform = cached.transform();
        return MctsBackendSupport.policyPriorsFromLogits(
                position,
                moves,
                fallback,
                logits,
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
     * {@inheritDoc}
     */
    @Override
    public void close() {
        lastPrediction = null;
        cache.clear();
        network.close();
    }

    /**
     * Predict.
     * @param position chess position
     * @return predict result
     */
    Bt4Prediction predict(Position position) {
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
     * Remember.
     * @param key lookup key
     * @param prediction prediction value
     */
    void remember(long key, Bt4Prediction prediction) {
        lastKey = key;
        lastPrediction = prediction;
        cache.put(key, prediction);
    }
}
