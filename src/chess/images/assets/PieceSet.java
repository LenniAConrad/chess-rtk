package chess.images.assets;

/**
 * Selectable chess piece artwork themes.
 *
 * <p>Each value names a distinct, original piece set rendered by {@link Shapes}.
 * {@link #SLATE} is the project's house set (two-tone charcoal silhouettes);
 * {@link #OUTLINE} is a clean line-art set derived from the same geometry but
 * stroked rather than filled. All sets are original artwork.</p>
 */
public enum PieceSet {

    /**
     * Two-tone charcoal house set (the original default).
     */
    SLATE("Slate"),

    /**
     * Minimalist single-weight line-art set.
     */
    OUTLINE("Outline"),

    /**
     * Original classic Staunton-style set (procedurally drawn, not derived from
     * any third-party artwork).
     */
    STAUNTON("Staunton");

    /**
     * Human-readable label shown in the piece-set selector.
     */
    private final String label;

    /**
     * Creates a piece set with a display label.
     *
     * @param label selector label
     */
    PieceSet(String label) {
        this.label = label;
    }

    /**
     * Returns the human-readable selector label.
     *
     * @return display label
     */
    public String label() {
        return label;
    }

    /**
     * Returns the set whose label matches the supplied text, or {@link #SLATE}
     * when none matches.
     *
     * @param text candidate label or name (case-insensitive)
     * @return matching set, or {@link #SLATE}
     */
    public static PieceSet fromLabel(String text) {
        if (text != null) {
            for (PieceSet set : values()) {
                if (set.label.equalsIgnoreCase(text.trim()) || set.name().equalsIgnoreCase(text.trim())) {
                    return set;
                }
            }
        }
        return SLATE;
    }
}
