/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package application.gui.workbench.window;

import application.Config;
import application.gui.workbench.board.BoardEditorPanel;
import application.gui.workbench.command.CommandPalette.PaletteAction;
import application.gui.workbench.command.CommandTemplates.CommandTemplate;
import application.gui.workbench.command.CommandTemplates.TemplateContext;
import application.gui.workbench.game.EcoExplorerPanel;
import application.gui.workbench.game.SanRenderer;
import application.gui.workbench.layout.EditorSplitArea;
import application.gui.workbench.layout.SplitPaneStyler;
import application.gui.workbench.ui.FileDialogs;
import application.gui.workbench.ui.SurfacePanel;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Toast;
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

public abstract class WindowBoardLayer extends WindowLifecycle {
    /** Serialization identifier for Swing frame compatibility. */
    private static final long serialVersionUID = 1L;

    /**
     * ECO explorer panel, created lazily with the board detail tabs.
     */
    protected EcoExplorerPanel ecoExplorerPanel;

    /**
     * Creates the board tab.
     *
     * @return tab component
     */
    protected JComponent createBoardTab() {
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
        SplitPaneStyler.style(boardPage);

        analysisTabs = createSectionTabs();
        analysisTabs.addTab("Board", boardPage);
        analysisTabs.addTab("Game", createGameSection());
        return analysisTabs;
    }

    /**
     * Creates an independent analysis workspace for duplicate Analyze tabs.
     *
     * @return workspace component
     */
    protected JComponent createDetachedAnalysisTab() {
        return new AnalysisWorkspacePanel(currentFen(), board.isWhiteDown(), this::buildAnalyzeArgs,
                args -> runCommand(args, null), this::copyText);
    }

    /**
     * Creates the compact board-side header.
     *
     * @return side header
     */
    protected JComponent createBoardSideHeader() {
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
    protected JComponent createPositionControls() {
        JPanel panel = new SurfacePanel(new GridBagLayout());
        GridBagConstraints c = constraints();
        grid(panel, Theme.section("Position"), c, 0, 0, 4, 1);

        styleFields(fenField);
        fenField.addActionListener(event -> setPositionFromField());
        grid(panel, fenField, c, 0, 1, 4, 1);

        boardStartButton = iconButton("Start", event -> jumpGameTo(0));
        boardBackButton = iconButton("Back", event -> navigateGame(-1));
        boardForwardButton = iconButton("Forward", event -> navigateGame(1));
        boardEndButton = iconButton("End", event -> jumpGameTo(gameModel.lastPly()));
        setTransportShortcut(boardStartButton, "Up / Home / Numpad 8 / Alt+Up");
        setTransportShortcut(boardBackButton, "Left / Numpad 4 / Alt+Left");
        setTransportShortcut(boardForwardButton, "Right / Numpad 6 / Alt+Right");
        setTransportShortcut(boardEndButton, "Down / End / Numpad 2 / Alt+Down");
        updateBoardNavigationControls();
        grid(panel, buttonRow(FlowLayout.LEFT,
                button("Load", true, event -> setPositionFromField()),
                boardStartButton,
                boardBackButton,
                boardForwardButton,
                boardEndButton,
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
    protected JComponent createAnalysisControls() {
        JPanel panel = new SurfacePanel(new GridBagLayout());
        GridBagConstraints c = constraints();
        grid(panel, Theme.section("Analysis"), c, 0, 0, 4, 1);
        Theme.foreground(statusLabel, Theme.ForegroundRole.MUTED);
        statusLabel.setFont(Theme.font(12, Font.PLAIN));
        grid(panel, statusLabel, c, 0, 1, 4, 1);

        styleSpinners(analysisDepthSpinner, analysisMultipvSpinner, analysisThreadsSpinner);
        styleFields(analysisDurationField);
        grid(panel, label("Depth"), c, 0, 2, 1, 1);
        grid(panel, analysisDepthSpinner, c, 1, 2, 1, 1);
        grid(panel, label("Time"), c, 2, 2, 1, 1);
        analysisDurationField.getDocument()
                .addDocumentListener(changeListener(this::syncDurationFromAnalysis));
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
    protected JComponent createAdvancedAnalysisControls() {
        JPanel panel = transparentPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        panel.add(optionGroup("Lines", analysisMultipvSpinner));
        panel.add(optionGroup("Threads", analysisThreadsSpinner));
        panel.add(button("Engine", false, event -> showEngineSettings()));
        panel.add(button("Tags", false, event -> runTagsCommand()));
        panel.add(button("Perft", false, event -> runPerft()));
        return panel;
    }

    /**
     * Synchronizes board-side transport controls with the current game ply.
     */
    protected void updateBoardNavigationControls() {
        boolean canBack = gameModel.canBack();
        boolean canForward = gameModel.canForward();
        setNavigationButtonEnabled(boardStartButton, canBack);
        setNavigationButtonEnabled(boardBackButton, canBack);
        setNavigationButtonEnabled(boardForwardButton, canForward);
        setNavigationButtonEnabled(boardEndButton, canForward);
    }

    /**
     * Applies an enabled state to one optional navigation button.
     *
     * @param button button, possibly null before the board page is built
     * @param enabled true to enable the button
     */
    private static void setNavigationButtonEnabled(JButton button, boolean enabled) {
        if (button != null) {
            button.setEnabled(enabled);
        }
    }

    /**
     * Adds keyboard-shortcut copy to one board transport button.
     *
     * @param button transport button
     * @param shortcut shortcut text
     */
    protected static void setTransportShortcut(JButton button, String shortcut) {
        if (button == null || shortcut == null || shortcut.isBlank()) {
            return;
        }
        String label = button.getAccessibleContext().getAccessibleName();
        if (label == null || label.isBlank()) {
            label = button.getToolTipText();
        }
        if (label == null || label.isBlank()) {
            return;
        }
        button.setToolTipText(label + " (" + shortcut + ")");
    }

    /**
     * Creates moves and tags lists.
     *
     * @return component
     */
    protected JComponent createMovesAndTags() {
        movesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        Theme.table(movesTable, 27);
        // Render the SAN column with inline chess-piece SVGs. Pin the
        // columns so a model data refresh does not drop the custom renderer.
        movesTable.setAutoCreateColumnsFromModel(false);
        movesTable.getColumnModel().getColumn(1).setCellRenderer(new SanRenderer());
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

        Theme.list(tagList);
        tagList.setFont(Theme.mono(12));

        boardDetailTabs = createSectionTabs();
        boardDetailTabs.addTab("Moves", titled("Legal Moves", scroll(movesTable)));
        boardDetailTabs.addTab("Tags", titled("Tags", scroll(tagList)));
        boardDetailTabs.addTab("ECO", createEcoExplorerPanel());
        boardDetailTabs.addTab("Editor", createBoardEditorPanel());
        boardDetailTabs.addTab("Data", createAnalysisDataPanel());
        boardDetailTabs.addTab("MCTS", mctsPanel);
        boardDetailTabs.addTab("Settings", createDisplaySettingsPanel());
        boardDetailTabs.addTab("Engine", createEngineSettingsPanel());
        return boardDetailTabs;
    }

    /**
     * Creates the ECO opening explorer.
     *
     * @return explorer component
     */
    protected JComponent createEcoExplorerPanel() {
        ecoExplorerPanel = new EcoExplorerPanel(
                () -> gameModel.startPosition(),
                () -> gameModel.currentPath(),
                this::loadEcoLine,
                this::copyText);
        return scroll(fillViewport(ecoExplorerPanel));
    }

    /**
     * Refreshes the ECO explorer when the visible game position changes.
     */
    protected void refreshEcoExplorer() {
        if (ecoExplorerPanel != null) {
            ecoExplorerPanel.refresh();
        }
    }

    /**
     * Loads one ECO movetext line from the standard starting position.
     *
     * @param movetext ECO movetext
     */
    protected abstract void loadEcoLine(String movetext);

    /**
     * Creates the board setup editor.
     *
     * @return editor component
     */
    protected JComponent createBoardEditorPanel() {
        BoardEditorPanel editor = new BoardEditorPanel(this::currentFen, this::applyBoardEditorFen, this::copyText);
        return scroll(fillViewport(editor));
    }

    /**
     * Applies a validated setup from the board editor.
     *
     * @param fen validated FEN
     */
    protected void applyBoardEditorFen(String fen) {
        startNewGame(fen);
        appendConsole("Board editor applied " + fen + "\n");
    }

    /**
     * Creates analysis graph and report controls.
     *
     * @return data panel
     */
    protected JComponent createAnalysisDataPanel() {
        JPanel content = transparentPanel(new BorderLayout(6, 6));
        JComponent actions = buttonRow(FlowLayout.LEFT,
                button("Copy CSV", false, event -> copyAnalysisCsv()),
                button("Copy Report", false, event -> copyAnalysisReport()),
                button("Print", false, event -> printAnalysisReport()),
                button("Clear", false, event -> clearAnalysisData()));
        content.add(collapsible("Actions", actions, false), BorderLayout.NORTH);
        content.add(analysisGraph, BorderLayout.CENTER);
        return titled("Analysis", content);
    }

    /**
     * Creates the in-workbench display settings panel.
     *
     * @return settings panel
     */
    protected JComponent createDisplaySettingsPanel() {
        JPanel panel = new SurfacePanel(new GridBagLayout());
        GridBagConstraints c = constraints();
        c.insets = new Insets(6, 6, 6, 6);

        JPanel appearance = settingsGroupPanel();
        GridBagConstraints appearanceC = constraints();
        appearanceC.insets = new Insets(3, 0, 3, 0);
        grid(appearance, settingsToggle("Dark mode",
                "Switch the workbench chrome and controls to the dark palette",
                isDarkMode(), this::setDarkMode), appearanceC, 0, 0, 1, 1);

        JPanel boardSettings = settingsGroupPanel();
        GridBagConstraints boardC = constraints();
        boardC.insets = new Insets(3, 0, 3, 0);
        addSettingsToggle(boardSettings, boardC, 0, "Legal move preview",
                "Show selected-piece destinations and legal drag targets on the board",
                showLegalMovePreview, selected -> showLegalMovePreview = selected, false);
        addSettingsToggle(boardSettings, boardC, 1, "Last move highlight",
                "Show the previous move on the board",
                showLastMoveHighlight, selected -> showLastMoveHighlight = selected, false);
        addSettingsToggle(boardSettings, boardC, 2, "Best move arrows",
                "Show engine best-move and analysis suggestions as board arrows",
                showBestMoveArrows, selected -> showBestMoveArrows = selected, false);
        addSettingsToggle(boardSettings, boardC, 3, "Coordinates",
                "Show file and rank notation on the board",
                showBoardCoordinates, selected -> showBoardCoordinates = selected, false);
        addSettingsToggle(boardSettings, boardC, 4, "Board animations",
                "Animate moves, snaps, snapbacks, and board flips",
                boardAnimationsEnabled, selected -> boardAnimationsEnabled = selected, false);

        JPanel analysisSettings = settingsGroupPanel();
        GridBagConstraints analysisC = constraints();
        analysisC.insets = new Insets(3, 0, 3, 0);
        addSettingsToggle(analysisSettings, analysisC, 0, "Auto eval bar",
                "Automatically refresh the side evaluation bar after position changes",
                autoEvalBarEnabled, selected -> autoEvalBarEnabled = selected, true);

        int row = 0;
        grid(panel, collapsible("Appearance", appearance, true), c, 0, row++, 1, 1);
        grid(panel, collapsible("Board", boardSettings, true), c, 0, row++, 1, 1);
        grid(panel, collapsible("Analysis", analysisSettings, false), c, 0, row++, 1, 1);
        addVerticalFiller(panel, c, row, 1);
        return panel;
    }

    /**
     * Creates an unframed single-column settings group.
     *
     * @return group panel
     */
    private static JPanel settingsGroupPanel() {
        return transparentPanel(new GridBagLayout());
    }

    /**
     * Creates the external-engine configuration panel.
     *
     * @return engine settings panel
     */
    protected JComponent createEngineSettingsPanel() {
        JPanel panel = new SurfacePanel(new GridBagLayout());
        GridBagConstraints c = constraints();
        c.insets = new Insets(6, 6, 6, 6);

        engineProtocolField.setColumns(28);
        engineNodesField.setColumns(10);
        engineHashField.setColumns(7);
        styleFields(engineProtocolField, engineNodesField, engineHashField);

        JPanel protocol = settingsGroupPanel();
        GridBagConstraints protocolC = constraints();
        protocolC.insets = new Insets(3, 0, 3, 0);
        grid(protocol, label("protocol"), protocolC, 0, 0, 1, 1);
        grid(protocol, engineProtocolField, protocolC, 1, 0, 2, 1);
        JButton chooseProtocol = button("Choose Protocol", false,
                event -> FileDialogs.choosePath(this, engineProtocolField, false, "Choose engine protocol",
    new FileNameExtensionFilter("TOML files", "toml")));
        grid(protocol, chooseProtocol, protocolC, 3, 0, 1, 1);

        JPanel limits = settingsGroupPanel();
        GridBagConstraints limitsC = constraints();
        limitsC.insets = new Insets(3, 0, 3, 0);
        JPanel limitRow = flow(FlowLayout.LEFT);
        limitRow.add(label("nodes"));
        limitRow.add(engineNodesField);
        limitRow.add(label("hash"));
        limitRow.add(engineHashField);
        grid(limits, limitRow, limitsC, 0, 0, 1, 1);

        grid(limits, buttonRow(FlowLayout.LEFT,
                button("Smoke", false, event -> runEngineSmoke()),
                button("Validate Config", false, event -> runConfigValidate()),
                button("Defaults", false, event -> resetEngineSettings())), limitsC, 0, 1, 1, 1);

        int row = 0;
        grid(panel, collapsible("Protocol", protocol, true), c, 0, row++, 4, 1);
        grid(panel, collapsible("Limits", limits, false), c, 0, row++, 4, 1);
        addVerticalFiller(panel, c, row, 4);
        return panel;
    }

    /**
     * Creates the merged game-line section inside the analysis tab.
     *
     * @return game section
     */
    protected JComponent createGameSection() {
        JComponent tools = scroll(fillViewport(createGameToolsPanel()));
        tools.setPreferredSize(new Dimension(390, 520));

        JSplitPane gamePage = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, createGameHistoryPanel(), tools);
        gamePage.setBorder(BorderFactory.createEmptyBorder());
        gamePage.setOpaque(false);
        gamePage.setContinuousLayout(true);
        gamePage.setResizeWeight(0.68);
        gamePage.setDividerSize(8);
        gamePage.setDividerLocation(0.68);
        SplitPaneStyler.style(gamePage);
        return gamePage;
    }

    /**
     * Creates the move-history panel.
     *
     * @return history panel
     */
    protected JComponent createGameHistoryPanel() {
        configureGameTable();
        JPanel panel = new SurfacePanel(new BorderLayout(8, 8));
        JPanel top = transparentPanel(new BorderLayout(8, 0));
        top.add(Theme.section("Game Line"), BorderLayout.WEST);
        Theme.foreground(gameStateLabel, Theme.ForegroundRole.MUTED);
        gameStateLabel.setFont(Theme.font(12, Font.PLAIN));
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
    protected JComponent createGameToolsPanel() {
        JPanel panel = new SurfacePanel(new GridBagLayout());
        GridBagConstraints c = constraints();
        grid(panel, Theme.section("Line"), c, 0, 0, 4, 1);

        JPanel importTools = settingsGroupPanel();
        GridBagConstraints importC = constraints();
        importC.insets = new Insets(3, 0, 3, 0);
        JScrollPane gameInputScroll = configureGameInputScroll(gameInput);
        gameInput.setText("1. e4 e5 2. Nf3 Nc6");
        grid(importTools, label("input"), importC, 0, 0, 1, 1);
        grid(importTools, gameInputScroll, importC, 1, 0, 3, 1);

        grid(importTools, buttonRow(FlowLayout.LEFT,
                button("Load Line", true, event -> loadGameText(gameInput.getText())),
                button("Load File", false, event -> loadGameFile()),
                button("Save PGN", false, event -> savePgnFile()),
                button("New Game", false, event -> startNewGame(currentFen()))), importC, 1, 1, 3, 1);

        JComponent exportTools = buttonRow(FlowLayout.LEFT,
                button("Copy FENs", false, event -> copyText(gameModel.fenList())),
                button("Add to Batch", false, event -> batchPanel.appendCurrentFen()));

        grid(panel, collapsible("Import", importTools, true), c, 0, 1, 4, 1);
        grid(panel, collapsible("Exports", exportTools, false), c, 0, 2, 4, 1);
        addVerticalFiller(panel, c, 3, 4);
        return panel;
    }

    /**
     * Styles the game-line import field as a compact multiline editor.
     *
     * @param input game-line input area
     * @return scroll pane with a stable usable height
     */
    private static JScrollPane configureGameInputScroll(JTextArea input) {
        styleAreas(input);
        input.setRows(5);
        input.setLineWrap(true);
        input.setWrapStyleWord(true);
        JScrollPane inputScroll = scroll(input);
        inputScroll.setPreferredSize(new Dimension(360, 96));
        inputScroll.setMinimumSize(new Dimension(260, 76));
        return inputScroll;
    }

    /**
     * Creates the command tab.
     *
     * @return tab
     */
    protected JComponent createCommandTab() {
        installTemplates();
    return scroll(fillViewport(createCommandBuilder()));
    }

    /**
     * Creates command builder.
     *
     * @return panel
     */
    protected JPanel createCommandBuilder() {
        JPanel panel = new SurfacePanel(new BorderLayout(0, 8));

        commandForm.setChangeListener(this::updateBuiltCommand);
        commandForm.setRunGate(this::updateCommandRunGate);
        styleFields(commandField);
        depthModel.addChangeListener(event -> requestCommandPreviews());
        multipvModel.addChangeListener(event -> requestCommandPreviews());
        threadsModel.addChangeListener(event -> requestCommandPreviews());

        // Header: the wrapping command-selector bar replaces a JTabbedPane
        // whose empty content pane left a blank gap.
        JPanel header = transparentPanel(new BorderLayout(0, 6));
        header.add(commandPicker, BorderLayout.CENTER);
        panel.add(header, BorderLayout.NORTH);

        panel.add(commandForm, BorderLayout.CENTER);

        JPanel south = transparentPanel(new BorderLayout(0, 8));
        commandField.setEditable(false);
        JPanel previewRow = transparentPanel(new BorderLayout(8, 0));
        commandField.setToolTipText("Generated command");
        previewRow.add(commandField, BorderLayout.CENTER);
        south.add(previewRow, BorderLayout.NORTH);
        runCommandButton = button("Run", true, event -> runSelectedTemplate());
        south.add(buttonRow(FlowLayout.LEFT,
                runCommandButton,
                button("Copy", false, event -> copyBuiltCommand()),
                button("Reset", false, event -> resetSelectedTemplate()),
                button("Clear Flags", false, event -> clearOptionalTemplateOptions()),
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
    protected void updateCommandRunGate(boolean ready) {
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
    protected JComponent createBatchTab() {
        return batchPanel.component();
    }

    /**
     * Creates the publishing tab.
     *
     * @return publish tab
     */
    protected JComponent createPublishTab() {
        return publishingPanel.component();
    }

    /**
     * Creates a compact nested tabbed pane for replacing divider-heavy sections.
     *
     * @return styled tabbed pane
     */
    protected static JTabbedPane createSectionTabs() {
        JTabbedPane pane = tabbedPane();
        pane.setBorder(BorderFactory.createEmptyBorder());
        return pane;
    }

    /**
     * Generates a report for the current position and game line.
     */
    protected void generateReport() {
        reportPanel.generateReport();
    }

    /**
     * Copies the current report, generating it first when empty.
     */
    protected void copyReport() {
        reportPanel.copyReport();
    }

    /**
     * Saves the current report to a text file.
     */
    protected void saveReportFile() {
        reportPanel.saveReportFile();
    }

    /**
     * Saves the visible console text to a user-chosen log file.
     */
    protected void saveConsoleLog() {
        JFileChooser chooser = FileDialogs.createFileChooser(null, new File("workbench-console.log"),
    new FileNameExtensionFilter("Log files", "log", "txt"));
        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = FileDialogs.ensureExtension(chooser.getSelectedFile(), ".log");
        String contents = console.getText();
        runAsync(
                () -> {
                    Files.writeString(file.toPath(), contents, StandardCharsets.UTF_8);
                    return file.toPath().toAbsolutePath().normalize();
                },
                saved -> {
                    session.artifacts().add(saved);
                    appendConsole("Saved console log to " + saved + "\n");
                    toast(Toast.Kind.SUCCESS, "Saved log to " + saved.getFileName());
                },
                ex -> showError("Save log failed", ex.getMessage()));
    }

    /**
     * Creates console panel.
     *
     * @return panel
     */
    protected JComponent createConsolePanel() {
        JPanel panel = new SurfacePanel(new BorderLayout(6, 6));
        JPanel top = transparentPanel(new BorderLayout());
        top.add(Theme.section("Console"), BorderLayout.WEST);
        Theme.foreground(commandStateLabel, Theme.ForegroundRole.MUTED);
        commandStateLabel.setFont(Theme.font(12, Font.PLAIN));
        commandStateLabel.setBorder(Theme.pad(0, Theme.SPACE_SM));
        top.add(commandStateLabel, BorderLayout.CENTER);
        top.add(buttonRow(FlowLayout.RIGHT,
                button("Open Logs", false, event -> runArtifacts.openLogsDirectory()),
                button("Save Log", false, event -> saveConsoleLog()),
                button("Clear", false, event -> console.clearOutput()),
                button("Stop", false, event -> stopCommand())), BorderLayout.EAST);
        panel.add(top, BorderLayout.NORTH);
        panel.add(scroll(console), BorderLayout.CENTER);
        return panel;
    }

}
