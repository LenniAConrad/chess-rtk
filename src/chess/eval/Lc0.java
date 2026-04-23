package chess.eval;

import java.io.IOException;
import java.nio.file.Path;

import chess.core.Position;

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
     * Loaded LC0 policy/value model used for every leaf evaluation.
     */
    private final chess.nn.lc0.Model model;

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
    }

    /**
     * Evaluates one position.
     *
     * @param position position to evaluate
     * @return centipawn-like score from LC0 WDL
     */
    @Override
    public int evaluate(Position position) {
        return wdlToCentipawns(model.predict(position).wdl());
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
        model.close();
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
