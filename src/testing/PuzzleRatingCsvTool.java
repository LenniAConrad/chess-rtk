package testing;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import application.cli.RecordIO;
import chess.book.render.MoveText;
import chess.book.render.NotationPieceSvg;
import chess.book.collection.Builder;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Position;
import chess.core.SAN;
import chess.images.render.Render;
import chess.pdf.document.Canvas;
import chess.pdf.document.Document;
import chess.pdf.document.Font;
import chess.pdf.document.Page;
import chess.pdf.document.PageSize;
import chess.uci.Analysis;
import chess.uci.Evaluation;
import chess.uci.Output;
import utility.Json;

/**
 * Redraws report artifacts from an already scored puzzle CSV without changing
 * the scorer's direct ratings.
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class PuzzleRatingCsvTool {

    /**
     * Rating bucket width.
     */
    private static final int BIN_WIDTH = 1;

    /**
     * Display bucket width for distribution charts.
     *
     * <p>
     * Ratings remain exact in CSV/report statistics, but the visual chart groups
     * neighboring ratings so a small sample does not read as a forest of 1-point
     * spikes.
     * </p>
     */
    private static final int DISPLAY_BIN_WIDTH = 10;

    /**
     * Moving-average trend width for distribution charts, in rating points.
     */
    private static final int DISTRIBUTION_TREND_POINTS = 180;

    /**
     * Lowest rating.
     */
    private static final int MIN_RATING = 600;

    /**
     * Highest rating.
     */
    private static final int MAX_RATING = 3000;

    /**
     * Report timestamp format.
     */
    private static final DateTimeFormatter REPORT_TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm z", Locale.ROOT);

    /**
     * Native board-renderer pixel size, matching the book renderer.
     */
    private static final int REPORT_BOARD_PIXELS = 900;

    /**
     * Default board fill emitted by the CRTK board SVG.
     */
    private static final String REPORT_BOARD_GRID_FILL = "#b2b2b2";

    /**
     * Default light-square fill emitted by the CRTK board SVG.
     */
    private static final String REPORT_BOARD_LIGHT_FILL = "#e5e5e5";

    /**
     * Default dark-square fill emitted by the CRTK board SVG.
     */
    private static final String REPORT_BOARD_DARK_FILL = "#cccccc";

    /**
     * Default inline coordinate fill emitted by the CRTK board SVG renderer.
     */
    private static final String REPORT_BOARD_COORDINATE_FILL = "#8c8c8c";

    /**
     * Default frame fill emitted by the CRTK board SVG renderer.
     */
    private static final String REPORT_RENDER_FRAME_FILL = "#646464";

    /**
     * Softer chess-web-inspired board frame color.
     */
    private static final String REPORT_WEB_BOARD_FRAME_FILL = "#8a6848";

    /**
     * Chess-web-inspired light-square color from {@code WebGuiTheme.light()}.
     */
    private static final String REPORT_WEB_BOARD_LIGHT_FILL = "#efd6ab";

    /**
     * Chess-web-inspired dark-square color from {@code WebGuiTheme.light()}.
     */
    private static final String REPORT_WEB_BOARD_DARK_FILL = "#bc8d62";

    /**
     * Muted coordinate detail color for the report board.
     */
    private static final String REPORT_WEB_BOARD_COORDINATE_FILL = "#6f5238";

    /**
     * Matches the generated square path elements in the embedded board SVG.
     */
    private static final Pattern REPORT_BOARD_SQUARE_PATH = Pattern.compile(
            "(?m)^(\\s*)<path fill=\"(" + Pattern.quote(REPORT_BOARD_LIGHT_FILL) + "|"
                    + Pattern.quote(REPORT_BOARD_DARK_FILL)
                    + ")\" stroke=\"none\" d=\"M(\\d+) (\\d+) L(\\d+) (\\d+) L(\\d+) (\\d+) L(\\d+) (\\d+) Z\"/>$");

    /**
     * Report left/right page margin in PDF points.
     */
    private static final double REPORT_MARGIN = 28.0;

    /**
     * Report top page margin in PDF points.
     */
    private static final double REPORT_TOP_MARGIN = 28.0;

    /**
     * Report bottom page margin in PDF points.
     */
    private static final double REPORT_BOTTOM_MARGIN = 28.0;

    /**
     * Report colors.
     */
    private static final Color REPORT_TEXT = new Color(17, 17, 17);
    private static final Color REPORT_MUTED = new Color(74, 85, 98);
    private static final Color REPORT_ACCENT = new Color(111, 82, 56);
    private static final Color REPORT_RULE = new Color(42, 32, 24);
    private static final Color REPORT_GRID = new Color(216, 216, 216);
    private static final Color REPORT_BAR = new Color(132, 185, 216);
    private static final Color REPORT_TREND = new Color(92, 143, 174);
    private static final Color REPORT_SOFT_RULE = new Color(226, 211, 193);
    private static final Color REPORT_CARD = new Color(253, 249, 242);
    private static final Color REPORT_PANEL = new Color(254, 251, 247);
    private static final Color REPORT_TABLE_HEADER = new Color(247, 236, 220);
    private static final Color REPORT_TABLE_STRIPE = new Color(254, 251, 247);
    private static final Color REPORT_TABLE_RULE = new Color(229, 214, 195);
    private static final Color REPORT_BOARD_ACCENT = new Color(138, 104, 72);
    private static final Color REPORT_QUANTILE_LINE = new Color(178, 178, 178);
    private static final Color REPORT_QUANTILE_LABEL = REPORT_MUTED;

    /**
     * Report typography.
     *
     * <p>
     * Latin Modern matches the ChessRTK book renderer and gives the report a
     * printed chess-publication tone. Helvetica remains the compact data face for
     * dense labels, chart axes, and tables.
     * </p>
     */
    private static final Font REPORT_DISPLAY_FONT = Font.LATIN_MODERN_BOLD;
    private static final Font REPORT_SECTION_FONT = Font.LATIN_MODERN_BOLD;
    private static final Font REPORT_BODY_FONT = Font.LATIN_MODERN_ROMAN;
    private static final Font REPORT_BODY_BOLD_FONT = Font.LATIN_MODERN_BOLD;
    private static final Font REPORT_DATA_FONT = Font.HELVETICA;
    private static final Font REPORT_DATA_BOLD_FONT = Font.HELVETICA_BOLD;

    /**
     * Inline notation piece size relative to the surrounding text.
     */
    private static final double REPORT_NOTATION_PIECE_SIZE_SCALE = 1.16;

    /**
     * Inline notation piece left padding relative to the surrounding text.
     */
    private static final double REPORT_NOTATION_PIECE_LEFT_PADDING_SCALE = 0.04;

    /**
     * Inline notation piece right padding relative to the surrounding text.
     */
    private static final double REPORT_NOTATION_PIECE_RIGHT_PADDING_SCALE = 0.19;

    /**
     * Inline notation piece top shift relative to the surrounding text.
     */
    private static final double REPORT_NOTATION_PIECE_TOP_SHIFT_SCALE = 0.03;

    /**
     * Mate score base used to mirror record-PGN branch sorting.
     */
    private static final int REPORT_MATE_SORT_SCORE_BASE = 100_000;

    /**
     * Utility class; prevent instantiation.
     */
    private PuzzleRatingCsvTool() {
        // utility
    }

    /**
     * Runs the CSV report tool.
     *
     * @param args command-line arguments
     * @throws IOException if reading or writing fails
     */
    public static void main(String[] args) throws IOException {
        Arguments parsed = parseArguments(args);
        if (parsed == null) {
            System.err.println("Usage: java -cp out testing.PuzzleRatingCsvTool "
                    + "[--out-prefix out/prefix] [--report out/report.pdf] "
                    + "[--records records.jsonl] <scored-puzzles.csv>");
            System.exit(2);
        }
        List<Row> rows = readRows(parsed.input());
        rows.sort(Comparator.comparingInt(Row::index));
        Path csv = Path.of(parsed.prefix() + ".csv");
        Path png = Path.of(parsed.prefix() + ".png");
        writeCsv(csv, rows);
        writePng(png, rows);
        if (parsed.report() != null) {
            writeReport(parsed.report(), rows, parsed.records());
        }
        printSummary(csv, png, rows);
    }

    /**
     * Parses command-line arguments.
     */
    private static Arguments parseArguments(String[] args) {
        if (args.length == 0) {
            return null;
        }
        String prefix = null;
        Path report = null;
        Path input = null;
        List<Path> records = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if ("--out-prefix".equals(args[i])) {
                if (++i >= args.length) {
                    return null;
                }
                prefix = args[i];
            } else if ("--report".equals(args[i])) {
                if (++i >= args.length) {
                    return null;
                }
                report = Path.of(args[i]);
            } else if ("--records".equals(args[i]) || "--record".equals(args[i])) {
                if (++i >= args.length) {
                    return null;
                }
                records.add(Path.of(args[i]));
            } else if (args[i].startsWith("-")) {
                return null;
            } else if (input == null) {
                input = Path.of(args[i]);
            } else {
                return null;
            }
        }
        if (input == null) {
            return null;
        }
        if (prefix == null) {
            String name = input.getFileName().toString();
            int dot = name.lastIndexOf('.');
            String stem = dot > 0 ? name.substring(0, dot) : name;
            Path parent = input.getParent();
            Path out = Path.of(stem + "-positive-skew");
            prefix = (parent == null ? out : parent.resolve(out)).toString();
        }
        return new Arguments(input, prefix, report, List.copyOf(records));
    }

    /**
     * Reads CSV rows.
     */
    private static List<Row> readRows(Path input) throws IOException {
        List<Row> rows = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null || !header.startsWith("index,")) {
                throw new IOException("not a puzzle difficulty CSV: " + input);
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> fields = parseCsv(line);
                if (fields.size() < 22) {
                    continue;
                }
                rows.add(new Row(Integer.parseInt(fields.get(0)), fields,
                        Double.parseDouble(fields.get(4)), fields.get(21)));
            }
        }
        return rows;
    }

    /**
     * Writes scored CSV rows.
     */
    private static void writeCsv(Path output, List<Row> rows) throws IOException {
        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            writer.write("index,goal,rating,score,raw_score,label,solution,cheap_best,cheap_rank,deep_best_cp,"
                    + "deep_second_cp,deep_margin_cp,cheap_static_cp,cheap_best_cp,cheap_solution_cp,"
                    + "legal_moves,explicit_tree_plies,root_reply_count,tree_node_count,branch_point_count,"
                    + "features,fen");
            writer.newLine();
            for (Row row : rows) {
                writer.write(toCsvLine(row.fields()));
                writer.newLine();
            }
        }
    }

    /**
     * Writes a native Java-rendered PNG histogram.
     */
    private static void writePng(Path output, List<Row> rows) throws IOException {
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
     */
    private static void drawDistributionGraph(Graphics2D g, List<Row> rows, int width, int height) {
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
     */
    private static void drawGraphTitle(Graphics2D g, List<Row> rows, int left) {
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
     */
    private static void drawGraphDifficultyBands(Graphics2D g, List<Row> rows, int left, int top, int plotW,
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
     */
    private static void drawGraphDifficultyBandLabels(Graphics2D g, List<Row> rows, int left, int top, int plotW) {
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
     */
    private static void drawGraphGrid(Graphics2D g, int left, int top, int plotW, int plotH, double yMax) {
        g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 11));
        g.setStroke(new BasicStroke(0.8f));
        int ticks = 4;
        for (int i = 0; i <= ticks; i++) {
            double value = yMax * i / ticks;
            double y = top + plotH - plotH * value / yMax;
            g.setColor(REPORT_GRID);
            g.draw(new java.awt.geom.Line2D.Double(left, y, left + plotW, y));
            g.setColor(REPORT_MUTED);
            drawRight(g, left - 12.0, y + 4.0, percent(value));
        }
    }

    /**
     * Draws a contiguous graph histogram fill.
     */
    private static void drawGraphBars(Graphics2D g, double[] percentages, int left, int top, int plotW, int plotH,
            double yMax) {
        double bw = plotW / (double) percentages.length;
        double base = top + plotH;
        Path2D path = new Path2D.Double();
        path.moveTo(left, base);
        for (int i = 0; i < percentages.length; i++) {
            double h = plotH * percentages[i] / yMax;
            double x1 = left + i * bw;
            double x2 = left + (i + 1) * bw;
            double y = base - h;
            path.lineTo(x1, y);
            path.lineTo(x2, y);
        }
        path.lineTo(left + plotW, base);
        path.closePath();
        g.setColor(REPORT_BAR);
        g.fill(path);
    }

    /**
     * Draws a smoothed trend line over the standalone histogram.
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
     */
    private static void drawGraphAxes(Graphics2D g, int left, int top, int plotW, int plotH) {
        g.setColor(REPORT_RULE);
        g.setStroke(new BasicStroke(1.2f));
        g.drawLine(left, top + plotH, left + plotW, top + plotH);
        g.drawLine(left, top, left, top + plotH);
        g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 11));
        for (int rating = MIN_RATING; rating <= MAX_RATING; rating += 100) {
            double x = ratingX(rating, left, plotW);
            g.draw(new java.awt.geom.Line2D.Double(x, top + plotH, x, top + plotH + 5.0));
            g.setColor(REPORT_MUTED);
            drawCentered(g, x, top + plotH + 22.0, Integer.toString(rating));
            g.setColor(REPORT_RULE);
        }
        g.setColor(REPORT_MUTED);
        g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 12));
        drawCentered(g, left + plotW / 2.0, top + plotH + 58.0, "Puzzle rating");
        AffineTransform old = g.getTransform();
        g.rotate(-Math.PI / 2.0, left - 56.0, top + plotH / 2.0);
        drawCentered(g, left - 56.0, top + plotH / 2.0,
                "% of puzzles per " + DISPLAY_BIN_WIDTH + " rating points");
        g.setTransform(old);
    }

    /**
     * Draws graph percentile markers.
     */
    private static void drawGraphQuantiles(Graphics2D g, List<Row> rows, int left, int top, int plotW, int plotH) {
        double[] ps = { 0.10, 0.25, 0.50, 0.75, 0.90, 0.99 };
        String[] labels = { "p10", "p25", "median", "p75", "p90", "p99" };
        g.setColor(new Color(128, 128, 128, 92));
        g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 11));
        g.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f,
                new float[] { 3.0f, 6.0f }, 0.0f));
        for (int i = 0; i < ps.length; i++) {
            int rating = percentile(rows, ps[i]);
            double x = ratingX(rating, left, plotW);
            g.draw(new java.awt.geom.Line2D.Double(x, top, x, top + plotH));
            g.setColor(REPORT_MUTED);
            drawCentered(g, x, top - 9.0, labels[i] + " " + rating);
            g.setColor(new Color(128, 128, 128, 92));
        }
    }

    /**
     * Converts a rating to plot x-coordinate.
     */
    private static double ratingX(int rating, int left, int plotW) {
        double range = (double) MAX_RATING - MIN_RATING + 1.0;
        return left + plotW * (rating - MIN_RATING) / range;
    }

    /**
     * Builds a rating histogram.
     */
    private static int[] histogram(List<Row> rows, int binWidth) {
        int safeBinWidth = Math.max(1, binWidth);
        int[] bins = new int[((MAX_RATING - MIN_RATING) / safeBinWidth) + 1];
        for (Row row : rows) {
            int rating = Math.max(MIN_RATING, Math.min(MAX_RATING, row.rating()));
            bins[(rating - MIN_RATING) / safeBinWidth]++;
        }
        return bins;
    }

    /**
     * Converts counts to percentages.
     */
    private static double[] percentages(int[] bins, int total) {
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
     */
    private static double[] movingAverage(double[] values, int window) {
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
     */
    private static int distributionTrendWindowBins() {
        int bins = Math.max(1, (int) Math.round(DISTRIBUTION_TREND_POINTS / (double) DISPLAY_BIN_WIDTH));
        return bins % 2 == 0 ? bins + 1 : bins;
    }

    /**
     * Chooses a y-axis max.
     */
    private static double yMax(double[] percentages) {
        double max = 0.0;
        for (double pct : percentages) {
            max = Math.max(max, pct);
        }
        return Math.max(0.10, Math.ceil(max * 120.0) / 100.0);
    }

    /**
     * Returns percentage in a rating interval.
     */
    private static String percentInRange(List<Row> rows, int lo, int hi) {
        if (rows.isEmpty()) {
            return "0.0%";
        }
        int count = 0;
        for (Row row : rows) {
            if (row.rating() >= lo && row.rating() <= hi) {
                count++;
            }
        }
        return String.format(Locale.ROOT, "%.1f%%", 100.0 * count / rows.size());
    }

    /**
     * Returns a percentile rating.
     */
    private static int percentile(List<Row> rows, double p) {
        if (rows.isEmpty()) {
            return MIN_RATING;
        }
        int[] ratings = rows.stream().mapToInt(Row::rating).sorted().toArray();
        int index = (int) Math.round(Math.max(0, Math.min(ratings.length - 1, p * (ratings.length - 1))));
        return ratings[index];
    }

    /**
     * Prints summary statistics.
     */
    private static void printSummary(Path csv, Path png, List<Row> rows) {
        System.out.println("rows: " + rows.size());
        System.out.println("rating min/p10/p25/median/p75/p90/p99/max: "
                + percentile(rows, 0.00) + " / "
                + percentile(rows, 0.10) + " / "
                + percentile(rows, 0.25) + " / "
                + percentile(rows, 0.50) + " / "
                + percentile(rows, 0.75) + " / "
                + percentile(rows, 0.90) + " / "
                + percentile(rows, 0.99) + " / "
                + percentile(rows, 1.00));
        System.out.println("rating mean: " + Math.round(mean(rows)));
        System.out.println("rating skewness: " + String.format(Locale.ROOT, "%.3f", skewness(rows)));
        System.out.println("csv: " + csv);
        System.out.println("png: " + png);
    }

    /**
     * Writes the native PDF report for the current curve.
     */
    private static void writeReport(Path output, List<Row> rows, List<Path> records) throws IOException {
        Path pdf = reportPdfPath(output);
        Path parent = pdf.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        ReportStats stats = reportStats(rows);
        Row snapshot = puzzleSnapshotRow(rows);
        ReportPuzzleDetail snapshotDetail = reportPuzzleDetail(snapshot, records);
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
     * Maps legacy markdown report names to the native PDF artifact.
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
     */
    private static void drawReportPage(Page page, List<Row> rows, ReportStats stats, Row snapshot,
            ReportPuzzleDetail snapshotDetail) {
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
    private static void drawReportAppendixPage(Page page, List<Row> rows, ReportStats stats) {
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
     */
    private static void drawReportHeader(Canvas canvas, double pageWidth, double contentWidth, double top) {
        drawReportHeader(canvas, pageWidth, contentWidth, top,
                "Puzzle Difficulty Report",
                "Independent heuristic ratings, solver complexity, and sample health");
    }

    /**
     * Draws the title block and source metadata.
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
     */
    private static void drawMetricCards(Canvas canvas, double x, double y, double width, List<Row> rows,
            ReportStats stats) {
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
    private static void drawAppendixMetricCards(Canvas canvas, double x, double y, double width, List<Row> rows,
            ReportStats stats) {
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
     */
    private static void drawInterpretationPanel(Canvas canvas, double x, double y, double width, List<Row> rows,
            ReportStats stats) {
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
    private static void drawAppendixNotes(Canvas canvas, double x, double y, double width, List<Row> rows) {
        double height = 68.0;
        List<Row> veryHard = rowsInRange(rows, 2140, MAX_RATING);
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
     */
    private static String readingDistributionBullet(List<Row> rows) {
        DifficultyBand dominant = dominantDifficultyBand(rows);
        int body = rowsInRange(rows, MIN_RATING, 1809).size();
        int hardTail = rowsInRange(rows, 1810, MAX_RATING).size();
        return "Largest band: " + dominant.name() + " " + pct(dominant.count(), rows.size())
                + " (" + num(dominant.count()) + " rows); " + pct(body, rows.size())
                + " below hard, " + pct(hardTail, rows.size()) + " in hard tail.";
    }

    /**
     * Builds the second dynamic reading-curve bullet.
     */
    private static String coverageShapeBullet(List<Row> rows) {
        return "Observed range " + percentile(rows, 0.00) + "-" + percentile(rows, 1.00)
                + "; p99 " + percentile(rows, 0.99) + "; "
                + pct(rowsAtOrBelow(rows, 2140), rows.size()) + " at or below 2140 rating.";
    }

    /**
     * Builds the first dynamic scoring-note bullet.
     */
    private static String directScoringBullet(List<Row> rows) {
        return "No subset calibration: each row is scored independently; p10/median/p90 = "
                + percentile(rows, 0.10) + " / " + percentile(rows, 0.50)
                + " / " + percentile(rows, 0.90) + ".";
    }

    /**
     * Builds the second dynamic scoring-note bullet.
     */
    private static String rawScoreBullet(List<Row> rows, ReportStats stats) {
        return "CSV keeps exact ratings; chart uses " + DISPLAY_BIN_WIDTH + "-point bins and a "
                + DISTRIBUTION_TREND_POINTS + "-point trend only for readability.";
    }

    /**
     * Draws one wrapped bullet.
     */
    private static void drawBullet(Canvas canvas, double x, double y, double width, String text) {
        canvas.fillRect(x, y + 3.0, 2.0, 2.0, REPORT_ACCENT);
        canvas.drawWrappedText(x + 7.0, y, width - 7.0, REPORT_BODY_FONT, 6.2, 7.0, REPORT_MUTED, text);
    }

    /**
     * Draws the chart directly in the PDF.
     */
    private static void drawReportChart(Canvas canvas, List<Row> rows, double x, double y, double width,
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
     */
    private static void drawReportBands(Canvas canvas, List<Row> rows, double left, double top, double plotWidth,
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
     */
    private static void drawReportBandLabels(Canvas canvas, List<Row> rows, double left, double top,
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
     */
    private static void drawReportQuantiles(Canvas canvas, List<Row> rows, double left, double top, double plotWidth,
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
     */
    private static void drawComplexityDriversGraph(Canvas canvas, List<Row> rows, double x, double y, double width,
            double height) {
        ComplexityBand[] bands = complexityBands(rows);
        String[] metricNames = { "Legal moves", "Best-move rank", "Solution plies", "Branch-point rows" };
        double[][] values = {
                metricValues(bands, ComplexityBand::avgLegal),
                metricValues(bands, ComplexityBand::avgRank),
                metricValues(bands, ComplexityBand::avgPlies),
                metricValues(bands, ComplexityBand::branchShare)
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
     */
    private static void drawPuzzleSnapshotPanel(Canvas canvas, Row row, ReportPuzzleDetail detail, double x, double y,
            double width, double height) {
        canvas.fillRect(x, y, width, height, REPORT_PANEL);
        canvas.strokeRect(x, y, width, height, REPORT_SOFT_RULE, 0.45);
        canvas.drawText(x + 8.0, y + 7.0, REPORT_SECTION_FONT, 8.7, REPORT_TEXT,
                "Hardest Puzzle Snapshot");
        if (row == null) {
            canvas.drawWrappedText(x + 8.0, y + 24.0, width - 16.0, REPORT_BODY_FONT, 5.6, 6.6,
                    REPORT_MUTED, "No puzzle rows available.");
            return;
        }

        canvas.drawText(x + 8.0, y + 21.0, REPORT_BODY_FONT, 5.6, REPORT_MUTED,
                "Rating " + row.rating() + " / " + row.label().replace('_', ' ')
                        + "; " + row.plies() + " plies, " + row.nodes() + " nodes");
        double boardSize = Math.min(width - 18.0, height - 132.0);
        boardSize = Math.max(82.0, boardSize);
        double boardX = x + (width - boardSize) / 2.0;
        double boardY = y + 43.0;
        canvas.drawSvg(renderReportBoardSvg(row), boardX, boardY, boardSize, boardSize);

        double infoY = boardY + boardSize + 9.0;
        canvas.drawText(x + 8.0, infoY, REPORT_DATA_BOLD_FONT, 5.8, REPORT_ACCENT,
                sideToMoveLabel(row.fen()).toUpperCase(Locale.ROOT) + " TO MOVE");
        double cursor = infoY + 11.0;
        ReportPuzzleDetail shown = detail == null ? csvOnlyPuzzleDetail(row) : detail;
        canvas.drawText(x + 8.0, cursor, REPORT_DATA_BOLD_FONT, 4.9, REPORT_ACCENT,
                shown.sourceTree() ? "PUZZLE SOLUTION TREE" : "KEY MOVE FROM CSV");
        cursor += 8.0;
        double moveLeading = shown.sourceTree() ? 5.6 : 6.7;
        int maxMoveLines = Math.max(3, Math.min(8, (int) Math.floor((y + height - cursor - 35.0) / moveLeading)));
        cursor += drawWrappedNotationText(canvas, x + 8.0, cursor, width - 16.0, REPORT_BODY_BOLD_FONT,
                shown.sourceTree() ? 4.7 : 5.8, moveLeading, REPORT_TEXT,
                MoveText.figurine(shown.movetext()), maxMoveLines);
        cursor += 4.0;
        cursor += canvas.drawWrappedText(x + 8.0, cursor, width - 16.0, REPORT_BODY_FONT, 5.4, 6.2,
                REPORT_MUTED,
                "Goal " + row.goal() + "; best-move rank " + row.cheapRank() + ".");
        cursor += 4.0;
        String evidence = shown.sourceTree()
                ? "Explicit source tree; CSV evidence: " + row.plies() + " plies, "
                        + row.replies() + " root replies, " + row.nodes() + " nodes."
                : "CSV stores only the key; tree evidence still reports " + row.plies()
                        + " solver plies across " + row.nodes() + " nodes.";
        canvas.drawWrappedText(x + 8.0, cursor, width - 16.0, REPORT_BODY_FONT, 5.1, 5.9, REPORT_MUTED,
                evidence);
    }

    /**
     * Draws wrapped chess notation with SVG figurines in place of SAN piece
     * letters.
     *
     * @param canvas drawing surface
     * @param x left edge in PDF points
     * @param y top edge in PDF points
     * @param width wrap width in PDF points
     * @param font text font for ordinary characters
     * @param fontSize text font size
     * @param leading line advance
     * @param color text fill color
     * @param text figurine-formatted movetext
     * @return consumed vertical space in PDF points
     */
    private static double drawWrappedNotationText(Canvas canvas, double x, double y, double width, Font font,
            double fontSize, double leading, Color color, String text) {
        return drawWrappedNotationText(canvas, x, y, width, font, fontSize, leading, color, text, Integer.MAX_VALUE);
    }

    /**
     * Draws wrapped chess notation with a maximum line count.
     *
     * @param canvas drawing surface
     * @param x left edge in PDF points
     * @param y top edge in PDF points
     * @param width wrap width in PDF points
     * @param font text font for ordinary characters
     * @param fontSize text font size
     * @param leading line advance
     * @param color text fill color
     * @param text figurine-formatted movetext
     * @param maxLines maximum lines to draw
     * @return consumed vertical space in PDF points
     */
    private static double drawWrappedNotationText(Canvas canvas, double x, double y, double width, Font font,
            double fontSize, double leading, Color color, String text, int maxLines) {
        List<String> lines = wrapNotationLines(text, font, fontSize, width);
        if (lines.size() > maxLines) {
            lines = new ArrayList<>(lines.subList(0, Math.max(1, maxLines)));
            int last = lines.size() - 1;
            lines.set(last, fitNotationEllipsis(lines.get(last), font, fontSize, width));
        }
        double cursorY = y;
        for (String line : lines) {
            if (!line.isBlank()) {
                drawNotationText(canvas, x, cursorY, font, fontSize, color, line);
            }
            cursorY += leading;
        }
        return cursorY - y;
    }

    /**
     * Fits an ellipsis onto a notation line.
     *
     * @param line source line
     * @param font measurement font for ordinary characters
     * @param fontSize font size
     * @param width wrap width in PDF points
     * @return shortened line ending with {@code ...}
     */
    private static String fitNotationEllipsis(String line, Font font, double fontSize, double width) {
        String base = line == null ? "" : line.stripTrailing();
        while (!base.isEmpty() && notationTextWidth(font, fontSize, base + "...") > width) {
            base = base.substring(0, base.length() - 1).stripTrailing();
        }
        return base.isEmpty() ? "..." : base + "...";
    }

    /**
     * Wraps a figurine-formatted notation paragraph using SVG piece widths.
     *
     * @param text source movetext
     * @param font measurement font for ordinary characters
     * @param fontSize font size
     * @param width wrap width in PDF points
     * @return wrapped notation lines
     */
    private static List<String> wrapNotationLines(String text, Font font, double fontSize, double width) {
        List<String> lines = new ArrayList<>();
        String safe = normalizeNotationWhitespace(text);
        if (safe.isBlank()) {
            return lines;
        }
        if (width <= 0.0) {
            lines.add(safe.trim());
            return lines;
        }

        String[] paragraphs = safe.split("\n", -1);
        for (String paragraph : paragraphs) {
            if (paragraph.isBlank()) {
                lines.add("");
                continue;
            }
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.trim().split("\\s+")) {
                appendWrappedNotationWord(lines, line, word, font, fontSize, width);
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
            }
        }
        return lines;
    }

    /**
     * Appends one notation word to the current wrapped line.
     *
     * @param lines output lines
     * @param line current line buffer
     * @param word notation word
     * @param font measurement font for ordinary characters
     * @param fontSize font size
     * @param width wrap width in PDF points
     */
    private static void appendWrappedNotationWord(List<String> lines, StringBuilder line, String word, Font font,
            double fontSize, double width) {
        if (line.isEmpty()) {
            if (notationTextWidth(font, fontSize, word) <= width) {
                line.append(word);
            } else {
                appendBrokenNotationWord(lines, line, word, font, fontSize, width);
            }
            return;
        }
        String candidate = line + " " + word;
        if (notationTextWidth(font, fontSize, candidate) <= width) {
            line.setLength(0);
            line.append(candidate);
            return;
        }
        lines.add(line.toString());
        appendBrokenNotationWord(lines, line, word, font, fontSize, width);
    }

    /**
     * Breaks one oversized notation word into line-sized fragments.
     *
     * @param lines output lines
     * @param scratch reusable line buffer
     * @param word notation word
     * @param font measurement font for ordinary characters
     * @param fontSize font size
     * @param width wrap width in PDF points
     */
    private static void appendBrokenNotationWord(List<String> lines, StringBuilder scratch, String word, Font font,
            double fontSize, double width) {
        scratch.setLength(0);
        for (int i = 0; i < word.length(); i++) {
            char ch = word.charAt(i);
            String candidate = scratch.toString() + ch;
            if (!scratch.isEmpty() && notationTextWidth(font, fontSize, candidate) > width) {
                lines.add(scratch.toString());
                scratch.setLength(0);
            }
            scratch.append(ch);
        }
    }

    /**
     * Normalizes notation whitespace while preserving paragraph breaks.
     *
     * @param text source text
     * @return normalized text
     */
    private static String normalizeNotationWhitespace(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').replace('\t', ' ');
        return Pattern.compile("[ ]+").matcher(normalized).replaceAll(" ").trim();
    }

    /**
     * Draws one chess notation line with SVG piece placeholders.
     *
     * @param canvas drawing surface
     * @param x left edge in PDF points
     * @param y top edge in PDF points
     * @param font text font for ordinary characters
     * @param fontSize text font size
     * @param color text fill color
     * @param text figurine-formatted notation line
     */
    private static void drawNotationText(Canvas canvas, double x, double y, Font font, double fontSize, Color color,
            String text) {
        String safe = text == null ? "" : text;
        double cursorX = x;
        StringBuilder run = new StringBuilder();
        for (int i = 0; i < safe.length(); i++) {
            char ch = safe.charAt(i);
            String pieceSvg = NotationPieceSvg.svg(ch);
            if (pieceSvg == null) {
                run.append(ch);
                continue;
            }
            cursorX = drawNotationTextRun(canvas, cursorX, y, font, fontSize, color, run);
            drawNotationPiece(canvas, cursorX, y, fontSize, pieceSvg);
            cursorX += notationPieceAdvance(fontSize);
        }
        drawNotationTextRun(canvas, cursorX, y, font, fontSize, color, run);
    }

    /**
     * Draws one ordinary text run inside chess notation.
     *
     * @param canvas drawing surface
     * @param x left edge in PDF points
     * @param y top edge in PDF points
     * @param font text font
     * @param fontSize text font size
     * @param color text fill color
     * @param run pending text run
     * @return next cursor x-coordinate
     */
    private static double drawNotationTextRun(Canvas canvas, double x, double y, Font font, double fontSize,
            Color color, StringBuilder run) {
        if (run.isEmpty()) {
            return x;
        }
        String text = run.toString();
        canvas.drawText(x, y, font, fontSize, color, text);
        run.setLength(0);
        return x + font.textWidth(text, fontSize);
    }

    /**
     * Draws one inline notation piece.
     *
     * @param canvas drawing surface
     * @param x logical cursor x-coordinate
     * @param y surrounding text top edge
     * @param fontSize surrounding text size
     * @param pieceSvg embedded SVG source
     */
    private static void drawNotationPiece(Canvas canvas, double x, double y, double fontSize, String pieceSvg) {
        double boxSize = notationPieceBoxSize(fontSize);
        double drawX = x + notationPieceLeftPadding(fontSize);
        double drawY = y - fontSize * REPORT_NOTATION_PIECE_TOP_SHIFT_SCALE;
        canvas.drawSvg(pieceSvg, drawX, drawY, boxSize, boxSize);
    }

    /**
     * Measures chess notation with SVG piece advances.
     *
     * @param font measurement font for ordinary characters
     * @param fontSize font size
     * @param text figurine-formatted notation text
     * @return measured width in PDF points
     */
    private static double notationTextWidth(Font font, double fontSize, String text) {
        String safe = text == null ? "" : text;
        double width = 0.0;
        StringBuilder run = new StringBuilder();
        for (int i = 0; i < safe.length(); i++) {
            char ch = safe.charAt(i);
            if (!NotationPieceSvg.isPlaceholder(ch)) {
                run.append(ch);
                continue;
            }
            width += font.textWidth(run.toString(), fontSize);
            run.setLength(0);
            width += notationPieceAdvance(fontSize);
        }
        return width + font.textWidth(run.toString(), fontSize);
    }

    /**
     * Computes the inline SVG square size.
     *
     * @param fontSize surrounding text size
     * @return SVG box size
     */
    private static double notationPieceBoxSize(double fontSize) {
        return fontSize * REPORT_NOTATION_PIECE_SIZE_SCALE;
    }

    /**
     * Computes inline notation piece left padding.
     *
     * @param fontSize surrounding text size
     * @return left padding
     */
    private static double notationPieceLeftPadding(double fontSize) {
        return fontSize * REPORT_NOTATION_PIECE_LEFT_PADDING_SCALE;
    }

    /**
     * Computes inline notation piece right padding.
     *
     * @param fontSize surrounding text size
     * @return right padding
     */
    private static double notationPieceRightPadding(double fontSize) {
        return fontSize * REPORT_NOTATION_PIECE_RIGHT_PADDING_SCALE;
    }

    /**
     * Computes inline notation piece cursor advance.
     *
     * @param fontSize surrounding text size
     * @return cursor advance
     */
    private static double notationPieceAdvance(double fontSize) {
        return notationPieceLeftPadding(fontSize) + notationPieceBoxSize(fontSize)
                + notationPieceRightPadding(fontSize);
    }

    /**
     * Builds the display detail for the report puzzle snapshot.
     *
     * @param row source puzzle row
     * @param records optional original record JSON/JSONL files
     * @return display detail using the original source tree when available
     * @throws IOException if a record source cannot be read
     */
    private static ReportPuzzleDetail reportPuzzleDetail(Row row, List<Path> records) throws IOException {
        if (row == null) {
            return new ReportPuzzleDetail("-", false);
        }
        if (records != null) {
            for (Path source : records) {
                String tree = sourceTreeAlgebraic(row, source);
                if (!tree.isBlank()) {
                    return new ReportPuzzleDetail(tree, true);
                }
            }
        }
        return csvOnlyPuzzleDetail(row);
    }

    /**
     * Builds a display detail from the CSV solution column only.
     *
     * @param row source puzzle row
     * @return CSV-backed display detail
     */
    private static ReportPuzzleDetail csvOnlyPuzzleDetail(Row row) {
        return new ReportPuzzleDetail(solutionAlgebraic(row), false);
    }

    /**
     * Finds and formats the explicit solution tree for one puzzle row.
     *
     * @param row source puzzle row
     * @param source original record JSON/JSONL source
     * @return move-numbered SAN variation tree, or blank when not found
     * @throws IOException if the source cannot be read
     */
    private static String sourceTreeAlgebraic(Row row, Path source) throws IOException {
        if (row == null || source == null || !Files.isRegularFile(source)) {
            return "";
        }
        SourceSolutionNode root = sourceRootNode(row, source);
        if (root == null || root.solution() == Move.NO_MOVE) {
            return "";
        }
        populateSourceTree(root, source, Math.max(1, row.nodes()));
        return formatSourceTree(new Position(row.fen()), root);
    }

    /**
     * Reads the source root node for one puzzle row.
     *
     * @param row source puzzle row
     * @param source original record JSON/JSONL source
     * @return source root node, or {@code null} when unavailable
     * @throws IOException if the source cannot be read
     */
    private static SourceSolutionNode sourceRootNode(Row row, Path source) throws IOException {
        try {
            RecordIO.streamRecordJson(source, json -> {
                if (!row.fen().equals(Json.parseStringField(json, "position"))) {
                    return;
                }
                SourceSolutionNode node = sourceNode(Json.parseStringField(json, "position"),
                        Json.parseStringArrayField(json, "analysis"));
                if (node != null && sourceNodeStartsWithCsvKey(new Position(row.fen()), node, row.solution())) {
                    throw new FoundSourceNode(node);
                }
            });
        } catch (FoundSourceNode found) {
            return found.node;
        }
        return null;
    }

    /**
     * Populates child variations by following explicit record parent links.
     *
     * @param root source solution root
     * @param source original record JSON/JSONL source
     * @param nodeLimit maximum solver nodes to include
     * @throws IOException if the source cannot be read
     */
    private static void populateSourceTree(SourceSolutionNode root, Path source, int nodeLimit) throws IOException {
        List<SourceSolutionNode> frontier = List.of(root);
        int nodes = 1;
        while (!frontier.isEmpty() && nodes < nodeLimit) {
            Map<String, SourceSolutionNode> parents = sourceParentsAfterSolution(frontier);
            if (parents.isEmpty()) {
                return;
            }
            List<SourceSolutionNode> next = new ArrayList<>();
            RecordIO.streamRecordJson(source, json -> {
                SourceSolutionNode parent = parents.get(Json.parseStringField(json, "parent"));
                if (parent == null || sourceTreeSize(root) >= nodeLimit) {
                    return;
                }
                SourceSolutionNode child = sourceNode(Json.parseStringField(json, "position"),
                        Json.parseStringArrayField(json, "analysis"));
                if (child == null || child.solution() == Move.NO_MOVE) {
                    return;
                }
                short reply = sourceReplyMove(parent.afterSolution(), child.position());
                if (reply == Move.NO_MOVE || parent.hasReply(reply)) {
                    return;
                }
                parent.branches().add(new SourceSolutionBranch(reply, child));
                next.add(child);
            });
            nodes = sourceTreeSize(root);
            frontier = next;
        }
    }

    /**
     * Builds a lookup of positions after the solver's move for each frontier node.
     *
     * @param frontier current source-tree frontier
     * @return parent FEN to node map
     */
    private static Map<String, SourceSolutionNode> sourceParentsAfterSolution(List<SourceSolutionNode> frontier) {
        Map<String, SourceSolutionNode> parents = new HashMap<>();
        for (SourceSolutionNode node : frontier) {
            Position after = node.afterSolution();
            if (after != null) {
                parents.put(after.toString(), node);
            }
        }
        return parents;
    }

    /**
     * Creates one source-tree node from raw record fields.
     *
     * @param fen record position
     * @param analysisLines raw UCI analysis lines from the source record
     * @return source node, or {@code null} when unavailable
     */
    private static SourceSolutionNode sourceNode(String fen, String[] analysisLines) {
        if (fen == null || fen.isBlank() || analysisLines == null || analysisLines.length == 0) {
            return null;
        }
        Output best = new Analysis().addAll(analysisLines).getBestOutput(1);
        short solution = firstMove(best == null ? new short[0] : best.getMoves());
        return new SourceSolutionNode(new Position(fen), solution, sourceEvalScore(best));
    }

    /**
     * Extracts a sortable evaluation score from a source record output.
     *
     * @param output source UCI output
     * @return normalized score matching record-PGN sorting
     */
    private static int sourceEvalScore(Output output) {
        if (output == null) {
            return Integer.MIN_VALUE / 2;
        }
        Evaluation eval = output.getEvaluation();
        if (eval == null || !eval.isValid()) {
            return Integer.MIN_VALUE / 2;
        }
        int value = eval.getValue();
        if (eval.isMate()) {
            int sign = value >= 0 ? 1 : -1;
            int mate = Math.min(9_999, Math.abs(value));
            return sign * (REPORT_MATE_SORT_SCORE_BASE - mate);
        }
        return value;
    }

    /**
     * Verifies that a source node begins with the same key stored in the CSV row.
     *
     * @param start starting position
     * @param node source tree node
     * @param csvSolution CSV solution text
     * @return true when the source line is compatible with the CSV key
     */
    private static boolean sourceNodeStartsWithCsvKey(Position start, SourceSolutionNode node, String csvSolution) {
        short csvKey = firstSolutionMove(start, csvSolution);
        return csvKey == Move.NO_MOVE || Move.equals(csvKey, node.solution());
    }

    /**
     * Returns the first non-empty move in a move array.
     *
     * @param moves source moves
     * @return first move, or {@link Move#NO_MOVE}
     */
    private static short firstMove(short[] moves) {
        for (short move : moves) {
            if (move != Move.NO_MOVE) {
                return move;
            }
        }
        return Move.NO_MOVE;
    }

    /**
     * Finds the legal move that reaches a child source position.
     *
     * @param parentAfterSolution position after the parent solver move
     * @param childPosition child solver-position record
     * @return opponent reply move, or {@link Move#NO_MOVE}
     */
    private static short sourceReplyMove(Position parentAfterSolution, Position childPosition) {
        if (parentAfterSolution == null || childPosition == null) {
            return Move.NO_MOVE;
        }
        long target = childPosition.signatureCore();
        MoveList legal = parentAfterSolution.legalMoves();
        for (int i = 0; i < legal.size(); i++) {
            short move = legal.get(i);
            Position candidate = parentAfterSolution.copy();
            try {
                candidate.play(move);
            } catch (RuntimeException ex) {
                continue;
            }
            if (candidate.signatureCore() == target) {
                return move;
            }
        }
        return Move.NO_MOVE;
    }

    /**
     * Counts nodes in a source solution tree.
     *
     * @param node root node
     * @return node count
     */
    private static int sourceTreeSize(SourceSolutionNode node) {
        if (node == null) {
            return 0;
        }
        int count = 1;
        for (SourceSolutionBranch branch : sortedSourceBranches(node.branches())) {
            count += sourceTreeSize(branch.child());
        }
        return count;
    }

    /**
     * Formats an explicit solution tree with PGN-style variations.
     *
     * @param start starting position
     * @param root source solution root
     * @return move-numbered SAN variation text
     */
    private static String formatSourceTree(Position start, SourceSolutionNode root) {
        if (start == null || root == null || root.solution() == Move.NO_MOVE) {
            return "";
        }
        StringBuilder out = new StringBuilder(Math.max(128, sourceTreeSize(root) * 18));
        appendSourceNode(out, start.copy(), root, true);
        return out.toString().trim();
    }

    /**
     * Appends one solver move and its continuation tree.
     *
     * @param out destination movetext
     * @param start position before the solver move
     * @param node source-tree solver node
     * @param lineStart whether this move starts a line or variation
     */
    private static void appendSourceNode(StringBuilder out, Position start, SourceSolutionNode node,
            boolean lineStart) {
        if (start == null || node == null || node.solution() == Move.NO_MOVE) {
            return;
        }
        appendMove(out, start, node.solution(), lineStart);
        Position afterSolution = playOrNull(start, node.solution());
        if (afterSolution == null) {
            return;
        }
        List<SourceSolutionBranch> branches = sortedSourceBranches(node.branches());
        if (branches.isEmpty()) {
            return;
        }
        SourceSolutionBranch main = branches.get(0);
        appendMove(out, afterSolution, main.reply(), false);
        for (int i = 1; i < branches.size(); i++) {
            out.append(" (");
            appendSourceBranch(out, afterSolution.copy(), branches.get(i), true);
            out.append(')');
        }
        Position afterReply = playOrNull(afterSolution, main.reply());
        appendSourceNode(out, afterReply, main.child(), false);
    }

    /**
     * Appends one opponent reply and solver continuation as a branch line.
     *
     * @param out destination movetext
     * @param start position before the opponent reply
     * @param branch source solution branch
     * @param lineStart whether the reply starts a variation
     */
    private static void appendSourceBranch(StringBuilder out, Position start, SourceSolutionBranch branch,
            boolean lineStart) {
        if (start == null || branch == null || branch.reply() == Move.NO_MOVE
                || branch.child().solution() == Move.NO_MOVE) {
            return;
        }
        appendMove(out, start, branch.reply(), lineStart);
        Position afterReply = playOrNull(start, branch.reply());
        appendSourceNode(out, afterReply, branch.child(), false);
    }

    /**
     * Appends a move with explicit PGN-style move numbering.
     *
     * @param out destination movetext
     * @param position position before the move
     * @param move encoded move
     * @param lineStart whether the move starts a line or variation
     */
    private static void appendMove(StringBuilder out, Position position, short move, boolean lineStart) {
        if (position == null || move == Move.NO_MOVE) {
            return;
        }
        if (!out.isEmpty() && out.charAt(out.length() - 1) != '(') {
            out.append(' ');
        }
        if (position.isWhiteToMove()) {
            out.append(Math.max(1, position.fullMoveNumber())).append(". ");
        } else if (lineStart) {
            out.append(Math.max(1, position.fullMoveNumber())).append("... ");
        }
        try {
            out.append(SAN.toAlgebraic(position, move));
        } catch (RuntimeException ex) {
            out.append(Move.toString(move));
        }
    }

    /**
     * Applies one move and suppresses invalid-tree failures.
     *
     * @param position position before the move
     * @param move encoded move
     * @return resulting position, or {@code null} when the move cannot be played
     */
    private static Position playOrNull(Position position, short move) {
        if (position == null || move == Move.NO_MOVE) {
            return null;
        }
        try {
            return position.copy().play(move);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * Sorts branches using the same score-first, length-second policy as the
     * record-to-PGN exporter.
     *
     * @param branches source branches
     * @return sorted copy
     */
    private static List<SourceSolutionBranch> sortedSourceBranches(List<SourceSolutionBranch> branches) {
        if (branches == null || branches.size() <= 1) {
            return branches == null ? List.of() : branches;
        }
        List<SourceSolutionBranch> sorted = new ArrayList<>(branches);
        sorted.sort(PuzzleRatingCsvTool::compareSourceBranches);
        return sorted;
    }

    /**
     * Compares source branches by side-to-move quality, then continuation length.
     *
     * @param a first branch
     * @param b second branch
     * @return negative when {@code a} should sort first
     */
    private static int compareSourceBranches(SourceSolutionBranch a, SourceSolutionBranch b) {
        int scoreA = branchOptionScore(a);
        int scoreB = branchOptionScore(b);
        if (scoreA != scoreB) {
            return Integer.compare(scoreB, scoreA);
        }
        int lenA = branchOptionLength(a);
        int lenB = branchOptionLength(b);
        return Integer.compare(lenB, lenA);
    }

    /**
     * Returns the record-PGN-style option score for one opponent reply branch.
     *
     * @param branch source branch
     * @return adjusted score
     */
    private static int branchOptionScore(SourceSolutionBranch branch) {
        if (branch == null || branch.child() == null) {
            return Integer.MIN_VALUE / 2;
        }
        return -branch.child().evalScore();
    }

    /**
     * Returns the record-PGN-style option length for one branch.
     *
     * @param branch source branch
     * @return remaining line length
     */
    private static int branchOptionLength(SourceSolutionBranch branch) {
        return branch == null ? 0 : 1 + sourceLineLength(branch.child());
    }

    /**
     * Computes the longest remaining source-tree line from one solver node.
     *
     * @param node source node
     * @return line length in plies
     */
    private static int sourceLineLength(SourceSolutionNode node) {
        if (node == null) {
            return 0;
        }
        int best = node.solution() == Move.NO_MOVE ? 0 : 1;
        for (SourceSolutionBranch branch : node.branches()) {
            best = Math.max(best, 1 + sourceLineLength(branch.child()));
        }
        return best;
    }

    /**
     * Renders the puzzle board through the same native CRTK SVG renderer used by
     * the book pipeline.
     *
     * @param row source puzzle row
     * @return rendered board SVG
     */
    private static String renderReportBoardSvg(Row row) {
        Position position = new Position(row.fen());
        Render render = new Render()
                .setPosition(position)
                .setWhiteSideDown(true)
                .setShowBorder(true)
                .setShowCoordinates(true)
                .setShowSpecialMoveHints(false)
                .addCastlingRights(position)
                .addEnPassant(position);
        return chessWebBoardPalette(render.renderSvg(REPORT_BOARD_PIXELS, REPORT_BOARD_PIXELS));
    }

    /**
     * Recolors the native board SVG to match the chess-web-inspired board palette.
     *
     * @param svg source board SVG
     * @return recolored board SVG
     */
    private static String chessWebBoardPalette(String svg) {
        return removeBoardGridGaps(svg)
                .replace(REPORT_RENDER_FRAME_FILL, REPORT_WEB_BOARD_FRAME_FILL)
                .replace(REPORT_BOARD_GRID_FILL, REPORT_WEB_BOARD_FRAME_FILL)
                .replace(REPORT_BOARD_LIGHT_FILL, REPORT_WEB_BOARD_LIGHT_FILL)
                .replace(REPORT_BOARD_DARK_FILL, REPORT_WEB_BOARD_DARK_FILL)
                .replace(REPORT_BOARD_COORDINATE_FILL, REPORT_WEB_BOARD_COORDINATE_FILL);
    }

    /**
     * Expands generated board-square paths to full tiles so the separator
     * background never appears between fields.
     *
     * @param svg source board SVG
     * @return SVG with contiguous board fields
     */
    private static String removeBoardGridGaps(String svg) {
        Matcher matcher = REPORT_BOARD_SQUARE_PATH.matcher(svg);
        StringBuilder out = new StringBuilder(svg.length());
        while (matcher.find()) {
            String indent = matcher.group(1);
            String fill = matcher.group(2);
            int x0 = Integer.parseInt(matcher.group(3));
            int y0 = Integer.parseInt(matcher.group(4));
            int x1 = Integer.parseInt(matcher.group(5));
            int y1 = Integer.parseInt(matcher.group(8));
            int file = Math.max(0, Math.min(7, ((x0 + x1) / 2) / 200));
            int rank = Math.max(0, Math.min(7, ((y0 + y1) / 2) / 200));
            int fullX0 = file * 200;
            int fullY0 = rank * 200;
            int fullX1 = fullX0 + 200;
            int fullY1 = fullY0 + 200;
            String replacement = indent + "<path fill=\"" + fill + "\" stroke=\"none\" d=\"M"
                    + fullX0 + " " + fullY0
                    + " L" + fullX1 + " " + fullY0
                    + " L" + fullX1 + " " + fullY1
                    + " L" + fullX0 + " " + fullY1
                    + " Z\"/>";
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * Formats a row solution using the same move-numbered SAN generator used by
     * the puzzle collection builder.
     *
     * @param row source puzzle row
     * @return move-numbered SAN text, or the raw solution when parsing fails
     */
    private static String solutionAlgebraic(Row row) {
        String raw = row.solution();
        if (raw == null || raw.isBlank()) {
            return "-";
        }
        Position start = new Position(row.fen());
        String formatted = Builder.formatSanLine(start, solutionMoves(start, raw));
        return formatted.isBlank() ? raw : formatted;
    }

    /**
     * Parses a UCI/SAN move sequence into compact moves for the Chess Puzzle Collection
     * SAN-line formatter.
     *
     * @param start starting position
     * @param moves UCI or SAN move sequence
     * @return parsed compact moves
     */
    private static short[] solutionMoves(Position start, String moves) {
        if (start == null || moves == null || moves.isBlank()) {
            return new short[0];
        }
        List<Short> parsed = new ArrayList<>();
        Position cursor = start.copy();
        for (String token : solutionTokens(moves)) {
            try {
                short move = parseSolutionMove(cursor, token);
                parsed.add(move);
                cursor.play(move);
            } catch (RuntimeException ex) {
                break;
            }
        }
        short[] out = new short[parsed.size()];
        for (int i = 0; i < parsed.size(); i++) {
            out[i] = parsed.get(i);
        }
        return out;
    }

    /**
     * Returns the first parsed move in a solution line.
     *
     * @param start starting position
     * @param moves UCI or SAN move sequence
     * @return first encoded move, or {@link Move#NO_MOVE} when parsing fails
     */
    private static short firstSolutionMove(Position start, String moves) {
        if (start == null || moves == null || moves.isBlank()) {
            return Move.NO_MOVE;
        }
        for (String token : solutionTokens(moves)) {
            try {
                return parseSolutionMove(start, token);
            } catch (RuntimeException ex) {
                return Move.NO_MOVE;
            }
        }
        return Move.NO_MOVE;
    }

    /**
     * Splits UCI/SAN movetext into move tokens and drops PGN move numbers/results.
     *
     * @param moves raw UCI or SAN move text
     * @return move tokens
     */
    private static List<String> solutionTokens(String moves) {
        List<String> out = new ArrayList<>();
        if (moves == null || moves.isBlank()) {
            return out;
        }
        for (String raw : moves.replace(',', ' ').replace('\r', ' ').replace('\n', ' ').trim().split("\\s+")) {
            String token = stripMoveNumberPrefix(raw.trim());
            if (!token.isBlank() && !isResultToken(token)) {
                out.add(token);
            }
        }
        return out;
    }

    /**
     * Removes a leading PGN move-number prefix from a token.
     *
     * @param token source token
     * @return token without a leading {@code 23.} or {@code 23...} prefix
     */
    private static String stripMoveNumberPrefix(String token) {
        int i = 0;
        while (i < token.length() && Character.isDigit(token.charAt(i))) {
            i++;
        }
        int dots = 0;
        while (i + dots < token.length() && token.charAt(i + dots) == '.') {
            dots++;
        }
        if (i > 0 && dots > 0) {
            return token.substring(i + dots);
        }
        return token;
    }

    /**
     * Returns whether a token is a PGN result marker.
     *
     * @param token token to inspect
     * @return true for result markers
     */
    private static boolean isResultToken(String token) {
        return "1-0".equals(token) || "0-1".equals(token) || "1/2-1/2".equals(token) || "*".equals(token);
    }

    /**
     * Parses one solution token as UCI when possible, otherwise as SAN.
     *
     * @param position position before the move
     * @param token move token
     * @return encoded move
     */
    private static short parseSolutionMove(Position position, String token) {
        return Move.isMove(token) ? Move.parse(token) : SAN.fromAlgebraic(position, token);
    }

    /**
     * Draws one normalized metric strip for the difficulty-driver chart.
     */
    private static void drawDriverMetricRow(Canvas canvas, ComplexityBand[] bands, Color[] colors, String metricName,
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
     */
    private static void drawDriverBandLabels(Canvas canvas, ComplexityBand[] bands, double plotLeft, double y,
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
     */
    private static String complexitySummary(ComplexityBand[] bands) {
        ComplexityBand medium = bands[2];
        ComplexityBand hardest = bands[bands.length - 1];
        return "Very hard vs medium: legal moves "
                + oneDecimal(hardest.avgLegal()) + " vs " + oneDecimal(medium.avgLegal())
                + ", best-move rank " + oneDecimal(hardest.avgRank()) + " vs " + oneDecimal(medium.avgRank())
                + ", solution plies " + oneDecimal(hardest.avgPlies()) + " vs " + oneDecimal(medium.avgPlies())
                + ", branch-point rows " + pctLabel(hardest.branchShare()) + " vs " + pctLabel(medium.branchShare())
                + ".";
    }

    /**
     * Formats a metric value for the difficulty-driver chart.
     */
    private static String metricValueLabel(int metricIndex, double value) {
        if (metricIndex == 3) {
            return pctLabel(value);
        }
        return oneDecimal(value);
    }

    /**
     * Draws a titled table and returns its bottom edge.
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
     */
    private static String[][] difficultyRows(List<Row> rows) {
        String[] names = { "Very easy", "Easy", "Medium", "Hard", "Very hard" };
        int[] lo = { 600, 1040, 1370, 1810, 2140 };
        int[] hi = { 1039, 1369, 1809, 2139, 3000 };
        String[][] out = new String[names.length][5];
        for (int i = 0; i < names.length; i++) {
            List<Row> bucket = rowsInRange(rows, lo[i], hi[i]);
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
     * Builds solver-shape rows.
     */
    private static String[][] solverRows(List<Row> rows) {
        String[] names = { "Very easy", "Easy", "Medium", "Hard", "Very hard" };
        int[] lo = { 600, 1040, 1370, 1810, 2140 };
        int[] hi = { 1039, 1369, 1809, 2139, 3000 };
        String[][] out = new String[names.length][6];
        for (int i = 0; i < names.length; i++) {
            List<Row> bucket = rowsInRange(rows, lo[i], hi[i]);
            out[i] = new String[] {
                    names[i],
                    avg(bucket, Row::legalMoves),
                    avg(bucket, Row::cheapRank),
                    avg(bucket, Row::plies),
                    avg(bucket, Row::nodes),
                    pct(bucket.stream().filter(row -> "draw".equals(row.goal())).count(), bucket.size())
            };
        }
        return out;
    }

    /**
     * Builds top feature rows.
     */
    private static String[][] featureRows(List<Row> rows) {
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
     */
    private static String[][] structureRows(List<Row> rows, ReportStats stats) {
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
    private static String[][] hardestRows(List<Row> rows, int limit) {
        List<Row> top = topRows(rows, hardEvidenceComparator(), limit);
        String[][] out = new String[top.size()][10];
        for (int i = 0; i < top.size(); i++) {
            Row row = top.get(i);
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
    private static String[][] calculationLoadRows(List<Row> rows, int limit) {
        Comparator<Row> comparator = Comparator
                .comparingInt(Row::plies)
                .thenComparingInt(Row::nodes)
                .thenComparingInt(Row::branches)
                .thenComparingInt(Row::replies)
                .thenComparingInt(Row::rating)
                .thenComparingInt(Row::cheapRank)
                .thenComparingInt(Row::index);
        List<Row> top = topRows(rows, comparator, limit);
        String[][] out = new String[top.size()][6];
        for (int i = 0; i < top.size(); i++) {
            Row row = top.get(i);
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
    private static String[][] rareSignalRows(List<Row> rows) {
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
    private static String[][] hardTailSignalRows(List<Row> rows, int limit) {
        List<Row> veryHard = rowsInRange(rows, 2140, MAX_RATING);
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
    private static String[][] appendixBandRows(List<Row> rows) {
        String[] names = { "Very easy", "Easy", "Medium", "Hard", "Very hard" };
        int[] lo = { 600, 1040, 1370, 1810, 2140 };
        int[] hi = { 1039, 1369, 1809, 2139, 3000 };
        String[][] out = new String[names.length][6];
        for (int i = 0; i < names.length; i++) {
            List<Row> bucket = rowsInRange(rows, lo[i], hi[i]);
            out[i] = new String[] {
                    names[i],
                    num(bucket.size()),
                    avg(bucket, Row::cheapRank),
                    avg(bucket, Row::plies),
                    avg(bucket, Row::nodes),
                    pct(bucket.stream().filter(row -> row.branches() > 0).count(), bucket.size())
            };
        }
        return out;
    }

    /**
     * Returns the report creation timestamp.
     */
    private static String createdStamp() {
        return REPORT_TIMESTAMP.format(ZonedDateTime.now());
    }

    /**
     * Converts a rating to a PDF plot x-coordinate.
     */
    private static double ratingX(int rating, double left, double plotWidth) {
        double range = (double) MAX_RATING - MIN_RATING + 1.0;
        return left + plotWidth * (rating - MIN_RATING) / range;
    }

    /**
     * Draws right-aligned Java2D text.
     */
    private static void drawRight(Graphics2D g, double right, double y, String text) {
        String safe = text == null ? "" : text;
        g.drawString(safe, (float) (right - g.getFontMetrics().stringWidth(safe)), (float) y);
    }

    /**
     * Draws centered Java2D text.
     */
    private static void drawCentered(Graphics2D g, double center, double y, String text) {
        String safe = text == null ? "" : text;
        g.drawString(safe, (float) (center - g.getFontMetrics().stringWidth(safe) / 2.0), (float) y);
    }

    /**
     * Draws right-aligned text.
     */
    private static void drawRight(Canvas canvas, double right, double y, Font font, double size, Color color,
            String text) {
        String safe = text == null ? "" : text;
        canvas.drawText(right - font.textWidth(safe, size), y, font, size, color, safe);
    }

    /**
     * Draws centered text.
     */
    private static void drawCentered(Canvas canvas, double center, double y, Font font, double size, Color color,
            String text) {
        String safe = text == null ? "" : text;
        canvas.drawText(center - font.textWidth(safe, size) / 2.0, y, font, size, color, safe);
    }

    /**
     * Builds report-wide stats.
     */
    private static ReportStats reportStats(List<Row> rows) {
        double mean = mean(rows);
        double m2 = 0.0;
        double m3 = 0.0;
        double m4 = 0.0;
        int win = 0;
        int draw = 0;
        double rawMin = Double.POSITIVE_INFINITY;
        double rawMax = Double.NEGATIVE_INFINITY;
        int explicitNodes = 0;
        int branchRows = 0;
        int maxNodes = 0;
        int maxPlies = 0;
        int maxReplies = 0;
        int maxBranches = 0;
        long nodes = 0;
        long replies = 0;
        long branches = 0;
        for (Row row : rows) {
            double d = row.rating() - mean;
            m2 += d * d;
            m3 += d * d * d;
            m4 += d * d * d * d;
            win += "win".equals(row.goal()) ? 1 : 0;
            draw += "draw".equals(row.goal()) ? 1 : 0;
            rawMin = Math.min(rawMin, row.rawScore());
            rawMax = Math.max(rawMax, row.rawScore());
            explicitNodes += row.nodes() > 1 ? 1 : 0;
            branchRows += row.branches() > 0 ? 1 : 0;
            maxNodes = Math.max(maxNodes, row.nodes());
            maxPlies = Math.max(maxPlies, row.plies());
            maxReplies = Math.max(maxReplies, row.replies());
            maxBranches = Math.max(maxBranches, row.branches());
            nodes += row.nodes();
            replies += row.replies();
            branches += row.branches();
        }
        int n = Math.max(1, rows.size());
        double sd = Math.sqrt(m2 / n);
        double skew = sd <= 0.0 ? 0.0 : (m3 / n) / (sd * sd * sd);
        double kurt = sd <= 0.0 ? 0.0 : (m4 / n) / (sd * sd * sd * sd) - 3.0;
        return new ReportStats(mean, sd, skew, kurt, win, draw, rawMin, rawMax,
                explicitNodes, branchRows, nodes / (double) n, replies / (double) n, branches / (double) n,
                maxNodes, maxPlies, maxReplies, maxBranches);
    }

    /**
     * Returns the largest adjacent-count jump between two neighboring display bins.
     */
    private static int maxAdjacentBinDifference(List<Row> rows) {
        int[] bins = histogram(rows, DISPLAY_BIN_WIDTH);
        int max = 0;
        for (int i = 1; i < bins.length; i++) {
            max = Math.max(max, Math.abs(bins[i] - bins[i - 1]));
        }
        return max;
    }

    /**
     * Returns arithmetic mean.
     */
    private static double mean(List<Row> rows) {
        if (rows.isEmpty()) {
            return 0.0;
        }
        long sum = 0L;
        for (Row row : rows) {
            sum += row.rating();
        }
        return sum / (double) rows.size();
    }

    /**
     * Returns population skewness.
     */
    private static double skewness(List<Row> rows) {
        if (rows.isEmpty()) {
            return 0.0;
        }
        double mean = mean(rows);
        double m2 = 0.0;
        double m3 = 0.0;
        for (Row row : rows) {
            double d = row.rating() - mean;
            m2 += d * d;
            m3 += d * d * d;
        }
        m2 /= rows.size();
        m3 /= rows.size();
        double sd = Math.sqrt(m2);
        return sd <= 0.0 ? 0.0 : m3 / (sd * sd * sd);
    }

    /**
     * Parses one CSV line.
     */
    private static List<String> parseCsv(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(ch);
                }
            } else if (ch == ',') {
                fields.add(field.toString());
                field.setLength(0);
            } else if (ch == '"') {
                quoted = true;
            } else {
                field.append(ch);
            }
        }
        fields.add(field.toString());
        return fields;
    }

    /**
     * Converts fields to CSV.
     */
    private static String toCsvLine(List<String> fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(csv(fields.get(i)));
        }
        return sb.toString();
    }

    /**
     * Escapes one CSV field.
     */
    private static String csv(String value) {
        String v = value == null ? "" : value;
        if (!v.contains(",") && !v.contains("\"") && !v.contains("\n") && !v.contains("\r")) {
            return v;
        }
        return '"' + v.replace("\"", "\"\"") + '"';
    }

    /**
     * Formats compact graph coordinates.
     */
    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /**
     * Formats a percentage.
     */
    private static String percent(double value) {
        if (Math.abs(value) < 0.000_001) {
            return "0%";
        }
        return String.format(Locale.ROOT, "%.2f%%", value);
    }

    /**
     * Formats a count percentage.
     */
    private static String pct(long count, long total) {
        return total <= 0 ? "0.0%" : String.format(Locale.ROOT, "%.1f%%", 100.0 * count / total);
    }

    /**
     * Formats rare-event percentages without rounding nonzero values to 0.0%.
     *
     * @param count count
     * @param total total rows
     * @return percentage label
     */
    private static String rarePct(long count, long total) {
        if (total <= 0) {
            return "0.0%";
        }
        double value = 100.0 * count / total;
        if (value > 0.0 && value < 0.1) {
            return String.format(Locale.ROOT, "%.2f%%", value);
        }
        return String.format(Locale.ROOT, "%.1f%%", value);
    }

    /**
     * Formats a percentage value that is already on a 0-100 scale.
     */
    private static String pctLabel(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value);
    }

    /**
     * Formats a one-decimal number.
     */
    private static String oneDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    /**
     * Formats an integer with grouping.
     */
    private static String num(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    /**
     * Rows inside a rating range.
     */
    private static List<Row> rowsInRange(List<Row> rows, int lo, int hi) {
        return rows.stream().filter(row -> row.rating() >= lo && row.rating() <= hi).toList();
    }

    /**
     * Counts rows at or below a rating.
     */
    private static int rowsAtOrBelow(List<Row> rows, int rating) {
        int count = 0;
        for (Row row : rows) {
            if (row.rating() <= rating) {
                count++;
            }
        }
        return count;
    }

    /**
     * Counts rows with a feature.
     */
    private static int countFeature(List<Row> rows, String feature) {
        int count = 0;
        for (Row row : rows) {
            count += row.hasFeature(feature) ? 1 : 0;
        }
        return count;
    }

    /**
     * Counts all feature tags in a row set.
     *
     * @param rows source rows
     * @return feature counts
     */
    private static Map<String, Integer> featureCounts(List<Row> rows) {
        Map<String, Integer> counts = new HashMap<>();
        for (Row row : rows) {
            for (String feature : row.featureSet().keySet()) {
                counts.merge(feature, 1, Integer::sum);
            }
        }
        return counts;
    }

    /**
     * Highest rating for a feature.
     */
    private static int highestFeatureRating(List<Row> rows, String feature) {
        return rows.stream()
                .filter(row -> row.hasFeature(feature))
                .mapToInt(Row::rating)
                .max()
                .orElse(0);
    }

    /**
     * Returns a display label for a feature tag.
     *
     * @param feature feature tag
     * @return human-readable label
     */
    private static String featureLabel(String feature) {
        return feature == null ? "" : feature.replace('_', ' ');
    }

    /**
     * Returns rows sorted descending by a comparator.
     *
     * @param rows source rows
     * @param comparator ascending comparator
     * @param limit maximum number of rows
     * @return sorted rows
     */
    private static List<Row> topRows(List<Row> rows, Comparator<Row> comparator, int limit) {
        return rows.stream()
                .sorted(comparator.reversed())
                .limit(Math.max(0, limit))
                .toList();
    }

    /**
     * Comparator for final-rating hard-tail evidence.
     *
     * @return ascending comparator suitable for {@link #topRows(List, Comparator, int)}
     */
    private static Comparator<Row> hardEvidenceComparator() {
        return Comparator
                .comparingInt(Row::rating)
                .thenComparingDouble(Row::rawScore)
                .thenComparingInt(Row::plies)
                .thenComparingInt(Row::nodes)
                .thenComparingInt(Row::branches)
                .thenComparingInt(Row::replies)
                .thenComparingInt(Row::legalMoves)
                .thenComparingInt(Row::cheapRank)
                .thenComparingInt(Row::index);
    }

    /**
     * Returns the row used for the report puzzle snapshot.
     *
     * <p>
     * The snapshot should show the scorer's hardest puzzle example. Direct rating
     * is primary; raw score and explicit-tree evidence are used only to break ties
     * inside the top rating bucket.
     * </p>
     *
     * <p>
     * <strong>Warning:</strong> this is a presentation selector, not part of the
     * rating calculation. It does not change exported puzzle ratings.
     * </p>
     *
     * @param rows report input rows
     * @return highest-rated row by scorer ordering
     */
    private static Row puzzleSnapshotRow(List<Row> rows) {
        Comparator<Row> comparator = Comparator
                .comparingInt(Row::rating)
                .thenComparingDouble(Row::rawScore)
                .thenComparingInt(Row::plies)
                .thenComparingInt(Row::nodes)
                .thenComparingInt(Row::branches)
                .thenComparingInt(Row::replies)
                .thenComparingInt(Row::legalMoves)
                .thenComparingInt(Row::cheapRank)
                .thenComparingInt(Row::index);
        return rows.stream()
                .max(comparator)
                .orElse(null);
    }

    /**
     * Returns the side-to-move token from a FEN string.
     *
     * @param fen source FEN
     * @return {@code "w"} for White or {@code "b"} for Black
     */
    private static String sideToMove(String fen) {
        if (fen == null || fen.isBlank()) {
            return "w";
        }
        String[] parts = fen.trim().split("\\s+");
        return parts.length > 1 && "b".equals(parts[1]) ? "b" : "w";
    }

    /**
     * Returns a human-readable side-to-move label.
     *
     * @param fen source FEN
     * @return side-to-move label
     */
    private static String sideToMoveLabel(String fen) {
        return "b".equals(sideToMove(fen)) ? "Black" : "White";
    }

    /**
     * Builds a short, printable feature list for the puzzle snapshot panel.
     *
     * @param row source row
     * @return compact feature summary
     */
    private static String compactFeatureSummary(Row row) {
        String[] parts = row.features().split("[,|]");
        StringBuilder out = new StringBuilder();
        int used = 0;
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (used > 0) {
                out.append(", ");
            }
            out.append(trimmed.replace('_', ' '));
            used++;
            if (used == 3) {
                break;
            }
        }
        if (used == 0) {
            return "none";
        }
        if (parts.length > used) {
            out.append(", ...");
        }
        return out.toString();
    }

    /**
     * Formats the first solution move as SAN.
     *
     * @param row source row
     * @return SAN key move or raw solution fallback
     */
    private static String keySan(Row row) {
        if (row == null) {
            return "-";
        }
        try {
            Position start = new Position(row.fen());
            short key = firstSolutionMove(start, row.solution());
            if (key == Move.NO_MOVE) {
                return row.solution();
            }
            return SAN.toAlgebraic(start, key);
        } catch (RuntimeException ex) {
            return row.solution();
        }
    }

    /**
     * Returns the most populated difficulty band.
     */
    private static DifficultyBand dominantDifficultyBand(List<Row> rows) {
        String[] names = { "Very easy", "Easy", "Medium", "Hard", "Very hard" };
        int[] lo = { 600, 1040, 1370, 1810, 2140 };
        int[] hi = { 1039, 1369, 1809, 2139, 3000 };
        int bestIndex = 0;
        int bestCount = -1;
        for (int i = 0; i < names.length; i++) {
            int count = rowsInRange(rows, lo[i], hi[i]).size();
            if (count > bestCount) {
                bestIndex = i;
                bestCount = count;
            }
        }
        return new DifficultyBand(names[bestIndex], bestCount);
    }

    /**
     * Builds per-band aggregates for the difficulty-driver chart.
     */
    private static ComplexityBand[] complexityBands(List<Row> rows) {
        String[] names = { "Very easy", "Easy", "Medium", "Hard", "Very hard" };
        String[] shortNames = { "V.easy", "Easy", "Medium", "Hard", "V.hard" };
        int[] lo = { 600, 1040, 1370, 1810, 2140 };
        int[] hi = { 1039, 1369, 1809, 2139, 3000 };
        ComplexityBand[] out = new ComplexityBand[names.length];
        for (int i = 0; i < names.length; i++) {
            List<Row> bucket = rowsInRange(rows, lo[i], hi[i]);
            out[i] = new ComplexityBand(
                    names[i],
                    shortNames[i],
                    bucket.size(),
                    avgValue(bucket, Row::legalMoves),
                    avgValue(bucket, Row::cheapRank),
                    avgValue(bucket, Row::plies),
                    avgValue(bucket, Row::nodes),
                    shareWithBranches(bucket));
        }
        return out;
    }

    /**
     * Extracts one metric from all difficulty-band aggregates.
     */
    private static double[] metricValues(ComplexityBand[] bands, DoubleMetric metric) {
        double[] values = new double[bands.length];
        for (int i = 0; i < bands.length; i++) {
            values[i] = metric.value(bands[i]);
        }
        return values;
    }

    /**
     * Returns the total row count represented by difficulty bands.
     */
    private static int totalBandCount(ComplexityBand[] bands) {
        int total = 0;
        for (ComplexityBand band : bands) {
            total += band.count();
        }
        return total;
    }

    /**
     * Returns the maximum value in an array.
     */
    private static double max(double[] values) {
        double max = 0.0;
        for (double value : values) {
            max = Math.max(max, value);
        }
        return max;
    }

    /**
     * Averages a row integer field as a numeric value.
     */
    private static double avgValue(List<Row> rows, IntField field) {
        if (rows.isEmpty()) {
            return 0.0;
        }
        long sum = 0L;
        for (Row row : rows) {
            sum += field.value(row);
        }
        return sum / (double) rows.size();
    }

    /**
     * Returns the percentage of rows that have branch points.
     */
    private static double shareWithBranches(List<Row> rows) {
        if (rows.isEmpty()) {
            return 0.0;
        }
        long count = rows.stream().filter(row -> row.branches() > 0).count();
        return 100.0 * count / rows.size();
    }

    /**
     * Averages a row integer field.
     */
    private static String avg(List<Row> rows, IntField field) {
        if (rows.isEmpty()) {
            return "0.0";
        }
        long sum = 0L;
        for (Row row : rows) {
            sum += field.value(row);
        }
        return String.format(Locale.ROOT, "%.1f", sum / (double) rows.size());
    }

    /**
     * CSV row.
     */
    private static final class Row {

        /**
         * Original index.
         */
        private final int index;

        /**
         * Mutable CSV fields.
         */
        private final List<String> fields;

        /**
         * Raw difficulty score.
         */
        private final double rawScore;

        /**
         * FEN tie-break key.
         */
        private final String fen;

        /**
         * Creates a row.
         */
        Row(int index, List<String> fields, double rawScore, String fen) {
            this.index = index;
            this.fields = new ArrayList<>(fields);
            this.rawScore = rawScore;
            this.fen = fen;
        }

        /**
         * Original index.
         */
        int index() {
            return index;
        }

        /**
         * CSV fields.
         */
        List<String> fields() {
            return fields;
        }

        /**
         * Raw score.
         */
        double rawScore() {
            return rawScore;
        }

        /**
         * FEN key.
         */
        String fen() {
            return fen;
        }

        /**
         * Current rating.
         */
        int rating() {
            return Integer.parseInt(fields.get(2));
        }

        /**
         * Goal label.
         */
        String goal() {
            return fields.get(1);
        }

        /**
         * Difficulty label.
         */
        String label() {
            return fields.get(5);
        }

        /**
         * Principal solution move or line.
         */
        String solution() {
            return fields.get(6);
        }

        /**
         * Cheap rank.
         */
        int cheapRank() {
            return Integer.parseInt(fields.get(8));
        }

        /**
         * Legal move count.
         */
        int legalMoves() {
            return Integer.parseInt(fields.get(15));
        }

        /**
         * Explicit plies.
         */
        int plies() {
            return Integer.parseInt(fields.get(16));
        }

        /**
         * Root replies.
         */
        int replies() {
            return Integer.parseInt(fields.get(17));
        }

        /**
         * Tree nodes.
         */
        int nodes() {
            return Integer.parseInt(fields.get(18));
        }

        /**
         * Branch point count.
         */
        int branches() {
            return Integer.parseInt(fields.get(19));
        }

        /**
         * Raw feature list.
         */
        String features() {
            return fields.get(20);
        }

        /**
         * Whether this row contains a feature.
         */
        boolean hasFeature(String feature) {
            return featureSet().containsKey(feature);
        }

        /**
         * Feature set.
         */
        private Map<String, Boolean> featureSet() {
            Map<String, Boolean> out = new HashMap<>();
            for (String feature : fields.get(20).split("[,|]")) {
                String trimmed = feature.trim();
                if (!trimmed.isEmpty()) {
                    out.put(trimmed, Boolean.TRUE);
                }
            }
            return out;
        }

    }

    /**
     * Parsed arguments.
     */
    private record Arguments(Path input, String prefix, Path report, List<Path> records) {
    }

    /**
     * Render-ready puzzle snapshot move text.
     *
     * @param movetext move-numbered SAN text
     * @param sourceTree whether the text came from an original source tree
     */
    private record ReportPuzzleDetail(String movetext, boolean sourceTree) {
    }

    /**
     * One solver-to-move node in an explicit puzzle solution tree.
     */
    private static final class SourceSolutionNode {

        /**
         * Solver-to-move position.
         */
        private final Position position;

        /**
         * Solver solution move.
         */
        private final short solution;

        /**
         * Sortable evaluation score from the record's primary output.
         */
        private final int evalScore;

        /**
         * Opponent-reply branches after this solution move.
         */
        private final List<SourceSolutionBranch> branches = new ArrayList<>();

        /**
         * Creates a source solution node.
         *
         * @param position solver-to-move position
         * @param solution solver solution move
         * @param evalScore sortable evaluation score
         */
        SourceSolutionNode(Position position, short solution, int evalScore) {
            this.position = position;
            this.solution = solution;
            this.evalScore = evalScore;
        }

        /**
         * Returns the solver-to-move position.
         *
         * @return solver-to-move position
         */
        Position position() {
            return position;
        }

        /**
         * Returns the solver solution move.
         *
         * @return solver solution move
         */
        short solution() {
            return solution;
        }

        /**
         * Returns the sortable evaluation score.
         *
         * @return sortable evaluation score
         */
        int evalScore() {
            return evalScore;
        }

        /**
         * Returns mutable opponent-reply branches.
         *
         * @return opponent-reply branches
         */
        List<SourceSolutionBranch> branches() {
            return branches;
        }

        /**
         * Returns the position after the solver solution.
         *
         * @return position after the solution, or {@code null} when invalid
         */
        Position afterSolution() {
            if (position == null || solution == Move.NO_MOVE) {
                return null;
            }
            try {
                return position.copy().play(solution);
            } catch (RuntimeException ex) {
                return null;
            }
        }

        /**
         * Checks whether this node already contains a reply branch.
         *
         * @param reply opponent reply move
         * @return true when the reply is already present
         */
        boolean hasReply(short reply) {
            for (SourceSolutionBranch branch : branches) {
                if (Move.equals(branch.reply(), reply)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * One opponent reply and solver response branch.
     *
     * @param reply opponent reply after the parent solution move
     * @param child solver-to-move child node after the reply
     */
    private record SourceSolutionBranch(short reply, SourceSolutionNode child) {
    }

    /**
     * Internal control-flow signal used to stop record scanning once the root is found.
     */
    private static final class FoundSourceNode extends RuntimeException {

        /**
         * Serialization marker for runtime exception compatibility.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Found source solution node.
         */
        private final SourceSolutionNode node;

        /**
         * Creates a found-node sentinel.
         *
         * @param node found source node
         */
        FoundSourceNode(SourceSolutionNode node) {
            this.node = node;
        }
    }

    /**
     * Integer row field accessor.
     */
    private interface IntField {
        int value(Row row);
    }

    /**
     * Floating-point accessor for difficulty-driver metrics.
     */
    private interface DoubleMetric {
        double value(ComplexityBand band);
    }

    /**
     * Report-wide statistics.
     */
    private record ReportStats(
            double mean,
            double sd,
            double skewness,
            double kurtosis,
            int win,
            int draw,
            double rawMin,
            double rawMax,
            int explicitNodes,
            int branchRows,
            double avgNodes,
            double avgReplies,
            double avgBranches,
            int maxNodes,
            int maxPlies,
            int maxReplies,
            int maxBranches) {
    }

    /**
     * Difficulty-band aggregate used by dynamic report copy.
     */
    private record DifficultyBand(String name, int count) {
    }

    /**
     * Difficulty-band aggregate used by the driver chart.
     */
    private record ComplexityBand(
            String name,
            String shortName,
            int count,
            double avgLegal,
            double avgRank,
            double avgPlies,
            double avgNodes,
            double branchShare) {
    }
}
