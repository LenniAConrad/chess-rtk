package application.cli.command;

import static application.cli.Constants.OPT_JSON;
import static application.cli.Constants.OPT_MAX_DURATION;
import static application.cli.Constants.OPT_MAX_NODES;
import static application.cli.Constants.OPT_NODES;
import static application.cli.Constants.OPT_THREADS;
import static application.cli.Constants.OPT_VERBOSE;
import static application.cli.Constants.OPT_VERBOSE_SHORT;
import static application.cli.Validation.requirePositive;

import chess.core.Move;
import chess.core.MoveInference;
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
 * Implements {@code engine search}: runs a PUCT (MCTS) search and prints the
 * per-root-move statistics — visits, prior, value, and centipawn score — that
 * the workbench Search panel visualizes.
 *
 * <p>
 * Deterministic for a fixed node budget. Use {@code --json} for one
 * machine-readable summary object. The backend is selected exactly like
 * {@code engine builtin} via {@code --classical}/{@code --nnue}/{@code --lc0}/
 * {@code --otis}/{@code --evaluator}/{@code --weights}.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class EngineSearchCommand {

    /**
     * MCTS exploration-constant flag.
     */
    private static final String OPT_CPUCT = "--cpuct";

    /**
     * Top-root-move limit flag.
     */
    private static final String OPT_MOVES = "--moves";

    /**
     * Default playout budget.
     */
    private static final long DEFAULT_NODES = 2_000L;

    /**
     * Default MCTS exploration constant.
     */
    private static final double DEFAULT_CPUCT = 2.8;

    /**
     * Utility class; prevent instantiation.
     */
    private EngineSearchCommand() {
        // utility
    }

    /**
     * Handles {@code engine search}.
     *
     * @param a argument parser
     */
    public static void runSearch(Argv a) {
        String cmd = "engine search";
        boolean verbose = a.flag(OPT_VERBOSE, OPT_VERBOSE_SHORT);
        boolean json = a.flag(OPT_JSON);
        long nodes = a.lngOr(DEFAULT_NODES, OPT_NODES, OPT_MAX_NODES);
        Duration duration = a.duration(OPT_MAX_DURATION);
        double cpuct = a.dblOr(DEFAULT_CPUCT, OPT_CPUCT);
        int threads = a.integerOr(1, OPT_THREADS);
        int topMoves = a.integerOr(0, OPT_MOVES);
        long movetime = duration == null ? 0L : Math.max(0L, duration.toMillis());
        MctsCommandSupport.Built built = build(a, cmd, cpuct, threads);
        Position position = CommandSupport.resolvePositionArgument(a, cmd, true, verbose);
        a.ensureConsumed();

        requirePositive(cmd, OPT_THREADS, threads);
        if (movetime <= 0) {
            requirePositive(cmd, OPT_NODES, nodes);
        }

        try (Mcts mcts = built.mcts()) {
            Limits limits = MctsCommandSupport.limits(nodes, movetime);
            Result result = mcts.search(position, limits);
            Mcts.TreeNode root = mcts.treeSnapshot(1, topMoves, 0);
            List<Mcts.TreeNode> moves = root == null ? List.of() : root.children();
            if (json) {
                System.out.println(toJson(position, built.backend(), result, moves));
            } else {
                printText(position, built.backend(), cpuct, threads, result, moves);
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
     * Prints the human-readable search report.
     *
     * @param position root position
     * @param backend backend label
     * @param cpuct exploration constant
     * @param threads worker threads
     * @param result search result
     * @param moves ranked root-move snapshots
     */
    private static void printText(Position position, String backend, double cpuct, int threads,
            Result result, List<Mcts.TreeNode> moves) {
        System.out.println("FEN: " + position);
        System.out.printf(Locale.ROOT, "backend: %s   playouts: %d   cpuct: %s   threads: %d%n",
                backend, result.nodes(), trimDouble(cpuct), threads);
        String bestUci = result.bestMove() == Move.NO_MOVE
                ? "(none)"
                : MoveInference.notation(position, result.bestMove()).uci();
        String bestSan = result.bestMove() == Move.NO_MOVE
                ? "(none)"
                : MoveInference.notation(position, result.bestMove()).san();
        System.out.printf(Locale.ROOT, "best: %s (%s)   score: %+dcp%n",
                bestSan, bestUci, result.scoreCentipawns());
        System.out.println("pv: " + pvText(position, result.principalVariation()));
        System.out.println("moves (ranked):");
        if (moves.isEmpty()) {
            System.out.println("  (no expanded root moves)");
            return;
        }
        for (Mcts.TreeNode move : moves) {
            System.out.printf(Locale.ROOT, "  %-7s %-6s N=%-6d P=%5.1f%%  Q=%+.3f  %+dcp%n",
                    move.san(), move.uci(), move.visits(), move.prior() * 100.0, move.q(),
                    move.scoreCentipawns());
        }
    }

    /**
     * Renders the search result as one JSON object.
     *
     * @param position root position
     * @param backend backend label
     * @param result search result
     * @param moves ranked root-move snapshots
     * @return JSON object
     */
    private static String toJson(Position position, String backend, Result result, List<Mcts.TreeNode> moves) {
        StringBuilder out = new StringBuilder(256);
        out.append('{');
        out.append("\"fen\":").append(CommandSupport.jsonString(position.toString()));
        out.append(",\"backend\":").append(CommandSupport.jsonString(backend));
        out.append(",\"playouts\":").append(result.nodes());
        out.append(",\"best\":");
        if (result.bestMove() == Move.NO_MOVE) {
            out.append("null");
        } else {
            MoveInference.Notation best = MoveInference.notation(position, result.bestMove());
            out.append("{\"san\":").append(CommandSupport.jsonString(best.san()))
                    .append(",\"uci\":").append(CommandSupport.jsonString(best.uci()))
                    .append(",\"scoreCentipawns\":").append(result.scoreCentipawns()).append('}');
        }
        out.append(",\"pv\":[");
        short[] pv = result.principalVariation();
        Position walker = position.copy();
        for (int i = 0; i < pv.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(CommandSupport.jsonString(MoveInference.notation(walker, pv[i]).uci()));
            walker.play(pv[i]);
        }
        out.append(']');
        out.append(",\"moves\":[");
        for (int i = 0; i < moves.size(); i++) {
            Mcts.TreeNode move = moves.get(i);
            if (i > 0) {
                out.append(',');
            }
            out.append("{\"san\":").append(CommandSupport.jsonString(move.san()))
                    .append(",\"uci\":").append(CommandSupport.jsonString(move.uci()))
                    .append(",\"visits\":").append(move.visits())
                    .append(",\"prior\":").append(String.format(Locale.ROOT, "%.5f", move.prior()))
                    .append(",\"q\":").append(String.format(Locale.ROOT, "%.5f", move.q()))
                    .append(",\"scoreCentipawns\":").append(move.scoreCentipawns()).append('}');
        }
        out.append("]}");
        return out.toString();
    }

    /**
     * Renders a principal variation as a space-separated UCI line.
     *
     * @param position root position
     * @param pv principal-variation moves
     * @return UCI line, or {@code (none)} when empty
     */
    private static String pvText(Position position, short[] pv) {
        if (pv.length == 0) {
            return "(none)";
        }
        StringBuilder out = new StringBuilder(pv.length * 6);
        Position walker = position.copy();
        for (int i = 0; i < pv.length; i++) {
            if (i > 0) {
                out.append(' ');
            }
            out.append(MoveInference.notation(walker, pv[i]).uci());
            walker.play(pv[i]);
        }
        return out.toString();
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
