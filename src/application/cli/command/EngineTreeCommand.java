package application.cli.command;

import static application.cli.Constants.OPT_DEPTH;
import static application.cli.Constants.OPT_DEPTH_SHORT;
import static application.cli.Constants.OPT_JSON;
import static application.cli.Constants.OPT_MAX_DURATION;
import static application.cli.Constants.OPT_MAX_NODES;
import static application.cli.Constants.OPT_NODES;
import static application.cli.Constants.OPT_THREADS;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;
import static application.cli.Validation.requireNonNegative;
import static application.cli.Validation.requirePositive;

import chess.core.Position;
import chess.engine.Limits;
import chess.engine.Mcts;
import chess.engine.Result;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import utility.Argv;

/**
 * Implements {@code engine tree}: runs a PUCT (MCTS) search and dumps the
 * resulting search tree — the structure the workbench Tree panel visualizes —
 * with per-node visits, prior, value, and centipawn score.
 *
 * <p>
 * Children are ordered exactly as the engine ranks them (proven outcomes first,
 * then visits, then value, then prior). Deterministic for a fixed node budget.
 * Use {@code --json} for a nested machine-readable tree. The backend is selected
 * like {@code engine builtin}.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class EngineTreeCommand {

    /**
     * MCTS exploration-constant flag.
     */
    private static final String OPT_CPUCT = "--cpuct";

    /**
     * Per-node child-limit flag.
     */
    private static final String OPT_BRANCHES = "--branches";

    /**
     * Minimum-visit filter flag.
     */
    private static final String OPT_MIN_VISITS = "--min-visits";

    /**
     * Default playout budget.
     */
    private static final long DEFAULT_NODES = 2_000L;

    /**
     * Default MCTS exploration constant.
     */
    private static final double DEFAULT_CPUCT = 2.8;

    /**
     * Default printed tree depth.
     */
    private static final int DEFAULT_DEPTH = 3;

    /**
     * Default maximum children printed per node.
     */
    private static final int DEFAULT_BRANCHES = 4;

    /**
     * Utility class; prevent instantiation.
     */
    private EngineTreeCommand() {
        // utility
    }

    /**
     * Handles {@code engine tree}.
     *
     * @param a argument parser
     */
    public static void runTree(Argv a) {
        String cmd = "engine tree";
        boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
        boolean json = a.flag(OPT_JSON);
        long nodes = a.lngOr(DEFAULT_NODES, OPT_NODES, OPT_MAX_NODES);
        Duration duration = a.duration(OPT_MAX_DURATION);
        double cpuct = a.dblOr(DEFAULT_CPUCT, OPT_CPUCT);
        int threads = a.integerOr(1, OPT_THREADS);
        int depth = a.integerOr(DEFAULT_DEPTH, OPT_DEPTH, OPT_DEPTH_SHORT);
        int branches = a.integerOr(DEFAULT_BRANCHES, OPT_BRANCHES);
        int minVisits = a.integerOr(1, OPT_MIN_VISITS);
        long movetime = duration == null ? 0L : Math.max(0L, duration.toMillis());
        MctsCommandSupport.Built built = build(a, cmd, cpuct, threads);
        Position position = CommandSupport.resolvePositionArgument(a, cmd, true, verbose);
        a.ensureConsumed();

        requirePositive(cmd, OPT_THREADS, threads);
        requirePositive(cmd, OPT_DEPTH, depth);
        requireNonNegative(cmd, OPT_BRANCHES, branches);
        requireNonNegative(cmd, OPT_MIN_VISITS, minVisits);
        if (movetime <= 0) {
            requirePositive(cmd, OPT_NODES, nodes);
        }

        try (Mcts mcts = built.mcts()) {
            Limits limits = MctsCommandSupport.limits(nodes, movetime);
            Result result = mcts.search(position, limits);
            Mcts.TreeNode root = mcts.treeSnapshot(depth, branches, minVisits);
            if (json) {
                System.out.println(toJson(position, built.backend(), result, root));
            } else {
                printText(position, built.backend(), cpuct, threads, result, root);
            }
        }
    }

    /**
     * Builds the searcher, mapping load failures to a CLI diagnostic.
     *
     * @param a argument parser
     * @param cmd command label
     * @param cpuct exploration constant
     * @param threads worker threads
     * @return configured searcher
     */
    private static MctsCommandSupport.Built build(Argv a, String cmd, double cpuct, int threads) {
        try {
            return MctsCommandSupport.build(a, cmd, cpuct, threads);
        } catch (IOException ex) {
            throw new CommandFailure(cmd + ": could not load engine backend: " + ex.getMessage(), 2);
        }
    }

    /**
     * Prints the human-readable tree.
     *
     * @param position root position
     * @param backend backend label
     * @param cpuct exploration constant
     * @param threads worker threads
     * @param result search result
     * @param root snapshot root, or {@code null}
     */
    private static void printText(Position position, String backend, double cpuct, int threads,
            Result result, Mcts.TreeNode root) {
        System.out.println("FEN: " + position);
        System.out.printf(Locale.ROOT, "backend: %s   playouts: %d   cpuct: %s   threads: %d%n",
                backend, result.nodes(), trimDouble(cpuct), threads);
        if (root == null) {
            System.out.println("(no search tree)");
            return;
        }
        System.out.printf(Locale.ROOT, "(root)  N=%d  Q=%+.3f%n", root.visits(), root.q());
        printChildren(root, 1);
    }

    /**
     * Prints a node's children with indentation.
     *
     * @param node parent snapshot node
     * @param indent indentation depth
     */
    private static void printChildren(Mcts.TreeNode node, int indent) {
        String pad = "  ".repeat(indent);
        for (Mcts.TreeNode child : node.children()) {
            String proof = "UNKNOWN".equals(child.proof()) ? "" : "  [" + child.proof() + "]";
            System.out.printf(Locale.ROOT, "%s%-7s %-6s N=%-6d P=%5.1f%%  Q=%+.3f  %+dcp%s%n",
                    pad, child.san(), child.uci(), child.visits(), child.prior() * 100.0, child.q(),
                    child.scoreCentipawns(), proof);
            printChildren(child, indent + 1);
        }
    }

    /**
     * Renders the tree as one nested JSON object.
     *
     * @param position root position
     * @param backend backend label
     * @param result search result
     * @param root snapshot root, or {@code null}
     * @return JSON object
     */
    private static String toJson(Position position, String backend, Result result, Mcts.TreeNode root) {
        StringBuilder out = new StringBuilder(512);
        out.append('{');
        out.append("\"fen\":").append(CommandSupport.jsonString(position.toString()));
        out.append(",\"backend\":").append(CommandSupport.jsonString(backend));
        out.append(",\"playouts\":").append(result.nodes());
        out.append(",\"tree\":");
        if (root == null) {
            out.append("null");
        } else {
            appendNodeJson(out, root);
        }
        out.append('}');
        return out.toString();
    }

    /**
     * Appends one snapshot node and its descendants as JSON.
     *
     * @param out output buffer
     * @param node snapshot node
     */
    private static void appendNodeJson(StringBuilder out, Mcts.TreeNode node) {
        out.append('{');
        out.append("\"san\":").append(CommandSupport.jsonString(node.san()));
        out.append(",\"uci\":").append(CommandSupport.jsonString(node.uci()));
        out.append(",\"visits\":").append(node.visits());
        out.append(",\"prior\":").append(String.format(Locale.ROOT, "%.5f", node.prior()));
        out.append(",\"q\":").append(String.format(Locale.ROOT, "%.5f", node.q()));
        out.append(",\"scoreCentipawns\":").append(node.scoreCentipawns());
        out.append(",\"depth\":").append(node.depth());
        out.append(",\"proof\":").append(CommandSupport.jsonString(node.proof()));
        out.append(",\"children\":[");
        List<Mcts.TreeNode> children = node.children();
        for (int i = 0; i < children.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            appendNodeJson(out, children.get(i));
        }
        out.append("]}");
    }

    /**
     * Formats a double without a trailing {@code .0} for whole values.
     *
     * @param value value to format
     * @return compact string
     */
    private static String trimDouble(double value) {
        if (value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }
}
