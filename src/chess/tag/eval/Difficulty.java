package chess.tag.eval;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import chess.classical.Wdl;
import chess.core.Position;
import chess.eval.Evaluator;
import chess.eval.Result;
import utility.Numbers;

/**
 * Converts an engine evaluation into a human-readable difficulty tag.
 * <p>
 * The difficulty score is derived from the expected score implied by the
 * win-draw-loss result and then mapped into a stable textual label.
 * </p>
 * @author Lennart A. Conrad
 * @since 2026
 */
public final class Difficulty {

    /**
     * Prevents instantiation of this utility class.
     */
    private Difficulty() {
        // utility class
    }

    /**
     * Returns the difficulty tag as a single-item list.
     *
     * @param position the position to evaluate
     * @param evaluator the evaluator used to compute the underlying result
     * @return a one-element list containing the formatted difficulty tag
     */
    public static List<String> tags(Position position, Evaluator evaluator) {
        return List.of(tag(position, evaluator));
    }

    /**
     * Computes a difficulty tag from a position and evaluator.
     * <p>
     * The output is formatted as {@code difficulty: <label> (<score>)} where the
     * score is normalized to two decimal places.
     * </p>
     *
     * @param position the position to evaluate
     * @param evaluator the evaluator used to compute the result
     * @return the formatted difficulty tag
     */
    public static String tag(Position position, Evaluator evaluator) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(evaluator, "evaluator");

        Result result = evaluator.evaluate(position);
        double expectedScore = expectedScore(result.wdl());
        double difficulty = logarithmicDifficulty(expectedScore);
        String label = labelFor(difficulty);
        return "difficulty: " + label + " (" + format01(difficulty) + ")";
    }

    /**
     * Converts a WDL distribution into an expected score.
     *
     * @param wdl the win-draw-loss distribution to convert
     * @return the expected score on the {@code [0,1]} interval
     */
    private static double expectedScore(Wdl wdl) {
        double win = wdl.win() / (double) Wdl.TOTAL;
        double draw = wdl.draw() / (double) Wdl.TOTAL;
        return win + 0.5 * draw;
    }

    /**
     * Converts an expected score into a normalized logarithmic difficulty value.
     *
     * @param expectedScore the expected score in the {@code [0,1]} interval
     * @return the normalized difficulty score
     */
    private static double logarithmicDifficulty(double expectedScore) {
        double linear = Numbers.clamp01(1.0 - expectedScore);
        double k = 3.0;
        double difficulty = Math.log1p(k * linear) / Math.log1p(k);
        return Numbers.clamp01(difficulty);
    }

    /**
     * Formats a normalized score with two decimal places.
     *
     * @param v the value to format
     * @return the formatted decimal string
     */
    private static String format01(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    /**
     * Maps a normalized difficulty score to a coarse human label.
     *
     * @param difficulty the normalized difficulty value
     * @return a label ranging from very easy to very hard
     */
    private static String labelFor(double difficulty) {
        if (difficulty <= 0.20) {
            return "very easy";
        }
        if (difficulty <= 0.35) {
            return "easy";
        }
        if (difficulty <= 0.55) {
            return "medium";
        }
        if (difficulty <= 0.70) {
            return "hard";
        }
        return "very hard";
    }
}
