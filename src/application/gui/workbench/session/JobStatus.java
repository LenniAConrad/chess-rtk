package application.gui.workbench.session;

import application.gui.workbench.board.*;
import application.gui.workbench.command.*;
import application.gui.workbench.dashboard.*;
import application.gui.workbench.game.*;
import application.gui.workbench.layout.*;
import application.gui.workbench.mcts.*;
import application.gui.workbench.network.*;
import application.gui.workbench.publish.*;
import application.gui.workbench.ui.*;
import application.gui.workbench.window.*;

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
