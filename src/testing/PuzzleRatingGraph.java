package testing;

import static testing.PuzzleRatingCsvTool.*;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;

/**
 * PNG distribution graph writer for {@link PuzzleRatingCsvTool}.
 */
final class PuzzleRatingGraph {

    /**
     * Utility class; prevent instantiation.
     */
    private PuzzleRatingGraph() {
        // utility
    }

    /**
     * Writes the puzzle rating distribution graph as PNG.
     *
     * @param output output image path
     * @param rows puzzle rating rows
     * @throws IOException on write failure
     */
    static void writePng(Path output, List<PuzzleRatingRow> rows) throws IOException {
        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        int width = 2520;
        int height = 1275;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.scale(width / 1680.0, height / 850.0);
            drawDistributionGraph(g, rows, 1680, 850);
        } finally {
            g.dispose();
        }
        ImageIO.write(image, "png", output.toFile());
    }

    /**
     * Draws the standalone distribution graph with Java2D.
     * @param g graphics context
     * @param rows data rows
     * @param width width in pixels
     * @param height height in pixels
     */
    private static void drawDistributionGraph(Graphics2D g, List<PuzzleRatingRow> rows, int width, int height) {
        int left = 88;
        int right = 52;
        int top = 118;
        int bottom = 96;
        int plotW = width - left - right;
        int plotH = height - top - bottom;
        int[] bins = histogram(rows, DISPLAY_BIN_WIDTH);
        double[] percentages = percentages(bins, rows.size());
        double yMax = yMax(percentages);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);

        drawGraphTitle(g, rows, left);
        drawGraphDifficultyBands(g, rows, left, top, plotW, plotH);
        drawGraphGrid(g, left, top, plotW, plotH, yMax);
        drawGraphBars(g, percentages, left, top, plotW, plotH, yMax);
        drawGraphTrendLine(g, movingAverage(percentages, distributionTrendWindowBins()), left, top, plotW, plotH,
                yMax);
        drawGraphAxes(g, left, top, plotW, plotH);
        drawGraphQuantiles(g, rows, left, top, plotW, plotH);
        drawGraphDifficultyBandLabels(g, rows, left, top, plotW);
    }

    /**
     * Draws graph title and subtitle.
     * @param g graphics context
     * @param rows data rows
     * @param left left coordinate
     */
    private static void drawGraphTitle(Graphics2D g, List<PuzzleRatingRow> rows, int left) {
        g.setColor(REPORT_TEXT);
        g.setFont(new java.awt.Font(java.awt.Font.SERIF, java.awt.Font.BOLD, 28));
        g.drawString("Heuristic puzzle rating distribution", left, 42);
        g.setColor(REPORT_MUTED);
        g.setFont(new java.awt.Font(java.awt.Font.SERIF, java.awt.Font.PLAIN, 13));
        g.drawString(num(rows.size()) + " puzzles; " + DISPLAY_BIN_WIDTH + "-point bins with "
                + DISTRIBUTION_TREND_POINTS + "-point trend; p10/median/p90/p99 = "
                + percentile(rows, 0.10) + " / " + percentile(rows, 0.50)
                + " / " + percentile(rows, 0.90) + " / " + percentile(rows, 0.99), left, 70);
    }

    /**
     * Draws color-coded graph difficulty band backgrounds.
     * @param g graphics context
     * @param rows data rows
     * @param left left coordinate
     * @param top top coordinate
     * @param plotW plot width in pixels
     * @param plotH plot height in pixels
     */
    private static void drawGraphDifficultyBands(Graphics2D g, List<PuzzleRatingRow> rows, int left, int top, int plotW,
            int plotH) {
        int[] starts = { MIN_RATING, 1040, 1370, 1810, 2140 };
        int[] ends = { 1039, 1369, 1809, 2139, MAX_RATING };
        Color[] fills = {
                new Color(223, 243, 234), new Color(228, 242, 251), new Color(255, 242, 204),
                new Color(255, 226, 203), new Color(248, 215, 222)
        };
        for (int i = 0; i < starts.length; i++) {
            double x1 = ratingX(starts[i], left, plotW);
            double x2 = ratingX(ends[i] + 1, left, plotW);
            g.setColor(fills[i]);
            g.fill(new java.awt.geom.Rectangle2D.Double(x1, top, x2 - x1, plotH));
        }
    }

    /**
     * Draws graph difficulty band labels above percentile dividers.
     * @param g graphics context
     * @param rows data rows
     * @param left left coordinate
     * @param top top coordinate
     * @param plotW plot width in pixels
     */
    private static void drawGraphDifficultyBandLabels(Graphics2D g, List<PuzzleRatingRow> rows, int left, int top, int plotW) {
        int[] starts = { MIN_RATING, 1040, 1370, 1810, 2140 };
        int[] ends = { 1039, 1369, 1809, 2139, MAX_RATING };
        String[] labels = { "Very easy", "Easy", "Medium", "Hard", "Very hard" };
        Color[] text = {
                new Color(30, 107, 88), new Color(30, 102, 140), new Color(138, 90, 5),
                new Color(148, 66, 19), new Color(152, 33, 61)
        };
        g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 11));
        for (int i = 0; i < starts.length; i++) {
            double x1 = ratingX(starts[i], left, plotW);
            g.setColor(text[i]);
            g.drawString(labels[i], (float) x1 + 6.0f, top + 18.0f);
            g.setColor(REPORT_MUTED);
            g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 11));
            g.drawString(percentInRange(rows, starts[i], ends[i]), (float) x1 + 6.0f, top + 34.0f);
            g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 11));
        }
    }

    /**
     * Draws graph grid lines and y labels.
     * @param g graphics context
     * @param left left coordinate
     * @param top top coordinate
     * @param plotW plot width in pixels
     * @param plotH plot height in pixels
     * @param yMax maximum y value
     */
    private static void drawGraphGrid(Graphics2D g, int left, int top, int plotW, int plotH, double yMax) {
        g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 11));
        g.setStroke(new BasicStroke(0.8f));
        int ticks = 4;
        for (int i = 0; i <= ticks; i++) {
            double value = yMax * i / ticks;
            double y = top + plotH - plotH * value / yMax;
            g.setColor(REPORT_GRID);
            g.draw(new java.awt.geom.Line2D.Double(left, y, (double) left + plotW, y));
            g.setColor(REPORT_MUTED);
            drawRight(g, left - 12.0, y + 4.0, percent(value));
        }
    }

    /**
     * Draws a contiguous graph histogram fill.
     * @param g graphics context
     * @param percentages percentage values
     * @param left left coordinate
     * @param top top coordinate
     * @param plotW plot width in pixels
     * @param plotH plot height in pixels
     * @param yMax maximum y value
     */
    private static void drawGraphBars(Graphics2D g, double[] percentages, int left, int top, int plotW, int plotH,
            double yMax) {
        double bw = plotW / (double) percentages.length;
        double base = (double) top + plotH;
        Path2D path = new Path2D.Double();
        path.moveTo(left, base);
        for (int i = 0; i < percentages.length; i++) {
            double h = plotH * percentages[i] / yMax;
            double x1 = left + i * bw;
            double x2 = left + ((double) i + 1.0) * bw;
            double y = base - h;
            path.lineTo(x1, y);
            path.lineTo(x2, y);
        }
        path.lineTo((double) left + plotW, base);
        path.closePath();
        g.setColor(REPORT_BAR);
        g.fill(path);
    }

    /**
     * Draws a smoothed trend line over the standalone histogram.
     * @param g graphics context
     * @param smoothed smoothed value
     * @param left left coordinate
     * @param top top coordinate
     * @param plotW plot width in pixels
     * @param plotH plot height in pixels
     * @param yMax maximum y value
     */
    private static void drawGraphTrendLine(Graphics2D g, double[] smoothed, int left, int top, int plotW, int plotH,
            double yMax) {
        if (smoothed.length == 0) {
            return;
        }
        double bw = plotW / (double) smoothed.length;
        Path2D path = new Path2D.Double();
        for (int i = 0; i < smoothed.length; i++) {
            double x = left + (i + 0.5) * bw;
            double y = top + plotH - plotH * smoothed[i] / yMax;
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        g.setColor(REPORT_TREND);
        g.setStroke(new BasicStroke(2.2f));
        g.draw(path);
    }

    /**
     * Draws graph axes and x labels.
     * @param g graphics context
     * @param left left coordinate
     * @param top top coordinate
     * @param plotW plot width in pixels
     * @param plotH plot height in pixels
     */
    private static void drawGraphAxes(Graphics2D g, int left, int top, int plotW, int plotH) {
        g.setColor(REPORT_RULE);
        g.setStroke(new BasicStroke(1.2f));
        g.drawLine(left, top + plotH, left + plotW, top + plotH);
        g.drawLine(left, top, left, top + plotH);
        g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 11));
        for (int rating = MIN_RATING; rating <= MAX_RATING; rating += 100) {
            double x = ratingX(rating, left, plotW);
            g.draw(new java.awt.geom.Line2D.Double(x, (double) top + plotH, x, (double) top + plotH + 5.0));
            g.setColor(REPORT_MUTED);
            drawCentered(g, x, top + plotH + 22.0, Integer.toString(rating));
            g.setColor(REPORT_RULE);
        }
        g.setColor(REPORT_MUTED);
        g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 12));
        drawCentered(g, (double) left + plotW / 2.0, (double) top + plotH + 58.0, "Puzzle rating");
        AffineTransform old = g.getTransform();
        g.rotate(-Math.PI / 2.0, left - 56.0, (double) top + plotH / 2.0);
        drawCentered(g, left - 56.0, (double) top + plotH / 2.0,
                "% of puzzles per " + DISPLAY_BIN_WIDTH + " rating points");
        g.setTransform(old);
    }

    /**
     * Draws graph percentile markers.
     * @param g graphics context
     * @param rows data rows
     * @param left left coordinate
     * @param top top coordinate
     * @param plotW plot width in pixels
     * @param plotH plot height in pixels
     */
    private static void drawGraphQuantiles(Graphics2D g, List<PuzzleRatingRow> rows, int left, int top, int plotW, int plotH) {
        double[] ps = { 0.10, 0.25, 0.50, 0.75, 0.90, 0.99 };
        String[] labels = { "p10", "p25", "median", "p75", "p90", "p99" };
        g.setColor(new Color(128, 128, 128, 92));
        g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 11));
        g.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f,
                new float[] { 3.0f, 6.0f }, 0.0f));
        for (int i = 0; i < ps.length; i++) {
            int rating = percentile(rows, ps[i]);
            double x = ratingX(rating, left, plotW);
            g.draw(new java.awt.geom.Line2D.Double(x, top, x, (double) top + plotH));
            g.setColor(REPORT_MUTED);
            drawCentered(g, x, top - 9.0, labels[i] + " " + rating);
            g.setColor(new Color(128, 128, 128, 92));
        }
    }

    /**
     * Converts a rating to plot x-coordinate.
     * @param rating rating value
     * @param left left coordinate
     * @param plotW plot width in pixels
     * @return rating x result
     */
    private static double ratingX(int rating, int left, int plotW) {
        double range = (double) MAX_RATING - MIN_RATING + 1.0;
        return left + plotW * (rating - MIN_RATING) / range;
    }

    /**
     * Builds a rating histogram.
     * @param rows data rows
     * @param binWidth bin width value
     * @return histogram result
     */
    static int[] histogram(List<PuzzleRatingRow> rows, int binWidth) {
        int safeBinWidth = Math.max(1, binWidth);
        int[] bins = new int[((MAX_RATING - MIN_RATING) / safeBinWidth) + 1];
        for (PuzzleRatingRow row : rows) {
            int rating = Math.max(MIN_RATING, Math.min(MAX_RATING, row.rating()));
            bins[(rating - MIN_RATING) / safeBinWidth]++;
        }
        return bins;
    }

    /**
     * Converts counts to percentages.
     * @param bins histogram bins
     * @param total total value
     * @return percentages result
     */
    static double[] percentages(int[] bins, int total) {
        double[] out = new double[bins.length];
        if (total <= 0) {
            return out;
        }
        for (int i = 0; i < bins.length; i++) {
            out[i] = bins[i] * 100.0 / total;
        }
        return out;
    }

    /**
     * Smooths a percentage series with a centered moving average.
     * @param values values to inspect
     * @param window window value
     * @return moving average result
     */
    static double[] movingAverage(double[] values, int window) {
        double[] out = new double[values.length];
        if (values.length == 0) {
            return out;
        }
        int radius = Math.max(0, window / 2);
        double running = 0.0;
        int start = 0;
        int end = -1;
        for (int i = 0; i < values.length; i++) {
            int wantedStart = Math.max(0, i - radius);
            int wantedEnd = Math.min(values.length - 1, i + radius);
            while (end < wantedEnd) {
                running += values[++end];
            }
            while (start < wantedStart) {
                running -= values[start++];
            }
            out[i] = running / (end - start + 1);
        }
        return out;
    }

    /**
     * Converts the configured trend width from Elo points to display bins.
     * @return distribution trend window bins result
     */
    static int distributionTrendWindowBins() {
        int bins = Math.max(1, (int) Math.round(DISTRIBUTION_TREND_POINTS / (double) DISPLAY_BIN_WIDTH));
        return bins % 2 == 0 ? bins + 1 : bins;
    }

    /**
     * Chooses a y-axis max.
     * @param percentages percentage values
     * @return y max result
     */
    static double yMax(double[] percentages) {
        double max = 0.0;
        for (double pct : percentages) {
            max = Math.max(max, pct);
        }
        return Math.max(0.10, Math.ceil(max * 120.0) / 100.0);
    }

    /**
     * Returns percentage in a rating interval.
     * @param rows data rows
     * @param lo lo value
     * @param hi hi value
     * @return percent in range result
     */
    static String percentInRange(List<PuzzleRatingRow> rows, int lo, int hi) {
        if (rows.isEmpty()) {
            return "0.0%";
        }
        int count = 0;
        for (PuzzleRatingRow row : rows) {
            if (row.rating() >= lo && row.rating() <= hi) {
                count++;
            }
        }
        return String.format(Locale.ROOT, "%.1f%%", 100.0 * count / rows.size());
    }

    /**
     * Returns a percentile rating.
     * @param rows data rows
     * @param p point value
     * @return percentile result
     */
    static int percentile(List<PuzzleRatingRow> rows, double p) {
        if (rows.isEmpty()) {
            return MIN_RATING;
        }
        int[] ratings = rows.stream().mapToInt(PuzzleRatingRow::rating).sorted().toArray();
        int index = (int) Math.round(Math.max(0.0, Math.min(ratings.length - 1.0, p * (ratings.length - 1.0))));
        return ratings[index];
    }

    /**
     * Prints summary statistics.
     * @param csv csv value
     * @param png png value
     * @param rows data rows
     */
}
