package application.gui.workbench.board;

import java.awt.Color;

/**
 * Annotation brush.
 *
 * @param name brush name
 * @param color brush color
 * @param lineWidth Chessground-style line width
 */
public record MarkupBrush(String name, Color color, int lineWidth) {

    /**
     * Dedicated annotation colours — saturated, semi-transparent overlays
     * (chessground-style) that read cleanly as bold arrows/circles over the wood
     * board. These replace the pale {@code STATUS_*_TEXT} tokens previously
     * reused here, which were tuned for legible text rather than board markup and
     * washed out as arrows. Fixed (not theme-resolved) so an arrow's colour stays
     * the same in light and dark, matching every other chess UI.
     */
    private static final Color GREEN = new Color(0x21, 0x9E, 0x3C, 212);

    /**
     * Annotation red.
     */
    private static final Color RED = new Color(0xCB, 0x37, 0x37, 212);

    /**
     * Annotation blue.
     */
    private static final Color BLUE = new Color(0x30, 0x72, 0xE0, 212);

    /**
     * Annotation yellow.
     */
    private static final Color YELLOW = new Color(0xE8, 0x9B, 0x16, 212);

    /**
     * Creates a Chessground-style gesture brush from the two annotation
     * modifier bits.
     *
     * @param index gesture index from zero to three
     * @return themed gesture brush
     */
    public static MarkupBrush forGesture(int index) {
        return switch (index) {
            case 1 -> new MarkupBrush("red", RED, 10);
            case 2 -> new MarkupBrush("blue", BLUE, 10);
            case 3 -> new MarkupBrush("yellow", YELLOW, 10);
            default -> new MarkupBrush("green", GREEN, 10);
        };
    }

    /**
     * Creates a themed brush when the supplied color is one of the current
     * workbench annotation colors; otherwise keeps the exact custom color.
     *
     * @param color display color
     * @return annotation brush
     */
    public static MarkupBrush forThemeColor(Color color) {
        if (sameColor(color, GREEN)) {
            return new MarkupBrush("green", color, 10);
        }
        if (sameColor(color, RED)) {
            return new MarkupBrush("red", color, 10);
        }
        if (sameColor(color, BLUE)) {
            return new MarkupBrush("blue", color, 10);
        }
        if (sameColor(color, YELLOW)) {
            return new MarkupBrush("yellow", color, 10);
        }
        return new MarkupBrush("custom", color, 10);
    }

    /**
     * Returns whether this brush resolves its color from the active workbench
     * theme.
     *
     * @return true for named workbench annotation brushes
     */
    public boolean isThemed() {
        return switch (name) {
            case "green", "red", "blue", "yellow" -> true;
            default -> false;
        };
    }

    /**
     * Resolves the brush color for the current workbench theme.
     *
     * @return active brush color
     */
    public Color themedColor() {
        return switch (name) {
            case "green" -> GREEN;
            case "red" -> RED;
            case "blue" -> BLUE;
            case "yellow" -> YELLOW;
            default -> color;
        };
    }

    /**
     * Returns whether this brush represents the same user annotation brush as
     * another brush.
     *
     * @param other other brush
     * @return true when both brushes match
     */
    public boolean matches(MarkupBrush other) {
        if (other == null) {
            return false;
        }
        if (isThemed() || other.isThemed()) {
            return java.util.Objects.equals(name, other.name);
        }
        return sameColor(color, other.color);
    }

    /**
     * Returns whether two colors have the same ARGB value.
     *
     * @param first first color
     * @param second second color
     * @return true when both colors match
     */
    private static boolean sameColor(Color first, Color second) {
        return first != null && second != null && first.getRGB() == second.getRGB();
    }
}
