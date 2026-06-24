package chess.engine;

import java.util.LinkedHashMap;
import java.util.Map;

import chess.core.Position;

/**
 * Shared helpers for MCTS policy/value backends.
 */
final class MctsBackendSupport {

    /**
     * Utility class; prevent instantiation.
     */
    private MctsBackendSupport() {
        // utility
    }

    /**
     * Converts legal-move policy logits to normalized priors.
     * @param position chess position
     * @param moves candidate moves
     * @param fallback default used when input is absent or invalid
     * @param logits policy logits
     * @param indexer move-index encoder
     * @return converted legal-move policy logits to normalized priors
     */
    static double[] policyPriorsFromLogits(
            Position position,
            short[] moves,
            double[] fallback,
            float[] logits,
            PolicyIndex indexer) {
        if (position == null || moves == null || logits == null || indexer == null) {
            return fallback;
        }
        double[] out = new double[moves.length];
        int[] indices = new int[moves.length];
        float max = Float.NEGATIVE_INFINITY;
        int valid = 0;
        for (int i = 0; i < moves.length; i++) {
            int index = indexer.index(position, moves[i]);
            indices[i] = index;
            if (index >= 0 && index < logits.length && Float.isFinite(logits[index])) {
                max = Math.max(max, logits[index]);
                valid++;
            }
        }
        if (valid == 0 || !Float.isFinite(max)) {
            return fallback;
        }
        double sum = 0.0;
        for (int i = 0; i < moves.length; i++) {
            int index = indices[i];
            if (index >= 0 && index < logits.length && Float.isFinite(logits[index])) {
                out[i] = Math.exp(logits[index] - max);
                sum += out[i];
            }
        }
        if (!Double.isFinite(sum) || sum <= 0.0) {
            return fallback;
        }
        // Legal moves the policy head cannot represent (no encoder slot) would
        // otherwise get prior 0 and become effectively unsearchable under PUCT;
        // give each a small floor so MCTS can still reach them.
        if (valid < moves.length) {
            double floor = sum / valid * 0.05;
            for (int i = 0; i < moves.length; i++) {
                int index = indices[i];
                boolean mapped = index >= 0 && index < logits.length && Float.isFinite(logits[index]);
                if (!mapped) {
                    out[i] = floor;
                    sum += floor;
                }
            }
        }
        for (int i = 0; i < out.length; i++) {
            out[i] /= sum;
        }
        return out;
    }
    /**
     * Creates a small access-ordered prediction cache.
     * @param <T> cached prediction type
     * @return access-ordered prediction cache
     */
    static <T> Map<Long, T> newRecentPredictionCache() {
        return new LinkedHashMap<>(256, 0.75f, true) {
            /**
             * Serialization identifier for Swing compatibility.
             */
            private static final long serialVersionUID = 1L;

            /**
             * {@inheritDoc}
             */
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, T> eldest) {
                return size() > 512;
            }
        };
    }
}
