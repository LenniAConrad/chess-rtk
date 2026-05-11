package application.gui.workbench;

import java.util.Random;

/**
 * Deterministic synthetic activation provider used by the Workbench network
 * visualizer while real inference is not wired up.
 *
 * <p>Seeds a PRNG from the FEN string, then fills the snapshot keys each
 * architecture view expects. Shapes match the real model dimensions so
 * swapping in real inference later only changes the producer.</p>
 */
final class WorkbenchSyntheticActivations {

    /**
     * Default NNUE L1 width (one accumulator side).
     */
    private static final int NNUE_L1 = 256;

    /**
     * Total half-KP features per side (king-square x piece-square pairs).
     */
    private static final int NNUE_FEATURE_DIM = 41024;

    /**
     * LC0 CNN residual block count for the demo model.
     */
    private static final int CNN_BLOCKS = 10;

    /**
     * LC0 CNN channels per residual block.
     */
    private static final int CNN_CHANNELS = 128;

    /**
     * LC0 BT4 transformer block count for the demo model.
     */
    private static final int BT4_BLOCKS = 15;

    /**
     * LC0 BT4 attention head count per block.
     */
    private static final int BT4_HEADS = 32;

    /**
     * Prevents instantiation.
     */
    private WorkbenchSyntheticActivations() {
        // utility
    }

    /**
     * Fills an NNUE snapshot.
     *
     * @param fen current position FEN, used as PRNG seed
     * @param out destination snapshot (cleared first)
     */
    static void fillNnue(String fen, WorkbenchActivationSnapshot out) {
        out.clear();
        Random rng = seed(fen, "nnue");

        int active = 20 + rng.nextInt(12);
        int[] activeUs = pickFeatureIndices(rng, active, NNUE_FEATURE_DIM);
        int activeThemCount = 20 + rng.nextInt(12);
        int[] activeThem = pickFeatureIndices(rng, activeThemCount, NNUE_FEATURE_DIM);

        float[] featureWeightsUs = gaussian(rng, NNUE_L1, 0.05f);
        float[] featureWeightsThem = gaussian(rng, NNUE_L1, 0.05f);
        float[] featureBias = gaussian(rng, NNUE_L1, 0.02f);

        float[] accumUs = new float[NNUE_L1];
        float[] accumThem = new float[NNUE_L1];
        for (int i = 0; i < NNUE_L1; ++i) {
            accumUs[i] = featureBias[i];
            accumThem[i] = featureBias[i];
        }
        for (int idx : activeUs) {
            float w = featureWeightsUs[idx % NNUE_L1];
            for (int i = 0; i < NNUE_L1; ++i) {
                accumUs[i] += w * (((idx * 2654435761L) >>> (i & 31) & 1) == 1 ? 1.0f : -1.0f) * 0.18f;
            }
        }
        for (int idx : activeThem) {
            float w = featureWeightsThem[idx % NNUE_L1];
            for (int i = 0; i < NNUE_L1; ++i) {
                accumThem[i] += w * (((idx * 1597334677L) >>> (i & 31) & 1) == 1 ? 1.0f : -1.0f) * 0.18f;
            }
        }

        float[] clippedUs = clipped(accumUs);
        float[] clippedThem = clipped(accumThem);

        float[] outputWeightsUs = gaussian(rng, NNUE_L1, 0.08f);
        float[] outputWeightsThem = gaussian(rng, NNUE_L1, 0.08f);
        float[] contribUs = new float[NNUE_L1];
        float[] contribThem = new float[NNUE_L1];
        float affine = 0.0f;
        for (int i = 0; i < NNUE_L1; ++i) {
            contribUs[i] = clippedUs[i] * outputWeightsUs[i];
            contribThem[i] = clippedThem[i] * outputWeightsThem[i];
            affine += contribUs[i] - contribThem[i];
        }
        float[] perFeatureImpact = new float[activeUs.length];
        for (int i = 0; i < activeUs.length; ++i) {
            int idx = activeUs[i];
            perFeatureImpact[i] = (float) ((rng.nextGaussian() * 18.0) + featureWeightsUs[idx % NNUE_L1] * 90.0);
        }

        float centipawns = affine * 12.0f;

        float[] featuresUs = sparseOneHotPattern(activeUs, NNUE_L1, rng);
        float[] featuresThem = sparseOneHotPattern(activeThem, NNUE_L1, rng);

        out.put("nnue.features.us.indices", new int[] { activeUs.length }, intsToFloats(activeUs));
        out.put("nnue.features.them.indices", new int[] { activeThem.length }, intsToFloats(activeThem));
        out.put("nnue.features.us.impact", new int[] { activeUs.length }, perFeatureImpact);
        float[] activeWeightsUs = new float[activeUs.length * NNUE_L1];
        for (int i = 0; i < activeUs.length; ++i) {
            int idx = activeUs[i];
            for (int h = 0; h < NNUE_L1; ++h) {
                long mix = (long) idx * 2654435761L + h * 16777619L;
                activeWeightsUs[i * NNUE_L1 + h] = (float) ((rng.nextGaussian() * 0.03) + (mix % 17) * 0.001);
            }
        }
        out.put("nnue.features.us.weights", new int[] { activeUs.length, NNUE_L1 }, activeWeightsUs);
        out.put("nnue.features.us", new int[] { NNUE_L1 }, featuresUs);
        out.put("nnue.features.them", new int[] { NNUE_L1 }, featuresThem);
        out.put("nnue.feature.weights.us", new int[] { NNUE_L1 }, featureWeightsUs);
        out.put("nnue.feature.weights.them", new int[] { NNUE_L1 }, featureWeightsThem);
        out.put("nnue.feature.bias", new int[] { NNUE_L1 }, featureBias);
        out.put("nnue.accumulator.us", new int[] { NNUE_L1 }, accumUs);
        out.put("nnue.accumulator.them", new int[] { NNUE_L1 }, accumThem);
        out.put("nnue.clipped.us", new int[] { NNUE_L1 }, clippedUs);
        out.put("nnue.clipped.them", new int[] { NNUE_L1 }, clippedThem);
        out.put("nnue.output.weights.us", new int[] { NNUE_L1 }, outputWeightsUs);
        out.put("nnue.output.weights.them", new int[] { NNUE_L1 }, outputWeightsThem);
        out.put("nnue.output.contribution.us", new int[] { NNUE_L1 }, contribUs);
        out.put("nnue.output.contribution.them", new int[] { NNUE_L1 }, contribThem);
        out.put("nnue.output.affine", new int[] { 1 }, new float[] { affine });
        out.putScalar("nnue.output.centipawns", centipawns);
    }

    /**
     * Fills an LC0 CNN snapshot.
     *
     * @param fen current position FEN, used as PRNG seed
     * @param out destination snapshot (cleared first)
     */
    static void fillCnn(String fen, WorkbenchActivationSnapshot out) {
        out.clear();
        Random rng = seed(fen, "cnn");

        float[] inputPlanes = gaussian(rng, 112 * 8 * 8, 0.4f);
        out.put("cnn.input", new int[] { 112, 8, 8 }, inputPlanes);

        float[] stem = relu(gaussian(rng, CNN_CHANNELS * 8 * 8, 0.6f));
        out.put("cnn.stem.relu", new int[] { CNN_CHANNELS, 8, 8 }, stem);

        for (int b = 0; b < CNN_BLOCKS; ++b) {
            float scale = 0.55f + 0.05f * (float) Math.sin(b * 1.7 + rng.nextDouble());
            float[] block = relu(gaussian(rng, CNN_CHANNELS * 8 * 8, scale));
            out.put("cnn.block" + b + ".relu", new int[] { CNN_CHANNELS, 8, 8 }, block);
        }

        float[] finalMap = relu(gaussian(rng, CNN_CHANNELS * 8 * 8, 0.7f));
        out.put("cnn.final.relu", new int[] { CNN_CHANNELS, 8, 8 }, finalMap);

        float[] heatmap = new float[64];
        for (int i = 0; i < 64; ++i) {
            heatmap[i] = (float) Math.tanh(rng.nextGaussian() * 0.5);
        }
        out.put("cnn.final.activation", new int[] { 8, 8 }, heatmap);

        float[] policyHidden = relu(gaussian(rng, 80 * 8 * 8, 0.4f));
        out.put("cnn.policy.hidden", new int[] { 80, 8, 8 }, policyHidden);
        float[] policyPlanes = gaussian(rng, 73 * 8 * 8, 0.5f);
        out.put("cnn.policy.planes", new int[] { 73, 8, 8 }, policyPlanes);
        float[] policyLogits = gaussian(rng, 1858, 1.4f);
        out.put("cnn.policy.logits", new int[] { 1858 }, policyLogits);

        float[] valueConv = relu(gaussian(rng, 32 * 8 * 8, 0.4f));
        out.put("cnn.value.conv", new int[] { 32, 8, 8 }, valueConv);
        float[] valueFc1 = relu(gaussian(rng, 128, 0.5f));
        out.put("cnn.value.fc1", new int[] { 128 }, valueFc1);
        float[] valueLogits = gaussian(rng, 3, 1.0f);
        out.put("cnn.value.logits", new int[] { 3 }, valueLogits);
        float[] wdl = softmax(valueLogits);
        out.put("cnn.value.wdl", new int[] { 3 }, wdl);
        out.putScalar("cnn.value.scalar", wdl[0] - wdl[2]);
    }

    /**
     * Fills an LC0 BT4 snapshot.
     *
     * @param fen current position FEN, used as PRNG seed
     * @param out destination snapshot (cleared first)
     */
    static void fillBt4(String fen, WorkbenchActivationSnapshot out) {
        out.clear();
        Random rng = seed(fen, "bt4");

        float[] inputPlanes = gaussian(rng, 112 * 8 * 8, 0.4f);
        out.put("bt4.input", new int[] { 112, 8, 8 }, inputPlanes);

        float[] tokenFeatures = gaussian(rng, 64 * 112, 0.3f);
        out.put("bt4.token.features", new int[] { 64, 112 }, tokenFeatures);

        float[] embedding = gaussian(rng, 64 * 768, 0.45f);
        out.put("bt4.token.embedding", new int[] { 64, 768 }, embedding);

        for (int blk = 0; blk < BT4_BLOCKS; ++blk) {
            float[] heads = new float[BT4_HEADS * 64 * 64];
            for (int h = 0; h < BT4_HEADS; ++h) {
                fillHeadAttention(rng, heads, h * 64 * 64, blk, h);
            }
            out.put("bt4.block" + blk + ".attention.heads",
                    new int[] { BT4_HEADS, 64, 64 }, heads);

            float[] attnOut = gaussian(rng, 64 * 768, 0.4f + 0.02f * blk);
            out.put("bt4.block" + blk + ".attention.out", new int[] { 64, 768 }, attnOut);

            float[] ffn = relu(gaussian(rng, 64 * 768, 0.5f));
            out.put("bt4.block" + blk + ".ffn", new int[] { 64, 768 }, ffn);

            float[] blkOut = gaussian(rng, 64 * 768, 0.5f);
            out.put("bt4.block" + blk + ".out", new int[] { 64, 768 }, blkOut);
        }

        float[] finalTokens = gaussian(rng, 64 * 768, 0.6f);
        out.put("bt4.final.tokens", new int[] { 64, 768 }, finalTokens);

        float[] tokenEnergy = new float[64];
        for (int sq = 0; sq < 64; ++sq) {
            tokenEnergy[sq] = (float) Math.tanh(rng.nextGaussian() * 0.6);
        }
        out.put("bt4.token.energy", new int[] { 8, 8 }, tokenEnergy);

        float[] valueSalience = new float[64];
        for (int sq = 0; sq < 64; ++sq) {
            valueSalience[sq] = (float) Math.tanh(rng.nextGaussian() * 0.5);
        }
        out.put("bt4.value.salience", new int[] { 8, 8 }, valueSalience);

        float[] policyLogits = gaussian(rng, 1858, 1.3f);
        out.put("bt4.policy.logits", new int[] { 1858 }, policyLogits);
        float[] valueLogits = gaussian(rng, 3, 1.0f);
        out.put("bt4.value.logits", new int[] { 3 }, valueLogits);
        float[] wdl = softmax(valueLogits);
        out.put("bt4.value.wdl", new int[] { 3 }, wdl);
        out.putScalar("bt4.value.scalar", wdl[0] - wdl[2]);
    }

    /**
     * Fills a single 64x64 attention head into the destination buffer with a
     * non-uniform pattern that emphasises rank/file/diagonal structure so the
     * visualizer can show recognisable chess motifs.
     *
     * @param rng PRNG
     * @param dst destination buffer
     * @param offset write offset
     * @param blockIndex block index (used to shift focus deeper in the network)
     * @param headIndex head index (used to vary per-head pattern)
     */
    private static void fillHeadAttention(Random rng, float[] dst, int offset,
            int blockIndex, int headIndex) {
        int mode = headIndex % 6;
        for (int from = 0; from < 64; ++from) {
            int fromFile = from & 7;
            int fromRank = from >> 3;
            float[] row = new float[64];
            float total = 0.0f;
            for (int to = 0; to < 64; ++to) {
                int toFile = to & 7;
                int toRank = to >> 3;
                float w;
                switch (mode) {
                    case 0 -> w = (fromFile == toFile) ? 1.6f : 0.05f;
                    case 1 -> w = (fromRank == toRank) ? 1.5f : 0.05f;
                    case 2 -> w = (Math.abs(fromFile - toFile) == Math.abs(fromRank - toRank)) ? 1.3f : 0.04f;
                    case 3 -> {
                        int df = Math.abs(fromFile - toFile);
                        int dr = Math.abs(fromRank - toRank);
                        w = ((df == 1 && dr == 2) || (df == 2 && dr == 1)) ? 1.4f : 0.03f;
                    }
                    case 4 -> {
                        int df = Math.abs(fromFile - toFile);
                        int dr = Math.abs(fromRank - toRank);
                        w = Math.max(0.0f, 1.5f - (df + dr) * 0.25f);
                    }
                    default -> w = (float) Math.max(0.0, 0.4 + rng.nextGaussian() * 0.3);
                }
                w *= (float) (0.7 + 0.6 * rng.nextDouble());
                w += blockIndex * 0.005f * headIndex;
                w = Math.max(0.0f, w);
                row[to] = w;
                total += w;
            }
            if (total <= 0.0f) {
                total = 1.0f;
            }
            for (int to = 0; to < 64; ++to) {
                dst[offset + from * 64 + to] = row[to] / total;
            }
        }
    }

    /**
     * Returns a Random seeded from a string key.
     *
     * @param fen FEN string
     * @param salt namespace string
     * @return Random
     */
    private static Random seed(String fen, String salt) {
        long h = 1469598103934665603L;
        String combined = salt + "|" + (fen == null ? "" : fen);
        for (int i = 0; i < combined.length(); ++i) {
            h ^= combined.charAt(i);
            h *= 1099511628211L;
        }
        return new Random(h);
    }

    /**
     * Picks n distinct feature indices in [0, dim).
     *
     * @param rng PRNG
     * @param n count
     * @param dim dimension
     * @return indices
     */
    private static int[] pickFeatureIndices(Random rng, int n, int dim) {
        int[] out = new int[n];
        for (int i = 0; i < n; ++i) {
            out[i] = rng.nextInt(dim);
        }
        return out;
    }

    /**
     * Returns a gaussian-noise float array.
     *
     * @param rng PRNG
     * @param size element count
     * @param sigma standard deviation
     * @return array
     */
    private static float[] gaussian(Random rng, int size, float sigma) {
        float[] out = new float[size];
        for (int i = 0; i < size; ++i) {
            out[i] = (float) (rng.nextGaussian() * sigma);
        }
        return out;
    }

    /**
     * ReLU in-place copy.
     *
     * @param input source
     * @return ReLU(input)
     */
    private static float[] relu(float[] input) {
        float[] out = new float[input.length];
        for (int i = 0; i < input.length; ++i) {
            out[i] = Math.max(0.0f, input[i]);
        }
        return out;
    }

    /**
     * NNUE-style clipped activation (clamped to [0, 1]).
     *
     * @param input source
     * @return clipped result
     */
    private static float[] clipped(float[] input) {
        float[] out = new float[input.length];
        for (int i = 0; i < input.length; ++i) {
            out[i] = Math.max(0.0f, Math.min(1.0f, input[i]));
        }
        return out;
    }

    /**
     * Stable softmax across all elements.
     *
     * @param input logits
     * @return probabilities
     */
    private static float[] softmax(float[] input) {
        float max = Float.NEGATIVE_INFINITY;
        for (float v : input) {
            if (v > max) {
                max = v;
            }
        }
        float[] out = new float[input.length];
        float sum = 0.0f;
        for (int i = 0; i < input.length; ++i) {
            out[i] = (float) Math.exp(input[i] - max);
            sum += out[i];
        }
        if (sum <= 0.0f) {
            sum = 1.0f;
        }
        for (int i = 0; i < input.length; ++i) {
            out[i] /= sum;
        }
        return out;
    }

    /**
     * Returns a sparse pattern with bumps at indices &amp; %% L1 to fake
     * accumulator alignment.
     *
     * @param indices feature indices
     * @param dim accumulator width
     * @param rng PRNG
     * @return sparse-ish pattern
     */
    private static float[] sparseOneHotPattern(int[] indices, int dim, Random rng) {
        float[] out = new float[dim];
        for (int idx : indices) {
            out[idx % dim] += 1.0f;
        }
        for (int i = 0; i < dim; ++i) {
            out[i] += (float) (rng.nextGaussian() * 0.02);
        }
        return out;
    }

    /**
     * Converts an int[] of indices into a float[] for snapshot storage.
     *
     * @param indices indices
     * @return float-cast indices
     */
    private static float[] intsToFloats(int[] indices) {
        float[] out = new float[indices.length];
        for (int i = 0; i < indices.length; ++i) {
            out[i] = indices[i];
        }
        return out;
    }
}
