package application.gui.workbench.game;

import application.gui.workbench.board.*;
import application.gui.workbench.command.*;
import application.gui.workbench.dashboard.*;
import application.gui.workbench.layout.*;
import application.gui.workbench.mcts.*;
import application.gui.workbench.network.*;
import application.gui.workbench.publish.*;
import application.gui.workbench.session.*;
import application.gui.workbench.ui.*;
import application.gui.workbench.window.*;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

/**
 * Table-cell renderer that draws algebraic-notation (SAN) text with inline
 * chess-piece figurines: the K/Q/R/B/N piece letters become neutral figurine
 * notation glyphs, matching the notation used in generated reports.
 *
 * <p>Pawn moves, castling, files, ranks, and annotations stay as text; only the
 * leading piece letter of each move token and the piece after a {@code =}
 * promotion marker are replaced.</p>
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
     * Parsed segments of the current cell value.
     */
    private transient String text = "";

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
        text = figurine(value == null ? "" : value.toString());
        textColor = Theme.TEXT;
        setBackground(isSelected ? Theme.SELECTION_SOLID : table.getBackground());
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
            int baseline = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
            g.setColor(textColor);
            g.drawString(text, PAD_X, baseline);
        } finally {
            g.dispose();
        }
    }

    @Override
    public Dimension getPreferredSize() {
    return new Dimension(80, 22);
    }

    /**
     * Converts a SAN line into neutral figurine algebraic notation.
     *
     * @param line SAN text (one or more move tokens)
     * @return formatted text
     */
    public static String figurine(String line) {
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
                char piece = figurinePiece(ch);
                if (piece != 0) {
                    text.append(piece);
                    continue;
                }
                text.append(ch);
                continue;
            }
            if (ch == '=' && i + 1 < line.length()) {
                char piece = figurinePiece(line.charAt(i + 1));
                if (piece == 0) {
                    text.append(ch);
                    continue;
                }
                text.append(ch);
                text.append(piece);
                i++;
                continue;
            }
            text.append(ch);
        }
        return text.toString();
    }

    /**
     * Converts a SAN piece letter to the standard white figurine glyph.
     *
     * @param piece SAN piece letter
     * @return figurine glyph, or {@code 0} when not a piece letter
     */
    private static char figurinePiece(char piece) {
    return switch (piece) {
            case 'K' -> '\u2654';
            case 'Q' -> '\u2655';
            case 'R' -> '\u2656';
            case 'B' -> '\u2657';
            case 'N' -> '\u2658';
            default -> 0;
        };
    }
}
