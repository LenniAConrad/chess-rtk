package application.gui.workbench.window;

import application.gui.workbench.command.CommandRunner;
import application.gui.workbench.dashboard.DashboardActions;
import application.gui.workbench.session.Job;

/**
 * Routes dashboard quick actions to the owning workbench window.
 */
public final class WindowDashboardActions extends WindowHost implements DashboardActions {

    /**
     * Creates dashboard actions.
     *
     * @param window owning workbench window
     */
    public WindowDashboardActions(WindowBase window) {
        super(window);
    }

    /**
     * Runs the built-in search flow.
     */
    @Override
    public void builtInSearch() {
        window.runBuiltInSearch();
    }

    /**
     * Runs the best-move flow.
     */
    @Override
    public void bestMove() {
        window.runBestMove();
    }

    /**
     * Runs the board-analysis flow.
     */
    @Override
    public void analyze() {
        window.runAnalyze();
    }

    /**
     * Runs the position-tagging flow.
     */
    @Override
    public void tags() {
        window.runTagsCommand();
    }

    /**
     * Runs the perft flow.
     */
    @Override
    public void perft() {
        window.runPerft();
    }

    /**
     * Runs the currently configured batch workflow.
     */
    @Override
    public void runBatch() {
        window.batchPanel.runBatch();
    }

    /**
     * Runs the engine smoke test.
     */
    @Override
    public void engineSmoke() {
        window.runEngineSmoke();
    }

    /**
     * Runs configuration validation.
     */
    @Override
    public void configValidate() {
        window.runConfigValidate();
    }

    /**
     * Runs the doctor diagnostic command.
     */
    @Override
    public void doctor() {
        window.runDoctor();
    }

    /**
     * Runs all dashboard health checks.
     */
    @Override
    public void runAllHealthChecks() {
        window.runAllHealthChecks();
    }

    /**
     * Copies the current board FEN.
     */
    @Override
    public void copyCurrentFen() {
        window.copyText(window.currentFen());
    }

    /**
     * Opens the Analyze tab.
     */
    @Override
    public void openAnalyzeTab() {
        window.selectTab(WindowBase.TAB_ANALYZE);
    }

    /**
     * Opens the Batch tab.
     */
    @Override
    public void openBatchTab() {
        window.selectTab(WindowBase.TAB_BATCH);
    }

    /**
     * Opens the Console tab.
     */
    @Override
    public void openConsoleTab() {
        window.selectTab(WindowBase.TAB_CONSOLE);
    }

    /**
     * Re-runs a dashboard job.
     *
     * @param job job to retry
     */
    @Override
    public void retryJob(Job job) {
        if (job != null) {
            window.runCommand(job.args(), null);
        }
    }

    /**
     * Copies the command that launched a dashboard job.
     *
     * @param job job whose command should be copied
     */
    @Override
    public void copyJobCommand(Job job) {
        if (job != null) {
            window.copyText(CommandRunner.displayCommand(job.args()));
        }
    }

    /**
     * Opens a dashboard job manifest.
     *
     * @param job job whose manifest should open
     */
    @Override
    public void openJobManifest(Job job) {
        if (job != null) {
            window.runArtifacts.openManifest(job);
        }
    }

    /**
     * Opens a dashboard job log.
     *
     * @param job job whose log should open
     */
    @Override
    public void openJobLog(Job job) {
        if (job != null) {
            window.runArtifacts.openLog(job);
        }
    }
}
