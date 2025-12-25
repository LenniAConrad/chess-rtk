package chess.tag;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import chess.classical.Wdl;
import chess.core.Position;
import chess.eval.Evaluator;
import chess.eval.Result;

/**
 * Computes a coarse difficulty estimate for a position.
 *
 * <p>
 * Difficulty is derived from the evaluator's WDL distribution (side-to-move perspective):
 * the worse the expected score, the higher the difficulty. The evaluator may use LC0
 * when available and falls back to a classical heuristic evaluator otherwise.
 * </p>
 *
 * @since 2025
 * @author Lennart A. Conrad
 */
public final class Difficulty {

    /**
     * Prevents instantiation; this class exposes only static helpers.
     */
    private Difficulty() {
        // utility class
    }

    /**
     * Returns a single difficulty tag for {@code position}.
     *
     * @param position position to evaluate (non-null)
     * @param evaluator evaluator instance to reuse (non-null)
     * @return immutable one-element list containing the difficulty tag
     */
    public static List<String> tags(Position position, Evaluator evaluator) {
        return List.of(tag(position, evaluator));
    }

    /**
     * Returns the difficulty tag for {@code position}.
     *
     * @param position position to evaluate (non-null)
     * @param evaluator evaluator instance to reuse (non-null)
     * @return tag like {@code "difficulty: easy (0.12)"}
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
     * Converts a WDL triple into an expected score in {@code [0,1]}.
     *
     * <p>
     * The returned value is from the side-to-move perspective: {@code win + 0.5 * draw}.
     * </p>
     *
     * @param wdl WDL distribution (non-null)
     * @return expected score in {@code [0,1]}
     */
    private static double expectedScore(Wdl wdl) {
        double win = wdl.win() / (double) Wdl.TOTAL;
        double draw = wdl.draw() / (double) Wdl.TOTAL;
        return win + 0.5 * draw;
    }

    /**
     * Converts an expected score into a normalized difficulty in {@code [0,1]} using a logarithmic
     * curve.
     *
     * <p>
     * We start with {@code linear = 1 - expectedScore} and then apply a normalized log curve
     * {@code log(1 + k * linear) / log(1 + k)} to spread values close to 0 while keeping
     * {@code 0 -> 0} and {@code 1 -> 1}. Larger {@code k} makes the curve stronger (more
     * aggressive) for small {@code linear} values.
     * </p>
     *
     * @param expectedScore expected score in {@code [0,1]}
     * @return difficulty in {@code [0,1]}
     */
    private static double logarithmicDifficulty(double expectedScore) {
        double linear = clamp01(1.0 - expectedScore);
        double k = 3.0;
        double difficulty = Math.log1p(k * linear) / Math.log1p(k);
        return clamp01(difficulty);
    }

    /**
     * Clamps {@code v} to the inclusive range {@code [0,1]}.
     *
     * @param v input value
     * @return clamped value in {@code [0,1]}
     */
    private static double clamp01(double v) {
        if (v < 0.0) {
            return 0.0;
        }
        if (v > 1.0) {
            return 1.0;
        }
        return v;
    }

    /**
     * Formats a probability-like value using two decimal digits.
     *
     * @param v value in {@code [0,1]}
     * @return formatted value such as {@code "0.42"}
     */
    private static String format01(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    /**
     * Maps a numeric difficulty into a coarse label.
     *
     * @param difficulty normalized difficulty in {@code [0,1]}
     * @return human-friendly label such as {@code "easy"} or {@code "very hard"}
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
