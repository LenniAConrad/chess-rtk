package testing;

import static testing.TestSupport.*;

import java.nio.charset.StandardCharsets;

import chess.pdf.document.Document;
import chess.pdf.document.PageSize;

/**
 * Zero-dependency regression checks for the low-level PDF package.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
@SuppressWarnings("java:S2187")
public final class PDFDocumentRegressionTest {

	/**
	 * Utility class; prevent instantiation.
	 */
	private PDFDocumentRegressionTest() {
		// utility
	}

	/**
	 * Runs the regression checks.
	 *
	 * @param args unused command-line arguments
	 */
	public static void main(String[] args) {
		testSvgInlineStyleOverridesPresentationAttributes();
		testSvgSansSerifFontResolvesBeforeSerif();
		System.out.println("PDFDocumentRegressionTest: all checks passed");
	}

	/**
	 * Verifies that SVG inline style properties take precedence over presentation
	 * attributes.
	 */
	private static void testSvgInlineStyleOverridesPresentationAttributes() {
		Document document = new Document();
		document.addPage(PageSize.A4).canvas().drawSvg("""
				<svg viewBox="0 0 10 10" xmlns="http://www.w3.org/2000/svg">
				  <rect x="0" y="0" width="10" height="10"
				      fill="#ff0000" stroke="#000000"
				      style="fill:#0000ff;stroke:#00ff00"/>
				</svg>
				""", 24.0, 24.0, 72.0, 72.0);

		String text = new String(document.toByteArray(), StandardCharsets.ISO_8859_1);
		assertTrue(text.contains("0 0 1 rg"), "style fill override");
		assertTrue(text.contains("0 1 0 RG"), "style stroke override");
		assertFalse(text.contains("1 0 0 rg"), "presentation fill overridden");
	}

	/**
	 * Verifies that {@code sans-serif} font lists do not accidentally match the
	 * generic {@code serif} suffix.
	 */
	private static void testSvgSansSerifFontResolvesBeforeSerif() {
		Document document = new Document();
		document.addPage(PageSize.A4).canvas().drawSvg("""
				<svg viewBox="0 0 120 24" xmlns="http://www.w3.org/2000/svg">
				  <text x="4" y="12" font-family="Arial,sans-serif" font-size="12">sample</text>
				</svg>
				""", 24.0, 24.0, 120.0, 24.0);

		String text = new String(document.toByteArray(), StandardCharsets.ISO_8859_1);
		assertTrue(text.contains("/F1 12 Tf"), "sans-serif maps to Helvetica");
		assertFalse(text.contains("/F3 12 Tf"), "sans-serif must not map to Times");
	}
}
