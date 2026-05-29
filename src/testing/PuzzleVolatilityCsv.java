package testing;

import static testing.PuzzleVolatilityReport.*;
import static testing.PuzzleVolatilityOutput.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * CSV writers for {@link PuzzleVolatilityReport}.
 */
final class PuzzleVolatilityCsv {

    /**
     * Utility class; prevent instantiation.
     */
    private PuzzleVolatilityCsv() {
        // utility
    }

    /**
     * Writes root-level volatility metrics as CSV.
     *
     * @param path output CSV path
     * @param reports root reports
     * @throws IOException on write failure
     */
    static void writeRootCsv(Path path, List<RootReport> reports) throws IOException {
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
    static void writeNodeCsv(Path path, List<RootReport> reports) throws IOException {
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
    static void writeTimelineCsv(Path path, List<RootReport> reports) throws IOException {
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
}
