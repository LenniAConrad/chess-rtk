package application.gui.workbench;

import java.util.List;

import javax.swing.table.AbstractTableModel;

/**
 * Read-only {@link javax.swing.table.TableModel} view over a
 * {@link WorkbenchJobManager}'s history, for the dashboard's recent-jobs table.
 *
 * <p>Rows are ordered newest-first. The model re-snapshots the manager and
 * fires a structure change whenever the manager reports a change, which is
 * simple and correct for a history this small.</p>
 */
final class WorkbenchJobTableModel extends AbstractTableModel {

    /**
     * Serialization identifier for Swing table-model compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Column index: display command.
     */
    static final int COL_COMMAND = 0;

    /**
     * Column index: status label.
     */
    static final int COL_STATUS = 1;

    /**
     * Column index: duration.
     */
    static final int COL_DURATION = 2;

    /**
     * Column index: result summary.
     */
    static final int COL_RESULT = 3;

    /**
     * Column titles.
     */
    private static final String[] COLUMNS = { "Command", "Status", "Duration", "Result" };

    /**
     * Backing job manager.
     */
    private final WorkbenchJobManager manager;

    /**
     * Current snapshot of the manager's history, newest first.
     */
    private List<WorkbenchJob> rows;

    /**
     * Creates a table model bound to a job manager.
     *
     * @param manager job manager to mirror
     */
    WorkbenchJobTableModel(WorkbenchJobManager manager) {
        this.manager = manager;
        this.rows = manager.recent();
        manager.addListener(this::refresh);
    }

    /**
     * Re-snapshots the manager and fires a table data change.
     */
    private void refresh() {
        this.rows = manager.recent();
        fireTableDataChanged();
    }

    /**
     * Returns the job displayed on a row.
     *
     * @param row row index
     * @return job, or null when the row is out of range
     */
    WorkbenchJob jobAt(int row) {
        return row >= 0 && row < rows.size() ? rows.get(row) : null;
    }

    /**
     * Returns the number of visible job rows.
     *
     * @return row count
     */
    @Override
    public int getRowCount() {
        return rows.size();
    }

    /**
     * Returns the number of table columns.
     *
     * @return column count
     */
    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    /**
     * Returns the display name for a column.
     *
     * @param column column index
     * @return column name
     */
    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    /**
     * Returns one table cell value.
     *
     * @param rowIndex row index
     * @param columnIndex column index
     * @return display value
     */
    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        WorkbenchJob job = rows.get(rowIndex);
        return switch (columnIndex) {
            case COL_COMMAND -> job.displayCommand();
            case COL_STATUS -> job.status().label();
            case COL_DURATION -> formatDuration(job.durationMillis());
            case COL_RESULT -> job.resultSummary();
            default -> "";
        };
    }

    /**
     * Returns whether a cell is editable.
     *
     * @param rowIndex row index
     * @param columnIndex column index
     * @return false because job rows are read-only
     */
    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    /**
     * Formats a duration in milliseconds for display.
     *
     * @param millis duration, or negative when unknown
     * @return formatted duration
     */
    static String formatDuration(long millis) {
        if (millis < 0) {
            return "—";
        }
        if (millis < 1000) {
            return millis + " ms";
        }
        return String.format("%.1f s", millis / 1000.0);
    }
}
