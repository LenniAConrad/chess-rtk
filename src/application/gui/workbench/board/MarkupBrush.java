package application.gui.workbench.board;

import application.gui.workbench.ui.Theme;
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
     * Creates a Chessground-style gesture brush from the two annotation
     * modifier bits.
     *
     * @param index gesture index from zero to three
     * @return themed gesture brush
     */
    public static MarkupBrush forGesture(int index) {
        return switch (index) {
            case 1 -> new MarkupBrush("red", Theme.STATUS_ERROR_TEXT, 10);
            case 2 -> new MarkupBrush("blue", Theme.ACCENT, 10);
            case 3 -> new MarkupBrush("yellow", Theme.STATUS_WARNING_TEXT, 10);
            default -> new MarkupBrush("green", Theme.STATUS_SUCCESS_TEXT, 10);
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
        if (sameColor(color, Theme.STATUS_SUCCESS_TEXT)) {
            return new MarkupBrush("green", color, 10);
        }
        if (sameColor(color, Theme.STATUS_ERROR_TEXT)) {
            return new MarkupBrush("red", color, 10);
        }
        if (sameColor(color, Theme.ACCENT)) {
            return new MarkupBrush("blue", color, 10);
        }
        if (sameColor(color, Theme.STATUS_WARNING_TEXT)) {
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
        return switch (String.valueOf(name)) {
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
        return switch (String.valueOf(name)) {
            case "green" -> Theme.STATUS_SUCCESS_TEXT;
            case "red" -> Theme.STATUS_ERROR_TEXT;
            case "blue" -> Theme.ACCENT;
            case "yellow" -> Theme.STATUS_WARNING_TEXT;
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
