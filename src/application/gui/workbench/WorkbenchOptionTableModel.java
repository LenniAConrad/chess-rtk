package application.gui.workbench;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.swing.table.AbstractTableModel;

import application.gui.workbench.WorkbenchCommandTemplates.CommandOption;
import application.gui.workbench.WorkbenchCommandTemplates.TemplateContext;
import application.gui.workbench.WorkbenchCommandTemplates.ValueSource;

/**
 * Table model for enabling, disabling, and editing command-builder flags.
 */
final class WorkbenchOptionTableModel extends AbstractTableModel {

    /**
     * Serialization identifier for Swing table model compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Use column index.
     */
    private static final int COL_USE = 0;

    /**
     * Flag column index.
     */
    private static final int COL_FLAG = 1;

    /**
     * Value column index.
     */
    private static final int COL_VALUE = 2;

    /**
     * Description column index.
     */
    private static final int COL_DESCRIPTION = 3;

    /**
     * Column names.
     */
    private static final String[] COLUMNS = { "Use", "Flag", "Value", "Description" };

    /**
     * Editable option rows.
     */
    private final List<OptionRow> rows = new ArrayList<>();

    /**
     * Sets the available options for the selected command.
     *
     * @param options command options
     * @param context template context
     */
    void setOptions(List<CommandOption> options, TemplateContext context) {
        rows.clear();
        for (CommandOption option : options) {
            rows.add(new OptionRow(option, option.enabledByDefault(), option.initialValue(context)));
        }
        enforceEnabledConflicts();
        fireTableDataChanged();
    }

    /**
     * Refreshes dynamic values such as the current FEN.
     *
     * @param context template context
     */
    void refreshDynamicValues(TemplateContext context) {
        for (int i = 0; i < rows.size(); i++) {
            OptionRow row = rows.get(i);
            if (row.option().source() != ValueSource.STATIC) {
                String value = row.option().initialValue(context);
                if (!Objects.equals(row.value(), value)) {
                    row.value(value);
                    fireTableCellUpdated(i, COL_VALUE);
                }
            }
        }
    }

    /**
     * Resets the rows to command defaults.
     *
     * @param context template context
     */
    void resetDefaults(TemplateContext context) {
        for (int i = 0; i < rows.size(); i++) {
            OptionRow row = rows.get(i);
            row.enabled(row.option().enabledByDefault());
            row.value(row.option().initialValue(context));
        }
        enforceEnabledConflicts();
        fireTableDataChanged();
    }

    /**
     * Disables all options except defaults.
     */
    void clearOptional() {
        for (OptionRow row : rows) {
            row.enabled(row.option().enabledByDefault());
        }
        enforceEnabledConflicts();
        fireTableDataChanged();
    }

    /**
     * Builds enabled command arguments.
     *
     * @return command arguments
     */
    List<String> enabledArgs() {
        validateEnabledConflicts();
        List<String> args = new ArrayList<>();
        for (OptionRow row : rows) {
            if (!row.enabled()) {
                continue;
            }
            String value = row.value().trim();
            if (row.option().takesValue() && value.isEmpty()) {
                continue;
            }
            String flag = row.option().flag();
            if (!flag.isBlank()) {
                args.add(flag);
            }
            if (row.option().takesValue()) {
                args.add(value);
            }
        }
        return List.copyOf(args);
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
        return COLUMNS.length;
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
     * Returns a column class.
     *
     * @param columnIndex column index
     * @return column value class
     */
    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return columnIndex == COL_USE ? Boolean.class : String.class;
    }

    /**
     * Returns whether a cell is editable.
     *
     * @param rowIndex row index
     * @param columnIndex column index
     * @return true when editable
     */
    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex == COL_USE || (columnIndex == COL_VALUE && rows.get(rowIndex).option().takesValue());
    }

    /**
     * Returns a cell value.
     *
     * @param rowIndex row index
     * @param columnIndex column index
     * @return cell value
     */
    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        OptionRow row = rows.get(rowIndex);
        return switch (columnIndex) {
            case COL_USE -> row.enabled();
            case COL_FLAG -> row.option().flag().isBlank() ? "(argument)" : row.option().flag();
            case COL_VALUE -> row.option().takesValue() ? row.value() : "";
            case COL_DESCRIPTION -> row.option().description();
            default -> "";
        };
    }

    /**
     * Sets a cell value.
     *
     * @param value new value
     * @param rowIndex row index
     * @param columnIndex column index
     */
    @Override
    public void setValueAt(Object value, int rowIndex, int columnIndex) {
        OptionRow row = rows.get(rowIndex);
        if (columnIndex == COL_USE && value instanceof Boolean enabled) {
            row.enabled(enabled);
            fireTableCellUpdated(rowIndex, COL_USE);
            if (enabled.booleanValue()) {
                disableConflictingRows(rowIndex);
            }
        } else if (columnIndex == COL_VALUE) {
            row.value(String.valueOf(value));
            fireTableCellUpdated(rowIndex, COL_VALUE);
            if (!row.value().isBlank() && !row.enabled()) {
                row.enabled(true);
                fireTableCellUpdated(rowIndex, COL_USE);
                disableConflictingRows(rowIndex);
            }
        }
    }

    /**
     * Enforces mutual-exclusion rules for the current enabled rows.
     */
    private void enforceEnabledConflicts() {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).enabled()) {
                disableConflictingRows(i);
            }
        }
    }

    /**
     * Disables any enabled rows that conflict with the selected row.
     *
     * @param selectedIndex selected row index
     * @return true when another row changed
     */
    private boolean disableConflictingRows(int selectedIndex) {
        CommandOption selected = rows.get(selectedIndex).option();
        boolean changed = false;
        for (int i = 0; i < rows.size(); i++) {
            if (i == selectedIndex) {
                continue;
            }
            OptionRow candidate = rows.get(i);
            if (candidate.enabled() && selected.conflictsWith(candidate.option())) {
                candidate.enabled(false);
                fireTableCellUpdated(i, COL_USE);
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Guards command creation against stale mutually-exclusive selections.
     */
    private void validateEnabledConflicts() {
        for (int left = 0; left < rows.size(); left++) {
            OptionRow leftRow = rows.get(left);
            if (!leftRow.enabled()) {
                continue;
            }
            for (int right = left + 1; right < rows.size(); right++) {
                OptionRow rightRow = rows.get(right);
                if (rightRow.enabled() && leftRow.option().conflictsWith(rightRow.option())) {
                    throw new IllegalArgumentException("choose only one of "
                            + displayFlag(leftRow.option()) + " and " + displayFlag(rightRow.option()));
                }
            }
        }
    }

    /**
     * Returns a user-facing option label.
     *
     * @param option option metadata
     * @return display label
     */
    private static String displayFlag(CommandOption option) {
        return option.flag().isBlank() ? "(argument)" : option.flag();
    }

    /**
     * Mutable table row.
     */
    private static final class OptionRow {

        /**
         * Option metadata.
         */
        private final CommandOption option;

        /**
         * Whether the option is enabled.
         */
        private boolean enabled;

        /**
         * Option value.
         */
        private String value;

        /**
         * Creates an option row.
         *
         * @param option option metadata
         * @param enabled whether the option is enabled
         * @param value option value
         */
        OptionRow(CommandOption option, boolean enabled, String value) {
            this.option = option;
            this.enabled = enabled;
            this.value = value;
        }

        /**
         * Returns option metadata.
         *
         * @return option metadata
         */
        CommandOption option() {
            return option;
        }

        /**
         * Returns whether the option is enabled.
         *
         * @return true when enabled
         */
        boolean enabled() {
            return enabled;
        }

        /**
         * Returns the option value.
         *
         * @return option value
         */
        String value() {
            return value;
        }

        /**
         * Updates enabled state.
         *
         * @param next new state
         */
        void enabled(boolean next) {
            enabled = next;
        }

        /**
         * Updates option value.
         *
         * @param next new value
         */
        void value(String next) {
            value = next;
        }
    }
}
