package application.gui.workbench.board;

import chess.core.Field;
import chess.study.ShapeCommentCodec;
import chess.study.StudyShape;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serializes board annotations to and from PGN comment commands.
 *
 * <p>Arrows and circles use the Lichess-compatible {@code [%cal ...]} and
 * {@code [%csl ...]} directives (the Draw preset colours map exactly onto
 * Lichess green/red/yellow/blue), so they interoperate with Lichess and
 * chess.com. Glyph badges and rectangles have no Lichess equivalent, so they use
 * crtk extensions {@code [%cgl ...]} (square/glyph/colour) and {@code [%crl ...]}
 * (from/to/colour) that preserve the exact colour and glyph token. Unknown
 * directives are ignored on read, and free-text in a comment is preserved.</p>
 *
 * @author Lennart A. Conrad
 */
public final class BoardMarkupComment {

    /**
     * crtk glyph-badge directive: {@code [%cgl <square>/<glyph>/<rrggbb>,...]}.
     */
    private static final Pattern GLYPH_DIRECTIVE = Pattern.compile("\\[%cgl\\s+([^\\]]*)\\]");

    /**
     * crtk rectangle directive: {@code [%crl <from>/<to>/<rrggbb>[/r],...]}.
     */
    private static final Pattern RECT_DIRECTIVE = Pattern.compile("\\[%crl\\s+([^\\]]*)\\]");

    /**
     * Lichess brush RGBs, matching the {@link MarkupBrush} green/red/yellow/blue presets.
     */
    private static final int[] BRUSH_RGB = {0x81_B6_4C, 0xD9_51_4E, 0xF0_B1_3A, 0x2D_70_B8};

    /**
     * Lichess brushes parallel to {@link #BRUSH_RGB}.
     */
    private static final StudyShape.Brush[] BRUSHES = {
            StudyShape.Brush.GREEN, StudyShape.Brush.RED, StudyShape.Brush.YELLOW, StudyShape.Brush.BLUE};

    /**
     * Dark glyph mark used on light badges.
     */
    private static final Color DARK_MARK = new Color(0x1F, 0x23, 0x28);

    /**
     * Prevents instantiation.
     */
    private BoardMarkupComment() {
        // utility
    }

    /**
     * Encodes board markups as a PGN comment body.
     *
     * @param markups board markups
     * @return PGN comment body with graphical directives
     */
    public static String encode(List<BoardMarkup> markups) {
        return encode("", markups);
    }

    /**
     * Encodes preserved text plus board markups as a PGN comment body.
     *
     * @param text free-text comment to preserve
     * @param markups board markups
     * @return PGN comment body with the text followed by graphical directives
     */
    public static String encode(String text, List<BoardMarkup> markups) {
        List<StudyShape> shapes = new ArrayList<>();
        StringBuilder glyphs = new StringBuilder();
        StringBuilder rects = new StringBuilder();
        for (BoardMarkup markup : markups == null ? List.<BoardMarkup>of() : markups) {
            if (markup.isArrow()) {
                shapes.add(StudyShape.arrow(nearestBrush(markup.brush().displayColor()),
                        Field.toString(markup.from()), Field.toString(markup.to())));
            } else if (markup.isCircle()) {
                shapes.add(StudyShape.circle(nearestBrush(markup.brush().displayColor()),
                        Field.toString(markup.from())));
            } else if (markup.isGlyph()) {
                appendToken(glyphs, Field.toString(markup.from()) + '/' + markup.brush().glyph()
                        + '/' + hex(markup.brush().displayColor()));
            } else if (markup.isRectangle()) {
                String token = Field.toString(markup.from()) + '/' + Field.toString(markup.to())
                        + '/' + hex(markup.brush().displayColor());
                appendToken(rects, markup.brush().displayRoundedRectangle() ? token + "/r" : token);
            }
        }
        StringBuilder out = new StringBuilder(ShapeCommentCodec.render(text, shapes));
        appendDirective(out, "%cgl", glyphs);
        appendDirective(out, "%crl", rects);
        return out.toString().trim();
    }

    /**
     * Decodes board markups from a PGN comment body.
     *
     * @param comment PGN comment body
     * @return board markups described by the comment's directives
     */
    public static List<BoardMarkup> decode(String comment) {
        List<BoardMarkup> markups = new ArrayList<>();
        if (comment == null || comment.isBlank()) {
            return markups;
        }
        for (StudyShape shape : ShapeCommentCodec.parse(comment).shapes()) {
            MarkupBrush brush = MarkupBrush.forColor(brushColor(shape.brush()));
            if (shape.type() == StudyShape.Type.ARROW) {
                markups.add(new BoardMarkup(BoardMarkupTool.ARROW, Field.toIndex(shape.origin()),
                        Field.toIndex(shape.destination()), brush));
            } else {
                markups.add(new BoardMarkup(BoardMarkupTool.CIRCLE, Field.toIndex(shape.origin()),
                        Field.NO_SQUARE, brush));
            }
        }
        decodeGlyphs(comment, markups);
        decodeRects(comment, markups);
        return markups;
    }

    /**
     * Returns the comment text with all graphical directives removed.
     *
     * @param comment PGN comment body
     * @return free text without directives
     */
    public static String plainText(String comment) {
        if (comment == null) {
            return "";
        }
        String text = ShapeCommentCodec.parse(comment).text();
        text = GLYPH_DIRECTIVE.matcher(text).replaceAll("");
        text = RECT_DIRECTIVE.matcher(text).replaceAll("");
        return text.replaceAll("\\s{2,}", " ").trim();
    }

    /**
     * Parses {@code [%cgl ...]} glyph badges into markups.
     *
     * @param comment PGN comment body
     * @param out destination markups
     */
    private static void decodeGlyphs(String comment, List<BoardMarkup> out) {
        Matcher matcher = GLYPH_DIRECTIVE.matcher(comment);
        while (matcher.find()) {
            for (String raw : matcher.group(1).split(",")) {
                String[] parts = raw.trim().split("/");
                if (parts.length < 3 || !isSquare(parts[0])) {
                    continue;
                }
                Color color = parseHex(parts[2]);
                if (color == null || parts[1].isEmpty()) {
                    continue;
                }
                MarkupBrush brush = MarkupBrush.custom(color, markColor(color),
                        MarkupBrush.DEFAULT_LINE_WIDTH, MarkupBrush.DEFAULT_BORDER_WIDTH, parts[1]);
                out.add(new BoardMarkup(BoardMarkupTool.GLYPH, Field.toIndex(parts[0]), Field.NO_SQUARE, brush));
            }
        }
    }

    /**
     * Parses {@code [%crl ...]} rectangles into markups.
     *
     * @param comment PGN comment body
     * @param out destination markups
     */
    private static void decodeRects(String comment, List<BoardMarkup> out) {
        Matcher matcher = RECT_DIRECTIVE.matcher(comment);
        while (matcher.find()) {
            for (String raw : matcher.group(1).split(",")) {
                String[] parts = raw.trim().split("/");
                if (parts.length < 3 || !isSquare(parts[0]) || !isSquare(parts[1])) {
                    continue;
                }
                Color color = parseHex(parts[2]);
                if (color == null) {
                    continue;
                }
                boolean rounded = parts.length > 3 && "r".equalsIgnoreCase(parts[3]);
                MarkupBrush brush = MarkupBrush.custom(color, MarkupBrush.DEFAULT_LINE_WIDTH, rounded);
                out.add(new BoardMarkup(BoardMarkupTool.RECTANGLE, Field.toIndex(parts[0]),
                        Field.toIndex(parts[1]), brush));
            }
        }
    }

    /**
     * Returns the Lichess brush nearest a colour by RGB distance.
     *
     * @param color source colour
     * @return nearest brush
     */
    private static StudyShape.Brush nearestBrush(Color color) {
        int best = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < BRUSH_RGB.length; i++) {
            long distance = colorDistance(color, BRUSH_RGB[i]);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return BRUSHES[best];
    }

    /**
     * Returns the canonical colour for a Lichess brush.
     *
     * @param brush brush
     * @return brush colour
     */
    private static Color brushColor(StudyShape.Brush brush) {
        for (int i = 0; i < BRUSHES.length; i++) {
            if (BRUSHES[i] == brush) {
                return new Color(BRUSH_RGB[i]);
            }
        }
        return new Color(BRUSH_RGB[0]);
    }

    /**
     * Returns the squared RGB distance between a colour and a packed RGB.
     *
     * @param color colour
     * @param rgb packed RGB
     * @return squared distance
     */
    private static long colorDistance(Color color, int rgb) {
        int dr = color.getRed() - ((rgb >> 16) & 0xFF);
        int dg = color.getGreen() - ((rgb >> 8) & 0xFF);
        int db = color.getBlue() - (rgb & 0xFF);
        return (long) dr * dr + (long) dg * dg + (long) db * db;
    }

    /**
     * Returns a contrasting glyph mark colour for a badge fill.
     *
     * @param fill badge fill colour
     * @return white on dark fills, dark on light fills
     */
    private static Color markColor(Color fill) {
        double luminance = (0.2126 * fill.getRed() + 0.7152 * fill.getGreen() + 0.0722 * fill.getBlue()) / 255.0;
        return luminance > 0.6 ? DARK_MARK : Color.WHITE;
    }

    /**
     * Formats a colour as a six-digit hexadecimal string.
     *
     * @param color colour
     * @return uppercase RRGGBB
     */
    private static String hex(Color color) {
        return String.format("%06X", color.getRGB() & 0xFF_FF_FF);
    }

    /**
     * Parses a six-digit hexadecimal colour.
     *
     * @param value RRGGBB text
     * @return colour, or {@code null} when malformed
     */
    private static Color parseHex(String value) {
        if (value == null || !value.matches("(?i)[0-9a-f]{6}")) {
            return null;
        }
        return new Color(Integer.parseInt(value, 16));
    }

    /**
     * Returns whether a token is an algebraic square.
     *
     * @param value token
     * @return true for an a1..h8 square
     */
    private static boolean isSquare(String value) {
        return value != null && value.matches("[a-h][1-8]");
    }

    /**
     * Appends a comma-separated token to a directive body.
     *
     * @param body directive body
     * @param token token to append
     */
    private static void appendToken(StringBuilder body, String token) {
        if (body.length() > 0) {
            body.append(',');
        }
        body.append(token);
    }

    /**
     * Appends a directive to the comment when it has tokens.
     *
     * @param out comment builder
     * @param name directive name
     * @param body directive body
     */
    private static void appendDirective(StringBuilder out, String name, StringBuilder body) {
        if (body.length() == 0) {
            return;
        }
        if (out.length() > 0) {
            out.append(' ');
        }
        out.append('[').append(name).append(' ').append(body).append(']');
    }
}
