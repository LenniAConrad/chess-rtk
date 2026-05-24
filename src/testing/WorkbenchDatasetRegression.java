/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package testing;

import application.cli.PathOps;
import static testing.WorkbenchTestSupport.START_FEN;
import static testing.WorkbenchTestSupport.assertEquals;
import static testing.WorkbenchTestSupport.assertFalse;
import static testing.WorkbenchTestSupport.assertPaintsOpaqueCorner;
import static testing.WorkbenchTestSupport.assertTrue;
import static testing.WorkbenchTestSupport.field;
import static testing.WorkbenchTestSupport.paint;

import application.gui.workbench.dataset.DatasetAnalyzer;
import application.gui.workbench.dataset.DatasetChart;
import application.gui.workbench.dataset.DatasetPanel;
import application.gui.workbench.dataset.DatasetSummary;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JTextField;

/**
 * Regression checks for the Workbench Datasets tab.
 */
final class WorkbenchDatasetRegression {

    /**
     * Secondary legal FEN used for dataset balance checks.
     */
    private static final String BLACK_TO_MOVE_FEN =
            "8/8/8/8/8/8/K7/7k b - - 1 1";

    /**
     * Prevents instantiation.
     */
    private WorkbenchDatasetRegression() {
        // utility
    }

    /**
     * Runs all dataset regressions.
     */
    static void run() {
        testAnalyzerSummarizesFenJsonAndIssues();
        testAnalyzerStreamsJsonArraysInDirectories();
        testDatasetPanelAndChartsPaint();
    }

    /**
     * Verifies mixed line-based datasets are parsed into useful summary counts.
     */
    private static void testAnalyzerSummarizesFenJsonAndIssues() {
        Path file = tempDataset("mixed", ".txt", String.join(System.lineSeparator(),
                START_FEN,
                "{\"position\":\"" + BLACK_TO_MOVE_FEN
                        + "\",\"tags\":[\"mate\",\"tactic\"],\"engine\":\"stockfish\","
                        + "\"analysis\":[\"info depth 4 score cp 35 nodes 10\"]}",
                "not a fen",
                START_FEN));

        DatasetSummary summary = analyze(file, 100L);
        assertEquals(Long.valueOf(4L), Long.valueOf(summary.rows()), "dataset rows");
        assertEquals(Long.valueOf(3L), Long.valueOf(summary.validPositions()), "dataset valid rows");
        assertEquals(Long.valueOf(1L), Long.valueOf(summary.invalidRows()), "dataset invalid rows");
        assertEquals(Long.valueOf(1L), Long.valueOf(summary.duplicatePositions()), "dataset duplicate rows");
        assertEquals(Long.valueOf(2L), Long.valueOf(summary.whiteToMove()), "dataset white rows");
        assertEquals(Long.valueOf(1L), Long.valueOf(summary.blackToMove()), "dataset black rows");
        assertEquals(Long.valueOf(1L), Long.valueOf(summary.withTags()), "dataset tagged rows");
        assertEquals(Long.valueOf(1L), Long.valueOf(summary.withEval()), "dataset scored rows");
        assertFalse(summary.topTags().isEmpty(), "dataset tag frequencies");
        assertEquals("mate", summary.topTags().get(0).name(), "dataset top tag");
        assertFalse(summary.samples().isEmpty(), "dataset sample rows");
        assertFalse(summary.issues().isEmpty(), "dataset issue rows");
    }

    /**
     * Verifies directory scans include JSON arrays and honor row limits.
     */
    private static void testAnalyzerStreamsJsonArraysInDirectories() {
        Path dir = tempDirectory("dataset-dir");
        tempDataset(dir, "records.json", "[{\"fen\":\"" + START_FEN + "\",\"tag\":\"opening\"},"
                + "{\"fen\":\"" + BLACK_TO_MOVE_FEN + "\",\"tag\":\"endgame\"}]");
        tempDataset(dir, "ignored.bin", "not scanned");

        DatasetSummary summary = analyze(dir, 1L);
        assertEquals(Integer.valueOf(1), Integer.valueOf(summary.scannedFiles()), "dataset supported files");
        assertEquals(Long.valueOf(1L), Long.valueOf(summary.rows()), "dataset row limit rows");
        assertTrue(summary.truncated(), "dataset row limit truncates");
    }

    /**
     * Verifies the panel and chart render without theme holes.
     */
    private static void testDatasetPanelAndChartsPaint() {
        DatasetPanel panel = new DatasetPanel();
        JButton copy = (JButton) field(panel, "copyReportButton");
        JTextField rowLimit = (JTextField) field(panel, "rowLimitField");
        JTextField source = (JTextField) field(panel, "sourceField");
        assertFalse(copy.isEnabled(), "copy report disabled before a scan");
        assertTrue(rowLimit.getPreferredSize().width >= 108, "dataset row-limit field stays legible");
        assertEquals("50,000", rowLimit.getText(),
                "dataset row-limit value uses readable grouping");
        assertEquals("Dataset file or directory", source.getToolTipText(), "dataset source tooltip");
        panel.applySummary(new DatasetSummary(Path.of("sample.fen"), 1, 2L, 2L, 0L, 0L, 1L, 1L,
                0L, 0L, 0L, 1L, 1L, 0, 8_000, 4_000.0d,
                new int[] { 0, 0, 0, 0, 1, 0, 0, 1 },
                new int[] { 0, 0, 1, 0, 1, 0, 0 },
                List.of(new DatasetSummary.NamedCount("opening", 1L)),
                List.of(new DatasetSummary.NamedCount("stockfish", 1L)),
                List.of(new DatasetSummary.SampleRow("sample.fen", 1L, "FEN", START_FEN,
                        "white", 8_000, "opening", "")),
                List.of(), false, "Scan complete"));
        assertTrue(copy.isEnabled(), "copy report enabled after a scan");
        paintPanel(panel, 980, 680, "dataset panel paints surface");

        DatasetChart chart = new DatasetChart();
        chart.setBars(List.of(new DatasetChart.Bar("valid", 3L, DatasetChart.Role.SUCCESS),
                new DatasetChart.Bar("invalid", 1L, DatasetChart.Role.ERROR)));
        paintPanel(chart, 340, 160, "dataset chart paints surface");
    }

    /**
     * Runs the analyzer and wraps checked failures.
     *
     * @param source source path
     * @param limit row limit
     * @return summary
     */
    private static DatasetSummary analyze(Path source, long limit) {
        try {
            return DatasetAnalyzer.analyze(source, limit);
        } catch (java.io.IOException ex) {
            throw new AssertionError("dataset analysis failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Writes a temporary dataset file.
     *
     * @param prefix filename prefix
     * @param suffix filename suffix
     * @param content file content
     * @return file path
     */
    private static Path tempDataset(String prefix, String suffix, String content) {
        try {
            Path file = PathOps.createLocalTempFile(prefix, suffix);
            Files.writeString(file, content);
            return file;
        } catch (java.io.IOException ex) {
            throw new AssertionError("failed to write dataset fixture", ex);
        }
    }

    /**
     * Writes a temporary dataset file inside a directory.
     *
     * @param dir directory
     * @param name filename
     * @param content file content
     */
    private static void tempDataset(Path dir, String name, String content) {
        try {
            Files.writeString(dir.resolve(name), content);
        } catch (java.io.IOException ex) {
            throw new AssertionError("failed to write dataset fixture", ex);
        }
    }

    /**
     * Creates a temporary directory.
     *
     * @param prefix directory prefix
     * @return directory path
     */
    private static Path tempDirectory(String prefix) {
        try {
            return PathOps.createLocalTempDirectory(prefix);
        } catch (java.io.IOException ex) {
            throw new AssertionError("failed to create dataset fixture directory", ex);
        }
    }

    /**
     * Paints a panel and asserts the corner is opaque.
     *
     * @param component component
     * @param width width
     * @param height height
     * @param message assertion message
     */
    private static void paintPanel(JComponent component, int width, int height, String message) {
        component.setSize(width, height);
        paint(component, width, height);
        assertPaintsOpaqueCorner(component, width, height, message);
    }
}
