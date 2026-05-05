package chess.puzzle.difficulty;

/**
 * Coarse objective inferred for a puzzle root.
 *
 * <p>
 * The value describes what the side to move is expected to achieve according
 * to the available engine analysis. It is intentionally coarse because the
 * downstream export only needs stable win/draw/unknown buckets.
 * </p>
 *
 * <p>
 * <strong>Warning:</strong> the goal is inferred from analysis thresholds, not
 * from a full proof of the game-theoretic result of the position.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public enum Goal {
    /**
     * The side to move should win or obtain a decisive advantage.
     *
     * <p>
     * This value is used when the primary variation clears the winning
     * centipawn gate.
     * </p>
     */
    WIN("win"),

    /**
     * The side to move should hold a draw or avoid a losing line.
     *
     * <p>
     * This value is used when the best line holds while lower-ranked candidate
     * lines are losing.
     * </p>
     */
    DRAW("draw"),

    /**
     * The available analysis does not match the standard win/draw puzzle gates.
     *
     * <p>
     * Unknown positions can still receive difficulty estimates, but their goal
     * tag should be treated as diagnostic metadata.
     * </p>
     */
    UNKNOWN("unknown");

    /**
     * Stable lowercase label used in tags.
     *
     * <p>
     * The label is the serialized form written into {@code META} tags and CSV
     * exports.
     * </p>
     */
    private final String label;

    /**
     * Creates a goal value.
     *
     * <p>
     * Enum values keep their serialized label explicit so future display names
     * can change without changing exported tags.
     * </p>
     *
     * @param label stable output label
     */
    Goal(String label) {
        this.label = label;
    }

    /**
     * Returns the stable tag label.
     *
     * <p>
     * Use this value for persisted metadata instead of {@link #name()}.
     * </p>
     *
     * @return lowercase goal label
     */
    public String label() {
        return label;
    }
}
