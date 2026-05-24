/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package application.gui.workbench.network;

import application.gui.workbench.Defaults;
import application.gui.workbench.game.Positions;
import application.gui.workbench.mcts.MctsSearch;
import application.gui.workbench.mcts.MctsWeightsPanel;
import application.gui.workbench.ui.InspectorPanel;
import application.gui.workbench.ui.SegmentedSwitcher;
import application.gui.workbench.ui.StatusBadge;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Toast;
import application.gui.workbench.ui.ToggleBox;
import application.gui.workbench.ui.Ui;
import chess.core.Move;
import chess.core.Position;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
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
 * <p>Owns the architecture switcher (NNUE / lc0-CNN / lc0-BT4) and the
 * abstract-vs-detailed view-mode toggle. Runs real network inference on a
 * SwingWorker with a short debounce so rapid FEN changes coalesce into one
 * forward pass per architecture. Falls back to synthetic activations when a
 * model file is missing or fails to load.</p>
 */
public final class NetworkPanel extends JPanel {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Architecture identifier for NNUE.
     */
    private static final String ARCH_NNUE = "NNUE";

    /**
     * User-facing NNUE selector label.
     */
    private static final String ARCH_NNUE_LABEL = RealActivations.LABEL_NNUE;

    /**
     * Architecture identifier for LC0 CNN.
     */
    private static final String ARCH_CNN = "LC0 CNN";

    /**
     * User-facing CNN selector label with the shipped small-net dimensions.
     */
    private static final String ARCH_CNN_LABEL = RealActivations.LABEL_CNN;

    /**
     * Architecture identifier for LC0 BT4.
     */
    private static final String ARCH_BT4 = "LC0 BT4";

    /**
     * User-facing BT4 selector label with the shipped transformer dimensions.
     */
    private static final String ARCH_BT4_LABEL = RealActivations.LABEL_BT4;

    /**
     * Card key for the animated loading surface.
     */
    private static final String CARD_LOADING = "loading";

    /**
     * Debounce delay before kicking off inference on a new FEN.
     */
    private static final int DEBOUNCE_MS = 220;

    /**
     * NNUE view.
     */
    private final NnueView nnueView = new NnueView();

    /**
     * CNN view.
     */
    private final CnnView cnnView = new CnnView();

    /**
     * BT4 view.
     */
    private final Bt4View bt4View = new Bt4View();

    /**
     * Upper bound on cached snapshots before the least-recently-used entry is
     * evicted. One forward pass per (architecture, FEN) pair, so 64 covers a
     * deep game tree across all three architectures comfortably.
     */
    private static final int SNAPSHOT_CACHE_LIMIT = 64;

    /**
     * Per-(architecture, FEN) snapshot cache, accessed only on the EDT.
     *
     * <p>Inference is expensive — BT4 in particular — so each finished
     * snapshot is kept here keyed by {@code cardKey + "::" + fen}. Switching
     * architecture or stepping back to a visited position is then an instant
     * cache hit instead of a fresh forward pass. Snapshots are sealed before
     * they land here, so caching and sharing them is safe. The map is an
     * access-ordered LRU bounded by {@link #SNAPSHOT_CACHE_LIMIT}.</p>
     */
    private final Map<String, ActivationSnapshot> snapshotCache =
            new LinkedHashMap<>(16, 0.75f, true) {
                private static final long serialVersionUID = 1L;

                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<String, ActivationSnapshot> eldest) {
    return size() > SNAPSHOT_CACHE_LIMIT;
                }
            };

    /**
     * Architecture switcher.
     */
    private final JComboBox<String> archCombo = new JComboBox<>(buildArchOptions());

    /**
     * Position picker. "Use main board" forwards live FENs; any other selection
     * pins the view to a canned position so the user can explore.
     */
    private final JComboBox<String> positionCombo = new JComboBox<>(Positions.labels());

    /**
     * Toolbar segment index of the dense all-neurons view mode.
     */
    private static final int MODE_RAW = 2;

    /**
     * Toolbar segment index of the simplified Atlas view mode.
     */
    private static final int MODE_ATLAS = 3;

    /**
     * Mutually-exclusive view-mode selector. The toolbar intentionally exposes
     * only the modes a normal workflow needs: overview, drill-down trace, a
     * dense all-neurons readout, and learned-weight atlas.
     */
    private final SegmentedSwitcher viewMode = new SegmentedSwitcher(
            new String[] { "Overview", "Trace", "All", "Atlas" });

    /**
     * PNG export button — captures the current network-view card to disk.
     */
    private final JButton exportPngButton = Ui.button("Export PNG", false,
            event -> exportPng());

    /**
     * Status badge in the toolbar — a coloured state dot plus a short message.
     */
    private final StatusBadge statusBadge = new StatusBadge();

    /**
     * Playout interval between Network-tab MCTS UI snapshots.
     */
    private static final int NETWORK_MCTS_PUBLISH_INTERVAL = 24;

    /**
     * Minimum time between Network-tab MCTS UI snapshots.
     */
    private static final long NETWORK_MCTS_MIN_UPDATE_NANOS = 70_000_000L;

    /**
     * Playout interval where the background worker yields briefly so pointer
     * and keyboard events stay responsive on smaller machines.
     */
    private static final int NETWORK_MCTS_YIELD_INTERVAL = 64;

    /**
     * Starts a PUCT search rooted at the current network position.
     */
    private final JButton mctsStartButton = Ui.button("Start MCTS", true,
            event -> startNetworkMcts());

    /**
     * Pauses/resumes the Network-tab PUCT search.
     */
    private final JButton mctsPauseButton = Ui.button("Pause", false,
            event -> toggleNetworkMctsPaused());

    /**
     * Stops the Network-tab PUCT search.
     */
    private final JButton mctsStopButton = Ui.button("Stop", false,
            event -> stopNetworkMcts());

    /**
     * Visit budget for Network-tab MCTS.
     */
    private final JSpinner mctsVisitsSpinner = new JSpinner(
            new SpinnerNumberModel(
                    Defaults.MCTS_VISITS,
                    Defaults.MCTS_VISITS_MIN,
                    Defaults.MCTS_VISITS_MAX,
                    Defaults.MCTS_VISITS_STEP));

    /**
     * Optional Network-tab MCTS wall-clock budget in milliseconds. Zero means
     * visit-only.
     */
    private final JSpinner mctsMillisSpinner = new JSpinner(
            new SpinnerNumberModel(
                    Defaults.MCTS_MILLIS,
                    Defaults.MCTS_MILLIS_MIN,
                    Defaults.MCTS_MILLIS_MAX,
                    Defaults.MCTS_MILLIS_STEP));

    /**
     * PUCT exploration constant for Network-tab MCTS.
     */
    private final JSpinner mctsCpuctSpinner = new JSpinner(
            new SpinnerNumberModel(
                    Defaults.MCTS_CPUCT,
                    Defaults.MCTS_CPUCT_MIN,
                    Defaults.MCTS_CPUCT_MAX,
                    Defaults.MCTS_CPUCT_STEP));

    /**
     * When selected, the network views re-infer the MCTS leaf currently being
     * evaluated.
     */
    private final ToggleBox mctsFollowLeafToggle =
            new ToggleBox("Follow leaf", Defaults.NETWORK_MCTS_FOLLOW_LEAF);

    /**
     * Search status shown in the Network tab.
     */
    private final StatusBadge mctsStatusBadge = new StatusBadge();

    /**
     * Live MCTS edge-weight renderer shown while Network-tab search is active.
     */
    private final MctsWeightsPanel mctsWeightsPanel = new MctsWeightsPanel();

    /**
     * Card layout container that swaps views.
     */
    private final CardLayout cards = new CardLayout();

    /**
     * Card-hosting panel.
     */
    private final JPanel cardPanel = new JPanel(cards);

    /**
     * Animated loading surface shown before the active view has data.
     */
    private final LoadingPanel loadingPanel = new LoadingPanel();

    /**
     * Embedded right-side details panel shared by all three views.
     */
    private final InspectorPanel inspectorPanel = new InspectorPanel();

    /**
     * Runtime diagnostics panel: model files/load state, GPU support, and
     * syntax-colored config preview.
     */
    private final NetworkDiagnosticsPanel diagnosticsPanel =
    new NetworkDiagnosticsPanel();

    /**
     * Right-side details tabs.
     */
    private final JTabbedPane detailsTabs = Ui.tabbedPane();

    /**
     * Primary network toolbar strip.
     */
    private JPanel networkToolbar;

    /**
     * Collapsible MCTS toolbar strip.
     */
    private JPanel mctsToolbar;

    /**
     * Real activation provider. Loads networks lazily.
     */
    private final RealActivations provider = new RealActivations();

    /**
     * Most recent main-board FEN. Saved separately so the picker can switch
     * back to live updates without losing the live value.
     */
    private String mainBoardFen = "";

    /**
     * Canned-position FEN; null when the picker is set to "use main board".
     */
    private String overrideFen;

    /**
     * Debounce timer; fires inference after the user stops navigating.
     */
    private final Timer debounceTimer;

    /**
     * Worker for the currently in-flight inference, or null when idle. Carries
     * the sealed snapshot it produced back to {@code done()}.
     */
    private SwingWorker<ActivationSnapshot, Void> inferenceWorker;

    /**
     * Architecture currently being inferred by {@link #inferenceWorker}.
     */
    private String runningArch;

    /**
     * FEN currently being inferred by {@link #inferenceWorker}.
     */
    private String runningFen;

    /**
     * Architecture represented by the active loading card.
     */
    private String loadingArch;

    /**
     * FEN represented by the active loading card.
     */
    private String loadingFen;

    /**
     * Worker for the Network-tab PUCT search.
     */
    private SwingWorker<Void, MctsSearch.Snapshot> mctsWorker;

    /**
     * Active Network-tab PUCT search.
     */
    private MctsSearch mctsSearch;

    /**
     * Pause flag read by the MCTS worker.
     */
    private volatile boolean mctsPaused;

    /**
     * FEN of the MCTS leaf the network views should currently follow. Null
     * means the views follow the selected board/canned position as usual.
     */
    private String mctsLeafFen;

    /**
     * Card key ({@link #ARCH_NNUE} / {@link #ARCH_CNN} / {@link #ARCH_BT4})
     * the next debounce tick should infer, or null when nothing is pending.
     */
    private String pendingArch;

    /**
     * FEN the next debounce tick should infer, paired with {@link #pendingArch}.
     */
    private String pendingFen;

    /**
     * Whether the Network pane is currently visible in any workbench editor
     * group. Heavy inference stays lazy until this is true.
     */
    private boolean active;

    /**
     * The {@code cardKey + "::" + fen} key currently displayed by the active
     * view, used to skip redundant inference requests for the same state.
     */
    private String displayedKey;

    /**
     * Creates the network panel.
     */
    public NetworkPanel() {
        super(new BorderLayout());
        setOpaque(true);
        setBackground(Theme.BG);
        setBorder(Theme.pad(Theme.SPACE_SM));
        add(buildToolbar(), BorderLayout.NORTH);
        cardPanel.setOpaque(true);
        cardPanel.setBackground(Theme.BG);
        // Each architecture card lives inside its own JScrollPane so the
        // atlas mode can paint a tall mosaic and the user scrolls through
        // it, while abstract/detailed/raw stay zero-overhead.
        cardPanel.add(wrapInScroll(nnueView), ARCH_NNUE);
        cardPanel.add(wrapInScroll(cnnView), ARCH_CNN);
        cardPanel.add(wrapInScroll(bt4View), ARCH_BT4);
        cardPanel.add(loadingPanel, CARD_LOADING);
        nnueView.setInspector(inspectorPanel);
        cnnView.setInspector(inspectorPanel);
        bt4View.setInspector(inspectorPanel);
        detailsTabs.addTab("Inspector", inspectorPanel);
        detailsTabs.addTab("Runtime", diagnosticsPanel);
        detailsTabs.setPreferredSize(new Dimension(304, 600));
        detailsTabs.setMinimumSize(new Dimension(232, 260));
        JPanel content = new JPanel(new BorderLayout(Theme.SPACE_SM, 0));
        content.setOpaque(false);
        content.add(cardPanel, BorderLayout.CENTER);
        content.add(Ui.collapsible("Inspector", detailsTabs, true), BorderLayout.EAST);
        JPanel center = new JPanel(new BorderLayout(0, Theme.SPACE_SM));
        center.setOpaque(false);
        center.add(content, BorderLayout.CENTER);
        JPanel lowerDiagnostics = Ui.transparentPanel(new BorderLayout(0, Theme.SPACE_SM));
        lowerDiagnostics.add(Ui.collapsible("MCTS", buildMctsToolbar(), false), BorderLayout.NORTH);
        lowerDiagnostics.add(Ui.collapsible("Edge weights", mctsWeightsPanel, false),
                BorderLayout.CENTER);
        center.add(lowerDiagnostics, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);
        archCombo.addActionListener(event -> onArchitectureChanged());
        positionCombo.addActionListener(event -> onPositionPicked());
        viewMode.addActionListener(event -> propagateViewMode());
        mctsFollowLeafToggle.setSelected(Defaults.NETWORK_MCTS_FOLLOW_LEAF);
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
        diagnosticsPanel.refresh(provider, displayNameFor(activeCardKey()));
    }

    /**
     * Wraps a network view in a JScrollPane styled to match the workbench
     * theme. The atlas paint paths use the scroll view to expose every
     * neuron at a comfortable tile size.
     *
     * @param view view component
     * @return scroll-pane wrapping the view
     */
    private static JScrollPane wrapInScroll(JComponent view) {
        JScrollPane scroll = new JScrollPane(view);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBackground(Theme.BG);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.getHorizontalScrollBar().setUnitIncrement(24);
        return scroll;
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
     * Sets whether the Network pane is visible. Becoming visible triggers the
     * latest pending inference; becoming hidden cancels only the debounce so a
     * worker already in flight can finish without blocking the EDT.
     *
     * @param value true when visible
     */
    public void setActive(boolean value) {
        if (active == value) {
            return;
        }
        active = value;
        if (active) {
            requestActiveInference();
        } else {
            debounceTimer.stop();
            pendingArch = null;
            pendingFen = null;
            clearLoading(null, null);
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
     * Returns the FEN the views should currently reflect — the canned override
     * when one is pinned, otherwise the live main-board FEN.
     *
     * @return effective FEN
     */
    private String effectiveFen() {
        if (mctsLeafFen != null && mctsFollowLeafToggle.isSelected()) {
            return mctsLeafFen;
        }
    return baseFen();
    }

    /**
     * Returns the selected non-MCTS position: canned override when pinned,
     * otherwise the live main board.
     *
     * @return board/canned FEN
     */
    private String baseFen() {
        return overrideFen != null ? overrideFen : mainBoardFen;
    }

    /**
     * Maps an architecture-combo entry to one of the three card keys.
     *
     * @param comboKey selected combo entry, or null
     * @return {@link #ARCH_NNUE} / {@link #ARCH_CNN} / {@link #ARCH_BT4}
     */
    private String cardKeyFor(String comboKey) {
        if (ARCH_CNN.equals(comboKey) || ARCH_CNN_LABEL.equals(comboKey)) {
            return ARCH_CNN;
        }
        if (ARCH_BT4.equals(comboKey) || ARCH_BT4_LABEL.equals(comboKey)) {
            return ARCH_BT4;
        }
        return ARCH_NNUE;
    }

    /**
     * Returns the card key of the architecture currently on screen.
     *
     * @return active card key
     */
    private String activeCardKey() {
    return cardKeyFor((String) archCombo.getSelectedItem());
    }

    /**
     * Builds the snapshot-cache key for an (architecture, FEN) pair.
     *
     * @param cardKey card key
     * @param fen position FEN
     * @return cache key
     */
    private static String cacheKey(String cardKey, String fen) {
        return cardKey + "::" + fen;
    }

    /**
     * Ensures the active architecture's view reflects the effective FEN.
     *
     * <p>On a cache hit the snapshot is applied immediately with no worker; on
     * a miss a debounced single-architecture inference is scheduled. Only the
     * architecture that is actually on screen is ever inferred — the others
     * are filled lazily the first time the user switches to them.</p>
     */
    private void requestActiveInference() {
        String cardKey = activeCardKey();
        String fen = effectiveFen();
        if (!active || fen == null || fen.isBlank()) {
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
     * Reacts to a position picker change.
     */
    private void onPositionPicked() {
        String label = (String) positionCombo.getSelectedItem();
        String fen = Positions.fenFor(label);
        overrideFen = fen;
        requestActiveInference();
    }

    /**
     * Builds the network toolbar.
     *
     * <p>The toolbar keeps only the daily-use controls: network, position, mode,
     * export, and load status. Expert atlas controls were removed from the
     * primary surface because the defaults are the clearest path for reading the
     * view.</p>
     *
     * @return toolbar
     */
    private JComponent buildToolbar() {
        JPanel bar = Ui.transparentPanel(new BorderLayout(Theme.SPACE_MD, 0));
        networkToolbar = bar;
        styleToolbarShell(bar, Theme.pad(Theme.SPACE_SM));

        styleToolbarCombo(archCombo, 166,
                "Pick the network family to visualise.");
        styleToolbarCombo(positionCombo, 188,
                "Pin a canned position to explore, or follow the main board.");
        exportPngButton.setToolTipText("Capture the current network view to a PNG file.");

        JPanel controls = Ui.transparentPanel(
    new FlowLayout(FlowLayout.LEFT, Theme.SPACE_SM, 0));
        controls.add(archCombo);
        controls.add(positionCombo);
        controls.add(viewMode);

        JPanel actions = Ui.transparentPanel(
    new FlowLayout(FlowLayout.RIGHT, Theme.SPACE_SM, 0));
        actions.add(exportPngButton);
        actions.add(statusBadge);

        bar.add(controls, BorderLayout.WEST);
        bar.add(actions, BorderLayout.EAST);
        JPanel outer = Ui.transparentPanel(new BorderLayout(0, Theme.SPACE_XS));
        outer.add(bar, BorderLayout.NORTH);
        return outer;
    }

    /**
     * Builds the network-local MCTS control strip. While running, the active
     * architecture view follows the PUCT leaf currently being evaluated so the
     * user can watch NNUE/CNN/BT4 activations change through search.
     *
     * @return MCTS toolbar
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
        mctsStartButton.setToolTipText("Run PUCT from the current board/canned position and stream each leaf into the network view.");
        mctsPauseButton.setToolTipText("Pause or resume the PUCT worker while keeping the current leaf on screen.");
        mctsStopButton.setToolTipText("Stop PUCT and return the network view to the board/canned position.");
        mctsFollowLeafToggle.setToolTipText("When on, the network view shows the leaf currently being evaluated.");
        mctsStatusBadge.idle("MCTS idle");

        JPanel controls = Ui.transparentPanel(
    new FlowLayout(FlowLayout.LEFT, Theme.SPACE_SM, 0));
        controls.add(Ui.label("MCTS"));
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

        bar.add(controls, BorderLayout.WEST);
        bar.add(status, BorderLayout.EAST);
        return bar;
    }

    /**
     * Reapplies toolbar chrome from the current palette.
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
     * Applies flat toolbar chrome with a single bottom separator.
     *
     * @param bar toolbar panel
     * @param padding inner padding
     */
    private static void styleToolbarShell(JPanel bar, Border padding) {
        bar.setOpaque(true);
        bar.setBackground(Theme.PANEL_SOLID);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.LINE),
                padding));
    }

    /**
     * Styles a toolbar combo box: the shared combo styling, a fixed width, the
     * shared compact-control height, and a tooltip.
     *
     * @param combo combo box
     * @param width preferred width in pixels
     * @param tooltip hover tooltip
     */
    private static void styleToolbarCombo(JComboBox<?> combo, int width, String tooltip) {
        Ui.styleCombo(combo);
        combo.setPreferredSize(new Dimension(width, Theme.CONTROL_HEIGHT));
        combo.setToolTipText(tooltip);
    }

    /**
     * Propagates the atlas-control state to the NNUE view. The other views
     * ignore atlas controls.
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
     * Returns the network view component for the architecture currently on
     * screen.
     *
     * @return active view component
     */
    private JComponent activeView() {
    return switch (activeCardKey()) {
            case ARCH_CNN -> cnnView;
            case ARCH_BT4 -> bt4View;
            default -> nnueView;
        };
    }

    /**
     * Captures the currently-visible network view as a PNG and writes it next
     * to the user's last export. The file name embeds the architecture and a
     * timestamp so consecutive exports don't collide.
     *
     * <p>The capture is taken at the view's full preferred size, not the
     * clipped viewport — so a tall atlas mosaic is exported in full rather
     * than just the part that happened to be scrolled into view.</p>
     */
    private void exportPng() {
        JComponent target = activeView();
        java.awt.Dimension original = target.getSize();
        int w = Math.max(1, Math.max(target.getWidth(), target.getPreferredSize().width));
        int h = Math.max(1, Math.max(target.getHeight(), target.getPreferredSize().height));
        try {
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = img.createGraphics();
            try {
                // Lay the view out at the full capture size, paint, then let
                // the scroll pane restore it on the next validation pass.
                target.setSize(w, h);
                target.doLayout();
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
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File out = new File(dir, "workbench-" + arch + "-" + stamp + ".png");
            ImageIO.write(img, "png", out);
            statusBadge.success("exported " + out.getName());
            toast(Toast.Kind.SUCCESS, "Exported " + out.getName());
        } catch (IOException ex) {
            statusBadge.error("export failed: " + ex.getMessage());
            toast(Toast.Kind.ERROR, "Export failed: " + ex.getMessage());
        }
    }

    /**
     * Shows a workbench toast from this embedded panel.
     *
     * @param kind toast kind
     * @param message message text
     */
    private void toast(Toast.Kind kind, String message) {
        java.awt.Window window = SwingUtilities.getWindowAncestor(this);
        if (window instanceof JFrame frame) {
            Toast.show(frame, kind, message);
        }
    }

    /**
     * Handles an architecture-switcher change.
     */
    private void onArchitectureChanged() {
        updateAtlasAvailability();
        // Re-apply simplified atlas defaults after an architecture change.
        propagateAtlasControls();
        showSelected();
        // Make sure the now-visible card reflects the current position —
        // an instant cache hit when it has been seen before, otherwise a
        // debounced single-architecture inference.
        requestActiveInference();
    }

    /**
     * Returns true when the current architecture supports atlas mode.
     *
     * @return atlas supported flag
     */
    private boolean isAtlasSupported() {
        return true;
    }

    /**
     * Keeps the Atlas segment enabled. All architectures now have a dedicated
     * atlas renderer.
     */
    private void updateAtlasAvailability() {
        boolean atlasSupported = isAtlasSupported();
        if (!atlasSupported && viewMode.getSelectedIndex() == MODE_ATLAS) {
            viewMode.setSelectedIndex(0);
        }
        viewMode.setSegmentEnabled(MODE_ATLAS, atlasSupported);
    }

    /**
     * Shows the architecture selected by the combo box.
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
    }

    /**
     * Returns whether the loading card currently represents the supplied
     * architecture.
     *
     * @param cardKey architecture card key
     * @return true when the loading card is active for that architecture
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
     * Refreshes the per-architecture status badge with the provider's current
     * load state for the active architecture.
     */
    private void refreshStatusBadge() {
        String cardKey = activeCardKey();
        if (cardKey == null) {
            statusBadge.idle("");
            return;
        }
        String statusKey;
        if (ARCH_CNN.equals(cardKey)) {
            statusKey = "cnn";
        } else if (ARCH_BT4.equals(cardKey)) {
            statusKey = "bt4";
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
        diagnosticsPanel.refresh(provider, displayNameFor(cardKey));
    }

    /**
     * Builds the architecture-combo options array. The selector intentionally
     * exposes one entry per visualized network family rather than one entry
     * per model file in {@code models/}.
     *
     * @return combo option labels
     */
    private static String[] buildArchOptions() {
        return new String[] { ARCH_NNUE_LABEL, ARCH_CNN_LABEL, ARCH_BT4_LABEL };
    }

    /**
     * Propagates the current view-mode selection to every view. The mode is a
     * single {@link ViewMode} value, so the views can no longer end
     * up in an inconsistent combination of flags.
     */
    private void propagateViewMode() {
        int index = viewMode.getSelectedIndex();
        ViewMode mode = selectedViewMode();
        nnueView.setViewMode(mode);
        cnnView.setViewMode(mode);
        bt4View.setViewMode(mode);
        // The Atlas sub-controls only do anything in Atlas mode — grey them
        // out elsewhere so the toolbar shows what is actually live.
        updateAtlasAvailability();
        cardPanel.revalidate();
        cardPanel.repaint();
    }

    /**
     * Returns the rendering mode selected by the simplified toolbar.
     *
     * @return selected mode
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
     * Applies the fixed-scale flag to every view. The simplified UI keeps this
     * off so heatmaps adapt naturally to the current position.
     *
     * @param fixed true to pin scales
     */
    private void applyFixedScale(boolean fixed) {
        nnueView.setFixedScale(fixed);
        cnnView.setFixedScale(fixed);
        bt4View.setFixedScale(fixed);
        cardPanel.repaint();
    }

    /**
     * Starts a Network-tab PUCT search and streams the current leaf into the
     * active network view.
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
            return;
        }
        int budget = ((Number) mctsVisitsSpinner.getValue()).intValue();
        long maxMillis = ((Number) mctsMillisSpinner.getValue()).longValue();
        double cpuct = ((Number) mctsCpuctSpinner.getValue()).doubleValue();
        mctsPaused = false;
        mctsSearch = new MctsSearch(root, cpuct);
    final MctsSearch search = mctsSearch;
        mctsLeafFen = root.toString();
        updateNetworkMctsButtons(true);
        SwingWorker<Void, MctsSearch.Snapshot> activeWorker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                publish(search.snapshot(false));
                long lastPublishNanos = System.nanoTime();
                while (!isCancelled() && search.shouldContinue(budget, maxMillis)) {
                    if (mctsPaused) {
                        publish(search.snapshot(true));
                        lastPublishNanos = System.nanoTime();
                        Thread.sleep(80L);
                        continue;
                    }
                    search.iterate();
                    long playouts = search.playouts();
                    long now = System.nanoTime();
                    if (playouts % NETWORK_MCTS_PUBLISH_INTERVAL == 0
                            || now - lastPublishNanos >= NETWORK_MCTS_MIN_UPDATE_NANOS) {
                        publish(search.snapshot(false));
                        lastPublishNanos = now;
                    }
                    if (playouts % NETWORK_MCTS_YIELD_INTERVAL == 0) {
                        Thread.sleep(1L);
                    }
                }
                publish(search.snapshot(mctsPaused));
                return null;
            }

            @Override
            protected void process(List<MctsSearch.Snapshot> chunks) {
                if (mctsWorker != this || mctsSearch != search || chunks.isEmpty()) {
                    return;
                }
                showNetworkMctsSnapshot(chunks.get(chunks.size() - 1), true);
            }

            @Override
            protected void done() {
                if (mctsWorker != this || mctsSearch != search) {
                    return;
                }
                updateNetworkMctsButtons(false);
                try {
                    get();
                } catch (CancellationException ex) {
                    // stop button already restored the toolbar state
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    mctsStatusBadge.error("MCTS failed: "
                            + cause.getClass().getSimpleName() + ": " + cause.getMessage());
                }
                if (!isCancelled()) {
                    showNetworkMctsSnapshot(search.snapshot(mctsPaused), false);
                }
                search.close();
            }
        };
        mctsWorker = activeWorker;
        activeWorker.execute();
    }

    /**
     * Toggles Network-tab MCTS pause state.
     */
    private void toggleNetworkMctsPaused() {
        if (!isNetworkMctsRunning()) {
            return;
        }
        mctsPaused = !mctsPaused;
        updateNetworkMctsButtons(true);
        if (mctsSearch != null) {
            showNetworkMctsSnapshot(mctsSearch.snapshot(mctsPaused), true);
        }
    }

    /**
     * Stops Network-tab MCTS and restores normal network-position following.
     */
    private void stopNetworkMcts() {
        stopNetworkMcts(true);
    }

    /**
     * Stops Network-tab MCTS.
     *
     * @param restoreBase true to return to the board/canned position
     */
    private void stopNetworkMcts(boolean restoreBase) {
        MctsSearch activeSearch = mctsSearch;
        if (mctsWorker != null && !mctsWorker.isDone()) {
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
            mctsLeafFen = null;
            mctsWeightsPanel.clear();
            mctsStatusBadge.idle("MCTS stopped");
            revalidate();
            requestActiveInference();
        }
    }

    /**
     * Returns whether Network-tab MCTS is actively running.
     *
     * @return true when running
     */
    private boolean isNetworkMctsRunning() {
        return mctsWorker != null && !mctsWorker.isDone();
    }

    /**
     * Applies a Network-tab MCTS snapshot to the toolbar and network FEN.
     *
     * @param snapshot search snapshot
     * @param running true while the worker is expected to keep running
     */
    private void showNetworkMctsSnapshot(MctsSearch.Snapshot snapshot, boolean running) {
        showNetworkMctsSnapshot(snapshot, running, null, null, null);
    }

    /**
     * Applies a Network-tab MCTS snapshot plus an optional precomputed
     * activation snapshot. Used by the step-by-step streamer so the MCTS edge
     * weights and the active network view change as one frame.
     *
     * @param snapshot search snapshot
     * @param running true while the worker is expected to keep running
     * @param activationCardKey card key for activationSnapshot
     * @param activationFen FEN for activationSnapshot
     * @param activationSnapshot precomputed activation snapshot, or null
     */
    private void showNetworkMctsSnapshot(MctsSearch.Snapshot snapshot,
            boolean running, String activationCardKey, String activationFen,
            ActivationSnapshot activationSnapshot) {
        if (snapshot == null) {
            return;
        }
        mctsWeightsPanel.setSnapshot(snapshot);
        revalidate();
        if (mctsFollowLeafToggle.isSelected() && snapshot.exploringPosition() != null) {
            String fen = snapshot.exploringPosition().toString();
            boolean changed = !fen.equals(mctsLeafFen);
            mctsLeafFen = fen;
            boolean applied = false;
            if (activationSnapshot != null
                    && activationCardKey != null
                    && activationCardKey.equals(activeCardKey())
                    && fen.equals(activationFen)) {
                applySnapshot(activationCardKey, fen, activationSnapshot);
                applied = true;
            }
            if (changed && !applied) {
                requestActiveInference();
            }
        }
        String leaf = snapshot.exploringLineText();
        if (leaf == null || leaf.isBlank()) {
            leaf = "root";
        }
        String best = snapshot.bestMove() == Move.NO_MOVE ? "-" : Move.toString(snapshot.bestMove());
        String message = String.format("MCTS %,d visits · root %s · best %s · leaf %s",
                snapshot.playouts(),
                snapshot.rootScoreLabel(),
                best,
                Ui.elide(leaf, getFontMetrics(Theme.font(11, java.awt.Font.PLAIN)), 190));
        if (snapshot.paused()) {
            mctsStatusBadge.idle("paused · " + message);
        } else if (running || isNetworkMctsRunning()) {
            mctsStatusBadge.busy(message);
        } else {
            mctsStatusBadge.success("done · " + message);
        }
    }

    /**
     * Reacts to the "Follow leaf" switch.
     */
    private void onMctsFollowLeafChanged() {
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
     * Updates Network-tab MCTS button state.
     *
     * @param running true when a worker is active
     */
    private void updateNetworkMctsButtons(boolean running) {
        mctsStartButton.setEnabled(!running);
        mctsPauseButton.setEnabled(running);
        mctsStopButton.setEnabled(running);
        mctsPauseButton.setText(mctsPaused ? "Resume" : "Pause");
    }

    /**
     * Cancels any debounced/asynchronous inference before the step-by-step
     * MCTS streamer takes over the active network view.
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
     * Runs one forward pass for a card key.
     *
     * @param cardKey architecture card
     * @param fen FEN to infer
     * @return sealed activation snapshot
     */
    private ActivationSnapshot inferSnapshot(String cardKey, String fen) {
    return switch (cardKey) {
            case ARCH_CNN -> provider.inferCnn(fen,
                    (architecture, phase, path) -> updateLoadingPhase(cardKey, fen, phase));
            case ARCH_BT4 -> provider.inferBt4(fen,
                    (architecture, phase, path) -> updateLoadingPhase(cardKey, fen, phase));
            default -> provider.inferNnue(fen,
                    (architecture, phase, path) -> updateLoadingPhase(cardKey, fen, phase));
        };
    }

    /**
     * Updates the loading card from a background inference worker.
     *
     * @param cardKey architecture card key
     * @param fen FEN being inferred
     * @param phase provider phase
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
     * Returns the loading card title for a provider phase.
     *
     * @param cardKey architecture card key
     * @param phase provider phase
     * @return short title
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
     * Returns the loading card detail for a provider phase.
     *
     * @param phase provider phase
     * @return short detail
     */
    private static String loadingDetail(RealActivations.Phase phase) {
        return switch (phase) {
            case LOADING_MODEL -> "Loading model weights";
            case RUNNING_INFERENCE -> "Running inference";
            case SYNTHETIC_FALLBACK -> "Using generated activations";
        };
    }

    /**
     * Spawns a SwingWorker that runs one forward pass for the pending
     * (architecture, FEN) pair off the EDT and publishes the resulting sealed
     * snapshot on completion.
     *
     * <p>Only the pending architecture is inferred. The snapshot the worker
     * builds is freshly allocated and sealed by the provider, so nothing the
     * worker touches is shared with the EDT — the previous fix re-filled
     * snapshot objects the views were still painting.</p>
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
            @Override
            protected ActivationSnapshot doInBackground() {
    return inferSnapshot(cardKey, fen);
            }

            @Override
            protected void done() {
                inferenceWorker = null;
                runningArch = null;
                runningFen = null;
                boolean failed = false;
                try {
                    ActivationSnapshot snapshot = get();
                    snapshotCache.put(key, snapshot);
                    if (cardKey.equals(activeCardKey()) && fen.equals(effectiveFen())) {
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
                if (!failed) {
                    refreshStatusBadge();
                } else {
                    showSelected();
                }
                if (pendingFen != null) {
                    SwingUtilities.invokeLater(NetworkPanel.this::startInference);
                } else if (displayedKey == null
                        || !displayedKey.equals(cacheKey(activeCardKey(), effectiveFen()))) {
                    requestActiveInference();
                }
            }
        };
        inferenceWorker.execute();
    }

    /**
     * Publishes a sealed snapshot to the view that owns the given card key.
     * Only the matching view is touched — the other two keep whatever they
     * last displayed until the user switches to them and triggers their own
     * (cached or fresh) inference.
     *
     * @param cardKey {@link #ARCH_NNUE} / {@link #ARCH_CNN} / {@link #ARCH_BT4}
     * @param fen FEN the snapshot was produced for
     * @param snapshot sealed activation snapshot
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
        }
    }

    /**
     * Shows the animated loading surface for one architecture/FEN pair.
     *
     * @param cardKey architecture card key
     * @param fen position FEN
     * @param detail loading detail text
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
        cards.show(cardPanel, CARD_LOADING);
        statusBadge.busy("loading " + displayNameFor(cardKey).toLowerCase(java.util.Locale.ROOT) + "...");
    }

    /**
     * Returns the first loading detail shown before the provider reports the
     * exact load/inference phase.
     *
     * @param cardKey architecture card key
     * @return initial loading detail
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
     * Clears the loading card when it belongs to the supplied architecture/FEN.
     * Passing null values clears any active loading state.
     *
     * @param cardKey architecture card key, or null to clear unconditionally
     * @param fen position FEN, or null to clear unconditionally
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
     * Returns the model detail line used by the loading screen.
     *
     * @param cardKey architecture card key
     * @return compact model detail
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
     * Returns the position detail line used by the loading screen.
     *
     * @param fen position FEN
     * @return compact position detail
     */
    private static String loadingPositionDetail(String fen) {
        if (fen == null || fen.isBlank()) {
            return "No position loaded";
        }
        String[] parts = fen.trim().split("\\s+");
        String side = parts.length > 1 && "b".equals(parts[1])
                ? "black to move"
                : "white to move";
        return parts[0] + " - " + side;
    }

    /**
     * Returns a compact display name for one architecture card.
     *
     * @param cardKey architecture card key
     * @return display name
     */
    private static String displayNameFor(String cardKey) {
        return switch (cardKey) {
            case ARCH_CNN -> ARCH_CNN_LABEL;
            case ARCH_BT4 -> ARCH_BT4_LABEL;
            default -> ARCH_NNUE_LABEL;
        };
    }
}
