/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package application.gui.workbench.game;

import application.gui.workbench.ui.Theme;
import chess.core.Piece;
import chess.images.assets.Shapes;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

/**
 * Table-cell renderer that draws algebraic-notation (SAN) text with inline SVG
 * chess pieces.
 *
 * <p>Pawn moves, castling, files, ranks, and annotations stay as text; only the
 * leading piece letter of each move token and the promotion piece after a
 * {@code =} marker are replaced by the neutral white-piece artwork.</p>
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
        textColor = Theme.TEXT;
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
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setFont(getFont());
            FontMetrics fm = g.getFontMetrics();
            int baseline = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
            int iconSize = iconSize(fm);
            int iconY = (getHeight() - iconSize) / 2;
            int x = PAD_X;
            for (SanSegment segment : segments) {
                if (segment.isPiece()) {
                    Shapes.drawPiece(segment.piece(), g, x, iconY, iconSize, iconSize);
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
                byte piece = pieceForSan(ch);
                if (piece != Piece.EMPTY) {
                    flushText(parsed, text);
                    parsed.add(SanSegment.piece(piece));
                    continue;
                }
                text.append(ch);
                continue;
            }
            if (ch == '=' && i + 1 < line.length()) {
                byte piece = pieceForSan(line.charAt(i + 1));
                if (piece == Piece.EMPTY) {
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
     * Converts a SAN piece letter to neutral white-piece SVG artwork.
     *
     * @param piece SAN piece letter
     * @return piece code, or {@link Piece#EMPTY} when not a piece letter
     */
    private static byte pieceForSan(char piece) {
        return switch (piece) {
            case 'K' -> Piece.WHITE_KING;
            case 'Q' -> Piece.WHITE_QUEEN;
            case 'R' -> Piece.WHITE_ROOK;
            case 'B' -> Piece.WHITE_BISHOP;
            case 'N' -> Piece.WHITE_KNIGHT;
            default -> Piece.EMPTY;
        };
    }

    /**
     * Parsed SAN rendering segment.
     *
     * @param text text content for text segments
     * @param piece neutral white-piece code for SVG segments
     */
    private record SanSegment(String text, byte piece) {

        /**
         * Creates a text segment.
         *
         * @param value segment text
         * @return text segment
         */
        static SanSegment text(String value) {
            return new SanSegment(value, Piece.EMPTY);
        }

        /**
         * Creates a piece segment.
         *
         * @param value piece code
         * @return piece segment
         */
        static SanSegment piece(byte value) {
            return new SanSegment("", value);
        }

        /**
         * Returns whether this segment paints a piece SVG.
         *
         * @return true for piece segments
         */
        boolean isPiece() {
            return piece != Piece.EMPTY;
        }
    }
}
