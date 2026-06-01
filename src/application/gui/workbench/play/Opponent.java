package application.gui.workbench.play;

import chess.core.Position;
import java.util.List;

/**
 * A chess opponent that, given a position and a search budget, produces a move
 * together with the ranked root candidates it considered.
 *
 * <p>
 * This is the single backend-swap seam for Play mode. Implementations drive the
 * in-process MCTS and alpha-beta searches behind the same controller contract,
 * so {@link PlaySession} does not depend on a concrete search class.
 * </p>
 *
 * <p>
 * {@link #chooseMove} is always invoked off the event-dispatch thread and may
 * block for the duration of the search budget. Implementations must observe
 * {@link #cancel()} and thread interruption so a superseding request (new game,
 * resign, take-back) can abandon an in-flight search promptly.
 * </p>
 */
public interface Opponent extends AutoCloseable {

    /**
     * Tree-search algorithm. The two are research-comparable: alpha-beta is a
     * deep pruned minimax search (Stockfish-style), MCTS is a PUCT policy/value
     * tree search (lc0-style). Either can be paired with any {@link Network}.
     */
    enum Search {
        /**
         * Iterative-deepening alpha-beta with a transposition table, null-move
         * pruning, and aspiration windows. Strong with a fast evaluator.
         */
        ALPHA_BETA("Alpha-Beta"),

        /**
         * PUCT Monte-Carlo tree search. Built for a policy+value evaluator; with
         * the classical evaluator it serves as the baseline.
         */
        MCTS("MCTS");

        /**
         * Display label.
         */
        private final String label;

        /**
         * Creates a search with a display label.
         *
         * @param label display label
         */
        Search(String label) {
            this.label = label;
        }

        /**
         * Returns the display label.
         *
         * @return display label
         */
        public String label() {
            return label;
        }
    }

    /**
     * Static-evaluation / policy network feeding the search. Each maps to a
     * {@link chess.eval.CentipawnEvaluator} for alpha-beta and to an
     * {@code MctsSearch} backend for MCTS.
     */
    enum Network {
        /**
         * Handcrafted classical evaluation. No weights; always available.
         */
        CLASSICAL("Classical"),

        /**
         * Pure-Java NNUE (Stockfish HalfKP net). Strong, slower per node.
         */
        NNUE("NNUE"),

        /**
         * lc0-style convolutional policy/value network.
         */
        CNN("CNN"),

        /**
         * OTIS policy/WDL network.
         */
        OTIS("OTIS");

        /**
         * Display label.
         */
        private final String label;

        /**
         * Creates a network with a display label.
         *
         * @param label display label
         */
        Network(String label) {
            this.label = label;
        }

        /**
         * Returns the display label.
         *
         * @return display label
         */
        public String label() {
            return label;
        }
    }

    /**
     * Chooses a reply for the side to move under a fixed search budget.
     *
     * @param position position to move in; the side to move is the opponent
     * @param budget search budget for this move
     * @param requestId monotonic id of the originating request, for diagnostics
     * @return the chosen move and its ranked candidates; {@link MoveChoice#move()}
     *         is {@link chess.core.Move#NO_MOVE} when the position is terminal
     */
    MoveChoice chooseMove(Position position, StrengthModel.Budget budget, long requestId);

    /**
     * Requests that any in-flight {@link #chooseMove} abandon its search as soon
     * as possible. Safe to call from any thread.
     */
    void cancel();

    /**
     * Releases any resources held by the opponent.
     */
    @Override
    default void close() {
        // default opponent holds no long-lived resources
    }

    /**
     * The opponent's decision for one position.
     *
     * @param move chosen move, or {@link chess.core.Move#NO_MOVE} when terminal
     * @param ranked root candidates in result order (best first); may be empty
     * @param centipawnsSideToMove root evaluation in centipawns from the side-to-move
     *        (i.e. opponent) perspective
     * @param principalVariation principal variation as SAN text, possibly empty
     */
    record MoveChoice(short move, List<RankedMove> ranked, int centipawnsSideToMove,
            String principalVariation) {

        /**
         * Normalizes the ranked list to an immutable copy.
         *
         * @param move chosen move
         * @param ranked root candidates
         * @param centipawnsSideToMove side-to-move evaluation in centipawns
         * @param principalVariation principal variation text
         */
        public MoveChoice {
            ranked = ranked == null ? List.of() : List.copyOf(ranked);
            principalVariation = principalVariation == null ? "" : principalVariation;
        }
    }

    /**
     * One ranked root candidate.
     *
     * <p>
     * The fields mirror the MCTS root statistics so a later strength model can
     * sample over them (temperature, top-k, value cutoff). In the budget-only
     * v1 the selection is a simple arg-max by {@code visits}.
     * </p>
     *
     * @param move candidate move
     * @param visits MCTS visit count for this edge
     * @param prior policy prior probability
     * @param q mean action value from the root side-to-move perspective
     */
    record RankedMove(short move, int visits, double prior, double q) {
    }
}
