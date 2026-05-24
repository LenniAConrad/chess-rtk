package application.gui.workbench.mcts;

import application.gui.workbench.Defaults;
import application.gui.workbench.audio.SoundCue;
import application.gui.workbench.audio.SoundService;
import application.gui.workbench.board.BoardPanel;
import application.gui.workbench.mcts.MctsSearch;
import application.gui.workbench.ui.SurfacePanel;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.ToggleBox;
import application.gui.workbench.ui.Ui;
import chess.core.Move;
import chess.core.Position;
import chess.struct.Game;
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

/**
 * Interactive PUCT/MCTS panel for the Analyze side bar.
 */
public final class MctsPanel extends JPanel {

    /**
     * Serialization identifier.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Publish every N playouts.
     */
    private static final int PUBLISH_INTERVAL = 8;

    /**
     * Initial playouts streamed one-by-one so short searches are visibly live.
     */
    private static final long LIVE_WARMUP_PLAYOUTS = 32L;

    /**
     * Minimum time between regular live UI frames.
     */
    private static final long MIN_PUBLISH_NANOS = 35_000_000L;

    /**
     * Short delay after warmup frames, giving the EDT time to paint the leaf.
     */
    private static final long LIVE_WARMUP_DELAY_MS = 6L;

    /**
     * Minimum time between audible progress ticks.
     */
    private static final long SOUND_INTERVAL_NANOS = 1_350_000_000L;

    /**
     * Current-board FEN supplied by the workbench.
     */
    @SuppressWarnings("java:S1450")
    private String liveFen = Game.STANDARD_START_FEN;

    /**
     * Editable FEN root.
     */
    private final JTextField fenField = new JTextField(Game.STANDARD_START_FEN);

    /**
     * Playout budget.
     */
    private final JSpinner playoutSpinner = new JSpinner(
            new SpinnerNumberModel(
                    Defaults.MCTS_VISITS,
                    Defaults.MCTS_VISITS_MIN,
                    Defaults.MCTS_VISITS_MAX,
                    Defaults.MCTS_VISITS_STEP));

    /**
     * Optional wall-clock budget in milliseconds. Zero means visit-only.
     */
    private final JSpinner millisSpinner = new JSpinner(
            new SpinnerNumberModel(
                    Defaults.MCTS_MILLIS,
                    Defaults.MCTS_MILLIS_MIN,
                    Defaults.MCTS_MILLIS_MAX,
                    Defaults.MCTS_MILLIS_STEP));

    /**
     * PUCT exploration constant.
     */
    private final JSpinner cpuctSpinner = new JSpinner(
            new SpinnerNumberModel(
                    Defaults.MCTS_CPUCT,
                    Defaults.MCTS_CPUCT_MIN,
                    Defaults.MCTS_CPUCT_MAX,
                    Defaults.MCTS_CPUCT_STEP));

    /**
     * Root board with the current best move arrow.
     */
    private final BoardPanel rootBoard = new BoardPanel();

    /**
     * Leaf board showing the latest explored position.
     */
    private final BoardPanel leafBoard = new BoardPanel();

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
    private final JButton startButton = Ui.button("Start", true, event -> startSearch());

    /**
     * Pause/resume button.
     */
    private final JButton pauseButton = Ui.button("Pause", false, event -> togglePaused());

    /**
     * Stop button.
     */
    private final JButton stopButton = Ui.button("Stop", false, event -> stopSearch());

    /**
     * Compact global sound toggle for MCTS controls.
     */
    private final ToggleBox soundOutputToggle = new ToggleBox("Sound", true);

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
     * Last audible progress tick timestamp.
     */
    private long lastProgressSoundNanos;

    /**
     * Keeps the compact sound chip synchronized with global sound settings.
     */
    private final transient Runnable soundSettingsListener = this::syncSoundOutputToggle;

    /**
     * Creates the panel.
     */
    public MctsPanel() {
        super(new BorderLayout(Theme.SPACE_SM, Theme.SPACE_SM));
        setOpaque(false);
        add(Ui.collapsible("Controls", createControls(), true), BorderLayout.NORTH);
        add(createCenter(), BorderLayout.CENTER);
        syncSoundOutputToggle();
        soundOutputToggle.addActionListener(event -> SoundService.setMuted(!soundOutputToggle.isSelected()));
        SoundService.addSettingsListener(soundSettingsListener);
        showFen(Game.STANDARD_START_FEN);
        updateButtons(false);
    }

    /**
     * Receives the main-board FEN.
     *
     * @param fen FEN string
     */
    public void setFen(String fen) {
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
    public void dispose() {
        SoundService.removeSettingsListener(soundSettingsListener);
        stopSearch(false);
    }

    /**
     * Creates the controls surface.
     *
     * @return controls component
     */
    private JComponent createControls() {
        JPanel panel = new SurfacePanel(new GridBagLayout());
        GridBagConstraints c = Ui.constraints();
        Ui.grid(panel, Theme.section("MCTS"), c, 0, 0, 4, 1);
        Ui.styleFields(fenField);
        fenField.addActionListener(event -> loadFenFromField());
        Ui.grid(panel, fenField, c, 0, 1, 4, 1);
        Ui.styleIntegerSpinner(playoutSpinner);
        Ui.styleIntegerSpinner(millisSpinner);
        Ui.styleIntegerSpinner(cpuctSpinner);
        Ui.grid(panel, Ui.label("Visits"), c, 0, 2, 1, 1);
        Ui.grid(panel, playoutSpinner, c, 1, 2, 1, 1);
        Ui.grid(panel, Ui.label("Millis"), c, 2, 2, 1, 1);
        Ui.grid(panel, millisSpinner, c, 3, 2, 1, 1);
        Ui.grid(panel, Ui.label("Cpuct"), c, 0, 3, 1, 1);
        Ui.grid(panel, cpuctSpinner, c, 1, 3, 1, 1);
        Ui.grid(panel, buttonRow(), c, 0, 4, 4, 1);
        statusLabel.setForeground(Theme.MUTED);
        statusLabel.setFont(Theme.font(12, java.awt.Font.PLAIN));
        Ui.grid(panel, statusLabel, c, 0, 5, 4, 1);
        return panel;
    }

    /**
     * Creates the center split.
     *
     * @return center component
     */
    private JComponent createCenter() {
        JPanel boards = new SurfacePanel(new GridBagLayout());
        GridBagConstraints c = Ui.constraints();
        rootBoard.setPreferredSize(new Dimension(186, 186));
        leafBoard.setPreferredSize(new Dimension(186, 186));
        Ui.grid(boards, Theme.section("root"), c, 0, 0, 1, 1);
        Ui.grid(boards, rootBoard, c, 0, 1, 1, 1);
        Ui.grid(boards, Theme.section("leaf"), c, 0, 2, 1, 1);
        Ui.grid(boards, leafBoard, c, 0, 3, 1, 1);

        Theme.table(treeTable, 24);
        treeTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        JScrollPane treeScroll = Ui.scroll(treeTable);
        treeScroll.setBorder(BorderFactory.createEmptyBorder());

        Ui.styleAreas(pvArea);
        pvArea.setEditable(false);
        pvArea.setLineWrap(true);
        pvArea.setWrapStyleWord(true);

        JPanel right = new JPanel(new BorderLayout(Theme.SPACE_SM, Theme.SPACE_SM));
        right.setOpaque(false);
        right.add(Theme.section("root"), BorderLayout.NORTH);
        right.add(treeScroll, BorderLayout.CENTER);
        right.add(Ui.collapsible("Best line", Ui.titled("Best line", new JScrollPane(pvArea)), false),
                BorderLayout.SOUTH);

        JPanel content = new JPanel(new BorderLayout(Theme.SPACE_SM, 0));
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
        soundOutputToggle.setToolTipText("Enable restrained sound cues for moves, jobs, puzzles, and MCTS.");
        row.add(soundOutputToggle);
        row.add(Ui.button("Use board", false, event -> {
            fenField.setText(liveFen);
            showFen(liveFen);
        }));
        row.add(Ui.button("Load FEN", false, event -> loadFenFromField()));
        return row;
    }

    /**
     * Starts a new search.
     */
    private void startSearch() {
        stopSearch(false);
        Position root;
        try {
            root = new Position(fenField.getText().trim());
        } catch (IllegalArgumentException ex) {
            statusLabel.setText("invalid FEN: " + ex.getMessage());
            SoundService.play(SoundCue.ILLEGAL);
            return;
        }
        showFen(root.toString());
        int budget = ((Number) playoutSpinner.getValue()).intValue();
        long maxMillis = ((Number) millisSpinner.getValue()).longValue();
        double cpuct = ((Number) cpuctSpinner.getValue()).doubleValue();
        paused = false;
        search = new MctsSearch(root, cpuct);
        final MctsSearch activeSearch = search;
        lastProgressSoundNanos = 0L;
        updateButtons(true);
        SoundService.play(SoundCue.MCTS_START);
        SwingWorker<Void, MctsSearch.Snapshot> activeWorker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                publish(activeSearch.snapshot(false));
                long lastPublishNanos = System.nanoTime();
                while (!isCancelled() && activeSearch.shouldContinue(budget, maxMillis)) {
                    if (paused) {
                        publish(activeSearch.snapshot(true));
                        lastPublishNanos = System.nanoTime();
                        Thread.sleep(80L);
                        continue;
                    }
                    activeSearch.iterate();
                    long playouts = activeSearch.playouts();
                    long now = System.nanoTime();
                    if (shouldPublishLiveFrame(playouts, now, lastPublishNanos)) {
                        publish(activeSearch.snapshot(false));
                        lastPublishNanos = now;
                        if (playouts <= LIVE_WARMUP_PLAYOUTS) {
                            Thread.sleep(LIVE_WARMUP_DELAY_MS);
                        }
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
                boolean failed = false;
                try {
                    get();
                } catch (java.util.concurrent.CancellationException ex) {
                    // stopSearch already restored the controls and played the stop cue
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    failed = true;
                } catch (java.util.concurrent.ExecutionException ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    statusLabel.setText("failed: " + cause.getMessage());
                    failed = true;
                    SoundService.play(SoundCue.JOB_FAILURE);
                }
                if (!isCancelled() && !failed) {
                    showSnapshot(activeSearch.snapshot(paused));
                    SoundService.play(SoundCue.MCTS_COMPLETE);
                }
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
        SoundService.play(paused ? SoundCue.MCTS_PAUSE : SoundCue.MCTS_RESUME);
    }

    /**
     * Stops the active search.
     */
    private void stopSearch() {
        stopSearch(true);
    }

    /**
     * Stops the active search.
     *
     * @param audible true to play a user-facing stop cue
     */
    private void stopSearch(boolean audible) {
        MctsSearch activeSearch = search;
        boolean cancelled = worker != null && !worker.isDone();
        if (cancelled) {
            worker.cancel(true);
        }
        worker = null;
        search = null;
        paused = false;
        updateButtons(false);
        if (activeSearch != null) {
            activeSearch.close();
        }
        if (audible && cancelled) {
            SoundService.play(SoundCue.MCTS_STOP);
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
        String leaf = snapshot.exploringLineText();
        if (leaf == null || leaf.isBlank()) {
            leaf = "root";
        }
        String shortLeaf = Ui.elide(leaf, getFontMetrics(Theme.font(12, java.awt.Font.PLAIN)), 180);
        statusLabel.setText(String.format("%s · %,d visits · %,d ms · root %s · best %s · leaf %s",
                snapshot.paused() ? "paused" : isRunning() ? "running" : "done",
                snapshot.playouts(),
                snapshot.elapsedMillis(),
                snapshot.rootScoreLabel(),
                best,
                shortLeaf));
        if (isRunning() && !snapshot.paused()) {
            maybePlayProgressSound(snapshot);
        }
    }

    /**
     * Returns whether a live MCTS frame should be pushed to the EDT.
     *
     * @param playouts completed playouts
     * @param nowNanos current monotonic time
     * @param lastPublishNanos last published monotonic time
     * @return true when the UI should refresh
     */
    private static boolean shouldPublishLiveFrame(long playouts, long nowNanos, long lastPublishNanos) {
        return playouts <= LIVE_WARMUP_PLAYOUTS
                || playouts % PUBLISH_INTERVAL == 0L
                || nowNanos - lastPublishNanos >= MIN_PUBLISH_NANOS;
    }

    /**
     * Synchronizes the compact sound chip with global sound settings.
     */
    private void syncSoundOutputToggle() {
        soundOutputToggle.setSelected(!SoundService.isMuted());
    }

    /**
     * Plays an occasional soft MCTS progress tick.
     *
     * @param snapshot current search snapshot
     */
    private void maybePlayProgressSound(MctsSearch.Snapshot snapshot) {
        if (snapshot.playouts() < PUBLISH_INTERVAL) {
            return;
        }
        long now = System.nanoTime();
        if (lastProgressSoundNanos == 0L
                || now - lastProgressSoundNanos >= SOUND_INTERVAL_NANOS) {
            lastProgressSoundNanos = now;
            SoundService.play(SoundCue.MCTS_PROGRESS);
        }
    }
}
