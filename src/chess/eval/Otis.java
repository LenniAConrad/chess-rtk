package chess.eval;

import chess.classical.Wdl;
import chess.core.Move;
import chess.core.Position;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * OTIS centipawn evaluator backed by the randomized policy/WDL placeholder
 * model.
 */
public final class Otis implements CentipawnEvaluator {

    /**
     * Maximum move-ordering bonus derived from OTIS policy.
     */
    private static final int POLICY_ORDERING_BONUS = 30_000;

    /**
     * Direct-mapped cache entry count.
     */
    private static final int POLICY_CACHE_ENTRIES = 128;

    /**
     * Empty policy sentinel.
     */
    private static final float[] NO_POLICY = new float[0];

    /**
     * Loaded OTIS model.
     */
    private final chess.nn.otis.Model model;

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
     * @param weights OTIS weights path, or null for the default placeholder
     * @throws IOException if the model cannot be loaded
     */
    public Otis(Path weights) throws IOException {
        this(chess.nn.otis.Model.load(weights == null ? chess.nn.otis.Model.DEFAULT_WEIGHTS : weights));
    }

    /**
     * Creates an evaluator from an already loaded model.
     *
     * @param model loaded model
     */
    public Otis(chess.nn.otis.Model model) {
        if (model == null) {
            throw new IllegalArgumentException("model == null");
        }
        this.model = model;
        Arrays.fill(policyCacheKeys, Long.MIN_VALUE);
    }

    /**
     * Evaluates one position and returns the full result.
     *
     * @param position position to evaluate
     * @return OTIS result
     */
    public Result result(Position position) {
        chess.nn.otis.Model.Prediction prediction = predict(position);
        Wdl wdl = toWdl(prediction.wdl());
        return new Result(Backend.OTIS, wdl, prediction.value(), null);
    }

    /**
     * Evaluates one position.
     *
     * @param position position to evaluate
     * @return centipawn-like score from OTIS WDL
     */
    @Override
    public int evaluate(Position position) {
        return wdlToCentipawns(predict(position).wdl());
    }

    /**
     * Preloads policy logits for later move ordering.
     *
     * @param position position whose policy should be cached
     */
    @Override
    public void prepareMoveOrdering(Position position) {
        if (position != null) {
            ensurePolicy(position);
        }
    }

    /**
     * Adds policy-derived ordering bonuses to legal moves.
     *
     * @param position source position
     * @param moves legal moves
     * @param scores mutable score array
     */
    @Override
    public void scoreMoves(Position position, short[] moves, int[] scores) {
        if (position == null || moves == null || scores == null || moves.length != scores.length) {
            return;
        }
        float[] policy = policy(position.signature());
        if (policy.length == 0) {
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
     * Returns the evaluator display name.
     *
     * @return evaluator name
     */
    @Override
    public String name() {
        return Kind.OTIS.label() + "(" + model.backend() + ", "
                + chess.nn.otis.Model.formatParameterCount(model.info().parameterCount()) + " params)";
    }

    /**
     * Releases model resources and clears cached policy state.
     */
    @Override
    public void close() {
        lastPolicy = null;
        model.close();
    }

    /**
     * Runs one prediction and remembers its policy logits.
     *
     * @param position position to evaluate
     * @return prediction
     */
    private chess.nn.otis.Model.Prediction predict(Position position) {
        chess.nn.otis.Model.Prediction prediction = model.predict(position);
        rememberPolicy(position.signature(), prediction.policy());
        return prediction;
    }

    /**
     * Ensures policy logits are cached.
     *
     * @param position source position
     * @return policy logits
     */
    private float[] ensurePolicy(Position position) {
        long signature = position.signature();
        float[] policy = policy(signature);
        if (policy.length > 0) {
            return policy;
        }
        return predict(position).policy();
    }

    /**
     * Returns cached policy.
     *
     * @param signature position signature
     * @return policy or empty sentinel
     */
    private float[] policy(long signature) {
        float[] policy = lastPolicy;
        if (policy != null && lastPolicySignature == signature) {
            return policy;
        }
        int index = policyCacheIndex(signature);
        if (policyCacheKeys[index] != signature) {
            return NO_POLICY;
        }
        policy = policyCacheValues[index];
        if (policy == null) {
            return NO_POLICY;
        }
        lastPolicySignature = signature;
        lastPolicy = policy;
        return policy;
    }

    /**
     * Stores policy logits in the direct-mapped cache.
     *
     * @param signature position signature
     * @param policy policy logits
     */
    private void rememberPolicy(long signature, float[] policy) {
        lastPolicySignature = signature;
        lastPolicy = policy;
        int index = policyCacheIndex(signature);
        policyCacheKeys[index] = signature;
        policyCacheValues[index] = policy;
    }

    /**
     * Computes cache index.
     *
     * @param signature position signature
     * @return cache index
     */
    private static int policyCacheIndex(long signature) {
        long mixed = signature ^ (signature >>> 32);
        return (int) mixed & (POLICY_CACHE_ENTRIES - 1);
    }

    /**
     * Returns one policy logit for a move.
     *
     * @param position source position
     * @param policy policy logits
     * @param move move
     * @return logit or negative infinity
     */
    private static float policyLogit(Position position, float[] policy, short move) {
        if (move == Move.NO_MOVE) {
            return Float.NEGATIVE_INFINITY;
        }
        int index = chess.nn.lc0.bt4.PolicyEncoder.compressedPolicyIndex(position, move);
        return index >= 0 && index < policy.length ? policy[index] : Float.NEGATIVE_INFINITY;
    }

    /**
     * Converts WDL probabilities into centipawns.
     *
     * @param wdl WDL probabilities
     * @return centipawn-like score
     */
    private static int wdlToCentipawns(float[] wdl) {
        Wdl normalized = toWdl(wdl);
        double win = normalized.win() / (double) Wdl.TOTAL;
        double draw = normalized.draw() / (double) Wdl.TOTAL;
        double expectedScore = win + 0.5d * draw;
        double eps = 1.0e-6d;
        double clamped = Math.max(eps, Math.min(1.0d - eps, expectedScore));
        return (int) Math.round(400.0d * Math.log10(clamped / (1.0d - clamped)));
    }

    /**
     * Converts raw WDL probabilities into CRTK's fixed-point WDL type.
     *
     * @param wdl raw probabilities
     * @return normalized WDL
     */
    private static Wdl toWdl(float[] wdl) {
        if (wdl == null || wdl.length != 3) {
            throw new IllegalStateException("OTIS returned invalid WDL array");
        }
        float win = finiteNonNegative(wdl[0], "win");
        float draw = finiteNonNegative(wdl[1], "draw");
        float loss = finiteNonNegative(wdl[2], "loss");
        double sum = (double) win + draw + loss;
        if (sum <= 0.0d) {
            throw new IllegalStateException("OTIS returned WDL sum <= 0");
        }
        double pWin = win / sum;
        double pDraw = draw / sum;
        double pLoss = loss / sum;

        int winBase = (int) Math.floor(pWin * Wdl.TOTAL);
        int drawBase = (int) Math.floor(pDraw * Wdl.TOTAL);
        int lossBase = (int) Math.floor(pLoss * Wdl.TOTAL);
        double winFrac = (pWin * Wdl.TOTAL) - winBase;
        double drawFrac = (pDraw * Wdl.TOTAL) - drawBase;
        double lossFrac = (pLoss * Wdl.TOTAL) - lossBase;

        int remainder = Wdl.TOTAL - (winBase + drawBase + lossBase);
        int w = winBase;
        int d = drawBase;
        int l = lossBase;
        for (int i = 0; i < remainder; i++) {
            if (winFrac >= drawFrac && winFrac >= lossFrac) {
                w++;
                winFrac = -1.0d;
            } else if (drawFrac >= lossFrac) {
                d++;
                drawFrac = -1.0d;
            } else {
                l++;
                lossFrac = -1.0d;
            }
        }
        return new Wdl((short) w, (short) d, (short) l);
    }

    /**
     * Validates one WDL component.
     *
     * @param value component
     * @param label component label
     * @return component
     */
    private static float finiteNonNegative(float value, String label) {
        if (!Float.isFinite(value) || value < 0.0f) {
            throw new IllegalStateException("OTIS returned invalid " + label + " WDL value");
        }
        return value;
    }
}
