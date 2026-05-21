package application.gui.workbench;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

import application.gui.workbench.mcts.MctsSearch;
import chess.core.Move;
import chess.core.Position;
import chess.struct.Game;

/**
 * Interactive PUCT/MCTS panel for the Analyze side bar.
 */
final class MctsPanel extends JPanel {

    /**
     * Serialization identifier.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Publish every N playouts.
     */
    private static final int PUBLISH_INTERVAL = 24;

    /**
     * Current-board FEN supplied by the workbench.
     */
    private String liveFen = Game.STANDARD_START_FEN;

    /**
     * Editable FEN root.
     */
    private final JTextField fenField = new JTextField(Game.STANDARD_START_FEN);

    /**
     * Playout budget.
     */
    private final JSpinner playoutSpinner = new JSpinner(
            new SpinnerNumberModel(1000, 1, 1_000_000, 100));

    /**
     * Optional wall-clock budget in milliseconds. Zero means visit-only.
     */
    private final JSpinner millisSpinner = new JSpinner(
            new SpinnerNumberModel(0, 0, 3_600_000, 1000));

    /**
     * PUCT exploration constant.
     */
    private final JSpinner cpuctSpinner = new JSpinner(
            new SpinnerNumberModel(1, 1, 5, 1));

    /**
     * Root board with the current best move arrow.
     */
    private final WorkbenchBoardPanel rootBoard = new WorkbenchBoardPanel();

    /**
     * Leaf board showing the latest explored position.
     */
    private final WorkbenchBoardPanel leafBoard = new WorkbenchBoardPanel();

    /**
     * Root-child table model.
     */
    private final DefaultTableModel treeModel = new DefaultTableModel(
            new Object[] { "Move", "Visits", "P", "Q", "U", "Score", "PV" }, 0) {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    /**
     * Root-child table.
     */
    private final JTable treeTable = new JTable(treeModel);

    /**
     * Principal variation readout.
     */
    private final JTextArea pvArea = new JTextArea(3, 20);

    /**
     * Search status line.
     */
    private final JLabel statusLabel = new JLabel("idle");

    /**
     * Start button.
     */
    private final JButton startButton = WorkbenchUi.button("Start", true, event -> startSearch());

    /**
     * Pause/resume button.
     */
    private final JButton pauseButton = WorkbenchUi.button("Pause", false, event -> togglePaused());

    /**
     * Stop button.
     */
    private final JButton stopButton = WorkbenchUi.button("Stop", false, event -> stopSearch());

    /**
     * Active worker.
     */
    private SwingWorker<Void, MctsSearch.Snapshot> worker;

    /**
     * Active search instance.
     */
    private MctsSearch search;

    /**
     * Pause flag read by the worker.
     */
    private volatile boolean paused;

    /**
     * Creates the panel.
     */
    MctsPanel() {
        super(new BorderLayout(WorkbenchTheme.SPACE_SM, WorkbenchTheme.SPACE_SM));
        setOpaque(false);
        add(createControls(), BorderLayout.NORTH);
        add(createCenter(), BorderLayout.CENTER);
        showFen(Game.STANDARD_START_FEN);
        updateButtons(false);
    }

    /**
     * Receives the main-board FEN.
     *
     * @param fen FEN string
     */
    void setFen(String fen) {
        if (fen == null || fen.isBlank()) {
            return;
        }
        liveFen = fen;
        if (!isRunning() && !fenField.hasFocus()) {
            fenField.setText(fen);
            showFen(fen);
        }
    }

    /**
     * Stops background work.
     */
    void dispose() {
        stopSearch();
    }

    /**
     * Creates the controls surface.
     *
     * @return controls component
     */
    private JComponent createControls() {
        JPanel panel = new WorkbenchSurfacePanel(new GridBagLayout());
        GridBagConstraints c = WorkbenchUi.constraints();
        WorkbenchUi.grid(panel, WorkbenchTheme.section("MCTS / PUCT"), c, 0, 0, 4, 1);
        WorkbenchUi.styleFields(fenField);
        fenField.addActionListener(event -> loadFenFromField());
        WorkbenchUi.grid(panel, fenField, c, 0, 1, 4, 1);
        WorkbenchUi.styleIntegerSpinner(playoutSpinner);
        WorkbenchUi.styleIntegerSpinner(millisSpinner);
        WorkbenchUi.styleIntegerSpinner(cpuctSpinner);
        WorkbenchUi.grid(panel, WorkbenchUi.label("Visits"), c, 0, 2, 1, 1);
        WorkbenchUi.grid(panel, playoutSpinner, c, 1, 2, 1, 1);
        WorkbenchUi.grid(panel, WorkbenchUi.label("Millis"), c, 2, 2, 1, 1);
        WorkbenchUi.grid(panel, millisSpinner, c, 3, 2, 1, 1);
        WorkbenchUi.grid(panel, WorkbenchUi.label("Cpuct"), c, 0, 3, 1, 1);
        WorkbenchUi.grid(panel, cpuctSpinner, c, 1, 3, 1, 1);
        WorkbenchUi.grid(panel, buttonRow(), c, 0, 4, 4, 1);
        statusLabel.setForeground(WorkbenchTheme.MUTED);
        statusLabel.setFont(WorkbenchTheme.font(12, java.awt.Font.PLAIN));
        WorkbenchUi.grid(panel, statusLabel, c, 0, 5, 4, 1);
        return panel;
    }

    /**
     * Creates the center split.
     *
     * @return center component
     */
    private JComponent createCenter() {
        JPanel boards = new WorkbenchSurfacePanel(new GridBagLayout());
        GridBagConstraints c = WorkbenchUi.constraints();
        rootBoard.setPreferredSize(new Dimension(186, 186));
        leafBoard.setPreferredSize(new Dimension(186, 186));
        WorkbenchUi.grid(boards, WorkbenchTheme.section("root"), c, 0, 0, 1, 1);
        WorkbenchUi.grid(boards, rootBoard, c, 0, 1, 1, 1);
        WorkbenchUi.grid(boards, WorkbenchTheme.section("current leaf"), c, 0, 2, 1, 1);
        WorkbenchUi.grid(boards, leafBoard, c, 0, 3, 1, 1);

        WorkbenchTheme.table(treeTable, 24);
        treeTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        JScrollPane treeScroll = WorkbenchUi.scroll(treeTable);
        treeScroll.setBorder(BorderFactory.createEmptyBorder());

        WorkbenchUi.styleAreas(pvArea);
        pvArea.setEditable(false);
        pvArea.setLineWrap(true);
        pvArea.setWrapStyleWord(true);

        JPanel right = new JPanel(new BorderLayout(WorkbenchTheme.SPACE_SM, WorkbenchTheme.SPACE_SM));
        right.setOpaque(false);
        right.add(WorkbenchTheme.section("root moves"), BorderLayout.NORTH);
        right.add(treeScroll, BorderLayout.CENTER);
        right.add(WorkbenchUi.titled("Best line", new JScrollPane(pvArea)), BorderLayout.SOUTH);

        JPanel content = new JPanel(new BorderLayout(WorkbenchTheme.SPACE_SM, 0));
        content.setOpaque(false);
        content.add(boards, BorderLayout.WEST);
        content.add(right, BorderLayout.CENTER);
        return content;
    }

    /**
     * Creates the button row.
     *
     * @return button-row component
     */
    private JComponent buttonRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row.setOpaque(false);
        row.add(startButton);
        row.add(pauseButton);
        row.add(stopButton);
        row.add(WorkbenchUi.button("Use board", false, event -> {
            fenField.setText(liveFen);
            showFen(liveFen);
        }));
        row.add(WorkbenchUi.button("Load FEN", false, event -> loadFenFromField()));
        return row;
    }

    /**
     * Starts a new search.
     */
    private void startSearch() {
        stopSearch();
        Position root;
        try {
            root = new Position(fenField.getText().trim());
        } catch (IllegalArgumentException ex) {
            statusLabel.setText("invalid FEN: " + ex.getMessage());
            return;
        }
        showFen(root.toString());
        int budget = ((Number) playoutSpinner.getValue()).intValue();
        long maxMillis = ((Number) millisSpinner.getValue()).longValue();
        double cpuct = ((Number) cpuctSpinner.getValue()).doubleValue();
        paused = false;
        search = new MctsSearch(root, cpuct);
        final MctsSearch activeSearch = search;
        updateButtons(true);
        SwingWorker<Void, MctsSearch.Snapshot> activeWorker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                publish(activeSearch.snapshot(false));
                while (!isCancelled() && activeSearch.shouldContinue(budget, maxMillis)) {
                    if (paused) {
                        publish(activeSearch.snapshot(true));
                        Thread.sleep(80L);
                        continue;
                    }
                    activeSearch.iterate();
                    if (activeSearch.playouts() % PUBLISH_INTERVAL == 0) {
                        publish(activeSearch.snapshot(false));
                    }
                }
                publish(activeSearch.snapshot(paused));
                return null;
            }

            @Override
            protected void process(List<MctsSearch.Snapshot> chunks) {
                if (!chunks.isEmpty()) {
                    showSnapshot(chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                if (worker != this || search != activeSearch) {
                    return;
                }
                updateButtons(false);
                showSnapshot(activeSearch.snapshot(paused));
                activeSearch.close();
            }
        };
        worker = activeWorker;
        activeWorker.execute();
    }

    /**
     * Toggles pause/resume.
     */
    private void togglePaused() {
        if (!isRunning()) {
            return;
        }
        paused = !paused;
        pauseButton.setText(paused ? "Resume" : "Pause");
        statusLabel.setText(paused ? "paused" : "running");
    }

    /**
     * Stops the active search.
     */
    private void stopSearch() {
        MctsSearch activeSearch = search;
        if (worker != null && !worker.isDone()) {
            worker.cancel(true);
        }
        worker = null;
        search = null;
        paused = false;
        updateButtons(false);
        if (activeSearch != null) {
            activeSearch.close();
        }
    }

    /**
     * Returns whether a search is active.
     *
     * @return true while a search worker is active
     */
    private boolean isRunning() {
        return worker != null && !worker.isDone();
    }

    /**
     * Updates button enabled state.
     *
     * @param running true when search is running
     */
    private void updateButtons(boolean running) {
        startButton.setEnabled(!running);
        pauseButton.setEnabled(running);
        stopButton.setEnabled(running);
        pauseButton.setText(paused ? "Resume" : "Pause");
    }

    /**
     * Loads the typed FEN into both boards.
     */
    private void loadFenFromField() {
        try {
            Position position = new Position(fenField.getText().trim());
            showFen(position.toString());
            statusLabel.setText("root loaded");
        } catch (IllegalArgumentException ex) {
            statusLabel.setText("invalid FEN: " + ex.getMessage());
        }
    }

    /**
     * Paints both boards at a FEN.
     *
     * @param fen FEN to show
     */
    private void showFen(String fen) {
        try {
            Position position = new Position(fen);
            rootBoard.setPosition(position.copy(), Move.NO_MOVE);
            leafBoard.setPosition(position.copy(), Move.NO_MOVE);
            rootBoard.setSuggestedMove(Move.NO_MOVE);
            treeModel.setRowCount(0);
            pvArea.setText("");
        } catch (IllegalArgumentException ex) {
            // The caller already surfaces FEN errors.
        }
    }

    /**
     * Applies a search snapshot to the UI.
     *
     * @param snapshot search snapshot
     */
    private void showSnapshot(MctsSearch.Snapshot snapshot) {
        rootBoard.setPosition(new Position(snapshot.rootFen()), Move.NO_MOVE);
        rootBoard.setSuggestedMove(snapshot.bestMove());
        leafBoard.setPosition(snapshot.exploringPosition(), Move.NO_MOVE);
        treeModel.setRowCount(0);
        for (MctsSearch.Row row : snapshot.rows()) {
            treeModel.addRow(new Object[] {
                    row.san(),
                    row.visits(),
                    String.format("%.1f%%", row.prior() * 100.0),
                    String.format("%+.3f", row.q()),
                    String.format("%.3f", row.u()),
                    String.format("%+.3f", row.score()),
                    row.pvText()
            });
        }
        pvArea.setText(snapshot.bestPvText());
        String best = snapshot.bestMove() == Move.NO_MOVE ? "-"
                : Move.toString(snapshot.bestMove());
        statusLabel.setText(String.format("%s · %,d visits · %,d ms · root %s · best %s",
                snapshot.paused() ? "paused" : isRunning() ? "running" : "done",
                snapshot.playouts(),
                snapshot.elapsedMillis(),
                snapshot.rootScoreLabel(),
                best));
    }
}
