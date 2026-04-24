package chess.eval;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

import chess.core.Move;
import chess.core.Position;
import chess.nn.lc0.PolicyEncoder;

/**
 * LC0 centipawn evaluator backed by {@link chess.nn.lc0.Model}.
 *
 * <p>
 * The LC0 value head returns WDL probabilities. This evaluator maps expected
 * score to a centipawn-like value with the same log-odds transform used by
 * other CRTK display paths.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Lc0 implements CentipawnEvaluator {

    /**
     * Maximum move-ordering bonus derived from LC0 policy.
     */
    private static final int POLICY_ORDERING_BONUS = 30_000;

    /**
     * Direct-mapped cache of recent LC0 policy arrays by position signature.
     */
    private static final int POLICY_CACHE_ENTRIES = 128;

    /**
     * Loaded LC0 policy/value model used for every leaf evaluation.
     */
    private final chess.nn.lc0.Model model;

    /**
     * Last position signature whose policy logits were computed.
     */
    private long lastPolicySignature = Long.MIN_VALUE;

    /**
     * Policy logits cached from the most recent evaluation.
     */
    private float[] lastPolicy;

    /**
     * Direct-mapped recent policy cache signatures.
     */
    private final long[] policyCacheKeys = new long[POLICY_CACHE_ENTRIES];

    /**
     * Direct-mapped recent policy cache values.
     */
    private final float[][] policyCacheValues = new float[POLICY_CACHE_ENTRIES][];

    /**
     * Creates an evaluator from a weights path.
     *
     * @param weights LC0J weights path
     * @throws IOException if the model cannot be loaded
     */
    public Lc0(Path weights) throws IOException {
        this(chess.nn.lc0.Model.load(weights == null ? chess.nn.lc0.Model.DEFAULT_WEIGHTS : weights));
    }

    /**
     * Creates an evaluator from an already loaded model.
     *
     * @param model loaded model
     * @throws IllegalArgumentException if the model is null
     */
    public Lc0(chess.nn.lc0.Model model) {
        if (model == null) {
            throw new IllegalArgumentException("model == null");
        }
        this.model = model;
        Arrays.fill(policyCacheKeys, Long.MIN_VALUE);
    }

    /**
     * Evaluates one position.
     *
     * @param position position to evaluate
     * @return centipawn-like score from LC0 WDL
     */
    @Override
    public int evaluate(Position position) {
        chess.nn.lc0.Network.Prediction prediction = predict(position);
        return wdlToCentipawns(prediction.wdl());
    }

    /**
     * Ensures LC0 policy logits are available before root move ordering.
     *
     * @param position position whose moves are about to be ordered
     */
    @Override
    public void prepareMoveOrdering(Position position) {
        if (position != null) {
            ensurePolicy(position);
        }
    }

    /**
     * Adds LC0 policy-based move-ordering bonuses when logits for the current
     * position are already cached from evaluation.
     *
     * @param position position whose legal moves are being ordered
     * @param moves legal moves aligned with {@code scores}
     * @param scores mutable ordering scores to adjust in place
     */
    @Override
    public void scoreMoves(Position position, short[] moves, int[] scores) {
        if (position == null || moves == null || scores == null || moves.length != scores.length) {
            return;
        }
        float[] policy = policy(position.signature());
        if (policy == null) {
            return;
        }

        float best = Float.NEGATIVE_INFINITY;
        float worst = Float.POSITIVE_INFINITY;
        for (short move : moves) {
            float logit = policyLogit(position, policy, move);
            if (Float.isFinite(logit)) {
                best = Math.max(best, logit);
                worst = Math.min(worst, logit);
            }
        }
        if (!Float.isFinite(best)) {
            return;
        }

        float span = Math.max(1.0e-6f, best - worst);
        for (int i = 0; i < moves.length; i++) {
            float logit = policyLogit(position, policy, moves[i]);
            if (!Float.isFinite(logit)) {
                continue;
            }
            float normalized = (logit - worst) / span;
            scores[i] += Math.round(normalized * POLICY_ORDERING_BONUS);
        }
    }

    /**
     * Returns the evaluator label.
     *
     * @return label including the active LC0 backend
     */
    @Override
    public String name() {
        return Kind.LC0.label() + "(" + model.backend() + ")";
    }

    /**
     * Releases model resources.
     */
    @Override
    public void close() {
        lastPolicy = null;
        model.close();
    }

    /**
     * Runs one LC0 prediction and remembers the returned policy logits.
     *
     * @param position position to evaluate
     * @return LC0 prediction
     */
    private chess.nn.lc0.Network.Prediction predict(Position position) {
        chess.nn.lc0.Network.Prediction prediction = model.predict(position);
        rememberPolicy(position.signature(), prediction.policy());
        return prediction;
    }

    /**
     * Returns a cached policy for a position, loading it on demand when absent.
     *
     * @param position position whose policy is needed
     * @return cached or freshly computed policy logits
     */
    private float[] ensurePolicy(Position position) {
        long signature = position.signature();
        float[] policy = policy(signature);
        if (policy != null) {
            return policy;
        }
        return predict(position).policy();
    }

    /**
     * Returns a cached policy array for a signature when available.
     *
     * @param signature position signature
     * @return cached policy logits or {@code null}
     */
    private float[] policy(long signature) {
        float[] policy = lastPolicy;
        if (policy != null && lastPolicySignature == signature) {
            return policy;
        }
        int index = policyCacheIndex(signature);
        if (policyCacheKeys[index] != signature) {
            return null;
        }
        policy = policyCacheValues[index];
        lastPolicySignature = signature;
        lastPolicy = policy;
        return policy;
    }

    /**
     * Stores a policy array in the recent direct-mapped cache.
     *
     * @param signature position signature
     * @param policy policy logits to cache
     */
    private void rememberPolicy(long signature, float[] policy) {
        lastPolicySignature = signature;
        lastPolicy = policy;
        int index = policyCacheIndex(signature);
        policyCacheKeys[index] = signature;
        policyCacheValues[index] = policy;
    }

    /**
     * Computes a direct-mapped cache index from a position signature.
     *
     * @param signature position signature
     * @return cache bucket index
     */
    private static int policyCacheIndex(long signature) {
        long mixed = signature ^ (signature >>> 32);
        return (int) mixed & (POLICY_CACHE_ENTRIES - 1);
    }

    /**
     * Returns one policy logit for a move.
     *
     * @param position position whose policy is cached
     * @param policy policy logits
     * @param move candidate move
     * @return finite logit, or negative infinity when unavailable
     */
    private static float policyLogit(Position position, float[] policy, short move) {
        if (move == Move.NO_MOVE) {
            return Float.NEGATIVE_INFINITY;
        }
        int index = PolicyEncoder.rawPolicyIndex(position, move);
        return index >= 0 && index < policy.length ? policy[index] : Float.NEGATIVE_INFINITY;
    }

    /**
     * Converts WDL probabilities into a centipawn-like score.
     *
     * <p>
     * The mapping treats win plus half draw as expected score and applies the
     * same logistic display transform used elsewhere in CRTK.
     * </p>
     *
     * @param wdl WDL probabilities in win/draw/loss order
     * @return centipawn-like score
     * @throws IllegalStateException if LC0 returns an invalid WDL vector
     */
    private static int wdlToCentipawns(float[] wdl) {
        if (wdl == null || wdl.length != 3) {
            throw new IllegalStateException("LC0 returned invalid WDL array");
        }
        double win = finiteNonNegative(wdl[0], "win");
        double draw = finiteNonNegative(wdl[1], "draw");
        double loss = finiteNonNegative(wdl[2], "loss");
        double sum = win + draw + loss;
        if (sum <= 0.0) {
            throw new IllegalStateException("LC0 returned WDL sum <= 0");
        }
        double expectedScore = (win + 0.5 * draw) / sum;
        double eps = 1e-6;
        double clamped = Math.max(eps, Math.min(1.0 - eps, expectedScore));
        return (int) Math.round(400.0 * Math.log10(clamped / (1.0 - clamped)));
    }

    /**
     * Validates one WDL component.
     *
     * @param value component value
     * @param label component label
     * @return validated component as a double
     * @throws IllegalStateException if the component is negative or non-finite
     */
    private static double finiteNonNegative(float value, String label) {
        if (!Float.isFinite(value) || value < 0.0f) {
            throw new IllegalStateException("LC0 returned invalid " + label + " WDL value");
        }
        return value;
    }
}
