package application.gui.workbench.window;

import application.gui.workbench.audio.SoundCue;
import application.gui.workbench.audio.SoundService;
import application.gui.workbench.board.BoardEditorPanel;
import application.gui.workbench.board.BoardExportActions;
import application.gui.workbench.draw.DrawPanel;
import application.gui.workbench.engine.EngineGauntletPanel;
import application.gui.workbench.game.EcoExplorerPanel;
import application.gui.workbench.game.GameReviewPanel;
import application.gui.workbench.game.MoveListPanel;
import application.gui.workbench.game.ReviewCliArtifactProducer;
import application.gui.workbench.game.SavedGame;
import application.gui.workbench.game.SanRenderer;
import application.gui.workbench.game.StudyAuthorPanel;
import application.gui.workbench.study.StudyRepository;
import application.gui.workbench.study.StudyWorkspacePanel;
import application.gui.workbench.game.TablebasePanel;
import application.gui.workbench.library.GameLibrary;
import application.gui.workbench.library.GameLibraryPanel;
import application.gui.workbench.layout.BoardInspectorRail;
import application.gui.workbench.layout.SplitPaneStyler;
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
import application.gui.workbench.ui.WorkspaceMode;
import chess.images.assets.PieceSet;
import chess.core.Position;
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
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.accessibility.AccessibleContext;
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
     * Preferred width of the Analyze variation tree rail.
     */
    private static final int VARIATION_TREE_WIDTH = 236;

    /**
     * Resize weight and initial divider for the board-or-content (left) versus
     * inspector-rail (right) split shared by every board-centric page (Analyze,
     * Play, Relations, Draw, and the Analyze game review).
     */
    private static final double BOARD_PAGE_WEIGHT = 0.68d;

    /**
     * Vertical-only row padding shared by every settings-group row.
     */
    private static final Insets SETTINGS_ROW_INSETS = new Insets(3, 0, 3, 0);

    /**
     * Read-only FEN preview for the selected game/variation position.
     */
    private final JTextArea gameFenPreview = Ui.commandBlock("");

    /**
     * Read-only PGN preview for the current game line.
     */
    private final JTextArea gamePgnPreview = Ui.commandBlock("");

    /**
     * Always-available FEN line in the board-page PGN/FEN peek (south of the
     * Board workspace, shared by every board mode).
     */
    private final JTextArea boardFenPeek = Ui.commandBlock("");

    /**
     * Always-available PGN body in the board-page PGN/FEN peek.
     */
    private final JTextArea boardPgnPeek = Ui.commandBlock("");

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
     * Latest analysis best-move value (drives the on-board best-move arrow).
     */
    private final JLabel analysisBestMoveValue = metricValue();

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
     * PGN-backed local Study Workspace panel, created lazily with board detail tabs.
     */
    protected StudyWorkspacePanel studyWorkspacePanel;

    /**
     * PGN-backed game library panel, created lazily with the board detail tabs.
     */
    protected GameLibraryPanel gameLibraryPanel;

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
     * The currently active board mode (see {@code BOARD_*}); tracked so header
     * refreshes can re-preview the Play start position while it is the live mode.
     */
    private int currentBoardMode = BOARD_ANALYZE;

    /**
     * Guards the annotation-binding observer while markup is being reloaded for a
     * position (so reloading does not immediately re-persist it).
     */
    private boolean markupBindingSuppressed;

    /**
     * When true, drawn annotations stay on the board across navigation (legacy
     * free-canvas / diagram-export behaviour) instead of binding to the current
     * move node. Default false = annotations are a property of the position.
     */
    private boolean annotationsPinnedToBoard;

    /**
     * Board slot in the Analyze mode card; hosts the shared board when Analyze
     * is active.
     */
    private transient JPanel analyzeBoardSlot;

    /**
     * Compact main-line tree shown beside the Analyze board.
     */
    private transient AnalyzeVariationTreePanel analyzeVariationTree;

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
     * Board slot in the Study mode card.
     */
    private transient JPanel studyBoardSlot;

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
     * Shared inspector rail for the Relations mode, reset when the mode activates
     * so the rail never opens mid-list after reused viewport state or automated
     * capture scroll events.
     */
    private transient BoardInspectorRail relationsRail;

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
        // One shared board backs the Analyze, Play, Relations, Draw, and Study
        // modes: it is re-parented into the active mode's slot and reconfigured
        // for it by configureBoardForMode, so a position carries across modes
        // with no duplicate widgets. Solve keeps its own board (puzzles step
        // their own positions, independent of the analysis line). The eval bar
        // lives on the shared board and paints inside its paint pass.
        evalBar.setToolTipText("Engine evaluation");
        board.setEvalBar(evalBar);
        sharedBoardStage = new application.gui.workbench.board.BoardStage(board);
        // Persist board annotations onto the current move node as they are drawn
        // (Phase 2: annotation == position property; merges Draw and Study).
        board.addMarkupChangeObserver(this::onBoardMarkupChanged);
        boardWorkspace = new SwitchedWorkspace("Board",
                List.of(
                        new WorkspaceMode("Analyze", this::createBoardTab, this::boardAnalyzeContext,
                                this::boardAnalyzeActions),
                        new WorkspaceMode("Play", this::createPlayTab, this::boardPlayContext),
                        new WorkspaceMode("Solve", this::createPuzzleTab, this::boardSolveContext),
                        new WorkspaceMode("Relations", this::createRelationsTab, this::boardRelationsContext),
                        new WorkspaceMode("Draw", this::createDrawTab, this::boardDrawContext),
                        new WorkspaceMode("Study", this::createStudyTab, this::boardStudyContext)),
                BOARD_ANALYZE);
        boardWorkspace.setModeListener(this::configureBoardForMode);
        // A shared footer south of the whole workspace: position-level actions
        // (continue vs bot / make into study) over a collapsible PGN/FEN peek,
        // so every board mode treats the current position the same way (one
        // instance, travels with no mode).
        JPanel footer = transparentPanel(new BorderLayout(0, Theme.SPACE_XS));
        footer.add(createBoardActionsRow(), BorderLayout.NORTH);
        footer.add(createBoardPgnPeek(), BorderLayout.CENTER);
        JPanel boardPage = transparentPanel(new BorderLayout(0, Theme.SPACE_SM));
        boardPage.add(boardWorkspace, BorderLayout.CENTER);
        boardPage.add(footer, BorderLayout.SOUTH);
        refreshGameNotationPreview();
        return boardPage;
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
            case BOARD_STUDY -> studyBoardSlot;
            default -> null;
        };
        if (slot == null || sharedBoardStage == null) {
            return;
        }
        currentBoardMode = mode;
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
            // the move funnel and turn-gated input.
            clearRelationMarkupIfActive();
            board.setDirectAnnotationMode(false);
            board.setMoveHandler(this::playMove);
            board.setDragStartFilter(context ->
                    playSession == null || playSession.isHumanInputAllowed());
            board.setPremoveStartFilter(context ->
                    playSession != null && playSession.isPremoveSourceAllowed(context.square(), context.piece()));
            board.setPremoveHandler(context ->
                    playSession != null && playSession.queuePremove(context.tentativeMove()));
            if (mode == BOARD_PLAY && playPanel().isAwaitingGame()) {
                // Pre-game Play is a setup screen: show its own chosen start, not
                // the analysis line (or a stray Draw/hint arrow) under a "no game
                // active" header. A live or finished game keeps its position.
                showPlayStartPreview();
            } else {
                restoreSharedBoardPosition();
            }
        }
    }

    /**
     * Previews the Play setup's chosen start position on the shared board before
     * a game begins, dropping any markup, hint, or premove inherited from Analyze
     * or Draw. This does not touch the shared analysis line — that is restored
     * when Analyze reactivates — so peeking at Play is non-destructive.
     */
    private void showPlayStartPreview() {
        board.clearMarkup();
        board.setSuggestedMove(chess.core.Move.NO_MOVE);
        board.clearPremove();
        chess.core.Position preview = playPanel().previewStartPosition();
        if (preview != null) {
            board.setPositionInstant(preview, chess.core.Move.NO_MOVE);
        }
    }

    /**
     * Re-previews the chosen start position when the Play "Start from" selection
     * changes while Play is the live, idle mode — so the board tracks the choice
     * without disturbing a live or finished game, or any other mode.
     */
    private void maybeShowPlayStartPreview() {
        if (currentBoardMode == BOARD_PLAY
                && sharedBoardStage != null && sharedBoardStage.getParent() == playBoardSlot
                && playPanel().isAwaitingGame()) {
            showPlayStartPreview();
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
        reloadBoardMarkupForCurrentNode();
    }

    /**
     * Persists the board's current annotations onto the current move node as
     * they are drawn, so an arrow/comment becomes a property of the position
     * (survives navigation, reload, and PGN export). Skipped while reloading,
     * while annotations are pinned to the board, and in the read-only Relations
     * overlay (whose markup is the tactical channel painting, not user
     * annotation).
     */
    private void onBoardMarkupChanged() {
        if (markupBindingSuppressed || annotationsPinnedToBoard
                || currentBoardMode == BOARD_RELATIONS || relationMarkupActive) {
            return;
        }
        String existing = gameModel.currentNodeComment();
        String merged = application.gui.workbench.board.BoardMarkupComment.encode(
                application.gui.workbench.board.BoardMarkupComment.plainText(existing), board.boardMarkups());
        gameModel.setCurrentNodeComment(merged);
        refreshGameNotationPreview();
    }

    /**
     * Repaints the current position's bound annotations onto the board (or
     * leaves the board markup alone when annotations are pinned to the board).
     * Called after every navigation / position change.
     */
    protected void reloadBoardMarkupForCurrentNode() {
        if (annotationsPinnedToBoard || currentBoardMode == BOARD_RELATIONS || relationMarkupActive) {
            return;
        }
        markupBindingSuppressed = true;
        try {
            board.applyMarkupComment(gameModel.currentNodeComment());
        } finally {
            markupBindingSuppressed = false;
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
     * Builds the standard board-page split: primary content (board slot, or the
     * board beside its variation tree) on the left and an inspector rail on the
     * right, at the shared {@link #BOARD_PAGE_WEIGHT} proportion.
     *
     * @param content leading content component
     * @param rail trailing inspector rail
     * @return the styled board-page split
     */
    private static JSplitPane boardPageSplit(java.awt.Component content, java.awt.Component rail) {
        return SplitPaneStyler.styledHorizontalSplit(content, rail, BOARD_PAGE_WEIGHT);
    }

    /**
     * Creates the unified Engine surface: one workspace hosting the neural
     * network visualizer, the PUCT/MCTS table/graph Search workspace, and engine
     * gauntlets. The Network mode is built first; Search and Gauntlet build
     * lazily on first selection.
     *
     * @return engine workspace component
     */
    protected JComponent createEngineWorkspaceTab() {
        engineWorkspace = new SwitchedWorkspace("Engine Lab",
                List.of(
                        new WorkspaceMode("Network", this::createNetworkTab, this::engineEvaluatorContext),
                        new WorkspaceMode("Search", this::createMctsTab, this::engineSearchContext),
                        new WorkspaceMode("Gauntlet", this::createGauntletTab, this::engineGauntletContext)),
                ENGINE_NETWORK);
        return engineWorkspace;
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
     * Returns the Board / Study context line.
     *
     * @return context summary
     */
    private String boardStudyContext() {
        if (studyWorkspacePanel == null) {
            return "No study loaded";
        }
        int chapters = studyWorkspacePanel.chapterCount();
        return chapters + (chapters == 1 ? " chapter" : " chapters") + " · moves recorded on the board";
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
        return currentPosition == null ? "No position loaded · PUCT/MCTS table and graph"
                : "Current FEN · PUCT/MCTS table and graph";
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

        JComponent lineTree = createVariationTreePanel();
        lineTree.setPreferredSize(new Dimension(VARIATION_TREE_WIDTH, 1));
        lineTree.setMinimumSize(new Dimension(176, 1));

        // Analyze keeps its lighter PANEL document tone (vs the BACKDROP board
        // wash of Play/Draw/Relations); the variation tree on the left is its
        // move-list lens, so the rail carries no move list.
        JComponent side = new BoardInspectorRail(Theme.Surface.PANEL, null, createAnalyzeInspector(), null);

        JSplitPane boardAndLine = SplitPaneStyler.styledHorizontalSplit(lineTree, analyzeBoardSlot, 0.22);
        boardAndLine.setResizeWeight(0.0d);
        boardAndLine.setDividerLocation(VARIATION_TREE_WIDTH);
        JSplitPane boardPage = boardPageSplit(boardAndLine, side);

        analysisCards = transparentPanel(new CardLayout());
        analysisCards.add(boardPage, ANALYZE_CARD_BOARD);
        analysisCards.add(createGameSection(), ANALYZE_CARD_GAME);
        return analysisCards;
    }

    /**
     * Creates the compact main-line tree beside the Analyze board.
     *
     * @return variation tree component
     */
    private JComponent createVariationTreePanel() {
        analyzeVariationTree = new AnalyzeVariationTreePanel(gameModel, this::showGameRow);
        return scroll(analyzeVariationTree, () -> Theme.BG);
    }

    /**
     * Shows a visible game-history row on the board, including imported PGN
     * variation rows.
     *
     * @param row visible game-history row
     */
    protected abstract void showGameRow(int row);

    /**
     * Creates the Board / Analyze right-inspector contents (the
     * {@link BoardInspectorRail} wraps this in a scroll on the lighter
     * Analyze document surface).
     *
     * @return inspector stack
     */
    private JComponent createAnalyzeInspector() {
        JPanel stack = transparentPanel(null);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.add(createAnalysisWorkspaceCard());
        stack.add(Box.createVerticalStrut(Theme.SPACE_MD));
        stack.add(createPositionControls());
        stack.add(Box.createVerticalStrut(Theme.SPACE_MD));
        stack.add(createAnalysisControls());
        stack.add(Box.createVerticalGlue());
        return stack;
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
        fenField.setColumns(24);

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

        body.add(metricList(
                metricRow("Side", analyzeSideValue),
                metricRow("Legal", analyzeLegalValue),
                metricRow("Material", analyzeMaterialValue)));
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
        JButton review = button("Review", false, event -> runCurrentGameReview());
        review.setToolTipText("Review the current game line and retry critical moves");
        JButton games = button("Library", false, event -> showBoardDetail("Library"));
        games.setToolTipText("Open saved Workbench games for resume or review");
        JButton endgame = button("Endgame", false, event -> showBoardDetail("Endgame"));
        endgame.setToolTipText("Show tablebase-eligibility and fixed-node endgame analysis");
        JButton study = button("Study", false, event -> showBoardDetail("Study"));
        study.setToolTipText("Show study/repertoire TOML authoring for the current game line");
        JButton gauntlet = button("Gauntlet", false, event -> openEngine(ENGINE_GAUNTLET));
        gauntlet.setToolTipText("Open deterministic built-in engine self-play gauntlets");
        row.add(opening);
        row.add(review);
        row.add(games);
        row.add(endgame);
        row.add(study);
        row.add(gauntlet);
        return row;
    }

    /**
     * Returns theme option labels in {@link Theme.Mode} ordinal order.
     *
     * @return theme labels
     */
    private static String[] themeModeLabels() {
        Theme.Mode[] modes = Theme.Mode.values();
        String[] labels = new String[modes.length];
        for (int i = 0; i < modes.length; i++) {
            labels[i] = modes[i].label();
        }
        return labels;
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
        group.setBorder(Theme.lineBorder(Theme.LINE, 1));
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
    protected static JPanel verticalBody() {
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
     * Builds a stacked metric list for narrow inspector cards.
     *
     * @param metrics metric rows
     * @return metric list
     */
    private static JComponent metricList(JComponent... metrics) {
        JPanel panel = transparentPanel(null);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (JComponent metric : metrics) {
            panel.add(metric);
            panel.add(Box.createVerticalStrut(Theme.SPACE_XS));
        }
        return panel;
    }

    /**
     * Builds one horizontal metric row.
     *
     * @param label metric label
     * @param value value label
     * @return metric row
     */
    private static JComponent metricRow(String label, JLabel value) {
        JPanel panel = transparentPanel(new BorderLayout(Theme.SPACE_SM, 0));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel title = new JLabel(label);
        Theme.foreground(title, Theme.ForegroundRole.MUTED);
        value.setHorizontalAlignment(JLabel.RIGHT);
        panel.add(title, BorderLayout.WEST);
        panel.add(value, BorderLayout.EAST);
        return panel;
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
     * Creates the merged analysis result and candidate-move workspace.
     *
     * @return analysis workspace card
     */
    private JComponent createAnalysisWorkspaceCard() {
        JPanel body = verticalBody();
        body.add(createAnalysisResultPanel());
        body.add(Box.createVerticalStrut(Theme.SPACE_SM));
        body.add(createMovesAndTags());
        return Ui.card("Analysis", body);
    }

    /**
     * Creates the analysis-result panel.
     *
     * @return result panel
     */
    private JComponent createAnalysisResultPanel() {
        analysisResultCards.setOpaque(false);

        JComponent empty = createCompactAnalysisEmptyState();

        JPanel populated = verticalBody();
        // Position-first surface: one evaluation readout plus the engine's best
        // move (which drives the on-board arrow). The depth / nodes / nodes-per-
        // second / samples telemetry is intentionally dropped here — the full
        // eval timeline belongs to Game Review, not the live board.
        populated.add(metricGrid(
                metric("Eval", analysisEvalValue),
                metric("Best move", analysisBestMoveValue)));
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
        return analysisResultCards;
    }

    /**
     * Creates a narrow empty state for the analysis rail.
     *
     * @return compact empty state
     */
    private JComponent createCompactAnalysisEmptyState() {
        JPanel panel = verticalBody();
        panel.add(label("No analysis"));
        panel.add(Box.createVerticalStrut(Theme.SPACE_XS));
        panel.add(Ui.caption("Run Analyze or Search to see eval lines."));
        panel.add(Box.createVerticalStrut(Theme.SPACE_SM));
        panel.add(controlRow(FlowLayout.LEFT, button("Analyze", true, event -> runAnalyze())));
        return panel;
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
        if (analyzeVariationTree != null) {
            analyzeVariationTree.revalidate();
            analyzeVariationTree.repaint();
        }
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
        boardDetailTabs.addTab("Library", createGameLibraryPanel());
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
     * Creates the deterministic post-game review panel.
     *
     * @return review component
     */
    protected JComponent createGameReviewPanel() {
        gameReviewPanel = new GameReviewPanel(() -> gameModel, this::jumpGameTo, this::copyText,
                ReviewCliArtifactProducer.offline());
        return scroll(fillViewport(gameReviewPanel));
    }

    /**
     * Creates the PGN-backed game library detail tab.
     *
     * @return game library component
     */
    protected JComponent createGameLibraryPanel() {
        gameLibraryPanel = new GameLibraryPanel(this::libraryGames, this::openLibraryGame,
                this::copyText, this::importPgnIntoLibrary, this::saveCurrentGameFromUi);
        return scroll(fillViewport(gameLibraryPanel));
    }

    /**
     * Refreshes the PGN-backed library panel when present.
     */
    @Override
    protected void refreshGameLibraryPanel() {
        if (gameLibraryPanel != null) {
            gameLibraryPanel.refresh();
        }
    }

    /**
     * Imports one PGN file into the local game library.
     */
    protected void importPgnIntoLibrary() {
        JFileChooser chooser = FileDialogs.createFileChooser(null, null,
                new FileNameExtensionFilter("PGN files", "pgn"));
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        java.nio.file.Path path = chooser.getSelectedFile().toPath();
        runAsync(
                () -> new GameLibrary().importPgn(path),
                report -> {
                    appendConsole("Imported PGN library file " + path
                            + " (new " + report.imported()
                            + ", duplicate " + report.duplicates()
                            + ", malformed " + report.malformed() + ")\n");
                    toast(Toast.Kind.SUCCESS, "Imported " + report.imported() + " library games");
                    refreshGameLibraryPanel();
                },
                ex -> showError("PGN library import failed", ex.getMessage()));
    }

    /**
     * Opens Review and analyzes the current game line.
     */
    protected void runCurrentGameReview() {
        showBoardDetail("Review");
        if (gameReviewPanel != null) {
            gameReviewPanel.runReview();
        } else {
            SwingUtilities.invokeLater(() -> {
                if (gameReviewPanel != null) {
                    gameReviewPanel.runReview();
                }
            });
        }
    }

    /**
     * Resumes a saved game and immediately runs Review.
     *
     * @param game saved game
     */
    protected void reviewSavedGame(SavedGame game) {
        if (resumeSavedGame(game)) {
            runCurrentGameReview();
        }
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
     * Creates the PGN-backed local study workspace.
     *
     * @return study workspace component
     */
    protected StudyWorkspacePanel createStudyWorkspacePanel() {
        studyWorkspacePanel = new StudyWorkspacePanel(StudyRepository.defaultRepository(),
                () -> gameModel, this::currentFen,
                this::showStudyWorkspacePosition,
                this::copyText);
        return studyWorkspacePanel;
    }

    /**
     * Shows a Study Workspace position on the shared board.
     *
     * @param position study position
     */
    protected abstract void showStudyWorkspacePosition(Position position);

    /**
     * Returns whether Study is the active board mode (so shared-board moves are
     * recorded into the study).
     *
     * @return true when the Study mode is active
     */
    protected boolean isStudyWorkspaceActive() {
        return boardWorkspace != null && boardWorkspace.mode() == BOARD_STUDY;
    }

    /**
     * Records a board move in the active Study Workspace.
     *
     * @param before position before move
     * @param move encoded move
     */
    protected void recordStudyWorkspaceMove(Position before, short move) {
        if (isStudyWorkspaceActive() && studyWorkspacePanel != null) {
            studyWorkspacePanel.addBoardMove(before, move);
        }
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
        boardEditorPanel = new BoardEditorPanel(this::currentFen, this::applyBoardEditorFen, this::copyText);
        boardEditorPanel.attachBoard(board);
        return scroll(fillViewport(boardEditorPanel));
    }

    /**
     * Toggles direct setup editing on the shared board when the Editor tab is
     * opened or closed.
     */
    protected void syncBoardEditorMode() {
        if (boardEditorPanel == null || boardDetailTabs == null) {
            return;
        }
        boolean selected = isBoardEditorSelected();
        if (selected) {
            boardEditorPanel.loadFen(currentFen());
        }
        boardEditorPanel.setEditingBoardActive(selected && !playPositionLocked);
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
            boardEditorPanel.setEditingBoardActive(isBoardEditorSelected() && !locked);
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
        c.insets = new Insets(6, 6, 10, 6);
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1.0;
        c.weighty = 1.0;

        grid(panel, settingsColumn(
                Ui.card("Keyboard shortcuts", createKeyboardShortcutSettings()),
                Ui.card("General", createGeneralSettings()),
                Ui.card("Appearance", createAppearanceSettings())), c, 0, 0, 1, 1);
        grid(panel, settingsColumn(
                Ui.card("Move list", createMoveListSettings()),
                Ui.card("Board", createBoardMenuSettings()),
                Ui.card("Sound", createSoundSettings())), c, 1, 0, 1, 1);
        return panel;
    }

    /**
     * Creates one column in the lichess-style settings sheet.
     *
     * @param groups section groups
     * @return vertical column
     */
    private static JPanel settingsColumn(JComponent... groups) {
        JPanel column = verticalBody();
        for (int i = 0; i < groups.length; i++) {
            column.add(groups[i]);
            if (i + 1 < groups.length) {
                column.add(Box.createVerticalStrut(Theme.SPACE_MD));
            }
        }
        return column;
    }

    /**
     * Creates the display keyboard-shortcut reference group.
     *
     * @return shortcut group
     */
    private JComponent createKeyboardShortcutSettings() {
        JPanel panel = settingsGroupPanel();
        GridBagConstraints c = constraints();
        c.insets = SETTINGS_ROW_INSETS;
        grid(panel, shortcutRow("Flip board", "f",
                "Planned shortcut for flipping the shared board"), c, 0, 0, 1, 1);
        grid(panel, shortcutRow("Toggle local engine", "l",
                "Planned shortcut for local-engine analysis"), c, 0, 1, 1, 1);
        grid(panel, shortcutRow("Show server analysis", "z",
                "Planned shortcut for live external-engine analysis"), c, 0, 2, 1, 1);
        grid(panel, shortcutRow("Inline notation", "shift + i",
                "Inline notation is the current Workbench move-list presentation"), c, 0, 3, 1, 1);
        grid(panel, shortcutRow("Show best move arrows", "a",
                "Planned shortcut for best-move board arrows"), c, 0, 4, 1, 1);
        grid(panel, shortcutRow("Show variation arrows", "v",
                "Planned shortcut for variation arrows once that overlay exists"), c, 0, 5, 1, 1);
        grid(panel, button("Show all", false, event ->
                toast(Toast.Kind.INFO, "Keyboard shortcuts will expand as Workbench actions gain bindings")),
                c, 0, 6, 1, 1);
        return panel;
    }

    /**
     * Creates the general analysis preferences group.
     *
     * @return general settings group
     */
    private JComponent createGeneralSettings() {
        JPanel panel = settingsGroupPanel();
        GridBagConstraints c = constraints();
        c.insets = SETTINGS_ROW_INSETS;
        grid(panel, menuToggleRow("Show server analysis",
                "Keep the configured external engine analyzing the visible board",
                liveExternalEngineEnabled, this::setLiveExternalEngineEnabled), c, 0, 0, 1, 1);
        grid(panel, menuToggleRow("Show evaluation gauge",
                "Automatically refresh the side evaluation bar after position changes",
                autoEvalBarEnabled,
                selected -> updateDisplaySetting(() -> autoEvalBarEnabled = selected, true)), c, 0, 1, 1, 1);
        return panel;
    }

    /**
     * Creates the move-list preferences group.
     *
     * @return move-list settings group
     */
    private JComponent createMoveListSettings() {
        JPanel panel = settingsGroupPanel();
        GridBagConstraints c = constraints();
        c.insets = SETTINGS_ROW_INSETS;
        grid(panel, disabledMenuToggleRow("Inline notation",
                "Inline notation is the current Workbench move-list default", true), c, 0, 0, 1, 1);
        grid(panel, disabledMenuToggleRow("Enable variation hiding",
                "Variation hiding is not a separate Workbench setting yet", false), c, 0, 1, 1, 1);
        grid(panel, disabledMenuToggleRow("Live engine annotations",
                "Per-move live annotations are not split from the analysis pane yet", false), c, 0, 2, 1, 1);
        return panel;
    }

    /**
     * Creates the board overlay preferences group.
     *
     * @return board settings group
     */
    private JComponent createBoardMenuSettings() {
        JPanel panel = settingsGroupPanel();
        GridBagConstraints c = constraints();
        c.insets = SETTINGS_ROW_INSETS;
        grid(panel, menuToggleRow("Legal move preview",
                "Show selected-piece destinations and legal drag targets on the board",
                showLegalMovePreview,
                selected -> updateDisplaySetting(() -> showLegalMovePreview = selected, false)), c, 0, 0, 1, 1);
        grid(panel, menuToggleRow("Last move highlight",
                "Show the previous move on the board",
                showLastMoveHighlight,
                selected -> updateDisplaySetting(() -> showLastMoveHighlight = selected, false)), c, 0, 1, 1, 1);
        grid(panel, menuToggleRow("Show best move arrows",
                "Show engine best-move and analysis suggestions as board arrows",
                showBestMoveArrows,
                selected -> updateDisplaySetting(() -> showBestMoveArrows = selected, false)), c, 0, 2, 1, 1);
        grid(panel, menuToggleRow("Coordinates",
                "Show file and rank notation on the board",
                showBoardCoordinates,
                selected -> updateDisplaySetting(() -> showBoardCoordinates = selected, false)), c, 0, 3, 1, 1);
        grid(panel, menuToggleRow("Board animations",
                "Animate moves, snaps, snapbacks, and board flips",
                boardAnimationsEnabled,
                selected -> updateDisplaySetting(() -> boardAnimationsEnabled = selected, false)), c, 0, 4, 1, 1);
        grid(panel, disabledMenuToggleRow("Show variation arrows",
                "Variation arrows are not a separate board overlay yet", false), c, 0, 5, 1, 1);
        grid(panel, disabledMenuToggleRow("Show maneuver arrows",
                "Maneuver arrows are not a separate board overlay yet", false), c, 0, 6, 1, 1);
        grid(panel, disabledMenuToggleRow("Show move annotations",
                "Move annotations are shown in analysis text, not as a board overlay yet", false), c, 0, 7, 1, 1);
        grid(panel, menuToggleRow("Show undefended pieces",
                "Mark attacked pieces that have no same-side defender",
                showUndefendedPieces,
                selected -> updateDisplaySetting(() -> showUndefendedPieces = selected, false)), c, 0, 8, 1, 1);
        grid(panel, menuToggleRow("Show pinned pieces",
                "Mark pieces pinned to their own king by an enemy slider",
                showPinnedPieces,
                selected -> updateDisplaySetting(() -> showPinnedPieces = selected, false)), c, 0, 9, 1, 1);
        grid(panel, menuToggleRow("Show checkable king",
                "Mark the king when the side to move has a legal check available",
                showCheckableKing,
                selected -> updateDisplaySetting(() -> showCheckableKing = selected, false)), c, 0, 10, 1, 1);
        return panel;
    }

    /**
     * Creates the appearance preferences group.
     *
     * @return appearance settings group
     */
    private JComponent createAppearanceSettings() {
        JPanel appearance = settingsGroupPanel();
        GridBagConstraints c = constraints();
        c.insets = SETTINGS_ROW_INSETS;
        Theme.Mode[] themeModes = Theme.Mode.values();
        application.gui.workbench.ui.ChipGroup themePicker =
                new application.gui.workbench.ui.ChipGroup(java.util.List.of(themeModeLabels()));
        themePicker.setSelectedIndex(Theme.mode().ordinal());
        themePicker.setOnSelect(index -> {
            if (index >= 0 && index < themeModes.length) {
                setThemeMode(themeModes[index]);
            }
        });
        themePicker.setToolTipText("Switch the workbench palette");
        grid(appearance, createLabelDetailRow("Theme", "Pick the workbench colour palette",
                themePicker), c, 0, 0, 1, 1);

        application.gui.workbench.ui.ChipGroup piecePicker =
                new application.gui.workbench.ui.ChipGroup(java.util.List.of(pieceSetLabels()));
        piecePicker.setSelectedIndex(pieceSet.ordinal());
        piecePicker.setOnSelect(index -> applyPieceSet(PieceSet.values()[index]));
        piecePicker.setToolTipText("Choose the chess piece artwork set");
        pieceSetChips = piecePicker;
        grid(appearance, createLabelDetailRow("Pieces", "Artwork used to draw the pieces",
                piecePicker), c, 0, 1, 1, 1);
        return appearance;
    }

    /**
     * Creates the sound preferences group.
     *
     * @return sound settings group
     */
    private JComponent createSoundSettings() {
        JPanel soundSettings = settingsGroupPanel();
        GridBagConstraints c = constraints();
        c.insets = SETTINGS_ROW_INSETS;
        grid(soundSettings, settingsToggle("Sound effects",
                "Play procedural feedback for controls, moves, loaded positions, puzzles, MCTS, and long jobs",
                !SoundService.isMuted(), selected -> {
                    SoundService.setMuted(!selected);
                    if (settingsMenu != null) {
                        settingsMenu.syncMode();
                    }
                }), c, 0, 0, 1, 1);
        grid(soundSettings, labeledControl("Volume", createSoundVolumeSlider()),
                c, 0, 1, 1, 1);
        grid(soundSettings, button("Preview", false, event -> SoundService.play(SoundCue.POSITION_LOAD)),
                c, 0, 2, 1, 1);
        return soundSettings;
    }

    /**
     * Creates one display-menu toggle row.
     *
     * @param text visible row text
     * @param tooltip tooltip and accessible description
     * @param selected selected state
     * @param onChange change callback
     * @return row component
     */
    private static JComponent menuToggleRow(String text, String tooltip, boolean selected,
            Consumer<Boolean> onChange) {
        return menuToggleRow(text, tooltip, selected, true, onChange);
    }

    /**
     * Creates one disabled display-menu toggle row.
     *
     * @param text visible row text
     * @param tooltip disabled-state explanation
     * @param selected selected state to display
     * @return row component
     */
    private static JComponent disabledMenuToggleRow(String text, String tooltip, boolean selected) {
        return menuToggleRow(text, tooltip, selected, false, value -> {
            // disabled placeholder for planned Workbench display options
        });
    }

    /**
     * Creates one display-menu toggle row.
     *
     * @param text visible row text
     * @param tooltip tooltip and accessible description
     * @param selected selected state
     * @param enabled whether the row is interactive
     * @param onChange change callback
     * @return row component
     */
    private static JComponent menuToggleRow(String text, String tooltip, boolean selected,
            boolean enabled, Consumer<Boolean> onChange) {
        JPanel row = transparentPanel(new BorderLayout(Theme.SPACE_SM, 0));
        row.setToolTipText(tooltip);
        JLabel title = new JLabel(text);
        Theme.foreground(title, enabled ? Theme.ForegroundRole.TEXT : Theme.ForegroundRole.MUTED);
        title.setEnabled(enabled);
        title.setToolTipText(tooltip);
        ToggleBox toggle = new ToggleBox("", true);
        toggle.setSelected(selected);
        toggle.setEnabled(enabled);
        toggle.setToolTipText(tooltip);
        toggle.setActionCommand(displaySettingActionId(text));
        AccessibleContext context = toggle.getAccessibleContext();
        if (context != null) {
            context.setAccessibleName(text);
            context.setAccessibleDescription(tooltip);
        }
        if (enabled) {
            toggle.addActionListener(event -> onChange.accept(toggle.isSelected()));
        }
        row.add(title, BorderLayout.CENTER);
        row.add(toggle, BorderLayout.EAST);
        return row;
    }

    /**
     * Creates one keyboard-shortcut display row.
     *
     * @param text shortcut label
     * @param shortcut keycap text
     * @param tooltip row tooltip
     * @return row component
     */
    private static JComponent shortcutRow(String text, String shortcut, String tooltip) {
        JPanel row = transparentPanel(new BorderLayout(Theme.SPACE_SM, 0));
        row.setToolTipText(tooltip);
        JLabel title = new JLabel(text);
        Theme.foreground(title, Theme.ForegroundRole.MUTED);
        title.setToolTipText(tooltip);
        JLabel key = Ui.caption(shortcut);
        key.setHorizontalAlignment(JLabel.CENTER);
        key.setPreferredSize(new Dimension(Math.max(28, shortcut.length() * 8 + 12), Theme.CONTROL_HEIGHT));
        key.setToolTipText(tooltip);
        row.add(title, BorderLayout.CENTER);
        row.add(key, BorderLayout.EAST);
        return row;
    }

    /**
     * Returns a stable action id for one display setting.
     *
     * @param text visible label
     * @return action id
     */
    private static String displaySettingActionId(String text) {
        String key = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        key = key.replaceAll("^-|-$", "");
        return "workbench.display." + key;
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
        preview.setFont(Theme.mono(11));
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
        tools.setPreferredSize(new Dimension(360, 520));

        JSplitPane gamePage = boardPageSplit(createGameHistoryPanel(), tools);
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
        JPanel content = transparentPanel(new BorderLayout(0, Theme.SPACE_SM));
        content.add(createGameNotationPreview(), BorderLayout.NORTH);
        content.add(scroll(gameTable), BorderLayout.CENTER);
        panel.add(content, BorderLayout.CENTER);

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
     * Creates the read-only FEN / PGN preview block for the selected game line.
     *
     * @return notation preview component
     */
    private JComponent createGameNotationPreview() {
        gameFenPreview.setEditable(false);
        gameFenPreview.setRows(1);
        gameFenPreview.setColumns(42);
        gameFenPreview.setLineWrap(false);
        gameFenPreview.setToolTipText("Current selected position FEN");
        gameFenPreview.getAccessibleContext().setAccessibleName("Selected position FEN");
        gamePgnPreview.setEditable(false);
        gamePgnPreview.setRows(2);
        gamePgnPreview.setLineWrap(true);
        gamePgnPreview.setWrapStyleWord(true);
        gamePgnPreview.setToolTipText("Current game PGN with variations");
        gamePgnPreview.getAccessibleContext().setAccessibleName("Current game PGN");

        JPanel panel = transparentPanel(new GridBagLayout());
        GridBagConstraints c = constraints();
        c.insets = new Insets(0, 0, Theme.SPACE_SM, Theme.SPACE_SM);
        c.weightx = 0.0d;
        c.fill = GridBagConstraints.NONE;
        grid(panel, label("FEN"), c, 0, 0, 1, 1);
        c.weightx = 1.0d;
        c.fill = GridBagConstraints.HORIZONTAL;
        JScrollPane fenScroll = scroll(gameFenPreview, () -> Theme.INPUT);
        fenScroll.setPreferredSize(new Dimension(260, 36));
        grid(panel, fenScroll, c, 1, 0, 1, 1);

        GridBagConstraints pgnC = constraints();
        pgnC.insets = new Insets(0, 0, 0, Theme.SPACE_SM);
        pgnC.weightx = 0.0d;
        pgnC.fill = GridBagConstraints.NONE;
        grid(panel, label("PGN"), pgnC, 0, 1, 1, 1);
        pgnC.weightx = 1.0d;
        pgnC.fill = GridBagConstraints.BOTH;
        JScrollPane pgnScroll = scroll(gamePgnPreview, () -> Theme.INPUT);
        pgnScroll.setPreferredSize(new Dimension(260, 64));
        grid(panel, pgnScroll, pgnC, 1, 1, 1, 1);
        refreshGameNotationPreview();
        return panel;
    }

    /**
     * Refreshes read-only FEN / PGN previews for the active game selection.
     */
    protected void refreshGameNotationPreview() {
        if (gameFenPreview == null || gamePgnPreview == null) {
            return;
        }
        String fen = gameModel.currentPosition().toString();
        String pgn = gameModel.pgn();
        gameFenPreview.setText(fen);
        gameFenPreview.setCaretPosition(0);
        gamePgnPreview.setText(pgn);
        gamePgnPreview.setCaretPosition(0);
        boardFenPeek.setText(fen);
        boardFenPeek.setCaretPosition(0);
        boardPgnPeek.setText(pgn);
        boardPgnPeek.setCaretPosition(0);
    }

    /**
     * Builds the always-visible position-action row in the board footer:
     * continue the current position against a bot, or turn it into a study —
     * lichess-style "actions on the position" reachable from every board mode.
     *
     * @return board actions row
     */
    private JComponent createBoardActionsRow() {
        return controlRow(FlowLayout.LEFT,
                button("Continue vs bot", false, event -> continueVsBotFromHere()),
                button("Make into study", false, event -> makeIntoStudyFromHere()));
    }

    /**
     * Continues the current board position as a bot game (the inline
     * "continue from here" action): activates Play and starts immediately from
     * the current position with the selected opponent.
     */
    private void continueVsBotFromHere() {
        boardWorkspace.setMode(BOARD_PLAY);
        playPanel().continueFromCurrentPosition();
    }

    /**
     * Turns the current game line into a study chapter (the inline "make into
     * study" action) and opens Study showing it.
     */
    private void makeIntoStudyFromHere() {
        if (studyWorkspacePanel == null) {
            createStudyWorkspacePanel();
        }
        studyWorkspacePanel.addCurrentGameAsChapter();
        boardWorkspace.setMode(BOARD_STUDY);
    }

    /**
     * Builds the always-available PGN/FEN peek docked south of the Board
     * workspace: the current line's PGN (with variations and annotations) over a
     * one-line FEN, collapsible, with Copy actions. Refreshed by
     * {@link #refreshGameNotationPreview()} on every game-line update.
     *
     * @return collapsible PGN/FEN peek
     */
    private JComponent createBoardPgnPeek() {
        boardFenPeek.setEditable(false);
        boardFenPeek.setRows(1);
        boardFenPeek.setLineWrap(false);
        boardFenPeek.setToolTipText("Current position FEN");
        boardFenPeek.getAccessibleContext().setAccessibleName("Board PGN peek FEN");
        boardPgnPeek.setEditable(false);
        boardPgnPeek.setRows(2);
        boardPgnPeek.setLineWrap(true);
        boardPgnPeek.setWrapStyleWord(true);
        boardPgnPeek.setToolTipText("Current line PGN, with variations and annotations");
        boardPgnPeek.getAccessibleContext().setAccessibleName("Board PGN peek");

        JPanel body = transparentPanel(new BorderLayout(0, Theme.SPACE_XS));
        body.add(scroll(boardFenPeek, () -> Theme.INPUT), BorderLayout.NORTH);
        body.add(scroll(boardPgnPeek, () -> Theme.INPUT), BorderLayout.CENTER);
        body.add(controlRow(FlowLayout.LEFT,
                button("Copy PGN", false, event -> copyText(gameModel.pgn())),
                button("Copy FEN", false, event -> copyText(gameModel.currentPosition().toString()))),
                BorderLayout.SOUTH);
        return collapsible("PGN / FEN", body, true);
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
                button("Review Game", false, event -> runCurrentGameReview()),
                button("Library", false, event -> showBoardDetail("Library")),
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
     * Creates the MCTS Search workspace.
     *
     * @return MCTS Search workspace
     */
    protected JComponent createMctsTab() {
        return mctsWorkspacePanel();
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
        controls.setStartPreviewListener(this::maybeShowPlayStartPreview);
        playBoardSlot = transparentPanel(new BorderLayout(0, Theme.SPACE_SM));
        playBoardSlot.add(controls.opponentIdentityStrip(), BorderLayout.NORTH);
        playBoardSlot.add(controls.playerIdentityStrip(), BorderLayout.SOUTH);

        // The rail is the shared board-inspector seam: a status header above the
        // Play setup form above the shared move list, the form/list boundary
        // filling the canvas height so the column tracks the window instead of
        // stranding a fixed block. The move list is the reusable MoveListPanel
        // over the same game line Analyze shows.
        JComponent rail = new BoardInspectorRail(controls.playStatusHeader(), controls,
                new MoveListPanel(gameModel, this::jumpGameTo));

        return boardPageSplit(playBoardSlot, rail);
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
        // Same shared rail as Play/Draw (no status header, no move list): the
        // channel controls fill the canvas height instead of a fixed block.
        relationsRail = new BoardInspectorRail(null, relationsControls, null);
        scrollRelationsRailToTop();
        return boardPageSplit(relationsBoardSlot, relationsRail);
    }

    /**
     * Returns the Relations control rail to its first section after layout
     * settles.
     */
    private void scrollRelationsRailToTop() {
        if (relationsRail == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (relationsRail != null) {
                relationsRail.scrollInspectorToTop();
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
        // Same shared rail as Play (no status header, no move list): the markup
        // tools fill the canvas height instead of a fixed block.
        JComponent rail = new BoardInspectorRail(null, drawControls, null);
        return boardPageSplit(drawBoardSlot, rail);
    }

    /**
     * Creates the Study tab: the shared board on the left and the PGN-backed
     * study editor (chapters, move tree, annotations) as a wider right rail.
     * Study drives and records onto the shared board like Analyze; the board is
     * re-parented into this slot when Study activates (see configureBoardForMode).
     *
     * @return study tab
     */
    protected JComponent createStudyTab() {
        studyBoardSlot = boardSlotPanel();
        // Keep the board from collapsing when the dense study rail wants width.
        studyBoardSlot.setMinimumSize(new Dimension(320, 0));
        if (studyWorkspacePanel == null) {
            createStudyWorkspacePanel();
        }
        // A width-constrained, fill-viewport scrolled rail (same idea as
        // BoardInspectorRail): the study editor is dense, so pin the rail width
        // and let the panel fit/scroll inside it rather than letting its natural
        // width steal the board column. The editor is the working surface and the
        // board the reference, so the rail is wider than the usual board pages.
        JScrollPane studyRail = scroll(fillViewport(studyWorkspacePanel));
        studyRail.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        studyRail.setPreferredSize(new Dimension(460, 0));
        studyRail.setMinimumSize(new Dimension(380, 0));
        return SplitPaneStyler.styledHorizontalSplit(studyBoardSlot, studyRail, 0.6d);
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

}
