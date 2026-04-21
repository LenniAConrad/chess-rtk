package chess.pdf.document;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.zip.DeflaterOutputStream;

/**
 * One PDF page with a drawing canvas, text content stream, and image resources.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
@SuppressWarnings("java:S107")
public final class Page {

	/**
	 * Physical page size.
	 */
	private final PageSize size;

	/**
	 * Drawing API attached to this page.
	 */
	private final Canvas canvas;

	/**
	 * Raw PDF content-stream commands.
	 */
	private final StringBuilder content = new StringBuilder(8_192);

	/**
	 * Fonts referenced by this page.
	 */
	private final EnumSet<Font> fonts = EnumSet.noneOf(Font.class);

	/**
	 * Image resources referenced by this page.
	 */
	private final List<ImageResource> images = new ArrayList<>();

	/**
	 * Opacity resources referenced by this page.
	 */
	private final List<OpacityResource> opacityResources = new ArrayList<>();

	/**
	 * Gradient shading resources referenced by this page.
	 */
	private final List<ShadingResource> shadingResources = new ArrayList<>();

	/**
	 * Link annotations attached to this page.
	 */
	private final List<LinkAnnotation> links = new ArrayList<>();

	/**
	 * Creates a page with the requested size.
	 *
	 * @param size physical page size
	 */
	Page(PageSize size) {
		this.size = size;
		this.canvas = new Canvas(this);
	}

	/**
	 * Returns the drawing canvas for this page.
	 *
	 * @return page canvas
	 */
	public Canvas canvas() {
		return canvas;
	}

	/**
	 * Returns the page size.
	 *
	 * @return page size
	 */
	public PageSize getSize() {
		return size;
	}

	/**
	 * Returns the page width in points.
	 *
	 * @return page width
	 */
	public double getWidth() {
		return size.getWidth();
	}

	/**
	 * Returns the page height in points.
	 *
	 * @return page height
	 */
	public double getHeight() {
		return size.getHeight();
	}

	/**
	 * Appends raw PDF drawing commands to the content stream.
	 *
	 * @param value raw command text
	 */
	void append(String value) {
		content.append(value);
	}

	/**
	 * Marks a font as used by this page.
	 *
	 * @param font font referenced by text commands
	 */
	void useFont(Font font) {
		fonts.add(font);
	}

	/**
	 * Returns the finished content stream text.
	 *
	 * @return raw PDF content stream
	 */
	String contentStream() {
		return content.toString();
	}

	/**
	 * Returns the fonts used by this page.
	 *
	 * @return defensive copy of used fonts
	 */
	EnumSet<Font> usedFonts() {
		return fonts.isEmpty() ? EnumSet.noneOf(Font.class) : EnumSet.copyOf(fonts);
	}

	/**
	 * Returns image resources attached to this page.
	 *
	 * @return live image resource list
	 */
	List<ImageResource> images() {
		return images;
	}

	/**
	 * Adds an image resource and returns its PDF resource name.
	 *
	 * @param image image to embed
	 * @return image resource name
	 */
	String addImage(BufferedImage image) {
		ImageResource resource = ImageResource.from(image, "Im" + (images.size() + 1));
		images.add(resource);
		return resource.name;
	}

	/**
	 * Returns opacity graphics-state resources attached to this page.
	 *
	 * @return live opacity resource list
	 */
	List<OpacityResource> opacityResources() {
		return opacityResources;
	}

	/**
	 * Adds or reuses an opacity graphics state.
	 *
	 * @param fillOpacity fill opacity
	 * @param strokeOpacity stroke opacity
	 * @return graphics-state resource name, or null for fully opaque drawing
	 */
	String addOpacity(double fillOpacity, double strokeOpacity) {
		double safeFill = clampOpacity(fillOpacity);
		double safeStroke = clampOpacity(strokeOpacity);
		if (safeFill >= 0.999 && safeStroke >= 0.999) {
			return null;
		}
		for (OpacityResource resource : opacityResources) {
			if (sameOpacity(resource.fillOpacity, safeFill) && sameOpacity(resource.strokeOpacity, safeStroke)) {
				return resource.name;
			}
		}
		String name = "Gs" + (opacityResources.size() + 1);
		opacityResources.add(new OpacityResource(name, safeFill, safeStroke));
		return name;
	}

	/**
	 * Returns gradient shading resources attached to this page.
	 *
	 * @return live shading resource list
	 */
	List<ShadingResource> shadingResources() {
		return shadingResources;
	}

	/**
	 * Adds one axial gradient shading resource.
	 *
	 * @param x0 start x in PDF coordinates
	 * @param y0 start y in PDF coordinates
	 * @param x1 end x in PDF coordinates
	 * @param y1 end y in PDF coordinates
	 * @param start start color
	 * @param end end color
	 * @return shading resource name
	 */
	String addAxialShading(double x0, double y0, double x1, double y1, Color start, Color end) {
		String name = "Sh" + (shadingResources.size() + 1);
		shadingResources.add(ShadingResource.axial(name, x0, y0, x1, y1, start, end));
		return name;
	}

	/**
	 * Adds one radial gradient shading resource.
	 *
	 * @param x0 inner-circle center x in PDF coordinates
	 * @param y0 inner-circle center y in PDF coordinates
	 * @param r0 inner-circle radius
	 * @param x1 outer-circle center x in PDF coordinates
	 * @param y1 outer-circle center y in PDF coordinates
	 * @param r1 outer-circle radius
	 * @param start start color
	 * @param end end color
	 * @return shading resource name
	 */
	String addRadialShading(double x0, double y0, double r0, double x1, double y1, double r1,
			Color start, Color end) {
		String name = "Sh" + (shadingResources.size() + 1);
		shadingResources.add(ShadingResource.radial(name, x0, y0, r0, x1, y1, r1, start, end));
		return name;
	}

	/**
	 * Adds an invisible link annotation that jumps to another page.
	 *
	 * @param x left edge in top-left page coordinates
	 * @param y top edge in top-left page coordinates
	 * @param width link rectangle width
	 * @param height link rectangle height
	 * @param targetPageNumber one-based destination page number
	 */
	void addPageLink(double x, double y, double width, double height, int targetPageNumber) {
		if (width <= 0.0 || height <= 0.0 || targetPageNumber <= 0) {
			return;
		}
		links.add(new LinkAnnotation(x, y, width, height, targetPageNumber));
	}

	/**
	 * Returns link annotations attached to this page.
	 *
	 * @return live link annotation list
	 */
	List<LinkAnnotation> links() {
		return links;
	}

	/**
	 * Compares opacity values with the same precision used for serialization.
	 *
	 * @param left first opacity
	 * @param right second opacity
	 * @return true when the values are effectively equal
	 */
	private static boolean sameOpacity(double left, double right) {
		return Math.abs(left - right) < 0.0001;
	}

	/**
	 * Clamps an opacity value into the PDF-supported unit interval.
	 *
	 * @param value source opacity
	 * @return clamped opacity
	 */
	private static double clampOpacity(double value) {
		if (value <= 0.0) {
			return 0.0;
		}
		if (value >= 1.0) {
			return 1.0;
		}
		return value;
	}

	/**
	 * Package-private image resource used during final PDF serialization.
	 */
	static final class ImageResource {

		/**
		 * PDF resource name.
		 */
		final String name;

		/**
		 * Image width in pixels.
		 */
		final int width;

		/**
		 * Image height in pixels.
		 */
		final int height;

		/**
		 * Deflated RGB byte payload.
		 */
		final byte[] bytes;

		/**
		 * Creates an image resource.
		 *
		 * @param name PDF resource name
		 * @param width image width
		 * @param height image height
		 * @param bytes deflated RGB bytes
		 */
		private ImageResource(String name, int width, int height, byte[] bytes) {
			this.name = name;
			this.width = width;
			this.height = height;
			this.bytes = bytes;
		}

		/**
		 * Converts a Java image into a flattened PDF image resource.
		 *
		 * @param image source image
		 * @param name PDF resource name
		 * @return image resource
		 */
		private static ImageResource from(BufferedImage image, String name) {
			if (image == null) {
				throw new IllegalArgumentException("image cannot be null");
			}
			BufferedImage flattened = flatten(image);
			byte[] rgb = new byte[flattened.getWidth() * flattened.getHeight() * 3];
			int offset = 0;
			for (int y = 0; y < flattened.getHeight(); y++) {
				for (int x = 0; x < flattened.getWidth(); x++) {
					int argb = flattened.getRGB(x, y);
					rgb[offset++] = (byte) ((argb >>> 16) & 0xFF);
					rgb[offset++] = (byte) ((argb >>> 8) & 0xFF);
					rgb[offset++] = (byte) (argb & 0xFF);
				}
			}
			return new ImageResource(name, flattened.getWidth(), flattened.getHeight(), deflate(rgb));
		}

		/**
		 * Flattens transparency onto a white RGB background.
		 *
		 * @param image source image
		 * @return RGB image
		 */
		private static BufferedImage flatten(BufferedImage image) {
			BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
			Graphics2D g = rgb.createGraphics();
			g.setColor(Color.WHITE);
			g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
			g.drawImage(image, 0, 0, null);
			g.dispose();
			return rgb;
		}

		/**
		 * Compresses raw image bytes with Flate encoding.
		 *
		 * @param bytes raw RGB bytes
		 * @return compressed bytes
		 */
		private static byte[] deflate(byte[] bytes) {
			try (ByteArrayOutputStream out = new ByteArrayOutputStream();
					DeflaterOutputStream zip = new DeflaterOutputStream(out)) {
				zip.write(bytes);
				zip.finish();
				return out.toByteArray();
			} catch (IOException e) {
				throw new IllegalStateException("failed to compress image bytes", e);
			}
		}
	}

	/**
	 * Package-private opacity resource used during final PDF serialization.
	 */
	static final class OpacityResource {

		/**
		 * PDF graphics-state resource name.
		 */
		final String name;

		/**
		 * Fill opacity.
		 */
		final double fillOpacity;

		/**
		 * Stroke opacity.
		 */
		final double strokeOpacity;

		/**
		 * Creates an opacity resource.
		 *
		 * @param name PDF graphics-state resource name
		 * @param fillOpacity fill opacity
		 * @param strokeOpacity stroke opacity
		 */
		private OpacityResource(String name, double fillOpacity, double strokeOpacity) {
			this.name = name;
			this.fillOpacity = fillOpacity;
			this.strokeOpacity = strokeOpacity;
		}
	}

	/**
	 * Package-private gradient shading resource used during final PDF serialization.
	 */
	static final class ShadingResource {

		/**
		 * PDF shading resource name.
		 */
		final String name;

		/**
		 * PDF shading type.
		 */
		final int type;

		/**
		 * PDF shading coordinate array.
		 */
		final double[] coords;

		/**
		 * Start color.
		 */
		final Color start;

		/**
		 * End color.
		 */
		final Color end;

		/**
		 * Creates a shading resource.
		 *
		 * @param name resource name
		 * @param type PDF shading type
		 * @param coords PDF coordinate array
		 * @param start start color
		 * @param end end color
		 */
		private ShadingResource(String name, int type, double[] coords, Color start, Color end) {
			this.name = name;
			this.type = type;
			this.coords = coords;
			this.start = start == null ? Color.BLACK : start;
			this.end = end == null ? Color.WHITE : end;
		}

		/**
		 * Creates an axial shading.
		 *
		 * @param name resource name
		 * @param x0 start x
		 * @param y0 start y
		 * @param x1 end x
		 * @param y1 end y
		 * @param start start color
		 * @param end end color
		 * @return shading resource
		 */
		private static ShadingResource axial(String name, double x0, double y0, double x1, double y1,
				Color start, Color end) {
			return new ShadingResource(name, 2, new double[] { x0, y0, x1, y1 }, start, end);
		}

		/**
		 * Creates a radial shading.
		 *
		 * @param name resource name
		 * @param x0 inner center x
		 * @param y0 inner center y
		 * @param r0 inner radius
		 * @param x1 outer center x
		 * @param y1 outer center y
		 * @param r1 outer radius
		 * @param start start color
		 * @param end end color
		 * @return shading resource
		 */
		private static ShadingResource radial(String name, double x0, double y0, double r0, double x1,
				double y1, double r1, Color start, Color end) {
			return new ShadingResource(name, 3, new double[] { x0, y0, r0, x1, y1, r1 }, start, end);
		}
	}

	/**
	 * Package-private internal page-link annotation.
	 */
	static final class LinkAnnotation {

		/**
		 * Link rectangle left edge in top-left coordinates.
		 */
		final double x;

		/**
		 * Link rectangle top edge in top-left coordinates.
		 */
		final double y;

		/**
		 * Link rectangle width.
		 */
		final double width;

		/**
		 * Link rectangle height.
		 */
		final double height;

		/**
		 * One-based destination page number.
		 */
		final int targetPageNumber;

		/**
		 * Creates one internal link annotation.
		 *
		 * @param x link rectangle left edge
		 * @param y link rectangle top edge
		 * @param width link rectangle width
		 * @param height link rectangle height
		 * @param targetPageNumber one-based destination page number
		 */
		private LinkAnnotation(double x, double y, double width, double height, int targetPageNumber) {
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
			this.targetPageNumber = targetPageNumber;
		}
	}
}
