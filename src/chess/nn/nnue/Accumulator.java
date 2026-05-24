package chess.nn.nnue;

import java.util.Arrays;

import chess.core.Position;

/**
 * Mutable NNUE accumulator for both board perspectives.
 *
 * <p>
 * The accumulator stores the hidden feature-transformer sums for White and Black
 * perspectives. It can be rebuilt from a {@link Position}, or updated by adding
 * and removing sparse feature indices supplied by {@link FeatureEncoder}.
 * </p>
 */
public final class Accumulator {

    /**
     * Weight owner. Used by {@link Network} to reject accumulators from a different
     * network instance.
     */
    final Network.Weights weights;

    /**
     * White-perspective feature-transformer sums.
     */
    private final float[] white;

    /**
     * Black-perspective feature-transformer sums.
     */
    private final float[] black;

    /**
     * Creates an accumulator for the provided network weights.
     *
     * @param weights network weights
     */
    Accumulator(Network.Weights weights) {
        this.weights = weights;
        this.white = new float[weights.hiddenSize];
        this.black = new float[weights.hiddenSize];
        reset();
    }

    /**
     * Rebuilds both perspective accumulators from a position.
     *
     * @param position position to encode
     * @return this accumulator
     */
    public Accumulator refresh(Position position) {
        if (position == null) {
            throw new IllegalArgumentException("position == null");
        }
        reset();
        addFeatures(true, FeatureEncoder.activeFeatures(position, true));
        addFeatures(false, FeatureEncoder.activeFeatures(position, false));
        return this;
    }

    /**
     * Resets both perspectives to the feature-transformer bias.
     *
     * @return this accumulator
     */
    public Accumulator reset() {
        System.arraycopy(weights.featureBias, 0, white, 0, white.length);
        System.arraycopy(weights.featureBias, 0, black, 0, black.length);
        return this;
    }

    /**
     * Adds one sparse feature to a perspective accumulator.
     *
     * @param whitePerspective true for White's perspective, false for Black's
     * @param feature sparse feature index
     * @return this accumulator
     */
    public Accumulator addFeature(boolean whitePerspective, int feature) {
        weights.addFeature(values(whitePerspective), feature, 1.0f);
        return this;
    }

    /**
     * Removes one sparse feature from a perspective accumulator.
     *
     * @param whitePerspective true for White's perspective, false for Black's
     * @param feature sparse feature index
     * @return this accumulator
     */
    public Accumulator removeFeature(boolean whitePerspective, int feature) {
        weights.addFeature(values(whitePerspective), feature, -1.0f);
        return this;
    }

    /**
     * Replaces one sparse feature with another in a perspective accumulator.
     *
     * @param whitePerspective true for White's perspective, false for Black's
     * @param removeFeature feature to subtract
     * @param addFeature feature to add
     * @return this accumulator
     */
    public Accumulator replaceFeature(boolean whitePerspective, int removeFeature, int addFeature) {
        removeFeature(whitePerspective, removeFeature);
        addFeature(whitePerspective, addFeature);
        return this;
    }

    /**
     * Adds a batch of sparse features to one perspective.
     *
     * @param whitePerspective true for White's perspective, false for Black's
     * @param features sparse feature indices
     * @return this accumulator
     */
    public Accumulator addFeatures(boolean whitePerspective, int[] features) {
        if (features == null) {
            throw new IllegalArgumentException("features == null");
        }
        float[] target = values(whitePerspective);
        for (int feature : features) {
            weights.addFeature(target, feature, 1.0f);
        }
        return this;
    }

    /**
     * Returns a defensive copy of one perspective's hidden sums.
     *
     * @param whitePerspective true for White's perspective, false for Black's
     * @return hidden accumulator values
     */
    public float[] copyValues(boolean whitePerspective) {
        return Arrays.copyOf(values(whitePerspective), weights.hiddenSize);
    }

    /**
     * Returns the hidden size for this accumulator.
     *
     * @return hidden unit count
     */
    public int hiddenSize() {
        return weights.hiddenSize;
    }

    /**
     * Returns one mutable perspective buffer.
     *
     * @param whitePerspective true for White's perspective, false for Black's
     * @return accumulator buffer
     */
    float[] values(boolean whitePerspective) {
        return whitePerspective ? white : black;
    }
}
