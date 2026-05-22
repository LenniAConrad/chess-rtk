/**
 * Source file attribution.
 *
 * @author Lennart A. Conrad
 */

package application.gui.workbench.session;

import java.nio.file.Path;
import java.util.List;

/**
 * One child-process command run tracked by the workbench dashboard.
 *
 * <p>A job is created in {@link JobStatus#QUEUED} state, transitions
 * to {@link JobStatus#RUNNING} when the process starts, and ends in
 * one of the terminal states carrying its exit code, combined output, duration
 * and a short parsed result summary. Instances are mutated only on the Swing
 * event-dispatch thread by {@link JobManager}.</p>
 */
public final class Job {

    /**
     * Monotonic id, unique within a {@link JobManager}.
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
    private JobStatus status = JobStatus.QUEUED;

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
     * Full plain-text log persisted for this run, or null until one is written.
     */
    private Path logPath;

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
    public Job(long id, List<String> args, String displayCommand) {
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
    public long id() {
        return id;
    }

    /**
     * Returns the command arguments.
     *
     * @return immutable argument list
     */
    public List<String> args() {
        return args;
    }

    /**
     * Returns the display command string.
     *
     * @return display command
     */
    public String displayCommand() {
        return displayCommand;
    }

    /**
     * Returns the creation timestamp in epoch millis.
     *
     * @return creation time
     */
    public long createdAtMillis() {
        return createdAtMillis;
    }

    /**
     * Returns the current lifecycle state.
     *
     * @return status
     */
    public JobStatus status() {
        return status;
    }

    /**
     * Returns the process exit code, or {@link Integer#MIN_VALUE} when the job
     * has not produced one.
     *
     * @return exit code
     */
    public int exitCode() {
        return exitCode;
    }

    /**
     * Returns whether the job has recorded an exit code.
     *
     * @return true when an exit code is available
     */
    public boolean hasExitCode() {
        return exitCode != Integer.MIN_VALUE;
    }

    /**
     * Returns the run duration in milliseconds, or -1 when not finished.
     *
     * @return duration in millis
     */
    public long durationMillis() {
        return durationMillis;
    }

    /**
     * Returns the combined captured output.
     *
     * @return output text
     */
    public String output() {
        return output;
    }

    /**
     * Returns the short parsed result summary, or an empty string.
     *
     * @return result summary
     */
    public String resultSummary() {
        return resultSummary;
    }

    /**
     * Returns the persisted run-manifest path, or null when not written yet.
     *
     * @return manifest path or null
     */
    public Path manifestPath() {
        return manifestPath;
    }

    /**
     * Returns the persisted full log path, or null when not written yet.
     *
     * @return log path or null
     */
    public Path logPath() {
        return logPath;
    }

    /**
     * Returns output artifacts detected for this run.
     *
     * @return immutable artifact path list
     */
    public List<Path> artifacts() {
        return artifacts;
    }

    /**
     * Records the manifest and output artifact paths associated with this job.
     *
     * @param path manifest path
     * @param artifactPaths detected output artifacts
     */
    public void recordManifest(Path path, List<Path> artifactPaths) {
        this.manifestPath = path;
        this.artifacts = artifactPaths == null ? List.of() : List.copyOf(artifactPaths);
    }

    /**
     * Records the full log path associated with this job.
     *
     * @param path log path
     */
    public void recordLog(Path path) {
        this.logPath = path;
    }

    /**
     * Marks the job as running.
     */
    public void markRunning() {
        this.status = JobStatus.RUNNING;
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
    public void markFinished(int exitCodeValue, String outputValue, long durationMillisValue,
            String summary) {
        this.exitCode = exitCodeValue;
        this.output = outputValue == null ? "" : outputValue;
        this.durationMillis = durationMillisValue;
        this.resultSummary = summary == null ? "" : summary;
        this.status = exitCodeValue == 0
                ? JobStatus.SUCCEEDED
                : JobStatus.FAILED;
    }

    /**
     * Records that the job failed before producing an exit code (the process
     * could not be launched, was stopped, or threw).
     *
     * @param reason failure reason
     * @param durationMillisValue elapsed time in millis
     */
    public void markFailed(String reason, long durationMillisValue) {
        this.status = JobStatus.FAILED;
        this.durationMillis = durationMillisValue;
        this.resultSummary = reason == null ? "failed" : reason;
    }

    /**
     * Records that the user cancelled the job.
     *
     * @param durationMillisValue elapsed time in millis
     */
    public void markCancelled(long durationMillisValue) {
        this.status = JobStatus.CANCELLED;
        this.durationMillis = durationMillisValue;
        this.resultSummary = "cancelled";
    }
}
