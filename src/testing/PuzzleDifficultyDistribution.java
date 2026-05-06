package testing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.StringJoiner;

import application.Config;
import application.cli.RecordIO;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Position;
import chess.puzzle.Difficulty;
import chess.puzzle.DifficultyFeatures;
import chess.puzzle.Scorer;
import chess.puzzle.Scorer.NodeScore;
import chess.puzzle.Scorer.PuzzleTreeSummary;
import chess.puzzle.Goal;
import chess.puzzle.PieceIdentityTracker;
import chess.struct.Record;
import utility.Json;

/**
 * Utility for scoring a record dump and writing puzzle rating distribution
 * artifacts.
 *
 * <p>
 * This is intentionally kept under {@code testing} so it can be run manually
 * without expanding the public CLI surface while the scoring curve is still
 * being tuned.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class PuzzleDifficultyDistribution {

    /**
     * Histogram bucket width in rating points.
     */
    private static final int BIN_WIDTH = 10;

    /**
     * Labeled x-axis tick spacing in rating points.
     */
    private static final int RATING_TICK_WIDTH = 100;

    /**
     * Width of the smoothed trend line in rating points.
     *
     * <p>
     * The chart groups ratings into display bins and overlays a broader trend so
     * the visual curve is not dominated by sample-level integer-rating noise.
     * </p>
     */
    private static final int TREND_POINTS = 180;

    /**
     * Lowest displayed rating.
     */
    private static final int MIN_RATING = 600;

    /**
     * Lowest upper bound used for displayed ratings.
     */
    private static final int MIN_DISPLAY_MAX_RATING = 3000;

    /**
     * Right-side rating padding when the sampled tail exceeds the default axis.
     */
    private static final int DISPLAY_MAX_PADDING = 50;

    /**
     * Maximum explicit solver-position depth to traverse per puzzle. The harmonic
     * continuation term is already almost flat beyond this point, and the cap
     * prevents accidental cycles from dominating a full USB run.
     */
    private static final int MAX_TREE_SOLVER_DEPTH = 24;

    /**
     * Safety cap for one root tree. Real puzzle trees should stay far below this.
     */
    private static final int MAX_TREE_NODES_PER_ROOT = 100_000;

    /**
     * Shared SVG font family.
     */
    private static final String SVG_FONT = "Arial,sans-serif";

    /**
     * Dark axis stroke color.
     */
    private static final String AXIS_STROKE = "#1f2933";

    /**
     * Muted SVG text color.
     */
    private static final String MUTED_TEXT_FILL = "#5f6b76";

    /**
     * Utility class; prevent instantiation.
     */
    private PuzzleDifficultyDistribution() {
        // utility
    }

    /**
     * Scores records and writes {@code .csv} and {@code .svg} artifacts.
     *
     * @param args input record files and optional output prefix
     * @throws IOException if reading or writing fails
     */
    public static void main(String[] args) throws IOException {
        Arguments parsed = parseArguments(args);
        if (parsed == null) {
            System.err.println("Usage: java -cp out testing.PuzzleDifficultyDistribution [--out out-prefix] "
                    + "[--max-puzzles N] <records.json>...");
            System.exit(2);
        }
        DistributionAccumulator acc = new DistributionAccumulator(parsed.maxPuzzles());
        List<Path> processedInputs = new ArrayList<>();
        for (Path input : parsed.inputs()) {
            processedInputs.add(input);
            try {
                RecordIO.streamRecordJson(input, acc::acceptJson);
            } catch (StopScanning done) {
                break;
            }
            if (processedInputs.size() % 25 == 0) {
                System.err.println("indexed inputs=" + processedInputs.size() + " puzzles=" + acc.roots.size());
            }
        }
        acc.finishScoring();
        acc.samples.sort(Comparator.comparingInt(Sample::rating));

        Path csv = withExtension(parsed.prefix(), ".csv");
        Path svg = withExtension(parsed.prefix(), ".svg");
        writeCsv(csv, acc.samples);
        writeSvg(svg, acc.samples);
        printSummary(processedInputs, csv, svg, acc);
    }

    /**
     * Parses command-line arguments.
     *
     * @param args raw args
     * @return parsed arguments, or null when invalid
     */
    private static Arguments parseArguments(String[] args) {
        if (args.length < 1) {
            return null;
        }
        RawArguments raw = parseFlaggedInvocation(args);
        if (raw == null || raw.inputs().isEmpty()) {
            return null;
        }
        Path prefix = raw.prefix() == null ? defaultPrefix(raw.inputs()) : raw.prefix();
        return new Arguments(List.copyOf(raw.inputs()), prefix, raw.maxPuzzles());
    }

    /**
     * Parses flag-based invocations.
     */
    private static RawArguments parseFlaggedInvocation(String[] args) {
        RawArgumentsBuilder builder = new RawArgumentsBuilder();
        Iterator<String> tokens = List.of(args).iterator();
        while (tokens.hasNext()) {
            if (!builder.accept(tokens.next(), tokens)) {
                return null;
            }
        }
        return builder.toRawArguments();
    }

    /**
     * Builds the default prefix for one or more input files.
     */
    private static Path defaultPrefix(List<Path> inputs) {
        return inputs.size() == 1 ? defaultPrefix(inputs.get(0)) : Path.of("puzzle-difficulty");
    }

    /**
     * Builds the default output prefix next to the input file.
     *
     * @param input input file
     * @return output prefix
     */
    private static Path defaultPrefix(Path input) {
        Path parent = input.getParent();
        String name = input.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        Path file = Path.of(stem + ".difficulty");
        return parent == null ? file : parent.resolve(file);
    }

    /**
     * Replaces the current suffix with an extension.
     *
     * @param prefix path prefix
     * @param ext extension including dot
     * @return output path
     */
    private static Path withExtension(Path prefix, String ext) {
        return Path.of(prefix.toString() + ext);
    }

    /**
     * Writes scored samples as CSV for further inspection.
     *
     * @param path output path
     * @param samples scored samples
     * @throws IOException if writing fails
     */
    private static void writeCsv(Path path, List<Sample> samples) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        StringBuilder sb = new StringBuilder(Math.max(1024, samples.size() * 180));
        sb.append("index,goal,rating,score,raw_score,label,solution,cheap_best,cheap_rank,deep_best_cp,")
                .append("deep_second_cp,deep_margin_cp,cheap_static_cp,cheap_best_cp,cheap_solution_cp,")
                .append("legal_moves,explicit_tree_plies,")
                .append("root_reply_count,tree_node_count,branch_point_count,features,fen\n");
        for (int i = 0; i < samples.size(); i++) {
            Sample sample = samples.get(i);
            DifficultyFeatures f = sample.difficulty.features();
            sb.append(i).append(',');
            sb.append(sample.difficulty.goal().label()).append(',');
            sb.append(sample.rating()).append(',');
            sb.append(String.format(Locale.ROOT, "%.4f", sample.difficulty.score())).append(',');
            sb.append(String.format(Locale.ROOT, "%.5f", f.rawScore())).append(',');
            sb.append(sample.difficulty.label()).append(',');
            sb.append(csv(f.solutionMoveUci())).append(',');
            sb.append(csv(f.cheapBestMoveUci())).append(',');
            sb.append(f.solutionRankByCheap()).append(',');
            sb.append(f.deepBestCp()).append(',');
            sb.append(f.deepSecondCp() == null ? "" : f.deepSecondCp()).append(',');
            sb.append(f.deepMarginCp() == null ? "" : f.deepMarginCp()).append(',');
            sb.append(f.cheapStaticCp()).append(',');
            sb.append(f.cheapBestCp()).append(',');
            sb.append(f.cheapSolutionCp()).append(',');
            sb.append(f.legalMoveCount()).append(',');
            sb.append(f.solutionPlies()).append(',');
            sb.append(f.variationCount()).append(',');
            sb.append(f.recordVariationCount()).append(',');
            sb.append(f.branchPointCount()).append(',');
            sb.append(csv(String.join(",", f.featureNames()))).append(',');
            sb.append(csv(sample.fen)).append('\n');
        }
        Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
    }

    /**
     * Escapes one CSV field.
     *
     * @param value raw value
     * @return CSV-safe value
     */
    private static String csv(String value) {
        String v = value == null ? "" : value;
        if (v.indexOf(',') < 0 && v.indexOf('"') < 0 && v.indexOf('\n') < 0 && v.indexOf('\r') < 0) {
            return v;
        }
        return '"' + v.replace("\"", "\"\"") + '"';
    }

    /**
     * Writes a compact histogram as SVG.
     *
     * @param path output path
     * @param samples scored samples
     * @throws IOException if writing fails
     */
    private static void writeSvg(Path path, List<Sample> samples) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        int displayMaxRating = displayMaxRating(samples);
        int[] bins = bins(samples, displayMaxRating);
        double[] percentages = percentages(bins, samples.size());
        double maxPercent = 0.0;
        for (double percent : percentages) {
            maxPercent = Math.max(maxPercent, percent);
        }
        double percentStep = percentTickStep(maxPercent);
        double axisMaxPercent = Math.max(percentStep, Math.ceil(maxPercent / percentStep) * percentStep);
        int width = 1600;
        int height = 760;
        int left = 70;
        int right = 42;
        int top = 86;
        int bottom = 82;
        int plotW = width - left - right;
        int plotH = height - top - bottom;
        double bw = plotW / (double) bins.length;

        StringBuilder sb = new StringBuilder(8192);
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(width).append("\" height=\"")
                .append(height).append("\" viewBox=\"0 0 ").append(width).append(' ').append(height).append("\">\n");
        sb.append("<rect width=\"100%\" height=\"100%\" fill=\"#fbfaf7\"/>\n");
        appendText(sb, left, 32, null, 22, "#17202a", "Puzzle rating distribution");
        appendText(sb, left, 56, null, 12, MUTED_TEXT_FILL,
                samples.size() + " scored puzzles; p10 " + percentile(samples, 0.10)
                        + ", median " + percentile(samples, 0.50)
                        + ", p90 " + percentile(samples, 0.90)
                        + "; " + BIN_WIDTH + "-point bins with " + TREND_POINTS
                        + "-point trend; axis max " + displayMaxRating);
        drawDifficultyBands(sb, samples, left, top, plotW, plotH, displayMaxRating);
        drawAxes(sb, left, top, plotW, plotH, axisMaxPercent, percentStep);
        for (int i = 0; i < percentages.length; i++) {
            double x = left + i * bw + 1.0;
            double h = plotH * percentages[i] / axisMaxPercent;
            double y = top + plotH - h;
            sb.append("<rect x=\"").append(fmt(x)).append("\" y=\"").append(fmt(y)).append("\" width=\"")
                    .append(fmt(Math.max(0.20, bw * 0.85))).append("\" height=\"").append(fmt(h))
                    .append("\" fill=\"#2f6f9f\" opacity=\"0.62\"/>\n");
        }
        drawTrendLine(sb, movingAverage(percentages, trendWindowBins()), left, top, plotW, plotH, axisMaxPercent);
        drawPercentileMarkers(sb, samples, left, top, plotW, plotH, displayMaxRating);
        drawRatingTicks(sb, left, top, plotW, plotH, displayMaxRating);
        drawLegend(sb, width - 305, 30);
        sb.append("</svg>\n");
        Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
    }

    /**
     * Draws subtle difficulty bands behind the distribution.
     */
    private static void drawDifficultyBands(StringBuilder sb, List<Sample> samples, int left, int top, int plotW,
            int plotH, int displayMaxRating) {
        int[] starts = { MIN_RATING, 1040, 1370, 1810, 2140 };
        int[] ends = { 1039, 1369, 1809, 2139, displayMaxRating };
        String[] fills = { "#edf7ef", "#f3f7ed", "#fff7e6", "#fff0e4", "#f9e8ec" };
        String[] labels = { "Very easy", "Easy", "Medium", "Hard", "Very hard" };
        for (int i = 0; i < starts.length; i++) {
            double x1 = xForRating(starts[i], left, plotW, displayMaxRating);
            double x2 = xForRating(ends[i] + 1, left, plotW, displayMaxRating);
            sb.append("<rect x=\"").append(fmt(x1)).append("\" y=\"").append(fmt(top))
                    .append("\" width=\"").append(fmt(Math.max(0.0, x2 - x1)))
                    .append("\" height=\"").append(fmt(plotH))
                    .append("\" fill=\"").append(fills[i]).append("\" opacity=\"0.38\"/>\n");
            appendText(sb, x1 + 8.0, top + 16.0, null, 11, "#69737d",
                    labels[i] + " " + String.format(Locale.ROOT, "%.1f%%",
                            percentInRatingRange(samples, starts[i], ends[i])));
        }
    }

    /**
     * Returns percentage of samples inside a rating interval.
     */
    private static double percentInRatingRange(List<Sample> samples, int start, int end) {
        if (samples.isEmpty()) {
            return 0.0;
        }
        int count = 0;
        for (Sample sample : samples) {
            int rating = sample.rating();
            if (rating >= start && rating <= end) {
                count++;
            }
        }
        return count * 100.0 / samples.size();
    }

    /**
     * Draws percentile guide lines.
     */
    private static void drawPercentileMarkers(StringBuilder sb, List<Sample> samples, int left, int top, int plotW,
            int plotH, int displayMaxRating) {
        double[] qs = { 0.10, 0.25, 0.50, 0.75, 0.90, 0.99 };
        String[] labels = { "p10", "p25", "median", "p75", "p90", "p99" };
        for (int i = 0; i < qs.length; i++) {
            int rating = percentile(samples, qs[i]);
            double x = xForRating(rating, left, plotW, displayMaxRating);
            appendLine(sb, x, top, x, top + (double) plotH, "#7b8794", "2 7");
            appendText(sb, x, top - 8.0 - (i % 2) * 14.0, "middle", 11, MUTED_TEXT_FILL,
                    labels[i] + " " + rating);
        }
    }

    /**
     * Draws a small legend.
     */
    private static void drawLegend(StringBuilder sb, double x, double y) {
        sb.append("<rect x=\"").append(fmt(x)).append("\" y=\"").append(fmt(y - 17.0))
                .append("\" width=\"275.00\" height=\"34.00\" fill=\"#fbfaf7\" opacity=\"0.88\"/>\n");
        sb.append("<rect x=\"").append(fmt(x + 8.0)).append("\" y=\"").append(fmt(y - 4.0))
                .append("\" width=\"24.00\" height=\"10.00\" fill=\"#2f6f9f\" opacity=\"0.62\"/>\n");
        appendText(sb, x + 38.0, y + 5.0, null, 11, MUTED_TEXT_FILL,
                BIN_WIDTH + "-point bins + " + TREND_POINTS + "-point trend");
    }

    /**
     * Draws the smoothed distribution trend line.
     *
     * @param sb SVG output buffer
     * @param smoothed centered moving-average percentages
     * @param left plot left edge
     * @param top plot top edge
     * @param plotW plot width
     * @param plotH plot height
     * @param maxPercent y-axis maximum percentage
     */
    private static void drawTrendLine(StringBuilder sb, double[] smoothed, int left, int top, int plotW, int plotH,
            double maxPercent) {
        if (smoothed.length == 0) {
            return;
        }
        double bw = plotW / (double) smoothed.length;
        sb.append("<path d=\"");
        for (int i = 0; i < smoothed.length; i++) {
            double x = left + (i + 0.5) * bw;
            double y = top + plotH - plotH * smoothed[i] / maxPercent;
            sb.append(i == 0 ? "M" : " L").append(fmt(x)).append(' ').append(fmt(y));
        }
        sb.append("\" fill=\"none\" stroke=\"#1f5f8f\" stroke-width=\"2.20\" stroke-linejoin=\"round\"")
                .append(" stroke-linecap=\"round\"/>\n");
    }

    /**
     * Draws the graph axes and y-grid.
     */
    private static void drawAxes(StringBuilder sb, int left, int top, int plotW, int plotH, double maxPercent,
            double percentStep) {
        double plotBottom = top + (double) plotH;
        double plotRight = left + (double) plotW;
        appendLine(sb, left, top, left, plotBottom, AXIS_STROKE, null);
        appendLine(sb, left, plotBottom, plotRight, plotBottom, AXIS_STROKE, null);
        for (double percent = 0.0; percent <= maxPercent + percentStep / 2.0; percent += percentStep) {
            double y = top + plotH - plotH * percent / maxPercent;
            appendLine(sb, left, y, plotRight, y, "#d7dde3", "3 5");
            appendText(sb, left - 10.0, y + 4.0, "end", 11, MUTED_TEXT_FILL, formatPercent(percent));
        }
    }

    /**
     * Draws x-axis rating ticks.
     */
    private static void drawRatingTicks(StringBuilder sb, int left, int top, int plotW, int plotH,
            int displayMaxRating) {
        double plotBottom = top + (double) plotH;
        for (int rating = MIN_RATING; rating <= displayMaxRating; rating += RATING_TICK_WIDTH) {
            double x = xForRating(rating, left, plotW, displayMaxRating);
            appendLine(sb, x, plotBottom, x, plotBottom + 6.0, AXIS_STROKE, null);
            appendText(sb, x, plotBottom + 24.0, "middle", 11, MUTED_TEXT_FILL, Integer.toString(rating));
        }
    }

    /**
     * Maps a rating to an x-coordinate in the plot area.
     */
    private static double xForRating(int rating, int left, int plotW, int displayMaxRating) {
        double ratingRange = (double) displayMaxRating - MIN_RATING + BIN_WIDTH;
        return left + plotW * (rating - MIN_RATING) / ratingRange;
    }

    /**
     * Appends one SVG line.
     */
    private static void appendLine(StringBuilder sb, double x1, double y1, double x2, double y2, String stroke,
            String dashArray) {
        sb.append("<line x1=\"").append(fmt(x1)).append("\" y1=\"").append(fmt(y1))
                .append("\" x2=\"").append(fmt(x2)).append("\" y2=\"").append(fmt(y2))
                .append("\" stroke=\"").append(stroke).append('"');
        if (dashArray != null) {
            sb.append(" stroke-dasharray=\"").append(dashArray).append('"');
        }
        sb.append("/>\n");
    }

    /**
     * Appends one SVG text element.
     */
    private static void appendText(StringBuilder sb, double x, double y, String anchor, int size, String fill,
            String text) {
        sb.append("<text x=\"").append(fmt(x)).append("\" y=\"").append(fmt(y)).append('"');
        if (anchor != null) {
            sb.append(" text-anchor=\"").append(anchor).append('"');
        }
        sb.append(" font-family=\"").append(SVG_FONT).append("\" font-size=\"").append(size)
                .append("\" fill=\"").append(fill).append("\">").append(text).append("</text>\n");
    }

    /**
     * Builds histogram bins.
     *
     * @param samples scored samples
     * @return bin counts
     */
    private static int[] bins(List<Sample> samples, int displayMaxRating) {
        int binCount = ((displayMaxRating - MIN_RATING) / BIN_WIDTH) + 1;
        int[] bins = new int[binCount];
        for (Sample sample : samples) {
            int rating = Math.max(MIN_RATING, Math.min(displayMaxRating, sample.rating()));
            int idx = Math.min(binCount - 1, Math.max(0, (rating - MIN_RATING) / BIN_WIDTH));
            bins[idx]++;
        }
        return bins;
    }

    /**
     * Converts bin counts to percentages of the scored sample.
     */
    private static double[] percentages(int[] bins, int sampleCount) {
        double[] percentages = new double[bins.length];
        if (sampleCount <= 0) {
            return percentages;
        }
        for (int i = 0; i < bins.length; i++) {
            percentages[i] = bins[i] * 100.0 / sampleCount;
        }
        return percentages;
    }

    /**
     * Smooths a percentage series with a centered moving average.
     *
     * @param values raw per-bin percentages
     * @param window smoothing window in bins
     * @return smoothed percentage series
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
     * Converts the configured trend width from Elo points to histogram bins.
     */
    private static int trendWindowBins() {
        int bins = Math.max(1, (int) Math.round(TREND_POINTS / (double) BIN_WIDTH));
        return bins % 2 == 0 ? bins + 1 : bins;
    }

    /**
     * Chooses a readable y-axis percentage step.
     */
    private static double percentTickStep(double maxPercent) {
        double target = Math.max(0.01, maxPercent / 5.0);
        if (target <= 0.05) {
            return 0.05;
        }
        if (target <= 0.10) {
            return 0.10;
        }
        if (target <= 0.20) {
            return 0.20;
        }
        if (target <= 0.25) {
            return 0.25;
        }
        if (target <= 0.50) {
            return 0.50;
        }
        if (target <= 1.00) {
            return 1.00;
        }
        return Math.ceil(target);
    }

    /**
     * Formats a percentage label without unnecessary trailing zeroes.
     */
    private static String formatPercent(double percent) {
        if (Math.abs(percent - Math.rint(percent)) < 0.000_001) {
            return String.format(Locale.ROOT, "%.0f%%", percent);
        }
        if (Math.abs(percent * 10.0 - Math.rint(percent * 10.0)) < 0.000_001) {
            return String.format(Locale.ROOT, "%.1f%%", percent);
        }
        return String.format(Locale.ROOT, "%.2f%%", percent);
    }

    /**
     * Chooses a display maximum that includes the observed tail without creating an
     * artificial right-edge pile-up.
     */
    private static int displayMaxRating(List<Sample> samples) {
        int maxRating = MIN_RATING;
        for (Sample sample : samples) {
            maxRating = Math.max(maxRating, sample.rating());
        }
        if (maxRating <= MIN_DISPLAY_MAX_RATING) {
            return MIN_DISPLAY_MAX_RATING;
        }
        return roundUp(maxRating + DISPLAY_MAX_PADDING, RATING_TICK_WIDTH);
    }

    /**
     * Rounds a positive integer up to a step.
     */
    private static int roundUp(int value, int step) {
        if (step <= 0) {
            return value;
        }
        return ((value + step - 1) / step) * step;
    }

    /**
     * Formats a decimal for SVG coordinates.
     */
    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /**
     * Prints distribution summary to stdout.
     */
    private static void printSummary(List<Path> inputs, Path csv, Path svg, DistributionAccumulator acc) {
        List<Sample> samples = acc.samples;
        System.out.println("inputs: " + inputs.size());
        for (Path input : inputs) {
            System.out.println("input: " + input);
        }
        System.out.println("scored: " + samples.size());
        System.out.println("non_puzzles: " + acc.nonPuzzles);
        System.out.println("skipped: " + acc.skipped);
        System.out.println("invalid: " + acc.invalid);
        System.out.println("indexed_puzzles: " + acc.roots.size());
        System.out.println("truncated_trees: " + acc.truncatedTrees);
        if (!samples.isEmpty()) {
            System.out.println("rating min/p10/p25/median/p75/p90/max: "
                    + percentile(samples, 0.00) + " / "
                    + percentile(samples, 0.10) + " / "
                    + percentile(samples, 0.25) + " / "
                    + percentile(samples, 0.50) + " / "
                    + percentile(samples, 0.75) + " / "
                    + percentile(samples, 0.90) + " / "
                    + percentile(samples, 1.00));
            System.out.println("rating mean: " + Math.round(samples.stream().mapToInt(Sample::rating).average().orElse(0.0)));
            System.out.println("goals: " + enumCounts(samples, Sample::goal));
            System.out.println("labels: " + labelCounts(samples));
            System.out.println("top features: " + featureCounts(samples, 12));
        }
        System.out.println("csv: " + csv);
        System.out.println("svg: " + svg);
    }

    /**
     * Parsed command-line arguments.
     *
     * @param inputs source record files
     * @param prefix output path prefix
     * @param maxPuzzles maximum number of verified puzzles to score, or 0 for no cap
     */
    private record Arguments(List<Path> inputs, Path prefix, int maxPuzzles) {
        // data carrier
    }

    /**
     * Parsed flag-based arguments before defaults are applied.
     *
     * @param inputs source record files
     * @param prefix output path prefix, or null for default
     * @param maxPuzzles maximum number of verified puzzles to score, or 0 for no cap
     */
    private record RawArguments(List<Path> inputs, Path prefix, int maxPuzzles) {
        // data carrier
    }

    /**
     * Mutable parser state for flag-based arguments.
     */
    private static final class RawArgumentsBuilder {

        /**
         * Source record files.
         */
        private final List<Path> inputs = new ArrayList<>();

        /**
         * Output prefix, or null for default.
         */
        private Path prefix;

        /**
         * Maximum verified puzzle count, or 0 for no cap.
         */
        private int maxPuzzles;

        /**
         * Accepts one command-line token.
         */
        boolean accept(String arg, Iterator<String> tokens) {
            if ("--out".equals(arg)) {
                return acceptOutputPrefix(tokens);
            }
            if ("--max-puzzles".equals(arg)) {
                return acceptMaxPuzzles(tokens);
            }
            inputs.add(Path.of(arg));
            return true;
        }

        /**
         * Builds immutable raw arguments.
         */
        RawArguments toRawArguments() {
            return new RawArguments(List.copyOf(inputs), prefix, maxPuzzles);
        }

        private boolean acceptOutputPrefix(Iterator<String> tokens) {
            if (prefix != null || !tokens.hasNext()) {
                return false;
            }
            prefix = Path.of(tokens.next());
            return true;
        }

        private boolean acceptMaxPuzzles(Iterator<String> tokens) {
            if (maxPuzzles != 0 || !tokens.hasNext()) {
                return false;
            }
            maxPuzzles = parseMaxPuzzles(tokens.next());
            return maxPuzzles != 0;
        }

        private static int parseMaxPuzzles(String value) {
            try {
                return Math.max(1, Integer.parseInt(value));
            } catch (NumberFormatException ex) {
                return 0;
            }
        }
    }

    /**
     * Returns nearest-rank percentile.
     */
    private static int percentile(List<Sample> samples, double p) {
        if (samples.isEmpty()) {
            return 0;
        }
        double maxIndex = samples.size() - 1.0;
        int idx = (int) Math.round(maxIndex * p);
        return samples.get(Math.max(0, Math.min(samples.size() - 1, idx))).rating();
    }

    /**
     * Counts enum values.
     */
    private static <E extends Enum<E>> String enumCounts(List<Sample> samples, java.util.function.Function<Sample, E> fn) {
        Map<E, Integer> counts = new EnumMap<>(fn.apply(samples.get(0)).getDeclaringClass());
        for (Sample sample : samples) {
            E key = fn.apply(sample);
            counts.put(key, counts.getOrDefault(key, 0) + 1);
        }
        return counts.toString();
    }

    /**
     * Counts labels.
     */
    private static String labelCounts(List<Sample> samples) {
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (Sample sample : samples) {
            counts.put(sample.difficulty.label(), counts.getOrDefault(sample.difficulty.label(), 0) + 1);
        }
        return counts.toString();
    }

    /**
     * Counts feature labels.
     */
    private static String featureCounts(List<Sample> samples, int limit) {
        Map<String, Integer> counts = new java.util.HashMap<>();
        for (Sample sample : samples) {
            for (String feature : sample.difficulty.features().featureNames()) {
                counts.put(feature, counts.getOrDefault(feature, 0) + 1);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .limit(limit)
                .collect(StringJoinerCollector.collecting(", ", e -> e.getKey() + "=" + e.getValue()));
    }

    /**
     * Record-stream accumulator.
     */
    private static final class DistributionAccumulator {

        /**
         * Scored samples.
         */
        private final List<Sample> samples = new ArrayList<>();

        /**
         * Verified puzzle roots selected for output.
         */
        private final List<PuzzleNode> roots = new ArrayList<>();

        /**
         * Root position signatures already selected for output.
         */
        private final Set<Long> rootSignatures = new HashSet<>();

        /**
         * Verified puzzle records keyed by the parent position they came from.
         */
        private final Map<Long, List<PuzzleNode>> childrenByParent = new HashMap<>();

        /**
         * Skipped records.
         */
        private int skipped;

        /**
         * Parsed records rejected by the puzzle verifier.
         */
        private int nonPuzzles;

        /**
         * Invalid records.
         */
        private int invalid;

        /**
         * Root trees stopped by the safety caps.
         */
        private int truncatedTrees;

        /**
         * Maximum number of verified puzzles to score, or 0 for no cap.
         */
        private final int maxPuzzles;

        /**
         * Creates an accumulator.
         *
         * @param maxPuzzles maximum number of verified puzzles to score, or 0
         */
        private DistributionAccumulator(int maxPuzzles) {
            this.maxPuzzles = Math.max(0, maxPuzzles);
        }

        /**
         * Accepts one parsed record.
         *
         * @param objJson raw record JSON object
         */
        void acceptJson(String objJson) {
            Record rec;
            try {
                rec = Record.fromJson(objJson);
            } catch (Exception ex) {
                invalid++;
                return;
            }
            if (rec == null) {
                invalid++;
                return;
            }
            if (rec.getPosition() == null || rec.getAnalysis() == null || rec.getAnalysis().isEmpty()
                    || rec.getAnalysis().getBestMove() == Move.NO_MOVE) {
                skipped++;
                return;
            }
            if (!isPuzzle(objJson, rec)) {
                nonPuzzles++;
                return;
            }
            PuzzleNode node = puzzleNode(rec);
            if (node == null) {
                skipped++;
                return;
            }
            if (node.parentSignature != Long.MIN_VALUE) {
                childrenByParent.computeIfAbsent(node.parentSignature, ignored -> new ArrayList<>()).add(node);
            }
            if (!rootSignatures.add(node.positionSignature)) {
                return;
            }
            roots.add(node);
            if (maxPuzzles > 0 && roots.size() >= maxPuzzles) {
                throw new StopScanning();
            }
        }

        /**
         * Scores all collected roots after the parent-child index is available.
         */
        void finishScoring() {
            samples.clear();
            for (int i = 0; i < roots.size(); i++) {
                PuzzleNode root = roots.get(i);
                TreeBuild tree = buildTree(root);
                truncatedTrees += tree.truncated ? 1 : 0;
                Difficulty difficulty = Scorer.score(root.nodeScore, tree.summary);
                samples.add(new Sample(root.fen, difficulty));
                if ((i + 1) % 100_000 == 0) {
                    System.err.println("scored trees=" + (i + 1) + "/" + roots.size());
                }
            }
        }

        /**
         * Converts one verified record into a compact indexed puzzle node.
         */
        private PuzzleNode puzzleNode(Record rec) {
            try {
                Position position = rec.getPosition();
                short best = rec.getAnalysis().getBestMove();
                Position afterBest = position.copy().play(best);
                NodeScore nodeScore = Scorer.scoreNode(position, rec.getAnalysis());
                long parentSignature = rec.getParent() == null ? Long.MIN_VALUE : rec.getParent().signatureCore();
                return new PuzzleNode(
                        position.toString(),
                        parentSignature,
                        position.signatureCore(),
                        afterBest.signatureCore(),
                        best,
                        nodeScore);
            } catch (RuntimeException ex) {
                return null;
            }
        }

        /**
         * Builds the explicit continuation tree by matching after-best positions to
         * child record parents.
         */
        private TreeBuild buildTree(PuzzleNode root) {
            Position rootPosition = new Position(root.fen);
            PieceIdentityTracker rootIdentities = PieceIdentityTracker.from(rootPosition);
            TreeSummaryBuilder builder = new TreeSummaryBuilder(root.nodeScore,
                    rootIdentities.movingIdentity(rootPosition, root.solutionMove));
            Queue<NodeAtDepth> queue = new ArrayDeque<>();
            Set<Long> seen = new HashSet<>();
            queue.add(new NodeAtDepth(root, 1, rootIdentities));
            seen.add(root.positionSignature);
            boolean truncated = false;
            while (!queue.isEmpty()) {
                NodeAtDepth current = queue.remove();
                Position currentPosition = new Position(current.node.fen);
                Position afterSolution = playOrNull(currentPosition, current.node.solutionMove);
                PieceIdentityTracker afterSolutionIdentities = afterSolution == null
                        ? current.identities
                        : current.identities.after(currentPosition, current.node.solutionMove, afterSolution);
                List<PuzzleNode> children = uniqueChildren(childrenByParent.get(current.node.afterBestSignature));
                if (current.depth == 1) {
                    builder.setRootReplyCount(children.size());
                }
                if (children.size() > 1) {
                    builder.addBranch(children.size());
                }
                int childDepth = current.depth + 1;
                if (childDepth > MAX_TREE_SOLVER_DEPTH) {
                    truncated |= !children.isEmpty();
                    continue;
                }
                for (PuzzleNode child : children) {
                    if (!seen.add(child.positionSignature)) {
                        continue;
                    }
                    if (builder.nodeCount >= MAX_TREE_NODES_PER_ROOT) {
                        truncated = true;
                        queue.clear();
                        break;
                    }
                    Position childPosition = new Position(child.fen);
                    short reply = afterSolution == null
                            ? Move.NO_MOVE
                            : replyMove(afterSolution, child.positionSignature);
                    PieceIdentityTracker childIdentities = reply == Move.NO_MOVE
                            ? PieceIdentityTracker.from(childPosition)
                            : afterSolutionIdentities.after(afterSolution, reply, childPosition);
                    builder.addNode(child.nodeScore, childDepth,
                            childIdentities.movingIdentity(childPosition, child.solutionMove));
                    queue.add(new NodeAtDepth(child, childDepth, childIdentities));
                }
            }
            return new TreeBuild(builder.build(), truncated);
        }

        /**
         * Plays a move defensively.
         *
         * @param position source position
         * @param move encoded move
         * @return resulting position, or null when the move cannot be applied
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
         * Finds the opponent reply that reaches a child solver position.
         *
         * @param parentAfterSolution position after the parent solver move
         * @param childSignature child position signature
         * @return opponent reply move, or {@link Move#NO_MOVE}
         */
        private static short replyMove(Position parentAfterSolution, long childSignature) {
            if (parentAfterSolution == null) {
                return Move.NO_MOVE;
            }
            MoveList legal = parentAfterSolution.legalMoves();
            for (int i = 0; i < legal.size(); i++) {
                short move = legal.get(i);
                Position candidate = playOrNull(parentAfterSolution, move);
                if (candidate != null && candidate.signatureCore() == childSignature) {
                    return move;
                }
            }
            return Move.NO_MOVE;
        }

        /**
         * Deduplicates child records by analyzed position signature.
         */
        private static List<PuzzleNode> uniqueChildren(List<PuzzleNode> children) {
            if (children == null || children.isEmpty()) {
                return List.of();
            }
            if (children.size() == 1) {
                return children;
            }
            List<PuzzleNode> unique = new ArrayList<>(children.size());
            Set<Long> seen = new HashSet<>();
            for (PuzzleNode child : children) {
                if (seen.add(child.positionSignature)) {
                    unique.add(child);
                }
            }
            return unique;
        }

        /**
         * Determines whether a raw record should be counted as a puzzle.
         *
         * <p>
         * Mining outputs may carry a top-level {@code kind} field. Older stack files
         * often do not, so those are verified with the configured puzzle mining gate:
         * quality AND (winning OR drawing).
         * </p>
         *
         * @param objJson raw record JSON object
         * @param rec parsed record
         * @return true when the record is a verified puzzle
         */
        private boolean isPuzzle(String objJson, Record rec) {
            String kind = Json.parseStringField(objJson, "kind");
            if (kind != null && !kind.isBlank()) {
                return "puzzle".equalsIgnoreCase(kind);
            }
            return Config.getPuzzleVerify().apply(rec.getAnalysis());
        }
    }

    /**
     * Internal control-flow signal used to stop scanning after a sample cap.
     */
    private static final class StopScanning extends RuntimeException {

        /**
         * Avoids warning noise for this local control-flow exception.
         */
        private static final long serialVersionUID = 1L;
    }

    /**
     * Compact indexed puzzle record.
     *
     * @param fen source FEN
     * @param parentSignature parent FEN core signature, or {@link Long#MIN_VALUE}
     * @param positionSignature analyzed position core signature
     * @param afterBestSignature core signature after the solver best move
     * @param solutionMove encoded solver move
     * @param nodeScore per-position score
     */
    private record PuzzleNode(String fen, long parentSignature, long positionSignature, long afterBestSignature,
            short solutionMove, NodeScore nodeScore) {
    }

    /**
     * Queue item for tree traversal.
     *
     * @param node puzzle node
     * @param depth solver-position depth, root = 1
     * @param identities stable piece identities for the solver position
     */
    private record NodeAtDepth(PuzzleNode node, int depth, PieceIdentityTracker identities) {
    }

    /**
     * Result of one tree traversal.
     *
     * @param summary explicit tree summary
     * @param truncated whether safety caps stopped traversal
     */
    private record TreeBuild(PuzzleTreeSummary summary, boolean truncated) {
    }

    /**
     * Mutable explicit-tree summary builder.
     */
    private static final class TreeSummaryBuilder {

        /**
         * Count of solver positions included in the tree.
         */
        private int nodeCount;

        /**
         * Child replies immediately after the root key.
         */
        private int rootReplyCount = 1;

        /**
         * Branching parent count.
         */
        private int branchPointCount;

        /**
         * Maximum solver depth reached.
         */
        private int maxDepth;

        /**
         * Harmonic weighted non-root raw score numerator.
         */
        private double continuationWeightedRaw;

        /**
         * Harmonic weighted non-root denominator.
         */
        private double continuationWeight;

        /**
         * Harmonic depth mass over every non-root solver node.
         */
        private double continuationDepthLoad;

        /**
         * Hardest non-root node raw score.
         */
        private double continuationPeakRaw;

        /**
         * Sublinear branch burden.
         */
        private double branchLoad;

        /**
         * Bit mask of moving piece types.
         */
        private int pieceTypeMask;

        /**
         * Bit mask of non-pawn, non-king moving piece types.
         */
        private int nonPawnNonKingPieceTypeMask;

        /**
         * Stable moving-piece identities tracked through the explicit tree.
         */
        private final Set<Long> pieceIdentities = new HashSet<>();

        /**
         * Solver pawn moves.
         */
        private int pawnMoveCount;

        /**
         * Solver king moves.
         */
        private int kingMoveCount;

        /**
         * Move counts per stable moving piece.
         */
        private final Map<Long, Integer> pieceIdentityMoveCounts = new HashMap<>();

        /**
         * Non-checking non-capturing solver moves.
         */
        private int nonforcingMoveCount;

        /**
         * Underpromotion solver moves.
         */
        private int underpromotionCount;

        /**
         * En-passant solver moves.
         */
        private int enPassantCount;

        /**
         * Castling solver moves.
         */
        private int castleCount;

        /**
         * Creates a tree starting at the root.
         */
        TreeSummaryBuilder(NodeScore root, long rootIdentity) {
            addNode(root, 1, rootIdentity);
        }

        /**
         * Adds one solver node at a measured depth.
         */
        void addNode(NodeScore node, int depth, long pieceIdentity) {
            nodeCount++;
            maxDepth = Math.max(maxDepth, depth);
            if (depth > 1) {
                double weight = 1.0 / depth;
                continuationWeightedRaw += node.rawScore() * weight;
                continuationWeight += weight;
                continuationDepthLoad += weight;
                continuationPeakRaw = Math.max(continuationPeakRaw, node.rawScore());
            }
            if (node.pieceType() > 0) {
                pieceTypeMask |= 1 << node.pieceType();
            }
            if (node.pieceType() > chess.core.Piece.PAWN && node.pieceType() < chess.core.Piece.KING) {
                nonPawnNonKingPieceTypeMask |= 1 << node.pieceType();
            }
            if (node.pieceType() == chess.core.Piece.PAWN) {
                pawnMoveCount++;
            }
            if (node.pieceType() == chess.core.Piece.KING) {
                kingMoveCount++;
            }
            if (pieceIdentity != PieceIdentityTracker.NO_IDENTITY) {
                pieceIdentities.add(pieceIdentity);
                pieceIdentityMoveCounts.merge(pieceIdentity, 1, Integer::sum);
            }
            if (node.keyQuiet()) {
                nonforcingMoveCount++;
            }
            if (node.keyUnderpromotion()) {
                underpromotionCount++;
            }
            if (node.keyEnPassant()) {
                enPassantCount++;
            }
            if (node.keyCastle()) {
                castleCount++;
            }
        }

        /**
         * Records root reply count.
         */
        void setRootReplyCount(int count) {
            rootReplyCount = Math.max(1, count);
        }

        /**
         * Records one branching parent.
         */
        void addBranch(int childCount) {
            if (childCount <= 1) {
                return;
            }
            branchPointCount++;
            branchLoad += Math.log1p(childCount - 1.0);
        }

        /**
         * Builds the immutable scorer summary.
         */
        PuzzleTreeSummary build() {
            double continuationAverage = continuationWeight <= 0.0 ? 0.0 : continuationWeightedRaw / continuationWeight;
            double continuation = continuationWeight <= 0.0 ? 0.0
                    : 0.70 * continuationAverage + 0.30 * continuationPeakRaw;
            int dominantPieceMoves = 0;
            for (int count : pieceIdentityMoveCounts.values()) {
                dominantPieceMoves = Math.max(dominantPieceMoves, count);
            }
            double dominantPieceMoveShare = nodeCount <= 0 ? 0.0 : dominantPieceMoves / (double) nodeCount;
            return new PuzzleTreeSummary(
                    nodeCount,
                    rootReplyCount,
                    branchPointCount,
                    Math.max(1, maxDepth),
                    continuation,
                    continuationDepthLoad,
                    branchLoad,
                    Integer.bitCount(pieceTypeMask),
                    Integer.bitCount(nonPawnNonKingPieceTypeMask),
                    pieceIdentities.size(),
                    pawnMoveCount,
                    kingMoveCount,
                    dominantPieceMoveShare,
                    nonforcingMoveCount,
                    underpromotionCount,
                    enPassantCount,
                    castleCount);
        }
    }

    /**
     * One scored row.
     *
     * @param fen source FEN
     * @param difficulty scored difficulty
     */
    private record Sample(String fen, Difficulty difficulty) {

        /**
         * Returns the sample rating.
         */
        int rating() {
            return difficulty.rating();
        }

        /**
         * Returns the sample goal.
         */
        Goal goal() {
            return difficulty.goal();
        }

    }

    /**
     * Small collector helper for joining mapped stream entries.
     */
    private static final class StringJoinerCollector {

        /**
         * Utility class; prevent instantiation.
         */
        private StringJoinerCollector() {
            // utility
        }

        /**
         * Creates a joining collector.
         */
        static <T> java.util.stream.Collector<T, StringJoiner, String> collecting(String delimiter,
                java.util.function.Function<T, String> mapper) {
            return java.util.stream.Collector.of(
                    () -> new StringJoiner(delimiter),
                    (joiner, value) -> joiner.add(mapper.apply(value)),
                    StringJoiner::merge,
                    StringJoiner::toString);
        }
    }
}
