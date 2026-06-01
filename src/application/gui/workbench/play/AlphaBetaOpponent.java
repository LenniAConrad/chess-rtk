package application.gui.workbench.play;

import application.gui.workbench.play.Opponent.Network;
import chess.core.Move;
import chess.core.Position;
import chess.engine.AlphaBeta;
import chess.engine.Limits;
import chess.engine.Result;
import java.util.List;

/**
 * Alpha-beta {@link Opponent}: iterative-deepening minimax with a transposition
 * table, null-move pruning, and aspiration windows (Stockfish-style search),
 * paired with a selectable static {@link Network}.
 *
 * <p>
 * The classical network needs no weights and is always available; the NNUE/CNN/
 * OTIS networks load their weights and play materially stronger positional chess
 * (in self-play at equal time the NNUE search scored 2 wins and a draw over 3
 * games with no losses against classical). If a neural network cannot be loaded
 * the opponent falls back to the classical evaluator, so it is always usable.
 * </p>
 *
 * <p>
 * Strength scales with the {@link StrengthModel} depth derived from the Elo
 * slider and a per-move think time. Alpha-beta returns a single best line, not a
 * ranked candidate list, so the strength model's move-sampling layer does not
 * apply; weakening comes from the reduced depth and think time, and the single
 * move is reported as the sole ranked candidate.
 * </p>
 */
public final class AlphaBetaOpponent implements Opponent {

    /**
     * Centipawn magnitude used to display a forced mate, matching the workbench
     * eval-bar convention.
     */
    private static final int MATE_DISPLAY_CP = 3000;

    /**
     * Per-move think time at minimum strength, in milliseconds.
     */
    private static final long MIN_THINK_MILLIS = 300L;

    /**
     * Per-move think time at maximum strength, in milliseconds. Depth is the
     * strength ceiling; this time governor keeps moves responsive. The ceiling
     * is generous because the neural evaluators search far fewer nodes per second
     * than the classical eval, so they need more wall-clock to reach a strong
     * depth. Iterative deepening returns the best line completed in time.
     */
    private static final long MAX_THINK_MILLIS = 6_000L;

    /**
     * Maximum Lazy SMP search threads used at the top strength setting. Capped to
     * keep a core free and because the measured gain saturates around here.
     */
    private static final int SMP_MAX_THREADS = 8;

    /**
     * Selected static-evaluation network.
     */
    private final Network network;

    /**
     * Reused searcher, created lazily so the (possibly slow) network load happens
     * off the UI thread on the first move. {@link AlphaBeta} keeps a transposition
     * table that is safe and beneficial to reuse across moves of one game.
     */
    private AlphaBeta engine;

    /**
     * Set asynchronously by {@link #cancel()} to abort the active search at the
     * search's next stop check.
     */
    private volatile boolean cancelled;

    /**
     * Creates an alpha-beta opponent using the given evaluation network.
     *
     * @param network static-evaluation network
     */
    public AlphaBetaOpponent(Network network) {
        this.network = network;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MoveChoice chooseMove(Position position, StrengthModel.Budget budget, long requestId) {
        cancelled = false;
        Position root = position.copy();
        if (root.legalMoves().isEmpty()) {
            return new MoveChoice(Move.NO_MOVE, List.of(), 0, "");
        }
        AlphaBeta active = engine(budget.targetElo());
        // At the top strength setting, use Lazy SMP across cores for a materially
        // deeper time-bounded search (helper threads diversify via depth-skip and
        // share the transposition table). Below max we stay single-threaded so the
        // Elo-calibrated weakening — and reproducibility — are unaffected.
        int threads = budget.targetElo() >= StrengthModel.MAX_ELO
                ? Math.max(1, Math.min(SMP_MAX_THREADS, Runtime.getRuntime().availableProcessors() - 1))
                : 1;
        active.setSearchThreads(threads);
        // Depth is the strength ceiling; an Elo-scaled think time keeps moves
        // responsive (a deep search at mid strength would otherwise run many
        // seconds). Iterative deepening returns the deepest line finished in
        // time. The think time is bounded by the budget's own safety cap and
        // lets cancel() take effect at the next stop check.
        long think = Math.min(StrengthModel.thinkMillis(budget.targetElo(), MIN_THINK_MILLIS, MAX_THINK_MILLIS),
                budget.maxMillis());
        Limits limits = new Limits(
                Math.max(1, Math.min(AlphaBeta.MAX_DEPTH, budget.depth())),
                Limits.DEFAULT_MAX_NODES * 8,
                think);
        Result result = active.search(root, limits, completed -> {
            if (cancelled) {
                Thread.currentThread().interrupt();
            }
        });
        short move = result.bestMove();
        if (move == Move.NO_MOVE) {
            return new MoveChoice(Move.NO_MOVE, List.of(), 0, "");
        }
        // AlphaBeta scores from the root side-to-move perspective, which is the
        // opponent here, so the centipawn value maps straight onto MoveChoice —
        // except mate sentinels (~900k), which are clamped so the eval bar stays
        // on a sane scale.
        return new MoveChoice(move,
                List.of(new RankedMove(move, 1, 1.0, 0.0)),
                clampScore(result),
                result.scoreLabel());
    }

    /**
     * Maps a search result's score to a bounded centipawn value, collapsing mate
     * sentinels onto a fixed magnitude.
     *
     * @param result search result
     * @return bounded centipawn value from the side-to-move perspective
     */
    private static int clampScore(Result result) {
        if (result.isMateScore()) {
            return result.scoreCentipawns() > 0 ? MATE_DISPLAY_CP : -MATE_DISPLAY_CP;
        }
        return Math.max(-MATE_DISPLAY_CP, Math.min(MATE_DISPLAY_CP, result.scoreCentipawns()));
    }

    /**
     * Returns the search engine, creating it on first use with the evaluator for
     * the selected network (classical fallback handled by {@link Networks}). At
     * the top strength setting it additionally enables the depth-scaling search
     * features (LMR table + check extensions), which self-play measured as a
     * combined ~+32 Elo at deep search but neutral/negative when shallow — so
     * lower strengths keep the calibrated default feature set. The Play game's
     * Elo is fixed, so the first move's target Elo determines the configuration.
     *
     * @param targetElo target Elo of the game (from the first move's budget)
     * @return the alpha-beta searcher
     */
    private AlphaBeta engine(int targetElo) {
        if (engine == null) {
            // Persist the transposition table across the game's moves: positions
            // analysed last move are often revisited, so carrying the table over
            // is a free strength gain. A fresh opponent is built per game, so the
            // table is naturally scoped to one game.
            if (targetElo >= StrengthModel.MAX_ELO) {
                engine = new AlphaBeta(Networks.evaluator(network), true, java.util.EnumSet.of(
                        AlphaBeta.Feature.SEE_PRUNING,
                        AlphaBeta.Feature.SEARCH_REPETITION,
                        AlphaBeta.Feature.LMR_TABLE,
                        AlphaBeta.Feature.CHECK_EXTENSION));
            } else {
                engine = new AlphaBeta(Networks.evaluator(network), true);
            }
        }
        return engine;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cancel() {
        cancelled = true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        if (engine != null) {
            engine.close();
            engine = null;
        }
    }
}
