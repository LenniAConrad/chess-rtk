package chess.describe;

import java.util.Locale;

/**
 * Detail levels for deterministic position descriptions.
 */
public enum PositionDescriptionDetail {

    /**
     * One compact sentence.
     */
    BRIEF,

    /**
     * A few practical sentences.
     */
    NORMAL,

    /**
     * Topic-grouped text with source signals.
     */
    FULL;

    /**
     * Parses a CLI/UI detail label.
     *
     * @param value raw label
     * @return parsed detail level
     * @throws IllegalArgumentException when unsupported
     */
    public static PositionDescriptionDetail parse(String value) {
        if (value == null || value.isBlank()) {
            return NORMAL;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "brief", "short" -> BRIEF;
            case "normal", "default" -> NORMAL;
            case "full", "long", "verbose" -> FULL;
            default -> throw new IllegalArgumentException(
                    "Unsupported detail level: " + value + " (expected brief, normal, or full)");
        };
    }

    /**
     * Returns the stable lowercase label.
     *
     * @return label
     */
    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }
}
