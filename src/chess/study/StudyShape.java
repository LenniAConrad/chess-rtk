package chess.study;

import java.util.Locale;
import java.util.Objects;

/**
 * One Lichess-compatible graphical annotation shape.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public record StudyShape(Type type, Brush brush, String origin, String destination) {

    /**
     * Graphical shape type.
     */
    public enum Type {
        /**
         * Arrow from origin to destination.
         */
        ARROW,

        /**
         * Circle on the origin square.
         */
        CIRCLE
    }

    /**
     * Lichess graphical annotation brush.
     */
    public enum Brush {
        /**
         * Green annotation.
         */
        GREEN('G', "green"),

        /**
         * Red annotation.
         */
        RED('R', "red"),

        /**
         * Yellow annotation.
         */
        YELLOW('Y', "yellow"),

        /**
         * Blue annotation.
         */
        BLUE('B', "blue");

        /**
         * PGN comment prefix.
         */
        private final char token;

        /**
         * Display id.
         */
        private final String id;

        /**
         * Creates a brush.
         *
         * @param token PGN token
         * @param id display id
         */
        Brush(char token, String id) {
            this.token = token;
            this.id = id;
        }

        /**
         * Returns the PGN token.
         *
         * @return token
         */
        public char token() {
            return token;
        }

        /**
         * Returns the stable id.
         *
         * @return id
         */
        public String id() {
            return id;
        }

        /**
         * Parses a PGN brush token.
         *
         * @param token token character
         * @return brush
         */
        public static Brush fromToken(char token) {
            char normalized = Character.toUpperCase(token);
            for (Brush brush : values()) {
                if (brush.token == normalized) {
                    return brush;
                }
            }
            throw new IllegalArgumentException("unsupported study shape brush: " + token);
        }
    }

    /**
     * Creates and validates a shape.
     *
     * @param type shape type
     * @param brush brush
     * @param origin origin square
     * @param destination destination square
     */
    public StudyShape {
        Objects.requireNonNull(type, "type");
        brush = brush == null ? Brush.GREEN : brush;
        origin = normalizeSquare(origin);
        if (type == Type.ARROW) {
            destination = normalizeSquare(destination);
        } else {
            destination = null;
        }
    }

    /**
     * Creates an arrow.
     *
     * @param brush brush
     * @param origin origin square
     * @param destination destination square
     * @return arrow shape
     */
    public static StudyShape arrow(Brush brush, String origin, String destination) {
        return new StudyShape(Type.ARROW, brush, origin, destination);
    }

    /**
     * Creates a circle.
     *
     * @param brush brush
     * @param origin origin square
     * @return circle shape
     */
    public static StudyShape circle(Brush brush, String origin) {
        return new StudyShape(Type.CIRCLE, brush, origin, null);
    }

    /**
     * Returns the Lichess comment token.
     *
     * @return comment token body
     */
    public String token() {
        return type == Type.CIRCLE
                ? String.valueOf(brush.token()) + origin
                : String.valueOf(brush.token()) + origin + destination;
    }

    /**
     * Formats a concise display label.
     *
     * @return display label
     */
    @Override
    public String toString() {
        return type == Type.CIRCLE
                ? brush.id() + " circle " + origin
                : brush.id() + " arrow " + origin + destination;
    }

    /**
     * Normalizes one algebraic square.
     *
     * @param square source square
     * @return normalized square
     */
    private static String normalizeSquare(String square) {
        String value = square == null ? "" : square.trim().toLowerCase(Locale.ROOT);
        if (!value.matches("[a-h][1-8]")) {
            throw new IllegalArgumentException("invalid study shape square: " + square);
        }
        return value;
    }
}
