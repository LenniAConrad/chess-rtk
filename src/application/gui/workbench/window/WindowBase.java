package application.gui.workbench.window;

import application.Config;
import application.gui.app.LegacyWorkbenchComposition;
import application.gui.feature.dataset.DatasetDependencies;
import application.gui.feature.dataset.DatasetView;
import application.gui.feature.publishing.PublishingDependencies;
import application.gui.feature.publishing.PublishingView;
import application.gui.feature.publishing.ReportDependencies;
import application.gui.feature.publishing.ReportView;
import application.gui.platform.NotificationKind;
import application.gui.workbench.Defaults;
import application.gui.workbench.board.BoardPanel;
import application.gui.workbench.command.CommandForm;
import application.gui.workbench.command.CommandPalette;
import application.gui.workbench.command.CommandRunner;
import application.gui.workbench.command.CommandTemplates.CommandTemplate;
import application.gui.workbench.command.CommandTemplates.TemplateContext;
import application.gui.workbench.command.Console;
import application.gui.workbench.dashboard.DashboardPanel;
import application.gui.workbench.game.EngineEval;
import application.gui.workbench.game.GameModel;
import application.gui.workbench.game.MovesModel;
import application.gui.workbench.game.PgnExplorerDialog;
import application.gui.workbench.game.PositionDescriptionPanel;
import application.gui.workbench.game.PuzzlePanel;
import application.gui.workbench.game.SavedGame;
import application.gui.workbench.game.SavedGameStore;
import application.gui.workbench.game.SavedGamesPanel;
import application.gui.workbench.library.GameLibrary;
import application.gui.workbench.layout.EditorSplitArea;
import application.gui.workbench.mcts.MctsPanel;
import application.gui.workbench.mcts.MctsSession;
import application.gui.workbench.mcts.MctsWorkspacePanel;
import application.gui.workbench.play.MctsOpponent;
import application.gui.workbench.play.PlayPanel;
import application.gui.workbench.play.PlaySession;
import application.gui.workbench.play.StrengthModel;
import application.gui.workbench.network.NetworkPanel;
import application.gui.workbench.session.Job;
import application.gui.workbench.session.LogPanel;
import application.gui.workbench.session.RunArtifacts;
import application.gui.workbench.session.Session;
import application.gui.workbench.ui.AnalysisGraph;
import application.gui.workbench.ui.EvalBar;
import application.gui.workbench.ui.HoldButton;
import application.gui.workbench.ui.StatusBadge;
import application.gui.workbench.ui.TagCloud;
import application.gui.workbench.ui.Toast;
import application.gui.workbench.ui.ToggleBox;
import application.gui.workbench.ui.WorkspaceHeader;
import chess.core.Position;
import chess.struct.Game;
import chess.struct.Pgn;
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
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
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
     * Board tab index — the unified board surface hosting the Analyze, Play,
     * Solve, Relations, and Draw modes behind a switcher.
     */
    protected static final int TAB_BOARD = 1;

    /**
     * Run tab index — the command-builder surface. Batch workflows are command
     * templates inside this view; Console and Logs are registered as their own
     * top-level output views.
     */
    protected static final int TAB_RUN = 2;

    /**
     * Datasets tab index.
     */
    protected static final int TAB_DATASETS = 3;

    /**
     * Publishing tab index.
     */
    protected static final int TAB_PUBLISH = 4;

    /**
     * Engine tab index — the unified engine surface hosting the neural-network
     * visualizer (Network) and the PUCT/MCTS search (Search) modes.
     */
    protected static final int TAB_ENGINE = 5;

    /**
     * Console tab index — the command console, a first-class split/dockable
     * surface (appended after the original surfaces so earlier indices hold).
     */
    protected static final int TAB_CONSOLE = 6;

    /**
     * Logs tab index — the persisted-log browser, a first-class split/dockable
     * surface.
     */
    protected static final int TAB_LOGS = 7;

    /**
     * Studies tab index — PGN-backed local study books and chapters.
     */
    protected static final int TAB_STUDIES = 8;

    /**
     * Position description tab sentinel. The panel exists, but the Workbench
     * shell does not register it until the feature is ready for navigation.
     */
    protected static final int TAB_DESCRIBE = -1;

    /**
     * Legacy title for the persisted logs surface (a Run mode title).
     */
    protected static final String DOCK_LOGS = "Logs";

    /**
     * Run surface mode: the CLI command builder (the only Run mode now that
     * batch is merged into Build and Console/Logs are top-level surfaces).
     */
    protected static final int RUN_BUILD = 0;

    /**
     * Board surface mode: position analysis.
     */
    protected static final int BOARD_ANALYZE = 0;

    /**
     * Board surface mode: play versus the engine.
     */
    protected static final int BOARD_PLAY = 1;

    /**
     * Board surface mode: puzzle solving.
     */
    protected static final int BOARD_SOLVE = 2;

    /**
     * Board surface mode: tactical-incidence relations overlay.
     */
    protected static final int BOARD_RELATIONS = 3;

    /**
     * Board surface mode: free annotation drawing and board export.
     */
    protected static final int BOARD_DRAW = 4;

    /**
     * Card key for the default Analyze board layout.
     */
    protected static final String ANALYZE_CARD_BOARD = "board";

    /**
     * Card key for the Analyze game-line layout.
     */
    protected static final String ANALYZE_CARD_GAME = "game";

    /**
     * Engine surface mode: the neural-network visualizer.
     */
    protected static final int ENGINE_NETWORK = 0;

    /**
     * Engine surface mode: the PUCT/MCTS search inspector.
     */
    protected static final int ENGINE_SEARCH = 1;

    /**
     * Engine surface mode: the live search-tree graph. Kept as a compatibility
     * alias for callers that still ask for the old Tree mode; it opens Search and
     * selects the Graph subview.
     */
    protected static final int ENGINE_TREE = ENGINE_SEARCH;

    /**
     * Engine surface mode: deterministic self-play gauntlets.
     */
    protected static final int ENGINE_GAUNTLET = 2;

    // Analyze, Play, Solve (puzzles), Relations, and Draw are no longer separate
    // top-level tabs: they are modes of the unified Board surface (TAB_BOARD,
    // BOARD_* constants). Network, Search, and Gauntlet are likewise modes of
    // the unified Engine surface (TAB_ENGINE, ENGINE_* constants), and the
    // former CLI tabs are modes of the Run surface (TAB_RUN, RUN_* constants).
    // All three are application.gui.workbench.ui.SwitchedWorkspace instances.

    /**
     * The single shared board view. The Board surface's Analyze, Play, Relations,
     * and Draw modes all reuse this one board (it is re-parented into the
     * active mode and reconfigured for it), so a position carries across modes
     * with no duplicate widgets. The Solve (puzzle) mode keeps its own board
     * because puzzles step through their own positions independent of the
     * analysis line.
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
     * Canonical Search workspace that hosts the MCTS table and graph views.
     */
    protected MctsWorkspacePanel mctsWorkspacePanel;

    /**
     * All materialized MCTS inspection panels, including duplicates.
     */
    protected final List<MctsPanel> mctsPanels = new ArrayList<>();

    /**
     * Canonical search-tree graph panel.
     */
    protected application.gui.workbench.mcts.TreePanel treePanel;

    /**
     * All materialized search-tree graph panels, including duplicates.
     */
    protected final List<application.gui.workbench.mcts.TreePanel> treePanels = new ArrayList<>();

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
    protected DatasetView datasetPanel;

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
    protected final ReportView reportPanel =
            LegacyWorkbenchComposition.reportView(reportDependencies());

    /**
     * Console output with carriage-return handling and line highlighting. This
     * is the primary console; {@link #consoles} also holds any duplicates so
     * command output fans out to every open console view.
     */
    protected final Console console = new Console();

    /**
     * Every materialized console, including the primary and any duplicates, so
     * {@code appendConsole} can fan out to all of them.
     */
    protected final List<Console> consoles = new ArrayList<>(List.of(console));

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
     * Short description for the selected command template.
     */
    protected final JTextArea commandDescriptionLabel = new JTextArea();

    /**
     * Canonical command path for the selected command template.
     */
    protected final JLabel commandPathLabel = new JLabel();

    /**
     * Scroll wrapper for the generated command preview, retained so the wrap
     * toggle can switch horizontal scrolling on and off.
     */
    protected JScrollPane commandPreviewScroll;

    /**
     * Status badge for the current Run command lifecycle.
     */
    protected final StatusBadge runStateBadge = new StatusBadge();

    /**
     * Command-builder validation badges.
     */
    protected final StatusBadge runFenBadge = new StatusBadge(),
            runProtocolBadge = new StatusBadge(),
            runDurationBadge = new StatusBadge();

    /**
     * Parsed result view for the most recent Run command.
     */
    protected final JTextArea runParsedOutput = new JTextArea();

    /**
     * Raw command output mirrored from the foreground process.
     */
    protected final Console runRawOutput = new Console();

    /**
     * Small recent-command list local to the Run builder.
     */
    protected final DefaultListModel<String> recentCommandModel = new DefaultListModel<>();

    /**
     * Recent-command list view.
     */
    protected final JList<String> recentCommandList = new JList<>(recentCommandModel);

    /**
     * Stop button in the Run header, visible only while a command is running.
     */
    protected JComponent runStopButton;

    /**
     * Stop buttons outside the Run header, also visible only while a command is
     * running.
     */
    protected final List<JComponent> commandStopButtons = new ArrayList<>();

    /**
     * Last known command-form validity.
     */
    protected boolean commandFormRunnable = true;

    /**
     * Host bar for the command-template selector.
     */
    protected final JPanel commandPicker = transparentPanel(
    new FlowLayout(FlowLayout.LEFT, 6, 6));

    /**
     * Run button for the command builder, gated on command validity.
     */
    protected JButton runCommandButton;

    /**
     * Command-template selector — a single dropdown over every template, in
     * place of a wrapping row of one toggle button per template.
     */
    protected transient javax.swing.JComboBox<String> commandCombo;

    /**
     * Guards against the combo's selection listener re-entering
     * {@code selectCommandTemplate} while it is syncing the combo.
     */
    protected transient boolean syncingCommandCombo;

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
     * Local saved-game store for resumable Workbench lines.
     */
    protected final SavedGameStore savedGameStore = new SavedGameStore();

    /**
     * Current saved-game id being updated, or null until the line is first saved.
     */
    protected String activeSavedGameId;

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
    protected final List<PublishingView> publishingPanels = new ArrayList<>();

    /**
     * Publishing workflow panel.
     */
    protected PublishingView publishingPanel;

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
        NetworkPanel panel = new NetworkPanel(!primary);
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
     * Returns the Search workspace, creating it only when Engine / Search is
     * first opened.
     *
     * @return Search workspace
     */
    protected MctsWorkspacePanel mctsWorkspacePanel() {
        if (mctsWorkspacePanel == null) {
            mctsWorkspacePanel = new MctsWorkspacePanel(this::mctsPanel, this::treePanel);
        }
        return mctsWorkspacePanel;
    }

    /**
     * Returns the Play-vs-engine setup panel, creating it on first use.
     *
     * @return play panel
     */
    protected PlayPanel playPanel() {
        if (playPanel == null) {
            playPanel = new PlayPanel(playSession(), this::currentFen,
                    this::runAnalyze, this::runCurrentGameReview,
                    this::saveCurrentGameFromUi, this::refreshWorkspaceHeaders);
        }
        return playPanel;
    }

    /**
     * Refreshes workspace headers when lazily owned panels update context.
     * Subclasses with a shell header override this; the base hook keeps lazy
     * panel construction decoupled from the concrete window layer.
     */
    protected void refreshWorkspaceHeaders() {
        // no shell header at this base layer
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
     * Returns the search-tree graph, creating it only when the Tree mode is
     * first opened.
     *
     * @return search-tree graph panel
     */
    protected application.gui.workbench.mcts.TreePanel treePanel() {
        if (treePanel == null) {
            treePanel = createTreePanelInstance(true);
        }
        return treePanel;
    }

    /**
     * Creates an additional independent search-tree graph.
     *
     * @return new search-tree graph panel
     */
    protected application.gui.workbench.mcts.TreePanel createDetachedTreePanel() {
        return createTreePanelInstance(false);
    }

    /**
     * Creates and registers a search-tree graph instance.
     *
     * @param primary true when this is the canonical Tree mode
     * @return search-tree graph panel
     */
    private application.gui.workbench.mcts.TreePanel createTreePanelInstance(boolean primary) {
        if (primary && treePanel != null) {
            return treePanel;
        }
        application.gui.workbench.mcts.TreePanel panel =
                new application.gui.workbench.mcts.TreePanel(mctsSession, this::currentFen, !primary);
        panel.setOpenInNewBoard(this::openFenInNewBoard);
        if (currentPosition != null) {
            panel.setBoardFen(currentPosition.toString());
        }
        treePanels.add(panel);
        if (primary) {
            treePanel = panel;
        }
        return panel;
    }

    /**
     * Returns the dataset panel, creating it only when a dataset workflow is
     * requested.
     *
     * @return dataset panel
     */
    protected DatasetView datasetPanel() {
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
    protected DatasetView createDetachedDatasetPanel() {
        return createDatasetPanelInstance(false);
    }

    /**
     * Creates a dataset panel instance.
     *
     * @param primary true when this is the canonical Datasets tab
     * @return dataset panel
     */
    private DatasetView createDatasetPanelInstance(boolean primary) {
        if (primary && datasetPanel != null) {
            return datasetPanel;
        }
        DatasetView panel = LegacyWorkbenchComposition.datasetView(datasetDependencies());
        if (primary) {
            datasetPanel = panel;
        }
        return panel;
    }

    /**
     * Builds the narrow dependency set for a Dataset view instance.
     *
     * @return dataset dependencies
     */
    private DatasetDependencies datasetDependencies() {
        return new DatasetDependencies(this::openFenInBoard, this::openFenInNewBoard);
    }

    /**
     * Returns the puzzle trainer, creating it only when opened.
     *
     * @return puzzle trainer panel
     */
    protected PuzzlePanel puzzlePanel() {
        if (puzzlePanel == null) {
            puzzlePanel = new PuzzlePanel(this::refreshWorkspaceHeaders);
        }
        return puzzlePanel;
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
    protected PublishingView publishingPanel() {
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
    protected PublishingView createDetachedPublishingPanel() {
        return createPublishingPanelInstance(false);
    }

    /**
     * Creates and registers a publishing panel instance.
     *
     * @param primary true when this is the canonical Publish tab
     * @return publishing panel
     */
    private PublishingView createPublishingPanelInstance(boolean primary) {
        if (primary && publishingPanel != null) {
            return publishingPanel;
        }
        PublishingView panel = LegacyWorkbenchComposition.publishingView(publishingDependencies());
        publishingPanels.add(panel);
        if (primary) {
            publishingPanel = panel;
        }
        return panel;
    }

    /**
     * Builds the narrow dependency set for a Publishing workflow instance.
     *
     * @return publishing dependencies
     */
    private PublishingDependencies publishingDependencies() {
        ReportView reportPanel = LegacyWorkbenchComposition.reportView(reportDependencies());
        return new PublishingDependencies(
                this,
                this::currentFen,
                () -> gameModel,
                () -> commandForm.positionsText(),
                reportPanel,
                this::runCommand,
                this::copyText,
                new PublishingDependencies.CommandControl() {
                    /**
                     * {@inheritDoc}
                     */
                    @Override
                    public void stopCommand() {
                        WindowBase.this.stopCommand();
                    }

                    /**
                     * {@inheritDoc}
                     */
                    @Override
                    public JComponent stopButton() {
                        return createCommandStopButton();
                    }
                },
                (kind, message) -> toast(toastKind(kind), message),
                this::showError);
    }

    /**
     * Builds the narrow dependency set for a report view instance.
     *
     * @return report dependencies
     */
    private ReportDependencies reportDependencies() {
        return new ReportDependencies(
                this,
                () -> currentPosition,
                () -> visibleMoves,
                () -> gameModel,
                () -> tagModel,
                this::copyText,
                this::appendConsole,
                (kind, message) -> toast(toastKind(kind), message),
                this::showError);
    }

    /**
     * Maps platform notification severities to the current Workbench toast API.
     *
     * @param kind notification kind
     * @return toast kind
     */
    private static Toast.Kind toastKind(NotificationKind kind) {
        return switch (kind) {
            case SUCCESS -> Toast.Kind.SUCCESS;
            case WARNING -> Toast.Kind.WARNING;
            case ERROR -> Toast.Kind.ERROR;
            case INFO -> Toast.Kind.INFO;
        };
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
     * Whether attacked, undefended pieces are marked on the board.
     */
    protected boolean showUndefendedPieces;

    /**
     * Whether pieces pinned to their own king are marked on the board.
     */
    protected boolean showPinnedPieces;

    /**
     * Whether the opponent king is marked when a legal checking move exists.
     */
    protected boolean showCheckableKing;

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
     * Card stack for Analyze board vs game-line layouts. This intentionally has
     * no visible tab chrome; entry points select the needed card directly.
     */
    protected JPanel analysisCards;

    /**
     * The unified Board surface hosting the Analyze/Play/Solve/Relations/Draw modes.
     * Assigned when the Board tab is built; used to route navigation and input
     * gating to the active board mode.
     */
    protected application.gui.workbench.ui.SwitchedWorkspace boardWorkspace;

    /**
     * The unified Engine surface hosting the Network and Search modes. Assigned
     * when the Engine tab is built; used to route navigation to the active mode.
     */
    protected application.gui.workbench.ui.SwitchedWorkspace engineWorkspace;

    /**
     * The Run command-builder surface, assigned when the Run tab is built.
     */
    protected application.gui.workbench.ui.SwitchedWorkspace runWorkspace;

    /**
     * Header for the Run command-builder surface.
     */
    protected WorkspaceHeader runHeader;

    /**
     * Headers for materialized Console surfaces, including duplicates.
     */
    protected final List<WorkspaceHeader> consoleHeaders = new ArrayList<>();

    /**
     * Refreshes the Run header when command context changes. The command layer
     * overrides this; earlier window layers call it through this stable hook.
     */
    protected void refreshRunHeader() {
        // no-op until the command layer is initialized
    }

    /**
     * Loads saved games for the library panel.
     *
     * @return saved games, most recent first
     */
    protected List<SavedGame> savedGames() {
        try {
            return savedGameStore.load();
        } catch (java.io.IOException ex) {
            appendConsole("Saved games load failed: " + ex.getMessage() + System.lineSeparator());
            return List.of();
        }
    }

    /**
     * Persists the current game line silently.
     *
     * @param status saved-game status
     * @return true when a line was saved
     */
    protected boolean persistCurrentGame(String status) {
        return persistCurrentGame(status, false);
    }

    /**
     * Persists the current game line.
     *
     * @param status saved-game status
     * @param notify whether to show user feedback
     * @return true when a line was saved
     */
    protected boolean persistCurrentGame(String status, boolean notify) {
        if (gameModel.lastPly() <= 0) {
            if (notify) {
                toast(Toast.Kind.INFO, "No moves to save");
            }
            return false;
        }
        try {
            SavedGame existing = activeSavedGameId == null ? null : savedGameStore.find(activeSavedGameId);
            long now = System.currentTimeMillis();
            String id = activeSavedGameId == null || activeSavedGameId.isBlank()
                    ? savedGameStore.nextId()
                    : activeSavedGameId;
            long created = existing == null || existing.createdAtMillis() <= 0L
                    ? now
                    : existing.createdAtMillis();
            SavedGame game = SavedGame.capture(id, created, now, status, gameModel);
            savedGameStore.save(game);
            activeSavedGameId = game.id();
            maybePersistLibraryGame(status);
            refreshSavedGamesPanel();
            if (notify) {
                toast(Toast.Kind.SUCCESS, "Saved game");
            }
            return true;
        } catch (java.io.IOException | RuntimeException ex) {
            appendConsole("Saved games write failed: " + ex.getMessage() + System.lineSeparator());
            if (notify) {
                toast(Toast.Kind.ERROR, "Could not save game");
            }
            return false;
        }
    }

    /**
     * Saves the current game from a visible UI action.
     */
    protected void saveCurrentGameFromUi() {
        persistCurrentGame("Saved", true);
    }

    /**
     * Loads recent PGN-backed library entries for the library panel.
     *
     * @return library entries
     */
    protected List<GameLibrary.Entry> libraryGames() {
        try {
            return new GameLibrary().recent(500);
        } catch (java.io.IOException ex) {
            appendConsole("Game library load failed: " + ex.getMessage() + System.lineSeparator());
            return List.of();
        }
    }

    /**
     * Imports the current game into the PGN-backed library for durable lookup.
     *
     * @param status saved-game status
     */
    protected void maybePersistLibraryGame(String status) {
        if (!shouldPersistLibraryGame(status)) {
            return;
        }
        try {
            new GameLibrary().saveCurrent(gameModel, "Workbench " + status);
            refreshGameLibraryPanel();
        } catch (java.io.IOException | RuntimeException ex) {
            appendConsole("Game library write failed: " + ex.getMessage() + System.lineSeparator());
        }
    }

    /**
     * Returns whether one saved-game status should be copied to the PGN library.
     *
     * @param status saved-game status
     * @return true when the PGN library should receive the game
     */
    private static boolean shouldPersistLibraryGame(String status) {
        if (status == null) {
            return false;
        }
        return switch (status) {
            case "Saved", "Finished", "Imported" -> true;
            default -> false;
        };
    }

    /**
     * Opens one PGN-backed library entry on the board.
     *
     * @param entry selected library entry
     */
    protected void openLibraryGame(GameLibrary.Entry entry) {
        if (entry == null || entry.pgn().isBlank()) {
            return;
        }
        try {
            List<Game> games = Pgn.parseGames(entry.pgn());
            if (games.isEmpty()) {
                showError("Open library game failed", "Stored PGN contains no game.");
                return;
            }
            if (playSession != null && playSession.isActive()) {
                playSession.stop();
            }
            loadGame(games.get(0));
            appendConsole("Opened library game " + entry.label() + System.lineSeparator());
            toast(Toast.Kind.SUCCESS, "Opened library game");
        } catch (IllegalArgumentException ex) {
            showError("Open library game failed", ex.getMessage());
        }
    }

    /**
     * Reloads one saved game into the shared board line.
     *
     * @param game saved game
     * @return true when resumed
     */
    protected boolean resumeSavedGame(SavedGame game) {
        if (game == null) {
            return false;
        }
        try {
            persistCurrentGame("Aborted");
            if (playSession != null && playSession.isActive()) {
                playSession.stop();
            }
            Position start = new Position(game.startFen());
            List<Short> line = SavedGameStore.parseUciLine(start, game.uciLine());
            gameModel.loadLine(start, line);
            activeSavedGameId = game.id();
            session.clearEvalHistory();
            showGamePly(Math.max(0, Math.min(game.currentPly(), gameModel.lastPly())));
            appendConsole("Resumed saved game " + game.title() + System.lineSeparator());
            toast(Toast.Kind.SUCCESS, "Resumed saved game");
            refreshSavedGamesPanel();
            return true;
        } catch (IllegalArgumentException ex) {
            showError("Resume game failed", ex.getMessage());
            return false;
        }
    }

    /**
     * Refreshes the saved-games panel when present.
     */
    protected void refreshSavedGamesPanel() {
        if (savedGamesPanel != null) {
            savedGamesPanel.refresh();
        }
        refreshGameLibraryPanel();
    }

    /**
     * Refreshes the PGN-backed library panel when present.
     */
    protected void refreshGameLibraryPanel() {
        // implemented by the board layer once the library panel exists
    }

    /**
     * Board detail tabs on the Analyze/Board side panel.
     */
    protected JTabbedPane boardDetailTabs;

    /**
     * Local saved-games panel, created with the board detail tabs.
     */
    protected SavedGamesPanel savedGamesPanel;

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
     * Runs review for the current game line.
     */
    protected abstract void runCurrentGameReview();

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
     * Opens Run and selects a named command template.
     *
     * @param name command-template display name
     */
    protected abstract void openCommandTemplate(String name);

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
     * Returns the selected template's preview arguments.
     * @return selected command-template arguments
     */
    protected abstract List<String> selectedTemplateArgs();

    /**
     * Returns the selected template's runnable arguments, materializing any
     * multi-line input list to a temporary file.
     *
     * @return runnable command-template arguments
     * @throws java.io.IOException when an input file cannot be written
     */
    protected abstract List<String> selectedTemplateRunArgs() throws java.io.IOException;

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
     * Creates a shared command stop button for non-Run surfaces. It follows the
     * foreground command lifecycle instead of staying red while idle.
     *
     * @return stop button
     */
    protected JComponent createCommandStopButton() {
        boolean running = runningCommand != null && runningCommand.isRunning();
        HoldButton button = new HoldButton("Stop", this::stopCommand, true);
        button.setVisible(running);
        button.setEnabled(running);
        commandStopButtons.add(button);
        return button;
    }

    /**
     * Requests a publish command refresh.
     */
    protected abstract void updatePublishCommand();

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
     * @return created the Analyze tab
     */
    protected abstract JComponent createBoardTab();

    /**
     * Creates the unified Board surface (Analyze/Play/Solve/Relations/Draw modes
     * behind a switcher), assigning {@link #boardWorkspace}.
     *
     * @return board workspace component
     */
    protected abstract JComponent createBoardWorkspaceTab();

    /**
     * Creates the unified Engine surface (Network/Search modes behind a
     * switcher), assigning {@link #engineWorkspace}.
     *
     * @return engine workspace component
     */
    protected abstract JComponent createEngineWorkspaceTab();

    /**
     * Creates the Run command-builder surface, assigning {@link #runWorkspace}.
     *
     * @return run workspace component
     */
    protected abstract JComponent createRunWorkspaceTab();

    /**
     * Opens the Run surface (the command builder).
     *
     * @param mode run mode (currently only {@link #RUN_BUILD})
     */
    protected abstract void openRun(int mode);

    /**
     * Creates a detached analysis tab instance.
     *
     * @return detached analysis tab
     */
    protected abstract JComponent createDetachedAnalysisTab();

    /**
     * Creates the Commands tab.
     * @return created the Commands tab
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
     * Creates the Datasets tab.
     * @return created the Datasets tab
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
     * @return created the Publish tab
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
     * Creates the Engine / Search tab.
     *
     * @return Search tab
     */
    protected abstract JComponent createMctsTab();

    /**
     * Creates the Play-vs-engine tab.
     *
     * @return play tab
     */
    protected abstract JComponent createPlayTab();

    /**
     * Creates the tactical-incidence Relations tab.
     *
     * @return relations tab
     */
    protected abstract JComponent createRelationsTab();

    /**
     * Locks or unlocks position-entry controls while a Play game is active.
     *
     * @param locked true to lock position entry
     */
    protected abstract void setPlayPositionLocked(boolean locked);

    /**
     * Creates the Puzzles tab.
     *
     * @return created the Puzzles tab
     */
    protected abstract JComponent createPuzzleTab();

    /**
     * Creates the primary Console surface.
     * @return created the primary Console surface
     */
    protected abstract JComponent createConsolePanel();

    /**
     * Creates an independent duplicate Console surface (its own console
     * instance, wired into the output fan-out).
     *
     * @return duplicate console surface
     */
    protected abstract JComponent createDetachedConsolePanel();

    /**
     * Creates the primary Logs surface.
     *
     * @return created the primary Logs surface
     */
    protected abstract JComponent createLogTab();

    /**
     * Creates an independent duplicate Logs surface.
     *
     * @return duplicate logs surface
     */
    protected abstract JComponent createDetachedLogTab();

    /**
     * Creates the primary Studies surface.
     *
     * @return studies surface
     */
    protected abstract JComponent createStudiesWorkspaceTab();

    /**
     * Creates an independent duplicate Studies surface.
     *
     * @return duplicate studies surface
     */
    protected abstract JComponent createDetachedStudiesWorkspaceTab();

    /**
     * Returns the primary persisted-log browser.
     *
     * @return log browser
     */
    protected abstract LogPanel primaryLogPanel();

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
     * Opens a position in the Board tab's analysis surface.
     * @param fen FEN string
     */
    protected abstract void openFenInBoard(String fen);

    /**
     * Opens a position in a new detached Board tab (its own board), leaving the
     * shared analysis board untouched.
     * @param fen FEN string
     */
    protected abstract void openFenInNewBoard(String fen);

    /**
     * Shows a game ply.
     * @param ply game ply index
     */
    protected abstract void showGamePly(int ply);

    /**
     * Loads a parsed PGN game on the shared board.
     *
     * @param game parsed game
     */
    protected abstract void loadGame(Game game);

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
