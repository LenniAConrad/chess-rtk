package application.gui.workbench.session;

/**
 * Lifecycle state of a {@link Job} — one child-process command run
 * tracked by the workbench dashboard.
 */
public enum JobStatus {

    /**
     * Created but not yet started.
     */
    QUEUED("Queued"),

    /**
     * Child process is currently running.
     */
    RUNNING("Running"),

    /**
     * Finished with exit code 0.
     */
    SUCCEEDED("Succeeded"),

    /**
     * Finished with a non-zero exit code or threw before completing.
     */
    FAILED("Failed"),

    /**
     * Stopped by the user before it finished.
     */
    CANCELLED("Cancelled");

    /**
     * Human-readable label for tables and badges.
     */
    private final String label;

    /**
     * Creates a lifecycle state.
     *
     * @param label display label
     */
    JobStatus(String label) {
        this.label = label;
    }

    /**
     * Returns the display label.
     *
     * @return label
     */
    public String label() {
        return label;
    }

    /**
     * Returns whether this is a terminal state (the job will not change again).
     *
     * @return true when terminal
     */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }
}
