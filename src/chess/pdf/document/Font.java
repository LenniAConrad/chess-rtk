package chess.pdf.document;

import java.awt.font.FontRenderContext;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.awt.font.LineMetrics;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

/**
 * Small wrapper around standard PDF base fonts with AWT-based measurement.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public enum Font {

	/**
	 * Helvetica regular.
	 */
	HELVETICA("F1", "Helvetica", Family.NIMBUS_SANS, java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN),

	/**
	 * Helvetica bold.
	 */
	HELVETICA_BOLD("F2", "Helvetica-Bold", Family.NIMBUS_SANS, java.awt.Font.SANS_SERIF, java.awt.Font.BOLD),

	/**
	 * Times Roman regular.
	 */
	TIMES_ROMAN("F3", "Times-Roman", Family.NIMBUS_ROMAN, java.awt.Font.SERIF, java.awt.Font.PLAIN),

	/**
	 * Times Roman bold.
	 */
	TIMES_BOLD("F4", "Times-Bold", Family.NIMBUS_ROMAN, java.awt.Font.SERIF, java.awt.Font.BOLD),

	/**
	 * Courier regular.
	 */
	COURIER("F5", "Courier", Family.NIMBUS_MONO, java.awt.Font.MONOSPACED, java.awt.Font.PLAIN),

	/**
	 * Helvetica oblique.
	 */
	HELVETICA_OBLIQUE("F6", "Helvetica-Oblique", Family.NIMBUS_SANS, java.awt.Font.SANS_SERIF,
			java.awt.Font.ITALIC),

	/**
	 * Helvetica bold oblique.
	 */
	HELVETICA_BOLD_OBLIQUE("F7", "Helvetica-BoldOblique", Family.NIMBUS_SANS, java.awt.Font.SANS_SERIF,
			java.awt.Font.BOLD | java.awt.Font.ITALIC),

	/**
	 * Times Roman italic.
	 */
	TIMES_ITALIC("F8", "Times-Italic", Family.NIMBUS_ROMAN, java.awt.Font.SERIF, java.awt.Font.ITALIC),

	/**
	 * Times Roman bold italic.
	 */
	TIMES_BOLD_ITALIC("F9", "Times-BoldItalic", Family.NIMBUS_ROMAN, java.awt.Font.SERIF,
			java.awt.Font.BOLD | java.awt.Font.ITALIC),

	/**
	 * Courier oblique.
	 */
	COURIER_OBLIQUE("F10", "Courier-Oblique", Family.NIMBUS_MONO, java.awt.Font.MONOSPACED,
			java.awt.Font.ITALIC),

	/**
	 * Courier bold.
	 */
	COURIER_BOLD("F11", "Courier-Bold", Family.NIMBUS_MONO, java.awt.Font.MONOSPACED, java.awt.Font.BOLD),

	/**
	 * Courier bold oblique.
	 */
	COURIER_BOLD_OBLIQUE("F12", "Courier-BoldOblique", Family.NIMBUS_MONO, java.awt.Font.MONOSPACED,
			java.awt.Font.BOLD | java.awt.Font.ITALIC),

	/**
	 * Latin Modern Roman regular, matching the old LaTeX book body font.
	 */
	LATIN_MODERN_ROMAN("F13", "LMRoman10-Regular", Family.LATIN_MODERN_ROMAN, java.awt.Font.SERIF,
			java.awt.Font.PLAIN, "/usr/share/texmf/fonts/opentype/public/lm/lmroman10-regular.otf"),

	/**
	 * Latin Modern Roman bold, matching the old LaTeX book heading font.
	 */
	LATIN_MODERN_BOLD("F14", "LMRoman10-Bold", Family.LATIN_MODERN_ROMAN, java.awt.Font.SERIF,
			java.awt.Font.BOLD, "/usr/share/texmf/fonts/opentype/public/lm/lmroman10-bold.otf"),

	/**
	 * Latin Modern Roman italic, matching the old LaTeX book caption font.
	 */
	LATIN_MODERN_ITALIC("F15", "LMRoman10-Italic", Family.LATIN_MODERN_ROMAN, java.awt.Font.SERIF,
			java.awt.Font.ITALIC, "/usr/share/texmf/fonts/opentype/public/lm/lmroman10-italic.otf"),

	/**
	 * Latin Modern Roman bold italic.
	 */
	LATIN_MODERN_BOLD_ITALIC("F16", "LMRoman10-BoldItalic", Family.LATIN_MODERN_ROMAN, java.awt.Font.SERIF,
			java.awt.Font.BOLD | java.awt.Font.ITALIC,
			"/usr/share/texmf/fonts/opentype/public/lm/lmroman10-bolditalic.otf");

	/**
	 * AWT font-family names used as local fallbacks.
	 */
	private static final class Family {

		/**
		 * Helvetica-compatible fallback family.
		 */
		private static final String NIMBUS_SANS = "Nimbus Sans";

		/**
		 * Times-compatible fallback family.
		 */
		private static final String NIMBUS_ROMAN = "Nimbus Roman";

		/**
		 * Courier-compatible fallback family.
		 */
		private static final String NIMBUS_MONO = "Nimbus Mono PS";

		/**
		 * Latin Modern fallback family.
		 */
		private static final String LATIN_MODERN_ROMAN = "Latin Modern Roman";

		/**
		 * Utility class; prevent instantiation.
		 */
		private Family() {
			// utility
		}
	}

	/**
	 * Shared font-render context used for deterministic text measurement.
	 */
	private static final FontRenderContext FONT_RENDER_CONTEXT = new FontRenderContext(null, true, true);

	/**
	 * PDF page-resource alias for this font.
	 */
	private final String resourceName;

	/**
	 * Standard PDF base-font name.
	 */
	private final String baseName;

	/**
	 * Logical AWT family used for measuring and vector fallback text.
	 */
	private final String awtFamily;

	/**
	 * AWT style flag used for font descriptors and base-font fallback.
	 */
	private final int awtStyle;

	/**
	 * Optional local OpenType font file used for PDF embedding.
	 */
	private final Path embeddedFontPath;

	/**
	 * Concrete AWT font used for measurements and vector fallback text.
	 */
	private final java.awt.Font awtBaseFont;

	/**
	 * Creates one base-font descriptor.
	 *
	 * @param resourceName PDF page-resource alias
	 * @param baseName standard PDF base-font name
	 * @param preferredAwtFamily preferred AWT family matching the PDF base font
	 * @param fallbackAwtFamily logical AWT family used when the preferred family is unavailable
	 * @param awtStyle AWT style flag
	 */
	Font(String resourceName, String baseName, String preferredAwtFamily, String fallbackAwtFamily, int awtStyle) {
		this(resourceName, baseName, preferredAwtFamily, fallbackAwtFamily, awtStyle, null);
	}

	/**
	 * Creates one font descriptor with an optional OpenType source file.
	 *
	 * @param resourceName PDF page-resource alias
	 * @param baseName PDF base-font name
	 * @param preferredAwtFamily preferred AWT family matching the PDF font
	 * @param fallbackAwtFamily logical AWT family used when the preferred family is unavailable
	 * @param awtStyle AWT style flag
	 * @param embeddedFontPath optional OpenType font path
	 */
	Font(String resourceName, String baseName, String preferredAwtFamily, String fallbackAwtFamily, int awtStyle,
			String embeddedFontPath) {
		this.resourceName = resourceName;
		this.baseName = baseName;
		this.awtStyle = awtStyle;
		this.embeddedFontPath = embeddedFontPath == null ? null : Path.of(embeddedFontPath);
		this.awtBaseFont = loadAwtBaseFont(this.embeddedFontPath, preferredAwtFamily, fallbackAwtFamily, awtStyle);
		this.awtFamily = awtBaseFont.getFamily(Locale.ROOT);
	}

	/**
	 * Returns the page-resource font alias used inside the PDF.
	 *
	 * @return resource name such as {@code F1}
	 */
	public String resourceName() {
		return resourceName;
	}

	/**
	 * Returns the standard PDF base-font name.
	 *
	 * @return base font name
	 */
	public String baseName() {
		return baseName;
	}

	/**
	 * Returns whether this font has a readable OpenType source file for embedding.
	 *
	 * @return true when a local font file can be embedded
	 */
	boolean canEmbedType1C() {
		return embeddedFontPath != null && Files.isRegularFile(embeddedFontPath);
	}

	/**
	 * Reads the embedded OpenType file and extracts its CFF table.
	 *
	 * @return CFF table bytes suitable for a PDF {@code FontFile3} stream
	 * @throws IOException if the local font file cannot be read
	 */
	byte[] type1CFontBytes() throws IOException {
		if (!canEmbedType1C()) {
			return new byte[0];
		}
		return extractOpenTypeTable(Files.readAllBytes(embeddedFontPath), "CFF ");
	}

	/**
	 * Returns the PDF font flags used in the font descriptor.
	 *
	 * @return PDF font descriptor flags
	 */
	int descriptorFlags() {
		int flags = 32;
		if (java.awt.Font.SERIF.equals(awtFamily) || awtFamily.toLowerCase(Locale.ROOT).contains("roman")) {
			flags |= 2;
		}
		if ((awtStyle & java.awt.Font.ITALIC) != 0) {
			flags |= 64;
		}
		if ((awtStyle & java.awt.Font.BOLD) != 0) {
			flags |= 262_144;
		}
		return flags;
	}

	/**
	 * Returns the PDF font bounding box at 1000 units per em.
	 *
	 * @return font bounding box text
	 */
	String descriptorFontBox() {
		Rectangle2D bounds = awtFont(1000.0).getMaxCharBounds(FONT_RENDER_CONTEXT);
		double left = Math.floor(bounds.getX());
		double bottom = Math.floor(-bounds.getMaxY());
		double right = Math.ceil(bounds.getMaxX());
		double top = Math.ceil(-bounds.getY());
		return "["
				+ Document.number(left) + " "
				+ Document.number(bottom) + " "
				+ Document.number(right) + " "
				+ Document.number(top) + "]";
	}

	/**
	 * Returns the PDF ascent in 1000-unit font coordinates.
	 *
	 * @return ascent value
	 */
	double descriptorAscent() {
		return awtFont(1000.0).getLineMetrics("Hg", FONT_RENDER_CONTEXT).getAscent();
	}

	/**
	 * Returns the PDF descent in 1000-unit font coordinates.
	 *
	 * @return descent value
	 */
	double descriptorDescent() {
		return -awtFont(1000.0).getLineMetrics("Hg", FONT_RENDER_CONTEXT).getDescent();
	}

	/**
	 * Returns the approximate PDF cap height in 1000-unit font coordinates.
	 *
	 * @return cap-height value
	 */
	double descriptorCapHeight() {
		Rectangle2D bounds = awtFont(1000.0).getStringBounds("H", FONT_RENDER_CONTEXT);
		return -bounds.getY();
	}

	/**
	 * Returns the italic angle used in the font descriptor.
	 *
	 * @return italic angle in degrees
	 */
	double descriptorItalicAngle() {
		return (awtStyle & java.awt.Font.ITALIC) == 0 ? 0.0 : -12.0;
	}

	/**
	 * Returns approximate character widths for printable ASCII glyphs.
	 *
	 * @return PDF widths array contents
	 */
	String asciiWidthsArray() {
		StringBuilder widths = new StringBuilder(512);
		widths.append('[');
		for (int ch = 32; ch <= 126; ch++) {
			if (ch > 32) {
				widths.append(' ');
			}
			widths.append(Document.number(awtFont(1000.0)
					.getStringBounds(String.valueOf((char) ch), FONT_RENDER_CONTEXT)
					.getWidth()));
		}
		widths.append(']');
		return widths.toString();
	}

	/**
	 * Measures rendered text width for layout calculations.
	 *
	 * @param text     text to measure
	 * @param fontSize requested font size in points
	 * @return approximate rendered width in points
	 */
	public double textWidth(String text, double fontSize) {
		if (text == null || text.isEmpty()) {
			return 0.0;
		}
		return awtFont(fontSize).getStringBounds(text, FONT_RENDER_CONTEXT).getWidth();
	}

	/**
	 * Returns the line height for the requested font size.
	 *
	 * @param fontSize requested font size in points
	 * @return measured line height in points
	 */
	public double lineHeight(double fontSize) {
		LineMetrics metrics = awtFont(fontSize).getLineMetrics("Hg", FONT_RENDER_CONTEXT);
		return metrics.getHeight();
	}

	/**
	 * Builds the matching AWT font used for measurement and vector text fallback.
	 *
	 * @param fontSize requested font size in points
	 * @return derived AWT font
	 */
	java.awt.Font awtFont(double fontSize) {
		float size = (float) Math.max(1.0, fontSize);
		return awtBaseFont.deriveFont(size);
	}

	/**
	 * Loads the concrete AWT font used for layout measurements.
	 *
	 * @param embeddedFontPath optional embedded font file path
	 * @param preferred preferred concrete font family
	 * @param fallback fallback logical font family
	 * @param awtStyle AWT style flag for base-font fallback
	 * @return concrete AWT font at unit size
	 */
	private static java.awt.Font loadAwtBaseFont(Path embeddedFontPath, String preferred, String fallback,
			int awtStyle) {
		if (embeddedFontPath != null && Files.isRegularFile(embeddedFontPath)) {
			try {
				return java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, embeddedFontPath.toFile()).deriveFont(1.0f);
			} catch (FontFormatException | IOException e) {
				// Fall through to installed-family lookup.
			}
		}
		String family = availableFontFamily(preferred, fallback);
		return new java.awt.Font(family, awtStyle, 1);
	}

	/**
	 * Extracts a named table from an OpenType font file.
	 *
	 * @param fontBytes complete OpenType font bytes
	 * @param tag four-character table tag
	 * @return table payload bytes, or an empty array when the table is missing
	 */
	private static byte[] extractOpenTypeTable(byte[] fontBytes, String tag) {
		if (fontBytes == null || fontBytes.length < 12 || tag == null || tag.length() != 4) {
			return new byte[0];
		}
		int tableCount = readUnsignedShort(fontBytes, 4);
		int directoryEnd = 12 + tableCount * 16;
		if (directoryEnd > fontBytes.length) {
			return new byte[0];
		}
		for (int i = 0; i < tableCount; i++) {
			int offset = 12 + i * 16;
			if (!matchesTag(fontBytes, offset, tag)) {
				continue;
			}
			int tableOffset = readInt(fontBytes, offset + 8);
			int tableLength = readInt(fontBytes, offset + 12);
			if (tableOffset < 0 || tableLength < 0 || tableOffset + tableLength > fontBytes.length) {
				return new byte[0];
			}
			return Arrays.copyOfRange(fontBytes, tableOffset, tableOffset + tableLength);
		}
		return new byte[0];
	}

	/**
	 * Returns whether the bytes at the supplied offset match a four-character tag.
	 *
	 * @param bytes source bytes
	 * @param offset tag offset
	 * @param tag expected tag
	 * @return true when all four tag bytes match
	 */
	private static boolean matchesTag(byte[] bytes, int offset, String tag) {
		for (int i = 0; i < 4; i++) {
			if (offset + i >= bytes.length || bytes[offset + i] != (byte) tag.charAt(i)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Reads an unsigned 16-bit big-endian integer.
	 *
	 * @param bytes source bytes
	 * @param offset integer offset
	 * @return integer value, or zero when the offset is out of range
	 */
	private static int readUnsignedShort(byte[] bytes, int offset) {
		if (offset < 0 || offset + 1 >= bytes.length) {
			return 0;
		}
		return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
	}

	/**
	 * Reads a signed 32-bit big-endian integer.
	 *
	 * @param bytes source bytes
	 * @param offset integer offset
	 * @return integer value, or -1 when the offset is out of range
	 */
	private static int readInt(byte[] bytes, int offset) {
		if (offset < 0 || offset + 3 >= bytes.length) {
			return -1;
		}
		return ((bytes[offset] & 0xFF) << 24)
				| ((bytes[offset + 1] & 0xFF) << 16)
				| ((bytes[offset + 2] & 0xFF) << 8)
				| (bytes[offset + 3] & 0xFF);
	}

	/**
	 * Returns an installed font family when available.
	 *
	 * @param preferred preferred concrete font family
	 * @param fallback fallback logical font family
	 * @return available font family
	 */
	private static String availableFontFamily(String preferred, String fallback) {
		String target = preferred.toLowerCase(Locale.ROOT);
		boolean available = Arrays.stream(GraphicsEnvironment
				.getLocalGraphicsEnvironment()
				.getAvailableFontFamilyNames())
				.anyMatch(name -> name.toLowerCase(Locale.ROOT).equals(target));
		return available ? preferred : fallback;
	}
}
