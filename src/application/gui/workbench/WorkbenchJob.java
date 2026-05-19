package application.gui.workbench;

import java.nio.file.Path;
import java.util.List;

/**
 * One child-process command run tracked by the workbench dashboard.
 *
 * <p>A job is created in {@link WorkbenchJobStatus#QUEUED} state, transitions
 * to {@link WorkbenchJobStatus#RUNNING} when the process starts, and ends in
 * one of the terminal states carrying its exit code, combined output, duration
 * and a short parsed result summary. Instances are mutated only on the Swing
 * event-dispatch thread by {@link WorkbenchJobManager}.</p>
 */
final class WorkbenchJob {

    /**
     * Monotonic id, unique within a {@link WorkbenchJobManager}.
     */
    private final long id;

    /**
     * Raw command arguments (without the {@code crtk} prefix).
     */
    private final List<String> args;

    /**
     * Pre-formatted display form of {@link #args}.
     */
    private final String displayCommand;

    /**
     * Wall-clock time the job was created, in epoch millis.
     */
    private final long createdAtMillis;

    /**
     * Current lifecycle state.
     */
    private WorkbenchJobStatus status = WorkbenchJobStatus.QUEUED;

    /**
     * Process exit code, or {@link Integer#MIN_VALUE} until the job finishes.
     */
    private int exitCode = Integer.MIN_VALUE;

    /**
     * Run duration in milliseconds, or -1 until the job finishes.
     */
    private long durationMillis = -1;

    /**
     * Combined stdout/stderr captured from the run (may be truncated upstream).
     */
    private String output = "";

    /**
     * Short, human-readable result summary parsed from the output.
     */
    private String resultSummary = "";

    /**
     * JSON manifest persisted for this run, or null until one is written.
     */
    private Path manifestPath;

    /**
     * Output artifact paths detected for this run.
     */
    private List<Path> artifacts = List.of();

    /**
     * Creates a queued job.
     *
     * @param id unique id
     * @param args command arguments
     * @param displayCommand pre-formatted display command
     */
    WorkbenchJob(long id, List<String> args, String displayCommand) {
        this.id = id;
        this.args = List.copyOf(args);
        this.displayCommand = displayCommand;
        this.createdAtMillis = System.currentTimeMillis();
    }

    /**
     * Returns the unique id.
     *
     * @return id
     */
    long id() {
        return id;
    }

    /**
     * Returns the command arguments.
     *
     * @return immutable argument list
     */
    List<String> args() {
        return args;
    }

    /**
     * Returns the display command string.
     *
     * @return display command
     */
    String displayCommand() {
        return displayCommand;
    }

    /**
     * Returns the creation timestamp in epoch millis.
     *
     * @return creation time
     */
    long createdAtMillis() {
        return createdAtMillis;
    }

    /**
     * Returns the current lifecycle state.
     *
     * @return status
     */
    WorkbenchJobStatus status() {
        return status;
    }

    /**
     * Returns the process exit code, or {@link Integer#MIN_VALUE} when the job
     * has not produced one.
     *
     * @return exit code
     */
    int exitCode() {
        return exitCode;
    }

    /**
     * Returns whether the job has recorded an exit code.
     *
     * @return true when an exit code is available
     */
    boolean hasExitCode() {
        return exitCode != Integer.MIN_VALUE;
    }

    /**
     * Returns the run duration in milliseconds, or -1 when not finished.
     *
     * @return duration in millis
     */
    long durationMillis() {
        return durationMillis;
    }

    /**
     * Returns the combined captured output.
     *
     * @return output text
     */
    String output() {
        return output;
    }

    /**
     * Returns the short parsed result summary, or an empty string.
     *
     * @return result summary
     */
    String resultSummary() {
        return resultSummary;
    }

    /**
     * Returns the persisted run-manifest path, or null when not written yet.
     *
     * @return manifest path or null
     */
    Path manifestPath() {
        return manifestPath;
    }

    /**
     * Returns output artifacts detected for this run.
     *
     * @return immutable artifact path list
     */
    List<Path> artifacts() {
        return artifacts;
    }

    /**
     * Records the manifest and output artifact paths associated with this job.
     *
     * @param path manifest path
     * @param artifactPaths detected output artifacts
     */
    void recordManifest(Path path, List<Path> artifactPaths) {
        this.manifestPath = path;
        this.artifacts = artifactPaths == null ? List.of() : List.copyOf(artifactPaths);
    }

    /**
     * Marks the job as running.
     */
    void markRunning() {
        this.status = WorkbenchJobStatus.RUNNING;
    }

    /**
     * Records a finished run and derives the terminal status from the exit
     * code (zero succeeds, anything else fails).
     *
     * @param exitCodeValue process exit code
     * @param outputValue combined output text
     * @param durationMillisValue run duration in millis
     * @param summary short parsed result summary
     */
    void markFinished(int exitCodeValue, String outputValue, long durationMillisValue,
            String summary) {
        this.exitCode = exitCodeValue;
        this.output = outputValue == null ? "" : outputValue;
        this.durationMillis = durationMillisValue;
        this.resultSummary = summary == null ? "" : summary;
        this.status = exitCodeValue == 0
                ? WorkbenchJobStatus.SUCCEEDED
                : WorkbenchJobStatus.FAILED;
    }

    /**
     * Records that the job failed before producing an exit code (the process
     * could not be launched, was stopped, or threw).
     *
     * @param reason failure reason
     * @param durationMillisValue elapsed time in millis
     */
    void markFailed(String reason, long durationMillisValue) {
        this.status = WorkbenchJobStatus.FAILED;
        this.durationMillis = durationMillisValue;
        this.resultSummary = reason == null ? "failed" : reason;
    }

    /**
     * Records that the user cancelled the job.
     *
     * @param durationMillisValue elapsed time in millis
     */
    void markCancelled(long durationMillisValue) {
        this.status = WorkbenchJobStatus.CANCELLED;
        this.durationMillis = durationMillisValue;
        this.resultSummary = "cancelled";
    }
}
