package chess.nn.nnue;

import static chess.nn.nnue.UpstreamNetwork.*;

import java.io.IOException;
import java.util.Arrays;

import chess.core.Position;
import utility.Numbers;

/**
 * Feature-transformer weights.
 */
final class FeatureTransformer {

    /**
     * Layout.
     */
    final Layout layout;

    /**
     * Transformed feature dimensions.
     */
    final int transformedDimensions;

    /**
     * Biases.
     */
    final short[] biases;

    /**
     * PSQ feature weights, feature-major.
     */
    final short[] psqWeights;

    /**
     * Threat feature weights, feature-major.
     */
    final byte[] threatWeights;

    /**
     * PSQ PSQT weights.
     */
    final int[] psqtWeights;

    /**
     * Threat PSQT weights.
     */
    final int[] threatPsqtWeights;

    /**
     * Creates a feature transformer.
     *
     * @param layout layout
     * @param biases biases
     * @param psqWeights PSQ weights
     * @param threatWeights threat weights
     * @param psqtWeights PSQT weights
     * @param threatPsqtWeights threat PSQT weights
     */
    FeatureTransformer(
            Layout layout,
            short[] biases,
            short[] psqWeights,
            byte[] threatWeights,
            int[] psqtWeights,
            int[] threatPsqtWeights) {
        this.layout = layout;
        this.transformedDimensions = layout.transformedDimensions;
        this.biases = biases;
        this.psqWeights = psqWeights;
        this.threatWeights = threatWeights;
        this.psqtWeights = psqtWeights;
        this.threatPsqtWeights = threatPsqtWeights;
        validate();
    }

    /**
     * Reads feature-transformer weights.
     *
     * @param cursor source cursor
     * @param layout layout
     * @return feature transformer
     * @throws IOException if parsing fails
     */
    static FeatureTransformer read(Cursor cursor, Layout layout) throws IOException {
        short[] biases = cursor.readLebShortArray(layout.transformedDimensions);
        byte[] threatWeights = new byte[0];
        short[] psqWeights;
        int[] psqtWeights;
        int[] threatPsqtWeights = new int[0];

        if (layout.useThreats) {
            threatWeights = cursor.readByteArray((long) layout.threatDimensions() * layout.transformedDimensions);
            psqWeights = cursor.readLebShortArray((long) layout.psqDimensions() * layout.transformedDimensions);
            int[] combinedPsqt = cursor.readLebIntArray((long) layout.totalInputDimensions() * LAYER_STACKS);
            int threatPsqtLength = layout.threatDimensions() * LAYER_STACKS;
            threatPsqtWeights = Arrays.copyOfRange(combinedPsqt, 0, threatPsqtLength);
            psqtWeights = Arrays.copyOfRange(combinedPsqt, threatPsqtLength, combinedPsqt.length);
        } else {
            psqWeights = cursor.readLebShortArray((long) layout.psqDimensions() * layout.transformedDimensions);
            psqtWeights = cursor.readLebIntArray((long) layout.psqDimensions() * LAYER_STACKS);
            if (layout.variant.scaleSmallTransformer) {
                scaleSmallTransformer(biases, psqWeights);
            }
        }

        return new FeatureTransformer(layout, biases, psqWeights, threatWeights, psqtWeights, threatPsqtWeights);
    }

    /**
     * Creates transformed features and PSQT contribution.
     *
     * @param position position
     * @param board Stockfish-order board
     * @param bucket layer bucket
     * @return transformed output
     */
    TransformOutput transform(Position position, int[] board, int bucket) {
        int[][] psqAccumulation = new int[2][transformedDimensions];
        int[][] threatAccumulation = layout.useThreats ? new int[2][transformedDimensions] : null;
        int[][] psqtAccumulation = new int[2][LAYER_STACKS];
        int[][] threatPsqtAccumulation = layout.useThreats ? new int[2][LAYER_STACKS] : null;

        for (int perspective = UpstreamFeatures.WHITE; perspective <= UpstreamFeatures.BLACK; perspective++) {
            accumulatePerspectiveFeatures(
                    board,
                    perspective,
                    psqAccumulation[perspective],
                    psqtAccumulation[perspective],
                    threatAccumulation == null ? null : threatAccumulation[perspective],
                    threatPsqtAccumulation == null ? null : threatPsqtAccumulation[perspective]);
        }

        int stm = UpstreamFeatures.sideToMove(position);
        int opponent = stm ^ 1;
        int psqt = psqtAccumulation[stm][bucket] - psqtAccumulation[opponent][bucket];
	        if (threatPsqtAccumulation != null) {
	            psqt = (psqt + threatPsqtAccumulation[stm][bucket] - threatPsqtAccumulation[opponent][bucket]) / 2;
	        } else {
	            psqt /= 2;
	        }

        int[] transformed = new int[transformedDimensions];
        int half = transformedDimensions / 2;
        int smallClamp = layout.variant.scaleSmallTransformer ? 254 : 255;
        for (int p = 0; p < 2; p++) {
            int perspective = p == 0 ? stm : opponent;
            int offset = half * p;
            writePerspectiveFeatures(
                    transformed,
                    offset,
                    psqAccumulation[perspective],
                    threatAccumulation == null ? null : threatAccumulation[perspective],
                    half,
                    smallClamp);
        }
        return new TransformOutput(transformed, psqt);
    }

    /**
     * Returns total input dimensions.
     *
     * @return dimensions
     */
    int totalInputDimensions() {
        return layout.totalInputDimensions();
    }

    /**
     * Copies biases into one accumulator.
     *
     * @param target target accumulator
     */
    private void copyBias(int[] target) {
        for (int i = 0; i < target.length; i++) {
            target[i] = biases[i];
        }
    }

    /**
     * Accumulates active PSQ and optional threat features for one perspective.
     *
     * @param board Stockfish-order board
     * @param perspective perspective color
     * @param psqAccumulation hidden PSQ accumulator
     * @param psqtAccumulation PSQT accumulator
     * @param threatAccumulation hidden threat accumulator, or {@code null}
     * @param threatPsqtAccumulation threat PSQT accumulator, or {@code null}
     */
    void accumulatePerspectiveFeatures(
            int[] board,
            int perspective,
            int[] psqAccumulation,
            int[] psqtAccumulation,
            int[] threatAccumulation,
            int[] threatPsqtAccumulation) {
        copyBias(psqAccumulation);
        int[] psqFeatures = UpstreamFeatures.activeHalfKa(board, perspective);
        addShortFeatures(psqAccumulation, psqtAccumulation, psqFeatures, psqWeights, psqtWeights);
        // A null threat accumulator means "PSQ only" (the incremental BIG-net slot
        // keeps PSQ here and rebuilds threats separately), so skip the threat pass.
        if (!layout.useThreats || threatAccumulation == null) {
            return;
        }
        int[] threatFeatures = UpstreamFeatures.activeThreats(board, perspective, layout.variant);
        addByteFeatures(threatAccumulation, threatPsqtAccumulation, threatFeatures, threatWeights, threatPsqtWeights);
    }

    /**
     * Whether this transformer uses threat features (the BIG nets do).
     *
     * @return true when threat features are active
     */
    boolean useThreats() {
        return layout.useThreats;
    }

    /**
     * Rebuilds only the threat accumulators for one perspective from scratch,
     * leaving PSQ untouched. Used by the incremental BIG-net search state, which
     * keeps PSQ incremental but recomputes threats each node (threat features
     * depend non-locally on board occupancy). Produces exactly the threat
     * contribution {@link #accumulatePerspectiveFeatures} would.
     *
     * @param board Stockfish-order board
     * @param perspective perspective color
     * @param threatAccumulation hidden threat accumulator (reset then filled)
     * @param threatPsqtAccumulation threat PSQT accumulator (reset then filled)
     */
    void accumulateThreatFeatures(
            int[] board,
            int perspective,
            int[] threatAccumulation,
            int[] threatPsqtAccumulation) {
        Arrays.fill(threatAccumulation, 0);
        Arrays.fill(threatPsqtAccumulation, 0);
        int[] threatFeatures = UpstreamFeatures.activeThreats(board, perspective, layout.variant);
        addByteFeatures(threatAccumulation, threatPsqtAccumulation, threatFeatures, threatWeights, threatPsqtWeights);
    }

    /**
     * Writes transformed features for one perspective half.
     *
     * @param transformed output feature vector
     * @param offset destination half offset
     * @param psqAccumulation PSQ accumulator
     * @param threatAccumulation threat accumulator, or {@code null}
     * @param half half-width of the transformed vector
     * @param smallClamp clamp value for small networks without threats
     */
    void writePerspectiveFeatures(
            int[] transformed,
            int offset,
            int[] psqAccumulation,
            int[] threatAccumulation,
            int half,
            int smallClamp) {
        for (int j = 0; j < half; j++) {
            int sum0 = psqAccumulation[j];
            int sum1 = psqAccumulation[j + half];
            if (threatAccumulation != null) {
                sum0 = Numbers.clamp(sum0 + threatAccumulation[j], 0, 255);
                sum1 = Numbers.clamp(sum1 + threatAccumulation[j + half], 0, 255);
            } else {
                sum0 = Numbers.clamp(sum0, 0, smallClamp);
                sum1 = Numbers.clamp(sum1, 0, smallClamp);
            }
            transformed[offset + j] = (sum0 * sum1) / 512;
        }
    }

    /**
     * Adds short-valued feature weights.
     *
     * @param accumulation hidden accumulator
     * @param psqtAccumulation PSQT accumulator
     * @param features active feature list
     * @param weights feature weights
     * @param psqt PSQT weights
     */
    private void addShortFeatures(
            int[] accumulation,
            int[] psqtAccumulation,
            int[] features,
            short[] weights,
            int[] psqt) {
        for (int feature : features) {
            int weightBase = feature * transformedDimensions;
            for (int i = 0; i < transformedDimensions; i++) {
                accumulation[i] += weights[weightBase + i];
            }
            int psqtBase = feature * LAYER_STACKS;
            for (int i = 0; i < LAYER_STACKS; i++) {
                psqtAccumulation[i] += psqt[psqtBase + i];
            }
        }
    }

    /**
     * Adds byte-valued threat feature weights.
     *
     * @param accumulation hidden accumulator
     * @param psqtAccumulation PSQT accumulator
     * @param features active feature list
     * @param weights feature weights
     * @param psqt PSQT weights
     */
    private void addByteFeatures(
            int[] accumulation,
            int[] psqtAccumulation,
            int[] features,
            byte[] weights,
            int[] psqt) {
        for (int feature : features) {
            int weightBase = feature * transformedDimensions;
            for (int i = 0; i < transformedDimensions; i++) {
                accumulation[i] += weights[weightBase + i];
            }
            int psqtBase = feature * LAYER_STACKS;
            for (int i = 0; i < LAYER_STACKS; i++) {
                psqtAccumulation[i] += psqt[psqtBase + i];
            }
        }
    }

    /**
     * Scales Stockfish 18 small-net transformer weights after loading.
     *
     * @param biases biases
     * @param weights PSQ weights
     */
    private static void scaleSmallTransformer(short[] biases, short[] weights) {
        for (int i = 0; i < biases.length; i++) {
            biases[i] = (short) (biases[i] * 2);
        }
        for (int i = 0; i < weights.length; i++) {
            weights[i] = (short) (weights[i] * 2);
        }
    }

    /**
     * Validates tensor shapes.
     */
    private void validate() {
        requireLength(biases, transformedDimensions, "biases");
        requireLength(psqWeights, (long) layout.psqDimensions() * transformedDimensions, "psqWeights");
        requireLength(psqtWeights, (long) layout.psqDimensions() * LAYER_STACKS, "psqtWeights");
        if (layout.useThreats) {
            requireLength(threatWeights, (long) layout.threatDimensions() * transformedDimensions, "threatWeights");
            requireLength(threatPsqtWeights, (long) layout.threatDimensions() * LAYER_STACKS, "threatPsqtWeights");
        }
    }

    /**
     * Validates array length.
     *
     * @param values array
     * @param expected expected length
     * @param label label
     */
    private void requireLength(short[] values, long expected, String label) {
        if (values == null || values.length != checkedLength(expected, label)) {
            throw new IllegalArgumentException(label + " length mismatch.");
        }
    }

    /**
     * Validates array length.
     *
     * @param values array
     * @param expected expected length
     * @param label label
     */
    private void requireLength(int[] values, long expected, String label) {
        if (values == null || values.length != checkedLength(expected, label)) {
            throw new IllegalArgumentException(label + " length mismatch.");
        }
    }

    /**
     * Validates array length.
     *
     * @param values array
     * @param expected expected length
     * @param label label
     */
    private void requireLength(byte[] values, long expected, String label) {
        if (values == null || values.length != checkedLength(expected, label)) {
            throw new IllegalArgumentException(label + " length mismatch.");
        }
    }
}
