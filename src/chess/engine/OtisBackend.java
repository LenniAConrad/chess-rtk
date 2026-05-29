package chess.engine;

import chess.core.Position;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OTIS policy/WDL backend.
 */
final class OtisBackend implements SearchBackend {

    /**
     * Model.
     */
    final chess.nn.otis.Model model;

    /**
     * Prediction cache.
     */
    final Map<Long, chess.nn.otis.Model.Prediction> cache = MctsBackendSupport.newRecentPredictionCache();

    /**
     * Last key.
     */
    long lastKey = Long.MIN_VALUE;

    /**
     * Last prediction.
     */
    chess.nn.otis.Model.Prediction lastPrediction;

    /**
     * Creates backend.
     *
     * @param model model
     */
    OtisBackend(chess.nn.otis.Model model) {
        this.model = model;
    }

    /**
     * Evaluates one position with OTIS WDL output.
     *
     * @param position source position
     * @return search evaluation
     */
    @Override
    public Evaluation evaluate(Position position) {
        return Evaluation.fromWdl(predict(position).wdl());
    }

    /**
     * Evaluates a batch of positions and records their predictions.
     *
     * @param positions source positions
     * @return evaluations in input order
     */
    @Override
    public List<Evaluation> evaluateBatch(List<Position> positions) {
        List<Evaluation> out = new ArrayList<>(positions.size());
        for (Position position : positions) {
            chess.nn.otis.Model.Prediction prediction = model.predict(position);
            remember(position.signature(), prediction);
            out.add(Evaluation.fromWdl(prediction.wdl()));
        }
        return out;
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
        float[] logits = predict(position).policy();
        return MctsBackendSupport.policyPriorsFromLogits(
                position,
                moves,
                fallback,
                logits,
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
     * Predicts a position.
     *
     * @param position position
     * @return prediction
     */
    chess.nn.otis.Model.Prediction predict(Position position) {
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
     * Remembers a prediction.
     *
     * @param key key
     * @param prediction prediction
     */
    void remember(long key, chess.nn.otis.Model.Prediction prediction) {
        lastKey = key;
        lastPrediction = prediction;
        cache.put(key, prediction);
    }
}
