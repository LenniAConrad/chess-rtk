package testing;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.util.Locale;
import javax.swing.Icon;
import javax.swing.SwingConstants;

/**
 * Formats Swing component diagnostics for {@link WorkbenchComponentDebug}.
 */
final class WorkbenchComponentDebugFormat {

    /**
     * Maximum text value included inline before it is abbreviated.
     */
    private static final int TEXT_SAMPLE_LIMIT = 160;

    /**
     * Prevents instantiation.
     */
    private WorkbenchComponentDebugFormat() {
        // utility
    }

    /**
     * Formats a rectangle.
     *
     * @param rect rectangle value
     * @return compact rectangle text
     */
    static String rect(Rectangle rect) {
        if (rect == null) {
            return "<null>";
        }
        return rect.x + "," + rect.y + " " + rect.width + "x" + rect.height;
    }

    /**
     * Formats a dimension.
     *
     * @param dim dimension value
     * @return compact dimension text
     */
    static String dim(Dimension dim) {
        if (dim == null) {
            return "<null>";
        }
        return dim.width + "x" + dim.height;
    }

    /**
     * Formats a color as RGBA hex.
     *
     * @param color display color
     * @return RGBA hex text
     */
    static String color(Color color) {
        if (color == null) {
            return "<null>";
        }
        return String.format(Locale.ROOT, "#%02X%02X%02X%02X",
                color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
    }

    /**
     * Formats a font style.
     *
     * @param font font used for measurement or drawing
     * @return compact font-style token
     */
    static String fontStyle(Font font) {
        if (font.isBold() && font.isItalic()) {
            return "bolditalic";
        }
        if (font.isBold()) {
            return "bold";
        }
        if (font.isItalic()) {
            return "italic";
        }
        return "plain";
    }

    /**
     * Formats an icon.
     *
     * @param icon icon image
     * @return icon class and size
     */
    static String icon(Icon icon) {
        if (icon == null) {
            return "<null>";
        }
        return icon.getClass().getName() + "[" + icon.getIconWidth() + "x" + icon.getIconHeight() + "]";
    }

    /**
     * Formats a Swing alignment constant.
     *
     * @param value Swing alignment constant
     * @return symbolic alignment name or numeric fallback
     */
    static String alignment(int value) {
        return switch (value) {
            case SwingConstants.LEFT -> "left";
            case SwingConstants.RIGHT -> "right";
            case SwingConstants.CENTER -> "center";
            case SwingConstants.TOP -> "top";
            case SwingConstants.BOTTOM -> "bottom";
            case SwingConstants.LEADING -> "leading";
            case SwingConstants.TRAILING -> "trailing";
            default -> String.valueOf(value);
        };
    }

    /**
     * Returns a class name or null marker.
     *
     * @param value object value
     * @return class name or null marker
     */
    static String className(Object value) {
        return value == null ? "<null>" : value.getClass().getName();
    }

    /**
     * Quotes and escapes a nullable value.
     *
     * @param value raw value
     * @return quoted value or null marker
     */
    static String quote(String value) {
        if (value == null) {
            return "<null>";
        }
        return "\"" + clean(value) + "\"";
    }

    /**
     * Returns a bounded one-line text sample.
     *
     * @param value raw value
     * @return abbreviated text sample
     */
    static String sample(String value) {
        if (value == null) {
            return null;
        }
        String clean = clean(value);
        if (clean.length() <= TEXT_SAMPLE_LIMIT) {
            return clean;
        }
        return clean.substring(0, TEXT_SAMPLE_LIMIT) + "...";
    }

    /**
     * Escapes control characters for one-line diagnostics.
     *
     * @param value raw value
     * @return escaped text
     */
    static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
