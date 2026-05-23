/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package application.gui.workbench.dataset;

import java.util.List;
import javax.swing.table.AbstractTableModel;

/**
 * Read-only table model for dataset sample and issue rows.
 */
public final class DatasetTableModel extends AbstractTableModel {

    /**
     * Serialization identifier for Swing table model compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Table column names.
     */
    private static final String[] COLUMNS = {
        "File", "Line", "Kind", "Side", "Material", "Label", "FEN / row", "Issue"
    };

    /**
     * Visible rows.
     */
    private List<DatasetSummary.SampleRow> rows = List.of();

    /**
     * Whether the issue column should be included.
     */
    private final boolean showIssue;

    /**
     * Creates a table model.
     *
     * @param showIssue true to include the issue column
     */
    public DatasetTableModel(boolean showIssue) {
        this.showIssue = showIssue;
    }

    /**
     * Replaces visible rows.
     *
     * @param nextRows rows to display
     */
    public void setRows(List<DatasetSummary.SampleRow> nextRows) {
        rows = nextRows == null ? List.of() : List.copyOf(nextRows);
        fireTableDataChanged();
    }

    /**
     * Returns row count.
     *
     * @return row count
     */
    @Override
    public int getRowCount() {
        return rows.size();
    }

    /**
     * Returns column count.
     *
     * @return column count
     */
    @Override
    public int getColumnCount() {
        return showIssue ? COLUMNS.length : COLUMNS.length - 1;
    }

    /**
     * Returns a column name.
     *
     * @param column column index
     * @return column name
     */
    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    /**
     * Returns the class used by a column.
     *
     * @param columnIndex column index
     * @return column class
     */
    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return switch (columnIndex) {
            case 1, 4 -> Number.class;
            default -> String.class;
        };
    }

    /**
     * Returns a table value.
     *
     * @param rowIndex row index
     * @param columnIndex column index
     * @return cell value
     */
    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        DatasetSummary.SampleRow row = rows.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> row.file();
            case 1 -> row.line();
            case 2 -> row.kind();
            case 3 -> row.side();
            case 4 -> row.material();
            case 5 -> row.label();
            case 6 -> row.fen();
            case 7 -> row.issue();
            default -> "";
        };
    }

    /**
     * Returns whether a cell can be edited.
     *
     * @param rowIndex row index
     * @param columnIndex column index
     * @return false for every cell
     */
    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }
}
