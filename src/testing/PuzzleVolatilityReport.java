/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

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
    private static final int BIN_WIDTH = 25;

    /**
     * Minimum displayed swing axis.
     */
    private static final int MIN_DISPLAY_MAX_SWING = 1200;

    /**
     * Score at which a position is treated as clearly winning.
     */
    private static final int WIN_SCORE = 300;

    /**
     * Score window treated as unclear/drawish.
     */
    private static final int DRAW_SCORE = 80;

    /**
     * Score swing that is high enough to deserve close inspection.
     */
    private static final int VOLATILE_SWING = 400;

    /**
     * Score swing that indicates a sharp depth effect.
     */
    private static final int SHARP_SWING = 800;

    /**
     * Score swing where the report tail is treated as extreme.
     */
    private static final int EXTREME_SWING = 1200;

    /**
     * Right-side score padding when the sampled tail exceeds the default axis.
     */
    private static final int DISPLAY_MAX_PADDING = 50;

    /**
     * Score cap used for WDL/mate display values.
     */
    private static final int SCORE_CAP = 3200;

    /**
     * Maximum explicit solver-position depth to traverse per puzzle stack.
     */
    private static final int MAX_TREE_SOLVER_DEPTH = 24;

    /**
     * Safety cap for one root tree.
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
     * SVG report image width.
     */
    private static final int REPORT_WIDTH = 1600;

    /**
     * SVG report image height.
     */
    private static final int REPORT_HEIGHT = 760;

    /**
     * PDF report timestamp format.
     */
    private static final DateTimeFormatter REPORT_TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm z", Locale.ROOT);

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
     * Report colors copied from the puzzle difficulty PDF report.
     */
    private static final Color REPORT_TEXT = new Color(17, 17, 17);
    /**
     * Color.
     * @param 74 74 value
     * @param 85 85 value
     * @param 98 98 value */
    private static final Color REPORT_MUTED = new Color(74, 85, 98);
    /**
     * Color.
     * @param 111 111 value
     * @param 82 82 value
     * @param 56 56 value */
    private static final Color REPORT_ACCENT = new Color(111, 82, 56);
    /**
     * Color.
     * @param 42 42 value
     * @param 32 32 value
     * @param 24 24 value */
    private static final Color REPORT_RULE = new Color(42, 32, 24);
    /**
     * Color.
     * @param 216 216 value
     * @param 216 216 value
     * @param 216 216 value */
    private static final Color REPORT_GRID = new Color(216, 216, 216);
    /**
     * Color.
     * @param 132 132 value
     * @param 185 185 value
     * @param 216 216 value */
    private static final Color REPORT_BAR = new Color(132, 185, 216);
    /**
     * Color.
     * @param 92 92 value
     * @param 143 143 value
     * @param 174 174 value */
    private static final Color REPORT_TREND = new Color(92, 143, 174);
    /**
     * Color.
     * @param 226 226 value
     * @param 211 211 value
     * @param 193 193 value */
    private static final Color REPORT_SOFT_RULE = new Color(226, 211, 193);
    /**
     * Color.
     * @param 253 253 value
     * @param 249 249 value
     * @param 242 242 value */
    private static final Color REPORT_CARD = new Color(253, 249, 242);
    /**
     * Color.
     * @param 254 254 value
     * @param 251 251 value
     * @param 247 247 value */
    private static final Color REPORT_PANEL = new Color(254, 251, 247);
    /**
     * Color.
     * @param 247 247 value
     * @param 236 236 value
     * @param 220 220 value */
    private static final Color REPORT_TABLE_HEADER = new Color(247, 236, 220);
    /**
     * Color.
     * @param 254 254 value
     * @param 251 251 value
     * @param 247 247 value */
    private static final Color REPORT_TABLE_STRIPE = new Color(254, 251, 247);
    /**
     * Color.
     * @param 229 229 value
     * @param 214 214 value
     * @param 195 195 value */
    private static final Color REPORT_TABLE_RULE = new Color(229, 214, 195);
    /**
     * Color.
     * @param 178 178 value
     * @param 178 178 value
     * @param 178 178 value */
    private static final Color REPORT_QUANTILE_LINE = new Color(178, 178, 178);
    /**
     * R e p o r t  q u a n t i l e  l a b e l.
     */
    private static final Color REPORT_QUANTILE_LABEL = REPORT_MUTED;

    /**
     * Report typography copied from the puzzle difficulty PDF report.
     */
    private static final Font REPORT_DISPLAY_FONT = Font.LATIN_MODERN_BOLD;
    /**
     * R e p o r t  s e c t i o n  f o n t.
     */
    private static final Font REPORT_SECTION_FONT = Font.LATIN_MODERN_BOLD;
    /**
     * R e p o r t  b o d y  f o n t.
     */
    private static final Font REPORT_BODY_FONT = Font.LATIN_MODERN_ROMAN;
    /**
     * R e p o r t  d a t a  f o n t.
     */
    private static final Font REPORT_DATA_FONT = Font.HELVETICA;
    /**
     * R e p o r t  d a t a  b o l d  f o n t.
     */
    private static final Font REPORT_DATA_BOLD_FONT = Font.HELVETICA_BOLD;

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
        writeRootCsv(rootCsv, changedReports);
        writeNodeCsv(nodeCsv, changedReports);
        writeTimelineCsv(timelineCsv, changedReports);
        String svgText = buildSvg(acc.rootReports);
        writeSvg(svg, svgText);
        writePdf(pdf, acc.rootReports);
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
     * Writes one row per root puzzle stack.
     * @param path file path
     * @param reports report data rows
     * @throws java.io.IOException if IOException is raised by the underlying operation
     */
    private static void writeRootCsv(Path path, List<RootReport> reports) throws IOException {
        createParent(path);
        StringBuilder sb = new StringBuilder(Math.max(1024, reports.size() * 420));
        sb.append("rank,tree_worst_fen_id,engine,created,stack_severity,root_severity,root_direction,score_sources,")
                .append("root_depth_samples,tree_max_swing_elo,root_swing_elo,root_net_elo,")
                .append("root_start_depth,root_final_depth,root_start_score_elo,")
                .append("root_final_score_elo,root_min_score_elo,root_min_depth,root_max_score_elo,")
                .append("root_max_depth,root_worst_adjacent_delta_elo,root_sign_flips,")
                .append("root_outcome_changes,root_bestmove_changes,root_pv_changes,")
                .append("root_final_best_depth,root_first_to_final_lcp,root_avg_adjacent_lcp,")
                .append("root_final_nodes,root_final_time_ms,root_start_best,root_final_best,")
                .append("root_final_second_best,root_final_second_score_elo,root_final_margin_elo,")
                .append("tree_nodes,tree_depth,tree_branch_points,tree_changed_nodes,tree_unstable_nodes,")
                .append("tree_reversal_nodes,tree_bestmove_changes,tree_pv_changes,")
                .append("tree_worst_node_depth,tree_worst_node_severity,tree_worst_node_direction,")
                .append("tree_worst_node_swing_elo,tree_worst_node_net_elo,")
                .append("tree_worst_node_start_depth,tree_worst_node_final_depth,")
                .append("tree_worst_node_start_score_elo,tree_worst_node_final_score_elo,")
                .append("tree_worst_node_bestmove_changes,tree_worst_node_pv_changes,")
                .append("tree_worst_node_start_best,tree_worst_node_final_best,")
                .append("tree_worst_node_final_second_best,tree_worst_node_final_margin_elo,")
                .append("tree_worst_fen,tree_worst_start_pv,tree_worst_final_pv,")
                .append("root_start_pv,root_final_pv,fen\n");
        for (int i = 0; i < reports.size(); i++) {
            RootReport report = reports.get(i);
            NodeReport root = report.root();
            Volatility v = root.volatility();
            NodeReport worst = worstNode(report);
            Volatility worstVolatility = worst.volatility();
            sb.append(i + 1).append(',');
            sb.append(fenId(worst.fen())).append(',');
            sb.append(csv(root.engine())).append(',');
            sb.append(root.created()).append(',');
            sb.append(report.stackSeverity()).append(',');
            sb.append(v.severity()).append(',');
            sb.append(v.direction()).append(',');
            sb.append(csv(v.scoreSources())).append(',');
            sb.append(v.depthSamples()).append(',');
            sb.append(report.treeMaxSwing()).append(',');
            sb.append(v.swing()).append(',');
            sb.append(v.net()).append(',');
            sb.append(v.startDepth()).append(',');
            sb.append(v.finalDepth()).append(',');
            sb.append(v.startScore()).append(',');
            sb.append(v.finalScore()).append(',');
            sb.append(v.minScore()).append(',');
            sb.append(v.minDepth()).append(',');
            sb.append(v.maxScore()).append(',');
            sb.append(v.maxDepth()).append(',');
            sb.append(v.worstAdjacentDelta()).append(',');
            sb.append(v.signFlips()).append(',');
            sb.append(v.outcomeChanges()).append(',');
            sb.append(v.bestMoveChanges()).append(',');
            sb.append(v.pvChanges()).append(',');
            sb.append(v.finalBestStableDepth()).append(',');
            sb.append(v.firstToFinalLcp()).append(',');
            sb.append(String.format(Locale.ROOT, "%.2f", v.avgAdjacentLcp())).append(',');
            sb.append(csv(v.finalNodes())).append(',');
            sb.append(csv(v.finalTimeMs())).append(',');
            sb.append(csv(v.startBestMove())).append(',');
            sb.append(csv(v.finalBestMove())).append(',');
            sb.append(csv(v.finalSecondBestMove())).append(',');
            sb.append(csv(v.finalSecondScore())).append(',');
            sb.append(csv(v.finalMargin())).append(',');
            sb.append(report.treeNodes()).append(',');
            sb.append(report.treeDepth()).append(',');
            sb.append(report.treeBranchPoints()).append(',');
            sb.append(changedNodeCountInNodes(report.nodes())).append(',');
            sb.append(report.treeUnstableNodes()).append(',');
            sb.append(report.treeReversalNodes()).append(',');
            sb.append(report.treeBestMoveChanges()).append(',');
            sb.append(report.treePvChanges()).append(',');
            sb.append(worst.depth()).append(',');
            sb.append(worstVolatility.severity()).append(',');
            sb.append(worstVolatility.direction()).append(',');
            sb.append(worstVolatility.swing()).append(',');
            sb.append(worstVolatility.net()).append(',');
            sb.append(worstVolatility.startDepth()).append(',');
            sb.append(worstVolatility.finalDepth()).append(',');
            sb.append(worstVolatility.startScore()).append(',');
            sb.append(worstVolatility.finalScore()).append(',');
            sb.append(worstVolatility.bestMoveChanges()).append(',');
            sb.append(worstVolatility.pvChanges()).append(',');
            sb.append(csv(worstVolatility.startBestMove())).append(',');
            sb.append(csv(worstVolatility.finalBestMove())).append(',');
            sb.append(csv(worstVolatility.finalSecondBestMove())).append(',');
            sb.append(csv(worstVolatility.finalMargin())).append(',');
            sb.append(csv(worst.fen())).append(',');
            sb.append(csv(worstVolatility.startPv())).append(',');
            sb.append(csv(worstVolatility.finalPv())).append(',');
            sb.append(csv(v.startPv())).append(',');
            sb.append(csv(v.finalPv())).append(',');
            sb.append(csv(root.fen())).append('\n');
        }
        Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
    }

    /**
     * Writes one row per puzzle-stack node.
     * @param path file path
     * @param reports report data rows
     * @throws java.io.IOException if IOException is raised by the underlying operation
     */
    private static void writeNodeCsv(Path path, List<RootReport> reports) throws IOException {
        createParent(path);
        StringBuilder sb = new StringBuilder(Math.max(1024, reports.size() * 360));
        sb.append("root_rank,node_depth,node_kind,fen_id,engine,created,severity,direction,score_sources,")
                .append("depth_samples,swing_elo,net_elo,start_depth,final_depth,start_score_elo,")
                .append("final_score_elo,min_score_elo,min_depth,")
                .append("max_score_elo,max_depth,worst_adjacent_delta_elo,sign_flips,outcome_changes,")
                .append("bestmove_changes,pv_changes,final_best_depth,first_to_final_lcp,")
                .append("avg_adjacent_lcp,final_nodes,final_time_ms,start_best,final_best,")
                .append("final_second_best,final_second_score_elo,final_margin_elo,start_pv,final_pv,fen\n");
        for (int i = 0; i < reports.size(); i++) {
            RootReport report = reports.get(i);
            for (NodeReport node : report.nodes()) {
                if (!isChangedNode(node)) {
                    continue;
                }
                Volatility v = node.volatility();
                sb.append(i + 1).append(',');
                sb.append(node.depth()).append(',');
                sb.append(node.depth() == 1 ? "root" : "continuation").append(',');
                sb.append(fenId(node.fen())).append(',');
                sb.append(csv(node.engine())).append(',');
                sb.append(node.created()).append(',');
                sb.append(v.severity()).append(',');
                sb.append(v.direction()).append(',');
                sb.append(csv(v.scoreSources())).append(',');
                sb.append(v.depthSamples()).append(',');
                sb.append(v.swing()).append(',');
                sb.append(v.net()).append(',');
                sb.append(v.startDepth()).append(',');
                sb.append(v.finalDepth()).append(',');
                sb.append(v.startScore()).append(',');
                sb.append(v.finalScore()).append(',');
                sb.append(v.minScore()).append(',');
                sb.append(v.minDepth()).append(',');
                sb.append(v.maxScore()).append(',');
                sb.append(v.maxDepth()).append(',');
                sb.append(v.worstAdjacentDelta()).append(',');
                sb.append(v.signFlips()).append(',');
                sb.append(v.outcomeChanges()).append(',');
                sb.append(v.bestMoveChanges()).append(',');
                sb.append(v.pvChanges()).append(',');
                sb.append(v.finalBestStableDepth()).append(',');
                sb.append(v.firstToFinalLcp()).append(',');
                sb.append(String.format(Locale.ROOT, "%.2f", v.avgAdjacentLcp())).append(',');
                sb.append(csv(v.finalNodes())).append(',');
                sb.append(csv(v.finalTimeMs())).append(',');
                sb.append(csv(v.startBestMove())).append(',');
                sb.append(csv(v.finalBestMove())).append(',');
                sb.append(csv(v.finalSecondBestMove())).append(',');
                sb.append(csv(v.finalSecondScore())).append(',');
                sb.append(csv(v.finalMargin())).append(',');
                sb.append(csv(v.startPv())).append(',');
                sb.append(csv(v.finalPv())).append(',');
                sb.append(csv(node.fen())).append('\n');
            }
        }
        Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
    }

    /**
     * Writes one row per scored depth sample.
     * @param path file path
     * @param reports report data rows
     * @throws java.io.IOException if IOException is raised by the underlying operation
     */
    private static void writeTimelineCsv(Path path, List<RootReport> reports) throws IOException {
        createParent(path);
        StringBuilder sb = new StringBuilder(Math.max(1024, reports.size() * 720));
        sb.append("root_rank,node_depth,node_kind,fen_id,node_severity,node_direction,node_swing_elo,")
                .append("engine,created,timeline_depth,score_elo,")
                .append("score_source,raw_eval,raw_wdl,outcome,best,nodes,time_ms,pv,fen\n");
        for (int i = 0; i < reports.size(); i++) {
            RootReport report = reports.get(i);
            for (NodeReport node : report.nodes()) {
                if (!isChangedNode(node)) {
                    continue;
                }
                Volatility v = node.volatility();
                for (TimelineEntry entry : v.timeline()) {
                    sb.append(i + 1).append(',');
                    sb.append(node.depth()).append(',');
                    sb.append(node.depth() == 1 ? "root" : "continuation").append(',');
                    sb.append(fenId(node.fen())).append(',');
                    sb.append(v.severity()).append(',');
                    sb.append(v.direction()).append(',');
                    sb.append(v.swing()).append(',');
                    sb.append(csv(node.engine())).append(',');
                    sb.append(node.created()).append(',');
                    sb.append(entry.depth()).append(',');
                    sb.append(entry.score()).append(',');
                    sb.append(entry.source()).append(',');
                    sb.append(csv(entry.rawEval())).append(',');
                    sb.append(csv(entry.rawWdl())).append(',');
                    sb.append(entry.outcome().label()).append(',');
                    sb.append(csv(moveToString(entry.bestMove()))).append(',');
                    sb.append(csv(entry.nodes())).append(',');
                    sb.append(csv(entry.timeMs())).append(',');
                    sb.append(csv(pvToString(entry.pv()))).append(',');
                    sb.append(csv(node.fen())).append('\n');
                }
            }
        }
        Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
    }

    /**
     * Builds the root-stack swing distribution as SVG.
     * @param reports report data rows
     * @return build svg result
     */
    private static String buildSvg(List<RootReport> reports) {
        List<RootReport> changedReports = changedReports(reports);
        int displayMax = displayMaxSwing(changedReports);
        int[] bins = bins(changedReports, displayMax);
        double[] percentages = percentages(bins, changedReports.size());
        double maxPercent = max(percentages);
        double percentStep = percentTickStep(maxPercent);
        double axisMaxPercent = Math.max(percentStep, Math.ceil(maxPercent / percentStep) * percentStep);
        int width = REPORT_WIDTH;
        int height = REPORT_HEIGHT;
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
        appendText(sb, left, 32, null, 22, "#17202a", "Puzzle depth volatility");
        appendText(sb, left, 56, null, 12, MUTED_TEXT_FILL,
                reports.size() + " puzzle stacks; " + changedReports.size() + " changed; "
                        + reversalCount(reports) + " reversals; changed p50 "
                        + percentileSwing(changedReports, 0.50)
                        + ", p90 " + percentileSwing(changedReports, 0.90)
                        + ", p99 " + percentileSwing(changedReports, 0.99)
                        + "; " + BIN_WIDTH + "-point bars show % of changed stacks; axis max " + displayMax);
        drawVolatilityBands(sb, changedReports, left, top, plotW, plotH, displayMax);
        drawAxes(sb, left, top, plotW, plotH, axisMaxPercent, percentStep);
        for (int i = 0; i < percentages.length; i++) {
            double x = left + i * bw + 1.0;
            double h = plotH * percentages[i] / axisMaxPercent;
            double y = top + plotH - h;
            sb.append("<rect x=\"").append(fmt(x)).append("\" y=\"").append(fmt(y)).append("\" width=\"")
                    .append(fmt(Math.max(0.20, bw * 0.85))).append("\" height=\"").append(fmt(h))
                    .append("\" fill=\"#2f6f9f\" opacity=\"0.62\"/>\n");
        }
        drawPercentileMarkers(sb, changedReports, left, top, plotW, plotH, displayMax);
        drawSwingTicks(sb, left, top, plotW, plotH, displayMax);
        drawLegend(sb, width - 305, 30);
        sb.append("</svg>\n");
        return sb.toString();
    }

    /**
     * Writes the root-stack swing distribution as SVG.
     * @param path file path
     * @param svgText svg text value
     * @throws java.io.IOException if IOException is raised by the underlying operation
     */
    private static void writeSvg(Path path, String svgText) throws IOException {
        createParent(path);
        Files.writeString(path, svgText, StandardCharsets.UTF_8);
    }

    /**
     * Writes the depth-volatility report as a native A4 portrait PDF.
     * @param path file path
     * @param reports report data rows
     * @throws java.io.IOException if IOException is raised by the underlying operation
     */
    private static void writePdf(Path path, List<RootReport> reports) throws IOException {
        createParent(path);
        Document pdf = new Document()
                .setTitle("Puzzle Volatility Report")
                .setAuthor("ChessRTK")
                .setSubject("Puzzle stack depth-volatility report")
                .setCreator("testing.PuzzleVolatilityReport")
                .setProducer("chess-rtk native PDF");
        Page page = pdf.addPage(PageSize.A4);
        drawPdfReportPage(page, reports);
        Page evidencePage = pdf.addPage(PageSize.A4);
        drawPdfEvidencePage(evidencePage, reports);
        pdf.write(path);
    }

    /**
     * Draws the single-page volatility report using the same visual system as the
     * puzzle difficulty PDF.
     * @param page page index
     * @param reports report data rows
     */
    private static void drawPdfReportPage(Page page, List<RootReport> reports) {
        Canvas canvas = page.canvas();
        double width = page.getWidth() - REPORT_MARGIN * 2.0;
        ReportStats stats = reportStats(reports);
        List<RootReport> changed = changedReports(reports);

        drawReportHeader(canvas, page.getWidth(), width, REPORT_TOP_MARGIN);
        double y = REPORT_TOP_MARGIN + 50.0;
        drawMetricCards(canvas, REPORT_MARGIN, y, width, stats);
        y += 57.0;
        drawReportChart(canvas, changed, stats, REPORT_MARGIN, y, width, 216.0);
        y += 226.0;
        drawInterpretationPanel(canvas, REPORT_MARGIN, y, width, stats);
        y += 76.0;

        double gap = 10.0;
        double snapshotWidth = 178.0;
        double driversWidth = width - gap - snapshotWidth;
        double analysisHeight = 204.0;
        drawSwitchDriversGraph(canvas, changed, REPORT_MARGIN, y, driversWidth, analysisHeight);
        drawLargestSwitchPanel(canvas, changed.isEmpty() ? null : changed.get(0),
                REPORT_MARGIN + driversWidth + gap, y, snapshotWidth, analysisHeight);
        y += analysisHeight + 14.0;

        double leftWidth = 346.0;
        double rightWidth = width - leftWidth - gap;
        drawSectionWithTable(canvas, REPORT_MARGIN, y, leftWidth,
                "Top Reversals",
                new String[] { "Rank", "ID", "Dir", "Swing", "Depth", "Score", "Best", "PV" },
                topSwitchRows(changed, "reversal", 5),
                new double[] { 0.07, 0.12, 0.09, 0.12, 0.12, 0.18, 0.20, 0.10 },
                new boolean[] { true, false, false, true, false, false, false, true });
        drawSectionWithTable(canvas, REPORT_MARGIN + leftWidth + gap, y, rightWidth,
                "Top Sharp Swings",
                new String[] { "Rank", "ID", "Swing", "Depth", "Best" },
                topSwitchRows(changed, "sharp", 5),
                new double[] { 0.15, 0.22, 0.20, 0.20, 0.23 },
                new boolean[] { true, false, true, false, false });
        drawReportFooter(canvas, page.getHeight(), page.getWidth(), page.getHeight() - REPORT_BOTTOM_MARGIN - 12.0,
                "Page 1 of 2");
    }

    /**
     * Draws the evidence page with traceable depth-switch details.
     * @param page page index
     * @param reports report data rows
     */
    private static void drawPdfEvidencePage(Page page, List<RootReport> reports) {
        Canvas canvas = page.canvas();
        double width = page.getWidth() - REPORT_MARGIN * 2.0;
        List<RootReport> changed = changedReports(reports);

        drawEvidenceHeader(canvas, page.getWidth(), width, REPORT_TOP_MARGIN);
        double y = REPORT_TOP_MARGIN + 50.0;
        drawEvidenceSummaryPanel(canvas, REPORT_MARGIN, y, width, changed);
        y += 64.0;

        double timelineBottom = drawSwitchTimelineCards(canvas, REPORT_MARGIN, y, width, topEvidenceCases(changed, 3));
        y = timelineBottom + 16.0;

        double gap = 10.0;
        double leftWidth = (width - gap) / 2.0;
        double rightWidth = width - leftWidth - gap;
        double jumpsBottom = drawSectionWithTable(canvas, REPORT_MARGIN, y, leftWidth,
                "Largest Adjacent Depth Jumps",
                new String[] { "Rank", "ID", "Jump", "Depth", "Score", "Best" },
                topAdjacentJumpRows(changed, 7),
                new double[] { 0.13, 0.18, 0.15, 0.16, 0.23, 0.15 },
                new boolean[] { true, false, true, false, false, false });
        double pvBottom = drawSectionWithTable(canvas, REPORT_MARGIN + leftWidth + gap, y, rightWidth,
                "PV Rewrite Cases",
                new String[] { "Rank", "ID", "LCP", "PV", "Swing", "Best" },
                topPvRewriteRows(changed, 7),
                new double[] { 0.13, 0.18, 0.12, 0.12, 0.17, 0.28 },
                new boolean[] { true, false, true, true, true, false });
        y = Math.max(jumpsBottom, pvBottom) + 18.0;

        drawSectionWithTable(canvas, REPORT_MARGIN, y, width,
                "Research Triage",
                new String[] { "Rank", "ID", "Type", "Swing", "Jump", "Score", "Best", "Depth/PV" },
                researchTriageRows(changed, 9),
                new double[] { 0.06, 0.10, 0.10, 0.10, 0.10, 0.17, 0.18, 0.19 },
                new boolean[] { true, false, false, true, true, false, false, false });
        drawReportFooter(canvas, page.getHeight(), page.getWidth(), page.getHeight() - REPORT_BOTTOM_MARGIN - 12.0,
                "Page 2 of 2");
    }

    /**
     * Draws the evidence-page title block.
     * @param canvas SVG canvas builder
     * @param pageWidth page width in pixels
     * @param contentWidth available content width
     * @param top top coordinate
     */
    private static void drawEvidenceHeader(Canvas canvas, double pageWidth, double contentWidth, double top) {
        canvas.drawText(REPORT_MARGIN, top, REPORT_DISPLAY_FONT, 16.6, REPORT_TEXT,
                "Puzzle Volatility Report");
        canvas.drawText(REPORT_MARGIN, top + 18.0, REPORT_BODY_FONT, 8.4, REPORT_MUTED,
                "Switch evidence: depth timelines, jumps, PV rewrites");
        drawRight(canvas, pageWidth - REPORT_MARGIN, top + 5.0, REPORT_BODY_FONT, 7.8, REPORT_TEXT,
                "Created " + createdStamp());
        canvas.line(REPORT_MARGIN, top + 42.0, REPORT_MARGIN + contentWidth, top + 42.0, REPORT_ACCENT, 0.75);
    }

    /**
     * Draws a concise scope note for the evidence page.
     * @param canvas SVG canvas builder
     * @param x x coordinate
     * @param y y coordinate
     * @param width width in pixels
     * @param changed true when the value changed
     */
    private static void drawEvidenceSummaryPanel(Canvas canvas, double x, double y, double width,
            List<RootReport> changed) {
        double height = 50.0;
        canvas.fillRect(x, y, width, height, REPORT_PANEL);
        canvas.strokeRect(x, y, width, height, REPORT_SOFT_RULE, 0.45);
        canvas.drawText(x + 8.0, y + 7.0, REPORT_SECTION_FONT, 8.3, REPORT_TEXT, "Evidence Scope");
        canvas.drawText(x + width * 0.36, y + 7.0, REPORT_SECTION_FONT, 8.3, REPORT_TEXT, "Research Use");
        drawBullet(canvas, x + 9.0, y + 21.0, width * 0.34,
                "Rows are the worst changed position per stack unless the table explicitly shows adjacent jumps.");
        drawBullet(canvas, x + width * 0.36 + 9.0, y + 21.0, width * 0.31,
                "Start/final score, best move, outcome and PV show whether the engine changed its conclusion.");
        drawBullet(canvas, x + width * 0.69 + 9.0, y + 21.0, width * 0.28,
                num(changed.size()) + " changed stacks are traceable by FEN hash ID in the CSV exports.");
    }

    /**
     * Draws top switch depth timelines.
     * @param canvas SVG canvas builder
     * @param x x coordinate
     * @param y y coordinate
     * @param width width in pixels
     * @param cases cases value
     * @return draw switch timeline cards result
     */
    private static double drawSwitchTimelineCards(Canvas canvas, double x, double y, double width,
            List<RootReport> cases) {
        drawSectionTitle(canvas, x, y, width, "Critical Switch Timelines");
        if (cases.isEmpty()) {
            canvas.drawWrappedText(x, y + 24.0, width, REPORT_BODY_FONT, 6.2, 7.2, REPORT_MUTED,
                    "No changed puzzle stacks were found.");
            return y + 48.0;
        }
        double cardHeight = 58.0;
        double gap = 7.0;
        double cursor = y + 19.0;
        for (int i = 0; i < cases.size(); i++) {
            drawSwitchTimelineCard(canvas, cases.get(i), i + 1, x, cursor, width, cardHeight);
            cursor += cardHeight + gap;
        }
        return cursor - gap;
    }

    /**
     * Draws one switch-timeline card.
     * @param canvas SVG canvas builder
     * @param report report data
     * @param rank rank value
     * @param x x coordinate
     * @param y y coordinate
     * @param width width in pixels
     * @param height height in pixels
     */
    private static void drawSwitchTimelineCard(Canvas canvas, RootReport report, int rank, double x, double y,
            double width, double height) {
        NodeReport node = worstNode(report);
        Volatility v = node.volatility();
        canvas.fillRect(x, y, width, height, REPORT_PANEL);
        canvas.strokeRect(x, y, width, height, REPORT_SOFT_RULE, 0.45);
        canvas.fillRect(x, y, 4.0, height, "reversal".equals(v.severity()) ? new Color(211, 129, 154) : REPORT_BAR);

        double leftWidth = 128.0;
        canvas.drawText(x + 8.0, y + 7.0, REPORT_DATA_BOLD_FONT, 5.8, REPORT_ACCENT,
                "RANK " + rank + " / ID " + fenId(node.fen()));
        canvas.drawWrappedText(x + 8.0, y + 18.0, leftWidth - 16.0, REPORT_BODY_FONT, 5.5, 6.4, REPORT_TEXT,
                directionShort(v.direction()) + "; swing " + v.swing()
                        + "; d" + v.startDepth() + "->" + v.finalDepth()
                        + "; " + signed(v.startScore()) + "->" + signed(v.finalScore()));

        double sparkX = x + leftWidth + 8.0;
        double sparkY = y + 15.0;
        double sparkWidth = 166.0;
        double sparkHeight = 31.0;
        drawScoreSparkline(canvas, v.timeline(), sparkX, sparkY, sparkWidth, sparkHeight);

        double textX = sparkX + sparkWidth + 14.0;
        double textWidth = width - (textX - x) - 8.0;
        canvas.drawWrappedText(textX, y + 8.0, textWidth, REPORT_BODY_FONT, 5.4, 6.3, REPORT_TEXT,
                "Best " + movePath(v.startBestMove(), v.finalBestMove())
                        + "; second " + emptyDash(v.finalSecondBestMove())
                        + "; margin " + (v.finalMargin() == null ? "-" : v.finalMargin() + " Elo") + ".");
        canvas.drawWrappedText(textX, y + 27.0, textWidth, REPORT_BODY_FONT, 5.2, 6.1, REPORT_MUTED,
                "PV " + limitPv(v.startPv(), 4) + " -> " + limitPv(v.finalPv(), 7));
    }

    /**
     * Draws a compact score-vs-depth sparkline.
     * @param canvas SVG canvas builder
     * @param timeline timeline values
     * @param x x coordinate
     * @param y y coordinate
     * @param width width in pixels
     * @param height height in pixels
     */
    private static void drawScoreSparkline(Canvas canvas, List<TimelineEntry> timeline, double x, double y,
            double width, double height) {
        canvas.fillRect(x, y, width, height, Color.WHITE);
        canvas.strokeRect(x, y, width, height, REPORT_TABLE_RULE, 0.28);
        if (timeline.isEmpty()) {
            return;
        }
        int minDepth = timeline.get(0).depth();
        int maxDepth = timeline.get(timeline.size() - 1).depth();
        int minScore = timeline.stream().mapToInt(TimelineEntry::score).min().orElse(0);
        int maxScore = timeline.stream().mapToInt(TimelineEntry::score).max().orElse(0);
        int scorePad = Math.max(80, (maxScore - minScore) / 10);
        minScore -= scorePad;
        maxScore += scorePad;
        if (minScore < 0 && maxScore > 0) {
            double zeroY = scoreY(0, minScore, maxScore, y, height);
            canvas.line(x, zeroY, x + width, zeroY, REPORT_SOFT_RULE, 0.25);
        }
        double previousX = 0.0;
        double previousY = 0.0;
        for (int i = 0; i < timeline.size(); i++) {
            TimelineEntry entry = timeline.get(i);
            double px = depthX(entry.depth(), minDepth, maxDepth, x, width);
            double py = scoreY(entry.score(), minScore, maxScore, y, height);
            if (i > 0) {
                canvas.line(previousX, previousY, px, py, REPORT_TREND, 0.65);
            }
            Color point = i == timeline.size() - 1 ? new Color(211, 129, 154) : REPORT_ACCENT;
            canvas.fillRect(px - 1.0, py - 1.0, 2.0, 2.0, point);
            previousX = px;
            previousY = py;
        }
        TimelineEntry first = timeline.get(0);
        TimelineEntry last = timeline.get(timeline.size() - 1);
        canvas.drawText(x, y + height + 4.0, REPORT_DATA_FONT, 3.8, REPORT_MUTED,
                "d" + first.depth() + " " + signed(first.score()));
        drawRight(canvas, x + width, y + height + 4.0, REPORT_DATA_FONT, 3.8, REPORT_MUTED,
                "d" + last.depth() + " " + signed(last.score()));
    }

    /**
     * Converts search depth to sparkline x-coordinate.
     * @param depth search depth
     * @param minDepth min depth value
     * @param maxDepth max depth value
     * @param x x coordinate
     * @param width width in pixels
     * @return depth x result
     */
    private static double depthX(int depth, int minDepth, int maxDepth, double x, double width) {
        if (maxDepth <= minDepth) {
            return x + width / 2.0;
        }
        return x + width * (depth - minDepth) / (double) (maxDepth - minDepth);
    }

    /**
     * Converts score to sparkline y-coordinate.
     * @param score score value
     * @param minScore min score value
     * @param maxScore max score value
     * @param y y coordinate
     * @param height height in pixels
     * @return score y result
     */
    private static double scoreY(int score, int minScore, int maxScore, double y, double height) {
        if (maxScore <= minScore) {
            return y + height / 2.0;
        }
        return y + height - height * (score - minScore) / (double) (maxScore - minScore);
    }

    /**
     * Draws the title block and source metadata.
     * @param canvas SVG canvas builder
     * @param pageWidth page width in pixels
     * @param contentWidth available content width
     * @param top top coordinate
     */
    private static void drawReportHeader(Canvas canvas, double pageWidth, double contentWidth, double top) {
        canvas.drawText(REPORT_MARGIN, top, REPORT_DISPLAY_FONT, 16.6, REPORT_TEXT,
                "Puzzle Volatility Report");
        canvas.drawText(REPORT_MARGIN, top + 18.0, REPORT_BODY_FONT, 8.4, REPORT_MUTED,
                "Depth score switches, PV rewrites, and reversal stacks");
        drawRight(canvas, pageWidth - REPORT_MARGIN, top + 5.0, REPORT_BODY_FONT, 7.8, REPORT_TEXT,
                "Created " + createdStamp());
        canvas.line(REPORT_MARGIN, top + 42.0, REPORT_MARGIN + contentWidth, top + 42.0, REPORT_ACCENT, 0.75);
    }

    /**
     * Draws the report footer.
     * @param canvas SVG canvas builder
     * @param pageHeight page height value
     * @param pageWidth page width in pixels
     * @param y y coordinate
     * @param pageLabel page label value
     */
    private static void drawReportFooter(Canvas canvas, double pageHeight, double pageWidth, double y,
            String pageLabel) {
        if (y > pageHeight - REPORT_BOTTOM_MARGIN - 4.0) {
            return;
        }
        canvas.line(REPORT_MARGIN, y - 5.0, pageWidth - REPORT_MARGIN, y - 5.0, REPORT_SOFT_RULE, 0.35);
        canvas.drawText(REPORT_MARGIN, y, REPORT_BODY_FONT, 5.1, REPORT_MUTED,
                "Score swings use WDL-derived Elo-like values when present; raw eval and WDL remain in the timeline CSV.");
        if (pageLabel != null && !pageLabel.isBlank()) {
            drawRight(canvas, pageWidth - REPORT_MARGIN, y, REPORT_BODY_FONT, 5.1, REPORT_MUTED, pageLabel);
        }
    }

    /**
     * Draws headline metric cards.
     * @param canvas SVG canvas builder
     * @param x x coordinate
     * @param y y coordinate
     * @param width width in pixels
     * @param stats statistics data
     */
    private static void drawMetricCards(Canvas canvas, double x, double y, double width, ReportStats stats) {
        double gap = 8.0;
        double cardWidth = (width - gap * 3.0) / 4.0;
        drawMetricCard(canvas, x, y, cardWidth, "Corpus", num(stats.rootStacks()), "verified puzzle stacks scanned");
        drawMetricCard(canvas, x + cardWidth + gap, y, cardWidth, "Changed", num(stats.changedStacks()),
                pct(stats.changedStacks(), stats.rootStacks()) + " of stacks");
        drawMetricCard(canvas, x + (cardWidth + gap) * 2.0, y, cardWidth, "Reversals",
                num(stats.reversalStacks()), "losing/winning outcome flips");
        drawMetricCard(canvas, x + (cardWidth + gap) * 3.0, y, cardWidth, "What Changed",
                pctLabel(stats.bestChangedShare()), "best move changed; outcome " + pctLabel(stats.outcomeChangedShare())
                        + "; PV prefix " + oneDecimal(stats.medianPvPrefix()));
    }

    /**
     * Draws one headline metric card.
     * @param canvas SVG canvas builder
     * @param x x coordinate
     * @param y y coordinate
     * @param width width in pixels
     * @param label label text
     * @param value value to use
     * @param note note value
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
     * Draws the swing distribution chart directly in the PDF.
     * @param canvas SVG canvas builder
     * @param changed true when the value changed
     * @param stats statistics data
     * @param x x coordinate
     * @param y y coordinate
     * @param width width in pixels
     * @param height height in pixels
     */
    private static void drawReportChart(Canvas canvas, List<RootReport> changed, ReportStats stats, double x, double y,
            double width, double height) {
        SwingAxis axis = reportSwingAxis(changed, stats);
        int[] bins = bins(changed, axis);
        double[] percentages = percentages(bins, changed.size());
        double yMax = reportYMax(percentages);
        double plotLeft = x + 29.0;
        double plotTop = y + 48.0;
        double plotWidth = width - 45.0;
        double plotHeight = height - 81.0;

        canvas.drawText(plotLeft, y, REPORT_SECTION_FONT, 10.8, REPORT_TEXT,
                "Depth Swing Distribution");
        String overflow = axis.highOutliers() == 0 ? ""
                : "; " + axis.highOutliers() + " above axis, max " + stats.maxSwing();
        canvas.drawText(plotLeft, y + 16.0, REPORT_BODY_FONT, 5.0, REPORT_TEXT,
                num(changed.size()) + " changed stacks; " + BIN_WIDTH
                        + "-Elo bars over " + axis.min() + "-" + axis.max()
                        + "; p10/median/p90/p99 = "
                        + stats.p10Swing() + " / " + stats.medianSwing()
                        + " / " + stats.p90Swing() + " / " + stats.p99Swing() + overflow);

        drawReportSwingBands(canvas, changed, plotLeft, plotTop, plotWidth, plotHeight, axis);
        drawReportGrid(canvas, plotLeft, plotTop, plotWidth, plotHeight, yMax);
        drawReportHistogram(canvas, percentages, plotLeft, plotTop, plotWidth, plotHeight, yMax);
        drawReportReversalBars(canvas, changed, axis, plotLeft, plotTop, plotWidth, plotHeight, yMax);
        drawReportTrendLine(canvas, movingAverage(percentages, 7), plotLeft, plotTop, plotWidth, plotHeight, yMax);
        drawReportDistributionLegend(canvas, plotLeft + plotWidth - 78.0, y + 28.0);
        drawReportAxes(canvas, plotLeft, plotTop, plotWidth, plotHeight, axis);
        drawReportQuantiles(canvas, changed, plotLeft, plotTop, plotWidth, plotHeight, axis);
    }

    /**
     * Draws volatility background bands.
     * @param canvas SVG canvas builder
     * @param changed true when the value changed
     * @param left left coordinate
     * @param top top coordinate
     * @param plotWidth plot width in pixels
     * @param plotHeight plot height in pixels
     * @param axis axis label
     */
    private static void drawReportSwingBands(Canvas canvas, List<RootReport> changed, double left, double top,
            double plotWidth, double plotHeight, SwingAxis axis) {
        int[] starts = { 0, 150, VOLATILE_SWING, SHARP_SWING, EXTREME_SWING };
        int[] ends = { 149, VOLATILE_SWING - 1, SHARP_SWING - 1, EXTREME_SWING - 1, axis.max() };
        String[] labels = { "Stable", "Noisy", "Volatile", "Sharp", "Extreme" };
        Color[] fills = {
                new Color(244, 252, 248), new Color(244, 251, 255), new Color(255, 251, 238),
                new Color(255, 245, 238), new Color(255, 242, 245)
        };
        Color[] text = {
                new Color(30, 107, 88), new Color(30, 102, 140), new Color(138, 90, 5),
                new Color(148, 66, 19), new Color(152, 33, 61)
        };
        for (int i = 0; i < starts.length; i++) {
            if (starts[i] > axis.max() || ends[i] < axis.min()) {
                continue;
            }
            double x1 = swingX(Math.max(starts[i], axis.min()), left, plotWidth, axis);
            double x2 = swingX(Math.min(ends[i], axis.max()) + 1, left, plotWidth, axis);
            double bandWidth = Math.max(0.0, x2 - x1);
            canvas.fillRect(x1, top, bandWidth, plotHeight, fills[i]);
            double share = percentInSwingRange(changed, starts[i], ends[i]);
            if (bandWidth >= 34.0 && share > 0.05) {
                canvas.drawText(x1 + 2.5, top + 4.0, REPORT_DATA_BOLD_FONT, 4.7, text[i], labels[i]);
                canvas.drawText(x1 + 2.5, top + 12.0, REPORT_DATA_FONT, 4.5, REPORT_MUTED,
                        pctLabel(share));
            }
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
            double yy = top + plotHeight - plotHeight * value / yMax;
            canvas.line(left, yy, left + plotWidth, yy, REPORT_GRID, 0.35);
            drawRight(canvas, left - 4.0, yy - 2.6, REPORT_DATA_FONT, 4.2, REPORT_MUTED, formatPercent(value));
        }
    }

    /**
     * Draws the PDF histogram bars.
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
     * Overlays reversal stacks as a second color on the swing chart.
     * @param canvas SVG canvas builder
     * @param changed true when the value changed
     * @param axis axis label
     * @param left left coordinate
     * @param top top coordinate
     * @param plotWidth plot width in pixels
     * @param plotHeight plot height in pixels
     * @param yMax maximum y value
     */
    private static void drawReportReversalBars(Canvas canvas, List<RootReport> changed, SwingAxis axis, double left,
            double top, double plotWidth, double plotHeight, double yMax) {
        int[] bins = bins(reversalReports(changed), axis);
        double[] percentages = percentages(bins, changed.size());
        double bw = plotWidth / percentages.length;
        double barWidth = Math.max(0.35, bw * 0.48);
        Color reversal = new Color(211, 129, 154);
        for (int i = 0; i < percentages.length; i++) {
            if (percentages[i] <= 0.0) {
                continue;
            }
            double h = plotHeight * percentages[i] / yMax;
            double x = left + i * bw + (bw - barWidth) / 2.0;
            double y = top + plotHeight - h;
            canvas.fillRect(x, y, barWidth, h, reversal);
        }
    }

    /**
     * Draws the smoothed distribution trend line.
     * @param canvas SVG canvas builder
     * @param smoothed smoothed value
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
        for (int i = 1; i < smoothed.length; i++) {
            double x = left + (i + 0.5) * bw;
            double yy = top + plotHeight - plotHeight * smoothed[i] / yMax;
            canvas.line(prevX, prevY, x, yy, REPORT_TREND, 0.7);
            prevX = x;
            prevY = yy;
        }
    }

    /**
     * Draws the distribution chart legend.
     * @param canvas SVG canvas builder
     * @param x x coordinate
     * @param y y coordinate
     */
    private static void drawReportDistributionLegend(Canvas canvas, double x, double y) {
        canvas.fillRect(x, y + 1.0, 5.0, 4.0, REPORT_BAR);
        canvas.drawText(x + 7.0, y, REPORT_DATA_FONT, 4.3, REPORT_MUTED, "all");
        canvas.fillRect(x + 25.0, y + 1.0, 5.0, 4.0, new Color(211, 129, 154));
        canvas.drawText(x + 32.0, y, REPORT_DATA_FONT, 4.3, REPORT_MUTED, "reversal");
        canvas.line(x + 65.0, y + 3.0, x + 77.0, y + 3.0, REPORT_TREND, 0.7);
        canvas.drawText(x + 80.0, y, REPORT_DATA_FONT, 4.3, REPORT_MUTED, "trend");
    }

    /**
     * Draws chart axes and x labels.
     * @param canvas SVG canvas builder
     * @param left left coordinate
     * @param top top coordinate
     * @param plotWidth plot width in pixels
     * @param plotHeight plot height in pixels
     * @param axis axis label
     */
    private static void drawReportAxes(Canvas canvas, double left, double top, double plotWidth, double plotHeight,
            SwingAxis axis) {
        canvas.line(left, top + plotHeight, left + plotWidth, top + plotHeight, REPORT_RULE, 0.6);
        canvas.line(left, top, left, top + plotHeight, REPORT_RULE, 0.6);
        int step = swingTickStep(axis.max() - axis.min());
        for (int swing = axis.min(); swing <= axis.max(); swing += step) {
            double x = swingX(swing, left, plotWidth, axis);
            canvas.line(x, top + plotHeight, x, top + plotHeight + 2.5, REPORT_RULE, 0.35);
            drawCentered(canvas, x, top + plotHeight + 7.0, REPORT_DATA_FONT, 4.0, REPORT_MUTED,
                    Integer.toString(swing));
        }
        drawCentered(canvas, left + plotWidth / 2.0, top + plotHeight + 18.0, REPORT_DATA_FONT, 4.4,
                REPORT_TEXT, "Depth swing (Elo-like score)");
        canvas.drawTextRotated(left - 24.0, top + plotHeight / 2.0 + 26.0, -90.0, left - 24.0,
                top + plotHeight / 2.0 + 26.0, REPORT_DATA_FONT, 4.4, REPORT_TEXT,
                "% of changed stacks per 25 Elo");
    }

    /**
     * Draws quantile markers.
     * @param canvas SVG canvas builder
     * @param changed true when the value changed
     * @param left left coordinate
     * @param top top coordinate
     * @param plotWidth plot width in pixels
     * @param plotHeight plot height in pixels
     * @param axis axis label
     */
    private static void drawReportQuantiles(Canvas canvas, List<RootReport> changed, double left, double top,
            double plotWidth, double plotHeight, SwingAxis axis) {
        double[] qs = { 0.10, 0.50, 0.90, 0.99 };
        String[] names = { "p10", "median", "p90", "p99" };
        for (int i = 0; i < qs.length; i++) {
            int swing = percentileSwing(changed, qs[i]);
            if (swing < axis.min() || swing > axis.max()) {
                continue;
            }
            double x = swingX(swing, left, plotWidth, axis);
            canvas.line(x, top - 4.0, x, top + plotHeight, REPORT_QUANTILE_LINE, 0.22);
            drawCentered(canvas, x, top - 9.0, REPORT_DATA_FONT, 4.0, REPORT_QUANTILE_LABEL,
                    names[i] + " " + swing);
        }
    }

    /**
     * Draws concise interpretation and method notes.
     * @param canvas SVG canvas builder
     * @param x x coordinate
     * @param y y coordinate
     * @param width width in pixels
     * @param stats statistics data
     */
    private static void drawInterpretationPanel(Canvas canvas, double x, double y, double width, ReportStats stats) {
        double height = 64.0;
        double gap = 14.0;
        double column = (width - gap) / 2.0;
        canvas.fillRect(x, y, width, height, REPORT_PANEL);
        canvas.strokeRect(x, y, width, height, REPORT_SOFT_RULE, 0.45);
        canvas.drawText(x + 8.0, y + 7.0, REPORT_SECTION_FONT, 8.3, REPORT_TEXT, "Reading the Switches");
        canvas.drawText(x + column + gap + 8.0, y + 7.0, REPORT_SECTION_FONT, 8.3, REPORT_TEXT,
                "Export Notes");
        drawBullet(canvas, x + 9.0, y + 21.0, column - 14.0,
                num(stats.changedStacks()) + " of " + num(stats.rootStacks()) + " stacks changed; p10/median/p90 = "
                        + stats.p10Swing() + " / " + stats.medianSwing() + " / " + stats.p90Swing() + ".");
        drawBullet(canvas, x + 9.0, y + 38.0, column - 14.0,
                num(stats.reversalStacks()) + " reversal stacks; the largest switch moved "
                        + stats.maxSwing() + " Elo-like points across depth.");
        drawBullet(canvas, x + column + gap + 9.0, y + 21.0, column - 14.0,
                "Score source: " + stats.scoreSources() + "; final depth median "
                        + stats.medianFinalDepth() + ", range "
                        + stats.minFinalDepth() + "-" + stats.maxFinalDepth() + ".");
        drawBullet(canvas, x + column + gap + 9.0, y + 38.0, column - 14.0,
                "CSVs list changed stacks and positions; row IDs are FEN hashes; timeline keeps raw eval, WDL, nodes/time, PV, and FEN.");
    }

    /**
     * Draws one wrapped bullet.
     * @param canvas SVG canvas builder
     * @param x x coordinate
     * @param y y coordinate
     * @param width width in pixels
     * @param text text value
     */
    private static void drawBullet(Canvas canvas, double x, double y, double width, String text) {
        canvas.fillRect(x, y + 3.0, 2.0, 2.0, REPORT_ACCENT);
        canvas.drawWrappedText(x + 7.0, y, width - 7.0, REPORT_BODY_FONT, 6.2, 7.0, REPORT_MUTED, text);
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
     * @return draw section with table result
     */
    private static double drawSectionWithTable(Canvas canvas, double x, double y, double width, String title,
            String[] headers, String[][] rows, double[] columns, boolean[] rightAligned) {
        drawSectionTitle(canvas, x, y, width, title);
        return drawTable(canvas, x, y + 16.0, width, headers, rows, columns, rightAligned);
    }

    /**
     * Draws the compact section title used above dense evidence tables.
     * @param canvas SVG canvas builder
     * @param x x coordinate
     * @param y y coordinate
     * @param width width in pixels
     * @param title title text
     */
    private static void drawSectionTitle(Canvas canvas, double x, double y, double width, String title) {
        canvas.fillRect(x, y + 1.1, 2.0, 8.0, REPORT_ACCENT);
        canvas.drawText(x + 5.5, y + 0.2, REPORT_SECTION_FONT, 8.4, REPORT_TEXT, title);
        canvas.line(x, y + 12.3, x + width, y + 12.3, REPORT_TABLE_RULE, 0.35);
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
     * @return draw table result
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
            drawRow(canvas, x, rowTop + 2.6, width, rows[i], columns, rightAligned, REPORT_DATA_FONT, 5.95,
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
     * @param row row value
     * @param columns table columns
     * @param rightAligned true to right-align the value
     * @param font font name
     * @param size size value
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
     * @param size size value
     * @param color display color
     * @param text text value
     * @param rightAligned true to right-align the value
     */
    private static void drawCell(Canvas canvas, double x, double y, double width, Font font, double size, Color color,
            String text, boolean rightAligned) {
        double pad = 3.0;
        String safe = fitText(text, font, size, width - pad * 2.0);
        if (rightAligned) {
            drawRight(canvas, x + width - pad, y, font, size, color, safe);
        } else {
            canvas.drawText(x + pad, y, font, size, color, safe);
        }
    }

    /**
     * Draws the largest-switch detail panel.
     * @param canvas SVG canvas builder
     * @param report report data
     * @param x x coordinate
     * @param y y coordinate
     * @param width width in pixels
     * @param height height in pixels
     */
    private static void drawLargestSwitchPanel(Canvas canvas, RootReport report, double x, double y, double width,
            double height) {
        height = Math.max(128.0, height);
        canvas.fillRect(x, y, width, height, REPORT_PANEL);
        canvas.strokeRect(x, y, width, height, REPORT_SOFT_RULE, 0.45);
        canvas.drawText(x + 8.0, y + 7.0, REPORT_SECTION_FONT, 9.0, REPORT_TEXT,
                "Largest Switch Snapshot");
        if (report == null) {
            canvas.drawWrappedText(x + 8.0, y + 24.0, width - 16.0, REPORT_BODY_FONT, 5.8, 6.8,
                    REPORT_MUTED, "No changing stacks were found.");
            return;
        }
        NodeReport node = worstNode(report);
        Volatility v = node.volatility();
        double cursor = y + 23.0;
        canvas.drawText(x + 8.0, cursor, REPORT_DATA_BOLD_FONT, 5.8, REPORT_ACCENT,
                directionShort(v.direction()) + "  SWING " + v.swing());
        cursor += 10.0;
        canvas.drawWrappedText(x + 8.0, cursor, width - 16.0, REPORT_BODY_FONT, 5.6, 6.5, REPORT_TEXT,
                "Score " + signed(v.startScore()) + " -> " + signed(v.finalScore())
                        + "; depth " + v.startDepth() + " -> " + v.finalDepth()
                        + "; best " + emptyDash(v.startBestMove()) + " -> " + emptyDash(v.finalBestMove()) + ".");
        cursor += 17.0;
        canvas.drawText(x + 8.0, cursor, REPORT_DATA_BOLD_FONT, 5.5, REPORT_ACCENT, "START PV");
        cursor += canvas.drawWrappedText(x + 48.0, cursor, width - 56.0, REPORT_BODY_FONT, 5.6, 6.6,
                REPORT_TEXT, limitPv(v.startPv(), 10));
        cursor += 4.0;
        canvas.drawText(x + 8.0, cursor, REPORT_DATA_BOLD_FONT, 5.5, REPORT_ACCENT, "FINAL PV");
        cursor += canvas.drawWrappedText(x + 48.0, cursor, width - 56.0, REPORT_BODY_FONT, 5.6, 6.6,
                REPORT_TEXT, limitPv(v.finalPv(), 14));
        cursor += 5.0;
        canvas.drawWrappedText(x + 8.0, cursor, width - 16.0, REPORT_BODY_FONT, 5.1, 5.9, REPORT_MUTED,
                "Rank 1 / ID " + fenId(node.fen()) + " in CSV. Second line " + emptyDash(v.finalSecondBestMove())
                        + "; margin " + (v.finalMargin() == null ? "-" : v.finalMargin() + " Elo")
                        + "; PV rewrites " + v.pvChanges() + ".");
    }

    /**
     * Draws a compact driver panel for changed-stack groups.
     * @param canvas SVG canvas builder
     * @param changed true when the value changed
     * @param x x coordinate
     * @param y y coordinate
     * @param width width in pixels
     * @param height height in pixels
     */
    private static void drawSwitchDriversGraph(Canvas canvas, List<RootReport> changed, double x, double y,
            double width, double height) {
        SwitchGroup[] groups = switchGroups(changed);
        String[] metricNames = { "Median swing", "PV prefix", "Best changed", "Outcome changed" };
        double[][] values = {
                metricValues(groups, SwitchGroup::medianSwing),
                metricValues(groups, SwitchGroup::avgFirstToFinalLcp),
                metricValues(groups, SwitchGroup::bestChangedShare),
                metricValues(groups, SwitchGroup::outcomeChangedShare)
        };
        Color[] colors = { REPORT_BAR, new Color(211, 129, 154) };

        canvas.fillRect(x, y, width, height, REPORT_PANEL);
        canvas.strokeRect(x, y, width, height, REPORT_SOFT_RULE, 0.45);
        canvas.drawText(x + 8.0, y + 7.0, REPORT_SECTION_FONT, 9.0, REPORT_TEXT,
                "Switch Drivers by Severity");
        canvas.drawWrappedText(x + 8.0, y + 21.0, width - 16.0, REPORT_BODY_FONT, 5.4, 6.3, REPORT_MUTED,
                "Normalized bars compare sharp and reversal stacks; labels show actual values.");

        double plotLeft = x + 84.0;
        double plotTop = y + 45.0;
        double plotWidth = width - 101.0;
        double plotHeight = Math.max(86.0, height - 88.0);
        double rowHeight = plotHeight / metricNames.length;
        for (int metric = 0; metric < metricNames.length; metric++) {
            drawSwitchMetricRow(canvas, groups, colors, metricNames[metric], values[metric],
                    x + 8.0, plotLeft, plotTop + metric * rowHeight, plotWidth, rowHeight, metric);
        }
        drawSwitchGroupLabels(canvas, groups, plotLeft, plotTop + plotHeight + 5.0, plotWidth);
        canvas.drawWrappedText(x + 8.0, y + height - 20.0, width - 16.0, REPORT_BODY_FONT, 5.2, 6.2,
                REPORT_MUTED, switchDriverSummary(groups));
    }

    /**
     * Draws one switch-driver metric row.
     * @param canvas SVG canvas builder
     * @param groups grouped values
     * @param colors colors value
     * @param metricName metric name value
     * @param values values to inspect
     * @param labelX label x value
     * @param plotLeft plot left coordinate
     * @param rowTop row top value
     * @param plotWidth plot width in pixels
     * @param rowHeight row height value
     * @param metricIndex metric index
     */
    private static void drawSwitchMetricRow(Canvas canvas, SwitchGroup[] groups, Color[] colors, String metricName,
            double[] values, double labelX, double plotLeft, double rowTop, double plotWidth, double rowHeight,
            int metricIndex) {
        double max = Math.max(0.000_001, max(values));
        double baseline = rowTop + rowHeight - 9.0;
        double barMax = Math.max(8.0, rowHeight - 19.0);
        double groupWidth = plotWidth / groups.length;
        double barWidth = groupWidth * 0.54;
        canvas.drawText(labelX, rowTop + 5.0, REPORT_DATA_BOLD_FONT, 5.8, REPORT_TEXT, metricName);
        canvas.line(plotLeft, baseline, plotLeft + plotWidth, baseline, REPORT_TABLE_RULE, 0.28);
        for (int i = 0; i < groups.length; i++) {
            double h = barMax * values[i] / max;
            double bx = plotLeft + i * groupWidth + (groupWidth - barWidth) / 2.0;
            double by = baseline - h;
            canvas.fillRect(bx, by, barWidth, h, colors[i]);
            canvas.strokeRect(bx, by, barWidth, h, new Color(255, 255, 255, 150), 0.15);
            drawCentered(canvas, bx + barWidth / 2.0, Math.max(rowTop + 2.0, by - 6.8), REPORT_DATA_FONT,
                    4.5, REPORT_TEXT, switchMetricValueLabel(metricIndex, values[i]));
        }
    }

    /**
     * Draws labels below the switch-driver panel.
     * @param canvas SVG canvas builder
     * @param groups grouped values
     * @param plotLeft plot left coordinate
     * @param y y coordinate
     * @param plotWidth plot width in pixels
     */
    private static void drawSwitchGroupLabels(Canvas canvas, SwitchGroup[] groups, double plotLeft, double y,
            double plotWidth) {
        double groupWidth = plotWidth / groups.length;
        int total = 0;
        for (SwitchGroup group : groups) {
            total += group.count();
        }
        for (int i = 0; i < groups.length; i++) {
            double center = plotLeft + i * groupWidth + groupWidth / 2.0;
            drawCentered(canvas, center, y, REPORT_DATA_BOLD_FONT, 4.4, REPORT_TEXT, groups[i].shortName());
            drawCentered(canvas, center, y + 7.0, REPORT_DATA_FONT, 4.1, REPORT_MUTED,
                    pct(groups[i].count(), total));
        }
    }

    /**
     * Builds switch severity groups.
     * @param changed true when the value changed
     * @return switch groups result
     */
    private static SwitchGroup[] switchGroups(List<RootReport> changed) {
        List<RootReport> sharp = changed.stream()
                .filter(report -> !"reversal".equals(report.stackSeverity()))
                .toList();
        List<RootReport> reversal = changed.stream()
                .filter(report -> "reversal".equals(report.stackSeverity()))
                .toList();
        return new SwitchGroup[] {
                switchGroup("Sharp", "Sharp", sharp),
                switchGroup("Reversal", "Reversal", reversal)
        };
    }

    /**
     * Builds one switch severity group.
     * @param name name value
     * @param shortName short name value
     * @param reports report data rows
     * @return switch group result
     */
    private static SwitchGroup switchGroup(String name, String shortName, List<RootReport> reports) {
        if (reports.isEmpty()) {
            return new SwitchGroup(name, shortName, 0, 0.0, 0.0, 0.0, 0.0);
        }
        return new SwitchGroup(
                name,
                shortName,
                reports.size(),
                percentileSwing(reports, 0.50),
                avgFirstToFinalLcp(reports),
                shareWithBestMoveChange(reports),
                shareWithOutcomeChange(reports));
    }

    /**
     * Returns average first-to-final PV common prefix for worst nodes.
     * @param reports report data rows
     * @return avg first to final lcp result
     */
    private static double avgFirstToFinalLcp(List<RootReport> reports) {
        if (reports.isEmpty()) {
            return 0.0;
        }
        double total = 0.0;
        for (RootReport report : reports) {
            total += worstNode(report).volatility().firstToFinalLcp();
        }
        return total / reports.size();
    }

    /**
     * Returns percent of worst nodes whose best move changed.
     * @param reports report data rows
     * @return share with best move change result
     */
    private static double shareWithBestMoveChange(List<RootReport> reports) {
        if (reports.isEmpty()) {
            return 0.0;
        }
        int changed = 0;
        for (RootReport report : reports) {
            if (worstNode(report).volatility().bestMoveChanges() > 0) {
                changed++;
            }
        }
        return changed * 100.0 / reports.size();
    }

    /**
     * Returns percent of worst nodes whose outcome bucket changed.
     * @param reports report data rows
     * @return share with outcome change result
     */
    private static double shareWithOutcomeChange(List<RootReport> reports) {
        if (reports.isEmpty()) {
            return 0.0;
        }
        int changed = 0;
        for (RootReport report : reports) {
            if (worstNode(report).volatility().outcomeChanges() > 0) {
                changed++;
            }
        }
        return changed * 100.0 / reports.size();
    }

    /**
     * Extracts one metric vector for switch groups.
     * @param groups grouped values
     * @param metric metric value
     * @return metric values result
     */
    private static double[] metricValues(SwitchGroup[] groups, java.util.function.ToDoubleFunction<SwitchGroup> metric) {
        double[] out = new double[groups.length];
        for (int i = 0; i < groups.length; i++) {
            out[i] = metric.applyAsDouble(groups[i]);
        }
        return out;
    }

    /**
     * Formats switch-driver metric values.
     * @param metricIndex metric index
     * @param value value to use
     * @return switch metric value label result
     */
    private static String switchMetricValueLabel(int metricIndex, double value) {
        if (metricIndex == 0) {
            return String.format(Locale.ROOT, "%.0f", value);
        }
        if (metricIndex == 2 || metricIndex == 3) {
            return pctLabel(value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    /**
     * Builds the switch-driver summary.
     * @param groups grouped values
     * @return switch driver summary result
     */
    private static String switchDriverSummary(SwitchGroup[] groups) {
        SwitchGroup sharp = groups[0];
        SwitchGroup reversal = groups[1];
        return "Reversal vs sharp: median swing " + switchMetricValueLabel(0, reversal.medianSwing())
                + " vs " + switchMetricValueLabel(0, sharp.medianSwing())
                + ", PV prefix " + switchMetricValueLabel(1, reversal.avgFirstToFinalLcp())
                + " vs " + switchMetricValueLabel(1, sharp.avgFirstToFinalLcp())
                + ", best changed " + switchMetricValueLabel(2, reversal.bestChangedShare())
                + " vs " + switchMetricValueLabel(2, sharp.bestChangedShare()) + ".";
    }

    /**
     * Builds top-switch table rows.
     * @param changed true when the value changed
     * @param kind kind value
     * @param limit limit value
     * @return top switch rows result
     */
    private static String[][] topSwitchRows(List<RootReport> changed, String kind, int limit) {
        List<String[]> rows = new ArrayList<>();
        for (int i = 0; i < changed.size() && rows.size() < limit; i++) {
            RootReport report = changed.get(i);
            if ("reversal".equals(kind) != "reversal".equals(report.stackSeverity())) {
                continue;
            }
            NodeReport node = worstNode(report);
            Volatility v = node.volatility();
            if ("reversal".equals(kind)) {
                rows.add(new String[] {
                    Integer.toString(i + 1),
                    fenId(node.fen()),
                    directionShort(v.direction()),
                    Integer.toString(v.swing()),
                    v.startDepth() + "->" + v.finalDepth(),
                    signed(v.startScore()) + "->" + signed(v.finalScore()),
                    emptyDash(v.startBestMove()) + "->" + emptyDash(v.finalBestMove()),
                    Integer.toString(v.pvChanges())
                });
            } else {
                rows.add(new String[] {
                    Integer.toString(i + 1),
                    fenId(node.fen()),
                    Integer.toString(v.swing()),
                    v.startDepth() + "->" + v.finalDepth(),
                    emptyDash(v.startBestMove()) + "->" + emptyDash(v.finalBestMove())
                });
            }
        }
        return rows.toArray(String[][]::new);
    }

    /**
     * Chooses the most important switches for page-two timelines.
     * @param changed true when the value changed
     * @param limit limit value
     * @return top evidence cases result
     */
    private static List<RootReport> topEvidenceCases(List<RootReport> changed, int limit) {
        List<RootReport> sorted = new ArrayList<>(changed);
        sorted.sort(Comparator
                .comparingInt((RootReport report) -> "reversal".equals(report.stackSeverity()) ? 0 : 1)
                .thenComparing(Comparator.comparingInt(RootReport::treeMaxSwing).reversed())
                .thenComparing(report -> report.root().fen()));
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }

    /**
     * Builds rows for the largest adjacent search-depth jumps.
     * @param changed true when the value changed
     * @param limit limit value
     * @return top adjacent jump rows result
     */
    private static String[][] topAdjacentJumpRows(List<RootReport> changed, int limit) {
        List<AdjacentJump> jumps = topAdjacentJumps(changed, limit);
        String[][] rows = new String[jumps.size()][];
        for (int i = 0; i < jumps.size(); i++) {
            AdjacentJump jump = jumps.get(i);
            rows[i] = new String[] {
                    Integer.toString(jump.rootRank()),
                    fenId(jump.node().fen()),
                    Integer.toString(jump.delta()),
                    jump.from().depth() + "->" + jump.to().depth(),
                    signed(jump.from().score()) + "->" + signed(jump.to().score()),
                    movePath(moveToString(jump.from().bestMove()), moveToString(jump.to().bestMove()))
            };
        }
        return rows;
    }

    /**
     * Builds rows for positions whose PV was most rewritten.
     * @param changed true when the value changed
     * @param limit limit value
     * @return top pv rewrite rows result
     */
    private static String[][] topPvRewriteRows(List<RootReport> changed, int limit) {
        List<RankedNode> nodes = changedRankedNodes(changed);
        nodes.sort(Comparator
                .comparingInt((RankedNode item) -> item.node().volatility().firstToFinalLcp())
                .thenComparing(Comparator.comparingInt((RankedNode item) -> item.node().volatility().pvChanges()).reversed())
                .thenComparing(Comparator.comparingInt((RankedNode item) -> item.node().volatility().swing()).reversed())
                .thenComparingInt(RankedNode::rootRank));
        int size = Math.min(limit, nodes.size());
        String[][] rows = new String[size][];
        for (int i = 0; i < size; i++) {
            RankedNode item = nodes.get(i);
            Volatility v = item.node().volatility();
            rows[i] = new String[] {
                    Integer.toString(item.rootRank()),
                    fenId(item.node().fen()),
                    Integer.toString(v.firstToFinalLcp()),
                    Integer.toString(v.pvChanges()),
                    Integer.toString(v.swing()),
                    movePath(v.startBestMove(), v.finalBestMove())
            };
        }
        return rows;
    }

    /**
     * Builds the final triage list combining reversal, jump and PV information.
     * @param changed true when the value changed
     * @param limit limit value
     * @return research triage rows result
     */
    private static String[][] researchTriageRows(List<RootReport> changed, int limit) {
        List<RankedNode> nodes = changedRankedNodes(changed);
        nodes.sort(Comparator
                .comparingInt((RankedNode item) -> severityRank(item.node().volatility().severity())).reversed()
                .thenComparing(Comparator.comparingInt((RankedNode item) -> item.node().volatility().swing()).reversed())
                .thenComparing(Comparator.comparingInt((RankedNode item) -> item.node().volatility().worstAdjacentDelta()).reversed())
                .thenComparingInt(RankedNode::rootRank));
        int size = Math.min(limit, nodes.size());
        String[][] rows = new String[size][];
        for (int i = 0; i < size; i++) {
            RankedNode item = nodes.get(i);
            NodeReport node = item.node();
            Volatility v = node.volatility();
            rows[i] = new String[] {
                    Integer.toString(item.rootRank()),
                    fenId(node.fen()),
                    directionShort(v.direction()),
                    Integer.toString(v.swing()),
                    Integer.toString(v.worstAdjacentDelta()),
                    signed(v.startScore()) + "->" + signed(v.finalScore()),
                    movePath(v.startBestMove(), v.finalBestMove()),
                    "d" + node.depth() + "; LCP " + v.firstToFinalLcp() + "; PV " + v.pvChanges()
            };
        }
        return rows;
    }

    /**
     * Returns changed nodes with their root-stack rank.
     * @param changed true when the value changed
     * @return changed ranked nodes result
     */
    private static List<RankedNode> changedRankedNodes(List<RootReport> changed) {
        List<RankedNode> nodes = new ArrayList<>();
        for (int i = 0; i < changed.size(); i++) {
            RootReport report = changed.get(i);
            for (NodeReport node : report.nodes()) {
                if (isChangedNode(node)) {
                    nodes.add(new RankedNode(i + 1, report, node));
                }
            }
        }
        return nodes;
    }

    /**
     * Returns the highest adjacent-depth jumps in changed nodes.
     * @param changed true when the value changed
     * @param limit limit value
     * @return top adjacent jumps result
     */
    private static List<AdjacentJump> topAdjacentJumps(List<RootReport> changed, int limit) {
        List<AdjacentJump> jumps = new ArrayList<>();
        for (RankedNode item : changedRankedNodes(changed)) {
            AdjacentJump jump = worstAdjacentJump(item);
            if (jump != null) {
                jumps.add(jump);
            }
        }
        jumps.sort(Comparator
                .comparingInt(AdjacentJump::delta).reversed()
                .thenComparing(Comparator
                        .comparingInt((AdjacentJump jump) -> severityRank(jump.node().volatility().severity()))
                        .reversed())
                .thenComparingInt(AdjacentJump::rootRank));
        return jumps.subList(0, Math.min(limit, jumps.size()));
    }

    /**
     * Returns the largest adjacent jump for one node.
     * @param item item value
     * @return worst adjacent jump result
     */
    private static AdjacentJump worstAdjacentJump(RankedNode item) {
        List<TimelineEntry> timeline = item.node().volatility().timeline();
        if (timeline.size() < 2) {
            return null;
        }
        TimelineEntry bestFrom = timeline.get(0);
        TimelineEntry bestTo = timeline.get(1);
        int bestDelta = Math.abs(bestTo.score() - bestFrom.score());
        for (int i = 2; i < timeline.size(); i++) {
            TimelineEntry from = timeline.get(i - 1);
            TimelineEntry to = timeline.get(i);
            int delta = Math.abs(to.score() - from.score());
            if (delta > bestDelta) {
                bestDelta = delta;
                bestFrom = from;
                bestTo = to;
            }
        }
        return new AdjacentJump(item.rootRank(), item.root(), item.node(), bestDelta, bestFrom, bestTo);
    }

    /**
     * Builds output-health rows.
     * @param stats statistics data
     * @return output health rows result
     */
    private static String[][] outputHealthRows(ReportStats stats) {
        return new String[][] {
                { "Stacks scanned", num(stats.rootStacks()) },
                { "Changed stacks", num(stats.changedStacks()) + " (" + pct(stats.changedStacks(), stats.rootStacks()) + ")" },
                { "Changed nodes", num(stats.changedNodes()) },
                { "Reversal stacks", num(stats.reversalStacks()) },
                { "Swing span", stats.minSwing() + "-" + stats.maxSwing() },
                { "Score sources", stats.scoreSources() },
                { "CSV scope", "changed only" }
        };
    }

    /**
     * Computes report-wide PDF statistics.
     * @param reports report data rows
     * @return report stats result
     */
    private static ReportStats reportStats(List<RootReport> reports) {
        List<RootReport> changed = changedReports(reports);
        return new ReportStats(
                reports.size(),
                changed.size(),
                changedNodeCountInReports(changed),
                reversalCount(reports),
                percentileSwing(changed, 0.00),
                percentileSwing(changed, 0.10),
                percentileSwing(changed, 0.50),
                percentileSwing(changed, 0.90),
                percentileSwing(changed, 0.99),
                percentileSwing(changed, 1.00),
                scoreSourceSummary(changed),
                changedBestMoveShare(changed),
                changedOutcomeShare(changed),
                medianFirstToFinalLcp(changed),
                percentileFinalDepth(changed, 0.00),
                percentileFinalDepth(changed, 0.50),
                percentileFinalDepth(changed, 1.00));
    }

    /**
     * Chooses the focused PDF x-axis for the swing chart.
     * @param changed true when the value changed
     * @param stats statistics data
     * @return report swing axis result
     */
    private static SwingAxis reportSwingAxis(List<RootReport> changed, ReportStats stats) {
        if (changed.isEmpty()) {
            return new SwingAxis(0, MIN_DISPLAY_MAX_SWING, 0);
        }
        int min = Math.max(0, roundDown(stats.minSwing(), 100));
        int max = roundUp(stats.p99Swing() + 100, 100);
        max = Math.max(max, min + 1_200);
        int highOutliers = 0;
        for (RootReport report : changed) {
            if (report.treeMaxSwing() > max) {
                highOutliers++;
            }
        }
        return new SwingAxis(min, max, highOutliers);
    }

    /**
     * Summarizes score source labels across changed nodes.
     * @param reports report data rows
     * @return score source summary result
     */
    private static String scoreSourceSummary(List<RootReport> reports) {
        Set<String> sources = new java.util.TreeSet<>();
        for (RootReport report : reports) {
            for (NodeReport node : report.nodes()) {
                if (isChangedNode(node) && !node.volatility().scoreSources().isBlank()) {
                    for (String source : node.volatility().scoreSources().split("\\+")) {
                        if (!source.isBlank()) {
                            sources.add(source);
                        }
                    }
                }
            }
        }
        if (sources.isEmpty()) {
            return "-";
        }
        return String.join("+", sources);
    }

    /**
     * Returns percent of changed stacks whose worst node changed best move.
     * @param reports report data rows
     * @return changed best move share result
     */
    private static double changedBestMoveShare(List<RootReport> reports) {
        if (reports.isEmpty()) {
            return 0.0;
        }
        int count = 0;
        for (RootReport report : reports) {
            if (worstNode(report).volatility().bestMoveChanges() > 0) {
                count++;
            }
        }
        return count * 100.0 / reports.size();
    }

    /**
     * Returns percent of changed stacks whose worst node changed outcome bucket.
     * @param reports report data rows
     * @return changed outcome share result
     */
    private static double changedOutcomeShare(List<RootReport> reports) {
        if (reports.isEmpty()) {
            return 0.0;
        }
        int count = 0;
        for (RootReport report : reports) {
            if (worstNode(report).volatility().outcomeChanges() > 0) {
                count++;
            }
        }
        return count * 100.0 / reports.size();
    }

    /**
     * Returns median first-to-final PV common prefix over changed stacks.
     * @param reports report data rows
     * @return median first to final lcp result
     */
    private static double medianFirstToFinalLcp(List<RootReport> reports) {
        if (reports.isEmpty()) {
            return 0.0;
        }
        List<Integer> values = reports.stream()
                .map(report -> worstNode(report).volatility().firstToFinalLcp())
                .sorted()
                .toList();
        int idx = (int) Math.round((values.size() - 1.0) * 0.50);
        return values.get(Math.max(0, Math.min(values.size() - 1, idx)));
    }

    /**
     * Returns nearest-rank final-depth percentile over changed stacks.
     * @param reports report data rows
     * @param p point value
     * @return percentile final depth result
     */
    private static int percentileFinalDepth(List<RootReport> reports, double p) {
        if (reports.isEmpty()) {
            return 0;
        }
        List<Integer> values = reports.stream()
                .map(report -> worstNode(report).volatility().finalDepth())
                .sorted()
                .toList();
        int idx = (int) Math.round((values.size() - 1.0) * p);
        return values.get(Math.max(0, Math.min(values.size() - 1, idx)));
    }

    /**
     * Returns the PDF chart y-axis maximum.
     * @param percentages percentage values
     * @return report y max result
     */
    private static double reportYMax(double[] percentages) {
        double maxPercent = max(percentages);
        double step = percentTickStep(maxPercent);
        return Math.max(step, Math.ceil(maxPercent / step) * step);
    }

    /**
     * Converts a swing value to a PDF plot x-coordinate.
     * @param swing swing value
     * @param left left coordinate
     * @param plotWidth plot width in pixels
     * @param axis axis label
     * @return swing x result
     */
    private static double swingX(int swing, double left, double plotWidth, SwingAxis axis) {
        return left + plotWidth * (swing - axis.min()) / (double) (axis.max() - axis.min() + BIN_WIDTH);
    }

    /**
     * Chooses x-axis tick spacing for the PDF swing chart.
     * @param displayMax display maximum value
     * @return swing tick step result
     */
    private static int swingTickStep(int displayMax) {
        if (displayMax <= 1600) {
            return 200;
        }
        if (displayMax <= 3200) {
            return 400;
        }
        return 400;
    }

    /**
     * Computes a centered moving average.
     * @param values values to inspect
     * @param window window value
     * @return moving average result
     */
    private static double[] movingAverage(double[] values, int window) {
        if (values.length == 0 || window <= 1) {
            return Arrays.copyOf(values, values.length);
        }
        int radius = window / 2;
        double[] out = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            int start = Math.max(0, i - radius);
            int end = Math.min(values.length - 1, i + radius);
            double sum = 0.0;
            for (int j = start; j <= end; j++) {
                sum += values[j];
            }
            out[i] = sum / (end - start + 1.0);
        }
        return out;
    }

    /**
     * Returns the report creation timestamp.
     * @return created stamp result
     */
    private static String createdStamp() {
        return REPORT_TIMESTAMP.format(ZonedDateTime.now());
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
    private static void drawRight(Canvas canvas, double right, double y, Font font, double size, Color color,
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
    private static void drawCentered(Canvas canvas, double center, double y, Font font, double size, Color color,
            String text) {
        String safe = text == null ? "" : text;
        canvas.drawText(center - font.textWidth(safe, size) / 2.0, y, font, size, color, safe);
    }

    /**
     * Fits text into a PDF table cell.
     * @param text text value
     * @param font font name
     * @param size size value
     * @param width width in pixels
     * @return fit text result
     */
    private static String fitText(String text, Font font, double size, double width) {
        String safe = text == null ? "" : text;
        if (font.textWidth(safe, size) <= width) {
            return safe;
        }
        String suffix = "...";
        while (!safe.isEmpty() && font.textWidth(safe + suffix, size) > width) {
            safe = safe.substring(0, safe.length() - 1);
        }
        return safe.isEmpty() ? suffix : safe + suffix;
    }

    /**
     * Limits long body text.
     * @param text text value
     * @param maxChars max chars value
     * @return fit chars result
     */
    private static String fitChars(String text, int maxChars) {
        String safe = text == null ? "" : text;
        if (safe.length() <= maxChars) {
            return safe;
        }
        return safe.substring(0, Math.max(0, maxChars - 3)).stripTrailing() + "...";
    }

    /**
     * Formats an empty value as a dash.
     * @param value value to use
     * @return empty dash result
     */
    private static String emptyDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    /**
     * Formats a before/after move path.
     * @param before before value
     * @param after after value
     * @return move path result
     */
    private static String movePath(String before, String after) {
        return emptyDash(before) + "->" + emptyDash(after);
    }

    /**
     * Builds a short stable row identifier from a FEN.
     * @param fen FEN string
     * @return fen id result
     */
    private static String fenId(String fen) {
        long hash = 0xcbf29ce484222325L;
        String safe = fen == null ? "" : fen;
        for (int i = 0; i < safe.length(); i++) {
            hash ^= safe.charAt(i);
            hash *= 0x100000001b3L;
        }
        String id = Long.toUnsignedString(hash, 36).toUpperCase(Locale.ROOT);
        return id.length() <= 6 ? id : id.substring(0, 6);
    }

    /**
     * Formats a signed score.
     * @param value value to use
     * @return signed result
     */
    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    /**
     * Truncates a PV by moves rather than characters.
     * @param pv pv value
     * @param maxMoves max moves value
     * @return limit pv result
     */
    private static String limitPv(String pv, int maxMoves) {
        String safe = emptyDash(pv);
        if ("-".equals(safe) || maxMoves <= 0) {
            return safe;
        }
        String[] moves = safe.trim().split("\\s+");
        if (moves.length <= maxMoves) {
            return safe;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxMoves; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(moves[i]);
        }
        return sb.append(" ...").toString();
    }

    /**
     * Formats a count percentage.
     * @param count item count
     * @param total total value
     * @return pct result
     */
    private static String pct(long count, long total) {
        return total <= 0 ? "0.0%" : String.format(Locale.ROOT, "%.1f%%", 100.0 * count / total);
    }

    /**
     * Formats a percentage value already on a 0-100 scale.
     * @param value value to use
     * @return pct label result
     */
    private static String pctLabel(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value);
    }

    /**
     * Formats one decimal place.
     * @param value value to use
     * @return one decimal result
     */
    private static String oneDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    /**
     * Formats an integer with grouping.
     * @param value value to use
     * @return num result
     */
    private static String num(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    /**
     * Compact direction label for tables.
     * @param direction direction value
     * @return direction short result
     */
    private static String directionShort(String direction) {
        return switch (direction == null ? "" : direction) {
            case "losing_to_winning" -> "L->W";
            case "winning_to_losing" -> "W->L";
            case "drawish_to_winning" -> "D->W";
            case "drawish_to_losing" -> "D->L";
            case "unclear_to_winning" -> "U->W";
            case "unclear_to_losing" -> "U->L";
            case "both_reversals" -> "Both";
            case "winning" -> "Win";
            case "losing" -> "Lose";
            case "drawish" -> "Draw";
            default -> direction == null || direction.isBlank() ? "-" : direction;
        };
    }

    /**
     * Human-readable direction label for narrative text.
     * @param direction direction value
     * @return direction label result
     */
    private static String directionLabel(String direction) {
        return switch (direction == null ? "" : direction) {
            case "losing_to_winning" -> "losing to winning";
            case "winning_to_losing" -> "winning to losing";
            case "drawish_to_winning" -> "drawish to winning";
            case "drawish_to_losing" -> "drawish to losing";
            case "unclear_to_winning" -> "unclear to winning";
            case "unclear_to_losing" -> "unclear to losing";
            case "both_reversals" -> "both reversal directions";
            default -> direction == null || direction.isBlank() ? "unknown direction" : direction.replace('_', ' ');
        };
    }

    /**
     * Prints a compact summary to stdout.
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
                    + percentileSwing(reports, 0.00) + " / "
                    + percentileSwing(reports, 0.50) + " / "
                    + percentileSwing(reports, 0.90) + " / "
                    + percentileSwing(reports, 0.99) + " / "
                    + percentileSwing(reports, 1.00));
            System.out.println("stack severities: " + severityCounts(reports));
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
    private static List<RootReport> changedReports(List<RootReport> reports) {
        return reports.stream()
                .filter(report -> report.treeMaxSwing() > 0)
                .toList();
    }

    /**
     * Returns changed stacks with an actual sign/outcome reversal.
     * @param reports report data rows
     * @return reversal reports result
     */
    private static List<RootReport> reversalReports(List<RootReport> reports) {
        return reports.stream()
                .filter(report -> "reversal".equals(report.stackSeverity()))
                .toList();
    }

    /**
     * Counts stacks whose tree contains a sign/outcome reversal.
     * @param reports report data rows
     * @return reversal count result
     */
    private static int reversalCount(List<RootReport> reports) {
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
    private static int changedNodeCountInReports(List<RootReport> reports) {
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
    private static int changedNodeCountInNodes(List<NodeReport> nodes) {
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
    private static boolean isChangedNode(NodeReport node) {
        return node != null && node.volatility().swing() > 0;
    }

    /**
     * Returns the node that explains a root stack's highest volatility rank.
     * @param report report data
     * @return worst node result
     */
    private static NodeReport worstNode(RootReport report) {
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
    private static boolean isMoreImportantNode(NodeReport candidate, NodeReport current) {
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
    private static int severityRank(String severity) {
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
    private static void createParent(Path path) throws IOException {
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
    private static String csv(String value) {
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
    private static String csv(Integer value) {
        return value == null ? "" : value.toString();
    }

    /**
     * Formats an optional long CSV field.
     * @param value value to use
     * @return csv result
     */
    private static String csv(Long value) {
        return value == null ? "" : value.toString();
    }

    /**
     * Appends one SVG line.
     * @param sb string builder
     * @param x1 x1 value
     * @param y1 y1 value
     * @param x2 x2 value
     * @param y2 y2 value
     * @param stroke stroke value
     * @param dashArray dash array value
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
     * @param sb string builder
     * @param x x coordinate
     * @param y y coordinate
     * @param anchor anchor value
     * @param size size value
     * @param fill fill color
     * @param text text value
     */
    private static void appendText(StringBuilder sb, double x, double y, String anchor, int size, String fill,
            String text) {
        sb.append("<text x=\"").append(fmt(x)).append("\" y=\"").append(fmt(y)).append('"');
        if (anchor != null) {
            sb.append(" text-anchor=\"").append(anchor).append('"');
        }
        sb.append(" font-family=\"").append(SVG_FONT).append("\" font-size=\"").append(size)
                .append("\" fill=\"").append(fill).append("\">").append(escapeXml(text)).append("</text>\n");
    }

    /**
     * Escapes SVG text.
     * @param text text value
     * @return escape xml result
     */
    private static String escapeXml(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Draws volatility severity bands.
     * @param sb string builder
     * @param reports report data rows
     * @param left left coordinate
     * @param top top coordinate
     * @param plotW plot width in pixels
     * @param plotH plot height in pixels
     * @param displayMax display maximum value
     */
    private static void drawVolatilityBands(StringBuilder sb, List<RootReport> reports, int left, int top, int plotW,
            int plotH, int displayMax) {
        int[] starts = { 0, 150, VOLATILE_SWING, SHARP_SWING, EXTREME_SWING };
        int[] ends = { 149, VOLATILE_SWING - 1, SHARP_SWING - 1, EXTREME_SWING - 1, displayMax };
        String[] fills = { "#edf7ef", "#f3f7ed", "#fff7e6", "#fff0e4", "#f9e8ec" };
        String[] labels = { "Stable", "Noisy", "Volatile", "Sharp", "Extreme" };
        for (int i = 0; i < starts.length; i++) {
            if (starts[i] > displayMax) {
                continue;
            }
            double x1 = xForSwing(starts[i], left, plotW, displayMax);
            double x2 = xForSwing(Math.min(ends[i], displayMax) + 1, left, plotW, displayMax);
            sb.append("<rect x=\"").append(fmt(x1)).append("\" y=\"").append(fmt(top))
                    .append("\" width=\"").append(fmt(Math.max(0.0, x2 - x1)))
                    .append("\" height=\"").append(fmt(plotH))
                    .append("\" fill=\"").append(fills[i]).append("\" opacity=\"0.38\"/>\n");
            double width = Math.max(0.0, x2 - x1);
            String anchor = width < 135.0 ? "middle" : null;
            double labelX = width < 135.0 ? x1 + width / 2.0 : x1 + 8.0;
            double labelY = top + 16.0 + (width < 135.0 ? (i % 2) * 16.0 : 0.0);
            appendText(sb, labelX, labelY, anchor, 11, "#69737d",
                    labels[i] + " " + String.format(Locale.ROOT, "%.1f%%",
                            percentInSwingRange(reports, starts[i], ends[i])));
        }
    }

    /**
     * Draws graph axes.
     * @param sb string builder
     * @param left left coordinate
     * @param top top coordinate
     * @param plotW plot width in pixels
     * @param plotH plot height in pixels
     * @param maxPercent maximum percentage value
     * @param percentStep percent step value
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
     * Draws percentile guide lines.
     * @param sb string builder
     * @param reports report data rows
     * @param left left coordinate
     * @param top top coordinate
     * @param plotW plot width in pixels
     * @param plotH plot height in pixels
     * @param displayMax display maximum value
     */
    private static void drawPercentileMarkers(StringBuilder sb, List<RootReport> reports, int left, int top, int plotW,
            int plotH, int displayMax) {
        double[] qs = { 0.10, 0.25, 0.50, 0.75, 0.90, 0.99 };
        String[] labels = { "p10", "p25", "median", "p75", "p90", "p99" };
        for (int i = 0; i < qs.length; i++) {
            int swing = percentileSwing(reports, qs[i]);
            double x = xForSwing(swing, left, plotW, displayMax);
            appendLine(sb, x, top, x, top + (double) plotH, "#7b8794", "2 7");
            appendText(sb, x, top - 8.0 - (i % 2) * 14.0, "middle", 11, MUTED_TEXT_FILL,
                    labels[i] + " " + swing);
        }
    }

    /**
     * Draws x-axis swing ticks.
     * @param sb string builder
     * @param left left coordinate
     * @param top top coordinate
     * @param plotW plot width in pixels
     * @param plotH plot height in pixels
     * @param displayMax display maximum value
     */
    private static void drawSwingTicks(StringBuilder sb, int left, int top, int plotW, int plotH,
            int displayMax) {
        double plotBottom = top + (double) plotH;
        int step = displayMax <= 3000 ? 100 : 200;
        for (int swing = 0; swing <= displayMax; swing += step) {
            double x = xForSwing(swing, left, plotW, displayMax);
            appendLine(sb, x, plotBottom, x, plotBottom + 6.0, AXIS_STROKE, null);
            appendText(sb, x, plotBottom + 24.0, "middle", 11, MUTED_TEXT_FILL, Integer.toString(swing));
        }
    }

    /**
     * Draws a small legend.
     * @param sb string builder
     * @param x x coordinate
     * @param y y coordinate
     */
    private static void drawLegend(StringBuilder sb, double x, double y) {
        sb.append("<rect x=\"").append(fmt(x)).append("\" y=\"").append(fmt(y - 17.0))
                .append("\" width=\"275.00\" height=\"34.00\" fill=\"#fbfaf7\" opacity=\"0.88\"/>\n");
        sb.append("<rect x=\"").append(fmt(x + 8.0)).append("\" y=\"").append(fmt(y - 4.0))
                .append("\" width=\"24.00\" height=\"10.00\" fill=\"#2f6f9f\" opacity=\"0.62\"/>\n");
        appendText(sb, x + 38.0, y + 5.0, null, 11, MUTED_TEXT_FILL,
                BIN_WIDTH + "-point bars (% changed)");
    }

    /**
     * Maps a swing value to an x-coordinate.
     * @param swing swing value
     * @param left left coordinate
     * @param plotW plot width in pixels
     * @param displayMax display maximum value
     * @return x for swing result
     */
    private static double xForSwing(int swing, int left, int plotW, int displayMax) {
        return left + plotW * swing / (double) (displayMax + BIN_WIDTH);
    }

    /**
     * Returns the maximum value in a vector.
     * @param values values to inspect
     * @return max result
     */
    private static double max(double[] values) {
        double out = 0.0;
        for (double value : values) {
            out = Math.max(out, value);
        }
        return out;
    }

    /**
     * Builds histogram bins.
     * @param reports report data rows
     * @param displayMax display maximum value
     * @return bins result
     */
    private static int[] bins(List<RootReport> reports, int displayMax) {
        int binCount = (displayMax / BIN_WIDTH) + 1;
        int[] bins = new int[binCount];
        for (RootReport report : reports) {
            int swing = Math.max(0, Math.min(displayMax, report.treeMaxSwing()));
            int idx = Math.min(binCount - 1, swing / BIN_WIDTH);
            bins[idx]++;
        }
        return bins;
    }

    /**
     * Builds histogram bins inside a focused swing axis.
     * @param reports report data rows
     * @param axis axis label
     * @return bins result
     */
    private static int[] bins(List<RootReport> reports, SwingAxis axis) {
        int binCount = ((axis.max() - axis.min()) / BIN_WIDTH) + 1;
        int[] bins = new int[binCount];
        for (RootReport report : reports) {
            int swing = report.treeMaxSwing();
            if (swing < axis.min() || swing > axis.max()) {
                continue;
            }
            int idx = Math.min(binCount - 1, Math.max(0, (swing - axis.min()) / BIN_WIDTH));
            bins[idx]++;
        }
        return bins;
    }

    /**
     * Converts bin counts to percentages.
     * @param bins histogram bins
     * @param sampleCount sample count value
     * @return percentages result
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
     * Chooses a readable percentage tick step.
     * @param maxPercent maximum percentage value
     * @return percent tick step result
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
     * Formats a percentage label.
     * @param percent percent value
     * @return format percent result
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
     * Chooses the displayed swing axis maximum.
     * @param reports report data rows
     * @return display max swing result
     */
    private static int displayMaxSwing(List<RootReport> reports) {
        int max = 0;
        for (RootReport report : reports) {
            max = Math.max(max, report.treeMaxSwing());
        }
        return roundUp(Math.max(MIN_DISPLAY_MAX_SWING, max + DISPLAY_MAX_PADDING), 100);
    }

    /**
     * Rounds a positive integer up to a step.
     * @param value value to use
     * @param step step size
     * @return round up result
     */
    private static int roundUp(int value, int step) {
        return ((value + step - 1) / step) * step;
    }

    /**
     * Rounds a positive integer down to a step.
     * @param value value to use
     * @param step step size
     * @return round down result
     */
    private static int roundDown(int value, int step) {
        if (step <= 0) {
            return value;
        }
        return Math.floorDiv(Math.max(0, value), step) * step;
    }

    /**
     * Formats a decimal for SVG coordinates.
     * @param value value to use
     * @return fmt result
     */
    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /**
     * Returns nearest-rank swing percentile.
     * @param reports report data rows
     * @param p point value
     * @return percentile swing result
     */
    private static int percentileSwing(List<RootReport> reports, double p) {
        if (reports.isEmpty()) {
            return 0;
        }
        List<Integer> swings = reports.stream().map(RootReport::treeMaxSwing).sorted().toList();
        int idx = (int) Math.round((swings.size() - 1.0) * p);
        return swings.get(Math.max(0, Math.min(swings.size() - 1, idx)));
    }

    /**
     * Returns percentage of stack reports inside a swing interval.
     * @param reports report data rows
     * @param start start index
     * @param end end index
     * @return percent in swing range result
     */
    private static double percentInSwingRange(List<RootReport> reports, int start, int end) {
        if (reports.isEmpty()) {
            return 0.0;
        }
        int count = 0;
        for (RootReport report : reports) {
            int swing = report.treeMaxSwing();
            if (swing >= start && swing <= end) {
                count++;
            }
        }
        return count * 100.0 / reports.size();
    }

    /**
     * Counts root stack severities.
     * @param reports report data rows
     * @return severity counts result
     */
    private static String severityCounts(List<RootReport> reports) {
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (RootReport report : reports) {
            counts.merge(report.stackSeverity(), 1, Integer::sum);
        }
        return counts.toString();
    }

    /**
     * Converts one analysis into depth-by-depth volatility metrics.
     * @param analysis analysis value
     * @return volatility result
     */
    private static Volatility volatility(Analysis analysis) {
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
    private static String severity(int swing, String direction) {
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
    private static String moveToString(short move) {
        return move == Move.NO_MOVE ? "" : Move.toString(move);
    }

    /**
     * Converts a PV to UCI text.
     * @param pv pv value
     * @return pv to string result
     */
    private static String pvToString(short[] pv) {
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
    private record Arguments(List<Path> inputs, Path prefix, int maxPuzzles) {
    }

    /**
     * Parsed flag-based arguments before defaults.
     */
    private record RawArguments(List<Path> inputs, Path prefix, int maxPuzzles) {
    }

    /**
     * Mutable parser state.
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
         * Maximum verified puzzle count.
         */
        private int maxPuzzles;

        /**
         * Accepts one command-line token.
         * @param arg arg value
         * @param tokens token values
         * @return accept result
         */
        boolean accept(String arg, Iterator<String> tokens) {
            if ("--out".equals(arg)) {
                return acceptOutputPrefix(tokens);
            }
            if ("--max-puzzles".equals(arg)) {
                return acceptMaxPuzzles(tokens);
            }
            if (arg.startsWith("-")) {
                return false;
            }
            inputs.add(Path.of(arg));
            return true;
        }

        /**
         * Builds immutable raw arguments.
         * @return to raw arguments result
         */
        RawArguments toRawArguments() {
            return new RawArguments(List.copyOf(inputs), prefix, maxPuzzles);
        }

        /**
         * Accept output prefix.
         * @param tokens token values
         * @return accept output prefix result */
        private boolean acceptOutputPrefix(Iterator<String> tokens) {
            if (prefix != null || !tokens.hasNext()) {
                return false;
            }
            prefix = Path.of(tokens.next());
            return true;
        }

        /**
         * Accept max puzzles.
         * @param tokens token values
         * @return accept max puzzles result */
        private boolean acceptMaxPuzzles(Iterator<String> tokens) {
            if (maxPuzzles != 0 || !tokens.hasNext()) {
                return false;
            }
            maxPuzzles = parseMaxPuzzles(tokens.next());
            return maxPuzzles != 0;
        }

        /**
         * Parse max puzzles.
         * @param value value to use
         * @return parse max puzzles result */
        private static int parseMaxPuzzles(String value) {
            try {
                return Math.max(1, Integer.parseInt(value));
            } catch (NumberFormatException ex) {
                return 0;
            }
        }
    }

    /**
     * Record-stream accumulator.
     */
    private static final class VolatilityAccumulator {

        /**
         * Root stack reports.
         */
        private final List<RootReport> rootReports = new ArrayList<>();

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
         * Root trees stopped by safety caps.
         */
        private int truncatedTrees;

        /**
         * Maximum number of verified puzzles to score.
         */
        private final int maxPuzzles;

        /**
         * Creates an accumulator.
         * @param maxPuzzles max puzzles value
         */
        VolatilityAccumulator(int maxPuzzles) {
            this.maxPuzzles = Math.max(0, maxPuzzles);
        }

        /**
         * Accepts one raw JSON record.
         * @param objJson JSON object text
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
            if (rec.getPosition() == null || rec.getAnalysis() == null || rec.getAnalysis().isEmpty()) {
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
         * Builds reports after all child links are indexed.
         */
        void finishReports() {
            rootReports.clear();
            for (int i = 0; i < roots.size(); i++) {
                TreeReport tree = buildTreeReport(roots.get(i));
                truncatedTrees += tree.truncated() ? 1 : 0;
                if (!tree.nodes().isEmpty()) {
                    rootReports.add(rootReport(tree));
                }
                if ((i + 1) % 100_000 == 0) {
                    System.err.println("reported trees=" + (i + 1) + "/" + roots.size());
                }
            }
        }

        /**
         * Converts a verified record into an indexed puzzle node.
         * @param rec record value
         * @return puzzle node result
         */
        private PuzzleNode puzzleNode(Record rec) {
            try {
                Position position = rec.getPosition();
                short best = rec.getAnalysis().getBestMove();
                Position afterBest = best == Move.NO_MOVE ? null : position.copy().play(best);
                long parentSignature = rec.getParent() == null ? Long.MIN_VALUE : rec.getParent().signatureCore();
                return new PuzzleNode(
                        position.toString(),
                        rec.getEngine(),
                        rec.getCreated(),
                        parentSignature,
                        position.signatureCore(),
                        afterBest == null ? Long.MIN_VALUE : afterBest.signatureCore(),
                        best,
                        rec.getAnalysis().copyOf());
            } catch (RuntimeException ex) {
                return null;
            }
        }

        /**
         * Builds one explicit puzzle-stack tree report.
         * @param root root position or node
         * @return build tree report result
         */
        private TreeReport buildTreeReport(PuzzleNode root) {
            List<NodeReport> nodes = new ArrayList<>();
            Queue<NodeAtDepth> queue = new ArrayDeque<>();
            Set<Long> seen = new HashSet<>();
            queue.add(new NodeAtDepth(root, 1));
            seen.add(root.positionSignature);
            boolean truncated = false;
            int branchPoints = 0;
            while (!queue.isEmpty()) {
                NodeAtDepth current = queue.remove();
                nodes.add(new NodeReport(current.node.fen, current.node.engine, current.node.created,
                        current.depth, volatility(current.node.analysis)));
                List<PuzzleNode> children = uniqueChildren(childrenByParent.get(current.node.afterBestSignature));
                if (children.size() > 1) {
                    branchPoints++;
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
                    if (nodes.size() + queue.size() >= MAX_TREE_NODES_PER_ROOT) {
                        truncated = true;
                        queue.clear();
                        break;
                    }
                    queue.add(new NodeAtDepth(child, childDepth));
                }
            }
            return new TreeReport(nodes, branchPoints, truncated);
        }

        /**
         * Builds an aggregate root report.
         * @param tree tree value
         * @return root report result
         */
        private static RootReport rootReport(TreeReport tree) {
            NodeReport root = tree.nodes().get(0);
            int treeDepth = 0;
            int treeMaxSwing = 0;
            int treeUnstableNodes = 0;
            int treeReversalNodes = 0;
            int treeBestMoveChanges = 0;
            int treePvChanges = 0;
            String treeWorstFen = root.fen();
            for (NodeReport node : tree.nodes()) {
                Volatility v = node.volatility();
                treeDepth = Math.max(treeDepth, node.depth());
                treeBestMoveChanges += v.bestMoveChanges();
                treePvChanges += v.pvChanges();
                if ("reversal".equals(v.severity()) || v.swing() >= VOLATILE_SWING) {
                    treeUnstableNodes++;
                }
                if ("reversal".equals(v.severity())) {
                    treeReversalNodes++;
                }
                if (v.swing() > treeMaxSwing) {
                    treeMaxSwing = v.swing();
                    treeWorstFen = node.fen();
                }
            }
            return new RootReport(
                    root,
                    List.copyOf(tree.nodes()),
                    treeMaxSwing,
                    severity(treeMaxSwing, treeReversalNodes > 0 ? "both_reversals" : ""),
                    tree.nodes().size(),
                    treeDepth,
                    tree.branchPoints(),
                    treeUnstableNodes,
                    treeReversalNodes,
                    treeBestMoveChanges,
                    treePvChanges,
                    treeWorstFen);
        }

        /**
         * Deduplicates child records by analyzed position signature.
         * @param children child nodes
         * @return unique children result
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
         * @param objJson JSON object text
         * @param rec record value
         * @return true when is puzzle
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
     */
    private record PuzzleNode(
            String fen,
            String engine,
            long created,
            long parentSignature,
            long positionSignature,
            long afterBestSignature,
            short solutionMove,
            Analysis analysis) {
    }

    /**
     * Queue item for tree traversal.
     */
    private record NodeAtDepth(PuzzleNode node, int depth) {
    }

    /**
     * PDF report-wide statistics.
     */
    private record ReportStats(
            int rootStacks,
            int changedStacks,
            int changedNodes,
            int reversalStacks,
            int minSwing,
            int p10Swing,
            int medianSwing,
            int p90Swing,
            int p99Swing,
            int maxSwing,
            String scoreSources,
            double bestChangedShare,
            double outcomeChangedShare,
            double medianPvPrefix,
            int minFinalDepth,
            int medianFinalDepth,
            int maxFinalDepth) {
    }

    /**
     * Focused PDF chart axis.
     */
    private record SwingAxis(int min, int max, int highOutliers) {
    }

    /**
     * Severity group used by the PDF driver panel.
     */
    private record SwitchGroup(
            String name,
            String shortName,
            int count,
            double medianSwing,
            double avgFirstToFinalLcp,
            double bestChangedShare,
            double outcomeChangedShare) {
    }

    /**
     * Changed node with its root-stack rank in the report.
     */
    private record RankedNode(int rootRank, RootReport root, NodeReport node) {
    }

    /**
     * Largest adjacent-depth score jump for one changed node.
     */
    private record AdjacentJump(
            int rootRank,
            RootReport root,
            NodeReport node,
            int delta,
            TimelineEntry from,
            TimelineEntry to) {
    }

    /**
     * Full tree report before aggregation.
     */
    private record TreeReport(List<NodeReport> nodes, int branchPoints, boolean truncated) {
    }

    /**
     * One node-level report.
     */
    private record NodeReport(String fen, String engine, long created, int depth, Volatility volatility) {
    }

    /**
     * One root stack report.
     */
    private record RootReport(
            NodeReport root,
            List<NodeReport> nodes,
            int treeMaxSwing,
            String stackSeverity,
            int treeNodes,
            int treeDepth,
            int treeBranchPoints,
            int treeUnstableNodes,
            int treeReversalNodes,
            int treeBestMoveChanges,
            int treePvChanges,
            String treeWorstFen) {
    }

    /**
     * One depth timeline point.
     */
    private record TimelineEntry(
            int depth,
            int score,
            String source,
            String rawEval,
            String rawWdl,
            Outcome outcome,
            short bestMove,
            Long nodes,
            Long timeMs,
            short[] pv) {
        /**
         * {.
         * @param depth search depth
         * @param score centipawn score
         * @param source score source
         * @param rawEval raw evaluation text
         * @param rawWdl raw WDL text
         * @param outcome WDL outcome label
         * @param bestMove best move text
         * @param nodes searched node count
         * @param timeMs elapsed time in milliseconds
         * @param pv principal variation text
         */
        private TimelineEntry {
            source = source == null || source.isBlank() ? "unknown" : source;
            rawEval = rawEval == null ? "" : rawEval;
            rawWdl = rawWdl == null ? "" : rawWdl;
            pv = pv == null ? new short[0] : Arrays.copyOf(pv, pv.length);
        }
    }

    /**
     * Signed score plus data source label.
     */
    private record ScoredOutput(int score, String source, String rawEval, String rawWdl) {
        /**
         * {.
         * @param score centipawn score
         * @param source score source
         * @param rawEval raw evaluation text
         * @param rawWdl raw WDL text
         */
        private ScoredOutput {
            source = source == null || source.isBlank() ? "unknown" : source;
            rawEval = rawEval == null ? "" : rawEval;
            rawWdl = rawWdl == null ? "" : rawWdl;
        }
    }

    /**
     * Final second-PV summary.
     */
    private record SecondLine(String bestMove, Integer score, Integer margin) {

        /**
         * Empty second-line summary.
         * @return empty result
         */
        static SecondLine empty() {
            return new SecondLine("", null, null);
        }
    }

    /**
     * Depth volatility summary.
     */
    private record Volatility(
            List<TimelineEntry> timeline,
            int depthSamples,
            String scoreSources,
            int startDepth,
            int finalDepth,
            int startScore,
            int finalScore,
            int minScore,
            int minDepth,
            int maxScore,
            int maxDepth,
            int swing,
            int net,
            int worstAdjacentDelta,
            int signFlips,
            int outcomeChanges,
            int bestMoveChanges,
            int pvChanges,
            int finalBestStableDepth,
            int firstToFinalLcp,
            double avgAdjacentLcp,
            Long finalNodes,
            Long finalTimeMs,
            String direction,
            String severity,
            String startBestMove,
            String finalBestMove,
            String finalSecondBestMove,
            Integer finalSecondScore,
            Integer finalMargin,
            String startPv,
            String finalPv) {

        /**
         * {.
         * @param timeline timeline entries
         * @param depthSamples number of depth samples
         * @param scoreSources score source count
         * @param startDepth starting depth
         * @param finalDepth final depth
         * @param startScore starting score
         * @param finalScore final score
         * @param minScore minimum score
         * @param minDepth depth at the minimum score
         * @param maxScore maximum score
         * @param maxDepth depth at the maximum score
         * @param swing largest score swing
         * @param net net score change
         * @param worstAdjacentDelta largest adjacent score delta
         * @param signFlips number of score sign flips
         * @param outcomeChanges number of outcome changes
         * @param bestMoveChanges number of best-move changes
         * @param pvChanges number of principal-variation changes
         * @param finalBestStableDepth first depth where the final best move stayed stable
         * @param firstToFinalLcp common prefix length between first and final PVs
         * @param avgAdjacentLcp average adjacent principal-variation common prefix length
         * @param finalNodes final searched node count
         * @param finalTimeMs final elapsed time in milliseconds
         * @param direction volatility direction label
         * @param severity volatility severity label
         * @param startBestMove starting best move
         * @param finalBestMove final best move
         * @param finalSecondBestMove final second-best move
         * @param finalSecondScore final second-best score
         * @param finalMargin final score margin
         * @param startPv starting principal variation
         * @param finalPv final principal variation
         */
        private Volatility {
            timeline = timeline == null ? List.of() : List.copyOf(timeline);
            scoreSources = scoreSources == null ? "" : scoreSources;
            direction = direction == null ? "missing" : direction;
            severity = severity == null ? "stable" : severity;
            startBestMove = startBestMove == null ? "" : startBestMove;
            finalBestMove = finalBestMove == null ? "" : finalBestMove;
            finalSecondBestMove = finalSecondBestMove == null ? "" : finalSecondBestMove;
            startPv = startPv == null ? "" : startPv;
            finalPv = finalPv == null ? "" : finalPv;
        }

        /**
         * Empty volatility for records with no usable depth timeline.
         * @return empty result
         */
        static Volatility empty() {
            return new Volatility(
                    List.<TimelineEntry>of(),
                    0,
                    "",
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0,
                    0.0,
                    null,
                    null,
                    "missing",
                    "stable",
                    "",
                    "",
                    "",
                    null,
                    null,
                    "",
                    "");
        }
    }

    /**
     * Coarse outcome buckets.
     */
    private enum Outcome {
        /**
         * The side to move is clearly better.
         */
        WINNING("winning"),

        /**
         * The side to move is clearly worse.
         */
        LOSING("losing"),

        /**
         * The score is close to equality.
         */
        DRAWISH("drawish"),

        /**
         * The score is neither equal nor clearly decisive.
         */
        UNCLEAR("unclear");

        /**
         * CSV-safe label.
         */
        private final String label;

        /**
         * Outcome.
         * @param label label text */
        Outcome(String label) {
            this.label = label;
        }

        /**
         * Returns the CSV-safe label.
         * @return label result
         */
        String label() {
            return label;
        }
    }
}
