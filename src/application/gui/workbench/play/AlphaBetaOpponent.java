package application.gui.workbench.play;

import application.gui.workbench.play.Opponent.Network;
import application.gui.workbench.play.Opponent.RankedMove;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Position;
import chess.engine.AlphaBeta;
import chess.engine.Limits;
import chess.engine.Result;
import java.util.ArrayList;
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
 * slider, a per-move think time, and — below the top setting — the strength
 * model's move-sampling weakening. Alpha-beta natively returns only the best
 * line, so when the Elo curve calls for sampling
 * ({@link StrengthModel#weakensAt(int)}) the root moves are ranked by a fast
 * shallow pass and reported as a candidate list; {@link StrengthModel#select}
 * then draws an Elo-appropriate (often inferior) move from it, exactly as for
 * the MCTS opponent. Without this the depth/time ceiling was the only lever, and
 * a shallow NNUE search still played far above its Elo label. At the top setting
 * (or any deterministic profile) selection collapses to the arg-max best move.
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
     * Depth of the per-root-move ranking search used to drive strength sampling:
     * one ply plus quiescence. Deep enough to be tactically aware (a candidate
     * that hangs a piece to a capture is scored as such) yet cheap enough to run
     * over every legal move without a perceptible pause.
     */
    private static final int RANK_DEPTH = 1;

    /**
     * Node cap per ranking search; a generous backstop that a one-ply search
     * never approaches in a normal position.
     */
    private static final long RANK_MAX_NODES = 20_000L;

    /**
     * Wall-clock cap per ranking search, guarding against a pathologically slow
     * position; the shallow depth binds long before this in practice.
     */
    private static final long RANK_MAX_MILLIS = 60L;

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
        MoveList legal = root.legalMoves();
        if (legal.isEmpty()) {
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
        int bestCp = clampScore(result);
        // Below the top strength the strength model weakens play by sampling
        // among ranked candidates (PlaySession applies StrengthModel.select).
        // Alpha-beta yields only the best line, so when the Elo curve calls for
        // sampling we rank the root moves with a fast shallow pass purely to give
        // the sampler something to draw from. Otherwise the single best move is
        // the sole candidate and selection is a no-op arg-max.
        List<RankedMove> ranked = legal.size() > 1 && StrengthModel.weakensAt(budget.targetElo())
                ? rankRootMoves(active, root, legal, move, bestCp)
                : List.of(new RankedMove(move, 1, 1.0, winrateValue(bestCp)));
        return new MoveChoice(move, ranked, bestCp, result.scoreLabel());
    }

    /**
     * Ranks every legal root move with a fast shallow search so the strength
     * model can sample an Elo-appropriate move. Each child position is searched
     * to {@link #RANK_DEPTH} (one ply plus quiescence — cheap but tactically
     * aware, so a candidate that simply hangs material is scored accordingly) and
     * the resulting score is mapped onto the same value scale ({@code q} in
     * roughly [-1, 1]) the MCTS opponent uses, so the strength model's cutoffs
     * carry over unchanged. The deep best move from the full search is pinned to
     * the front as the authoritative arg-max; the shallow scores only order the
     * pool the sampler picks from. Runs single-threaded and honors {@link #cancel}.
     *
     * @param active reused searcher (transposition table shared across children)
     * @param root position to move in, side to move is the opponent
     * @param legal legal moves at the root
     * @param bestMove deep best move to pin to the front of the ranking
     * @param bestCp deep score of {@code bestMove}, used as its value directly
     * @return ranked candidates, best move first
     */
    private List<RankedMove> rankRootMoves(AlphaBeta active, Position root, MoveList legal,
            short bestMove, int bestCp) {
        active.setSearchThreads(1);
        Limits shallow = new Limits(RANK_DEPTH, RANK_MAX_NODES, RANK_MAX_MILLIS);
        List<RankedMove> ranked = new ArrayList<>(legal.size());
        for (int i = 0; i < legal.size() && !cancelled; i++) {
            short candidate = legal.get(i);
            int cp;
            if (candidate == bestMove) {
                // The full search already scored the best move accurately; use that
                // rather than a shallow re-estimate, so it reliably leads the pool.
                cp = bestCp;
            } else {
                Position child = root.copy();
                child.play(candidate);
                if (child.legalMoves().isEmpty()) {
                    // Terminal immediately after our move: our checkmate (best) or
                    // a stalemate (a draw, a blunder from a winning position).
                    cp = child.inCheck() ? MATE_DISPLAY_CP : 0;
                } else {
                    Result reply = active.search(child, shallow, completed -> {
                        if (cancelled) {
                            Thread.currentThread().interrupt();
                        }
                    });
                    // The child's score is from the opponent's perspective; negate
                    // it to rank from our (root side-to-move) perspective.
                    cp = -clampScore(reply);
                }
            }
            double q = winrateValue(cp);
            // A strictly positive, monotonic weight so the sampler favors better
            // moves within the value-bounded pool even in a losing position.
            double prior = Math.max(1e-3, (q + 1.0) / 2.0);
            ranked.add(new RankedMove(candidate, 1, prior, q));
        }
        if (ranked.isEmpty()) {
            // Cancelled before any move was scored: fall back to the best move.
            return List.of(new RankedMove(bestMove, 1, 1.0, 0.0));
        }
        ranked.sort((a, b) -> Double.compare(b.q(), a.q()));
        promoteBestMove(ranked, bestMove);
        return ranked;
    }

    /**
     * Moves {@code bestMove} to the front of the ranking so a deterministic or
     * arg-max selection plays the full search's best move rather than the
     * shallow-pass leader. A no-op when it is already first or absent.
     *
     * @param ranked ranked candidates, mutated in place
     * @param bestMove move to pin to the front
     */
    private static void promoteBestMove(List<RankedMove> ranked, short bestMove) {
        for (int i = 1; i < ranked.size(); i++) {
            if (ranked.get(i).move() == bestMove) {
                ranked.add(0, ranked.remove(i));
                return;
            }
        }
    }

    /**
     * Maps a centipawn score to a mean action value {@code q} in [-1, 1] using the
     * same soft WDL curve the MCTS search reports, so the strength model's q-unit
     * cutoffs apply identically to both opponents.
     *
     * @param centipawns score from the side-to-move perspective
     * @return expected value in [-1, 1]
     */
    private static double winrateValue(int centipawns) {
        double clamped = Math.max(-4000.0, Math.min(4000.0, centipawns));
        double win = sigmoid((clamped - 180.0) / 420.0);
        double loss = sigmoid((-clamped - 180.0) / 420.0);
        double draw = Math.max(0.0, 1.0 - win - loss);
        double sum = win + draw + loss;
        return sum > 0.0 ? (win - loss) / sum : 0.0;
    }

    /**
     * Numerically safe logistic sigmoid.
     *
     * @param x unbounded input
     * @return value in [0, 1]
     */
    private static double sigmoid(double x) {
        if (x > 20.0) {
            return 1.0;
        }
        if (x < -20.0) {
            return 0.0;
        }
        return 1.0 / (1.0 + Math.exp(-x));
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
            // table is naturally scoped to one game. The default feature set is the
            // strongest measured configuration, so every tier uses it; the Max tier
            // additionally turns on Lazy SMP (handled by setSearchThreads).
            engine = new AlphaBeta(Networks.evaluator(network), true);
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
