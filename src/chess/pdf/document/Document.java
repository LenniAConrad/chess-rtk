package chess.pdf.document;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal PDF 1.4 document writer with text, vector primitives, and embedded
 * RGB images.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Document {

	/**
	 * Suffix for an indirect PDF object reference.
	 */
	private static final String INDIRECT_REFERENCE_SUFFIX = " 0 R";

	/**
	 * Ordered physical pages in this document.
	 */
	private final List<Page> pages = new ArrayList<>();

	/**
	 * Optional PDF title metadata.
	 */
	private String title = "";

	/**
	 * Optional PDF author metadata.
	 */
	private String author = "";

	/**
	 * Optional PDF subject metadata.
	 */
	private String subject = "";

	/**
	 * PDF creator metadata.
	 */
	private String creator = "chess-rtk";

	/**
	 * PDF producer metadata.
	 */
	private String producer = "chess-rtk";

	/**
	 * Adds a new A4 page.
	 *
	 * @return created page
	 */
	public Page addPage() {
		return addPage(PageSize.A4);
	}

	/**
	 * Adds a page with the requested size.
	 *
	 * @param size page size
	 * @return created page
	 */
	public Page addPage(PageSize size) {
		Page page = new Page(size == null ? PageSize.A4 : size);
		pages.add(page);
		return page;
	}

	/**
	 * Sets the PDF title metadata.
	 *
	 * @param title document title
	 * @return this document
	 */
	public Document setTitle(String title) {
		this.title = normalizeMeta(title);
		return this;
	}

	/**
	 * Sets the PDF author metadata.
	 *
	 * @param author author name
	 * @return this document
	 */
	public Document setAuthor(String author) {
		this.author = normalizeMeta(author);
		return this;
	}

	/**
	 * Sets the PDF subject metadata.
	 *
	 * @param subject subject text
	 * @return this document
	 */
	public Document setSubject(String subject) {
		this.subject = normalizeMeta(subject);
		return this;
	}

	/**
	 * Sets the PDF creator metadata.
	 *
	 * @param creator creator text
	 * @return this document
	 */
	public Document setCreator(String creator) {
		this.creator = normalizeMeta(creator);
		return this;
	}

	/**
	 * Sets the PDF producer metadata.
	 *
	 * @param producer producer text
	 * @return this document
	 */
	public Document setProducer(String producer) {
		this.producer = normalizeMeta(producer);
		return this;
	}

	/**
	 * Writes the PDF file to disk.
	 *
	 * @param output output path
	 * @throws IOException if writing fails
	 */
	public void write(Path output) throws IOException {
		if (output == null) {
			throw new IllegalArgumentException("output cannot be null");
		}
		Path parent = output.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Files.write(output, toByteArray());
	}

	/**
	 * Builds the PDF as a byte array.
	 *
	 * @return encoded PDF bytes
	 */
	public byte[] toByteArray() {
		if (pages.isEmpty()) {
			throw new IllegalStateException("pdf document has no pages");
		}

		List<byte[]> objects = new ArrayList<>();
		int pagesId = reserveObject(objects);
		EnumMap<Font, Integer> fontObjectIds = createFontObjects(objects);
		List<Integer> pageIds = new ArrayList<>(pages.size());
		for (int i = 0; i < pages.size(); i++) {
			pageIds.add(reserveObject(objects));
		}

		for (int index = 0; index < pages.size(); index++) {
			Page page = pages.get(index);
			LinkedHashMap<String, Integer> imageObjectIds = new LinkedHashMap<>();
			for (Page.ImageResource image : page.images()) {
				String imageEntries = "/Type /XObject /Subtype /Image /Width " + image.width + " /Height "
						+ image.height + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode";
				int imageId = addStreamObject(objects, imageEntries, image.bytes);
				imageObjectIds.put(image.name, imageId);
			}

			LinkedHashMap<String, Integer> opacityObjectIds = new LinkedHashMap<>();
			for (Page.OpacityResource opacity : page.opacityResources()) {
				int opacityId = addObject(objects, "<< /Type /ExtGState /ca " + number(opacity.fillOpacity)
						+ " /CA " + number(opacity.strokeOpacity) + " >>");
				opacityObjectIds.put(opacity.name, opacityId);
			}

			LinkedHashMap<String, Integer> shadingObjectIds = new LinkedHashMap<>();
			for (Page.ShadingResource shading : page.shadingResources()) {
				int shadingId = addObject(objects, buildShadingObject(shading));
				shadingObjectIds.put(shading.name, shadingId);
			}

			byte[] contentBytes = page.contentStream().getBytes(StandardCharsets.US_ASCII);
			int contentId = addStreamObject(objects, "", contentBytes);
			List<Integer> annotationObjectIds = createAnnotationObjects(objects, page, pageIds);
			PageObjectContext pageContext = new PageObjectContext(
					pagesId,
					fontObjectIds,
					imageObjectIds,
					opacityObjectIds,
					shadingObjectIds,
					annotationObjectIds,
					contentId);
			setObject(objects, pageIds.get(index),
					buildPageObject(page, pageContext));
		}

		setObject(objects, pagesId, buildPagesObject(pageIds));
		int catalogId = addObject(objects, "<< /Type /Catalog /Pages " + indirectReference(pagesId) + " >>");
		int infoId = addObject(objects, buildInfoObject());
		return serialize(objects, catalogId, infoId);
	}

	/**
	 * Escapes a Java string for use inside a PDF literal string.
	 *
	 * @param text source text
	 * @return escaped ASCII-safe PDF literal content
	 */
	static String escape(String text) {
		StringBuilder escaped = new StringBuilder(text.length() + 16);
		for (int i = 0; i < text.length(); i++) {
			char ch = text.charAt(i);
			switch (ch) {
				case '\\', '(', ')' -> escaped.append('\\').append(ch);
				case '\r', '\n', '\t' -> escaped.append(' ');
				default -> {
					if (ch < 32 || ch > 126) {
						escaped.append('?');
					} else {
						escaped.append(ch);
					}
				}
			}
		}
		return escaped.toString();
	}

	/**
	 * Formats a double using compact PDF number syntax.
	 *
	 * @param value numeric value
	 * @return compact decimal string
	 */
	static String number(double value) {
		double rounded = Math.rint(value);
		if (Math.abs(value - rounded) < 0.0001) {
			return Long.toString(Math.round(rounded));
		}
		String text = String.format(Locale.ROOT, "%.3f", value);
		int end = text.length();
		while (end > 0 && text.charAt(end - 1) == '0') {
			end--;
		}
		if (end > 0 && text.charAt(end - 1) == '.') {
			end--;
		}
		return text.substring(0, end);
	}

	/**
	 * Formats an RGB color for PDF graphics commands.
	 *
	 * @param color source color
	 * @return three normalized color channels
	 */
	static String rgb(java.awt.Color color) {
		return number(color.getRed() / 255.0) + " "
				+ number(color.getGreen() / 255.0) + " "
				+ number(color.getBlue() / 255.0);
	}

	/**
	 * Formats an indirect object reference.
	 *
	 * @param objectId one-based object id
	 * @return PDF indirect object reference
	 */
	private static String indirectReference(int objectId) {
		return objectId + INDIRECT_REFERENCE_SUFFIX;
	}

	/**
	 * Creates PDF font objects for fonts used by at least one page.
	 *
	 * @param objects object list being serialized
	 * @return map from logical font to object id
	 */
	private EnumMap<Font, Integer> createFontObjects(List<byte[]> objects) {
		EnumSet<Font> usedFonts = EnumSet.noneOf(Font.class);
		for (Page page : pages) {
			usedFonts.addAll(page.usedFonts());
		}

		EnumMap<Font, Integer> fontIds = new EnumMap<>(Font.class);
		for (Font font : usedFonts) {
			int id = createFontObject(objects, font);
			fontIds.put(font, id);
		}
		return fontIds;
	}

	/**
	 * Creates either an embedded Type1C simple font or a base-font resource.
	 *
	 * @param objects object list being serialized
	 * @param font font descriptor
	 * @return font object id
	 */
	private int createFontObject(List<byte[]> objects, Font font) {
		if (!font.canEmbedType1C()) {
			return createBaseFontObject(objects, font);
		}
		try {
			byte[] fontBytes = font.type1CFontBytes();
			if (fontBytes.length == 0) {
				return createBaseFontObject(objects, font);
			}
			int fontFileId = addStreamObject(objects, "/Subtype /Type1C", fontBytes);
			int descriptorId = addObject(objects, buildFontDescriptor(font, fontFileId));
			return addObject(objects, buildEmbeddedSimpleFont(font, descriptorId));
		} catch (IOException e) {
			return createBaseFontObject(objects, font);
		}
	}

	/**
	 * Creates an unembedded simple Type1 font object.
	 *
	 * @param objects object list being serialized
	 * @param font font descriptor
	 * @return font object id
	 */
	private int createBaseFontObject(List<byte[]> objects, Font font) {
		return addObject(objects, "<< /Type /Font /Subtype /Type1 /BaseFont /" + font.baseName() + " >>");
	}

	/**
	 * Builds a font descriptor for an embedded Type1C font.
	 *
	 * @param font font descriptor
	 * @param fontFileId embedded font-file stream id
	 * @return PDF font descriptor dictionary
	 */
	private String buildFontDescriptor(Font font, int fontFileId) {
		return "<< /Type /FontDescriptor"
				+ " /FontName /" + font.baseName()
				+ " /Flags " + font.descriptorFlags()
				+ " /FontBBox " + font.descriptorFontBox()
				+ " /ItalicAngle " + number(font.descriptorItalicAngle())
				+ " /Ascent " + number(font.descriptorAscent())
				+ " /Descent " + number(font.descriptorDescent())
				+ " /CapHeight " + number(font.descriptorCapHeight())
				+ " /StemV 80"
				+ " /FontFile3 " + indirectReference(fontFileId) + " >>";
	}

	/**
	 * Builds the simple-font dictionary for an embedded Type1C font.
	 *
	 * @param font font descriptor
	 * @param descriptorId font descriptor object id
	 * @return PDF font dictionary
	 */
	private String buildEmbeddedSimpleFont(Font font, int descriptorId) {
		return "<< /Type /Font /Subtype /Type1"
				+ " /BaseFont /" + font.baseName()
				+ " /FirstChar 32 /LastChar 126"
				+ " /Widths " + font.asciiWidthsArray()
				+ " /Encoding /WinAnsiEncoding"
				+ " /FontDescriptor " + indirectReference(descriptorId) + " >>";
	}

	/**
	 * Creates PDF annotation objects for one page.
	 *
	 * @param objects object list being serialized
	 * @param page source page
	 * @param pageIds one-based page index to object id map
	 * @return annotation object ids
	 */
	private List<Integer> createAnnotationObjects(List<byte[]> objects, Page page, List<Integer> pageIds) {
		List<Integer> annotationIds = new ArrayList<>();
		for (Page.LinkAnnotation link : page.links()) {
			if (link.targetPageNumber < 1 || link.targetPageNumber > pageIds.size()) {
				continue;
			}
			int targetPageId = pageIds.get(link.targetPageNumber - 1);
			annotationIds.add(addObject(objects, buildPageLinkAnnotation(page, link, targetPageId)));
		}
		return annotationIds;
	}

	/**
	 * Builds one PDF axial or radial gradient shading dictionary.
	 *
	 * @param shading page shading resource
	 * @return PDF shading dictionary
	 */
	private String buildShadingObject(Page.ShadingResource shading) {
		StringBuilder coords = new StringBuilder(64);
		coords.append('[');
		for (int i = 0; i < shading.coords.length; i++) {
			if (i > 0) {
				coords.append(' ');
			}
			coords.append(number(shading.coords[i]));
		}
		coords.append(']');

		return "<< /ShadingType " + shading.type
				+ " /ColorSpace /DeviceRGB /Coords " + coords
				+ " /Function << /FunctionType 2 /Domain [0 1] /C0 "
				+ colorArray(shading.start) + " /C1 " + colorArray(shading.end)
				+ " /N 1 >> /Extend [true true] >>";
	}

	/**
	 * Converts a Java color into a PDF DeviceRGB function array.
	 *
	 * @param color source color
	 * @return PDF color array
	 */
	private static String colorArray(java.awt.Color color) {
		java.awt.Color safe = color == null ? java.awt.Color.BLACK : color;
		return "[" + number(safe.getRed() / 255.0)
				+ " " + number(safe.getGreen() / 255.0)
				+ " " + number(safe.getBlue() / 255.0) + "]";
	}

	/**
	 * Builds one invisible internal page-link annotation dictionary.
	 *
	 * @param page source page
	 * @param link link geometry and target
	 * @param targetPageId PDF object id of the target page
	 * @return annotation dictionary
	 */
	private String buildPageLinkAnnotation(Page page, Page.LinkAnnotation link, int targetPageId) {
		double left = link.x;
		double bottom = page.getHeight() - (link.y + link.height);
		double right = link.x + link.width;
		double top = page.getHeight() - link.y;
		return "<< /Type /Annot /Subtype /Link /Rect ["
				+ number(left) + " "
				+ number(bottom) + " "
				+ number(right) + " "
				+ number(top)
				+ "] /Border [0 0 0] /A << /S /GoTo /D ["
				+ indirectReference(targetPageId) + " /Fit] >> >>";
	}

	/**
	 * Builds the dictionary for one PDF page object.
	 *
	 * @param page page to serialize
	 * @param context page-object dependencies and resource ids
	 * @return page-object dictionary
	 */
	private String buildPageObject(Page page, PageObjectContext context) {
		StringBuilder resources = new StringBuilder(256);
		resources.append("<< /ProcSet [/PDF /Text /ImageC]");
		appendFontResources(resources, page.usedFonts(), context.fontObjectIds);
		appendNamedResources(resources, "XObject", context.imageObjectIds);
		appendNamedResources(resources, "ExtGState", context.opacityObjectIds);
		appendNamedResources(resources, "Shading", context.shadingObjectIds);
		resources.append(" >>");

		StringBuilder pageObject = new StringBuilder(256);
		pageObject.append("<< /Type /Page /Parent ").append(indirectReference(context.pagesId)).append(" /MediaBox [0 0 ")
				.append(number(page.getWidth())).append(' ')
				.append(number(page.getHeight())).append("] /Resources ")
				.append(resources).append(" /Contents ").append(indirectReference(context.contentId));
		if (!context.annotationObjectIds.isEmpty()) {
			pageObject.append(" /Annots [");
			for (int i = 0; i < context.annotationObjectIds.size(); i++) {
				if (i > 0) {
					pageObject.append(' ');
				}
				pageObject.append(indirectReference(context.annotationObjectIds.get(i)));
			}
			pageObject.append(']');
		}
		pageObject.append(" >>");
		return pageObject.toString();
	}

	/**
	 * Appends font resources used by one page.
	 *
	 * @param resources page resource builder
	 * @param usedFonts fonts used on the page
	 * @param fontObjectIds font resource object ids
	 */
	private void appendFontResources(
			StringBuilder resources,
			Collection<Font> usedFonts,
			Map<Font, Integer> fontObjectIds) {
		if (usedFonts.isEmpty()) {
			return;
		}
		resources.append(" /Font <<");
		for (Font font : usedFonts) {
			resources.append(" /").append(font.resourceName()).append(" ")
					.append(indirectReference(fontObjectIds.get(font)));
		}
		resources.append(" >>");
	}

	/**
	 * Appends a named resource dictionary when entries exist.
	 *
	 * @param resources page resource builder
	 * @param resourceName PDF resource category name
	 * @param objectIds resource-name to object-id mapping
	 */
	private void appendNamedResources(
			StringBuilder resources,
			String resourceName,
			Map<String, Integer> objectIds) {
		if (objectIds.isEmpty()) {
			return;
		}
		resources.append(" /").append(resourceName).append(" <<");
		for (Map.Entry<String, Integer> entry : objectIds.entrySet()) {
			resources.append(" /").append(entry.getKey()).append(" ")
					.append(indirectReference(entry.getValue()));
		}
		resources.append(" >>");
	}

	/**
	 * Dependencies required to serialize one page object.
	 */
	private static final class PageObjectContext {

		/**
		 * Parent pages-tree object id.
		 */
		private final int pagesId;

		/**
		 * Font resource object ids.
		 */
		private final Map<Font, Integer> fontObjectIds;

		/**
		 * Image resource object ids.
		 */
		private final Map<String, Integer> imageObjectIds;

		/**
		 * Opacity resource object ids.
		 */
		private final Map<String, Integer> opacityObjectIds;

		/**
		 * Shading resource object ids.
		 */
		private final Map<String, Integer> shadingObjectIds;

		/**
		 * Annotation object ids.
		 */
		private final List<Integer> annotationObjectIds;

		/**
		 * Page content-stream object id.
		 */
		private final int contentId;

		/**
		 * Creates a page-object context.
		 *
		 * @param pagesId parent pages-tree object id
		 * @param fontObjectIds font resource object ids
		 * @param imageObjectIds image resource object ids
		 * @param opacityObjectIds opacity state object ids
		 * @param shadingObjectIds shading resource object ids
		 * @param annotationObjectIds annotation object ids
		 * @param contentId content-stream object id
		 */
		private PageObjectContext(
				int pagesId,
				Map<Font, Integer> fontObjectIds,
				Map<String, Integer> imageObjectIds,
				Map<String, Integer> opacityObjectIds,
				Map<String, Integer> shadingObjectIds,
				List<Integer> annotationObjectIds,
				int contentId) {
			this.pagesId = pagesId;
			this.fontObjectIds = fontObjectIds;
			this.imageObjectIds = imageObjectIds;
			this.opacityObjectIds = opacityObjectIds;
			this.shadingObjectIds = shadingObjectIds;
			this.annotationObjectIds = annotationObjectIds;
			this.contentId = contentId;
		}
	}

	/**
	 * Builds the root pages-tree object.
	 *
	 * @param pageIds child page-object ids
	 * @return pages-tree dictionary
	 */
	private String buildPagesObject(List<Integer> pageIds) {
		StringBuilder kids = new StringBuilder(128);
		kids.append("[");
		for (int i = 0; i < pageIds.size(); i++) {
			if (i > 0) {
				kids.append(' ');
			}
			kids.append(indirectReference(pageIds.get(i)));
		}
		kids.append("]");
		return "<< /Type /Pages /Count " + pageIds.size() + " /Kids " + kids + " >>";
	}

	/**
	 * Builds the PDF document information dictionary.
	 *
	 * @return information dictionary
	 */
	private String buildInfoObject() {
		StringBuilder info = new StringBuilder(128);
		info.append("<<");
		appendMeta(info, "Title", title);
		appendMeta(info, "Author", author);
		appendMeta(info, "Subject", subject);
		appendMeta(info, "Creator", creator);
		appendMeta(info, "Producer", producer);
		info.append(" >>");
		return info.toString();
	}

	/**
	 * Appends one metadata field when it has a nonblank value.
	 *
	 * @param info target dictionary builder
	 * @param key metadata key
	 * @param value metadata value
	 */
	private void appendMeta(StringBuilder info, String key, String value) {
		if (value == null || value.isBlank()) {
			return;
		}
		info.append(" /").append(key).append(" (").append(escape(value)).append(")");
	}

	/**
	 * Serializes all PDF objects, the cross-reference table, and the trailer.
	 *
	 * @param objects complete object byte payloads
	 * @param catalogId catalog object id
	 * @param infoId information dictionary object id
	 * @return encoded PDF bytes
	 */
	private byte[] serialize(List<byte[]> objects, int catalogId, int infoId) {
		try (ByteArrayOutputStream out = new ByteArrayOutputStream(64_000)) {
			out.write("%PDF-1.4\n%\u00e2\u00e3\u00cf\u00d3\n".getBytes(StandardCharsets.ISO_8859_1));
			long[] offsets = new long[objects.size() + 1];

			for (int i = 0; i < objects.size(); i++) {
				offsets[i + 1] = out.size();
				out.write((i + 1 + " 0 obj\n").getBytes(StandardCharsets.US_ASCII));
				out.write(objects.get(i));
				out.write("\nendobj\n".getBytes(StandardCharsets.US_ASCII));
			}

			long xrefOffset = out.size();
			out.write(("xref\n0 " + (objects.size() + 1) + "\n").getBytes(StandardCharsets.US_ASCII));
			out.write("0000000000 65535 f \n".getBytes(StandardCharsets.US_ASCII));
			for (int i = 1; i < offsets.length; i++) {
				String line = String.format(Locale.ROOT, "%010d 00000 n %n", offsets[i]);
				out.write(line.getBytes(StandardCharsets.US_ASCII));
			}

			String trailer = "trailer\n<< /Size " + (objects.size() + 1) + " /Root "
					+ indirectReference(catalogId) + " /Info " + indirectReference(infoId)
					+ " >>\nstartxref\n" + xrefOffset + "\n%%EOF\n";
			out.write(trailer.getBytes(StandardCharsets.US_ASCII));
			return out.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException("failed to serialize pdf", e);
		}
	}

	/**
	 * Reserves one object slot for content that is only known later.
	 *
	 * @param objects object list
	 * @return reserved object id
	 */
	private static int reserveObject(List<byte[]> objects) {
		objects.add(null);
		return objects.size();
	}

	/**
	 * Replaces a reserved object slot with ASCII string content.
	 *
	 * @param objects object list
	 * @param objectId one-based object id
	 * @param content replacement content
	 */
	private static void setObject(List<byte[]> objects, int objectId, String content) {
		setObject(objects, objectId, content.getBytes(StandardCharsets.US_ASCII));
	}

	/**
	 * Replaces a reserved object slot with raw bytes.
	 *
	 * @param objects object list
	 * @param objectId one-based object id
	 * @param content replacement content
	 */
	private static void setObject(List<byte[]> objects, int objectId, byte[] content) {
		objects.set(objectId - 1, content);
	}

	/**
	 * Appends one ASCII string object.
	 *
	 * @param objects object list
	 * @param content object content
	 * @return new object id
	 */
	private static int addObject(List<byte[]> objects, String content) {
		return addObject(objects, content.getBytes(StandardCharsets.US_ASCII));
	}

	/**
	 * Appends one raw byte object.
	 *
	 * @param objects object list
	 * @param content object content
	 * @return new object id
	 */
	private static int addObject(List<byte[]> objects, byte[] content) {
		objects.add(content);
		return objects.size();
	}

	/**
	 * Appends one stream object with optional dictionary entries.
	 *
	 * @param objects object list
	 * @param extraEntries extra stream dictionary entries
	 * @param streamBytes encoded stream bytes
	 * @return new object id
	 */
	private static int addStreamObject(List<byte[]> objects, String extraEntries, byte[] streamBytes) {
		try (ByteArrayOutputStream out = new ByteArrayOutputStream(streamBytes.length + 128)) {
			StringBuilder dictionary = new StringBuilder(128);
			dictionary.append("<< /Length ").append(streamBytes.length);
			if (extraEntries != null && !extraEntries.isBlank()) {
				dictionary.append(' ').append(extraEntries.trim());
			}
			dictionary.append(" >>\nstream\n");
			out.write(dictionary.toString().getBytes(StandardCharsets.US_ASCII));
			out.write(streamBytes);
			out.write("\nendstream".getBytes(StandardCharsets.US_ASCII));
			return addObject(objects, out.toByteArray());
		} catch (IOException e) {
			throw new IllegalStateException("failed to build stream object", e);
		}
	}

	/**
	 * Normalizes one metadata field.
	 *
	 * @param value raw metadata value
	 * @return trimmed metadata value, never null
	 */
	private String normalizeMeta(String value) {
		return value == null ? "" : value.trim();
	}
}
