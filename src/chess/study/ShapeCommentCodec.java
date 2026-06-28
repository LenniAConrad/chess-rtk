package chess.study;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and renders Lichess-compatible PGN graphical annotations.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class ShapeCommentCodec {

    /**
     * PGN graphical annotation directive.
     */
    private static final Pattern DIRECTIVE = Pattern.compile("\\[%c([as]l)\\s+([^\\]]*)\\]");

    /**
     * Prevents instantiation.
     */
    private ShapeCommentCodec() {
        // utility
    }

    /**
     * Parses one PGN comment.
     *
     * @param comment raw PGN comment content
     * @return decoded comment
     */
    public static DecodedComment parse(String comment) {
        if (comment == null || comment.isBlank()) {
            return new DecodedComment("", List.of());
        }
        List<StudyShape> shapes = new ArrayList<>();
        Matcher matcher = DIRECTIVE.matcher(comment);
        while (matcher.find()) {
            boolean arrows = "al".equals(matcher.group(1));
            parseShapeList(matcher.group(2), arrows, shapes);
        }
        String text = matcher.replaceAll("").replaceAll("\\s{2,}", " ").trim();
        return new DecodedComment(text, shapes);
    }

    /**
     * Renders text and shapes as one PGN comment body.
     *
     * @param text preserved non-shape text
     * @param shapes graphical annotations
     * @return rendered comment content
     */
    public static String render(String text, List<StudyShape> shapes) {
        StringBuilder out = new StringBuilder();
        if (text != null && !text.isBlank()) {
            out.append(text.trim());
        }
        String circles = renderShapes(shapes, StudyShape.Type.CIRCLE);
        String arrows = renderShapes(shapes, StudyShape.Type.ARROW);
        appendDirective(out, "%csl", circles);
        appendDirective(out, "%cal", arrows);
        return out.toString().trim();
    }

    /**
     * Renders a decoded comment.
     *
     * @param comment decoded comment
     * @return rendered comment content
     */
    public static String render(DecodedComment comment) {
        return comment == null ? "" : render(comment.text(), comment.shapes());
    }

    /**
     * Parses a comma-separated shape list.
     *
     * @param text shape token text
     * @param arrows true for arrows
     * @param shapes destination
     */
    private static void parseShapeList(String text, boolean arrows, List<StudyShape> shapes) {
        if (text == null || text.isBlank()) {
            return;
        }
        for (String raw : text.split(",")) {
            String token = raw.trim();
            try {
                if (arrows && token.matches("[GRYBgryb][a-h][1-8][a-h][1-8]")) {
                    shapes.add(StudyShape.arrow(StudyShape.Brush.fromToken(token.charAt(0)),
                            token.substring(1, 3), token.substring(3, 5)));
                } else if (!arrows && token.matches("[GRYBgryb][a-h][1-8]")) {
                    shapes.add(StudyShape.circle(StudyShape.Brush.fromToken(token.charAt(0)),
                            token.substring(1, 3)));
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed shape tokens inside otherwise valid comments.
            }
        }
    }

    /**
     * Renders shapes of one type.
     *
     * @param shapes source shapes
     * @param type shape type
     * @return comma-separated token list
     */
    private static String renderShapes(List<StudyShape> shapes, StudyShape.Type type) {
        if (shapes == null || shapes.isEmpty()) {
            return "";
        }
        List<String> tokens = new ArrayList<>();
        for (StudyShape shape : shapes) {
            if (shape != null && shape.type() == type) {
                tokens.add(shape.token());
            }
        }
        return String.join(",", tokens);
    }

    /**
     * Appends one directive when it has tokens.
     *
     * @param out destination
     * @param name directive name
     * @param value token list
     */
    private static void appendDirective(StringBuilder out, String name, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!out.isEmpty()) {
            out.append(' ');
        }
        out.append('[').append(name).append(' ').append(value).append(']');
    }

    /**
     * Decoded PGN comment body.
     *
     * @param text non-shape text
     * @param shapes graphical annotations
     */
    public record DecodedComment(String text, List<StudyShape> shapes) {

        /**
         * Creates a decoded comment.
         *
         * @param text non-shape text
         * @param shapes graphical annotations
         */
        public DecodedComment {
            text = text == null ? "" : text;
            shapes = shapes == null ? List.of() : List.copyOf(shapes);
        }
    }
}
