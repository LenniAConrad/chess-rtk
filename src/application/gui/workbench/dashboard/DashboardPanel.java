package application.gui.workbench.dashboard;

import application.Config;
import application.gui.workbench.board.BoardStyle;
import application.gui.workbench.network.NetworkDiagnosticsPanel;
import application.gui.workbench.network.NetworkPanel;
import application.gui.workbench.network.RealActivations;
import application.gui.workbench.network.TensorViz;
import application.gui.workbench.session.HealthSnapshot;
import application.gui.workbench.session.Job;
import application.gui.workbench.session.JobTableModel;
import application.gui.workbench.session.Session;
import application.gui.workbench.session.SessionListener;
import application.gui.workbench.ui.CardGrid;
import application.gui.workbench.ui.CommandBlock;
import application.gui.workbench.ui.StatusBadge;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import application.gui.workbench.ui.WorkspaceHeader;
import chess.core.Position;
import chess.struct.Game;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import application.gui.workbench.ui.SurfacePanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * Dashboard command center for the Workbench. The panel renders from the shared
 * {@link Session} plus lightweight runtime diagnostics, then routes all actions
 * back through {@link DashboardActions}.
 */
public final class DashboardPanel extends SurfacePanel implements SessionListener {

    /**
     * Serialization identifier for Swing panel compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Shared session model the dashboard renders from.
     */
    private final transient Session session;

    /**
     * Action delegate routing quick actions back to the host window.
     */
    private final transient DashboardActions actions;

    /**
     * Shared workspace header for the dashboard surface.
     */
    private final WorkspaceHeader workspaceHeader = new WorkspaceHeader("Dashboard", "", null);

    /**
     * Current-position card: readable FEN preview.
     */
    private final CommandBlock fenValue = Ui.commandBlock("");

    /**
     * Current-position card: side to move.
     */
    private final JLabel sideValue = metricValue();

    /**
     * Current-position card: ply counter.
     */
    private final JLabel plyValue = metricValue();

    /**
     * Current-position card: legal-move count.
     */
    private final JLabel legalValue = metricValue();

    /**
     * Current-position card: material summary.
     */
    private final JLabel materialValue = metricValue();

    /**
     * Current-position card: compact board preview.
     */
    private final DashboardBoardPreview positionPreview = new DashboardBoardPreview();

    /**
     * Engine card: overall status.
     */
    private final StatusBadge engineBadge = new StatusBadge();

    /**
     * Engine card: protocol path.
     */
    private final JLabel enginePathValue = value();

    /**
     * Engine card: live/offline state.
     */
    private final JLabel engineModeValue = value();

    /**
     * Engine card: latest eval / best move summary.
     */
    private final JLabel engineSummaryValue = value();

    /**
     * Health card: config-validation state.
     */
    private final StatusBadge configBadge = new StatusBadge();

    /**
     * Health card: doctor state.
     */
    private final StatusBadge doctorBadge = new StatusBadge();

    /**
     * Health card: engine smoke-test state.
     */
    private final StatusBadge smokeBadge = new StatusBadge();

    /**
     * Shared compact Network tab diagnostics used by the Dashboard runtime card.
     */
    private final NetworkDiagnosticsPanel networkDiagnostics = new NetworkDiagnosticsPanel(false);

    /**
     * Warning/setup issue rows.
     */
    private final JPanel warningRows = rowsPanel();

    /**
     * Recent-jobs table model.
     */
    private final transient JobTableModel jobModel;

    /**
     * Recent-jobs table.
     */
    private final JTable jobTable;

    /**
     * Recent-jobs state hint above the table.
     */
    private final JLabel jobsCaption = caption("Newest first - select a row for actions");

    /**
     * Card container swapping the jobs list with an empty state.
     */
    private final JPanel jobsBody = new JPanel(new java.awt.CardLayout());

    /**
     * Recent-job action row, hidden while there are no jobs.
     */
    private JComponent jobActionRow;

    /**
     * Recent-job actions that require a selected job row.
     */
    private List<JButton> jobActionButtons = List.of();

    /**
     * Lightweight provider used only for runtime model-status previews.
     */
    private final RealActivations runtimeProvider = new RealActivations();

    /**
     * Creates the dashboard panel.
     *
     * @param session shared session model
     * @param actions action delegate routing quick actions to the host window
     */
    public DashboardPanel(Session session, DashboardActions actions) {
        super(new BorderLayout());
        this.session = session;
        this.actions = actions;
        this.jobModel = new JobTableModel(session.jobs());
        this.jobTable = new JTable(jobModel);

        CardGrid summaryGrid = Ui.contentGrid(300);
        summaryGrid.add(buildPositionCard());
        summaryGrid.add(buildEngineCard());
        summaryGrid.add(buildHealthCard());

        CardGrid lowerGrid = Ui.contentGrid(420);
        lowerGrid.add(buildQuickActionsCard());
        lowerGrid.add(buildNetworkRuntimeCard());
        lowerGrid.add(buildWarningsCard());
        lowerGrid.add(buildJobsCard());

        JPanel content = Ui.transparentPanel(new GridBagLayout());
        content.setBorder(Theme.pad(Theme.SPACE_MD));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTH;
        c.insets = new Insets(Theme.SPACE_XS, Theme.SPACE_XS,
                Theme.SPACE_SM, Theme.SPACE_XS);

        c.gridy = 0;
        content.add(summaryGrid, c);

        c.gridy = 1;
        c.weighty = 1.0;
        content.add(lowerGrid, c);

        add(buildToolbarBand(), BorderLayout.NORTH);
        add(Ui.scroll(Ui.fillViewport(content), () -> Theme.PANEL_SOLID), BorderLayout.CENTER);

        session.addListener(this);
        render();
    }

    // ------------------------------------------------------------------
    // Cards
    // ------------------------------------------------------------------

    /**
     * Builds the workspace-header band.
     *
     * @return toolbar band component
     */
    private JComponent buildToolbarBand() {
        workspaceHeader.setActions(Ui.controlRow(java.awt.FlowLayout.RIGHT,
                Ui.button("Refresh", Theme.ButtonVariant.SECONDARY, e -> actions.refresh()),
                Ui.button("Open Folder", Theme.ButtonVariant.SECONDARY, e -> actions.openSessionFolder())));
        return workspaceHeader;
    }

    /**
     * Builds the current-position card.
     *
     * @return position card
     */
    private JComponent buildPositionCard() {
        fenValue.setRows(3);
        fenValue.setMinimumSize(new Dimension(0, 70));
        fenValue.setMaximumSize(new Dimension(Integer.MAX_VALUE, 76));
        fenValue.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel body = cardBody();
        JPanel textStack = cardBody();
        textStack.add(fenValue);
        textStack.add(Box.createVerticalStrut(Theme.SPACE_SM));
        textStack.add(metricGrid(
                metric("Side", sideValue),
                metric("Ply", plyValue),
                metric("Legal", legalValue),
                metric("Material", materialValue)));
        JPanel overview = Ui.transparentPanel(new BorderLayout(Theme.SPACE_MD, 0));
        overview.setAlignmentX(Component.LEFT_ALIGNMENT);
        overview.add(textStack, BorderLayout.CENTER);
        overview.add(positionPreview, BorderLayout.EAST);
        body.add(overview);
        body.add(Box.createVerticalStrut(Theme.SPACE_SM));
        body.add(actionRow(
                quickButton("Copy FEN", actions::copyCurrentFen),
                quickButton("Analyze", actions::analyze),
                quickButton("Search", actions::builtInSearch),
                quickButton("Open Board", actions::openAnalyzeTab)));
        return card("Current Position", body);
    }

    /**
     * Builds the Engine Status card.
     *
     * @return card component
     */
    private JComponent buildEngineCard() {
        JPanel body = cardBody();
        body.add(infoRow("Protocol", enginePathValue));
        body.add(infoRow("Mode", engineModeValue));
        body.add(infoRow("Latest", engineSummaryValue));
        body.add(Box.createVerticalStrut(Theme.SPACE_SM));
        body.add(actionRow(quickButton("Smoke test", actions::engineSmoke)));
        return card("Engine Status", engineBadge, body);
    }

    /**
     * Builds the Health Checks card.
     *
     * @return card component
     */
    private JComponent buildHealthCard() {
        JPanel body = cardBody();
        body.add(healthActionRow("Config validate", configBadge,
                quickButton("Validate", actions::configValidate)));
        body.add(healthActionRow("Doctor", doctorBadge,
                quickButton("Run doctor", actions::doctor)));
        body.add(healthActionRow("Engine smoke", smokeBadge,
                quickButton("Smoke test", actions::engineSmoke)));
        body.add(Box.createVerticalStrut(Theme.SPACE_SM));
        body.add(actionRow(quickButton("Run all checks", actions::runAllHealthChecks)));
        return card("Health Checks", body);
    }

    /**
     * Builds the Quick Actions card.
     *
     * @return card component
     */
    private JComponent buildQuickActionsCard() {
        JPanel body = cardBody();
        body.add(actionRow(
                Ui.button("Analyze position", Theme.ButtonVariant.PRIMARY,
                        event -> actions.openAnalyzeTab()),
                quickButton("Open Batch", actions::openBatchTab),
                quickButton("Open Console", actions::openConsoleTab),
                quickButton("Run all checks", actions::runAllHealthChecks)));
        return card("Quick Actions", body);
    }

    /**
     * Builds the Network Runtime card.
     *
     * @return card component
     */
    private JComponent buildNetworkRuntimeCard() {
        JPanel body = cardBody();
        networkDiagnostics.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(networkDiagnostics);
        return card("Network Runtime", body);
    }

    /**
     * Builds the Recent Jobs card.
     *
     * @return card component
     */
    private JComponent buildJobsCard() {
        jobTable.setFillsViewportHeight(true);
        jobTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        jobTable.setRowHeight(Theme.TABLE_ROW_HEIGHT);
        jobTable.getTableHeader().setReorderingAllowed(false);
        jobTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        Ui.styleComponentTree(jobTable);
        jobTable.getColumnModel().getColumn(JobTableModel.COL_COMMAND)
                .setPreferredWidth(320);
        jobTable.getColumnModel().getColumn(JobTableModel.COL_STATUS)
                .setPreferredWidth(90);
        jobTable.getColumnModel().getColumn(JobTableModel.COL_DURATION)
                .setPreferredWidth(80);
        jobTable.getColumnModel().getColumn(JobTableModel.COL_RESULT)
                .setPreferredWidth(300);

        JScrollPane jobScrollPane = Ui.scroll(jobTable);
        jobScrollPane.setPreferredSize(new Dimension(0, 156));

        JButton retryButton = quickButton("Retry", () -> withSelectedJob(actions::retryJob));
        JButton copyButton = quickButton("Copy command", () -> withSelectedJob(actions::copyJobCommand));
        JButton logButton = quickButton("Open log", () -> withSelectedJob(actions::openJobLog));
        JButton manifestButton = quickButton("Open manifest", () -> withSelectedJob(actions::openJobManifest));
        jobActionButtons = List.of(retryButton, copyButton, logButton, manifestButton);
        jobActionRow = actionRow(retryButton, copyButton, logButton, manifestButton);
        jobTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                updateJobActionState();
            }
        });
        jobModel.addTableModelListener(event -> updateJobActionState());

        JPanel list = cardBody();
        list.add(jobsCaption);
        list.add(Box.createVerticalStrut(Theme.SPACE_XS));
        list.add(jobScrollPane);
        list.add(Box.createVerticalStrut(Theme.SPACE_SM));
        list.add(jobActionRow);

        jobsBody.setOpaque(false);
        jobsBody.add(list, "list");
        jobsBody.add(Ui.emptyState("No recent jobs",
                "Analyses, batches, dataset scans, and gauntlets will appear here.",
                Ui.button("Open Run", Theme.ButtonVariant.PRIMARY, event -> actions.openBatchTab()),
                quickButton("Run health checks", actions::runAllHealthChecks)), "empty");
        updateJobActionState();
        return card("Recent Jobs", jobsBody);
    }

    /**
     * Builds the Warnings / Setup Issues card.
     *
     * @return card component
     */
    private JComponent buildWarningsCard() {
        JPanel body = cardBody();
        body.add(warningRows);
        return card("Warnings / Setup Issues", body);
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    /**
     * Re-renders every card from the current session state. Safe to call from
     * any thread.
     */
    private void render() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::render);
            return;
        }
        String sessionFen = session.fen();
        String fen = sessionFen.isEmpty() ? Game.STANDARD_START_FEN : sessionFen;
        fenValue.setText(fen);
        fenValue.setToolTipText(fen);
        positionPreview.setFen(fen);
        renderPositionMetrics(fen, sessionFen.isEmpty());

        String protocol = session.engineProtocolPath();
        enginePathValue.setText(protocol.isEmpty() ? "(CLI default)" : compactPath(protocol));
        enginePathValue.setToolTipText(protocol.isEmpty() ? null : protocol);
        String engineSummary = session.engineSummary();
        boolean pausedLiveEngine = session.liveEngine() && "paused".equalsIgnoreCase(engineSummary);
        engineModeValue.setText(pausedLiveEngine ? "paused" : session.liveEngine() ? "live" : "offline");
        engineSummaryValue.setText(pausedLiveEngine || engineSummary.isEmpty() ? "-" : engineSummary);
        engineSummaryValue.setToolTipText(pausedLiveEngine || engineSummary.isEmpty() ? null : engineSummary);
        applyEngineBadge(protocol, engineSummary, pausedLiveEngine);

        HealthSnapshot health = session.health();
        applyHealthBadge(configBadge, health.config());
        applyHealthBadge(doctorBadge, health.doctor());
        applyHealthBadge(smokeBadge, health.engineSmoke());
        refreshRuntimeSections(health);
        workspaceHeader.setContext(dashboardContext(health));
    }

    /**
     * Renders current-position metrics from a FEN, falling back to session values
     * if a malformed snapshot reaches the dashboard.
     *
     * @param fen display FEN
     * @param defaultStart true when the session has not yet published a FEN
     */
    private void renderPositionMetrics(String fen, boolean defaultStart) {
        try {
            Position position = new Position(fen);
            sideValue.setText(position.isWhiteToMove() ? "White" : "Black");
            plyValue.setText(defaultStart ? "0 / 0" : session.ply() + " / " + session.lastPly());
            legalValue.setText(Integer.toString(position.legalMoves().size()));
            String taggedMaterial = defaultStart ? "Pending" : materialStatus(session.tags());
            materialValue.setText("Pending".equals(taggedMaterial)
                    ? materialFromCentipawns(position.materialDiscrepancy())
                    : taggedMaterial);
        } catch (IllegalArgumentException ex) {
            sideValue.setText(session.whiteToMove() ? "White" : "Black");
            plyValue.setText(session.ply() + " / " + session.lastPly());
            legalValue.setText(Integer.toString(session.legalMoveCount()));
            materialValue.setText(materialStatus(session.tags()));
        }
    }

    /**
     * Refreshes all runtime-derived card rows.
     *
     * @param health latest health snapshot
     */
    private void refreshRuntimeSections(HealthSnapshot health) {
        networkDiagnostics.refresh(runtimeProvider, "Dashboard",
                NetworkPanel.runtimeCacheSummary());
        refreshWarningRows(health);
    }

    /**
     * Rebuilds warning/setup rows.
     *
     * @param health latest health snapshot
     */
    private void refreshWarningRows(HealthSnapshot health) {
        warningRows.removeAll();
        List<JComponent> issues = new ArrayList<>();
        addHealthIssue(issues, "Config validate", health.config(),
                "Validate the CLI config before running batch or publish workflows.");
        addHealthIssue(issues, "Doctor", health.doctor(),
                "Run doctor when tools, native backends, or environment paths look wrong.");
        addHealthIssue(issues, "Engine smoke", health.engineSmoke(),
                "Smoke test the configured UCI protocol before trusting engine output.");
        for (RealActivations.ModelStatus status : runtimeProvider.modelStatuses()) {
            if (!status.present() || "fallback".equals(status.state())) {
                String state = "fallback".equals(status.state()) ? "fallback" : "missing";
                issues.add(statusDetailRow(status.label(), state, statusRole(state),
                        modelSource(status), modelNote(status)));
            }
        }
        addConfigPathIssue(issues, "Config LC0", Config.getLc0ModelPath());
        addConfigPathIssue(issues, "Config T5", Config.getT5ModelPath());

        if (issues.isEmpty()) {
            warningRows.add(Ui.emptyState("No setup issues",
                    "Health checks and runtime diagnostics are quiet."));
        } else {
            for (JComponent issue : issues) {
                warningRows.add(issue);
            }
        }
        warningRows.revalidate();
        warningRows.repaint();
    }

    /**
     * Refreshes the dashboard after a session change.
     *
     * @param changed updated session
     */
    @Override
    public void sessionChanged(Session changed) {
        render();
    }

    // ------------------------------------------------------------------
    // Status mapping
    // ------------------------------------------------------------------

    /**
     * Applies the engine badge state.
     *
     * @param protocol configured protocol path
     * @param summary latest engine summary
     * @param pausedLiveEngine true when live mode is explicitly paused
     */
    private void applyEngineBadge(String protocol, String summary, boolean pausedLiveEngine) {
        String normalized = summary == null ? "" : summary.toLowerCase(Locale.ROOT);
        if (normalized.contains("failed") || normalized.contains("error")) {
            engineBadge.error("error");
            return;
        }
        if (protocol != null && !protocol.isBlank() && !pathExists(protocol)) {
            engineBadge.missing("missing");
            return;
        }
        if (session.liveEngine() && !pausedLiveEngine) {
            engineBadge.ready("ready");
        } else {
            engineBadge.paused("paused");
        }
    }

    /**
     * Applies a health-check badge state.
     *
     * @param badge target badge
     * @param check check state
     */
    private static void applyHealthBadge(StatusBadge badge, HealthSnapshot.Check check) {
        switch (check) {
            case OK -> badge.complete("complete");
            case FAILED -> badge.error("failed");
            case RUNNING -> badge.running("running");
            case UNKNOWN -> badge.notRun("not run");
        }
    }

    /**
     * Adds one setup issue for a health check when it failed.
     *
     * @param issues target issue list
     * @param label row label
     * @param check check state
     * @param note explanatory note
     */
    private static void addHealthIssue(List<JComponent> issues, String label,
            HealthSnapshot.Check check, String note) {
        if (check != HealthSnapshot.Check.FAILED) {
            return;
        }
        issues.add(statusDetailRow(label, "failed", Theme.ForegroundRole.ERROR, "Health check", note));
    }

    /**
     * Adds one setup issue for a missing configured model path.
     *
     * @param issues target issue list
     * @param label row label
     * @param pathText configured path text
     */
    private static void addConfigPathIssue(List<JComponent> issues, String label, String pathText) {
        if (pathText == null || pathText.isBlank() || pathExists(pathText)) {
            return;
        }
        issues.add(statusDetailRow(label, "missing", Theme.ForegroundRole.ERROR,
                "Configured model path", pathText));
    }

    /**
     * Returns the dashboard shell context line.
     *
     * @param health latest health snapshot
     * @return context summary
     */
    private String dashboardContext(HealthSnapshot health) {
        String position = session.fen().isEmpty() ? "Start position" : "Loaded position";
        String engine = engineContext();
        int issues = setupIssueCount(health);
        String issueText = issues == 0 ? "No setup issues"
                : issues + " setup issue" + (issues == 1 ? "" : "s");
        return position + " - " + engine + " - " + issueText;
    }

    /**
     * Returns the dashboard engine context phrase.
     *
     * @return engine phrase
     */
    private String engineContext() {
        String summary = session.engineSummary();
        if (summary != null && summary.toLowerCase(Locale.ROOT).contains("failed")) {
            return "Engine error";
        }
        if (session.liveEngine() && summary != null && !summary.equalsIgnoreCase("paused")) {
            return "Engine ready";
        }
        return "Engine paused";
    }

    /**
     * Counts active setup issues for the header context.
     *
     * @param health latest health snapshot
     * @return issue count
     */
    private int setupIssueCount(HealthSnapshot health) {
        int count = 0;
        for (HealthSnapshot.Check check : new HealthSnapshot.Check[] {
                health.config(), health.doctor(), health.engineSmoke()
        }) {
            if (check == HealthSnapshot.Check.FAILED) {
                count++;
            }
        }
        for (RealActivations.ModelStatus status : runtimeProvider.modelStatuses()) {
            if (!status.present() || "fallback".equals(status.state())) {
                count++;
            }
        }
        if (Config.getLc0ModelPath() != null && !Config.getLc0ModelPath().isBlank()
                && !pathExists(Config.getLc0ModelPath())) {
            count++;
        }
        if (Config.getT5ModelPath() != null && !Config.getT5ModelPath().isBlank()
                && !pathExists(Config.getT5ModelPath())) {
            count++;
        }
        return count;
    }

    // ------------------------------------------------------------------
    // Recent jobs
    // ------------------------------------------------------------------

    /**
     * Runs an action with the currently-selected job, if any.
     *
     * @param consumer action to run on the selected job
     */
    private void withSelectedJob(java.util.function.Consumer<Job> consumer) {
        Job job = jobModel.jobAt(jobTable.getSelectedRow());
        if (job != null) {
            consumer.accept(job);
        }
    }

    /**
     * Enables recent-job actions only while a real table row is selected.
     */
    private void updateJobActionState() {
        boolean hasRows = jobModel.getRowCount() > 0;
        boolean hasSelection = jobModel.jobAt(jobTable.getSelectedRow()) != null;
        ((java.awt.CardLayout) jobsBody.getLayout()).show(jobsBody, hasRows ? "list" : "empty");
        if (jobActionRow != null) {
            jobActionRow.setVisible(hasRows);
        }
        for (JButton button : jobActionButtons) {
            button.setEnabled(hasSelection);
        }
    }

    // ------------------------------------------------------------------
    // Row builders
    // ------------------------------------------------------------------

    /**
     * Builds one health row with a status badge and action.
     *
     * @param label row label
     * @param badge status badge
     * @param action action button
     * @return row component
     */
    private static JComponent healthActionRow(String label, StatusBadge badge, JButton action) {
        JPanel row = Ui.transparentPanel(new GridBagLayout());
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(Theme.pad(Theme.SPACE_XS / 2, 0));

        JLabel title = new JLabel(label);
        title.setFont(Theme.font(Theme.FONT_CONTROL, Font.BOLD));
        Theme.foreground(title, Theme.ForegroundRole.TEXT);
        badge.setFixedTextWidth(74);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, 0, 0, Theme.SPACE_SM);
        row.add(title, c);

        c.gridx = 1;
        c.weightx = 0.0;
        c.fill = GridBagConstraints.NONE;
        row.add(badge, c);

        c.gridx = 2;
        c.insets = new Insets(0, 0, 0, 0);
        row.add(action, c);
        return row;
    }

    /**
     * Builds one aligned status/detail row.
     *
     * @param label row label
     * @param state short state text
     * @param role foreground role for the state text
     * @param source source or size
     * @param note explanatory note
     * @return row component
     */
    private static JComponent statusDetailRow(String label, String state,
            Theme.ForegroundRole role, String source, String note) {
        JPanel row = Ui.transparentPanel(new BorderLayout(0, 2));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(Theme.pad(Theme.SPACE_XS, 0, Theme.SPACE_XS, 0));

        JLabel title = new JLabel(label);
        title.setFont(Theme.font(Theme.FONT_CONTROL, Font.BOLD));
        Theme.foreground(title, Theme.ForegroundRole.TEXT);
        title.setToolTipText(label);

        JLabel stateLabel = new JLabel(state == null || state.isBlank() ? "-" : state);
        stateLabel.setFont(Theme.font(Theme.FONT_METADATA, Font.BOLD));
        Theme.foreground(stateLabel, role == null ? Theme.ForegroundRole.MUTED : role);
        stateLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        stateLabel.setPreferredSize(new Dimension(92, Theme.CONTROL_HEIGHT));
        stateLabel.setToolTipText(stateLabel.getText());

        JPanel top = Ui.transparentPanel(new BorderLayout(Theme.SPACE_SM, 0));
        top.add(title, BorderLayout.CENTER);
        top.add(stateLabel, BorderLayout.EAST);
        row.add(top, BorderLayout.NORTH);

        JLabel sourceLabel = new JLabel(source == null || source.isBlank() ? "-" : source);
        sourceLabel.setFont(Theme.font(Theme.FONT_METADATA, Font.PLAIN));
        Theme.foreground(sourceLabel, Theme.ForegroundRole.INFO);
        sourceLabel.setToolTipText(sourceLabel.getText());

        JLabel noteLabel = new JLabel(note == null || note.isBlank() ? "-" : note);
        noteLabel.setFont(Theme.font(Theme.FONT_METADATA, Font.PLAIN));
        Theme.foreground(noteLabel, Theme.ForegroundRole.MUTED);
        noteLabel.setToolTipText(noteLabel.getText());

        JPanel bottom = Ui.transparentPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.weightx = 0.55;
        c.insets = new Insets(0, 0, 0, Theme.SPACE_SM);
        bottom.add(sourceLabel, c);
        c.gridx = 1;
        c.weightx = 0.45;
        c.insets = new Insets(0, 0, 0, 0);
        bottom.add(noteLabel, c);
        row.add(bottom, BorderLayout.CENTER);
        return row;
    }

    /**
     * Returns a lightweight text colour for passive status rows.
     *
     * @param state state token
     * @return foreground role
     */
    private static Theme.ForegroundRole statusRole(String state) {
        String normalized = state == null ? "" : state.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "loaded", "available", "present", "ready" -> Theme.ForegroundRole.SUCCESS;
            case "fallback" -> Theme.ForegroundRole.WARNING;
            case "missing", "invalid", "failed" -> Theme.ForegroundRole.ERROR;
            default -> Theme.ForegroundRole.MUTED;
        };
    }

    /**
     * Builds a "caption: value" row.
     *
     * @param caption left-hand caption
     * @param value right-hand value label
     * @return row component
     */
    private static JComponent infoRow(String caption, JLabel value) {
        JPanel row = Ui.transparentPanel(new BorderLayout(Theme.SPACE_SM, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel captionLabel = new JLabel(caption);
        captionLabel.setFont(Theme.font(Theme.FONT_CONTROL, Font.BOLD));
        Theme.foreground(captionLabel, Theme.ForegroundRole.MUTED);
        captionLabel.setPreferredSize(new Dimension(92, Theme.CONTROL_HEIGHT - 8));
        captionLabel.setVerticalAlignment(SwingConstants.TOP);
        row.add(captionLabel, BorderLayout.WEST);
        value.setHorizontalAlignment(SwingConstants.RIGHT);
        row.add(value, BorderLayout.CENTER);
        row.setBorder(Theme.pad(Theme.SPACE_XS / 2, 0));
        return row;
    }

    /**
     * Builds a two-column metric grid.
     *
     * @param metrics metric components
     * @return metric grid
     */
    private static JComponent metricGrid(JComponent... metrics) {
        JPanel grid = Ui.transparentPanel(new GridBagLayout());
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (int i = 0; i < metrics.length; i++) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = i % 2;
            c.gridy = i / 2;
            c.weightx = 1.0;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(0, 0, Theme.SPACE_SM, i % 2 == 0 ? Theme.SPACE_SM : 0);
            grid.add(metrics[i], c);
        }
        return grid;
    }

    /**
     * Builds one small metric block.
     *
     * @param label metric label
     * @param value metric value label
     * @return metric component
     */
    private static JComponent metric(String label, JLabel value) {
        JPanel panel = Ui.transparentPanel(null);
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
     * Builds a left-aligned row of quick-action buttons.
     *
     * @param buttons buttons to lay out
     * @return row component
     */
    private static JComponent actionRow(JButton... buttons) {
        JPanel row = Ui.transparentPanel(
                new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, Theme.SPACE_SM, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (JButton button : buttons) {
            row.add(button);
        }
        return row;
    }

    // ------------------------------------------------------------------
    // Text/data helpers
    // ------------------------------------------------------------------

    /**
     * Creates a value label.
     *
     * @return styled value label
     */
    private static JLabel value() {
        JLabel label = new JLabel("-");
        label.setFont(Theme.font(Theme.FONT_CONTROL, Font.PLAIN));
        Theme.foreground(label, Theme.ForegroundRole.TEXT);
        return label;
    }

    /**
     * Creates a stronger metric value label.
     *
     * @return styled metric value
     */
    private static JLabel metricValue() {
        JLabel label = new JLabel("-");
        label.setFont(Theme.font(13, Font.BOLD));
        Theme.foreground(label, Theme.ForegroundRole.TEXT);
        return label;
    }

    /**
     * Creates a muted caption label.
     *
     * @param text caption text
     * @return styled caption label
     */
    private static JLabel caption(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.font(Theme.FONT_METADATA, Font.PLAIN));
        Theme.foreground(label, Theme.ForegroundRole.MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    /**
     * Creates a compact secondary quick-action button.
     *
     * @param text button text
     * @param action action to run on click
     * @return styled button
     */
    private static JButton quickButton(String text, Runnable action) {
        return Ui.button(text, Theme.ButtonVariant.SECONDARY, event -> action.run());
    }

    /**
     * Wraps dashboard content in the shared elevated card.
     *
     * @param title section title
     * @param body section body
     * @return card component
     */
    private static JComponent card(String title, JComponent body) {
        return Ui.card(title, body);
    }

    /**
     * Wraps dashboard content in the shared elevated card with trailing chrome.
     *
     * @param title section title
     * @param trailing trailing header component
     * @param body section body
     * @return card component
     */
    private static JComponent card(String title, JComponent trailing, JComponent body) {
        return Ui.card(title, trailing, body);
    }

    /**
     * Creates an empty vertical card-body panel.
     *
     * @return card body panel
     */
    private static JPanel cardBody() {
        JPanel body = Ui.transparentPanel(null);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        return body;
    }

    /**
     * Small board thumbnail used by the current-position dashboard card.
     */
    private static final class DashboardBoardPreview extends JComponent {

        /**
         * Serialization identifier for Swing component compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * FEN currently shown by the thumbnail.
         */
        private String fen = Game.STANDARD_START_FEN;

        /**
         * Creates the dashboard board preview.
         */
        DashboardBoardPreview() {
            setOpaque(false);
            setPreferredSize(new Dimension(118, 118));
            setMinimumSize(new Dimension(96, 96));
        }

        /**
         * Updates the rendered FEN.
         *
         * @param next next FEN
         */
        void setFen(String next) {
            fen = next == null || next.isBlank() ? Game.STANDARD_START_FEN : next;
            repaint();
        }

        /**
         * Paints the thumbnail board.
         *
         * @param graphics graphics context
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int pad = Theme.scaledPx(7);
                int side = Math.max(72, Math.min(getWidth(), getHeight()) - pad * 2);
                int x = (getWidth() - side) / 2;
                int y = (getHeight() - side) / 2;
                g.setColor(Theme.withAlpha(Theme.ACCENT, Theme.isDark() ? 34 : 22));
                g.fillRoundRect(x - pad, y - pad, side + pad * 2, side + pad * 2,
                        Theme.scaledPx(10), Theme.scaledPx(10));
                Rectangle board = new Rectangle(x, y, side, side);
                BoardStyle.drawBoardSurface(g, board, true);
                try {
                    boolean whiteDown = TensorViz.whiteDownForSideToMove(fen);
                    TensorViz.drawPositionPieces(g, board, fen, whiteDown);
                    TensorViz.drawBoardCoordinates(g, board, whiteDown);
                } catch (IllegalArgumentException ignored) {
                    // The dashboard falls back to textual metrics for malformed snapshots.
                }
            } finally {
                g.dispose();
            }
        }
    }

    /**
     * Creates an empty vertical rows panel.
     *
     * @return rows panel
     */
    private static JPanel rowsPanel() {
        JPanel panel = Ui.transparentPanel(null);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    /**
     * Returns a model row source string.
     *
     * @param status model status
     * @return source/size text
     */
    private static String modelSource(RealActivations.ModelStatus status) {
        String size = detailSize(status.detail());
        String path = status.path() == null ? "" : status.path().getFileName().toString();
        if (!size.isBlank() && !path.isBlank()) {
            return size + " - " + path;
        }
        if (!path.isBlank()) {
            return path;
        }
        return status.present() ? "local file" : "fallback";
    }

    /**
     * Returns a model row note string.
     *
     * @param status model status
     * @return note text
     */
    private static String modelNote(RealActivations.ModelStatus status) {
        String detail = status.detail();
        int split = detail == null ? -1 : detail.indexOf(" - ");
        if (split >= 0 && split + 3 < detail.length()) {
            return detail.substring(split + 3);
        }
        return detail == null || detail.isBlank() ? status.state() : detail;
    }

    /**
     * Extracts a leading size token from a detail string.
     *
     * @param detail detail text
     * @return size token or blank
     */
    private static String detailSize(String detail) {
        if (detail == null) {
            return "";
        }
        int split = detail.indexOf(" - ");
        String first = split >= 0 ? detail.substring(0, split) : detail;
        return first.matches("[0-9.]+ [KMG]?B") ? first : "";
    }

    /**
     * Returns a best-effort material summary from current position tags.
     *
     * @param tags position tags
     * @return material summary
     */
    private static String materialStatus(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "Pending";
        }
        for (String tag : tags) {
            String lower = tag.toLowerCase(Locale.ROOT);
            if (lower.contains("material_discrepancy_cp=")) {
                Integer cp = parseTaggedCentipawns(tag, "material_discrepancy_cp=");
                if (cp != null) {
                    return materialFromCentipawns(cp.intValue());
                }
            }
        }
        for (String tag : tags) {
            String lower = tag.toLowerCase(Locale.ROOT);
            if (lower.startsWith("material:") || lower.startsWith("material ")) {
                if (lower.contains("equal") || lower.contains("even")) {
                    return "Even";
                }
                return trimMaterialTag(tag);
            }
        }
        return "Pending";
    }

    /**
     * Parses a centipawn value from a tagged key.
     *
     * @param tag source tag
     * @param key key prefix
     * @return parsed value, or null
     */
    private static Integer parseTaggedCentipawns(String tag, String key) {
        int start = tag.toLowerCase(Locale.ROOT).indexOf(key);
        if (start < 0) {
            return null;
        }
        int valueStart = start + key.length();
        int valueEnd = valueStart;
        while (valueEnd < tag.length()) {
            char ch = tag.charAt(valueEnd);
            if ((ch < '0' || ch > '9') && ch != '-' && ch != '+') {
                break;
            }
            valueEnd++;
        }
        try {
            return Integer.valueOf(tag.substring(valueStart, valueEnd));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Formats a material centipawn delta.
     *
     * @param cp centipawns, white-relative
     * @return material label
     */
    private static String materialFromCentipawns(int cp) {
        if (Math.abs(cp) < 25) {
            return "Even";
        }
        int abs = Math.abs(cp);
        return (cp > 0 ? "White" : "Black") + " +" + abs + " cp";
    }

    /**
     * Returns a readable material tag body.
     *
     * @param tag source tag
     * @return compact label
     */
    private static String trimMaterialTag(String tag) {
        int split = tag.indexOf(':');
        String body = split >= 0 ? tag.substring(split + 1).trim() : tag.trim();
        if (body.length() > 22) {
            return body.substring(0, 21) + "...";
        }
        return body.isBlank() ? "Pending" : body;
    }

    /**
     * Returns a compact display path.
     *
     * @param text path text
     * @return compact path
     */
    private static String compactPath(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        try {
            Path path = Path.of(text);
            Path file = path.getFileName();
            return file == null ? path.toString() : file.toString();
        } catch (RuntimeException ex) {
            return text;
        }
    }

    /**
     * Returns whether a path exists without throwing.
     *
     * @param text path text
     * @return true when the path exists
     */
    private static boolean pathExists(String text) {
        try {
            return text != null && !text.isBlank() && Files.exists(Path.of(text));
        } catch (RuntimeException ex) {
            return false;
        }
    }

}
