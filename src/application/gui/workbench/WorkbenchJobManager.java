package application.gui.workbench;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Bounded history of {@link WorkbenchJob} command runs for the workbench
 * dashboard.
 *
 * <p>Even though only one foreground command runs at a time, the manager keeps
 * a rolling history of recent runs so the dashboard can show what happened
 * (status, duration, exit code, parsed result). Entries past
 * {@link #HISTORY_LIMIT} are dropped oldest-first. All access is expected on
 * the Swing event-dispatch thread; listeners are notified synchronously after
 * every change.</p>
 */
final class WorkbenchJobManager {

    /**
     * Maximum number of jobs retained in history.
     */
    static final int HISTORY_LIMIT = 50;

    /**
     * Jobs in arrival order (oldest first). Trimmed from the front.
     */
    private final List<WorkbenchJob> jobs = new ArrayList<>();

    /**
     * Change listeners notified after every mutation.
     */
    private final List<Runnable> listeners = new ArrayList<>();

    /**
     * Source of monotonic job ids.
     */
    private long nextId = 1;

    /**
     * Registers a change listener.
     *
     * @param listener listener invoked after every job-history change
     */
    void addListener(Runnable listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /**
     * Creates a new queued job, appends it to the history (trimming the oldest
     * entry past {@link #HISTORY_LIMIT}), and notifies listeners.
     *
     * @param args command arguments
     * @return the created job
     */
    WorkbenchJob create(List<String> args) {
        WorkbenchJob job = new WorkbenchJob(nextId++, args,
                WorkbenchCommandRunner.displayCommand(args));
        jobs.add(job);
        while (jobs.size() > HISTORY_LIMIT) {
            jobs.remove(0);
        }
        fireChanged();
        return job;
    }

    /**
     * Transitions a job to running and notifies listeners.
     *
     * @param job job to update
     */
    void markRunning(WorkbenchJob job) {
        job.markRunning();
        fireChanged();
    }

    /**
     * Records a finished run on a job and notifies listeners.
     *
     * @param job job to update
     * @param exitCode process exit code
     * @param output combined output text
     * @param durationMillis run duration in millis
     */
    void markFinished(WorkbenchJob job, int exitCode, String output, long durationMillis) {
        job.markFinished(exitCode, output, durationMillis,
                WorkbenchCommandResultParser.summarize(job.args(), exitCode, output));
        fireChanged();
    }

    /**
     * Records that a job failed before producing an exit code.
     *
     * @param job job to update
     * @param reason failure reason
     * @param durationMillis elapsed time in millis
     */
    void markFailed(WorkbenchJob job, String reason, long durationMillis) {
        job.markFailed(reason, durationMillis);
        fireChanged();
    }

    /**
     * Records that the user cancelled a job.
     *
     * @param job job to update
     * @param durationMillis elapsed time in millis
     */
    void markCancelled(WorkbenchJob job, long durationMillis) {
        job.markCancelled(durationMillis);
        fireChanged();
    }

    /**
     * Returns the job history, newest first.
     *
     * @return immutable snapshot of the history, newest first
     */
    List<WorkbenchJob> recent() {
        List<WorkbenchJob> copy = new ArrayList<>(jobs);
        Collections.reverse(copy);
        return Collections.unmodifiableList(copy);
    }

    /**
     * Returns the number of jobs currently in the history.
     *
     * @return history size
     */
    int size() {
        return jobs.size();
    }

    /**
     * Returns the most recent job, or null when the history is empty.
     *
     * @return latest job or null
     */
    WorkbenchJob latest() {
        return jobs.isEmpty() ? null : jobs.get(jobs.size() - 1);
    }

    /**
     * Notifies every registered listener.
     */
    private void fireChanged() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }
}
