/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package application.gui.workbench.dashboard;

import application.gui.workbench.session.HealthSnapshot;
import application.gui.workbench.session.Job;
import application.gui.workbench.session.JobTableModel;
import application.gui.workbench.session.Session;
import application.gui.workbench.session.SessionListener;
import application.gui.workbench.ui.MiniChart;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import chess.core.Position;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
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
 * position, engine status, batch readiness, recent command jobs, generated
 * artifacts, and environment health, with quick actions that route into the
 * deeper workbench tabs.
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
     * Maximum width of the dashboard content column. Wide workbench windows
     * should still feel like an operational surface, not a narrow report page.
     */
    private static final int CONTENT_MAX_WIDTH = 1440;

    /**
     * Rough character budget for the inline tag preview. Whole tags are added
     * until the next one would exceed this, then the rest collapse into a
     * "+N more" suffix (the full set stays in the tooltip). Breaking on whole
     * tags keeps the label from clipping mid-word at the card edge.
     */
    private static final int INLINE_TAG_BUDGET = 60;

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
     * Current-position card: latest tags.
     */
    private final JLabel tagsValue = value();

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
     * Batch card: FEN-input summary.
     */
    private final JLabel batchValue = value();

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
     * Recent-job action row, hidden while there are no jobs.
     */
    private JComponent jobActionRow;

    /**
     * Recent-job actions that require a selected job row.
     */
    private List<JButton> jobActionButtons = List.of();

    /**
     * Eval-over-plies sparkline for the Current Position card.
     */
    private final MiniChart evalChart = new MiniChart();

    /**
     * Compact material-balance infographic.
     */
    private final MaterialStrip materialStrip = new MaterialStrip();

    /**
     * Game-phase meter.
     */
    private final MetricMeter phaseMeter = new MetricMeter("Phase");

    /**
     * Legal-move mobility meter.
     */
    private final MetricMeter mobilityMeter = new MetricMeter("Mobility");

    /**
     * Side-to-move king safety meter.
     */
    private final MetricMeter kingSafetyMeter = new MetricMeter("King safety");

    /**
     * Pawn-structure meter.
     */
    private final MetricMeter pawnStructureMeter = new MetricMeter("Pawn structure");

    /**
     * Tag cloud for static position tags.
     */
    private final TagCloud tagCloud = new TagCloud();

    /**
     * Health-check ring infographic.
     */
    private final HealthRings healthRings = new HealthRings();

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

        // Top section is two independent masonry columns so a tall card on one
        // side does not leave a gap beside a short card on the other.
        JPanel leftColumn = stackColumn();
        leftColumn.add(buildPositionCard());
        leftColumn.add(Box.createVerticalStrut(Theme.SPACE_SM));
        leftColumn.add(buildBatchCard());
        leftColumn.add(Box.createVerticalGlue());

        JPanel rightColumn = stackColumn();
        rightColumn.add(buildEngineCard());
        rightColumn.add(Box.createVerticalStrut(Theme.SPACE_SM));
        rightColumn.add(buildHealthCard());
        rightColumn.add(Box.createVerticalGlue());

        JPanel topColumns = Ui.transparentPanel(
    new GridLayout(1, 2, Theme.SPACE_SM, 0));
        topColumns.add(leftColumn);
        topColumns.add(rightColumn);

        JPanel grid = Ui.transparentPanel(new GridBagLayout());
        grid.setBorder(Theme.pad(Theme.SPACE_MD));
        GridBagConstraints c = new GridBagConstraints();
        // One full-width column of sections; a trailing filler row soaks up the
        // spare vertical space so sections stay packed at the top.
        c.gridx = 0;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTH;
        c.insets = new Insets(Theme.SPACE_XS, Theme.SPACE_XS,
                Theme.SPACE_XS, Theme.SPACE_XS);

        c.gridy = 0;
        grid.add(topColumns, c);
        c.gridy = 1;
        grid.add(buildJobsCard(), c);
        c.gridy = 2;
        grid.add(buildOutputsCard(), c);

        c.gridy = 3;
        c.weighty = 1.0;
        c.fill = GridBagConstraints.BOTH;
        grid.add(Ui.transparentPanel(null), c);

        // Cap the content column and centre it: on a wide monitor the cards
        // would otherwise stretch edge-to-edge and look sparse.
        grid.setMaximumSize(new Dimension(CONTENT_MAX_WIDTH, Integer.MAX_VALUE));
        grid.setPreferredSize(new Dimension(CONTENT_MAX_WIDTH,
                grid.getPreferredSize().height));
        JPanel centered = Ui.transparentPanel(new GridBagLayout());
        GridBagConstraints centerConstraints = new GridBagConstraints();
        centerConstraints.gridx = 0;
        centerConstraints.gridy = 0;
        centerConstraints.weightx = 1.0;
        centerConstraints.weighty = 1.0;
        centerConstraints.anchor = GridBagConstraints.NORTH;
        centerConstraints.fill = GridBagConstraints.VERTICAL;
        centered.add(grid, centerConstraints);

        add(Ui.scroll(centered), BorderLayout.CENTER);

        session.addListener(this);
        session.artifacts().addListener(() -> SwingUtilities.invokeLater(this::refreshArtifacts));
        refreshArtifacts();
        render();
    }

    // ------------------------------------------------------------------
    // Cards
    // ------------------------------------------------------------------

    /**
     * Builds the Current Position card.
     *
     * @return card component
     */
    private JComponent buildPositionCard() {
        JPanel body = cardBody();
        body.add(infoRow("FEN", fenValue));
        body.add(infoRow("Side to move", sideValue));
        body.add(infoRow("Ply", plyValue));
        body.add(infoRow("Legal moves", legalValue));
        body.add(Box.createVerticalStrut(Theme.SPACE_SM));
        JPanel metrics = cardBody();
        materialStrip.setAlignmentX(Component.LEFT_ALIGNMENT);
        metrics.add(materialStrip);
        metrics.add(Box.createVerticalStrut(Theme.SPACE_SM));
        metrics.add(metricGrid(phaseMeter, mobilityMeter, kingSafetyMeter, pawnStructureMeter));
        body.add(Ui.collapsible("Metrics", metrics, true));
        JPanel context = cardBody();
        context.add(caption("Position tags"));
        context.add(Box.createVerticalStrut(Theme.SPACE_XS));
        tagCloud.setAlignmentX(Component.LEFT_ALIGNMENT);
        context.add(tagCloud);
        context.add(Box.createVerticalStrut(Theme.SPACE_SM));
        evalChart.setEmptyText("No eval yet.");
        evalChart.setAlignmentX(Component.LEFT_ALIGNMENT);
        context.add(caption("Eval · White"));
        context.add(Box.createVerticalStrut(Theme.SPACE_XS));
        context.add(evalChart);
        body.add(Ui.collapsible("Tags", context, true));
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
     * Builds the Batch card.
     *
     * @return card component
     */
    private JComponent buildBatchCard() {
        JPanel body = cardBody();
        body.add(infoRow("Input", batchValue));
        body.add(Box.createVerticalStrut(Theme.SPACE_SM));
        body.add(actionRow(
                quickButton("Run batch", actions::runBatch),
                quickButton("Open Batch", actions::openBatchTab)));
        return card("Batch", body);
    }

    /**
     * Builds the Health card.
     *
     * @return card component
     */
    private JComponent buildHealthCard() {
        JPanel body = cardBody();
        healthRings.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(healthRings);
        body.add(Box.createVerticalStrut(Theme.SPACE_SM));
        body.add(infoRow("Config validate", healthConfigValue));
        body.add(infoRow("Doctor", healthDoctorValue));
        body.add(infoRow("Engine smoke", healthSmokeValue));
        body.add(Box.createVerticalStrut(Theme.SPACE_SM));
        body.add(actionRow(
                quickButton("Run all checks", actions::runAllHealthChecks)));
    return card("Health", body);
    }

    /**
     * Builds the recent-Jobs card.
     *
     * @return card component
     */
    private JComponent buildJobsCard() {
        jobTable.setFillsViewportHeight(true);
        jobTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        jobTable.setRowHeight(Theme.CONTROL_HEIGHT - 4);
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
        jobScrollPane.setPreferredSize(new Dimension(640, 150));

        JPanel body = cardBody();
        body.add(jobsCaption);
        body.add(Box.createVerticalStrut(Theme.SPACE_XS));
        body.add(jobScrollPane);
        body.add(Box.createVerticalStrut(Theme.SPACE_SM));
        JButton retryButton = quickButton("Retry", () -> withSelectedJob(actions::retryJob));
        JButton copyButton = quickButton("Copy command", () -> withSelectedJob(actions::copyJobCommand));
        JButton logButton = quickButton("Open log", () -> withSelectedJob(actions::openJobLog));
        JButton manifestButton = quickButton("Open manifest", () -> withSelectedJob(actions::openJobManifest));
        jobActionButtons = List.of(retryButton, copyButton, logButton, manifestButton);
        jobTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                updateJobActionState();
            }
        });
        jobModel.addTableModelListener(event -> updateJobActionState());
        updateJobActionState();
        jobActionRow = actionRow(retryButton, copyButton, logButton, manifestButton);
        body.add(jobActionRow);
        updateJobActionState();
    return card("Recent Jobs", body);
    }

    /**
     * Builds the Outputs card.
     *
     * @return card component
     */
    private JComponent buildOutputsCard() {
        artifactList.setLayout(new BoxLayout(artifactList, BoxLayout.Y_AXIS));
        artifactList.setOpaque(false);
        JPanel body = cardBody();
        body.add(artifactList);
    return card("Outputs", body);
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
        List<String> tags = session.tags();
        if (tags.isEmpty()) {
            tagsValue.setText("—");
            tagsValue.setToolTipText(null);
        } else {
            tagsValue.setText(inlineTags(tags));
            tagsValue.setToolTipText("<html>" + String.join("<br>", tags) + "</html>");
        }
        tagCloud.setTags(tags);
        applyPositionInfographics(PositionStats.from(fen, session.legalMoveCount()));

        String protocol = session.engineProtocolPath();
        enginePathValue.setText(protocol.isEmpty() ? "(CLI default)" : protocol);
        enginePathValue.setToolTipText(protocol.isEmpty() ? null : protocol);
        String engineSummary = session.engineSummary();
        boolean pausedLiveEngine = session.liveEngine() && "paused".equalsIgnoreCase(engineSummary);
        engineModeValue.setText(pausedLiveEngine ? "paused" : session.liveEngine() ? "live" : "offline");
        engineSummaryValue.setText(pausedLiveEngine || engineSummary.isEmpty() ? "—" : engineSummary);
        engineSummaryValue.setToolTipText(pausedLiveEngine || engineSummary.isEmpty() ? null : engineSummary);

        String batch = session.batchSummary();
        batchValue.setText(batch.isEmpty() ? "no FEN rows" : batch);
        batchValue.setToolTipText(batch.isEmpty() ? null : batch);

        HealthSnapshot health = session.health();
        healthConfigValue.setText(health.config().label());
        healthDoctorValue.setText(health.doctor().label());
        healthSmokeValue.setText(health.engineSmoke().label());
        healthRings.setChecks(health.config(), health.doctor(), health.engineSmoke());

        int[] evalCentipawns = session.evalHistoryCentipawns();
        float[] evalPawns = new float[evalCentipawns.length];
        for (int i = 0; i < evalCentipawns.length; i++) {
            evalPawns[i] = evalCentipawns[i] / 100f;
        }
        evalChart.setLine(evalPawns);
    }

    /**
     * Applies computed position stats to the infographic components.
     *
     * @param stats stats snapshot
     */
    private void applyPositionInfographics(PositionStats stats) {
        materialStrip.setStats(stats);
        phaseMeter.setValue(stats.phaseValue(), stats.phaseLabel(),
                colorForPhase(stats.phaseValue()));
        mobilityMeter.setValue(stats.mobilityValue(), stats.mobilityLabel(),
                colorForMobility(stats.mobilityValue()));
        kingSafetyMeter.setValue(stats.kingSafetyValue(), stats.kingSafetyLabel(),
                colorForSafety(stats.kingSafetyValue()));
        pawnStructureMeter.setValue(stats.pawnStructureValue(), stats.pawnStructureLabel(),
                colorForSafety(stats.pawnStructureValue()));
    }

    /**
     * Rebuilds the Outputs card body from the artifact index.
     */
    private void refreshArtifacts() {
        artifactList.removeAll();
        List<Path> recent = session.artifacts().recent();
        if (recent.isEmpty()) {
            JLabel empty = value();
            empty.setText("No artifacts yet.");
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
        jobsCaption.setText(hasRows
                ? "Newest first · select a row for actions"
                : "No runs yet.");
        if (jobScrollPane != null) {
            jobScrollPane.setVisible(hasRows);
        }
        if (jobActionRow != null) {
            jobActionRow.setVisible(hasRows);
        }
        for (JButton button : jobActionButtons) {
            button.setEnabled(hasSelection);
        }
    }

    /**
     * Builds the inline tag preview: as many whole tags as fit
     * {@link #INLINE_TAG_BUDGET}, then a "+N more" suffix for the remainder.
     * Always at least one tag is shown.
     *
     * @param tags full tag list (non-empty)
     * @return inline preview string
     */
    private static String inlineTags(List<String> tags) {
        StringBuilder builder = new StringBuilder();
        int shown = 0;
        for (String tag : tags) {
            int added = (builder.length() == 0 ? 0 : 2) + tag.length();
            if (shown > 0 && builder.length() + added > INLINE_TAG_BUDGET) {
                break;
            }
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(tag);
            shown++;
        }
        if (shown < tags.size()) {
            builder.append("  +").append(tags.size() - shown).append(" more");
        }
        return builder.toString();
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
     * Builds a two-column metric grid.
     *
     * @param meters meters to add
     * @return grid component
     */
    private static JComponent metricGrid(MetricMeter... meters) {
        JPanel panel = Ui.transparentPanel(new GridLayout(2, 2,
                Theme.SPACE_SM, Theme.SPACE_SM));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        Dimension size = new Dimension(260, 2 * 54 + Theme.SPACE_SM);
        panel.setPreferredSize(size);
        panel.setMinimumSize(size);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, size.height));
        for (MetricMeter meter : meters) {
            panel.add(meter);
        }
        return panel;
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
     * Picks a phase colour.
     *
     * @param value phase value
     * @return colour
     */
    private static Color colorForPhase(float value) {
        if (value < 0.25f) {
            return Theme.STATUS_INFO_BORDER;
        }
        if (value < 0.68f) {
            return Theme.ACCENT;
        }
        return Theme.STATUS_WARNING_BORDER;
    }

    /**
     * Picks a mobility colour.
     *
     * @param value normalized mobility
     * @return colour
     */
    private static Color colorForMobility(float value) {
        if (value < 0.25f) {
            return Theme.STATUS_WARNING_BORDER;
        }
        if (value > 0.75f) {
            return Theme.STATUS_SUCCESS_BORDER;
        }
        return Theme.ACCENT;
    }

    /**
     * Picks a quality/safety colour.
     *
     * @param value normalized quality
     * @return colour
     */
    private static Color colorForSafety(float value) {
        if (value < 0.36f) {
            return Theme.STATUS_ERROR_BORDER;
        }
        if (value < 0.66f) {
            return Theme.STATUS_WARNING_BORDER;
        }
        return Theme.STATUS_SUCCESS_BORDER;
    }

    /**
     * Clamps a value into {@code 0..1}.
     *
     * @param value raw value
     * @return clamped value
     */
    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    /**
     * Wraps dashboard content as a flat section. The dashboard still groups
     * related facts, but it avoids boxed card chrome so the page reads like
     * adjacent workbench regions.
     *
     * @param title section title
     * @param body section body
     * @return section component
     */
    private static JComponent card(String title, JComponent body) {
        JPanel cardPanel = new JPanel(new BorderLayout(0, Theme.SPACE_SM));
        cardPanel.setOpaque(true);
        cardPanel.setBackground(Theme.PANEL_SOLID);
        cardPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.LINE),
                Theme.pad(Theme.SPACE_MD, Theme.SPACE_SM,
                        Theme.SPACE_MD, Theme.SPACE_SM)));
        JLabel header = new JLabel(title);
        header.setFont(Theme.font(13, Font.BOLD));
        header.setForeground(Theme.TEXT);
        cardPanel.add(header, BorderLayout.NORTH);
        cardPanel.add(body, BorderLayout.CENTER);
        // Cap the height so a card keeps its natural size inside a vertical
        // BoxLayout column instead of stretching to share spare space.
        cardPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        cardPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                cardPanel.getPreferredSize().height));
        return cardPanel;
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
     * Creates a vertical stacking column for the masonry-style top section —
     * a transparent {@code BoxLayout} panel that packs its cards from the top.
     *
     * @return empty stacking column
     */
    private static JPanel stackColumn() {
        JPanel column = Ui.transparentPanel(null);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setAlignmentY(Component.TOP_ALIGNMENT);
        return column;
    }

    /**
     * Computed position stats for dashboard infographics.
     *
     * @param valid true when a FEN could be parsed
     * @param whiteMaterial white material in centipawns
     * @param blackMaterial black material in centipawns
     * @param whitePieces white piece count
     * @param blackPieces black piece count
     * @param phaseValue normalized game phase
     * @param phaseLabel phase label
     * @param mobilityValue normalized legal-move mobility
     * @param mobilityLabel mobility label
     * @param kingSafetyValue normalized side-to-move king safety
     * @param kingSafetyLabel king-safety label
     * @param pawnStructureValue normalized pawn-structure score
     * @param pawnStructureLabel pawn-structure label
     */
    private record PositionStats(
            boolean valid,
            int whiteMaterial,
            int blackMaterial,
            int whitePieces,
            int blackPieces,
            float phaseValue,
            String phaseLabel,
            float mobilityValue,
            String mobilityLabel,
            float kingSafetyValue,
            String kingSafetyLabel,
            float pawnStructureValue,
            String pawnStructureLabel) {

        /**
         * Starting non-king material for both sides, in centipawns.
         */
        private static final int START_TOTAL_MATERIAL = 7800;

        /**
         * Creates stats from a FEN and legal-move count.
         *
         * @param fen FEN
         * @param legalMoveCount legal move count
         * @return stats
         */
        private static PositionStats from(String fen, int legalMoveCount) {
            if (fen == null || fen.isBlank()) {
    return empty();
            }
            try {
                Position position = new Position(fen);
                int whiteMaterial = position.countWhiteMaterial();
                int blackMaterial = position.countBlackMaterial();
                int totalMaterial = Math.max(0, whiteMaterial + blackMaterial);
                float phase = clamp01(1f - totalMaterial / (float) START_TOTAL_MATERIAL);
                int passed = Long.bitCount(position.passedPawns(true))
                        + Long.bitCount(position.passedPawns(false));
                int weak = Long.bitCount(position.doubledPawns(true))
                        + Long.bitCount(position.doubledPawns(false))
                        + Long.bitCount(position.isolatedPawns(true))
                        + Long.bitCount(position.isolatedPawns(false));
                float pawnStructure = clamp01(0.55f + passed * 0.08f - weak * 0.045f);
                boolean side = position.isWhiteToMove();
                float kingSafety = kingSafety(position, side);
    return new PositionStats(
                        true,
                        whiteMaterial,
                        blackMaterial,
                        position.countWhitePieces(),
                        position.countBlackPieces(),
                        phase,
                        phaseLabel(phase),
                        clamp01(legalMoveCount / 60f),
                        mobilityLabel(legalMoveCount),
                        kingSafety,
                        (side ? "White " : "Black ") + kingSafetyLabel(kingSafety, position.inCheck(side)),
                        pawnStructure,
                        passed + " passed / " + weak + " weak");
            } catch (IllegalArgumentException ex) {
    return empty();
            }
        }

        /**
         * Empty stats fallback.
         *
         * @return empty stats
         */
        private static PositionStats empty() {
    return new PositionStats(false, 0, 0, 0, 0,
                    0f, "n/a", 0f, "n/a", 0f, "n/a", 0f, "n/a");
        }

        /**
         * Computes side-to-move king safety from attacks around the king.
         *
         * @param position position
         * @param white side to inspect
         * @return normalized safety
         */
        private static float kingSafety(Position position, boolean white) {
            int king = position.kingSquare(white);
            if (king < 0) {
                return 0.5f;
            }
            int attackers = position.countAttackers(!white, king);
            int attackedRing = 0;
            int ring = 0;
            int file = king & 7;
            int rank = king >>> 3;
            for (int dr = -1; dr <= 1; dr++) {
                for (int df = -1; df <= 1; df++) {
                    if (df == 0 && dr == 0) {
                        continue;
                    }
                    int f = file + df;
                    int r = rank + dr;
                    if (f < 0 || f > 7 || r < 0 || r > 7) {
                        continue;
                    }
                    ring++;
                    if (position.isSquareAttacked((r << 3) | f, !white)) {
                        attackedRing++;
                    }
                }
            }
            float ringDanger = attackedRing / (float) Math.max(1, ring);
            float danger = attackers * 0.28f + ringDanger * 0.58f
                    + (position.inCheck(white) ? 0.35f : 0f);
    return clamp01(1f - danger);
        }

        /**
         * Labels game phase.
         *
         * @param phase normalized phase
         * @return label
         */
        private static String phaseLabel(float phase) {
            if (phase < 0.25f) {
                return "Opening";
            }
            if (phase < 0.68f) {
                return "Middlegame";
            }
            return "Endgame";
        }

        /**
         * Labels mobility.
         *
         * @param legal legal move count
         * @return label
         */
        private static String mobilityLabel(int legal) {
            if (legal < 12) {
                return legal + " tight";
            }
            if (legal < 32) {
                return legal + " balanced";
            }
            return legal + " mobile";
        }

        /**
         * Labels king safety.
         *
         * @param safety normalized safety
         * @param inCheck true when checked
         * @return label
         */
        private static String kingSafetyLabel(float safety, boolean inCheck) {
            if (inCheck) {
                return "in check";
            }
            if (safety < 0.36f) {
                return "exposed";
            }
            if (safety < 0.66f) {
                return "watched";
            }
            return "steady";
        }
    }

    /**
     * Material-balance strip.
     */
    private static final class MaterialStrip extends JComponent {

        /**
         * Serialization identifier for Swing component compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Position statistics currently displayed by the strip.
         */
        private PositionStats stats = PositionStats.empty();

        /**
         * Sets stats.
         *
         * @param value stats
         */
    void setStats(PositionStats value) {
            stats = value == null ? PositionStats.empty() : value;
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
    return new Dimension(260, 58);
        }

        @Override
        public Dimension getMinimumSize() {
    return getPreferredSize();
        }

        @Override
        public Dimension getMaximumSize() {
    return new Dimension(Integer.MAX_VALUE, 58);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                installQuality(g);
                int w = getWidth();
                int h = getHeight();
                paintPanel(g, 0, 0, w, h);
                int balance = stats.whiteMaterial() - stats.blackMaterial();
                String title = stats.valid()
                        ? "Material " + signedPawns(balance)
                        : "Material n/a";
                g.setFont(Theme.font(11, Font.BOLD));
                g.setColor(Theme.TEXT);
                g.drawString(title, 10, 18);
                g.setFont(Theme.font(10, Font.PLAIN));
                g.setColor(Theme.MUTED);
                String detail = "White " + stats.whitePieces() + " pcs / Black "
                        + stats.blackPieces() + " pcs";
                FontMetrics detailMetrics = g.getFontMetrics();
                g.drawString(Ui.elide(detail, detailMetrics, Math.max(0, w - 20)),
                        10, h - 10);

                int x = 10;
                int y = 28;
                int barW = Math.max(1, w - 20);
                int barH = 10;
                int total = Math.max(1, stats.whiteMaterial() + stats.blackMaterial());
                int whiteW = Math.round(barW * stats.whiteMaterial() / (float) total);
                g.setColor(Theme.withAlpha(Theme.ACCENT, 190));
                g.fillRoundRect(x, y, whiteW, barH, 5, 5);
                g.setColor(Theme.withAlpha(Theme.STATUS_WARNING_BORDER, 190));
                g.fillRoundRect(x + whiteW, y, Math.max(0, barW - whiteW), barH, 5, 5);
                g.setColor(Theme.LINE);
                g.drawRoundRect(x, y, barW, barH, 5, 5);
            } finally {
                g.dispose();
            }
        }
    }

    /**
     * Compact labelled meter.
     */
    private static final class MetricMeter extends JComponent {

        /**
         * Serialization identifier for Swing component compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Meter title.
         */
        private final String title;

        /**
         * Normalized meter value.
         */
        private float value;

        /**
         * Current value label.
         */
        private String label = "n/a";

        /**
         * Accent color used for the meter fill.
         */
        private Color accent = Theme.ACCENT;

        /**
         * Creates a metric meter.
         *
         * @param title meter title
         */
        MetricMeter(String title) {
            this.title = title;
            setOpaque(false);
        }

        /**
         * Updates meter state.
         *
         * @param newValue normalized value
         * @param newLabel label
         * @param color accent color
         */
    void setValue(float newValue, String newLabel, Color color) {
            value = clamp01(newValue);
            label = newLabel == null ? "" : newLabel;
            accent = color == null ? Theme.ACCENT : color;
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
    return new Dimension(150, 54);
        }

        @Override
        public Dimension getMinimumSize() {
    return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                installQuality(g);
                int w = getWidth();
                int h = getHeight();
                paintPanel(g, 0, 0, w, h);
                g.setFont(Theme.font(10, Font.BOLD));
                g.setColor(Theme.MUTED);
                g.drawString(title, 10, 17);
                g.setFont(Theme.font(11, Font.PLAIN));
                g.setColor(Theme.TEXT);
                FontMetrics fm = g.getFontMetrics();
                String visible = Ui.elide(label, fm, Math.max(0, w - 20));
                g.drawString(visible, 10, 32);
                int x = 10;
                int y = h - 14;
                int barW = Math.max(1, w - 20);
                int fillW = Math.round(barW * value);
                g.setColor(Theme.ELEVATED_SOLID);
                g.fillRoundRect(x, y, barW, 6, 4, 4);
                g.setColor(Theme.withAlpha(accent, 210));
                g.fillRoundRect(x, y, fillW, 6, 4, 4);
            } finally {
                g.dispose();
            }
        }
    }

    /**
     * Small static-tag chip cloud.
     */
    private static final class TagCloud extends JComponent {

        /**
         * Serialization identifier for Swing component compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Tags currently shown in the cloud.
         */
        private List<String> tags = List.of();

        /**
         * Sets tags.
         *
         * @param values tag values
         */
    void setTags(List<String> values) {
            tags = values == null ? List.of() : List.copyOf(values);
            setToolTipText(tags.isEmpty() ? null : "<html>" + htmlTags(tags) + "</html>");
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
    return new Dimension(260, 62);
        }

        @Override
        public Dimension getMinimumSize() {
    return getPreferredSize();
        }

        @Override
        public Dimension getMaximumSize() {
    return new Dimension(Integer.MAX_VALUE, 62);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                installQuality(g);
                paintPanel(g, 0, 0, getWidth(), getHeight());
                if (tags.isEmpty()) {
                    g.setColor(Theme.MUTED);
                    g.setFont(Theme.font(10, Font.PLAIN));
                    g.drawString("No tags yet", 10, 22);
                    return;
                }
                g.setFont(Theme.font(10, Font.PLAIN));
                FontMetrics fm = g.getFontMetrics();
                int x = 8;
                int y = 8;
                int rowH = 21;
                int maxY = getHeight() - rowH;
                int shown = 0;
                for (String tag : tags) {
                    if (shown >= 9) {
                        break;
                    }
                    String visible = compactTag(tag);
                    int chipW = Math.min(getWidth() - 16, fm.stringWidth(visible) + 16);
                    if (x + chipW > getWidth() - 8) {
                        x = 8;
                        y += rowH;
                    }
                    if (y > maxY) {
                        break;
                    }
                    Color fill = shown % 3 == 0
                            ? Theme.STATUS_INFO_BG
                            : Theme.ELEVATED_SOLID;
                    g.setColor(fill);
                    g.fillRoundRect(x, y, chipW, 17, 8, 8);
                    g.setColor(Theme.LINE);
                    g.drawRoundRect(x, y, chipW, 17, 8, 8);
                    g.setColor(Theme.TEXT);
                    g.drawString(Ui.elide(visible, fm, chipW - 10), x + 8, y + 12);
                    x += chipW + 5;
                    shown++;
                }
                if (shown < tags.size()) {
                    g.setColor(Theme.MUTED);
                    g.drawString("+" + (tags.size() - shown) + " more", x + 2,
                            Math.min(getHeight() - 10, y + 12));
                }
            } finally {
                g.dispose();
            }
        }

        /**
         * Compacts a tag for a chip.
         *
         * @param tag raw tag
         * @return compact tag
         */
        private static String compactTag(String tag) {
            if (tag == null) {
                return "";
            }
            String out = tag.trim();
            int colon = out.indexOf(':');
            if (colon >= 0 && colon + 1 < out.length()) {
                out = out.substring(colon + 1).trim();
            }
            out = out.replace('_', ' ')
                    .replace(" side=", " ")
                    .replace(" square=", " ")
                    .replace(" file=", " ")
                    .replace('=', ' ')
                    .replaceAll("\\s+", " ")
                    .trim();
            return out.length() > 24 ? out.substring(0, 21) + "..." : out;
        }

        /**
         * Escapes a tag list for a simple Swing HTML tooltip.
         *
         * @param values raw tags
         * @return escaped tag lines joined with breaks
         */
        private static String htmlTags(List<String> values) {
            StringBuilder builder = new StringBuilder();
            for (String value : values) {
                if (builder.length() > 0) {
                    builder.append("<br>");
                }
                builder.append(escapeHtml(value));
            }
            return builder.toString();
        }

        /**
         * Escapes text for Swing HTML labels.
         *
         * @param text raw text
         * @return escaped text
         */
        private static String escapeHtml(String text) {
            return text == null ? "" : text.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;");
        }
    }

    /**
     * Three-ring health infographic.
     */
    private static final class HealthRings extends JComponent {

        /**
         * Serialization identifier for Swing component compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Configuration validation state.
         */
        private HealthSnapshot.Check config = HealthSnapshot.Check.UNKNOWN;

        /**
         * Doctor command state.
         */
        private HealthSnapshot.Check doctor = HealthSnapshot.Check.UNKNOWN;

        /**
         * Engine smoke-test state.
         */
        private HealthSnapshot.Check smoke = HealthSnapshot.Check.UNKNOWN;

        /**
         * Sets health checks.
         *
         * @param configCheck config check
         * @param doctorCheck doctor check
         * @param smokeCheck engine smoke check
         */
    void setChecks(HealthSnapshot.Check configCheck,
                HealthSnapshot.Check doctorCheck,
                HealthSnapshot.Check smokeCheck) {
            config = configCheck == null ? HealthSnapshot.Check.UNKNOWN : configCheck;
            doctor = doctorCheck == null ? HealthSnapshot.Check.UNKNOWN : doctorCheck;
            smoke = smokeCheck == null ? HealthSnapshot.Check.UNKNOWN : smokeCheck;
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
    return new Dimension(260, 74);
        }

        @Override
        public Dimension getMaximumSize() {
    return new Dimension(Integer.MAX_VALUE, 74);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                installQuality(g);
                paintPanel(g, 0, 0, getWidth(), getHeight());
                int gap = Math.max(12, (getWidth() - 3 * 54) / 4);
                int x = gap;
                paintRing(g, x, 10, "Config", config);
                paintRing(g, x + 54 + gap, 10, "Doctor", doctor);
                paintRing(g, x + 2 * (54 + gap), 10, "Engine", smoke);
            } finally {
                g.dispose();
            }
        }

        /**
         * Paints one check ring.
         *
         * @param g graphics
         * @param x x
         * @param y y
         * @param label label
         * @param check check state
         */
        private static void paintRing(Graphics2D g, int x, int y, String label,
                HealthSnapshot.Check check) {
            int d = 34;
            g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(Theme.LINE);
            g.drawOval(x + 10, y, d, d);
            g.setColor(checkColor(check));
            int arc = switch (check) {
                case OK, FAILED -> 360;
                case RUNNING -> 260;
                case UNKNOWN -> 90;
            };
            g.drawArc(x + 10, y, d, d, 90, -arc);
            g.setStroke(new BasicStroke(1f));
            g.setFont(Theme.font(9, Font.BOLD));
            g.setColor(Theme.TEXT);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(checkGlyph(check), x + 10 + (d - fm.stringWidth(checkGlyph(check))) / 2,
                    y + 22);
            g.setFont(Theme.font(10, Font.PLAIN));
            g.setColor(Theme.MUTED);
            fm = g.getFontMetrics();
            g.drawString(label, x + (54 - fm.stringWidth(label)) / 2, y + 52);
        }

        /**
         * Returns the check ring colour.
         *
         * @param check check
         * @return colour
         */
        private static Color checkColor(HealthSnapshot.Check check) {
    return switch (check) {
                case OK -> Theme.STATUS_SUCCESS_BORDER;
                case FAILED -> Theme.STATUS_ERROR_BORDER;
                case RUNNING -> Theme.STATUS_WARNING_BORDER;
                case UNKNOWN -> Theme.STATUS_INFO_BORDER;
            };
        }

        /**
         * Returns the center glyph.
         *
         * @param check check
         * @return glyph
         */
        private static String checkGlyph(HealthSnapshot.Check check) {
    return switch (check) {
                case OK -> "OK";
                case FAILED -> "!";
                case RUNNING -> "...";
                case UNKNOWN -> "?";
            };
        }
    }

    /**
     * Installs antialiasing.
     *
     * @param g graphics
     */
    private static void installQuality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    /**
     * Paints the shared infographic panel background.
     *
     * @param g graphics
     * @param x x
     * @param y y
     * @param w width
     * @param h height
     */
    private static void paintPanel(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(Theme.ELEVATED_SOLID);
        g.fillRoundRect(x, y, Math.max(0, w - 1), Math.max(0, h - 1),
                Theme.RADIUS, Theme.RADIUS);
        g.setColor(Theme.LINE);
        g.drawRoundRect(x, y, Math.max(0, w - 1), Math.max(0, h - 1),
                Theme.RADIUS, Theme.RADIUS);
    }

    /**
     * Formats a centipawn balance as pawns.
     *
     * @param centipawns centipawn balance
     * @return signed pawn value
     */
    private static String signedPawns(int centipawns) {
        if (centipawns == 0) {
            return "0.0";
        }
        return String.format(Locale.ROOT, "%+.1f", centipawns / 100.0);
    }
}
