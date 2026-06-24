package chess.describe;

import chess.core.Position;
import chess.engine.AlphaBeta;
import chess.engine.Limits;
import chess.engine.Result;
import chess.eval.Classical;

/**
 * Produces a real engine-search evaluation for a position description.
 *
 * <p>
 * The cheap static evaluation used by default reads the board "at a glance" and
 * can judge a tactical position falsely &mdash; calling a side winning that is
 * actually being mated, or missing a forced win. This runs a short alpha-beta
 * search (with the bundled, weight-free classical evaluator) so the description's
 * verdict reflects actual analysis rather than a static guess.
 * </p>
 *
 * <p>
 * The search is deterministic: a fresh transposition table, the default single
 * search thread, a fixed depth, and no wall-clock budget (only a node cap), so
 * identical positions yield identical evaluations across runs and machines.
 * </p>
 */
public final class EngineEvaluator {

    /**
     * Default search depth in plies. Deep enough for quiescence to resolve short
     * tactics, shallow enough to stay interactive.
     */
    public static final int DEFAULT_DEPTH = 10;

    /**
     * Visited-node cap that bounds the search while keeping it deterministic.
     */
    private static final long NODE_CAP = 1_500_000L;

    /**
     * Clamp magnitude used as the centipawn stand-in for a forced mate. It sits
     * well inside the decisive band and is never rendered as a pawn figure.
     */
    private static final int DECISIVE_CP = 30_000;

    /**
     * Prevents instantiation.
     */
    private EngineEvaluator() {
        // utility
    }

    /**
     * Evaluates a position with the default search depth.
     *
     * @param position chess position
     * @return engine-search evaluation
     */
    public static PositionDescriptionInput.Evaluation evaluate(Position position) {
        return evaluate(position, DEFAULT_DEPTH);
    }

    /**
     * Evaluates a position with the requested search depth.
     *
     * @param position chess position
     * @param depth search depth in plies, clamped to the supported range
     * @return engine-search evaluation
     */
    public static PositionDescriptionInput.Evaluation evaluate(Position position, int depth) {
        if (position == null) {
            throw new IllegalArgumentException("position == null");
        }
        int resolvedDepth = Math.max(1, Math.min(depth, AlphaBeta.MAX_DEPTH));
        // Search a copy so extraction state on the caller's position is untouched.
        Position copy = new Position(position.toString());
        AlphaBeta engine = new AlphaBeta(new Classical(), false);
        Result result = engine.search(copy, new Limits(resolvedDepth, NODE_CAP, 0L));
        boolean white = position.isWhiteToMove();
        String source = "engine-d" + Math.max(1, result.depth());
        if (result.isMateScore()) {
            int mateStm = result.mateIn();
            int mateWhite = white ? mateStm : -mateStm;
            int cpWhite = mateWhite > 0 ? DECISIVE_CP : -DECISIVE_CP;
            int cpSide = white ? cpWhite : -cpWhite;
            return new PositionDescriptionInput.Evaluation(source, cpWhite, cpSide, null, mateWhite);
        }
        int cpSide = result.scoreCentipawns();
        int cpWhite = white ? cpSide : -cpSide;
        return new PositionDescriptionInput.Evaluation(source, cpWhite, cpSide, null, 0);
    }
}
