/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package application.gui.workbench.game;

import application.gui.workbench.ui.Theme;
import chess.core.Position;
import chess.core.Setup;
import chess.eco.Encyclopedia;
import chess.eco.Entry;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumnModel;

import static application.gui.workbench.ui.Ui.button;
import static application.gui.workbench.ui.Ui.buttonRow;
import static application.gui.workbench.ui.Ui.changeListener;
import static application.gui.workbench.ui.Ui.collapsible;
import static application.gui.workbench.ui.Ui.constraints;
import static application.gui.workbench.ui.Ui.grid;
import static application.gui.workbench.ui.Ui.label;
import static application.gui.workbench.ui.Ui.placeholder;
import static application.gui.workbench.ui.Ui.scroll;
import static application.gui.workbench.ui.Ui.styleFields;
import static application.gui.workbench.ui.Ui.transparentPanel;

/**
 * Browses the bundled ECO book and links it to the current workbench line.
 */
public final class EcoExplorerPanel extends JPanel {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Standard starting position used by the ECO book.
     */
    private static final Position STANDARD_START = new Position(Setup.getStandardStartFEN());

    /**
     * Placeholder shown when no value is available.
     */
    private static final String NONE = "-";

    /**
     * Maximum rows shown before search or current-line filtering is encouraged.
     */
    private static final int DISPLAY_LIMIT = 800;

    /**
     * Minimum row height for dense ECO entries.
     */
    private static final int ROW_HEIGHT = 24;

    /**
     * Supplies the current game root position.
     */
    private final Supplier<Position> startPositionSupplier;

    /**
     * Supplies the current move path from the root.
     */
    private final Supplier<List<Short>> currentPathSupplier;

    /**
     * Loads a selected ECO movetext into the workbench.
     */
    private final Consumer<String> loadLineConsumer;

    /**
     * Copies selected ECO text through the workbench clipboard helper.
     */
    private final Consumer<String> copyTextConsumer;

    /**
     * Parsed ECO book entries.
     */
    private final List<BookEntry> bookEntries;

    /**
     * Error text when the ECO book failed to load.
     */
    private final String loadError;

    /**
     * Current ECO code label.
     */
    private final JLabel codeLabel = valueLabel(NONE);

    /**
     * Current opening-name label.
     */
    private final JLabel nameLabel = valueLabel("No current ECO match");

    /**
     * Current line-depth label.
     */
    private final JLabel plyLabel = valueLabel("0 plies");

    /**
     * Table status label.
     */
    private final JLabel statusLabel = valueLabel("Loading ECO book");

    /**
     * Search field for code, name, or movetext.
     */
    private final JTextField filterField = new JTextField();

    /**
     * Visible continuation table model.
     */
    private final EcoTableModel tableModel = new EcoTableModel();

    /**
     * Visible continuation table.
     */
    private final JTable table = new JTable(tableModel);

    /**
     * Current standard-start move path.
     */
    private List<Short> currentPath = List.of();

    /**
     * Whether the current path can be matched against standard ECO entries.
     */
    private boolean currentPathStandard = true;

    /**
     * Best known opening ancestor for the current path.
     */
    private BookEntry currentMatch;

    /**
     * Creates an ECO explorer panel.
     *
     * @param startPositionSupplier current game root supplier
     * @param currentPathSupplier current move path supplier
     * @param loadLineConsumer selected-line loader
     * @param copyTextConsumer selected-line clipboard helper
     */
    public EcoExplorerPanel(
            Supplier<Position> startPositionSupplier,
            Supplier<List<Short>> currentPathSupplier,
            Consumer<String> loadLineConsumer,
            Consumer<String> copyTextConsumer) {
        super(new BorderLayout(0, 8));
        this.startPositionSupplier = Objects.requireNonNull(startPositionSupplier, "startPositionSupplier");
        this.currentPathSupplier = Objects.requireNonNull(currentPathSupplier, "currentPathSupplier");
        this.loadLineConsumer = Objects.requireNonNull(loadLineConsumer, "loadLineConsumer");
        this.copyTextConsumer = Objects.requireNonNull(copyTextConsumer, "copyTextConsumer");
        BookLoad load = loadBook();
        this.bookEntries = load.entries();
        this.loadError = load.error();
        configurePanel();
        refresh();
    }

    /**
     * Refreshes from the current suppliers.
     */
    public void refresh() {
        refresh(startPositionSupplier.get(), currentPathSupplier.get());
    }

    /**
     * Refreshes the explorer from an explicit game root and move path.
     *
     * @param startPosition game root position
     * @param path moves from the game root to the visible position
     */
    public void refresh(Position startPosition, List<Short> path) {
        currentPath = safePath(path);
        currentPathStandard = isStandardStart(startPosition) && isBookPrefix(currentPath);
        currentMatch = currentPathStandard ? bestAncestor(currentPath) : null;
        updateCurrentSummary();
        rebuildRows();
    }

    /**
     * Sets the search filter.
     *
     * @param text filter text
     */
    public void setFilter(String text) {
        filterField.setText(text == null ? "" : text);
        rebuildRows();
    }

    /**
     * Returns the number of visible ECO rows.
     *
     * @return visible row count
     */
    public int rowCount() {
        return tableModel.getRowCount();
    }

    /**
     * Returns a compact current-opening summary.
     *
     * @return current opening summary
     */
    public String currentOpeningSummary() {
        return codeLabel.getText() + " " + nameLabel.getText();
    }

    /**
     * Selects the first visible row when one exists.
     *
     * @return true when a row was selected
     */
    public boolean selectFirstRow() {
        if (tableModel.getRowCount() == 0) {
            return false;
        }
        table.setRowSelectionInterval(0, 0);
        return true;
    }

    /**
     * Loads the selected ECO line into the workbench.
     *
     * @return true when a row was selected and loaded
     */
    public boolean loadSelectedLine() {
        EcoRow row = selectedRow();
        if (row == null) {
            setStatus("Select an ECO line first", Theme.ForegroundRole.WARNING);
            return false;
        }
        loadLineConsumer.accept(row.entry().entry().getMovetext());
        setStatus("Loaded " + row.entry().eco(), Theme.ForegroundRole.SUCCESS);
        return true;
    }

    /**
     * Copies the selected line movetext.
     *
     * @return true when copied
     */
    public boolean copySelectedLine() {
        EcoRow row = selectedRow();
        if (row == null) {
            setStatus("Select an ECO line first", Theme.ForegroundRole.WARNING);
            return false;
        }
        copyTextConsumer.accept(row.entry().entry().getMovetext());
        setStatus("Copied line", Theme.ForegroundRole.SUCCESS);
        return true;
    }

    /**
     * Copies the selected entry's final FEN.
     *
     * @return true when copied
     */
    public boolean copySelectedFen() {
        EcoRow row = selectedRow();
        if (row == null) {
            setStatus("Select an ECO line first", Theme.ForegroundRole.WARNING);
            return false;
        }
        Position position = row.entry().entry().getPosition();
        copyTextConsumer.accept(position == null ? "" : position.toString());
        setStatus("Copied final FEN", Theme.ForegroundRole.SUCCESS);
        return true;
    }

    /**
     * Configures controls and layout.
     */
    private void configurePanel() {
        setLayout(new BorderLayout(0, 8));
        setOpaque(true);
        setBackground(Theme.PANEL_SOLID);
        setForeground(Theme.TEXT);
        setBorder(Theme.pad(10, 10, 10, 10));
        add(collapsible("Search", createHeader(), true), BorderLayout.NORTH);
        add(createTablePanel(), BorderLayout.CENTER);
        add(createActions(), BorderLayout.SOUTH);
    }

    /**
     * Creates the summary and search controls.
     *
     * @return header component
     */
    private JComponent createHeader() {
        JPanel header = transparentPanel(new GridBagLayout());
        GridBagConstraints c = constraints();
        c.insets = new Insets(0, 0, 6, 0);
        grid(header, Theme.section("ECO"), c, 0, 0, 4, 1);

        c.insets = new Insets(3, 0, 3, 6);
        grid(header, label("eco"), c, 0, 1, 1, 1);
        grid(header, codeLabel, c, 1, 1, 1, 1);
        grid(header, label("ply"), c, 2, 1, 1, 1);
        grid(header, plyLabel, c, 3, 1, 1, 1);

        grid(header, label("opening"), c, 0, 2, 1, 1);
        grid(header, nameLabel, c, 1, 2, 3, 1);

        styleFields(filterField);
        placeholder(filterField, "Filter code, opening, or movetext");
        filterField.getDocument().addDocumentListener(changeListener(this::rebuildRows));
        grid(header, label("filter"), c, 0, 3, 1, 1);
        grid(header, filterField, c, 1, 3, 3, 1);
        return header;
    }

    /**
     * Creates the ECO result table.
     *
     * @return table component
     */
    private JComponent createTablePanel() {
        Theme.table(table, ROW_HEIGHT);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);
        table.setPreferredScrollableViewportSize(new Dimension(360, 135));
        configureColumns();

        JPanel panel = transparentPanel(new BorderLayout(0, 5));
        panel.add(statusLabel, BorderLayout.NORTH);
        panel.add(scroll(table), BorderLayout.CENTER);
        return panel;
    }

    /**
     * Configures table column widths.
     */
    private void configureColumns() {
        TableColumnModel columns = table.getColumnModel();
        columns.getColumn(0).setPreferredWidth(48);
        columns.getColumn(1).setPreferredWidth(58);
        columns.getColumn(2).setPreferredWidth(44);
        columns.getColumn(3).setPreferredWidth(170);
        columns.getColumn(4).setPreferredWidth(260);
    }

    /**
     * Creates action buttons.
     *
     * @return action component
     */
    private JComponent createActions() {
        JButton refresh = button("Refresh", false, event -> refresh());
        return buttonRow(FlowLayout.LEFT,
                button("Load Line", true, event -> loadSelectedLine()),
                button("Copy Line", false, event -> copySelectedLine()),
                button("Copy FEN", false, event -> copySelectedFen()),
                refresh,
                button("Clear", false, event -> setFilter("")));
    }

    /**
     * Rebuilds visible ECO rows.
     */
    private void rebuildRows() {
        if (bookEntries.isEmpty()) {
            tableModel.setRows(List.of());
            setStatus(loadError == null ? "No ECO entries loaded" : loadError, Theme.ForegroundRole.ERROR);
            return;
        }
        String query = filterText();
        boolean searching = !query.isBlank();
        List<EcoRow> rows = new ArrayList<>();
        for (BookEntry entry : bookEntries) {
            if (searching && !entry.searchable().contains(query)) {
                continue;
            }
            if (!searching && currentPathStandard && !isContinuation(entry.moves(), currentPath)) {
                continue;
            }
            rows.add(toRow(entry));
            if (rows.size() >= DISPLAY_LIMIT && !searching && currentPath.isEmpty()) {
                break;
            }
        }
        tableModel.setRows(rows);
        updateStatusForRows(rows.size(), searching);
    }

    /**
     * Updates the summary labels.
     */
    private void updateCurrentSummary() {
        if (!currentPathStandard) {
            codeLabel.setText(NONE);
            nameLabel.setText("No standard ECO prefix");
        } else if (currentMatch == null) {
            codeLabel.setText(NONE);
            nameLabel.setText(currentPath.isEmpty() ? "Start position" : "Book line, unnamed so far");
        } else {
            codeLabel.setText(currentMatch.eco());
            nameLabel.setText(currentMatch.name());
        }
        plyLabel.setText(currentPath.size() + " plies");
    }

    /**
     * Updates the result status.
     *
     * @param rows visible rows
     * @param searching true when search is active
     */
    private void updateStatusForRows(int rows, boolean searching) {
        if (!currentPathStandard && !searching) {
            setStatus("Off-book line.", Theme.ForegroundRole.WARNING);
            return;
        }
        String scope = searching ? "matching entries" : "continuations";
        String suffix = rows >= DISPLAY_LIMIT && currentPath.isEmpty() && !searching
                ? " shown; filter to narrow" : "";
        setStatus(rows + " " + scope + suffix, Theme.ForegroundRole.MUTED);
    }

    /**
     * Updates the result status text and role.
     *
     * @param text status text
     * @param role semantic foreground role
     */
    private void setStatus(String text, Theme.ForegroundRole role) {
        statusLabel.setText(text);
        Theme.foreground(statusLabel, role);
    }

    /**
     * Converts a book entry into a visible row.
     *
     * @param entry book entry
     * @return visible row
     */
    private EcoRow toRow(BookEntry entry) {
        int matched = currentPathStandard && startsWith(entry.moves(), currentPath) ? currentPath.size() : 0;
        return new EcoRow(entry, nextSan(entry.moves(), matched), entry.moves().length,
                entry.name(), entry.entry().getMovetext());
    }

    /**
     * Returns the current filter text in lower-case form.
     *
     * @return filter text
     */
    private String filterText() {
        String text = filterField.getText();
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Returns the selected visible row.
     *
     * @return selected row, or null
     */
    private EcoRow selectedRow() {
        int selected = table.getSelectedRow();
        if (selected < 0) {
            return null;
        }
        int modelRow = table.convertRowIndexToModel(selected);
        return tableModel.rowAt(modelRow);
    }

    /**
     * Finds the best ECO ancestor for a path.
     *
     * @param path move path
     * @return best ancestor, or null
     */
    private BookEntry bestAncestor(List<Short> path) {
        BookEntry best = null;
        for (BookEntry entry : bookEntries) {
            if (entry.moves().length <= path.size() && startsWith(path, entry.moves())
                    && (best == null || entry.moves().length > best.moves().length)) {
                best = entry;
            }
        }
        return best;
    }

    /**
     * Returns whether a path has at least one book continuation.
     *
     * @param path move path
     * @return true when the path is still in the book
     */
    private boolean isBookPrefix(List<Short> path) {
        if (path.isEmpty()) {
            return true;
        }
        for (BookEntry entry : bookEntries) {
            if (startsWith(entry.moves(), path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether an entry continues a path.
     *
     * @param entryMoves entry moves
     * @param path current path
     * @return true when the entry has more moves after the path
     */
    private static boolean isContinuation(short[] entryMoves, List<Short> path) {
        return entryMoves.length > path.size() && startsWith(entryMoves, path);
    }

    /**
     * Returns whether an entry move array starts with a list path.
     *
     * @param moves entry moves
     * @param prefix prefix path
     * @return true when prefix matches
     */
    private static boolean startsWith(short[] moves, List<Short> prefix) {
        if (prefix.size() > moves.length) {
            return false;
        }
        for (int i = 0; i < prefix.size(); i++) {
            if (moves[i] != prefix.get(i).shortValue()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns whether one path starts with another path.
     *
     * @param path path moves
     * @param prefix prefix moves
     * @return true when prefix matches
     */
    private static boolean startsWith(List<Short> path, short[] prefix) {
        if (prefix.length > path.size()) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (path.get(i).shortValue() != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Formats the next move after a matched prefix.
     *
     * @param moves entry moves
     * @param index matched prefix length
     * @return next SAN text, or {@link #NONE}
     */
    private static String nextSan(short[] moves, int index) {
        if (index < 0 || index >= moves.length) {
            return NONE;
        }
        Position cursor = STANDARD_START.copy();
        for (int i = 0; i < index; i++) {
            cursor.play(moves[i]);
        }
        return PositionText.safeSan(cursor, moves[index]);
    }

    /**
     * Returns a safe copy of a nullable path.
     *
     * @param path nullable path
     * @return immutable path
     */
    private static List<Short> safePath(List<Short> path) {
        if (path == null || path.isEmpty()) {
            return List.of();
        }
        return List.copyOf(path);
    }

    /**
     * Returns whether a position is the standard ECO root.
     *
     * @param position position to test
     * @return true when standard root
     */
    private static boolean isStandardStart(Position position) {
        return position != null && STANDARD_START.toString().equals(position.toString());
    }

    /**
     * Creates a value label.
     *
     * @param text label text
     * @return styled label
     */
    private static JLabel valueLabel(String text) {
        JLabel label = new JLabel(text);
        Theme.foreground(label, Theme.ForegroundRole.TEXT);
        label.setFont(Theme.font(12, Font.PLAIN));
        return label;
    }

    /**
     * Loads and sorts the default ECO book.
     *
     * @return loaded entries and optional error
     */
    private static BookLoad loadBook() {
        try {
            List<BookEntry> rows = new ArrayList<>();
            for (Entry entry : Encyclopedia.defaultBook().entries()) {
                rows.add(new BookEntry(entry, entry.getMoves(), searchable(entry)));
            }
            rows.sort(Comparator.comparing(BookEntry::eco)
                    .thenComparing(BookEntry::name)
                    .thenComparingInt(entry -> entry.moves().length)
                    .thenComparing(entry -> entry.entry().getMovetext()));
            return new BookLoad(List.copyOf(rows), null);
        } catch (RuntimeException ex) {
            return new BookLoad(List.of(), ex.getMessage());
        }
    }

    /**
     * Builds lower-case searchable text for an ECO entry.
     *
     * @param entry ECO entry
     * @return searchable text
     */
    private static String searchable(Entry entry) {
        return (entry.getECO() + " " + entry.getName() + " " + entry.getMovetext())
                .toLowerCase(Locale.ROOT);
    }

    /**
     * Loaded ECO book payload.
     *
     * @param entries parsed entries
     * @param error load error, or null
     */
    private record BookLoad(List<BookEntry> entries, String error) {
    }

    /**
     * Cached ECO entry with cloned move data and searchable text.
     *
     * @param entry original ECO entry
     * @param moves parsed moves
     * @param searchable lower-case search payload
     */
    private record BookEntry(Entry entry, short[] moves, String searchable) {
        /**
         * Returns the ECO code.
         *
         * @return ECO code
         */
        private String eco() {
            return entry.getECO();
        }

        /**
         * Returns the opening name.
         *
         * @return opening name
         */
        private String name() {
            return entry.getName();
        }
    }

    /**
     * Visible ECO table row.
     *
     * @param entry backing book entry
     * @param nextSan next move after the current prefix
     * @param ply total entry length in plies
     * @param name opening name
     * @param movetext SAN movetext
     */
    private record EcoRow(BookEntry entry, String nextSan, int ply, String name, String movetext) {
    }

    /**
     * Swing table model for ECO rows.
     */
    private static final class EcoTableModel extends AbstractTableModel {

        /**
         * Serialization identifier for Swing table compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Column names.
         */
        private static final String[] COLUMNS = { "ECO", "Next", "Ply", "Opening", "Line" };

        /**
         * Visible rows.
         */
        private List<EcoRow> rows = List.of();

        /**
         * Replaces all visible rows.
         *
         * @param nextRows replacement rows
         */
        private void setRows(List<EcoRow> nextRows) {
            rows = List.copyOf(nextRows);
            fireTableDataChanged();
        }

        /**
         * Returns one row by model index.
         *
         * @param row row index
         * @return row value
         */
        private EcoRow rowAt(int row) {
            return row < 0 || row >= rows.size() ? null : rows.get(row);
        }

        /**
         * Returns the row count.
         *
         * @return row count
         */
        @Override
        public int getRowCount() {
            return rows.size();
        }

        /**
         * Returns the column count.
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
         * Returns a column type.
         *
         * @param columnIndex column index
         * @return column class
         */
        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 2 ? Integer.class : String.class;
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
            EcoRow row = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> row.entry().eco();
                case 1 -> row.nextSan();
                case 2 -> Integer.valueOf(row.ply());
                case 3 -> row.name();
                case 4 -> row.movetext();
                default -> "";
            };
        }
    }
}
