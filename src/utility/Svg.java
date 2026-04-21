package utility;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Minimal SVG loader and renderer for the embedded chess piece SVGs.
 *
 * <p>Supported path commands: {@code M/m}, {@code L/l}, {@code C/c}, {@code Z/z}.</p>
 * <p>Supported transforms: {@code translate}, {@code scale}, {@code rotate}, {@code matrix}.</p>
 *
 * Hope you like the shape of it~
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Svg {

    /**
     * Regex used to extract numeric tokens from SVG path and attribute strings.
     */
    private static final Pattern NUMBER_PATTERN = Pattern.compile(
            "[+-]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?");

    /**
     * Hidden constructor for the utility holder.
     */
    private Svg() {
        // utility holder
    }

    /**
     * Parses an SVG string into a lightweight document model.
     *
     * @param svgText SVG content to parse
     * @return parsed document model
     */
    public static DocumentModel parse(String svgText) {
        if (svgText == null || svgText.isBlank()) {
            throw new IllegalArgumentException("SVG input is empty");
        }
        Document doc = parseXml(svgText);
        Element root = doc.getDocumentElement();
        if (root == null || !"svg".equals(localName(root))) {
            throw new IllegalArgumentException("Root element is not <svg>");
        }
        double[] viewBox = parseViewBox(root);
        List<ShapeModel> shapes = new ArrayList<>();
        walk(root, new StyleState(), new AffineTransform(), shapes);
        return new DocumentModel(viewBox[0], viewBox[1], viewBox[2], viewBox[3], shapes);
    }

    /**
     * Renders a parsed SVG document into a buffered image.
     *
     * @param doc    parsed SVG document
     * @param width  output width in pixels
     * @param height output height in pixels
     * @return rendered image
     */
    public static BufferedImage render(DocumentModel doc, int width, int height) {
        if (doc == null) {
            throw new IllegalArgumentException("SVG document is null");
        }
        int w = Math.max(1, width);
        int h = Math.max(1, height);
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        double sx = w / doc.viewBoxWidth();
        double sy = h / doc.viewBoxHeight();
        AffineTransform base = new AffineTransform();
        base.scale(sx, sy);
        base.translate(-doc.viewBoxX(), -doc.viewBoxY());

        for (ShapeModel shape : doc.shapes()) {
            Shape rendered = base.createTransformedShape(shape.transform().createTransformedShape(shape.path()));
            Color fill = shape.fill();
            if (fill == null) {
                continue;
            }
            g2.setColor(fill);
            g2.fill(rendered);
        }
        g2.dispose();
        return image;
    }

    /**
     * Draws a parsed SVG document into an existing graphics context.
     *
     * @param doc    parsed SVG document
     * @param g2     target graphics context
     * @param x      target x position
     * @param y      target y position
     * @param width  target width
     * @param height target height
     */
    public static void draw(DocumentModel doc, Graphics2D g2, double x, double y, double width, double height) {
        if (doc == null) {
            throw new IllegalArgumentException("SVG document is null");
        }
        if (g2 == null) {
            throw new IllegalArgumentException("graphics context is null");
        }
        if (width <= 0.0 || height <= 0.0) {
            return;
        }

        AffineTransform previous = g2.getTransform();
        g2.translate(x, y);
        g2.scale(width / doc.viewBoxWidth(), height / doc.viewBoxHeight());
        g2.translate(-doc.viewBoxX(), -doc.viewBoxY());

        for (ShapeModel shape : doc.shapes()) {
            Color fill = shape.fill();
            if (fill == null) {
                continue;
            }
            Shape rendered = shape.transform().createTransformedShape(shape.path());
            g2.setColor(fill);
            g2.fill(rendered);
        }
        g2.setTransform(previous);
    }

    /**
     * Parses and renders an SVG string in one call.
     *
     * @param svgText SVG content
     * @param width   output width in pixels
     * @param height  output height in pixels
     * @return rendered image
     */
    public static BufferedImage render(String svgText, int width, int height) {
        return render(parse(svgText), width, height);
    }

    /**
     * Immutable SVG document container with view box and shapes.
     *
     * @param viewBoxX      view box x origin
     * @param viewBoxY      view box y origin
     * @param viewBoxWidth  view box width
     * @param viewBoxHeight view box height
     * @param shapes        list of drawable shapes
     */
    public record DocumentModel(
        /**
         * Stores the view box x.
         */
        double viewBoxX,
        /**
         * Stores the view box y.
         */
        double viewBoxY,
        /**
         * Stores the view box width.
         */
        double viewBoxWidth,
        /**
         * Stores the view box height.
         */
        double viewBoxHeight,
        /**
         * Stores the shapes.
         */
        List<ShapeModel> shapes
    ) {
        /**
         * Canonical constructor that freezes the shape list.
         */
        public DocumentModel {
            shapes = List.copyOf(shapes);
        }
    }

    /**
     * One drawable SVG shape with fill and local transform.
     *
     * @param path      shape geometry
     * @param fill      fill color (null for none)
     * @param transform local transform for the shape
     */
    public record ShapeModel(
        /**
         * Stores the path.
         */
        Shape path,
        /**
         * Stores the fill.
         */
        Color fill,
        /**
         * Stores the transform.
         */
        AffineTransform transform
    ) {
        /**
         * Canonical constructor that ensures a non-null transform.
         */
        public ShapeModel {
            transform = transform == null ? new AffineTransform() : new AffineTransform(transform);
        }

        /**
         * Returns a defensive copy of the local transform.
         *
         * @return transform copy
         */
        @Override
        public AffineTransform transform() {
            return new AffineTransform(transform);
        }
    }

    /**
     * Self-test harness comparing SVG renders to embedded PNGs.
     */
    public static final class SelfTest {

        /**
         * Piece name list used by the comparison run.
         */
        private static final String[] PIECES = {
                "black-bishop",
                "black-king",
                "black-knight",
                "black-pawn",
                "black-queen",
                "black-rook",
                "white-bishop",
                "white-king",
                "white-knight",
                "white-pawn",
                "white-queen",
                "white-rook"
        };

        /**
         * Minimum acceptable alpha intersection-over-union.
         */
        private static final double MIN_ALPHA_IOU = 0.90;

        /**
         * Maximum acceptable alpha mean absolute error.
         */
        private static final double MAX_ALPHA_MAE = 0.18;

        /**
         * Location of the source SVGs.
         */
        private static final java.nio.file.Path SVG_DIR = java.nio.file.Path.of("assets", "embedded", "pieces", "svg");

        /**
         * Location of the reference PNGs.
         */
        private static final java.nio.file.Path PNG_DIR = java.nio.file.Path.of("assets", "embedded", "pieces", "png");

        /**
         * Output directory for optional debug dumps.
         */
        private static final java.nio.file.Path DUMP_DIR = java.nio.file.Path.of("dump", "svg-render-self-test");

        /**
         * Hidden constructor for the self-test utility.
         */
        private SelfTest() {
            // utility holder
        }

        /**
         * Runs the self-test suite.
         *
         * @param args command-line arguments (`--dump` to emit PNGs)
         * @throws Exception when rendering or IO fails
         */
        public static void main(String[] args) throws Exception {
            boolean dump = false;
            for (String arg : args) {
                if ("--dump".equals(arg)) {
                    dump = true;
                }
            }

            int failures = 0;
            for (String piece : PIECES) {
                Metrics metrics = testPiece(piece, dump);
                if (!metrics.ok()) {
                    failures++;
                    System.err.printf(Locale.ROOT,
                            "FAIL %s: alphaIoU=%.3f alphaMAE=%.3f%n",
                            piece, metrics.alphaIoU, metrics.alphaMae);
                } else {
                    System.out.printf(Locale.ROOT,
                            "OK   %s: alphaIoU=%.3f alphaMAE=%.3f%n",
                            piece, metrics.alphaIoU, metrics.alphaMae);
                }
            }

            if (failures > 0) {
                throw new IllegalStateException("SVG render self-test failed: " + failures + " piece(s)");
            }
        }

        /**
         * Runs the comparison for a single piece.
         *
         * @param piece piece name stem (e.g., {@code white-king})
         * @param dump  whether to emit debug images
         * @return comparison metrics
         * @throws java.io.IOException on IO errors
         */
        private static Metrics testPiece(String piece, boolean dump) throws java.io.IOException {
            java.nio.file.Path svgPath = SVG_DIR.resolve(piece + ".svg");
            java.nio.file.Path pngPath = PNG_DIR.resolve(piece + ".png");
            String svgText = java.nio.file.Files.readString(svgPath, StandardCharsets.UTF_8);
            BufferedImage png = ImageIO.read(pngPath.toFile());
            if (png == null) {
                throw new java.io.IOException("Failed to load PNG: " + pngPath);
            }
            DocumentModel doc = Svg.parse(svgText);
            BufferedImage rendered = Svg.render(doc, png.getWidth(), png.getHeight());
            Metrics metrics = compare(rendered, png);

            if (dump) {
                java.nio.file.Files.createDirectories(DUMP_DIR);
                ImageIO.write(rendered, "png", DUMP_DIR.resolve(piece + "-svg.png").toFile());
                ImageIO.write(png, "png", DUMP_DIR.resolve(piece + "-png.png").toFile());
                ImageIO.write(buildAlphaDiff(rendered, png), "png",
                        DUMP_DIR.resolve(piece + "-alpha-diff.png").toFile());
            }

            return metrics;
        }

        /**
         * Compares two images by alpha overlap and absolute error.
         *
         * @param svg rendered SVG image
         * @param png reference PNG image
         * @return comparison metrics
         */
        private static Metrics compare(BufferedImage svg, BufferedImage png) {
            int w = Math.min(svg.getWidth(), png.getWidth());
            int h = Math.min(svg.getHeight(), png.getHeight());
            long union = 0;
            long intersection = 0;
            double alphaAbs = 0.0;
            long count = 0;

            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int svgArgb = svg.getRGB(x, y);
                    int pngArgb = png.getRGB(x, y);
                    int svgA = (svgArgb >>> 24) & 0xFF;
                    int pngA = (pngArgb >>> 24) & 0xFF;
                    boolean svgOn = svgA > 8;
                    boolean pngOn = pngA > 8;
                    if (svgOn || pngOn) {
                        union++;
                    }
                    if (svgOn && pngOn) {
                        intersection++;
                    }
                    alphaAbs += Math.abs(svgA - pngA);
                    count++;
                }
            }

            double alphaIoU = union == 0 ? 1.0 : (double) intersection / (double) union;
            double alphaMae = (count == 0) ? 0.0 : (alphaAbs / (count * 255.0));
            return new Metrics(alphaIoU, alphaMae);
        }

        /**
         * Builds a heatmap that highlights alpha differences.
         *
         * @param svg rendered SVG image
         * @param png reference PNG image
         * @return diff image
         */
        private static BufferedImage buildAlphaDiff(BufferedImage svg, BufferedImage png) {
            int w = Math.min(svg.getWidth(), png.getWidth());
            int h = Math.min(svg.getHeight(), png.getHeight());
            BufferedImage diff = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int svgA = (svg.getRGB(x, y) >>> 24) & 0xFF;
                    int pngA = (png.getRGB(x, y) >>> 24) & 0xFF;
                    int delta = Math.abs(svgA - pngA);
                    int rgb = (0xFF << 24) | (delta << 16);
                    diff.setRGB(x, y, rgb);
                }
            }
            return diff;
        }

        /**
         * Metric pair capturing overlap and error between two alpha masks.
         *
         * @param alphaIoU overlap of alpha masks
         * @param alphaMae normalized mean absolute alpha error
         */
        private record Metrics(
            /**
             * Stores the alpha io u.
             */
            double alphaIoU,
            /**
             * Stores the alpha mae.
             */
            double alphaMae
        ) {
            /**
             * Checks whether the metrics satisfy the acceptance thresholds.
             *
             * @return true when the metrics pass the thresholds
             */
            boolean ok() {
                return alphaIoU >= MIN_ALPHA_IOU && alphaMae <= MAX_ALPHA_MAE;
            }
        }
    }

    /**
     * Parses raw SVG text into a DOM document with safe parser settings.
     *
     * @param svgText SVG content
     * @return parsed DOM document
     */
    private static Document parseXml(String svgText) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setExpandEntityReferences(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(svgText.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse SVG", e);
        }
    }

    /**
     * Extracts the view box from the root SVG element.
     *
     * @param root root SVG element
     * @return four-element view box array
     */
    private static double[] parseViewBox(Element root) {
        String viewBox = root.getAttribute("viewBox");
        if (viewBox != null && !viewBox.isBlank()) {
            double[] values = parseNumbers(viewBox);
            if (values.length == 4) {
                return values;
            }
        }
        double width = parseSize(root.getAttribute("width"), 0);
        double height = parseSize(root.getAttribute("height"), 0);
        if (width <= 0 || height <= 0) {
            width = 1;
            height = 1;
        }
        return new double[] { 0, 0, width, height };
    }

    /**
     * Parses a single numeric size from a length attribute.
     *
     * @param value    attribute value
     * @param fallback fallback value when parsing fails
     * @return parsed size or fallback
     */
    private static double parseSize(String value, double fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        double[] values = parseNumbers(value);
        return values.length > 0 ? values[0] : fallback;
    }

    /**
     * Walks the DOM tree and collects drawable path shapes.
     *
     * @param node            current DOM node
     * @param parentStyle     inherited style state
     * @param parentTransform inherited transform
     * @param shapes          output shape list
     */
    private static void walk(Node node, StyleState parentStyle, AffineTransform parentTransform,
            List<ShapeModel> shapes) {
        if (node.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }
        Element element = (Element) node;
        StyleState style = parentStyle.inherit(element);
        AffineTransform transform = new AffineTransform(parentTransform);
        String transformAttr = element.getAttribute("transform");
        if (transformAttr != null && !transformAttr.isBlank()) {
            transform.concatenate(parseTransform(transformAttr));
        }

        String localName = localName(element);
        if ("path".equals(localName)) {
            String d = element.getAttribute("d");
            if (d != null && !d.isBlank() && style.fill != null) {
                Path2D.Double path = parsePath(d);
                shapes.add(new ShapeModel(path, style.fill, transform));
            }
        } else if ("rect".equals(localName) && style.fill != null) {
            Shape rect = parseRect(element);
            if (rect != null) {
                shapes.add(new ShapeModel(rect, style.fill, transform));
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            walk(children.item(i), style, transform, shapes);
        }
    }

    /**
     * Parses an SVG rectangle element.
     *
     * @param element rectangle element
     * @return rectangle shape, or {@code null} when width or height is not positive
     */
    private static Shape parseRect(Element element) {
        double x = parseSize(element.getAttribute("x"), 0);
        double y = parseSize(element.getAttribute("y"), 0);
        double width = parseSize(element.getAttribute("width"), 0);
        double height = parseSize(element.getAttribute("height"), 0);
        if (width <= 0 || height <= 0) {
            return null;
        }
        return new Rectangle2D.Double(x, y, width, height);
    }

    /**
     * Resolves the local name for an element, falling back to node name.
     *
     * @param element element to inspect
     * @return local or node name
     */
    private static String localName(Element element) {
        String name = element.getLocalName();
        if (name == null || name.isBlank()) {
            return element.getNodeName();
        }
        return name;
    }

    /**
     * Minimal style state for inheriting fill colors.
     */
    private static final class StyleState {
        /**
         * Current fill color, or null when unset.
         */
        private final Color fill;

        /**
         * Creates a state with no explicit fill.
         */
        private StyleState() {
            this(null);
        }

        /**
         * Creates a state with a given fill.
         *
         * @param fill fill color or null
         */
        private StyleState(Color fill) {
            this.fill = fill;
        }

        /**
         * Creates a new style state inheriting from the current one.
         *
         * @param element element with optional fill attribute
         * @return derived style state
         */
        private StyleState inherit(Element element) {
            Color nextFill = fill;
            String fillAttr = element.getAttribute("fill");
            if (fillAttr != null && !fillAttr.isBlank()) {
                Color parsed = parseColor(fillAttr);
                nextFill = parsed;
            }
            return new StyleState(nextFill);
        }

        /**
         * Parses an SVG fill color.
         *
         * @param value raw fill value
         * @return parsed color or null for {@code none}
         */
        private static Color parseColor(String value) {
            String trimmed = value.trim();
            if (trimmed.isEmpty() || "none".equalsIgnoreCase(trimmed)) {
                return null;
            }
            if (trimmed.startsWith("#")) {
                String hex = trimmed.substring(1);
                if (hex.length() == 3) {
                    int r = Integer.parseInt(hex.substring(0, 1) + hex.substring(0, 1), 16);
                    int g = Integer.parseInt(hex.substring(1, 2) + hex.substring(1, 2), 16);
                    int b = Integer.parseInt(hex.substring(2, 3) + hex.substring(2, 3), 16);
                    return new Color(r, g, b);
                }
                if (hex.length() == 6) {
                    int rgb = Integer.parseInt(hex, 16);
                    return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
                }
            }
            if (trimmed.startsWith("rgb")) {
                double[] values = parseNumbers(trimmed);
                if (values.length >= 3) {
                    return new Color(clamp(values[0]), clamp(values[1]), clamp(values[2]));
                }
            }
            throw new IllegalArgumentException("Unsupported color: " + value);
        }

        /**
         * Clamps a double to the {@code [0,255]} color channel range.
         *
         * @param value channel value
         * @return clamped integer channel
         */
        private static int clamp(double value) {
            if (value <= 0) {
                return 0;
            }
            if (value >= 255) {
                return 255;
            }
            return (int) Math.round(value);
        }
    }

    /**
     * Parses all numeric tokens from a string.
     *
     * @param value input string
     * @return array of parsed doubles
     */
    private static double[] parseNumbers(String value) {
        if (value == null || value.isBlank()) {
            return new double[0];
        }
        List<Double> numbers = new ArrayList<>();
        Matcher matcher = NUMBER_PATTERN.matcher(value);
        while (matcher.find()) {
            numbers.add(Double.parseDouble(matcher.group()));
        }
        double[] result = new double[numbers.size()];
        for (int i = 0; i < numbers.size(); i++) {
            result[i] = numbers.get(i);
        }
        return result;
    }

    /**
     * Parses SVG path data into a Java2D path.
     *
     * @param data SVG path data string
     * @return parsed path (possibly empty)
     */
    private static Path2D.Double parsePath(String data) {
        Path2D.Double path = new Path2D.Double(Path2D.WIND_NON_ZERO);
        if (data == null || data.isBlank()) {
            return path;
        }

        PathCursor cursor = new PathCursor(data);
        while (cursor.hasMore()) {
            cursor.consumeSeparators();
            if (!cursor.hasMore()) {
                break;
            }
            char ch = cursor.currentChar();
            if (isCommand(ch)) {
                cursor.command = ch;
                cursor.advance();
            } else if (cursor.command == 0) {
                throw new IllegalArgumentException("SVG path missing command at index " + cursor.index);
            }

            switch (cursor.command) {
                case 'M', 'm' -> parseMoveTo(cursor, path);
                case 'L', 'l' -> parseLineTo(cursor, path);
                case 'C', 'c' -> parseCurveTo(cursor, path);
                case 'Z', 'z' -> closePath(cursor, path);
                default -> throw new IllegalArgumentException("Unsupported SVG path command: " + cursor.command);
            }
        }

        return path;
    }

    /**
     * Parses a move-to segment and any implicit line-to pairs.
     *
     * @param cursor path cursor
     * @param path   path to update
     */
    private static void parseMoveTo(PathCursor cursor, Path2D.Double path) {
        boolean relative = cursor.command == 'm';
        double x = cursor.readNumber();
        double y = cursor.readNumber();
        if (relative) {
            x += cursor.cx;
            y += cursor.cy;
        }
        cursor.cx = x;
        cursor.cy = y;
        cursor.sx = x;
        cursor.sy = y;
        path.moveTo(cursor.cx, cursor.cy);

        cursor.command = relative ? 'l' : 'L';
        while (cursor.hasNumber()) {
            x = cursor.readNumber();
            y = cursor.readNumber();
            if (cursor.command == 'l') {
                x += cursor.cx;
                y += cursor.cy;
            }
            cursor.cx = x;
            cursor.cy = y;
            path.lineTo(cursor.cx, cursor.cy);
        }
    }

    /**
     * Parses one or more line-to segments.
     *
     * @param cursor path cursor
     * @param path   path to update
     */
    private static void parseLineTo(PathCursor cursor, Path2D.Double path) {
        boolean relative = cursor.command == 'l';
        while (cursor.hasNumber()) {
            double x = cursor.readNumber();
            double y = cursor.readNumber();
            if (relative) {
                x += cursor.cx;
                y += cursor.cy;
            }
            cursor.cx = x;
            cursor.cy = y;
            path.lineTo(cursor.cx, cursor.cy);
        }
    }

    /**
     * Parses one or more cubic curve segments.
     *
     * @param cursor path cursor
     * @param path   path to update
     */
    private static void parseCurveTo(PathCursor cursor, Path2D.Double path) {
        boolean relative = cursor.command == 'c';
        while (cursor.hasNumber()) {
            double x1 = cursor.readNumber();
            double y1 = cursor.readNumber();
            double x2 = cursor.readNumber();
            double y2 = cursor.readNumber();
            double x = cursor.readNumber();
            double y = cursor.readNumber();
            if (relative) {
                x1 += cursor.cx;
                y1 += cursor.cy;
                x2 += cursor.cx;
                y2 += cursor.cy;
                x += cursor.cx;
                y += cursor.cy;
            }
            path.curveTo(x1, y1, x2, y2, x, y);
            cursor.cx = x;
            cursor.cy = y;
        }
    }

    /**
     * Closes the current subpath and resets the cursor position.
     *
     * @param cursor path cursor
     * @param path   path to close
     */
    private static void closePath(PathCursor cursor, Path2D.Double path) {
        path.closePath();
        cursor.cx = cursor.sx;
        cursor.cy = cursor.sy;
    }

    /**
     * Cursor state for incremental path parsing.
     */
    private static final class PathCursor {
        /**
         * Path data string being parsed.
         */
        private final String data;

        /**
         * Current index into the data string.
         */
        private int index;

        /**
         * Current SVG command.
         */
        private char command;

        /**
         * Current x coordinate.
         */
        private double cx;

        /**
         * Current y coordinate.
         */
        private double cy;

        /**
         * Start x of the current subpath.
         */
        private double sx;

        /**
         * Start y of the current subpath.
         */
        private double sy;

        /**
         * Creates a cursor for the given path data.
         *
         * @param data path data string
         */
        private PathCursor(String data) {
            this.data = data;
        }

        /**
         * Checks whether more characters are available.
         *
         * @return true if more characters remain
         */
        private boolean hasMore() {
            return index < data.length();
        }

        /**
         * Advances the cursor past separators.
         */
        private void consumeSeparators() {
            index = skipSeparators(data, index);
        }

        /**
         * Returns the current character.
         *
         * @return current character
         */
        private char currentChar() {
            return data.charAt(index);
        }

        /**
         * Advances the cursor by one character.
         */
        private void advance() {
            index++;
        }

        /**
         * Checks whether a number token is next.
         *
         * @return true if a number follows
         */
        private boolean hasNumber() {
            return Svg.hasNumber(data, index);
        }

        /**
         * Reads the next numeric token.
         *
         * @return parsed number
         */
        private double readNumber() {
            double value = Svg.readNumber(data, index);
            index = numberEndIndex;
            return value;
        }
    }

    /**
     * Parses a transform attribute into an affine transform.
     *
     * @param transform transform attribute value
     * @return affine transform
     */
    private static AffineTransform parseTransform(String transform) {
        if (transform == null || transform.isBlank()) {
            return new AffineTransform();
        }
        List<TransformOp> ops = tokenizeTransform(transform);
        AffineTransform result = new AffineTransform();
        for (TransformOp op : ops) {
            result.concatenate(op.toAffine());
        }
        return result;
    }

    /**
     * Tokenizes a transform string into operations.
     *
     * @param value transform attribute value
     * @return ordered list of transform operations
     */
    private static List<TransformOp> tokenizeTransform(String value) {
        TransformCursor cursor = new TransformCursor(value);
        List<TransformOp> ops = new ArrayList<>();
        while (cursor.hasMore()) {
            String name = cursor.readName();
            if (name == null) {
                break;
            }
            String args = cursor.readArgs(name);
            ops.add(new TransformOp(name, parseNumbers(args)));
        }
        return ops;
    }

    /**
     * Parsed transform operation with its arguments.
     *
     * @param name operation name
     * @param args operation arguments
     */
    private record TransformOp(
        /**
         * Stores the name.
         */
        String name,
        /**
         * Stores the args.
         */
        double[] args
    ) {
        /**
         * Converts the operation into an affine transform.
         *
         * @return affine transform
         */
        AffineTransform toAffine() {
            switch (name) {
                case "translate":
                    return translate(args);
                case "scale":
                    return scale(args);
                case "rotate":
                    return rotate(args);
                case "matrix":
                    return matrix(args);
                default:
                    throw new IllegalArgumentException("Unsupported transform: " + name);
            }
        }

        /**
         * Compares operations by name and argument values.
         *
         * @param obj other object
         * @return true when equal
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof TransformOp other)) {
                return false;
            }
            return name.equals(other.name) && Arrays.equals(args, other.args);
        }

        /**
         * Computes a hash code including the argument array.
         *
         * @return hash code
         */
        @Override
        public int hashCode() {
            return 31 * name.hashCode() + Arrays.hashCode(args);
        }

        /**
         * Returns a descriptive string for the operation.
         *
         * @return string description
         */
        @Override
        public String toString() {
            return "TransformOp[name=" + name + ", args=" + Arrays.toString(args) + "]";
        }

        /**
         * Builds a translate transform.
         *
         * @param args numeric arguments
         * @return translation transform
         */
        private static AffineTransform translate(double[] args) {
            double tx = args.length > 0 ? args[0] : 0.0;
            double ty = args.length > 1 ? args[1] : 0.0;
            return AffineTransform.getTranslateInstance(tx, ty);
        }

        /**
         * Builds a scale transform.
         *
         * @param args numeric arguments
         * @return scale transform
         */
        private static AffineTransform scale(double[] args) {
            double sx = args.length > 0 ? args[0] : 1.0;
            double sy = args.length > 1 ? args[1] : sx;
            return AffineTransform.getScaleInstance(sx, sy);
        }

        /**
         * Builds a rotation transform.
         *
         * @param args numeric arguments
         * @return rotation transform
         */
        private static AffineTransform rotate(double[] args) {
            double angle = args.length > 0 ? Math.toRadians(args[0]) : 0.0;
            if (args.length >= 3) {
                return AffineTransform.getRotateInstance(angle, args[1], args[2]);
            }
            return AffineTransform.getRotateInstance(angle);
        }

        /**
         * Builds a matrix transform.
         *
         * @param args numeric arguments
         * @return matrix transform
         */
        private static AffineTransform matrix(double[] args) {
            if (args.length != 6) {
                throw new IllegalArgumentException("matrix() expects 6 values");
            }
            return new AffineTransform(args[0], args[1], args[2], args[3], args[4], args[5]);
        }
    }

    /**
     * Cursor for tokenizing transform strings.
     */
    private static final class TransformCursor {
        /**
         * Transform string being parsed.
         */
        private final String value;

        /**
         * Current index within the transform string.
         */
        private int index;

        /**
         * Creates a cursor for a transform string.
         *
         * @param value transform string
         */
        private TransformCursor(String value) {
            this.value = value == null ? "" : value;
        }

        /**
         * Checks whether there is more input to parse.
         *
         * @return true if more input remains
         */
        private boolean hasMore() {
            index = skipSeparators(value, index);
            return index < value.length();
        }

        /**
         * Reads the next transform name.
         *
         * @return transform name or null when done
         */
        private String readName() {
            if (!hasMore()) {
                return null;
            }
            int start = index;
            while (index < value.length() && Character.isLetter(value.charAt(index))) {
                index++;
            }
            if (start == index) {
                throw new IllegalArgumentException("Invalid transform at index " + index);
            }
            return value.substring(start, index);
        }

        /**
         * Reads the argument list for a named transform.
         *
         * @param name transform name
         * @return argument substring without parentheses
         */
        private String readArgs(String name) {
            index = skipSeparators(value, index);
            if (index >= value.length() || value.charAt(index) != '(') {
                throw new IllegalArgumentException("Expected '(' after transform " + name);
            }
            int depth = 1;
            int argsStart = ++index;
            while (index < value.length() && depth > 0) {
                char ch = value.charAt(index);
                if (ch == '(') {
                    depth++;
                } else if (ch == ')') {
                    depth--;
                }
                index++;
            }
            if (depth != 0) {
                throw new IllegalArgumentException("Unclosed transform arguments for " + name);
            }
            return value.substring(argsStart, index - 1);
        }
    }

    /**
     * Checks whether a character is a supported SVG path command.
     *
     * @param ch character to inspect
     * @return true when the character is a supported command
     */
    private static boolean isCommand(char ch) {
        return ch == 'M' || ch == 'm' || ch == 'L' || ch == 'l' || ch == 'C' || ch == 'c' || ch == 'Z'
                || ch == 'z';
    }

    /**
     * Skips whitespace and comma separators in a list string.
     *
     * @param data  input string
     * @param index starting index
     * @return index of the next non-separator character
     */
    private static int skipSeparators(String data, int index) {
        int i = index;
        while (i < data.length()) {
            char ch = data.charAt(i);
            if (ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t' || ch == ',') {
                i++;
                continue;
            }
            return i;
        }
        return i;
    }

    /**
     * End index of the last parsed number token.
     */
    private static int numberEndIndex;

    /**
     * Checks if a numeric token starts at or after the given index.
     *
     * @param data  input string
     * @param index starting index
     * @return true if a number token follows
     */
    private static boolean hasNumber(String data, int index) {
        int i = skipSeparators(data, index);
        if (i >= data.length()) {
            return false;
        }
        char ch = data.charAt(i);
        return ch == '+' || ch == '-' || ch == '.' || Character.isDigit(ch);
    }

    /**
     * Reads a numeric token starting at the given index.
     *
     * @param data  input string
     * @param index starting index
     * @return parsed number
     */
    private static double readNumber(String data, int index) {
        int i = skipSeparators(data, index);
        if (i >= data.length()) {
            throw new IllegalArgumentException("Unexpected end of SVG path data");
        }
        Matcher matcher = NUMBER_PATTERN.matcher(data);
        matcher.region(i, data.length());
        if (!matcher.lookingAt()) {
            throw new IllegalArgumentException("Expected number in SVG path at index " + i);
        }
        numberEndIndex = matcher.end();
        return Double.parseDouble(matcher.group());
    }
}
