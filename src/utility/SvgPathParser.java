package utility;

import java.awt.geom.Path2D;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the full SVG path-data grammar into a Java2D {@link Path2D.Double}.
 *
 * <p>Supports every path command: {@code M/m L/l H/h V/v C/c S/s Q/q T/t A/a
 * Z/z}, including relative coordinates, smooth-curve control-point reflection,
 * and elliptical arcs (converted to cubic Bezier segments). This lets vector
 * markers keep a single SVG-string source of truth that is shared between the
 * on-screen Java2D painter and the SVG exporter, instead of hand-translating
 * each path into builder calls.</p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class SvgPathParser {

    /**
     * Pattern used to scan one SVG numeric token.
     */
    private static final Pattern NUMBER_PATTERN = Pattern.compile(
            "[+-]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?");

    /**
     * Maximum sweep, in radians, approximated by a single cubic arc segment.
     */
    private static final double MAX_ARC_SEGMENT = Math.PI / 2.0;

    /**
     * Prevents instantiation.
     */
    private SvgPathParser() {
        // utility
    }

    /**
     * Parses SVG path data using the non-zero winding rule.
     *
     * @param data SVG path data string
     * @return parsed path (possibly empty)
     */
    public static Path2D.Double parse(String data) {
        return parse(data, Path2D.WIND_NON_ZERO);
    }

    /**
     * Parses SVG path data with an explicit winding rule.
     *
     * @param data SVG path data string
     * @param windingRule {@link Path2D} winding rule
     * @return parsed path (possibly empty)
     */
    public static Path2D.Double parse(String data, int windingRule) {
        Path2D.Double path = new Path2D.Double(windingRule);
        if (data == null || data.isBlank()) {
            return path;
        }
        Cursor cursor = new Cursor(data);
        while (cursor.skipSeparators()) {
            char ch = cursor.peek();
            if (isCommand(ch)) {
                cursor.command = ch;
                cursor.index++;
            } else if (cursor.command == 0) {
                throw new IllegalArgumentException("SVG path missing command at index " + cursor.index);
            }
            dispatch(cursor, path);
        }
        return path;
    }

    /**
     * Executes one path command using the cursor's current command letter.
     *
     * @param cursor parser cursor
     * @param path target path
     */
    private static void dispatch(Cursor cursor, Path2D.Double path) {
        switch (cursor.command) {
            case 'M', 'm' -> moveTo(cursor, path);
            case 'L', 'l' -> lineTo(cursor, path);
            case 'H', 'h' -> horizontalTo(cursor, path);
            case 'V', 'v' -> verticalTo(cursor, path);
            case 'C', 'c' -> cubicTo(cursor, path);
            case 'S', 's' -> smoothCubicTo(cursor, path);
            case 'Q', 'q' -> quadTo(cursor, path);
            case 'T', 't' -> smoothQuadTo(cursor, path);
            case 'A', 'a' -> arcTo(cursor, path);
            case 'Z', 'z' -> closePath(cursor, path);
            default -> throw new IllegalArgumentException("Unsupported SVG path command: " + cursor.command);
        }
    }

    /**
     * Parses a move-to command and any implicit following line-to pairs.
     *
     * @param cursor parser cursor
     * @param path target path
     */
    private static void moveTo(Cursor cursor, Path2D.Double path) {
        boolean relative = cursor.command == 'm';
        double x = cursor.number() + (relative ? cursor.cx : 0);
        double y = cursor.number() + (relative ? cursor.cy : 0);
        cursor.cx = x;
        cursor.cy = y;
        cursor.sx = x;
        cursor.sy = y;
        path.moveTo(x, y);
        cursor.clearReflection();
        cursor.command = relative ? 'l' : 'L';
        while (cursor.hasNumber()) {
            lineSegment(cursor, path, cursor.command == 'l');
        }
    }

    /**
     * Parses one or more line-to coordinate pairs.
     *
     * @param cursor parser cursor
     * @param path target path
     */
    private static void lineTo(Cursor cursor, Path2D.Double path) {
        boolean relative = cursor.command == 'l';
        while (cursor.hasNumber()) {
            lineSegment(cursor, path, relative);
        }
    }

    /**
     * Appends one line segment.
     *
     * @param cursor parser cursor
     * @param path target path
     * @param relative whether the coordinates are relative
     */
    private static void lineSegment(Cursor cursor, Path2D.Double path, boolean relative) {
        double x = cursor.number() + (relative ? cursor.cx : 0);
        double y = cursor.number() + (relative ? cursor.cy : 0);
        cursor.cx = x;
        cursor.cy = y;
        path.lineTo(x, y);
        cursor.clearReflection();
    }

    /**
     * Parses one or more horizontal line-to coordinates.
     *
     * @param cursor parser cursor
     * @param path target path
     */
    private static void horizontalTo(Cursor cursor, Path2D.Double path) {
        boolean relative = cursor.command == 'h';
        while (cursor.hasNumber()) {
            double x = cursor.number() + (relative ? cursor.cx : 0);
            cursor.cx = x;
            path.lineTo(x, cursor.cy);
            cursor.clearReflection();
        }
    }

    /**
     * Parses one or more vertical line-to coordinates.
     *
     * @param cursor parser cursor
     * @param path target path
     */
    private static void verticalTo(Cursor cursor, Path2D.Double path) {
        boolean relative = cursor.command == 'v';
        while (cursor.hasNumber()) {
            double y = cursor.number() + (relative ? cursor.cy : 0);
            cursor.cy = y;
            path.lineTo(cursor.cx, y);
            cursor.clearReflection();
        }
    }

    /**
     * Parses one or more cubic curve-to segments.
     *
     * @param cursor parser cursor
     * @param path target path
     */
    private static void cubicTo(Cursor cursor, Path2D.Double path) {
        boolean relative = cursor.command == 'c';
        while (cursor.hasNumber()) {
            double ox = relative ? cursor.cx : 0;
            double oy = relative ? cursor.cy : 0;
            double x1 = cursor.number() + ox;
            double y1 = cursor.number() + oy;
            double x2 = cursor.number() + ox;
            double y2 = cursor.number() + oy;
            double x = cursor.number() + ox;
            double y = cursor.number() + oy;
            path.curveTo(x1, y1, x2, y2, x, y);
            cursor.setCubicReflection(x2, y2, x, y);
        }
    }

    /**
     * Parses one or more smooth cubic curve-to segments.
     *
     * @param cursor parser cursor
     * @param path target path
     */
    private static void smoothCubicTo(Cursor cursor, Path2D.Double path) {
        boolean relative = cursor.command == 's';
        while (cursor.hasNumber()) {
            double ox = relative ? cursor.cx : 0;
            double oy = relative ? cursor.cy : 0;
            double x1 = cursor.reflectedCubicX();
            double y1 = cursor.reflectedCubicY();
            double x2 = cursor.number() + ox;
            double y2 = cursor.number() + oy;
            double x = cursor.number() + ox;
            double y = cursor.number() + oy;
            path.curveTo(x1, y1, x2, y2, x, y);
            cursor.setCubicReflection(x2, y2, x, y);
        }
    }

    /**
     * Parses one or more quadratic curve-to segments.
     *
     * @param cursor parser cursor
     * @param path target path
     */
    private static void quadTo(Cursor cursor, Path2D.Double path) {
        boolean relative = cursor.command == 'q';
        while (cursor.hasNumber()) {
            double ox = relative ? cursor.cx : 0;
            double oy = relative ? cursor.cy : 0;
            double x1 = cursor.number() + ox;
            double y1 = cursor.number() + oy;
            double x = cursor.number() + ox;
            double y = cursor.number() + oy;
            path.quadTo(x1, y1, x, y);
            cursor.setQuadReflection(x1, y1, x, y);
        }
    }

    /**
     * Parses one or more smooth quadratic curve-to segments.
     *
     * @param cursor parser cursor
     * @param path target path
     */
    private static void smoothQuadTo(Cursor cursor, Path2D.Double path) {
        boolean relative = cursor.command == 't';
        while (cursor.hasNumber()) {
            double x1 = cursor.reflectedQuadX();
            double y1 = cursor.reflectedQuadY();
            double x = cursor.number() + (relative ? cursor.cx : 0);
            double y = cursor.number() + (relative ? cursor.cy : 0);
            path.quadTo(x1, y1, x, y);
            cursor.setQuadReflection(x1, y1, x, y);
        }
    }

    /**
     * Parses one or more elliptical arc segments.
     *
     * @param cursor parser cursor
     * @param path target path
     */
    private static void arcTo(Cursor cursor, Path2D.Double path) {
        boolean relative = cursor.command == 'a';
        while (cursor.hasNumber()) {
            double rx = cursor.number();
            double ry = cursor.number();
            double rotation = cursor.number();
            boolean largeArc = cursor.flag();
            boolean sweep = cursor.flag();
            double x = cursor.number() + (relative ? cursor.cx : 0);
            double y = cursor.number() + (relative ? cursor.cy : 0);
            appendArc(path, cursor.cx, cursor.cy, rx, ry, rotation, largeArc, sweep, x, y);
            cursor.cx = x;
            cursor.cy = y;
            cursor.clearReflection();
        }
    }

    /**
     * Closes the current subpath and restores the subpath start point.
     *
     * @param cursor parser cursor
     * @param path target path
     */
    private static void closePath(Cursor cursor, Path2D.Double path) {
        path.closePath();
        cursor.cx = cursor.sx;
        cursor.cy = cursor.sy;
        cursor.clearReflection();
    }

    /**
     * Appends an elliptical arc as one or more cubic Bezier segments using the
     * SVG endpoint-to-center parameterization (SVG spec appendix F.6).
     *
     * @param path target path
     * @param x1 arc start x
     * @param y1 arc start y
     * @param rxIn x radius
     * @param ryIn y radius
     * @param rotationDeg x-axis rotation in degrees
     * @param largeArc large-arc flag
     * @param sweep sweep flag
     * @param x2 arc end x
     * @param y2 arc end y
     */
    private static void appendArc(Path2D.Double path, double x1, double y1, double rxIn, double ryIn,
            double rotationDeg, boolean largeArc, boolean sweep, double x2, double y2) {
        if (rxIn == 0 || ryIn == 0) {
            path.lineTo(x2, y2);
            return;
        }
        double rx = Math.abs(rxIn);
        double ry = Math.abs(ryIn);
        double phi = Math.toRadians(rotationDeg % 360.0);
        double cosPhi = Math.cos(phi);
        double sinPhi = Math.sin(phi);

        double dx = (x1 - x2) / 2.0;
        double dy = (y1 - y2) / 2.0;
        double x1p = cosPhi * dx + sinPhi * dy;
        double y1p = -sinPhi * dx + cosPhi * dy;

        double lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry);
        if (lambda > 1.0) {
            double scale = Math.sqrt(lambda);
            rx *= scale;
            ry *= scale;
        }

        double rx2 = rx * rx;
        double ry2 = ry * ry;
        double x1p2 = x1p * x1p;
        double y1p2 = y1p * y1p;
        double numerator = Math.max(0.0, rx2 * ry2 - rx2 * y1p2 - ry2 * x1p2);
        double denominator = rx2 * y1p2 + ry2 * x1p2;
        double coefficient = denominator == 0 ? 0 : Math.sqrt(numerator / denominator);
        if (largeArc == sweep) {
            coefficient = -coefficient;
        }
        double cxp = coefficient * (rx * y1p / ry);
        double cyp = coefficient * (-ry * x1p / rx);

        double cx = cosPhi * cxp - sinPhi * cyp + (x1 + x2) / 2.0;
        double cy = sinPhi * cxp + cosPhi * cyp + (y1 + y2) / 2.0;

        double startAngle = angle(1.0, 0.0, (x1p - cxp) / rx, (y1p - cyp) / ry);
        double sweepAngle = angle((x1p - cxp) / rx, (y1p - cyp) / ry,
                (-x1p - cxp) / rx, (-y1p - cyp) / ry);
        if (!sweep && sweepAngle > 0) {
            sweepAngle -= 2.0 * Math.PI;
        } else if (sweep && sweepAngle < 0) {
            sweepAngle += 2.0 * Math.PI;
        }

        int segments = Math.max(1, (int) Math.ceil(Math.abs(sweepAngle) / MAX_ARC_SEGMENT));
        double delta = sweepAngle / segments;
        double handle = (4.0 / 3.0) * Math.tan(delta / 4.0);
        double theta = startAngle;
        for (int i = 0; i < segments; i++) {
            double theta2 = theta + delta;
            double cosT1 = Math.cos(theta);
            double sinT1 = Math.sin(theta);
            double cosT2 = Math.cos(theta2);
            double sinT2 = Math.sin(theta2);

            double p1x = cx + rx * cosPhi * cosT1 - ry * sinPhi * sinT1;
            double p1y = cy + rx * sinPhi * cosT1 + ry * cosPhi * sinT1;
            double p2x = cx + rx * cosPhi * cosT2 - ry * sinPhi * sinT2;
            double p2y = cy + rx * sinPhi * cosT2 + ry * cosPhi * sinT2;

            double d1x = -rx * cosPhi * sinT1 - ry * sinPhi * cosT1;
            double d1y = -rx * sinPhi * sinT1 + ry * cosPhi * cosT1;
            double d2x = -rx * cosPhi * sinT2 - ry * sinPhi * cosT2;
            double d2y = -rx * sinPhi * sinT2 + ry * cosPhi * cosT2;

            path.curveTo(p1x + handle * d1x, p1y + handle * d1y,
                    p2x - handle * d2x, p2y - handle * d2y, p2x, p2y);
            theta = theta2;
        }
    }

    /**
     * Returns the signed angle between two vectors.
     *
     * @param ux first vector x
     * @param uy first vector y
     * @param vx second vector x
     * @param vy second vector y
     * @return signed angle in radians
     */
    private static double angle(double ux, double uy, double vx, double vy) {
        double dot = ux * vx + uy * vy;
        double length = Math.sqrt((ux * ux + uy * uy) * (vx * vx + vy * vy));
        double cos = length == 0 ? 0 : Math.max(-1.0, Math.min(1.0, dot / length));
        double sign = ux * vy - uy * vx < 0 ? -1.0 : 1.0;
        return sign * Math.acos(cos);
    }

    /**
     * Returns whether a character begins an SVG path command.
     *
     * @param ch character to inspect
     * @return true when the character is a path command letter
     */
    private static boolean isCommand(char ch) {
        return switch (ch) {
            case 'M', 'm', 'L', 'l', 'H', 'h', 'V', 'v',
                    'C', 'c', 'S', 's', 'Q', 'q', 'T', 't', 'A', 'a', 'Z', 'z' -> true;
            default -> false;
        };
    }

    /**
     * Stateful cursor over SVG path data.
     */
    private static final class Cursor {

        /**
         * Raw path data.
         */
        private final String data;

        /**
         * Current scan index.
         */
        private int index;

        /**
         * Active command letter.
         */
        private char command;

        /**
         * Current point x.
         */
        private double cx;

        /**
         * Current point y.
         */
        private double cy;

        /**
         * Current subpath start x.
         */
        private double sx;

        /**
         * Current subpath start y.
         */
        private double sy;

        /**
         * Reflected cubic control point x for smooth curves.
         */
        private double reflectCubicX;

        /**
         * Reflected cubic control point y for smooth curves.
         */
        private double reflectCubicY;

        /**
         * Reflected quadratic control point x for smooth curves.
         */
        private double reflectQuadX;

        /**
         * Reflected quadratic control point y for smooth curves.
         */
        private double reflectQuadY;

        /**
         * True when the previous segment was a cubic curve.
         */
        private boolean hadCubic;

        /**
         * True when the previous segment was a quadratic curve.
         */
        private boolean hadQuad;

        /**
         * Creates a cursor.
         *
         * @param data raw path data
         */
        private Cursor(String data) {
            this.data = data;
        }

        /**
         * Skips list separators and reports whether content remains.
         *
         * @return true when more characters are available
         */
        private boolean skipSeparators() {
            while (index < data.length()) {
                char ch = data.charAt(index);
                if (Character.isWhitespace(ch) || ch == ',') {
                    index++;
                } else {
                    return true;
                }
            }
            return false;
        }

        /**
         * Returns the current character.
         *
         * @return current character
         */
        private char peek() {
            return data.charAt(index);
        }

        /**
         * Returns whether a number can be read at the cursor.
         *
         * @return true when a numeric token follows
         */
        private boolean hasNumber() {
            int i = index;
            while (i < data.length()) {
                char ch = data.charAt(i);
                if (Character.isWhitespace(ch) || ch == ',') {
                    i++;
                } else {
                    return ch == '+' || ch == '-' || ch == '.' || Character.isDigit(ch);
                }
            }
            return false;
        }

        /**
         * Reads one numeric token and advances the cursor.
         *
         * @return parsed value
         */
        private double number() {
            skipSeparators();
            Matcher matcher = NUMBER_PATTERN.matcher(data);
            matcher.region(index, data.length());
            if (!matcher.lookingAt()) {
                throw new IllegalArgumentException("Expected SVG number at index " + index);
            }
            index = matcher.end();
            return Double.parseDouble(matcher.group());
        }

        /**
         * Reads one arc flag (a single {@code 0} or {@code 1}).
         *
         * @return true for a set flag
         */
        private boolean flag() {
            skipSeparators();
            char ch = data.charAt(index);
            if (ch != '0' && ch != '1') {
                throw new IllegalArgumentException("Expected SVG arc flag at index " + index);
            }
            index++;
            return ch == '1';
        }

        /**
         * Records the reflected cubic control point for a following smooth cubic.
         *
         * @param ctrlX second control point x
         * @param ctrlY second control point y
         * @param endX segment end x
         * @param endY segment end y
         */
        private void setCubicReflection(double ctrlX, double ctrlY, double endX, double endY) {
            cx = endX;
            cy = endY;
            reflectCubicX = 2.0 * endX - ctrlX;
            reflectCubicY = 2.0 * endY - ctrlY;
            hadCubic = true;
            hadQuad = false;
        }

        /**
         * Records the reflected quadratic control point for a following smooth quad.
         *
         * @param ctrlX control point x
         * @param ctrlY control point y
         * @param endX segment end x
         * @param endY segment end y
         */
        private void setQuadReflection(double ctrlX, double ctrlY, double endX, double endY) {
            cx = endX;
            cy = endY;
            reflectQuadX = 2.0 * endX - ctrlX;
            reflectQuadY = 2.0 * endY - ctrlY;
            hadQuad = true;
            hadCubic = false;
        }

        /**
         * Clears smooth-curve reflection state after a non-curve segment.
         */
        private void clearReflection() {
            hadCubic = false;
            hadQuad = false;
        }

        /**
         * Returns the reflected cubic control point x, or the current point.
         *
         * @return first control point x for a smooth cubic
         */
        private double reflectedCubicX() {
            return hadCubic ? reflectCubicX : cx;
        }

        /**
         * Returns the reflected cubic control point y, or the current point.
         *
         * @return first control point y for a smooth cubic
         */
        private double reflectedCubicY() {
            return hadCubic ? reflectCubicY : cy;
        }

        /**
         * Returns the reflected quadratic control point x, or the current point.
         *
         * @return control point x for a smooth quad
         */
        private double reflectedQuadX() {
            return hadQuad ? reflectQuadX : cx;
        }

        /**
         * Returns the reflected quadratic control point y, or the current point.
         *
         * @return control point y for a smooth quad
         */
        private double reflectedQuadY() {
            return hadQuad ? reflectQuadY : cy;
        }
    }
}
