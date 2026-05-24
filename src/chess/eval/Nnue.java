package chess.eval;

import java.io.IOException;
import java.nio.file.Path;

import chess.core.Position;

/**
 * NNUE centipawn evaluator backed by {@link chess.nn.nnue.Model}.
 *
 * <p>
 * A missing default model is tolerated when no explicit weights are requested:
 * the model wrapper supplies a tiny neutral fallback so the in-house engine can
 * still run smoke tests and deterministic CLI checks.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
public final class Nnue implements CentipawnEvaluator {

    /**
     * Loaded NNUE model used for all leaf evaluations.
     */
    private final chess.nn.nnue.Model model;

    /**
     * Creates an evaluator from a weights path.
     *
     * @param weights weights path, or null for the default/fallback model
     * @throws IOException if the model cannot be loaded
     */
    public Nnue(Path weights) throws IOException {
        this(weights == null ? chess.nn.nnue.Model.loadDefaultOrFallback() : chess.nn.nnue.Model.load(weights));
    }

    /**
     * Creates an evaluator from an already loaded model.
     *
     * @param model loaded model
     * @throws IllegalArgumentException if the model is null
     */
    public Nnue(chess.nn.nnue.Model model) {
        if (model == null) {
            throw new IllegalArgumentException("model == null");
        }
        this.model = model;
    }

    /**
     * Evaluates one position.
     *
     * @param position position to evaluate
     * @return NNUE centipawns from the side-to-move perspective
     */
    @Override
    public int evaluate(Position position) {
        return model.evaluateCentipawns(position);
    }

    /**
     * Opens optional per-search NNUE incremental state.
     *
     * @param root root position
     * @param searchPlies maximum ply count
     * @return incremental search state, or {@code null} when unsupported
     */
    @Override
    public SearchState openSearchState(Position root, int searchPlies) {
        return adaptSearchState(model.newSearchState(root, searchPlies));
    }

    /**
     * Converts an optional model search state into the evaluator contract.
     *
     * @param state model search state, or null when unsupported
     * @return evaluator search state, or null
     */
    private static SearchState adaptSearchState(chess.nn.nnue.Model.SearchState state) {
        return state == null ? null : new NnueSearchState(state);
    }

    /**
     * Adapts the model-level search state to the evaluator interface.
     */
    private static final class NnueSearchState implements SearchState {

        /**
         * Wrapped NNUE model state.
         */
        private final chess.nn.nnue.Model.SearchState state;

        /**
         * Creates an adapter.
         *
         * @param state wrapped state
         */
        private NnueSearchState(chess.nn.nnue.Model.SearchState state) {
            this.state = state;
        }

        /**
         * Forwards a played-move notification to the model search state.
         *
         * @param position child position after the move
         * @param move encoded move that was played
         * @param undoState undo state filled by the move application
         * @param ply child ply from the root
         */
        @Override
        public void movePlayed(Position position, short move, Position.State undoState, int ply) {
            state.movePlayed(position, move, undoState, ply);
        }

        /**
         * Forwards a null-move notification to the model search state.
         *
         * @param ply child ply from the root
         */
        @Override
        public void nullMovePlayed(int ply) {
            state.nullMovePlayed(ply);
        }

        /**
         * Evaluates a position through the model search state.
         *
         * @param position current position
         * @param ply current ply from the root
         * @return NNUE centipawns from the side-to-move perspective
         */
        @Override
        public int evaluate(Position position, int ply) {
            return state.evaluate(position, ply);
        }

        /**
         * Releases the wrapped model search state.
         */
        @Override
        public void close() {
            state.close();
        }
    }

    /**
     * Returns the evaluator label.
     *
     * @return label including the active NNUE backend
     */
    @Override
    public String name() {
        return Kind.NNUE.label() + "(" + model.backend() + ")";
    }

    /**
     * Releases model resources.
     */
    @Override
    public void close() {
        model.close();
    }
}
