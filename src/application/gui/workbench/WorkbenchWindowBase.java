package application.gui.workbench;

import static application.gui.workbench.WorkbenchCommandArgs.addOptionalPositiveIntegerArg;
import static application.gui.workbench.WorkbenchCommandArgs.addOptionalTextArg;
import static application.gui.workbench.WorkbenchUi.addVerticalFiller;
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
import static application.gui.workbench.WorkbenchUi.optionGroup;
import static application.gui.workbench.WorkbenchUi.placeholder;
import static application.gui.workbench.WorkbenchUi.scroll;
import static application.gui.workbench.WorkbenchUi.showConfirmDialog;
import static application.gui.workbench.WorkbenchUi.styleAreas;
import static application.gui.workbench.WorkbenchUi.styleFields;
import static application.gui.workbench.WorkbenchUi.styleSpinners;
import static application.gui.workbench.WorkbenchUi.tabbedPane;
import static application.gui.workbench.WorkbenchUi.titled;
import static application.gui.workbench.WorkbenchUi.trimmed;
import static application.gui.workbench.WorkbenchUi.transparentPanel;
import static application.gui.workbench.WorkbenchUi.withTooltip;
import static application.gui.workbench.WorkbenchSwingTasks.runAsync;

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
import application.gui.workbench.WorkbenchCommandTemplates.CommandTemplate;
import application.gui.workbench.WorkbenchCommandTemplates.TemplateContext;
import application.gui.workbench.WorkbenchCommandPalette.PaletteAction;
import application.gui.workbench.layout.EditorSplitArea;
import application.gui.workbench.layout.SplitPaneStyler;
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

abstract class WorkbenchWindowBase extends JFrame {
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
    protected final WorkbenchBoardPanel board = new WorkbenchBoardPanel();

    /**
     * Engine evaluation bar.
     */
    protected final WorkbenchEvalBar evalBar = new WorkbenchEvalBar();

    /**
     * Live analysis data graph.
     */
    protected final WorkbenchAnalysisGraph analysisGraph = new WorkbenchAnalysisGraph();

    /**
     * Network-architecture visualizer (NNUE / lc0-CNN / lc0-BT4).
     */
    protected final WorkbenchNetworkPanel networkPanel = new WorkbenchNetworkPanel();

    /**
     * Leela-style PUCT search visualizer.
     */
    protected final MctsPanel mctsPanel = new MctsPanel();

    /**
     * Shared, observable session model the Dashboard tab renders from.
     */
    protected final WorkbenchSession session = new WorkbenchSession();

    /**
     * Run-log, manifest, and artifact controller.
     */
    protected final WorkbenchRunArtifacts runArtifacts = new WorkbenchRunArtifacts(new RunArtifactHost());

    /**
     * Operational overview tab, rendered from {@link #session}.
     */
    protected final WorkbenchDashboardPanel dashboardPanel =
            new WorkbenchDashboardPanel(session, new DashboardActions());

    /**
     * Job record for the foreground command currently tracked, or null.
     */
    protected WorkbenchJob runningJob;

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
     * Routes {@link WorkbenchDashboardPanel} quick actions to the window's
     * existing protected actions, so the dashboard never needs those methods
     * widened to package or public visibility.
     */
    protected final class DashboardActions implements WorkbenchDashboardActions {

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
            batchPanel.runBatch();
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
            WorkbenchWindowBase.this.runAllHealthChecks();
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
            runArtifacts.openManifest(job);
        }

        @Override
        public void openJobLog(WorkbenchJob job) {
            if (job == null) {
                return;
            }
            runArtifacts.openLog(job);
        }
    }

    /**
     * Host callbacks for the extracted report panel.
     */
    protected final class ReportHost implements WorkbenchReportPanel.Host {

        @Override
        public Component owner() {
            return WorkbenchWindowBase.this;
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
            WorkbenchWindowBase.this.copyText(text);
        }

        @Override
        public void appendConsole(String text) {
            WorkbenchWindowBase.this.appendConsole(text);
        }

        @Override
        public void toast(WorkbenchToast.Kind kind, String message) {
            WorkbenchWindowBase.this.toast(kind, message);
        }

        @Override
        public void showError(String title, String message) {
            WorkbenchWindowBase.this.showError(title, message);
        }
    }

    /**
     * Host callbacks for the extracted batch panel.
     */
    protected final class BatchHost implements WorkbenchBatchPanel.Host {

        @Override
        public String currentFen() {
            return WorkbenchWindowBase.this.currentFen();
        }

        @Override
        public TemplateContext templateContext() {
            return WorkbenchWindowBase.this.templateContext();
        }

        @Override
        public void runCommand(List<String> args, String stdin) {
            WorkbenchWindowBase.this.runCommand(args, stdin);
        }

        @Override
        public void copyText(String text) {
            WorkbenchWindowBase.this.copyText(text);
        }

        @Override
        public void showError(String title, String message) {
            WorkbenchWindowBase.this.showError(title, message);
        }

        @Override
        public void updateBatchSummary(String summary) {
            session.updateBatch(summary);
        }

        @Override
        public void updatePublishCommand() {
            if (publishingPanel != null) {
                WorkbenchWindowBase.this.updatePublishCommand();
            }
        }

        @Override
        public void syncBatchDuration(String value) {
            WorkbenchWindowBase.this.syncDurationFromBatch(value);
        }
    }

    /**
     * Host callbacks for the extracted publishing panel.
     */
    protected final class PublishingHost implements WorkbenchPublishingPanel.Host {

        @Override
        public Component owner() {
            return WorkbenchWindowBase.this;
        }

        @Override
        public String currentFen() {
            return WorkbenchWindowBase.this.currentFen();
        }

        @Override
        public WorkbenchGameModel gameModel() {
            return gameModel;
        }

        @Override
        public String batchInputText() {
            return batchPanel.inputText();
        }

        @Override
        public JComponent reportPanel() {
            return reportPanel.component();
        }

        @Override
        public void generateReport() {
            WorkbenchWindowBase.this.generateReport();
        }

        @Override
        public void runCommand(List<String> args, String stdin) {
            WorkbenchWindowBase.this.runCommand(args, stdin);
        }

        @Override
        public void copyText(String text) {
            WorkbenchWindowBase.this.copyText(text);
        }

        @Override
        public void stopCommand() {
            WorkbenchWindowBase.this.stopCommand();
        }

        @Override
        public void toast(WorkbenchToast.Kind kind, String message) {
            WorkbenchWindowBase.this.toast(kind, message);
        }

        @Override
        public void showError(String title, String message) {
            WorkbenchWindowBase.this.showError(title, message);
        }
    }

    /**
     * Host callbacks for the extracted run-artifact controller.
     */
    protected final class RunArtifactHost implements WorkbenchRunArtifacts.Host {

        @Override
        public WorkbenchSession session() {
            return session;
        }

        @Override
        public void appendConsole(String text) {
            WorkbenchWindowBase.this.appendConsole(text);
        }

        @Override
        public void showWarning(String title, String message) {
            WorkbenchWindowBase.this.showWarning(title, message);
        }

        @Override
        public void showError(String title, String message) {
            WorkbenchWindowBase.this.showError(title, message);
        }
    }

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
    protected final WorkbenchMovesModel movesModel = new WorkbenchMovesModel();

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
    protected final WorkbenchReportPanel reportPanel = new WorkbenchReportPanel(new ReportHost());

    /**
     * Console output with carriage-return handling and line highlighting.
     */
    protected final WorkbenchConsole console = new WorkbenchConsole();

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
    protected final WorkbenchCommandForm commandForm = new WorkbenchCommandForm();

    /**
     * Main-line move history model.
     */
    protected final WorkbenchGameModel gameModel = new WorkbenchGameModel();

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
     * Duration field.
     */
    protected final JTextField analysisDurationField = new JTextField("2s");

    /**
     * Shared depth model.
     */
    protected final SpinnerNumberModel depthModel = new SpinnerNumberModel(4, 1, 99, 1);

    /**
     * Analysis depth control.
     */
    protected final JSpinner analysisDepthSpinner = new JSpinner(depthModel);

    /**
     * Shared MultiPV model.
     */
    protected final SpinnerNumberModel multipvModel = new SpinnerNumberModel(3, 1, 20, 1);

    /**
     * Analysis MultiPV control.
     */
    protected final JSpinner analysisMultipvSpinner = new JSpinner(multipvModel);

    /**
     * Shared thread-count model.
     */
    protected final SpinnerNumberModel threadsModel = new SpinnerNumberModel(1, 1, 256, 1);

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
    protected final JCheckBox liveEngineToggle = withTooltip(new WorkbenchToggleBox("Live external engine"),
            "Continuously analyze the current board with the configured external UCI engine");

    /**
     * Batch workflow panel.
     */
    protected final WorkbenchBatchPanel batchPanel = new WorkbenchBatchPanel(new BatchHost(), depthModel, multipvModel,
            threadsModel);

    /**
     * Publishing workflow panel.
     */
    protected final WorkbenchPublishingPanel publishingPanel = new WorkbenchPublishingPanel(new PublishingHost());

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
    protected WorkbenchCommandRunner.RunningCommand runningCommand;

    /**
     * Running eval-bar engine command.
     */
    protected WorkbenchCommandRunner.RunningCommand evalCommand;

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
    protected WorkbenchCommandPalette commandPalette;

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

    /** Copies text to the clipboard and console. */
    protected abstract void copyText(String text);

    /** Selects the workbench tab at the supplied index. */
    protected abstract void selectTab(int index);

    /** Runs a command with optional standard input. */
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

    /** Returns the selected template arguments. */
    protected abstract List<String> selectedTemplateArgs();

    /** Installs command templates. */
    protected abstract void installTemplates();

    /** Returns the current FEN. */
    protected abstract String currentFen();

    /** Returns the current command-template context. */
    protected abstract TemplateContext templateContext();

    /** Appends text to the console. */
    protected abstract void appendConsole(String text);

    /** Shows a toast. */
    protected abstract void toast(WorkbenchToast.Kind kind, String message);

    /** Shows a warning notice. */
    protected abstract void showWarning(String title, String message);

    /** Shows an error notice. */
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

    /** Synchronizes batch duration into analysis controls. */
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

    /** Enables or disables live external analysis. */
    protected abstract void setLiveExternalEngineEnabled(boolean enabled);

    /** Returns true when a live-analysis worker exists. */
    protected abstract boolean hasLiveEngineWorker();

    /** Creates the Analyze tab. */
    protected abstract JComponent createBoardTab();

    /** Creates the Commands tab. */
    protected abstract JComponent createCommandTab();

    /** Creates the Batch tab. */
    protected abstract JComponent createBatchTab();

    /** Creates the Publish tab. */
    protected abstract JComponent createPublishTab();

    /** Creates the Console tab. */
    protected abstract JComponent createConsolePanel();

    /** Navigates the loaded game by a relative ply delta. */
    protected abstract void navigateGame(int delta);

    /** Jumps the loaded game to an absolute ply. */
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

    /** Plays a move from the board. */
    protected abstract void playMove(short move);

    /** Applies the current FEN field. */
    protected abstract void setPositionFromField();

    /** Starts a new game from a FEN. */
    protected abstract void startNewGame(String fen);

    /** Shows a game ply. */
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

    /** Loads game text as PGN, FEN, or SAN/UCI line. */
    protected abstract void loadGameText(String text);

    /** Opens a game file. */
    protected abstract void loadGameFile();

    /** Saves the current game as PGN. */
    protected abstract void savePgnFile();

    /** Loads a move line from text. */
    protected abstract void loadMoveLine(String text);

    /** Synchronizes analysis duration into batch controls. */
    protected abstract void syncDurationFromAnalysis();

    /** Requests command-preview refresh. */
    protected abstract void requestCommandPreviews();

    /** Requests an eval refresh. */
    protected abstract void requestEvalUpdate();

    /** Builds eval-bar CLI arguments for a FEN. */
    protected abstract List<String> buildEvalBarArgs(String fen);

    /** Returns the engine protocol field value. */
    protected abstract String engineProtocolValue();

    /** Returns the engine nodes field value. */
    protected abstract String engineNodesValue();

    /** Returns the engine hash field value. */
    protected abstract String engineHashValue();

    /** Returns the duration field value. */
    protected abstract String durationValue();

    /** Returns the depth field value. */
    protected abstract String depthValue();

    /** Returns the MultiPV field value. */
    protected abstract String multipvValue();

    /** Returns the thread-count field value. */
    protected abstract String threadsValue();

    /** Highlights a move from command output when appropriate. */
    protected abstract void maybeHighlightMove(List<String> args, String output);

    /** Applies health status from a finished command. */
    protected abstract void updateHealthFromCommand(List<String> args, int exitCode);

    /** Applies health failure status from a failed command. */
    protected abstract void updateHealthFailedFromCommand(List<String> args);

    /** Updates the command-state label. */
    protected abstract void setCommandState(String state);

    /** Parses engine eval output for the eval bar. */
    protected static WorkbenchEngineEval parseEngineEval(String output) {
        return WorkbenchEngineEval.parse(output);
    }
}
