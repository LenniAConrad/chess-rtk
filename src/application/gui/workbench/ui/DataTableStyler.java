package application.gui.workbench.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.Icon;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;

/**
 * Shared styling for compact workbench data tables.
 */
final class DataTableStyler {

    /**
     * Header height in pixels.
     */
    private static final int HEADER_HEIGHT = 23;

    /**
     * Checkbox glyph size in pixels.
     */
    private static final int BOX_SIZE = 15;

    /**
     * Client-property key storing the currently hovered view row.
     */
    private static final String HOVER_ROW_PROPERTY =
            DataTableStyler.class.getName() + ".hoverRow";

    /**
     * Client-property key marking tables with hover tracking installed.
     */
    private static final String HOVER_LISTENER_PROPERTY =
            DataTableStyler.class.getName() + ".hoverListener";

    /**
     * Prevents instantiation.
     */
    private DataTableStyler() {
        // utility
    }

    /**
     * Styles a table as a flat VS Code-like data surface.
     *
     * @param table table
     * @param rowHeight row height
     */
    static void style(JTable table, int rowHeight) {
        table.setFillsViewportHeight(true);
        table.setRowHeight(rowHeight);
        table.setRowMargin(0);
        table.setOpaque(true);
        table.setBackground(Theme.ELEVATED_SOLID);
        table.setForeground(Theme.TEXT);
        table.setGridColor(Theme.LINE);
        table.setSelectionBackground(Theme.SELECTION_SOLID);
        table.setSelectionForeground(Theme.TEXT);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setBorder(Theme.pad(0, 0, 0, 0));
        table.setFont(Theme.font(12, java.awt.Font.PLAIN));
        table.setRowSelectionAllowed(true);
        table.setColumnSelectionAllowed(false);
        installHoverTracking(table);
        styleHeader(table);
        TableCellRenderer textRenderer = new TextCellRenderer();
        table.setDefaultRenderer(Object.class, textRenderer);
        table.setDefaultRenderer(String.class, textRenderer);
        table.setDefaultRenderer(Number.class, textRenderer);
        table.setDefaultRenderer(Boolean.class, new BooleanCellRenderer());
        table.setDefaultEditor(Boolean.class, new DefaultCellEditor(booleanEditor()));
    }

    /**
     * Installs row-hover tracking for modern table feedback.
     *
     * @param table table
     */
    private static void installHoverTracking(JTable table) {
        if (Boolean.TRUE.equals(table.getClientProperty(HOVER_LISTENER_PROPERTY))) {
            return;
        }
        table.putClientProperty(HOVER_LISTENER_PROPERTY, Boolean.TRUE);
        table.putClientProperty(HOVER_ROW_PROPERTY, Integer.valueOf(-1));
        MouseAdapter adapter = new MouseAdapter() {
            /**
             * Updates hovered row while the pointer moves.
             *
             * @param event mouse event
             */
            @Override
            public void mouseMoved(MouseEvent event) {
                updateHoverRow(table, table.rowAtPoint(event.getPoint()));
            }

            /**
             * Clears hover state when the pointer leaves.
             *
             * @param event mouse event
             */
            @Override
            public void mouseExited(MouseEvent event) {
                updateHoverRow(table, -1);
            }
        };
        table.addMouseMotionListener(adapter);
        table.addMouseListener(adapter);
    }

    /**
     * Updates the hovered row client property and repaints changed rows.
     *
     * @param table table
     * @param nextRow next hovered view row
     */
    private static void updateHoverRow(JTable table, int nextRow) {
        int previous = hoverRow(table);
        if (previous == nextRow) {
            return;
        }
        table.putClientProperty(HOVER_ROW_PROPERTY, Integer.valueOf(nextRow));
        repaintRow(table, previous);
        repaintRow(table, nextRow);
    }

    /**
     * Repaints one table row if it is visible.
     *
     * @param table table
     * @param row view row
     */
    private static void repaintRow(JTable table, int row) {
        if (row < 0 || row >= table.getRowCount()) {
            return;
        }
        Rectangle bounds = table.getCellRect(row, 0, true);
        bounds.x = 0;
        bounds.width = table.getWidth();
        table.repaint(bounds);
    }

    /**
     * Returns the hovered row stored on the table.
     *
     * @param table table
     * @return hovered view row or -1
     */
    static int hoverRow(JTable table) {
        Object value = table.getClientProperty(HOVER_ROW_PROPERTY);
        return value instanceof Integer row ? row.intValue() : -1;
    }

    /**
     * Styles the table header and installs its flat renderer.
     *
     * @param table table
     */
    private static void styleHeader(JTable table) {
        JTableHeader header = table.getTableHeader();
        header.setOpaque(true);
        header.setBackground(Theme.PANEL_SOLID);
        header.setForeground(Theme.MUTED);
        header.setFont(Theme.font(11, java.awt.Font.BOLD));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE));
        header.setDefaultRenderer(new HeaderCellRenderer());
        header.setPreferredSize(new Dimension(1, HEADER_HEIGHT));
        header.setReorderingAllowed(false);
    }

    /**
     * Creates the editor used by boolean table cells.
     *
     * @return styled checkbox editor
     */
    private static JCheckBox booleanEditor() {
        JCheckBox editor = new JCheckBox();
        Ui.styleCheckBox(editor);
        editor.setHorizontalAlignment(SwingConstants.CENTER);
        editor.setOpaque(true);
        editor.setBackground(Theme.ELEVATED_SOLID);
        editor.setForeground(Theme.TEXT);
        editor.setFocusPainted(false);
        editor.setBorder(Theme.pad(0, 0, 0, 0));
        return editor;
    }

    /**
     * Returns the active sort order for a view column.
     *
     * @param table source table
     * @param column view column
     * @return active sort order, or {@link SortOrder#UNSORTED}
     */
    private static SortOrder sortOrder(JTable table, int column) {
        if (table == null || table.getRowSorter() == null || column < 0) {
            return SortOrder.UNSORTED;
        }
        int modelColumn = table.convertColumnIndexToModel(column);
        List<? extends RowSorter.SortKey> keys = table.getRowSorter().getSortKeys();
        if (keys.isEmpty() || keys.get(0).getColumn() != modelColumn) {
            return SortOrder.UNSORTED;
        }
        return keys.get(0).getSortOrder();
    }

    /**
     * Header renderer with a flat background and themed sort glyph.
     */
    private static final class HeaderCellRenderer extends DefaultTableCellRenderer {

        /**
         * Serialization identifier for Swing renderer compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Returns a styled header cell.
         *
         * @param table source table
         * @param value cell value
         * @param selected whether selected
         * @param focused whether focused
         * @param row row index
         * @param column column index
         * @return renderer component
         */
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean selected, boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            setOpaque(true);
            setText(value == null ? "" : value.toString());
            setFont(Theme.font(11, java.awt.Font.BOLD));
            setForeground(Theme.MUTED);
            setBackground(Theme.PANEL_SOLID);
            setHorizontalAlignment(SwingConstants.LEFT);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE),
                    Theme.pad(0, 8, 0, 8)));
            SortOrder order = sortOrder(table, column);
            setIcon(order == SortOrder.UNSORTED ? null : new SortIcon(order));
            setHorizontalTextPosition(SwingConstants.LEFT);
            setIconTextGap(4);
            return this;
        }
    }

    /**
     * Compact text table renderer that avoids default gray and blue cells.
     */
    private static final class TextCellRenderer extends DefaultTableCellRenderer {

        /**
         * Serialization identifier for Swing renderer compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Returns a styled table cell component.
         *
         * @param table source table
         * @param value cell value
         * @param selected whether the row is selected
         * @param focused whether the cell has focus
         * @param row row index
         * @param column column index
         * @return renderer component
         */
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean selected, boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            setOpaque(true);
            setFont(table.getFont());
            setForeground(table.isEnabled() ? Theme.TEXT : Theme.BUTTON_DISABLED_TEXT);
            boolean hovered = row == hoverRow(table);
            setBackground(selected ? table.getSelectionBackground()
                    : hovered ? Theme.SECONDARY_BUTTON_HOVER : table.getBackground());
            setHorizontalAlignment(value instanceof Number ? SwingConstants.RIGHT : SwingConstants.LEFT);
            setBorder(Theme.pad(0, 8, 0, 8));
            return this;
        }
    }

    /**
     * Custom boolean table renderer matching the workbench palette.
     */
    private static final class BooleanCellRenderer extends JComponent implements TableCellRenderer {

        /**
         * Serialization identifier for Swing renderer compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Current checked state.
         */
        private boolean checked;

        /**
         * Current selected-row state.
         */
        private boolean rowSelected;

        /**
         * Current table enabled state.
         */
        private boolean tableEnabled;

        /**
         * Returns a styled boolean cell component.
         *
         * @param table source table
         * @param value cell value
         * @param selected whether the row is selected
         * @param focused whether the cell has focus
         * @param row row index
         * @param column column index
         * @return renderer component
         */
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean selected, boolean focused, int row, int column) {
            checked = Boolean.TRUE.equals(value);
            rowSelected = selected;
            tableEnabled = table.isEnabled();
            setOpaque(true);
            boolean hovered = row == hoverRow(table);
            setBackground(selected ? table.getSelectionBackground()
                    : hovered ? Theme.SECONDARY_BUTTON_HOVER : table.getBackground());
            setToolTipText(checked ? "enabled" : "disabled");
            return this;
        }

        /**
         * Paints the boolean chip.
         *
         * @param graphics graphics context
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(getBackground());
                g.fillRect(0, 0, getWidth(), getHeight());
                int size = Math.min(BOX_SIZE,
                        Math.max(8, Math.min(getWidth() - 6, getHeight() - 6)));
                int x = (getWidth() - size) / 2;
                int y = (getHeight() - size) / 2;
                Color fill = checked ? Theme.ACCENT
                        : rowSelected ? Theme.ELEVATED_SOLID : Theme.INPUT_DISABLED;
                Color border = checked ? Theme.ACCENT_PRESSED : Theme.INPUT_BORDER;
                if (!tableEnabled) {
                    fill = Theme.BUTTON_DISABLED_BG;
                    border = Theme.BUTTON_DISABLED_BORDER;
                }
                g.setColor(fill);
                g.fillRoundRect(x, y, size, size, 5, 5);
                g.setColor(border);
                g.drawRoundRect(x, y, size, size, 5, 5);
                if (checked) {
                    g.setColor(Theme.PRIMARY_BUTTON_TEXT);
                    g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND,
                            BasicStroke.JOIN_ROUND));
                    int left = x + Math.max(4, size / 4);
                    int midX = x + size / 2 - 1;
                    int right = x + size - Math.max(4, size / 4);
                    int midY = y + size - Math.max(4, size / 4);
                    int topY = y + Math.max(4, size / 4);
                    g.drawLine(left, midY - 1, midX, y + size - 4);
                    g.drawLine(midX, y + size - 4, right, topY);
                }
            } finally {
                g.dispose();
            }
        }
    }

    /**
     * Small themed sort chevron.
     */
    private static final class SortIcon implements Icon {

        /**
         * Sort order represented by the icon.
         */
        private final SortOrder order;

        /**
         * Creates a sort icon.
         *
         * @param order sort order
         */
        SortIcon(SortOrder order) {
            this.order = order;
        }

        /**
         * Returns icon width.
         *
         * @return width
         */
        @Override
        public int getIconWidth() {
            return 8;
        }

        /**
         * Returns icon height.
         *
         * @return height
         */
        @Override
        public int getIconHeight() {
            return 8;
        }

        /**
         * Paints the chevron.
         *
         * @param component owner component
         * @param graphics graphics
         * @param x x position
         * @param y y position
         */
        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(Theme.STATUS_INFO_TEXT);
                int mid = x + 4;
                int top = y + 2;
                int bottom = y + 6;
                if (order == SortOrder.DESCENDING) {
                    g.fillPolygon(new int[] { x + 1, x + 7, mid },
                            new int[] { top, top, bottom }, 3);
                } else {
                    g.fillPolygon(new int[] { x + 1, x + 7, mid },
                            new int[] { bottom, bottom, top }, 3);
                }
            } finally {
                g.dispose();
            }
        }
    }
}
