/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package application.gui.workbench.window;

import application.Config;
import application.gui.workbench.Defaults;
import application.gui.workbench.board.BoardPanel;
import application.gui.workbench.command.BatchPanel;
import application.gui.workbench.command.CommandForm;
import application.gui.workbench.command.CommandPalette.PaletteAction;
import application.gui.workbench.command.CommandPalette;
import application.gui.workbench.command.CommandRunner;
import application.gui.workbench.command.CommandTemplates.CommandTemplate;
import application.gui.workbench.command.CommandTemplates.TemplateContext;
import application.gui.workbench.command.Console;
import application.gui.workbench.dashboard.DashboardPanel;
import application.gui.workbench.game.EngineEval;
import application.gui.workbench.game.GameModel;
import application.gui.workbench.game.MovesModel;
import application.gui.workbench.layout.EditorSplitArea;
import application.gui.workbench.layout.SplitPaneStyler;
import application.gui.workbench.mcts.MctsPanel;
import application.gui.workbench.network.NetworkPanel;
import application.gui.workbench.publish.PublishingPanel;
import application.gui.workbench.publish.ReportPanel;
import application.gui.workbench.session.Job;
import application.gui.workbench.session.RunArtifacts;
import application.gui.workbench.session.Session;
import application.gui.workbench.ui.AnalysisGraph;
import application.gui.workbench.ui.EvalBar;
import application.gui.workbench.ui.Toast;
import application.gui.workbench.ui.ToggleBox;
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
import java.awt.BorderLayout;
import java.awt.Component;
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
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
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
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.JTextComponent;

import static application.gui.workbench.command.CommandArgs.addOptionalPositiveIntegerArg;
import static application.gui.workbench.command.CommandArgs.addOptionalTextArg;
import static application.gui.workbench.ui.SwingTasks.runAsync;
import static application.gui.workbench.ui.Ui.addVerticalFiller;
import static application.gui.workbench.ui.Ui.button;
import static application.gui.workbench.ui.Ui.buttonRow;
import static application.gui.workbench.ui.Ui.changeListener;
import static application.gui.workbench.ui.Ui.collapsible;
import static application.gui.workbench.ui.Ui.constraints;
import static application.gui.workbench.ui.Ui.fillViewport;
import static application.gui.workbench.ui.Ui.flow;
import static application.gui.workbench.ui.Ui.grid;
import static application.gui.workbench.ui.Ui.iconButton;
import static application.gui.workbench.ui.Ui.label;
import static application.gui.workbench.ui.Ui.optionGroup;
import static application.gui.workbench.ui.Ui.placeholder;
import static application.gui.workbench.ui.Ui.scroll;
import static application.gui.workbench.ui.Ui.showConfirmDialog;
import static application.gui.workbench.ui.Ui.styleAreas;
import static application.gui.workbench.ui.Ui.styleFields;
import static application.gui.workbench.ui.Ui.styleSpinners;
import static application.gui.workbench.ui.Ui.tabbedPane;
import static application.gui.workbench.ui.Ui.titled;
import static application.gui.workbench.ui.Ui.transparentPanel;
import static application.gui.workbench.ui.Ui.trimmed;
import static application.gui.workbench.ui.Ui.withTooltip;

/**
 * A new native Swing command and analysis workbench for ChessRTK.
 */
@SuppressWarnings("java:S6539")

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
     * Publishing tab index.
     */
    protected static final int TAB_PUBLISH = 4;

    /**
     * Console tab index.
     */
    protected static final int TAB_CONSOLE = 5;

    /**
     * Network visualizer tab index.
     */
    protected static final int TAB_NETWORK = 6;

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
    protected final NetworkPanel networkPanel = new NetworkPanel();

    /**
     * Leela-style PUCT search visualizer.
     */
    protected final MctsPanel mctsPanel = new MctsPanel();

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
     * Tag list.
     */
    protected final JList<String> tagList = new JList<>(tagModel);

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
     * Command execution state label.
     */
    protected final JLabel commandStateLabel = new JLabel("Idle");

    /**
     * Command text field.
     */
    protected final JTextField commandField = new JTextField();

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
    protected final JCheckBox liveEngineToggle = withTooltip(new ToggleBox("Live external engine"),
            "Continuously analyze the current board with the configured external UCI engine");

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
    protected final PublishingPanel publishingPanel =
    new PublishingPanel(new WindowPublishingHost(this));

    /**
     * Current position.
     */
    protected Position currentPosition;

    /**
     * Legal moves currently shown.
     */
    protected short[] visibleMoves = new short[0];

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
     * Background tag worker.
     */
    protected SwingWorker<List<String>, Void> tagWorker;

    /**
     * Guards against recursive duration-field synchronization.
     */
    protected boolean syncingDuration;

    /** Runs the built-in engine search action. */
    protected abstract void runBuiltInSearch();

    /** Runs the best-move action. */
    protected abstract void runBestMove();

    /** Runs the analysis action. */
    protected abstract void runAnalyze();

    /** Runs the tag-generation action. */
    protected abstract void runTagsCommand();

    /** Runs the perft action. */
    protected abstract void runPerft();

    /** Runs the engine-smoke health check. */
    protected abstract void runEngineSmoke();

    /** Runs configuration validation. */
    protected abstract void runConfigValidate();

    /** Runs the doctor health check. */
    protected abstract void runDoctor();

    /** Runs all health checks. */
    protected abstract void runAllHealthChecks();

    /** Copies text to the clipboard and console.
     * @param text source text */
    protected abstract void copyText(String text);

    /** Selects the workbench tab at the supplied index.
     * @param index tab index */
    protected abstract void selectTab(int index);

    /** Runs a command with optional standard input.
     * @param args command arguments
     * @param stdin standard input text, or null for none */
    protected abstract void runCommand(List<String> args, String stdin);

    /** Runs the selected command-template action. */
    protected abstract void runSelectedTemplate();

    /** Copies the currently built command. */
    protected abstract void copyBuiltCommand();

    /** Resets the selected command template. */
    protected abstract void resetSelectedTemplate();

    /** Clears optional command-template options. */
    protected abstract void clearOptionalTemplateOptions();

    /** Updates command-builder option controls. */
    protected abstract void updateCommandOptions();

    /** Updates the command-builder preview text. */
    protected abstract void updateBuiltCommand();

    /** Refreshes dynamic command previews. */
    protected abstract void updateCommandPreviews();

    /** Returns the selected template arguments.
     * @return selected command-template arguments */
    protected abstract List<String> selectedTemplateArgs();

    /** Installs command templates. */
    protected abstract void installTemplates();

    /** Returns the current FEN.
     * @return current FEN string */
    protected abstract String currentFen();

    /** Returns the current command-template context.
     * @return current command-template context */
    protected abstract TemplateContext templateContext();

    /** Appends text to the console.
     * @param text source text */
    protected abstract void appendConsole(String text);

    /** Shows a toast.
     * @param kind toast kind
     * @param message message text */
    protected abstract void toast(Toast.Kind kind, String message);

    /** Shows a warning notice.
     * @param title dialog title
     * @param message message text */
    protected abstract void showWarning(String title, String message);

    /** Shows an error notice.
     * @param title dialog title
     * @param message message text */
    protected abstract void showError(String title, String message);

    /** Generates the report panel content. */
    protected abstract void generateReport();

    /** Copies the generated report. */
    protected abstract void copyReport();

    /** Runs the selected publishing workflow. */
    protected abstract void runPublishingCommand();

    /** Stops the foreground command. */
    protected abstract void stopCommand();

    /** Requests a publish command refresh. */
    protected abstract void updatePublishCommand();

    /** Synchronizes batch duration into analysis controls.
     * @param value new value */
    protected abstract void syncDurationFromBatch(String value);

    /** Starts the debounced eval command. */
    protected abstract void startEvalCommand();

    /** Cancels the eval-bar command. */
    protected abstract void cancelEvalCommand();

    /** Restarts live external analysis. */
    protected abstract void restartLiveAnalysis();

    /** Pauses hidden live analysis. */
    protected abstract void pauseHiddenLiveAnalysis();

    /** Requests a live-analysis update. */
    protected abstract void requestLiveAnalysisUpdate();

    /** Stops live external analysis. */
    protected abstract void stopLiveAnalysis();

    /** Enables or disables live external analysis.
     * @param enabled true to enable the behavior */
    protected abstract void setLiveExternalEngineEnabled(boolean enabled);

    /** Returns true when a live-analysis worker exists.
     * @return true when a live-analysis worker exists */
    protected abstract boolean hasLiveEngineWorker();

    /** Creates the Analyze tab.
     * @return computed value */
    protected abstract JComponent createBoardTab();

    /**
     * Creates a detached analysis tab instance.
     *
     * @return detached analysis tab
     */
    protected abstract JComponent createDetachedAnalysisTab();

    /** Creates the Commands tab.
     * @return computed value */
    protected abstract JComponent createCommandTab();

    /** Creates the Batch tab.
     * @return computed value */
    protected abstract JComponent createBatchTab();

    /** Creates the Publish tab.
     * @return computed value */
    protected abstract JComponent createPublishTab();

    /** Creates the Console tab.
     * @return computed value */
    protected abstract JComponent createConsolePanel();

    /** Navigates the loaded game by a relative ply delta.
     * @param delta relative ply delta */
    protected abstract void navigateGame(int delta);

    /** Jumps the loaded game to an absolute ply.
     * @param ply game ply index */
    protected abstract void jumpGameTo(int ply);

    /** Focuses the option filter. */
    protected abstract void focusOptionFilter();

    /** Focuses the FEN field. */
    protected abstract void focusFenField();

    /** Focuses the game-input field. */
    protected abstract void focusGameInput();

    /** Shows display settings. */
    protected abstract void showDisplaySettings();

    /** Shows engine settings. */
    protected abstract void showEngineSettings();

    /** Shows analysis data. */
    protected abstract void showAnalysisData();

    /** Copies the analysis CSV. */
    protected abstract void copyAnalysisCsv();

    /** Copies the analysis report. */
    protected abstract void copyAnalysisReport();

    /** Prints the analysis report. */
    protected abstract void printAnalysisReport();

    /** Clears the analysis data. */
    protected abstract void clearAnalysisData();

    /** Resets engine settings to defaults. */
    protected abstract void resetEngineSettings();

    /** Plays a move from the board.
     * @param move move encoded in CRTK move format */
    protected abstract void playMove(short move);

    /** Applies the current FEN field. */
    protected abstract void setPositionFromField();

    /** Starts a new game from a FEN.
     * @param fen FEN string */
    protected abstract void startNewGame(String fen);

    /** Shows a game ply.
     * @param ply game ply index */
    protected abstract void showGamePly(int ply);

    /** Updates the legal-move table. */
    protected abstract void updateMoves();

    /** Updates the position status label. */
    protected abstract void updateStatus();

    /** Updates generated tags asynchronously. */
    protected abstract void updateTagsAsync();

    /** Updates game-state text. */
    protected abstract void updateGameState();

    /** Configures the game table. */
    protected abstract void configureGameTable();

    /** Loads game text as PGN, FEN, or SAN/UCI line.
     * @param text source text */
    protected abstract void loadGameText(String text);

    /** Opens a game file. */
    protected abstract void loadGameFile();

    /** Saves the current game as PGN. */
    protected abstract void savePgnFile();

    /** Loads a move line from text.
     * @param text source text */
    protected abstract void loadMoveLine(String text);

    /** Synchronizes analysis duration into batch controls. */
    protected abstract void syncDurationFromAnalysis();

    /** Requests command-preview refresh. */
    protected abstract void requestCommandPreviews();

    /** Requests an eval refresh. */
    protected abstract void requestEvalUpdate();

    /** Builds eval-bar CLI arguments for a FEN.
     * @param fen FEN string
     * @return CLI arguments for the eval-bar command */
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

    /** Returns the engine protocol field value.
     * @return engine protocol field value */
    protected abstract String engineProtocolValue();

    /** Returns the engine nodes field value.
     * @return engine nodes field value */
    protected abstract String engineNodesValue();

    /** Returns the engine hash field value.
     * @return engine hash field value */
    protected abstract String engineHashValue();

    /** Returns the duration field value.
     * @return duration field value */
    protected abstract String durationValue();

    /** Returns the depth field value.
     * @return depth field value */
    protected abstract String depthValue();

    /** Returns the MultiPV field value.
     * @return MultiPV field value */
    protected abstract String multipvValue();

    /** Returns the thread-count field value.
     * @return thread-count field value */
    protected abstract String threadsValue();

    /** Highlights a move from command output when appropriate.
     * @param args command arguments
     * @param output command or engine output text */
    protected abstract void maybeHighlightMove(List<String> args, String output);

    /** Applies health status from a finished command.
     * @param args command arguments
     * @param exitCode process exit code */
    protected abstract void updateHealthFromCommand(List<String> args, int exitCode);

    /** Applies health failure status from a failed command.
     * @param args command arguments */
    protected abstract void updateHealthFailedFromCommand(List<String> args);

    /** Updates the command-state label.
     * @param state state text */
    protected abstract void setCommandState(String state);

    /** Parses engine eval output for the eval bar.
     * @param output command or engine output text
     * @return parsed engine evaluation */
    protected static EngineEval parseEngineEval(String output) {
        return EngineEval.parse(output);
    }
}
