package chess.pdf.document;

import java.awt.Color;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Minimal dependency-free SVG-to-PDF vector translator for the embedded chess
 * artwork.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class Svg {

	/**
	 * Pattern used to scan SVG numeric tokens.
	 */
	private static final Pattern NUMBER_PATTERN = Pattern.compile(
			"[+-]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?");

	/**
	 * Utility class; prevent instantiation.
	 */
	private Svg() {
		// utility holder
	}

	/**
	 * Draws SVG markup into a rectangular page region.
	 *
	 * @param page target page
	 * @param svgText SVG source
	 * @param x left edge
	 * @param y top edge
	 * @param width output width
	 * @param height output height
	 */
	static void draw(Page page, String svgText, double x, double y, double width, double height) {
		org.w3c.dom.Document document = parseXml(svgText);
		Element root = document.getDocumentElement();
		if (root == null || !"svg".equals(localName(root))) {
			throw new IllegalArgumentException("Root element is not <svg>");
		}

		ViewBox viewBox = parseViewBox(root);
		Map<String, GradientPaint> gradients = parseGradients(root);
		AffineTransform pageTransform = new AffineTransform(
				width / viewBox.width,
				0.0,
				0.0,
				-height / viewBox.height,
				x - viewBox.x * (width / viewBox.width),
				page.getHeight() - y + viewBox.y * (height / viewBox.height));

		walk(root, StyleState.defaults(), new AffineTransform(), page, pageTransform, gradients);
	}

	/**
	 * Walks one SVG DOM subtree and renders supported elements.
	 *
	 * @param node current DOM node
	 * @param parentStyle inherited style state
	 * @param parentTransform inherited SVG transform
	 * @param page target page
	 * @param pageTransform transform from SVG coordinates to page coordinates
	 * @param gradients parsed gradient resources by id
	 */
	private static void walk(Node node, StyleState parentStyle, AffineTransform parentTransform,
			Page page, AffineTransform pageTransform, Map<String, GradientPaint> gradients) {
		if (node.getNodeType() != Node.ELEMENT_NODE) {
			return;
		}

		Element element = (Element) node;
		StyleState style = parentStyle.inherit(element);
		AffineTransform transform = new AffineTransform(parentTransform);
		String transformAttr = attribute(element, "transform");
		if (transformAttr != null) {
			transform.concatenate(SvgTransform.parse(transformAttr));
		}

		String name = localName(element);
		if ("path".equals(name)) {
			drawShape(page, SvgPath.parse(attribute(element, "d"), style.fillRule), style, transform, pageTransform,
					gradients);
		} else if ("rect".equals(name)) {
			drawShape(page, parseRect(element), style, transform, pageTransform, gradients);
		} else if ("circle".equals(name)) {
			drawShape(page, parseCircle(element), style, transform, pageTransform, gradients);
		} else if ("polygon".equals(name)) {
			drawShape(page, parsePolygon(attribute(element, "points"), style.fillRule), style, transform, pageTransform,
					gradients);
		} else if ("text".equals(name)) {
			drawText(page, element, style, transform, pageTransform);
		}

		NodeList children = element.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			walk(children.item(i), style, transform, page, pageTransform, gradients);
		}
	}

	/**
	 * Draws one parsed SVG shape using fill and stroke style.
	 *
	 * @param page target page
	 * @param shape shape in SVG coordinates
	 * @param style resolved style
	 * @param transform element transform
	 * @param pageTransform transform from SVG coordinates to page coordinates
	 * @param gradients parsed gradient resources by id
	 */
	private static void drawShape(Page page, Shape shape, StyleState style, AffineTransform transform,
			AffineTransform pageTransform, Map<String, GradientPaint> gradients) {
		if (shape == null) {
			return;
		}

		Color fill = style.fill;
		Color stroke = style.stroke;
		double fillOpacity = style.effectiveFillOpacity();
		double strokeOpacity = style.effectiveStrokeOpacity();
		GradientPaint gradient = style.fillReference == null ? null : gradients.get(style.fillReference);
		boolean hasGradientFill = gradient != null;
		boolean hasFill = hasGradientFill || (fill != null && fillOpacity > 0.0);
		boolean hasStroke = stroke != null && strokeOpacity > 0.0 && style.strokeWidth > 0.0;
		if (!hasFill && !hasStroke) {
			return;
		}

		AffineTransform finalTransform = new AffineTransform(pageTransform);
		finalTransform.concatenate(transform);
		Shape transformed = finalTransform.createTransformedShape(shape);

		if (hasGradientFill) {
			drawGradientShape(page, transformed, gradient, finalTransform);
			if (!hasStroke) {
				return;
			}
			hasFill = false;
		}

		page.append("q\n");
		if (hasFill) {
			page.append(Document.rgb(fill) + " rg\n");
		}
		if (hasStroke) {
			page.append(Document.rgb(stroke) + " RG\n");
			page.append(Document.number(effectiveStrokeWidth(finalTransform, style.strokeWidth)) + " w\n");
			page.append(pdfLineJoin(style.lineJoin) + " j\n");
			page.append(pdfLineCap(style.lineCap) + " J\n");
		}

		String opacityName = page.addOpacity(hasFill ? fillOpacity : 1.0, hasStroke ? strokeOpacity : 1.0);
		if (opacityName != null) {
			page.append("/" + opacityName + " gs\n");
		}

		appendPath(page, transformed);
		page.append(paintOperator(hasFill, hasStroke, style.fillRule));
		page.append("Q\n");
	}

	/**
	 * Draws one clipped PDF gradient shading for an SVG gradient fill.
	 *
	 * @param page target page
	 * @param transformed clipping shape in PDF coordinates
	 * @param gradient SVG gradient definition
	 * @param finalTransform SVG-to-PDF transform
	 */
	private static void drawGradientShape(Page page, Shape transformed, GradientPaint gradient,
			AffineTransform finalTransform) {
		page.append("q\n");
		appendPath(page, transformed);
		page.append("W n\n");
		String name;
		if (gradient instanceof LinearGradient linear) {
			name = addLinearShading(page, linear, finalTransform);
		} else if (gradient instanceof RadialGradient radial) {
			name = addRadialShading(page, radial, finalTransform);
		} else {
			page.append("Q\n");
			return;
		}
		page.append("/" + name + " sh\n");
		page.append("Q\n");
	}

	/**
	 * Adds one linear PDF shading for an SVG linear gradient.
	 *
	 * @param page target page
	 * @param gradient parsed SVG gradient
	 * @param finalTransform SVG-to-PDF transform
	 * @return shading resource name
	 */
	private static String addLinearShading(Page page, LinearGradient gradient, AffineTransform finalTransform) {
		Point2D start = finalTransform.transform(new Point2D.Double(gradient.x1, gradient.y1), null);
		Point2D end = finalTransform.transform(new Point2D.Double(gradient.x2, gradient.y2), null);
		return page.addAxialShading(start.getX(), start.getY(), end.getX(), end.getY(),
				gradient.start.color, gradient.end.color);
	}

	/**
	 * Adds one radial PDF shading for an SVG radial gradient.
	 *
	 * @param page target page
	 * @param gradient parsed SVG gradient
	 * @param finalTransform SVG-to-PDF transform
	 * @return shading resource name
	 */
	private static String addRadialShading(Page page, RadialGradient gradient, AffineTransform finalTransform) {
		Point2D center = finalTransform.transform(new Point2D.Double(gradient.cx, gradient.cy), null);
		double radius = Math.max(0.001, gradient.r * averageScale(finalTransform));
		return page.addRadialShading(center.getX(), center.getY(), 0.0, center.getX(), center.getY(), radius,
				gradient.start.color, gradient.end.color);
	}

	/**
	 * Draws one SVG text element using the closest matching PDF base font.
	 *
	 * @param page target page
	 * @param element SVG text element
	 * @param style resolved style
	 * @param transform element transform
	 * @param pageTransform transform from SVG coordinates to page coordinates
	 */
	private static void drawText(Page page, Element element, StyleState style, AffineTransform transform,
			AffineTransform pageTransform) {
		String text = normalizeText(element.getTextContent());
		if (text.isBlank()) {
			return;
		}

		double x = parseSize(attribute(element, "x"), 0.0);
		double y = parseSize(attribute(element, "y"), 0.0);
		Color fill = style.fill == null ? Color.BLACK : style.fill;
		double fillOpacity = style.effectiveFillOpacity();
		if (fillOpacity <= 0.0) {
			return;
		}

		AffineTransform finalTransform = new AffineTransform(pageTransform);
		finalTransform.concatenate(transform);
		Point2D origin = finalTransform.transform(new Point2D.Double(x, y), null);
		Point2D xBasis = finalTransform.deltaTransform(new Point2D.Double(1.0, 0.0), null);
		Point2D yDown = finalTransform.deltaTransform(new Point2D.Double(0.0, 1.0), null);

		Font font = resolveFont(style.fontFamily, style.fontWeight);
		double fontSize = Math.max(0.1, style.fontSize);

		page.useFont(font);
		page.append("q\n");
		String opacityName = page.addOpacity(fillOpacity, 1.0);
		if (opacityName != null) {
			page.append("/" + opacityName + " gs\n");
		}
		page.append("BT\n");
		page.append("/" + font.resourceName() + " " + Document.number(fontSize) + " Tf\n");
		page.append(Document.rgb(fill) + " rg\n");
		page.append(Document.number(xBasis.getX()) + " " + Document.number(xBasis.getY()) + " "
				+ Document.number(-yDown.getX()) + " " + Document.number(-yDown.getY()) + " "
				+ Document.number(origin.getX()) + " " + Document.number(origin.getY()) + " Tm\n");
		page.append("(" + Document.escape(text) + ") Tj\n");
		page.append("ET\n");
		page.append("Q\n");
	}

	/**
	 * Appends a Java2D shape as PDF path commands.
	 *
	 * @param page target page
	 * @param shape transformed shape
	 */
	private static void appendPath(Page page, Shape shape) {
		PathIterator iterator = shape.getPathIterator(null);
		double[] coords = new double[6];
		double currentX = 0.0;
		double currentY = 0.0;

		while (!iterator.isDone()) {
			int type = iterator.currentSegment(coords);
			switch (type) {
				case PathIterator.SEG_MOVETO -> {
					currentX = coords[0];
					currentY = coords[1];
					page.append(Document.number(currentX) + " " + Document.number(currentY) + " m\n");
				}
				case PathIterator.SEG_LINETO -> {
					currentX = coords[0];
					currentY = coords[1];
					page.append(Document.number(currentX) + " " + Document.number(currentY) + " l\n");
				}
				case PathIterator.SEG_QUADTO -> {
					double x1 = currentX + (coords[0] - currentX) * (2.0 / 3.0);
					double y1 = currentY + (coords[1] - currentY) * (2.0 / 3.0);
					double x2 = coords[2] + (coords[0] - coords[2]) * (2.0 / 3.0);
					double y2 = coords[3] + (coords[1] - coords[3]) * (2.0 / 3.0);
					currentX = coords[2];
					currentY = coords[3];
					page.append(Document.number(x1) + " " + Document.number(y1) + " "
							+ Document.number(x2) + " " + Document.number(y2) + " "
							+ Document.number(currentX) + " " + Document.number(currentY) + " c\n");
				}
				case PathIterator.SEG_CUBICTO -> {
					currentX = coords[4];
					currentY = coords[5];
					page.append(Document.number(coords[0]) + " " + Document.number(coords[1]) + " "
							+ Document.number(coords[2]) + " " + Document.number(coords[3]) + " "
							+ Document.number(currentX) + " " + Document.number(currentY) + " c\n");
				}
				case PathIterator.SEG_CLOSE -> page.append("h\n");
				default -> throw new IllegalArgumentException("Unsupported path segment: " + type);
			}
			iterator.next();
		}
	}

	/**
	 * Chooses the PDF paint operator for fill/stroke state.
	 *
	 * @param fill whether fill is active
	 * @param stroke whether stroke is active
	 * @param fillRule SVG fill-rule value
	 * @return PDF paint operator
	 */
	private static String paintOperator(boolean fill, boolean stroke, String fillRule) {
		boolean evenOdd = "evenodd".equals(fillRule);
		if (fill && stroke) {
			return evenOdd ? "B*\n" : "B\n";
		}
		if (fill) {
			return evenOdd ? "f*\n" : "f\n";
		}
		return "S\n";
	}

	/**
	 * Converts an SVG line-join value into a PDF line-join code.
	 *
	 * @param value SVG line-join value
	 * @return PDF line-join code
	 */
	private static int pdfLineJoin(String value) {
		return switch (safeLower(value)) {
			case "round" -> 1;
			case "bevel" -> 2;
			default -> 0;
		};
	}

	/**
	 * Converts an SVG line-cap value into a PDF line-cap code.
	 *
	 * @param value SVG line-cap value
	 * @return PDF line-cap code
	 */
	private static int pdfLineCap(String value) {
		return switch (safeLower(value)) {
			case "round" -> 1;
			case "square" -> 2;
			default -> 0;
		};
	}

	/**
	 * Computes stroke width after transform scaling.
	 *
	 * @param transform final shape transform
	 * @param strokeWidth source stroke width
	 * @return effective PDF stroke width
	 */
	private static double effectiveStrokeWidth(AffineTransform transform, double strokeWidth) {
		return Math.max(0.1, strokeWidth * averageScale(transform));
	}

	/**
	 * Computes the average transform scale.
	 *
	 * @param transform transform to inspect
	 * @return average scale factor
	 */
	private static double averageScale(AffineTransform transform) {
		Point2D xBasis = transform.deltaTransform(new Point2D.Double(1.0, 0.0), null);
		Point2D yBasis = transform.deltaTransform(new Point2D.Double(0.0, 1.0), null);
		return (length(xBasis) + length(yBasis)) / 2.0;
	}

	/**
	 * Computes vector length for a point treated as a vector.
	 *
	 * @param point vector endpoint
	 * @return vector length
	 */
	private static double length(Point2D point) {
		return Math.hypot(point.getX(), point.getY());
	}

	/**
	 * Parses an SVG {@code rect} element.
	 *
	 * @param element rectangle element
	 * @return rectangle shape, or null when dimensions are invalid
	 */
	private static Shape parseRect(Element element) {
		double x = parseSize(attribute(element, "x"), 0.0);
		double y = parseSize(attribute(element, "y"), 0.0);
		double width = parseSize(attribute(element, "width"), 0.0);
		double height = parseSize(attribute(element, "height"), 0.0);
		if (width <= 0.0 || height <= 0.0) {
			return null;
		}

		double rx = parseSize(attribute(element, "rx"), -1.0);
		double ry = parseSize(attribute(element, "ry"), -1.0);
		if (rx < 0.0 && ry >= 0.0) {
			rx = ry;
		}
		if (ry < 0.0 && rx >= 0.0) {
			ry = rx;
		}
		if (rx <= 0.0 && ry <= 0.0) {
			return new Rectangle2D.Double(x, y, width, height);
		}

		double arcWidth = Math.min(width, Math.max(0.0, rx) * 2.0);
		double arcHeight = Math.min(height, Math.max(0.0, ry) * 2.0);
		return new RoundRectangle2D.Double(x, y, width, height, arcWidth, arcHeight);
	}

	/**
	 * Parses an SVG {@code circle} element.
	 *
	 * @param element circle element
	 * @return circle shape, or null when radius is invalid
	 */
	private static Shape parseCircle(Element element) {
		double cx = parseSize(attribute(element, "cx"), 0.0);
		double cy = parseSize(attribute(element, "cy"), 0.0);
		double r = parseSize(attribute(element, "r"), 0.0);
		if (r <= 0.0) {
			return null;
		}
		return new Ellipse2D.Double(cx - r, cy - r, r * 2.0, r * 2.0);
	}

	/**
	 * Parses an SVG polygon point list.
	 *
	 * @param points raw point list
	 * @param fillRule SVG fill rule
	 * @return polygon path, or null when too few coordinates are present
	 */
	private static Shape parsePolygon(String points, String fillRule) {
		double[] values = parseNumbers(points);
		if (values.length < 4) {
			return null;
		}
		Path2D.Double path = new Path2D.Double(windingRule(fillRule));
		path.moveTo(values[0], values[1]);
		for (int i = 2; i + 1 < values.length; i += 2) {
			path.lineTo(values[i], values[i + 1]);
		}
		path.closePath();
		return path;
	}

	/**
	 * Converts an SVG fill rule into a Java2D winding rule.
	 *
	 * @param fillRule SVG fill-rule value
	 * @return Java2D winding rule
	 */
	private static int windingRule(String fillRule) {
		return "evenodd".equals(fillRule) ? Path2D.WIND_EVEN_ODD : Path2D.WIND_NON_ZERO;
	}

	/**
	 * Parses SVG XML with external entity access disabled.
	 *
	 * @param svgText SVG source text
	 * @return DOM document
	 */
	private static org.w3c.dom.Document parseXml(String svgText) {
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
	 * Parses the SVG viewport from {@code viewBox} or dimensions.
	 *
	 * @param root root SVG element
	 * @return parsed view box
	 */
	private static ViewBox parseViewBox(Element root) {
		double[] values = parseNumbers(attribute(root, "viewBox"));
		if (values.length == 4 && values[2] > 0.0 && values[3] > 0.0) {
			return new ViewBox(values[0], values[1], values[2], values[3]);
		}

		double width = parseSize(attribute(root, "width"), 1.0);
		double height = parseSize(attribute(root, "height"), 1.0);
		return new ViewBox(0.0, 0.0, Math.max(1.0, width), Math.max(1.0, height));
	}

	/**
	 * Parses SVG gradient definitions used by vector shadows.
	 *
	 * @param root root SVG element
	 * @return gradients by id
	 */
	private static Map<String, GradientPaint> parseGradients(Element root) {
		Map<String, GradientPaint> gradients = new HashMap<>();
		NodeList linear = root.getElementsByTagNameNS("*", "linearGradient");
		for (int i = 0; i < linear.getLength(); i++) {
			Element element = (Element) linear.item(i);
			LinearGradient gradient = parseLinearGradient(element);
			if (gradient.id != null && !gradient.id.isBlank()) {
				gradients.put(gradient.id, gradient);
			}
		}
		NodeList radial = root.getElementsByTagNameNS("*", "radialGradient");
		for (int i = 0; i < radial.getLength(); i++) {
			Element element = (Element) radial.item(i);
			RadialGradient gradient = parseRadialGradient(element);
			if (gradient.id != null && !gradient.id.isBlank()) {
				gradients.put(gradient.id, gradient);
			}
		}
		return gradients;
	}

	/**
	 * Parses one SVG linear gradient.
	 *
	 * @param element gradient element
	 * @return parsed gradient
	 */
	private static LinearGradient parseLinearGradient(Element element) {
		GradientStops stops = parseGradientStops(element);
		return new LinearGradient(
				element.getAttribute("id"),
				parseSize(attribute(element, "x1"), 0.0),
				parseSize(attribute(element, "y1"), 0.0),
				parseSize(attribute(element, "x2"), 1.0),
				parseSize(attribute(element, "y2"), 0.0),
				stops.start,
				stops.end);
	}

	/**
	 * Parses one SVG radial gradient.
	 *
	 * @param element gradient element
	 * @return parsed gradient
	 */
	private static RadialGradient parseRadialGradient(Element element) {
		GradientStops stops = parseGradientStops(element);
		double cx = parseSize(attribute(element, "cx"), 0.5);
		double cy = parseSize(attribute(element, "cy"), 0.5);
		return new RadialGradient(
				element.getAttribute("id"),
				cx,
				cy,
				parseSize(attribute(element, "r"), 0.5),
				stops.start,
				stops.end);
	}

	/**
	 * Parses the first and last stop from a gradient.
	 *
	 * @param gradient gradient element
	 * @return gradient endpoints
	 */
	private static GradientStops parseGradientStops(Element gradient) {
		NodeList nodes = gradient.getElementsByTagNameNS("*", "stop");
		if (nodes.getLength() == 0) {
			return new GradientStops(new GradientStop(0.0, Color.BLACK),
					new GradientStop(1.0, Color.WHITE));
		}
		GradientStop first = parseGradientStop((Element) nodes.item(0));
		GradientStop last = parseGradientStop((Element) nodes.item(nodes.getLength() - 1));
		return new GradientStops(first, last);
	}

	/**
	 * Parses one gradient stop, compositing stop opacity over white for PDF
	 * shadings.
	 *
	 * @param element stop element
	 * @return parsed stop
	 */
	private static GradientStop parseGradientStop(Element element) {
		double offset = normalizeStopOffset(attribute(element, "offset"));
		Color color = parseStopColor(attribute(element, "stop-color"));
		double opacity = clamp01(parseSize(attribute(element, "stop-opacity"), 1.0) * (color.getAlpha() / 255.0));
		return new GradientStop(offset, compositeOverWhite(color, opacity));
	}

	/**
	 * Parses a gradient stop color, falling back to black for missing or disabled
	 * stop paint.
	 *
	 * @param value raw stop-color value
	 * @return non-null stop color
	 */
	private static Color parseStopColor(String value) {
		String effective = value == null || value.isBlank() ? "#000000" : value;
		Color color = parseColor(effective);
		return color == null ? Color.BLACK : color;
	}

	/**
	 * Normalizes stop offsets from unit or percent notation.
	 *
	 * @param value source stop offset
	 * @return normalized offset
	 */
	private static double normalizeStopOffset(String value) {
		double offset = parseSize(value, 0.0);
		if (value != null && value.contains("%")) {
			offset /= 100.0;
		}
		return clamp01(offset);
	}

	/**
	 * Composites a color over white.
	 *
	 * @param color source color
	 * @param opacity source opacity
	 * @return opaque color equivalent on white paper
	 */
	private static Color compositeOverWhite(Color color, double opacity) {
		double alpha = clamp01(opacity);
		int red = (int) Math.round(color.getRed() * alpha + 255.0 * (1.0 - alpha));
		int green = (int) Math.round(color.getGreen() * alpha + 255.0 * (1.0 - alpha));
		int blue = (int) Math.round(color.getBlue() * alpha + 255.0 * (1.0 - alpha));
		return new Color(clampColor(red), clampColor(green), clampColor(blue));
	}

	/**
	 * Maps SVG font information to a PDF base font.
	 *
	 * @param family SVG font-family value
	 * @param weight SVG font-weight value
	 * @return closest PDF font
	 */
	private static Font resolveFont(String family, int weight) {
		String value = family == null ? "" : family.toLowerCase(Locale.ROOT);
		boolean bold = weight >= 600;
		if (value.contains("courier") || value.contains("mono")) {
			return Font.COURIER;
		}
		if (value.contains("times") || value.contains("serif")) {
			return bold ? Font.TIMES_BOLD : Font.TIMES_ROMAN;
		}
		return bold ? Font.HELVETICA_BOLD : Font.HELVETICA;
	}

	/**
	 * Normalizes SVG text content for one PDF text run.
	 *
	 * @param text source text
	 * @return single-line normalized text
	 */
	private static String normalizeText(String text) {
		if (text == null) {
			return "";
		}
		return text.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
	}

	/**
	 * Parses the first numeric component of an SVG size value.
	 *
	 * @param value raw size value
	 * @param fallback fallback when no number is present
	 * @return parsed size
	 */
	private static double parseSize(String value, double fallback) {
		double[] values = parseNumbers(value);
		return values.length > 0 ? values[0] : fallback;
	}

	/**
	 * Parses all numeric tokens from a string.
	 *
	 * @param value source string
	 * @return parsed number array
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
	 * Resolves an attribute from direct XML attributes or inline style.
	 *
	 * @param element source element
	 * @param name attribute name
	 * @return resolved value, or null
	 */
	private static String attribute(Element element, String name) {
		Map<String, String> style = parseStyle(element.getAttribute("style"));
		String inline = style.get(name);
		if (inline != null && !inline.isBlank()) {
			return inline.trim();
		}
		String direct = element.getAttribute(name);
		return direct == null || direct.isBlank() ? null : direct.trim();
	}

	/**
	 * Parses an SVG inline style declaration.
	 *
	 * @param value raw style attribute
	 * @return style property map
	 */
	private static Map<String, String> parseStyle(String value) {
		Map<String, String> map = new HashMap<>();
		if (value == null || value.isBlank()) {
			return map;
		}
		String[] entries = value.split(";");
		for (String entry : entries) {
			int colon = entry.indexOf(':');
			if (colon <= 0) {
				continue;
			}
			String key = entry.substring(0, colon).trim();
			String val = entry.substring(colon + 1).trim();
			if (!key.isEmpty() && !val.isEmpty()) {
				map.put(key, val);
			}
		}
		return map;
	}

	/**
	 * Returns an element local name with a DOM node-name fallback.
	 *
	 * @param element source element
	 * @return local element name
	 */
	private static String localName(Element element) {
		String localName = element.getLocalName();
		if (localName != null && !localName.isBlank()) {
			return localName;
		}
		return element.getNodeName();
	}

	/**
	 * Parses an SVG color value supported by the renderer.
	 *
	 * @param value raw color string
	 * @return parsed color, or null for {@code none}
	 */
	private static Color parseColor(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String trimmed = value.trim();
		String lower = safeLower(trimmed);
		if ("none".equals(lower)) {
			return null;
		}
		if ("black".equals(lower)) {
			return Color.BLACK;
		}
		if ("white".equals(lower)) {
			return Color.WHITE;
		}
		return parseStructuredColor(trimmed, value);
	}

	/**
	 * Parses structured SVG color syntaxes after named colors have been handled.
	 *
	 * @param trimmed trimmed color value
	 * @param original original color value for diagnostics
	 * @return parsed color
	 */
	private static Color parseStructuredColor(String trimmed, String original) {
		if (trimmed == null || trimmed.isBlank()) {
			return null;
		}
		if (trimmed.startsWith("#")) {
			return parseHexColor(trimmed.substring(1), original);
		}
		if (safeLower(trimmed).startsWith("rgb")) {
			return parseRgbColor(trimmed, original);
		}
		throw new IllegalArgumentException("Unsupported color: " + original);
	}

	/**
	 * Parses an SVG hexadecimal color.
	 *
	 * @param hex hexadecimal color without the leading hash
	 * @param original original color value for diagnostics
	 * @return parsed color
	 */
	private static Color parseHexColor(String hex, String original) {
		return switch (hex.length()) {
			case 3 -> new Color(hexNibble(hex, 0), hexNibble(hex, 1), hexNibble(hex, 2));
			case 4 -> new Color(hexNibble(hex, 0), hexNibble(hex, 1), hexNibble(hex, 2), hexNibble(hex, 3));
			case 6 -> parseLongHexColor(hex);
			case 8 -> parseLongHexColorWithAlpha(hex);
			default -> throw new IllegalArgumentException("Unsupported color: " + original);
		};
	}

	/**
	 * Expands one short-form hexadecimal color nibble into a byte channel.
	 *
	 * @param hex hexadecimal color
	 * @param index nibble index
	 * @return expanded byte channel
	 */
	private static int hexNibble(String hex, int index) {
		String digit = hex.substring(index, index + 1);
		return Integer.parseInt(digit + digit, 16);
	}

	/**
	 * Parses a six-digit hexadecimal color.
	 *
	 * @param hex hexadecimal color
	 * @return parsed RGB color
	 */
	private static Color parseLongHexColor(String hex) {
		int rgb = Integer.parseInt(hex, 16);
		return new Color((rgb >>> 16) & 0xFF, (rgb >>> 8) & 0xFF, rgb & 0xFF);
	}

	/**
	 * Parses an eight-digit hexadecimal RGBA color.
	 *
	 * @param hex hexadecimal color
	 * @return parsed RGBA color
	 */
	private static Color parseLongHexColorWithAlpha(String hex) {
		long rgba = Long.parseLong(hex, 16);
		return new Color(
				(int) ((rgba >>> 24) & 0xFF),
				(int) ((rgba >>> 16) & 0xFF),
				(int) ((rgba >>> 8) & 0xFF),
				(int) (rgba & 0xFF));
	}

	/**
	 * Parses an SVG {@code rgb(...)} color.
	 *
	 * @param value raw rgb function value
	 * @param original original color value for diagnostics
	 * @return parsed color
	 */
	private static Color parseRgbColor(String value, String original) {
		double[] values = parseNumbers(value);
		if (values.length < 3) {
			throw new IllegalArgumentException("Unsupported color: " + original);
		}
		return new Color(clampColor(values[0]), clampColor(values[1]), clampColor(values[2]));
	}

	/**
	 * Clamps a color channel into the 8-bit range.
	 *
	 * @param value source channel
	 * @return clamped channel
	 */
	private static int clampColor(double value) {
		if (value <= 0.0) {
			return 0;
		}
		if (value >= 255.0) {
			return 255;
		}
		return (int) Math.round(value);
	}

	/**
	 * Clamps a double into the unit interval.
	 *
	 * @param value source value
	 * @return clamped value
	 */
	private static double clamp01(double value) {
		if (value <= 0.0) {
			return 0.0;
		}
		if (value >= 1.0) {
			return 1.0;
		}
		return value;
	}

	/**
	 * Lowercases a nullable string using locale-stable rules.
	 *
	 * @param value source value
	 * @return lowercase value, or an empty string
	 */
	private static String safeLower(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT);
	}

	/**
	 * Parsed SVG view box.
	 *
	 * @param x left coordinate
	 * @param y top coordinate
	 * @param width view-box width
	 * @param height view-box height
	 */
	private record ViewBox(
		/**
		 * Stores the x.
		 */
		double x,
		/**
		 * Stores the y.
		 */
		double y,
		/**
		 * Stores the width.
		 */
		double width,
		/**
		 * Stores the height.
		 */
		double height
	) {
	}

	/**
	 * Marker for parsed SVG gradients.
	 *
	 * @since 2026
	 * @author Lennart A. Conrad
	 */
	private interface GradientPaint {
	}

	/**
	 * Parsed SVG linear gradient.
	 *
	 * @param id gradient id
	 * @param x1 start x coordinate
	 * @param y1 start y coordinate
	 * @param x2 end x coordinate
	 * @param y2 end y coordinate
	 * @param start first gradient stop
	 * @param end final gradient stop
	 */
	private record LinearGradient(
		/**
		 * Stores the id.
		 */
		String id,
		/**
		 * Stores the x1.
		 */
		double x1,
		/**
		 * Stores the y1.
		 */
		double y1,
		/**
		 * Stores the x2.
		 */
		double x2,
		/**
		 * Stores the y2.
		 */
		double y2,
		/**
		 * Stores the start.
		 */
		GradientStop start,
		/**
		 * Stores the end.
		 */
		GradientStop end
	) implements GradientPaint {
	}

	/**
	 * Parsed SVG radial gradient.
	 *
	 * @param id gradient id
	 * @param cx center x coordinate
	 * @param cy center y coordinate
	 * @param r radius
	 * @param start first gradient stop
	 * @param end final gradient stop
	 */
	private record RadialGradient(
		/**
		 * Stores the id.
		 */
		String id,
		/**
		 * Stores the cx.
		 */
		double cx,
		/**
		 * Stores the cy.
		 */
		double cy,
		/**
		 * Stores the r.
		 */
		double r,
		/**
		 * Stores the start.
		 */
		GradientStop start,
		/**
		 * Stores the end.
		 */
		GradientStop end
	) implements GradientPaint {
	}

	/**
	 * Parsed SVG gradient stop.
	 *
	 * @param offset stop offset in the unit interval
	 * @param color stop color
	 */
	private record GradientStop(
		/**
		 * Stores the offset.
		 */
		double offset,
		/**
		 * Stores the color.
		 */
		Color color
	) {
	}

	/**
	 * Parsed gradient endpoints.
	 *
	 * @param start first gradient stop
	 * @param end final gradient stop
	 */
	private record GradientStops(
		/**
		 * Stores the start.
		 */
		GradientStop start,
		/**
		 * Stores the end.
		 */
		GradientStop end
	) {
	}

	/**
	 * Resolved SVG style state inherited through the DOM tree.
	 */
	@SuppressWarnings("java:S107")
	private static final class StyleState {

		/**
		 * Fill color, or null when disabled.
		 */
		private final Color fill;

		/**
		 * Referenced gradient fill id, or null for solid fills.
		 */
		private final String fillReference;

		/**
		 * Stroke color, or null when disabled.
		 */
		private final Color stroke;

		/**
		 * Inherited group opacity.
		 */
		private final double opacity;

		/**
		 * Fill-specific opacity.
		 */
		private final double fillOpacity;

		/**
		 * Stroke-specific opacity.
		 */
		private final double strokeOpacity;

		/**
		 * SVG stroke width.
		 */
		private final double strokeWidth;

		/**
		 * SVG fill rule.
		 */
		private final String fillRule;

		/**
		 * SVG stroke-linejoin value.
		 */
		private final String lineJoin;

		/**
		 * SVG stroke-linecap value.
		 */
		private final String lineCap;

		/**
		 * SVG font-family value.
		 */
		private final String fontFamily;

		/**
		 * SVG font size.
		 */
		private final double fontSize;

		/**
		 * SVG font weight.
		 */
		private final int fontWeight;

		/**
		 * Creates one resolved style state.
		 *
		 * @param fill fill color
		 * @param fillReference referenced gradient fill id
		 * @param stroke stroke color
		 * @param opacity inherited opacity
		 * @param fillOpacity fill opacity
		 * @param strokeOpacity stroke opacity
		 * @param strokeWidth stroke width
		 * @param fillRule fill rule
		 * @param lineJoin line-join style
		 * @param lineCap line-cap style
		 * @param fontFamily font family
		 * @param fontSize font size
		 * @param fontWeight font weight
		 */
		private StyleState(Color fill, String fillReference, Color stroke, double opacity, double fillOpacity,
				double strokeOpacity, double strokeWidth, String fillRule, String lineJoin, String lineCap,
				String fontFamily, double fontSize, int fontWeight) {
			this.fill = fill;
			this.fillReference = fillReference;
			this.stroke = stroke;
			this.opacity = opacity;
			this.fillOpacity = fillOpacity;
			this.strokeOpacity = strokeOpacity;
			this.strokeWidth = strokeWidth;
			this.fillRule = fillRule;
			this.lineJoin = lineJoin;
			this.lineCap = lineCap;
			this.fontFamily = fontFamily;
			this.fontSize = fontSize;
			this.fontWeight = fontWeight;
		}

		/**
		 * Computes effective fill opacity including inherited opacity.
		 *
		 * @return fill opacity in the unit interval
		 */
		private double effectiveFillOpacity() {
			if (fill == null) {
				return 0.0;
			}
			return clamp01(opacity * fillOpacity * (fill.getAlpha() / 255.0));
		}

		/**
		 * Computes effective stroke opacity including inherited opacity.
		 *
		 * @return stroke opacity in the unit interval
		 */
		private double effectiveStrokeOpacity() {
			if (stroke == null) {
				return 0.0;
			}
			return clamp01(opacity * strokeOpacity * (stroke.getAlpha() / 255.0));
		}

		/**
		 * Returns the root SVG default style state.
		 *
		 * @return default style state
		 */
		private static StyleState defaults() {
			return new StyleState(
					Color.BLACK,
					null,
					null,
					1.0,
					1.0,
					1.0,
					1.0,
					"nonzero",
					"miter",
					"butt",
					"Helvetica",
					16.0,
					400);
		}

		/**
		 * Resolves style for a child element.
		 *
		 * @param element child SVG element
		 * @return inherited child style
		 */
		private StyleState inherit(Element element) {
			String fillAttr = attribute(element, "fill");
			String strokeAttr = attribute(element, "stroke");
			String opacityAttr = attribute(element, "opacity");
			String fillOpacityAttr = attribute(element, "fill-opacity");
			String strokeOpacityAttr = attribute(element, "stroke-opacity");
			String strokeWidthAttr = attribute(element, "stroke-width");
			String fillRuleAttr = attribute(element, "fill-rule");
			String lineJoinAttr = attribute(element, "stroke-linejoin");
			String lineCapAttr = attribute(element, "stroke-linecap");
			String fontFamilyAttr = attribute(element, "font-family");
			String fontSizeAttr = attribute(element, "font-size");
			String fontWeightAttr = attribute(element, "font-weight");

			String nextFillReference = fillReference;
			Color nextFill = fill;
			if (fillAttr != null) {
				nextFillReference = parsePaintReference(fillAttr);
				nextFill = nextFillReference == null ? parseColor(fillAttr) : null;
			}
			Color nextStroke = strokeAttr == null ? stroke : parseColor(strokeAttr);
			double nextOpacity = opacityAttr == null ? opacity : clamp01(opacity * parseSize(opacityAttr, 1.0));
			double nextFillOpacity = fillOpacityAttr == null ? fillOpacity : clamp01(parseSize(fillOpacityAttr, fillOpacity));
			double nextStrokeOpacity = strokeOpacityAttr == null
					? strokeOpacity
					: clamp01(parseSize(strokeOpacityAttr, strokeOpacity));
			double nextStrokeWidth = strokeWidthAttr == null ? strokeWidth : Math.max(0.0, parseSize(strokeWidthAttr, 1.0));
			String nextFillRule = fillRuleAttr == null ? fillRule : safeLower(fillRuleAttr);
			String nextLineJoin = lineJoinAttr == null ? lineJoin : safeLower(lineJoinAttr);
			String nextLineCap = lineCapAttr == null ? lineCap : safeLower(lineCapAttr);
			String nextFontFamily = fontFamilyAttr == null ? fontFamily : fontFamilyAttr;
			double nextFontSize = fontSizeAttr == null ? fontSize : Math.max(0.1, parseSize(fontSizeAttr, fontSize));
			int nextFontWeight = fontWeightAttr == null ? fontWeight : parseFontWeight(fontWeightAttr);

			return new StyleState(
					nextFill,
					nextFillReference,
					nextStroke,
					nextOpacity,
					nextFillOpacity,
					nextStrokeOpacity,
					nextStrokeWidth,
					nextFillRule,
					nextLineJoin,
					nextLineCap,
					nextFontFamily,
					nextFontSize,
					nextFontWeight);
		}

		/**
		 * Parses an SVG paint-server reference of the form {@code url(#id)}.
		 *
		 * @param value raw paint value
		 * @return referenced id, or null when this is a solid paint
		 */
		private static String parsePaintReference(String value) {
			if (value == null) {
				return null;
			}
			String trimmed = value.trim();
			if (!trimmed.startsWith("url(")) {
				return null;
			}
			int hash = trimmed.indexOf('#');
			int end = trimmed.indexOf(')', Math.max(0, hash));
			if (hash < 0 || end <= hash + 1) {
				return null;
			}
			return trimmed.substring(hash + 1, end).trim();
		}

		/**
		 * Parses an SVG font-weight value.
		 *
		 * @param value raw font-weight value
		 * @return numeric font weight
		 */
		private static int parseFontWeight(String value) {
			String normalized = safeLower(value).replace("\"", "").replace("'", "");
			if ("bold".equals(normalized)) {
				return 700;
			}
			if ("normal".equals(normalized)) {
				return 400;
			}
			double[] numbers = parseNumbers(value);
			if (numbers.length > 0) {
				return (int) Math.round(numbers[0]);
			}
			return 400;
		}
	}

}
