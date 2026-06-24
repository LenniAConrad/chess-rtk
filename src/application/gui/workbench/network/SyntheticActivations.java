package application.gui.workbench.network;

import chess.nn.nnue.FeatureEncoder;
import java.util.Random;

/**
 * Deterministic synthetic activation provider used by the Workbench network
 * visualizer while real inference is not wired up.
 *
 * <p>Seeds a PRNG from the FEN string, then fills the snapshot keys each
 * architecture view expects. Shapes match the real model dimensions so
 * swapping in real inference later only changes the producer.</p>
 */
public final class SyntheticActivations {

    /**
     * Default NNUE L1 width (one accumulator side).
     */
    private static final int NNUE_L1 = 256;

    /**
     * Synthetic Stockfish transformed feature width.
     */
    private static final int STOCKFISH_TRANSFORMED = 128;

    /**
     * Synthetic Stockfish FC0 hidden width.
     */
    private static final int STOCKFISH_FC0 = 15;

    /**
     * Synthetic Stockfish FC1 hidden width.
     */
    private static final int STOCKFISH_FC1 = 32;

    /**
     * Stockfish FC0 forward-row scaling in centipawns.
     */
    private static final float STOCKFISH_FWD_CP_SCALE = 600.0f / (127.0f * 64.0f);

    /**
     * Total half-KP features per side (king-square x piece-square pairs).
     */
    private static final int NNUE_FEATURE_DIM = FeatureEncoder.FEATURE_COUNT;

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
     * OTIS placeholder trunk channel count.
     */
    private static final int OTIS_CHANNELS = 64;

    /**
     * Prevents instantiation.
     */
    private SyntheticActivations() {
        // utility
    }

    /**
     * Fills an NNUE snapshot.
     *
     * @param fen current position FEN, used as PRNG seed
     * @param out destination snapshot (cleared first)
     */
    public static void fillNnue(String fen, ActivationSnapshot out) {
        out.clear();
        Random rng = seed(fen, "nnue");

        int active = 20 + rng.nextInt(FeatureEncoder.MAX_ACTIVE_FEATURES - 19);
        int[] activeUs = pickFeatureIndices(rng, active, NNUE_FEATURE_DIM);
        int activeThemCount = 20 + rng.nextInt(FeatureEncoder.MAX_ACTIVE_FEATURES - 19);
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
        float[] contribTotal = new float[NNUE_L1];
        float affine = 0.0f;
        for (int i = 0; i < NNUE_L1; ++i) {
            contribUs[i] = clippedUs[i] * outputWeightsUs[i];
            contribThem[i] = clippedThem[i] * outputWeightsThem[i];
            contribTotal[i] = contribUs[i] + contribThem[i];
            affine += contribTotal[i];
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
        out.put("nnue.output.contribution.total", new int[] { NNUE_L1 }, contribTotal);
        out.put("nnue.output.affine", new int[] { 1 }, new float[] { affine });
        out.putScalar("nnue.output.centipawns", centipawns);
        fillSyntheticStockfishTrace(out, rng, activeWeightsUs);
        fillSyntheticAtlas(out, outputWeightsUs);
    }

    /**
     * Adds Stockfish-shaped synthetic layer-stack tensors. These make the
     * workbench teach the Stockfish architecture even when a real .nnue file is
     * not installed locally.
     *
     * @param out destination snapshot
     * @param rng deterministic random source
     * @param activeWeightsUs existing feature rows used for the first edge stage
     */
    private static void fillSyntheticStockfishTrace(
            ActivationSnapshot out,
            Random rng,
            float[] activeWeightsUs) {
        int half = STOCKFISH_TRANSFORMED / 2;
        float[] transformed = new float[STOCKFISH_TRANSFORMED];
        float[] transformedUs = new float[half];
        float[] transformedThem = new float[half];
        for (int i = 0; i < half; i++) {
            transformedUs[i] = Math.max(0.0f, (float) (rng.nextGaussian() * 18.0 + 46.0));
            transformedThem[i] = Math.max(0.0f, (float) (rng.nextGaussian() * 18.0 + 42.0));
            transformed[i] = transformedUs[i];
            transformed[half + i] = transformedThem[i];
        }

        float[] fc0Weights = gaussian(rng, STOCKFISH_FC0 * half, 0.45f);
        float[] fc0FwdWeights = gaussian(rng, half, 0.45f);
        float[] fc0Raw = new float[STOCKFISH_FC0 + 1];
        for (int row = 0; row < STOCKFISH_FC0; row++) {
            float sum = (float) (rng.nextGaussian() * 12.0);
            for (int col = 0; col < half; col++) {
                sum += fc0Weights[row * half + col] * transformedUs[col] * 0.02f;
            }
            fc0Raw[row] = sum;
        }
        float fwdRaw = (float) (rng.nextGaussian() * 4.0);
        for (int col = 0; col < half; col++) {
            fwdRaw += fc0FwdWeights[col] * transformedUs[col] * 0.02f;
        }
        fc0Raw[STOCKFISH_FC0] = fwdRaw;

        float[] fc0Sqr = new float[STOCKFISH_FC0];
        float[] fc0Crelu = new float[STOCKFISH_FC0];
        float[] fc1Input = new float[STOCKFISH_FC0 * 2];
        for (int i = 0; i < STOCKFISH_FC0; i++) {
            float clipped = Math.max(0.0f, Math.min(127.0f, fc0Raw[i]));
            fc0Sqr[i] = Math.min(127.0f, clipped * clipped / 127.0f);
            fc0Crelu[i] = clipped;
            fc1Input[i] = fc0Sqr[i];
            fc1Input[STOCKFISH_FC0 + i] = fc0Crelu[i];
        }

        float[] fc1Weights = gaussian(rng, STOCKFISH_FC1 * STOCKFISH_FC0, 0.35f);
        float[] fc1Raw = new float[STOCKFISH_FC1];
        float[] fc1Clipped = new float[STOCKFISH_FC1];
        for (int row = 0; row < STOCKFISH_FC1; row++) {
            float sum = (float) (rng.nextGaussian() * 8.0);
            for (int col = 0; col < STOCKFISH_FC0; col++) {
                sum += fc1Weights[row * STOCKFISH_FC0 + col] * (fc0Sqr[col] + fc0Crelu[col]) * 0.03f;
            }
            fc1Raw[row] = sum;
            fc1Clipped[row] = Math.max(0.0f, Math.min(127.0f, sum));
        }

        float[] fc2Weights = gaussian(rng, STOCKFISH_FC1, 0.08f);
        float[] fc2Contribution = new float[STOCKFISH_FC1];
        float positional = 0.0f;
        for (int i = 0; i < STOCKFISH_FC1; i++) {
            fc2Contribution[i] = fc1Clipped[i] * fc2Weights[i];
            positional += fc2Contribution[i];
        }
        float fwdContribution = fc0Raw[STOCKFISH_FC0] * STOCKFISH_FWD_CP_SCALE;
        positional += fwdContribution;
        float psqt = (float) (rng.nextGaussian() * 12.0);
        float cp = psqt + positional;

        out.put("nnue.stockfish.bucket", new int[] { 1 }, new float[] { 7 });
        out.put("nnue.stockfish.psqt.cp", new int[] { 1 }, new float[] { psqt });
        out.put("nnue.stockfish.positional.cp", new int[] { 1 }, new float[] { positional });
        out.put("nnue.stockfish.transformed", new int[] { STOCKFISH_TRANSFORMED }, transformed);
        out.put("nnue.stockfish.transformed.us", new int[] { half }, transformedUs);
        out.put("nnue.stockfish.transformed.them", new int[] { half }, transformedThem);
        out.put("nnue.stockfish.fc0.raw", new int[] { STOCKFISH_FC0 + 1 }, fc0Raw);
        out.put("nnue.stockfish.fc0.sqr", new int[] { STOCKFISH_FC0 }, fc0Sqr);
        out.put("nnue.stockfish.fc0.crelu", new int[] { STOCKFISH_FC0 }, fc0Crelu);
        out.put("nnue.stockfish.fc0.weights.us", new int[] { STOCKFISH_FC0, half }, fc0Weights);
        out.put("nnue.stockfish.fc0.weights.fwd.us", new int[] { half }, fc0FwdWeights);
        out.put("nnue.stockfish.fc1.input", new int[] { STOCKFISH_FC0 * 2 }, fc1Input);
        out.put("nnue.stockfish.fc1.raw", new int[] { STOCKFISH_FC1 }, fc1Raw);
        out.put("nnue.stockfish.fc1.clipped", new int[] { STOCKFISH_FC1 }, fc1Clipped);
        out.put("nnue.stockfish.fc1.weights.combined", new int[] { STOCKFISH_FC1, STOCKFISH_FC0 }, fc1Weights);
        out.put("nnue.stockfish.fc2.weights", new int[] { STOCKFISH_FC1 }, fc2Weights);
        out.put("nnue.stockfish.fc2.contribution", new int[] { STOCKFISH_FC1 }, fc2Contribution);
        out.put("nnue.stockfish.fc2.bias.cp", new int[] { 1 }, new float[] { 0.0f });
        out.put("nnue.stockfish.fc0.fwd.cp", new int[] { 1 }, new float[] { fwdContribution });
        out.put("nnue.stockfish.output.parts", new int[] { 4 },
                new float[] { psqt, 0.0f, positional - fwdContribution, fwdContribution });
        out.put("nnue.output.contribution.us", new int[] { STOCKFISH_FC1 }, fc2Contribution);
        out.put("nnue.output.contribution.them", new int[] { STOCKFISH_FC1 }, new float[STOCKFISH_FC1]);
        out.put("nnue.output.contribution.total", new int[] { STOCKFISH_FC1 }, fc2Contribution);
        out.put("nnue.output.weights.us", new int[] { STOCKFISH_FC1 }, fc2Weights);
        out.put("nnue.output.weights.them", new int[] { STOCKFISH_FC1 }, new float[STOCKFISH_FC1]);
        out.putScalar("nnue.output.centipawns", cp);

        int[] shape = out.shape("nnue.features.us.weights");
        if (shape.length >= 2 && shape[1] != half && activeWeightsUs.length >= shape[0] * shape[1]) {
            float[] clippedRows = new float[shape[0] * half];
            for (int row = 0; row < shape[0]; row++) {
                System.arraycopy(activeWeightsUs, row * shape[1], clippedRows, row * half, half);
            }
            out.put("nnue.features.us.weights", new int[] { shape[0], half }, clippedRows);
        }
    }

    /**
     * Writes a position-independent synthetic feature-weight atlas so the
     * workbench atlas view always has something to render even when the
     * loaded NNUE is in an upstream format that the CRTK weight-dump path
     * does not yet support. Each neuron gets a stable per-piece "preferred
     * square" pattern derived from a deterministic hash so the atlas looks
     * Wikipedia-like (varied tiles, hot spots, no two neurons identical).
     *
     * @param out destination snapshot
     * @param outputWeights pre-computed output-weight vector for slot
     *     importance sorting
     */
    private static void fillSyntheticAtlas(ActivationSnapshot out, float[] outputWeights) {
        int hidden = NNUE_L1;
        int planes = 10;
        int squares = 64;
        float[] atlas = new float[hidden * planes * squares];
        float[] king = new float[hidden * squares];
        float kingNorm = 1.0f / planes;
        for (int h = 0; h < hidden; h++) {
            for (int p = 0; p < planes; p++) {
                int hotSq = (int) (((h * 1103515245L + p * 12345L) & 0x7fffffff) % 64);
                int hotSq2 = (int) (((h * 2654435761L ^ (p * 1664525L)) & 0x7fffffff) % 64);
                float polarity = (((h ^ p) & 1) == 0) ? 1.0f : -1.0f;
                for (int s = 0; s < squares; s++) {
                    float dist1 = squareDistance(s, hotSq);
                    float dist2 = squareDistance(s, hotSq2);
                    float bumped = (float) (Math.exp(-dist1 * dist1 * 0.55) * 0.85
                            + Math.exp(-dist2 * dist2 * 0.5) * 0.5);
                    float wiggle = (float) Math.sin((h * 0.13 + p * 0.41 + s * 0.27)) * 0.12f;
                    float v = polarity * (bumped + wiggle);
                    atlas[(h * planes + p) * squares + s] = v;
                    king[h * squares + s] += v * kingNorm;
                }
            }
        }
        out.put("nnue.atlas.weights", new int[] { hidden, planes, squares }, atlas);
        out.put("nnue.atlas.king", new int[] { hidden, squares }, king);
        out.put("nnue.atlas.output", new int[] { hidden }, outputWeights.clone());
    }

    /**
     * Chebyshev-style square distance for the synthetic atlas falloff.
     *
     * @param a square index (0..63)
     * @param b square index (0..63)
     * @return distance in board steps
     */
    private static float squareDistance(int a, int b) {
        int fa = a & 7;
        int ra = a >> 3;
        int fb = b & 7;
        int rb = b >> 3;
        return Math.max(Math.abs(fa - fb), Math.abs(ra - rb));
    }

    /**
     * Fills an LC0 CNN snapshot.
     *
     * @param fen current position FEN, used as PRNG seed
     * @param out destination snapshot (cleared first)
     */
    public static void fillCnn(String fen, ActivationSnapshot out) {
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
    public static void fillBt4(String fen, ActivationSnapshot out) {
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
     * Fills an OTIS policy/WDL snapshot.
     *
     * @param fen current position FEN, used as PRNG seed
     * @param out destination snapshot (cleared first)
     */
    public static void fillOtis(String fen, ActivationSnapshot out) {
        out.clear();
        Random rng = seed(fen, "otis");

        float[] input = gaussian(rng, chess.nn.otis.Model.INPUT_PLANES * 8 * 8, 0.35f);
        out.put("otis.input", new int[] { chess.nn.otis.Model.INPUT_PLANES, 8, 8 }, input);

        float[] salience = new float[64];
        for (int sq = 0; sq < 64; sq++) {
            salience[sq] = (float) Math.tanh(rng.nextGaussian() * 0.65);
        }
        out.put("otis.square.salience", new int[] { 8, 8 }, salience);

        float[] sheafNode = new float[64];
        float[] sheafLaplacian = new float[64];
        for (int sq = 0; sq < 64; sq++) {
            sheafNode[sq] = (float) Math.tanh(salience[sq] + rng.nextGaussian() * 0.22);
            sheafLaplacian[sq] = (float) Math.tanh(rng.nextGaussian() * 0.35 - sheafNode[sq] * 0.18);
        }
        out.put("otis.sheaf.node", new int[] { 8, 8 }, sheafNode);
        out.put("otis.sheaf.laplacian", new int[] { 8, 8 }, sheafLaplacian);

        float[] relationDensity = new float[chess.nn.otis.Model.RELATION_COUNT];
        float[] relationEnergy = new float[chess.nn.otis.Model.RELATION_COUNT];
        float[] relationGate = new float[chess.nn.otis.Model.RELATION_COUNT];
        float[] sourcePressure = new float[chess.nn.otis.Model.RELATION_COUNT * 64];
        float[] targetPressure = new float[chess.nn.otis.Model.RELATION_COUNT * 64];
        for (int r = 0; r < chess.nn.otis.Model.RELATION_COUNT; r++) {
            relationDensity[r] = (float) Math.max(0.0, rng.nextGaussian() * 0.018 + 0.035);
            relationEnergy[r] = (float) Math.abs(rng.nextGaussian() * 0.22 + relationDensity[r] * 6.0);
            relationGate[r] = (float) (0.75 + rng.nextDouble() * 0.65);
            for (int sq = 0; sq < 64; sq++) {
                float base = Math.max(0.0f, sheafNode[sq] + 1.0f) * 0.5f;
                sourcePressure[r * 64 + sq] = (float) Math.max(0.0, base + rng.nextGaussian() * 0.18);
                targetPressure[r * 64 + sq] = (float) Math.max(0.0, base + rng.nextGaussian() * 0.18);
            }
        }
        out.put("otis.sheaf.target.pressure",
                new int[] { chess.nn.otis.Model.RELATION_COUNT, 8, 8 }, targetPressure);
        out.put("otis.sheaf.source.pressure",
                new int[] { chess.nn.otis.Model.RELATION_COUNT, 8, 8 }, sourcePressure);
        out.put("otis.sheaf.relation.density",
                new int[] { chess.nn.otis.Model.RELATION_COUNT }, relationDensity);
        out.put("otis.sheaf.relation.energy",
                new int[] { chess.nn.otis.Model.RELATION_COUNT }, relationEnergy);
        out.put("otis.sheaf.relation.gate",
                new int[] { chess.nn.otis.Model.RELATION_COUNT }, relationGate);
        out.putScalar("otis.sheaf.tension", mean(relationEnergy));
        out.putScalar("otis.sheaf.transport_imbalance", Math.abs(relationDensity[0] - relationDensity[1])
                / Math.max(1.0e-6f, relationDensity[0] + relationDensity[1]));
        out.putScalar("otis.sheaf.topology_pressure", mean(relationDensity));
        out.putScalar("otis.sheaf.pin_pressure", relationDensity[11]);

        float[] trunk = new float[OTIS_CHANNELS * 64];
        float[] summary = new float[OTIS_CHANNELS];
        for (int c = 0; c < OTIS_CHANNELS; c++) {
            float sum = 0.0f;
            for (int sq = 0; sq < 64; sq++) {
                float v = (float) Math.tanh(salience[sq] * (0.8 + rng.nextDouble() * 0.35)
                        + rng.nextGaussian() * 0.18);
                trunk[c * 64 + sq] = v;
                sum += v;
            }
            summary[c] = sum / 64.0f;
        }
        out.put("otis.trunk", new int[] { OTIS_CHANNELS, 8, 8 }, trunk);
        out.put("otis.trunk.summary", new int[] { OTIS_CHANNELS }, summary);

        float[] policyLogits = gaussian(rng, chess.nn.lc0.bt4.PolicyEncoder.POLICY_SIZE, 1.1f);
        out.put("otis.policy.logits", new int[] { policyLogits.length }, policyLogits);

        float[] valueLogits = gaussian(rng, 3, 1.0f);
        out.put("otis.value.logits", new int[] { 3 }, valueLogits);
        float[] wdl = softmax(valueLogits);
        out.put("otis.value.wdl", new int[] { 3 }, wdl);
        out.putScalar("otis.value.scalar", wdl[0] - wdl[2]);

        out.put("otis.weights.square", new int[] { 8, 8 }, gaussian(rng, 64, 0.7f));
        out.put("otis.weights.policy", new int[] { 8, 8 }, gaussian(rng, 64, 0.7f));
        out.put("otis.weights.value", new int[] { 8, 8 }, gaussian(rng, 64, 0.7f));
        out.put("otis.weights.raw_proj",
                new int[] { chess.nn.otis.Model.RAW_DIM, chess.nn.otis.Model.INPUT_PLANES },
                gaussian(rng, chess.nn.otis.Model.RAW_DIM * chess.nn.otis.Model.INPUT_PLANES, 0.08f));
        out.put("otis.weights.piece_proj",
                new int[] { chess.nn.otis.Model.PIECE_DIM, chess.nn.otis.Model.PIECE_STATE_PLANES },
                gaussian(rng, chess.nn.otis.Model.PIECE_DIM * chess.nn.otis.Model.PIECE_STATE_PLANES, 0.08f));
        out.put("otis.weights.coord_proj", new int[] { chess.nn.otis.Model.COORD_DIM, 6 },
                gaussian(rng, chess.nn.otis.Model.COORD_DIM * 6, 0.08f));
        out.put("otis.weights.rho_src",
                new int[] { chess.nn.otis.Model.DEFAULT_BLOCKS * chess.nn.otis.Model.RELATION_COUNT,
                        chess.nn.otis.Model.STALK_DIM, chess.nn.otis.Model.STALK_DIM },
                gaussian(rng, chess.nn.otis.Model.DEFAULT_BLOCKS * chess.nn.otis.Model.RELATION_COUNT
                        * chess.nn.otis.Model.STALK_DIM * chess.nn.otis.Model.STALK_DIM, 0.12f));
        out.put("otis.weights.rho_dst",
                new int[] { chess.nn.otis.Model.DEFAULT_BLOCKS * chess.nn.otis.Model.RELATION_COUNT,
                        chess.nn.otis.Model.STALK_DIM, chess.nn.otis.Model.STALK_DIM },
                gaussian(rng, chess.nn.otis.Model.DEFAULT_BLOCKS * chess.nn.otis.Model.RELATION_COUNT
                        * chess.nn.otis.Model.STALK_DIM * chess.nn.otis.Model.STALK_DIM, 0.12f));
        out.put("otis.weights.readout_hidden",
                new int[] { chess.nn.otis.Model.HIDDEN_DIM, chess.nn.otis.Model.defaultReadoutDim() },
                gaussian(rng, chess.nn.otis.Model.HIDDEN_DIM * chess.nn.otis.Model.defaultReadoutDim(), 0.04f));
        out.put("otis.weights.policy_head",
                new int[] { chess.nn.otis.Model.DEFAULT_POLICY_SIZE, chess.nn.otis.Model.HIDDEN_DIM },
                gaussian(rng, chess.nn.otis.Model.DEFAULT_POLICY_SIZE * chess.nn.otis.Model.HIDDEN_DIM, 0.03f));
        out.put("otis.weights.wdl_head", new int[] { 3, chess.nn.otis.Model.HIDDEN_DIM },
                gaussian(rng, 3 * chess.nn.otis.Model.HIDDEN_DIM, 0.04f));
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
        int count = Math.min(Math.max(n, 0), Math.max(dim, 0));
        int[] out = new int[count];
        if (count == 0) {
            return out;
        }
        boolean[] used = new boolean[dim];
        int filled = 0;
        while (filled < count) {
            int candidate = rng.nextInt(dim);
            if (!used[candidate]) {
                used[candidate] = true;
                out[filled++] = candidate;
            }
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
     * @return clipped
     */
    private static float[] clipped(float[] input) {
        float[] out = new float[input.length];
        for (int i = 0; i < input.length; ++i) {
            out[i] = Math.max(0.0f, Math.min(1.0f, input[i]));
        }
        return out;
    }

    /**
     * Returns arithmetic mean.
     *
     * @param values input values
     * @return mean, or zero for an empty array
     */
    private static float mean(float[] values) {
        if (values == null || values.length == 0) {
            return 0.0f;
        }
        float sum = 0.0f;
        for (float value : values) {
            sum += value;
        }
        return sum / values.length;
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
     * @param indices source indices
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
