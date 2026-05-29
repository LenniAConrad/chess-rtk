package application.gui.workbench.mcts;

import chess.core.Move;
import chess.core.MoveList;
import chess.core.Piece;
import chess.core.Position;
import chess.core.SAN;
import chess.engine.MateProver;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small in-process PUCT search used by the workbench MCTS panel.
 *
 * <p>The shape mirrors Leela-style policy/value tree search: each edge stores
 * a prior probability, visits and an accumulated Q value; selection combines Q
 * with a PUCT exploration term; leaf values are backed up through the visited
 * path. The default constructor uses CRTK's built-in classical evaluator,
 * while the Network tab can select NNUE, LC0 CNN, BT4, or OTIS policy/value
 * backends.</p>
 */
public final class MctsSearch implements AutoCloseable {

    /**
     * Default bounded transposition-stat table size.
     */
    private static final int DEFAULT_TRANSPOSITION_LIMIT = 1 << 18;

    /**
     * Maximum descendant nodes scanned when reusing a searched subtree.
     */
    private static final int MAX_REUSE_SCAN_NODES = 8192;

    /**
     * First-play urgency reduction for unvisited children.
     */
    private static final double FPU_REDUCTION = 0.25;

    /**
     * Maximum forcing plies searched when valuing tactically unstable leaves.
     */
    private static final int QUIESCENCE_MAX_PLY = 4;

    /**
     * Maximum non-evasion forcing moves searched at one quiescence node.
     */
    private static final int QUIESCENCE_MAX_MOVES = 12;

    /**
     * Exploration constant used by PUCT selection.
     */
    private final double cpuct;

    /**
     * Policy/value backend used for leaf values and move priors.
     */
    private final SearchBackend backend;

    /**
     * Bounded hash table keyed by full position signature. Buckets hold MCTS
     * visit/value statistics and are shared by repeated transpositions.
     */
    private final Map<Long, Stats> transpositions;

    /**
     * Root position.
     */
    private Position rootPosition;

    /**
     * Root tree node.
     */
    private Node root;

    /**
     * Immediate root result for terminal roots, static draws, and root states
     * already proven by expanded terminal children.
     */
    private RootDecision immediateRootDecision;

    /**
     * Search start time.
     */
    private final long startedNanos = System.nanoTime();

    /**
     * Completed playout count.
     */
    private long playouts;

    /**
     * Most recently evaluated leaf, used for the "currently exploring" board.
     */
    private Node lastLeaf;

    /**
     * Leaf selected for the playout currently being evaluated.
     */
    private Node currentLeaf;

    /**
     * True after resources owned by the backend have been released.
     */
    private boolean closed;

    /**
     * Creates a PUCT search rooted at a position.
     *
     * @param root root position
     * @param cpuct exploration constant
     */
    public MctsSearch(Position root, double cpuct) {
        if (root == null) {
    throw new IllegalArgumentException("root == null");
        }
        this.backend = new ClassicalSearchBackend();
        this.transpositions = newTranspositionTable(DEFAULT_TRANSPOSITION_LIMIT);
        this.rootPosition = root.copy();
        this.cpuct = Math.max(0.05, cpuct);
        this.root = newNode(null, Move.NO_MOVE, this.rootPosition.copy(), 1.0, 0);
        expand(this.root, List.of(this.root));
        refreshImmediateRootDecision();
    }

    /**
     * Creates a BT4 policy/value MCTS search. The regular constructor stays on
     * the cheap classical backend so the UI remains responsive by default.
     *
     * @param root root position
     * @param cpuct exploration constant
     * @param weights BT4 weights path
     * @return BT4-backed search
     * @throws IOException if weights cannot be loaded
     */
    public static MctsSearch bt4(Position root, double cpuct, Path weights) throws IOException {
        if (root == null) {
    throw new IllegalArgumentException("root == null");
        }
        if (weights == null) {
    throw new IllegalArgumentException("weights == null");
        }
        return new MctsSearch(root, cpuct, Bt4SearchBackend.load(weights), DEFAULT_TRANSPOSITION_LIMIT);
    }

    /**
     * Creates an NNUE value-backed MCTS search.
     *
     * @param root root position
     * @param cpuct exploration constant
     * @param weights NNUE weights path
     * @return NNUE-backed search
     * @throws IOException if weights cannot be loaded
     */
    public static MctsSearch nnue(Position root, double cpuct, Path weights) throws IOException {
        if (root == null) {
            throw new IllegalArgumentException("root == null");
        }
        return new MctsSearch(root, cpuct, NnueSearchBackend.load(weights), DEFAULT_TRANSPOSITION_LIMIT);
    }

    /**
     * Creates an LC0 CNN policy/value MCTS search.
     *
     * @param root root position
     * @param cpuct exploration constant
     * @param weights CNN weights path
     * @return CNN-backed search
     * @throws IOException if weights cannot be loaded
     */
    public static MctsSearch cnn(Position root, double cpuct, Path weights) throws IOException {
        if (root == null) {
            throw new IllegalArgumentException("root == null");
        }
        if (weights == null) {
            throw new IllegalArgumentException("weights == null");
        }
        return new MctsSearch(root, cpuct, CnnSearchBackend.load(weights), DEFAULT_TRANSPOSITION_LIMIT);
    }

    /**
     * Creates an OTIS policy/WDL MCTS search.
     *
     * @param root root position
     * @param cpuct exploration constant
     * @param weights OTIS weights path
     * @return OTIS-backed search
     * @throws IOException if weights cannot be loaded
     */
    public static MctsSearch otis(Position root, double cpuct, Path weights) throws IOException {
        if (root == null) {
            throw new IllegalArgumentException("root == null");
        }
        if (weights == null) {
            throw new IllegalArgumentException("weights == null");
        }
        return new MctsSearch(root, cpuct, OtisSearchBackend.load(weights), DEFAULT_TRANSPOSITION_LIMIT);
    }

    /**
     * Private constructor used by alternate policy/value backends.
     *
     * @param root root position
     * @param cpuct exploration constant
     * @param backend evaluation and policy backend
     * @param transpositionLimit maximum transposition-table entries
     */
    private MctsSearch(
            Position root,
            double cpuct,
            SearchBackend backend,
            int transpositionLimit) {
        this.rootPosition = root.copy();
        this.cpuct = Math.max(0.05, cpuct);
        this.backend = backend == null ? new ClassicalSearchBackend() : backend;
        this.transpositions = newTranspositionTable(transpositionLimit);
        this.root = newNode(null, Move.NO_MOVE, this.rootPosition.copy(), 1.0, 0);
        expand(this.root, List.of(this.root));
        refreshImmediateRootDecision();
    }

    /**
     * Runs one PUCT playout.
     */
    public void iterate() {
        ensureOpen();
        if (immediateRootDecision != null) {
            return;
        }
        List<Node> path = selectPathToLeaf();
        Node node = path.get(path.size() - 1);
        currentLeaf = node;
        SearchEvaluation value = evaluate(node, path);
        if (!node.expanded && !isTerminal(node, path)) {
            expand(node, path);
        }
        backup(path, value);
        propagateProof(path);
        lastLeaf = node;
        playouts++;
    }

    /**
     * Builds a snapshot after selecting the leaf that the next playout will
     * evaluate, without mutating visits or values.
     *
     * @param paused true when the worker is paused
     * @return search snapshot focused on the next evaluated leaf
     */
    public Snapshot previewNextLeaf(boolean paused) {
        ensureOpen();
        if (immediateRootDecision == null && root.proof == ProofState.UNKNOWN) {
            List<Node> path = selectPathToLeaf();
            currentLeaf = path.get(path.size() - 1);
        }
        return snapshot(paused);
    }

    /**
     * Returns whether another playout may be started under the given budgets.
     *
     * @param maxPlayouts max visits, or zero for unlimited
     * @param maxMillis max elapsed milliseconds, or zero for unlimited
     * @return true when search should continue
     */
    public boolean shouldContinue(long maxPlayouts, long maxMillis) {
        if (immediateRootDecision != null) {
            return false;
        }
        if (root.proof != ProofState.UNKNOWN) {
            return false;
        }
        boolean visitsOk = maxPlayouts <= 0L || playouts < maxPlayouts;
        boolean timeOk = maxMillis <= 0L || elapsedMillis() < maxMillis;
        return visitsOk && timeOk;
    }

    /**
     * Selects the root-to-leaf path that the next playout will evaluate.
     *
     * @return selected path
     */
    private List<Node> selectPathToLeaf() {
        List<Node> path = new ArrayList<>();
        Node node = root;
        path.add(node);
        while (node.proof == ProofState.UNKNOWN && node.expanded && !node.children.isEmpty()) {
            node = selectChild(node);
            path.add(node);
        }
        return path;
    }

    /**
     * Re-roots the tree to an already-searched child position when possible.
     *
     * @param newRoot target root position
     * @return true when an existing child subtree was reused
     */
    public boolean reuseRoot(Position newRoot) {
        if (newRoot == null) {
            return false;
        }
        long key = newRoot.signature();
        if (root.key == key) {
            rootPosition = newRoot.copy();
            currentLeaf = root;
            refreshImmediateRootDecision();
            return true;
        }
        Node reused = findReusableRoot(key);
        if (reused != null) {
            reused.parent = null;
            reused.move = Move.NO_MOVE;
            rebaseDepths(reused, 0);
            root = reused;
            rootPosition = newRoot.copy();
            lastLeaf = root;
            currentLeaf = root;
            playouts = root.visits();
            refreshImmediateRootDecision();
            return true;
        }
        rootPosition = newRoot.copy();
        root = newNode(null, Move.NO_MOVE, rootPosition.copy(), 1.0, 0);
        lastLeaf = null;
        currentLeaf = null;
        playouts = 0L;
        expand(root, List.of(root));
        refreshImmediateRootDecision();
        return false;
    }

    /**
     * Releases backend resources owned by this search.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        backend.close();
    }

    /**
     * Throws if the search has already been closed.
     */
    private void ensureOpen() {
        if (closed) {
    throw new IllegalStateException("MCTS search is closed");
        }
    }

    /**
     * Returns completed playouts.
     *
     * @return playout count
     */
    public long playouts() {
        return playouts;
    }

    /**
     * Returns the active backend name.
     *
     * @return backend name
     */
    public String backendName() {
        return backend.name();
    }

    /**
     * Builds an immutable UI snapshot of the current tree root.
     *
     * @param paused true when the worker is paused
     * @return search snapshot
     */
    public Snapshot snapshot(boolean paused) {
        RootDecision decision = immediateRootDecision;
        List<Node> children = orderedRootChildren(decision);
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < Math.min(18, children.size()); i++) {
            Node child = children.get(i);
            double u = exploration(root, child);
            double q = rootRowQ(decision, child);
            short[] pv = principalVariation(child, 10);
            rows.add(new Row(
                    nodeId(child),
                    child.move,
                    moveSan(rootPosition, child.move),
                    Move.toString(child.move),
                    child.visits(),
                    child.prior,
                    q,
                    u,
                    q + u,
                    pv,
                    pvText(rootPosition, pv)));
        }
        Node best = decision == null && !children.isEmpty() ? children.get(0) : null;
        short bestMove = decision == null
                ? best == null ? Move.NO_MOVE : best.move
                : decision.bestMove();
        Position preview = rootPosition.copy();
        short[] bestPv = decision == null
                ? best == null ? new short[0] : principalVariation(best, 12)
                : decision.pv();
        for (short move : bestPv) {
            try {
                preview.play(move);
            } catch (RuntimeException ex) {
                break;
            }
        }
        Node exploringLeaf = currentLeaf == null ? lastLeaf : currentLeaf;
        boolean decisionLeaf = exploringLeaf == null && immediateDecisionAsLeaf(decision);
        Position exploring = decisionLeaf ? preview.copy()
                : exploringLeaf == null ? rootPosition.copy() : exploringLeaf.position.copy();
        short[] exploringLine = decisionLeaf ? bestPv
                : exploringLeaf == null ? new short[0] : lineTo(exploringLeaf, 12);
        double displayValue = decision == null
                ? root.proof == ProofState.UNKNOWN
                        ? best == null ? root.q() : rootPerspectiveQ(best)
                        : proofValue(root.proof)
                : decision.value();
        int rootCentipawns = valueToCentipawns(displayValue);
        String rootScoreLabel = decision != null && decision.mateMoves() > 0
                ? "#" + decision.mateMoves()
                : root.proof == ProofState.WIN ? "#" + mateMoves(root.proofPlies)
                : root.proof == ProofState.LOSS ? "#-" + mateMoves(root.proofPlies)
                : String.format("%+d cp", rootCentipawns);
    return new Snapshot(
                rootPosition.toString(),
                root.visits(),
                playouts,
                elapsedMillis(),
                paused,
                bestMove,
                rootCentipawns,
                rootScoreLabel,
                bestPv,
                pvText(rootPosition, bestPv),
                preview,
                exploring,
                exploringLine,
                pvText(rootPosition, exploringLine),
                rows);
    }

    /**
     * Builds a bounded tree-inspection snapshot for Swing.
     */
    public TreeSnapshot treeSnapshot(boolean paused, TreeOptions options, String selectedNodeId) {
        TreeOptions safeOptions = options == null ? TreeOptions.defaults() : options.normalized();
        Snapshot rootSnapshot = snapshot(paused);
        List<NodeInfo> nodes = new ArrayList<>();
        int[] omitted = { 0 };
        NodeInfo selected = null;
        List<Node> frontier = new ArrayList<>();
        frontier.add(root);
        while (!frontier.isEmpty()) {
            Node node = frontier.remove(0);
            if (node.depth > safeOptions.maxDepth()) {
                omitted[0]++;
                continue;
            }
            if (nodes.size() >= safeOptions.maxNodes()) {
                omitted[0] += countVisibleDescendants(node, safeOptions);
                continue;
            }
            NodeInfo info = nodeInfo(node);
            nodes.add(info);
            if (info.id().equals(selectedNodeId)) {
                selected = info;
            }
            if (node.depth >= safeOptions.maxDepth()) {
                omitted[0] += visibleChildCount(node, safeOptions);
                continue;
            }
            List<Node> children = safeOptions.pvOnly()
                    ? pvChildren(node)
                    : orderedChildren(node);
            for (Node child : children) {
                if (child.visits() < safeOptions.minVisits()) {
                    omitted[0] += countVisibleDescendants(child, safeOptions);
                } else {
                    frontier.add(child);
                }
            }
        }
        if (selected == null) {
            selected = nodes.isEmpty() ? nodeInfo(root) : nodes.get(0);
        }
        return new TreeSnapshot(
                rootSnapshot.rootFen(),
                rootSnapshot.playouts(),
                rootSnapshot.elapsedMillis(),
                rootSnapshot.paused(),
                backend.name(),
                rootSnapshot.rootScoreLabel(),
                rootSnapshot.bestMove(),
                rootSnapshot.bestPvText(),
                rootSnapshot.rows(),
                nodes,
                selected,
                Math.max(0, omitted[0]));
    }

    /**
     * Recomputes the root decision from terminal and tree-proven proof state.
     */
    private void refreshImmediateRootDecision() {
        immediateRootDecision = immediateRootDecision();
    }

    /**
     * Returns the immediate result for terminal roots, automatic draw roots, and
     * tree-proven root states.
     *
     * @return immediate root decision, or null when search should continue
     */
    private RootDecision immediateRootDecision() {
        MoveList legal = rootPosition.legalMoves();
        if (legal.isEmpty()) {
            double value = rootPosition.inCheck() ? -1.0 : 0.0;
    return new RootDecision(Move.NO_MOVE, value, new short[0], false, 0);
        }
        if (isDraw(rootPosition) || isRepetition(List.of(root))) {
            short bestMove = immediateFallbackMove(legal);
            short[] pv = bestMove == Move.NO_MOVE ? new short[0] : new short[] { bestMove };
    return new RootDecision(bestMove, 0.0, pv, false, 0);
        }
        MateProver.Proof proof = MateProver.proveMate(rootPosition);
        if (proof != null) {
    return new RootDecision(
                    proof.bestMove(),
                    1.0,
                    proof.principalVariation(),
                    true,
                    proof.mateMoves());
        }
        if (root.proof != ProofState.UNKNOWN) {
            Node best = proofPreferredChild(root);
            short bestMove = best == null ? Move.NO_MOVE : best.move;
            short[] pv = best == null ? new short[0] : principalVariation(best, 12);
    return new RootDecision(
                    bestMove,
                    proofValue(root.proof),
                    pv,
                    true,
                    root.proof == ProofState.WIN ? mateMoves(root.proofPlies) : 0);
        }
        return null;
    }

    /**
     * Returns the deterministic draw-root fallback move used by the CLI search.
     *
     * @param legal legal root moves
     * @return fallback move
     */
    private short immediateFallbackMove(MoveList legal) {
        List<Node> ordered = orderedRootChildren(null);
        Node best = ordered.isEmpty() ? null : ordered.get(0);
        return best == null ? legal.raw(0) : best.move;
    }

    /**
     * Returns root children in MCTS result order, with terminal root decisions
     * pinned first for the workbench UI.
     *
     * @param decision immediate root decision, or null
     * @return ordered root children
     */
    private List<Node> orderedRootChildren(RootDecision decision) {
        List<Node> children = new ArrayList<>(root.children);
        children.sort((left, right) -> compareRootChildren(decision, left, right));
        return children;
    }

    /**
     * Returns children in deterministic inspection order.
     */
    private List<Node> orderedChildren(Node parent) {
        if (parent == root) {
            return orderedRootChildren(immediateRootDecision);
        }
        List<Node> children = new ArrayList<>(parent.children);
        children.sort((left, right) -> {
            int leftRank = proofRankForParent(left);
            int rightRank = proofRankForParent(right);
            if (leftRank != rightRank) {
                return Integer.compare(rightRank, leftRank);
            }
            int visits = Integer.compare(right.visits(), left.visits());
            if (visits != 0) {
                return visits;
            }
            int prior = Double.compare(right.prior, left.prior);
            if (prior != 0) {
                return prior;
            }
            return Move.toString(left.move).compareTo(Move.toString(right.move));
        });
        return children;
    }

    /**
     * Returns the single principal-variation child for PV-spine traversal.
     */
    private List<Node> pvChildren(Node parent) {
        Node child = mostVisitedChild(parent);
        return child == null ? List.of() : List.of(child);
    }

    /**
     * Counts descendants omitted by traversal controls.
     */
    private int countVisibleDescendants(Node start, TreeOptions options) {
        if (start.depth > options.maxDepth()) {
            return 0;
        }
        int count = 0;
        List<Node> queue = new ArrayList<>();
        queue.add(start);
        for (int i = 0; i < queue.size(); i++) {
            Node node = queue.get(i);
            if (node.depth > options.maxDepth()) {
                continue;
            }
            count++;
            if (node.depth >= options.maxDepth()) {
                continue;
            }
            for (Node child : options.pvOnly() ? pvChildren(node) : orderedChildren(node)) {
                if (child.visits() >= options.minVisits()) {
                    queue.add(child);
                }
            }
        }
        return count;
    }

    /**
     * Counts children that traversal would expose below a depth-limited node.
     */
    private int visibleChildCount(Node node, TreeOptions options) {
        int count = 0;
        for (Node child : options.pvOnly() ? pvChildren(node) : orderedChildren(node)) {
            if (child.visits() >= options.minVisits()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Builds one immutable node-inspection row.
     */
    private NodeInfo nodeInfo(Node node) {
        String id = nodeId(node);
        String parentId = node.parent == null ? "" : nodeId(node.parent);
        short[] line = lineTo(node, 64);
        short[] pv = node == root ? rootPrincipalVariation(10) : principalVariation(node, 10);
        double q = node.parent == null ? node.q() : rootPerspective(node.parent, node);
        double u = node.parent == null ? 0.0 : exploration(node.parent, node);
        int visits = node.visits();
        double win = visits == 0 ? 0.0 : node.stats.winSum / visits;
        double draw = visits == 0 ? 0.0 : node.stats.drawSum / visits;
        double loss = visits == 0 ? 0.0 : node.stats.lossSum / visits;
        return new NodeInfo(
                id,
                parentId,
                line,
                pv,
                node.move,
                moveSan(node.parent == null ? rootPosition : node.parent.position, node.move),
                node.move == Move.NO_MOVE ? "" : Move.toString(node.move),
                uciText(line),
                pvText(rootPosition, line),
                node.depth,
                visits,
                node.prior,
                q,
                u,
                q + u,
                win,
                draw,
                loss,
                node.proof.name().toLowerCase(java.util.Locale.ROOT),
                terminalState(node),
                node.children.size(),
                node.position.toString(),
                pvText(rootPosition, pv));
    }

    /**
     * Returns the current root principal variation.
     */
    private short[] rootPrincipalVariation(int maxMoves) {
        RootDecision decision = immediateRootDecision;
        if (decision != null) {
            return decision.pv();
        }
        Node child = mostVisitedChild(root);
        return child == null ? new short[0] : principalVariation(child, maxMoves);
    }

    /**
     * Compares root children by immediate decision, visits, then prior.
     *
     * @param decision immediate root decision, or null
     * @param left left child
     * @param right right child
     * @return comparison result
     */
    private static int compareRootChildren(RootDecision decision, Node left, Node right) {
        if (decision != null && decision.bestMove() != Move.NO_MOVE) {
            boolean leftImmediate = left.move == decision.bestMove();
            boolean rightImmediate = right.move == decision.bestMove();
            if (leftImmediate != rightImmediate) {
                return leftImmediate ? -1 : 1;
            }
        }
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
        return Double.compare(right.prior, left.prior);
    }

    /**
     * Returns the root-perspective row value for a child.
     *
     * @param decision immediate root decision, or null
     * @param child root child
     * @return row value
     */
    private static double rootRowQ(RootDecision decision, Node child) {
        if (decision != null && child.move == decision.bestMove()) {
            return decision.value();
        }
    return rootPerspectiveQ(child);
    }

    /**
     * Returns whether an immediate decision should be visualized as the current
     * explored leaf.
     *
     * @param decision immediate root decision, or null
     * @return true when it should be shown as a leaf
     */
    private static boolean immediateDecisionAsLeaf(RootDecision decision) {
        return decision != null && decision.showAsLeaf();
    }

    /**
     * Selects the highest PUCT child.
     *
     * @param parent parent node
     * @return selected child
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
     *
     * @param parent parent node
     * @param child child node
     * @return exploitation value
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
     *
     * @param child root child
     * @return root-perspective Q
     */
    private static double rootPerspectiveQ(Node child) {
        if (child.proof != ProofState.UNKNOWN) {
    return proofValueForParent(child);
        }
        return child.visits() == 0 ? 0.0 : -child.q();
    }

    /**
     * PUCT exploration term for one edge.
     *
     * @param parent parent node
     * @param child child node
     * @return exploration value
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
     *
     * @param node node to expand
     * @param path path from root to node
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
     * Evaluates a leaf from the side-to-move perspective.
     *
     * @param position position to evaluate
     * @return scalar evaluation
     */
    double evaluate(Position position) {
        return evaluatePosition(position).value();
    }

    /**
     * Evaluates a tree node from the node side-to-move perspective.
     *
     * @param node node to evaluate
     * @param path path from root to node
     * @return structured evaluation
     */
    private SearchEvaluation evaluate(Node node, List<Node> path) {
        if (node.proof != ProofState.UNKNOWN) {
    return evaluationForProof(node.proof);
        }
        if (isRepetition(path)) {
            return SearchEvaluation.draw();
        }
    return evaluatePosition(node.position);
    }

    /**
     * Evaluates a standalone position from the side-to-move perspective.
     *
     * @param position position to evaluate
     * @return structured evaluation
     */
    private SearchEvaluation evaluatePosition(Position position) {
        MoveList legal = position.legalMoves();
        if (legal.isEmpty()) {
            return position.inCheck() ? SearchEvaluation.loss() : SearchEvaluation.draw();
        }
        if (isDraw(position)) {
            return SearchEvaluation.draw();
        }
    return quiescence(position, 0, -1.0, 1.0);
    }

    /**
     * Values a leaf after resolving immediate mates, captures/promotions, and
     * forced check evasions. The returned value is from the side-to-move
     * perspective of {@code position}.
     *
     * @param position position to evaluate
     * @param qply quiescence ply
     * @param alpha alpha bound
     * @param beta beta bound
     * @return quiescence evaluation
     */
    private SearchEvaluation quiescence(Position position, int qply, double alpha, double beta) {
        if (isDraw(position)) {
            return SearchEvaluation.draw();
        }
        MoveList legal = position.legalMoves();
        if (legal.isEmpty()) {
            return position.inCheck() ? SearchEvaluation.loss() : SearchEvaluation.draw();
        }
        if (qply == 0 && findMateInOne(position, legal) != Move.NO_MOVE) {
            return SearchEvaluation.win();
        }

        boolean inCheck = position.inCheck();
        if (qply >= QUIESCENCE_MAX_PLY) {
            return backend.evaluate(position);
        }

        SearchEvaluation best = inCheck ? SearchEvaluation.loss() : backend.evaluate(position);
        double currentAlpha = Math.max(alpha, best.value());
        if (!inCheck && best.value() >= beta) {
            return best;
        }
        short[] moves = quiescenceMoves(position, legal, inCheck);
        for (short move : moves) {
            Position.State state = new Position.State();
            position.play(move, state);
            try {
                SearchEvaluation value = quiescence(position, qply + 1, -beta, -currentAlpha).flipped();
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
     *
     * @param position position to inspect
     * @param legal legal moves
     * @param inCheck whether side to move is in check
     * @return quiescence candidate moves
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
     *
     * @param position position to inspect
     * @param legal legal moves
     * @return mate-in-one move or {@link Move#NO_MOVE}
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
     *
     * @param position position to inspect
     * @param move move to inspect
     * @return true when the move is tactically noisy
     */
    private static boolean isQuiescenceMove(Position position, short move) {
        return position.isCapture(move) || position.isPromotion(move);
    }

    /**
     * Scores a quiescence move for deterministic tactical ordering.
     *
     * @param position position to inspect
     * @param move move to score
     * @return ordering score
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
     *
     * @param moves moves to reorder
     * @param scores move scores
     * @param count number of entries to sort
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
     * Returns whether the position is terminal by workbench search draw rules.
     *
     * @param position position to inspect
     * @return true for automatic static draws
     */
    private static boolean isDraw(Position position) {
        return position.halfMoveClock() >= 100 || position.isInsufficientMaterial();
    }

    /**
     * Returns whether a node is terminal under the current path.
     *
     * @param node node to inspect
     * @param path root-to-node path
     * @return true when the node is terminal
     */
    private static boolean isTerminal(Node node, List<Node> path) {
        return node.proof != ProofState.UNKNOWN
                || node.position.legalMoves().isEmpty()
                || isDraw(node.position)
                || isRepetition(path);
    }

    /**
     * Initializes directly terminal node proof status.
     *
     * @param node node to initialize
     */
    private static void initializeDirectProof(Node node) {
        MoveList legal = node.position.legalMoves();
        initializeTerminalProof(node, legal.isEmpty(), false);
    }

    /**
     * Initializes terminal proof from legal move and draw state.
     *
     * @param node node to initialize
     * @param noLegalMoves true when no legal moves exist
     * @param repetition true when the path repeats
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
     *
     * @param path root-to-leaf path
     */
    private static void propagateProof(List<Node> path) {
        for (int i = path.size() - 1; i >= 0; i--) {
            refreshProof(path.get(i));
        }
    }

    /**
     * Recomputes one node's proof status from known child proof bounds.
     *
     * @param node node to refresh
     */
    private static void refreshProof(Node node) {
        if (node.children.isEmpty()) {
            return;
        }
        Node winningChild = null;
        Node drawingChild = null;
        Node longestLosingChild = null;
        boolean hasUnknownChild = false;
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
                hasUnknownChild = true;
                allChildrenWinForOpponent = false;
            }
        }
        boolean allKnown = !hasUnknownChild;
        if (winningChild != null) {
            setProof(node, ProofState.WIN, winningChild.proofPlies + 1);
        } else if (allKnown && drawingChild != null) {
            setProof(node, ProofState.DRAW, drawingChild.proofPlies + 1);
        } else if (allChildrenWinForOpponent && longestLosingChild != null) {
            setProof(node, ProofState.LOSS, longestLosingChild.proofPlies + 1);
        }
    }

    /**
     * Updates a node proof.
     *
     * @param node node to update
     * @param proof proof state
     * @param plies proof distance in plies
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
     *
     * @param proof proof state
     * @return proof evaluation
     */
    private static SearchEvaluation evaluationForProof(ProofState proof) {
    return switch (proof) {
            case WIN -> SearchEvaluation.win();
            case LOSS -> SearchEvaluation.loss();
            case DRAW -> SearchEvaluation.draw();
            default -> SearchEvaluation.draw();
        };
    }

    /**
     * Returns a proven child to select from a node, if one dominates search.
     *
     * @param parent parent node
     * @return preferred proven child, or null
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
     * Ranks a child proof from the parent's perspective.
     *
     * @param child child node
     * @return proof rank
     */
    private static int proofRankForParent(Node child) {
    return switch (child.proof) {
            case LOSS -> 4;
            case WIN -> 0;
            default -> 2;
        };
    }

    /**
     * Returns a root-perspective value for a child proof state.
     *
     * @param child child node
     * @return proof value from parent perspective
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
     * Returns a side-to-move value for a node proof.
     *
     * @param proof proof state
     * @return normalized proof value
     */
    private static double proofValue(ProofState proof) {
    return switch (proof) {
            case WIN -> 1.0;
            case LOSS -> -1.0;
            default -> 0.0;
        };
    }

    /**
     * Converts proof plies to mate moves.
     *
     * @param plies proof distance in plies
     * @return mate distance in moves
     */
    private static int mateMoves(int plies) {
        return (Math.max(0, plies) + 1) / 2;
    }

    /**
     * Returns true when the current path has reached the same core position for
     * the third time. The core key intentionally ignores move counters.
     *
     * @param path root-to-node path
     * @return true when the path repeats
     */
    private static boolean isRepetition(List<Node> path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        long key = path.get(path.size() - 1).coreKey;
        int count = 0;
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
     *
     * @param path root-to-leaf path
     * @param leafValue leaf evaluation
     */
    private static void backup(List<Node> path, SearchEvaluation leafValue) {
        SearchEvaluation value = leafValue;
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
     * Builds normalized move priors.
     *
     * @param position position to evaluate
     * @param moves legal moves
     * @return normalized priors
     */
    private double[] priors(Position position, short[] moves) {
        int[] scores = new int[moves.length];
        backend.prepareMoveOrdering(position);
        backend.scoreMoves(position, moves, scores);
        double max = -Double.MAX_VALUE;
        double[] fallback = new double[moves.length];
        for (int i = 0; i < moves.length; i++) {
            fallback[i] = (scores[i] + tacticalPrior(position, moves[i])) / 18_000.0;
            if (fallback[i] > max) {
                max = fallback[i];
            }
        }
        double sum = 0.0;
        for (int i = 0; i < fallback.length; i++) {
            fallback[i] = Math.exp(fallback[i] - max);
            sum += fallback[i];
        }
        if (!Double.isFinite(sum) || sum <= 0.0) {
            double uniform = 1.0 / Math.max(1, moves.length);
            for (int i = 0; i < fallback.length; i++) {
                fallback[i] = uniform;
            }
            return backend.priors(position, moves, fallback);
        }
        for (int i = 0; i < fallback.length; i++) {
            fallback[i] /= sum;
        }
        return backend.priors(position, moves, fallback);
    }

    /**
     * Adds cheap tactical priors so the tree opens on plausible moves.
     *
     * @param position position to inspect
     * @param move move to score
     * @return handcrafted prior score
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
     *
     * @param promotion promotion code
     * @return promoted piece value
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
     * Returns a principal variation by following most-visited children.
     *
     * @param start starting node
     * @param maxMoves maximum moves
     * @return principal variation
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
     * Finds a searched descendant to reuse as a new root.
     *
     * @param key target full position signature
     * @return reusable node, or null
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
     * Rebases subtree depths after re-rooting.
     *
     * @param node subtree root
     * @param depth new depth
     */
    private static void rebaseDepths(Node node, int depth) {
        node.depth = depth;
        for (Node child : node.children) {
            rebaseDepths(child, depth + 1);
        }
    }

    /**
     * Returns the most visited child of a node.
     *
     * @param node parent node
     * @return most visited child, or null
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
     * Returns the move line from the root to a node.
     *
     * @param node target node
     * @param maxMoves maximum moves to include
     * @return root-to-node move sequence
     */
    private static short[] lineTo(Node node, int maxMoves) {
        List<Short> reversed = new ArrayList<>();
        Node cursor = node;
        while (cursor != null && cursor.move != Move.NO_MOVE && reversed.size() < maxMoves) {
            reversed.add(cursor.move);
            cursor = cursor.parent;
        }
        short[] out = new short[reversed.size()];
        for (int i = 0; i < reversed.size(); i++) {
            out[i] = reversed.get(reversed.size() - 1 - i);
        }
        return out;
    }

    /**
     * Returns the stable UCI-line id for a node.
     */
    private static String nodeId(Node node) {
        short[] line = lineTo(node, 128);
        return line.length == 0 ? "root" : uciText(line);
    }

    /**
     * Formats a move line as UCI tokens.
     */
    private static String uciText(short[] line) {
        if (line == null || line.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (short move : line) {
            if (move == Move.NO_MOVE) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Move.toString(move));
        }
        return sb.toString();
    }

    /**
     * Returns compact terminal-state text for tree inspection.
     */
    private static String terminalState(Node node) {
        if (node.proof != ProofState.UNKNOWN) {
            return node.proof == ProofState.DRAW ? "draw" : "proven";
        }
        if (node.expanded && node.children.isEmpty()) {
            return "terminal";
        }
        return "non-terminal";
    }

    /**
     * Formats a move in SAN, falling back to UCI if SAN cannot be produced.
     *
     * @param position position before the move
     * @param move move to format
     * @return SAN or UCI text
     */
    private static String moveSan(Position position, short move) {
        if (move == Move.NO_MOVE) {
            return "";
        }
        try {
            return SAN.toAlgebraic(position, move);
        } catch (RuntimeException ex) {
            return Move.toString(move);
        }
    }

    /**
     * Formats a PV as SAN tokens.
     *
     * @param root root position
     * @param pv move sequence
     * @return SAN principal variation text
     */
    private static String pvText(Position root, short[] pv) {
        Position cursor = root.copy();
        StringBuilder sb = new StringBuilder();
        for (short move : pv) {
            if (move == Move.NO_MOVE) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(moveSan(cursor, move));
            try {
                cursor.play(move);
            } catch (RuntimeException ex) {
                break;
            }
        }
        return sb.toString();
    }

    /**
     * Converts a normalized value back to a compact centipawn display.
     *
     * @param value normalized value
     * @return centipawn approximation
     */
    private static int valueToCentipawns(double value) {
        double v = Math.max(-0.999, Math.min(0.999, value));
        return (int) Math.round(600.0 * 0.5 * Math.log((1.0 + v) / (1.0 - v)));
    }

    /**
     * Returns elapsed wall-clock time in milliseconds.
     *
     * @return elapsed milliseconds
     */
    private long elapsedMillis() {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    /**
     * Creates a node wired to a shared hash-table stats bucket.
     *
     * @param parent parent node
     * @param move move from parent
     * @param position node position
     * @param prior move prior
     * @param depth node depth
     * @return created node
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
     *
     * @param key position signature
     * @return shared stats bucket
     */
    private Stats statsFor(long key) {
        return transpositions.computeIfAbsent(key, ignored -> new Stats());
    }

    /**
     * Creates a bounded access-ordered transposition stats table.
     *
     * @param limit maximum table size
     * @return transposition table
     */
    private static Map<Long, Stats> newTranspositionTable(int limit) {
        int cap = Math.max(1, limit);
        return new LinkedHashMap<>(1024, 0.75f, true) {
            /**
             * Serialization identifier for the bounded map implementation.
             */
            private static final long serialVersionUID = 1L;

            /**
             * Returns whether the oldest entry should be evicted.
             *
             * @param eldest eldest map entry
             * @return true when the cache is above capacity
             */
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, Stats> eldest) {
    return size() > cap;
            }
        };
    }

    /**
     * Immediate root decision that bypasses PUCT playouts, matching the CLI
     * MCTS behavior for terminal roots, static draws, and proven mate roots.
     */
    private record RootDecision(short bestMove, double value, short[] pv, boolean showAsLeaf, int mateMoves) {
    }

    /**
     * Proven endgame state from the node side-to-move perspective.
     */
    private enum ProofState {
        /**
         * Proof state has not been established.
         */
        UNKNOWN,
        /**
         * Side to move can force a win.
         */
        WIN,
        /**
         * Side to move can force a draw.
         */
        DRAW,
        /**
         * Side to move is losing with best play.
         */
        LOSS
    }

    /**
     * Shared MCTS stats for one hashed position.
     */
    private static final class Stats {
        /**
         * Visit count.
         */
        private int visits;

        /**
         * Sum of scalar values.
         */
        private double valueSum;

        /**
         * Sum of win probabilities.
         */
        private double winSum;

        /**
         * Sum of draw probabilities.
         */
        private double drawSum;

        /**
         * Sum of loss probabilities.
         */
        private double lossSum;

        /**
         * Returns average scalar value.
         *
         * @return average value
         */
        private double q() {
            return visits == 0 ? 0.0 : valueSum / visits;
        }
    }

    /**
     * One tree node.
     */
    private static final class Node {
        /**
         * Parent node, or null for root.
         */
        private Node parent;

        /**
         * Move from the parent to this node.
         */
        private short move;

        /**
         * Position represented by this node.
         */
        private final Position position;

        /**
         * Prior probability for the parent edge.
         */
        private final double prior;

        /**
         * Depth from the root.
         */
        private int depth;

        /**
         * Full position signature.
         */
        private final long key;

        /**
         * Core repetition signature.
         */
        private final long coreKey;

        /**
         * Shared transposition stats.
         */
        private final Stats stats;

        /**
         * Expanded children.
         */
        private final List<Node> children = new ArrayList<>();

        /**
         * Whether legal children have been expanded.
         */
        private boolean expanded;

        /**
         * Proven state.
         */
        private ProofState proof = ProofState.UNKNOWN;

        /**
         * Distance to the proof in plies.
         */
        private int proofPlies = Integer.MAX_VALUE;

        /**
         * Creates a tree node.
         *
         * @param parent parent node
         * @param move move from parent
         * @param position node position
         * @param prior edge prior
         * @param depth node depth
         * @param key full position signature
         * @param coreKey repetition signature
         * @param stats shared stats bucket
         */
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
         * Returns the average value.
         *
         * @return average value
         */
        private double q() {
            return stats.q();
        }

        /**
         * Returns visit count.
         *
         * @return visits
         */
        private int visits() {
            return stats.visits;
        }
    }

    /**
     * Immutable root child row for the UI table.
     */
    public record Row(
            String nodeId,
            short move,
            String san,
            String uci,
            int visits,
            double prior,
            double q,
            double u,
            double score,
            short[] pv,
            String pvText) {
    }

    /**
     * Bounded tree traversal controls.
     */
    public record TreeOptions(int maxDepth, int maxNodes, int minVisits, boolean pvOnly) {
        /**
         * Default compact tree controls for interactive Swing rendering.
         */
        public static TreeOptions defaults() {
            return new TreeOptions(3, 256, 0, false);
        }

        /**
         * Returns clamped traversal controls.
         */
        public TreeOptions normalized() {
            int depth = Math.max(0, Math.min(16, maxDepth));
            int nodes = Math.max(1, Math.min(2_000, maxNodes));
            int visits = Math.max(0, minVisits);
            return new TreeOptions(depth, nodes, visits, pvOnly);
        }
    }

    /**
     * Immutable node row for tree inspection.
     */
    public record NodeInfo(
            String id,
            String parentId,
            short[] line,
            short[] pv,
            short move,
            String san,
            String uci,
            String lineUci,
            String lineSan,
            int depth,
            int visits,
            double prior,
            double q,
            double u,
            double score,
            double win,
            double draw,
            double loss,
            String proofState,
            String terminalState,
            int childCount,
            String fen,
            String pvText) {
    }

    /**
     * Bounded tree-inspection snapshot.
     */
    public record TreeSnapshot(
            String rootFen,
            long playouts,
            long elapsedMillis,
            boolean paused,
            String backendName,
            String rootScoreLabel,
            short bestMove,
            String bestPvText,
            List<Row> rootRows,
            List<NodeInfo> nodes,
            NodeInfo selectedNode,
            int omittedNodes) {
    }

    /**
     * Immutable UI snapshot.
     */
    public record Snapshot(
            String rootFen,
            int rootVisits,
            long playouts,
            long elapsedMillis,
            boolean paused,
            short bestMove,
            int rootCentipawns,
            String rootScoreLabel,
            short[] bestPv,
            String bestPvText,
            Position previewPosition,
            Position exploringPosition,
            short[] exploringLine,
            String exploringLineText,
            List<Row> rows) {
    }
}
