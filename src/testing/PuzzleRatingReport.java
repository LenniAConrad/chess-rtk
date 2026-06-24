package testing;

import static testing.PuzzleRatingCsvTool.*;
import static testing.PuzzleRatingGraph.*;
import static testing.PuzzleRatingSnapshot.*;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import chess.pdf.document.Canvas;
import chess.pdf.document.Document;
import chess.pdf.document.Font;
import chess.pdf.document.Page;
import chess.pdf.document.PageSize;

/**
 * Native PDF report writer for {@link PuzzleRatingCsvTool}.
 */
final class PuzzleRatingReport {

    /**
     * Utility class; prevent instantiation.
     */
    private PuzzleRatingReport() {
        // utility
    }

    /**
     * Writes the native PDF puzzle rating report.
     *
     * @param output requested output path
     * @param rows puzzle rating rows
     * @param records source record paths
     * @throws IOException on write failure
     */
    static void writeReport(Path output, List<PuzzleRatingRow> rows, List<Path> records) throws IOException {
        Path pdf = reportPdfPath(output);
        Path parent = pdf.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        PuzzleRatingStats stats = reportStats(rows);
        PuzzleRatingRow snapshot = puzzleSnapshotRow(rows);
        PuzzleRatingDetail snapshotDetail = reportPuzzleDetail(snapshot, records);
        Document document = new Document()
                .setTitle("Puzzle Difficulty Report")
                .setAuthor("ChessRTK")
                .setSubject("Current direct chess puzzle rating distribution")
                .setCreator("testing.PuzzleRatingCsvTool")
                .setProducer("chess-rtk native PDF");
        Page page = document.addPage(PageSize.A4);
        drawReportPage(page, rows, stats, snapshot, snapshotDetail);
        Page appendix = document.addPage(PageSize.A4);
        drawReportAppendixPage(appendix, rows, stats);
        document.write(pdf);
        System.out.println("report: " + pdf);
    }

    /**
     * Maps earlier markdown report names to the native PDF artifact.
     * @param output output text
     * @return report pdf path
     */
    private static Path reportPdfPath(Path output) {
        String name = output.getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".md") && !lower.endsWith(".markdown")) {
            return output;
        }
        int dot = name.lastIndexOf('.');
        Path pdfName = Path.of(name.substring(0, dot) + ".pdf");
        Path parent = output.getParent();
        return parent == null ? pdfName : parent.resolve(pdfName);
    }

    /**
     * Draws the single-page report.
     * @param page page index
     * @param rows data rows
     * @param stats statistics data
     * @param snapshot captured snapshot
     * @param snapshotDetail source snapshot detail
     */
    private static void drawReportPage(Page page, List<PuzzleRatingRow> rows, PuzzleRatingStats stats, PuzzleRatingRow snapshot,
            PuzzleRatingDetail snapshotDetail) {
        Canvas canvas = page.canvas();
        double width = page.getWidth() - REPORT_MARGIN * 2.0;
        drawReportHeader(canvas, page.getWidth(), width, REPORT_TOP_MARGIN);
        double y = REPORT_TOP_MARGIN + 50.0;
        drawMetricCards(canvas, REPORT_MARGIN, y, width, rows, stats);
        y += 57.0;
        drawReportChart(canvas, rows, REPORT_MARGIN, y, width, 216.0);
        y += 226.0;
        drawInterpretationPanel(canvas, REPORT_MARGIN, y, width, rows, stats);
        y += 76.0;

        double gap = 10.0;
        double snapshotWidth = 178.0;
        double driversWidth = width - gap - snapshotWidth;
        double analysisHeight = 246.0;
        drawComplexityDriversGraph(canvas, rows, REPORT_MARGIN, y, driversWidth, analysisHeight);
        drawPuzzleSnapshotPanel(canvas, snapshot, snapshotDetail, REPORT_MARGIN + driversWidth + gap, y,
                snapshotWidth, analysisHeight);
        y += analysisHeight + 14.0;

        double leftWidth = 185.0;
        double middleWidth = 164.0;
        double rightWidth = width - leftWidth - middleWidth - gap * 2.0;
        double leftBottom = drawSectionWithTable(canvas, REPORT_MARGIN, y, leftWidth, "Difficulty Mix",
                new String[] { "Bucket", "Count", "Share", "Median", "p90" },
                difficultyRows(rows),
                new double[] { 0.28, 0.20, 0.17, 0.17, 0.18 },
                new boolean[] { false, true, true, true, true });
        double featureBottom = drawSectionWithTable(canvas, REPORT_MARGIN + leftWidth + gap, y, middleWidth,
                "Top Puzzle Signals",
                new String[] { "Feature", "Count", "Share" },
                featureRows(rows),
                new double[] { 0.47, 0.29, 0.24 },
                new boolean[] { false, true, true });
        double structureBottom = drawSectionWithTable(canvas, REPORT_MARGIN + leftWidth + middleWidth + gap * 2.0,
                y, rightWidth, "Sample Health",
                null, structureRows(rows, stats),
                new double[] { 0.60, 0.40 },
                new boolean[] { false, true });
        double footerY = Math.max(Math.max(leftBottom, featureBottom), structureBottom) + 12.0;
        drawReportFooter(canvas, page.getHeight(), page.getWidth(), footerY);
    }

    /**
     * Draws the report appendix page with audit data for hard-tail examples.
     *
     * @param page PDF page
     * @param rows report input rows
     * @param stats report-wide statistics
     */
    private static void drawReportAppendixPage(Page page, List<PuzzleRatingRow> rows, PuzzleRatingStats stats) {
        Canvas canvas = page.canvas();
        double width = page.getWidth() - REPORT_MARGIN * 2.0;
        drawReportHeader(canvas, page.getWidth(), width, REPORT_TOP_MARGIN,
                "Difficulty Evidence Appendix",
                "Hard-tail leaders, explicit tree load, and rare move signals");

        double y = REPORT_TOP_MARGIN + 50.0;
        drawAppendixMetricCards(canvas, REPORT_MARGIN, y, width, rows, stats);
        y += 57.0;

        y = drawSectionWithTable(canvas, REPORT_MARGIN, y, width, "Hard-Tail Leaders",
                new String[] { "#", "Idx", "Rating", "Key", "Plies", "Nodes", "Replies", "Br", "Rank",
                        "Signals" },
                hardestRows(rows, 10),
                new double[] { 0.045, 0.065, 0.075, 0.095, 0.065, 0.065, 0.075, 0.050, 0.060, 0.405 },
                new boolean[] { true, true, true, false, true, true, true, true, true, false });
        y += 13.0;

        double gap = 10.0;
        double columnWidth = (width - gap) / 2.0;
        double loadBottom = drawSectionWithTable(canvas, REPORT_MARGIN, y, columnWidth,
                "Calculation Load Leaders",
                new String[] { "Idx", "Rating", "Key", "Plies", "Nodes", "Branches" },
                calculationLoadRows(rows, 8),
                new double[] { 0.14, 0.18, 0.25, 0.14, 0.14, 0.15 },
                new boolean[] { true, true, false, true, true, true });
        double rareBottom = drawSectionWithTable(canvas, REPORT_MARGIN + columnWidth + gap, y, columnWidth,
                "Rare & Tactical Signals",
                new String[] { "Signal", "Rows", "Share", "Max" },
                rareSignalRows(rows),
                new double[] { 0.46, 0.18, 0.18, 0.18 },
                new boolean[] { false, true, true, true });
        y = Math.max(loadBottom, rareBottom) + 13.0;

        double hardSignalBottom = drawSectionWithTable(canvas, REPORT_MARGIN, y, columnWidth,
                "Very-Hard Signal Mix",
                new String[] { "Signal", "Rows", "Share", "Max" },
                hardTailSignalRows(rows, 8),
                new double[] { 0.46, 0.18, 0.18, 0.18 },
                new boolean[] { false, true, true, true });
        double bandBottom = drawSectionWithTable(canvas, REPORT_MARGIN + columnWidth + gap, y, columnWidth,
                "Band Evidence Matrix",
                new String[] { "Band", "Rows", "Rank", "Plies", "Nodes", "Br%" },
                appendixBandRows(rows),
                new double[] { 0.29, 0.17, 0.13, 0.14, 0.14, 0.13 },
                new boolean[] { false, true, true, true, true, true });
        y = Math.max(hardSignalBottom, bandBottom) + 13.0;

        drawAppendixNotes(canvas, REPORT_MARGIN, y, width, rows);
        drawReportFooter(canvas, page.getHeight(), page.getWidth(), y + 70.0);
    }

    /**
     * Draws the title block and source metadata.
     * @param canvas SVG canvas builder
     * @param pageWidth page width in pixels
     * @param contentWidth available content width
     * @param top top coordinate
     */
    private static void drawReportHeader(Canvas canvas, double pageWidth, double contentWidth, double top) {
        drawReportHeader(canvas, pageWidth, contentWidth, top,
                "Puzzle Difficulty Report",
                "Independent heuristic ratings, solver complexity, and sample health");
    }

    /**
     * Draws the title block and source metadata.
     * @param canvas SVG canvas builder
     * @param pageWidth page width in pixels
     * @param contentWidth available content width
     * @param top top coordinate
     * @param title title text
     * @param subtitle display subtitle
     */
    private static void drawReportHeader(Canvas canvas, double pageWidth, double contentWidth, double top,
            String title, String subtitle) {
        canvas.drawText(REPORT_MARGIN, top, REPORT_DISPLAY_FONT, 16.6, REPORT_TEXT, title);
        canvas.drawText(REPORT_MARGIN, top + 18.0, REPORT_BODY_FONT, 8.4, REPORT_MUTED,
                subtitle);
        drawRight(canvas, pageWidth - REPORT_MARGIN, top + 5.0, REPORT_BODY_FONT, 7.8, REPORT_TEXT,
                "Created " + createdStamp());
        canvas.line(REPORT_MARGIN, top + 42.0, REPORT_MARGIN + contentWidth, top + 42.0, REPORT_ACCENT, 0.75);
    }

    /**
     * Draws the report footer when there is room below the main content.
     * @param canvas SVG canvas builder
     * @param pageHeight source page height
     * @param pageWidth page width in pixels
     * @param contentBottom source content bottom
     */
    private static void drawReportFooter(Canvas canvas, double pageHeight, double pageWidth, double contentBottom) {
        double y = Math.max(contentBottom, pageHeight - REPORT_BOTTOM_MARGIN - 12.0);
        if (y > pageHeight - REPORT_BOTTOM_MARGIN - 4.0) {
            return;
        }
        canvas.line(REPORT_MARGIN, y - 5.0, pageWidth - REPORT_MARGIN, y - 5.0, REPORT_SOFT_RULE, 0.35);
        canvas.drawText(REPORT_MARGIN, y, REPORT_BODY_FONT, 5.1, REPORT_MUTED,
                "Ratings are deterministic heuristic puzzle estimates on a 600-3000 scale; trend smoothing is display-only.");
    }

    /**
     * Draws headline metric cards.
     * @param canvas SVG canvas builder
     * @param x x coordinate
     * @param y y coordinate
     * @param width width in pixels
     * @param rows data rows
     * @param stats statistics data
     */
    private static void drawMetricCards(Canvas canvas, double x, double y, double width, List<PuzzleRatingRow> rows,
            PuzzleRatingStats stats) {
        double gap = 8.0;
        double cardWidth = (width - gap * 3.0) / 4.0;
        int body = rowsInRange(rows, MIN_RATING, 1809).size();
        int hardTail = rowsInRange(rows, 1810, MAX_RATING).size();
        drawMetricCard(canvas, x, y, cardWidth, "Sample", num(rows.size()), "puzzles scored");
        drawMetricCard(canvas, x + cardWidth + gap, y, cardWidth, "Median", Integer.toString(percentile(rows, 0.50)),
                "median rating; p90 " + percentile(rows, 0.90));
        drawMetricCard(canvas, x + (cardWidth + gap) * 2.0, y, cardWidth, "Main Body", pct(body, rows.size()),
                "below hard threshold");
        drawMetricCard(canvas, x + (cardWidth + gap) * 3.0, y, cardWidth, "Hard Tail",
                pct(hardTail, rows.size()), "rating 1810 and above");
    }

    /**
     * Draws appendix metric cards.
     *
     * @param canvas drawing surface
     * @param x left edge in PDF points
     * @param y top edge in PDF points
     * @param width content width in PDF points
     * @param rows report input rows
     * @param stats report-wide statistics
     */
    private static void drawAppendixMetricCards(Canvas canvas, double x, double y, double width, List<PuzzleRatingRow> rows,
            PuzzleRatingStats stats) {
        double gap = 8.0;
        double cardWidth = (width - gap * 3.0) / 4.0;
        int veryHard = rowsInRange(rows, 2140, MAX_RATING).size();
        int extreme = rowsInRange(rows, 2500, MAX_RATING).size();
        int special = countFeature(rows, "special_move");
        drawMetricCard(canvas, x, y, cardWidth, "Very Hard", num(veryHard), pct(veryHard, rows.size())
                + " at rating 2140+");
        drawMetricCard(canvas, x + cardWidth + gap, y, cardWidth, "2500+", num(extreme), pct(extreme, rows.size())
                + " in the extreme tail");
        drawMetricCard(canvas, x + (cardWidth + gap) * 2.0, y, cardWidth, "Source Trees",
                num(stats.explicitNodes()), pct(stats.explicitNodes(), rows.size()) + " with continuations");
        drawMetricCard(canvas, x + (cardWidth + gap) * 3.0, y, cardWidth, "Rare Moves",
                num(special), "special solution-move tags");
    }

    /**
     * Draws one headline metric card.
     * @param canvas SVG canvas builder
     * @param x x coordinate
     * @param y y coordinate
     * @param width width in pixels
     * @param label label text
     * @param value value to use
     * @param note note text
     */
    private static void drawMetricCard(Canvas canvas, double x, double y, double width, String label, String value,
            String note) {
        double height = 45.0;
        canvas.fillRect(x, y, width, height, REPORT_CARD);
        canvas.strokeRect(x, y, width, height, REPORT_SOFT_RULE, 0.45);
        canvas.drawText(x + 7.0, y + 6.0, REPORT_DATA_BOLD_FONT, 6.6, REPORT_ACCENT,
                label.toUpperCase(Locale.ROOT));
        canvas.drawText(x + 7.0, y + 17.0, REPORT_DATA_BOLD_FONT, 13.0, REPORT_TEXT, value);
        canvas.drawWrappedText(x + 7.0, y + 32.0, width - 14.0, REPORT_BODY_FONT, 6.3, 7.0, REPORT_MUTED,
                note);
    }

    /**
     * Draws concise interpretation and method notes.
     * @param canvas SVG canvas builder
     * @param x x coordinate
     * @param y y coordinate
     * @param width width in pixels
     * @param rows data rows
     * @param stats statistics data
     */
    private static void drawInterpretationPanel(Canvas canvas, double x, double y, double width, List<PuzzleRatingRow> rows,
            PuzzleRatingStats stats) {
        double height = 64.0;
        double gap = 14.0;
        double column = (width - gap) / 2.0;
        canvas.fillRect(x, y, width, height, REPORT_PANEL);
        canvas.strokeRect(x, y, width, height, REPORT_SOFT_RULE, 0.45);
        canvas.drawText(x + 8.0, y + 7.0, REPORT_SECTION_FONT, 8.3, REPORT_TEXT, "Reading the Curve");
        canvas.drawText(x + column + gap + 8.0, y + 7.0, REPORT_SECTION_FONT, 8.3, REPORT_TEXT,
                "Method Notes");
        drawBullet(canvas, x + 9.0, y + 21.0, column - 14.0,
                readingDistributionBullet(rows));
        drawBullet(canvas, x + 9.0, y + 38.0, column - 14.0,
                coverageShapeBullet(rows));
        drawBullet(canvas, x + column + gap + 9.0, y + 21.0, column - 14.0,
                directScoringBullet(rows));
        drawBullet(canvas, x + column + gap + 9.0, y + 38.0, column - 14.0,
                rawScoreBullet(rows, stats));
    }

    /**
     * Draws appendix reading notes.
     *
     * @param canvas drawing surface
     * @param x left edge in PDF points
     * @param y top edge in PDF points
     * @param width panel width in PDF points
     * @param rows report input rows
     */
    private static void drawAppendixNotes(Canvas canvas, double x, double y, double width, List<PuzzleRatingRow> rows) {
        double height = 68.0;
        List<PuzzleRatingRow> veryHard = rowsInRange(rows, 2140, MAX_RATING);
        canvas.fillRect(x, y, width, height, REPORT_PANEL);
        canvas.strokeRect(x, y, width, height, REPORT_SOFT_RULE, 0.45);
        canvas.drawText(x + 8.0, y + 7.0, REPORT_SECTION_FONT, 8.5, REPORT_TEXT,
                "How to Read the Appendix");
        drawBullet(canvas, x + 9.0, y + 22.0, width - 18.0,
                "Hard-tail leaders are sorted by final direct rating; tree metrics are explicit source evidence, not PV length.");
        drawBullet(canvas, x + 9.0, y + 38.0, width - 18.0,
                "Calculation-load leaders sort by solution plies, nodes, branches, replies, and then rating; they can differ from the highest-rated rows.");
        drawBullet(canvas, x + 9.0, y + 54.0, width - 18.0,
                "Very-hard rows: " + num(veryHard.size()) + "; "
                        + pct(veryHard.stream().filter(row -> row.branches() > 0).count(), veryHard.size())
                        + " have branch points and "
                        + pct(veryHard.stream().filter(row -> row.hasFeature("multi_move")).count(),
                                veryHard.size())
                        + " carry multi-move evidence.");
    }

    /**
     * Builds the first dynamic reading-curve bullet.
     * @param rows data rows
     * @return built the first dynamic reading-curve bullet
     */
    private static String readingDistributionBullet(List<PuzzleRatingRow> rows) {
        PuzzleRatingDifficultyBand dominant = dominantDifficultyBand(rows);
        int body = rowsInRange(rows, MIN_RATING, 1809).size();
        int hardTail = rowsInRange(rows, 1810, MAX_RATING).size();
        return "Largest band: " + dominant.name() + " " + pct(dominant.count(), rows.size())
                + " (" + num(dominant.count()) + " rows); " + pct(body, rows.size())
                + " below hard, " + pct(hardTail, rows.size()) + " in hard tail.";
    }

    /**
     * Builds the second dynamic reading-curve bullet.
     * @param rows data rows
     * @return built the second dynamic reading-curve bullet
     */
    private static String coverageShapeBullet(List<PuzzleRatingRow> rows) {
        return "Observed range " + percentile(rows, 0.00) + "-" + percentile(rows, 1.00)
                + "; p99 " + percentile(rows, 0.99) + "; "
                + pct(rowsAtOrBelow(rows, 2140), rows.size()) + " at or below 2140 rating.";
    }

    /**
     * Builds the first dynamic scoring-note bullet.
     * @param rows data rows
     * @return built the first dynamic scoring-note bullet
     */
    private static String directScoringBullet(List<PuzzleRatingRow> rows) {
        return "No subset calibration: each row is scored independently; p10/median/p90 = "
                + percentile(rows, 0.10) + " / " + percentile(rows, 0.50)
                + " / " + percentile(rows, 0.90) + ".";
    }

    /**
     * Builds the second dynamic scoring-note bullet.
     * @param rows data rows
     * @param stats statistics data
     * @return built the second dynamic scoring-note bullet
     */
    private static String rawScoreBullet(List<PuzzleRatingRow> rows, PuzzleRatingStats stats) {
        return "CSV keeps exact ratings; chart uses " + DISPLAY_BIN_WIDTH + "-point bins and a "
                + DISTRIBUTION_TREND_POINTS + "-point trend only for readability.";
    }

    /**
     * Draws one wrapped bullet.
     * @param canvas SVG canvas builder
     * @param x x coordinate
     * @param y y coordinate
     * @param width width in pixels
     * @param text text to render or parse
     */
    private static void drawBullet(Canvas canvas, double x, double y, double width, String text) {
        canvas.fillRect(x, y + 3.0, 2.0, 2.0, REPORT_ACCENT);
        canvas.drawWrappedText(x + 7.0, y, width - 7.0, REPORT_BODY_FONT, 6.2, 7.0, REPORT_MUTED, text);
    }

    /**
     * Draws the chart directly in the PDF.
     * @param canvas SVG canvas builder
     * @param rows data rows
     * @param x x coordinate
     * @param y y coordinate
     * @param width width in pixels
     * @param height height in pixels
     */
    private static void drawReportChart(Canvas canvas, List<PuzzleRatingRow> rows, double x, double y, double width,
            double height) {
        int[] bins = histogram(rows, DISPLAY_BIN_WIDTH);
        double[] percentages = percentages(bins, rows.size());
        double yMax = yMax(percentages);
        double plotLeft = x + 29.0;
        double plotTop = y + 48.0;
        double plotWidth = width - 45.0;
        double plotHeight = height - 81.0;

        canvas.drawText(plotLeft, y, REPORT_SECTION_FONT, 10.8, REPORT_TEXT,
                "Heuristic Puzzle Rating Distribution");
        canvas.drawText(plotLeft, y + 16.0, REPORT_BODY_FONT, 5.0, REPORT_TEXT,
                num(rows.size()) + " puzzles; " + DISPLAY_BIN_WIDTH + "-point bins with "
                        + DISTRIBUTION_TREND_POINTS + "-point trend; p10/median/p90/p99 = "
                        + percentile(rows, 0.10) + " / " + percentile(rows, 0.50)
                        + " / " + percentile(rows, 0.90) + " / " + percentile(rows, 0.99));

        drawReportBands(canvas, rows, plotLeft, plotTop, plotWidth, plotHeight);
        drawReportGrid(canvas, plotLeft, plotTop, plotWidth, plotHeight, yMax);
        drawReportHistogram(canvas, percentages, plotLeft, plotTop, plotWidth, plotHeight, yMax);
        drawReportTrendLine(canvas, movingAverage(percentages, distributionTrendWindowBins()), plotLeft, plotTop,
                plotWidth, plotHeight, yMax);
        drawReportDistributionLegend(canvas, plotLeft + plotWidth - 78.0, y + 28.0);
        drawReportAxes(canvas, plotLeft, plotTop, plotWidth, plotHeight, yMax);
        drawReportQuantiles(canvas, rows, plotLeft, plotTop, plotWidth, plotHeight);
        drawReportBandLabels(canvas, rows, plotLeft, plotTop, plotWidth);
    }

    /**
     * Draws difficulty background bands.
     * @param canvas SVG canvas builder
     * @param rows data rows
     * @param left left coordinate
     * @param top top coordinate
     * @param plotWidth plot width in pixels
     * @param plotHeight plot height in pixels
     */
    private static void drawReportBands(Canvas canvas, List<PuzzleRatingRow> rows, double left, double top, double plotWidth,
            double plotHeight) {
        int[] starts = { MIN_RATING, 1040, 1370, 1810, 2140 };
        int[] ends = { 1039, 1369, 1809, 2139, MAX_RATING };
        Color[] fills = {
                new Color(244, 252, 248), new Color(244, 251, 255), new Color(255, 251, 238),
                new Color(255, 245, 238), new Color(255, 242, 245)
        };
        for (int i = 0; i < starts.length; i++) {
            double x1 = ratingX(starts[i], left, plotWidth);
            double x2 = ratingX(ends[i] + 1, left, plotWidth);
            canvas.fillRect(x1, top, x2 - x1, plotHeight, fills[i]);
        }
    }

    /**
     * Draws difficulty band labels above percentile dividers.
     * @param canvas SVG canvas builder
     * @param rows data rows
     * @param left left coordinate
     * @param top top coordinate
     * @param plotWidth plot width in pixels
     */
    private static void drawReportBandLabels(Canvas canvas, List<PuzzleRatingRow> rows, double left, double top,
            double plotWidth) {
        int[] starts = { MIN_RATING, 1040, 1370, 1810, 2140 };
        int[] ends = { 1039, 1369, 1809, 2139, MAX_RATING };
        String[] labels = { "Very easy", "Easy", "Medium", "Hard", "Very hard" };
        Color[] text = {
                new Color(30, 107, 88), new Color(30, 102, 140), new Color(138, 90, 5),
                new Color(148, 66, 19), new Color(152, 33, 61)
        };
        for (int i = 0; i < starts.length; i++) {
            double x1 = ratingX(starts[i], left, plotWidth);
            canvas.drawText(x1 + 2.5, top + 4.0, REPORT_DATA_BOLD_FONT, 4.7, text[i], labels[i]);
            canvas.drawText(x1 + 2.5, top + 12.0, REPORT_DATA_FONT, 4.5, REPORT_MUTED,
                    percentInRange(rows, starts[i], ends[i]));
        }
    }

    /**
     * Draws chart grid lines and y labels.
     * @param canvas SVG canvas builder
     * @param left left coordinate
     * @param top top coordinate
     * @param plotWidth plot width in pixels
     * @param plotHeight plot height in pixels
     * @param yMax maximum y value
     */
    private static void drawReportGrid(Canvas canvas, double left, double top, double plotWidth, double plotHeight,
            double yMax) {
        int ticks = 4;
        for (int i = 0; i <= ticks; i++) {
            double value = yMax * i / ticks;
            double y = top + plotHeight - plotHeight * value / yMax;
            canvas.line(left, y, left + plotWidth, y, REPORT_GRID, 0.35);
            drawRight(canvas, left - 4.0, y - 2.6, REPORT_DATA_FONT, 4.2, REPORT_MUTED, percent(value));
        }
    }

    /**
     * Draws the contiguous histogram fill with native PDF rectangles.
     * @param canvas SVG canvas builder
     * @param percentages percentage values
     * @param left left coordinate
     * @param top top coordinate
     * @param plotWidth plot width in pixels
     * @param plotHeight plot height in pixels
     * @param yMax maximum y value
     */
    private static void drawReportHistogram(Canvas canvas, double[] percentages, double left, double top,
            double plotWidth, double plotHeight, double yMax) {
        double bw = plotWidth / percentages.length;
        double overlap = Math.max(0.03, bw * 0.08);
        for (int i = 0; i < percentages.length; i++) {
            double h = plotHeight * percentages[i] / yMax;
            double x = left + i * bw;
            double y = top + plotHeight - h;
            canvas.fillRect(x, y, bw + overlap, h, REPORT_BAR);
        }
    }

    /**
     * Draws the smoothed distribution trend line over the PDF histogram.
     * @param canvas SVG canvas builder
     * @param smoothed smoothed metric series
     * @param left left coordinate
     * @param top top coordinate
     * @param plotWidth plot width in pixels
     * @param plotHeight plot height in pixels
     * @param yMax maximum y value
     */
    private static void drawReportTrendLine(Canvas canvas, double[] smoothed, double left, double top,
            double plotWidth, double plotHeight, double yMax) {
        if (smoothed.length == 0) {
            return;
        }
        double bw = plotWidth / smoothed.length;
        double prevX = left + bw * 0.5;
        double prevY = top + plotHeight - plotHeight * smoothed[0] / yMax;
        Color trend = REPORT_TREND;
        for (int i = 1; i < smoothed.length; i++) {
            double x = left + (i + 0.5) * bw;
            double y = top + plotHeight - plotHeight * smoothed[i] / yMax;
            canvas.line(prevX, prevY, x, y, trend, 0.7);
            prevX = x;
            prevY = y;
        }
    }

    /**
     * Draws a small legend for the distribution chart.
     * @param canvas SVG canvas builder
     * @param x x coordinate
     * @param y y coordinate
     */
    private static void drawReportDistributionLegend(Canvas canvas, double x, double y) {
        canvas.fillRect(x, y + 1.0, 5.0, 4.0, REPORT_BAR);
        canvas.drawText(x + 7.0, y, REPORT_DATA_FONT, 4.3, REPORT_MUTED,
                DISPLAY_BIN_WIDTH + "-point bins");
        canvas.line(x + 42.0, y + 3.0, x + 54.0, y + 3.0, REPORT_TREND, 0.7);
        canvas.drawText(x + 57.0, y, REPORT_DATA_FONT, 4.3, REPORT_MUTED, "trend");
    }

    /**
     * Draws chart axes and x labels.
     * @param canvas SVG canvas builder
     * @param left left coordinate
     * @param top top coordinate
     * @param plotWidth plot width in pixels
     * @param plotHeight plot height in pixels
     * @param yMax maximum y value
     */
    private static void drawReportAxes(Canvas canvas, double left, double top, double plotWidth, double plotHeight,
            double yMax) {
        canvas.line(left, top + plotHeight, left + plotWidth, top + plotHeight, REPORT_RULE, 0.6);
        canvas.line(left, top, left, top + plotHeight, REPORT_RULE, 0.6);
        for (int rating = MIN_RATING; rating <= MAX_RATING; rating += 100) {
            double x = ratingX(rating, left, plotWidth);
            canvas.line(x, top + plotHeight, x, top + plotHeight + 2.5, REPORT_RULE, 0.35);
            drawCentered(canvas, x, top + plotHeight + 7.0, REPORT_DATA_FONT, 4.0, REPORT_MUTED,
                    Integer.toString(rating));
        }
        drawCentered(canvas, left + plotWidth / 2.0, top + plotHeight + 18.0, REPORT_DATA_FONT, 4.4,
                REPORT_TEXT, "Puzzle rating");
        canvas.drawTextRotated(left - 24.0, top + plotHeight / 2.0 + 26.0, -90.0, left - 24.0,
                top + plotHeight / 2.0 + 26.0, REPORT_DATA_FONT, 4.4, REPORT_TEXT,
                "% of puzzles per " + DISPLAY_BIN_WIDTH + " rating points");
    }

    /**
     * Draws quantile markers.
     * @param canvas SVG canvas builder
     * @param rows data rows
     * @param left left coordinate
     * @param top top coordinate
     * @param plotWidth plot width in pixels
     * @param plotHeight plot height in pixels
     */
    private static void drawReportQuantiles(Canvas canvas, List<PuzzleRatingRow> rows, double left, double top, double plotWidth,
            double plotHeight) {
        double[] qs = { 0.10, 0.25, 0.50, 0.75, 0.90, 0.99 };
        String[] names = { "p10", "p25", "median", "p75", "p90", "p99" };
        for (int i = 0; i < qs.length; i++) {
            int rating = percentile(rows, qs[i]);
            double x = ratingX(rating, left, plotWidth);
            canvas.line(x, top - 4.0, x, top + plotHeight, REPORT_QUANTILE_LINE, 0.22);
            drawCentered(canvas, x, top - 9.0, REPORT_DATA_FONT, 4.0, REPORT_QUANTILE_LABEL,
                    names[i] + " " + rating);
        }
    }

    /**
     * Draws a value-add chart showing what changes by difficulty band.
     * @param canvas SVG canvas builder
     * @param rows data rows
     * @param x x coordinate
     * @param y y coordinate
     * @param width width in pixels
     * @param height height in pixels
     */
    private static void drawComplexityDriversGraph(Canvas canvas, List<PuzzleRatingRow> rows, double x, double y, double width,
            double height) {
        PuzzleRatingComplexityBand[] bands = complexityBands(rows);
        String[] metricNames = { "Legal moves", "Best-move rank", "Solution plies", "Branch-point rows" };
        double[][] values = {
                metricValues(bands, PuzzleRatingComplexityBand::avgLegal),
                metricValues(bands, PuzzleRatingComplexityBand::avgRank),
                metricValues(bands, PuzzleRatingComplexityBand::avgPlies),
                metricValues(bands, PuzzleRatingComplexityBand::branchShare)
        };
        Color[] colors = {
                new Color(137, 198, 169), new Color(132, 185, 216), new Color(224, 186, 103),
                new Color(224, 151, 118), new Color(211, 129, 154)
        };

        canvas.fillRect(x, y, width, height, REPORT_PANEL);
        canvas.strokeRect(x, y, width, height, REPORT_SOFT_RULE, 0.45);
        canvas.drawText(x + 8.0, y + 7.0, REPORT_SECTION_FONT, 9.0, REPORT_TEXT,
                "Difficulty Drivers by Rating Band");
        canvas.drawWrappedText(x + 8.0, y + 21.0, width - 16.0, REPORT_BODY_FONT, 5.4, 6.3, REPORT_MUTED,
                "Normalized bars compare each signal across bands; labels show actual averages.");

        double plotLeft = x + 84.0;
        double plotTop = y + 45.0;
        double plotWidth = width - 101.0;
        double plotHeight = Math.max(118.0, height - 94.0);
        double rowHeight = plotHeight / metricNames.length;
        for (int metric = 0; metric < metricNames.length; metric++) {
            drawDriverMetricRow(canvas, bands, colors, metricNames[metric], values[metric],
                    x + 8.0, plotLeft, plotTop + metric * rowHeight, plotWidth, rowHeight, metric);
        }
        drawDriverBandLabels(canvas, bands, plotLeft, plotTop + plotHeight + 5.0, plotWidth);
        canvas.drawWrappedText(x + 8.0, y + height - 20.0, width - 16.0, REPORT_BODY_FONT, 5.2, 6.2,
                REPORT_MUTED,
                complexitySummary(bands));
    }

    /**
     * Draws a compact board snapshot for a hard row with explicit continuation
     * depth.
     *
     * @param canvas drawing surface
     * @param row selected hard puzzle row, or {@code null} when the corpus is empty
     * @param detail formatted puzzle detail for the row
     * @param x left edge in PDF points
     * @param y top edge in PDF points
     * @param width panel width in PDF points
     * @param height panel height in PDF points
     * @param bands rating or metric bands to plot
     * @param colors colors paired with the plotted series
     * @param metricName source metric name
     * @param values input values
     * @param labelX x-coordinate for the metric label
     * @param plotLeft left edge of the plot area
     * @param rowTop top edge of the metric row
     * @param plotWidth plot width in pixels
     * @param rowHeight row height in pixels
     * @param metricIndex zero-based metric index
     */

    private static void drawDriverMetricRow(Canvas canvas, PuzzleRatingComplexityBand[] bands, Color[] colors, String metricName,
            double[] values, double labelX, double plotLeft, double rowTop, double plotWidth, double rowHeight,
            int metricIndex) {
        double max = Math.max(0.000_001, max(values));
        double baseline = rowTop + rowHeight - 9.0;
        double barMax = Math.max(8.0, rowHeight - 19.0);
        double groupWidth = plotWidth / bands.length;
        double barWidth = groupWidth * 0.54;
        canvas.drawText(labelX, rowTop + 5.0, REPORT_DATA_BOLD_FONT, 5.8, REPORT_TEXT, metricName);
        canvas.line(plotLeft, baseline, plotLeft + plotWidth, baseline, REPORT_TABLE_RULE, 0.28);
        for (int i = 0; i < bands.length; i++) {
            double h = barMax * values[i] / max;
            double bx = plotLeft + i * groupWidth + (groupWidth - barWidth) / 2.0;
            double by = baseline - h;
            canvas.fillRect(bx, by, barWidth, h, colors[i]);
            canvas.strokeRect(bx, by, barWidth, h, new Color(255, 255, 255, 150), 0.15);
            drawCentered(canvas, bx + barWidth / 2.0, Math.max(rowTop + 2.0, by - 6.8), REPORT_DATA_FONT,
                    4.5, REPORT_TEXT, metricValueLabel(metricIndex, values[i]));
        }
    }

    /**
     * Draws labels below the difficulty-driver chart.
     * @param canvas SVG canvas builder
     * @param bands rating bands
     * @param plotLeft plot left coordinate
     * @param y y coordinate
     * @param plotWidth plot width in pixels
     */
    private static void drawDriverBandLabels(Canvas canvas, PuzzleRatingComplexityBand[] bands, double plotLeft, double y,
            double plotWidth) {
        double groupWidth = plotWidth / bands.length;
        for (int i = 0; i < bands.length; i++) {
            double center = plotLeft + i * groupWidth + groupWidth / 2.0;
            drawCentered(canvas, center, y, REPORT_DATA_BOLD_FONT, 4.4, REPORT_TEXT, bands[i].shortName());
            drawCentered(canvas, center, y + 7.0, REPORT_DATA_FONT, 4.1, REPORT_MUTED,
                    pct(bands[i].count(), totalBandCount(bands)));
        }
    }

    /**
     * Builds the dynamic summary under the difficulty-driver chart.
     * @param bands rating bands
     * @return built the dynamic summary under the difficulty-driver chart
     */
    private static String complexitySummary(PuzzleRatingComplexityBand[] bands) {
        PuzzleRatingComplexityBand medium = bands[2];
        PuzzleRatingComplexityBand hardest = bands[bands.length - 1];
        return "Very hard vs medium: legal moves "
                + oneDecimal(hardest.avgLegal()) + " vs " + oneDecimal(medium.avgLegal())
                + ", best-move rank " + oneDecimal(hardest.avgRank()) + " vs " + oneDecimal(medium.avgRank())
                + ", solution plies " + oneDecimal(hardest.avgPlies()) + " vs " + oneDecimal(medium.avgPlies())
                + ", branch-point rows " + pctLabel(hardest.branchShare()) + " vs " + pctLabel(medium.branchShare())
                + ".";
    }

    /**
     * Formats a metric value for the difficulty-driver chart.
     * @param metricIndex zero-based metric index
     * @param value value to use
     * @return formatted a metric value for the difficulty-driver chart
     */
    private static String metricValueLabel(int metricIndex, double value) {
        if (metricIndex == 3) {
            return pctLabel(value);
        }
        return oneDecimal(value);
    }

    /**
     * Draws a titled table and returns its bottom edge.
     * @param canvas SVG canvas builder
     * @param x x coordinate
     * @param y y coordinate
     * @param width width in pixels
     * @param title title text
     * @param headers table headers
     * @param rows data rows
     * @param columns table columns
     * @param rightAligned true to right-align the value
     * @return drawn a titled table and returns its bottom edge
     */
    private static double drawSectionWithTable(Canvas canvas, double x, double y, double width, String title,
            String[] headers, String[][] rows, double[] columns, boolean[] rightAligned) {
        canvas.fillRect(x, y + 1.1, 2.0, 8.0, REPORT_ACCENT);
        canvas.drawText(x + 5.5, y + 0.2, REPORT_SECTION_FONT, 8.4, REPORT_TEXT, title);
        double ruleY = y + 12.3;
        canvas.line(x, ruleY, x + width, ruleY, REPORT_TABLE_RULE, 0.35);
        return drawTable(canvas, x, y + 16.0, width, headers, rows, columns, rightAligned);
    }

    /**
     * Draws a compact report table.
     * @param canvas SVG canvas builder
     * @param x x coordinate
     * @param y y coordinate
     * @param width width in pixels
     * @param headers table headers
     * @param rows data rows
     * @param columns table columns
     * @param rightAligned true to right-align the value
     * @return drawn a compact report table
     */
    private static double drawTable(Canvas canvas, double x, double y, double width, String[] headers,
            String[][] rows, double[] columns, boolean[] rightAligned) {
        double headerHeight = headers == null ? 0.0 : 12.2;
        double rowHeight = 11.0;
        double bottom = y + headerHeight + rows.length * rowHeight + 2.0;
        canvas.fillRect(x, y, width, bottom - y, Color.WHITE);
        canvas.strokeRect(x, y, width, bottom - y, REPORT_TABLE_RULE, 0.35);
        if (headers != null) {
            canvas.fillRect(x, y, width, headerHeight, REPORT_TABLE_HEADER);
        }
        if (headers != null) {
            drawRow(canvas, x, y + 3.3, width, headers, columns, rightAligned, REPORT_DATA_BOLD_FONT, 6.0,
                    REPORT_ACCENT);
            canvas.line(x, y + headerHeight, x + width, y + headerHeight, REPORT_SOFT_RULE, 0.45);
        }
        double bodyTop = y + headerHeight;
        for (int i = 0; i < rows.length; i++) {
            double rowTop = bodyTop + i * rowHeight;
            if (i % 2 == 1) {
                canvas.fillRect(x + 0.4, rowTop, width - 0.8, rowHeight, REPORT_TABLE_STRIPE);
            }
            String[] row = rows[i];
            drawRow(canvas, x, rowTop + 2.6, width, row, columns, rightAligned, REPORT_DATA_FONT, 5.95,
                    REPORT_TEXT);
        }
        return bottom;
    }

    /**
     * Draws one table row.
     * @param canvas SVG canvas builder
     * @param x x coordinate
     * @param y y coordinate
     * @param width width in pixels
     * @param row row data
     * @param columns table columns
     * @param rightAligned true to right-align the value
     * @param font font name
     * @param size size in pixels or points
     * @param color display color
     */
    private static void drawRow(Canvas canvas, double x, double y, double width, String[] row, double[] columns,
            boolean[] rightAligned, Font font, double size, Color color) {
        double cursor = x;
        for (int i = 0; i < row.length && i < columns.length; i++) {
            double columnWidth = width * columns[i];
            boolean right = i < rightAligned.length && rightAligned[i];
            drawCell(canvas, cursor, y, columnWidth, font, size, color, row[i], right);
            cursor += columnWidth;
        }
    }

    /**
     * Draws one table cell.
     * @param canvas SVG canvas builder
     * @param x x coordinate
     * @param y y coordinate
     * @param width width in pixels
     * @param font font name
     * @param size size in pixels or points
     * @param color display color
     * @param text text to render or parse
     * @param rightAligned true to right-align the value
     */
    private static void drawCell(Canvas canvas, double x, double y, double width, Font font, double size, Color color,
            String text, boolean rightAligned) {
        double pad = 3.0;
        String safe = fitCellText(font, size, text == null ? "" : text, Math.max(0.0, width - pad * 2.0));
        if (rightAligned) {
            drawRight(canvas, x + width - pad, y, font, size, color, safe);
        } else {
            canvas.drawText(x + pad, y, font, size, color, safe);
        }
    }

    /**
     * Fits table text into one cell.
     *
     * @param font measurement font
     * @param size font size
     * @param text source text
     * @param maxWidth available text width
     * @return original text or a shortened text ending with {@code ...}
     */
    private static String fitCellText(Font font, double size, String text, double maxWidth) {
        String safe = text == null ? "" : text;
        if (safe.isEmpty() || maxWidth <= 0.0 || font.textWidth(safe, size) <= maxWidth) {
            return maxWidth <= 0.0 ? "" : safe;
        }
        String ellipsis = "...";
        if (font.textWidth(ellipsis, size) > maxWidth) {
            return "";
        }
        String base = safe.stripTrailing();
        while (!base.isEmpty() && font.textWidth(base + ellipsis, size) > maxWidth) {
            base = base.substring(0, base.length() - 1).stripTrailing();
        }
        return base.isEmpty() ? ellipsis : base + ellipsis;
    }

    /**
     * Builds difficulty bucket rows.
     * @param rows data rows
     * @return built difficulty bucket rows
     */
    private static String[][] difficultyRows(List<PuzzleRatingRow> rows) {
        String[] names = { "Very easy", "Easy", "Medium", "Hard", "Very hard" };
        int[] lo = { 600, 1040, 1370, 1810, 2140 };
        int[] hi = { 1039, 1369, 1809, 2139, 3000 };
        String[][] out = new String[names.length][5];
        for (int i = 0; i < names.length; i++) {
            List<PuzzleRatingRow> bucket = rowsInRange(rows, lo[i], hi[i]);
            out[i] = new String[] {
                    names[i],
                    num(bucket.size()),
                    pct(bucket.size(), rows.size()),
                    Integer.toString(percentile(bucket, 0.50)),
                    Integer.toString(percentile(bucket, 0.90))
            };
        }
        return out;
    }

    /**
     * Builds top feature rows.
     * @param rows data rows
     * @return built top feature rows
     */
    private static String[][] featureRows(List<PuzzleRatingRow> rows) {
        String[] features = {
                "forcing_key", "multiple_pieces", "quiet_key",
                "multi_move", "ambiguous_candidates", "nonforcing"
        };
        String[][] out = new String[features.length][3];
        for (int i = 0; i < features.length; i++) {
            int count = countFeature(rows, features[i]);
            out[i] = new String[] { features[i], num(count), pct(count, rows.size()) };
        }
        return out;
    }

    /**
     * Builds structure rows.
     * @param rows data rows
     * @param stats statistics data
     * @return built structure rows
     */
    private static String[][] structureRows(List<PuzzleRatingRow> rows, PuzzleRatingStats stats) {
        return new String[][] {
                { "Sample rows", num(rows.size()) },
                { "Heuristic rating span", percentile(rows, 0.00) + "-" + percentile(rows, 1.00) },
                { "Raw score range",
                        String.format(Locale.ROOT, "%.5f to %.5f", stats.rawMin(), stats.rawMax()) },
                { "Explicit continuation nodes",
                        num(stats.explicitNodes()) + " (" + pct(stats.explicitNodes(), rows.size()) + ")" },
                { "Rows with branch points",
                        num(stats.branchRows()) + " (" + pct(stats.branchRows(), rows.size()) + ")" },
                { "Avg nodes / replies / branches",
                        String.format(Locale.ROOT, "%.2f / %.2f / %.3f",
                                stats.avgNodes(), stats.avgReplies(), stats.avgBranches()) },
                { "Max nodes / plies / replies / br.",
                        stats.maxNodes() + " / " + stats.maxPlies() + " / " + stats.maxReplies() + " / "
                                + stats.maxBranches() }
        };
    }

    /**
     * Builds the hardest-row appendix table.
     *
     * @param rows report input rows
     * @param limit maximum rows
     * @return table rows
     */
    private static String[][] hardestRows(List<PuzzleRatingRow> rows, int limit) {
        List<PuzzleRatingRow> top = topRows(rows, hardEvidenceComparator(), limit);
        String[][] out = new String[top.size()][10];
        for (int i = 0; i < top.size(); i++) {
            PuzzleRatingRow row = top.get(i);
            out[i] = new String[] {
                    Integer.toString(i + 1),
                    Integer.toString(row.index()),
                    Integer.toString(row.rating()),
                    keySan(row),
                    Integer.toString(row.plies()),
                    Integer.toString(row.nodes()),
                    Integer.toString(row.replies()),
                    Integer.toString(row.branches()),
                    Integer.toString(row.cheapRank()),
                    compactFeatureSummary(row)
            };
        }
        return out;
    }

    /**
     * Builds the calculation-load appendix table.
     *
     * @param rows report input rows
     * @param limit maximum rows
     * @return table rows
     */
    private static String[][] calculationLoadRows(List<PuzzleRatingRow> rows, int limit) {
        Comparator<PuzzleRatingRow> comparator = Comparator
                .comparingInt(PuzzleRatingRow::plies)
                .thenComparingInt(PuzzleRatingRow::nodes)
                .thenComparingInt(PuzzleRatingRow::branches)
                .thenComparingInt(PuzzleRatingRow::replies)
                .thenComparingInt(PuzzleRatingRow::rating)
                .thenComparingInt(PuzzleRatingRow::cheapRank)
                .thenComparingInt(PuzzleRatingRow::index);
        List<PuzzleRatingRow> top = topRows(rows, comparator, limit);
        String[][] out = new String[top.size()][6];
        for (int i = 0; i < top.size(); i++) {
            PuzzleRatingRow row = top.get(i);
            out[i] = new String[] {
                    Integer.toString(row.index()),
                    Integer.toString(row.rating()),
                    keySan(row),
                    Integer.toString(row.plies()),
                    Integer.toString(row.nodes()),
                    Integer.toString(row.branches())
            };
        }
        return out;
    }

    /**
     * Builds rare tactical signal rows.
     *
     * @param rows report input rows
     * @return table rows
     */
    private static String[][] rareSignalRows(List<PuzzleRatingRow> rows) {
        String[] features = {
                "underpromotion", "en_passant", "castling", "promotion",
                "special_move", "draw_resource", "mate_key", "sacrifice_or_concession"
        };
        String[][] out = new String[features.length][4];
        for (int i = 0; i < features.length; i++) {
            int count = countFeature(rows, features[i]);
            out[i] = new String[] {
                    featureLabel(features[i]),
                    num(count),
                    rarePct(count, rows.size()),
                    count == 0 ? "-" : Integer.toString(highestFeatureRating(rows, features[i]))
            };
        }
        return out;
    }

    /**
     * Builds the feature mix table for the very-hard band.
     *
     * @param rows report input rows
     * @param limit maximum rows
     * @return table rows
     */
    private static String[][] hardTailSignalRows(List<PuzzleRatingRow> rows, int limit) {
        List<PuzzleRatingRow> veryHard = rowsInRange(rows, 2140, MAX_RATING);
        Map<String, Integer> counts = featureCounts(veryHard);
        List<Map.Entry<String, Integer>> entries = counts.entrySet().stream()
                .sorted((a, b) -> {
                    int count = Integer.compare(b.getValue(), a.getValue());
                    return count != 0 ? count : a.getKey().compareTo(b.getKey());
                })
                .limit(limit)
                .toList();
        String[][] out = new String[entries.size()][4];
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, Integer> entry = entries.get(i);
            out[i] = new String[] {
                    featureLabel(entry.getKey()),
                    num(entry.getValue()),
                    pct(entry.getValue(), veryHard.size()),
                    Integer.toString(highestFeatureRating(veryHard, entry.getKey()))
            };
        }
        return out;
    }

    /**
     * Builds the appendix band evidence table.
     *
     * @param rows report input rows
     * @return table rows
     */
    private static String[][] appendixBandRows(List<PuzzleRatingRow> rows) {
        String[] names = { "Very easy", "Easy", "Medium", "Hard", "Very hard" };
        int[] lo = { 600, 1040, 1370, 1810, 2140 };
        int[] hi = { 1039, 1369, 1809, 2139, 3000 };
        String[][] out = new String[names.length][6];
        for (int i = 0; i < names.length; i++) {
            List<PuzzleRatingRow> bucket = rowsInRange(rows, lo[i], hi[i]);
            out[i] = new String[] {
                    names[i],
                    num(bucket.size()),
                    avg(bucket, PuzzleRatingRow::cheapRank),
                    avg(bucket, PuzzleRatingRow::plies),
                    avg(bucket, PuzzleRatingRow::nodes),
                    pct(bucket.stream().filter(row -> row.branches() > 0).count(), bucket.size())
            };
        }
        return out;
    }

}
