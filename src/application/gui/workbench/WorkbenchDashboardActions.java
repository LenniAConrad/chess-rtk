package application.gui.workbench;

/**
 * The set of operations the {@link WorkbenchDashboardPanel} can trigger on the
 * host {@link WorkbenchWindow}.
 *
 * <p>Defined as a narrow interface so the dashboard does not depend on the
 * 5000-line window class directly and the window does not have to widen the
 * visibility of unrelated private methods — it simply hands the dashboard one
 * implementation that delegates to its existing actions.</p>
 */
interface WorkbenchDashboardActions {

    /**
     * Runs the built-in search on the current position.
     */
    void builtInSearch();

    /**
     * Runs the external-engine best-move command.
     */
    void bestMove();

    /**
     * Runs the external-engine analysis command.
     */
    void analyze();

    /**
     * Runs the static-tag command for the current position.
     */
    void tags();

    /**
     * Runs perft on the current position.
     */
    void perft();

    /**
     * Runs the currently-selected batch task.
     */
    void runBatch();

    /**
     * Runs the external-engine UCI smoke test.
     */
    void engineSmoke();

    /**
     * Runs CLI config validation.
     */
    void configValidate();

    /**
     * Runs the {@code doctor} environment self-test.
     */
    void doctor();

    /**
     * Runs every environment-health check (config validate, doctor, engine
     * smoke) in sequence.
     */
    void runAllHealthChecks();

    /**
     * Copies the current position FEN to the system clipboard.
     */
    void copyCurrentFen();

    /**
     * Brings the Analyze tab to the front.
     */
    void openAnalyzeTab();

    /**
     * Brings the Batch tab to the front.
     */
    void openBatchTab();

    /**
     * Brings the Console tab to the front.
     */
    void openConsoleTab();

    /**
     * Re-runs the command behind a recorded job.
     *
     * @param job job whose command should be re-run
     */
    void retryJob(WorkbenchJob job);

    /**
     * Copies a recorded job's command line to the system clipboard.
     *
     * @param job job whose command should be copied
     */
    void copyJobCommand(WorkbenchJob job);

    /**
     * Opens a recorded job's run manifest.
     *
     * @param job job whose manifest should be opened
     */
    void openJobManifest(WorkbenchJob job);
}
