package application.gui.workbench.dashboard;

import application.gui.workbench.board.*;
import application.gui.workbench.command.*;
import application.gui.workbench.game.*;
import application.gui.workbench.layout.*;
import application.gui.workbench.mcts.*;
import application.gui.workbench.network.*;
import application.gui.workbench.publish.*;
import application.gui.workbench.session.*;
import application.gui.workbench.ui.*;
import application.gui.workbench.window.*;

/**
 * The set of operations the {@link DashboardPanel} can trigger on the
 * host {@link Window}.
 *
 * <p>Defined as a narrow interface so the dashboard does not depend on the
 * 5000-line window class directly and the window does not have to widen the
 * visibility of unrelated private methods — it simply hands the dashboard one
 * implementation that delegates to its existing actions.</p>
 */
public interface DashboardActions {

    /**
     * Runs the built-in search on the current position.
     */
    public void builtInSearch();

    /**
     * Runs the external-engine best-move command.
     */
    public void bestMove();

    /**
     * Runs the external-engine analysis command.
     */
    public void analyze();

    /**
     * Runs the static-tag command for the current position.
     */
    public void tags();

    /**
     * Runs perft on the current position.
     */
    public void perft();

    /**
     * Runs the currently-selected batch task.
     */
    public void runBatch();

    /**
     * Runs the external-engine UCI smoke test.
     */
    public void engineSmoke();

    /**
     * Runs CLI config validation.
     */
    public void configValidate();

    /**
     * Runs the {@code doctor} environment self-test.
     */
    public void doctor();

    /**
     * Runs every environment-health check (config validate, doctor, engine
     * smoke) in sequence.
     */
    public void runAllHealthChecks();

    /**
     * Copies the current position FEN to the system clipboard.
     */
    public void copyCurrentFen();

    /**
     * Brings the Analyze tab to the front.
     */
    public void openAnalyzeTab();

    /**
     * Brings the Batch tab to the front.
     */
    public void openBatchTab();

    /**
     * Brings the Console tab to the front.
     */
    public void openConsoleTab();

    /**
     * Re-runs the command behind a recorded job.
     *
     * @param job job whose command should be re-run
     */
    public void retryJob(Job job);

    /**
     * Copies a recorded job's command line to the system clipboard.
     *
     * @param job job whose command should be copied
     */
    public void copyJobCommand(Job job);

    /**
     * Opens a recorded job's run manifest.
     *
     * @param job job whose manifest should be opened
     */
    public void openJobManifest(Job job);

    /**
     * Opens a recorded job's full command log.
     *
     * @param job job whose log should be opened
     */
    public void openJobLog(Job job);
}
