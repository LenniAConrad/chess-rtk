package application.gui.workbench.network;

import application.gui.workbench.Defaults;
import application.gui.workbench.audio.SoundCue;
import application.gui.workbench.audio.SoundService;
import application.gui.workbench.command.CommandRunner;
import application.gui.workbench.game.Positions;
import application.gui.workbench.mcts.MctsSearch;
import application.gui.workbench.mcts.MctsWeightsPanel;
import application.gui.workbench.ui.HoldButton;
import application.gui.workbench.ui.InspectorPanel;
import application.gui.workbench.ui.WrappingFlowLayout;
import application.gui.workbench.ui.RenderAcceleration;
import application.gui.workbench.ui.SegmentedSwitcher;
import application.gui.workbench.ui.StatusBadge;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Toast;
import application.gui.workbench.ui.ToggleBox;
import application.gui.workbench.ui.Ui;
import application.gui.workbench.ui.WorkspaceHeader;
import chess.core.Move;
import chess.core.Position;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import application.gui.workbench.ui.SurfacePanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.border.Border;

/**
 * Workbench network-visualizer host panel.
 *
 * <p>Owns the architecture switcher (NNUE / lc0-CNN / lc0-BT4 / OTIS) and the
 * abstract-vs-detailed view-mode toggle. Runs real network inference on a
 * SwingWorker with a short debounce so rapid FEN changes coalesce into one
 * forward pass per architecture. Falls back to synthetic activations when a
 * model file is missing or fails to load.</p>
 */
public final class NetworkPanel extends SurfacePanel {

    /**
     * Serialization identifier for Swing compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Stable text value for arch nnue.
     */
    private static final String ARCH_NNUE = "NNUE";

    /**
     * Scale factor used when exporting the visible activation view to PNG.
     */
    private static final double PNG_EXPORT_SCALE = 2.0;

    /**
     * Maximum exported PNG width or height in pixels.
     */
    private static final int PNG_EXPORT_MAX_DIMENSION = 8192;

    /**
     * Activation-provider label for NNUE views.
     */
    private static final String ARCH_NNUE_LABEL = RealActivations.LABEL_NNUE;

    /**
     * Stable text value for arch cnn.
     */
    private static final String ARCH_CNN = "LC0 CNN";

    /**
     * Activation-provider label for LC0 CNN views.
     */
    private static final String ARCH_CNN_LABEL = RealActivations.LABEL_CNN;

    /**
     * Stable text value for arch bt4.
     */
    private static final String ARCH_BT4 = "LC0 BT4";

    /**
     * Activation-provider label for LC0 BT4 views.
     */
    private static final String ARCH_BT4_LABEL = RealActivations.LABEL_BT4;

    /**
     * Stable text value for arch otis.
     */
    private static final String ARCH_OTIS = "OTIS";

    /**
     * Activation-provider label for OTIS views.
     */
    private static final String ARCH_OTIS_LABEL = RealActivations.LABEL_OTIS;

    /**
     * Stable text value for arch classical.
     */
    private static final String ARCH_CLASSICAL = "Classical";

    /**
     * Stable text value for arch classical label.
     */
    private static final String ARCH_CLASSICAL_LABEL = "Classical (handcrafted)";

    /**
     * Stable text value for card loading.
     */
    private static final String CARD_LOADING = "loading";

    /**
     * Debounce delay, in milliseconds, before launching inference after edits.
     */
    private static final int DEBOUNCE_MS = 220;

    /**
     * Neural-network visualizers by architecture.
     */
    private final NnueView nnueView = new NnueView();

    /**
     * CNN visualizer.
     */
    private final CnnView cnnView = new CnnView();

    /**
     * BT4 transformer visualizer.
     */
    private final Bt4View bt4View = new Bt4View();

    /**
     * OTIS policy/WDL visualizer.
     */
    private final OtisView otisView = new OtisView();

    /**
     * Classical handcrafted-evaluator visualizer.
     */
    private final ClassicalView classicalView = new ClassicalView();

    /**
     * Shared value for snapshot cache limit.
     */
    private static final int SNAPSHOT_CACHE_LIMIT = 64;

    /**
     * Last published activation-cache size, surfaced on Dashboard diagnostics.
     */
    private static volatile int lastSnapshotCacheSize;

    /**
     * Last architecture whose snapshot cache changed.
     */
    private static volatile String lastSnapshotCacheArchitecture = "";

    /**
     * LRU cache for recently computed activation snapshots.
     */
    private final Map<String, ActivationSnapshot> snapshotCache =
            new LinkedHashMap<>(16, 0.75f, true) {
                /**
                 * Serialization identifier for Swing compatibility.
                 */
                private static final long serialVersionUID = 1L;

                /**
                 * {@inheritDoc}
                 */
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<String, ActivationSnapshot> eldest) {
                    return size() > SNAPSHOT_CACHE_LIMIT;
                }
            };

    /**
     * Workspace header for the evaluator / trace lab.
     */
    private final WorkspaceHeader workspaceHeader = new WorkspaceHeader("Engine Lab", "", null);

    /**
     * Scroll panes for each architecture view.
     */
    private final Map<String, JScrollPane> viewScrolls = new LinkedHashMap<>();

    /**
     * Architecture selector.
     */
    private final JComboBox<String> archCombo = new JComboBox<>(buildArchOptions());

    /**
     * Position-source selector.
     */
    private final JComboBox<String> positionCombo = new JComboBox<>(Positions.labels());

    /**
     * Mode token for raw.
     */
    private static final int MODE_RAW = 2;

    /**
     * Mode token for atlas.
     */
    private static final int MODE_ATLAS = 3;

    /**
     * Network view-mode switcher.
     */
    private final SegmentedSwitcher viewMode = new SegmentedSwitcher(
            new String[] { "Overview", "Trace", "All", "Atlas" });

    /**
     * View positioning controls.
     */
    private final JButton fitViewButton = Ui.button("Fit View", false, event -> fitActiveView());
    /**
     * Button that restores the default view.
     */
    private final JButton resetViewButton = Ui.button("Reset View", false, event -> resetActiveView());

    /**
     * Export button for the active network view.
     */
    private final JButton exportPngButton = buildNetworkExportButton(this);

    /**
     * Builds the build network export button.
     *
     * @param panel Swing panel
     * @return build network export button
     */
    private static JButton buildNetworkExportButton(NetworkPanel panel) {
        JButton trigger = Ui.button("Export", false, null);
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
        menu.add(networkExportMenuItem("Save PNG…", event -> panel.exportPng()));
        menu.add(networkExportMenuItem("Copy image", event -> panel.copyImage()));
        Ui.stylePopupMenu(menu);
        trigger.addActionListener(event -> menu.show(trigger, 0, trigger.getHeight()));
        return trigger;
    }

    /**
     * Returns the network export menu item.
     *
     * @param label display label
     * @param listener event listener
     * @return network export menu item
     */
    private static javax.swing.JMenuItem networkExportMenuItem(String label,
            java.awt.event.ActionListener listener) {
        javax.swing.JMenuItem item = new javax.swing.JMenuItem(label);
        Ui.stylePopupMenuItem(item);
        item.addActionListener(listener);
        return item;
    }

    /**
     * Copies image.
     */
    private void copyImage() {
        JComponent target = activeView();
        java.awt.Dimension original = target.getSize();
        int w = Math.max(1, Math.max(target.getWidth(), target.getPreferredSize().width));
        int h = Math.max(1, Math.max(target.getHeight(), target.getPreferredSize().height));
        try {
            BufferedImage img = RenderAcceleration.translucentImage(w, h);
            java.awt.Graphics2D g = img.createGraphics();
            try {
                target.setSize(w, h);
                target.doLayout();
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                target.paint(g);
            } finally {
                g.dispose();
                target.setSize(original);
                target.revalidate();
                target.repaint();
            }
            java.awt.datatransfer.Clipboard clipboard =
                    java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(new java.awt.datatransfer.Transferable() {
                /**
                 * {@inheritDoc}
                 */
                @Override
                public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors() {
                    return new java.awt.datatransfer.DataFlavor[] {
                            java.awt.datatransfer.DataFlavor.imageFlavor };
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public boolean isDataFlavorSupported(java.awt.datatransfer.DataFlavor flavor) {
                    return java.awt.datatransfer.DataFlavor.imageFlavor.equals(flavor);
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public Object getTransferData(java.awt.datatransfer.DataFlavor flavor) {
                    return img;
                }
            }, null);
        } catch (RuntimeException ex) {
            statusBadge.error("copy failed: " + ex.getMessage());
        }
    }

    /**
     * Status badge for network inference.
     */
    private final StatusBadge statusBadge = new StatusBadge();

    /**
     * Playout cadence for publishing network-MCTS progress frames.
     */
    private static final int NETWORK_MCTS_PUBLISH_INTERVAL = 8;

    /**
     * Minimum nanoseconds between network-MCTS UI frame updates.
     */
    private static final long NETWORK_MCTS_MIN_UPDATE_NANOS = 35_000_000L;

    /**
     * Initial playout count before the first network-MCTS frame is shown.
     */
    private static final long NETWORK_MCTS_WARMUP_PLAYOUTS = 32L;

    /**
     * Small startup delay, in milliseconds, before showing warmup progress.
     */
    private static final long NETWORK_MCTS_WARMUP_DELAY_MS = 6L;

    /**
     * Playout cadence for yielding to the Swing event queue.
     */
    private static final int NETWORK_MCTS_YIELD_INTERVAL = 64;

    /**
     * Maximum UI-yield sleep in milliseconds during animated network-MCTS runs.
     */
    private static final long NETWORK_MCTS_FRAME_YIELD_MS = 3L;

    /**
     * Minimum nanoseconds between network-MCTS progress sounds.
     */
    private static final long NETWORK_MCTS_SOUND_INTERVAL_NANOS = 1_350_000_000L;

    /**
     * Preferred width in pixels for the network-MCTS status text.
     */
    private static final int NETWORK_MCTS_STATUS_TEXT_WIDTH = 430;

    /**
     * MCTS start button.
     */
    private final JButton mctsStartButton = Ui.button("Start PUCT", true,
            event -> startNetworkMcts());

    /**
     * MCTS pause/resume button.
     */
    private final JButton mctsPauseButton = Ui.button("Pause", false,
            event -> toggleNetworkMctsPaused());

    /**
     * MCTS stop button.
     */
    private final HoldButton mctsStopButton = new HoldButton("Stop",
            this::stopNetworkMcts, true);

    /**
     * MCTS visit limit spinner.
     */
    private final JSpinner mctsVisitsSpinner = new JSpinner(
            new SpinnerNumberModel(
                    Defaults.MCTS_VISITS,
                    Defaults.MCTS_VISITS_MIN,
                    Defaults.MCTS_VISITS_MAX,
                    Defaults.MCTS_VISITS_STEP));

    /**
     * MCTS time limit spinner.
     */
    private final JSpinner mctsMillisSpinner = new JSpinner(
            new SpinnerNumberModel(
                    Defaults.MCTS_MILLIS,
                    Defaults.MCTS_MILLIS_MIN,
                    Defaults.MCTS_MILLIS_MAX,
                    Defaults.MCTS_MILLIS_STEP));

    /**
     * MCTS exploration constant spinner.
     */
    private final JSpinner mctsCpuctSpinner = new JSpinner(
            new SpinnerNumberModel(
                    Defaults.MCTS_CPUCT,
                    Defaults.MCTS_CPUCT_MIN,
                    Defaults.MCTS_CPUCT_MAX,
                    Defaults.MCTS_CPUCT_STEP));

    /**
     * Toggle selecting whether the view follows the current MCTS leaf.
     */
    private final ToggleBox mctsFollowLeafToggle = new ToggleBox("Follow leaf", true);

    /**
     * Status badge for MCTS progress.
     */
    private final StatusBadge mctsStatusBadge = new StatusBadge();

    /**
     * MCTS edge-weight details panel.
     */
    private final MctsWeightsPanel mctsWeightsPanel = new MctsWeightsPanel();

    /**
     * Collapsible section containing MCTS edge weights. The weights panel is
     * scrollable so every root edge is reachable while the section stays a
     * compact, fixed-height band in the layout.
     */
    private final JComponent mctsWeightsSection =
            Ui.collapsible("Edge weights",
                    Ui.scroll(mctsWeightsPanel, () -> Theme.CARD), false);

    /**
     * Card layout for loading and architecture views.
     */
    private final CardLayout cards = new CardLayout();

    /**
     * Card panel for loading and architecture views.
     */
    private final JPanel cardPanel = new JPanel(cards);

    /**
     * Loading placeholder panel.
     */
    private final LoadingPanel loadingPanel = new LoadingPanel();

    /**
     * Inspector panel shared by network views.
     */
    private final InspectorPanel inspectorPanel = new InspectorPanel();

    /**
     * Details tab container.
     */
    private final JTabbedPane detailsTabs = Ui.tabbedPane();

    /**
     * Right-side details section.
     */
    private JComponent detailsSection;

    /**
     * Top network toolbar.
     */
    private JPanel networkToolbar;

    /**
     * MCTS control toolbar.
     */
    private JPanel mctsToolbar;

    /**
     * Real activation provider.
     */
    private final RealActivations provider = new RealActivations();

    /**
     * Current main-board FEN.
     */
    private String mainBoardFen = "";

    /**
     * Optional selected override FEN.
     */
    private String overrideFen;

    /**
     * Debounce timer for inference refreshes.
     */
    private final Timer debounceTimer;

    /**
     * Active inference worker.
     */
    private SwingWorker<ActivationSnapshot, Void> inferenceWorker;

    /**
     * Architecture and FEN currently being inferred.
     */
    private String runningArch;
    /**
     * FEN currently being inferred.
     */
    private String runningFen;

    /**
     * Architecture and FEN currently shown in the loading panel.
     */
    private String loadingArch;
    /**
     * FEN currently shown in the loading panel.
     */
    private String loadingFen;

    /**
     * Active MCTS worker.
     */
    private SwingWorker<Void, NetworkMctsFrame> mctsWorker;

    /**
     * Active MCTS search instance.
     */
    private MctsSearch mctsSearch;

    /**
     * True when MCTS is paused.
     */
    private volatile boolean mctsPaused;

    /**
     * True when MCTS should publish leaf positions to the view.
     */
    private volatile boolean mctsFollowLeafEnabled = Defaults.NETWORK_MCTS_FOLLOW_LEAF;

    /**
     * Architecture card currently used for MCTS streaming.
     */
    private volatile String mctsStreamCardKey = ARCH_NNUE;

    /**
     * Latest MCTS leaf FEN.
     */
    private String mctsLeafFen;

    /**
     * Last MCTS progress sound timestamp.
     */
    private long lastMctsProgressSoundNanos;

    /**
     * Pending architecture and FEN requested while inference is busy.
     */
    private String pendingArch;
    /**
     * Pending FEN requested while inference is busy.
     */
    private String pendingFen;

    /**
     * True while this panel is visible in an active editor pane.
     */
    private volatile boolean active;

    /**
     * Cache key currently displayed by the active view.
     */
    private String displayedKey;

    /**
     * Published MCTS frame used for network follow-leaf updates.
     *
     * @param snapshot root search snapshot
     * @param leafCardKey selected leaf architecture key
     * @param leafFen selected leaf FEN
     * @param leafSnapshot selected leaf activations
     */
    private record NetworkMctsFrame(
            MctsSearch.Snapshot snapshot,
            String leafCardKey,
            String leafFen,
            ActivationSnapshot leafSnapshot) {
    }

    /**
     * Creates a standalone network panel with its own header.
     */
    public NetworkPanel() {
        this(true);
    }

    /**
     * Creates the network panel.
     *
     * @param showWorkspaceHeader true to show the standalone Engine Lab header
     */
    public NetworkPanel(boolean showWorkspaceHeader) {
        super(new BorderLayout(), Theme.Surface.BACKDROP);
        setBorder(Theme.pad(Theme.SPACE_SM));
        JPanel north = new JPanel(new BorderLayout(0, showWorkspaceHeader ? Theme.SPACE_SM : 0));
        north.setOpaque(false);
        if (showWorkspaceHeader) {
            north.add(workspaceHeader, BorderLayout.NORTH);
        }
        north.add(buildToolbar(), BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);
        cardPanel.setOpaque(true);
        cardPanel.setBackground(Theme.BG);
        // Each architecture card lives inside its own JScrollPane so the
        // atlas mode can paint a tall mosaic and the user scrolls through
        // it, while abstract/detailed/raw stay zero-overhead.
        addViewCard(nnueView, ARCH_NNUE);
        addViewCard(cnnView, ARCH_CNN);
        addViewCard(bt4View, ARCH_BT4);
        addViewCard(otisView, ARCH_OTIS);
        addViewCard(classicalView, ARCH_CLASSICAL);
        cardPanel.add(loadingPanel, CARD_LOADING);
        nnueView.setInspector(inspectorPanel);
        cnnView.setInspector(inspectorPanel);
        bt4View.setInspector(inspectorPanel);
        otisView.setInspector(inspectorPanel);
        detailsTabs.addTab("Inspector", inspectorPanel);
        detailsTabs.addTab("Legend", createNetworkLegend());
        detailsTabs.setPreferredSize(new Dimension(304, 600));
        detailsTabs.setMinimumSize(new Dimension(232, 260));
        inspectorPanel.setInspectListener(() -> Ui.setCollapsibleExpanded(detailsSection, true));
        // Each architecture view owns its position board. Keep the shared
        // MCTS diagnostics focused on edge weights so all network families
        // use the same one-board visual language.
        mctsWeightsPanel.setLeafBoardVisible(false);
        JPanel content = new JPanel(new BorderLayout(Theme.SPACE_SM, 0));
        content.setOpaque(false);
        content.add(cardPanel, BorderLayout.CENTER);
        detailsSection = Ui.collapsible("Inspector", detailsTabs, false);
        content.add(detailsSection, BorderLayout.EAST);
        JPanel center = new JPanel(new BorderLayout(0, Theme.SPACE_SM));
        center.setOpaque(false);
        center.add(content, BorderLayout.CENTER);
        JPanel lowerDiagnostics = Ui.transparentPanel(new BorderLayout(0, Theme.SPACE_SM));
        lowerDiagnostics.add(buildMctsToolbar(), BorderLayout.NORTH);
        lowerDiagnostics.add(mctsWeightsSection, BorderLayout.CENTER);
        center.add(Ui.collapsible("PUCT probe", lowerDiagnostics, false), BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);
        archCombo.addActionListener(event -> onArchitectureChanged());
        positionCombo.addActionListener(event -> onPositionPicked());
        viewMode.addActionListener(event -> propagateViewMode());
        mctsFollowLeafToggle.setSelected(Defaults.NETWORK_MCTS_FOLLOW_LEAF);
        mctsFollowLeafEnabled = mctsFollowLeafToggle.isSelected();
        mctsFollowLeafToggle.addActionListener(event -> onMctsFollowLeafChanged());
        updateNetworkMctsButtons(false);
        debounceTimer = new Timer(DEBOUNCE_MS, event -> startInference());
        debounceTimer.setRepeats(false);
        updateAtlasAvailability();
        propagateAtlasControls();
        applyFixedScale(false);
        showSelected();
        propagateViewMode();
        refreshToolbarChrome();
        refreshWorkspaceHeader();
        publishCacheDiagnostics(activeCardKey());
    }

    /**
     * Activates expensive work only while the panel is actually attached to a
     * visible editor group.
     */
    @Override
    public void addNotify() {
        super.addNotify();
        setActive(true);
    }

    /**
     * Suspends expensive work as soon as the panel leaves the visible Swing
     * hierarchy.
     */
    @Override
    public void removeNotify() {
        setActive(false);
        super.removeNotify();
    }

    /**
     * Adds one architecture card and remembers its scroll pane for view controls.
     *
     * @param view view component
     * @param key card key
     */
    private void addViewCard(JComponent view, String key) {
        JScrollPane scroll = wrapInScroll(view);
        viewScrolls.put(key, scroll);
        cardPanel.add(scroll, key);
    }

    /**
     * Returns the wrap in scroll.
     *
     * @param view source view
     * @return wrap in scroll
     */
    private static JScrollPane wrapInScroll(JComponent view) {
        // Ui.scroll(...) already applies styleScrollPane; the explicit
        // AS_NEEDED policies were just restating Swing's defaults.
        JScrollPane scroll = Ui.scroll(view);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.getHorizontalScrollBar().setUnitIncrement(24);
        return scroll;
    }

    /**
     * Creates the trace / evaluator legend.
     *
     * @return legend component
     */
    private static JComponent createNetworkLegend() {
        JPanel legend = new JPanel(new GridLayout(0, 1, 0, Theme.SPACE_SM));
        legend.setOpaque(false);
        legend.setBorder(Theme.pad(Theme.SPACE_SM));
        legend.add(Ui.legendRow(TensorViz.POSITIVE, "Positive contribution",
                "Green cells, bars, and edges raise the side-to-move evaluation."));
        legend.add(Ui.legendRow(TensorViz.NEGATIVE, "Negative contribution",
                "Red cells, bars, and edges lower the side-to-move evaluation."));
        legend.add(Ui.legendRow(Theme.withAlpha(Theme.ACCENT, 150), "Opacity / magnitude",
                "Stronger weights and activations are drawn with higher opacity or longer bars."));
        legend.add(Ui.legendRow(TensorViz.FOCUS, "Selected node / layer",
                "Focused nodes, lanes, and layers use the focus accent and drive the inspector."));
        legend.add(Ui.legendRow(Theme.MUTED, "Raw technical values",
                "Dense tensors remain available through Trace, All, and Atlas modes."));
        return Ui.card("Trace Legend", legend);
    }

    /**
     * Scrolls the active evaluator view to its primary graph area.
     */
    private void fitActiveView() {
        resetActiveView();
    }

    /**
     * Resets the active evaluator viewport.
     */
    private void resetActiveView() {
        JScrollPane scroll = viewScrolls.get(activeCardKey());
        if (scroll != null) {
            scroll.getHorizontalScrollBar().setValue(0);
            scroll.getVerticalScrollBar().setValue(0);
        }
        activeView().revalidate();
        activeView().repaint();
    }

    /**
     * Refreshes the Engine Lab workspace context.
     */
    private void refreshWorkspaceHeader() {
        String source = overrideFen != null ? "Pinned position"
                : mctsLeafFen != null && mctsFollowLeafToggle.isSelected() ? "PUCT leaf" : "Current board";
        String mode = switch (viewMode.getSelectedIndex()) {
            case 1 -> "Neural trace";
            case MODE_RAW -> "Raw tensors";
            case MODE_ATLAS -> "Atlas";
            default -> "Evaluator overview";
        };
        workspaceHeader.setContext(displayNameFor(activeCardKey()) + " · " + source + " · " + mode);
    }

    /**
     * Sets the current main-board FEN. When the position picker is following
     * the main board this requests inference for the active architecture;
     * when a canned override is pinned the value is just remembered for when
     * the picker switches back.
     *
     * @param fen current position FEN
     */
    public void setFen(String fen) {
        mainBoardFen = fen == null ? "" : fen;
        if (!active || overrideFen != null) {
            return;
        }
        requestActiveInference();
    }

    /**
     * Sets whether the Network pane is visible. Becoming visible refreshes the
     * current frame; becoming hidden cancels pending inference and suspends
     * MCTS visualization so hidden tabs do not consume paint time.
     *
     * @param value true when visible
     */
    public void setActive(boolean value) {
        if (active == value) {
            return;
        }
        active = value;
        if (active) {
            if (isNetworkMctsRunning() && mctsSearch != null) {
                showNetworkMctsSnapshot(mctsSearch.snapshot(mctsPaused), true);
            } else {
                requestActiveInference();
            }
        } else {
            cancelPendingInference();
        }
    }

    /**
     * Cancels background workers owned by this panel.
     */
    public void dispose() {
        stopNetworkMcts(false);
        debounceTimer.stop();
        if (inferenceWorker != null && !inferenceWorker.isDone()) {
            inferenceWorker.cancel(true);
        }
        runningArch = null;
        runningFen = null;
        loadingArch = null;
        loadingFen = null;
        loadingPanel.stop();
    }

    /**
     * Returns the effective FEN.
     *
     * @return effective FEN text
     */
    private String effectiveFen() {
        if (mctsLeafFen != null && mctsFollowLeafToggle.isSelected()) {
            return mctsLeafFen;
        }
        return baseFen();
    }

    /**
     * Returns the base FEN.
     *
     * @return base FEN text
     */
    private String baseFen() {
        return overrideFen != null ? overrideFen : mainBoardFen;
    }

    /**
     * Returns the card key for.
     *
     * @param comboKey source combo key
     * @return card key for text
     */
    private String cardKeyFor(String comboKey) {
        if (ARCH_CNN.equals(comboKey) || ARCH_CNN_LABEL.equals(comboKey)) {
            return ARCH_CNN;
        }
        if (ARCH_BT4.equals(comboKey) || ARCH_BT4_LABEL.equals(comboKey)) {
            return ARCH_BT4;
        }
        if (ARCH_OTIS.equals(comboKey) || ARCH_OTIS_LABEL.equals(comboKey)) {
            return ARCH_OTIS;
        }
        if (ARCH_CLASSICAL.equals(comboKey) || ARCH_CLASSICAL_LABEL.equals(comboKey)) {
            return ARCH_CLASSICAL;
        }
        return ARCH_NNUE;
    }

    /**
     * Returns the active card key.
     *
     * @return active card key text
     */
    private String activeCardKey() {
        return cardKeyFor((String) archCombo.getSelectedItem());
    }

    /**
     * Returns the cache key.
     *
     * @param cardKey source card key
     * @param fen FEN string
     * @return cache key text
     */
    private static String cacheKey(String cardKey, String fen) {
        return cardKey + "::" + fen;
    }

    /**
     * Schedules inference for the currently active architecture.
     */
    private void requestActiveInference() {
        String cardKey = activeCardKey();
        String fen = effectiveFen();
        if (!active || fen == null || fen.isBlank()) {
            return;
        }
        if (ARCH_CLASSICAL.equals(cardKey)) {
            // The classical evaluator is cheap and model-free, so it skips the
            // debounced background-inference pipeline entirely and renders the
            // term breakdown for the current FEN synchronously.
            classicalView.setFen(fen);
            displayedKey = cacheKey(cardKey, fen);
            cards.show(cardPanel, cardKey);
            statusBadge.success(ARCH_CLASSICAL_LABEL + ": handcrafted, no model needed");
            refreshWorkspaceHeader();
            return;
        }
        String key = cacheKey(cardKey, fen);
        ActivationSnapshot cached = snapshotCache.get(key);
        if (cached != null) {
            applySnapshot(cardKey, fen, cached);
            refreshStatusBadge();
            return;
        }
        if (key.equals(displayedKey)) {
            return;
        }
        showLoading(cardKey, fen, "Preparing " + displayNameFor(cardKey));
        pendingArch = cardKey;
        pendingFen = fen;
        debounceTimer.restart();
    }

    /**
     * Applies a picked position to the network viewer.
     */
    private void onPositionPicked() {
        String label = (String) positionCombo.getSelectedItem();
        String fen = Positions.fenFor(label);
        overrideFen = fen;
        clearMctsLeafOverrideForManualPosition();
        requestActiveInference();
    }

    /**
     * Clears MCTS leaf override for manual position.
     */
    private void clearMctsLeafOverrideForManualPosition() {
        boolean hadSearch = mctsSearch != null || mctsWorker != null || mctsLeafFen != null;
        if (!hadSearch) {
            return;
        }
        stopNetworkMcts(false);
        cancelPendingInference();
        mctsLeafFen = null;
        mctsWeightsPanel.clear();
        mctsStatusBadge.idle("MCTS stopped");
        revalidate();
    }

    /**
     * Builds the build toolbar.
     *
     * @return build toolbar
     */
    private JComponent buildToolbar() {
        JPanel bar = Ui.transparentPanel(new BorderLayout(Theme.SPACE_MD, 0));
        networkToolbar = bar;
        styleToolbarShell(bar, Theme.pad(Theme.SPACE_SM));

        styleToolbarCombo(archCombo, 188,
                "Pick the evaluator to visualise (neural families or the classical hand-crafted evaluator).");
        styleToolbarCombo(positionCombo, 244,
                "Pin a canned position to explore, or follow the main board.");
        viewMode.setToolTipText("Overview, Trace, All, and Atlas switch between curated and raw evaluator internals.");
        exportPngButton.setToolTipText("Render the full current evaluator view to a high-resolution PNG file.");
        fitViewButton.setToolTipText("Scroll the active evaluator view to its primary graph area.");
        resetViewButton.setToolTipText("Reset the active evaluator viewport to the top-left.");

        JPanel controls = Ui.transparentPanel(
                new WrappingFlowLayout(FlowLayout.LEFT, Theme.SPACE_SM, 0));
        controls.add(archCombo);
        controls.add(positionCombo);
        controls.add(viewMode);
        controls.add(fitViewButton);
        controls.add(resetViewButton);

        JPanel actions = Ui.transparentPanel(
                new FlowLayout(FlowLayout.RIGHT, Theme.SPACE_SM, 0));
        actions.add(Ui.button("Copy command", false, event -> copyCliCommand()));
        actions.add(exportPngButton);
        actions.add(statusBadge);

        bar.add(controls, BorderLayout.CENTER);
        bar.add(actions, BorderLayout.EAST);
        JPanel outer = Ui.transparentPanel(new BorderLayout(0, Theme.SPACE_XS));
        outer.add(bar, BorderLayout.NORTH);
        return outer;
    }

    /**
     * Builds the build MCTS toolbar.
     *
     * @return build MCTS toolbar
     */
    private JComponent buildMctsToolbar() {
        JPanel bar = Ui.transparentPanel(new BorderLayout(Theme.SPACE_MD, 0));
        mctsToolbar = bar;
        styleToolbarShell(bar, Theme.pad(Theme.SPACE_XS, Theme.SPACE_SM,
                Theme.SPACE_XS, Theme.SPACE_SM));

        Ui.styleIntegerSpinner(mctsVisitsSpinner);
        Ui.styleIntegerSpinner(mctsMillisSpinner);
        Ui.styleIntegerSpinner(mctsCpuctSpinner);
        mctsVisitsSpinner.setPreferredSize(new Dimension(86, Theme.CONTROL_HEIGHT));
        mctsMillisSpinner.setPreferredSize(new Dimension(86, Theme.CONTROL_HEIGHT));
        mctsCpuctSpinner.setPreferredSize(new Dimension(72, Theme.CONTROL_HEIGHT));
        mctsCpuctSpinner.setToolTipText("Cpuct: PUCT exploration constant. Higher values explore prior-favored moves more aggressively.");
        mctsStartButton.setToolTipText("Run PUCT from the current board/canned position and stream each leaf into the evaluator view.");
        mctsPauseButton.setToolTipText("Pause or resume the PUCT worker while keeping the current leaf on screen.");
        mctsStopButton.setToolTipText("Stop PUCT and return the network view to the board/canned position.");
        mctsFollowLeafToggle.setToolTipText("When on, the evaluator view shows the leaf currently being evaluated.");
        mctsFollowLeafToggle.setSelected(Defaults.NETWORK_MCTS_FOLLOW_LEAF);
        mctsStatusBadge.setFixedTextWidth(NETWORK_MCTS_STATUS_TEXT_WIDTH);
        mctsStatusBadge.idle("MCTS idle");

        JPanel controls = Ui.transparentPanel(
                new WrappingFlowLayout(FlowLayout.LEFT, Theme.SPACE_SM, 0));
        controls.add(Ui.label("PUCT"));
        controls.add(mctsStartButton);
        controls.add(mctsPauseButton);
        controls.add(mctsStopButton);
        controls.add(Ui.label("Visits"));
        controls.add(mctsVisitsSpinner);
        controls.add(Ui.label("Millis"));
        controls.add(mctsMillisSpinner);
        controls.add(Ui.label("Cpuct"));
        controls.add(mctsCpuctSpinner);
        controls.add(mctsFollowLeafToggle);

        JPanel status = Ui.transparentPanel(
                new FlowLayout(FlowLayout.RIGHT, Theme.SPACE_SM, 0));
        status.add(mctsStatusBadge);

        bar.add(controls, BorderLayout.CENTER);
        bar.add(status, BorderLayout.EAST);
        return bar;
    }

    /**
     * Refreshes the refresh toolbar chrome.
     */
    private void refreshToolbarChrome() {
        if (networkToolbar != null) {
            styleToolbarShell(networkToolbar, Theme.pad(Theme.SPACE_SM));
        }
        if (mctsToolbar != null) {
            styleToolbarShell(mctsToolbar, Theme.pad(Theme.SPACE_XS, Theme.SPACE_SM,
                    Theme.SPACE_XS, Theme.SPACE_SM));
        }
    }

    /**
     * Applies compact styling to the network toolbar shell.
     *
     * @param bar progress bar
     * @param padding source padding
     */
    private static void styleToolbarShell(JPanel bar, Border padding) {
        Ui.styleToolbarBand(bar, padding);
    }

    /**
     * Applies compact styling to a network toolbar selector.
     *
     * @param combo source combo
     * @param width width in pixels
     * @param tooltip tooltip text
     */
    private static void styleToolbarCombo(JComboBox<?> combo, int width, String tooltip) {
        Ui.styleCombo(combo);
        combo.setPreferredSize(new Dimension(width, Theme.CONTROL_HEIGHT));
        combo.setToolTipText(tooltip);
    }

    /**
     * Pushes atlas control settings into each architecture view.
     */
    private void propagateAtlasControls() {
        nnueView.setAtlasSort("magnitude");
        nnueView.setAtlasCompareData(null);
        nnueView.setAtlasOverlay(false);
        nnueView.setAtlasGrid(false, java.util.List.of());
    }

    /**
     * One labelled atlas for use by the grid renderer.
     */
    public static final class NnueAtlasEntry {

        /**
         * Display label.
         */
    final String label;

        /**
         * Cached atlas snapshot.
         */
    final ActivationSnapshot atlas;

        /**
         * Creates an atlas entry.
         *
         * @param label display label
         * @param atlas atlas snapshot
         */
        NnueAtlasEntry(String label, ActivationSnapshot atlas) {
            this.label = label;
            this.atlas = atlas;
        }
    }

    /**
     * Returns the active view.
     *
     * @return active view
     */
    private JComponent activeView() {
        return switch (activeCardKey()) {
            case ARCH_CNN -> cnnView;
            case ARCH_BT4 -> bt4View;
            case ARCH_OTIS -> otisView;
            case ARCH_CLASSICAL -> classicalView;
            default -> nnueView;
        };
    }

    /**
     * Exports the active network view as a PNG image.
     */
    private void exportPng() {
        JComponent target = activeView();
        java.awt.Dimension original = target.getSize();
        int w = Math.max(1, Math.max(target.getWidth(), target.getPreferredSize().width));
        int h = Math.max(1, Math.max(target.getHeight(), target.getPreferredSize().height));
        try {
            double scale = exportScale(w, h);
            BufferedImage img = RenderAcceleration.translucentImage(
                    Math.max(1, (int) Math.ceil(w * scale)),
                    Math.max(1, (int) Math.ceil(h * scale)));
            java.awt.Graphics2D g = img.createGraphics();
            try {
                // Lay the view out at the full capture size, paint, then let
                // the scroll pane restore it on the next validation pass.
                target.setSize(w, h);
                target.doLayout();
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                        java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.scale(scale, scale);
                target.paint(g);
            } finally {
                g.dispose();
                target.setSize(original);
                target.revalidate();
                target.repaint();
            }
            String stamp = LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            String arch = displayNameFor(activeCardKey()).replaceAll("\\W+", "_");
            File dir = new File(Prefs.exportDir());
            Files.createDirectories(dir.toPath());
            File out = new File(dir, "workbench-" + arch + "-" + stamp + ".png");
            ImageIO.write(img, "png", out);
            Prefs.setExportDir(dir.getPath());
            statusBadge.success("rendered " + out.getName());
            toast(Toast.Kind.SUCCESS, "Exported " + out.getName());
        } catch (IOException ex) {
            statusBadge.error("export failed: " + ex.getMessage());
            toast(Toast.Kind.ERROR, "Export failed: " + ex.getMessage());
        }
    }

    /**
     * Returns the export scale.
     *
     * @param width width in pixels
     * @param height height in pixels
     * @return export scale
     */
    private static double exportScale(int width, int height) {
        return width * PNG_EXPORT_SCALE <= PNG_EXPORT_MAX_DIMENSION
                && height * PNG_EXPORT_SCALE <= PNG_EXPORT_MAX_DIMENSION
                        ? PNG_EXPORT_SCALE
                        : 1.0;
    }

    /**
     * Shows a transient status message in the network view.
     *
     * @param kind kind label
     * @param message diagnostic message
     */
    private void toast(Toast.Kind kind, String message) {
        java.awt.Window window = SwingUtilities.getWindowAncestor(this);
        if (window instanceof JFrame frame) {
            Toast.show(frame, kind, message);
        }
    }

    /**
     * Copies the equivalent {@code crtk engine trace} command for the active
     * architecture and position to the system clipboard.
     */
    private void copyCliCommand() {
        java.util.List<String> args = new java.util.ArrayList<>(java.util.List.of("engine", "trace"));
        String fen = effectiveFen();
        if (fen != null && !fen.isBlank()) {
            args.add("--fen");
            args.add(fen);
        }
        traceBackendArgs(args, (String) archCombo.getSelectedItem());
        CommandRunner.copyToClipboard(CommandRunner.displayCommand(args));
        statusBadge.success("command copied");
        toast(Toast.Kind.SUCCESS, "Copied to clipboard");
    }

    /**
     * Appends the {@code engine trace} backend flags for an architecture label.
     *
     * @param args mutable argument list
     * @param archLabel selected architecture label
     */
    private static void traceBackendArgs(List<String> args, String archLabel) {
        if (ARCH_CNN_LABEL.equals(archLabel)) {
            args.add("--lc0");
        } else if (ARCH_BT4_LABEL.equals(archLabel)) {
            args.add("--bt4");
            args.add("--weights");
            args.add("models/bt4-1024x15x32h.bin");
        } else if (ARCH_OTIS_LABEL.equals(archLabel)) {
            args.add("--otis");
        } else if (ARCH_CLASSICAL_LABEL.equals(archLabel)) {
            args.add("--classical");
        } else {
            args.add("--nnue");
        }
    }

    /**
     * Switches the visible architecture card and refreshes inference.
     */
    private void onArchitectureChanged() {
        mctsStreamCardKey = activeCardKey();
        updateAtlasAvailability();
        // Re-apply simplified atlas defaults after an architecture change.
        propagateAtlasControls();
        showSelected();
        if (isNetworkMctsRunning()) {
            startNetworkMcts();
            if (!isNetworkMctsRunning()) {
                requestActiveInference();
            }
            return;
        }
        // Make sure the now-visible card reflects the current position —
        // an instant cache hit when it has been seen before, otherwise a
        // debounced single-architecture inference.
        requestActiveInference();
    }

    /**
     * Refreshes the update atlas availability.
     */
    private void updateAtlasAvailability() {
        viewMode.setSegmentEnabled(MODE_ATLAS, true);
    }

    /**
     * Shows selected.
     */
    private void showSelected() {
        String cardKey = cardKeyFor((String) archCombo.getSelectedItem());
        if (isLoadingActiveCard(cardKey)) {
            cards.show(cardPanel, CARD_LOADING);
            return;
        }
        cards.show(cardPanel, cardKey);
        propagateViewMode();
        refreshStatusBadge();
        refreshWorkspaceHeader();
    }

    /**
     * Returns whether loading active card.
     *
     * @param cardKey source card key
     * @return true when loading active card
     */
    private boolean isLoadingActiveCard(String cardKey) {
        if (!loadingPanel.isActive()) {
            return false;
        }
        String fen = effectiveFen();
        String activeKey = fen == null || fen.isBlank() ? null : cacheKey(cardKey, fen);
        String loadingKey = loadingArch == null || loadingFen == null
                ? null
                : cacheKey(loadingArch, loadingFen);
        String pendingKey = pendingArch == null || pendingFen == null
                ? null
                : cacheKey(pendingArch, pendingFen);
        String runningKey = runningArch == null || runningFen == null
                ? null
                : cacheKey(runningArch, runningFen);
        return loadingKey != null
                && loadingKey.equals(activeKey)
                && (loadingKey.equals(pendingKey) || loadingKey.equals(runningKey));
    }

    /**
     * Refreshes the refresh status badge.
     */
    private void refreshStatusBadge() {
        String cardKey = activeCardKey();
        if (cardKey == null) {
            statusBadge.idle("");
            refreshWorkspaceHeader();
            return;
        }
        if (ARCH_CLASSICAL.equals(cardKey)) {
            statusBadge.success(ARCH_CLASSICAL_LABEL + ": handcrafted, no model needed");
            refreshWorkspaceHeader();
            return;
        }
        String statusKey;
        if (ARCH_CNN.equals(cardKey)) {
            statusKey = "cnn";
        } else if (ARCH_BT4.equals(cardKey)) {
            statusKey = "bt4";
        } else if (ARCH_OTIS.equals(cardKey)) {
            statusKey = "otis";
        } else {
            statusKey = "nnue";
        }
        String status = provider.statusFor(statusKey);
        String message = displayNameFor(cardKey) + ": " + status;
        // "real inference" is the healthy state; anything mentioning an error
        // or a synthetic fallback is worth flagging more loudly.
        if (status.startsWith("real")) {
            statusBadge.success(message);
        } else {
            statusBadge.idle(message);
        }
        refreshWorkspaceHeader();
    }

    /**
     * Builds the build arch options.
     *
     * @return build arch options
     */
    private static String[] buildArchOptions() {
        return new String[] {
                ARCH_NNUE_LABEL, ARCH_CNN_LABEL, ARCH_BT4_LABEL, ARCH_OTIS_LABEL, ARCH_CLASSICAL_LABEL };
    }

    /**
     * Pushes the abstract/detail mode into each architecture view.
     */
    private void propagateViewMode() {
        ViewMode mode = selectedViewMode();
        nnueView.setViewMode(mode);
        cnnView.setViewMode(mode);
        bt4View.setViewMode(mode);
        otisView.setViewMode(mode);
        classicalView.setViewMode(mode);
        // The Atlas sub-controls only do anything in Atlas mode — grey them
        // out elsewhere so the toolbar shows what is actually live.
        updateAtlasAvailability();
        cardPanel.revalidate();
        cardPanel.repaint();
        refreshWorkspaceHeader();
    }

    /**
     * Returns the view mode.
     *
     * @return view mode
     */
    private ViewMode selectedViewMode() {
        return switch (viewMode.getSelectedIndex()) {
            case 1 -> ViewMode.DETAILED;
            case MODE_RAW -> ViewMode.RAW;
            case MODE_ATLAS -> ViewMode.ATLAS;
            default -> ViewMode.ABSTRACT;
        };
    }

    /**
     * Applies fixed scale.
     *
     * @param fixed whether fixed
     */
    private void applyFixedScale(boolean fixed) {
        nnueView.setFixedScale(fixed);
        cnnView.setFixedScale(fixed);
        bt4View.setFixedScale(fixed);
        otisView.setFixedScale(fixed);
        cardPanel.repaint();
    }

    /**
     * Starts network MCTS.
     */
    private void startNetworkMcts() {
        stopNetworkMcts(false);
        cancelPendingInference();
        Position root;
        try {
            String fen = baseFen();
            if (fen == null || fen.isBlank()) {
                throw new IllegalArgumentException("no position is loaded");
            }
            root = new Position(fen);
        } catch (IllegalArgumentException ex) {
            mctsStatusBadge.error("MCTS: " + ex.getMessage());
            SoundService.play(SoundCue.ILLEGAL);
            return;
        }
        int budget = ((Number) mctsVisitsSpinner.getValue()).intValue();
        long maxMillis = ((Number) mctsMillisSpinner.getValue()).longValue();
        double cpuct = ((Number) mctsCpuctSpinner.getValue()).doubleValue();
        String searchCardKey = activeCardKey();
        MctsSearch builtSearch;
        try {
            builtSearch = createNetworkMctsSearch(root, cpuct, searchCardKey);
        } catch (IOException ex) {
            mctsStatusBadge.error("MCTS " + displayNameFor(searchCardKey) + ": " + ex.getMessage());
            SoundService.play(SoundCue.JOB_FAILURE);
            return;
        }
        mctsPaused = false;
        mctsFollowLeafEnabled = mctsFollowLeafToggle.isSelected();
        mctsStreamCardKey = searchCardKey;
        mctsSearch = builtSearch;
        final MctsSearch search = mctsSearch;
        mctsLeafFen = root.toString();
        lastMctsProgressSoundNanos = 0L;
        updateNetworkMctsButtons(true);
        Ui.setCollapsibleExpanded(mctsWeightsSection, true);
        mctsStatusBadge.busy("starting " + search.backendName() + " MCTS...");
        SoundService.play(SoundCue.MCTS_START);
        SwingWorker<Void, NetworkMctsFrame> activeWorker = new SwingWorker<>() {
            /**
             * {@inheritDoc}
             */
            @Override
            protected Void doInBackground() throws Exception {
                publishNetworkMctsFrame(buildNetworkMctsFrame(search.snapshot(false)), 0L);
                long lastPublishNanos = System.nanoTime();
                while (!isCancelled()) {
                    if (!active) {
                        Thread.sleep(120L);
                        continue;
                    }
                    if (!search.shouldContinue(budget, maxMillis)) {
                        break;
                    }
                    if (mctsPaused) {
                        publishNetworkMctsFrame(buildNetworkMctsFrame(search.snapshot(true)), 0L);
                        lastPublishNanos = System.nanoTime();
                        Thread.sleep(80L);
                        continue;
                    }
                    long nextPlayout = search.playouts() + 1L;
                    long now = System.nanoTime();
                    boolean treeFrameDue = shouldPublishNetworkMctsFrame(nextPlayout, now, lastPublishNanos);
                    boolean leafFrameDue = mctsFollowLeafEnabled;
                    if (treeFrameDue || leafFrameDue) {
                        NetworkMctsFrame frame = buildNetworkMctsFrame(search.previewNextLeaf(false));
                        if (treeFrameDue) {
                            publishNetworkMctsFrame(frame, nextPlayout);
                            lastPublishNanos = now;
                        }
                        if (leafFrameDue && !isCancelled()) {
                            NetworkMctsFrame activationFrame = buildNetworkMctsLeafActivationFrame(frame);
                            if (!isCancelled() && mctsFollowLeafEnabled
                                    && activationFrame.leafSnapshot() != null) {
                                applyLeafFrameSynchronously(this, search, activationFrame);
                            }
                        }
                    }
                    search.iterate();
                    if (search.playouts() % NETWORK_MCTS_YIELD_INTERVAL == 0) {
                        Thread.sleep(1L);
                    }
                }
                publishNetworkMctsFrame(buildNetworkMctsFrame(search.snapshot(mctsPaused)), search.playouts());
                return null;
            }

            /**
             * Publishes one network-MCTS frame to the Swing UI.
             *
             * @param frame frame instance
             * @param playouts source playouts
             */
            private void publishNetworkMctsFrame(NetworkMctsFrame frame, long playouts)
                    throws InterruptedException {
                publish(frame);
                Thread.sleep(networkMctsFrameDelay(playouts));
            }

            /**
             * {@inheritDoc}
             */
            @Override
            protected void process(List<NetworkMctsFrame> chunks) {
                if (mctsWorker != this || mctsSearch != search || chunks.isEmpty() || !active) {
                    return;
                }
                NetworkMctsFrame frame = latestNetworkMctsFrameForDisplay(chunks);
                showNetworkMctsSnapshot(frame.snapshot(), true,
                        frame.leafCardKey(), frame.leafFen(), frame.leafSnapshot());
            }

            /**
             * {@inheritDoc}
             */
            @Override
            protected void done() {
                if (mctsWorker != this || mctsSearch != search) {
                    return;
                }
                updateNetworkMctsButtons(false);
                boolean failed = false;
                try {
                    get();
                } catch (CancellationException ex) {
                    // stop button already restored the toolbar state
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    failed = true;
                } catch (ExecutionException ex) {
                    failed = true;
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    mctsStatusBadge.error("MCTS failed: "
                            + cause.getClass().getSimpleName() + ": " + cause.getMessage());
                    SoundService.play(SoundCue.JOB_FAILURE);
                }
                if (!isCancelled() && !failed) {
                    if (active) {
                        showNetworkMctsSnapshot(search.snapshot(mctsPaused), false);
                    }
                    SoundService.play(SoundCue.MCTS_COMPLETE);
                }
                search.close();
            }
        };
        mctsWorker = activeWorker;
        activeWorker.execute();
    }

    /**
     * Applies leaf frame synchronously.
     *
     * @param worker source worker
     * @param search source search
     * @param frame frame instance
     * @throws java.lang.InterruptedException if the worker thread is interrupted
     * @throws java.util.concurrent.ExecutionException if the worker task fails
     */
    private void applyLeafFrameSynchronously(
            SwingWorker<?, ?> worker,
            MctsSearch search,
            NetworkMctsFrame frame) throws InterruptedException, ExecutionException {
        if (frame == null || frame.leafSnapshot() == null
                || frame.leafCardKey() == null || frame.leafFen() == null) {
            return;
        }
        try {
            SwingUtilities.invokeAndWait(() -> {
                if (mctsWorker != worker || mctsSearch != search || !active
                        || !mctsFollowLeafToggle.isSelected()) {
                    return;
                }
                showNetworkMctsSnapshot(frame.snapshot(), true,
                        frame.leafCardKey(), frame.leafFen(), frame.leafSnapshot());
                paintActiveNetworkViewImmediately();
            });
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            throw new ExecutionException(cause);
        }
    }

    /**
     * Draws the paint active network view immediately.
     */
    private void paintActiveNetworkViewImmediately() {
        JComponent target = activeView();
        if (target.isShowing() && target.getWidth() > 0 && target.getHeight() > 0) {
            target.paintImmediately(0, 0, target.getWidth(), target.getHeight());
        }
    }

    /**
     * Creates the create network MCTS search.
     *
     * @param root root position or node
     * @param cpuct PUCT exploration constant
     * @param cardKey source card key
     * @return create network MCTS search
     * @throws java.io.IOException if external I/O or engine communication fails
     */
    private MctsSearch createNetworkMctsSearch(Position root, double cpuct, String cardKey) throws IOException {
        return switch (cardKey) {
            case ARCH_CNN -> MctsSearch.cnn(root, cpuct, RealActivations.cnnPath());
            case ARCH_BT4 -> MctsSearch.bt4(root, cpuct, RealActivations.bt4Path());
            case ARCH_OTIS -> MctsSearch.otis(root, cpuct, RealActivations.otisPath());
            case ARCH_CLASSICAL -> new MctsSearch(root, cpuct);
            default -> MctsSearch.nnue(root, cpuct, provider.nnuePath());
        };
    }

    /**
     * Toggles network MCTS paused.
     */
    private void toggleNetworkMctsPaused() {
        if (!isNetworkMctsRunning()) {
            return;
        }
        mctsPaused = !mctsPaused;
        updateNetworkMctsButtons(true);
        SoundService.play(mctsPaused ? SoundCue.MCTS_PAUSE : SoundCue.MCTS_RESUME);
        if (mctsSearch != null) {
            showNetworkMctsSnapshot(mctsSearch.snapshot(mctsPaused), true);
        }
    }

    /**
     * Stops network MCTS.
     */
    private void stopNetworkMcts() {
        stopNetworkMcts(true);
    }

    /**
     * Stops network MCTS.
     *
     * @param restoreBase whether restore base
     */
    private void stopNetworkMcts(boolean restoreBase) {
        MctsSearch activeSearch = mctsSearch;
        boolean cancelled = mctsWorker != null && !mctsWorker.isDone();
        if (cancelled) {
            mctsWorker.cancel(true);
        }
        mctsWorker = null;
        mctsSearch = null;
        mctsPaused = false;
        if (activeSearch != null) {
            activeSearch.close();
        }
        updateNetworkMctsButtons(false);
        if (restoreBase) {
            cancelPendingInference();
            mctsLeafFen = null;
            mctsWeightsPanel.clear();
            mctsStatusBadge.idle("MCTS stopped");
            if (cancelled) {
                SoundService.play(SoundCue.MCTS_STOP);
            }
            revalidate();
            requestActiveInference();
        }
    }

    /**
     * Returns whether network MCTS running.
     *
     * @return true when network MCTS running
     */
    private boolean isNetworkMctsRunning() {
        return mctsWorker != null && !mctsWorker.isDone();
    }

    /**
     * Shows network MCTS snapshot.
     *
     * @param snapshot captured snapshot
     * @param running whether running
     */
    private void showNetworkMctsSnapshot(MctsSearch.Snapshot snapshot, boolean running) {
        showNetworkMctsSnapshot(snapshot, running, null, null, null);
    }

    /**
     * Returns the latest network MCTS frame for display.
     *
     * @param chunks source chunks
     * @return latest network MCTS frame for display
     */
    private static NetworkMctsFrame latestNetworkMctsFrameForDisplay(List<NetworkMctsFrame> chunks) {
        NetworkMctsFrame last = chunks.get(chunks.size() - 1);
        for (int i = chunks.size() - 1; i >= 0; --i) {
            NetworkMctsFrame frame = chunks.get(i);
            if (frame.leafSnapshot() != null
                    && !isOlderNetworkMctsFrame(frame, last)) {
                return frame;
            }
        }
        return last;
    }

    /**
     * Returns whether older network MCTS frame.
     *
     * @param left source left
     * @param right source right
     * @return true when older network MCTS frame
     */
    private static boolean isOlderNetworkMctsFrame(NetworkMctsFrame left, NetworkMctsFrame right) {
        if (left == null || right == null || left.snapshot() == null || right.snapshot() == null) {
            return false;
        }
        return left.snapshot().playouts() < right.snapshot().playouts();
    }

    /**
     * Builds the build network MCTS frame.
     *
     * @param snapshot captured snapshot
     * @return build network MCTS frame
     */
    private NetworkMctsFrame buildNetworkMctsFrame(MctsSearch.Snapshot snapshot) {
        if (snapshot == null
                || !mctsFollowLeafEnabled
                || snapshot.exploringPosition() == null) {
            return new NetworkMctsFrame(snapshot, null, null, null);
        }
        String fen = snapshot.exploringPosition().toString();
        String cardKey = mctsStreamCardKey == null ? ARCH_NNUE : mctsStreamCardKey;
        return new NetworkMctsFrame(snapshot, cardKey, fen, null);
    }

    /**
     * Builds the build network MCTS leaf activation frame.
     *
     * @param frame frame instance
     * @return build network MCTS leaf activation frame
     */
    private NetworkMctsFrame buildNetworkMctsLeafActivationFrame(NetworkMctsFrame frame) {
        // Classical needs no neural inference; like the hidden / follow-leaf-off
        // cases it returns the frame unchanged and the view follows the leaf FEN
        // directly from the published frame.
        if (frame == null
                || frame.leafCardKey() == null
                || frame.leafFen() == null
                || frame.leafFen().isBlank()
                || ARCH_CLASSICAL.equals(frame.leafCardKey())
                || !mctsFollowLeafEnabled
                || !active) {
            return frame;
        }
        ActivationSnapshot leafSnapshot = inferSnapshotQuietly(frame.leafCardKey(), frame.leafFen());
        return new NetworkMctsFrame(frame.snapshot(), frame.leafCardKey(), frame.leafFen(), leafSnapshot);
    }

    /**
     * Shows network MCTS snapshot.
     *
     * @param snapshot captured snapshot
     * @param running whether running
     * @param leafCardKey source leaf card key
     * @param leafFen source leaf fen
     * @param leafSnapshot source leaf snapshot
     */
    private void showNetworkMctsSnapshot(MctsSearch.Snapshot snapshot,
            boolean running, String leafCardKey, String leafFen, ActivationSnapshot leafSnapshot) {
        if (snapshot == null || !active) {
            return;
        }
        boolean visibleBefore = mctsWeightsPanel.isVisible();
        mctsWeightsPanel.setSnapshot(snapshot);
        if (visibleBefore != mctsWeightsPanel.isVisible()) {
            revalidate();
        }
        if (mctsFollowLeafToggle.isSelected() && snapshot.exploringPosition() != null) {
            String fen = leafFen == null ? snapshot.exploringPosition().toString() : leafFen;
            mctsLeafFen = fen;
            applyNetworkMctsLeafSnapshot(leafCardKey == null ? activeCardKey() : leafCardKey, fen, leafSnapshot);
        }
        String leaf = snapshot.exploringLineText();
        if (leaf == null || leaf.isBlank()) {
            leaf = "root";
        }
        String best = snapshot.bestMove() == Move.NO_MOVE ? "-" : Move.toString(snapshot.bestMove());
        String backend = mctsSearch == null ? displayNameFor(mctsStreamCardKey) : mctsSearch.backendName();
        String message = String.format("MCTS %,d visits · %s · root %s · best %s",
                snapshot.playouts(),
                backend,
                snapshot.rootScoreLabel(),
                best);
        String detail = message + " · leaf " + leaf;
        if (snapshot.paused()) {
            mctsStatusBadge.idle("paused · " + message);
            mctsStatusBadge.setToolTipText("paused · " + detail);
        } else if (running || isNetworkMctsRunning()) {
            mctsStatusBadge.busy(message);
            mctsStatusBadge.setToolTipText(detail);
            maybePlayNetworkMctsProgress(snapshot);
        } else {
            mctsStatusBadge.success("done · " + message);
            mctsStatusBadge.setToolTipText("done · " + detail);
        }
    }

    /**
     * Returns whether publish network MCTS frame.
     *
     * @param playouts source playouts
     * @param nowNanos duration in nanoseconds
     * @param lastPublishNanos duration in nanoseconds
     * @return true when publish network MCTS frame
     */
    private static boolean shouldPublishNetworkMctsFrame(long playouts, long nowNanos, long lastPublishNanos) {
        return playouts <= NETWORK_MCTS_WARMUP_PLAYOUTS
                || playouts % NETWORK_MCTS_PUBLISH_INTERVAL == 0L
                || nowNanos - lastPublishNanos >= NETWORK_MCTS_MIN_UPDATE_NANOS;
    }

    /**
     * Returns the network MCTS frame delay.
     *
     * @param playouts source playouts
     * @return network MCTS frame delay
     */
    private static long networkMctsFrameDelay(long playouts) {
        return playouts <= NETWORK_MCTS_WARMUP_PLAYOUTS
                ? Math.max(NETWORK_MCTS_FRAME_YIELD_MS, NETWORK_MCTS_WARMUP_DELAY_MS)
                : NETWORK_MCTS_FRAME_YIELD_MS;
    }

    /**
     * Applies network MCTS leaf snapshot.
     *
     * @param cardKey source card key
     * @param fen FEN string
     * @param leafSnapshot source leaf snapshot
     */
    private void applyNetworkMctsLeafSnapshot(String cardKey, String fen, ActivationSnapshot leafSnapshot) {
        if (!active || cardKey == null || fen == null || fen.isBlank()) {
            return;
        }
        if (ARCH_CLASSICAL.equals(cardKey)) {
            classicalView.setFen(fen);
            if (cardKey.equals(activeCardKey())) {
                displayedKey = cacheKey(cardKey, fen);
                cards.show(cardPanel, cardKey);
            }
            return;
        }
        String key = cacheKey(cardKey, fen);
        ActivationSnapshot snapshot = leafSnapshot == null ? snapshotCache.get(key) : leafSnapshot;
        if (snapshot == null) {
            return;
        }
        snapshotCache.put(key, snapshot);
        publishCacheDiagnostics(cardKey);
        applySnapshot(cardKey, fen, snapshot);
    }

    /**
     * Plays throttled feedback for network-MCTS progress.
     *
     * @param snapshot captured snapshot
     */
    private void maybePlayNetworkMctsProgress(MctsSearch.Snapshot snapshot) {
        if (snapshot.playouts() < NETWORK_MCTS_PUBLISH_INTERVAL) {
            return;
        }
        long now = System.nanoTime();
        if (lastMctsProgressSoundNanos == 0L
                || now - lastMctsProgressSoundNanos >= NETWORK_MCTS_SOUND_INTERVAL_NANOS) {
            lastMctsProgressSoundNanos = now;
            SoundService.play(SoundCue.MCTS_PROGRESS);
        }
    }

    /**
     * Updates leaf-follow behavior after the toggle changes.
     */
    private void onMctsFollowLeafChanged() {
        mctsFollowLeafEnabled = mctsFollowLeafToggle.isSelected();
        if (!mctsFollowLeafToggle.isSelected()) {
            mctsLeafFen = null;
            requestActiveInference();
            return;
        }
        if (mctsSearch != null) {
            showNetworkMctsSnapshot(mctsSearch.snapshot(mctsPaused), isNetworkMctsRunning());
        }
    }

    /**
     * Refreshes the update network MCTS buttons.
     *
     * @param running whether running
     */
    private void updateNetworkMctsButtons(boolean running) {
        mctsStartButton.setEnabled(!running);
        mctsPauseButton.setEnabled(running);
        mctsStopButton.setEnabled(running);
        mctsPauseButton.setText(mctsPaused ? "Resume" : "Pause");
    }

    /**
     * Cancels pending inference.
     */
    private void cancelPendingInference() {
        debounceTimer.stop();
        pendingArch = null;
        pendingFen = null;
        if (inferenceWorker != null && !inferenceWorker.isDone()) {
            inferenceWorker.cancel(true);
        }
        runningArch = null;
        runningFen = null;
        clearLoading(null, null);
    }

    /**
     * Returns the infer snapshot.
     *
     * @param cardKey source card key
     * @param fen FEN string
     * @return infer snapshot
     */
    private ActivationSnapshot inferSnapshot(String cardKey, String fen) {
    return switch (cardKey) {
            case ARCH_CNN -> provider.inferCnn(fen,
                    (architecture, phase, path) -> updateLoadingPhase(cardKey, fen, phase));
            case ARCH_BT4 -> provider.inferBt4(fen,
                    (architecture, phase, path) -> updateLoadingPhase(cardKey, fen, phase));
            case ARCH_OTIS -> provider.inferOtis(fen,
                    (architecture, phase, path) -> updateLoadingPhase(cardKey, fen, phase));
            default -> provider.inferNnue(fen,
                    (architecture, phase, path) -> updateLoadingPhase(cardKey, fen, phase));
        };
    }

    /**
     * Returns the infer snapshot quietly.
     *
     * @param cardKey source card key
     * @param fen FEN string
     * @return infer snapshot quietly
     */
    private ActivationSnapshot inferSnapshotQuietly(String cardKey, String fen) {
        return switch (cardKey) {
            case ARCH_CNN -> provider.inferCnn(fen);
            case ARCH_BT4 -> provider.inferBt4(fen);
            case ARCH_OTIS -> provider.inferOtis(fen);
            default -> provider.inferNnue(fen);
        };
    }

    /**
     * Refreshes the update loading phase.
     *
     * @param cardKey source card key
     * @param fen FEN string
     * @param phase pipeline phase
     */
    private void updateLoadingPhase(String cardKey, String fen, RealActivations.Phase phase) {
        SwingUtilities.invokeLater(() -> {
            if (!cardKey.equals(loadingArch) || !fen.equals(loadingFen)) {
                return;
            }
            String display = displayNameFor(cardKey);
            loadingPanel.start(loadingTitle(cardKey, phase),
                    loadingDetail(phase),
                    loadingModelDetail(cardKey),
                    loadingPositionDetail(fen));
            String badge = phase == RealActivations.Phase.RUNNING_INFERENCE
                    ? "running " + display.toLowerCase(java.util.Locale.ROOT) + " inference..."
                    : phase == RealActivations.Phase.LOADING_MODEL
                            ? "loading " + display.toLowerCase(java.util.Locale.ROOT) + " model..."
                            : "using " + display.toLowerCase(java.util.Locale.ROOT) + " fallback...";
            statusBadge.busy(badge);
        });
    }

    /**
     * Reads the loading title.
     *
     * @param cardKey source card key
     * @param phase pipeline phase
     * @return loading title text
     */
    private static String loadingTitle(String cardKey, RealActivations.Phase phase) {
        String display = displayNameFor(cardKey);
        return switch (phase) {
            case LOADING_MODEL -> "Loading " + display;
            case RUNNING_INFERENCE -> "Running " + display;
            case SYNTHETIC_FALLBACK -> "Preparing fallback";
        };
    }

    /**
     * Formats the loading detail.
     *
     * @param phase pipeline phase
     * @return loading detail text
     */
    private static String loadingDetail(RealActivations.Phase phase) {
        return switch (phase) {
            case LOADING_MODEL -> "Loading model weights";
            case RUNNING_INFERENCE -> "Running inference";
            case SYNTHETIC_FALLBACK -> "Using generated activations";
        };
    }

    /**
     * Starts inference.
     */
    private void startInference() {
    final String fen = pendingFen;
    final String cardKey = pendingArch;
        pendingFen = null;
        pendingArch = null;
        if (fen == null || cardKey == null) {
            return;
        }
        if (inferenceWorker != null && !inferenceWorker.isDone()) {
            // A worker is in flight; let it finish, then re-fire for whatever
            // the latest request was.
            pendingFen = fen;
            pendingArch = cardKey;
            return;
        }
        String key = cacheKey(cardKey, fen);
        ActivationSnapshot cached = snapshotCache.get(key);
        if (cached != null) {
            applySnapshot(cardKey, fen, cached);
            refreshStatusBadge();
            return;
        }
        showLoading(cardKey, fen, initialLoadingDetail(cardKey));
        runningArch = cardKey;
        runningFen = fen;
        inferenceWorker = new SwingWorker<>() {
            /**
             * {@inheritDoc}
             */
            @Override
            protected ActivationSnapshot doInBackground() {
                return inferSnapshot(cardKey, fen);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            protected void done() {
                inferenceWorker = null;
                runningArch = null;
                runningFen = null;
                boolean failed = false;
                try {
                    ActivationSnapshot snapshot = get();
                    snapshotCache.put(key, snapshot);
                    publishCacheDiagnostics(cardKey);
                    if (active && cardKey.equals(activeCardKey()) && fen.equals(effectiveFen())) {
                        applySnapshot(cardKey, fen, snapshot);
                    }
                } catch (CancellationException ex) {
                    failed = true;
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    statusBadge.error("inference failed: "
                            + cause.getClass().getSimpleName() + ": " + cause.getMessage());
                    failed = true;
                } finally {
                    clearLoading(cardKey, fen);
                }
                // Keep the error message visible; otherwise show the
                // provider's healthy load state.
                if (!failed && active) {
                    refreshStatusBadge();
                } else if (active) {
                    showSelected();
                }
                if (active && pendingFen != null) {
                    SwingUtilities.invokeLater(NetworkPanel.this::startInference);
                } else if (active && (displayedKey == null
                        || !displayedKey.equals(cacheKey(activeCardKey(), effectiveFen())))) {
                    requestActiveInference();
                }
            }
        };
        inferenceWorker.execute();
    }

    /**
     * Applies snapshot.
     *
     * @param cardKey source card key
     * @param fen FEN string
     * @param snapshot captured snapshot
     */
    private void applySnapshot(String cardKey, String fen,
            ActivationSnapshot snapshot) {
        switch (cardKey) {
            case ARCH_CNN -> {
                cnnView.setSnapshot(snapshot);
                cnnView.setFen(fen);
            }
            case ARCH_BT4 -> {
                bt4View.setSnapshot(snapshot);
                bt4View.setFen(fen);
            }
            case ARCH_OTIS -> {
                otisView.setSnapshot(snapshot);
                otisView.setFen(fen);
            }
            default -> {
                nnueView.setSnapshot(snapshot);
                nnueView.setFen(fen);
                nnueView.setVersionLabel(provider.nnueVersionLabel());
            }
        }
        clearLoading(cardKey, fen);
        if (cardKey.equals(activeCardKey())) {
            displayedKey = cacheKey(cardKey, fen);
            cards.show(cardPanel, cardKey);
            publishCacheDiagnostics(cardKey);
            refreshWorkspaceHeader();
        }
    }

    /**
     * Returns a compact summary of the most recently active Network tab cache.
     *
     * @return dashboard-friendly cache summary
     */
    public static String runtimeCacheSummary() {
        String arch = lastSnapshotCacheArchitecture == null || lastSnapshotCacheArchitecture.isBlank()
                ? "No active architecture"
                : lastSnapshotCacheArchitecture;
        return lastSnapshotCacheSize + " / " + SNAPSHOT_CACHE_LIMIT
                + " snapshots - " + arch;
    }

    /**
     * Publishes activation-cache diagnostics to the status badge.
     *
     * @param cardKey source card key
     */
    private void publishCacheDiagnostics(String cardKey) {
        lastSnapshotCacheSize = snapshotCache.size();
        lastSnapshotCacheArchitecture = displayNameFor(cardKey);
    }

    /**
     * Shows loading.
     *
     * @param cardKey source card key
     * @param fen FEN string
     * @param detail detail text
     */
    private void showLoading(String cardKey, String fen, String detail) {
        if (!cardKey.equals(activeCardKey())) {
            return;
        }
        loadingArch = cardKey;
        loadingFen = fen;
        loadingPanel.start("Loading " + displayNameFor(cardKey),
                detail,
                loadingModelDetail(cardKey),
                loadingPositionDetail(fen));
        statusBadge.busy("running " + displayNameFor(cardKey).toLowerCase(java.util.Locale.ROOT) + " inference");
        refreshWorkspaceHeader();
        // Only swap to the full-screen loading card when there is no prior
        // content to keep visible. Otherwise leave the previous snapshot on
        // screen so changing the position does not blank out the entire
        // network view — much smoother visual continuity while the new
        // inference runs in the background. The status badge already
        // communicates that work is in flight.
        if (!hasRenderedContentFor(cardKey)) {
            cards.show(cardPanel, CARD_LOADING);
        }
    }

    /**
     * Returns whether rendered content for.
     *
     * @param cardKey source card key
     * @return true when rendered content for
     */
    private boolean hasRenderedContentFor(String cardKey) {
        if (displayedKey == null) {
            return false;
        }
        return displayedKey.startsWith(cardKey + "::");
    }

    /**
     * Formats the initial loading detail.
     *
     * @param cardKey source card key
     * @return initial loading detail text
     */
    private String initialLoadingDetail(String cardKey) {
        String label = displayNameFor(cardKey);
        for (RealActivations.ModelStatus status : provider.modelStatuses()) {
            if (label.equals(status.label())) {
                if (status.loaded()) {
                    return "Running inference";
                }
                if (status.present() && !"fallback".equals(status.state())) {
                    return "Loading model weights";
                }
                return "Preparing fallback activations";
            }
        }
        return "Preparing network activations";
    }

    /**
     * Clears loading.
     *
     * @param cardKey source card key
     * @param fen FEN string
     */
    private void clearLoading(String cardKey, String fen) {
        boolean unconditional = cardKey == null || fen == null;
        boolean matches = !unconditional
                && cardKey.equals(loadingArch)
                && fen.equals(loadingFen);
        if (!unconditional && !matches) {
            return;
        }
        loadingArch = null;
        loadingFen = null;
        loadingPanel.stop();
    }

    /**
     * Formats the loading model detail.
     *
     * @param cardKey source card key
     * @return loading model detail text
     */
    private String loadingModelDetail(String cardKey) {
        String label = displayNameFor(cardKey);
        for (RealActivations.ModelStatus status : provider.modelStatuses()) {
            if (label.equals(status.label())) {
                String file = status.path() == null
                        ? "configured model"
                        : status.path().getFileName().toString();
                if (status.present()) {
                    return file + " - " + status.detail();
                }
                return file + " - missing, synthetic fallback ready";
            }
        }
        return label + " model status pending";
    }

    /**
     * Formats the loading position detail.
     *
     * @param fen FEN string
     * @return loading position detail text
     */
    private static String loadingPositionDetail(String fen) {
        if (fen == null || fen.isBlank()) {
            return "No position loaded";
        }
        String trimmed = fen.trim();
        String placement = trimmed.split("\\s+", 2)[0];
        String side = TensorViz.whiteDownForSideToMove(trimmed)
                ? "white to move"
                : "black to move";
        return placement + " - " + side;
    }

    /**
     * Returns the display name for.
     *
     * @param cardKey source card key
     * @return display name for text
     */
    private static String displayNameFor(String cardKey) {
        return switch (cardKey) {
            case ARCH_CNN -> ARCH_CNN_LABEL;
            case ARCH_BT4 -> ARCH_BT4_LABEL;
            case ARCH_OTIS -> ARCH_OTIS_LABEL;
            case ARCH_CLASSICAL -> ARCH_CLASSICAL_LABEL;
            default -> ARCH_NNUE_LABEL;
        };
    }
}
