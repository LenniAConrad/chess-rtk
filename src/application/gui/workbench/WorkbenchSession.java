package application.gui.workbench;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Shared, observable model of the workbench's operational state.
 *
 * <p>This is the single source of truth the dashboard renders from — current
 * position, engine status, batch readiness and environment health — plus the
 * {@link WorkbenchJobManager} and {@link WorkbenchArtifactIndex} that track
 * command runs and generated files. {@link WorkbenchWindow} pushes updates
 * here as the user works; the dashboard listens rather than scraping Swing
 * components or console text.</p>
 *
 * <p>It does not (yet) replace every field in {@code WorkbenchWindow}; it holds
 * exactly what the dashboard needs. All access is expected on the Swing
 * event-dispatch thread.</p>
 */
final class WorkbenchSession {

    /**
     * Job history for command runs launched from the workbench.
     */
    private final WorkbenchJobManager jobs = new WorkbenchJobManager();

    /**
     * Index of recently generated artifact files.
     */
    private final WorkbenchArtifactIndex artifacts = new WorkbenchArtifactIndex();

    /**
     * Session-change listeners.
     */
    private final List<WorkbenchSessionListener> listeners = new ArrayList<>();

    /**
     * Current position FEN.
     */
    private String fen = "";

    /**
     * True when White is to move in the current position.
     */
    private boolean whiteToMove = true;

    /**
     * Current ply within the active game line.
     */
    private int ply;

    /**
     * Last (deepest) ply available in the active game line.
     */
    private int lastPly;

    /**
     * Number of legal moves in the current position.
     */
    private int legalMoveCount;

    /**
     * Latest static tags computed for the current position.
     */
    private List<String> tags = List.of();

    /**
     * External-engine protocol path (blank when using the CLI default).
     */
    private String engineProtocolPath = "";

    /**
     * True when the live external-engine mode is enabled.
     */
    private boolean liveEngine;

    /**
     * Short human-readable engine summary (latest eval / best move / depth).
     */
    private String engineSummary = "";

    /**
     * Short human-readable batch-input summary (row counts, first error).
     */
    private String batchSummary = "";

    /**
     * Latest environment-health snapshot.
     */
    private WorkbenchHealthSnapshot health = WorkbenchHealthSnapshot.unknown();

    /**
     * White-relative engine evaluation (centipawns) recorded per visited ply
     * of the active game line. Ordered by ply so the dashboard can draw an
     * eval-over-plies sparkline; cleared whenever a new game starts.
     */
    private final TreeMap<Integer, Integer> evalByPly = new TreeMap<>();

    /**
     * Returns the job manager for command runs.
     *
     * @return job manager
     */
    WorkbenchJobManager jobs() {
        return jobs;
    }

    /**
     * Returns the artifact index for generated files.
     *
     * @return artifact index
     */
    WorkbenchArtifactIndex artifacts() {
        return artifacts;
    }

    /**
     * Registers a session-change listener.
     *
     * @param listener listener notified after every session change
     */
    void addListener(WorkbenchSessionListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /**
     * Returns the current position FEN.
     *
     * @return FEN
     */
    String fen() {
        return fen;
    }

    /**
     * Returns whether White is to move.
     *
     * @return true when White to move
     */
    boolean whiteToMove() {
        return whiteToMove;
    }

    /**
     * Returns the current ply.
     *
     * @return current ply
     */
    int ply() {
        return ply;
    }

    /**
     * Returns the deepest ply in the active line.
     *
     * @return last ply
     */
    int lastPly() {
        return lastPly;
    }

    /**
     * Returns the legal-move count for the current position.
     *
     * @return legal move count
     */
    int legalMoveCount() {
        return legalMoveCount;
    }

    /**
     * Returns the latest static tags for the current position.
     *
     * @return immutable tag list
     */
    List<String> tags() {
        return tags;
    }

    /**
     * Returns the external-engine protocol path (blank for the CLI default).
     *
     * @return protocol path
     */
    String engineProtocolPath() {
        return engineProtocolPath;
    }

    /**
     * Returns whether live external-engine mode is enabled.
     *
     * @return true when live mode is on
     */
    boolean liveEngine() {
        return liveEngine;
    }

    /**
     * Returns the latest engine summary text.
     *
     * @return engine summary
     */
    String engineSummary() {
        return engineSummary;
    }

    /**
     * Returns the latest batch-input summary text.
     *
     * @return batch summary
     */
    String batchSummary() {
        return batchSummary;
    }

    /**
     * Returns the latest environment-health snapshot.
     *
     * @return health snapshot
     */
    WorkbenchHealthSnapshot health() {
        return health;
    }

    /**
     * Updates the position state and notifies listeners.
     *
     * @param fenValue position FEN
     * @param whiteToMoveValue true when White is to move
     * @param plyValue current ply
     * @param lastPlyValue deepest ply in the active line
     * @param legalMoveCountValue legal-move count
     */
    void updatePosition(String fenValue, boolean whiteToMoveValue, int plyValue,
            int lastPlyValue, int legalMoveCountValue) {
        this.fen = fenValue == null ? "" : fenValue;
        this.whiteToMove = whiteToMoveValue;
        this.ply = plyValue;
        this.lastPly = lastPlyValue;
        this.legalMoveCount = legalMoveCountValue;
        fireChanged();
    }

    /**
     * Updates the cached static tags and notifies listeners.
     *
     * @param tagValues tag list (copied; null treated as empty)
     */
    void updateTags(List<String> tagValues) {
        this.tags = tagValues == null ? List.of() : List.copyOf(tagValues);
        fireChanged();
    }

    /**
     * Updates the engine state and notifies listeners.
     *
     * @param protocolPath external-engine protocol path
     * @param live true when live mode is enabled
     * @param summary short engine summary text
     */
    void updateEngine(String protocolPath, boolean live, String summary) {
        this.engineProtocolPath = protocolPath == null ? "" : protocolPath;
        this.liveEngine = live;
        this.engineSummary = summary == null ? "" : summary;
        fireChanged();
    }

    /**
     * Updates the batch-input summary and notifies listeners.
     *
     * @param summary short batch summary text
     */
    void updateBatch(String summary) {
        this.batchSummary = summary == null ? "" : summary;
        fireChanged();
    }

    /**
     * Updates the environment-health snapshot and notifies listeners.
     *
     * @param healthValue new health snapshot (null treated as all-unknown)
     */
    void updateHealth(WorkbenchHealthSnapshot healthValue) {
        this.health = healthValue == null ? WorkbenchHealthSnapshot.unknown() : healthValue;
        fireChanged();
    }

    /**
     * Records a white-relative engine evaluation for a ply and notifies
     * listeners. A later evaluation for the same ply replaces the earlier one.
     *
     * @param plyValue ply the evaluation belongs to
     * @param centipawns white-relative evaluation in centipawns
     */
    void recordEval(int plyValue, int centipawns) {
        evalByPly.put(plyValue, centipawns);
        fireChanged();
    }

    /**
     * Clears the per-ply evaluation history (called when a new game starts)
     * and notifies listeners.
     */
    void clearEvalHistory() {
        if (!evalByPly.isEmpty()) {
            evalByPly.clear();
            fireChanged();
        }
    }

    /**
     * Returns the recorded white-relative evaluations in ply order, in
     * centipawns.
     *
     * @return evaluation series, oldest ply first
     */
    int[] evalHistoryCentipawns() {
        int[] series = new int[evalByPly.size()];
        int i = 0;
        for (int value : evalByPly.values()) {
            series[i++] = value;
        }
        return series;
    }

    /**
     * Notifies every registered listener that the session changed.
     */
    private void fireChanged() {
        for (WorkbenchSessionListener listener : listeners) {
            listener.sessionChanged(this);
        }
    }
}
