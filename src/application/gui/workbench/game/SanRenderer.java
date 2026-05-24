/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package application.gui.workbench.game;

import application.gui.workbench.ui.Theme;
import chess.book.render.NotationPieceSvg;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import utility.Svg;
import utility.Svg.DocumentModel;

/**
 * Table-cell renderer that draws algebraic-notation (SAN) text with inline SVG
 * chess pieces.
 *
 * <p>Pawn moves, castling, files, ranks, and annotations stay as text; only the
 * leading piece letter of each move token and the promotion piece after a
 * {@code =} marker are replaced by the same neutral cutout artwork used by the
 * puzzle PDF report.</p>
 */
public final class SanRenderer extends JComponent implements TableCellRenderer {

    /**
     * Serialization identifier for Swing component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Horizontal padding inside the cell.
     */
    private static final int PAD_X = 6;

    /**
     * Gap between an inline piece SVG and adjacent text.
     */
    private static final int PIECE_GAP = 2;

    /**
     * Extra vertical inset reserved around piece SVGs.
     */
    private static final int PIECE_VERTICAL_INSET = 5;

    /**
     * Marker for text-only segments.
     */
    private static final char NO_PIECE = '\0';

    /**
     * White king notation placeholder.
     */
    private static final char KING_PLACEHOLDER = '\u2654';

    /**
     * White queen notation placeholder.
     */
    private static final char QUEEN_PLACEHOLDER = '\u2655';

    /**
     * White rook notation placeholder.
     */
    private static final char ROOK_PLACEHOLDER = '\u2656';

    /**
     * White bishop notation placeholder.
     */
    private static final char BISHOP_PLACEHOLDER = '\u2657';

    /**
     * White knight notation placeholder.
     */
    private static final char KNIGHT_PLACEHOLDER = '\u2658';

    /**
     * Parsed cutout king SVG.
     */
    private static final DocumentModel KING_DOCUMENT = notationDocument(KING_PLACEHOLDER);

    /**
     * Parsed cutout queen SVG.
     */
    private static final DocumentModel QUEEN_DOCUMENT = notationDocument(QUEEN_PLACEHOLDER);

    /**
     * Parsed cutout rook SVG.
     */
    private static final DocumentModel ROOK_DOCUMENT = notationDocument(ROOK_PLACEHOLDER);

    /**
     * Parsed cutout bishop SVG.
     */
    private static final DocumentModel BISHOP_DOCUMENT = notationDocument(BISHOP_PLACEHOLDER);

    /**
     * Parsed cutout knight SVG.
     */
    private static final DocumentModel KNIGHT_DOCUMENT = notationDocument(KNIGHT_PLACEHOLDER);

    /**
     * Parsed segments of the current cell value.
     */
    private transient List<SanSegment> segments = List.of();

    /**
     * Text colour for the current cell.
     */
    private transient Color textColor = Theme.TEXT;

    /**
     * Creates the renderer.
     */
    public SanRenderer() {
        setOpaque(true);
        setFont(Theme.mono(12));
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int row, int column) {
        setFont(table.getFont());
        segments = parseSegments(value == null ? "" : value.toString());
        Color foreground = isSelected ? table.getSelectionForeground() : table.getForeground();
        textColor = foreground == null ? Theme.TEXT : foreground;
        setBackground(isSelected ? Theme.SELECTION_SOLID : table.getBackground());
        return this;
    }

    /**
     * Paints the SAN text and inline piece SVG segments.
     *
     * @param graphics graphics context
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                    RenderingHints.VALUE_STROKE_PURE);
            g.setFont(getFont());
            FontMetrics fm = g.getFontMetrics();
            int baseline = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
            int iconSize = iconSize(fm);
            int iconY = (getHeight() - iconSize) / 2;
            int x = PAD_X;
            for (SanSegment segment : segments) {
                if (segment.isPiece()) {
                    Svg.draw(documentFor(segment.piece()), g, x, iconY, iconSize, iconSize, textColor);
                    x += iconSize + PIECE_GAP;
                } else if (!segment.text().isEmpty()) {
                    g.setColor(textColor);
                    g.drawString(segment.text(), x, baseline);
                    x += fm.stringWidth(segment.text());
                }
            }
        } finally {
            g.dispose();
        }
    }

    /**
     * Returns the preferred SAN cell size.
     *
     * @return preferred size
     */
    @Override
    public Dimension getPreferredSize() {
        return new Dimension(96, 22);
    }

    /**
     * Counts inline SVG piece segments that would be painted for a SAN line.
     *
     * @param line SAN text (one or more move tokens)
     * @return piece segment count
     */
    static int pieceSvgCount(String line) {
        int count = 0;
        for (SanSegment segment : parseSegments(line)) {
            if (segment.isPiece()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Parses SAN into text and piece segments.
     *
     * @param line SAN text (one or more move tokens)
     * @return parsed SAN segments
     */
    private static List<SanSegment> parseSegments(String line) {
        List<SanSegment> parsed = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        boolean tokenStart = true;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (Character.isWhitespace(ch)) {
                text.append(ch);
                tokenStart = true;
                continue;
            }
            if (tokenStart) {
                if (Character.isDigit(ch) || ch == '.' || ch == '(' || ch == '[' || ch == '{') {
                    text.append(ch);
                    continue;
                }
                tokenStart = false;
                char piece = pieceForSan(ch);
                if (piece != NO_PIECE) {
                    flushText(parsed, text);
                    parsed.add(SanSegment.piece(piece));
                    continue;
                }
                text.append(ch);
                continue;
            }
            if (ch == '=' && i + 1 < line.length()) {
                char piece = pieceForSan(line.charAt(i + 1));
                if (piece == NO_PIECE) {
                    text.append(ch);
                    continue;
                }
                text.append(ch);
                flushText(parsed, text);
                parsed.add(SanSegment.piece(piece));
                i++;
                continue;
            }
            text.append(ch);
        }
        flushText(parsed, text);
        return parsed;
    }

    /**
     * Appends pending text to the parsed segment list.
     *
     * @param segments parsed segments
     * @param text pending text buffer
     */
    private static void flushText(List<SanSegment> segments, StringBuilder text) {
        if (!text.isEmpty()) {
            segments.add(SanSegment.text(text.toString()));
            text.setLength(0);
        }
    }

    /**
     * Returns a cell-appropriate inline piece size.
     *
     * @param metrics active font metrics
     * @return icon size in pixels
     */
    private int iconSize(FontMetrics metrics) {
        int byRowHeight = Math.max(10, getHeight() - PIECE_VERTICAL_INSET);
        int byFont = metrics.getHeight() + 3;
        return Math.min(byRowHeight, byFont);
    }

    /**
     * Converts a SAN piece letter to neutral cutout SVG artwork.
     *
     * @param piece SAN piece letter
     * @return notation placeholder, or {@link #NO_PIECE} when not a piece letter
     */
    private static char pieceForSan(char piece) {
        return switch (piece) {
            case 'K' -> KING_PLACEHOLDER;
            case 'Q' -> QUEEN_PLACEHOLDER;
            case 'R' -> ROOK_PLACEHOLDER;
            case 'B' -> BISHOP_PLACEHOLDER;
            case 'N' -> KNIGHT_PLACEHOLDER;
            default -> NO_PIECE;
        };
    }

    /**
     * Parses the report-style cutout SVG for one placeholder.
     *
     * @param placeholder notation placeholder
     * @return parsed SVG document
     */
    private static DocumentModel notationDocument(char placeholder) {
        return Svg.parse(NotationPieceSvg.svg(placeholder));
    }

    /**
     * Returns the parsed cutout SVG for one placeholder.
     *
     * @param piece notation placeholder
     * @return parsed SVG document
     */
    private static DocumentModel documentFor(char piece) {
        return switch (piece) {
            case KING_PLACEHOLDER -> KING_DOCUMENT;
            case QUEEN_PLACEHOLDER -> QUEEN_DOCUMENT;
            case ROOK_PLACEHOLDER -> ROOK_DOCUMENT;
            case BISHOP_PLACEHOLDER -> BISHOP_DOCUMENT;
            case KNIGHT_PLACEHOLDER -> KNIGHT_DOCUMENT;
            default -> throw new IllegalArgumentException("Unknown SAN piece placeholder: " + piece);
        };
    }

    /**
     * Parsed SAN rendering segment.
     *
     * @param text text content for text segments
     * @param piece neutral cutout-piece placeholder for SVG segments
     */
    private record SanSegment(String text, char piece) {

        /**
         * Creates a text segment.
         *
         * @param value segment text
         * @return text segment
         */
        static SanSegment text(String value) {
            return new SanSegment(value, NO_PIECE);
        }

        /**
         * Creates a piece segment.
         *
         * @param value notation placeholder
         * @return piece segment
         */
        static SanSegment piece(char value) {
            return new SanSegment("", value);
        }

        /**
         * Returns whether this segment paints a piece SVG.
         *
         * @return true for piece segments
         */
        boolean isPiece() {
            return piece != NO_PIECE;
        }
    }
}
