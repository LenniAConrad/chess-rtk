package chess.images.render;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Stroke;
import java.util.Locale;

/**
 * SVG serialization helpers shared by the board renderer.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class RenderSvgSupport {

    /**
     * Serif font family used for coordinate/detail overlays.
     */
    private static final String FONT_TIMES_NEW_ROMAN = "Times New Roman";

    /**
     * Shared SVG y attribute fragment.
     */
    private static final String SVG_Y_ATTRIBUTE = "\" y=\"";

    /**
     * Prevents construction.
     */
    private RenderSvgSupport() {
        // utility class
    }

    /**
     * Appends the body of an embedded SVG source, excluding its root and title.
     *
     * @param out SVG builder receiving the body
     * @param source embedded SVG source
     * @param indent indentation to prefix on each appended line
     */
    static void appendEmbeddedSvgBody(StringBuilder out, String source, String indent) {
        int start = source.indexOf("<g");
        int end = source.lastIndexOf("</svg>");
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("embedded SVG source does not contain a drawable group");
        }
        String body = source.substring(start, end).stripTrailing();
        String[] lines = body.split("\\R");
        for (String line : lines) {
            out.append(indent).append(line).append('\n');
        }
    }

    /**
     * Appends a simple SVG text element.
     *
     * @param svg SVG builder
     * @param indent indentation prefix
     * @param x text x position
     * @param y text y position
     * @param text text content
     */
    static void appendTextElement(StringBuilder svg, String indent, int x, int y, String text) {
        svg.append(indent).append("<text x=\"").append(x).append(SVG_Y_ATTRIBUTE).append(y)
                .append("\">").append(escapeText(text)).append("</text>\n");
    }

    /**
     * Appends SVG fill/stroke color and opacity attributes.
     *
     * @param svg SVG builder
     * @param attribute attribute name, such as {@code fill} or {@code stroke}
     * @param color color to serialize
     */
    static void appendColorAttribute(StringBuilder svg, String attribute, Color color) {
        Color value = color != null ? color : new Color(0, 0, 0, 0);
        svg.append(' ').append(attribute).append("=\"").append(hexColor(value)).append('"');
        if (value.getAlpha() < 255) {
            svg.append(' ').append(attribute).append("-opacity=\"");
            appendNumber(svg, value.getAlpha() / 255.0);
            svg.append('"');
        }
    }

    /**
     * Appends SVG font attributes.
     *
     * @param svg SVG builder
     * @param font font to serialize
     */
    static void appendFontAttributes(StringBuilder svg, Font font) {
        Font value = font != null ? font : new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        svg.append(" font-family=\"").append(escapeAttribute(svgFontFamily(value))).append("\" font-size=\"")
                .append(value.getSize()).append('"');
        if (value.isBold()) {
            svg.append(" font-weight=\"700\"");
        }
        if (value.isItalic()) {
            svg.append(" font-style=\"italic\"");
        }
    }

    /**
     * Converts an AWT font family to a CSS-compatible SVG font family list.
     *
     * @param font font to inspect
     * @return SVG font-family value
     */
    private static String svgFontFamily(Font font) {
        String name = font.getName();
        String family = font.getFamily(Locale.ROOT);
        if (FONT_TIMES_NEW_ROMAN.equalsIgnoreCase(name) || "Serif".equalsIgnoreCase(family)) {
            return FONT_TIMES_NEW_ROMAN + ", Times, serif";
        }
        if (Font.SANS_SERIF.equalsIgnoreCase(name) || "SansSerif".equalsIgnoreCase(family)) {
            return "Arial, Helvetica, sans-serif";
        }
        return family;
    }

    /**
     * Serializes a color as an SVG hex color without alpha.
     *
     * @param color color to serialize
     * @return SVG hex color
     */
    private static String hexColor(Color color) {
        return String.format(Locale.ROOT, "#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    /**
     * Returns the visible stroke width for SVG output.
     *
     * @param stroke AWT stroke
     * @return stroke width
     */
    static float strokeWidth(Stroke stroke) {
        if (stroke instanceof BasicStroke basicStroke) {
            return basicStroke.getLineWidth();
        }
        return 1.0f;
    }

    /**
     * Appends a compact decimal number suitable for SVG attributes.
     *
     * @param svg SVG builder
     * @param value numeric value
     */
    static void appendNumber(StringBuilder svg, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("non-finite SVG number: " + value);
        }
        double rounded = Math.rint(value);
        if (Math.abs(value - rounded) < 0.000001) {
            svg.append((long) rounded);
            return;
        }
        String text = String.format(Locale.ROOT, "%.6f", value);
        while (text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        if (text.endsWith(".")) {
            text = text.substring(0, text.length() - 1);
        }
        svg.append(text);
    }

    /**
     * Escapes text content for SVG.
     *
     * @param text raw text
     * @return escaped text
     */
    static String escapeText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    /**
     * Escapes attribute content for SVG.
     *
     * @param text raw attribute value
     * @return escaped attribute value
     */
    private static String escapeAttribute(String text) {
        return escapeText(text).replace("\"", "&quot;");
    }
}
