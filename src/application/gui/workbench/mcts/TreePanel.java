package application.gui.workbench.mcts;

import static application.gui.workbench.ui.Ui.setColumnWidth;
import application.gui.workbench.Defaults;
import application.gui.workbench.command.CommandRunner;
import application.gui.workbench.game.Positions;
import application.gui.workbench.layout.SplitPaneStyler;
import application.gui.workbench.network.Prefs;
import application.gui.workbench.network.TensorViz;
import application.gui.workbench.ui.HoldButton;
import application.gui.workbench.ui.SegmentedSwitcher;
import application.gui.workbench.ui.StatusBadge;
import application.gui.workbench.ui.SurfacePanel;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Toast;
import application.gui.workbench.ui.ToggleBox;
import application.gui.workbench.ui.Ui;
import application.gui.workbench.ui.WrappingFlowLayout;
import application.gui.workbench.ui.WorkspaceHeader;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import chess.core.Move;
import chess.core.Position;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.table.TableColumnModel;

/**
 * Engine-tab "Tree" mode: a live, zoomable view of the shared PUCT/MCTS search
 * tree with a navigation inspector and a growth scrubber.
 *
 * <p>Observes the shared {@link MctsSession}: starting a search here (or in the
 * Search tab) streams the growing tree into a {@link TreeGraphView} where every
 * node is a mini board, transpositions collapse to a single node, and childless
 * frontier siblings batch into one blob so the layout stays compact. Clicking a
 * node lists its children with their MCTS statistics in the collapsible
 * inspector; clicking a child jumps the view to it. The position to search is
 * chosen with the shared {@link Positions} picker, the controls are grouped into
 * labelled boxes, and a bottom scrubber replays the recorded tree growth frame
 * by frame. The tree can be exported to a standalone vector SVG.</p>
 */
public final class TreePanel extends SurfacePanel implements MctsSession.Listener {

    /**
     * Serialization identifier.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Minimum vertical subtree-guide layer. Zero means no vertical partitions.
     */
    private static final int GUIDE_LEVEL_MIN = 0;

    /**
     * Maximum vertical subtree-guide layer exposed by the stepper.
     */
    private static final int GUIDE_LEVEL_MAX = 64;

    /**
     * Node keys plus exact directed segments for a highlighted tree line.
     *
     * @param keys keys participating in the line
     * @param segments directed parent-child key pairs on the line
     */
    private record PathOverlay(Set<String> keys, Set<TreeGraphView.PathSegment> segments) {

        /**
         * Empty overlay.
         *
         * @return empty path overlay
         */
        private static PathOverlay empty() {
            return new PathOverlay(Set.of(), Set.of());
        }
    }

    /**
     * Caption strip height beneath each node board.
     */
    private static final int CAPTION_HEIGHT = 34;

    /**
     * Default board thumbnail size for tree nodes. The old 64px control existed
     * for pre-SVG pixelated boards; vector piece rendering is clear enough to
     * use larger thumbnails by default without exposing a manual resolution
     * knob.
     */
    private static final int NODE_BOARD_SIZE = 96;

    /**
     * Horizontal gap between sibling columns.
     */
    private static final int H_GAP = 22;

    /**
     * Vertical gap between layers.
     */
    private static final int V_GAP = 54;

    /**
     * Split weight giving most of the width to the canvas.
     */
    private static final double INSPECTOR_SPLIT = 0.76;

    /**
     * Graph-card key for the empty launch state.
     */
    private static final String TREE_CARD_EMPTY = "empty";

    /**
     * Graph-card key for the live tree canvas.
     */
    private static final String TREE_CARD_VIEW = "view";

    /**
     * Shared MCTS session.
     */
    private final transient MctsSession session;

    /**
     * Supplier for the current main-board FEN.
     */
    private final transient Supplier<String> currentFen;

    /**
     * Tree canvas.
     */
    private final TreeGraphView view = new TreeGraphView();

    /**
     * Card host that swaps the graph canvas with an actionable empty state.
     */
    private final JPanel treeCanvasCards = new JPanel(new CardLayout());

    /**
     * Workspace header for the search-tree workbench.
     */
    private final WorkspaceHeader workspaceHeader = new WorkspaceHeader("Search Tree", "", null);

    /**
     * Backend selector.
     */
    private final JComboBox<MctsSession.Backend> backendCombo =
            new JComboBox<>(MctsSession.Backend.values());

    /**
     * Position picker (shared canned positions plus "use main board").
     */
    private final JComboBox<String> positionCombo = new JComboBox<>(Positions.labels());

    /**
     * Visit-limit control.
     */
    private final JSpinner visitsSpinner = new JSpinner(new SpinnerNumberModel(
            Defaults.MCTS_VISITS, Defaults.MCTS_VISITS_MIN,
            Defaults.MCTS_VISITS_MAX, Defaults.MCTS_VISITS_STEP));

    /**
     * Exploration-constant control.
     */
    private final JSpinner cpuctSpinner = new JSpinner(new SpinnerNumberModel(
            Double.valueOf(Defaults.MCTS_CPUCT), Double.valueOf(0.05),
            Double.valueOf(8.0), Double.valueOf(0.25)));

    /**
     * Wall-clock time-limit control in milliseconds (0 = visit-limited only).
     */
    private final JSpinner millisSpinner = new JSpinner(new SpinnerNumberModel(
            Defaults.MCTS_MILLIS, Defaults.MCTS_MILLIS_MIN,
            Defaults.MCTS_MILLIS_MAX, Defaults.MCTS_MILLIS_STEP));

    /**
     * Visible-depth control.
     */
    private final JSpinner depthSpinner = new JSpinner(new SpinnerNumberModel(14, 1, 256, 1));

    /**
     * Maximum visible nodes control.
     */
    private final JSpinner maxNodesSpinner = new JSpinner(new SpinnerNumberModel(1000, 64, 250_000, 500));

    /**
     * Minimum visits filter control.
     */
    private final JSpinner minVisitsSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 100_000, 1));

    /**
     * Branching cap: maximum children shown per node (0 = all). Keeps the budget
     * flowing into depth along the relevant lines rather than a wide frontier.
     */
    private final JSpinner branchesSpinner = new JSpinner(new SpinnerNumberModel(4, 0, 64, 1));

    /**
     * Tree layer used to partition vertical guide lines; zero hides them.
     */
    private int guideLevel = 1;

    /**
     * Decrease vertical guide layer.
     */
    private final JButton guideLevelDownButton = Ui.button("-", false, event -> adjustGuideLevel(-1));

    /**
     * Increase vertical guide layer.
     */
    private final JButton guideLevelUpButton = Ui.button("+", false, event -> adjustGuideLevel(1));

    /**
     * Readout for the current vertical guide layer.
     */
    private final JLabel guideLevelLabel = new JLabel("1");

    /**
     * Start button.
     */
    private final JButton startButton = Ui.button("Start", true, event -> start());

    /**
     * Actionable empty state shown before any tree frame exists.
     */
    private final JComponent treeEmptyState =
            Ui.emptyState("No search tree yet", "Grow a PUCT tree from the selected position.",
                    Ui.button("Start search", true, event -> start()),
                    Ui.button("Copy command", false, event -> copyCliCommand()));

    /**
     * Pause button.
     */
    private final JButton pauseButton = Ui.button("Pause", false, null);

    /**
     * Resume button.
     */
    private final JButton resumeButton = Ui.button("Resume", false, null);

    /**
     * Stop button.
     */
    private final HoldButton stopButton;

    /**
     * Fit-to-view button.
     */
    private final JButton fitButton = Ui.button("Fit", false, event -> view.fit());

    /**
     * Reset zoom/pan button.
     */
    private final JButton resetViewButton = Ui.button("Reset View", false, event -> view.resetView());

    /**
     * SVG export button.
     */
    private final JButton exportSvgButton = Ui.button("Export SVG", false, event -> exportSvg());

    /**
     * Transposition-merge toggle (on by default: never show a position twice).
     */
    private final ToggleBox mergeToggle = Ui.withTooltip(
            new ToggleBox("Merge transpositions", true),
            "Collapse positions reached by different move orders into one node with dashed back-links");

    /**
     * Leaf-batching toggle (on by default: keep the frontier compact).
     */
    private final ToggleBox batchLeavesToggle = Ui.withTooltip(
            new ToggleBox("Batch leaves", true),
            "Group childless sibling positions into one blob so only branching nodes spread out");

    /**
     * Depth-level guide-lines toggle.
     */
    private final ToggleBox layersToggle = Ui.withTooltip(
            new ToggleBox("Layers", true),
            "Show horizontal ply separators and labels for the tree grid");

    /**
     * Auto-fit-while-running toggle.
     */
    private final ToggleBox autoFitToggle = Ui.withTooltip(
            new ToggleBox("Auto-fit", true),
            "Zoom the whole growing tree into view, centered, on every search frame (off keeps your pan/zoom)");

    /**
     * Details-inspector visibility toggle.
     */
    private final ToggleBox detailsToggle = Ui.withTooltip(
            new ToggleBox("Details", true),
            "Show or hide the node-inspector panel on the right");

    /**
     * Graph node rendering style.
     */
    private final SegmentedSwitcher graphStyleSwitcher = Ui.segmentedControl("Boards", "Moves");

    /**
     * Status badge.
     */
    private final StatusBadge statusBadge = new StatusBadge();

    /**
     * Inspector summary for the selected node.
     */
    private final JTextArea inspectorSummary = new JTextArea();

    /**
     * Child-statistics table model.
     */
    private final TreeChildTableModel childModel = new TreeChildTableModel();

    /**
     * Child-statistics table.
     */
    private final JTable childTable = new JTable(childModel);

    /**
     * Heading above the child table.
     */
    private final JLabel childHeading = Ui.caption("Children");

    /**
     * Clickable breadcrumb of the line from the root to the inspected node.
     */
    private final JPanel breadcrumb = Ui.transparentPanel(new WrappingFlowLayout(FlowLayout.LEFT, 3, 1));

    /**
     * Alternative-parent chips shown when a merged node is reached via several
     * move orders (transpositions).
     */
    private final JPanel altParents = Ui.transparentPanel(new WrappingFlowLayout(FlowLayout.LEFT, 3, 1));

    /**
     * Depth/level readout for the inspected node.
     */
    private final JLabel depthLabel = new JLabel(" ");

    /**
     * Visual win/draw/loss + eval bar for the inspected node.
     */
    private final TreeWdlBar wdlBar = new TreeWdlBar();

    /**
     * Split between canvas and inspector, retained for the collapse toggle.
     */
    private JSplitPane split;

    /**
     * Inspector panel, retained for the collapse toggle.
     */
    private JPanel inspector;

    /**
     * Divider size restored when the inspector is shown again.
     */
    private int defaultDividerSize = 8;

    /**
     * Last main-board FEN observed by the panel.
     */
    private String boardFen;

    /**
     * Pinned root from the position picker, or null to follow the main board.
     */
    private String overrideFen;

    /**
     * Latest snapshot node infos backing the inspector.
     */
    private transient List<MctsSearch.NodeInfo> currentInfos = List.of();

    /**
     * Inspector's selected merge key.
     */
    private String inspectorKey;

    /**
     * Inspector's selected node id (null when a blob is selected).
     */
    private String inspectorNodeId;

    /**
     * Last session-selected node id adopted by this panel. Used to distinguish a
     * real cross-surface selection change from a local blob inspection, which
     * intentionally does not update {@link MctsSession}.
     */
    private String lastSessionSelectedNodeId;

    /**
     * Graph key that should be centered once the next laid-out model contains
     * it. This bridges session selections from the table and graph clicks
     * through the synchronous snapshot refresh without turning the canvas into a
     * session-aware component.
     */
    private String pendingFocusKey;

    /**
     * Recorded search-tree growth controller.
     */
    private final TreeScrubber scrubber;

    /**
     * Callback that opens a position FEN in a new detached board, or null.
     */
    private transient Consumer<String> openInNewBoard;

    /**
     * FEN of the currently inspected node, for the "open in board" action.
     */
    private String selectedFen;

    /**
     * Opens the inspected node's position in a new board.
     */
    private final JButton openBoardButton = Ui.button("Open board", false, event -> openSelectedInBoard());

    /**
     * Target move-line input (SAN or UCI, space-separated).
     */
    private final JTextField targetField = new JTextField();

    /**
     * Target-tracking readout (time-to-find / PV agreement).
     */
    private final JLabel targetLabel = new JLabel("no target");

    /**
     * Target-line parser and status latch.
     */
    private final TreeTargetTracker targetTracker = new TreeTargetTracker();

    /**
     * Creates the panel.
     *
     * @param session shared MCTS session
     * @param currentFen current board FEN supplier
     */
    public TreePanel(MctsSession session, Supplier<String> currentFen) {
        this(session, currentFen, true);
    }

    /**
     * Creates the panel.
     *
     * @param session shared MCTS session
     * @param currentFen current board FEN supplier
     * @param showWorkspaceHeader true to show the standalone Search Tree header
     */
    public TreePanel(MctsSession session, Supplier<String> currentFen, boolean showWorkspaceHeader) {
        super(new BorderLayout(0, 0), Theme.Surface.BACKDROP);
        this.session = session;
        this.currentFen = currentFen;
        this.stopButton = new HoldButton("Stop", () -> session.stop(), true);
        this.scrubber = new TreeScrubber(session, tree -> rebuildModel(tree, null), this::renderLive);
        configureControls();
        JPanel north = Ui.transparentPanel(new BorderLayout(0, 0));
        if (showWorkspaceHeader) {
            north.add(workspaceHeader, BorderLayout.NORTH);
        }
        north.add(buildToolbar(), BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        add(scrubber.buildBar(), BorderLayout.SOUTH);
        view.setSelectionListener(this::onNodeSelected);
        installShortcuts();
        session.addListener(this);
        boardFen = currentFen.get();
        applySnapshot(session.snapshot());
    }

    /**
     * Binds scrubber keyboard shortcuts on the focused canvas: Space toggles
     * playback, Left/Right step a recorded frame, Home jumps to the first frame,
     * and End returns to the live tree.
     */
    private void installShortcuts() {
        javax.swing.InputMap keys = view.getInputMap(JComponent.WHEN_FOCUSED);
        javax.swing.ActionMap actions = view.getActionMap();
        bindKey(keys, actions, "SPACE", "tree.play", scrubber::togglePlay);
        bindKey(keys, actions, "LEFT", "tree.prev", () -> scrubber.step(-1));
        bindKey(keys, actions, "RIGHT", "tree.next", () -> scrubber.step(1));
        bindKey(keys, actions, "HOME", "tree.first", () -> scrubber.step(-session.historySize()));
        bindKey(keys, actions, "END", "tree.live", scrubber::goLive);
    }

    /**
     * Binds a keystroke to a runnable on the canvas.
     *
     * @param keys input map
     * @param actions action map
     * @param stroke key-stroke text
     * @param name action key
     * @param action action to run
     */
    private static void bindKey(javax.swing.InputMap keys, javax.swing.ActionMap actions,
            String stroke, String name, Runnable action) {
        keys.put(javax.swing.KeyStroke.getKeyStroke(stroke), name);
        actions.put(name, new javax.swing.AbstractAction() {
            /**
             * Serialization identifier for Swing compatibility.
             */
            private static final long serialVersionUID = 1L;

            /**
             * Runs the bound action.
             *
             * @param event action event
             */
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                action.run();
            }
        });
    }

    /**
     * Stops observing the shared session.
     */
    public void dispose() {
        scrubber.stopPlay();
        session.removeListener(this);
    }

    /**
     * Sets the callback that opens a node's position in a new detached board.
     *
     * @param callback open-in-new-board callback, or null to disable the action
     */
    public void setOpenInNewBoard(Consumer<String> callback) {
        this.openInNewBoard = callback;
        updateOpenButton();
    }

    /**
     * Updates the main-board FEN observed by this panel.
     *
     * @param fen FEN string
     */
    public void setBoardFen(String fen) {
        if (fen != null && !fen.isBlank()) {
            boardFen = fen;
        }
    }

    /**
     * Raises the published tree depth while the Tree mode is on screen.
     */
    @Override
    public void addNotify() {
        super.addNotify();
        applyTreeOptions();
    }

    /**
     * Restores the compact published tree once the Tree mode is hidden.
     */
    @Override
    public void removeNotify() {
        scrubber.stopPlay();
        session.setTreeOptions(null);
        super.removeNotify();
    }

    /**
     * Refreshes the panel after the shared session changes.
     *
     * @param source changed session
     */
    @Override
    public void sessionChanged(MctsSession source) {
        applySnapshot(source.snapshot());
    }

    /**
     * Applies styling and listeners to child controls.
     */
    private void configureControls() {
        Ui.styleCombo(backendCombo);
        backendCombo.setPreferredSize(new Dimension(138, Theme.CONTROL_HEIGHT));
        Ui.styleCombo(positionCombo);
        positionCombo.setPreferredSize(new Dimension(220, Theme.CONTROL_HEIGHT));
        positionCombo.setToolTipText("Search a canned position, or follow the main board");
        positionCombo.addActionListener(event -> onPositionPicked());
        Ui.styleIntegerSpinner(visitsSpinner);
        Ui.styleSpinner(cpuctSpinner);
        Ui.styleIntegerSpinner(depthSpinner);
        Ui.styleIntegerSpinner(maxNodesSpinner);
        Ui.styleIntegerSpinner(millisSpinner);
        millisSpinner.setPreferredSize(new Dimension(86, Theme.CONTROL_HEIGHT));
        millisSpinner.setToolTipText("Wall-clock time limit in ms (0 = limit by visits only)");
        Ui.styleIntegerSpinner(minVisitsSpinner);
        Ui.styleIntegerSpinner(branchesSpinner);
        branchesSpinner.setToolTipText("Max children shown per node (0 = all). Lower = a deeper, focused tree.");
        configureGuideLevelControl();
        visitsSpinner.setPreferredSize(new Dimension(92, Theme.CONTROL_HEIGHT));
        cpuctSpinner.setPreferredSize(new Dimension(72, Theme.CONTROL_HEIGHT));
        cpuctSpinner.setToolTipText("Cpuct: PUCT exploration constant. Higher values explore prior-favored moves more aggressively.");
        depthSpinner.setPreferredSize(new Dimension(66, Theme.CONTROL_HEIGHT));
        maxNodesSpinner.setPreferredSize(new Dimension(96, Theme.CONTROL_HEIGHT));
        minVisitsSpinner.setPreferredSize(new Dimension(78, Theme.CONTROL_HEIGHT));
        branchesSpinner.setPreferredSize(new Dimension(62, Theme.CONTROL_HEIGHT));
        statusBadge.setFixedTextWidth(190);
        statusBadge.idle("MCTS idle");
        pauseButton.addActionListener(event -> session.pause());
        resumeButton.addActionListener(event -> session.resume());
        depthSpinner.addChangeListener(event -> applyTreeOptions());
        maxNodesSpinner.addChangeListener(event -> applyTreeOptions());
        minVisitsSpinner.addChangeListener(event -> applyTreeOptions());
        branchesSpinner.addChangeListener(event -> applyTreeOptions());
        mergeToggle.addActionListener(event -> rebuildAndReset());
        batchLeavesToggle.addActionListener(event -> rebuildAndReset());
        detailsToggle.addActionListener(event -> setInspectorVisible(detailsToggle.isSelected()));
        layersToggle.addActionListener(event -> view.setShowLayers(layersToggle.isSelected()));
        graphStyleSwitcher.setToolTipText("Switch graph nodes between chessboard thumbnails and move-stat cards");
        graphStyleSwitcher.getAccessibleContext().setAccessibleName("Graph node style");
        graphStyleSwitcher.addActionListener(event -> applyGraphDisplayMode());
        // Default states (the ToggleBox second arg is "compact", not "selected",
        // so these must be set explicitly): merge transpositions and batch leaves
        // keep the tree compact, and Details matches the inspector being shown.
        mergeToggle.setSelected(true);
        batchLeavesToggle.setSelected(true);
        detailsToggle.setSelected(true);
        layersToggle.setSelected(true);
        view.setShowLayers(true);
        applyGraphDisplayMode();
        syncGuideLevelControl();

        depthLabel.setFont(Theme.font(11, Font.BOLD));
        depthLabel.setForeground(Theme.MUTED);
        depthLabel.setAlignmentX(LEFT_ALIGNMENT);
        breadcrumb.setAlignmentX(LEFT_ALIGNMENT);
        breadcrumb.setMaximumSize(new Dimension(Integer.MAX_VALUE, 240));
        altParents.setAlignmentX(LEFT_ALIGNMENT);
        altParents.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));

        Ui.styleFields(targetField);
        Ui.placeholder(targetField, "e.g. Qh8 c5 ...");
        targetField.setPreferredSize(new Dimension(190, Theme.CONTROL_HEIGHT));
        targetField.setToolTipText(
                "Track a move line (SAN or UCI). Watch the search reach it and how long it takes to find it.");
        targetField.addActionListener(event -> onTargetChanged());
        targetLabel.setFont(Theme.mono(11));
        targetLabel.setForeground(Theme.MUTED);
        fitButton.setToolTipText("Fit the full search tree into the canvas");
        resetViewButton.setToolTipText("Reset tree zoom and pan to the default view");
        openBoardButton.setToolTipText("Open the inspected node's position in a new board");

        inspectorSummary.setEditable(false);
        inspectorSummary.setOpaque(true);
        inspectorSummary.setBackground(Theme.PANEL_SOLID);
        inspectorSummary.setForeground(Theme.TEXT);
        inspectorSummary.setFont(Theme.mono(12));
        inspectorSummary.setLineWrap(true);
        inspectorSummary.setWrapStyleWord(true);
        inspectorSummary.setBorder(Theme.pad(8));
        inspectorSummary.setText("No node selected");

        Theme.table(childTable, Theme.TABLE_ROW_HEIGHT);
        childTable.setAutoCreateRowSorter(true);
        childTable.setFillsViewportHeight(true);
        childTable.addMouseListener(new java.awt.event.MouseAdapter() {
            /**
             * Handles presses on the child table so a row can become the inspected
             * tree node.
             *
             * @param event mouse event
             */
            @Override
            public void mousePressed(java.awt.event.MouseEvent event) {
                childClicked(event);
            }
        });
        TableColumnModel columns = childTable.getColumnModel();
        setColumnWidth(columns, 0, 64);
        setColumnWidth(columns, 1, 64);
        setColumnWidth(columns, 2, 56);
        setColumnWidth(columns, 3, 60);
        setColumnWidth(columns, 4, 56);
        setColumnWidth(columns, 5, 60);
        // Draw the move column with inline figurine piece artwork.
        columns.getColumn(0).setCellRenderer(new application.gui.workbench.game.SanRenderer());
    }

    /**
     * Styles the custom vertical-guide level stepper.
     */
    private void configureGuideLevelControl() {
        Dimension buttonSize = new Dimension(32, Theme.CONTROL_HEIGHT);
        guideLevelDownButton.setPreferredSize(buttonSize);
        guideLevelDownButton.setMinimumSize(buttonSize);
        guideLevelDownButton.setToolTipText("Move guide level down. Level 0 hides vertical subtree dividers.");
        guideLevelDownButton.getAccessibleContext().setAccessibleName("Decrease guide level");
        guideLevelUpButton.setPreferredSize(buttonSize);
        guideLevelUpButton.setMinimumSize(buttonSize);
        guideLevelUpButton.setToolTipText("Move guide level up to partition by a deeper tree layer.");
        guideLevelUpButton.getAccessibleContext().setAccessibleName("Increase guide level");

        guideLevelLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        guideLevelLabel.setOpaque(true);
        guideLevelLabel.setBackground(Theme.INPUT);
        guideLevelLabel.setForeground(Theme.TEXT);
        guideLevelLabel.setFont(Theme.font(Theme.FONT_CONTROL, Font.BOLD));
        guideLevelLabel.setBorder(Theme.lineBorder(Theme.INPUT_BORDER));
        guideLevelLabel.setPreferredSize(new Dimension(48, Theme.CONTROL_HEIGHT));
        guideLevelLabel.setMinimumSize(new Dimension(48, Theme.CONTROL_HEIGHT));
        guideLevelLabel.setToolTipText("Vertical guide level. Off means no vertical subtree dividers.");
        guideLevelLabel.getAccessibleContext().setAccessibleName("Guide level");
    }

    /**
     * Builds the vertical-guide level stepper control.
     *
     * @return labelled guide level control
     */
    private JComponent guideLevelControl() {
        JPanel stepper = Ui.transparentPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_XS, 0));
        stepper.add(guideLevelDownButton);
        stepper.add(guideLevelLabel);
        stepper.add(guideLevelUpButton);
        return Ui.labeledControl("Guide", stepper);
    }

    /**
     * Applies the selected graph node style to the canvas.
     */
    private void applyGraphDisplayMode() {
        TreeGraphView.DisplayMode mode = graphStyleSwitcher.getSelectedIndex() == 1
                ? TreeGraphView.DisplayMode.MOVES
                : TreeGraphView.DisplayMode.BOARDS;
        view.setDisplayMode(mode);
    }

    /**
     * Adjusts the vertical-guide layer.
     *
     * @param delta level delta
     */
    private void adjustGuideLevel(int delta) {
        guideLevel = Math.max(GUIDE_LEVEL_MIN, Math.min(GUIDE_LEVEL_MAX, guideLevel + delta));
        syncGuideLevelControl();
    }

    /**
     * Pushes the guide level into the graph view and refreshes the stepper state.
     */
    private void syncGuideLevelControl() {
        guideLevel = Math.max(GUIDE_LEVEL_MIN, Math.min(GUIDE_LEVEL_MAX, guideLevel));
        guideLevelLabel.setText(guideLevel == GUIDE_LEVEL_MIN ? "Off" : Integer.toString(guideLevel));
        guideLevelDownButton.setEnabled(guideLevel > GUIDE_LEVEL_MIN);
        guideLevelUpButton.setEnabled(guideLevel < GUIDE_LEVEL_MAX);
        view.setGuidePartitionLayer(guideLevel);
    }

    /**
     * Builds the Tree control toolbar as one plain wrapped band. Primary search
     * controls are added first so they remain visible whenever the toolbar wraps.
     *
     * @return toolbar component
     */
    private JComponent buildToolbar() {
        JPanel bar = Ui.transparentPanel(null);
        bar.setLayout(new BoxLayout(bar, BoxLayout.Y_AXIS));
        bar.setOpaque(true);
        bar.setBackground(Theme.PANEL_SOLID);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE),
                Theme.pad(Theme.SPACE_XS, Theme.SPACE_SM, Theme.SPACE_XS, Theme.SPACE_SM)));

        JPanel searchRow = toolbarRow();
        searchRow.add(startButton);
        searchRow.add(pauseButton);
        searchRow.add(resumeButton);
        searchRow.add(stopButton);
        searchRow.add(statusBadge);
        searchRow.add(Ui.labeledControl("Backend", backendCombo));
        searchRow.add(Ui.labeledControl("Position", positionCombo));
        searchRow.add(Ui.labeledControl("Visits", visitsSpinner));
        searchRow.add(Ui.labeledControl("Millis", millisSpinner));

        JPanel viewRow = toolbarRow();
        viewRow.add(Ui.labeledControl("Graph", graphStyleSwitcher));
        viewRow.add(detailsToggle);
        viewRow.add(fitButton);
        viewRow.add(resetViewButton);
        viewRow.add(Ui.button("Copy command", false, event -> copyCliCommand()));

        JPanel advancedRow = toolbarRow();
        advancedRow.add(Ui.labeledControl("Cpuct", cpuctSpinner));
        advancedRow.add(Ui.labeledControl("Target", targetField));
        advancedRow.add(targetLabel);
        advancedRow.add(Ui.labeledControl("Depth", depthSpinner));
        advancedRow.add(Ui.labeledControl("Max nodes", maxNodesSpinner));
        advancedRow.add(Ui.labeledControl("Min visits", minVisitsSpinner));
        advancedRow.add(Ui.labeledControl("Branches", branchesSpinner));
        advancedRow.add(mergeToggle);
        advancedRow.add(batchLeavesToggle);
        advancedRow.add(layersToggle);
        advancedRow.add(guideLevelControl());
        advancedRow.add(autoFitToggle);
        advancedRow.add(openBoardButton);
        advancedRow.add(exportSvgButton);

        bar.add(searchRow);
        bar.add(viewRow);
        bar.add(Ui.collapsible("Advanced search / graph settings", advancedRow, false));
        return bar;
    }

    /**
     * Creates one compact toolbar row.
     *
     * @return wrapping row
     */
    private static JPanel toolbarRow() {
        JPanel row = Ui.transparentPanel(new WrappingFlowLayout(FlowLayout.LEFT, Theme.SPACE_SM, Theme.SPACE_XS));
        row.setAlignmentX(LEFT_ALIGNMENT);
        return row;
    }

    /**
     * Builds the canvas + navigation-inspector split.
     *
     * @return body component
     */
    private JComponent buildBody() {
        inspector = new JPanel(new BorderLayout(0, Theme.SPACE_SM));
        inspector.setOpaque(true);
        inspector.setBackground(Theme.PANEL_SOLID);
        inspector.setBorder(Theme.pad(Theme.SPACE_SM));
        inspector.setPreferredSize(new Dimension(340, 0));
        JScrollPane summaryScroll = Ui.scroll(inspectorSummary, () -> Theme.PANEL_SOLID);
        summaryScroll.setPreferredSize(new Dimension(320, 120));
        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        childHeading.setAlignmentX(LEFT_ALIGNMENT);
        wdlBar.setAlignmentX(LEFT_ALIGNMENT);
        wdlBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        head.add(breadcrumb);
        head.add(altParents);
        head.add(depthLabel);
        head.add(Box.createVerticalStrut(Theme.SPACE_SM));
        head.add(childHeading);
        head.add(Box.createVerticalStrut(Theme.SPACE_XS));
        head.add(wdlBar);
        JPanel north = new JPanel(new BorderLayout(0, Theme.SPACE_XS));
        north.setOpaque(false);
        north.add(head, BorderLayout.NORTH);
        north.add(summaryScroll, BorderLayout.CENTER);
        inspector.add(north, BorderLayout.NORTH);
        inspector.add(Ui.scroll(childTable, () -> Theme.PANEL_SOLID), BorderLayout.CENTER);
        inspector.add(Ui.collapsible("Legend", createTreeLegend(), false), BorderLayout.SOUTH);

        treeCanvasCards.setOpaque(true);
        treeCanvasCards.setBackground(Theme.BG);
        treeCanvasCards.add(treeEmptyState, TREE_CARD_EMPTY);
        treeCanvasCards.add(view, TREE_CARD_VIEW);
        updateTreeCanvasCard(false);

        split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeCanvasCards, inspector);
        split.setResizeWeight(INSPECTOR_SPLIT);
        split.setBorder(BorderFactory.createEmptyBorder());
        SplitPaneStyler.style(split);
        defaultDividerSize = Math.max(1, split.getDividerSize());
        return split;
    }

    /**
     * Handles a position-picker change: pin the chosen root (or follow the main
     * board) for the next search, and reflect it on the idle session root.
     */
    private void onPositionPicked() {
        String label = (String) positionCombo.getSelectedItem();
        overrideFen = Positions.fenFor(label);
        if (!session.snapshot().running()) {
            String root = overrideFen != null ? overrideFen : boardFen;
            if (root != null && !root.isBlank()) {
                session.requestRootFen(root, false);
            }
        }
    }

    /**
     * Starts a search from the current controls and chosen position.
     */
    private void start() {
        String fresh = currentFen.get();
        if (fresh != null && !fresh.isBlank()) {
            boardFen = fresh;
        }
        String rootFen;
        if (overrideFen != null && !overrideFen.isBlank()) {
            rootFen = overrideFen;
        } else if (boardFen != null && !boardFen.isBlank()) {
            rootFen = boardFen;
        } else {
            rootFen = session.snapshot().rootFen();
        }
        // Normalize to the same canonical FEN the session publishes, so the
        // target line is parsed against the exact root that snapshots report
        // (avoids a redundant re-parse on the first frame).
        try {
            rootFen = new Position(rootFen).toString();
        } catch (RuntimeException ex) {
            // Keep the raw FEN if it cannot be parsed.
        }
        applyTreeOptions();
        scrubber.stopPlay();
        targetTracker.resetTracking();
        targetTracker.parse(rootFen, targetField.getText());
        session.start(new MctsSession.Config(
                rootFen,
                (MctsSession.Backend) backendCombo.getSelectedItem(),
                ((Number) visitsSpinner.getValue()).intValue(),
                ((Number) millisSpinner.getValue()).longValue(),
                ((Number) cpuctSpinner.getValue()).doubleValue(),
                true));
    }

    /**
     * Pushes the current depth/node/visit controls onto the shared session.
     */
    private void applyTreeOptions() {
        session.setTreeOptions(new MctsSearch.TreeOptions(
                ((Number) depthSpinner.getValue()).intValue(),
                ((Number) maxNodesSpinner.getValue()).intValue(),
                ((Number) minVisitsSpinner.getValue()).intValue(),
                ((Number) branchesSpinner.getValue()).intValue(),
                false));
    }

    /**
     * Rebuilds the active frame (live or scrubbed) and resets the view.
     */
    private void rebuildAndReset() {
        if (scrubber.isScrubbing()) {
            scrubber.renderCurrentFrame();
        } else {
            renderLive(session.snapshot());
        }
        view.resetView();
    }

    /**
     * Applies the latest session snapshot to controls, scrubber, and canvas.
     *
     * @param snapshot session snapshot
     */
    private void applySnapshot(MctsSession.Snapshot snapshot) {
        updateButtons(snapshot);
        updateStatus(snapshot);
        updateWorkspaceHeader(snapshot);
        scrubber.updateControls();
        updateTargetStatus(snapshot);
        if (!scrubber.isScrubbing()) {
            renderLive(snapshot);
        }
    }

    /**
     * Renders the live, growing tree and (optionally) keeps it fitted.
     *
     * @param snapshot session snapshot
     */
    private void renderLive(MctsSession.Snapshot snapshot) {
        MctsSearch.TreeSnapshot tree = snapshot == null ? null : snapshot.tree();
        boolean selectedFocusApplied = rebuildModel(tree, snapshot);
        if (!selectedFocusApplied && autoFitToggle.isSelected() && snapshot != null && snapshot.running()) {
            view.fit();
        }
    }

    /**
     * Rebuilds the tree layout from a snapshot and refreshes the inspector.
     *
     * @param tree tree snapshot to render, or null for empty
     * @param pathSnapshot live session snapshot whose exploring line is overlaid
     *     as the search path, or null (scrubbing) to show no live path
     * @return true when the tree layout from a snapshot and refreshes the inspector was rebuilt
     */
    private boolean rebuildModel(MctsSearch.TreeSnapshot tree, MctsSession.Snapshot pathSnapshot) {
        if (tree == null) {
            // No tree (idle / starting / stopped): clear the canvas AND the
            // inspector, so stale node statistics from a prior search don't
            // linger while the next search spins up.
            currentInfos = List.of();
            inspectorKey = null;
            inspectorNodeId = null;
            lastSessionSelectedNodeId = null;
            pendingFocusKey = null;
            inspectorSummary.setText("No node selected");
            inspectorSummary.setCaretPosition(0);
            childHeading.setText("Children");
            setChildRows(List.of(), 1);
            wdlBar.clear();
            clearNav();
            view.clear();
            view.setSearchPath(Set.of(), Set.of());
            view.setTargetPath(Set.of(), Set.of(), false);
            view.setSelectedPath(Set.of(), Set.of());
            updateTreeCanvasCard(false);
            return false;
        }
        currentInfos = tree.nodes();
        String selectedId = tree.selectedNode() == null ? null : tree.selectedNode().id();
        if (selectedId != null && !selectedId.equals(lastSessionSelectedNodeId)) {
            inspectorNodeId = selectedId;
            inspectorKey = null;
            lastSessionSelectedNodeId = selectedId;
            MctsSearch.NodeInfo selected = findById(selectedId);
            if (selected != null) {
                pendingFocusKey = graphKey(selected);
            }
        }
        String activeSelectedId = inspectorNodeId != null ? inspectorNodeId : selectedId;
        TreeLayout.Model model = TreeLayout.layout(currentInfos, mergeToggle.isSelected(),
                batchLeavesToggle.isSelected(), activeSelectedId, NODE_BOARD_SIZE, NODE_BOARD_SIZE + CAPTION_HEIGHT,
                H_GAP, V_GAP, tree.omittedNodes());
        view.setModel(model);
        updateTreeCanvasCard(!model.isEmpty());
        // Compute the search-path and target overlays after currentInfos is
        // updated, so they map the live exploring line / target line against the
        // frame just rendered.
        PathOverlay searchPath = searchPathFor(pathSnapshot);
        PathOverlay targetPath = targetPathOverlay();
        PathOverlay selectedPath = selectedPathOverlay(activeSelectedId);
        view.setSearchPath(searchPath.keys(), searchPath.segments());
        view.setTargetPath(targetPath.keys(), targetPath.segments(), targetTracker.hasMoves());
        view.setSelectedPath(selectedPath.keys(), selectedPath.segments());
        boolean selectedFocusApplied = applyPendingFocus(model);
        if (inspectorKey == null) {
            showInitialInspectorNode(model, activeSelectedId);
        } else {
            refreshInspectorAfterRebuild();
        }
        return selectedFocusApplied;
    }

    /**
     * Shows either the graph canvas or the centered launch state.
     *
     * @param hasTree true when a non-empty tree model is available
     */
    private void updateTreeCanvasCard(boolean hasTree) {
        CardLayout layout = (CardLayout) treeCanvasCards.getLayout();
        layout.show(treeCanvasCards, hasTree ? TREE_CARD_VIEW : TREE_CARD_EMPTY);
    }

    /**
     * Populates the inspector after a model rebuild when no local inspector key
     * exists yet. An explicit session selection wins; otherwise the root is shown
     * so children are immediately listed.
     *
     * @param model current layout model
     * @param selectedId selected node id, or null
     */
    private void showInitialInspectorNode(TreeLayout.Model model, String selectedId) {
        if (selectedId != null && !selectedId.isBlank()) {
            MctsSearch.NodeInfo selected = findById(selectedId);
            if (selected != null) {
                showNodeInInspector(selected);
                return;
            }
        }
        for (TreeLayout.Node node : model.nodes()) {
            if (node.root()) {
                showNodeInInspector(node.info());
                break;
            }
        }
    }

    /**
     * Re-derives the inspector contents for the currently selected key against
     * the latest snapshot so its statistics track the live search.
     */
    private void refreshInspectorAfterRebuild() {
        if (inspectorNodeId != null) {
            MctsSearch.NodeInfo info = findById(inspectorNodeId);
            if (info != null) {
                showNodeInInspector(info);
            }
        } else if (inspectorKey != null) {
            for (TreeLayout.Node node : view.model().nodes()) {
                if (node.key().equals(inspectorKey) && node.blob()) {
                    showBlobInInspector(node);
                    return;
                }
            }
        }
    }

    /**
     * Opens the inspected node's position in a new board, if a callback is set.
     */
    private void openSelectedInBoard() {
        if (openInNewBoard != null && selectedFen != null && !selectedFen.isBlank()) {
            openInNewBoard.accept(selectedFen);
        }
    }

    /**
     * Enables the open-in-board button when there is a position and a callback.
     */
    private void updateOpenButton() {
        openBoardButton.setEnabled(openInNewBoard != null && selectedFen != null && !selectedFen.isBlank());
    }

    /**
     * Re-parses the target line when the user edits the field, refreshing the
     * overlay and readout immediately.
     */
    private void onTargetChanged() {
        targetTracker.parse(session.snapshot().rootFen(), targetField.getText());
        targetTracker.resetTracking();
        if (scrubber.isScrubbing()) {
            scrubber.renderCurrentFrame();
        } else {
            renderLive(session.snapshot());
        }
        updateTargetStatus(session.snapshot());
    }

    /**
     * Returns the contiguous target-line prefix that currently exists in the
     * tree, for the teal overlay.
     *
     * @return target path present in the current frame
     */
    private PathOverlay targetPathOverlay() {
        short[] targetMoves = targetTracker.moves();
        if (targetMoves.length == 0 || currentInfos.isEmpty()) {
            return PathOverlay.empty();
        }
        return pathOverlayForMoveLine(targetMoves);
    }

    /**
     * Builds a path overlay from a root-relative move line.
     *
     * @param moves encoded move line
     * @return visible path prefix and exact directed edge segments
     */
    private PathOverlay pathOverlayForMoveLine(short[] moves) {
        if (currentInfos.isEmpty()) {
            return PathOverlay.empty();
        }
        boolean merge = mergeToggle.isSelected();
        java.util.Map<String, MctsSearch.NodeInfo> idToNode = new java.util.HashMap<>();
        for (MctsSearch.NodeInfo n : currentInfos) {
            idToNode.put(n.id(), n);
        }
        MctsSearch.NodeInfo previous = rootInfo();
        if (previous == null) {
            return PathOverlay.empty();
        }
        Set<String> keys = new java.util.LinkedHashSet<>();
        Set<TreeGraphView.PathSegment> segments = new java.util.LinkedHashSet<>();
        keys.add(nodeKey(previous, merge));
        if (moves == null) {
            return new PathOverlay(keys, segments);
        }
        StringBuilder id = new StringBuilder();
        for (short move : moves) {
            if (move == Move.NO_MOVE) {
                continue;
            }
            if (id.length() > 0) {
                id.append(' ');
            }
            id.append(Move.toString(move));
            MctsSearch.NodeInfo node = idToNode.get(id.toString());
            if (node == null) {
                break;
            }
            String fromKey = nodeKey(previous, merge);
            String toKey = nodeKey(node, merge);
            keys.add(toKey);
            if (!fromKey.equals(toKey)) {
                segments.add(new TreeGraphView.PathSegment(fromKey, toKey));
            }
            previous = node;
        }
        return new PathOverlay(keys, segments);
    }

    /**
     * Returns the current snapshot root row.
     *
     * @return root node info, or null
     */
    private MctsSearch.NodeInfo rootInfo() {
        for (MctsSearch.NodeInfo n : currentInfos) {
            if (n.parentId() == null || n.parentId().isEmpty()) {
                return n;
            }
        }
        return null;
    }

    /**
     * Returns the visible layout key for a node in the current merge mode.
     *
     * @param info node info
     * @param merge whether transposition merging is enabled
     * @return node layout key
     */
    private static String nodeKey(MctsSearch.NodeInfo info, boolean merge) {
        return merge ? Long.toString(info.signature()) : info.id();
    }

    /**
     * Updates the target-tracking readout from the live snapshot, latching the
     * first time the search makes the target's first move its best move.
     *
     * @param snapshot session snapshot
     */
    private void updateTargetStatus(MctsSession.Snapshot snapshot) {
        targetLabel.setText(targetTracker.updateStatus(snapshot, targetField.getText()));
    }

    /**
     * Shows or hides the inspector panel, collapsing the split when hidden.
     *
     * @param visible true to show the inspector
     */
    private void setInspectorVisible(boolean visible) {
        if (inspector == null || split == null) {
            return;
        }
        inspector.setVisible(visible);
        split.setDividerSize(visible ? defaultDividerSize : 0);
        split.setResizeWeight(visible ? INSPECTOR_SPLIT : 1.0);
        split.setDividerLocation(visible ? INSPECTOR_SPLIT : 1.0);
        split.revalidate();
        split.repaint();
    }

    /**
     * Handles a canvas node selection.
     *
     * @param node selected node
     */
    private void onNodeSelected(TreeLayout.Node node) {
        if (node.blob()) {
            view.focusNode(node.key());
            showBlobInInspector(node);
        } else {
            // Populate the inspector (sets inspectorKey) before pushing the
            // session selection: setSelectedNodeId re-enters rebuildModel
            // synchronously, and it must see the new key to take the refresh
            // path rather than re-selecting and recursing.
            requestSelectionFocus(node.key());
            showNodeInInspector(node.info());
            session.setSelectedNodeId(node.info().id());
        }
    }

    /**
     * Shows a normal node and its children in the inspector.
     *
     * @param info node info
     */
    private void showNodeInInspector(MctsSearch.NodeInfo info) {
        inspectorNodeId = info.id();
        inspectorKey = graphKey(info);
        selectedFen = info.fen();
        updateOpenButton();
        wdlBar.set(info.win(), info.draw(), info.loss(), info.q());
        List<MctsSearch.NodeInfo> children =
                TreeLayout.childrenOf(currentInfos, inspectorKey, mergeToggle.isSelected());
        inspectorSummary.setText(nodeSummary(info, children.size()));
        inspectorSummary.setCaretPosition(0);
        childHeading.setText(children.isEmpty() ? "No children (leaf)" : "Children · " + children.size());
        setChildRows(children, Math.max(1, info.visits()));
        buildNav(info);
    }

    /**
     * Shows a batched-leaf blob and its members in the inspector.
     *
     * @param node blob node
     */
    private void showBlobInInspector(TreeLayout.Node node) {
        inspectorNodeId = null;
        inspectorKey = node.key();
        List<MctsSearch.NodeInfo> members = node.members();
        selectedFen = members.isEmpty() ? null : members.get(0).fen();
        updateOpenButton();
        wdlBar.clear();
        clearNav();
        int total = 0;
        for (MctsSearch.NodeInfo m : members) {
            total += m.visits();
        }
        inspectorSummary.setText("Batched leaves\n"
                + members.size() + " childless positions\n"
                + "combined visits: " + String.format("%,d", total)
                + "\n\nThese frontier positions share the blob to keep the tree compact."
                + " Pick one to inspect it.");
        inspectorSummary.setCaretPosition(0);
        childHeading.setText("Leaves · " + members.size());
        setChildRows(members, Math.max(1, total));
    }

    /**
     * Replaces the child-table rows.
     *
     * @param rows child rows
     * @param shareBase visits used as the N% denominator
     */
    private void setChildRows(List<MctsSearch.NodeInfo> rows, int shareBase) {
        childModel.setRows(rows, shareBase);
        childTable.clearSelection();
    }

    /**
     * Handles a single click on a child row: navigate to that node immediately.
     *
     * @param event mouse event
     */
    private void childClicked(java.awt.event.MouseEvent event) {
        int viewRow = childTable.rowAtPoint(event.getPoint());
        if (viewRow < 0) {
            return;
        }
        MctsSearch.NodeInfo info = childModel.row(childTable.convertRowIndexToModel(viewRow));
        if (info != null) {
            navigateTo(info);
        }
    }

    /**
     * Selects a node from the inspector (a child row or a breadcrumb crumb):
     * drills the inspector, centers the canvas on it, and pushes the session
     * selection.
     *
     * @param info node to select
     */
    private void navigateTo(MctsSearch.NodeInfo info) {
        String key = graphKey(info);
        showNodeInInspector(info);
        requestSelectionFocus(key);
        session.setSelectedNodeId(info.id());
    }

    /**
     * Returns the graph key for a node under the current merge setting.
     *
     * @param info node info
     * @return visible graph key before leaf batching is applied
     */
    private String graphKey(MctsSearch.NodeInfo info) {
        return mergeToggle.isSelected() ? Long.toString(info.signature()) : info.id();
    }

    /**
     * Centers a selection immediately when possible and carries the same request
     * through the next model refresh, where merge/batching may shift the node's
     * rendered position.
     *
     * @param key graph key to center
     */
    private void requestSelectionFocus(String key) {
        pendingFocusKey = key;
        view.focusNode(key);
    }

    /**
     * Applies a pending selected-node focus to the current model, falling back to
     * the model's selected visible node when leaf batching replaced the original
     * requested key with a blob.
     *
     * @param model current layout model
     * @return true when a visible node was centered
     */
    private boolean applyPendingFocus(TreeLayout.Model model) {
        if (pendingFocusKey == null) {
            return false;
        }
        String focusKey = resolvedFocusKey(model, pendingFocusKey);
        if (!view.focusNode(focusKey)) {
            return false;
        }
        pendingFocusKey = null;
        return true;
    }

    /**
     * Resolves the pending focus key against the visible layout model.
     *
     * @param model current layout model
     * @param requestedKey graph key requested before layout
     * @return visible node key to focus
     */
    private static String resolvedFocusKey(TreeLayout.Model model, String requestedKey) {
        for (TreeLayout.Node node : model.nodes()) {
            if (node.key().equals(requestedKey)) {
                return requestedKey;
            }
        }
        for (TreeLayout.Node node : model.nodes()) {
            if (node.selected()) {
                return node.key();
            }
        }
        return requestedKey;
    }

    /**
     * Rebuilds the inspector's breadcrumb, transposition-parent chips, and depth
     * readout for the inspected node.
     *
     * @param info inspected node
     */
    private void buildNav(MctsSearch.NodeInfo info) {
        breadcrumb.removeAll();
        altParents.removeAll();
        java.util.List<MctsSearch.NodeInfo> chain = new ArrayList<>();
        MctsSearch.NodeInfo cur = info;
        int guard = 0;
        while (cur != null && guard++ < 600) {
            chain.add(cur);
            String pid = cur.parentId();
            cur = pid == null || pid.isEmpty() ? null : findById(pid);
        }
        java.util.Collections.reverse(chain);
        for (int i = 0; i < chain.size(); i++) {
            MctsSearch.NodeInfo n = chain.get(i);
            if (i > 0) {
                breadcrumb.add(crumbSeparator());
            }
            breadcrumb.add(crumbLabel(i == 0 ? "root" : crumbText(n), n, i == chain.size() - 1));
        }
        java.util.List<MctsSearch.NodeInfo> parents = parentsOf(info);
        if (parents.size() > 1) {
            JLabel also = new JLabel("also reached from:");
            also.setFont(Theme.font(10, Font.PLAIN));
            also.setForeground(Theme.MUTED);
            altParents.add(also);
            for (MctsSearch.NodeInfo parent : parents) {
                altParents.add(crumbLabel(crumbText(parent), parent, false));
            }
        }
        int depth = info.depth();
        depthLabel.setText(depth == 0 ? "Root position"
                : depth + (depth == 1 ? " move" : " moves") + " from root  ·  depth " + depth);
        breadcrumb.revalidate();
        breadcrumb.repaint();
        altParents.revalidate();
        altParents.repaint();
    }

    /**
     * Clears the inspector navigation (no node / batched leaves selected).
     */
    private void clearNav() {
        breadcrumb.removeAll();
        altParents.removeAll();
        depthLabel.setText(" ");
        breadcrumb.revalidate();
        breadcrumb.repaint();
        altParents.revalidate();
        altParents.repaint();
    }

    /**
     * Returns a crumb's display text (its move SAN, or UCI).
     *
     * @param node tree node
     * @return crumb text
     */
    private static String crumbText(MctsSearch.NodeInfo node) {
        if (node.san() != null && !node.san().isBlank()) {
            return node.san();
        }
        return node.uci() == null || node.uci().isBlank() ? "?" : node.uci();
    }

    /**
     * Returns a breadcrumb separator label.
     *
     * @return separator
     */
    private JLabel crumbSeparator() {
        JLabel separator = new JLabel("/");
        separator.setFont(Theme.font(11, Font.PLAIN));
        separator.setForeground(Theme.MUTED);
        return separator;
    }

    /**
     * Builds one clickable breadcrumb crumb.
     *
     * @param text crumb text
     * @param target node to select on click
     * @param current true for the inspected node (bold, non-clickable)
     * @return crumb label
     */
    private JLabel crumbLabel(String text, MctsSearch.NodeInfo target, boolean current) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.font(11, current ? Font.BOLD : Font.PLAIN));
        label.setForeground(current ? Theme.TEXT : Theme.ACCENT);
        if (!current) {
            label.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            label.addMouseListener(new java.awt.event.MouseAdapter() {
                /**
                 * Navigates from a breadcrumb crumb to its corresponding tree node.
                 *
                 * @param event mouse event
                 */
                @Override
                public void mouseClicked(java.awt.event.MouseEvent event) {
                    navigateTo(target);
                }
            });
        }
        return label;
    }

    /**
     * Returns the distinct parents of a node — more than one when a merged node
     * is reached by several move orders (transpositions).
     *
     * @param info node
     * @return representative parent infos
     */
    private java.util.List<MctsSearch.NodeInfo> parentsOf(MctsSearch.NodeInfo info) {
        boolean merge = mergeToggle.isSelected();
        String selKey = merge ? Long.toString(info.signature()) : info.id();
        java.util.Map<String, MctsSearch.NodeInfo> idToNode = new java.util.HashMap<>();
        for (MctsSearch.NodeInfo n : currentInfos) {
            idToNode.put(n.id(), n);
        }
        java.util.LinkedHashMap<String, MctsSearch.NodeInfo> parents = new java.util.LinkedHashMap<>();
        for (MctsSearch.NodeInfo m : currentInfos) {
            String mKey = merge ? Long.toString(m.signature()) : m.id();
            if (!mKey.equals(selKey)) {
                continue;
            }
            String pid = m.parentId();
            if (pid == null || pid.isEmpty()) {
                continue;
            }
            MctsSearch.NodeInfo parent = idToNode.get(pid);
            if (parent != null) {
                parents.putIfAbsent(merge ? Long.toString(parent.signature()) : parent.id(), parent);
            }
        }
        return new ArrayList<>(parents.values());
    }

    /**
     * Computes the keys on the live search path (root to the leaf currently
     * being evaluated) for the canvas overlay. Empty unless a search is running.
     *
     * @param snapshot session snapshot
     * @return node keys on the current search path
     */
    private PathOverlay searchPathFor(MctsSession.Snapshot snapshot) {
        if (snapshot == null || !snapshot.running() || snapshot.root() == null) {
            return PathOverlay.empty();
        }
        short[] line = snapshot.root().exploringLine();
        return pathOverlayForMoveLine(line);
    }

    /**
     * Computes the keys on the path from the root to the selected inspector
     * node, so the canvas can draw a persistent selected-path overlay.
     *
     * @param selectedId selected node id
     * @return node keys from selected node back through its ancestors
     */
    private PathOverlay selectedPathOverlay(String selectedId) {
        if (selectedId == null || selectedId.isBlank() || currentInfos.isEmpty()) {
            return PathOverlay.empty();
        }
        boolean merge = mergeToggle.isSelected();
        java.util.Map<String, MctsSearch.NodeInfo> idToNode = new java.util.HashMap<>();
        for (MctsSearch.NodeInfo n : currentInfos) {
            idToNode.put(n.id(), n);
        }
        MctsSearch.NodeInfo cursor = idToNode.get(selectedId);
        if (cursor == null) {
            return PathOverlay.empty();
        }
        java.util.List<MctsSearch.NodeInfo> reversed = new ArrayList<>();
        int guard = 0;
        while (cursor != null && guard++ < 600) {
            reversed.add(cursor);
            String parentId = cursor.parentId();
            cursor = parentId == null || parentId.isEmpty() ? null : idToNode.get(parentId);
        }
        Set<String> keys = new java.util.LinkedHashSet<>();
        Set<TreeGraphView.PathSegment> segments = new java.util.LinkedHashSet<>();
        MctsSearch.NodeInfo previous = null;
        for (int i = reversed.size() - 1; i >= 0; i--) {
            MctsSearch.NodeInfo node = reversed.get(i);
            String key = nodeKey(node, merge);
            keys.add(key);
            if (previous != null) {
                String fromKey = nodeKey(previous, merge);
                if (!fromKey.equals(key)) {
                    segments.add(new TreeGraphView.PathSegment(fromKey, key));
                }
            }
            previous = node;
        }
        return new PathOverlay(keys, segments);
    }

    /**
     * Finds a node info by id in the current snapshot.
     *
     * @param id node id
     * @return node info, or null
     */
    private MctsSearch.NodeInfo findById(String id) {
        for (MctsSearch.NodeInfo info : currentInfos) {
            if (info.id().equals(id)) {
                return info;
            }
        }
        return null;
    }

    /**
     * Builds the selected-node summary text.
     *
     * @param info node info
     * @param childCount number of children
     * @return summary text
     */
    private static String nodeSummary(MctsSearch.NodeInfo info, int childCount) {
        String line = info.lineSan() == null || info.lineSan().isBlank() ? "root" : info.lineSan();
        String state = info.terminalState() == null || info.terminalState().isBlank()
                ? "" : "   " + info.terminalState();
        return "Line: " + line + '\n'
                + "Visits: " + String.format("%,d", info.visits())
                + "   Children: " + childCount + '\n'
                + String.format("Q %+.3f   Prior %.1f%%   U %.3f   PUCT %+.3f%s%n",
                        info.q(), info.prior() * 100.0, info.u(), info.score(), state)
                + "FEN: " + info.fen();
    }

    /**
     * Updates lifecycle buttons from session state.
     *
     * @param snapshot session snapshot
     */
    private void updateButtons(MctsSession.Snapshot snapshot) {
        boolean running = snapshot.state() == MctsSession.State.RUNNING
                || snapshot.state() == MctsSession.State.PAUSED
                || snapshot.state() == MctsSession.State.STARTING;
        boolean paused = snapshot.state() == MctsSession.State.PAUSED;
        startButton.setEnabled(!running);
        pauseButton.setEnabled(running && !paused);
        resumeButton.setEnabled(paused);
        stopButton.setEnabled(running);
        exportSvgButton.setEnabled(!view.model().isEmpty());
    }

    /**
     * Updates the status badge from session state.
     *
     * @param snapshot session snapshot
     */
    private void updateStatus(MctsSession.Snapshot snapshot) {
        switch (snapshot.state()) {
            case ERROR -> statusBadge.error(snapshot.error() == null ? snapshot.status() : snapshot.error());
            case RUNNING, STARTING -> statusBadge.busy(snapshot.status());
            case PAUSED -> statusBadge.idle(snapshot.status());
            case DONE -> statusBadge.success(snapshot.status());
            default -> statusBadge.idle(snapshot.status());
        }
    }

    /**
     * Updates the search-tree workspace context.
     *
     * @param snapshot session snapshot
     */
    private void updateWorkspaceHeader(MctsSession.Snapshot snapshot) {
        long visits = 0L;
        if (snapshot.tree() != null) {
            visits = snapshot.tree().playouts();
        } else if (snapshot.root() != null) {
            visits = snapshot.root().playouts();
        }
        String cpuct = MctsCliSupport.trimDouble(((Number) cpuctSpinner.getValue()).doubleValue());
        workspaceHeader.setContext("Root position · Cpuct " + cpuct + " · "
                + String.format(java.util.Locale.ROOT, "%,d", visits) + " visits · " + snapshot.status());
    }

    /**
     * Creates the tree legend.
     *
     * @return legend component
     */
    private static JComponent createTreeLegend() {
        JPanel legend = new JPanel(new java.awt.GridLayout(0, 1, 0, Theme.SPACE_XS));
        legend.setOpaque(false);
        legend.setBorder(Theme.pad(Theme.SPACE_SM));
        legend.add(Ui.legendRow(Theme.ACCENT, "Selected principal path",
                "Highlighted route from root to the inspected node."));
        legend.add(Ui.legendRow(TensorViz.POLICY, "Explored branch",
                "Visible child board with visits, prior, Q, U, and PUCT."));
        legend.add(Ui.legendRow(Theme.MUTED, "Transposition",
                "Dashed link when merge transpositions is enabled."));
        legend.add(Ui.legendRow(Theme.STATUS_WARNING_BORDER, "Collapsed leaf",
                "Batched frontier node when leaf batching keeps the tree readable."));
        legend.add(Ui.legendRow(Theme.LINE, "Guide level",
                "0 hides vertical subtree dividers; higher levels partition that layer and descendants."));
        return legend;
    }

    /**
     * Copies the equivalent {@code crtk engine tree} command for the current
     * controls to the system clipboard.
     */
    private void copyCliCommand() {
        java.util.List<String> args = new ArrayList<>(java.util.List.of("engine", "tree"));
        String fen = currentFen.get();
        if (fen != null && !fen.isBlank()) {
            args.add("--fen");
            args.add(fen);
        }
        MctsCliSupport.backendArgs(args, (MctsSession.Backend) backendCombo.getSelectedItem());
        long millis = ((Number) millisSpinner.getValue()).longValue();
        if (millis > 0) {
            args.add("--max-duration");
            args.add(millis + "ms");
        } else {
            args.add("--nodes");
            args.add(Integer.toString(((Number) visitsSpinner.getValue()).intValue()));
        }
        args.add("--cpuct");
        args.add(MctsCliSupport.trimDouble(((Number) cpuctSpinner.getValue()).doubleValue()));
        args.add("--depth");
        args.add(Integer.toString(((Number) depthSpinner.getValue()).intValue()));
        args.add("--branches");
        args.add(Integer.toString(((Number) branchesSpinner.getValue()).intValue()));
        args.add("--min-visits");
        args.add(Integer.toString(((Number) minVisitsSpinner.getValue()).intValue()));
        CommandRunner.copyToClipboard(CommandRunner.displayCommand(args));
        statusBadge.success("command copied");
        toast(Toast.Kind.SUCCESS, "Copied to clipboard");
    }

    /**
     * Exports the current tree to an SVG file.
     */
    private void exportSvg() {
        TreeLayout.Model model = view.model();
        if (model.isEmpty()) {
            statusBadge.idle("nothing to export");
            return;
        }
        try {
            String svg = TreeSvgExporter.toSvg(model, Theme.BG, Theme.ACCENT,
                    application.gui.workbench.network.TensorViz.POLICY, Theme.LINE,
                    Theme.PANEL_SOLID, Theme.TEXT, Theme.MUTED);
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            File dir = new File(Prefs.exportDir());
            Files.createDirectories(dir.toPath());
            File out = new File(dir, "search-tree-" + stamp + ".svg");
            Files.writeString(out.toPath(), svg);
            Prefs.setExportDir(dir.getPath());
            statusBadge.success("exported " + out.getName());
            toast(Toast.Kind.SUCCESS, "Exported " + out.getName());
        } catch (IOException | RuntimeException ex) {
            statusBadge.error("export failed: " + ex.getMessage());
            toast(Toast.Kind.ERROR, "Export failed: " + ex.getMessage());
        }
    }

    /**
     * Shows a toast on the hosting frame.
     *
     * @param kind toast kind
     * @param message toast message
     */
    private void toast(Toast.Kind kind, String message) {
        java.awt.Window window = SwingUtilities.getWindowAncestor(this);
        if (window instanceof JFrame frame) {
            Toast.show(frame, kind, message);
        }
    }

}
