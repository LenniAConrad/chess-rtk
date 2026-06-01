package application.gui.workbench.window;

import application.cli.PathOps;
import application.gui.workbench.audio.SoundCue;
import application.gui.workbench.audio.SoundService;
import application.gui.workbench.board.BoardEditorPanel;
import application.gui.workbench.board.BoardExportActions;
import application.gui.workbench.game.EcoExplorerPanel;
import application.gui.workbench.game.SanRenderer;
import application.gui.workbench.layout.SplitPaneStyler;
import application.gui.workbench.session.LogPanel;
import application.gui.workbench.ui.FileDialogs;
import application.gui.workbench.ui.SegmentedSwitcher;
import application.gui.workbench.ui.SurfacePanel;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Toast;
import chess.images.assets.PieceSet;
import chess.core.Setup;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.filechooser.FileNameExtensionFilter;

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
import static application.gui.workbench.ui.Ui.labeledControl;
import static application.gui.workbench.ui.Ui.optionGroup;
import static application.gui.workbench.ui.Ui.scroll;
import static application.gui.workbench.ui.Ui.styleAreas;
import static application.gui.workbench.ui.Ui.styleFields;
import static application.gui.workbench.ui.Ui.stylePopupMenu;
import static application.gui.workbench.ui.Ui.stylePopupMenuItem;
import static application.gui.workbench.ui.Ui.styleSlider;
import static application.gui.workbench.ui.Ui.styleSpinners;
import static application.gui.workbench.ui.Ui.tabbedPane;
import static application.gui.workbench.ui.Ui.titled;
import static application.gui.workbench.ui.Ui.transparentPanel;

/**
 * A new native Swing command and analysis workbench for ChessRTK.
 */

public abstract class WindowBoardLayer extends WindowLifecycle {
    /**
     * Serialization identifier for Swing frame compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * ECO explorer panel, created lazily with the board detail tabs.
     */
    protected EcoExplorerPanel ecoExplorerPanel;

    /**
     * Setup editor panel, created lazily with the board detail tabs.
     */
    protected BoardEditorPanel boardEditorPanel;

    /**
     * Creates the board tab.
     *
     * @return tab component
     */
    protected JComponent createBoardTab() {
        board.setMoveHandler(this::playMove);
        // In Play mode, only let the human pick up a piece when it is their turn
        // and the engine is idle. Outside a Play game this always allows input.
        board.setDragStartFilter(context ->
                playSession == null || playSession.isHumanInputAllowed());
        evalBar.setToolTipText("Engine evaluation");

        // The eval bar is a child of the board panel so it paints inside the
        // board's own paint pass — flush to the square, matched to its height,
        // and free of the sibling-overlap flicker.
        board.setEvalBar(evalBar);
        JPanel boardStage = transparentPanel(new BorderLayout());
        // Lichess-style material strips flush above and below the board, showing
        // each side's captured pieces and a +N material advantage.
        application.gui.workbench.board.MaterialStrip materialTop =
                new application.gui.workbench.board.MaterialStrip(board);
        application.gui.workbench.board.MaterialStrip materialBottom =
                new application.gui.workbench.board.MaterialStrip(board);
        board.setMaterialStrips(materialTop, materialBottom);
        boardStage.add(materialTop, BorderLayout.NORTH);
        boardStage.add(board, BorderLayout.CENTER);
        boardStage.add(materialBottom, BorderLayout.SOUTH);

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

        // The "Position" title was previously added here as part of a
        // hierarchy pass but flagged as redundant by WorkbenchBoardRegression:
        // the FEN field and transport row already speak for themselves.
        styleFields(fenField);
        fenField.addActionListener(event -> setPositionFromField());
        grid(panel, fenField, c, 0, 0, 4, 1);

        boardStartButton = iconButton("Start", event -> jumpGameTo(0));
        boardBackButton = iconButton("Back", event -> navigateGame(-1));
        boardForwardButton = iconButton("Forward", event -> navigateGame(1));
        boardEndButton = iconButton("End", event -> jumpGameTo(gameModel.lastPly()));
        setTransportShortcut(boardStartButton, "Up / Home / Numpad 8 / Alt+Up");
        setTransportShortcut(boardBackButton, "Left / Numpad 4 / Alt+Left");
        setTransportShortcut(boardForwardButton, "Right / Numpad 6 / Alt+Right");
        setTransportShortcut(boardEndButton, "Down / End / Numpad 2 / Alt+Down");
        updateBoardNavigationControls();
        // Transport (start/back/forward/end) is a single logical widget;
        // pack the four icon buttons into their own tight cell so the row
        // doesn't read as four peer-level actions next to Load and Reset.
        JComponent transportGroup = transportButtonGroup(boardStartButton,
                boardBackButton, boardForwardButton, boardEndButton);
        grid(panel, buttonRow(FlowLayout.LEFT,
                // Load FEN is demoted to secondary — pressing Enter in the
                // FEN field already loads it, and the headline primary on
                // the Analyze tab should be the analysis action, not the
                // position-input action.
                button("Load", false, event -> setPositionFromField())), c, 0, 2, 1, 1);
        JPanel transportRow = transparentPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        transportRow.add(transportGroup);
        transportRow.add(iconButton("Reset", event -> startNewGame(Setup.getStandardStartFEN())));
        grid(panel, transportRow, c, 1, 2, 3, 1);
        grid(panel, buttonRow(FlowLayout.LEFT,
                iconButton("Flip", event -> {
                    board.setWhiteDown(!board.isWhiteDown());
                    appendConsole("Board flipped\n");
                }),
                iconButton("Copy FEN", event -> copyText(fenField.getText())),
                createExportBoardButton()), c, 0, 3, 4, 1);
        JPanel pieceRow = transparentPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        pieceRow.add(label("Pieces"));
        pieceRow.add(createPieceSetSwitcher());
        grid(panel, pieceRow, c, 0, 4, 4, 1);
        grid(panel, createPgnExplorerLauncher(), c, 0, 5, 4, 1);
        return panel;
    }

    /**
     * Builds the segmented selector that switches the board's piece artwork
     * set between the available {@link PieceSet} themes.
     *
     * @return piece-set switcher
     */
    private SegmentedSwitcher createPieceSetSwitcher() {
        PieceSet[] sets = PieceSet.values();
        String[] labels = new String[sets.length];
        for (int i = 0; i < sets.length; i++) {
            labels[i] = sets[i].label();
        }
        SegmentedSwitcher switcher = new SegmentedSwitcher(labels);
        for (int i = 0; i < sets.length; i++) {
            if (sets[i] == pieceSet) {
                switcher.setSelectedIndex(i);
            }
        }
        switcher.addActionListener(event -> {
            pieceSet = PieceSet.values()[switcher.getSelectedIndex()];
            board.setPieceSet(pieceSet);
            saveDisplaySettings();
        });
        switcher.setToolTipText("Choose the chess piece artwork set");
        return switcher;
    }

    /**
     * Creates a single Export button that opens a popup with PNG, SVG, and
     * clipboard targets. Replaces two peer-level export icons so the row reads
     * as one action.
     *
     * @return export button
     */
    private JButton createExportBoardButton() {
        JPopupMenu menu = new JPopupMenu();
        menu.add(exportMenuItem("Save PNG…", event -> BoardExportActions.exportPng(this, board)));
        menu.add(exportMenuItem("Save SVG…", event -> BoardExportActions.exportSvg(this, board)));
        menu.addSeparator();
        menu.add(exportMenuItem("Copy image", event -> BoardExportActions.copyImage(this, board)));
        menu.add(exportMenuItem("Copy SVG", event -> BoardExportActions.copySvg(this, board)));
        stylePopupMenu(menu);
        JButton trigger = iconButton("Export", null);
        for (java.awt.event.ActionListener listener : trigger.getActionListeners()) {
            trigger.removeActionListener(listener);
        }
        trigger.addActionListener(event -> {
            SoundService.play(SoundCue.UI_CLICK);
            menu.show(trigger, 0, trigger.getHeight());
        });
        trigger.setToolTipText("Export board image (PNG / SVG / Copy)");
        return trigger;
    }

    /**
     * Packs transport icon buttons into a single tight pill so the four
     * actions read as one navigation widget rather than four peers.
     *
     * @param buttons transport buttons in display order
     * @return wrapped transport group
     */
    private static JComponent transportButtonGroup(JButton... buttons) {
        JPanel group = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        group.setOpaque(true);
        group.setBackground(Theme.ELEVATED_SOLID);
        // Square border matches the VS Code pattern: structural strips and
        // segmented groups are hard-edged; only interactive controls inside
        // them carry the subtle Theme.RADIUS rounding.
        group.setBorder(BorderFactory.createLineBorder(Theme.LINE, 1, false));
        for (JButton button : buttons) {
            group.add(button);
        }
        return group;
    }

    /**
     * Builds a styled popup menu item for the board export menu.
     *
     * @param label menu label
     * @param listener click listener
     * @return menu item
     */
    private static JMenuItem exportMenuItem(String label, java.awt.event.ActionListener listener) {
        JMenuItem item = new JMenuItem(label);
        stylePopupMenuItem(item);
        item.addActionListener(listener);
        return item;
    }

    /**
     * Creates a single PGN explorer launcher button. Replaces the previous
     * faux-input field + button pair that mimicked a search affordance the
     * field did not actually have.
     *
     * @return PGN explorer launcher
     */
    private JComponent createPgnExplorerLauncher() {
        JPanel row = transparentPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JButton open = button("Open PGN…", false, event -> showPgnExplorer());
        open.setToolTipText("Open PGN explorer (Ctrl+P)");
        row.add(open);
        return row;
    }

    /**
     * Creates board controls.
     *
     * @return controls
     */
    protected JComponent createAnalysisControls() {
        JPanel panel = new SurfacePanel(new GridBagLayout());
        GridBagConstraints c = constraints();
        // Row 0: section title + the live-engine toggle, side by side. The
        // toggle was buried at row 4 below the depth/time spinners; that
        // hid a feature that fundamentally changes app behaviour (constant
        // background CPU vs on-demand). Promoting it to the section header
        // makes the on/off state the first thing the eye reads.
        JPanel sectionHeader = transparentPanel(new BorderLayout(Theme.SPACE_SM, 0));
        sectionHeader.add(Theme.section("Analysis"), BorderLayout.WEST);
        liveEngineToggle.setSelected(liveExternalEngineEnabled);
        liveEngineToggle.addActionListener(event -> setLiveExternalEngineEnabled(liveEngineToggle.isSelected()));
        multipvModel.addChangeListener(event -> requestLiveAnalysisUpdate());
        threadsModel.addChangeListener(event -> requestLiveAnalysisUpdate());
        sectionHeader.add(liveEngineToggle, BorderLayout.EAST);
        grid(panel, sectionHeader, c, 0, 0, 4, 1);
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
        grid(panel, collapsible("More", createAdvancedAnalysisControls(), false), c, 0, 4, 4, 1);
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
                if (event.getClickCount() == 2
                        && (playSession == null || playSession.isHumanInputAllowed())) {
                    int row = movesTable.getSelectedRow();
                    if (row >= 0 && row < visibleMoves.length) {
                        playMove(visibleMoves[row]);
                    }
                }
            }
        });

        // Position inspector: read-only views of the current position. Editor
        // remains here as the one creative "change the position" affordance;
        // MCTS / Settings / Engine moved to the dedicated Settings dialog so
        // this strip stops being an eight-tab junk drawer.
        boardDetailTabs = createSectionTabs();
        boardDetailTabs.addTab("Moves", titled("Legal Moves", scroll(movesTable)));
        boardDetailTabs.addTab("Tags", titled("Tags", scroll(tagCloud)));
        boardDetailTabs.addTab("ECO", createEcoExplorerPanel());
        boardDetailTabs.addTab("Data", createAnalysisDataPanel());
        boardDetailTabs.addTab("Editor", createBoardEditorPanel());
        boardDetailTabs.addChangeListener(event -> syncBoardEditorMode());
        syncBoardEditorMode();
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
        boardEditorPanel = new BoardEditorPanel(this::currentFen, this::applyBoardEditorFen, this::copyText);
        boardEditorPanel.attachBoard(board);
        return scroll(fillViewport(boardEditorPanel));
    }

    /**
     * Synchronizes direct board editing with the selected side tab.
     */
    protected void syncBoardEditorMode() {
        if (boardEditorPanel == null || boardDetailTabs == null) {
            board.setSetupEditMode(false);
            return;
        }
        boolean active = isBoardEditorSelected();
        if (active) {
            boardEditorPanel.loadFen(currentFen());
        }
        boardEditorPanel.setEditingBoardActive(active);
    }

    /**
     * Returns whether the setup editor tab is selected.
     *
     * @return true when the setup editor is selected
     */
    protected boolean isBoardEditorSelected() {
        return boardDetailTabs != null
                && boardDetailTabs.getSelectedIndex() >= 0
                && "Editor".equals(boardDetailTabs.getTitleAt(boardDetailTabs.getSelectedIndex()));
    }

    /**
     * Applies a validated setup from the board editor.
     *
     * @param fen validated FEN
     */
    protected void applyBoardEditorFen(String fen) {
        if (playPositionLocked) {
            toast(application.gui.workbench.ui.Toast.Kind.WARNING,
                    "Finish or resign the game before changing the position");
            return;
        }
        startNewGame(fen);
        appendConsole("Board editor applied " + fen + "\n");
    }

    /**
     * Locks or unlocks position-entry controls while a Play game is active so
     * the displayed position cannot drift from the one the engine is playing.
     *
     * @param locked true to lock entry controls
     */
    @Override
    protected void setPlayPositionLocked(boolean locked) {
        playPositionLocked = locked;
        fenField.setEnabled(!locked);
        if (boardEditorPanel != null) {
            boardEditorPanel.setEnabled(!locked);
        }
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
        // VS Code-style horizontal chip picker for the workbench theme.
        // Replaces the previous single Dark-mode toggle so users see both
        // choices at a glance and can flip with one click.
        application.gui.workbench.ui.ChipGroup themePicker =
                new application.gui.workbench.ui.ChipGroup(java.util.List.of("Light", "Dark"));
        themePicker.setSelectedIndex(isDarkMode() ? 1 : 0);
        themePicker.setOnSelect(index -> setDarkMode(index == 1));
        themePicker.setToolTipText("Switch the workbench palette");
        JPanel themeRow = transparentPanel(new BorderLayout(Theme.SPACE_SM, 0));
        JLabel themeLabel = new JLabel("Theme");
        themeLabel.setFont(Theme.font(13, Font.BOLD));
        Theme.foreground(themeLabel, Theme.ForegroundRole.TEXT);
        JLabel themeDetail = new JLabel("Pick the workbench colour palette");
        themeDetail.setFont(Theme.font(11, Font.PLAIN));
        Theme.foreground(themeDetail, Theme.ForegroundRole.MUTED);
        JPanel themeCopy = transparentPanel(new BorderLayout(0, 1));
        themeCopy.add(themeLabel, BorderLayout.NORTH);
        themeCopy.add(themeDetail, BorderLayout.CENTER);
        themeRow.add(themeCopy, BorderLayout.CENTER);
        themeRow.add(themePicker, BorderLayout.EAST);
        grid(appearance, themeRow, appearanceC, 0, 0, 1, 1);

        JPanel soundSettings = settingsGroupPanel();
        GridBagConstraints soundC = constraints();
        soundC.insets = new Insets(3, 0, 3, 0);
        grid(soundSettings, settingsToggle("Sound effects",
                "Play procedural feedback for controls, moves, loaded positions, puzzles, MCTS, and long jobs",
                !SoundService.isMuted(), selected -> {
                    SoundService.setMuted(!selected);
                    if (settingsMenu != null) {
                        settingsMenu.syncMode();
                    }
                }), soundC, 0, 0, 1, 1);
        grid(soundSettings, labeledControl("Volume", createSoundVolumeSlider()),
                soundC, 0, 1, 1, 1);
        grid(soundSettings, button("Preview", false, event -> SoundService.play(SoundCue.POSITION_LOAD)),
                soundC, 0, 2, 1, 1);

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
        grid(panel, collapsible("Sound", soundSettings, true), c, 0, row++, 1, 1);
        grid(panel, collapsible("Board", boardSettings, true), c, 0, row++, 1, 1);
        grid(panel, collapsible("Analysis", analysisSettings, false), c, 0, row++, 1, 1);
        addVerticalFiller(panel, c, row, 1);
        return panel;
    }

    /**
     * Creates the compact global sound-volume slider.
     *
     * @return configured slider
     */
    private static JSlider createSoundVolumeSlider() {
        JSlider slider = new JSlider(0, 100, SoundService.volumePercent());
        styleSlider(slider);
        slider.setToolTipText("Set global workbench feedback volume");
        slider.setPreferredSize(new Dimension(190, Theme.CONTROL_HEIGHT));
        slider.addChangeListener(event -> SoundService.setVolumePercent(slider.getValue()));
        return slider;
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

        // Live preview line: shows the effective config that would be
        // applied so the user sees the side-effect of a tuning change
        // before the next Search/Analyze run.
        JLabel preview = new JLabel(" ");
        preview.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        Theme.foreground(preview, Theme.ForegroundRole.MUTED);
        preview.setBorder(Theme.pad(Theme.SPACE_SM, 0, 0, 0));
        Runnable updatePreview = () -> preview.setText(buildEnginePreview());
        engineProtocolField.getDocument().addDocumentListener(changeListener(updatePreview));
        engineNodesField.getDocument().addDocumentListener(changeListener(updatePreview));
        engineHashField.getDocument().addDocumentListener(changeListener(updatePreview));
        updatePreview.run();

        int row = 0;
        grid(panel, collapsible("Protocol", protocol, true), c, 0, row++, 4, 1);
        grid(panel, collapsible("Limits", limits, false), c, 0, row++, 4, 1);
        grid(panel, preview, c, 0, row++, 4, 1);
        addVerticalFiller(panel, c, row, 4);
        return panel;
    }

    /**
     * Builds the engine settings preview string. Renders the current field
     * values as the effective config so the user sees what would be applied
     * before launching another search.
     *
     * @return one-line preview text
     */
    private String buildEnginePreview() {
        StringBuilder line = new StringBuilder("preview: ");
        String protocolPath = engineProtocolField.getText().trim();
        line.append("protocol=").append(protocolPath.isEmpty() ? "<none>" : protocolPath);
        String nodes = engineNodesField.getText().trim();
        line.append("  ·  nodes=").append(nodes.isEmpty() ? "default" : nodes);
        String hash = engineHashField.getText().trim();
        line.append("  ·  hash=").append(hash.isEmpty() ? "default" : hash + " MB");
        return line.toString();
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
                // Demoted to secondary so the Analyze tab has a single
                // headline primary (Search) across all sections.
                button("Load Line", false, event -> loadGameText(gameInput.getText())),
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
        styleCommandPreviewField(commandField);
        depthModel.addChangeListener(event -> requestCommandPreviews());
        multipvModel.addChangeListener(event -> requestCommandPreviews());
        threadsModel.addChangeListener(event -> requestCommandPreviews());

        commandField.setEditable(false);
        commandField.setToolTipText("Generated command");
        JPanel previewRow = transparentPanel(new BorderLayout(8, 4));
        JLabel previewLabel = new JLabel("$");
        previewLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        Theme.foreground(previewLabel, Theme.ForegroundRole.MUTED);
        previewRow.add(previewLabel, BorderLayout.WEST);
        JScrollPane commandScroll = scroll(commandField);
        commandScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        commandScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        previewRow.add(commandScroll, BorderLayout.CENTER);
        previewRow.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE),
                Theme.pad(6, 10, 6, 10)));
        previewRow.setOpaque(true);
        previewRow.setBackground(Theme.ELEVATED_SOLID);

        // Top stack: command picker, the form, and the generated CLI preview
        // ride together as a single vertical block so the form and its
        // resulting command line stay visually connected. Previously the
        // preview sat at the bottom of the pane with a huge empty band
        // between it and the form, breaking the cause-and-effect line.
        JPanel topStack = new JPanel();
        topStack.setOpaque(false);
        topStack.setLayout(new BoxLayout(topStack, BoxLayout.Y_AXIS));
        commandPicker.setAlignmentX(LEFT_ALIGNMENT);
        commandForm.setAlignmentX(LEFT_ALIGNMENT);
        previewRow.setAlignmentX(LEFT_ALIGNMENT);
        topStack.add(commandPicker);
        topStack.add(Box.createVerticalStrut(8));
        topStack.add(commandForm);
        topStack.add(Box.createVerticalStrut(6));
        topStack.add(previewRow);
        panel.add(topStack, BorderLayout.CENTER);

        runCommandButton = button("Run", true, event -> runSelectedTemplate());
        panel.add(buttonRow(FlowLayout.LEFT,
                runCommandButton,
                button("Copy", false, event -> copyBuiltCommand()),
                button("Reset", false, event -> resetSelectedTemplate()),
                button("Clear Flags", false, event -> clearOptionalTemplateOptions()),
                button("Stop", false, event -> stopCommand())), BorderLayout.SOUTH);

        updateCommandOptions();
        updateBuiltCommand();
        return panel;
    }

    /**
     * Styles the generated command field so it reads as command output rather
     * than another input row: monospace text, no editable border, muted
     * foreground, transparent background that blends with the wrapper panel.
     *
     * @param field generated-command field
     */
    private static void styleCommandPreviewField(javax.swing.JTextArea field) {
        field.setOpaque(false);
        field.setBorder(BorderFactory.createEmptyBorder());
        field.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        field.setForeground(Theme.TEXT);
        field.setCaretColor(Theme.MUTED);
        field.setSelectionColor(Theme.SELECTION_SOLID);
        field.setEditable(false);
        field.setLineWrap(false);
        field.setWrapStyleWord(false);
        field.setRows(1);
        field.setColumns(72);
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
     * Creates the datasets tab.
     *
     * @return datasets tab
     */
    protected JComponent createDatasetTab() {
        return datasetPanel().component();
    }

    /**
     * Creates an independent datasets tab instance.
     *
     * @return datasets tab
     */
    protected JComponent createDetachedDatasetTab() {
        return createDetachedDatasetPanel().component();
    }

    /**
     * Creates the publishing tab.
     *
     * @return publish tab
     */
    protected JComponent createPublishTab() {
        return publishingPanel().component();
    }

    /**
     * Creates the position-description tab.
     *
     * @return position-description tab
     */
    protected JComponent createDescribeTab() {
        return positionDescriptionPanel();
    }

    /**
     * Creates an independent publishing tab instance.
     *
     * @return publish tab
     */
    protected JComponent createDetachedPublishTab() {
        return createDetachedPublishingPanel().component();
    }

    /**
     * Creates the network visualizer tab.
     *
     * @return network tab
     */
    protected JComponent createNetworkTab() {
        return networkPanel();
    }

    /**
     * Creates an independent network visualizer tab instance.
     *
     * @return network tab
     */
    protected JComponent createDetachedNetworkTab() {
        return createDetachedNetworkPanel();
    }

    /**
     * Creates the MCTS inspection tab.
     *
     * @return MCTS tab
     */
    protected JComponent createMctsTab() {
        return mctsPanel();
    }

    /**
     * Creates an independent MCTS inspection tab sharing the session.
     *
     * @return MCTS tab
     */
    protected JComponent createDetachedMctsTab() {
        return createDetachedMctsPanel();
    }

    /**
     * Creates the Play-vs-engine tab.
     *
     * @return play tab
     */
    @Override
    protected JComponent createPlayTab() {
        return playPanel();
    }

    /**
     * Creates a Play-vs-engine tab. Play is registered single-instance (no
     * duplicate factory), so this is not currently reached; it returns the
     * canonical play panel via the lazy session getter rather than binding a
     * second panel to the shared session (which would NPE on a null session and
     * drive the main board). Genuine per-tab Play needs its own session + host.
     *
     * @return play tab
     */
    @Override
    protected JComponent createDetachedPlayTab() {
        return playPanel();
    }

    /**
     * Creates the puzzle trainer tab.
     *
     * @return puzzle tab
     */
    protected JComponent createPuzzleTab() {
        return puzzlePanel();
    }

    /**
     * Creates an independent puzzle trainer tab instance.
     *
     * @return puzzle tab
     */
    protected JComponent createDetachedPuzzleTab() {
        return createDetachedPuzzlePanel();
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
        JFileChooser chooser = FileDialogs.createFileChooser(null, PathOps.dumpPath("workbench-console.log").toFile(),
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
                button("Open Logs", false, event -> showLogsDock()),
                button("Save Log", false, event -> saveConsoleLog()),
                button("Clear", false, event -> console.clearOutput()),
                button("Stop", false, event -> stopCommand())), BorderLayout.EAST);
        panel.add(top, BorderLayout.NORTH);
        panel.add(scroll(console), BorderLayout.CENTER);
        return panel;
    }

    /**
     * Creates the persisted-log tab.
     *
     * @return log tab component
     */
    protected JComponent createLogTab() {
        return primaryLogPanel();
    }

    /**
     * Creates an independent persisted-log tab instance.
     *
     * @return log tab component
     */
    protected JComponent createDetachedLogTab() {
        return createLogPanelInstance(false);
    }

    /**
     * Returns the lazily-created persisted-log browser.
     *
     * @return log panel
     */
    protected LogPanel primaryLogPanel() {
        if (logPanel == null) {
            logPanel = createLogPanelInstance(true);
        }
        return logPanel;
    }

    /**
     * Creates and registers a log browser instance.
     *
     * @param primary true when this is the canonical Logs tab
     * @return log panel
     */
    private LogPanel createLogPanelInstance(boolean primary) {
        if (primary && logPanel != null) {
            return logPanel;
        }
        LogPanel panel = new LogPanel(this::copyText);
        logPanels.add(panel);
        if (primary) {
            logPanel = panel;
        }
        return panel;
    }

}
