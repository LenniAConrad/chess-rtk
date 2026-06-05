package application.gui.workbench.dashboard;

import application.gui.workbench.session.HealthSnapshot;
import application.gui.workbench.session.Job;
import application.gui.workbench.session.JobTableModel;
import application.gui.workbench.session.Session;
import application.gui.workbench.session.SessionListener;
import application.gui.workbench.network.NetworkDiagnosticsPanel;
import application.gui.workbench.network.NetworkPanel;
import application.gui.workbench.network.RealActivations;
import application.gui.workbench.ui.CardGrid;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridLayout;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Path;
import java.util.List;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * The workbench Dashboard tab — a single operational overview of the current
 * position, engine status, recent command jobs, and environment health, with
 * quick actions that route into the deeper workbench tabs.
 *
 * <p>The panel renders entirely from the shared {@link Session} model
 * (plus its {@link JobManager} and {@link ArtifactIndex}); it
 * never scrapes Swing components or console text. Quick-action buttons delegate
 * to the host window through {@link DashboardActions}.</p>
 */
public final class DashboardPanel extends JPanel implements SessionListener {

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
     * Current-position card: compact FEN.
     */
    private final JLabel fenValue = value();

    /**
     * Current-position card: side to move.
     */
    private final JLabel sideValue = value();

    /**
     * Current-position card: ply counter.
     */
    private final JLabel plyValue = value();

    /**
     * Current-position card: legal-move count.
     */
    private final JLabel legalValue = value();

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
    private final JLabel healthConfigValue = value();

    /**
     * Health card: doctor state.
     */
    private final JLabel healthDoctorValue = value();

    /**
     * Health card: engine-smoke state.
     */
    private final JLabel healthSmokeValue = value();

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
    private final JLabel jobsCaption = caption("Newest first · select a row for actions");

    /**
     * Recent-jobs scroll pane, hidden while there are no jobs.
     */
    private JScrollPane jobScrollPane;

    /**
     * Card container swapping the jobs list with a centered empty-state.
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
     * Lightweight provider used only for runtime diagnostic previews.
     */
    private final RealActivations networkDiagnosticsProvider = new RealActivations();

    /**
     * Dashboard-hosted network runtime diagnostics.
     */
    private final NetworkDiagnosticsPanel networkDiagnosticsPanel =
            new NetworkDiagnosticsPanel(false);

    /**
     * Outputs card body — rebuilt whenever the artifact index changes.
     */
    private final JPanel artifactList = new JPanel();

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

        setOpaque(true);
        setBackground(Theme.BG);

        // The compact status cards flow through a responsive masonry so the
        // dashboard uses the full desktop canvas instead of a narrow centred
        // ribbon: it packs into as many columns as the window allows, drops
        // each card into the shortest column, and reflows on resize. The wide
        // data panels (network runtime, recent jobs) fill the room below it.
        CardGrid statusGrid = Ui.contentGrid(340);
        statusGrid.add(buildPositionCard());
        statusGrid.add(buildEngineCard());
        statusGrid.add(buildHealthCard());

        JPanel grid = Ui.transparentPanel(new GridBagLayout());
        grid.setBorder(Theme.pad(Theme.SPACE_MD));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTH;
        c.insets = new Insets(Theme.SPACE_XS, Theme.SPACE_XS,
                Theme.SPACE_XS, Theme.SPACE_XS);

        // Status cards pack across the top at their natural height.
        c.gridy = 0;
        grid.add(statusGrid, c);

        // The two wide data panels stretch to fill the remaining canvas so the
        // dashboard never bottoms out into a dead void — the same full-height
        // body the Network and Datasets tabs use. GridLayout forces both cards
        // to the cell height; their scroll / empty-state bodies grow gracefully.
        JPanel dataRow = Ui.transparentPanel(new GridLayout(1, 2, Theme.SPACE_MD, 0));
        dataRow.add(buildNetworkRuntimeCard());
        dataRow.add(buildJobsCard());
        c.gridy = 1;
        c.weighty = 1.0;
        c.fill = GridBagConstraints.BOTH;
        grid.add(dataRow, c);

        // A top toolbar band leads with the primary action and a hairline
        // divider, the same chrome the Network and Datasets tabs open with so
        // the operational tabs read as one design system.
        add(buildToolbarBand(), BorderLayout.NORTH);
        // Declare the backdrop so cards float on BG, not the darker PANEL_SOLID
        // the viewport would otherwise stamp behind a transparent grid.
        add(Ui.scroll(Ui.fillViewport(grid), () -> Theme.BG), BorderLayout.CENTER);

        session.addListener(this);
        session.artifacts().addListener(() -> SwingUtilities.invokeLater(this::refreshArtifacts));
        refreshArtifacts();
        render();
    }

    // ------------------------------------------------------------------
    // Cards
    // ------------------------------------------------------------------

    /**
     * Builds the top toolbar band: a {@code PANEL_SOLID} strip closed by a
     * hairline divider, carrying the dashboard's quick actions. This mirrors the
     * toolbar the Network and Datasets tabs lead with so the operational tabs
     * share one chrome instead of the dashboard alone opening with a card.
     *
     * @return toolbar band component
     */
    private JComponent buildToolbarBand() {
        // A titled lead band states what the surface is up front, then carries
        // its quick actions on the right — the same identity band the other
        // overview surfaces (Datasets, Publish) lead with so they read as a set.
        // The primary "Analyze position" CTA stays blue; the rest are quiet.
        return Ui.surfaceHeader("Dashboard",
                "Snapshot of the loaded position, engine status, and recent activity",
                actionRow(
                        Ui.button("Analyze position", true, event -> actions.openAnalyzeTab()),
                        quickButton("Open Batch", actions::openBatchTab),
                        quickButton("Open Console", actions::openConsoleTab),
                        quickButton("Run all checks", actions::runAllHealthChecks)));
    }

    /**
     * Builds the current-position summary card.
     *
     * @return position card
     */
    private JComponent buildPositionCard() {
        JPanel body = cardBody();
        // Just the facts that matter at a glance: the current position and its
        // legal-move count, with the actions that act on it. Material, phase,
        // king-safety and the full tag cloud are position-analysis detail that
        // belongs in the Analyze tab, not decorating the operations dashboard.
        body.add(infoRow("FEN", fenValue));
        body.add(infoRow("Side to move", sideValue));
        body.add(infoRow("Ply", plyValue));
        body.add(infoRow("Legal moves", legalValue));
        body.add(Box.createVerticalStrut(Theme.SPACE_SM));
        body.add(actionRow(
                quickButton("Copy FEN", actions::copyCurrentFen),
                quickButton("Analyze", actions::analyze),
                quickButton("Search", actions::builtInSearch)));
        return card("Position", body);
    }

    /**
     * Builds the Engine card.
     *
     * @return card component
     */
    private JComponent buildEngineCard() {
        JPanel body = cardBody();
        body.add(infoRow("Protocol", enginePathValue));
        body.add(infoRow("Mode", engineModeValue));
        body.add(infoRow("Latest", engineSummaryValue));
        body.add(Box.createVerticalStrut(Theme.SPACE_SM));
        body.add(actionRow(
                quickButton("Smoke test", actions::engineSmoke)));
        return card("Engine", body);
    }

    /**
     * Builds the Health card. Three compact status rows; the "Run all checks"
     * action lives once in the dashboard toolbar rather than being repeated here.
     *
     * @return card component
     */
    private JComponent buildHealthCard() {
        JPanel body = cardBody();
        body.add(infoRow("Config validate", healthConfigValue));
        body.add(infoRow("Doctor", healthDoctorValue));
        body.add(infoRow("Engine smoke", healthSmokeValue));
        return card("Health", body);
    }

    /**
     * Builds the Network Runtime diagnostics card.
     *
     * @return card component
     */
    private JComponent buildNetworkRuntimeCard() {
        // Scroll the runtime panel inside its card so a long models/backends list
        // is never clipped when the card is short — it scrolls instead. A modest
        // preferred height keeps the card from demanding the whole window; the
        // data row stretches it taller and the scroll reveals any overflow.
        networkDiagnosticsPanel.setOpaque(false);
        // The scroll sits inside this card, so its viewport must read as CARD,
        // not the default darker PANEL_SOLID, or the models/backends/cache area
        // shows as a darker box inside the lighter card.
        JScrollPane scroll = Ui.scroll(networkDiagnosticsPanel, () -> Theme.CARD);
        scroll.setPreferredSize(new Dimension(0, 240));
        JPanel body = Ui.transparentPanel(new BorderLayout());
        body.add(scroll, BorderLayout.CENTER);
        return card("Network Runtime", body);
    }

    /**
     * Builds the recent-Jobs card.
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
        // Give each column a sensible share so the command and result columns
        // get the room and status/duration stay compact.
        jobTable.getColumnModel().getColumn(JobTableModel.COL_COMMAND)
                .setPreferredWidth(320);
        jobTable.getColumnModel().getColumn(JobTableModel.COL_STATUS)
                .setPreferredWidth(90);
        jobTable.getColumnModel().getColumn(JobTableModel.COL_DURATION)
                .setPreferredWidth(80);
        jobTable.getColumnModel().getColumn(JobTableModel.COL_RESULT)
                .setPreferredWidth(300);

        jobScrollPane = Ui.scroll(jobTable);
        // Pin only the height; width tracks the full-width card cell so the
        // command/result columns get the room instead of a fixed 640px box.
        jobScrollPane.setPreferredSize(new Dimension(0, 168));

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

        // Swap to a centered empty-state when there are no runs, so the
        // full-height jobs card never shows a lonely caption in a tall void.
        jobsBody.setOpaque(false);
        jobsBody.add(list, "list");
        jobsBody.add(Ui.emptyState("No runs yet",
                "Run a command from the Commands or Batch tab to populate this list."), "empty");
        updateJobActionState();
        return card("Recent Jobs", jobsBody);
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
        String fen = session.fen();
        fenValue.setText(fen.isEmpty() ? "—" : fen);
        fenValue.setToolTipText(fen.isEmpty() ? null : fen);
        sideValue.setText(session.whiteToMove() ? "White" : "Black");
        plyValue.setText(session.ply() + " / " + session.lastPly());
        legalValue.setText(Integer.toString(session.legalMoveCount()));

        String protocol = session.engineProtocolPath();
        enginePathValue.setText(protocol.isEmpty() ? "(CLI default)" : protocol);
        enginePathValue.setToolTipText(protocol.isEmpty() ? null : protocol);
        String engineSummary = session.engineSummary();
        boolean pausedLiveEngine = session.liveEngine() && "paused".equalsIgnoreCase(engineSummary);
        engineModeValue.setText(pausedLiveEngine ? "paused" : session.liveEngine() ? "live" : "offline");
        engineSummaryValue.setText(pausedLiveEngine || engineSummary.isEmpty() ? "—" : engineSummary);
        engineSummaryValue.setToolTipText(pausedLiveEngine || engineSummary.isEmpty() ? null : engineSummary);

        HealthSnapshot health = session.health();
        applyHealthValue(healthConfigValue, health.config());
        applyHealthValue(healthDoctorValue, health.doctor());
        applyHealthValue(healthSmokeValue, health.engineSmoke());
        networkDiagnosticsPanel.refresh(networkDiagnosticsProvider, "Dashboard",
                NetworkPanel.runtimeCacheSummary());
    }

    /**
     * Rebuilds the Outputs card body from the artifact index.
     */
    private void refreshArtifacts() {
        artifactList.removeAll();
        List<Path> recent = session.artifacts().recent();
        if (recent.isEmpty()) {
            JLabel empty = value();
            empty.setText("No artifacts yet. Run a command and exported files will appear here.");
            empty.setForeground(Theme.MUTED);
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            artifactList.add(empty);
        } else {
            for (Path path : recent) {
                JLabel row = value();
                row.setText(path.getFileName().toString());
                row.setToolTipText(path.toString());
                row.setAlignmentX(Component.LEFT_ALIGNMENT);
                artifactList.add(row);
            }
        }
        artifactList.revalidate();
        artifactList.repaint();
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
    // Small UI helpers
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

    /**
     * Creates a value label (the right-hand side of an info row).
     *
     * @return styled value label
     */
    private static JLabel value() {
        JLabel label = new JLabel("—");
        label.setFont(Theme.font(12, Font.PLAIN));
        label.setForeground(Theme.TEXT);
        return label;
    }

    /**
     * Sets a health-check value label's text and colours it by state — green for
     * a pass, red for a failure, muted for not-run/running — so a failed check
     * is visually distinct instead of reading as neutral ink. Uses
     * {@link Theme#foreground} so the colour round-trips on a theme toggle.
     *
     * @param label health value label
     * @param check check state
     */
    private static void applyHealthValue(JLabel label, HealthSnapshot.Check check) {
        label.setText(check.label());
        Theme.foreground(label, switch (check) {
            case OK -> Theme.ForegroundRole.SUCCESS;
            case FAILED -> Theme.ForegroundRole.ERROR;
            case RUNNING, UNKNOWN -> Theme.ForegroundRole.MUTED;
        });
    }

    /**
     * Creates a small muted caption label, left-aligned for a {@code BoxLayout}
     * card body — used to title an inline chart.
     *
     * @param text caption text
     * @return styled caption label
     */
    private static JLabel caption(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.font(10, Font.PLAIN));
        Theme.foreground(label, Theme.ForegroundRole.MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
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
        captionLabel.setFont(Theme.font(12, Font.BOLD));
        Theme.foreground(captionLabel, Theme.ForegroundRole.MUTED);
        captionLabel.setPreferredSize(new Dimension(112, Theme.CONTROL_HEIGHT - 8));
        captionLabel.setVerticalAlignment(SwingConstants.TOP);
        row.add(captionLabel, BorderLayout.WEST);
        // Right-align the value so every row reads as a clean spec sheet — the
        // values line up on the card's right edge instead of floating mid-row
        // with a ragged gap after short labels.
        value.setHorizontalAlignment(SwingConstants.RIGHT);
        row.add(value, BorderLayout.CENTER);
        row.setBorder(Theme.pad(Theme.SPACE_XS / 2, 0));
        return row;
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

    /**
     * Creates a compact secondary quick-action button.
     *
     * @param text button text
     * @param action action to run on click
     * @return styled button
     */
    private static JButton quickButton(String text, Runnable action) {
        return Ui.button(text, false, event -> action.run());
    }

    /**
     * Wraps dashboard content in the shared elevated card used across the
     * workbench (Datasets/Network analytics cards): a rounded surface with a
     * low-contrast hairline and a quiet uppercase section eyebrow. Using the
     * one shared primitive — instead of a bespoke hover-lift card — keeps the
     * dashboard, dataset, and network grids reading as a single design system.
     *
     * @param title section title
     * @param body section body
     * @return card component
     */
    private static JComponent card(String title, JComponent body) {
        return Ui.card(title, body);
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
}
