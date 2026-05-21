package application.gui.workbench;

import static application.gui.workbench.WorkbenchUi.button;
import static application.gui.workbench.WorkbenchUi.buttonRow;
import static application.gui.workbench.WorkbenchUi.changeListener;
import static application.gui.workbench.WorkbenchUi.collapsible;
import static application.gui.workbench.WorkbenchUi.constraints;
import static application.gui.workbench.WorkbenchUi.flow;
import static application.gui.workbench.WorkbenchUi.fillViewport;
import static application.gui.workbench.WorkbenchUi.grid;
import static application.gui.workbench.WorkbenchUi.iconButton;
import static application.gui.workbench.WorkbenchUi.label;
import static application.gui.workbench.WorkbenchUi.placeholder;
import static application.gui.workbench.WorkbenchUi.scroll;
import static application.gui.workbench.WorkbenchUi.showConfirmDialog;
import static application.gui.workbench.WorkbenchUi.styleAreas;
import static application.gui.workbench.WorkbenchUi.styleCombos;
import static application.gui.workbench.WorkbenchUi.styleFields;
import static application.gui.workbench.WorkbenchUi.styleSpinners;
import static application.gui.workbench.WorkbenchUi.tabbedPane;
import static application.gui.workbench.WorkbenchUi.titled;
import static application.gui.workbench.WorkbenchUi.transparentPanel;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.print.PrinterException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ButtonGroup;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.JTextComponent;

import application.Config;
import application.gui.workbench.WorkbenchCommandTemplates.BatchTask;
import application.gui.workbench.WorkbenchCommandTemplates.CommandTemplate;
import application.gui.workbench.WorkbenchCommandTemplates.TemplateContext;
import application.gui.workbench.WorkbenchCommandTemplates.WorkflowControls;
import application.gui.workbench.WorkbenchCommandPalette.PaletteAction;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Position;
import chess.core.SAN;
import chess.core.Setup;
import chess.struct.Game;
import chess.struct.Pgn;
import chess.tag.Generator;
import chess.uci.Analysis;
import chess.uci.Engine;
import chess.uci.Evaluation;
import chess.uci.Output;
import chess.uci.Protocol;

/**
 * A new native Swing command and analysis workbench for ChessRTK.
 */
@SuppressWarnings("java:S6539")
public final class WorkbenchWindow extends JFrame {

    /**
     * Serialization identifier for Swing frame compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * UCI move pattern.
     */
    private static final Pattern UCI_PATTERN = Pattern.compile("\\b[a-h][1-8][a-h][1-8][qrbn]?\\b");

    /**
     * Short engine duration for automatic eval-bar refresh.
     */
    private static final String EVAL_BAR_DURATION = "350ms";

    /**
     * Dashboard tab index — the default, first tab.
     */
    private static final int TAB_DASHBOARD = 0;

    /**
     * Analysis tab index.
     */
    private static final int TAB_ANALYZE = 1;

    /**
     * Commands tab index.
     */
    private static final int TAB_COMMANDS = 2;

    /**
     * Batch tab index.
     */
    private static final int TAB_BATCH = 3;

    /**
     * Publishing tab index.
     */
    private static final int TAB_PUBLISH = 4;

    /**
     * Console tab index.
     */
    private static final int TAB_CONSOLE = 5;

    /**
     * Network visualizer tab index.
     */
    private static final int TAB_NETWORK = 6;

    /**
     * Placeholder path used in command previews for generated FEN files.
     */
    private static final String WORKBENCH_FENS_PLACEHOLDER = "<workbench-fens.txt>";

    /**
     * Cached preview-only path used when building batch commands without
     * materializing the FENs file. Constructed once because parsing the
     * angle-bracket placeholder on every keystroke is wasteful.
     */
    private static final Path WORKBENCH_FENS_PLACEHOLDER_PATH = Path.of(WORKBENCH_FENS_PLACEHOLDER);

    /**
     * Board view.
     */
    private final WorkbenchBoardPanel board = new WorkbenchBoardPanel();

    /**
     * Engine evaluation bar.
     */
    private final WorkbenchEvalBar evalBar = new WorkbenchEvalBar();

    /**
     * Live analysis data graph.
     */
    private final WorkbenchAnalysisGraph analysisGraph = new WorkbenchAnalysisGraph();

    /**
     * Network-architecture visualizer (NNUE / lc0-CNN / lc0-BT4).
     */
    private final WorkbenchNetworkPanel networkPanel = new WorkbenchNetworkPanel();

    /**
     * Leela-style PUCT search visualizer.
     */
    private final WorkbenchMctsPanel mctsPanel = new WorkbenchMctsPanel();

    /**
     * Shared, observable session model the Dashboard tab renders from.
     */
    private final WorkbenchSession session = new WorkbenchSession();

    /**
     * Operational overview tab, rendered from {@link #session}.
     */
    private final WorkbenchDashboardPanel dashboardPanel =
            new WorkbenchDashboardPanel(session, new DashboardActions());

    /**
     * Job record for the foreground command currently tracked, or null.
     */
    private WorkbenchJob runningJob;

    /**
     * Wall-clock start time of {@link #runningJob}, in epoch millis.
     */
    private long runningJobStartMillis;

    /**
     * Queue of command argument lists for a "run all health checks" sequence;
     * drained one at a time as each command finishes.
     */
    private final java.util.Deque<List<String>> healthCheckQueue = new java.util.ArrayDeque<>();

    /**
     * Routes {@link WorkbenchDashboardPanel} quick actions to the window's
     * existing private actions, so the dashboard never needs those methods
     * widened to package or public visibility.
     */
    private final class DashboardActions implements WorkbenchDashboardActions {

        @Override
        public void builtInSearch() {
            runBuiltInSearch();
        }

        @Override
        public void bestMove() {
            runBestMove();
        }

        @Override
        public void analyze() {
            runAnalyze();
        }

        @Override
        public void tags() {
            runTagsCommand();
        }

        @Override
        public void perft() {
            runPerft();
        }

        @Override
        public void runBatch() {
            WorkbenchWindow.this.runBatch();
        }

        @Override
        public void engineSmoke() {
            runEngineSmoke();
        }

        @Override
        public void configValidate() {
            runConfigValidate();
        }

        @Override
        public void doctor() {
            runDoctor();
        }

        @Override
        public void runAllHealthChecks() {
            WorkbenchWindow.this.runAllHealthChecks();
        }

        @Override
        public void copyCurrentFen() {
            copyText(currentFen());
        }

        @Override
        public void openAnalyzeTab() {
            selectTab(TAB_ANALYZE);
        }

        @Override
        public void openBatchTab() {
            selectTab(TAB_BATCH);
        }

        @Override
        public void openConsoleTab() {
            selectTab(TAB_CONSOLE);
        }

        @Override
        public void retryJob(WorkbenchJob job) {
            if (job != null) {
                runCommand(job.args(), null);
            }
        }

        @Override
        public void copyJobCommand(WorkbenchJob job) {
            if (job != null) {
                copyText(WorkbenchCommandRunner.displayCommand(job.args()));
            }
        }

        @Override
        public void openJobManifest(WorkbenchJob job) {
            if (job == null) {
                return;
            }
            openManifestForJob(job);
        }

        @Override
        public void openJobLog(WorkbenchJob job) {
            if (job == null) {
                return;
            }
            openLogForJob(job);
        }
    }

    /**
     * Host callbacks for the extracted report panel.
     */
    private final class ReportHost implements WorkbenchReportPanel.Host {

        @Override
        public Component owner() {
            return WorkbenchWindow.this;
        }

        @Override
        public Position currentPosition() {
            return currentPosition;
        }

        @Override
        public short[] visibleMoves() {
            return visibleMoves;
        }

        @Override
        public WorkbenchGameModel gameModel() {
            return gameModel;
        }

        @Override
        public DefaultListModel<String> tagModel() {
            return tagModel;
        }

        @Override
        public void copyText(String text) {
            WorkbenchWindow.this.copyText(text);
        }

        @Override
        public void appendConsole(String text) {
            WorkbenchWindow.this.appendConsole(text);
        }

        @Override
        public void toast(WorkbenchToast.Kind kind, String message) {
            WorkbenchWindow.this.toast(kind, message);
        }

        @Override
        public void showError(String title, String message) {
            WorkbenchWindow.this.showError(title, message);
        }
    }

    /**
     * Host callbacks for the extracted publishing panel.
     */
    private final class PublishingHost implements WorkbenchPublishingPanel.Host {

        @Override
        public Component owner() {
            return WorkbenchWindow.this;
        }

        @Override
        public String currentFen() {
            return WorkbenchWindow.this.currentFen();
        }

        @Override
        public WorkbenchGameModel gameModel() {
            return gameModel;
        }

        @Override
        public String batchInputText() {
            return batchInput.getText();
        }

        @Override
        public JComponent reportPanel() {
            return reportPanel.component();
        }

        @Override
        public void generateReport() {
            WorkbenchWindow.this.generateReport();
        }

        @Override
        public void runCommand(List<String> args, String stdin) {
            WorkbenchWindow.this.runCommand(args, stdin);
        }

        @Override
        public void copyText(String text) {
            WorkbenchWindow.this.copyText(text);
        }

        @Override
        public void stopCommand() {
            WorkbenchWindow.this.stopCommand();
        }

        @Override
        public void toast(WorkbenchToast.Kind kind, String message) {
            WorkbenchWindow.this.toast(kind, message);
        }

        @Override
        public void showError(String title, String message) {
            WorkbenchWindow.this.showError(title, message);
        }
    }

    /**
     * FEN input.
     */
    private final JTextField fenField = new JTextField();

    /**
     * Status label.
     */
    private final JLabel statusLabel = new JLabel();

    /**
     * Move table model.
     */
    private final WorkbenchMovesModel movesModel = new WorkbenchMovesModel();

    /**
     * Move table.
     */
    private final JTable movesTable = new JTable(movesModel);

    /**
     * Tag model.
     */
    private final DefaultListModel<String> tagModel = new DefaultListModel<>();

    /**
     * Tag list.
     */
    private final JList<String> tagList = new JList<>(tagModel);

    /**
     * Position report panel used by the publishing tab and command palette.
     */
    private final WorkbenchReportPanel reportPanel = new WorkbenchReportPanel(new ReportHost());

    /**
     * Console output with carriage-return handling and line highlighting.
     */
    private final WorkbenchConsole console = new WorkbenchConsole();

    /**
     * Command execution state label.
     */
    private final JLabel commandStateLabel = new JLabel("Idle");

    /**
     * Command text field.
     */
    private final JTextField commandField = new JTextField();

    /**
     * Wrapping bar of command-selector toggle buttons.
     */
    private final JPanel commandPicker = transparentPanel(
            new FlowLayout(FlowLayout.LEFT, 6, 6));

    /**
     * Run button for the command builder, gated on command validity.
     */
    private JButton runCommandButton;

    /**
     * Command-selector toggle buttons, one per template.
     */
    private final transient List<javax.swing.JToggleButton> commandButtons = new ArrayList<>();

    /**
     * Index of the selected command template.
     */
    private int selectedCommandIndex;

    /**
     * Command templates shown in the {@link #commandPicker}.
     */
    private List<CommandTemplate> commandTemplates = List.of();

    /**
     * Structured command-builder form: required inputs, exclusive radio
     * groups, and filtered optional flags.
     */
    private final WorkbenchCommandForm commandForm = new WorkbenchCommandForm();

    /**
     * Main-line move history model.
     */
    private final WorkbenchGameModel gameModel = new WorkbenchGameModel();

    /**
     * Main-line move history table.
     */
    private final JTable gameTable = new JTable(gameModel);

    /**
     * Game line import text area.
     */
    private final JTextArea gameInput = new JTextArea();

    /**
     * Game line state label.
     */
    private final JLabel gameStateLabel = new JLabel();

    /**
     * Duration field.
     */
    private final JTextField analysisDurationField = new JTextField("2s");

    /**
     * Batch duration field.
     */
    private final JTextField batchDurationField = new JTextField("2s");

    /**
     * Batch options panel.
     */
    private final JPanel batchOptionsPanel = transparentPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));

    /**
     * Shared depth model.
     */
    private final SpinnerNumberModel depthModel = new SpinnerNumberModel(4, 1, 99, 1);

    /**
     * Analysis depth control.
     */
    private final JSpinner analysisDepthSpinner = new JSpinner(depthModel);

    /**
     * Batch depth control.
     */
    private final JSpinner batchDepthSpinner = new JSpinner(depthModel);

    /**
     * Shared MultiPV model.
     */
    private final SpinnerNumberModel multipvModel = new SpinnerNumberModel(3, 1, 20, 1);

    /**
     * Analysis MultiPV control.
     */
    private final JSpinner analysisMultipvSpinner = new JSpinner(multipvModel);

    /**
     * Batch MultiPV control.
     */
    private final JSpinner batchMultipvSpinner = new JSpinner(multipvModel);

    /**
     * Shared thread-count model.
     */
    private final SpinnerNumberModel threadsModel = new SpinnerNumberModel(1, 1, 256, 1);

    /**
     * Analysis thread control.
     */
    private final JSpinner analysisThreadsSpinner = new JSpinner(threadsModel);

    /**
     * Batch thread control.
     */
    private final JSpinner batchThreadsSpinner = new JSpinner(threadsModel);

    /**
     * External engine protocol TOML path.
     */
    private final JTextField engineProtocolField = new JTextField(Config.getProtocolPath());

    /**
     * Optional external engine node budget.
     */
    private final JTextField engineNodesField = new JTextField();

    /**
     * Optional external engine hash size.
     */
    private final JTextField engineHashField = new JTextField();

    /**
     * Live external-engine analysis toggle for the board.
     */
    private final JCheckBox liveEngineToggle = withTooltip(new WorkbenchToggleBox("Live external engine"),
            "Continuously analyze the current board with the configured external UCI engine");

    /**
     * Returns the toggle after attaching a tooltip.
     *
     * @param toggle target toggle
     * @param tooltip tooltip text
     * @return the same toggle for fluent field initialization
     */
    private static <T extends JCheckBox> T withTooltip(T toggle, String tooltip) {
        toggle.setToolTipText(tooltip);
        return toggle;
    }

    /**
     * Batch input area.
     */
    private final JTextArea batchInput = new JTextArea();

    /**
     * Batch input status summary.
     */
    private final JLabel batchInputStatus = new JLabel();

    /**
     * Batch command field.
     */
    private final JTextArea batchCommandField = new JTextArea(3, 36);

    /**
     * Batch task selector.
     */
    private final JComboBox<BatchTask> batchTaskCombo = new JComboBox<>();

    /**
     * Publishing workflow panel.
     */
    private final WorkbenchPublishingPanel publishingPanel = new WorkbenchPublishingPanel(new PublishingHost());

    /**
     * Button that appends the current FEN to the batch input.
     */
    private JButton addCurrentFenButton;

    /**
     * Button that clears the batch FEN input.
     */
    private JButton clearBatchInputButton;

    /**
     * Current position.
     */
    private Position currentPosition;

    /**
     * Legal moves currently shown.
     */
    private short[] visibleMoves = new short[0];

    /**
     * Whether selected-piece legal destinations and drag targets are shown.
     */
    private boolean showLegalMovePreview = true;

    /**
     * Whether the previous move is highlighted.
     */
    private boolean showLastMoveHighlight = true;

    /**
     * Whether engine suggestions are shown as board arrows.
     */
    private boolean showBestMoveArrows = true;

    /**
     * Whether board coordinates are shown.
     */
    private boolean showBoardCoordinates = true;

    /**
     * Whether board transitions are animated.
     */
    private boolean boardAnimationsEnabled = true;

    /**
     * Whether the eval bar automatically runs short engine analysis.
     */
    private boolean autoEvalBarEnabled = true;

    /**
     * Whether the board keeps an external UCI engine running for live analysis.
     */
    private boolean liveExternalEngineEnabled;

    /**
     * Running command.
     */
    private WorkbenchCommandRunner.RunningCommand runningCommand;

    /**
     * Running eval-bar engine command.
     */
    private WorkbenchCommandRunner.RunningCommand evalCommand;

    /**
     * Background worker that owns the live external-engine process.
     */
    private LiveEngineWorker liveEngineWorker;

    /**
     * Lock guarding live-analysis request handoff to the worker.
     */
    private final Object liveAnalysisLock = new Object();

    /**
     * Latest live-analysis request consumed by the worker.
     */
    private LiveAnalysisRequest liveAnalysisRequest;

    /**
     * Monotonic eval-bar request id.
     */
    private long evalRequestId;

    /**
     * Monotonic live-analysis request id.
     */
    private long liveAnalysisRequestId;

    /**
     * Whether a live-analysis failure has already been surfaced for the current
     * enabled session.
     */
    private boolean liveAnalysisFailureLogged;

    /**
     * Debounce delay for eval-bar refresh, in ms.
     */
    private static final int EVAL_DEBOUNCE_MS = 90;

    /**
     * Max live-analysis idle time before the engine is considered stalled.
     */
    private static final long LIVE_ANALYSIS_STALL_TIMEOUT_MS = 10_000L;

    /**
     * Minimum interval between live-analysis UI updates.
     */
    private static final long LIVE_ANALYSIS_UPDATE_INTERVAL_MS = 80L;

    /**
     * Debounce timer that coalesces eval refresh requests.
     */
    private final Timer evalDebounceTimer = createEvalDebounceTimer();

    /**
     * Whether a command-preview refresh is already queued on the event thread.
     */
    private boolean commandPreviewUpdateQueued;

    /**
     * Whether a batch-input status refresh is already queued on the event thread.
     */
    private boolean batchInputStatusUpdateQueued;

    /**
     * Creates the eval-bar debounce timer.
     *
     * @return single-shot debounce timer
     */
    private Timer createEvalDebounceTimer() {
        Timer timer = new Timer(EVAL_DEBOUNCE_MS, event -> startEvalCommand());
        timer.setRepeats(false);
        return timer;
    }

    /**
     * Main workbench split area: VS Code-style tabs with an optional
     * side-by-side split.
     */
    private WorkbenchSplitArea tabs;

    /**
     * Nested analysis tabs.
     */
    private JTabbedPane analysisTabs;

    /**
     * Board detail tabs on the Analyze/Board side panel.
     */
    private JTabbedPane boardDetailTabs;

    /**
     * Lazy command palette.
     */
    private WorkbenchCommandPalette commandPalette;

    /**
     * Background tag worker.
     */
    private SwingWorker<List<String>, Void> tagWorker;

    /**
     * Guards against recursive duration-field synchronization.
     */
    private boolean syncingDuration;

    /**
     * Creates and shows the workbench.
     *
     * @param initialFen initial FEN
     * @param whiteDown true when White is at the bottom
     */
    public WorkbenchWindow(String initialFen, boolean whiteDown) {
        super("ChessRTK Workbench");
        WorkbenchTheme.install();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(1180, 760));
        restoreWindowGeometry();
        loadDisplaySettings();
        loadEngineSettings();
        installFieldPlaceholders();
        installEngineSettingListeners();
        buildUi();
        applyDisplaySettings(false);
        board.setWhiteDown(whiteDown);
        startNewGame(initialFen);
        generateReport();
        setVisible(true);
    }

    /**
     * Preferences node used to persist workbench UI state.
     */
    private static final Preferences WORKBENCH_PREFS =
            Preferences.userNodeForPackage(WorkbenchWindow.class);

    private static final String PREF_SHOW_LEGAL_MOVES = "display.showLegalMovePreview";
    private static final String PREF_SHOW_LAST_MOVE = "display.showLastMoveHighlight";
    private static final String PREF_SHOW_BEST_ARROWS = "display.showBestMoveArrows";
    private static final String PREF_SHOW_COORDINATES = "display.showCoordinates";
    private static final String PREF_BOARD_ANIMATIONS = "display.boardAnimations";
    private static final String PREF_AUTO_EVAL_BAR = "display.autoEvalBar";
    private static final String PREF_LIVE_EXTERNAL_ENGINE = "engine.liveExternal";
    private static final String PREF_ENGINE_PROTOCOL = "engine.protocolPath";
    private static final String PREF_ENGINE_NODES = "engine.maxNodes";
    private static final String PREF_ENGINE_HASH = "engine.hash";

    /**
     * Default window width when no preference is recorded.
     */
    private static final int DEFAULT_WINDOW_WIDTH = 1440;

    /**
     * Default window height when no preference is recorded.
     */
    private static final int DEFAULT_WINDOW_HEIGHT = 920;

    /**
     * Restores window size and position from {@link #WORKBENCH_PREFS}, with a
     * fallback to the historical defaults when no preference exists or the
     * stored bounds are off-screen.
     */
    private void restoreWindowGeometry() {
        int width = WORKBENCH_PREFS.getInt("window.width", DEFAULT_WINDOW_WIDTH);
        int height = WORKBENCH_PREFS.getInt("window.height", DEFAULT_WINDOW_HEIGHT);
        int x = WORKBENCH_PREFS.getInt("window.x", Integer.MIN_VALUE);
        int y = WORKBENCH_PREFS.getInt("window.y", Integer.MIN_VALUE);
        boolean maximized = WORKBENCH_PREFS.getBoolean("window.maximized", false);
        setSize(Math.max(1180, width), Math.max(760, height));
        if (x == Integer.MIN_VALUE || y == Integer.MIN_VALUE || !isOnAnyScreen(x, y, width, height)) {
            setLocationRelativeTo(null);
        } else {
            setLocation(x, y);
        }
        if (maximized) {
            setExtendedState(getExtendedState() | java.awt.Frame.MAXIMIZED_BOTH);
        }
    }

    /**
     * Returns whether the supplied bounds intersect any visible screen.
     *
     * @param x window x
     * @param y window y
     * @param width window width
     * @param height window height
     * @return true when at least one virtual screen contains the rectangle
     */
    private static boolean isOnAnyScreen(int x, int y, int width, int height) {
        try {
            java.awt.Rectangle bounds = new java.awt.Rectangle(x, y, Math.max(1, width), Math.max(1, height));
            for (java.awt.GraphicsDevice device :
                    java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
                if (device.getDefaultConfiguration().getBounds().intersects(bounds)) {
                    return true;
                }
            }
        } catch (java.awt.HeadlessException ignored) {
            return false;
        }
        return false;
    }

    /**
     * Persists current window size and position so the next launch restores
     * them.
     */
    private void saveWindowGeometry() {
        boolean maximized = (getExtendedState() & java.awt.Frame.MAXIMIZED_BOTH) == java.awt.Frame.MAXIMIZED_BOTH;
        WORKBENCH_PREFS.putBoolean("window.maximized", maximized);
        if (maximized) {
            return;
        }
        WORKBENCH_PREFS.putInt("window.width", getWidth());
        WORKBENCH_PREFS.putInt("window.height", getHeight());
        WORKBENCH_PREFS.putInt("window.x", getX());
        WORKBENCH_PREFS.putInt("window.y", getY());
    }

    /**
     * Loads persisted display settings.
     */
    private void loadDisplaySettings() {
        showLegalMovePreview = WORKBENCH_PREFS.getBoolean(PREF_SHOW_LEGAL_MOVES, true);
        showLastMoveHighlight = WORKBENCH_PREFS.getBoolean(PREF_SHOW_LAST_MOVE, true);
        showBestMoveArrows = WORKBENCH_PREFS.getBoolean(PREF_SHOW_BEST_ARROWS, true);
        showBoardCoordinates = WORKBENCH_PREFS.getBoolean(PREF_SHOW_COORDINATES, true);
        boardAnimationsEnabled = WORKBENCH_PREFS.getBoolean(PREF_BOARD_ANIMATIONS, true);
        autoEvalBarEnabled = WORKBENCH_PREFS.getBoolean(PREF_AUTO_EVAL_BAR, true);
        liveExternalEngineEnabled = WORKBENCH_PREFS.getBoolean(PREF_LIVE_EXTERNAL_ENGINE, false);
    }

    /**
     * Persists display settings.
     */
    private void saveDisplaySettings() {
        WORKBENCH_PREFS.putBoolean(PREF_SHOW_LEGAL_MOVES, showLegalMovePreview);
        WORKBENCH_PREFS.putBoolean(PREF_SHOW_LAST_MOVE, showLastMoveHighlight);
        WORKBENCH_PREFS.putBoolean(PREF_SHOW_BEST_ARROWS, showBestMoveArrows);
        WORKBENCH_PREFS.putBoolean(PREF_SHOW_COORDINATES, showBoardCoordinates);
        WORKBENCH_PREFS.putBoolean(PREF_BOARD_ANIMATIONS, boardAnimationsEnabled);
        WORKBENCH_PREFS.putBoolean(PREF_AUTO_EVAL_BAR, autoEvalBarEnabled);
        WORKBENCH_PREFS.putBoolean(PREF_LIVE_EXTERNAL_ENGINE, liveExternalEngineEnabled);
    }

    /**
     * Loads persisted external-engine settings.
     */
    private void loadEngineSettings() {
        engineProtocolField.setText(WORKBENCH_PREFS.get(PREF_ENGINE_PROTOCOL, Config.getProtocolPath()));
        engineNodesField.setText(WORKBENCH_PREFS.get(PREF_ENGINE_NODES, ""));
        engineHashField.setText(WORKBENCH_PREFS.get(PREF_ENGINE_HASH, ""));
    }

    /**
     * Adds empty-field examples for the workbench's editable text inputs.
     */
    private void installFieldPlaceholders() {
        placeholder(fenField, "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        placeholder(analysisDurationField, "e.g. 2s or 500ms");
        placeholder(batchDurationField, "e.g. 2s or 500ms");
        placeholder(engineProtocolField, "path/to/engine.toml");
        placeholder(engineNodesField, "e.g. 1000000");
        placeholder(engineHashField, "e.g. 128");
        placeholder(batchInput, "one FEN per line; blank uses the current game line");
    }

    /**
     * Installs change listeners for external-engine fields.
     */
    private void installEngineSettingListeners() {
        engineProtocolField.getDocument().addDocumentListener(changeListener(this::engineSettingsChanged));
        engineNodesField.getDocument().addDocumentListener(changeListener(this::engineSettingsChanged));
        engineHashField.getDocument().addDocumentListener(changeListener(this::engineSettingsChanged));
    }

    /**
     * Persists external-engine settings.
     */
    private void saveEngineSettings() {
        putPreference(PREF_ENGINE_PROTOCOL, trimmed(engineProtocolField));
        putPreference(PREF_ENGINE_NODES, trimmed(engineNodesField));
        putPreference(PREF_ENGINE_HASH, trimmed(engineHashField));
    }

    /**
     * Stores or clears a string preference.
     *
     * @param key preference key
     * @param value preference value
     */
    private static void putPreference(String key, String value) {
        if (value == null || value.isBlank()) {
            WORKBENCH_PREFS.remove(key);
        } else {
            WORKBENCH_PREFS.put(key, value);
        }
    }

    /**
     * Handles a changed external-engine setting.
     */
    private void engineSettingsChanged() {
        saveEngineSettings();
        requestCommandPreviews();
        if (liveExternalEngineEnabled) {
            restartLiveAnalysis();
        } else {
            requestEvalUpdate();
        }
    }

    /**
     * Applies current display settings to the board and auxiliary analysis UI.
     *
     * @param refreshEval true to restart eval behavior immediately
     */
    private void applyDisplaySettings(boolean refreshEval) {
        board.setShowLegalMovePreview(showLegalMovePreview);
        board.setShowLastMoveHighlight(showLastMoveHighlight);
        board.setShowSuggestedMoveArrow(showBestMoveArrows);
        board.setShowNotation(showBoardCoordinates);
        board.setAnimationsEnabled(boardAnimationsEnabled);
        liveEngineToggle.setSelected(liveExternalEngineEnabled);
        if (liveExternalEngineEnabled) {
            evalDebounceTimer.stop();
            cancelEvalCommand();
            if (!isAnalyzePaneVisible()) {
                pauseHiddenLiveAnalysis();
            } else if (refreshEval) {
                requestLiveAnalysisUpdate();
            }
            return;
        }
        if (!autoEvalBarEnabled) {
            evalDebounceTimer.stop();
            cancelEvalCommand();
            stopLiveAnalysis();
            evalBar.setUnavailable("off");
        } else if (refreshEval) {
            requestEvalUpdate();
        }
    }

    /**
     * Cancels background work and persists window geometry before disposing.
     */
    @Override
    public void dispose() {
        saveWindowGeometry();
        evalRequestId++;
        cancelEvalCommand();
        stopLiveAnalysis();
        evalDebounceTimer.stop();
        if (runningCommand != null && runningCommand.isRunning()) {
            runningCommand.cancel();
        }
        if (tagWorker != null && !tagWorker.isDone()) {
            tagWorker.cancel(true);
        }
        networkPanel.dispose();
        mctsPanel.dispose();
        super.dispose();
    }

    /**
     * Builds the UI.
     */
    private void buildUi() {
        JPanel root = new WorkbenchBackdropPanel();
        root.setLayout(new BorderLayout(10, 10));
        root.setBackground(WorkbenchTheme.BG);
        root.setBorder(WorkbenchTheme.pad(12, 12, 12, 12));

        tabs = new WorkbenchSplitArea();
        tabs.addPanel("Dashboard", dashboardPanel);
        tabs.addPanel("Analyze", createBoardTab());
        tabs.addPanel("Commands", createCommandTab());
        tabs.addPanel("Batch", createBatchTab());
        tabs.addPanel("Publish", createPublishTab());
        tabs.addPanel("Console", createConsolePanel());
        tabs.addPanel("Network", networkPanel);
        tabs.install();
        tabs.setSelectionListener(index -> onWorkbenchTabVisibilityChanged());
        tabs.select(TAB_DASHBOARD);

        root.add(tabs, BorderLayout.CENTER);
        root.add(createStatusBar(), BorderLayout.SOUTH);
        setContentPane(root);
        installKeyBindings();
    }

    /**
     * Status-bar position label (FEN summary).
     */
    private final JLabel statusBarPosition = new JLabel("");

    /**
     * Status-bar move-counter label.
     */
    private final JLabel statusBarPly = new JLabel("");

    /**
     * Status-bar engine-state label.
     */
    private final JLabel statusBarEngine = new JLabel("idle");

    /**
     * Builds a compact status bar pinned to the south edge of the workbench.
     *
     * @return status bar component
     */
    private JComponent createStatusBar() {
        JPanel bar = WorkbenchUi.transparentPanel(new java.awt.GridBagLayout());
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, WorkbenchTheme.LINE),
                WorkbenchTheme.pad(6, 14, 6, 14)));
        java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
        c.fill = java.awt.GridBagConstraints.HORIZONTAL;
        c.gridy = 0;
        c.weighty = 0.0;

        c.gridx = 0;
        c.weightx = 0.5;
        bar.add(statusBarPosition, c);

        c.gridx = 1;
        c.weightx = 0.3;
        bar.add(statusBarPly, c);

        c.gridx = 2;
        c.weightx = 0.2;
        bar.add(statusBarEngine, c);

        for (JLabel label : new JLabel[] { statusBarPosition, statusBarPly, statusBarEngine }) {
            label.setFont(WorkbenchTheme.font(11, java.awt.Font.PLAIN));
            label.setForeground(WorkbenchTheme.MUTED);
        }
        statusBarPosition.setHorizontalAlignment(SwingConstants.LEFT);
        statusBarPly.setHorizontalAlignment(SwingConstants.CENTER);
        statusBarEngine.setHorizontalAlignment(SwingConstants.RIGHT);
        return bar;
    }

    /**
     * Truncation budget for the inline FEN preview in the status bar; the
     * full FEN is exposed via the label tooltip.
     */
    private static final int STATUS_FEN_BUDGET = 60;

    /**
     * Updates the status bar from the current model state.
     */
    private void refreshStatusBar() {
        if (currentPosition == null) {
            statusBarPosition.setText("");
            statusBarPosition.setToolTipText(null);
            statusBarPly.setText("");
            return;
        }
        String fen = currentPosition.toString();
        statusBarPosition.setText(fen.length() > STATUS_FEN_BUDGET
                ? fen.substring(0, STATUS_FEN_BUDGET - 3) + "…"
                : fen);
        statusBarPosition.setToolTipText(fen);
        int ply = gameModel.currentPly();
        int last = gameModel.lastPly();
        statusBarPly.setText("ply " + ply + " / " + last + "  ·  "
                + (currentPosition.isWhiteToMove() ? "white to move" : "black to move"));
    }

    /**
     * Updates the engine status label.
     *
     * @param text status text
     */
    private void setStatusBarEngine(String text) {
        statusBarEngine.setText(text == null ? "" : text);
    }

    /**
     * Shows a transient toast notification.
     *
     * @param kind severity
     * @param message message text
     */
    private void toast(WorkbenchToast.Kind kind, String message) {
        WorkbenchToast.show(this, kind, message);
    }

    /**
     * Installs global workbench key bindings.
     */
    private void installKeyBindings() {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("control K"), "openCommandPalette");
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("meta K"), "openCommandPalette");
        getRootPane().getActionMap().put("openCommandPalette", new AbstractAction() {
            /**
             * Serialization identifier for Swing action compatibility.
             */
            private static final long serialVersionUID = 1L;

            /**
             * Opens the command palette.
             *
             * @param event action event
             */
            @Override
            public void actionPerformed(ActionEvent event) {
                showCommandPalette();
            }
        });
        bindWindowAction("alt LEFT", "navigateBack", event -> navigateGame(-1));
        bindWindowAction("alt RIGHT", "navigateForward", event -> navigateGame(1));
        bindWindowAction("alt UP", "navigateStart", event -> jumpGameTo(0));
        bindWindowAction("alt DOWN", "navigateEnd", event -> jumpGameTo(gameModel.lastPly()));
        bindWindowAction("control TAB", "nextWorkbenchTab", event -> tabs.selectNextTab());
        bindWindowAction("control shift TAB", "previousWorkbenchTab", event -> tabs.selectPreviousTab());
        bindWindowAction("control PAGE_DOWN", "nextWorkbenchTabPage", event -> tabs.selectNextTab());
        bindWindowAction("control PAGE_UP", "previousWorkbenchTabPage", event -> tabs.selectPreviousTab());
        bindWindowAction("ESCAPE", "stopRunningCommand", event -> stopCommand());
        bindPositionNavigation("LEFT", "previousPosition", event -> navigateGame(-1));
        bindPositionNavigation("RIGHT", "nextPosition", event -> navigateGame(1));
        bindPositionNavigation("UP", "firstPosition", event -> jumpGameTo(0));
        bindPositionNavigation("DOWN", "lastPosition", event -> jumpGameTo(gameModel.lastPly()));
    }

    /**
     * Binds a window-level arrow key to game-position navigation when the board
     * view is active and focus is not in a component that owns arrow keys.
     *
     * @param keyStroke keystroke
     * @param name action name
     * @param body action body
     */
    private void bindPositionNavigation(String keyStroke, String name,
            Consumer<ActionEvent> body) {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(keyStroke), name);
        getRootPane().getActionMap().put(name, new AbstractAction() {
            private static final long serialVersionUID = 1L;
            @Override
            public void actionPerformed(ActionEvent event) {
                if (shouldRoutePositionNavigation()) {
                    body.accept(event);
                }
            }
        });
    }

    /**
     * Returns whether an unmodified arrow key should navigate game positions.
     *
     * @return true when routing to position navigation is appropriate
     */
    private boolean shouldRoutePositionNavigation() {
        if (tabs == null || tabs.selectedIndex() != TAB_ANALYZE) {
            return false;
        }
        if (analysisTabs != null && analysisTabs.getSelectedIndex() != 0) {
            return false;
        }
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        return shouldRoutePositionNavigation(focusOwner);
    }

    /**
     * Returns whether arrow-key focus should be routed to position navigation.
     *
     * @param focusOwner current focus owner
     * @return true when arrow keys should move through game positions
     */
    private static boolean shouldRoutePositionNavigation(Component focusOwner) {
        if (focusOwner == null) {
            return true;
        }
        return !(focusOwner instanceof JTextComponent
                || focusOwner instanceof JTable
                || focusOwner instanceof JList<?>
                || focusOwner instanceof JComboBox<?>
                || focusOwner instanceof JSpinner);
    }

    /**
     * Binds a window-level keyboard action.
     *
     * @param keyStroke keystroke
     * @param name action name
     * @param body action body
     */
    private void bindWindowAction(String keyStroke, String name,
            Consumer<ActionEvent> body) {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(keyStroke), name);
        getRootPane().getActionMap().put(name, new AbstractAction() {
            private static final long serialVersionUID = 1L;
            @Override
            public void actionPerformed(ActionEvent event) {
                body.accept(event);
            }
        });
    }

    /**
     * Opens the searchable action palette.
     */
    private void showCommandPalette() {
        if (commandPalette == null) {
            commandPalette = new WorkbenchCommandPalette(this);
        }
        commandPalette.showActions(commandPaletteActions());
    }

    /**
     * Builds the current command-palette action list.
     *
     * @return palette actions
     */
    private List<PaletteAction> commandPaletteActions() {
        return List.of(
                new PaletteAction("Analyze board", "Run built-in search on the current FEN", this::runBuiltInSearch),
                new PaletteAction("Best move", "Run engine bestmove with the current duration", this::runBestMove),
                new PaletteAction("Engine analysis", "Run multipv analysis for the current position", this::runAnalyze),
                new PaletteAction("Live external engine", "Toggle continuous UCI analysis for the board",
                        () -> setLiveExternalEngineEnabled(!liveExternalEngineEnabled)),
                new PaletteAction("Analysis data", "Show live evaluation, depth, and speed graphs",
                        this::showAnalysisData),
                new PaletteAction("Copy analysis CSV", "Copy live analysis graph data as CSV",
                        this::copyAnalysisCsv),
                new PaletteAction("Copy analysis report", "Copy the live analysis summary report",
                        this::copyAnalysisReport),
                new PaletteAction("Print analysis report", "Print the live analysis graph report",
                        this::printAnalysisReport),
                new PaletteAction("Position tags", "Generate tags for the current FEN", this::runTagsCommand),
                new PaletteAction("Perft", "Run perft with the selected depth and threads", this::runPerft),
                new PaletteAction("Run built command", "Execute the selected command template", this::runSelectedTemplate),
                new PaletteAction("Copy built command", "Copy the command-builder preview", this::copyBuiltCommand),
                new PaletteAction("Reset built command", "Restore default command-builder options", this::resetSelectedTemplate),
                new PaletteAction("Filter command flags", "Focus the command-builder option filter", this::focusOptionFilter),
                new PaletteAction("Run batch", "Execute the selected batch workflow", this::runBatch),
                new PaletteAction("Run publishing", "Execute the selected publishing workflow", this::runPublishingCommand),
                new PaletteAction("Generate report", "Refresh the report maker output", this::generateReport),
                new PaletteAction("Copy report", "Copy the current report output", this::copyReport),
                new PaletteAction("Copy FEN", "Copy the current board FEN", () -> copyText(fenField.getText())),
                new PaletteAction("Board settings", "Adjust board highlights, arrows, notation, animations, and eval",
                        this::showDisplaySettings),
                new PaletteAction("Engine settings", "Adjust external engine protocol, nodes, and hash",
                        this::showEngineSettings),
                new PaletteAction("Engine smoke test", "Validate that the configured UCI engine starts",
                        this::runEngineSmoke),
                new PaletteAction("Validate config", "Run config validation", this::runConfigValidate),
                new PaletteAction("Focus FEN", "Move focus to the position field", this::focusFenField),
                new PaletteAction("Stop command", "Cancel the running child process", this::stopCommand),
                new PaletteAction("Open analyze tab", "Show board analysis tools", () -> selectTab(TAB_ANALYZE)),
                new PaletteAction("Focus game line", "Show the merged game tools", this::focusGameInput),
                new PaletteAction("Open commands tab", "Show command controller", () -> selectTab(TAB_COMMANDS)),
                new PaletteAction("Open batch tab", "Show batch workflows", () -> selectTab(TAB_BATCH)),
                new PaletteAction("Open publish tab", "Show report and publishing tools", () -> selectTab(TAB_PUBLISH)),
                new PaletteAction("Open console tab", "Show command output and process state",
                        () -> selectTab(TAB_CONSOLE)),
                new PaletteAction("Open logs folder", "Show persisted workbench command logs",
                        this::openLogsDirectory));
    }

    /**
     * Shows the in-workbench board settings panel.
     */
    private void showDisplaySettings() {
        selectTab(TAB_ANALYZE);
        if (analysisTabs != null) {
            analysisTabs.setSelectedIndex(0);
        }
        if (boardDetailTabs != null) {
            boardDetailTabs.setSelectedIndex(4);
        }
        toast(WorkbenchToast.Kind.INFO, "Settings are in the side panel");
    }

    /**
     * Shows the external-engine settings panel.
     */
    private void showEngineSettings() {
        selectTab(TAB_ANALYZE);
        if (analysisTabs != null) {
            analysisTabs.setSelectedIndex(0);
        }
        if (boardDetailTabs != null) {
            boardDetailTabs.setSelectedIndex(5);
        }
        SwingUtilities.invokeLater(engineProtocolField::requestFocusInWindow);
    }

    /**
     * Shows the analysis data visualization panel.
     */
    private void showAnalysisData() {
        selectTab(TAB_ANALYZE);
        if (analysisTabs != null) {
            analysisTabs.setSelectedIndex(0);
        }
        if (boardDetailTabs != null) {
            boardDetailTabs.setSelectedIndex(2);
        }
    }

    /**
     * Copies the live-analysis data as CSV.
     */
    private void copyAnalysisCsv() {
        copyText(analysisGraph.csvText());
        toast(WorkbenchToast.Kind.SUCCESS, "Analysis CSV copied");
    }

    /**
     * Copies the live-analysis report.
     */
    private void copyAnalysisReport() {
        copyText(analysisGraph.reportText());
        toast(WorkbenchToast.Kind.SUCCESS, "Analysis report copied");
    }

    /**
     * Prints the live-analysis report.
     */
    private void printAnalysisReport() {
        if (!analysisGraph.hasSamples()) {
            toast(WorkbenchToast.Kind.WARNING, "No analysis data to print");
            return;
        }
        try {
            boolean submitted = analysisGraph.printReport();
            if (submitted) {
                toast(WorkbenchToast.Kind.SUCCESS, "Analysis report sent to printer");
            }
        } catch (PrinterException ex) {
            showError("Print report failed", ex.getMessage());
        }
    }

    /**
     * Clears the live-analysis graph.
     */
    private void clearAnalysisData() {
        analysisGraph.clearSamples();
        toast(WorkbenchToast.Kind.INFO, "Analysis data cleared");
    }

    /**
     * Creates one settings toggle.
     *
     * @param text toggle text
     * @param tooltip tooltip text
     * @param selected selected state
     * @param onChange change callback
     * @return styled toggle
     */
    private WorkbenchToggleBox settingsToggle(String text, String tooltip, boolean selected,
            Consumer<Boolean> onChange) {
        WorkbenchToggleBox toggle = withTooltip(new WorkbenchToggleBox(text), tooltip);
        toggle.setSelected(selected);
        toggle.addActionListener(event -> onChange.accept(toggle.isSelected()));
        return toggle;
    }

    /**
     * Adds one display setting row and wires persistence.
     *
     * @param panel target panel
     * @param c constraints
     * @param row grid row
     * @param text toggle text
     * @param tooltip tooltip text
     * @param selected selected state
     * @param update update callback
     * @param refreshEval whether eval behavior should refresh immediately
     */
    private void addSettingsToggle(JPanel panel, GridBagConstraints c, int row, String text, String tooltip,
            boolean selected, Consumer<Boolean> update, boolean refreshEval) {
        grid(panel, settingsToggle(text, tooltip, selected,
                value -> updateDisplaySetting(() -> update.accept(value), refreshEval)), c, 0, row, 1, 1);
    }

    /**
     * Adds a transparent filler row so stretched form panels keep controls at
     * the top of the work surface.
     *
     * @param panel target panel
     * @param c reusable constraints
     * @param row grid row
     * @param width grid width
     */
    private static void addVerticalFiller(JPanel panel, GridBagConstraints c, int row, int width) {
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = width;
        c.gridheight = 1;
        c.weightx = 1;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        panel.add(transparentPanel(new BorderLayout()), c);
        c.weighty = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
    }

    /**
     * Applies one display setting change.
     *
     * @param update state update
     * @param refreshEval whether eval behavior should refresh immediately
     */
    private void updateDisplaySetting(Runnable update, boolean refreshEval) {
        update.run();
        saveDisplaySettings();
        applyDisplaySettings(refreshEval);
    }

    /**
     * Focuses the FEN input field.
     */
    private void focusFenField() {
        selectTab(TAB_ANALYZE);
        SwingUtilities.invokeLater(fenField::requestFocusInWindow);
    }

    /**
     * Focuses the command option filter.
     */
    private void focusOptionFilter() {
        selectTab(TAB_COMMANDS);
        SwingUtilities.invokeLater(commandForm.filterField()::requestFocusInWindow);
    }

    /**
     * Focuses the merged game-line editor.
     */
    private void focusGameInput() {
        selectTab(TAB_ANALYZE);
        if (analysisTabs != null) {
            analysisTabs.setSelectedIndex(1);
        }
        SwingUtilities.invokeLater(gameInput::requestFocusInWindow);
    }

    /**
     * Selects a workbench tab by index.
     *
     * @param index tab index
     */
    private void selectTab(int index) {
        if (tabs != null && index >= 0 && index < tabs.count()) {
            tabs.select(index);
        }
    }

    /**
     * Reacts to top-level tab visibility changes. Expensive live analysis is
     * scoped to visible board panes so Dashboard/Commands idle without a
     * background UCI engine consuming a core.
     */
    private void onWorkbenchTabVisibilityChanged() {
        networkPanel.setActive(tabs != null && tabs.isVisibleInPane(TAB_NETWORK));
        if (liveExternalEngineEnabled && isAnalyzePaneVisible()) {
            requestLiveAnalysisUpdate();
        } else if (liveEngineWorker != null) {
            pauseHiddenLiveAnalysis();
        } else if (liveExternalEngineEnabled) {
            setStatusBarEngine("live paused");
            session.updateEngine(engineProtocolValue(), true, "paused");
        }
    }

    /**
     * Returns whether the Analyze board pane is visible in any editor group.
     *
     * @return true when Analyze is visible
     */
    private boolean isAnalyzePaneVisible() {
        return tabs != null && tabs.isVisibleInPane(TAB_ANALYZE);
    }

    /**
     * Creates the board tab.
     *
     * @return tab component
     */
    private JComponent createBoardTab() {
        board.setMoveHandler(this::playMove);
        evalBar.setToolTipText("Engine evaluation");

        // The eval bar is a child of the board panel so it paints inside the
        // board's own paint pass — flush to the square, matched to its height,
        // and free of the sibling-overlap flicker.
        board.setEvalBar(evalBar);
        JPanel boardStage = transparentPanel(new BorderLayout());
        boardStage.add(board, BorderLayout.CENTER);

        JPanel side = transparentPanel(new BorderLayout(8, 8));
        side.setPreferredSize(new Dimension(400, 560));
        side.add(createBoardSideHeader(), BorderLayout.NORTH);
        side.add(createMovesAndTags(), BorderLayout.CENTER);

        JSplitPane boardPage = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, boardStage, side);
        boardPage.setBorder(BorderFactory.createEmptyBorder());
        boardPage.setOpaque(false);
        boardPage.setContinuousLayout(true);
        boardPage.setResizeWeight(0.68);
        boardPage.setDividerSize(8);
        boardPage.setDividerLocation(0.68);
        WorkbenchSplitPanes.style(boardPage);

        analysisTabs = createSectionTabs();
        analysisTabs.addTab("Board", boardPage);
        analysisTabs.addTab("Game", createGameSection());
        return analysisTabs;
    }

    /**
     * Creates the compact board-side header.
     *
     * @return side header
     */
    private JComponent createBoardSideHeader() {
        JPanel header = transparentPanel(new GridBagLayout());
        GridBagConstraints c = constraints();
        c.gridx = 0;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridy = 0;
        c.insets = new Insets(0, 0, 8, 0);
        header.add(createPositionControls(), c);

        c.gridy = 1;
        c.insets = new Insets(0, 0, 0, 0);
        header.add(createAnalysisControls(), c);
        return header;
    }

    /**
     * Creates position setup and board-navigation controls.
     *
     * @return controls
     */
    private JComponent createPositionControls() {
        JPanel panel = new WorkbenchSurfacePanel(new GridBagLayout());
        GridBagConstraints c = constraints();
        grid(panel, WorkbenchTheme.section("Position"), c, 0, 0, 4, 1);

        styleFields(fenField);
        fenField.addActionListener(event -> setPositionFromField());
        grid(panel, fenField, c, 0, 1, 4, 1);

        grid(panel, buttonRow(FlowLayout.LEFT,
                button("Load", true, event -> setPositionFromField()),
                iconButton("Back", event -> navigateGame(-1)),
                iconButton("Forward", event -> navigateGame(1)),
                iconButton("Reset", event -> startNewGame(Setup.getStandardStartFEN()))), c, 0, 2, 4, 1);
        grid(panel, buttonRow(FlowLayout.LEFT,
                iconButton("Flip", event -> {
                    board.setWhiteDown(!board.isWhiteDown());
                    appendConsole("Board flipped\n");
                }),
                iconButton("Copy FEN", event -> copyText(fenField.getText())),
                iconButton("Settings", event -> showDisplaySettings()),
                iconButton("Actions", event -> showCommandPalette())), c, 0, 3, 4, 1);
        return panel;
    }

    /**
     * Creates board controls.
     *
     * @return controls
     */
    private JComponent createAnalysisControls() {
        JPanel panel = new WorkbenchSurfacePanel(new GridBagLayout());
        GridBagConstraints c = constraints();
        grid(panel, WorkbenchTheme.section("Analysis"), c, 0, 0, 4, 1);
        statusLabel.setForeground(WorkbenchTheme.MUTED);
        statusLabel.setFont(WorkbenchTheme.font(12, Font.PLAIN));
        grid(panel, statusLabel, c, 0, 1, 4, 1);

        styleSpinners(analysisDepthSpinner, analysisMultipvSpinner, analysisThreadsSpinner);
        styleFields(analysisDurationField);
        grid(panel, label("Depth"), c, 0, 2, 1, 1);
        grid(panel, analysisDepthSpinner, c, 1, 2, 1, 1);
        grid(panel, label("Time"), c, 2, 2, 1, 1);
        analysisDurationField.getDocument()
                .addDocumentListener(changeListener(() -> syncDuration(analysisDurationField, batchDurationField)));
        grid(panel, analysisDurationField, c, 3, 2, 1, 1);

        grid(panel, buttonRow(FlowLayout.LEFT,
                button("Search", true, event -> runBuiltInSearch()),
                button("Best", false, event -> runBestMove()),
                button("Analyze", false, event -> runAnalyze())), c, 0, 3, 4, 1);
        liveEngineToggle.setSelected(liveExternalEngineEnabled);
        liveEngineToggle.addActionListener(event -> setLiveExternalEngineEnabled(liveEngineToggle.isSelected()));
        multipvModel.addChangeListener(event -> requestLiveAnalysisUpdate());
        threadsModel.addChangeListener(event -> requestLiveAnalysisUpdate());
        grid(panel, liveEngineToggle, c, 0, 4, 4, 1);
        grid(panel, collapsible("More", createAdvancedAnalysisControls(), false), c, 0, 5, 4, 1);
        return panel;
    }

    /**
     * Creates advanced analysis controls hidden behind the More disclosure.
     *
     * @return advanced controls
     */
    private JComponent createAdvancedAnalysisControls() {
        JPanel panel = transparentPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        panel.add(optionGroup("Lines", analysisMultipvSpinner));
        panel.add(optionGroup("Threads", analysisThreadsSpinner));
        panel.add(button("Engine", false, event -> showEngineSettings()));
        panel.add(button("Tags", false, event -> runTagsCommand()));
        panel.add(button("Perft", false, event -> runPerft()));
        return panel;
    }

    /**
     * Creates moves and tags lists.
     *
     * @return component
     */
    private JComponent createMovesAndTags() {
        movesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        WorkbenchTheme.table(movesTable, 27);
        // Render the SAN column with inline chess-piece figurines. Pin the
        // columns so a model data refresh does not drop the custom renderer.
        movesTable.setAutoCreateColumnsFromModel(false);
        movesTable.getColumnModel().getColumn(1).setCellRenderer(new WorkbenchSanRenderer());
        movesTable.addMouseListener(new MouseAdapter() {
            /**
             * Plays a legal move when a row is double-clicked.
             *
             * @param event mouse event
             */
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    int row = movesTable.getSelectedRow();
                    if (row >= 0 && row < visibleMoves.length) {
                        playMove(visibleMoves[row]);
                    }
                }
            }
        });

        WorkbenchTheme.list(tagList);
        tagList.setFont(WorkbenchTheme.mono(12));

        boardDetailTabs = createSectionTabs();
        boardDetailTabs.addTab("Moves", titled("Legal Moves", scroll(movesTable)));
        boardDetailTabs.addTab("Tags", titled("Tags", scroll(tagList)));
        boardDetailTabs.addTab("Data", createAnalysisDataPanel());
        boardDetailTabs.addTab("MCTS", mctsPanel);
        boardDetailTabs.addTab("Settings", createDisplaySettingsPanel());
        boardDetailTabs.addTab("Engine", createEngineSettingsPanel());
        return boardDetailTabs;
    }

    /**
     * Creates analysis graph and report controls.
     *
     * @return data panel
     */
    private JComponent createAnalysisDataPanel() {
        JPanel content = transparentPanel(new BorderLayout(6, 6));
        content.add(buttonRow(FlowLayout.LEFT,
                button("Copy CSV", false, event -> copyAnalysisCsv()),
                button("Copy Report", false, event -> copyAnalysisReport()),
                button("Print Report", false, event -> printAnalysisReport()),
                button("Clear", false, event -> clearAnalysisData())), BorderLayout.NORTH);
        content.add(analysisGraph, BorderLayout.CENTER);
        return titled("Analysis Data", content);
    }

    /**
     * Creates the in-workbench display settings panel.
     *
     * @return settings panel
     */
    private JComponent createDisplaySettingsPanel() {
        JPanel panel = new WorkbenchSurfacePanel(new GridBagLayout());
        GridBagConstraints c = constraints();
        c.insets = new Insets(6, 6, 6, 6);
        grid(panel, WorkbenchTheme.section("Board"), c, 0, 0, 1, 1);
        addSettingsToggle(panel, c, 1, "Legal move preview",
                "Show selected-piece destinations and legal drag targets on the board",
                showLegalMovePreview, selected -> showLegalMovePreview = selected, false);
        addSettingsToggle(panel, c, 2, "Last move highlight",
                "Show the previous move on the board",
                showLastMoveHighlight, selected -> showLastMoveHighlight = selected, false);
        addSettingsToggle(panel, c, 3, "Best move arrows",
                "Show engine best-move and analysis suggestions as board arrows",
                showBestMoveArrows, selected -> showBestMoveArrows = selected, false);
        addSettingsToggle(panel, c, 4, "Coordinates",
                "Show file and rank notation on the board",
                showBoardCoordinates, selected -> showBoardCoordinates = selected, false);
        addSettingsToggle(panel, c, 5, "Board animations",
                "Animate moves, snaps, snapbacks, and board flips",
                boardAnimationsEnabled, selected -> boardAnimationsEnabled = selected, false);
        grid(panel, WorkbenchTheme.section("Analysis"), c, 0, 6, 1, 1);
        addSettingsToggle(panel, c, 7, "Auto eval bar",
                "Automatically refresh the side evaluation bar after position changes",
                autoEvalBarEnabled, selected -> autoEvalBarEnabled = selected, true);

        addVerticalFiller(panel, c, 8, 1);
        return panel;
    }

    /**
     * Creates the external-engine configuration panel.
     *
     * @return engine settings panel
     */
    private JComponent createEngineSettingsPanel() {
        JPanel panel = new WorkbenchSurfacePanel(new GridBagLayout());
        GridBagConstraints c = constraints();
        c.insets = new Insets(6, 6, 6, 6);
        grid(panel, WorkbenchTheme.section("Engine"), c, 0, 0, 4, 1);

        engineProtocolField.setColumns(28);
        engineNodesField.setColumns(10);
        engineHashField.setColumns(7);
        styleFields(engineProtocolField, engineNodesField, engineHashField);
        grid(panel, label("protocol"), c, 0, 1, 1, 1);
        grid(panel, engineProtocolField, c, 1, 1, 2, 1);
        JButton chooseProtocol = button("Choose Protocol", false,
                event -> WorkbenchFileDialogs.choosePath(this, engineProtocolField, false, "Choose engine protocol",
                        new FileNameExtensionFilter("TOML files", "toml")));
        grid(panel, chooseProtocol, c, 3, 1, 1, 1);

        JPanel limitRow = flow(FlowLayout.LEFT);
        limitRow.add(label("nodes"));
        limitRow.add(engineNodesField);
        limitRow.add(label("hash"));
        limitRow.add(engineHashField);
        grid(panel, limitRow, c, 1, 2, 3, 1);

        grid(panel, buttonRow(FlowLayout.LEFT,
                button("Smoke", false, event -> runEngineSmoke()),
                button("Validate Config", false, event -> runConfigValidate()),
                button("Defaults", false, event -> resetEngineSettings())), c, 1, 3, 3, 1);
        addVerticalFiller(panel, c, 4, 4);
        return panel;
    }

    /**
     * Creates the merged game-line section inside the analysis tab.
     *
     * @return game section
     */
    private JComponent createGameSection() {
        JComponent tools = scroll(fillViewport(createGameToolsPanel()));
        tools.setPreferredSize(new Dimension(390, 520));

        JSplitPane gamePage = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, createGameHistoryPanel(), tools);
        gamePage.setBorder(BorderFactory.createEmptyBorder());
        gamePage.setOpaque(false);
        gamePage.setContinuousLayout(true);
        gamePage.setResizeWeight(0.68);
        gamePage.setDividerSize(8);
        gamePage.setDividerLocation(0.68);
        WorkbenchSplitPanes.style(gamePage);
        return gamePage;
    }

    /**
     * Creates the move-history panel.
     *
     * @return history panel
     */
    private JComponent createGameHistoryPanel() {
        configureGameTable();
        JPanel panel = new WorkbenchSurfacePanel(new BorderLayout(8, 8));
        JPanel top = transparentPanel(new BorderLayout(8, 0));
        top.add(WorkbenchTheme.section("Game Line"), BorderLayout.WEST);
        gameStateLabel.setForeground(WorkbenchTheme.MUTED);
        gameStateLabel.setFont(WorkbenchTheme.font(12, Font.PLAIN));
        top.add(gameStateLabel, BorderLayout.CENTER);
        panel.add(top, BorderLayout.NORTH);
        panel.add(scroll(gameTable), BorderLayout.CENTER);

        panel.add(buttonRow(FlowLayout.LEFT,
                button("Start", false, event -> jumpGameTo(0)),
                button("Back", false, event -> navigateGame(-1)),
                button("Forward", false, event -> navigateGame(1)),
                button("End", false, event -> jumpGameTo(gameModel.lastPly())),
                button("Copy PGN", false, event -> copyText(gameModel.pgn())),
                button("Copy SAN", false, event -> copyText(gameModel.sanLine())),
                button("Copy UCI", false, event -> copyText(gameModel.uciLine()))), BorderLayout.SOUTH);
        return panel;
    }

    /**
     * Creates game import/export tools.
     *
     * @return tools panel
     */
    private JComponent createGameToolsPanel() {
        JPanel panel = new WorkbenchSurfacePanel(new GridBagLayout());
        GridBagConstraints c = constraints();
        grid(panel, WorkbenchTheme.section("Line Tools"), c, 0, 0, 4, 1);

        styleAreas(gameInput);
        gameInput.setRows(5);
        gameInput.setLineWrap(true);
        gameInput.setWrapStyleWord(true);
        gameInput.setText("1. e4 e5 2. Nf3 Nc6");
        grid(panel, label("input"), c, 0, 1, 1, 1);
        grid(panel, scroll(gameInput), c, 1, 1, 3, 1);

        grid(panel, buttonRow(FlowLayout.LEFT,
                button("Load Line", true, event -> loadGameText(gameInput.getText())),
                button("Load File", false, event -> loadGameFile()),
                button("Save PGN", false, event -> savePgnFile()),
                button("New Game", false, event -> startNewGame(currentFen()))), c, 1, 2, 3, 1);

        grid(panel, buttonRow(FlowLayout.LEFT,
                button("Copy FEN List", false, event -> copyText(gameModel.fenList())),
                button("Add to Batch", false, event -> appendCurrentFenToBatch())), c, 1, 3, 3, 1);
        addVerticalFiller(panel, c, 4, 4);
        return panel;
    }

    /**
     * Creates the command tab.
     *
     * @return tab
     */
    private JComponent createCommandTab() {
        installTemplates();
        return scroll(fillViewport(createCommandBuilder()));
    }

    /**
     * Creates command builder.
     *
     * @return panel
     */
    private JPanel createCommandBuilder() {
        JPanel panel = transparentPanel(new BorderLayout(0, 10));
        panel.setBorder(WorkbenchTheme.pad(WorkbenchTheme.SPACE_MD));

        commandForm.setChangeListener(this::updateBuiltCommand);
        commandForm.setRunGate(this::updateCommandRunGate);
        styleFields(commandField);
        depthModel.addChangeListener(event -> requestCommandPreviews());
        multipvModel.addChangeListener(event -> requestCommandPreviews());
        threadsModel.addChangeListener(event -> requestCommandPreviews());

        // Header: section title + the wrapping command-selector bar. The bar
        // replaces a JTabbedPane whose empty content pane left a blank gap.
        JPanel header = transparentPanel(new BorderLayout(0, 6));
        header.add(WorkbenchTheme.section("Command Controller"), BorderLayout.NORTH);
        header.add(commandPicker, BorderLayout.CENTER);
        panel.add(header, BorderLayout.NORTH);

        panel.add(commandForm, BorderLayout.CENTER);

        JPanel south = transparentPanel(new BorderLayout(0, 8));
        commandField.setEditable(false);
        JPanel previewRow = transparentPanel(new BorderLayout(8, 0));
        previewRow.add(label("preview"), BorderLayout.WEST);
        previewRow.add(commandField, BorderLayout.CENTER);
        south.add(previewRow, BorderLayout.NORTH);
        runCommandButton = button("Run", true, event -> runSelectedTemplate());
        south.add(buttonRow(FlowLayout.LEFT,
                runCommandButton,
                button("Copy", false, event -> copyBuiltCommand()),
                button("Reset", false, event -> resetSelectedTemplate()),
                button("Drop Optional", false, event -> clearOptionalTemplateOptions()),
                button("Stop", false, event -> stopCommand())), BorderLayout.SOUTH);
        panel.add(south, BorderLayout.SOUTH);

        updateCommandOptions();
        updateBuiltCommand();
        return panel;
    }

    /**
     * Enables or disables the Run button from the command form's validity.
     *
     * @param ready true when the built command is runnable
     */
    private void updateCommandRunGate(boolean ready) {
        if (runCommandButton != null) {
            runCommandButton.setEnabled(ready);
            runCommandButton.setToolTipText(ready ? null
                    : "Fix the highlighted fields before running");
        }
    }

    /**
     * Creates the batch tab.
     *
     * @return tab
     */
    private JComponent createBatchTab() {
        installBatchTasks();
        JPanel panel = transparentPanel(new BorderLayout(10, 10));

        batchInput.setText(Setup.getStandardStartFEN() + System.lineSeparator()
                + "4R1k1/5ppp/8/8/8/8/8/6K1 b - - 0 1" + System.lineSeparator());
        styleAreas(batchInput);
        batchInput.setRows(12);
        batchInput.getDocument().addDocumentListener(changeListener(this::requestBatchInputStatusUpdate));

        JComponent runner = scroll(fillViewport(createBatchRunnerPanel()));
        runner.setPreferredSize(new Dimension(420, 520));

        JSplitPane batchPage = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, createBatchFenPanel(), runner);
        batchPage.setBorder(BorderFactory.createEmptyBorder());
        batchPage.setOpaque(false);
        batchPage.setContinuousLayout(true);
        batchPage.setResizeWeight(0.64);
        batchPage.setDividerSize(8);
        batchPage.setDividerLocation(0.64);
        WorkbenchSplitPanes.style(batchPage);
        panel.add(batchPage, BorderLayout.CENTER);
        updateBatchControls();
        updateBatchCommand();
        refreshBatchInputStatus();
        return panel;
    }

    /**
     * Creates the batch FEN input surface.
     *
     * @return FEN input panel
     */
    private JComponent createBatchFenPanel() {
        JPanel panel = new WorkbenchSurfacePanel(new BorderLayout(8, 8));
        JPanel top = transparentPanel(new BorderLayout(8, 0));
        top.add(WorkbenchTheme.section("FEN Batch"), BorderLayout.WEST);
        batchInputStatus.setForeground(WorkbenchTheme.MUTED);
        batchInputStatus.setFont(WorkbenchTheme.font(12, Font.PLAIN));
        batchInputStatus.setHorizontalAlignment(SwingConstants.RIGHT);
        top.add(batchInputStatus, BorderLayout.CENTER);
        panel.add(top, BorderLayout.NORTH);
        panel.add(scroll(batchInput), BorderLayout.CENTER);
        addCurrentFenButton = button("Add Current FEN", false, event -> appendCurrentFenToBatch());
        clearBatchInputButton = button("Clear", false, event -> batchInput.setText(""));
        panel.add(buttonRow(FlowLayout.LEFT, addCurrentFenButton, clearBatchInputButton), BorderLayout.SOUTH);
        return panel;
    }

    /**
     * Appends the current board FEN to the batch input without joining it onto
     * an existing last line.
     */
    private void appendCurrentFenToBatch() {
        String text = batchInput.getText();
        if (!text.isEmpty() && !text.endsWith("\n") && !text.endsWith("\r")) {
            batchInput.append(System.lineSeparator());
        }
        batchInput.append(currentFen() + System.lineSeparator());
        batchInput.requestFocusInWindow();
        updateBatchCommand();
    }

    /**
     * Creates the batch-runner controls.
     *
     * @return runner panel
     */
    private JComponent createBatchRunnerPanel() {
        JPanel controls = new WorkbenchSurfacePanel(new GridBagLayout());
        GridBagConstraints c = constraints();
        styleCombos(batchTaskCombo);
        batchTaskCombo.setPrototypeDisplayValue(new BatchTask("Analyze batch", true,
                new WorkflowControls(true, false, true, true), (input, ctx) -> List.of()));
        batchTaskCombo.addActionListener(event -> {
            updateBatchControls();
            updateBatchCommand();
        });
        grid(controls, WorkbenchTheme.section("Batch Runner"), c, 0, 0, 4, 1);
        grid(controls, label("task"), c, 0, 1, 1, 1);
        grid(controls, batchTaskCombo, c, 1, 1, 3, 1);
        batchDurationField.setColumns(8);
        styleFields(batchDurationField);
        styleAreas(batchCommandField);
        batchDurationField.getDocument()
                .addDocumentListener(changeListener(() -> syncDuration(batchDurationField, analysisDurationField)));
        styleSpinners(batchDepthSpinner, batchMultipvSpinner, batchThreadsSpinner);
        grid(controls, batchOptionsPanel, c, 1, 2, 3, 1);
        grid(controls, label("command"), c, 0, 3, 1, 1);
        batchCommandField.setLineWrap(true);
        batchCommandField.setWrapStyleWord(false);
        batchCommandField.setEditable(false);
        batchCommandField.setFocusable(false);
        batchCommandField.setToolTipText("Generated batch command");
        batchCommandField.setMinimumSize(new Dimension(220, 64));
        grid(controls, batchCommandField, c, 1, 3, 3, 1);
        grid(controls, buttonRow(FlowLayout.LEFT,
                button("Run Batch", true, event -> runBatch()),
                button("Copy Command", false, event -> copyText(batchCommandField.getText()))), c, 1, 4, 3, 1);
        addVerticalFiller(controls, c, 5, 4);
        return controls;
    }

    /**
     * Creates the publishing tab.
     *
     * @return publish tab
     */
    private JComponent createPublishTab() {
        return publishingPanel.component();
    }

    /**
     * Creates a compact nested tabbed pane for replacing divider-heavy sections.
     *
     * @return styled tabbed pane
     */
    private static JTabbedPane createSectionTabs() {
        JTabbedPane pane = tabbedPane();
        pane.setBorder(BorderFactory.createEmptyBorder());
        return pane;
    }

    /**
     * Generates a report for the current position and game line.
     */
    private void generateReport() {
        reportPanel.generateReport();
    }

    /**
     * Copies the current report, generating it first when empty.
     */
    private void copyReport() {
        reportPanel.copyReport();
    }

    /**
     * Saves the current report to a text file.
     */
    private void saveReportFile() {
        reportPanel.saveReportFile();
    }

    /**
     * Saves the visible console text to a user-chosen log file.
     */
    private void saveConsoleLog() {
        JFileChooser chooser = WorkbenchFileDialogs.createFileChooser(null, new File("workbench-console.log"),
                new FileNameExtensionFilter("Log files", "log", "txt"));
        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = WorkbenchFileDialogs.ensureExtension(chooser.getSelectedFile(), ".log");
        String contents = console.getText();
        runAsync(
                () -> {
                    Files.writeString(file.toPath(), contents, StandardCharsets.UTF_8);
                    return file.toPath().toAbsolutePath().normalize();
                },
                saved -> {
                    session.artifacts().add(saved);
                    appendConsole("Saved console log to " + saved + "\n");
                    toast(WorkbenchToast.Kind.SUCCESS, "Saved log to " + saved.getFileName());
                },
                ex -> showError("Save log failed", ex.getMessage()));
    }

    /**
     * Creates console panel.
     *
     * @return panel
     */
    private JComponent createConsolePanel() {
        JPanel panel = new WorkbenchSurfacePanel(new BorderLayout(6, 6));
        JPanel top = transparentPanel(new BorderLayout());
        top.add(WorkbenchTheme.section("Console"), BorderLayout.WEST);
        commandStateLabel.setForeground(WorkbenchTheme.MUTED);
        commandStateLabel.setFont(WorkbenchTheme.font(12, Font.PLAIN));
        commandStateLabel.setBorder(WorkbenchTheme.pad(0, WorkbenchTheme.SPACE_SM));
        top.add(commandStateLabel, BorderLayout.CENTER);
        top.add(buttonRow(FlowLayout.RIGHT,
                button("Open Logs", false, event -> openLogsDirectory()),
                button("Save Log", false, event -> saveConsoleLog()),
                button("Clear", false, event -> console.clearOutput()),
                button("Stop", false, event -> stopCommand())), BorderLayout.EAST);
        panel.add(top, BorderLayout.NORTH);
        panel.add(scroll(console), BorderLayout.CENTER);
        return panel;
    }

    /**
     * Updates the current position from the FEN field.
     */
    private void setPositionFromField() {
        startNewGame(fenField.getText());
    }

    /**
     * Starts a fresh game line from a FEN.
     *
     * @param fen FEN
     */
    private void startNewGame(String fen) {
        try {
            Position start = new Position(fen.trim());
            gameModel.reset(start);
            session.clearEvalHistory();
            showGamePly(0);
            appendConsole("New game from " + start + "\n");
        } catch (IllegalArgumentException ex) {
            showError("Invalid FEN", ex.getMessage());
        }
    }

    /**
     * Sets the visible current position.
     *
     * @param position position
     * @param lastMove last move
     */
    private void setPosition(Position position, short lastMove) {
        currentPosition = position.copy();
        fenField.setText(currentPosition.toString());
        board.setPosition(currentPosition, lastMove);
        analysisGraph.resetForPosition(currentPosition.toString());
        networkPanel.setFen(currentPosition.toString());
        mctsPanel.setFen(currentPosition.toString());
        updateMoves();
        updateStatus();
        updateTagsAsync();
        requestCommandPreviews();
        updateGameState();
        requestEvalUpdate();
        refreshStatusBar();
        updateSessionPosition();
    }

    /**
     * Pushes the current position into the shared {@link #session} so the
     * Dashboard tab updates without scraping Swing components. Tags arrive
     * later, asynchronously, via {@link #updateTagsAsync()}.
     */
    private void updateSessionPosition() {
        if (currentPosition == null) {
            return;
        }
        session.updatePosition(currentPosition.toString(), currentPosition.isWhiteToMove(),
                gameModel.currentPly(), gameModel.lastPly(), visibleMoves.length);
        updateSessionEngine();
    }

    /**
     * Pushes the current external-engine configuration into the session so the
     * Dashboard's Engine card stays current.
     */
    private void updateSessionEngine() {
        session.updateEngine(engineProtocolValue(), liveExternalEngineEnabled,
                session.engineSummary());
    }

    /**
     * Magnitude, in centipawns, used to represent a forced mate in the
     * dashboard eval sparkline so the line stays on a sensible scale.
     */
    private static final int MATE_EVAL_CENTIPAWNS = 3000;

    /**
     * Records a white-relative engine evaluation for the current ply into the
     * session so the Dashboard's eval-over-plies sparkline can plot it.
     *
     * @param whiteCentipawns evaluation in centipawns, from White's view
     */
    private void recordSessionEval(int whiteCentipawns) {
        session.recordEval(gameModel.currentPly(), whiteCentipawns);
    }

    /**
     * Schedules an eval-bar refresh; coalesces rapid navigation into a single
     * subprocess fork after a short debounce.
     */
    private void requestEvalUpdate() {
        if (liveExternalEngineEnabled) {
            evalDebounceTimer.stop();
            cancelEvalCommand();
            if (isAnalyzePaneVisible()) {
                requestLiveAnalysisUpdate();
            } else {
                pauseHiddenLiveAnalysis();
            }
            return;
        }
        if (!autoEvalBarEnabled) {
            evalDebounceTimer.stop();
            cancelEvalCommand();
            stopLiveAnalysis();
            evalBar.setUnavailable("off");
            return;
        }
        if (currentPosition == null) {
            evalDebounceTimer.stop();
            cancelEvalCommand();
            evalBar.setUnavailable("n/a");
            return;
        }
        ++evalRequestId;
        cancelEvalCommand();
        evalBar.setThinking();
        evalDebounceTimer.restart();
    }

    /**
     * Forks the engine subprocess for the most recent eval request.
     */
    private void startEvalCommand() {
        if (currentPosition == null) {
            return;
        }
        evalFailureLogged = false;
        long requestId = evalRequestId;
        String fen = currentFen();
        boolean whiteToMove = currentPosition.isWhiteToMove();
        List<String> args;
        try {
            args = buildEvalBarArgs(fen);
        } catch (IllegalArgumentException ex) {
            evalBar.setUnavailable("cfg");
            appendConsole("Eval bar paused: " + ex.getMessage() + System.lineSeparator());
            return;
        }
        evalCommand = WorkbenchCommandRunner.run(args, null, null,
                result -> applyEvalResult(requestId, whiteToMove, result.output()),
                ex -> handleEvalFailure(requestId, ex));
    }

    /**
     * Applies an engine result to the eval bar when it is still current.
     *
     * @param requestId request id
     * @param whiteToMove true when White was to move in the analyzed position
     * @param output engine command output
     */
    private void applyEvalResult(long requestId, boolean whiteToMove, String output) {
        if (requestId != evalRequestId) {
            return;
        }
        WorkbenchEngineEval eval = parseEngineEval(output);
        if (eval == null) {
            evalBar.setUnavailable("n/a");
            return;
        }
        int value = whiteToMove ? eval.value() : -eval.value();
        if (eval.mate()) {
            if (eval.value() == 0) {
                evalBar.setMateDelivered(whiteToMove);
                recordSessionEval(whiteToMove ? -MATE_EVAL_CENTIPAWNS : MATE_EVAL_CENTIPAWNS);
            } else {
                evalBar.setMate(value);
                recordSessionEval(value > 0 ? MATE_EVAL_CENTIPAWNS : -MATE_EVAL_CENTIPAWNS);
            }
        } else {
            evalBar.setCentipawns(value);
            recordSessionEval(value);
        }
    }

    /**
     * Handles automatic eval-bar command failures.
     *
     * @param requestId request id
     * @param exception failure
     */
    private void handleEvalFailure(long requestId, Exception exception) {
        if (requestId != evalRequestId || exception instanceof CancellationException) {
            return;
        }
        evalBar.setUnavailable("n/a");
        if (!evalFailureLogged) {
            evalFailureLogged = true;
            String message = exception == null ? "unknown failure"
                    : exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            appendConsole("Eval bar disabled: " + message + System.lineSeparator());
            toast(WorkbenchToast.Kind.WARNING, "Eval bar disabled: " + message);
        }
    }

    /**
     * Whether the eval-bar diagnostic line has already been emitted.
     * Reset by {@link #startEvalCommand} so a recovery message after the user
     * fixes their configuration is also surfaced once.
     */
    private boolean evalFailureLogged;

    /**
     * Cancels the currently running eval-bar process.
     */
    private void cancelEvalCommand() {
        if (evalCommand != null && evalCommand.isRunning()) {
            evalCommand.cancel();
        }
        evalCommand = null;
    }

    /**
     * Enables or disables live external-engine analysis.
     *
     * @param enabled true to keep an external engine analyzing the board
     */
    private void setLiveExternalEngineEnabled(boolean enabled) {
        if (liveExternalEngineEnabled == enabled) {
            liveEngineToggle.setSelected(enabled);
            return;
        }
        liveExternalEngineEnabled = enabled;
        liveEngineToggle.setSelected(enabled);
        saveDisplaySettings();
        if (enabled) {
            liveAnalysisFailureLogged = false;
            evalDebounceTimer.stop();
            cancelEvalCommand();
            appendConsole("Live external engine enabled\n");
            if (isAnalyzePaneVisible()) {
                requestLiveAnalysisUpdate();
            } else {
                pauseHiddenLiveAnalysis();
            }
        } else {
            appendConsole("Live external engine disabled\n");
            stopLiveAnalysis();
            setStatusBarEngine("idle");
            session.updateEngine(engineProtocolValue(), false, "");
            if (autoEvalBarEnabled) {
                requestEvalUpdate();
            } else {
                evalBar.setUnavailable("off");
            }
        }
    }

    /**
     * Restarts the live engine after external-engine settings change.
     */
    private void restartLiveAnalysis() {
        stopLiveAnalysisWorker();
        liveAnalysisFailureLogged = false;
        if (isAnalyzePaneVisible()) {
            requestLiveAnalysisUpdate();
        } else {
            pauseHiddenLiveAnalysis();
        }
    }

    /**
     * Stops live analysis while preserving the enabled setting because the
     * Analyze pane is hidden.
     */
    private void pauseHiddenLiveAnalysis() {
        stopLiveAnalysis();
        setStatusBarEngine("live paused");
        session.updateEngine(engineProtocolValue(), true, "paused");
    }

    /**
     * Requests live analysis for the current board position.
     */
    private void requestLiveAnalysisUpdate() {
        if (!liveExternalEngineEnabled) {
            return;
        }
        if (currentPosition == null) {
            evalBar.setUnavailable("n/a");
            return;
        }
        LiveAnalysisRequest request;
        try {
            request = createLiveAnalysisRequest();
        } catch (IllegalArgumentException ex) {
            evalBar.setUnavailable("cfg");
            setStatusBarEngine("live config");
            toast(WorkbenchToast.Kind.WARNING, ex.getMessage());
            return;
        }
        board.setSuggestedMove(Move.NO_MOVE);
        evalBar.setThinking();
        setStatusBarEngine("live starting");
        synchronized (liveAnalysisLock) {
            liveAnalysisRequest = request;
            liveAnalysisLock.notifyAll();
        }
        ensureLiveAnalysisWorker(request.protocolPath());
    }

    /**
     * Creates a snapshot request for live analysis.
     *
     * @return live-analysis request
     */
    private LiveAnalysisRequest createLiveAnalysisRequest() {
        return new LiveAnalysisRequest(
                ++liveAnalysisRequestId,
                currentPosition.copy(),
                currentPosition.isWhiteToMove(),
                liveProtocolPath(),
                ((Number) multipvModel.getValue()).intValue(),
                ((Number) threadsModel.getValue()).intValue(),
                optionalPositiveInteger(engineHashField, "--hash"));
    }

    /**
     * Returns the protocol path used by live analysis.
     *
     * @return configured protocol path, or the config default
     */
    private String liveProtocolPath() {
        String path = engineProtocolValue();
        return path.isEmpty() ? Config.getProtocolPath() : path;
    }

    /**
     * Ensures a live-analysis worker is running for the requested protocol.
     *
     * @param protocolPath protocol TOML path
     */
    private void ensureLiveAnalysisWorker(String protocolPath) {
        if (liveEngineWorker != null && !liveEngineWorker.isDone()
                && liveEngineWorker.usesProtocol(protocolPath)) {
            return;
        }
        stopLiveAnalysisWorker();
        liveEngineWorker = new LiveEngineWorker(protocolPath);
        liveEngineWorker.execute();
    }

    /**
     * Stops live analysis and clears the latest request.
     */
    private void stopLiveAnalysis() {
        liveAnalysisRequestId++;
        synchronized (liveAnalysisLock) {
            liveAnalysisRequest = null;
            liveAnalysisLock.notifyAll();
        }
        stopLiveAnalysisWorker();
        board.setSuggestedMove(Move.NO_MOVE);
    }

    /**
     * Stops the live-analysis worker without changing the enabled flag.
     */
    private void stopLiveAnalysisWorker() {
        LiveEngineWorker worker = liveEngineWorker;
        if (worker != null) {
            worker.requestStop();
            worker.cancel(true);
            liveEngineWorker = null;
        }
        synchronized (liveAnalysisLock) {
            liveAnalysisLock.notifyAll();
        }
    }

    /**
     * Waits for the next live-analysis request after the supplied id.
     *
     * @param previousId request id already consumed by the worker
     * @param worker worker waiting for work
     * @return next request, or null when the worker should exit
     * @throws InterruptedException when interrupted while waiting
     */
    private LiveAnalysisRequest awaitLiveAnalysisRequest(long previousId, LiveEngineWorker worker)
            throws InterruptedException {
        synchronized (liveAnalysisLock) {
            while (!worker.shouldStop()
                    && (liveAnalysisRequest == null || liveAnalysisRequest.id() == previousId)) {
                liveAnalysisLock.wait();
            }
            return worker.shouldStop() ? null : liveAnalysisRequest;
        }
    }

    /**
     * Returns whether a live request has been superseded.
     *
     * @param request request currently being analyzed
     * @return true when analysis should stop
     */
    private boolean liveRequestSuperseded(LiveAnalysisRequest request) {
        synchronized (liveAnalysisLock) {
            return !liveExternalEngineEnabled
                    || liveAnalysisRequest == null
                    || liveAnalysisRequest.id() != request.id();
        }
    }

    /**
     * Applies one live-analysis update to the board.
     *
     * @param update streamed update
     */
    private void applyLiveAnalysisUpdate(LiveAnalysisUpdate update) {
        if (update.status() != null) {
            setStatusBarEngine(update.status());
            return;
        }
        if (!liveExternalEngineEnabled || update.requestId() != liveAnalysisRequestId) {
            return;
        }
        Output output = update.output();
        if (output == null) {
            return;
        }
        applyLiveEvaluation(update.whiteToMove(), output.getEvaluation());
        if (update.bestMove() != Move.NO_MOVE) {
            board.setSuggestedMove(update.bestMove());
        }
        analysisGraph.addSample(update.whiteToMove(), output, update.bestMove());
        String summary = formatLiveEngineStatus(output, update.bestMove());
        setStatusBarEngine(summary);
        session.updateEngine(engineProtocolValue(), true, summary);
    }

    /**
     * Applies a live engine evaluation to the eval bar.
     *
     * @param whiteToMove true when White was to move in the analyzed position
     * @param evaluation engine evaluation from side-to-move perspective
     */
    private void applyLiveEvaluation(boolean whiteToMove, Evaluation evaluation) {
        if (evaluation == null || !evaluation.isValid()) {
            return;
        }
        int value = whiteToMove ? evaluation.getValue() : -evaluation.getValue();
        if (evaluation.isMate()) {
            if (evaluation.getValue() == 0) {
                evalBar.setMateDelivered(whiteToMove);
                recordSessionEval(whiteToMove ? -MATE_EVAL_CENTIPAWNS : MATE_EVAL_CENTIPAWNS);
            } else {
                evalBar.setMate(value);
                recordSessionEval(value > 0 ? MATE_EVAL_CENTIPAWNS : -MATE_EVAL_CENTIPAWNS);
            }
        } else {
            evalBar.setCentipawns(value);
            recordSessionEval(value);
        }
    }

    /**
     * Formats compact live-engine status text for the bottom status bar.
     *
     * @param output latest engine output
     * @param bestMove latest best move
     * @return status text
     */
    private static String formatLiveEngineStatus(Output output, short bestMove) {
        StringBuilder status = new StringBuilder("live d").append(output.getDepth());
        Evaluation evaluation = output.getEvaluation();
        if (evaluation != null && evaluation.isValid()) {
            status.append(' ').append(formatLiveEvaluation(evaluation));
        }
        if (bestMove != Move.NO_MOVE) {
            status.append(' ').append(Move.toString(bestMove));
        }
        return status.toString();
    }

    /**
     * Formats an engine evaluation for compact live status text.
     *
     * @param evaluation engine evaluation
     * @return formatted text
     */
    private static String formatLiveEvaluation(Evaluation evaluation) {
        if (evaluation.isMate()) {
            return "#" + evaluation.getValue();
        }
        int value = evaluation.getValue();
        return (value > 0 ? "+" : "") + value;
    }

    /**
     * Handles an unrecoverable live-analysis failure.
     *
     * @param exception failure
     */
    private void handleLiveAnalysisFailure(Exception exception) {
        if (liveAnalysisFailureLogged) {
            return;
        }
        liveAnalysisFailureLogged = true;
        String message = exception == null ? "unknown failure"
                : exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        liveExternalEngineEnabled = false;
        liveEngineToggle.setSelected(false);
        saveDisplaySettings();
        evalBar.setUnavailable("n/a");
        board.setSuggestedMove(Move.NO_MOVE);
        setStatusBarEngine("live failed");
        appendConsole("Live external engine disabled: " + message + System.lineSeparator());
        toast(WorkbenchToast.Kind.WARNING, "Live engine disabled: " + message);
    }

    /**
     * Loads a UCI protocol file for live analysis.
     *
     * @param protocolPath protocol TOML path
     * @return parsed protocol
     * @throws IOException when the protocol cannot be read or is invalid
     */
    private static Protocol loadLiveProtocol(String protocolPath) throws IOException {
        Protocol protocol = new Protocol().fromToml(Files.readString(Path.of(protocolPath)));
        if (!protocol.assertValid()) {
            StringBuilder message = new StringBuilder("Protocol is missing required values:");
            for (String error : protocol.collectValidationErrors()) {
                message.append(System.lineSeparator()).append("  - ").append(error);
            }
            throw new IOException(message.toString());
        }
        return protocol;
    }

    /**
     * Applies live-engine options before a search starts.
     *
     * @param engine engine process
     * @param request live-analysis request
     */
    private static void configureLiveEngine(Engine engine, LiveAnalysisRequest request) {
        engine.setMultiPivot(request.multipv());
        engine.setThreadAmount(request.threads());
        if (request.hash() != null) {
            engine.setHashSize(request.hash());
        }
    }

    /**
     * Parses an optional positive integer field.
     *
     * @param field source field
     * @param label human-readable setting label
     * @return parsed value, or null when blank
     */
    private static Integer optionalPositiveInteger(JTextField field, String label) {
        String value = trimmed(field);
        if (value.isEmpty()) {
            return null;
        }
        if (!value.matches("[1-9]\\d*")) {
            throw new IllegalArgumentException(label + " expects a positive integer.");
        }
        return Integer.valueOf(value);
    }

    /**
     * Adds an optional text option.
     *
     * @param args target arguments
     * @param flag option flag
     * @param field source field
     */
    private static void addOptionalTextArg(List<String> args, String flag, JTextField field) {
        String value = trimmed(field);
        if (!value.isEmpty()) {
            args.add(flag);
            args.add(value);
        }
    }

    /**
     * Adds an optional positive integer option.
     *
     * @param args target arguments
     * @param flag option flag
     * @param field source field
     */
    private static void addOptionalPositiveIntegerArg(List<String> args, String flag, JTextField field) {
        String value = trimmed(field);
        if (value.isEmpty()) {
            return;
        }
        if (!value.matches("[1-9]\\d*")) {
            throw new IllegalArgumentException(flag + " expects a positive integer.");
        }
        args.add(flag);
        args.add(value);
    }

    /**
     * Returns trimmed field text.
     *
     * @param field source field
     * @return trimmed text
     */
    private static String trimmed(JTextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }

    /**
     * Live external-engine worker. Owns the UCI process and streams updates until
     * the current request is replaced or live mode is disabled.
     */
    private final class LiveEngineWorker extends SwingWorker<Void, LiveAnalysisUpdate> {

        /**
         * Protocol path this worker was started with.
         */
        private final String protocolPath;

        /**
         * Whether this worker should stop.
         */
        private volatile boolean stopRequested;

        /**
         * Last time a UI update was published.
         */
        private long lastPublishedAt;

        /**
         * Creates a worker.
         *
         * @param protocolPath protocol TOML path
         */
        private LiveEngineWorker(String protocolPath) {
            this.protocolPath = protocolPath;
        }

        /**
         * Returns whether this worker uses the given protocol path.
         *
         * @param value protocol path
         * @return true when equal
         */
        private boolean usesProtocol(String value) {
            return Objects.equals(protocolPath, value);
        }

        /**
         * Requests worker shutdown.
         */
        private void requestStop() {
            stopRequested = true;
        }

        /**
         * Returns whether this worker should stop.
         *
         * @return true when stopping
         */
        private boolean shouldStop() {
            return stopRequested || isCancelled();
        }

        /**
         * Runs live analysis.
         *
         * @return unused
         * @throws Exception on engine startup or I/O failure
         */
        @Override
        protected Void doInBackground() throws Exception {
            publish(LiveAnalysisUpdate.status("live starting"));
            try (Engine engine = new Engine(loadLiveProtocol(protocolPath))) {
                long lastRequestId = -1;
                while (!shouldStop()) {
                    LiveAnalysisRequest request = awaitLiveAnalysisRequest(lastRequestId, this);
                    if (request == null) {
                        break;
                    }
                    lastRequestId = request.id();
                    configureLiveEngine(engine, request);
                    publish(LiveAnalysisUpdate.status("live thinking"));
                    Analysis analysis = new Analysis();
                    engine.analyseInfinite(request.position(), analysis, null, LIVE_ANALYSIS_STALL_TIMEOUT_MS,
                            () -> shouldStop() || liveRequestSuperseded(request),
                            current -> publishLiveUpdate(request, current));
                }
            }
            return null;
        }

        /**
         * Publishes throttled live-analysis output.
         *
         * @param request active request
         * @param analysis active analysis buffer
         */
        private void publishLiveUpdate(LiveAnalysisRequest request, Analysis analysis) {
            long now = System.currentTimeMillis();
            if (now - lastPublishedAt < LIVE_ANALYSIS_UPDATE_INTERVAL_MS) {
                return;
            }
            lastPublishedAt = now;
            publish(LiveAnalysisUpdate.analysis(request, analysis));
        }

        /**
         * Applies streamed updates on the event thread.
         *
         * @param chunks updates
         */
        @Override
        protected void process(List<LiveAnalysisUpdate> chunks) {
            for (LiveAnalysisUpdate update : chunks) {
                applyLiveAnalysisUpdate(update);
            }
        }

        /**
         * Handles worker completion on the event thread.
         */
        @Override
        protected void done() {
            if (liveEngineWorker != this) {
                return;
            }
            liveEngineWorker = null;
            if (shouldStop() || !liveExternalEngineEnabled) {
                return;
            }
            try {
                get();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                handleLiveAnalysisFailure(ex);
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                handleLiveAnalysisFailure(cause instanceof Exception failure ? failure : ex);
            }
        }
    }

    /**
     * Snapshot of one live-analysis request.
     *
     * @param id request id
     * @param position position snapshot
     * @param whiteToMove true when White is to move
     * @param protocolPath protocol TOML path
     * @param multipv requested MultiPV count
     * @param threads requested engine thread count
     * @param hash optional hash size
     */
    private record LiveAnalysisRequest(
            long id,
            Position position,
            boolean whiteToMove,
            String protocolPath,
            int multipv,
            int threads,
            Integer hash) {
    }

    /**
     * Streamed live-analysis UI update.
     *
     * @param requestId request id
     * @param whiteToMove true when White was to move
     * @param output latest best output
     * @param bestMove latest best move
     * @param status status-only update
     */
    private record LiveAnalysisUpdate(
            long requestId,
            boolean whiteToMove,
            Output output,
            short bestMove,
            String status) {

        /**
         * Creates a status-only update.
         *
         * @param status status text
         * @return update
         */
        private static LiveAnalysisUpdate status(String status) {
            return new LiveAnalysisUpdate(0, false, null, Move.NO_MOVE, status);
        }

        /**
         * Creates an analysis update from the current analysis buffer.
         *
         * @param request source request
         * @param analysis analysis buffer
         * @return update
         */
        private static LiveAnalysisUpdate analysis(LiveAnalysisRequest request, Analysis analysis) {
            Output output = analysis.getBestOutput();
            return new LiveAnalysisUpdate(
                    request.id(),
                    request.whiteToMove(),
                    output == null ? null : new Output(output),
                    analysis.getBestMove(),
                    null);
        }
    }

    /**
     * Plays a move on the board.
     *
     * @param move move
     */
    private void playMove(short move) {
        if (currentPosition == null) {
            return;
        }
        if (!isLegalMove(currentPosition, move)) {
            showError("Illegal move", Move.toString(move) + " is not legal in the current position.");
            return;
        }
        Position before = currentPosition.copy();
        Position next = currentPosition.copy();
        next.play(move);
        gameModel.append(before, move, next);
        showGamePly(gameModel.currentPly());
    }

    /**
     * Updates legal moves.
     */
    private void updateMoves() {
        visibleMoves = movesModel.setPosition(currentPosition);
    }

    /**
     * Updates status label.
     */
    private void updateStatus() {
        if (currentPosition == null) {
            statusLabel.setText("");
            return;
        }
        statusLabel.setText((currentPosition.isWhiteToMove() ? "White" : "Black")
                + " to move  |  " + WorkbenchPositionText.status(currentPosition)
                + "  |  legal moves " + visibleMoves.length);
    }

    /**
     * Shows a game ply on the board and in dependent panels.
     *
     * @param ply ply to show
     */
    private void showGamePly(int ply) {
        gameModel.jumpToPly(ply);
        setPosition(gameModel.currentPosition(), gameModel.currentLastMove());
        selectCurrentGameRow();
    }

    /**
     * Moves backward or forward in the current game line.
     *
     * @param delta ply delta
     */
    private void navigateGame(int delta) {
        jumpGameTo(gameModel.currentPly() + delta);
    }

    /**
     * Jumps to a game ply.
     *
     * @param ply target ply
     */
    private void jumpGameTo(int ply) {
        showGamePly(Math.max(0, Math.min(ply, gameModel.lastPly())));
    }

    /**
     * Selects the table row for the current game ply.
     */
    private void selectCurrentGameRow() {
        int row = gameModel.currentRow();
        if (row < 0) {
            gameTable.clearSelection();
            return;
        }
        gameTable.getSelectionModel().setSelectionInterval(row, row);
        java.awt.Rectangle target = gameTable.getCellRect(row, 0, true);
        java.awt.Rectangle visible = gameTable.getVisibleRect();
        if (!visible.contains(target.x, target.y)
                || !visible.contains(target.x, target.y + target.height - 1)) {
            gameTable.scrollRectToVisible(target);
        }
    }

    /**
     * Updates game-line status text.
     */
    private void updateGameState() {
        gameStateLabel.setText("Ply " + gameModel.currentPly() + " / " + gameModel.lastPly()
                + (gameModel.canBack() ? "  |  back" : "")
                + (gameModel.canForward() ? "  |  forward" : ""));
    }

    /**
     * Tests whether a move is legal in a position.
     *
     * @param position position
     * @param move encoded move
     * @return true when legal
     */
    private static boolean isLegalMove(Position position, short move) {
        MoveList moves = position.legalMoves();
        for (int i = 0; i < moves.size(); i++) {
            if (moves.raw(i) == move) {
                return true;
            }
        }
        return false;
    }

    /**
     * Updates tags on a background worker.
     */
    private void updateTagsAsync() {
        if (tagWorker != null && !tagWorker.isDone()) {
            tagWorker.cancel(true);
        }
        tagModel.clear();
        tagModel.addElement("calculating...");
        Position snapshot = currentPosition.copy();
        long requestId = ++tagRequestId;
        tagWorker = new SwingWorker<>() {
            /**
             * Computes static tags away from the event-dispatch thread.
             *
             * @return computed tags
             */
            @Override
            protected List<String> doInBackground() {
                return Generator.tags(snapshot);
            }

            /**
             * Updates the tag list after background tagging completes.
             * Stale results from superseded requests are dropped via
             * {@link #tagRequestId}.
             */
            @Override
            protected void done() {
                if (isCancelled() || requestId != tagRequestId) {
                    return;
                }
                try {
                    tagModel.clear();
                    List<String> computedTags = get();
                    for (String tag : computedTags) {
                        tagModel.addElement(tag);
                    }
                    session.updateTags(computedTags);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    showTaggingError(ex);
                } catch (ExecutionException ex) {
                    showTaggingError(ex);
                }
            }

            /**
             * Shows a tagging failure in the tag list.
             *
             * @param ex failure
             */
            private void showTaggingError(Exception ex) {
                if (requestId != tagRequestId) {
                    return;
                }
                tagModel.clear();
                String message = ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage();
                tagModel.addElement("tagging failed: " + (message == null ? ex.getClass().getSimpleName() : message));
            }
        };
        tagWorker.execute();
    }

    /**
     * Monotonic identifier for in-flight tag requests; checked in
     * {@code done()} so a slow worker cannot overwrite the latest tags.
     */
    private long tagRequestId;

    /**
     * Runs built-in search.
     */
    private void runBuiltInSearch() {
        runCommand(List.of("engine", "builtin", "--fen", currentFen(), "--depth", depthValue(), "--format", "summary"),
                null);
    }

    /**
     * Runs UCI bestmove.
     */
    private void runBestMove() {
        try {
            runCommand(buildBestMoveArgs(), null);
        } catch (IllegalArgumentException ex) {
            showError("Engine settings", ex.getMessage());
        }
    }

    /**
     * Runs UCI analysis.
     */
    private void runAnalyze() {
        try {
            runCommand(buildAnalyzeArgs(), null);
        } catch (IllegalArgumentException ex) {
            showError("Engine settings", ex.getMessage());
        }
    }

    /**
     * Runs a quick external-engine smoke test.
     */
    private void runEngineSmoke() {
        try {
            runCommand(buildEngineSmokeArgs(), null);
        } catch (IllegalArgumentException ex) {
            showError("Engine settings", ex.getMessage());
        }
    }

    /**
     * Runs CLI config validation.
     */
    private void runConfigValidate() {
        runCommand(List.of("config", "validate"), null);
    }

    /**
     * Runs the {@code doctor} environment self-test.
     */
    private void runDoctor() {
        runCommand(List.of("doctor"), null);
    }

    /**
     * Runs every environment-health check (config validate, doctor, engine
     * smoke) one after another. {@link #runCommand} only allows one foreground
     * command at a time, so the checks are queued and the completion callback
     * advances the queue — keeping the single-command contract intact while
     * still giving the dashboard a one-click "check everything" entry point.
     */
    private void runAllHealthChecks() {
        if (runningCommand != null && runningCommand.isRunning()) {
            showWarning("Command running",
                    "Stop the current command before running the health checks.");
            return;
        }
        healthCheckQueue.clear();
        healthCheckQueue.add(List.of("config", "validate"));
        healthCheckQueue.add(List.of("doctor"));
        try {
            healthCheckQueue.add(buildEngineSmokeArgs());
        } catch (IllegalArgumentException ex) {
            // External engine not configured — skip the smoke test silently;
            // the config + doctor checks still run.
            appendConsole("Engine smoke skipped: " + ex.getMessage() + System.lineSeparator());
        }
        runNextHealthCheck();
    }

    /**
     * Launches the next queued health check, if any.
     */
    private void runNextHealthCheck() {
        List<String> next = healthCheckQueue.poll();
        if (next != null) {
            runCommand(next, null);
        }
    }

    /**
     * Restores external-engine fields to config-backed defaults.
     */
    private void resetEngineSettings() {
        engineProtocolField.setText(Config.getProtocolPath());
        engineNodesField.setText("");
        engineHashField.setText("");
        saveEngineSettings();
        requestCommandPreviews();
        requestEvalUpdate();
    }

    /**
     * Builds a best-move command from current workbench engine settings.
     *
     * @return command args
     */
    private List<String> buildBestMoveArgs() {
        List<String> args = new ArrayList<>(List.of("engine", "bestmove",
                "--fen", currentFen(),
                "--format", "both",
                "--max-duration", durationValue()));
        appendEngineSettingsArgs(args, true, true, true);
        return List.copyOf(args);
    }

    /**
     * Builds an analysis command from current workbench engine settings.
     *
     * @return command args
     */
    private List<String> buildAnalyzeArgs() {
        List<String> args = new ArrayList<>(List.of("engine", "analyze",
                "--fen", currentFen(),
                "--multipv", multipvValue(),
                "--max-duration", durationValue()));
        appendEngineSettingsArgs(args, true, true, true);
        return List.copyOf(args);
    }

    /**
     * Builds the lightweight eval-bar command.
     *
     * @param fen position FEN
     * @return command args
     */
    private List<String> buildEvalBarArgs(String fen) {
        List<String> args = new ArrayList<>(List.of("engine", "analyze",
                "--fen", fen,
                "--multipv", "1",
                "--max-duration", EVAL_BAR_DURATION));
        appendEngineSettingsArgs(args, true, true, true);
        return List.copyOf(args);
    }

    /**
     * Builds a quick UCI smoke-test command.
     *
     * @return command args
     */
    private List<String> buildEngineSmokeArgs() {
        List<String> args = new ArrayList<>(List.of("engine", "uci-smoke",
                "--nodes", "1",
                "--max-duration", durationValue()));
        appendEngineSettingsArgs(args, false, true, true);
        return List.copyOf(args);
    }

    /**
     * Appends shared external-engine settings to one command.
     *
     * @param args target args
     * @param includeNodes whether to include max nodes
     * @param includeThreads whether to include engine threads
     * @param includeHash whether to include hash
     */
    private void appendEngineSettingsArgs(List<String> args, boolean includeNodes, boolean includeThreads,
            boolean includeHash) {
        addOptionalTextArg(args, "--protocol-path", engineProtocolField);
        if (includeNodes) {
            addOptionalPositiveIntegerArg(args, "--max-nodes", engineNodesField);
        }
        if (includeThreads) {
            args.add("--threads");
            args.add(threadsValue());
        }
        if (includeHash) {
            addOptionalPositiveIntegerArg(args, "--hash", engineHashField);
        }
    }

    /**
     * Runs tag command.
     */
    private void runTagsCommand() {
        runCommand(List.of("fen", "tags", "--fen", currentFen()), null);
    }

    /**
     * Runs perft.
     */
    private void runPerft() {
        runCommand(List.of("engine", "perft", "--fen", currentFen(), "--depth", depthValue(), "--threads",
                threadsValue()), null);
    }

    /**
     * Runs the selected curated command template.
     */
    private void runSelectedTemplate() {
        try {
            updateCommandPreviews();
            runCommand(selectedTemplateArgs(), null);
        } catch (IllegalArgumentException ex) {
            showError("Template failed", ex.getMessage());
        }
    }

    /**
     * Copies the current command-builder preview after flushing pending edits.
     */
    private void copyBuiltCommand() {
        updateCommandPreviews();
        copyText(commandField.getText());
    }

    /**
     * Runs one command.
     *
     * @param args args
     * @param stdin stdin
     */
    private void runCommand(List<String> args, String stdin) {
        if (args == null || args.isEmpty()) {
            showWarning("No command", "Select a workflow first.");
            return;
        }
        if (runningCommand != null && runningCommand.isRunning()) {
            showWarning("Command running", "Stop the current command before starting another one.");
            return;
        }
        appendConsole("\n$ " + WorkbenchCommandRunner.displayCommand(args) + "\n");
        setCommandState("Running");
        // Track the run as a dashboard job so the recent-jobs table reflects
        // status, duration, exit code and a parsed result.
        WorkbenchJob job = session.jobs().create(args);
        session.jobs().markRunning(job);
        runningJob = job;
        runningJobStartMillis = System.currentTimeMillis();
        runningCommand = WorkbenchCommandRunner.run(args, stdin, this::appendConsole, result -> {
            appendConsole("[exit " + result.exitCode() + ", " + result.millis() + " ms]\n");
            setCommandState("Exit " + result.exitCode());
            session.jobs().markFinished(job, result.exitCode(), result.output(), result.millis());
            updateHealthFromCommand(args, result.exitCode());
            List<Path> artifacts = List.of();
            if (result.exitCode() == 0) {
                artifacts = recordArtifactsFromCommand(args);
            }
            persistRunManifest(job, artifacts, stdin);
            runningJob = null;
            maybeHighlightMove(args, result.output());
            if (!healthCheckQueue.isEmpty()) {
                SwingUtilities.invokeLater(this::runNextHealthCheck);
            }
        }, ex -> {
            setCommandState("Stopped");
            appendConsole("[stopped] " + Objects.toString(ex.getMessage(), ex.getClass().getSimpleName()) + "\n");
            // stopCommand() may already have marked the job cancelled; only
            // record a failure when the job is still in a non-terminal state.
            if (!job.status().isTerminal()) {
                session.jobs().markFailed(job,
                        Objects.toString(ex.getMessage(), ex.getClass().getSimpleName()),
                        System.currentTimeMillis() - runningJobStartMillis);
            }
            persistRunManifest(job, List.of(), stdin);
            updateHealthFailedFromCommand(args);
            runningJob = null;
            healthCheckQueue.clear();
        });
    }

    /**
     * Updates the session's health snapshot after a health-check command
     * finishes with an exit code. Uses only the structured exit code, never
     * the command's prose output.
     *
     * @param args finished command arguments
     * @param exitCode process exit code
     */
    private void updateHealthFromCommand(List<String> args, int exitCode) {
        WorkbenchHealthSnapshot.Check check = WorkbenchHealthSnapshot.Check.ofExitCode(exitCode);
        String command = String.join(" ", args);
        WorkbenchHealthSnapshot health = session.health();
        if (command.startsWith("config validate")) {
            session.updateHealth(health.withConfig(check));
        } else if (command.equals("doctor") || command.startsWith("doctor ")) {
            session.updateHealth(health.withDoctor(check));
        } else if (command.contains("uci-smoke")) {
            session.updateHealth(health.withEngineSmoke(check));
        }
    }

    /**
     * Marks the matching health check failed when a health-check command could
     * not even produce an exit code (process failed to launch or was stopped).
     *
     * @param args attempted command arguments
     */
    private void updateHealthFailedFromCommand(List<String> args) {
        updateHealthFromCommand(args, 1);
    }

    /**
     * Records any artifact files a finished command produced into the session
     * artifact index. Reads only the command arguments the workbench itself
     * built — it scans them for output-file tokens and keeps the ones that now
     * exist on disk — so it never parses arbitrary command output.
     *
     * @param args finished command arguments
     */
    private List<Path> recordArtifactsFromCommand(List<String> args) {
        List<Path> artifacts = new ArrayList<>();
        if (args == null) {
            return artifacts;
        }
        for (int i = 0; i < args.size(); i++) {
            String token = args.get(i);
            if (isArtifactOutputFlag(token) && i + 1 < args.size()) {
                addArtifactIfPresent(artifacts, args.get(++i));
                continue;
            }
            if (isArtifactInputFlag(token) && i + 1 < args.size()) {
                i++;
                continue;
            }
            if (token == null || token.startsWith("-") || !looksLikeArtifactPath(token)) {
                continue;
            }
            addArtifactIfPresent(artifacts, token);
        }
        return List.copyOf(artifacts);
    }

    /**
     * Returns whether a command flag names a generated output path.
     *
     * @param flag command flag
     * @return true when the next token is an output path
     */
    private static boolean isArtifactOutputFlag(String flag) {
        return "--output".equals(flag)
                || "-o".equals(flag)
                || "--output-dir".equals(flag)
                || "--pdf-output".equals(flag)
                || "--cover-output".equals(flag);
    }

    /**
     * Returns whether a command flag names an input/config path.
     *
     * @param flag command flag
     * @return true when the next token should not be recorded as an output
     */
    private static boolean isArtifactInputFlag(String flag) {
        return "--input".equals(flag)
                || "-i".equals(flag)
                || "--pgn".equals(flag)
                || "--suite".equals(flag)
                || "--protocol-path".equals(flag)
                || "--weights".equals(flag)
                || "--pdf".equals(flag);
    }

    /**
     * Returns whether a free command token looks like an artifact path.
     *
     * @param token command token
     * @return true when the token has an output-like extension
     */
    private static boolean looksLikeArtifactPath(String token) {
        if (token == null) {
            return false;
        }
        String lower = token.toLowerCase(Locale.ROOT);
        return lower.endsWith(".pdf") || lower.endsWith(".jsonl") || lower.endsWith(".csv")
                || lower.endsWith(".png") || lower.endsWith(".pgn");
    }

    /**
     * Records an artifact path when it exists.
     *
     * @param artifacts mutable artifact list
     * @param token path token
     */
    private void addArtifactIfPresent(List<Path> artifacts, String token) {
        try {
            Path path = Path.of(token);
            if (Files.exists(path)) {
                Path artifact = path.toAbsolutePath().normalize();
                artifacts.add(artifact);
                session.artifacts().add(artifact);
            }
        } catch (java.nio.file.InvalidPathException ex) {
            // Not a usable path token — ignore.
        }
    }

    /**
     * Persists the full plain-text log for a finished, failed, or cancelled
     * command run, then records that log in the dashboard artifact list.
     *
     * @param job job to persist
     * @return log path, or null when writing failed
     */
    private Path persistRunLog(WorkbenchJob job) {
        if (job == null) {
            return null;
        }
        if (job.logPath() != null) {
            return job.logPath();
        }
        try {
            Path log = WorkbenchRunLog.write(job, Path.of(""));
            job.recordLog(log);
            session.artifacts().add(log);
            return log;
        } catch (IOException ex) {
            appendConsole("Run log failed: " + ex.getMessage() + System.lineSeparator());
            return null;
        }
    }

    /**
     * Persists a full log and JSON manifest for a finished, failed, or
     * cancelled command run, then records them in the dashboard artifact list.
     *
     * @param job job to persist
     * @param artifacts output artifacts detected for the job
     * @param stdin optional stdin payload
     */
    private void persistRunManifest(WorkbenchJob job, List<Path> artifacts, String stdin) {
        if (job == null || job.manifestPath() != null) {
            return;
        }
        persistRunLog(job);
        try {
            Path manifest = WorkbenchRunManifest.write(job, artifacts, stdin, Path.of(""));
            job.recordManifest(manifest, artifacts);
            session.artifacts().add(manifest);
        } catch (IOException ex) {
            appendConsole("Run manifest failed: " + ex.getMessage() + System.lineSeparator());
        }
    }

    /**
     * Opens a job's persisted run manifest through the desktop shell.
     *
     * @param job job whose manifest should be opened
     */
    private void openManifestForJob(WorkbenchJob job) {
        if (job == null || job.manifestPath() == null) {
            showWarning("Run manifest", "No manifest has been written for this job yet.");
            return;
        }
        openPath(job.manifestPath(), "Run manifest");
    }

    /**
     * Opens a job's persisted full log through the desktop shell.
     *
     * @param job job whose log should be opened
     */
    private void openLogForJob(WorkbenchJob job) {
        if (job == null) {
            showWarning("Run log", "No job is selected.");
            return;
        }
        Path log = job.logPath();
        if (log == null && job.status().isTerminal()) {
            log = persistRunLog(job);
        }
        if (log == null) {
            showWarning("Run log", "No log has been written for this job yet.");
            return;
        }
        openPath(log, "Run log");
    }

    /**
     * Opens the workbench log directory.
     */
    private void openLogsDirectory() {
        try {
            Path dir = WorkbenchRunLog.DEFAULT_DIR.toAbsolutePath().normalize();
            Files.createDirectories(dir);
            openPath(dir, "Workbench logs");
        } catch (IOException ex) {
            showError("Workbench logs", "Failed to open log directory: " + ex.getMessage());
        }
    }

    /**
     * Opens a path through the desktop shell.
     *
     * @param path path to open
     * @param title dialog title
     */
    private void openPath(Path path, String title) {
        if (path == null) {
            showWarning(title, "No file is available.");
            return;
        }
        if (!Files.exists(path)) {
            showWarning(title, "File does not exist: " + path);
            return;
        }
        if (!Desktop.isDesktopSupported()) {
            showWarning(title, "Desktop integration is not supported.");
            return;
        }
        try {
            Desktop.getDesktop().open(path.toFile());
        } catch (IOException ex) {
            showError(title, "Failed to open file: " + ex.getMessage());
        }
    }

    /**
     * Stops a running command.
     */
    private void stopCommand() {
        if (runningCommand != null && runningCommand.isRunning()) {
            runningCommand.cancel();
            if (runningJob != null) {
                session.jobs().markCancelled(runningJob,
                        System.currentTimeMillis() - runningJobStartMillis);
                persistRunManifest(runningJob, List.of(), null);
                runningJob = null;
            }
            healthCheckQueue.clear();
        }
    }

    /**
     * Runs selected batch task.
     */
    private void runBatch() {
        BatchTask task = (BatchTask) batchTaskCombo.getSelectedItem();
        if (task == null) {
            return;
        }
        try {
            Path input = null;
            if (task.usesFenInput()) {
                WorkbenchFenInput.Summary validation = validateBatchFenInput(batchInput.getText());
                refreshBatchInputStatus();
                if (validation.rows() == 0) {
                    showError("Batch input", "Add at least one FEN before running this batch task.");
                    return;
                }
                if (validation.hasError()) {
                    showError("Batch input", "Line " + validation.firstErrorLine() + ": "
                            + validation.firstError());
                    return;
                }
                input = Files.createTempFile("crtk-workbench-fens-", ".txt");
                input.toFile().deleteOnExit();
                Files.writeString(input, batchInput.getText(), StandardCharsets.UTF_8);
            }
            runCommand(task.build(input, templateContext()), null);
        } catch (IOException ex) {
            showError("Batch input failed", ex.getMessage());
        }
    }

    /**
     * Loads game text as PGN or as a plain SAN/UCI line.
     *
     * @param text raw game text
     */
    private void loadGameText(String text) {
        if (text == null || text.isBlank()) {
            showError("Game input", "Paste PGN, SAN, or UCI moves first.");
            return;
        }
        WorkbenchFenInput.Scan fenScan = WorkbenchFenInput.firstFenOrFailure(text);
        if (fenScan.fen() != null) {
            startNewGame(fenScan.fen());
            return;
        }
        List<Game> games = Pgn.parseGames(text);
        if (!games.isEmpty()) {
            Game game = chooseGame(games);
            if (game == null) {
                return;
            }
            try {
                loadGame(game);
                return;
            } catch (IllegalArgumentException ex) {
                if (looksLikePgn(text)) {
                    showError("PGN import failed", ex.getMessage());
                    return;
                }
            }
        }
        try {
            loadMoveLine(text);
        } catch (IllegalArgumentException ex) {
            String detail = ex.getMessage();
            if (fenScan.firstError() != null && !looksLikePgn(text)) {
                detail = (detail == null ? "" : detail + System.lineSeparator())
                        + "FEN parse hint: " + fenScan.firstError();
            }
            showError("Line import failed", detail);
        }
    }

    /**
     * Loads a PGN or move-line file.
     */
    private void loadGameFile() {
        JFileChooser chooser = WorkbenchFileDialogs.createFileChooser(null, null,
                new FileNameExtensionFilter("PGN, text, or FEN files", "pgn", "txt", "fen"));
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        java.nio.file.Path path = chooser.getSelectedFile().toPath();
        runAsync(
                () -> Files.readString(path, StandardCharsets.UTF_8),
                this::loadGameText,
                ex -> showError("Load file failed", ex.getMessage()));
    }

    /**
     * Saves the current line as PGN.
     */
    private void savePgnFile() {
        JFileChooser chooser = WorkbenchFileDialogs.createFileChooser(null, new File("workbench-game.pgn"),
                new FileNameExtensionFilter("PGN file", "pgn"));
        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = WorkbenchFileDialogs.ensureExtension(chooser.getSelectedFile(), ".pgn");
        String contents = gameModel.pgn() + System.lineSeparator();
        runAsync(
                () -> {
                    Files.writeString(file.toPath(), contents, StandardCharsets.UTF_8);
                    return file;
                },
                saved -> {
                    appendConsole("Saved PGN to " + saved + "\n");
                    toast(WorkbenchToast.Kind.SUCCESS, "Saved PGN to " + saved.getName());
                },
                ex -> showError("Save PGN failed", ex.getMessage()));
    }

    /**
     * Runs a possibly blocking task off the EDT and dispatches the result back on the EDT.
     *
     * @param <T> result type
     * @param task background task
     * @param onDone EDT callback for success
     * @param onError EDT callback for failure
     */
    private static <T> void runAsync(Callable<T> task,
            Consumer<T> onDone, Consumer<Exception> onError) {
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return task.call();
            }

            @Override
            protected void done() {
                try {
                    onDone.accept(get());
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    onError.accept(cause instanceof Exception inner ? inner : ex);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    onError.accept(ex);
                }
            }
        }.execute();
    }

    /**
     * Loads a parsed PGN game mainline.
     *
     * @param game parsed game
     */
    private void loadGame(Game game) {
        Position start = game.getStartPosition() == null ? new Position(Game.STANDARD_START_FEN)
                : game.getStartPosition().copy();
        List<Short> line = parseGameMainline(start, game.getMainline());
        if (line.isEmpty()) {
            throw new IllegalArgumentException("No legal mainline moves found.");
        }
        gameModel.loadLine(start, line);
        session.clearEvalHistory();
        showGamePly(gameModel.lastPly());
        appendConsole("Loaded PGN mainline with " + gameModel.lastPly() + " plies\n");
    }

    /**
     * Loads a plain SAN or UCI move line from the current position.
     *
     * @param text raw line text
     */
    private void loadMoveLine(String text) {
        Position start = currentPosition == null ? new Position(Game.STANDARD_START_FEN) : currentPosition.copy();
        List<String> tokens = moveTokens(text);
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("No moves found.");
        }
        List<Short> line = new ArrayList<>();
        Position cursor = start.copy();
        for (String token : tokens) {
            short move = parseMoveToken(cursor, token);
            line.add(move);
            cursor.play(move);
        }
        gameModel.loadLine(start, line);
        showGamePly(gameModel.lastPly());
        appendConsole("Loaded move line with " + gameModel.lastPly() + " plies\n");
    }

    /**
     * Parses a PGN mainline into encoded moves.
     *
     * @param start start position
     * @param node first PGN node
     * @return encoded moves
     */
    private static List<Short> parseGameMainline(Position start, Game.Node node) {
        List<Short> line = new ArrayList<>();
        Position cursor = start.copy();
        Game.Node current = node;
        while (current != null) {
            short move = SAN.fromAlgebraic(cursor, current.getSan());
            line.add(move);
            cursor.play(move);
            current = current.getNext();
        }
        return line;
    }

    /**
     * Chooses a PGN game when multiple games are present.
     *
     * @param games parsed games
     * @return selected game, or null
     */
    private Game chooseGame(List<Game> games) {
        if (games.size() == 1) {
            return games.get(0);
        }
        String[] labels = new String[games.size()];
        for (int i = 0; i < games.size(); i++) {
            labels[i] = gameLabel(games.get(i), i + 1);
        }
        JList<String> list = new JList<>(labels);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(0);
        list.setVisibleRowCount(Math.min(15, labels.length));
        WorkbenchTheme.list(list);
        JScrollPane scroll = WorkbenchUi.scroll(list);
        scroll.setPreferredSize(new Dimension(420, 320));
        int result = showConfirmDialog(this, scroll, "Select PGN game");
        if (result != JOptionPane.OK_OPTION) {
            return null;
        }
        int index = list.getSelectedIndex();
        return games.get(Math.max(0, Math.min(index, games.size() - 1)));
    }

    /**
     * Builds a compact PGN game label.
     *
     * @param game game
     * @param index one-based index
     * @return label
     */
    private static String gameLabel(Game game, int index) {
        String white = game.getTags().getOrDefault("White", "?");
        String black = game.getTags().getOrDefault("Black", "?");
        String event = game.getTags().getOrDefault("Event", "PGN");
        return index + ". " + event + " | " + white + " vs " + black + " | " + game.getResult();
    }

    /**
     * Returns whether text appears to be structured PGN.
     *
     * @param text raw text
     * @return true when PGN tags or comments are present
     */
    private static boolean looksLikePgn(String text) {
        return text.contains("[") || text.contains("{") || text.contains("(");
    }

    /**
     * Extracts move tokens from a plain line.
     *
     * @param text raw line
     * @return move tokens
     */
    private static List<String> moveTokens(String text) {
        String cleaned = text.replaceAll("\\{[^}]*}", " ")
                .replaceAll("\\([^)]*\\)", " ")
                .replace('\n', ' ')
                .replace('\r', ' ');
        List<String> tokens = new ArrayList<>();
        for (String raw : cleaned.split("\\s+")) {
            String token = normalizeLineToken(raw);
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    /**
     * Returns the first valid FEN line from pasted text, or {@code null} when
     * no candidate line parses. Retained for the reflective regression test.
     *
     * @param text raw text
     * @return FEN or null
     */
    private static String firstFenLine(String text) {
        return WorkbenchFenInput.firstFenLine(text);
    }

    /**
     * Validates every non-empty batch FEN row.
     *
     * @param text raw input
     * @return validation summary
     */
    private static WorkbenchFenInput.Summary validateBatchFenInput(String text) {
        return WorkbenchFenInput.validateBatchFenInput(text);
    }

    /**
     * Returns a short FEN label for compact previews.
     *
     * @param fen full FEN
     * @return piece placement plus side to move when available
     */
    private static String compactFenPreview(String fen) {
        if (fen == null || fen.isBlank()) {
            return "";
        }
        String[] parts = fen.trim().split("\\s+");
        return parts.length > 1 ? parts[0] + " " + parts[1] : parts[0];
    }

    /**
     * Normalizes one plain-line token.
     *
     * @param raw raw token
     * @return move token or blank
     */
    private static String normalizeLineToken(String raw) {
        if (raw == null) {
            return "";
        }
        String token = raw.trim();
        if (token.isEmpty() || token.startsWith("$") || token.matches("\\.+") || isResultToken(token)) {
            return "";
        }
        token = token.replaceFirst("^\\d+\\.{1,3}", "");
        if (token.isEmpty() || token.matches("\\d+\\.{1,3}")) {
            return "";
        }
        return token;
    }

    /**
     * Returns whether a token is a game result.
     *
     * @param token token
     * @return true for result tokens
     */
    private static boolean isResultToken(String token) {
        return "1-0".equals(token) || "0-1".equals(token) || "1/2-1/2".equals(token) || "*".equals(token);
    }

    /**
     * Parses one SAN or UCI move token.
     *
     * @param position position before the move
     * @param token move token
     * @return encoded move
     */
    private static short parseMoveToken(Position position, String token) {
        String uci = token.toLowerCase(Locale.ROOT);
        if (Move.isMove(uci)) {
            short move = Move.parse(uci);
            if (isLegalMove(position, move)) {
                return move;
            }
        }
        return SAN.fromAlgebraic(position, token);
    }

    /**
     * Appends console text on the EDT.
     *
     * @param text text
     */
    private void appendConsole(String text) {
        if (SwingUtilities.isEventDispatchThread()) {
            console.appendOutput(text);
        } else {
            SwingUtilities.invokeLater(() -> appendConsole(text));
        }
    }

    /**
     * Updates the command execution state label.
     *
     * @param state state text
     */
    private void setCommandState(String state) {
        commandStateLabel.setText(state);
        setStatusBarEngine(state == null || state.isBlank() ? "idle" : state);
    }

    /**
     * Installs command templates.
     */
    private void installTemplates() {
        commandTemplates = WorkbenchCommandTemplates.commandTemplates();
        commandPicker.removeAll();
        commandButtons.clear();
        ButtonGroup group = new ButtonGroup();
        for (int i = 0; i < commandTemplates.size(); i++) {
            CommandTemplate template = commandTemplates.get(i);
            javax.swing.JToggleButton tab = new javax.swing.JToggleButton(template.name());
            WorkbenchTheme.commandTab(tab);
            tab.setSelected(i == selectedCommandIndex);
            int index = i;
            tab.addActionListener(event -> selectCommandTemplate(index));
            group.add(tab);
            commandButtons.add(tab);
            commandPicker.add(tab);
        }
        commandPicker.revalidate();
        commandPicker.repaint();
    }

    /**
     * Selects a command template by index and refreshes the builder.
     *
     * @param index template index
     */
    private void selectCommandTemplate(int index) {
        if (index < 0 || index >= commandTemplates.size()) {
            return;
        }
        selectedCommandIndex = index;
        for (int i = 0; i < commandButtons.size(); i++) {
            commandButtons.get(i).setSelected(i == index);
        }
        updateCommandOptions();
        updateBuiltCommand();
    }

    /**
     * Installs batch tasks.
     */
    private void installBatchTasks() {
        batchTaskCombo.setModel(WorkbenchCommandTemplates.batchModel());
    }

    /**
     * Returns whether option-row text matches all filter tokens.
     *
     * @param query raw query
     * @param values row values
     * @return true when every token appears somewhere in the row text
     */
    static boolean optionFilterMatches(String query, String... values) {
        if (query == null || query.isBlank()) {
            return true;
        }
        StringBuilder haystack = new StringBuilder();
        for (String value : values) {
            if (value != null) {
                haystack.append(value).append(' ');
            }
        }
        String normalized = haystack.toString().toLowerCase(Locale.ROOT);
        for (String token : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            if (!token.isBlank() && !normalized.contains(token)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Configures the game history table.
     */
    private void configureGameTable() {
        gameTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        WorkbenchTheme.table(gameTable, 29);
        gameTable.getColumnModel().getColumn(0).setPreferredWidth(64);
        gameTable.getColumnModel().getColumn(1).setPreferredWidth(120);
        gameTable.getColumnModel().getColumn(2).setPreferredWidth(110);
        gameTable.getColumnModel().getColumn(3).setPreferredWidth(520);
        gameTable.setPreferredScrollableViewportSize(new Dimension(780, 180));
        gameTable.addMouseListener(new MouseAdapter() {
            /**
             * Jumps to a move when a history row is double-clicked.
             *
             * @param event mouse event
             */
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    int row = gameTable.getSelectedRow();
                    if (row >= 0) {
                        jumpGameTo(gameModel.plyForRow(row));
                    }
                }
            }
        });
    }

    /**
     * Updates command options for the selected template.
     */
    private void updateCommandOptions() {
        CommandTemplate template = selectedCommandTemplate();
        if (template == null) {
            return;
        }
        commandForm.setTemplate(template, templateContext());
    }

    /**
     * Resets the selected command to its default flags and values.
     */
    private void resetSelectedTemplate() {
        commandForm.resetDefaults(templateContext());
        updateBuiltCommand();
    }

    /**
     * Returns the selected command to its default enabled option set.
     */
    private void clearOptionalTemplateOptions() {
        commandForm.clearOptional();
        updateBuiltCommand();
    }

    /**
     * Updates batch-only controls for the selected task.
     */
    private void updateBatchControls() {
        BatchTask task = (BatchTask) batchTaskCombo.getSelectedItem();
        if (task == null) {
            return;
        }
        batchInput.setEditable(task.usesFenInput());
        if (addCurrentFenButton != null) {
            addCurrentFenButton.setEnabled(task.usesFenInput());
        }
        if (clearBatchInputButton != null) {
            clearBatchInputButton.setEnabled(task.usesFenInput());
        }
        rebuildOptionsPanel(batchOptionsPanel, task.controls(), batchDurationField, batchDepthSpinner,
                batchMultipvSpinner, batchThreadsSpinner);
        refreshBatchInputStatus();
    }

    /**
     * Queues a lightweight refresh of the batch FEN status line.
     */
    private void requestBatchInputStatusUpdate() {
        if (batchInputStatusUpdateQueued) {
            return;
        }
        batchInputStatusUpdateQueued = true;
        SwingUtilities.invokeLater(() -> {
            if (!batchInputStatusUpdateQueued) {
                return;
            }
            refreshBatchInputStatus();
        });
    }

    /**
     * Updates the batch FEN status line.
     */
    private void refreshBatchInputStatus() {
        batchInputStatusUpdateQueued = false;
        BatchTask task = (BatchTask) batchTaskCombo.getSelectedItem();
        String taskName = task == null ? "none" : task.name();
        if (task != null && !task.usesFenInput()) {
            batchInputStatus.setText("FEN list not used");
            batchInputStatus.setToolTipText("The selected batch task runs without FEN input.");
            batchInputStatus.setForeground(WorkbenchTheme.MUTED);
            session.updateBatch(taskName + " · no FEN input required");
            updatePublishCommand();
            return;
        }
        WorkbenchFenInput.Summary scan = validateBatchFenInput(batchInput.getText());
        if (scan.rows() == 0) {
            batchInputStatus.setText("No FEN rows");
            batchInputStatus.setToolTipText("Add one FEN per line.");
            batchInputStatus.setForeground(WorkbenchTheme.MUTED);
            session.updateBatch(taskName + " · no FEN rows");
        } else if (scan.hasError()) {
            batchInputStatus.setText(scan.rows() + " row" + (scan.rows() == 1 ? "" : "s")
                    + ", issue on line " + scan.firstErrorLine());
            batchInputStatus.setToolTipText(scan.firstError());
            batchInputStatus.setForeground(WorkbenchTheme.STATUS_WARNING_TEXT);
            session.updateBatch(taskName + " · " + scan.rows() + " rows, issue on line "
                    + scan.firstErrorLine());
        } else {
            batchInputStatus.setText(scan.validRows() + " FEN row" + (scan.validRows() == 1 ? "" : "s"));
            batchInputStatus.setToolTipText("Ready to run batch workflow.");
            batchInputStatus.setForeground(WorkbenchTheme.MUTED);
            session.updateBatch(taskName + " · " + scan.validRows() + " FEN row"
                    + (scan.validRows() == 1 ? "" : "s") + " ready");
        }
        updatePublishCommand();
    }

    /**
     * Rebuilds a workflow option panel with only the controls that matter.
     *
     * @param panel target panel
     * @param controls enabled workflow controls
     * @param durationField duration input
     * @param depthSpinner depth input
     * @param multipvSpinner MultiPV input
     * @param threadsSpinner thread-count input
     */
    private void rebuildOptionsPanel(JPanel panel, WorkflowControls controls, JTextField durationField,
            JSpinner depthSpinner, JSpinner multipvSpinner, JSpinner threadsSpinner) {
        panel.removeAll();
        if (controls.duration()) {
            panel.add(optionGroup("duration", durationField));
        }
        if (controls.depth()) {
            panel.add(optionGroup("depth", depthSpinner));
        }
        if (controls.multipv()) {
            panel.add(optionGroup("multipv", multipvSpinner));
        }
        if (controls.threads()) {
            panel.add(optionGroup("threads", threadsSpinner));
        }
        panel.revalidate();
        panel.repaint();
    }

    /**
     * Creates one compact option group.
     *
     * @param text label text
     * @param control option control
     * @return option group
     */
    private JComponent optionGroup(String text, JComponent control) {
        JPanel panel = transparentPanel(new BorderLayout(6, 0));
        control.setPreferredSize(new Dimension(120, 28));
        panel.add(label(text), BorderLayout.WEST);
        panel.add(control, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Updates command field from selected template.
     */
    private void updateBuiltCommand() {
        try {
            commandField.setText(WorkbenchCommandRunner.displayCommand(selectedTemplateArgs()));
        } catch (IllegalArgumentException ex) {
            commandField.setText("invalid template: " + ex.getMessage());
        }
        // Keep the command start visible rather than scrolling to the caret.
        commandField.setCaretPosition(0);
    }

    /**
     * Queues command, batch, and publishing preview refreshes.
     */
    private void requestCommandPreviews() {
        if (commandPreviewUpdateQueued) {
            return;
        }
        commandPreviewUpdateQueued = true;
        SwingUtilities.invokeLater(() -> {
            if (!commandPreviewUpdateQueued) {
                return;
            }
            updateCommandPreviews();
        });
    }

    /**
     * Updates command, batch, and publishing previews immediately.
     */
    private void updateCommandPreviews() {
        commandPreviewUpdateQueued = false;
        commandForm.refreshDynamicValues(templateContext());
        updateBuiltCommand();
        updateBatchCommand();
        updatePublishCommand();
    }

    /**
     * Builds arguments for the selected curated command template.
     *
     * @return command arguments
     */
    private List<String> selectedTemplateArgs() {
        CommandTemplate template = selectedCommandTemplate();
        if (template == null) {
            return List.of();
        }
        List<String> args = new ArrayList<>(template.baseArgs());
        args.addAll(commandForm.args());
        return List.copyOf(args);
    }

    /**
     * Returns the selected command template.
     *
     * @return selected template or null
     */
    private CommandTemplate selectedCommandTemplate() {
        if (selectedCommandIndex < 0 || selectedCommandIndex >= commandTemplates.size()) {
            return null;
        }
        return commandTemplates.get(selectedCommandIndex);
    }

    /**
     * Updates batch command preview.
     */
    private void updateBatchCommand() {
        BatchTask task = (BatchTask) batchTaskCombo.getSelectedItem();
        if (task == null) {
            return;
        }
        batchCommandField.setText(WorkbenchCommandRunner.displayCommand(
                task.build(WORKBENCH_FENS_PLACEHOLDER_PATH, templateContext())));
        batchCommandField.setCaretPosition(0);
    }

    /**
     * Updates the publishing command preview.
     */
    private void updatePublishCommand() {
        publishingPanel.updateCommand();
    }

    /**
     * Queues a publishing preview refresh after document edits settle.
     */
    private void requestPublishCommandUpdate() {
        publishingPanel.requestCommandUpdate();
    }

    /**
     * Runs the selected publishing workflow.
     */
    private void runPublishingCommand() {
        publishingPanel.runCommand();
    }

    /**
     * Builds template context.
     *
     * @return context
     */
    private TemplateContext templateContext() {
        return new TemplateContext(currentFen(), durationValue(), depthValue(), multipvValue(), threadsValue(),
                engineProtocolValue(), engineNodesValue(), engineHashValue());
    }

    /**
     * Synchronizes visible duration fields.
     *
     * @param source source duration field
     * @param targets target duration fields
     */
    private void syncDuration(JTextField source, JTextField... targets) {
        if (syncingDuration) {
            return;
        }
        syncingDuration = true;
        try {
            String value = source.getText();
            for (JTextField target : targets) {
                if (!Objects.equals(value, target.getText())) {
                    target.setText(value);
                }
            }
            requestCommandPreviews();
        } finally {
            syncingDuration = false;
        }
    }

    /**
     * Highlights first legal UCI move in output.
     *
     * @param output command output
     */
    private void maybeHighlightMove(List<String> args, String output) {
        board.setSuggestedMove(Move.NO_MOVE);
        if (!shouldShowSuggestedMove(args) || currentPosition == null || output == null) {
            return;
        }
        MoveList legal = currentPosition.legalMoves();
        Matcher matcher = UCI_PATTERN.matcher(output);
        while (matcher.find()) {
            String candidate = matcher.group();
            if (Move.isMove(candidate)) {
                short move = Move.parse(candidate);
                if (legal.contains(move)) {
                    board.setSuggestedMove(move);
                    return;
                }
            }
        }
    }

    /**
     * Returns whether a command output should be interpreted as a current-position
     * engine suggestion.
     *
     * @param args command arguments
     * @return true when a best-move arrow is appropriate
     */
    private static boolean shouldShowSuggestedMove(List<String> args) {
        if (args == null || args.size() < 2 || !"engine".equals(args.get(0))) {
            return false;
        }
        return switch (args.get(1)) {
            case "bestmove", "bestmove-uci", "bestmove-both", "analyze", "builtin" -> true;
            default -> false;
        };
    }

    /**
     * Parses the first engine evaluation emitted by {@code engine analyze}.
     *
     * @param output command output
     * @return parsed evaluation, or null when unavailable
     */
    static WorkbenchEngineEval parseEngineEval(String output) {
        return WorkbenchEngineEval.parse(output);
    }

    /**
     * Returns current FEN.
     *
     * @return FEN
     */
    private String currentFen() {
        return currentPosition == null ? Setup.getStandardStartFEN() : currentPosition.toString();
    }

    /**
     * Returns duration.
     *
     * @return duration string
     */
    private String durationValue() {
        String value = trimmed(analysisDurationField);
        return value.isEmpty() ? "2s" : value;
    }

    /**
     * Returns depth.
     *
     * @return depth string
     */
    private String depthValue() {
        return String.valueOf(depthModel.getValue());
    }

    /**
     * Returns multipv.
     *
     * @return multipv string
     */
    private String multipvValue() {
        return String.valueOf(multipvModel.getValue());
    }

    /**
     * Returns threads.
     *
     * @return thread count string
     */
    private String threadsValue() {
        return String.valueOf(threadsModel.getValue());
    }

    /**
     * Returns external-engine protocol path.
     *
     * @return protocol path or blank to use CLI config
     */
    private String engineProtocolValue() {
        return trimmed(engineProtocolField);
    }

    /**
     * Returns optional external-engine node budget.
     *
     * @return node budget or blank
     */
    private String engineNodesValue() {
        return trimmed(engineNodesField);
    }

    /**
     * Returns optional external-engine hash size.
     *
     * @return hash MB or blank
     */
    private String engineHashValue() {
        return trimmed(engineHashField);
    }

    /**
     * Copies text to clipboard.
     *
     * @param text text
     */
    private void copyText(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text == null ? "" : text),
                null);
        appendConsole("[copied]\n");
    }

    /**
     * Shows a warning in the workbench notification layer.
     *
     * @param title title
     * @param message message
     */
    private void showWarning(String title, String message) {
        showNotice(WorkbenchToast.Kind.WARNING, title, message);
    }

    /**
     * Shows an error in the workbench notification layer.
     *
     * @param title title
     * @param message message
     */
    private void showError(String title, String message) {
        showNotice(WorkbenchToast.Kind.ERROR, title, message);
    }

    /**
     * Shows an inline bottom notice without opening a modal dialog.
     *
     * @param kind severity
     * @param title fallback title
     * @param message primary message
     */
    private void showNotice(WorkbenchToast.Kind kind, String title, String message) {
        toast(kind, message == null || message.isBlank() ? title : message);
    }

}
