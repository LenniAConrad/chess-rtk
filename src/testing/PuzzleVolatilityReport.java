package testing;

import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
import chess.core.Position;
import chess.pdf.document.Canvas;
import chess.pdf.document.Document;
import chess.pdf.document.Font;
import chess.pdf.document.Page;
import chess.pdf.document.PageSize;
import chess.struct.Record;
import chess.uci.Analysis;
import chess.uci.Chances;
import chess.uci.Evaluation;
import chess.uci.Output;
import utility.Json;

/**
 * Manual report for finding puzzle records whose evaluation and PV change
 * sharply across search depth.
 *
 * <p>
 * This mirrors the manual puzzle difficulty distribution workflow: stream one or
 * more record dumps, keep verified puzzle records, rebuild parent-linked puzzle
 * stacks, then write CSV/SVG artifacts for offline inspection.
 * </p>
 *
 * <p>
 * The exported {@code score_elo} values are an Elo-like expected-score scale
 * when WDL is present. When a record has only centipawn output, the centipawn
 * value is used as the fallback score on the same signed axis. Mate scores are
 * capped into the tail so decisive mate swings remain visible without
 * dominating every chart.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class PuzzleVolatilityReport {

    /**
     * Histogram bucket width in score points.
     */
    static final int BIN_WIDTH = 25;

    /**
     * Minimum displayed swing axis.
     */
    static final int MIN_DISPLAY_MAX_SWING = 1200;

    /**
     * Score at which a position is treated as clearly winning.
     */
    static final int WIN_SCORE = 300;

    /**
     * Score window treated as unclear/drawish.
     */
    static final int DRAW_SCORE = 80;

    /**
     * Score swing that is high enough to deserve close inspection.
     */
    static final int VOLATILE_SWING = 400;

    /**
     * Score swing that indicates a sharp depth effect.
     */
    static final int SHARP_SWING = 800;

    /**
     * Score swing where the report tail is treated as extreme.
     */
    static final int EXTREME_SWING = 1200;

    /**
     * Right-side score padding when the sampled tail exceeds the default axis.
     */
    static final int DISPLAY_MAX_PADDING = 50;

    /**
     * Score cap used for WDL/mate display values.
     */
    static final int SCORE_CAP = 3200;

    /**
     * Maximum explicit solver-position depth to traverse per puzzle stack.
     */
    static final int MAX_TREE_SOLVER_DEPTH = 24;

    /**
     * Safety cap for one root tree.
     */
    static final int MAX_TREE_NODES_PER_ROOT = 100_000;

    /**
     * Shared SVG font family.
     */
    static final String SVG_FONT = "Arial,sans-serif";

    /**
     * Dark axis stroke color.
     */
    static final String AXIS_STROKE = "#1f2933";

    /**
     * Muted SVG text color.
     */
    static final String MUTED_TEXT_FILL = "#5f6b76";

    /**
     * SVG report image width.
     */
    static final int REPORT_WIDTH = 1600;

    /**
     * SVG report image height.
     */
    static final int REPORT_HEIGHT = 760;

    /**
     * PDF report timestamp format.
     */
    static final DateTimeFormatter REPORT_TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm z", Locale.ROOT);

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
     * Report colors copied from the puzzle difficulty PDF report.
     */
    static final Color REPORT_TEXT = new Color(17, 17, 17);
    /**
     * Color.
     * @param 74 74 value
     * @param 85 85 value
     * @param 98 98 value
     */
    static final Color REPORT_MUTED = new Color(74, 85, 98);
    /**
     * Color.
     * @param 111 111 value
     * @param 82 82 value
     * @param 56 56 value
     */
    static final Color REPORT_ACCENT = new Color(111, 82, 56);
    /**
     * Color.
     * @param 42 42 value
     * @param 32 32 value
     * @param 24 24 value
     */
    static final Color REPORT_RULE = new Color(42, 32, 24);
    /**
     * Color.
     * @param 216 216 value
     * @param 216 216 value
     * @param 216 216 value
     */
    static final Color REPORT_GRID = new Color(216, 216, 216);
    /**
     * Color.
     * @param 132 132 value
     * @param 185 185 value
     * @param 216 216 value
     */
    static final Color REPORT_BAR = new Color(132, 185, 216);
    /**
     * Color.
     * @param 92 92 value
     * @param 143 143 value
     * @param 174 174 value
     */
    static final Color REPORT_TREND = new Color(92, 143, 174);
    /**
     * Color.
     * @param 226 226 value
     * @param 211 211 value
     * @param 193 193 value
     */
    static final Color REPORT_SOFT_RULE = new Color(226, 211, 193);
    /**
     * Color.
     * @param 253 253 value
     * @param 249 249 value
     * @param 242 242 value
     */
    static final Color REPORT_CARD = new Color(253, 249, 242);
    /**
     * Color.
     * @param 254 254 value
     * @param 251 251 value
     * @param 247 247 value
     */
    static final Color REPORT_PANEL = new Color(254, 251, 247);
    /**
     * Color.
     * @param 247 247 value
     * @param 236 236 value
     * @param 220 220 value
     */
    static final Color REPORT_TABLE_HEADER = new Color(247, 236, 220);
    /**
     * Color.
     * @param 254 254 value
     * @param 251 251 value
     * @param 247 247 value
     */
    static final Color REPORT_TABLE_STRIPE = new Color(254, 251, 247);
    /**
     * Color.
     * @param 229 229 value
     * @param 214 214 value
     * @param 195 195 value
     */
    static final Color REPORT_TABLE_RULE = new Color(229, 214, 195);
    /**
     * Color.
     * @param 178 178 value
     * @param 178 178 value
     * @param 178 178 value
     */
    static final Color REPORT_QUANTILE_LINE = new Color(178, 178, 178);
    /**
     * R e p o r t  q u a n t i l e  l a b e l.
     */
    static final Color REPORT_QUANTILE_LABEL = REPORT_MUTED;

    /**
     * Report typography copied from the puzzle difficulty PDF report.
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
     * R e p o r t  d a t a  f o n t.
     */
    static final Font REPORT_DATA_FONT = Font.HELVETICA;
    /**
     * R e p o r t  d a t a  b o l d  f o n t.
     */
    static final Font REPORT_DATA_BOLD_FONT = Font.HELVETICA_BOLD;

    /**
     * Utility class; prevent instantiation.
     */
    private PuzzleVolatilityReport() {
        // utility
    }

    /**
     * Runs the report.
     *
     * @param args input record files and optional flags
     * @throws IOException if reading or writing fails
     */
    public static void main(String[] args) throws IOException {
        Arguments parsed = parseArguments(args);
        if (parsed == null) {
            System.err.println("Usage: java -cp out testing.PuzzleVolatilityReport "
                    + "[--out out-prefix] [--max-puzzles N] <records.json>...");
            System.exit(2);
        }
        Config.reload();
        VolatilityAccumulator acc = new VolatilityAccumulator(parsed.maxPuzzles());
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
        acc.finishReports();
        acc.rootReports.sort(Comparator.comparingInt(RootReport::treeMaxSwing).reversed()
                .thenComparing(Comparator.comparingInt(RootReport::treeReversalNodes).reversed())
                .thenComparing(report -> report.root().fen()));

        Path rootCsv = withExtension(parsed.prefix(), ".csv");
        Path nodeCsv = withExtension(parsed.prefix(), ".nodes.csv");
        Path timelineCsv = withExtension(parsed.prefix(), ".timeline.csv");
        Path svg = withExtension(parsed.prefix(), ".svg");
        Path pdf = withExtension(parsed.prefix(), ".pdf");
        List<RootReport> changedReports = changedReports(acc.rootReports);
        PuzzleVolatilityCsv.writeRootCsv(rootCsv, changedReports);
        PuzzleVolatilityCsv.writeNodeCsv(nodeCsv, changedReports);
        PuzzleVolatilityCsv.writeTimelineCsv(timelineCsv, changedReports);
        String svgText = PuzzleVolatilitySvg.buildSvg(acc.rootReports);
        PuzzleVolatilityOutput.writeSvg(svg, svgText);
        PuzzleVolatilityOutput.writePdf(pdf, acc.rootReports);
        printSummary(processedInputs, rootCsv, nodeCsv, timelineCsv, svg, pdf, acc);
    }

    /**
     * Parses command-line arguments.
     * @param args command arguments
     * @return parse arguments result
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
     * @param args command arguments
     * @return parse flagged invocation result
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
     * Builds the default output prefix.
     * @param inputs input values
     * @return default prefix result
     */
    private static Path defaultPrefix(List<Path> inputs) {
        return inputs.size() == 1 ? defaultPrefix(inputs.get(0)) : Path.of("puzzle-volatility");
    }

    /**
     * Builds the default output prefix next to the input file.
     * @param input input value
     * @return default prefix result
     */
    private static Path defaultPrefix(Path input) {
        Path parent = input.getParent();
        String name = input.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        Path file = Path.of(stem + ".volatility");
        return parent == null ? file : parent.resolve(file);
    }

    /**
     * Replaces the current suffix with an extension.
     * @param prefix prefix value
     * @param ext ext value
     * @return with extension result
     */
    private static Path withExtension(Path prefix, String ext) {
        return Path.of(prefix.toString() + ext);
    }

    /**
     * Prints the generated report paths and aggregate counts.
     * @param inputs input values
     * @param rootCsv root csv value
     * @param nodeCsv node csv value
     * @param timelineCsv timeline csv value
     * @param svg svg value
     * @param pdf pdf value
     * @param acc acc value
     */
    private static void printSummary(List<Path> inputs, Path rootCsv, Path nodeCsv, Path timelineCsv, Path svg,
            Path pdf, VolatilityAccumulator acc) {
        List<RootReport> reports = acc.rootReports;
        System.out.println("inputs: " + inputs.size());
        for (Path input : inputs) {
            System.out.println("input: " + input);
        }
        System.out.println("root_stacks: " + reports.size());
        System.out.println("node_rows: " + reports.stream().mapToInt(r -> r.nodes().size()).sum());
        List<RootReport> changedReports = changedReports(reports);
        System.out.println("changed_root_stacks: " + changedReports.size());
        System.out.println("changed_node_rows: " + changedNodeCountInReports(changedReports));
        System.out.println("non_puzzles: " + acc.nonPuzzles);
        System.out.println("skipped: " + acc.skipped);
        System.out.println("invalid: " + acc.invalid);
        System.out.println("indexed_puzzles: " + acc.roots.size());
        System.out.println("truncated_trees: " + acc.truncatedTrees);
        if (!reports.isEmpty()) {
            System.out.println("stack swing min/p50/p90/p99/max: "
                    + PuzzleVolatilityOutput.percentileSwing(reports, 0.00) + " / "
                    + PuzzleVolatilityOutput.percentileSwing(reports, 0.50) + " / "
                    + PuzzleVolatilityOutput.percentileSwing(reports, 0.90) + " / "
                    + PuzzleVolatilityOutput.percentileSwing(reports, 0.99) + " / "
                    + PuzzleVolatilityOutput.percentileSwing(reports, 1.00));
            System.out.println("stack severities: " + PuzzleVolatilityOutput.severityCounts(reports));
            System.out.println("stacks with reversals: "
                    + reports.stream().filter(r -> r.treeReversalNodes() > 0).count());
        }
        System.out.println("csv: " + rootCsv);
        System.out.println("nodes_csv: " + nodeCsv);
        System.out.println("timeline_csv: " + timelineCsv);
        System.out.println("svg: " + svg);
        System.out.println("pdf: " + pdf);
    }

    /**
     * Returns stacks with any measured depth swing.
     * @param reports report data rows
     * @return changed reports result
     */
    static List<RootReport> changedReports(List<RootReport> reports) {
        return reports.stream()
                .filter(report -> report.treeMaxSwing() > 0)
                .toList();
    }

    /**
     * Returns changed stacks with an actual sign/outcome reversal.
     * @param reports report data rows
     * @return reversal reports result
     */
    static List<RootReport> reversalReports(List<RootReport> reports) {
        return reports.stream()
                .filter(report -> "reversal".equals(report.stackSeverity()))
                .toList();
    }

    /**
     * Counts stacks whose tree contains a sign/outcome reversal.
     * @param reports report data rows
     * @return reversal count result
     */
    static int reversalCount(List<RootReport> reports) {
        int count = 0;
        for (RootReport report : reports) {
            if (report.treeReversalNodes() > 0) {
                count++;
            }
        }
        return count;
    }

    /**
     * Counts nodes with a measured depth swing.
     * @param reports report data rows
     * @return changed node count in reports result
     */
    static int changedNodeCountInReports(List<RootReport> reports) {
        int count = 0;
        for (RootReport report : reports) {
            count += changedNodeCountInNodes(report.nodes());
        }
        return count;
    }

    /**
     * Counts nodes with a measured depth swing.
     * @param nodes node count
     * @return changed node count in nodes result
     */
    static int changedNodeCountInNodes(List<NodeReport> nodes) {
        int count = 0;
        for (NodeReport node : nodes) {
            if (isChangedNode(node)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns whether a node has any measured depth swing.
     * @param node node value
     * @return true when is changed node
     */
    static boolean isChangedNode(NodeReport node) {
        return node != null && node.volatility().swing() > 0;
    }

    /**
     * Returns the node that explains a root stack's highest volatility rank.
     * @param report report data
     * @return worst node result
     */
    static NodeReport worstNode(RootReport report) {
        NodeReport worst = report.root();
        for (NodeReport node : report.nodes()) {
            if (isMoreImportantNode(node, worst)) {
                worst = node;
            }
        }
        return worst;
    }

    /**
     * Compares node volatility for human-facing root summaries.
     * @param candidate candidate value
     * @param current current value
     * @return true when is more important node
     */
    static boolean isMoreImportantNode(NodeReport candidate, NodeReport current) {
        Volatility c = candidate.volatility();
        Volatility w = current.volatility();
        if (c.swing() != w.swing()) {
            return c.swing() > w.swing();
        }
        if (severityRank(c.severity()) != severityRank(w.severity())) {
            return severityRank(c.severity()) > severityRank(w.severity());
        }
        if (c.pvChanges() != w.pvChanges()) {
            return c.pvChanges() > w.pvChanges();
        }
        if (c.bestMoveChanges() != w.bestMoveChanges()) {
            return c.bestMoveChanges() > w.bestMoveChanges();
        }
        return candidate.depth() < current.depth();
    }

    /**
     * Orders severity labels for tie-breaking.
     * @param severity severity value
     * @return severity rank result
     */
    static int severityRank(String severity) {
        return switch (severity == null ? "" : severity) {
            case "reversal" -> 4;
            case "sharp" -> 3;
            case "volatile" -> 2;
            case "noisy" -> 1;
            default -> 0;
        };
    }

    /**
     * Creates the parent directory for an output path.
     * @param path file path
     * @throws java.io.IOException if IOException is raised by the underlying operation
     */
    static void createParent(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    /**
     * Escapes one CSV field.
     * @param value value to use
     * @return csv result
     */
    static String csv(String value) {
        String v = value == null ? "" : value;
        if (v.indexOf(',') < 0 && v.indexOf('"') < 0 && v.indexOf('\n') < 0 && v.indexOf('\r') < 0) {
            return v;
        }
        return '"' + v.replace("\"", "\"\"") + '"';
    }

    /**
     * Formats an optional integer CSV field.
     * @param value value to use
     * @return csv result
     */
    static String csv(Integer value) {
        return value == null ? "" : value.toString();
    }

    /**
     * Formats an optional long CSV field.
     * @param value value to use
     * @return csv result
     */
    static String csv(Long value) {
        return value == null ? "" : value.toString();
    }

    /**
     * Computes one analysis depth-volatility summary.
     * @param analysis analysis value
     * @return volatility summary
     */
    static Volatility volatility(Analysis analysis) {
        List<TimelineEntry> timeline = timeline(analysis);
        if (timeline.isEmpty()) {
            return Volatility.empty();
        }
        TimelineEntry first = timeline.get(0);
        TimelineEntry last = timeline.get(timeline.size() - 1);
        TimelineEntry min = first;
        TimelineEntry max = first;
        int worstAdjacentDelta = 0;
        int signFlips = 0;
        int outcomeChanges = 0;
        int bestMoveChanges = 0;
        int pvChanges = 0;
        double lcpSum = 0.0;
        int lcpCount = 0;
        boolean seenWinning = false;
        boolean seenLosing = false;
        boolean losingToWinning = false;
        boolean winningToLosing = false;

        for (int i = 0; i < timeline.size(); i++) {
            TimelineEntry entry = timeline.get(i);
            if (entry.score() < min.score()) {
                min = entry;
            }
            if (entry.score() > max.score()) {
                max = entry;
            }
            if (entry.outcome() == Outcome.WINNING) {
                losingToWinning |= seenLosing;
                seenWinning = true;
            } else if (entry.outcome() == Outcome.LOSING) {
                winningToLosing |= seenWinning;
                seenLosing = true;
            }
            if (i == 0) {
                continue;
            }
            TimelineEntry prev = timeline.get(i - 1);
            worstAdjacentDelta = Math.max(worstAdjacentDelta, Math.abs(entry.score() - prev.score()));
            if (prev.outcome() != entry.outcome()) {
                outcomeChanges++;
            }
            if (oppositeSigns(prev.score(), entry.score())) {
                signFlips++;
            }
            if (prev.bestMove() != Move.NO_MOVE && entry.bestMove() != Move.NO_MOVE
                    && prev.bestMove() != entry.bestMove()) {
                bestMoveChanges++;
            }
            if (isPvRewrite(prev.pv(), entry.pv())) {
                pvChanges++;
            }
            lcpSum += lcp(prev.pv(), entry.pv());
            lcpCount++;
        }

        int swing = max.score() - min.score();
        int net = last.score() - first.score();
        String direction = direction(first.outcome(), last.outcome(), losingToWinning, winningToLosing);
        int finalBestStableDepth = finalBestStableDepth(timeline, last.bestMove());
        int firstToFinalLcp = lcp(first.pv(), last.pv());
        double avgAdjacentLcp = lcpCount == 0 ? 0.0 : lcpSum / lcpCount;
        SecondLine second = secondLine(analysis, last.depth(), last.score());
        return new Volatility(
                List.copyOf(timeline),
                timeline.size(),
                scoreSources(timeline),
                first.depth(),
                last.depth(),
                first.score(),
                last.score(),
                min.score(),
                min.depth(),
                max.score(),
                max.depth(),
                swing,
                net,
                worstAdjacentDelta,
                signFlips,
                outcomeChanges,
                bestMoveChanges,
                pvChanges,
                finalBestStableDepth,
                firstToFinalLcp,
                avgAdjacentLcp,
                last.nodes(),
                last.timeMs(),
                direction,
                severity(swing, direction),
                moveToString(first.bestMove()),
                moveToString(last.bestMove()),
                second.bestMove(),
                second.score(),
                second.margin(),
                pvToString(first.pv()),
                pvToString(last.pv()));
    }

    /**
     * Builds a sorted PV1 timeline.
     * @param analysis analysis value
     * @return timeline result
     */
    private static List<TimelineEntry> timeline(Analysis analysis) {
        if (analysis == null || analysis.isEmpty()) {
            return List.of();
        }
        List<TimelineEntry> entries = new ArrayList<>();
        for (Output output : analysis.getOutputs()) {
            if (output == null || output.getPrincipalVariation() != 1) {
                continue;
            }
            ScoredOutput score = scored(output);
            if (score == null) {
                continue;
            }
            short[] pv = output.getMoves();
            short best = pv != null && pv.length > 0 ? pv[0] : Move.NO_MOVE;
            entries.add(new TimelineEntry(output.getDepth(), score.score(), score.source(), score.rawEval(),
                    score.rawWdl(), outcome(score.score()),
                    best, optionalLong(output.hasNodes(), output.getNodes()),
                    optionalLong(output.hasTime(), output.getTime()),
                    pv == null ? new short[0] : pv));
        }
        entries.sort(Comparator.comparingInt(TimelineEntry::depth));
        return entries;
    }

    /**
     * Converts one UCI output into a signed score.
     * @param output output text
     * @return scored result
     */
    private static ScoredOutput scored(Output output) {
        if (output.hasChances()) {
            Chances c = output.getChances();
            double expected = c.winChanceToDouble() + 0.5 * c.drawChanceToDouble();
            return new ScoredOutput(expectedToElo(expected), "wdl", rawEval(output), c.toUciString());
        }
        if (output.hasEvaluation()) {
            Evaluation e = output.getEvaluation();
            if (e.isMate()) {
                return new ScoredOutput(mateToScore(e.getValue()), "mate", rawEval(output), rawWdl(output));
            }
            return new ScoredOutput(clampScore(e.getValue()), "cp", rawEval(output), rawWdl(output));
        }
        return null;
    }

    /**
     * Formats the raw UCI evaluation field.
     * @param output output text
     * @return raw eval result
     */
    private static String rawEval(Output output) {
        if (output == null || !output.hasEvaluation()) {
            return "";
        }
        Evaluation e = output.getEvaluation();
        return e.isMate() ? "mate " + e.getValue() : "cp " + e.getValue();
    }

    /**
     * Formats the raw WDL field.
     * @param output output text
     * @return raw wdl result
     */
    private static String rawWdl(Output output) {
        return output != null && output.hasChances() ? output.getChances().toUciString() : "";
    }

    /**
     * Returns an optional long field.
     * @param present present value
     * @param value value to use
     * @return optional long result
     */
    private static Long optionalLong(boolean present, long value) {
        return present ? value : null;
    }

    /**
     * Builds a compact score-source list.
     * @param timeline timeline values
     * @return score sources result
     */
    private static String scoreSources(List<TimelineEntry> timeline) {
        StringJoiner joiner = new StringJoiner("|");
        Set<String> seen = new java.util.LinkedHashSet<>();
        for (TimelineEntry entry : timeline) {
            if (seen.add(entry.source())) {
                joiner.add(entry.source());
            }
        }
        return joiner.toString();
    }

    /**
     * Returns final-depth PV2 information when available.
     * @param analysis analysis value
     * @param finalDepth final depth value
     * @param finalScore final score value
     * @return second line result
     */
    private static SecondLine secondLine(Analysis analysis, int finalDepth, int finalScore) {
        Output output = analysis == null ? null : analysis.get(finalDepth, 2);
        if (output == null && analysis != null) {
            output = analysis.getBestOutput(2);
        }
        if (output == null) {
            return SecondLine.empty();
        }
        ScoredOutput score = scored(output);
        if (score == null) {
            return SecondLine.empty();
        }
        short[] pv = output.getMoves();
        short best = pv != null && pv.length > 0 ? pv[0] : Move.NO_MOVE;
        return new SecondLine(moveToString(best), score.score(), finalScore - score.score());
    }

    /**
     * Converts expected score to an Elo-like signed score.
     * @param expected expected value
     * @return expected to elo result
     */
    private static int expectedToElo(double expected) {
        double eps = 1e-6;
        double clamped = Math.max(eps, Math.min(1.0 - eps, expected));
        return clampScore((int) Math.round(400.0 * Math.log10(clamped / (1.0 - clamped))));
    }

    /**
     * Maps mate distance to the display tail.
     * @param mateValue mate value value
     * @return mate to score result
     */
    private static int mateToScore(int mateValue) {
        if (mateValue == 0) {
            return 0;
        }
        int sign = mateValue > 0 ? 1 : -1;
        int distance = Math.max(1, Math.abs(mateValue));
        int score = SCORE_CAP - Math.min(1200, (distance - 1) * 35);
        return sign * score;
    }

    /**
     * Clamps score values into the report display range.
     * @param value value to use
     * @return clamp score result
     */
    private static int clampScore(int value) {
        return Math.max(-SCORE_CAP, Math.min(SCORE_CAP, value));
    }

    /**
     * Classifies one score.
     * @param score score value
     * @return outcome result
     */
    private static Outcome outcome(int score) {
        if (score >= WIN_SCORE) {
            return Outcome.WINNING;
        }
        if (score <= -WIN_SCORE) {
            return Outcome.LOSING;
        }
        if (Math.abs(score) <= DRAW_SCORE) {
            return Outcome.DRAWISH;
        }
        return Outcome.UNCLEAR;
    }

    /**
     * Returns whether two scores cross opposite decisive signs.
     * @param a first value
     * @param b second value
     * @return opposite signs result
     */
    private static boolean oppositeSigns(int a, int b) {
        return a >= WIN_SCORE && b <= -WIN_SCORE || a <= -WIN_SCORE && b >= WIN_SCORE;
    }

    /**
     * Labels the depth direction.
     * @param first first value
     * @param last last value
     * @param losingToWinning losing to winning value
     * @param winningToLosing winning to losing value
     * @return direction result
     */
    private static String direction(Outcome first, Outcome last, boolean losingToWinning, boolean winningToLosing) {
        if (losingToWinning && winningToLosing) {
            return "both_reversals";
        }
        if (losingToWinning) {
            return "losing_to_winning";
        }
        if (winningToLosing) {
            return "winning_to_losing";
        }
        if (first != last) {
            return first.label() + "_to_" + last.label();
        }
        return first.label();
    }

    /**
     * Labels volatility severity.
     * @param swing swing value
     * @param direction direction value
     * @return severity result
     */
    static String severity(int swing, String direction) {
        if (direction.endsWith("_reversals") || "losing_to_winning".equals(direction)
                || "winning_to_losing".equals(direction)) {
            return "reversal";
        }
        if (swing >= SHARP_SWING) {
            return "sharp";
        }
        if (swing >= VOLATILE_SWING) {
            return "volatile";
        }
        if (swing >= 150) {
            return "noisy";
        }
        return "stable";
    }

    /**
     * Returns whether two PVs have rewritten an existing prefix.
     * @param a first value
     * @param b second value
     * @return true when is pv rewrite
     */
    private static boolean isPvRewrite(short[] a, short[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0) {
            return false;
        }
        int min = Math.min(a.length, b.length);
        return lcp(a, b) < min;
    }

    /**
     * Returns longest common prefix in plies.
     * @param a first value
     * @param b second value
     * @return lcp result
     */
    private static int lcp(short[] a, short[] b) {
        if (a == null || b == null) {
            return 0;
        }
        int min = Math.min(a.length, b.length);
        int i = 0;
        while (i < min && a[i] == b[i]) {
            i++;
        }
        return i;
    }

    /**
     * Returns the earliest depth where the final best move becomes stable.
     * @param timeline timeline values
     * @param finalBest final best value
     * @return final best stable depth result
     */
    private static int finalBestStableDepth(List<TimelineEntry> timeline, short finalBest) {
        if (timeline.isEmpty() || finalBest == Move.NO_MOVE) {
            return 0;
        }
        int earliest = timeline.get(timeline.size() - 1).depth();
        for (int i = timeline.size() - 1; i >= 0; i--) {
            TimelineEntry entry = timeline.get(i);
            if (entry.bestMove() != finalBest) {
                break;
            }
            earliest = entry.depth();
        }
        return earliest;
    }

    /**
     * Converts a move to UCI text.
     * @param move move encoded in CRTK move format
     * @return move to string result
     */
    static String moveToString(short move) {
        return move == Move.NO_MOVE ? "" : Move.toString(move);
    }

    /**
     * Converts a PV to UCI text.
     * @param pv pv value
     * @return pv to string result
     */
    static String pvToString(short[] pv) {
        if (pv == null || pv.length == 0) {
            return "";
        }
        StringJoiner joiner = new StringJoiner(" ");
        for (short move : pv) {
            joiner.add(Move.toString(move));
        }
        return joiner.toString();
    }

    /**
     * Parsed command-line arguments.
     */
}
