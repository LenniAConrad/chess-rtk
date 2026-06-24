package application.gui.workbench.play;

import application.gui.workbench.mcts.MctsSearch;
import application.gui.workbench.network.RealActivations;
import application.gui.workbench.play.Opponent.Network;
import chess.core.Move;
import chess.core.Position;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * MCTS {@link Opponent}: PUCT policy/value tree search (lc0-style), paired with
 * a selectable {@link Network}.
 *
 * <p>
 * One {@link MctsSearch} is kept for the life of the opponent (a fresh opponent
 * is built per game) and re-rooted to the new position each move via
 * {@link MctsSearch#reuseRoot}, so visit statistics accumulated for the line
 * actually played carry over — a free strength gain, the MCTS analogue of a
 * persistent transposition table. When the new position is not already in the
 * tree, {@code reuseRoot} rebuilds from scratch. The classical backend is
 * weightless and mate/draw-correct at any budget (the immediate-root-decision
 * path), so even a single-playout search will not hang a mate in one. A neural
 * network that fails to load falls back to the classical backend.
 * </p>
 *
 * <p>
 * The per-move budget is enforced here rather than via the search's own clock:
 * because the tree persists, the search's internal {@code elapsedMillis} spans
 * the whole game, so this opponent times each move independently and bounds
 * playouts relative to the reused subtree's existing visits. {@link #cancel()}
 * flips a volatile flag the iteration loop polls, so a superseding request
 * abandons the search promptly.
 * </p>
 */
public final class MctsOpponent implements Opponent {

    /**
     * Selected policy/value network.
     */
    private final Network network;

    /**
     * Persistent search reused across the game's moves, or null before the first
     * move and after {@link #close()}.
     */
    private MctsSearch search;

    /**
     * Exploration constant the persistent search was built with. The strength
     * slider (hence cpuct) is fixed for a game, but if it ever differs the search
     * is rebuilt rather than silently using a stale constant.
     */
    private double searchCpuct;

    /**
     * Set asynchronously by {@link #cancel()} to abort the active search.
     */
    private volatile boolean cancelled;

    /**
     * Creates an MCTS opponent using the given policy/value network.
     *
     * @param network selected network
     */
    public MctsOpponent(Network network) {
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
        MctsSearch active = searchFor(root, budget.cpuct());
        // Carry over the reused subtree's visits: target a fixed number of NEW
        // playouts this move, and time the move on our own clock since the
        // search's internal timer spans the whole (persistent) tree's life.
        long playoutTarget = active.playouts() + Math.max(1, budget.maxPlayouts());
        long deadline = System.nanoTime() + Math.max(1L, budget.maxMillis()) * 1_000_000L;
        while (!cancelled
                && !Thread.currentThread().isInterrupted()
                && active.shouldContinue(playoutTarget, 0L)
                && System.nanoTime() < deadline) {
            active.iterate();
        }
        MctsSearch.Snapshot snapshot = active.snapshot(false);
        List<RankedMove> ranked = new ArrayList<>();
        for (MctsSearch.Row row : snapshot.rows()) {
            ranked.add(new RankedMove(row.move(), row.visits(), row.prior(), row.q()));
        }
        // The snapshot's bestMove() is authoritative — it accounts for proven
        // mates and draws that the visit-ordered rows may not surface first.
        // Pin it to the front so a budget-only arg-max selection plays it and
        // never hangs a forced mate even at one playout.
        promoteBestMove(ranked, snapshot.bestMove());
        return new MoveChoice(snapshot.bestMove(), ranked,
                snapshot.rootCentipawns(), snapshot.bestPvText());
    }

    /**
     * Returns the persistent search re-rooted to {@code root}, rebuilding it when
     * absent or when the exploration constant changed.
     *
     * @param root current position
     * @param cpuct exploration constant from the budget
     * @return a search rooted at {@code root}
     */
    private MctsSearch searchFor(Position root, double cpuct) {
        if (search != null && cpuct == searchCpuct) {
            search.reuseRoot(root);
            return search;
        }
        if (search != null) {
            search.close();
        }
        search = createSearch(root, cpuct);
        searchCpuct = cpuct;
        return search;
    }

    /**
     * Builds an MCTS search for the selected network, falling back to the
     * weightless classical backend when a neural network cannot be loaded.
     *
     * @param root root position
     * @param cpuct exploration constant from the budget
     * @return a search ready to iterate
     */
    private MctsSearch createSearch(Position root, double cpuct) {
        try {
            return switch (network) {
                case NNUE -> MctsSearch.nnue(root, cpuct, weightsPath(Network.NNUE));
                case CNN -> MctsSearch.cnn(root, cpuct, RealActivations.cnnPath());
                case OTIS -> MctsSearch.otis(root, cpuct, RealActivations.otisPath());
                default -> new MctsSearch(root, cpuct);
            };
        } catch (RuntimeException | java.io.IOException ex) {
            return new MctsSearch(root, cpuct);
        }
    }

    /**
     * Returns the weights path for a network via the shared {@link Networks} map.
     *
     * @param network network model
     * @return weights path
     */
    private static Path weightsPath(Network network) {
        return Networks.weightsPath(network);
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
        if (search != null) {
            search.close();
            search = null;
        }
    }

    /**
     * Moves the authoritative best move to the front of the ranked list so the
     * strength model's arg-max selection plays it.
     *
     * @param ranked ranked candidates (mutable)
     * @param best authoritative best move
     */
    private static void promoteBestMove(List<RankedMove> ranked, short best) {
        if (best == Move.NO_MOVE || ranked.isEmpty() || ranked.get(0).move() == best) {
            return;
        }
        for (int i = 1; i < ranked.size(); i++) {
            if (ranked.get(i).move() == best) {
                ranked.add(0, ranked.remove(i));
                return;
            }
        }
    }
}
