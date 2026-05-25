package testing;

import static testing.TestSupport.assertTrue;

import chess.pdf.document.Document;
import chess.pdf.document.PageSize;
import utility.Svg;

/**
 * Regression checks for XML parser hardening.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */

public final class XmlSecurityRegressionTest {

    /**
     * SVG payload that would be unsafe if external entities were expanded.
     */
    private static final String SVG_WITH_DOCTYPE = """
            <!DOCTYPE svg [
              <!ENTITY xxe SYSTEM "file:///etc/passwd">
            ]>
            <svg viewBox="0 0 10 10" xmlns="http://www.w3.org/2000/svg">
              <text x="1" y="5">&xxe;</text>
            </svg>
            """;

    /**
     * Prevents instantiation.
     */
    private XmlSecurityRegressionTest() {
        // utility
    }

    /**
     * Runs XML security regression checks.
     *
     * @param args unused command-line arguments
     */
    public static void main(String[] args) {
        testImageSvgParserRejectsDoctype();
        testPdfSvgParserRejectsDoctype();
        System.out.println("XmlSecurityRegressionTest: all checks passed");
    }

    /**
     * Verifies the image SVG parser rejects DTD-bearing documents.
     */
    private static void testImageSvgParserRejectsDoctype() {
        boolean rejected = false;
        try {
            Svg.parse(SVG_WITH_DOCTYPE);
        } catch (IllegalArgumentException ex) {
            rejected = true;
        }
        assertTrue(rejected, "image SVG parser rejects DOCTYPE");
    }

    /**
     * Verifies the PDF SVG parser rejects DTD-bearing documents.
     */
    private static void testPdfSvgParserRejectsDoctype() {
        boolean rejected = false;
        try {
            new Document().addPage(PageSize.A4).canvas()
                    .drawSvg(SVG_WITH_DOCTYPE, 24.0, 24.0, 72.0, 72.0);
        } catch (IllegalArgumentException ex) {
            rejected = true;
        }
        assertTrue(rejected, "PDF SVG parser rejects DOCTYPE");
    }
}
