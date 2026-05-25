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
    static final int BIN_WIDTH = 1;

    /**
     * Display bucket width for distribution charts.
     *
     * <p>
     * Ratings remain exact in CSV/report statistics, but the visual chart groups
     * neighboring ratings so a small sample does not read as a forest of 1-point
     * spikes.
     * </p>
     */
    static final int DISPLAY_BIN_WIDTH = 10;

    /**
     * Moving-average trend width for distribution charts, in rating points.
     */
    static final int DISTRIBUTION_TREND_POINTS = 180;

    /**
     * Lowest rating.
     */
    static final int MIN_RATING = 600;

    /**
     * Highest rating.
     */
    static final int MAX_RATING = 3000;

    /**
     * Report timestamp format.
     */
    static final DateTimeFormatter REPORT_TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm z", Locale.ROOT);

    /**
     * Native board-renderer pixel size, matching the book renderer.
     */
    static final int REPORT_BOARD_PIXELS = 900;

    /**
     * Default board fill emitted by the CRTK board SVG.
     */
    static final String REPORT_BOARD_GRID_FILL = "#b2b2b2";

    /**
     * Default light-square fill emitted by the CRTK board SVG.
     */
    static final String REPORT_BOARD_LIGHT_FILL = "#e5e5e5";

    /**
     * Default dark-square fill emitted by the CRTK board SVG.
     */
    static final String REPORT_BOARD_DARK_FILL = "#cccccc";

    /**
     * Default inline coordinate fill emitted by the CRTK board SVG renderer.
     */
    static final String REPORT_BOARD_COORDINATE_FILL = "#8c8c8c";

    /**
     * Default frame fill emitted by the CRTK board SVG renderer.
     */
    static final String REPORT_RENDER_FRAME_FILL = "#646464";

    /**
     * Softer board frame color used by report SVG previews.
     */
    static final String REPORT_WEB_BOARD_FRAME_FILL = "#8a6848";

    /**
     * Light-square color used by report SVG previews.
     */
    static final String REPORT_WEB_BOARD_LIGHT_FILL = "#efd6ab";

    /**
     * Dark-square color used by report SVG previews.
     */
    static final String REPORT_WEB_BOARD_DARK_FILL = "#bc8d62";

    /**
     * Muted coordinate detail color for the report board.
     */
    static final String REPORT_WEB_BOARD_COORDINATE_FILL = "#6f5238";

    /**
     * Matches the generated square path elements in the embedded board SVG.
     */
    static final Pattern REPORT_BOARD_SQUARE_PATH = Pattern.compile(
            "(?m)^(\\s*)<path fill=\"(" + Pattern.quote(REPORT_BOARD_LIGHT_FILL) + "|"
                    + Pattern.quote(REPORT_BOARD_DARK_FILL)
                    + ")\" stroke=\"none\" d=\"M(\\d+) (\\d+) L(\\d+) (\\d+) L(\\d+) (\\d+) L(\\d+) (\\d+) Z\"/>$");

    /**
     * Report left/right page margin in PDF points.
     */
    static final double REPORT_MARGIN = 28.0;

    /**
     * Report top page margin in PDF points.
     */
    static final double REPORT_TOP_MARGIN = 28.0;

    /**
     * Report bottom page margin in PDF points.
     */
    static final double REPORT_BOTTOM_MARGIN = 28.0;

    /**
     * Report colors.
     */
    static final Color REPORT_TEXT = new Color(17, 17, 17);
    /**
     * Color.
     * @param 74 74 value
     * @param 85 85 value
     * @param 98 98 value */
    static final Color REPORT_MUTED = new Color(74, 85, 98);
    /**
     * Color.
     * @param 111 111 value
     * @param 82 82 value
     * @param 56 56 value */
    static final Color REPORT_ACCENT = new Color(111, 82, 56);
    /**
     * Color.
     * @param 42 42 value
     * @param 32 32 value
     * @param 24 24 value */
    static final Color REPORT_RULE = new Color(42, 32, 24);
    /**
     * Color.
     * @param 216 216 value
     * @param 216 216 value
     * @param 216 216 value */
    static final Color REPORT_GRID = new Color(216, 216, 216);
    /**
     * Color.
     * @param 132 132 value
     * @param 185 185 value
     * @param 216 216 value */
    static final Color REPORT_BAR = new Color(132, 185, 216);
    /**
     * Color.
     * @param 92 92 value
     * @param 143 143 value
     * @param 174 174 value */
    static final Color REPORT_TREND = new Color(92, 143, 174);
    /**
     * Color.
     * @param 226 226 value
     * @param 211 211 value
     * @param 193 193 value */
    static final Color REPORT_SOFT_RULE = new Color(226, 211, 193);
    /**
     * Color.
     * @param 253 253 value
     * @param 249 249 value
     * @param 242 242 value */
    static final Color REPORT_CARD = new Color(253, 249, 242);
    /**
     * Color.
     * @param 254 254 value
     * @param 251 251 value
     * @param 247 247 value */
    static final Color REPORT_PANEL = new Color(254, 251, 247);
    /**
     * Color.
     * @param 247 247 value
     * @param 236 236 value
     * @param 220 220 value */
    static final Color REPORT_TABLE_HEADER = new Color(247, 236, 220);
    /**
     * Color.
     * @param 254 254 value
     * @param 251 251 value
     * @param 247 247 value */
    static final Color REPORT_TABLE_STRIPE = new Color(254, 251, 247);
    /**
     * Color.
     * @param 229 229 value
     * @param 214 214 value
     * @param 195 195 value */
    static final Color REPORT_TABLE_RULE = new Color(229, 214, 195);
    /**
     * Color.
     * @param 138 138 value
     * @param 104 104 value
     * @param 72 72 value */
    static final Color REPORT_BOARD_ACCENT = new Color(138, 104, 72);
    /**
     * Color.
     * @param 178 178 value
     * @param 178 178 value
     * @param 178 178 value */
    static final Color REPORT_QUANTILE_LINE = new Color(178, 178, 178);
    /**
     * R e p o r t  q u a n t i l e  l a b e l.
     */
    static final Color REPORT_QUANTILE_LABEL = REPORT_MUTED;

    /**
     * Report typography.
     *
     * <p>
     * Latin Modern matches the ChessRTK book renderer and gives the report a
     * printed chess-publication tone. Helvetica remains the compact data face for
     * dense labels, chart axes, and tables.
     * </p>
     */
    static final Font REPORT_DISPLAY_FONT = Font.LATIN_MODERN_BOLD;
    /**
     * R e p o r t  s e c t i o n  f o n t.
     */
    static final Font REPORT_SECTION_FONT = Font.LATIN_MODERN_BOLD;
    /**
     * R e p o r t  b o d y  f o n t.
     */
    static final Font REPORT_BODY_FONT = Font.LATIN_MODERN_ROMAN;
    /**
     * R e p o r t  b o d y  b o l d  f o n t.
     */
    static final Font REPORT_BODY_BOLD_FONT = Font.LATIN_MODERN_BOLD;
    /**
     * R e p o r t  d a t a  f o n t.
     */
    static final Font REPORT_DATA_FONT = Font.HELVETICA;
    /**
     * R e p o r t  d a t a  b o l d  f o n t.
     */
    static final Font REPORT_DATA_BOLD_FONT = Font.HELVETICA_BOLD;

    /**
     * Inline notation piece size relative to the surrounding text.
     */
    static final double REPORT_NOTATION_PIECE_SIZE_SCALE = 1.16;

    /**
     * Inline notation piece left padding relative to the surrounding text.
     */
    static final double REPORT_NOTATION_PIECE_LEFT_PADDING_SCALE = 0.04;

    /**
     * Inline notation piece right padding relative to the surrounding text.
     */
    static final double REPORT_NOTATION_PIECE_RIGHT_PADDING_SCALE = 0.19;

    /**
     * Inline notation piece top shift relative to the surrounding text.
     */
    static final double REPORT_NOTATION_PIECE_TOP_SHIFT_SCALE = 0.03;

    /**
     * Mate score base used to mirror record-PGN branch sorting.
     */
    static final int REPORT_MATE_SORT_SCORE_BASE = 100_000;

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
        PuzzleRatingArguments parsed = parseArguments(args);
        if (parsed == null) {
            System.err.println("Usage: java -cp out testing.PuzzleRatingCsvTool "
                    + "[--out-prefix out/prefix] [--report out/report.pdf] "
                    + "[--records records.jsonl] <scored-puzzles.csv>");
            System.exit(2);
        }
        List<PuzzleRatingRow> rows = readRows(parsed.input());
        rows.sort(Comparator.comparingInt(PuzzleRatingRow::index));
        Path csv = Path.of(parsed.prefix() + ".csv");
        Path png = Path.of(parsed.prefix() + ".png");
        writeCsv(csv, rows);
        PuzzleRatingGraph.writePng(png, rows);
        if (parsed.report() != null) {
            PuzzleRatingReport.writeReport(parsed.report(), rows, parsed.records());
        }
        printSummary(csv, png, rows);
    }

    /**
     * Parses command-line arguments.
     * @param args command arguments
     * @return parse arguments result
     */
    static PuzzleRatingArguments parseArguments(String[] args) {
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
        return new PuzzleRatingArguments(input, prefix, report, List.copyOf(records));
    }

    /**
     * Reads CSV rows.
     * @param input input value
     * @return read rows result
     * @throws java.io.IOException if IOException is raised by the underlying operation
     */
    static List<PuzzleRatingRow> readRows(Path input) throws IOException {
        List<PuzzleRatingRow> rows = new ArrayList<>();
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
                rows.add(new PuzzleRatingRow(Integer.parseInt(fields.get(0)), fields,
                        Double.parseDouble(fields.get(4)), fields.get(21)));
            }
        }
        return rows;
    }

    /**
     * Writes scored CSV rows.
     * @param output output text
     * @param rows data rows
     * @throws java.io.IOException if IOException is raised by the underlying operation
     */
    static void writeCsv(Path output, List<PuzzleRatingRow> rows) throws IOException {
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
            for (PuzzleRatingRow row : rows) {
                writer.write(toCsvLine(row.fields()));
                writer.newLine();
            }
        }
    }

    /**
     * Writes a native Java-rendered PNG histogram.
     * @param output output text
     * @param rows data rows
     * @throws java.io.IOException if IOException is raised by the underlying operation
     */
    static void printSummary(Path csv, Path png, List<PuzzleRatingRow> rows) {
        System.out.println("rows: " + rows.size());
        System.out.println("rating min/p10/p25/median/p75/p90/p99/max: "
                + PuzzleRatingGraph.percentile(rows, 0.00) + " / "
                + PuzzleRatingGraph.percentile(rows, 0.10) + " / "
                + PuzzleRatingGraph.percentile(rows, 0.25) + " / "
                + PuzzleRatingGraph.percentile(rows, 0.50) + " / "
                + PuzzleRatingGraph.percentile(rows, 0.75) + " / "
                + PuzzleRatingGraph.percentile(rows, 0.90) + " / "
                + PuzzleRatingGraph.percentile(rows, 0.99) + " / "
                + PuzzleRatingGraph.percentile(rows, 1.00));
        System.out.println("rating mean: " + Math.round(mean(rows)));
        System.out.println("rating skewness: " + String.format(Locale.ROOT, "%.3f", skewness(rows)));
        System.out.println("csv: " + csv);
        System.out.println("png: " + png);
    }

    /**
     * Returns the report creation timestamp.
     * @return created stamp result
     */
    static String createdStamp() {
        return REPORT_TIMESTAMP.format(ZonedDateTime.now());
    }

    /**
     * Converts a rating to a PDF plot x-coordinate.
     * @param rating rating value
     * @param left left coordinate
     * @param plotWidth plot width in pixels
     * @return rating x result
     */
    static double ratingX(int rating, double left, double plotWidth) {
        double range = (double) MAX_RATING - MIN_RATING + 1.0;
        return left + plotWidth * (rating - MIN_RATING) / range;
    }

    /**
     * Draws right-aligned Java2D text.
     * @param g graphics context
     * @param right right coordinate
     * @param y y coordinate
     * @param text text value
     */
    static void drawRight(Graphics2D g, double right, double y, String text) {
        String safe = text == null ? "" : text;
        g.drawString(safe, (float) (right - g.getFontMetrics().stringWidth(safe)), (float) y);
    }

    /**
     * Draws centered Java2D text.
     * @param g graphics context
     * @param center center value
     * @param y y coordinate
     * @param text text value
     */
    static void drawCentered(Graphics2D g, double center, double y, String text) {
        String safe = text == null ? "" : text;
        g.drawString(safe, (float) (center - g.getFontMetrics().stringWidth(safe) / 2.0), (float) y);
    }

    /**
     * Draws right-aligned text.
     * @param canvas SVG canvas builder
     * @param right right coordinate
     * @param y y coordinate
     * @param font font name
     * @param size size value
     * @param color display color
     * @param text text value
     */
    static void drawRight(Canvas canvas, double right, double y, Font font, double size, Color color,
            String text) {
        String safe = text == null ? "" : text;
        canvas.drawText(right - font.textWidth(safe, size), y, font, size, color, safe);
    }

    /**
     * Draws centered text.
     * @param canvas SVG canvas builder
     * @param center center value
     * @param y y coordinate
     * @param font font name
     * @param size size value
     * @param color display color
     * @param text text value
     */
    static void drawCentered(Canvas canvas, double center, double y, Font font, double size, Color color,
            String text) {
        String safe = text == null ? "" : text;
        canvas.drawText(center - font.textWidth(safe, size) / 2.0, y, font, size, color, safe);
    }

    /**
     * Builds report-wide stats.
     * @param rows data rows
     * @return report stats result
     */
    static PuzzleRatingStats reportStats(List<PuzzleRatingRow> rows) {
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
        for (PuzzleRatingRow row : rows) {
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
        return new PuzzleRatingStats(mean, sd, skew, kurt, win, draw, rawMin, rawMax,
                explicitNodes, branchRows, nodes / (double) n, replies / (double) n, branches / (double) n,
                maxNodes, maxPlies, maxReplies, maxBranches);
    }

    /**
     * Returns the largest adjacent-count jump between two neighboring display bins.
     * @param rows data rows
     * @return max adjacent bin difference result
     */
    static int maxAdjacentBinDifference(List<PuzzleRatingRow> rows) {
        int[] bins = PuzzleRatingGraph.histogram(rows, DISPLAY_BIN_WIDTH);
        int max = 0;
        for (int i = 1; i < bins.length; i++) {
            max = Math.max(max, Math.abs(bins[i] - bins[i - 1]));
        }
        return max;
    }

    /**
     * Returns arithmetic mean.
     * @param rows data rows
     * @return mean result
     */
    static double mean(List<PuzzleRatingRow> rows) {
        if (rows.isEmpty()) {
            return 0.0;
        }
        long sum = 0L;
        for (PuzzleRatingRow row : rows) {
            sum += row.rating();
        }
        return sum / (double) rows.size();
    }

    /**
     * Returns population skewness.
     * @param rows data rows
     * @return skewness result
     */
    static double skewness(List<PuzzleRatingRow> rows) {
        if (rows.isEmpty()) {
            return 0.0;
        }
        double mean = mean(rows);
        double m2 = 0.0;
        double m3 = 0.0;
        for (PuzzleRatingRow row : rows) {
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
     * @param line line text
     * @return parse csv result
     */
    static List<String> parseCsv(String line) {
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
     * @param fields record fields
     * @return to csv line result
     */
    static String toCsvLine(List<String> fields) {
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
     * @param value value to use
     * @return csv result
     */
    static String csv(String value) {
        String v = value == null ? "" : value;
        if (!v.contains(",") && !v.contains("\"") && !v.contains("\n") && !v.contains("\r")) {
            return v;
        }
        return '"' + v.replace("\"", "\"\"") + '"';
    }

    /**
     * Formats compact graph coordinates.
     * @param value value to use
     * @return fmt result
     */
    static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /**
     * Formats a percentage.
     * @param value value to use
     * @return percent result
     */
    static String percent(double value) {
        if (Math.abs(value) < 0.000_001) {
            return "0%";
        }
        return String.format(Locale.ROOT, "%.2f%%", value);
    }

    /**
     * Formats a count percentage.
     * @param count item count
     * @param total total value
     * @return pct result
     */
    static String pct(long count, long total) {
        return total <= 0 ? "0.0%" : String.format(Locale.ROOT, "%.1f%%", 100.0 * count / total);
    }

    /**
     * Formats rare-event percentages without rounding nonzero values to 0.0%.
     *
     * @param count count
     * @param total total rows
     * @return percentage label
     */
    static String rarePct(long count, long total) {
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
     * @param value value to use
     * @return pct label result
     */
    static String pctLabel(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value);
    }

    /**
     * Formats a one-decimal number.
     * @param value value to use
     * @return one decimal result
     */
    static String oneDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    /**
     * Formats an integer with grouping.
     * @param value value to use
     * @return num result
     */
    static String num(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    /**
     * Rows inside a rating range.
     * @param rows data rows
     * @param lo lo value
     * @param hi hi value
     * @return rows in range result
     */
    static List<PuzzleRatingRow> rowsInRange(List<PuzzleRatingRow> rows, int lo, int hi) {
        return rows.stream().filter(row -> row.rating() >= lo && row.rating() <= hi).toList();
    }

    /**
     * Counts rows at or below a rating.
     * @param rows data rows
     * @param rating rating value
     * @return rows at or below result
     */
    static int rowsAtOrBelow(List<PuzzleRatingRow> rows, int rating) {
        int count = 0;
        for (PuzzleRatingRow row : rows) {
            if (row.rating() <= rating) {
                count++;
            }
        }
        return count;
    }

    /**
     * Counts rows with a feature.
     * @param rows data rows
     * @param feature feature value
     * @return count feature result
     */
    static int countFeature(List<PuzzleRatingRow> rows, String feature) {
        int count = 0;
        for (PuzzleRatingRow row : rows) {
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
    static Map<String, Integer> featureCounts(List<PuzzleRatingRow> rows) {
        Map<String, Integer> counts = new HashMap<>();
        for (PuzzleRatingRow row : rows) {
            for (String feature : row.featureSet().keySet()) {
                counts.merge(feature, 1, Integer::sum);
            }
        }
        return counts;
    }

    /**
     * Highest rating for a feature.
     * @param rows data rows
     * @param feature feature value
     * @return highest feature rating result
     */
    static int highestFeatureRating(List<PuzzleRatingRow> rows, String feature) {
        return rows.stream()
                .filter(row -> row.hasFeature(feature))
                .mapToInt(PuzzleRatingRow::rating)
                .max()
                .orElse(0);
    }

    /**
     * Returns a display label for a feature tag.
     *
     * @param feature feature tag
     * @return human-readable label
     */
    static String featureLabel(String feature) {
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
    static List<PuzzleRatingRow> topRows(List<PuzzleRatingRow> rows, Comparator<PuzzleRatingRow> comparator, int limit) {
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
    static Comparator<PuzzleRatingRow> hardEvidenceComparator() {
        return Comparator
                .comparingInt(PuzzleRatingRow::rating)
                .thenComparingDouble(PuzzleRatingRow::rawScore)
                .thenComparingInt(PuzzleRatingRow::plies)
                .thenComparingInt(PuzzleRatingRow::nodes)
                .thenComparingInt(PuzzleRatingRow::branches)
                .thenComparingInt(PuzzleRatingRow::replies)
                .thenComparingInt(PuzzleRatingRow::legalMoves)
                .thenComparingInt(PuzzleRatingRow::cheapRank)
                .thenComparingInt(PuzzleRatingRow::index);
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
    static PuzzleRatingRow puzzleSnapshotRow(List<PuzzleRatingRow> rows) {
        Comparator<PuzzleRatingRow> comparator = Comparator
                .comparingInt(PuzzleRatingRow::rating)
                .thenComparingDouble(PuzzleRatingRow::rawScore)
                .thenComparingInt(PuzzleRatingRow::plies)
                .thenComparingInt(PuzzleRatingRow::nodes)
                .thenComparingInt(PuzzleRatingRow::branches)
                .thenComparingInt(PuzzleRatingRow::replies)
                .thenComparingInt(PuzzleRatingRow::legalMoves)
                .thenComparingInt(PuzzleRatingRow::cheapRank)
                .thenComparingInt(PuzzleRatingRow::index);
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
    static String sideToMove(String fen) {
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
    static String sideToMoveLabel(String fen) {
        return "b".equals(sideToMove(fen)) ? "Black" : "White";
    }

    /**
     * Builds a short, printable feature list for the puzzle snapshot panel.
     *
     * @param row source row
     * @return compact feature summary
     */
    static String compactFeatureSummary(PuzzleRatingRow row) {
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
    static String keySan(PuzzleRatingRow row) {
        if (row == null) {
            return "-";
        }
        try {
            Position start = new Position(row.fen());
            short key = PuzzleRatingSnapshot.firstSolutionMove(start, row.solution());
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
     * @param rows data rows
     * @return dominant difficulty band result
     */
    static PuzzleRatingDifficultyBand dominantDifficultyBand(List<PuzzleRatingRow> rows) {
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
        return new PuzzleRatingDifficultyBand(names[bestIndex], bestCount);
    }

    /**
     * Builds per-band aggregates for the difficulty-driver chart.
     * @param rows data rows
     * @return complexity bands result
     */
    static PuzzleRatingComplexityBand[] complexityBands(List<PuzzleRatingRow> rows) {
        String[] names = { "Very easy", "Easy", "Medium", "Hard", "Very hard" };
        String[] shortNames = { "V.easy", "Easy", "Medium", "Hard", "V.hard" };
        int[] lo = { 600, 1040, 1370, 1810, 2140 };
        int[] hi = { 1039, 1369, 1809, 2139, 3000 };
        PuzzleRatingComplexityBand[] out = new PuzzleRatingComplexityBand[names.length];
        for (int i = 0; i < names.length; i++) {
            List<PuzzleRatingRow> bucket = rowsInRange(rows, lo[i], hi[i]);
            out[i] = new PuzzleRatingComplexityBand(
                    names[i],
                    shortNames[i],
                    bucket.size(),
                    avgValue(bucket, PuzzleRatingRow::legalMoves),
                    avgValue(bucket, PuzzleRatingRow::cheapRank),
                    avgValue(bucket, PuzzleRatingRow::plies),
                    avgValue(bucket, PuzzleRatingRow::nodes),
                    shareWithBranches(bucket));
        }
        return out;
    }

    /**
     * Extracts one metric from all difficulty-band aggregates.
     * @param bands rating bands
     * @param metric metric value
     * @return metric values result
     */
    static double[] metricValues(PuzzleRatingComplexityBand[] bands, PuzzleRatingDoubleMetric metric) {
        double[] values = new double[bands.length];
        for (int i = 0; i < bands.length; i++) {
            values[i] = metric.value(bands[i]);
        }
        return values;
    }

    /**
     * Returns the total row count represented by difficulty bands.
     * @param bands rating bands
     * @return total band count result
     */
    static int totalBandCount(PuzzleRatingComplexityBand[] bands) {
        int total = 0;
        for (PuzzleRatingComplexityBand band : bands) {
            total += band.count();
        }
        return total;
    }

    /**
     * Returns the maximum value in an array.
     * @param values values to inspect
     * @return max result
     */
    static double max(double[] values) {
        double max = 0.0;
        for (double value : values) {
            max = Math.max(max, value);
        }
        return max;
    }

    /**
     * Averages a row integer field as a numeric value.
     * @param rows data rows
     * @param field field value
     * @return avg value result
     */
    static double avgValue(List<PuzzleRatingRow> rows, PuzzleRatingIntField field) {
        if (rows.isEmpty()) {
            return 0.0;
        }
        long sum = 0L;
        for (PuzzleRatingRow row : rows) {
            sum += field.value(row);
        }
        return sum / (double) rows.size();
    }

    /**
     * Returns the percentage of rows that have branch points.
     * @param rows data rows
     * @return share with branches result
     */
    static double shareWithBranches(List<PuzzleRatingRow> rows) {
        if (rows.isEmpty()) {
            return 0.0;
        }
        long count = rows.stream().filter(row -> row.branches() > 0).count();
        return 100.0 * count / rows.size();
    }

    /**
     * Averages a row integer field.
     * @param rows data rows
     * @param field field value
     * @return avg result
     */
    static String avg(List<PuzzleRatingRow> rows, PuzzleRatingIntField field) {
        if (rows.isEmpty()) {
            return "0.0";
        }
        long sum = 0L;
        for (PuzzleRatingRow row : rows) {
            sum += field.value(row);
        }
        return String.format(Locale.ROOT, "%.1f", sum / (double) rows.size());
    }

    /**
     * CSV row.
     */
}
