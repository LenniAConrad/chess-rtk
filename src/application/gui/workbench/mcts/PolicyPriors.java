package application.gui.workbench.mcts;

import chess.core.Position;

/**
 * Converts network policy logits into normalized MCTS priors.
 */
final class PolicyPriors {

    /**
     * Prevents instantiation.
     */
    private PolicyPriors() {
        // utility
    }

    /**
     * Maps a legal move to one policy-logit index.
     */
    @FunctionalInterface
    interface Indexer {

        /**
         * Returns the policy index for a legal move.
         *
         * @param position source position
         * @param move legal move
         * @return policy index, or a negative value when unmapped
         */
        int index(Position position, short move);
    }

    /**
     * Converts legal-move logits to normalized priors.
     *
     * @param position source position
     * @param moves legal moves
     * @param fallback fallback priors
     * @param logits raw policy logits
     * @param indexer move-to-policy indexer
     * @return normalized policy priors
     */
    static double[] fromLogits(
            Position position,
            short[] moves,
            double[] fallback,
            float[] logits,
            Indexer indexer) {
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
        double sum = 0.0d;
        for (int i = 0; i < moves.length; i++) {
            int index = indices[i];
            if (index >= 0 && index < logits.length && Float.isFinite(logits[index])) {
                out[i] = Math.exp(logits[index] - max);
                sum += out[i];
            }
        }
        if (!Double.isFinite(sum) || sum <= 0.0d) {
            return fallback;
        }
        for (int i = 0; i < out.length; i++) {
            out[i] /= sum;
        }
        return out;
    }
}
