package chess.eval;

import java.util.Locale;

/**
 * In-process centipawn evaluator families.
 *
 * <p>
 * Values are intentionally small and CLI-oriented: each enum constant maps to
 * one evaluator implementation supported by the built-in searcher.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public enum Kind {

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
     * Stable lowercase token accepted by CLI flags and shown in engine output.
     */
    private final String label;

    /**
     * Creates an evaluator kind.
     *
     * @param label stable lowercase CLI label
     */
    Kind(String label) {
        this.label = label;
    }

    /**
     * Returns the CLI label.
     *
     * @return stable lowercase label
     */
    public String label() {
        return label;
    }

    /**
     * Parses an evaluator label.
     *
     * @param value raw label
     * @return evaluator kind
     * @throws IllegalArgumentException when the label is unsupported
     */
    public static Kind parse(String value) {
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
