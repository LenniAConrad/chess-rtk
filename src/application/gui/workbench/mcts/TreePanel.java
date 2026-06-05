package application.gui.workbench.mcts;

import static application.gui.workbench.ui.Ui.setColumnWidth;
import application.gui.workbench.Defaults;
import application.gui.workbench.game.Positions;
import application.gui.workbench.layout.SplitPaneStyler;
import application.gui.workbench.network.Prefs;
import application.gui.workbench.network.TensorViz;
import application.gui.workbench.ui.GroupBox;
import application.gui.workbench.ui.StatusBadge;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Toast;
import application.gui.workbench.ui.ToggleBox;
import application.gui.workbench.ui.Ui;
import application.gui.workbench.ui.WrappingFlowLayout;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Position;
import chess.tag.game.SanResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
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
public final class TreePanel extends JPanel implements MctsSession.Listener {

    /**
     * Serialization identifier.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Caption strip height beneath each node board.
     */
    private static final int CAPTION_HEIGHT = 34;

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
     * Frame interval for scrubber playback, in milliseconds.
     */
    private static final int PLAY_INTERVAL_MS = 110;

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
     * Board-thumbnail size control.
     */
    private final JSpinner boardSizeSpinner = new JSpinner(new SpinnerNumberModel(64, 40, 128, 8));

    /**
     * Start button.
     */
    private final JButton startButton = Ui.button("Start", true, event -> start());

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
    private final JButton stopButton = Ui.button("Stop", false, null);

    /**
     * Fit-to-view button.
     */
    private final JButton fitButton = Ui.button("Fit", false, event -> view.fit());

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
            "Show depth-level separator lines and per-level labels so it is clear which ply each row is");

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
    private final ChildTableModel childModel = new ChildTableModel();

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
    private final WdlBar wdlBar = new WdlBar();

    /**
     * Faint node-count-over-time sparkline behind the growth scrubber.
     */
    private final Sparkline sparkline = new Sparkline();

    /**
     * Growth-scrubber timeline.
     */
    private final JSlider scrubSlider = new JSlider(0, 0, 0);

    /**
     * Step-back-one-frame button.
     */
    private final JButton scrubPrevButton = Ui.button("«", false, event -> stepScrub(-1));

    /**
     * Step-forward-one-frame button.
     */
    private final JButton scrubNextButton = Ui.button("»", false, event -> stepScrub(1));

    /**
     * Play/pause button that animates the scrubber through recorded frames.
     */
    private final JButton playButton = Ui.button("Play", false, event -> togglePlay());

    /**
     * Return-to-live button.
     */
    private final JButton liveButton = Ui.button("Live", false, event -> goLive());

    /**
     * Scrubber readout (frame counter / visits / nodes).
     */
    private final JLabel scrubLabel = new JLabel("no recording yet");

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
     * True while showing a recorded growth frame instead of the live tree.
     */
    private boolean scrubbing;

    /**
     * Index of the recorded growth frame shown while scrubbing.
     */
    private int scrubIndex;

    /**
     * Guards scrubber events triggered by programmatic slider updates.
     */
    private boolean adjustingScrub;

    /**
     * Last recorded frame rendered while scrubbing, so the canvas is re-rendered
     * only when history decimation changes the frame under the slider thumb.
     */
    private transient MctsSearch.TreeSnapshot lastScrubbedFrame;


    /**
     * Timer driving scrubber playback (animating through recorded frames), or
     * null when not playing.
     */
    private transient javax.swing.Timer playTimer;

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
     * Parsed target line as encoded moves from {@link #targetRootFen}.
     */
    private transient short[] targetMoves = new short[0];

    /**
     * Root FEN the {@link #targetMoves} were parsed against.
     */
    private String targetRootFen;

    /**
     * Elapsed millis when the search first made the target's first move its best
     * move, or -1 when not yet found.
     */
    private long targetFoundMillis = -1L;

    /**
     * Visit count when the target's first move first became the best move.
     */
    private long targetFoundVisits;

    /**
     * Creates the panel.
     *
     * @param session shared MCTS session
     * @param currentFen current board FEN supplier
     */
    public TreePanel(MctsSession session, Supplier<String> currentFen) {
        super(new BorderLayout(0, 0));
        this.session = session;
        this.currentFen = currentFen;
        setOpaque(true);
        setBackground(Theme.BG);
        configureControls();
        add(buildToolbar(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        add(buildScrubBar(), BorderLayout.SOUTH);
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
        bindKey(keys, actions, "SPACE", "tree.play", this::togglePlay);
        bindKey(keys, actions, "LEFT", "tree.prev", () -> stepScrub(-1));
        bindKey(keys, actions, "RIGHT", "tree.next", () -> stepScrub(1));
        bindKey(keys, actions, "HOME", "tree.first", () -> stepScrub(-session.historySize()));
        bindKey(keys, actions, "END", "tree.live", this::goLive);
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
        stopPlay();
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
     * @param fen FEN
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
        stopPlay();
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
        Ui.styleIntegerSpinner(boardSizeSpinner);
        visitsSpinner.setPreferredSize(new Dimension(92, Theme.CONTROL_HEIGHT));
        cpuctSpinner.setPreferredSize(new Dimension(72, Theme.CONTROL_HEIGHT));
        depthSpinner.setPreferredSize(new Dimension(66, Theme.CONTROL_HEIGHT));
        maxNodesSpinner.setPreferredSize(new Dimension(96, Theme.CONTROL_HEIGHT));
        minVisitsSpinner.setPreferredSize(new Dimension(78, Theme.CONTROL_HEIGHT));
        branchesSpinner.setPreferredSize(new Dimension(62, Theme.CONTROL_HEIGHT));
        boardSizeSpinner.setPreferredSize(new Dimension(64, Theme.CONTROL_HEIGHT));
        statusBadge.setFixedTextWidth(300);
        statusBadge.idle("MCTS idle");
        pauseButton.addActionListener(event -> session.pause());
        resumeButton.addActionListener(event -> session.resume());
        stopButton.addActionListener(event -> session.stop());
        depthSpinner.addChangeListener(event -> applyTreeOptions());
        maxNodesSpinner.addChangeListener(event -> applyTreeOptions());
        minVisitsSpinner.addChangeListener(event -> applyTreeOptions());
        branchesSpinner.addChangeListener(event -> applyTreeOptions());
        boardSizeSpinner.addChangeListener(event -> rebuildAndReset());
        mergeToggle.addActionListener(event -> rebuildAndReset());
        batchLeavesToggle.addActionListener(event -> rebuildAndReset());
        detailsToggle.addActionListener(event -> setInspectorVisible(detailsToggle.isSelected()));
        layersToggle.addActionListener(event -> view.setShowLayers(layersToggle.isSelected()));
        // Default states (the ToggleBox second arg is "compact", not "selected",
        // so these must be set explicitly): merge transpositions and batch leaves
        // keep the tree compact, and Details matches the inspector being shown.
        mergeToggle.setSelected(true);
        batchLeavesToggle.setSelected(true);
        detailsToggle.setSelected(true);
        layersToggle.setSelected(true);
        view.setShowLayers(true);

        depthLabel.setFont(Theme.font(11, Font.BOLD));
        depthLabel.setForeground(Theme.MUTED);
        depthLabel.setAlignmentX(LEFT_ALIGNMENT);
        breadcrumb.setAlignmentX(LEFT_ALIGNMENT);
        breadcrumb.setMaximumSize(new Dimension(Integer.MAX_VALUE, 240));
        altParents.setAlignmentX(LEFT_ALIGNMENT);
        altParents.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));

        Ui.styleSlider(scrubSlider);
        scrubSlider.setToolTipText("Scrub through the recorded growth of the tree");
        scrubSlider.setPreferredSize(new Dimension(260, Theme.CONTROL_HEIGHT));
        scrubSlider.addChangeListener(event -> onScrubSlider());
        scrubPrevButton.setToolTipText("Step one recorded frame back");
        scrubNextButton.setToolTipText("Step one recorded frame forward");
        liveButton.setToolTipText("Jump back to the live, growing tree");
        scrubLabel.setFont(Theme.mono(12));
        scrubLabel.setForeground(Theme.MUTED);
        playButton.setToolTipText("Play back the recorded growth frame by frame");

        Ui.styleFields(targetField);
        Ui.placeholder(targetField, "e.g. Qh8 c5 ...");
        targetField.setPreferredSize(new Dimension(190, Theme.CONTROL_HEIGHT));
        targetField.setToolTipText(
                "Track a move line (SAN or UCI). Watch the search reach it and how long it takes to find it.");
        targetField.addActionListener(event -> onTargetChanged());
        targetLabel.setFont(Theme.mono(11));
        targetLabel.setForeground(Theme.MUTED);
        openBoardButton.setToolTipText("Open the inspected node's position in a new board");

        inspectorSummary.setEditable(false);
        inspectorSummary.setOpaque(true);
        inspectorSummary.setBackground(Theme.PANEL_SOLID);
        inspectorSummary.setForeground(Theme.TEXT);
        inspectorSummary.setFont(Theme.mono(12));
        inspectorSummary.setLineWrap(true);
        inspectorSummary.setWrapStyleWord(true);
        inspectorSummary.setBorder(Theme.pad(8));
        inspectorSummary.setText("Click a node to list its children here.");

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
     * Builds the boxed control toolbar. Groups wrap responsively so nothing is
     * clipped when the window narrows.
     *
     * @return toolbar component
     */
    private JComponent buildToolbar() {
        JPanel bar = Ui.transparentPanel(new WrappingFlowLayout(FlowLayout.LEFT, Theme.SPACE_SM, Theme.SPACE_XS));
        bar.setOpaque(true);
        bar.setBackground(Theme.PANEL_SOLID);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE),
                Theme.pad(Theme.SPACE_SM, Theme.SPACE_SM, Theme.SPACE_SM, Theme.SPACE_SM)));
        bar.add(GroupBox.of("PUCT", startButton, pauseButton, resumeButton, stopButton,
                Ui.labeledControl("Backend", backendCombo),
                Ui.labeledControl("Visits", visitsSpinner),
                Ui.labeledControl("Millis", millisSpinner),
                Ui.labeledControl("Cpuct", cpuctSpinner)));
        bar.add(GroupBox.of("Position", positionCombo));
        bar.add(GroupBox.of("Target", targetField, targetLabel));
        // "Tree" = the filters controlling which nodes show; "View" = how they're
        // drawn; "Display" = canvas actions. Everything stays visible (the bar
        // wraps responsively) so no setting is hidden.
        bar.add(GroupBox.of("Tree",
                Ui.labeledControl("Depth", depthSpinner),
                Ui.labeledControl("Max nodes", maxNodesSpinner),
                Ui.labeledControl("Min visits", minVisitsSpinner),
                Ui.labeledControl("Branches", branchesSpinner)));
        bar.add(GroupBox.of("View", mergeToggle, batchLeavesToggle, layersToggle,
                Ui.labeledControl("Board", boardSizeSpinner)));
        bar.add(GroupBox.of("Display", autoFitToggle, detailsToggle,
                fitButton, openBoardButton, exportSvgButton));
        bar.add(statusBadge);
        return bar;
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

        split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, view, inspector);
        split.setResizeWeight(INSPECTOR_SPLIT);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setOpaque(false);
        SplitPaneStyler.style(split);
        defaultDividerSize = Math.max(1, split.getDividerSize());
        return split;
    }

    /**
     * Builds the bottom growth-scrubber bar.
     *
     * @return scrubber component
     */
    private JComponent buildScrubBar() {
        JPanel bar = Ui.transparentPanel(new BorderLayout(Theme.SPACE_SM, 0));
        bar.setOpaque(true);
        bar.setBackground(Theme.PANEL_SOLID);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.LINE),
                Theme.pad(Theme.SPACE_XS, Theme.SPACE_SM, Theme.SPACE_XS, Theme.SPACE_SM)));
        JPanel nav = Ui.transparentPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_XS, 0));
        nav.add(Ui.label("Growth"));
        nav.add(scrubPrevButton);
        nav.add(scrubNextButton);
        nav.add(playButton);
        nav.add(liveButton);
        bar.add(nav, BorderLayout.WEST);
        JPanel timeline = Ui.transparentPanel(new BorderLayout(0, 0));
        timeline.add(sparkline, BorderLayout.NORTH);
        timeline.add(scrubSlider, BorderLayout.CENTER);
        bar.add(timeline, BorderLayout.CENTER);
        bar.add(scrubLabel, BorderLayout.EAST);
        return bar;
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
        stopPlay();
        resetTargetTracking();
        parseTarget(rootFen);
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
        if (scrubbing) {
            renderScrubbed();
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
        updateScrubControls();
        updateTargetStatus(snapshot);
        if (!scrubbing) {
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
        rebuildModel(tree, snapshot);
        if (autoFitToggle.isSelected() && snapshot != null && snapshot.running()) {
            view.fit();
        }
    }

    /**
     * Renders the recorded growth frame currently selected on the scrubber.
     */
    private void renderScrubbed() {
        MctsSearch.TreeSnapshot tree = session.historyFrame(scrubIndex);
        lastScrubbedFrame = tree;
        rebuildModel(tree, null);
    }

    /**
     * Rebuilds the tree layout from a snapshot and refreshes the inspector.
     *
     * @param tree tree snapshot to render, or null for empty
     * @param pathSnapshot live session snapshot whose exploring line is overlaid
     *     as the search path, or null (scrubbing) to show no live path
     */
    private void rebuildModel(MctsSearch.TreeSnapshot tree, MctsSession.Snapshot pathSnapshot) {
        if (tree == null) {
            // No tree (idle / starting / stopped): clear the canvas AND the
            // inspector, so stale node statistics from a prior search don't
            // linger while the next search spins up.
            currentInfos = List.of();
            inspectorKey = null;
            inspectorNodeId = null;
            inspectorSummary.setText("Click a node to list its children here.");
            inspectorSummary.setCaretPosition(0);
            childHeading.setText("Children");
            setChildRows(List.of(), 1);
            wdlBar.clear();
            clearNav();
            view.clear();
            view.setSearchPath(Set.of());
            view.setTargetPath(Set.of(), false);
            return;
        }
        currentInfos = tree.nodes();
        String selectedId = tree.selectedNode() == null ? null : tree.selectedNode().id();
        int boardSize = ((Number) boardSizeSpinner.getValue()).intValue();
        TreeLayout.Model model = TreeLayout.layout(currentInfos, mergeToggle.isSelected(),
                batchLeavesToggle.isSelected(), inspectorNodeId != null ? inspectorNodeId : selectedId,
                boardSize, boardSize + CAPTION_HEIGHT, H_GAP, V_GAP);
        view.setModel(model);
        // Compute the search-path and target overlays after currentInfos is
        // updated, so they map the live exploring line / target line against the
        // frame just rendered.
        view.setSearchPath(searchPathFor(pathSnapshot));
        view.setTargetPath(targetPathKeys(), targetMoves.length > 0);
        if (inspectorKey == null) {
            // Default the inspector to the root so children are immediately
            // listed. This only populates the inspector; it must not push the
            // session selection (that would re-enter rebuildModel before
            // inspectorKey is set and recurse).
            for (TreeLayout.Node node : model.nodes()) {
                if (node.root()) {
                    showNodeInInspector(node.info());
                    break;
                }
            }
        } else {
            refreshInspectorAfterRebuild();
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
     * Updates the scrubber slider, buttons, and readout from the recorded
     * history. Following live keeps the thumb pinned to the latest frame.
     */
    private void updateScrubControls() {
        int frames = session.historySize();
        boolean has = frames > 0;
        if (!has) {
            scrubbing = false;
        }
        scrubSlider.setEnabled(has);
        scrubPrevButton.setEnabled(has);
        scrubNextButton.setEnabled(has);
        liveButton.setEnabled(has && scrubbing);
        adjustingScrub = true;
        scrubSlider.setMinimum(0);
        scrubSlider.setMaximum(Math.max(0, frames - 1));
        if (scrubbing) {
            scrubIndex = Math.min(scrubIndex, Math.max(0, frames - 1));
        } else {
            scrubIndex = Math.max(0, frames - 1);
        }
        scrubSlider.setValue(scrubIndex);
        adjustingScrub = false;
        // History decimation can remap which frame an index points at; while
        // scrubbing, if the frame under the thumb changed, re-render so the
        // canvas stays in step with the moved slider and readout.
        if (scrubbing && session.historyFrame(scrubIndex) != lastScrubbedFrame) {
            renderScrubbed();
        }
        updateScrubLabel();
        sparkline.repaint();
    }

    /**
     * Updates the scrubber readout text.
     */
    private void updateScrubLabel() {
        int frames = session.historySize();
        if (frames == 0) {
            scrubLabel.setText("no recording yet");
            return;
        }
        if (!scrubbing) {
            scrubLabel.setText(String.format("Live · %,d frames", frames));
            return;
        }
        MctsSearch.TreeSnapshot frame = session.historyFrame(scrubIndex);
        scrubLabel.setText(String.format("Frame %,d / %,d · %,d visits · %,d nodes",
                scrubIndex + 1, frames, frame == null ? 0 : frame.playouts(),
                frame == null ? 0 : frame.nodes().size()));
    }

    /**
     * Handles a user drag of the scrubber slider.
     */
    private void onScrubSlider() {
        if (adjustingScrub) {
            return;
        }
        stopPlay();
        scrubbing = true;
        scrubIndex = scrubSlider.getValue();
        liveButton.setEnabled(true);
        updateScrubLabel();
        renderScrubbed();
    }

    /**
     * Steps the scrubber by a number of frames.
     *
     * @param delta frame delta (negative to step back)
     */
    private void stepScrub(int delta) {
        int frames = session.historySize();
        if (frames == 0) {
            return;
        }
        stopPlay();
        scrubbing = true;
        scrubIndex = Math.max(0, Math.min(frames - 1, scrubIndex + delta));
        adjustingScrub = true;
        scrubSlider.setValue(scrubIndex);
        adjustingScrub = false;
        liveButton.setEnabled(true);
        updateScrubLabel();
        renderScrubbed();
    }

    /**
     * Returns the scrubber to following the live, growing tree.
     */
    private void goLive() {
        stopPlay();
        scrubbing = false;
        MctsSession.Snapshot snapshot = session.snapshot();
        updateScrubControls();
        renderLive(snapshot);
    }

    /**
     * Toggles scrubber playback, animating forward through the recorded frames.
     */
    private void togglePlay() {
        if (playTimer != null) {
            stopPlay();
        } else {
            startPlay();
        }
    }

    /**
     * Starts playback. Replays the recorded frames in order without recomputing
     * the search, so you watch exactly how the tree grew.
     */
    private void startPlay() {
        if (session.historySize() == 0) {
            return;
        }
        scrubbing = true;
        if (scrubIndex >= session.historySize() - 1) {
            scrubIndex = 0;
        }
        liveButton.setEnabled(true);
        playButton.setText("Pause");
        playTimer = new javax.swing.Timer(PLAY_INTERVAL_MS, event -> advancePlay());
        playTimer.start();
        showScrubFrame();
    }

    /**
     * Stops playback if running.
     */
    private void stopPlay() {
        if (playTimer != null) {
            playTimer.stop();
            playTimer = null;
        }
        playButton.setText("Play");
    }

    /**
     * Advances playback by one recorded frame, stopping at the end.
     */
    private void advancePlay() {
        int frames = session.historySize();
        if (frames == 0) {
            stopPlay();
            return;
        }
        if (scrubIndex >= frames - 1) {
            scrubIndex = frames - 1;
            showScrubFrame();
            stopPlay();
            return;
        }
        scrubIndex++;
        showScrubFrame();
    }

    /**
     * Syncs the slider and readout to the current scrub index and renders it.
     */
    private void showScrubFrame() {
        adjustingScrub = true;
        scrubSlider.setValue(scrubIndex);
        adjustingScrub = false;
        updateScrubLabel();
        renderScrubbed();
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
        parseTarget(session.snapshot().rootFen());
        resetTargetTracking();
        if (scrubbing) {
            renderScrubbed();
        } else {
            renderLive(session.snapshot());
        }
        updateTargetStatus(session.snapshot());
    }

    /**
     * Parses the target field text into encoded moves against a root FEN.
     *
     * @param rootFen root position the line starts from
     */
    private void parseTarget(String rootFen) {
        targetRootFen = rootFen;
        targetMoves = parseLine(rootFen, targetField.getText());
    }

    /**
     * Resets the time-to-find tracking for a fresh measurement.
     */
    private void resetTargetTracking() {
        targetFoundMillis = -1L;
        targetFoundVisits = 0L;
    }

    /**
     * Parses a SAN/UCI move line from a root FEN into encoded moves, stopping at
     * the first token that does not resolve to a legal move. Move numbers like
     * {@code 1.} / {@code 1...} are ignored.
     *
     * @param rootFen root FEN
     * @param text move line text
     * @return the resolvable prefix as encoded moves
     */
    private static short[] parseLine(String rootFen, String text) {
        if (rootFen == null || rootFen.isBlank() || text == null || text.isBlank()) {
            return new short[0];
        }
        Position pos;
        try {
            pos = new Position(rootFen);
        } catch (RuntimeException ex) {
            return new short[0];
        }
        List<Short> moves = new ArrayList<>();
        for (String raw : text.trim().split("\\s+")) {
            String token = raw.replaceAll("^\\d+\\.+", "").trim();
            if (token.isEmpty() || ".".equals(token)) {
                continue;
            }
            short move = SanResolver.resolve(pos, token);
            if (move == Move.NO_MOVE) {
                move = parseUci(pos, token);
            }
            if (move == Move.NO_MOVE) {
                break;
            }
            moves.add(move);
            pos = pos.play(move);
        }
        short[] out = new short[moves.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = moves.get(i);
        }
        return out;
    }

    /**
     * Resolves a UCI token to a legal move by matching its rendered UCI text, so
     * castling, en passant, and promotions resolve regardless of the engine's
     * internal square convention.
     *
     * @param pos position
     * @param token UCI token (e.g. {@code "d1h5"}, {@code "e7e8q"})
     * @return the legal move, or {@link Move#NO_MOVE}
     */
    private static short parseUci(Position pos, String token) {
        if (!Move.isMove(token)) {
            return Move.NO_MOVE;
        }
        MoveList legal = pos.legalMoves();
        for (int i = 0; i < legal.size(); i++) {
            short move = legal.get(i);
            if (token.equalsIgnoreCase(Move.toString(move))) {
                return move;
            }
        }
        return Move.NO_MOVE;
    }

    /**
     * Returns the keys of the contiguous target-line prefix that currently
     * exists in the tree, for the teal overlay.
     *
     * @return target node keys present in the current frame
     */
    private Set<String> targetPathKeys() {
        if (targetMoves.length == 0 || currentInfos.isEmpty()) {
            return Set.of();
        }
        boolean merge = mergeToggle.isSelected();
        java.util.Map<String, MctsSearch.NodeInfo> idToNode = new java.util.HashMap<>();
        for (MctsSearch.NodeInfo n : currentInfos) {
            idToNode.put(n.id(), n);
        }
        Set<String> keys = new java.util.HashSet<>();
        for (MctsSearch.NodeInfo n : currentInfos) {
            if (n.parentId() == null || n.parentId().isEmpty()) {
                keys.add(merge ? Long.toString(n.signature()) : n.id());
                break;
            }
        }
        StringBuilder id = new StringBuilder();
        for (short move : targetMoves) {
            if (id.length() > 0) {
                id.append(' ');
            }
            id.append(Move.toString(move));
            MctsSearch.NodeInfo node = idToNode.get(id.toString());
            if (node == null) {
                break;
            }
            keys.add(merge ? Long.toString(node.signature()) : node.id());
        }
        return keys;
    }

    /**
     * Updates the target-tracking readout from the live snapshot, latching the
     * first time the search makes the target's first move its best move.
     *
     * @param snapshot session snapshot
     */
    private void updateTargetStatus(MctsSession.Snapshot snapshot) {
        if (snapshot != null && !Objects.equals(snapshot.rootFen(), targetRootFen)) {
            parseTarget(snapshot.rootFen());
            resetTargetTracking();
        }
        if (targetMoves.length == 0) {
            targetLabel.setText("no target");
            return;
        }
        MctsSearch.Snapshot root = snapshot == null ? null : snapshot.root();
        if (root == null) {
            targetLabel.setText("set · start search");
            return;
        }
        boolean bestMatch = root.bestMove() == targetMoves[0];
        int agree = commonPrefix(root.bestPv(), targetMoves);
        if (bestMatch && targetFoundMillis < 0) {
            targetFoundMillis = root.elapsedMillis();
            targetFoundVisits = root.playouts();
        }
        String pv = "PV " + agree + "/" + targetMoves.length;
        if (targetFoundMillis >= 0) {
            targetLabel.setText(String.format("found %.2fs · %,d v · %s",
                    targetFoundMillis / 1000.0, targetFoundVisits, pv));
        } else {
            targetLabel.setText("searching · " + pv);
        }
    }

    /**
     * Returns the length of the common leading move prefix of two lines.
     *
     * @param a first line (may be null)
     * @param b second line
     * @return common prefix length
     */
    private static int commonPrefix(short[] a, short[] b) {
        if (a == null) {
            return 0;
        }
        int n = Math.min(a.length, b.length);
        int i = 0;
        while (i < n && a[i] == b[i]) {
            i++;
        }
        return i;
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
            showBlobInInspector(node);
        } else {
            // Populate the inspector (sets inspectorKey) before pushing the
            // session selection: setSelectedNodeId re-enters rebuildModel
            // synchronously, and it must see the new key to take the refresh
            // path rather than re-selecting and recursing.
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
        inspectorKey = mergeToggle.isSelected() ? Long.toString(info.signature()) : info.id();
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
        String key = mergeToggle.isSelected() ? Long.toString(info.signature()) : info.id();
        showNodeInInspector(info);
        view.focusNode(key);
        session.setSelectedNodeId(info.id());
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
     * @param node node
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
    private java.util.Set<String> searchPathFor(MctsSession.Snapshot snapshot) {
        if (snapshot == null || !snapshot.running() || snapshot.root() == null) {
            return java.util.Set.of();
        }
        boolean merge = mergeToggle.isSelected();
        java.util.Map<String, MctsSearch.NodeInfo> idToNode = new java.util.HashMap<>();
        for (MctsSearch.NodeInfo n : currentInfos) {
            idToNode.put(n.id(), n);
        }
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (MctsSearch.NodeInfo n : currentInfos) {
            if (n.parentId() == null || n.parentId().isEmpty()) {
                keys.add(merge ? Long.toString(n.signature()) : n.id());
                break;
            }
        }
        short[] line = snapshot.root().exploringLine();
        if (line != null) {
            StringBuilder id = new StringBuilder();
            for (short move : line) {
                if (move == chess.core.Move.NO_MOVE) {
                    continue;
                }
                if (id.length() > 0) {
                    id.append(' ');
                }
                id.append(chess.core.Move.toString(move));
                MctsSearch.NodeInfo node = idToNode.get(id.toString());
                if (node != null) {
                    keys.add(merge ? Long.toString(node.signature()) : node.id());
                }
            }
        }
        return keys;
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
     * Exports the current tree to a standalone SVG file.
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

    /**
     * A compact win/draw/loss stacked bar plus a centered eval bar for the
     * inspected node, so its value reads at a glance instead of as raw text.
     */
    private static final class WdlBar extends JComponent {
        /**
         * Serialization identifier.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Win / draw / loss probabilities and mean value of the shown node.
         */
        private double win;
        private double draw;
        private double loss;
        private double q;

        /**
         * True once a node's values have been set.
         */
        private boolean has;

        /**
         * Creates the bar.
         */
        WdlBar() {
            setOpaque(false);
            setPreferredSize(new Dimension(0, 36));
        }

        /**
         * Sets the node values and repaints.
         *
         * @param w win probability
         * @param d draw probability
         * @param l loss probability
         * @param value mean value in [-1, 1]
         */
        void set(double w, double d, double l, double value) {
            win = w;
            draw = d;
            loss = l;
            q = value;
            has = true;
            repaint();
        }

        /**
         * Clears the bar.
         */
        void clear() {
            has = false;
            repaint();
        }

        /**
         * Paints the WDL distribution bar.
         *
         * @param graphics graphics context
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            if (!has) {
                return;
            }
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = Math.max(1, getWidth());
                int barH = 13;
                double total = Math.max(1e-6, win + draw + loss);
                int ww = (int) Math.round(w * win / total);
                int dw = (int) Math.round(w * draw / total);
                int x = 0;
                g.setColor(TensorViz.POSITIVE);
                g.fillRect(x, 0, ww, barH);
                x += ww;
                g.setColor(Theme.withAlpha(Theme.MUTED, 150));
                g.fillRect(x, 0, dw, barH);
                x += dw;
                g.setColor(TensorViz.NEGATIVE);
                g.fillRect(x, 0, Math.max(0, w - x), barH);
                g.setFont(Theme.font(9, Font.BOLD));
                g.setColor(Theme.TEXT);
                g.drawString(String.format("%.0f", win * 100), 3, barH - 3);
                String lossText = String.format("%.0f", loss * 100);
                g.drawString(lossText, w - g.getFontMetrics().stringWidth(lossText) - 3, barH - 3);
                // Eval bar: q in [-1, 1] mapped to a bar growing from the center.
                int evalY = barH + 7;
                int evalH = 8;
                g.setColor(Theme.withAlpha(Theme.LINE, 120));
                g.fillRoundRect(0, evalY, w, evalH, evalH, evalH);
                int mid = w / 2;
                int qx = (int) Math.round(mid + Math.max(-1, Math.min(1, q)) * (w / 2.0));
                g.setColor(q >= 0 ? TensorViz.POSITIVE : TensorViz.NEGATIVE);
                if (q >= 0) {
                    g.fillRoundRect(mid, evalY, Math.max(0, qx - mid), evalH, evalH, evalH);
                } else {
                    g.fillRoundRect(qx, evalY, Math.max(0, mid - qx), evalH, evalH, evalH);
                }
                g.setColor(Theme.withAlpha(Theme.TEXT, 180));
                g.fillRect(mid - 1, evalY - 1, 2, evalH + 2);
            } finally {
                g.dispose();
            }
        }
    }

    /**
     * A faint node-count-over-time sparkline that sits above the growth slider,
     * so you can see where the search accelerated.
     */
    private final class Sparkline extends JComponent {
        /**
         * Serialization identifier.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Creates the sparkline.
         */
        Sparkline() {
            setOpaque(false);
            setPreferredSize(new Dimension(0, 14));
        }

        /**
         * Paints the search-history sparkline.
         *
         * @param graphics graphics context
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            int n = session.historySize();
            if (n < 2 || getWidth() <= 1) {
                return;
            }
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                int maxNodes = 1;
                for (int i = 0; i < n; i++) {
                    MctsSearch.TreeSnapshot f = session.historyFrame(i);
                    if (f != null) {
                        maxNodes = Math.max(maxNodes, f.nodes().size());
                    }
                }
                Path2D.Double path = new Path2D.Double();
                for (int i = 0; i < n; i++) {
                    MctsSearch.TreeSnapshot f = session.historyFrame(i);
                    int count = f == null ? 0 : f.nodes().size();
                    double x = (double) i / (n - 1) * (w - 1);
                    double y = h - 1 - (double) count / maxNodes * (h - 2);
                    if (i == 0) {
                        path.moveTo(x, y);
                    } else {
                        path.lineTo(x, y);
                    }
                }
                g.setColor(Theme.withAlpha(Theme.ACCENT, 95));
                g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(path);
            } finally {
                g.dispose();
            }
        }
    }

    /**
     * Table model for the navigation inspector's child rows.
     */
    private static final class ChildTableModel extends AbstractTableModel {
        /**
         * Serialization identifier.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Column names.
         */
        private final String[] columns = { "Move", "Visits", "N%", "Q", "Prior", "PUCT" };

        /**
         * Current rows.
         */
        private final transient List<MctsSearch.NodeInfo> rows = new ArrayList<>();

        /**
         * Visit denominator for the N% column.
         */
        private int shareBase = 1;

        /**
         * Replaces the rows.
         *
         * @param next next rows
         * @param base N% denominator
         */
        void setRows(List<MctsSearch.NodeInfo> next, int base) {
            rows.clear();
            if (next != null) {
                rows.addAll(next);
            }
            shareBase = Math.max(1, base);
            fireTableDataChanged();
        }

        /**
         * Returns a row by model index.
         *
         * @param index model row index
         * @return row or null
         */
        MctsSearch.NodeInfo row(int index) {
            return index < 0 || index >= rows.size() ? null : rows.get(index);
        }

        /**
         * Returns the number of displayed child rows.
         *
         * @return row count
         */
        @Override
        public int getRowCount() {
            return rows.size();
        }

        /**
         * Returns the number of visible columns.
         *
         * @return column count
         */
        @Override
        public int getColumnCount() {
            return columns.length;
        }

        /**
         * Returns the column title.
         *
         * @param column column index
         * @return column name
         */
        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        /**
         * Returns the preferred column value class.
         *
         * @param columnIndex column index
         * @return value class
         */
        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 1 ? Integer.class : String.class;
        }

        /**
         * Returns the displayed cell value.
         *
         * @param rowIndex row index
         * @param columnIndex column index
         * @return cell value
         */
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            MctsSearch.NodeInfo row = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> row.san() == null || row.san().isBlank() ? row.uci() : row.san();
                case 1 -> row.visits();
                case 2 -> String.format("%.0f%%", 100.0 * row.visits() / shareBase);
                case 3 -> String.format("%+.2f", row.q());
                case 4 -> String.format("%.1f%%", row.prior() * 100.0);
                case 5 -> String.format("%+.2f", row.score());
                default -> "";
            };
        }
    }
}
