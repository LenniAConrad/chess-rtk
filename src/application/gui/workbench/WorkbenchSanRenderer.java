package application.gui.workbench;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

/**
 * Table-cell renderer that draws algebraic-notation (SAN) text with inline
 * chess-piece figurines: the K/Q/R/B/N piece letters become small piece
 * silhouettes, matching the figurine notation used in generated reports.
 *
 * <p>Pawn moves, castling, files, ranks, and annotations stay as text; only the
 * leading piece letter of each move token and the piece after a {@code =}
 * promotion marker are replaced.</p>
 */
final class WorkbenchSanRenderer extends JComponent implements TableCellRenderer {

    /**
     * Serialization identifier for Swing component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Horizontal padding inside the cell.
     */
    private static final int PAD_X = 6;

    /**
     * One renderable run: either a text fragment or a piece figurine.
     */
    private record Segment(String text, char piece) {
        boolean isIcon() {
            return piece != 0;
        }
    }

    /**
     * Parsed segments of the current cell value.
     */
    private transient List<Segment> segments = List.of();

    /**
     * Text colour for the current cell.
     */
    private transient Color textColor = WorkbenchTheme.TEXT;

    /**
     * Creates the renderer.
     */
    WorkbenchSanRenderer() {
        setOpaque(true);
        setFont(WorkbenchTheme.mono(12));
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int row, int column) {
        setFont(table.getFont());
        segments = parse(value == null ? "" : value.toString());
        textColor = WorkbenchTheme.TEXT;
        setBackground(isSelected ? WorkbenchTheme.SELECTION_SOLID : table.getBackground());
        return this;
    }

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
            int iconSize = Math.min(getHeight() - 2, fm.getAscent() + fm.getDescent() + 5);
            int baseline = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
            int x = PAD_X;
            for (Segment segment : segments) {
                if (segment.isIcon()) {
                    Image icon = WorkbenchFigurine.icon(segment.piece(), iconSize, textColor);
                    if (icon != null) {
                        int iconY = (getHeight() - iconSize) / 2;
                        g.drawImage(icon, x, iconY, null);
                        x += iconSize;
                        continue;
                    }
                    // Fall back to the letter if artwork is unavailable.
                    g.setColor(textColor);
                    g.drawString(String.valueOf(segment.piece()), x, baseline);
                    x += fm.charWidth(segment.piece());
                } else {
                    g.setColor(textColor);
                    g.drawString(segment.text(), x, baseline);
                    x += fm.stringWidth(segment.text());
                }
            }
        } finally {
            g.dispose();
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(80, 22);
    }

    /**
     * Splits a SAN line into text and figurine segments.
     *
     * @param line SAN text (one or more move tokens)
     * @return ordered renderable segments
     */
    private static List<Segment> parse(String line) {
        List<Segment> out = new ArrayList<>();
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
                // Move numbers and bracketing punctuation precede the piece.
                if (Character.isDigit(ch) || ch == '.' || ch == '(' || ch == '[' || ch == '{') {
                    text.append(ch);
                    continue;
                }
                tokenStart = false;
                if (WorkbenchFigurine.isPieceLetter(ch)) {
                    flush(out, text);
                    out.add(new Segment(null, ch));
                    continue;
                }
                text.append(ch);
                continue;
            }
            if (ch == '=' && i + 1 < line.length()
                    && WorkbenchFigurine.isPieceLetter(line.charAt(i + 1))) {
                text.append(ch);
                flush(out, text);
                out.add(new Segment(null, line.charAt(i + 1)));
                i++;
                continue;
            }
            text.append(ch);
        }
        flush(out, text);
        return out;
    }

    /**
     * Appends any pending text as a segment and clears the buffer.
     *
     * @param out segment list
     * @param text pending text buffer
     */
    private static void flush(List<Segment> out, StringBuilder text) {
        if (text.length() > 0) {
            out.add(new Segment(text.toString(), (char) 0));
            text.setLength(0);
        }
    }
}
