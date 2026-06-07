package application.cli.command;

import static application.cli.Constants.OPT_CLASSICAL;
import static application.cli.Constants.OPT_EVALUATOR;
import static application.cli.Constants.OPT_LC0;
import static application.cli.Constants.OPT_OTIS;
import static application.cli.Constants.OPT_WEIGHTS;

import chess.engine.AlphaBeta;
import chess.engine.Limits;
import chess.engine.Mcts;
import chess.eval.Factory;
import chess.eval.Kind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import utility.Argv;

/**
 * Shared backend selection and limit parsing for the MCTS-backed engine
 * commands ({@code engine search} and {@code engine tree}).
 *
 * <p>
 * Offers the same backends as the workbench Search/Tree panels
 * ({@code --classical}, {@code --nnue}, {@code --lc0}, {@code --bt4},
 * {@code --otis}, plus {@code --evaluator}/{@code --weights}) and builds a
 * configured {@link Mcts} searcher.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
final class MctsCommandSupport {

    /**
     * {@code --nnue} backend shortcut flag.
     */
    static final String OPT_NNUE = "--nnue";

    /**
     * {@code --bt4} backend shortcut flag.
     */
    static final String OPT_BT4 = "--bt4";

    /**
     * Selectable MCTS backends.
     */
    enum Backend {

        /**
         * Classical handcrafted evaluator with heuristic priors.
         */
        CLASSICAL,

        /**
         * Pure-Java NNUE evaluator with heuristic priors.
         */
        NNUE,

        /**
         * LC0 CNN policy/value network.
         */
        LC0,

        /**
         * LC0 BT4 attention policy/value network.
         */
        BT4,

        /**
         * OTIS policy/WDL network.
         */
        OTIS;

        /**
         * Returns the stable lowercase label.
         *
         * @return backend label
         */
        String label() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Utility class; prevent instantiation.
     */
    private MctsCommandSupport() {
        // utility
    }

    /**
     * A configured MCTS searcher and its backend label.
     *
     * @param mcts configured searcher
     * @param backend backend label for output
     */
    record Built(Mcts mcts, String backend) {
    }

    /**
     * Resolves the requested backend from shortcut flags or {@code --evaluator}.
     *
     * @param a argument parser
     * @param cmd command label for diagnostics
     * @return resolved backend
     */
    static Backend resolveBackend(Argv a, String cmd) {
        String value = a.string(OPT_EVALUATOR);
        boolean classical = a.flag(OPT_CLASSICAL);
        boolean nnue = a.flag(OPT_NNUE);
        boolean lc0 = a.flag(OPT_LC0);
        boolean bt4 = a.flag(OPT_BT4);
        boolean otis = a.flag(OPT_OTIS);
        int flags = (classical ? 1 : 0) + (nnue ? 1 : 0) + (lc0 ? 1 : 0) + (bt4 ? 1 : 0) + (otis ? 1 : 0);
        if (value != null && flags > 0) {
            throw new CommandFailure(
                    cmd + ": use either " + OPT_EVALUATOR + " or a backend shortcut flag, not both", 2);
        }
        if (flags > 1) {
            throw new CommandFailure(cmd + ": choose only one backend flag", 2);
        }
        if (value != null) {
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "classical", "static" -> Backend.CLASSICAL;
                case "nnue" -> Backend.NNUE;
                case "lc0", "leela", "cnn" -> Backend.LC0;
                case "bt4" -> Backend.BT4;
                case "otis" -> Backend.OTIS;
                default -> throw new CommandFailure(cmd + ": unknown backend: " + value
                        + " (use classical, nnue, lc0, bt4, or otis)", 2);
            };
        }
        if (nnue) {
            return Backend.NNUE;
        }
        if (lc0) {
            return Backend.LC0;
        }
        if (bt4) {
            return Backend.BT4;
        }
        if (otis) {
            return Backend.OTIS;
        }
        return Backend.CLASSICAL;
    }

    /**
     * Builds a configured MCTS searcher from the standard backend flags.
     *
     * @param a argument parser
     * @param cmd command label for diagnostics
     * @param cpuct exploration constant
     * @param threads worker threads
     * @return configured searcher and backend label
     * @throws IOException if a neural backend's weights cannot be loaded
     */
    static Built build(Argv a, String cmd, double cpuct, int threads) throws IOException {
        Backend backend = resolveBackend(a, cmd);
        Path weights = a.path(OPT_WEIGHTS);
        Mcts mcts = switch (backend) {
            case LC0 -> Mcts.lc0(weights, cpuct);
            case BT4 -> {
                if (weights == null) {
                    throw new CommandFailure(cmd + ": " + OPT_BT4 + " requires " + OPT_WEIGHTS + " <path>", 2);
                }
                yield Mcts.bt4(weights, cpuct);
            }
            case OTIS -> Mcts.otis(weights, cpuct);
            case NNUE -> new Mcts(Factory.create(Kind.NNUE, resolveNnueWeights(weights, cmd)), cpuct);
            case CLASSICAL -> new Mcts(Factory.create(Kind.CLASSICAL, null), cpuct);
        };
        mcts.setThreads(threads);
        return new Built(mcts, backend.label());
    }

    /**
     * Builds the per-move search limits from a node (playout) or time budget.
     *
     * @param nodes fixed playout budget (used when {@code movetime} is zero)
     * @param movetime fixed time budget in milliseconds, or zero
     * @return search limits
     */
    static Limits limits(long nodes, long movetime) {
        return movetime > 0
                ? new Limits(AlphaBeta.MAX_DEPTH, 0L, movetime)
                : new Limits(AlphaBeta.MAX_DEPTH, nodes, 0L);
    }

    /**
     * Resolves NNUE weights, falling back to the bundled default.
     *
     * @param weights explicit weights path, or {@code null}
     * @param cmd command label for diagnostics
     * @return resolved weights path
     */
    private static Path resolveNnueWeights(Path weights, String cmd) {
        if (weights != null) {
            return weights;
        }
        Path defaultWeights = chess.nn.nnue.Model.DEFAULT_WEIGHTS;
        if (Files.isRegularFile(defaultWeights)) {
            return defaultWeights;
        }
        throw new CommandFailure(
                cmd + ": default NNUE weights not found at " + defaultWeights
                        + "; install that file, run ./install.sh --models, or pass " + OPT_WEIGHTS + " <path>",
                2);
    }
}
