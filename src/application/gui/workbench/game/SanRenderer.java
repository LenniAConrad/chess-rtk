package application.gui.workbench.game;

import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.NotationPainter;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.RenderingHints;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

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
     * Extra vertical inset reserved around piece SVGs.
     */
    private static final int PIECE_VERTICAL_INSET = 5;

    /**
     * Current cell value.
     */
    private transient String line = "";

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

    /**
     * {@inheritDoc}
     */
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int row, int column) {
        setFont(table.getFont());
        line = value == null ? "" : value.toString();
        Color foreground = isSelected ? table.getSelectionForeground() : table.getForeground();
        textColor = foreground == null ? Theme.TEXT : foreground;
        boolean hovered = Theme.isHoveredTableRow(table, row);
        setBackground(isSelected ? table.getSelectionBackground()
                : hovered ? Theme.SECONDARY_BUTTON_HOVER : table.getBackground());
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
            NotationPainter.draw(g, line, PAD_X, baseline,
                    Math.max(1, getWidth() - PAD_X * 2), textColor, iconSize);
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
        return NotationPainter.pieceSvgCount(line);
    }

    /**
     * Returns a cell-appropriate inline piece size.
     *
     * @param metrics active font metrics
     * @return icon size in pixels
     */
    private int iconSize(FontMetrics metrics) {
        int byRowHeight = Math.max(10, getHeight() - PIECE_VERTICAL_INSET);
        int byFont = NotationPainter.iconSize(metrics);
        return Math.min(byRowHeight, byFont);
    }
}
