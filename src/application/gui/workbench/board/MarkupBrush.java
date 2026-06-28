package application.gui.workbench.board;

import java.awt.Color;
import java.util.List;
import java.util.Objects;

/**
 * Annotation brush.
 *
 * @param name brush name
 * @param color fill color
 * @param borderColor outline color
 * @param lineWidth Chessground-style fill line width
 * @param borderWidth outline width at a 64 px board-square scale
 * @param glyph chess annotation glyph for glyph markups
 * @param roundedRectangle true when rectangle markups use rounded corners
 */
public record MarkupBrush(String name, Color color, Color borderColor, int lineWidth, int borderWidth,
        String glyph, boolean roundedRectangle) {

    /**
     * Default Chessground-style line width for Workbench annotation arrows.
     */
    public static final int DEFAULT_LINE_WIDTH = 10;

    /**
     * Default annotation outline width at a 64 px board-square scale.
     */
    public static final int DEFAULT_BORDER_WIDTH = 4;

    /**
     * Default chess annotation glyph.
     */
    public static final String DEFAULT_GLYPH = "!!";

    /**
     * Built-in annotation palette tuned toward chess.com review colours while
     * staying readable over the Workbench wood board.
     */
    private static final int GREEN_RGB = 0x81B64C,
            RED_RGB = 0xD9514E,
            BLUE_RGB = 0x2D70B8,
            AMBER_RGB = 0xF0B13A;

    /**
     * Chess annotation glyphs exposed in the Draw rail.
     */
    private static final List<String> GLYPHS = List.of(
            "!!", "!", "!?", "?!", "?", "??", "□", "Zz", "+", "#", AnnotationGlyphs.DOUBLE_CHECK,
            "=", "∞", "±", "∓", "+=", "=+", "+-", "-+", "N",
            AnnotationGlyphs.DRAW_RESULT, AnnotationGlyphs.WHITE_CHECKMATED, AnnotationGlyphs.BLACK_CHECKMATED,
            "↑↑", "↑", "→", "⇆", "⊕", "=∞", "△", AnnotationGlyphs.COUNTERING,
            "MW", "Bk", "Pin", AnnotationGlyphs.FORK,
            AnnotationGlyphs.SKEWER, AnnotationGlyphs.DISCOVERED_ATTACK, AnnotationGlyphs.DOUBLE_ATTACK,
            AnnotationGlyphs.XRAY, AnnotationGlyphs.BATTERY,
            AnnotationGlyphs.UNDEFENDED, AnnotationGlyphs.CHECKABLE_KING);

    /**
     * Exclusive glyph group for position-evaluation annotations.
     */
    private static final String EVALUATION_GLYPH_GROUP = "evaluation";

    /**
     * Exclusive glyph group for game-result annotations.
     */
    private static final String RESULT_GLYPH_GROUP = "result";

    /**
     * Exclusive glyph group for move-quality assessments (a move has one).
     */
    private static final String MOVE_QUALITY_GLYPH_GROUP = "move-quality";

    /**
     * Exclusive glyph group for opening-theory annotations (book xor novelty).
     */
    private static final String OPENING_GLYPH_GROUP = "opening";

    /**
     * Exclusive glyph group for check/checkmate move markers (a move is one).
     */
    private static final String CHECK_GLYPH_GROUP = "check";

    /**
     * Custom brush name.
     */
    private static final String CUSTOM_NAME = "custom";

    /**
     * Immutable built-in annotation brushes in display order.
     *
     * <p>These presets are fully opaque and fixed rather than theme-resolved, so
     * annotation colors stay stable in light and dark themes.</p>
     */
    private static final List<MarkupBrush> PRESET_BRUSHES = List.of(
            named("green", presetColor(GREEN_RGB)),
            named("red", presetColor(RED_RGB)),
            named("blue", presetColor(BLUE_RGB)),
            named("yellow", presetColor(AMBER_RGB))
    );

    /**
     * Creates a legacy fill-only annotation brush with an automatic border.
     *
     * @param name brush name
     * @param color fill color
     * @param lineWidth stroke width in pixels
     */
    public MarkupBrush(String name, Color color, int lineWidth) {
        this(name, color, automaticBorder(color), lineWidth, DEFAULT_BORDER_WIDTH, DEFAULT_GLYPH, false);
    }

    /**
     * Creates a legacy annotation brush with square rectangle corners.
     *
     * @param name brush name
     * @param color fill color
     * @param borderColor outline color
     * @param lineWidth stroke width in pixels
     * @param borderWidth outline width
     * @param glyph chess annotation glyph
     */
    public MarkupBrush(String name, Color color, Color borderColor, int lineWidth, int borderWidth, String glyph) {
        this(name, color, borderColor, lineWidth, borderWidth, glyph, false);
    }

    /**
     * Normalizes brush state.
     *
     * @param name display name
     * @param color display color or side color
     * @param borderColor outline color
     * @param lineWidth stroke width
     * @param borderWidth outline stroke width
     * @param glyph optional marker glyph
     * @param roundedRectangle whether the brush draws rounded rectangles
     */
    public MarkupBrush {
        name = name == null || name.isBlank() ? CUSTOM_NAME : name;
        color = color == null ? presetColor(GREEN_RGB) : color;
        borderColor = borderColor == null ? automaticBorder(color) : borderColor;
        lineWidth = Math.max(1, lineWidth);
        borderWidth = Math.max(0, borderWidth);
        glyph = normalizeGlyph(glyph);
    }

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
     * Returns the selectable chess annotation glyphs.
     *
     * @return immutable glyph labels
     */
    public static List<String> glyphs() {
        return GLYPHS;
    }

    /**
     * Returns the mutually exclusive group for a glyph, if it belongs to one.
     *
     * @param glyph annotation glyph
     * @return group key, or null when the glyph can stack with other glyphs
     */
    public static String exclusiveGlyphGroup(String glyph) {
        String normalized = normalizeGlyph(glyph);
        return switch (normalized) {
            case "!!", "!", "!?", "?!", "?", "??", AnnotationGlyphs.MISSED_WIN -> MOVE_QUALITY_GLYPH_GROUP;
            case "=", "∞", "=∞", "±", "∓", "+=", "=+", AnnotationGlyphs.WHITE_WINNING, "-+" ->
                    EVALUATION_GLYPH_GROUP;
            case AnnotationGlyphs.DRAW_RESULT, AnnotationGlyphs.WHITE_CHECKMATED,
                    AnnotationGlyphs.BLACK_CHECKMATED -> RESULT_GLYPH_GROUP;
            case AnnotationGlyphs.NOVELTY, AnnotationGlyphs.BOOK_MOVE -> OPENING_GLYPH_GROUP;
            case AnnotationGlyphs.CHECK, AnnotationGlyphs.DOUBLE_CHECK, AnnotationGlyphs.MATE ->
                    CHECK_GLYPH_GROUP;
            default -> null;
        };
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
        return custom(color, automaticBorder(color), lineWidth, DEFAULT_GLYPH);
    }

    /**
     * Creates an exact custom-color brush and sets rectangle corner style.
     *
     * @param color exact display color
     * @param lineWidth requested line width
     * @param roundedRectangle true for rounded rectangle corners
     * @return custom annotation brush
     */
    public static MarkupBrush custom(Color color, int lineWidth, boolean roundedRectangle) {
        return custom(color, automaticBorder(color), lineWidth, DEFAULT_BORDER_WIDTH,
                DEFAULT_GLYPH, roundedRectangle);
    }

    /**
     * Creates an exact custom brush.
     *
     * @param color fill color
     * @param borderColor outline color
     * @param lineWidth stroke width in pixels
     * @param glyph chess annotation glyph
     * @return custom annotation brush
     */
    public static MarkupBrush custom(Color color, Color borderColor, int lineWidth, String glyph) {
        return custom(color, borderColor, lineWidth, DEFAULT_BORDER_WIDTH, glyph);
    }

    /**
     * Creates an exact custom brush.
     *
     * @param color fill color
     * @param borderColor outline color
     * @param lineWidth stroke width in pixels
     * @param borderWidth outline width
     * @param glyph chess annotation glyph
     * @return custom annotation brush
     */
    public static MarkupBrush custom(Color color, Color borderColor, int lineWidth, int borderWidth, String glyph) {
        return custom(color, borderColor, lineWidth, borderWidth, glyph, false);
    }

    /**
     * Creates an exact custom brush.
     *
     * @param color fill color
     * @param borderColor outline color
     * @param lineWidth stroke width in pixels
     * @param borderWidth outline width
     * @param glyph chess annotation glyph
     * @param roundedRectangle true for rounded rectangle corners
     * @return custom annotation brush
     */
    public static MarkupBrush custom(Color color, Color borderColor, int lineWidth, int borderWidth, String glyph,
            boolean roundedRectangle) {
        return new MarkupBrush(CUSTOM_NAME, color, borderColor, Math.max(1, lineWidth),
                Math.max(0, borderWidth), glyph, roundedRectangle);
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
     * Returns the display border color for this brush.
     *
     * @return border color
     */
    public Color displayBorderColor() {
        MarkupBrush preset = presetNamed(name);
        return preset == null ? borderColor : preset.borderColor();
    }

    /**
     * Returns the display border width for this brush.
     *
     * @return border width
     */
    public int displayBorderWidth() {
        MarkupBrush preset = presetNamed(name);
        return preset == null ? borderWidth : preset.borderWidth();
    }

    /**
     * Returns whether rectangle markups should use rounded corners.
     *
     * @return true for rounded rectangle corners
     */
    public boolean displayRoundedRectangle() {
        MarkupBrush preset = presetNamed(name);
        return preset == null ? roundedRectangle : preset.roundedRectangle();
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
        return new MarkupBrush(name, color, automaticBorder(color), DEFAULT_LINE_WIDTH,
                DEFAULT_BORDER_WIDTH, DEFAULT_GLYPH, false);
    }

    /**
     * Creates one built-in brush color from an opaque palette value.
     *
     * @param rgb opaque RGB value
     * @return opaque preset color
     */
    private static Color presetColor(int rgb) {
        return new Color(rgb);
    }

    /**
     * Returns the preset for a built-in brush name.
     *
     * @param brushName source brush name
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
        if (lineWidth != other.lineWidth || displayBorderWidth() != other.displayBorderWidth()
                || displayRoundedRectangle() != other.displayRoundedRectangle()
                || !Objects.equals(glyph, other.glyph)) {
            return false;
        }
        if (isPreset() || other.isPreset()) {
            return Objects.equals(name, other.name) && sameColor(displayBorderColor(), other.displayBorderColor());
        }
        return sameColor(color, other.color) && sameColor(borderColor, other.borderColor);
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

    /**
     * Picks a useful default outline color for a fill.
     *
     * @param fill fill color
     * @return contrasting border color
     */
    private static Color automaticBorder(Color fill) {
        Color color = fill == null ? Color.GREEN : fill;
        double luminance = (0.2126 * color.getRed()
                + 0.7152 * color.getGreen()
                + 0.0722 * color.getBlue()) / 255.0;
        int alpha = Math.max(150, Math.min(220, color.getAlpha() + 36));
        return luminance > 0.5 ? new Color(18, 18, 18, alpha) : new Color(245, 245, 245, alpha);
    }

    /**
     * Normalizes free-form glyph text to a compact board label.
     *
     * @param value raw glyph
     * @return glyph text
     */
    private static String normalizeGlyph(String value) {
        String text = value == null ? DEFAULT_GLYPH : value.trim();
        if (text.isEmpty()) {
            return DEFAULT_GLYPH;
        }
        return text.length() > 3 ? text.substring(0, 3) : text;
    }
}
