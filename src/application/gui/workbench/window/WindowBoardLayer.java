package application.gui.workbench.window;

import application.cli.PathOps;
import application.gui.workbench.audio.SoundCue;
import application.gui.workbench.audio.SoundService;
import application.gui.workbench.board.BoardEditorPanel;
import application.gui.workbench.board.BoardExportActions;
import application.gui.workbench.draw.DrawPanel;
import application.gui.workbench.engine.EngineGauntletPanel;
import application.gui.workbench.game.EcoExplorerPanel;
import application.gui.workbench.game.GameReviewPanel;
import application.gui.workbench.game.PlayMoveHistoryModel;
import application.gui.workbench.game.SanRenderer;
import application.gui.workbench.game.StudyAuthorPanel;
import application.gui.workbench.game.TablebasePanel;
import application.gui.workbench.layout.SplitPaneStyler;
import application.gui.workbench.session.LogPanel;
import application.gui.workbench.command.Console;
import application.gui.workbench.ui.FieldValidator;
import application.gui.workbench.ui.FileDialogs;
import application.gui.workbench.ui.WrappingFlowLayout;
import application.gui.workbench.ui.HoldButton;
import application.gui.workbench.ui.StatusBadge;
import application.gui.workbench.ui.SurfacePanel;
import application.gui.workbench.ui.SwitchedWorkspace;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.ToggleBox;
import application.gui.workbench.ui.Toast;
import application.gui.workbench.ui.Ui;
import application.gui.workbench.ui.WorkspaceHeader;
import application.gui.workbench.ui.WorkspaceMode;
import chess.images.assets.PieceSet;
import chess.core.Setup;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
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
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
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
     * Play / Relations / Draw all use the same rail proportion).
     */
    private static final Dimension SIDE_RAIL_SIZE = new Dimension(400, 560);

    /**
     * Vertical-only row padding shared by every settings-group row.
     */
    private static final Insets SETTINGS_ROW_INSETS = new Insets(3, 0, 3, 0);

    /**
     * Board / Analyze position validity badge.
     */
    private final StatusBadge analyzePositionBadge = new StatusBadge();

    /**
     * Board / Analyze position side value.
     */
    private final JLabel analyzeSideValue = metricValue();

    /**
     * Board / Analyze legal-move count value.
     */
    private final JLabel analyzeLegalValue = metricValue();

    /**
     * Board / Analyze material state value.
     */
    private final JLabel analyzeMaterialValue = metricValue();

    /**
     * Analysis result card switcher.
     */
    private final JPanel analysisResultCards = new JPanel(new CardLayout());

    /**
     * Latest analysis evaluation value.
     */
    private final JLabel analysisEvalValue = metricValue();

    /**
     * Latest analysis best-move value.
     */
    private final JLabel analysisBestMoveValue = metricValue();

    /**
     * Latest analysis depth value.
     */
    private final JLabel analysisDepthValue = metricValue();

    /**
     * Latest analysis node-count value.
     */
    private final JLabel analysisNodesValue = metricValue();

    /**
     * Latest analysis NPS value.
     */
    private final JLabel analysisNpsValue = metricValue();

    /**
     * Latest analysis sample-count value.
     */
    private final JLabel analysisSamplesValue = metricValue();

    /**
     * ECO explorer panel, created lazily with the board detail tabs.
     */
    protected EcoExplorerPanel ecoExplorerPanel;

    /**
     * Post-game review panel, created lazily with the board detail tabs.
     */
    protected GameReviewPanel gameReviewPanel;

    /**
     * Endgame tablebase panel, created lazily with the board detail tabs.
     */
    protected TablebasePanel tablebasePanel;

    /**
     * Study authoring panel, created lazily with the board detail tabs.
     */
    protected StudyAuthorPanel studyAuthorPanel;

    /**
     * Setup editor panel, created lazily with the board detail tabs.
     */
    protected BoardEditorPanel boardEditorPanel;

    /**
     * The shared board stage (board + material strips) re-parented into the
     * active Analyze/Play/Relations/Draw mode slot.
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
     * Board slot in the Draw mode card.
     */
    private transient JPanel drawBoardSlot;

    /**
     * True after the Relations overlay has populated shared board markups.
     */
    private transient boolean relationMarkupActive;

    /**
     * Relations control rail, retained so the shared board can be re-synced with
     * its tactical overlay when the Relations mode activates.
     */
    private transient application.gui.workbench.relations.RelationsPanel relationsControls;

    /**
     * Scroll pane for the Relations rail, reset when the mode activates so the
     * rail never opens mid-list after reused viewport state or automated capture
     * scroll events.
     */
    private transient JScrollPane relationsRailScroll;

    /**
     * Draw control rail, retained so the workspace header can reflect annotation
     * counts.
     */
    private transient DrawPanel drawControls;

    /**
     * Settings piece-set picker, retained so {@link #applyPieceSet} can keep its
     * selection in step when the set changes.
     */
    private transient application.gui.workbench.ui.ChipGroup pieceSetChips;

    /**
     * The collapsed-by-default "Raw Output / Log" section on the Run surface,
     * retained so a run can expand it when live process output starts streaming.
     */
    protected transient JComponent runRawOutputSection;

    /**
     * Creates the unified Board surface: one workspace hosting the Analyze,
     * Play, Solve, Relations, and Draw modes behind a switcher, replacing the four
     * former top-level tabs. Analyze is built eagerly (it wires the main board
     * and the nested analysis tabs at startup); the other modes build lazily on
     * first selection.
     *
     * @return board workspace component
     */
    protected JComponent createBoardWorkspaceTab() {
        // One shared board backs the Analyze, Play, Relations, and Draw modes: it is
        // re-parented into the active mode's slot and reconfigured for it by
        // configureBoardForMode, so a position carries across modes with no
        // duplicate widgets. Solve keeps its own board (puzzles step their own
        // positions, independent of the analysis line). The eval bar lives on
        // the shared board and paints inside its paint pass.
        evalBar.setToolTipText("Engine evaluation");
        board.setEvalBar(evalBar);
        sharedBoardStage = new application.gui.workbench.board.BoardStage(board);
        boardWorkspace = new SwitchedWorkspace("Board",
                List.of(
                        new WorkspaceMode("Analyze", this::createBoardTab, this::boardAnalyzeContext,
                                this::boardAnalyzeActions),
                        new WorkspaceMode("Play", this::createPlayTab, this::boardPlayContext),
                        new WorkspaceMode("Solve", this::createPuzzleTab, this::boardSolveContext),
                        new WorkspaceMode("Relations", this::createRelationsTab, this::boardRelationsContext),
                        new WorkspaceMode("Draw", this::createDrawTab, this::boardDrawContext)),
                BOARD_ANALYZE);
        boardWorkspace.setModeListener(this::configureBoardForMode);
        return boardWorkspace;
    }

    /**
     * Re-parents the single shared board into the activated board mode's slot
     * and reconfigures it for that mode. Analyze and Play are interactive
     * (turn-gated input and the analysis position), Relations is a read-only
     * tactical overlay, Draw routes pointer gestures to annotations, and Solve
     * keeps its own board, so this is a no-op for it.
     *
     * @param mode activated board mode (see BOARD_* constants)
     */
    private void configureBoardForMode(int mode) {
        JPanel slot = switch (mode) {
            case BOARD_ANALYZE -> analyzeBoardSlot;
            case BOARD_PLAY -> playBoardSlot;
            case BOARD_RELATIONS -> relationsBoardSlot;
            case BOARD_DRAW -> drawBoardSlot;
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
        board.setEvalBar(mode == BOARD_DRAW ? null : evalBar);
        if (mode == BOARD_RELATIONS) {
            // A visualization, not an editor: dragging is disabled and the
            // current position's relation channels are drawn onto the board.
            board.setDirectAnnotationMode(false);
            board.clearMarkup();
            relationMarkupActive = true;
            disableSharedBoardMoveInput();
            if (relationsControls != null) {
                relationsControls.activateBoardInteractions();
                relationsControls.syncToBoard();
            }
            scrollRelationsRailToTop();
        } else if (mode == BOARD_DRAW) {
            if (relationsControls != null) {
                relationsControls.deactivateBoardInteractions();
            }
            clearRelationMarkupIfActive();
            board.setDirectAnnotationMode(true);
            disableSharedBoardMoveInput();
            restoreSharedBoardPosition();
        } else {
            if (relationsControls != null) {
                relationsControls.deactivateBoardInteractions();
            }
            // Analyze / Play: interactive. Drop any relation overlay, re-arm
            // the move funnel and turn-gated input, and restore the analysis line.
            clearRelationMarkupIfActive();
            board.setDirectAnnotationMode(false);
            board.setMoveHandler(this::playMove);
            board.setDragStartFilter(context ->
                    playSession == null || playSession.isHumanInputAllowed());
            board.setPremoveStartFilter(context ->
                    playSession != null && playSession.isPremoveSourceAllowed(context.square(), context.piece()));
            board.setPremoveHandler(context ->
                    playSession != null && playSession.queuePremove(context.tentativeMove()));
            restoreSharedBoardPosition();
        }
    }

    /**
     * Clears temporary Relations overlays when leaving the Relations mode.
     */
    private void clearRelationMarkupIfActive() {
        if (!relationMarkupActive) {
            return;
        }
        board.clearMarkup();
        relationMarkupActive = false;
    }

    /**
     * Disables board move input for read-only or annotation-focused modes.
     */
    private void disableSharedBoardMoveInput() {
        board.setMoveHandler(null);
        board.setDragStartFilter(context -> false);
        board.setPremoveStartFilter(context -> false);
        board.setPremoveHandler(null);
        board.clearPremove();
    }

    /**
     * Restores the shared board to the current analysis position without move
     * animation.
     */
    private void restoreSharedBoardPosition() {
        if (currentPosition != null) {
            board.setPositionInstant(currentPosition, chess.core.Move.NO_MOVE);
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
        engineWorkspace = new SwitchedWorkspace("Engine Lab",
                List.of(
                        new WorkspaceMode("Evaluator", this::createNetworkTab, this::engineEvaluatorContext),
                        new WorkspaceMode("Search", this::createMctsTab, this::engineSearchContext),
                        new WorkspaceMode("Tree", this::createTreeTab, this::engineTreeContext),
                        new WorkspaceMode("Gauntlet", this::createGauntletTab, this::engineGauntletContext)),
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
     * Returns the Board / Analyze context line.
     *
     * @return context summary
     */
    private String boardAnalyzeContext() {
        if (currentPosition == null) {
            return "No position loaded";
        }
        return sideToMoveText() + " to move · " + session.legalMoveCount() + " legal moves · "
                + materialSummary() + " · " + evaluatorContext();
    }

    /**
     * Builds Board / Analyze header actions.
     *
     * @return action row
     */
    private JComponent boardAnalyzeActions() {
        JPanel row = transparentPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SPACE_SM, 0));
        JButton analyze = button("Analyze", true, event -> runAnalyze());
        analyze.setToolTipText("Run external-engine analysis for the current position");
        JButton search = button("Search", false, event -> runBuiltInSearch());
        search.setToolTipText("Run the deterministic built-in search on the current FEN");
        JButton bestMove = button("Best move", false, event -> runBestMove());
        bestMove.setToolTipText("Ask the configured UCI engine for one best move");
        row.add(analyze);
        row.add(search);
        row.add(bestMove);
        if (isForegroundCommandRunning()) {
            HoldButton stop = new HoldButton("Stop", this::stopCommand, true);
            stop.setToolTipText("Stop the running command");
            row.add(stop);
        }
        return row;
    }

    /**
     * Returns the Board / Play context line.
     *
     * @return context summary
     */
    private String boardPlayContext() {
        return playPanel == null ? "Play vs local engine · Ready" : playPanel.workspaceContext();
    }

    /**
     * Returns the Board / Solve context line.
     *
     * @return context summary
     */
    private String boardSolveContext() {
        return puzzlePanel == null ? "No puzzle loaded" : puzzlePanel.workspaceContext();
    }

    /**
     * Returns the Board / Relations context line.
     *
     * @return context summary
     */
    private String boardRelationsContext() {
        if (relationsControls != null) {
            return relationsControls.workspaceContext();
        }
        if (currentPosition == null) {
            return "No position loaded";
        }
        return "Current position · tactical incidence overlay";
    }

    /**
     * Returns the Board / Draw context line.
     *
     * @return context summary
     */
    private String boardDrawContext() {
        if (drawControls != null) {
            return drawControls.workspaceContext();
        }
        return currentPosition == null ? "No position loaded" : "Current board · annotation mode";
    }

    /**
     * Returns the Engine / Evaluator context line.
     *
     * @return context summary
     */
    private String engineEvaluatorContext() {
        return currentPosition == null
                ? "No position loaded · evaluator visualizers ready"
                : "Current FEN · NNUE / CNN / BT4 / OTIS evaluators";
    }

    /**
     * Returns the Engine / Search context line.
     *
     * @return context summary
     */
    private String engineSearchContext() {
        return currentPosition == null ? "No position loaded · PUCT/MCTS inspector"
                : "Current FEN · PUCT/MCTS inspector";
    }

    /**
     * Returns the Engine / Tree context line.
     *
     * @return context summary
     */
    private String engineTreeContext() {
        return "Live tree · follows the current search session";
    }

    /**
     * Returns the Engine / Gauntlet context line.
     *
     * @return context summary
     */
    private String engineGauntletContext() {
        return "Candidate vs baseline · configurable deterministic games";
    }

    /**
     * Returns the current side-to-move label.
     *
     * @return side label
     */
    private String sideToMoveText() {
        return currentPosition != null && currentPosition.isWhiteToMove() ? "White" : "Black";
    }

    /**
     * Returns a compact material summary.
     *
     * @return material summary
     */
    private String materialSummary() {
        if (currentPosition == null) {
            return "Material unknown";
        }
        int cp = currentPosition.materialDiscrepancy();
        if (cp == 0) {
            return "Material even";
        }
        return (cp > 0 ? "White +" + cp : "Black +" + Math.abs(cp)) + " cp";
    }

    /**
     * Returns a compact evaluator / live-engine phrase.
     *
     * @return evaluator context
     */
    private String evaluatorContext() {
        String summary = session.engineSummary();
        if (summary != null && summary.toLowerCase(java.util.Locale.ROOT).contains("failed")) {
            return "Engine error";
        }
        if (session.liveEngine() && summary != null && !summary.equalsIgnoreCase("paused")) {
            return "Engine live";
        }
        return "Evaluator ready";
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

        JComponent side = createAnalyzeInspector();
        side.setPreferredSize(SIDE_RAIL_SIZE);

        JSplitPane boardPage = SplitPaneStyler.styledHorizontalSplit(analyzeBoardSlot, side, 0.70);

        analysisCards = transparentPanel(new CardLayout());
        analysisCards.add(boardPage, ANALYZE_CARD_BOARD);
        analysisCards.add(createGameSection(), ANALYZE_CARD_GAME);
        return analysisCards;
    }

    /**
     * Creates the Board / Analyze right inspector.
     *
     * @return inspector component
     */
    private JComponent createAnalyzeInspector() {
        JPanel stack = transparentPanel(null);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.add(createPositionControls());
        stack.add(Box.createVerticalStrut(Theme.SPACE_MD));
        stack.add(createAnalysisControls());
        stack.add(Box.createVerticalStrut(Theme.SPACE_MD));
        stack.add(createAnalysisResultCard());
        stack.add(Box.createVerticalStrut(Theme.SPACE_MD));
        stack.add(createMovesAndTags());
        stack.add(Box.createVerticalGlue());

        JScrollPane scrollPane = scroll(fillViewport(stack), () -> Theme.PANEL_SOLID);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        return scrollPane;
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
     * Creates position setup and board-navigation controls.
     *
     * @return controls
     */
    protected JComponent createPositionControls() {
        JPanel body = verticalBody();
        styleFields(fenField);
        fenField.setFont(Theme.mono(Theme.FONT_MONO));
        fenField.setToolTipText("Paste a FEN and press Enter to load");
        fenField.addActionListener(event -> setPositionFromField());
        fenField.setColumns(34);

        JPanel fenHeader = transparentPanel(new BorderLayout(Theme.SPACE_SM, 0));
        JLabel fenLabel = new JLabel("FEN");
        fenLabel.setFont(Theme.font(Theme.FONT_METADATA, Font.BOLD));
        Theme.foreground(fenLabel, Theme.ForegroundRole.MUTED);
        fenHeader.add(fenLabel, BorderLayout.WEST);
        fenHeader.add(analyzePositionBadge, BorderLayout.EAST);
        body.add(fenHeader);
        body.add(Box.createVerticalStrut(Theme.SPACE_XS));
        body.add(fenField);
        body.add(Box.createVerticalStrut(Theme.SPACE_SM));

        body.add(metricGrid(
                metric("Side", analyzeSideValue),
                metric("Legal moves", analyzeLegalValue),
                metric("Material", analyzeMaterialValue)));
        body.add(Box.createVerticalStrut(Theme.SPACE_SM));

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
        transportRow.add(button("Edit", false, event -> fenField.requestFocusInWindow()));
        transportRow.add(button("Reset", false, event -> startNewGame(Setup.getStandardStartFEN())));
        body.add(transportRow);
        body.add(Box.createVerticalStrut(Theme.SPACE_SM));

        body.add(buttonRow(FlowLayout.LEFT,
                button("Copy", false, event -> copyText(fenField.getText())),
                button("Flip", false, event -> {
                    board.setWhiteDown(!board.isWhiteDown());
                    appendConsole("Board flipped\n");
                }),
                button("Open PGN", false, event -> showPgnExplorer()),
                createExportBoardButton()));
        body.add(Box.createVerticalStrut(Theme.SPACE_SM));
        body.add(createAnalyzeFeatureShortcuts());
        refreshAnalyzeInspector();
        return Ui.card("Position", body);
    }

    /**
     * Creates compact shortcuts for the analysis features that live in the
     * side-rail detail tabs. Keeping them in the Position card avoids a separate
     * rail card on small windows.
     *
     * @return shortcut row
     */
    private JComponent createAnalyzeFeatureShortcuts() {
        JPanel row = transparentPanel(new WrappingFlowLayout(FlowLayout.LEFT, Theme.SPACE_SM, 0));
        JButton opening = button("Opening Tree", false, event -> showBoardDetail("ECO"));
        opening.setToolTipText("Show ECO opening tree and candidate line explorer");
        JButton review = button("Review", false, event -> showBoardDetail("Review"));
        review.setToolTipText("Show post-game review and retry critical moves");
        JButton endgame = button("Endgame", false, event -> showBoardDetail("Endgame"));
        endgame.setToolTipText("Show tablebase-eligibility and fixed-node endgame analysis");
        JButton study = button("Study", false, event -> showBoardDetail("Study"));
        study.setToolTipText("Show study/repertoire TOML authoring for the current game line");
        JButton gauntlet = button("Gauntlet", false, event -> openEngine(ENGINE_GAUNTLET));
        gauntlet.setToolTipText("Open deterministic built-in engine self-play gauntlets");
        row.add(opening);
        row.add(review);
        row.add(endgame);
        row.add(study);
        row.add(gauntlet);
        return row;
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
     * Creates a transparent vertical card body.
     *
     * @return vertical body
     */
    private static JPanel verticalBody() {
        JPanel body = transparentPanel(null);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        return body;
    }

    /**
     * Creates a value label for compact metrics.
     *
     * @return metric value label
     */
    private static JLabel metricValue() {
        JLabel label = new JLabel("-");
        label.setFont(Theme.font(13, Font.BOLD));
        Theme.foreground(label, Theme.ForegroundRole.TEXT);
        return label;
    }

    /**
     * Builds a responsive metric grid.
     *
     * @param metrics metric cells
     * @return metric grid
     */
    private static JComponent metricGrid(JComponent... metrics) {
        JPanel gridPanel = transparentPanel(new GridBagLayout());
        gridPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (int i = 0; i < metrics.length; i++) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = i % 3;
            c.gridy = i / 3;
            c.weightx = 1.0;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(0, 0, Theme.SPACE_SM, i % 3 == 2 ? 0 : Theme.SPACE_SM);
            gridPanel.add(metrics[i], c);
        }
        return gridPanel;
    }

    /**
     * Builds one metric cell.
     *
     * @param label metric label
     * @param value value label
     * @return metric component
     */
    private static JComponent metric(String label, JLabel value) {
        JPanel panel = transparentPanel(null);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(Theme.pad(Theme.SPACE_XS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel title = new JLabel(label);
        title.setFont(Theme.font(Theme.FONT_METADATA, Font.PLAIN));
        Theme.foreground(title, Theme.ForegroundRole.MUTED);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        value.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(2));
        panel.add(value);
        return panel;
    }

    /**
     * Creates board controls.
     *
     * @return controls
     */
    protected JComponent createAnalysisControls() {
        JPanel panel = verticalBody();
        GridBagConstraints c = constraints();
        liveEngineToggle.setSelected(liveExternalEngineEnabled);
        liveEngineToggle.addActionListener(event -> setLiveExternalEngineEnabled(liveEngineToggle.isSelected()));
        multipvModel.addChangeListener(event -> requestLiveAnalysisUpdate());
        threadsModel.addChangeListener(event -> requestLiveAnalysisUpdate());

        Theme.foreground(statusLabel, Theme.ForegroundRole.MUTED);
        statusLabel.setFont(Theme.font(12, Font.PLAIN));

        styleSpinners(analysisDepthSpinner, analysisMultipvSpinner, analysisThreadsSpinner);
        styleFields(analysisDurationField);
        analysisDurationField.getDocument()
                .addDocumentListener(changeListener(this::syncDurationFromAnalysis));
        analysisDepthSpinner.setToolTipText("Maximum search depth for built-in search and command previews");
        analysisDurationField.setToolTipText("Maximum external-engine time, e.g. 1s or 500ms");
        FieldValidator.attach(analysisDurationField,
                FieldValidator.numberWithOptionalUnit(true, "ms", "s", "m", "h"));
        analysisMultipvSpinner.setToolTipText("Number of principal variations for external analysis");
        analysisThreadsSpinner.setToolTipText("Thread count for supported commands");

        JPanel settings = transparentPanel(new GridBagLayout());
        c.insets = SETTINGS_ROW_INSETS;
        grid(settings, createLabelDetailRow("Depth", "Built-in search limit", analysisDepthSpinner),
                c, 0, 0, 1, 1);
        grid(settings, createLabelDetailRow("Time", "External-engine budget", analysisDurationField),
                c, 0, 1, 1, 1);
        grid(settings, createLabelDetailRow("Live", "Continuously analyze visible FEN", liveEngineToggle),
                c, 0, 2, 1, 1);

        panel.add(settings);
        panel.add(Box.createVerticalStrut(Theme.SPACE_SM));
        panel.add(collapsible("Advanced / raw command settings", createAdvancedAnalysisControls(), false));
        refreshAnalysisCommandState();
        return Ui.card("Engine / Search Settings", panel);
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
     * Creates the analysis-result card.
     *
     * @return result card
     */
    private JComponent createAnalysisResultCard() {
        analysisResultCards.setOpaque(false);

        JComponent empty = Ui.emptyState("No analysis yet",
                "Run Analyze or Search to see evaluation history, depth, and candidate moves.",
                button("Analyze", true, event -> runAnalyze()));

        JPanel populated = verticalBody();
        populated.add(metricGrid(
                metric("Eval", analysisEvalValue),
                metric("Best move", analysisBestMoveValue),
                metric("Depth", analysisDepthValue),
                metric("Nodes", analysisNodesValue),
                metric("NPS", analysisNpsValue),
                metric("Samples", analysisSamplesValue)));
        populated.add(Box.createVerticalStrut(Theme.SPACE_SM));
        populated.add(analysisGraph);
        populated.add(Box.createVerticalStrut(Theme.SPACE_SM));
        populated.add(controlRow(FlowLayout.LEFT,
                button("Copy CSV", false, event -> copyAnalysisCsv()),
                button("Copy Report", false, event -> copyAnalysisReport()),
                button("Print", false, event -> printAnalysisReport()),
                new HoldButton("Clear", this::clearAnalysisData, true)));

        analysisResultCards.add(empty, "empty");
        analysisResultCards.add(populated, "populated");
        analysisGraph.addPropertyChangeListener("analysisSamples", event -> refreshAnalysisResult());
        refreshAnalysisResult();
        return Ui.card("Analysis Result", analysisResultCards);
    }

    /**
     * Refreshes Board / Analyze position and analysis summary widgets.
     */
    protected void refreshAnalyzeInspector() {
        if (currentPosition == null) {
            analyzePositionBadge.notRun("no position");
            analyzeSideValue.setText("-");
            analyzeLegalValue.setText("-");
            analyzeMaterialValue.setText("Unknown");
        } else {
            analyzePositionBadge.ready("valid");
            analyzePositionBadge.setToolTipText(null);
            analyzeSideValue.setText(sideToMoveText());
            analyzeLegalValue.setText(Integer.toString(visibleMoves.length));
            analyzeMaterialValue.setText(materialSummary().replace("Material ", ""));
        }
        refreshAnalysisResult();
    }

    /**
     * Marks the position summary invalid after a failed FEN edit.
     *
     * @param message validation message
     */
    protected void markAnalyzePositionInvalid(String message) {
        analyzePositionBadge.error("invalid");
        analyzePositionBadge.setToolTipText(message == null || message.isBlank()
                ? "Invalid FEN" : message);
    }

    /**
     * Refreshes command-dependent Analyze controls.
     */
    protected void refreshAnalysisCommandState() {
        if (boardWorkspace != null && boardWorkspace.mode() == BOARD_ANALYZE) {
            boardWorkspace.refreshHeader();
        }
    }

    /**
     * Refreshes the analysis result card from graph data.
     */
    private void refreshAnalysisResult() {
        if (analysisResultCards == null) {
            return;
        }
        boolean hasSamples = analysisGraph.hasSamples();
        CardLayout layout = (CardLayout) analysisResultCards.getLayout();
        layout.show(analysisResultCards, hasSamples ? "populated" : "empty");
        application.gui.workbench.ui.AnalysisGraph.LatestSummary summary =
                analysisGraph.latestSummaryValues();
        analysisEvalValue.setText(summary.eval());
        analysisBestMoveValue.setText(summary.bestMove());
        analysisDepthValue.setText(summary.depth());
        analysisNodesValue.setText(summary.nodes());
        analysisNpsValue.setText(summary.nps());
        analysisSamplesValue.setText(summary.samples());
    }

    /**
     * Returns whether a foreground command is currently running.
     *
     * @return true when a command is running
     */
    private boolean isForegroundCommandRunning() {
        return runningCommand != null && runningCommand.isRunning();
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

        boardDetailTabs = createSectionTabs();
        boardDetailTabs.addTab("Moves", scroll(movesTable));
        boardDetailTabs.addTab("Tags", scroll(tagCloud));
        boardDetailTabs.addTab("ECO", createEcoExplorerPanel());
        boardDetailTabs.addTab("Review", createGameReviewPanel());
        boardDetailTabs.addTab("Study", createStudyAuthorPanel());
        boardDetailTabs.addTab("Endgame", createTablebasePanel());
        boardDetailTabs.addTab("Raw", createAnalysisDataPanel());
        boardDetailTabs.addTab("Editor", createBoardEditorPanel());
        boardDetailTabs.addChangeListener(event -> syncBoardEditorMode());
        int movesTab = boardDetailTabs.indexOfTab("Moves");
        if (movesTab >= 0) {
            boardDetailTabs.setSelectedIndex(movesTab);
        }
        syncBoardEditorMode();
        return Ui.card("Candidate Moves / Expert Tabs", boardDetailTabs);
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
     * Creates the deterministic post-game review panel.
     *
     * @return review component
     */
    protected JComponent createGameReviewPanel() {
        gameReviewPanel = new GameReviewPanel(() -> gameModel, this::jumpGameTo, this::copyText);
        return scroll(fillViewport(gameReviewPanel));
    }

    /**
     * Creates the endgame tablebase panel.
     *
     * @return tablebase component
     */
    protected JComponent createTablebasePanel() {
        tablebasePanel = new TablebasePanel(this::currentFen, this::copyText);
        return scroll(fillViewport(tablebasePanel));
    }

    /**
     * Creates the study/repertoire authoring panel.
     *
     * @return study authoring component
     */
    protected JComponent createStudyAuthorPanel() {
        studyAuthorPanel = new StudyAuthorPanel(() -> gameModel, this::currentFen, this::copyText);
        return scroll(fillViewport(studyAuthorPanel));
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
     * Refreshes the endgame tablebase panel when the visible game position
     * changes.
     */
    protected void refreshTablebasePanel() {
        if (tablebasePanel != null) {
            tablebasePanel.refreshPosition();
        }
    }

    /**
     * Refreshes the generated study manifest when the visible game line changes.
     */
    protected void refreshStudyAuthorPanel() {
        if (studyAuthorPanel != null) {
            studyAuthorPanel.refreshManifest();
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
        JPanel content = verticalBody();
        JComponent actions = controlRow(FlowLayout.LEFT,
                button("Copy CSV", false, event -> copyAnalysisCsv()),
                button("Copy Report", false, event -> copyAnalysisReport()),
                button("Print", false, event -> printAnalysisReport()),
                new HoldButton("Clear", this::clearAnalysisData, true));
        content.add(actions);
        return content;
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
        engineNodesField.setToolTipText("Optional node limit per move");
        engineHashField.setToolTipText("Optional engine hash size in MB");
        // Both are optional positive integers (blank leaves the limit unset).
        FieldValidator.attach(engineNodesField, FieldValidator.wholeNumber(1, Long.MAX_VALUE, true));
        FieldValidator.attach(engineHashField, FieldValidator.wholeNumber(1, Long.MAX_VALUE, true));

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
        JComponent studyTools = buttonRow(FlowLayout.LEFT,
                button("Opening Tree", false, event -> showBoardDetail("ECO")),
                button("Review Game", false, event -> showBoardDetail("Review")),
                button("Author Study", false, event -> showBoardDetail("Study")),
                button("PGN Database", false, event -> showPgnExplorer()));

        grid(panel, collapsible("Import", importTools, true), c, 0, 1, 4, 1);
        grid(panel, collapsible("Study / Review / Database", studyTools, true), c, 0, 2, 4, 1);
        grid(panel, collapsible("Exports", exportTools, false), c, 0, 3, 4, 1);
        addVerticalFiller(panel, c, 4, 4);
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
        runCommandButton = button("Run", true, event -> runSelectedTemplate());
        JPanel panel = transparentPanel(new BorderLayout(0, 0));
        runHeader = new WorkspaceHeader("Run", "", createCommandActions());
        panel.add(runHeader, BorderLayout.NORTH);
        panel.add(createCommandBuilder(), BorderLayout.CENTER);
        refreshRunHeader();
        return panel;
    }

    /**
     * Creates command builder.
     *
     * @return panel
     */
    protected JPanel createCommandBuilder() {
        JPanel panel = transparentPanel(new BorderLayout(0, 0));
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
        configureRunOutputPanes();

        JSplitPane split = SplitPaneStyler.styledHorizontalSplit(
                createRunSettingsColumn(), createRunOutputColumn(), 0.47);
        split.setMinimumSize(new Dimension(880, 560));
        panel.add(split, BorderLayout.CENTER);

        updateCommandOptions();
        updateBuiltCommand();
        setCommandState("Idle");
        return panel;
    }

    /**
     * Creates the command-settings column.
     *
     * @return scrollable settings column
     */
    private JComponent createRunSettingsColumn() {
        JPanel column = verticalBody();
        column.setBorder(Theme.pad(Theme.SPACE_MD));
        column.add(createRunCommandSelectorCard());
        column.add(Box.createVerticalStrut(Theme.SPACE_MD));
        commandForm.setAlignmentX(LEFT_ALIGNMENT);
        column.add(Ui.card("Command Settings", commandForm));
        column.add(Box.createVerticalGlue());
        JScrollPane pane = scroll(fillViewport(column));
        pane.setMinimumSize(new Dimension(380, 520));
        pane.setPreferredSize(new Dimension(470, 640));
        return pane;
    }

    /**
     * Creates the command selector card.
     *
     * @return selector card
     */
    private JComponent createRunCommandSelectorCard() {
        JPanel body = verticalBody();
        commandPicker.setAlignmentX(LEFT_ALIGNMENT);
        commandPathLabel.setAlignmentX(LEFT_ALIGNMENT);
        commandDescriptionLabel.setAlignmentX(LEFT_ALIGNMENT);
        commandPathLabel.setFont(Theme.mono(Theme.FONT_MONO));
        commandDescriptionLabel.setFont(Theme.font(Theme.FONT_CONTROL, Font.PLAIN));
        commandDescriptionLabel.putClientProperty(Theme.CLIENT_TRANSPARENT_FIELD, Boolean.TRUE);
        commandDescriptionLabel.setOpaque(false);
        commandDescriptionLabel.setEditable(false);
        commandDescriptionLabel.setLineWrap(true);
        commandDescriptionLabel.setWrapStyleWord(true);
        commandDescriptionLabel.setBorder(BorderFactory.createEmptyBorder());
        Theme.foreground(commandPathLabel, Theme.ForegroundRole.MUTED);
        Theme.foreground(commandDescriptionLabel, Theme.ForegroundRole.TEXT);
        body.add(commandPicker);
        body.add(Box.createVerticalStrut(Theme.SPACE_SM));
        body.add(commandPathLabel);
        body.add(Box.createVerticalStrut(Theme.SPACE_XS));
        body.add(commandDescriptionLabel);
        return Ui.card("Command", body);
    }

    /**
     * Creates the preview/output column.
     *
     * @return scrollable output column
     */
    private JComponent createRunOutputColumn() {
        JPanel column = verticalBody();
        column.setBorder(Theme.pad(Theme.SPACE_MD));
        column.add(createRunPreviewCard());
        column.add(Box.createVerticalStrut(Theme.SPACE_MD));
        column.add(createRunValidationCard());
        column.add(Box.createVerticalStrut(Theme.SPACE_MD));
        column.add(createRunParsedResultCard());
        column.add(Box.createVerticalStrut(Theme.SPACE_MD));
        column.add(createRunRawOutputCard());
        column.add(Box.createVerticalStrut(Theme.SPACE_MD));
        column.add(createRecentCommandsCard());
        column.add(Box.createVerticalGlue());
        JScrollPane pane = scroll(fillViewport(column));
        pane.setMinimumSize(new Dimension(420, 520));
        pane.setPreferredSize(new Dimension(560, 640));
        return pane;
    }

    /**
     * Creates the generated command preview card.
     *
     * @return preview card
     */
    private JComponent createRunPreviewCard() {
        JPanel body = verticalBody();
        commandPreviewScroll = scroll(commandField, () -> Theme.CODE_BLOCK_BG);
        commandPreviewScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        commandPreviewScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        commandPreviewScroll.setPreferredSize(new Dimension(520, 132));
        commandPreviewScroll.setMinimumSize(new Dimension(320, 110));
        body.add(commandPreviewScroll);
        body.add(Box.createVerticalStrut(Theme.SPACE_SM));

        JCheckBox wrapToggle = new ToggleBox("Wrap", true);
        wrapToggle.setSelected(true);
        wrapToggle.setToolTipText("Wrap long generated commands inside the preview");
        wrapToggle.addActionListener(event -> setCommandPreviewWrap(wrapToggle.isSelected()));

        JPanel actions = transparentPanel(new WrappingFlowLayout(FlowLayout.LEFT, Theme.SPACE_SM, 0));
        actions.add(button("Copy Command", false, event -> copyBuiltCommand()));
        actions.add(button("Open Console", false, event -> showConsoleDock()));
        actions.add(wrapToggle);
        body.add(actions);
        return Ui.card("Command Preview", body);
    }

    /**
     * Creates the validation badge card.
     *
     * @return validation card
     */
    private JComponent createRunValidationCard() {
        JPanel body = verticalBody();
        body.add(validationRow("Position source", runFenBadge));
        body.add(Box.createVerticalStrut(Theme.SPACE_XS));
        body.add(validationRow("Engine config", runProtocolBadge));
        body.add(Box.createVerticalStrut(Theme.SPACE_XS));
        body.add(validationRow("Search duration", runDurationBadge));
        return Ui.card("Validation", body);
    }

    /**
     * Creates one validation row.
     *
     * @param title row title
     * @param badge status badge
     * @return row
     */
    private static JComponent validationRow(String title, StatusBadge badge) {
        JPanel row = transparentPanel(new BorderLayout(Theme.SPACE_SM, 0));
        JLabel label = new JLabel(title);
        label.setFont(Theme.font(Theme.FONT_CONTROL, Font.PLAIN));
        Theme.foreground(label, Theme.ForegroundRole.TEXT);
        row.add(label, BorderLayout.WEST);
        row.add(badge, BorderLayout.EAST);
        row.setBorder(Theme.pad(Theme.SPACE_XS, 0, Theme.SPACE_XS, 0));
        row.setAlignmentX(LEFT_ALIGNMENT);
        return row;
    }

    /**
     * Creates the parsed output card.
     *
     * @return parsed-result card
     */
    private JComponent createRunParsedResultCard() {
        JScrollPane pane = scroll(runParsedOutput, () -> Theme.TEXT_AREA);
        pane.setPreferredSize(new Dimension(520, 150));
        pane.setMinimumSize(new Dimension(320, 120));
        return Ui.card("Parsed Result", runStateBadge, pane);
    }

    /**
     * Creates the raw command-output card.
     *
     * @return raw-output card
     */
    private JComponent createRunRawOutputCard() {
        JScrollPane pane = scroll(runRawOutput, () -> Theme.TERMINAL);
        pane.setPreferredSize(new Dimension(520, 220));
        pane.setMinimumSize(new Dimension(320, 160));
        // Raw log is secondary to the parsed result: collapse it by default so it
        // no longer dominates the column with a large empty terminal box. A run
        // re-expands it (see prepareRunCommandOutput) so live output stays visible.
        runRawOutputSection = collapsible("Raw Output / Log", pane, false);
        return runRawOutputSection;
    }

    /**
     * Creates the recent-command history card.
     *
     * @return recent command card
     */
    private JComponent createRecentCommandsCard() {
        JPanel body = verticalBody();
        Theme.list(recentCommandList);
        recentCommandList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        recentCommandList.setVisibleRowCount(5);
        JScrollPane listScroll = scroll(recentCommandList, () -> Theme.ELEVATED_SOLID);
        listScroll.setPreferredSize(new Dimension(520, 112));
        body.add(listScroll);
        body.add(Box.createVerticalStrut(Theme.SPACE_SM));
        JButton copySelected = button("Copy Selected", false, event -> copySelectedRecentCommand());
        body.add(buttonRow(FlowLayout.LEFT, copySelected));
        // History is a convenience; keep it collapsed until the user wants it so
        // an empty list does not read as a dead panel.
        return collapsible("Recent Commands", body, false);
    }

    /**
     * Configures the parsed/raw output panes.
     */
    private void configureRunOutputPanes() {
        styleAreas(runParsedOutput);
        runParsedOutput.setEditable(false);
        runParsedOutput.setLineWrap(true);
        runParsedOutput.setWrapStyleWord(true);
        runParsedOutput.setRows(7);
        runParsedOutput.setText("No command run yet.\nRun a command to see parsed fields here.");
        runParsedOutput.setCaretPosition(0);
        runRawOutput.setPlaceholder("Raw command output appears here after running.");
        runStateBadge.notRun("idle");
    }

    /**
     * Copies the selected recent command to the clipboard.
     */
    private void copySelectedRecentCommand() {
        String selected = recentCommandList.getSelectedValue();
        if (selected == null || selected.isBlank()) {
            showWarning("Recent commands", "Select a command to copy.");
            return;
        }
        copyText(selected);
    }

    /**
     * Toggles command preview wrapping.
     *
     * @param wrap true to wrap
     */
    private void setCommandPreviewWrap(boolean wrap) {
        commandField.setLineWrap(wrap);
        commandField.setWrapStyleWord(false);
        if (commandPreviewScroll != null) {
            commandPreviewScroll.setHorizontalScrollBarPolicy(wrap
                    ? JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
                    : JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        }
        commandField.revalidate();
    }

    /**
     * Creates the Run header actions.
     *
     * @return action row
     */
    private JComponent createCommandActions() {
        if (runCommandButton == null) {
            runCommandButton = button("Run", true, event -> runSelectedTemplate());
        }
        runStopButton = new HoldButton("Stop", this::stopCommand, true);
        runStopButton.setVisible(false);
        return controlRow(FlowLayout.RIGHT,
                runCommandButton,
                button("Copy Command", false, event -> copyBuiltCommand()),
                button("Reset", false, event -> resetSelectedTemplate()),
                button("Clear Flags", false, event -> clearOptionalTemplateOptions()),
                runStopButton);
    }

    /**
     * Styles the generated command field so it reads as command output rather
     * than another input row: monospace text, no editable border, muted
     * foreground, transparent background that blends with the wrapper panel.
     *
     * @param field generated-command field
     */
    private static void styleCommandPreviewField(javax.swing.JTextArea field) {
        Theme.codeBlock(field);
        field.setEditable(false);
        field.setLineWrap(true);
        field.setWrapStyleWord(false);
        field.setRows(6);
        field.setColumns(72);
    }

    /**
     * Enables or disables the Run button from the command form's validity.
     *
     * @param ready true when the built command is runnable
     */
    protected void updateCommandRunGate(boolean ready) {
        commandFormRunnable = ready;
        boolean running = runningCommand != null && runningCommand.isRunning();
        if (runCommandButton != null) {
            runCommandButton.setEnabled(ready && !running);
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
     * Creates the engine self-play gauntlet tab.
     *
     * @return gauntlet tab
     */
    protected JComponent createGauntletTab() {
        return new EngineGauntletPanel(this::copyText);
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
        application.gui.workbench.play.PlayPanel controls = playPanel();
        playBoardSlot = transparentPanel(new BorderLayout(0, Theme.SPACE_SM));
        playBoardSlot.add(controls.opponentIdentityStrip(), BorderLayout.NORTH);
        playBoardSlot.add(controls.playerIdentityStrip(), BorderLayout.SOUTH);

        // The setup form scrolls on its own (it is tall once Custom is open) and
        // the move list keeps a stable lower slot. A fixed column avoids the
        // visual collision that came from nesting a vertical split inside the
        // already-split Play workspace.
        JComponent setup = scroll(fillViewport(controls));
        JComponent moves = createPlayMoveHistory();
        moves.setPreferredSize(new Dimension(SIDE_RAIL_SIZE.width, 300));
        moves.setMinimumSize(new Dimension(0, 170));
        JPanel rail = transparentPanel(new BorderLayout(0, Theme.SPACE_MD));
        rail.add(setup, BorderLayout.CENTER);
        rail.add(moves, BorderLayout.SOUTH);
        rail.setPreferredSize(SIDE_RAIL_SIZE);

        JSplitPane playPage = SplitPaneStyler.styledHorizontalSplit(playBoardSlot, rail, 0.68);
        return playPage;
    }

    /**
     * Creates the compact move-pair table used by Play mode.
     *
     * @return move-history component
     */
    private JComponent createPlayMoveHistory() {
        PlayMoveHistoryModel historyModel = new PlayMoveHistoryModel(gameModel);
        javax.swing.JTable playMoves = new javax.swing.JTable(historyModel) {
            /**
             * Serialization identifier for Swing table compatibility.
             */
            private static final long serialVersionUID = 1L;

            /**
             * Paints a shared empty state into the viewport when no moves exist.
             *
             * @param graphics graphics context
             */
            @Override
            protected void paintComponent(java.awt.Graphics graphics) {
                super.paintComponent(graphics);
                if (getRowCount() == 0 && graphics instanceof java.awt.Graphics2D graphics2D) {
                    Ui.paintEmptyState(graphics2D, new java.awt.Rectangle(0, 0, getWidth(), getHeight()),
                            "No moves yet", "Start a game to record moves here.");
                }
            }
        };
        Theme.table(playMoves, Theme.TABLE_ROW_HEIGHT);
        playMoves.setAutoCreateColumnsFromModel(false);
        playMoves.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_LAST_COLUMN);
        playMoves.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        playMoves.setFillsViewportHeight(true);
        playMoves.setPreferredScrollableViewportSize(new Dimension(320, 220));
        javax.swing.table.TableColumnModel columns = playMoves.getColumnModel();
        columns.getColumn(0).setMaxWidth(42);
        columns.getColumn(0).setPreferredWidth(42);
        columns.getColumn(1).setPreferredWidth(130);
        columns.getColumn(2).setPreferredWidth(130);
        columns.getColumn(1).setCellRenderer(new SanRenderer());
        columns.getColumn(2).setCellRenderer(new SanRenderer());
        playMoves.addMouseListener(new MouseAdapter() {
            /**
             * Navigates to the clicked move ply when a recorded move cell is selected.
             *
             * @param event mouse event
             */
            @Override
            public void mouseClicked(MouseEvent event) {
                int row = playMoves.rowAtPoint(event.getPoint());
                int viewColumn = playMoves.columnAtPoint(event.getPoint());
                if (row < 0 || viewColumn < 0) {
                    return;
                }
                int column = playMoves.convertColumnIndexToModel(viewColumn);
                int ply = row * 2 + (column == 2 ? 2 : 1);
                if (ply > 0 && ply <= gameModel.lastPly()) {
                    jumpGameTo(ply);
                }
            }
        });
        historyModel.addTableModelListener(event -> javax.swing.SwingUtilities.invokeLater(() -> {
            int lastRow = playMoves.getRowCount() - 1;
            if (lastRow >= 0) {
                playMoves.getSelectionModel().setSelectionInterval(lastRow, lastRow);
                playMoves.scrollRectToVisible(playMoves.getCellRect(lastRow, 0, true));
            }
        }));
        JComponent section = Ui.card("Move History", scroll(playMoves));
        section.setOpaque(true);
        section.setBackground(Theme.BG);
        return section;
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
                new application.gui.workbench.relations.RelationsPanel(board, () -> currentPosition,
                        this::refreshWorkspaceHeaders);
        relationsRailScroll = scroll(fillViewport(relationsControls));
        relationsRailScroll.setPreferredSize(SIDE_RAIL_SIZE);
        scrollRelationsRailToTop();
        return SplitPaneStyler.styledHorizontalSplit(relationsBoardSlot, relationsRailScroll, 0.68);
    }

    /**
     * Returns the Relations control rail to its first section after layout
     * settles.
     */
    private void scrollRelationsRailToTop() {
        if (relationsRailScroll == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (relationsRailScroll != null) {
                relationsRailScroll.getVerticalScrollBar().setValue(0);
            }
        });
    }

    /**
     * Creates the board drawing tab: the shared board on the left and annotation
     * tool/color/export controls on the right.
     *
     * @return draw tab
     */
    protected JComponent createDrawTab() {
        drawBoardSlot = boardSlotPanel();
        drawControls = new DrawPanel(board, this, this::refreshWorkspaceHeaders);
        JComponent rail = scroll(fillViewport(drawControls));
        rail.setPreferredSize(SIDE_RAIL_SIZE);
        return SplitPaneStyler.styledHorizontalSplit(drawBoardSlot, rail, 0.68);
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
        JPanel panel = new SurfacePanel(new BorderLayout(6, 6));
        WorkspaceHeader header = new WorkspaceHeader("Console",
                "Command output · " + commandStateLabel.getText(),
                controlRow(FlowLayout.RIGHT,
                button("Open Logs", false, event -> showLogsDock()),
                button("Save Log", false, event -> saveConsoleLog(target)),
                button("Empty Log", false, event -> target.clearOutput()),
                createCommandStopButton()));
        consoleHeaders.add(header);
        panel.add(header, BorderLayout.NORTH);
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

}
