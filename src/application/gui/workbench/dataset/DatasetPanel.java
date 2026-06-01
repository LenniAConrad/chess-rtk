package application.gui.workbench.dataset;

import static application.gui.workbench.ui.Ui.button;
import static application.gui.workbench.ui.Ui.caption;
import static application.gui.workbench.ui.Ui.fillViewport;
import static application.gui.workbench.ui.Ui.label;
import static application.gui.workbench.ui.Ui.placeholder;
import static application.gui.workbench.ui.Ui.scroll;
import static application.gui.workbench.ui.Ui.styleFields;
import static application.gui.workbench.ui.Ui.styleProgressBar;
import static application.gui.workbench.ui.Ui.titled;
import static application.gui.workbench.ui.Ui.transparentPanel;

import application.gui.workbench.layout.SplitPaneStyler;
import application.gui.workbench.ui.FileDialogs;
import application.gui.workbench.ui.SurfacePanel;
import application.gui.workbench.ui.Theme;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
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
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableColumnModel;

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
    private final JButton stopButton = button("Stop", false, event -> cancelAnalysis());

    /**
     * Copy report action button.
     */
    private final JButton copyReportButton = button("Copy Report", false, event -> copyReport());

    /**
     * Status label.
     */
    private final JLabel statusLabel = caption("No dataset loaded");

    /**
     * Activity indicator shown while scanning.
     */
    private final JProgressBar progress = new JProgressBar();

    /**
     * Source-size metric tile.
     */
    private final MetricTile filesMetric = new MetricTile("source", DatasetChart.Role.NEUTRAL);

    /**
     * Row-count metric tile.
     */
    private final MetricTile rowsMetric = new MetricTile("rows", DatasetChart.Role.ACCENT);

    /**
     * Validity metric tile.
     */
    private final MetricTile validMetric = new MetricTile("validity", DatasetChart.Role.SUCCESS);

    /**
     * Duplicate-position metric tile.
     */
    private final MetricTile duplicateMetric = new MetricTile("uniqueness", DatasetChart.Role.WARNING);

    /**
     * Tag-coverage metric tile.
     */
    private final MetricTile tagsMetric = new MetricTile("tags", DatasetChart.Role.PURPLE);

    /**
     * Evaluation-coverage metric tile.
     */
    private final MetricTile evalMetric = new MetricTile("scores", DatasetChart.Role.ACCENT);

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
     * Quality chart.
     */
    private final DatasetChart qualityChart = new DatasetChart();

    /**
     * Side-to-move chart.
     */
    private final DatasetChart sideChart = new DatasetChart();

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
     * Issue table.
     */
    private final JTable issueTable = new JTable(issueModel);

    /**
     * Active background worker.
     */
    private transient SwingWorker<DatasetSummary, Void> worker;

    /**
     * Last completed summary.
     */
    private DatasetSummary summary = DatasetSummary.empty();

    /**
     * Creates the dataset panel.
     */
    public DatasetPanel() {
        super(new BorderLayout(0, 0));
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
        SurfacePanel page = new SurfacePanel(new BorderLayout(0, Theme.SPACE_MD));
        page.add(createToolbar(), BorderLayout.NORTH);
        page.add(createBody(), BorderLayout.CENTER);
        add(scroll(fillViewport(page)), BorderLayout.CENTER);
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
        JButton browse = button("Browse", false, event -> chooseDatasetPath());
        JPanel sourceRow = transparentPanel(new BorderLayout(Theme.SPACE_SM, 0));
        sourceRow.add(sourceField, BorderLayout.CENTER);
        sourceRow.add(browse, BorderLayout.EAST);

        JPanel controls = transparentPanel(new FlowLayout(FlowLayout.LEFT, Theme.SPACE_SM, 0));
        styleFields(rowLimitField);
        rowLimitField.setColumns(10);
        rowLimitField.setToolTipText("Maximum rows to scan");
        rowLimitField.setPreferredSize(new Dimension(120, Theme.CONTROL_HEIGHT));
        controls.add(label("row limit"));
        controls.add(rowLimitField);
        controls.add(analyzeButton);
        controls.add(stopButton);
        controls.add(copyReportButton);

        JPanel wrapper = transparentPanel(new BorderLayout(0, Theme.SPACE_SM));
        JPanel toolbar = transparentPanel(new BorderLayout(0, Theme.SPACE_SM));
        toolbar.add(sourceRow, BorderLayout.NORTH);
        toolbar.add(controls, BorderLayout.CENTER);
        wrapper.add(toolbar, BorderLayout.CENTER);
        JPanel status = transparentPanel(new BorderLayout(Theme.SPACE_SM, 0));
        progress.setIndeterminate(true);
        styleProgressBar(progress);
        status.add(statusLabel, BorderLayout.CENTER);
        status.add(progress, BorderLayout.EAST);
        wrapper.add(status, BorderLayout.SOUTH);
        return wrapper;
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
        split.setResizeWeight(0.58d);
        split.setDividerLocation(0.58d);
        SplitPaneStyler.style(split);
        return split;
    }

    /**
     * Creates metric and chart sections.
     *
     * @return overview component
     */
    private JComponent createOverview() {
        JPanel overview = transparentPanel(new BorderLayout(0, Theme.SPACE_MD));
        JPanel top = transparentPanel(new BorderLayout(0, Theme.SPACE_SM));
        top.add(createMetrics(), BorderLayout.NORTH);
        top.add(createInsights(), BorderLayout.CENTER);
        overview.add(top, BorderLayout.NORTH);
        JPanel charts = transparentPanel(new GridLayout(2, 3, Theme.SPACE_MD, Theme.SPACE_MD));
        charts.add(titled("Dataset Health", qualityChart));
        charts.add(titled("Side Balance", sideChart));
        charts.add(titled("Material Bands", materialChart));
        charts.add(titled("Score Bands", evalChart));
        charts.add(titled("Top Tags", tagChart));
        charts.add(titled("Engine Sources", engineChart));
        overview.add(charts, BorderLayout.CENTER);
        return overview;
    }

    /**
     * Creates the metric strip.
     *
     * @return metrics component
     */
    private JComponent createMetrics() {
        JPanel metrics = transparentPanel(new GridLayout(1, 7, Theme.SPACE_SM, 0));
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
     * Creates the analytical insight strip.
     *
     * @return insight strip
     */
    private JComponent createInsights() {
        JPanel insights = transparentPanel(new GridLayout(1, 4, Theme.SPACE_SM, 0));
        insights.add(insight("Quality", qualityInsight));
        insights.add(insight("Coverage", coverageInsight));
        insights.add(insight("Balance", balanceInsight));
        insights.add(insight("Material", materialInsight));
        return insights;
    }

    /**
     * Creates one insight cell.
     *
     * @param title insight title
     * @param value insight text
     * @return insight component
     */
    private static JComponent insight(String title, JLabel value) {
        JPanel panel = transparentPanel(new BorderLayout(0, 2));
        panel.setBorder(Theme.pad(5, 7, 5, 7));
        panel.add(caption(title), BorderLayout.NORTH);
        panel.add(value, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Creates the sample and issue table area.
     *
     * @return table component
     */
    private JComponent createTables() {
        configureTable(sampleTable);
        configureTable(issueTable);
        JTabbedPane tabs = application.gui.workbench.ui.Ui.tabbedPane();
        tabs.addTab("Samples", tableScroll(sampleTable));
        tabs.addTab("Issues", tableScroll(issueTable));
        return tabs;
    }

    /**
     * Wraps a table in a styled scroll pane.
     *
     * @param table table
     * @return scroll pane
     */
    private static JScrollPane tableScroll(JTable table) {
        JScrollPane pane = scroll(table);
        pane.setPreferredSize(new Dimension(560, 210));
        return pane;
    }

    /**
     * Applies table styling and column widths.
     *
     * @param table table
     */
    private static void configureTable(JTable table) {
        Theme.table(table, TABLE_ROW_HEIGHT);
        table.setAutoCreateRowSorter(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        TableColumnModel columns = table.getColumnModel();
        setColumnWidth(columns, 0, 120);
        setColumnWidth(columns, 1, 64);
        setColumnWidth(columns, 2, 70);
        setColumnWidth(columns, 3, 70);
        setColumnWidth(columns, 4, 82);
        setColumnWidth(columns, 5, 150);
        setColumnWidth(columns, 6, 420);
        if (columns.getColumnCount() > 7) {
            setColumnWidth(columns, 7, 170);
        }
    }

    /**
     * Sets preferred table column width.
     *
     * @param columns column model
     * @param index column index
     * @param width preferred width
     */
    private static void setColumnWidth(TableColumnModel columns, int index, int width) {
        if (index < columns.getColumnCount()) {
            columns.getColumn(index).setPreferredWidth(width);
        }
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
        updateMetrics();
        updateCharts();
        sampleModel.setRows(summary.samples());
        issueModel.setRows(summary.issues());
        copyReportButton.setEnabled(hasReport());
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
        duplicateMetric.setMetric(summary.validPositions() == 0L ? "-" : percent(1.0d - summary.duplicateRatio()),
                format(summary.duplicatePositions()) + " duplicate FENs");
        tagsMetric.setMetric(summary.validPositions() == 0L ? "-" : percent(ratio(summary.withTags(), summary.validPositions())),
                format(summary.withTags()) + " tagged rows");
        evalMetric.setMetric(summary.validPositions() == 0L ? "-" : percent(ratio(summary.withEval(), summary.validPositions())),
                format(summary.withEval()) + " scored rows");
        materialMetric.setMetric(summary.validPositions() == 0L ? "-" : Math.round(summary.averageMaterial()) + " cp",
                materialRangeText());
        updateInsights();
    }

    /**
     * Updates analytical insight text.
     */
    private void updateInsights() {
        if (summary.rows() == 0L) {
            setInsight(qualityInsight, "Load a dataset to profile row quality.", Theme.ForegroundRole.MUTED);
            setInsight(coverageInsight, "Tags and scores will be measured per valid row.", Theme.ForegroundRole.MUTED);
            setInsight(balanceInsight, "Side-to-move balance will appear after scanning.", Theme.ForegroundRole.MUTED);
            setInsight(materialInsight, "Material range will show opening/endgame spread.", Theme.ForegroundRole.MUTED);
            return;
        }
        boolean clean = summary.invalidRows() == 0L && summary.duplicatePositions() == 0L;
        setInsight(qualityInsight, clean
                        ? "Clean scan: no invalid rows or duplicate FENs."
                        : format(summary.invalidRows()) + " invalid, "
                                + format(summary.duplicatePositions()) + " duplicate.",
                clean ? Theme.ForegroundRole.SUCCESS : Theme.ForegroundRole.WARNING);
        setInsight(coverageInsight,
                "Tags " + percent(ratio(summary.withTags(), summary.validPositions()))
                        + " · scores " + percent(ratio(summary.withEval(), summary.validPositions())) + ".",
                coverageRole());
        setInsight(balanceInsight, sideBalanceText(), sideBalanceRole());
        setInsight(materialInsight,
                summary.validPositions() == 0L ? "No valid positions to profile."
                        : "Avg " + Math.round(summary.averageMaterial()) + " cp · " + materialRangeText() + ".",
                Theme.ForegroundRole.INFO);
    }

    /**
     * Updates chart components.
     */
    private void updateCharts() {
        qualityChart.setBars(List.of(
                new DatasetChart.Bar("unique valid", Math.max(0L,
                        summary.validPositions() - summary.duplicatePositions()), DatasetChart.Role.SUCCESS),
                new DatasetChart.Bar("duplicate valid", summary.duplicatePositions(), DatasetChart.Role.WARNING),
                new DatasetChart.Bar("invalid row", summary.invalidRows(), DatasetChart.Role.ERROR)));
        sideChart.setBars(List.of(
                new DatasetChart.Bar("white to move", summary.whiteToMove(), DatasetChart.Role.ACCENT),
                new DatasetChart.Bar("black to move", summary.blackToMove(), DatasetChart.Role.PURPLE)));
        materialChart.setBuckets(MATERIAL_LABELS, summary.materialBuckets(), DatasetChart.Role.ACCENT);
        evalChart.setBuckets(EVAL_LABELS, summary.evalBuckets(), DatasetChart.Role.PURPLE);
        tagChart.setBars(namedBars(summary.topTags(), DatasetChart.Role.PURPLE));
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
        analyzeButton.setEnabled(!busy);
        stopButton.setEnabled(busy);
        copyReportButton.setEnabled(!busy && hasReport());
        progress.setVisible(busy);
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
     * Applies insight text and semantic color.
     *
     * @param label target label
     * @param text insight text
     * @param role foreground role
     */
    private static void setInsight(JLabel label, String text, Theme.ForegroundRole role) {
        label.setText(text == null ? "" : text);
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
     * Returns the semantic role for side-balance insight.
     *
     * @return foreground role
     */
    private Theme.ForegroundRole sideBalanceRole() {
        long total = summary.whiteToMove() + summary.blackToMove();
        if (total <= 0L) {
            return Theme.ForegroundRole.MUTED;
        }
        double whiteRatio = ratio(summary.whiteToMove(), total);
        return Math.abs(whiteRatio - 0.5d) <= 0.15d
                ? Theme.ForegroundRole.SUCCESS : Theme.ForegroundRole.WARNING;
    }

    /**
     * Returns the semantic role for metadata coverage.
     *
     * @return foreground role
     */
    private Theme.ForegroundRole coverageRole() {
        if (summary.validPositions() == 0L) {
            return Theme.ForegroundRole.MUTED;
        }
        double coverage = (ratio(summary.withTags(), summary.validPositions())
                + ratio(summary.withEval(), summary.validPositions())) / 2.0d;
        if (coverage >= 0.75d) {
            return Theme.ForegroundRole.SUCCESS;
        }
        return coverage >= 0.35d ? Theme.ForegroundRole.WARNING : Theme.ForegroundRole.MUTED;
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
         * Accent role painted on the tile edge.
         */
        private final DatasetChart.Role role;

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
}
