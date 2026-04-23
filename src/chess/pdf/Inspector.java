package chess.pdf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads lightweight page geometry from an existing PDF file.
 *
 * <p>
 * The inspector is intentionally small and targets the plain-text object layout
 * emitted by the repository's native PDF writer. It extracts the total page
 * count from the page tree and the first page size from the first visible
 * {@code /MediaBox}.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Inspector {

	/**
	 * PDF header prefix.
	 */
	private static final String PDF_HEADER = "%PDF-";

	/**
	 * Decimal-number pattern used in PDF numeric arrays.
	 */
	private static final String NUMBER = "[-+]?(?:\\d+\\.\\d+|\\d+|\\.\\d+)";

	/**
	 * Shared separator between media-box numeric capture groups.
	 */
	private static final String BOX_SEPARATOR = ")\\s+(";

	/**
	 * Shared empty media-box marker.
	 */
	private static final double[] NO_MEDIA_BOX = new double[0];

	/**
	 * Page-tree count object pattern.
	 */
	private static final Pattern PAGE_COUNT = Pattern.compile(
			"/Type\\s*/Pages\\b.{0,4096}?/Count\\s+(\\d+)",
			Pattern.DOTALL);

	/**
	 * Page-object marker pattern used for count fallback.
	 */
	private static final Pattern PAGE_OBJECT = Pattern.compile("/Type\\s*/Page\\b");

	/**
	 * First-page media-box pattern.
	 */
	private static final Pattern PAGE_MEDIA_BOX = Pattern.compile(
			"/Type\\s*/Page\\b.{0,4096}?/MediaBox\\s*\\[\\s*("
					+ NUMBER + BOX_SEPARATOR + NUMBER + BOX_SEPARATOR + NUMBER + BOX_SEPARATOR + NUMBER
					+ ")\\s*\\]",
			Pattern.DOTALL);

	/**
	 * Fallback media-box pattern used when the page-object form is not found.
	 */
	private static final Pattern ANY_MEDIA_BOX = Pattern.compile(
			"/MediaBox\\s*\\[\\s*("
					+ NUMBER + BOX_SEPARATOR + NUMBER + BOX_SEPARATOR + NUMBER + BOX_SEPARATOR + NUMBER
					+ ")\\s*\\]");

	/**
	 * Utility class; prevent instantiation.
	 */
	private Inspector() {
		// utility
	}

	/**
	 * Reads page count and first-page size from a PDF file.
	 *
	 * @param path source PDF path
	 * @return extracted document metrics
	 * @throws IOException if the file cannot be read
	 */
	public static DocumentMetrics inspect(Path path) throws IOException {
		if (path == null) {
			throw new IllegalArgumentException("pdf path cannot be null");
		}
		byte[] bytes = Files.readAllBytes(path);
		if (bytes.length == 0) {
			throw new IllegalArgumentException("pdf is empty: " + path.toAbsolutePath());
		}
		String text = new String(bytes, StandardCharsets.ISO_8859_1);
		if (!text.startsWith(PDF_HEADER)) {
			throw new IllegalArgumentException("not a PDF file: " + path.toAbsolutePath());
		}

		int pageCount = parsePageCount(text);
		double[] mediaBox = parseMediaBox(text);
		if (pageCount <= 0) {
			throw new IllegalArgumentException("could not read PDF page count from " + path.toAbsolutePath());
		}
		if (mediaBox.length == 0) {
			throw new IllegalArgumentException("could not read PDF page size from " + path.toAbsolutePath());
		}

		double width = Math.abs(mediaBox[2] - mediaBox[0]);
		double height = Math.abs(mediaBox[3] - mediaBox[1]);
		return new DocumentMetrics(pageCount, width, height);
	}

	/**
	 * Parses the page count from the root page-tree object.
	 *
	 * @param text decoded PDF text
	 * @return positive page count, or zero when unavailable
	 */
	private static int parsePageCount(String text) {
		Matcher matcher = PAGE_COUNT.matcher(text);
		if (matcher.find()) {
			try {
				return Math.max(0, Integer.parseInt(matcher.group(1)));
			} catch (NumberFormatException ex) {
				return 0;
			}
		}

		matcher = PAGE_OBJECT.matcher(text);
		int count = 0;
		while (matcher.find()) {
			count++;
		}
		return count;
	}

	/**
	 * Parses the first visible media box from the PDF text.
	 *
	 * @param text decoded PDF text
	 * @return four media-box coordinates, or an empty array when unavailable
	 */
	private static double[] parseMediaBox(String text) {
		Matcher matcher = PAGE_MEDIA_BOX.matcher(text);
		if (matcher.find()) {
			return toMediaBox(matcher);
		}
		matcher = ANY_MEDIA_BOX.matcher(text);
		if (matcher.find()) {
			return toMediaBox(matcher);
		}
		return NO_MEDIA_BOX;
	}

	/**
	 * Converts one regex match into four media-box coordinates.
	 *
	 * @param matcher regex match with four numeric capture groups
	 * @return parsed media-box coordinates, or an empty array when invalid
	 */
	private static double[] toMediaBox(Matcher matcher) {
		try {
			return new double[] {
					Double.parseDouble(matcher.group(1)),
					Double.parseDouble(matcher.group(2)),
					Double.parseDouble(matcher.group(3)),
						Double.parseDouble(matcher.group(4))
				};
		} catch (NumberFormatException ex) {
			return NO_MEDIA_BOX;
		}
	}
}
