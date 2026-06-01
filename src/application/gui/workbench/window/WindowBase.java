package application.gui.workbench.window;

import application.Config;
import application.gui.workbench.Defaults;
import application.gui.workbench.board.BoardPanel;
import application.gui.workbench.command.BatchPanel;
import application.gui.workbench.command.CommandForm;
import application.gui.workbench.command.CommandPalette;
import application.gui.workbench.command.CommandRunner;
import application.gui.workbench.command.CommandTemplates.CommandTemplate;
import application.gui.workbench.command.CommandTemplates.TemplateContext;
import application.gui.workbench.command.Console;
import application.gui.workbench.dashboard.DashboardPanel;
import application.gui.workbench.dataset.DatasetPanel;
import application.gui.workbench.game.EngineEval;
import application.gui.workbench.game.GameModel;
import application.gui.workbench.game.MovesModel;
import application.gui.workbench.game.PgnExplorerDialog;
import application.gui.workbench.game.PositionDescriptionPanel;
import application.gui.workbench.game.PuzzlePanel;
import application.gui.workbench.layout.EditorSplitArea;
import application.gui.workbench.mcts.MctsPanel;
import application.gui.workbench.mcts.MctsSession;
import application.gui.workbench.play.MctsOpponent;
import application.gui.workbench.play.PlayPanel;
import application.gui.workbench.play.PlaySession;
import application.gui.workbench.play.StrengthModel;
import application.gui.workbench.network.NetworkPanel;
import application.gui.workbench.publish.PublishingPanel;
import application.gui.workbench.publish.ReportPanel;
import application.gui.workbench.session.Job;
import application.gui.workbench.session.LogPanel;
import application.gui.workbench.session.RunArtifacts;
import application.gui.workbench.session.Session;
import application.gui.workbench.ui.AnalysisGraph;
import application.gui.workbench.ui.EvalBar;
import application.gui.workbench.ui.TagCloud;
import application.gui.workbench.ui.Toast;
import application.gui.workbench.ui.ToggleBox;
import chess.core.Position;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.Timer;

import static application.gui.workbench.ui.Ui.transparentPanel;
import static application.gui.workbench.ui.Ui.withTooltip;

/**
 * A new native Swing command and analysis workbench for ChessRTK.
 */

public abstract class WindowBase extends JFrame {
    /**
     * Serialization identifier for Swing frame compatibility.
     */
    protected static final long serialVersionUID = 1L;

    /**
     * UCI move pattern.
     */
    protected static final Pattern UCI_PATTERN = Pattern.compile("\\b[a-h][1-8][a-h][1-8][qrbn]?\\b");

    /**
     * Short engine duration for automatic eval-bar refresh.
     */
    protected static final String EVAL_BAR_DURATION = "350ms";

    /**
     * Dashboard tab index — the default, first tab.
     */
    protected static final int TAB_DASHBOARD = 0;

    /**
     * Analysis tab index.
     */
    protected static final int TAB_ANALYZE = 1;

    /**
     * Commands tab index.
     */
    protected static final int TAB_COMMANDS = 2;

    /**
     * Batch tab index.
     */
    protected static final int TAB_BATCH = 3;

    /**
     * Datasets tab index.
     */
    protected static final int TAB_DATASETS = 4;

    /**
     * Publishing tab index.
     */
    protected static final int TAB_PUBLISH = 5;

    /**
     * Position description tab sentinel. The panel exists, but the Workbench
     * shell does not register it until the feature is ready for navigation.
     */
    protected static final int TAB_DESCRIBE = -1;

    /**
     * Console tab index.
     */
    protected static final int TAB_CONSOLE = 6;

    /**
     * Logs tab index.
     */
    protected static final int TAB_LOGS = 7;

    /**
     * Legacy title for the command console surface.
     */
    protected static final String DOCK_CONSOLE = "Console";

    /**
     * Legacy title for the persisted logs surface.
     */
    protected static final String DOCK_LOGS = "Logs";

    /**
     * Network visualizer tab index.
     */
    protected static final int TAB_NETWORK = 8;

    /**
     * MCTS tree-inspection tab index.
     */
    protected static final int TAB_MCTS = 9;

    /**
     * Puzzle trainer tab index.
     */
    protected static final int TAB_PUZZLES = 10;

    /**
     * Play-vs-engine tab index.
     */
    protected static final int TAB_PLAY = 11;

    /**
     * Board view.
     */
    protected final BoardPanel board = new BoardPanel();

    /**
     * Engine evaluation bar.
     */
    protected final EvalBar evalBar = new EvalBar();

    /**
     * Live analysis data graph.
     */
    protected final AnalysisGraph analysisGraph = new AnalysisGraph();

    /**
     * Network-architecture visualizer (NNUE / lc0-CNN / lc0-BT4).
     */
    protected NetworkPanel networkPanel;

    /**
     * All materialized Network visualizer panels, including duplicates.
     */
    protected final List<NetworkPanel> networkPanels = new ArrayList<>();

    /**
     * Shared MCTS session observed by MCTS inspection panels.
     */
    protected final MctsSession mctsSession = new MctsSession();

    /**
     * Canonical MCTS inspection panel.
     */
    protected MctsPanel mctsPanel;

    /**
     * All materialized MCTS inspection panels, including duplicates.
     */
    protected final List<MctsPanel> mctsPanels = new ArrayList<>();

    /**
     * Human-versus-engine game controller, created with the concrete window.
     */
    protected PlaySession playSession;

    /**
     * Canonical Play-vs-engine setup panel.
     */
    protected PlayPanel playPanel;

    /**
     * Whether position-entry controls are locked because a Play game is active.
     */
    protected boolean playPositionLocked;

    /**
     * Shared, observable session model the Dashboard tab renders from.
     */
    protected final Session session = new Session();

    /**
     * Run-log, manifest, and artifact controller.
     */
    protected final RunArtifacts runArtifacts =
    new RunArtifacts(new WindowRunArtifactHost(this));

    /**
     * Operational overview tab, rendered from {@link #session}.
     */
    protected final DashboardPanel dashboardPanel =
    new DashboardPanel(session, new WindowDashboardActions(this));

    /**
     * Dataset inspection and visualization tab.
     */
    protected DatasetPanel datasetPanel;

    /**
     * PGN puzzle trainer tab, created on first use.
     */
    protected PuzzlePanel puzzlePanel;

    /**
     * Position description tab, created on first use.
     */
    protected PositionDescriptionPanel positionDescriptionPanel;

    /**
     * Job record for the foreground command currently tracked, or null.
     */
    protected Job runningJob;

    /**
     * Wall-clock start time of {@link #runningJob}, in epoch millis.
     */
    protected long runningJobStartMillis;

    /**
     * Queue of command argument lists for a "run all health checks" sequence;
     * drained one at a time as each command finishes.
     */
    protected final java.util.Deque<List<String>> healthCheckQueue = new java.util.ArrayDeque<>();

    /**
     * FEN input.
     */
    protected final JTextField fenField = new JTextField();

    /**
     * Status label.
     */
    protected final JLabel statusLabel = new JLabel();

    /**
     * Move table model.
     */
    protected final MovesModel movesModel = new MovesModel();

    /**
     * Move table.
     */
    protected final JTable movesTable = new JTable(movesModel);

    /**
     * Tag model.
     */
    protected final DefaultListModel<String> tagModel = new DefaultListModel<>();

    /**
     * Visual tag cloud for categorized position tags.
     */
    protected final TagCloud tagCloud = new TagCloud();

    /**
     * Position report panel used by the publishing tab and command palette.
     */
    protected final ReportPanel reportPanel =
    new ReportPanel(new WindowReportHost(this));

    /**
     * Console output with carriage-return handling and line highlighting.
     */
    protected final Console console = new Console();

    /**
     * Persisted application and command log browser, created lazily.
     */
    protected LogPanel logPanel;

    /**
     * Materialized persisted-log browsers, including duplicates.
     */
    protected final List<LogPanel> logPanels = new ArrayList<>();

    /**
     * Command execution state label.
     */
    protected final JLabel commandStateLabel = new JLabel("Idle");

    /**
     * Generated command preview. A multi-line wrapping text area so long
     * commands stay legible without horizontal scrubbing; styled to read as
     * terminal output, not as another input field.
     */
    protected final javax.swing.JTextArea commandField = new javax.swing.JTextArea();

    /**
     * Wrapping bar of command-selector toggle buttons.
     */
    protected final JPanel commandPicker = transparentPanel(
    new FlowLayout(FlowLayout.LEFT, 6, 6));

    /**
     * Run button for the command builder, gated on command validity.
     */
    protected JButton runCommandButton;

    /**
     * Command-selector toggle buttons, one per template.
     */
    protected final transient List<javax.swing.JToggleButton> commandButtons = new ArrayList<>();

    /**
     * Index of the selected command template.
     */
    protected int selectedCommandIndex;

    /**
     * Command templates shown in the {@link #commandPicker}.
     */
    protected List<CommandTemplate> commandTemplates = List.of();

    /**
     * Structured command-builder form: required inputs, exclusive radio
     * groups, and filtered optional flags.
     */
    protected final CommandForm commandForm = new CommandForm();

    /**
     * Main-line move history model.
     */
    protected final GameModel gameModel = new GameModel();

    /**
     * Main-line move history table.
     */
    protected final JTable gameTable = new JTable(gameModel);

    /**
     * Game line import text area.
     */
    protected final JTextArea gameInput = new JTextArea();

    /**
     * Game line state label.
     */
    protected final JLabel gameStateLabel = new JLabel();

    /**
     * Whether code is currently synchronizing the game table selection from the
     * active board position. Selection listeners ignore these programmatic
     * updates to avoid feedback loops.
     */
    protected boolean syncingGameTableSelection;

    /**
     * Whether the game table has already received its row-selection navigation
     * listener.
     */
    protected boolean gameTableSelectionListenerInstalled;

    /**
     * Board-side button that jumps to the first ply.
     */
    protected JButton boardStartButton;

    /**
     * Board-side button that jumps to the previous ply.
     */
    protected JButton boardBackButton;

    /**
     * Board-side button that jumps to the next ply.
     */
    protected JButton boardForwardButton;

    /**
     * Board-side button that jumps to the final ply.
     */
    protected JButton boardEndButton;

    /**
     * Duration field.
     */
    protected final JTextField analysisDurationField = new JTextField(Defaults.ANALYSIS_DURATION);

    /**
     * Shared depth model.
     */
    protected final SpinnerNumberModel depthModel = new SpinnerNumberModel(
            Defaults.ANALYSIS_DEPTH,
            Defaults.ANALYSIS_DEPTH_MIN,
            Defaults.ANALYSIS_DEPTH_MAX,
            Defaults.ANALYSIS_DEPTH_STEP);

    /**
     * Analysis depth control.
     */
    protected final JSpinner analysisDepthSpinner = new JSpinner(depthModel);

    /**
     * Shared MultiPV model.
     */
    protected final SpinnerNumberModel multipvModel = new SpinnerNumberModel(
            Defaults.ANALYSIS_MULTIPV,
            Defaults.ANALYSIS_MULTIPV_MIN,
            Defaults.ANALYSIS_MULTIPV_MAX,
            Defaults.ANALYSIS_MULTIPV_STEP);

    /**
     * Analysis MultiPV control.
     */
    protected final JSpinner analysisMultipvSpinner = new JSpinner(multipvModel);

    /**
     * Shared thread-count model.
     */
    protected final SpinnerNumberModel threadsModel = new SpinnerNumberModel(
            Defaults.ANALYSIS_THREADS,
            Defaults.ANALYSIS_THREADS_MIN,
            Defaults.ANALYSIS_THREADS_MAX,
            Defaults.ANALYSIS_THREADS_STEP);

    /**
     * Analysis thread control.
     */
    protected final JSpinner analysisThreadsSpinner = new JSpinner(threadsModel);

    /**
     * External engine protocol TOML path.
     */
    protected final JTextField engineProtocolField = new JTextField(Config.getProtocolPath());

    /**
     * Optional external engine node budget.
     */
    protected final JTextField engineNodesField = new JTextField();

    /**
     * Optional external engine hash size.
     */
    protected final JTextField engineHashField = new JTextField();

    /**
     * Live external-engine analysis toggle for the board.
     */
    protected final JCheckBox liveEngineToggle = withTooltip(new ToggleBox("Live", true),
            "Continuously analyze the current board with the configured external UCI engine");

    /**
     * Materialized publishing workflow panels, including duplicates.
     */
    protected final List<PublishingPanel> publishingPanels = new ArrayList<>();

    /**
     * Batch workflow panel.
     */
    protected final BatchPanel batchPanel = new BatchPanel(
    new WindowBatchHost(this),
            depthModel,
            multipvModel,
            threadsModel);

    /**
     * Publishing workflow panel.
     */
    protected PublishingPanel publishingPanel;

    /**
     * Returns the network visualizer, creating it only when the Network tab is
     * first opened.
     *
     * @return network visualizer panel
     */
    protected NetworkPanel networkPanel() {
        if (networkPanel == null) {
            networkPanel = createNetworkPanelInstance(true);
        }
        return networkPanel;
    }

    /**
     * Creates an additional independent Network visualizer.
     *
     * @return new network visualizer panel
     */
    protected NetworkPanel createDetachedNetworkPanel() {
        return createNetworkPanelInstance(false);
    }

    /**
     * Creates and registers a Network visualizer instance.
     *
     * @param primary true when this is the canonical Network tab
     * @return network visualizer panel
     */
    private NetworkPanel createNetworkPanelInstance(boolean primary) {
        if (primary && networkPanel != null) {
            return networkPanel;
        }
        NetworkPanel panel = new NetworkPanel();
        if (currentPosition != null) {
            panel.setFen(currentPosition.toString());
        }
        networkPanels.add(panel);
        if (primary) {
            networkPanel = panel;
        }
        return panel;
    }

    /**
     * Returns the MCTS inspector, creating it only when the tab is first
     * opened.
     *
     * @return MCTS inspector
     */
    protected MctsPanel mctsPanel() {
        if (mctsPanel == null) {
            mctsPanel = createMctsPanelInstance(true);
        }
        return mctsPanel;
    }

    /**
     * Creates an additional MCTS inspector observing the shared session.
     *
     * @return new MCTS inspector
     */
    protected MctsPanel createDetachedMctsPanel() {
        return createMctsPanelInstance(false);
    }

    /**
     * Returns the Play-vs-engine setup panel, creating it on first use.
     *
     * @return play panel
     */
    protected PlayPanel playPanel() {
        if (playPanel == null) {
            playPanel = new PlayPanel(playSession(), this::currentFen);
        }
        return playPanel;
    }

    /**
     * Returns the shared Play-vs-engine session, creating it on first use. The
     * opponent provider maps the selected backend to a concrete engine.
     *
     * @return play session
     */
    protected PlaySession playSession() {
        if (playSession == null) {
            playSession = new PlaySession(new WindowPlayHost(this), new StrengthModel(),
                    WindowBase::createOpponent);
        }
        return playSession;
    }

    /**
     * Creates an opponent backend for the Play session from the selected search
     * algorithm and evaluation network.
     *
     * @param config selected search + network
     * @return a fresh opponent
     */
    private static application.gui.workbench.play.Opponent createOpponent(PlaySession.Config config) {
        return switch (config.search()) {
            case MCTS -> new MctsOpponent(config.network());
            default -> new application.gui.workbench.play.AlphaBetaOpponent(config.network());
        };
    }

    /**
     * Creates and registers an MCTS inspector instance.
     *
     * @param primary true when this is the canonical MCTS tab
     * @return MCTS inspector
     */
    private MctsPanel createMctsPanelInstance(boolean primary) {
        if (primary && mctsPanel != null) {
            return mctsPanel;
        }
        MctsPanel panel = new MctsPanel(mctsSession, this::currentFen);
        if (currentPosition != null) {
            panel.setBoardFen(currentPosition.toString());
        }
        mctsPanels.add(panel);
        if (primary) {
            mctsPanel = panel;
        }
        return panel;
    }

    /**
     * Returns the dataset panel, creating it only when a dataset workflow is
     * requested.
     *
     * @return dataset panel
     */
    protected DatasetPanel datasetPanel() {
        if (datasetPanel == null) {
            datasetPanel = createDatasetPanelInstance(true);
        }
        return datasetPanel;
    }

    /**
     * Creates an additional independent dataset inspector.
     *
     * @return new dataset panel
     */
    protected DatasetPanel createDetachedDatasetPanel() {
        return createDatasetPanelInstance(false);
    }

    /**
     * Creates a dataset panel instance.
     *
     * @param primary true when this is the canonical Datasets tab
     * @return dataset panel
     */
    private DatasetPanel createDatasetPanelInstance(boolean primary) {
        if (primary && datasetPanel != null) {
            return datasetPanel;
        }
        DatasetPanel panel = new DatasetPanel();
        if (primary) {
            datasetPanel = panel;
        }
        return panel;
    }

    /**
     * Returns the puzzle trainer, creating it only when opened.
     *
     * @return puzzle trainer panel
     */
    protected PuzzlePanel puzzlePanel() {
        if (puzzlePanel == null) {
            puzzlePanel = new PuzzlePanel();
        }
        return puzzlePanel;
    }

    /**
     * Creates an additional independent puzzle trainer.
     *
     * @return new puzzle panel
     */
    protected PuzzlePanel createDetachedPuzzlePanel() {
        return new PuzzlePanel();
    }

    /**
     * Returns the position-description panel, creating it only when opened.
     *
     * @return position-description panel
     */
    protected PositionDescriptionPanel positionDescriptionPanel() {
        if (positionDescriptionPanel == null) {
            positionDescriptionPanel = new PositionDescriptionPanel();
            if (currentPosition != null) {
                positionDescriptionPanel.setFen(currentPosition.toString());
            }
        }
        return positionDescriptionPanel;
    }

    /**
     * Returns the publishing panel, creating it only when publishing tools are
     * opened or run.
     *
     * @return publishing panel
     */
    protected PublishingPanel publishingPanel() {
        if (publishingPanel == null) {
            publishingPanel = createPublishingPanelInstance(true);
        }
        return publishingPanel;
    }

    /**
     * Creates an additional independent publishing workflow panel.
     *
     * @return new publishing panel
     */
    protected PublishingPanel createDetachedPublishingPanel() {
        return createPublishingPanelInstance(false);
    }

    /**
     * Creates and registers a publishing panel instance.
     *
     * @param primary true when this is the canonical Publish tab
     * @return publishing panel
     */
    private PublishingPanel createPublishingPanelInstance(boolean primary) {
        if (primary && publishingPanel != null) {
            return publishingPanel;
        }
        PublishingPanel panel = new PublishingPanel(new WindowPublishingHost(this));
        publishingPanels.add(panel);
        if (primary) {
            publishingPanel = panel;
        }
        return panel;
    }

    /**
     * Current position.
     */
    protected Position currentPosition;

    /**
     * Legal moves currently shown.
     */
    protected short[] visibleMoves = new short[0];

    /**
     * Selected chess-piece artwork set.
     */
    protected chess.images.assets.PieceSet pieceSet = chess.images.assets.PieceSet.SLATE;

    /**
     * Whether selected-piece legal destinations and drag targets are shown.
     */
    protected boolean showLegalMovePreview = true;

    /**
     * Whether the previous move is highlighted.
     */
    protected boolean showLastMoveHighlight = true;

    /**
     * Whether engine suggestions are shown as board arrows.
     */
    protected boolean showBestMoveArrows = true;

    /**
     * Whether board coordinates are shown.
     */
    protected boolean showBoardCoordinates = true;

    /**
     * Whether board transitions are animated.
     */
    protected boolean boardAnimationsEnabled = true;

    /**
     * Whether the eval bar automatically runs short engine analysis.
     */
    protected boolean autoEvalBarEnabled = true;

    /**
     * Whether the board keeps an external UCI engine running for live analysis.
     */
    protected boolean liveExternalEngineEnabled;

    /**
     * Running command.
     */
    protected CommandRunner.RunningCommand runningCommand;

    /**
     * Running eval-bar engine command.
     */
    protected CommandRunner.RunningCommand evalCommand;

    /**
     * Monotonic eval-bar request id.
     */
    protected long evalRequestId;

    /**
     * Debounce delay for eval-bar refresh, in ms.
     */
    protected static final int EVAL_DEBOUNCE_MS = 90;

    /**
     * Debounce timer that coalesces eval refresh requests.
     */
    protected final Timer evalDebounceTimer = createEvalDebounceTimer();

    /**
     * Whether a command-preview refresh is already queued on the event thread.
     */
    protected boolean commandPreviewUpdateQueued;

    /**
     * Creates the eval-bar debounce timer.
     *
     * @return single-shot debounce timer
     */
    protected Timer createEvalDebounceTimer() {
        Timer timer = new Timer(EVAL_DEBOUNCE_MS, event -> startEvalCommand());
        timer.setRepeats(false);
        return timer;
    }

    /**
     * Main workbench split area: VS Code-style tabs with an optional
     * side-by-side split.
     */
    protected EditorSplitArea tabs;

    /**
     * Nested analysis tabs.
     */
    protected JTabbedPane analysisTabs;

    /**
     * Board detail tabs on the Analyze/Board side panel.
     */
    protected JTabbedPane boardDetailTabs;

    /**
     * Lazy command palette.
     */
    protected CommandPalette commandPalette;

    /**
     * Lazy PGN explorer dialog.
     */
    protected PgnExplorerDialog pgnExplorer;

    /**
     * Background tag worker.
     */
    protected SwingWorker<List<String>, Void> tagWorker;

    /**
     * Guards against recursive duration-field synchronization.
     */
    protected boolean syncingDuration;

    /**
     * Runs the built-in engine search action.
     */
    protected abstract void runBuiltInSearch();

    /**
     * Runs the best-move action.
     */
    protected abstract void runBestMove();

    /**
     * Runs the analysis action.
     */
    protected abstract void runAnalyze();

    /**
     * Runs the tag-generation action.
     */
    protected abstract void runTagsCommand();

    /**
     * Runs the perft action.
     */
    protected abstract void runPerft();

    /**
     * Runs the engine-smoke health check.
     */
    protected abstract void runEngineSmoke();

    /**
     * Runs configuration validation.
     */
    protected abstract void runConfigValidate();

    /**
     * Runs the doctor health check.
     */
    protected abstract void runDoctor();

    /**
     * Runs all health checks.
     */
    protected abstract void runAllHealthChecks();

    /**
     * Copies text to the clipboard and console.
     * @param text source text
     */
    protected abstract void copyText(String text);

    /**
     * Selects the workbench tab at the supplied index.
     * @param index tab index
     */
    protected abstract void selectTab(int index);

    /**
     * Selects a former dock surface by title. Retained for host adapters that
     * still speak in Console/Logs terms.
     *
     * @param dockTab surface title ("Console" / "Logs")
     */
    public abstract void showInBottomDock(String dockTab);

    /**
     * Runs a command with optional standard input.
     * @param args command arguments
     * @param stdin standard input text, or null for none
     */
    protected abstract void runCommand(List<String> args, String stdin);

    /**
     * Runs the selected command-template action.
     */
    protected abstract void runSelectedTemplate();

    /**
     * Copies the currently built command.
     */
    protected abstract void copyBuiltCommand();

    /**
     * Resets the selected command template.
     */
    protected abstract void resetSelectedTemplate();

    /**
     * Clears optional command-template options.
     */
    protected abstract void clearOptionalTemplateOptions();

    /**
     * Updates command-builder option controls.
     */
    protected abstract void updateCommandOptions();

    /**
     * Updates the command-builder preview text.
     */
    protected abstract void updateBuiltCommand();

    /**
     * Refreshes dynamic command previews.
     */
    protected abstract void updateCommandPreviews();

    /**
     * Returns the selected template arguments.
     * @return selected command-template arguments
     */
    protected abstract List<String> selectedTemplateArgs();

    /**
     * Installs command templates.
     */
    protected abstract void installTemplates();

    /**
     * Returns the current FEN.
     * @return current FEN string
     */
    protected abstract String currentFen();

    /**
     * Returns the current command-template context.
     * @return current command-template context
     */
    protected abstract TemplateContext templateContext();

    /**
     * Appends text to the console.
     * @param text source text
     */
    protected abstract void appendConsole(String text);

    /**
     * Shows a toast.
     * @param kind toast kind
     * @param message message text
     */
    protected abstract void toast(Toast.Kind kind, String message);

    /**
     * Shows a warning notice.
     * @param title dialog title
     * @param message message text
     */
    protected abstract void showWarning(String title, String message);

    /**
     * Shows an error notice.
     * @param title dialog title
     * @param message message text
     */
    protected abstract void showError(String title, String message);

    /**
     * Generates the report panel content.
     */
    protected abstract void generateReport();

    /**
     * Copies the generated report.
     */
    protected abstract void copyReport();

    /**
     * Runs the selected publishing workflow.
     */
    protected abstract void runPublishingCommand();

    /**
     * Stops the foreground command.
     */
    protected abstract void stopCommand();

    /**
     * Requests a publish command refresh.
     */
    protected abstract void updatePublishCommand();

    /**
     * Synchronizes batch duration into analysis controls.
     * @param value new value
     */
    protected abstract void syncDurationFromBatch(String value);

    /**
     * Starts the debounced eval command.
     */
    protected abstract void startEvalCommand();

    /**
     * Cancels the eval-bar command.
     */
    protected abstract void cancelEvalCommand();

    /**
     * Restarts live external analysis.
     */
    protected abstract void restartLiveAnalysis();

    /**
     * Pauses hidden live analysis.
     */
    protected abstract void pauseHiddenLiveAnalysis();

    /**
     * Requests a live-analysis update.
     */
    protected abstract void requestLiveAnalysisUpdate();

    /**
     * Stops live external analysis.
     */
    protected abstract void stopLiveAnalysis();

    /**
     * Enables or disables live external analysis.
     * @param enabled true to enable the behavior
     */
    protected abstract void setLiveExternalEngineEnabled(boolean enabled);

    /**
     * Returns true when a live-analysis worker exists.
     * @return true when a live-analysis worker exists
     */
    protected abstract boolean hasLiveEngineWorker();

    /**
     * Creates the Analyze tab.
     * @return computed value
     */
    protected abstract JComponent createBoardTab();

    /**
     * Creates a detached analysis tab instance.
     *
     * @return detached analysis tab
     */
    protected abstract JComponent createDetachedAnalysisTab();

    /**
     * Creates the Commands tab.
     * @return computed value
     */
    protected abstract JComponent createCommandTab();

    /**
     * Creates the in-workbench display settings panel, exposed as an abstract
     * hook so the parent lifecycle layer can build the unified Settings dialog
     * without depending on a concrete subclass.
     *
     * @return display-settings panel
     */
    protected abstract JComponent createDisplaySettingsPanel();

    /**
     * Creates the in-workbench engine settings panel, exposed as an abstract
     * hook for the unified Settings dialog.
     *
     * @return engine-settings panel
     */
    protected abstract JComponent createEngineSettingsPanel();

    /**
     * Creates the Batch tab.
     * @return computed value
     */
    protected abstract JComponent createBatchTab();

    /**
     * Creates the Datasets tab.
     * @return computed value
     */
    protected abstract JComponent createDatasetTab();

    /**
     * Creates a detached Datasets tab instance.
     *
     * @return detached datasets tab
     */
    protected abstract JComponent createDetachedDatasetTab();

    /**
     * Creates the Publish tab.
     * @return computed value
     */
    protected abstract JComponent createPublishTab();

    /**
     * Creates the position-description tab.
     *
     * @return position-description tab
     */
    protected abstract JComponent createDescribeTab();

    /**
     * Creates a detached Publish tab instance.
     *
     * @return detached publish tab
     */
    protected abstract JComponent createDetachedPublishTab();

    /**
     * Creates the Network tab.
     *
     * @return network tab
     */
    protected abstract JComponent createNetworkTab();

    /**
     * Creates a detached Network tab instance.
     *
     * @return detached network tab
     */
    protected abstract JComponent createDetachedNetworkTab();

    /**
     * Creates the MCTS tab.
     *
     * @return MCTS tab
     */
    protected abstract JComponent createMctsTab();

    /**
     * Creates a detached MCTS tab instance.
     *
     * @return detached MCTS tab
     */
    protected abstract JComponent createDetachedMctsTab();

    /**
     * Creates the Play-vs-engine tab.
     *
     * @return play tab
     */
    protected abstract JComponent createPlayTab();

    /**
     * Creates a detached Play-vs-engine tab instance.
     *
     * @return detached play tab
     */
    protected abstract JComponent createDetachedPlayTab();

    /**
     * Locks or unlocks position-entry controls while a Play game is active.
     *
     * @param locked true to lock position entry
     */
    protected abstract void setPlayPositionLocked(boolean locked);

    /**
     * Creates the Puzzles tab.
     *
     * @return computed value
     */
    protected abstract JComponent createPuzzleTab();

    /**
     * Creates a detached Puzzles tab instance.
     *
     * @return detached puzzles tab
     */
    protected abstract JComponent createDetachedPuzzleTab();

    /**
     * Creates the Console tab.
     * @return computed value
     */
    protected abstract JComponent createConsolePanel();

    /**
     * Creates the Logs tab.
     *
     * @return computed value
     */
    protected abstract JComponent createLogTab();

    /**
     * Returns the primary persisted-log browser.
     *
     * @return log browser
     */
    protected abstract LogPanel primaryLogPanel();

    /**
     * Creates a detached Logs tab instance.
     *
     * @return detached logs tab
     */
    protected abstract JComponent createDetachedLogTab();

    /**
     * Navigates the loaded game by a relative ply delta.
     * @param delta relative ply delta
     */
    protected abstract void navigateGame(int delta);

    /**
     * Jumps the loaded game to an absolute ply.
     * @param ply game ply index
     */
    protected abstract void jumpGameTo(int ply);

    /**
     * Focuses the option filter.
     */
    protected abstract void focusOptionFilter();

    /**
     * Focuses the FEN field.
     */
    protected abstract void focusFenField();

    /**
     * Focuses the game-input field.
     */
    protected abstract void focusGameInput();

    /**
     * Shows display settings.
     */
    protected abstract void showDisplaySettings();

    /**
     * Shows engine settings.
     */
    protected abstract void showEngineSettings();

    /**
     * Shows analysis data.
     */
    protected abstract void showAnalysisData();

    /**
     * Copies the analysis CSV.
     */
    protected abstract void copyAnalysisCsv();

    /**
     * Copies the analysis report.
     */
    protected abstract void copyAnalysisReport();

    /**
     * Prints the analysis report.
     */
    protected abstract void printAnalysisReport();

    /**
     * Clears the analysis data.
     */
    protected abstract void clearAnalysisData();

    /**
     * Resets engine settings to defaults.
     */
    protected abstract void resetEngineSettings();

    /**
     * Plays a move from the board.
     * @param move move encoded in CRTK move format
     */
    protected abstract void playMove(short move);

    /**
     * Applies the current FEN field.
     */
    protected abstract void setPositionFromField();

    /**
     * Starts a new game from a FEN.
     * @param fen FEN string
     */
    protected abstract void startNewGame(String fen);

    /**
     * Shows a game ply.
     * @param ply game ply index
     */
    protected abstract void showGamePly(int ply);

    /**
     * Updates the legal-move table.
     */
    protected abstract void updateMoves();

    /**
     * Updates the position status label.
     */
    protected abstract void updateStatus();

    /**
     * Updates generated tags asynchronously.
     */
    protected abstract void updateTagsAsync();

    /**
     * Updates game-state text.
     */
    protected abstract void updateGameState();

    /**
     * Configures the game table.
     */
    protected abstract void configureGameTable();

    /**
     * Loads game text as PGN, FEN, or SAN/UCI line.
     * @param text source text
     */
    protected abstract void loadGameText(String text);

    /**
     * Opens a game file.
     */
    protected abstract void loadGameFile();

    /**
     * Saves the current game as PGN.
     */
    protected abstract void savePgnFile();

    /**
     * Loads a move line from text.
     * @param text source text
     */
    protected abstract void loadMoveLine(String text);

    /**
     * Synchronizes analysis duration into batch controls.
     */
    protected abstract void syncDurationFromAnalysis();

    /**
     * Requests command-preview refresh.
     */
    protected abstract void requestCommandPreviews();

    /**
     * Requests an eval refresh.
     */
    protected abstract void requestEvalUpdate();

    /**
     * Builds eval-bar CLI arguments for a FEN.
     * @param fen FEN string
     * @return CLI arguments for the eval-bar command
     */
    protected abstract List<String> buildEvalBarArgs(String fen);

    /**
     * Builds an analysis command from explicit workspace settings.
     *
     * @param fen position FEN
     * @param multipv requested line count
     * @param duration maximum analysis duration
     * @return command args
     */
    protected abstract List<String> buildAnalyzeArgs(String fen, String multipv, String duration);

    /**
     * Returns the engine protocol field value.
     * @return engine protocol field value
     */
    protected abstract String engineProtocolValue();

    /**
     * Returns the engine nodes field value.
     * @return engine nodes field value
     */
    protected abstract String engineNodesValue();

    /**
     * Returns the engine hash field value.
     * @return engine hash field value
     */
    protected abstract String engineHashValue();

    /**
     * Returns the duration field value.
     * @return duration field value
     */
    protected abstract String durationValue();

    /**
     * Returns the depth field value.
     * @return depth field value
     */
    protected abstract String depthValue();

    /**
     * Returns the MultiPV field value.
     * @return MultiPV field value
     */
    protected abstract String multipvValue();

    /**
     * Returns the thread-count field value.
     * @return thread-count field value
     */
    protected abstract String threadsValue();

    /**
     * Highlights a move from command output when appropriate.
     * @param args command arguments
     * @param output command or engine output text
     */
    protected abstract void maybeHighlightMove(List<String> args, String output);

    /**
     * Applies health status from a finished command.
     * @param args command arguments
     * @param exitCode process exit code
     */
    protected abstract void updateHealthFromCommand(List<String> args, int exitCode);

    /**
     * Applies health failure status from a failed command.
     * @param args command arguments
     */
    protected abstract void updateHealthFailedFromCommand(List<String> args);

    /**
     * Updates the command-state label.
     * @param state state text
     */
    protected abstract void setCommandState(String state);

    /**
     * Parses engine eval output for the eval bar.
     * @param output command or engine output text
     * @return parsed engine evaluation
     */
    protected static EngineEval parseEngineEval(String output) {
        return EngineEval.parse(output);
    }
}
