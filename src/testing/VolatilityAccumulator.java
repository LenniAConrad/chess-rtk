package testing;

import static testing.PuzzleVolatilityReport.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import application.Config;
import chess.core.Move;
import chess.core.Position;
import chess.struct.Record;
import utility.Json;

/**
 * Record-stream accumulator.
 */
final class VolatilityAccumulator {

    /**
     * Root stack reports.
     */
    final List<RootReport> rootReports = new ArrayList<>();

    /**
     * Verified puzzle roots selected for output.
     */
    final List<PuzzleNode> roots = new ArrayList<>();

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
    int skipped;

    /**
     * Parsed records rejected by the puzzle verifier.
     */
    int nonPuzzles;

    /**
     * Invalid records.
     */
    int invalid;

    /**
     * Root trees stopped by safety caps.
     */
    int truncatedTrees;

    /**
     * Maximum number of verified puzzles to score.
     */
    private final int maxPuzzles;

    /**
     * Creates an accumulator.
     * @param maxPuzzles maximum puzzle count
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
        if (node.parentSignature() != Long.MIN_VALUE) {
            childrenByParent.computeIfAbsent(node.parentSignature(), ignored -> new ArrayList<>()).add(node);
        }
        if (!rootSignatures.add(node.positionSignature())) {
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
     * @return converted a verified record into an indexed puzzle node
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
     * @return built one explicit puzzle-stack tree report
     */
    private TreeReport buildTreeReport(PuzzleNode root) {
        List<NodeReport> nodes = new ArrayList<>();
        Queue<NodeAtDepth> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        queue.add(new NodeAtDepth(root, 1));
        seen.add(root.positionSignature());
        boolean truncated = false;
        int branchPoints = 0;
        while (!queue.isEmpty()) {
            NodeAtDepth current = queue.remove();
            PuzzleNode node = current.node();
            nodes.add(new NodeReport(node.fen(), node.engine(), node.created(),
                    current.depth(), volatility(node.analysis())));
            List<PuzzleNode> children = uniqueChildren(childrenByParent.get(node.afterBestSignature()));
            if (children.size() > 1) {
                branchPoints++;
            }
            int childDepth = current.depth() + 1;
            if (childDepth > MAX_TREE_SOLVER_DEPTH) {
                truncated |= !children.isEmpty();
                continue;
            }
            for (PuzzleNode child : children) {
                if (!seen.add(child.positionSignature())) {
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
     * @param tree source tree
     * @return built an aggregate root report
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
     * @return unique children
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
            if (seen.add(child.positionSignature())) {
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
