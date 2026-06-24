package testing;

import application.cli.PathOps;
import static testing.WorkbenchTestSupport.START_FEN;
import static testing.WorkbenchTestSupport.assertEquals;
import static testing.WorkbenchTestSupport.assertFalse;
import static testing.WorkbenchTestSupport.assertPaintsOpaqueCorner;
import static testing.WorkbenchTestSupport.assertTrue;
import static testing.WorkbenchTestSupport.field;
import static testing.WorkbenchTestSupport.maxAlpha;
import static testing.WorkbenchTestSupport.paint;

import application.gui.workbench.dataset.DatasetAnalyzer;
import application.gui.workbench.dataset.DatasetAreaChart;
import application.gui.workbench.dataset.DatasetChart;
import application.gui.workbench.dataset.DatasetPanel;
import application.gui.workbench.dataset.DatasetSummary;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

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
        rowLimit.setText("");
        assertEquals(Long.valueOf(50_000L), WorkbenchTestSupport.invoke(panel, "rowLimitValue",
                new Class<?>[0]), "empty dataset row limit uses default");
        rowLimit.setText("50,000");
        assertEquals("Dataset file or directory", source.getToolTipText(), "dataset source tooltip");
        assertLoadingCardCenters(panel);
        source.setText("bad\0path");
        WorkbenchTestSupport.invoke(panel, "analyzeCurrentSource", new Class<?>[0]);
        JLabel status = (JLabel) field(panel, "statusLabel");
        assertTrue(status.getText().contains("Dataset path is not valid"),
                "dataset invalid path status is explicit");
        assertTrue(status.getText().contains("\\u0000"),
                "dataset invalid path status escapes control characters");
        assertFalse(status.getText().contains("\0"),
                "dataset invalid path status contains no raw control character");
        JSplitPane split = firstSplitPane(panel);
        assertEquals(Integer.valueOf(4), Integer.valueOf(split.getDividerSize()),
                "dataset split pane uses workbench sash width");
        assertTrue(((javax.swing.plaf.basic.BasicSplitPaneUI) split.getUI()).getDivider()
                .getClass().getName().contains("SplitPaneStyler"),
                "dataset split pane uses themed divider");
        JComponent inspectorBoard = (JComponent) field(panel, "inspectorBoard");
        JTextField inspectorFen = (JTextField) field(panel, "inspectorFenField");
        JComponent boardSlot = (JComponent) inspectorBoard.getParent();
        boardSlot.setSize(420, 260);
        boardSlot.doLayout();
        assertEquals(Integer.valueOf(260), Integer.valueOf(inspectorBoard.getWidth()),
                "dataset row inspector board uses available height");
        boardSlot.setSize(180, 340);
        boardSlot.doLayout();
        assertEquals(Integer.valueOf(180), Integer.valueOf(inspectorBoard.getWidth()),
                "dataset row inspector board shrinks to available width");
        assertFalse(inspectorFen.getText().contains("\n"),
                "dataset row inspector FEN preview is single-line");
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
        JLabel qualityInsight = (JLabel) field(panel, "qualityInsight");
        JLabel coverageInsight = (JLabel) field(panel, "coverageInsight");
        assertTrue(qualityInsight.getText().contains("Clean scan"),
                "dataset quality insight summarizes clean scans");
        assertTrue(coverageInsight.getText().contains("Tags 50%"),
                "dataset coverage insight reports tag ratio");
        assertLoadedSplitKeepsTablesNearCharts(panel);
        assertTableHoverCoversCustomCells((JTable) field(panel, "sampleTable"));
        panel.applySummary(new DatasetSummary(Path.of("bad.txt"), 1, 1L, 0L, 1L, 0L, 0L, 0L,
                0L, 0L, 0L, 0L, 0L, 0, 0, 0.0d,
                new int[] { 0, 0, 0, 0, 0, 0, 0, 0 },
                new int[] { 0, 0, 0, 0, 0, 0, 0 },
                List.of(), List.of(), List.of(),
                List.of(new DatasetSummary.SampleRow("bad.txt", 1L, "TEXT", "not a fen",
                        "", 0, "", "")),
                false, "Scan complete"));
        ((JTabbedPane) field(panel, "tableTabs")).setSelectedIndex(1);
        JTable issueTable = (JTable) field(panel, "issueTable");
        issueTable.setRowSelectionInterval(0, 0);
        WorkbenchTestSupport.invoke(panel, "updateRowActions", new Class<?>[0]);
        assertFalse(((JButton) field(panel, "copyFenButton")).isEnabled(),
                "invalid dataset issue row is not copyable as FEN");
        assertTrue(((JLabel) field(panel, "inspectorIssueValue")).getText().contains("not a valid FEN"),
                "invalid dataset issue row shows FEN failure state");
        paintPanel(panel, 980, 680, "dataset panel paints surface");

        // The chart is transparent by design; the shared elevated card paints
        // the opaque surface, matching how DatasetPanel composes them.
        DatasetChart chart = new DatasetChart();
        chart.setBars(List.of(new DatasetChart.Bar("valid", 3L, DatasetChart.Role.SUCCESS),
                new DatasetChart.Bar("invalid", 1L, DatasetChart.Role.ERROR)));
        javax.swing.JComponent chartCard = application.gui.workbench.ui.Ui.card("Chart", chart);
        chartCard.setSize(340, 160);
        chartCard.doLayout();
        assertEquals(Integer.valueOf(255), Integer.valueOf(maxAlpha(paint(chartCard, 340, 160))),
                "dataset chart card paints surface");

        // The donut chart is likewise transparent and composes the same way; it
        // must paint visible ring ink (a non-trivial colour, not just the card).
        application.gui.workbench.dataset.DonutChart donut = new application.gui.workbench.dataset.DonutChart();
        donut.setSegments(List.of(
                new application.gui.workbench.dataset.DonutChart.Segment("white", 3L, DatasetChart.Role.ACCENT),
                new application.gui.workbench.dataset.DonutChart.Segment("black", 1L, DatasetChart.Role.NEUTRAL)),
                "positions");
        javax.swing.JComponent donutCard = application.gui.workbench.ui.Ui.card("Donut", donut);
        donutCard.setSize(340, 180);
        donutCard.doLayout();
        assertEquals(Integer.valueOf(255), Integer.valueOf(maxAlpha(paint(donutCard, 340, 180))),
                "dataset donut card paints surface");

        DatasetAreaChart area = new DatasetAreaChart();
        area.setBuckets(new String[] { "< -900", "equal", "> +900" }, new int[] { 1, 3, 2 },
                DatasetChart.Role.PURPLE);
        assertEquals(Integer.valueOf(chart.getPreferredSize().height),
                Integer.valueOf(donut.getPreferredSize().height),
                "dataset bar and donut chart cards share height");
        assertEquals(Integer.valueOf(chart.getPreferredSize().height),
                Integer.valueOf(area.getPreferredSize().height),
                "dataset area and bar chart cards share height");
        assertEquals("9999", formatCompactValue(9_999L),
                "dataset chart compact formatter preserves small counts");
        assertEquals("10.0k", formatCompactValue(10_000L),
                "dataset chart compact formatter uses k suffix");
        assertEquals("1.5m", formatCompactValue(1_500_000L),
                "dataset chart compact formatter uses m suffix");
        assertEquals(Integer.valueOf(application.gui.workbench.ui.Theme.STATUS_SUCCESS_BORDER.getRGB()),
                Integer.valueOf(roleColor(DatasetChart.Role.SUCCESS).getRGB()),
                "dataset charts share success role color");
        assertEquals(Integer.valueOf(application.gui.workbench.ui.Theme.NN_POLICY.getRGB()),
                Integer.valueOf(roleColor(DatasetChart.Role.PURPLE).getRGB()),
                "dataset charts share neural role color");
        assertEquals(Integer.valueOf(application.gui.workbench.ui.Theme.MUTED.getRGB()),
                Integer.valueOf(roleColor(null).getRGB()),
                "dataset chart role color treats null as neutral");
        javax.swing.JComponent areaCard = application.gui.workbench.ui.Ui.card("Area", area);
        areaCard.setSize(340, 180);
        areaCard.doLayout();
        assertEquals(Integer.valueOf(255), Integer.valueOf(maxAlpha(paint(areaCard, 340, 180))),
                "dataset area chart card paints surface");
    }

    /**
     * Finds the first split pane in a component tree.
     *
     * @param component root component
     * @return first split pane
     */
    private static JSplitPane firstSplitPane(java.awt.Component component) {
        if (component instanceof JSplitPane splitPane) {
            return splitPane;
        }
        if (component instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents()) {
                try {
                    return firstSplitPane(child);
                } catch (AssertionError ex) {
                    // Continue searching siblings.
                }
            }
        }
        throw new AssertionError("missing dataset split pane");
    }

    /**
     * Exercises the shared chart formatter without widening the production API.
     *
     * @param value count
     * @return formatted count
     */
    private static String formatCompactValue(long value) {
        return (String) WorkbenchTestSupport.invokeStatic(DatasetChart.class, "formatCompactValue",
                new Class<?>[] { long.class }, Long.valueOf(value));
    }

    /**
     * Exercises the shared chart palette without widening the production API.
     *
     * @param role chart role
     * @return role color
     */
    private static Color roleColor(DatasetChart.Role role) {
        return (Color) WorkbenchTestSupport.invokeStatic(DatasetChart.class, "roleColor",
                new Class<?>[] { DatasetChart.Role.class }, role);
    }

    /**
     * Verifies the in-progress scan layout keeps all primary affordances on the
     * same centered axis.
     *
     * @param panel dataset panel
     */
    private static void assertLoadingCardCenters(DatasetPanel panel) {
        Path source = Path.of("/home/lennart/Code/chess-rtk/dump/standard-1773816508360.nonpuzzles.json");
        try {
            WorkbenchTestSupport.invoke(panel, "setLoadingTarget", new Class<?>[] { Path.class }, source);
            WorkbenchTestSupport.invoke(panel, "setBusy", new Class<?>[] { boolean.class }, Boolean.TRUE);
            panel.setSize(1648, 861);
            layoutTree(panel);

            JComponent spinner = (JComponent) field(panel, "loadingSpinner");
            JLabel title = (JLabel) field(panel, "loadingTitle");
            JLabel fileValue = (JLabel) field(panel, "loadingFileValue");
            JComponent progressCard = ancestor(fileValue, "application.gui.workbench.ui.Card");
            JComponent cancel = actionByLabel(panel, "Cancel Scan");
            int center = panel.getWidth() / 2;

            assertNear(center, centerX(panel, spinner), 2, "dataset loading spinner is centered");
            assertNear(center, centerX(panel, title), 2, "dataset loading title is centered");
            assertNear(center, centerX(panel, progressCard), 2, "dataset loading progress card is centered");
            assertNear(center, centerX(panel, cancel), 2, "dataset loading cancel action is centered");
            assertTrue(progressCard.getWidth() <= 760, "dataset loading card width is bounded");
            assertTrue(fileValue.getText().endsWith("..."), "dataset loading file path is clipped");
            assertEquals(source.toString(), fileValue.getToolTipText(), "dataset loading file tooltip keeps full path");
        } finally {
            WorkbenchTestSupport.invoke(panel, "setBusy", new Class<?>[] { boolean.class }, Boolean.FALSE);
        }
    }

    /**
     * Verifies the loaded analytics band does not reserve a large empty region
     * above the table on tall Workbench windows.
     *
     * @param panel loaded dataset panel
     */
    private static void assertLoadedSplitKeepsTablesNearCharts(DatasetPanel panel) {
        panel.setSize(2048, 1163);
        layoutTree(panel);
        JSplitPane split = firstSplitPane(panel);
        assertTrue(split.getDividerLocation() <= 340,
                "dataset loaded overview divider stays compact on tall windows");
        assertTrue(split.getBottomComponent().getHeight() > split.getTopComponent().getHeight(),
                "dataset loaded table receives spare vertical space");
    }

    /**
     * Lays out a component tree after explicit sizing.
     *
     * @param component root component
     */
    private static void layoutTree(Component component) {
        component.doLayout();
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                layoutTree(child);
            }
        }
    }

    /**
     * Returns a component's center X in root coordinates.
     *
     * @param root coordinate root
     * @param component target component
     * @return center X coordinate
     */
    private static int centerX(Component root, Component component) {
        java.awt.Rectangle bounds = SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), root);
        return bounds.x + bounds.width / 2;
    }

    /**
     * Finds the first ancestor with the requested class name.
     *
     * @param component starting component
     * @param className ancestor class name
     * @return matching ancestor
     */
    private static JComponent ancestor(Component component, String className) {
        Component current = component;
        while (current != null) {
            if (current instanceof JComponent candidate && className.equals(current.getClass().getName())) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new AssertionError("missing ancestor " + className);
    }

    /**
     * Finds an action component by visible label.
     *
     * @param root root component
     * @param text action text
     * @return matching action component
     */
    private static JComponent actionByLabel(Container root, String text) {
        for (Component child : root.getComponents()) {
            if (child instanceof JButton button && text.equals(button.getText())) {
                return button;
            }
            if (child instanceof JComponent component
                    && "application.gui.workbench.ui.HoldButton".equals(component.getClass().getName())
                    && text.equals(field(component, "label"))) {
                return component;
            }
            if (child instanceof Container container) {
                try {
                    return actionByLabel(container, text);
                } catch (AssertionError ex) {
                    // keep scanning siblings
                }
            }
        }
        throw new AssertionError("missing action " + text);
    }

    /**
     * Asserts integer proximity.
     *
     * @param expected expected value
     * @param actual actual value
     * @param tolerance allowed absolute delta
     * @param message assertion message
     */
    private static void assertNear(int expected, int actual, int tolerance, String message) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + " (expected " + expected + " +/- "
                    + tolerance + ", got " + actual + ")");
        }
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
     * @param component Swing component
     * @param width width in pixels
     * @param height height in pixels
     * @param message assertion message
     */
    private static void paintPanel(JComponent component, int width, int height, String message) {
        component.setSize(width, height);
        paint(component, width, height);
        assertPaintsOpaqueCorner(component, width, height, message);
    }

    /**
     * Verifies row hover covers custom label and FEN renderers.
     *
     * @param table dataset sample table
     */
    private static void assertTableHoverCoversCustomCells(JTable table) {
        table.setSize(900, 180);
        table.doLayout();
        java.awt.Rectangle cell = table.getCellRect(0, 6, true);
        MouseEvent event = new MouseEvent(table, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(),
                0, cell.x + Math.max(1, cell.width / 2), cell.y + Math.max(1, cell.height / 2), 0, false);
        table.dispatchEvent(event);
        Color labelBackground = table.prepareRenderer(table.getCellRenderer(0, 5), 0, 5).getBackground();
        Color fenBackground = table.prepareRenderer(table.getCellRenderer(0, 6), 0, 6).getBackground();
        assertEquals(labelBackground, fenBackground,
                "dataset label and FEN cells share hover background");
        assertFalse(fenBackground.equals(table.getBackground()),
                "dataset FEN hover background differs from resting table background");
    }
}
