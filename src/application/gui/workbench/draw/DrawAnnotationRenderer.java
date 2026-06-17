package application.gui.workbench.draw;

import application.gui.workbench.board.MarkupBrush;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.ListCellRenderer;

/**
 * Themed renderer for the Draw rail annotation history list.
 */
final class DrawAnnotationRenderer extends JComponent implements ListCellRenderer<DrawAnnotationRow> {
    /**
     * Serialization identifier for Swing renderer compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Fixed row height.
     */
    private final int rowHeight;

    /**
     * Size of the row color sample.
     */
    private final int swatchSize;

    /**
     * Row currently being painted.
     */
    private DrawAnnotationRow row;

    /**
     * True when the row is selected.
     */
    private boolean selected;

    /**
     * True when the row has keyboard focus.
     */
    private boolean focused;

    DrawAnnotationRenderer(int rowHeight, int swatchSize) {
        this.rowHeight = rowHeight;
        this.swatchSize = swatchSize;
    }

    /**
     * Returns the renderer component for one annotation row.
     *
     * @param list owning list
     * @param value row value
     * @param index row index
     * @param selected true when selected
     * @param focused true when focused
     * @return renderer component
     */
    @Override
    public Component getListCellRendererComponent(JList<? extends DrawAnnotationRow> list, DrawAnnotationRow value,
            int index, boolean selected, boolean focused) {
        row = value;
        this.selected = selected;
        this.focused = focused;
        setEnabled(list.isEnabled());
        setFont(list.getFont());
        setForeground(selected ? list.getSelectionForeground() : list.getForeground());
        setToolTipText(value == null ? null : value.toString());
        return this;
    }

    /**
     * Returns a stable row size.
     *
     * @return preferred size
     */
    @Override
    public Dimension getPreferredSize() {
        return new Dimension(120, rowHeight);
    }

    /**
     * Paints the annotation row.
     *
     * @param graphics graphics context
     */
    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            paintBackground(g);
            if (row != null) {
                paintSwatch(g);
                paintText(g);
            }
        } finally {
            g.dispose();
        }
    }

    private void paintBackground(Graphics2D g) {
        int width = Math.max(0, getWidth() - Theme.SPACE_XS);
        int height = Math.max(0, getHeight() - Theme.SPACE_XS);
        int y = Theme.SPACE_XS / 2;
        if (selected) {
            g.setColor(Theme.SELECTION_SOLID);
            g.fillRoundRect(0, y, width, height, Theme.RADIUS, Theme.RADIUS);
        } else {
            g.setColor(Theme.withAlpha(Theme.LINE, Theme.isDark() ? 22 : 16));
            g.fillRoundRect(0, y, width, height, Theme.RADIUS, Theme.RADIUS);
        }
        if (focused) {
            g.setColor(Theme.withAlpha(Theme.ACCENT, Theme.isDark() ? 190 : 170));
            g.drawRoundRect(0, y, width, height, Theme.RADIUS, Theme.RADIUS);
        }
    }

    private void paintSwatch(Graphics2D g) {
        int x = Theme.SPACE_SM;
        int y = Math.max(0, (getHeight() - swatchSize) / 2);
        MarkupBrush brush = row.markup().brush();
        g.setColor(brush.displayColor());
        g.fillRoundRect(x, y, swatchSize, swatchSize, Theme.RADIUS, Theme.RADIUS);
        Color outline = brush.displayBorderColor().getAlpha() > 0
                ? brush.displayBorderColor()
                : Theme.LINE;
        g.setColor(Theme.withAlpha(outline, selected ? 220 : 150));
        g.drawRoundRect(x, y, swatchSize, swatchSize, Theme.RADIUS, Theme.RADIUS);
    }

    private void paintText(Graphics2D g) {
        int textX = Theme.SPACE_SM + swatchSize + Theme.SPACE_SM;
        int textWidth = Math.max(0, getWidth() - textX - Theme.SPACE_SM);
        g.setFont(Theme.font(Theme.FONT_BODY, Font.BOLD));
        FontMetrics titleMetrics = g.getFontMetrics();
        if (row.details()) {
            g.setColor(Theme.TEXT);
            g.drawString(Ui.elide(row.title(), titleMetrics, textWidth), textX, 15);
            g.setFont(Theme.font(Theme.FONT_METADATA, Font.PLAIN));
            FontMetrics detailMetrics = g.getFontMetrics();
            g.setColor(selected ? Theme.TEXT : Theme.MUTED);
            g.drawString(Ui.elide(row.detailWithColor(), detailMetrics, textWidth), textX, 30);
        } else {
            int baseline = (getHeight() - titleMetrics.getHeight()) / 2 + titleMetrics.getAscent();
            g.setColor(Theme.TEXT);
            g.drawString(Ui.elide(row.title(), titleMetrics, textWidth), textX, baseline);
        }
    }
}
