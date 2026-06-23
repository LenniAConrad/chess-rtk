package application.gui.workbench.game;

import application.gui.workbench.ui.NotationPainter;
import application.gui.workbench.ui.Theme;
import chess.core.Position;
import chess.core.Setup;
import chess.eco.Encyclopedia;
import chess.eco.Entry;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumnModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;

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
import static application.gui.workbench.ui.Ui.styleTree;
import static application.gui.workbench.ui.Ui.tabbedPane;
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
     * Shared data-table row height so the ECO grid matches every other table.
     */
    private static final int ROW_HEIGHT = Theme.TABLE_ROW_HEIGHT;

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
     * Root node for the current ECO opening tree.
     */
    private DefaultMutableTreeNode treeRoot = new DefaultMutableTreeNode(EcoTreeNode.root("ECO root", List.of()));

    /**
     * Tree model showing the current ECO prefix and continuations.
     */
    private final DefaultTreeModel treeModel = new DefaultTreeModel(treeRoot);

    /**
     * Tree view of ECO continuations reachable from the current prefix.
     */
    private final JTree openingTree = new JTree(treeModel);

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
     * Returns the number of visible rows in the ECO opening tree.
     *
     * @return visible tree row count
     */
    public int treeRowCount() {
        return openingTree.getRowCount();
    }

    /**
     * Selects the first visible child under the ECO tree root.
     *
     * @return true when a child node was selected
     */
    public boolean selectFirstTreeChild() {
        if (treeRoot.getChildCount() == 0) {
            return false;
        }
        DefaultMutableTreeNode child = (DefaultMutableTreeNode) treeRoot.getChildAt(0);
        openingTree.setSelectionPath(new TreePath(child.getPath()));
        openingTree.scrollPathToVisible(new TreePath(child.getPath()));
        return true;
    }

    /**
     * Loads the selected ECO tree prefix into the workbench.
     *
     * @return true when a non-root tree node was selected and loaded
     */
    public boolean loadSelectedTreeLine() {
        EcoTreeNode node = requireSelectedTreeNode();
        if (node == null) {
            return false;
        }
        loadLineConsumer.accept(node.movetext());
        setStatus("Loaded " + node.summary(), Theme.ForegroundRole.SUCCESS);
        return true;
    }

    /**
     * Copies the selected ECO tree prefix movetext.
     *
     * @return true when copied
     */
    public boolean copySelectedTreeLine() {
        EcoTreeNode node = requireSelectedTreeNode();
        if (node == null) {
            return false;
        }
        copyTextConsumer.accept(node.movetext());
        setStatus("Copied tree line", Theme.ForegroundRole.SUCCESS);
        return true;
    }

    /**
     * Returns the selected ECO row, or {@code null} after setting a "select a
     * line first" warning. Shared by the load/copy actions so the guard and its
     * message live in one place.
     *
     * @return selected row, or null when nothing is selected
     */
    private EcoRow requireSelectedRow() {
        EcoRow row = selectedRow();
        if (row == null) {
            setStatus("Select an ECO line first", Theme.ForegroundRole.WARNING);
        }
        return row;
    }

    /**
     * Returns the selected tree node, or {@code null} after setting a "select a
     * tree node first" warning.
     *
     * @return selected tree node, or null when nothing usable is selected
     */
    private EcoTreeNode requireSelectedTreeNode() {
        EcoTreeNode node = selectedTreeNode();
        if (node == null || node.root() || node.movetext().isBlank()) {
            setStatus("Select an ECO tree node first", Theme.ForegroundRole.WARNING);
            return null;
        }
        return node;
    }

    /**
     * Loads the selected ECO line into the workbench.
     *
     * @return true when a row was selected and loaded
     */
    public boolean loadSelectedLine() {
        EcoRow row = requireSelectedRow();
        if (row == null) {
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
        EcoRow row = requireSelectedRow();
        if (row == null) {
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
        EcoRow row = requireSelectedRow();
        if (row == null) {
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
        setBorder(Theme.pad(Theme.SPACE_MD));
        add(collapsible("Search", createHeader(), true), BorderLayout.NORTH);
        add(createResultsPanel(), BorderLayout.CENTER);
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
    private JComponent createResultsPanel() {
        JTabbedPane tabs = tabbedPane();
        tabs.addTab("Tree", createTreePanel());
        tabs.addTab("Lines", createTablePanel());
        JPanel panel = transparentPanel(new BorderLayout(0, 5));
        panel.add(statusLabel, BorderLayout.NORTH);
        panel.add(tabs, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Creates the ECO opening tree view.
     *
     * @return tree component
     */
    private JComponent createTreePanel() {
        openingTree.setRootVisible(true);
        openingTree.setShowsRootHandles(true);
        openingTree.setModel(treeModel);
        styleTree(openingTree);
        openingTree.setCellRenderer(new EcoTreeRenderer());
        openingTree.setToolTipText("ECO opening tree for the current board prefix");

        JPanel panel = transparentPanel(new BorderLayout(0, 5));
        panel.add(scroll(openingTree), BorderLayout.CENTER);
        return panel;
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
        columns.getColumn(1).setCellRenderer(new SanRenderer());
        columns.getColumn(4).setCellRenderer(new SanRenderer());
    }

    /**
     * Creates action buttons.
     *
     * @return action component
     */
    private JComponent createActions() {
        JButton refresh = button("Refresh", false, event -> refresh());
        return buttonRow(FlowLayout.LEFT,
                button("Load Node", true, event -> loadSelectedTreeLine()),
                button("Copy Node", false, event -> copySelectedTreeLine()),
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
            rebuildTree(List.of(), false);
            setStatus(loadError == null ? "No ECO entries loaded" : loadError, Theme.ForegroundRole.ERROR);
            return;
        }
        String query = filterText();
        boolean searching = !query.isBlank();
        List<EcoRow> rows = new ArrayList<>();
        List<BookEntry> treeEntries = new ArrayList<>();
        for (BookEntry entry : bookEntries) {
            if (searching && !entry.searchable().contains(query)) {
                continue;
            }
            if (!searching && currentPathStandard && !isContinuation(entry.moves(), currentPath)) {
                continue;
            }
            treeEntries.add(entry);
            rows.add(toRow(entry));
            if (rows.size() >= DISPLAY_LIMIT && !searching && currentPath.isEmpty()) {
                break;
            }
        }
        tableModel.setRows(rows);
        rebuildTree(!searching && !currentPathStandard ? List.of() : treeEntries, searching);
        updateStatusForRows(rows.size(), searching);
    }

    /**
     * Rebuilds the prefix tree for the current ECO scope.
     *
     * @param entries ECO entries in the current table scope
     * @param searching true when the text filter is active
     */
    private void rebuildTree(List<BookEntry> entries, boolean searching) {
        List<Short> rootPath = searching ? List.of() : currentPath;
        TreeBuildNode root = new TreeBuildNode(rootPath, treeRootLabel(searching), currentMatch);
        root.lineCount = entries.size();
        int matchedPrefix = searching || !currentPathStandard ? 0 : currentPath.size();
        for (BookEntry entry : entries) {
            addTreeEntry(root, entry, matchedPrefix);
        }
        treeRoot = toTreeNode(root, true);
        treeModel.setRoot(treeRoot);
        treeModel.reload();
        openingTree.expandRow(0);
    }

    /**
     * Adds one ECO entry to the current prefix tree.
     *
     * @param root tree root
     * @param entry ECO entry
     * @param matchedPrefix number of already-matched current-path plies
     */
    private void addTreeEntry(TreeBuildNode root, BookEntry entry, int matchedPrefix) {
        TreeBuildNode node = root;
        Position cursor = STANDARD_START.copy();
        short[] moves = entry.moves();
        for (int i = 0; i < moves.length; i++) {
            String san = PositionText.safeSan(cursor, moves[i]);
            cursor.play(moves[i]);
            if (i < matchedPrefix) {
                continue;
            }
            node = node.child(moves[i], san);
            node.lineCount++;
            if (i == moves.length - 1) {
                node.entry = entry;
            }
        }
    }

    /**
     * Converts an internal tree node into a Swing tree node.
     *
     * @param source internal node
     * @param root true when the node is the displayed root
     * @return Swing tree node
     */
    private DefaultMutableTreeNode toTreeNode(TreeBuildNode source, boolean root) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(EcoTreeNode.from(source, root));
        List<TreeBuildNode> children = new ArrayList<>(source.children.values());
        children.sort(Comparator.comparingInt(TreeBuildNode::lineCount).reversed()
                .thenComparing(TreeBuildNode::san));
        for (TreeBuildNode child : children) {
            node.add(toTreeNode(child, false));
        }
        return node;
    }

    /**
     * Returns the root label for the current tree scope.
     *
     * @param searching true when the text filter is active
     * @return tree root label
     */
    private String treeRootLabel(boolean searching) {
        if (searching) {
            return "Filtered ECO entries";
        }
        if (!currentPathStandard) {
            return "Off-book position";
        }
        if (currentPath.isEmpty()) {
            return "ECO root";
        }
        if (currentMatch != null) {
            return currentMatch.eco() + " " + currentMatch.name();
        }
        return "Current ECO prefix";
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
     * Returns the selected tree node.
     *
     * @return selected tree node, or null
     */
    private EcoTreeNode selectedTreeNode() {
        TreePath path = openingTree.getSelectionPath();
        if (path == null) {
            return null;
        }
        Object selected = path.getLastPathComponent();
        if (!(selected instanceof DefaultMutableTreeNode treeNode)) {
            return null;
        }
        Object value = treeNode.getUserObject();
        return value instanceof EcoTreeNode node ? node : null;
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
     * Formats a move path as SAN movetext from the standard start position.
     *
     * @param path move path
     * @return SAN movetext, or empty text at the root
     */
    private static String movetext(List<Short> path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        Position cursor = STANDARD_START.copy();
        for (Short boxedMove : path) {
            if (boxedMove == null) {
                continue;
            }
            short move = boxedMove.shortValue();
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(PositionText.safeSan(cursor, move));
            cursor.play(move);
        }
        return sb.toString();
    }

    /**
     * Converts a list move path into an immutable primitive-array copy.
     *
     * @param path move path
     * @return primitive move array
     */
    private static short[] pathArray(List<Short> path) {
        if (path == null || path.isEmpty()) {
            return new short[0];
        }
        short[] copy = new short[path.size()];
        for (int i = 0; i < path.size(); i++) {
            copy[i] = path.get(i).shortValue();
        }
        return copy;
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
     * Mutable builder node used while aggregating ECO entries into a prefix tree.
     */
    private static final class TreeBuildNode {

        /**
         * Move path from the standard start to this node.
         */
        private final List<Short> path;

        /**
         * Display SAN for the edge into this node, or a root label.
         */
        private final String san;

        /**
         * Child nodes keyed by encoded move.
         */
        private final Map<Short, TreeBuildNode> children = new LinkedHashMap<>();

        /**
         * Representative ECO entry ending exactly on this node.
         */
        private BookEntry entry;

        /**
         * Number of ECO lines passing through this node.
         */
        private int lineCount;

        /**
         * Creates a tree builder node.
         *
         * @param path move path
         * @param san display SAN
         * @param entry exact ECO entry, or null
         */
        private TreeBuildNode(List<Short> path, String san, BookEntry entry) {
            this.path = List.copyOf(path);
            this.san = san == null ? "" : san;
            this.entry = entry;
        }

        /**
         * Returns the existing child for a move, or creates one.
         *
         * @param move encoded move
         * @param san display SAN
         * @return child node
         */
        private TreeBuildNode child(short move, String san) {
            Short key = Short.valueOf(move);
            TreeBuildNode child = children.get(key);
            if (child != null) {
                return child;
            }
            List<Short> nextPath = new ArrayList<>(path);
            nextPath.add(key);
            child = new TreeBuildNode(nextPath, san, null);
            children.put(key, child);
            return child;
        }

        /**
         * Returns the display SAN.
         *
         * @return display SAN
         */
        private String san() {
            return san;
        }

        /**
         * Returns the line count.
         *
         * @return line count
         */
        private int lineCount() {
            return lineCount;
        }
    }

    /**
     * Immutable value stored in one visible ECO tree node.
     *
     * @param label display label
     * @param path move path from the standard start
     * @param movetext SAN movetext for the path
     * @param eco ECO code, or empty text
     * @param name opening name, or empty text
     * @param lineCount number of ECO lines under the node
     * @param root true when this is the tree root
     */
    private record EcoTreeNode(
            String label,
            short[] path,
            String movetext,
            String eco,
            String name,
            int lineCount,
            boolean root) {

        /**
         * Creates a root node.
         *
         * @param label root label
         * @param path current path
         * @return root tree node
         */
        private static EcoTreeNode root(String label, List<Short> path) {
            return new EcoTreeNode(label, pathArray(path), EcoExplorerPanel.movetext(path), "", "", 0, true);
        }

        /**
         * Converts a builder node into an immutable tree value.
         *
         * @param source builder node
         * @param root true when this is the root node
         * @return tree node value
         */
        private static EcoTreeNode from(TreeBuildNode source, boolean root) {
            BookEntry entry = source.entry;
            return new EcoTreeNode(
                    source.san,
                    pathArray(source.path),
                    EcoExplorerPanel.movetext(source.path),
                    entry == null ? "" : entry.eco(),
                    entry == null ? "" : entry.name(),
                    source.lineCount,
                    root);
        }

        /**
         * Defensive path copy for immutable record semantics.
         */
        private EcoTreeNode {
            path = path == null ? new short[0] : path.clone();
        }

        /**
         * Returns a defensive move-path copy.
         *
         * @return move path
         */
        @Override
        public short[] path() {
            return path.clone();
        }

        /**
         * Returns a compact status summary for load/copy feedback.
         *
         * @return summary text
         */
        private String summary() {
            if (eco == null || eco.isBlank()) {
                return label;
            }
            return eco + " " + name;
        }

        /**
         * Returns the display text shown by the Swing tree.
         *
         * @return display text
         */
        @Override
        public String toString() {
            if (root) {
                return label + countSuffix();
            }
            String opening = eco == null || eco.isBlank() ? "" : " - " + eco + " " + name;
            return label + countSuffix() + opening;
        }

        /**
         * Formats the descendant line count suffix.
         *
         * @return count suffix
         */
        private String countSuffix() {
            if (lineCount <= 0) {
                return "";
            }
            return " (" + lineCount + (lineCount == 1 ? " line)" : " lines)");
        }
    }

    /**
     * Workbench-coloured renderer for the ECO opening tree.
     */
    private static final class EcoTreeRenderer extends JComponent implements TreeCellRenderer {

        /**
         * Serialization identifier for Swing renderer compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Horizontal row padding.
         */
        private static final int PAD_X = 8;

        /**
         * Current node label.
         */
        private transient String text = "";

        /**
         * Current foreground colour.
         */
        private transient Color textColor = Theme.TEXT;

        /**
         * Renders one tree node in the Workbench palette.
         *
         * @param tree source tree
         * @param value node value
         * @param selected true when selected
         * @param expanded true when expanded
         * @param leaf true when leaf
         * @param row row index
         * @param hasFocus true when focused
         * @return renderer component
         */
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                boolean expanded, boolean leaf, int row, boolean hasFocus) {
            text = value == null ? "" : value.toString();
            setFont(tree == null ? Theme.font(12, Font.PLAIN) : tree.getFont());
            setOpaque(true);
            textColor = Theme.TEXT;
            if (selected) {
                setBackground(Theme.SELECTION_SOLID);
            } else {
                Color treeBackground = tree == null ? null : tree.getBackground();
                setBackground(treeBackground == null ? Theme.PANEL_SOLID : treeBackground);
            }
            return this;
        }

        /**
         * Paints the tree row with inline SAN piece artwork.
         *
         * @param graphics graphics context
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setColor(getBackground());
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setFont(getFont());
                FontMetrics metrics = g.getFontMetrics();
                int baseline = (getHeight() + metrics.getAscent() - metrics.getDescent()) / 2;
                int iconSize = Math.min(18, NotationPainter.iconSize(metrics));
                NotationPainter.draw(g, text, PAD_X, baseline, Math.max(1, getWidth() - PAD_X * 2),
                        textColor, iconSize);
            } finally {
                g.dispose();
            }
        }

        /**
         * Returns the preferred tree-row size.
         *
         * @return preferred size
         */
        @Override
        public Dimension getPreferredSize() {
            FontMetrics metrics = getFontMetrics(getFont());
            int iconSize = Math.min(18, NotationPainter.iconSize(metrics));
            int width = PAD_X * 2 + NotationPainter.width(text, metrics, iconSize);
            int height = Math.max(Theme.TABLE_ROW_HEIGHT, metrics.getHeight() + 8);
            return new Dimension(width, height);
        }
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
