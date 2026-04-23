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
public final class PdfDocumentRegressionTest {

	/**
	 * Utility class; prevent instantiation.
	 */
	private PdfDocumentRegressionTest() {
		// utility
	}

	/**
	 * Runs the regression checks.
	 *
	 * @param args unused command-line arguments
	 */
	public static void main(String[] args) {
		testSvgInlineStyleOverridesPresentationAttributes();
		System.out.println("PdfDocumentRegressionTest: all checks passed");
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
}
