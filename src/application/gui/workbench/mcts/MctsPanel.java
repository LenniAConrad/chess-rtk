package application.gui.workbench.mcts;

import static application.gui.workbench.ui.Ui.setColumnWidth;
import application.gui.workbench.Defaults;
import application.gui.workbench.board.BoardStyle;
import application.gui.workbench.command.CommandRunner;
import application.gui.workbench.game.SanRenderer;
import application.gui.workbench.layout.SplitPaneStyler;
import application.gui.workbench.network.TensorViz;
import application.gui.workbench.ui.HoldButton;
import application.gui.workbench.ui.StatusBadge;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Toast;
import application.gui.workbench.ui.ToggleBox;
import application.gui.workbench.ui.Ui;
import application.gui.workbench.ui.WrappingFlowLayout;
import chess.core.Move;
import chess.struct.Game;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import application.gui.workbench.ui.SurfacePanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumnModel;

/**
 * First dedicated Swing-native MCTS inspection tab.
 */
public final class MctsPanel extends SurfacePanel implements MctsSession.Listener {

    /**
     * Serialization identifier for Swing component persistence.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Root move table row height.
     */
    private static final int TABLE_ROW_HEIGHT = Theme.TABLE_ROW_HEIGHT;

    /**
     * Shared MCTS session.
     */
    private final MctsSession session;

    /**
     * Supplier for the current main-board FEN.
     */
    private final Supplier<String> currentFen;

    /**
     * Backend selector.
     */
    private final JComboBox<MctsSession.Backend> backendCombo =
            new JComboBox<>(MctsSession.Backend.values());

    /**
     * Visit-limit control.
     */
    private final JSpinner visitsSpinner = new JSpinner(new SpinnerNumberModel(
            Defaults.MCTS_VISITS,
            Defaults.MCTS_VISITS_MIN,
            Defaults.MCTS_VISITS_MAX,
            Defaults.MCTS_VISITS_STEP));

    /**
     * Time-limit control in milliseconds.
     */
    private final JSpinner millisSpinner = new JSpinner(new SpinnerNumberModel(
            Defaults.MCTS_MILLIS,
            Defaults.MCTS_MILLIS_MIN,
            Defaults.MCTS_MILLIS_MAX,
            Defaults.MCTS_MILLIS_STEP));

    /**
     * Exploration-constant control.
     */
    private final JSpinner cpuctSpinner = new JSpinner(new SpinnerNumberModel(
            Double.valueOf(Defaults.MCTS_CPUCT),
            Double.valueOf(0.05),
            Double.valueOf(8.0),
            Double.valueOf(0.25)));

    /**
     * Toggle that keeps the search root following the main board.
     */
    private final ToggleBox followBoardToggle = Ui.withTooltip(
            new ToggleBox("Follow board", true),
            "Keep the MCTS root synchronized with the main board");

    /**
     * Toggle that allows child subtree reuse after root changes.
     */
    private final ToggleBox reuseSubtreeToggle = Ui.withTooltip(
            new ToggleBox("Reuse subtree", true),
            "Reuse searched child subtrees when the board advances to a known position");

    /**
     * Starts a new MCTS worker.
     */
    private final JButton startButton = Ui.button("Start", true, event -> start());

    /**
     * Pauses the current MCTS worker.
     */
    private final JButton pauseButton = Ui.button("Pause", false, null);

    /**
     * Resumes a paused MCTS worker.
     */
    private final JButton resumeButton = Ui.button("Resume", false, null);

    /**
     * Stops the current MCTS worker.
     */
    private final HoldButton stopButton;

    /**
     * Resets the search root to the current main-board FEN.
     */
    private final JButton resetRootButton = Ui.button("Reset root", false, event -> resetRoot());

    /**
     * Compact session status indicator.
     */
    private final StatusBadge statusBadge = new StatusBadge();

    /**
     * Table model for root moves.
     */
    private final RootTableModel rootModel = new RootTableModel();

    /**
     * Root move table.
     */
    private final JTable rootTable = new JTable(rootModel);

    /**
     * Card container for the root-move table and its centered idle state.
     */
    private final JPanel rootTableArea = new JPanel(new CardLayout());

    /**
     * Table content shown after the first root rows arrive.
     */
    private final JPanel rootTableContent = new JPanel(new BorderLayout(0, Theme.SPACE_SM));

    /**
     * Idle state shown before a search streams root rows.
     */
    private final JComponent rootEmptyState =
            Ui.emptyState("No search yet", "Stream root moves and PUCT scores from the selected backend.",
                    Ui.button("Start search", true, event -> start()),
                    Ui.button("Copy command", false, event -> copyCliCommand()));

    /**
     * Selected-node detail area.
     */
    private final JTextArea detailArea = new JTextArea();

    /**
     * Lightweight board preview for the selected node.
     */
    private final BoardPreview boardPreview = new BoardPreview();

    /**
     * Last main-board FEN observed by the panel.
     */
    private String boardFen = Game.STANDARD_START_FEN;

    /**
     * Guards selection events triggered by model refreshes.
     */
    private boolean applyingSelection;

    /**
     * Creates the panel.
     *
     * @param session shared MCTS session
     * @param currentFen current board FEN supplier
     */
    public MctsPanel(MctsSession session, Supplier<String> currentFen) {
        super(new BorderLayout(0, 0), Theme.Surface.BACKDROP);
        this.session = session;
        this.currentFen = currentFen;
        this.stopButton = new HoldButton("Stop", () -> session.stop(), true);
        configureControls();
        add(buildToolbar(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        session.addListener(this);
        setBoardFen(currentFen.get());
        applySnapshot(session.snapshot());
    }

    /**
     * Stops observing the shared session.
     */
    public void dispose() {
        session.removeListener(this);
    }

    /**
     * Updates the main-board FEN observed by this panel.
     *
     * @param fen FEN string
     */
    public void setBoardFen(String fen) {
        if (fen == null || fen.isBlank()) {
            return;
        }
        boardFen = fen;
        if (followBoardToggle.isSelected()) {
            session.requestRootFen(boardFen, reuseSubtreeToggle.isSelected());
        }
    }

    /**
     * Refreshes the panel after the shared session changes.
     *
     * @param source changed session
     */
    @Override
    public void sessionChanged(MctsSession source) {
        applySnapshot(source.snapshot());
    }

    /**
     * Applies styling, listeners, and table sizing to child controls.
     */
    private void configureControls() {
        Ui.styleCombo(backendCombo);
        backendCombo.setPreferredSize(new Dimension(138, Theme.CONTROL_HEIGHT));
        Ui.styleIntegerSpinner(visitsSpinner);
        Ui.styleIntegerSpinner(millisSpinner);
        Ui.styleSpinner(cpuctSpinner);
        visitsSpinner.setPreferredSize(new Dimension(86, Theme.CONTROL_HEIGHT));
        millisSpinner.setPreferredSize(new Dimension(86, Theme.CONTROL_HEIGHT));
        cpuctSpinner.setPreferredSize(new Dimension(76, Theme.CONTROL_HEIGHT));
        statusBadge.setFixedTextWidth(420);
        statusBadge.idle("MCTS idle");
        pauseButton.addActionListener(event -> session.pause());
        resumeButton.addActionListener(event -> session.resume());
        Theme.table(rootTable, TABLE_ROW_HEIGHT);
        rootTable.setAutoCreateRowSorter(true);
        // The principal-variation column (last) absorbs any spare width so the
        // long PV text stays as readable as the viewport allows; the styled
        // renderer adds a hover tooltip when it still has to clip.
        rootTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        rootTable.setFillsViewportHeight(true);
        rootTable.getSelectionModel().addListSelectionListener(this::rootSelectionChanged);
        TableColumnModel columns = rootTable.getColumnModel();
        setColumnWidth(columns, 0, 74);
        setColumnWidth(columns, 1, 72);
        setColumnWidth(columns, 2, 76);
        setColumnWidth(columns, 3, 74);
        setColumnWidth(columns, 4, 70);
        setColumnWidth(columns, 5, 70);
        setColumnWidth(columns, 6, 74);
        setColumnWidth(columns, 7, 320);
        // Render the SAN move and the SAN principal variation with inline
        // figurine piece artwork instead of plain piece letters.
        columns.getColumn(0).setCellRenderer(new SanRenderer());
        columns.getColumn(7).setCellRenderer(new SanRenderer());

        detailArea.setEditable(false);
        detailArea.setOpaque(true);
        detailArea.setBackground(Theme.PANEL_SOLID);
        detailArea.setForeground(Theme.TEXT);
        detailArea.setCaretColor(Theme.ACCENT);
        detailArea.setSelectionColor(Theme.TEXT_SELECTION);
        detailArea.setFont(Theme.mono(12));
        detailArea.setLineWrap(true);
        detailArea.setWrapStyleWord(true);
        detailArea.setBorder(Theme.pad(10));
    }

    /**
     * Builds the top control toolbar.
     *
     * @return toolbar component
     */
    private JComponent buildToolbar() {
        JPanel bar = Ui.transparentPanel(new BorderLayout(Theme.SPACE_MD, 0));
        bar.setOpaque(true);
        bar.setBackground(Theme.PANEL_SOLID);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE),
                Theme.pad(Theme.SPACE_SM)));

        JPanel controls = Ui.transparentPanel(new WrappingFlowLayout(FlowLayout.LEFT, Theme.SPACE_SM, 0));
        controls.add(Ui.labeledControl("Backend", backendCombo));
        controls.add(Ui.labeledControl("Visits", visitsSpinner));
        controls.add(Ui.labeledControl("Millis", millisSpinner));
        controls.add(Ui.labeledControl("Cpuct", cpuctSpinner));
        controls.add(startButton);
        controls.add(pauseButton);
        controls.add(resumeButton);
        controls.add(stopButton);
        controls.add(resetRootButton);
        controls.add(Ui.button("Copy command", false, event -> copyCliCommand()));
        controls.add(followBoardToggle);
        controls.add(reuseSubtreeToggle);

        JPanel status = Ui.transparentPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SPACE_SM, 0));
        status.add(statusBadge);
        bar.add(controls, BorderLayout.CENTER);
        bar.add(status, BorderLayout.EAST);
        return bar;
    }

    /**
     * Builds the main table and inspector split pane.
     *
     * @return body component
     */
    private JComponent buildBody() {
        JScrollPane tableScroll = Ui.scroll(rootTable);
        rootTableArea.setOpaque(true);
        rootTableArea.setBackground(Theme.PANEL_SOLID);
        rootTableContent.setOpaque(true);
        rootTableContent.setBackground(Theme.PANEL_SOLID);
        rootTableContent.add(tableScroll, BorderLayout.CENTER);
        rootTableArea.add(rootEmptyState, "empty");
        rootTableArea.add(rootTableContent, "table");
        rootTableArea.setPreferredSize(new Dimension(740, 260));
        updateRootEmptyState();

        JPanel inspector = new JPanel(new BorderLayout(0, Theme.SPACE_SM));
        inspector.setOpaque(true);
        inspector.setBackground(Theme.PANEL_SOLID);
        inspector.setBorder(Theme.pad(Theme.SPACE_SM));
        inspector.add(boardPreview, BorderLayout.NORTH);
        inspector.add(Ui.scroll(detailArea), BorderLayout.CENTER);
        inspector.setPreferredSize(new Dimension(340, 0));

        JSplitPane horizontal = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, rootTableArea, inspector);
        horizontal.setResizeWeight(0.72);
        horizontal.setBorder(BorderFactory.createEmptyBorder());
        SplitPaneStyler.style(horizontal);
        return horizontal;
    }

    /**
     * Shows the root table when it has rows, otherwise the empty-state.
     */
    private void updateRootEmptyState() {
        ((CardLayout) rootTableArea.getLayout()).show(rootTableArea,
                rootModel.getRowCount() == 0 ? "empty" : "table");
    }

    /**
     * Starts a search from the selected UI controls.
     */
    private void start() {
        String rootFen = followBoardToggle.isSelected() ? boardFen : session.snapshot().rootFen();
        session.start(new MctsSession.Config(
                rootFen,
                (MctsSession.Backend) backendCombo.getSelectedItem(),
                ((Number) visitsSpinner.getValue()).intValue(),
                ((Number) millisSpinner.getValue()).longValue(),
                ((Number) cpuctSpinner.getValue()).doubleValue(),
                reuseSubtreeToggle.isSelected()));
    }

    /**
     * Copies the equivalent {@code crtk engine search} command for the current
     * controls to the system clipboard.
     */
    private void copyCliCommand() {
        List<String> args = new ArrayList<>(List.of("engine", "search"));
        String fen = currentFen.get();
        if (fen != null && !fen.isBlank()) {
            args.add("--fen");
            args.add(fen);
        }
        MctsCliSupport.backendArgs(args, (MctsSession.Backend) backendCombo.getSelectedItem());
        long millis = ((Number) millisSpinner.getValue()).longValue();
        if (millis > 0) {
            args.add("--max-duration");
            args.add(millis + "ms");
        } else {
            args.add("--nodes");
            args.add(Integer.toString(((Number) visitsSpinner.getValue()).intValue()));
        }
        args.add("--cpuct");
        args.add(MctsCliSupport.trimDouble(((Number) cpuctSpinner.getValue()).doubleValue()));
        CommandRunner.copyToClipboard(CommandRunner.displayCommand(args));
        statusBadge.success("command copied");
        Toast.show(this, Toast.Kind.SUCCESS, "Copied to clipboard");
    }

    /**
     * Resets the search root from the main board.
     */
    private void resetRoot() {
        String rootFen = currentFen.get();
        if (rootFen != null && !rootFen.isBlank()) {
            boardFen = rootFen;
        }
        session.requestRootFen(boardFen, false);
    }

    /**
     * Applies the latest session snapshot to visible controls.
     *
     * @param snapshot session snapshot
     */
    private void applySnapshot(MctsSession.Snapshot snapshot) {
        updateButtons(snapshot);
        updateStatus(snapshot);
        MctsSearch.TreeSnapshot tree = snapshot.tree();
        List<MctsSearch.Row> rows = tree == null
                ? snapshot.root() == null ? List.of() : snapshot.root().rows()
                : tree.rootRows();
        String selectedBefore = selectedNodeId();
        applyingSelection = true;
        rootModel.setRows(rows);
        restoreSelection(selectedBefore);
        applyingSelection = false;
        updateRootEmptyState();
        MctsSearch.NodeInfo selected = tree == null ? null : tree.selectedNode();
        if (selected == null && !rows.isEmpty()) {
            selected = rowAsNode(rows.get(0), snapshot.rootFen());
        }
        boardPreview.setPosition(snapshot.rootFen(), selected);
        detailArea.setText(detailText(snapshot, tree, selected));
        detailArea.setCaretPosition(0);
    }

    /**
     * Updates lifecycle buttons from session state.
     *
     * @param snapshot session snapshot
     */
    private void updateButtons(MctsSession.Snapshot snapshot) {
        boolean running = snapshot.state() == MctsSession.State.RUNNING
                || snapshot.state() == MctsSession.State.PAUSED
                || snapshot.state() == MctsSession.State.STARTING;
        boolean paused = snapshot.state() == MctsSession.State.PAUSED;
        startButton.setEnabled(!running);
        pauseButton.setEnabled(running && !paused);
        resumeButton.setEnabled(paused);
        stopButton.setEnabled(running);
        resetRootButton.setEnabled(true);
    }

    /**
     * Updates the status badge from session state.
     *
     * @param snapshot session snapshot
     */
    private void updateStatus(MctsSession.Snapshot snapshot) {
        if (snapshot.state() == MctsSession.State.ERROR) {
            statusBadge.error(snapshot.error() == null ? snapshot.status() : snapshot.error());
        } else if (snapshot.state() == MctsSession.State.RUNNING
                || snapshot.state() == MctsSession.State.STARTING) {
            statusBadge.busy(snapshot.status());
        } else if (snapshot.state() == MctsSession.State.PAUSED) {
            statusBadge.idle(snapshot.status());
        } else if (snapshot.state() == MctsSession.State.DONE) {
            statusBadge.success(snapshot.status());
        } else {
            statusBadge.idle(snapshot.status());
        }
    }

    /**
     * Handles root-table selection changes.
     *
     * @param event selection event
     */
    private void rootSelectionChanged(ListSelectionEvent event) {
        if (event.getValueIsAdjusting() || applyingSelection) {
            return;
        }
        String nodeId = selectedNodeId();
        if (nodeId != null) {
            session.setSelectedNodeId(nodeId);
        }
    }

    /**
     * Returns the selected tree node id.
     *
     * @return selected node id or {@code root}
     */
    private String selectedNodeId() {
        int viewRow = rootTable.getSelectedRow();
        if (viewRow < 0) {
            return "root";
        }
        int modelRow = rootTable.convertRowIndexToModel(viewRow);
        MctsSearch.Row row = rootModel.row(modelRow);
        return row == null ? "root" : row.nodeId();
    }

    /**
     * Restores table selection after replacing model rows.
     *
     * @param nodeId preferred selected node id
     */
    private void restoreSelection(String nodeId) {
        if (rootModel.getRowCount() == 0) {
            rootTable.clearSelection();
            return;
        }
        int modelRow = rootModel.indexOf(nodeId);
        if (modelRow < 0) {
            modelRow = 0;
        }
        int viewRow = rootTable.convertRowIndexToView(modelRow);
        if (viewRow >= 0) {
            rootTable.getSelectionModel().setSelectionInterval(viewRow, viewRow);
        }
    }

    /**
     * Converts a root-row summary into a selected-node fallback.
     *
     * @param row root row
     * @param rootFen source root fen
     * @return node info
     */
    private static MctsSearch.NodeInfo rowAsNode(MctsSearch.Row row, String rootFen) {
        return new MctsSearch.NodeInfo(
                row.nodeId(),
                "root",
                new short[] { row.move() },
                row.pv(),
                row.move(),
                row.san(),
                row.uci(),
                row.uci(),
                row.san(),
                1,
                row.visits(),
                row.prior(),
                row.q(),
                row.u(),
                row.score(),
                0.0,
                0.0,
                0.0,
                "unknown",
                "non-terminal",
                0,
                rootFen,
                row.pvText(),
                0L);
    }

    /**
     * Builds the selected-node detail text.
     *
     * @param snapshot session snapshot
     * @param tree tree snapshot
     * @param selected selected node, or null
     * @return detail text
     */
    private static String detailText(
            MctsSession.Snapshot snapshot,
            MctsSearch.TreeSnapshot tree,
            MctsSearch.NodeInfo selected) {
        StringBuilder sb = new StringBuilder();
        sb.append("Root: ").append(snapshot.rootFen() == null ? "-" : snapshot.rootFen()).append('\n');
        if (tree != null) {
            sb.append("Backend: ").append(tree.backendName())
                    .append("    Visits: ").append(String.format("%,d", tree.playouts()))
                    .append("    Score: ").append(tree.rootScoreLabel()).append('\n');
            sb.append("PV: ").append(blank(tree.bestPvText())).append('\n');
            if (tree.omittedNodes() > 0) {
                sb.append("Omitted: ").append(tree.omittedNodes()).append(" nodes by snapshot cap\n");
            }
        }
        if (selected == null) {
            sb.append("\nSelect a root move to inspect its line.");
            return sb.toString();
        }
        sb.append("\nSelected: ").append(selected.id()).append('\n');
        sb.append("Line: ").append(blank(selected.lineSan())).append('\n');
        sb.append("UCI: ").append(blank(selected.lineUci())).append('\n');
        sb.append("Node PV: ").append(blank(selected.pvText())).append('\n');
        sb.append("Depth: ").append(selected.depth())
                .append("    Children: ").append(selected.childCount())
                .append("    State: ").append(selected.terminalState()).append('\n');
        sb.append("Visits: ").append(String.format("%,d", selected.visits()))
                .append("    Prior: ").append(percent(selected.prior()))
                .append("    Q: ").append(signed(selected.q()))
                .append("    U: ").append(signed(selected.u()))
                .append("    Score: ").append(signed(selected.score())).append('\n');
        sb.append("WDL: ")
                .append(percent(selected.win())).append(" / ")
                .append(percent(selected.draw())).append(" / ")
                .append(percent(selected.loss())).append('\n');
        sb.append("FEN: ").append(selected.fen());
        return sb.toString();
    }

    /**
     * Formats blank text as a dash.
     *
     * @param text input text
     * @return input text or dash
     */
    private static String blank(String text) {
        return text == null || text.isBlank() ? "-" : text;
    }

    /**
     * Formats a signed decimal value.
     *
     * @param value candidate value
     * @return signed text
     */
    private static String signed(double value) {
        return String.format("%+.3f", value);
    }

    /**
     * Formats a normalized probability as a percentage.
     *
     * @param value normalized value
     * @return percent text
     */
    private static String percent(double value) {
        return String.format("%.1f%%", value * 100.0);
    }


    /**
     * Table model for root move rows.
     */
    private static final class RootTableModel extends AbstractTableModel {
        /**
         * Serialization identifier for Swing model persistence.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Current root rows.
         */
        private final List<MctsSearch.Row> rows = new ArrayList<>();

        /**
         * Visible table column names.
         */
        private final String[] columns = {
                "Move", "UCI", "Visits", "Prior", "Q", "U", "Score", "PV"
        };

        /**
         * Replaces the current rows.
         *
         * @param next next row list
         */
        void setRows(List<MctsSearch.Row> next) {
            rows.clear();
            if (next != null) {
                rows.addAll(next);
            }
            fireTableDataChanged();
        }

        /**
         * Returns a row by model index.
         *
         * @param index model row index
         * @return row, or null when out of bounds
         */
        MctsSearch.Row row(int index) {
            return index < 0 || index >= rows.size() ? null : rows.get(index);
        }

        /**
         * Finds the row index for a tree node id.
         *
         * @param nodeId source node id
         * @return model row index, or {@code -1}
         */
        int indexOf(String nodeId) {
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).nodeId().equals(nodeId)) {
                    return i;
                }
            }
            return -1;
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
            return columns.length;
        }

        /**
         * Returns a column display name.
         *
         * @param column column index
         * @return column name
         */
        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        /**
         * Returns a formatted cell value.
         *
         * @param rowIndex zero-based row index
         * @param columnIndex zero-based column index
         * @return cell value
         */
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            MctsSearch.Row row = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> row.san();
                case 1 -> row.uci();
                case 2 -> row.visits();
                case 3 -> percent(row.prior());
                case 4 -> signed(row.q());
                case 5 -> signed(row.u());
                case 6 -> signed(row.score());
                case 7 -> row.pvText();
                default -> "";
            };
        }

        /**
         * Returns the preferred cell class for sorting/rendering.
         *
         * @param columnIndex zero-based column index
         * @return cell class
         */
        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 2 ? Integer.class : String.class;
        }
    }

    /**
     * Board preview component for the selected MCTS line.
     */
    private static final class BoardPreview extends JComponent {
        /**
         * Serialization identifier for Swing component persistence.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Root FEN used before the search has produced a selected node.
         */
        private String rootFen = Game.STANDARD_START_FEN;

        /**
         * Currently selected node.
         */
        private MctsSearch.NodeInfo node;

        /**
         * Creates the board preview.
         */
        BoardPreview() {
            setOpaque(false);
            setPreferredSize(new Dimension(300, 300));
        }

        /**
         * Updates the preview root and selected node.
         *
         * @param root next root FEN
         * @param next next selected node
         */
        void setPosition(String root, MctsSearch.NodeInfo next) {
            rootFen = root == null || root.isBlank() ? Game.STANDARD_START_FEN : root;
            node = next;
            repaint();
        }

        /**
         * Paints the current board preview.
         *
         * @param graphics graphics context
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(Theme.PANEL_SOLID);
                g.fillRect(0, 0, getWidth(), getHeight());
                int side = Math.max(96, Math.min(getWidth() - 24, getHeight() - 40));
                Rectangle board = new Rectangle((getWidth() - side) / 2, 28, side, side);
                g.setColor(Theme.MUTED);
                g.setFont(Theme.font(12, Font.BOLD));
                g.drawString("Board preview", board.x, 18);
                BoardStyle.drawBoardSurface(g, board, true);
                String fen = node != null && node.fen() != null ? node.fen() : rootFen;
                if (fen != null && !fen.isBlank()) {
                    boolean whiteDown = TensorViz.whiteDownForSideToMove(fen);
                    short[] line = node == null ? null : node.line();
                    if (line != null && line.length > 0) {
                        short last = line[line.length - 1];
                        BoardStyle.drawInsetSquareHighlight(g,
                                BoardStyle.fieldSquareBounds(board, Move.getFromIndex(last), whiteDown),
                                Theme.withAlpha(Theme.ACCENT, 230));
                        BoardStyle.drawInsetSquareHighlight(g,
                                BoardStyle.fieldSquareBounds(board, Move.getToIndex(last), whiteDown),
                                Theme.withAlpha(TensorViz.POSITIVE, 230));
                    }
                    TensorViz.drawPositionPieces(g, board, fen, whiteDown);
                    TensorViz.drawBoardCoordinates(g, board, whiteDown);
                }
            } finally {
                g.dispose();
            }
        }
    }
}
