package application.gui.workbench;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

import chess.images.assets.Shapes;

/**
 * Inline chess-piece artwork for algebraic notation. Renders the K/Q/R/B/N
 * piece letters of SAN text as small solid piece silhouettes — the figurine
 * notation used by generated puzzle reports and books — tinted to the
 * surrounding text colour so it stays legible on any background.
 *
 * <p>Silhouettes are derived from the shared {@link Shapes} SVG piece artwork,
 * scaled and tinted once, then cached per glyph / height / colour.</p>
 */
final class WorkbenchFigurine {

    /**
     * Cache of tinted, scaled piece silhouettes keyed by glyph, height, colour.
     */
    private static final Map<String, Image> CACHE = new HashMap<>();

    /**
     * Utility class; not instantiated.
     */
    private WorkbenchFigurine() {
    }

    /**
     * Returns whether a character is a SAN piece letter that should render as
     * an inline figurine.
     *
     * @param ch character to test
     * @return true for K, Q, R, B, or N
     */
    static boolean isPieceLetter(char ch) {
        return ch == 'K' || ch == 'Q' || ch == 'R' || ch == 'B' || ch == 'N';
    }

    /**
     * Returns a tinted piece silhouette for one SAN piece letter.
     *
     * @param pieceLetter SAN piece letter (K/Q/R/B/N)
     * @param height target icon height in pixels
     * @param color tint colour (usually the text colour)
     * @return cached silhouette image, or {@code null} when the letter is not a piece
     */
    static Image icon(char pieceLetter, int height, Color color) {
        if (!isPieceLetter(pieceLetter) || height <= 0 || color == null) {
            return null;
        }
        String key = pieceLetter + "|" + height + "|" + color.getRGB();
        Image cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        BufferedImage source = sourceArtwork(pieceLetter);
        if (source == null) {
            return null;
        }
        // Pieces sit centred in a square viewbox; keep the icon square so the
        // baseline alignment of every glyph matches.
        BufferedImage tinted = new BufferedImage(height, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = tinted.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(source, 0, 0, height, height, null);
            // SrcIn keeps only the piece silhouette and recolours it.
            g.setComposite(AlphaComposite.SrcIn);
            g.setColor(color);
            g.fillRect(0, 0, height, height);
        } finally {
            g.dispose();
        }
        CACHE.put(key, tinted);
        return tinted;
    }

    /**
     * Resolves the shared SVG artwork for one SAN piece letter.
     *
     * @param pieceLetter SAN piece letter
     * @return source artwork, or {@code null} when unknown
     */
    private static BufferedImage sourceArtwork(char pieceLetter) {
        return switch (pieceLetter) {
            case 'K' -> Shapes.WhiteKing;
            case 'Q' -> Shapes.WhiteQueen;
            case 'R' -> Shapes.WhiteRook;
            case 'B' -> Shapes.WhiteBishop;
            case 'N' -> Shapes.WhiteKnight;
            default -> null;
        };
    }
}
