package application.gui.workbench.play;

import application.gui.workbench.network.RealActivations;
import application.gui.workbench.play.Opponent.Network;
import chess.eval.CentipawnEvaluator;
import chess.eval.Classical;
import chess.eval.Lc0;
import chess.eval.Nnue;
import chess.eval.Otis;
import java.nio.file.Path;

/**
 * Maps an {@link Network} to its weight file and to a static evaluator for the
 * alpha-beta opponent. Centralizes the network→file→evaluator wiring shared by
 * the Play opponents so both search algorithms select networks the same way.
 */
final class Networks {

    /**
     * NNUE network path (Stockfish HalfKP net).
     */
    private static final Path NNUE_PATH = Path.of("models/crtk-halfkp.nnue");

    /**
     * Prevents instantiation.
     */
    private Networks() {
        // utility
    }

    /**
     * Returns the weights path for a network, or {@code null} for the
     * weightless classical network.
     *
     * @param network selected network
     * @return weights path, or {@code null}
     */
    static Path weightsPath(Network network) {
        return switch (network) {
            case NNUE -> NNUE_PATH;
            case CNN -> RealActivations.cnnPath();
            case OTIS -> RealActivations.otisPath();
            default -> null;
        };
    }

    /**
     * Returns whether a network can actually be used, i.e. its weights file is
     * present. The weightless Classical network is always available; the neural
     * networks require their weights on disk. Lets the UI fall back to Classical
     * (and warn) instead of silently degrading when a model file is missing.
     *
     * @param network selected network
     * @return true when the network is usable
     */
    static boolean isAvailable(Network network) {
        if (network == Network.CLASSICAL) {
            return true;
        }
        Path path = weightsPath(network);
        return path != null && java.nio.file.Files.isRegularFile(path);
    }

    /**
     * Builds a static evaluator for the alpha-beta search, falling back to the
     * classical evaluator when a neural network cannot be loaded so the opponent
     * is always usable.
     *
     * @param network requested network
     * @return an evaluator for {@code network}, or a classical evaluator on failure
     */
    static CentipawnEvaluator evaluator(Network network) {
        try {
            return switch (network) {
                case NNUE -> new Nnue(NNUE_PATH);
                case CNN -> new Lc0(RealActivations.cnnPath());
                case OTIS -> new Otis(RealActivations.otisPath());
                default -> new Classical();
            };
        } catch (RuntimeException | java.io.IOException ex) {
            return new Classical();
        }
    }
}
