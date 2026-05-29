package application.gui.workbench.mcts;

import application.gui.workbench.Defaults;
import application.gui.workbench.network.RealActivations;
import chess.core.Position;
import chess.struct.Game;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/**
 * Shared owner for an interactive Workbench MCTS search.
 *
 * <p>The session owns the worker, current root, pause state, and bounded
 * snapshots. UI panels observe immutable snapshots and never call into
 * {@link MctsSearch} directly on the event-dispatch thread.</p>
 */
public final class MctsSession implements AutoCloseable {

    /**
     * Minimum elapsed time between published worker snapshots after warmup.
     */
    private static final long PUBLISH_INTERVAL_NANOS = 125_000_000L;

    /**
     * Number of first playouts published individually for fast initial feedback.
     */
    private static final int WARMUP_PUBLISH_PLAYOUTS = 16;

    /**
     * Worker playout interval for briefly yielding the search thread.
     */
    private static final int YIELD_INTERVAL = 64;

    /**
     * Guards mutable worker and request state.
     */
    private final Object lock = new Object();

    /**
     * Registered session observers.
     */
    private final List<Listener> listeners = new ArrayList<>();

    /**
     * Shared activation-path resolver for neural backends.
     */
    private final RealActivations activations = new RealActivations();

    /**
     * Active background worker, if any.
     */
    private SwingWorker<Void, Frame> worker;

    /**
     * Active search owned by the worker.
     */
    private MctsSearch search;

    /**
     * Monotonic worker generation used to discard stale frames.
     */
    private int generation;

    /**
     * Paused flag read by the active worker.
     */
    private boolean paused;

    /**
     * Pending root FEN requested by the UI.
     */
    private String requestedRootFen;

    /**
     * Whether the pending root request should attempt subtree reuse.
     */
    private boolean requestedRootReuse;

    /**
     * Currently selected node id for tree snapshots.
     */
    private String selectedNodeId = "root";

    /**
     * Latest immutable UI snapshot.
     */
    private Snapshot snapshot = Snapshot.idle(Game.STANDARD_START_FEN);

    /**
     * Search backend exposed by the MCTS tab.
     */
    public enum Backend {
        /**
         * Built-in classical evaluator.
         */
        CLASSICAL("Classical"),

        /**
         * NNUE evaluator.
         */
        NNUE("NNUE"),

        /**
         * LC0 convolutional evaluator.
         */
        LC0_CNN("LC0 CNN"),

        /**
         * LC0 BT4 evaluator.
         */
        LC0_BT4("LC0 BT4"),

        /**
         * OTIS policy/WDL evaluator.
         */
        OTIS("OTIS");

        /**
         * Display label.
         */
        private final String label;

        /**
         * Creates a backend selector.
         *
         * @param label display label
         */
        Backend(String label) {
            this.label = label;
        }

        /**
         * Returns the display label.
         *
         * @return display label
         */
        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Session lifecycle state.
     */
    public enum State {
        /**
         * No worker is active.
         */
        IDLE,

        /**
         * Worker has been requested and is initializing.
         */
        STARTING,

        /**
         * Worker is actively searching.
         */
        RUNNING,

        /**
         * Worker is alive but paused.
         */
        PAUSED,

        /**
         * Worker completed normally.
         */
        DONE,

        /**
         * Worker failed.
         */
        ERROR
    }

    /**
     * Session listener.
     */
    @FunctionalInterface
    public interface Listener {
        /**
         * Called after the session snapshot changes.
         *
         * @param session changed session
         */
        void sessionChanged(MctsSession session);
    }

    /**
     * Search configuration.
     *
     * @param rootFen requested root FEN
     * @param backend selected backend
     * @param maxPlayouts playout limit, or zero for no visit limit
     * @param maxMillis wall-clock limit, or zero for no time limit
     * @param cpuct exploration constant
     * @param reuseSubtree true to reuse known child subtrees
     */
    public record Config(
            String rootFen,
            Backend backend,
            int maxPlayouts,
            long maxMillis,
            double cpuct,
            boolean reuseSubtree) {
        /**
         * Returns a validated copy with defaults and normalized FEN.
         *
         * @param fallbackFen fallback root FEN
         * @return normalized config
         */
        public Config normalized(String fallbackFen) {
            String fen = rootFen == null || rootFen.isBlank() ? fallbackFen : rootFen;
            Position root = new Position(fen);
            Backend safeBackend = backend == null ? Backend.CLASSICAL : backend;
            int visits = Math.max(0, maxPlayouts);
            long millis = Math.max(0L, maxMillis);
            double c = Math.max(0.05, cpuct);
            return new Config(root.toString(), safeBackend, visits, millis, c, reuseSubtree);
        }
    }

    /**
     * Immutable session snapshot.
     *
     * @param state lifecycle state
     * @param status status text
     * @param error error text
     * @param rootFen root FEN
     * @param backend selected backend
     * @param root root summary
     * @param tree tree snapshot
     */
    public record Snapshot(
            State state,
            String status,
            String error,
            String rootFen,
            Backend backend,
            MctsSearch.Snapshot root,
            MctsSearch.TreeSnapshot tree) {
        /**
         * Creates an idle snapshot for a root position.
         *
         * @param rootFen root FEN
         * @return idle snapshot
         */
        static Snapshot idle(String rootFen) {
            return new Snapshot(State.IDLE, "MCTS idle", null, rootFen,
                    Backend.CLASSICAL, null, null);
        }

        /**
         * Returns whether this snapshot represents a live worker.
         *
         * @return true when starting, running, or paused
         */
        boolean running() {
            return state == State.STARTING || state == State.RUNNING || state == State.PAUSED;
        }
    }

    /**
     * Pending root update requested by the UI thread.
     *
     * @param fen requested root FEN
     * @param reuse true to try subtree reuse
     */
    private record RootRequest(String fen, boolean reuse) {
    }

    /**
     * Worker-published snapshot frame.
     *
     * @param generation worker generation
     * @param state lifecycle state
     * @param status status text
     * @param error error text
     * @param rootFen root FEN
     * @param backend selected backend
     * @param root root summary
     * @param tree tree snapshot
     */
    private record Frame(
            int generation,
            State state,
            String status,
            String error,
            String rootFen,
            Backend backend,
            MctsSearch.Snapshot root,
            MctsSearch.TreeSnapshot tree) {
    }

    /**
     * Adds an observer.
     *
     * @param listener listener
     */
    public void addListener(Listener listener) {
        if (listener == null) {
            return;
        }
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    /**
     * Removes an observer.
     *
     * @param listener listener
     */
    public void removeListener(Listener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    /**
     * Returns the latest immutable session snapshot.
     *
     * @return snapshot
     */
    public Snapshot snapshot() {
        return snapshot;
    }

    /**
     * Updates the requested root position.
     *
     * @param fen FEN
     * @param reuse true to reuse an existing subtree when possible
     */
    public void requestRootFen(String fen, boolean reuse) {
        Position root = new Position(fen);
        String normalized = root.toString();
        boolean running;
        synchronized (lock) {
            requestedRootFen = normalized;
            requestedRootReuse = reuse;
            running = snapshot.running();
        }
        if (!running) {
            String side = root.isWhiteToMove() ? "white" : "black";
            updateSnapshot(new Snapshot(State.IDLE, "root " + side + " to move",
                    null, normalized, snapshot.backend(), null, null));
        }
    }

    /**
     * Starts a new worker with the supplied configuration.
     *
     * @param config search config
     */
    public void start(Config config) {
        Config safeConfig = config.normalized(snapshot.rootFen());
        stop();
        int id;
        synchronized (lock) {
            generation++;
            id = generation;
            paused = false;
            requestedRootFen = null;
            requestedRootReuse = false;
            selectedNodeId = "root";
        }
        updateSnapshot(new Snapshot(State.STARTING,
                "starting " + safeConfig.backend() + " MCTS",
                null, safeConfig.rootFen(), safeConfig.backend(), null, null));
        SwingWorker<Void, Frame> active = new SwingWorker<>() {
            /**
             * Runs the search loop away from the event-dispatch thread.
             *
             * @return null when the worker exits
             * @throws Exception if backend setup or search iteration fails
             */
            @Override
            protected Void doInBackground() throws Exception {
                MctsSearch localSearch = null;
                try {
                    Position root = new Position(safeConfig.rootFen());
                    localSearch = createSearch(root, safeConfig.cpuct(), safeConfig.backend());
                    synchronized (lock) {
                        if (generation != id) {
                            return null;
                        }
                        search = localSearch;
                    }
                    publishFrame(id, safeConfig, localSearch, false, "running");
                    long lastPublish = 0L;
                    while (!isCancelled() && generation == id) {
                        RootRequest request = pollRootRequest();
                        if (request != null) {
                            boolean reused;
                            if (request.reuse()) {
                                reused = localSearch.reuseRoot(new Position(request.fen()));
                            } else {
                                localSearch.close();
                                localSearch = createSearch(new Position(request.fen()),
                                        safeConfig.cpuct(), safeConfig.backend());
                                synchronized (lock) {
                                    if (generation == id) {
                                        search = localSearch;
                                    }
                                }
                                reused = false;
                            }
                            publishFrame(id, safeConfig, localSearch, paused,
                                    reused && request.reuse() ? "root reused" : "root reset");
                        }
                        if (paused) {
                            publishFrame(id, safeConfig, localSearch, true, "paused");
                            Thread.sleep(80L);
                            continue;
                        }
                        if (!localSearch.shouldContinue(safeConfig.maxPlayouts(), safeConfig.maxMillis())) {
                            break;
                        }
                        long nextPlayout = localSearch.playouts() + 1L;
                        long now = System.nanoTime();
                        if (shouldPublish(nextPlayout, now, lastPublish)) {
                            publishFrame(id, safeConfig, localSearch, false, "running");
                            lastPublish = now;
                        }
                        localSearch.iterate();
                        if (localSearch.playouts() % YIELD_INTERVAL == 0) {
                            Thread.sleep(1L);
                        }
                    }
                    if (!isCancelled() && generation == id) {
                        publishFrame(id, safeConfig, localSearch, paused, "done");
                    }
                } finally {
                    if (localSearch != null) {
                        localSearch.close();
                    }
                }
                return null;
            }

            /**
             * Publishes a bounded UI frame from the search worker.
             *
             * @param id worker generation
             * @param config normalized search config
             * @param localSearch worker-local search
             * @param pausedFrame true when the frame represents a paused worker
             * @param status short status label
             */
            private void publishFrame(int id, Config config, MctsSearch localSearch,
                    boolean pausedFrame, String status) {
                MctsSearch.Snapshot root = localSearch.snapshot(pausedFrame);
                MctsSearch.TreeSnapshot tree = localSearch.treeSnapshot(
                        pausedFrame,
                        MctsSearch.TreeOptions.defaults(),
                        selectedNodeId);
                State state = pausedFrame ? State.PAUSED : State.RUNNING;
                String detail = status + " · " + localSearch.backendName()
                        + " · " + String.format("%,d", root.playouts()) + " visits";
                publish(new Frame(id, state, detail, null, root.rootFen(),
                        config.backend(), root, tree));
            }

            /**
             * Applies the newest published worker frame on the event-dispatch thread.
             *
             * @param chunks pending worker frames
             */
            @Override
            protected void process(List<Frame> chunks) {
                if (chunks.isEmpty()) {
                    return;
                }
                Frame frame = chunks.get(chunks.size() - 1);
                if (frame.generation() == generation) {
                    applyFrame(frame);
                }
            }

            /**
             * Finalizes session state after worker completion.
             */
            @Override
            protected void done() {
                if (generation != id) {
                    return;
                }
                synchronized (lock) {
                    worker = null;
                    search = null;
                    paused = false;
                }
                try {
                    get();
                    if (snapshot.state() != State.ERROR && snapshot.state() != State.IDLE) {
                        updateSnapshot(new Snapshot(State.DONE, "MCTS done", null,
                                snapshot.rootFen(), safeConfig.backend(), snapshot.root(), snapshot.tree()));
                    }
                } catch (CancellationException ex) {
                    updateSnapshot(new Snapshot(State.IDLE, "MCTS stopped", null,
                            snapshot.rootFen(), safeConfig.backend(), snapshot.root(), snapshot.tree()));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    updateSnapshot(errorSnapshot(safeConfig, "interrupted"));
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    updateSnapshot(errorSnapshot(safeConfig,
                            cause.getClass().getSimpleName() + ": " + cause.getMessage()));
                }
            }
        };
        synchronized (lock) {
            worker = active;
        }
        active.execute();
    }

    /**
     * Pauses the running worker.
     */
    public void pause() {
        synchronized (lock) {
            if (snapshot.running() || worker != null) {
                paused = true;
            }
        }
    }

    /**
     * Resumes the worker.
     */
    public void resume() {
        synchronized (lock) {
            if (snapshot.running() || worker != null) {
                paused = false;
            }
        }
    }

    /**
     * Stops the active worker.
     */
    public void stop() {
        SwingWorker<Void, Frame> active;
        synchronized (lock) {
            active = worker;
            worker = null;
            search = null;
            paused = false;
            generation++;
        }
        if (active != null && !active.isDone()) {
            active.cancel(true);
        }
        if (snapshot.running()) {
            updateSnapshot(new Snapshot(State.IDLE, "MCTS stopped", null,
                    snapshot.rootFen(), snapshot.backend(), snapshot.root(), snapshot.tree()));
        }
    }

    /**
     * Updates the selected node id inside the latest bounded snapshot.
     *
     * @param nodeId node id
     */
    public void setSelectedNodeId(String nodeId) {
        selectedNodeId = nodeId == null || nodeId.isBlank() ? "root" : nodeId;
        Runnable update = this::refreshSelectedNode;
        if (SwingUtilities.isEventDispatchThread()) {
            update.run();
        } else {
            SwingUtilities.invokeLater(update);
        }
    }

    /**
     * Stops the session and releases the active search, if any.
     */
    @Override
    public void close() {
        stop();
    }

    /**
     * Creates a search for the selected backend.
     *
     * @param root root position
     * @param cpuct exploration constant
     * @param backend selected backend
     * @return configured search
     * @throws IOException if backend weights cannot be loaded
     */
    private MctsSearch createSearch(Position root, double cpuct, Backend backend) throws IOException {
        return switch (backend) {
            case NNUE -> MctsSearch.nnue(root, cpuct, activations.nnuePath());
            case LC0_CNN -> MctsSearch.cnn(root, cpuct, RealActivations.cnnPath());
            case LC0_BT4 -> MctsSearch.bt4(root, cpuct, RealActivations.bt4Path());
            case OTIS -> MctsSearch.otis(root, cpuct, RealActivations.otisPath());
            default -> new MctsSearch(root, cpuct);
        };
    }

    /**
     * Polls and clears the pending root request.
     *
     * @return root request, or null
     */
    private RootRequest pollRootRequest() {
        synchronized (lock) {
            if (requestedRootFen == null) {
                return null;
            }
            RootRequest request = new RootRequest(requestedRootFen, requestedRootReuse);
            requestedRootFen = null;
            requestedRootReuse = false;
            return request;
        }
    }

    /**
     * Returns whether the worker should publish a search frame.
     *
     * @param playouts completed playouts
     * @param nowNanos current monotonic time
     * @param lastPublishNanos last publish time
     * @return true when a frame should be published
     */
    private static boolean shouldPublish(long playouts, long nowNanos, long lastPublishNanos) {
        return playouts <= WARMUP_PUBLISH_PLAYOUTS
                || nowNanos - lastPublishNanos >= PUBLISH_INTERVAL_NANOS;
    }

    /**
     * Builds an error snapshot while preserving any available tree state.
     *
     * @param config normalized search config
     * @param message error message
     * @return error snapshot
     */
    private Snapshot errorSnapshot(Config config, String message) {
        return new Snapshot(State.ERROR, "MCTS failed", message,
                snapshot.rootFen() == null ? config.rootFen() : snapshot.rootFen(),
                config.backend(), snapshot.root(), snapshot.tree());
    }

    /**
     * Applies a worker frame to the session snapshot.
     *
     * @param frame worker frame
     */
    private void applyFrame(Frame frame) {
        updateSnapshot(new Snapshot(frame.state(), frame.status(), frame.error(),
                frame.rootFen(), frame.backend(), frame.root(), frame.tree()));
    }

    /**
     * Refreshes the tree snapshot around the currently selected node id.
     */
    private void refreshSelectedNode() {
        MctsSearch.TreeSnapshot tree = snapshot.tree();
        if (tree == null) {
            notifyListeners();
            return;
        }
        MctsSearch.NodeInfo selected = tree.nodes().stream()
                .filter(node -> node.id().equals(selectedNodeId))
                .findFirst()
                .orElse(tree.selectedNode());
        MctsSearch.TreeSnapshot updated = new MctsSearch.TreeSnapshot(
                tree.rootFen(),
                tree.playouts(),
                tree.elapsedMillis(),
                tree.paused(),
                tree.backendName(),
                tree.rootScoreLabel(),
                tree.bestMove(),
                tree.bestPvText(),
                tree.rootRows(),
                tree.nodes(),
                selected,
                tree.omittedNodes());
        updateSnapshot(new Snapshot(snapshot.state(), snapshot.status(), snapshot.error(),
                snapshot.rootFen(), snapshot.backend(), snapshot.root(), updated));
    }

    /**
     * Replaces the latest snapshot on the event-dispatch thread.
     *
     * @param next next snapshot
     */
    private void updateSnapshot(Snapshot next) {
        Runnable apply = () -> {
            snapshot = next;
            notifyListeners();
        };
        if (SwingUtilities.isEventDispatchThread()) {
            apply.run();
        } else {
            SwingUtilities.invokeLater(apply);
        }
    }

    /**
     * Notifies a stable copy of registered listeners.
     */
    private void notifyListeners() {
        List<Listener> copy;
        synchronized (listeners) {
            copy = List.copyOf(listeners);
        }
        for (Listener listener : copy) {
            listener.sessionChanged(this);
        }
    }
}
