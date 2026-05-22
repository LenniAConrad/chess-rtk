/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package chess.engine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import chess.classical.Wdl;
import chess.core.Move;
import chess.core.MoveList;
import chess.core.Piece;
import chess.core.Position;
import chess.eval.CentipawnEvaluator;
import chess.eval.Classical;

/**
 * Built-in PUCT Monte Carlo tree searcher.
 *
 * <p>
 * The search stores a reusable tree plus a bounded hash table of shared
 * position statistics. Leaf values use WDL probabilities, while move priors are
 * derived from evaluator move-ordering hooks and cheap tactical features.
 * </p>
 *
 * @since 2026
 * @author Lennart A. Conrad
 */
@SuppressWarnings({"java:S135", "squid:S135"})
public final class Mcts implements AutoCloseable {

    /**
     * Default bounded transposition-stat table size.
     */
    private static final int DEFAULT_TRANSPOSITION_LIMIT = 1 << 18;

    /**
     * Default PUCT exploration constant.
     */
    private static final double DEFAULT_CPUCT = 1.4;

    /**
     * First-play urgency reduction for unvisited children.
     */
    private static final double FPU_REDUCTION = 0.25;

    /**
     * Upper bound for one tree line.
     */
    private static final int MAX_SEARCH_PLY = 256;

    /**
     * Maximum forcing plies searched when valuing tactically unstable leaves.
     */
    private static final int QUIESCENCE_MAX_PLY = 4;

    /**
     * Maximum non-evasion forcing moves searched at one quiescence node.
     */
    private static final int QUIESCENCE_MAX_MOVES = 12;

    /**
     * Maximum PV moves included in the public result.
     */
    private static final int MAX_RESULT_PV = 32;

    /**
     * Maximum nodes scanned when re-rooting to a deeper descendant.
     */
    private static final int MAX_REUSE_SCAN_NODES = 200_000;

    /**
     * Default leaf batch size.
     */
    private static final int DEFAULT_BATCH_SIZE = 1;

    /**
     * Policy/value backend.
     */
    private final SearchBackend backend;

    /**
     * Coordinates shared-tree updates for worker searches.
     */
    private final Object treeLock = new Object();

    /**
     * Bounded hash table keyed by full position signature.
     */
    private Map<Long, Stats> transpositions;

    /**
     * PUCT exploration constant.
     */
    private double cpuct;

    /**
     * Worker thread count for MCTS search.
     */
    private int threads = 1;

    /**
     * Leaf batch size for single-threaded search.
     */
    private int batchSize = DEFAULT_BATCH_SIZE;

    /**
     * Root position for reusable searches.
     */
    private Position rootPosition;

    /**
     * Current tree root.
     */
    private Node root;

    /**
     * Core-position occurrence counts before the current root.
     */
    private Map<Long, Integer> rootHistory;

    /**
     * Completed playout count in the current root search.
     */
    private long playouts;

    /**
     * Deepest selected node in the current search.
     */
    private int maxDepthReached;

    /**
     * External stop flag used by UCI stop.
     */
    private volatile boolean stopRequested;

    /**
     * Whether a search is currently inside the playout loop.
     */
    private volatile boolean searching;

    /**
     * Creates a classical MCTS searcher.
     */
    public Mcts() {
        this(new Classical(), DEFAULT_CPUCT);
    }

    /**
     * Creates an evaluator-backed MCTS searcher.
     *
     * @param evaluator centipawn evaluator used for values and move priors
     */
    public Mcts(CentipawnEvaluator evaluator) {
        this(evaluator, DEFAULT_CPUCT);
    }

    /**
     * Creates an evaluator-backed MCTS searcher.
     *
     * @param evaluator centipawn evaluator used for values and move priors
     * @param cpuct PUCT exploration constant
     */
    public Mcts(CentipawnEvaluator evaluator, double cpuct) {
        this(new EvaluatorBackend(requireEvaluator(evaluator)), cpuct);
    }

    /**
     * Creates an LC0 CNN policy/value-backed MCTS searcher.
     *
     * @param weights LC0 CNN weights path
     * @return policy/value-backed searcher
     * @throws IOException if weights cannot be loaded
     */
    public static Mcts lc0(Path weights) throws IOException {
        Path resolved = weights == null ? chess.nn.lc0.cnn.Model.DEFAULT_WEIGHTS : weights;
        return new Mcts(new CnnBackend(chess.nn.lc0.cnn.Model.load(resolved)), DEFAULT_CPUCT);
    }

    /**
     * Creates an LC0 BT4 policy/value-backed MCTS searcher.
     *
     * @param weights BT4 weights path
     * @return policy/value-backed searcher
     * @throws IOException if weights cannot be loaded
     */
    public static Mcts bt4(Path weights) throws IOException {
        if (weights == null) {
            throw new IllegalArgumentException("weights == null");
        }
        return new Mcts(new Bt4Backend(chess.nn.lc0.bt4.Network.load(weights)), DEFAULT_CPUCT);
    }

    /**
     * Creates a searcher around a policy/value backend.
     * @param backend backend value
     * @param cpuct cpuct value
     */
    private Mcts(SearchBackend backend, double cpuct) {
        if (backend == null) {
            throw new IllegalArgumentException("backend == null");
        }
        this.backend = backend;
        this.cpuct = Math.max(0.05, cpuct);
        this.transpositions = newTranspositionTable(DEFAULT_TRANSPOSITION_LIMIT);
        this.rootHistory = Map.of();
    }

    /**
     * Validates an evaluator before constructor delegation.
     * @param evaluator evaluator value
     * @return require evaluator result
     */
    private static CentipawnEvaluator requireEvaluator(CentipawnEvaluator evaluator) {
        if (evaluator == null) {
            throw new IllegalArgumentException("evaluator == null");
        }
        return evaluator;
    }

    /**
     * Searches a position with a fresh root tree.
     *
     * @param position root position
     * @param limits resource limits
     * @return search result
     */
    public Result search(Position position, Limits limits) {
        return search(position, limits, null);
    }

    /**
     * Searches a position with a fresh root tree.
     *
     * @param position root position
     * @param limits resource limits
     * @param listener optional search-info listener
     * @return search result
     */
    public Result search(Position position, Limits limits, SearchListener listener) {
        return search(position, limits, listener, false);
    }

    /**
     * Searches a position while attempting to reuse an existing child subtree.
     *
     * @param position root position
     * @param limits resource limits
     * @param listener optional search-info listener
     * @return search result
     */
    public Result searchReusable(Position position, Limits limits, SearchListener listener) {
        return search(position, limits, listener, true, null);
    }

    /**
     * Searches a position with known pre-root repetition history.
     *
     * @param position root position
     * @param limits resource limits
     * @param listener optional search-info listener
     * @param historyCoreKeys core position keys before the root, excluding the
     *        root itself
     * @return search result
     */
    public Result searchReusable(
            Position position,
            Limits limits,
            SearchListener listener,
            long[] historyCoreKeys) {
        return search(position, limits, listener, true, historyCoreKeys);
    }

    /**
     * Requests that an active search stop at the next playout boundary.
     */
    public void requestStop() {
        stopRequested = true;
    }

    /**
     * Returns whether this searcher is actively searching.
     *
     * @return true while a search call is active
     */
    public boolean isSearching() {
        return searching;
    }

    /**
     * Clears the reusable tree and transposition table.
     */
    public void reset() {
        rootPosition = null;
        root = null;
        playouts = 0L;
        maxDepthReached = 0;
        stopRequested = false;
        rootHistory = Map.of();
        transpositions.clear();
    }

    /**
     * Updates the hash table capacity.
     *
     * @param hashMegabytes approximate memory target in MiB
     */
    public void setHashMegabytes(int hashMegabytes) {
        int mb = Math.max(1, hashMegabytes);
        int buckets = Math.max(1024, Math.min(1 << 24, (mb * 1024 * 1024) / 64));
        transpositions = newTranspositionTable(buckets);
        root = null;
        rootPosition = null;
    }

    /**
     * Updates the PUCT exploration constant.
     *
     * @param value new exploration constant
     */
    public void setCpuct(double value) {
        cpuct = Math.max(0.05, value);
    }

    /**
     * Updates the MCTS worker thread count.
     *
     * @param value requested worker count
     */
    public void setThreads(int value) {
        threads = Math.max(1, value);
    }

    /**
     * Returns the configured worker thread count.
     *
     * @return worker threads
     */
    public int threads() {
        return threads;
    }

    /**
     * Updates the single-thread leaf batch size.
     *
     * @param value requested batch size
     */
    public void setBatchSize(int value) {
        batchSize = Math.max(1, value);
    }

    /**
     * Returns the backend evaluator label.
     *
     * @return evaluator name
     */
    public String evaluatorName() {
        return backend.name();
    }

    /**
     * Releases evaluator resources.
     */
    @Override
    public void close() {
        backend.close();
    }

    /**
     * Main search entry point.
     * @param position chess position
     * @param limits search limits
     * @param listener event listener
     * @param reuseTree true to reuse the existing search tree
     * @return search result
     */
    private Result search(Position position, Limits limits, SearchListener listener, boolean reuseTree) {
        return search(position, limits, listener, reuseTree, null);
    }

    /**
     * Main search entry point.
     * @param position chess position
     * @param limits search limits
     * @param listener event listener
     * @param reuseTree true to reuse the existing search tree
     * @param historyCoreKeys history core keys value
     * @return search result
     */
    private Result search(
            Position position,
            Limits limits,
            SearchListener listener,
            boolean reuseTree,
            long[] historyCoreKeys) {
        if (position == null) {
            throw new IllegalArgumentException("position == null");
        }
        if (limits == null) {
            throw new IllegalArgumentException("limits == null");
        }

        stopRequested = false;
        searching = true;
        try {
            long started = System.currentTimeMillis();
            setRoot(position, reuseTree, historyCoreKeys);
            Result immediate = immediateRootResult(started);
            if (immediate != null) {
                notifySearch(listener, immediate);
                return immediate;
            }

            long maxNodes = effectiveMaxNodes(limits);
            long maxMillis = effectiveMaxMillis(limits);
            Result best = currentResult(started, false);
            if (!shouldContinue(started, maxNodes, maxMillis)) {
                Result stopped = currentResult(started, true);
                notifySearch(listener, stopped);
                return stopped;
            }

            if (threads > 1) {
                return searchParallel(started, maxNodes, maxMillis, listener);
            }

            long nextReport = 1L;
            while (shouldContinue(started, maxNodes, maxMillis)) {
                int count = nextBatchCount(maxNodes);
                if (count <= 1) {
                    iterate();
                } else {
                    iterateBatch(count);
                }
                if (playouts >= nextReport) {
                    best = currentResult(started, false);
                    notifySearch(listener, best);
                    nextReport = nextReport(playouts);
                }
            }
            boolean stopped = stopRequested || maxNodes > 0L && playouts >= maxNodes
                    || maxMillis > 0L && elapsedSince(started) >= maxMillis;
            best = currentResult(started, stopped);
            notifySearch(listener, best);
            return best;
        } finally {
            searching = false;
        }
    }

    /**
     * Returns the next serial batch size under the node budget.
     * @param maxNodes maximum node count
     * @return next batch count result
     */
    private int nextBatchCount(long maxNodes) {
        int count = Math.max(1, batchSize);
        if (maxNodes > 0L) {
            long remaining = Math.max(0L, maxNodes - playouts);
            count = (int) Math.max(1L, Math.min(count, remaining));
        }
        return count;
    }

    /**
     * Returns the effective visit budget.
     * @param limits search limits
     * @return effective max nodes result
     */
    private static long effectiveMaxNodes(Limits limits) {
        if (limits.maxNodes() > 0L) {
            return limits.maxNodes();
        }
        if (limits.maxDurationMillis() > 0L) {
            return 0L;
        }
        return Math.max(256L, limits.depth() * 1024L);
    }

    /**
     * Returns the effective time budget.
     * @param limits search limits
     * @return effective max millis result
     */
    private static long effectiveMaxMillis(Limits limits) {
        return limits.maxDurationMillis();
    }

    /**
     * Returns the next playout count at which an info line should be emitted.
     * @param current current value
     * @return next report result
     */
    private static long nextReport(long current) {
        if (current < 1024L) {
            return current * 2L;
        }
        return current + 1024L;
    }

    /**
     * Returns whether another playout may begin.
     * @param started start time
     * @param maxNodes maximum node count
     * @param maxMillis maximum runtime in milliseconds
     * @return true when should continue
     */
    private boolean shouldContinue(long started, long maxNodes, long maxMillis) {
        if (stopRequested) {
            return false;
        }
        if (root != null && root.proof != ProofState.UNKNOWN) {
            return false;
        }
        boolean visitsOk = maxNodes <= 0L || playouts < maxNodes;
        boolean timeOk = maxMillis <= 0L || elapsedSince(started) < maxMillis;
        return visitsOk && timeOk;
    }

    /**
     * Parallel worker search over one shared tree.
     * @param started start time
     * @param maxNodes maximum node count
     * @param maxMillis maximum runtime in milliseconds
     * @param listener event listener
     * @return search parallel result
     */
    private Result searchParallel(long started, long maxNodes, long maxMillis, SearchListener listener) {
        int count = Math.max(1, threads);
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        Thread[] workers = new Thread[count];
        for (int i = 0; i < workers.length; i++) {
            workers[i] = new Thread(
                    () -> runWorker(started, maxNodes, maxMillis, failure),
                    "crtk-mcts-worker-" + (i + 1));
            workers[i].setDaemon(true);
            workers[i].start();
        }

        long nextReport = 1L;
        while (anyAlive(workers)) {
            sleepBriefly();
            RuntimeException ex = failure.get();
            if (ex != null) {
                requestStop();
            }
            synchronized (treeLock) {
                if (playouts >= nextReport) {
                    notifySearch(listener, currentResult(started, false));
                    nextReport = nextReport(playouts);
                }
            }
        }
        joinAll(workers);
        RuntimeException ex = failure.get();
        if (ex != null) {
            throw ex;
        }
        synchronized (treeLock) {
            boolean stopped = stopRequested || maxNodes > 0L && playouts >= maxNodes
                    || maxMillis > 0L && elapsedSince(started) >= maxMillis;
            Result result = currentResult(started, stopped);
            notifySearch(listener, result);
            return result;
        }
    }

    /**
     * Runs one worker loop.
     * @param started start time
     * @param maxNodes maximum node count
     * @param maxMillis maximum runtime in milliseconds
     * @param failure failure value
     */
    private void runWorker(
            long started,
            long maxNodes,
            long maxMillis,
            AtomicReference<RuntimeException> failure) {
        while (failure.get() == null) {
            LeafTask task;
            synchronized (treeLock) {
                if (!shouldContinue(started, maxNodes, maxMillis)) {
                    return;
                }
                task = selectLeaf();
                addVirtualLoss(task.path());
            }
            Evaluation value;
            try {
                value = evaluateTask(task);
            } catch (RuntimeException ex) {
                synchronized (treeLock) {
                    removeVirtualLoss(task.path());
                }
                failure.compareAndSet(null, ex);
                requestStop();
                return;
            }
            synchronized (treeLock) {
                completeTask(task, value);
            }
        }
    }

    /**
     * Returns whether any worker is still alive.
     * @param workers workers value
     * @return any alive result
     */
    private static boolean anyAlive(Thread[] workers) {
        for (Thread worker : workers) {
            if (worker.isAlive()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Waits briefly between worker status checks.
     */
    private static void sleepBriefly() {
        try {
            Thread.sleep(5L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Joins all workers.
     * @param workers workers value
     */
    private static void joinAll(Thread[] workers) {
        for (Thread worker : workers) {
            try {
                worker.join();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * Re-roots or rebuilds the tree.
     * @param position chess position
     * @param reuseTree true to reuse the existing search tree
     * @param historyCoreKeys history core keys value
     */
    private void setRoot(Position position, boolean reuseTree, long[] historyCoreKeys) {
        rootHistory = historyCounts(historyCoreKeys);
        Position copy = position.copy();
        if (reuseTree && root != null && root.key == copy.signature()) {
            rootPosition = copy;
            playouts = 0L;
            maxDepthReached = 0;
            return;
        }
        if (reuseTree && root != null) {
            long key = copy.signature();
            Node reused = findReusableRoot(key);
            if (reused != null) {
                reused.parent = null;
                reused.move = Move.NO_MOVE;
                rebaseDepths(reused, 0);
                root = reused;
                rootPosition = copy;
                playouts = 0L;
                maxDepthReached = 0;
                return;
            }
        }
        if (!reuseTree) {
            transpositions.clear();
        }
        rootPosition = copy;
        root = newNode(null, Move.NO_MOVE, copy.copy(), 1.0, 0);
        playouts = 0L;
        maxDepthReached = 0;
        expand(root, List.of(root));
    }

    /**
     * Finds a searched descendant to reuse as the new root.
     * @param key lookup key
     * @return find reusable root result
     */
    private Node findReusableRoot(long key) {
        if (root == null) {
            return null;
        }
        List<Node> queue = new ArrayList<>();
        queue.add(root);
        for (int i = 0; i < queue.size() && i < MAX_REUSE_SCAN_NODES; i++) {
            Node node = queue.get(i);
            if (node.key == key) {
                return node;
            }
            queue.addAll(node.children);
        }
        return null;
    }

    /**
     * Rebases cached subtree depths after re-rooting.
     * @param node node value
     * @param depth search depth
     */
    private static void rebaseDepths(Node node, int depth) {
        node.depth = depth;
        for (Node child : node.children) {
            rebaseDepths(child, depth + 1);
        }
    }

    /**
     * Builds occurrence counts for pre-root positions.
     * @param keys keys value
     * @return history counts result
     */
    private static Map<Long, Integer> historyCounts(long[] keys) {
        if (keys == null || keys.length == 0) {
            return Map.of();
        }
        Map<Long, Integer> counts = new HashMap<>();
        for (long key : keys) {
            counts.merge(key, 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Returns a terminal, draw, or tree-proven root result.
     * @param started start time
     * @return immediate root result result
     */
    private Result immediateRootResult(long started) {
        MoveList legal = rootPosition.legalMoves();
        if (legal.isEmpty()) {
            int score = rootPosition.inCheck() ? -AlphaBeta.MATE_SCORE : 0;
            return new Result(Move.NO_MOVE, score, 0, 0L, elapsedSince(started), false, new short[0]);
        }
        if (isDraw(rootPosition) || isRepetition(List.of(root))) {
            List<Node> ordered = orderedRootChildren();
            Node best = ordered.isEmpty() ? null : ordered.get(0);
            short bestMove = best == null ? legal.raw(0) : best.move;
            short[] pv = bestMove == Move.NO_MOVE ? new short[0] : new short[] { bestMove };
            return new Result(bestMove, 0, 0, playouts, elapsedSince(started), false, pv);
        }
        MateProver.Proof proof = MateProver.proveMate(rootPosition);
        if (proof != null) {
            return new Result(
                    proof.bestMove(),
                    AlphaBeta.MATE_SCORE - proof.plies(),
                    proof.plies(),
                    0L,
                    elapsedSince(started),
                    false,
                    proof.principalVariation());
        }
        if (root.proof != ProofState.UNKNOWN) {
            Node best = proofPreferredChild(root);
            short bestMove = best == null ? Move.NO_MOVE : best.move;
            short[] pv = best == null ? new short[0] : principalVariation(best, MAX_RESULT_PV);
            return new Result(
                    bestMove,
                    proofScore(root),
                    root.proofPlies,
                    playouts,
                    elapsedSince(started),
                    false,
                    pv);
        }
        return null;
    }

    /**
     * Runs one PUCT playout.
     */
    private void iterate() {
        LeafTask task = selectLeaf();
        addVirtualLoss(task.path());
        try {
            completeTask(task, evaluateTask(task));
        } catch (RuntimeException ex) {
            removeVirtualLoss(task.path());
            throw ex;
        }
    }

    /**
     * Runs a batch of playouts.
     * @param count item count
     */
    private void iterateBatch(int count) {
        List<LeafTask> tasks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            LeafTask task = selectLeaf();
            addVirtualLoss(task.path());
            tasks.add(task);
        }
        List<Evaluation> values;
        try {
            values = evaluateTasks(tasks);
        } catch (RuntimeException ex) {
            for (LeafTask task : tasks) {
                removeVirtualLoss(task.path());
            }
            throw ex;
        }
        for (int i = 0; i < tasks.size(); i++) {
            completeTask(tasks.get(i), values.get(i));
        }
    }

    /**
     * Selects a leaf task and applies no updates.
     * @return select leaf result
     */
    private LeafTask selectLeaf() {
        List<Node> path = new ArrayList<>();
        Node node = root;
        path.add(node);
        while (node.proof == ProofState.UNKNOWN
                && node.expanded
                && !node.children.isEmpty()
                && node.depth < MAX_SEARCH_PLY) {
            node = selectChild(node);
            path.add(node);
        }
        return new LeafTask(node, path, node.position.copy());
    }

    /**
     * Completes one selected playout.
     * @param task task name
     * @param value value to use
     */
    private void completeTask(LeafTask task, Evaluation value) {
        removeVirtualLoss(task.path());
        Node node = task.node();
        maxDepthReached = Math.max(maxDepthReached, node.depth);
        if (!node.expanded && !isTerminal(node, task.path()) && node.depth < MAX_SEARCH_PLY) {
            expand(node, task.path());
        }
        backup(task.path(), value);
        propagateProof(task.path());
        playouts++;
    }

    /**
     * Selects the highest PUCT child.
     * @param parent parent node
     * @return select child result
     */
    private Node selectChild(Node parent) {
        Node proof = proofPreferredChild(parent);
        if (proof != null) {
            return proof;
        }
        Node best = null;
        double bestScore = -Double.MAX_VALUE;
        for (Node child : parent.children) {
            double score = rootPerspective(parent, child) + exploration(parent, child);
            if (score > bestScore) {
                bestScore = score;
                best = child;
            }
        }
        return best;
    }

    /**
     * Returns exploitation from the parent node's perspective.
     * @param parent parent node
     * @param child child node
     * @return root perspective result
     */
    private static double rootPerspective(Node parent, Node child) {
        if (child.proof != ProofState.UNKNOWN) {
            return proofValueForParent(child);
        }
        if (child.visits() == 0) {
            return parent.visits() == 0 ? 0.0 : parent.q() - FPU_REDUCTION;
        }
        return -child.q();
    }

    /**
     * Returns root-perspective Q for a root child.
     * @param child child node
     * @return root perspective q result
     */
    private static double rootPerspectiveQ(Node child) {
        if (child.proof != ProofState.UNKNOWN) {
            return proofValueForParent(child);
        }
        return child.visits() == 0 ? 0.0 : -child.q();
    }

    /**
     * PUCT exploration term for one edge.
     * @param parent parent node
     * @param child child node
     * @return exploration result
     */
    private double exploration(Node parent, Node child) {
        if (child.proof != ProofState.UNKNOWN) {
            return 0.0;
        }
        double parentVisits = Math.sqrt(Math.max(1, parent.visits()));
        return cpuct * child.prior * parentVisits / (1.0 + child.visits());
    }

    /**
     * Expands legal children for one node.
     * @param node node value
     * @param path file path
     */
    private void expand(Node node, List<Node> path) {
        if (node.proof != ProofState.UNKNOWN) {
            node.expanded = true;
            return;
        }
        MoveList legal = node.position.legalMoves();
        if (legal.isEmpty() || isDraw(node.position) || isRepetition(path)) {
            initializeTerminalProof(node, legal.isEmpty(), isRepetition(path));
            node.expanded = true;
            return;
        }
        short[] moves = new short[legal.size()];
        for (int i = 0; i < moves.length; i++) {
            moves[i] = legal.raw(i);
        }
        double[] priors = priors(node.position, moves);
        for (int i = 0; i < moves.length; i++) {
            Position childPosition = node.position.copy().play(moves[i]);
            node.children.add(newNode(node, moves[i], childPosition, priors[i], node.depth + 1));
        }
        node.expanded = true;
        refreshProof(node);
    }

    /**
     * Evaluates a tree node from the node side-to-move perspective.
     * @param task task name
     * @return evaluate task result
     */
    private Evaluation evaluateTask(LeafTask task) {
        if (task.node().proof != ProofState.UNKNOWN) {
            return evaluationForProof(task.node().proof);
        }
        if (isRepetition(task.path())) {
            return Evaluation.draw();
        }
        return evaluatePosition(task.position());
    }

    /**
     * Evaluates selected leaves through the backend batch hook.
     * @param tasks tasks value
     * @return evaluate tasks result
     */
    private List<Evaluation> evaluateTasks(List<LeafTask> tasks) {
        List<Evaluation> out = new ArrayList<>(tasks.size());
        List<Position> backendPositions = new ArrayList<>();
        List<Integer> backendIndexes = new ArrayList<>();
        for (int i = 0; i < tasks.size(); i++) {
            LeafTask task = tasks.get(i);
            Evaluation terminal = terminalEvaluation(task);
            out.add(terminal);
            if (terminal == null) {
                Position position = task.position();
                if (shouldQuiesce(position)) {
                    out.set(i, quiescence(position, 0, -1.0, 1.0));
                } else {
                    backendIndexes.add(i);
                    backendPositions.add(position);
                }
            }
        }
        if (!backendPositions.isEmpty()) {
            List<Evaluation> evaluated = evaluateBackendBatch(backendPositions);
            for (int i = 0; i < backendIndexes.size(); i++) {
                out.set(backendIndexes.get(i), evaluated.get(i));
            }
        }
        return out;
    }

    /**
     * Returns a terminal/draw evaluation, or null when backend evaluation is
     * needed.
     * @param task task name
     * @return terminal evaluation result
     */
    private Evaluation terminalEvaluation(LeafTask task) {
        if (isRepetition(task.path())) {
            return Evaluation.draw();
        }
        Position position = task.position();
        MoveList legal = position.legalMoves();
        if (legal.isEmpty()) {
            return position.inCheck() ? Evaluation.loss() : Evaluation.draw();
        }
        if (isDraw(position)) {
            return Evaluation.draw();
        }
        return null;
    }

    /**
     * Evaluates a standalone position from the side-to-move perspective.
     * @param position chess position
     * @return evaluate position result
     */
    private Evaluation evaluatePosition(Position position) {
        MoveList legal = position.legalMoves();
        if (legal.isEmpty()) {
            return position.inCheck() ? Evaluation.loss() : Evaluation.draw();
        }
        if (isDraw(position)) {
            return Evaluation.draw();
        }
        return quiescence(position, 0, -1.0, 1.0);
    }

    /**
     * Evaluates one non-terminal position through the backend.
     * @param position chess position
     * @return evaluate backend result
     */
    private Evaluation evaluateBackend(Position position) {
        if (backend.threadSafe()) {
            return backend.evaluate(position);
        }
        synchronized (backend) {
            return backend.evaluate(position);
        }
    }

    /**
     * Evaluates non-terminal positions through the backend batch hook.
     * @param positions positions value
     * @return evaluate backend batch result
     */
    private List<Evaluation> evaluateBackendBatch(List<Position> positions) {
        if (backend.threadSafe()) {
            return backend.evaluateBatch(positions);
        }
        synchronized (backend) {
            return backend.evaluateBatch(positions);
        }
    }

    /**
     * Returns whether a non-terminal leaf needs a forcing extension before static
     * backend evaluation.
     * @param position chess position
     * @return true when should quiesce
     */
    private boolean shouldQuiesce(Position position) {
        MoveList legal = position.legalMoves();
        if (legal.isEmpty() || isDraw(position)) {
            return false;
        }
        if (position.inCheck() || findMateInOne(position, legal) != Move.NO_MOVE) {
            return true;
        }
        for (int i = 0; i < legal.size(); i++) {
            if (isQuiescenceMove(position, legal.raw(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Values a leaf after resolving immediate mates, captures/promotions, and
     * forced check evasions. The returned value is from the side-to-move
     * perspective of {@code position}.
     * @param position chess position
     * @param qply qply value
     * @param alpha alpha search bound
     * @param beta beta value
     * @return quiescence result
     */
    private Evaluation quiescence(Position position, int qply, double alpha, double beta) {
        if (isDraw(position)) {
            return Evaluation.draw();
        }
        MoveList legal = position.legalMoves();
        if (legal.isEmpty()) {
            return position.inCheck() ? Evaluation.loss() : Evaluation.draw();
        }
        if (qply == 0 && findMateInOne(position, legal) != Move.NO_MOVE) {
            return Evaluation.win();
        }

        boolean inCheck = position.inCheck();
        if (qply >= QUIESCENCE_MAX_PLY) {
            return evaluateBackend(position);
        }

        Evaluation best = inCheck ? Evaluation.loss() : evaluateBackend(position);
        double currentAlpha = Math.max(alpha, best.value());
        if (!inCheck && best.value() >= beta) {
            return best;
        }
        short[] moves = quiescenceMoves(position, legal, inCheck);
        for (short move : moves) {
            Position.State state = new Position.State();
            position.play(move, state);
            try {
                Evaluation value = quiescence(position, qply + 1, -beta, -currentAlpha).flipped();
                if (value.value() >= beta) {
                    return value;
                }
                if (value.value() > best.value()) {
                    best = value;
                    currentAlpha = Math.max(currentAlpha, value.value());
                }
            } finally {
                position.undo(move, state);
            }
        }
        return best;
    }

    /**
     * Returns quiescence candidate moves: all legal evasions while in check, or
     * captures/promotions in otherwise quiet positions.
     * @param position chess position
     * @param legal legal moves
     * @param inCheck in check value
     * @return quiescence moves result
     */
    private static short[] quiescenceMoves(Position position, MoveList legal, boolean inCheck) {
        if (inCheck) {
            return legal.toArray();
        }
        short[] moves = new short[legal.size()];
        int[] scores = new int[legal.size()];
        int count = 0;
        for (int i = 0; i < legal.size(); i++) {
            short move = legal.raw(i);
            if (isQuiescenceMove(position, move)) {
                moves[count] = move;
                scores[count] = quiescenceMoveScore(position, move);
                count++;
            }
        }
        insertionSortDescending(moves, scores, count);
        int outCount = Math.min(count, QUIESCENCE_MAX_MOVES);
        short[] filtered = new short[outCount];
        if (outCount > 0) {
            System.arraycopy(moves, 0, filtered, 0, outCount);
        }
        return filtered;
    }

    /**
     * Returns a legal mate-in-one move, or {@link Move#NO_MOVE}.
     * @param position chess position
     * @param legal legal moves
     * @return find mate in one result
     */
    private static short findMateInOne(Position position, MoveList legal) {
        for (int i = 0; i < legal.size(); i++) {
            short move = legal.raw(i);
            Position.State state = new Position.State();
            position.play(move, state);
            try {
                if (position.inCheck() && position.legalMoves().isEmpty()) {
                    return move;
                }
            } finally {
                position.undo(move, state);
            }
        }
        return Move.NO_MOVE;
    }

    /**
     * Returns whether a move should be searched in quiescence from a non-check
     * position.
     * @param position chess position
     * @param move move encoded in CRTK move format
     * @return true when is quiescence move
     */
    private static boolean isQuiescenceMove(Position position, short move) {
        return position.isCapture(move) || position.isPromotion(move);
    }

    /**
     * Scores a quiescence move for deterministic tactical ordering.
     * @param position chess position
     * @param move move encoded in CRTK move format
     * @return quiescence move score result
     */
    private static int quiescenceMoveScore(Position position, short move) {
        int score = promotionValue(Move.getPromotion(move)) * 100;
        int captured = position.capturedPiece(move);
        if (captured >= 0) {
            score += Piece.getValue((byte) captured) * 100;
            score -= Piece.getValue(position.pieceAt(Move.getFromIndex(move)));
        }
        return score;
    }

    /**
     * Sorts the first {@code count} moves by descending scores.
     * @param moves candidate moves
     * @param scores score values
     * @param count item count
     */
    private static void insertionSortDescending(short[] moves, int[] scores, int count) {
        for (int i = 1; i < count; i++) {
            short move = moves[i];
            int score = scores[i];
            int j = i - 1;
            while (j >= 0 && scores[j] < score) {
                moves[j + 1] = moves[j];
                scores[j + 1] = scores[j];
                j--;
            }
            moves[j + 1] = move;
            scores[j + 1] = score;
        }
    }

    /**
     * Returns whether the position is statically drawn.
     * @param position chess position
     * @return true when is draw
     */
    private static boolean isDraw(Position position) {
        return position.halfMoveClock() >= 100 || position.isInsufficientMaterial();
    }

    /**
     * Returns whether a node is terminal under the current path.
     * @param node node value
     * @param path file path
     * @return true when is terminal
     */
    private boolean isTerminal(Node node, List<Node> path) {
        return node.proof != ProofState.UNKNOWN
                || node.position.legalMoves().isEmpty()
                || isDraw(node.position)
                || isRepetition(path);
    }

    /**
     * Initializes directly terminal node proof status.
     * @param node node value
     */
    private static void initializeDirectProof(Node node) {
        MoveList legal = node.position.legalMoves();
        initializeTerminalProof(node, legal.isEmpty(), false);
    }

    /**
     * Initializes terminal proof from legal move and draw state.
     * @param node node value
     * @param noLegalMoves no legal moves value
     * @param repetition repetition value
     */
    private static void initializeTerminalProof(Node node, boolean noLegalMoves, boolean repetition) {
        if (noLegalMoves) {
            if (node.position.inCheck()) {
                setProof(node, ProofState.LOSS, 0);
            } else {
                setProof(node, ProofState.DRAW, 0);
            }
        } else if (repetition || isDraw(node.position)) {
            setProof(node, ProofState.DRAW, 0);
        }
    }

    /**
     * Propagates proven endgame bounds up a searched path.
     * @param path file path
     */
    private static void propagateProof(List<Node> path) {
        for (int i = path.size() - 1; i >= 0; i--) {
            refreshProof(path.get(i));
        }
    }

    /**
     * Recomputes one node's proof status from known child proof bounds.
     * @param node node value
     */
    private static void refreshProof(Node node) {
        if (node.children.isEmpty()) {
            return;
        }
        Node winningChild = null;
        Node drawingChild = null;
        Node longestLosingChild = null;
        boolean allKnown = true;
        boolean allChildrenWinForOpponent = true;
        for (Node child : node.children) {
            if (child.proof == ProofState.LOSS) {
                if (winningChild == null || child.proofPlies < winningChild.proofPlies) {
                    winningChild = child;
                }
            } else if (child.proof == ProofState.DRAW) {
                if (drawingChild == null || child.proofPlies < drawingChild.proofPlies) {
                    drawingChild = child;
                }
                allChildrenWinForOpponent = false;
            } else if (child.proof == ProofState.WIN) {
                if (longestLosingChild == null || child.proofPlies > longestLosingChild.proofPlies) {
                    longestLosingChild = child;
                }
            } else {
                allKnown = false;
                allChildrenWinForOpponent = false;
            }
        }
        if (winningChild != null) {
            setProof(node, ProofState.WIN, winningChild.proofPlies + 1);
        } else if (allKnown && drawingChild != null) {
            setProof(node, ProofState.DRAW, drawingChild.proofPlies + 1);
        } else if (allKnown && allChildrenWinForOpponent && longestLosingChild != null) {
            setProof(node, ProofState.LOSS, longestLosingChild.proofPlies + 1);
        }
    }

    /**
     * Updates a node proof only when the new proof is better defined.
     * @param node node value
     * @param proof proof value
     * @param plies ply count
     */
    private static void setProof(Node node, ProofState proof, int plies) {
        if (proof == ProofState.UNKNOWN) {
            return;
        }
        if (node.proof == ProofState.UNKNOWN
                || proof == ProofState.WIN && node.proof == ProofState.WIN && plies < node.proofPlies
                || proof == ProofState.LOSS && node.proof == ProofState.LOSS && plies > node.proofPlies
                || proof == ProofState.DRAW && node.proof == ProofState.DRAW && plies < node.proofPlies) {
            node.proof = proof;
            node.proofPlies = Math.max(0, plies);
        }
    }

    /**
     * Returns a side-to-move evaluation for a proof state.
     * @param proof proof value
     * @return evaluation for proof result
     */
    private static Evaluation evaluationForProof(ProofState proof) {
        return switch (proof) {
            case WIN -> Evaluation.win();
            case LOSS -> Evaluation.loss();
            case DRAW -> Evaluation.draw();
            default -> Evaluation.draw();
        };
    }

    /**
     * Returns a proven child to select from a node, if one dominates search.
     * @param parent parent node
     * @return proof preferred child result
     */
    private static Node proofPreferredChild(Node parent) {
        if (parent.children.isEmpty()) {
            return null;
        }
        Node winning = null;
        Node drawing = null;
        Node delayingLoss = null;
        boolean allKnown = true;
        for (Node child : parent.children) {
            if (child.proof == ProofState.LOSS) {
                if (winning == null || child.proofPlies < winning.proofPlies) {
                    winning = child;
                }
            } else if (child.proof == ProofState.DRAW) {
                if (drawing == null || child.proofPlies < drawing.proofPlies) {
                    drawing = child;
                }
            } else if (child.proof == ProofState.WIN) {
                if (delayingLoss == null || child.proofPlies > delayingLoss.proofPlies) {
                    delayingLoss = child;
                }
            } else {
                allKnown = false;
            }
        }
        if (winning != null) {
            return winning;
        }
        if (parent.proof == ProofState.DRAW && drawing != null) {
            return drawing;
        }
        if (allKnown && delayingLoss != null) {
            return delayingLoss;
        }
        return null;
    }

    /**
     * Returns a root-perspective value for a child proof state.
     * @param child child node
     * @return proof value for parent result
     */
    private static double proofValueForParent(Node child) {
        return switch (child.proof) {
            case LOSS -> 1.0;
            case DRAW -> 0.0;
            case WIN -> -1.0;
            default -> 0.0;
        };
    }

    /**
     * Returns true when the current path has reached the same core position for
     * the third time.
     * @param path file path
     * @return true when is repetition
     */
    private boolean isRepetition(List<Node> path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        long key = path.get(path.size() - 1).coreKey;
        int count = rootHistory.getOrDefault(key, 0);
        for (Node node : path) {
            if (node.coreKey == key) {
                count++;
                if (count >= 3) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Backs up a leaf value through the visited path.
     * @param path file path
     * @param leafValue leaf value value
     */
    private static void backup(List<Node> path, Evaluation leafValue) {
        Evaluation value = leafValue;
        for (int i = path.size() - 1; i >= 0; i--) {
            Node node = path.get(i);
            node.stats.visits++;
            node.stats.valueSum += value.value();
            node.stats.winSum += value.pWin();
            node.stats.drawSum += value.pDraw();
            node.stats.lossSum += value.pLoss();
            value = value.flipped();
        }
    }

    /**
     * Applies virtual loss along an in-flight path.
     * @param path file path
     */
    private static void addVirtualLoss(List<Node> path) {
        for (Node node : path) {
            node.stats.virtualVisits++;
            node.stats.virtualLossSum += 1.0;
        }
    }

    /**
     * Removes virtual loss from an in-flight path.
     * @param path file path
     */
    private static void removeVirtualLoss(List<Node> path) {
        for (Node node : path) {
            if (node.stats.virtualVisits > 0) {
                node.stats.virtualVisits--;
            }
            node.stats.virtualLossSum = Math.max(0.0, node.stats.virtualLossSum - 1.0);
        }
    }

    /**
     * Builds normalized move priors.
     * @param position chess position
     * @param moves candidate moves
     * @return priors result
     */
    private double[] priors(Position position, short[] moves) {
        int[] scores = new int[moves.length];
        scoreBackendMoves(position, moves, scores);
        double max = -Double.MAX_VALUE;
        double[] priors = new double[moves.length];
        for (int i = 0; i < moves.length; i++) {
            priors[i] = (scores[i] + tacticalPrior(position, moves[i])) / 18_000.0;
            if (priors[i] > max) {
                max = priors[i];
            }
        }
        double sum = 0.0;
        for (int i = 0; i < priors.length; i++) {
            priors[i] = Math.exp(priors[i] - max);
            sum += priors[i];
        }
        if (!Double.isFinite(sum) || sum <= 0.0) {
            double uniform = 1.0 / Math.max(1, moves.length);
            for (int i = 0; i < priors.length; i++) {
                priors[i] = uniform;
            }
            return backendPriors(position, moves, priors);
        }
        for (int i = 0; i < priors.length; i++) {
            priors[i] /= sum;
        }
        return backendPriors(position, moves, priors);
    }

    /**
     * Adds backend move-prior scores with backend synchronization when needed.
     * @param position chess position
     * @param moves candidate moves
     * @param scores score values
     */
    private void scoreBackendMoves(Position position, short[] moves, int[] scores) {
        if (backend.threadSafe()) {
            backend.prepareMoveOrdering(position);
            backend.scoreMoves(position, moves, scores);
            return;
        }
        synchronized (backend) {
            backend.prepareMoveOrdering(position);
            backend.scoreMoves(position, moves, scores);
        }
    }

    /**
     * Lets the backend replace priors with backend synchronization when needed.
     * @param position chess position
     * @param moves candidate moves
     * @param fallback fallback value
     * @return backend priors result
     */
    private double[] backendPriors(Position position, short[] moves, double[] fallback) {
        if (backend.threadSafe()) {
            return backend.priors(position, moves, fallback);
        }
        synchronized (backend) {
            return backend.priors(position, moves, fallback);
        }
    }

    /**
     * Adds cheap tactical priors so the tree opens on plausible moves.
     * @param position chess position
     * @param move move encoded in CRTK move format
     * @return tactical prior result
     */
    private static int tacticalPrior(Position position, short move) {
        int bonus = 0;
        byte fromPiece = position.pieceAt(Move.getFromIndex(move));
        byte captured = position.pieceAt(Move.getToIndex(move));
        if (captured != Piece.EMPTY) {
            bonus += Piece.getValue(captured) * 40;
            bonus -= Piece.getValue(fromPiece) * 6;
        }
        if (position.isPromotion(move)) {
            bonus += promotionValue(Move.getPromotion(move)) * 35;
        }
        if (position.isCastle(move)) {
            bonus += 12_000;
        }
        return bonus;
    }

    /**
     * Material value for a promotion code.
     * @param promotion promotion value
     * @return promotion value result
     */
    private static int promotionValue(int promotion) {
        return switch (promotion) {
            case 1 -> Piece.VALUE_KNIGHT;
            case 2 -> Piece.VALUE_BISHOP;
            case 3 -> Piece.VALUE_ROOK;
            case 4 -> Piece.VALUE_QUEEN;
            default -> 0;
        };
    }

    /**
     * Builds the current public result.
     * @param started start time
     * @param stopped stopped value
     * @return current result result
     */
    private Result currentResult(long started, boolean stopped) {
        List<Node> children = orderedRootChildren();
        Node best = children.isEmpty() ? null : children.get(0);
        short[] pv = best == null ? new short[0] : principalVariation(best, MAX_RESULT_PV);
        short bestMove = best == null ? Move.NO_MOVE : best.move;
        int score = root.proof == ProofState.UNKNOWN
                ? valueToCentipawns(best == null ? root.q() : rootPerspectiveQ(best))
                : proofScore(root);
        int depth = root.proof == ProofState.UNKNOWN
                ? playouts == 0L ? 0 : Math.max(1, maxDepthReached)
                : root.proofPlies;
        return new Result(
                bestMove,
                score,
                depth,
                playouts,
                elapsedSince(started),
                root.proof == ProofState.UNKNOWN && stopped,
                pv);
    }

    /**
     * Returns root children ordered by proof outcome, visits, and priors.
     * @return ordered root children result
     */
    private List<Node> orderedRootChildren() {
        List<Node> children = new ArrayList<>(root.children);
        children.sort(Mcts::compareRootChildren);
        return children;
    }

    /**
     * Compares root children with LC0-style terminal proof priority.
     * @param left left coordinate
     * @param right right coordinate
     * @return compare root children result
     */
    private static int compareRootChildren(Node left, Node right) {
        int leftRank = proofRankForParent(left);
        int rightRank = proofRankForParent(right);
        if (leftRank != rightRank) {
            return Integer.compare(rightRank, leftRank);
        }
        if (leftRank > 2) {
            return Integer.compare(left.proofPlies, right.proofPlies);
        }
        if (leftRank < 2) {
            return Integer.compare(right.proofPlies, left.proofPlies);
        }
        int visits = Integer.compare(right.visits(), left.visits());
        if (visits != 0) {
            return visits;
        }
        double leftQ = rootPerspectiveQ(left);
        double rightQ = rootPerspectiveQ(right);
        if (left.visits() > 0 && right.visits() > 0 && Double.compare(leftQ, rightQ) != 0) {
            return Double.compare(rightQ, leftQ);
        }
        return Double.compare(right.prior, left.prior);
    }

    /**
     * Ranks a child proof from the parent's perspective.
     * @param child child node
     * @return proof rank for parent result
     */
    private static int proofRankForParent(Node child) {
        return switch (child.proof) {
            case LOSS -> 4;
            case WIN -> 0;
            default -> 2;
        };
    }

    /**
     * Returns a principal variation by following most-visited children.
     * @param start start index
     * @param maxMoves max moves value
     * @return principal variation result
     */
    private static short[] principalVariation(Node start, int maxMoves) {
        List<Short> moves = new ArrayList<>();
        Node node = start;
        while (node != null && node.move != Move.NO_MOVE && moves.size() < maxMoves) {
            moves.add(node.move);
            node = mostVisitedChild(node);
        }
        short[] out = new short[moves.size()];
        for (int i = 0; i < moves.size(); i++) {
            out[i] = moves.get(i);
        }
        return out;
    }

    /**
     * Returns the most visited child of a node.
     * @param node node value
     * @return most visited child result
     */
    private static Node mostVisitedChild(Node node) {
        Node proof = proofPreferredChild(node);
        if (proof != null) {
            return proof;
        }
        Node best = null;
        for (Node child : node.children) {
            if (best == null || child.visits() > best.visits()) {
                best = child;
            }
        }
        return best;
    }

    /**
     * Converts a normalized value back to a compact centipawn display.
     * @param value value to use
     * @return value to centipawns result
     */
    private static int valueToCentipawns(double value) {
        double v = Math.max(-0.999, Math.min(0.999, value));
        return (int) Math.round(600.0 * 0.5 * Math.log((1.0 + v) / (1.0 - v)));
    }

    /**
     * Converts a proven root state to the engine mate/draw score convention.
     * @param node node value
     * @return proof score result
     */
    private static int proofScore(Node node) {
        return switch (node.proof) {
            case WIN -> AlphaBeta.MATE_SCORE - node.proofPlies;
            case LOSS -> -AlphaBeta.MATE_SCORE + node.proofPlies;
            case DRAW -> 0;
            default -> valueToCentipawns(node.q());
        };
    }

    /**
     * Creates a node wired to a shared hash-table stats bucket.
     * @param parent parent node
     * @param move move encoded in CRTK move format
     * @param position chess position
     * @param prior prior value
     * @param depth search depth
     * @return new node result
     */
    private Node newNode(Node parent, short move, Position position, double prior, int depth) {
        long key = position.signature();
        Node node = new Node(parent, move, position, prior, depth, key,
                position.signatureCore(), statsFor(key));
        initializeDirectProof(node);
        return node;
    }

    /**
     * Returns the shared stats bucket for a position signature.
     * @param key lookup key
     * @return stats for result
     */
    private Stats statsFor(long key) {
        Stats stats = transpositions.get(key);
        if (stats == null) {
            stats = new Stats();
            transpositions.put(key, stats);
        }
        return stats;
    }

    /**
     * Creates a bounded access-ordered transposition stats table.
     * @param limit limit value
     * @return new transposition table result
     */
    private static Map<Long, Stats> newTranspositionTable(int limit) {
        int cap = Math.max(1, limit);
        return new LinkedHashMap<>(1024, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, Stats> eldest) {
                return size() > cap;
            }
        };
    }

    /**
     * Returns elapsed wall-clock time.
     * @param started start time
     * @return elapsed since result
     */
    private static long elapsedSince(long started) {
        return Math.max(0L, System.currentTimeMillis() - started);
    }

    /**
     * Notifies a listener.
     * @param listener event listener
     * @param result result value
     */
    private static void notifySearch(SearchListener listener, Result result) {
        if (listener != null) {
            listener.onInfo(result);
        }
    }

    /**
     * Converts legal-move policy logits to normalized priors.
     * @param position chess position
     * @param moves candidate moves
     * @param fallback fallback value
     * @param logits logits value
     * @param indexer indexer value
     * @return policy priors from logits result
     */
    private static double[] policyPriorsFromLogits(
            Position position,
            short[] moves,
            double[] fallback,
            float[] logits,
            PolicyIndex indexer) {
        if (position == null || moves == null || logits == null || indexer == null) {
            return fallback;
        }
        double[] out = new double[moves.length];
        int[] indices = new int[moves.length];
        float max = Float.NEGATIVE_INFINITY;
        int valid = 0;
        for (int i = 0; i < moves.length; i++) {
            int index = indexer.index(position, moves[i]);
            indices[i] = index;
            if (index >= 0 && index < logits.length && Float.isFinite(logits[index])) {
                max = Math.max(max, logits[index]);
                valid++;
            }
        }
        if (valid == 0 || !Float.isFinite(max)) {
            return fallback;
        }
        double sum = 0.0;
        for (int i = 0; i < moves.length; i++) {
            int index = indices[i];
            if (index >= 0 && index < logits.length && Float.isFinite(logits[index])) {
                out[i] = Math.exp(logits[index] - max);
                sum += out[i];
            }
        }
        if (!Double.isFinite(sum) || sum <= 0.0) {
            return fallback;
        }
        for (int i = 0; i < out.length; i++) {
            out[i] /= sum;
        }
        return out;
    }

    /**
     * Maps one legal move to a policy-logit index.
     */
    @FunctionalInterface
    private interface PolicyIndex {
        /**
         * Returns the backend policy index for one legal move.
         *
         * @param position source position
         * @param move legal move
         * @return policy index, or negative when unmapped
         */
        int index(Position position, short move);
    }

    /**
     * Policy/value backend used by MCTS.
     */
    private interface SearchBackend extends AutoCloseable {

        /**
         * Evaluates one non-terminal position from the side-to-move perspective.
         * @param position chess position
         * @return evaluation score
         */
        Evaluation evaluate(Position position);

        /**
         * Evaluates non-terminal positions as one backend batch.
         * @param positions positions value
         * @return evaluate batch result
         */
        default List<Evaluation> evaluateBatch(List<Position> positions) {
            List<Evaluation> out = new ArrayList<>(positions.size());
            for (Position position : positions) {
                out.add(evaluate(position));
            }
            return out;
        }

        /**
         * Lets the backend prime move-ordering side data.
         * @param position chess position
         */
        default void prepareMoveOrdering(Position position) {
            // default backend has no side data
        }

        /**
         * Adds backend-specific move-prior scores.
         * @param position chess position
         * @param moves candidate moves
         * @param scores score values
         */
        default void scoreMoves(Position position, short[] moves, int[] scores) {
            // default backend does not alter handcrafted priors
        }

        /**
         * Replaces fallback priors when direct policy logits are available.
         * @param position chess position
         * @param moves candidate moves
         * @param fallback fallback value
         * @return priors result
         */
        default double[] priors(Position position, short[] moves, double[] fallback) {
            return fallback;
        }

        /**
         * Returns the backend name.
         * @return name result
         */
        String name();

        /**
         * Returns whether backend calls are safe from multiple worker threads.
         * @return thread safe result
         */
        default boolean threadSafe() {
            return false;
        }

        /**
         * Releases backend resources.
         */
        @Override
        default void close() {
            // default backend has no resources
        }
    }

    /**
     * Centipawn-evaluator backend.
     */
    private static final class EvaluatorBackend implements SearchBackend {
        /**
         * Evaluator.
         */
        private final CentipawnEvaluator evaluator;

        /**
         * Evaluator backend.
         * @param evaluator evaluator value */
        private EvaluatorBackend(CentipawnEvaluator evaluator) {
            this.evaluator = evaluator;
        }

        @Override
        public Evaluation evaluate(Position position) {
            if (evaluator instanceof Classical) {
                return Evaluation.fromWdl(Wdl.evaluate(position, false));
            }
            return Evaluation.fromCentipawns(evaluator.evaluate(position));
        }

        @Override
        public void prepareMoveOrdering(Position position) {
            evaluator.prepareMoveOrdering(position);
        }

        @Override
        public void scoreMoves(Position position, short[] moves, int[] scores) {
            evaluator.scoreMoves(position, moves, scores);
        }

        @Override
        public String name() {
            return evaluator.name();
        }

        @Override
        public boolean threadSafe() {
            return evaluator instanceof Classical;
        }

        @Override
        public void close() {
            evaluator.close();
        }
    }

    /**
     * LC0 CNN policy/value backend.
     */
    private static final class CnnBackend implements SearchBackend {
        /**
         * Model.
         */
        private final chess.nn.lc0.cnn.Model model;
        /**
         * New recent prediction cache.
         * @return new recent prediction cache result */
        private final Map<Long, chess.nn.lc0.cnn.Network.Prediction> cache = newRecentPredictionCache();
        /**
         * Last key.
         */
        private long lastKey = Long.MIN_VALUE;
        /**
         * Last prediction.
         */
        private chess.nn.lc0.cnn.Network.Prediction lastPrediction;

        /**
         * Cnn backend.
         * @param model model value */
        private CnnBackend(chess.nn.lc0.cnn.Model model) {
            this.model = model;
        }

        @Override
        public Evaluation evaluate(Position position) {
            return Evaluation.fromWdl(predict(position).wdl());
        }

        @Override
        public List<Evaluation> evaluateBatch(List<Position> positions) {
            List<chess.nn.lc0.cnn.Network.Prediction> predictions = model.predictBatch(positions);
            List<Evaluation> out = new ArrayList<>(predictions.size());
            for (int i = 0; i < predictions.size(); i++) {
                Position position = positions.get(i);
                chess.nn.lc0.cnn.Network.Prediction prediction = predictions.get(i);
                remember(position.signature(), prediction);
                out.add(Evaluation.fromWdl(prediction.wdl()));
            }
            return out;
        }

        @Override
        public double[] priors(Position position, short[] moves, double[] fallback) {
            float[] logits = predict(position).policy();
            return policyPriorsFromLogits(
                    position,
                    moves,
                    fallback,
                    logits,
                    chess.nn.lc0.cnn.PolicyEncoder::rawPolicyIndex);
        }

        @Override
        public String name() {
            return "lc0(" + model.backend() + ")";
        }

        @Override
        public void close() {
            lastPrediction = null;
            cache.clear();
            model.close();
        }

        /**
         * Predict.
         * @param position chess position
         * @return predict result */
        private chess.nn.lc0.cnn.Network.Prediction predict(Position position) {
            long key = position.signature();
            if (lastPrediction != null && lastKey == key) {
                return lastPrediction;
            }
            chess.nn.lc0.cnn.Network.Prediction cached = cache.get(key);
            if (cached != null) {
                lastKey = key;
                lastPrediction = cached;
                return cached;
            }
            remember(key, model.predict(position));
            return lastPrediction;
        }

        /**
         * Remember.
         * @param key lookup key
         * @param prediction prediction value */
        private void remember(long key, chess.nn.lc0.cnn.Network.Prediction prediction) {
            lastKey = key;
            lastPrediction = prediction;
            cache.put(key, prediction);
        }
    }

    /**
     * LC0 BT4 policy/value backend.
     */
    private static final class Bt4Backend implements SearchBackend {
        /**
         * Network.
         */
        private final chess.nn.lc0.bt4.Network network;
        /**
         * New recent prediction cache.
         * @return new recent prediction cache result */
        private final Map<Long, Bt4Prediction> cache = newRecentPredictionCache();
        /**
         * Last key.
         */
        private long lastKey = Long.MIN_VALUE;
        /**
         * Last prediction.
         */
        private Bt4Prediction lastPrediction;

        /**
         * Bt4 backend.
         * @param network network value */
        private Bt4Backend(chess.nn.lc0.bt4.Network network) {
            this.network = network;
        }

        @Override
        public Evaluation evaluate(Position position) {
            return Evaluation.fromWdl(predict(position).prediction().wdl());
        }

        @Override
        public List<Evaluation> evaluateBatch(List<Position> positions) {
            List<chess.nn.lc0.bt4.Network.TransformedPrediction> predictions =
                    network.predictBatchWithTransforms(positions);
            List<Evaluation> out = new ArrayList<>(predictions.size());
            for (int i = 0; i < predictions.size(); i++) {
                chess.nn.lc0.bt4.Network.TransformedPrediction prediction = predictions.get(i);
                Bt4Prediction cached = new Bt4Prediction(prediction.prediction(), prediction.transform());
                remember(positions.get(i).signature(), cached);
                out.add(Evaluation.fromWdl(cached.prediction().wdl()));
            }
            return out;
        }

        @Override
        public double[] priors(Position position, short[] moves, double[] fallback) {
            Bt4Prediction cached = predict(position);
            float[] logits = cached.prediction().policy();
            int transform = cached.transform();
            return policyPriorsFromLogits(
                    position,
                    moves,
                    fallback,
                    logits,
                    (pos, move) -> chess.nn.lc0.bt4.PolicyEncoder.compressedPolicyIndex(pos, move, transform));
        }

        @Override
        public String name() {
            return "bt4(" + network.backend() + ")";
        }

        @Override
        public void close() {
            lastPrediction = null;
            cache.clear();
            network.close();
        }

        /**
         * Predict.
         * @param position chess position
         * @return predict result */
        private Bt4Prediction predict(Position position) {
            long key = position.signature();
            if (lastPrediction != null && lastKey == key) {
                return lastPrediction;
            }
            Bt4Prediction cached = cache.get(key);
            if (cached != null) {
                lastKey = key;
                lastPrediction = cached;
                return cached;
            }
            TransformSink sink = new TransformSink();
            chess.nn.lc0.bt4.Network.Prediction prediction = network.predict(position, sink);
            remember(key, new Bt4Prediction(prediction, sink.transform));
            return lastPrediction;
        }

        /**
         * Remember.
         * @param key lookup key
         * @param prediction prediction value */
        private void remember(long key, Bt4Prediction prediction) {
            lastKey = key;
            lastPrediction = prediction;
            cache.put(key, prediction);
        }
    }

    /**
     * Creates a small access-ordered prediction cache.
     * @param <T> < t> value
     * @return new recent prediction cache result
     */
    private static <T> Map<Long, T> newRecentPredictionCache() {
        return new LinkedHashMap<>(256, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, T> eldest) {
                return size() > 512;
            }
        };
    }

    /**
     * Cached BT4 prediction plus canonical transform.
     */
    private record Bt4Prediction(chess.nn.lc0.bt4.Network.Prediction prediction, int transform) {
    }

    /**
     * Captures BT4 canonical transform from activation output.
     */
    private static final class TransformSink implements chess.nn.ActivationSink {
        /**
         * Transform.
         */
        private int transform;

        @Override
        public void put(String key, int[] shape, float[] data) {
            if ("bt4.input.transform".equals(key) && data != null && data.length > 0) {
                transform = Math.round(data[0]);
            }
        }
    }

    /**
     * WDL leaf value from side-to-move perspective.
     */
    private record Evaluation(double pWin, double pDraw, double pLoss, double value) {

        /**
         * Returns a drawn evaluation.
         *
         * @return draw evaluation
         */
        private static Evaluation draw() {
            return new Evaluation(0.0, 1.0, 0.0, 0.0);
        }

        /**
         * Returns a forced win evaluation.
         *
         * @return win evaluation
         */
        private static Evaluation win() {
            return new Evaluation(1.0, 0.0, 0.0, 1.0);
        }

        /**
         * Returns a forced loss evaluation.
         *
         * @return loss evaluation
         */
        private static Evaluation loss() {
            return new Evaluation(0.0, 0.0, 1.0, -1.0);
        }

        /**
         * Converts a classical WDL triplet to an evaluation.
         *
         * @param wdl source WDL
         * @return evaluation
         */
        private static Evaluation fromWdl(Wdl wdl) {
            return new Evaluation(
                    wdl.win() / (double) Wdl.TOTAL,
                    wdl.draw() / (double) Wdl.TOTAL,
                    wdl.loss() / (double) Wdl.TOTAL,
                    (wdl.win() - wdl.loss()) / (double) Wdl.TOTAL);
        }

        /**
         * Converts floating-point WDL probabilities to an evaluation.
         *
         * @param wdl source WDL probabilities
         * @return normalized evaluation
         */
        private static Evaluation fromWdl(float[] wdl) {
            if (wdl == null || wdl.length < 3) {
                return draw();
            }
            double win = Math.max(0.0, wdl[0]);
            double draw = Math.max(0.0, wdl[1]);
            double loss = Math.max(0.0, wdl[2]);
            double sum = win + draw + loss;
            if (!Double.isFinite(sum) || sum <= 0.0) {
                return draw();
            }
            win /= sum;
            draw /= sum;
            loss /= sum;
            return new Evaluation(win, draw, loss, win - loss);
        }

        /**
         * Converts centipawns to a soft WDL evaluation.
         *
         * @param centipawns centipawn score from side to move
         * @return evaluation
         */
        private static Evaluation fromCentipawns(int centipawns) {
            double value = Math.tanh(Math.max(-2000, Math.min(2000, centipawns)) / 600.0);
            double draw = Math.max(0.0, 0.35 - Math.abs(value) * 0.25);
            double win = value >= 0.0 ? (1.0 - draw) * (0.5 + value * 0.5) : (1.0 - draw) * (0.5 + value * 0.5);
            double loss = Math.max(0.0, 1.0 - draw - win);
            return new Evaluation(win, draw, loss, value);
        }

        /**
         * Returns this value from the opponent perspective.
         *
         * @return flipped evaluation
         */
        private Evaluation flipped() {
            return new Evaluation(pLoss, pDraw, pWin, -value);
        }
    }

    /**
     * Proven endgame state from the node side-to-move perspective.
     */
    private enum ProofState {
        /**
         * U n k n o w n,.
         */
        UNKNOWN,
        /**
         * W i n,.
         */
        WIN,
        /**
         * D r a w,.
         */
        DRAW,
        /**
         * L o s s.
         */
        LOSS
    }

    /**
     * Shared MCTS stats for one hashed position.
     */
    private static final class Stats {
        /**
         * Visits.
         */
        private int visits;
        /**
         * Virtual visits.
         */
        private int virtualVisits;
        /**
         * Value sum.
         */
        private double valueSum;
        /**
         * Virtual loss sum.
         */
        private double virtualLossSum;
        /**
         * Win sum.
         */
        private double winSum;
        /**
         * Draw sum.
         */
        private double drawSum;
        /**
         * Loss sum.
         */
        private double lossSum;

        /**
         * Q.
         * @return q result */
        private double q() {
            int totalVisits = visits + virtualVisits;
            return totalVisits == 0 ? 0.0 : (valueSum - virtualLossSum) / totalVisits;
        }
    }

    /**
     * Selected leaf and root-to-leaf path.
     */
    private record LeafTask(Node node, List<Node> path, Position position) {
    }

    /**
     * One tree node.
     */
    private static final class Node {
        /**
         * Parent.
         */
        private Node parent;
        /**
         * Move.
         */
        private short move;
        /**
         * Position.
         */
        private final Position position;
        /**
         * Prior.
         */
        private final double prior;
        /**
         * Depth.
         */
        private int depth;
        /**
         * Key.
         */
        private final long key;
        /**
         * Core key.
         */
        private final long coreKey;
        /**
         * Stats.
         */
        private final Stats stats;
        /**
         * Array list<>.
         */
        private final List<Node> children = new ArrayList<>();
        /**
         * Expanded.
         */
        private boolean expanded;
        /**
         * Proof.
         */
        private ProofState proof = ProofState.UNKNOWN;
        /**
         * Proof plies.
         */
        private int proofPlies = Integer.MAX_VALUE;

        /**
         * Node.
         * @param parent parent node
         * @param move move encoded in CRTK move format
         * @param position chess position
         * @param prior prior value
         * @param depth search depth
         * @param key lookup key
         * @param coreKey core key value
         * @param stats statistics data */
        private Node(
                Node parent,
                short move,
                Position position,
                double prior,
                int depth,
                long key,
                long coreKey,
                Stats stats) {
            this.parent = parent;
            this.move = move;
            this.position = position;
            this.prior = prior;
            this.depth = depth;
            this.key = key;
            this.coreKey = coreKey;
            this.stats = stats;
        }

        /**
         * Q.
         * @return q result */
        private double q() {
            return stats.q();
        }

        /**
         * Visits.
         * @return visits result */
        private int visits() {
            return stats.visits;
        }
    }

    /**
     * Callback for completed MCTS info snapshots.
     */
    @FunctionalInterface
    public interface SearchListener {

        /**
         * Handles one search info result.
         *
         * @param result current search result
         */
        void onInfo(Result result);
    }
}
