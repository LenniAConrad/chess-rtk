package application.gui.workbench.mcts;

import java.util.ArrayList;
import java.util.List;
import javax.swing.table.AbstractTableModel;

/**
 * Table model for the search-tree inspector's child rows.
 */
final class TreeChildTableModel extends AbstractTableModel {
    /**
     * Serialization identifier.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Column names.
     */
    private final String[] columns = { "Move", "Visits", "N%", "Q", "Prior", "PUCT" };

    /**
     * Current rows.
     */
    private final transient List<MctsSearch.NodeInfo> rows = new ArrayList<>();

    /**
     * Visit denominator for the N% column.
     */
    private int shareBase = 1;

    /**
     * Replaces table rows and updates the visit-share denominator.
     *
     * @param next next root-child rows
     * @param base visit count used as 100 percent share
     */
    void setRows(List<MctsSearch.NodeInfo> next, int base) {
        rows.clear();
        if (next != null) {
            rows.addAll(next);
        }
        shareBase = Math.max(1, base);
        fireTableDataChanged();
    }

    /**
     * Returns one row from the current root-child table.
     *
     * @param index zero-based index
     * @return row, or {@code null} when the index is out of range
     */
    MctsSearch.NodeInfo row(int index) {
        return index < 0 || index >= rows.size() ? null : rows.get(index);
    }

    /**
     * Returns the number of displayed child rows.
     *
     * @return row count
     */
    @Override
    public int getRowCount() {
        return rows.size();
    }

    /**
     * Returns the number of visible columns.
     *
     * @return column count
     */
    @Override
    public int getColumnCount() {
        return columns.length;
    }

    /**
     * Returns the column title.
     *
     * @param column column index
     * @return column name
     */
    @Override
    public String getColumnName(int column) {
        return columns[column];
    }

    /**
     * Returns the preferred column value class.
     *
     * @param columnIndex zero-based column index
     * @return value class
     */
    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return columnIndex == 1 ? Integer.class : String.class;
    }

    /**
     * Returns the displayed cell value.
     *
     * @param rowIndex zero-based row index
     * @param columnIndex zero-based column index
     * @return cell value
     */
    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        MctsSearch.NodeInfo row = rows.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> row.san() == null || row.san().isBlank() ? row.uci() : row.san();
            case 1 -> row.visits();
            case 2 -> String.format("%.0f%%", 100.0 * row.visits() / shareBase);
            case 3 -> String.format("%+.2f", row.q());
            case 4 -> String.format("%.1f%%", row.prior() * 100.0);
            case 5 -> String.format("%+.2f", row.score());
            default -> "";
        };
    }
}
