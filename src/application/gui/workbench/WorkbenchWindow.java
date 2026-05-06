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
import static application.gui.workbench.WorkbenchUi.onTextChange;
import static application.gui.workbench.WorkbenchUi.scroll;
import static application.gui.workbench.WorkbenchUi.showConfirmDialog;
import static application.gui.workbench.WorkbenchUi.showErrorDialog;
import static application.gui.workbench.WorkbenchUi.styleAreas;
import static application.gui.workbench.WorkbenchUi.styleCheckBox;
import static application.gui.workbench.WorkbenchUi.styleCombos;
import static application.gui.workbench.WorkbenchUi.styleFileChooser;
import static application.gui.workbench.WorkbenchUi.styleFields;
import static application.gui.workbench.WorkbenchUi.styleSpinners;
import static application.gui.workbench.WorkbenchUi.tabbedPane;
import static application.gui.workbench.WorkbenchUi.titled;
import static application.gui.workbench.WorkbenchUi.transparentPanel;

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
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;
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

/**
 * A new native Swing command and analysis workbench for ChessRTK.
 */
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
     * Engine evaluation line pattern.
     */
    private static final Pattern EVAL_PATTERN = Pattern.compile("(?m)^\\s*eval:\\s*(#-?\\d+|[+-]?\\d+)\\b");

    /**
     * Short engine duration for automatic eval-bar refresh.
     */
    private static final String EVAL_BAR_DURATION = "350ms";

    /**
     * Analysis tab index.
     */
    private static final int TAB_ANALYZE = 0;

    /**
     * Commands tab index.
     */
    private static final int TAB_COMMANDS = 1;

    /**
     * Batch tab index.
     */
    private static final int TAB_BATCH = 2;

    /**
     * Publishing tab index.
     */
    private static final int TAB_PUBLISH = 3;

    /**
     * Console tab index.
     */
    private static final int TAB_CONSOLE = 4;

    /**
     * Publishing task type with stable identity and display label.
     */
    private enum PublishTask {
        /** Direct diagram PDFs. */
        DIAGRAMS("Diagrams PDF"),
        /** Render a manifest PDF. */
        RENDER("Render Manifest PDF"),
        /** Build a puzzle collection. */
        COLLECTION("Puzzle Collection"),
        /** Render a study book. */
        STUDY("Study Book"),
        /** Render a cover PDF. */
        COVER("Cover PDF");

        private final String label;

        PublishTask(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Diagram source type with stable identity and display label.
     */
    private enum PublishSource {
        /** The current board position. */
        CURRENT_FEN("Current FEN"),
        /** The workbench game PGN. */
        GAME_PGN("Game PGN"),
        /** The batch FEN editor. */
        BATCH_FENS("Batch FENs"),
        /** A user-selected file. */
        EXISTING_FILE("Existing File");

        private final String label;

        PublishSource(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Placeholder path used in command previews for generated PGN files.
     */
    private static final String WORKBENCH_GAME_PLACEHOLDER = "<workbench-game.pgn>";

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
     * Default diagram PDF output path.
     */
    private static final String DEFAULT_DIAGRAMS_OUTPUT = "workbench-diagrams.pdf";

    /**
     * Default interior PDF output path.
     */
    private static final String DEFAULT_BOOK_OUTPUT = "workbench-book.pdf";

    /**
     * Default cover PDF output path.
     */
    private static final String DEFAULT_COVER_OUTPUT = "workbench-cover.pdf";

    /**
     * Default manifest output path.
     */
    private static final String DEFAULT_MANIFEST_OUTPUT = "workbench-book.toml";

    /**
     * Board view.
     */
    private final WorkbenchBoardPanel board = new WorkbenchBoardPanel();

    /**
     * Engine evaluation bar.
     */
    private final WorkbenchEvalBar evalBar = new WorkbenchEvalBar();

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
     * Console output.
     */
    private final JTextArea console = new JTextArea();

    /**
     * Command execution state label.
     */
    private final JLabel commandStateLabel = new JLabel("Idle");

    /**
     * Command text field.
     */
    private final JTextField commandField = new JTextField();

    /**
     * Command option filter field.
     */
    private final JTextField optionFilterField = new JTextField();

    /**
     * Template selector.
     */
    private final JComboBox<CommandTemplate> templateCombo = new JComboBox<>();

    /**
     * Command option table model.
     */
    private final WorkbenchOptionTableModel optionModel = new WorkbenchOptionTableModel();

    /**
     * Command option table.
     */
    private final JTable optionTable = new JTable(optionModel);

    /**
     * Button that expands or collapses command option descriptions.
     */
    private final JButton optionInfoButton = button("Info +", false, event -> toggleCommandInfo());

    /**
     * Optional command description table column.
     */
    private TableColumn optionDescriptionColumn;

    /**
     * Whether command option descriptions are visible.
     */
    private boolean commandInfoVisible;

    /**
     * Sorter and filter for command option rows.
     */
    private final TableRowSorter<WorkbenchOptionTableModel> optionSorter = new TableRowSorter<>(optionModel);

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
    private final SpinnerNumberModel threadsModel = new SpinnerNumberModel(
            Math.max(1, Runtime.getRuntime().availableProcessors()), 1, 256, 1);

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
    private final JTextField batchCommandField = new JTextField();

    /**
     * Batch task selector.
     */
    private final JComboBox<BatchTask> batchTaskCombo = new JComboBox<>();

    /**
     * Publishing task selector.
     */
    private final JComboBox<PublishTask> publishTaskCombo = new JComboBox<>(PublishTask.values());

    /**
     * Diagram publishing source selector.
     */
    private final JComboBox<PublishSource> publishSourceCombo = new JComboBox<>(PublishSource.values());

    /**
     * Publishing input path field.
     */
    private final JTextField publishInputField = new JTextField();

    /**
     * Publishing primary output path field.
     */
    private final JTextField publishOutputField = new JTextField(DEFAULT_DIAGRAMS_OUTPUT);

    /**
     * Publishing PDF or interior path field.
     */
    private final JTextField publishPdfOutputField = new JTextField();

    /**
     * Publishing cover output path field.
     */
    private final JTextField publishCoverOutputField = new JTextField(DEFAULT_COVER_OUTPUT);

    /**
     * Publishing title field.
     */
    private final JTextField publishTitleField = new JTextField("ChessRTK Workbench");

    /**
     * Publishing subtitle field.
     */
    private final JTextField publishSubtitleField = new JTextField();

    /**
     * Publishing limit field.
     */
    private final JTextField publishLimitField = new JTextField();

    /**
     * Publishing page-count field.
     */
    private final JTextField publishPagesField = new JTextField();

    /**
     * Publishing validation toggle.
     */
    private final JCheckBox publishValidateBox = withTooltip(new WorkbenchToggleBox("validate only"),
            "Adds --check: validate inputs without writing output");

    /**
     * Returns the toggle after attaching a tooltip describing the underlying
     * CLI flag, since the chip labels alone are too terse to discover.
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
     * Publishing board-orientation toggle.
     */
    private final JCheckBox publishFlipBox = withTooltip(new WorkbenchToggleBox("black down"),
            "Adds --flip: render diagrams with Black at the bottom");

    /**
     * Publishing FEN visibility toggle.
     */
    private final JCheckBox publishNoFenBox = withTooltip(new WorkbenchToggleBox("hide FEN"),
            "Adds --no-fen: omit FEN captions under generated diagrams");

    /**
     * Publishing command preview field.
     */
    private final JTextField publishCommandField = new JTextField();

    /**
     * Publishing input chooser button.
     */
    private JButton publishInputButton;

    /**
     * Publishing output chooser button.
     */
    private JButton publishOutputButton;

    /**
     * Publishing PDF chooser button.
     */
    private JButton publishPdfOutputButton;

    /**
     * Publishing cover chooser button.
     */
    private JButton publishCoverOutputButton;

    /**
     * Position report note field.
     */
    private final JTextField reportNoteField = new JTextField();

    /**
     * Position report preview and editor.
     */
    private final JTextArea reportPreview = new JTextArea();

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
     * Running command.
     */
    private WorkbenchCommandRunner.RunningCommand runningCommand;

    /**
     * Running eval-bar engine command.
     */
    private WorkbenchCommandRunner.RunningCommand evalCommand;

    /**
     * Monotonic eval-bar request id.
     */
    private long evalRequestId;

    /**
     * Debounce delay for eval-bar refresh, in ms.
     */
    private static final int EVAL_DEBOUNCE_MS = 90;

    /**
     * Debounce timer that coalesces eval refresh requests.
     */
    private final Timer evalDebounceTimer = createEvalDebounceTimer();

    /**
     * Whether a command-preview refresh is already queued on the event thread.
     */
    private boolean commandPreviewUpdateQueued;

    /**
     * Whether an option-filter refresh is already queued on the event thread.
     */
    private boolean optionFilterUpdateQueued;

    /**
     * Whether a publishing preview refresh is already queued on the event thread.
     */
    private boolean publishCommandUpdateQueued;

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
     * Main workbench tabs.
     */
    private JTabbedPane tabs;

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
        requestEvalUpdate();
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
        if (!autoEvalBarEnabled) {
            evalDebounceTimer.stop();
            cancelEvalCommand();
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
        evalDebounceTimer.stop();
        if (runningCommand != null && runningCommand.isRunning()) {
            runningCommand.cancel();
        }
        if (tagWorker != null && !tagWorker.isDone()) {
            tagWorker.cancel(true);
        }
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
        root.add(createHeader(), BorderLayout.NORTH);

        tabs = tabbedPane();
        tabs.addTab("Analyze", createBoardTab());
        tabs.addTab("Commands", createCommandTab());
        tabs.addTab("Batch", createBatchTab());
        tabs.addTab("Publish", createPublishTab());
        tabs.addTab("Console", createConsolePanel());
        tabs.setSelectedIndex(TAB_ANALYZE);

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
     * Creates the header.
     *
     * @return header component
     */
    private JComponent createHeader() {
        JPanel header = new WorkbenchSurfacePanel(new BorderLayout(10, 8));
        header.setBackground(WorkbenchTheme.PANEL);

        styleFields(fenField);
        fenField.addActionListener(event -> setPositionFromField());
        header.add(fenField, BorderLayout.CENTER);

        header.add(buttonRow(FlowLayout.RIGHT,
                button("Load", true, event -> setPositionFromField()),
                iconButton("Back", event -> navigateGame(-1)),
                iconButton("Forward", event -> navigateGame(1)),
                iconButton("Reset", event -> startNewGame(Setup.getStandardStartFEN())),
                iconButton("Flip", event -> {
                    board.setWhiteDown(!board.isWhiteDown());
                    appendConsole("Board flipped\n");
                }),
                iconButton("Copy FEN", event -> copyText(fenField.getText())),
                iconButton("Settings", event -> showDisplaySettings()),
                iconButton("Actions", event -> showCommandPalette())), BorderLayout.EAST);

        return header;
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
        if (tabs == null || tabs.getSelectedIndex() != TAB_ANALYZE) {
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
                        () -> selectTab(TAB_CONSOLE)));
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
            boardDetailTabs.setSelectedIndex(2);
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
            boardDetailTabs.setSelectedIndex(3);
        }
        SwingUtilities.invokeLater(() -> engineProtocolField.requestFocusInWindow());
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
        SwingUtilities.invokeLater(() -> fenField.requestFocusInWindow());
    }

    /**
     * Focuses the command option filter.
     */
    private void focusOptionFilter() {
        selectTab(TAB_COMMANDS);
        SwingUtilities.invokeLater(() -> optionFilterField.requestFocusInWindow());
    }

    /**
     * Focuses the merged game-line editor.
     */
    private void focusGameInput() {
        selectTab(TAB_ANALYZE);
        if (analysisTabs != null) {
            analysisTabs.setSelectedIndex(1);
        }
        SwingUtilities.invokeLater(() -> gameInput.requestFocusInWindow());
    }

    /**
     * Selects a workbench tab by index.
     *
     * @param index tab index
     */
    private void selectTab(int index) {
        if (tabs != null && index >= 0 && index < tabs.getTabCount()) {
            tabs.setSelectedIndex(index);
        }
    }

    /**
     * Creates the board tab.
     *
     * @return tab component
     */
    private JComponent createBoardTab() {
        board.setMoveHandler(this::playMove);
        evalBar.setToolTipText("Engine evaluation");

        JPanel boardStage = transparentPanel(new BorderLayout(8, 0));
        boardStage.add(evalBar, BorderLayout.WEST);
        boardStage.add(board, BorderLayout.CENTER);

        JPanel side = transparentPanel(new BorderLayout(8, 8));
        side.setPreferredSize(new Dimension(400, 560));
        side.add(createAnalysisControls(), BorderLayout.NORTH);
        side.add(createMovesAndTags(), BorderLayout.CENTER);

        JSplitPane boardPage = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, boardStage, side);
        boardPage.setBorder(BorderFactory.createEmptyBorder());
        boardPage.setOpaque(false);
        boardPage.setContinuousLayout(true);
        boardPage.setResizeWeight(0.68);
        boardPage.setDividerSize(8);
        boardPage.setDividerLocation(0.68);
        styleSplitPane(boardPage);

        analysisTabs = createSectionTabs();
        analysisTabs.addTab("Board", boardPage);
        analysisTabs.addTab("Game", createGameSection());
        return analysisTabs;
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
        grid(panel, collapsible("More", createAdvancedAnalysisControls(), false), c, 0, 4, 4, 1);
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
        boardDetailTabs.addTab("Settings", createDisplaySettingsPanel());
        boardDetailTabs.addTab("Engine", createEngineSettingsPanel());
        return boardDetailTabs;
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
                event -> choosePath(engineProtocolField, false, "Choose engine protocol",
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
        styleSplitPane(gamePage);
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
                button("Add to Batch", false, event -> {
                    appendCurrentFenToBatch();
                })), c, 1, 3, 3, 1);
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
        JPanel panel = new WorkbenchSurfacePanel(new GridBagLayout());
        GridBagConstraints c = constraints();
        grid(panel, WorkbenchTheme.section("Command Controller"), c, 0, 0, 4, 1);

        styleCombos(templateCombo);
        templateCombo.addActionListener(event -> {
            updateCommandOptions();
            updateBuiltCommand();
        });
        grid(panel, label("template"), c, 0, 1, 1, 1);
        grid(panel, templateCombo, c, 1, 1, 3, 1);

        configureOptionTable();
        optionModel.addTableModelListener(event -> updateBuiltCommand());
        depthModel.addChangeListener(event -> requestCommandPreviews());
        multipvModel.addChangeListener(event -> requestCommandPreviews());
        threadsModel.addChangeListener(event -> requestCommandPreviews());
        grid(panel, label("options"), c, 0, 2, 1, 1);
        styleFields(optionFilterField, commandField);
        onTextChange(this::requestOptionFilterUpdate, optionFilterField);
        grid(panel, optionFilterField, c, 1, 2, 3, 1);

        c.gridx = 0;
        c.gridy = 3;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.weightx = 0;
        c.weighty = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;
        panel.add(label("flags"), c);

        c.gridx = 1;
        c.gridy = 3;
        c.gridwidth = 3;
        c.gridheight = 1;
        c.weightx = 1;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        c.anchor = GridBagConstraints.CENTER;
        panel.add(scroll(optionTable), c);

        c.weighty = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;

        commandField.setEditable(false);
        grid(panel, label("command"), c, 0, 4, 1, 1);
        grid(panel, commandField, c, 1, 4, 3, 1);

        grid(panel, buttonRow(FlowLayout.LEFT,
                button("Run", true, event -> runSelectedTemplate()),
                button("Copy", false, event -> copyBuiltCommand()),
                button("Reset", false, event -> resetSelectedTemplate()),
                button("Drop Optional", false, event -> clearOptionalTemplateOptions()),
                optionInfoButton,
                button("Stop", false, event -> stopCommand())), c, 1, 5, 3, 1);

        updateCommandOptions();
        updateBuiltCommand();
        return panel;
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
        styleSplitPane(batchPage);
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
        styleFields(batchDurationField, batchCommandField);
        batchDurationField.getDocument()
                .addDocumentListener(changeListener(() -> syncDuration(batchDurationField, analysisDurationField)));
        styleSpinners(batchDepthSpinner, batchMultipvSpinner, batchThreadsSpinner);
        grid(controls, batchOptionsPanel, c, 1, 2, 3, 1);
        grid(controls, label("command"), c, 0, 3, 1, 1);
        batchCommandField.setColumns(36);
        batchCommandField.setEditable(false);
        grid(controls, batchCommandField, c, 1, 3, 3, 1);
        grid(controls, buttonRow(FlowLayout.LEFT,
                button("Run Batch", true, event -> runBatch()),
                button("Copy Command", false, event -> copyText(batchCommandField.getText()))), c, 1, 4, 3, 1);
        addVerticalFiller(controls, c, 5, 4);
        return controls;
    }

    /**
     * Creates the publishing and report tab.
     *
     * @return publish tab
     */
    private JComponent createPublishTab() {
        JPanel panel = transparentPanel(new BorderLayout(10, 10));

        JTabbedPane publishTabs = createSectionTabs();
        publishTabs.addTab("Report", createReportPanel());
        publishTabs.addTab("Book", scroll(fillViewport(createBookPublishingPanel())));
        panel.add(publishTabs, BorderLayout.CENTER);
        configurePublishControls();
        updatePublishControlState();
        generateReport();
        updatePublishCommand();
        return panel;
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
     * Styles a split pane as a quiet resizable workbench divider.
     *
     * @param pane split pane
     */
    private static void styleSplitPane(JSplitPane pane) {
        pane.setUI(new BasicSplitPaneUI() {
            /**
             * Creates a divider without the platform grip artifact.
             *
             * @return divider
             */
            @Override
            public BasicSplitPaneDivider createDefaultDivider() {
                return new BasicSplitPaneDivider(this) {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public void paint(java.awt.Graphics graphics) {
                        graphics.setColor(WorkbenchTheme.LINE);
                        int x = Math.max(0, getWidth() / 2);
                        graphics.drawLine(x, 0, x, getHeight());
                    }
                };
            }
        });
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.setBackground(WorkbenchTheme.BG);
    }

    /**
     * Creates the position and game report panel.
     *
     * @return report panel
     */
    private JComponent createReportPanel() {
        JPanel panel = new WorkbenchSurfacePanel(new BorderLayout(8, 8));
        panel.add(WorkbenchTheme.section("Report Maker"), BorderLayout.NORTH);

        styleFields(reportNoteField);
        reportNoteField.setToolTipText("Optional note included in the generated report.");
        JPanel body = transparentPanel(new BorderLayout(8, 8));
        JPanel noteRow = transparentPanel(new BorderLayout(8, 0));
        noteRow.add(label("note"), BorderLayout.WEST);
        noteRow.add(reportNoteField, BorderLayout.CENTER);
        body.add(noteRow, BorderLayout.NORTH);

        styleAreas(reportPreview);
        reportPreview.setRows(20);
        reportPreview.setLineWrap(false);
        reportPreview.setWrapStyleWord(false);
        reportPreview.setEditable(false);
        JPanel reportBox = transparentPanel(new BorderLayout(8, 0));
        reportBox.add(label("report"), BorderLayout.WEST);
        reportBox.add(scroll(reportPreview), BorderLayout.CENTER);
        body.add(reportBox, BorderLayout.CENTER);
        panel.add(body, BorderLayout.CENTER);

        panel.add(buttonRow(FlowLayout.LEFT,
                button("Generate Report", true, event -> generateReport()),
                button("Copy Report", false, event -> copyReport()),
                button("Save Report", false, event -> saveReportFile())), BorderLayout.SOUTH);
        return panel;
    }

    /**
     * Creates the book publishing command panel.
     *
     * @return publishing panel
     */
    private JComponent createBookPublishingPanel() {
        JPanel panel = new WorkbenchSurfacePanel(new GridBagLayout());
        GridBagConstraints c = constraints();
        grid(panel, WorkbenchTheme.section("Book Publishing"), c, 0, 0, 4, 1);

        styleCombos(publishTaskCombo, publishSourceCombo);
        grid(panel, label("task"), c, 0, 1, 1, 1);
        grid(panel, publishTaskCombo, c, 1, 1, 3, 1);

        grid(panel, label("source"), c, 0, 2, 1, 1);
        grid(panel, publishSourceCombo, c, 1, 2, 3, 1);

        publishInputField.setColumns(28);
        publishOutputField.setColumns(28);
        publishPdfOutputField.setColumns(28);
        publishCoverOutputField.setColumns(28);
        publishTitleField.setColumns(28);
        publishSubtitleField.setColumns(28);
        publishLimitField.setColumns(8);
        publishPagesField.setColumns(8);
        publishCommandField.setColumns(40);
        styleFields(publishInputField, publishOutputField, publishPdfOutputField, publishCoverOutputField,
                publishTitleField, publishSubtitleField, publishLimitField, publishPagesField, publishCommandField);
        publishInputButton = addChooserRow(panel, c, "input", publishInputField, "Choose Input", 3,
                () -> choosePath(publishInputField, false, "Choose publishing input"));
        publishOutputButton = addChooserRow(panel, c, "output", publishOutputField, "Choose Output", 4,
                () -> choosePath(publishOutputField, true, "Choose publishing output"));
        publishPdfOutputButton = addChooserRow(panel, c, "pdf/interior", publishPdfOutputField, "Choose PDF", 5,
                this::choosePublishPdfPath);
        publishCoverOutputButton = addChooserRow(panel, c, "cover", publishCoverOutputField, "Choose Cover", 6,
                () -> choosePath(publishCoverOutputField, true, "Choose cover output"));

        grid(panel, label("title"), c, 0, 7, 1, 1);
        grid(panel, publishTitleField, c, 1, 7, 3, 1);
        grid(panel, label("subtitle"), c, 0, 8, 1, 1);
        grid(panel, publishSubtitleField, c, 1, 8, 3, 1);

        JPanel limitRow = flow(FlowLayout.LEFT);
        limitRow.add(publishLimitField);
        limitRow.add(label("pages"));
        limitRow.add(publishPagesField);
        grid(panel, label("limit"), c, 0, 9, 1, 1);
        grid(panel, limitRow, c, 1, 9, 3, 1);

        JPanel toggles = flow(FlowLayout.LEFT);
        toggles.add(publishValidateBox);
        toggles.add(publishFlipBox);
        toggles.add(publishNoFenBox);
        grid(panel, toggles, c, 1, 10, 3, 1);

        publishCommandField.setEditable(false);
        grid(panel, label("command"), c, 0, 11, 1, 1);
        grid(panel, publishCommandField, c, 1, 11, 3, 1);

        grid(panel, buttonRow(FlowLayout.LEFT,
                button("Run Publishing", true, event -> runPublishingCommand()),
                button("Copy Command", false, event -> copyText(publishCommandField.getText())),
                button("Stop", false, event -> stopCommand())), c, 1, 12, 3, 1);
        addVerticalFiller(panel, c, 13, 4);
        return panel;
    }

    /**
     * Adds a publishing path row with a matching chooser button.
     *
     * @param panel target panel
     * @param c shared constraints
     * @param labelText row label
     * @param field path field
     * @param buttonText chooser button text
     * @param row grid row
     * @param chooserAction chooser action
     * @return chooser button
     */
    private JButton addChooserRow(JPanel panel, GridBagConstraints c, String labelText, JTextField field,
            String buttonText, int row, Runnable chooserAction) {
        grid(panel, label(labelText), c, 0, row, 1, 1);
        grid(panel, field, c, 1, row, 2, 1);
        JButton chooserButton = button(buttonText, false, event -> chooserAction.run());
        chooserButton.setText("");
        chooserButton.setToolTipText(buttonText);
        chooserButton.getAccessibleContext().setAccessibleName(buttonText);
        chooserButton.setPreferredSize(new Dimension(34, 34));
        chooserButton.setMinimumSize(new Dimension(34, 34));
        grid(panel, chooserButton, c, 3, row, 1, 1);
        return chooserButton;
    }

    /**
     * Installs publishing control listeners and checkbox styling.
     */
    private void configurePublishControls() {
        publishTaskCombo.addActionListener(event -> {
            applyDefaultPublishingOutputs();
            updatePublishControlState();
            updatePublishCommand();
        });
        publishSourceCombo.addActionListener(event -> {
            updatePublishControlState();
            updatePublishCommand();
        });
        publishValidateBox.addActionListener(event -> updatePublishCommand());
        publishFlipBox.addActionListener(event -> updatePublishCommand());
        publishNoFenBox.addActionListener(event -> updatePublishCommand());
        onTextChange(this::requestPublishCommandUpdate, publishInputField, publishOutputField, publishPdfOutputField,
                publishCoverOutputField, publishTitleField, publishSubtitleField, publishLimitField,
                publishPagesField);
        styleCheckBox(publishValidateBox);
        styleCheckBox(publishFlipBox);
        styleCheckBox(publishNoFenBox);
    }

    /**
     * Updates publishing controls so irrelevant fields are visually disabled.
     */
    private void updatePublishControlState() {
        PublishTask task = selectedPublishTask();
        boolean diagrams = task == PublishTask.DIAGRAMS;
        boolean render = task == PublishTask.RENDER;
        boolean collection = task == PublishTask.COLLECTION;
        boolean study = task == PublishTask.STUDY;
        boolean cover = task == PublishTask.COVER;
        boolean existingDiagramInput = diagrams && selectedPublishSource() == PublishSource.EXISTING_FILE;

        publishSourceCombo.setEnabled(diagrams);
        publishInputField.setEnabled(!diagrams || existingDiagramInput);
        publishPdfOutputField.setEnabled(collection || cover);
        publishCoverOutputField.setEnabled(collection || study);
        publishSubtitleField.setEnabled(!diagrams);
        publishLimitField.setEnabled(render || collection);
        publishPagesField.setEnabled(collection || study || cover);
        publishValidateBox.setEnabled(!diagrams);
        publishFlipBox.setEnabled(diagrams || study);
        publishNoFenBox.setEnabled(diagrams || study);
        setButtonEnabled(publishInputButton, publishInputField.isEnabled());
        setButtonEnabled(publishOutputButton, publishOutputField.isEnabled());
        setButtonEnabled(publishPdfOutputButton, publishPdfOutputField.isEnabled());
        setButtonEnabled(publishCoverOutputButton, publishCoverOutputField.isEnabled());
    }

    /**
     * Enables a lazily-created button when it is available.
     *
     * @param button button, or null before panel creation
     * @param enabled target enabled state
     */
    private static void setButtonEnabled(JButton button, boolean enabled) {
        if (button != null) {
            button.setEnabled(enabled);
        }
    }

    /**
     * Applies a sensible output default when switching publishing tasks.
     */
    private void applyDefaultPublishingOutputs() {
        String current = trimmed(publishOutputField);
        if (!current.isEmpty() && !isKnownPublishDefault(current)) {
            return;
        }
        PublishTask task = selectedPublishTask();
        switch (task) {
            case DIAGRAMS -> publishOutputField.setText(DEFAULT_DIAGRAMS_OUTPUT);
            case COLLECTION -> {
                publishOutputField.setText(DEFAULT_MANIFEST_OUTPUT);
                setDefaultWhenBlank(publishPdfOutputField, DEFAULT_BOOK_OUTPUT);
                setDefaultWhenBlank(publishCoverOutputField, DEFAULT_COVER_OUTPUT);
            }
            case COVER -> publishOutputField.setText(DEFAULT_COVER_OUTPUT);
            case STUDY -> {
                publishOutputField.setText(DEFAULT_BOOK_OUTPUT);
                setDefaultWhenBlank(publishCoverOutputField, DEFAULT_COVER_OUTPUT);
            }
            case RENDER -> publishOutputField.setText(DEFAULT_BOOK_OUTPUT);
        }
    }

    /**
     * Sets a text field to a default value when it is blank.
     *
     * @param field field to update
     * @param value default value
     */
    private static void setDefaultWhenBlank(JTextField field, String value) {
        if (trimmed(field).isEmpty()) {
            field.setText(value);
        }
    }

    /**
     * Returns whether a path is one of the workbench publish defaults.
     *
     * @param value path text
     * @return true when the text is a known default output
     */
    private static boolean isKnownPublishDefault(String value) {
        return DEFAULT_DIAGRAMS_OUTPUT.equals(value)
                || DEFAULT_BOOK_OUTPUT.equals(value)
                || DEFAULT_COVER_OUTPUT.equals(value)
                || DEFAULT_MANIFEST_OUTPUT.equals(value);
    }

    /**
     * Updates the publishing command preview.
     */
    private void updatePublishCommand() {
        publishCommandUpdateQueued = false;
        try {
            publishCommandField.setText(WorkbenchCommandRunner.displayCommand(buildPublishArgs(false)));
        } catch (IllegalArgumentException | IOException ex) {
            publishCommandField.setText("incomplete: " + ex.getMessage());
        }
    }

    /**
     * Runs the selected publishing command.
     */
    private void runPublishingCommand() {
        try {
            runCommand(buildPublishArgs(true), null);
        } catch (IllegalArgumentException | IOException ex) {
            showError("Publishing command failed", ex.getMessage());
        }
    }

    /**
     * Builds publishing command arguments.
     *
     * @param materialize whether generated workbench inputs should be written to temporary files
     * @return CRTK arguments
     * @throws IOException on temporary input creation failure
     */
    private List<String> buildPublishArgs(boolean materialize) throws IOException {
        List<String> args = new ArrayList<>();
        args.add("book");
        PublishTask task = selectedPublishTask();
        switch (task) {
            case DIAGRAMS -> buildDiagramPublishArgs(args, materialize);
            case RENDER -> buildRenderPublishArgs(args);
            case COLLECTION -> buildCollectionPublishArgs(args);
            case STUDY -> buildStudyPublishArgs(args);
            case COVER -> buildCoverPublishArgs(args);
        }
        return List.copyOf(args);
    }

    /**
     * Builds {@code book pdf} arguments.
     *
     * @param args target arguments
     * @param materialize whether generated inputs should be written
     * @throws IOException on temporary input creation failure
     */
    private void buildDiagramPublishArgs(List<String> args, boolean materialize) throws IOException {
        args.add("pdf");
        PublishSource source = selectedPublishSource();
        switch (source) {
            case CURRENT_FEN -> {
                args.add("--fen");
                args.add(currentFen());
            }
            case GAME_PGN -> {
                args.add("--pgn");
                args.add(materialize ? materializeWorkbenchPgn().toString() : WORKBENCH_GAME_PLACEHOLDER);
            }
            case BATCH_FENS -> {
                args.add("--input");
                args.add(materialize ? materializeWorkbenchFens().toString() : WORKBENCH_FENS_PLACEHOLDER);
            }
            case EXISTING_FILE -> addBookPdfFileInput(args, requiredText(publishInputField, "diagram input path"));
        }
        addRequiredTextArg(args, "--output", publishOutputField, "output path");
        addOptionalTextArg(args, "--title", publishTitleField);
        addToggleArg(args, "--flip", publishFlipBox.isSelected());
        addToggleArg(args, "--no-fen", publishNoFenBox.isSelected());
    }

    /**
     * Builds {@code book render} arguments.
     *
     * @param args target arguments
     */
    private void buildRenderPublishArgs(List<String> args) {
        args.add("render");
        addRequiredTextArg(args, "--input", publishInputField, "manifest input path");
        addOptionalTextArg(args, "--output", publishOutputField);
        addOptionalTextArg(args, "--title", publishTitleField);
        addOptionalTextArg(args, "--subtitle", publishSubtitleField);
        addOptionalPositiveIntegerArg(args, "--limit", publishLimitField);
        addToggleArg(args, "--check", publishValidateBox.isSelected());
    }

    /**
     * Builds {@code book collection} arguments.
     *
     * @param args target arguments
     */
    private void buildCollectionPublishArgs(List<String> args) {
        args.add("collection");
        addRequiredTextArg(args, "--input", publishInputField, "record input path");
        addOptionalTextArg(args, "--output", publishOutputField);
        addOptionalTextArg(args, "--pdf-output", publishPdfOutputField);
        addOptionalTextArg(args, "--cover-output", publishCoverOutputField);
        addOptionalTextArg(args, "--title", publishTitleField);
        addOptionalTextArg(args, "--subtitle", publishSubtitleField);
        addOptionalPositiveIntegerArg(args, "--limit", publishLimitField);
        addOptionalPositiveIntegerArg(args, "--pages", publishPagesField);
        addToggleArg(args, "--check", publishValidateBox.isSelected());
    }

    /**
     * Builds {@code book study} arguments.
     *
     * @param args target arguments
     */
    private void buildStudyPublishArgs(List<String> args) {
        args.add("study");
        addRequiredTextArg(args, "--input", publishInputField, "study manifest input path");
        addOptionalTextArg(args, "--output", publishOutputField);
        addOptionalTextArg(args, "--cover-output", publishCoverOutputField);
        addOptionalTextArg(args, "--title", publishTitleField);
        addOptionalTextArg(args, "--subtitle", publishSubtitleField);
        addOptionalPositiveIntegerArg(args, "--pages", publishPagesField);
        addToggleArg(args, "--flip", publishFlipBox.isSelected());
        addToggleArg(args, "--no-fen", publishNoFenBox.isSelected());
        addToggleArg(args, "--check", publishValidateBox.isSelected());
    }

    /**
     * Builds {@code book cover} arguments.
     *
     * @param args target arguments
     */
    private void buildCoverPublishArgs(List<String> args) {
        args.add("cover");
        addRequiredTextArg(args, "--input", publishInputField, "book manifest input path");
        addOptionalTextArg(args, "--pdf", publishPdfOutputField);
        addOptionalTextArg(args, "--output", publishOutputField);
        addOptionalTextArg(args, "--title", publishTitleField);
        addOptionalTextArg(args, "--subtitle", publishSubtitleField);
        addOptionalPositiveIntegerArg(args, "--pages", publishPagesField);
        addToggleArg(args, "--check", publishValidateBox.isSelected());
    }

    /**
     * Adds the correct diagram-file input flag for {@code book pdf}.
     *
     * @param args target arguments
     * @param input input path
     */
    private static void addBookPdfFileInput(List<String> args, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        args.add(lower.endsWith(".pgn") ? "--pgn" : "--input");
        args.add(input);
    }

    /**
     * Adds a required text option.
     *
     * @param args target arguments
     * @param flag option flag
     * @param field source field
     * @param label human-readable field label
     */
    private static void addRequiredTextArg(List<String> args, String flag, JTextField field, String label) {
        args.add(flag);
        args.add(requiredText(field, label));
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
        if (!value.matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException(flag + " expects a positive integer.");
        }
        args.add(flag);
        args.add(value);
    }

    /**
     * Adds a toggle flag when selected.
     *
     * @param args target arguments
     * @param flag flag
     * @param selected whether the flag is selected
     */
    private static void addToggleArg(List<String> args, String flag, boolean selected) {
        if (selected) {
            args.add(flag);
        }
    }

    /**
     * Returns required trimmed text from a field.
     *
     * @param field source field
     * @param label human-readable field label
     * @return trimmed text
     */
    private static String requiredText(JTextField field, String label) {
        String value = trimmed(field);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Missing " + label + ".");
        }
        return value;
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
     * Writes the current workbench game to a temporary PGN file.
     *
     * @return temporary PGN file
     * @throws IOException on write failure
     */
    private Path materializeWorkbenchPgn() throws IOException {
        if (gameModel.lastPly() <= 0) {
            throw new IllegalArgumentException("Play or import at least one game move before exporting PGN.");
        }
        Path file = Files.createTempFile("crtk-workbench-game-", ".pgn");
        file.toFile().deleteOnExit();
        Files.writeString(file, gameModel.pgn() + System.lineSeparator(), StandardCharsets.UTF_8);
        return file;
    }

    /**
     * Writes batch FENs or the game FEN list to a temporary file.
     *
     * @return temporary FEN file
     * @throws IOException on write failure
     */
    private Path materializeWorkbenchFens() throws IOException {
        String text = batchInput.getText() == null ? "" : batchInput.getText().trim();
        if (text.isEmpty()) {
            text = gameModel.fenList();
        }
        if (text.isBlank()) {
            throw new IllegalArgumentException("Add FENs to the Batch tab first.");
        }
        Path file = Files.createTempFile("crtk-workbench-fens-", ".txt");
        file.toFile().deleteOnExit();
        Files.writeString(file, text + System.lineSeparator(), StandardCharsets.UTF_8);
        return file;
    }

    /**
     * Returns the selected publishing task.
     *
     * @return selected task label
     */
    private PublishTask selectedPublishTask() {
        PublishTask selected = (PublishTask) publishTaskCombo.getSelectedItem();
        return selected == null ? PublishTask.DIAGRAMS : selected;
    }

    /**
     * Returns the selected diagram source.
     *
     * @return selected source
     */
    private PublishSource selectedPublishSource() {
        PublishSource selected = (PublishSource) publishSourceCombo.getSelectedItem();
        return selected == null ? PublishSource.CURRENT_FEN : selected;
    }

    /**
     * Chooses a PDF path using the direction required by the selected task.
     */
    private void choosePublishPdfPath() {
        boolean save = selectedPublishTask() != PublishTask.COVER;
        String title = save ? "Choose PDF output" : "Choose interior PDF";
        choosePath(publishPdfOutputField, save, title, new FileNameExtensionFilter("PDF document", "pdf"));
    }

    /**
     * Opens a file chooser and writes the selected path into a field.
     *
     * @param target target field
     * @param save true for save dialogs
     * @param title chooser title
     */
    private void choosePath(JTextField target, boolean save, String title) {
        choosePath(target, save, title, null);
    }

    /**
     * Opens a file chooser with an optional extension filter.
     *
     * @param target target field
     * @param save true for save dialogs
     * @param title chooser title
     * @param filter optional extension filter
     */
    private void choosePath(JTextField target, boolean save, String title, FileNameExtensionFilter filter) {
        String existing = trimmed(target);
        JFileChooser chooser = createFileChooser(title, existing.isEmpty() ? null : new File(existing), filter);
        int result = save ? chooser.showSaveDialog(this) : chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            if (save && filter != null) {
                selected = ensureExtension(selected, "." + filter.getExtensions()[0]);
            }
            target.setText(selected.getPath());
        }
    }

    /**
     * Creates and styles a file chooser.
     *
     * @param title optional dialog title
     * @param selectedFile optional selected file
     * @param filter optional file filter
     * @return styled chooser
     */
    private static JFileChooser createFileChooser(String title, File selectedFile, FileNameExtensionFilter filter) {
        JFileChooser chooser = new JFileChooser();
        if (title != null && !title.isBlank()) {
            chooser.setDialogTitle(title);
        }
        if (selectedFile != null) {
            chooser.setSelectedFile(selectedFile);
        }
        if (filter != null) {
            chooser.setFileFilter(filter);
        }
        styleFileChooser(chooser);
        return chooser;
    }

    /**
     * Generates a report for the current position and game line.
     */
    private void generateReport() {
        reportPreview.setText(buildReportText());
        reportPreview.setCaretPosition(0);
    }

    /**
     * Copies the current report, generating it first when empty.
     */
    private void copyReport() {
        if (reportPreview.getText() == null || reportPreview.getText().isBlank()) {
            generateReport();
        }
        copyText(reportPreview.getText());
    }

    /**
     * Saves the current report to a text file.
     */
    private void saveReportFile() {
        if (reportPreview.getText() == null || reportPreview.getText().isBlank()) {
            generateReport();
        }
        JFileChooser chooser = createFileChooser(null, new File("workbench-report.txt"),
                new FileNameExtensionFilter("Text report", "txt"));
        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = ensureExtension(chooser.getSelectedFile(), ".txt");
        String contents = reportPreview.getText();
        runAsync(
                () -> {
                    Files.writeString(file.toPath(), contents, StandardCharsets.UTF_8);
                    return file;
                },
                saved -> {
                    appendConsole("Saved report to " + saved + "\n");
                    toast(WorkbenchToast.Kind.SUCCESS, "Saved report to " + saved.getName());
                },
                ex -> showError("Save report failed", ex.getMessage()));
    }

    /**
     * Builds the current report text.
     *
     * @return report text
     */
    private String buildReportText() {
        String newline = System.lineSeparator();
        StringBuilder sb = new StringBuilder(2048);
        sb.append("ChessRTK Workbench Report").append(newline);
        sb.append("========================").append(newline).append(newline);
        if (currentPosition == null) {
            sb.append("No position loaded.").append(newline);
            return sb.toString();
        }
        sb.append("Position").append(newline);
        sb.append("FEN: ").append(currentPosition).append(newline);
        sb.append("Side: ").append(currentPosition.isWhiteToMove() ? "White" : "Black").append(" to move")
                .append(newline);
        sb.append("Status: ").append(formatPositionStatus(currentPosition)).append(newline);
        sb.append("Legal moves: ").append(visibleMoves.length).append(newline);
        sb.append("Tags: ").append(formatCurrentTags()).append(newline).append(newline);

        appendLegalMoves(sb);
        appendGameReport(sb);
        String note = trimmed(reportNoteField);
        if (!note.isEmpty()) {
            sb.append("Note").append(newline);
            sb.append(note).append(newline);
        }
        return sb.toString();
    }

    /**
     * Appends legal move details to a report.
     *
     * @param sb target builder
     */
    private void appendLegalMoves(StringBuilder sb) {
        String newline = System.lineSeparator();
        sb.append("Legal Move Table").append(newline);
        MoveList moves = currentPosition.legalMoves();
        for (int i = 0; i < moves.size(); i++) {
            short move = moves.raw(i);
            sb.append(String.format(Locale.ROOT, "%2d. %-8s %s", i + 1, safeSan(currentPosition, move),
                    Move.toString(move))).append(newline);
        }
        sb.append(newline);
    }

    /**
     * Appends game-line details to a report.
     *
     * @param sb target builder
     */
    private void appendGameReport(StringBuilder sb) {
        String newline = System.lineSeparator();
        sb.append("Game Line").append(newline);
        sb.append("Current ply: ").append(gameModel.currentPly()).append(" / ").append(gameModel.lastPly())
                .append(newline);
        if (gameModel.lastPly() <= 0) {
            sb.append("(no moves)").append(newline).append(newline);
            return;
        }
        sb.append("SAN: ").append(gameModel.sanLine()).append(newline);
        sb.append("UCI: ").append(gameModel.uciLine()).append(newline);
        sb.append("PGN:").append(newline).append(gameModel.pgn()).append(newline);
        sb.append("FEN list:").append(newline).append(gameModel.fenList()).append(newline).append(newline);
    }

    /**
     * Formats the current tag list for reports.
     *
     * @return formatted tags
     */
    private String formatCurrentTags() {
        List<String> tags = new ArrayList<>();
        for (int i = 0; i < tagModel.size(); i++) {
            String tag = tagModel.get(i);
            if (tag != null && !tag.isBlank() && !"calculating...".equals(tag)
                    && !tag.startsWith("tagging failed:")) {
                tags.add(tag);
            }
        }
        return tags.isEmpty() ? "(none)" : String.join(", ", tags);
    }

    /**
     * Formats the tactical status of a position.
     *
     * @param position position
     * @return status text
     */
    private static String formatPositionStatus(Position position) {
        if (position.isCheckmate()) {
            return "checkmate";
        }
        if (position.isStalemate()) {
            return "stalemate";
        }
        return position.inCheck() ? "check" : "normal";
    }

    /**
     * Formats SAN while keeping reports resilient.
     *
     * @param position position before the move
     * @param move encoded move
     * @return SAN or UCI fallback
     */
    private static String safeSan(Position position, short move) {
        try {
            return SAN.toAlgebraic(position, move);
        } catch (IllegalArgumentException ex) {
            return Move.toString(move);
        }
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
        top.add(commandStateLabel, BorderLayout.CENTER);
        top.add(buttonRow(FlowLayout.RIGHT,
                button("Clear", false, event -> console.setText("")),
                button("Stop", false, event -> stopCommand())), BorderLayout.EAST);
        panel.add(top, BorderLayout.NORTH);
        WorkbenchTheme.terminal(console);
        console.setEditable(false);
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
        updateMoves();
        updateStatus();
        updateTagsAsync();
        requestCommandPreviews();
        updateGameState();
        requestEvalUpdate();
        refreshStatusBar();
    }

    /**
     * Schedules an eval-bar refresh; coalesces rapid navigation into a single
     * subprocess fork after a short debounce.
     */
    private void requestEvalUpdate() {
        if (!autoEvalBarEnabled) {
            evalDebounceTimer.stop();
            cancelEvalCommand();
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
        EngineEval eval = parseEngineEval(output);
        if (eval == null) {
            evalBar.setUnavailable("n/a");
            return;
        }
        int value = whiteToMove ? eval.value() : -eval.value();
        if (eval.mate()) {
            evalBar.setMate(value);
        } else {
            evalBar.setCentipawns(value);
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
                + " to move  |  " + formatPositionStatus(currentPosition)
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
                    for (String tag : get()) {
                        tagModel.addElement(tag);
                    }
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
            showError("No command", "Select a workflow first.");
            return;
        }
        if (runningCommand != null && runningCommand.isRunning()) {
            showError("Command running", "Stop the current command before starting another one.");
            return;
        }
        appendConsole("\n$ " + WorkbenchCommandRunner.displayCommand(args) + "\n");
        setCommandState("Running");
        runningCommand = WorkbenchCommandRunner.run(args, stdin, this::appendConsole, result -> {
            appendConsole("[exit " + result.exitCode() + ", " + result.millis() + " ms]\n");
            setCommandState("Exit " + result.exitCode());
            maybeHighlightMove(args, result.output());
        }, ex -> {
            setCommandState("Stopped");
            appendConsole("[stopped] " + Objects.toString(ex.getMessage(), ex.getClass().getSimpleName()) + "\n");
        });
    }

    /**
     * Stops a running command.
     */
    private void stopCommand() {
        if (runningCommand != null && runningCommand.isRunning()) {
            runningCommand.cancel();
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
                FenInputSummary validation = validateBatchFenInput(batchInput.getText());
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
        FenScan fenScan = firstFenOrFailure(text);
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
        JFileChooser chooser = createFileChooser(null, null,
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
        JFileChooser chooser = createFileChooser(null, new File("workbench-game.pgn"),
                new FileNameExtensionFilter("PGN file", "pgn"));
        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = ensureExtension(chooser.getSelectedFile(), ".pgn");
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
     * Result of FEN scanning: a successful FEN, or the most recent rejection
     * message from a non-bracketed non-empty line so callers can surface a
     * meaningful diagnostic instead of silently dropping the input.
     */
    private record FenScan(String fen, String firstError) { }

    /**
     * Batch FEN validation summary.
     */
    private record FenInputSummary(int rows, int validRows, int firstErrorLine, String firstError) {

        /**
         * Returns whether at least one row failed to parse.
         *
         * @return true when a parse error was found
         */
        boolean hasError() {
            return firstError != null;
        }
    }

    /**
     * Returns the first valid FEN line from pasted text, or {@code null} when
     * no candidate line parses. Retained for the reflective regression test.
     *
     * @param text raw text
     * @return FEN or null
     */
    private static String firstFenLine(String text) {
        return firstFenOrFailure(text).fen();
    }

    /**
     * Scans pasted text for a FEN line and remembers the first parse error.
     *
     * @param text raw text
     * @return scan result
     */
    private static FenScan firstFenOrFailure(String text) {
        String firstError = null;
        for (String line : text.split("\\R")) {
            String candidate = line.trim();
            if (candidate.isEmpty() || candidate.startsWith("[")) {
                continue;
            }
            try {
                new Position(candidate);
                return new FenScan(candidate, null);
            } catch (IllegalArgumentException ex) {
                if (firstError == null) {
                    firstError = fenErrorMessage(candidate, ex);
                }
            }
        }
        return new FenScan(null, firstError);
    }

    /**
     * Validates every non-empty batch FEN row.
     *
     * @param text raw input
     * @return validation summary
     */
    private static FenInputSummary validateBatchFenInput(String text) {
        int rows = 0;
        int validRows = 0;
        int firstErrorLine = 0;
        String firstError = null;
        String[] lines = text == null ? new String[0] : text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String candidate = lines[i].trim();
            if (candidate.isEmpty() || candidate.startsWith("[")) {
                continue;
            }
            rows++;
            try {
                new Position(candidate);
                validRows++;
            } catch (IllegalArgumentException ex) {
                if (firstError == null) {
                    firstErrorLine = i + 1;
                    firstError = fenErrorMessage(candidate, ex);
                }
            }
        }
        return new FenInputSummary(rows, validRows, firstErrorLine, firstError);
    }

    /**
     * Returns a stable FEN parse error message.
     *
     * @param candidate parsed row
     * @param ex parser exception
     * @return human-readable error
     */
    private static String fenErrorMessage(String candidate, IllegalArgumentException ex) {
        return ex.getMessage() == null ? "Could not parse FEN: " + candidate : ex.getMessage();
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
     * Ensures a selected file has an extension.
     *
     * @param file selected file
     * @param extension extension including dot
     * @return file with extension
     */
    private static File ensureExtension(File file, String extension) {
        String path = file.getAbsolutePath();
        return path.toLowerCase(Locale.ROOT).endsWith(extension) ? file : new File(path + extension);
    }

    /**
     * Appends console text on the EDT.
     *
     * @param text text
     */
    private void appendConsole(String text) {
        if (SwingUtilities.isEventDispatchThread()) {
            console.append(text);
            console.setCaretPosition(console.getDocument().getLength());
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
        templateCombo.setModel(WorkbenchCommandTemplates.commandModel());
    }

    /**
     * Installs batch tasks.
     */
    private void installBatchTasks() {
        batchTaskCombo.setModel(WorkbenchCommandTemplates.batchModel());
    }

    /**
     * Configures the command option table.
     */
    private void configureOptionTable() {
        WorkbenchTheme.table(optionTable, 27);
        optionTable.setRowSorter(optionSorter);
        optionSorter.setSortsOnUpdates(true);
        TableColumnModel columns = optionTable.getColumnModel();
        columns.getColumn(0).setPreferredWidth(52);
        columns.getColumn(1).setPreferredWidth(150);
        columns.getColumn(2).setPreferredWidth(260);
        if (optionDescriptionColumn == null && columns.getColumnCount() > 3) {
            optionDescriptionColumn = columns.getColumn(3);
            optionDescriptionColumn.setPreferredWidth(420);
        }
        setCommandInfoVisible(commandInfoVisible);
        updateOptionTableViewportSize();
    }

    /**
     * Toggles the command option description column.
     */
    private void toggleCommandInfo() {
        setCommandInfoVisible(!commandInfoVisible);
    }

    /**
     * Shows or hides the command option description column.
     *
     * @param visible true when descriptions should be shown
     */
    private void setCommandInfoVisible(boolean visible) {
        commandInfoVisible = visible;
        optionInfoButton.setText(visible ? "Info -" : "Info +");
        optionInfoButton.setToolTipText(visible ? "Hide option descriptions" : "Show option descriptions");
        WorkbenchTheme.button(optionInfoButton, visible);
        if (optionDescriptionColumn == null) {
            return;
        }
        TableColumnModel columns = optionTable.getColumnModel();
        boolean present = containsColumn(columns, optionDescriptionColumn);
        if (visible && !present) {
            columns.addColumn(optionDescriptionColumn);
            int current = columns.getColumnCount() - 1;
            int target = Math.min(3, current);
            if (current != target) {
                columns.moveColumn(current, target);
            }
        } else if (!visible && present) {
            columns.removeColumn(optionDescriptionColumn);
        }
        updateOptionTableViewportSize();
    }

    /**
     * Updates command option table's preferred viewport width for the info state.
     */
    private void updateOptionTableViewportSize() {
        optionTable.setPreferredScrollableViewportSize(new Dimension(commandInfoVisible ? 860 : 560, 280));
        optionTable.revalidate();
    }

    /**
     * Returns whether a table column is currently visible.
     *
     * @param columns column model
     * @param column target column
     * @return true when the column is present
     */
    private static boolean containsColumn(TableColumnModel columns, TableColumn column) {
        for (int i = 0; i < columns.getColumnCount(); i++) {
            if (columns.getColumn(i) == column) {
                return true;
            }
        }
        return false;
    }

    /**
     * Queues command-option filtering after the current event finishes.
     */
    private void requestOptionFilterUpdate() {
        if (optionFilterUpdateQueued) {
            return;
        }
        optionFilterUpdateQueued = true;
        SwingUtilities.invokeLater(() -> {
            if (!optionFilterUpdateQueued) {
                return;
            }
            updateOptionFilter();
        });
    }

    /**
     * Applies the current command option filter.
     */
    private void updateOptionFilter() {
        optionFilterUpdateQueued = false;
        String query = optionFilterField.getText();
        if (query == null || query.isBlank()) {
            optionSorter.setRowFilter(null);
            return;
        }
        optionSorter.setRowFilter(new RowFilter<>() {
            /**
             * Returns whether a command option should remain visible.
             *
             * @param entry row-filter entry
             * @return true when all query tokens match the row text
             */
            @Override
            public boolean include(Entry<? extends WorkbenchOptionTableModel, ? extends Integer> entry) {
                return optionFilterMatches(query, entry.getStringValue(1), entry.getStringValue(2),
                        entry.getStringValue(3));
            }
        });
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
        CommandTemplate template = (CommandTemplate) templateCombo.getSelectedItem();
        if (template == null) {
            return;
        }
        optionModel.setOptions(template.options(), templateContext());
    }

    /**
     * Resets the selected command to its default flags and values.
     */
    private void resetSelectedTemplate() {
        optionModel.resetDefaults(templateContext());
        updateBuiltCommand();
    }

    /**
     * Returns the selected command to its default enabled option set.
     */
    private void clearOptionalTemplateOptions() {
        optionModel.clearOptional();
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
        if (task != null && !task.usesFenInput()) {
            batchInputStatus.setText("FEN list not used");
            batchInputStatus.setToolTipText("The selected batch task runs without FEN input.");
            batchInputStatus.setForeground(WorkbenchTheme.MUTED);
            return;
        }
        FenInputSummary scan = validateBatchFenInput(batchInput.getText());
        if (scan.rows() == 0) {
            batchInputStatus.setText("No FEN rows");
            batchInputStatus.setToolTipText("Add one FEN per line.");
            batchInputStatus.setForeground(WorkbenchTheme.MUTED);
        } else if (scan.hasError()) {
            batchInputStatus.setText(scan.rows() + " row" + (scan.rows() == 1 ? "" : "s")
                    + ", issue on line " + scan.firstErrorLine());
            batchInputStatus.setToolTipText(scan.firstError());
            batchInputStatus.setForeground(WorkbenchTheme.STATUS_WARNING_TEXT);
        } else {
            batchInputStatus.setText(scan.validRows() + " FEN row" + (scan.validRows() == 1 ? "" : "s"));
            batchInputStatus.setToolTipText("Ready to run batch workflow.");
            batchInputStatus.setForeground(WorkbenchTheme.MUTED);
        }
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
        optionModel.refreshDynamicValues(templateContext());
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
        CommandTemplate template = (CommandTemplate) templateCombo.getSelectedItem();
        if (template == null) {
            return List.of();
        }
        List<String> args = new ArrayList<>(template.baseArgs());
        args.addAll(optionModel.enabledArgs());
        return List.copyOf(args);
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
    }

    /**
     * Queues a publishing preview refresh after document edits settle.
     */
    private void requestPublishCommandUpdate() {
        if (publishCommandUpdateQueued) {
            return;
        }
        publishCommandUpdateQueued = true;
        SwingUtilities.invokeLater(() -> {
            if (!publishCommandUpdateQueued) {
                return;
            }
            updatePublishCommand();
        });
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
    static EngineEval parseEngineEval(String output) {
        if (output == null) {
            return null;
        }
        Matcher matcher = EVAL_PATTERN.matcher(output);
        if (!matcher.find()) {
            return null;
        }
        String value = matcher.group(1);
        try {
            if (value.startsWith("#")) {
                return new EngineEval(true, Integer.parseInt(value.substring(1)));
            }
            return new EngineEval(false, Integer.parseInt(value));
        } catch (NumberFormatException ex) {
            return null;
        }
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
     * Shows an error.
     *
     * @param title title
     * @param message message
     */
    private void showError(String title, String message) {
        showErrorDialog(this, title, message);
    }

    /**
     * Parsed engine evaluation from command output.
     *
     * @param mate true when the value is a mate distance
     * @param value signed centipawn or mate value from the side-to-move perspective
     */
    record EngineEval(boolean mate, int value) {
    }
}
