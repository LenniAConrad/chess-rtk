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
import application.gui.workbench.command.Console;
import application.gui.workbench.ui.FileDialogs;
import application.gui.workbench.ui.WrappingFlowLayout;
import application.gui.workbench.ui.HoldButton;
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
import static application.gui.workbench.ui.Ui.controlRow;
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
     * Preferred size of the right-side rail next to the shared board (Analyze /
     * Play / Relations all use the same rail proportion).
     */
    private static final Dimension SIDE_RAIL_SIZE = new Dimension(400, 560);

    /**
     * Vertical-only row padding shared by every settings-group row.
     */
    private static final Insets SETTINGS_ROW_INSETS = new Insets(3, 0, 3, 0);

    /**
     * ECO explorer panel, created lazily with the board detail tabs.
     */
    protected EcoExplorerPanel ecoExplorerPanel;

    /**
     * Setup editor panel, created lazily with the board detail tabs.
     */
    protected BoardEditorPanel boardEditorPanel;

    /**
     * The shared board stage (board + material strips) re-parented into the
     * active Analyze/Play/Relations mode slot.
     */
    private transient application.gui.workbench.board.BoardStage sharedBoardStage;

    /**
     * Board slot in the Analyze mode card; hosts the shared board when Analyze
     * is active.
     */
    private transient JPanel analyzeBoardSlot;

    /**
     * Board slot in the Play mode card.
     */
    private transient JPanel playBoardSlot;

    /**
     * Board slot in the Relations mode card.
     */
    private transient JPanel relationsBoardSlot;

    /**
     * Relations control rail, retained so the shared board can be re-synced with
     * its tactical overlay when the Relations mode activates.
     */
    private transient application.gui.workbench.relations.RelationsPanel relationsControls;

    /**
     * Settings piece-set picker, retained so {@link #applyPieceSet} can keep its
     * selection in step when the set changes.
     */
    private transient application.gui.workbench.ui.ChipGroup pieceSetChips;

    /**
     * Creates the unified Board surface: one workspace hosting the Analyze,
     * Play, Solve, and Relations modes behind a switcher, replacing the four
     * former top-level tabs. Analyze is built eagerly (it wires the main board
     * and the nested analysis tabs at startup); the other modes build lazily on
     * first selection.
     *
     * @return board workspace component
     */
    protected JComponent createBoardWorkspaceTab() {
        // One shared board backs the Analyze, Play, and Relations modes: it is
        // re-parented into the active mode's slot and reconfigured for it by
        // configureBoardForMode, so a position carries across modes with no
        // duplicate widgets. Solve keeps its own board (puzzles step their own
        // positions, independent of the analysis line). The eval bar lives on
        // the shared board and paints inside its paint pass.
        evalBar.setToolTipText("Engine evaluation");
        board.setEvalBar(evalBar);
        sharedBoardStage = new application.gui.workbench.board.BoardStage(board);
        boardWorkspace = new application.gui.workbench.ui.SwitchedWorkspace(
                new String[] { "Analyze", "Play", "Solve", "Relations" },
                java.util.List.of(
                        this::createBoardTab,
                        this::createPlayTab,
                        this::createPuzzleTab,
                        this::createRelationsTab),
                BOARD_ANALYZE);
        boardWorkspace.setModeListener(this::configureBoardForMode);
        return boardWorkspace;
    }

    /**
     * Re-parents the single shared board into the activated board mode's slot
     * and reconfigures it for that mode. Analyze and Play are interactive
     * (turn-gated input, the analysis position, no relation overlay); Relations
     * is a read-only tactical overlay; Solve keeps its own board, so this is a
     * no-op for it.
     *
     * @param mode activated board mode (see BOARD_* constants)
     */
    private void configureBoardForMode(int mode) {
        JPanel slot = switch (mode) {
            case BOARD_ANALYZE -> analyzeBoardSlot;
            case BOARD_PLAY -> playBoardSlot;
            case BOARD_RELATIONS -> relationsBoardSlot;
            default -> null;
        };
        if (slot == null || sharedBoardStage == null) {
            return;
        }
        if (sharedBoardStage.getParent() != slot) {
            slot.add(sharedBoardStage, BorderLayout.CENTER);
            slot.revalidate();
            slot.repaint();
        }
        if (mode == BOARD_RELATIONS) {
            // A visualization, not an editor: dragging is disabled and the
            // current position's relation channels are drawn onto the board.
            board.setDragStartFilter(context -> false);
            if (relationsControls != null) {
                relationsControls.syncToBoard();
            }
        } else {
            // Analyze / Play: interactive. Drop any relation overlay, re-arm the
            // move funnel and turn-gated input, and restore the analysis line.
            board.clearMarkup();
            board.setMoveHandler(this::playMove);
            board.setDragStartFilter(context ->
                    playSession == null || playSession.isHumanInputAllowed());
            if (currentPosition != null) {
                board.setPositionInstant(currentPosition, chess.core.Move.NO_MOVE);
            }
        }
    }

    /**
     * Creates an empty board slot: a transparent host the shared board stage is
     * re-parented into when its owning mode activates.
     *
     * @return board slot panel
     */
    private JPanel boardSlotPanel() {
        return transparentPanel(new BorderLayout());
    }

    /**
     * Creates the unified Engine surface: one workspace hosting the neural
     * network visualizer and the PUCT/MCTS search behind a switcher, replacing
     * the former Network and MCTS top-level tabs. The Network mode is built
     * first; the Search mode builds lazily on first selection.
     *
     * @return engine workspace component
     */
    protected JComponent createEngineWorkspaceTab() {
        engineWorkspace = new application.gui.workbench.ui.SwitchedWorkspace(
                new String[] { "Evaluator", "Search", "Tree" },
                java.util.List.of(
                        this::createNetworkTab,
                        this::createMctsTab,
                        this::createTreeTab),
                ENGINE_NETWORK);
        return engineWorkspace;
    }

    /**
     * Creates the Run surface: the single command builder (Build). Batch
     * workflows are now commands inside this builder, and the Console and Logs
     * are first-class top-level surfaces, so the Run tab no longer needs a
     * mode switcher.
     *
     * @return run workspace component
     */
    protected JComponent createRunWorkspaceTab() {
        return createCommandTab();
    }

    /**
     * Creates the board tab.
     *
     * @return tab component
     */
    protected JComponent createBoardTab() {
        // The shared board is re-parented into this slot when Analyze activates;
        // board wiring (move handler, drag filter, eval bar) lives in
        // createBoardWorkspaceTab / configureBoardForMode.
        analyzeBoardSlot = boardSlotPanel();

        JPanel side = transparentPanel(new BorderLayout(8, 8));
        side.setPreferredSize(SIDE_RAIL_SIZE);
        side.add(createBoardSideHeader(), BorderLayout.NORTH);
        side.add(createMovesAndTags(), BorderLayout.CENTER);

        JSplitPane boardPage = SplitPaneStyler.styledHorizontalSplit(analyzeBoardSlot, side, 0.68);

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
     * Opens a position in a new detached Board tab — an independent analysis
     * workspace with its own board, so the shared analysis position is not
     * overwritten. The new tab is added next to the current one and selected;
     * the user can split it beside the source tab from the tab strip.
     *
     * @param fen FEN to load into the new board
     */
    @Override
    protected void openFenInNewBoard(String fen) {
        if (fen == null || fen.isBlank() || tabs == null) {
            return;
        }
        JComponent workspace = new AnalysisWorkspacePanel(fen, board.isWhiteDown(), this::buildAnalyzeArgs,
                args -> runCommand(args, null), this::copyText);
        tabs.openInstance(TAB_BOARD, workspace);
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
        fenField.setToolTipText("Paste a FEN and press Enter to load");
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
        // No separate "Load" button — pressing Enter in the FEN field loads the
        // position (see the field tooltip), so the row is just transport + Reset.
        JPanel transportRow = transparentPanel(new WrappingFlowLayout(FlowLayout.LEFT, 8, 0));
        transportRow.add(transportGroup);
        transportRow.add(iconButton("Reset", event -> startNewGame(Setup.getStandardStartFEN())));
        grid(panel, transportRow, c, 0, 2, 4, 1);
        grid(panel, buttonRow(FlowLayout.LEFT,
                iconButton("Flip", event -> {
                    board.setWhiteDown(!board.isWhiteDown());
                    appendConsole("Board flipped\n");
                }),
                iconButton("Copy FEN", event -> copyText(fenField.getText())),
                createExportBoardButton()), c, 0, 3, 4, 1);
        // The piece-artwork picker lives once, in Settings > Display > Appearance —
        // it was duplicated here on the rail. Removing the rail copy trims a row
        // of chrome from the analyze rail without losing the control.
        grid(panel, createPgnExplorerLauncher(), c, 0, 4, 4, 1);
        return panel;
    }

    /**
     * Returns the piece-set option labels in {@link PieceSet} ordinal order, so a
     * selector's index maps straight onto {@link PieceSet#values()}.
     *
     * @return piece-set labels
     */
    private static String[] pieceSetLabels() {
        PieceSet[] sets = PieceSet.values();
        String[] labels = new String[sets.length];
        for (int i = 0; i < sets.length; i++) {
            labels[i] = sets[i].label();
        }
        return labels;
    }

    /**
     * Applies a piece-artwork set to the board and keeps every piece-set control
     * (the board toolbar switcher and the Settings picker) in sync so the two
     * surfaces never drift. A no-op when the set is already active.
     *
     * @param set piece set to apply
     */
    protected void applyPieceSet(PieceSet set) {
        if (set == null || set == pieceSet) {
            return;
        }
        pieceSet = set;
        board.setPieceSet(set);
        saveDisplaySettings();
        // Re-sync the Settings picker. ChipGroup.setSelectedIndex never fires its
        // callback, so this cannot recurse back into applyPieceSet.
        if (pieceSetChips != null) {
            pieceSetChips.setSelectedIndex(set.ordinal());
        }
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
        JPanel panel = transparentPanel(new WrappingFlowLayout(FlowLayout.LEFT, 6, 0));
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
        Theme.table(movesTable, Theme.TABLE_ROW_HEIGHT);
        // Render the SAN column with inline chess-piece SVGs. Pin the
        // columns so a model data refresh does not drop the custom renderer.
        movesTable.setAutoCreateColumnsFromModel(false);
        movesTable.getColumnModel().getColumn(1).setCellRenderer(new SanRenderer());
        // Pin compact widths so the narrow index/SAN/UCI columns stop floating
        // and the trailing flags column absorbs the slack instead of leaving a
        // ragged, mostly-empty gap.
        movesTable.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_LAST_COLUMN);
        javax.swing.table.TableColumnModel moveColumns = movesTable.getColumnModel();
        moveColumns.getColumn(0).setMaxWidth(46);
        moveColumns.getColumn(0).setPreferredWidth(46);
        moveColumns.getColumn(1).setPreferredWidth(96);
        if (moveColumns.getColumnCount() > 2) {
            moveColumns.getColumn(2).setPreferredWidth(82);
        }
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
        // Open on the position-summary "Data" view rather than the full legal-move
        // dump: the move count is already in the analysis status line, so leading
        // with a 34-row table is noise. The Moves tab stays one click away.
        int dataTab = boardDetailTabs.indexOfTab("Data");
        if (dataTab >= 0) {
            boardDetailTabs.setSelectedIndex(dataTab);
        }
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
        // The editor owns its own board now, so it no longer hijacks the shared
        // board into setup-edit mode; Apply commits the edited position instead.
        boardEditorPanel = new BoardEditorPanel(this::currentFen, this::applyBoardEditorFen, this::copyText);
        return scroll(fillViewport(boardEditorPanel));
    }

    /**
     * Loads the current position into the editor whenever its tab is opened.
     */
    protected void syncBoardEditorMode() {
        if (boardEditorPanel == null || boardDetailTabs == null) {
            return;
        }
        if (isBoardEditorSelected()) {
            boardEditorPanel.loadFen(currentFen());
        }
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
        appearanceC.insets = SETTINGS_ROW_INSETS;
        // VS Code-style horizontal chip picker for the workbench theme.
        // Replaces the previous single Dark-mode toggle so users see both
        // choices at a glance and can flip with one click.
        application.gui.workbench.ui.ChipGroup themePicker =
                new application.gui.workbench.ui.ChipGroup(java.util.List.of("Light", "Dark"));
        themePicker.setSelectedIndex(isDarkMode() ? 1 : 0);
        themePicker.setOnSelect(index -> setDarkMode(index == 1));
        themePicker.setToolTipText("Switch the workbench palette");
        grid(appearance, createLabelDetailRow("Theme", "Pick the workbench colour palette",
                themePicker), appearanceC, 0, 0, 1, 1);

        // Piece artwork picker. Shares state with the board-toolbar switcher via
        // applyPieceSet so the two never drift; consolidating the preference here
        // is what makes the Settings dialog the one home for display choices.
        application.gui.workbench.ui.ChipGroup piecePicker =
                new application.gui.workbench.ui.ChipGroup(java.util.List.of(pieceSetLabels()));
        piecePicker.setSelectedIndex(pieceSet.ordinal());
        piecePicker.setOnSelect(index -> applyPieceSet(PieceSet.values()[index]));
        piecePicker.setToolTipText("Choose the chess piece artwork set");
        pieceSetChips = piecePicker;
        grid(appearance, createLabelDetailRow("Pieces", "Artwork used to draw the pieces",
                piecePicker), appearanceC, 0, 1, 1, 1);

        JPanel soundSettings = settingsGroupPanel();
        GridBagConstraints soundC = constraints();
        soundC.insets = SETTINGS_ROW_INSETS;
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
        boardC.insets = SETTINGS_ROW_INSETS;
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
        analysisC.insets = SETTINGS_ROW_INSETS;
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
     * Builds a settings row with a bold title over a muted detail line on the
     * left and a control on the right. Shared by the Appearance group's Theme and
     * Pieces rows so the two read identically.
     *
     * @param title bold row title
     * @param detail muted one-line description
     * @param control right-aligned control
     * @return row panel
     */
    private static JPanel createLabelDetailRow(String title, String detail, JComponent control) {
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(Theme.font(13, Font.BOLD));
        Theme.foreground(titleLabel, Theme.ForegroundRole.TEXT);
        JLabel detailLabel = new JLabel(detail);
        detailLabel.setFont(Theme.font(11, Font.PLAIN));
        Theme.foreground(detailLabel, Theme.ForegroundRole.MUTED);
        JPanel copy = transparentPanel(new BorderLayout(0, 1));
        copy.add(titleLabel, BorderLayout.NORTH);
        copy.add(detailLabel, BorderLayout.CENTER);
        JPanel row = transparentPanel(new BorderLayout(Theme.SPACE_SM, 0));
        row.add(copy, BorderLayout.CENTER);
        row.add(control, BorderLayout.EAST);
        return row;
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
        protocolC.insets = SETTINGS_ROW_INSETS;
        grid(protocol, label("protocol"), protocolC, 0, 0, 1, 1);
        grid(protocol, engineProtocolField, protocolC, 1, 0, 2, 1);
        JButton chooseProtocol = button("Choose Protocol", false,
                event -> FileDialogs.choosePath(this, engineProtocolField, false, "Choose engine protocol",
    new FileNameExtensionFilter("TOML files", "toml")));
        grid(protocol, chooseProtocol, protocolC, 3, 0, 1, 1);

        JPanel limits = settingsGroupPanel();
        GridBagConstraints limitsC = constraints();
        limitsC.insets = SETTINGS_ROW_INSETS;
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

        JSplitPane gamePage = SplitPaneStyler.styledHorizontalSplit(createGameHistoryPanel(), tools, 0.68);
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
        importC.insets = SETTINGS_ROW_INSETS;
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
                button("Copy FENs", false, event -> copyText(gameModel.fenList())));

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
        commandForm.setPositionContext(new application.gui.workbench.command.PositionListEditor.Context() {
            /**
             * Returns the current board FEN.
             *
             * @return current FEN
             */
            @Override
            public String currentFen() {
                return WindowBoardLayer.this.currentFen();
            }

            /**
             * Shows a command-builder error dialog.
             *
             * @param title dialog title
             * @param message dialog message
             */
            @Override
            public void showError(String title, String message) {
                WindowBoardLayer.this.showError(title, message);
            }

            /**
             * Returns the parent component for modal dialogs.
             *
             * @return dialog parent component
             */
            @Override
            public java.awt.Component dialogParent() {
                return WindowBoardLayer.this;
            }
        });
        styleCommandPreviewField(commandField);
        depthModel.addChangeListener(event -> requestCommandPreviews());
        multipvModel.addChangeListener(event -> requestCommandPreviews());
        threadsModel.addChangeListener(event -> requestCommandPreviews());

        commandField.setEditable(false);
        commandField.setToolTipText("Generated command");
        // A self-hugging band: BoxLayout would otherwise stretch an unbounded
        // panel to fill the leftover height, leaving a tall empty preview band.
        PreviewBand previewRow = new PreviewBand();
        JLabel previewLabel = new JLabel("$");
        previewLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        Theme.foreground(previewLabel, Theme.ForegroundRole.MUTED);
        previewRow.add(previewLabel, BorderLayout.WEST);
        // The preview row is ELEVATED_SOLID; declare it so the transparent
        // command field's viewport matches the row instead of a darker box.
        JScrollPane commandScroll = scroll(commandField, () -> Theme.ELEVATED_SOLID);
        commandScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        // Wrapping is on, so there is never horizontal overflow to scroll.
        commandScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
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
        // Absorb leftover vertical space below the (self-hugging) preview band.
        topStack.add(Box.createVerticalGlue());
        panel.add(topStack, BorderLayout.CENTER);

        runCommandButton = button("Run", true, event -> runSelectedTemplate());
        panel.add(controlRow(FlowLayout.LEFT,
                runCommandButton,
                button("Copy", false, event -> copyBuiltCommand()),
                button("Reset", false, event -> resetSelectedTemplate()),
                button("Clear Flags", false, event -> clearOptionalTemplateOptions()),
                new HoldButton("Stop", this::stopCommand, true)), BorderLayout.SOUTH);

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
        // Survive theme toggles as a transparent preview instead of being
        // restyled into an opaque bordered input by Theme.area().
        field.putClientProperty(Theme.CLIENT_TRANSPARENT_FIELD, Boolean.TRUE);
        field.setBorder(BorderFactory.createEmptyBorder());
        field.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        field.setForeground(Theme.TEXT);
        field.setCaretColor(Theme.MUTED);
        field.setSelectionColor(Theme.SELECTION_SOLID);
        field.setEditable(false);
        // Wrap long commands within the preview width instead of scrolling
        // sideways; the band caps at five rows and scrolls vertically beyond.
        field.setLineWrap(true);
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
     * Creates the live search-tree graph tab.
     *
     * @return tree tab
     */
    protected JComponent createTreeTab() {
        return treePanel();
    }

    /**
     * Creates an independent search-tree graph tab instance.
     *
     * @return tree tab
     */
    protected JComponent createDetachedTreeTab() {
        return createDetachedTreePanel();
    }

    /**
     * Creates the Play-vs-engine tab.
     *
     * @return play tab
     */
    @Override
    protected JComponent createPlayTab() {
        // Play reads as a real game screen: the shared board on the left, the
        // setup and in-game controls as a right rail. Play and Analyze share the
        // one board and the same move funnel / turn-gated input, so a position
        // carries straight across modes; board wiring lives in
        // configureBoardForMode. The board is re-parented into this slot when
        // Play activates.
        playBoardSlot = boardSlotPanel();

        // Fill the rail like a real game screen: setup/controls on top, the live
        // move list below — chess.com / lichess show the moves beside the board
        // during play. The list is a second view on the shared game model, so it
        // tracks every move with no extra wiring.
        javax.swing.JTable playMoves = new javax.swing.JTable(gameModel);
        Theme.table(playMoves, Theme.TABLE_ROW_HEIGHT);
        playMoves.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_LAST_COLUMN);
        playMoves.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // The setup form scrolls on its own (it is tall once Advanced/Expert are
        // open) and the move list scrolls on its own, joined by a resizable
        // sash — so neither is ever clipped on a short window.
        JComponent setup = scroll(fillViewport(playPanel()));
        JComponent moves = titled("Moves", scroll(playMoves));
        JSplitPane rail = SplitPaneStyler.styledVerticalSplit(setup, moves, 0.62);
        rail.setPreferredSize(SIDE_RAIL_SIZE);

        JSplitPane playPage = SplitPaneStyler.styledHorizontalSplit(playBoardSlot, rail, 0.68);
        return playPage;
    }

    /**
     * Creates the tactical-incidence Relations tab: a dedicated read-only board on
     * the left overlaid with the OTIS relation channels, and the channel/opacity
     * controls as a right rail — mirroring the Play tab's split. The board shows
     * the current analysis position (via the Sync control) or a pasted FEN.
     *
     * @return relations tab
     */
    @Override
    protected JComponent createRelationsTab() {
        // Relations overlays the OTIS tactical channels on the SHARED board: the
        // board is re-parented into this slot and put read-only when Relations
        // activates (see configureBoardForMode), which also re-syncs the overlay
        // to the current position. So a position analyzed elsewhere is one click
        // away from its tactical view, on the same board.
        relationsBoardSlot = boardSlotPanel();
        relationsControls =
                new application.gui.workbench.relations.RelationsPanel(board, () -> currentPosition);
        JComponent rail = scroll(fillViewport(relationsControls));
        rail.setPreferredSize(SIDE_RAIL_SIZE);
        return SplitPaneStyler.styledHorizontalSplit(relationsBoardSlot, rail, 0.68);
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
     * Saves the given console's visible text to a user-chosen log file.
     *
     * @param target console whose text to save
     */
    protected void saveConsoleLog(application.gui.workbench.command.Console target) {
        JFileChooser chooser = FileDialogs.createFileChooser(null, PathOps.dumpPath("workbench-console.log").toFile(),
    new FileNameExtensionFilter("Log files", "log", "txt"));
        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = FileDialogs.ensureExtension(chooser.getSelectedFile(), ".log");
        String contents = target.getText();
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
     * Creates the primary console surface (uses the shared console instance and
     * the shared run-state label).
     *
     * @return panel
     */
    protected JComponent createConsolePanel() {
        return createConsolePanelInstance(true);
    }

    /**
     * Creates an independent duplicate console surface with its own console
     * instance, wired into the output fan-out via {@link #consoles}.
     *
     * @return duplicate console panel
     */
    protected JComponent createDetachedConsolePanel() {
        return createConsolePanelInstance(false);
    }

    /**
     * Builds a console surface. The primary surface reuses the shared
     * {@code console} and {@code commandStateLabel}; a duplicate builds a fresh
     * console (registered in {@link #consoles}) and its own state label so a
     * Swing component is never parented twice.
     *
     * @param primary true for the canonical console surface
     * @return console panel
     */
    private JComponent createConsolePanelInstance(boolean primary) {
        Console target = primary ? console : new Console();
        if (!primary) {
            consoles.add(target);
        }
        JLabel state = primary ? commandStateLabel : new JLabel("Idle");
        JPanel panel = new SurfacePanel(new BorderLayout(6, 6));
        JPanel top = transparentPanel(new BorderLayout());
        top.add(Theme.section("Console"), BorderLayout.WEST);
        Theme.foreground(state, Theme.ForegroundRole.MUTED);
        state.setFont(Theme.font(12, Font.PLAIN));
        state.setBorder(Theme.pad(0, Theme.SPACE_SM));
        top.add(state, BorderLayout.CENTER);
        top.add(controlRow(FlowLayout.RIGHT,
                button("Open Logs", false, event -> showLogsDock()),
                button("Save Log", false, event -> saveConsoleLog(target)),
                new HoldButton("Clear", target::clearOutput, true),
                new HoldButton("Stop", this::stopCommand, true)), BorderLayout.EAST);
        panel.add(top, BorderLayout.NORTH);
        target.setPlaceholder("Run a command to see its output here.");
        // The shared console is constructed eagerly (a WindowBase field) before
        // the theme is applied; a duplicate is built fresh. Either way, apply the
        // current theme now that it joins the tree so it is never light-on-dark.
        target.applyConsoleTheme();
        panel.add(scroll(target), BorderLayout.CENTER);
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
     * Creates an independent duplicate logs surface.
     *
     * @return duplicate logs component
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

    /**
     * The generated-command preview band. Caps its own height to its content so
     * the surrounding {@code BoxLayout} cannot stretch it into a tall empty
     * band; the command field caps at five rows and scrolls vertically beyond.
     */
    private static final class PreviewBand extends JPanel {

        /**
         * Serialization identifier for Swing panel compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Creates the preview band.
         */
        PreviewBand() {
            super(new BorderLayout(8, 4));
        }

        /**
         * Bounds the band's height to its preferred content height.
         *
         * @return maximum size hugging the content height
         */
        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

}
