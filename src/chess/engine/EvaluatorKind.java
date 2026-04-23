package chess.engine;

import java.util.Locale;

/**
 * Built-in evaluator families.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public enum EvaluatorKind {

    /**
     * Classical handcrafted evaluator.
     */
    CLASSICAL("classical"),

    /**
     * Pure-Java NNUE evaluator.
     */
    NNUE("nnue"),

    /**
     * LC0 policy/value network evaluator.
     */
    LC0("lc0");

    /**
     * CLI label.
     */
    private final String label;

    /**
     * Creates an evaluator kind.
     *
     * @param label CLI label
     */
    EvaluatorKind(String label) {
        this.label = label;
    }

    /**
     * Returns the CLI label.
     *
     * @return label
     */
    public String label() {
        return label;
    }

    /**
     * Parses an evaluator label.
     *
     * @param value raw label
     * @return evaluator kind
     * @throws IllegalArgumentException when unsupported
     */
    public static EvaluatorKind parse(String value) {
        if (value == null || value.isBlank()) {
            return CLASSICAL;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "classical", "static" -> CLASSICAL;
            case "nnue" -> NNUE;
            case "lc0", "leela" -> LC0;
            default -> throw new IllegalArgumentException(
                    "Unsupported evaluator: " + value + " (expected classical, nnue, or lc0)");
        };
    }
}
