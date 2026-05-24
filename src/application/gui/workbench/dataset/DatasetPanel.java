/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package application.gui.workbench.dataset;

import static application.gui.workbench.ui.Ui.button;
import static application.gui.workbench.ui.Ui.caption;
import static application.gui.workbench.ui.Ui.fillViewport;
import static application.gui.workbench.ui.Ui.label;
import static application.gui.workbench.ui.Ui.placeholder;
import static application.gui.workbench.ui.Ui.scroll;
import static application.gui.workbench.ui.Ui.styleFields;
import static application.gui.workbench.ui.Ui.styleIntegerSpinner;
import static application.gui.workbench.ui.Ui.styleProgressBar;
import static application.gui.workbench.ui.Ui.titled;
import static application.gui.workbench.ui.Ui.transparentPanel;

import application.gui.workbench.ui.FileDialogs;
import application.gui.workbench.ui.SurfacePanel;
import application.gui.workbench.ui.Theme;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
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
     * Row-limit spinner step.
     */
    private static final int ROW_LIMIT_STEP = 1_000;

    /**
     * Standard row height for dataset tables.
     */
    private static final int TABLE_ROW_HEIGHT = 25;

    /**
     * Labels used by the material histogram.
     */
    private static final String[] MATERIAL_LABELS = {
        "0-999", "1k", "2k", "3k", "4k", "5k", "6k", "7k+"
    };

    /**
     * Labels used by the evaluation histogram.
     */
    private static final String[] EVAL_LABELS = {
        "-900", "-600", "-300", "0", "+300", "+600", "+900"
    };

    /**
     * Source path text field.
     */
    private final JTextField sourceField = new JTextField();

    /**
     * Row scan limit control.
     */
    private final JSpinner rowLimitSpinner = new JSpinner(
            new SpinnerNumberModel(DEFAULT_ROW_LIMIT, MIN_ROW_LIMIT, MAX_ROW_LIMIT, ROW_LIMIT_STEP));

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
     * File-count metric label.
     */
    private final JLabel filesValue = metricValue();

    /**
     * Row-count metric label.
     */
    private final JLabel rowsValue = metricValue();

    /**
     * Valid-position metric label.
     */
    private final JLabel validValue = metricValue();

    /**
     * Duplicate-position metric label.
     */
    private final JLabel duplicateValue = metricValue();

    /**
     * Average-material metric label.
     */
    private final JLabel materialValue = metricValue();

    /**
     * Tagged-row metric label.
     */
    private final JLabel tagsValue = metricValue();

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
        startAnalysis(Path.of(text), ((Number) rowLimitSpinner.getValue()).longValue());
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
        JPanel toolbar = transparentPanel(new BorderLayout(Theme.SPACE_SM, 0));
        styleFields(sourceField);
        sourceField.setToolTipText("Dataset file or directory");
        placeholder(sourceField, "Dataset file or directory");
        toolbar.add(sourceField, BorderLayout.CENTER);

        JPanel controls = transparentPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SPACE_SM, 0));
        JButton browse = button("Browse", false, event -> chooseDatasetPath());
        styleIntegerSpinner(rowLimitSpinner);
        rowLimitSpinner.setPreferredSize(new Dimension(118, Theme.CONTROL_HEIGHT));
        controls.add(label("row limit"));
        controls.add(rowLimitSpinner);
        controls.add(browse);
        controls.add(analyzeButton);
        controls.add(stopButton);
        controls.add(copyReportButton);
        toolbar.add(controls, BorderLayout.EAST);

        JPanel wrapper = transparentPanel(new BorderLayout(0, Theme.SPACE_SM));
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
     * Creates the main analysis body.
     *
     * @return body component
     */
    private JComponent createBody() {
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, createOverview(), createTables());
        split.setBorder(null);
        split.setResizeWeight(0.58d);
        split.setContinuousLayout(true);
        return split;
    }

    /**
     * Creates metric and chart sections.
     *
     * @return overview component
     */
    private JComponent createOverview() {
        JPanel overview = transparentPanel(new BorderLayout(0, Theme.SPACE_MD));
        overview.add(createMetrics(), BorderLayout.NORTH);
        JPanel charts = transparentPanel(new GridLayout(2, 3, Theme.SPACE_MD, Theme.SPACE_MD));
        charts.add(titled("Quality", qualityChart));
        charts.add(titled("Side To Move", sideChart));
        charts.add(titled("Material", materialChart));
        charts.add(titled("Evaluation", evalChart));
        charts.add(titled("Tags", tagChart));
        charts.add(titled("Engines", engineChart));
        overview.add(charts, BorderLayout.CENTER);
        return overview;
    }

    /**
     * Creates the metric strip.
     *
     * @return metrics component
     */
    private JComponent createMetrics() {
        JPanel metrics = transparentPanel(new GridLayout(1, 6, Theme.SPACE_SM, 0));
        metrics.add(metric("files", filesValue));
        metrics.add(metric("rows", rowsValue));
        metrics.add(metric("valid", validValue));
        metrics.add(metric("duplicates", duplicateValue));
        metrics.add(metric("avg material", materialValue));
        metrics.add(metric("tagged", tagsValue));
        return metrics;
    }

    /**
     * Creates one metric cell.
     *
     * @param title metric title
     * @param value value label
     * @return metric component
     */
    private JComponent metric(String title, JLabel value) {
        JPanel panel = transparentPanel(new BorderLayout(0, 2));
        panel.setBorder(Theme.pad(4, 6, 4, 6));
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
     * Creates a metric value label.
     *
     * @return label
     */
    private static JLabel metricValue() {
        JLabel value = new JLabel("-");
        Theme.foreground(value, Theme.ForegroundRole.TEXT);
        value.setFont(Theme.font(18, java.awt.Font.BOLD));
        return value;
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
        filesValue.setText(format(summary.scannedFiles()));
        rowsValue.setText(format(summary.rows()));
        validValue.setText(format(summary.validPositions()));
        duplicateValue.setText(format(summary.duplicatePositions()));
        materialValue.setText(summary.validPositions() == 0L ? "-" : Math.round(summary.averageMaterial()) + " cp");
        tagsValue.setText(format(summary.withTags()));
    }

    /**
     * Updates chart components.
     */
    private void updateCharts() {
        qualityChart.setBars(List.of(
                new DatasetChart.Bar("valid", summary.validPositions(), DatasetChart.Role.SUCCESS),
                new DatasetChart.Bar("invalid", summary.invalidRows(), DatasetChart.Role.ERROR),
                new DatasetChart.Bar("duplicate", summary.duplicatePositions(), DatasetChart.Role.WARNING)));
        sideChart.setBars(List.of(
                new DatasetChart.Bar("white", summary.whiteToMove(), DatasetChart.Role.ACCENT),
                new DatasetChart.Bar("black", summary.blackToMove(), DatasetChart.Role.PURPLE)));
        materialChart.setBuckets(MATERIAL_LABELS, summary.materialBuckets(), DatasetChart.Role.ACCENT);
        evalChart.setBuckets(EVAL_LABELS, summary.evalBuckets(), DatasetChart.Role.PURPLE);
        tagChart.setBars(namedBars(summary.topTags(), DatasetChart.Role.PURPLE));
        engineChart.setBars(namedBars(summary.topEngines(), DatasetChart.Role.NEUTRAL));
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
}
