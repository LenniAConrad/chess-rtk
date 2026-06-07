package application.gui.workbench.board;

import java.awt.Color;
import java.util.List;
import java.util.Objects;

/**
 * Annotation brush.
 *
 * @param name brush name
 * @param color brush color
 * @param lineWidth Chessground-style line width
 */
public record MarkupBrush(String name, Color color, int lineWidth) {

    /**
     * Default Chessground-style line width for Workbench annotation arrows.
     */
    public static final int DEFAULT_LINE_WIDTH = 10;

    /**
     * Custom brush name.
     */
    private static final String CUSTOM_NAME = "custom";

    /**
     * Immutable built-in annotation brushes in display order.
     *
     * <p>These saturated, semi-transparent overlays read cleanly as bold
     * arrows/circles over the wood board. They are fixed colors rather than
     * theme-resolved tokens, so an annotation's color stays stable in light and
     * dark themes.</p>
     */
    private static final List<MarkupBrush> PRESET_BRUSHES = List.of(
            named("green", new Color(0x21, 0x9E, 0x3C, 212)),
            named("red", new Color(0xCB, 0x37, 0x37, 212)),
            named("blue", new Color(0x30, 0x72, 0xE0, 212)),
            named("yellow", new Color(0xE8, 0x9B, 0x16, 212))
    );

    /**
     * Creates the default Workbench annotation brush.
     *
     * @return default green annotation brush
     */
    public static MarkupBrush defaultBrush() {
        return preset(0);
    }

    /**
     * Creates a Chessground-style gesture brush from the two annotation
     * modifier bits.
     *
     * @param index gesture index from zero to three
     * @return preset gesture brush
     */
    public static MarkupBrush forGesture(int index) {
        return preset(index);
    }

    /**
     * Returns the built-in annotation brushes in display order.
     *
     * @return immutable list of preset annotation brushes
     */
    public static List<MarkupBrush> presetBrushes() {
        return PRESET_BRUSHES;
    }

    /**
     * Creates a preset brush when the supplied color is one of the current
     * Workbench annotation colors; otherwise keeps the exact custom color.
     *
     * @param color display color
     * @return annotation brush
     */
    public static MarkupBrush forColor(Color color) {
        for (MarkupBrush brush : PRESET_BRUSHES) {
            if (sameColor(color, brush.color())) {
                return brush;
            }
        }
        return custom(color, DEFAULT_LINE_WIDTH);
    }

    /**
     * Creates an exact custom-color brush and clamps the line width to a visible
     * positive value.
     *
     * @param color exact display color
     * @param lineWidth requested line width
     * @return custom annotation brush
     */
    public static MarkupBrush custom(Color color, int lineWidth) {
        return new MarkupBrush(CUSTOM_NAME, color, Math.max(1, lineWidth));
    }

    /**
     * Returns whether this brush is one of the named built-in annotation
     * presets.
     *
     * @return true for named preset annotation brushes
     */
    public boolean isPreset() {
        return presetNamed(name) != null;
    }

    /**
     * Returns the display color for this brush, using the canonical color for
     * named presets and the exact stored color for custom brushes.
     *
     * @return display color
     */
    public Color displayColor() {
        MarkupBrush preset = presetNamed(name);
        return preset == null ? color : preset.color();
    }

    /**
     * Creates a built-in brush for a gesture index, defaulting invalid indices to
     * the primary green brush.
     *
     * @param index preset gesture index
     * @return built-in annotation brush
     */
    private static MarkupBrush preset(int index) {
        int safeIndex = index >= 0 && index < PRESET_BRUSHES.size() ? index : 0;
        return PRESET_BRUSHES.get(safeIndex);
    }

    /**
     * Creates a named built-in brush with the shared default line width.
     *
     * @param name built-in brush name
     * @param color brush color
     * @return named annotation brush
     */
    private static MarkupBrush named(String name, Color color) {
        return new MarkupBrush(name, color, DEFAULT_LINE_WIDTH);
    }

    /**
     * Returns the preset for a built-in brush name.
     *
     * @param brushName brush name
     * @return preset, or null when the brush is custom
     */
    private static MarkupBrush presetNamed(String brushName) {
        for (MarkupBrush preset : PRESET_BRUSHES) {
            if (Objects.equals(preset.name(), brushName)) {
                return preset;
            }
        }
        return null;
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
        if (isPreset() || other.isPreset()) {
            return Objects.equals(name, other.name);
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
