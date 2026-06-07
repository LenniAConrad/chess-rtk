package application.gui.workbench.dataset;

import static application.gui.workbench.ui.Ui.setColumnWidth;
import static application.gui.workbench.ui.Ui.button;
import static application.gui.workbench.ui.Ui.card;
import static application.gui.workbench.ui.Ui.caption;
import static application.gui.workbench.ui.Ui.changeListener;
import static application.gui.workbench.ui.Ui.fillViewport;
import static application.gui.workbench.ui.Ui.label;
import static application.gui.workbench.ui.Ui.placeholder;
import static application.gui.workbench.ui.Ui.scroll;
import static application.gui.workbench.ui.Ui.styleFields;
import static application.gui.workbench.ui.Ui.transparentPanel;

import application.gui.workbench.layout.SplitPaneStyler;
import application.gui.workbench.board.BoardPanel;
import application.gui.workbench.ui.FileDialogs;
import application.gui.workbench.ui.HoldButton;
import application.gui.workbench.ui.Spinner;
import application.gui.workbench.ui.StatusBadge;
import application.gui.workbench.ui.SurfacePanel;
import application.gui.workbench.ui.WrappingFlowLayout;
import application.gui.workbench.ui.Theme;
import application.gui.workbench.ui.Ui;
import application.gui.workbench.ui.WorkspaceHeader;
import chess.core.Move;
import chess.core.Position;
import chess.core.Setup;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.RowFilter;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;

/**
 * Workbench panel dedicated to dataset inspection and quality analysis.
 */
public final class DatasetPanel extends JPanel {

    /**
     * Serialization identifier for Swing component compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Default row scan limit.
     */
    private static final int DEFAULT_ROW_LIMIT = 50_000;

    /**
     * Minimum row scan limit.
     */
    private static final int MIN_ROW_LIMIT = 100;

    /**
     * Maximum row scan limit exposed by the UI.
     */
    private static final int MAX_ROW_LIMIT = 1_000_000;

    /**
     * Standard row height for dataset tables.
     */
    private static final int TABLE_ROW_HEIGHT = Theme.TABLE_ROW_HEIGHT;

    /**
     * Labels used by the material histogram.
     */
    private static final String[] MATERIAL_LABELS = {
        "0-999 cp", "1k cp", "2k cp", "3k cp", "4k cp", "5k cp", "6k cp", "7k+ cp"
    };

    /**
     * Labels used by the evaluation histogram.
     */
    private static final String[] EVAL_LABELS = {
        "< -900", "-600", "-300", "equal", "+300", "+600", "> +900"
    };

    /**
     * Source path text field.
     */
    private final JTextField sourceField = new JTextField();

    /**
     * Row scan limit control.
     */
    private final JTextField rowLimitField = new JTextField(formatRowLimit(DEFAULT_ROW_LIMIT));

    /**
     * Analyze action button.
     */
    private final JButton analyzeButton = button("Analyze", true, event -> analyzeCurrentSource());

    /**
     * Stop action button.
     */
    private final HoldButton stopButton = new HoldButton("Stop", this::cancelAnalysis, true);

    /**
     * Copy report action button.
     */
    private final JButton copyReportButton = button("Copy Report", false, event -> copyReport());

    /**
     * Status label.
     */
    private final JLabel statusLabel = caption("No dataset loaded");

    /**
     * Dataset verdict badge shown after analysis.
     */
    private final StatusBadge verdictBadge = new StatusBadge();

    /**
     * Shared workspace header for the Datasets surface.
     */
    private final WorkspaceHeader workspaceHeader = new WorkspaceHeader("Datasets", "", null);

    /**
     * Circular activity indicator shown while scanning.
     */
    private final Spinner spinner = new Spinner();

    /**
     * Callback that opens a FEN in the shared Board tab, or {@code null} when
     * this panel has no navigation context.
     */
    private final transient Consumer<String> openFenInBoard;

    /**
     * Callback that opens a FEN in a new detached Board tab (its own board, so
     * the shared analysis position is not overwritten), or {@code null}.
     */
    private final transient Consumer<String> openFenInNewBoard;

    /**
     * Larger spinner shown on the body's loading card during a scan.
     */
    private final Spinner loadingSpinner = new Spinner(44);

    /**
     * Loading-card headline; carries the dataset currently being scanned.
     */
    private final JLabel loadingTitle = new JLabel("Scanning…");

    /**
     * Loading-card hint line.
     */
    private final JLabel loadingHint =
            new JLabel("Profiling every position — this can take a moment. No need to browse again.");

    /**
     * Loading-card scan details.
     */
    private final JLabel loadingFileValue = detailValueLabel(),
            loadingPhaseValue = detailValueLabel(),
            loadingRowsValue = detailValueLabel(),
            loadingValidValue = detailValueLabel(),
            loadingDuplicateValue = detailValueLabel(),
            loadingElapsedValue = detailValueLabel();

    /**
     * Timer used only to keep elapsed scan time visible.
     */
    private final javax.swing.Timer loadingElapsedTimer =
            new javax.swing.Timer(250, event -> updateLoadingElapsed());

    /**
     * Wall-clock time when the current scan started.
     */
    private long loadingStartedAt;

    /**
     * Whether a scan is currently running.
     */
    private boolean busy;

    /**
     * Source-size metric tile.
     */
    private final MetricTile filesMetric = new MetricTile("source", DatasetChart.Role.NEUTRAL);

    /**
     * Row-count metric tile.
     */
    private final MetricTile rowsMetric = new MetricTile("rows", DatasetChart.Role.NEUTRAL);

    /**
     * Validity metric tile.
     */
    private final MetricTile validMetric = new MetricTile("validity", DatasetChart.Role.NEUTRAL);

    /**
     * Duplicate-position metric tile.
     */
    private final MetricTile duplicateMetric = new MetricTile("uniqueness", DatasetChart.Role.NEUTRAL);

    /**
     * Tag-coverage metric tile.
     */
    private final MetricTile tagsMetric = new MetricTile("tags", DatasetChart.Role.NEUTRAL);

    /**
     * Evaluation-coverage metric tile.
     */
    private final MetricTile evalMetric = new MetricTile("scores", DatasetChart.Role.NEUTRAL);

    /**
     * Average-material metric tile.
     */
    private final MetricTile materialMetric = new MetricTile("material", DatasetChart.Role.NEUTRAL);

    /**
     * Quality insight label.
     */
    private final JLabel qualityInsight = insightLabel();

    /**
     * Coverage insight label.
     */
    private final JLabel coverageInsight = insightLabel();

    /**
     * Side-balance insight label.
     */
    private final JLabel balanceInsight = insightLabel();

    /**
     * Material insight label.
     */
    private final JLabel materialInsight = insightLabel();

    /**
     * Row-health composition donut (unique / duplicate / invalid).
     */
    private final DonutChart qualityChart = new DonutChart();

    /**
     * Side-to-move composition donut (white / black).
     */
    private final DonutChart sideChart = new DonutChart();

    /**
     * Position-type composition donut (quiet / check / mate / stalemate).
     */
    private final DonutChart positionMixChart = new DonutChart();

    /**
     * Tag and score coverage chart, measured against valid rows.
     */
    private final DatasetChart coverageChart = new DatasetChart();

    /**
     * Material distribution chart.
     */
    private final DatasetChart materialChart = new DatasetChart();

    /**
     * Evaluation distribution chart.
     */
    private final DatasetChart evalChart = new DatasetChart();

    /**
     * Tag frequency chart.
     */
    private final DatasetChart tagChart = new DatasetChart();

    /**
     * Engine frequency chart.
     */
    private final DatasetChart engineChart = new DatasetChart();

    /**
     * Valid-row table model.
     */
    private final DatasetTableModel sampleModel = new DatasetTableModel(false);

    /**
     * Issue-row table model.
     */
    private final DatasetTableModel issueModel = new DatasetTableModel(true);

    /**
     * Valid-row sample table.
     */
    private final JTable sampleTable = new JTable(sampleModel);

    /**
     * Sample table sorter, retained so the filter row can update it.
     */
    private TableRowSorter<DatasetTableModel> sampleSorter;

    /**
     * Issue table.
     */
    private final JTable issueTable = new JTable(issueModel);

    /**
     * Issue table sorter, retained so the filter row can update it.
     */
    private TableRowSorter<DatasetTableModel> issueSorter;

    /**
     * Table filter field.
     */
    private final JTextField tableFilterField = new JTextField();

    /**
     * Inspector board preview for the selected dataset row.
     */
    private final BoardPanel inspectorBoard = new BoardPanel();

    /**
     * Inspector FEN/row text.
     */
    private final JTextArea inspectorFenArea = new JTextArea();

    /**
     * Inspector detail values.
     */
    private final JLabel inspectorSideValue = detailValueLabel(),
            inspectorMaterialValue = detailValueLabel(),
            inspectorLabelValue = detailValueLabel(),
            inspectorIssueValue = detailValueLabel();

    /**
     * Tabbed container hosting the sample and issue tables.
     */
    private JTabbedPane tableTabs;

    /**
     * Opens the selected sample's position in the shared Board tab.
     */
    private final JButton openInBoardButton = button("Open in Board", false, event -> openSelectedRowInBoard());

    /**
     * Opens the selected sample's position in a new detached Board tab.
     */
    private final JButton openInNewBoardButton =
            button("Open in New Board", false, event -> openSelectedRowInNewBoard());

    /**
     * Copies the selected sample's FEN to the clipboard.
     */
    private final JButton copyFenButton = button("Copy FEN", false, event -> copySelectedFen());

    /**
     * Body card container: a welcome hero while no dataset is loaded, the full
     * analytics (metrics, charts, tables) once a scan has rows.
     */
    private final JPanel bodyCard = new JPanel(new java.awt.CardLayout());

    /**
     * Active background worker.
     */
    private transient SwingWorker<DatasetSummary, Void> worker;

    /**
     * Last completed summary.
     */
    private DatasetSummary summary = DatasetSummary.empty();

    /**
     * Creates a dataset panel with no Board navigation.
     */
    public DatasetPanel() {
        this(null, null);
    }

    /**
     * Creates the dataset panel.
     *
     * @param openFenInBoard callback that loads a FEN into the shared Board tab,
     *     or {@code null} to omit the "Open in Board" affordance
     * @param openFenInNewBoard callback that loads a FEN into a new detached
     *     Board tab, or {@code null} to omit the "Open in New Board" affordance
     */
    public DatasetPanel(Consumer<String> openFenInBoard, Consumer<String> openFenInNewBoard) {
        super(new BorderLayout(0, 0));
        this.openFenInBoard = openFenInBoard;
        this.openFenInNewBoard = openFenInNewBoard;
        setOpaque(true);
        setBackground(Theme.BG);
        buildUi();
        applySummary(DatasetSummary.empty());
        setBusy(false);
    }

    /**
     * Returns this panel as a component.
     *
     * @return panel component
     */
    public JComponent component() {
        return this;
    }

    /**
     * Stops the elapsed-time timer when the panel leaves the component tree.
     */
    @Override
    public void removeNotify() {
        loadingElapsedTimer.stop();
        super.removeNotify();
    }

    /**
     * Returns the last completed summary.
     *
     * @return dataset summary
     */
    public DatasetSummary summary() {
        return summary;
    }

    /**
     * Starts analysis for the current source path.
     */
    public void analyzeCurrentSource() {
        String text = sourceField.getText() == null ? "" : sourceField.getText().trim();
        if (text.isEmpty()) {
            setStatus("Choose a dataset file or directory first", Theme.ForegroundRole.WARNING);
            return;
        }
        Long rowLimit = rowLimitValue();
        if (rowLimit == null) {
            setStatus("Row limit must be between " + formatRowLimit(MIN_ROW_LIMIT) + " and "
                    + formatRowLimit(MAX_ROW_LIMIT) + ".", Theme.ForegroundRole.WARNING);
            rowLimitField.requestFocusInWindow();
            return;
        }
        startAnalysis(Path.of(text), rowLimit.longValue());
    }

    /**
     * Starts analysis for a path.
     *
     * @param source dataset source
     * @param rowLimit maximum rows to inspect
     */
    public void startAnalysis(Path source, long rowLimit) {
        cancelAnalysis();
        setLoadingTarget(source);
        setBusy(true);
        setStatus("Scanning " + source, Theme.ForegroundRole.INFO);
        worker = new SwingWorker<>() {
            /**
             * Runs the dataset scan away from the event-dispatch thread.
             *
             * @return dataset summary
             * @throws Exception when scanning fails
             */
            @Override
            protected DatasetSummary doInBackground() throws Exception {
                return DatasetAnalyzer.analyze(source, rowLimit);
            }

            /**
             * Applies the finished result on the event-dispatch thread.
             */
            @Override
            protected void done() {
                finishAnalysis(this);
            }
        };
        worker.execute();
    }

    /**
     * Cancels the active analysis.
     */
    public void cancelAnalysis() {
        if (worker != null && !worker.isDone()) {
            worker.cancel(true);
            setStatus("Dataset scan cancelled", Theme.ForegroundRole.WARNING);
            setBusy(false);
        }
    }

    /**
     * Produces a human-readable summary report.
     *
     * @return report text
     */
    public String report() {
        StringBuilder out = new StringBuilder(512);
        out.append("Dataset: ").append(summary.source() == null ? "-" : summary.source()).append('\n');
        out.append("Files: ").append(summary.scannedFiles()).append('\n');
        out.append("Rows: ").append(summary.rows()).append('\n');
        out.append("Valid positions: ").append(summary.validPositions()).append('\n');
        out.append("Invalid rows: ").append(summary.invalidRows()).append('\n');
        out.append("Duplicates: ").append(summary.duplicatePositions()).append('\n');
        out.append("White to move: ").append(summary.whiteToMove()).append('\n');
        out.append("Black to move: ").append(summary.blackToMove()).append('\n');
        out.append("In check: ").append(summary.inCheck()).append('\n');
        out.append("Checkmates: ").append(summary.checkmates()).append('\n');
        out.append("Stalemates: ").append(summary.stalemates()).append('\n');
        out.append("Tagged rows: ").append(summary.withTags()).append('\n');
        out.append("Scored rows: ").append(summary.withEval()).append('\n');
        out.append("Average material: ").append(Math.round(summary.averageMaterial())).append(" cp\n");
        appendCounts(out, "Tags", summary.topTags());
        appendCounts(out, "Engines", summary.topEngines());
        out.append("Note: ").append(summary.note()).append('\n');
        return out.toString();
    }

    /**
     * Builds the visual layout.
     */
    private void buildUi() {
        workspaceHeader.setActions(createHeaderActions());
        workspaceHeader.setContext(datasetContext());
        add(workspaceHeader, BorderLayout.NORTH);
        SurfacePanel page = new SurfacePanel(new BorderLayout(0, Theme.SPACE_MD));
        page.add(createToolbar(), BorderLayout.NORTH);
        // The body fills the tab directly (the overview scrolls internally) so the
        // sample / issue split gets the full height to divide — wrapping the whole
        // page in a scroll instead let the charts dictate height and squeezed the
        // tables into a thin strip.
        page.add(createBody(), BorderLayout.CENTER);
        add(page, BorderLayout.CENTER);
    }

    /**
     * Creates the source toolbar.
     *
     * @return toolbar
     */
    private JComponent createToolbar() {
        styleFields(sourceField);
        sourceField.setToolTipText("Dataset file or directory");
        placeholder(sourceField, "Dataset file or directory");
        // The empty-state hero carries the primary Browse… affordance; this
        // toolbar no longer repeats it, so the source row is just the field.
        JPanel sourceRow = transparentPanel(new BorderLayout(Theme.SPACE_SM, 0));
        sourceRow.add(sourceField, BorderLayout.CENTER);

        JPanel controls = transparentPanel(new WrappingFlowLayout(FlowLayout.LEFT, Theme.SPACE_SM, 0));
        styleFields(rowLimitField);
        rowLimitField.setColumns(10);
        rowLimitField.setToolTipText("Maximum rows to scan");
        rowLimitField.setPreferredSize(new Dimension(120, Theme.CONTROL_HEIGHT));
        controls.add(label("row limit"));
        controls.add(rowLimitField);

        JPanel wrapper = transparentPanel(new BorderLayout(0, Theme.SPACE_SM));
        JPanel toolbar = transparentPanel(new BorderLayout(0, Theme.SPACE_SM));
        toolbar.add(sourceRow, BorderLayout.NORTH);
        toolbar.add(controls, BorderLayout.CENTER);
        wrapper.add(toolbar, BorderLayout.CENTER);
        JPanel status = transparentPanel(new BorderLayout(Theme.SPACE_SM, 0));
        status.add(statusLabel, BorderLayout.CENTER);
        JPanel spinnerCell = transparentPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SPACE_SM, Theme.SPACE_XS));
        spinnerCell.add(verdictBadge);
        spinnerCell.add(spinner);
        status.add(spinnerCell, BorderLayout.EAST);
        wrapper.add(status, BorderLayout.SOUTH);
        return wrapper;
    }

    /**
     * Creates the Datasets header action group.
     *
     * @return action row
     */
    private JComponent createHeaderActions() {
        return Ui.controlRow(FlowLayout.RIGHT,
                button("Browse…", false, event -> chooseDatasetPath()),
                analyzeButton,
                stopButton,
                copyReportButton);
    }

    /**
     * Parses the visible row-limit field.
     *
     * @return row limit, or null when the value is invalid
     */
    private Long rowLimitValue() {
        String raw = rowLimitField.getText() == null ? "" : rowLimitField.getText()
                .replace(",", "")
                .replace("_", "")
                .trim();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            long value = Long.parseLong(raw);
            return value >= MIN_ROW_LIMIT && value <= MAX_ROW_LIMIT ? Long.valueOf(value) : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Formats row-limit values using stable ASCII grouping.
     *
     * @param value row count
     * @return formatted row count
     */
    private static String formatRowLimit(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    /**
     * Creates the main analysis body.
     *
     * @return body component
     */
    private JComponent createBody() {
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, createOverview(), createTables());
        // Give the sample / issue tables a roughly equal share with the charts and
        // hand them half of any extra height, so inspecting rows is not squeezed
        // into a thin strip under the overview.
        split.setResizeWeight(0.5d);
        SplitPaneStyler.style(split);
        // setDividerLocation(double) is ignored until the split has a real height,
        // so place it 50/50 on the first sizing pass, then leave it to the user.
        split.addComponentListener(new java.awt.event.ComponentAdapter() {
            /**
             * {@inheritDoc}
             */
            @Override
            public void componentResized(java.awt.event.ComponentEvent event) {
                if (split.getHeight() > 0) {
                    split.removeComponentListener(this);
                    split.setDividerLocation(0.5d);
                }
            }
        });

        // Show a focused welcome hero until a dataset is scanned, so the tab is
        // not a wall of repeated "No dataset loaded" placeholders; reveal the
        // full analytics once there are rows.
        bodyCard.setOpaque(false);
        bodyCard.add(createEmptyHero(), "empty");
        bodyCard.add(createLoadingCard(), "loading");
        bodyCard.add(split, "loaded");
        return bodyCard;
    }

    /**
     * Builds the centered loading card shown while a scan runs, so the body
     * reads as "working" instead of still inviting another Browse.
     *
     * @return loading component
     */
    private JComponent createLoadingCard() {
        loadingSpinner.setAlignmentX(CENTER_ALIGNMENT);
        loadingTitle.setAlignmentX(CENTER_ALIGNMENT);
        loadingTitle.setFont(Theme.font(15, Font.BOLD));
        Theme.foreground(loadingTitle, Theme.ForegroundRole.TEXT);
        loadingHint.setAlignmentX(CENTER_ALIGNMENT);
        loadingHint.setFont(Theme.font(12, Font.PLAIN));
        Theme.foreground(loadingHint, Theme.ForegroundRole.MUTED);

        JPanel stack = transparentPanel(null);
        stack.setLayout(new javax.swing.BoxLayout(stack, javax.swing.BoxLayout.Y_AXIS));
        stack.add(loadingSpinner);
        stack.add(javax.swing.Box.createVerticalStrut(Theme.SPACE_MD));
        stack.add(loadingTitle);
        stack.add(javax.swing.Box.createVerticalStrut(Theme.SPACE_XS));
        stack.add(loadingHint);
        stack.add(javax.swing.Box.createVerticalStrut(Theme.SPACE_MD));
        stack.add(card("Scan Progress", createLoadingDetails()));
        stack.add(javax.swing.Box.createVerticalStrut(Theme.SPACE_SM));
        HoldButton cancel = new HoldButton("Cancel Scan", this::cancelAnalysis, true);
        cancel.setAlignmentX(CENTER_ALIGNMENT);
        stack.add(cancel);

        JPanel center = transparentPanel(new java.awt.GridBagLayout());
        center.add(stack);
        return center;
    }

    /**
     * Creates scan detail rows for the loading state.
     *
     * @return detail panel
     */
    private JComponent createLoadingDetails() {
        JPanel details = transparentPanel(new java.awt.GridLayout(0, 2, Theme.SPACE_MD, Theme.SPACE_XS));
        details.add(detailKeyLabel("File"));
        details.add(loadingFileValue);
        details.add(detailKeyLabel("Phase"));
        details.add(loadingPhaseValue);
        details.add(detailKeyLabel("Rows"));
        details.add(loadingRowsValue);
        details.add(detailKeyLabel("Valid"));
        details.add(loadingValidValue);
        details.add(detailKeyLabel("Duplicates"));
        details.add(loadingDuplicateValue);
        details.add(detailKeyLabel("Elapsed"));
        details.add(loadingElapsedValue);
        return details;
    }

    /**
     * Points the loading card at the dataset about to be scanned.
     *
     * @param source dataset path being scanned
     */
    private void setLoadingTarget(Path source) {
        String name = source == null ? "dataset"
                : source.getFileName() == null ? source.toString() : source.getFileName().toString();
        loadingTitle.setText("Scanning " + compactText(name, 40) + "…");
        loadingFileValue.setText(source == null ? "-" : source.toString());
        loadingPhaseValue.setText("reading / validating");
        loadingRowsValue.setText("row limit " + cleanRowLimitText());
        loadingValidValue.setText("available after scan");
        loadingDuplicateValue.setText("available after scan");
        loadingElapsedValue.setText("0.0s");
    }

    /**
     * Updates elapsed scan time while the worker is running.
     */
    private void updateLoadingElapsed() {
        if (!busy || loadingStartedAt == 0L) {
            return;
        }
        long elapsedMillis = Math.max(0L, System.currentTimeMillis() - loadingStartedAt);
        loadingElapsedValue.setText(String.format(Locale.ROOT, "%.1fs", elapsedMillis / 1000.0d));
    }

    /**
     * Builds the centered welcome hero shown before any dataset is scanned.
     *
     * @return hero component
     */
    private JComponent createEmptyHero() {
        JButton browse = button("Browse…", true, event -> chooseDatasetPath());
        return application.gui.workbench.ui.Ui.emptyState("Profile a dataset",
                "Choose a .pgn or .jsonl file — or a directory — then Analyze to chart validity, "
                        + "tags, score bands, and material across every position.",
                browse);
    }

    /**
     * Shows the body card that matches the current state: the loading card while
     * a scan runs, the analytics once a scan has rows, otherwise the welcome hero.
     */
    private void updateBodyState() {
        String card = busy ? "loading" : summary.rows() > 0L ? "loaded" : "empty";
        ((java.awt.CardLayout) bodyCard.getLayout()).show(bodyCard, card);
    }

    /**
     * Creates metric and chart sections.
     *
     * @return overview component
     */
    private JComponent createOverview() {
        JPanel overview = transparentPanel(new BorderLayout(0, Theme.SPACE_MD));
        // Paint the page surface so the overview reads identically whether or not
        // its internal scroll is engaged (it sits in the upper split pane and
        // scrolls when the charts overflow their half).
        overview.setOpaque(true);
        overview.setBackground(Theme.PANEL_SOLID);
        // The KPI tiles lead the overview; the former prose "insight" strip below
        // them restated the same numbers, so it was dropped — each insight now
        // rides as a hover tooltip on its related tile (see updateInsights).
        overview.add(createMetrics(), BorderLayout.NORTH);
        qualityChart.setEmpty("No dataset loaded", "Choose a .pgn or .jsonl file, then Analyze");
        sideChart.setEmpty("No dataset loaded", "Side-to-move balance appears after a scan");
        positionMixChart.setEmpty("No dataset loaded", "Quiet / check / mate mix appears after a scan");
        materialChart.setEmpty("No dataset loaded", "Material spread appears after a scan");
        evalChart.setEmpty("No dataset loaded", "Score bands appear once rows are scored");
        coverageChart.setEmpty("No dataset loaded", "Tag and score coverage appears after a scan");
        tagChart.setEmpty("No dataset loaded", "The most common tags appear after a scan");
        engineChart.setEmpty("No dataset loaded", "Source engines appear after a scan");
        tagChart.setSelectionHandler(this::applyChartFilter);
        engineChart.setSelectionHandler(this::applyChartFilter);
        // Responsive grid: the charts reflow and fill the width just like the KPI
        // strip above, with a slightly larger gap for breathing room. A smaller
        // minimum column keeps them in several columns (rather than collapsing to
        // one tall stack) as the panel narrows, so they "extend" like the tiles.
        application.gui.workbench.ui.CardGrid charts =
                new application.gui.workbench.ui.CardGrid(270, Theme.SPACE_LG);
        charts.add(card("Dataset Health", qualityChart));
        charts.add(card("Side Balance", sideChart));
        charts.add(card("Position Mix", positionMixChart));
        charts.add(card("Material Bands", materialChart));
        charts.add(card("Score Bands", evalChart));
        charts.add(card("Coverage", coverageChart));
        charts.add(card("Top Tags", tagChart));
        charts.add(card("Engine Sources", engineChart));
        overview.add(charts, BorderLayout.CENTER);
        return scroll(fillViewport(overview));
    }

    /**
     * Creates the metric strip.
     *
     * @return metrics component
     */
    private JComponent createMetrics() {
        // Responsive KPI strip: tiles flow across the width and wrap on narrow
        // windows instead of being squeezed thin in a fixed 1x7 row. Same gap as
        // the chart grid below so the whole overview shares one rhythm.
        application.gui.workbench.ui.CardGrid metrics =
                new application.gui.workbench.ui.CardGrid(150, Theme.SPACE_LG);
        metrics.add(filesMetric);
        metrics.add(rowsMetric);
        metrics.add(validMetric);
        metrics.add(duplicateMetric);
        metrics.add(tagsMetric);
        metrics.add(evalMetric);
        metrics.add(materialMetric);
        return metrics;
    }

    /**
     * Creates the sample and issue table area.
     *
     * @return table component
     */
    private JComponent createTables() {
        configureTable(sampleTable);
        configureTable(issueTable);
        sampleSorter = new TableRowSorter<>(sampleModel);
        issueSorter = new TableRowSorter<>(issueModel);
        sampleTable.setRowSorter(sampleSorter);
        issueTable.setRowSorter(issueSorter);
        wireRowActions(sampleTable);
        wireRowActions(issueTable);
        tableTabs = application.gui.workbench.ui.Ui.tabbedPane();
        tableTabs.addTab("Samples", emptyAwareTable(sampleTable, sampleModel,
                "No samples loaded", "Choose a dataset and Analyze to inspect its rows."));
        tableTabs.addTab("Issues", emptyAwareTable(issueTable, issueModel,
                "No issues found", "Validation problems appear here after a scan."));
        tableTabs.addChangeListener(event -> updateRowActions());

        JPanel actions = transparentPanel(new WrappingFlowLayout(FlowLayout.RIGHT, Theme.SPACE_SM, 0));
        actions.add(copyFenButton);
        if (openFenInNewBoard != null) {
            actions.add(openInNewBoardButton);
        }
        if (openFenInBoard != null) {
            actions.add(openInBoardButton);
        }
        styleFields(tableFilterField);
        placeholder(tableFilterField, "Filter tags, engine, FEN, file, issue...");
        tableFilterField.getDocument().addDocumentListener(changeListener(this::applyTableFilter));
        JButton clearFilter = button("Clear", false, event -> tableFilterField.setText(""));
        JPanel filters = transparentPanel(new BorderLayout(Theme.SPACE_SM, 0));
        filters.add(tableFilterField, BorderLayout.CENTER);
        filters.add(clearFilter, BorderLayout.EAST);
        JPanel top = transparentPanel(new BorderLayout(Theme.SPACE_SM, 0));
        top.add(filters, BorderLayout.CENTER);
        top.add(actions, BorderLayout.EAST);

        JSplitPane tableSplit = SplitPaneStyler.styledHorizontalSplit(tableTabs, createRowInspector(), 0.72d);
        JPanel wrap = transparentPanel(new BorderLayout(0, Theme.SPACE_SM));
        wrap.add(top, BorderLayout.NORTH);
        wrap.add(tableSplit, BorderLayout.CENTER);
        updateRowActions();
        return wrap;
    }

    /**
     * Creates the row inspector shown beside the sample and issue tables.
     *
     * @return inspector component
     */
    private JComponent createRowInspector() {
        inspectorBoard.setPreferredSize(new Dimension(180, 180));
        inspectorBoard.setMinimumSize(new Dimension(132, 132));
        inspectorBoard.setPositionInstant(new Position(Setup.getStandardStartFEN()), Move.NO_MOVE);
        Theme.codeBlock(inspectorFenArea);
        inspectorFenArea.setEditable(false);
        inspectorFenArea.setFocusable(true);
        inspectorFenArea.setLineWrap(true);
        inspectorFenArea.setWrapStyleWord(true);
        inspectorFenArea.setRows(4);
        inspectorFenArea.setText("Select a dataset row.");

        JPanel details = transparentPanel(new java.awt.GridLayout(0, 2, Theme.SPACE_MD, Theme.SPACE_XS));
        details.add(detailKeyLabel("Side"));
        details.add(inspectorSideValue);
        details.add(detailKeyLabel("Material"));
        details.add(inspectorMaterialValue);
        details.add(detailKeyLabel("Label"));
        details.add(inspectorLabelValue);
        details.add(detailKeyLabel("Issue"));
        details.add(inspectorIssueValue);

        JPanel body = transparentPanel(new BorderLayout(0, Theme.SPACE_SM));
        body.add(inspectorBoard, BorderLayout.NORTH);
        body.add(scroll(inspectorFenArea), BorderLayout.CENTER);
        body.add(details, BorderLayout.SOUTH);
        updateRowInspector(null);
        return card("Row Inspector", body);
    }

    /**
     * Enables row-level selection to open or copy a position: a selection
     * listener keeps the action buttons in sync and a double-click on a row
     * opens it in the Board tab.
     *
     * @param table sample or issue table
     */
    private void wireRowActions(JTable table) {
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                updateRowActions();
            }
        });
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            /**
             * {@inheritDoc}
             */
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2 && javax.swing.SwingUtilities.isLeftMouseButton(event)) {
                    openSelectedRowInBoard();
                }
            }
        });
    }

    /**
     * Returns the table behind the active tab.
     *
     * @return the visible sample or issue table
     */
    private JTable activeTable() {
        return tableTabs != null && tableTabs.getSelectedIndex() == 1 ? issueTable : sampleTable;
    }

    /**
     * Returns the sample backing the active table's selected row.
     *
     * @return selected sample row, or {@code null} when nothing is selected
     */
    private DatasetSummary.SampleRow selectedRow() {
        JTable table = activeTable();
        DatasetTableModel model = table == issueTable ? issueModel : sampleModel;
        int view = table.getSelectedRow();
        if (view < 0) {
            return null;
        }
        return model.rowAt(table.convertRowIndexToModel(view));
    }

    /**
     * Refreshes the enablement of the row-action buttons for the current
     * selection.
     */
    private void updateRowActions() {
        DatasetSummary.SampleRow row = selectedRow();
        boolean hasFen = row != null && !row.fen().isBlank();
        copyFenButton.setEnabled(hasFen);
        openInBoardButton.setEnabled(hasFen && openFenInBoard != null);
        openInNewBoardButton.setEnabled(hasFen && openFenInNewBoard != null);
        updateRowInspector(row);
    }

    /**
     * Updates the row inspector from the selected table row.
     *
     * @param row selected row, or null
     */
    private void updateRowInspector(DatasetSummary.SampleRow row) {
        if (row == null) {
            inspectorFenArea.setText("Select a sample or issue row.");
            inspectorSideValue.setText("-");
            inspectorMaterialValue.setText("-");
            inspectorLabelValue.setText("-");
            inspectorIssueValue.setText("-");
            return;
        }
        inspectorFenArea.setText(row.fen());
        inspectorFenArea.setCaretPosition(0);
        inspectorLabelValue.setText(row.label().isBlank() ? "-" : compactText(row.label(), 52));
        inspectorIssueValue.setText(row.issue().isBlank() ? "none" : compactText(row.issue(), 52));
        inspectorSideValue.setText(row.side().isBlank() ? "-" : row.side());
        inspectorMaterialValue.setText(format(row.material()) + " cp");
        try {
            Position position = new Position(row.fen());
            inspectorBoard.setPositionInstant(position, Move.NO_MOVE);
            inspectorSideValue.setText(position.isWhiteToMove() ? "White" : "Black");
            inspectorMaterialValue.setText(format(position.countTotalMaterial()) + " cp");
        } catch (RuntimeException ex) {
            inspectorIssueValue.setText(row.issue().isBlank() ? "not a valid FEN" : compactText(row.issue(), 52));
        }
    }

    /**
     * Opens the selected sample's position in the shared Board tab.
     */
    private void openSelectedRowInBoard() {
        openSelected(openFenInBoard, "Opened position in Board");
    }

    /**
     * Opens the selected sample's position in a new detached Board tab.
     */
    private void openSelectedRowInNewBoard() {
        openSelected(openFenInNewBoard, "Opened position in a new Board tab");
    }

    /**
     * Sends the selected row's FEN to a navigation sink. Rows whose text is not
     * a parseable position (e.g. malformed issue rows) are rejected with a status
     * note rather than handed to the board.
     *
     * @param sink callback that loads the FEN, or {@code null} to do nothing
     * @param successMessage status shown once the position is handed off
     */
    private void openSelected(Consumer<String> sink, String successMessage) {
        if (sink == null) {
            return;
        }
        DatasetSummary.SampleRow row = selectedRow();
        if (row == null || row.fen().isBlank()) {
            setStatus("Select a position row first", Theme.ForegroundRole.WARNING);
            return;
        }
        String fen = row.fen();
        try {
            new Position(fen);
        } catch (RuntimeException ex) {
            setStatus("That row is not a valid position", Theme.ForegroundRole.WARNING);
            return;
        }
        sink.accept(fen);
        setStatus(successMessage, Theme.ForegroundRole.SUCCESS);
    }

    /**
     * Copies the selected row's FEN to the system clipboard.
     */
    private void copySelectedFen() {
        DatasetSummary.SampleRow row = selectedRow();
        if (row == null || row.fen().isBlank()) {
            setStatus("Select a row with a FEN first", Theme.ForegroundRole.WARNING);
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(row.fen()), null);
        setStatus("FEN copied", Theme.ForegroundRole.SUCCESS);
    }

    /**
     * Wraps a table so it shows a centered empty-state while its model has no
     * rows, swapping to the scrolled table once it is populated.
     *
     * @param table data table
     * @param model the table's model
     * @param title empty-state title
     * @param hint empty-state hint
     * @return card-wrapped table
     */
    private static JComponent emptyAwareTable(JTable table, javax.swing.table.TableModel model,
            String title, String hint) {
        JPanel card = new JPanel(new java.awt.CardLayout());
        card.setOpaque(false);
        card.add(tableScroll(table), "table");
        card.add(application.gui.workbench.ui.Ui.emptyState(title, hint), "empty");
        Runnable toggle = () -> ((java.awt.CardLayout) card.getLayout())
                .show(card, model.getRowCount() == 0 ? "empty" : "table");
        model.addTableModelListener(event -> toggle.run());
        toggle.run();
        return card;
    }

    /**
     * Wraps a table in a styled scroll pane.
     *
     * @param table table
     * @return scroll pane
     */
    private static JScrollPane tableScroll(JTable table) {
        JScrollPane pane = scroll(table);
        pane.setPreferredSize(new Dimension(560, 320));
        return pane;
    }

    /**
     * Applies table styling and column widths.
     *
     * @param table table
     */
    private static void configureTable(JTable table) {
        Theme.table(table, TABLE_ROW_HEIGHT);
        table.setAutoCreateRowSorter(false);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        TableColumnModel columns = table.getColumnModel();
        setColumnWidth(columns, 0, 120);
        setColumnWidth(columns, 1, 64);
        setColumnWidth(columns, 2, 70);
        setColumnWidth(columns, 3, 70);
        setColumnWidth(columns, 4, 82);
        setColumnWidth(columns, 5, 150);
        setColumnWidth(columns, 6, 420);
        columns.getColumn(5).setCellRenderer(new ChipCellRenderer());
        columns.getColumn(6).setCellRenderer(new FenCellRenderer());
        if (columns.getColumnCount() > 7) {
            setColumnWidth(columns, 7, 170);
            columns.getColumn(7).setCellRenderer(new FenCellRenderer());
        }
    }

    /**
     * Applies the free-text row filter to both tables.
     */
    private void applyTableFilter() {
        RowFilter<DatasetTableModel, Integer> filter = rowFilter(tableFilterField.getText());
        if (sampleSorter != null) {
            sampleSorter.setRowFilter(filter);
        }
        if (issueSorter != null) {
            issueSorter.setRowFilter(filter);
        }
        updateRowActions();
    }

    /**
     * Uses a chart label as a table filter.
     *
     * @param label clicked chart label
     */
    private void applyChartFilter(String label) {
        String value = label == null ? "" : label.trim();
        if (value.isEmpty()) {
            return;
        }
        tableFilterField.setText(value);
        setStatus("Filtered rows by " + value, Theme.ForegroundRole.INFO);
    }

    /**
     * Creates a case-insensitive table filter.
     *
     * @param raw raw query
     * @return row filter, or null for no filter
     */
    private static RowFilter<DatasetTableModel, Integer> rowFilter(String raw) {
        String query = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return null;
        }
        return new RowFilter<>() {
            /**
             * {@inheritDoc}
             */
            @Override
            public boolean include(Entry<? extends DatasetTableModel, ? extends Integer> entry) {
                DatasetTableModel model = entry.getModel();
                int row = entry.getIdentifier().intValue();
                for (int column = 0; column < model.getColumnCount(); column++) {
                    Object value = model.getValueAt(row, column);
                    if (String.valueOf(value).toLowerCase(Locale.ROOT).contains(query)) {
                        return true;
                    }
                }
                return false;
            }
        };
    }


    /**
     * Opens a dataset file chooser.
     */
    private void chooseDatasetPath() {
        File selected = sourceField.getText() == null || sourceField.getText().isBlank()
                ? null : new File(sourceField.getText().trim());
        FileNameExtensionFilter filter = new FileNameExtensionFilter(
                "Datasets (fen, json, jsonl, record, csv, tsv, txt, pgn)",
                "fen", "fens", "json", "jsonl", "record", "records", "csv", "tsv", "txt", "pgn");
        JFileChooser chooser = FileDialogs.createFileChooser("Open Dataset", selected, filter);
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            sourceField.setText(chooser.getSelectedFile().getPath());
            analyzeCurrentSource();
        }
    }

    /**
     * Applies a completed worker result.
     *
     * @param finishedWorker completed worker
     */
    private void finishAnalysis(SwingWorker<DatasetSummary, Void> finishedWorker) {
        if (finishedWorker != worker) {
            return;
        }
        try {
            if (!finishedWorker.isCancelled()) {
                applySummary(finishedWorker.get());
                setStatus(summary.note(), summary.invalidRows() == 0L
                        ? Theme.ForegroundRole.SUCCESS : Theme.ForegroundRole.WARNING);
            }
        } catch (CancellationException ex) {
            setStatus("Dataset scan cancelled", Theme.ForegroundRole.WARNING);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            setStatus("Dataset scan interrupted", Theme.ForegroundRole.WARNING);
        } catch (ExecutionException ex) {
            setStatus(rootMessage(ex), Theme.ForegroundRole.ERROR);
        } finally {
            setBusy(false);
        }
    }

    /**
     * Applies a summary to the visual controls.
     *
     * @param next summary
     */
    public void applySummary(DatasetSummary next) {
        summary = next == null ? DatasetSummary.empty() : next;
        updateVerdict();
        updateMetrics();
        updateCharts();
        sampleModel.setRows(summary.samples());
        issueModel.setRows(summary.issues());
        copyReportButton.setEnabled(hasReport());
        workspaceHeader.setContext(datasetContext());
        updateBodyState();
    }

    /**
     * Updates the metric strip.
     */
    private void updateMetrics() {
        filesMetric.setMetric(format(summary.scannedFiles()), sourceName());
        rowsMetric.setMetric(format(summary.rows()),
                summary.truncated() ? "row limit reached" : "rows scanned");
        validMetric.setMetric(summary.rows() == 0L ? "-" : percent(summary.validRatio()),
                format(summary.validPositions()) + " valid / " + format(summary.rows()));
        // Quality-critical tiles flip green/amber by their actual result so the
        // KPI strip's only colour carries meaning (clean vs. needs attention).
        validMetric.setRole(summary.rows() == 0L ? DatasetChart.Role.NEUTRAL
                : summary.validRatio() >= 1.0d ? DatasetChart.Role.SUCCESS : DatasetChart.Role.WARNING);
        duplicateMetric.setMetric(summary.validPositions() == 0L ? "-" : percent(1.0d - summary.duplicateRatio()),
                format(summary.duplicatePositions()) + " duplicate FENs");
        duplicateMetric.setRole(summary.validPositions() == 0L ? DatasetChart.Role.NEUTRAL
                : summary.duplicatePositions() == 0L ? DatasetChart.Role.SUCCESS : DatasetChart.Role.WARNING);
        tagsMetric.setMetric(summary.validPositions() == 0L ? "-" : percent(ratio(summary.withTags(), summary.validPositions())),
                format(summary.withTags()) + " tagged rows");
        evalMetric.setMetric(summary.validPositions() == 0L ? "-" : percent(ratio(summary.withEval(), summary.validPositions())),
                format(summary.withEval()) + " scored rows");
        materialMetric.setMetric(summary.validPositions() == 0L ? "-" : Math.round(summary.averageMaterial()) + " cp",
                materialRangeText());
        updateInsights();
    }

    /**
     * Updates the dataset verdict badge.
     */
    private void updateVerdict() {
        if (busy) {
            verdictBadge.running("scanning");
            return;
        }
        if (summary.rows() == 0L) {
            verdictBadge.notRun("not run");
            verdictBadge.setToolTipText("Choose a dataset and run Analyze");
            return;
        }
        String verdict = verdictText();
        verdictBadge.setToolTipText(summary.note());
        switch (verdict) {
            case "Healthy" -> verdictBadge.complete(verdict);
            case "Warnings" -> verdictBadge.warning(verdict);
            default -> verdictBadge.error(verdict);
        }
    }

    /**
     * Returns the high-level dataset verdict.
     *
     * @return verdict text
     */
    private String verdictText() {
        if (summary.rows() == 0L) {
            return "Not run";
        }
        if (summary.invalidRows() == 0L && summary.duplicatePositions() == 0L && !summary.truncated()) {
            return "Healthy";
        }
        if (summary.validRatio() >= 0.95d && summary.duplicateRatio() <= 0.15d) {
            return "Warnings";
        }
        return "Needs cleanup";
    }

    /**
     * Updates analytical insight text.
     */
    private void updateInsights() {
        if (summary.rows() == 0L) {
            setInsight(qualityInsight, "Load a dataset to profile row quality.", Theme.ForegroundRole.MUTED);
            setInsight(coverageInsight, "Tags and scores per valid row.", Theme.ForegroundRole.MUTED);
            setInsight(balanceInsight, "Side-to-move balance after scanning.", Theme.ForegroundRole.MUTED);
            setInsight(materialInsight, "Material spread after scanning.", Theme.ForegroundRole.MUTED);
        } else {
            boolean clean = summary.invalidRows() == 0L && summary.duplicatePositions() == 0L;
            setInsight(qualityInsight, clean
                            ? "Clean scan: no invalid rows or duplicate FENs."
                            : format(summary.invalidRows()) + " invalid, "
                                    + format(summary.duplicatePositions()) + " duplicate.",
                    clean ? Theme.ForegroundRole.SUCCESS : Theme.ForegroundRole.WARNING);
            // Quality keeps the one genuine pass/warn status colour; the rest are
            // neutral descriptive text. The role colours already live on the KPI
            // tile edges, so colouring every insight line as well just makes a
            // distracting rainbow.
            setInsight(coverageInsight,
                    "Tags " + percent(ratio(summary.withTags(), summary.validPositions()))
                            + " · scores " + percent(ratio(summary.withEval(), summary.validPositions())) + ".",
                    Theme.ForegroundRole.MUTED);
            setInsight(balanceInsight, sideBalanceText(), Theme.ForegroundRole.MUTED);
            setInsight(materialInsight,
                    summary.validPositions() == 0L ? "No valid positions to profile."
                            : "Avg " + Math.round(summary.averageMaterial()) + " cp · " + materialRangeText() + ".",
                    Theme.ForegroundRole.MUTED);
        }
        // Surface the prose on hover over the related KPI tile rather than as an
        // always-visible strip, so the overview leads with the numbers.
        validMetric.setTooltip(qualityInsight.getText());
        tagsMetric.setTooltip(coverageInsight.getText());
        rowsMetric.setTooltip(balanceInsight.getText());
        materialMetric.setTooltip(materialInsight.getText());
    }

    /**
     * Updates chart components.
     */
    private void updateCharts() {
        // Compositional metrics read as donuts: row health is a clean/dup/invalid
        // whole, side-to-move is a white/black whole. Semantic green/amber/red is
        // reserved for the health ring where it means clean vs. bad.
        qualityChart.setSegments(List.of(
                new DonutChart.Segment("unique valid", Math.max(0L,
                        summary.validPositions() - summary.duplicatePositions()), DatasetChart.Role.SUCCESS),
                new DonutChart.Segment("duplicate", summary.duplicatePositions(), DatasetChart.Role.WARNING),
                new DonutChart.Segment("invalid", summary.invalidRows(), DatasetChart.Role.ERROR)),
                "rows");
        sideChart.setSegments(List.of(
                new DonutChart.Segment("white to move", summary.whiteToMove(), DatasetChart.Role.ACCENT),
                new DonutChart.Segment("black to move", summary.blackToMove(), DatasetChart.Role.NEUTRAL)),
                "positions");
        // Position mix partitions valid rows into disjoint states. Checkmate is a
        // subset of in-check and stalemate is disjoint from it, so "in check"
        // counts non-mating checks and the four slices sum to the valid total.
        long mates = summary.checkmates();
        long stalemates = summary.stalemates();
        long checksNonMate = Math.max(0L, summary.inCheck() - mates);
        long quiet = Math.max(0L, summary.validPositions() - summary.inCheck() - stalemates);
        positionMixChart.setSegments(List.of(
                new DonutChart.Segment("quiet", quiet, DatasetChart.Role.NEUTRAL),
                new DonutChart.Segment("in check", checksNonMate, DatasetChart.Role.ACCENT),
                new DonutChart.Segment("checkmate", mates, DatasetChart.Role.ERROR),
                new DonutChart.Segment("stalemate", stalemates, DatasetChart.Role.WARNING)),
                "positions");
        // Coverage reads against the valid total, so each bar's faint track is
        // 100% and a half-filled bar means half the valid rows carry that signal.
        coverageChart.setBars(List.of(
                new DatasetChart.Bar("tagged", summary.withTags(), DatasetChart.Role.SUCCESS),
                new DatasetChart.Bar("scored", summary.withEval(), DatasetChart.Role.ACCENT)),
                summary.validPositions());
        materialChart.setBuckets(MATERIAL_LABELS, summary.materialBuckets(), DatasetChart.Role.ACCENT);
        evalChart.setBuckets(EVAL_LABELS, summary.evalBuckets(), DatasetChart.Role.ACCENT);
        tagChart.setBars(namedBars(summary.topTags(), DatasetChart.Role.ACCENT));
        engineChart.setBars(namedBars(summary.topEngines(), DatasetChart.Role.ACCENT));
    }

    /**
     * Converts named counts into chart bars.
     *
     * @param counts named counts
     * @param role color role
     * @return chart bars
     */
    private static List<DatasetChart.Bar> namedBars(List<DatasetSummary.NamedCount> counts, DatasetChart.Role role) {
        List<DatasetChart.Bar> bars = new ArrayList<>();
        for (DatasetSummary.NamedCount count : counts) {
            bars.add(new DatasetChart.Bar(count.name(), count.count(), role));
        }
        return List.copyOf(bars);
    }

    /**
     * Copies the current report to the system clipboard.
     */
    private void copyReport() {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(report()), null);
        setStatus("Dataset report copied", Theme.ForegroundRole.SUCCESS);
    }

    /**
     * Sets busy state.
     *
     * @param busy true while scanning
     */
    private void setBusy(boolean busy) {
        this.busy = busy;
        analyzeButton.setEnabled(!busy);
        stopButton.setEnabled(busy);
        copyReportButton.setEnabled(!busy && hasReport());
        spinner.setSpinning(busy);
        loadingSpinner.setSpinning(busy);
        if (busy) {
            loadingStartedAt = System.currentTimeMillis();
            updateLoadingElapsed();
            loadingElapsedTimer.start();
        } else {
            loadingElapsedTimer.stop();
            loadingStartedAt = 0L;
        }
        updateVerdict();
        workspaceHeader.setContext(datasetContext());
        updateBodyState();
    }

    /**
     * Returns the Datasets shell context line.
     *
     * @return context summary
     */
    private String datasetContext() {
        if (busy) {
            return sourceTargetName() + " · Scanning · row limit " + cleanRowLimitText();
        }
        if (summary.rows() > 0L) {
            return sourceName() + " · " + format(summary.rows()) + " rows · "
                    + percent(summary.validRatio()) + " valid · "
                    + percent(1.0d - summary.duplicateRatio()) + " unique";
        }
        return "No dataset loaded · row limit " + cleanRowLimitText();
    }

    /**
     * Returns the currently typed source target, compacted for header use.
     *
     * @return source target name
     */
    private String sourceTargetName() {
        String text = sourceField.getText() == null ? "" : sourceField.getText().trim();
        if (text.isEmpty()) {
            return "dataset";
        }
        Path path = Path.of(text);
        Path name = path.getFileName();
        return compactText(name == null ? text : name.toString(), 42);
    }

    /**
     * Returns the row-limit field text with a fallback.
     *
     * @return row-limit text
     */
    private String cleanRowLimitText() {
        String text = rowLimitField.getText() == null ? "" : rowLimitField.getText().trim();
        return text.isEmpty() ? formatRowLimit(DEFAULT_ROW_LIMIT) : text;
    }

    /**
     * Returns whether there is a meaningful report to copy.
     *
     * @return true when the current summary has scanned content
     */
    private boolean hasReport() {
        return summary.scannedFiles() > 0L || summary.rows() > 0L;
    }

    /**
     * Sets status text and role.
     *
     * @param text status text
     * @param role foreground role
     */
    private void setStatus(String text, Theme.ForegroundRole role) {
        statusLabel.setText(text == null ? "" : text);
        Theme.foreground(statusLabel, role);
    }

    /**
     * Appends top count rows to a report.
     *
     * @param out target builder
     * @param title section title
     * @param counts counts
     */
    private static void appendCounts(StringBuilder out, String title, List<DatasetSummary.NamedCount> counts) {
        if (counts.isEmpty()) {
            return;
        }
        out.append(title).append(':');
        for (DatasetSummary.NamedCount count : counts) {
            out.append(' ').append(count.name()).append('=').append(count.count());
        }
        out.append('\n');
    }

    /**
     * Creates an insight label.
     *
     * @return styled insight label
     */
    private static JLabel insightLabel() {
        JLabel label = new JLabel("-");
        Theme.foreground(label, Theme.ForegroundRole.MUTED);
        label.setFont(Theme.font(12, Font.PLAIN));
        return label;
    }

    /**
     * Creates a muted key label for compact detail grids.
     *
     * @param text label text
     * @return styled label
     */
    private static JLabel detailKeyLabel(String text) {
        JLabel label = new JLabel(text == null ? "" : text);
        Theme.foreground(label, Theme.ForegroundRole.MUTED);
        label.setFont(Theme.font(11, Font.BOLD));
        return label;
    }

    /**
     * Creates a value label for compact detail grids.
     *
     * @return styled label
     */
    private static JLabel detailValueLabel() {
        JLabel label = new JLabel("-");
        Theme.foreground(label, Theme.ForegroundRole.TEXT);
        label.setFont(Theme.font(12, Font.PLAIN));
        return label;
    }

    /**
     * Applies insight text and semantic color.
     *
     * @param label target label
     * @param text insight text
     * @param role foreground role
     */
    private static void setInsight(JLabel label, String text, Theme.ForegroundRole role) {
        String value = text == null ? "" : text;
        label.setText(value);
        // The insight cells are an equal-width 4-column grid, so a long line can
        // clip; keep the full text reachable on hover.
        label.setToolTipText(value.isBlank() ? null : value);
        Theme.foreground(label, role);
    }

    /**
     * Returns a ratio with zero-denominator protection.
     *
     * @param value numerator
     * @param total denominator
     * @return ratio in {@code [0, 1]} when possible
     */
    private static double ratio(long value, long total) {
        return total <= 0L ? 0.0d : Math.max(0.0d, Math.min(1.0d, (double) value / (double) total));
    }

    /**
     * Formats a ratio as a compact percentage.
     *
     * @param ratio ratio value
     * @return percentage text
     */
    private static String percent(double ratio) {
        return String.format(Locale.ROOT, "%.0f%%", Math.max(0.0d, Math.min(1.0d, ratio)) * 100.0d);
    }

    /**
     * Compacts a text label to a maximum character count.
     *
     * @param text source text
     * @param maxChars maximum displayed characters
     * @return compact text
     */
    private static String compactText(String text, int maxChars) {
        String value = text == null ? "" : text;
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, Math.max(0, maxChars - 3)) + "...";
    }

    /**
     * Returns material range text.
     *
     * @return material range
     */
    private String materialRangeText() {
        if (summary.validPositions() == 0L) {
            return "no valid rows";
        }
        return format(summary.minMaterial()) + "-" + format(summary.maxMaterial()) + " cp";
    }

    /**
     * Returns a compact source filename for metric display.
     *
     * @return compact source label
     */
    private String sourceName() {
        if (summary.source() == null) {
            return "no source selected";
        }
        Path fileName = summary.source().getFileName();
        String text = fileName == null ? summary.source().toString() : fileName.toString();
        return compactText(text, 26);
    }

    /**
     * Returns side-balance insight text.
     *
     * @return side-balance text
     */
    private String sideBalanceText() {
        long total = summary.whiteToMove() + summary.blackToMove();
        if (total <= 0L) {
            return "No side-to-move data in valid rows.";
        }
        return "White " + percent(ratio(summary.whiteToMove(), total))
                + " · black " + percent(ratio(summary.blackToMove(), total)) + ".";
    }

    /**
     * Formats a count.
     *
     * @param value count
     * @return formatted count
     */
    private static String format(long value) {
        return String.format(java.util.Locale.ROOT, "%,d", value);
    }

    /**
     * Returns the root exception message.
     *
     * @param ex exception
     * @return message
     */
    private static String rootMessage(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    /**
     * Compact metric card for high-level dataset indicators.
     */
    private static final class MetricTile extends JPanel {

        /**
         * Serialization identifier for Swing component compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Accent role painted on the tile edge. Mutable so quality-critical
         * tiles can flip green/amber to reflect the actual scan result instead
         * of carrying a fixed decorative hue.
         */
        private DatasetChart.Role role;

        /**
         * Metric title.
         */
        private final JLabel titleLabel = new JLabel();

        /**
         * Primary metric value.
         */
        private final JLabel valueLabel = new JLabel("-");

        /**
         * Secondary metric context.
         */
        private final JLabel detailLabel = new JLabel("-");

        /**
         * Whether the pointer is currently over the tile.
         */
        private boolean hover;

        /**
         * Creates a metric tile.
         *
         * @param title metric title
         * @param role accent role
         */
        MetricTile(String title, DatasetChart.Role role) {
            super(new BorderLayout(0, 2));
            this.role = role == null ? DatasetChart.Role.NEUTRAL : role;
            setOpaque(false);
            setBorder(Theme.pad(7, 9, 7, 9));
            titleLabel.setText(title == null ? "" : title);
            Theme.foreground(titleLabel, Theme.ForegroundRole.MUTED);
            titleLabel.setFont(Theme.font(11, Font.BOLD));
            Theme.foreground(valueLabel, Theme.ForegroundRole.TEXT);
            valueLabel.setFont(Theme.font(18, Font.BOLD));
            Theme.foreground(detailLabel, Theme.ForegroundRole.MUTED);
            detailLabel.setFont(Theme.font(11, Font.PLAIN));
            add(titleLabel, BorderLayout.NORTH);
            add(valueLabel, BorderLayout.CENTER);
            add(detailLabel, BorderLayout.SOUTH);
            addMouseListener(new java.awt.event.MouseAdapter() {
                /**
                 * {@inheritDoc}
                 */
                @Override
                public void mouseEntered(java.awt.event.MouseEvent event) {
                    hover = true;
                    repaint();
                }

                /**
                 * {@inheritDoc}
                 */
                @Override
                public void mouseExited(java.awt.event.MouseEvent event) {
                    hover = false;
                    repaint();
                }
            });
        }

        /**
         * Applies a metric value and detail line.
         *
         * @param value primary value
         * @param detail secondary detail
         */
        void setMetric(String value, String detail) {
            valueLabel.setText(value == null || value.isBlank() ? "-" : value);
            detailLabel.setText(detail == null || detail.isBlank() ? "-" : detail);
            setToolTipText(titleLabel.getText() + ": " + valueLabel.getText()
                    + " - " + detailLabel.getText());
            repaint();
        }

        /**
         * Sets a richer hover tooltip (e.g. the analytical insight prose) on the
         * tile and its labels, so it shows wherever the pointer rests on the tile.
         *
         * @param text tooltip text
         */
        void setTooltip(String text) {
            String tip = text == null || text.isBlank() ? null : text;
            setToolTipText(tip);
            titleLabel.setToolTipText(tip);
            valueLabel.setToolTipText(tip);
            detailLabel.setToolTipText(tip);
        }

        /**
         * Sets the accent role painted on the tile edge.
         *
         * @param newRole accent role (treated as neutral when {@code null})
         */
        void setRole(DatasetChart.Role newRole) {
            this.role = newRole == null ? DatasetChart.Role.NEUTRAL : newRole;
            repaint();
        }

        /**
         * Gives the tile a card-like proportion so the metric strip reads as a
         * row of stat cards rather than a thin band of text.
         *
         * @return preferred size
         */
        @Override
        public Dimension getPreferredSize() {
            Dimension base = super.getPreferredSize();
            return new Dimension(Math.max(132, base.width), Math.max(78, base.height));
        }

        /**
         * Paints the tile surface.
         *
         * @param graphics graphics context
         */
        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(Theme.ELEVATED_SOLID);
                g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, Theme.RADIUS, Theme.RADIUS);
                // On hover, warm the border toward the tile's category colour
                // as a quiet affordance that the tile carries a detail tooltip.
                g.setColor(hover ? Theme.lerp(Theme.LINE, roleColor(role), 0.65f) : Theme.LINE);
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, Theme.RADIUS, Theme.RADIUS);
                g.setColor(roleColor(role));
                g.fillRoundRect(0, 0, hover ? 4 : 3, getHeight() - 1, Theme.RADIUS, Theme.RADIUS);
            } finally {
                g.dispose();
            }
            super.paintComponent(graphics);
        }
    }

    /**
     * Returns a themed chart/metric role color.
     *
     * @param role color role
     * @return themed color
     */
    private static Color roleColor(DatasetChart.Role role) {
        return switch (role == null ? DatasetChart.Role.NEUTRAL : role) {
            case SUCCESS -> Theme.STATUS_SUCCESS_BORDER;
            case WARNING -> Theme.STATUS_WARNING_BORDER;
            case ERROR -> Theme.STATUS_ERROR_BORDER;
            case PURPLE -> Theme.NN_POLICY;
            case NEUTRAL -> Theme.MUTED;
            case ACCENT -> Theme.ACCENT;
        };
    }

    /**
     * Renderer for compact chip-like table label cells.
     */
    private static final class ChipCellRenderer extends DefaultTableCellRenderer {

        /**
         * Serialization identifier for Swing component compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * {@inheritDoc}
         */
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                boolean focused, int row, int column) {
            Component component = super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            String text = value == null ? "" : value.toString();
            setText(compactText(text, 42));
            setToolTipText(text.isBlank() ? null : text);
            setFont(Theme.font(11, Font.BOLD));
            if (!selected) {
                setForeground(text.isBlank() ? Theme.MUTED : Theme.TEXT);
                setBackground(table.getBackground());
            }
            return component;
        }
    }

    /**
     * Renderer that keeps long FEN/raw cells readable without blowing out table width.
     */
    private static final class FenCellRenderer extends DefaultTableCellRenderer {

        /**
         * Serialization identifier for Swing component compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * {@inheritDoc}
         */
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                boolean focused, int row, int column) {
            Component component = super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            String text = value == null ? "" : value.toString();
            setText(compactText(text, 96));
            setToolTipText(text.isBlank() ? null : text);
            setFont(Theme.mono(12));
            if (!selected) {
                setForeground(text.isBlank() ? Theme.MUTED : Theme.TEXT);
                setBackground(table.getBackground());
            }
            return component;
        }
    }
}
